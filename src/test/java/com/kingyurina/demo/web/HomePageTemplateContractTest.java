package com.kingyurina.demo.web;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class HomePageTemplateContractTest {

    @Test
    void homepageDeclaresCinematicSceneAndSemanticProductContent() throws IOException {
        String template = resource("templates/index.html");

        assertTrue(template.contains("home-cinematic.css"));
        assertTrue(template.contains("data-cinematic-lab"));
        assertTrue(template.contains("data-cinematic-progress"));
        assertTrue(template.contains("Signal"));
        assertTrue(template.contains("20D Alpha"));
        assertTrue(template.contains("Upside"));
        assertTrue(template.contains("Risk"));
        assertTrue(template.contains("Explore the Intelligence"));
        assertTrue(template.contains("th:href=\"@{/quant}\""));
        assertTrue(template.contains("home-cinematic-3d.js"));
        assertFalse(template.contains("king-yurina-reference-hero.png"));
        assertFalse(template.contains("home-nature-3d.js"));
    }

    @Test
    void homepageAssetsStayLocalAndDeclareFallbacks() throws IOException {
        String css = resource("static/css/home-cinematic.css");
        String entry = resource("static/js/home-cinematic-3d.js");
        String quality = resource("static/js/home-cinematic-quality.js");

        assertTrue(css.contains("@media (prefers-reduced-motion: reduce)"));
        assertTrue(css.contains(".cinematic-home.is-fallback"));
        assertTrue(css.contains("cinematic-laboratory-bg.png"));
        assertTrue(new ClassPathResource("static/images/cinematic-laboratory-bg.png").exists());
        assertTrue(entry.contains("/js/vendor/three.module.js"));
        assertTrue(entry.contains("startCanvasFallback"));
        assertTrue(entry.contains("visibilitychange"));
        assertTrue(entry.contains("requestAnimationFrame"));
        assertTrue(quality.contains("selectQualityProfile"));
        assertFalse(entry.contains("https://"));
        assertFalse(entry.contains("fetch("));
    }

    @Test
    void sceneIsSplitIntoFocusedModules() throws IOException {
        String entry = resource("static/js/home-cinematic-3d.js");
        String lab = resource("static/js/home-cinematic-lab.js");
        String engine = resource("static/js/home-cinematic-engine.js");
        String particles = resource("static/js/home-cinematic-particles.js");

        assertTrue(entry.contains("createLaboratory"));
        assertTrue(entry.contains("createQuantEngine"));
        assertTrue(entry.contains("createDataVortex"));
        assertTrue(lab.contains("export function createLaboratory"));
        assertTrue(engine.contains("export function createQuantEngine"));
        assertTrue(particles.contains("export function createDataVortex"));
    }

    @Test
    void engineUsesLayeredHousingInsteadOfExposedBlockoutGeometry() throws IOException {
        String entry = resource("static/js/home-cinematic-3d.js");
        String engine = resource("static/js/home-cinematic-engine.js");

        assertTrue(engine.contains("addTurbineHousing"));
        assertTrue(engine.contains("addInternalRotor"));
        assertTrue(engine.contains("addObservationLens"));
        assertFalse(engine.contains("addStruts"));
        assertFalse(engine.contains("IcosahedronGeometry"));
        assertTrue(entry.contains("CAMERA_END_Z"));
    }

    @Test
    void studioEnvironmentUsesSupportedPmremBlur() throws IOException {
        String entry = resource("static/js/home-cinematic-3d.js");

        assertTrue(entry.contains("fromScene(studio, 0.04)"));
        assertFalse(entry.contains("fromScene(studio, 0.08)"));
    }

    @Test
    void handoffWaitsUntilMetricFadeCompletes() throws IOException {
        String css = resource("static/css/home-cinematic.css");

        assertTrue(css.contains("transition-delay: 280ms;"));
    }

    @Test
    void reducedMotionHandoffResetsResponsiveAnchors() throws IOException {
        String css = resource("static/css/home-cinematic.css");
        String reducedMotion = css.substring(css.indexOf("@media (prefers-reduced-motion: reduce)"));

        assertTrue(reducedMotion.contains("right: auto;"));
        assertTrue(reducedMotion.contains("bottom: auto;"));
        assertTrue(reducedMotion.contains("left: 50%;"));
        assertTrue(reducedMotion.contains("width: min(900px, calc(100% - 36px));"));
    }

    private static String resource(String path) throws IOException {
        ClassPathResource resource = new ClassPathResource(path);
        return resource.exists() ? resource.getContentAsString(StandardCharsets.UTF_8) : "";
    }
}
