# Quant Planet Reference Fit and Detail Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Match the supplied reference's compact full-planet composition, prevent every rotating landmark from clipping, and add readable mid-scale detail to the original Quant Planet world.

**Architecture:** Replace the centered square drawing surface with a full-viewport WebGL layer and fit the camera to the complete rotation-invariant world envelope. Keep glyph sizing independent from planet framing, then enrich the existing procedural landmark builders with instanced, shared-resource detail families.

**Tech Stack:** Java 21, Spring Boot, Thymeleaf, vanilla CSS, native ES modules, Three.js, Node test runner, JUnit/Maven.

## Global Constraints

- Modify route `/` only; the yellow action continues to navigate to `/quant`.
- Preserve nine independent glyph meshes, ambient world rotation, pointer response, intro timing, and reduced-motion behavior.
- Add no external 3D model, texture, API call, frontend runtime package, React, Svelte, audio, scroll, zoom, or game control.
- Keep pixel ratio capped at `1.5`.
- Keep total draw calls including shadow calls at or below `75`.
- Keep visible triangles at or below `140000`.
- Keep loopback average scene submission at or below `12ms`.
- Validate `375×812`, `768×1024`, `1024×768`, and `1440×900`.
- Use `mvn.cmd "-Dmaven.repo.local=C:\dev\codex-demo\.m2\repository" test` for Maven verification.

## File Structure

- `src/main/resources/static/js/home-quant-planet-composition.js`
  owns pure perspective-fit math and reference coverage constants.
- `src/main/resources/static/js/home-quant-planet-scene.js`
  applies full-envelope fitting and independent glyph layout.
- `src/main/resources/static/css/home-quant-planet.css`
  owns the full-viewport renderer, fallback title, and action sizing.
- `src/main/resources/static/js/home-quant-planet-world.js`
  owns shared geometry/material resources and procedural landmark detail.
- `src/test/js/home-quant-planet-composition.test.mjs`
  proves viewport fitting, rotation safety, glyph layout, and renderer budgets.
- `src/test/js/home-quant-planet-world.test.mjs`
  proves named detail families, instancing, bounds, budgets, and disposal.
- `src/test/java/com/kingyurina/demo/web/HomePageTemplateContractTest.java`
  proves the CSS/template fallback and full-viewport contracts.

---

### Task 1: Full-Viewport Rotation-Safe Camera Fit

**Files:**
- Modify: `src/main/resources/static/js/home-quant-planet-composition.js`
- Modify: `src/main/resources/static/js/home-quant-planet-scene.js`
- Modify: `src/main/resources/static/css/home-quant-planet.css`
- Test: `src/test/js/home-quant-planet-composition.test.mjs`
- Test: `src/test/java/com/kingyurina/demo/web/HomePageTemplateContractTest.java`

**Interfaces:**
- Consumes: `world.getFramingEnvelope(): { center, radius, min, max }`
- Produces: `fitReferenceWorld({ center, radius, aspect, verticalFovDegrees }): { target, radius, distance, limitingFovRadians, coverage, margin }`
- Produces: full-viewport `.planet-scene` and `[data-home-canvas]`

- [ ] **Step 1: Write failing pure-fit tests**

Add tests that require a new exported `fitReferenceWorld` contract:

```js
const landscape = composition.fitReferenceWorld({
    center: { x: 0, y: 0, z: 0 },
    radius: 5.4,
    aspect: 1440 / 900,
    verticalFovDegrees: 34
});
const portrait = composition.fitReferenceWorld({
    center: { x: 0, y: 0, z: 0 },
    radius: 5.4,
    aspect: 375 / 812,
    verticalFovDegrees: 34
});

assert.ok(landscape.coverage >= .68 && landscape.coverage <= .74);
assert.ok(portrait.coverage >= .68 && portrait.coverage <= .82);
assert.equal(landscape.radius, 5.4);
assert.equal(portrait.radius, 5.4);
```

Update the public-export expectation to include `fitReferenceWorld`.

- [ ] **Step 2: Write failing scene projection tests**

Change the desktop harness from a square `720×720` surface to the real
`1280×720` viewport, then render yaw values `0`, `π/2`, `π`, and `3π/2`.
For every frame assert:

```js
const xs = frame.projected.map(({ x }) => x);
const ys = frame.projected.map(({ y }) => y);
assert.ok(Math.min(...xs) >= -.94);
assert.ok(Math.max(...xs) <= .94);
assert.ok(Math.min(...ys) >= -.94);
assert.ok(Math.max(...ys) <= .86);
```

