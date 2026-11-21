import com.kingyurina.demo.stock.StockBacktestResult;
import com.kingyurina.demo.stock.StockCandleDaily;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

public final class QuantNewPeriodReplayTest {
    public static void main(String[] args){
        var cutoff=LocalDate.parse("2026-04-30");var r=new StockBacktestResult();r.setSignalDate(LocalDate.parse("2026-03-27"));r.setExitTradeDate(cutoff);
        if(QuantNewPeriodReplay.mature(r,cutoff))throw new AssertionError("Same-day exit is not mature before fixed cutoff");
        r.setExitTradeDate(cutoff.minusDays(1));if(!QuantNewPeriodReplay.mature(r,cutoff))throw new AssertionError("Strict prior matured outcome is usable");
        r.setSignalDate(cutoff);if(QuantNewPeriodReplay.mature(r,cutoff))throw new AssertionError("Target signal cannot train itself");
        var calendar=new ArrayList<LocalDate>();for(int i=0;i<26;i++)calendar.add(cutoff.plusDays(i));
        var hold=QuantNewPeriodReplay.holdingDates(calendar,cutoff);
        if(hold.size()!=21||!hold.getFirst().equals(LocalDate.parse("2026-05-01"))||!hold.getLast().equals(LocalDate.parse("2026-05-21")))throw new AssertionError("Entry next session close plus20 intervals");
        boolean missing=false;try{QuantNewPeriodReplay.holdingDates(calendar.subList(0,8),cutoff);}catch(IllegalArgumentException e){missing=true;}if(!missing)throw new AssertionError("Incomplete horizon rejected");
        var first=new StockCandleDaily();first.setSymbol("INSM");first.setTradeDate(LocalDate.parse("2026-06-01"));first.setClosePrice(BigDecimal.TEN);
        var extra=new StockCandleDaily();extra.setSymbol("INSM");extra.setTradeDate(LocalDate.parse("2026-06-02"));extra.setClosePrice(BigDecimal.valueOf(12));
        var merged=QuantNewPeriodReplay.mergePrices(List.of(first),List.of(first,extra));if(merged.size()!=2||merged.getFirst()!=first)throw new AssertionError("Merge fills only missing dates and preserves canonical objects");
        var conflict=new StockCandleDaily();conflict.setSymbol("INSM");conflict.setTradeDate(first.getTradeDate());conflict.setClosePrice(BigDecimal.valueOf(11));boolean mismatch=false;try{QuantNewPeriodReplay.mergePrices(List.of(first),List.of(conflict,extra));}catch(IllegalStateException e){mismatch=true;}if(!mismatch)throw new AssertionError("Conflicting price bases rejected");
        boolean duplicate=false;try{QuantNewPeriodReplay.mergePrices(List.of(first),List.of(extra,extra));}catch(IllegalStateException e){duplicate=true;}if(!duplicate)throw new AssertionError("Duplicate free source date rejected");
        System.out.println("NEW_PERIOD_BOUNDARY_TEST_PASS 8 cases");
    }
}
