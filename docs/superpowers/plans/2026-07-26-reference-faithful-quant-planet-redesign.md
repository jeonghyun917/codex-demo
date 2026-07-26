# Reference-Faithful Quant Planet Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild `/` as a complete centered Quant miniature world with Messenger-matched entry, rotation, central product selection, and lower action placement while routing the six central selections to Quant AI subpages.

**Architecture:** Add a small pure composition module that owns product metadata, camera fitting, route selection, and reference-calibrated timing. The existing Three.js world remains original Quant geometry but gains a denser silhouette and bounds-based camera framing. DOM typography provides the large accessible central title; the runtime couples it to the same drag/idle state that rotates the world.

**Tech Stack:** Java 21, Spring Boot, Thymeleaf, CSS, vanilla ES modules, self-hosted Three.js r165, self-hosted Motion 12.29.2, Node test runner, Maven/JUnit.

## Global Constraints

- Implement `docs/superpowers/specs/2026-07-26-reference-faithful-quant-planet-redesign.md`.
- Work only on `/` in the existing `codex/quant-planet-homepage` worktree.
- Preserve every subpage, controller, vendor asset, license, and unrelated user file.
- Match Messenger's perceived first-entry staging, centered-world scale, slow rotation, central selection, and lower-button placement.
- Use original Quant AI geometry, typography, copy, colors, and routes; copy no Messenger source code, model, texture, sound, logo, or branded asset.
- Keep exactly one yellow `ENTER QUANT` action linked to `/quant`.
- Keep these six route destinations exact:
  - `/quant`
  - `/stocks`
  - `/stocks/heatmap?index=SP500`
  - `/etfs`
  - `/atelier`
  - `/signals/backtest`
- The six route selections remain native keyboard-focusable anchors.
- Use no React, Svelte, npm frontend runtime, remote runtime asset, external homepage API request, multiplayer, game controls, or autoplay audio.
- Wheel input performs no scroll, zoom, or rotation.
- Intro hard deadline remains `4000ms`; BFCache return never replays it.
- Keep pixel ratio at or below `1.5`, high draw calls at or below `75`, visible triangles at or below `140000`, and loopback average submission at or below `12.0ms`.
- Reduced motion, no JavaScript, missing Motion, WebGL failure, and context loss retain a complete static planet, visible central title, one primary action, and all six routes.
- Validate `375×812`, `768×1024`, `1024×768`, and `1440×900` with no document overflow.
- Use strict TDD: every production behavior begins with a focused failing test.
- Use `mvn.cmd "-Dmaven.repo.local=C:\dev\codex-demo\.m2\repository" test` in this isolated worktree.

## File Map

### Create

- `src/main/resources/static/js/home-quant-planet-composition.js`
- `src/test/js/home-quant-planet-composition.test.mjs`

### Modify

- `src/main/resources/static/js/home-quant-planet-motion.js`
- `src/main/resources/static/js/home-quant-planet-world.js`
- `src/main/resources/static/js/home-quant-planet-scene.js`
- `src/main/resources/static/js/home-quant-planet-input.js`
- `src/main/resources/static/js/home-quant-planet-runtime.js`
- `src/main/resources/templates/index.html`
- `src/main/resources/static/css/home-quant-planet.css`
- `src/test/js/home-quant-planet-motion.test.mjs`
- `src/test/js/home-quant-planet-world.test.mjs`
- `src/test/js/home-quant-planet-input.test.mjs`
- `src/test/js/home-quant-planet-runtime.test.mjs`
- `src/test/java/com/kingyurina/demo/web/HomePageTemplateContractTest.java`

### Preserve

- `src/main/java/com/kingyurina/demo/web/HomeController.java`
- all subpage source and templates;
- `src/main/resources/static/js/vendor/three.module.js`;
- `src/main/resources/static/vendor/motion/*`;
- `src/main/resources/static/vendor/lenis/*`;
- all font and license files;
- world rollback and exactly-once resource disposal.

---

### Task 1: Pure Composition, Camera Fit, and Product Selection

**Files:**
- Create: `src/main/resources/static/js/home-quant-planet-composition.js`
- Create: `src/test/js/home-quant-planet-composition.test.mjs`
- Modify: `src/main/resources/static/js/home-quant-planet-motion.js`
- Modify: `src/test/js/home-quant-planet-motion.test.mjs`

