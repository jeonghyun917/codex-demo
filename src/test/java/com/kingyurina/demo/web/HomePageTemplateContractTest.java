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
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
        List<Element> actionLabels = elementsWithTag(
                actions.getFirst(), "span");
        assertEquals(1, actionLabels.size());
        assertEquals(
                "planet-primary-action__label",
                actionLabels.getFirst().getAttribute("class"));
        assertEquals("ENTER", actionLabels.getFirst().getTextContent().strip());
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
        String scene = cssRule(css, ".planet-scene");
        String title = cssRule(css, ".reference-title");
        String action = cssRule(css, ".planet-primary-action");
        String lockedAction = cssRule(
                css, ".planet-action-locked .planet-primary-action");

        assertTrue(normalized.contains("height: 100dvh"));
        assertTrue(normalized.contains("overflow: hidden"));
        assertEquals("0", cssDeclaration(scene, "inset"));
        assertEquals("100vw", cssDeclaration(scene, "width"));
        assertEquals("100dvh", cssDeclaration(scene, "height"));
        assertFalse(scene.contains("var(--reference-world-size)"));
        assertFalse(normalized.contains("--reference-world-size"));
        assertEquals(
                "min(30vw, 52vh)",
                cssDeclaration(experience, "--reference-title-width"));
        assertEquals(
                "max(10vh, env(safe-area-inset-bottom))",
                cssDeclaration(experience, "--reference-action-bottom"));
        assertEquals(
                "-5dvh",
                cssDeclaration(experience, "--reference-world-shift-y"));
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
                "clamp(156px, 11vw, 196px)",
                cssDeclaration(action, "min-width"));
        assertEquals("54px", cssDeclaration(action, "min-height"));
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
    void homepageEnterActionUsesTheApprovedIndustrialHatchMotion()
            throws IOException {
        String css = resource("static/css/home-quant-planet.css");
        String normalized = css.replaceAll("\\s+", " ");
        String action = cssRule(css, ".planet-primary-action");
        String before = cssRule(css, ".planet-primary-action::before");
        String after = cssRule(css, ".planet-primary-action::after");
        String hover = cssRuleContainingSelector(
                css, ".planet-primary-action:hover");
        String active = cssRule(css, ".planet-primary-action:active");

        assertEquals("1s", cssDeclaration(action, "animation-duration"));
        assertEquals(
                "enter-hatch-twitch",
                cssDeclaration(action, "animation-name"));
        assertEquals(
                "polygon(8% 0, 92% 0, 100% 24%, 100% 76%, "
                        + "92% 100%, 8% 100%, 0 76%, 0 24%)",
                cssDeclaration(action, "clip-path"));
        assertTrue(before.contains("radial-gradient"));
        assertTrue(after.contains("var(--reference-ink)"));
        assertTrue(hover.contains("translate(-50%, -3px)"));
        assertTrue(active.contains("translate(-50%, 6px)"));
        assertTrue(css.contains("@keyframes enter-hatch-twitch"));
        assertTrue(normalized.contains(
                "@media (prefers-reduced-motion: reduce)"));
        assertTrue(normalized.contains(
                ".planet-primary-action { animation: none;"));
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
                "fallback-rear-rocks",
                "fallback-rear-scraps",
                "fallback-rear-rings",
                "fallback-rear-atmosphere",
                "fallback-rear-flecks",
                "fallback-planet-artwork",
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
    void homepageFallbackRearFieldHasDiverseLowContrastGeometryBehindThePlanet()
            throws Exception {
        Element svg = parseXml(resource(
                "static/img/home-quant-planet-fallback.svg"))
                .getDocumentElement();
        Element artwork = elementWithId(svg, "fallback-planet-artwork");
        List<Element> topLevel = directElementChildren(svg);
        int artworkIndex = topLevel.indexOf(artwork);
        List<String> rearIds = List.of(
                "fallback-rear-rocks",
                "fallback-rear-scraps",
                "fallback-rear-rings",
                "fallback-rear-atmosphere",
                "fallback-rear-flecks");
        Map<String, Integer> minimumGeometry = Map.of(
                "fallback-rear-rocks", 4,
                "fallback-rear-scraps", 4,
                "fallback-rear-rings", 3,
                "fallback-rear-atmosphere", 4,
                "fallback-rear-flecks", 6);
        List<SvgBounds> allGeometry = new ArrayList<>();
        Set<String> familyStyles = new HashSet<>();

        assertTrue(artworkIndex >= 0);
        for (String id : rearIds) {
            Element group = elementWithId(svg, id);
            assertTrue(
                    group.getParentNode() == svg,
                    () -> id + " must be a direct SVG sibling");
            assertTrue(
                    topLevel.indexOf(group) < artworkIndex,
                    () -> id + " must paint before the planet artwork");

            List<Element> geometry = directElementChildren(group).stream()
                    .filter(element -> "path".equals(element.getTagName()))
                    .toList();
            assertTrue(
                    geometry.size() >= minimumGeometry.get(id),
                    () -> id + " needs multiple authored silhouettes");
            List<SvgBounds> bounds = geometry.stream()
                    .map(HomePageTemplateContractTest::pathBounds)
                    .toList();
            assertTrue(
                    geometry.stream().allMatch(
                            HomePageTemplateContractTest::hasVisiblePaint),
                    () -> id + " contains invisible geometry");
            assertTrue(
                    bounds.stream().allMatch(bound ->
                            bound.width() > 4 && bound.height() > 4),
                    () -> id + " contains empty or degenerate geometry");
            assertTrue(
                    bounds.stream()
                            .map(HomePageTemplateContractTest::sizeTier)
                            .distinct()
                            .count() >= 2,
                    () -> id + " needs more than one visible size");
            assertTrue(
                    geometry.stream()
                            .map(HomePageTemplateContractTest::silhouetteSignature)
                            .distinct()
                            .count() >= 2,
                    () -> id + " needs distinct authored silhouettes");
            allGeometry.addAll(bounds);
            familyStyles.add(String.join("|",
                    group.getAttribute("fill"),
                    group.getAttribute("stroke"),
                    group.getAttribute("stroke-width"),
                    group.getAttribute("opacity")));
        }

        assertTrue(familyStyles.size() >= 4);
        assertTrue(
                allGeometry.stream().map(HomePageTemplateContractTest::sizeTier)
                        .distinct().count() >= 5,
                "Rear field needs at least five visible size tiers");
        assertTrue(allGeometry.stream().anyMatch(bound -> bound.centerX() < 160));
        assertTrue(allGeometry.stream().anyMatch(bound -> bound.centerX() > 640));
        assertTrue(allGeometry.stream().anyMatch(bound -> bound.centerY() < 120));
        assertTrue(allGeometry.stream().anyMatch(bound -> bound.centerY() > 680));
        long mirrored = allGeometry.stream()
                .filter(bound -> hasMirroredPeer(bound, allGeometry, 800))
                .count();
        assertTrue(
                mirrored * 2 < allGeometry.size(),
                "Rear field must remain visibly asymmetric");

        String css = resource("static/css/home-quant-planet.css");
        int field = parseHexColor(cssDeclaration(
                cssRule(css, ":root"), "--reference-field"));
        Element atmosphere = elementWithId(
                svg, "fallback-rear-atmosphere");
        double groupOpacity = opacity(atmosphere, 1);
        for (Element cloud : directElementChildren(atmosphere)) {
            int fill = parseHexColor(inheritedAttribute(
                    cloud, atmosphere, "fill"));
            double effectiveOpacity = groupOpacity * opacity(cloud, 1);
            assertTrue(
                    effectiveOpacity >= .12 && effectiveOpacity <= .3,
                    "Atmospheric clouds must stay visible but low opacity");
            assertTrue(
                    contrastRatio(fill, field) <= 1.5,
                    "Atmospheric clouds must stay near the field color");
        }
    }

    @Test
    void homepageFallbackScaleLayersAndResponsiveActionStayInsideSafeBounds()
            throws Exception {
        Element svg = parseXml(resource(
                "static/img/home-quant-planet-fallback.svg"))
                .getDocumentElement();
        Element artwork = elementWithId(svg, "fallback-planet-artwork");
        SvgTransform transform = svgTransform(
                artwork.getAttribute("transform"));
        assertTrue(transform.scale() >= 1.17 && transform.scale() <= 1.23);

        long scaledGroups = elementsWithTag(svg, "g").stream()
                .filter(element ->
                        element.getAttribute("transform").contains("scale("))
                .count();
        assertEquals(1, scaledGroups);
        SvgBounds sourceArtwork = paintedGeometryBounds(artwork);
        SvgBounds fittedArtwork = transform.apply(sourceArtwork);
        assertTrue(
                fittedArtwork.minX() >= 0,
                () -> "Painted artwork clips left: " + fittedArtwork);
        assertTrue(
                fittedArtwork.minY() >= 0,
                () -> "Painted artwork clips top: " + fittedArtwork);
        assertTrue(
                fittedArtwork.maxX() <= 800,
                () -> "Painted artwork clips right: " + fittedArtwork);
        assertTrue(
                fittedArtwork.maxY() <= 760,
                () -> "Painted artwork enters CTA area: " + fittedArtwork);

        String css = resource("static/css/home-quant-planet.css");
        int sceneZ = Integer.parseInt(cssDeclaration(
                cssRule(css, ".planet-scene"), "z-index"));
        int titleZ = Integer.parseInt(cssDeclaration(
                cssRule(css, ".reference-title"), "z-index"));
        int actionZ = Integer.parseInt(cssDeclaration(
                cssRule(css, ".planet-primary-action"), "z-index"));
        assertTrue(sceneZ < titleZ && titleZ < actionZ);

        String experience = cssRule(css, ".planet-experience");
        String scene = cssRule(css, ".planet-scene");
        String fallback = cssRule(css, ".planet-fallback");
        assertEquals("fixed", cssDeclaration(experience, "position"));
        assertEquals("hidden", cssDeclaration(experience, "overflow"));
        assertEquals("hidden", cssDeclaration(scene, "overflow"));
        assertEquals("layout paint", cssDeclaration(scene, "contain"));
        assertEquals("paint", cssDeclaration(fallback, "contain"));
        assertEquals("contain", cssDeclaration(fallback, "background-size"));

        List<ResponsiveActionCase> cases = List.of(
                new ResponsiveActionCase(
                        1280, 720, null, 10, 54),
                new ResponsiveActionCase(
                        1440, 900,
                        "(min-width: 1440px) and (min-height: 900px)",
                        5.2, 54),
                new ResponsiveActionCase(
                        1920, 1080,
                        "(min-width: 1440px) and (min-height: 900px)",
                        5.2, 54),
                new ResponsiveActionCase(
                        768, 1024,
                        "(min-width: 600px) and (max-width: 768px) "
                                + "and (orientation: portrait)",
                        6, 54),
                new ResponsiveActionCase(
                        375, 812,
                        "(max-width: 375px) and (min-height: 700px) "
                                + "and (orientation: portrait)",
                        6.5, 48));
        for (ResponsiveActionCase viewport : cases) {
            String responsiveExperience = viewport.mediaQuery() == null
                    ? experience
                    : cssMediaRule(
                            css,
                            viewport.mediaQuery(),
                            ".planet-experience");
            double bottomVh = leadingVh(cssDeclaration(
                    responsiveExperience, "--reference-action-bottom"));
            assertEquals(viewport.bottomVh(), bottomVh, .001);
            double actionTop = viewport.height()
                    - viewport.height() * bottomVh / 100
                    - viewport.actionHeight();
            assertTrue(
                    actionTop >= 16,
                    () -> "ENTER clips at " + viewport.width()
                            + "x" + viewport.height());
        }
    }

    @Test
    void homepagePlanetAssetCacheVersionsStayInLockstep() throws IOException {
        String template = resource("templates/index.html");
        Matcher matcher = Pattern.compile(
                "home-quant-planet\\.(?:css|js)[^\\r\\n]*?"
                        + "(?:v='|[?&]v=)([0-9]+)")
                .matcher(template);
        List<String> versions = new ArrayList<>();
        while (matcher.find()) {
            versions.add(matcher.group(1));
        }

        assertEquals(4, versions.size());
        assertEquals(1, new HashSet<>(versions).size());
        assertTrue(versions.getFirst().length() >= 10);
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
        String pageRoots = cssRuleContainingSelector(css, "html");
        String experience = cssRule(css, ".planet-experience");
        String fallbackArtwork = cssRule(css, ".planet-fallback");

        assertEquals("hidden", cssDeclaration(pageRoots, "overflow"));
        assertEquals("fixed", cssDeclaration(experience, "position"));
        assertEquals("0", cssDeclaration(experience, "inset"));
        assertEquals("hidden", cssDeclaration(experience, "overflow"));
        assertEquals("0", cssDeclaration(scene, "inset"));
        assertEquals("100vw", cssDeclaration(scene, "width"));
        assertEquals("100dvh", cssDeclaration(scene, "height"));
        assertEquals("hidden", cssDeclaration(scene, "overflow"));
        assertEquals("layout paint", cssDeclaration(scene, "contain"));
        assertEquals(
                "translateY(var(--reference-world-shift-y))",
                cssDeclaration(scene, "transform"));
        assertEquals("0", cssDeclaration(fallback, "inset"));
        assertEquals("100%", cssDeclaration(fallback, "width"));
        assertEquals("100%", cssDeclaration(fallback, "height"));
        assertEquals("paint", cssDeclaration(fallbackArtwork, "contain"));
        assertEquals("hidden", cssDeclaration(fallbackArtwork, "overflow"));
        assertEquals(
                "contain",
                cssDeclaration(fallbackArtwork, "background-size"));
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
                "calc(100vw - 2rem - env(safe-area-inset-left) "
                        + "- env(safe-area-inset-right))",
                cssDeclaration(action, "max-width"));
        assertTrue(css.replaceAll("\\s+", " ").contains(
                "@media (prefers-reduced-motion: reduce)"));
        assertTrue(css.replaceAll("\\s+", " ").contains(
                ".planet-primary-action { animation: none; "
                        + "transition: none;"));

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
                resource("static/js/home-quant-planet-rear-field-layout.js"),
                resource("static/js/home-quant-planet-rear-field.js"),
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
        assertTrue(scene.contains("rearField.update({"));
        assertTrue(scene.contains("glyphs.update({"));
        assertTrue(scene.contains("fitReferenceWorld"));
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

    private static List<Element> directElementChildren(Element parent) {
        List<Element> children = new ArrayList<>();
        NodeList nodes = parent.getChildNodes();
        for (int index = 0; index < nodes.getLength(); index++) {
            if (nodes.item(index) instanceof Element element) {
                children.add(element);
            }
        }
        return children;
    }

    private static SvgBounds pathBounds(Element path) {
        Matcher matcher = Pattern.compile(
                "[A-Za-z]|[-+]?(?:\\d*\\.\\d+|\\d+\\.?\\d*)"
                        + "(?:[eE][-+]?\\d+)?")
                .matcher(path.getAttribute("d"));
        List<String> tokens = new ArrayList<>();
        while (matcher.find()) {
            tokens.add(matcher.group());
        }

        double currentX = 0;
        double currentY = 0;
        double startX = 0;
        double startY = 0;
        double lastControlX = 0;
        double lastControlY = 0;
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        char command = 0;
        char previousCommand = 0;
        int index = 0;
        while (index < tokens.size()) {
            String token = tokens.get(index);
            if (Character.isLetter(token.charAt(0))) {
                command = token.charAt(0);
                index++;
                if (Character.toUpperCase(command) == 'Z') {
                    currentX = startX;
                    currentY = startY;
                    lastControlX = currentX;
                    lastControlY = currentY;
                    previousCommand = 'Z';
                    continue;
                }
            }
            boolean relative = Character.isLowerCase(command);
            char normalizedCommand = Character.toUpperCase(command);
            switch (normalizedCommand) {
                case 'M', 'L' -> {
                    double x = Double.parseDouble(tokens.get(index++));
                    double y = Double.parseDouble(tokens.get(index++));
                    if (relative) {
                        x += currentX;
                        y += currentY;
                    }
                    currentX = x;
                    currentY = y;
                    if (Character.toUpperCase(command) == 'M') {
                        startX = x;
                        startY = y;
                        command = relative ? 'l' : 'L';
                    }
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                    lastControlX = currentX;
                    lastControlY = currentY;
                }
                case 'C' -> {
                    double originX = currentX;
                    double originY = currentY;
                    for (int point = 0; point < 3; point++) {
                        double x = Double.parseDouble(tokens.get(index++));
                        double y = Double.parseDouble(tokens.get(index++));
                        if (relative) {
                            x += originX;
                            y += originY;
                        }
                        minX = Math.min(minX, x);
                        minY = Math.min(minY, y);
                        maxX = Math.max(maxX, x);
                        maxY = Math.max(maxY, y);
                        if (point == 1) {
                            lastControlX = x;
                            lastControlY = y;
                        }
                        if (point == 2) {
                            currentX = x;
                            currentY = y;
                        }
                    }
                }
                case 'S' -> {
                    double originX = currentX;
                    double originY = currentY;
                    double reflectedX = previousCommand == 'C'
                            || previousCommand == 'S'
                            ? currentX * 2 - lastControlX
                            : currentX;
                    double reflectedY = previousCommand == 'C'
                            || previousCommand == 'S'
                            ? currentY * 2 - lastControlY
                            : currentY;
                    minX = Math.min(minX, reflectedX);
                    minY = Math.min(minY, reflectedY);
                    maxX = Math.max(maxX, reflectedX);
                    maxY = Math.max(maxY, reflectedY);
                    double controlX = Double.parseDouble(
                            tokens.get(index++));
                    double controlY = Double.parseDouble(
                            tokens.get(index++));
                    double endX = Double.parseDouble(tokens.get(index++));
                    double endY = Double.parseDouble(tokens.get(index++));
                    if (relative) {
                        controlX += originX;
                        controlY += originY;
                        endX += originX;
                        endY += originY;
                    }
                    minX = Math.min(minX, Math.min(controlX, endX));
                    minY = Math.min(minY, Math.min(controlY, endY));
                    maxX = Math.max(maxX, Math.max(controlX, endX));
                    maxY = Math.max(maxY, Math.max(controlY, endY));
                    lastControlX = controlX;
                    lastControlY = controlY;
                    currentX = endX;
                    currentY = endY;
                }
                case 'H' -> {
                    double x = Double.parseDouble(tokens.get(index++));
                    currentX = relative ? currentX + x : x;
                    minX = Math.min(minX, currentX);
                    maxX = Math.max(maxX, currentX);
                    minY = Math.min(minY, currentY);
                    maxY = Math.max(maxY, currentY);
                    lastControlX = currentX;
                    lastControlY = currentY;
                }
                case 'V' -> {
                    double y = Double.parseDouble(tokens.get(index++));
                    currentY = relative ? currentY + y : y;
                    minX = Math.min(minX, currentX);
                    maxX = Math.max(maxX, currentX);
                    minY = Math.min(minY, currentY);
                    maxY = Math.max(maxY, currentY);
                    lastControlX = currentX;
                    lastControlY = currentY;
                }
                default -> throw new AssertionError(
                        "Unsupported fallback path command: " + command);
            }
            previousCommand = normalizedCommand;
        }
        return new SvgBounds(minX, minY, maxX, maxY);
    }

    private static SvgBounds geometryBounds(Element root) {
        List<SvgBounds> bounds = new ArrayList<>();
        NodeList geometry = root.getElementsByTagName("*");
        for (int index = 0; index < geometry.getLength(); index++) {
            if (!(geometry.item(index) instanceof Element element)) {
                continue;
            }
            switch (element.getTagName()) {
                case "path" -> bounds.add(pathBounds(element));
                case "circle" -> {
                    double x = Double.parseDouble(element.getAttribute("cx"));
                    double y = Double.parseDouble(element.getAttribute("cy"));
                    double radius = Double.parseDouble(
                            element.getAttribute("r"));
                    bounds.add(new SvgBounds(
                            x - radius, y - radius,
                            x + radius, y + radius));
                }
                case "ellipse" -> {
                    double x = Double.parseDouble(element.getAttribute("cx"));
                    double y = Double.parseDouble(element.getAttribute("cy"));
                    double radiusX = Double.parseDouble(
                            element.getAttribute("rx"));
                    double radiusY = Double.parseDouble(
                            element.getAttribute("ry"));
                    bounds.add(new SvgBounds(
                            x - radiusX, y - radiusY,
                            x + radiusX, y + radiusY));
                }
                default -> {
                }
            }
        }
        assertFalse(bounds.isEmpty());
        return bounds.stream().reduce(SvgBounds::union).orElseThrow();
    }

    private static SvgBounds paintedGeometryBounds(Element root) {
        List<SvgBounds> bounds = new ArrayList<>();
        NodeList geometry = root.getElementsByTagName("*");
        for (int index = 0; index < geometry.getLength(); index++) {
            if (!(geometry.item(index) instanceof Element element)
                    || !hasVisiblePaint(element)) {
                continue;
            }
            SvgBounds painted = switch (element.getTagName()) {
                case "path" -> pathBounds(element);
                case "circle" -> {
                    double x = Double.parseDouble(
                            element.getAttribute("cx"));
                    double y = Double.parseDouble(
                            element.getAttribute("cy"));
                    double radius = Double.parseDouble(
                            element.getAttribute("r"));
                    yield new SvgBounds(
                            x - radius, y - radius,
                            x + radius, y + radius);
                }
                case "ellipse" -> {
                    double x = Double.parseDouble(
                            element.getAttribute("cx"));
                    double y = Double.parseDouble(
                            element.getAttribute("cy"));
                    double radiusX = Double.parseDouble(
                            element.getAttribute("rx"));
                    double radiusY = Double.parseDouble(
                            element.getAttribute("ry"));
                    yield new SvgBounds(
                            x - radiusX, y - radiusY,
                            x + radiusX, y + radiusY);
                }
                default -> null;
            };
            if (painted == null) {
                continue;
            }

            String stroke = inheritedAttribute(element, "stroke", "none");
            if (isVisiblePaintValue(stroke)) {
                double strokeWidth = Double.parseDouble(
                        inheritedAttribute(element, "stroke-width", "1"));
                painted = painted.expand(strokeWidth / 2);
            }
            painted = includeInheritedDropShadow(element, root, painted);
            bounds.add(painted);
        }
        assertFalse(bounds.isEmpty());
        return bounds.stream().reduce(SvgBounds::union).orElseThrow();
    }

    private static SvgBounds includeInheritedDropShadow(
            Element element,
            Element boundary,
            SvgBounds painted) {
        Node current = element;
        SvgBounds result = painted;
        while (current instanceof Element currentElement) {
            String filterValue = currentElement.getAttribute("filter");
            Matcher reference = Pattern.compile(
                    "url\\(\\s*#([^)\\s]+)\\s*\\)")
                    .matcher(filterValue);
            if (reference.matches()) {
                Element filter = elementWithId(
                        element.getOwnerDocument().getDocumentElement(),
                        reference.group(1));
                if (filter != null) {
                    List<Element> shadows = elementsWithTag(
                            filter, "feDropShadow");
                    for (Element shadow : shadows) {
                        double dx = numericAttribute(shadow, "dx", 0);
                        double dy = numericAttribute(shadow, "dy", 0);
                        double blur = numericAttribute(
                                shadow, "stdDeviation", 0) * 3;
                        result = result.union(
                                painted.expand(blur).translate(dx, dy));
                    }
                }
            }
            if (currentElement == boundary) {
                break;
            }
            current = currentElement.getParentNode();
        }
        return result;
    }

    private static boolean hasVisiblePaint(Element element) {
        if (!List.of("path", "circle", "ellipse")
                .contains(element.getTagName())) {
            return false;
        }
        if (effectiveOpacity(element) < .08) {
            return false;
        }
        String fill = inheritedAttribute(element, "fill", "black");
        String stroke = inheritedAttribute(element, "stroke", "none");
        boolean visibleFill = isVisiblePaintValue(fill);
        boolean visibleStroke = isVisiblePaintValue(stroke)
                && Double.parseDouble(
                        inheritedAttribute(element, "stroke-width", "1")) > 0;
        return visibleFill || visibleStroke;
    }

    private static boolean isVisiblePaintValue(String value) {
        String normalized = value.strip().toLowerCase();
        return !normalized.isBlank()
                && !"none".equals(normalized)
                && !"transparent".equals(normalized);
    }

    private static double effectiveOpacity(Element element) {
        double result = 1;
        Node current = element;
        while (current instanceof Element currentElement) {
            result *= opacity(currentElement, 1);
            current = currentElement.getParentNode();
        }
        return result;
    }

    private static String inheritedAttribute(
            Element element,
            String attribute,
            String fallback) {
        Node current = element;
        while (current instanceof Element currentElement) {
            String value = currentElement.getAttribute(attribute);
            if (!value.isBlank()) {
                return value;
            }
            current = currentElement.getParentNode();
        }
        return fallback;
    }

    private static String silhouetteSignature(Element path) {
        SvgBounds bounds = pathBounds(path);
        String commands = path.getAttribute("d")
                .replaceAll("[^A-Za-z]", "")
                .toUpperCase();
        long numbers = Pattern.compile(
                "[-+]?(?:\\d*\\.\\d+|\\d+\\.?\\d*)"
                        + "(?:[eE][-+]?\\d+)?")
                .matcher(path.getAttribute("d"))
                .results()
                .count();
        long aspectTier = Math.round(
                bounds.width() / Math.max(bounds.height(), .001) * 8);
        return commands + "|" + numbers + "|" + aspectTier;
    }

    private static double numericAttribute(
            Element element,
            String attribute,
            double fallback) {
        String value = element.getAttribute(attribute);
        return value.isBlank() ? fallback : Double.parseDouble(value);
    }

    private static long sizeTier(SvgBounds bounds) {
        return Math.round(Math.log(bounds.area()) / Math.log(1.65));
    }

    private static boolean hasMirroredPeer(
            SvgBounds source,
            List<SvgBounds> candidates,
            double viewBoxWidth) {
        return candidates.stream().anyMatch(candidate -> {
            if (candidate == source) {
                return false;
            }
            double areaRatio = candidate.area() / source.area();
            return Math.abs(candidate.centerX() + source.centerX()
                    - viewBoxWidth) <= 12
                    && Math.abs(candidate.centerY() - source.centerY()) <= 12
                    && areaRatio >= .65
                    && areaRatio <= 1.55;
        });
    }

    private static double opacity(Element element, double fallback) {
        String value = element.getAttribute("opacity");
        return value.isBlank() ? fallback : Double.parseDouble(value);
    }

    private static String inheritedAttribute(
            Element child,
            Element parent,
            String attribute) {
        String value = child.getAttribute(attribute);
        return value.isBlank() ? parent.getAttribute(attribute) : value;
    }

    private static int parseHexColor(String value) {
        String normalized = value.strip();
        assertTrue(normalized.matches("#[0-9a-fA-F]{6}"));
        return Integer.parseInt(normalized.substring(1), 16);
    }

    private static double contrastRatio(int first, int second) {
        double firstLuminance = relativeLuminance(first);
        double secondLuminance = relativeLuminance(second);
        return (Math.max(firstLuminance, secondLuminance) + .05)
                / (Math.min(firstLuminance, secondLuminance) + .05);
    }

    private static double relativeLuminance(int color) {
        double red = linearChannel((color >> 16) & 0xff);
        double green = linearChannel((color >> 8) & 0xff);
        double blue = linearChannel(color & 0xff);
        return .2126 * red + .7152 * green + .0722 * blue;
    }

    private static double linearChannel(int channel) {
        double value = channel / 255d;
        return value <= .04045
                ? value / 12.92
                : Math.pow((value + .055) / 1.055, 2.4);
    }

    private static SvgTransform svgTransform(String value) {
        Matcher translate = Pattern.compile(
                "translate\\(\\s*([-+.\\d]+)[ ,]+([-+.\\d]+)\\s*\\)")
                .matcher(value);
        Matcher scale = Pattern.compile(
                "scale\\(\\s*([-+.\\d]+)\\s*\\)")
                .matcher(value);
        assertTrue(translate.find());
        assertTrue(scale.find());
        return new SvgTransform(
                Double.parseDouble(translate.group(1)),
                Double.parseDouble(translate.group(2)),
                Double.parseDouble(scale.group(1)));
    }

    private static String cssMediaRule(
            String css,
            String mediaQuery,
            String selector) {
        String marker = "@media " + mediaQuery;
        int markerIndex = css.indexOf(marker);
        assertTrue(markerIndex >= 0, () -> "Missing media query: " + mediaQuery);
        int open = css.indexOf('{', markerIndex + marker.length());
        assertTrue(open >= 0);
        int close = matchingBrace(css, open);
        return cssRule(css.substring(open + 1, close), selector);
    }

    private static int matchingBrace(String source, int open) {
        int depth = 0;
        for (int index = open; index < source.length(); index++) {
            if (source.charAt(index) == '{') {
                depth++;
            } else if (source.charAt(index) == '}') {
                depth--;
                if (depth == 0) {
                    return index;
                }
            }
        }
        throw new AssertionError("Unclosed CSS block");
    }

    private static double leadingVh(String value) {
        Matcher matcher = Pattern.compile(
                "max\\(\\s*([0-9]+(?:\\.[0-9]+)?)vh\\s*,")
                .matcher(value);
        assertTrue(matcher.find(), () -> "Missing vh safe inset: " + value);
        return Double.parseDouble(matcher.group(1));
    }

    private record SvgBounds(
            double minX,
            double minY,
            double maxX,
            double maxY) {
        double width() {
            return maxX - minX;
        }

        double height() {
            return maxY - minY;
        }

        double area() {
            return width() * height();
        }

        double centerX() {
            return (minX + maxX) / 2;
        }

        double centerY() {
            return (minY + maxY) / 2;
        }

        SvgBounds union(SvgBounds other) {
            return new SvgBounds(
                    Math.min(minX, other.minX),
                    Math.min(minY, other.minY),
                    Math.max(maxX, other.maxX),
                    Math.max(maxY, other.maxY));
        }

        SvgBounds expand(double amount) {
            return new SvgBounds(
                    minX - amount,
                    minY - amount,
                    maxX + amount,
                    maxY + amount);
        }

        SvgBounds translate(double x, double y) {
            return new SvgBounds(
                    minX + x,
                    minY + y,
                    maxX + x,
                    maxY + y);
        }
    }

    private record SvgTransform(
            double translateX,
            double translateY,
            double scale) {
        SvgBounds apply(SvgBounds source) {
            return new SvgBounds(
                    source.minX() * scale + translateX,
                    source.minY() * scale + translateY,
                    source.maxX() * scale + translateX,
                    source.maxY() * scale + translateY);
        }
    }

    private record ResponsiveActionCase(
            int width,
            int height,
            String mediaQuery,
            double bottomVh,
            int actionHeight) {
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