The asymmetric vertical limit reserves the lower action zone.

- [ ] **Step 3: Write the failing CSS/template contract**

Require `.planet-scene` to use the entire fixed experience:

```java
String scene = cssRule(css, ".planet-scene");
assertTrue(scene.contains("inset: 0"));
assertTrue(scene.contains("width: 100vw"));
assertTrue(scene.contains("height: 100dvh"));
assertFalse(scene.contains("var(--reference-world-size)"));
```

- [ ] **Step 4: Run focused tests and verify failure**

Run:

```powershell
node --test src/test/js/home-quant-planet-composition.test.mjs
mvn.cmd "-Dmaven.repo.local=C:\dev\codex-demo\.m2\repository" -Dtest=HomePageTemplateContractTest test
```

Expected: JavaScript fails because `fitReferenceWorld` does not exist and the
old scene still fits a partial radius; Java fails because the scene is square.

- [ ] **Step 5: Implement reference coverage fitting**

Add a pure wrapper in `home-quant-planet-composition.js`:

```js
export function fitReferenceWorld({
    center,
    radius,
    aspect,
    verticalFovDegrees = 34
}) {
    const coverage = aspect < .75 ? .76 : .72;
    const margin = 1 / coverage;
    return {
        ...fitPerspectiveSphere({
            center,
            radius,
            aspect,
            verticalFovDegrees,
            margin
        }),
        coverage,
        margin
    };
}
```

In `home-quant-planet-scene.js`, remove `cameraHeroRadiusRatio()` and pass the
complete scaled envelope to `fitReferenceWorld()`. Keep near/far clip padding
based on the complete envelope radius.

In `home-quant-planet.css`, make `.planet-scene` use `inset: 0`, `width: 100vw`,
`height: 100dvh`, and remove `--reference-world-size` declarations from all
breakpoints.

- [ ] **Step 6: Run focused tests and tune only named coverage constants**

Run the two commands from Step 4. Adjust the desktop/portrait coverage constants
only if the complete projected mesh violates the tested safe frame.

- [ ] **Step 7: Commit**

```powershell
git add src/main/resources/static/js/home-quant-planet-composition.js src/main/resources/static/js/home-quant-planet-scene.js src/main/resources/static/css/home-quant-planet.css src/test/js/home-quant-planet-composition.test.mjs src/test/java/com/kingyurina/demo/web/HomePageTemplateContractTest.java
git commit -m "fix: frame the complete quant planet"
```

---

### Task 2: Reference Glyph and Primary-Action Scale

**Files:**
- Modify: `src/main/resources/static/js/home-quant-planet-scene.js`
- Modify: `src/main/resources/static/css/home-quant-planet.css`
- Test: `src/test/js/home-quant-planet-composition.test.mjs`
- Test: `src/test/java/com/kingyurina/demo/web/HomePageTemplateContractTest.java`

**Interfaces:**
- Consumes: `fitReferenceWorld()` result and complete world envelope.
- Produces: glyph layout radius ratio within `0.62` to `0.68`.
- Produces: fallback title width and `ENTER` action dimensions matched to the reference.

- [ ] **Step 1: Write failing glyph projection tests**

Extend the scene harness to record projected glyph bounds or the glyph rig's
layout radius. Assert at `1440×900`:

```js
const glyphToEnvelopeRatio = frame.glyphLayoutRadius / frame.worldRadius;
assert.ok(glyphToEnvelopeRatio >= .62);
assert.ok(glyphToEnvelopeRatio <= .68);
assert.ok(Math.abs(frame.glyphCenterProjected.x) < 1e-9);
assert.ok(Math.abs(frame.glyphCenterProjected.y) < .08);
```

Retain the test proving all nine pivots respond independently without moving
the planet or camera.

- [ ] **Step 2: Write failing CSS action/fallback tests**

Require the desktop fallback title and action to use bounded reference sizes:

```java
assertTrue(cssDeclaration(experience, "--reference-title-width")
        .contains("30vw"));
String action = cssRule(css, ".planet-primary-action");
assertTrue(action.contains("min-width: clamp(148px"));
assertTrue(action.contains("min-height: 48px"));
```

- [ ] **Step 3: Run focused tests and verify failure**

Run:

```powershell
node --test src/test/js/home-quant-planet-composition.test.mjs
mvn.cmd "-Dmaven.repo.local=C:\dev\codex-demo\.m2\repository" -Dtest=HomePageTemplateContractTest test
```

