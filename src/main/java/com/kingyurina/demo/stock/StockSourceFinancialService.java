package com.kingyurina.demo.stock;

import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.*;

/** Opt-in source policy. DB reads only; never repairs the original SEC table or backdates acquisition. */
@Service
public class StockSourceFinancialService {
    public static final String VERSION="SOURCE_FINANCIAL_BRIDGE_V1_20261105";
    private final ObjectProvider<StockSourceFinancialMapper> provider;
    private final ObjectMapper json;
    private final boolean enabled;

    public StockSourceFinancialService(ObjectProvider<StockSourceFinancialMapper> provider,ObjectMapper json,
            @Value("${app.quant.source-financial.enabled:false}") boolean enabled){
        this.provider=provider;this.json=json;this.enabled=enabled;
    }

    public SecFinancialStandard resolveQuarter(String symbol,LocalDate decision,LocalDate beforeEnd,
            SecFinancialStandard legacy,boolean reconstructed){
        if(!enabled||!Set.of("AMGN","GS").contains(symbol))return legacy;
        var mapper=provider.getIfAvailable();if(mapper==null)return legacy;
        SecFinancialStandard selected=null;StockSourceFinancialDocument chosen=null;var periods=new HashSet<String>();
        for(var doc:mapper.findBySymbol(symbol,VERSION)){
            if(!VERSION.equals(doc.packVersion())||!symbol.equals(doc.symbol())||doc.admittedAtUtc()==null)
                throw new IllegalArgumentException("Invalid admitted source identity");
            var body=json.readTree(doc.payloadJson());var identity=body.path("canonicalIdentity");
            if(!doc.identitySha256().equals(body.path("canonicalIdentitySha256").asText())
                    ||!symbol.equals(identity.path("symbol").asText())
                    ||!doc.publicationDate().toString().equals(identity.path("publicationDate").asText()))
                throw new IllegalArgumentException("Source payload/column identity drift");
            LocalDate start=LocalDate.parse(identity.path("startDate").asText()),end=LocalDate.parse(identity.path("endDate").asText());
            if(!periods.add(start+"/"+end))throw new IllegalArgumentException("Ambiguous source period");
            // Date-only public evidence is available strictly after its printed date, not at a made-up time.
            if(!doc.publicationDate().isBefore(decision)||!end.isBefore(decision))continue;
            Instant admitted=doc.admittedAtUtc().toInstant(ZoneOffset.UTC);
            if(!reconstructed&&(admitted.isAfter(Instant.now())||!admitted.isBefore(
                    decision.plusDays(1).atStartOfDay(ZoneId.of("America/New_York")).toInstant())))continue;
            if(beforeEnd!=null){long gap=ChronoUnit.DAYS.between(end,beforeEnd);if(gap<70||gap>110)continue;}
            var row=sourceRow(body,doc);
            if(!FinancialInputPolicy.validPeriod(row,false))throw new IllegalArgumentException("Invalid source quarter duration");
            if(selected==null||end.isAfter(selected.getEndDate())){selected=row;chosen=doc;}
        }
        if(selected==null)return legacy;
        if(legacy!=null){
            if(!sameIssuerCurrency(legacy,selected)||legacy.getEndDate()==null||legacy.getEndDate().isAfter(selected.getEndDate()))return legacy;
            if(legacy.getEndDate().equals(selected.getEndDate())){
                var identity=json.readTree(chosen.payloadJson()).path("canonicalIdentity");
                boolean samePeriod=Objects.equals(legacy.getStartDate(),selected.getStartDate())
                        &&Objects.equals(legacy.getFiscalPeriod(),selected.getFiscalPeriod());
                if(samePeriod){
                    if(legacy.getRevenue()!=null||selected.getRevenue()==null)return legacy;
                    var copy=json.readValue(json.writeValueAsString(legacy),SecFinancialStandard.class);
                    copy.setRevenue(selected.getRevenue());
                    copy.setRawJson(provenance(chosen,legacy,"EXACT_PERIOD_REVENUE_NULL_FILL",reconstructed));return copy;
                }
                // A different period is an independent whole statement, not a null fill into the legacy key.
                if(!"SEC_ORIGINAL_SAME_FILING_XBRL".equals(identity.path("sourceKind").asText())
                        ||!Objects.equals(legacy.getAccessionNumber(),selected.getAccessionNumber()))return legacy;
            }
        }
        selected.setRawJson(provenance(chosen,legacy,"WHOLE_DISTINCT_SOURCE_STATEMENT",reconstructed));return selected;
    }

