package com.kingyurina.demo.stock;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.UUID;
import java.nio.file.Path;
import java.net.URI;
import java.sql.Connection;
import java.sql.Date;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;
import javax.sql.DataSource;
import tools.jackson.databind.ObjectMapper;

@Component
public class StockCandleReconciliationBatchRunner implements ApplicationRunner {
    private static final String BASIS="YAHOO_PROVIDER_ADJUSTED_OHLC_NOT_DIVIDEND_ADJUSTED";
    private final String bundlePath, selectedSymbols;
    private final boolean apply, exit;
    private final ObjectProvider<DataSource> dataSource;
    private final ObjectMapper mapper;
    private final ConfigurableApplicationContext context;
    public StockCandleReconciliationBatchRunner(
            @Value("${app.batch.candle-reconcile.bundle:}") String bundlePath,
            @Value("${app.batch.candle-reconcile.symbols:}") String symbols,
            @Value("${app.batch.candle-reconcile.apply:false}") boolean apply,
            @Value("${app.batch.candle-reconcile.exit-on-complete:false}") boolean exit,
            ObjectProvider<DataSource> dataSource, ObjectMapper mapper, ConfigurableApplicationContext context) {
        this.bundlePath=bundlePath; this.selectedSymbols=symbols; this.apply=apply; this.exit=exit;
        this.dataSource=dataSource; this.mapper=mapper; this.context=context;
    }
    @Override public void run(ApplicationArguments args) throws Exception {
        if(bundlePath==null || bundlePath.isBlank()) return;
        int code=0;
        try {
            List<String> symbols=java.util.Arrays.stream(selectedSymbols.split(",")).map(String::trim)
                    .filter(s->!s.isEmpty()).distinct().sorted().toList();
            if(symbols.isEmpty() || symbols.size()>20) throw new IllegalArgumentException("Explicit selection of 1-20 symbols required");
            DataSource db=dataSource.getIfAvailable();
            if(db==null) throw new IllegalStateException("Reconciliation requires MariaDB");
            var report=reconcile(db,StockHistoricalEvidenceBundle.read(Path.of(bundlePath),mapper),symbols,apply,mapper);
            System.out.println("Candle reconciliation "+mapper.writeValueAsString(report));
        } catch(Exception ex) { code=1; throw ex; }
        finally {
            if(exit) { int result=code; new Thread(()->System.exit(SpringApplication.exit(context,()->result))).start(); }
        }
    }
    public record SymbolResult(String symbol, int comparedRows, int changedRows, int changedCloseRows, String sourceSha256) {}
    public record Report(String status, String reconciliationId, List<SymbolResult> symbols) {}
    static Report reconcile(DataSource db, StockHistoricalEvidenceBundle bundle, List<String> symbols,
            boolean apply, ObjectMapper mapper) throws Exception {
        if(symbols==null || symbols.isEmpty() || symbols.stream().anyMatch(s->s==null || !s.matches("[A-Z0-9.-]{1,20}"))
                || symbols.stream().distinct().count()!=symbols.size()) throw new IllegalArgumentException("Invalid symbol selection");
        // Read and validate immutable external data before taking database locks.
        Map<String,StockHistoricalEvidenceBundle.Source> sources=new LinkedHashMap<>();
        Map<String,List<StockCandleDaily>> snapshots=new LinkedHashMap<>();
        for(String symbol : symbols.stream().sorted().toList()) {
            var matches=bundle.sources().stream().filter(s->URI.create(s.url()).getPath()
                    .equals("/v8/finance/chart/"+symbol.replace('.','-'))).toList();
            if(matches.size()!=1) throw new IllegalArgumentException("One complete chart source required: "+symbol);
            var source=matches.getFirst(); sources.put(symbol,source);
            snapshots.put(symbol,StockCandleReconciliation.readSnapshot(source,symbol,mapper));
        }
        try(Connection connection=db.getConnection()) {
            connection.setAutoCommit(false);
            try {
                var plans=new ArrayList<StockCandleReconciliation.Plan>();
                var summaries=new ArrayList<SymbolResult>();
                for(String symbol:sources.keySet()) {
                    try(var lock=connection.prepareStatement("SELECT symbol FROM stock_symbol WHERE symbol=? FOR UPDATE")) {
                        lock.setString(1,symbol); try(var rows=lock.executeQuery()) {
                            if(!rows.next()) throw new IllegalArgumentException("Unknown stock identity: "+symbol);
                        }
                    }
                    var before=new ArrayList<StockCandleDaily>();
                    try(var select=connection.prepareStatement("SELECT * FROM stock_candle_daily WHERE symbol=? ORDER BY trade_date FOR UPDATE")) {
                        select.setString(1,symbol); try(var rows=select.executeQuery()) {
                            while(rows.next()) {
                                var candle=new StockCandleDaily(); candle.setSymbol(symbol); candle.setTradeDate(rows.getDate("trade_date").toLocalDate());
                                candle.setOpenPrice(rows.getBigDecimal("open_price")); candle.setHighPrice(rows.getBigDecimal("high_price"));
                                candle.setLowPrice(rows.getBigDecimal("low_price")); candle.setClosePrice(rows.getBigDecimal("close_price"));
                                candle.setVolume(rows.getObject("volume",Long.class)); candle.setSource(rows.getString("source"));
                                candle.setUpdatedAt(rows.getTimestamp("updated_at").toLocalDateTime()); before.add(candle);
                            }
                        }
                    }
                    var plan=StockCandleReconciliation.plan(symbol,before,snapshots.get(symbol)); plans.add(plan);
                    int closeChanges=(int)plan.changes().stream().filter(c->c.before().getClosePrice().compareTo(c.after().getClosePrice())!=0).count();
                    summaries.add(new SymbolResult(symbol,plan.comparedRows(),plan.changes().size(),closeChanges,sources.get(symbol).sha256()));
                }
                if(!apply) { connection.rollback(); return new Report("DRY_RUN",null,List.copyOf(summaries)); }
                if(plans.stream().allMatch(p->p.changes().isEmpty())) { connection.rollback(); return new Report("NO_CHANGES",null,List.copyOf(summaries)); }
                String id=UUID.randomUUID().toString();
                var report=new Report("COMMITTED",id,List.copyOf(summaries));
                try(var insert=connection.prepareStatement("INSERT INTO stock_candle_reconciliation_run(reconciliation_id,source_manifest_sha256,price_basis,report_json) VALUES (?,?,?,?)")) {
                    insert.setString(1,id); insert.setString(2,bundle.manifest().sha256()); insert.setString(3,BASIS);
                    insert.setString(4,mapper.writeValueAsString(report)); insert.executeUpdate();
                }
                try(var backup=connection.prepareStatement("""
                        INSERT INTO stock_candle_reconciliation_backup
                        (reconciliation_id,symbol,trade_date,old_open,old_high,old_low,old_close,old_volume,old_source,
                         old_created_at,old_updated_at,old_source_row_json,new_open,new_high,new_low,new_close,new_volume,source_content_sha256)
                        SELECT ?,c.symbol,c.trade_date,c.open_price,c.high_price,c.low_price,c.close_price,c.volume,c.source,
                               c.created_at,c.updated_at,
                               (SELECT JSON_OBJECT('open',s.open_price,'high',s.high_price,'low',s.low_price,'close',s.close_price,
                                   'volume',s.volume,'source',s.source,'createdAt',s.created_at,'updatedAt',s.updated_at)
                                FROM stock_candle_daily_source s WHERE s.symbol=c.symbol AND s.trade_date=c.trade_date AND s.source='yahoo-chart'),
                               ?,?,?,?,?,? FROM stock_candle_daily c WHERE c.symbol=? AND c.trade_date=?
                        """); var update=connection.prepareStatement("""
                        UPDATE stock_candle_daily SET open_price=?,high_price=?,low_price=?,close_price=?,volume=?,updated_at=CURRENT_TIMESTAMP
                        WHERE symbol=? AND trade_date=?
                        """); var sourceUpdate=connection.prepareStatement("""
                        INSERT INTO stock_candle_daily_source(symbol,trade_date,source,open_price,high_price,low_price,close_price,volume)
                        VALUES (?,?,'yahoo-chart',?,?,?,?,?) ON DUPLICATE KEY UPDATE open_price=VALUES(open_price),high_price=VALUES(high_price),
                        low_price=VALUES(low_price),close_price=VALUES(close_price),volume=VALUES(volume),updated_at=CURRENT_TIMESTAMP
                        """)) {
                    for(var plan:plans) for(var change:plan.changes()) {
                        var next=change.after();
                        backup.setString(1,id); setPrices(backup,2,next); backup.setString(7,sources.get(plan.symbol()).sha256());
                        backup.setString(8,plan.symbol()); backup.setDate(9,Date.valueOf(next.getTradeDate()));
                        if(backup.executeUpdate()!=1) throw new IllegalStateException("Missing canonical backup row");
                        setPrices(update,1,next); update.setString(6,plan.symbol()); update.setDate(7,Date.valueOf(next.getTradeDate()));
                        if(update.executeUpdate()!=1) throw new IllegalStateException("Unexpected update count");
                        sourceUpdate.setString(1,plan.symbol()); sourceUpdate.setDate(2,Date.valueOf(next.getTradeDate()));
                        setPrices(sourceUpdate,3,next); sourceUpdate.executeUpdate();
                    }
                }
                connection.commit(); return report;
            } catch(Exception ex) { connection.rollback(); throw ex; }
        }
    }
    private static void setPrices(java.sql.PreparedStatement statement,int first,StockCandleDaily row) throws Exception {
        statement.setBigDecimal(first,row.getOpenPrice()); statement.setBigDecimal(first+1,row.getHighPrice());
        statement.setBigDecimal(first+2,row.getLowPrice()); statement.setBigDecimal(first+3,row.getClosePrice());
        statement.setObject(first+4,row.getVolume(),java.sql.Types.BIGINT);
    }
}
