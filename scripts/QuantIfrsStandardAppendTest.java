import com.kingyurina.demo.stock.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/** Offline source-field corruption tests; never connects to a database. */
public final class QuantIfrsStandardAppendTest {
    static void reject(Runnable r){try{r.run();throw new AssertionError("Unsafe candidate accepted");}catch(IllegalStateException expected){}}
    public static void main(String[] args)throws Exception{
        Path root=Path.of(args[0]);var j=QuantHistoricalUsAppend.JSON;
        if(!"901832".equals(QuantIfrsStandardAppend.sourceCik(j.readTree("{\"cik\":\"0000901832\",\"facts\":{}}")))||!"901832".equals(QuantIfrsStandardAppend.sourceCik(j.readTree("{\"cik\":901832,\"facts\":{}}"))))throw new AssertionError("Equivalent padded source CIK rejected");
        if(QuantIfrsStandardAppend.sourceCik(j.readTree("{\"GFS\":[]}"))!=null)throw new AssertionError("Normalized bundle mistaken for original companyfacts");
        reject(()->QuantIfrsStandardAppend.sourceCik(j.readTree("{\"cik\":\"BAD\",\"facts\":{}}")));
        var facts=new ArrayList<SecCompanyFact>();for(var issuer:j.readTree(Files.readString(root.resolve("native-facts.json"))))for(var f:issuer)facts.add(j.treeToValue(f,SecCompanyFact.class));
        var sourceInputs=new TreeMap<String,String>();for(var e:j.readTree(Files.readString(root.resolve("result.json"))).path("inputHashes").properties())sourceInputs.put(e.getKey(),e.getValue().asText());
        QuantIfrsStandardAppend.verifyOriginalSources(facts,sourceInputs);
        var rows=new ArrayList<Map<String,String>>();for(var issuer:j.readTree(Files.readString(root.resolve("native-standards.json"))))for(var n:issuer){var b=j.treeToValue(n,SecFinancialStandard.class);b.setMappedAt(LocalDateTime.now(ZoneOffset.UTC).withNano(0));rows.add(QuantHistoricalUsAppend.columns(b,FinancialRestandardizationSafety.COLUMNS));}
        int fields=0;for(var row:rows)fields+=QuantIfrsStandardAppend.validateCandidate(row,facts);
        if(rows.size()!=35||fields<200)throw new AssertionError("Expected 35 source-backed standards / >200 fields, got "+rows.size()+" / "+fields);
        var first=rows.getFirst();
        var value=new LinkedHashMap<>(first);value.put("revenue",new BigDecimal(first.get("revenue")).add(BigDecimal.ONE).toString());reject(()->QuantIfrsStandardAppend.validateCandidate(value,facts));
        var cik=new LinkedHashMap<>(first);cik.put("cik","999");reject(()->QuantIfrsStandardAppend.validateCandidate(cik,facts));
        var quote=new LinkedHashMap<>(first);quote.put("eps_unit","USD/quoted-share");reject(()->QuantIfrsStandardAppend.validateCandidate(quote,facts));
        var date=new LinkedHashMap<>(first);date.put("filed_at","2020-01-01");reject(()->QuantIfrsStandardAppend.validateCandidate(date,facts));
        var old=new LinkedHashMap<>(first);old.put("mapped_at","2020-01-01T00:00:00");reject(()->QuantIfrsStandardAppend.validateCandidate(old,facts));
        var year=new LinkedHashMap<>(first);year.put("fiscal_year","2099");reject(()->QuantIfrsStandardAppend.validateCandidate(year,facts));
        var noncalendar=new LinkedHashMap<>(first);noncalendar.put("start_date","2020-07-01");noncalendar.put("end_date","2021-06-30");reject(()->QuantIfrsStandardAppend.validateAnnualRange(noncalendar));
        var annualFrame=new LinkedHashMap<>(first);annualFrame.put("raw_json",first.get("raw_json").replace("CY2021","CY2099"));reject(()->QuantIfrsStandardAppend.validateAnnualRange(annualFrame));
        var tax=new LinkedHashMap<>(first);tax.put("raw_json",first.get("raw_json").replace("ifrs-full","us-gaap"));reject(()->QuantIfrsStandardAppend.validateCandidate(tax,facts));
        var quarter=rows.stream().filter(r->r.get("fiscal_period").startsWith("Q")).findFirst().orElseThrow();
        var frame=new LinkedHashMap<>(quarter);frame.put("raw_json",quarter.get("raw_json").replace("CY20","BAD20"));reject(()->QuantIfrsStandardAppend.validateCandidate(frame,facts));
        reject(()->QuantIfrsStandardAppend.validateCandidate(first,List.of()));
        System.out.println("PASS 35 real standards, "+fields+" exact financial fields; 11 fail-closed corruption cases");
    }
}
