import com.kingyurina.demo.CodexDemoApplication;
import com.kingyurina.demo.stock.*;
import com.kingyurina.demo.stock.evaluation.ExpectedReturnEvaluationSummaryService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;

/** Read-only component timings; no warm-up, refresh job, external fetch or data mutation. */
public class QuantDashboardTimings {
    public static void main(String[] args) {
        var app = new SpringApplication(CodexDemoApplication.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        try (var context = app.run("--spring.profiles.active=mariadb", "--spring.sql.init.mode=never",
                "--app.signal.refresh.enabled=false", "--app.signal.refresh.run-on-startup=false")) {
            long start = System.nanoTime();
            var market = context.getBean(StockMarketViewService.class).build("SP500");
            System.out.printf("COMPONENT market seconds=%.3f%n", (System.nanoTime() - start) / 1e9);
            start = System.nanoTime();
            context.getBean(StockMacroRegimeService.class).latestView("SP500");
            System.out.printf("COMPONENT macro seconds=%.3f%n", (System.nanoTime() - start) / 1e9);
            start = System.nanoTime();
            var mapper = context.getBean(StockBacktestMapper.class);
            mapper.findQuantModelHealth("SP500");
            System.out.printf("COMPONENT health_layers seconds=%.3f%n", (System.nanoTime() - start) / 1e9);
            start = System.nanoTime();
            mapper.findQuantOperationHealth();
            System.out.printf("COMPONENT health_operations seconds=%.3f%n", (System.nanoTime() - start) / 1e9);
            start = System.nanoTime();
            mapper.findPitQuality("SP500");
            System.out.printf("COMPONENT health_pit seconds=%.3f%n", (System.nanoTime() - start) / 1e9);
            start = System.nanoTime();
            context.getBean(ExpectedReturnEvaluationSummaryService.class).build("SP500");
            System.out.printf("COMPONENT evaluation seconds=%.3f%n", (System.nanoTime() - start) / 1e9);
        }
    }
}
