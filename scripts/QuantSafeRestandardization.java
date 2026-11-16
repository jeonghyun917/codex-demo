import com.kingyurina.demo.stock.*;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.nio.file.*;
import java.security.MessageDigest;
import java.sql.*;
import java.time.*;
import java.util.*;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import static com.kingyurina.demo.stock.FinancialRestandardizationSafety.*;

/** Explicit offline operator tool. Never starts Spring, deletes, inserts, or alters schema.
 * --prepare NEW_DIRECTORY performs only a repeatable-read, read-only snapshot.
 * --apply DIRECTORY/manifest.json updates existing ids in one guarded transaction.
 * Recovery is the separately reviewed mariadb-dump; no unsafe automatic rollback mode.
 */
public final class QuantSafeRestandardization {
    private static final ObjectMapper JSON=new ObjectMapper();
    private static final String TABLE="stock_sec_financial_standard";
    private static final List<String> FACT_COLUMNS=List.of("id","symbol","cik","taxonomy","concept","unit","fiscal_year","fiscal_period","form","filed_at","start_date","end_date","accession_number","frame","value","raw_json","fetched_at");
    private static final TypeReference<List<LinkedHashMap<String,String>>> ROW_TYPE=new TypeReference<>(){};
    public record SymbolPlan(String symbol,String candidateFile,String candidateSha256,String extrasFile,String extrasSha256,int oldRows,int candidateRows,int quarantinedRows,int stagedExtraRows,List<String> quarantinedKeys){}
    public record Manifest(String policy,String createdAt,String database,String standardizerSha256,String backupFile,String backupSha256,String oldTableSha256,String sourceFactsSha256,long sourceFactCount,int oldRows,int symbolCount,List<SymbolPlan> symbols){}
    public static void main(String[] args)throws Exception{
        require(args.length==2&&(args[0].equals("--prepare")||args[0].equals("--apply")),"Usage: --prepare NEW_DIRECTORY | --apply EXISTING_MANIFEST.json");
        String url=env("DB_URL","jdbc:mariadb://localhost:3306/king_yurina");
        try(var c=DriverManager.getConnection(url,env("DB_USERNAME","root"),env("DB_PASSWORD",""))){
            try(var s=c.createStatement()){s.execute("SET SESSION time_zone = '+00:00'");s.execute("SET SESSION innodb_lock_wait_timeout = 30");}
            if(args[0].equals("--prepare"))prepare(c,Path.of(args[1]));else apply(c,Path.of(args[1]));
        }
    }
    private static void prepare(Connection c,Path out)throws Exception{
        require(!Files.exists(out),"Refuse to overwrite existing preparation directory");Files.createDirectories(out);
        c.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);c.setReadOnly(true);c.setAutoCommit(false);
        try{
            var old=readStandards(c,false);require(!old.isEmpty(),"Empty original table");
            var facts=fingerprintFacts(c,false);String backup="old-standards.json";writeNew(out.resolve(backup),old);
            var groups=new TreeMap<String,List<Map<String,String>>>();for(var r:old)groups.computeIfAbsent(r.get("symbol"),k->new ArrayList<>()).add(r);
            var service=new SecFinancialStandardService(null,null,JSON);Method method=SecFinancialStandardService.class.getDeclaredMethod("standardize",String.class,String.class,List.class);method.setAccessible(true);
            var plans=new ArrayList<SymbolPlan>();int sequence=0;
            for(var entry:groups.entrySet()){
                String symbol=entry.getKey();var source=loadFacts(c,symbol);require(!source.isEmpty(),"No facts for "+symbol);
                var sourceCiks=new HashSet<String>();for(var fact:source){require(symbol.equals(fact.getSymbol()),"Source symbol mismatch");sourceCiks.add(normalizeCik(fact.getCik()));}require(sourceCiks.size()==1,"Mixed source CIKs for "+symbol);for(var row:entry.getValue())require(sourceCiks.contains(normalizeCik(row.get("cik"))),"Old/source CIK mismatch for "+symbol);
                @SuppressWarnings("unchecked") var standardized=(List<SecFinancialStandard>)method.invoke(service,symbol,source.getFirst().getCik(),source);
                var candidates=new ArrayList<Map<String,String>>();for(var r:standardized)candidates.add(toColumns(r));
                var plan=plan(entry.getValue(),candidates,source.size());
                String prefix=String.format(Locale.ROOT,"%04d",++sequence);String file="candidate-"+prefix+".json",extras="extras-"+prefix+".json";
                writeNew(out.resolve(file),plan.rows());writeNew(out.resolve(extras),plan.extras());
                plans.add(new SymbolPlan(symbol,file,fileHash(out.resolve(file)),extras,fileHash(out.resolve(extras)),entry.getValue().size(),candidates.size(),plan.quarantinedKeys().size(),plan.extras().size(),plan.quarantinedKeys()));
                System.out.println("PREPARE "+symbol+" old="+entry.getValue().size()+" repaired="+(plan.rows().size()-plan.quarantinedKeys().size())+" quarantined="+plan.quarantinedKeys().size()+" stagedExtras="+plan.extras().size());
            }
            var manifest=new Manifest("UPDATE_EXISTING_IDS_ONLY_V1",Instant.now().toString(),database(c),standardizerHash(),backup,fileHash(out.resolve(backup)),hash(old),facts.hash(),facts.count(),old.size(),groups.size(),plans);
            writeNew(out.resolve("manifest.json"),manifest);writeNew(out.resolve("manifest.sha256"),Map.of("sha256",fileHash(out.resolve("manifest.json"))));c.rollback();
            System.out.println("READ_ONLY_PREPARATION_COMPLETE rows="+old.size()+" symbols="+groups.size()+" quarantined="+plans.stream().mapToInt(SymbolPlan::quarantinedRows).sum()+" extrasNotInserted="+plans.stream().mapToInt(SymbolPlan::stagedExtraRows).sum()+" manifest="+out.resolve("manifest.json"));
        }finally{c.rollback();c.setAutoCommit(true);c.setReadOnly(false);}
    }
    private static void apply(Connection c,Path manifestPath)throws Exception{
        Path root=manifestPath.toAbsolutePath().normalize().getParent();
        require(manifestPath.getFileName().toString().equals("manifest.json"),"Expected original manifest.json");
        String expectedManifestHash=JSON.readTree(Files.readString(root.resolve("manifest.sha256"))).path("sha256").asText();
        require(fileHash(manifestPath).equals(expectedManifestHash),"Manifest hash mismatch");
        var manifest=JSON.readValue(Files.readString(manifestPath),Manifest.class);
        require("UPDATE_EXISTING_IDS_ONLY_V1".equals(manifest.policy()),"Unsupported policy");require(database(c).equals(manifest.database()),"Wrong database identity");
        require(standardizerHash().equals(manifest.standardizerSha256()),"Standardizer code changed since preparation");
        Path backup=safeFile(root,manifest.backupFile());require(fileHash(backup).equals(manifest.backupSha256()),"Original backup hash mismatch");
        var old=readFileRows(backup);require(old.size()==manifest.oldRows()&&hash(old).equals(manifest.oldTableSha256()),"Backup table hash/count mismatch");
        var replacement=new ArrayList<Map<String,String>>();var seenSymbols=new HashSet<String>();
        for(var symbol:manifest.symbols()){
            require(seenSymbols.add(symbol.symbol()),"Duplicate manifest symbol");var file=safeFile(root,symbol.candidateFile());require(fileHash(file).equals(symbol.candidateSha256()),"Candidate hash mismatch "+symbol.symbol());
            var rows=readFileRows(file);require(rows.size()==symbol.oldRows()&&rows.stream().allMatch(r->symbol.symbol().equals(r.get("symbol"))),"Symbol/count mismatch");
            for(var row:rows)validate(row);replacement.addAll(rows);
            var extras=safeFile(root,symbol.extrasFile());require(fileHash(extras).equals(symbol.extrasSha256()),"Extra evidence hash mismatch");require(readFileRows(extras).size()==symbol.stagedExtraRows(),"Extra count mismatch");
        }
        require(seenSymbols.size()==manifest.symbolCount(),"Symbol count mismatch");replacement.sort(Comparator.comparingLong(r->Long.parseLong(r.get("id"))));
        require(replacement.size()==old.size(),"Prepared output count mismatch");
        String postHash=hash(replacement);Path intent=root.resolve("apply-intent-"+Instant.now().toString().replace(':','-')+".json");
        writeNew(intent,Map.of("manifestSha256",expectedManifestHash,"expectedPreimage",manifest.oldTableSha256(),"expectedPostimage",postHash,"note","Intent is not commit evidence; inspect live postimage after any uncertain outcome"));
        boolean acquired=false,committed=false;
        try{
            try(var p=c.prepareStatement("SELECT GET_LOCK(?, 0)")){p.setString(1,"quant_safe_restandardization");try(var rs=p.executeQuery()){require(rs.next()&&rs.getInt(1)==1,"Another repair holds advisory lock");acquired=true;}}
            var after=applyAtomically(c,old,replacement,()->{var fp=fingerprintFacts(c,true);require(fp.count()==manifest.sourceFactCount()&&fp.hash().equals(manifest.sourceFactsSha256()),"Source facts changed since preparation");});committed=true;
            System.out.println("COMMITTED rows="+after.size()+" postimageSha256="+hash(after)+" sourceFactsUnchanged=true IDsAndKeysPreserved=true");
            try{writeNew(root.resolve("committed-"+Instant.now().toString().replace(':','-')+".json"),Map.of("committed",true,"committedAt",Instant.now().toString(),"rows",after.size(),"postimageSha256",hash(after),"preimageSha256",manifest.oldTableSha256(),"manifestSha256",expectedManifestHash,"sourceFactsSha256",manifest.sourceFactsSha256(),"sourceFactsUnchanged",true,"recovery","Use independently reviewed SQL dump; automatic rollback intentionally unavailable"));}
            catch(Exception evidence){System.err.println("DATABASE ALREADY COMMITTED; evidence-file write failed. DO NOT RETRY APPLY. postimage="+hash(after));throw evidence;}
        }catch(Exception e){System.err.println(committed?"COMMITTED state; do not retry":"NOT CONFIRMED COMMITTED; transaction rolled back on precommit validation failures. Inspect postimage if connection/commit outcome was uncertain.");throw e;}
        finally{if(acquired)try(var p=c.prepareStatement("SELECT RELEASE_LOCK(?)")){p.setString(1,"quant_safe_restandardization");p.execute();}}
    }
    private record FactFingerprint(long count,String hash){}
    private static FactFingerprint fingerprintFacts(Connection c,boolean lock)throws Exception{
        var digest=MessageDigest.getInstance("SHA-256");long count=0;
        try(var st=c.createStatement(ResultSet.TYPE_FORWARD_ONLY,ResultSet.CONCUR_READ_ONLY)){st.setFetchSize(500);
            try(var rs=st.executeQuery("SELECT "+String.join(",",FACT_COLUMNS)+" FROM stock_sec_fact ORDER BY id"+(lock?" FOR UPDATE":""))){while(rs.next()){for(var col:FACT_COLUMNS)update(digest,rs.getString(col));count++;}}
        }return new FactFingerprint(count,HexFormat.of().formatHex(digest.digest()));
    }
    private static List<SecCompanyFact> loadFacts(Connection c,String symbol)throws Exception{
        var rows=new ArrayList<SecCompanyFact>();
        try(var ps=c.prepareStatement("SELECT "+String.join(",",FACT_COLUMNS)+" FROM stock_sec_fact WHERE symbol=? ORDER BY filed_at,end_date,concept,id")){ps.setString(1,symbol);try(var r=ps.executeQuery()){while(r.next()){
            var f=new SecCompanyFact();f.setId(r.getLong("id"));f.setSymbol(r.getString("symbol"));f.setCik(r.getString("cik"));f.setTaxonomy(r.getString("taxonomy"));f.setConcept(r.getString("concept"));f.setUnit(r.getString("unit"));f.setFiscalYear((Integer)r.getObject("fiscal_year"));f.setFiscalPeriod(r.getString("fiscal_period"));f.setForm(r.getString("form"));f.setFiledAt(date(r,"filed_at"));f.setStartDate(date(r,"start_date"));f.setEndDate(date(r,"end_date"));f.setAccessionNumber(r.getString("accession_number"));f.setFrame(r.getString("frame"));f.setValue(r.getBigDecimal("value"));f.setRawJson(r.getString("raw_json"));f.setFetchedAt(r.getTimestamp("fetched_at").toLocalDateTime());rows.add(f);
        }}}return rows;
    }
    private static LocalDate date(ResultSet rs,String col)throws SQLException{var value=rs.getDate(col);return value==null?null:value.toLocalDate();}
    private static Map<String,String> toColumns(SecFinancialStandard bean)throws Exception{
        var row=new LinkedHashMap<String,String>();for(var column:COLUMNS){var name=new StringBuilder("get");boolean up=true;for(char c:column.toCharArray()){if(c=='_')up=true;else{name.append(up?Character.toUpperCase(c):c);up=false;}}Object value=SecFinancialStandard.class.getMethod(name.toString()).invoke(bean);if(value instanceof BigDecimal number)value=number.setScale(6,java.math.RoundingMode.HALF_UP);if(column.equals("mapped_at"))value=LocalDateTime.ofInstant(Instant.now(),ZoneOffset.UTC).withNano(0);row.put(column,value==null?null:value.toString());}return row;
    }
    private static String standardizerHash()throws Exception{var digest=MessageDigest.getInstance("SHA-256");hashClass(SecFinancialStandardService.class,digest);return HexFormat.of().formatHex(digest.digest());}
    private static void hashClass(Class<?> type,MessageDigest digest)throws Exception{try(var in=type.getResourceAsStream("/"+type.getName().replace('.','/')+".class")){digest.update(Objects.requireNonNull(in).readAllBytes());}var nested=new ArrayList<>(List.of(type.getDeclaredClasses()));nested.sort(Comparator.comparing(Class::getName));for(var child:nested)hashClass(child,digest);}
    private static String database(Connection c)throws Exception{try(var s=c.createStatement();var rs=s.executeQuery("SELECT CONCAT(@@hostname, ':', @@port, '/', DATABASE())")){require(rs.next(),"Missing database identity");return rs.getString(1);}}
    private static String env(String key,String fallback){return System.getenv().getOrDefault(key,fallback);}
    private static String normalizeCik(String cik){require(cik!=null&&cik.matches("[0-9]+"),"Missing/invalid CIK");return new java.math.BigInteger(cik).toString();}
    private static Path safeFile(Path root,String filename){Path p=root.resolve(filename).normalize();require(p.getParent().equals(root),"Unsafe manifest path");require(Files.isRegularFile(p)&&!Files.isSymbolicLink(p),"Missing/nonregular file");return p;}
    private static List<Map<String,String>> readFileRows(Path file)throws Exception{return new ArrayList<>(JSON.readValue(Files.readString(file),ROW_TYPE));}
    private static String fileHash(Path p)throws Exception{var d=MessageDigest.getInstance("SHA-256");try(var in=Files.newInputStream(p)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)d.update(b,0,n);}return HexFormat.of().formatHex(d.digest());}
    private static void writeNew(Path p,Object value)throws Exception{Files.writeString(p,JSON.writerWithDefaultPrettyPrinter().writeValueAsString(value),StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE);}
}
