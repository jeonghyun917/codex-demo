package com.kingyurina.demo.stock;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/** Append-only observations from batch refresh, never reconstructed backfills or future outcomes. */
@Service
public class StockExpectedReturnForwardArchive {
    private final ObjectMapper json;
    private final Path root;
    private final String runId;

    public StockExpectedReturnForwardArchive(ObjectMapper json,
            @Value("${app.quant.forward-archive.directory:./data/quant-forward}") String directory) {
        this(json,directory,"");
    }

    @Autowired
    public StockExpectedReturnForwardArchive(ObjectMapper json,
            @Value("${app.quant.forward-archive.directory:./data/quant-forward}") String directory,
            @Value("${app.quant.forward.run-id:}") String runId) {
        this.json=json;
        this.root=Path.of(directory).toAbsolutePath().normalize();
        this.runId=runId==null||runId.isBlank()?null:UUID.fromString(runId).toString();
    }

    public Path capture(String indexCode, LocalDate day, List<StockExpectedReturnSnapshot> predictions,
            List<StockExpectedReturnSnapshot> upstreamV8, List<StockMarketSnapshot> markets) {
        if (indexCode==null || !indexCode.matches("[A-Z0-9_]{1,40}") || day==null || predictions.isEmpty()) {
            throw new IllegalArgumentException("Nonempty forward cohort and safe index/date required");
        }
        var symbols=new HashSet<String>();
        for (var p:predictions) {
            if (!indexCode.equals(p.getIndexCode()) || !day.equals(p.getSignalDate())
                    || !Integer.valueOf(20).equals(p.getHorizonDays())
                    || !StockExpectedReturnV9Service.MODEL_VERSION.equals(p.getModelVersion())
                    || p.getSource()==null || !p.getSource().startsWith("LIVE_V9_")
                    || p.getSymbol()==null || !symbols.add(p.getSymbol())) {
                throw new IllegalArgumentException("Only unique persisted live V9 20D rows in this cohort qualify");
            }
        }
        var body=new LinkedHashMap<String,Object>();
        body.put("schemaVersion","QUANT_FORWARD_OBSERVATION_V1");
        body.put("classification","OBSERVED_FORWARD_OUTPUT_NOT_COMPLETE_PIT_PROOF");
        body.put("capturedAtUtc",Instant.now().toString());
        if(runId!=null)body.put("runId",runId);
        body.put("indexCode",indexCode);body.put("signalDate",day.toString());
        body.put("modelVersion",StockExpectedReturnV9Service.MODEL_VERSION);
        body.put("applicationFinancialPolicyVersionAtCapture","q4-fin-20261031");
        body.put("displayedTarget","EXCESS_PRICE_RETURN_20_TRADING_DAYS");
        body.put("displayedSurface","QUANT_INDEX_TABLE_EXPECTED_EXCESS_COLUMN");
        body.put("probabilityTarget","POSITIVE_EXCESS_RETURN_NOT_ABSOLUTE_PRICE_UP");
        body.put("predictions",predictions);body.put("upstreamV8",upstreamV8);body.put("marketInputs",markets);
        body.put("limitations",List.of("Observed now; signal date is not proof of historical publication/receipt.",
                "Full underlying V2-V7 raw inputs and input receipt timestamps are not archived here.",
                "Application policy version does not prove the policy version of every upstream stored row.",
                "Capture may be intraday; evaluation must choose observations before the defined entry cutoff.",
                "No future realized outcomes or validation/promotion claim; retain this directory on durable storage."));
        try {
            Path directory=root.resolve(indexCode).resolve(day.toString());
            Files.createDirectories(directory);
            byte[] bytes=json.writerWithDefaultPrettyPrinter().writeValueAsBytes(body);
            Path file=directory.resolve(UUID.randomUUID()+".json");
            Files.write(file,bytes,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE);
            return file;
        } catch (Exception ex) {
            throw new IllegalStateException("Forward observation archive failed; batch must report failure",ex);
        }
    }
}
