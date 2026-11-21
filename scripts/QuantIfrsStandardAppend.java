import com.kingyurina.demo.stock.*;
import tools.jackson.databind.*;
import java.math.*;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import static com.kingyurina.demo.stock.FinancialRestandardizationSafety.*;

/** Explicit standards-only INSERT operator. No raw writes, update, delete, upsert or DDL.
 * Prepare is SELECT-only. All mutation modes are explicit and parent-operated.
 */
public final class QuantIfrsStandardAppend {
    static final ObjectMapper JSON=QuantHistoricalUsAppend.JSON;
    static final Map<String,String> CIK=Map.of("GFS","1709048","AZN","901832","CCEP","1650107","FER","1468522","TRI","1075124");
    static final Map<String,Integer> EXPECTED=Map.of("GFS",16,"AZN",9,"CCEP",3,"FER",3,"TRI",4);
    static final List<String> SC=COLUMNS.stream().filter(x->!x.equals("id")).toList();
    static final Map<String,Set<String>> CONCEPTS=Map.of(
        "revenue",Set.of("Revenue","RevenueFromContractsWithCustomers"),
        "operating_income",Set.of("ProfitLossFromOperatingActivities","OperatingProfitLossOperating"),
        "net_income",Set.of("ProfitLossAttributableToOwnersOfParent","ProfitLoss"),
        "eps_diluted",Set.of("DilutedEarningsLossPerShare"),"assets",Set.of("Assets"),
        "liabilities",Set.of("Liabilities"),"equity",Set.of("EquityAttributableToOwnersOfParent","Equity"),
        "operating_cash_flow",Set.of("CashFlowsFromUsedInOperatingActivities"));
    public record Manifest(String policy,String createdAt,String database,String candidateHash,String backupHash,
        String preimage,long rawCount,int oldCount,int newCount,Map<String,String> inputHashes,Map<String,String> codeHashes){}
    static String camel(String s){var b=new StringBuilder();boolean up=false;for(char ch:s.toCharArray()){if(ch=='_')up=true;else{b.append(up?Character.toUpperCase(ch):ch);up=false;}}return b.toString();}
    static String nullable(JsonNode n,String key){var v=n.path(key);return v.isMissingNode()||v.isNull()?null:v.asText();}
    static void validateAnnualRange(Map<String,String> r){
        var end=LocalDate.parse(r.get("end_date"));var start=LocalDate.parse(r.get("start_date"));int year=Integer.parseInt(r.get("fiscal_year"));
        require(start.equals(LocalDate.of(year,1,1))&&end.equals(LocalDate.of(year,12,31)),"Annual requires canonical January-December range/year");
        var evidence=JSON.readTree(r.get("raw_json")).path("selectedFieldEvidence");
        for(String field:VALUES)if(r.get(field)!=null&&!List.of("assets","liabilities","equity").contains(field)){
            String frame=nullable(evidence.path(camel(field)),"sourceFrame");require(frame==null||frame.equals("CY"+year),"Annual frame conflicts with range year");
        }
    }
    static int validateCandidate(Map<String,String> r,List<SecCompanyFact> facts){
        validate(r);require(r.get("id")==null,"Candidate has existing id");
        require(CIK.containsKey(r.get("symbol"))&&new BigInteger(r.get("cik")).toString().equals(CIK.get(r.get("symbol"))),"Wrong issuer CIK");
        require(Set.of("20-F","20-F/A","40-F","40-F/A","6-K","6-K/A").contains(r.get("form")),"Unsupported foreign form");
        require(Set.of("USD","EUR").contains(r.get("currency")),"Unsupported native currency");
        require(r.get("eps_unit")==null||r.get("eps_unit").equals(r.get("currency")+"/shares"),"Quoted EPS forbidden");
        require(Duration.between(LocalDateTime.parse(r.get("mapped_at")),LocalDateTime.now(ZoneOffset.UTC)).abs().toHours()<48,"mappedAt must be actual recent UTC");
        var start=LocalDate.parse(r.get("start_date"));var end=LocalDate.parse(r.get("end_date"));var filed=LocalDate.parse(r.get("filed_at"));
        require(!end.isAfter(filed)&&!start.isAfter(end),"Future/reversed statement dates");
        long days=ChronoUnit.DAYS.between(start,end)+1;String fp=r.get("fiscal_period");
        if(fp.equals("FY")){require(days>=330&&days<=380&&Integer.parseInt(r.get("fiscal_year"))==end.getYear(),"Annual duration/year");validateAnnualRange(r);}
        else{int q=Integer.parseInt(fp.substring(1));require(start.equals(LocalDate.of(end.getYear(),q*3-2,1))&&end.equals(start.plusMonths(3).minusDays(1)),"Not exact calendar quarter");require(Integer.parseInt(r.get("fiscal_year"))==end.getYear(),"Quarter year mismatch");}
        var raw=JSON.readTree(r.get("raw_json"));require("CALENDAR_FRAME_EXACT_DURATION_V1".equals(raw.path("periodPolicy").asText()),"Missing native period policy");int verified=0;
        for(String field:VALUES){String value=r.get(field);if(value==null)continue;
            var n=new BigDecimal(value);require(n.scale()<=6&&n.precision()-n.scale()<=26,"Nonexact database DECIMAL");
            var ev=raw.path("selectedFieldEvidence").path(camel(field));
            require("ifrs-full".equals(ev.path("taxonomy").asText())&&CONCEPTS.get(field).contains(ev.path("concept").asText()),"Wrong field taxonomy/concept");
            var source=JSON.readTree(ev.path("sourceObservationJson").asText());
            require(r.get("accession_number").equals(source.path("accn").asText())&&r.get("form").equals(source.path("form").asText())&&r.get("filed_at").equals(source.path("filed").asText()),"Original accession/filing/form mismatch");
            require(r.get("end_date").equals(source.path("end").asText())&&Objects.equals(nullable(source,"start"),nullable(ev,"startDate")),"Original range mismatch");
            require(n.compareTo(new BigDecimal(source.path("val").asText()))==0,"Original value mismatch");
            require(Objects.equals(nullable(source,"frame"),nullable(ev,"sourceFrame")),"Source frame metadata mismatch");
            require(facts.stream().anyMatch(f->r.get("symbol").equals(f.getSymbol())&&CIK.get(r.get("symbol")).equals(new BigInteger(f.getCik()).toString())&&"ifrs-full".equals(f.getTaxonomy())
                &&ev.path("concept").asText().equals(f.getConcept())&&ev.path("unit").asText().equals(f.getUnit())
                &&r.get("accession_number").equals(f.getAccessionNumber())&&filed.equals(f.getFiledAt())&&r.get("form").equals(f.getForm())
                &&end.equals(f.getEndDate())&&(nullable(source,"start")==null?
                    f.getStartDate()==null||f.getStartDate().equals(end):nullable(source,"start").equals(f.getStartDate()==null?null:f.getStartDate().toString()))
                &&n.compareTo(f.getValue())==0&&source.equals(JSON.readTree(f.getRawJson()))),"Selected field missing from exact native facts: "+key(r)+" "+field);
            if(!List.of("assets","liabilities","equity").contains(field)&&!fp.equals("FY"))require(("CY"+r.get("fiscal_year")+fp).equals(nullable(source,"frame")),"Quarter lacks exact original frame");
            verified++;
        }return verified;
    }
    static String sha(Path p)throws Exception{return QuantHistoricalUsAppend.sha(p);}
    static void write(Path p,Object body)throws Exception{QuantHistoricalUsAppend.write(p,body);}
    static Map<String,String> codeHashes()throws Exception{
        var out=new TreeMap<String,String>();for(String p:List.of("scripts/QuantIfrsStandardAppend.java","scripts/QuantHistoricalUsAppend.java","src/main/java/com/kingyurina/demo/stock/FinancialRestandardizationSafety.java","target/classes/com/kingyurina/demo/stock/FinancialRestandardizationSafety.class","src/main/java/com/kingyurina/demo/stock/FinancialInputPolicy.java","target/classes/com/kingyurina/demo/stock/FinancialInputPolicy.class"))out.put(p,sha(Path.of(p)));
        for(Class<?> c:List.of(QuantIfrsStandardAppend.class,QuantHistoricalUsAppend.class)){
            var path=Path.of(c.getProtectionDomain().getCodeSource().getLocation().toURI()).resolve(c.getSimpleName()+".class");out.put(path.toString(),sha(path));
        }return out;
    }
    static List<SecCompanyFact> facts(Path file)throws Exception{
        var out=new ArrayList<SecCompanyFact>();for(var issuer:JSON.readTree(Files.readString(file)))for(var f:issuer)out.add(JSON.treeToValue(f,SecCompanyFact.class));return out;
    }
    static String sourceCik(JsonNode root){
        if(!root.has("facts"))return null; // native-facts.json is an issuer bundle, not an original response.
        String cik=root.path("cik").asText();require(cik.matches("[0-9]{1,10}"),"Invalid original source CIK");
        return new BigInteger(cik).toString();
    }
    static void verifyOriginalSources(List<SecCompanyFact> facts,Map<String,String> inputs)throws Exception{
        var archives=new HashMap<String,JsonNode>();
        for(String p:inputs.keySet())if(p.endsWith("-facts.json")){
            var n=JSON.readTree(Files.readString(Path.of(p)));String cik=sourceCik(n);for(var e:CIK.entrySet())if(e.getValue().equals(cik))archives.put(e.getKey(),n);
        }
        require(archives.size()==5,"Missing five pinned original companyfacts archives");
        String queueFile=inputs.keySet().stream().filter(p->p.endsWith("accession-queue.json")).findFirst().orElseThrow();var queue=JSON.readTree(Files.readString(Path.of(queueFile)));
        for(var f:facts){
            boolean financial="ifrs-full".equals(f.getTaxonomy())&&CONCEPTS.values().stream().anyMatch(tags->tags.contains(f.getConcept()));
            boolean ancillary="dei".equals(f.getTaxonomy())&&"EntityCommonStockSharesOutstanding".equals(f.getConcept())&&"shares".equals(f.getUnit());
            require(CIK.containsKey(f.getSymbol())&&CIK.get(f.getSymbol()).equals(new BigInteger(f.getCik()).toString())&&(financial||ancillary),"Wrong native fact issuer/taxonomy");
            var original=JSON.readTree(f.getRawJson());var sourceRows=archives.get(f.getSymbol()).path("facts").path(f.getTaxonomy()).path(f.getConcept()).path("units").path(f.getUnit());
            require(sourceRows.valueStream().anyMatch(original::equals),"Native fact missing from hashed original archive");
            var accession=queue.valueStream().filter(q->f.getSymbol().equals(q.path("symbol").asText())&&f.getAccessionNumber().equals(q.path("accession").asText())).findFirst().orElseThrow();
            require(f.getFiledAt().toString().equals(accession.path("filed").asText())&&f.getForm().equals(accession.path("form").asText()),"Queue filing/form mismatch");
            require(accession.path("affectedDecisions").valueStream().allMatch(d->f.getFiledAt().isBefore(LocalDate.parse(d.asText()))&&f.getEndDate().isBefore(LocalDate.parse(d.asText()))),"Source not strictly prior to every affected decision");
        }
    }
    static void validateSet(List<Map<String,String>> rows,List<SecCompanyFact> facts){
        require(rows.size()==35&&facts.size()==331,"Unexpected candidate/native fact count");var counts=new TreeMap<String,Integer>();var seen=new HashSet<String>();
        for(var r:rows){validateCandidate(r,facts);require(seen.add(key(r)),"Duplicate standard natural key");counts.merge(r.get("symbol"),1,Integer::sum);}require(counts.equals(EXPECTED),"Issuer standard count mismatch");
    }
    static long rawCount(Connection c)throws Exception{try(var s=c.createStatement();var r=s.executeQuery("SELECT COUNT(*) FROM stock_sec_fact")){r.next();return r.getLong(1);}}
    static void emptyScope(Connection c,boolean lock)throws Exception{
        for(String symbol:new TreeSet<>(CIK.keySet()))try(var p=c.prepareStatement("SELECT id FROM stock_sec_financial_standard WHERE symbol=?"+(lock?" FOR UPDATE":""))){p.setString(1,symbol);try(var r=p.executeQuery()){require(!r.next(),"Existing issuer standard scope: "+symbol);}}
    }
    static void prepare(Path replay,Path out)throws Exception{
        require(!Files.exists(out),"Output directory exists");
        var result=JSON.readTree(Files.readString(replay.resolve("result.json")));var inputs=new TreeMap<String,String>();
        for(var e:result.path("inputHashes").properties()){require(sha(Path.of(e.getKey())).equals(e.getValue().asText()),"Replay dependency drift: "+e.getKey());inputs.put(e.getKey(),e.getValue().asText());}
        for(String name:List.of("result.json","native-standards.json","native-facts.json","native-source-audit.json"))inputs.put(replay.resolve(name).toString(),sha(replay.resolve(name)));
        var facts=facts(replay.resolve("native-facts.json"));verifyOriginalSources(facts,inputs);var rows=new ArrayList<Map<String,String>>();
        for(var issuer:JSON.readTree(Files.readString(replay.resolve("native-standards.json"))))for(var n:issuer){var bean=JSON.treeToValue(n,SecFinancialStandard.class);bean.setMappedAt(LocalDateTime.now(ZoneOffset.UTC).withNano(0));rows.add(QuantHistoricalUsAppend.columns(bean,COLUMNS));}validateSet(rows,facts);
        try(var c=QuantHistoricalUsAppend.connect()){
            c.setReadOnly(true);c.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);c.setAutoCommit(false);emptyScope(c,false);
            var backup=readStandards(c,false);long raw=rawCount(c);Files.createDirectories(out);
            write(out.resolve("old-standards.json"),backup);write(out.resolve("candidate-standards.json"),rows);
            var m=new Manifest("INSERT_ONLY_EMPTY_IFRS_STANDARDS_V1",Instant.now().toString(),QuantHistoricalUsAppend.database(c),sha(out.resolve("candidate-standards.json")),sha(out.resolve("old-standards.json")),hash(backup),raw,backup.size(),rows.size(),inputs,codeHashes());
            write(out.resolve("manifest.json"),m);write(out.resolve("manifest.sha256.json"),Map.of("sha256",sha(out.resolve("manifest.json"))));c.rollback();
            System.out.println("PREPARED SELECT-only standards="+rows.size()+" old="+backup.size()+" raw="+raw+" manifestSHA="+sha(out.resolve("manifest.json")));
        }
    }
    static void assertRollback(Connection c,Manifest m)throws Exception{emptyScope(c,false);require(hash(readStandards(c,false)).equals(m.preimage()),"Rollback standard preimage mismatch");require(rawCount(c)==m.rawCount(),"Raw count drift");}
    static void operate(Path manifestFile,boolean apply,boolean partial)throws Exception{
        Path root=manifestFile.toAbsolutePath().normalize().getParent();var m=JSON.readValue(Files.readString(manifestFile),Manifest.class);String mh=sha(manifestFile);
        require(mh.equals(JSON.readTree(Files.readString(root.resolve("manifest.sha256.json"))).path("sha256").asText()),"Manifest hash changed");
        require("INSERT_ONLY_EMPTY_IFRS_STANDARDS_V1".equals(m.policy())&&m.codeHashes().equals(codeHashes()),"Policy/operator code drift");
        for(var e:m.inputHashes().entrySet())require(sha(Path.of(e.getKey())).equals(e.getValue()),"Source/candidate replay drift: "+e.getKey());
        require(sha(root.resolve("candidate-standards.json")).equals(m.candidateHash())&&sha(root.resolve("old-standards.json")).equals(m.backupHash()),"Candidate/backup hash drift");
        var rows=QuantHistoricalUsAppend.readFile(root.resolve("candidate-standards.json"));var backup=QuantHistoricalUsAppend.readFile(root.resolve("old-standards.json"));
        require(rows.size()==m.newCount()&&backup.size()==m.oldCount()&&hash(backup).equals(m.preimage()),"Prepared count/preimage mismatch");
        String factsFile=m.inputHashes().keySet().stream().filter(p->p.endsWith("native-facts.json")).findFirst().orElseThrow();var facts=facts(Path.of(factsFile));verifyOriginalSources(facts,m.inputHashes());validateSet(rows,facts);
        String mode=apply?"apply":partial?"verify-partial-rollback":"verify-rollback";Path receipt=root.resolve(mode+".json");require(!Files.exists(receipt),"Evidence receipt exists; inspect before any retry");
        if(apply)for(String probe:List.of("verify-rollback.json","verify-partial-rollback.json")){var n=JSON.readTree(Files.readString(root.resolve(probe)));require(mh.equals(n.path("manifestSha256").asText())&&n.path("rollbackVerified").asBoolean(),"Both rollback probes required");}
        boolean committed=false;boolean injected=false;
        try(var c=QuantHistoricalUsAppend.connect()){
            require(m.database().equals(QuantHistoricalUsAppend.database(c)),"Wrong database identity");
            try(var p=c.prepareStatement("SELECT GET_LOCK('quant_ifrs_standard_append',0)");var r=p.executeQuery()){require(r.next()&&r.getInt(1)==1,"Another IFRS append active");}
            c.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);c.setAutoCommit(false);
            try{
                require(hash(readStandards(c,true)).equals(m.preimage()),"Full standard preimage drift");emptyScope(c,true);require(rawCount(c)==m.rawCount(),"Raw preimage count drift");
                if(partial){QuantHistoricalUsAppend.insert(c,"stock_sec_financial_standard",SC,List.of(rows.getFirst()));injected=true;throw new IllegalStateException("INJECTED_AFTER_FIRST_STANDARD_INSERT");}
                QuantHistoricalUsAppend.insert(c,"stock_sec_financial_standard",SC,rows);
                var current=readStandards(c,false);require(current.size()==m.oldCount()+35,"Postimage count mismatch");var old=current.stream().filter(r->!CIK.containsKey(r.get("symbol"))).toList();
                require(hash(old).equals(m.preimage()),"Existing standard rows changed");var added=current.stream().filter(r->CIK.containsKey(r.get("symbol"))).toList();
                require(QuantHistoricalUsAppend.rowDigest(added,SC).equals(QuantHistoricalUsAppend.rowDigest(rows,SC)),"35-row postimage mismatch");require(rawCount(c)==m.rawCount(),"Raw postimage count drift");
                if(apply){c.commit();committed=true;}else c.rollback();c.setAutoCommit(true);if(!apply)assertRollback(c,m);
            }catch(Exception|Error e){if(!committed)c.rollback();c.setAutoCommit(true);
                if(partial&&injected&&"INJECTED_AFTER_FIRST_STANDARD_INSERT".equals(e.getMessage()))assertRollback(c,m);else{System.err.println(committed?"ALREADY COMMITTED: do not retry":"Rolled back/no confirmed commit: inspect before retry");throw e;}
            }finally{try(var s=c.createStatement()){s.execute("SELECT RELEASE_LOCK('quant_ifrs_standard_append')");}}
            write(receipt,Map.of("mode",mode,"committed",committed,"rollbackVerified",!apply,"standards",35,"rawWrites",0,"databaseWritesCommitted",apply?35:0,"manifestSha256",mh,"recordedAt",Instant.now().toString()));
            System.out.println(apply?"COMMITTED 35 IFRS standard rows; raw writes=0":"ROLLBACK VERIFIED "+mode+"; committed rows=0");
        }
    }
    public static void main(String[] args)throws Exception{
        require(args.length>=2,"--prepare REPLAY NEW_PLAN | --verify-rollback MANIFEST | --verify-partial-rollback MANIFEST | --apply MANIFEST");
        switch(args[0]){case "--prepare"->{require(args.length==3,"Expected replay and new plan");prepare(Path.of(args[1]),Path.of(args[2]));}
            case "--verify-rollback"->{require(args.length==2,"Expected manifest");operate(Path.of(args[1]),false,false);}
            case "--verify-partial-rollback"->{require(args.length==2,"Expected manifest");operate(Path.of(args[1]),false,true);}
            case "--apply"->{require(args.length==2,"Expected manifest");operate(Path.of(args[1]),true,false);}
            default->throw new IllegalArgumentException("Unknown explicit mode");}
    }
}
