package com.kingyurina.demo.stock;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class FinancialRestandardizationSafetyTest {
    @Test void absentFactsRejectsBeforeAnyReplacement() {
        assertThrows(IllegalStateException.class, () -> FinancialRestandardizationSafety.plan(List.of(row("1", "A", "10")), List.of(row("1", "A", "20")), 0));
    }
    @Test void emptyCandidateRejectsRatherThanErasingSymbol() {
        assertThrows(IllegalStateException.class, () -> FinancialRestandardizationSafety.plan(List.of(row("1", "A", "10")), List.of(), 1));
    }
    @Test void duplicateCandidateKeyRejectsAmbiguousReplacement() {
        assertThrows(IllegalStateException.class, () -> FinancialRestandardizationSafety.plan(List.of(row("1", "A", "10")), List.of(row("1", "A", "20"), row("2", "A", "30")), 2));
    }
    @Test void lostKeyIsQuarantinedWithoutLosingIdOrKey() {
        var a = row("1", "A", "10"); var b = row("2", "B", "20");
        var plan = FinancialRestandardizationSafety.plan(List.of(a,b), List.of(row("9", "A", "30")), 1);
        assertEquals(2, plan.rows().size());
        var quarantined = plan.rows().get(1);
        assertEquals("2", quarantined.get("id")); assertEquals("B", quarantined.get("symbol"));
        assertNull(quarantined.get("revenue")); assertNull(quarantined.get("start_date")); assertNull(quarantined.get("period_days"));
        assertTrue(quarantined.get("raw_json").contains("QUARANTINED"));
        assertEquals("1", plan.rows().getFirst().get("id"));
    }
    @Test void currentDriftRejectsWithoutUpdatingAnything() throws Exception {
        try (var c = database()) {
            var old = FinancialRestandardizationSafety.readStandards(c, false);
            var next = new ArrayList<>(old); next.set(0, changed(old.getFirst(), "20"));
            c.createStatement().executeUpdate("UPDATE stock_sec_financial_standard SET revenue=11 WHERE id=1");
            assertThrows(IllegalStateException.class, () -> FinancialRestandardizationSafety.applyAtomically(c, old, next, () -> {}));
            assertEquals("11.000000", FinancialRestandardizationSafety.readStandards(c, false).getFirst().get("revenue"));
        }
    }
    @Test void lateValidationFailureRollsBackAllUpdates() throws Exception {
        try (var c = database()) {
            var old = FinancialRestandardizationSafety.readStandards(c, false);
            var next = List.of(changed(old.getFirst(), "20"));
            var guards=new java.util.concurrent.atomic.AtomicInteger();
            assertThrows(IllegalStateException.class, () -> FinancialRestandardizationSafety.applyAtomically(c, old, next, () -> { if(guards.incrementAndGet()==2)throw new IllegalStateException("source changed"); }));
            assertEquals(2,guards.get());
            assertEquals("10.000000", FinancialRestandardizationSafety.readStandards(c, false).getFirst().get("revenue"));
            assertEquals(1, FinancialRestandardizationSafety.readStandards(c, false).size());
        }
    }
    @Test void successfulRepairPreservesIdentityAndCommitsReplacement() throws Exception {
        try(var c=database()){
            var old=FinancialRestandardizationSafety.readStandards(c,false);
            var after=FinancialRestandardizationSafety.applyAtomically(c,old,List.of(changed(old.getFirst(),"25")),()->{});
            assertEquals(1,after.size());assertEquals("1",after.getFirst().get("id"));assertEquals("A",after.getFirst().get("symbol"));
            assertEquals("25.000000",FinancialRestandardizationSafety.readStandards(c,false).getFirst().get("revenue"));
        }
    }
    @Test void fatalLateGuardFailureDoesNotCommitPartialUpdates() throws Exception {
        try(var c=database()){
            var old=FinancialRestandardizationSafety.readStandards(c,false);var count=new java.util.concurrent.atomic.AtomicInteger();
            assertThrows(AssertionError.class,()->FinancialRestandardizationSafety.applyAtomically(c,old,List.of(changed(old.getFirst(),"99")),()->{if(count.incrementAndGet()==2)throw new AssertionError("fatal validation");}));
            assertEquals("10.000000",FinancialRestandardizationSafety.readStandards(c,false).getFirst().get("revenue"));
        }
    }
    @Test void mixedCurrencyEvidenceIsRejected() {
        var bad=row("1","A","10");bad.put("currency","EUR");
        assertThrows(IllegalStateException.class,()->FinancialRestandardizationSafety.plan(List.of(row("1","A","10")),List.of(bad),1));
    }
    private static Map<String,String> changed(Map<String,String> old,String value) { var r=new LinkedHashMap<>(old);r.put("revenue",value);return r; }
    private static Map<String,String> row(String id,String symbol,String revenue) {
        var r=new LinkedHashMap<String,String>();
        for(var col:FinancialRestandardizationSafety.COLUMNS) r.put(col,null);
        r.put("id",id);r.put("symbol",symbol);r.put("cik","1");r.put("fiscal_year","2024");r.put("fiscal_period","FY");r.put("form","10-K");
        r.put("filed_at","2025-02-01");r.put("start_date","2024-01-01");r.put("end_date","2024-12-31");r.put("accession_number","acc");r.put("period_days","366");r.put("currency","USD");r.put("revenue",revenue);r.put("mapped_at","2026-01-01 00:00:00");
        r.put("raw_json","{\"coherencePolicy\":\"EXPLICIT_CURRENCY_EXACT_DURATION_V1\",\"selectedFieldEvidence\":{\"revenue\":{\"unit\":\"USD\",\"startDate\":\"2024-01-01\",\"endDate\":\"2024-12-31\",\"periodDays\":366}}}");
        return r;
    }
    private static Connection database() throws Exception {
        var c=DriverManager.getConnection("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MariaDB;DATABASE_TO_LOWER=TRUE");
        var defs=new ArrayList<String>();for(var col:FinancialRestandardizationSafety.COLUMNS)defs.add(col+switch(col){case "id" -> " BIGINT PRIMARY KEY";case "revenue" -> " DECIMAL(32,6)";default -> " VARCHAR(8000)";});
        c.createStatement().execute("CREATE TABLE stock_sec_financial_standard ("+String.join(",",defs)+")");
        var r=row("1","A","10");try(var p=c.prepareStatement("INSERT INTO stock_sec_financial_standard VALUES ("+String.join(",",Collections.nCopies(r.size(),"?"))+")")){int i=1;for(var v:r.values())p.setString(i++,v);p.executeUpdate();}return c;
    }
    public static void main(String[] args) throws Exception {
        int passed=0;for(var m:FinancialRestandardizationSafetyTest.class.getDeclaredMethods())if(m.isAnnotationPresent(Test.class)){try{m.invoke(new FinancialRestandardizationSafetyTest());System.out.println("PASS "+m.getName());passed++;}catch(java.lang.reflect.InvocationTargetException e){throw new AssertionError(m.getName(),e.getCause());}}
        System.out.println("Passed "+passed+" safety tests");
    }
}
