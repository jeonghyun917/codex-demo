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
    void homepageRuntimeDependenciesAndFontsArePinnedLocally() throws IOException {
        assertTrue(new ClassPathResource("static/vendor/lenis/lenis.min.js").exists());
        assertTrue(new ClassPathResource("static/vendor/lenis/lenis.css").exists());
        assertTrue(new ClassPathResource("static/vendor/lenis/LICENSE").exists());
        assertTrue(new ClassPathResource("static/vendor/motion/motion.js").exists());
        assertTrue(new ClassPathResource("static/vendor/motion/LICENSE.md").exists());
        assertTrue(new ClassPathResource("static/js/vendor/three.module.js").exists());
        assertTrue(new ClassPathResource("static/js/vendor/three.LICENSE").exists());
        assertTrue(new ClassPathResource("static/fonts/instrument-sans-latin-wght-normal.woff2").exists());
        assertTrue(new ClassPathResource("static/fonts/ibm-plex-mono-latin-400-normal.woff2").exists());
        assertTrue(new ClassPathResource("static/fonts/ibm-plex-mono-latin-500-normal.woff2").exists());
        assertTrue(new ClassPathResource("static/fonts/Instrument-Sans-LICENSE").exists());
        assertTrue(new ClassPathResource("static/fonts/IBM-Plex-Mono-LICENSE").exists());

        assertTrue(resource("static/vendor/lenis/lenis.min.js").contains("1.3.25"));
        assertTrue(resource("static/vendor/motion/LICENSE.md").contains("MIT License"));
        assertTrue(resource("static/js/vendor/three.module.js").contains("const REVISION = '165'"));
    }

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
    void homepageRetiresAuroraRuntimeAndItsTemplateMarkers() throws IOException {
        String template = resource("templates/index.html");

        assertFalse(template.contains("home-aurora"));
        assertFalse(template.contains("data-aurora"));
        assertFalse(new ClassPathResource("static/css/home-aurora.css").exists());
        assertFalse(new ClassPathResource("static/js/home-aurora.js").exists());
        assertFalse(new ClassPathResource("static/js/home-aurora-field.js").exists());
        assertFalse(new ClassPathResource("static/js/home-aurora-motion.js").exists());
        assertFalse(new ClassPathResource("static/js/home-aurora-quality.js").exists());
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

    @Test
    void homepageCanvasUsesEventDrivenBoundedWakeAndSleepsWhenSettled() throws IOException {
        String entry = resource("static/js/home-brik.js");

        assertTrue(occurrences(entry, "shouldContinueHomeMotion") >= 2);
        assertTrue(entry.contains("wakeUntil"));
        assertTrue(entry.contains("wakeMotion"));
        assertFalse(entry.contains("setInterval"));
    }

    @Test
    void homepageHeroCopyUsesItsCssLoadRevealOnly() throws IOException {
        String template = resource("templates/index.html");
        String css = resource("static/css/home-brik.css");
        Pattern heroCopyReveal = Pattern.compile(
                "<[^>]*class=\"[^\"]*\\bhome-hero-copy\\b[^\"]*\""
                        + "[^>]*\\bdata-home-reveal\\b[^>]*>",
                Pattern.DOTALL
        );

        assertFalse(heroCopyReveal.matcher(template).find());
        assertTrue(cssRule(css, ".home-hero-copy").contains("animation: home-rise-in"));
    }

    @Test
    void homepageCompactsThePrimaryToolOnlyOnShortDesktopViewports() throws IOException {
        String css = resource("static/css/home-brik.css");
        String shortDesktop = cssMediaRule(css, "@media (min-width: 1024px) and (max-height: 820px)");

        assertFalse(shortDesktop.isEmpty());
        assertTrue(shortDesktop.contains(".home-hero-copy"));
        assertTrue(shortDesktop.contains(".home-hero h1"));
        assertTrue(shortDesktop.contains(".home-hero-summary"));
        assertTrue(shortDesktop.contains(".home-primary-link"));
        assertTrue(shortDesktop.contains(".home-tool-stage"));
        assertTrue(shortDesktop.contains(".home-composer-surface"));
        assertTrue(shortDesktop.contains(".home-instrument"));
        assertTrue(shortDesktop.contains(".home-metrics > div"));
    }

    @Test
    void homepageKeepsTheScrollableNavigationWithoutNativeScrollbars() throws IOException {
        String css = resource("static/css/home-brik.css");

        assertTrue(cssRule(css, ".home-nav-menu").contains("scrollbar-width: none"));
        assertTrue(cssRule(css, ".home-nav-menu::-webkit-scrollbar").contains("display: none"));
        assertFalse(css.contains("scrollbar-width: thin"));
    }

    @Test
    void homepagePlacesMobileNavigationInTwoExplicitRows() throws IOException {
        String css = resource("static/css/home-brik.css");
        String mobile = cssMediaRule(css, "@media (max-width: 767px)");

        assertTrue(cssRule(mobile, ".home-dashboard").contains("grid-column: 2"));
        assertTrue(cssRule(mobile, ".home-dashboard").contains("grid-row: 1"));
        assertTrue(cssRule(mobile, ".home-nav-menu").contains("grid-column: 1 / -1"));
        assertTrue(cssRule(mobile, ".home-nav-menu").contains("grid-row: 2"));
    }

    @Test
    void homepagePreservesAnEditorialButCompactMobileHero() throws IOException {
        String css = resource("static/css/home-brik.css");
        String mobile = cssMediaRule(css, "@media (max-width: 767px)");

        assertTrue(cssRule(mobile, ".home-hero-copy")
                .contains("padding: 44px var(--home-space-4) 32px"));
        assertTrue(cssRule(mobile, ".home-hero h1")
                .contains("font-size: clamp(3rem, 15vw, 4.5rem)"));
    }

    @Test
    void homepageSearchInputMeetsControlAndPlaceholderContrast() throws IOException {
        String css = resource("static/css/home-brik.css");
        String input = cssRule(css, ".home-composer-controls input");
        String hover = cssRule(css, ".home-composer-controls input:hover");
        String placeholder = cssRule(css, ".home-composer-controls input::placeholder");
        String inputBackground = resolvedCssColor(css, cssProperty(input, "background"));
        String surroundingSurface = resolvedCssColor(css, "var(--home-color-surface)");
        String normalBorder = resolvedCssColor(css, cssBorderColor(input));
        String hoverBorder = resolvedCssColor(css, cssBorderColor(hover));
        String placeholderColor = resolvedCssColor(css, cssProperty(placeholder, "color"));

        assertContrastAtLeast(normalBorder, inputBackground, 3.0, "normal input border on input background");
        assertContrastAtLeast(normalBorder, surroundingSurface, 3.0, "normal input border on surrounding surface");
        assertContrastAtLeast(hoverBorder, inputBackground, 3.0, "hover input border on input background");
        assertContrastAtLeast(hoverBorder, surroundingSurface, 3.0, "hover input border on surrounding surface");
        assertContrastAtLeast(placeholderColor, inputBackground, 4.5, "input placeholder text");
        assertEquals("1", cssProperty(placeholder, "opacity"));
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

    private static String cssBorderColor(String rule) {
        String borderColor = cssProperty(rule, "border-color");
        return borderColor.isEmpty() ? cssProperty(rule, "border") : borderColor;
    }

    private static String cssProperty(String rule, String property) {
        Matcher declaration = Pattern.compile(
                "(?:^|[;{])\\s*" + Pattern.quote(property) + "\\s*:\\s*([^;}]*)",
                Pattern.MULTILINE
        ).matcher(rule);
        return declaration.find() ? declaration.group(1).strip() : "";
    }

    private static String resolvedCssColor(String css, String declaration) {
        Matcher variable = Pattern.compile("var\\((--[a-z0-9-]+)\\)", Pattern.CASE_INSENSITIVE)
                .matcher(declaration);
        if (variable.find()) {
            String value = cssProperty(cssRule(css, ":root"), variable.group(1));
            return resolvedCssColor(css, value);
        }

        Matcher hex = Pattern.compile("#[0-9a-f]{6}", Pattern.CASE_INSENSITIVE).matcher(declaration);
        if (!hex.find()) {
            throw new IllegalArgumentException("No supported CSS color in declaration: " + declaration);
        }
        return hex.group();
    }

    private static void assertContrastAtLeast(String foreground, String background, double minimum, String label) {
        double ratio = contrastRatio(foreground, background);
        assertTrue(ratio >= minimum,
                () -> label + " contrast was " + String.format("%.2f", ratio) + ":1; expected at least " + minimum + ":1");
    }

    private static double contrastRatio(String first, String second) {
        double lighter = Math.max(relativeLuminance(first), relativeLuminance(second));
        double darker = Math.min(relativeLuminance(first), relativeLuminance(second));
        return (lighter + 0.05) / (darker + 0.05);
    }

    private static double relativeLuminance(String hex) {
        int rgb = Integer.parseInt(hex.substring(1), 16);
        double red = linearChannel((rgb >> 16) & 0xff);
        double green = linearChannel((rgb >> 8) & 0xff);
        double blue = linearChannel(rgb & 0xff);
        return 0.2126 * red + 0.7152 * green + 0.0722 * blue;
    }

    private static double linearChannel(int channel) {
        double srgb = channel / 255.0;
        return srgb <= 0.04045 ? srgb / 12.92 : Math.pow((srgb + 0.055) / 1.055, 2.4);
    }

    private static String cssRule(String css, String selector) {
        int start = css.indexOf(selector + " {");
        if (start < 0) {
            return "";
        }

        int end = css.indexOf('}', start);
        return end < 0 ? css.substring(start) : css.substring(start, end + 1);
    }

    private static String cssMediaRule(String css, String query) {
        int start = css.indexOf(query + " {");
        if (start < 0) {
            return "";
        }

        int depth = 0;
        for (int i = start; i < css.length(); i++) {
            char current = css.charAt(i);
            if (current == '{') {
                depth++;
            } else if (current == '}' && --depth == 0) {
                return css.substring(start, i + 1);
            }
        }

        return css.substring(start);
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
