package com.kingyurina.demo.stock;

import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import tools.jackson.databind.ObjectMapper;

/** Local batch-only, append-only evidence. Disabled when no explicit run ID is supplied. */
public final class StockForwardInputEvidence {
    private final ObjectMapper json;
    private final Path directory;
    private final String runId;

    public StockForwardInputEvidence(ObjectMapper json,String directory,String runId){
        this.json=json;
        this.directory=Path.of(directory).toAbsolutePath().normalize();
        this.runId=runId==null||runId.isBlank()?null:UUID.fromString(runId).toString();
    }

    public Path capture(String symbol,Map<String,Object> inputs){
        if(runId==null)return null;
        if(symbol==null||!symbol.matches("[A-Z0-9.^_=-]{1,40}"))throw new IllegalArgumentException("Unsafe security identifier");
        var body=new LinkedHashMap<String,Object>();
        body.put("schemaVersion","QUANT_SIGNAL_INPUT_EVIDENCE_V1");body.put("runId",runId);body.put("symbol",symbol);
        body.put("observedAtUtc",Instant.now().toString());body.put("inputs",inputs);
        body.put("completeHistoricalPIT",false);
        body.put("availabilityPolicy","Public filing date, cached fetch time and this actual observation time are distinct; missing remains null");
        try{
            Path folder=directory.resolve(runId);Files.createDirectories(folder);
            Path path=folder.resolve(symbol+".json");
            Files.write(path,json.writerWithDefaultPrettyPrinter().writeValueAsBytes(body),StandardOpenOption.CREATE_NEW);
            return path;
        }catch(Exception ex){throw new IllegalStateException("Input evidence could not be preserved; batch must report failure",ex);}
    }
}
