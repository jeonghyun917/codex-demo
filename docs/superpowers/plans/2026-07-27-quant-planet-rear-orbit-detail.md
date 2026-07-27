# Quant Planet Rear-Orbit Background and Detail Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a diverse, rear-only rotating background field, enlarge the visible planet to `1.2x`, and deepen the planet miniature detail while preserving the existing title interaction and performance envelope.

**Architecture:** Add a pure rear-orbit layout/motion module and a separate Three.js rear-field renderer owned by the existing scene controller. Keep all rear objects behind the planet through camera-facing elliptical motion with a clamped rear depth range and seam fades, then integrate their render statistics and lifecycle into the existing single-RAF scene. Adjust camera composition independently of the rear field, extend existing planet instance families for detail, and update the static fallback to match.

**Tech Stack:** Java 21, Spring Boot, Thymeleaf, vanilla JavaScript ES modules, Three.js, Node.js built-in test runner, JUnit 5, Maven, SVG, CSS.

## Global Constraints

- The rear field must remain behind the planet, title, and CTA for every sampled phase.
- The planet remains autonomous at one revolution per `36,000ms`.
- The rear-mid field circulates every `48,000ms`; the rear-far field circulates every `64,000ms`.
- Only the nine title glyphs receive pointer or touch input.
- Desktop visible planet diameter must be `1.2x` the current baseline within `±3%`.
- High profile contains 36-48 rear objects; balanced contains 24-34; low/mobile contains 14-22.
- High and balanced profiles expose at least four silhouette families and five size tiers.
- High profile stays at or below 80 draw calls and 145,000 triangles.
- Balanced profile stays at or below 70 draw calls and 95,000 triangles.
- Reduced motion freezes planet, rear-field circulation, local fragment spin, and title response.
- The rear field must use the existing animation-frame owner and must not create another RAF loop.
- Interactive rendering remains local and DB-backed page rendering is unchanged.

---

### Task 1: Deterministic Rear-Orbit Layout and Motion

**Files:**
- Create: `src/main/resources/static/js/home-quant-planet-rear-field-layout.js`
- Create: `src/test/js/home-quant-planet-rear-field-layout.test.mjs`

**Interfaces:**
- Produces: `REAR_FIELD_PERIODS_MS`, `REAR_FIELD_PROFILES`, `createRearFieldLayout(profile)`, and `sampleRearFieldTransform(placement, { worldAngle, elapsedMs, reduceMotion })`.
- `createRearFieldLayout(profile)` returns frozen placement records containing `id`, `band`, `family`, `phase`, `radius`, `verticalOffset`, `rearDepth`, `depthBreath`, `sizeTier`, `scale`, `rotation`, `spinPeriodMs`, and `colorIndex`.
- `sampleRearFieldTransform(...)` returns `{ position: [x, y, z], rotation: [x, y, z], opacity }`.

- [ ] **Step 1: Write failing layout and motion tests**

```js
import test from "node:test";
import assert from "node:assert/strict";
import {
    REAR_FIELD_PERIODS_MS,
    createRearFieldLayout,
    sampleRearFieldTransform
} from "../../main/resources/static/js/home-quant-planet-rear-field-layout.js";

test("authors diverse rear-field profiles", () => {
    const high = createRearFieldLayout({ name: "high" });
    const balanced = createRearFieldLayout({ name: "balanced" });
    const low = createRearFieldLayout({ name: "low" });
    assert.ok(high.length >= 36 && high.length <= 48);
    assert.ok(balanced.length >= 24 && balanced.length <= 34);
    assert.ok(low.length >= 14 && low.length <= 22);
    assert.ok(new Set(high.map((item) => item.family)).size >= 4);
    assert.ok(new Set(high.map((item) => item.sizeTier)).size >= 5);
    const visibleAreas = high.map((item) => item.scale[0] * item.scale[1]);
    assert.ok(Math.max(...visibleAreas) / Math.min(...visibleAreas) >= 6);
});

test("keeps every sampled object behind its safe plane", () => {
    const placements = createRearFieldLayout({ name: "high" });
    for (let step = 0; step <= 128; step += 1) {
        const worldAngle = Math.PI * 2 * step / 128;
        for (const placement of placements) {
            const sample = sampleRearFieldTransform(placement, {
                worldAngle,
                elapsedMs: step * 500,
                reduceMotion: false
            });
            assert.ok(sample.position[2] <= placement.rearDepth);
            assert.ok(sample.opacity >= 0 && sample.opacity <= 1);
        }
    }
});

test("uses independent mid and far circulation periods", () => {
    assert.deepEqual(REAR_FIELD_PERIODS_MS, {
        mid: 48000,
        far: 64000
    });
});

test("reduced motion returns an invariant authored pose", () => {
    const placement = createRearFieldLayout({ name: "high" })[0];
    const first = sampleRearFieldTransform(placement, {
        worldAngle: 0,
        elapsedMs: 0,
        reduceMotion: true
    });
    const later = sampleRearFieldTransform(placement, {
        worldAngle: Math.PI,
        elapsedMs: 32000,
        reduceMotion: true
    });
    assert.deepEqual(later, first);
});
```

