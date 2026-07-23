# Quant 3-Month High Ratio Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the Quant table's Value, Growth, Momentum, and Risk columns with one sortable current-price-to-3-month-high percentage.

**Architecture:** Aggregate one raw three-month high per symbol in the existing market-row SQL, anchored to the latest stored candle date. Keep ratio validation, rounding, display text, and semantic tone in a small Java value object, then expose it through the existing view model to Thymeleaf and the table sorter.

**Tech Stack:** Java 21, Spring Boot, MyBatis XML, MariaDB, Thymeleaf, vanilla JavaScript, CSS, JUnit 5, Maven.

## Global Constraints

- Keep the interactive page request DB-backed; add no external API calls.
- Use `MAX(stock_candle_daily.high_price)` from the latest stored trade date back three calendar months, inclusive.
- Calculate `current_price / three_month_high * 100`.
- Display exactly two decimal places and `%`; display `-` when unavailable.
- Allow ratios above `100.00%`; do not clamp.
- Use tones `near-high` for `>= 95`, `mid-range` for `>= 85` and `< 95`, and `extended` for `< 85`.
- Remove only the four Quant table presentations; preserve the underlying factor scores and signal model.
- Avoid N+1 candle queries and new schema objects.
- Preserve unrelated tracked and untracked files.
- Run Maven with `mvn.cmd "-Dmaven.repo.local=.m2/repository"`.

---

### Task 1: Ratio Domain Value and View Projection

**Files:**
- Create: `src/main/java/com/kingyurina/demo/stock/StockThreeMonthHighRatio.java`
- Modify: `src/main/java/com/kingyurina/demo/stock/StockMarketRow.java`
- Modify: `src/main/java/com/kingyurina/demo/stock/StockMarketView.java`
- Modify: `src/main/java/com/kingyurina/demo/stock/StockMarketViewService.java`
- Create: `src/test/java/com/kingyurina/demo/stock/StockThreeMonthHighRatioTest.java`

**Interfaces:**
- Consumes: nullable `BigDecimal currentPrice` and nullable `BigDecimal threeMonthHigh`.
- Produces: `StockThreeMonthHighRatio.from(BigDecimal, BigDecimal)` with `display()`, `value()`, and `tone()`.
- Produces: `StockMarketRow.getThreeMonthHigh()` / `setThreeMonthHigh(BigDecimal)`.
- Produces: `StockMarketView.Row` fields `threeMonthHighRatio`, `threeMonthHighRatioValue`, and `threeMonthHighRatioTone`.

- [ ] **Step 1: Write the failing ratio tests**

Create `StockThreeMonthHighRatioTest.java`:

```java
package com.kingyurina.demo.stock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class StockThreeMonthHighRatioTest {

    @Test
    void formatsCurrentPriceAsPercentOfThreeMonthHigh() {
        StockThreeMonthHighRatio ratio = StockThreeMonthHighRatio.from(
                new BigDecimal("325.04"), new BigDecimal("334.99"));

        assertEquals("97.03%", ratio.display());
        assertEquals(new BigDecimal("97.03"), ratio.value());
        assertEquals("near-high", ratio.tone());
    }

    @Test
    void keepsValuesAboveOneHundredPercent() {
        StockThreeMonthHighRatio ratio = StockThreeMonthHighRatio.from(
                new BigDecimal("101"), new BigDecimal("100"));

        assertEquals("101.00%", ratio.display());
        assertEquals(new BigDecimal("101.00"), ratio.value());
        assertEquals("near-high", ratio.tone());
    }

    @Test
    void appliesToneBoundaries() {
        assertEquals("near-high", ratio("95.00").tone());
        assertEquals("mid-range", ratio("94.99").tone());
        assertEquals("mid-range", ratio("85.00").tone());
        assertEquals("extended", ratio("84.99").tone());
    }

    @Test
    void returnsUnavailableForMissingOrNonPositiveInputs() {
        assertUnavailable(StockThreeMonthHighRatio.from(null, BigDecimal.TEN));
        assertUnavailable(StockThreeMonthHighRatio.from(BigDecimal.TEN, null));
        assertUnavailable(StockThreeMonthHighRatio.from(BigDecimal.ZERO, BigDecimal.TEN));
        assertUnavailable(StockThreeMonthHighRatio.from(BigDecimal.TEN, BigDecimal.ZERO));
        assertUnavailable(StockThreeMonthHighRatio.from(new BigDecimal("-1"), BigDecimal.TEN));
    }

    private static StockThreeMonthHighRatio ratio(String percent) {
        return StockThreeMonthHighRatio.from(new BigDecimal(percent), new BigDecimal("100"));
    }

    private static void assertUnavailable(StockThreeMonthHighRatio ratio) {
        assertEquals("-", ratio.display());
        assertNull(ratio.value());
        assertEquals("neutral", ratio.tone());
    }
}
```