**Interfaces:**
- Produces: `PRODUCT_SCENES`, `REFERENCE_TIMING`, `fitPerspectiveSphere`, `createProductSelection`, `resolveSelectedProduct`, `resolveAdjacentProduct`.
- `fitPerspectiveSphere({ center, radius, aspect, verticalFovDegrees, margin })` returns `{ target, radius, distance, limitingFovRadians }`.
- `createProductSelection({ rotationAngle, dragAngle, selectedIndex, snap, count })` returns `{ selectedIndex, frontIndex, baseAngle, items }`.
- `resolveSelectedProduct(index)` returns one immutable product scene with `route`, `lines`, and `supporting`.

- [ ] **Step 1: Write failing composition tests**

Add literal behavior tests:

```js
import test from "node:test";
import assert from "node:assert/strict";
import {
    PRODUCT_SCENES,
    REFERENCE_TIMING,
    createProductSelection,
    fitPerspectiveSphere,
    resolveAdjacentProduct,
    resolveSelectedProduct
} from "../../main/resources/static/js/home-quant-planet-composition.js";

test("defines the six original Quant product scenes and exact routes", () => {
    assert.deepEqual(PRODUCT_SCENES.map(({ route }) => route), [
        "/quant",
        "/stocks",
        "/stocks/heatmap?index=SP500",
        "/etfs",
        "/atelier",
        "/signals/backtest"
    ]);
    assert.deepEqual(resolveSelectedProduct(2).lines, ["MARKET", "HEATMAP"]);
    assert.equal(resolveSelectedProduct(5).supporting, "Backtest field");
});

test("fits a complete sphere against the stricter viewport field of view", () => {
    const landscape = fitPerspectiveSphere({
        center: { x: 0, y: .4, z: 0 },
        radius: 3.8,
        aspect: 16 / 9,
        verticalFovDegrees: 34,
        margin: 1.18
    });
    const portrait = fitPerspectiveSphere({
        center: { x: 0, y: .4, z: 0 },
        radius: 3.8,
        aspect: 375 / 812,
        verticalFovDegrees: 34,
        margin: 1.18
    });
    assert.deepEqual(landscape.target, { x: 0, y: .4, z: 0 });
    assert.ok(landscape.distance > 3.8);
    assert.ok(portrait.distance > landscape.distance);
});

test("snaps drag selection to one of six wrapped product scenes", () => {
    const state = createProductSelection({
        rotationAngle: 0,
        dragAngle: Math.PI * .34,
        selectedIndex: 0,
        snap: true,
        count: 6
    });
    assert.equal(Number.isInteger(state.selectedIndex), true);
    assert.equal(state.items.length, 6);
    assert.equal(resolveAdjacentProduct(0, -1).route, "/signals/backtest");
});

test("names reference-calibrated timing constants", () => {
    assert.equal(REFERENCE_TIMING.introDeadlineMs, 4000);
    assert.ok(REFERENCE_TIMING.worldRevolutionMs >= 60000);
    assert.ok(REFERENCE_TIMING.productRevolutionMs >= 20000);
});
```

- [ ] **Step 2: Run the new test and verify RED**

Run:

```powershell
node --test src/test/js/home-quant-planet-composition.test.mjs
```

Expected: `ERR_MODULE_NOT_FOUND` for `home-quant-planet-composition.js`.

- [ ] **Step 3: Implement the product scene contract**

Create immutable metadata:

```js
export const PRODUCT_SCENES = Object.freeze([
    Object.freeze({
        route: "/quant",
        lines: Object.freeze(["QUANT", "INTELLIGENCE"]),
        supporting: "Observe · Resolve · Act"
    }),
    Object.freeze({
        route: "/stocks",
        lines: Object.freeze(["STOCK", "SIGNALS"]),
        supporting: "Market field"
    }),
    Object.freeze({
        route: "/stocks/heatmap?index=SP500",
        lines: Object.freeze(["MARKET", "HEATMAP"]),
        supporting: "S&P 500 field"
    }),
    Object.freeze({
        route: "/etfs",
        lines: Object.freeze(["ETF", "RADAR"]),
        supporting: "Allocation field"
    }),
    Object.freeze({
        route: "/atelier",
        lines: Object.freeze(["STRATEGY", "ATELIER"]),
        supporting: "Research field"
    }),
    Object.freeze({
        route: "/signals/backtest",
        lines: Object.freeze(["SIGNAL", "LAB"]),
        supporting: "Backtest field"
    })
]);

export const REFERENCE_TIMING = Object.freeze({
    introDeadlineMs: 4000,
    worldRevolutionMs: 96000,
    productRevolutionMs: 28000,
    selectionSnapMs: 620,
    idleResumeMs: 900
});
```

