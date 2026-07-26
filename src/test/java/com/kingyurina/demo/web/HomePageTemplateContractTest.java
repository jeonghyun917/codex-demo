package com.kingyurina.demo.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;
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
    void homepageReferenceTitleAssetIsTransparentAndUnchanged()
            throws IOException, NoSuchAlgorithmException {
        ClassPathResource resource = new ClassPathResource(
                "static/img/home-messenger-reference-title.png");
        BufferedImage image = ImageIO.read(resource.getInputStream());

        assertNotNull(image);
        assertTrue(image.getWidth() >= 900);
        assertTrue(image.getHeight() >= 700);
        assertTrue(image.getColorModel().hasAlpha());
        assertEquals(0, image.getRGB(0, 0) >>> 24);
        assertEquals(0, image.getRGB(image.getWidth() - 1, 0) >>> 24);
        assertEquals(0, image.getRGB(0, image.getHeight() - 1) >>> 24);
        assertEquals(
                0,
                image.getRGB(
                        image.getWidth() - 1,
                        image.getHeight() - 1) >>> 24);

        String digest = HexFormat.of().withUpperCase().formatHex(
                MessageDigest.getInstance("SHA-256")
                        .digest(resource.getContentAsByteArray()));
        assertEquals(
                "A7CD6FAC1D59F919243EBFC4DF4948A438A3981111D3D54D4B7DF638B1398163",
                digest);
    }

    @Test
    void homepageAssetsAreLocalAndPublicWhileUnrelatedPathsRemainProtected()
            throws Exception {
        assertEquals(200, get("/vendor/motion/motion.js").statusCode());
        HttpResponse<byte[]> title = getBytes(
                "/img/home-messenger-reference-title.png");
        assertEquals(200, title.statusCode());
        assertEquals(
                "image/png",
                title.headers().firstValue("Content-Type").orElse(""));
        assertTrue(title.body().length > 0);
        HttpResponse<byte[]> fallback = getBytes(
                "/img/home-quant-planet-fallback.svg");
        assertEquals(200, fallback.statusCode());
        assertEquals(
                "image/svg+xml",
                fallback.headers().firstValue("Content-Type").orElse(""));
        assertTrue(fallback.body().length > 0);
        for (String asset : List.of(
                "/js/home-quant-planet-glyph-contours.js",
                "/js/home-quant-planet-glyphs.js")) {
            HttpResponse<byte[]> served = getBytes(asset);
            assertEquals(200, served.statusCode());
            assertTrue(served.body().length > 0);
        }
        assertEquals(403, get("/admin").statusCode());

        assertTrue(resource("static/vendor/motion/LICENSE.md")
                .contains("MIT License"));
        assertTrue(resource("static/js/vendor/three.module.js")
                .contains("const REVISION = '165'"));
    }

    @Test
    void homepageUsesOneReferenceTitleAndOneQuantAction() throws Exception {
        String template = resource("templates/index.html");
        HttpResponse<String> response = getBody("/");
        assertEquals(200, response.statusCode());
        Document document = parseRenderedHtml(response.body());
        Element root = document.getDocumentElement();

        assertEquals(0, occurrences(template, "data-product-title-link"));
        assertEquals(0, occurrences(template, "data-product-orbit-link"));
        assertEquals(0, occurrences(template, "data-product-position"));
        assertEquals(1, occurrences(template, "data-reference-title"));
        assertEquals(1, occurrences(template, "data-enter-quant"));

        List<Element> titles = elementsWithAttribute(
                root, "img", "data-reference-title");
        assertEquals(1, titles.size());
        assertEquals(
                "/img/home-messenger-reference-title.png",
                titles.getFirst().getAttribute("src"));
        assertTrue(List.of(
                titles.getFirst().getAttribute("class").split("\\s+"))
                .contains("reference-title--fallback"));
        assertTrue(titles.getFirst().hasAttribute("alt"));
        assertEquals("", titles.getFirst().getAttribute("alt"));

        List<Element> actions = elementsWithAttribute(
                root, "a", "data-enter-quant");
        assertEquals(1, actions.size());
        assertEquals("/quant", actions.getFirst().getAttribute("href"));
        assertEquals(
                "ENTER",
                actions.getFirst().getTextContent().strip());
        assertEquals("main", actions.getFirst().getParentNode().getNodeName());

        assertEquals(1, elementsWithTag(root, "a").size());
        assertEquals(0, elementsWithTag(root, "header").size());
        assertEquals(0, elementsWithTag(root, "nav").size());
        assertEquals(0, elementsWithTag(root, "dl").size());
        assertEquals(0, elementsWithTag(root, "dt").size());
        assertEquals(0, elementsWithTag(root, "dd").size());
        assertFalse(template.contains("data-home-nav"));
        assertFalse(template.contains("home-brand"));
        assertFalse(template.contains("metric"));
        assertFalse(template.contains("Product navigation"));
        assertFalse(template.contains("aria-label=\"Product navigation\""));
    }

    @Test
    void homepageNoScriptMarkupKeepsOneCompleteAccessibleComposition()
            throws Exception {
        HttpResponse<String> response = getBody("/");
        Document document = parseRenderedHtml(response.body());
        Element root = document.getDocumentElement();
        List<Element> titles = elementsWithAttribute(
                root, "img", "data-reference-title");
        List<Element> actions = elementsWithAttribute(
                root, "a", "data-enter-quant");
        List<Element> headings = elementsWithTag(root, "h1");

        assertEquals(1, titles.size());
        assertTrue(titles.getFirst().hasAttribute("alt"));
        assertEquals("", titles.getFirst().getAttribute("alt"));
        assertFalse(titles.getFirst().hasAttribute("hidden"));
        assertEquals(1, headings.size());
        assertEquals(
                "Messenger",
                headings.getFirst().getTextContent().strip());

        assertEquals(1, actions.size());
        assertEquals("/quant", actions.getFirst().getAttribute("href"));
        assertEquals("ENTER", actions.getFirst().getTextContent().strip());
        assertFalse(actions.getFirst().hasAttribute("hidden"));
        assertFalse(actions.getFirst().hasAttribute("tabindex"));
    }

    @Test
    void homepageKeepsOnlyTheMinimalReferenceShell() throws Exception {
        HttpResponse<String> response = getBody("/");
        Document document = parseRenderedHtml(response.body());
        Element root = document.getDocumentElement();
        List<Element> experiences = elementsWithAttribute(
                root, "main", "data-home-experience");

        assertEquals(1, experiences.size());
        Element experience = experiences.getFirst();
        assertEquals("main-content", experience.getAttribute("id"));
        assertEquals("home-title", experience.getAttribute("aria-labelledby"));
        assertEquals(
                1,
                elementsWithAttribute(
                        experience, "*", "data-intro-loader").size());
        assertEquals(
                1,
                elementsWithAttribute(
                        experience, "*", "data-home-scene").size());
        assertEquals(
                1,
                elementsWithAttribute(
                        experience, "canvas", "data-home-canvas").size());
        assertEquals(
                1,
                elementsWithAttribute(
                        experience, "*", "data-planet-fallback").size());
        assertEquals(1, elementsWithTag(experience, "h1").size());
        assertEquals(
                "Messenger",
                elementsWithTag(experience, "h1")
                        .getFirst().getTextContent().strip());
        assertTrue(response.body().contains(
                "It's a small planet, but someone's gotta make the deliveries."));
        assertFalse(response.body().contains("ENTER QUANT"));
        assertFalse(response.body().contains("Illustrative model view"));
        assertFalse(response.body().contains("DRAG TO ORBIT"));
        assertFalse(response.body().contains("QUANT FIELD / ONLINE"));
    }

    @Test
    void homepageCssDefinesFiveFullscreenReferenceLayers() throws IOException {
        String css = resource("static/css/home-quant-planet.css");
        String normalized = css.replaceAll("\\s+", " ");
        String experience = cssRule(css, ".planet-experience");
        String title = cssRule(css, ".reference-title");
        String action = cssRule(css, ".planet-primary-action");
        String lockedAction = cssRule(
                css, ".planet-action-locked .planet-primary-action");

        assertTrue(normalized.contains("height: 100dvh"));
        assertTrue(normalized.contains("overflow: hidden"));
        assertEquals(
                "min(82vh, 78vw)",
                cssDeclaration(experience, "--reference-world-size"));
        assertEquals(
                "min(48vw, 64vh)",
                cssDeclaration(experience, "--reference-title-width"));
        assertEquals(
                "max(4.8vh, env(safe-area-inset-bottom))",
                cssDeclaration(experience, "--reference-action-bottom"));
        assertEquals(
                "0",
                cssDeclaration(experience, "--intro-world-progress"));
        assertEquals(
                "0",
                cssDeclaration(experience, "--intro-title-progress"));
        assertEquals(
                "0",
                cssDeclaration(experience, "--intro-action-progress"));

        assertTrue(css.contains(".planet-intro-loader"));
        assertTrue(css.contains(".planet-intro-loader__mark"));
        assertTrue(css.contains(".planet-scene"));
        assertTrue(css.contains(".planet-fallback"));
        assertTrue(css.contains("[data-home-canvas]"));
        assertTrue(css.contains(".reference-title"));
        assertTrue(css.contains(".planet-primary-action"));
        assertTrue(css.contains(".home-semantic-summary"));
        assertEquals(
                1,
                occurrences(css, "/img/home-quant-planet-fallback.svg"));

        assertEquals("50%", cssDeclaration(title, "left"));
        assertEquals(
                "var(--reference-world-center-y)",
                cssDeclaration(title, "top"));
        assertEquals(
                "var(--reference-title-width)",
                cssDeclaration(title, "width"));
        assertEquals(
                "var(--reference-action-bottom)",
                cssDeclaration(action, "bottom"));
        assertEquals(
                "clamp(112px, 9.5vw, 148px)",
                cssDeclaration(action, "min-width"));
        assertEquals("44px", cssDeclaration(action, "min-height"));
        assertTrue(action.contains("var(--reference-action-yellow)"));
        assertEquals(
                "none",
                cssDeclaration(lockedAction, "pointer-events"));
        assertTrue(css.contains(":focus-visible"));
        assertTrue(css.contains("@media (prefers-reduced-motion: reduce)"));

        assertFalse(css.contains(".product-title"));
        assertFalse(css.contains(".product-orbit"));
        assertFalse(css.contains(".home-nav"));
        assertFalse(css.contains(".home-brand"));
        assertFalse(css.contains(".planet-status"));
        assertFalse(css.contains(".planet-interaction-hint"));
    }

    @Test
    void homepageFallbackIllustrationMapsEveryAuthoredWorldLandmark()
            throws Exception {
        ClassPathResource resource = new ClassPathResource(
                "static/img/home-quant-planet-fallback.svg");
        assertTrue(resource.exists());

        Document illustration = parseXml(
                resource.getContentAsString(StandardCharsets.UTF_8));
        Element svg = illustration.getDocumentElement();
        assertEquals("svg", svg.getNodeName());
        assertEquals("0 0 800 800", svg.getAttribute("viewBox"));
        assertEquals(0, elementsWithTag(svg, "image").size());

        for (String landmark : List.of(
                "fallback-planet-core",
                "fallback-water-band",
                "fallback-temple",
                "fallback-industrial-machinery",
                "fallback-utility-antenna",
                "fallback-lower-canopy",
                "fallback-exterior-debris",
                "fallback-summit-shrine",
                "fallback-mountain-village",
                "fallback-industrial-yard",
                "fallback-utility-ridge",
                "fallback-water-works",
                "fallback-backside-houses",
                "fallback-forest-belt",
                "fallback-floating-debris")) {
            assertNotNull(
                    elementWithId(svg, landmark),
                    () -> "Missing fallback landmark: " + landmark);
        }

        String css = resource("static/css/home-quant-planet.css");
        String fallbackRule = cssRule(css, ".planet-fallback");
        assertEquals(
                "url(\"/img/home-quant-planet-fallback.svg\")",
                cssDeclaration(fallbackRule, "background-image"));
        assertEquals("contain", cssDeclaration(fallbackRule, "background-size"));
    }

    @Test
    void homepageFallbackStatesKeepTheSameCompleteComposition()
            throws IOException {
        String css = resource("static/css/home-quant-planet.css");
        String scene = cssRule(css, ".planet-scene");
        String fallback = cssRuleContainingSelector(
                css, ".planet-fallback");
        String title = cssRule(css, ".reference-title");
        String action = cssRule(css, ".planet-primary-action");

        assertEquals(
                "var(--reference-world-size)",
                cssDeclaration(scene, "width"));
        assertEquals(
                "var(--reference-world-size)",
                cssDeclaration(scene, "height"));
        assertEquals("0", cssDeclaration(fallback, "inset"));
        assertEquals("100%", cssDeclaration(fallback, "width"));
        assertEquals("100%", cssDeclaration(fallback, "height"));
        assertEquals(
                "var(--reference-world-center-y)",
                cssDeclaration(scene, "top"));
        assertEquals(
                "var(--reference-world-center-y)",
                cssDeclaration(title, "top"));
        assertEquals(
                "var(--reference-title-width)",
                cssDeclaration(title, "width"));
        assertEquals(
                "var(--reference-action-bottom)",
                cssDeclaration(action, "bottom"));

        for (String selector : List.of(
                ".planet-static-mode .planet-experience",
                ".planet-reduced-motion .planet-experience",
                ".planet-webgl-failed .planet-experience")) {
            String fallbackState = cssRuleContainingSelector(css, selector);
            assertEquals(
                    "1",
                    cssDeclaration(
                            fallbackState, "--intro-world-progress"));
            assertEquals(
                    "1",
                    cssDeclaration(
                            fallbackState, "--intro-title-progress"));
            assertEquals(
                    "1",
                    cssDeclaration(
                            fallbackState, "--intro-action-progress"));
            assertFalse(fallbackState.contains("--reference-world-size"));
            assertFalse(fallbackState.contains("--reference-title-width"));
            assertFalse(fallbackState.contains("--reference-action-bottom"));
        }
    }

    @Test
    void homepageProductionHasNoScrollOrLegacyProductBehavior()
            throws IOException {
        String production = String.join("\n", List.of(
                resource("templates/index.html"),
                resource("static/css/home-quant-planet.css"),
                resource("static/js/home-quant-planet.js"),
                resource("static/js/home-quant-planet-composition.js"),
                resource("static/js/home-quant-planet-input.js"),
                resource("static/js/home-quant-planet-motion.js"),
                resource("static/js/home-quant-planet-runtime.js"),
                resource("static/js/home-quant-planet-scene.js"),
                resource("static/js/home-quant-planet-world.js")));
        String normalized = production.toLowerCase();

        assertFalse(normalized.contains("wheel"));
        assertFalse(normalized.contains("scroll"));
        assertFalse(production.contains("PRODUCT_SCENES"));
        assertFalse(production.contains("createProductSelection"));
        assertFalse(production.contains("data-product-"));
        assertFalse(production.contains("product-title"));
    }

    @Test
    void homepageCssHasReviewedDesktopTabletMobileAndShortLandscapeCases()
            throws IOException {
        String css = resource("static/css/home-quant-planet.css")
                .replaceAll("\\s+", " ");

        assertTrue(css.contains(
                "@media (min-width: 1440px) and (min-height: 900px)"));
        assertTrue(css.contains(
                "@media (min-width: 769px) and (max-width: 1024px) "
                        + "and (orientation: landscape)"));
        assertTrue(css.contains(
                "@media (min-width: 600px) and (max-width: 768px) "
                        + "and (orientation: portrait)"));
        assertTrue(css.contains(
                "@media (max-width: 375px) and (min-height: 700px) "
                        + "and (orientation: portrait)"));
        assertTrue(css.contains(
                "@media (max-height: 520px) and (orientation: landscape)"));
    }

    @Test
    void homepageCoordinatesTheSingleHeroRuntime() throws IOException {
        String template = resource("templates/index.html");
        String entry = resource("static/js/home-quant-planet.js");
        String runtime = resource("static/js/home-quant-planet-runtime.js");
        String scene = resource("static/js/home-quant-planet-scene.js");
        String world = resource("static/js/home-quant-planet-world.js");

        assertTrue(template.contains("/vendor/motion/motion.js"));
        assertTrue(template.contains("/js/home-quant-planet.js"));
        assertTrue(entry.contains("./home-quant-planet-runtime.js"));
        assertTrue(entry.contains("./home-quant-planet-scene.js"));
        assertTrue(runtime.contains("createHeroIntroState"));
        assertTrue(runtime.contains("createHeroPose"));
        assertTrue(runtime.contains("createHeroInputController"));
        assertTrue(runtime.contains("REFERENCE_HERO"));
        assertTrue(scene.contains("world.update({"));
        assertTrue(scene.contains("glyphs.update({"));
        assertTrue(scene.contains("fitPerspectiveSphere"));
        assertTrue(scene.contains("Math.min"));
        assertTrue(scene.contains("1.5"));

        for (String group : List.of(
                "ReferencePlanetCore",
                "ReferenceWaterBand",
                "ReferenceTemplePeak",
                "ReferenceIndustrialCluster",
                "ReferenceUtilityCluster",
                "ReferenceLowerCanopy",
                "ReferenceFloatingDebris")) {
            assertTrue(world.contains(group), () -> "Missing " + group);
        }
        assertFalse(world.contains("ObserveStation"));
        assertFalse(world.contains("ResolveWorks"));
        assertFalse(world.contains("DecisionGate"));
        assertFalse(world.contains("QuantCourier"));
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
                        "(?i)<(meta|link|img)([^>]*?)(?<!/)>",
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

    private static Document parseXml(String xml) throws Exception {
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
                new InputSource(new StringReader(xml)));
    }

    private static Element elementWithId(Element root, String id) {
        NodeList descendants = root.getElementsByTagName("*");
        for (int index = 0; index < descendants.getLength(); index++) {
            Node node = descendants.item(index);
            if (node instanceof Element element
                    && id.equals(element.getAttribute("id"))) {
                return element;
            }
        }
        return null;
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

    private static List<Element> elementsWithTag(
            Element root,
            String tagName) {
        List<Element> matches = new ArrayList<>();
        NodeList descendants = root.getElementsByTagName(tagName);
        for (int index = 0; index < descendants.getLength(); index++) {
            Node node = descendants.item(index);
            if (node instanceof Element element) {
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

    private static String cssRuleContainingSelector(
            String css,
            String selector) {
        Matcher matcher = Pattern.compile(
                "(?s)([^{}]+)\\{([^}]*)}")
                .matcher(css);
        while (matcher.find()) {
            if (matcher.group(1).contains("@")) {
                continue;
            }
            for (String candidate : matcher.group(1).split(",")) {
                if (candidate.strip().equals(selector)) {
                    return matcher.group(2);
                }
            }
        }
        throw new AssertionError("Missing CSS selector: " + selector);
    }

    private HttpResponse<Void> get(String path)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + path))
                .GET()
                .build();
        return HttpClient.newHttpClient().send(
                request,
                HttpResponse.BodyHandlers.discarding());
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

    private HttpResponse<byte[]> getBytes(String path)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + path))
                .GET()
                .build();
        return HttpClient.newHttpClient().send(
                request,
                HttpResponse.BodyHandlers.ofByteArray());
    }
}
