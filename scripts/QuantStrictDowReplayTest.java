import com.kingyurina.demo.stock.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

/** Synthetic contract checks only; never opens the historical test outcomes. */
public final class QuantStrictDowReplayTest {
    static Class<?> replay;
    static Object call(String name,Class<?>[] types,Object...args)throws Exception {
        var m=replay.getDeclaredMethod(name,types);m.setAccessible(true);
        try{return m.invoke(null,args);}catch(InvocationTargetException e){throw (Exception)e.getCause();}
    }
    static void fails(String name,Class<?>[] types,Object...args)throws Exception {
        boolean failed=false;try{call(name,types,args);}catch(IllegalStateException|IllegalArgumentException e){failed=true;}
        if(!failed)throw new AssertionError("Expected fail-closed "+name);
    }
    public static void main(String[] args)throws Exception {
        try{replay=Class.forName("QuantStrictDowReplay");}catch(ClassNotFoundException e){throw new AssertionError("Strict DOW replay behavior is not implemented",e);}
        var symbols=List.of("MMM","AXP","AMGN","AAPL","BA","CAT","CVX","CSCO","KO","DIS","DOW","GS","HD","HON","IBM","INTC","JNJ","JPM","MCD","MRK","MSFT","NKE","PG","CRM","TRV","UNH","VZ","V","WBA","WMT");
        call("validatePopulation",new Class[]{List.class},symbols);
        fails("validatePopulation",new Class[]{List.class},symbols.subList(0,29));
        var duplicate=new ArrayList<>(symbols);duplicate.set(29,"WBA");fails("validatePopulation",new Class[]{List.class},duplicate);
        var day=LocalDate.parse("2021-03-31");var calendar=new ArrayList<LocalDate>();for(var d=day;!d.isAfter(LocalDate.parse("2021-06-01"));d=d.plusDays(1))if(d.getDayOfWeek().getValue()<6&&!Set.of(LocalDate.parse("2021-04-02"),LocalDate.parse("2021-05-31")).contains(d))calendar.add(d);
        @SuppressWarnings("unchecked") var hold=(List<LocalDate>)call("holdingDates",new Class[]{List.class,LocalDate.class},calendar,day);
        if(!hold.getFirst().equals(LocalDate.parse("2021-04-01"))||!hold.getLast().equals(LocalDate.parse("2021-04-30"))||hold.size()!=21)throw new AssertionError("March target: Apr1-Apr30, next session +20 intervals");
        @SuppressWarnings("unchecked") var second=(List<LocalDate>)call("holdingDates",new Class[]{List.class,LocalDate.class},calendar,LocalDate.parse("2021-04-30"));
        if(!second.getFirst().equals(LocalDate.parse("2021-05-03"))||!second.getLast().equals(LocalDate.parse("2021-06-01"))||second.size()!=21)throw new AssertionError("April target: May3-Jun1, all20 intervals precede June7 exposed source calendar");
        var wrongHoliday=new ArrayList<>(calendar);wrongHoliday.add(LocalDate.parse("2021-04-02"));fails("holdingDates",new Class[]{List.class,LocalDate.class},wrongHoliday,day);
        fails("holdingDates",new Class[]{List.class,LocalDate.class},calendar.subList(0,21),day);
        var training=new ArrayList<StockBacktestResult>();
        for(String date:List.of("2020-12-24","2020-12-31","2021-01-08","2021-01-15","2021-01-22","2021-01-29","2021-02-03","2021-02-05","2021-02-10","2021-02-12","2021-02-19","2021-02-26"))for(String s:symbols){var r=new StockBacktestResult();r.setSymbol(s);r.setSignalDate(LocalDate.parse(date));r.setExitTradeDate(LocalDate.parse(date).plusDays(29));r.setHorizonDays(20);r.setForwardReturnPct(BigDecimal.ONE);r.setBenchmarkReturnPct(BigDecimal.ZERO);training.add(r);}
        call("validateTraining",new Class[]{List.class,List.class},training,symbols);
        training.getFirst().setExitTradeDate(day);fails("validateTraining",new Class[]{List.class,List.class},training,symbols);
        training.getFirst().setExitTradeDate(LocalDate.parse("2021-01-22"));training.removeLast();fails("validateTraining",new Class[]{List.class,List.class},training,symbols);
        // Real production models on artificial labels, all30members: detects hardcoded101,
        // escaped memory writes, or target labels reaching expected-return calculations.
        training.clear();
        for(int d=0;d<12;d++)for(String symbol:symbols){var r=row(symbol,LocalDate.parse("2020-01-01").plusDays(d*27L));r.setForwardReturnPct(BigDecimal.valueOf(d-5));training.add(r);}
        var target=new ArrayList<StockBacktestResult>();var markets=new ArrayList<StockMarketSnapshot>();for(String symbol:symbols){target.add(row(symbol,day));var m=new StockMarketSnapshot();m.setIndexCode("DOW30_STRICT_UNUSED_2021_RESEARCH_20261031");m.setSymbol(symbol);m.setSnapshotDate(day);m.setMarketCapUsd(BigDecimal.valueOf(1000000000));m.setSharesSource("SEC_XBRL_SYNTHETIC_TEST");markets.add(m);}
        var benchmark=new ArrayList<StockBenchmarkReturn>();for(int d=0;d<520;d++){var b=new StockBenchmarkReturn();b.setIndexCode("DOW30_STRICT_UNUSED_2021_RESEARCH_20261031");b.setTradeDate(LocalDate.parse("2020-01-01").plusDays(d));b.setBenchmarkLevel(BigDecimal.valueOf(100));b.setReturnPct(BigDecimal.ZERO);b.setSource("SYNTHETIC_FIXTURE");benchmark.add(b);}
        var types=new Class[]{LocalDate.class,List.class,List.class,List.class,List.class,List.class,List.class,int.class,boolean.class};
        @SuppressWarnings("unchecked") var base=(List<StockExpectedReturnSnapshot>)call("models",types,day,target,training,List.of(),benchmark,List.of(),markets,30,true);
        if(base.size()!=240)throw new AssertionError("8 real models x30 targets expected");
        for(var r:target)r.setForwardReturnPct(BigDecimal.valueOf(-99));
        @SuppressWarnings("unchecked") var perturbed=(List<StockExpectedReturnSnapshot>)call("models",types,day,target,training,List.of(),benchmark,List.of(),markets,30,true);
        if(!call("normalized",new Class[]{List.class},base).equals(call("normalized",new Class[]{List.class},perturbed)))throw new AssertionError("Future target labels changed predictions");
        var mapper=new tools.jackson.databind.ObjectMapper();
        var fixture=Files.createTempFile(Path.of("tmp"),"strict-dow-synthetic-shares-",".json");
        Files.writeString(fixture,"{\"facts\":{\"dei\":{\"EntityCommonStockSharesOutstanding\":{\"units\":{\"shares\":[{\"val\":1200,\"filed\":\"2020-10-10\",\"end\":\"2020-09-30\",\"accn\":\"synthetic\"}]}}}}}");
        var evidence=mapper.createObjectNode();evidence.put("sourcePath",fixture.toString());evidence.put("sourceSha256",String.valueOf(call("sha",new Class[]{Path.class},fixture)));evidence.put("totalEconomicCommonShares",1200);evidence.put("filedDate","2020-10-10");evidence.put("measurementDate","2020-09-30");evidence.put("accessionNumber","synthetic");
        call("verifySupplement",new Class[]{tools.jackson.databind.JsonNode.class},evidence);
        evidence.put("totalEconomicCommonShares",1201);fails("verifySupplement",new Class[]{tools.jackson.databind.JsonNode.class},evidence);
        evidence.put("totalEconomicCommonShares",1200);evidence.put("sourceSha256","wrong");fails("verifySupplement",new Class[]{tools.jackson.databind.JsonNode.class},evidence);
        System.out.println("STRICT_DOW_2021_CONTRACT_TEST_PASS 10 boundary checks +240 real model predictions/invariance +3 original-share provenance checks; synthetic data only");
    }
    static StockBacktestResult row(String symbol,LocalDate day)throws Exception{var r=new StockBacktestResult();r.setSymbol(symbol);r.setSignalDate(day);r.setEntryTradeDate(day.plusDays(1));r.setExitTradeDate(day.plusDays(21));r.setEntryClose(BigDecimal.valueOf(100));r.setExitClose(BigDecimal.valueOf(105));r.setForwardReturnPct(BigDecimal.valueOf(5));r.setBenchmarkReturnPct(BigDecimal.ZERO);r.setHorizonDays(20);r.setIntegratedScore(50);r.setDataQualityScore(80);r.setSector("SYNTHETIC");r.setMarketCap(BigDecimal.valueOf(1000));for(String factor:List.of("Valuation","Quality","Growth","Stability","Earnings","Analyst","News","Momentum","Risk","Institution"))StockBacktestResult.class.getMethod("set"+factor+"Score",Integer.class).invoke(r,50);return r;}
}
