import tools.jackson.databind.ObjectMapper;

/** Isolated policy regression; deliberately no DB connection or source imports. */
public final class QuantImportSourceFinancialTest {
    public static void main(String[] args)throws Exception{
        var json=new ObjectMapper();String rows="[{}]";
        for(int i=1;i<8;i++)rows=rows.substring(0,rows.length()-1)+",{}]";
        String prefix="{\"version\":\"SOURCE_FINANCIAL_BRIDGE_V1_20261105\",\"rows\":"+rows+",\"sourcePackSha256\":\"";
        QuantImportSourceFinancial.validateReviewedArtifact(json.readTree(prefix+"dd0a028de57e6261485cb36393a82ba2fb15bd39293932a90de5b46251fc1912\"}"));
        boolean rejected=false;
        try{QuantImportSourceFinancial.validateReviewedArtifact(json.readTree(prefix+"0".repeat(64)+"\"}"));}
        catch(IllegalArgumentException expected){rejected=true;}
        if(!rejected)throw new AssertionError("A different extraction/issuer pack must not be admitted under the reviewed version");
        System.out.println("SOURCE_ADMISSION_POLICY_TESTS_PASS 2");
    }
}
