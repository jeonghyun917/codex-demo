import com.kingyurina.demo.CodexDemoApplication;
import com.kingyurina.demo.stock.*;
import com.kingyurina.demo.stock.evaluation.CompleteBenchmarkReturn;
import org.springframework.boot.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import tools.jackson.databind.*;
import java.math.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** File-only research harness. Production formulas and the earlier sealed replay are unchanged.
 * CLI: sealed-protocol.json expectedSHA NEW_OUTPUT_DIR [inputs.json]
 * Forecasts and readiness are written before held-out returns may be constructed. */
public final class QuantLongHistoryVerifiedReplay {
    static final ObjectMapper json=QuantStrictDowReplay.json;
    static final String NAMESPACE="DOW30_LONG_VERIFIED_OOF_RESEARCH_20261031";
    static final LocalDate CUTOFF=LocalDate.parse("2020-09-30");
    static final LocalDate TRAIN_FROM=LocalDate.parse("2017-01-31"),TRAIN_TO=LocalDate.parse("2020-08-28");
    static final List<LocalDate> TEST=List.of(CUTOFF,LocalDate.parse("2020-10-30"));
    static final Map<String,String> primaryIssuers=new HashMap<>();
    static String version(int n){return "EXPECTED_RETURN_V"+n+"_CORRECTED_20261017";}
    static void require(boolean ok,String message){QuantStrictDowReplay.require(ok,message);}
    static boolean mature(StockBacktestResult r,LocalDate d){return QuantStrictDowReplay.mature(r,d);}
    static String rowKey(StockBacktestResult r){return r.getSignalDate()+"/"+r.getSymbol()+"/"+r.getHorizonDays();}
    static String predictionKey(StockExpectedReturnSnapshot p){return QuantStrictDowReplay.key(p);}
    static List<StockBenchmarkReturn> benchmarkBefore(List<StockBenchmarkReturn> rows,LocalDate day){return rows.stream().filter(b->b.getTradeDate().isBefore(day)).toList();}
    static List<LocalDate> nodeDates(JsonNode n){var out=new ArrayList<LocalDate>();for(var v:n)out.add(LocalDate.parse(v.asText()));return out;}
    static List<LocalDate> holding(List<LocalDate> calendar,LocalDate day){var h=calendar.stream().filter(d->d.isAfter(day)).limit(21).toList();require(h.size()==21,"Incomplete next-session plus20 intervals "+day);return h;}
    static void validateSchedule(List<LocalDate> calendar,List<LocalDate> training,List<LocalDate> tests,LocalDate cutoff){
        require(calendar.equals(calendar.stream().distinct().sorted().toList()),"Calendar must be unique ascending");
        require(CUTOFF.equals(cutoff)&&TEST.equals(tests),"Fixed cutoff/test schedule mismatch");
        var expected=calendar.stream().filter(d->!d.isBefore(TRAIN_FROM)&&!d.isAfter(TRAIN_TO)).toList();
        require(!expected.isEmpty()&&expected.equals(training),"All calendar sessions in fixed training range required");
        for(var day:training)require(holding(calendar,day).getLast().isBefore(cutoff),"Training exit must strictly precede fixed cutoff "+day);
        for(var day:tests)require(calendar.contains(day)&&holding(calendar,day).size()==21,"Missing target calendar");
    }
    static StockCandleDaily artificialCandle(String symbol,LocalDate day){var c=new StockCandleDaily();c.setSymbol(symbol);c.setTradeDate(day);c.setClosePrice(BigDecimal.valueOf(100));c.setSource("NONREALIZED_TARGET_PLACEHOLDER");return c;}
    static StockBacktestResult dummy(StockSignalSnapshot signal,StockMarketSnapshot market,List<LocalDate> h)throws Exception{
        var r=QuantStrictDowReplay.outcome(signal,artificialCandle(signal.getSymbol(),h.getFirst()),artificialCandle(signal.getSymbol(),h.getLast()),market);r.setForwardReturnPct(BigDecimal.ZERO);r.setBenchmarkReturnPct(BigDecimal.ZERO);return r;
    }
    static BigDecimal eventRatio(JsonNode event){var n=new BigDecimal(event.path("numerator").asText());var d=new BigDecimal(event.path("denominator").asText());require(n.signum()>0&&d.signum()>0,"Positive event ratio required");return n.divide(d,16,RoundingMode.HALF_UP);}
    static BigDecimal priceMultiplier(JsonNode events,LocalDate asOf){var result=BigDecimal.ONE;for(var event:events)if(LocalDate.parse(event.path("day").asText()).isAfter(asOf))result=result.multiply(eventRatio(event));return result;}
    static SecFinancialStandard financialAsOf(SecFinancialStandard original,JsonNode events,LocalDate asOf){
        if(original==null)return null;var copy=json.convertValue(original,SecFinancialStandard.class);if(copy.getEpsDiluted()==null)return copy;
        String unit=copy.getEpsUnit()==null?"":copy.getEpsUnit().replace(" ","");if(!"USD/shares".equalsIgnoreCase(unit)||!"USD".equalsIgnoreCase(copy.getCurrency())||copy.getFiledAt()==null){copy.setEpsDiluted(null);return copy;}
        for(var event:events){var day=LocalDate.parse(event.path("day").asText());if(!"SPLIT".equals(event.path("kind").asText())||day.isAfter(asOf))continue;
            if(copy.getFiledAt().equals(day)){copy.setEpsDiluted(null);break;}
            if(copy.getFiledAt().isBefore(day))copy.setEpsDiluted(copy.getEpsDiluted().divide(eventRatio(event),16,RoundingMode.HALF_UP));
        }return copy;
    }
    static BigDecimal sharesAsOf(BigDecimal shares,LocalDate filed,LocalDate measurement,JsonNode events,LocalDate asOf){
        require(shares!=null&&shares.signum()>0&&filed!=null&&measurement!=null,"Verified prior instant shares required");var value=shares;
        for(var event:events){var day=LocalDate.parse(event.path("day").asText());if(!"SPLIT".equals(event.path("kind").asText())||day.isAfter(asOf)||!measurement.isBefore(day))continue;
            require(filed.isBefore(day),"Post/same-split filing with pre-split measurement needs explicit share-basis proof");value=value.multiply(eventRatio(event));
        }return value;
    }
    static HistoricalBacktestSeedMapper asOfOverlay(HistoricalBacktestSeedMapper original,Map<String,NavigableMap<LocalDate,StockCandleDaily>> prices,Map<String,List<SecFinancialStandard>> financial,JsonNode eventsBySymbol,JsonNode issuerRules,Map<String,Object> financialReads,Map<String,Object> units){
        var base=QuantStrictDowReplay.overlay(original,prices,financial,financialReads);
        return (HistoricalBacktestSeedMapper)Proxy.newProxyInstance(QuantLongHistoryVerifiedReplay.class.getClassLoader(),new Class[]{HistoricalBacktestSeedMapper.class},(p,m,a)->{
            String symbol=(String)a[0];var events=eventsBySymbol.path(symbol);Object value;
            if(m.getName().contains("AnnualAsOf")||m.getName().contains("QuarterAsOf")){
                boolean previous=m.getName().contains("Previous"),annual=m.getName().contains("Annual");var asOf=(LocalDate)a[previous?2:1];var before=previous?(LocalDate)a[1]:null;var options=new ArrayList<SecFinancialStandard>();
                for(var f:financial.getOrDefault(symbol,List.of()))if(issuerAllowed(symbol,f.getCik(),asOf,issuerRules))options.add(f);
                var nativeRow=(SecFinancialStandard)m.invoke(original,a);if(nativeRow!=null&&issuerAllowed(symbol,nativeRow.getCik(),asOf,issuerRules))options.add(nativeRow);
                value=QuantStrictDowReplay.select(options,annual,before,asOf);financialReads.put(symbol+"/"+asOf+"/"+m.getName(),value==null?Map.of("missing",true):value);
            }else value=m.invoke(base,a);
            if(m.getName().equals("findCandlesOnOrBefore")){var asOf=(LocalDate)a[1];var factor=priceMultiplier(events,asOf);var adjusted=new ArrayList<StockCandleDaily>();for(var source:(List<?>)value){var row=json.convertValue(source,StockCandleDaily.class);row.setClosePrice(row.getClosePrice().multiply(factor));adjusted.add(row);}units.put(symbol+"/"+asOf+"/price",Map.of("uniformPriceMultiplier",factor,"method","ALL_PAST_PROVIDER_CLOSES_MULTIPLIED_BY_IDENTICAL_DECISION_ASOF_FACTOR"));return adjusted;}
            if(value instanceof SecFinancialStandard f){var asOf=(LocalDate)a[m.getName().contains("Previous")?2:1];var adjusted=financialAsOf(f,events,asOf);var audit=new LinkedHashMap<String,Object>();audit.put("originalEps",f.getEpsDiluted());audit.put("derivedEps",adjusted.getEpsDiluted());audit.put("filedAt",f.getFiledAt());audit.put("method","PRIOR_FILING_PURE_SPLITS_ONLY_SAME_DAY_EPS_BLOCKED");units.put(symbol+"/"+asOf+"/"+m.getName(),audit);return adjusted;}return value;
        });
    }

