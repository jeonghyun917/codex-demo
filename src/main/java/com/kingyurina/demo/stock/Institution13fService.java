package com.kingyurina.demo.stock;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.DocumentBuilderFactory;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class Institution13fService {

    private static final String PROVIDER = "SEC_EDGAR";
    private static final String SUBMISSIONS_ENDPOINT = "/submissions";
    private static final String FILING_INDEX_ENDPOINT = "/Archives/edgar/data/index.json";
    private static final String INFORMATION_TABLE_ENDPOINT = "/Archives/edgar/data/information-table";
    private static final int DB_BATCH_SIZE = 200;

    private final SecEdgarProperties properties;
    private final SecEdgarClientService client;
    private final StockCacheService cacheService;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<Institution13fMapper> mapperProvider;

    public Institution13fService(SecEdgarProperties properties,
            SecEdgarClientService client,
            StockCacheService cacheService,
            ObjectMapper objectMapper,
            ObjectProvider<Institution13fMapper> mapperProvider) {
        this.properties = properties;
        this.client = client;
        this.cacheService = cacheService;
        this.objectMapper = objectMapper;
        this.mapperProvider = mapperProvider;
    }

    public SyncSummary syncLatest(int managerLimit, int filingsPerManager) {
        Institution13fMapper mapper = requireMapper();
        List<Institution13fManager> managers = mapper.findActiveManagers(Math.max(1, managerLimit));
        if (managers.isEmpty()) {
            return new SyncSummary(0, 0, 0, 0, 0, "No active 13F managers.");
        }

        Map<String, String> symbolByName = symbolNameMap(mapper.findNameMappings());
        int filings = 0;
        int success = 0;
        int fail = 0;
        int holdings = 0;
        Set<LocalDate> quarters = new LinkedHashSet<>();
        String lastIssue = null;

        for (Institution13fManager manager : managers) {
            FinnhubResponse submissions = client.submissions(manager.getCik());
            cacheService.logApiCall(PROVIDER, SUBMISSIONS_ENDPOINT, manager.getCik(), submissions);
            sleepBetweenRequests();
            if (!submissions.success()) {
                fail++;
                lastIssue = "submissions failed for " + manager.getShortName() + ": " + submissions.errorMessage();
                continue;
            }

            List<Institution13fFiling> discovered = discoverFilings(manager, submissions.body(), filingsPerManager);
            for (Institution13fFiling filing : discovered) {
                filings++;
                mapper.upsertFiling(filing);
                FinnhubResponse index = client.filingIndex(manager.getCik(), filing.getAccessionNumber());
                cacheService.logApiCall(PROVIDER, FILING_INDEX_ENDPOINT, manager.getCik(), index);
                sleepBetweenRequests();
                if (!index.success()) {
                    fail++;
                    lastIssue = "filing index failed for " + filing.getAccessionNumber() + ": " + index.errorMessage();
                    mapper.updateFilingStatus(filing.getAccessionNumber(), "INDEX_FAILED", null);
                    continue;
                }

                String tableUrl = informationTableUrl(manager.getCik(), filing.getAccessionNumber(), index.body());
                if (!StringUtils.hasText(tableUrl)) {
                    fail++;
                    lastIssue = "information table not found for " + filing.getAccessionNumber();
                    mapper.updateFilingStatus(filing.getAccessionNumber(), "TABLE_MISSING", null);
                    continue;
                }

                FinnhubResponse table = client.informationTable(tableUrl);
                cacheService.logApiCall(PROVIDER, INFORMATION_TABLE_ENDPOINT, manager.getCik(), table);
                sleepBetweenRequests();
                if (!table.success()) {
                    fail++;
                    lastIssue = "information table failed for " + filing.getAccessionNumber() + ": " + table.errorMessage();
                    mapper.updateFilingStatus(filing.getAccessionNumber(), "TABLE_FAILED", tableUrl);
                    continue;
                }

                List<Institution13fHolding> parsed = parseInformationTable(filing, table.body(), symbolByName);
                mapper.deleteHoldings(filing.getAccessionNumber());
                saveHoldings(mapper, parsed);
                mapper.updateFilingStatus(filing.getAccessionNumber(), "COMPLETED", tableUrl);
                holdings += parsed.size();
                quarters.add(filing.getReportQuarter());
                success++;
            }
        }

        int aggregateRows = 0;
        for (LocalDate quarter : quarters) {
            mapper.deleteQuarterFlows(quarter);
            aggregateRows += mapper.rebuildQuarter(quarter);
        }
        String message = lastIssue == null ? "13F sync completed." : lastIssue;
        message += " managers=" + managers.size() + ", filings=" + filings + ", holdings=" + holdings
                + ", aggregateRows=" + aggregateRows + ".";
        return new SyncSummary(filings, success, fail, holdings, aggregateRows, message);
    }

    private List<Institution13fFiling> discoverFilings(Institution13fManager manager, String json, int limit) {
        JsonNode recent = readTree(json).path("filings").path("recent");
        JsonNode forms = recent.path("form");
        JsonNode accessions = recent.path("accessionNumber");
        JsonNode filingDates = recent.path("filingDate");
        JsonNode reportDates = recent.path("reportDate");
        List<Institution13fFiling> filings = new ArrayList<>();
        int max = Math.min(forms.size(), accessions.size());
        for (int index = 0; index < max; index++) {
            String form = forms.get(index).asText("");
            if (!form.startsWith("13F-HR")) {
                continue;
            }
            LocalDate reportDate = localDate(reportDates, index);
            LocalDate reportQuarter = quarterEnd(reportDate);
            if (reportQuarter == null) {
                continue;
            }
            Institution13fFiling filing = new Institution13fFiling();
            filing.setAccessionNumber(accessions.get(index).asText());
            filing.setManagerCik(manager.getCik());
            filing.setManagerName(firstText(manager.getShortName(), manager.getManagerName()));
            filing.setFormType(form);
            filing.setReportQuarter(reportQuarter);
            filing.setReportDate(reportDate);
            filing.setFilingDate(localDate(filingDates, index));
            filing.setStatus("DISCOVERED");
            filing.setRawJson(recent.toString());
            filing.setFetchedAt(LocalDateTime.now());
            filings.add(filing);
            if (filings.size() >= Math.max(1, limit)) {
                break;
            }
        }
        return filings;
    }

    private String informationTableUrl(String cik, String accessionNumber, String json) {
        JsonNode root = readTree(json);
        JsonNode items = root.path("directory").path("item");
        if (!items.isArray()) {
            return null;
        }
        String selected = null;
        for (int index = 0; index < items.size(); index++) {
            String name = items.get(index).path("name").asText("");
            String lower = name.toLowerCase(Locale.ROOT);
            if (lower.endsWith(".xml") && (lower.contains("infotable") || lower.contains("information"))) {
                selected = name;
                break;
            }
            if (selected == null && lower.endsWith(".xml") && !lower.contains("primary_doc")) {
                selected = name;
            }
        }
        if (!StringUtils.hasText(selected)) {
            return null;
        }
        String pathCik = cik.replaceFirst("^0+", "");
        String accessionPath = accessionNumber.replace("-", "");
        return properties.filesBaseUrl() + "/Archives/edgar/data/" + pathCik + "/" + accessionPath + "/" + selected;
    }

    private List<Institution13fHolding> parseInformationTable(Institution13fFiling filing,
            String xml,
            Map<String, String> symbolByName) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            Document document = factory.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            NodeList infoTables = document.getElementsByTagName("infoTable");
            List<Institution13fHolding> holdings = new ArrayList<>();
            LocalDateTime fetchedAt = LocalDateTime.now();
            for (int index = 0; index < infoTables.getLength(); index++) {
                Element item = (Element) infoTables.item(index);
                Institution13fHolding holding = new Institution13fHolding();
                holding.setAccessionNumber(filing.getAccessionNumber());
                holding.setManagerCik(filing.getManagerCik());
                holding.setManagerName(filing.getManagerName());
                holding.setReportQuarter(filing.getReportQuarter());
                holding.setIssuerName(text(item, "nameOfIssuer"));
                holding.setTitleOfClass(text(item, "titleOfClass"));
                holding.setCusip(normalizeCusip(text(item, "cusip")));
                holding.setValueUsdThousands(decimal(item, "value"));
                holding.setShares(decimal(item, "sshPrnamt"));
                holding.setShareType(text(item, "sshPrnamtType"));
                holding.setPutCall(text(item, "putCall"));
                holding.setInvestmentDiscretion(text(item, "investmentDiscretion"));
                holding.setOtherManager(text(item, "otherManager"));
                holding.setVotingSole(decimal(item, "Sole"));
                holding.setVotingShared(decimal(item, "Shared"));
                holding.setVotingNone(decimal(item, "None"));
                String symbol = mapSymbol(holding.getIssuerName(), symbolByName);
                holding.setSymbol(symbol);
                holding.setMappedBy(symbol == null ? null : "ISSUER_NAME");
                holding.setRawXml(nodeXml(item));
                holding.setFetchedAt(fetchedAt);
                holdings.add(holding);
            }
            return holdings;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to parse 13F information table " + filing.getAccessionNumber(), ex);
        }
    }

    private void saveHoldings(Institution13fMapper mapper, List<Institution13fHolding> holdings) {
        for (int start = 0; start < holdings.size(); start += DB_BATCH_SIZE) {
            mapper.upsertHoldings(holdings.subList(start, Math.min(start + DB_BATCH_SIZE, holdings.size())));
        }
    }

    private Map<String, String> symbolNameMap(List<Institution13fMapper.InstitutionNameMapping> mappings) {
        Map<String, String> byName = new HashMap<>();
        for (Institution13fMapper.InstitutionNameMapping mapping : mappings) {
            if (StringUtils.hasText(mapping.getNormalizedName())) {
                byName.putIfAbsent(normalizeName(mapping.getNormalizedName()), mapping.getSymbol());
            }
        }
        return byName;
    }

    private String mapSymbol(String issuerName, Map<String, String> symbolByName) {
        String normalized = normalizeName(issuerName);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        String exact = symbolByName.get(normalized);
        if (exact != null) {
            return exact;
        }
        if (normalized.length() < 8) {
            return null;
        }
        return symbolByName.entrySet().stream()
                .filter(entry -> strongNameMatch(normalized, entry.getKey()))
                .sorted(Comparator.comparingInt(entry -> Math.abs(entry.getKey().length() - normalized.length())))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    private static boolean strongNameMatch(String issuerName, String companyName) {
        if (!StringUtils.hasText(issuerName) || !StringUtils.hasText(companyName) || companyName.length() < 8) {
            return false;
        }
        return issuerName.startsWith(companyName + " ") || companyName.startsWith(issuerName + " ");
    }

    private JsonNode readTree(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to parse SEC EDGAR JSON.", ex);
        }
    }

    private void sleepBetweenRequests() {
        if (properties.requestDelayMillis() <= 0) {
            return;
        }
        try {
            Thread.sleep(properties.requestDelayMillis());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("13F sync interrupted.", ex);
        }
    }

    private Institution13fMapper requireMapper() {
        Institution13fMapper mapper = mapperProvider.getIfAvailable();
        if (mapper == null) {
            throw new IllegalStateException("Institution13fMapper is not available. Run with the mariadb profile.");
        }
        return mapper;
    }

    private static LocalDate localDate(JsonNode values, int index) {
        if (values == null || !values.isArray() || index >= values.size()) {
            return null;
        }
        String value = values.get(index).asText("");
        try {
            return StringUtils.hasText(value) ? LocalDate.parse(value) : null;
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private static LocalDate quarterEnd(LocalDate date) {
        if (date == null) {
            return null;
        }
        int month = ((date.getMonthValue() - 1) / 3 + 1) * 3;
        return LocalDate.of(date.getYear(), month, java.time.Month.of(month).length(date.isLeapYear()));
    }

    private static String text(Element parent, String tagName) {
        NodeList values = parent.getElementsByTagName(tagName);
        if (values.getLength() == 0) {
            return null;
        }
        String text = values.item(0).getTextContent();
        return StringUtils.hasText(text) ? text.trim() : null;
    }

    private static BigDecimal decimal(Element parent, String tagName) {
        String text = text(parent, tagName);
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return new BigDecimal(text.replace(",", ""));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String normalizeCusip(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    private static String normalizeName(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = value.toUpperCase(Locale.ROOT)
                .replace("&", " AND ")
                .replaceAll("[^A-Z0-9 ]", " ")
                .replaceAll("\\b(INCORPORATED|INC|CORPORATION|CORP|COMPANY|CO|LTD|PLC|CLASS A|CL A|COM)\\b", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return normalized;
    }

    private static String nodeXml(Node node) {
        return node == null ? null : node.getTextContent();
    }

    private static String firstText(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    public record SyncSummary(int requestedCount, int successCount, int failCount,
            int holdingCount, int aggregateRows, String message) {
    }
}