`worldRevolutionMs` is the initial calibrated estimate. Task 6 may adjust only
this named value after a synchronized comparison.

- [ ] **Step 4: Implement conservative sphere fitting**

Use the smaller horizontal/vertical field of view:

```js
const radians = (degrees) => degrees * Math.PI / 180;

export function fitPerspectiveSphere({
    center = { x: 0, y: 0, z: 0 },
    radius = 1,
    aspect = 1,
    verticalFovDegrees = 34,
    margin = 1.18
} = {}) {
    const safeRadius = Math.max(.001, Number(radius) || 1);
    const safeAspect = Math.max(.1, Number(aspect) || 1);
    const vertical = radians(verticalFovDegrees);
    const horizontal = 2 * Math.atan(Math.tan(vertical / 2) * safeAspect);
    const limitingFovRadians = Math.min(vertical, horizontal);
    const framedRadius = safeRadius * Math.max(1, Number(margin) || 1);
    return {
        target: {
            x: Number(center.x) || 0,
            y: Number(center.y) || 0,
            z: Number(center.z) || 0
        },
        radius: safeRadius,
        distance: framedRadius / Math.sin(limitingFovRadians / 2),
        limitingFovRadians
    };
}
```

- [ ] **Step 5: Implement deterministic product selection**

Use six equal angular sectors. `snap: true` selects the nearest sector, wraps
the index, and emits stable depth values for previous/current/next labels.
Reuse the existing `wrapIndex` behavior rather than duplicating modulo logic.

- [ ] **Step 6: Move timing consumers to `REFERENCE_TIMING`**

Import `REFERENCE_TIMING` from the composition module in
`home-quant-planet-motion.js`. Replace the anonymous `28000` ring duration with
`REFERENCE_TIMING.productRevolutionMs`. Do not change intro or lifecycle
behavior in this task.

- [ ] **Step 7: Run focused and relevant suites**

```powershell
node --test src/test/js/home-quant-planet-composition.test.mjs
node --test src/test/js/home-quant-planet-composition.test.mjs src/test/js/home-quant-planet-motion.test.mjs
```

Expected: all tests pass with no warnings.

- [ ] **Step 8: Commit**

```powershell
git add src/main/resources/static/js/home-quant-planet-composition.js src/main/resources/static/js/home-quant-planet-motion.js src/test/js/home-quant-planet-composition.test.mjs src/test/js/home-quant-planet-motion.test.mjs
git commit -m "feat: define reference planet composition"
```

---

### Task 2: Complete World Silhouette and Bounds-Based Camera

**Files:**
- Modify: `src/main/resources/static/js/home-quant-planet-world.js`
- Modify: `src/main/resources/static/js/home-quant-planet-scene.js`
- Modify: `src/test/js/home-quant-planet-world.test.mjs`
- Modify: `src/test/java/com/kingyurina/demo/web/HomePageTemplateContractTest.java`

**Interfaces:**
- Consumes: `fitPerspectiveSphere`, `REFERENCE_TIMING`.
- Produces: `world.getFramingEnvelope()` returning `{ center, radius }`.
- Scene keeps `resize`, `setPointer`, `setDragAngle`, `setVisible`, `render`, `getDiagnostics`, and `dispose`.

- [ ] **Step 1: Add failing world-envelope and camera-contract tests**

Add a world test asserting:

```js
test("exposes a complete framing envelope that includes upper landmarks", (t) => {
    const world = createWorld(t);
    const envelope = world.getFramingEnvelope();
    assert.ok(envelope.radius >= 3.6);
    assert.ok(envelope.center.y > 0);
    assert.ok(envelope.top >= 3.2);
    assert.ok(envelope.bottom <= -2.4);
});
```

