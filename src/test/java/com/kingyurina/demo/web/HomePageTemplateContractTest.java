package com.kingyurina.demo.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    @Test
    void homepageBindsExactlySixProductCardsToTheirApprovedRoutes() throws IOException {
        String template = resource("templates/index.html");

        assertEquals(6, occurrences(template, "data-home-product-card"));
        assertTrue(hasProductCardRoute(template, "/quant"));
        assertTrue(hasProductCardRoute(template, "/stocks"));
        assertTrue(hasProductCardRoute(template, "/stocks/heatmap"));
        assertTrue(hasProductCardRoute(template, "/etfs"));
        assertTrue(hasProductCardRoute(template, "/atelier"));
        assertTrue(hasProductCardRoute(template, "/signals/backtest"));
    }

    @Test
    void homepageMotionAvoidsPaintTransitionsAndNoscriptNoticeStaysInFlow() throws IOException {
        String css = resource("static/css/home-brik.css");
        String noscriptRule = cssRule(css, ".home-noscript");

        assertFalse(transitionsProperty(css, "background-color"));
        assertFalse(transitionsProperty(css, "color"));
        assertFalse(transitionsProperty(css, "border-color"));
        assertFalse(transitionsProperty(css, "box-shadow"));
        assertFalse(css.contains("240ms"));
        assertFalse(noscriptRule.contains("position: fixed"));
    }

    @Test
    void homepageLoadsLocalProgressiveMotionRuntimeWithoutNetworkTransports() throws IOException {
        String template = resource("templates/index.html");
        String entry = resource("static/js/home-brik.js");

        assertTrue(template.contains("/js/home-brik.js"));
        assertTrue(entry.contains("./home-brik-motion.js"));
        assertTrue(entry.contains("getContext(\"2d\")"));
        assertTrue(entry.contains("IntersectionObserver"));
        assertTrue(entry.contains("requestAnimationFrame"));
        assertTrue(entry.contains("visibilitychange"));
        assertTrue(entry.contains("pointermove"));
        assertTrue(entry.contains("pointerleave"));
        assertTrue(entry.contains(".animate("));
        assertFalse(entry.contains("fetch("));
        assertFalse(entry.contains("XMLHttpRequest"));
        assertFalse(entry.contains("WebSocket"));
        assertFalse(entry.contains("EventSource"));
        assertFalse(entry.contains("http://"));
        assertFalse(entry.contains("https://"));
    }

    private static boolean hasProductCardRoute(String template, String href) {
        Pattern productCard = Pattern.compile(
                "<a\\b(?=[^>]*\\bdata-home-product-card\\b)"
                        + "(?=[^>]*\\bhref=\"" + Pattern.quote(href) + "\")[^>]*>",
                Pattern.DOTALL
        );
        return productCard.matcher(template).find();
    }

    private static boolean transitionsProperty(String css, String property) {
        Matcher declarations = Pattern.compile("transition\\s*:\\s*([^;]+);", Pattern.DOTALL)
                .matcher(css);

        while (declarations.find()) {
            for (String transition : declarations.group(1).split(",")) {
                String value = transition.strip();
                if (value.equals(property) || value.startsWith(property + " ")) {
                    return true;
                }
            }
        }

        return false;
    }

    private static String cssRule(String css, String selector) {
        int start = css.indexOf(selector + " {");
        if (start < 0) {
            return "";
        }

        int end = css.indexOf('}', start);
        return end < 0 ? css.substring(start) : css.substring(start, end + 1);
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