    static JsonNode selectVerifiedShares(String symbol,JsonNode observations,LocalDate day){
        JsonNode chosen=null;
        for(var n:observations){
            if(!LocalDate.parse(n.path("filedDate").asText()).isBefore(day)||!LocalDate.parse(n.path("measurementDate").asText()).isBefore(day))continue;
            require(symbol.equals(n.path("symbol").asText())&&n.path("completeEconomicCoverage").asBoolean()&&n.path("notWeightedAverage").asBoolean(),"Incomplete economic issuer-share coverage "+symbol);
            String basis=symbol.equals("NKE")?"CLASS_A_PLUS_CLASS_B_SAME_ECONOMIC_RIGHTS":"ISSUER_REPORTED_AS_CONVERTED_CLASS_A_TOTAL";
            require(basis.equals(n.path("coverageBasis").asText())&&n.path("reportedPrecisionShares").asLong()>0&&new BigDecimal(n.path("quotedUnitEquivalentShares").asText("0")).signum()>0,"Unverified share unit or precision "+symbol);
            if(chosen==null||n.path("measurementDate").asText().compareTo(chosen.path("measurementDate").asText())>0||n.path("measurementDate").asText().equals(chosen.path("measurementDate").asText())&&n.path("filedDate").asText().compareTo(chosen.path("filedDate").asText())>0)chosen=n;
        }
        require(chosen!=null,"No whole-issuer prior-filed share observation "+symbol+day);return chosen;
    }
    static final Set<String> verifiedCoverKeys=new HashSet<>();
    static void verifyCoverObservation(JsonNode n)throws Exception{
        String p=n.path("sourcePath").asText(),h=n.path("sourceSha256").asText();require(!p.isBlank()&&!h.isBlank()&&h.equals(QuantStrictDowReplay.sha(Path.of(p))),"Verified share source hash mismatch");
        String key=p+"/"+h+"/"+n.path("quotedUnitEquivalentShares").asText();if(verifiedCoverKeys.contains(key))return;
        if(n.path("symbol").asText().equals("NKE")){
            require(n.path("sourceUrl").asText().startsWith("https://investors.nike.com/sec-filings/"),"Nike original issuer filing required");
            var a=new BigDecimal(n.path("classA").asText("0"));var b=new BigDecimal(n.path("classB").asText("0"));require(a.signum()>0&&b.signum()>0&&a.add(b).compareTo(new BigDecimal(n.path("quotedUnitEquivalentShares").asText()))==0,"Nike exact A+B total required");
            String text=Files.readString(Path.of(p)).replaceAll("<[^>]+>"," ").replace(",","");
            for(var count:List.of(a,b))require(java.util.regex.Pattern.compile("(?<![0-9])"+count.toPlainString()+"(?![0-9])").matcher(text).find(),"Nike class count absent from original");
        }else{
            var receipt=QuantStrictDowReplay.read(Path.of(p));require("PRIMARY_READER_TRANSCRIPT".equals(receipt.path("receiptType").asText())&&!receipt.path("isOriginalHtml").asBoolean()&&receipt.path("sourceUrl").asText().equals(n.path("sourceUrl").asText())&&n.path("sourceUrl").asText().startsWith("https://www.sec.gov/Archives/edgar/data/1403161/"),"Honest Visa primary-reader receipt required");
            boolean found=false;for(var row:receipt.path("observations"))if(row.path("symbol").asText().equals("V")&&row.path("filedDate").equals(n.path("filedDate"))&&row.path("measurementDate").equals(n.path("measurementDate"))&&row.path("quotedUnitEquivalentShares").asText().equals(n.path("quotedUnitEquivalentShares").asText())&&row.path("reportedPrecisionShares").equals(n.path("reportedPrecisionShares"))&&row.path("completeEconomicCoverage").asBoolean()&&row.path("notWeightedAverage").asBoolean()&&row.path("coverageBasis").equals(n.path("coverageBasis")))found=true;
            require(found&&receipt.path("notWeightedAverageEpsShares").asBoolean(),"Visa reported point-in-time total absent from reader receipt");
        }verifiedCoverKeys.add(key);
    }
    static StockMarketSnapshot marketVerified(JdbcTemplate db,String symbol,LocalDate day,BigDecimal close,JsonNode observations,Map<String,Object> audit)throws Exception{
        if(!Set.of("V","NKE").contains(symbol))return QuantStrictDowReplay.market(db,symbol,day,close,observations,audit);
        var n=selectVerifiedShares(symbol,observations,day);verifyCoverObservation(n);var shares=new BigDecimal(n.path("quotedUnitEquivalentShares").asText());
        var result=new StockMarketSnapshot();result.setIndexCode(NAMESPACE);result.setSymbol(symbol);result.setSnapshotDate(day);result.setCurrency("USD");result.setClosePrice(close);result.setSharesOutstanding(shares);result.setMarketCapUsd(shares.multiply(close));result.setPriceSource("COHERENT_PROVIDER_ASOF_NOMINAL_FEATURE_PRICE_RESEARCH");result.setSharesSource("VERIFIED_PRIOR_COMPLETE_ECONOMIC_ISSUER_EQUIVALENT");result.setMarketCapSource("PRIOR_REPORTED_EQUIVALENT_SHARES_X_HISTORICAL_PRICE_RESEARCH");
        audit.put(symbol+"/"+day,Map.of("value",shares,"measurementDate",n.path("measurementDate").asText(),"filedDate",n.path("filedDate").asText(),"source","VERIFIED_PRIOR_COMPLETE_ECONOMIC_ISSUER_EQUIVALENT","evidence",n));return result;
    }

