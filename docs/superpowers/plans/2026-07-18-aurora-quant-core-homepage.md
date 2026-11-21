# Aurora Quant Core Homepage Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the rejected procedural laboratory homepage with an Awwwards-quality, cursor-responsive Aurora Quant Core while retaining Spring Boot, Thymeleaf, DB-backed navigation, and the existing `/quant` handoff.

**Architecture:** Thymeleaf owns all semantic content and interaction targets. A local Three.js r165 renderer draws one full-screen `ShaderMaterial` plane, while focused vanilla ES modules handle shader creation, capability selection, spring motion, scroll progress, and lifecycle. CSS owns the glass interface, metric rail, responsive layout, accessibility states, and the no-WebGL fallback.

**Tech Stack:** Java 21, Spring Boot 4.0.6, Thymeleaf, JUnit 5, semantic CSS, native ES modules, Node.js built-in test runner, local Three.js r165, GLSL.

## Global Constraints

- Do not introduce React, React Three Fiber, Tailwind, npm packages, a frontend bundler, or a remote runtime dependency.
- `GET /` must keep using the existing controller and `MenuService.mainMenus()` DB-backed model.
- Do not add an external API call or `fetch()` to the homepage request path or animation lifecycle.
- Keep `/quant` and every non-home page unchanged.
- Serve Three.js only from `/js/vendor/three.module.js`.
- Use exactly one full-screen shader plane; do not recreate visible rings, laboratory primitives, particles, or a literal metal machine.
- Palette: void `#02040B`, elevated void `#07101F`, violet `#776BFF`, cobalt `#4C6FFF`, cyan `#32D8FF`, emerald `#3DFFD0`, rose `#FF6FC8`, text `#F7FAFF`, muted `#A7B4C8`.
- Copy: `QUANT INTELLIGENCE / AURORA CORE ONLINE`, `See the signal beneath the noise.`, `Signal, alpha, upside and risk—resolved into one adaptive market view.`, `Market intelligence, resolved.`, `Enter Quant Intelligence`, and `Scroll to resolve`.
- Metric values remain Signal `79 / 100` Strong, 20D Alpha `+2.4%` vs. benchmark, Upside `58%` Probability, Risk `Medium` Controlled.
- Desktop cinematic range is `280svh`; mobile range is `220svh`; reduced motion is a normal `100svh` hero.
- Device pixel ratio must never exceed `1.75`.
- All clickable targets are at least `44px` high, keyboard focus is visible, and `prefers-reduced-motion` is respected.
- The canvas is decorative with `aria-hidden="true"`; all meaningful content stays in HTML.
- Run Maven with the workspace-local repository: `mvn.cmd "-Dmaven.repo.local=.m2/repository" test`.

## File Structure

- Modify `src/main/resources/templates/index.html`: semantic Aurora homepage overlay and DB-backed navigation.
- Create `src/main/resources/static/css/home-aurora.css`: page tokens, layout, glass treatment, timeline states, fallback, responsive rules, and accessibility.
- Create `src/main/resources/static/js/home-aurora.js`: renderer lifecycle, cached DOM, events, scroll state, and CSS state projection.
- Create `src/main/resources/static/js/home-aurora-field.js`: one-plane GLSL aurora field and uniform interface.
- Create `src/main/resources/static/js/home-aurora-motion.js`: pure spring, smoothing, and normalized scroll helpers.
- Create `src/main/resources/static/js/home-aurora-quality.js`: deterministic capability profiles and browser environment reader.
- Modify `src/test/java/com/kingyurina/demo/web/HomePageTemplateContractTest.java`: semantic, local-asset, shader, fallback, and legacy-exclusion contracts.
- Create `src/test/js/home-aurora-motion.test.mjs`: real tests for reversible scroll and damped motion.
- Create `src/test/js/home-aurora-quality.test.mjs`: real tests for reduced, low, medium, and high profiles.
- Delete the superseded `home-cinematic.css`, `home-cinematic-3d.js`, `home-cinematic-engine.js`, `home-cinematic-lab.js`, `home-cinematic-particles.js`, `home-cinematic-quality.js`, and `cinematic-laboratory-bg.png` after the new page is green.

---

### Task 1: Replace the Homepage Contract and Semantic Shell

**Files:**
- Modify: `src/test/java/com/kingyurina/demo/web/HomePageTemplateContractTest.java`
- Modify: `src/main/resources/templates/index.html`
- Create: `src/main/resources/static/css/home-aurora.css`
- Create: `src/main/resources/static/js/home-aurora.js`

**Interfaces:**
- Consumes: the existing `mainMenus` Thymeleaf model and `/quant` route.
- Produces: `[data-aurora-core]`, `[data-aurora-canvas]`, `[data-aurora-progress]`, `[data-aurora-metric]`, and the CSS/JS asset entry points used by later tasks.

- [ ] **Step 1: Replace the laboratory template test with a failing Aurora semantic contract**