Extend the Java source contract so the scene must contain
`fitPerspectiveSphere`, `getFramingEnvelope`, and an aspect-dependent resize
fit, and must not contain the fixed `cameraDistance: 8.9` framing contract.

- [ ] **Step 2: Run focused tests and verify RED**

```powershell
node --test src/test/js/home-quant-planet-world.test.mjs
mvn.cmd "-Dmaven.repo.local=C:\dev\codex-demo\.m2\repository" "-Dtest=HomePageTemplateContractTest#homepageBuildsAnOriginalLocalQuantPlanetWorld" test
```

Expected: missing `getFramingEnvelope` and camera-fit source assertions.

- [ ] **Step 3: Rebalance the original planet silhouette**

Change the land/water form from the rejected oblate front plate toward a
compact world:

```js
scale: [2.82, 2.82, 2.82]
```

Keep landmarks on the upper hemisphere, but move their x/z placement so their
outer edges form a readable irregular contour. Preserve their names and
animations.

- [ ] **Step 4: Add instanced contour density**

Using existing box, cone, sphere, paper, rock, groundShade, coral, and ink
resources, add:

- `12` instanced `ContourSignalFragments`;
- `8` instanced `PerimeterUtilityModules`;
- `10` instanced `AtmosphericSpecks`.

Distribute them around radii `3.0` to `3.75` with deterministic angles. Use
shared geometry/materials; do not add a texture or per-item draw call.

- [ ] **Step 5: Expose the framing envelope**

After all visible children are attached, compute the envelope once:

```js
const framingBox = new THREE.Box3().setFromObject(planet);
const framingSphere = framingBox.getBoundingSphere(new THREE.Sphere());

function getFramingEnvelope() {
    return {
        center: {
            x: framingSphere.center.x,
            y: framingSphere.center.y,
            z: framingSphere.center.z
        },
        radius: framingSphere.radius,
        top: framingBox.max.y,
        bottom: framingBox.min.y
    };
}
```

Return `getFramingEnvelope` without exposing mutable Three.js objects.

- [ ] **Step 6: Fit the camera on every resize**

In the scene:

```js
const envelope = world.getFramingEnvelope();
let framing = fitPerspectiveSphere({
    center: envelope.center,
    radius: envelope.radius,
    aspect: 1,
    verticalFovDegrees: 34,
    margin: 1.18
});
```

Recompute `framing` after `camera.aspect` changes. Orbit the camera around
`framing.target` using the current yaw/pitch and `framing.distance`. Remove the
fixed `cameraDistance` dependency from visual framing; keep orbit pose yaw,
pitch, planet rotation, and pointer bounds.

- [ ] **Step 7: Verify budgets, rollback, and disposal**

```powershell
node --test src/test/js/home-quant-planet-world.test.mjs
node --test src/test/js/home-quant-planet-composition.test.mjs src/test/js/home-quant-planet-motion.test.mjs src/test/js/home-quant-planet-world.test.mjs
mvn.cmd "-Dmaven.repo.local=C:\dev\codex-demo\.m2\repository" "-Dtest=HomePageTemplateContractTest#homepageBuildsAnOriginalLocalQuantPlanetWorld" test
```

Expected: framing, render-budget, construction-rollback, and exactly-once
disposal tests pass.

- [ ] **Step 8: Commit**

```powershell
git add src/main/resources/static/js/home-quant-planet-world.js src/main/resources/static/js/home-quant-planet-scene.js src/test/js/home-quant-planet-world.test.mjs src/test/java/com/kingyurina/demo/web/HomePageTemplateContractTest.java
git commit -m "feat: frame the complete quant world"
```

---

### Task 3: Dominant Central Product Titles and Single Lower Action

**Files:**
- Modify: `src/main/resources/templates/index.html`
- Modify: `src/main/resources/static/css/home-quant-planet.css`
- Modify: `src/test/java/com/kingyurina/demo/web/HomePageTemplateContractTest.java`

**Interfaces:**
- Template produces six `[data-product-title-link]` native anchors.
- Each title anchor contains `[data-title-line]` spans and
  `[data-title-supporting]`.
