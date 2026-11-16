import com.kingyurina.demo.CodexDemoApplication;
import com.kingyurina.demo.stock.*;
import org.springframework.boot.*;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import java.lang.reflect.*;
import java.util.*;

/** Source-backed native IFRS input sensitivity. Actual model math, intercepted writes, read-only DB. */
public final class QuantIfrsFinancialReplay {
    static final String BASE="tmp/quant-matched-financial-all20-20261031/result.json";
    static final String CONTEXT="tmp/quant-restandardized-all20-20261031/frozen-context.json";
    static final String INPUT="tmp/quant-restandardized-prepared-v2-20261031/inputs.json";
    static final String QUEUE="tmp/quant-free-history-queue-primary-approved-20261031";
    static ObjectMapper json=new ObjectMapper();
    static void require(boolean ok,String message){QuantRestandardizedReplay.require(ok,message);}
    static <T> List<T> list(JsonNode rows,Class<T> type){var out=new ArrayList<T>();for(var r:rows)out.add(json.treeToValue(r,type));return out;}
    static JsonNode read(String file)throws Exception{return json.readTree(Files.readString(Path.of(file)));}
    static void write(Path file,Object body)throws Exception{Files.writeString(file,json.writerWithDefaultPrettyPrinter().writeValueAsString(body),StandardOpenOption.CREATE_NEW);}
    static Map<String,StockMarketSnapshot> market(JsonNode day,JsonNode input,List<StockMarketSnapshot> all){
        String text=day.path("decision").asText();var result=new TreeMap<String,StockMarketSnapshot>();
        for(var m:all)if(text.equals(m.getSnapshotDate().toString()))result.put(m.getSymbol(),m);
        for(var p:input.path("marketOverrides").path(text).properties())result.put(p.getKey(),json.treeToValue(p.getValue(),StockMarketSnapshot.class));
        return result;
    }
    static List<StockBacktestResult> targets(JsonNode day,List<StockSignalSnapshot> signals,JsonNode input,List<StockBacktestResult> source,Map<String,StockMarketSnapshot> market)throws Exception{
        String text=day.path("decision").asText();var old=new TreeMap<String,StockBacktestResult>();
        for(var r:source)if(text.equals(r.getSignalDate().toString())&&Integer.valueOf(20).equals(r.getHorizonDays()))old.put(r.getSymbol(),r);
        var result=new ArrayList<StockBacktestResult>();
        for(var signal:signals){var pair=day.path("pairs").valueStream().filter(p->signal.getSymbol().equals(p.path("symbol").asText())).findFirst().orElseThrow();
            var entry=new StockCandleDaily();entry.setSymbol(signal.getSymbol());entry.setTradeDate(LocalDate.parse(pair.path("entry").asText()));entry.setClosePrice(new BigDecimal(pair.path("entryClose").asText()));
            var exit=new StockCandleDaily();exit.setSymbol(signal.getSymbol());exit.setTradeDate(LocalDate.parse(pair.path("exit").asText()));exit.setClosePrice(new BigDecimal(pair.path("exitClose").asText()));
            boolean cap=input.path("oldRecoveredTargetCapSymbols").path(text).valueStream().anyMatch(n->signal.getSymbol().equals(n.asText()));
            result.add(QuantRestandardizedReplay.outcome(signal,entry,exit,old.get(signal.getSymbol()),cap?market.get(signal.getSymbol()):null));
        }return result;
    }
    public static void main(String[] args)throws Exception{
        require(args.length==2,"Usage: NEW_OUTPUT_DIR ALL|decision");Path output=Path.of(args[0]);require(!Files.exists(output),"Refuse overwrite");Files.createDirectories(output);
        QuantRestandardizedReplay.json=json;
        var hashes=new TreeMap<String,String>();for(String p:List.of(BASE,CONTEXT,INPUT,QUEUE+"/manifest.json",QUEUE+"/accession-queue.json","scripts/QuantIfrsFinancialReplay.java","scripts/QuantRestandardizedReplay.java","scripts/QuantMatchedFinancialReplay.java"))hashes.put(p,QuantRestandardizedReplay.sha(p));
        for(String p:List.of("src/main/java/com/kingyurina/demo/stock/SecEdgarService.java","src/main/java/com/kingyurina/demo/stock/SecFinancialStandardService.java","src/main/java/com/kingyurina/demo/stock/FinancialInputPolicy.java","target/classes/com/kingyurina/demo/stock/SecEdgarService.class","target/classes/com/kingyurina/demo/stock/SecFinancialStandardService.class","target/classes/com/kingyurina/demo/stock/SecFinancialStandardService$RowBuilder.class"))hashes.put(p,QuantRestandardizedReplay.sha(p));
        var codeSnapshots=new TreeMap<String,String>();var codeDir=output.resolve("code-snapshots");Files.createDirectory(codeDir);
        for(var h:hashes.entrySet())if(h.getKey().endsWith(".java")||h.getKey().endsWith(".class")){
            Path file=Path.of(h.getKey());Path copy=codeDir.resolve(h.getValue()+"-"+file.getFileName());Files.copy(file,copy);codeSnapshots.put(h.getKey(),copy.toString());
        }
        var baseline=read(BASE);var context=read(CONTEXT);var input=read(INPUT);var evidence=read(QUEUE+"/manifest.json");
        for(var h:baseline.path("inputHashes").properties())require(QuantRestandardizedReplay.sha(h.getKey()).equals(h.getValue().asText()),"Frozen baseline dependency changed: "+h.getKey());
        var required=new TreeMap<String,Set<String>>();for(var q:read(QUEUE+"/accession-queue.json"))required.computeIfAbsent(q.path("symbol").asText(),k->new TreeSet<>()).add(q.path("accession").asText());
        var parser=new SecEdgarService(new SecEdgarProperties("https://unused.invalid","https://unused.invalid","offline",168,100000,0,false),null,null,json,null,null,null);
        var parse=SecEdgarService.class.getDeclaredMethod("parseFacts",String.class,String.class,String.class);parse.setAccessible(true);
        var standardizer=new SecFinancialStandardService(null,null,json);var normalize=SecFinancialStandardService.class.getDeclaredMethod("standardize",String.class,String.class,List.class);normalize.setAccessible(true);
        var rowsBySymbol=new TreeMap<String,List<SecFinancialStandard>>();var rawBySymbol=new TreeMap<String,List<SecCompanyFact>>();var sourceAudit=new TreeMap<String,Object>();
        for(var f:evidence.path("sourceFiles")){
            String path=f.path("path").asText();require(QuantRestandardizedReplay.sha(path).equals(f.path("sha256").asText()),"Source hash mismatch");hashes.put(path,f.path("sha256").asText());if(!path.endsWith("-facts.json"))continue;
            String symbol=f.path("symbol").asText();var root=read(path);String cik=root.path("cik").asText();
            @SuppressWarnings("unchecked")var facts=(List<SecCompanyFact>)parse.invoke(parser,symbol,cik,Files.readString(Path.of(path)));
            facts=facts.stream().filter(x->required.get(symbol).contains(x.getAccessionNumber())).toList();
            require(facts.stream().allMatch(x->x.getFiscalYear()!=null&&x.getFiscalPeriod()!=null),"Nullable persisted fiscal keys");
            @SuppressWarnings("unchecked")var rows=(List<SecFinancialStandard>)normalize.invoke(standardizer,symbol,cik,facts);
            require(rows.stream().allMatch(x->x.getEpsUnit()==null||!x.getEpsUnit().equals("USD/QUOTED-SECURITY")),"Unproved foreign quote basis");
            rowsBySymbol.put(symbol,rows);rawBySymbol.put(symbol,facts);sourceAudit.put(symbol,Map.of("parsedScopedFacts",facts.size(),"nativeStandards",rows.size(),"annualRows",rows.stream().filter(x->"FY".equals(x.getFiscalPeriod())).count(),"quarterRows",rows.stream().filter(x->x.getFiscalPeriod().matches("Q[1-4]")).count()));
        }
        require(!rowsBySymbol.getOrDefault("GFS",List.of()).isEmpty(),"Native GFS support absent");
        write(output.resolve("native-standards.json"),rowsBySymbol);write(output.resolve("native-facts.json"),rawBySymbol);write(output.resolve("native-source-audit.json"),sourceAudit);
        var app=new SpringApplication(CodexDemoApplication.class);app.setWebApplicationType(WebApplicationType.NONE);
        try(var ctx=app.run("--spring.profiles.active=mariadb","--spring.sql.init.mode=never","--spring.datasource.hikari.read-only=true","--spring.datasource.hikari.connection-init-sql=SET SESSION TRANSACTION READ ONLY","--app.signal.refresh.enabled=false","--app.signal.refresh.run-on-startup=false")){
            var jdbc=ctx.getBean(JdbcTemplate.class);require(jdbc.queryForObject("SELECT @@tx_read_only",Integer.class)==1,"DB not read-only");var before=QuantRestandardizedReplay.databaseState(jdbc);
            var source=list(context.path("source"),StockBacktestResult.class);var existing=list(context.path("existing"),StockExpectedReturnSnapshot.class);var benchmark=list(context.path("benchmark"),StockBenchmarkReturn.class);var macro=list(context.path("macro"),StockMacroFeatureSnapshot.class);var allMarket=list(context.path("market"),StockMarketSnapshot.class);
            var originalPredictions=list(baseline.path("predictions"),StockExpectedReturnSnapshot.class);var expected=new TreeMap<String,StockExpectedReturnSnapshot>();for(var p:originalPredictions)expected.put(QuantRestandardizedReplay.key(p),p);
            var real=ctx.getBean(HistoricalBacktestSeedMapper.class);var builder=ctx.getBean(HistoricalBacktestSeedService.class);var build=HistoricalBacktestSeedService.class.getDeclaredMethod("buildSnapshot",HistoricalBacktestSeedMapper.class,String.class,LocalDate.class);build.setAccessible(true);
            QuantMatchedFinancialReplay.json=json;QuantMatchedFinancialReplay.validPeriod=Class.forName("com.kingyurina.demo.stock.FinancialInputPolicy").getDeclaredMethod("validPeriod",SecFinancialStandard.class,boolean.class);QuantMatchedFinancialReplay.validPeriod.setAccessible(true);
            var dates=new ArrayList<Object>();var allPredictions=new ArrayList<StockExpectedReturnSnapshot>();double maxControl=0;int controls=0;
            for(var previous:baseline.path("dates")){
                String text=previous.path("decision").asText();if(!args[1].equals("ALL")&&!args[1].equals(text))continue;LocalDate day=LocalDate.parse(text);System.out.println("IFRS_DATE_START "+day);
                var oldSignals=list(previous.path("newSignals"),StockSignalSnapshot.class);var market=market(previous,input,allMarket);
                var control=QuantRestandardizedReplay.models(day,targets(previous,oldSignals,input,source,market),source,existing,benchmark,macro,new ArrayList<>(market.values()),new TreeMap<>());
                QuantRestandardizedLeakageCheck.json=json;
                require(QuantRestandardizedLeakageCheck.normalized(control).equals(QuantRestandardizedLeakageCheck.normalized(originalPredictions.stream().filter(p->day.equals(p.getSignalDate())).toList())),"Control forecast fields differ from frozen baseline");
                for(var p:control){var saved=expected.get(QuantRestandardizedReplay.key(p));require(saved!=null,"Missing baseline model row");double delta=Math.max(p.getExpectedReturnPct().subtract(saved.getExpectedReturnPct()).abs().doubleValue(),p.getExpectedExcessReturnPct().subtract(saved.getExpectedExcessReturnPct()).abs().doubleValue());maxControl=Math.max(maxControl,delta);controls++;}require(maxControl==0,"Control does not exactly reproduce frozen baseline");
                var signals=new TreeMap<String,StockSignalSnapshot>();for(var s:oldSignals)signals.put(s.getSymbol(),json.convertValue(s,StockSignalSnapshot.class));var reads=new TreeMap<String,Object>();var changed=new TreeSet<String>();
                for(String symbol:rowsBySymbol.keySet()){
                    if(!signals.containsKey(symbol))continue;var rows=rowsBySymbol.get(symbol);var series=new TreeMap<LocalDate,StockCandleDaily>();for(var p:input.path("prices").path(symbol)){var c=new StockCandleDaily();c.setSymbol(symbol);c.setTradeDate(LocalDate.parse(p.path("day").asText()));c.setClosePrice(new BigDecimal(p.path("close").asText()));series.put(c.getTradeDate(),c);}
                    HistoricalBacktestSeedMapper overlay=(HistoricalBacktestSeedMapper)Proxy.newProxyInstance(QuantIfrsFinancialReplay.class.getClassLoader(),new Class[]{HistoricalBacktestSeedMapper.class},(p,m,a)->{
                        String name=m.getName();if(name.equals("findCandlesOnOrBefore")){var result=new ArrayList<StockCandleDaily>();for(var c:series.headMap((LocalDate)a[1],true).descendingMap().values()){result.add(json.convertValue(c,StockCandleDaily.class));if(result.size()==(Integer)a[2])break;}return result;}
                        if(Set.of("findLatestAnnualAsOf","findPreviousAnnualAsOf","findLatestQuarterAsOf","findPreviousQuarterAsOf").contains(name)){boolean annual=name.contains("Annual"),prev=name.contains("Previous");var f=QuantMatchedFinancialReplay.select(rows,annual,prev?(LocalDate)a[1]:null,(LocalDate)a[prev?2:1]);reads.put(symbol+"/"+name,f==null?Map.of("missing",true):f);return f;}
                        require(Set.of("findRecommendationAsOf","findInstitutionFlowAsOf","findNewsStatsAsOf").contains(name),"Unexpected real mapper operation");return m.invoke(real,a);
                    });
                    var next=(StockSignalSnapshot)build.invoke(builder,overlay,symbol,day);require(next!=null,"Cannot rebuild source financial panel");
                    for(String factor:List.of("Analyst","News","Momentum","Risk","Institution")){var get=StockSignalSnapshot.class.getMethod("get"+factor+"Score");require(Objects.equals(get.invoke(next),get.invoke(signals.get(symbol))),"Nonfinancial factor changed "+symbol+day+factor);}
                    signals.put(symbol,next);changed.add(symbol);
                }
                var updated=new ArrayList<>(signals.values());require(updated.size()==101,"Incomplete population");for(var s:updated){int score=0;for(int i=0;i<QuantRestandardizedReplay.FACTORS.size();i++)score+=(Integer)StockSignalSnapshot.class.getMethod("get"+QuantRestandardizedReplay.FACTORS.get(i)+"Score").invoke(s)*QuantRestandardizedReplay.WEIGHTS[i];s.setIntegratedScore(Math.round(score/100f));}QuantRestandardizedReplay.rank(updated);
                var target=targets(previous,updated,input,source,market);var predictions=QuantRestandardizedReplay.models(day,target,source,existing,benchmark,macro,new ArrayList<>(market.values()),new TreeMap<>());
                var pairs=new ArrayList<Map<String,Object>>();for(var n:previous.path("pairs")){var pair=new LinkedHashMap<String,Object>();pair.putAll(json.convertValue(n,Map.class));String symbol=n.path("symbol").asText();var prior=originalPredictions.stream().filter(p->day.equals(p.getSignalDate())&&QuantRestandardizedReplay.V9.equals(p.getModelVersion())&&symbol.equals(p.getSymbol())).findFirst().orElseThrow();var next=predictions.stream().filter(p->QuantRestandardizedReplay.V9.equals(p.getModelVersion())&&symbol.equals(p.getSymbol())).findFirst().orElseThrow();pair.put("oldAbsolute",prior.getExpectedReturnPct());pair.put("oldExcess",prior.getExpectedExcessReturnPct());pair.put("newAbsolute",next.getExpectedReturnPct());pair.put("newExcess",next.getExpectedExcessReturnPct());pair.put("affected",changed.contains(symbol));pairs.add(pair);}
                dates.add(Map.of("decision",day,"newSignals",updated,"financialReads",reads,"pairs",pairs,"affectedSymbols",changed));allPredictions.addAll(predictions);
                if(day.equals(LocalDate.parse("2026-03-13"))){var altered=new ArrayList<StockBacktestResult>();for(var r:target){var copy=json.convertValue(r,StockBacktestResult.class);copy.setForwardReturnPct(BigDecimal.valueOf(-50));altered.add(copy);}QuantRestandardizedLeakageCheck.json=json;var perturbed=QuantRestandardizedReplay.models(day,altered,source,existing,benchmark,macro,new ArrayList<>(market.values()),new TreeMap<>());require(QuantRestandardizedLeakageCheck.normalized(predictions).equals(QuantRestandardizedLeakageCheck.normalized(perturbed)),"Target future labels changed prediction");write(output.resolve("future-label-invariance.json"),Map.of("decision",day,"targetRowsPerturbed",101,"predictionsCompared",808,"allFieldsExceptCalculatedAtInvariant",true));}
                System.out.println("IFRS_DATE_DONE "+day+" affected="+changed);
            }
            var after=QuantRestandardizedReplay.databaseState(jdbc);require(before.equals(after),"DB changed during read-only replay");
            for(var h:hashes.entrySet())require(QuantRestandardizedReplay.sha(h.getKey()).equals(h.getValue()),"Source/code changed during replay: "+h.getKey());
            var report=new LinkedHashMap<String,Object>();report.put("classification","NATIVE_IFRS_INPUT_REPAIR_PAIRED_SENSITIVITY_NOT_NEW_HOLDOUT");report.put("databaseWrites",0);report.put("promotionAllowed",false);report.put("weightsChanged",false);report.put("sourceDatabaseBefore",before);report.put("sourceDatabaseAfter",after);report.put("controlPredictionsCompared",controls);report.put("controlMaxAbsDifference",maxControl);report.put("inputHashes",hashes);report.put("codeSnapshots",codeSnapshots);report.put("nativeSourceAudit",sourceAudit);report.put("dates",dates);report.put("predictions",allPredictions);write(output.resolve("result.json"),report);System.out.println("IFRS_DONE dates="+dates.size()+" controls="+controls+" maxControlDelta="+maxControl);
        }
    }
}
