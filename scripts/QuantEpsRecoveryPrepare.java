import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;
import com.kingyurina.demo.stock.*;

/** Offline, EPS-only selected-input recovery; never connects to a database. */
public final class QuantEpsRecoveryPrepare {
    static final ObjectMapper JSON = new ObjectMapper();
    static final LocalDate CUTOFF=LocalDate.of(2020,8,28);
    static final String[] IDENTITY={"symbol","fiscalYear","fiscalPeriod","form","filedAt","startDate","endDate","accessionNumber","periodDays","currency"};
    static String cik(JsonNode n){try{return new BigInteger(n.asText()).toString();}catch(RuntimeException e){return "INVALID";}}
    static String sha(Path p)throws Exception{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p)));}
    static void require(boolean v,String m){if(!v)throw new IllegalStateException(m);}
    static boolean sameIdentity(JsonNode a,JsonNode b){
        if(cik(a.path("cik")).equals("INVALID")||!cik(a.path("cik")).equals(cik(b.path("cik"))))return false;
        for(String f:IDENTITY)if(a.path(f).isNull()||a.path(f).isMissingNode()||!a.path(f).asText().equals(b.path(f).asText()))return false;
        return true;
    }
    static String natural(JsonNode n){return JSON.writeValueAsString(List.of(n.path("symbol").asText(),cik(n.path("cik")),n.path("fiscalYear").asText(),n.path("fiscalPeriod").asText(),n.path("form").asText(),n.path("accessionNumber").asText(),n.path("endDate").asText()));}
    static ObjectNode recover(ObjectNode old, ObjectNode full, JsonNode archive, Path source, String sha) {
        var out=old.deepCopy();
        try{
            if(!old.path("epsDiluted").isNull()||!sameIdentity(old,full)||full.path("epsDiluted").isNull()
                    ||!"USD".equals(old.path("currency").asText())||!"USD/shares".equals(full.path("epsUnit").asText())
                    ||!cik(old.path("cik")).equals(cik(archive.path("cik"))))return out;
            var start=LocalDate.parse(old.path("startDate").asText());var end=LocalDate.parse(old.path("endDate").asText());
            var filed=LocalDate.parse(old.path("filedAt").asText());long days=ChronoUnit.DAYS.between(start,end)+1;
            boolean annual=old.path("fiscalPeriod").asText().equals("FY");
            if(filed.isBefore(end)||filed.isAfter(CUTOFF)||days!=old.path("periodDays").asInt()
                    ||(annual?days<330||days>380:!old.path("fiscalPeriod").asText().matches("Q[1-4]")||days<70||days>110))return out;
            if(old.path("symbol").asText().equals("DIS")&&cik(old.path("cik")).equals("1001039")&&!filed.isBefore(LocalDate.of(2019,3,20)))return out;
            var fullRaw=JSON.readTree(full.path("rawJson").asText());var e=fullRaw.at("/selectedFieldEvidence/epsDiluted");
            if(!"us-gaap".equals(e.path("taxonomy").asText())||!"EarningsPerShareDiluted".equals(e.path("concept").asText())
                    ||!"USD/shares".equals(e.path("unit").asText())||!start.toString().equals(e.path("startDate").asText())
                    ||!end.toString().equals(e.path("endDate").asText())||days!=e.path("periodDays").asInt())return out;
            var observation=JSON.readTree(e.path("sourceObservationJson").asText());
            for(String[] f:new String[][]{{"startDate","start"},{"endDate","end"},{"accessionNumber","accn"},{"filedAt","filed"},{"form","form"},{"fiscalYear","fy"},{"fiscalPeriod","fp"}})
                if(!old.path(f[0]).asText().equals(observation.path(f[1]).asText()))return out;
            if(new BigDecimal(full.path("epsDiluted").asText()).compareTo(new BigDecimal(observation.path("val").asText()))!=0)return out;
            boolean found=false;
            for(var o:archive.at("/facts/us-gaap/EarningsPerShareDiluted/units/USD~1shares"))if(o.equals(observation)){found=true;break;}
            if(!found)return out;
            var raw=(ObjectNode)JSON.readTree(old.path("rawJson").asText());
            if(!raw.path("selectedFieldEvidence").isObject())return out;
            ((ObjectNode)raw.path("selectedFieldEvidence")).set("epsDiluted",e.deepCopy());
            raw.put("epsDilutedConcept","EarningsPerShareDiluted");raw.put("epsUnit","USD/shares");
            var receipt=raw.putObject("epsRecovery");receipt.put("policy","EPS_ONLY_EXACT_EXISTING_SELECTED_IDENTITY_V1");
            receipt.put("sourcePath",source.toString());receipt.put("sourceSha256",sha);
            receipt.put("originalRawJsonSha256",HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(old.path("rawJson").asText().getBytes(java.nio.charset.StandardCharsets.UTF_8))));
            receipt.put("sourceIsCurrentAcquisitionNotPitArchive",true);
            out.set("epsDiluted",full.path("epsDiluted").deepCopy());out.put("epsUnit","USD/shares");out.put("rawJson",raw.toString());
            var before=old.deepCopy();var after=out.deepCopy();for(String f:List.of("epsDiluted","epsUnit","rawJson")){before.remove(f);after.remove(f);}
            require(before.equals(after),"Non-EPS mutation");return out;
        }catch(Exception bad){return old.deepCopy();}
    }
    @SuppressWarnings("unchecked")
    static List<SecFinancialStandard> standards(String symbol,String sourceCik,String raw)throws Exception{
        var p=new SecEdgarProperties("offline.invalid","offline.invalid","offline",168,Integer.MAX_VALUE,0,false);
        var parser=new SecEdgarService(p,null,null,JSON,null,null,null);
        var parse=SecEdgarService.class.getDeclaredMethod("parseFacts",String.class,String.class,String.class);parse.setAccessible(true);
        var facts=(List<SecCompanyFact>)parse.invoke(parser,symbol,sourceCik,raw);
        facts=facts.stream().filter(f->f.getFiledAt()!=null&&!f.getFiledAt().isAfter(CUTOFF)).toList();
        var s=new SecFinancialStandardService(null,null,JSON);var m=SecFinancialStandardService.class.getDeclaredMethod("standardize",String.class,String.class,List.class);m.setAccessible(true);
        return (List<SecFinancialStandard>)m.invoke(s,symbol,sourceCik,facts);
    }
    static void write(Path p,Object value)throws Exception{Files.writeString(p,JSON.writerWithDefaultPrettyPrinter().writeValueAsString(value),StandardOpenOption.CREATE_NEW);}
    static List<ObjectNode> sources(Path root)throws Exception{
        var out=new ArrayList<ObjectNode>();
        for(String name:List.of("manifest-first3.json","manifest-rest.json"))for(var n:JSON.readTree(Files.readString(root.resolve(name))).path("records")){
            require(n.path("status").asText().equals("200")||n.path("status").asText().startsWith("REUSED"),"Unverified source status "+n);
            out.add((ObjectNode)n.deepCopy());
        }
        Path legacy=Path.of("tmp/quant-long-history-inputs-20261031-v1/DIS-legacy-companyfacts-original.json");
        var n=JSON.createObjectNode();n.put("symbol","DIS");n.put("cik","1001039");n.put("archivePath",legacy.toString());n.put("sha256",sha(legacy));n.put("sourceKind","EXISTING_LEGACY_PRIMARY_ARCHIVE");out.add(n);
        return out;
    }
    public static void main(String[] args)throws Exception{
        require(args.length==3,"Usage: financial-input-audit.json SOURCE_ROOT NEW_OUTPUT_DIRECTORY");
        Path auditFile=Path.of(args[0]),sourceRoot=Path.of(args[1]),output=Path.of(args[2]);require(!Files.exists(output),"Refuse output overwrite");
        String inputAuditHash=sha(auditFile);
        var selected=new TreeMap<String,ObjectNode>();var decisionRefs=new TreeMap<String,Set<String>>();
        for(var entry:JSON.readTree(Files.readString(auditFile)).properties()){
            String[] pieces=entry.getKey().split("/");if(pieces.length!=3)continue;var decision=LocalDate.parse(pieces[1]);
            if(decision.isBefore(LocalDate.of(2017,1,1))||decision.isAfter(CUTOFF)||entry.getValue().path("missing").asBoolean())continue;
            var n=(ObjectNode)entry.getValue();require(n.path("symbol").asText().equals(pieces[0]),"Symbol/key mismatch");
            require(LocalDate.parse(n.path("filedAt").asText()).isBefore(decision),"Filed/asof violation");
            String k=natural(n);var previous=selected.putIfAbsent(k,n.deepCopy());require(previous==null||previous.equals(n),"Conflicting selected DTO natural key "+k);
            decisionRefs.computeIfAbsent(k,x->new TreeSet<>()).add(entry.getKey());
        }
        var sourceReceipts=sources(sourceRoot);var candidates=new HashMap<String,List<ObjectNode>>();var archiveByReceipt=new HashMap<String,JsonNode>();
        var sourceByKey=new HashMap<String,ObjectNode>();
        for(var source:sourceReceipts){
            Path p=Path.of(source.path("archivePath").asText());require(sha(p).equalsIgnoreCase(source.path("sha256").asText()),"Source hash mismatch "+p);
            String raw=Files.readString(p);var archive=JSON.readTree(raw);require(cik(archive.path("cik")).equals(cik(source.path("cik"))),"Source CIK mismatch "+p);
            String symbol=source.path("symbol").asText();String sk=symbol+"/"+cik(archive.path("cik"));
            require(!archiveByReceipt.containsKey(sk),"Duplicate source issuer "+sk);archiveByReceipt.put(sk,archive);sourceByKey.put(sk,source);
            for(var row:standards(symbol,archive.path("cik").asText(),raw)){
                var n=(ObjectNode)JSON.valueToTree(row);candidates.computeIfAbsent(natural(n),x->new ArrayList<>()).add(n);
            }
        }
        var original=new TreeMap<String,List<ObjectNode>>();var repaired=new TreeMap<String,List<ObjectNode>>();
        var recoveredProofs=new ArrayList<Object>();var conflicts=new ArrayList<Object>();var reasons=new TreeMap<String,Integer>();
        for(var entry:selected.entrySet()){
            var old=entry.getValue();String symbol=old.path("symbol").asText();String sk=symbol+"/"+cik(old.path("cik"));var out=old.deepCopy();
            original.computeIfAbsent(symbol,x->new ArrayList<>()).add(old.deepCopy());var possible=candidates.getOrDefault(entry.getKey(),List.of());
            String reason=possible.isEmpty()?"NO_EXACT_STANDARD_NATURAL_KEY":"NO_COHERENT_PRIMARY_EPS";
            for(var full:possible){
                if(!sameIdentity(old,full))continue;
                if(!old.path("epsDiluted").isNull()){
                    reason="EXISTING_NONNULL_EPS_PRESERVED";
                    if(!full.path("epsDiluted").isNull()&&new BigDecimal(old.path("epsDiluted").asText()).compareTo(new BigDecimal(full.path("epsDiluted").asText()))!=0)
                        conflicts.add(Map.of("naturalKey",entry.getKey(),"oldEps",old.path("epsDiluted"),"sourceEps",full.path("epsDiluted"),"action","PRESERVED_ORIGINAL"));
                    continue;
                }
                var source=sourceByKey.get(sk);if(source==null)continue;
                var proposed=recover(old,full,archiveByReceipt.get(sk),Path.of(source.path("archivePath").asText()),source.path("sha256").asText());
                if(!proposed.path("epsDiluted").isNull()){
                    require(out.path("epsDiluted").isNull()||out.equals(proposed),"Conflicting recovery candidates");out=proposed;reason="RECOVERED_EPS_ONLY";
                }
            }
            if(reason.equals("RECOVERED_EPS_ONLY")){
                var raw=JSON.readTree(out.path("rawJson").asText());
                recoveredProofs.add(Map.of("naturalKey",entry.getKey(),"id",out.path("id"),"epsDiluted",out.path("epsDiluted"),"epsUnit",out.path("epsUnit"),"selectedFieldEvidence",raw.at("/selectedFieldEvidence/epsDiluted"),"source",sourceByKey.get(sk),"decisionReferenceCount",decisionRefs.get(entry.getKey()).size()));
            }
            reasons.merge(reason,1,Integer::sum);repaired.computeIfAbsent(symbol,x->new ArrayList<>()).add(out);
        }
        require(sha(auditFile).equals(inputAuditHash),"Input audit drift");
        for(var source:sourceReceipts)require(sha(Path.of(source.path("archivePath").asText())).equalsIgnoreCase(source.path("sha256").asText()),"Source archive drift");
        Files.createDirectory(output);
        write(output.resolve("original-selected-standards.json"),original);write(output.resolve("eps-recovered-selected-standards.json"),repaired);
        var report=new LinkedHashMap<String,Object>();report.put("policy","EPS_ONLY_EXISTING_TRAINING_SELECTED_DTO_EXACT_SOURCE_IDENTITY_V1");report.put("cutoffInclusive",CUTOFF.toString());
        report.put("schema","Map<String,List<SecFinancialStandard>> keyed by symbol; unique original natural keys; original IDs/all non-EPS values preserved");
        report.put("inputAuditPath",auditFile.toString());report.put("inputAuditSha256",inputAuditHash);report.put("selectedUniqueRows",selected.size());report.put("reasons",reasons);
        report.put("recoveredProofs",recoveredProofs);report.put("preservedNonnullConflicts",conflicts);report.put("sources",sourceReceipts);report.put("databaseConnections",0);report.put("databaseWrites",0);
        report.put("originalSha256",sha(output.resolve("original-selected-standards.json")));report.put("recoveredSha256",sha(output.resolve("eps-recovered-selected-standards.json")));
        write(output.resolve("recovery-audit.json"),report);
        System.out.println("EPS_RECOVERY_PREPARED selected="+selected.size()+" recovered="+recoveredProofs.size()+" conflictsPreserved="+conflicts.size()+" reasons="+reasons);
    }
}
