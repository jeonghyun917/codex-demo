import java.nio.file.Path;
import tools.jackson.databind.node.ObjectNode;

/** Standalone literal fixtures: rejects broad DTO replacement and unproved EPS. */
public final class QuantEpsRecoveryPrepareTest {
    static final String OBS="{\"start\":\"2015-09-01\",\"end\":\"2016-08-31\",\"val\":3.82,\"accn\":\"a\",\"fy\":2016,\"fp\":\"FY\",\"form\":\"10-K\",\"filed\":\"2016-10-20\"}";
    static ObjectNode old() {
        return (ObjectNode)QuantEpsRecoveryPrepare.JSON.readTree("""
            {"symbol":"WBA","cik":"0001618921","fiscalYear":2016,"fiscalPeriod":"FY","form":"10-K",
             "filedAt":"2016-10-20","startDate":"2015-09-01","endDate":"2016-08-31","accessionNumber":"a",
             "periodDays":366,"currency":"USD","epsDiluted":null,"epsUnit":null,"id":42,
             "revenue":123,"netIncome":null,"mappedAt":"2026-10-31T00:00:00","rawJson":"{\\"selectedFieldEvidence\\":{\\"revenue\\":{\\"legacy\\":true}},\\"candidateFieldEvidence\\":[]}"}
            """);
    }
    static ObjectNode full() {
        var n=old(); n.put("epsDiluted",3.82);n.put("epsUnit","USD/shares");n.put("revenue",999);n.put("netIncome",321);n.put("id",900);
        var raw=QuantEpsRecoveryPrepare.JSON.createObjectNode();
        var e=raw.putObject("selectedFieldEvidence").putObject("epsDiluted");
        e.put("concept","EarningsPerShareDiluted");e.put("taxonomy","us-gaap");e.put("unit","USD/shares");
        e.put("startDate","2015-09-01");e.put("endDate","2016-08-31");e.put("periodDays",366);e.put("sourceObservationJson",OBS);
        n.put("rawJson",raw.toString());return n;
    }
    static tools.jackson.databind.JsonNode archive() {
        return QuantEpsRecoveryPrepare.JSON.readTree("{\"cik\":1618921,\"facts\":{\"us-gaap\":{\"EarningsPerShareDiluted\":{\"units\":{\"USD/shares\":["+OBS+"]}}}}}");
    }
    static void check(boolean v,String m){if(!v)throw new AssertionError(m);}
    static String outputJoin(tools.jackson.databind.JsonNode n){
        return n.path("symbol").asText()+"/"+n.path("cik").asText()+"/"+n.path("accessionNumber").asText()+"/"+n.path("endDate").asText()+"/"+n.path("fiscalYear").asText()+"/"+n.path("fiscalPeriod").asText()+"/"+n.path("form").asText();
    }
    static void verifyOutputs(Path dir)throws Exception {
        var j=QuantEpsRecoveryPrepare.JSON;
        var original=j.readTree(java.nio.file.Files.readString(dir.resolve("original-selected-standards.json")));
        var recovered=j.readTree(java.nio.file.Files.readString(dir.resolve("eps-recovered-selected-standards.json")));
        var oldById=new java.util.HashMap<String,tools.jackson.databind.JsonNode>();
        for(var symbol:original.properties())for(var n:symbol.getValue())check(oldById.put(outputJoin(n),n)==null,"unique original natural keys (legacy archive IDs may be null)");
        var archives=new java.util.HashMap<String,tools.jackson.databind.JsonNode>();int rows=0,repairs=0;
        var seen=new java.util.HashSet<String>();
        for(var symbol:recovered.properties())for(var n:symbol.getValue()){
            String id=outputJoin(n);check(seen.add(id),"unique recovered natural keys");var before=oldById.get(id);check(before!=null,"no new row");rows++;
            if(before.path("epsDiluted").equals(n.path("epsDiluted"))){check(before.equals(n),"unchanged EPS means unchanged DTO");continue;}
            repairs++;check(before.path("epsDiluted").isNull(),"only repair null EPS");
            var b=(ObjectNode)before.deepCopy();var a=(ObjectNode)n.deepCopy();
            for(String f:new String[]{"epsDiluted","epsUnit","rawJson"}){b.remove(f);a.remove(f);}check(b.equals(a),"all non-EPS DTO fields identical");
            var oldRaw=(ObjectNode)j.readTree(before.path("rawJson").asText());var raw=(ObjectNode)j.readTree(n.path("rawJson").asText());
            var oldSelected=(ObjectNode)oldRaw.path("selectedFieldEvidence").deepCopy();var newSelected=(ObjectNode)raw.path("selectedFieldEvidence").deepCopy();
            oldSelected.remove("epsDiluted");newSelected.remove("epsDiluted");check(oldSelected.equals(newSelected),"all non-EPS selected evidence identical");
            check(oldRaw.path("candidateFieldEvidence").equals(raw.path("candidateFieldEvidence")),"legacy candidate receipts unchanged");
            String path=raw.at("/epsRecovery/sourcePath").asText();var archive=archives.get(path);
            if(archive==null){archive=j.readTree(java.nio.file.Files.readString(Path.of(path)));archives.put(path,archive);}
            var obs=j.readTree(raw.at("/selectedFieldEvidence/epsDiluted/sourceObservationJson").asText());boolean found=false;
            for(var source:archive.at("/facts/us-gaap/EarningsPerShareDiluted/units/USD~1shares"))if(source.equals(obs)){found=true;break;}
            check(found,"literal selected observation exists in actual original archive");
            check(new java.math.BigDecimal(n.path("epsDiluted").asText()).compareTo(new java.math.BigDecimal(obs.path("val").asText()))==0,"actual EPS equals primary value");
            for(String[] f:new String[][]{{"accessionNumber","accn"},{"filedAt","filed"},{"form","form"},{"startDate","start"},{"endDate","end"},{"fiscalYear","fy"},{"fiscalPeriod","fp"}})
                check(n.path(f[0]).asText().equals(obs.path(f[1]).asText()),"exact source identity "+f[0]);
        }
        check(rows==oldById.size(),"no removed rows");
        check(repairs==355,"literal verified recovery count355");
        for(String[] expected:new String[][]{{"AAPL","0001628280-16-020309","2016-09-24","8.31"},{"MSFT","0001564590-17-014900","2017-06-30","2.71"},{"JNJ","0000200406-19-000033","2019-03-31","1.39"}}){
            boolean matched=false;for(var n:recovered.path(expected[0]))if(n.path("accessionNumber").asText().equals(expected[1])&&n.path("endDate").asText().equals(expected[2])){
                check(new java.math.BigDecimal(expected[3]).compareTo(new java.math.BigDecimal(n.path("epsDiluted").asText()))==0,"first3 literal recovered EPS "+expected[0]);matched=true;
            }check(matched,"first3 row exists "+expected[0]);
        }
        System.out.println("PASS actual outputs: rows="+rows+" EPS-only repairs="+repairs+" primary observations verified individually; first3 literals8.31/2.71/1.39");
    }
    public static void main(String[] args)throws Exception {
        if(args.length==2){
            for(String name:new String[]{"original-selected-standards.json","eps-recovered-selected-standards.json","recovery-audit.json"}){
                var a=QuantEpsRecoveryPrepare.JSON.readTree(java.nio.file.Files.readString(Path.of(args[0],name)));
                var b=QuantEpsRecoveryPrepare.JSON.readTree(java.nio.file.Files.readString(Path.of(args[1],name)));
                check(a.equals(b),"deterministic semantic output "+name);
            }
            System.out.println("PASS all three fresh-run artifacts semantically identical (JSON object key order is irrelevant)");return;
        }
        if(args.length==1){verifyOutputs(Path.of(args[0]));return;}
        var old=old();var result=QuantEpsRecoveryPrepare.recover(old,full(),archive(),Path.of("literal.json"),"hash");
        check(result.path("epsDiluted").asText().equals("3.82"),"recover literal3.82 EPS");
        check(result.path("id").asInt()==42 && result.path("revenue").asInt()==123 && result.path("netIncome").isNull(),"preserve all non-EPS values/id");
        check(old.path("epsDiluted").isNull(),"do not mutate source DTO");
        var evidence=QuantEpsRecoveryPrepare.JSON.readTree(result.path("rawJson").asText());
        check(evidence.at("/selectedFieldEvidence/revenue/legacy").asBoolean(),"retain old non-EPS evidence");
        check(evidence.at("/selectedFieldEvidence/epsDiluted/sourceObservationJson").asText().equals(OBS),"retain exact source observation");
        for(String field:new String[]{"filedAt","startDate","endDate","accessionNumber","cik","currency","form","fiscalPeriod","fiscalYear","periodDays"}) {
            var bad=full();bad.put(field,"WRONG");
            check(QuantEpsRecoveryPrepare.recover(old,bad,archive(),Path.of("literal.json"),"hash").equals(old),"reject mismatched "+field);
        }
        var existing=old();existing.put("epsDiluted",7);existing.put("epsUnit","USD/shares");
        check(QuantEpsRecoveryPrepare.recover(existing,full(),archive(),Path.of("literal.json"),"hash").equals(existing),"preserve nonnull EPS even conflict");
        var noProof=archive().deepCopy();((ObjectNode)noProof).put("facts", "missing");
        check(QuantEpsRecoveryPrepare.recover(old,full(),noProof,Path.of("literal.json"),"hash").equals(old),"reject observation absent from archive");
        var future=full();var raw=(ObjectNode)QuantEpsRecoveryPrepare.JSON.readTree(future.path("rawJson").asText());
        ((ObjectNode)raw.at("/selectedFieldEvidence/epsDiluted")).put("sourceObservationJson",OBS.replace("2016-10-20","2021-01-01"));future.put("rawJson",raw.toString());
        check(QuantEpsRecoveryPrepare.recover(old,future,archive(),Path.of("literal.json"),"hash").equals(old),"reject changed source date");
        System.out.println("PASS EPS-only recovery literal/identity/proof/immutability checks");
    }
}
