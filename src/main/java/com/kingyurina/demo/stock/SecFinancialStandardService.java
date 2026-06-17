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

    public int rebuildFromDatabase(String symbol) {
        SecCompanyFactMapper facts = requireMapper(factMapper, "SecCompanyFactMapper");
        List<SecCompanyFact> rows = facts.findBySymbol(normalizeSymbol(symbol));
        if (rows.isEmpty()) {
            return 0;
        }
        return rebuildFromFacts(normalizeSymbol(symbol), rows.get(0).getCik(), rows);
    }

    public int rebuildFromFacts(String symbol, String cik, List<SecCompanyFact> facts) {
        SecFinancialStandardMapper standards = requireMapper(standardMapper, "SecFinancialStandardMapper");
        String normalized = normalizeSymbol(symbol);
        List<SecFinancialStandard> rows = standardize(normalized, cik, facts);
        standards.deleteBySymbol(normalized);
        if (!rows.isEmpty()) {
            standards.upsertBatch(rows);
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
            PeriodKey key = new PeriodKey(
                    fact.getSymbol(),
                    fact.getCik(),
                    fact.getFiscalYear(),
                    fact.getFiscalPeriod().toUpperCase(Locale.ROOT),
                    fact.getForm().toUpperCase(Locale.ROOT),
                    fact.getFiledAt(),
                    fact.getEndDate(),
                    fact.getAccessionNumber());
            RowBuilder builder = builders.computeIfAbsent(key, ignored -> new RowBuilder(key));
            builder.accept(fact);
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
                && fact.getFiscalYear() != null
                && StringUtils.hasText(fact.getFiscalPeriod())
                && StringUtils.hasText(fact.getForm())
                && fact.getEndDate() != null
                && StringUtils.hasText(fact.getAccessionNumber())
                && fact.getValue() != null;
    }

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
        private FieldValue revenue;
        private FieldValue operatingIncome;
        private FieldValue netIncome;
        private FieldValue epsDiluted;
        private FieldValue assets;
        private FieldValue liabilities;
        private FieldValue equity;
        private FieldValue operatingCashFlow;

        RowBuilder(PeriodKey key) {
            this.key = key;
        }

        void accept(SecCompanyFact fact) {
            String concept = fact.getConcept();
            String unit = fact.getUnit();
            if (!StringUtils.hasText(concept) || !StringUtils.hasText(unit)) {
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
            SecFinancialStandard row = new SecFinancialStandard();
            row.setSymbol(symbol);
            row.setCik(cik);
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
            row.setRawJson(writer.write(Map.of(
                    "revenueConcept", concept(revenue),
                    "operatingIncomeConcept", concept(operatingIncome),
                    "netIncomeConcept", concept(netIncome),
                    "epsDilutedConcept", concept(epsDiluted),
                    "assetsConcept", concept(assets),
                    "liabilitiesConcept", concept(liabilities),
                    "equityConcept", concept(equity),
                    "currency", row.getCurrency() == null ? "" : row.getCurrency(),
                    "epsUnit", row.getEpsUnit() == null ? "" : row.getEpsUnit(),
                    "operatingCashFlowConcept", concept(operatingCashFlow))));
            return row;
        }

        private FieldValue choose(FieldValue current, SecCompanyFact fact, int conceptPriority, boolean durationField) {
            if (conceptPriority < 0) {
                return current;
            }
            FieldValue candidate = FieldValue.from(fact, conceptPriority, durationPreference(fact, durationField));
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
            String unit,
            BigDecimal value,
            LocalDate filedAt,
            LocalDate startDate,
            int periodDays,
            int score) {

        static FieldValue from(SecCompanyFact fact, int conceptPriority, int durationPreference) {
            int days = SecFinancialStandardService.periodDays(fact.getStartDate(), fact.getEndDate());
            int score = conceptPriority * 100 + durationPreference;
            return new FieldValue(fact.getConcept(), fact.getUnit(), fact.getValue(), fact.getFiledAt(),
                    fact.getStartDate(), days, score);
        }
    }

    private static int periodDays(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null || endDate.isBefore(startDate)) {
            return 0;
        }
        return (int) ChronoUnit.DAYS.between(startDate, endDate) + 1;
    }
}