- [ ] **Step 2: Run the focused test to verify RED**

Run:

```powershell
mvn.cmd "-Dmaven.repo.local=.m2/repository" -Dtest=StockThreeMonthHighRatioTest test
```

Expected: compilation failure because `StockThreeMonthHighRatio` does not exist.

- [ ] **Step 3: Implement the ratio value**

Create `StockThreeMonthHighRatio.java`:

```java
package com.kingyurina.demo.stock;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record StockThreeMonthHighRatio(String display, BigDecimal value, String tone) {

    public static StockThreeMonthHighRatio from(BigDecimal currentPrice, BigDecimal threeMonthHigh) {
        if (currentPrice == null || threeMonthHigh == null
                || currentPrice.signum() <= 0 || threeMonthHigh.signum() <= 0) {
            return new StockThreeMonthHighRatio("-", null, "neutral");
        }

        BigDecimal ratio = currentPrice
                .multiply(BigDecimal.valueOf(100))
                .divide(threeMonthHigh, 2, RoundingMode.HALF_UP);
        String tone = ratio.compareTo(BigDecimal.valueOf(95)) >= 0
                ? "near-high"
                : ratio.compareTo(BigDecimal.valueOf(85)) >= 0 ? "mid-range" : "extended";
        return new StockThreeMonthHighRatio(ratio.toPlainString() + "%", ratio, tone);
    }
}
```

- [ ] **Step 4: Add the raw mapper property and project the value**

In `StockMarketRow.java`, add:

```java
private BigDecimal threeMonthHigh;

public BigDecimal getThreeMonthHigh() {
    return threeMonthHigh;
}

public void setThreeMonthHigh(BigDecimal threeMonthHigh) {
    this.threeMonthHigh = threeMonthHigh;
}
```

Replace the four trailing factor fields in `StockMarketView.Row`:

```java
String threeMonthHighRatio,
BigDecimal threeMonthHighRatioValue,
String threeMonthHighRatioTone
```

Add `java.math.BigDecimal` to `StockMarketView.java`.

In `StockMarketViewService.toViewRow`, create the value before the constructor:

```java
StockThreeMonthHighRatio threeMonthHighRatio = StockThreeMonthHighRatio.from(
        row.getCurrentPrice(), row.getThreeMonthHigh());
```

Replace the four trailing score constructor arguments with:

```java
threeMonthHighRatio.display(),
threeMonthHighRatio.value(),
threeMonthHighRatio.tone()
```

- [ ] **Step 5: Run focused tests to verify GREEN**

Run:

```powershell
mvn.cmd "-Dmaven.repo.local=.m2/repository" -Dtest=StockThreeMonthHighRatioTest test
```

Expected: 4 tests pass.

- [ ] **Step 6: Commit Task 1**

```powershell
git add src/main/java/com/kingyurina/demo/stock/StockThreeMonthHighRatio.java `
        src/main/java/com/kingyurina/demo/stock/StockMarketRow.java `
        src/main/java/com/kingyurina/demo/stock/StockMarketView.java `
        src/main/java/com/kingyurina/demo/stock/StockMarketViewService.java `
        src/test/java/com/kingyurina/demo/stock/StockThreeMonthHighRatioTest.java
git commit -m "feat: calculate quant three-month high ratio"
```

---

### Task 2: One-Pass Three-Month Candle Aggregate

**Files:**
- Modify: `src/main/resources/mapper/stock/IndexConstituentMapper.xml`
- Create: `src/test/java/com/kingyurina/demo/stock/IndexConstituentThreeMonthHighContractTest.java`

**Interfaces:**
- Consumes: `stock_candle_daily(symbol, trade_date, high_price)`.
- Produces: MyBatis result alias `threeMonthHigh`, mapped to `StockMarketRow.threeMonthHigh`.
- Preserves: one `findMarketRows` query and one `findMarketRowsBySymbols` query, with no service-level candle query.

- [ ] **Step 1: Write the failing mapper contract**

Create `IndexConstituentThreeMonthHighContractTest.java`:

```java
package com.kingyurina.demo.stock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class IndexConstituentThreeMonthHighContractTest {

    @Test
    void marketQueriesProjectOneLatestDateAnchoredThreeMonthHigh() throws IOException {
        String xml = resource("mapper/stock/IndexConstituentMapper.xml");

        assertTrue(xml.contains("MAX(trade_date) AS latest_trade_date"));
        assertTrue(xml.contains("DATE_SUB(window.latest_trade_date, INTERVAL 3 MONTH)"));
        assertTrue(xml.contains("MAX(c.high_price) AS three_month_high"));
        assertTrue(xml.contains("highs.three_month_high AS threeMonthHigh"));
        assertEquals(2, occurrences(xml, "highs.three_month_high AS threeMonthHigh"));
        assertEquals(2, occurrences(xml, "<include refid=\"threeMonthHighBySymbol\"/>"));
        assertTrue(xml.contains("<sql id=\"threeMonthHighBySymbol\">"));
    }

    private static String resource(String path) throws IOException {
        return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
    }

    private static int occurrences(String source, String target) {
        int count = 0;
        int offset = 0;
        while ((offset = source.indexOf(target, offset)) >= 0) {
            count++;
            offset += target.length();
        }
        return count;
    }
}
```

