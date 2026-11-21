import com.kingyurina.demo.CodexDemoApplication;
import com.kingyurina.demo.stock.*;
import com.kingyurina.demo.stock.evaluation.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

/** Explicit offline macro ablation. Nothing writes operating or frozen source namespaces. */
public final class QuantMacroAsOfValidation {
    private static final Path ROOT = Path.of("tmp/free-quant-followup-20261018");
    private static final String MANIFEST = "f40f6eb8f1682bd8ef71f9b0b1997f88d2f3e32801c7d3c903560742e72e591b";
    private static final String OBSERVATIONS = "02f7775aed5e8e19403175dc90ec889aad73856a473502f81410d9977f2a6700";
    private static final String DATES = "7c3c32840e130912b910f450ee63745e65d4fb45f5348c4feacee9717bc821bc";
    private static final LocalDate AS_OF = LocalDate.of(2026, 10, 18);
    private static final List<String> TABLES = List.of("stock_index_membership_snapshot", "stock_benchmark_return_series",
            "stock_market_snapshot", "stock_expected_return_snapshot", "stock_expected_return_factor_contribution");
    private record Inputs(SortedMap<LocalDate, List<StockMacroAsOfResearchFeatures.Observation>> byDecision) {}

    public static void main(String[] args) throws Exception {
        Inputs inputs = inputs();
        if (args.length == 1 && "--validate-inputs-only".equals(args[0])) {
            System.out.println("INPUTS_VERIFIED decisions=" + inputs.byDecision().size() + " promotionAllowed=false");
            return;
        }
        if (args.length > 1 || (args.length == 1 && !"--validate-only".equals(args[0])))
            throw new IllegalArgumentException("usage: [--validate-inputs-only|--validate-only]");
        boolean readOnly = args.length == 1;
        var app = new SpringApplication(CodexDemoApplication.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        try (var context = app.run("--spring.profiles.active=mariadb", "--spring.sql.init.mode=never",
                "--app.signal.refresh.enabled=false", "--app.signal.refresh.run-on-startup=false")) {
            var jdbc = context.getBean(JdbcTemplate.class);
            var mapper = context.getBean(StockBacktestMapper.class);
            var json = context.getBean(ObjectMapper.class);
            var tx = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
            var output = new ArrayList<Object>();
            for (String universe : List.of("SP500", "NASDAQ100")) {
                String source = universe + "_FREE_20261018", target = universe + "_MACRO_FREE_20261018";
                var declaration = new LinkedHashMap<String, Object>();
                declaration.put("version", "ASOF_MACRO_ABLATION_V1"); declaration.put("sourceNamespace", source);
                declaration.put("namespace", target); declaration.put("macroManifestSha256", MANIFEST);
                declaration.put("macroObservationSha256", OBSERVATIONS); declaration.put("dateMapSha256", DATES);
                declaration.put("baselineManifestSha256", jdbc.queryForObject(
                        "SELECT manifest_sha256 FROM stock_research_dataset WHERE index_code=?", String.class, source));
                declaration.put("decisionDates", inputs.byDecision().keySet().stream().map(Object::toString).toList());
                declaration.put("featureCodeSha256", codeHash(StockMacroAsOfResearchFeatures.class));
                declaration.put("scoreCodeSha256", codeHash(StockMacroFeatureService.class));
                declaration.put("v8CodeSha256", codeHash(StockExpectedReturnV8Service.class));
                declaration.put("v9CodeSha256", codeHash(StockExpectedReturnV9Service.class));
                declaration.put("classification", "RECONSTRUCTED_PREVIOUS_DAY_ASOF_RESEARCH_NOT_PIT");
                declaration.put("promotionAllowed", false);
                String manifest = json.writeValueAsString(declaration), hash = sha(manifest.getBytes(StandardCharsets.UTF_8));
                String dateSql = datesSql(inputs);
                var before = invariant(jdbc, source);
                var existing = jdbc.queryForList("SELECT manifest_sha256 FROM stock_research_dataset WHERE index_code=?", String.class, target);
                if (!existing.isEmpty()) {
                    if (!existing.getFirst().equals(hash)) throw new IllegalStateException("Existing namespace input/code differs; refuse overwrite");
                    System.out.println("REUSE_COMMITTED_NAMESPACE " + target);
                } else {
                    if (readOnly) throw new IllegalStateException("No completed research namespace: " + target);
                    // Copy, features and both affected stages commit together or roll back together.
                    tx.execute(status -> {
                        for (String table : TABLES) if (count(jdbc, table, target) != 0)
                            throw new IllegalStateException("Unregistered namespace is not empty: " + target);
                        if (count(jdbc, "stock_macro_feature_snapshot", target) != 0)
                            throw new IllegalStateException("Unregistered features exist");
                        jdbc.update("INSERT INTO stock_research_dataset(index_code,manifest_sha256,manifest_json) VALUES(?,?,?)", target, hash, manifest);
                        copy(jdbc, "stock_index_membership_snapshot", source, target, "snapshot_date IN (" + dateSql + ")");
                        copy(jdbc, "stock_benchmark_return_series", source, target, "1=1");
                        // Preserve the full original context lookup, including prior decision dates, without retiming any row.
                        copy(jdbc, "stock_market_snapshot", source, target, "1=1");
                        String v7 = "model_version='EXPECTED_RETURN_V7_CORRECTED_20261017' AND signal_date IN (" + dateSql + ")";
                        copy(jdbc, "stock_expected_return_snapshot", source, target, v7);
                        copy(jdbc, "stock_expected_return_factor_contribution", source, target, v7);
                        inputs.byDecision().forEach((day, rows) -> mapper.upsertMacroFeatureSnapshot(
                                StockMacroAsOfResearchFeatures.build(target, day, rows)));
                        System.out.println("ASOF_STAGE_START " + target + " V8");
                        System.out.println("ASOF_STAGE_DONE V8 " + context.getBean(StockExpectedReturnV8Service.class).refreshHistorical(target, 200000, 1000));
                        System.out.println("ASOF_STAGE_START " + target + " V9");
                        System.out.println("ASOF_STAGE_DONE V9 " + context.getBean(StockExpectedReturnV9Service.class).refreshHistorical(target, 200000, 1000));
                        assertKeyEquality(jdbc, source, target, dateSql);
                        return null;
                    });
                    System.out.println("ASOF_NAMESPACE_COMMITTED " + target);
                }
                assertKeyEquality(jdbc, source, target, dateSql);
                if (!before.equals(invariant(jdbc, source))) throw new IllegalStateException("Frozen baseline changed");
                var observations = mapper.findMacroFeatureSnapshots(target, LocalDate.of(2023,1,1), AS_OF);
                if (observations.size() != inputs.byDecision().size()
                        || observations.stream().anyMatch(f -> !"ALFRED_ASOF_RESEARCH_PREVDAY".equals(f.getSource())))
                    throw new IllegalStateException("Missing or incorrectly classified as-of feature rows");
                var benchmark = mapper.findBenchmarkReturns(source, LocalDate.of(2022,10,1), AS_OF);
                var outcomes = mapper.findResults(target, 200000);
                var members = mapper.findEvaluationMembershipSnapshots(target, LocalDate.of(2023,1,1), AS_OF);
                var candidate = mapper.findExpectedReturnSnapshots(target, LocalDate.of(2023,1,1), AS_OF);
                var baseline = mapper.findExpectedReturnSnapshots(source, LocalDate.of(2023,1,1), AS_OF).stream()
                        .filter(p -> inputs.byDecision().containsKey(p.getSignalDate())).toList();
                var sourceMembers = mapper.findEvaluationMembershipSnapshots(source, LocalDate.of(2023,1,1), AS_OF).stream()
                        .filter(m -> inputs.byDecision().containsKey(m.getSnapshotDate())).toList();
                var candidateBench = mapper.findBenchmarkReturns(target, LocalDate.of(2022,10,1), AS_OF);
                var comparisons = new ArrayList<Object>();
                for (int version : List.of(7,8,9)) {
                    String model = "EXPECTED_RETURN_V" + version + "_CORRECTED_20261017";
                    var diagnostic = new ReconstructedReturnDiagnostics();
                    var old = diagnostic.evaluate(source, model, baseline, outcomes, benchmark, sourceMembers, AS_OF);
                    var updated = diagnostic.evaluate(target, model, candidate, outcomes, candidateBench, members, AS_OF);
                    if (old.targetCount() != updated.targetCount() || old.usableRows() != updated.usableRows())
                        throw new IllegalStateException("Comparison populations differ");
                    var row = new LinkedHashMap<String,Object>();
                    row.put("model", model); row.put("baseline", old); row.put("asofMacro", updated);
                    row.put("baselinePortfolio", portfolio(source, model, baseline, outcomes, benchmark, sourceMembers));
                    row.put("asofPortfolio", portfolio(target, model, candidate, outcomes, candidateBench, members));
                    comparisons.add(row);
                    System.out.printf("ASOF_COMPARISON %s V%d usable=%d targets=%d IC=%s -> %s Brier=%s -> %s%n",
                            universe,version,updated.usableRows(),updated.targetCount(),old.metrics().rankIc(),updated.metrics().rankIc(),
                            old.metrics().brierScore(),updated.metrics().brierScore());
                }
                output.add(Map.of("universe",universe,"sourceNamespace",source,"researchNamespace",target,
                        "registrySha256",hash,"macroFeatureRows",observations.size(),"frozenInvariant",before,"comparisons",comparisons));
            }
            var result = new LinkedHashMap<String,Object>();
            result.put("asOf",AS_OF); result.put("classification","SAME_POPULATION_RESEARCH_ABLATION_NOT_LIVE_PERFORMANCE");
            result.put("promotionAllowed",false); result.put("results",output);
            result.put("strictProductionEvaluation",mapper.findLatestExpectedReturnEvaluationRun("SP500",ExpectedReturnPredictionContract.sp500Excess20dCorrected().version()));
            result.put("strictEvaluationReused",true);
            Files.writeString(Path.of("tmp/quant-macro-asof-validation-20261018.json"),json.writerWithDefaultPrettyPrinter().writeValueAsString(result));
        }
    }

    private static Inputs inputs() throws Exception {
        verify(ROOT.resolve("manifest.json"),MANIFEST);
        Path csv = ROOT.resolve("derived/macro_asof_snapshots_RESEARCH_ONLY.csv"), mapping=ROOT.resolve("derived/decision_asof_map.csv");
        verify(csv,OBSERVATIONS); verify(mapping,DATES);
        var rows = new HashMap<LocalDate,List<StockMacroAsOfResearchFeatures.Observation>>();
        var lines=Files.readAllLines(csv);
        if(!lines.getFirst().equals("series_code,observation_date,as_of_date,value,source_id"))throw new IllegalArgumentException("Unexpected observation header");
        for(String line:lines.subList(1,lines.size())){
            String[] p=line.split(",",-1);if(p.length!=5)throw new IllegalArgumentException("Invalid observation CSV");
            var row=new StockMacroAsOfResearchFeatures.Observation(p[0],LocalDate.parse(p[1]),LocalDate.parse(p[2]),new BigDecimal(p[3]),p[4]);
            rows.computeIfAbsent(row.asOfDate(),k->new ArrayList<>()).add(row);
        }
        var decisions=new TreeMap<LocalDate,List<StockMacroAsOfResearchFeatures.Observation>>();
        var dates=Files.readAllLines(mapping);
        if(!dates.getFirst().equals("decision_date,as_of_date,policy"))throw new IllegalArgumentException("Unexpected mapping header");
        for(String line:dates.subList(1,dates.size())){
            String[] p=line.split(",",-1);if(p.length!=3 || !p[2].equals("PREVIOUS_CALENDAR_DAY_NO_INTRADAY_ASSUMPTION"))throw new IllegalArgumentException("Invalid date policy");
            LocalDate day=LocalDate.parse(p[0]),asOf=LocalDate.parse(p[1]);
            if(!asOf.equals(day.minusDays(1)) || day.isAfter(AS_OF))throw new IllegalArgumentException("Wrong decision as-of mapping");
            var observations=rows.get(asOf);if(observations==null || decisions.putIfAbsent(day,observations)!=null)throw new IllegalArgumentException("Missing or duplicate decision");
            StockMacroAsOfResearchFeatures.build("SP500_MACRO_FREE_20261018",day,observations);
        }
        if(decisions.size()!=37)throw new IllegalArgumentException("Unexpected pinned decision population");
        return new Inputs(decisions);
    }
    private static String datesSql(Inputs inputs){return inputs.byDecision().keySet().stream().map(d->"'"+d+"'").collect(Collectors.joining(","));}
    private static int count(JdbcTemplate jdbc,String table,String index){return jdbc.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE index_code=?",Integer.class,index);}
    private static void copy(JdbcTemplate jdbc,String table,String source,String target,String condition){
        if(!TABLES.contains(table))throw new IllegalArgumentException("Unsupported research table");
        var columns=jdbc.queryForList("SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? AND COLUMN_NAME<>'index_code' ORDER BY ORDINAL_POSITION",String.class,table);
        String names=columns.stream().map(c->"`"+c+"`").collect(Collectors.joining(","));
        int copied=jdbc.update("INSERT INTO "+table+"(index_code,"+names+") SELECT ?,"+names+" FROM "+table+" WHERE index_code=? AND "+condition,target,source);
        System.out.println("ASOF_COPY "+target+" "+table+" rows="+copied);
    }
    private static void assertKeyEquality(JdbcTemplate jdbc,String source,String target,String dates){
        for(int v:List.of(7,8,9)){
            String model="EXPECTED_RETURN_V"+v+"_CORRECTED_20261017";
            Integer expected=jdbc.queryForObject("SELECT COUNT(*) FROM stock_expected_return_snapshot WHERE index_code=? AND model_version=? AND signal_date IN ("+dates+")",Integer.class,source,model);
            Integer actual=jdbc.queryForObject("SELECT COUNT(*) FROM stock_expected_return_snapshot WHERE index_code=? AND model_version=?",Integer.class,target,model);
            Integer missing=jdbc.queryForObject("SELECT COUNT(*) FROM stock_expected_return_snapshot b LEFT JOIN stock_expected_return_snapshot c ON c.index_code=? AND c.signal_date=b.signal_date AND c.symbol=b.symbol AND c.horizon_days=b.horizon_days AND c.model_version=b.model_version WHERE b.index_code=? AND b.model_version=? AND b.signal_date IN ("+dates+") AND c.symbol IS NULL",Integer.class,target,source,model);
            if(expected==0 || !expected.equals(actual) || missing!=0)throw new IllegalStateException("Research prediction keys differ V"+v);
        }
    }
    private static List<Map<String,Object>> invariant(JdbcTemplate jdbc,String source){
        return jdbc.queryForList("SELECT model_version,COUNT(*) n,MAX(calculated_at) latest,SUM(expected_excess_return_pct) excess_sum,SUM(calibrated_upside_probability_pct) probability_sum FROM stock_expected_return_snapshot WHERE index_code=? GROUP BY model_version ORDER BY model_version",source);
    }
    private static Object portfolio(String index,String model,List<StockExpectedReturnSnapshot> predictions,List<StockBacktestResult> outcomes,
            List<StockBenchmarkReturn> benchmarks,List<StockIndexMembershipSnapshot> members){
        var rows=new ArrayList<ExpectedReturnEvaluationRow>();var omitted=new TreeMap<String,Integer>();int completeDates=0;
        var dates=members.stream().map(StockIndexMembershipSnapshot::getSnapshotDate).distinct().sorted().toList();
        for(var day:dates){
            var population=members.stream().filter(m->day.equals(m.getSnapshotDate())).toList();
            var report=new ReconstructedReturnDiagnostics().evaluate(index,model,predictions,outcomes,benchmarks,population,AS_OF);
            if(report.targetCount()==0 || report.usableRows()!=report.targetCount()){
                omitted.put(day.toString(),report.targetCount()-report.usableRows());continue;
            }
            completeDates++;
            for(var p:predictions){
                if(!model.equals(p.getModelVersion()) || !Integer.valueOf(20).equals(p.getHorizonDays()) || !day.equals(p.getSignalDate()))continue;
                var r=outcomes.stream().filter(o->day.equals(o.getSignalDate()) && p.getSymbol().equals(o.getSymbol()) && Integer.valueOf(20).equals(o.getHorizonDays())).findFirst().orElseThrow();
                double b=Objects.requireNonNull(CompleteBenchmarkReturn.compound(benchmarks,index,r.getEntryTradeDate(),r.getExitTradeDate(),20));
                rows.add(new ExpectedReturnEvaluationRow("RESEARCH_NOT_PIT",index,model,day,p.getCalculatedAt(),r.getExitTradeDate(),p.getSymbol(),
                        p.getExpectedExcessReturnPct().doubleValue(),r.getForwardReturnPct().doubleValue()-b,p.getCalibratedUpsideProbabilityPct().doubleValue(),
                        p.getExcessP10Pct().doubleValue(),p.getExcessP90Pct().doubleValue(),r.getForwardReturnPct().doubleValue(),b,"UNKNOWN","UNKNOWN"));
            }
        }
        return Map.of("classification","FULL_POPULATION_PERIODS_ONLY_RESEARCH_NOT_PIT","promotionAllowed",false,
                "completeDecisionDates",completeDates,"excludedDatesMissingMembers",omitted,"perSideCostBps",10,
                "metrics",new ExpectedReturnPortfolioEvaluator(20).evaluate(rows,10));
    }
    private static String sha(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    private static void verify(Path path,String expected)throws Exception{if(!sha(Files.readAllBytes(path)).equals(expected))throw new IllegalStateException("Pinned input hash mismatch: "+path);}
    private static String codeHash(Class<?> type)throws Exception{try(var in=type.getResourceAsStream(type.getSimpleName()+".class")){return sha(Objects.requireNonNull(in).readAllBytes());}}
}
