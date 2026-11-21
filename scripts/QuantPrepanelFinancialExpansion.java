import com.kingyurina.demo.stock.*;
import tools.jackson.databind.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.file.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

/** File-only extraction: no network or DB imports. */
public final class QuantPrepanelFinancialExpansion {
    static final ObjectMapper JSON=new ObjectMapper();
    static final String BASE="tmp/quant-ifrs-standard-append-plan-20261031-v1";
    static final Map<String,String> CIK=Map.of("AZN","901832","CCEP","1650107","FER","1468522","GFS","1709048","TRI","1075124");
    static String key(SecFinancialStandard r){return String.join("/",r.getSymbol(),String.valueOf(r.getFiscalYear()),r.getFiscalPeriod(),r.getForm(),r.getAccessionNumber(),String.valueOf(r.getEndDate()));}
    static List<SecFinancialStandard> novelPrior(List<SecFinancialStandard> rows,Set<String> known,LocalDate cutoff){
        var seen=new HashSet<String>();var out=new ArrayList<SecFinancialStandard>();
        for(var r:rows){if(!seen.add(key(r)))throw new IllegalArgumentException("Duplicate standard natural key");
            if(r.getFiledAt()!=null&&r.getEndDate()!=null&&r.getFiledAt().isBefore(cutoff)&&r.getEndDate().isBefore(cutoff)&&!known.contains(key(r)))out.add(r);
        }return out;
    }
    static JsonNode read(Path p)throws Exception{return JSON.readTree(Files.readString(p));}
    static String sha(Path p)throws Exception{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p)));}
    static void require(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
    static void write(Path p,Object value)throws Exception{Files.writeString(p,JSON.writerWithDefaultPrettyPrinter().writeValueAsString(value),StandardOpenOption.CREATE_NEW);}
    static String beanName(String column){var b=new StringBuilder();boolean upper=false;for(char c:column.toCharArray()){if(c=='_')upper=true;else{b.append(upper?Character.toUpperCase(c):c);upper=false;}}return b.toString();}
    static Map<String,String> columns(SecFinancialStandard r){var n=JSON.valueToTree(r);var out=new LinkedHashMap<String,String>();for(String column:FinancialRestandardizationSafety.COLUMNS){var v=n.path(beanName(column));out.put(column,v.isMissingNode()||v.isNull()?null:v.asText());}return out;}
    static int validateOriginal(SecFinancialStandard r,JsonNode archive)throws Exception{
        var mapped=columns(r);FinancialRestandardizationSafety.validate(mapped);var raw=JSON.readTree(r.getRawJson());require("CALENDAR_FRAME_EXACT_DURATION_V1".equals(raw.path("periodPolicy").asText()),"Native period policy absent");
        require(CIK.get(r.getSymbol()).equals(r.getCik()),"CIK mismatch");int verified=0;
        for(String column:FinancialRestandardizationSafety.VALUES){if(mapped.get(column)==null)continue;String field=beanName(column);var e=raw.path("selectedFieldEvidence").path(field);var original=JSON.readTree(e.path("sourceObservationJson").asText());
            require("ifrs-full".equals(e.path("taxonomy").asText()),"Not IFRS field");
            require(archive.path("facts").path("ifrs-full").path(e.path("concept").asText()).path("units").path(e.path("unit").asText()).valueStream().anyMatch(original::equals),"Selected observation absent from original archive");
            require(r.getAccessionNumber().equals(original.path("accn").asText())&&r.getFiledAt().toString().equals(original.path("filed").asText())&&r.getEndDate().toString().equals(original.path("end").asText()),"Filing/range changed");
            require(new BigDecimal(mapped.get(column)).compareTo(new BigDecimal(original.path("val").asText()))==0,"Changed financial value");
            if(!Set.of("assets","liabilities","equity").contains(column)&&!r.getFiscalPeriod().equals("FY"))require(("CY"+r.getFiscalYear()+r.getFiscalPeriod()).equals(original.path("frame").asText()),"Quarter unproved");
            verified++;
        }return verified;
    }
    public static void main(String[] args)throws Exception{
        require(args.length==2,"Expected exclusive training cutoff and NEW output directory");LocalDate cutoff=LocalDate.parse(args[0]);Path out=Path.of(args[1]);require(!Files.exists(out),"Refuse overwrite");
        var known=new HashSet<String>();var hashes=new TreeMap<String,String>();
        for(String name:List.of("old-standards.json","candidate-standards.json")){Path path=Path.of(BASE,name);hashes.put(path.toString(),sha(path));for(var r:read(path))known.add(String.join("/",r.path("symbol").asText(),r.path("fiscal_year").asText(),r.path("fiscal_period").asText(),r.path("form").asText(),r.path("accession_number").asText(),r.path("end_date").asText()));}
        Path manifest=Path.of("tmp/quant-free-history-queue-primary-approved-20261031/manifest.json");hashes.put(manifest.toString(),sha(manifest));
        var parser=new SecEdgarService(new SecEdgarProperties("https://unused.invalid","https://unused.invalid","offline",168,Integer.MAX_VALUE,0,false),null,null,JSON,null,null,null);
        var parse=SecEdgarService.class.getDeclaredMethod("parseFacts",String.class,String.class,String.class);parse.setAccessible(true);
        var standardizer=new SecFinancialStandardService(null,null,JSON);var normalize=SecFinancialStandardService.class.getDeclaredMethod("standardize",String.class,String.class,List.class);normalize.setAccessible(true);
        var all=new TreeMap<String,List<SecFinancialStandard>>();var audit=new TreeMap<String,Object>();var sourceDetails=new ArrayList<Object>();int verified=0;
        for(var source:read(manifest).path("sourceFiles")){String file=source.path("path").asText();if(!file.endsWith("-facts.json"))continue;String symbol=source.path("symbol").asText();if(!CIK.containsKey(symbol))continue;Path path=Path.of(file);require(sha(path).equals(source.path("sha256").asText()),"Original hash mismatch");hashes.put(file,sha(path));var archive=read(path);require(new BigInteger(archive.path("cik").asText()).toString().equals(CIK.get(symbol)),"Wrong original issuer");
            @SuppressWarnings("unchecked") var facts=(List<SecCompanyFact>)parse.invoke(parser,symbol,CIK.get(symbol),Files.readString(path));
            // The prior bounded replay selected just 16accessions. Here all genuinely prior filings are eligible.
            facts=facts.stream().filter(f->f.getFiledAt()!=null&&f.getEndDate()!=null&&f.getFiledAt().isBefore(cutoff)&&f.getEndDate().isBefore(cutoff)).toList();
            @SuppressWarnings("unchecked") var rows=(List<SecFinancialStandard>)normalize.invoke(standardizer,symbol,CIK.get(symbol),facts);
            var fresh=novelPrior(rows,known,cutoff);for(var row:fresh)verified+=validateOriginal(row,archive);all.put(symbol,fresh);
            audit.put(symbol,Map.of("priorParsedFacts",facts.size(),"priorCandidateStandards",rows.size(),"newStandards",fresh.size(),"alreadyPresentStandards",rows.size()-fresh.size(),"newAccessions",fresh.stream().map(SecFinancialStandard::getAccessionNumber).distinct().count(),"annual",fresh.stream().filter(r->r.getFiscalPeriod().equals("FY")).count(),"quarter",fresh.stream().filter(r->!r.getFiscalPeriod().equals("FY")).count()));sourceDetails.add(source);
        }
        require(all.size()==5,"Five original source archives required");
        for(String file:List.of("scripts/QuantPrepanelFinancialExpansion.java","src/main/java/com/kingyurina/demo/stock/SecEdgarService.java","src/main/java/com/kingyurina/demo/stock/SecFinancialStandardService.java","target/classes/com/kingyurina/demo/stock/SecEdgarService.class","target/classes/com/kingyurina/demo/stock/SecFinancialStandardService.class"))hashes.put(file,sha(Path.of(file)));
        Files.createDirectories(out);write(out.resolve("new-standards-by-symbol.json"),all);write(out.resolve("manifest.json"),Map.of("classification","NEW_PREPANEL_NATIVE_FINANCIAL_ROWS_OFFLINE_NOT_IMPORTED_NOT_RECEIPT_PIT","createdAt",Instant.now().toString(),"cutoffExclusive",cutoff,"newStandardCount",all.values().stream().mapToInt(List::size).sum(),"verifiedOriginalSelectedValues",verified,"databaseConnections",0,"databaseWrites",0,"bySymbol",audit,"originalSources",sourceDetails,"inputHashes",hashes));
        Files.copy(Path.of("scripts/QuantPrepanelFinancialExpansion.java"),out.resolve("QuantPrepanelFinancialExpansion.java.source"));
        for(var entry:hashes.entrySet())require(sha(Path.of(entry.getKey())).equals(entry.getValue()),"Input drift during extraction");
        System.out.println("PREPANEL_EXPANSION_DONE new="+all.values().stream().mapToInt(List::size).sum()+" originalValues="+verified+" "+audit);
    }
}
