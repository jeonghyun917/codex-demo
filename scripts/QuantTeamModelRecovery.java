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
import java.util.function.Function;
import java.util.stream.Collectors;

/** Existing model formulas, read-only DB inputs and in-memory captured writes. No DB/model import. */
public final class QuantTeamModelRecovery {
    static final String SOURCE="NASDAQ100_FREE_20261018", MACRO="NASDAQ100_MACRO_FREE_20261018";
    static final String RESEARCH="NASDAQ100_TEAM_INPUT_RESEARCH_20261018";
    static final List<LocalDate> DATES=List.of(LocalDate.parse("2026-01-30"),LocalDate.parse("2026-02-27"),LocalDate.parse("2026-03-27"));
    static final List<String> FACTORS=List.of("Valuation","Quality","Growth","Stability","Earnings","Analyst","News","Momentum","Risk","Institution");
    static final int[] WEIGHTS={14,17,14,9,13,7,4,8,8,6};
    static Path ROOT=Path.of("tmp/quant-team-model-recovery-final-20261018");
    static String key(StockExpectedReturnSnapshot p){return p.getModelVersion()+"/"+p.getSignalDate()+"/"+p.getSymbol()+"/"+p.getHorizonDays();}
    public static void main(String[] args)throws Exception{
        boolean perturb=args.length==1&&"--perturb-target-outcomes".equals(args[0]);
        if(args.length>0&&!perturb)throw new IllegalArgumentException("Only --perturb-target-outcomes is supported");
        if(perturb)ROOT=Path.of("tmp/quant-team-model-recovery-perturbed-20261018");
        if(Files.exists(ROOT.resolve("result.json")))throw new IllegalStateException("Refuse to overwrite completed research output");
        Files.createDirectories(ROOT);
        var app=new SpringApplication(CodexDemoApplication.class);app.setWebApplicationType(WebApplicationType.NONE);
        try(var ctx=app.run("--spring.profiles.active=mariadb","--spring.sql.init.mode=never","--app.signal.refresh.enabled=false","--app.signal.refresh.run-on-startup=false")){
            var json=ctx.getBean(ObjectMapper.class);var real=ctx.getBean(StockBacktestMapper.class);
            var sec=json.readTree(Files.readString(Path.of("tmp/quant-team-inputs-20261018/financial-inputs.json")));
            var shares=json.readTree(Files.readString(Path.of("tmp/quant-team-share-evidence-20261018/share-facts.json")));
            var submissions=json.readTree(Files.readString(Path.of("tmp/quant-team-inputs-20261018/team-submissions.json"))).path("filings").path("recent");
            var bundle=json.readTree(Files.readString(Path.of("tmp/pit-acquisition/actions/bundle.json")));
            var priceSource=bundle.path("sources").valueStream().filter(s->"yahoo-chart-TEAM".equals(s.path("id").asText())).findFirst().orElseThrow();
            var candles=StockCandleReconciliation.readSnapshot(new StockHistoricalEvidenceBundle.Source("team-research-prices",priceSource.path("url").asText(),"Yahoo Finance",null,
                priceSource.path("retrievedAt").asText(),priceSource.path("sha256").asText(),"tmp/pit-acquisition/actions/"+priceSource.path("path").asText()),"TEAM",json);
            var outcomes=real.findResults(SOURCE,200000);
            String originalOutcomeState=json.writeValueAsString(outcomes);
            var benchmarks=real.findBenchmarkReturns(SOURCE,LocalDate.parse("2016-01-01"),LocalDate.parse("2026-10-18"));
            var memberships=real.findEvaluationMembershipSnapshots(MACRO,LocalDate.parse("2026-01-01"),LocalDate.parse("2026-03-27"));
            var existing=real.findExpectedReturnSnapshots(SOURCE,LocalDate.parse("2018-01-01"),LocalDate.parse("2026-03-27"));
            var market=real.findMarketSnapshots(SOURCE,LocalDate.parse("2025-12-01"),LocalDate.parse("2026-03-27"));
            var macro=real.findMacroFeatureSnapshots(MACRO,LocalDate.parse("2023-01-01"),LocalDate.parse("2026-03-27"));
            var output=new ArrayList<Object>();var allPredictions=new ArrayList<StockExpectedReturnSnapshot>();
            var allEvaluation=new HashMap<String,List<ExpectedReturnEvaluationRow>>();
            for(var day:DATES){
                List<SecFinancialStandard> financials=new ArrayList<>();
                for(var node:sec.path("byDecision").path(day.toString()))financials.add(json.treeToValue(node,SecFinancialStandard.class));
                if(financials.isEmpty()||financials.stream().anyMatch(f->f.getFiledAt()==null||!f.getFiledAt().isBefore(day)))throw new IllegalStateException("Noncausal financials");
                HistoricalBacktestSeedMapper seed=(HistoricalBacktestSeedMapper)Proxy.newProxyInstance(QuantTeamModelRecovery.class.getClassLoader(),new Class[]{HistoricalBacktestSeedMapper.class},(p,m,a)->{
                    return switch(m.getName()){
                        case "findCandlesOnOrBefore" -> candles.stream().filter(c->!c.getTradeDate().isAfter((LocalDate)a[1])).sorted(Comparator.comparing(StockCandleDaily::getTradeDate).reversed()).limit((Integer)a[2]).collect(Collectors.toCollection(ArrayList::new));
                        case "findLatestAnnualAsOf" -> choose(financials,true,null,(LocalDate)a[1]);
                        case "findPreviousAnnualAsOf" -> choose(financials,true,(LocalDate)a[1],(LocalDate)a[2]);
                        case "findLatestQuarterAsOf" -> choose(financials,false,null,(LocalDate)a[1]);
                        case "findPreviousQuarterAsOf" -> choose(financials,false,(LocalDate)a[1],(LocalDate)a[2]);
                        // Preserve actual DB historical availability/missingness for remaining inputs.
                        case "findRecommendationAsOf","findInstitutionFlowAsOf","findNewsStatsAsOf" -> m.invoke(ctx.getBean(HistoricalBacktestSeedMapper.class),a);
                        default -> throw new IllegalStateException("Unexpected seed call "+m.getName());
                    };
                });
                var beans=new StaticListableBeanFactory();beans.addBean("seed",seed);
                var builder=new HistoricalBacktestSeedService(beans.getBeanProvider(HistoricalBacktestSeedMapper.class),ctx.getBeanProvider(IndexConstituentMapper.class),
                    ctx.getBeanProvider(StockSignalSnapshotMapper.class),ctx.getBean(StockBacktestService.class),json);
                Method build=HistoricalBacktestSeedService.class.getDeclaredMethod("buildSnapshot",HistoricalBacktestSeedMapper.class,String.class,LocalDate.class);build.setAccessible(true);
                var team=(StockSignalSnapshot)build.invoke(builder,seed,"TEAM",day);
                if(team==null)throw new IllegalStateException("TEAM signal not built");
                var pop=memberships.stream().filter(m->day.equals(m.getSnapshotDate())&&Boolean.TRUE.equals(m.getMember())).map(StockIndexMembershipSnapshot::getSymbol).collect(Collectors.toCollection(TreeSet::new));
                if(pop.size()!=101||!pop.contains("TEAM"))throw new IllegalStateException("Unexpected complete target population");
                var sessionDates=benchmarks.stream().map(StockBenchmarkReturn::getTradeDate).distinct().sorted().toList();
                int entry=Collections.binarySearch(sessionDates,day)+1;
                if(entry<=0)throw new IllegalStateException("Decision is not a benchmark session");
                LocalDate entryDay=sessionDates.get(entry),exitDay=sessionDates.get(entry+20);
                var teamEntry=candles.stream().filter(c->entryDay.equals(c.getTradeDate())).findFirst().orElseThrow();
                var teamExit=candles.stream().filter(c->exitDay.equals(c.getTradeDate())).findFirst().orElseThrow();
                if(candles.stream().filter(c->!c.getTradeDate().isBefore(entryDay)&&!c.getTradeDate().isAfter(exitDay)).count()!=21)throw new IllegalStateException("Incomplete TEAM prices");
                Method toOutcome=StockBacktestService.class.getDeclaredMethod("toBacktestResult",StockSignalSnapshot.class,int.class,StockCandleDaily.class,StockCandleDaily.class,String.class);toOutcome.setAccessible(true);
                var teamResult=(StockBacktestResult)toOutcome.invoke(null,team,20,teamEntry,teamExit,"UNKNOWN");
                teamResult.setCalculatedAt(LocalDateTime.now());
                var share=shares.path("observations").valueStream().filter(s->s.path("accessionNumber").asText().equals(shares.path("decisionMapping").path(day.toString()).asText())).findFirst().orElseThrow();
                if(!LocalDate.parse(share.path("filedDate").asText()).isBefore(day)||!LocalDate.parse(share.path("shareMeasurementDate").asText()).isBefore(day))throw new IllegalStateException("Future shares");
                boolean filingVerified=false;for(int i=0;i<submissions.path("accessionNumber").size();i++){
                    if(share.path("accessionNumber").asText().equals(submissions.path("accessionNumber").get(i).asText()))
                        filingVerified=share.path("filedDate").asText().equals(submissions.path("filingDate").get(i).asText())&&share.path("shareMeasurementDate").asText().equals(submissions.path("reportDate").get(i).asText());
                }
                long countA=share.path("classAShares").longValue(),countB=share.path("classBShares").longValue(),totalShares=share.path("totalCommonShares").longValue();
                if(!filingVerified||countA<=0||countB<=0||countA+countB!=totalShares||!"shares".equals(share.path("unit").asText()))throw new IllegalStateException("Invalid common-share evidence");
                var decisionCandle=candles.stream().filter(c->day.equals(c.getTradeDate())).findFirst().orElseThrow();
                var teamMarket=new StockMarketSnapshot();teamMarket.setIndexCode(RESEARCH);teamMarket.setSymbol("TEAM");teamMarket.setSnapshotDate(day);
                teamMarket.setCurrency("USD");teamMarket.setClosePrice(decisionCandle.getClosePrice());teamMarket.setSharesOutstanding(BigDecimal.valueOf(totalShares));
                teamMarket.setMarketCapUsd(decisionCandle.getClosePrice().multiply(BigDecimal.valueOf(totalShares)));
                teamMarket.setPriceSource("ARCHIVED_YAHOO_CLOSE_RESEARCH");teamMarket.setSharesSource("SEC_TEXT_CURATED_PRIOR_QUARTER_TOTAL_COMMON");
                teamMarket.setMarketCapSource("SEC_TEXT_EQUAL_ECONOMIC_RIGHTS_COMPANY_EQUITY_VALUE_PROXY");teamMarket.setCalculatedAt(LocalDateTime.now());
                teamResult.setMarketCap(teamMarket.getMarketCapUsd().divide(BigDecimal.valueOf(1000000)));
                var dayMarket=new ArrayList<>(market);dayMarket.add(teamMarket);
                var target=outcomes.stream().filter(r->day.equals(r.getSignalDate())&&Integer.valueOf(20).equals(r.getHorizonDays())&&pop.contains(r.getSymbol())).map(r->json.convertValue(r,StockBacktestResult.class)).collect(Collectors.toCollection(ArrayList::new));
                if(target.stream().anyMatch(r->"TEAM".equals(r.getSymbol())))throw new IllegalStateException("Existing TEAM outcome must not be overwritten");
                target.add(teamResult);
                if(target.size()!=101||!target.stream().map(StockBacktestResult::getSymbol).collect(Collectors.toSet()).equals(pop))throw new IllegalStateException("Other missing target inputs");
                // Reapply the existing cross-sectional signal ranking to the full current research population.
                var signalRows=new ArrayList<StockSignalSnapshot>();
                for(var r:target){var s=new StockSignalSnapshot();s.setSymbol(r.getSymbol());s.setSignalDate(day);
                    int weighted=0;
                    for(int i=0;i<FACTORS.size();i++){String factor=FACTORS.get(i);Integer value=(Integer)StockBacktestResult.class.getMethod("get"+factor+"Score").invoke(r);
                        if(value==null)throw new IllegalStateException("Missing existing factor: "+r.getSymbol()+factor);
                        StockSignalSnapshot.class.getMethod("set"+factor+"Score",Integer.class).invoke(s,value);weighted+=value*WEIGHTS[i];}
                    s.setIntegratedScore(Math.round(weighted/100f));s.setDataQualityScore(r.getDataQualityScore());signalRows.add(s);}
                Method rank=HistoricalBacktestSeedService.class.getDeclaredMethod("applyHistoricalRankScores",List.class);rank.setAccessible(true);rank.invoke(null,signalRows);
                for(var r:target)r.setIntegratedScore(signalRows.stream().filter(s->s.getSymbol().equals(r.getSymbol())).findFirst().orElseThrow().getIntegratedScore());
                if(perturb)target.forEach(r->r.setForwardReturnPct(BigDecimal.valueOf(-50))); // Regression only: future target labels cannot alter predictions.
                var working=new ArrayList<StockBacktestResult>();
                outcomes.stream().filter(r->r.getSignalDate().isBefore(day)).forEach(r->working.add(json.convertValue(r,StockBacktestResult.class)));
                working.addAll(target);
                var state=new LinkedHashMap<String,StockExpectedReturnSnapshot>();
                existing.stream().filter(p->p.getSignalDate().isBefore(day)).forEach(p->{var copy=json.convertValue(p,StockExpectedReturnSnapshot.class);copy.setIndexCode(RESEARCH);state.put(key(copy),copy);});
                var writeAudit=new TreeMap<String,Integer>();
                StockBacktestMapper memory=(StockBacktestMapper)Proxy.newProxyInstance(QuantTeamModelRecovery.class.getClassLoader(),new Class[]{StockBacktestMapper.class},(p,m,a)->{
                    String name=m.getName();
                    if(name.equals("findResults"))return working;
                    if(name.equals("findBenchmarkReturns"))return benchmarks.stream().map(b->{var c=json.convertValue(b,StockBenchmarkReturn.class);c.setIndexCode(RESEARCH);return c;}).toList();
                    if(name.equals("findExpectedReturnSnapshots"))return state.values().stream().filter(s->!s.getSignalDate().isBefore((LocalDate)a[1])&&!s.getSignalDate().isAfter((LocalDate)a[2])).toList();
                    if(name.equals("findMacroRegimeSnapshots"))return List.of(); // Same empty research regime as frozen source.
                    if(name.equals("findMacroFeatureSnapshots"))return macro.stream().filter(f->!f.getSnapshotDate().isAfter(day)).toList();
                    if(name.equals("findMarketSnapshots"))return dayMarket;
                    if(name.equals("upsertExpectedReturnSnapshot")){var s=(StockExpectedReturnSnapshot)a[0];
                        if(!RESEARCH.equals(s.getIndexCode())||!day.equals(s.getSignalDate()))throw new IllegalStateException("Out of scope model write");
                        s.setCalculatedAt(LocalDateTime.now());state.put(key(s),s);writeAudit.merge(name,1,Integer::sum);return m.getReturnType()==void.class?null:1;}
                    if(Set.of("deleteExpectedReturnFactorContributions","copyExpectedReturnFactorContributions","upsertFactorExposureSnapshot","upsertExpectedReturnFactorContribution","deleteExpectedReturnCalibrations","upsertExpectedReturnCalibration").contains(name)){
                        writeAudit.merge(name,1,Integer::sum);return m.getReturnType()==void.class?null:1;}
                    throw new IllegalStateException("Unknown mapper operation, no delegation to DB: "+name);
                });
                var modelBeans=new StaticListableBeanFactory();modelBeans.addBean("mapper",memory);
                var provider=modelBeans.getBeanProvider(StockBacktestMapper.class);
                var service=new StockPortfolioBacktestService(provider,json,0);
                System.out.println("MODEL_DATE_START "+day+" targets="+target.size()+" trainingContext="+working.size());
                var stages=new ArrayList<Object>();
                stages.add(service.refreshExpectedReturnSnapshots(RESEARCH,200000,1,300));
                stages.add(service.refreshExpectedReturnV3Snapshots(RESEARCH,200000,1,300));
                stages.add(service.refreshExpectedReturnV4Snapshots(RESEARCH,200000,1,300));
                stages.add(service.refreshExpectedReturnV5Snapshots(RESEARCH,200000,1,300));
                stages.add(service.refreshExpectedReturnV6Snapshots(RESEARCH,200000,1,300));
                stages.add(service.refreshExpectedReturnV7Snapshots(RESEARCH,200000,1,300));
                stages.add(new StockExpectedReturnV8Service(provider).refreshHistorical(RESEARCH,200000,1));
                stages.add(new StockExpectedReturnV9Service(provider).refreshHistorical(RESEARCH,200000,1));
                var perModel=new ArrayList<Object>();
                for(int v=2;v<=9;v++){
                    String model="EXPECTED_RETURN_V"+v+"_CORRECTED_20261017";
                    var ps=state.values().stream().filter(s->model.equals(s.getModelVersion())&&day.equals(s.getSignalDate())&&Integer.valueOf(20).equals(s.getHorizonDays())).toList();
                    var missing=new TreeSet<>(pop);ps.forEach(s->missing.remove(s.getSymbol()));
                    var evaluation=new ArrayList<ExpectedReturnEvaluationRow>();
                    if(missing.isEmpty()&&ps.size()==101){for(var s:ps){var r=target.stream().filter(t->t.getSymbol().equals(s.getSymbol())).findFirst().orElseThrow();
                        Double b=CompleteBenchmarkReturn.compound(benchmarks,SOURCE,r.getEntryTradeDate(),r.getExitTradeDate(),20);
                        if(b==null||s.getExpectedExcessReturnPct()==null)throw new IllegalStateException("Incomplete evaluation input");
                        evaluation.add(new ExpectedReturnEvaluationRow("RECOVERED_INPUT_RESEARCH_NOT_PIT",RESEARCH,model,day,s.getCalculatedAt(),r.getExitTradeDate(),s.getSymbol(),
                            s.getExpectedExcessReturnPct().doubleValue(),r.getForwardReturnPct().doubleValue()-b,s.getCalibratedUpsideProbabilityPct().doubleValue(),s.getExcessP10Pct().doubleValue(),s.getExcessP90Pct().doubleValue(),r.getForwardReturnPct().doubleValue(),b,r.getSector(),"UNKNOWN"));}
                        allEvaluation.computeIfAbsent(model,k->new ArrayList<>()).addAll(evaluation);}
                    perModel.add(Map.of("model",model,"predictions",ps.size(),"missingSymbols",missing,"fullPopulation",missing.isEmpty(),"forecastMetrics",new ExpectedReturnMetricsCalculator().calculate(evaluation,101),"portfolio",new ExpectedReturnPortfolioEvaluator(20).evaluate(evaluation,10)));
                    allPredictions.addAll(ps);
                }
                output.add(Map.of("decision",day,"teamSignal",team,"teamMarket",teamMarket,"teamShareAccession",share.path("accessionNumber").asText(),"fullPopulation",pop,"stageResults",stages,"capturedWrites",writeAudit,"models",perModel));
                System.out.println("MODEL_DATE_DONE "+day+" "+json.writeValueAsString(perModel));
            }
            var combined=new TreeMap<String,Object>();allEvaluation.forEach((model,rows)->combined.put(model,new ExpectedReturnPortfolioEvaluator(20).evaluate(rows,10)));
            if(!originalOutcomeState.equals(json.writeValueAsString(outcomes)))throw new IllegalStateException("Source outcomes mutated across decision runs");
            var report=new LinkedHashMap<String,Object>();report.put("classification","EXISTING_QUANT_FORMULAS_RECOVERED_INPUTS_RESEARCH_NOT_PIT");report.put("databaseWrites",0);report.put("promotionAllowed",false);
            report.put("dates",output);report.put("combinedNonoverlapPortfolio",combined);report.put("predictions",allPredictions);report.put("targetOutcomePerturbation",perturb);report.put("sourceOutcomeIsolationVerified",true);
            var hashes=new TreeMap<String,String>();
            for(String path:List.of("scripts/QuantTeamModelRecovery.java","tmp/quant-team-inputs-20261018/team-facts.json","tmp/quant-team-inputs-20261018/team-submissions.json","tmp/quant-team-inputs-20261018/financial-inputs.json","tmp/quant-team-share-evidence-20261018/share-facts.json","tmp/quant-complete-window-members-20261018.tsv"))
                hashes.put(path,HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(Path.of(path)))));
            report.put("inputHashes",hashes);
            report.put("limitations",List.of("Historical official full roster and input receipt times unproven; no live performance claim.","Existing historical builder retains explicit missing analyst/news/institution policies; they are not newly observed facts.","Training context retains frozen reconstructed outcomes and missing former-constituent history; TEAM prior labels are not added to training.","TEAM cap uses manually curated SEC-text prior-quarter actualA+B shares timesClassAclose, a company-equity proxy; never SEC_XBRL/freefloat/index marketcap.","V9 retains existing weak-provenance shrinkage for manually curated SEC text; no special trust upgrade.","Target scores re-ranked using all101 current reconstructed members; older training ranks unchanged.","Historical formulas accessed reflectively only inside this offline tool; no production APIs changed."));
            Files.writeString(ROOT.resolve("result.json"),json.writerWithDefaultPrettyPrinter().writeValueAsString(report));
        }
    }
    static SecFinancialStandard choose(List<SecFinancialStandard> rows,boolean annual,LocalDate before,LocalDate asof){return rows.stream().filter(f->annual=="FY".equals(f.getFiscalPeriod())&&f.getFiledAt().isBefore(asof)&&(before==null||f.getEndDate().isBefore(before))).findFirst().orElse(null);}
}
