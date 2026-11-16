import com.kingyurina.demo.stock.StockExpectedReturnSnapshot;
import java.math.BigDecimal;
import java.time.LocalDate;

public final class QuantReturnBlendForwardReadTest {
    static StockExpectedReturnSnapshot fixture(){
        var p=new StockExpectedReturnSnapshot();p.setIndexCode("DOW30");p.setSignalDate(LocalDate.parse("2026-10-31"));
        p.setSymbol("AAPL");p.setHorizonDays(20);p.setModelVersion("EXPECTED_RETURN_V5_CORRECTED_20261017");
        p.setSource("LIVE_REGIME_NONLINEAR_INTERACTION_EXPECTED_RETURN_V5_CUTOFF_LT_20261031");
        p.setExpectedReturnPct(BigDecimal.ONE);p.setExpectedExcessReturnPct(BigDecimal.ZERO);return p;
    }
    static void rejected(Runnable call){try{call.run();throw new AssertionError("Invalid upstream admitted");}catch(IllegalArgumentException expected){}}
    public static void main(String[] args){
        var day=LocalDate.parse("2026-10-31");QuantReturnBlendForwardRead.validateUpstream(fixture(),"DOW30",day);
        var wrong=fixture();wrong.setSource("RESEARCH_BACKFILL");rejected(()->QuantReturnBlendForwardRead.validateUpstream(wrong,"DOW30",day));
        var wrongDay=fixture();wrongDay.setSignalDate(day.plusDays(1));rejected(()->QuantReturnBlendForwardRead.validateUpstream(wrongDay,"DOW30",day));
        var wrongHorizon=fixture();wrongHorizon.setHorizonDays(5);rejected(()->QuantReturnBlendForwardRead.validateUpstream(wrongHorizon,"DOW30",day));
        var missing=fixture();missing.setExpectedExcessReturnPct(null);rejected(()->QuantReturnBlendForwardRead.validateUpstream(missing,"DOW30",day));
        var wrongCutoff=fixture();wrongCutoff.setSource("LIVE_V5_CUTOFF_LT_20261101");rejected(()->QuantReturnBlendForwardRead.validateUpstream(wrongCutoff,"DOW30",day));
        var fabricated=fixture();fabricated.setSource("LIVE_UNKNOWN_CUTOFF_LT_20261031");rejected(()->QuantReturnBlendForwardRead.validateUpstream(fabricated,"DOW30",day));
        System.out.println("Forward read boundary tests PASS (7 cases)");
    }
}
