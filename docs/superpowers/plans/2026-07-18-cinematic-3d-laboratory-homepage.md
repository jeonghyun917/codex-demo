# Cinematic 3D Laboratory Homepage Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the King Yurina landing page with a scroll-directed, full real-time 3D cinematic laboratory containing a physical Quant engine and accessible Quant metric overlays.

**Architecture:** Keep Spring Boot and Thymeleaf unchanged while isolating the landing page from the shared 5,000-line stylesheet. A semantic HTML overlay remains functional without WebGL, while small native ES modules build and animate the laboratory, engine, and data vortex from one normalized scroll timeline. Capability-based quality selection and a canvas fallback protect mobile, reduced-motion, and WebGL-constrained devices.

**Tech Stack:** Java 21, Spring Boot 4, Thymeleaf, JUnit 5, native ES modules, local Three.js, HTML5 Canvas, CSS

## Global Constraints

- Change only the `/` landing page; do not redesign `/quant` or other product routes.
- Keep the existing Spring Boot, Thymeleaf, Spring Security, MariaDB, and MyBatis architecture.
- Do not add React, shadcn, npm, a frontend bundler, background video, or external API calls.
- Continue loading Three.js only from `/js/vendor/three.module.js`.
- Keep page-request rendering DB-backed through the existing `MenuService`.
- Treat Signal `79 / 100`, 20D Alpha `+2.4%`, Upside `58%`, and Risk `Medium` as presentation examples.
- Keep navigation, metrics, fallback copy, and the `/quant` action as semantic HTML.
- Respect `prefers-reduced-motion`, support WebGL failure, and avoid trapping scroll.
- Cap device pixel ratio and avoid per-frame object allocation in the render loop.
- Preserve unrelated tracked and untracked user files.
- Run Maven through `mvn.cmd "-Dmaven.repo.local=.m2/repository" test`.

---

## File Structure

- `src/main/resources/templates/index.html`: semantic landing-page overlay, metric copy, fallback content, and module entrypoint.
- `src/main/resources/static/css/home-cinematic.css`: landing-page-only layout, cinematic overlay styling, responsive behavior, loading/fallback states, and reduced-motion rules.
- `src/main/resources/static/js/home-cinematic-quality.js`: pure capability and quality configuration selection.
- `src/main/resources/static/js/home-cinematic-lab.js`: physical laboratory shell, structural foreground, practical lights, and depth layers.
- `src/main/resources/static/js/home-cinematic-engine.js`: manufactured metal-and-glass Quant engine and its activation timeline.
- `src/main/resources/static/js/home-cinematic-particles.js`: contained orbital particles and scroll-accelerated data vortex.
- `src/main/resources/static/js/home-cinematic-3d.js`: renderer lifecycle, camera timeline, input, visibility, adaptive quality, and fallback orchestration.
- `src/test/java/com/kingyurina/demo/web/HomePageTemplateContractTest.java`: executable contract for semantic content, local assets, fallbacks, and dependency boundaries.

---

### Task 1: Lock the Landing-page Contract with a Failing Test

**Files:**
- Create: `src/test/java/com/kingyurina/demo/web/HomePageTemplateContractTest.java`

**Interfaces:**
- Consumes: classpath resources under `templates/` and `static/`.
- Produces: resource contract tests that every later task must satisfy.

- [ ] **Step 1: Write the failing contract test**

```java
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

    private static String resource(String path) throws IOException {
        return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run:

```powershell
mvn.cmd "-Dmaven.repo.local=.m2/repository" -Dtest=HomePageTemplateContractTest test
```

Expected: FAIL because `home-cinematic.css` and the cinematic JavaScript modules do not exist and the old template still references `home-nature-3d.js`.

- [ ] **Step 3: Commit the failing test**

```powershell
git add src/test/java/com/kingyurina/demo/web/HomePageTemplateContractTest.java
git commit -m "test: define cinematic homepage contract"
```

---

### Task 2: Build the Semantic Cinematic Overlay

**Files:**
- Modify: `src/main/resources/templates/index.html`
- Create: `src/main/resources/static/css/home-cinematic.css`
- Test: `src/test/java/com/kingyurina/demo/web/HomePageTemplateContractTest.java`

**Interfaces:**
- Consumes: `${mainMenus}` from `HomeController`, existing `/quant` route.
- Produces: `[data-cinematic-lab]`, `[data-cinematic-canvas]`, `[data-cinematic-progress]`, and `[data-scene-phase]` hooks for the renderer.

- [ ] **Step 1: Replace the homepage template with semantic scene markup**

Use this structure in `index.html`:

```html
<!doctype html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta name="theme-color" content="#03050b">
    <title>King Yurina | Quant Intelligence</title>
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&family=Playfair+Display:wght@500;600&display=swap" rel="stylesheet">
    <link rel="stylesheet" th:href="@{/css/home-cinematic.css(v='20260718')}" href="../static/css/home-cinematic.css?v=20260718">
