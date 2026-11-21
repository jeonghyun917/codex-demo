import java.nio.file.*;
import java.util.*;

/** Adapts immutable matched-result and original frozen-context files for the file-only leakage check. */
public final class QuantMatchedFinancialLeakageCheck {
    public static void main(String[] args)throws Exception{
        if(args.length!=5)throw new IllegalArgumentException("Usage: matched-result-dir original-context-dir prepared-input decision NEW-output-dir");
        var output=Path.of(args[4]);if(Files.exists(output))throw new IllegalStateException("Refuse output overwrite");
        Files.createDirectories(output);var adapter=output.resolve("immutable-input-adapter");Files.createDirectory(adapter);
        Files.copy(Path.of(args[0]).resolve("result.json"),adapter.resolve("result.json"));
        Files.copy(Path.of(args[1]).resolve("frozen-context.json"),adapter.resolve("frozen-context.json"));
        QuantRestandardizedLeakageCheck.main(new String[]{adapter.toString(),args[2],args[3],output.resolve("counterfactual").toString()});
        var hashes=new TreeMap<String,String>();
        for(String p:List.of("scripts/QuantMatchedFinancialLeakageCheck.java",Path.of(args[0]).resolve("result.json").toString(),Path.of(args[1]).resolve("frozen-context.json").toString(),output.resolve("counterfactual/verification.json").toString()))hashes.put(p,QuantRestandardizedReplay.sha(p));
        Files.writeString(output.resolve("source-hashes.json"),QuantRestandardizedReplay.json.writerWithDefaultPrettyPrinter().writeValueAsString(hashes),StandardOpenOption.CREATE_NEW);
    }
}
