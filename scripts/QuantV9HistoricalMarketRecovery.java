import com.kingyurina.demo.CodexDemoApplication;
import com.kingyurina.demo.stock.*;
import com.kingyurina.demo.stock.evaluation.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import tools.jackson.databind.ObjectMapper;
import java.math.*;
import java.nio.file.*;
import java.time.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.stream.Collectors;

/** V9 only: immutable saved V8 forecasts, read-only native market rows, captured local writes. */
public final class QuantV9HistoricalMarketRecovery {
    static final String SOURCE="NASDAQ100_FREE_20261018", RESEARCH="NASDAQ100_V9_MARKET_RESEARCH_20261031";
    static final String V8="EXPECTED_RETURN_V8_CORRECTED_20261017", V9=StockExpectedReturnV9Service.MODEL_VERSION;
    static final String INPUT="tmp/quant-v9-market-inputs-20261030/inputs.json";
    static final String OLD="tmp/quant-older-model-recovery-v2-final-20261030/result.json";
    static String sha(String path)throws Exception{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(Path.of(path))));}
    public static void main(String[] args)throws Exception{
        boolean perturb=args.length==1&&args[0].equals("--perturb-target-outcomes");
        if(args.length>0&&!perturb)throw new IllegalArgumentException("Only target perturbation supported");
        var root=Path.of("tmp/quant-v9-historical-market-"+(perturb?"perturbed":"final")+"-20261031");
        if(Files.exists(root.resolve("result.json")))throw new IllegalStateException("Refuse completed output overwrite");
        Files.createDirectories(root);
        var app=new SpringApplication(CodexDemoApplication.class);app.setWebApplicationType(WebApplicationType.NONE);
        try(var ctx=app.run("--spring.profiles.active=mariadb","--spring.sql.init.mode=never","--app.signal.refresh.enabled=false","--app.signal.refresh.run-on-startup=false")){
            var json=ctx.getBean(ObjectMapper.class);var real=ctx.getBean(StockBacktestMapper.class);
            var input=json.readTree(Files.readString(Path.of(INPUT)));var old=json.readTree(Files.readString(Path.of(OLD)));
            for(var field:input.path("inputHashes").properties())if(!sha(field.getKey()).equals(field.getValue().asText()))throw new IllegalStateException("Changed input "+field.getKey());
            var nativeMarket=real.findMarketSnapshots(SOURCE,LocalDate.parse("2024-07-01"),LocalDate.parse("2025-05-30"));
            var benchmarks=real.findBenchmarkReturns(SOURCE,LocalDate.parse("2024-07-01"),LocalDate.parse("2025-07-01")).stream().map(b->{var c=json.convertValue(b,StockBenchmarkReturn.class);c.setIndexCode(RESEARCH);return c;}).toList();
            var nativeCopy=json.writeValueAsString(nativeMarket);
            var holdings=new TreeMap<String,List<String[]>>();
            var lines=Files.readAllLines(Path.of("tmp/quant-older-staged-20261030/holdings.csv"));
            for(String line:lines.subList(1,lines.size())){var r=line.split(",",-1);if(!r[0].equals("NASDAQ100_MACRO_FREE_20261018"))throw new IllegalStateException("Wrong holding index");holdings.computeIfAbsent(r[1],k->new ArrayList<>()).add(r);}
            var allPredictions=new ArrayList<StockExpectedReturnSnapshot>();var allEvaluation=new ArrayList<ExpectedReturnEvaluationRow>();
            var dates=new ArrayList<Object>();var usedMarket=new ArrayList<Object>();int recovered=0;
            for(var d:old.path("dates")){
                String dayText=d.path("decision").asText();LocalDate day=LocalDate.parse(dayText);
                var population=new TreeSet<String>();for(var s:d.path("fullPopulation"))if(!population.add(s.asText()))throw new IllegalStateException("Duplicate population");
                if(population.size()!=101)throw new IllegalStateException("Not full population");
                var v8=new ArrayList<StockExpectedReturnSnapshot>();
                for(var p:old.path("predictions"))if(p.path("signalDate").asText().equals(dayText)&&p.path("modelVersion").asText().equals(V8)){
                    var clone=json.treeToValue(p,StockExpectedReturnSnapshot.class);clone.setIndexCode(RESEARCH);v8.add(clone);}
                if(v8.size()!=101||!v8.stream().map(StockExpectedReturnSnapshot::getSymbol).collect(Collectors.toSet()).equals(population))throw new IllegalStateException("Incomplete saved V8");
                String savedV8=json.writeValueAsString(v8);
                var market=new TreeMap<String,StockMarketSnapshot>();
                for(var m:nativeMarket)if(day.equals(m.getSnapshotDate())&&population.contains(m.getSymbol())){
                    if(market.put(m.getSymbol(),json.convertValue(m,StockMarketSnapshot.class))!=null)throw new IllegalStateException("Duplicate native market");}
                int originalCount=market.size();
                for(var n:input.path("marketRowsByDecision").path(dayText)){
                    var evidence=n.path("shareEvidence");String symbol=n.path("symbol").asText();
                    if(!n.path("snapshotDate").asText().equals(dayText)||!population.contains(symbol)||market.containsKey(symbol)
                        ||evidence.path("filedDate").asText().compareTo(dayText)>=0||evidence.path("measurementDate").asText().compareTo(dayText)>=0)
                        throw new IllegalStateException("Wrong/duplicate/noncausal recovered market");
                    var m=new StockMarketSnapshot();m.setIndexCode(RESEARCH);m.setSymbol(symbol);m.setSnapshotDate(day);m.setCurrency(n.path("currency").asText());
                    m.setClosePrice(new BigDecimal(n.path("closePrice").asText()));m.setSharesOutstanding(new BigDecimal(n.path("sharesOutstanding").asText()));m.setMarketCapUsd(new BigDecimal(n.path("marketCapUsd").asText()));
                    if(m.getClosePrice().multiply(m.getSharesOutstanding()).compareTo(m.getMarketCapUsd())!=0)throw new IllegalStateException("Capitalization mismatch");
                    m.setPriceSource(n.path("priceSource").asText());m.setSharesSource(n.path("sharesSource").asText());m.setMarketCapSource(n.path("marketCapSource").asText());
                    market.put(symbol,m);recovered++;
                }
                if(market.size()!=101||!market.keySet().equals(population)||market.values().stream().anyMatch(m->m.getMarketCapUsd()==null||m.getMarketCapUsd().signum()<=0))throw new IllegalStateException("Incomplete market inputs");
                usedMarket.addAll(market.values());
                var target=new ArrayList<StockBacktestResult>();var bySymbol=new TreeMap<String,String[]>();
                for(var h:Objects.requireNonNull(holdings.get(dayText))){
                    if(bySymbol.put(h[4],h)!=null)throw new IllegalStateException("Duplicate holding");
                    var r=new StockBacktestResult();r.setSymbol(h[4]);r.setSignalDate(day);r.setHorizonDays(20);
                    r.setEntryTradeDate(LocalDate.parse(h[2]));r.setExitTradeDate(LocalDate.parse(h[3]));
                    r.setForwardReturnPct(new BigDecimal(perturb?"-50":h[7]));r.setBenchmarkReturnPct(new BigDecimal(h[8]));target.add(r);
                }
                if(!bySymbol.keySet().equals(population)||target.size()!=101)throw new IllegalStateException("Incomplete evaluation holdings");
                var predictions=new TreeMap<String,StockExpectedReturnSnapshot>();var writes=new TreeMap<String,Integer>();
                StockBacktestMapper memory=(StockBacktestMapper)Proxy.newProxyInstance(QuantV9HistoricalMarketRecovery.class.getClassLoader(),new Class[]{StockBacktestMapper.class},(p,m,a)->{
                    String name=m.getName();
                    String namespace=a[0] instanceof StockExpectedReturnSnapshot s?s.getIndexCode():a[0] instanceof StockExpectedReturnCalibration c?c.getIndexCode():(String)a[0];
                    if(!RESEARCH.equals(namespace))throw new IllegalStateException("Unexpected namespace");
                    if(name.equals("findResults"))return target;
                    if(name.equals("findBenchmarkReturns"))return benchmarks.stream().filter(b->!b.getTradeDate().isBefore((LocalDate)a[1])&&!b.getTradeDate().isAfter((LocalDate)a[2])).toList();
                    if(name.equals("findExpectedReturnSnapshots"))return v8.stream().filter(s->!s.getSignalDate().isBefore((LocalDate)a[1])&&!s.getSignalDate().isAfter((LocalDate)a[2])).toList();
                    if(name.equals("findMarketSnapshots"))return market.values().stream().filter(s->!s.getSnapshotDate().isBefore((LocalDate)a[1])&&!s.getSnapshotDate().isAfter((LocalDate)a[2])).toList();
                    if(name.equals("upsertExpectedReturnSnapshot")){
                        var s=(StockExpectedReturnSnapshot)a[0];
                        if(!day.equals(s.getSignalDate())||!V9.equals(s.getModelVersion())||!Integer.valueOf(20).equals(s.getHorizonDays())||!population.contains(s.getSymbol()))throw new IllegalStateException("Unexpected captured forecast");
                        s.setCalculatedAt(LocalDateTime.now());if(predictions.put(s.getSymbol(),s)!=null)throw new IllegalStateException("Duplicate forecast");
                    }else if(!Set.of("deleteExpectedReturnFactorContributions","copyExpectedReturnFactorContributions","deleteExpectedReturnCalibrations","upsertExpectedReturnCalibration").contains(name))throw new IllegalStateException("Unexpected mapper operation; never delegate: "+name);
                    writes.merge(name,1,Integer::sum);return m.getReturnType()==void.class?null:1;
                });
                var beans=new StaticListableBeanFactory();beans.addBean("mapper",memory);
                var stage=new StockExpectedReturnV9Service(beans.getBeanProvider(StockBacktestMapper.class)).refreshHistorical(RESEARCH,200000,1);
                if(predictions.size()!=101||!predictions.keySet().equals(population)||!savedV8.equals(json.writeValueAsString(v8)))throw new IllegalStateException("Missing forecasts or mutated saved V8");
                var evaluation=new ArrayList<ExpectedReturnEvaluationRow>();
                for(var p:predictions.values()){
                    var h=bySymbol.get(p.getSymbol());double actual=perturb?-50:Double.parseDouble(h[7]),b=Double.parseDouble(h[8]);
                    evaluation.add(new ExpectedReturnEvaluationRow("PRIOR_REPORTED_MARKET_RESEARCH_NOT_PIT",RESEARCH,V9,day,p.getCalculatedAt(),LocalDate.parse(h[3]),p.getSymbol(),
                        p.getExpectedExcessReturnPct().doubleValue(),actual-b,p.getCalibratedUpsideProbabilityPct().doubleValue(),p.getExcessP10Pct().doubleValue(),p.getExcessP90Pct().doubleValue(),actual,b,"UNKNOWN","UNKNOWN"));
                }
                allPredictions.addAll(predictions.values());allEvaluation.addAll(evaluation);
                dates.add(Map.of("decision",day,"fullPopulation",population,"nativeMarketCount",originalCount,"recoveredMarketCount",101-originalCount,"stageResults",stage,"capturedWrites",writes,
                    "models",List.of(Map.of("model",V9,"predictions",predictions.size(),"fullPopulation",true,"missingSymbols",List.of(),"forecastMetrics",new ExpectedReturnMetricsCalculator().calculate(evaluation,101),"portfolio",new ExpectedReturnPortfolioEvaluator(20).evaluate(evaluation,10)))));
                System.out.println("V9_DATE_DONE "+day+" native="+originalCount+" recovered="+(101-originalCount)+" predictions="+predictions.size());
            }
            if(recovered!=59||allPredictions.size()!=1111||!nativeCopy.equals(json.writeValueAsString(nativeMarket)))throw new IllegalStateException("Wrong totals or native input mutation");
            var hashes=new TreeMap<String,String>();
            for(String p:List.of(INPUT,OLD,"tmp/quant-older-staged-20261030/holdings.csv","scripts/QuantV9HistoricalMarketRecovery.java","src/main/java/com/kingyurina/demo/stock/StockExpectedReturnV9Service.java","tmp/com/kingyurina/demo/stock/StockExpectedReturnV9Service.class"))hashes.put(p,sha(p));
            var report=new LinkedHashMap<String,Object>();report.put("classification","EXISTING_V9_PRIOR_PUBLIC_SHARES_RESEARCH_NOT_OFFICIAL_PIT");report.put("databaseWrites",0);report.put("promotionAllowed",false);
            report.put("dates",dates);report.put("predictions",allPredictions);report.put("marketInputsUsed",usedMarket);report.put("recoveredMarketInputs",59);report.put("inputHashes",hashes);
            report.put("targetOutcomePerturbation",perturb);report.put("sourceOutcomeIsolationVerified",true);report.put("savedV8Unchanged",true);
            report.put("combinedNonoverlapPortfolio",Map.of(V9,new ExpectedReturnPortfolioEvaluator(20).evaluate(allEvaluation,10)));
            report.put("limitations",List.of("Saved V8 forecasts reused; V2-V8 neither rerun nor changed.","V9 only gates on positive capitalization and applies existing source-category shrinkage; capitalization magnitude is not a new alpha input.","59 new inputs use latest prior-reported actual shares, not exact daily shares, free float or official PIT proof.","Manual TEAM/AZN primary text keeps weak provenance; authentic SEC XBRL keeps existing trust classification.","Native 1052 market rows remain unchanged, including their preexisting source limitations.","Historical full official roster, receipt times, price adjustment/usage rights and complete former-member training context remain unproven.","Future labels used for separate diagnostic calibration only; no calibration copied into later predictions or live state.","Price returns, not dividend/corporate-action reinvested total returns; research only."));
            Files.writeString(root.resolve("result.json"),json.writerWithDefaultPrettyPrinter().writeValueAsString(report));
        }
    }
}