- Runtime later sets `data-product-position="active|previous|next|hidden"`.
- The only primary CTA is `[data-enter-quant]` linked to `/quant`.

- [ ] **Step 1: Write a failing semantic composition contract**

Add a focused Java test that parses rendered HTML and verifies:

- exactly six native `a[data-product-title-link]`;
- exact route order;
- exact two-line title text and supporting copy from `PRODUCT_SCENES`;
- no `.product-orbit__link` pill markup;
- exactly one `a[data-enter-quant]` with text `ENTER QUANT` and `/quant`;
- no top-right primary CTA;
- visible central title markup is outside the visually hidden semantic metrics;
- CSS includes `-webkit-text-stroke`, layered title shadows, a centered lower
  CTA, and no pill border-radius rule.

- [ ] **Step 2: Run the focused contract and verify RED**

```powershell
mvn.cmd "-Dmaven.repo.local=C:\dev\codex-demo\.m2\repository" "-Dtest=HomePageTemplateContractTest#homepageUsesDominantReferenceProductTitles" test
```

Expected: old pill navigation and top-right CTA fail the new contract.

- [ ] **Step 3: Replace the orbit pills with six title anchors**

Use this structure for each route:

```html
<a class="product-title"
   data-product-title-link
   data-product-position="active"
   href="/quant"
   aria-label="Open Quant Intelligence">
    <span class="product-title__lines" aria-hidden="true">
        <span data-title-line data-extrude="QUANT">QUANT</span>
        <span data-title-line data-extrude="INTELLIGENCE">INTELLIGENCE</span>
    </span>
    <span class="product-title__supporting"
          data-title-supporting>Observe · Resolve · Act</span>
</a>
```

Repeat with exact scene content. Only the first begins active; runtime owns
subsequent position state.

- [ ] **Step 4: Move semantics and CTA**

- Keep `h1`, narrative, and metrics in a dedicated accessible summary that may
  be visually hidden in WebGL mode.
- Never hide `.product-title`.
- Remove the top-right `Enter Quant`.
- Add one lower-center:

```html
<a class="planet-primary-action"
   data-enter-quant href="/quant">ENTER QUANT</a>
```

- [ ] **Step 5: Implement the reference-style title surface**

Title rules must:

- center the active title over the planet;
- use font size `clamp(2.7rem, 7.2vw, 7.6rem)`;
- cap line width to the planet safe region;
- use paper fill, ink stroke, and stepped extrusion shadows;
- place previous/next titles near the left/right planet edge at reduced scale
  and opacity;
- visually hide only non-neighboring titles while leaving them keyboard
  focusable through an offscreen technique;
- promote a focused title to the active visual state;
- avoid rounded pill backgrounds and detached button borders.

- [ ] **Step 6: Implement the lower reference action**

The yellow action is horizontally centered, below the complete planet, uses a
subtle trapezoid/perspective transform, and remains at least `44×44px`. On
mobile it stays inside the safe area without overlapping the planet.

- [ ] **Step 7: Run contract tests**

```powershell
mvn.cmd "-Dmaven.repo.local=C:\dev\codex-demo\.m2\repository" "-Dtest=HomePageTemplateContractTest" test
```

Expected: all homepage semantic, fallback, vendor-preservation, and new visual
composition contracts pass.

- [ ] **Step 8: Commit**

```powershell
git add src/main/resources/templates/index.html src/main/resources/static/css/home-quant-planet.css src/test/java/com/kingyurina/demo/web/HomePageTemplateContractTest.java
git commit -m "feat: center the quant product titles"
```

---

### Task 4: Couple Drag, Selection, Titles, and World Rotation

**Files:**
- Modify: `src/main/resources/static/js/home-quant-planet-input.js`
- Modify: `src/main/resources/static/js/home-quant-planet-runtime.js`
- Modify: `src/test/js/home-quant-planet-input.test.mjs`
- Modify: `src/test/js/home-quant-planet-runtime.test.mjs`

**Interfaces:**
- Consumes: `createProductSelection`, `resolveSelectedProduct`,
  `resolveAdjacentProduct`, `REFERENCE_TIMING`.
- Input `onChange` continues to emit pointer, drag, paused, pressed, focused,
  and hovered state.
