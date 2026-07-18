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

        assertTrue(template.contains("home-aurora.css"));
        assertTrue(template.contains("data-aurora-core"));
        assertTrue(template.contains("data-aurora-canvas"));
        assertTrue(template.contains("data-aurora-progress"));
        assertTrue(template.contains("data-aurora-metric"));
        assertTrue(template.contains("QUANT INTELLIGENCE / AURORA CORE ONLINE"));
        assertTrue(template.contains("See the signal beneath the noise."));
        assertTrue(template.contains("Signal, alpha, upside and risk?봱esolved into one adaptive market view."));
        assertTrue(template.contains("Market intelligence, resolved."));
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
        assertFalse(template.contains("home-cinematic"));
        assertFalse(template.contains("cinematic-laboratory-bg.png"));
        assertFalse(template.contains("fonts.googleapis.com"));
        assertFalse(template.contains("fonts.gstatic.com"));
    }

    private static String resource(String path) throws IOException {
        ClassPathResource resource = new ClassPathResource(path);
        return resource.exists() ? resource.getContentAsString(StandardCharsets.UTF_8) : "";
    }
}
