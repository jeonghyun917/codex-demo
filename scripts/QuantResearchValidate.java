import com.kingyurina.demo.CodexDemoApplication;
import com.kingyurina.demo.stock.*;
import com.kingyurina.demo.stock.evaluation.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import tools.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/** Persist descriptive research diagnostics separately from the unchanged strict production gate. */
public final class QuantResearchValidate {
    public static void main(String[] args) throws Exception {
        Path output = Path.of(args.length == 0 ? "tmp/quant-research-validation-20261018.json" : args[0]);
        var supported = List.of("SP500_FREE_20261018", "NASDAQ100_FREE_20261018");
        var indexes = args.length < 2 ? supported : List.of(args[1]);
        if (!supported.containsAll(indexes)) throw new IllegalArgumentException("Unknown research namespace");
        LocalDate asOf = LocalDate.of(2026, 10, 18), from = LocalDate.of(2023, 1, 1);
        var app = new SpringApplication(CodexDemoApplication.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        try (var context = app.run("--spring.profiles.active=mariadb", "--spring.sql.init.mode=never",
                "--app.signal.refresh.enabled=false", "--app.signal.refresh.run-on-startup=false")) {
            var mapper = context.getBean(StockBacktestMapper.class);
            var diagnostics = new ReconstructedReturnDiagnostics();
            var reports = new ArrayList<ReconstructedReturnDiagnostics.Report>();
            for (String index : indexes) {
                var predictions = mapper.findExpectedReturnSnapshots(index, from, asOf);
                var outcomes = mapper.findResults(index, 200000);
                var benchmarks = mapper.findBenchmarkReturns(index, from.minusMonths(3), asOf);
                var members = mapper.findEvaluationMembershipSnapshots(index, from, asOf);
                for (int version = 2; version <= 9; version++) {
                    var report = diagnostics.evaluate(index, "EXPECTED_RETURN_V" + version + "_CORRECTED_20261017",
                            predictions, outcomes, benchmarks, members, asOf);
                    reports.add(report);
                    System.out.printf("RESEARCH_DIAGNOSTIC index=%s model=V%d usable=%d targets=%d coverage=%.2f%% IC=%s Brier=%s%n",
                            index, version, report.usableRows(), report.targetCount(), report.coveragePct(),
                            report.metrics().rankIc(), report.metrics().brierScore());
                }
            }
            var strict = indexes.contains("SP500_FREE_20261018")
                    ? context.getBean(ExpectedReturnEvaluationService.class).evaluateBaseline("SP500", asOf) : null;
            var result = new LinkedHashMap<String, Object>();
            result.put("asOf", asOf); result.put("researchReports", reports);
            result.put("strictProductionEvaluation", strict);
            result.put("conclusion", "RESEARCH_ONLY_NOT_PROMOTION_OR_PROFITABILITY_PROOF");
            Files.writeString(output, context.getBean(ObjectMapper.class).writerWithDefaultPrettyPrinter().writeValueAsString(result));
            if (strict != null) {
                System.out.println("STRICT_EVALUATION id=" + strict.getId() + " status=" + strict.getStatus()
                        + " validRows=" + strict.getValidRowCount() + " validMonths=" + strict.getValidMonths());
                if ("FAILED".equals(strict.getStatus())) throw new IllegalStateException("Strict evaluator execution failed");
            }
        }
    }
}
