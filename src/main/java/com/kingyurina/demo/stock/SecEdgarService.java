package com.kingyurina.demo.stock;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class SecEdgarService {

    private static final String PROVIDER = "SEC_EDGAR";
    private static final String COMPANY_TICKERS_ENDPOINT = "/files/company_tickers.json";
    private static final String COMPANY_FACTS_ENDPOINT = "/api/xbrl/companyfacts";
    private static final int DB_BATCH_SIZE = 200;
    private static final List<String> PERIODIC_FORMS = List.of(
            "10-K", "10-Q", "10-K/A", "10-Q/A", "20-F", "20-F/A", "40-F", "40-F/A");
    private static final Map<String, List<String>> TARGET_CONCEPTS = targetConcepts();

    private final SecEdgarProperties properties;
    private final SecEdgarClientService client;
    private final StockCacheService cacheService;
    private final ObjectMapper objectMapper;
    private final SecFinancialStandardService financialStandardService;
    private final ObjectProvider<SecCompanyMapper> companyMapper;
    private final ObjectProvider<SecCompanyFactMapper> factMapper;

    public SecEdgarService(SecEdgarProperties properties,
            SecEdgarClientService client,
            StockCacheService cacheService,
            ObjectMapper objectMapper,
            SecFinancialStandardService financialStandardService,
            ObjectProvider<SecCompanyMapper> companyMapper,
            ObjectProvider<SecCompanyFactMapper> factMapper) {
        this.properties = properties;
        this.client = client;
        this.cacheService = cacheService;
        this.objectMapper = objectMapper;
        this.financialStandardService = financialStandardService;
        this.companyMapper = companyMapper;
        this.factMapper = factMapper;
    }

    public SecEdgarSyncSummary syncSymbols(List<String> requestedSymbols, int limit) {
        if (requestedSymbols == null || requestedSymbols.isEmpty()) {
            return new SecEdgarSyncSummary(0, 0, 0, 0, 0, 0, 0, "No target symbols.");
        }

        SecCompanyMapper companies = requireMapper(companyMapper, "SecCompanyMapper");
        SecCompanyFactMapper facts = requireMapper(factMapper, "SecCompanyFactMapper");

        List<String> symbols = requestedSymbols.stream()
                .filter(StringUtils::hasText)
                .map(SecEdgarService::normalizeSymbol)
                .distinct()
                .limit(Math.max(1, limit))
                .toList();
        if (symbols.isEmpty()) {
            return new SecEdgarSyncSummary(0, 0, 0, 0, 0, 0, 0, "No valid target symbols.");
        }

        MappingResult mapping = syncCompanyTickerMapping(symbols, companies);
        List<String> staleSymbols = companies.findSymbolsWithStaleFacts(
                symbols, properties.companyFactsCacheHours(), Math.max(1, limit));

        int requested = 0;
        int success = 0;
        int fail = 0;
        int factCount = 0;
        int standardCount = 0;
        String message = null;
        for (String symbol : staleSymbols) {
            requested++;
            SecCompany company = companies.findBySymbol(symbol);
            if (company == null || !StringUtils.hasText(company.getCik())) {
                fail++;
                message = "Missing SEC CIK for " + symbol + ".";
                continue;
            }

            FinnhubResponse response = client.companyFacts(company.getCik());
            cacheService.logApiCall(PROVIDER, COMPANY_FACTS_ENDPOINT, symbol, response);
            sleepBetweenRequests();
            if (!response.success()) {
                fail++;
                message = "SEC companyfacts failed at " + symbol + ": " + response.errorMessage();
                continue;
            }

            List<SecCompanyFact> parsedFacts = parseFacts(symbol, company.getCik(), response.body());
            if (parsedFacts.isEmpty()) {
                fail++;
                message = "SEC companyfacts had no target concepts for " + symbol + ".";
                continue;
            }

            saveFacts(facts, parsedFacts);
            factCount += parsedFacts.size();
            standardCount += financialStandardService.rebuildFromFacts(symbol, company.getCik(), parsedFacts);
            success++;
        }

        for (String symbol : financialStandardService.findSymbolsMissingStandard(symbols, Math.max(1, limit))) {
            if (staleSymbols.contains(symbol)) {
                continue;
            }
            standardCount += financialStandardService.rebuildFromDatabase(symbol);
        }

        int skipped = symbols.size() - requested;
        String summary = message == null
                ? "SEC EDGAR sync completed."
                : message;
        summary += " Mapped " + mapping.mappedCount() + " symbols, missing " + mapping.missingCount()
                + ", saved " + factCount + " facts, standardized " + standardCount + " rows.";
        return new SecEdgarSyncSummary(requested, success, fail, Math.max(0, skipped),
                mapping.mappedCount(), factCount, standardCount, summary);
    }

    private MappingResult syncCompanyTickerMapping(List<String> symbols, SecCompanyMapper companies) {
        FinnhubResponse response = client.companyTickers();
        cacheService.logApiCall(PROVIDER, COMPANY_TICKERS_ENDPOINT, null, response);
        if (!response.success()) {
            throw new IllegalStateException("SEC company ticker mapping failed: " + response.errorMessage());
        }

        Map<String, SecCompany> byTicker = parseCompanyTickerMapping(response.body());
        int mapped = 0;
        int missing = 0;
        LocalDateTime fetchedAt = LocalDateTime.now();
        for (String symbol : symbols) {
            SecCompany company = byTicker.get(tickerKey(symbol));
            if (company == null) {
                missing++;
                continue;
            }
            company.setSymbol(symbol);
            company.setFetchedAt(fetchedAt);
            companies.upsert(company);
            mapped++;
        }
        return new MappingResult(mapped, missing);
    }

    private Map<String, SecCompany> parseCompanyTickerMapping(String json) {
        JsonNode root = readTree(json);
        if (root == null || !root.isObject()) {
            return Map.of();
        }

        Map<String, SecCompany> byTicker = new LinkedHashMap<>();
        for (Entry<String, JsonNode> entry : root.properties()) {
            JsonNode item = entry.getValue();
            String ticker = text(item, "ticker");
            String cik = paddedCik(text(item, "cik_str"));
            if (!StringUtils.hasText(ticker) || !StringUtils.hasText(cik)) {
                continue;
            }

            SecCompany company = new SecCompany();
            company.setCik(cik);
            company.setTicker(ticker);
            company.setCompanyName(text(item, "title"));
            company.setSource("SEC_COMPANY_TICKERS");
            byTicker.put(tickerKey(ticker), company);
        }
        return byTicker;
    }

    private List<SecCompanyFact> parseFacts(String symbol, String cik, String json) {
        JsonNode root = readTree(json);
        JsonNode factsRoot = root == null ? null : root.get("facts");
        if (factsRoot == null || !factsRoot.isObject()) {
            return List.of();
        }

        List<SecCompanyFact> facts = new ArrayList<>();
        LocalDateTime fetchedAt = LocalDateTime.now();
        Map<String, List<String>> targetConcepts = properties.fullConceptEnabled()
                ? allConcepts(factsRoot)
                : TARGET_CONCEPTS;
        for (Entry<String, List<String>> taxonomyEntry : targetConcepts.entrySet()) {
            String taxonomy = taxonomyEntry.getKey();
            JsonNode taxonomyNode = factsRoot.get(taxonomy);
            if (taxonomyNode == null || !taxonomyNode.isObject()) {
                continue;
            }
            for (String concept : taxonomyEntry.getValue()) {
                JsonNode conceptNode = taxonomyNode.get(concept);
                JsonNode unitsNode = conceptNode == null ? null : conceptNode.get("units");
                if (unitsNode == null || !unitsNode.isObject()) {
                    continue;
                }
                collectConceptFacts(facts, symbol, cik, taxonomy, concept, unitsNode, fetchedAt);
            }
        }
        return facts;
    }

    private void collectConceptFacts(List<SecCompanyFact> results,
            String symbol,
            String cik,
            String taxonomy,
            String concept,
            JsonNode unitsNode,
            LocalDateTime fetchedAt) {
        for (Entry<String, JsonNode> unitEntry : unitsNode.properties()) {
            String unit = unitEntry.getKey();
            JsonNode values = unitEntry.getValue();
            if (values == null || !values.isArray()) {
                continue;
            }

            int maxFacts = Math.max(1, properties.maxFactsPerConcept());
            int start = Math.max(0, values.size() - maxFacts);
            for (int index = start; index < values.size(); index++) {
                JsonNode item = values.get(index);
                String accessionNumber = text(item, "accn");
                BigDecimal value = decimal(item, "val");
                String form = text(item, "form");
                if (!StringUtils.hasText(accessionNumber) || value == null || !isPeriodicForm(form)) {
                    continue;
                }

                SecCompanyFact fact = new SecCompanyFact();
                fact.setSymbol(symbol);
                fact.setCik(cik);
                fact.setTaxonomy(taxonomy);
                fact.setConcept(concept);
                fact.setUnit(unit);
                fact.setFiscalYear(integer(item, "fy"));
                fact.setFiscalPeriod(text(item, "fp"));
                fact.setForm(form);
                fact.setFiledAt(localDate(item, "filed"));
                LocalDate endDate = localDate(item, "end");
                LocalDate startDate = localDate(item, "start");
                fact.setStartDate(startDate == null ? endDate : startDate);
                fact.setEndDate(endDate);
                fact.setAccessionNumber(accessionNumber);
                fact.setFrame(text(item, "frame"));
                fact.setValue(value);
                fact.setRawJson(item.toString());
                fact.setFetchedAt(fetchedAt);
                results.add(fact);
            }
        }
    }

    private void saveFacts(SecCompanyFactMapper facts, List<SecCompanyFact> parsedFacts) {
        for (int start = 0; start < parsedFacts.size(); start += DB_BATCH_SIZE) {
            int end = Math.min(parsedFacts.size(), start + DB_BATCH_SIZE);
            facts.upsertBatch(parsedFacts.subList(start, end));
        }
    }

    private Map<String, List<String>> allConcepts(JsonNode factsRoot) {
        Map<String, List<String>> concepts = new LinkedHashMap<>();
        if (factsRoot == null || !factsRoot.isObject()) {
            return concepts;
        }
        for (Entry<String, JsonNode> taxonomyEntry : factsRoot.properties()) {
            JsonNode taxonomyNode = taxonomyEntry.getValue();
            if (taxonomyNode == null || !taxonomyNode.isObject()) {
                continue;
            }
            List<String> taxonomyConcepts = new ArrayList<>();
            for (Entry<String, JsonNode> conceptEntry : taxonomyNode.properties()) {
                taxonomyConcepts.add(conceptEntry.getKey());
            }
            concepts.put(taxonomyEntry.getKey(), taxonomyConcepts);
        }
        return concepts;
    }

    private void sleepBetweenRequests() {
        if (properties.requestDelayMillis() <= 0) {
            return;
        }
        try {
            Thread.sleep(properties.requestDelayMillis());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("SEC EDGAR sync interrupted.", ex);
        }
    }

    private JsonNode readTree(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to parse SEC EDGAR JSON.", ex);
        }
    }

    private static String normalizeSymbol(String symbol) {
        return symbol.trim().toUpperCase(Locale.ROOT);
    }

    private static String tickerKey(String ticker) {
        if (!StringUtils.hasText(ticker)) {
            return "";
        }
        return ticker.trim().toUpperCase(Locale.ROOT).replace('.', '-');
    }

    private static String paddedCik(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            long cik = Long.parseLong(value.trim());
            return String.format(Locale.ROOT, "%010d", cik);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String text(JsonNode node, String fieldName) {
        JsonNode value = node == null ? null : node.get(fieldName);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return StringUtils.hasText(text) ? text : null;
    }

    private static Integer integer(JsonNode node, String fieldName) {
        JsonNode value = node == null ? null : node.get(fieldName);
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isNumber()) {
            return value.intValue();
        }
        String text = value.asText();
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return Integer.valueOf(text);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static BigDecimal decimal(JsonNode node, String fieldName) {
        JsonNode value = node == null ? null : node.get(fieldName);
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isNumber()) {
            return value.decimalValue();
        }
        String text = value.asText();
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static LocalDate localDate(JsonNode node, String fieldName) {
        String value = text(node, fieldName);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private static boolean isPeriodicForm(String form) {
        return StringUtils.hasText(form) && PERIODIC_FORMS.contains(form.toUpperCase(Locale.ROOT));
    }

    private static <T> T requireMapper(ObjectProvider<T> provider, String name) {
        T mapper = provider.getIfAvailable();
        if (mapper == null) {
            throw new IllegalStateException(name + " is not available. Run with the mariadb profile.");
        }
        return mapper;
    }

    private static Map<String, List<String>> targetConcepts() {
        Map<String, List<String>> concepts = new LinkedHashMap<>();
        concepts.put("us-gaap", List.of(
                "Revenues",
                "RevenueFromContractWithCustomerExcludingAssessedTax",
                "SalesRevenueNet",
                "CostOfRevenue",
                "CostOfGoodsAndServicesSold",
                "GrossProfit",
                "OperatingIncomeLoss",
                "IncomeLossFromContinuingOperationsBeforeIncomeTaxesExtraordinaryItemsNoncontrollingInterest",
                "NetIncomeLoss",
                "NetIncomeLossAvailableToCommonStockholdersBasic",
                "EarningsPerShareBasic",
                "EarningsPerShareDiluted",
                "WeightedAverageNumberOfDilutedSharesOutstanding",
                "Assets",
                "AssetsCurrent",
                "Liabilities",
                "LiabilitiesCurrent",
                "StockholdersEquity",
                "StockholdersEquityIncludingPortionAttributableToNoncontrollingInterest",
                "CashAndCashEquivalentsAtCarryingValue",
                "NetCashProvidedByUsedInOperatingActivities",
                "NetCashProvidedByUsedInOperatingActivitiesContinuingOperations",
                "PaymentsToAcquirePropertyPlantAndEquipment",
                "LongTermDebtAndFinanceLeaseObligationsCurrent",
                "LongTermDebtAndFinanceLeaseObligationsNoncurrent"));
        concepts.put("dei", List.of("EntityCommonStockSharesOutstanding"));
        return concepts;
    }

    private record MappingResult(int mappedCount, int missingCount) {
    }

    public record SecEdgarSyncSummary(int requestedCount,
            int successCount,
            int failCount,
            int skippedCount,
            int mappedCount,
            int factCount,
            int standardCount,
            String message) {
    }
}