- [ ] **Step 2: Run the new test and confirm RED**

Run:

```powershell
node --test src/test/js/home-quant-planet-rear-field-layout.test.mjs
```

Expected: FAIL because `home-quant-planet-rear-field-layout.js` does not exist.

- [ ] **Step 3: Implement frozen placements and rear-only sampling**

Use explicit profile counts of `42`, `30`, and `18`. Author placements from a
deterministic seed table, not runtime randomness. Implement camera-facing
elliptical motion with fixed rear depth:

```js
export const REAR_FIELD_PERIODS_MS = Object.freeze({
    mid: 48000,
    far: 64000
});

export function sampleRearFieldTransform(placement, pose = {}) {
    const reduceMotion = Boolean(pose.reduceMotion);
    const worldAngle = reduceMotion
        ? 0
        : (Number.isFinite(pose.worldAngle) ? pose.worldAngle : 0);
    const period = REAR_FIELD_PERIODS_MS[placement.band];
    const phase = placement.phase
        + worldAngle * 36000 / period;
    const seamDistance = Math.abs(Math.cos(phase));
    const depthBreath = reduceMotion
        ? 0
        : -Math.abs(Math.cos(phase)) * placement.depthBreath;
    return {
        position: [
            Math.sin(phase) * placement.radius,
            placement.verticalOffset
                + Math.cos(phase) * placement.radius * placement.verticalRatio,
            placement.rearDepth + depthBreath
        ],
        rotation: [
            placement.rotation[0],
            placement.rotation[1],
            placement.rotation[2]
                + worldAngle * 36000 / placement.spinPeriodMs
        ],
        opacity: Math.min(1, Math.max(0, seamDistance * 3.2))
    };
}
```

Keep `depthBreath <= 8%` of the authored band thickness. Do not accept pointer,
drag, wheel, or route input in either public function.

- [ ] **Step 4: Run the layout tests and confirm GREEN**

Run:

```powershell
node --test src/test/js/home-quant-planet-rear-field-layout.test.mjs
```

Expected: all new tests PASS.

- [ ] **Step 5: Commit the pure rear-field contract**

```powershell
git add src/main/resources/static/js/home-quant-planet-rear-field-layout.js src/test/js/home-quant-planet-rear-field-layout.test.mjs
git commit -m "feat: define rear orbit field motion"
```

---

### Task 2: Three.js Rear-Field Renderer

**Files:**
- Create: `src/main/resources/static/js/home-quant-planet-rear-field.js`
- Create: `src/test/js/home-quant-planet-rear-field.test.mjs`
- Modify: `src/test/js/support/three-test-double.mjs`

**Interfaces:**
- Consumes: `createRearFieldLayout(profile)` and `sampleRearFieldTransform(...)`.
- Produces: `createQuantPlanetRearField(THREE, { profile })`.
- Returned controller: `{ group, update(pose), getRenderStats(), getDebugState(), dispose() }`.
- `getDebugState()` returns sampled placement transforms for loopback tests only; it contains no DOM or user data.

- [ ] **Step 1: Write failing renderer tests**

Test these exact contracts:

```js
test("builds varied instanced rear families behind the planet", () => {
    const field = createQuantPlanetRearField(THREE, {
        profile: { name: "high" }
    });
    const names = [
        "RearRockChips",
        "RearArchitecturalScraps",
        "RearRingRemnants",
        "RearAtmosphericClouds",
        "RearPaperFlecks"
    ];
    for (const name of names) {
        assert.ok(field.group.getObjectByName(name));
    }
    field.update({ worldAngle: Math.PI, elapsedMs: 16000 });
    assert.ok(field.getDebugState().every((item) => item.behindSafePlane));
    field.dispose();
});

test("reports bounded rear-field render work", () => {
    const field = createQuantPlanetRearField(THREE, {
        profile: { name: "high" }
    });
    const stats = field.getRenderStats();
    assert.ok(stats.drawCalls <= 5);
    assert.ok(stats.triangles <= 5000);
    field.dispose();
});
```