- [ ] **Step 2: Run the mapper contract to verify RED**

Run:

```powershell
mvn.cmd "-Dmaven.repo.local=.m2/repository" -Dtest=IndexConstituentThreeMonthHighContractTest test
```

Expected: assertions fail because the aggregate and `threeMonthHigh` projection do not exist.

- [ ] **Step 3: Add the reusable aggregate SQL**

Near the top of `IndexConstituentMapper.xml`, add:

```xml
<sql id="threeMonthHighBySymbol">
    SELECT
        c.symbol,
        MAX(c.high_price) AS three_month_high
    FROM stock_candle_daily c
    CROSS JOIN (
        SELECT MAX(trade_date) AS latest_trade_date
        FROM stock_candle_daily
    ) window
    WHERE c.trade_date BETWEEN DATE_SUB(window.latest_trade_date, INTERVAL 3 MONTH)
                           AND window.latest_trade_date
    GROUP BY c.symbol
</sql>
```

- [ ] **Step 4: Project and join the aggregate in both market queries**

In both `findMarketRows` and `findMarketRowsBySymbols`:

```xml
highs.three_month_high AS threeMonthHigh,
```

Add the join, using the correct outer symbol alias:

```xml
LEFT JOIN (
    <include refid="threeMonthHighBySymbol"/>
) highs
    ON highs.symbol = i.symbol
```

For `findMarketRowsBySymbols`, use:

```xml
ON highs.symbol = p.symbol
```

- [ ] **Step 5: Run focused and mapper smoke tests**

Run:

```powershell
mvn.cmd "-Dmaven.repo.local=.m2/repository" `
    -Dtest=IndexConstituentThreeMonthHighContractTest,StockThreeMonthHighRatioTest test
```

Expected: all focused tests pass.

Run the SQL directly against local MariaDB:

```sql
SELECT q.symbol,
       q.current_price,
       MAX(c.high_price) AS three_month_high,
       ROUND(q.current_price / MAX(c.high_price) * 100, 2) AS ratio
FROM stock_quote_cache q
JOIN stock_candle_daily c
  ON c.symbol = q.symbol
 AND c.trade_date BETWEEN
     DATE_SUB((SELECT MAX(trade_date) FROM stock_candle_daily), INTERVAL 3 MONTH)
     AND (SELECT MAX(trade_date) FROM stock_candle_daily)
WHERE q.symbol IN ('AAPL', 'MSFT', 'NVDA')
GROUP BY q.symbol, q.current_price;
```

Expected: one non-null row per covered symbol and ratios matching the service formula.

- [ ] **Step 6: Commit Task 2**

```powershell
git add src/main/resources/mapper/stock/IndexConstituentMapper.xml `
        src/test/java/com/kingyurina/demo/stock/IndexConstituentThreeMonthHighContractTest.java
git commit -m "feat: load quant three-month highs"
```

---

### Task 3: Quant Table Column, Sorting, and Visual Tones

**Files:**
- Modify: `src/main/resources/templates/dashboard.html`
- Modify: `src/main/resources/static/css/main.css`
- Create: `src/test/java/com/kingyurina/demo/web/QuantDashboardThreeMonthHighContractTest.java`

**Interfaces:**
- Consumes: `row.threeMonthHighRatio`, `row.threeMonthHighRatioValue`, and `row.threeMonthHighRatioTone`.
- Produces: sortable key `threeMonthHighRatio` at child index `10`.
- Produces: CSS classes `three-month-high-ratio`, `near-high`, `mid-range`, `extended`, and `neutral`.

- [ ] **Step 1: Write the failing template and CSS contract**

Create `QuantDashboardThreeMonthHighContractTest.java`:

