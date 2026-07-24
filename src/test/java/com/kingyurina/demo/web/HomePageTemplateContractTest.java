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
    void homepageDeclaresSemanticObservatorySurfaceAndResponsiveContracts() throws IOException {
        String template = resource("templates/index.html");
        String css = resource("static/css/home-observatory.css");

        assertTrue(template.contains("home-observatory.css"));
        assertTrue(template.contains("class=\"home-page home-observatory\""));
        assertTrue(template.contains("th:each=\"menu : ${mainMenus}\""));
        assertTrue(template.contains("id=\"main-content\""));
        assertTrue(template.contains("data-home-nav"));
        assertTrue(template.contains("data-home-story"));
        assertTrue(template.contains("data-home-scene"));
        assertTrue(template.contains("data-home-canvas"));
        assertTrue(template.contains("data-home-core-fallback"));
        assertTrue(template.contains("data-home-reveal"));
        assertEquals(3, occurrences(template, "data-home-chapter"));
        assertEquals(3, occurrences(template, "data-home-metric"));
        assertEquals(6, occurrences(template, "data-home-product-link"));
        assertEquals(1, occurrences(template, "<h1"));
        assertTrue(template.contains("Market intelligence, resolved."));
        assertTrue(template.contains("Observe the market. Resolve the signal. Act with context."));
        assertTrue(template.contains("Signal"));
        assertTrue(template.contains("79 <small>/ 100</small>"));
        assertTrue(template.contains("20D Alpha"));
        assertTrue(template.contains("+2.4%"));
        assertTrue(template.contains("Risk"));
        assertTrue(template.contains("Medium"));
        assertTrue(template.contains("Illustrative model view"));
        assertTrue(template.contains("href=\"/quant\""));
        assertTrue(template.contains("href=\"/stocks\""));
        assertTrue(template.contains("href=\"/stocks/heatmap?index=SP500\""));
        assertTrue(template.contains("href=\"/etfs\""));
        assertTrue(template.contains("href=\"/atelier\""));
        assertTrue(template.contains("href=\"/signals/backtest\""));
        assertTrue(template.contains("id=\"systems\""));
        assertFalse(template.contains("http://"));
        assertFalse(template.contains("https://"));

        assertTrue(css.contains("--home-bg-deep: #030405"));
        assertTrue(css.contains("--home-fg-primary: #f5f7fa"));
        assertTrue(css.contains("--home-signal: #6ea8ff"));
        assertTrue(css.contains("@media (max-width: 767px)"));
        assertTrue(css.contains("@media (min-width: 768px)"));
        assertTrue(css.contains("@media (min-width: 1024px)"));
        assertTrue(css.contains("@media (min-width: 1440px)"));
        assertTrue(css.contains("@media (prefers-reduced-motion: reduce)"));
        assertTrue(css.contains("min-height: 44px"));
        assertTrue(css.contains(":focus-visible"));
        assertTrue(css.contains("overflow-x: clip"));
    }

    @Test
    void homepageQuantCoreSceneUsesLocalPhysicalRenderingContracts() throws IOException {
        String scene = resource("static/js/home-observatory-scene.js");

        assertTrue(scene.contains("/js/vendor/three.module.js"));
        assertTrue(scene.contains("createQuantCoreScene"));
        assertTrue(scene.contains("MeshPhysicalMaterial"));
        assertTrue(scene.contains("InstancedMesh"));
        assertTrue(scene.contains("ACESFilmicToneMapping"));
        assertTrue(scene.contains("setPixelRatio(profile.pixelRatio)"));
        assertTrue(scene.contains("dispose()"));
        assertFalse(scene.contains("fetch("));
        assertFalse(scene.contains("http://"));
        assertFalse(scene.contains("https://"));
    }

    @Test
    void homepageCoordinatesProgressiveEnhancementAndRetiresBrikRuntime() throws IOException {
        String template = resource("templates/index.html");
        String entry = resource("static/js/home-observatory.js");

        assertTrue(template.contains("/vendor/lenis/lenis.css"));
        assertTrue(template.contains("/vendor/lenis/lenis.min.js"));
        assertTrue(template.contains("/vendor/motion/motion.js"));
        assertTrue(template.contains("/js/home-observatory.js"));
        assertTrue(entry.contains("./home-observatory-scroll.js"));
        assertTrue(entry.contains("./home-observatory-motion.js"));
        assertTrue(entry.contains("./home-observatory-scene.js"));
        assertTrue(entry.contains("requestAnimationFrame"));
        assertTrue(entry.contains("IntersectionObserver"));
        assertTrue(entry.contains("visibilitychange"));
        assertTrue(entry.contains("prefers-reduced-motion: reduce"));
        assertFalse(entry.contains("setInterval"));
        assertFalse(entry.contains("fetch("));
        assertFalse(template.contains("home-brik"));
        assertFalse(new ClassPathResource("static/css/home-brik.css").exists());
        assertFalse(new ClassPathResource("static/js/home-brik.js").exists());
        assertFalse(new ClassPathResource("static/js/home-brik-motion.js").exists());
    }

    @Test
    void homepageKeepsLenisFromCapturingHorizontalNavigation() throws IOException {
        String template = resource("templates/index.html");

        assertTrue(template.contains(
                "<nav aria-label=\"Primary navigation\" data-lenis-prevent-horizontal>"));
    }

    @Test
    void homepageSkipAndFooterLinksHaveFortyFourPixelClickTargets() throws IOException {
        String css = resource("static/css/home-observatory.css").replaceAll("\\s+", " ");

        assertTrue(css.contains(
                ".home-skip, .home-footer a { display: inline-flex; align-items: center; }"));
        assertTrue(css.contains(
                ".home-skip, .home-brand, .home-primary-action, .home-nav nav a, "
                        + ".home-story-link, .home-product-list > a, .home-footer a "
                        + "{ min-height: 44px; }"));
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
