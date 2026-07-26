# Messenger-Identical Quant Homepage Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> `superpowers:subagent-driven-development` (recommended) or
> `superpowers:executing-plans` to implement this plan task-by-task. Steps use
> checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the six-route Quant Planet homepage with one fullscreen,
real-time Three.js planet composition matching the supplied Messenger reference,
with the reference's central three-line title and one yellow `/quant` action.

**Architecture:** Keep the existing homepage module boundaries but collapse
their product-selection responsibilities into one reference hero. Rebuild the
world behind the existing `createQuantPlanetWorld()` interface, reduce motion
and input to one continuously rotating planet, and perform one atomic
template/CSS/runtime cutover after the new isolated units are tested.

**Tech Stack:** Java 21, Spring Boot, Thymeleaf, vanilla ES modules, Three.js,
Motion, CSS, Node's built-in test runner, JUnit 5, Jsoup.

## Global Constraints

- Work only in the existing isolated worktree
  `C:/dev/codex-demo/.worktrees/quant-planet-homepage` on
  `codex/quant-planet-homepage`.
- Preserve unrelated files in `main` and every other worktree.
- Do not add React, a bundler, external page-request APIs, or a second animation
  loop.
- Do not download or embed the reference site's source code, models, textures,
  shaders, or fonts.
- The supplied screenshot is a visual reference; only the reconstructed
  transparent title asset may be committed.
- Remove the six-route homepage carousel and every selection/snap/idle-route
  behavior.
- Keep exactly one visible homepage action, text `ENTER`, with exact
  destination `/quant`.
- Keep `100dvh`, `overflow: hidden`, and no wheel behavior.
- Keep the four-second intro hard deadline independent of `requestAnimationFrame`.
- Keep deterministic static, reduced-motion, missing-Motion, WebGL-failure,
  context-loss, and no-JavaScript fallbacks.
- Cap device pixel ratio at `1.5`, draw calls at `75`, triangles at `140000`,
  and steady diagnostic render time at `12ms`.
- Use the workspace-local Maven repository:
  `mvn.cmd "-Dmaven.repo.local=C:\dev\codex-demo\.m2\repository" test`.

---

### Task 1: Reference Title Asset and Single-Hero Composition Contract

**Files:**

- Create:
  `src/main/resources/static/img/home-messenger-reference-title.png`
- Modify:
  `src/main/resources/static/js/home-quant-planet-composition.js`
- Modify:
  `src/test/js/home-quant-planet-composition.test.mjs`
- Modify:
  `src/test/java/com/kingyurina/demo/web/HomePageTemplateContractTest.java`

**Interfaces:**

- Produces:
  `REFERENCE_HERO: Readonly<{titleAssetUrl:string,introDeadlineMs:number,worldRevolutionMs:number,dragRadiansPerPixel:number,settleMs:number}>`.
- Preserves:
  `fitPerspectiveSphere({radius, verticalFovDegrees, aspect, fill})`.
- The old `PRODUCT_SCENES`, `resolveSelectedProduct`,
  `resolveAdjacentProduct`, and `createProductSelection` remain temporarily
  available until the atomic cutover in Task 4.

- [ ] **Step 1: Write failing composition and asset contracts**

Add this contract to
`src/test/js/home-quant-planet-composition.test.mjs`:

```js
test("defines one messenger reference hero without homepage routes", () => {
    assert.deepEqual(REFERENCE_HERO, {
        titleAssetUrl: "/img/home-messenger-reference-title.png",
        introDeadlineMs: 3999,
        worldRevolutionMs: 96000,
        dragRadiansPerPixel: 0.0048,
        settleMs: 720
    });
    assert.equal("routes" in REFERENCE_HERO, false);
});
```

Add a Java asset test that resolves
`static/img/home-messenger-reference-title.png`, reads it with `ImageIO`, and
asserts:

```java
assertNotNull(image);
assertTrue(image.getWidth() >= 900);
assertTrue(image.getHeight() >= 700);
assertTrue(image.getColorModel().hasAlpha());
```

- [ ] **Step 2: Run focused tests and verify RED**

```powershell
node --test src/test/js/home-quant-planet-composition.test.mjs
mvn.cmd "-Dmaven.repo.local=C:\dev\codex-demo\.m2\repository" `
  "-Dtest=HomePageTemplateContractTest#homepageReferenceTitleAssetIsTransparent" test
```

Expected: JavaScript fails because `REFERENCE_HERO` is absent; Maven fails
because the title asset is absent.

- [ ] **Step 3: Reconstruct the transparent title asset**

Use the `imagegen` image-edit workflow with the supplied image:

`C:/Users/user/AppData/Local/Temp/codex-clipboard-4dbbe21d-7351-45b3-addb-24a0eec0db1c.png`

The edit instruction is:

> Preserve the central three-line 3D lettering exactly as visible, including
> every glyph, line break, off-white face, graphite outline, extrusion, and
> shadow. Remove the turquoise background and every planet, tree, building,
> pipe, rock, and particle pixel. Output only the title on a transparent
> background, centered with generous transparent padding. Do not redraw,
> translate, correct, or replace any character.

Save the result as
`src/main/resources/static/img/home-messenger-reference-title.png`. Inspect the
saved asset at original resolution and reject it if any visible character or
line break differs from the supplied screenshot.

- [ ] **Step 4: Add the single-hero constants**

In `home-quant-planet-composition.js`, add:

```js
export const REFERENCE_HERO = Object.freeze({
    titleAssetUrl: "/img/home-messenger-reference-title.png",
    introDeadlineMs: 3999,
    worldRevolutionMs: 96000,
    dragRadiansPerPixel: 0.0048,
    settleMs: 720
});
```

Keep the old product exports untouched until Task 4 so existing runtime tests
remain green.

- [ ] **Step 5: Verify GREEN and inspect the asset**

```powershell
node --test src/test/js/home-quant-planet-composition.test.mjs
mvn.cmd "-Dmaven.repo.local=C:\dev\codex-demo\.m2\repository" `
  "-Dtest=HomePageTemplateContractTest#homepageReferenceTitleAssetIsTransparent" test
git diff --check
```

Expected: focused tests pass; the PNG contains only the exact three-line title
on transparency.

- [ ] **Step 6: Commit**

```powershell
git add `
  src/main/resources/static/img/home-messenger-reference-title.png `
  src/main/resources/static/js/home-quant-planet-composition.js `
  src/test/js/home-quant-planet-composition.test.mjs `
  src/test/java/com/kingyurina/demo/web/HomePageTemplateContractTest.java
git commit -m "feat: define the messenger reference hero"
```

---

### Task 2: Rebuild the Reference Planet Silhouette

**Files:**

- Modify:
  `src/main/resources/static/js/home-quant-planet-world.js`
- Modify:
  `src/test/js/home-quant-planet-world.test.mjs`
- Modify:
  `src/test/js/home-quant-planet-composition.test.mjs`

**Interfaces:**

- Preserves:
  `createQuantPlanetWorld(THREE, {profile})`.
- Produces a world object with:
  `group`, `update(pose)`, `getFramingEnvelope()`, `getRenderStats()`,
  and `dispose()`.
- `update(pose)` consumes:

```js
{
    worldAngle: Number,
    dragAngle: Number,
    pointer: { x: Number, y: Number },
    elapsedMs: Number,
    reduceMotion: Boolean
}
```

- [ ] **Step 1: Replace chapter tests with reference silhouette tests**

Remove tests tied to Observe, Resolve, Act, route activity, and the courier
chapter loop. Add tests that require the new authored landmark groups:

