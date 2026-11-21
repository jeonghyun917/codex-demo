import java.sql.*;
import java.nio.file.*;
import java.math.BigDecimal;
import java.util.*;

/** Fresh-connection, read-only post-commit verification against every backed-up column. */
public final class QuantEpsRecoveryPostVerify {
    public static void main(String[] args)throws Exception {
        if(args.length!=2)throw new IllegalArgumentException("BACKUP_AND_PLANS NEW_RECEIPT");
        var j=QuantEpsRecoveryApply.J;
        var plans=j.readTree(Files.readString(Path.of(args[0])));
        int verified=0;
        try(var c=DriverManager.getConnection("jdbc:mariadb://localhost:3306/king_yurina","root","")) {
            try(var s=c.createStatement()){s.execute("SET SESSION time_zone='SYSTEM'");s.execute("SET SESSION TRANSACTION READ ONLY");}
            c.setReadOnly(true);c.setAutoCommit(false);
            for(var n:plans){
                var p=j.treeToValue(n,QuantEpsRecoveryApply.Plan.class);
                var expected=new TreeMap<>(p.preimage());
                expected.put("eps_diluted",new BigDecimal(p.recovered().path("epsDiluted").asText()).setScale(6).toPlainString());
                expected.put("eps_unit","USD/shares");expected.put("raw_json",p.recovered().path("rawJson").asText());
                QuantEpsRecoveryApply.require(QuantEpsRecoveryApply.equalCore(QuantEpsRecoveryApply.standard(c,p.original().path("id").asLong(),false),expected,new ArrayList<>(expected.keySet())),"Full-column postimage mismatch");
                QuantEpsRecoveryApply.require(QuantEpsRecoveryApply.equalCore(QuantEpsRecoveryApply.existingFact(c,p.fact(),false),p.fact(),QuantEpsRecoveryApply.FACT_CORE),"Source fact postimage mismatch");
                verified++;
            }
            QuantEpsRecoveryApply.require(verified==355,"Exact355 targets required");
            var counts=new TreeMap<String,Long>();for(String table:List.of("stock_sec_fact","stock_sec_financial_standard"))try(var s=c.createStatement();var r=s.executeQuery("SELECT COUNT(*) FROM "+table)){r.next();counts.put(table,r.getLong(1));}
            try(var out=Files.newOutputStream(Path.of(args[1]),StandardOpenOption.CREATE_NEW)){j.writerWithDefaultPrettyPrinter().writeValue(out,Map.of("verifiedUtc",java.time.Instant.now().toString(),"freshConnection",true,"readOnly",true,"verifiedStandards",verified,"verifiedSourceFacts",verified,"allOtherColumnsMatchBackup",true,"databaseCounts",counts,"backupSha256",QuantEpsRecoveryPrepare.sha(Path.of(args[0]))));}
            c.rollback();
        }
        System.out.println("FRESH_READ_ONLY_POSTIMAGE_VERIFIED standards="+verified+" facts="+verified+" allOtherColumnsUnchanged=true");
    }
}
