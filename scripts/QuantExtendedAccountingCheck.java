import com.kingyurina.demo.stock.evaluation.*;
import tools.jackson.databind.ObjectMapper;
import java.math.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/** File-only application evaluator check of both preserved experiments; no Spring/DB. */
public final class QuantExtendedAccountingCheck {
    public static void main(String[] args)throws Exception{
        var json=new ObjectMapper();var members=new HashMap<String,Set<String>>();
        var ml=Files.readAllLines(Path.of("tmp/quant-complete-window-members-20261018.tsv"));
        for(String line:ml.subList(1,ml.size())){var p=line.split("\t");if(p[0].equals("NASDAQ100_MACRO_FREE_20261018")){
            if(!members.computeIfAbsent(p[1],k->new HashSet<>()).add(p[2]))throw new IllegalStateException("Duplicate member");}}
        var holdings=new HashMap<String,String[]>();
        var lines=Files.readAllLines(Path.of("tmp/quant-complete-windows-recovered-20261018/complete-holdings.csv"));
        for(String line:lines.subList(1,lines.size())){var p=line.split(",",-1);if(p[0].equals("NASDAQ100_MACRO_FREE_20261018")){
            if(holdings.put(p[1]+"/"+p[4],p)!=null)throw new IllegalStateException("Duplicate holding");}}
        var grouped=new TreeMap<String,List<ExpectedReturnEvaluationRow>>();var covered=new TreeMap<String,Set<String>>();
        for(String root:List.of("tmp/quant-extended-model-recovery-final-20261018","tmp/quant-team-model-recovery-final-20261018")){
            var report=json.readTree(Files.readString(Path.of(root,"result.json")));
            if(report.path("targetOutcomePerturbation").booleanValue()||report.path("promotionAllowed").booleanValue())throw new IllegalStateException("Invalid experiment");
            for(var p:report.path("predictions")){
                String day=p.path("signalDate").asText(),model=p.path("modelVersion").asText(),symbol=p.path("symbol").asText();
                var h=Objects.requireNonNull(holdings.get(day+"/"+symbol));
                double actual=new BigDecimal(h[6]).divide(new BigDecimal(h[5]),18,RoundingMode.HALF_UP).subtract(BigDecimal.ONE).multiply(BigDecimal.valueOf(100)).doubleValue();
                if(Math.abs(actual-Double.parseDouble(h[7]))>1e-9)throw new IllegalStateException("Endpoint return mismatch");
                double benchmark=Double.parseDouble(h[8]);
                if(!covered.computeIfAbsent(model+"/"+day,k->new HashSet<>()).add(symbol))throw new IllegalStateException("Duplicate model member");
                grouped.computeIfAbsent(model,k->new ArrayList<>()).add(new ExpectedReturnEvaluationRow("COMBINED_RESEARCH_NOT_PIT","FILE_ONLY_COMBINED",model,
                    LocalDate.parse(day),LocalDateTime.now(),LocalDate.parse(h[3]),symbol,p.path("expectedExcessReturnPct").doubleValue(),actual-benchmark,
                    p.path("calibratedUpsideProbabilityPct").doubleValue(),p.path("excessP10Pct").doubleValue(),p.path("excessP90Pct").doubleValue(),actual,benchmark,"UNKNOWN","UNKNOWN"));
            }
        }
        covered.forEach((key,pop)->{String day=key.substring(key.lastIndexOf('/')+1);if(!pop.equals(members.get(day))||pop.size()!=101)throw new IllegalStateException("Incomplete model population");});
        var combined=new TreeMap<String,Object>();grouped.forEach((model,rows)->combined.put(model,new ExpectedReturnPortfolioEvaluator(20).evaluate(rows,10)));
        var output=Map.of("classification","COMBINED_EXISTING_FORMULA_RESEARCH_NOT_PIT","modelDateGroups",covered.size(),"combined",combined,"databaseWrites",0,"promotionAllowed",false);
        Files.writeString(Path.of("tmp/quant-extended-model-recovery-final-20261018/combined-java-accounting.json"),json.writerWithDefaultPrettyPrinter().writeValueAsString(output));
        System.out.println("Verified "+covered.size()+" complete model/date groups through existing evaluator");
    }
}
