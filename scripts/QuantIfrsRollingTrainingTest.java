import com.kingyurina.demo.stock.*;
import java.time.LocalDate;
import java.util.*;

public final class QuantIfrsRollingTrainingTest {
    static StockBacktestResult row(int score,String exit){var r=new StockBacktestResult();r.setSymbol("A");r.setSignalDate(LocalDate.parse("2025-01-01"));r.setHorizonDays(20);r.setExitTradeDate(LocalDate.parse(exit));r.setIntegratedScore(score);return r;}
    public static void main(String[] args)throws Exception{
        var original=row(50,"2025-02-03");var repaired=row(70,"2025-02-03");
        if(!QuantIfrsRollingTraining.sourceAt(List.of(original),List.of(repaired),LocalDate.parse("2025-02-03")).isEmpty())throw new AssertionError("Same-day close outcome leaked into training");
        var next=QuantIfrsRollingTraining.sourceAt(List.of(original),List.of(repaired),LocalDate.parse("2025-02-04"));
        if(next.size()!=1||next.getFirst().getIntegratedScore()!=70||original.getIntegratedScore()!=50)throw new AssertionError("Repair must replace same key without mutating frozen source");
        var extra=row(60,"2025-02-03");extra.setSymbol("B");
        if(QuantIfrsRollingTraining.sourceAt(List.of(original),List.of(repaired,extra),LocalDate.parse("2025-02-04")).size()!=2)throw new AssertionError("Recovered missing member lost");
        try{QuantIfrsRollingTraining.sourceAt(List.of(original,original),List.of(),LocalDate.parse("2025-02-04"));throw new AssertionError("Duplicate training key accepted");}catch(IllegalStateException expected){}
        var changed=row(50,"2025-02-03");changed.setEarningsScore(70);
        if(!QuantIfrsRollingTraining.changedScores(original,changed))throw new AssertionError("Same integrated score can conceal a changed financial factor");
        System.out.println("PASS rolling training maturity, replacement, recovered member, source immutability and duplicate guards");
    }
}
