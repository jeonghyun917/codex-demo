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
        assertEquals(200, get("/vendor/motion/motion.js").statusCode());
        assertEquals(200, get("/fonts/instrument-sans-latin-wght-normal.woff2").statusCode());
        assertEquals(200, get("/fonts/ibm-plex-mono-latin-400-normal.woff2").statusCode());
        assertEquals(200, get("/fonts/ibm-plex-mono-latin-500-normal.woff2").statusCode());
        assertEquals(403, get("/admin").statusCode());
    }

    @Test
    void homepageRuntimeDependenciesAndFontsArePinnedLocally() throws IOException {
        assertTrue(new ClassPathResource("static/vendor/motion/motion.js").exists());
        assertTrue(new ClassPathResource("static/vendor/motion/LICENSE.md").exists());
        assertTrue(new ClassPathResource("static/js/vendor/three.module.js").exists());
        assertTrue(new ClassPathResource("static/js/vendor/three.LICENSE").exists());
        assertTrue(new ClassPathResource("static/fonts/instrument-sans-latin-wght-normal.woff2").exists());
        assertTrue(new ClassPathResource("static/fonts/ibm-plex-mono-latin-400-normal.woff2").exists());
        assertTrue(new ClassPathResource("static/fonts/ibm-plex-mono-latin-500-normal.woff2").exists());
        assertTrue(new ClassPathResource("static/fonts/Instrument-Sans-LICENSE").exists());
        assertTrue(new ClassPathResource("static/fonts/IBM-Plex-Mono-LICENSE").exists());

        assertTrue(resource("static/vendor/motion/LICENSE.md").contains("MIT License"));
        assertTrue(resource("static/js/vendor/three.module.js").contains("const REVISION = '165'"));
    }

    @Test
    void homepageIsOneFullscreenProductOrbit() throws IOException {
        String template = resource("templates/index.html");
        String css = resource("static/css/home-quant-planet.css")
                .replaceAll("\\s+", " ");

        assertTrue(template.contains(
                "class=\"home-page home-quant-planet home-quant-fullscreen\""));
        assertTrue(template.contains("data-home-experience"));
        assertTrue(template.contains("data-home-scene"));
        assertTrue(template.contains("data-home-canvas"));
        assertTrue(template.contains("data-planet-fallback"));
        assertTrue(template.contains("data-intro-loader"));
        assertTrue(template.contains("data-intro-mark"));
        assertTrue(template.contains("data-product-orbit"));
        assertTrue(template.contains("data-home-semantic-summary"));
        assertTrue(template.contains("data-interaction-hint"));
        assertEquals(6, occurrences(template, "data-product-orbit-link"));
        assertEquals(1, occurrences(template, "<h1"));
        assertTrue(template.contains("Market intelligence, in motion."));
        assertTrue(template.contains(
                "Observe the market. Resolve the signal. Act with context."));
        assertTrue(template.contains(
                "<dt>Signal</dt><dd>79 / 100</dd>"));
        assertTrue(template.contains(
                "<dt>20D Alpha</dt><dd>+2.4%</dd>"));
        assertTrue(template.contains(
                "<dt>Risk</dt><dd>Medium</dd>"));
        assertTrue(template.contains("Illustrative model view"));
        assertEquals(1, occurrences(template, ">Enter Quant</a>"));
        assertTrue(template.contains("href=\"/quant\">QUANT</a>"));
        assertTrue(template.contains("href=\"/stocks\">STOCKS</a>"));
        assertTrue(template.contains(
                "href=\"/stocks/heatmap?index=SP500\">HEATMAP</a>"));
        assertTrue(template.contains("href=\"/etfs\">ETF</a>"));
        assertTrue(template.contains("href=\"/atelier\">ATELIER</a>"));
        assertTrue(template.contains(
                "href=\"/signals/backtest\">SIGNAL</a>"));
        assertFalse(template.contains("/vendor/lenis/"));
        assertFalse(template.contains("data-home-chapter"));
        assertFalse(template.contains("home-product-list"));
        assertFalse(template.contains("home-footer"));

        assertTrue(css.contains("height: 100dvh"));
        assertTrue(css.contains("overflow: hidden"));
        assertTrue(css.contains("--planet-size: min(82vh, 72vw)"));
        assertTrue(css.contains("--planet-size: min(92vw, 68vh)"));
        assertTrue(css.contains(".product-orbit__link"));
        assertTrue(css.contains("min-height: 44px"));
        assertTrue(css.contains(":focus-visible"));
        assertTrue(css.contains("@media (prefers-reduced-motion: reduce)"));
    }

    @Test
    void homepageFallbackNavigationOwnsAnUnobstructedTopBand() throws IOException {
        String template = resource("templates/index.html")
                .replaceAll("\\s+", " ");
        String css = resource("static/css/home-quant-planet.css")
                .replaceAll("\\s+", " ");

        assertTrue(template.contains(
                ".product-orbit { position: absolute !important; "
                        + "z-index: calc(var(--layer-content) + 1) !important; "
                        + "inset: 5rem 0 auto !important; display: grid !important; "
                        + "grid-template-columns: repeat(3, minmax(5rem, 1fr)); "
                        + "align-content: start;"));
        assertTrue(template.contains(
                ".product-orbit__link { position: relative !important; "
                        + "inset: auto !important; transform: none !important; "
                        + "opacity: 1 !important; }"));

        assertTrue(css.contains(
                "html, body { width: 100%; height: 100%; height: 100dvh; "
                        + "margin: 0; overflow: hidden; overscroll-behavior: none; }"));
        assertTrue(css.contains(
                ".planet-experience { --planet-size: min(82vh, 72vw); "
                        + "position: fixed; inset: 0; min-height: 100dvh; "
                        + "overflow: hidden; isolation: isolate;"));
        assertTrue(css.contains(
                "@media (prefers-reduced-motion: reduce)"));
        assertTrue(css.contains(
                ".product-orbit { position: absolute; "
                        + "z-index: calc(var(--layer-content) + 1); "
                        + "inset: 5rem 0 auto; display: grid; "
                        + "grid-template-columns: repeat(3, minmax(5rem, 7rem)); "
                        + "align-content: start;"));
        assertTrue(css.contains(
                ".product-orbit__link { position: relative; inset: auto; "
                        + "transform: none; opacity: 1; z-index: auto;"));
    }

    @Test
    void homepageCoordinatesProgressiveEnhancementAndRetiresObservatoryRuntime()
            throws IOException {
        String template = resource("templates/index.html");
        String entry = resource("static/js/home-quant-planet.js");

        assertTrue(template.contains("/js/home-quant-planet.js"));
        assertTrue(entry.contains("./home-quant-planet-motion.js"));
        assertTrue(entry.contains("./home-quant-planet-scene.js"));
        assertTrue(entry.contains("createPageLifecycle"));
        assertTrue(entry.contains("visibilitychange"));
        assertTrue(entry.contains("pageshow"));
        assertTrue(entry.contains("prefers-reduced-motion: reduce"));
        assertTrue(entry.contains("\"[::1]\""));
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
        assertTrue(scene.contains("let renderer = null"));
        assertTrue(scene.contains("let world = null"));
        assertTrue(scene.contains("world?.dispose();"));
        assertTrue(scene.contains("renderer?.dispose();"));
        assertTrue(scene.contains("throw error"));
        assertTrue(scene.contains("disposeInitializationResources"));
        assertTrue(scene.contains("setDragAngle(value)"));
        assertTrue(scene.contains("createOrbitPose"));
        assertTrue(scene.contains("createCourierLoopPose"));
        assertFalse(scene.contains("setProgress(value)"));
        assertFalse(scene.contains("createPlanetPose"));
        assertTrue(world.contains("disposeTrackedResources"));
        assertFalse(world.contains("fetch("));
        assertFalse(scene.contains("fetch("));
        assertFalse(world.contains("http://"));
        assertFalse(scene.contains("https://"));
    }

    @Test
    void homepageCoordinatesProgressiveEnhancementAndRetiresBrikRuntime() throws IOException {
        String template = resource("templates/index.html");

        assertTrue(template.contains("/vendor/motion/motion.js"));
        assertTrue(template.contains("/js/home-quant-planet.js"));
        assertFalse(template.contains("home-brik"));
        assertFalse(new ClassPathResource("static/css/home-brik.css").exists());
        assertFalse(new ClassPathResource("static/js/home-brik.js").exists());
        assertFalse(new ClassPathResource("static/js/home-brik-motion.js").exists());
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
