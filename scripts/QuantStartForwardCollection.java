import com.kingyurina.demo.CodexDemoApplication;
import com.kingyurina.demo.stock.*;
import org.springframework.boot.*;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.lang.reflect.Method;

/** Explicit one-shot operator of existing batch services, no price/network collection or model tuning. */
public final class QuantStartForwardCollection {
    public static void main(String[] args)throws Exception {
        var applicationOrigin=CodexDemoApplication.class.getProtectionDomain().getCodeSource().getLocation();
        for(Class<?> type:List.of(StockExpectedReturnV9Service.class,StockExpectedReturnForwardArchive.class,StockPortfolioBacktestService.class)){
            if(!applicationOrigin.equals(type.getProtectionDomain().getCodeSource().getLocation()))
                throw new IllegalStateException("Mixed application classes; remove stale helper directories from classpath: "+type.getName());
        }
        if(args.length==1&&"--verify-classpath".equals(args[0])){
            System.out.println("VERIFIED_APPLICATION_CLASS_ORIGIN "+applicationOrigin);return;
        }
        if(args.length!=2)throw new IllegalArgumentException("Expected completed-session-date NEW_OUTPUT_DIRECTORY");
        LocalDate completed=LocalDate.parse(args[0]);Path root=Path.of(args[1]);
        if(Files.exists(root))throw new IllegalStateException("Refuse existing operational output");
        if(!completed.isBefore(LocalDate.now(ZoneId.of("America/New_York"))))throw new IllegalArgumentException("Only completed prior exchange dates");
        Files.createDirectories(root);Instant started=Instant.now();
        String runId=UUID.randomUUID().toString();
        var app=new SpringApplication(CodexDemoApplication.class);app.setWebApplicationType(WebApplicationType.NONE);
        try(var ctx=app.run("--spring.profiles.active=mariadb","--spring.sql.init.mode=never","--app.signal.refresh.enabled=false","--app.signal.refresh.run-on-startup=false","--app.quant.source-financial.enabled=true","--app.quant.forward.run-id="+runId)){
            var jdbc=ctx.getBean(JdbcTemplate.class);var json=ctx.getBean(ObjectMapper.class);
            var portfolio=ctx.getBean(StockPortfolioBacktestService.class);
            var refresh=ctx.getBean(StockSignalRefreshService.class);
            Path archiveRoot=Path.of(ctx.getEnvironment().getProperty("app.quant.forward-archive.directory","./data/quant-forward")).toAbsolutePath().normalize();
            Set<Path> archivesBefore=archivePaths(archiveRoot);
            List<String> indices=List.of("DOW30","SP500","NASDAQ100");
            var before=new TreeMap<String,Object>();var after=new TreeMap<String,Object>();
            var markets=new TreeMap<String,Object>();var eligibility=new TreeMap<String,Object>();var eligible=new TreeSet<String>();
            for(String index:indices){
                var universe=jdbc.queryForList("SELECT symbol FROM index_constituent WHERE index_code=? AND current_member=TRUE ORDER BY symbol",String.class,index);
                var current=jdbc.queryForList("SELECT i.symbol FROM index_constituent i JOIN stock_candle_daily c ON c.symbol=i.symbol AND c.trade_date=? WHERE i.index_code=? AND i.current_member=TRUE ORDER BY i.symbol",String.class,completed,index);
                var excluded=new TreeSet<>(universe);excluded.removeAll(current);eligible.addAll(current);
                eligibility.put(index,Map.of("listedMembers",universe.size(),"listedNames",universe,
                    "eligibleCurrentClose",current.size(),"eligibleNames",current,"excludedKnownBeforeForecast",excluded,"reason","NO_EXPECTED_COMPLETED_CLOSE"));
                before.put(index,jdbc.queryForMap("SELECT COUNT(*) n,MAX(trade_date) lastDate FROM stock_benchmark_return_series WHERE index_code=?",index));
                markets.put(index,portfolio.refreshLatestMarketSnapshotsAndBenchmarks(index,completed));
                LocalDate last=jdbc.queryForObject("SELECT MAX(trade_date) FROM stock_benchmark_return_series WHERE index_code=?",LocalDate.class,index);
                LocalDate market=jdbc.queryForObject("SELECT MAX(snapshot_date) FROM stock_market_snapshot WHERE index_code=?",LocalDate.class,index);
                if(!completed.equals(last)||!completed.equals(market))throw new IllegalStateException("Fresh market/benchmark not established: "+index);
                after.put(index,jdbc.queryForMap("SELECT COUNT(*) n,MAX(trade_date) lastDate FROM stock_benchmark_return_series WHERE index_code=?",index));
            }
            // Freeze the known-at-generation eligible universe before creating any forecasts.
            Files.writeString(root.resolve("eligibility-before-forecast.json"),json.writerWithDefaultPrettyPrinter().writeValueAsString(Map.of("runId",runId,"observedAtUtc",Instant.now().toString(),"completedSession",completed.toString(),"indices",eligibility)),StandardOpenOption.CREATE_NEW);
            // Use the product's exact ranking/snapshot functions, but do not recompute historical
            // outcomes, risk history or research views just to start a forward observation.
            var signals=new ArrayList<StockSignalLatest>();var failedSymbols=new ArrayList<String>();
            for(String symbol:eligible){try{signals.add(ctx.getBean(StockSignalService.class).buildLatest(symbol));}
                catch(RuntimeException ex){failedSymbols.add(symbol);System.err.println("SIGNAL_FAILED "+symbol+" "+ex.getClass().getSimpleName());}}
            Method rank=StockSignalRefreshService.class.getDeclaredMethod("applyQuantRankScores",List.class);rank.setAccessible(true);rank.invoke(refresh,signals);
            Method save=StockSignalRefreshService.class.getDeclaredMethod("saveSnapshot",StockSignalLatest.class);save.setAccessible(true);
            var cache=ctx.getBean(StockCacheService.class);
            for(var signal:signals){cache.saveLatestSignal(signal);save.invoke(refresh,signal);}
            Path inputRoot=Path.of(ctx.getEnvironment().getProperty("app.quant.forward-inputs.directory","./data/quant-forward-inputs"))
                .toAbsolutePath().normalize().resolve(runId);
            var signalInputs=new ArrayList<Map<String,String>>();
            for(var signal:signals){
                Path path=inputRoot.resolve(signal.getSymbol()+".json");
                signalInputs.add(Map.of("symbol",signal.getSymbol(),"path",path.toString(),"sha256",sha(path)));
            }
            var inputBody=new LinkedHashMap<String,Object>();
            inputBody.put("format","QUANT_PRE_MODEL_INPUT_EVIDENCE_V1");inputBody.put("runId",runId);
            inputBody.put("observedAtUtc",Instant.now().toString());inputBody.put("signalsRanked",signals);
            inputBody.put("signalInputFiles",signalInputs);inputBody.put("completeHistoricalPIT",false);
            inputBody.put("fullUpstreamHistoricalTrainingCaptured",false);
            inputBody.put("configuredSourceFinancialPolicyVersion",StockSourceFinancialService.VERSION);
            var codeHashes=new TreeMap<String,String>();
            for(Class<?> type:List.of(QuantStartForwardCollection.class,StockSignalService.class,StockForwardInputEvidence.class,
                    StockPortfolioBacktestService.class,StockExpectedReturnV8Service.class,StockExpectedReturnV9Service.class,
                    StockExpectedReturnForwardArchive.class,SecFinancialStandardService.class,StockSourceFinancialService.class)){
                try(var bytes=type.getResourceAsStream("/"+type.getName().replace('.','/')+".class")){
                    if(bytes==null)throw new IllegalStateException("Running class bytes missing: "+type.getName());
                    codeHashes.put(type.getName(),java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes.readAllBytes())));
                }
            }
            inputBody.put("runningClassSha256",codeHashes);
            inputBody.put("limitations",List.of("Actual selected live inputs and ranked factors; not reconstructed historical receipt times.",
                "V2-V8 fitted outputs are retained below, but their complete historical training corpus is not copied."));
            Path inputPath=inputRoot.resolve("signals-ranked-before-models.json");
            Files.writeString(inputPath,json.writerWithDefaultPrettyPrinter().writeValueAsString(inputBody),StandardOpenOption.CREATE_NEW);
            var modelInputs=new TreeMap<String,Object>();
            var stages=new TreeMap<String,Object>();var failedStages=new ArrayList<String>();
            for(String index:indices){try{
                portfolio.refreshLatestRiskSnapshots(index,100000);
                portfolio.refreshLatestCovarianceSnapshot(index,120,126);
                portfolio.refreshLatestExpectedReturnSnapshots(index,100000,300);
                portfolio.refreshLatestExpectedReturnV3Snapshots(index,100000,300);
                portfolio.refreshLatestExpectedReturnV4Snapshots(index,100000,300);
                portfolio.refreshLatestExpectedReturnV5Snapshots(index,100000,300);
                portfolio.refreshLatestExpectedReturnV6Snapshots(index,100000,300);
                portfolio.refreshLatestExpectedReturnV7Snapshots(index,100000,300);
                ctx.getBean(StockMacroFeatureService.class).refreshLatestMacroFeature(index);
                ctx.getBean(StockExpectedReturnV8Service.class).refreshLatest(index);
                // Preserve fitted upstream rows/cutoffs BEFORE V9 consumes them. No target/outcome refit here.
                var upstream=new LinkedHashMap<String,Object>();upstream.put("runId",runId);upstream.put("indexCode",index);
                upstream.put("observedAtUtc",Instant.now().toString());upstream.put("signalDate",LocalDate.now().toString());
                upstream.put("expectedReturnInputs",jdbc.queryForList("SELECT * FROM stock_expected_return_snapshot WHERE index_code=? AND signal_date=? ORDER BY model_version,horizon_days,symbol",index,LocalDate.now()));
                upstream.put("factorContributions",jdbc.queryForList("SELECT * FROM stock_expected_return_factor_contribution WHERE index_code=? AND signal_date=? ORDER BY model_version,symbol",index,LocalDate.now()));
                Path upstreamPath=inputRoot.resolve(index+"-before-v9.json");
                Files.writeString(upstreamPath,json.writerWithDefaultPrettyPrinter().writeValueAsString(upstream),StandardOpenOption.CREATE_NEW);
                modelInputs.put(index,Map.of("path",upstreamPath.toString(),"sha256",sha(upstreamPath)));
                stages.put(index,ctx.getBean(StockExpectedReturnV9Service.class).refreshLatest(index));
            }catch(RuntimeException ex){failedStages.add(index);System.err.println("LIVE_MODELS_FAILED "+index+" "+ex);}}
            var dashboardStages=new TreeMap<String,Object>();
            for(String index:indices){try{dashboardStages.put(index,ctx.getBean(StockDashboardViewSnapshotService.class).refreshDashboardViewSnapshot(index));}
                catch(RuntimeException ex){dashboardStages.put(index,ex.getClass().getSimpleName());}}
            var live=new TreeMap<String,Object>();
            LocalDate signalDate=LocalDate.now();
            for(String index:indices)live.put(index,jdbc.queryForMap("SELECT COUNT(*) savedRows,SUM(expected_excess_return_pct IS NOT NULL) finiteExcessRows,MIN(calculated_at) firstCalculated,MAX(calculated_at) lastCalculated FROM stock_expected_return_snapshot WHERE index_code=? AND signal_date=? AND horizon_days=20 AND model_version=? AND source LIKE 'LIVE_V9_%'",index,signalDate,StockExpectedReturnV9Service.MODEL_VERSION));
            var archives=new TreeMap<String,Object>();
            for(Path path:archivePaths(archiveRoot))if(!archivesBefore.contains(path)){
                var body=json.readTree(Files.readAllBytes(path));
                if(!runId.equals(body.path("runId").asText()))continue;
                String index=body.path("indexCode").asText();
                if(archives.put(index,Map.of("path",path.toString(),"sha256",sha(path)))!=null)
                    failedStages.add("DUPLICATE_ARCHIVE:"+index);
            }
            if(!archives.keySet().equals(new HashSet<>(indices)))failedStages.add("MISSING_INDEX_ARCHIVE");
            var report=new LinkedHashMap<String,Object>();report.put("classification","ACTUAL_NEW_FORWARD_BATCH_OUTPUTS_NOT_VALIDATED_ACCURACY");
            report.put("runId",runId);report.put("capturedArchives",archives);
            report.put("admissionVersion",2);report.put("inputEvidence",Map.of("path",inputPath.toString(),"sha256",sha(inputPath)));
            report.put("preV9Inputs",modelInputs);
            report.put("eligibilitySha256",sha(root.resolve("eligibility-before-forecast.json")));
            report.put("startedAtUtc",started.toString());report.put("finishedAtUtc",Instant.now().toString());report.put("expectedCompletedSession",completed.toString());
            report.put("eligibility",eligibility);report.put("benchmarkBefore",before);report.put("benchmarkAfter",after);report.put("marketRefresh",markets);
            report.put("signalsSaved",signals.size());report.put("failedSymbols",failedSymbols);report.put("modelStages",stages);report.put("failedModelStages",failedStages);report.put("dashboardStages",dashboardStages);
            report.put("live20DCounts",live);report.put("modelWeightsChanged",false);report.put("promotionAllowed",false);
            Files.writeString(root.resolve("result.json"),json.writerWithDefaultPrettyPrinter().writeValueAsString(report),StandardOpenOption.CREATE_NEW);
            System.out.println("ACTUAL_FORWARD_BATCH_COMPLETED "+json.writeValueAsString(live)+" failedStages="+failedStages);
            if(!failedSymbols.isEmpty()||!failedStages.isEmpty())throw new IllegalStateException("Some core live batch stages failed; inspect recorded outputs, do not pretend full success");
        }
    }

    static Set<Path> archivePaths(Path root)throws Exception{
        if(!Files.exists(root))return Set.of();
        try(var paths=Files.walk(root)){return paths.filter(Files::isRegularFile).filter(p->p.toString().endsWith(".json"))
            .map(p->p.toAbsolutePath().normalize()).collect(java.util.stream.Collectors.toSet());}
    }
    static String sha(Path path)throws Exception{
        return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }
}
