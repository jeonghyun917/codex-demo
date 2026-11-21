import com.kingyurina.demo.CodexDemoApplication;
import com.kingyurina.demo.stock.*;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.*;
import tools.jackson.databind.*;

/** Operator-only additive source table. Never updates SEC facts/standards, model weights or forecasts. */
public final class QuantImportSourceFinancial {
    static final ObjectMapper JSON=new ObjectMapper();
    static void validateReviewedArtifact(JsonNode body){
        // This version admits the independently reviewed extraction, not arbitrary self-consistent proof assertions.
        if(!StockSourceFinancialService.VERSION.equals(body.path("version").asText())||body.path("rows").size()!=8
                ||!"dd0a028de57e6261485cb36393a82ba2fb15bd39293932a90de5b46251fc1912".equals(body.path("sourcePackSha256").asText()))
            throw new IllegalArgumentException("Only the exact reviewed8-source extraction pack qualifies for this version");
    }
    static String sha(byte[] raw)throws Exception{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(raw));}
    static void create(Path path,Object body)throws Exception{Files.writeString(path,JSON.writerWithDefaultPrettyPrinter().writeValueAsString(body),StandardOpenOption.CREATE_NEW);}
    static List<Map<String,String>> rows(Connection c,String sql)throws Exception{
        var result=new ArrayList<Map<String,String>>();try(var s=c.createStatement();var r=s.executeQuery(sql)){
            var meta=r.getMetaData();while(r.next()){var row=new LinkedHashMap<String,String>();
                for(int i=1;i<=meta.getColumnCount();i++)row.put(meta.getColumnLabel(i),r.getString(i));result.add(row);}}
        return result;
    }
    static String sourceRows(){return "SELECT * FROM stock_source_financial_document ORDER BY pack_version,identity_sha256";}
    static String legacyRows(){return "SELECT * FROM stock_sec_financial_standard WHERE symbol IN ('AMGN','GS') ORDER BY id";}
    static void verified(Path artifact)throws Exception{
        String digest=sha(Files.readAllBytes(artifact));
        var process=new ProcessBuilder("python","-X","utf8","scripts/quant_source_operating.py","--verify",artifact.toString(),"--expected-sha",digest)
            .redirectErrorStream(true).start();
        if(!process.waitFor(30,TimeUnit.SECONDS)){process.destroyForcibly();throw new IllegalStateException("Source validation timeout; no DB writes");}
        String output=new String(process.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
        if(process.exitValue()!=0||!output.contains("SOURCE_ADMISSION_VERIFIED "+digest)||!digest.equals(sha(Files.readAllBytes(artifact))))
            throw new IllegalArgumentException("Original source validation failed before DB connection");
    }
    public static void main(String[] args)throws Exception{
        if(args.length!=3||!Set.of("--install","--rollback-probe","--apply","--probe").contains(args[0]))
            throw new IllegalArgumentException("mode admission.json NEW_OUTPUT_DIRECTORY");
        Path artifact=Path.of(args[1]).toAbsolutePath(),out=Path.of(args[2]);
        var body=JSON.readTree(Files.readAllBytes(artifact));
        validateReviewedArtifact(body);verified(artifact);
        if(Files.exists(out))throw new IllegalArgumentException("Preserve existing output");Files.createDirectories(out);
        if(args[0].equals("--probe")){probe(body,out);return;}
        try(var c=DriverManager.getConnection(System.getenv().getOrDefault("DB_URL","jdbc:mariadb://localhost:3306/king_yurina"),
                System.getenv().getOrDefault("DB_USERNAME","root"),System.getenv().getOrDefault("DB_PASSWORD",""))){
            var legacyBefore=rows(c,legacyRows());create(out.resolve("legacy-preimage.json"),legacyBefore);
            if(args[0].equals("--install")){
                var before=rows(c,"SELECT COUNT(*) n FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='stock_source_financial_document'");
                String ddl=Files.readString(Path.of("src/main/resources/stock-source-financial-schema.sql"));
                create(out.resolve("schema-preimage.json"),Map.of("existing",before,"ddlSha256",sha(ddl.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                    "rollback","Leave additive table intact; disable source feature. Never drop an object possibly used by a later run."));
                try(var s=c.createStatement()){s.execute(ddl);}
                if(!legacyBefore.equals(rows(c,legacyRows())))throw new IllegalStateException("Legacy changed during schema install");
                create(out.resolve("receipt.json"),Map.of("status","ADDITIVE_TABLE_INSTALLED","legacyStandardWrites",false));return;
            }
            c.setAutoCommit(false);
            try{
                var before=rows(c,sourceRows());create(out.resolve("preimage.json"),before);
                if(before.stream().anyMatch(r->StockSourceFinancialService.VERSION.equals(r.get("pack_version"))))
                    throw new IllegalStateException("Version already admitted; never replace first admission timestamp");
                LocalDateTime admitted=LocalDateTime.now(ZoneOffset.UTC);
                try(var s=c.prepareStatement("INSERT INTO stock_source_financial_document(pack_version,identity_sha256,symbol,publication_date,admitted_at_utc,payload_sha256,payload_json) VALUES(?,?,?,?,?,?,?)")){
                    for(var row:body.path("rows")){
                        String payload=JSON.writeValueAsString(row.path("candidate"));s.setString(1,StockSourceFinancialService.VERSION);
                        s.setString(2,row.path("identitySha256").asText());s.setString(3,row.path("symbol").asText());
                        s.setDate(4,java.sql.Date.valueOf(row.path("publicationDate").asText()));s.setObject(5,admitted);
                        s.setString(6,sha(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8)));s.setString(7,payload);s.addBatch();
                    }s.executeBatch();
                }
                var after=rows(c,sourceRows());
                var newRows=after.stream().filter(r->StockSourceFinancialService.VERSION.equals(r.get("pack_version"))).toList();
                if(newRows.size()!=8||!legacyBefore.equals(rows(c,legacyRows())))throw new IllegalStateException("Import postimage/legacy mismatch");
                for(var row:newRows){
                    if(!sha(row.get("payload_json").getBytes(java.nio.charset.StandardCharsets.UTF_8)).equals(row.get("payload_sha256")))
                        throw new IllegalStateException("Stored payload hash mismatch");
                    var expected=body.path("rows").valueStream().filter(r->r.path("identitySha256").asText().equals(row.get("identity_sha256"))).findFirst().orElseThrow();
                    if(!expected.path("candidate").equals(JSON.readTree(row.get("payload_json"))))throw new IllegalStateException("Stored source payload mismatch");
                }
                create(out.resolve("postimage.json"),after);
                if(args[0].equals("--rollback-probe")){
                    c.rollback();if(!before.equals(rows(c,sourceRows())))throw new IllegalStateException("Rollback preimage mismatch");
                }else c.commit();
                create(out.resolve("receipt.json"),Map.of("status",args[0].equals("--apply")?"COMMITTED":"ROLLBACK_VERIFIED",
                    "admittedAtUtc",admitted.toString()+"Z","sourceRows",8,"artifactSha256",sha(Files.readAllBytes(artifact)),
                    "legacyStandardWrites",false,"forecastWrites",false,"modelWeightsChanged",false));
            }catch(Exception error){c.rollback();throw error;}
        }
    }
    static void probe(JsonNode body,Path out)throws Exception{
        var app=new SpringApplication(CodexDemoApplication.class);app.setWebApplicationType(WebApplicationType.NONE);
        try(var ctx=app.run("--spring.profiles.active=mariadb","--spring.sql.init.mode=never","--app.signal.refresh.enabled=false",
                "--app.signal.refresh.run-on-startup=false","--app.quant.source-financial.enabled=true",
                "--spring.datasource.hikari.connection-init-sql=SET SESSION TRANSACTION READ ONLY")){
            var mapper=ctx.getBean(HistoricalBacktestSeedMapper.class);var service=ctx.getBean(StockSourceFinancialService.class);
            var checks=new ArrayList<Map<String,Object>>();
            for(var candidate:body.path("rows")){
                var id=candidate.path("candidate").path("canonicalIdentity");String symbol=id.path("symbol").asText();
                LocalDate publicDate=LocalDate.parse(id.path("publicationDate").asText()),next=publicDate.plusDays(1);
                var before=mapper.findLatestQuarterAsOf(symbol,publicDate);var after=mapper.findLatestQuarterAsOf(symbol,next);
                var beforeSelected=service.resolveQuarter(symbol,publicDate,null,before,true);
                var selected=service.resolveQuarter(symbol,next,null,after,true);
                var item=new LinkedHashMap<String,Object>();item.put("symbol",symbol);item.put("publicationDate",publicDate.toString());
                item.put("sameDaySelectedSource",beforeSelected!=before);item.put("nextDaySourceSelected",selected!=after);
                item.put("selectedQuarter",selected);item.put("legacyQuarter",after);checks.add(item);
                if(selected==null||selected.getRawJson()==null||!selected.getRawJson().contains(StockSourceFinancialService.VERSION))
                    throw new IllegalStateException("Expected source not selected after publication: "+candidate.path("identitySha256"));
                // Other older documents may legitimately already apply on a later publication date.
                if(selected.getRevenue().compareTo(new java.math.BigDecimal(candidate.path("candidate").path("values").path("revenue").asText()))!=0)
                    throw new IllegalStateException("Selected revenue differs from proved source");
            }
            var live=new LinkedHashMap<String,Object>();var cache=ctx.getBean(StockCacheService.class);var legacyMapper=ctx.getBean(SecFinancialStandardMapper.class);
            for(String symbol:List.of("AMGN","GS"))live.put(symbol,Map.of("legacyQuarter",legacyMapper.findLatestQuarter(symbol),"selectedQuarter",cache.findLatestSecQuarterFinancial(symbol)));
            create(out.resolve("read-path-proof.json"),Map.of("policyVersion",StockSourceFinancialService.VERSION,"checks",checks,
                "liveLatest",live,"databaseReadOnly",true,"forecastsGenerated",false,"errorReductionProved",false));
        }
    }
}