```js
for (const name of [
    "ReferencePlanetCore",
    "ReferenceWaterBand",
    "ReferenceTemplePeak",
    "ReferenceIndustrialCluster",
    "ReferenceUtilityCluster",
    "ReferenceLowerCanopy",
    "ReferenceFloatingDebris"
]) {
    assert.ok(world.group.getObjectByName(name), `Missing ${name}`);
}
```

Add a pose test proving that only `worldAngle + dragAngle` controls the primary
yaw and that repeated calls with the same pose are deterministic.

Add framing assertions that the envelope contains:

```js
assert.ok(envelope.min.x <= -4.8);
assert.ok(envelope.max.x >= 4.4);
assert.ok(envelope.max.y >= 5.2);
assert.ok(envelope.min.y <= -4.9);
```

- [ ] **Step 2: Run world tests and verify RED**

```powershell
node --test src/test/js/home-quant-planet-world.test.mjs `
  src/test/js/home-quant-planet-composition.test.mjs
```

Expected: failures name the absent reference landmark groups and old chapter
behavior.

- [ ] **Step 3: Rebuild the authored world groups**

Refactor `createQuantPlanetWorld()` into local builders with these exact
responsibilities:

```js
function buildPlanetCore(THREE, resources, palette) {}
function buildWaterBand(THREE, resources, palette) {}
function buildTemplePeak(THREE, resources, palette) {}
function buildIndustrialCluster(THREE, resources, palette) {}
function buildUtilityCluster(THREE, resources, palette) {}
function buildLowerCanopy(THREE, resources, palette, count) {}
function buildFloatingDebris(THREE, resources, palette, count) {}
```

Use the supplied screenshot for silhouette placement:

- temple and pale mountain mass at the upper center;
- orange-red machinery projecting from the left;
- antenna, cables, and small structures on the right;
- water band crossing the center;
- dense green canopy along the lower edge;
- off-axis rocks and fragments outside the core silhouette.

Use `InstancedMesh` for canopy, rocks, roof pieces, and floating debris. Retain
the existing resource tracker and rollback path; do not introduce ad-hoc
disposal.

- [ ] **Step 4: Simplify world animation**

Replace chapter-linked animation with deterministic ambient details:

```js
world.group.rotation.y = pose.worldAngle + pose.dragAngle;
water.material.uniforms.time.value = pose.reduceMotion
    ? 0
    : pose.elapsedMs / 1000;
utilityRotor.rotation.z = pose.reduceMotion
    ? 0
    : pose.elapsedMs * 0.00035;
```

Pointer movement belongs to the scene camera rig, not to the world builder.

- [ ] **Step 5: Verify bounds, budgets, and disposal**

```powershell
node --test src/test/js/home-quant-planet-world.test.mjs `
  src/test/js/home-quant-planet-composition.test.mjs
git diff --check
```

Expected:

- all required landmark groups exist;
- the complete public envelope includes every transformed vertex and instance;
- draw calls are no more than `75`;
- triangles are no more than `140000`;
- disposal and construction rollback remain exactly-once.

- [ ] **Step 6: Commit**

```powershell
git add `
  src/main/resources/static/js/home-quant-planet-world.js `
  src/test/js/home-quant-planet-world.test.mjs `
  src/test/js/home-quant-planet-composition.test.mjs
