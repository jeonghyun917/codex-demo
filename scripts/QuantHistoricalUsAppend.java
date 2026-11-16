import com.kingyurina.demo.stock.*;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.core.type.TypeReference;
import java.lang.reflect.*;
import java.math.*;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import static com.kingyurina.demo.stock.FinancialRestandardizationSafety.*;

/** Explicit four-US-issuer NEW ROWS ONLY operator. Prepare is read-only; apply is never implicit.
 * --verify-rollback inserts inside a transaction and ALWAYS rolls back; parent operator runs it.
 * Plain INSERT only. Existing symbol rows cause rejection even when equal. No upsert/update/delete.
 */
public final class QuantHistoricalUsAppend {
    static final ObjectMapper JSON=new ObjectMapper();
    static final Map<String,String> CIK=Map.of("ANSS","1013462","ILMN","1110803","MDB","1441816","TEAM","1650372");
    static final List<String> FC=List.of("symbol","cik","taxonomy","concept","unit","fiscal_year","fiscal_period","form","filed_at","start_date","end_date","accession_number","frame","value","raw_json","fetched_at");
    static final Set<String> CONCEPTS=Set.of("Revenues","RevenueFromContractWithCustomerExcludingAssessedTax","SalesRevenueNet","OperatingIncomeLoss","NetIncomeLoss","NetIncomeLossAvailableToCommonStockholdersBasic","EarningsPerShareDiluted","Assets","Liabilities","StockholdersEquity","StockholdersEquityIncludingPortionAttributableToNoncontrollingInterest","NetCashProvidedByUsedInOperatingActivities","NetCashProvidedByUsedInOperatingActivitiesContinuingOperations");
    static final TypeReference<List<LinkedHashMap<String,String>>> MAPS=new TypeReference<>(){};
    public record Manifest(String policy,String createdAt,String database,String factsFile,String factsHash,int factsCount,
        String standardsFile,String standardsHash,int standardsCount,String backupFile,String backupHash,String standardsPreimage,
        Map<String,String> inputHashes,Map<String,String> codeHashes,Map<String,Object> expectedCounts){}
    static void validateFact(SecCompanyFact f){
        require(f!=null&&CIK.containsKey(f.getSymbol()),"Unknown issuer");
        require(f.getCik()!=null&&new BigInteger(f.getCik()).toString().equals(CIK.get(f.getSymbol())),"Wrong issuer CIK");
        require("us-gaap".equals(f.getTaxonomy())&&CONCEPTS.contains(f.getConcept()),"Unsupported taxonomy/concept");
        require("USD".equals(f.getUnit())||"USD/shares".equals(f.getUnit()),"Unsupported unit");
        require(f.getFiscalYear()!=null&&f.getFiscalPeriod()!=null&&f.getFiscalPeriod().matches("FY|Q[1-4]"),"Nullable/invalid fiscal key");
        require(Set.of("10-K","10-K/A","10-Q","10-Q/A").contains(f.getForm()),"Unsupported form");
        require(f.getEndDate()!=null&&(f.getStartDate()==null||!f.getStartDate().isAfter(f.getEndDate()))&&f.getFiledAt()!=null,"Invalid dates");
        require(f.getValue()!=null&&f.getValue().precision()-f.getValue().scale()<=26&&f.getValue().scale()<=6,"Value exceeds exact DECIMAL(32,6)");
        require(f.getFetchedAt()!=null&&Duration.between(f.getFetchedAt(),LocalDateTime.now(ZoneOffset.UTC)).abs().toHours()<48,"Receipt time is not now");
        var raw=JSON.readTree(Objects.requireNonNull(f.getRawJson()));
        require(raw.path("accn").asText().equals(f.getAccessionNumber())&&raw.path("filed").asText().equals(f.getFiledAt().toString())&&raw.path("end").asText().equals(f.getEndDate().toString()),"Raw identity mismatch");
        require(raw.path("form").asText().equals(f.getForm())&&raw.path("fy").asInt()==f.getFiscalYear()&&raw.path("fp").asText().equals(f.getFiscalPeriod()),"Raw fiscal/form mismatch");
        String start=raw.path("start").isMissingNode()||raw.path("start").isNull()?null:raw.path("start").asText();require(Objects.equals(start,f.getStartDate()==null?null:f.getStartDate().toString()),"Raw start mismatch");
        require(new BigDecimal(raw.path("val").asText()).compareTo(f.getValue())==0,"Raw value mismatch");
    }
    static String factKey(SecCompanyFact f){return JSON.writeValueAsString(Arrays.asList(f.getSymbol(),f.getTaxonomy(),f.getConcept(),f.getUnit(),f.getAccessionNumber(),f.getFiscalYear(),f.getFiscalPeriod(),f.getStartDate(),f.getEndDate()));}
    static List<SecCompanyFact> deduplicate(List<SecCompanyFact> source){
        var out=new LinkedHashMap<String,SecCompanyFact>();for(var f:source){String key=factKey(f);var old=out.putIfAbsent(key,f);
            if(old!=null)require(old.getValue().compareTo(f.getValue())==0&&Objects.equals(old.getFiledAt(),f.getFiledAt())&&Objects.equals(old.getRawJson(),f.getRawJson()),"Conflicting same-key raw facts");
        }return new ArrayList<>(out.values());
    }
    static String sha(Path p)throws Exception{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p)));}
    static void write(Path p,Object o)throws Exception{Files.writeString(p,JSON.writerWithDefaultPrettyPrinter().writeValueAsString(o),StandardOpenOption.CREATE_NEW);}
    static String env(String k,String fallback){return System.getenv().getOrDefault(k,fallback);}
    static Connection connect()throws Exception{
        var c=DriverManager.getConnection(env("DB_URL","jdbc:mariadb://localhost:3306/king_yurina"),env("DB_USERNAME","root"),env("DB_PASSWORD",""));
        try(var s=c.createStatement()){s.execute("SET SESSION time_zone = '+00:00'");s.execute("SET SESSION innodb_lock_wait_timeout=30");}return c;
    }
    static String database(Connection c)throws Exception{try(var s=c.createStatement();var r=s.executeQuery("SELECT CONCAT(@@hostname,':',@@port,'/',DATABASE())")){r.next();return r.getString(1);}}
    static Map<String,String> codeHashes()throws Exception{
        var out=new TreeMap<String,String>();for(String p:List.of("scripts/QuantHistoricalUsAppend.java","target/classes/com/kingyurina/demo/stock/SecFinancialStandardService.class","target/classes/com/kingyurina/demo/stock/SecFinancialStandardService$RowBuilder.class","target/classes/com/kingyurina/demo/stock/FinancialRestandardizationSafety.class"))out.put(p,sha(Path.of(p)));return out;
    }
    static void scopeEmpty(Connection c,boolean lock)throws Exception{
        for(String table:List.of("stock_sec_fact","stock_sec_financial_standard"))for(String symbol:new TreeSet<>(CIK.keySet())){
            try(var ps=c.prepareStatement("SELECT id FROM "+table+" WHERE symbol=?"+(lock?" FOR UPDATE":""))){ps.setString(1,symbol);try(var r=ps.executeQuery()){require(!r.next(),"Existing scoped row/collision: "+table+" "+symbol);}}
        }
    }
    static Map<String,String> columns(Object bean,List<String> names)throws Exception{
        var out=new LinkedHashMap<String,String>();for(String col:names){StringBuilder getter=new StringBuilder("get");boolean up=true;
            for(char ch:col.toCharArray()){if(ch=='_')up=true;else{getter.append(up?Character.toUpperCase(ch):ch);up=false;}}
            Object value=bean.getClass().getMethod(getter.toString()).invoke(bean);
            if(value instanceof BigDecimal n)value=n.setScale(6,RoundingMode.UNNECESSARY);
            out.put(col,value==null?null:value.toString());
        }return out;
    }
    static List<Map<String,String>> readFile(Path p)throws Exception{return new ArrayList<>(JSON.readValue(Files.readString(p),MAPS));}
    static void prepare(Path staging,Path out)throws Exception{
        require(!Files.exists(out),"Output exists");Files.createDirectories(out);
        var hashes=JSON.readTree(Files.readString(staging.resolve("hashes.json")));for(var e:hashes.properties())require(sha(Path.of(e.getKey())).equals(e.getValue().asText()),"Staging hash mismatch "+e.getKey());
        var sourceHashes=new TreeMap<String,String>();for(var e:hashes.properties())sourceHashes.put(e.getKey(),e.getValue().asText());
        sourceHashes.put(staging.resolve("hashes.json").toString(),sha(staging.resolve("hashes.json")));
        var coverage=JSON.readTree(Files.readString(staging.resolve("coverage.json")));var needed=new TreeMap<String,Set<String>>();
        var lastDecision=new TreeMap<String,LocalDate>();for(var p:coverage.path("coverage"))if(CIK.containsKey(p.path("symbol").asText())){
            String s=p.path("symbol").asText();var set=needed.computeIfAbsent(s,k->new HashSet<>());p.path("requiredAccessions").forEach(a->set.add(a.asText()));
            var day=LocalDate.parse(p.path("decision").asText());lastDecision.merge(s,day,(a,b)->a.isAfter(b)?a:b);
        }
        var facts=new ArrayList<SecCompanyFact>();
        for(var n:JSON.readTree(Files.readString(staging.resolve("candidate-raw-facts.json")))){
            String s=n.path("symbol").asText();if(!CIK.containsKey(s)||!CONCEPTS.contains(n.path("concept").asText())||!n.path("taxonomy").asText().equals("us-gaap"))continue;
            var copy=n.deepCopy();((tools.jackson.databind.node.ObjectNode)copy).remove("fetchedAt");
            var f=JSON.treeToValue(copy,SecCompanyFact.class);f.setFetchedAt(LocalDateTime.now(ZoneOffset.UTC).withNano(0));validateFact(f);
            require(needed.get(s).contains(f.getAccessionNumber())&&f.getFiledAt().isBefore(lastDecision.get(s)),"Unrequired/future source row");
            facts.add(f);
        }
        facts=new ArrayList<>(deduplicate(facts));require(!facts.isEmpty(),"No facts");
        var standardizer=new SecFinancialStandardService(null,null,JSON);var method=SecFinancialStandardService.class.getDeclaredMethod("standardize",String.class,String.class,List.class);method.setAccessible(true);
        var standards=new ArrayList<Map<String,String>>();var countBySymbol=new TreeMap<String,Object>();var seen=new HashSet<String>();
        for(String s:new TreeSet<>(CIK.keySet())){
            var sf=facts.stream().filter(f->s.equals(f.getSymbol())).toList();require(!sf.isEmpty(),"Missing issuer");
            @SuppressWarnings("unchecked")var rows=(List<SecFinancialStandard>)method.invoke(standardizer,s,CIK.get(s),sf);
            for(var r:rows){r.setMappedAt(LocalDateTime.now(ZoneOffset.UTC).withNano(0));var cols=columns(r,COLUMNS);validate(cols);require(seen.add(key(cols)),"Duplicate standard natural key");standards.add(cols);}
            countBySymbol.put(s,Map.of("facts",sf.size(),"standards",rows.size()));
        }
        var raw=new ArrayList<Map<String,String>>();for(var f:facts)raw.add(columns(f,FC));
        try(var c=connect()){
            c.setReadOnly(true);c.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);c.setAutoCommit(false);scopeEmpty(c,false);
            var backup=readStandards(c,false);write(out.resolve("current-coherent-standard-backup.json"),backup);
            write(out.resolve("candidate-facts.json"),raw);write(out.resolve("candidate-standards.json"),standards);
            var m=new Manifest("INSERT_ONLY_EMPTY_US_ISSUERS_V1",Instant.now().toString(),database(c),"candidate-facts.json",sha(out.resolve("candidate-facts.json")),raw.size(),
                "candidate-standards.json",sha(out.resolve("candidate-standards.json")),standards.size(),"current-coherent-standard-backup.json",sha(out.resolve("current-coherent-standard-backup.json")),hash(backup),sourceHashes,codeHashes(),countBySymbol);
            write(out.resolve("manifest.json"),m);write(out.resolve("manifest.sha256.json"),Map.of("sha256",sha(out.resolve("manifest.json"))));c.rollback();
            System.out.println("PREPARED no DB writes facts="+raw.size()+" standards="+standards.size()+" bySymbol="+JSON.writeValueAsString(countBySymbol));
        }
    }
    static void insert(Connection c,String table,List<String> cols,List<Map<String,String>> rows)throws Exception{
        String sql="INSERT INTO "+table+" ("+String.join(",",cols)+") VALUES ("+String.join(",",Collections.nCopies(cols.size(),"?"))+")";
        try(var ps=c.prepareStatement(sql)){int count=0;for(var row:rows){for(int i=0;i<cols.size();i++)ps.setString(i+1,row.get(cols.get(i)));ps.addBatch();if(++count%200==0)for(int n:ps.executeBatch())require(n==1||n==Statement.SUCCESS_NO_INFO,"Insert count mismatch");}
            if(count%200!=0)for(int n:ps.executeBatch())require(n==1||n==Statement.SUCCESS_NO_INFO,"Insert count mismatch");}
    }
    static List<Map<String,String>> scoped(Connection c,String table,List<String> cols)throws Exception{
        try(var st=c.createStatement();var rs=st.executeQuery("SELECT "+String.join(",",cols)+" FROM "+table+" WHERE symbol IN ('ANSS','ILMN','MDB','TEAM') ORDER BY id")){return readRows(rs,cols);}
    }
    static String rowDigest(List<Map<String,String>> rows,List<String> cols)throws Exception{
        var d=java.security.MessageDigest.getInstance("SHA-256");var canonical=new ArrayList<String>();
        for(var r:rows){var values=new ArrayList<String>();for(String col:cols){String value=r.get(col);
            if(value!=null){if(col.equals("value")||VALUES.contains(col))value=new BigDecimal(value).stripTrailingZeros().toPlainString();else if(col.equals("fetched_at")||col.equals("mapped_at"))value=LocalDateTime.parse(value.replace(' ','T')).toString();}
            values.add(value);
        }canonical.add(JSON.writeValueAsString(values));}Collections.sort(canonical);for(String s:canonical)update(d,s);return HexFormat.of().formatHex(d.digest());
    }
    static void operate(Path manifestFile,boolean commit,boolean failAfterRaw)throws Exception{
        Path root=manifestFile.toAbsolutePath().normalize().getParent();var m=JSON.readValue(Files.readString(manifestFile),Manifest.class);
        require(JSON.readTree(Files.readString(root.resolve("manifest.sha256.json"))).path("sha256").asText().equals(sha(manifestFile)),"Manifest hash mismatch");
        require(m.policy().equals("INSERT_ONLY_EMPTY_US_ISSUERS_V1")&&m.codeHashes().equals(codeHashes()),"Policy/code changed");
        for(var entry:m.inputHashes().entrySet())require(sha(Path.of(entry.getKey())).equals(entry.getValue()),"Input changed "+entry.getKey());
        require(m.factsFile().equals("candidate-facts.json")&&m.standardsFile().equals("candidate-standards.json")&&m.backupFile().equals("current-coherent-standard-backup.json"),"Unsafe filename");
        require(sha(root.resolve(m.factsFile())).equals(m.factsHash())&&sha(root.resolve(m.standardsFile())).equals(m.standardsHash())&&sha(root.resolve(m.backupFile())).equals(m.backupHash()),"Candidate/backup hash changed");
        var facts=readFile(root.resolve(m.factsFile()));var standards=readFile(root.resolve(m.standardsFile()));var backup=readFile(root.resolve(m.backupFile()));
        require(facts.size()==m.factsCount()&&standards.size()==m.standardsCount()&&hash(backup).equals(m.standardsPreimage()),"Count/backup preimage mismatch");
        var factSeen=new HashSet<String>();for(var r:facts){var f=new SecCompanyFact();
            // Read SQL-shaped columns back through the existing bean mapping names.
            var node=JSON.createObjectNode();for(var e:r.entrySet()){StringBuilder b=new StringBuilder();boolean up=false;for(char ch:e.getKey().toCharArray()){if(ch=='_')up=true;else{b.append(up?Character.toUpperCase(ch):ch);up=false;}}if(e.getValue()==null)node.putNull(b.toString());else node.put(b.toString(),e.getValue());}
            f=JSON.treeToValue(node,SecCompanyFact.class);validateFact(f);require(factSeen.add(factKey(f)),"Duplicate raw key");
        }
        var standardSeen=new HashSet<String>();for(var r:standards){validate(r);require(CIK.containsKey(r.get("symbol"))&&new BigInteger(r.get("cik")).toString().equals(CIK.get(r.get("symbol"))),"Wrong standard issuer");require(standardSeen.add(key(r)),"Duplicate standard key");}
        var evidence=root.resolve((commit?"apply":"rollback-probe")+"-"+Instant.now().toString().replace(':','-')+".json");
        boolean committed=false,ended=false;
        try(var c=connect()){
            require(database(c).equals(m.database()),"Wrong DB");
            try(var p=c.prepareStatement("SELECT GET_LOCK('quant_historical_us_append',0)")){try(var r=p.executeQuery()){require(r.next()&&r.getInt(1)==1,"Another append active");}}
            c.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);c.setAutoCommit(false);
            try{
                scopeEmpty(c,true);require(hash(readStandards(c,true)).equals(m.standardsPreimage()),"Standard preimage drift");
                insert(c,"stock_sec_fact",FC,facts);
                if(failAfterRaw)throw new IllegalStateException("INJECTED_AFTER_RAW_INSERT");
                var sc=COLUMNS.stream().filter(k->!k.equals("id")).toList();insert(c,"stock_sec_financial_standard",sc,standards);
                require(rowDigest(scoped(c,"stock_sec_fact",FC),FC).equals(rowDigest(facts,FC)),"Raw postimage mismatch");
                var current=readStandards(c,false);var old=current.stream().filter(r->!CIK.containsKey(r.get("symbol"))).toList();require(hash(old).equals(m.standardsPreimage()),"Existing standard rows changed");
                require(rowDigest(scoped(c,"stock_sec_financial_standard",sc),sc).equals(rowDigest(standards,sc)),"Standard postimage mismatch");
                if(commit){c.commit();committed=true;}else c.rollback();ended=true;
                c.setAutoCommit(true);
                if(!commit){scopeEmpty(c,false);require(hash(readStandards(c,false)).equals(m.standardsPreimage()),"Rollback did not preserve standard preimage");}
                write(evidence,Map.of("committed",committed,"rollbackVerified",!commit,"facts",facts.size(),"standards",standards.size(),"manifestSha256",sha(manifestFile),"databaseWritesCommitted",commit?facts.size()+standards.size():0,"recordedAt",Instant.now().toString()));
                System.out.println(commit?"COMMITTED new facts="+facts.size()+" standards="+standards.size():"ROLLBACK VERIFIED after full candidate insertion; committed rows=0");
            }catch(Exception e){
                if(!ended&&!committed)c.rollback();
                if(failAfterRaw&&e.getMessage().equals("INJECTED_AFTER_RAW_INSERT")){
                    c.setAutoCommit(true);scopeEmpty(c,false);require(hash(readStandards(c,false)).equals(m.standardsPreimage()),"Partial rollback altered standards");
                    write(evidence,Map.of("committed",false,"rollbackVerified",true,"injectedFailure","AFTER_RAW_INSERT","factsAttempted",facts.size(),"manifestSha256",sha(manifestFile)));System.out.println("ROLLBACK VERIFIED injected after raw insertion; committed rows=0");
                }else{System.err.println(committed?"ALREADY COMMITTED; do not retry apply":"No confirmed commit; transaction rollback attempted; inspect current state before retry");throw e;}
            }finally{try(var st=c.createStatement()){st.execute("SELECT RELEASE_LOCK('quant_historical_us_append')");}}
        }
    }
    public static void main(String[] args)throws Exception{
        require(args.length>=2,"Usage: --prepare STAGING_DIR NEW_PLAN_DIR | --verify-rollback MANIFEST | --verify-partial-rollback MANIFEST | --apply MANIFEST");
        switch(args[0]){case "--prepare"->{require(args.length==3,"Expected 3 arguments");prepare(Path.of(args[1]),Path.of(args[2]));}
            case "--verify-rollback"->operate(Path.of(args[1]),false,false);
            case "--verify-partial-rollback"->operate(Path.of(args[1]),false,true);
            case "--apply"->operate(Path.of(args[1]),true,false);
            default->throw new IllegalStateException("Unsupported mode");}
    }
}