</head>
<body class="cinematic-home">
<div class="cinematic-scroll" data-cinematic-lab>
    <div class="cinematic-viewport">
        <canvas class="cinematic-canvas" data-cinematic-canvas aria-hidden="true"></canvas>
        <div class="cinematic-fallback" aria-hidden="true"></div>
        <div class="cinematic-vignette" aria-hidden="true"></div>

        <header class="cinematic-nav" aria-label="Primary navigation">
            <a class="cinematic-brand" href="/" aria-label="King Yurina home"><span aria-hidden="true">♕</span><strong>king yurina</strong></a>
            <nav aria-label="Main menu">
                <a th:each="menu : ${mainMenus}" th:href="${menu.href}" th:text="${menu.label}" href="/quant">Quant</a>
            </nav>
            <a class="cinematic-dashboard" th:href="@{/quant}" href="/quant">Dashboard</a>
        </header>

        <main class="cinematic-content">
            <section class="cinematic-intro" data-scene-phase="intro" aria-labelledby="hero-title">
                <p>Calibrated · Intelligent · Disciplined</p>
                <h1 id="hero-title">king yurina</h1>
                <span>Quant AI engineered for calibrated market decisions</span>
            </section>

            <aside class="cinematic-metrics" aria-label="Illustrative Quant metrics">
                <article data-metric="signal"><span>Signal</span><strong>79<small>/100</small></strong><em>Strong</em></article>
                <article data-metric="alpha"><span>20D Alpha</span><strong>+2.4%</strong><em>vs. benchmark</em></article>
                <article data-metric="upside"><span>Upside</span><strong>58%</strong><em>Probability</em></article>
                <article data-metric="risk"><span>Risk</span><strong>Medium</strong><em>Controlled</em></article>
            </aside>

            <section class="cinematic-handoff" data-scene-phase="handoff">
                <p>Quant Engine · Online</p>
                <h2>Enter the intelligence core.</h2>
                <a th:href="@{/quant}" href="/quant">Explore the Intelligence</a>
            </section>

            <div class="cinematic-scroll-cue" aria-hidden="true"><span></span><b>Scroll to enter</b></div>
            <div class="cinematic-progress" data-cinematic-progress aria-hidden="true"><span></span></div>
            <p class="cinematic-disclosure">Illustrative model outputs · Not live market data</p>
        </main>
    </div>
</div>
<script type="module" th:src="@{/js/home-cinematic-3d.js(v='20260718')}" src="../static/js/home-cinematic-3d.js?v=20260718"></script>
</body>
</html>
```

- [ ] **Step 2: Add isolated responsive styling**

Create `home-cinematic.css` with:

```css
:root {
    color-scheme: dark;
    --lab-ink: #f2f7ff;
    --lab-muted: #93a3b8;
    --lab-blue: #3478ff;
    --lab-line: rgba(153, 191, 255, 0.2);
}