git commit -m "feat: rebuild the messenger reference planet"
```

---

### Task 3: Collapse Motion and Input to One Planet

**Files:**

- Modify:
  `src/main/resources/static/js/home-quant-planet-motion.js`
- Modify:
  `src/main/resources/static/js/home-quant-planet-input.js`
- Modify:
  `src/test/js/home-quant-planet-motion.test.mjs`
- Modify:
  `src/test/js/home-quant-planet-input.test.mjs`

**Interfaces:**

- Produces:
  `createHeroIntroState({elapsedMs, sceneReady, reduceMotion})`.
- Produces:
  `createHeroPose({elapsedMs, worldAngle, dragAngle, pointer, reduceMotion})`.
- Produces:
  `createHeroInputController({surface, windowTarget, onStateChange})`.
- Input state is:

```js
{
    pointer: { x: Number, y: Number },
    dragAngle: Number,
    dragging: Boolean,
    settling: Boolean
}
```

- [ ] **Step 1: Rewrite failing motion tests**

Delete product ring, wrapped index, product revolution, chapter courier, and
route activity expectations. Add:

```js
test("completes one reference world revolution in 96 seconds", () => {
    const start = createHeroPose({
        elapsedMs: 0,
        worldAngle: 0,
        dragAngle: 0,
        pointer: { x: 0, y: 0 },
        reduceMotion: false
    });
    const end = createHeroPose({
        elapsedMs: REFERENCE_HERO.worldRevolutionMs,
        worldAngle: 0,
        dragAngle: 0,
        pointer: { x: 0, y: 0 },
        reduceMotion: false
    });
    assert.ok(Math.abs((end.worldAngle - start.worldAngle) - Math.PI * 2) < 1e-9);
});
```

Add intro ordering assertions for `loader`, `world`, `title`, and `action`,
with `ready` forced by `3999ms`.

- [ ] **Step 2: Rewrite failing input tests**

Delete anchor-click, arrow-key, sector-snap, hover, focus, and idle-route tests.
Add realistic primary-pointer tests proving:

- pointer movement changes only `pointer`;
- horizontal drag changes only `dragAngle`;
- release begins a `720ms` settle without sector snapping;
- outside release, cancel, and lost capture clear ownership;
- secondary pointers do not change `pointer` or `dragAngle`;
- no wheel listener is registered;
- `destroy()` removes all listeners.

- [ ] **Step 3: Run focused tests and verify RED**

```powershell
node --test src/test/js/home-quant-planet-motion.test.mjs `
  src/test/js/home-quant-planet-input.test.mjs
```

Expected: the new single-hero functions are absent and product-selection
behavior remains.

- [ ] **Step 4: Implement single-hero motion**

In `home-quant-planet-motion.js`, implement:

```js
export function createHeroPose({
    elapsedMs = 0,
    worldAngle = 0,
    dragAngle = 0,
    pointer = { x: 0, y: 0 },
    reduceMotion = false
} = {}) {
    const ambient = reduceMotion
        ? 0
        : (elapsedMs / REFERENCE_HERO.worldRevolutionMs) * Math.PI * 2;
    return {
        worldAngle: worldAngle + ambient,
        dragAngle,
        pointer: reduceMotion ? { x: 0, y: 0 } : pointer,
        elapsedMs,
        reduceMotion
    };
}
```

Keep the existing quality profile, local diagnostic overrides, frame
eligibility, lifecycle, and DOM Motion cleanup behavior.

- [ ] **Step 5: Implement single-hero input**

Change the exported factory to `createHeroInputController`. Keep the proven
owner-gated terminal-event handling and touch ownership. Remove link inspection,
keyboard route selection, selection callbacks, idle timers, and sector
calculation.

The controller returns:

```js
{
    getState,
    setBaseAngle,
    destroy
}
```

`pointermove` uses `REFERENCE_HERO.dragRadiansPerPixel` while primary drag owns
the pointer. Release leaves the exact accumulated angle; Motion may ease the
visual settle but must not change the committed angle.

- [ ] **Step 6: Verify GREEN**

```powershell
node --check src/main/resources/static/js/home-quant-planet-motion.js
node --check src/main/resources/static/js/home-quant-planet-input.js
node --test src/test/js/home-quant-planet-motion.test.mjs `
  src/test/js/home-quant-planet-input.test.mjs
git diff --check
```

- [ ] **Step 7: Commit**

```powershell
git add `
  src/main/resources/static/js/home-quant-planet-motion.js `
  src/main/resources/static/js/home-quant-planet-input.js `
  src/test/js/home-quant-planet-motion.test.mjs `
  src/test/js/home-quant-planet-input.test.mjs