- Input adds `onCommit({ selectedIndex, dragAngle })`.
- Runtime writes `data-product-position`, `aria-current`, and
  `data-selected-product`.

- [ ] **Step 1: Write failing selection-commit tests**

Add input tests proving:

- a horizontal primary drag crosses product sectors;
- pointer release commits the nearest wrapped product index;
- a native title click without drag remains unprevented;
- a drag suppresses accidental activation;
- ArrowLeft/ArrowRight promotes and focuses the adjacent title;
- idle auto-rotation pauses for hover/focus/press/drag and resumes after
  `REFERENCE_TIMING.idleResumeMs`.

- [ ] **Step 2: Write failing runtime title-state tests**

Construct six real fake anchors and assert:

```js
assert.equal(links[2].dataset.productPosition, "active");
assert.equal(links[1].dataset.productPosition, "previous");
assert.equal(links[3].dataset.productPosition, "next");
assert.equal(links[2].getAttribute("aria-current"), "page");
assert.equal(experience.dataset.selectedProduct, "2");
```

Also assert the same committed selection is passed to scene/world rotation.

- [ ] **Step 3: Run focused tests and verify RED**

```powershell
node --test src/test/js/home-quant-planet-input.test.mjs
node --test src/test/js/home-quant-planet-runtime.test.mjs
```

Expected: missing `onCommit` and missing `data-product-position` behavior.

- [ ] **Step 4: Implement sector snapping in the input controller**

Track drag distance separately from pointer parallax. On primary release:

1. determine whether the gesture crossed the click-suppression threshold;
2. compute the nearest six-sector selection;
3. emit `onCommit`;
4. release pointer ownership;
5. preserve native activation when the gesture remained a click.

Retain current multi-touch primary ownership and cleanup guarantees.

- [ ] **Step 5: Apply product positions in the runtime**

Create one function:

```js
function applyProductSelection(selection) {
    links.forEach((link, index) => {
        const delta = (index - selection.selectedIndex + links.length)
            % links.length;
        const position = delta === 0 ? "active"
            : delta === 1 ? "next"
                : delta === links.length - 1 ? "previous" : "hidden";
        link.dataset.productPosition = position;
        if (position === "active") {
            link.setAttribute("aria-current", "page");
        } else {
            link.removeAttribute("aria-current");
        }
    });
    experience.dataset.selectedProduct = String(selection.selectedIndex);
}
```

The runtime's one animation lifecycle updates selection and scene drag/world
rotation from the same accumulated angle. Do not create a second RAF loop.

- [ ] **Step 6: Add reference-calibrated snap motion**

Use Motion when available to ease title states over
`REFERENCE_TIMING.selectionSnapMs`. Missing Motion applies the final state
immediately. Auto-rotation uses `REFERENCE_TIMING.productRevolutionMs`; planet
ambient rotation uses `REFERENCE_TIMING.worldRevolutionMs`.

- [ ] **Step 7: Run all JS behavior tests**

```powershell
node --test src/test/js/home-quant-planet-composition.test.mjs src/test/js/home-quant-planet-motion.test.mjs src/test/js/home-quant-planet-input.test.mjs src/test/js/home-quant-planet-runtime.test.mjs src/test/js/home-quant-planet-world.test.mjs
```

Expected: all tests pass with one lifecycle and no wheel registration.

- [ ] **Step 8: Commit**

```powershell
git add src/main/resources/static/js/home-quant-planet-input.js src/main/resources/static/js/home-quant-planet-runtime.js src/test/js/home-quant-planet-input.test.mjs src/test/js/home-quant-planet-runtime.test.mjs
git commit -m "feat: rotate quant product selection"
```

---

### Task 5: Entry Choreography, Static Parity, and Responsive Polish

**Files:**
- Modify: `src/main/resources/templates/index.html`
- Modify: `src/main/resources/static/css/home-quant-planet.css`
- Modify: `src/main/resources/static/js/home-quant-planet-runtime.js`
- Modify: `src/test/js/home-quant-planet-runtime.test.mjs`
- Modify: `src/test/java/com/kingyurina/demo/web/HomePageTemplateContractTest.java`