Expected: failures report the legacy `.504` ratio, oversized fallback title,
and undersized action.

- [ ] **Step 4: Implement independent reference sizing**

Set the scene glyph ratio to a named constant initially at `.65`. Set desktop
CSS to:

```css
--reference-title-width: min(30vw, 52vh);
```

Increase the action to a `clamp(148px, 10vw, 190px)` minimum width and `48px`
minimum height. Preserve the existing focus ring, press motion, route, and
bottom safe-area calculation. Define portrait overrides that keep the title at
or below `84vw`.

- [ ] **Step 5: Run focused tests**

Run the commands from Step 3 and confirm all focused tests pass.

- [ ] **Step 6: Commit**

```powershell
git add src/main/resources/static/js/home-quant-planet-scene.js src/main/resources/static/css/home-quant-planet.css src/test/js/home-quant-planet-composition.test.mjs src/test/java/com/kingyurina/demo/web/HomePageTemplateContractTest.java
git commit -m "fix: match reference hero proportions"
```

---

### Task 3: Industrial, Utility, and Temple Detail Families

**Files:**
- Modify: `src/main/resources/static/js/home-quant-planet-world.js`
- Test: `src/test/js/home-quant-planet-world.test.mjs`

**Interfaces:**
- Consumes: existing `resources.geometry`, palette, `addOutlinedMesh()`, and
  `orientInstance()`.
- Produces named meshes:
  `ReferenceIndustrialCollars`, `ReferenceIndustrialFasteners`,
  `ReferenceIndustrialCatwalk`, `ReferenceUtilitySupports`,
  `ReferenceTempleTrim`, and `ReferenceTempleRockLedges`.

- [ ] **Step 1: Write failing landmark-detail tests**

Add a test requiring each named mesh and instancing for repeated families:

```js
[
    "ReferenceIndustrialCollars",
    "ReferenceIndustrialFasteners",
    "ReferenceUtilitySupports",
    "ReferenceTempleTrim",
    "ReferenceTempleRockLedges"
].forEach((name) => {
    assert.ok(world.group.getObjectByName(name), `Missing ${name}`);
});
assert.equal(
    world.group.getObjectByName("ReferenceIndustrialFasteners").isInstancedMesh,
    true
);
```

Also assert the industrial cluster has at least three distinct non-ink
materials and its detail meshes are included in `getFramingEnvelope()`.

- [ ] **Step 2: Run the world test and verify failure**

Run:

```powershell
node --test src/test/js/home-quant-planet-world.test.mjs
```

Expected: named detail families are missing.

- [ ] **Step 3: Add shared detail geometry**

Register only reusable low-poly primitives through `ownGeometry()`:

```js
ring: ownGeometry(new THREE.TorusGeometry(1, .12, 5, 12)),
panel: ownGeometry(new THREE.BoxGeometry(1, 1, .12)),
rail: ownGeometry(new THREE.CylinderGeometry(1, 1, 1, 6, 1))
```

Do not allocate geometry inside per-instance loops.

- [ ] **Step 4: Implement industrial and utility detail**

Add instanced collar rings around both coral engines, small paper fasteners,
two secondary pipe sections, a narrow rock/ink catwalk, and one paper access
panel. Add utility mast feet, dish bracket, rotor ring, and deterministic cable
supports. Use existing palette materials and shared matrices.

- [ ] **Step 5: Implement temple detail**

Add instanced ink/paper roof ridge pieces, a recessed entrance panel, column
bases, and a small set of rock ledges around the mountain base. Keep the
highest point at or below the existing finial height plus `0.08` world units.

- [ ] **Step 6: Run world tests and budgets**

Run:

```powershell
node --test src/test/js/home-quant-planet-world.test.mjs
```

Expected: named details pass, total world calls including shadows remain at or
below `75`, triangles remain below `140000`, and disposal tests pass.

- [ ] **Step 7: Commit**

```powershell
git add src/main/resources/static/js/home-quant-planet-world.js src/test/js/home-quant-planet-world.test.mjs
git commit -m "feat: refine quant planet landmarks"
```

---

### Task 4: Village, Water, Terrain, and Foliage Detail Families

**Files:**
- Modify: `src/main/resources/static/js/home-quant-planet-world.js`
- Test: `src/test/js/home-quant-planet-world.test.mjs`

**Interfaces:**
- Consumes: `VILLAGE_PLACEMENTS`, water bridge placements, foliage placements,
  shared geometry/material registry, and surface orientation helpers.
