import java.sql.*;
import java.nio.file.*;
import java.util.*;
import tools.jackson.databind.node.ObjectNode;

public final class QuantEpsRecoveryApplyTest {
    static void check(boolean ok,String m){if(!ok)throw new AssertionError(m);}
    static void expectFailure(Throwing task)throws Exception{boolean failed=false;try{task.run();}catch(Exception expected){failed=true;}check(failed,"expected failure");}
    interface Throwing{void run()throws Exception;}
    static Connection db(String name)throws Exception{
        var c=DriverManager.getConnection("jdbc:h2:mem:"+name+";MODE=MariaDB;DB_CLOSE_DELAY=-1","sa","");
        try(var s=c.createStatement()){
            s.execute("CREATE TABLE stock_sec_financial_standard(id BIGINT PRIMARY KEY,symbol VARCHAR,cik VARCHAR,fiscal_year INT,fiscal_period VARCHAR,form VARCHAR,filed_at DATE,start_date DATE,end_date DATE,accession_number VARCHAR,period_days INT,currency VARCHAR,revenue DECIMAL(32,6),operating_income DECIMAL(32,6),net_income DECIMAL(32,6),eps_diluted DECIMAL(24,6),assets DECIMAL(32,6),liabilities DECIMAL(32,6),equity DECIMAL(32,6),operating_cash_flow DECIMAL(32,6),eps_unit VARCHAR,raw_json CLOB,mapped_at TIMESTAMP)");
            s.execute("CREATE TABLE stock_sec_fact(id BIGINT AUTO_INCREMENT PRIMARY KEY,symbol VARCHAR,cik VARCHAR,taxonomy VARCHAR,concept VARCHAR,unit VARCHAR,fiscal_year INT,fiscal_period VARCHAR,form VARCHAR,filed_at DATE,start_date DATE,end_date DATE,accession_number VARCHAR,frame VARCHAR,`value` DECIMAL(32,6),raw_json CLOB,fetched_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, UNIQUE(symbol,taxonomy,concept,unit,accession_number,fiscal_year,fiscal_period,start_date,end_date))");
        }return c;
    }
    static String column(String camel){return camel.replaceAll("([a-z])([A-Z])","$1_$2").toLowerCase();}
    static void seed(Connection c,ObjectNode n)throws Exception{
        var names=new ArrayList<String>();var values=new ArrayList<String>();
        for(var e:n.properties()){names.add(column(e.getKey()));values.add(e.getValue().isNull()?null:e.getValue().asText());}
        try(var p=c.prepareStatement("INSERT INTO stock_sec_financial_standard("+String.join(",",names)+") VALUES("+String.join(",",Collections.nCopies(names.size(),"?"))+")")){
            for(int i=0;i<values.size();i++)p.setString(i+1,values.get(i));p.executeUpdate();
        }
    }
    static List<ObjectNode> oldRows(){var a=QuantEpsRecoveryPrepareTest.old();var b=a.deepCopy();b.put("id",43);b.put("accessionNumber","b");return List.of(a,b);}
    static List<ObjectNode> newRows(List<ObjectNode> old,Path dir)throws Exception{
        var out=new ArrayList<ObjectNode>();
        for(var o:old){String accession=o.path("accessionNumber").asText();var archive=QuantEpsRecoveryPrepareTest.archive().deepCopy();
            var obs=archive.at("/facts/us-gaap/EarningsPerShareDiluted/units/USD~1shares/0");((ObjectNode)obs).put("accn",accession);
            Path file=dir.resolve(accession+".json");Files.writeString(file,archive.toString(),StandardOpenOption.CREATE_NEW);
            var full=QuantEpsRecoveryPrepareTest.full();full.put("accessionNumber",accession);
            var raw=(ObjectNode)QuantEpsRecoveryPrepare.JSON.readTree(full.path("rawJson").asText());((ObjectNode)raw.at("/selectedFieldEvidence/epsDiluted")).put("sourceObservationJson",obs.toString());full.put("rawJson",raw.toString());
            out.add(QuantEpsRecoveryPrepare.recover(o,full,archive,file,QuantEpsRecoveryPrepare.sha(file)));
        }return out;
    }
    static long count(Connection c,String table,String where)throws Exception{try(var s=c.createStatement();var r=s.executeQuery("SELECT COUNT(*) FROM "+table+" WHERE "+where)){r.next();return r.getLong(1);}}
    static void scenario(String name)throws Exception{
        Path dir=Files.createTempDirectory(Path.of("tmp/quant-eps-standardizer-trace-20261101-v1"),"apply-test-");var old=oldRows();var recovered=newRows(old,dir);
        try(var c=db(name)){
            for(var n:old)seed(c,n);
            var plans=QuantEpsRecoveryApply.plan(c,old,recovered);check(plans.size()==2,"two repair plans");
            switch(name){
                case "success"->{QuantEpsRecoveryApply.execute(c,plans,false,-1);check(count(c,"stock_sec_financial_standard","eps_diluted=3.82 AND revenue=123")==2,"EPS-only updates");check(count(c,"stock_sec_fact","concept='EarningsPerShareDiluted' AND `value`=3.82")==2,"only primary EPS facts inserted");}
                case "probe"->{QuantEpsRecoveryApply.execute(c,plans,true,-1);check(count(c,"stock_sec_financial_standard","eps_diluted IS NULL")==2,"probe restores both");check(count(c,"stock_sec_fact","1=1")==0,"probe restores raw table");}
                case "changed"->{try(var s=c.createStatement()){s.executeUpdate("UPDATE stock_sec_financial_standard SET revenue=124 WHERE id=43");}expectFailure(()->QuantEpsRecoveryApply.execute(c,plans,false,-1));check(count(c,"stock_sec_financial_standard","eps_diluted IS NULL")==2,"preimage failure cannot change any EPS");check(count(c,"stock_sec_fact","1=1")==0,"preimage failure no raw insert");}
                case "injected"->{expectFailure(()->QuantEpsRecoveryApply.execute(c,plans,false,1));check(count(c,"stock_sec_financial_standard","eps_diluted IS NULL")==2,"failure after first update restores all");check(count(c,"stock_sec_fact","1=1")==0,"failure restores raw inserts");}
                case "nonnull"->{try(var s=c.createStatement()){s.executeUpdate("UPDATE stock_sec_financial_standard SET eps_diluted=9 WHERE id=42");}expectFailure(()->QuantEpsRecoveryApply.plan(c,old,recovered));}
                case "nullid"->{var o=old.get(0).deepCopy();o.putNull("id");var n=recovered.get(0).deepCopy();n.putNull("id");check(QuantEpsRecoveryApply.plan(c,List.of(o),List.of(n)).isEmpty(),"null IDs skipped");}
                case "rawconflict"->{try(var s=c.createStatement()){s.executeUpdate("INSERT INTO stock_sec_fact(symbol,cik,taxonomy,concept,unit,fiscal_year,fiscal_period,form,filed_at,start_date,end_date,accession_number,`value`,raw_json) VALUES('WBA','0001618921','us-gaap','EarningsPerShareDiluted','USD/shares',2016,'FY','10-K','2016-10-20','2015-09-01','2016-08-31','a',99,'{}')");}expectFailure(()->QuantEpsRecoveryApply.execute(c,plans,false,-1));check(count(c,"stock_sec_fact","`value`=99")==1,"conflicting raw never overwritten");check(count(c,"stock_sec_financial_standard","eps_diluted IS NULL")==2,"raw conflict atomic no EPS update");}
                case "existingraw"->{QuantEpsRecoveryApply.insertFact(c,plans.get(0).fact());var before=QuantEpsRecoveryApply.existingFact(c,plans.get(0).fact(),false);var withExisting=QuantEpsRecoveryApply.plan(c,old,recovered);QuantEpsRecoveryApply.execute(c,withExisting,false,-1);check(before.equals(QuantEpsRecoveryApply.existingFact(c,plans.get(0).fact(),false)),"existing exact raw all columns/id/fetchedAt unchanged");check(count(c,"stock_sec_fact","1=1")==2,"only one missing fact inserted");}
                case "sealed"->{
                    Path pack=dir.resolve("pack");Files.createDirectory(pack);QuantEpsRecoveryApply.write(pack.resolve("original-selected-standards.json"),Map.of("WBA",old));QuantEpsRecoveryApply.write(pack.resolve("eps-recovered-selected-standards.json"),Map.of("WBA",recovered));
                    Path stage=dir.resolve("stage");QuantEpsRecoveryApply.prepare(c,pack,stage);Path manifest=stage.resolve("manifest.json");String hash=QuantEpsRecoveryPrepare.sha(manifest);
                    expectFailure(()->QuantEpsRecoveryApply.apply(c,manifest,"WRONG",false));check(count(c,"stock_sec_fact","1=1")==0,"wrong external SHA no writes");
                    QuantEpsRecoveryApply.apply(c,manifest,hash,true);check(count(c,"stock_sec_financial_standard","eps_diluted IS NULL")==2,"sealed rollback probe");
                    QuantEpsRecoveryApply.apply(c,manifest,hash,false);check(count(c,"stock_sec_financial_standard","eps_diluted=3.82 AND revenue=123")==2,"sealed manifest actual H2 commit");
                    expectFailure(()->QuantEpsRecoveryApply.apply(c,manifest,hash,false));check(count(c,"stock_sec_fact","1=1")==2,"replay preimage rejected no new raw");
                }
            }
        }
    }
    public static void main(String[] args)throws Exception{
        for(String scenario:List.of("success","probe","changed","injected","nonnull","nullid","rawconflict","existingraw","sealed"))scenario(scenario);
        System.out.println("PASS nine H2 EPS-only apply scenarios (including sealed prepare/probe/apply)");
    }
}