git commit -m "feat: reduce homepage motion to one planet"
```

---

### Task 4: Atomic Homepage and Runtime Cutover

**Files:**

- Modify:
  `src/main/resources/templates/index.html`
- Modify:
  `src/main/resources/static/css/home-quant-planet.css`
- Modify:
  `src/main/resources/static/js/home-quant-planet-composition.js`
- Modify:
  `src/main/resources/static/js/home-quant-planet-scene.js`
- Modify:
  `src/main/resources/static/js/home-quant-planet-runtime.js`
- Modify:
  `src/main/resources/static/js/home-quant-planet.js`
- Modify:
  `src/test/js/home-quant-planet-composition.test.mjs`
- Modify:
  `src/test/js/home-quant-planet-runtime.test.mjs`
- Modify:
  `src/test/java/com/kingyurina/demo/web/HomePageTemplateContractTest.java`

**Interfaces:**

- Preserves:
  `resolveQuantPlanetCapabilities()` and `createQuantPlanetRuntime()`.
- Scene consumes the Task 2 world and Task 3 pose.
- Runtime consumes:
  `createHeroIntroState`, `createHeroPose`, `createHeroInputController`, and
  `REFERENCE_HERO`.

- [ ] **Step 1: Replace template contracts with the single-action contract**

Replace product navigation assertions with:

```java
assertEquals(0, occurrences(template, "data-product-title-link"));
assertEquals(0, occurrences(template, "data-product-orbit-link"));
assertEquals(0, occurrences(template, "data-product-position"));
assertEquals(1, occurrences(template, "data-reference-title"));
assertEquals(1, occurrences(template, "data-enter-quant"));
assertEquals("/quant",
        document.selectFirst("[data-enter-quant]").attr("href"));
assertEquals("ENTER",
        document.selectFirst("[data-enter-quant]").text());
```

Assert the homepage has no header navigation, no metrics, no visible secondary
anchor, and no product navigation ARIA label.

- [ ] **Step 2: Replace runtime tests with one-scene contracts**

Remove selection, adjacent title, snap, product revolution, hover/focus, and
keyboard routing tests. Add:

- one scene and one RAF owner;
- intro order `loader -> world -> title -> action -> ready`;
- independent `3999ms` deadline;
- pointer/drag pose forwarding;
- no title state mutation;
- BFCache pause/resume without intro replay;
- missing Motion, reduced motion, static query, WebGL failure, and context loss
  show the complete title/action fallback;
- destroy cancels timers, Motion animations, input, scene, and RAF once.

- [ ] **Step 3: Run focused tests and verify RED**

```powershell
node --test src/test/js/home-quant-planet-composition.test.mjs `
  src/test/js/home-quant-planet-runtime.test.mjs
mvn.cmd "-Dmaven.repo.local=C:\dev\codex-demo\.m2\repository" `
  "-Dtest=HomePageTemplateContractTest" test
```

Expected: the old six-link markup and selection runtime violate the new
contracts.

- [ ] **Step 4: Replace `index.html` with the minimal reference shell**

Use this structure:

```html
<main id="main-content" class="planet-experience"
      data-home-experience aria-labelledby="home-title">
    <div class="planet-intro-loader" data-intro-loader aria-hidden="true">
        <span class="planet-intro-loader__mark"></span>
    </div>
    <div class="planet-scene" data-home-scene aria-hidden="true">
        <div class="planet-fallback" data-planet-fallback></div>
        <canvas data-home-canvas aria-hidden="true"></canvas>
    </div>
    <img class="reference-title"
         data-reference-title
         src="/img/home-messenger-reference-title.png"
         alt="">
    <a class="planet-primary-action"
       data-enter-quant href="/quant">ENTER</a>
    <section class="home-semantic-summary">
        <h1 id="home-title">Messenger</h1>
        <p>It's a small planet, but someone's gotta make the deliveries.</p>
    </section>
