package com.kingyurina.demo.stock;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class StockPointInTimeImportBatchRunner implements ApplicationRunner {

    private static final int MAX_RANGE_DAYS = 10_000;

    private final boolean enabled;
    private final boolean exitOnComplete;
    private final String defaultIndexCode;
    private final String membershipCsvPath;
    private final String sharesCsvPath;
    private final boolean expandOpenEndedRanges;
    private final String openEndedToDate;
    private final ObjectProvider<StockBacktestMapper> mapperProvider;
    private final ConfigurableApplicationContext applicationContext;

    public StockPointInTimeImportBatchRunner(
            @Value("${app.batch.pit-import.enabled:false}") boolean enabled,
            @Value("${app.batch.pit-import.exit-on-complete:false}") boolean exitOnComplete,
            @Value("${app.batch.pit-import.index-code:SP500}") String defaultIndexCode,
            @Value("${app.batch.pit-import.membership-csv-path:}") String membershipCsvPath,
            @Value("${app.batch.pit-import.shares-csv-path:}") String sharesCsvPath,
            @Value("${app.batch.pit-import.expand-open-ended-ranges:false}") boolean expandOpenEndedRanges,
            @Value("${app.batch.pit-import.open-ended-to-date:}") String openEndedToDate,
            ObjectProvider<StockBacktestMapper> mapperProvider,
            ConfigurableApplicationContext applicationContext) {
        this.enabled = enabled;
        this.exitOnComplete = exitOnComplete;
        this.defaultIndexCode = defaultIndexCode == null || defaultIndexCode.isBlank()
                ? "SP500" : defaultIndexCode.trim();
        this.membershipCsvPath = membershipCsvPath;
        this.sharesCsvPath = sharesCsvPath;
        this.expandOpenEndedRanges = expandOpenEndedRanges;
        this.openEndedToDate = openEndedToDate;
        this.mapperProvider = mapperProvider;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }
        int exitCode = 0;
        try {
            StockBacktestMapper mapper = mapperProvider.getIfAvailable();
            if (mapper == null) {
                throw new IllegalStateException("StockBacktestMapper is not available.");
            }
            ImportResult membershipRows = importMembership(mapper);
            ImportResult sharesRows = importShares(mapper);
            System.out.println("Stock PIT import indexCode=" + defaultIndexCode
                    + ", membershipRows=" + membershipRows.savedRows()
                    + ", membershipSkipped=" + membershipRows.skippedRows()
                    + ", sharesRows=" + sharesRows.savedRows()
                    + ", sharesSkipped=" + sharesRows.skippedRows());
        } catch (RuntimeException | IOException ex) {
            exitCode = 1;
            throw new IllegalStateException("Stock PIT import failed: " + ex.getMessage(), ex);
        } finally {
            if (exitOnComplete) {
                int code = exitCode;
                Thread shutdown = new Thread(() -> System.exit(SpringApplication.exit(applicationContext, () -> code)));
                shutdown.setDaemon(false);
                shutdown.start();
            }
        }
    }

    private ImportResult importMembership(StockBacktestMapper mapper) throws IOException {
        if (membershipCsvPath == null || membershipCsvPath.isBlank()) {
            return ImportResult.empty();
        }
        int saved = 0;
        int skipped = 0;
        for (Map<String, String> row : readCsv(Path.of(membershipCsvPath.trim()))) {
            String symbol = value(row, "symbol", "ticker");
            DateRange range = dateRange(row);
            if (symbol == null || range.startDate() == null) {
                skipped++;
                continue;
            }
            List<LocalDate> dates = expandDates(range.startDate(), range.endDate());
            if (dates.isEmpty()) {
                skipped++;
                continue;
            }
            for (LocalDate date : dates) {
                StockIndexMembershipSnapshot snapshot = new StockIndexMembershipSnapshot();
                snapshot.setIndexCode(defaulted(value(row, "index_code", "index"), defaultIndexCode));
                snapshot.setSymbol(symbol.toUpperCase(Locale.ROOT));
                snapshot.setSnapshotDate(date);
                snapshot.setSector(value(row, "sector", "gics_sector", "gics_sector_name"));
                snapshot.setIndustry(value(row, "industry", "gics_industry", "gics_industry_name",
                        "sub_industry", "gics_sub_industry", "gics_sub_industry_name"));
                snapshot.setExchange(value(row, "exchange"));
                snapshot.setMember(booleanValue(value(row, "is_member", "member", "current_member"), true));
                snapshot.setSource(defaulted(value(row, "source"), "CSV_IMPORT_MEMBERSHIP_PIT"));
                mapper.upsertIndexMembershipSnapshot(snapshot);
                saved++;
            }
        }
        return new ImportResult(saved, skipped);
    }

    private ImportResult importShares(StockBacktestMapper mapper) throws IOException {
        if (sharesCsvPath == null || sharesCsvPath.isBlank()) {
            return ImportResult.empty();
        }
        int saved = 0;
        int skipped = 0;
        for (Map<String, String> row : readCsv(Path.of(sharesCsvPath.trim()))) {
            String symbol = value(row, "symbol", "ticker");
            DateRange range = dateRange(row);
            BigDecimal shares = decimalValue(row, "shares_outstanding", "shares", "total_shares");
            if (shares == null) {
                shares = decimalValue(row, "shares_outstanding_millions", "shares_millions",
                        "share_outstanding_million");
                if (shares != null) {
                    shares = shares.multiply(BigDecimal.valueOf(1_000_000L));
                }
            }
            if (symbol == null || range.startDate() == null || shares == null || shares.compareTo(BigDecimal.ZERO) <= 0) {
                skipped++;
                continue;
            }
            List<LocalDate> dates = expandDates(range.startDate(), range.endDate());
            if (dates.isEmpty()) {
                skipped++;
                continue;
            }
            for (LocalDate date : dates) {
                StockSharesOutstandingSnapshot snapshot = new StockSharesOutstandingSnapshot();
                snapshot.setIndexCode(defaulted(value(row, "index_code", "index"), defaultIndexCode));
                snapshot.setSymbol(symbol.toUpperCase(Locale.ROOT));
                snapshot.setSnapshotDate(date);
                snapshot.setSharesOutstanding(shares);
                snapshot.setSource(defaulted(value(row, "source"), "CSV_IMPORT_SHARES_PIT"));
                mapper.upsertSharesOutstandingSnapshot(snapshot);
                saved++;
            }
        }
        return new ImportResult(saved, skipped);
    }

    private static List<Map<String, String>> readCsv(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        if (lines.isEmpty()) {
            return List.of();
        }
        List<String> headers = parseCsvLine(lines.get(0)).stream()
                .map(StockPointInTimeImportBatchRunner::normalizeHeader)
                .toList();
        List<Map<String, String>> rows = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line == null || line.isBlank()) {
                continue;
            }
            List<String> values = parseCsvLine(line);
            Map<String, String> row = new HashMap<>();
            for (int j = 0; j < headers.size() && j < values.size(); j++) {
                row.put(headers.get(j), values.get(j).trim());
            }
            rows.add(row);
        }
        return rows;
    }

    private static List<String> parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (ch == ',' && !quoted) {
                values.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        values.add(current.toString());
        return values;
    }

    private static String value(Map<String, String> row, String... keys) {
        for (String key : keys) {
            String value = row.get(normalizeHeader(key));
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private static LocalDate dateValue(Map<String, String> row, String... keys) {
        String value = value(row, keys);
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private DateRange dateRange(Map<String, String> row) {
        LocalDate explicitSnapshotDate = dateValue(row, "snapshot_date", "date");
        LocalDate effectiveDate = dateValue(row, "effective_date");
        LocalDate startDate = dateValue(row, "start_date", "from_date", "valid_from");
        LocalDate endDate = dateValue(row, "end_date", "to_date", "valid_to", "through_date");
        if (startDate != null) {
            return new DateRange(startDate, effectiveEnd(startDate, endDate));
        }
        if (effectiveDate != null) {
            return new DateRange(effectiveDate, endDate == null ? effectiveDate : endDate);
        }
        if (explicitSnapshotDate != null) {
            return new DateRange(explicitSnapshotDate, explicitSnapshotDate);
        }
        return new DateRange(null, null);
    }

    private LocalDate effectiveEnd(LocalDate startDate, LocalDate endDate) {
        if (endDate != null) {
            return endDate;
        }
        if (!expandOpenEndedRanges) {
            return startDate;
        }
        return parseDate(openEndedToDate, LocalDate.now());
    }

    private static LocalDate parseDate(String value, LocalDate fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (RuntimeException ex) {
            return fallback;
        }
    }

    private static BigDecimal decimalValue(Map<String, String> row, String... keys) {
        String value = value(row, keys);
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(value.replace(",", ""));
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static List<LocalDate> expandDates(LocalDate startDate, LocalDate endDate) {
        LocalDate effectiveEnd = endDate == null ? startDate : endDate;
        if (effectiveEnd.isBefore(startDate)) {
            return List.of();
        }
        long days = java.time.temporal.ChronoUnit.DAYS.between(startDate, effectiveEnd);
        if (days > MAX_RANGE_DAYS) {
            return List.of();
        }
        List<LocalDate> dates = new ArrayList<>();
        for (LocalDate date = startDate; !date.isAfter(effectiveEnd); date = date.plusDays(1)) {
            dates.add(date);
        }
        return dates;
    }

    private static Boolean booleanValue(String value, boolean fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return "1".equals(normalized) || "true".equals(normalized) || "y".equals(normalized)
                || "yes".equals(normalized);
    }

    private static String defaulted(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String normalizeHeader(String value) {
        return value == null ? "" : value.replace("\uFEFF", "")
                .trim()
                .toLowerCase(Locale.ROOT)
                .replace("-", "_")
                .replace(" ", "_");
    }

    private record ImportResult(int savedRows, int skippedRows) {
        private static ImportResult empty() {
            return new ImportResult(0, 0);
        }
    }

    private record DateRange(LocalDate startDate, LocalDate endDate) {
    }
}
