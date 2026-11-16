import com.kingyurina.demo.CodexDemoApplication;
import com.kingyurina.demo.stock.*;
import com.kingyurina.demo.stock.evaluation.CompleteBenchmarkReturn;
import org.springframework.boot.*;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;
import java.math.*;
import java.nio.file.*;
import java.time.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.stream.Collectors;

/** Offline only: corrected real builder, frozen training, captured model writes, read-only DB. */
public final class QuantRestandardizedReplay {
    static final String SOURCE="NASDAQ100_FREE_20261018", MACRO="NASDAQ100_MACRO_FREE_20261018";
    static final String RESEARCH="NASDAQ100_RESTANDARDIZED_RESEARCH_20261031";
    static final String V9="EXPECTED_RETURN_V9_CORRECTED_20261017";
    static final List<String> FACTORS=List.of("Valuation","Quality","Growth","Stability","Earnings","Analyst","News","Momentum","Risk","Institution");
    static final int[] WEIGHTS={14,17,14,9,13,7,4,8,8,6};
    static final LocalDate START=LocalDate.parse("2016-01-01"), END=LocalDate.parse("2026-10-18");
    static ObjectMapper json;
    static String hash(byte[] bytes)throws Exception{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));}
    static String sha(String p)throws Exception{return hash(Files.readAllBytes(Path.of(p)));}
    static String objectHash(Object value)throws Exception{return hash(json.writeValueAsBytes(value));}
    static String key(StockExpectedReturnSnapshot p){return p.getModelVersion()+"/"+p.getSignalDate()+"/"+p.getSymbol()+"/"+p.getHorizonDays();}
    static void require(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
    static Map<String,Object> databaseState(JdbcTemplate jdbc){
        var out=new TreeMap<String,Object>();
        for(String table:List.of("stock_sec_financial_standard","stock_backtest_result_v2","stock_expected_return_snapshot","stock_market_snapshot","stock_macro_feature_snapshot","stock_candle_daily","stock_signal_snapshot")){
            out.put(table,jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Long.class));
        }
        out.put("financialRawFingerprint",jdbc.queryForObject("SELECT COALESCE(SUM(CRC32(CONCAT_WS('|',id,symbol,COALESCE(raw_json,'')))),0) FROM stock_sec_financial_standard",String.class));
        return out;
    }
    static StockSignalSnapshot oldSignal(StockBacktestResult r)throws Exception{
        var s=new StockSignalSnapshot();s.setSymbol(r.getSymbol());s.setSignalDate(r.getSignalDate());int weighted=0;
        for(int i=0;i<FACTORS.size();i++){
            Integer value=(Integer)StockBacktestResult.class.getMethod("get"+FACTORS.get(i)+"Score").invoke(r);
            require(value!=null,"Missing frozen factor "+r.getSymbol()+FACTORS.get(i));
            StockSignalSnapshot.class.getMethod("set"+FACTORS.get(i)+"Score",Integer.class).invoke(s,value);weighted+=value*WEIGHTS[i];
        }
        s.setIntegratedScore(Math.round(weighted/100f));s.setDataQualityScore(r.getDataQualityScore());return s;
    }
    static void rank(List<StockSignalSnapshot> signals)throws Exception{
        var m=HistoricalBacktestSeedService.class.getDeclaredMethod("applyHistoricalRankScores",List.class);m.setAccessible(true);m.invoke(null,signals);
    }
    static StockBacktestResult outcome(StockSignalSnapshot s,StockCandleDaily entry,StockCandleDaily exit,StockBacktestResult old,StockMarketSnapshot market)throws Exception{
        var m=StockBacktestService.class.getDeclaredMethod("toBacktestResult",StockSignalSnapshot.class,int.class,StockCandleDaily.class,StockCandleDaily.class,String.class);m.setAccessible(true);
        var r=(StockBacktestResult)m.invoke(null,s,20,entry,exit,old==null?"UNKNOWN":old.getSector());
        r.setCalculatedAt(LocalDateTime.now());
        if(old!=null){r.setName(old.getName());r.setMarketCap(old.getMarketCap());}
        else if(market!=null&&market.getMarketCapUsd()!=null)r.setMarketCap(market.getMarketCapUsd().divide(BigDecimal.valueOf(1000000)));
        return r;
    }
    static List<StockExpectedReturnSnapshot> models(LocalDate day,List<StockBacktestResult> target,List<StockBacktestResult> source,
            List<StockExpectedReturnSnapshot> existing,List<StockBenchmarkReturn> benchmark,List<StockMacroFeatureSnapshot> macro,
            List<StockMarketSnapshot> market,Map<String,Integer> writes)throws Exception{
        var working=new ArrayList<StockBacktestResult>();
        for(var r:source)if(r.getSignalDate().isBefore(day))working.add(json.convertValue(r,StockBacktestResult.class));
        working.addAll(target);
        var state=new LinkedHashMap<String,StockExpectedReturnSnapshot>();
        for(var p:existing)if(p.getSignalDate().isBefore(day)){var c=json.convertValue(p,StockExpectedReturnSnapshot.class);c.setIndexCode(RESEARCH);state.put(key(c),c);}
        StockBacktestMapper memory=(StockBacktestMapper)Proxy.newProxyInstance(QuantRestandardizedReplay.class.getClassLoader(),new Class[]{StockBacktestMapper.class},(p,m,a)->{
            String name=m.getName();
            if(name.equals("findResults"))return working;
            if(name.equals("findBenchmarkReturns"))return benchmark.stream().map(b->{var c=json.convertValue(b,StockBenchmarkReturn.class);c.setIndexCode(RESEARCH);return c;}).toList();
            if(name.equals("findExpectedReturnSnapshots"))return state.values().stream().filter(s->!s.getSignalDate().isBefore((LocalDate)a[1])&&!s.getSignalDate().isAfter((LocalDate)a[2])).toList();
            if(name.equals("findMacroRegimeSnapshots"))return List.of();
            if(name.equals("findMacroFeatureSnapshots"))return macro.stream().filter(f->!f.getSnapshotDate().isAfter(day)).toList();
            if(name.equals("findMarketSnapshots"))return market.stream().filter(s->!s.getSnapshotDate().isBefore((LocalDate)a[1])&&!s.getSnapshotDate().isAfter((LocalDate)a[2])).toList();
            if(name.equals("upsertExpectedReturnSnapshot")){
                var s=(StockExpectedReturnSnapshot)a[0];require(RESEARCH.equals(s.getIndexCode())&&day.equals(s.getSignalDate()),"Out-of-scope captured write");
                s.setCalculatedAt(LocalDateTime.now());state.put(key(s),s);
            }else if(!Set.of("deleteExpectedReturnFactorContributions","copyExpectedReturnFactorContributions","upsertFactorExposureSnapshot","upsertExpectedReturnFactorContribution","deleteExpectedReturnCalibrations","upsertExpectedReturnCalibration").contains(name))throw new IllegalStateException("Never delegate unexpected model operation "+name);
            writes.merge(name,1,Integer::sum);return m.getReturnType()==void.class?null:1;
        });
        var beans=new StaticListableBeanFactory();beans.addBean("mapper",memory);var provider=beans.getBeanProvider(StockBacktestMapper.class);
        var service=new StockPortfolioBacktestService(provider,json,0);
        service.refreshExpectedReturnSnapshots(RESEARCH,200000,1,300);service.refreshExpectedReturnV3Snapshots(RESEARCH,200000,1,300);
        service.refreshExpectedReturnV4Snapshots(RESEARCH,200000,1,300);service.refreshExpectedReturnV5Snapshots(RESEARCH,200000,1,300);
        service.refreshExpectedReturnV6Snapshots(RESEARCH,200000,1,300);service.refreshExpectedReturnV7Snapshots(RESEARCH,200000,1,300);
        new StockExpectedReturnV8Service(provider).refreshHistorical(RESEARCH,200000,1);
        new StockExpectedReturnV9Service(provider).refreshHistorical(RESEARCH,200000,1);
        var predictions=state.values().stream().filter(s->day.equals(s.getSignalDate())&&Integer.valueOf(20).equals(s.getHorizonDays())).toList();
        require(predictions.size()==808,"Incomplete predictions for "+day+": "+predictions.size());
        for(int v=2;v<=9;v++){String model="EXPECTED_RETURN_V"+v+"_CORRECTED_20261017";
            require(predictions.stream().filter(s->model.equals(s.getModelVersion())).count()==101,"Incomplete model "+model);}
        return predictions;
    }
    static Map<String,Object> metrics(List<Map<String,Object>> pairs){
        var result=new LinkedHashMap<String,Object>();
        for(String target:List.of("Absolute","Excess")){
            double old=0,now=0,control=0;
            for(var p:pairs){double actual=((Number)p.get("actual")).doubleValue()-(target.equals("Excess")?((Number)p.get("benchmark")).doubleValue():0);
                old+=Math.abs(((Number)p.get("old"+target)).doubleValue()-actual);now+=Math.abs(((Number)p.get("new"+target)).doubleValue()-actual);
                control+=Math.abs(((Number)p.get("control"+target)).doubleValue()-actual);}
            result.put(target.toLowerCase(),Map.of("oldMaePp",old/pairs.size(),"newMaePp",now/pairs.size(),"maeDeltaPp",(now-old)/pairs.size(),"controlMaePp",control/pairs.size(),"newVsControlMaeDeltaPp",(now-control)/pairs.size()));
        }
        return result;
    }
    public static void main(String[] args)throws Exception{
        require(args.length>=3&&args.length<=4,"Usage: input.json NEW_OUTPUT_DIR REPAIR_EVIDENCE [YYYY-MM-DD|ALL]");
        String inputPath=args[0],repairEvidence=args[2];var root=Path.of(args[1]);require(!Files.exists(root),"Refuse output overwrite");
        require(Files.isRegularFile(Path.of(repairEvidence)),"Repair evidence required before DB replay");
        Files.createDirectories(root);
        var app=new SpringApplication(CodexDemoApplication.class);app.setWebApplicationType(WebApplicationType.NONE);
        try(var ctx=app.run("--spring.profiles.active=mariadb","--spring.sql.init.mode=never","--spring.datasource.hikari.read-only=true",
                "--spring.datasource.hikari.connection-init-sql=SET SESSION TRANSACTION READ ONLY",
                "--app.signal.refresh.enabled=false","--app.signal.refresh.run-on-startup=false","--spring.task.scheduling.enabled=false")){
            json=ctx.getBean(ObjectMapper.class);var real=ctx.getBean(StockBacktestMapper.class);var seedReal=ctx.getBean(HistoricalBacktestSeedMapper.class);
            var jdbc=ctx.getBean(JdbcTemplate.class);var before=databaseState(jdbc);
            require(jdbc.queryForObject("SELECT @@tx_read_only",Integer.class)==1,"Database session is not read-only");
            var input=json.readTree(Files.readString(Path.of(inputPath)));var hashes=new TreeMap<String,String>();
            hashes.put(inputPath,sha(inputPath));hashes.put(repairEvidence,sha(repairEvidence));
            for(var f:input.path("inputHashes").properties()){require(sha(f.getKey()).equals(f.getValue().asText()),"Changed frozen input "+f.getKey());hashes.put(f.getKey(),f.getValue().asText());}
            for(String p:List.of("scripts/QuantRestandardizedReplay.java","scripts/verify_quant_restandardized_replay.py","src/main/java/com/kingyurina/demo/stock/HistoricalBacktestSeedService.java","src/main/java/com/kingyurina/demo/stock/FinancialInputPolicy.java","src/main/resources/mapper/stock/HistoricalBacktestSeedMapper.xml","src/main/resources/mapper/stock/SecFinancialStandardMapper.xml","src/main/java/com/kingyurina/demo/stock/StockPortfolioBacktestService.java","src/main/java/com/kingyurina/demo/stock/StockExpectedReturnV8Service.java","src/main/java/com/kingyurina/demo/stock/StockExpectedReturnV9Service.java"))hashes.put(p,sha(p));
            for(String name:List.of("HistoricalBacktestSeedService","FinancialInputPolicy","StockBacktestService","StockPortfolioBacktestService","StockExpectedReturnV8Service","StockExpectedReturnV9Service")){
                String p="target/classes/com/kingyurina/demo/stock/"+name+".class";hashes.put(p,sha(p));
            }
            for(String name:List.of("HistoricalBacktestSeedMapper","SecFinancialStandardMapper")){
                String p="target/classes/mapper/stock/"+name+".xml";require(sha(p).equals(sha("src/main/resources/mapper/stock/"+name+".xml")),"Stale SQL resource "+name);hashes.put(p,sha(p));
            }
            var source=real.findResults(SOURCE,200000);var existing=real.findExpectedReturnSnapshots(SOURCE,START,END);
            var benchmark=real.findBenchmarkReturns(SOURCE,START,END);var macro=real.findMacroFeatureSnapshots(MACRO,START,END);
            var nativeMarket=real.findMarketSnapshots(SOURCE,START,END);
            var sourceFingerprints=Map.of("outcomes",objectHash(source),"predictions",objectHash(existing),"benchmark",objectHash(benchmark),"macro",objectHash(macro),"market",objectHash(nativeMarket));
            Files.writeString(root.resolve("frozen-context.json"),json.writeValueAsString(Map.of("source",source,"existing",existing,"benchmark",benchmark,"macro",macro,"market",nativeMarket)),StandardOpenOption.CREATE_NEW);
            hashes.put(root.resolve("frozen-context.json").toString(),sha(root.resolve("frozen-context.json").toString()));
            var prices=new TreeMap<String,NavigableMap<LocalDate,StockCandleDaily>>();
            for(var s:input.path("prices").properties()){
                var series=new TreeMap<LocalDate,StockCandleDaily>();
                for(var n:s.getValue()){
                    var c=new StockCandleDaily();c.setSymbol(s.getKey());c.setTradeDate(LocalDate.parse(n.path("day").asText()));
                    c.setClosePrice(new BigDecimal(n.path("close").asText()));c.setSource(s.getKey().equals("ANSS")?"CHARTEXCHANGE_RESEARCH_UNVERIFIED_ADJUSTMENT":"PRESERVED_CANONICAL_COMPATIBLE_ARCHIVE");
                    require(c.getClosePrice().signum()>0&&series.put(c.getTradeDate(),c)==null,"Invalid duplicate price");
                }
                prices.put(s.getKey(),series);
            }
            var dates=new ArrayList<Object>();var allPredictions=new ArrayList<StockExpectedReturnSnapshot>();var allControl=new ArrayList<StockExpectedReturnSnapshot>();
            var build=HistoricalBacktestSeedService.class.getDeclaredMethod("buildSnapshot",HistoricalBacktestSeedMapper.class,String.class,LocalDate.class);build.setAccessible(true);
            var builder=ctx.getBean(HistoricalBacktestSeedService.class);
            var dayTexts=input.path("dates").propertyNames().stream().sorted().toList();
            for(String dayText:dayTexts){
                if(args.length==4&&!args[3].equals("ALL")&&!args[3].equals(dayText))continue;
                var day=LocalDate.parse(dayText);var population=new TreeSet<String>();for(var s:input.path("dates").path(dayText))population.add(s.asText());require(population.size()==101,"Wrong population");
                var market=new TreeMap<String,StockMarketSnapshot>();
                for(var m:nativeMarket)if(day.equals(m.getSnapshotDate())&&population.contains(m.getSymbol()))market.put(m.getSymbol(),json.convertValue(m,StockMarketSnapshot.class));
                for(var m:input.path("marketOverrides").path(dayText).properties())market.put(m.getKey(),json.treeToValue(m.getValue(),StockMarketSnapshot.class));
                require(market.size()==101&&market.keySet().equals(population),"Incomplete frozen market "+day);
                var oldBySymbol=new TreeMap<String,StockBacktestResult>();for(var r:source)if(day.equals(r.getSignalDate())&&Integer.valueOf(20).equals(r.getHorizonDays())&&population.contains(r.getSymbol()))oldBySymbol.put(r.getSymbol(),r);
                var newSignals=new ArrayList<StockSignalSnapshot>();var oldSignals=new ArrayList<StockSignalSnapshot>();var financialReads=new TreeMap<String,Object>();
                HistoricalBacktestSeedMapper seed=(HistoricalBacktestSeedMapper)Proxy.newProxyInstance(QuantRestandardizedReplay.class.getClassLoader(),new Class[]{HistoricalBacktestSeedMapper.class},(p,m,a)->{
                    if(m.getName().equals("findCandlesOnOrBefore")){
                        String symbol=(String)a[0];var result=new ArrayList<StockCandleDaily>();
                        for(var c:prices.get(symbol).headMap((LocalDate)a[1],true).descendingMap().values()){result.add(json.convertValue(c,StockCandleDaily.class));if(result.size()==(Integer)a[2])break;}
                        return result;
                    }
                    require(Set.of("findLatestAnnualAsOf","findPreviousAnnualAsOf","findLatestQuarterAsOf","findPreviousQuarterAsOf","findRecommendationAsOf","findInstitutionFlowAsOf","findNewsStatsAsOf").contains(m.getName()),"Unexpected seed method "+m.getName());
                    Object value=m.invoke(seedReal,a);
                    if(m.getName().contains("Annual")||m.getName().contains("Quarter"))financialReads.put(a[0]+"/"+m.getName(),value==null?Map.of("missing",true):json.convertValue(value,SecFinancialStandard.class));
                    return value;
                });
                for(String symbol:population){
                    var signal=(StockSignalSnapshot)build.invoke(builder,seed,symbol,day);require(signal!=null,"Cannot rebuild actual signal "+day+" "+symbol);
                    require("q4-hist-fin-20261031".equals(signal.getSignalVersion()),"Stale historical builder class");newSignals.add(signal);
                    var recovered=input.path("oldRecoveredSignals").path(dayText).path(symbol);
                    oldSignals.add(recovered.isMissingNode()?oldSignal(Objects.requireNonNull(oldBySymbol.get(symbol),"Missing frozen target "+symbol)):json.treeToValue(recovered,StockSignalSnapshot.class));
                }
                rank(newSignals);rank(oldSignals);
                var newTarget=new ArrayList<StockBacktestResult>();var oldTarget=new ArrayList<StockBacktestResult>();var pairs=new ArrayList<Map<String,Object>>();
                for(String symbol:population){
                    var h=input.path("holdings").path(dayText).path(symbol);var entry=LocalDate.parse(h.path("entry").asText());var exit=LocalDate.parse(h.path("exit").asText());
                    var first=prices.get(symbol).get(entry);var last=prices.get(symbol).get(exit);require(first!=null&&last!=null,"Missing observed endpoint");
                    require(Math.abs(first.getClosePrice().doubleValue()-h.path("entryClose").asDouble())<0.00002&&Math.abs(last.getClosePrice().doubleValue()-h.path("exitClose").asDouble())<0.00002,"Endpoint basis mismatch "+symbol+day);
                    var expected=benchmark.stream().filter(b->!b.getTradeDate().isBefore(entry)&&!b.getTradeDate().isAfter(exit)).map(StockBenchmarkReturn::getTradeDate).distinct().sorted().toList();
                    var observed=new ArrayList<>(prices.get(symbol).subMap(entry,true,exit,true).keySet());require(expected.size()==21&&expected.equals(observed),"Incomplete exact holding calendar "+symbol+day);
                    double actual=(last.getClosePrice().doubleValue()/first.getClosePrice().doubleValue()-1)*100,b=h.path("benchmarkReturnPct").asDouble();
                    require(Math.abs(actual-h.path("actualReturnPct").asDouble())<0.00002,"Outcome mismatch");
                    var compounded=CompleteBenchmarkReturn.compound(benchmark,SOURCE,entry,exit,20);require(compounded!=null&&Math.abs(compounded-b)<0.00002,"Frozen benchmark mismatch");
                    var previous=oldBySymbol.get(symbol);var ns=newSignals.stream().filter(s->symbol.equals(s.getSymbol())).findFirst().orElseThrow();var os=oldSignals.stream().filter(s->symbol.equals(s.getSymbol())).findFirst().orElseThrow();
                    // Match the old experiments' target metadata, not newly available V9 market coverage.
                    boolean hadRecoveredCap=input.path("oldRecoveredTargetCapSymbols").path(dayText).valueStream().anyMatch(n->symbol.equals(n.asText()));
                    var targetMarket=hadRecoveredCap?market.get(symbol):null;
                    var nr=outcome(ns,first,last,previous,targetMarket);var or=outcome(os,first,last,previous,targetMarket);newTarget.add(nr);oldTarget.add(or);
                    var frozen=input.path("frozenPredictions").path(dayText+"/"+symbol+"/"+V9);
                    var pair=new LinkedHashMap<String,Object>();pair.put("symbol",symbol);pair.put("entry",entry);pair.put("exit",exit);pair.put("actual",h.path("actualReturnPct").asDouble());pair.put("benchmark",b);
                    pair.put("entryClose",first.getClosePrice());pair.put("exitClose",last.getClosePrice());pair.put("expectedHoldingSessions",expected);pair.put("observedHoldingSessions",observed);
                    pair.put("oldAbsolute",frozen.path("expectedReturnPct").asDouble());pair.put("oldExcess",frozen.path("expectedExcessReturnPct").asDouble());pairs.add(pair);
                }
                var frozenTraining=Map.of("outcomes",source.stream().filter(r->r.getSignalDate().isBefore(day)).toList(),"predictions",existing.stream().filter(p->p.getSignalDate().isBefore(day)).toList());
                String oldContext=objectHash(frozenTraining);var oldWrites=new TreeMap<String,Integer>();var newWrites=new TreeMap<String,Integer>();
                System.out.println("REPLAY_DATE_START "+day+" signals="+newSignals.size());
                var control=models(day,oldTarget,source,existing,benchmark,macro,new ArrayList<>(market.values()),oldWrites);
                var predictions=models(day,newTarget,source,existing,benchmark,macro,new ArrayList<>(market.values()),newWrites);
                String newContext=objectHash(frozenTraining);require(oldContext.equals(newContext),"Training context mutation");
                double maxControlDelta=0;
                for(var pair:pairs){String symbol=(String)pair.get("symbol");
                    var n=predictions.stream().filter(p->symbol.equals(p.getSymbol())&&V9.equals(p.getModelVersion())).findFirst().orElseThrow();
                    var o=control.stream().filter(p->symbol.equals(p.getSymbol())&&V9.equals(p.getModelVersion())).findFirst().orElseThrow();
                    pair.put("newAbsolute",n.getExpectedReturnPct());pair.put("newExcess",n.getExpectedExcessReturnPct());pair.put("controlAbsolute",o.getExpectedReturnPct());pair.put("controlExcess",o.getExpectedExcessReturnPct());
                    maxControlDelta=Math.max(maxControlDelta,Math.max(Math.abs(o.getExpectedReturnPct().doubleValue()-((Number)pair.get("oldAbsolute")).doubleValue()),Math.abs(o.getExpectedExcessReturnPct().doubleValue()-((Number)pair.get("oldExcess")).doubleValue())));
                }
                var date=new LinkedHashMap<String,Object>();date.put("decision",day);date.put("population",population);date.put("rebuiltSignals",newSignals.size());date.put("newSignals",newSignals);date.put("financialReads",financialReads);
                date.put("frozenTrainingContextHashOld",oldContext);date.put("frozenTrainingContextHashNew",newContext);date.put("oldCapturedWrites",oldWrites);date.put("newCapturedWrites",newWrites);
                date.put("maxControlVsFrozenPredictionDeltaPp",maxControlDelta);date.put("controlMatchesFrozenWithin2eMinus5",maxControlDelta<0.00002);date.put("pairs",pairs);date.put("metrics",metrics(pairs));
                dates.add(date);allPredictions.addAll(predictions);allControl.addAll(control);
                Files.writeString(root.resolve("date-"+day+".json"),json.writerWithDefaultPrettyPrinter().writeValueAsString(date),StandardOpenOption.CREATE_NEW);
                System.out.println("REPLAY_DATE_DONE "+day+" maxFrozenControlDelta="+maxControlDelta+" "+json.writeValueAsString(metrics(pairs)));
            }
            require(!dates.isEmpty(),"No requested date");var after=databaseState(jdbc);require(before.equals(after),"Database row counts changed");
            require(sourceFingerprints.equals(Map.of("outcomes",objectHash(real.findResults(SOURCE,200000)),"predictions",objectHash(real.findExpectedReturnSnapshots(SOURCE,START,END)),"benchmark",objectHash(real.findBenchmarkReturns(SOURCE,START,END)),"macro",objectHash(real.findMacroFeatureSnapshots(MACRO,START,END)),"market",objectHash(real.findMarketSnapshots(SOURCE,START,END)))),"Source DB input changed");
            var report=new LinkedHashMap<String,Object>();report.put("classification","CORRECTED_INPUT_FROZEN_TRAINING_RECONSTRUCTION_NOT_PIT");report.put("databaseWrites",0);report.put("promotionAllowed",false);report.put("sourceDatabaseBefore",before);report.put("sourceDatabaseAfter",after);report.put("sourceFingerprints",sourceFingerprints);report.put("dates",dates);report.put("predictions",allPredictions);report.put("controlPredictions",allControl);report.put("inputHashes",hashes);
            report.put("limitations",List.of("All20 dates previously explored; no untouched holdout or live forecast claim.","Official PIT population/receipt times and price action basis remain unproven; ANSS is secondary-provider close history.","Frozen original DB training context and prior expected forecasts, not newly corrected historical training.","Actual corrected production financial selection used; missing financial inputs retain actual builder missing policies, never fabricated observed inputs.","Paired old-input control separates replay drift from corrected target-input effects; inspect maxControlVsFrozenPredictionDeltaPp before interpreting frozen comparisons.","Prices use archived canonical-compatible observations; no live market/API calls or production persistence."));
            Files.writeString(root.resolve("result.json"),json.writerWithDefaultPrettyPrinter().writeValueAsString(report),StandardOpenOption.CREATE_NEW);
            System.out.println("RESTANDARDIZED_REPLAY_DONE dates="+dates.size()+" predictions="+allPredictions.size());
        }
    }
}
