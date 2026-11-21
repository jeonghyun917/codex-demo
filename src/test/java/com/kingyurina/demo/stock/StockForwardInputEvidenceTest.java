package com.kingyurina.demo.stock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

class StockForwardInputEvidenceTest {
    @TempDir Path root;

    @Test void preservesNullZeroAndObservationTimeWithoutClaimingHistoricalReceipt() throws Exception {
        var writer=new StockForwardInputEvidence(new ObjectMapper(),root.toString(),"11111111-1111-4111-8111-111111111111");
        var payload=new LinkedHashMap<String,Object>();payload.put("unavailable",null);payload.put("zero",0);
        Path path=writer.capture("AAPL",payload);
        var value=new ObjectMapper().readTree(Files.readAllBytes(path));
        assertTrue(value.path("inputs").path("unavailable").isNull());
        assertEquals(0,value.path("inputs").path("zero").asInt());
        assertFalse(value.path("completeHistoricalPIT").asBoolean());
        assertFalse(value.path("observedAtUtc").asText().isBlank());
        byte[] original=Files.readAllBytes(path);
        assertThrows(IllegalStateException.class,()->writer.capture("AAPL",Map.of("zero",9)));
        assertArrayEquals(original,Files.readAllBytes(path));
    }

    @Test void disabledNormalWebContextDoesNotWriteAndUnsafeIdsAreRejected() throws Exception {
        var disabled=new StockForwardInputEvidence(new ObjectMapper(),root.toString(),"");
        assertNull(disabled.capture("AAPL",Map.of()));
        try(var paths=Files.list(root)){assertEquals(0,paths.count());}
        assertThrows(IllegalArgumentException.class,()->new StockForwardInputEvidence(new ObjectMapper(),root.toString(),"../bad"));
        var writer=new StockForwardInputEvidence(new ObjectMapper(),root.toString(),"11111111-1111-4111-8111-111111111111");
        assertThrows(IllegalArgumentException.class,()->writer.capture("../bad",Map.of()));
    }

    @Test void actualSignalBuildPreservesSelectedUnavailableInputsWithoutChangingScores() throws Exception {
        var cache=mock(StockCacheService.class);var quality=mock(StockDataQualityService.class);var json=new ObjectMapper();
        String run="11111111-1111-4111-8111-111111111111";
        var baseline=new StockSignalService(cache,quality,json).buildLatest("AAA");
        var traced=new StockSignalService(cache,quality,json,run,root.toString()).buildLatest("AAA");
        assertEquals(baseline.getIntegratedScore(),traced.getIntegratedScore());
        assertEquals(baseline.getGrowthScore(),traced.getGrowthScore());
        var body=json.readTree(Files.readAllBytes(root.resolve(run).resolve("AAA.json")));
        assertEquals("AAA",body.path("inputs").path("dashboard").path("symbol").asText());
        assertTrue(body.path("inputs").path("annual").isNull());
        assertEquals(10,body.path("inputs").path("unrankedFactors").size());
        for(var factor:body.path("inputs").path("unrankedFactors")){
            assertFalse(factor.path("available").asBoolean());assertEquals(50,factor.path("score").asInt());
        }
    }
}
