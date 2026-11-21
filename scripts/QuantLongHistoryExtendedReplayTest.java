import com.kingyurina.demo.stock.*;
import java.math.BigDecimal;
import java.lang.reflect.*;
import java.time.*;
import java.util.*;

/** Synthetic only; no database or held-out outcome access. */
public final class QuantLongHistoryExtendedReplayTest {
    static Class<?> subject;
    static Object call(String name,Class<?>[] types,Object...args)throws Exception{var m=subject.getDeclaredMethod(name,types);m.setAccessible(true);try{return m.invoke(null,args);}catch(InvocationTargetException e){throw (Exception)e.getCause();}}
    static void check(boolean ok,String text){if(!ok)throw new AssertionError(text);}
    static StockCandleDaily candle(String symbol,LocalDate day){var r=new StockCandleDaily();r.setSymbol(symbol);r.setTradeDate(day);r.setClosePrice(BigDecimal.valueOf(100));return r;}
    public static void main(String[]args)throws Exception{
        try{subject=Class.forName("QuantLongHistoryExtendedReplay");}catch(ClassNotFoundException e){throw new AssertionError("Extended chronological replay is not implemented",e);}
        var population=new ArrayList<>(QuantStrictDowReplay.ROSTER);Collections.sort(population);var prices=new TreeMap<String,NavigableMap<LocalDate,StockCandleDaily>>();
        for(String s:population){var series=new TreeMap<LocalDate,StockCandleDaily>();if(!s.equals("DOW"))for(int i=0;i<100;i++){var d=LocalDate.parse("2016-08-01").plusDays(i);series.put(d,candle(s,d));}prices.put(s,series);}
        var eligibleTypes=new Class[]{List.class,Map.class,LocalDate.class};
        @SuppressWarnings("unchecked") var early=(List<String>)call("eligiblePopulation",eligibleTypes,population,prices,LocalDate.parse("2017-01-31"));check(early.size()==29&&!early.contains("DOW"),"Pre-IPO training must use29 actual histories, never predecessor DOW");
        var dowDays=new ArrayList<LocalDate>();for(var d=LocalDate.parse("2019-04-02");dowDays.size()<80;d=d.plusDays(1))if(d.getDayOfWeek().getValue()<6)dowDays.add(d);
        for(var d:dowDays.subList(0,79))prices.get("DOW").put(d,candle("DOW",d));
        @SuppressWarnings("unchecked") var insufficient=(List<String>)call("eligiblePopulation",eligibleTypes,population,prices,dowDays.get(78));check(insufficient.size()==29,"79 actual DOW closes cannot pass80 threshold");
        prices.get("DOW").put(dowDays.get(79),candle("DOW",dowDays.get(79)));
        @SuppressWarnings("unchecked") var full=(List<String>)call("eligiblePopulation",eligibleTypes,population,prices,dowDays.get(79));check(full.size()==30&&full.contains("DOW"),"80 actual DOW closes admit30-cohort with no discretion");
        prices.get("DOW").put(LocalDate.parse("2019-03-20"),candle("DOW",LocalDate.parse("2019-03-20")));
        boolean denied=false;try{call("eligiblePopulation",eligibleTypes,population,prices,dowDays.get(79));}catch(IllegalStateException e){denied=true;}check(denied,"When-issued/predecessor history before Apr2 must be rejected");prices.get("DOW").remove(LocalDate.parse("2019-03-20"));
        prices.get("MMM").clear();denied=false;try{call("eligiblePopulation",eligibleTypes,population,prices,LocalDate.parse("2017-01-31"));}catch(IllegalStateException e){denied=true;}check(denied,"No other missing member may be silently dropped");
        for(int i=0;i<100;i++){var d=LocalDate.parse("2016-08-01").plusDays(i);prices.get("MMM").put(d,candle("MMM",d));}
        var issuerRules=QuantStrictDowReplay.json.readTree("{\"DIS\":{\"legacyCik\":\"1001039\",\"validUntilExclusive\":\"2019-03-20\",\"eligibleIntervals\":[{\"fromInclusive\":\"2017-01-31\",\"toExclusive\":\"2019-03-20\"},{\"fromInclusive\":\"2019-05-09\",\"toExclusive\":\"2020-12-02\"}]}}");
        var issuerEligibleTypes=new Class[]{List.class,Map.class,LocalDate.class,tools.jackson.databind.JsonNode.class};
        @SuppressWarnings("unchecked") var disGap=(List<String>)call("eligiblePopulation",issuerEligibleTypes,population,prices,LocalDate.parse("2019-03-20"),issuerRules);check(disGap.size()==28&&!disGap.contains("DIS")&&!disGap.contains("DOW"),"DIS issuer transition gap and DOW pre-eligibility permit exactly28 training names");
        @SuppressWarnings("unchecked") var disReady=(List<String>)call("eligiblePopulation",issuerEligibleTypes,population,prices,LocalDate.parse("2019-05-09"),issuerRules);check(disReady.size()==29&&disReady.contains("DIS")&&!disReady.contains("DOW"),"First proven new DIS count strictfiled+1 restores member");
        var issuerAllowedTypes=new Class[]{String.class,String.class,LocalDate.class,tools.jackson.databind.JsonNode.class};
        check((boolean)call("issuerAllowed",issuerAllowedTypes,"DIS","0001001039",LocalDate.parse("2019-03-19"),issuerRules),"Legacy Disney financial/share issuer allowed before cutover");
        check(!(boolean)call("issuerAllowed",issuerAllowedTypes,"DIS","1001039",LocalDate.parse("2019-03-20"),issuerRules),"Legacy Disney issuer must never bridge merger cutover");
        check((boolean)call("issuerAllowed",issuerAllowedTypes,"DIS","1744489",LocalDate.parse("2019-05-09"),issuerRules),"Verified new Disney issuer allowed after readiness");
        var calendar=new ArrayList<LocalDate>();for(var d=LocalDate.parse("2016-10-31");d.isBefore(LocalDate.parse("2020-12-15"));d=d.plusDays(1))if(d.getDayOfWeek().getValue()<6)calendar.add(d);var trainDates=calendar.stream().filter(d->!d.isBefore(LocalDate.parse("2017-01-31"))&&!d.isAfter(LocalDate.parse("2020-08-28"))).toList();
        call("validateSchedule",new Class[]{List.class,List.class,List.class,LocalDate.class},calendar,trainDates,List.of(LocalDate.parse("2020-09-30"),LocalDate.parse("2020-10-30")),LocalDate.parse("2020-09-30"));
        var now=LocalDate.parse("2026-10-31");var replayDay=LocalDate.parse("2017-06-30");var rangeTypes=new Class[]{LocalDate.class,LocalDate.class,LocalDate.class,LocalDate.class};
        check(call("snapshotRange",rangeTypes,LocalDate.parse("2018-10-31"),now,replayDay,now).equals(List.of(LocalDate.parse("2009-06-30"),replayDay)),"Only exact present-anchored8year source query may rebase to2017");
        check(call("snapshotRange",rangeTypes,LocalDate.parse("2017-01-31"),replayDay,replayDay,now).equals(List.of(LocalDate.parse("2017-01-31"),replayDay)),"Normal OOF calibration query retains exact bounds");
        check(call("snapshotRange",rangeTypes,LocalDate.parse("2018-10-30"),now,replayDay,now).equals(List.of(LocalDate.parse("2018-10-30"),now)),"Nearly matching8year source query must not widen");
        var ns="DOW30_LONG_EXTENDED_OOF_RESEARCH_SYNTHETIC";var day=LocalDate.parse("2017-06-30");var training=new ArrayList<StockBacktestResult>();var targets=new ArrayList<StockBacktestResult>();var markets=new ArrayList<StockMarketSnapshot>();var p29=population.stream().filter(s->!s.equals("DOW")).toList();
        for(int i=0;i<11;i++)for(String s:p29){var r=QuantStrictDowReplayTest.row(s,LocalDate.parse("2017-01-31").plusDays(i));training.add(r);}
        for(String s:p29){targets.add(QuantStrictDowReplayTest.row(s,day));var m=new StockMarketSnapshot();m.setIndexCode(ns);m.setSymbol(s);m.setSnapshotDate(day);m.setMarketCapUsd(BigDecimal.valueOf(1000000000));m.setSharesSource("SEC_XBRL_SYNTHETIC");markets.add(m);}
        var benchmark=new ArrayList<StockBenchmarkReturn>();for(var d=LocalDate.parse("2017-01-01");d.isBefore(LocalDate.parse("2017-09-01"));d=d.plusDays(1)){var b=new StockBenchmarkReturn();b.setIndexCode(ns);b.setTradeDate(d);b.setBenchmarkLevel(BigDecimal.valueOf(100));b.setReturnPct(BigDecimal.ZERO);b.setSource("SYNTHETIC");benchmark.add(b);}
        var modelTypes=new Class[]{String.class,LocalDate.class,List.class,List.class,List.class,List.class,List.class,List.class,int.class,boolean.class};
        @SuppressWarnings("unchecked") var predicted=(List<StockExpectedReturnSnapshot>)call("models",modelTypes,ns,day,targets,training,List.of(),benchmark,List.of(),markets,29,true);check(predicted.size()==232,"2017 requires29 predictions from all8actualmodels despite present-anchored8yr source query");
        for(var r:targets)r.setForwardReturnPct(BigDecimal.valueOf(-99));
        @SuppressWarnings("unchecked") var changed=(List<StockExpectedReturnSnapshot>)call("models",modelTypes,ns,day,targets,training,List.of(),benchmark,List.of(),markets,29,true);check(QuantStrictDowReplay.normalized(predicted).equals(QuantStrictDowReplay.normalized(changed)),"2017 replay forecasts cannot depend on target labels");
        System.out.println("EXTENDED_SYNTHETIC_PASS 29/preIPO/79vs80/regularway/no-other-drop/schedule +232 actual2017modeloutputs +futurelabelinvariance");
    }
}