    private SecFinancialStandard sourceRow(JsonNode body,StockSourceFinancialDocument doc){
        var id=body.path("canonicalIdentity");var values=body.path("values");var units=body.path("units");
        if(!"USD".equals(id.path("currency").asText())||!Set.of("OFFICIAL_ISSUER_EARNINGS_RELEASE","SEC_ORIGINAL_SAME_FILING_XBRL")
                .contains(id.path("sourceKind").asText()))throw new IllegalArgumentException("Unsupported financial source/unit");
        var row=new SecFinancialStandard();row.setSymbol(doc.symbol());row.setCik(id.path("cik").asText());
        row.setStartDate(LocalDate.parse(id.path("startDate").asText()));row.setEndDate(LocalDate.parse(id.path("endDate").asText()));
        row.setFiscalYear(row.getEndDate().getYear());row.setFiscalPeriod(id.path("fiscalPeriod").asText());row.setCurrency("USD");
        row.setPeriodDays(Math.toIntExact(ChronoUnit.DAYS.between(row.getStartDate(),row.getEndDate())+1));
        row.setForm(body.path("sourceForm").asText());row.setAccessionNumber(nullable(id.path("secAccessionNumber")));
        String filed=nullable(id.path("secFiledDate"));row.setFiledAt(filed==null?null:LocalDate.parse(filed));
        row.setRevenue(value(values,units,"revenue","USD"));row.setNetIncome(value(values,units,"netIncome","USD"));
        row.setOperatingIncome(value(values,units,"operatingIncome","USD"));row.setEpsDiluted(value(values,units,"epsDiluted","USD/shares"));
        row.setEpsUnit(nullable(units.path("epsDiluted")));row.setMappedAt(doc.admittedAtUtc());return row;
    }
    private BigDecimal value(JsonNode values,JsonNode units,String key,String unit){
        if(values.path(key).isMissingNode()||values.path(key).isNull())return null;
        if(!unit.equals(units.path(key).asText()))throw new IllegalArgumentException("Unknown financial field unit");
        return new BigDecimal(values.path(key).asText());
    }
    private static String nullable(JsonNode n){return n.isMissingNode()||n.isNull()?null:n.asText();}
    private boolean sameIssuerCurrency(SecFinancialStandard a,SecFinancialStandard b){
        return Objects.equals(a.getSymbol(),b.getSymbol())&&a.getCik()!=null&&b.getCik()!=null
            &&a.getCik().replaceFirst("^0+","").equals(b.getCik().replaceFirst("^0+",""))&&Objects.equals(a.getCurrency(),b.getCurrency());
    }
    private String provenance(StockSourceFinancialDocument doc,SecFinancialStandard legacy,String selection,boolean reconstructed){
        var data=new LinkedHashMap<String,Object>();data.put("sourceFinancialPolicyVersion",VERSION);data.put("selection",selection);
        data.put("knowledgeClass",reconstructed?"RECONSTRUCTED_NOT_HISTORICAL_SYSTEM_PIT":"OBSERVED_LIVE_SOURCE_ADMISSION");
        data.put("admittedAtUtc",doc.admittedAtUtc().toString()+"Z");data.put("sourceDocument",json.readTree(doc.payloadJson()));
        data.put("legacySelectedRow",legacy);data.put("legacyRowMutated",false);return json.writeValueAsString(data);
    }
}