</main>
```

Do not retain the home header, brand, product orbit, route grid, metric cards,
or six route anchors.

- [ ] **Step 5: Rewrite CSS around five layers**

Retain only selectors for:

1. fullscreen field;
2. intro loader;
3. planet canvas/fallback;
4. reference title image;
5. yellow action and semantic content.

Define composition tokens:

```css
.planet-experience {
    --reference-world-size: min(82vh, 78vw);
    --reference-title-width: min(48vw, 64vh);
    --reference-action-bottom: max(4.8vh, env(safe-area-inset-bottom));
    --intro-world-progress: 0;
    --intro-title-progress: 0;
    --intro-action-progress: 0;
}
```

Use explicit media queries for `1440x900`, `1024x768`, `768x1024`,
`375x812`, and short landscape. The title asset stays centered over the world;
the button never overlaps the lower canopy.

- [ ] **Step 6: Simplify composition and scene**

Remove the temporary legacy product exports from
`home-quant-planet-composition.js`.

In `home-quant-planet-scene.js`:

- keep bounds-derived camera fitting and resize refit;
- keep profile-based pointer parallax;
- remove product/ring angle concepts;
- forward the Task 3 pose directly to `world.update(pose)`;
- keep diagnostic dataset fields `renderMs`, `drawCalls`, and `triangles`;
- keep pixel ratio capped at `1.5`.

- [ ] **Step 7: Simplify runtime**

In `createQuantPlanetRuntime()`:

- query only experience, loader, scene, canvas, fallback, title, and action;
- remove all product-link arrays, selected index, title position updates,
  selection Motion, hover/focus selection, and sector snapping;
- use one accumulated world angle plus the input controller's drag angle;
- set only:
  `--intro-world-progress`, `--intro-title-progress`,
  `--intro-action-progress`;
- retain the independent `3999ms` deadline and BFCache lifecycle;
- reveal the final static composition immediately for every fallback path.

- [ ] **Step 8: Verify the atomic cutover**

```powershell
node --check src/main/resources/static/js/home-quant-planet.js
node --check src/main/resources/static/js/home-quant-planet-composition.js
node --check src/main/resources/static/js/home-quant-planet-scene.js
node --check src/main/resources/static/js/home-quant-planet-runtime.js
node --test src/test/js/home-quant-planet-composition.test.mjs `
  src/test/js/home-quant-planet-motion.test.mjs `
  src/test/js/home-quant-planet-input.test.mjs `
  src/test/js/home-quant-planet-runtime.test.mjs `
  src/test/js/home-quant-planet-world.test.mjs
mvn.cmd "-Dmaven.repo.local=C:\dev\codex-demo\.m2\repository" `
  "-Dtest=HomePageTemplateContractTest" test
git diff --check
```

Expected: no product-selection contract remains; all homepage suites pass.

- [ ] **Step 9: Commit**

```powershell
git add `
  src/main/resources/templates/index.html `
  src/main/resources/static/css/home-quant-planet.css `
  src/main/resources/static/js/home-quant-planet-composition.js `
  src/main/resources/static/js/home-quant-planet-scene.js `
  src/main/resources/static/js/home-quant-planet-runtime.js `
  src/main/resources/static/js/home-quant-planet.js `
  src/test/js/home-quant-planet-composition.test.mjs `
  src/test/js/home-quant-planet-runtime.test.mjs `
  src/test/java/com/kingyurina/demo/web/HomePageTemplateContractTest.java
