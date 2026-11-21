import com.kingyurina.demo.stock.evaluation.*;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/** Independent accounting check through the existing application evaluator; no Spring or DB. */
public final class QuantCompleteWindowCheck {
    public static void main(String[] args) throws Exception {
        Path root=Path.of("tmp/quant-complete-windows-recovered-20261018");
        var populations=new HashMap<String,Set<String>>();
        var memberLines=Files.readAllLines(Path.of("tmp/quant-complete-window-members-20261018.tsv"));
        for(String line:memberLines.subList(1,memberLines.size())){
            String[] p=line.split("\t",-1);
            if(!populations.computeIfAbsent(p[0]+"/"+p[1],k->new HashSet<>()).add(p[2]))throw new IllegalArgumentException("Duplicate member");
        }
        var grouped=new TreeMap<String,List<ExpectedReturnEvaluationRow>>();
        var actualPopulations=new HashMap<String,Set<String>>();
        var lines=Files.readAllLines(root.resolve("complete-holdings.csv"));
        for(String line:lines.subList(1,lines.size())){
            String[] p=line.split(",",-1);
            // canonical-export source strings may contain commas; numerical columns remain fixed.
            if(p.length<10)throw new IllegalArgumentException("Malformed holdings CSV");
            String index=p[0],key=index+"/"+p[1];
            if(!actualPopulations.computeIfAbsent(key,k->new HashSet<>()).add(p[4]))throw new IllegalArgumentException("Duplicate holding");
            BigDecimal entry=new BigDecimal(p[5]),exit=new BigDecimal(p[6]);
            if(entry.signum()<=0 || exit.signum()<=0)throw new IllegalArgumentException("Invalid endpoints");
            double independentlyComputed=exit.divide(entry,18,java.math.RoundingMode.HALF_UP).subtract(BigDecimal.ONE).multiply(BigDecimal.valueOf(100)).doubleValue();
            if(Math.abs(independentlyComputed-Double.parseDouble(p[7]))>1e-10)throw new IllegalStateException("Return mismatch");
            double benchmark=Double.parseDouble(p[8]);
            grouped.computeIfAbsent(index,k->new ArrayList<>()).add(new ExpectedReturnEvaluationRow(
                "COMMUNITY_UNIVERSE_PRICE_ONLY_NOT_QUANT",index,"ALL_MEMBERS_EQUAL_WEIGHT_DATA_BASELINE",
                LocalDate.parse(p[1]),LocalDateTime.now(),LocalDate.parse(p[3]),p[4],
                0,independentlyComputed-benchmark,Double.NaN,Double.NaN,Double.NaN,
                independentlyComputed,benchmark,"UNKNOWN","UNKNOWN"));
        }
        actualPopulations.forEach((key,symbols)->{
            if(!symbols.equals(populations.get(key)))throw new IllegalStateException("Not full population: "+key);
        });
        var results=new TreeMap<String,Object>();
        grouped.forEach((index,rows)->results.put(index,new ExpectedReturnPortfolioEvaluator(10000).evaluate(rows,10)));
        String result=new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(Map.of(
            "classification","FULL_POPULATION_EQUAL_WEIGHT_PRICE_ONLY_NOT_QUANT_OR_PIT",
            "completeDateCount",actualPopulations.size(),"perSideCostBps",10,"metrics",results));
        Files.writeString(root.resolve("independent-java-accounting.json"),result);
        System.out.println(result);
    }
}
