import com.kingyurina.demo.stock.*;
import tools.jackson.databind.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;

/** File-only paired rolling repair of the inspected panels, not complete historical PIT training. */
public final class QuantIfrsRollingTraining {
    static String rowKey(StockBacktestResult r){return r.getSignalDate()+"/"+r.getSymbol()+"/"+r.getHorizonDays();}
    static boolean mature(StockBacktestResult r,LocalDate day){return r.getSignalDate()!=null&&r.getSignalDate().isBefore(day)&&r.getExitTradeDate()!=null&&r.getExitTradeDate().isBefore(day);}
    static boolean changedScores(StockBacktestResult a,StockBacktestResult b)throws Exception{
        if(!Objects.equals(a.getIntegratedScore(),b.getIntegratedScore()))return true;
        for(String factor:QuantRestandardizedReplay.FACTORS){var get=StockBacktestResult.class.getMethod("get"+factor+"Score");if(!Objects.equals(get.invoke(a),get.invoke(b)))return true;}
        return false;
    }
    static List<StockBacktestResult> sourceAt(List<StockBacktestResult> original,List<StockBacktestResult> repairs,LocalDate day){
        var rows=new LinkedHashMap<String,StockBacktestResult>();
        for(var r:original){if(rows.put(rowKey(r),r)!=null)throw new IllegalStateException("Duplicate original training key");}
        var seen=new HashSet<String>();for(var r:repairs){if(!seen.add(rowKey(r)))throw new IllegalStateException("Duplicate repaired training key");if(mature(r,day))rows.put(rowKey(r),r);}
        return rows.values().stream().filter(r->mature(r,day)).toList();
    }
    static List<StockExpectedReturnSnapshot> predictionsAt(List<StockExpectedReturnSnapshot> original,List<StockExpectedReturnSnapshot> repairs,LocalDate day){
        var rows=new LinkedHashMap<String,StockExpectedReturnSnapshot>();for(var p:original)rows.put(QuantRestandardizedReplay.key(p),p);for(var p:repairs)rows.put(QuantRestandardizedReplay.key(p),p);
        return rows.values().stream().filter(p->p.getSignalDate().isBefore(day)).toList();
    }
    public static void main(String[] args)throws Exception{
        if(args.length!=2)throw new IllegalArgumentException("Expected verified-native-replay NEW_OUTPUT_DIRECTORY");
        Path nativeRoot=Path.of(args[0]),out=Path.of(args[1]);if(Files.exists(out))throw new IllegalStateException("Refuse overwrite");Files.createDirectories(out);
        var json=new ObjectMapper();QuantRestandardizedReplay.json=json;QuantRestandardizedLeakageCheck.json=json;QuantIfrsFinancialReplay.json=json;
        var nativeReport=json.readTree(Files.readString(nativeRoot.resolve("result.json")));
        var before=json.readTree(Files.readString(Path.of(QuantIfrsFinancialReplay.BASE)));
        var context=json.readTree(Files.readString(Path.of(QuantIfrsFinancialReplay.CONTEXT)));
        var input=json.readTree(Files.readString(Path.of(QuantIfrsFinancialReplay.INPUT)));
        var hashes=new TreeMap<String,String>();for(String p:List.of(nativeRoot.resolve("result.json").toString(),QuantIfrsFinancialReplay.BASE,QuantIfrsFinancialReplay.CONTEXT,QuantIfrsFinancialReplay.INPUT,"scripts/QuantIfrsRollingTraining.java","scripts/QuantIfrsFinancialReplay.java","scripts/QuantRestandardizedReplay.java"))hashes.put(p,QuantRestandardizedReplay.sha(p));
        Files.copy(Path.of("scripts/QuantIfrsRollingTraining.java"),out.resolve("QuantIfrsRollingTraining.java.source"));
        var source=QuantIfrsFinancialReplay.list(context.path("source"),StockBacktestResult.class);
        var existing=QuantIfrsFinancialReplay.list(context.path("existing"),StockExpectedReturnSnapshot.class);
        var benchmark=QuantIfrsFinancialReplay.list(context.path("benchmark"),StockBenchmarkReturn.class);
        var macro=QuantIfrsFinancialReplay.list(context.path("macro"),StockMacroFeatureSnapshot.class);
        var marketAll=QuantIfrsFinancialReplay.list(context.path("market"),StockMarketSnapshot.class);
        var oldRepairs=new ArrayList<StockBacktestResult>();var newRepairs=new ArrayList<StockBacktestResult>();
        var oldHistory=new ArrayList<StockExpectedReturnSnapshot>();var newHistory=new ArrayList<StockExpectedReturnSnapshot>();
        var dates=new ArrayList<Object>();var allOld=new ArrayList<StockExpectedReturnSnapshot>();var allNew=new ArrayList<StockExpectedReturnSnapshot>();var trainingAudit=new ArrayList<Object>();
        double firstBaselineDifference=0;
        for(var nativeDay:nativeReport.path("dates")){
            String text=nativeDay.path("decision").asText();LocalDate day=LocalDate.parse(text);
            var originalDay=before.path("dates").valueStream().filter(d->text.equals(d.path("decision").asText())).findFirst().orElseThrow();
            var oldSignals=QuantIfrsFinancialReplay.list(originalDay.path("newSignals"),StockSignalSnapshot.class);
            var newSignals=QuantIfrsFinancialReplay.list(nativeDay.path("newSignals"),StockSignalSnapshot.class);
            var market=QuantIfrsFinancialReplay.market(originalDay,input,marketAll);
            var oldTarget=QuantIfrsFinancialReplay.targets(originalDay,oldSignals,input,source,market);
            var newTarget=QuantIfrsFinancialReplay.targets(originalDay,newSignals,input,source,market);
            var oldTraining=sourceAt(source,oldRepairs,day);var newTraining=sourceAt(source,newRepairs,day);
            QuantRestandardizedReplay.require(oldTraining.size()==newTraining.size(),"Training denominator differs");
            var oldByKey=new HashMap<String,StockBacktestResult>();oldTraining.forEach(r->oldByKey.put(rowKey(r),r));int changedTrainingRows=0;
            for(var r:newTraining){var old=oldByKey.get(rowKey(r));QuantRestandardizedReplay.require(old!=null&&Objects.equals(old.getExitTradeDate(),r.getExitTradeDate())&&old.getForwardReturnPct().compareTo(r.getForwardReturnPct())==0,"Paired training labels/windows differ");
                if(changedScores(old,r))changedTrainingRows++;
            }
            var oldPrior=predictionsAt(existing,oldHistory,day);var newPrior=predictionsAt(existing,newHistory,day);
            var oldPred=QuantRestandardizedReplay.models(day,oldTarget,oldTraining,oldPrior,benchmark,macro,new ArrayList<>(market.values()),new TreeMap<>());
            var newPred=QuantRestandardizedReplay.models(day,newTarget,newTraining,newPrior,benchmark,macro,new ArrayList<>(market.values()),new TreeMap<>());
            if(dates.isEmpty())for(var p:oldPred){var saved=before.path("predictions").valueStream().filter(n->p.getModelVersion().equals(n.path("modelVersion").asText())&&text.equals(n.path("signalDate").asText())&&p.getSymbol().equals(n.path("symbol").asText())).findFirst().orElseThrow();firstBaselineDifference=Math.max(firstBaselineDifference,p.getExpectedReturnPct().subtract(new BigDecimal(saved.path("expectedReturnPct").asText())).abs().doubleValue());}
            var pairs=new ArrayList<Map<String,Object>>();for(var n:nativeDay.path("pairs")){
                var pair=new LinkedHashMap<String,Object>();pair.putAll(json.convertValue(n,Map.class));String symbol=n.path("symbol").asText();
                var old=oldPred.stream().filter(p->symbol.equals(p.getSymbol())&&QuantRestandardizedReplay.V9.equals(p.getModelVersion())).findFirst().orElseThrow();
                var next=newPred.stream().filter(p->symbol.equals(p.getSymbol())&&QuantRestandardizedReplay.V9.equals(p.getModelVersion())).findFirst().orElseThrow();
                pair.put("oldAbsolute",old.getExpectedReturnPct());pair.put("oldExcess",old.getExpectedExcessReturnPct());pair.put("newAbsolute",next.getExpectedReturnPct());pair.put("newExcess",next.getExpectedExcessReturnPct());pairs.add(pair);
            }
            dates.add(Map.of("decision",day,"pairs",pairs,"affectedSymbols",nativeDay.path("affectedSymbols")));
            trainingAudit.add(Map.of("decision",day,"pairedMatureTrainingRows",oldTraining.size(),"repairedPriorPanelRowsUsed",oldRepairs.stream().filter(r->mature(r,day)).count(),"changedTrainingScoreRows",changedTrainingRows,"maxTrainingExit",oldTraining.stream().map(StockBacktestResult::getExitTradeDate).max(LocalDate::compareTo).orElseThrow()));
            if(text.equals("2026-03-13")){var altered=new ArrayList<StockBacktestResult>();for(var r:newTarget){var c=json.convertValue(r,StockBacktestResult.class);c.setForwardReturnPct(BigDecimal.valueOf(-50));altered.add(c);}var perturbed=QuantRestandardizedReplay.models(day,altered,newTraining,newPrior,benchmark,macro,new ArrayList<>(market.values()),new TreeMap<>());QuantRestandardizedReplay.require(QuantRestandardizedLeakageCheck.normalized(newPred).equals(QuantRestandardizedLeakageCheck.normalized(perturbed)),"Future target labels changed rolling predictions");}
            // Current outcomes/predictions become candidates ONLY for subsequent decisions;
            // sourceAt still requires an actual exit strictly before the next decision.
            oldRepairs.addAll(oldTarget);newRepairs.addAll(newTarget);oldHistory.addAll(oldPred);newHistory.addAll(newPred);allOld.addAll(oldPred);allNew.addAll(newPred);
            System.out.println("IFRS_ROLLING_DATE_DONE "+day+" matureTraining="+oldTraining.size()+" changedScoreRows="+changedTrainingRows);
        }
        QuantRestandardizedReplay.require(firstBaselineDifference==0,"First-date baseline differs before any panel repair");
        for(var h:hashes.entrySet())QuantRestandardizedReplay.require(QuantRestandardizedReplay.sha(h.getKey()).equals(h.getValue()),"Input changed");
        var report=new LinkedHashMap<String,Object>();report.put("classification","PAIRED_CHRONOLOGICAL_REPAIRED_PANELS_BASE_HISTORY_FROZEN_NOT_COMPLETE_PIT_OR_NEW_HOLDOUT");
        report.put("databaseWrites",0);report.put("databaseConnections",0);report.put("promotionAllowed",false);report.put("weightsChanged",false);report.put("firstDateBaselineMaxAbsDifference",firstBaselineDifference);report.put("inputHashes",hashes);
        report.put("dates",dates);report.put("trainingAudit",trainingAudit);report.put("controlPredictions",allOld);report.put("predictions",allNew);report.put("futureTargetLabelInvariant",true);
        report.put("limitations",List.of("Only these20recovered panels are repaired consistently in later training; earlier history remains frozen.","No model parameter search; both arms share actual matured labels and dates, not independent PIT sources.","Dates already inspected, not a holdout; no promotion or future-error guarantee."));
        Files.writeString(out.resolve("result.json"),json.writerWithDefaultPrettyPrinter().writeValueAsString(report),StandardOpenOption.CREATE_NEW);
        System.out.println("IFRS_ROLLING_DONE dates="+dates.size()+" databaseConnections=0");
    }
}
