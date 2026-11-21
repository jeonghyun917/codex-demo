import com.kingyurina.demo.stock.SecFinancialStandard;
import java.time.LocalDate;
public class QuantWbaSourcePrepareTest {
    static SecFinancialStandard row(int year,String fp,String end){var r=new SecFinancialStandard();r.setFiscalYear(year);r.setFiscalPeriod(fp);r.setEndDate(LocalDate.parse(end));return r;}
    public static void main(String[] args){
        if(!QuantWbaSourcePrepare.fiscalValid(row(2022,"FY","2022-08-31")))throw new AssertionError("FY anchor must be accepted");
        if(QuantWbaSourcePrepare.fiscalValid(row(2023,"FY","2022-08-31")))throw new AssertionError("Comparative mislabeled by filing year must fail");
        if(!QuantWbaSourcePrepare.fiscalValid(row(2022,"Q1","2021-11-30")))throw new AssertionError("November belongs next fiscal year");
        if(QuantWbaSourcePrepare.fiscalValid(row(2021,"Q1","2021-11-30")))throw new AssertionError("Calendar FY substitution must fail");
        System.out.println("WBA_FISCAL_BOUNDARY_TEST_PASS 4 cases");
    }
}
