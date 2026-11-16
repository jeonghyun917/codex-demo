import com.kingyurina.demo.CodexDemoApplication;
import com.kingyurina.demo.stock.*;
import org.springframework.boot.*;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;

/** Independent actual MyBatis as-of reads versus source-verified native replay statements. */
public final class QuantIfrsDbLookupCheck {
    static void same(SecFinancialStandard actual,SecFinancialStandard expected)throws Exception {
        QuantRestandardizedReplay.require((actual==null)==(expected==null),"DB/source missingness mismatch");
        if(actual==null)return;
        var left=QuantHistoricalUsAppend.columns(actual,FinancialRestandardizationSafety.COLUMNS);
        var right=QuantHistoricalUsAppend.columns(expected,FinancialRestandardizationSafety.COLUMNS);
        for(String column:FinancialRestandardizationSafety.COLUMNS){
            if(Set.of("id","mapped_at").contains(column))continue;
            String a=left.get(column),b=right.get(column);
            boolean equal=FinancialRestandardizationSafety.VALUES.contains(column)&&a!=null&&b!=null
                    ? new BigDecimal(a).compareTo(new BigDecimal(b))==0:Objects.equals(a,b);
            QuantRestandardizedReplay.require(equal,"Actual DB financial field mismatch: "+column);
        }
    }
    public static void main(String[] args)throws Exception{
        if(args.length!=2)throw new IllegalArgumentException("Expected replay-directory NEW-RECEIPT");
        Path replay=Path.of(args[0]),receipt=Path.of(args[1]);if(Files.exists(receipt))throw new IllegalStateException("Refuse overwrite");
        var json=new ObjectMapper();var nativeRows=json.readTree(Files.readString(replay.resolve("native-standards.json")));var report=json.readTree(Files.readString(replay.resolve("result.json")));
        QuantMatchedFinancialReplay.json=json;QuantMatchedFinancialReplay.validPeriod=Class.forName("com.kingyurina.demo.stock.FinancialInputPolicy").getDeclaredMethod("validPeriod",SecFinancialStandard.class,boolean.class);QuantMatchedFinancialReplay.validPeriod.setAccessible(true);
        var app=new SpringApplication(CodexDemoApplication.class);app.setWebApplicationType(WebApplicationType.NONE);
        try(var ctx=app.run("--spring.profiles.active=mariadb","--spring.sql.init.mode=never","--spring.datasource.hikari.read-only=true","--spring.datasource.hikari.connection-init-sql=SET SESSION TRANSACTION READ ONLY","--app.signal.refresh.enabled=false","--app.signal.refresh.run-on-startup=false")){
            var jdbc=ctx.getBean(JdbcTemplate.class);QuantRestandardizedReplay.require(jdbc.queryForObject("SELECT @@tx_read_only",Integer.class)==1,"Not read-only");var before=QuantRestandardizedReplay.databaseState(jdbc);
            var mapper=ctx.getBean(HistoricalBacktestSeedMapper.class);int reads=0,present=0;var audit=new ArrayList<Object>();
            for(var date:report.path("dates")){LocalDate day=LocalDate.parse(date.path("decision").asText());
                for(var symbol:date.path("affectedSymbols")){
                    String s=symbol.asText();var rows=QuantMatchedFinancialReplay.list(nativeRows.path(s),SecFinancialStandard.class);
                    for(boolean annual:List.of(true,false)){
                        var expected=QuantMatchedFinancialReplay.select(rows,annual,null,day);
                        var actual=annual?mapper.findLatestAnnualAsOf(s,day):mapper.findLatestQuarterAsOf(s,day);same(actual,expected);reads++;if(actual!=null)present++;
                        var prior=expected==null?null:QuantMatchedFinancialReplay.select(rows,annual,expected.getEndDate(),day);
                        var dbPrior=actual==null?null:annual?mapper.findPreviousAnnualAsOf(s,actual.getEndDate(),day):mapper.findPreviousQuarterAsOf(s,actual.getEndDate(),day);same(dbPrior,prior);reads++;if(dbPrior!=null)present++;
                        audit.add(Map.of("decision",day,"symbol",s,"annual",annual,"currentAccession",actual==null?"MISSING":actual.getAccessionNumber(),"previousAccession",dbPrior==null?"MISSING":dbPrior.getAccessionNumber()));
                    }
                }
            }
            var after=QuantRestandardizedReplay.databaseState(jdbc);QuantRestandardizedReplay.require(before.equals(after),"Database changed");
            Files.writeString(receipt,json.writerWithDefaultPrettyPrinter().writeValueAsString(Map.of("status","PASS","actualAsOfReadsCompared",reads,"presentReads",present,"databaseWrites",0,"sourceDatabaseBefore",before,"sourceDatabaseAfter",after,"reads",audit)),StandardOpenOption.CREATE_NEW);
            System.out.println("DB_IFRS_MODEL_LOOKUP_PASS reads="+reads+" present="+present+" writes=0");
        }
    }
}
