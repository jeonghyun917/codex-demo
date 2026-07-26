package com.kingyurina.demo.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ClassPathResource;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

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
        assertEquals(6, occurrences(template, "data-product-title-link"));
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
        assertEquals(1, occurrences(template, "data-enter-quant"));
        assertFalse(template.contains("/vendor/lenis/"));
        assertFalse(template.contains("data-home-chapter"));
        assertFalse(template.contains("home-product-list"));
        assertFalse(template.contains("home-footer"));

        assertTrue(css.contains("height: 100dvh"));
        assertTrue(css.contains("overflow: hidden"));
        assertTrue(css.contains("--planet-diameter: min(82vh, 72vw)"));
        assertTrue(css.contains("--planet-diameter: min(92vw, 68vh)"));
        assertTrue(css.contains(".product-title"));
        assertTrue(css.contains("min-height: 44px"));
        assertTrue(css.contains(":focus-visible"));
        assertTrue(css.contains("@media (prefers-reduced-motion: reduce)"));
    }

    @Test
    void homepageUsesDominantReferenceProductTitles() throws Exception {
        HttpResponse<String> response = getBody("/");
        assertEquals(200, response.statusCode());
        Document homepage = parseRenderedHtml(response.body());
        Element root = homepage.getDocumentElement();

        List<Element> titles = elementsWithAttribute(
                root, "a", "data-product-title-link");
        assertEquals(6, titles.size());
        List<String> expectedRoutes = List.of(
                "/quant",
                "/stocks",
                "/stocks/heatmap?index=SP500",
                "/etfs",
                "/atelier",
                "/signals/backtest");
        List<List<String>> expectedLines = List.of(
                List.of("QUANT", "INTELLIGENCE"),
                List.of("STOCK", "SIGNALS"),
                List.of("MARKET", "HEATMAP"),
                List.of("ETF", "RADAR"),
                List.of("STRATEGY", "ATELIER"),
                List.of("SIGNAL", "LAB"));
        List<String> expectedSupporting = List.of(
                "Observe 쨌 Resolve 쨌 Act",
                "Market field",
                "S&P 500 field",
                "Allocation field",
                "Research field",
                "Backtest field");

        for (int index = 0; index < titles.size(); index++) {
            Element title = titles.get(index);
            assertEquals(expectedRoutes.get(index), title.getAttribute("href"));
            List<Element> lines = elementsWithAttribute(
                    title, "span", "data-title-line");
            assertEquals(2, lines.size());
            assertEquals(expectedLines.get(index), lines.stream()
                    .map(line -> line.getTextContent().strip())
                    .toList());
            for (Element line : lines) {
                assertEquals(
                        line.getTextContent().strip(),
                        line.getAttribute("data-extrude"));
            }
            List<Element> supporting = elementsWithAttribute(
                    title, "span", "data-title-supporting");
            assertEquals(1, supporting.size());
            assertEquals(
                    expectedSupporting.get(index),
                    supporting.getFirst().getTextContent().strip());
        }

        assertEquals(0, elementsWithClass(root, "product-orbit__link").size());
        List<Element> enterQuant = elementsWithAttribute(
                root, "a", "data-enter-quant");
        assertEquals(1, enterQuant.size());
        assertEquals("ENTER QUANT", enterQuant.getFirst().getTextContent().strip());
        assertEquals("/quant", enterQuant.getFirst().getAttribute("href"));
        assertEquals("main", enterQuant.getFirst().getParentNode().getNodeName());

        Element header = homepage.getElementsByTagName("header")
                .item(0) instanceof Element element ? element : null;
        assertTrue(header != null);
        assertEquals(
                0,
                elementsWithAttribute(header, "a", "data-enter-quant").size());
        Element semanticSummary = elementsWithAttribute(
                root, "*", "data-home-semantic-summary").getFirst();
        assertEquals(
                0,
                elementsWithAttribute(
                        semanticSummary, "a", "data-product-title-link").size());

        String css = resource("static/css/home-quant-planet.css");
        String titleRule = cssRule(css, ".product-title");
        String titleLineRule = cssRule(
                css, ".product-title [data-title-line]");
        String activeTitleRule = cssRule(
                css, ".product-title[data-product-position=\"active\"]");
        assertEquals(
                "clamp(2.7rem, 7.2vw, 7.6rem)",
                cssDeclaration(titleLineRule, "font-size"));
        assertEquals(
                "var(--planet-paper)",
                cssDeclaration(titleLineRule, "color"));
        assertTrue(cssDeclaration(titleLineRule, "-webkit-text-stroke")
                .contains("var(--planet-ink)"));
        assertTrue(occurrences(
                cssDeclaration(titleLineRule, "text-shadow"),
                "var(--planet-ink)") >= 3);
        assertEquals("1", cssDeclaration(activeTitleRule, "opacity"));
        assertFalse(titleRule.contains("border-radius"));
        assertFalse(css.contains(".product-orbit__link"));
        assertFalse(css.contains(".planet-webgl-ready .product-title"));

        String actionRule = cssRule(css, ".planet-primary-action");
        assertEquals("absolute", cssDeclaration(actionRule, "position"));
        assertEquals("50%", cssDeclaration(actionRule, "left"));
        assertTrue(cssDeclaration(actionRule, "top").startsWith("calc("));
        assertEquals("44px", cssDeclaration(actionRule, "min-height"));
        assertEquals(
                "var(--planet-action)",
                cssDeclaration(actionRule, "background"));
        assertTrue(cssDeclaration(actionRule, "clip-path").startsWith("polygon("));
        assertTrue(cssDeclaration(actionRule, "transform")
                .contains("translateX(-50%)"));
        assertTrue(cssDeclaration(actionRule, "transform")
                .contains("perspective("));
    }

    @Test
    void homepageFocusKeepsOneForegroundProductTitle() throws Exception {
        Document homepage = parseRenderedHtml(getBody("/").body());
        Element root = homepage.getDocumentElement();
        List<Element> titles = elementsWithAttribute(
                root, "a", "data-product-title-link");

        assertEquals(
                List.of(
                        "active",
                        "next",
                        "hidden",
                        "hidden",
                        "hidden",
                        "previous"),
                titles.stream()
                        .map(title -> title.getAttribute(
                                "data-product-position"))
                        .toList());
        assertEquals(1, titles.stream()
                .filter(title -> "active".equals(title.getAttribute(
                        "data-product-position")))
                .count());
        assertEquals(1, titles.stream()
                .filter(title -> "previous".equals(title.getAttribute(
                        "data-product-position")))
                .count());
        assertEquals(1, titles.stream()
                .filter(title -> "next".equals(title.getAttribute(
                        "data-product-position")))
                .count());

        Element header = (Element) homepage.getElementsByTagName("header").item(0);
        List<Element> headerLinks = elementsWithAttribute(header, "a", "href");
        assertEquals(1, headerLinks.size());
        assertTrue(elementsWithClass(header, "home-brand")
                .contains(headerLinks.getFirst()));
        assertFalse("/quant".equals(headerLinks.getFirst().getAttribute("href")));
        assertEquals(0, elementsWithClass(
                header, "planet-primary-action").size());

        String css = resource("static/css/home-quant-planet.css");
        String activeDemotion = cssRule(
                css,
                ".product-orbit:focus-within "
                        + ".product-title[data-product-position=\"active\"]"
                        + ":not(:focus)");
        assertEquals("0", cssDeclaration(activeDemotion, "opacity"));
        assertTrue(cssDeclaration(activeDemotion, "transform")
                .contains("scale("));

        String focusedTitle = cssRule(css, ".product-title:focus");
        assertEquals("1", cssDeclaration(focusedTitle, "opacity"));
        assertEquals("50%", cssDeclaration(focusedTitle, "left"));

        String mobileCss = cssAtRuleBlock(
                css, "@media (max-width: 767px)");
        int mobileFocusIndex = mobileCss.indexOf(".product-title:focus");
        assertTrue(mobileFocusIndex
                > mobileCss.indexOf(
                        ".product-title[data-product-position=\"previous\"]"));
        assertTrue(mobileFocusIndex
                > mobileCss.indexOf(
                        ".product-title[data-product-position=\"next\"]"));
        String mobileFocusedTitle = cssRule(
                mobileCss, ".product-title:focus");
        assertEquals("50%", cssDeclaration(mobileFocusedTitle, "left"));
        assertEquals(
                "var(--planet-center-y)",
                cssDeclaration(mobileFocusedTitle, "top"));
        assertEquals("1", cssDeclaration(mobileFocusedTitle, "opacity"));
        assertTrue(cssDeclaration(mobileFocusedTitle, "transform")
                .contains("scale(1)"));
    }

    @Test
    void homepageTitleEnvelopeStaysWithinPlanetScale() throws Exception {
        Document homepage = parseRenderedHtml(getBody("/").body());
        Element root = homepage.getDocumentElement();
        Element semanticSummary = elementsWithAttribute(
                root, "*", "data-home-semantic-summary").getFirst();
        NodeList pageHeadings = root.getElementsByTagName("h1");
        assertEquals(1, pageHeadings.getLength());
        assertTrue(semanticSummary.isSameNode(
                pageHeadings.item(0).getParentNode()));
        assertEquals(3, semanticSummary.getElementsByTagName("dt").getLength());
        assertEquals(3, semanticSummary.getElementsByTagName("dd").getLength());
        assertEquals(
                root.getElementsByTagName("dt").getLength(),
                semanticSummary.getElementsByTagName("dt").getLength());
        assertEquals(
                root.getElementsByTagName("dd").getLength(),
                semanticSummary.getElementsByTagName("dd").getLength());

        String css = resource("static/css/home-quant-planet.css");
        String desktopExperience = cssRule(css, ".planet-experience");
        assertMinLengthRatioBetween(
                cssDeclaration(
                        desktopExperience, "--planet-title-envelope"),
                cssDeclaration(desktopExperience, "--planet-diameter"),
                .45,
                .62);
        String sceneRule = cssRule(css, ".planet-scene");
        assertEquals(
                "var(--planet-diameter)",
                cssDeclaration(sceneRule, "width"));
        assertEquals(
                "var(--planet-diameter)",
                cssDeclaration(sceneRule, "height"));
        assertEquals(
                "var(--planet-title-envelope)",
                cssDeclaration(cssRule(css, ".product-title"), "width"));

        String mobileCss = cssAtRuleBlock(
                css, "@media (max-width: 767px)");
        String mobileExperience = cssRule(
                mobileCss, ".planet-experience");
        assertMinLengthRatioBetween(
                cssDeclaration(
                        mobileExperience, "--planet-title-envelope"),
                cssDeclaration(mobileExperience, "--planet-diameter"),
                .45,
                .62);
        String mobileTitle = cssRule(mobileCss, ".product-title");
        assertEquals(
                "var(--planet-title-envelope)",
                cssDeclaration(mobileTitle, "width"));
    }

    @Test
    void homepageActionClearsSharedPlanetBounds() throws IOException {
        String css = resource("static/css/home-quant-planet.css");
        String experienceRule = cssRule(css, ".planet-experience");
        String actionRule = cssRule(css, ".planet-primary-action");
        String clearance = "calc(var(--planet-center-y) "
                + "+ var(--planet-radius) + var(--planet-action-gap))";

        assertEquals(clearance, cssDeclaration(actionRule, "top"));
        List<String> actionPositionRules = cssRuleBodiesForExactSelector(
                css, ".planet-primary-action").stream()
                .filter(body -> hasCssDeclaration(body, "top"))
                .toList();
        assertTrue(actionPositionRules.size() >= 2);
        for (String positionRule : actionPositionRules) {
            assertEquals(clearance, cssDeclaration(positionRule, "top"));
        }
        assertEquals(
                "var(--planet-center-y)",
                cssDeclaration(cssRule(css, ".planet-scene"), "top"));
        assertTrue(Double.parseDouble(cssDeclaration(
                experienceRule, "--planet-center-y")
                .replace("%", "")) <= 44);
        assertTrue(cssDeclaration(
                experienceRule, "--planet-radius").startsWith("min("));
        assertTrue(cssDeclaration(
                experienceRule, "--planet-action-gap").startsWith("clamp("));
        assertMinLengthRatioBetween(
                cssDeclaration(experienceRule, "--planet-radius"),
                cssDeclaration(experienceRule, "--planet-diameter"),
                .499,
                .501);
        assertPlanetBottomAtMost(experienceRule, 85);

        String mobileCss = cssAtRuleBlock(
                css, "@media (max-width: 767px)");
        String mobileExperience = cssRule(
                mobileCss, ".planet-experience");
        assertTrue(Double.parseDouble(cssDeclaration(
                mobileExperience, "--planet-center-y")
                .replace("%", "")) <= 44);
        assertFalse(cssDeclaration(
                mobileExperience, "--planet-diameter").equals(
                        cssDeclaration(
                                experienceRule, "--planet-diameter")));
        assertMinLengthRatioBetween(
                cssDeclaration(mobileExperience, "--planet-radius"),
                cssDeclaration(mobileExperience, "--planet-diameter"),
                .499,
                .501);
        assertPlanetBottomAtMost(mobileExperience, 85);
        assertEquals(
                clearance,
                cssDeclaration(
                        cssRule(mobileCss, ".planet-primary-action"), "top"));

        for (String body : cssRuleBodiesWithSelectorTokens(
                css, ".planet-webgl-ready", ".product-title")) {
            String normalized = body.replaceAll("\\s+", " ");
            assertFalse(normalized.matches(
                    "(?s).*\\bdisplay\\s*:\\s*none\\b.*"));
            assertFalse(normalized.matches(
                    "(?s).*\\bvisibility\\s*:\\s*hidden\\b.*"));
            assertFalse(normalized.matches(
                    "(?s).*\\bopacity\\s*:\\s*0(?:\\D|$).*"));
            assertFalse(normalized.matches(
                    "(?s).*\\b(?:clip|clip-path)\\s*:.*"));
            assertFalse(normalized.matches(
                    "(?s).*\\bleft\\s*:\\s*-.*"));
            assertFalse(normalized.matches(
                    "(?s).*\\b(?:width|height)\\s*:\\s*1px\\b.*"));
            assertFalse(normalized.matches(
                    "(?s).*\\btransform\\s*:.*scale\\(0\\).*"));
        }
    }

    @Test
    void homepageFallbackNavigationKeepsDepthTitlesKeyboardReachable()
            throws IOException {
        String template = resource("templates/index.html")
                .replaceAll("\\s+", " ");
        String css = resource("static/css/home-quant-planet.css");
        String normalizedCss = css.replaceAll("\\s+", " ");

        assertTrue(template.contains(
                ".planet-intro-loader { display: none !important; }"));
        assertFalse(template.contains(".product-orbit__link"));

        assertTrue(normalizedCss.contains(
                "html, body { width: 100%; height: 100%; height: 100dvh; "
                        + "margin: 0; overflow: hidden; overscroll-behavior: none; }"));
        String experienceRule = cssRule(css, ".planet-experience");
        assertEquals(
                "min(82vh, 72vw)",
                cssDeclaration(experienceRule, "--planet-diameter"));
        assertEquals(
                "42%",
                cssDeclaration(experienceRule, "--planet-center-y"));
        assertEquals("fixed", cssDeclaration(experienceRule, "position"));
        assertEquals("0", cssDeclaration(experienceRule, "inset"));
        assertEquals(
                "100dvh",
                cssDeclaration(experienceRule, "min-height"));
        assertTrue(normalizedCss.contains(
                "@media (prefers-reduced-motion: reduce)"));

        String previousRule = cssRule(
                css,
                ".product-title[data-product-position=\"previous\"]");
        String nextRule = cssRule(
                css,
                ".product-title[data-product-position=\"next\"]");
        assertEquals(
                "calc(50% - var(--planet-radius))",
                cssDeclaration(previousRule, "left"));
        assertEquals(
                "calc(50% + var(--planet-radius))",
                cssDeclaration(nextRule, "left"));
        assertTrue(Double.parseDouble(
                cssDeclaration(previousRule, "opacity")) > 0);
        assertTrue(Double.parseDouble(
                cssDeclaration(nextRule, "opacity")) > 0);

        String hiddenRule = cssRule(
                css,
                ".product-title[data-product-position=\"hidden\"]:not(:focus)");
        assertEquals("-100vw", cssDeclaration(hiddenRule, "left"));
        assertEquals("1px", cssDeclaration(hiddenRule, "width"));
        assertEquals("hidden", cssDeclaration(hiddenRule, "overflow"));
        assertFalse(hiddenRule.contains("display: none"));
        assertFalse(hiddenRule.contains("visibility: hidden"));

        String focusRule = cssRule(css, ".product-title:focus");
        assertEquals("50%", cssDeclaration(focusRule, "left"));
        assertEquals("1", cssDeclaration(focusRule, "opacity"));
    }

    @Test
    void homepageCoordinatesProgressiveEnhancementAndRetiresObservatoryRuntime()
            throws IOException {
        String template = resource("templates/index.html");
        String entry = resource("static/js/home-quant-planet.js");
        String runtime = resource("static/js/home-quant-planet-runtime.js");
        String orchestration = entry + "\n" + runtime;

        assertTrue(template.contains("/js/home-quant-planet.js"));
        assertTrue(entry.contains("./home-quant-planet-motion.js"));
        assertTrue(entry.contains("./home-quant-planet-scene.js"));
        assertTrue(orchestration.contains("createPageLifecycle"));
        assertTrue(orchestration.contains("visibilitychange"));
        assertTrue(orchestration.contains("pageshow"));
        assertTrue(entry.contains("prefers-reduced-motion: reduce"));
        assertTrue(entry.contains("\"[::1]\""));
        assertFalse(orchestration.contains("setInterval"));
        assertFalse(orchestration.contains("fetch("));
        assertFalse(new ClassPathResource("static/css/home-observatory.css").exists());
        assertFalse(new ClassPathResource("static/js/home-observatory.js").exists());
        assertFalse(new ClassPathResource("static/js/home-observatory-motion.js").exists());
        assertFalse(new ClassPathResource("static/js/home-observatory-scroll.js").exists());
        assertFalse(new ClassPathResource("static/js/home-observatory-scene.js").exists());
    }

    @Test
    void homepageCoordinatesFullscreenOrbitRuntime() throws IOException {
        String entry = resource("static/js/home-quant-planet.js");
        String runtime = resource("static/js/home-quant-planet-runtime.js");
        String orchestration = entry + "\n" + runtime;

        assertTrue(entry.contains("./home-quant-planet-runtime.js"));
        assertTrue(runtime.contains("./home-quant-planet-input.js"));
        assertTrue(orchestration.contains("createIntroState"));
        assertTrue(orchestration.contains("createNavigationRingState"));
        assertTrue(orchestration.contains("createOrbitInputController"));
        assertTrue(orchestration.contains("webglcontextlost"));
        assertTrue(orchestration.contains("4000"));
        assertTrue(orchestration.contains("visibilitychange"));
        assertTrue(orchestration.contains("pagehide"));
        assertTrue(orchestration.contains("pageshow"));
        assertFalse(orchestration.contains("createScrollController"));
        assertFalse(orchestration.contains("normalizeScrollProgress"));
        assertFalse(orchestration.contains("IntersectionObserver"));
        assertFalse(orchestration.contains("setInterval"));
        assertFalse(orchestration.contains("fetch("));
        assertFalse(new ClassPathResource(
                "static/js/home-quant-planet-scroll.js").exists());
    }

    @Test
    void homepageBuildsAnOriginalLocalQuantPlanetWorld() throws Exception {
        String world = resource("static/js/home-quant-planet-world.js");
        String scene = resource("static/js/home-quant-planet-scene.js");

        assertEquals(200, get("/js/home-quant-planet-scene.js").statusCode());
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

    private static Document parseRenderedHtml(String html) throws Exception {
        String xhtml = html
                .replaceFirst("(?is)<!doctype html>", "")
                .replaceAll(
                        "\\s(data-[\\w-]+)(?=\\s|/?>)",
                        " $1=\"\"")
                .replaceAll(
                        "(?i)<(meta|link)([^>]*?)(?<!/)>",
                        "<$1$2/>");
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(
                "http://apache.org/xml/features/disallow-doctype-decl",
                true);
        factory.setFeature(
                "http://xml.org/sax/features/external-general-entities",
                false);
        factory.setFeature(
                "http://xml.org/sax/features/external-parameter-entities",
                false);
        return factory.newDocumentBuilder().parse(
                new InputSource(new StringReader(xhtml)));
    }

    private static List<Element> elementsWithAttribute(
            Element root,
            String tagName,
            String attribute) {
        List<Element> matches = new ArrayList<>();
        NodeList descendants = root.getElementsByTagName(tagName);
        for (int index = 0; index < descendants.getLength(); index++) {
            Node node = descendants.item(index);
            if (node instanceof Element element
                    && element.hasAttribute(attribute)) {
                matches.add(element);
            }
        }
        return matches;
    }

    private static List<Element> elementsWithClass(
            Element root,
            String className) {
        List<Element> matches = new ArrayList<>();
        NodeList descendants = root.getElementsByTagName("*");
        for (int index = 0; index < descendants.getLength(); index++) {
            Node node = descendants.item(index);
            if (node instanceof Element element
                    && List.of(element.getAttribute("class").split("\\s+"))
                            .contains(className)) {
                matches.add(element);
            }
        }
        return matches;
    }

    private static String cssRule(String css, String selector) {
        Matcher matcher = Pattern.compile(
                "(?s)(?:^|})\\s*" + Pattern.quote(selector)
                        + "\\s*\\{([^}]*)}")
                .matcher(css);
        assertTrue(matcher.find(), () -> "Missing CSS rule: " + selector);
        return matcher.group(1);
    }

    private static String cssDeclaration(String rule, String property) {
        Matcher matcher = Pattern.compile(
                "(?s)(?:^|;)\\s*" + Pattern.quote(property)
                        + "\\s*:\\s*([^;]+)")
                .matcher(rule);
        assertTrue(matcher.find(), () -> "Missing CSS property: " + property);
        return matcher.group(1).strip().replaceAll("\\s+", " ");
    }

    private static String cssAtRuleBlock(String css, String atRule) {
        int atRuleStart = css.indexOf(atRule);
        assertTrue(atRuleStart >= 0, () -> "Missing CSS at-rule: " + atRule);
        int blockStart = css.indexOf('{', atRuleStart);
        assertTrue(blockStart >= 0, () -> "Missing CSS block: " + atRule);
        int depth = 0;
        for (int index = blockStart; index < css.length(); index++) {
            char character = css.charAt(index);
            if (character == '{') {
                depth++;
            } else if (character == '}') {
                depth--;
                if (depth == 0) {
                    return css.substring(blockStart + 1, index);
                }
            }
        }
        throw new AssertionError("Unclosed CSS at-rule: " + atRule);
    }

    private static void assertMinLengthRatioBetween(
            String numerator,
            String denominator,
            double minimum,
            double maximum) {
        Pattern minLengths = Pattern.compile(
                "min\\(\\s*([0-9.]+)(v[hw])\\s*,\\s*"
                        + "([0-9.]+)(v[hw])\\s*\\)");
        Matcher numeratorMatcher = minLengths.matcher(numerator);
        Matcher denominatorMatcher = minLengths.matcher(denominator);
        assertTrue(numeratorMatcher.matches(), () -> "Not a min length: "
                + numerator);
        assertTrue(denominatorMatcher.matches(), () -> "Not a min length: "
                + denominator);
        for (int component = 0; component < 2; component++) {
            int valueGroup = 1 + component * 2;
            int unitGroup = valueGroup + 1;
            assertEquals(
                    denominatorMatcher.group(unitGroup),
                    numeratorMatcher.group(unitGroup));
            double ratio = Double.parseDouble(
                    numeratorMatcher.group(valueGroup))
                    / Double.parseDouble(
                            denominatorMatcher.group(valueGroup));
            assertTrue(
                    ratio >= minimum && ratio <= maximum,
                    () -> "Length ratio outside range: " + ratio);
        }
    }

    private static void assertPlanetBottomAtMost(
            String experienceRule,
            double maximumViewportPercent) {
        double center = Double.parseDouble(cssDeclaration(
                experienceRule, "--planet-center-y").replace("%", ""));
        Matcher verticalRadius = Pattern.compile(
                "([0-9.]+)vh")
                .matcher(cssDeclaration(
                        experienceRule, "--planet-radius"));
        assertTrue(verticalRadius.find());
        double radius = Double.parseDouble(verticalRadius.group(1));
        assertTrue(center + radius <= maximumViewportPercent);
    }

    private static List<String> cssRuleBodiesWithSelectorTokens(
            String css,
            String... tokens) {
        List<String> bodies = new ArrayList<>();
        Matcher matcher = Pattern.compile(
                "(?s)([^{}]+)\\{([^{}]*)}")
                .matcher(css);
        while (matcher.find()) {
            String selector = matcher.group(1);
            boolean includesAllTokens = true;
            for (String token : tokens) {
                includesAllTokens &= selector.contains(token);
            }
            if (includesAllTokens) {
                bodies.add(matcher.group(2));
            }
        }
        return bodies;
    }

    private static List<String> cssRuleBodiesForExactSelector(
            String css,
            String expectedSelector) {
        List<String> bodies = new ArrayList<>();
        Matcher matcher = Pattern.compile(
                "(?s)([^{}]+)\\{([^{}]*)}")
                .matcher(css);
        while (matcher.find()) {
            String[] selectors = matcher.group(1).split(",");
            for (String selector : selectors) {
                if (expectedSelector.equals(selector.strip())) {
                    bodies.add(matcher.group(2));
                    break;
                }
            }
        }
        return bodies;
    }

    private static boolean hasCssDeclaration(String rule, String property) {
        return Pattern.compile(
                "(?s)(?:^|;)\\s*" + Pattern.quote(property)
                        + "\\s*:")
                .matcher(rule)
                .find();
    }

    private HttpResponse<Void> get(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + path))
                .GET()
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.discarding());
    }

    private HttpResponse<String> getBody(String path)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + path))
                .GET()
                .build();
        return HttpClient.newHttpClient().send(
                request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
}