* { box-sizing: border-box; }
html { background: #03050b; }
body { margin: 0; font-family: "Inter", system-ui, sans-serif; }
a { color: inherit; text-decoration: none; }

.cinematic-scroll { position: relative; min-height: 500svh; background: #03050b; }
.cinematic-viewport { position: sticky; top: 0; min-height: 100svh; overflow: hidden; color: var(--lab-ink); background: #03050b; }
.cinematic-canvas, .cinematic-fallback, .cinematic-vignette { position: absolute; inset: 0; width: 100%; height: 100%; }
.cinematic-canvas { z-index: 1; display: block; }
.cinematic-fallback { z-index: 0; background: radial-gradient(circle at 50% 55%, #173b78 0, #09142b 22%, #03050b 58%); }
.cinematic-vignette { z-index: 2; pointer-events: none; background: radial-gradient(circle, transparent 30%, rgba(0, 2, 8, 0.78) 100%); }
.cinematic-nav, .cinematic-content { position: relative; z-index: 5; }
.cinematic-nav { position: absolute; top: 22px; right: 3vw; left: 3vw; display: grid; grid-template-columns: auto 1fr auto; align-items: center; gap: 24px; min-height: 58px; padding: 0 22px; border: 1px solid var(--lab-line); border-radius: 999px; background: rgba(3, 7, 16, 0.58); backdrop-filter: blur(18px); }
.cinematic-brand { display: flex; align-items: center; gap: 10px; font-family: "Playfair Display", serif; }
.cinematic-nav nav { display: flex; justify-content: center; gap: clamp(14px, 2.5vw, 34px); color: var(--lab-muted); font-size: 0.68rem; text-transform: uppercase; }
.cinematic-dashboard { padding: 10px 14px; border: 1px solid var(--lab-line); border-radius: 999px; font-size: 0.72rem; }
.cinematic-content { min-height: 100svh; pointer-events: none; }
.cinematic-content a { pointer-events: auto; }
.cinematic-intro, .cinematic-handoff { position: absolute; top: 50%; left: 50%; width: min(900px, calc(100% - 36px)); text-align: center; transform: translate(-50%, -50%); transition: opacity 240ms ease, transform 240ms ease; }
.cinematic-intro.is-hidden { opacity: 0; transform: translate(-50%, -58%); }
.cinematic-intro p, .cinematic-handoff p { color: #8fb4ff; font-size: 0.68rem; letter-spacing: 0.34em; text-transform: uppercase; }
.cinematic-intro h1 { margin: 8px 0; font-family: "Playfair Display", serif; font-size: clamp(4.4rem, 11vw, 10rem); font-weight: 500; line-height: 0.82; }
.cinematic-intro span { color: #bcc8d8; font-family: "Playfair Display", serif; font-size: clamp(1rem, 1.8vw, 1.45rem); }
.cinematic-metrics { position: absolute; inset: 0; pointer-events: none; }
.cinematic-metrics article { position: absolute; width: 190px; padding: 16px; border: 1px solid var(--lab-line); border-radius: 18px; opacity: 0; background: linear-gradient(135deg, rgba(16, 31, 58, 0.82), rgba(4, 9, 19, 0.56)); box-shadow: inset 0 1px rgba(255, 255, 255, 0.14), 0 18px 54px rgba(0, 0, 0, 0.28); backdrop-filter: blur(18px); transform: translateY(18px) scale(0.94); transition: opacity 260ms ease, transform 260ms ease; }
.cinematic-metrics article.is-visible { opacity: 1; transform: none; }
.cinematic-metrics span, .cinematic-metrics em { display: block; color: var(--lab-muted); font-size: 0.7rem; font-style: normal; }
.cinematic-metrics strong { display: block; margin: 7px 0 4px; font-family: "Playfair Display", serif; font-size: 2rem; font-weight: 500; }
[data-metric="signal"] { top: 30%; left: 5%; }
[data-metric="alpha"] { top: 57%; left: 8%; }
[data-metric="upside"] { top: 32%; right: 6%; }
[data-metric="risk"] { top: 60%; right: 8%; }
.cinematic-handoff { opacity: 0; transform: translate(-50%, -42%); }
.cinematic-handoff.is-visible { opacity: 1; transform: translate(-50%, -50%); }
.cinematic-handoff h2 { margin: 8px 0 24px; font-family: "Playfair Display", serif; font-size: clamp(2.2rem, 5vw, 5rem); font-weight: 500; }
.cinematic-handoff a { display: inline-flex; padding: 14px 20px; border: 1px solid rgba(115, 165, 255, 0.62); border-radius: 999px; background: rgba(26, 82, 196, 0.22); box-shadow: 0 0 32px rgba(52, 120, 255, 0.22); }
.cinematic-scroll-cue, .cinematic-progress, .cinematic-disclosure { position: absolute; z-index: 6; }
.cinematic-scroll-cue { bottom: 28px; left: 50%; color: var(--lab-muted); font-size: 0.64rem; letter-spacing: 0.16em; text-transform: uppercase; transform: translateX(-50%); }
.cinematic-progress { top: 50%; right: 18px; width: 2px; height: 120px; background: rgba(255, 255, 255, 0.12); transform: translateY(-50%); }
.cinematic-progress span { display: block; width: 100%; height: 100%; background: #4a83ff; transform: scaleY(var(--scene-progress, 0)); transform-origin: top; }
.cinematic-disclosure { right: 24px; bottom: 20px; margin: 0; color: rgba(190, 204, 224, 0.6); font-size: 0.6rem; }
.cinematic-home.is-fallback .cinematic-canvas { opacity: 0; }

@media (max-width: 760px) {
    .cinematic-scroll { min-height: 360svh; }
    .cinematic-nav { grid-template-columns: 1fr auto; }
    .cinematic-nav nav { display: none; }
    .cinematic-intro { top: 32%; }
    .cinematic-metrics article { width: 150px; padding: 12px; }
    [data-metric="signal"], [data-metric="alpha"] { left: 12px; }
    [data-metric="upside"], [data-metric="risk"] { right: 12px; }
    [data-metric="signal"], [data-metric="upside"] { top: 50%; }
    [data-metric="alpha"], [data-metric="risk"] { top: 68%; }
}

@media (prefers-reduced-motion: reduce) {
    .cinematic-scroll { min-height: 100svh; }
    .cinematic-viewport { position: relative; }
    .cinematic-metrics article { opacity: 1; transform: none; transition: none; }
    .cinematic-handoff { top: 78%; opacity: 1; transform: translate(-50%, -50%); transition: none; }
    .cinematic-scroll-cue, .cinematic-progress { display: none; }
}
```

- [ ] **Step 3: Run the template test and record the expected partial failure**

Run:

```powershell
mvn.cmd "-Dmaven.repo.local=.m2/repository" -Dtest=HomePageTemplateContractTest test
```

Expected: the template assertions pass; asset assertions still fail because the JavaScript modules are not present.

- [ ] **Step 4: Commit the semantic overlay**

```powershell
git add src/main/resources/templates/index.html src/main/resources/static/css/home-cinematic.css
git commit -m "feat: add cinematic homepage overlay"
```

---

### Task 3: Add Adaptive Renderer Lifecycle and Fallback

**Files:**
- Create: `src/main/resources/static/js/home-cinematic-quality.js`
- Create: `src/main/resources/static/js/home-cinematic-3d.js`
- Test: `src/test/java/com/kingyurina/demo/web/HomePageTemplateContractTest.java`

**Interfaces:**
- Consumes: `[data-cinematic-lab]`, `[data-cinematic-canvas]`, local Three.js module.
- Produces: `selectQualityProfile(environment)`, normalized `progress`, renderer lifecycle, and `startCanvasFallback(canvas)`.

- [ ] **Step 1: Implement deterministic quality selection**

Create `home-cinematic-quality.js`:

```javascript
export function selectQualityProfile(environment) {
    if (environment.reduceMotion) {
        return { name: "reduced", pixelRatio: 1, shadows: false, particleCount: 120, segments: 24 };
    }
    if (environment.mobile || environment.cores <= 4 || environment.memory <= 4) {
        return { name: "low", pixelRatio: 1, shadows: false, particleCount: 320, segments: 36 };
    }
    if (environment.cores <= 8 || environment.memory <= 8) {
        return { name: "medium", pixelRatio: 1.35, shadows: true, particleCount: 720, segments: 48 };
    }
    return { name: "high", pixelRatio: 1.65, shadows: true, particleCount: 1200, segments: 64 };
}

export function readEnvironment() {
    return {
        reduceMotion: window.matchMedia("(prefers-reduced-motion: reduce)").matches,
        mobile: window.matchMedia("(max-width: 760px)").matches,
        cores: navigator.hardwareConcurrency || 4,
        memory: navigator.deviceMemory || 4
    };
}
```

- [ ] **Step 2: Implement the renderer and fallback orchestration**

Create `home-cinematic-3d.js` with these exact imports and lifecycle:

```javascript
import { readEnvironment, selectQualityProfile } from "./home-cinematic-quality.js";
import { createLaboratory } from "./home-cinematic-lab.js";
import { createQuantEngine } from "./home-cinematic-engine.js";
import { createDataVortex } from "./home-cinematic-particles.js";

const THREE_MODULE_URL = "/js/vendor/three.module.js";
const root = document.querySelector("[data-cinematic-lab]");
const canvas = document.querySelector("[data-cinematic-canvas]");

if (root && canvas) {
    startCinematicLaboratory(root, canvas).catch(() => {
        document.body.classList.add("is-fallback");
        startCanvasFallback(canvas);
    });
}

async function startCinematicLaboratory(stage, targetCanvas) {
    const THREE = await import(THREE_MODULE_URL);
    const quality = selectQualityProfile(readEnvironment());
    const renderer = new THREE.WebGLRenderer({ canvas: targetCanvas, antialias: quality.name !== "low", powerPreference: "high-performance" });
    renderer.outputColorSpace = THREE.SRGBColorSpace;
    renderer.toneMapping = THREE.ACESFilmicToneMapping;
    renderer.toneMappingExposure = 0.88;
    renderer.shadowMap.enabled = quality.shadows;
    renderer.shadowMap.type = THREE.PCFSoftShadowMap;

    const scene = new THREE.Scene();
    scene.background = new THREE.Color(0x03050b);
    scene.fog = new THREE.FogExp2(0x03050b, 0.035);
    const camera = new THREE.PerspectiveCamera(42, 1, 0.1, 80);
    const laboratory = createLaboratory(THREE, quality);
    const engine = createQuantEngine(THREE, quality);
    const vortex = createDataVortex(THREE, quality);
    scene.add(laboratory.group, engine.group, vortex.group);

    const state = {
        width: 0,
        height: 0,
        progress: 0,
        targetProgress: 0,
        visible: true,
        pointerX: 0,
        pointerY: 0,
        targetPointerX: 0,
        targetPointerY: 0,
        startedAt: performance.now()
    };

    function resize() {
        const rect = targetCanvas.getBoundingClientRect();
        const width = Math.max(1, Math.round(rect.width));
        const height = Math.max(1, Math.round(rect.height));
        if (width === state.width && height === state.height) return;
        state.width = width;
        state.height = height;
        renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, quality.pixelRatio));
        renderer.setSize(width, height, false);
        camera.aspect = width / height;
        camera.updateProjectionMatrix();
    }

    function readProgress() {
        const rect = stage.getBoundingClientRect();
        state.targetProgress = clamp(-rect.top / Math.max(1, rect.height - window.innerHeight), 0, 1);
    }

    function readPointer(event) {
        state.targetPointerX = clamp(event.clientX / window.innerWidth * 2 - 1, -1, 1);
        state.targetPointerY = clamp(event.clientY / window.innerHeight * -2 + 1, -1, 1);
    }

    const observer = new IntersectionObserver((entries) => {
        state.visible = entries.some((entry) => entry.isIntersecting) && !document.hidden;
    });
    observer.observe(stage);

    window.addEventListener("resize", resize, { passive: true });
    window.addEventListener("scroll", readProgress, { passive: true });
    window.addEventListener("pointermove", readPointer, { passive: true });
    document.addEventListener("visibilitychange", () => {
        state.visible = !document.hidden;
    });

    function frame(now) {
        resize();
        readProgress();
        state.progress += (state.targetProgress - state.progress) * 0.075;
        state.pointerX += (state.targetPointerX - state.pointerX) * 0.05;
        state.pointerY += (state.targetPointerY - state.pointerY) * 0.05;
        const time = quality.name === "reduced" ? 0 : (now - state.startedAt) * 0.001;

        updateCamera(camera, state, quality);
        laboratory.update(time, state.progress, state);
        engine.update(time, state.progress, state);
        vortex.update(time, state.progress, state);
        updateOverlay(state.progress);

        if (state.visible) renderer.render(scene, camera);
        requestAnimationFrame(frame);
    }

    requestAnimationFrame(frame);
}

function updateCamera(camera, state, quality) {
    const mobileOffset = quality.name === "low" ? 2.2 : 0;
    camera.position.set(state.pointerX * 0.18, 1.2 + state.pointerY * 0.08 - state.progress * 0.34, 15.5 + mobileOffset - state.progress * 14.2);
    camera.lookAt(state.pointerX * 0.08, 0.2 - state.progress * 0.2, -2.4 - state.progress * 1.4);
}

function updateOverlay(progress) {
    document.documentElement.style.setProperty("--scene-progress", progress.toFixed(4));
    document.querySelector(".cinematic-intro")?.classList.toggle("is-hidden", progress > 0.2);
    document.querySelectorAll("[data-metric]").forEach((metric, index) => {
        metric.classList.toggle("is-visible", progress > 0.27 + index * 0.085 && progress < 0.9);
    });
    document.querySelector(".cinematic-handoff")?.classList.toggle("is-visible", progress > 0.82);
}

export function startCanvasFallback(targetCanvas) {
    const context = targetCanvas.getContext("2d");
    if (!context) return;
    function draw() {
        const width = targetCanvas.clientWidth || window.innerWidth;
        const height = targetCanvas.clientHeight || window.innerHeight;
        targetCanvas.width = width;
        targetCanvas.height = height;
        const gradient = context.createRadialGradient(width / 2, height * 0.56, 10, width / 2, height * 0.56, width * 0.58);
        gradient.addColorStop(0, "#1f5fc4");
        gradient.addColorStop(0.22, "#0a1d42");
        gradient.addColorStop(1, "#03050b");
        context.fillStyle = gradient;
        context.fillRect(0, 0, width, height);
    }
    draw();
    window.addEventListener("resize", draw, { passive: true });
}

function clamp(value, min, max) {
    return Math.min(max, Math.max(min, value));
}
```

- [ ] **Step 3: Run the contract test and confirm only scene-module assertions fail**

Run:

```powershell
mvn.cmd "-Dmaven.repo.local=.m2/repository" -Dtest=HomePageTemplateContractTest test
```

Expected: template and fallback assertions pass; test fails because laboratory, engine, and particle modules are not present.

- [ ] **Step 4: Commit adaptive lifecycle work**

```powershell
git add src/main/resources/static/js/home-cinematic-quality.js src/main/resources/static/js/home-cinematic-3d.js
git commit -m "feat: add adaptive cinematic renderer lifecycle"
```

---

### Task 4: Build the Physical Laboratory, Quant Engine, and Data Vortex

**Files:**
- Create: `src/main/resources/static/js/home-cinematic-lab.js`
- Create: `src/main/resources/static/js/home-cinematic-engine.js`
- Create: `src/main/resources/static/js/home-cinematic-particles.js`
- Test: `src/test/java/com/kingyurina/demo/web/HomePageTemplateContractTest.java`

**Interfaces:**
- Consumes: Three.js namespace, quality profile, normalized `progress`, pointer state.
- Produces: `{ group, update(time, progress, state) }` from all three factories.

- [ ] **Step 1: Implement the physical laboratory shell**

`createLaboratory(THREE, quality)` must:

- create a `THREE.Group` at `(0, 0, -3)`;
- build a dark floor with `PlaneGeometry(36, 44)`;
- build paired wall ribs from instanced or shared `BoxGeometry`;
- add foreground rails and overhead cable conduits for parallax;
- add cool strip lights and two restrained warm practical lights;
- enable cast/receive shadows only when `quality.shadows` is true;
- return an `update` method that moves only foreground parallax groups and practical-light intensity.

Use this public shape:

```javascript
export function createLaboratory(THREE, quality) {
    const group = new THREE.Group();
    group.position.set(0, 0, -3);
    const foreground = new THREE.Group();
    const practicalLights = [];

    const gunmetal = new THREE.MeshStandardMaterial({ color: 0x111722, metalness: 0.82, roughness: 0.3 });
    const floorMaterial = new THREE.MeshStandardMaterial({ color: 0x070a10, metalness: 0.62, roughness: 0.48 });
    const glass = new THREE.MeshPhysicalMaterial({ color: 0x183157, metalness: 0.06, roughness: 0.18, transmission: 0.28, transparent: true, opacity: 0.38 });
    const floor = new THREE.Mesh(new THREE.PlaneGeometry(36, 44), floorMaterial);
    floor.rotation.x = -Math.PI / 2;
    floor.position.set(0, -2.35, -7);
    floor.receiveShadow = quality.shadows;
    group.add(floor);

    const ribGeometry = new THREE.BoxGeometry(0.32, 7.4, 0.46);
    for (let index = 0; index < 10; index += 1) {
        const z = 5 - index * 3.5;
        [-1, 1].forEach((side) => {
            const rib = new THREE.Mesh(ribGeometry, gunmetal);
            rib.position.set(side * 7.6, 1.0, z);
            rib.rotation.z = side * -0.08;
            rib.castShadow = quality.shadows;
            group.add(rib);
        });
    }

    const conduitGeometry = new THREE.CylinderGeometry(0.09, 0.09, 34, 16);
    [-3.4, 0, 3.4].forEach((x) => {
        const conduit = new THREE.Mesh(conduitGeometry, gunmetal);
        conduit.rotation.x = Math.PI / 2;
        conduit.position.set(x, 4.25, -7);
        group.add(conduit);
    });

    const railGeometry = new THREE.BoxGeometry(0.18, 0.22, 22);
    [-3.1, 3.1].forEach((x) => {
        const rail = new THREE.Mesh(railGeometry, gunmetal);
        rail.position.set(x, -1.55, 3.4);
        foreground.add(rail);
    });

    [-1, 1].forEach((side) => {
        const partition = new THREE.Mesh(new THREE.BoxGeometry(3.2, 4.6, 0.08), glass);
        partition.position.set(side * 5.4, 0.15, -5.6);
        partition.rotation.y = side * -0.13;
        group.add(partition);
    });

    const ambient = new THREE.HemisphereLight(0x4c71ae, 0x020306, 0.72);
    group.add(ambient);
    [[-5.8, 2.6, 1.5, 0x4f86ff], [5.8, 1.8, -5, 0x4f86ff], [-4.4, 3.4, -11, 0xffb16b]].forEach((config) => {
        const light = new THREE.PointLight(config[3], 1.4, 13, 2);
        light.position.set(config[0], config[1], config[2]);
        practicalLights.push(light);
        group.add(light);
    });

    group.add(foreground);
    return {
        group,
        update(time, progress, state) {
            foreground.position.x = state.pointerX * -0.08;
            foreground.position.z = progress * 0.8;
            practicalLights.forEach((light, index) => {
                light.intensity = 1.4 + smoothstep(0.08 + index * 0.04, 0.46, progress) * 2.2;
            });
        }
    };
}

function smoothstep(edge0, edge1, value) {
    const x = Math.min(1, Math.max(0, (value - edge0) / Math.max(0.0001, edge1 - edge0)));
    return x * x * (3 - 2 * x);
}
```

- [ ] **Step 2: Implement the manufactured Quant engine**

`createQuantEngine(THREE, quality)` must:

- use shared `MeshStandardMaterial` and `MeshPhysicalMaterial` instances for gunmetal, brushed steel, dark glass, and cobalt energy;
- create a supported pedestal, layered cylindrical chamber, central core, six structural struts, and three thick mechanical rings;
- give every visible surface actual thickness;
- keep engine placement at `(0, -0.8, -4.4)`;
- activate core light, ring speed, and chamber glow between progress `0.28` and `0.78`;
- avoid creating geometry, material, arrays, or colors inside `update`.

Use this public shape:

```javascript
export function createQuantEngine(THREE, quality) {
    const group = new THREE.Group();
    group.position.set(0, -0.8, -4.4);
    const rings = [];
    const activationLights = [];

    const gunmetal = new THREE.MeshStandardMaterial({ color: 0x151c28, metalness: 0.9, roughness: 0.24 });
    const steel = new THREE.MeshStandardMaterial({ color: 0x8793a4, metalness: 0.96, roughness: 0.16 });
    const glass = new THREE.MeshPhysicalMaterial({ color: 0x214f91, metalness: 0.02, roughness: 0.08, transmission: 0.62, thickness: 1.2, transparent: true, opacity: 0.5 });
    const energy = new THREE.MeshBasicMaterial({ color: 0x3478ff, transparent: true, opacity: 0.72, blending: THREE.AdditiveBlending, depthWrite: false });

    const pedestal = new THREE.Mesh(new THREE.CylinderGeometry(3.45, 4.05, 0.54, quality.segments), gunmetal);
    pedestal.position.y = -1.62;
    pedestal.castShadow = quality.shadows;
    pedestal.receiveShadow = quality.shadows;
    group.add(pedestal);

    const pedestalTrim = new THREE.Mesh(new THREE.TorusGeometry(3.44, 0.12, 18, quality.segments * 2), steel);
    pedestalTrim.rotation.x = Math.PI / 2;
    pedestalTrim.position.y = -1.34;
    group.add(pedestalTrim);

    const chamber = new THREE.Mesh(new THREE.SphereGeometry(1.56, quality.segments, Math.max(24, quality.segments / 2)), glass);
    chamber.castShadow = quality.shadows;
    group.add(chamber);

    const core = new THREE.Mesh(new THREE.IcosahedronGeometry(0.82, 4), energy);
    core.userData.baseScale = 1;
    group.add(core);

    const strutGeometry = new THREE.BoxGeometry(0.18, 3.4, 0.28);
    for (let index = 0; index < 6; index += 1) {
        const angle = index / 6 * Math.PI * 2;
        const strut = new THREE.Mesh(strutGeometry, gunmetal);
        strut.position.set(Math.cos(angle) * 2.05, -0.05, Math.sin(angle) * 2.05);
        strut.rotation.z = Math.cos(angle) * 0.24;
        strut.rotation.x = Math.sin(angle) * 0.24;
        strut.castShadow = quality.shadows;
        group.add(strut);
    }

    [
        { radius: 2.25, tube: 0.16, x: 1.18, y: 0.12, z: 0.18 },
        { radius: 2.62, tube: 0.11, x: 0.32, y: 0.84, z: -0.22 },
        { radius: 3.02, tube: 0.07, x: 1.46, y: -0.18, z: 0.56 }
    ].forEach((specification) => {
        const ring = new THREE.Mesh(new THREE.TorusGeometry(specification.radius, specification.tube, 20, quality.segments * 2), specification.tube > 0.12 ? steel : glass);
        ring.rotation.set(specification.x, specification.y, specification.z);
        ring.userData.baseY = specification.y;
        ring.userData.baseZ = specification.z;
        rings.push(ring);
        group.add(ring);
    });

    [0x3478ff, 0x82bdff].forEach((color, index) => {
        const light = new THREE.PointLight(color, 0, 11 + index * 4, 2);
        light.position.set(index ? 1.8 : -1.2, index ? 1.1 : -0.4, 1.4);
        activationLights.push(light);
        group.add(light);
    });

    return {
        group,
        update(time, progress, state) {
            const activation = smoothstep(0.28, 0.68, progress);
            rings.forEach((ring, index) => {
                ring.rotation.y = ring.userData.baseY + time * (0.12 + index * 0.08) * activation * (index % 2 ? -1 : 1);
                ring.rotation.z = ring.userData.baseZ + Math.sin(time * 0.4 + index) * 0.04 * activation;
            });
            activationLights.forEach((light, index) => {
                light.intensity = activation * (5 + index * 1.5);
            });
            const pulse = 0.92 + activation * 0.16 + Math.sin(time * 2.4) * 0.025 * activation;
            core.scale.setScalar(pulse);
            group.rotation.y = state.pointerX * 0.025;
        }
    };
}

function smoothstep(edge0, edge1, value) {
    const x = Math.min(1, Math.max(0, (value - edge0) / Math.max(0.0001, edge1 - edge0)));
    return x * x * (3 - 2 * x);
}
```

- [ ] **Step 3: Implement orbital particles and the contained data vortex**

`createDataVortex(THREE, quality)` must:

- allocate exactly `quality.particleCount` positions and seed values during construction;
- keep particles spatially contained around and behind the engine;
- transition from slow orbital movement to forward acceleration after progress `0.55`;
- update the existing `Float32Array` in place;
- use additive blending with restrained opacity;
- omit string labels and UI from the canvas.

Use this public shape:

```javascript
export function createDataVortex(THREE, quality) {
    const group = new THREE.Group();
    group.position.set(0, -0.25, -4.2);
    const count = quality.particleCount;
    const positions = new Float32Array(count * 3);
    const seeds = new Float32Array(count * 4);
    for (let index = 0; index < count; index += 1) {
        const positionOffset = index * 3;
        const seedOffset = index * 4;
        const radius = 0.8 + Math.random() * 4.8;
        const phase = Math.random() * Math.PI * 2;
        const speed = 0.12 + Math.random() * 0.42;
        const depth = -12 + Math.random() * 18;
        seeds[seedOffset] = radius;
        seeds[seedOffset + 1] = phase;
        seeds[seedOffset + 2] = speed;
        seeds[seedOffset + 3] = depth;
        positions[positionOffset] = Math.cos(phase) * radius;
        positions[positionOffset + 1] = Math.sin(phase * 1.3) * radius * 0.38;
        positions[positionOffset + 2] = depth;
    }

    const geometry = new THREE.BufferGeometry();
    geometry.setAttribute("position", new THREE.BufferAttribute(positions, 3));
    const material = new THREE.PointsMaterial({
        color: 0x65a2ff,
        size: quality.name === "low" ? 0.035 : 0.045,
        transparent: true,
        opacity: 0.58,
        depthWrite: false,
        blending: THREE.AdditiveBlending
    });
    group.add(new THREE.Points(geometry, material));

    return {
        group,
        update(time, progress, state) {
            const intake = smoothstep(0.55, 0.94, progress);
            for (let index = 0; index < count; index += 1) {
                const offset = index * 3;
                const seed = index * 4;
                const radius = seeds[seed];
                const phase = seeds[seed + 1];
                const speed = seeds[seed + 2];
                const depth = seeds[seed + 3];
                const angle = phase + time * speed * (1 + intake * 4);
                positions[offset] = Math.cos(angle) * radius * (1 - intake * 0.72) + state.pointerX * depth * 0.01;
                positions[offset + 1] = Math.sin(angle * 1.3) * radius * 0.38;
                positions[offset + 2] = depth + intake * ((time * speed * 3 + phase) % 8 - 4);
            }
            geometry.attributes.position.needsUpdate = true;
        }
    };
}
```

- [ ] **Step 4: Run the focused test**

Run:

```powershell
mvn.cmd "-Dmaven.repo.local=.m2/repository" -Dtest=HomePageTemplateContractTest test
```

Expected: PASS, 3 tests run with 0 failures and 0 errors.

- [ ] **Step 5: Commit the complete 3D scene**

```powershell
git add src/main/resources/static/js/home-cinematic-lab.js src/main/resources/static/js/home-cinematic-engine.js src/main/resources/static/js/home-cinematic-particles.js
git commit -m "feat: build cinematic quant laboratory scene"
```

---

### Task 5: Integrate, Inspect, and Tune the Complete Experience

**Files:**
- Modify: `src/main/resources/static/css/home-cinematic.css`
- Modify: `src/main/resources/static/js/home-cinematic-3d.js`
- Modify as required by defects only: `src/main/resources/static/js/home-cinematic-lab.js`
- Modify as required by defects only: `src/main/resources/static/js/home-cinematic-engine.js`
- Modify as required by defects only: `src/main/resources/static/js/home-cinematic-particles.js`
- Test: `src/test/java/com/kingyurina/demo/web/HomePageTemplateContractTest.java`

**Interfaces:**
- Consumes: the complete landing-page scene and overlay.
- Produces: a visually verified responsive experience without regressions to other routes.

- [ ] **Step 1: Run all automated tests**

Run:

```powershell
mvn.cmd "-Dmaven.repo.local=.m2/repository" test
```

Expected: BUILD SUCCESS with all repository tests passing.

- [ ] **Step 2: Start the existing application**

Run the workspace-approved local server flow and open `http://127.0.0.1:8080/`.

Expected: the landing page loads without a database-dependent page-request failure, and `/quant` remains reachable.

- [ ] **Step 3: Inspect desktop cinematic continuity**

Verify at a desktop viewport:

- the opening shot reads as a physical dark laboratory;
- the camera path advances continuously from wide shot to engine chamber;
- reversing scroll reverses the scene without snapping;
- rings and lights activate after the approach begins;
- metrics reveal in order and remain readable;
- the CTA appears only in the final phase;
- there are no browser console errors.

Fix only observed defects, rerun `HomePageTemplateContractTest`, and reload the page.

- [ ] **Step 4: Inspect responsive and fallback states**

Verify:

- tablet and mobile framing retain the engine, all metrics, and CTA;
- reduced-motion shows all essential content without a 500-viewport scroll journey;
- forcing WebGL initialization failure adds `is-fallback` and keeps content usable;
- tab navigation reaches the brand, menu links, Dashboard, and final CTA;
- bright engine states do not reduce text contrast.

Fix only observed defects and rerun the focused test.

- [ ] **Step 5: Re-run the full verification suite**

Run:

```powershell
mvn.cmd "-Dmaven.repo.local=.m2/repository" test
```

Expected: BUILD SUCCESS.

- [ ] **Step 6: Commit integration tuning**

```powershell
git add src/main/resources/templates/index.html src/main/resources/static/css/home-cinematic.css src/main/resources/static/js/home-cinematic-*.js src/test/java/com/kingyurina/demo/web/HomePageTemplateContractTest.java
git commit -m "feat: deliver cinematic 3d laboratory homepage"
```

---

## Final Verification Checklist

- [ ] Only the landing page design changed.
- [ ] No external API or frontend build dependency was introduced.
- [ ] The desktop background is live WebGL, not `king-yurina-reference-hero.png`.
- [ ] The laboratory, engine, and vortex are separate focused modules.
- [ ] Scroll progress is reversible and drives camera, lights, engine, particles, metrics, and CTA.
- [ ] Reduced-motion and WebGL failure keep semantic content usable.
- [ ] Mobile uses an adaptive quality profile.
- [ ] `/quant` and existing navigation links remain functional.
- [ ] Full Maven test suite passes.
