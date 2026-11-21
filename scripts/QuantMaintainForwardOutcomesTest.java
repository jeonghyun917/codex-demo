import tools.jackson.databind.ObjectMapper;
import java.time.LocalDate;

public final class QuantMaintainForwardOutcomesTest {
    public static void main(String[] args) throws Exception {
        var json = new ObjectMapper();
        String good = "{\"completedSession\":\"2026-12-02\",\"knownSessions\":[\"2026-12-01\",\"2026-12-02\"],\"indices\":[\"DOW30\"]}";
        var value = QuantMaintainForwardOutcomes.validateRequest(json.readTree(good), LocalDate.of(2026,12,3));
        if (!value.completed().equals(LocalDate.of(2026,12,2)) || !value.indices().equals(java.util.List.of("DOW30")))
            throw new AssertionError("Valid known completed request was not preserved");
        for (String invalid : java.util.List.of(
                good.replace("2026-12-02\",\"known", "2026-12-03\",\"known"),
                good.replace("2026-12-02\",\"known", "2026-11-30\",\"known"),
                good.replace("\"DOW30\"", "\"OTHER\""),
                good.replace("\"DOW30\"", "\"DOW30\",\"DOW30\""),
                good.replace("\"DOW30\"", ""),
                good.replace("\"2026-12-01\",", "\"2026-12-02\","))) {
            try {
                QuantMaintainForwardOutcomes.validateRequest(json.readTree(invalid), LocalDate.of(2026,12,3));
                throw new AssertionError("Invalid request was admitted: " + invalid);
            } catch (IllegalArgumentException expected) { }
        }
        System.out.println("Outcome request boundary tests: 7 PASS; no DB/network opened");
    }
}