    static List<String> eligiblePopulation(List<String> population,Map<String,NavigableMap<LocalDate,StockCandleDaily>> prices,LocalDate day){
        return eligiblePopulation(population,prices,day,json.createObjectNode());
    }
    static List<String> eligiblePopulation(List<String> population,Map<String,NavigableMap<LocalDate,StockCandleDaily>> prices,LocalDate day,JsonNode issuerRules){
        QuantStrictDowReplay.validatePopulation(population);var out=new ArrayList<String>();
        for(String symbol:population){var series=prices.get(symbol);require(series!=null,"Missing actual price series "+symbol);
            if(symbol.equals("DIS")&&issuerRules.has("DIS")){boolean admitted=false;for(var interval:issuerRules.path("DIS").path("eligibleIntervals"))if(!day.isBefore(LocalDate.parse(interval.path("fromInclusive").asText()))&&day.isBefore(LocalDate.parse(interval.path("toExclusive").asText())))admitted=true;if(!admitted)continue;}
            if(symbol.equals("DOW"))require(series.isEmpty()||!series.firstKey().isBefore(LocalDate.parse("2019-04-02")),"DOW must use regular-way standalone issuer prices from Apr2 2019 only");
            long count=series.headMap(day,true).values().stream().filter(c->c.getClosePrice()!=null&&c.getClosePrice().signum()>0).count();
            if(symbol.equals("DOW")&&count<80)continue;require(count>=80,"Missing80 actual historical closes for non-DOW member "+symbol+day);out.add(symbol);
        }require(out.size()>=28&&out.size()<=30,"Only sealed DOW IPO/DIS issuer exclusions are authorized");return out;
    }
    static String normalizedCik(String cik){return cik==null?"":cik.replaceFirst("^0+","");}
    static boolean issuerAllowed(String symbol,String cik,LocalDate asOf,JsonNode rules){
        if(!symbol.equals("DIS")||!rules.has("DIS"))return true;var rule=rules.path("DIS");var cutover=LocalDate.parse(rule.path("validUntilExclusive").asText());var id=normalizedCik(cik);
        return id.equals(normalizedCik(rule.path("legacyCik").asText()))?asOf.isBefore(cutover):id.equals("1744489")&&!asOf.isBefore(cutover);
    }
    static String evidenceCik(JsonNode evidence)throws Exception{
        String direct=evidence.path("sourceCIK").asText(evidence.path("cik").asText(""));if(!direct.isBlank())return direct;String path=evidence.path("sourcePath").asText("");if(path.isBlank())return "";
        String cached=primaryIssuers.get(path);if(cached==null){cached=QuantStrictDowReplay.read(Path.of(path)).path("cik").asText("");primaryIssuers.put(path,cached);}return cached;
    }
    static JsonNode issuerShares(String symbol,JsonNode observations,LocalDate asOf,JsonNode rules)throws Exception{
        if(!symbol.equals("DIS")||!rules.has("DIS"))return observations;var allowed=json.createArrayNode();for(var n:observations)if(issuerAllowed(symbol,evidenceCik(n),asOf,rules))allowed.add(n);return allowed;
    }
    static List<LocalDate> snapshotRange(LocalDate from,LocalDate to,LocalDate replayDay,LocalDate now){
        boolean presentAnchored=from.equals(now.minusYears(8))&&to.equals(now)&&replayDay.isBefore(from);
        return presentAnchored?List.of(replayDay.minusYears(8),replayDay):List.of(from,to);
    }
    static List<StockExpectedReturnSnapshot> models(String namespace,LocalDate day,List<StockBacktestResult> target,List<StockBacktestResult> training,List<StockExpectedReturnSnapshot> existing,List<StockBenchmarkReturn> benchmarks,List<StockMacroFeatureSnapshot> macro,List<StockMarketSnapshot> markets,int population,boolean complete)throws Exception{
        require(namespace.contains("RESEARCH"),"Research namespace required");
        var working=new ArrayList<StockBacktestResult>();for(var r:training)if(mature(r,day))working.add(json.convertValue(r,StockBacktestResult.class));
        for(var r:target){require(day.equals(r.getSignalDate())&&!mature(r,day),"Targets must be current and immature");working.add(json.convertValue(r,StockBacktestResult.class));}
        var state=new LinkedHashMap<String,StockExpectedReturnSnapshot>();for(var p:existing)if(p.getSignalDate().isBefore(day))require(state.put(predictionKey(p),p)==null,"Duplicate OOF snapshot");
        StockBacktestMapper memory=(StockBacktestMapper)Proxy.newProxyInstance(QuantLongHistoryVerifiedReplay.class.getClassLoader(),new Class[]{StockBacktestMapper.class},(proxy,m,a)->{
            String name=m.getName();
            if(name.equals("findResults"))return working;
            if(name.equals("findBenchmarkReturns"))return benchmarkBefore(benchmarks,day);
            if(name.equals("findExpectedReturnSnapshots")){
                // V8/V9 source admission only: replay their exact present-anchored eight-year query as of this sealed historical day.
                var range=snapshotRange((LocalDate)a[1],(LocalDate)a[2],day,LocalDate.now());var admittedFrom=range.getFirst();var admittedTo=range.getLast();
                return state.values().stream().filter(p->namespace.equals(p.getIndexCode())&&!p.getSignalDate().isAfter(day)&&!p.getSignalDate().isBefore(admittedFrom)&&!p.getSignalDate().isAfter(admittedTo)).toList();
            }
            if(name.equals("findMacroRegimeSnapshots"))return List.of();
            if(name.equals("findMacroFeatureSnapshots"))return macro.stream().filter(p->!p.getSnapshotDate().isAfter(day)).toList();
            if(name.equals("findMarketSnapshots"))return markets.stream().filter(p->!p.getSnapshotDate().isBefore((LocalDate)a[1])&&!p.getSnapshotDate().isAfter((LocalDate)a[2])).toList();
            if(name.equals("upsertExpectedReturnSnapshot")){var p=(StockExpectedReturnSnapshot)a[0];require(namespace.equals(p.getIndexCode())&&day.equals(p.getSignalDate()),"Escaped captured write");state.put(predictionKey(p),p);}
            else require(Set.of("deleteExpectedReturnFactorContributions","copyExpectedReturnFactorContributions","upsertFactorExposureSnapshot","upsertExpectedReturnFactorContribution","deleteExpectedReturnCalibrations","upsertExpectedReturnCalibration").contains(name),"Unexpected model operation "+name);
            return m.getReturnType()==void.class?null:1;
        });
        var factory=new StaticListableBeanFactory();factory.addBean("memory",memory);var provider=factory.getBeanProvider(StockBacktestMapper.class);var service=new StockPortfolioBacktestService(provider,json,0);
        service.refreshExpectedReturnSnapshots(namespace,200000,1,300);service.refreshExpectedReturnV3Snapshots(namespace,200000,1,300);service.refreshExpectedReturnV4Snapshots(namespace,200000,1,300);service.refreshExpectedReturnV5Snapshots(namespace,200000,1,300);service.refreshExpectedReturnV6Snapshots(namespace,200000,1,300);service.refreshExpectedReturnV7Snapshots(namespace,200000,1,300);new StockExpectedReturnV8Service(provider).refreshHistorical(namespace,200000,1);new StockExpectedReturnV9Service(provider).refreshHistorical(namespace,200000,1);
        var result=state.values().stream().filter(p->day.equals(p.getSignalDate())&&Integer.valueOf(20).equals(p.getHorizonDays())).toList();
        if(complete)for(int n=2;n<=9;n++){var v=version(n);require(result.stream().filter(p->v.equals(p.getModelVersion())).count()==population,"Incomplete model "+v+"/"+day);}
        return result;
    }
    static Map<String,Object> readiness(LocalDate cutoff,List<StockBacktestResult> training,List<StockExpectedReturnSnapshot> oof,List<StockExpectedReturnSnapshot> targets,int expectedTargets){
        var outcomes=new HashMap<String,StockBacktestResult>();for(var r:training)if(mature(r,cutoff))require(outcomes.put(rowKey(r),r)==null,"Duplicate training label");
        var seen=new HashSet<String>();var buckets=new TreeMap<String,List<StockExpectedReturnSnapshot>>();
        for(var p:oof){require(seen.add(predictionKey(p)),"Duplicate calibration prediction");if(!Set.of(version(5),version(6)).contains(p.getModelVersion()))continue;
            var r=outcomes.get(p.getSignalDate()+"/"+p.getSymbol()+"/"+p.getHorizonDays());if(r==null||r.getBenchmarkReturnPct()==null||p.getCalibratedUpsideProbabilityPct()==null||!p.getSignalDate().isBefore(cutoff))continue;
            int bucket=p.getCalibratedUpsideProbabilityPct().divideToIntegralValue(BigDecimal.TEN).intValue()*10;
            buckets.computeIfAbsent(p.getModelVersion()+"/"+p.getHorizonDays()+"/"+bucket,k->new ArrayList<>()).add(p);
        }
        boolean ready=true;var checks=new ArrayList<Object>();var summaries=new ArrayList<Object>();
        for(var e:buckets.entrySet()){var rows=e.getValue();summaries.add(Map.of("key",e.getKey(),"rows",rows.size(),"firstDecision",rows.stream().map(StockExpectedReturnSnapshot::getSignalDate).min(LocalDate::compareTo).orElseThrow(),"lastDecision",rows.stream().map(StockExpectedReturnSnapshot::getSignalDate).max(LocalDate::compareTo).orElseThrow()));}
        for(int source:List.of(5,6)){
            var sources=targets.stream().filter(p->version(source).equals(p.getModelVersion())).toList();var adjusted=targets.stream().filter(p->version(source+1).equals(p.getModelVersion())).collect(Collectors.toMap(p->p.getSignalDate()+"/"+p.getSymbol(),Function.identity(),(a,b)->{throw new IllegalStateException("Duplicate target");}));
            ready &= sources.size()==expectedTargets&&adjusted.size()==expectedTargets;
            for(var p:sources){var rows=buckets.getOrDefault(p.getModelVersion()+"/"+p.getHorizonDays()+"/"+p.getCalibrationBucket(),List.of());var next=adjusted.get(p.getSignalDate()+"/"+p.getSymbol());boolean ok=rows.size()>=200&&next!=null&&next.getCalibrationErrorPct()!=null;ready &=ok;
                checks.add(Map.of("decision",p.getSignalDate(),"symbol",p.getSymbol(),"sourceModel",p.getModelVersion(),"probabilityBucket",p.getCalibrationBucket(),"matureOOFRows",rows.size(),"adjustedErrorPresent",next!=null&&next.getCalibrationErrorPct()!=null,"ready",ok));}
        }
        return Map.of("ready",ready,"status",ready?"READY":"NOT_READY","fixedCutoffExclusive",cutoff,"minimumFunctionalBucketRows",200,"diagnosticV8V9Minimum50IsNotFunctionalReadiness",true,"expectedTargets",expectedTargets,"buckets",summaries,"targetChecks",checks);
    }
    static void write(Path path,Object value)throws Exception{Files.writeString(path,json.writerWithDefaultPrettyPrinter().writeValueAsString(value),StandardOpenOption.CREATE_NEW);}
    static void verifyHashes(Map<String,String> hashes)throws Exception{for(var e:hashes.entrySet())require(QuantStrictDowReplay.sha(Path.of(e.getKey())).equals(e.getValue()),"Input/runtime drift "+e.getKey());}
    static void validateRuntime(Map<String,String> hashes)throws Exception{
        for(String name:List.of("HistoricalBacktestSeedService","FinancialInputPolicy","StockBacktestService","StockPortfolioBacktestService","StockExpectedReturnV8Service","StockExpectedReturnV9Service")){String p="target/classes/com/kingyurina/demo/stock/"+name+".class";hashes.put(p,QuantStrictDowReplay.sha(Path.of(p)));}
        for(String name:List.of("HistoricalBacktestSeedMapper","SecFinancialStandardMapper")){String p="target/classes/mapper/stock/"+name+".xml";require(QuantStrictDowReplay.sha(Path.of(p)).equals(QuantStrictDowReplay.sha(Path.of("src/main/resources/mapper/stock/"+name+".xml"))),"Stale runtime SQL "+name);hashes.put(p,QuantStrictDowReplay.sha(Path.of(p)));}
    }
    public static void main(String[]args)throws Exception{
        require(args.length==3||args.length==4,"Usage: SEALED_PROTOCOL expectedSHA NEW_OUTPUT_DIR [INPUT_JSON]");var protocol=Path.of(args[0]);var out=Path.of(args[2]);require(!Files.exists(out),"Refuse output overwrite");require(QuantStrictDowReplay.sha(protocol).equals(args[1]),"Protocol hash mismatch");var sealed=QuantStrictDowReplay.read(protocol);
        var inputPath=Path.of(args.length==4?args[3]:sealed.path("inputsPath").asText());String inputHash=sealed.path("inputsSha256").asText(sealed.path("inputHashes").path(inputPath.toString()).asText());require(!inputHash.isBlank()&&inputHash.equals(QuantStrictDowReplay.sha(inputPath)),"Inputs must be hash sealed");var input=QuantStrictDowReplay.read(inputPath);
        require(NAMESPACE.equals(input.path("namespace").asText())&&"COHERENT_PROVIDER_SPLIT_ADJUSTED_CLOSE_WITH_ASOF_NOMINAL_FEATURES".equals(input.path("pricePolicy").asText()),"Explicit research namespace/coherent label price policy required");
        var providerSnapshot=LocalDate.parse(input.path("providerSnapshotDate").asText());require(providerSnapshot.equals(LocalDate.parse("2024-06-26")),"Fixed provider snapshot required");
        for(var e:input.path("providerEventsBySymbol").properties()){require(QuantStrictDowReplay.ROSTER.contains(e.getKey()),"Unknown event symbol");var seenEvents=new HashSet<LocalDate>();for(var event:e.getValue()){var d=LocalDate.parse(event.path("day").asText());require(!d.isAfter(providerSnapshot)&&seenEvents.add(d)&&Set.of("SPLIT","SEPARATION_RESEARCH").contains(event.path("kind").asText()),"Duplicate/future/unknown provider event");eventRatio(event);}}
        var population=new ArrayList<String>();for(var p:input.path("population"))population.add(p.asText());QuantStrictDowReplay.validatePopulation(population);Collections.sort(population);
        require("ACTUAL_IPO_AND_VERIFIED_ISSUER_ELIGIBILITY".equals(input.path("trainingPopulationPolicy").asText()),"Explicit sealed population eligibility policy required");var issuerRules=input.path("issuerRulesBySymbol");require("1001039".equals(normalizedCik(issuerRules.path("DIS").path("legacyCik").asText()))&&"2019-03-20".equals(issuerRules.path("DIS").path("validUntilExclusive").asText()),"Verified Disney issuer cutover rule required");
        var disIntervals=issuerRules.path("DIS").path("eligibleIntervals");require(disIntervals.size()==2&&"2017-01-31".equals(disIntervals.get(0).path("fromInclusive").asText())&&"2019-03-20".equals(disIntervals.get(0).path("toExclusive").asText())&&"2019-05-09".equals(disIntervals.get(1).path("fromInclusive").asText())&&"2020-12-02".equals(disIntervals.get(1).path("toExclusive").asText()),"Fixed primary-source Disney eligibility intervals required");
        var trainDates=nodeDates(input.path("trainingDecisions"));var tests=nodeDates(input.path("testDecisions"));var cutoff=LocalDate.parse(input.path("fixedCutoff").asText());
        var hashes=new TreeMap<String,String>();hashes.put(protocol.toString(),args[1]);hashes.put(inputPath.toString(),inputHash);for(var e:input.path("inputHashes").properties())hashes.put(e.getKey(),e.getValue().asText());
        for(String p:List.of("scripts/QuantLongHistoryVerifiedReplay.java","scripts/QuantStrictDowReplay.java","src/main/java/com/kingyurina/demo/stock/HistoricalBacktestSeedService.java","src/main/java/com/kingyurina/demo/stock/StockPortfolioBacktestService.java","src/main/java/com/kingyurina/demo/stock/StockExpectedReturnV8Service.java","src/main/java/com/kingyurina/demo/stock/StockExpectedReturnV9Service.java"))hashes.put(p,QuantStrictDowReplay.sha(Path.of(p)));
        for(var e:input.path("sharesBySymbol").properties())for(var n:e.getValue()){String p=n.path("sourcePath").asText(),h=n.path("sourceSha256").asText();require(!p.isBlank()&&!h.isBlank(),"Original shares evidence required");hashes.put(p,h);}
        validateRuntime(hashes);verifyHashes(hashes);
        var benchmark=new ArrayList<StockBenchmarkReturn>();for(var n:input.path("benchmark")){var b=new StockBenchmarkReturn();b.setIndexCode(NAMESPACE);b.setTradeDate(LocalDate.parse(n.path("day").asText()));b.setBenchmarkLevel(new BigDecimal(n.path("level").asText()));b.setSource(n.path("source").asText());require(b.getBenchmarkLevel().signum()>0&&!b.getSource().isBlank(),"Invalid benchmark level/source");benchmark.add(b);}benchmark.sort(Comparator.comparing(StockBenchmarkReturn::getTradeDate));
        StockBenchmarkReturn prior=null;for(var b:benchmark){if(prior!=null&&b.getTradeDate().isBefore(cutoff))b.setReturnPct(b.getBenchmarkLevel().divide(prior.getBenchmarkLevel(),14,RoundingMode.HALF_UP).subtract(BigDecimal.ONE).multiply(BigDecimal.valueOf(100)));prior=b;}
        var calendar=benchmark.stream().map(StockBenchmarkReturn::getTradeDate).toList();validateSchedule(calendar,trainDates,tests,cutoff);
        var prices=new TreeMap<String,NavigableMap<LocalDate,StockCandleDaily>>();for(String symbol:population){var series=new TreeMap<LocalDate,StockCandleDaily>();for(var n:input.path("pricesBySymbol").path(symbol)){var c=new StockCandleDaily();c.setSymbol(symbol);c.setTradeDate(LocalDate.parse(n.path("day").asText()));c.setClosePrice(new BigDecimal(n.path("close").asText()));c.setSource(n.path("source").asText());require(c.getClosePrice().signum()>0&&series.put(c.getTradeDate(),c)==null,"Duplicate/nonpositive price");}require(!series.isEmpty(),"Missing prepared symbol "+symbol);prices.put(symbol,series);}
        var financial=new TreeMap<String,List<SecFinancialStandard>>();for(var e:input.path("financialsBySymbol").properties())financial.put(e.getKey(),QuantStrictDowReplay.list(e.getValue(),SecFinancialStandard.class));
        for(var e:input.path("sectorsBySymbol").properties())QuantStrictDowReplay.sectorsBySymbol.put(e.getKey(),e.getValue().asText());
        QuantStrictDowReplay.validPeriod=Class.forName("com.kingyurina.demo.stock.FinancialInputPolicy").getDeclaredMethod("validPeriod",SecFinancialStandard.class,boolean.class);QuantStrictDowReplay.validPeriod.setAccessible(true);
        var macro=new ArrayList<StockMacroFeatureSnapshot>();for(var n:input.path("macro"))macro.add(json.treeToValue(n,StockMacroFeatureSnapshot.class));
        var app=new SpringApplication(CodexDemoApplication.class);app.setWebApplicationType(WebApplicationType.NONE);
        try(var ctx=app.run("--spring.profiles.active=mariadb","--spring.sql.init.mode=never","--spring.datasource.hikari.read-only=true","--spring.datasource.hikari.connection-init-sql=SET SESSION TRANSACTION READ ONLY","--app.signal.refresh.enabled=false","--app.signal.refresh.run-on-startup=false","--spring.task.scheduling.enabled=false")){
            var db=ctx.getBean(JdbcTemplate.class);require(db.queryForObject("SELECT @@tx_read_only",Integer.class)==1,"Read-only DB required");var before=QuantStrictDowReplay.databaseState(db);var financialReads=new TreeMap<String,Object>();var marketReads=new TreeMap<String,Object>();var units=new TreeMap<String,Object>();var eventsBySymbol=input.path("providerEventsBySymbol");var mapper=asOfOverlay(ctx.getBean(HistoricalBacktestSeedMapper.class),prices,financial,eventsBySymbol,issuerRules,financialReads,units);var builder=ctx.getBean(HistoricalBacktestSeedService.class);var build=HistoricalBacktestSeedService.class.getDeclaredMethod("buildSnapshot",HistoricalBacktestSeedMapper.class,String.class,LocalDate.class);build.setAccessible(true);
            Files.createDirectories(out);var training=new ArrayList<StockBacktestResult>();var rowsByDay=new TreeMap<LocalDate,List<StockBacktestResult>>();var marketsByDay=new TreeMap<LocalDate,List<StockMarketSnapshot>>();var signalsByDay=new TreeMap<LocalDate,List<StockSignalSnapshot>>();var allDays=new ArrayList<>(trainDates);allDays.addAll(tests);
            for(var day:allDays){var dayPopulation=tests.contains(day)?population:eligiblePopulation(population,prices,day,issuerRules);var h=holding(calendar,day);var signals=new ArrayList<StockSignalSnapshot>();var markets=new ArrayList<StockMarketSnapshot>();for(String symbol:dayPopulation){var signal=(StockSignalSnapshot)build.invoke(builder,mapper,symbol,day);require(signal!=null,"Missing rebuilt signal "+symbol+day);signals.add(signal);var events=eventsBySymbol.path(symbol);var close=prices.get(symbol).get(day).getClosePrice().multiply(priceMultiplier(events,day));var market=marketVerified(db,symbol,day,close,issuerShares(symbol,input.path("sharesBySymbol").path(symbol),day,issuerRules),marketReads);market.setIndexCode(NAMESPACE);
                    @SuppressWarnings("unchecked") var chosen=(Map<String,Object>)marketReads.get(symbol+"/"+day);if(symbol.equals("DIS")){var chosenEvidence=json.valueToTree(chosen.get("evidence"));require(issuerAllowed(symbol,evidenceCik(chosenEvidence),day,issuerRules),"Unadmitted Disney share issuer");if(!day.isBefore(LocalDate.parse("2019-03-20")))require(!LocalDate.parse(String.valueOf(chosen.get("measurementDate"))).isBefore(LocalDate.parse("2019-03-20")),"Postmerger Disney cannot use premerger shares");}var originalShares=market.getSharesOutstanding();var adjustedShares=sharesAsOf(originalShares,LocalDate.parse(String.valueOf(chosen.get("filedDate"))),LocalDate.parse(String.valueOf(chosen.get("measurementDate"))),events,day);market.setSharesOutstanding(adjustedShares);market.setMarketCapUsd(adjustedShares.multiply(close));units.put(symbol+"/"+day+"/shares",Map.of("originalActualInstantShares",originalShares,"derivedDecisionAsOfShares",adjustedShares,"measurementDate",chosen.get("measurementDate"),"filedDate",chosen.get("filedDate"),"method","PURE_SPLITS_AFTER_PRIOR_INSTANT_AND_FILING_ONLY_NO_SEPARATION_COUNT_INFERENCE"));markets.add(market);}QuantStrictDowReplay.rank(signals);var rows=new ArrayList<StockBacktestResult>();
                for(var signal:signals){var market=markets.stream().filter(m->m.getSymbol().equals(signal.getSymbol())).findFirst().orElseThrow();StockBacktestResult row;if(trainDates.contains(day)){var bars=prices.get(signal.getSymbol()).subMap(h.getFirst(),true,h.getLast(),true).values().stream().toList();require(bars.size()==21&&bars.stream().map(StockCandleDaily::getTradeDate).toList().equals(h),"Incomplete training holding calendar "+signal.getSymbol()+day);row=QuantStrictDowReplay.outcome(signal,bars.getFirst(),bars.getLast(),market);Double b=CompleteBenchmarkReturn.compound(benchmark,NAMESPACE,h.getFirst(),h.getLast(),20);require(b!=null,"Missing training benchmark");row.setBenchmarkReturnPct(BigDecimal.valueOf(b));require(mature(row,cutoff),"Unpurged fixed training label");training.add(row);}else row=dummy(signal,market,h);rows.add(row);}
                rowsByDay.put(day,rows);marketsByDay.put(day,markets);signalsByDay.put(day,signals);if(rowsByDay.size()%10==0)System.out.println("LONG_HISTORY_BUILD "+rowsByDay.size()+"/"+allDays.size());
            }
            require(training.size()==trainDates.stream().mapToInt(d->eligiblePopulation(population,prices,d,issuerRules).size()).sum(),"Incomplete dynamic training population");System.out.println("LONG_HISTORY_MARKET_PREFLIGHT_PASS all admitted dates and mandatory actual market observations built before OOF");write(out.resolve("market-input-audit.json"),marketReads);write(out.resolve("financial-input-audit.json"),financialReads);write(out.resolve("unit-input-audit.json"),units);if(Boolean.getBoolean("quant.replay.preflightOnly")){System.out.println("VERIFIED_PREFLIGHT_ONLY_DONE noOOF/noHeldout");return;}String frozenTraining=QuantStrictDowReplay.objectHash(training);var oof=new ArrayList<StockExpectedReturnSnapshot>();var oofAudit=new ArrayList<Object>();int i=0;
            for(var day:trainDates){verifyHashes(Map.of(protocol.toString(),args[1]));var eligible=training.stream().filter(r->mature(r,day)).toList();List<StockExpectedReturnSnapshot> predicted=eligible.size()<300?List.of():models(NAMESPACE,day,rowsByDay.get(day),eligible,oof,benchmark,macro,marketsByDay.get(day),rowsByDay.get(day).size(),true);oof.addAll(predicted);oofAudit.add(Map.of("decision",day,"matureTrainingRows",eligible.size(),"rankPopulation",rowsByDay.get(day).size(),"predictions",predicted.size()));if(++i%10==0)System.out.println("LONG_HISTORY_OOF "+i+"/"+trainDates.size()+" snapshots="+oof.size());}
            String frozenOOF=QuantStrictDowReplay.objectHash(oof);var candidate=new ArrayList<StockExpectedReturnSnapshot>();var baseline=new ArrayList<StockExpectedReturnSnapshot>();
            for(var day:tests){var target=rowsByDay.get(day);var a=models(NAMESPACE,day,target,training,List.of(),benchmark,macro,marketsByDay.get(day),30,true);var b=models(NAMESPACE,day,target,training,oof,benchmark,macro,marketsByDay.get(day),30,true);var altered=new ArrayList<StockBacktestResult>();for(var r:target){var copy=json.convertValue(r,StockBacktestResult.class);copy.setForwardReturnPct(BigDecimal.valueOf(-95));copy.setBenchmarkReturnPct(BigDecimal.valueOf(80));altered.add(copy);}
                require(QuantStrictDowReplay.normalized(a).equals(QuantStrictDowReplay.normalized(models(NAMESPACE,day,altered,training,List.of(),benchmark,macro,marketsByDay.get(day),30,true))),"Baseline future-label leak");require(QuantStrictDowReplay.normalized(b).equals(QuantStrictDowReplay.normalized(models(NAMESPACE,day,altered,training,oof,benchmark,macro,marketsByDay.get(day),30,true))),"Candidate future-label leak");baseline.addAll(a);candidate.addAll(b);
            }
            require(frozenTraining.equals(QuantStrictDowReplay.objectHash(training))&&frozenOOF.equals(QuantStrictDowReplay.objectHash(oof)),"Frozen archives mutated");var readiness=readiness(cutoff,training,oof,candidate,60);var v9OOF=oof.stream().filter(p->version(9).equals(p.getModelVersion())).toList();write(out.resolve("forecast-only.json"),Map.of("classification","FORECAST_ONLY_NO_HELDOUT_LABELS","protocolSha256",args[1],"baselinePredictions",baseline,"predictions",candidate,"futureLabelsInvariantBothArms",true,"trainingRows",training,"oofPredictions",v9OOF));write(out.resolve("readiness.json"),readiness);write(out.resolve("chronological-oof.json"),oof);
            var report=new LinkedHashMap<String,Object>();report.put("status",readiness.get("status"));report.put("classification","FIXED_COHORT_2017_2020_TRAINING_2020_TESTS_LONG_CHRONOLOGICAL_OOF_RESEARCH_NOT_PROSPECTIVE");report.put("protocolSha256",args[1]);report.put("researchNamespace",NAMESPACE);report.put("trainingRows",training);report.put("oofPredictions",v9OOF);report.put("priceUnitAudit",units);report.put("baselinePredictions",baseline);report.put("predictions",candidate);report.put("readiness",readiness);report.put("oofAudit",oofAudit);report.put("trainingAudit",Map.of("fixedCutoffExclusive",cutoff,"trainingDecisions",trainDates,"trainingRows",training.size(),"trainingSha256",frozenTraining,"oofSha256",frozenOOF,"chronologicalOOF",oofAudit,"fullCohortRankPopulation","Complete admitted28/29/30 cohort per sealed DOW IPO and DIS issuer availability rules; all targets30","holdoutLabelsRolledIntoTraining",0,"trainingPopulationPolicy","ACTUAL_IPO_AND_VERIFIED_ISSUER_ELIGIBILITY","v8v9SourceAdmissionClock","Only exact present-anchored eight-year source queries rebased to sealed replay decision"));
            var dates=new ArrayList<Object>();if(Boolean.TRUE.equals(readiness.get("ready"))){verifyHashes(hashes);StockBenchmarkReturn previous=null;for(var b:benchmark){if(previous!=null)b.setReturnPct(b.getBenchmarkLevel().divide(previous.getBenchmarkLevel(),14,RoundingMode.HALF_UP).subtract(BigDecimal.ONE).multiply(BigDecimal.valueOf(100)));previous=b;}for(var day:tests){var h=holding(calendar,day);Double b=CompleteBenchmarkReturn.compound(benchmark,NAMESPACE,h.getFirst(),h.getLast(),20);require(b!=null,"Missing held-out benchmark");var pairs=new ArrayList<Object>();for(String symbol:population){var bars=prices.get(symbol).subMap(h.getFirst(),true,h.getLast(),true).values().stream().toList();require(bars.size()==21&&bars.stream().map(StockCandleDaily::getTradeDate).toList().equals(h),"Incomplete held-out closes "+symbol+day);var p=candidate.stream().filter(v->version(9).equals(v.getModelVersion())&&day.equals(v.getSignalDate())&&symbol.equals(v.getSymbol())).findFirst().orElseThrow();var a=baseline.stream().filter(v->version(9).equals(v.getModelVersion())&&day.equals(v.getSignalDate())&&symbol.equals(v.getSymbol())).findFirst().orElseThrow();var pair=new LinkedHashMap<String,Object>();pair.put("symbol",symbol);pair.put("entry",h.getFirst());pair.put("exit",h.getLast());pair.put("entryClose",bars.getFirst().getClosePrice());pair.put("exitClose",bars.getLast().getClosePrice());pair.put("actual",bars.getLast().getClosePrice().divide(bars.getFirst().getClosePrice(),14,RoundingMode.HALF_UP).subtract(BigDecimal.ONE).multiply(BigDecimal.valueOf(100)));pair.put("benchmark",b);pair.put("baselineAbsolute",a.getExpectedReturnPct());pair.put("baselineExcess",a.getExpectedExcessReturnPct());pair.put("calibratedAbsolute",p.getExpectedReturnPct());pair.put("calibratedExcess",p.getExpectedExcessReturnPct());pairs.add(pair);}dates.add(Map.of("decision",day,"newSignals",signalsByDay.get(day),"pairs",pairs));}report.put("status","SCORED");}
            report.put("dates",dates);report.put("heldoutOutcomesComputed",Boolean.TRUE.equals(readiness.get("ready")));report.put("financialReads",financialReads);report.put("marketReads",marketReads);var after=QuantStrictDowReplay.databaseState(db);require(before.equals(after),"Database state drift");report.put("sourceDatabaseBefore",before);report.put("sourceDatabaseAfter",after);report.put("databaseWrites",0);report.put("promotionAllowed",false);report.put("inputHashes",hashes);report.put("limitations",List.of("Fixed post-Aug2020 target cohort during2017-2020 training: survivorship selection, not certified historical DOW membership; DOW omitted only until80 actual regular-way standalone closes exist; DIS omitted2019Mar20-May8 solely because actual postmerger issuer shares were not yet publicly filed.","Daily overlapping20D cohorts do not constitute independent observations.","Missing sector/analyst/news/institution/macro inputs retain production fallbacks; no current substitutes.","Prepared nominal prices, stale prior shares, historical filing reconstruction and provider adjustment rounding retain source limitations.","V8/V9 calibration exports are diagnostics only; functional calibration is V5-to-V6 and V6-to-V7.","Historical research, not prospective archived forecasts; no model promotion."));verifyHashes(hashes);write(out.resolve("result.json"),report);System.out.println("LONG_HISTORY_DONE status="+report.get("status")+" training="+training.size()+" oof="+oof.size()+" DBwrites=0");
        }
    }
}
