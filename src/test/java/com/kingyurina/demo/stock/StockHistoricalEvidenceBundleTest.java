package com.kingyurina.demo.stock;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

class StockHistoricalEvidenceBundleTest {
    @TempDir Path directory;
    private static final String HASH = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
    private static final String EVENT = """
            {"sourceId":"issuer","symbol":"APH","action":"SPLIT",
             "effectiveDate":"2026-09-02","dateBasis":"DISTRIBUTION_DATE","numerator":2,"denominator":1,
             "ratioBasis":"OFFICIAL_SHARE_RATIO"}
            """;
    private Path bundle(String event, String sourcePath, String hash, String coverage) throws Exception {
        Files.writeString(directory.resolve("raw.txt"), "abc");
        Path result = directory.resolve("bundle.json");
        Files.writeString(result, """
                {"sources":[{"id":"issuer","url":"https://example.org/announcement",
                 "publisher":"Issuer","publishedDate":"2026-08-06","retrievedAt":"2026-10-17T00:00:00Z",
                 "sha256":"%s","path":"%s"}],"events":[%s],"coverage":"%s"}
                """.formatted(hash, sourcePath, event, coverage));
        return result;
    }
    private StockHistoricalEvidenceBundle read(Path path) {
        return StockHistoricalEvidenceBundle.read(path, new ObjectMapper());
    }
    @Test void preservesDistributionDateWithoutPretendingItIsAnExDate() throws Exception {
        var result = read(bundle(EVENT, "raw.txt", HASH, "PARTIAL_OFFICIAL_EVENTS"));
        assertEquals("DISTRIBUTION_DATE", result.events().getFirst().dateBasis());
        assertEquals("2026-09-02", result.events().getFirst().effectiveDate());
        assertEquals("PARTIAL_OFFICIAL_EVENTS", result.coverage());
        assertEquals(1, result.sources().size());
    }
    @Test void rejectsTamperedSourceBeforeAnyImport() throws Exception {
        Path path = bundle(EVENT, "raw.txt", "0".repeat(64), "PARTIAL_OFFICIAL_EVENTS");
        var error = assertThrows(IllegalArgumentException.class, () -> read(path));
        assertTrue(error.getMessage().contains("hash"));
    }
    @Test void rejectsSourceOutsideBundleDirectory() throws Exception {
        Path path = bundle(EVENT, "../outside.txt", HASH, "PARTIAL_OFFICIAL_EVENTS");
        assertThrows(IllegalArgumentException.class, () -> read(path));
    }
    @Test void rejectsUnreferencedSourceAndInvalidSplitRatio() throws Exception {
        Path path = bundle(EVENT.replace("issuer", "unknown"), "raw.txt", HASH, "PARTIAL_OFFICIAL_EVENTS");
        assertThrows(IllegalArgumentException.class, () -> read(path));
        Path invalid = bundle(EVENT.replace("\"denominator\":1", "\"denominator\":0"), "raw.txt", HASH, "PARTIAL_OFFICIAL_EVENTS");
        assertThrows(IllegalArgumentException.class, () -> read(invalid));
    }
    @Test void doesNotAcceptAClaimOfCompletePitCoverage() throws Exception {
        Path path = bundle(EVENT, "raw.txt", HASH, "OFFICIAL_PIT");
        assertThrows(IllegalArgumentException.class, () -> read(path));
    }
    @Test void acceptsMembershipChangesButRequiresMarketTiming() throws Exception {
        String event = """
                {"sourceId":"issuer","indexCode":"SP500","symbol":"ABC","action":"ADD",
                 "effectiveDate":"2026-09-02","timing":"BEFORE_OPEN"}
                """;
        var result = read(bundle(event, "raw.txt", HASH, "PARTIAL_OFFICIAL_CHANGE_EVENTS"));
        assertEquals("SP500", result.events().getFirst().indexCode());
        assertEquals("BEFORE_OPEN", result.events().getFirst().timing());
        Path invalid = bundle(event.replace("BEFORE_OPEN", ""), "raw.txt", HASH, "PARTIAL_OFFICIAL_CHANGE_EVENTS");
        assertThrows(IllegalArgumentException.class, () -> read(invalid));
    }
    @Test void identicalBundleHasStableKeysAndDuplicateEventsAreRejected() throws Exception {
        Path path = bundle(EVENT, "raw.txt", HASH, "PARTIAL_OFFICIAL_EVENTS");
        assertEquals(read(path), read(path));
        Path duplicate = bundle(EVENT + "," + EVENT, "raw.txt", HASH, "PARTIAL_OFFICIAL_EVENTS");
        assertThrows(IllegalArgumentException.class, () -> read(duplicate));
    }
    @Test void dividendRequiresCurrencyAndExplicitAmountBasis() throws Exception {
        String event = """
                {"sourceId":"issuer","symbol":"APH","action":"DIVIDEND","effectiveDate":"2026-10-14",
                 "dateBasis":"PAYMENT_DATE","amount":0.125,"currency":"USD","amountBasis":"POST_SPLIT_PER_SHARE"}
                """;
        assertEquals("0.125", read(bundle(event, "raw.txt", HASH, "PARTIAL_OFFICIAL_EVENTS")).events().getFirst().amount());
        Path path = bundle(event.replace("USD", ""), "raw.txt", HASH, "PARTIAL_OFFICIAL_EVENTS");
        assertThrows(IllegalArgumentException.class, () -> read(path));
    }
    @Test void providerAdjustmentFactorIsNotACertifiedDeliveredShareRatio() throws Exception {
        Path path = bundle(EVENT, "raw.txt", HASH, "PROVIDER_REPORTED_EVENTS");
        assertThrows(IllegalArgumentException.class, () -> read(path));
        var result = read(bundle(EVENT.replace("OFFICIAL_SHARE_RATIO", "PROVIDER_ADJUSTMENT_FACTOR_UNVERIFIED"),
                "raw.txt", HASH, "PROVIDER_REPORTED_EVENTS"));
        assertEquals("PROVIDER_ADJUSTMENT_FACTOR_UNVERIFIED", result.events().getFirst().ratioBasis());
    }
    @Test void retainsOriginalAnnouncementLinkAndPriceBasisWarnings() throws Exception {
        String event = EVENT.replace("\"ratioBasis\":", "\"announcementSourceId\":\"issuer\",\"priceBasis\":\"ALREADY_ADJUSTED_DO_NOT_REAPPLY\",\"ratioBasis\":");
        var result = read(bundle(event, "raw.txt", HASH, "PARTIAL_OFFICIAL_EVENTS"));
        String saved = new ObjectMapper().writeValueAsString(result);
        assertTrue(saved.contains("DO_NOT_REAPPLY"));
        assertTrue(saved.contains("announcementSourceId"));
    }
    @Test void rejectsAnAnnouncementLinkToMissingEvidence() throws Exception {
        String event = EVENT.replace("\"ratioBasis\":", "\"announcementSourceId\":\"missing\",\"ratioBasis\":");
        Path path = bundle(event, "raw.txt", HASH, "PARTIAL_OFFICIAL_EVENTS");
        assertThrows(IllegalArgumentException.class, () -> read(path));
    }
    @Test void rejectsMultipleIdsAliasingOneRawSource() throws Exception {
        Path path = bundle(EVENT, "raw.txt", HASH, "PARTIAL_OFFICIAL_EVENTS");
        var mapper = new ObjectMapper();
        var tree = mapper.readTree(Files.readString(path));
        var sources = (tools.jackson.databind.node.ArrayNode) tree.get("sources");
        var alias = (tools.jackson.databind.node.ObjectNode) sources.get(0).deepCopy();
        alias.put("id", "alias"); alias.put("publisher", "Conflicting publisher"); sources.add(alias);
        Files.writeString(path, mapper.writeValueAsString(tree));
        assertThrows(IllegalArgumentException.class, () -> read(path));
    }
}