- Produces named instanced meshes:
  `ReferenceVillageWindows`, `ReferenceVillageDoors`,
  `ReferenceVillageRoofTrim`, `ReferenceBridgeRails`,
  `ReferenceBankStones`, and `ReferenceCliffLedges`.

- [ ] **Step 1: Write failing detail-family tests**

Add:

```js
[
    "ReferenceVillageWindows",
    "ReferenceVillageDoors",
    "ReferenceVillageRoofTrim",
    "ReferenceBridgeRails",
    "ReferenceBankStones",
    "ReferenceCliffLedges"
].forEach((name) => {
    const mesh = world.group.getObjectByName(name);
    assert.equal(mesh?.isInstancedMesh, true, `${name} must be instanced`);
    assert.ok(mesh.count > 0, `${name} must not be empty`);
});
```

Add a foliage assertion proving at least three deterministic crown scale
variants remain and all instance greens stay above the existing crushed-black
floor.

- [ ] **Step 2: Run the world test and verify failure**

Run:

```powershell
node --test src/test/js/home-quant-planet-world.test.mjs
```

Expected: the six detail-family names are missing.

- [ ] **Step 3: Implement village facade details**

For each village placement, calculate its surface normal and tangent once.
Create instanced dark window panels, coral/paper door panels, and thin roof
ridge caps offset along the surface normal enough to avoid z-fighting. Vary
window count and chimney inclusion deterministically by placement index.

- [ ] **Step 4: Implement water and terrain detail**

Add two low rails per bridge, instanced bank stones around the water district,
and a limited cliff-ledge family on existing terrain patch sectors. Use
existing water, paper, rock, and ground-shade materials; no transparent black
backing meshes.

- [ ] **Step 5: Refine foliage variation**

Keep the recovered instance colors and emissive floor. Vary crown scale and
rotation using the placement index, and add only instanced branch or secondary
crown geometry if the render budget remains below the hard limit.

- [ ] **Step 6: Run world tests and budgets**

Run:

```powershell
node --test src/test/js/home-quant-planet-world.test.mjs
```

Expected: detail, color, envelope, budget, rollback, and disposal tests pass.

- [ ] **Step 7: Commit**

```powershell
git add src/main/resources/static/js/home-quant-planet-world.js src/test/js/home-quant-planet-world.test.mjs
git commit -m "feat: enrich quant planet surface detail"
```

---

### Task 5: Full Regression and Visual Comparison

**Files:**
- Modify only if a failing regression or visual measurement requires a scoped
  correction in the files already listed.

**Interfaces:**
- Consumes: completed full-viewport fit, glyph scale, action scale, and detail
  families.
- Produces: verified browser composition and clean test results.

- [ ] **Step 1: Run the complete JavaScript suite**

Run:

```powershell
node --test src/test/js/*.test.mjs
```

Expected: all tests pass with no skipped Quant Planet coverage.

- [ ] **Step 2: Run the complete Maven suite**

Run:

```powershell
mvn.cmd "-Dmaven.repo.local=C:\dev\codex-demo\.m2\repository" test
```

Expected: all Maven tests pass.

- [ ] **Step 3: Start or refresh the local preview**

Use the existing repository server workflow and confirm `/` returns HTTP 200.
Do not change DB-backed request behavior or introduce a frontend build server.

- [ ] **Step 4: Capture required viewports**

Capture `/` at:

```text
375×812
768×1024
1024×768
1440×900
```

At desktop, compare against the supplied reference screenshot. Confirm the
world occupies `68%` to `74%` of usable height, the title occupies `26%` to
`32%` of viewport width, the action is centered below it, and the industrial
landmark has visible turquoise clearance.

- [ ] **Step 5: Verify rotation-safe views**

Inspect deterministic or timed views corresponding to yaw `0°`, `90°`, `180°`,
and `270°`. Confirm no landmark touches the renderer boundary, no facade panels
z-fight, and no large surface becomes crushed black.

- [ ] **Step 6: Verify fallbacks and accessibility**

Confirm:

- reduced motion stops ambient rotation and glyph parallax;
- WebGL failure and static fallback use the same compact composition;
- the action is keyboard reachable with a visible focus ring;
- the document has no horizontal or vertical overflow;
- browser console and network panel show no runtime error or remote 3D asset.

- [ ] **Step 7: Run final diff and status checks**

Run:

```powershell
git diff --check
git status --short
git log -6 --oneline
```

Expected: no whitespace errors, only intentional files changed, and each
implementation task has its own commit.
