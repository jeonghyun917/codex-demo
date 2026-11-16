import com.kingyurina.demo.stock.*;
import tools.jackson.databind.JsonNode;
import java.lang.reflect.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/** Synthetic, database-free safety tests. A missing boundary must fail closed. */
public final class QuantEpsRecoveredTrainingReplayV2Test {
    static Class<?> subject;
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static Object call(String name,Class<?>[] types,Object...args)throws Exception{
        var method=subject.getDeclaredMethod(name,types);method.setAccessible(true);
        try{return method.invoke(null,args);}catch(InvocationTargetException e){throw (Exception)e.getCause();}
    }
    static void denied(JsonNode input)throws Exception{
        boolean denied=false;try{call("validateBoundary",new Class[]{JsonNode.class},input);}catch(IllegalStateException e){denied=true;}
        check(denied,"Unsafe input must be rejected");
    }
    static StockBacktestResult row(String symbol,LocalDate date)throws Exception{
        var r=new StockBacktestResult();r.setSymbol(symbol);r.setSignalDate(date);r.setEntryTradeDate(date.plusDays(1));r.setExitTradeDate(date.plusDays(21));
        r.setEntryClose(BigDecimal.valueOf(100));r.setExitClose(BigDecimal.valueOf(105));r.setForwardReturnPct(BigDecimal.valueOf(5));r.setBenchmarkReturnPct(BigDecimal.ZERO);r.setHorizonDays(20);r.setIntegratedScore(50);r.setDataQualityScore(80);r.setSector("SYNTHETIC");r.setMarketCap(BigDecimal.valueOf(1000));
        for(String factor:List.of("Valuation","Quality","Growth","Stability","Earnings","Analyst","News","Momentum","Risk","Institution"))StockBacktestResult.class.getMethod("set"+factor+"Score",Integer.class).invoke(r,50);
        return r;
    }
    public static void main(String[] args)throws Exception{
        try{subject=Class.forName("QuantEpsRecoveredTrainingReplayV2");}catch(ClassNotFoundException e){throw new AssertionError("Training-only safety boundary not implemented",e);}
        var json=QuantStrictDowReplay.json;
        var input=json.createObjectNode();input.put("namespace","DOW30_EPS_RECOVERED_TRAINING_ONLY_RESEARCH_20261101");input.put("fixedCutoff","2020-09-30");input.putArray("testDecisions");
        call("validateBoundary",new Class[]{JsonNode.class},input);
        var bad=input.deepCopy();bad.withArray("testDecisions").add("2020-09-30");denied(bad);
        bad=input.deepCopy();bad.put("fixedCutoff","2020-10-01");denied(bad);
        bad=input.deepCopy();bad.putArray("targetLabels").add(5);denied(bad);
        bad=input.deepCopy();bad.putObject("pricesBySymbol").putArray("AAPL").addObject().put("day","2020-09-30").put("close",100);denied(bad);
        bad=input.deepCopy();bad.putArray("benchmark").addObject().put("day","2020-09-30").put("level",100);denied(bad);
        var hashes=json.readTree("{\"source\":\"\"}");boolean missing=false;try{call("requireSourceHashes",new Class[]{JsonNode.class},hashes);}catch(IllegalStateException e){missing=true;}check(missing,"Missing source checksum rejected");
        var d=LocalDate.parse("2020-08-28");var r=row("S",d.minusDays(30));r.setExitTradeDate(d);
        @SuppressWarnings("unchecked") var eligible=(List<StockBacktestResult>)call("matureBefore",new Class[]{List.class,LocalDate.class},List.of(r),d);check(eligible.isEmpty(),"Same-day exits are not mature");
        var training=new ArrayList<StockBacktestResult>();var current=new ArrayList<StockBacktestResult>();var markets=new ArrayList<StockMarketSnapshot>();var benchmark=new ArrayList<StockBenchmarkReturn>();
        var roster=new ArrayList<>(QuantStrictDowReplay.ROSTER);Collections.sort(roster);
        for(int i=0;i<12;i++)for(String symbol:roster)training.add(row(symbol,LocalDate.parse("2018-01-02").plusDays(i*25L)));
        for(String symbol:roster){current.add(row(symbol,d));var market=new StockMarketSnapshot();market.setIndexCode("DOW30_EPS_RECOVERED_TRAINING_ONLY_RESEARCH_20261101");market.setSymbol(symbol);market.setSnapshotDate(d);market.setMarketCapUsd(BigDecimal.valueOf(1000000000));market.setSharesSource("SEC_SYNTHETIC");markets.add(market);}
        for(var date=LocalDate.parse("2018-01-02");!date.isAfter(d.plusDays(35));date=date.plusDays(1)){var b=new StockBenchmarkReturn();b.setIndexCode("DOW30_EPS_RECOVERED_TRAINING_ONLY_RESEARCH_20261101");b.setTradeDate(date);b.setBenchmarkLevel(BigDecimal.valueOf(100));b.setReturnPct(BigDecimal.ZERO);b.setSource("SYNTHETIC");benchmark.add(b);}
        var types=new Class[]{LocalDate.class,List.class,List.class,List.class,List.class,List.class,List.class};
        @SuppressWarnings("unchecked") var baseline=(List<StockExpectedReturnSnapshot>)call("predictTrainingDay",types,d,current,training,List.of(),benchmark,List.of(),markets);
        check(baseline.size()==240,"Eight actual model versions x30 training decision rows");
        for(var target:current){target.setForwardReturnPct(BigDecimal.valueOf(-95));target.setBenchmarkReturnPct(BigDecimal.valueOf(80));target.setEntryClose(BigDecimal.ONE);target.setExitClose(BigDecimal.valueOf(900));}
        var future=row("FUTURE",d.plusDays(1));future.setForwardReturnPct(BigDecimal.valueOf(900));training.add(future);
        for(var b:benchmark)if(!b.getTradeDate().isBefore(d))b.setReturnPct(BigDecimal.valueOf(99));
        @SuppressWarnings("unchecked") var changed=(List<StockExpectedReturnSnapshot>)call("predictTrainingDay",types,d,current,training,List.of(),benchmark,List.of(),markets);
        check(QuantStrictDowReplay.normalized(baseline).equals(QuantStrictDowReplay.normalized(changed)),"Current/future labels and future benchmark increments cannot affect predictions");
        System.out.println("EPS_TRAINING_REPLAY_SYNTHETIC_PASS boundaries/maturity/sourcehash/240actualmodels/futureinvariance");
    }
}
