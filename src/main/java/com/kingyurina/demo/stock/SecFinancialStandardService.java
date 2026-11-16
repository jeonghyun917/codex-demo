package com.kingyurina.demo.stock;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import tools.jackson.databind.ObjectMapper;

@Service
public class SecFinancialStandardService {

    private final ObjectProvider<SecCompanyFactMapper> factMapper;
    private final ObjectProvider<SecFinancialStandardMapper> standardMapper;
    private final ObjectMapper objectMapper;

    public SecFinancialStandardService(ObjectProvider<SecCompanyFactMapper> factMapper,
            ObjectProvider<SecFinancialStandardMapper> standardMapper,
            ObjectMapper objectMapper) {
        this.factMapper = factMapper;
        this.standardMapper = standardMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public int rebuildFromDatabase(String symbol) {
        SecCompanyFactMapper facts = requireMapper(factMapper, "SecCompanyFactMapper");
        List<SecCompanyFact> rows = facts.findBySymbol(normalizeSymbol(symbol));
        if (rows.isEmpty()) {
            return 0;
        }
        return rebuildFromFacts(normalizeSymbol(symbol), rows.get(0).getCik(), rows);
    }

    @Transactional
    public int rebuildFromFacts(String symbol, String cik, List<SecCompanyFact> facts) {
        if (facts == null || facts.isEmpty()) {
            return 0;
        }
        SecFinancialStandardMapper standards = requireMapper(standardMapper, "SecFinancialStandardMapper");
        String normalized = normalizeSymbol(symbol);
        List<SecFinancialStandard> rows = standardize(normalized, cik, facts);
        // Native/foreign batches can be partial and deliberately exclude unsupported source periods.
        // Insert only new coherent keys; missing evidence never erases or overwrites an existing statement.
        boolean partialForeignInput = facts.stream().anyMatch(fact -> isNativeIfrs(fact) || isForeignInterim(fact));
        if (!partialForeignInput) {
            standards.deleteBySymbol(normalized);
        }
        if (!rows.isEmpty()) {
            if (partialForeignInput) {
                standards.insertNativeBatch(rows);
            } else {
                standards.upsertBatch(rows);
            }
        }
        return rows.size();
    }

    public List<String> findSymbolsMissingStandard(List<String> symbols, int limit) {
        SecFinancialStandardMapper standards = standardMapper.getIfAvailable();
        if (standards == null || symbols == null || symbols.isEmpty()) {
            return List.of();
        }
        return standards.findSymbolsMissingStandard(symbols, Math.max(1, limit));
    }

    private List<SecFinancialStandard> standardize(String symbol, String cik, List<SecCompanyFact> facts) {
        if (facts == null || facts.isEmpty()) {
            return List.of();
        }

        Map<PeriodKey, RowBuilder> builders = new LinkedHashMap<>();
        for (SecCompanyFact fact : facts) {
            if (!usable(fact)) {
                continue;
            }
            boolean sourcePeriodRequired = isNativeIfrs(fact) || isForeignInterim(fact);
            NativePeriod nativePeriod = sourcePeriodRequired ? nativePeriod(fact) : null;
            if (sourcePeriodRequired && nativePeriod == null) {
                continue;
            }
            PeriodKey key = new PeriodKey(
                    fact.getSymbol(),
                    fact.getCik(),
                    nativePeriod == null ? fact.getFiscalYear() : nativePeriod.year(),
                    nativePeriod == null ? fact.getFiscalPeriod().toUpperCase(Locale.ROOT) : nativePeriod.period(),
                    fact.getForm().toUpperCase(Locale.ROOT),
                    fact.getFiledAt(),
                    fact.getEndDate(),
                    fact.getAccessionNumber());
            RowBuilder builder = builders.computeIfAbsent(key, ignored -> new RowBuilder(key, objectMapper));
            builder.accept(fact);
        }

        // IFRS instant metadata may be null or identify the filing rather than the comparative period.
        // Attach balances only to a duration actually proved by the same accession and period end.
        for (SecCompanyFact fact : facts) {
            if (!usable(fact) || !(isNativeIfrs(fact) || isForeignInterim(fact))
                    || !(fact.getStartDate() == null || fact.getStartDate().equals(fact.getEndDate()))) {
                continue;
            }
            for (var entry : builders.entrySet()) {
                PeriodKey key = entry.getKey();
                if (key.symbol().equals(fact.getSymbol()) && key.cik().equals(fact.getCik())
                        && key.accessionNumber().equals(fact.getAccessionNumber())
                        && key.filedAt().equals(fact.getFiledAt()) && key.endDate().equals(fact.getEndDate())) {
                    entry.getValue().accept(fact);
                }
            }
        }

        LocalDateTime mappedAt = LocalDateTime.now();
        List<SecFinancialStandard> rows = new ArrayList<>();
        for (RowBuilder builder : builders.values()) {
            SecFinancialStandard row = builder.toRow(symbol, cik, mappedAt, this::writeJson);
            if (hasAnyStandardValue(row)) {
                rows.add(row);
            }
        }
        return rows;
    }

    private boolean usable(SecCompanyFact fact) {
        return fact != null
                && StringUtils.hasText(fact.getSymbol())
                && StringUtils.hasText(fact.getCik())
                && (isNativeIfrs(fact) || isForeignInterim(fact)
                        || (fact.getFiscalYear() != null && StringUtils.hasText(fact.getFiscalPeriod())))
                && StringUtils.hasText(fact.getForm())
                && fact.getEndDate() != null
                && StringUtils.hasText(fact.getAccessionNumber())
                && fact.getValue() != null
                && (!(isNativeIfrs(fact) || isForeignInterim(fact))
                        || (fact.getFiledAt() != null && !fact.getFiledAt().isBefore(fact.getEndDate())));
    }

    private static boolean isNativeIfrs(SecCompanyFact fact) {
        return fact != null && "ifrs-full".equals(fact.getTaxonomy());
    }

    static boolean isForeignInterim(SecCompanyFact fact) {
        return fact != null && ("6-K".equalsIgnoreCase(fact.getForm()) || "6-K/A".equalsIgnoreCase(fact.getForm()));
    }

    static NativePeriod nativePeriod(SecCompanyFact fact) {
        LocalDate start = fact.getStartDate();
        LocalDate end = fact.getEndDate();
        int days = periodDays(start, end);
        if (start == null || end == null || start.equals(end)) {
            return null;
        }
        // Calendar inference is limited to exact source dates; source fy/fp often describe the filing,
        // not this observation. No half-year subtraction, calendar guess or YTD-to-quarter conversion.
        int year = end.getYear();
        if (days >= 330 && days <= 380 && start.equals(LocalDate.of(year, 1, 1))
                && end.equals(LocalDate.of(year, 12, 31))
                && (!StringUtils.hasText(fact.getFrame()) || ("CY" + year).equals(fact.getFrame()))) {
            return new NativePeriod(year, "FY");
        }
        int quarter = (end.getMonthValue() - 1) / 3 + 1;
        LocalDate quarterStart = LocalDate.of(year, (quarter - 1) * 3 + 1, 1);
        if (days >= 70 && days <= 110 && start.equals(quarterStart)
                && end.equals(quarterStart.plusMonths(3).minusDays(1))
                && ("CY" + year + "Q" + quarter).equals(fact.getFrame())) {
            return new NativePeriod(year, "Q" + quarter);
        }
        return null;
    }

    record NativePeriod(int year, String period) { }

    private static boolean hasAnyStandardValue(SecFinancialStandard row) {
        return row.getRevenue() != null
                || row.getOperatingIncome() != null
                || row.getNetIncome() != null
                || row.getEpsDiluted() != null
                || row.getOperatingCashFlow() != null;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return null;
        }
    }

