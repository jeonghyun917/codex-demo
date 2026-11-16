import com.kingyurina.demo.CodexDemoApplication;
import com.kingyurina.demo.stock.*;
import org.springframework.boot.*;
import tools.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

/** Read-only freeze of available prior-filed database financials; never a historical receipt claim. */
public final class QuantMarchInputInventory {
    public static void main(String[] args)throws Exception{
        var path=Path.of("tmp/quant-march-inputs-20261031/native-inputs-v2.json");
        if(Files.exists(path))throw new IllegalStateException("Refuse overwrite");Files.createDirectories(path.getParent());
        var app=new SpringApplication(CodexDemoApplication.class);app.setWebApplicationType(WebApplicationType.NONE);
        try(var ctx=app.run("--spring.profiles.active=mariadb","--spring.sql.init.mode=never","--app.signal.refresh.enabled=false","--app.signal.refresh.run-on-startup=false")){
            var json=ctx.getBean(ObjectMapper.class);var mapper=ctx.getBean(StockBacktestMapper.class);var seed=ctx.getBean(HistoricalBacktestSeedMapper.class);
            var day=LocalDate.parse("2026-03-13");var cutoff=day.minusDays(1);var financials=new TreeMap<String,List<SecFinancialStandard>>();
            var symbols=List.of("ALNY","ARM","ASML","CCEP","FER","INSM","MELI","MRVL","MSTR","PDD","SHOP","TEAM","TRI","ZS");
            for(String symbol:symbols){
                var rows=new ArrayList<SecFinancialStandard>();
                for(boolean annual:List.of(true,false)){
                    var f=annual?seed.findLatestAnnualAsOf(symbol,cutoff):seed.findLatestQuarterAsOf(symbol,cutoff);int accepted=0;
                    while(f!=null&&accepted<2){
                        long days=f.getStartDate()==null?0:java.time.temporal.ChronoUnit.DAYS.between(f.getStartDate(),f.getEndDate())+1;
                        if(annual?days>=330&&days<=380:days>=70&&days<=110){rows.add(f);accepted++;}
                        else System.out.println("REJECT_INVALID_PERIOD "+symbol+" "+f.getFiscalPeriod()+" "+f.getEndDate()+" days="+days);
                        f=annual?seed.findPreviousAnnualAsOf(symbol,f.getEndDate(),cutoff):seed.findPreviousQuarterAsOf(symbol,f.getEndDate(),cutoff);
                    }
                }
                if(rows.stream().anyMatch(f->f.getFiledAt()==null||!f.getFiledAt().isBefore(day)||!f.getEndDate().isBefore(day)))throw new IllegalStateException("Noncausal financials");
                financials.put(symbol,rows);System.out.println("FINANCIAL_INVENTORY "+symbol+" rows="+rows.size());
            }
            var members=mapper.findEvaluationMembershipSnapshots("NASDAQ100_MACRO_FREE_20261018",day,day).stream().filter(m->Boolean.TRUE.equals(m.getMember())).map(StockIndexMembershipSnapshot::getSymbol).collect(Collectors.toCollection(TreeSet::new));
            var outcomes=mapper.findResults("NASDAQ100_FREE_20261018",200000).stream().filter(r->day.equals(r.getSignalDate())&&Integer.valueOf(20).equals(r.getHorizonDays())&&members.contains(r.getSymbol())).toList();
            var missing=new TreeSet<>(members);outcomes.forEach(r->missing.remove(r.getSymbol()));
            if(members.size()!=101||outcomes.size()!=87||!missing.equals(new TreeSet<>(symbols)))throw new IllegalStateException("Inventory changed: "+missing);
            var market=mapper.findMarketSnapshots("NASDAQ100_FREE_20261018",day,day);
            var output=Map.of("classification","CURRENT_DB_EXPORT_PRIOR_FILED_RECONSTRUCTION_NOT_PIT","databaseWrites",0,"promotionAllowed",false,"decision",day,
                "financialsBySymbol",financials,"population",members,"existingOutcomes",outcomes,"nativeMarketInputs",market,"exportedAt",OffsetDateTime.now().toString());
            Files.writeString(path,json.writerWithDefaultPrettyPrinter().writeValueAsString(output));
            System.out.println("INVENTORY full="+members.size()+" outcomes="+outcomes.size()+" nativeMarkets="+market.size());
        }
    }
}
