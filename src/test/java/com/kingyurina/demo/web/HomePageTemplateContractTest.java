package com.kingyurina.demo.web;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

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
        assertTrue(css.contains(".aurora-story { min-height: 280svh; }"));
        assertTrue(css.contains("@media (max-width: 900px) {\n    .aurora-story { min-height: 220svh; }\n}"));
        assertTrue(css.contains("@media (prefers-reduced-motion: reduce) {\n    .aurora-story { min-height: 100svh; }\n}"));
        assertTrue(css.contains(".aurora-skip-link,\n.aurora-brand,\n.aurora-nav nav a,\n.aurora-dashboard,\n.aurora-primary-action {"));
        assertTrue(css.contains("min-height: 44px;"));
        assertTrue(css.contains(".aurora-skip-link:focus-visible,\n.aurora-brand:focus-visible,\n.aurora-nav nav a:focus-visible,\n.aurora-dashboard:focus-visible,\n.aurora-primary-action:focus-visible {"));
        assertTrue(css.contains("outline: 2px solid #f7faff;"));
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

    private static String resource(String path) throws IOException {
        ClassPathResource resource = new ClassPathResource(path);
        return resource.exists() ? resource.getContentAsString(StandardCharsets.UTF_8) : "";
    }
}
