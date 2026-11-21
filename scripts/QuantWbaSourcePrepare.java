import com.kingyurina.demo.stock.*;
import tools.jackson.databind.*;
import java.math.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
public class QuantWbaSourcePrepare {
    static final ObjectMapper JSON=new ObjectMapper();
    static final Path ROOT=Path.of("tmp/quant-strict-dow-inputs-20261031-v1");
    static boolean fiscalValid(SecFinancialStandard r){
        var d=r.getEndDate();if(d==null||r.getFiscalYear()==null||r.getFiscalPeriod()==null)return false;
        int fy=d.getYear()+(d.getMonthValue()>8?1:0);
        String fp=switch(d.getMonthValue()){case 11->"Q1";case 2->"Q2";case 5->"Q3";case 8->"FY";default->"UNSUPPORTED";};
        return fy==r.getFiscalYear()&&(fp.equals(r.getFiscalPeriod())||(fp.equals("FY")&&r.getFiscalPeriod().equals("Q4")))&&d.equals(d.withDayOfMonth(d.lengthOfMonth()));
    }
    static String sha(Path p)throws Exception{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p)));}
    static void write(String name,Object v)throws Exception{Files.writeString(ROOT.resolve(name),JSON.writerWithDefaultPrettyPrinter().writeValueAsString(v),StandardOpenOption.CREATE_NEW);}
    static void require(boolean v,String m){if(!v)throw new IllegalStateException(m);}
    static String camel(String c){var b=new StringBuilder();boolean up=false;for(char x:c.toCharArray()){if(x=='_')up=true;else{b.append(up?Character.toUpperCase(x):x);up=false;}}return b.toString();}
    static void validate(SecFinancialStandard row,JsonNode archive)throws Exception{
        var n=JSON.valueToTree(row);var cols=new LinkedHashMap<String,String>();
        for(String c:FinancialRestandardizationSafety.COLUMNS){var v=n.path(camel(c));cols.put(c,v.isNull()||v.isMissingNode()?null:v.asText());}
        FinancialRestandardizationSafety.validate(cols);
        require("USD".equals(row.getCurrency()),"Non-USD statement");
        var ev=JSON.readTree(row.getRawJson()).path("selectedFieldEvidence");
        for(String c:FinancialRestandardizationSafety.VALUES){if(cols.get(c)==null)continue;var e=ev.path(camel(c));var obs=JSON.readTree(e.path("sourceObservationJson").asText());
            require(archive.path("facts").path(e.path("taxonomy").asText()).path(e.path("concept").asText()).path("units").path(e.path("unit").asText()).valueStream().anyMatch(obs::equals),"Observation absent from original");
            require(row.getAccessionNumber().equals(obs.path("accn").asText())&&row.getFiledAt().toString().equals(obs.path("filed").asText())&&row.getEndDate().toString().equals(obs.path("end").asText()),"Source identity mismatch");
            require(new BigDecimal(cols.get(c)).compareTo(new BigDecimal(obs.path("val").asText()))==0,"Value changed");
        }
    }
    public static void main(String[]args)throws Exception{
        Path source=ROOT.resolve("WBA-companyfacts.json");String hash=sha(source);var archive=JSON.readTree(Files.readString(source));
        require("1618921".equals(new BigInteger(archive.path("cik").asText()).toString()),"Wrong source issuer");
        require(archive.path("entityName").asText().contains("WALGREENS BOOTS ALLIANCE"),"Wrong issuer name");
        var parser=new SecEdgarService(new SecEdgarProperties("https://unused.invalid","https://unused.invalid","offline",168,Integer.MAX_VALUE,0,true),null,null,JSON,null,null,null);
        var parse=SecEdgarService.class.getDeclaredMethod("parseFacts",String.class,String.class,String.class);parse.setAccessible(true);
        @SuppressWarnings("unchecked")var parsed=(List<SecCompanyFact>)parse.invoke(parser,"WBA","1618921",Files.readString(source));
        var cutoff=LocalDate.parse("2022-12-30");
        var facts=parsed.stream().filter(f->f.getFiledAt()!=null&&f.getEndDate()!=null&&f.getFiledAt().isBefore(cutoff)&&f.getEndDate().isBefore(cutoff)).toList();
        var anchors=facts.stream().filter(f->f.getFiscalYear()!=null&&"FY".equals(f.getFiscalPeriod())&&f.getForm().startsWith("10-K")&&f.getStartDate()!=null&&f.getStartDate().equals(LocalDate.of(f.getEndDate().getYear()-1,9,1))&&f.getEndDate().equals(LocalDate.of(f.getEndDate().getYear(),8,31))&&f.getFiscalYear()==f.getEndDate().getYear()).toList();
        require(!anchors.isEmpty(),"No actual August fiscal-year source anchor");
        var svc=new SecFinancialStandardService(null,null,JSON);var normalize=SecFinancialStandardService.class.getDeclaredMethod("standardize",String.class,String.class,List.class);normalize.setAccessible(true);
        @SuppressWarnings("unchecked")var candidates=(List<SecFinancialStandard>)normalize.invoke(svc,"WBA","1618921",facts);
        var rows=new ArrayList<SecFinancialStandard>();var rejected=new ArrayList<Object>();
        for(var row:candidates){String reason=null;
            if(!fiscalValid(row))reason="SOURCE_FILING_FY_FP_COMPARATIVE_MISMATCH_NO_RELABEL";
            else if(anchors.stream().noneMatch(a->!a.getFiledAt().isAfter(row.getFiledAt())))reason="NO_PRIOR_ACTUAL_FISCAL_ANCHOR";
            if(reason==null)try{validate(row,archive);}catch(RuntimeException ex){reason="COHERENCE_REJECTED: "+ex.getMessage();}
            if(reason==null)rows.add(row);else rejected.add(Map.of("reason",reason,"filedAt",row.getFiledAt(),"endDate",row.getEndDate(),"fiscalYear",row.getFiscalYear(),"fiscalPeriod",row.getFiscalPeriod(),"accession",row.getAccessionNumber()));
        }
        var shares=new ArrayList<Object>();
        for(var o:archive.path("facts").path("dei").path("EntityCommonStockSharesOutstanding").path("units").path("shares")){
            var filed=LocalDate.parse(o.path("filed").asText());var end=LocalDate.parse(o.path("end").asText());
            if(!filed.isBefore(cutoff)||!end.isBefore(cutoff))continue;
            require(!filed.isBefore(end),"Shares measured after filed");BigInteger val=new BigDecimal(o.path("val").asText()).toBigIntegerExact();require(val.signum()>0,"Nonpositive shares");
            var r=new LinkedHashMap<String,Object>();r.put("symbol","WBA");r.put("cik","1618921");r.put("filedDate",filed);r.put("measurementDate",end);r.put("accessionNumber",o.path("accn").asText());r.put("totalEconomicCommonShares",val);r.put("sharesPerQuotedUnit",1);r.put("quotedUnitEquivalentShares",val);r.put("unit","shares");r.put("quoteCurrency","USD");r.put("quoteUnit","ordinary common share; no ADS");r.put("sourcePath",source.toString());r.put("sourceSha256",hash);r.put("sourceUrl","https://data.sec.gov/api/xbrl/companyfacts/CIK0001618921.json");r.put("sourceClassification","fact_source_reported");r.put("sourceObservation",o);shares.add(r);
        }
        require(!rows.isEmpty()&&!shares.isEmpty(),"Empty usable source extraction");
        var coverage=new TreeMap<String,Object>();
        for(String date:List.of("2021-10-29","2022-01-31","2022-03-31","2022-06-30","2022-09-30","2022-11-30","2022-12-30")){var d=LocalDate.parse(date);coverage.put(date,Map.of("financialRows",rows.stream().filter(r->r.getFiledAt().isBefore(d)&&r.getEndDate().isBefore(d)).count(),"latestFinancialFiled",rows.stream().filter(r->r.getFiledAt().isBefore(d)&&r.getEndDate().isBefore(d)).map(SecFinancialStandard::getFiledAt).max(Comparator.naturalOrder()).map(Object::toString).orElse("MISSING")));}
        write("WBA-financials.json",rows);write("WBA-shares.json",shares);write("WBA-rejected-financials.json",rejected);
        write("WBA-source-audit.json",Map.of("classification","OFFLINE_ORIGINAL_SEC_US_GAAP_NOT_IMPORTED","sourceSha256",hash,"cik","1618921","priorFacts",facts.size(),"candidateRows",candidates.size(),"acceptedRows",rows.size(),"rejectedRows",rejected.size(),"actualFiscalAnchorObservations",anchors.size(),"shares",shares.size(),"coverage",coverage));
        require(hash.equals(sha(source)),"Original drift");
        System.out.println("WBA_SOURCE_PREPARED rows="+rows.size()+" shares="+shares.size()+" rejected="+rejected.size()+" "+coverage);
    }
}
