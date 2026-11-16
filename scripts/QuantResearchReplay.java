import com.kingyurina.demo.CodexDemoApplication;
import com.kingyurina.demo.stock.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.List;
import java.util.function.Supplier;

/** Explicit offline replay. Never generates LIVE rows or backdates availability timestamps. */
public final class QuantResearchReplay {
    public static void main(String[] args) {
        var supported = List.of("SP500_FREE_20261018", "NASDAQ100_FREE_20261018");
        List<String> indexes = args.length == 0 ? supported : List.of(args[0]);
        int fromVersion = args.length < 2 ? 2 : Integer.parseInt(args[1]);
        if (!supported.containsAll(indexes) || fromVersion < 2 || fromVersion > 9)
            throw new IllegalArgumentException("usage: [research-index] [resume-from-version 2..9]");
        var app = new SpringApplication(CodexDemoApplication.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        try (var context = app.run("--spring.profiles.active=mariadb", "--spring.sql.init.mode=never",
                "--app.signal.refresh.enabled=false", "--app.signal.refresh.run-on-startup=false")) {
            var service = context.getBean(StockPortfolioBacktestService.class);
            var tx = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
            for (String index : indexes) {
                long start = System.nanoTime();
                System.out.println("RESEARCH_REPLAY index=" + index + " classification=RECONSTRUCTED_NOT_PIT live=false");
                if (fromVersion <= 2) stage(tx, "V2", () -> service.refreshExpectedReturnSnapshots(index, 200000, 1000, 300));
                if (fromVersion <= 3) stage(tx, "V3", () -> service.refreshExpectedReturnV3Snapshots(index, 200000, 1000, 300));
                if (fromVersion <= 4) stage(tx, "V4", () -> service.refreshExpectedReturnV4Snapshots(index, 200000, 1000, 300));
                if (fromVersion <= 5) stage(tx, "V5", () -> service.refreshExpectedReturnV5Snapshots(index, 200000, 1000, 300));
                if (fromVersion <= 6) stage(tx, "V6", () -> service.refreshExpectedReturnV6Snapshots(index, 200000, 1000, 300));
                if (fromVersion <= 7) stage(tx, "V7", () -> service.refreshExpectedReturnV7Snapshots(index, 200000, 1000, 300));
                // Strict-vintage macro rows are absent in the research namespace: V8 keeps
                // its existing explicit no-macro behavior rather than borrowing later data.
                if (fromVersion <= 8) stage(tx, "V8", () -> context.getBean(StockExpectedReturnV8Service.class)
                        .refreshHistorical(index, 200000, 1000));
                stage(tx, "V9", () -> context.getBean(StockExpectedReturnV9Service.class)
                        .refreshHistorical(index, 200000, 1000));
                System.out.printf("REPLAY_DONE index=%s elapsedSeconds=%.1f%n", index,
                        (System.nanoTime() - start) / 1e9);
            }
        }
    }
    private static void stage(TransactionTemplate tx, String name, Supplier<?> task) {
        long started = System.nanoTime();
        System.out.println("REPLAY_STAGE_START " + name);
        Object result = tx.execute(status -> task.get());
        System.out.printf("REPLAY_STAGE_DONE %s elapsedSeconds=%.1f result=%s%n", name,
                (System.nanoTime() - started) / 1e9, result);
    }
}