**Interfaces:**
- Intro phases remain `boot`, `mark`, `split`, `reveal`, `ready`.
- Runtime sets `data-intro-phase` and CSS variables
  `--intro-world-scale`, `--intro-title-progress`, `--intro-action-progress`.

- [ ] **Step 1: Write failing intro and fallback parity tests**

Add runtime tests proving:

- boot starts with the planet/title/action concealed;
- split reveals the complete planet, not a cropped scene;
- title appears after world reveal;
- lower action appears last;
- ready occurs before `4000ms`;
- BFCache return keeps ready;
- missing Motion/WebGL/reduced motion produces the final complete composition
  immediately.

Add Java/CSS contracts for the three intro variables and static title/action
visibility.

- [ ] **Step 2: Run focused tests and verify RED**

```powershell
node --test src/test/js/home-quant-planet-runtime.test.mjs
mvn.cmd "-Dmaven.repo.local=C:\dev\codex-demo\.m2\repository" "-Dtest=HomePageTemplateContractTest#homepageKeepsReferenceCompositionInFallbacks" test
```

Expected: missing title/action intro variables and fallback parity rules.

- [ ] **Step 3: Apply staged entry variables**

Use the existing intro state to set:

```js
experience.style.setProperty("--intro-world-scale", intro.worldScale);
experience.style.setProperty(
    "--intro-title-progress",
    String(Math.max(0, (intro.revealProgress - .2) / .8))
);
experience.style.setProperty(
    "--intro-action-progress",
    String(Math.max(0, (intro.revealProgress - .62) / .38))
);
```

Clamp values to `[0,1]`. The hard deadline forces all three to `1`.

- [ ] **Step 4: Replace horizontal landscape bands**

Use one turquoise field with restrained radial atmosphere and small original
signal marks. Remove the rejected horizon/ground strips. Keep the complete
planet drop shadow subtle and centered.

- [ ] **Step 5: Match static composition**

The CSS fallback world uses the same bounding box as WebGL, shows the active
central title and lower yellow action, and exposes previous/next route labels.
No-JS markup must default to Quant active and keep all six anchors reachable.

- [ ] **Step 6: Tune four responsive layouts**

Use explicit media-query checks:

- `1440×900`: world `58–68vh`, title `45–62%` of world width;
- `1024×768`: complete world, action above lower safe edge;
- `768×1024`: world based on portrait width with title centered;
- `375×812`: world `68–82vw`, compact side labels, 44px targets.

No rule may hide the active title after `.planet-webgl-ready`.

- [ ] **Step 7: Run runtime and Java tests**

```powershell
node --test src/test/js/home-quant-planet-runtime.test.mjs
mvn.cmd "-Dmaven.repo.local=C:\dev\codex-demo\.m2\repository" "-Dtest=HomePageTemplateContractTest" test
```

Expected: all intro, fallback, semantic, responsive contract tests pass.

- [ ] **Step 8: Commit**

```powershell
git add src/main/resources/templates/index.html src/main/resources/static/css/home-quant-planet.css src/main/resources/static/js/home-quant-planet-runtime.js src/test/js/home-quant-planet-runtime.test.mjs src/test/java/com/kingyurina/demo/web/HomePageTemplateContractTest.java
git commit -m "feat: reveal the reference quant composition"
```

---

### Task 6: Synchronized Reference QA, Performance, and Regression Closure

**Files:**
- Modify only if a browser-discovered defect has a failing regression test.
- Test: all JS suites and `HomePageTemplateContractTest`.
- Report: `.superpowers/sdd/2026-07-26-reference-faithful-quant-planet-redesign/task-6-report.md`

**Interfaces:**
- Loopback diagnostics: `data-render-ms`, `data-draw-calls`,
  `data-triangles`.
- Calibration changes may modify only named `REFERENCE_TIMING` values or
  explicit composition-size CSS tokens after evidence is recorded.

- [ ] **Step 1: Run final automated verification**

```powershell
node --check src/main/resources/static/js/home-quant-planet-composition.js
node --check src/main/resources/static/js/home-quant-planet-scene.js
node --check src/main/resources/static/js/home-quant-planet-runtime.js
node --test src/test/js/home-quant-planet-composition.test.mjs src/test/js/home-quant-planet-motion.test.mjs src/test/js/home-quant-planet-input.test.mjs src/test/js/home-quant-planet-runtime.test.mjs src/test/js/home-quant-planet-world.test.mjs
mvn.cmd "-Dmaven.repo.local=C:\dev\codex-demo\.m2\repository" test
git diff --check
```

