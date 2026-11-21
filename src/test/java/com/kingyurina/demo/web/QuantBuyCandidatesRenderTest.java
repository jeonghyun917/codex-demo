package com.kingyurina.demo.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.ui.ExtendedModelMap;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;
import com.kingyurina.demo.menu.MenuService;
import com.kingyurina.demo.stock.*;

class QuantBuyCandidatesRenderTest {
    private String render(QuantBuyCandidatesService.View view) {
        var resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setCharacterEncoding("UTF-8");
        var engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        var servlet = new MockServletContext();
        var exchange = JakartaServletWebApplication.buildApplication(servlet).buildExchange(
                new MockHttpServletRequest(servlet), new MockHttpServletResponse());
        return engine.process("fragments/quant-buy-candidates", new WebContext(exchange, Locale.KOREAN,
                Map.of("buyCandidates", view)));
    }

    @Test void unavailableViewDoesNotInventCandidatesOrProbabilities() {
        String html = render(QuantBuyCandidatesService.unavailable());
        assertThat(html).contains("익절 확률 상위", "관심 종목", "TOP20", "매수 검토 보류");
        assertThat(html).doesNotContain("class=\"buy-table\"", "th:text=", "0.00%");
    }

    @Test void readyViewSeparatesPredictionFromHistoricalEvidenceAndOrders() {
        var row = Map.<String, Object>of("symbol", "AAPL", "probability", "52.80%", "referencePrice", "미기록",
                "tpRate", "25.00%", "slRate", "50.00%", "meanReturn", "1.20%", "sample", "12건 · 소표본",
                "buyEligible", false, "buyReason", "시장 추세 미충족 · 변동성 초과");
        var evidence = Map.<String, Object>of("start", "2025-01-02", "end", "2026-09-16", "fee", "0.10%");
        String html = render(new QuantBuyCandidatesService.View("READY", "조건 통과", "2026-11-13",
                "2026-11-13T22:00:00Z", "2026-11-16T14:30:00Z", "1.20%", "2.00%", 1, List.of(row), evidence));
        assertThat(html).contains("/stocks/AAPL", "52.80%", "25.00%", "12건 · 소표본", "과거 손절률",
                "주문·체결이 아닙니다", "저장 이후 모의운용 성과가 아닙니다", "매수 보류", "시장 추세 미충족 · 변동성 초과");
        assertThat(html).doesNotContain("th:text=");
    }

    @Test void controllerConnectsReadOnlyCandidateProjection() {
        var snapshots = mock(StockDashboardViewSnapshotService.class);
        var candidates = mock(QuantBuyCandidatesService.class);
        var view = QuantBuyCandidatesService.unavailable();
        when(snapshots.build("SP500")).thenReturn(new StockDashboardViewPayload("SP500", null, null, null, null, null));
        when(candidates.build("SP500")).thenReturn(view);
        var model = new ExtendedModelMap();
        new HomeController(mock(MenuService.class), snapshots, null, candidates).quant("SP500", model);
        assertThat(model.get("buyCandidates")).isSameAs(view);
        verify(candidates).build("SP500");
    }
}