Also add disposal idempotency and partial-construction rollback tests following
the existing world and glyph test patterns.

- [ ] **Step 2: Run the renderer test and confirm RED**

Run:

```powershell
node --test src/test/js/home-quant-planet-rear-field.test.mjs
```

Expected: FAIL because the renderer module and factory do not exist.

- [ ] **Step 3: Build five compact visual families**

Create five shared geometries/materials:

- irregular low-poly wedge for rock chips;
- thin beveled box for architectural scraps;
- partial torus or narrow brace for ring remnants;
- flattened merged low-poly blob for atmospheric fields;
- cone-derived shard for paper flecks.

Use one `THREE.InstancedMesh` per family. Use per-instance matrices and colors.
Atmospheric material is transparent, `depthWrite: false`, low opacity, and
background-adjacent turquoise. Every solid family uses the existing toon
palette language. Set all field meshes behind the planet with normal depth
testing and no shadows.

`update(pose)` samples every authored placement, updates instance matrices and
opacity-compatible color intensity, then marks instance matrices/colors dirty.
It must not schedule frames.

- [ ] **Step 4: Run rear-field tests and confirm GREEN**

Run:

```powershell
node --test src/test/js/home-quant-planet-rear-field*.test.mjs
```

Expected: layout and renderer tests PASS.

- [ ] **Step 5: Commit the renderer**

```powershell
git add src/main/resources/static/js/home-quant-planet-rear-field.js src/test/js/home-quant-planet-rear-field.test.mjs src/test/js/support/three-test-double.mjs
git commit -m "feat: render diverse rear orbit field"
```

---

### Task 3: Scene Ownership, Lifecycle, and Budgets

**Files:**
- Modify: `src/main/resources/static/js/home-quant-planet-scene.js`
- Modify: `src/test/js/home-quant-planet-composition.test.mjs`
- Modify: `src/test/java/com/kingyurina/demo/web/HomePageTemplateContractTest.java`

**Interfaces:**
- Consumes: `createQuantPlanetRearField(THREE, { profile })`.
- Scene controller continues to expose `{ resize, setVisible, render, getDiagnostics, dispose }`.
- `getDiagnostics()` adds `rearField` statistics to existing world and glyph statistics.

- [ ] **Step 1: Write failing scene integration tests**

Add a rear-field factory test seam to `createQuantPlanetScene`:

```js
const controller = await createQuantPlanetScene({
    canvas,
    profile,
    three: THREE,
    glyphFactory,
    rearFieldFactory
});

controller.render({
    worldAngle: Math.PI / 2,
    elapsedMs: 9000,
    pointer: { x: 1, y: -1 },
    reduceMotion: false
});

assert.deepEqual(rearField.updates.at(-1), {
    worldAngle: Math.PI / 2,
    elapsedMs: 9000,
    reduceMotion: false
});
assert.equal("pointer" in rearField.updates.at(-1), false);
```

Add tests proving:

- scene child order is rear field, planet, glyphs;
- rear-field stats are included in diagnostics;
- renderer construction rollback disposes the rear field;
- normal disposal attempts rear field, glyphs, world, and renderer exactly once;
- high limit is 80/145000 and balanced limit is 70/95000.

- [ ] **Step 2: Run focused scene tests and confirm RED**

Run:

```powershell
node --test src/test/js/home-quant-planet-composition.test.mjs
```

Expected: FAIL because `rearFieldFactory` and rear-field ownership are absent.

- [ ] **Step 3: Integrate the rear field**

Import the new factory, create the field after the world and before glyph
layout, add `rearField.group` before `world.group`, forward only autonomous
pose data, include statistics, and include the field in rollback/disposal:

```js
rearField.update({
    worldAngle: pose.worldAngle,
    elapsedMs: pose.elapsedMs,
    reduceMotion: pose.reduceMotion
});
```

Do not modify `home-quant-planet-runtime.js` RAF ownership.

- [ ] **Step 4: Run scene and runtime tests**

Run:

```powershell
node --test src/test/js/home-quant-planet-composition.test.mjs src/test/js/home-quant-planet-runtime.test.mjs
```

Expected: PASS with no new RAF, wheel, pointer, or route listener.

- [ ] **Step 5: Commit scene integration**