    private static String normalizeSymbol(String symbol) {
        return symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT);
    }

    private static <T> T requireMapper(ObjectProvider<T> provider, String name) {
        T mapper = provider.getIfAvailable();
        if (mapper == null) {
            throw new IllegalStateException(name + " is not available. Run with the mariadb profile.");
        }
        return mapper;
    }

    private record PeriodKey(String symbol,
            String cik,
            Integer fiscalYear,
            String fiscalPeriod,
            String form,
            LocalDate filedAt,
            LocalDate endDate,
            String accessionNumber) {
    }

    @FunctionalInterface
    private interface JsonWriter {
        String write(Object value);
    }

    private static class RowBuilder {

        private final PeriodKey key;
        private final ObjectMapper objectMapper;
        private final List<SecCompanyFact> facts = new ArrayList<>();
        private final List<FieldValue> candidates = new ArrayList<>();
        private boolean collecting = true;
        private String referenceCurrency;
        private FieldValue referenceDuration;
        private FieldValue revenue;
        private FieldValue operatingIncome;
        private FieldValue netIncome;
        private FieldValue epsDiluted;
        private FieldValue assets;
        private FieldValue liabilities;
        private FieldValue equity;
        private FieldValue operatingCashFlow;

        RowBuilder(PeriodKey key, ObjectMapper objectMapper) {
            this.key = key;
            this.objectMapper = objectMapper;
        }

        void accept(SecCompanyFact fact) {
            facts.add(fact);
            select(fact);
        }

        private void select(SecCompanyFact fact) {
            String concept = fact.getConcept();
            String unit = fact.getUnit();
            if (!StringUtils.hasText(concept) || !StringUtils.hasText(unit)) {
                return;
            }
            boolean nativeIfrs = isNativeIfrs(fact);
            // Null taxonomy retains legacy in-memory US-GAAP callers; named extension taxonomies
            // can never inherit native mappings merely by using a similarly named concept.
            if (!nativeIfrs && StringUtils.hasText(fact.getTaxonomy()) && !"us-gaap".equals(fact.getTaxonomy())) {
                return;
            }
            if (nativeIfrs) {
                if (isMoneyUnit(unit)) {
                    revenue = choose(revenue, fact, conceptPriority(concept, "Revenue", "RevenueFromContractsWithCustomers"), true);
                    operatingIncome = choose(operatingIncome, fact, conceptPriority(concept, "ProfitLossFromOperatingActivities"), true);
                    netIncome = choose(netIncome, fact, conceptPriority(concept, "ProfitLossAttributableToOwnersOfParent", "ProfitLoss"), true);
                    assets = choose(assets, fact, conceptPriority(concept, "Assets"), false);
                    liabilities = choose(liabilities, fact, conceptPriority(concept, "Liabilities"), false);
                    equity = choose(equity, fact, conceptPriority(concept, "EquityAttributableToOwnersOfParent", "Equity"), false);
                    operatingCashFlow = choose(operatingCashFlow, fact, conceptPriority(concept, "CashFlowsFromUsedInOperatingActivities"), true);
                } else if (isPerShareUnit(unit)) {
                    epsDiluted = choose(epsDiluted, fact, conceptPriority(concept, "DilutedEarningsLossPerShare"), true);
                }
                return;
            }
            if (isMoneyUnit(unit)) {
                revenue = choose(revenue, fact, conceptPriority(concept,
                        "Revenues", "RevenueFromContractWithCustomerExcludingAssessedTax", "SalesRevenueNet"),
                        true);
                operatingIncome = choose(operatingIncome, fact, conceptPriority(concept, "OperatingIncomeLoss"),
                        true);
                netIncome = choose(netIncome, fact, conceptPriority(concept,
                        "NetIncomeLoss", "NetIncomeLossAvailableToCommonStockholdersBasic"), true);
                assets = choose(assets, fact, conceptPriority(concept, "Assets"), false);
                liabilities = choose(liabilities, fact, conceptPriority(concept, "Liabilities"), false);
                equity = choose(equity, fact, conceptPriority(concept, "StockholdersEquity",
                        "StockholdersEquityIncludingPortionAttributableToNoncontrollingInterest"), false);
                operatingCashFlow = choose(operatingCashFlow, fact, conceptPriority(concept,
                        "NetCashProvidedByUsedInOperatingActivities",
                        "NetCashProvidedByUsedInOperatingActivitiesContinuingOperations"), true);
            } else if (isPerShareUnit(unit)) {
                epsDiluted = choose(epsDiluted, fact, conceptPriority(concept, "EarningsPerShareDiluted"), true);
            }
        }

        SecFinancialStandard toRow(String symbol, String cik, LocalDateTime mappedAt, JsonWriter writer) {
            // Choose the row's explicit basis first, then reconsider all candidates on that basis.
            // This retains a coherent lower-priority alternative instead of keeping mixed values.
            referenceCurrency = unit(firstNonNull(revenue, operatingIncome, netIncome, operatingCashFlow,
                    assets, liabilities, equity));
            referenceDuration = firstNonNull(revenue, operatingIncome, netIncome, operatingCashFlow, epsDiluted);
            if (referenceCurrency == null && epsDiluted != null) {
                referenceCurrency = epsDiluted.unit().substring(0, 3);
            }
            revenue = operatingIncome = netIncome = epsDiluted = assets = liabilities = equity = operatingCashFlow = null;
            collecting = false;
            for (SecCompanyFact fact : facts) {
                select(fact);
            }
            SecFinancialStandard row = new SecFinancialStandard();
            row.setSymbol(symbol);
            row.setCik(key.cik());
            row.setFiscalYear(key.fiscalYear());
            row.setFiscalPeriod(key.fiscalPeriod());
            row.setForm(key.form());
            row.setFiledAt(key.filedAt());
            row.setEndDate(key.endDate());
            row.setAccessionNumber(key.accessionNumber());
            row.setMappedAt(mappedAt);
            row.setCurrency(unit(firstNonNull(revenue, operatingIncome, netIncome, assets, liabilities, equity,
                    operatingCashFlow)));
            row.setRevenue(value(revenue));
            row.setOperatingIncome(value(operatingIncome));
            row.setNetIncome(value(netIncome));
            row.setEpsDiluted(value(epsDiluted));
            row.setAssets(value(assets));
            row.setLiabilities(value(liabilities));
            row.setEquity(value(equity));
            row.setOperatingCashFlow(value(operatingCashFlow));
            row.setEpsUnit(unit(epsDiluted));

            FieldValue duration = firstNonNull(revenue, operatingIncome, netIncome, epsDiluted, operatingCashFlow);
            if (duration != null) {
                row.setStartDate(duration.startDate());
                row.setPeriodDays(duration.periodDays());
            }
            Map<String, Object> raw = new LinkedHashMap<>(Map.of(
                    "revenueConcept", concept(revenue),
                    "operatingIncomeConcept", concept(operatingIncome),
                    "netIncomeConcept", concept(netIncome),
                    "epsDilutedConcept", concept(epsDiluted),
                    "assetsConcept", concept(assets),
                    "liabilitiesConcept", concept(liabilities),
                    "equityConcept", concept(equity),
                    "currency", row.getCurrency() == null ? "" : row.getCurrency(),
                    "epsUnit", row.getEpsUnit() == null ? "" : row.getEpsUnit(),
                    "operatingCashFlowConcept", concept(operatingCashFlow)));
            raw.put("coherencePolicy", "EXPLICIT_CURRENCY_EXACT_DURATION_V1");
            if (facts.stream().anyMatch(SecFinancialStandardService::isNativeIfrs)) {
                raw.put("periodPolicy", "CALENDAR_FRAME_EXACT_DURATION_V1");
            }
            Map<String, Object> selectedEvidence = new LinkedHashMap<>();
            selectedEvidence.put("revenue", evidence(revenue));
            selectedEvidence.put("operatingIncome", evidence(operatingIncome));
            selectedEvidence.put("netIncome", evidence(netIncome));
            selectedEvidence.put("epsDiluted", evidence(epsDiluted));
            selectedEvidence.put("assets", evidence(assets));
            selectedEvidence.put("liabilities", evidence(liabilities));
            selectedEvidence.put("equity", evidence(equity));
            selectedEvidence.put("operatingCashFlow", evidence(operatingCashFlow));
            raw.put("selectedFieldEvidence", selectedEvidence);
            raw.put("candidateFieldEvidence", candidates.stream().map(this::evidence).toList());
            row.setRawJson(writer.write(raw));
            return row;
        }

        private FieldValue choose(FieldValue current, SecCompanyFact fact, int conceptPriority, boolean durationField) {
            if (conceptPriority < 0) {
                return current;
            }
            // Keep the persisted legacy key unchanged; recover instant semantics only from original source evidence.
            LocalDate startDate = !durationField && sourceProvesInstant(fact) ? null : fact.getStartDate();
            FieldValue candidate = FieldValue.from(fact, startDate, conceptPriority, durationPreference(fact, durationField));
            if (collecting) {
                candidates.add(candidate);
            }
            if (durationField ? !validDuration(candidate) : candidate.startDate() != null) {
                return current;
            }
            if (!collecting) {
                String currency = isPerShareUnit(candidate.unit()) ? candidate.unit().substring(0, 3) : candidate.unit();
                if (!currency.equals(referenceCurrency)
                        || (durationField && (referenceDuration == null
                                || !candidate.startDate().equals(referenceDuration.startDate())
                                || !candidate.endDate().equals(referenceDuration.endDate())))) {
                    return current;
                }
            }
            if (current == null) {
                return candidate;
            }
            if (!candidate.unit().equals(current.unit())) {
                if ("USD".equals(candidate.unit()) || "USD/shares".equals(candidate.unit())) {
                    return candidate;
                }
                if ("USD".equals(current.unit()) || "USD/shares".equals(current.unit())) {
                    return current;
                }
            }
            if (candidate.score() > current.score()) {
                return candidate;
            }
            if (candidate.score() == current.score() && candidate.filedAt() != null && current.filedAt() != null
                    && candidate.filedAt().isAfter(current.filedAt())) {
                return candidate;
            }
            return current;
        }

        private boolean sourceProvesInstant(SecCompanyFact fact) {
            if (!fact.getEndDate().equals(fact.getStartDate()) || !StringUtils.hasText(fact.getRawJson())) {
                return false;
            }
            try {
                var raw = objectMapper.readTree(fact.getRawJson());
                return raw != null && raw.isObject() && !raw.has("start")
                        && fact.getEndDate().toString().equals(raw.path("end").asText());
            } catch (RuntimeException ex) {
                return false;
            }
        }

        private boolean validDuration(FieldValue candidate) {
            int days = candidate.periodDays();
            return candidate.startDate() != null && ("FY".equals(key.fiscalPeriod())
                    ? days >= 330 && days <= 380
                    : key.fiscalPeriod().matches("Q[1-4]") && days >= 70 && days <= 110);
        }

        private Map<String, Object> evidence(FieldValue field) {
            if (field == null) {
                return Map.of();
            }
            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("concept", field.concept());
            evidence.put("taxonomy", field.taxonomy());
            evidence.put("unit", field.unit());
            evidence.put("startDate", field.startDate() == null ? null : field.startDate().toString());
            evidence.put("endDate", field.endDate() == null ? null : field.endDate().toString());
            evidence.put("periodDays", field.periodDays());
            evidence.put("sourceFiscalYear", field.sourceFiscalYear());
            evidence.put("sourceFiscalPeriod", field.sourceFiscalPeriod());
            evidence.put("sourceFrame", field.sourceFrame());
            evidence.put("sourceObservationJson", field.sourceObservationJson());
            evidence.put("storedFiscalYear", field.sourceFiscalYear());
            evidence.put("storedFiscalPeriod", field.sourceFiscalPeriod());
            if (StringUtils.hasText(field.sourceObservationJson())) {
                try {
                    var original = objectMapper.readTree(field.sourceObservationJson());
                    if (original != null && original.isObject()) {
                        if (original.has("fy")) {
                            evidence.put("sourceFiscalYear", original.get("fy").isNull() ? null : original.get("fy").asInt());
                        }
                        if (original.has("fp")) {
                            evidence.put("sourceFiscalPeriod", original.get("fp").isNull() ? null : original.get("fp").asText());
                        }
                    }
                } catch (RuntimeException ignored) {
                    // Preserve legacy metadata and the original unparseable receipt; do not invent source fields.
                }
            }
            return evidence;
        }

        private int durationPreference(SecCompanyFact fact, boolean durationField) {
            if (!durationField) {
                return 20;
            }
            int days = periodDays(fact.getStartDate(), fact.getEndDate());
            if ("FY".equals(key.fiscalPeriod())) {
                return days >= 300 ? 30 : 5;
            }
            if (key.fiscalPeriod().startsWith("Q")) {
                return days >= 70 && days <= 120 ? 30 : 5;
            }
            return 10;
        }

        private static int conceptPriority(String concept, String... candidates) {
            for (int index = 0; index < candidates.length; index++) {
                if (candidates[index].equals(concept)) {
                    return candidates.length - index;
                }
            }
            return -1;
        }

        private static boolean isMoneyUnit(String unit) {
            return unit != null && unit.matches("[A-Z]{3}");
        }

        private static boolean isPerShareUnit(String unit) {
            return unit != null && unit.matches("[A-Z]{3}/shares");
        }

        private static BigDecimal value(FieldValue value) {
            return value == null ? null : value.value();
        }

        private static String unit(FieldValue value) {
            return value == null ? null : value.unit();
        }

        private static String concept(FieldValue value) {
            return value == null ? "" : value.concept();
        }

        private static FieldValue firstNonNull(FieldValue... values) {
            for (FieldValue value : values) {
                if (value != null) {
                    return value;
                }
            }
            return null;
        }
    }

    private record FieldValue(String concept,
            String taxonomy,
            String unit,
            BigDecimal value,
            LocalDate filedAt,
            LocalDate startDate,
            LocalDate endDate,
            int periodDays,
            Integer sourceFiscalYear,
            String sourceFiscalPeriod,
            String sourceFrame,
            String sourceObservationJson,
            int score) {

        static FieldValue from(SecCompanyFact fact, LocalDate startDate, int conceptPriority, int durationPreference) {
            int days = SecFinancialStandardService.periodDays(startDate, fact.getEndDate());
            int score = conceptPriority * 100 + durationPreference;
            return new FieldValue(fact.getConcept(), fact.getTaxonomy(), fact.getUnit(), fact.getValue(), fact.getFiledAt(),
                    startDate, fact.getEndDate(), days, fact.getFiscalYear(), fact.getFiscalPeriod(), fact.getFrame(), fact.getRawJson(), score);
        }
    }

    private static int periodDays(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null || endDate.isBefore(startDate)) {
            return 0;
        }
        return (int) ChronoUnit.DAYS.between(startDate, endDate) + 1;
    }
}
