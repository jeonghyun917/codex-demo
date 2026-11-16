package com.kingyurina.demo.stock;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import java.util.ArrayList;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class StockExpectedReturnForwardArchiveTest {
    @TempDir Path root;

    @Test void explicitRunIdIsArchivedWithoutChangingForecastValues() throws Exception {
        String runId="11111111-1111-4111-8111-111111111111";
        var archive=new StockExpectedReturnForwardArchive(new ObjectMapper(),root.toString(),runId);
        var p=prediction();
        Path path=archive.capture("SP500",p.getSignalDate(),List.of(p),List.of(),List.of());
        var saved=new ObjectMapper().readTree(Files.readAllBytes(path));
        assertEquals(runId,saved.path("runId").asText());
        assertEquals("2.5",saved.path("predictions").get(0).path("expectedExcessReturnPct").asText());
    }

    @Test void repeatedCaptureNeverOverwritesEarlierPrediction() throws Exception {
        var archive=new StockExpectedReturnForwardArchive(new ObjectMapper(),root.toString());
        var p=prediction();
        Path first=archive.capture("SP500",p.getSignalDate(),List.of(p),List.of(),List.of());
        byte[] saved=Files.readAllBytes(first);
        p.setExpectedExcessReturnPct(new BigDecimal("9.5"));
        Path second=archive.capture("SP500",p.getSignalDate(),List.of(p),List.of(),List.of());
        assertNotEquals(first,second);
        assertArrayEquals(saved,Files.readAllBytes(first));
        var json=new ObjectMapper().readTree(saved);
        assertEquals("OBSERVED_FORWARD_OUTPUT_NOT_COMPLETE_PIT_PROOF",json.path("classification").asText());
        assertEquals("EXCESS_PRICE_RETURN_20_TRADING_DAYS",json.path("displayedTarget").asText());
        assertEquals("2.5",json.path("predictions").get(0).path("expectedExcessReturnPct").asText());
        assertFalse(json.path("capturedAtUtc").asText().isBlank());
    }

    @Test void rejectsReconstructedRowsAndPathTraversalWithoutWriting() throws Exception {
        var archive=new StockExpectedReturnForwardArchive(new ObjectMapper(),root.toString());
        var p=prediction();p.setSource("RESEARCH_RECONSTRUCTED");
        assertThrows(IllegalArgumentException.class,()->archive.capture("SP500",p.getSignalDate(),List.of(p),List.of(),List.of()));
        p.setSource("LIVE_V9_SEC_SOURCE_CUTOFF_LT_20261031");
        assertThrows(IllegalArgumentException.class,()->archive.capture("../outside",p.getSignalDate(),List.of(p),List.of(),List.of()));
        try(var files=Files.list(root)){assertEquals(0,files.count());}
    }

    @Test void archiveFailureIsNotReportedAsSuccess() throws Exception {
        Path file=Files.createFile(root.resolve("not-a-directory"));
        var archive=new StockExpectedReturnForwardArchive(new ObjectMapper(),file.toString());
        var p=prediction();
        assertThrows(IllegalStateException.class,()->archive.capture("SP500",p.getSignalDate(),List.of(p),List.of(),List.of()));
    }

    @Test void refreshArchivesPersisted20dOnlyAndKeepsOtherHorizonsWorking() throws Exception {
        var mapper=mock(StockBacktestMapper.class);
        var a=prediction();a.setSignalDate(LocalDate.now());a.setModelVersion("EXPECTED_RETURN_V8_CORRECTED_20261017");
        a.setSource("LIVE_FRED_MACRO_FEATURE_ADJUSTED_EXPECTED_RETURN_V8_CUTOFF_LT_"+LocalDate.now().toString().replace("-",""));
        var b=prediction();b.setSignalDate(LocalDate.now());b.setHorizonDays(5);b.setModelVersion(a.getModelVersion());
        b.setSource(a.getSource());
        var source=List.of(a,b);var state=new ArrayList<StockExpectedReturnSnapshot>(source);
        when(mapper.findExpectedReturnSnapshots(eq("SP500"),any(),any())).thenAnswer(c->List.copyOf(state));
        var market=new StockMarketSnapshot();market.setSymbol("AAPL");market.setSnapshotDate(LocalDate.now());
        market.setMarketCapUsd(BigDecimal.TEN);market.setSharesSource("SEC_XBRL");
        when(mapper.findMarketSnapshots(eq("SP500"),any(),any())).thenReturn(List.of(market));
        doAnswer(c->{
            StockExpectedReturnSnapshot p=c.getArgument(0);
            // Simulate the DB's persisted decimal value, not the in-memory pre-write precision.
            var persisted=new ObjectMapper().convertValue(p,StockExpectedReturnSnapshot.class);
            persisted.setExpectedExcessReturnPct(new BigDecimal("2.12345678"));
            state.add(persisted);return null;
        }).when(mapper).upsertExpectedReturnSnapshot(any());
        var factory=new StaticListableBeanFactory();factory.addBean("mapper",mapper);
        var service=new StockExpectedReturnV9Service(factory.getBeanProvider(StockBacktestMapper.class),
                new StockExpectedReturnForwardArchive(new ObjectMapper(),root.toString()));
        assertEquals(2,service.refreshLatest("SP500").savedRows());
        try(var paths=Files.walk(root)){
            var path=paths.filter(Files::isRegularFile).findFirst().orElseThrow();
            var saved=new ObjectMapper().readTree(Files.readAllBytes(path));
            assertEquals(1,saved.path("predictions").size());
            assertEquals(20,saved.path("predictions").get(0).path("horizonDays").asInt());
            assertEquals("2.12345678",saved.path("predictions").get(0).path("expectedExcessReturnPct").asText());
        }
    }

    @Test void skipsStaleMarketInputsButAllowsTheFreshestNonTodaySession() throws Exception {
        var mapper=mock(StockBacktestMapper.class);
        var fresh=prediction();fresh.setSignalDate(LocalDate.now());
        fresh.setModelVersion("EXPECTED_RETURN_V8_CORRECTED_20261017");
        fresh.setSource("LIVE_FRED_MACRO_FEATURE_ADJUSTED_EXPECTED_RETURN_V8_CUTOFF_LT_"
                +LocalDate.now().toString().replace("-",""));
        var stale=new ObjectMapper().convertValue(fresh,StockExpectedReturnSnapshot.class);stale.setSymbol("STALE");
        var state=new ArrayList<StockExpectedReturnSnapshot>(List.of(fresh,stale));
        when(mapper.findExpectedReturnSnapshots(eq("SP500"),any(),any())).thenAnswer(c->List.copyOf(state));
        var freshMarket=new StockMarketSnapshot();freshMarket.setSymbol("AAPL");
        freshMarket.setSnapshotDate(LocalDate.now().minusDays(1));freshMarket.setMarketCapUsd(BigDecimal.TEN);
        freshMarket.setSharesSource("SEC_XBRL");
        var staleMarket=new ObjectMapper().convertValue(freshMarket,StockMarketSnapshot.class);
        staleMarket.setSymbol("STALE");staleMarket.setSnapshotDate(LocalDate.now().minusDays(25));
        when(mapper.findMarketSnapshots(eq("SP500"),any(),any())).thenReturn(List.of(freshMarket,staleMarket));
        doAnswer(c->{state.add(c.getArgument(0));return null;}).when(mapper).upsertExpectedReturnSnapshot(any());
        var factory=new StaticListableBeanFactory();factory.addBean("mapper",mapper);
        var service=new StockExpectedReturnV9Service(factory.getBeanProvider(StockBacktestMapper.class),
                new StockExpectedReturnForwardArchive(new ObjectMapper(),root.toString()));
        assertEquals(1,service.refreshLatest("SP500").savedRows());
        assertEquals(List.of("AAPL"),state.stream()
                .filter(p->StockExpectedReturnV9Service.MODEL_VERSION.equals(p.getModelVersion()))
                .map(StockExpectedReturnSnapshot::getSymbol).toList());
        try(var paths=Files.walk(root)){
            var path=paths.filter(Files::isRegularFile).findFirst().orElseThrow();
            var saved=new ObjectMapper().readTree(Files.readAllBytes(path));
            assertEquals(1,saved.path("predictions").size());
            assertEquals("AAPL",saved.path("predictions").get(0).path("symbol").asText());
            assertEquals(1,saved.path("marketInputs").size());
            assertEquals(freshMarket.getSnapshotDate().toString(),
                    saved.path("marketInputs").get(0).path("snapshotDate").asText());
        }
    }

    @Test void currentDayHistoricalV8CannotBeRelabeledAsForwardLive() throws Exception {
        var mapper=mock(StockBacktestMapper.class);
        var source=prediction();source.setSignalDate(LocalDate.now());
        source.setModelVersion("EXPECTED_RETURN_V8_CORRECTED_20261017");
        source.setSource("FRED_MACRO_FEATURE_ADJUSTED_EXPECTED_RETURN_V8_CUTOFF_LT_"+LocalDate.now().toString().replace("-",""));
        when(mapper.findExpectedReturnSnapshots(eq("SP500"),any(),any())).thenReturn(List.of(source));
        var factory=new StaticListableBeanFactory();factory.addBean("mapper",mapper);
        var service=new StockExpectedReturnV9Service(factory.getBeanProvider(StockBacktestMapper.class),
                new StockExpectedReturnForwardArchive(new ObjectMapper(),root.toString()));
        assertThrows(IllegalStateException.class,()->service.refreshLatest("SP500"));
        try(var paths=Files.list(root)){assertEquals(0,paths.count());}
        verify(mapper,never()).upsertExpectedReturnSnapshot(any());
    }

    private static StockExpectedReturnSnapshot prediction(){
        var p=new StockExpectedReturnSnapshot();p.setIndexCode("SP500");p.setSignalDate(LocalDate.of(2026,10,31));
        p.setSymbol("AAPL");p.setHorizonDays(20);p.setModelVersion(StockExpectedReturnV9Service.MODEL_VERSION);
        p.setSource("LIVE_V9_SEC_SOURCE_CUTOFF_LT_20261031");p.setExpectedExcessReturnPct(new BigDecimal("2.5"));return p;
    }
}
