import com.kingyurina.demo.stock.*;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/** File-only counterfactual. Does not start Spring or connect to any database. */
public final class QuantRestandardizedLeakageCheck {
    static ObjectMapper json=new ObjectMapper();
    static <T> List<T> list(JsonNode node,Class<T> type){var out=new ArrayList<T>();for(var n:node)out.add(json.treeToValue(n,type));return out;}
    static String normalized(List<StockExpectedReturnSnapshot> predictions){
        var out=new TreeMap<String,JsonNode>();
        for(var prediction:predictions){ObjectNode n=(ObjectNode)json.valueToTree(prediction);n.remove("calculatedAt");out.put(QuantRestandardizedReplay.key(prediction),n);}
        return json.writeValueAsString(out);
    }
    public static void main(String[] args)throws Exception{
        QuantRestandardizedReplay.require(args.length==4,"Usage: replay-dir prepared-input.json decision NEW-OUTPUT-DIR");
        QuantRestandardizedReplay.json=json;
        var replay=Path.of(args[0]);var input=json.readTree(Files.readString(Path.of(args[1])));var day=LocalDate.parse(args[2]);var output=Path.of(args[3]);
        QuantRestandardizedReplay.require(!Files.exists(output),"Refuse output overwrite");Files.createDirectories(output);
        var context=json.readTree(Files.readString(replay.resolve("frozen-context.json")));
        var report=json.readTree(Files.readString(replay.resolve("result.json")));
        var date=report.path("dates").valueStream().filter(d->args[2].equals(d.path("decision").asText())).findFirst().orElseThrow();
        var source=list(context.path("source"),StockBacktestResult.class);
        var existing=list(context.path("existing"),StockExpectedReturnSnapshot.class);
        var benchmarks=list(context.path("benchmark"),StockBenchmarkReturn.class);
        var macro=list(context.path("macro"),StockMacroFeatureSnapshot.class);
        var allMarket=list(context.path("market"),StockMarketSnapshot.class);
        var market=new TreeMap<String,StockMarketSnapshot>();
        for(var m:allMarket)if(day.equals(m.getSnapshotDate()))market.put(m.getSymbol(),m);
        for(var m:input.path("marketOverrides").path(args[2]).properties())market.put(m.getKey(),json.treeToValue(m.getValue(),StockMarketSnapshot.class));
        var old=new TreeMap<String,StockBacktestResult>();for(var r:source)if(day.equals(r.getSignalDate())&&Integer.valueOf(20).equals(r.getHorizonDays()))old.put(r.getSymbol(),r);
        var signals=list(date.path("newSignals"),StockSignalSnapshot.class);
        var target=new ArrayList<StockBacktestResult>();
        for(var signal:signals){
            var pair=date.path("pairs").valueStream().filter(p->signal.getSymbol().equals(p.path("symbol").asText())).findFirst().orElseThrow();
            var entry=new StockCandleDaily();entry.setSymbol(signal.getSymbol());entry.setTradeDate(LocalDate.parse(pair.path("entry").asText()));entry.setClosePrice(new BigDecimal(pair.path("entryClose").asText()));
            var exit=new StockCandleDaily();exit.setSymbol(signal.getSymbol());exit.setTradeDate(LocalDate.parse(pair.path("exit").asText()));exit.setClosePrice(new BigDecimal(pair.path("exitClose").asText()));
            boolean recoveredCap=input.path("oldRecoveredTargetCapSymbols").path(args[2]).valueStream().anyMatch(n->signal.getSymbol().equals(n.asText()));
            target.add(QuantRestandardizedReplay.outcome(signal,entry,exit,old.get(signal.getSymbol()),recoveredCap?market.get(signal.getSymbol()):null));
        }
        QuantRestandardizedReplay.require(target.size()==101,"Full101 target required");
        String sourceBefore=QuantRestandardizedReplay.objectHash(source), existingBefore=QuantRestandardizedReplay.objectHash(existing);
        var baselineWrites=new TreeMap<String,Integer>();var perturbWrites=new TreeMap<String,Integer>();
        var baseline=QuantRestandardizedReplay.models(day,target,source,existing,benchmarks,macro,new ArrayList<>(market.values()),baselineWrites);
        var altered=new ArrayList<StockBacktestResult>();
        for(var r:target){var c=json.convertValue(r,StockBacktestResult.class);c.setForwardReturnPct(BigDecimal.valueOf(-50));altered.add(c);}
        var perturbed=QuantRestandardizedReplay.models(day,altered,source,existing,benchmarks,macro,new ArrayList<>(market.values()),perturbWrites);
        QuantRestandardizedReplay.require(normalized(baseline).equals(normalized(perturbed)),"Future target labels changed forecasts");
        var saved=list(report.path("predictions"),StockExpectedReturnSnapshot.class).stream().filter(p->day.equals(p.getSignalDate())).toList();
        QuantRestandardizedReplay.require(normalized(baseline).equals(normalized(saved)),"File-only baseline does not reproduce original replay");
        QuantRestandardizedReplay.require(sourceBefore.equals(QuantRestandardizedReplay.objectHash(source))&&existingBefore.equals(QuantRestandardizedReplay.objectHash(existing)),"Frozen context mutation");
        var hashes=new TreeMap<String,String>();
        for(String p:List.of(replay.resolve("result.json").toString(),replay.resolve("frozen-context.json").toString(),args[1],"scripts/QuantRestandardizedLeakageCheck.java","scripts/QuantRestandardizedReplay.java"))hashes.put(p,QuantRestandardizedReplay.sha(p));
        var result=new LinkedHashMap<String,Object>();result.put("status","PASS");result.put("decision",day);result.put("targetRowsPerturbed",101);result.put("predictionsCompared",baseline.size());result.put("modelCount",8);
        result.put("allPredictionFieldsExceptCalculatedAtUnchanged",true);result.put("baselineExactlyReproducesSavedReplay",true);result.put("frozenTrainingUnchanged",true);result.put("databaseConnections",0);result.put("databaseWrites",0);result.put("promotionAllowed",false);
        result.put("baselineCapturedWrites",baselineWrites);result.put("perturbedCapturedWrites",perturbWrites);result.put("inputHashes",hashes);
        result.put("limitations",List.of("Target-label perturbation only; not a proof of source receipt-time/PIT integrity or all possible leakage modes.","Future target returns may still enter separately captured diagnostic calibration; no calibration state is rolled into prediction training."));
        Files.writeString(output.resolve("verification.json"),json.writerWithDefaultPrettyPrinter().writeValueAsString(result),StandardOpenOption.CREATE_NEW);
        System.out.println(json.writerWithDefaultPrettyPrinter().writeValueAsString(result));
    }
}
