import com.kingyurina.demo.stock.SecCompanyFact;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public final class QuantHistoricalUsAppendTest {
    static SecCompanyFact fact(){
        var f=new SecCompanyFact();f.setSymbol("TEAM");f.setCik("1650372");f.setTaxonomy("us-gaap");
        f.setConcept("Revenues");f.setUnit("USD");f.setFiscalYear(2024);f.setFiscalPeriod("FY");
        f.setForm("10-K");f.setFiledAt(LocalDate.parse("2024-08-16"));f.setStartDate(LocalDate.parse("2023-07-01"));
        f.setEndDate(LocalDate.parse("2024-06-30"));f.setAccessionNumber("0001650372-24-000001");f.setValue(new BigDecimal("100"));
        f.setRawJson("{\"start\":\"2023-07-01\",\"end\":\"2024-06-30\",\"val\":100,\"accn\":\"0001650372-24-000001\",\"fy\":2024,\"fp\":\"FY\",\"form\":\"10-K\",\"filed\":\"2024-08-16\"}");
        f.setFetchedAt(LocalDateTime.now(ZoneOffset.UTC));return f;
    }
    static void reject(Runnable fn){try{fn.run();throw new AssertionError("Expected rejection");}catch(IllegalStateException expected){}}
    public static void main(String[] args)throws Exception{
        var f=fact();QuantHistoricalUsAppend.validateFact(f);
        var wrong=fact();wrong.setCik("999");reject(()->QuantHistoricalUsAppend.validateFact(wrong));
        var changed=fact();changed.setValue(new BigDecimal("99"));reject(()->QuantHistoricalUsAppend.validateFact(changed));
        var rows=QuantHistoricalUsAppend.deduplicate(List.of(fact(),fact()));if(rows.size()!=1)throw new AssertionError("Duplicate keys retained");
        var conflict=fact();conflict.setValue(new BigDecimal("99"));reject(()->QuantHistoricalUsAppend.deduplicate(List.of(fact(),conflict)));
        var different=fact();different.setStartDate(LocalDate.parse("2024-01-01"));
        if(QuantHistoricalUsAppend.factKey(f).equals(QuantHistoricalUsAppend.factKey(different)))throw new AssertionError("Start date omitted");
        var nullable=fact();nullable.setFiscalYear(null);reject(()->QuantHistoricalUsAppend.validateFact(nullable));
        var oldReceipt=fact();oldReceipt.setFetchedAt(LocalDateTime.parse("2024-08-16T00:00:00"));reject(()->QuantHistoricalUsAppend.validateFact(oldReceipt));
        var instant=fact();instant.setConcept("Assets");instant.setStartDate(null);
        instant.setRawJson("{\"end\":\"2024-06-30\",\"val\":100,\"accn\":\"0001650372-24-000001\",\"fy\":2024,\"fp\":\"FY\",\"form\":\"10-K\",\"filed\":\"2024-08-16\"}");
        QuantHistoricalUsAppend.validateFact(instant);
        System.out.println("PASS 8 historical append validation cases");
    }
}