Expected: clean syntax, all Node tests pass, Maven build succeeds, no whitespace
errors.

- [ ] **Step 2: Start the isolated server without touching port 8080**

Run on port `8081` using the existing workspace-local database/server helper or
the project's established Spring Boot command. Confirm:

```powershell
Invoke-WebRequest -UseBasicParsing http://127.0.0.1:8081/
```

Expected: HTTP `200`.

- [ ] **Step 3: Capture the Quant page at four viewports**

Using `browser:control-in-app-browser`, capture:

- `1440×900`
- `1024×768`
- `768×1024`
- `375×812`

For each, record viewport size, `scrollWidth/clientWidth`,
`scrollHeight/clientHeight`, complete-world bounding box, central-title
bounding box, CTA bounding box, and screenshot path.

- [ ] **Step 4: Capture the reference under the same desktop viewport**

Open `https://messenger.abeto.co/` in the same browser surface. Record a
synchronized ten-second observation of entry and steady-state rotation. Do not
download or inspect proprietary assets/source. Record only visible timing,
angular displacement, and viewport-relative bounding boxes.

- [ ] **Step 5: Compare measurable parity**

Quant acceptance thresholds:

- complete-world relative width/height within approximately `5%`;
- central title and CTA relative placement within approximately `5%`;
- world angular displacement over ten seconds within approximately `8%`;
- entry phase ordering and idle-resume rhythm visibly equivalent;
- no cropped upper landmark or lower planet edge.

If timing alone misses tolerance, change only named `REFERENCE_TIMING`
constants. If placement alone misses tolerance, change only explicit CSS
composition tokens. Write a failing regression test before every correction.

- [ ] **Step 6: Validate interaction and fallbacks**

Check:

- first entry and hard timeout;
- drag/touch selection and snapping;
- ArrowLeft/ArrowRight wrapping;
- central title navigation to all exact six routes;
- one lower `ENTER QUANT` action;
- wheel has no effect;
- reduced motion;
- `?render=static`;
- `?motion=reduce`;
- no JavaScript;
- missing Motion;
- WebGL failure and context loss;
- BFCache return;
- console/network errors.

- [ ] **Step 7: Validate performance**

After at least `60` stable frames on loopback, record:

- average `data-render-ms <= 12.0`;
- `data-draw-calls <= 75`;
- `data-triangles <= 140000`;
- pixel ratio `<= 1.5`.

- [ ] **Step 8: Re-run verification after any correction**

Run the complete Step 1 command set again. Browser-corrected code is not
accepted without a focused RED/GREEN regression test and complete green suite.

- [ ] **Step 9: Commit only if QA required a correction**

```powershell
git add src/main/resources/static/js/home-quant-planet-composition.js src/main/resources/static/js/home-quant-planet-motion.js src/main/resources/static/js/home-quant-planet-world.js src/main/resources/static/js/home-quant-planet-scene.js src/main/resources/static/js/home-quant-planet-input.js src/main/resources/static/js/home-quant-planet-runtime.js src/main/resources/templates/index.html src/main/resources/static/css/home-quant-planet.css src/test/js/home-quant-planet-composition.test.mjs src/test/js/home-quant-planet-motion.test.mjs src/test/js/home-quant-planet-input.test.mjs src/test/js/home-quant-planet-runtime.test.mjs src/test/js/home-quant-planet-world.test.mjs src/test/java/com/kingyurina/demo/web/HomePageTemplateContractTest.java
git commit -m "fix: calibrate the quant reference composition"
```

If no code changes are required, create no empty commit.

---

## Final Review Gate

After all six tasks:

1. run a whole-branch review from the redesign base to `HEAD`;
2. triage every deferred Minor and parked finding;
3. dispatch one consolidated fix wave for Critical/Important findings;
4. run one scoped re-review;
5. use `superpowers:verification-before-completion`;
6. use `superpowers:finishing-a-development-branch`;
7. do not describe the result as reference-faithful until screenshots and
   synchronized comparison evidence exist.
