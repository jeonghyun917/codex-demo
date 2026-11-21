package com.kingyurina.demo.stock;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import tools.jackson.databind.ObjectMapper;

/** Fail-closed, update-only boundary for the explicitly operated repair script. */
public final class FinancialRestandardizationSafety {
    public static final List<String> COLUMNS = List.of("id","symbol","cik","fiscal_year","fiscal_period","form","filed_at","start_date","end_date","accession_number","period_days","currency","revenue","operating_income","net_income","eps_diluted","assets","liabilities","equity","operating_cash_flow","eps_unit","raw_json","mapped_at");
    public static final List<String> KEY = List.of("symbol","fiscal_year","fiscal_period","form","accession_number","end_date");
    public static final List<String> VALUES = List.of("revenue","operating_income","net_income","eps_diluted","assets","liabilities","equity","operating_cash_flow");
    private static final ObjectMapper JSON = new ObjectMapper();
    private FinancialRestandardizationSafety() {}
    public record Plan(List<Map<String,String>> rows,List<String> quarantinedKeys,List<Map<String,String>> extras) {}
    public static String key(Map<String,String> r) { return JSON.writeValueAsString(KEY.stream().map(r::get).toList()); }
    public static Plan plan(List<Map<String,String>> old,List<Map<String,String>> candidates,long factCount) {
        require(factCount>0,"No raw facts; refusing repair"); require(!old.isEmpty(),"No existing rows");
        require(!candidates.isEmpty(),"Empty candidate set; refusing repair");
        var byKey=new LinkedHashMap<String,Map<String,String>>();
        for(var r:candidates){validate(r);require(byKey.put(key(r),r)==null,"Duplicate candidate key: "+key(r));}
        var rows=new ArrayList<Map<String,String>>();var lost=new ArrayList<String>();var seen=new HashSet<String>();var ids=new HashSet<String>();
        for(var r:old){require(seen.add(key(r))&&ids.add(r.get("id")),"Duplicate existing key/id");var candidate=byKey.remove(key(r));var next=new LinkedHashMap<>(r);
            if(candidate==null){lost.add(key(r));for(var v:VALUES)next.put(v,null);for(var v:List.of("currency","eps_unit","start_date","period_days"))next.put(v,null);
                next.put("raw_json",JSON.writeValueAsString(Map.of("coherencePolicy","QUARANTINED_NO_COHERENT_SOURCE_GROUP_V1","quarantined",true,"reason","Existing key has no coherent safely rebuildable candidate; original preserved in old-standards.json backup","originalId",r.get("id"),"originalKey",key(r))));
            }else{for(var col:COLUMNS)if(!col.equals("id")&&!KEY.contains(col))next.put(col,candidate.get(col));}
            validate(next);rows.add(Collections.unmodifiableMap(next));
        }
        return new Plan(List.copyOf(rows),List.copyOf(lost),List.copyOf(byKey.values()));
    }
    public static void validate(Map<String,String> r) {
        require(r.keySet().containsAll(COLUMNS),"Incomplete row schema");for(var k:KEY)require(r.get(k)!=null,"Null key "+k);
        var raw=JSON.readTree(Objects.requireNonNull(r.get("raw_json"),"Missing evidence"));
        if(raw.path("quarantined").asBoolean(false)){
            require(raw.path("coherencePolicy").asText().startsWith("QUARANTINED_"),"Invalid quarantine policy");
            for(var v:VALUES)require(r.get(v)==null,"Quarantine retains value "+v);
            for(var v:List.of("currency","eps_unit","start_date","period_days"))require(r.get(v)==null,"Quarantine retains "+v);
            require(raw.path("originalId").asText().equals(r.get("id"))&&raw.path("originalKey").asText().equals(key(r)),"Quarantine identity mismatch");return;
        }
        require("EXPLICIT_CURRENCY_EXACT_DURATION_V1".equals(raw.path("coherencePolicy").asText()),"Missing coherence policy");
        var evidence=raw.path("selectedFieldEvidence"); boolean any=false;
        for(var v:VALUES){if(r.get(v)==null)continue;any=true;var ev=evidence.path(camel(v));
            String unit=ev.path("unit").asText();String currency=r.get("currency");
            if(v.equals("eps_diluted"))require(unit.matches("[A-Z]{3}/shares")&&unit.equals(r.get("eps_unit"))&&(currency==null||unit.equals(currency+"/shares")),"Incoherent EPS unit");
            else require(currency!=null&&currency.matches("[A-Z]{3}")&&currency.equals(unit),"Incoherent currency: "+v);
            require(Objects.equals(r.get("end_date"),ev.path("endDate").asText()),"Incoherent end date");
            if(List.of("assets","liabilities","equity").contains(v)){require(ev.path("startDate").isNull()||ev.path("startDate").isMissingNode(),"Instant has start date");continue;}
            String start=r.get("start_date");require(start!=null&&start.equals(ev.path("startDate").asText()),"Incoherent duration");
            long days=ChronoUnit.DAYS.between(LocalDate.parse(start),LocalDate.parse(r.get("end_date")))+1;
            require(r.get("period_days")!=null&&days==Long.parseLong(r.get("period_days"))&&days==ev.path("periodDays").asLong(),"Duration count mismatch");
            require("FY".equals(r.get("fiscal_period"))?days>=330&&days<=380:r.get("fiscal_period").matches("Q[1-4]")&&days>=70&&days<=110,"Unsafe fiscal duration");
        }require(any,"Unquarantined row has no financial values");
    }
    private static String camel(String s){var b=new StringBuilder();boolean up=false;for(char c:s.toCharArray()){if(c=='_')up=true;else{b.append(up?Character.toUpperCase(c):c);up=false;}}return b.toString();}
    public static List<Map<String,String>> readStandards(Connection c,boolean lock)throws SQLException {
        try(var st=c.createStatement();var rs=st.executeQuery("SELECT "+String.join(",",COLUMNS)+" FROM stock_sec_financial_standard ORDER BY id"+(lock?" FOR UPDATE":""))){return readRows(rs,COLUMNS);}
    }
    public static List<Map<String,String>> readRows(ResultSet rs,List<String> columns)throws SQLException {
        var rows=new ArrayList<Map<String,String>>();while(rs.next()){var r=new LinkedHashMap<String,String>();for(var col:columns)r.put(col,rs.getString(col));rows.add(r);}return rows;
    }
    public static String hash(List<Map<String,String>> rows){
        try{var digest=MessageDigest.getInstance("SHA-256");for(var r:rows)for(var col:COLUMNS){String value=r.get(col);if(value!=null){if(VALUES.contains(col))value=new BigDecimal(value).stripTrailingZeros().toPlainString();else if(col.equals("mapped_at"))value=LocalDateTime.parse(value.replace(' ','T')).toString();}update(digest,value);}return HexFormat.of().formatHex(digest.digest());}catch(Exception e){throw new IllegalStateException(e);}
    }
    public static void update(MessageDigest digest,String value){byte[] bytes=value==null?new byte[0]:value.getBytes(StandardCharsets.UTF_8);digest.update((value==null?"N:":"S"+bytes.length+":").getBytes(StandardCharsets.UTF_8));digest.update(bytes);}
    @FunctionalInterface public interface Guard {void verify()throws Exception;}
    public static List<Map<String,String>> applyAtomically(Connection c,List<Map<String,String>> expected,List<Map<String,String>> replacement,Guard guard)throws Exception {
        require(c.getAutoCommit(),"Connection must be owned by repair and autocommit initially");
        c.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);c.setAutoCommit(false);
        boolean transactionEnded=false;
        try{
            var current=readStandards(c,true);require(hash(current).equals(hash(expected)),"Current table drifted from prepared preimage");
            require(expected.size()==replacement.size()&&!expected.isEmpty(),"Replacement count mismatch");
            var byId=new HashMap<String,Map<String,String>>();for(var r:expected)require(byId.put(r.get("id"),r)==null,"Duplicate old id");
            var unique=new HashSet<String>();for(var r:replacement){var old=byId.remove(r.get("id"));require(old!=null&&key(old).equals(key(r)),"Identity/key changed");require(unique.add(key(r)),"Duplicate output key");validate(r);}require(byId.isEmpty(),"Missing output ids");
            guard.verify();
            var mutable=COLUMNS.stream().filter(k->!k.equals("id")&&!KEY.contains(k)).toList();
            String sql="UPDATE stock_sec_financial_standard SET "+String.join(",",mutable.stream().map(k->k+"=?").toList())+" WHERE id=?";
            try(var ps=c.prepareStatement(sql)){int count=0;for(var r:replacement){int i=1;for(var col:mutable)ps.setString(i++,r.get(col));ps.setLong(i,Long.parseLong(r.get("id")));ps.addBatch();if(++count%250==0)assertBatch(ps.executeBatch());}if(count%250!=0)assertBatch(ps.executeBatch());}
            var after=readStandards(c,true);require(after.size()==expected.size()&&hash(after).equals(hash(replacement)),"Postimage mismatch");for(var r:after)validate(r);guard.verify();c.commit();transactionEnded=true;return after;
        }catch(Exception|Error e){try{c.rollback();transactionEnded=true;}catch(SQLException rollback){e.addSuppressed(rollback);}throw e;}finally{if(transactionEnded)c.setAutoCommit(true);}
    }
    private static void assertBatch(int[] results){for(int n:results)require(n==1||n==Statement.SUCCESS_NO_INFO,"Unexpected update count "+n);}
    public static void require(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
}
