import java.sql.*;
import java.nio.file.*;
import java.math.BigDecimal;
import java.util.*;
import tools.jackson.databind.*;
import tools.jackson.databind.node.ObjectNode;

/** Explicit operator: no live writes unless --apply/--probe plus externally supplied manifest SHA. */
public final class QuantEpsRecoveryApply {
    static final ObjectMapper J=new ObjectMapper();
    record Plan(ObjectNode original,ObjectNode recovered,Map<String,String> preimage,Map<String,String> fact,Map<String,String> factPreimage){}
    static final List<String> FACT_CORE=List.of("symbol","cik","taxonomy","concept","unit","fiscal_year","fiscal_period","form","filed_at","start_date","end_date","accession_number","frame","value","raw_json");
    static final List<String> FACT_KEY=List.of("symbol","taxonomy","concept","unit","accession_number","fiscal_year","fiscal_period","start_date","end_date");
    static void require(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
    static String text(JsonNode n){return n==null||n.isNull()||n.isMissingNode()?null:n.asText();}
    static String column(String camel){return camel.replaceAll("([a-z])([A-Z])","$1_$2").toLowerCase(Locale.ROOT);}
    static Map<String,String> readOne(PreparedStatement p)throws Exception{
        try(var r=p.executeQuery()){
            if(!r.next())return null;var row=new TreeMap<String,String>();var md=r.getMetaData();
            for(int i=1;i<=md.getColumnCount();i++)row.put(md.getColumnLabel(i).toLowerCase(Locale.ROOT),r.getString(i));
            require(!r.next(),"Duplicate DB natural key");return row;
        }
    }
    static Map<String,String> standard(Connection c,long id,boolean lock)throws Exception{
        try(var p=c.prepareStatement("SELECT * FROM stock_sec_financial_standard WHERE id=?"+(lock?" FOR UPDATE":""))){p.setLong(1,id);return readOne(p);}
    }
    static String canonical(String column,String value){
        if(value==null)return null;
        if(Set.of("id","fiscal_year","period_days","revenue","operating_income","net_income","eps_diluted","assets","liabilities","equity","operating_cash_flow","value").contains(column))return new BigDecimal(value).stripTrailingZeros().toPlainString();
        if(column.equals("cik"))return new java.math.BigInteger(value).toString();
        if(column.equals("mapped_at")||column.equals("fetched_at"))return Timestamp.valueOf(value.replace('T',' ')).toLocalDateTime().toString();
        return value;
    }
    static boolean equalCore(Map<String,String> a,Map<String,String> b,List<String> columns){
        if(a==null||b==null)return a==b;
        for(String column:columns){
            if(column.equals("raw_json")){if(!Objects.equals(J.readTree(a.get(column)),J.readTree(b.get(column))))return false;}
            else if(!Objects.equals(canonical(column,a.get(column)),canonical(column,b.get(column))))return false;
        }return true;
    }
    static Map<String,String> fact(ObjectNode recovered){
        var evidence=J.readTree(recovered.path("rawJson").asText()).at("/selectedFieldEvidence/epsDiluted");var obs=J.readTree(evidence.path("sourceObservationJson").asText());
        var f=new TreeMap<String,String>();f.put("symbol",recovered.path("symbol").asText());f.put("cik",recovered.path("cik").asText());
        f.put("taxonomy",evidence.path("taxonomy").asText());f.put("concept",evidence.path("concept").asText());f.put("unit",evidence.path("unit").asText());
        for(String[] pair:new String[][]{{"fiscal_year","fy"},{"fiscal_period","fp"},{"form","form"},{"filed_at","filed"},{"start_date","start"},{"end_date","end"},{"accession_number","accn"},{"frame","frame"},{"value","val"}})f.put(pair[0],text(obs.path(pair[1])));
        f.put("raw_json",obs.toString());return f;
    }
    static Map<String,String> existingFact(Connection c,Map<String,String> f,boolean lock)throws Exception{
        String where=String.join(" AND ",FACT_KEY.stream().map(k->k+"=?").toList());
        try(var p=c.prepareStatement("SELECT * FROM stock_sec_fact WHERE "+where+(lock?" FOR UPDATE":""))){for(int i=0;i<FACT_KEY.size();i++)p.setString(i+1,f.get(FACT_KEY.get(i)));return readOne(p);}
    }
    static void validatePair(ObjectNode old,ObjectNode recovered,Map<String,JsonNode> archives)throws Exception{
        var a=old.deepCopy();var b=recovered.deepCopy();for(String f:List.of("epsDiluted","epsUnit","rawJson")){a.remove(f);b.remove(f);}require(a.equals(b),"Non-EPS DTO mutation");
        require(old.path("epsDiluted").isNull(),"Cannot overwrite existing nonnull EPS");
        var raw=J.readTree(recovered.path("rawJson").asText());var receipt=raw.path("epsRecovery");Path path=Path.of(receipt.path("sourcePath").asText());
        require(QuantEpsRecoveryPrepare.sha(path).equalsIgnoreCase(receipt.path("sourceSha256").asText()),"Primary source SHA mismatch");
        String key=path.toAbsolutePath().normalize().toString();var archive=archives.get(key);if(archive==null){archive=J.readTree(Files.readString(path));archives.put(key,archive);}
        var expected=QuantEpsRecoveryPrepare.recover(old,recovered,archive,path,receipt.path("sourceSha256").asText());
        require(!expected.path("epsDiluted").isNull(),"Coherent primary EPS proof absent");
        var e=expected.deepCopy();var r=recovered.deepCopy();e.remove("rawJson");r.remove("rawJson");require(e.equals(r),"EPS DTO changed beyond proven repair");
        require(J.readTree(expected.path("rawJson").asText()).equals(raw),"Raw non-EPS evidence mutation");
        var exact=new BigDecimal(recovered.path("epsDiluted").asText()).setScale(6,java.math.RoundingMode.UNNECESSARY);require(exact.precision()-exact.scale()<=18,"EPS exceeds DECIMAL(24,6)");
    }
    static List<Plan> plan(Connection c,List<ObjectNode> old,List<ObjectNode> recovered)throws Exception {
        var byKey=new TreeMap<String,ObjectNode>();for(var n:old)require(byKey.put(QuantEpsRecoveryPrepare.natural(n),n)==null,"Duplicate original key");
        var seen=new HashSet<String>();var ids=new HashSet<Long>();var plans=new ArrayList<Plan>();var archives=new HashMap<String,JsonNode>();
        for(var n:recovered){String key=QuantEpsRecoveryPrepare.natural(n);require(seen.add(key),"Duplicate recovered key");var o=byKey.get(key);require(o!=null,"New recovered key");
            if(o.equals(n))continue;validatePair(o,n,archives);
            if(o.path("id").isNull())continue;
            long id=o.path("id").asLong();require(id>0&&ids.add(id),"Invalid/duplicate target ID");var pre=standard(c,id,false);require(pre!=null,"Missing target ID "+id);
            for(var field:o.properties())require(Objects.equals(canonical(column(field.getKey()),text(field.getValue())),canonical(column(field.getKey()),pre.get(column(field.getKey())))),"Live original DTO preimage mismatch id="+id+" field="+field.getKey());
            require(pre.get("eps_diluted")==null,"Live nonnull EPS cannot be overwritten");
            var f=fact(n);var existing=existingFact(c,f,false);require(existing==null||equalCore(existing,f,FACT_CORE),"Existing raw EPS fact conflict");
            plans.add(new Plan(o.deepCopy(),n.deepCopy(),pre,f,existing));
        }
        require(seen.size()==byKey.size(),"Missing recovered keys");plans.sort(Comparator.comparingLong(p->p.original().path("id").asLong()));return plans;
    }
    static void insertFact(Connection c,Map<String,String> f)throws Exception{
        String columns=String.join(",",FACT_CORE.stream().map(k->k.equals("value")?"`value`":k).toList());
        try(var p=c.prepareStatement("INSERT INTO stock_sec_fact("+columns+",fetched_at) VALUES("+String.join(",",Collections.nCopies(FACT_CORE.size(),"?"))+",CURRENT_TIMESTAMP)")){
            for(int i=0;i<FACT_CORE.size();i++)p.setString(i+1,f.get(FACT_CORE.get(i)));require(p.executeUpdate()==1,"Raw insert count");
        }
    }
    static void execute(Connection c,List<Plan> plans,boolean rollback,int failAfter)throws Exception{
        require(c.getAutoCommit(),"Transaction already active");c.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);c.setAutoCommit(false);
        try{
            // Lock and verify every preimage before the first mutation.
            for(var p:plans){
                var live=standard(c,p.original().path("id").asLong(),true);require(Objects.equals(live,p.preimage()),"Standard preimage drift");
                var f=existingFact(c,p.fact(),true);require(Objects.equals(f,p.factPreimage()),"Raw preimage drift");require(f==null||equalCore(f,p.fact(),FACT_CORE),"Raw conflict");
            }
            int count=0;
            for(var p:plans){
                if(p.factPreimage()==null)insertFact(c,p.fact());
                var old=p.preimage();var keys=List.of("id","symbol","cik","fiscal_year","fiscal_period","form","accession_number","filed_at","start_date","end_date","period_days","currency","raw_json");
                String where=String.join(" AND ",keys.stream().map(k->k+"=?").toList());
                try(var sql=c.prepareStatement("UPDATE stock_sec_financial_standard SET eps_diluted=?,eps_unit=?,raw_json=? WHERE "+where+" AND eps_diluted IS NULL")){
                    sql.setBigDecimal(1,new BigDecimal(p.recovered().path("epsDiluted").asText()));sql.setString(2,"USD/shares");sql.setString(3,p.recovered().path("rawJson").asText());
                    for(int i=0;i<keys.size();i++)sql.setString(i+4,old.get(keys.get(i)));require(sql.executeUpdate()==1,"Guarded update count");
                }
                var actual=standard(c,p.original().path("id").asLong(),false);var expected=new TreeMap<>(old);expected.put("eps_diluted",new BigDecimal(p.recovered().path("epsDiluted").asText()).setScale(6).toPlainString());expected.put("eps_unit","USD/shares");expected.put("raw_json",p.recovered().path("rawJson").asText());
                require(equalCore(actual,expected,new ArrayList<>(expected.keySet())),"Postimage changed non-EPS columns");
                require(equalCore(existingFact(c,p.fact(),false),p.fact(),FACT_CORE),"Raw EPS postimage mismatch");
                if(++count==failAfter)throw new IllegalStateException("INJECTED_AFTER_UPDATE_"+count);
            }
            if(rollback){
                c.rollback();
                for(var p:plans){require(Objects.equals(standard(c,p.original().path("id").asLong(),false),p.preimage()),"Rollback standard preimage mismatch");require(Objects.equals(existingFact(c,p.fact(),false),p.factPreimage()),"Rollback raw preimage mismatch");}
            }else c.commit();
        }catch(Exception failure){c.rollback();throw failure;}finally{c.setAutoCommit(true);}
    }
    static List<ObjectNode> readPack(Path file)throws Exception{var list=new ArrayList<ObjectNode>();for(var symbol:J.readTree(Files.readString(file)).properties())for(var row:symbol.getValue()){require(symbol.getKey().equals(row.path("symbol").asText()),"Pack symbol mismatch");list.add((ObjectNode)row);}return list;}
    static String database(Connection c)throws Exception{
        if(c.getMetaData().getDatabaseProductName().equals("MariaDB"))try(var s=c.createStatement();var r=s.executeQuery("SELECT CONCAT(@@hostname,':',@@port,'/',DATABASE())")){require(r.next(),"Database identity absent");return r.getString(1);}
        return c.getMetaData().getURL().split("[;?]",2)[0]+"/"+c.getCatalog();
    }
    static void requireTransactionalTables(Connection c)throws Exception{
        if(!c.getMetaData().getDatabaseProductName().equals("MariaDB"))return;
        int count=0;try(var p=c.prepareStatement("SELECT TABLE_NAME,ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN ('stock_sec_financial_standard','stock_sec_fact')");var r=p.executeQuery()){
            while(r.next()){require("InnoDB".equalsIgnoreCase(r.getString("ENGINE")),"Atomic recovery requires InnoDB: "+r.getString("TABLE_NAME"));count++;}
        }require(count==2,"Both transactional target tables required");
    }
    static Map<String,String> codeHashes()throws Exception{
        var out=new TreeMap<String,String>();for(String file:List.of("scripts/QuantEpsRecoveryApply.java","scripts/QuantEpsRecoveryPrepare.java"))out.put(file,QuantEpsRecoveryPrepare.sha(Path.of(file)));
        for(Class<?> type:List.of(QuantEpsRecoveryApply.class,QuantEpsRecoveryPrepare.class))try(var in=type.getResourceAsStream("/"+type.getName()+".class")){require(in!=null,"Missing running class");out.put(type.getName()+".class",HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(in.readAllBytes())));}
        return out;
    }
    static void write(Path p,Object v)throws Exception{Files.writeString(p,J.writerWithDefaultPrettyPrinter().writeValueAsString(v),StandardOpenOption.CREATE_NEW);}
    static void prepare(Connection c,Path input,Path output)throws Exception{
        require(!Files.exists(output),"Output exists");Path old=input.resolve("original-selected-standards.json"),fresh=input.resolve("eps-recovered-selected-standards.json");
        String oldHash=QuantEpsRecoveryPrepare.sha(old),freshHash=QuantEpsRecoveryPrepare.sha(fresh);c.setReadOnly(true);c.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);c.setAutoCommit(false);
        try{
            requireTransactionalTables(c);
            var plans=plan(c,readPack(old),readPack(fresh));require(!plans.isEmpty(),"No live-ID repairs");
            require(oldHash.equals(QuantEpsRecoveryPrepare.sha(old))&&freshHash.equals(QuantEpsRecoveryPrepare.sha(fresh)),"Input pack drift");
            Files.createDirectory(output);write(output.resolve("backup-and-plans.json"),plans);
            var manifest=new LinkedHashMap<String,Object>();manifest.put("policy","EPS_ONLY_GUARDED_EXISTING_IDS_INSERT_MISSING_EPS_FACTS_V1");manifest.put("database",database(c));manifest.put("codeHashes",codeHashes());
            manifest.put("oldPack",old.toAbsolutePath().toString());manifest.put("oldPackSha256",oldHash);manifest.put("recoveredPack",fresh.toAbsolutePath().toString());manifest.put("recoveredPackSha256",freshHash);
            manifest.put("backupFile","backup-and-plans.json");manifest.put("backupSha256",QuantEpsRecoveryPrepare.sha(output.resolve("backup-and-plans.json")));manifest.put("targetRows",plans.size());manifest.put("missingRawFacts",plans.stream().filter(p->p.factPreimage()==null).count());
            manifest.put("preparedUtc",java.time.Instant.now().toString());write(output.resolve("manifest.json"),manifest);write(output.resolve("manifest.sha256.json"),Map.of("sha256",QuantEpsRecoveryPrepare.sha(output.resolve("manifest.json"))));
            System.out.println("READ_ONLY_PREPARED targets="+plans.size()+" manifestSHA="+QuantEpsRecoveryPrepare.sha(output.resolve("manifest.json")));
        }finally{c.rollback();c.setAutoCommit(true);c.setReadOnly(false);}
    }
    static void apply(Connection c,Path manifestPath,String expectedHash,boolean probe)throws Exception{
        require(QuantEpsRecoveryPrepare.sha(manifestPath).equalsIgnoreCase(expectedHash),"External expected manifest SHA mismatch");var m=J.readTree(Files.readString(manifestPath));
        require(m.path("policy").asText().equals("EPS_ONLY_GUARDED_EXISTING_IDS_INSERT_MISSING_EPS_FACTS_V1"),"Wrong policy");require(database(c).equals(m.path("database").asText()),"Wrong database");
        requireTransactionalTables(c);
        require(J.valueToTree(codeHashes()).equals(m.path("codeHashes")),"Code changed since prepare");
        Path root=manifestPath.toAbsolutePath().normalize().getParent();require(m.path("backupFile").asText().equals("backup-and-plans.json"),"Unsafe backup path");Path backup=root.resolve("backup-and-plans.json");
        require(QuantEpsRecoveryPrepare.sha(backup).equals(m.path("backupSha256").asText()),"Backup SHA mismatch");
        for(String[] pair:new String[][]{{"oldPack","oldPackSha256"},{"recoveredPack","recoveredPackSha256"}})require(QuantEpsRecoveryPrepare.sha(Path.of(m.path(pair[0]).asText())).equals(m.path(pair[1]).asText()),"Pack SHA mismatch");
        var plans=new ArrayList<Plan>();var archives=new HashMap<String,JsonNode>();for(var n:J.readTree(Files.readString(backup))){var p=J.treeToValue(n,Plan.class);validatePair(p.original(),p.recovered(),archives);require(fact(p.recovered()).equals(p.fact()),"Prepared fact mismatch");plans.add(p);}
        require(plans.size()==m.path("targetRows").asInt(),"Target count mismatch");
        var intent=root.resolve((probe?"probe":"apply")+"-intent-"+UUID.randomUUID()+".json");write(intent,Map.of("manifestSha256",expectedHash,"targets",plans.size(),"database",database(c),"note","Intent is not commit evidence"));
        boolean returned=false;
        try{execute(c,plans,probe,-1);returned=true;System.out.println(probe?"ROLLBACK_PROBE_SUCCEEDED":"COMMIT_RETURNED_SUCCESSFULLY");write(root.resolve((probe?"probe-rolled-back-":"commit-returned-")+UUID.randomUUID()+".json"),Map.of("manifestSha256",expectedHash,"committed",!probe,"targets",plans.size(),"recordedUtc",java.time.Instant.now().toString()));}
        catch(Exception failure){System.err.println(returned?"DB operation returned successfully; receipt write failed. Do not retry blindly.":"No confirmed commit. Inspect live postimage if transport/commit outcome was uncertain.");throw failure;}
    }
    public static void main(String[] args)throws Exception{
        require(args.length==3&&(args[0].equals("--prepare")||args[0].equals("--apply")||args[0].equals("--probe")),"Usage: --prepare INPUT_PACK NEW_DIRECTORY | --apply MANIFEST EXPECTED_SHA256 | --probe MANIFEST EXPECTED_SHA256");
        try(var c=DriverManager.getConnection(System.getenv().getOrDefault("DB_URL","jdbc:mariadb://localhost:3306/king_yurina"),System.getenv().getOrDefault("DB_USERNAME","root"),System.getenv().getOrDefault("DB_PASSWORD",""))){
            // DTO mappedAt values came from the application's server-local JDBC session.
            // Keep that same TIMESTAMP representation; never bypass a preimage check by dropping it.
            try(var s=c.createStatement()){s.execute("SET SESSION time_zone = 'SYSTEM'");s.execute("SET SESSION innodb_lock_wait_timeout=30");}
            if(args[0].equals("--prepare"))prepare(c,Path.of(args[1]),Path.of(args[2]));else apply(c,Path.of(args[1]),args[2],args[0].equals("--probe"));
        }
    }
}