```powershell
git add src/main/resources/static/js/home-quant-planet-scene.js src/test/js/home-quant-planet-composition.test.mjs src/test/java/com/kingyurina/demo/web/HomePageTemplateContractTest.java
git commit -m "feat: integrate rear field scene layer"
```

---

### Task 4: Enlarge the Visible Planet to 1.2x

**Files:**
- Modify: `src/main/resources/static/js/home-quant-planet-composition.js`
- Modify: `src/main/resources/static/js/home-quant-planet-scene.js`
- Modify: `src/main/resources/static/js/home-quant-planet-world.js`
- Modify: `src/test/js/home-quant-planet-composition.test.mjs`
- Modify: `src/test/js/home-quant-planet-world.test.mjs`

**Interfaces:**
- Add `REFERENCE_HERO.planetDiameterScale = 1.2`.
- Add `visualScale = 1` to `fitReferenceWorld(...)`.
- Keep `world.getFramingEnvelope()` limited to structural planet content; rear
  field geometry is never part of it.

- [ ] **Step 1: Write failing visual-coverage and clipping tests**

Add a baseline measurement helper that compares projected structural bounds:

```js
assert.equal(REFERENCE_HERO.planetDiameterScale, 1.2);
const enlarged = fitReferenceWorld({
    center,
    radius,
    aspect: 16 / 9,
    verticalFovDegrees: 34,
    visualScale: REFERENCE_HERO.planetDiameterScale
});
assert.ok(Math.abs(
    enlarged.projectedDiameterRatio / baseline.projectedDiameterRatio - 1.2
) <= .03);
```

For `1280x720`, `1440x900`, `1920x1080`, `768x1024`, and `375x812`, sample
world angles `0`, `π/2`, `π`, and `3π/2`; assert roundness, no horizontal
overflow, no structural clipping, and preserved CTA safe area.

- [ ] **Step 2: Run composition tests and confirm RED**

Run:

```powershell
node --test src/test/js/home-quant-planet-composition.test.mjs src/test/js/home-quant-planet-world.test.mjs
```

Expected: FAIL because `planetDiameterScale` and `visualScale` are absent.

- [ ] **Step 3: Implement composition-scale fitting**

Use the full structural envelope for near/far clipping, but calculate camera
distance against `radius / visualScale`. Clamp portrait scale only when the
actual sampled structural points would leave the safe frame:

```js
const requestedVisualScale = Math.max(1, Number(visualScale) || 1);
const fittedRadius = safeRadius / requestedVisualScale;
```

Return the applied `visualScale` and projected diameter ratio from the fitter
for deterministic tests. Keep glyph layout based on the structural envelope,
not the rear-field radius.

- [ ] **Step 4: Run composition tests and tune only through measured bounds**

Run:

```powershell
node --test src/test/js/home-quant-planet-composition.test.mjs src/test/js/home-quant-planet-world.test.mjs
```

Expected: PASS at `1.2x ±3%` desktop coverage and safe portrait clamping.

- [ ] **Step 5: Commit the framing change**

```powershell
git add src/main/resources/static/js/home-quant-planet-composition.js src/main/resources/static/js/home-quant-planet-scene.js src/main/resources/static/js/home-quant-planet-world.js src/test/js/home-quant-planet-composition.test.mjs src/test/js/home-quant-planet-world.test.mjs
git commit -m "feat: enlarge quant planet composition"
```

---

### Task 5: Deepen Authored Planet Detail

**Files:**
- Modify: `src/main/resources/static/js/home-quant-planet-world-layout.js`
- Modify: `src/main/resources/static/js/home-quant-planet-world.js`
- Modify: `src/test/js/home-quant-planet-world.test.mjs`

**Interfaces:**
- Existing `createQuantPlanetWorld(...)` public interface remains unchanged.
- Extend existing named instance families rather than adding independent mesh
  controllers.

- [ ] **Step 1: Write failing detail-family tests**

Require these minimum high-profile counts:

```js
const expectedDetailCounts = {
    ReferenceIndustrialBarrelBands: 12,
    ReferenceIndustrialCableStays: 10,
    ReferenceVillageRoofCaps: 18,
    ReferenceVillageChimneys: 12,
    ReferenceTempleBeamEnds: 10,
    ReferenceMountainStrata: 16,
    ReferenceWaterFoamAccents: 20,
    ReferenceTerrainRetainingEdges: 14,
    ReferenceCanopyForks: 18,
    ReferenceUnderCanopyStones: 16
};
```

Assert each mesh exists, is instanced, meets the count, uses readable palette
colors, and remains distributed across the appropriate semantic districts.
Retain existing tests for black-floor avoidance and dense yaw coverage.

