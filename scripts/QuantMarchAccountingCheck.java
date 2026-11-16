import com.kingyurina.demo.stock.evaluation.*;
import tools.jackson.databind.ObjectMapper;
import java.math.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/** File-only combined accounting; partial groups excluded, no Spring or database. */
public final class QuantMarchAccountingCheck {
    public static void main(String[] args)throws Exception{
        var json=new ObjectMapper();var members=new HashMap<String,Set<String>>();
        var ml=Files.readAllLines(Path.of("tmp/quant-complete-window-members-20261018.tsv"));
        for(String line:ml.subList(1,ml.size())){var p=line.split("\t");if(p[0].equals("NASDAQ100_MACRO_FREE_20261018")&&!members.computeIfAbsent(p[1],k->new HashSet<>()).add(p[2]))throw new IllegalStateException("Duplicate member");}
        var holdings=new HashMap<String,String[]>();var lines=new ArrayList<>(Files.readAllLines(Path.of("tmp/quant-complete-windows-recovered-20261018/complete-holdings.csv")));
        var added=Files.readAllLines(Path.of("tmp/quant-older-staged-20261030/holdings.csv"));lines.addAll(added.subList(1,added.size()));
        for(String line:lines.subList(1,lines.size())){var p=line.split(",",-1);if(p[0].equals("NASDAQ100_MACRO_FREE_20261018")&&holdings.put(p[1]+"/"+p[4],p)!=null)throw new IllegalStateException("Duplicate holding");}
        var groups=new TreeMap<String,List<ExpectedReturnEvaluationRow>>();var covered=new TreeMap<String,Set<String>>();
        for(String root:List.of("tmp/quant-older-model-recovery-v2-final-20261030","tmp/quant-extended-model-recovery-final-20261018","tmp/quant-team-model-recovery-final-20261018","tmp/quant-v9-historical-market-final-20261031","tmp/quant-march-model-v3-final-20261031")){
            var report=json.readTree(Files.readString(Path.of(root,"result.json")));
            if(report.path("targetOutcomePerturbation").booleanValue()||report.path("promotionAllowed").booleanValue())throw new IllegalStateException("Invalid run");
            var admitted=new HashSet<String>();for(var d:report.path("dates"))for(var m:d.path("models"))if(m.path("fullPopulation").booleanValue()&&m.path("predictions").intValue()==101)admitted.add(d.path("decision").asText()+"/"+m.path("model").asText());
            for(var p:report.path("predictions")){
                String day=p.path("signalDate").asText(),model=p.path("modelVersion").asText(),symbol=p.path("symbol").asText();if(!admitted.contains(day+"/"+model))continue;
                var h=Objects.requireNonNull(holdings.get(day+"/"+symbol));
                double actual=new BigDecimal(h[6]).divide(new BigDecimal(h[5]),18,RoundingMode.HALF_UP).subtract(BigDecimal.ONE).multiply(BigDecimal.valueOf(100)).doubleValue();
                if(Math.abs(actual-Double.parseDouble(h[7]))>1e-9)throw new IllegalStateException("Endpoint mismatch");double b=Double.parseDouble(h[8]);
                if(!covered.computeIfAbsent(model+"/"+day,k->new HashSet<>()).add(symbol))throw new IllegalStateException("Duplicate forecast");
                groups.computeIfAbsent(model,k->new ArrayList<>()).add(new ExpectedReturnEvaluationRow("RESEARCH_NOT_PIT","FILE_ONLY",model,LocalDate.parse(day),LocalDateTime.parse(p.path("calculatedAt").asText()),LocalDate.parse(h[3]),symbol,
                    p.path("expectedExcessReturnPct").doubleValue(),actual-b,p.path("calibratedUpsideProbabilityPct").doubleValue(),p.path("excessP10Pct").doubleValue(),p.path("excessP90Pct").doubleValue(),actual,b,"UNKNOWN","UNKNOWN"));
            }
        }
        covered.forEach((k,p)->{String day=k.substring(k.lastIndexOf('/')+1);if(p.size()!=101||!p.equals(members.get(day)))throw new IllegalStateException("Incomplete population");});
        if(covered.size()!=160||groups.size()!=8||groups.values().stream().anyMatch(r->r.size()!=2020))throw new IllegalStateException("Wrong coverage");
        var combined=new TreeMap<String,Object>();groups.forEach((model,rows)->combined.put(model,new ExpectedReturnPortfolioEvaluator(20).evaluate(rows,10)));
        Files.writeString(Path.of("tmp/quant-march-model-v3-final-20261031/combined-java-accounting.json"),json.writerWithDefaultPrettyPrinter().writeValueAsString(Map.of("modelDateGroups",160,"combined",combined,"databaseWrites",0,"promotionAllowed",false)));
        System.out.println("Verified160 full model/date groups,16160 forecasts; overlapping March13 is not an extra cash-to-cash period");
    }
}
