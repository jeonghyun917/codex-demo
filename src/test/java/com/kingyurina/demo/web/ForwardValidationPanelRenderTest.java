package com.kingyurina.demo.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import com.kingyurina.demo.stock.StockForwardValidationStatusService;

class ForwardValidationPanelRenderTest {
    @Test
    void actualDashboardPanelRendersMissingStatusWithoutFabricatedZeroScore() {
        var resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setCharacterEncoding("UTF-8");
        var engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        var context = new Context();
        context.setVariable("forwardValidation", StockForwardValidationStatusService.unavailable());

        String rendered = engine.process("dashboard", Set.of("section[aria-labelledby='forward-validation-title']"), context);

        assertThat(rendered).contains("전방 상태 미발행", "미평가", "예측 오차는 투자 수익이 아닙니다.");
        assertThat(rendered).doesNotContain("0.0000%p", "th:text=");
    }
}
