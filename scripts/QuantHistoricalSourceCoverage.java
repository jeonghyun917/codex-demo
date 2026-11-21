import com.kingyurina.demo.stock.*;
import tools.jackson.databind.ObjectMapper;
import java.lang.reflect.*;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;

/** Offline read-only snapshot and existing-parser/standardizer compatibility evidence. No Spring. */
public final class QuantHistoricalSourceCoverage {
    static final ObjectMapper JSON = new ObjectMapper();
    static final Map<String,String> SOURCES = Map.of(
        "ANSS","tmp/quant-older-inputs-20261018/ANSS-facts.json",
        "MDB","tmp/quant-older-inputs-20261018/MDB-facts.json",
        "ILMN","tmp/quant-older-inputs-20261018/ILMN-facts.json",
        "TEAM","tmp/quant-team-inputs-20261018/team-facts.json",
        "AZN","tmp/quant-extended-inputs-20261018/AZN-facts.json",
        "GFS","tmp/quant-extended-inputs-20261018/GFS-facts.json",
        "CCEP","tmp/quant-march-foreign-evidence-20261031/network-approved/CCEP-facts.json",
        "FER","tmp/quant-march-foreign-evidence-20261031/network-approved/FER-facts.json",
        "TRI","tmp/quant-march-foreign-evidence-20261031/network-approved/TRI-facts.json");
    static String env(String key,String fallback){return System.getenv().getOrDefault(key,fallback);}
    static void write(Path path,Object value)throws Exception{
        Files.writeString(path,JSON.writerWithDefaultPrettyPrinter().writeValueAsString(value),StandardOpenOption.CREATE_NEW);
    }
    static List<Map<String,String>> query(Connection c,String sql,String symbol)throws Exception{
        var out=new ArrayList<Map<String,String>>();
        try(var ps=c.prepareStatement(sql)){ps.setString(1,symbol);try(var rs=ps.executeQuery()){
            var md=rs.getMetaData();while(rs.next()){var row=new LinkedHashMap<String,String>();
                for(int i=1;i<=md.getColumnCount();i++)row.put(md.getColumnLabel(i),rs.getString(i));out.add(row);
            }
        }}return out;
    }
    public static void main(String[] args)throws Exception{
        if(args.length!=1)throw new IllegalArgumentException("Usage: NEW_OUTPUT_DIRECTORY");
        var out=Path.of(args[0]);if(Files.exists(out))throw new IllegalStateException("Refuse overwrite");Files.createDirectories(out);
        var properties=new SecEdgarProperties("","","",0,Integer.MAX_VALUE,0,true);
        var parser=new SecEdgarService(properties,null,null,JSON,null,null,null);
        var parse=SecEdgarService.class.getDeclaredMethod("parseFacts",String.class,String.class,String.class);parse.setAccessible(true);
        var standardizer=new SecFinancialStandardService(null,null,JSON);
        var standardize=SecFinancialStandardService.class.getDeclaredMethod("standardize",String.class,String.class,List.class);standardize.setAccessible(true);
        var before=new TreeMap<String,Object>();var metadata=new TreeMap<String,Object>();
        try(var c=DriverManager.getConnection(env("DB_URL","jdbc:mariadb://localhost:3306/king_yurina"),env("DB_USERNAME","root"),env("DB_PASSWORD",""))){
            try(var s=c.createStatement()){s.execute("SET SESSION time_zone = '+00:00'");s.execute("SET SESSION TRANSACTION READ ONLY");}
            c.setReadOnly(true);c.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);c.setAutoCommit(false);
            for(var entry:new TreeMap<>(SOURCES).entrySet()){
                String symbol=entry.getKey(),body=Files.readString(Path.of(entry.getValue()));
                String cik=JSON.readTree(body).path("cik").asText();
                var existing=query(c,"SELECT * FROM stock_sec_fact WHERE symbol=? ORDER BY id",symbol);
                var standards=query(c,"SELECT * FROM stock_sec_financial_standard WHERE symbol=? ORDER BY id",symbol);
                write(out.resolve(symbol+"-db-facts.json"),existing);write(out.resolve(symbol+"-db-standards.json"),standards);
                @SuppressWarnings("unchecked")var parsed=(List<SecCompanyFact>)parse.invoke(parser,symbol,cik,body);
                // This timestamp marks local parsing now, never receipt before historical decisions.
                for(var f:parsed)f.setFetchedAt(LocalDateTime.ofInstant(Instant.now(),ZoneOffset.UTC));
                write(out.resolve(symbol+"-current-parser-facts.json"),parsed);
                @SuppressWarnings("unchecked")var candidates=(List<SecFinancialStandard>)standardize.invoke(standardizer,symbol,cik,parsed);
                write(out.resolve(symbol+"-current-standardizer-candidates.json"),candidates);
                var fp=query(c,"SELECT COUNT(*) AS rows_count,COALESCE(SUM(CRC32(CONCAT_WS('|',id,raw_json))),0) AS fingerprint FROM stock_sec_fact WHERE symbol=?",symbol);
                before.put(symbol,fp);metadata.put(symbol,Map.of("source",entry.getValue(),"cik",cik,"dbFacts",existing.size(),"dbStandards",standards.size(),"parsedFacts",parsed.size(),"candidateStandards",candidates.size()));
                System.out.println(symbol+" dbFacts="+existing.size()+" parserFacts="+parsed.size()+" currentStandardizerRows="+candidates.size());
            }
            for(String symbol:List.of("ALNY","ARM","ASML","INSM","MELI","MRVL","MSTR","PDD","SHOP","ZS")){
                var existing=query(c,"SELECT * FROM stock_sec_fact WHERE symbol=? ORDER BY id",symbol);
                var standards=query(c,"SELECT * FROM stock_sec_financial_standard WHERE symbol=? ORDER BY id",symbol);
                write(out.resolve(symbol+"-db-facts.json"),existing);write(out.resolve(symbol+"-db-standards.json"),standards);
                write(out.resolve(symbol+"-current-standardizer-candidates.json"),List.of());
                before.put(symbol,query(c,"SELECT COUNT(*) AS rows_count,COALESCE(SUM(CRC32(CONCAT_WS('|',id,raw_json))),0) AS fingerprint FROM stock_sec_fact WHERE symbol=?",symbol));
                metadata.put(symbol,Map.of("source","NATIVE_DB_ONLY","dbFacts",existing.size(),"dbStandards",standards.size()));
            }
            // Same read-only repeatable-read snapshot: no claim about concurrently written market tables.
            var after=new TreeMap<String,Object>();
            for(String symbol:before.keySet())after.put(symbol,query(c,"SELECT COUNT(*) AS rows_count,COALESCE(SUM(CRC32(CONCAT_WS('|',id,raw_json))),0) AS fingerprint FROM stock_sec_fact WHERE symbol=?",symbol));
            if(!before.equals(after))throw new IllegalStateException("Source snapshot drift");
            write(out.resolve("snapshot.json"),Map.of("classification","READ_ONLY_SOURCE_COMPATIBILITY_NOT_IMPORTED","createdAt",Instant.now().toString(),"databaseWrites",0,"before",before,"after",after,"issuers",metadata,
                "codeHashes",Map.of("SecEdgarService",sha(Path.of("target/classes/com/kingyurina/demo/stock/SecEdgarService.class")),"SecFinancialStandardService",sha(Path.of("target/classes/com/kingyurina/demo/stock/SecFinancialStandardService.class")))));
            c.rollback();
        }
    }
    static String sha(Path p)throws Exception{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p)));}
}
