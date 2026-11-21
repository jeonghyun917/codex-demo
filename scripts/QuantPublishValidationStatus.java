import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Scoped batch publisher. No application boot, collection, schema changes or forecast updates. */
public final class QuantPublishValidationStatus {
    static final String VERSION="QUANT_FORWARD_VALIDATION_STATUS_V1";
    static final String SELECT="SELECT index_code,view_version,generated_at,source,payload_json FROM stock_dashboard_view_snapshot WHERE index_code=? AND view_version=?";
    static void validate(JsonNode body) throws Exception {
        if(!"QUANT_VALIDATION_STATUS_PUBLICATION_V1".equals(body.path("format").asText())
                || !body.path("promotionAllowed").isBoolean() || body.path("promotionAllowed").asBoolean())
            throw new IllegalArgumentException("Unsupported or promoting status publication");
        Instant at=Instant.parse(body.path("generatedAtUtc").asText());
        if(at.isAfter(Instant.now()))throw new IllegalArgumentException("Future publication");
        var indices=new HashSet<String>();
        for(var p:body.path("payloads")){
            String index=p.path("indexCode").asText();
            if(!indices.add(index)||!VERSION.equals(p.path("format").asText())
                    ||!body.path("generatedAtUtc").asText().equals(p.path("generatedAtUtc").asText())
                    ||!p.path("promotionAllowed").isBoolean()||p.path("promotionAllowed").asBoolean())
                throw new IllegalArgumentException("Invalid status identity");
            var f=p.path("forward");
            if(f.path("maturedCohorts").asInt(-1)==0&&(!f.path("rawMae").isNull()||!f.path("excessMae").isNull()))
                throw new IllegalArgumentException("Pending score cannot be published");
        }
        if(!indices.equals(Set.of("DOW30","SP500","NASDAQ100")))throw new IllegalArgumentException("All3 separate indices required");
        for(var evidence:body.path("evidence")){
            if(!sha(Path.of(evidence.path("path").asText())).equals(evidence.path("sha256").asText()))
                throw new IllegalArgumentException("Publication evidence changed");
        }
    }
    static String sha(Path path)throws Exception{
        return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }
    static List<Map<String,Object>> rows(Connection c, boolean lock)throws Exception{
        var result=new ArrayList<Map<String,Object>>();
        for(String index:List.of("DOW30","SP500","NASDAQ100"))try(var s=c.prepareStatement(SELECT+(lock?" FOR UPDATE":""))){
            s.setString(1,index);s.setString(2,VERSION);
            try(var r=s.executeQuery()){if(r.next()){
                var row=new LinkedHashMap<String,Object>();row.put("indexCode",r.getString(1));row.put("viewVersion",r.getString(2));
                row.put("generatedAt",r.getTimestamp(3).toInstant().toString());row.put("source",r.getString(4));row.put("payloadJson",r.getString(5));result.add(row);
            }}
        }
        return result;
    }
    static void create(ObjectMapper json,Path file,Object body)throws Exception{
        Files.writeString(file,json.writerWithDefaultPrettyPrinter().writeValueAsString(body),StandardOpenOption.CREATE_NEW);
    }
    public static void main(String[] args)throws Exception{
        if(args.length!=3||!Set.of("--apply","--rollback-probe").contains(args[0]))
            throw new IllegalArgumentException("--apply|--rollback-probe publication.json NEW_OUTPUT_DIRECTORY");
        var json=new ObjectMapper();Path input=Path.of(args[1]),out=Path.of(args[2]);
        String inputSha=sha(input);
        var body=json.readTree(Files.readAllBytes(input));validate(body);
        // A list of unchanged evidence files alone does not prove that MAE/counts were derived from them.
        // Rebuild with the single canonical Python implementation before opening a writable DB transaction.
        var verifier=new ProcessBuilder("python","-X","utf8","scripts/quant_validation_status.py",
                "--verify-publication",input.toAbsolutePath().toString(),"--expected-sha",inputSha)
                .redirectErrorStream(true).start();
        if(!verifier.waitFor(30,java.util.concurrent.TimeUnit.SECONDS)){
            verifier.destroyForcibly();throw new IllegalStateException("Publication verification timed out; no DB writes");
        }
        String verification=new String(verifier.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
        if(verifier.exitValue()!=0||!verification.contains("PUBLICATION_VERIFIED "+inputSha)||!inputSha.equals(sha(input)))
            throw new IllegalArgumentException("Publication seal/rebuild verification failed; no DB writes: "+verification.substring(0,Math.min(512,verification.length())));
        if(Files.exists(out))throw new IllegalArgumentException("Create-only output required");
        Files.createDirectories(out);
        String url=System.getenv().getOrDefault("DB_URL","jdbc:mariadb://localhost:3306/king_yurina");
        try(var c=DriverManager.getConnection(url,System.getenv().getOrDefault("DB_USERNAME","root"),System.getenv().getOrDefault("DB_PASSWORD",""))){
            c.setAutoCommit(false);
            try{
                var before=rows(c,true);create(json,out.resolve("preimage.json"),before);
                for(var old:before){
                    Instant oldAt=Instant.parse(json.readTree((String)old.get("payloadJson")).path("generatedAtUtc").asText());
                    if(!oldAt.isBefore(Instant.parse(body.path("generatedAtUtc").asText())))
                        throw new IllegalArgumentException("Refuse same/older publication; preserve existing status");
                }
                try(var s=c.prepareStatement("INSERT INTO stock_dashboard_view_snapshot(index_code,view_version,generated_at,source,payload_json) VALUES(?,?,?,?,?) ON DUPLICATE KEY UPDATE generated_at=VALUES(generated_at),source=VALUES(source),payload_json=VALUES(payload_json)")){
                    for(var p:body.path("payloads")){
                        s.setString(1,p.path("indexCode").asText());s.setString(2,VERSION);
                        s.setTimestamp(3,Timestamp.from(Instant.parse(body.path("generatedAtUtc").asText())));
                        s.setString(4,"SEALED_FORWARD_EVALUATION_BATCH");s.setString(5,json.writeValueAsString(p));s.addBatch();
                    }s.executeBatch();
                }
                var after=rows(c,false);
                if(after.size()!=3)throw new IllegalStateException("Publication lost status rows");
                for(var row:after){
                    var expected=body.path("payloads").valueStream().filter(p->p.path("indexCode").asText().equals(row.get("indexCode"))).findFirst().orElseThrow();
                    if(!expected.equals(json.readTree((String)row.get("payloadJson"))))throw new IllegalStateException("Postimage mismatch");
                }
                create(json,out.resolve("postimage.json"),after);
                if("--rollback-probe".equals(args[0])){
                    c.rollback();
                    if(!before.equals(rows(c,false)))throw new IllegalStateException("Rollback differs from preimage");
                }else c.commit();
                create(json,out.resolve("receipt.json"),Map.of("status",args[0].equals("--apply")?"COMMITTED":"ROLLBACK_VERIFIED",
                    "publicationSha256",sha(input),"version",VERSION,"scopedRows",3,"forecastWrites",false,"schemaWrites",false,"promotion",false));
            }catch(Exception ex){c.rollback();throw ex;}
        }
    }
}
