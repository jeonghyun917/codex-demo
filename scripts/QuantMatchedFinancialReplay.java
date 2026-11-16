import com.kingyurina.demo.CodexDemoApplication;
import com.kingyurina.demo.stock.*;
import org.springframework.boot.*;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.lang.reflect.*;
import java.util.*;

/** Matched financial-source sensitivity, no DB writes; never alters the completed production-path run. */
public final class QuantMatchedFinancialReplay {
    static ObjectMapper json;
    static Method validPeriod;
    static final List<String> OVERLAYS=List.of("tmp/quant-older-staged-20261030/inputs.json","tmp/quant-extended-inputs-20261018/financial-inputs.json","tmp/quant-team-inputs-20261018/financial-inputs.json","tmp/quant-march-inputs-20261031/inputs-v3.json");
    static boolean valid(SecFinancialStandard f,boolean annual){try{return (boolean)validPeriod.invoke(null,f,annual);}catch(Exception e){throw new IllegalStateException(e);}}
    static int official(SecFinancialStandard f){return Set.of("10-K","10-K/A","20-F","20-F/A","40-F","40-F/A").contains(f.getForm())?0:1;}
    static SecFinancialStandard select(List<SecFinancialStandard> rows,boolean annual,LocalDate before,LocalDate asof){
        Comparator<SecFinancialStandard> order=Comparator.comparing(SecFinancialStandard::getEndDate,Comparator.reverseOrder());
        order=annual?order.thenComparingInt(QuantMatchedFinancialReplay::official):order.thenComparingInt(f->f.getAssets()!=null||f.getEquity()!=null?0:1);
        order=order.thenComparing(SecFinancialStandard::getFiledAt,Comparator.reverseOrder()).thenComparing(SecFinancialStandard::getId,Comparator.nullsLast(Comparator.reverseOrder()));
        return rows.stream().filter(f->valid(f,annual)&&f.getFiledAt()!=null&&f.getFiledAt().isBefore(asof)
            &&(before==null||(f.getEndDate().isBefore(before)&&ChronoUnit.DAYS.between(f.getEndDate(),before)>=(annual?330:70)&&ChronoUnit.DAYS.between(f.getEndDate(),before)<=(annual?380:110))))
            .sorted(order).findFirst().orElse(null);
    }
    static <T> List<T> list(JsonNode n,Class<T> type){var out=new ArrayList<T>();for(var r:n)out.add(json.treeToValue(r,type));return out;}
    static JsonNode financialRows(List<JsonNode> files,String day,String symbol){
        var n=files.get(0).path("bySymbol").path(symbol).path(day);if(!n.isMissingNode())return n;
        n=files.get(1).path("bySymbol").path(symbol).path(day);if(!n.isMissingNode())return n;
        if(symbol.equals("TEAM")){n=files.get(2).path("byDecision").path(day);if(!n.isMissingNode())return n;}
        if(day.equals("2026-03-13"))return files.get(3).path("financialsBySymbol").path(symbol);
        throw new IllegalStateException("No original financial panel "+day+" "+symbol);
    }
    public static void main(String[] args)throws Exception{
        QuantRestandardizedReplay.require(args.length==4,"Usage: production-replay-dir prepared-input NEW-OUTPUT-DIR ALL|date");
        var prior=Path.of(args[0]);var output=Path.of(args[2]);QuantRestandardizedReplay.require(!Files.exists(output),"Output exists");Files.createDirectories(output);
        var app=new SpringApplication(CodexDemoApplication.class);app.setWebApplicationType(WebApplicationType.NONE);
        try(var ctx=app.run("--spring.profiles.active=mariadb","--spring.sql.init.mode=never","--spring.datasource.hikari.read-only=true","--spring.datasource.hikari.connection-init-sql=SET SESSION TRANSACTION READ ONLY","--app.signal.refresh.enabled=false","--app.signal.refresh.run-on-startup=false")){
            json=ctx.getBean(ObjectMapper.class);QuantRestandardizedReplay.json=json;
            validPeriod=Class.forName("com.kingyurina.demo.stock.FinancialInputPolicy").getDeclaredMethod("validPeriod",SecFinancialStandard.class,boolean.class);validPeriod.setAccessible(true);
            var jdbc=ctx.getBean(JdbcTemplate.class);QuantRestandardizedReplay.require(jdbc.queryForObject("SELECT @@tx_read_only",Integer.class)==1,"Not read-only");
            var before=QuantRestandardizedReplay.databaseState(jdbc);
            var oldReport=json.readTree(Files.readString(prior.resolve("result.json")));var input=json.readTree(Files.readString(Path.of(args[1])));
            var context=json.readTree(Files.readString(prior.resolve("frozen-context.json")));
            var source=list(context.path("source"),StockBacktestResult.class);var existing=list(context.path("existing"),StockExpectedReturnSnapshot.class);
            var benchmark=list(context.path("benchmark"),StockBenchmarkReturn.class);var macro=list(context.path("macro"),StockMacroFeatureSnapshot.class);var allMarket=list(context.path("market"),StockMarketSnapshot.class);
            var hashes=new TreeMap<String,String>();
            for(var f:oldReport.path("inputHashes").properties())QuantRestandardizedReplay.require(QuantRestandardizedReplay.sha(f.getKey()).equals(f.getValue().asText()),"Changed prior replay dependency "+f.getKey());
            for(String p:List.of(args[1],prior.resolve("result.json").toString(),prior.resolve("frozen-context.json").toString(),"scripts/QuantMatchedFinancialReplay.java","scripts/QuantRestandardizedReplay.java"))hashes.put(p,QuantRestandardizedReplay.sha(p));
            var files=new ArrayList<JsonNode>();for(String p:OVERLAYS){
                String observed=QuantRestandardizedReplay.sha(p);boolean matched=false;
                for(var recorded:input.path("priorReportsRecordedSourceHashes").properties())if(recorded.getValue().has(p)){
                    QuantRestandardizedReplay.require(observed.equals(recorded.getValue().path(p).asText()),"Changed original overlay "+p);matched=true;
                }
                QuantRestandardizedReplay.require(matched,"No original recorded overlay hash "+p);
                files.add(json.readTree(Files.readString(Path.of(p))));hashes.put(p,observed);
            }
            var manifest=new ArrayList<Object>();var allDates=new ArrayList<Object>();var allPredictions=new ArrayList<StockExpectedReturnSnapshot>();
            var seedReal=ctx.getBean(HistoricalBacktestSeedMapper.class);var builder=ctx.getBean(HistoricalBacktestSeedService.class);
            var build=HistoricalBacktestSeedService.class.getDeclaredMethod("buildSnapshot",HistoricalBacktestSeedMapper.class,String.class,LocalDate.class);build.setAccessible(true);
            int affected=0,accessions=0;
            for(var previous:oldReport.path("dates")){
                String dayText=previous.path("decision").asText();if(!args[3].equals("ALL")&&!args[3].equals(dayText))continue;var day=LocalDate.parse(dayText);
                var signals=list(previous.path("newSignals"),StockSignalSnapshot.class);var bySymbol=new TreeMap<String,StockSignalSnapshot>();for(var s:signals)bySymbol.put(s.getSymbol(),s);
                var lookupAudit=new TreeMap<String,Object>();var rebuilt=new TreeSet<String>();
                for(var old:input.path("oldRecoveredSignals").path(dayText).properties()){
                    String symbol=old.getKey();var oldRaw=json.readTree(old.getValue().path("rawJson").asText());
                    var nodes=financialRows(files,dayText,symbol);var rows=list(nodes,SecFinancialStandard.class);QuantRestandardizedReplay.require(!rows.isEmpty(),"Missing old source rows");
                    var required=new TreeSet<String>();for(String field:List.of("annualAccession","previousAnnualAccession","quarterAccession","previousQuarterAccession"))if(!oldRaw.path(field).isNull())required.add(oldRaw.path(field).asText());
                    var available=new TreeSet<String>();rows.forEach(f->available.add(f.getAccessionNumber()));QuantRestandardizedReplay.require(available.containsAll(required),"Uncovered old accession "+symbol+day+required);
                    accessions+=required.size();
                    var prices=new TreeMap<LocalDate,StockCandleDaily>();for(var n:input.path("prices").path(symbol)){
                        var c=new StockCandleDaily();c.setSymbol(symbol);c.setTradeDate(LocalDate.parse(n.path("day").asText()));c.setClosePrice(new BigDecimal(n.path("close").asText()));c.setSource("PRESERVED_CANONICAL_COMPATIBLE_ARCHIVE");prices.put(c.getTradeDate(),c);}
                    HistoricalBacktestSeedMapper seed=(HistoricalBacktestSeedMapper)Proxy.newProxyInstance(QuantMatchedFinancialReplay.class.getClassLoader(),new Class[]{HistoricalBacktestSeedMapper.class},(p,m,a)->{
                        String name=m.getName();
                        if(name.equals("findCandlesOnOrBefore")){
                            var result=new ArrayList<StockCandleDaily>();for(var c:prices.headMap((LocalDate)a[1],true).descendingMap().values()){result.add(json.convertValue(c,StockCandleDaily.class));if(result.size()==(Integer)a[2])break;}return result;
                        }
                        if(Set.of("findLatestAnnualAsOf","findPreviousAnnualAsOf","findLatestQuarterAsOf","findPreviousQuarterAsOf").contains(name)){
                            boolean annual=name.contains("Annual"),prev=name.contains("Previous");var f=select(rows,annual,prev?(LocalDate)a[1]:null,(LocalDate)a[prev?2:1]);
                            lookupAudit.put(symbol+"/"+name,f==null?Map.of("missing",true):f);return f;
                        }
                        QuantRestandardizedReplay.require(Set.of("findRecommendationAsOf","findInstitutionFlowAsOf","findNewsStatsAsOf").contains(name),"Unexpected mapper method");return m.invoke(seedReal,a);
                    });
                    var signal=(StockSignalSnapshot)build.invoke(builder,seed,symbol,day);QuantRestandardizedReplay.require(signal!=null,"Cannot rebuild "+symbol);
                    for(String factor:List.of("Analyst","News","Momentum","Risk","Institution")){
                        var getter=StockSignalSnapshot.class.getMethod("get"+factor+"Score");
                        QuantRestandardizedReplay.require(Objects.equals(getter.invoke(signal),getter.invoke(bySymbol.get(symbol))),"Unexpected nonfinancial input change "+symbol+day+factor);
                    }
                    bySymbol.put(symbol,signal);rebuilt.add(symbol);affected++;
                    manifest.add(Map.of("decision",day,"symbol",symbol,"sourceRows",rows.size(),"oldRequiredAccessions",required,"allOldAccessionsPresent",true,
                        "validAnnualRows",rows.stream().filter(f->valid(f,true)&&f.getFiledAt()!=null&&f.getFiledAt().isBefore(day)).count(),"validQuarterRows",rows.stream().filter(f->valid(f,false)&&f.getFiledAt()!=null&&f.getFiledAt().isBefore(day)).count()));
                }
                var matched=new ArrayList<>(bySymbol.values());QuantRestandardizedReplay.require(matched.size()==101,"Incomplete full cohort");
                // Undo the previous run's rank before reapplying it to all101 members.
                for(var s:matched){int weighted=0;for(int i=0;i<QuantRestandardizedReplay.FACTORS.size();i++)weighted+=(Integer)StockSignalSnapshot.class.getMethod("get"+QuantRestandardizedReplay.FACTORS.get(i)+"Score").invoke(s)*QuantRestandardizedReplay.WEIGHTS[i];s.setIntegratedScore(Math.round(weighted/100f));}
                QuantRestandardizedReplay.rank(matched);
                var market=new TreeMap<String,StockMarketSnapshot>();for(var m:allMarket)if(day.equals(m.getSnapshotDate()))market.put(m.getSymbol(),m);
                for(var f:input.path("marketOverrides").path(dayText).properties())market.put(f.getKey(),json.treeToValue(f.getValue(),StockMarketSnapshot.class));
                var oldBySymbol=new TreeMap<String,StockBacktestResult>();for(var r:source)if(day.equals(r.getSignalDate())&&Integer.valueOf(20).equals(r.getHorizonDays()))oldBySymbol.put(r.getSymbol(),r);
                var target=new ArrayList<StockBacktestResult>();var pairs=new ArrayList<Map<String,Object>>();
                for(var s:matched){var pairNode=previous.path("pairs").valueStream().filter(p->s.getSymbol().equals(p.path("symbol").asText())).findFirst().orElseThrow();
                    var first=new StockCandleDaily();first.setSymbol(s.getSymbol());first.setTradeDate(LocalDate.parse(pairNode.path("entry").asText()));first.setClosePrice(new BigDecimal(pairNode.path("entryClose").asText()));
                    var last=new StockCandleDaily();last.setSymbol(s.getSymbol());last.setTradeDate(LocalDate.parse(pairNode.path("exit").asText()));last.setClosePrice(new BigDecimal(pairNode.path("exitClose").asText()));
                    boolean cap=input.path("oldRecoveredTargetCapSymbols").path(dayText).valueStream().anyMatch(n->s.getSymbol().equals(n.asText()));
                    target.add(QuantRestandardizedReplay.outcome(s,first,last,oldBySymbol.get(s.getSymbol()),cap?market.get(s.getSymbol()):null));
                    @SuppressWarnings("unchecked")var pair=(Map<String,Object>)json.convertValue(pairNode,Map.class);pairs.add(pair);
                }
                var writes=new TreeMap<String,Integer>();System.out.println("MATCHED_DATE_START "+day+" overlaySymbols="+rebuilt.size());
                var predictions=QuantRestandardizedReplay.models(day,target,source,existing,benchmark,macro,new ArrayList<>(market.values()),writes);
                for(var pair:pairs){String symbol=(String)pair.get("symbol");var p=predictions.stream().filter(s->QuantRestandardizedReplay.V9.equals(s.getModelVersion())&&symbol.equals(s.getSymbol())).findFirst().orElseThrow();pair.put("newAbsolute",p.getExpectedReturnPct());pair.put("newExcess",p.getExpectedExcessReturnPct());}
                var date=new LinkedHashMap<String,Object>();date.put("decision",day);date.put("population",bySymbol.keySet());date.put("rebuiltSignals",101);date.put("overlayRebuiltSymbols",rebuilt);date.put("newSignals",matched);date.put("financialReads",lookupAudit);date.put("pairs",pairs);date.put("metrics",QuantRestandardizedReplay.metrics(pairs));date.put("frozenTrainingContextHashOld",previous.path("frozenTrainingContextHashOld").asText());date.put("frozenTrainingContextHashNew",previous.path("frozenTrainingContextHashNew").asText());date.put("capturedWrites",writes);
                allDates.add(date);allPredictions.addAll(predictions);System.out.println("MATCHED_DATE_DONE "+day+" "+json.writeValueAsString(date.get("metrics")));
            }
            var after=QuantRestandardizedReplay.databaseState(jdbc);QuantRestandardizedReplay.require(before.equals(after),"Database change");
            var report=new LinkedHashMap<String,Object>();report.put("classification","MATCHED_RECOVERED_FINANCIAL_SOURCE_SENSITIVITY_NOT_OFFICIAL_PIT");report.put("databaseWrites",0);report.put("promotionAllowed",false);report.put("sourceDatabaseBefore",before);report.put("sourceDatabaseAfter",after);report.put("inputHashes",hashes);report.put("dates",allDates);report.put("predictions",allPredictions);report.put("controlPredictions",oldReport.path("controlPredictions"));report.put("sourceMatchedOverlaySymbolDates",affected);report.put("coveredOldRequiredAccessionReferences",accessions);report.put("overlayManifest",manifest);
            report.put("limitations",List.of("Uses the same original verified recovered financial source panels, not missing-DB substitutions; all originally referenced accessions present.","Production validPeriod, strict filed-before cutoff and annual/quarter selection ordering are enforced; production FinancialInputPolicy guards still decide whether comparisons/EPS are usable.","Policy-invalid old periods may be excluded despite source availability; no forced reuse of invalid inputs or invented FX/ADS conversions.","Existing native financial inputs come from the corrected production-path replay; original DB training, macro and market context remain frozen.","Historical sources, universe and arrival times are reconstructed, not official PIT/live evidence; no predictive improvement or promotion inferred from source repair alone."));
            Files.writeString(output.resolve("result.json"),json.writerWithDefaultPrettyPrinter().writeValueAsString(report),StandardOpenOption.CREATE_NEW);
            Files.writeString(output.resolve("overlay-manifest.json"),json.writerWithDefaultPrettyPrinter().writeValueAsString(Map.of("sourceHashes",hashes,"rows",manifest)),StandardOpenOption.CREATE_NEW);
            System.out.println("MATCHED_DONE dates="+allDates.size()+" overlaySymbolDates="+affected+" coveredAccessionReferences="+accessions);
        }
    }
}
