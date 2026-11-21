import com.kingyurina.demo.stock.*;
import java.math.BigDecimal;
import java.lang.reflect.*;
import java.time.*;
import java.util.*;

/** Synthetic tests only: never opens real held-out prices or a database. */
public final class QuantLongHistoryReplayTest {
    static Class<?> subject;
    static Object call(String name,Class<?>[] types,Object...args)throws Exception {
        var m=subject.getDeclaredMethod(name,types);m.setAccessible(true);
        try{return m.invoke(null,args);}catch(InvocationTargetException e){throw (Exception)e.getCause();}
    }
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static void fails(String name,Class<?>[] types,Object...args)throws Exception {
        boolean failed=false;try{call(name,types,args);}catch(IllegalArgumentException|IllegalStateException e){failed=true;}
        check(failed,"Expected fail-closed "+name);
    }
    static StockExpectedReturnSnapshot prediction(String version,StockBacktestResult r,int bucket){
        var p=new StockExpectedReturnSnapshot();p.setModelVersion("EXPECTED_RETURN_V"+version+"_CORRECTED_20261017");p.setSymbol(r.getSymbol());p.setSignalDate(r.getSignalDate());p.setHorizonDays(20);p.setCalibrationBucket(bucket);p.setCalibratedUpsideProbabilityPct(BigDecimal.valueOf(bucket+5));return p;
    }
    public static void main(String[]args)throws Exception{
        try{subject=Class.forName("QuantLongHistoryReplay");}catch(ClassNotFoundException e){throw new AssertionError("Long chronological replay is not implemented",e);}
        var cutoff=LocalDate.parse("2020-09-30");var calendar=new ArrayList<LocalDate>();for(var d=LocalDate.parse("2019-03-01");!d.isAfter(LocalDate.parse("2020-12-15"));d=d.plusDays(1))if(d.getDayOfWeek().getValue()<6)calendar.add(d);
        var trainDates=calendar.stream().filter(d->!d.isBefore(LocalDate.parse("2019-07-31"))&&!d.isAfter(LocalDate.parse("2020-08-28"))).toList();var tests=List.of(cutoff,LocalDate.parse("2020-10-30"));
        var scheduleTypes=new Class[]{List.class,List.class,List.class,LocalDate.class};call("validateSchedule",scheduleTypes,calendar,trainDates,tests,cutoff);
        fails("validateSchedule",scheduleTypes,calendar,trainDates.subList(1,trainDates.size()),tests,cutoff);
        fails("validateSchedule",scheduleTypes,calendar,trainDates,List.of(tests.getLast(),tests.getFirst()),cutoff);
        fails("validateSchedule",scheduleTypes,calendar,trainDates,tests,cutoff.plusDays(1));
        var r=QuantStrictDowReplayTest.row("S",cutoff.minusDays(25));r.setExitTradeDate(cutoff);check(!(boolean)call("mature",new Class[]{StockBacktestResult.class,LocalDate.class},r,cutoff),"Same-day exit excluded");r.setExitTradeDate(cutoff.minusDays(1));check((boolean)call("mature",new Class[]{StockBacktestResult.class,LocalDate.class},r,cutoff),"Prior exit mature");
        var signal=new StockSignalSnapshot();signal.setSymbol("S");signal.setSignalDate(cutoff);signal.setIntegratedScore(50);var market=new StockMarketSnapshot();market.setMarketCapUsd(BigDecimal.valueOf(1000000));
        var holding=List.of(cutoff.plusDays(1),cutoff.plusDays(29));var dummy=(StockBacktestResult)call("dummy",new Class[]{StockSignalSnapshot.class,StockMarketSnapshot.class,List.class},signal,market,holding);
        check(dummy.getEntryClose().compareTo(BigDecimal.valueOf(100))==0&&dummy.getExitClose().compareTo(BigDecimal.valueOf(100))==0&&dummy.getForwardReturnPct().signum()==0&&dummy.getBenchmarkReturnPct().signum()==0,"Targets must be synthetic zero labels without realized prices");
        var training=new ArrayList<StockBacktestResult>();var oof=new ArrayList<StockExpectedReturnSnapshot>();
        for(int i=0;i<200;i++){var row=QuantStrictDowReplayTest.row("S"+i,LocalDate.parse("2020-01-02"));training.add(row);oof.add(prediction("5",row,50));oof.add(prediction("6",row,50));}
        var target5=prediction("5",dummy,50);var target6=prediction("6",dummy,50);target6.setCalibrationErrorPct(BigDecimal.ONE);var target7=prediction("7",dummy,50);target7.setCalibrationErrorPct(BigDecimal.ONE);
        var readyTypes=new Class[]{LocalDate.class,List.class,List.class,List.class,int.class};
        @SuppressWarnings("unchecked") var ready=(Map<String,Object>)call("readiness",readyTypes,cutoff,training,oof,List.of(target5,target6,target7),1);
        check(Boolean.TRUE.equals(ready.get("ready")),"200 matured same-bucket OOF rows should admit both functional stages");
        @SuppressWarnings("unchecked") var shortHistory=(Map<String,Object>)call("readiness",readyTypes,cutoff,training.subList(0,199),oof,List.of(target5,target6,target7),1);
        check(Boolean.FALSE.equals(shortHistory.get("ready")),"199 rows must not pass 200 threshold");
        oof.add(oof.getFirst());fails("readiness",readyTypes,cutoff,training,oof,List.of(target5,target6,target7),1);oof.removeLast();
        target7.setCalibrationErrorPct(null);
        @SuppressWarnings("unchecked") var noError=(Map<String,Object>)call("readiness",readyTypes,cutoff,training,oof,List.of(target5,target6,target7),1);check(Boolean.FALSE.equals(noError.get("ready")),"Missing V7 functional error must block scoring");
        var ns="DOW30_LONG_OOF_RESEARCH_SYNTHETIC";var day=LocalDate.parse("2020-09-30");training.clear();oof.clear();var symbols=new ArrayList<>(QuantStrictDowReplay.ROSTER);Collections.sort(symbols);
        for(int d=0;d<12;d++)for(String symbol:symbols){var row=QuantStrictDowReplayTest.row(symbol,LocalDate.parse("2019-08-01").plusDays(25L*d));row.setForwardReturnPct(BigDecimal.valueOf(1));training.add(row);}
        var targets=new ArrayList<StockBacktestResult>();var markets=new ArrayList<StockMarketSnapshot>();for(String symbol:symbols){targets.add(QuantStrictDowReplayTest.row(symbol,day));var m=new StockMarketSnapshot();m.setIndexCode(ns);m.setSymbol(symbol);m.setSnapshotDate(day);m.setMarketCapUsd(BigDecimal.valueOf(1000000000));m.setSharesSource("SEC_XBRL_SYNTHETIC");markets.add(m);}
        var benchmark=new ArrayList<StockBenchmarkReturn>();for(var d=LocalDate.parse("2019-08-01");d.isBefore(day.plusDays(40));d=d.plusDays(1)){var b=new StockBenchmarkReturn();b.setIndexCode(ns);b.setTradeDate(d);b.setBenchmarkLevel(BigDecimal.valueOf(100));b.setReturnPct(BigDecimal.ZERO);b.setSource("SYNTHETIC_BENCHMARK");benchmark.add(b);}
        var modelTypes=new Class[]{String.class,LocalDate.class,List.class,List.class,List.class,List.class,List.class,List.class,int.class,boolean.class};
        @SuppressWarnings("unchecked") var base=(List<StockExpectedReturnSnapshot>)call("models",modelTypes,ns,day,targets,training,List.of(),benchmark,List.of(),markets,30,true);check(base.size()==240,"Eight production models x30 complete targets");
        int bucket5=base.stream().filter(p->p.getModelVersion().contains("V5_")).findFirst().orElseThrow().getCalibrationBucket();for(var row:training)oof.add(prediction("5",row,bucket5));
        @SuppressWarnings("unchecked") var v5Adjusted=(List<StockExpectedReturnSnapshot>)call("models",modelTypes,ns,day,targets,training,oof,benchmark,List.of(),markets,30,true);
        var sample6=v5Adjusted.stream().filter(p->p.getModelVersion().contains("V6_")).findFirst().orElseThrow();check(sample6.getCalibrationErrorPct()!=null,"Real V6 must consume matured V5 OOF");for(var row:training)oof.add(prediction("6",row,sample6.getCalibrationBucket()));
        @SuppressWarnings("unchecked") var candidate=(List<StockExpectedReturnSnapshot>)call("models",modelTypes,ns,day,targets,training,oof,benchmark,List.of(),markets,30,true);check(candidate.stream().filter(p->p.getModelVersion().contains("V7_")).allMatch(p->p.getCalibrationErrorPct()!=null),"Real V7 must consume matured V6 OOF");
        for(var row:targets){row.setForwardReturnPct(BigDecimal.valueOf(-95));row.setBenchmarkReturnPct(BigDecimal.valueOf(80));}
        @SuppressWarnings("unchecked") var perturbed=(List<StockExpectedReturnSnapshot>)call("models",modelTypes,ns,day,targets,training,oof,benchmark,List.of(),markets,30,true);
        check(QuantStrictDowReplay.normalized(candidate).equals(QuantStrictDowReplay.normalized(perturbed)),"Held-out labels cannot alter candidate forecasts");
        for(var b:benchmark)if(!b.getTradeDate().isBefore(day))b.setReturnPct(BigDecimal.valueOf(70));
        @SuppressWarnings("unchecked") var futureBenchmark=(List<StockExpectedReturnSnapshot>)call("models",modelTypes,ns,day,targets,training,oof,benchmark,List.of(),markets,30,true);
        check(QuantStrictDowReplay.normalized(candidate).equals(QuantStrictDowReplay.normalized(futureBenchmark)),"Future benchmark increments cannot enter forecast mapper");
        @SuppressWarnings("unchecked") var pastBenchmark=(List<StockBenchmarkReturn>)call("benchmarkBefore",new Class[]{List.class,LocalDate.class},benchmark,day);
        check(pastBenchmark.stream().allMatch(b->b.getTradeDate().isBefore(day)),"Prediction mapper must not expose same/future benchmark observations even to diagnostics");
        var events=QuantStrictDowReplay.json.readTree("[{\"day\":\"2020-08-31\",\"numerator\":4,\"denominator\":1,\"kind\":\"SPLIT\"},{\"day\":\"2024-02-26\",\"numerator\":3,\"denominator\":1,\"kind\":\"SPLIT\"}]");
        var eventTypes=new Class[]{tools.jackson.databind.JsonNode.class,LocalDate.class};
        check(((BigDecimal)call("priceMultiplier",eventTypes,events,LocalDate.parse("2020-08-28"))).compareTo(BigDecimal.valueOf(12))==0,"All provider-future events reverse uniformly at decision time");
        check(((BigDecimal)call("priceMultiplier",eventTypes,events,LocalDate.parse("2020-09-30"))).compareTo(BigDecimal.valueOf(3))==0,"Past split is not undone in post-split features");
        var eps=new SecFinancialStandard();eps.setSymbol("AAPL");eps.setFiledAt(LocalDate.parse("2020-07-31"));eps.setEpsDiluted(BigDecimal.valueOf(12));eps.setEpsUnit("USD/shares");eps.setCurrency("USD");
        var epsTypes=new Class[]{SecFinancialStandard.class,tools.jackson.databind.JsonNode.class,LocalDate.class};
        var adjusted=(SecFinancialStandard)call("financialAsOf",epsTypes,eps,events,day);check(adjusted.getEpsDiluted().compareTo(BigDecimal.valueOf(3))==0&&eps.getEpsDiluted().compareTo(BigDecimal.valueOf(12))==0,"EPS adjusts prior filing once without source mutation");
        eps.setFiledAt(LocalDate.parse("2020-09-01"));adjusted=(SecFinancialStandard)call("financialAsOf",epsTypes,eps,events,day);check(adjusted.getEpsDiluted().compareTo(BigDecimal.valueOf(12))==0,"Already post-split filing EPS cannot be divided again");
        eps.setFiledAt(LocalDate.parse("2020-08-31"));adjusted=(SecFinancialStandard)call("financialAsOf",epsTypes,eps,events,day);check(adjusted.getEpsDiluted()==null,"Same-day split filing EPS blocked as ambiguous");
        var shareTypes=new Class[]{BigDecimal.class,LocalDate.class,LocalDate.class,tools.jackson.databind.JsonNode.class,LocalDate.class};
        check(((BigDecimal)call("sharesAsOf",shareTypes,BigDecimal.valueOf(10),LocalDate.parse("2020-07-31"),LocalDate.parse("2020-07-17"),events,day)).compareTo(BigDecimal.valueOf(40))==0,"Prior instant shares adjust by pure split");
        fails("sharesAsOf",shareTypes,BigDecimal.TEN,LocalDate.parse("2020-09-01"),LocalDate.parse("2020-07-17"),events,day);
        var stockSignal=new StockSignalSnapshot();stockSignal.setSymbol("AAPL");stockSignal.setSignalDate(LocalDate.parse("2020-08-03"));stockSignal.setIntegratedScore(50);var entry=QuantLongHistoryReplayTest.candle("2020-08-04",100);var exit=QuantLongHistoryReplayTest.candle("2020-09-01",105);
        var label=QuantStrictDowReplay.outcome(stockSignal,entry,exit,market);check(label.getForwardReturnPct().compareTo(BigDecimal.valueOf(5))==0,"Coherent provider closes across split yield +5%, not artificial -73.75%");
        System.out.println("LONG_HISTORY_SYNTHETIC_PASS schedule/purge/dummy/200-bucket/duplicate/null gates +240 actual model predictions + functional V6/V7 calibration + future-label invariance");
    }
    static StockCandleDaily candle(String day,int close){var c=new StockCandleDaily();c.setSymbol("AAPL");c.setTradeDate(LocalDate.parse(day));c.setClosePrice(BigDecimal.valueOf(close));return c;}
}
