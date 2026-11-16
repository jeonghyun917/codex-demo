import com.kingyurina.demo.CodexDemoApplication;
import com.kingyurina.demo.stock.*;
import com.kingyurina.demo.stock.evaluation.CompleteBenchmarkReturn;
import org.springframework.boot.*;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.*;
import java.math.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/** Training-only research: no target forecast, readiness, heldout outcome or promotion path.
 * CLI: SEALED_PROTOCOL expectedSHA NEW_OUTPUT_DIR [INPUT_JSON]. */
public final class QuantEpsRecoveredTrainingReplayV2 {
    static final ObjectMapper json=QuantStrictDowReplay.json;
    static final String NAMESPACE="DOW30_EPS_RECOVERED_TRAINING_ONLY_RESEARCH_20261101";
    static final LocalDate FROM=LocalDate.parse("2017-01-31"),TO=LocalDate.parse("2020-08-28"),LAST_LABEL=LocalDate.parse("2020-09-29"),CUTOFF=LocalDate.parse("2020-09-30");
    static void require(boolean condition,String message){QuantStrictDowReplay.require(condition,message);}
    static void validateBoundary(JsonNode input){
        require(NAMESPACE.equals(input.path("namespace").asText()),"Training-only research namespace required");
        require(input.path("testDecisions").isArray()&&input.path("testDecisions").isEmpty(),"testDecisions must explicitly be empty");
        require(CUTOFF.toString().equals(input.path("fixedCutoff").asText()),"Fixed training cutoff required");
        for(String forbidden:List.of("targetLabels","testLabels","heldoutLabels","evaluation","predictions","baselinePredictions","targetForecasts","oofPredictions","trainingRows","result","dates"))require(!input.has(forbidden),"Forbidden outcome/result payload "+forbidden);
        for(var e:input.path("pricesBySymbol").properties())for(var n:e.getValue())require(!LocalDate.parse(n.path("day").asText()).isAfter(LAST_LABEL),"Future target price forbidden");
        for(var n:input.path("benchmark"))require(!LocalDate.parse(n.path("day").asText()).isAfter(LAST_LABEL),"Future target benchmark forbidden");
    }
    static void requireSourceHashes(JsonNode hashes){
        require(hashes.isObject()&&!hashes.isEmpty(),"Source hashes required");
        for(var e:hashes.properties())require(!e.getKey().isBlank()&&e.getValue().asText().matches("[a-fA-F0-9]{64}"),"Source checksum missing "+e.getKey());
    }
    static void validateSchedule(List<LocalDate> calendar,List<LocalDate> training){
        require(calendar.equals(calendar.stream().distinct().sorted().toList()),"Unique ascending benchmark calendar required");
        require(!calendar.isEmpty()&&calendar.getLast().equals(LAST_LABEL),"Training source must end at last mature label");
        var expected=calendar.stream().filter(d->!d.isBefore(FROM)&&!d.isAfter(TO)).toList();
        require(training.size()==902&&training.equals(expected)&&training.getFirst().equals(FROM)&&training.getLast().equals(TO),"Exactly902 fixed training decisions required");
        for(var day:training)require(QuantLongHistoryVerifiedReplay.holding(calendar,day).getLast().isBefore(CUTOFF),"Training label not strictly mature at cutoff");
        require(QuantLongHistoryVerifiedReplay.holding(calendar,TO).getLast().equals(LAST_LABEL),"Fixed final training label exit required");
    }
    static List<StockBacktestResult> matureBefore(List<StockBacktestResult> rows,LocalDate day){return rows.stream().filter(r->QuantLongHistoryVerifiedReplay.mature(r,day)).toList();}
    static List<StockExpectedReturnSnapshot> predictTrainingDay(LocalDate day,List<StockBacktestResult> current,List<StockBacktestResult> allTraining,List<StockExpectedReturnSnapshot> archived,List<StockBenchmarkReturn> benchmark,List<StockMacroFeatureSnapshot> macro,List<StockMarketSnapshot> markets)throws Exception{
        require(!day.isBefore(FROM)&&!day.isAfter(TO),"Only training decision dates permitted");
        var masked=new ArrayList<StockBacktestResult>();
        for(var r:current){var copy=json.convertValue(r,StockBacktestResult.class);copy.setForwardReturnPct(BigDecimal.ZERO);copy.setBenchmarkReturnPct(BigDecimal.ZERO);copy.setEntryClose(BigDecimal.valueOf(100));copy.setExitClose(BigDecimal.valueOf(100));masked.add(copy);}
        return QuantLongHistoryVerifiedReplay.models(NAMESPACE,day,masked,matureBefore(allTraining,day),archived,benchmark,macro,markets,current.size(),true);
    }
    static void verifyHashes(Map<String,String> hashes)throws Exception{QuantLongHistoryVerifiedReplay.verifyHashes(hashes);}
    static Map<String,String> runtimeHashes()throws Exception{
        var out=new TreeMap<String,String>();
        for(Class<?> type:List.of(QuantEpsRecoveredTrainingReplayV2.class,QuantLongHistoryVerifiedReplay.class,QuantStrictDowReplay.class,HistoricalBacktestSeedService.class,Class.forName("com.kingyurina.demo.stock.FinancialInputPolicy"),SecFinancialStandardService.class,StockBacktestService.class,StockPortfolioBacktestService.class,StockExpectedReturnV8Service.class,StockExpectedReturnV9Service.class)){
            var resource="/"+type.getName().replace('.','/')+".class";
            try(var stream=type.getResourceAsStream(resource)){require(stream!=null,"Missing actual runtime bytecode "+resource);out.put(type.getName(),HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes())));}
            if(type.getName().startsWith("com.kingyurina"))require(out.get(type.getName()).equals(QuantStrictDowReplay.sha(Path.of("target/classes"+resource))),"Loaded class differs from target/classes "+resource);
        }return out;
    }
    static Map<String,Object> databaseState(JdbcTemplate db){var state=new TreeMap<String,Object>(QuantStrictDowReplay.databaseState(db));state.put("stock_sec_fact",db.queryForObject("SELECT COUNT(*) FROM stock_sec_fact",Long.class));return state;}
    static void write(Path path,Object value)throws Exception{try(var out=Files.newOutputStream(path,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)){json.writerWithDefaultPrettyPrinter().writeValue(out,value);}}
    public static void main(String[] args)throws Exception{
        require(args.length==3||args.length==4,"Usage: SEALED_PROTOCOL expectedSHA NEW_OUTPUT_DIR [INPUT_JSON]");
        var protocolPath=Path.of(args[0]);var out=Path.of(args[2]);require(!Files.exists(out),"Refuse output overwrite");require(QuantStrictDowReplay.sha(protocolPath).equals(args[1]),"Protocol checksum mismatch");
        var protocol=QuantStrictDowReplay.read(protocolPath);var inputPath=Path.of(args.length==4?args[3]:protocol.path("inputsPath").asText());
        var inputHash=protocol.path("inputsSha256").asText();require(!inputHash.isBlank()&&inputHash.equals(QuantStrictDowReplay.sha(inputPath)),"Sealed training inputs required");
        var input=QuantStrictDowReplay.read(inputPath);validateBoundary(input);requireSourceHashes(input.path("inputHashes"));
        var hashes=new TreeMap<String,String>();hashes.put(protocolPath.toString(),args[1]);hashes.put(inputPath.toString(),inputHash);
        for(var e:input.path("inputHashes").properties())hashes.put(e.getKey(),e.getValue().asText());
        for(String path:List.of("scripts/QuantEpsRecoveredTrainingReplayV2.java","scripts/QuantLongHistoryVerifiedReplay.java","scripts/QuantStrictDowReplay.java","src/main/java/com/kingyurina/demo/stock/FinancialInputPolicy.java","src/main/java/com/kingyurina/demo/stock/HistoricalBacktestSeedService.java","src/main/java/com/kingyurina/demo/stock/StockBacktestService.java","src/main/java/com/kingyurina/demo/stock/StockPortfolioBacktestService.java","src/main/java/com/kingyurina/demo/stock/StockExpectedReturnV8Service.java","src/main/java/com/kingyurina/demo/stock/StockExpectedReturnV9Service.java"))hashes.put(path,QuantStrictDowReplay.sha(Path.of(path)));
        for(var e:input.path("sharesBySymbol").properties())for(var n:e.getValue()){String path=n.path("sourcePath").asText(),sha=n.path("sourceSha256").asText();require(!path.isBlank()&&sha.matches("[a-fA-F0-9]{64}"),"Complete shares source receipt required");hashes.put(path,sha);}
        QuantLongHistoryVerifiedReplay.validateRuntime(hashes);verifyHashes(hashes);var runtimeBefore=runtimeHashes();
        require("COHERENT_PROVIDER_SPLIT_ADJUSTED_CLOSE_WITH_ASOF_NOMINAL_FEATURES".equals(input.path("pricePolicy").asText()),"Unchanged coherent provider price policy required");
        require("2024-06-26".equals(input.path("providerSnapshotDate").asText()),"Frozen provider snapshot required");
        require("ACTUAL_IPO_AND_VERIFIED_ISSUER_ELIGIBILITY".equals(input.path("trainingPopulationPolicy").asText()),"Frozen population policy required");
        var population=new ArrayList<String>();for(var n:input.path("population"))population.add(n.asText());QuantStrictDowReplay.validatePopulation(population);Collections.sort(population);
        var issuerRules=input.path("issuerRulesBySymbol");require("1001039".equals(QuantLongHistoryVerifiedReplay.normalizedCik(issuerRules.path("DIS").path("legacyCik").asText()))&&"2019-03-20".equals(issuerRules.path("DIS").path("validUntilExclusive").asText()),"Unchanged DIS issuer cutover required");
        var intervals=issuerRules.path("DIS").path("eligibleIntervals");require(intervals.size()==2&&"2017-01-31".equals(intervals.get(0).path("fromInclusive").asText())&&"2019-03-20".equals(intervals.get(0).path("toExclusive").asText())&&"2019-05-09".equals(intervals.get(1).path("fromInclusive").asText())&&"2020-12-02".equals(intervals.get(1).path("toExclusive").asText()),"Unchanged DIS eligibility intervals required");
        var events=input.path("providerEventsBySymbol");
        for(var e:events.properties()){require(population.contains(e.getKey()),"Unknown provider event symbol");var seen=new HashSet<LocalDate>();for(var n:e.getValue()){var date=LocalDate.parse(n.path("day").asText());require(!date.isAfter(LocalDate.parse("2024-06-26"))&&seen.add(date)&&Set.of("SPLIT","SEPARATION_RESEARCH").contains(n.path("kind").asText()),"Invalid provider event");QuantLongHistoryVerifiedReplay.eventRatio(n);}}
        var benchmark=new ArrayList<StockBenchmarkReturn>();
        for(var n:input.path("benchmark")){var b=new StockBenchmarkReturn();b.setIndexCode(NAMESPACE);b.setTradeDate(LocalDate.parse(n.path("day").asText()));b.setBenchmarkLevel(new BigDecimal(n.path("level").asText()));b.setSource(n.path("source").asText());require(b.getBenchmarkLevel().signum()>0&&!b.getSource().isBlank(),"Invalid benchmark");benchmark.add(b);}benchmark.sort(Comparator.comparing(StockBenchmarkReturn::getTradeDate));
        StockBenchmarkReturn previous=null;for(var b:benchmark){if(previous!=null)b.setReturnPct(b.getBenchmarkLevel().divide(previous.getBenchmarkLevel(),14,RoundingMode.HALF_UP).subtract(BigDecimal.ONE).multiply(BigDecimal.valueOf(100)));previous=b;}
        var calendar=benchmark.stream().map(StockBenchmarkReturn::getTradeDate).toList();var decisions=QuantLongHistoryVerifiedReplay.nodeDates(input.path("trainingDecisions"));validateSchedule(calendar,decisions);
        var prices=new TreeMap<String,NavigableMap<LocalDate,StockCandleDaily>>();
        for(String symbol:population){var series=new TreeMap<LocalDate,StockCandleDaily>();for(var n:input.path("pricesBySymbol").path(symbol)){var c=new StockCandleDaily();c.setSymbol(symbol);c.setTradeDate(LocalDate.parse(n.path("day").asText()));c.setClosePrice(new BigDecimal(n.path("close").asText()));c.setSource(n.path("source").asText());require(c.getClosePrice().signum()>0&&series.put(c.getTradeDate(),c)==null,"Duplicate/nonpositive price");}require(!series.isEmpty(),"Missing actual source prices "+symbol);prices.put(symbol,series);}
        var financial=new TreeMap<String,List<SecFinancialStandard>>();for(var e:input.path("financialsBySymbol").properties())financial.put(e.getKey(),QuantStrictDowReplay.list(e.getValue(),SecFinancialStandard.class));
        require(financial.keySet().containsAll(population),"Complete financial source map required (empty list allowed)");
        for(var e:input.path("sectorsBySymbol").properties())QuantStrictDowReplay.sectorsBySymbol.put(e.getKey(),e.getValue().asText());
        QuantStrictDowReplay.validPeriod=Class.forName("com.kingyurina.demo.stock.FinancialInputPolicy").getDeclaredMethod("validPeriod",SecFinancialStandard.class,boolean.class);QuantStrictDowReplay.validPeriod.setAccessible(true);
        var macro=new ArrayList<StockMacroFeatureSnapshot>();for(var n:input.path("macro"))macro.add(json.treeToValue(n,StockMacroFeatureSnapshot.class));
        var app=new SpringApplication(CodexDemoApplication.class);app.setWebApplicationType(WebApplicationType.NONE);
        try(var ctx=app.run("--spring.profiles.active=mariadb","--spring.sql.init.mode=never","--spring.datasource.hikari.read-only=true","--spring.datasource.hikari.connection-init-sql=SET SESSION TRANSACTION READ ONLY","--app.signal.refresh.enabled=false","--app.signal.refresh.run-on-startup=false","--spring.task.scheduling.enabled=false")){
            var db=ctx.getBean(JdbcTemplate.class);require(db.queryForObject("SELECT @@tx_read_only",Integer.class)==1,"Read-only database required");var before=databaseState(db);
            var financialReads=new TreeMap<String,Object>();var marketReads=new TreeMap<String,Object>();var units=new TreeMap<String,Object>();
            var mapper=QuantLongHistoryVerifiedReplay.asOfOverlay(ctx.getBean(HistoricalBacktestSeedMapper.class),prices,financial,events,issuerRules,financialReads,units);
            var builder=ctx.getBean(HistoricalBacktestSeedService.class);var build=HistoricalBacktestSeedService.class.getDeclaredMethod("buildSnapshot",HistoricalBacktestSeedMapper.class,String.class,LocalDate.class);build.setAccessible(true);
            Files.createDirectories(out);var training=new ArrayList<StockBacktestResult>();var allSignals=new ArrayList<StockSignalSnapshot>();var rowsByDay=new TreeMap<LocalDate,List<StockBacktestResult>>();var marketsByDay=new TreeMap<LocalDate,List<StockMarketSnapshot>>();
            int done=0;for(var day:decisions){var members=QuantLongHistoryVerifiedReplay.eligiblePopulation(population,prices,day,issuerRules);var h=QuantLongHistoryVerifiedReplay.holding(calendar,day);var signals=new ArrayList<StockSignalSnapshot>();var markets=new ArrayList<StockMarketSnapshot>();
                for(String symbol:members){var signal=(StockSignalSnapshot)build.invoke(builder,mapper,symbol,day);require(signal!=null,"Missing rebuilt signal "+symbol+day);signals.add(signal);
                    var event=events.path(symbol);require(prices.get(symbol).containsKey(day),"Missing current price");var close=prices.get(symbol).get(day).getClosePrice().multiply(QuantLongHistoryVerifiedReplay.priceMultiplier(event,day));
                    var market=QuantLongHistoryVerifiedReplay.marketVerified(db,symbol,day,close,QuantLongHistoryVerifiedReplay.issuerShares(symbol,input.path("sharesBySymbol").path(symbol),day,issuerRules),marketReads);market.setIndexCode(NAMESPACE);
                    @SuppressWarnings("unchecked") var chosen=(Map<String,Object>)marketReads.get(symbol+"/"+day);
                    if(symbol.equals("DIS")){var evidence=json.valueToTree(chosen.get("evidence"));require(QuantLongHistoryVerifiedReplay.issuerAllowed(symbol,QuantLongHistoryVerifiedReplay.evidenceCik(evidence),day,issuerRules),"Unadmitted DIS share issuer");if(!day.isBefore(LocalDate.parse("2019-03-20")))require(!LocalDate.parse(String.valueOf(chosen.get("measurementDate"))).isBefore(LocalDate.parse("2019-03-20")),"Premerger DIS shares forbidden");}
                    var shares=market.getSharesOutstanding();var adjusted=QuantLongHistoryVerifiedReplay.sharesAsOf(shares,LocalDate.parse(String.valueOf(chosen.get("filedDate"))),LocalDate.parse(String.valueOf(chosen.get("measurementDate"))),event,day);market.setSharesOutstanding(adjusted);market.setMarketCapUsd(adjusted.multiply(close));units.put(symbol+"/"+day+"/shares",Map.of("originalActualInstantShares",shares,"derivedDecisionAsOfShares",adjusted,"measurementDate",chosen.get("measurementDate"),"filedDate",chosen.get("filedDate"),"method","FROZEN_PRIOR_SHARE_PURE_SPLIT_ALIGNMENT"));markets.add(market);
                }
                QuantStrictDowReplay.rank(signals);var rows=new ArrayList<StockBacktestResult>();
                for(var signal:signals){var bars=prices.get(signal.getSymbol()).subMap(h.getFirst(),true,h.getLast(),true).values().stream().toList();require(bars.size()==21&&bars.stream().map(StockCandleDaily::getTradeDate).toList().equals(h),"Incomplete21-close training label");var market=markets.stream().filter(m->m.getSymbol().equals(signal.getSymbol())).findFirst().orElseThrow();var row=QuantStrictDowReplay.outcome(signal,bars.getFirst(),bars.getLast(),market);Double b=CompleteBenchmarkReturn.compound(benchmark,NAMESPACE,h.getFirst(),h.getLast(),20);require(b!=null,"Incomplete training benchmark label");row.setBenchmarkReturnPct(BigDecimal.valueOf(b));require(QuantLongHistoryVerifiedReplay.mature(row,CUTOFF),"Training label exits at/after cutoff");rows.add(row);training.add(row);}
                rowsByDay.put(day,rows);marketsByDay.put(day,markets);allSignals.addAll(signals);if(++done%10==0)System.out.println("EPS_TRAIN_BUILD "+done+"/902");
            }
            require(training.size()==26401&&allSignals.size()==26401,"Fixed26401 admitted training rows required");
            write(out.resolve("financial-input-audit.json"),financialReads);write(out.resolve("market-input-audit.json"),marketReads);write(out.resolve("unit-input-audit.json"),units);
            String trainingHash=QuantStrictDowReplay.objectHash(training);var oof=new ArrayList<StockExpectedReturnSnapshot>();var audit=new ArrayList<Object>();done=0;
            for(var day:decisions){verifyHashes(Map.of(protocolPath.toString(),args[1]));var mature=matureBefore(training,day);var predicted=mature.size()<300?List.<StockExpectedReturnSnapshot>of():predictTrainingDay(day,rowsByDay.get(day),mature,oof,benchmark,macro,marketsByDay.get(day));oof.addAll(predicted);var a=new LinkedHashMap<String,Object>();a.put("decision",day);a.put("matureTrainingRows",mature.size());a.put("latestMatureExit",mature.stream().map(StockBacktestResult::getExitTradeDate).max(LocalDate::compareTo).orElse(null));a.put("rankPopulation",rowsByDay.get(day).size());a.put("predictions",predicted.size());audit.add(a);if(++done%10==0)System.out.println("EPS_TRAIN_OOF "+done+"/902 snapshots="+oof.size());}
            require(trainingHash.equals(QuantStrictDowReplay.objectHash(training)),"Training archive mutated");verifyHashes(hashes);require(runtimeBefore.equals(runtimeHashes()),"Actual runtime class drift");var after=databaseState(db);require(before.equals(after),"Source database drift");
            var v9=oof.stream().filter(p->QuantLongHistoryVerifiedReplay.version(9).equals(p.getModelVersion())).toList();write(out.resolve("forecast-only.json"),Map.of("trainingRows",training,"oofPredictions",v9,"heldoutOutcomesComputed",false));write(out.resolve("chronological-oof.json"),oof);var report=new LinkedHashMap<String,Object>();report.put("classification","EPS_RECOVERED_INTERNAL_TRAINING_RESEARCH_NOT_UNTOUCHED_NOT_PROSPECTIVE");report.put("researchNamespace",NAMESPACE);report.put("protocolSha256",args[1]);report.put("trainingRows",training);report.put("rebuiltSignalCount",allSignals.size());report.put("oofPredictions",oof.stream().filter(p->QuantLongHistoryVerifiedReplay.version(9).equals(p.getModelVersion())).toList());report.put("allOofPredictionsFile","chronological-oof.json");report.put("allOofPredictionsSha256",QuantStrictDowReplay.sha(out.resolve("chronological-oof.json")));report.put("allOofPredictionsCount",oof.size());report.put("oofAudit",audit);report.put("financialReads",financialReads);report.put("marketReads",marketReads);report.put("priceUnitAudit",units);report.put("trainingSha256",trainingHash);report.put("inputHashes",hashes);report.put("runtimeClassHashes",runtimeBefore);report.put("sourceDatabaseBefore",before);report.put("sourceDatabaseAfter",after);report.put("databaseWrites",0);report.put("promotionAllowed",false);report.put("heldoutOutcomesComputed",false);report.put("testDecisions",List.of());report.put("trainingAudit",Map.of("decisions",902,"rows",26401,"firstDecision",FROM,"lastDecision",TO,"lastLabelExit",LAST_LABEL,"cutoffExclusive",CUTOFF,"currentLabelsMaskedBeforeModels",true,"OOFArchiveAdmission","STRICTLY_PRIOR_DECISION_AND_EXIT_BEFORE_CURRENT_DAY"));report.put("limitations",List.of("Internal previously exposed training research, not untouched validation or model promotion.","Fixed post-Aug2020 cohort retains survivorship bias and overlapping20D labels.","Original nonfinancial inputs, actual-share guards, issuer exclusions, provider price/event basis and missing-input fallbacks retained.","Current primary source acquisition is not a historical API snapshot; admission uses original accession and filing-date evidence."));write(out.resolve("result.json"),report);System.out.println("EPS_TRAINING_ONLY_DONE rows="+training.size()+" oof="+oof.size()+" DBwrites=0 noHeldout");
        }
    }
}
