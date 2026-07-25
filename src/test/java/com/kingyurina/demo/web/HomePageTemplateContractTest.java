package com.kingyurina.demo.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ClassPathResource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HomePageTemplateContractTest {

    @LocalServerPort
    private int port;

    @Test
    void homepageAssetsArePublicWhileUnrelatedPathsRemainProtected() throws Exception {
        assertEquals(200, get("/vendor/lenis/lenis.css").statusCode());
        assertEquals(200, get("/vendor/lenis/lenis.min.js").statusCode());
        assertEquals(200, get("/vendor/motion/motion.js").statusCode());
        assertEquals(200, get("/fonts/instrument-sans-latin-wght-normal.woff2").statusCode());
        assertEquals(200, get("/fonts/ibm-plex-mono-latin-400-normal.woff2").statusCode());
        assertEquals(200, get("/fonts/ibm-plex-mono-latin-500-normal.woff2").statusCode());
        assertEquals(403, get("/admin").statusCode());
    }

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
    void homepageDeclaresSemanticQuantPlanetSurfaceAndResponsiveContracts() throws IOException {
        String template = resource("templates/index.html");
        String css = resource("static/css/home-quant-planet.css");

        assertTrue(template.contains("home-quant-planet.css"));
        assertTrue(template.contains("/js/home-quant-planet.js"));
        assertTrue(template.contains("class=\"home-page home-quant-planet\""));
        assertTrue(template.contains("th:each=\"menu : ${mainMenus}\""));
        assertTrue(template.contains("id=\"main-content\""));
        assertTrue(template.contains("data-home-nav"));
        assertTrue(template.contains("data-home-story"));
        assertTrue(template.contains("data-home-scene"));
        assertTrue(template.contains("data-home-canvas"));
        assertTrue(template.contains("data-planet-fallback"));
        assertTrue(template.contains("aria-hidden=\"true\""));
        assertTrue(template.contains("data-home-reveal"));
        assertEquals(3, occurrences(template, "data-home-chapter"));
        assertEquals(3, occurrences(template, "data-home-metric"));
        assertEquals(6, occurrences(template, "data-home-product-link"));
        assertEquals(1, occurrences(template, "<h1"));
        assertTrue(template.contains("Market intelligence,<br>in motion."));
        assertTrue(template.contains("Observe the market. Resolve the signal. Act with context."));
        assertTrue(template.contains("<dt>Signal</dt><dd>79 <small>/ 100</small></dd>"));
        assertTrue(template.contains("<dt>20D Alpha</dt><dd>+2.4%</dd>"));
        assertTrue(template.contains("<dt>Risk</dt><dd>Medium</dd>"));
        assertTrue(template.contains("Illustrative model view"));
        assertTrue(template.contains("Arrive at a decision."));
        assertTrue(template.contains("href=\"/quant\""));
        assertTrue(template.contains("href=\"/stocks\""));
        assertTrue(template.contains("href=\"/stocks/heatmap?index=SP500\""));
        assertTrue(template.contains("href=\"/etfs\""));
        assertTrue(template.contains("href=\"/atelier\""));
        assertTrue(template.contains("href=\"/signals/backtest\""));
        assertTrue(template.contains("id=\"systems\""));
        assertFalse(template.contains("home-observatory"));
        assertFalse(template.contains("http://"));
        assertFalse(template.contains("https://"));

        assertTrue(css.contains("--planet-sky: #9ed9d5"));
        assertTrue(css.contains("--planet-haze: #d9efe8"));
        assertTrue(css.contains("--planet-ground: #b8c989"));
        assertTrue(css.contains("--planet-warm: #f1d7a4"));
        assertTrue(css.contains("--planet-coral: #e8755d"));
        assertTrue(css.contains("--planet-blue: #2357a5"));
        assertTrue(css.contains("--planet-ink: #173149"));
        assertTrue(css.contains("--planet-paper: #fff9ea"));
        assertTrue(css.contains("--planet-positive: #2f8c73"));
        assertTrue(css.contains("--planet-risk: #c85a5a"));
        assertTrue(css.contains("--layer-world: 0"));
        assertTrue(css.contains("--layer-content: 10"));
        assertTrue(css.contains("--layer-nav: 30"));
        assertTrue(css.contains("--layer-skip: 50"));
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
    void homepageCoordinatesProgressiveEnhancementAndRetiresObservatoryRuntime()
            throws IOException {
        String template = resource("templates/index.html");
        String entry = resource("static/js/home-quant-planet.js");

        assertTrue(template.contains("/js/home-quant-planet.js"));
        assertTrue(entry.contains("./home-quant-planet-scroll.js"));
        assertTrue(entry.contains("./home-quant-planet-motion.js"));
        assertTrue(entry.contains("./home-quant-planet-scene.js"));
        assertTrue(entry.contains("createPageLifecycle"));
        assertTrue(entry.contains("IntersectionObserver"));
        assertTrue(entry.contains("visibilitychange"));
        assertTrue(entry.contains("pageshow"));
        assertTrue(entry.contains("prefers-reduced-motion: reduce"));
        assertTrue(entry.contains("updateStoryProgress();\n    scene?.resize();\n    lifecycle.start();"));
        assertFalse(entry.contains("setInterval"));
        assertFalse(entry.contains("fetch("));
        assertFalse(new ClassPathResource("static/css/home-observatory.css").exists());
        assertFalse(new ClassPathResource("static/js/home-observatory.js").exists());
        assertFalse(new ClassPathResource("static/js/home-observatory-motion.js").exists());
        assertFalse(new ClassPathResource("static/js/home-observatory-scroll.js").exists());
        assertFalse(new ClassPathResource("static/js/home-observatory-scene.js").exists());
    }

    @Test
    void homepageBuildsAnOriginalLocalQuantPlanetWorld() throws IOException {
        String world = resource("static/js/home-quant-planet-world.js");
        String scene = resource("static/js/home-quant-planet-scene.js");

        assertTrue(world.contains("createQuantPlanetWorld"));
        assertTrue(world.contains("ObserveStation"));
        assertTrue(world.contains("ResolveWorks"));
        assertTrue(world.contains("DecisionGate"));
        assertTrue(world.contains("QuantCourier"));
        assertTrue(world.contains("MeshToonMaterial"));
        assertTrue(world.contains("CatmullRomCurve3"));
        assertTrue(world.contains("InstancedMesh"));
        assertTrue(world.contains("BackSide"));
        assertTrue(scene.contains("/js/vendor/three.module.js"));
        assertTrue(scene.contains("createQuantPlanetScene"));
        assertTrue(scene.contains("setPixelRatio(profile.pixelRatio)"));
        assertTrue(scene.contains("getDiagnostics"));
        assertTrue(scene.contains("dispose()"));
        assertFalse(world.contains("fetch("));
        assertFalse(scene.contains("fetch("));
        assertFalse(world.contains("http://"));
        assertFalse(scene.contains("https://"));
    }

    @Test
    void homepageCoordinatesProgressiveEnhancementAndRetiresBrikRuntime() throws IOException {
        String template = resource("templates/index.html");

        assertTrue(template.contains("/vendor/lenis/lenis.css"));
        assertTrue(template.contains("/vendor/lenis/lenis.min.js"));
        assertTrue(template.contains("/vendor/motion/motion.js"));
        assertTrue(template.contains("/js/home-quant-planet.js"));
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
        String css = resource("static/css/home-quant-planet.css").replaceAll("\\s+", " ");

        assertTrue(css.contains(
                ".home-skip, .home-footer a { display: inline-flex; align-items: center; }"));
        assertTrue(css.contains(
                ".home-skip, .home-brand, .home-nav > .planet-primary-action, "
                        + ".home-nav nav a, .planet-primary-action, .planet-story-link, "
                        + ".home-product-list > a, .home-footer a "
                        + "{ min-height: 44px; }"));
    }

    @Test
    void homepageKeepsTheMobilePlanetBelowTheHeroActionsAndMetricsBelowTheWorld()
            throws IOException {
        String css = resource("static/css/home-quant-planet.css").replaceAll("\\s+", " ");

        assertTrue(css.contains(
                "@media (max-width: 767px) {"));
        assertTrue(css.contains(
                ".planet-chapter--observe { top: 9rem; }"));
        assertTrue(css.contains(
                ".planet-fallback { top: 55svh; right: auto; left: 50%; "
                        + "width: min(82vw, 19rem); transform: translateX(-50%); }"));
        assertTrue(css.contains(
                ".planet-metrics { grid-template-columns: 1fr; }"));
    }

    @Test
    void homepageKeepsTabletNavigationProductsAndPlanetWithinTheViewport() throws IOException {
        String css = resource("static/css/home-quant-planet.css").replaceAll("\\s+", " ");

        assertTrue(css.contains(
                "justify-content: flex-start; overflow-x: auto; scrollbar-width: none;"));
        assertTrue(css.contains(
                ".home-nav nav::-webkit-scrollbar { display: none; }"));
        assertTrue(css.contains(
                ".home-product-list { min-width: 0;"));
        assertTrue(css.contains(
                "grid-template-columns: "
                        + "3rem minmax(0, .8fr) minmax(0, 1fr) auto; gap: 1rem;"));
        assertTrue(css.contains(
                "@media (min-width: 1024px) {"));
        assertTrue(css.contains(
                ".home-products { grid-template-columns: "
                        + "minmax(13rem, .65fr) minmax(0, 1.35fr); }"));
        assertTrue(css.contains(
                "@media (min-width: 1440px) {"));
    }

    @Test
    void homepageRendersTheStaticPlanetRouteStationAndCourierUntilWebglIsReady()
            throws IOException {
        String css = resource("static/css/home-quant-planet.css").replaceAll("\\s+", " ");

        assertTrue(css.contains(
                ".planet-fallback { position: absolute;"));
        assertTrue(css.contains(".planet-fallback__world {"));
        assertTrue(css.contains(".planet-fallback__route {"));
        assertTrue(css.contains(".planet-fallback__station {"));
        assertTrue(css.contains(".planet-fallback__courier {"));
        assertTrue(css.contains(
                "pointer-events: none;"));
        assertTrue(css.contains(
                ".planet-webgl-ready .planet-fallback { opacity: 0; }"));
        assertTrue(css.contains(
                ".planet-webgl-failed .planet-fallback { opacity: 1; }"));
    }

    @Test
    void homepageUsesRestrainedChapterIndicesAndNavigationOpacity() throws IOException {
        String template = resource("templates/index.html");
        String css = resource("static/css/home-quant-planet.css").replaceAll("\\s+", " ");

        assertTrue(template.contains(
                "<p class=\"planet-chapter__index\">01 / OBSERVE</p>"));
        assertTrue(template.contains(
                "<p class=\"planet-chapter__index\">02 / RESOLVE</p>"));
        assertTrue(template.contains(
                "<p class=\"planet-chapter__index\">03 / ACT</p>"));
        assertEquals(3, occurrences(template, "class=\"planet-chapter__index\""));
        assertTrue(css.contains(
                "[data-planet-chapter=\"observe\"] .home-nav nav { opacity: .78; }"));
        assertTrue(css.contains(
                "[data-planet-chapter=\"resolve\"] .home-nav nav { opacity: .68; }"));
        assertTrue(css.contains(
                "[data-planet-chapter=\"act\"] .home-nav nav { opacity: .82; }"));
        assertTrue(css.contains(
                ".home-nav nav:hover, .home-nav nav:focus-within { opacity: 1; }"));
    }

    @Test
    void homepageLimitsHeroSubtitleMeasureBeforeThePlanet() throws IOException {
        String css = resource("static/css/home-quant-planet.css").replaceAll("\\s+", " ");

        assertTrue(css.contains(
                ".planet-chapter > p:not(.planet-eyebrow):not(.planet-chapter__index) "
                        + "{ max-width: 28ch;"));
    }

    @Test
    void homepageOnlyHidesRevealContentAfterMotionStartsAndRetainsShortLandscapeCta()
            throws IOException {
        String css = resource("static/css/home-quant-planet.css").replaceAll("\\s+", " ");

        assertTrue(css.contains(
                ".planet-motion-enabled [data-home-reveal] { opacity: 0;"));
        assertTrue(css.contains(
                "@media (max-height: 540px) and (orientation: landscape) {"));
        assertTrue(css.contains(
                ".planet-chapter--observe .planet-primary-action { display: inline-flex; }"));
        assertTrue(css.contains(
                "@media (prefers-reduced-motion: reduce)"));
        assertTrue(css.contains(
                ".planet-motion-enabled [data-home-reveal] { opacity: 1; transform: none; }"));
    }

    @Test
    void homepageKeepsMobileNavigationBrandAndProductDescriptionsVisible()
            throws IOException {
        String css = resource("static/css/home-quant-planet.css").replaceAll("\\s+", " ");

        assertTrue(css.contains(
                ".home-nav nav { grid-column: 1 / -1; display: flex;"));
        assertTrue(css.contains(
                "overflow-x: auto; scrollbar-width: none;"));
        assertTrue(css.contains(
                ".home-product-list p { display: block;"));
        assertFalse(css.contains(
                ".home-brand span { display: none; }"));
        assertFalse(css.contains(
                ".home-product-list p { display: none;"));
    }

    @Test
    void homepageUsesHighContrastFooterFocus() throws IOException {
        String css = resource("static/css/home-quant-planet.css").replaceAll("\\s+", " ");

        assertTrue(css.contains(
                ".home-footer :focus-visible { outline-color: var(--planet-paper); }"));
    }

    @Test
    void homepageUsesHighContrastEyebrowText() throws IOException {
        String css = resource("static/css/home-quant-planet.css").replaceAll("\\s+", " ");

        assertTrue(css.contains(
                ".planet-eyebrow { color: var(--planet-ink); }"));
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

    private HttpResponse<Void> get(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + path))
                .GET()
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.discarding());
    }
}