```java
package com.kingyurina.demo.web;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class QuantDashboardThreeMonthHighContractTest {

    @Test
    void quantTableReplacesFourFactorColumnsWithSortableThreeMonthHighRatio() throws IOException {
        String template = resource("templates/dashboard.html");

        assertFalse(template.contains("data-sort=\"valuation\""));
        assertFalse(template.contains("data-sort=\"growth\""));
        assertFalse(template.contains("data-sort=\"momentum\""));
        assertFalse(template.contains("data-sort=\"risk\""));
        assertFalse(template.contains("row.valuationScore"));
        assertFalse(template.contains("row.growthScore"));
        assertFalse(template.contains("row.momentumScore"));
        assertFalse(template.contains("row.riskScore"));
        assertTrue(template.contains("data-sort=\"threeMonthHighRatio\""));
        assertTrue(template.contains("3M 고점비율"));
        assertTrue(template.contains("row.threeMonthHighRatio"));
        assertTrue(template.contains("row.threeMonthHighRatioTone"));
        assertTrue(template.contains("threeMonthHighRatio: 10"));
    }

    @Test
    void quantTableDefinesElevenColumnsAndRatioTones() throws IOException {
        String css = resource("static/css/main.css");

        assertTrue(css.contains(".three-month-high-ratio"));
        assertTrue(css.contains(".three-month-high-ratio.near-high"));
        assertTrue(css.contains(".three-month-high-ratio.mid-range"));
        assertTrue(css.contains(".three-month-high-ratio.extended"));
        assertTrue(css.contains("min-width: 1080px"));
    }

    private static String resource(String path) throws IOException {
        return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
    }
}
```

- [ ] **Step 2: Run the contract to verify RED**

Run:

```powershell
mvn.cmd "-Dmaven.repo.local=.m2/repository" -Dtest=QuantDashboardThreeMonthHighContractTest test
```

Expected: assertions fail because the old columns remain and the new key/classes are absent.

- [ ] **Step 3: Replace the table headers and row cells**

In `dashboard.html`, replace the four trailing header cells with:

```html
<span class="dashboard-head-cell">
    <button class="sp-sort" type="button" data-sort="threeMonthHighRatio">3M 고점비율</button>
    <span class="header-help"
          tabindex="0"
          aria-label="3개월 고점 대비 현재가 비율 설명"
          data-tip="저장된 최신 거래일부터 3개월간의 장중 고가 최댓값을 100으로 본 현재가 비율입니다.">i</span>
</span>
```

Replace the four trailing row cells with:

```html
<span class="three-month-high-ratio"
      th:classappend="${row.threeMonthHighRatioTone}"
      th:text="${row.threeMonthHighRatio}"
      th:attr="data-sort-value=${row.threeMonthHighRatioValue}">-</span>
```

Replace the four sort-map entries with:

```javascript
threeMonthHighRatio: 10
```

Keep the existing numeric parser; `97.03%` parses as `97.03`, while `-` remains null.

- [ ] **Step 4: Reduce the grid and add ratio tones**

In `.quant-dashboard-row`, keep the first ten existing tracks through model confidence, then add:

```css
minmax(104px, 0.72fr);
```

Set:

```css
min-width: 1080px;
```

Add:

```css
.three-month-high-ratio {
    color: #dfe4f4;
    font-variant-numeric: tabular-nums;
}

.three-month-high-ratio.near-high {
    color: #7ee2ad;
}

.three-month-high-ratio.mid-range {
    color: #d9d58a;
}

.three-month-high-ratio.extended {
    color: #ff9d9d;
}

.three-month-high-ratio.neutral {
    color: var(--market-muted);
}
```

- [ ] **Step 5: Run focused and full automated verification**

Run:

```powershell
mvn.cmd "-Dmaven.repo.local=.m2/repository" `
    -Dtest=StockThreeMonthHighRatioTest,IndexConstituentThreeMonthHighContractTest,QuantDashboardThreeMonthHighContractTest test
```

Expected: focused tests pass.

Run:

```powershell
mvn.cmd "-Dmaven.repo.local=.m2/repository" test
```

Expected: full suite passes with zero failures.

Run:

```powershell
git diff --check
```

Expected: no output.

- [ ] **Step 6: Run browser verification**

Start the isolated worktree server on port `8082`, backed by local MariaDB, and inspect `/quant`.

Verify:

- headers `Value`, `Growth`, `Momentum`, and `Risk` are absent;
- `3M 고점비율` is present;
- AAPL, MSFT, and NVDA display numeric ratios consistent with the DB query;
- clicking the new header toggles descending/ascending numeric order;
- missing values display `-` after numeric values;
- the reduced table width has no clipped header or tooltip;
- no browser console warnings or errors occur at desktop and mobile widths.

- [ ] **Step 7: Commit Task 3**

```powershell
git add src/main/resources/templates/dashboard.html `
        src/main/resources/static/css/main.css `
        src/test/java/com/kingyurina/demo/web/QuantDashboardThreeMonthHighContractTest.java
git commit -m "feat: show quant three-month high ratio"
```

- [ ] **Step 8: Request final review**

Review the complete diff from the design-doc parent through Task 3 for:

- exact ratio definition and window anchor;
- single-query DB aggregation;
- null and boundary behavior;
- template/grid/sort-index alignment;
- retention of underlying factor-model fields;
- test coverage and unrelated-file preservation.