git commit -m "feat: cut over to the messenger reference homepage"
```

---

### Task 5: Fallback, Accessibility, and Performance Closure

**Files:**

- Modify:
  `src/main/resources/templates/index.html`
- Modify:
  `src/main/resources/static/css/home-quant-planet.css`
- Modify:
  `src/main/resources/static/js/home-quant-planet-runtime.js`
- Modify:
  `src/main/java/com/kingyurina/demo/config/SecurityConfig.java`
- Modify:
  `src/test/js/home-quant-planet-runtime.test.mjs`
- Modify:
  `src/test/java/com/kingyurina/demo/web/HomePageTemplateContractTest.java`

**Interfaces:**

- Loopback diagnostics remain:
  `data-render-ms`, `data-draw-calls`, `data-triangles`.
- Deterministic query overrides remain:
  `?render=static` and `?motion=reduce` on loopback only.

- [ ] **Step 1: Add failing final-state contracts**

Add tests proving:

- no-JavaScript markup shows the reference title and `/quant` action;
- static and reduced classes force all three intro progress variables to `1`;
- fallback composition uses the same world/title/action bounding tokens;
- title `alt=""` and the semantic `<h1>` prevent duplicate announcements;
- the decorative title explicitly has an `alt` attribute, rather than merely
  resolving a missing attribute to an empty value;
- action remains at least `44x44px`;
- no `wheel` listener or scroll container exists;
- no `PRODUCT_SCENES`, `createProductSelection`, `data-product-*`, or
  `product-title` string remains in production homepage files.
- anonymous `GET /img/home-messenger-reference-title.png` returns `200`,
  `Content-Type: image/png`, and a non-empty body while protected routes remain
  protected.

- [ ] **Step 2: Run focused tests and verify RED**

```powershell
node --test src/test/js/home-quant-planet-runtime.test.mjs
mvn.cmd "-Dmaven.repo.local=C:\dev\codex-demo\.m2\repository" `
  "-Dtest=HomePageTemplateContractTest" test
```

- [ ] **Step 3: Complete fallback CSS and markup**

Make the static planet reproduce the reference silhouette with layered CSS
shapes or one checked-in fallback illustration generated from the rebuilt
world. Ensure the same title PNG and native action remain visible in:

- no JavaScript;
- reduced motion;
- forced static;
- missing Motion;
- WebGL construction failure;
- WebGL context loss.

Do not introduce a second visible action or a hidden route list.

Permit `/img/**` alongside the existing public static-resource matchers in
`SecurityConfig`. Do not broaden any application or administrative route
matcher.

- [ ] **Step 4: Verify all regression and budget contracts**

```powershell
node --check src/main/resources/static/js/home-quant-planet-composition.js
node --check src/main/resources/static/js/home-quant-planet-scene.js
node --check src/main/resources/static/js/home-quant-planet-runtime.js
node --test src/test/js/home-quant-planet-composition.test.mjs `
  src/test/js/home-quant-planet-motion.test.mjs `
  src/test/js/home-quant-planet-input.test.mjs `
  src/test/js/home-quant-planet-runtime.test.mjs `
  src/test/js/home-quant-planet-world.test.mjs
mvn.cmd "-Dmaven.repo.local=C:\dev\codex-demo\.m2\repository" test
git diff --check
```

Expected: all Node and Maven tests pass; draw-call, triangle, DPR, lifecycle,
fallback, and accessibility contracts remain green.

- [ ] **Step 5: Commit**

```powershell
git add `
  src/main/resources/templates/index.html `
  src/main/resources/static/css/home-quant-planet.css `
  src/main/resources/static/js/home-quant-planet-runtime.js `
  src/main/java/com/kingyurina/demo/config/SecurityConfig.java `
  src/test/js/home-quant-planet-runtime.test.mjs `
  src/test/java/com/kingyurina/demo/web/HomePageTemplateContractTest.java
git commit -m "fix: complete the messenger reference fallbacks"
```

---

### Task 6: Synchronized Visual Calibration and Server Handoff

**Files:**

- Modify only when a measured defect has a failing regression test:
  `src/main/resources/static/css/home-quant-planet.css`
- Modify only when a measured defect has a failing regression test:
  `src/main/resources/static/js/home-quant-planet-composition.js`
- Modify the corresponding focused test for every correction.
- Create:
  `.superpowers/sdd/2026-07-26-messenger-identical-quant-homepage/task-6-report.md`

**Interfaces:**