Replace the current test class with:

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
    void homepageDeclaresAuroraCoreAndSemanticProductContent() throws IOException {
        String template = resource("templates/index.html");

        assertTrue(template.contains("home-aurora.css"));
        assertTrue(template.contains("data-aurora-core"));
        assertTrue(template.contains("data-aurora-canvas"));
        assertTrue(template.contains("data-aurora-progress"));
        assertTrue(template.contains("data-aurora-metric"));
        assertTrue(template.contains("QUANT INTELLIGENCE / AURORA CORE ONLINE"));
        assertTrue(template.contains("See the signal beneath the noise."));
        assertTrue(template.contains("Signal, alpha, upside and risk—resolved into one adaptive market view."));
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
```

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```powershell
mvn.cmd "-Dmaven.repo.local=.m2/repository" -Dtest=HomePageTemplateContractTest test
```

Expected: FAIL because the existing template still contains
`home-cinematic.css`, `data-cinematic-lab`, and the old copy.

- [ ] **Step 3: Replace `index.html` with the semantic Aurora shell**

Use this complete structure:

```html
<!doctype html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta name="theme-color" content="#02040b">
    <title>King Yurina | Quant Intelligence</title>
    <link rel="stylesheet" th:href="@{/css/home-aurora.css(v='20260718')}" href="../static/css/home-aurora.css?v=20260718">
</head>
<body class="aurora-home">
<a class="aurora-skip-link" href="#main-content">Skip to content</a>
<div class="aurora-story" data-aurora-core>
    <div class="aurora-viewport">
        <canvas class="aurora-canvas" data-aurora-canvas aria-hidden="true"></canvas>
        <div class="aurora-fallback" aria-hidden="true"></div>
        <div class="aurora-atmosphere" aria-hidden="true"></div>

        <header class="aurora-nav" aria-label="Primary navigation">
            <a class="aurora-brand" href="/" aria-label="King Yurina home">
                <span aria-hidden="true">K/Y</span>
                <strong>king yurina</strong>
            </a>
            <nav aria-label="Main menu">
                <a th:each="menu : ${mainMenus}"
                   th:href="${menu.href}"
                   th:text="${menu.label}"
                   href="/quant">Quant</a>
            </nav>
            <a class="aurora-dashboard" th:href="@{/quant}" href="/quant">Dashboard</a>
        </header>

        <main id="main-content" class="aurora-content">
            <section class="aurora-hero" aria-labelledby="hero-title">
                <div class="aurora-panel" data-aurora-panel>
                    <p class="aurora-status">
                        <span aria-hidden="true"></span>
                        QUANT INTELLIGENCE / AURORA CORE ONLINE
                    </p>
                    <h1 id="hero-title">See the signal beneath the noise.</h1>
                    <div class="aurora-copy-stage">
                        <p class="aurora-summary">Signal, alpha, upside and risk—resolved into one adaptive market view.</p>
                        <p class="aurora-resolved-copy" aria-hidden="true">Market intelligence, resolved.</p>
                    </div>
                    <a class="aurora-primary-action" th:href="@{/quant}" href="/quant">
                        <span>Enter Quant Intelligence</span>
                        <svg aria-hidden="true" viewBox="0 0 24 24">
                            <path d="M5 12h13M14 7l5 5-5 5"/>
                        </svg>
                    </a>
                </div>

                <section id="systems" class="aurora-metric-stage" aria-labelledby="metrics-title">
                    <h2 id="metrics-title" class="aurora-visually-hidden">Illustrative Quant metrics</h2>
                    <ul class="aurora-metric-rail">
                        <li data-aurora-metric style="--metric-index: 0">
                            <span>Signal</span><strong>79 <small>/ 100</small></strong><em>Strong</em>
                        </li>
                        <li data-aurora-metric style="--metric-index: 1">
                            <span>20D Alpha</span><strong>+2.4%</strong><em>vs. benchmark</em>
                        </li>
                        <li data-aurora-metric style="--metric-index: 2">
                            <span>Upside</span><strong>58%</strong><em>Probability</em>
                        </li>
                        <li data-aurora-metric style="--metric-index: 3">
                            <span>Risk</span><strong>Medium</strong><em>Controlled</em>
                        </li>
                    </ul>
                </section>
            </section>

            <div class="aurora-scroll-cue" aria-hidden="true">
                <span></span><b>Scroll to resolve</b>
            </div>
            <div class="aurora-progress" data-aurora-progress aria-hidden="true"><span></span></div>
            <p id="contact" class="aurora-disclosure">Illustrative model outputs · Not live market data</p>
        </main>
    </div>
</div>
<noscript>
    <style>
        .aurora-metric-rail,
        .aurora-metric-rail li { opacity: 1 !important; transform: none !important; }
    </style>
</noscript>
<script type="module" th:src="@{/js/home-aurora.js(v='20260718')}" src="../static/js/home-aurora.js?v=20260718"></script>
</body>
</html>
```

- [ ] **Step 4: Create minimal local assets so the intermediate page has no broken requests**

Create `home-aurora.css`:

```css
:root { color-scheme: dark; }
* { box-sizing: border-box; }
body { margin: 0; color: #f7faff; background: #02040b; font-family: "Segoe UI", Arial, sans-serif; }
a { color: inherit; }
.aurora-story,
.aurora-viewport { min-height: 100svh; }
.aurora-viewport { position: relative; overflow: hidden; }
.aurora-canvas,
.aurora-fallback,
.aurora-atmosphere { position: absolute; inset: 0; width: 100%; height: 100%; }
.aurora-fallback {
    background:
        radial-gradient(circle at 30% 35%, rgba(119,107,255,.42), transparent 45%),
        radial-gradient(circle at 75% 65%, rgba(50,216,255,.28), transparent 44%),
        #02040b;
}
.aurora-nav,
.aurora-content { position: relative; z-index: 2; }
.aurora-metric-rail li { opacity: 1; }
```

Create `home-aurora.js`:

```js
const root = document.querySelector("[data-aurora-core]");
if (root) {
    root.classList.add("is-ready", "has-metrics");
    const metrics = root.querySelectorAll("[data-aurora-metric]");
    for (let index = 0; index < metrics.length; index += 1) {
        metrics[index].classList.add("is-visible");
    }
}
```

- [ ] **Step 5: Run the focused test and verify GREEN**

Run the same Maven command. Expected: `Tests run: 1, Failures: 0, Errors: 0`.

- [ ] **Step 6: Commit the semantic shell**

```powershell
git add -- src/test/java/com/kingyurina/demo/web/HomePageTemplateContractTest.java src/main/resources/templates/index.html src/main/resources/static/css/home-aurora.css src/main/resources/static/js/home-aurora.js
git commit -m "feat: define aurora homepage shell"
```

---

### Task 2: Add Tested Motion and Capability Primitives

**Files:**
- Create: `src/test/js/home-aurora-motion.test.mjs`
- Create: `src/test/js/home-aurora-quality.test.mjs`
- Create: `src/main/resources/static/js/home-aurora-motion.js`
- Create: `src/main/resources/static/js/home-aurora-quality.js`

**Interfaces:**
- Produces: `createSpring()`, `stepSpring()`, `normalizedScrollProgress()`,
  `smoothstep()`, `selectAuroraQuality()`, and `readEnvironment()`.
- Consumed by: Task 4 renderer lifecycle.

- [ ] **Step 1: Write failing Node tests for reversible progress and damped motion**

Create `src/test/js/home-aurora-motion.test.mjs`:

```js
import test from "node:test";
import assert from "node:assert/strict";
import {
    createSpring,
    normalizedScrollProgress,
    smoothstep,
    stepSpring
} from "../../main/resources/static/js/home-aurora-motion.js";

test("normalized scroll progress is clamped and reversible", () => {
    assert.equal(normalizedScrollProgress(0, 2800, 1000), 0);
    assert.equal(normalizedScrollProgress(-900, 2800, 1000), 0.5);
    assert.equal(normalizedScrollProgress(-1800, 2800, 1000), 1);
    assert.equal(normalizedScrollProgress(-2400, 2800, 1000), 1);
    assert.equal(normalizedScrollProgress(300, 2800, 1000), 0);
});

test("spring converges to its target without snapping", () => {
    const spring = createSpring(0);
    spring.target = 1;
    stepSpring(spring, 1 / 60);
    assert.ok(spring.value > 0);
    assert.ok(spring.value < 1);
    for (let index = 0; index < 240; index += 1) {
        stepSpring(spring, 1 / 60);
    }
    assert.ok(Math.abs(spring.value - 1) < 0.001);
    assert.ok(Math.abs(spring.velocity) < 0.001);
});

test("smoothstep remains bounded", () => {
    assert.equal(smoothstep(0.2, 0.8, 0), 0);
    assert.equal(smoothstep(0.2, 0.8, 1), 1);
    assert.ok(smoothstep(0.2, 0.8, 0.5) > 0);
});
```

Create `src/test/js/home-aurora-quality.test.mjs`:

```js
import test from "node:test";
import assert from "node:assert/strict";
import { selectAuroraQuality } from "../../main/resources/static/js/home-aurora-quality.js";

test("reduced motion always selects the static profile", () => {
    assert.deepEqual(selectAuroraQuality({
        reduceMotion: true, mobile: false, cores: 16, memory: 32, devicePixelRatio: 3
    }), {
        name: "reduced", pixelRatio: 1, shaderQuality: 0.35,
        pointerEnabled: false, ambientSpeed: 0
    });
});

test("mobile and constrained devices use the low profile", () => {
    const profile = selectAuroraQuality({
        reduceMotion: false, mobile: true, cores: 8, memory: 8, devicePixelRatio: 3
    });
    assert.equal(profile.name, "low");
    assert.equal(profile.pixelRatio, 1);
    assert.equal(profile.pointerEnabled, false);
});

test("medium and high profiles cap device pixel ratio", () => {
    const medium = selectAuroraQuality({
        reduceMotion: false, mobile: false, cores: 8, memory: 8, devicePixelRatio: 2
    });
    const high = selectAuroraQuality({
        reduceMotion: false, mobile: false, cores: 16, memory: 32, devicePixelRatio: 3
    });
    assert.equal(medium.pixelRatio, 1.4);
    assert.equal(high.pixelRatio, 1.75);
});
```

- [ ] **Step 2: Run both files and verify RED**

Run:

```powershell
node --experimental-default-type=module --test src/test/js/home-aurora-motion.test.mjs src/test/js/home-aurora-quality.test.mjs
```

Expected: FAIL with module-not-found errors for `home-aurora-motion.js` and
`home-aurora-quality.js`.

- [ ] **Step 3: Implement the minimal motion module**

Create `home-aurora-motion.js`:

```js
const DEFAULT_SPRING = Object.freeze({
    stiffness: 90,
    damping: 20,
    mass: 1
});

export function createSpring(initialValue = 0) {
    return {
        value: initialValue,
        target: initialValue,
        velocity: 0
    };
}

export function stepSpring(spring, deltaSeconds, configuration = DEFAULT_SPRING) {
    const delta = Math.min(Math.max(deltaSeconds, 0), 1 / 20);
    const displacement = spring.value - spring.target;
    const acceleration = (
        -configuration.stiffness * displacement
        -configuration.damping * spring.velocity
    ) / configuration.mass;
    spring.velocity += acceleration * delta;
    spring.value += spring.velocity * delta;
    if (Math.abs(spring.target - spring.value) < 0.00001
        && Math.abs(spring.velocity) < 0.00001) {
        spring.value = spring.target;
        spring.velocity = 0;
    }
    return spring.value;
}

export function normalizedScrollProgress(rectTop, stageHeight, viewportHeight) {
    const travel = Math.max(1, stageHeight - viewportHeight);
    return clamp(-rectTop / travel, 0, 1);
}

export function smoothstep(edge0, edge1, value) {
    const amount = clamp((value - edge0) / Math.max(0.0001, edge1 - edge0), 0, 1);
    return amount * amount * (3 - 2 * amount);
}

export function clamp(value, minimum, maximum) {
    return Math.min(maximum, Math.max(minimum, value));
}
```

- [ ] **Step 4: Implement the deterministic quality module**

Create `home-aurora-quality.js`:

```js
export function selectAuroraQuality(environment) {
    if (environment.reduceMotion) {
        return {
            name: "reduced",
            pixelRatio: 1,
            shaderQuality: 0.35,
            pointerEnabled: false,
            ambientSpeed: 0
        };
    }
    if (environment.mobile || environment.cores <= 4 || environment.memory <= 4) {
        return {
            name: "low",
            pixelRatio: 1,
            shaderQuality: 0.5,
            pointerEnabled: false,
            ambientSpeed: 0.55
        };
    }
    if (environment.cores <= 8 || environment.memory <= 8) {
        return {
            name: "medium",
            pixelRatio: Math.min(environment.devicePixelRatio, 1.4),
            shaderQuality: 0.75,
            pointerEnabled: true,
            ambientSpeed: 0.8
        };
    }
    return {
        name: "high",
        pixelRatio: Math.min(environment.devicePixelRatio, 1.75),
        shaderQuality: 1,
        pointerEnabled: true,
        ambientSpeed: 1
    };
}

export function readEnvironment() {
    return {
        reduceMotion: window.matchMedia("(prefers-reduced-motion: reduce)").matches,
        mobile: window.matchMedia("(max-width: 900px), (pointer: coarse)").matches,
        cores: navigator.hardwareConcurrency || 8,
        memory: navigator.deviceMemory || 8,
        devicePixelRatio: window.devicePixelRatio || 1
    };
}
```

- [ ] **Step 5: Run both Node test files and verify GREEN**

Expected: `tests 6`, `pass 6`, `fail 0`.

- [ ] **Step 6: Commit the tested primitives**

```powershell
git add -- src/test/js/home-aurora-motion.test.mjs src/test/js/home-aurora-quality.test.mjs src/main/resources/static/js/home-aurora-motion.js src/main/resources/static/js/home-aurora-quality.js
git commit -m "feat: add aurora motion and quality primitives"
```

---

### Task 3: Build the One-Plane Aurora Shader

**Files:**
- Modify: `src/test/java/com/kingyurina/demo/web/HomePageTemplateContractTest.java`
- Create: `src/main/resources/static/js/home-aurora-field.js`

**Interfaces:**
- Consumes: a Three.js namespace and the quality profile from Task 2.
- Produces: `createAuroraField(THREE, quality)` returning
  `{ mesh, uniforms, resize(width, height), update(time, pointerX, pointerY, pointerEnergy, scroll), dispose() }`.

- [ ] **Step 1: Add a failing shader contract test**

Add this method to `HomePageTemplateContractTest`:

```java
@Test
void auroraFieldUsesOneLocalShaderPlaneAndRequiredUniforms() throws IOException {
    String field = resource("static/js/home-aurora-field.js");

    assertTrue(field.contains("export function createAuroraField"));
    assertTrue(field.contains("new THREE.PlaneGeometry(2, 2)"));
    assertTrue(field.contains("new THREE.ShaderMaterial"));
    assertTrue(field.contains("uTime"));
    assertTrue(field.contains("uResolution"));
    assertTrue(field.contains("uPointer"));
    assertTrue(field.contains("uPointerEnergy"));
    assertTrue(field.contains("uScroll"));
    assertTrue(field.contains("uQuality"));
    assertTrue(field.contains("fbm"));
    assertTrue(field.contains("auroraBand"));
    assertTrue(field.contains("grain"));
    assertFalse(field.contains("TorusGeometry"));
    assertFalse(field.contains("PointsMaterial"));
    assertFalse(field.contains("https://"));
    assertFalse(field.contains("fetch("));
}
```

- [ ] **Step 2: Run the focused Maven test and verify RED**

Expected: FAIL because `home-aurora-field.js` does not exist.

- [ ] **Step 3: Create the shader field with the required interface**

Create `home-aurora-field.js` with one `PlaneGeometry(2, 2)`, one
`ShaderMaterial`, and these exact uniforms:

```js
export function createAuroraField(THREE, quality) {
    const uniforms = {
        uTime: { value: 0 },
        uResolution: { value: new THREE.Vector2(1, 1) },
        uPointer: { value: new THREE.Vector2(0.5, 0.5) },
        uPointerEnergy: { value: 0 },
        uScroll: { value: 0 },
        uQuality: { value: quality.shaderQuality }
    };

    const material = new THREE.ShaderMaterial({
        uniforms,
        vertexShader: `
            varying vec2 vUv;
            void main() {
                vUv = uv;
                gl_Position = vec4(position, 1.0);
            }
        `,
        fragmentShader: `
            precision highp float;
            varying vec2 vUv;
            uniform float uTime;
            uniform vec2 uResolution;
            uniform vec2 uPointer;
            uniform float uPointerEnergy;
            uniform float uScroll;
            uniform float uQuality;

            float hash21(vec2 value) {
                value = fract(value * vec2(123.34, 456.21));
                value += dot(value, value + 45.32);
                return fract(value.x * value.y);
            }

            float noise(vec2 point) {
                vec2 cell = floor(point);
                vec2 local = fract(point);
                local = local * local * (3.0 - 2.0 * local);
                return mix(
                    mix(hash21(cell), hash21(cell + vec2(1.0, 0.0)), local.x),
                    mix(hash21(cell + vec2(0.0, 1.0)), hash21(cell + 1.0), local.x),
                    local.y
                );
            }

            float fbm(vec2 point) {
                float value = 0.0;
                float amplitude = 0.5;
                mat2 rotation = mat2(0.8, -0.6, 0.6, 0.8);
                for (int octave = 0; octave < 5; octave++) {
                    if (octave >= 3 && uQuality < 0.65) {
                        break;
                    }
                    if (octave >= 4 && uQuality < 0.9) {
                        break;
                    }
                    value += amplitude * noise(point);
                    point = rotation * point * 2.03 + 19.1;
                    amplitude *= 0.5;
                }
                return value;
            }

            float auroraBand(vec2 point, float offset, float width, float frequency, float phase) {
                float warp = fbm(vec2(point.x * frequency + phase, point.y * 0.7 - phase));
                float center = offset + (warp - 0.5) * (0.42 + uScroll * 0.16);
                float distanceToBand = abs(point.y - center);
                float core = exp(-distanceToBand * distanceToBand / max(0.001, width));
                float strands = 0.72 + 0.28 * noise(vec2(point.x * 8.0 + phase, point.y * 3.0));
                return core * strands;
            }

            vec3 screenBlend(vec3 base, vec3 light) {
                return 1.0 - (1.0 - base) * (1.0 - light);
            }

            void main() {
                vec2 uv = vUv;
                vec2 point = uv - 0.5;
                point.x *= uResolution.x / max(1.0, uResolution.y);

                vec2 pointerPoint = uPointer - 0.5;
                pointerPoint.x *= uResolution.x / max(1.0, uResolution.y);
                float pointerDistance = length(point - pointerPoint);
                float pointerGlow = exp(-pointerDistance * pointerDistance * 5.2);
                float localEnergy = pointerGlow * (0.12 + uPointerEnergy * 0.55);
                float widthLift = pointerGlow * uPointerEnergy * 0.028;

                float convergence = smoothstep(0.74, 1.0, uScroll);
                float time = uTime * mix(1.0, 0.35, convergence);
                float depth = mix(0.86, 1.42, uScroll);
                vec2 driftA = point * depth + vec2(time * 0.018, time * -0.012);
                vec2 driftB = point * (depth * 1.32) + vec2(time * -0.012, time * 0.016);
                vec2 driftC = point * (depth * 0.74) + vec2(time * 0.009, time * 0.011);

                float violetBand = auroraBand(driftA, 0.05, 0.075 + widthLift, 1.65, time * 0.035);
                float cobaltBand = auroraBand(driftB, -0.08, 0.052 + widthLift, 2.1, 4.7 - time * 0.028);
                float cyanBand = auroraBand(driftC, 0.15, 0.062 + widthLift, 1.4, 8.3 + time * 0.022);
                float emeraldBand = auroraBand(driftB * 0.88, -0.18, 0.045 + widthLift * 0.7, 1.8, 12.6);
                float roseBand = auroraBand(driftA * 1.12, 0.24, 0.038 + widthLift * 0.45, 2.35, 16.2);

                vec3 color = vec3(0.0078, 0.0157, 0.0431);
                color = screenBlend(color, vec3(0.4667, 0.4196, 1.0) * violetBand * 0.54);
                color = screenBlend(color, vec3(0.2980, 0.4353, 1.0) * cobaltBand * 0.48);
                color = screenBlend(color, vec3(0.1961, 0.8471, 1.0) * cyanBand * 0.43);
                color = screenBlend(color, vec3(0.2392, 1.0, 0.8157) * emeraldBand * 0.25);
                color = screenBlend(color, vec3(1.0, 0.4353, 0.7843) * roseBand * 0.12);
                color = screenBlend(color, vec3(0.22, 0.64, 1.0) * localEnergy);

                float aperture = exp(-length(point * vec2(0.78, 1.2)) * 2.8);
                color += vec3(0.10, 0.23, 0.42) * aperture * convergence * 0.34;

                float radiance = max(max(color.r, color.g), color.b);
                color += color * smoothstep(0.42, 0.9, radiance) * 0.22;

                float vignette = 1.0 - smoothstep(0.18, 0.95, length(point * vec2(0.82, 1.0)));
                color *= 0.62 + vignette * 0.42;
                float grain = (hash21(gl_FragCoord.xy + floor(time * 24.0)) - 0.5)
                    * mix(0.012, 0.022, uQuality)
                    * mix(1.0, 0.35, convergence);
                color += grain;
                color = clamp(color, vec3(0.0), vec3(0.94));

                gl_FragColor = vec4(color, 0.96);
                #include <tonemapping_fragment>
                #include <colorspace_fragment>
            }
        `,
        depthTest: false,
        depthWrite: false
    });

    const geometry = new THREE.PlaneGeometry(2, 2);
    const mesh = new THREE.Mesh(geometry, material);
    mesh.frustumCulled = false;

    return {
        mesh,
        uniforms,
        resize(width, height) {
            uniforms.uResolution.value.set(width, height);
        },
        update(time, pointerX, pointerY, pointerEnergy, scroll) {
            uniforms.uTime.value = time;
            uniforms.uPointer.value.set(pointerX, pointerY);
            uniforms.uPointerEnergy.value = pointerEnergy;
            uniforms.uScroll.value = scroll;
        },
        dispose() {
            geometry.dispose();
            material.dispose();
        }
    };
}
```

- [ ] **Step 4: Run the focused Maven test and verify GREEN**

Expected: all current `HomePageTemplateContractTest` methods pass.

- [ ] **Step 5: Commit the shader**

```powershell
git add -- src/test/java/com/kingyurina/demo/web/HomePageTemplateContractTest.java src/main/resources/static/js/home-aurora-field.js
git commit -m "feat: render the aurora quant field"
```

---

### Task 4: Add the Renderer Lifecycle and Reversible UI Timeline

**Files:**
- Modify: `src/test/java/com/kingyurina/demo/web/HomePageTemplateContractTest.java`
- Modify: `src/main/resources/static/js/home-aurora.js`

**Interfaces:**
- Consumes: `readEnvironment`, `selectAuroraQuality`, `createSpring`,
  `stepSpring`, `normalizedScrollProgress`, `smoothstep`, and
  `createAuroraField`.
- Produces: the running renderer plus CSS custom properties
  `--aurora-progress`, `--pointer-x`, and `--pointer-y`, and state classes
  `is-ready`, `is-resolved`, and `is-webgl-fallback`.

- [ ] **Step 1: Add a failing renderer lifecycle contract**

Add:

```java
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
```

- [ ] **Step 2: Run the focused Maven test and verify RED**

Expected: FAIL because `home-aurora.js` does not exist.

- [ ] **Step 3: Implement the entry module with cached DOM and one frame loop**

Create `home-aurora.js` with this lifecycle:

```js
import { createAuroraField } from "./home-aurora-field.js";
import {
    createSpring,
    normalizedScrollProgress,
    smoothstep,
    stepSpring
} from "./home-aurora-motion.js";
import { readEnvironment, selectAuroraQuality } from "./home-aurora-quality.js";

const THREE_MODULE_URL = "/js/vendor/three.module.js";
const POINTER_ENERGY_SPRING = Object.freeze({ stiffness: 70, damping: 18, mass: 1 });
const SCROLL_SPRING = Object.freeze({ stiffness: 82, damping: 22, mass: 1 });
const stage = document.querySelector("[data-aurora-core]");
const canvas = document.querySelector("[data-aurora-canvas]");

if (stage && canvas) {
    startAuroraCore(stage, canvas).catch(() => {
        document.body.classList.add("is-webgl-fallback");
        stage.classList.add("is-ready", "has-metrics", "is-resolved");
    });
}

async function startAuroraCore(root, targetCanvas) {
    const THREE = await import(THREE_MODULE_URL);
    const quality = selectAuroraQuality(readEnvironment());
    const renderer = new THREE.WebGLRenderer({
        canvas: targetCanvas,
        antialias: false,
        alpha: true,
        powerPreference: quality.name === "low" ? "default" : "high-performance"
    });
    renderer.setClearColor(0x02040b, 0);
    renderer.outputColorSpace = THREE.SRGBColorSpace;
    renderer.toneMapping = THREE.ACESFilmicToneMapping;
    renderer.toneMappingExposure = 1;

    const scene = new THREE.Scene();
    const camera = new THREE.Camera();
    const field = createAuroraField(THREE, quality);
    scene.add(field.mesh);

    const metrics = Array.from(root.querySelectorAll("[data-aurora-metric]"));
    const progressBar = root.querySelector("[data-aurora-progress] span");
    const pointerX = createSpring(0.5);
    const pointerY = createSpring(0.5);
    const pointerEnergy = createSpring(0);
    const initialProgress = quality.name === "reduced" ? 1 : 0;
    const scrollSpring = createSpring(initialProgress);
    const state = {
        width: 0,
        height: 0,
        intersecting: true,
        running: false,
        animationFrame: 0,
        previousTime: performance.now(),
        elapsed: 0,
        pointerSampleX: window.innerWidth * 0.5,
        pointerSampleY: window.innerHeight * 0.5
    };

    const resize = () => {
        const rect = targetCanvas.getBoundingClientRect();
        const width = Math.max(1, Math.round(rect.width));
        const height = Math.max(1, Math.round(rect.height));
        if (width === state.width && height === state.height) {
            return;
        }
        state.width = width;
        state.height = height;
        renderer.setPixelRatio(quality.pixelRatio);
        renderer.setSize(width, height, false);
        field.resize(width * quality.pixelRatio, height * quality.pixelRatio);
    };

    const readScroll = () => {
        const rect = root.getBoundingClientRect();
        scrollSpring.target = quality.name === "reduced"
            ? 1
            : normalizedScrollProgress(rect.top, rect.height, window.innerHeight);
    };

    const readPointer = (event) => {
        if (!quality.pointerEnabled) {
            return;
        }
        const nextX = event.clientX / Math.max(1, window.innerWidth);
        const nextY = 1 - event.clientY / Math.max(1, window.innerHeight);
        const speed = Math.hypot(
            event.clientX - state.pointerSampleX,
            event.clientY - state.pointerSampleY
        );
        state.pointerSampleX = event.clientX;
        state.pointerSampleY = event.clientY;
        pointerX.target = nextX;
        pointerY.target = nextY;
        pointerEnergy.target = Math.min(1, 0.16 + speed / 54);
    };

    const clearPointer = () => {
        pointerX.target = 0.5;
        pointerY.target = 0.5;
        pointerEnergy.target = 0;
    };

    const projectInterfaceState = (progress) => {
        root.style.setProperty("--aurora-progress", progress.toFixed(4));
        root.style.setProperty("--pointer-x", `${(pointerX.value * 100).toFixed(2)}%`);
        root.style.setProperty("--pointer-y", `${((1 - pointerY.value) * 100).toFixed(2)}%`);
        root.classList.toggle("has-metrics", progress >= 0.24);
        root.classList.toggle("is-resolved", progress >= 0.74);
        for (let index = 0; index < metrics.length; index += 1) {
            const metric = metrics[index];
            metric.classList.toggle("is-visible", progress >= 0.28 + index * 0.08);
        }
        if (progressBar) {
            progressBar.style.transform = `scaleY(${progress.toFixed(4)})`;
        }
    };

    const stopFrameLoop = () => {
        if (state.animationFrame) {
            cancelAnimationFrame(state.animationFrame);
        }
        state.animationFrame = 0;
        state.running = false;
    };

    const ensureFrameLoop = () => {
        if (state.running || !state.intersecting || document.hidden) {
            return;
        }
        state.running = true;
        state.previousTime = performance.now();
        state.animationFrame = requestAnimationFrame(frame);
    };

    const synchronizePlayback = () => {
        if (state.intersecting && !document.hidden) {
            ensureFrameLoop();
        } else {
            stopFrameLoop();
        }
    };

    const frame = (now) => {
        if (!state.intersecting || document.hidden) {
            stopFrameLoop();
            return;
        }
        const deltaSeconds = Math.min((now - state.previousTime) / 1000, 1 / 20);
        state.previousTime = now;
        state.elapsed += deltaSeconds * quality.ambientSpeed;
        pointerEnergy.target *= Math.exp(-deltaSeconds * 5);
        stepSpring(pointerX, deltaSeconds);
        stepSpring(pointerY, deltaSeconds);
        stepSpring(pointerEnergy, deltaSeconds, POINTER_ENERGY_SPRING);
        stepSpring(scrollSpring, deltaSeconds, SCROLL_SPRING);

        const scroll = smoothstep(0, 1, scrollSpring.value);
        field.update(
            state.elapsed,
            pointerX.value,
            pointerY.value,
            pointerEnergy.value,
            scroll
        );
        projectInterfaceState(scroll);
        renderer.render(scene, camera);

        if (quality.name === "reduced") {
            stopFrameLoop();
            return;
        }
        state.animationFrame = requestAnimationFrame(frame);
    };

    const observer = new IntersectionObserver((entries) => {
        state.intersecting = entries.some((entry) => entry.isIntersecting);
        synchronizePlayback();
    }, { threshold: 0.01 });
    observer.observe(root);

    window.addEventListener("resize", () => {
        resize();
        ensureFrameLoop();
    }, { passive: true });
    window.addEventListener("scroll", readScroll, { passive: true });
    window.addEventListener("pointermove", readPointer, { passive: true });
    window.addEventListener("pointerleave", clearPointer, { passive: true });
    document.addEventListener("visibilitychange", synchronizePlayback);

    resize();
    readScroll();
    root.classList.add("is-ready");
    projectInterfaceState(initialProgress);
    ensureFrameLoop();
}
```

The single `querySelectorAll` call must remain the cached initialization shown
above. Do not move DOM queries into `frame()` or `projectInterfaceState()`.

- [ ] **Step 4: Run the focused Maven test and both Node tests**

Expected: Maven contract tests pass; Node reports `pass 6`, `fail 0`.

- [ ] **Step 5: Commit the lifecycle**

```powershell
git add -- src/test/java/com/kingyurina/demo/web/HomePageTemplateContractTest.java src/main/resources/static/js/home-aurora.js
git commit -m "feat: orchestrate the aurora core lifecycle"
```

---

### Task 5: Style the Glass Interface, Timeline, Fallback, and Responsive States

**Files:**
- Modify: `src/test/java/com/kingyurina/demo/web/HomePageTemplateContractTest.java`
- Modify: `src/main/resources/static/css/home-aurora.css`

**Interfaces:**
- Consumes: template classes and `--aurora-progress`, `--pointer-x`,
  `--pointer-y`, `is-ready`, `is-resolved`, `is-visible`, and
  `is-webgl-fallback`.
- Produces: the final first viewport, metric timeline, CSS fallback, mobile
  layout, focus states, and reduced-motion composition.

- [ ] **Step 1: Add a failing stylesheet contract**

Add:

```java
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
```

- [ ] **Step 2: Run the focused Maven test and verify RED**

Expected: FAIL because `home-aurora.css` does not exist.

- [ ] **Step 3: Create the complete page stylesheet**

The stylesheet must implement these exact tokens and rules:

```css
:root {
    color-scheme: dark;
    --aurora-void: #02040b;
    --aurora-elevated: #07101f;
    --aurora-violet: #776bff;
    --aurora-cobalt: #4c6fff;
    --aurora-cyan: #32d8ff;
    --aurora-emerald: #3dffd0;
    --aurora-rose: #ff6fc8;
    --aurora-text: #f7faff;
    --aurora-muted: #a7b4c8;
    --aurora-line: rgba(205, 225, 255, 0.16);
    --aurora-ease: cubic-bezier(0.16, 1, 0.3, 1);
}

* { box-sizing: border-box; }
html { background: var(--aurora-void); scroll-behavior: smooth; }
body { margin: 0; font-family: "Segoe UI", Arial, sans-serif; background: var(--aurora-void); }
a { color: inherit; text-decoration: none; }

.aurora-skip-link {
    position: fixed;
    z-index: 30;
    top: 12px;
    left: 12px;
    min-height: 44px;
    padding: 12px 16px;
    border-radius: 12px;
    color: #07101f;
    background: #f7faff;
    transform: translateY(-150%);
    transition: transform 180ms ease;
}
.aurora-skip-link:focus { transform: translateY(0); }
.aurora-story { position: relative; min-height: 280svh; background: var(--aurora-void); }
.aurora-viewport {
    position: sticky;
    top: 0;
    min-height: 100svh;
    overflow: hidden;
    color: var(--aurora-text);
    background: var(--aurora-void);
}
.aurora-canvas,
.aurora-fallback,
.aurora-atmosphere {
    position: absolute;
    inset: 0;
    width: 100%;
    height: 100%;
}
.aurora-canvas { z-index: 1; display: block; opacity: 0; transition: opacity 900ms var(--aurora-ease); }
.aurora-story.is-ready .aurora-canvas { opacity: 1; }
.aurora-fallback {
    z-index: 0;
    background:
        radial-gradient(circle at var(--pointer-x, 52%) var(--pointer-y, 46%), rgba(50,216,255,.22), transparent 28%),
        radial-gradient(ellipse at 24% 36%, rgba(119,107,255,.42), transparent 46%),
        radial-gradient(ellipse at 78% 30%, rgba(76,111,255,.34), transparent 48%),
        radial-gradient(ellipse at 58% 78%, rgba(61,255,208,.18), transparent 44%),
        linear-gradient(145deg, #02040b 8%, #07101f 54%, #030714 100%);
    filter: saturate(.92);
}
.aurora-atmosphere {
    z-index: 2;
    pointer-events: none;
    background:
        linear-gradient(180deg, rgba(2,4,11,.62), transparent 24%, transparent 72%, rgba(2,4,11,.74)),
        radial-gradient(circle at center, transparent 28%, rgba(0,2,9,.62) 100%);
}
.aurora-atmosphere::after {
    content: "";
    position: absolute;
    inset: -20%;
    opacity: .12;
    background-image: repeating-radial-gradient(circle at 30% 20%, rgba(255,255,255,.35) 0 1px, transparent 1px 4px);
    mix-blend-mode: soft-light;
    animation: aurora-grain 9s steps(6) infinite;
}
.aurora-nav,
.aurora-content { position: relative; z-index: 5; }
.aurora-nav {
    position: absolute;
    top: 22px;
    right: clamp(16px, 3vw, 48px);
    left: clamp(16px, 3vw, 48px);
    display: grid;
    grid-template-columns: auto 1fr auto;
    align-items: center;
    gap: 24px;
    min-height: 58px;
    padding: 0 22px;
    border: 1px solid var(--aurora-line);
    border-radius: 999px;
    background: rgba(4, 9, 22, .48);
    box-shadow: inset 0 1px rgba(255,255,255,.08), 0 16px 46px rgba(0,0,0,.18);
    backdrop-filter: blur(18px);
}
.aurora-brand { display: inline-flex; align-items: center; gap: 10px; min-height: 44px; font-family: Georgia, "Times New Roman", serif; }
.aurora-brand span { color: #9bdfff; font: 600 .72rem/1 "Segoe UI", Arial, sans-serif; letter-spacing: .12em; }
.aurora-nav nav { display: flex; justify-content: center; gap: clamp(12px, 2vw, 30px); color: var(--aurora-muted); font-size: .7rem; letter-spacing: .08em; text-transform: uppercase; }
.aurora-nav nav a,
.aurora-dashboard { display: inline-flex; align-items: center; min-height: 44px; transition: color 220ms ease, border-color 220ms ease, background 220ms ease, transform 220ms var(--aurora-ease); }
.aurora-nav nav a:hover { color: var(--aurora-text); }
.aurora-dashboard { padding: 0 17px; border: 1px solid rgba(155,223,255,.28); border-radius: 999px; background: rgba(8,18,39,.34); }
.aurora-dashboard:hover { border-color: rgba(155,223,255,.66); background: rgba(50,216,255,.1); transform: translateY(-1px); }
.aurora-content { min-height: 100svh; pointer-events: none; }
.aurora-content a { pointer-events: auto; }
.aurora-hero {
    position: absolute;
    inset: 0;
    display: grid;
    place-items: center;
    padding: 116px clamp(18px, 5vw, 80px) 126px;
}
.aurora-panel {
    position: relative;
    width: min(860px, calc(100vw - 36px));
    padding: clamp(30px, 5vw, 58px);
    overflow: hidden;
    border: 1px solid rgba(220,236,255,.2);
    border-radius: 28px;
    text-align: center;
    background:
        radial-gradient(circle at var(--pointer-x, 50%) var(--pointer-y, 45%), rgba(146,220,255,.12), transparent 38%),
        linear-gradient(145deg, rgba(14,24,50,.52), rgba(4,9,22,.28));
    box-shadow: inset 0 1px rgba(255,255,255,.16), 0 34px 100px rgba(0,0,0,.32);
    backdrop-filter: blur(22px);
    transform: translateY(calc(var(--aurora-progress, 0) * -5vh)) scale(calc(1 - var(--aurora-progress, 0) * .06));
    transition: border-color 500ms ease, background 500ms ease;
}
.aurora-panel::before {
    content: "";
    position: absolute;
    top: 0;
    right: 12%;
    left: 12%;
    height: 1px;
    background: linear-gradient(90deg, transparent, rgba(255,255,255,.82), transparent);
}
.aurora-status {
    display: inline-flex;
    align-items: center;
    gap: 10px;
    margin: 0 0 18px;
    color: #b9eaff;
    font-size: .67rem;
    font-weight: 600;
    letter-spacing: .2em;
}
.aurora-status span { width: 7px; height: 7px; border-radius: 50%; background: var(--aurora-emerald); box-shadow: 0 0 18px rgba(61,255,208,.78); }
.aurora-panel h1 {
    max-width: 13.5ch;
    margin: 0 auto;
    font-family: Georgia, "Times New Roman", serif;
    font-size: clamp(3.4rem, 6vw, 6.2rem);
    font-weight: 500;
    line-height: .91;
    letter-spacing: -.045em;
    text-wrap: balance;
    text-shadow: 0 8px 38px rgba(0,0,0,.3);
}
.aurora-copy-stage { display: grid; place-items: center; max-width: 560px; min-height: 58px; margin: 24px auto 30px; }
.aurora-copy-stage > p { grid-area: 1 / 1; margin: 0; color: var(--aurora-muted); font-size: clamp(.98rem, 1.5vw, 1.14rem); line-height: 1.65; text-wrap: balance; transition: opacity 420ms var(--aurora-ease), transform 420ms var(--aurora-ease); }
.aurora-resolved-copy { opacity: 0; transform: translateY(8px); }
.aurora-primary-action {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    gap: 12px;
    min-height: 52px;
    padding: 0 22px;
    border: 1px solid rgba(220,240,255,.48);
    border-radius: 999px;
    color: #06101e;
    font-size: .88rem;
    font-weight: 700;
    background: linear-gradient(110deg, #f7faff, #bdefff);
    box-shadow: 0 10px 34px rgba(50,216,255,.18);
    transition: transform 220ms var(--aurora-ease), box-shadow 220ms ease;
}
.aurora-primary-action svg { width: 18px; fill: none; stroke: currentColor; stroke-width: 1.8; }
.aurora-primary-action:hover { transform: translateY(-2px); box-shadow: 0 16px 44px rgba(50,216,255,.28); }
.aurora-primary-action:active { transform: scale(.98); }
.aurora-home :focus-visible { outline: 2px solid #b9eaff; outline-offset: 4px; }
.aurora-metric-stage { position: absolute; right: 5vw; bottom: 54px; left: 5vw; }
.aurora-metric-rail {
    display: grid;
    grid-template-columns: repeat(4, minmax(0, 1fr));
    width: min(1040px, 100%);
    margin: 0 auto;
    padding: 0;
    overflow: hidden;
    border: 1px solid var(--aurora-line);
    border-radius: 20px;
    list-style: none;
    background: rgba(4,9,22,.48);
    box-shadow: inset 0 1px rgba(255,255,255,.08), 0 20px 54px rgba(0,0,0,.22);
    backdrop-filter: blur(18px);
    opacity: 0;
    transform: translateY(14px);
    transition: opacity 420ms var(--aurora-ease), transform 420ms var(--aurora-ease);
}
.aurora-story.has-metrics .aurora-metric-rail { opacity: 1; transform: none; }
.aurora-metric-rail li {
    position: relative;
    min-width: 0;
    padding: 16px 20px;
    opacity: 0;
    transform: translateY(16px);
    transition: opacity 520ms var(--aurora-ease), transform 520ms var(--aurora-ease);
}
.aurora-metric-rail li + li { border-left: 1px solid var(--aurora-line); }
.aurora-metric-rail li.is-visible { opacity: 1; transform: none; }
.aurora-metric-rail span,
.aurora-metric-rail em { display: block; overflow: hidden; color: var(--aurora-muted); font: 500 .65rem/1.3 ui-monospace, monospace; letter-spacing: .09em; text-overflow: ellipsis; text-transform: uppercase; white-space: nowrap; }
.aurora-metric-rail strong { display: block; margin: 7px 0 4px; font-family: Georgia, "Times New Roman", serif; font-size: clamp(1.45rem, 2vw, 2rem); font-weight: 500; }
.aurora-metric-rail small { font: 500 .7rem/1 "Segoe UI", Arial, sans-serif; }
.aurora-scroll-cue,
.aurora-progress,
.aurora-disclosure { position: absolute; z-index: 7; }
.aurora-scroll-cue { bottom: 28px; left: 50%; display: grid; justify-items: center; gap: 8px; color: var(--aurora-muted); font-size: .62rem; letter-spacing: .16em; text-transform: uppercase; opacity: calc(1 - min(1, var(--aurora-progress, 0) * 8)); transform: translateX(-50%); }
.aurora-scroll-cue span { width: 1px; height: 22px; background: linear-gradient(#b9eaff, transparent); animation: aurora-cue 1.6s ease-in-out infinite; }
.aurora-progress { top: 50%; right: 18px; width: 2px; height: 110px; background: rgba(255,255,255,.12); transform: translateY(-50%); }
.aurora-progress span { display: block; width: 100%; height: 100%; background: linear-gradient(var(--aurora-cyan), var(--aurora-violet)); transform: scaleY(0); transform-origin: top; }
.aurora-disclosure { right: 24px; bottom: 18px; margin: 0; color: rgba(190,205,225,.6); font-size: .58rem; }
.aurora-visually-hidden { position: absolute; width: 1px; height: 1px; padding: 0; overflow: hidden; clip: rect(0,0,0,0); white-space: nowrap; border: 0; }
.aurora-story.is-resolved .aurora-panel { border-color: rgba(185,234,255,.32); background: linear-gradient(145deg, rgba(12,28,54,.42), rgba(3,8,20,.24)); }
.aurora-story.is-resolved .aurora-summary { opacity: 0; transform: translateY(-8px); }
.aurora-story.is-resolved .aurora-resolved-copy { opacity: 1; transform: none; }
.aurora-story.is-resolved .aurora-primary-action { box-shadow: 0 14px 48px rgba(50,216,255,.3); }
.aurora-home.is-webgl-fallback .aurora-canvas { display: none; }
.aurora-home.is-webgl-fallback .aurora-metric-rail,
.aurora-home.is-webgl-fallback .aurora-metric-rail li { opacity: 1; transform: none; }
.aurora-home.is-webgl-fallback .aurora-fallback { animation: aurora-fallback-drift 14s ease-in-out infinite alternate; }

@keyframes aurora-cue { 0%,100% { opacity:.25; transform:scaleY(.65); } 50% { opacity:1; transform:scaleY(1); } }
@keyframes aurora-grain { 0% { transform:translate3d(-3%, -2%, 0); } 50% { transform:translate3d(3%, 2%, 0); } 100% { transform:translate3d(-1%, 4%, 0); } }
@keyframes aurora-fallback-drift { from { transform:scale(1.02) translate3d(-1%,0,0); } to { transform:scale(1.08) translate3d(1%,-1%,0); } }

@media (max-width: 900px) {
    .aurora-story { min-height: 220svh; }
    .aurora-nav { grid-template-columns: 1fr auto; gap: 0 14px; padding: 7px 14px 5px; border-radius: 24px; }
    .aurora-nav nav {
        grid-column: 1 / -1;
        justify-content: flex-start;
        gap: 22px;
        overflow-x: auto;
        scrollbar-width: none;
    }
    .aurora-nav nav::-webkit-scrollbar { display: none; }
    .aurora-hero { align-items: start; padding: 148px 14px 196px; }
    .aurora-panel { width: 100%; padding: 32px 20px; border-radius: 22px; }
    .aurora-panel h1 { font-size: clamp(3.1rem, 14vw, 5rem); }
    .aurora-status { font-size: .57rem; letter-spacing: .12em; }
    .aurora-metric-stage { right: 14px; bottom: 62px; left: 14px; }
    .aurora-metric-rail { grid-template-columns: repeat(2, minmax(0, 1fr)); }
    .aurora-metric-rail li { padding: 12px 14px; }
    .aurora-metric-rail li + li { border-left: 0; }
    .aurora-metric-rail li:nth-child(even) { border-left: 1px solid var(--aurora-line); }
    .aurora-metric-rail li:nth-child(n+3) { border-top: 1px solid var(--aurora-line); }
    .aurora-progress { display: none; }
    .aurora-disclosure { right: 14px; bottom: 16px; }
}

@media (prefers-reduced-motion: reduce) {
    html { scroll-behavior: auto; }
    .aurora-story { min-height: 100svh; }
    .aurora-canvas,
    .aurora-fallback,
    .aurora-atmosphere::after,
    .aurora-panel,
    .aurora-primary-action,
    .aurora-copy-stage > p,
    .aurora-metric-rail,
    .aurora-metric-rail li { animation: none !important; transition-duration: .01ms !important; }
    .aurora-panel { transform: none; }
    .aurora-metric-rail { opacity: 1; transform: none; }
    .aurora-metric-rail li { opacity: 1; transform: none; }
    .aurora-scroll-cue,
    .aurora-progress { display: none; }
}
```

- [ ] **Step 4: Run the focused Maven test and verify GREEN**

Expected: all homepage contract methods pass.

- [ ] **Step 5: Commit the complete interface styling**

```powershell
git add -- src/test/java/com/kingyurina/demo/web/HomePageTemplateContractTest.java src/main/resources/static/css/home-aurora.css
git commit -m "feat: style the aurora intelligence interface"
```

---

### Task 6: Remove the Rejected Laboratory Runtime and Verify the Complete Page

**Files:**
- Delete: `src/main/resources/static/css/home-cinematic.css`
- Delete: `src/main/resources/static/js/home-cinematic-3d.js`
- Delete: `src/main/resources/static/js/home-cinematic-engine.js`
- Delete: `src/main/resources/static/js/home-cinematic-lab.js`
- Delete: `src/main/resources/static/js/home-cinematic-particles.js`
- Delete: `src/main/resources/static/js/home-cinematic-quality.js`
- Delete: `src/main/resources/static/images/cinematic-laboratory-bg.png`
- Modify only if a failing test or browser defect requires it:
  `index.html`, `home-aurora.css`, `home-aurora.js`,
  `home-aurora-field.js`, `home-aurora-motion.js`,
  `home-aurora-quality.js`, or the two test locations.

**Interfaces:**
- Consumes: the complete page from Tasks 1-5.
- Produces: a branch with no loaded legacy runtime, fresh automated evidence,
  and desktop/tablet/mobile/reduced-motion browser evidence.

- [ ] **Step 1: Add a failing source-level legacy deletion contract**

Add `java.nio.file.Files` and `java.nio.file.Path` imports, then add:

```java
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
```

- [ ] **Step 2: Run the focused test and verify RED**

Expected: FAIL because the seven superseded source files still exist.

- [ ] **Step 3: Delete only the seven superseded tracked assets listed above**

Use `apply_patch` delete hunks for text files and a native file deletion for
the tracked PNG after verifying its absolute path remains under the worktree.
Do not delete `three.module.js`, `king-yurina-reference-hero.png`, unrelated
images, or any user-owned untracked files.

- [ ] **Step 4: Run all focused automated checks**

Run:

```powershell
node --experimental-default-type=module --test src/test/js/home-aurora-motion.test.mjs src/test/js/home-aurora-quality.test.mjs
```

Expected: `tests 6`, `pass 6`, `fail 0`.

Run:

```powershell
mvn.cmd "-Dmaven.repo.local=.m2/repository" -Dtest=HomePageTemplateContractTest test
```

Expected: all homepage contract tests pass.

- [ ] **Step 5: Run the full clean regression suite**

Run:

```powershell
mvn.cmd "-Dmaven.repo.local=.m2/repository" clean test
```

Expected: `BUILD SUCCESS`, with no failures or errors.

- [ ] **Step 6: Start this worktree on port 8081 and validate it in a real browser**

From `C:\dev\codex-demo\.worktrees\cinematic-lab-homepage`, run:

```powershell
mvn.cmd "-Dmaven.repo.local=.m2/repository" "-Dspring-boot.run.arguments=--server.port=8081" spring-boot:run
```

Keep that yielded process running during QA and open
`http://127.0.0.1:8081/`. This command compiles and serves the feature
worktree directly; do not use the root checkout’s server helper.

Validate:

- `1440x900`: first viewport, pointer bloom, full forward and reverse scroll.
- `1920x1080`: no exposed edges and centered composition.
- `768x1024`: metric rail and navigation do not overlap.
- `390x844`: two-column metrics, readable headline, accessible CTA.
- Reduced-motion emulation: normal `100svh`, metrics visible, no continuous
  parallax or pointer response.
- Keyboard: skip link, menu, Dashboard, and primary action show focus.
- WebGL failure: add `is-webgl-fallback` in DevTools and confirm semantic
  content remains readable over the CSS aurora.
- Console: no uncaught exception, failed asset request, or external runtime
  request.

Capture screenshots for desktop first, middle, final, and mobile first
viewport under `tmp/aurora-qa/`. These are QA artifacts and must remain
untracked. Stop the yielded Maven process after the browser pass.

- [ ] **Step 7: Convert every browser defect into a failing contract or focused reproduction before changing production code**

For a semantic or source defect, add a failing JUnit or Node assertion. For a
purely visual overlap, record the viewport and scroll fraction in
`tmp/aurora-qa/visual-findings.md`, reproduce it in the browser, apply the
smallest CSS correction, and recapture the same viewport.

- [ ] **Step 8: Re-run the affected focused checks and the full Maven suite**

Expected: Node `pass 6`, Maven `BUILD SUCCESS`, and no unresolved browser
finding.

- [ ] **Step 9: Commit the verified final page**

```powershell
git add -- `
  src/main/resources/templates/index.html `
  src/main/resources/static/css/home-aurora.css `
  src/main/resources/static/css/home-cinematic.css `
  src/main/resources/static/js/home-aurora.js `
  src/main/resources/static/js/home-aurora-field.js `
  src/main/resources/static/js/home-aurora-motion.js `
  src/main/resources/static/js/home-aurora-quality.js `
  src/main/resources/static/js/home-cinematic-3d.js `
  src/main/resources/static/js/home-cinematic-engine.js `
  src/main/resources/static/js/home-cinematic-lab.js `
  src/main/resources/static/js/home-cinematic-particles.js `
  src/main/resources/static/js/home-cinematic-quality.js `
  src/main/resources/static/images/cinematic-laboratory-bg.png `
  src/test/java/com/kingyurina/demo/web/HomePageTemplateContractTest.java `
  src/test/js/home-aurora-motion.test.mjs `
  src/test/js/home-aurora-quality.test.mjs
git commit -m "feat: deliver the aurora quant core homepage"
```

Do not stage `tmp/`, `.m2/`, server logs, screenshots, or unrelated files.
