import com.kingyurina.demo.CodexDemoApplication;
import com.kingyurina.demo.stock.*;
import com.kingyurina.demo.stock.evaluation.*;
import org.springframework.boot.*;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import tools.jackson.databind.ObjectMapper;
import java.math.*;
import java.nio.file.*;
import java.time.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.stream.Collectors;

/** One new decision only. Read-only DB inputs, causal reconstructed features, captured writes. */
public final class QuantMarchModelRecovery {
    static final String SOURCE="NASDAQ100_FREE_20261018",MACRO="NASDAQ100_MACRO_FREE_20261018",RESEARCH="NASDAQ100_MARCH_RESEARCH_20261031";
    static final LocalDate DAY=LocalDate.parse("2026-03-13");
    static final List<String> FACTORS=List.of("Valuation","Quality","Growth","Stability","Earnings","Analyst","News","Momentum","Risk","Institution");
    static final int[] WEIGHTS={14,17,14,9,13,7,4,8,8,6};
    static String key(StockExpectedReturnSnapshot p){return p.getModelVersion()+"/"+p.getSignalDate()+"/"+p.getSymbol()+"/"+p.getHorizonDays();}
    static String sha(String p)throws Exception{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(Path.of(p))));}
    public static void main(String[] args)throws Exception{
        boolean perturb=args.length==1&&args[0].equals("--perturb-target-outcomes");
        if(args.length>0&&!perturb)throw new IllegalArgumentException("Only target perturbation supported");
        var root=Path.of("tmp/quant-march-model-v3-"+(perturb?"perturbed":"final")+"-20261031");
        if(Files.exists(root.resolve("result.json")))throw new IllegalStateException("Refuse completed output overwrite");Files.createDirectories(root);
        var app=new SpringApplication(CodexDemoApplication.class);app.setWebApplicationType(WebApplicationType.NONE);
        try(var ctx=app.run("--spring.profiles.active=mariadb","--spring.sql.init.mode=never","--app.signal.refresh.enabled=false","--app.signal.refresh.run-on-startup=false")){
            var json=ctx.getBean(ObjectMapper.class);var real=ctx.getBean(StockBacktestMapper.class);
            var sec=json.readTree(Files.readString(Path.of("tmp/quant-march-inputs-20261031/inputs-v3.json")));
            var nativeInput=json.readTree(Files.readString(Path.of("tmp/quant-march-inputs-20261031/native-inputs-v2.json")));
            for(var field:sec.path("inputHashes").properties())if(!sha(field.getKey()).equals(field.getValue().asText()))throw new IllegalStateException("Changed input "+field.getKey());
            var pop=new TreeSet<String>();for(var s:nativeInput.path("population"))pop.add(s.asText());
            if(pop.size()!=101)throw new IllegalStateException("Incomplete population");
            var target=new ArrayList<StockBacktestResult>();for(var n:nativeInput.path("existingOutcomes"))target.add(json.treeToValue(n,StockBacktestResult.class));
            var missing=new TreeSet<>(pop);target.forEach(r->missing.remove(r.getSymbol()));
            if(target.size()!=87||missing.size()!=14)throw new IllegalStateException("Wrong missing population");
            var bundle=json.readTree(Files.readString(Path.of("tmp/pit-acquisition/actions/bundle.json")));
            var holdings=new TreeMap<String,tools.jackson.databind.JsonNode>();for(var h:sec.path("holdings"))if(holdings.put(h.path("symbol").asText(),h)!=null)throw new IllegalStateException("Duplicate holding");
            var benchmarks=real.findBenchmarkReturns(SOURCE,LocalDate.parse("2016-01-01"),LocalDate.parse("2026-10-18"));
            var recovered=new ArrayList<Object>();
            for(String symbol:missing){
                var source=bundle.path("sources").valueStream().filter(s->s.path("id").asText().equals("yahoo-chart-"+symbol)).findFirst().orElseThrow();
                var candles=StockCandleReconciliation.readSnapshot(new StockHistoricalEvidenceBundle.Source(symbol+"-research-prices",source.path("url").asText(),"Yahoo Finance",null,
                    source.path("retrievedAt").asText(),source.path("sha256").asText(),"tmp/pit-acquisition/actions/"+source.path("path").asText()),symbol,json);
                var financials=new ArrayList<SecFinancialStandard>();for(var n:sec.path("financialsBySymbol").path(symbol))financials.add(json.treeToValue(n,SecFinancialStandard.class));
                if(financials.isEmpty()||financials.stream().anyMatch(f->f.getFiledAt()==null||!f.getFiledAt().isBefore(DAY)))throw new IllegalStateException("Noncausal/missing financials");
                HistoricalBacktestSeedMapper seed=(HistoricalBacktestSeedMapper)Proxy.newProxyInstance(QuantMarchModelRecovery.class.getClassLoader(),new Class[]{HistoricalBacktestSeedMapper.class},(p,m,a)->{
                    if(!symbol.equals(a[0]))throw new IllegalStateException("Cross-symbol lookup");
                    return switch(m.getName()){
                        case "findCandlesOnOrBefore" -> candles.stream().filter(c->!c.getTradeDate().isAfter((LocalDate)a[1])).sorted(Comparator.comparing(StockCandleDaily::getTradeDate).reversed()).limit((Integer)a[2]).collect(Collectors.toCollection(ArrayList::new));
                        case "findLatestAnnualAsOf" -> choose(financials,true,null,(LocalDate)a[1]);
                        case "findPreviousAnnualAsOf" -> choose(financials,true,(LocalDate)a[1],(LocalDate)a[2]);
                        case "findLatestQuarterAsOf" -> choose(financials,false,null,(LocalDate)a[1]);
                        case "findPreviousQuarterAsOf" -> choose(financials,false,(LocalDate)a[1],(LocalDate)a[2]);
                        case "findRecommendationAsOf","findInstitutionFlowAsOf","findNewsStatsAsOf" -> m.invoke(ctx.getBean(HistoricalBacktestSeedMapper.class),a);
                        default -> throw new IllegalStateException("Unknown seed method "+m.getName());
                    };
                });
                var beans=new StaticListableBeanFactory();beans.addBean("seed",seed);
                var builder=new HistoricalBacktestSeedService(beans.getBeanProvider(HistoricalBacktestSeedMapper.class),ctx.getBeanProvider(IndexConstituentMapper.class),ctx.getBeanProvider(StockSignalSnapshotMapper.class),ctx.getBean(StockBacktestService.class),json);
                var build=HistoricalBacktestSeedService.class.getDeclaredMethod("buildSnapshot",HistoricalBacktestSeedMapper.class,String.class,LocalDate.class);build.setAccessible(true);
                var signal=(StockSignalSnapshot)build.invoke(builder,seed,symbol,DAY);if(signal==null)throw new IllegalStateException("Cannot build "+symbol);
                var h=Objects.requireNonNull(holdings.get(symbol));var entry=LocalDate.parse(h.path("entry").asText());var exit=LocalDate.parse(h.path("exit").asText());
                var first=candles.stream().filter(c->entry.equals(c.getTradeDate())).findFirst().orElseThrow();var last=candles.stream().filter(c->exit.equals(c.getTradeDate())).findFirst().orElseThrow();
                var sessions=benchmarks.stream().filter(b->!b.getTradeDate().isBefore(entry)&&!b.getTradeDate().isAfter(exit)).map(StockBenchmarkReturn::getTradeDate).collect(Collectors.toSet());
                var actualSessions=candles.stream().filter(c->!c.getTradeDate().isBefore(entry)&&!c.getTradeDate().isAfter(exit)).map(StockCandleDaily::getTradeDate).collect(Collectors.toSet());
                if(sessions.size()!=21||!sessions.equals(actualSessions))throw new IllegalStateException("Incomplete exact holding calendar");
                var toOutcome=StockBacktestService.class.getDeclaredMethod("toBacktestResult",StockSignalSnapshot.class,int.class,StockCandleDaily.class,StockCandleDaily.class,String.class);toOutcome.setAccessible(true);
                var result=(StockBacktestResult)toOutcome.invoke(null,signal,20,first,last,"UNKNOWN");result.setCalculatedAt(LocalDateTime.now());
                if(Math.abs(result.getForwardReturnPct().doubleValue()-h.path("actualReturnPct").asDouble())>.00002)throw new IllegalStateException("Outcome basis mismatch");
                if(symbol.equals("TEAM"))result.setMarketCap(new BigDecimal(sec.path("teamMarket").path("marketCapUsd").asText()).divide(BigDecimal.valueOf(1000000)));
                target.add(result);recovered.add(Map.of("symbol",symbol,"signal",signal,"priceObservationsBeforeDecision",sec.path("priorPriceCounts").path(symbol).asInt(),"financialRowCount",financials.size()));
            }
            if(target.size()!=101||!target.stream().map(StockBacktestResult::getSymbol).collect(Collectors.toSet()).equals(pop))throw new IllegalStateException("Incomplete target");
            // Existing population ranking, recomputed for all101; historic training context stays frozen.
            var signals=new ArrayList<StockSignalSnapshot>();
            for(var r:target){var s=new StockSignalSnapshot();s.setSymbol(r.getSymbol());s.setSignalDate(DAY);int weighted=0;
                for(int i=0;i<FACTORS.size();i++){var v=(Integer)StockBacktestResult.class.getMethod("get"+FACTORS.get(i)+"Score").invoke(r);if(v==null)throw new IllegalStateException("Missing factor");StockSignalSnapshot.class.getMethod("set"+FACTORS.get(i)+"Score",Integer.class).invoke(s,v);weighted+=v*WEIGHTS[i];}
                s.setIntegratedScore(Math.round(weighted/100f));s.setDataQualityScore(r.getDataQualityScore());signals.add(s);}
            var rank=HistoricalBacktestSeedService.class.getDeclaredMethod("applyHistoricalRankScores",List.class);rank.setAccessible(true);rank.invoke(null,signals);
            for(var r:target)r.setIntegratedScore(signals.stream().filter(s->s.getSymbol().equals(r.getSymbol())).findFirst().orElseThrow().getIntegratedScore());
            if(perturb)target.forEach(r->r.setForwardReturnPct(BigDecimal.valueOf(-50)));
            var outcomes=real.findResults(SOURCE,200000);String originalState=json.writeValueAsString(outcomes);
            var working=outcomes.stream().filter(r->r.getSignalDate().isBefore(DAY)).map(r->json.convertValue(r,StockBacktestResult.class)).collect(Collectors.toCollection(ArrayList::new));working.addAll(target);
            var state=new LinkedHashMap<String,StockExpectedReturnSnapshot>();
            for(var p:real.findExpectedReturnSnapshots(SOURCE,LocalDate.parse("2018-01-01"),DAY.minusDays(1))){var clone=json.convertValue(p,StockExpectedReturnSnapshot.class);clone.setIndexCode(RESEARCH);state.put(key(clone),clone);}
            var market=new ArrayList<StockMarketSnapshot>();for(var n:nativeInput.path("nativeMarketInputs"))market.add(json.treeToValue(n,StockMarketSnapshot.class));
            var tm=sec.path("teamMarket");var team=new StockMarketSnapshot();team.setIndexCode(RESEARCH);team.setSnapshotDate(DAY);team.setSymbol("TEAM");team.setCurrency("USD");
            team.setClosePrice(new BigDecimal(tm.path("closePrice").asText()));team.setSharesOutstanding(new BigDecimal(tm.path("sharesOutstanding").asText()));team.setMarketCapUsd(new BigDecimal(tm.path("marketCapUsd").asText()));
            team.setPriceSource(tm.path("priceSource").asText());team.setSharesSource(tm.path("sharesSource").asText());team.setMarketCapSource(tm.path("marketCapSource").asText());market.add(team);
            if(market.size()!=101||!market.stream().map(StockMarketSnapshot::getSymbol).collect(Collectors.toSet()).equals(pop))throw new IllegalStateException("Incomplete market");
            var macro=real.findMacroFeatureSnapshots(MACRO,LocalDate.parse("2023-01-01"),DAY);var writes=new TreeMap<String,Integer>();
            StockBacktestMapper memory=(StockBacktestMapper)Proxy.newProxyInstance(QuantMarchModelRecovery.class.getClassLoader(),new Class[]{StockBacktestMapper.class},(p,m,a)->{
                String name=m.getName();
                if(name.equals("findResults"))return working;
                if(name.equals("findBenchmarkReturns"))return benchmarks.stream().map(b->{var c=json.convertValue(b,StockBenchmarkReturn.class);c.setIndexCode(RESEARCH);return c;}).toList();
                if(name.equals("findExpectedReturnSnapshots"))return state.values().stream().filter(s->!s.getSignalDate().isBefore((LocalDate)a[1])&&!s.getSignalDate().isAfter((LocalDate)a[2])).toList();
                if(name.equals("findMacroRegimeSnapshots"))return List.of();
                if(name.equals("findMacroFeatureSnapshots"))return macro.stream().filter(f->!f.getSnapshotDate().isAfter(DAY)).toList();
                if(name.equals("findMarketSnapshots"))return market.stream().filter(s->!s.getSnapshotDate().isBefore((LocalDate)a[1])&&!s.getSnapshotDate().isAfter((LocalDate)a[2])).toList();
                if(name.equals("upsertExpectedReturnSnapshot")){var s=(StockExpectedReturnSnapshot)a[0];if(!RESEARCH.equals(s.getIndexCode())||!DAY.equals(s.getSignalDate()))throw new IllegalStateException("Out-of-scope write");s.setCalculatedAt(LocalDateTime.now());state.put(key(s),s);}
                else if(!Set.of("deleteExpectedReturnFactorContributions","copyExpectedReturnFactorContributions","upsertFactorExposureSnapshot","upsertExpectedReturnFactorContribution","deleteExpectedReturnCalibrations","upsertExpectedReturnCalibration").contains(name))throw new IllegalStateException("Unknown operation; never delegate "+name);
                writes.merge(name,1,Integer::sum);return m.getReturnType()==void.class?null:1;
            });
            var beans=new StaticListableBeanFactory();beans.addBean("mapper",memory);var provider=beans.getBeanProvider(StockBacktestMapper.class);var service=new StockPortfolioBacktestService(provider,json,0);
            var stages=new ArrayList<Object>();stages.add(service.refreshExpectedReturnSnapshots(RESEARCH,200000,1,300));stages.add(service.refreshExpectedReturnV3Snapshots(RESEARCH,200000,1,300));
            stages.add(service.refreshExpectedReturnV4Snapshots(RESEARCH,200000,1,300));stages.add(service.refreshExpectedReturnV5Snapshots(RESEARCH,200000,1,300));stages.add(service.refreshExpectedReturnV6Snapshots(RESEARCH,200000,1,300));stages.add(service.refreshExpectedReturnV7Snapshots(RESEARCH,200000,1,300));
            stages.add(new StockExpectedReturnV8Service(provider).refreshHistorical(RESEARCH,200000,1));stages.add(new StockExpectedReturnV9Service(provider).refreshHistorical(RESEARCH,200000,1));
            var predictions=state.values().stream().filter(p->DAY.equals(p.getSignalDate())&&Integer.valueOf(20).equals(p.getHorizonDays())).toList();var models=new ArrayList<Object>();
            for(int version=2;version<=9;version++){String model="EXPECTED_RETURN_V"+version+"_CORRECTED_20261017";var ps=predictions.stream().filter(p->model.equals(p.getModelVersion())).toList();
                if(ps.size()!=101||!ps.stream().map(StockExpectedReturnSnapshot::getSymbol).collect(Collectors.toSet()).equals(pop))throw new IllegalStateException("Incomplete model "+model);
                var evaluation=new ArrayList<ExpectedReturnEvaluationRow>();
                for(var p:ps){var r=target.stream().filter(t->t.getSymbol().equals(p.getSymbol())).findFirst().orElseThrow();double b=Objects.requireNonNull(CompleteBenchmarkReturn.compound(benchmarks,SOURCE,r.getEntryTradeDate(),r.getExitTradeDate(),20));
                    evaluation.add(new ExpectedReturnEvaluationRow("RECOVERED_MARCH_RESEARCH_NOT_PIT",RESEARCH,model,DAY,p.getCalculatedAt(),r.getExitTradeDate(),p.getSymbol(),p.getExpectedExcessReturnPct().doubleValue(),r.getForwardReturnPct().doubleValue()-b,p.getCalibratedUpsideProbabilityPct().doubleValue(),p.getExcessP10Pct().doubleValue(),p.getExcessP90Pct().doubleValue(),r.getForwardReturnPct().doubleValue(),b,r.getSector(),"UNKNOWN"));}
                models.add(Map.of("model",model,"predictions",101,"fullPopulation",true,"missingSymbols",List.of(),"forecastMetrics",new ExpectedReturnMetricsCalculator().calculate(evaluation,101),"portfolio",new ExpectedReturnPortfolioEvaluator(20).evaluate(evaluation,10)));}
            if(predictions.size()!=808||!originalState.equals(json.writeValueAsString(outcomes)))throw new IllegalStateException("Wrong count/source mutation");
            var hashes=new TreeMap<String,String>();for(String path:List.of("scripts/QuantMarchModelRecovery.java","scripts/quant_march_inputs.py","tmp/quant-march-inputs-20261031/inputs-v3.json","tmp/quant-march-inputs-20261031/native-inputs-v2.json","tmp/quant-complete-window-members-20261018.tsv","tmp/quant-complete-window-benchmarks-20261018.tsv"))hashes.put(path,sha(path));
            var report=new LinkedHashMap<String,Object>();report.put("classification","EXISTING_FORMULA_MARCH_RECOVERY_RESEARCH_NOT_PIT");report.put("databaseWrites",0);report.put("promotionAllowed",false);report.put("targetOutcomePerturbation",perturb);report.put("sourceOutcomeIsolationVerified",true);report.put("predictions",predictions);report.put("inputHashes",hashes);
            report.put("dates",List.of(Map.of("decision",DAY,"fullPopulation",pop,"recoveredInputs",recovered,"stageResults",stages,"capturedWrites",writes,"models",models)));
            report.put("limitations",List.of("Official PIT population/receipt timing unproven; reconstructed now, not live.","Prior training context frozen; recovered current/previous experiments not rolled into historical training.","EUR EPS omitted for USD quotes without FX; PDD reported USD perordinary EPS scaled4perADS retaining rounding.","Unobserved recent foreign standalone quarters and analyst/news/institution factors retain existing explicit missing-factor policy.","Existing 100 native market inputs/source limitations unchanged; TEAM actualA+B prior-reported company-equity proxy retains weak provenance.","One additional decision may overlap prior cash-to-cash holdings; never claim additional nonoverlap duration without accounting check."));
            Files.writeString(root.resolve("result.json"),json.writerWithDefaultPrettyPrinter().writeValueAsString(report));System.out.println("MARCH_RECOVERY_DONE recovered="+missing.size()+" forecasts="+predictions.size());
        }
    }
    static SecFinancialStandard choose(List<SecFinancialStandard> rows,boolean annual,LocalDate before,LocalDate asof){return rows.stream().filter(f->annual=="FY".equals(f.getFiscalPeriod())&&f.getFiledAt().isBefore(asof)&&(before==null||f.getEndDate().isBefore(before))).sorted(Comparator.comparing(SecFinancialStandard::getEndDate).thenComparing(SecFinancialStandard::getFiledAt).reversed()).findFirst().orElse(null);}
}