- [ ] **Step 2: Run world tests and confirm RED**

Run:

```powershell
node --test src/test/js/home-quant-planet-world.test.mjs
```

Expected: FAIL on the first missing named detail family.

- [ ] **Step 3: Author and attach detail placements**

Add deterministic placement tables grouped by district. Extend the existing
industrial, village, temple, water, terrain, and foliage builder families with
shared geometry/materials. Use scale, rotation, and color variation to prevent
obvious repetition. Ensure additions follow surface normals and do not inflate
the structural framing envelope beyond the Task 4 safe bounds.

- [ ] **Step 4: Run world tests and render-budget tests**

Run:

```powershell
node --test src/test/js/home-quant-planet-world.test.mjs src/test/js/home-quant-planet-composition.test.mjs
```

Expected: PASS; complete scene remains under the global budgets.

- [ ] **Step 5: Commit the planet detail pass**

```powershell
git add src/main/resources/static/js/home-quant-planet-world-layout.js src/main/resources/static/js/home-quant-planet-world.js src/test/js/home-quant-planet-world.test.mjs src/test/js/home-quant-planet-composition.test.mjs
git commit -m "feat: enrich quant planet miniature detail"
```

---

### Task 6: Static Fallback, Responsive Behavior, and Final Verification

**Files:**
- Modify: `src/main/resources/static/img/home-quant-planet-fallback.svg`
- Modify: `src/main/resources/static/css/home-quant-planet.css`
- Modify: `src/main/resources/templates/index.html`
- Modify: `src/test/java/com/kingyurina/demo/web/HomePageTemplateContractTest.java`
- Modify: `src/test/js/home-quant-planet-composition.test.mjs`

**Interfaces:**
- Existing fallback element and route markup remain unchanged.
- Update asset cache query versions in `index.html`.

- [ ] **Step 1: Write failing fallback and responsive contracts**

Add assertions for SVG groups:

```java
for (String group : List.of(
        "fallback-rear-rocks",
        "fallback-rear-scraps",
        "fallback-rear-rings",
        "fallback-rear-atmosphere",
        "fallback-rear-flecks")) {
    assertTrue(svg.contains("id=\"" + group + "\""));
}
```

Assert CSS preserves full-screen sizing, no overflow, fallback containment, CTA
safe area, and disabled animations under `prefers-reduced-motion: reduce`.

- [ ] **Step 2: Run template tests and confirm RED**

Run:

```powershell
mvn.cmd "-Dmaven.repo.local=C:\dev\codex-demo\.m2\repository" -Dtest=HomePageTemplateContractTest test
```

Expected: FAIL because the new fallback groups are absent.

- [ ] **Step 3: Update SVG, CSS, and cache versions**

Add wide asymmetric rear fragments and turquoise atmospheric blotches to the
fallback SVG behind the planet group. Increase only the planet artwork group to
the measured 1.2x composition while keeping the ENTER button clear. Update CSS
only where needed for responsive containment and reduced motion; do not add a
CSS animation that duplicates the Three.js rear field.

- [ ] **Step 4: Run all automated verification**

Run:

```powershell
node --test src/test/js/*.test.mjs
mvn.cmd "-Dmaven.repo.local=C:\dev\codex-demo\.m2\repository" test
git diff --check
```

Expected: all JavaScript and Maven tests PASS, build reports success, and
`git diff --check` emits no errors.

- [ ] **Step 5: Perform browser verification**

Start or rebuild the local server and verify:

- `1920x1080`, `1440x900`, and `1280x720`;
- `768x1024` and `375x812`;
- phases near `0`, `12`, `24`, `36`, `48`, and `64` seconds;
- all rear objects remain behind the planet and title;
- object families and scale tiers remain visibly diverse;
- cloud-like atmospheric shapes are readable but low contrast;
- planet coverage is `1.2x ±3%` on desktop;
- right-side structures, title, and ENTER control do not clip;
- no horizontal overflow;
- diagnostics remain within profile budgets;
- reduced-motion and static modes show a balanced frozen composition.

- [ ] **Step 6: Commit fallback and final integration**

```powershell
git add src/main/resources/static/img/home-quant-planet-fallback.svg src/main/resources/static/css/home-quant-planet.css src/main/resources/templates/index.html src/test/java/com/kingyurina/demo/web/HomePageTemplateContractTest.java src/test/js/home-quant-planet-composition.test.mjs
git commit -m "feat: complete rear orbit planet experience"
```
