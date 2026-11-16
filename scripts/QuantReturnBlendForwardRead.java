import com.kingyurina.demo.CodexDemoApplication;
import com.kingyurina.demo.stock.*;
import org.springframework.boot.*;
import org.springframework.jdbc.core.JdbcTemplate;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/** Read existing live V5 outputs only; never refresh, persist or evaluate a model. */
public final class QuantReturnBlendForwardRead {
    static void validateUpstream(StockExpectedReturnSnapshot p,String index,LocalDate day){
        if(!index.equals(p.getIndexCode())||!day.equals(p.getSignalDate())||!Integer.valueOf(20).equals(p.getHorizonDays())
            ||!"EXPECTED_RETURN_V5_CORRECTED_20261017".equals(p.getModelVersion())||p.getSymbol()==null
            ||!("LIVE_REGIME_NONLINEAR_INTERACTION_EXPECTED_RETURN_V5_CUTOFF_LT_"+day.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE)).equals(p.getSource())
            ||p.getExpectedReturnPct()==null||p.getExpectedExcessReturnPct()==null){
            throw new IllegalArgumentException("Only finite matching LIVE prior-cutoff V5 outputs qualify: "
                +p.getIndexCode()+"/"+p.getSignalDate()+"/"+p.getSymbol()+"/"+p.getHorizonDays()+"/"+p.getModelVersion()
                +" source="+p.getSource()+" finiteHeads="+(p.getExpectedReturnPct()!=null&&p.getExpectedExcessReturnPct()!=null));
        }
    }
    public static void main(String[] args)throws Exception{
        if(args.length!=3)throw new IllegalArgumentException("COHORT_FILE EXPECTED_SHA NEW_OUTPUT_JSON required");
        Path input=Path.of(args[0]),output=Path.of(args[2]);
        if(Files.exists(output)||!QuantStrictDowReplay.sha(input).equals(args[1]))throw new IllegalArgumentException("Immutable cohort/output required");
        var json=QuantStrictDowReplay.json;var frozen=QuantStrictDowReplay.read(input);
        if(frozen.path("cohorts").size()!=1)throw new IllegalArgumentException("Exactly one frozen source cohort required");
        var cohort=frozen.path("cohorts").get(0);String index=cohort.path("indexCode").asText();
        LocalDate day=LocalDate.parse(cohort.path("originalSignalDate").asText());
        Instant entry=LocalDate.parse(cohort.path("entrySession").asText()).atTime(16,0).atZone(ZoneId.of("America/New_York")).toInstant();
        if(!index.equals("DOW30")||!Instant.now().isBefore(entry))throw new IllegalArgumentException("DOW30 read must precede original entry close");
        var archive=cohort.path("archiveInput");
        if(!QuantStrictDowReplay.sha(Path.of(archive.path("path").asText())).equals(archive.path("sha256").asText()))throw new IllegalArgumentException("Original live archive changed");
        var names=new TreeSet<String>();for(var p:cohort.path("predictions"))if(!names.add(p.path("symbol").asText()))throw new IllegalArgumentException("Duplicate frozen name");
        if(names.size()!=30)throw new IllegalArgumentException("Whole frozen DOW30 cohort required");
        var app=new SpringApplication(CodexDemoApplication.class);app.setWebApplicationType(WebApplicationType.NONE);
        try(var ctx=app.run("--spring.profiles.active=mariadb","--spring.sql.init.mode=never","--spring.datasource.hikari.read-only=true",
                "--spring.datasource.hikari.connection-init-sql=SET SESSION TRANSACTION READ ONLY","--app.signal.refresh.enabled=false",
                "--app.signal.refresh.run-on-startup=false","--spring.task.scheduling.enabled=false")){
            var db=ctx.getBean(JdbcTemplate.class);
            if(db.queryForObject("SELECT @@tx_read_only",Integer.class)!=1)throw new IllegalStateException("Read-only session required");
            var before=QuantStrictDowReplay.databaseState(db);var selected=new ArrayList<StockExpectedReturnSnapshot>();var seen=new TreeSet<String>();
            for(var p:ctx.getBean(StockBacktestMapper.class).findExpectedReturnSnapshots(index,day,day)){
                if(!"EXPECTED_RETURN_V5_CORRECTED_20261017".equals(p.getModelVersion())||!Integer.valueOf(20).equals(p.getHorizonDays()))continue;
                validateUpstream(p,index,day);
                if(!seen.add(p.getSymbol()))throw new IllegalStateException("Duplicate live upstream");selected.add(p);
            }
            if(!seen.equals(names))throw new IllegalStateException("Do not publish partial upstream coverage: "+seen.size()+"/"+names.size());
            var after=QuantStrictDowReplay.databaseState(db);if(!before.equals(after))throw new IllegalStateException("DB drift");
            var result=new LinkedHashMap<String,Object>();result.put("classification","READ_ONLY_EXISTING_LIVE_V5_OUTPUTS_NO_FUTURE_LABELS");
            result.put("capturedAtUtc",Instant.now().toString());result.put("sourceCohortPath",input.toString());result.put("sourceCohortSha256",args[1]);
            result.put("indexCode",index);result.put("originalSignalDate",day.toString());result.put("entryCloseUtc",entry.toString());
            result.put("upstreamV5",selected);result.put("databaseBefore",before);result.put("databaseAfter",after);result.put("databaseWrites",0);
            Files.createDirectories(output.toAbsolutePath().getParent());
            try(var stream=Files.newOutputStream(output,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)){json.writerWithDefaultPrettyPrinter().writeValue(stream,result);}
            System.out.println("READ_ONLY_FORWARD_UPSTREAM_CAPTURED names="+selected.size()+" writes=0 noOutcomes");
        }
    }
}