- Timing calibration may change only:
  `REFERENCE_HERO.worldRevolutionMs` and `REFERENCE_HERO.settleMs`.
- Placement calibration may change only explicit CSS composition tokens.

- [ ] **Step 1: Run final automated verification**

```powershell
node --check src/main/resources/static/js/home-quant-planet-composition.js
node --check src/main/resources/static/js/home-quant-planet-scene.js
node --check src/main/resources/static/js/home-quant-planet-runtime.js
node --test src/test/js/home-quant-planet-composition.test.mjs `
  src/test/js/home-quant-planet-motion.test.mjs `
  src/test/js/home-quant-planet-input.test.mjs `
  src/test/js/home-quant-planet-runtime.test.mjs `
  src/test/js/home-quant-planet-world.test.mjs
mvn.cmd "-Dmaven.repo.local=C:\dev\codex-demo\.m2\repository" test
git diff --check
```

- [ ] **Step 2: Package and start the isolated server on `8081`**

Stop only the verified process listening on `8081`; never touch `8080`.

```powershell
mvn.cmd "-Dmaven.repo.local=C:\dev\codex-demo\.m2\repository" `
  -DskipTests package
```

Start the packaged JAR with profile `mariadb`, port `8081`, startup signal
refresh disabled, and a worktree-local log. Confirm `/`, `/?render=static`,
`/?motion=reduce`, and `/quant` return HTTP `200`.

- [ ] **Step 3: Capture local composition at four viewports**

Using `browser:control-in-app-browser`, capture and record:

- `1440x900`;
- `1024x768`;
- `768x1024`;
- `375x812`.

For each, record viewport, scroll/client dimensions, world bounds, title bounds,
action bounds, diagnostics, and screenshot path.

- [ ] **Step 4: Capture the reference in the same browser**

Open `https://messenger.abeto.co/` at `1440x900`. Record only visible behavior:

- intro phase order and elapsed times;
- complete-world bounds;
- title bounds;
- action bounds;
- world angular displacement over ten seconds;
- pointer and drag response.

Do not inspect or download source code or proprietary assets.

- [ ] **Step 5: Apply measured calibration only**

Acceptance thresholds:

- world/title/action relative bounds within approximately `5%`;
- ten-second angular displacement within approximately `8%`;
- identical ordering of loader, world, title, and action;
- no clipped temple, machinery, antenna, canopy, or yellow action;
- no scroll at any viewport.

Before every correction, add a focused failing test for the measured timing or
composition token. Change only the permitted constants/tokens, then rerun the
focused test and the complete verification suite.

- [ ] **Step 6: Record performance**

After at least `60` stable frames, record:

- average `data-render-ms <= 12.0`;
- `data-draw-calls <= 75`;
- `data-triangles <= 140000`;
- device pixel ratio `<= 1.5`.

- [ ] **Step 7: Commit only measured corrections**

If calibration changed code:

```powershell
git add `
  src/main/resources/static/css/home-quant-planet.css `
  src/main/resources/static/js/home-quant-planet-composition.js `
  src/test/js/home-quant-planet-composition.test.mjs `
  src/test/java/com/kingyurina/demo/web/HomePageTemplateContractTest.java
git commit -m "fix: calibrate the messenger reference homepage"
```

If no correction is required, create no empty commit.

- [ ] **Step 8: Write the QA report**

Write exact commands, results, screenshots, measurements, performance numbers,
browser limitations, and the final server PID/URL to:

`.superpowers/sdd/2026-07-26-messenger-identical-quant-homepage/task-6-report.md`.

Do not claim pixel or motion parity when same-browser evidence is unavailable.

---

## Completion Gate

Before handoff:

- every task has a separate implementation report and reviewer gate;
- all Important and Critical review findings are fixed and rereviewed;
- full Node and Maven suites pass at final HEAD;
- the isolated `8081` server serves final HEAD;
- the branch remains unpushed and unmerged unless the user separately requests
  integration.
