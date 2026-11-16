import com.kingyurina.demo.stock.SecFinancialStandard;
import java.time.LocalDate;
import java.util.*;

public final class QuantPrepanelFinancialExpansionTest {
    static SecFinancialStandard row(String filed,String end,String accession){
        var r=new SecFinancialStandard();r.setSymbol("GFS");r.setCik("1709048");r.setFiscalYear(2023);r.setFiscalPeriod("FY");r.setForm("20-F");r.setFiledAt(LocalDate.parse(filed));r.setStartDate(LocalDate.parse("2023-01-01"));r.setEndDate(LocalDate.parse(end));r.setAccessionNumber(accession);return r;
    }
    public static void main(String[] args){
        var cutoff=LocalDate.parse("2024-07-31");var old=row("2024-03-15","2023-12-31","old");var fresh=row("2024-03-20","2023-12-31","new");
        var sameDay=row("2024-07-31","2023-12-31","same-day");var future=row("2025-03-15","2023-12-31","future-restatement");
        var futureEnd=row("2024-04-20","2024-07-31","end");
        var accepted=QuantPrepanelFinancialExpansion.novelPrior(List.of(old,fresh,sameDay,future,futureEnd),Set.of(QuantPrepanelFinancialExpansion.key(old)),cutoff);
        if(accepted.size()!=1||!accepted.getFirst().getAccessionNumber().equals("new"))throw new AssertionError("Must retain only new strictly prior filing and period");
        boolean duplicateRejected=false;try{QuantPrepanelFinancialExpansion.novelPrior(List.of(fresh,fresh),Set.of(),cutoff);}catch(IllegalArgumentException e){duplicateRejected=true;}
        if(!duplicateRejected)throw new AssertionError("Duplicate standard keys must fail instead of inflate additions");
        System.out.println("PREPANEL_FILTER_TEST_PASS 6 boundary cases");
    }
}
