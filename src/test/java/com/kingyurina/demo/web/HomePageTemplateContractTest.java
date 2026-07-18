package com.kingyurina.demo.web;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class HomePageTemplateContractTest {

    @Test
    void homepageDeclaresAuroraCoreAndSemanticProductContent() throws IOException {
        String template = resource("templates/index.html");
        String css = resource("static/css/home-aurora.css");

        assertTrue(template.contains("home-aurora.css"));
        assertTrue(template.contains("data-aurora-core"));
        assertTrue(template.contains("data-aurora-canvas"));
        assertTrue(template.contains("data-aurora-progress"));
        assertTrue(template.contains("data-aurora-metric"));
        assertTrue(template.contains("QUANT INTELLIGENCE / AURORA CORE ONLINE"));
        assertTrue(template.contains("See the signal beneath the noise."));
        assertTrue(template.contains("risk&mdash;resolved"));
        assertTrue(template.contains("Market intelligence, resolved."));
        assertTrue(template.contains("Illustrative model outputs &middot; Not live market data"));
        assertTrue(template.contains("Enter Quant Intelligence"));
        assertTrue(template.contains("Scroll to resolve"));
        assertTrue(template.contains("Signal"));
        assertTrue(template.contains("20D Alpha"));
        assertTrue(template.contains("Upside"));
        assertTrue(template.contains("Risk"));
        assertTrue(template.contains("th:each=\"menu : ${mainMenus}\""));
        assertTrue(template.contains("th:href=\"@{/quant}\""));
        assertTrue(template.contains("home-aurora.js"));
        assertTrue(new ClassPathResource("static/css/home-aurora.css").exists());
        assertTrue(new ClassPathResource("static/js/home-aurora.js").exists());
        assertTrue(css.contains(".aurora-story.is-ready { min-height: 280svh; }"));
        assertTrue(css.contains("@media (max-width: 900px)"));
        assertTrue(css.contains(".aurora-story.is-ready { min-height: 220svh; }"));
        assertTrue(css.contains("@media (prefers-reduced-motion: reduce)"));
        assertTrue(css.contains("min-height: 100svh"));
        assertTrue(css.contains(".aurora-skip-link {"));
        assertTrue(css.contains(".aurora-brand {"));
        assertTrue(css.contains(".aurora-nav nav a,\n.aurora-dashboard {"));
        assertTrue(css.contains(".aurora-primary-action {"));
        assertTrue(css.contains("min-height: 44px;"));
        assertTrue(css.contains("min-height: 52px;"));
        assertTrue(css.contains(".aurora-home :focus-visible"));
        assertTrue(css.contains("outline: 2px solid #b9eaff;"));
        assertFalse(template.contains("home-cinematic"));
        assertFalse(template.contains("cinematic-laboratory-bg.png"));
        assertFalse(template.contains("fonts.googleapis.com"));
        assertFalse(template.contains("fonts.gstatic.com"));
    }

    @Test
    void auroraFieldUsesOneLocalShaderPlaneAndRequiredUniforms() throws IOException {
        String field = resource("static/js/home-aurora-field.js");

        assertTrue(field.contains("export function createAuroraField"));
        assertTrue(field.contains("new THREE.PlaneGeometry(2, 2)"));
        assertTrue(field.contains("new THREE.ShaderMaterial"));
        assertTrue(field.contains("transparent: true"));
        assertTrue(field.contains("uTime"));
        assertTrue(field.contains("uResolution"));
        assertTrue(field.contains("uPointer"));
        assertTrue(field.contains("uPointerEnergy"));
        assertTrue(field.contains("uScroll"));
        assertTrue(field.contains("uQuality"));
        assertTrue(field.contains("fbm"));
        assertTrue(field.contains("auroraBand"));
        assertTrue(field.contains("grain"));
        assertTrue(field.contains("float time = uTime;"));
        assertTrue(field.contains("float motionAmplitude = mix(1.0, 0.35, convergence);"));
        assertTrue(field.contains("(warp - 0.5) * motionAmplitude"));
        assertFalse(field.contains("uTime * mix"));
        assertTrue(field.contains("let phaseTime = 0;"));
        assertTrue(field.contains("let lastElapsedTime = null;"));
        assertTrue(field.contains("phaseTime = time;"));
        assertTrue(field.contains("Math.min(Math.max(time - lastElapsedTime, 0), 0.1)"));
        assertTrue(field.contains("const convergence = scrollPhase * scrollPhase * (3 - 2 * scrollPhase);"));
        assertTrue(field.contains("const phaseSpeed = 1 - convergence * 0.65;"));
        assertTrue(field.contains("phaseTime += deltaTime * phaseSpeed;"));
        assertTrue(field.contains("uniforms.uTime.value = phaseTime;"));
        assertFalse(field.contains("uniforms.uTime.value = time;"));
        assertFalse(field.contains("TorusGeometry"));
        assertFalse(field.contains("PointsMaterial"));
        assertFalse(field.contains("https://"));
        assertFalse(field.contains("fetch("));
    }

    @Test
    void auroraLifecycleStaysLocalAdaptiveAndAllocationConscious() throws IOException {
        String entry = resource("static/js/home-aurora.js");

        assertTrue(entry.contains("/js/vendor/three.module.js"));
        assertTrue(entry.contains("selectAuroraQuality"));
        assertTrue(entry.contains("createAuroraField"));
        assertTrue(entry.contains("createSpring"));
        assertTrue(entry.contains("normalizedScrollProgress"));
        assertTrue(entry.contains("requestAnimationFrame"));
        assertTrue(entry.contains("IntersectionObserver"));
        assertTrue(entry.contains("visibilitychange"));
        assertTrue(entry.contains("pointermove"));
        assertTrue(entry.contains("pointerleave"));
        assertTrue(entry.contains("--aurora-progress"));
        assertTrue(entry.contains("is-webgl-fallback"));
        assertTrue(entry.contains("alpha: true"));
        assertTrue(entry.contains("renderer.setClearColor(0x02040b, 0)"));
        assertTrue(entry.contains("cancelAnimationFrame"));
        assertTrue(entry.contains("state.intersecting"));
        assertTrue(entry.contains("POINTER_ENERGY_SPRING"));
        assertTrue(entry.contains("SCROLL_SPRING"));
        assertTrue(entry.contains(
            "const metrics = Array.from(root.querySelectorAll(\"[data-aurora-metric]\"))"
        ));
        assertFalse(entry.contains("https://"));
        assertFalse(entry.contains("fetch("));
    }

    @Test
    void auroraObserverUsesTheLatestTransitionForItsSingleRoot() throws IOException {
        String entry = resource("static/js/home-aurora.js");

        assertTrue(entry.contains("const latestEntry = entries[entries.length - 1]"));
        assertTrue(entry.contains("if (!latestEntry)"));
        assertTrue(entry.contains("state.intersecting = latestEntry.isIntersecting"));
        assertFalse(entry.contains("entries.some("));
    }

    @Test
    void auroraResizeRefreshesScrollBeforeSchedulingAFrame() throws IOException {
        String entry = resource("static/js/home-aurora.js");

        assertTrue(entry.contains(
            "window.addEventListener(\"resize\", () => {\n"
                + "        const resized = resize();\n"
                + "        readScroll();"
        ));
        assertTrue(entry.contains(
            "        }\n"
                + "        ensureFrameLoop();\n"
                + "    }, { passive: true });"
        ));
    }

    @Test
    void reducedMotionRendersOnceUntilARealResize() throws IOException {
        String entry = resource("static/js/home-aurora.js");

        assertTrue(entry.contains("let staticFrameRendered = false"));
        assertTrue(entry.contains(
            "quality.name === \"reduced\" && staticFrameRendered"
        ));
        assertTrue(entry.contains(
            "if (resized && quality.name === \"reduced\") {\n"
                + "            staticFrameRendered = false;\n"
                + "        }"
        ));
        assertTrue(entry.contains(
            "if (quality.name === \"reduced\") {\n"
                + "            staticFrameRendered = true;\n"
                + "            stopFrameLoop();"
        ));
    }

    @Test
    void auroraStylesDefineResponsiveFallbackAndAccessibleMotionStates() throws IOException {
        String css = resource("static/css/home-aurora.css");

        assertTrue(css.contains("--aurora-void: #02040b"));
        assertTrue(css.contains("min-height: 280svh"));
        assertTrue(css.contains("min-height: 44px"));
        assertTrue(css.contains(".aurora-home.is-webgl-fallback .aurora-canvas"));
        assertTrue(css.contains("@media (max-width: 900px)"));
        assertTrue(css.contains("min-height: 220svh"));
        assertTrue(css.contains("@media (prefers-reduced-motion: reduce)"));
        assertTrue(css.contains("min-height: 100svh"));
        assertTrue(css.contains(":focus-visible"));
        assertTrue(css.contains("backdrop-filter: blur(22px)"));
        assertTrue(css.contains(".aurora-home.is-webgl-fallback .aurora-metric-rail"));
        assertTrue(css.contains(".aurora-atmosphere::after"));
        assertFalse(css.contains("cinematic-laboratory-bg.png"));
    }

    @Test
    void auroraStylesProgressivelyEnhanceFromAResolvedStaticBase() throws IOException {
        String css = resource("static/css/home-aurora.css");

        assertTrue(css.contains(
            ".aurora-story { position: relative; min-height: 100svh; background: var(--aurora-void); }"
        ));
        assertTrue(css.contains(".aurora-story.is-ready { min-height: 280svh; }"));
        assertFalse(css.contains(
            ".aurora-story { position: relative; min-height: 280svh;"
        ));
        assertTrue(css.contains(
            ".aurora-summary { opacity: 0; transform: translateY(-8px); }"
        ));
        assertTrue(css.contains(".aurora-resolved-copy { opacity: 1; transform: none; }"));
        assertTrue(css.contains(
            ".aurora-story.is-ready:not(.is-resolved) .aurora-summary"
        ));
        assertTrue(css.contains(
            ".aurora-story.is-ready:not(.is-resolved) .aurora-resolved-copy"
        ));
        assertTrue(css.contains(
            "    opacity: 1;\n"
                + "    transform: none;\n"
                + "    transition: opacity 420ms"
        ));
        assertTrue(css.contains(
            "    padding: 16px 20px;\n"
                + "    opacity: 1;\n"
                + "    transform: none;"
        ));
        assertTrue(css.contains(
            ".aurora-story.is-ready .aurora-metric-rail { opacity: 0;"
        ));
        assertTrue(css.contains(
            ".aurora-story.is-ready.has-metrics .aurora-metric-rail"
        ));
        assertTrue(css.contains(
            ".aurora-story.is-ready .aurora-metric-rail li { opacity: 0;"
        ));
        assertTrue(css.contains(
            ".aurora-story.is-ready .aurora-metric-rail li.is-visible"
        ));
        assertTrue(css.contains(".aurora-scroll-cue { display: none;"));
        assertTrue(css.contains(".aurora-progress { display: none;"));
        assertTrue(css.contains(
            ".aurora-story.is-ready .aurora-scroll-cue { display: grid; }"
        ));
        assertTrue(css.contains(
            ".aurora-story.is-ready .aurora-progress { display: block; }"
        ));
        assertTrue(css.contains(
            ".aurora-home.is-webgl-fallback .aurora-story { min-height: 100svh; }"
        ));
        assertTrue(css.contains(
            ".aurora-home.is-webgl-fallback .aurora-scroll-cue,\n"
                + ".aurora-home.is-webgl-fallback .aurora-progress { display: none; }"
        ));
    }

    @Test
    void auroraStylesAdaptGlassAndCompactShortDesktopLayouts() throws IOException {
        String css = resource("static/css/home-aurora.css");

        assertTrue(css.contains("-webkit-backdrop-filter: blur(22px)"));
        assertTrue(css.contains(
            "@supports not ((backdrop-filter: blur(1px)) or "
                + "(-webkit-backdrop-filter: blur(1px)))"
        ));
        assertTrue(css.contains(
            "@media (max-width: 900px) {\n"
                + "    .aurora-story.is-ready { min-height: 220svh; }"
        ));
        assertTrue(css.contains(
            ".aurora-nav,\n"
                + "    .aurora-metric-rail {\n"
                + "        -webkit-backdrop-filter: blur(10px);\n"
                + "        backdrop-filter: blur(10px);"
        ));
        assertTrue(css.contains(
            "        -webkit-backdrop-filter: blur(12px);\n"
                + "        backdrop-filter: blur(12px);"
        ));
        assertTrue(css.contains(".aurora-atmosphere::after { opacity: .07; }"));
        assertTrue(css.contains("min-width: 0;"));
        assertTrue(css.contains("scroll-padding-inline: 8px;"));
        assertTrue(css.contains(
            "@media (min-width: 901px) and (max-height: 820px)"
        ));
        assertTrue(css.contains(".aurora-panel { padding: 28px 44px;"));
        assertTrue(css.contains(".aurora-copy-stage { min-height: 48px;"));
        assertTrue(css.contains(".aurora-metric-rail li { padding: 10px 18px; }"));
    }

    @Test
    void auroraStylesKeepSmallTextReadableAndStopReducedMotionInteractions() throws IOException {
        String css = resource("static/css/home-aurora.css");

        assertTrue(css.contains("font-size: .72rem;"));
        assertTrue(css.contains("font-size: .68rem;"));
        assertFalse(css.contains("font-size: .57rem;"));
        assertFalse(css.contains("font-size: .58rem;"));
        assertTrue(css.contains(
            ".aurora-nav nav a,\n"
                + "    .aurora-dashboard,\n"
                + "    .aurora-primary-action {\n"
                + "        animation: none !important;\n"
                + "        transition: none !important;\n"
                + "        transform: none !important;"
        ));
    }

    @Test
    void auroraNavigationDefinesPressedStatesAndReducedMotionSafety() throws IOException {
        String css = resource("static/css/home-aurora.css");

        assertTrue(css.contains(".aurora-brand:active {"));
        assertTrue(css.contains(".aurora-nav nav a:active {"));
        assertTrue(css.contains(".aurora-dashboard:active {"));
        assertTrue(css.contains(
            "    .aurora-brand,\n"
                + "    .aurora-nav nav a,\n"
                + "    .aurora-dashboard,\n"
                + "    .aurora-primary-action {\n"
                + "        animation: none !important;\n"
                + "        transition: none !important;\n"
                + "        transform: none !important;"
        ));
    }

    @Test
    void auroraCopySemanticsFollowResolvedStateTransitions() throws IOException {
        String template = resource("templates/index.html");
        String entry = resource("static/js/home-aurora.js");

        assertTrue(template.contains(
            "<div class=\"aurora-story is-resolved\" data-aurora-core>"
        ));
        assertTrue(template.contains(
            "<p class=\"aurora-summary\" aria-hidden=\"true\">"
        ));
        assertTrue(template.contains(
            "<p class=\"aurora-resolved-copy\">Market intelligence, resolved.</p>"
        ));
        assertFalse(template.contains(
            "class=\"aurora-resolved-copy\" aria-hidden=\"true\""
        ));
        assertTrue(entry.contains(
            "const summaryCopy = root.querySelector(\".aurora-summary\")"
        ));
        assertTrue(entry.contains(
            "const resolvedCopy = root.querySelector(\".aurora-resolved-copy\")"
        ));
        assertTrue(entry.contains("let resolvedState = true;"));
        assertTrue(entry.contains("const nextResolvedState = progress >= 0.74;"));
        assertTrue(entry.contains("if (nextResolvedState !== resolvedState)"));
        assertTrue(entry.contains(
            "root.classList.toggle(\"is-resolved\", nextResolvedState)"
        ));
        assertTrue(entry.contains(
            "summaryCopy.setAttribute(\"aria-hidden\", String(nextResolvedState))"
        ));
        assertTrue(entry.contains(
            "resolvedCopy.setAttribute(\"aria-hidden\", String(!nextResolvedState))"
        ));
        assertTrue(entry.contains("resolvedState = nextResolvedState;"));
        assertTrue(entry.contains(
            "stage.classList.add(\"is-ready\", \"has-metrics\", \"is-resolved\")"
        ));
    }

    @Test
    void homepageNoLongerLoadsTheRejectedLaboratoryRuntime() throws IOException {
        String template = resource("templates/index.html");
        String entry = resource("static/js/home-aurora.js");

        assertFalse(template.contains("home-cinematic.css"));
        assertFalse(template.contains("home-cinematic-3d.js"));
        assertFalse(entry.contains("createLaboratory"));
        assertFalse(entry.contains("createQuantEngine"));
        assertFalse(entry.contains("createDataVortex"));
        assertFalse(entry.contains("cinematic-laboratory-bg.png"));
        assertFalse(Files.exists(Path.of("src/main/resources/static/css/home-cinematic.css")));
        assertFalse(Files.exists(Path.of("src/main/resources/static/js/home-cinematic-3d.js")));
        assertFalse(Files.exists(Path.of("src/main/resources/static/js/home-cinematic-engine.js")));
        assertFalse(Files.exists(Path.of("src/main/resources/static/js/home-cinematic-lab.js")));
        assertFalse(Files.exists(Path.of("src/main/resources/static/js/home-cinematic-particles.js")));
        assertFalse(Files.exists(Path.of("src/main/resources/static/js/home-cinematic-quality.js")));
        assertFalse(Files.exists(Path.of("src/main/resources/static/images/cinematic-laboratory-bg.png")));
    }

    private static String resource(String path) throws IOException {
        ClassPathResource resource = new ClassPathResource(path);
        return resource.exists() ? resource.getContentAsString(StandardCharsets.UTF_8) : "";
    }
}
