package com.kingyurina.demo.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class HomePageTemplateContractTest {

    @Test
    void homepageDeclaresSemanticBrikSurfaceAndResponsiveContracts() throws IOException {
        String template = resource("templates/index.html");
        String css = resource("static/css/home-brik.css");

        assertTrue(template.contains("home-brik.css"));
        assertTrue(template.contains("class=\"home-page\""));
        assertTrue(template.contains("data-home-nav"));
        assertTrue(template.contains("th:each=\"menu : ${mainMenus}\""));
        assertTrue(template.contains("id=\"main-content\""));
        assertTrue(template.contains("data-home-composer"));
        assertTrue(template.contains("action=\"/stocks\""));
        assertTrue(template.contains("name=\"symbol\""));
        assertTrue(template.contains("data-home-instrument"));
        assertTrue(template.contains("data-home-metric=\"signal\""));
        assertTrue(template.contains("data-home-metric=\"alpha\""));
        assertTrue(template.contains("data-home-metric=\"risk\""));
        assertTrue(template.contains("id=\"systems\""));
        assertTrue(template.contains("data-home-product-card"));
        assertTrue(template.contains("id=\"contact\""));
        assertTrue(css.contains("@media (max-width: 767px)"));
        assertTrue(css.contains("@media (min-width: 768px)"));
        assertTrue(css.contains("@media (min-width: 1024px)"));
        assertTrue(css.contains("@media (prefers-reduced-motion: reduce)"));
        assertTrue(css.contains("min-height: 44px"));
        assertTrue(css.contains(":focus-visible"));
        assertFalse(template.contains("home-aurora"));
        assertFalse(template.contains("data-aurora"));
        assertFalse(template.contains("http://"));
        assertFalse(template.contains("https://"));
    }

    @Test
    void homepageLinksEveryApprovedProductRouteAndUsesOnePrimaryHeading() throws IOException {
        String template = resource("templates/index.html");

        assertTrue(template.contains("href=\"/quant\""));
        assertTrue(template.contains("href=\"/quant?index=SP500\""));
        assertTrue(template.contains("href=\"/stocks\""));
        assertTrue(template.contains("href=\"/stocks/heatmap?index=SP500\""));
        assertTrue(template.contains("href=\"/etfs\""));
        assertTrue(template.contains("href=\"/atelier\""));
        assertTrue(template.contains("href=\"/signals/backtest\""));
        assertEquals(1, occurrences(template, "<h1"));
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

    private static String resource(String path) throws IOException {
        ClassPathResource resource = new ClassPathResource(path);
        if (!resource.exists()) {
            return "";
        }
        return resource.getContentAsString(StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');
    }
}
