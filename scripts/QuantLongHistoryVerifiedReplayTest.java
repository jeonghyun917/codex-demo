import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

public class QuantLongHistoryVerifiedReplayTest {
    static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
    public static void main(String[]args)throws Exception{
        var json=QuantStrictDowReplay.json;
        var rows=json.readTree("[{\"symbol\":\"V\",\"filedDate\":\"2020-07-31\",\"measurementDate\":\"2020-06-30\",\"quotedUnitEquivalentShares\":2203000000,\"reportedPrecisionShares\":1000000,\"completeEconomicCoverage\":true,\"notWeightedAverage\":true,\"coverageBasis\":\"ISSUER_REPORTED_AS_CONVERTED_CLASS_A_TOTAL\"}]");
        boolean denied=false;try{QuantLongHistoryVerifiedReplay.selectVerifiedShares("V",rows,LocalDate.parse("2020-07-31"));}catch(IllegalStateException e){denied=true;}check(denied,"Same-day filing cannot be used");
        var chosen=QuantLongHistoryVerifiedReplay.selectVerifiedShares("V",rows,LocalDate.parse("2020-09-30"));check(chosen.path("quotedUnitEquivalentShares").asLong()==2203000000L,"Actual issuer-reported whole-economic-equivalent total, not ClassA only");
        var unsafe=json.readTree("[{\"symbol\":\"V\",\"filedDate\":\"2010-02-03\",\"measurementDate\":\"2010-01-27\",\"quotedUnitEquivalentShares\":469280842}]");
        denied=false;try{QuantLongHistoryVerifiedReplay.selectVerifiedShares("V",unsafe,LocalDate.parse("2020-09-30"));}catch(IllegalStateException e){denied=true;}check(denied,"Positive ClassA-only old value must fail closed");
        var split=json.readTree("[{\"day\":\"2015-12-24\",\"numerator\":2,\"denominator\":1,\"kind\":\"SPLIT\"}]");
        check(QuantLongHistoryVerifiedReplay.sharesAsOf(new BigDecimal("855351589"),LocalDate.parse("2015-07-23"),LocalDate.parse("2015-07-17"),split,LocalDate.parse("2017-01-31")).compareTo(new BigDecimal("1710703178"))==0,"Split before price window still aligns older shares");
        System.out.println("VERIFIED_SHARE_BOUNDARIES_PASS strictfiling/fullissuer/precision/prewindow_split");
    }
}
