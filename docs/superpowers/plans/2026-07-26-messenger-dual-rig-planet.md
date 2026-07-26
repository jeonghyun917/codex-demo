# Messenger Dual-Rig Planet Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a video-calibrated fullscreen homepage where a dense miniature planet rotates autonomously and nine independent extruded title glyphs react to pointer or touch input without affecting the planet.

**Architecture:** Keep the existing single Three.js renderer and animation-frame owner, but split the scene into an autonomous `PlanetRoot` and a pointer-driven `GlyphRig`. Trace the existing local transparent title image into nine groups of vector contours at startup, extrude those contours into real meshes, and keep the DOM title image only as the no-JavaScript, reduced-motion, and WebGL-failure fallback.

**Tech Stack:** Java 21, Spring Boot, Thymeleaf, Three.js r165 ES modules, vanilla JavaScript, CSS, Node's built-in test runner, JUnit 5, Jsoup/XML contract checks, MariaDB-backed existing application routes.

## Global Constraints

- Work only in `C:\dev\codex-demo\.worktrees\quant-planet-homepage` on branch `codex/quant-planet-homepage`.
- Do not modify or stage unrelated files from `main` or any other worktree.
- Keep `/quant` and all destination pages DB-backed; add no external API calls to page-request paths.
- Do not download or embed reference-site source code, models, textures, shaders, or fonts.
- Preserve the exact visible title silhouettes from `/img/home-messenger-reference-title.png`.
- Keep exactly one visible navigation action, the yellow `ENTER` anchor targeting `/quant`.
- Keep the homepage fullscreen with no wheel, page-scroll, camera-orbit, or planet-drag behavior.
- Cap device pixel ratio at `1.5`, draw calls at `75`, triangles at `140,000`, and established desktop average render time at `12ms`.
- Keep one animation-frame owner and allocate no geometry, material, texture, or vectors inside the steady render loop.
- Run Java verification with `mvn.cmd "-Dmaven.repo.local=C:\dev\codex-demo\.m2\repository" test` in this isolated worktree.
- Use test-driven development for every behavior change and commit after each independently reviewable task.

## File Structure

### New files

- `src/main/resources/static/js/home-quant-planet-glyph-contours.js`
  - Reads the local title image's alpha mask and converts its three-by-three cells into simplified outer and hole contours.
- `src/main/resources/static/js/home-quant-planet-glyphs.js`
  - Converts contours into nine extruded Three.js glyph objects, owns pointer damping, layout, render statistics, and disposal.
- `src/main/resources/static/js/home-quant-planet-world-layout.js`
  - Holds deterministic spherical landmark placements so all azimuth sectors stay populated and reviewable.
- `src/test/js/home-quant-planet-glyph-contours.test.mjs`
  - Verifies nine-cell extraction, outer/hole classification, coordinate normalization, and simplification.
- `src/test/js/home-quant-planet-glyphs.test.mjs`
  - Verifies nine independent extruded objects, response limits, intro state, reduced motion, statistics, and disposal.
- `docs/superpowers/verification/2026-07-26-messenger-dual-rig-planet.md`
  - Records final automated results, browser bounds, timed rotation comparison, pointer poses, and fallback evidence.

### Modified files

- `src/main/resources/static/js/home-quant-planet-composition.js`
  - Stores video-calibrated rotation and glyph-response constants.
- `src/main/resources/static/js/home-quant-planet-motion.js`
  - Produces autonomous planet yaw and a separate glyph pointer target.
- `src/main/resources/static/js/home-quant-planet-input.js`
  - Reports pointer or primary-touch position only; removes drag angle and inertia ownership.
- `src/main/resources/static/js/home-quant-planet-runtime.js`
  - Removes drag release state and passes intro title progress into the scene pose.
- `src/main/resources/static/js/home-quant-planet-world.js`
  - Rebuilds the planet as a recentered, densely populated round world and consumes the authored layout data.
- `src/main/resources/static/js/home-quant-planet-scene.js`
  - Creates and disposes both rigs, keeps the camera fixed, and combines their diagnostics.
- `src/main/resources/static/js/home-quant-planet.js`
  - Keeps fallback title selection while loading the new runtime modules.
- `src/main/resources/static/css/home-quant-planet.css`
  - Treats the DOM title as fallback-only, removes grab affordances, and preserves responsive/fallback composition.
- `src/main/resources/static/img/home-quant-planet-fallback.svg`
  - Mirrors the denser rounded planet for static, reduced-motion, and WebGL-failure modes.
- `src/main/resources/templates/index.html`
  - Marks the title image as the fallback layer and bumps local asset versions.
- Existing five `src/test/js/home-quant-planet-*.test.mjs` files
  - Replace drag/camera expectations with dual-rig contracts.
- `src/test/java/com/kingyurina/demo/web/HomePageTemplateContractTest.java`
  - Includes the new local modules and verifies the fallback/runtime split.

---

### Task 1: Separate autonomous planet time from pointer input

**Files:**
- Modify: `src/main/resources/static/js/home-quant-planet-composition.js`
- Modify: `src/main/resources/static/js/home-quant-planet-motion.js`
- Modify: `src/main/resources/static/js/home-quant-planet-input.js`
- Modify: `src/main/resources/static/js/home-quant-planet-runtime.js`
- Modify: `src/test/js/home-quant-planet-motion.test.mjs`
- Modify: `src/test/js/home-quant-planet-input.test.mjs`
- Modify: `src/test/js/home-quant-planet-runtime.test.mjs`

**Interfaces:**
- Produces: `createHeroPose({ elapsedMs, worldAngle, pointer, titleProgress, reduceMotion })`.
- Produces: pose shape `{ worldAngle, pointer, titleProgress, elapsedMs, reduceMotion }`.
- Produces: input snapshot `{ pointer: { x, y }, touching: boolean }`.
- Removes: `dragAngle`, `dragging`, `settling`, `setBaseAngle`, release impulse, and camera/planet drag constants.

- [ ] **Step 1: Write failing motion tests for independent yaw and title progress**

Add these contracts to `home-quant-planet-motion.test.mjs`:

```js
test("advances autonomous planet yaw independently of pointer", () => {
    const left = createHeroPose({
        elapsedMs: 9000,
        worldAngle: .25,
        pointer: { x: -1, y: .8 },
        titleProgress: .7
    });
    const right = createHeroPose({
        elapsedMs: 9000,
        worldAngle: .25,
        pointer: { x: 1, y: -.8 },
        titleProgress: .7
    });

    assert.equal(left.worldAngle, right.worldAngle);
    assert.deepEqual(left.pointer, { x: -1, y: .8 });
    assert.deepEqual(right.pointer, { x: 1, y: -.8 });
    assert.equal(left.titleProgress, .7);
    assert.equal(
        left.worldAngle,
        .25 + Math.PI * 2 * 9000 / REFERENCE_HERO.worldRevolutionMs
    );
    assert.equal("dragAngle" in left, false);
});

test("reduced motion freezes both autonomous and pointer targets", () => {
    assert.deepEqual(createHeroPose({
        elapsedMs: 9000,
        worldAngle: .4,
        pointer: { x: 1, y: -1 },
        titleProgress: .3,
        reduceMotion: true
    }), {
        worldAngle: .4,
        pointer: { x: 0, y: 0 },
        titleProgress: 1,
        elapsedMs: 9000,
        reduceMotion: true
    });
});
```

- [ ] **Step 2: Write failing input tests that reject planet drag ownership**

Replace drag/inertia cases in `home-quant-planet-input.test.mjs` with:

```js
test("mouse down and drag report pointer position without a drag angle", () => {
    const { controller, surface } = createController();
    surface.emit("pointerdown", {
        ...primaryPointer(7, 100, 75),
        button: 0,
        pointerType: "mouse"
    });
    surface.emit("pointermove", {
        ...primaryPointer(7, 400, 225),
        pointerType: "mouse"
    });

    const state = controller.getState();
    assert.deepEqual(state.pointer, { x: 1, y: .5 });
    assert.equal("dragAngle" in state, false);
    assert.equal("dragging" in state, false);
    assert.deepEqual(surface.captureCalls, []);
});

test("primary touch resets glyph target on release", () => {
    const { controller, surface } = createController();
    surface.emit("pointerdown", {
        ...primaryPointer(8, 300, 75),
        button: 0,
        pointerType: "touch"
    });
    assert.equal(controller.getState().touching, true);
    surface.emit("pointerup", {
        ...primaryPointer(8, 300, 75),
        pointerType: "touch"
    });
    assert.deepEqual(controller.getState(), {
        pointer: { x: 0, y: 0 },
        touching: false
    });
});
```

- [ ] **Step 3: Run focused tests and verify they fail**

Run:

```powershell
node --test src/test/js/home-quant-planet-motion.test.mjs `
  src/test/js/home-quant-planet-input.test.mjs `
  src/test/js/home-quant-planet-runtime.test.mjs
```

Expected: failures reference the old `dragAngle`, capture, settle, and release behavior.

- [ ] **Step 4: Implement the new constants and pose**

Set `REFERENCE_HERO` to the approved contract:

```js
export const REFERENCE_HERO = Object.freeze({
    titleAssetUrl: "/img/home-messenger-reference-title.png",
    introDeadlineMs: 3999,
    worldRevolutionMs: 36000,
    glyphYawRadians: Math.PI / 9,
    glyphPitchRadians: Math.PI / 15,
    glyphVariationRadians: Math.PI / 45,
    glyphResponsePerSecond: 8.5
});
```

Implement `createHeroPose` without any drag state:

```js
export function createHeroPose({
    elapsedMs = 0,
    worldAngle = 0,
    pointer = { x: 0, y: 0 },
    titleProgress = 1,
    reduceMotion = false
} = {}) {
    const elapsed = Math.max(0, Number(elapsedMs) || 0);
    const ambient = reduceMotion
        ? 0
        : elapsed / REFERENCE_HERO.worldRevolutionMs * Math.PI * 2;
    return {
        worldAngle: (Number(worldAngle) || 0) + ambient,
        pointer: reduceMotion ? { x: 0, y: 0 } : {
            x: Number(pointer?.x) || 0,
            y: Number(pointer?.y) || 0
        },
        titleProgress: reduceMotion ? 1 : clampUnit(titleProgress),
        elapsedMs: elapsed,
        reduceMotion
    };
}
```

- [ ] **Step 5: Simplify input to pointer and primary-touch ownership**

Implement the controller state as:

```js
let activeTouchId = null;
let pointer = { x: 0, y: 0 };

function snapshot() {
    return {
        pointer: { ...pointer },
        touching: activeTouchId !== null
    };
}

function updatePosition(event) {
    pointer = normalizePointer(
        event.clientX,
        event.clientY,
        surface?.getBoundingClientRect?.()
    );
    notify();
}

function handlePointerDown(event) {
    if (destroyed || event?.isPrimary !== true) return;
    if (event.pointerType === "touch") activeTouchId = event.pointerId;
    updatePosition(event);
}

function handlePointerMove(event) {
    if (destroyed || event?.isPrimary !== true) return;
    if (activeTouchId !== null && event.pointerId !== activeTouchId) return;
    updatePosition(event);
}

function finishPointer(event) {
    if (event?.pointerId !== activeTouchId) return;
    activeTouchId = null;
    pointer = { x: 0, y: 0 };
    notify();
}
```

Mouse `pointerleave` resets to `{ x: 0, y: 0 }`. Destruction removes every listener once and performs no pointer capture or timers.

- [ ] **Step 6: Remove runtime release inertia and pass title progress**

Keep the latest intro state in `currentIntroState`. In `tick`, replace release calculations with:

```js
const incrementalPose = createHeroPose({
    elapsedMs: ambientDeltaTime,
    worldAngle,
    pointer: inputState.pointer,
    titleProgress: currentIntroState.title,
    reduceMotion: false
});
worldAngle = incrementalPose.worldAngle;
scene?.render?.({
    ...incrementalPose,
    elapsedMs: activeElapsedMs
});
```

Delete `lastDragDelta`, `releaseElapsedMs`, `releaseImpulse`,
`advanceReleaseOffset`, `MAX_RELEASE_OFFSET_RADIANS`, and
`RELEASE_IMPULSE_SCALE`.

- [ ] **Step 7: Run focused tests and commit**

Run the Step 3 command. Expected: all focused tests pass.

```powershell
git add src/main/resources/static/js/home-quant-planet-composition.js `
  src/main/resources/static/js/home-quant-planet-motion.js `
  src/main/resources/static/js/home-quant-planet-input.js `
  src/main/resources/static/js/home-quant-planet-runtime.js `
  src/test/js/home-quant-planet-motion.test.mjs `
  src/test/js/home-quant-planet-input.test.mjs `
  src/test/js/home-quant-planet-runtime.test.mjs
git commit -m "refactor: separate planet rotation from pointer input"
```

### Task 2: Trace the local title image into nine contour sets

**Files:**
- Create: `src/main/resources/static/js/home-quant-planet-glyph-contours.js`
- Create: `src/test/js/home-quant-planet-glyph-contours.test.mjs`

**Interfaces:**
- Produces: `GLYPH_GRID`, an immutable nine-region three-by-three grid.
- Produces: `extractGlyphContours(imageData, options)`.
- Returns: nine arrays of `{ outer: Array<{x,y}>, holes: Array<Array<{x,y}>> }`.
- Produces: `loadLocalImageData(assetUrl, documentTarget)`.

- [ ] **Step 1: Write synthetic alpha-mask tests**

Create a helper that paints a binary rectangle and a transparent hole into an
`ImageData`-shaped object. Test:

```js
test("extracts exactly nine normalized glyph cells", () => {
    const image = createNineCellMask(90, 90);
    const glyphs = extractGlyphContours(image, {
        alphaThreshold: 32,
        simplifyTolerance: 0
    });

    assert.equal(glyphs.length, 9);
    assert.ok(glyphs.every((glyph) => glyph.length === 1));
    assert.ok(glyphs.flatMap((glyph) => glyph)
        .every(({ outer }) => signedArea(outer) > 0));
});

test("assigns enclosed transparent loops as holes", () => {
    const image = createDonutInFirstCell(90, 90);
    const [first] = extractGlyphContours(image, {
        alphaThreshold: 32,
        simplifyTolerance: 0
    });

    assert.equal(first.length, 1);
    assert.equal(first[0].holes.length, 1);
    assert.ok(signedArea(first[0].outer) > 0);
    assert.ok(signedArea(first[0].holes[0]) < 0);
});

test("simplifies pixel stairs while preserving closed contours", () => {
    const image = createNineCellMask(90, 90);
    const raw = extractGlyphContours(image, { simplifyTolerance: 0 });
    const simplified = extractGlyphContours(image, {
        simplifyTolerance: 1.5
    });
    assert.ok(simplified[0][0].outer.length < raw[0][0].outer.length);
    assert.deepEqual(
        simplified[0][0].outer[0],
        simplified[0][0].outer.at(-1)
    );
});
```

- [ ] **Step 2: Run the contour test and verify it fails**

```powershell
node --test src/test/js/home-quant-planet-glyph-contours.test.mjs
```

Expected: module-not-found failure for `home-quant-planet-glyph-contours.js`.

- [ ] **Step 3: Implement deterministic pixel-edge contour extraction**

Define the source grid as normalized thirds so transparent padding does not
alter the title spacing:

```js
export const GLYPH_GRID = Object.freeze(
    Array.from({ length: 9 }, (_, index) => Object.freeze({
        column: index % 3,
        row: Math.floor(index / 3),
        left: (index % 3) / 3,
        top: Math.floor(index / 3) / 3,
        width: 1 / 3,
        height: 1 / 3
    }))
);
```

For every opaque pixel in a region, emit a clockwise directed edge for each
side whose neighboring pixel is transparent or outside the region. Link edges
whose end key equals the next start key, close each loop, simplify with
Ramer-Douglas-Peucker, invert image-space Y, and normalize coordinates around
the cell center:

```js
export function extractGlyphContours(imageData, {
    alphaThreshold = 32,
    simplifyTolerance = 1.4
} = {}) {
    validateImageData(imageData);
    return GLYPH_GRID.map((region) => {
        const bounds = resolvePixelBounds(region, imageData);
        const loops = traceOpaquePixelEdges(
            imageData,
            bounds,
            alphaThreshold
        ).map((loop) => closeLoop(
            simplifyClosedLoop(loop, simplifyTolerance)
        )).map((loop) => normalizeLoop(loop, bounds));
        return classifyOuterAndHoleLoops(loops);
    });
}
```

`classifyOuterAndHoleLoops` sorts loops by absolute area, makes positive-area
loops outers, reverses negative/positive orientation when required, and assigns
each contained loop to the smallest enclosing outer using an even-odd point
test. Empty glyph regions throw `Error("Missing alpha contour for glyph N")`
instead of silently producing a flat title.

- [ ] **Step 4: Implement local image loading**

```js
export async function loadLocalImageData(
    assetUrl,
    documentTarget = globalThis.document
) {
    const image = new Image();
    image.decoding = "async";
    image.src = assetUrl;
    await image.decode();
    const canvas = documentTarget.createElement("canvas");
    canvas.width = image.naturalWidth;
    canvas.height = image.naturalHeight;
    const context = canvas.getContext("2d", {
        alpha: true,
        willReadFrequently: true
    });
    if (!context) throw new Error("2D canvas is unavailable");
    context.drawImage(image, 0, 0);
    return context.getImageData(0, 0, canvas.width, canvas.height);
}
```

The loader accepts only the already configured local asset URL and introduces
no network or package dependency.

- [ ] **Step 5: Run the test and commit**

```powershell
node --test src/test/js/home-quant-planet-glyph-contours.test.mjs
git add src/main/resources/static/js/home-quant-planet-glyph-contours.js `
  src/test/js/home-quant-planet-glyph-contours.test.mjs
git commit -m "feat: trace title image into glyph contours"
```

Expected: all contour tests pass.

### Task 3: Build and animate nine extruded glyph objects

**Files:**
- Create: `src/main/resources/static/js/home-quant-planet-glyphs.js`
- Create: `src/test/js/home-quant-planet-glyphs.test.mjs`
- Modify: `src/main/resources/static/js/home-quant-planet-composition.js`

**Interfaces:**
- Consumes: `extractGlyphContours`, `loadLocalImageData`, and `REFERENCE_HERO`.
- Produces: `await createQuantGlyphRig(THREE, options)`.
- Returns: `{ group, update, setLayout, getRenderStats, dispose }`.
- `setLayout({ center, radius, aspect })` places the title in front of the world.
- `update({ pointer, elapsedMs, titleProgress, reduceMotion })` changes glyphs only.

- [ ] **Step 1: Write failing construction and motion tests**

Use a synthetic nine-cell `imageDataSource` and assert:

```js
test("builds nine independent extruded glyph pivots", async (t) => {
    const rig = await createRig(t);
    const pivots = rig.group.children.filter(
        ({ name }) => name.startsWith("ReferenceGlyphPivot-")
    );

    assert.equal(rig.group.name, "ReferenceGlyphRig");
    assert.equal(pivots.length, 9);
    assert.equal(new Set(pivots).size, 9);
    pivots.forEach((pivot, index) => {
        const mesh = pivot.getObjectByName(`ReferenceGlyphMesh-${index + 1}`);
        assert.equal(mesh?.geometry?.type, "ExtrudeGeometry");
        assert.ok(mesh.geometry.parameters.options.depth > 0);
    });
});

test("pointer moves glyphs within limits and leaves the parent layout fixed",
        async (t) => {
    const rig = await createRig(t);
    rig.setLayout({
        center: { x: 0, y: 0, z: 0 },
        radius: 5,
        aspect: 1
    });
    rig.group.updateMatrix();
    const parentBefore = rig.group.matrix.clone();
    rig.update({
        pointer: { x: 1, y: -1 },
        elapsedMs: 1000,
        titleProgress: 1,
        reduceMotion: false
    });
    rig.group.updateMatrix();

    assert.deepEqual(rig.group.matrix.elements, parentBefore.elements);
    const pivots = glyphPivots(rig.group);
    assert.ok(pivots.every((pivot) =>
        Math.abs(pivot.rotation.y) <= REFERENCE_HERO.glyphYawRadians
            + REFERENCE_HERO.glyphVariationRadians + 1e-6));
    assert.ok(pivots.some((pivot) => Math.abs(pivot.rotation.y) > .01));
});

test("reduced motion restores all glyphs to authored rest poses", async (t) => {
    const rig = await createRig(t);
    rig.update({
        pointer: { x: 1, y: 1 },
        elapsedMs: 1000,
        titleProgress: .2,
        reduceMotion: true
    });
    glyphPivots(rig.group).forEach((pivot) => {
        assert.equal(pivot.rotation.x, pivot.userData.restPitch);
        assert.equal(pivot.rotation.y, pivot.userData.restYaw);
    });
    assert.equal(rig.group.visible, true);
});
```

Add resource tests that call `dispose()` twice and count one disposal per
geometry, material, texture, and outline geometry.

- [ ] **Step 2: Run the glyph-rig test and verify it fails**

```powershell
node --test src/test/js/home-quant-planet-glyphs.test.mjs
```

Expected: module-not-found failure for `home-quant-planet-glyphs.js`.

- [ ] **Step 3: Convert contour records into Three.js shapes**

Implement:

```js
function createShape(THREE, record) {
    const shape = new THREE.Shape();
    record.outer.forEach(({ x, y }, index) => {
        if (index === 0) shape.moveTo(x, y);
        else shape.lineTo(x, y);
    });
    record.holes.forEach((hole) => {
        const path = new THREE.Path();
        hole.forEach(({ x, y }, index) => {
            if (index === 0) path.moveTo(x, y);
            else path.lineTo(x, y);
        });
        shape.holes.push(path);
    });
    return shape;
}
```

Build one `THREE.ExtrudeGeometry(shapes, options)` per grid cell so disconnected
components still belong to one glyph object:

```js
const geometry = ownGeometry(new THREE.ExtrudeGeometry(shapes, {
    depth: .24,
    bevelEnabled: true,
    bevelSegments: 1,
    steps: 1,
    bevelSize: .025,
    bevelThickness: .025,
    curveSegments: 1
}));
geometry.center();
```

Use a two-material mesh for off-white faces and gray-green sides, plus one
`THREE.LineSegments(new THREE.EdgesGeometry(geometry, 28), inkLineMaterial)`
per glyph. Keep all nine meshes and nine outlines under independent pivots.

- [ ] **Step 4: Implement physical pointer response and layout**

Use fixed per-glyph values:

```js
const GLYPH_RESPONSE = Object.freeze([
    [.96, .94, -.018, .92, -.018],
    [1.02, .98, .012, 1.04, .012],
    [.94, 1.04, -.01, .96, -.01],
    [1.04, .96, .014, 1.06, .014],
    [1.00, 1.00, 0, 1, 0],
    [.97, 1.03, -.014, .94, -.014],
    [.95, 1.02, .01, .98, .01],
    [1.03, .95, -.012, 1.03, -.012],
    [.98, .97, .018, .9, .018]
]);
```

For each frame:

```js
const deltaSeconds = Math.min(
    .05,
    Math.max(0, elapsedMs - previousElapsedMs) / 1000
);
const targetYaw = pointer.x * REFERENCE_HERO.glyphYawRadians;
const targetPitch = -pointer.y * REFERENCE_HERO.glyphPitchRadians;

pivots.forEach((pivot, index) => {
    const [
        yawGain,
        pitchGain,
        restYaw,
        responseGain,
        depthOffset
    ] = GLYPH_RESPONSE[index];
    const alpha = reduceMotion
        ? 1
        : 1 - Math.exp(
            -REFERENCE_HERO.glyphResponsePerSecond
                * responseGain * deltaSeconds
        );
    pivot.rotation.y += (
        restYaw + targetYaw * yawGain - pivot.rotation.y
    ) * alpha;
    pivot.rotation.x += (
        pivot.userData.restPitch + targetPitch * pitchGain
            - pivot.rotation.x
    ) * alpha;
    pivot.position.z += (depthOffset - pivot.position.z) * alpha;
});
```

`setLayout` scales the three-by-three glyph arrangement to approximately `62%`
of the planet diameter, stores `finalZ = center.z + radius * 1.05`, and uses a
slightly narrower scale on portrait aspects. `titleProgress` controls
visibility, scales the rig from `.82` to `1`, and moves it from
`finalZ - radius * .18` to `finalZ`; this reproduces the approved compressed
depth reveal without moving the camera.

- [ ] **Step 5: Add statistics, rollback, and disposal**

`getRenderStats()` counts the nine meshes and nine line segments and their
triangles. Track every geometry and material in `Set` instances. A construction
failure disposes all previously created resources before rethrowing; normal
`dispose()` is idempotent.

- [ ] **Step 6: Run tests and commit**

```powershell
node --test src/test/js/home-quant-planet-glyph-contours.test.mjs `
  src/test/js/home-quant-planet-glyphs.test.mjs
git add src/main/resources/static/js/home-quant-planet-glyphs.js `
  src/main/resources/static/js/home-quant-planet-composition.js `
  src/test/js/home-quant-planet-glyphs.test.mjs
git commit -m "feat: build pointer-reactive extruded glyph rig"
```

### Task 4: Integrate the two rigs under one fixed camera

**Files:**
- Modify: `src/main/resources/static/js/home-quant-planet-scene.js`
- Modify: `src/test/js/home-quant-planet-composition.test.mjs`
- Modify: `src/test/js/home-quant-planet-runtime.test.mjs`

**Interfaces:**
- Consumes: `createQuantPlanetWorld` and `createQuantGlyphRig`.
- `createQuantPlanetScene` accepts optional `glyphFactory` for deterministic tests.
- Scene controller continues to expose `{ resize, setVisible, render, getDiagnostics, dispose }`.

- [ ] **Step 1: Write a failing dual-rig scene test**

Extend the renderer harness to record `QuantPlanet` and
`ReferenceGlyphRig` quaternions:

```js
test("pointer changes glyphs without changing planet or camera", async (t) => {
    const scene = await createTestScene(t, { width: 1242, height: 1270 });
    scene.controller.render(pose({
        worldAngle: .45,
        pointer: { x: -1, y: -.5 },
        elapsedMs: 1000
    }));
    scene.controller.render(pose({
        worldAngle: .45,
        pointer: { x: 1, y: .5 },
        elapsedMs: 1100
    }));

    const [left, right] = scene.frames.slice(-2);
    assert.deepEqual(
        left.planetQuaternion.toArray(),
        right.planetQuaternion.toArray()
    );
    assert.deepEqual(
        left.cameraPosition.toArray(),
        right.cameraPosition.toArray()
    );
    assert.notDeepEqual(
        left.glyphQuaternions,
        right.glyphQuaternions
    );
    assert.equal(right.glyphQuaternions.length, 9);
});
```

Add a diagnostic test asserting combined world and glyph counts remain within
`75` draw calls and `140000` triangles.

- [ ] **Step 2: Run the composition test and verify it fails**

```powershell
node --test src/test/js/home-quant-planet-composition.test.mjs
```

Expected: no `ReferenceGlyphRig` exists and the camera still changes with pointer input.

- [ ] **Step 3: Create and lay out both rigs**

In `createQuantPlanetScene`:

```js
const makeGlyphs = glyphFactory || createQuantGlyphRig;
world = createQuantPlanetWorld(THREE, { profile });
glyphs = await makeGlyphs(THREE, {
    profile,
    assetUrl: REFERENCE_HERO.titleAssetUrl
});
scene.add(world.group, glyphs.group);
```

After calculating the world envelope and on every resize:

```js
glyphs.setLayout({
    center: envelope.center,
    radius: envelope.radius,
    aspect: camera.aspect
});
camera.position.set(
    framing.target.x,
    framing.target.y,
    framing.target.z + framing.distance
);
camera.lookAt(
    framing.target.x,
    framing.target.y,
    framing.target.z
);
```

Delete `cameraYaw`, `cameraPitch`, and pointer-derived camera positioning.

- [ ] **Step 4: Update and dispose the rigs independently**

Render in this exact order:

```js
world.update({
    worldAngle: pose.worldAngle,
    elapsedMs: pose.elapsedMs,
    reduceMotion: pose.reduceMotion
});
glyphs.update({
    pointer: profile.pointerEnabled ? pose.pointer : EMPTY_POSE.pointer,
    elapsedMs: pose.elapsedMs,
    titleProgress: pose.titleProgress,
    reduceMotion: pose.reduceMotion
});
renderer.render(scene, camera);
```

On construction failure or disposal, call `glyphs.dispose()`,
`world.dispose()`, and `renderer.dispose()` exactly once even if one cleanup
throws. Combine `world.getRenderStats()` and `glyphs.getRenderStats()` in local
diagnostics before comparing against the profile budget.

- [ ] **Step 5: Run tests and commit**

```powershell
node --test src/test/js/home-quant-planet-composition.test.mjs `
  src/test/js/home-quant-planet-glyphs.test.mjs `
  src/test/js/home-quant-planet-runtime.test.mjs
git add src/main/resources/static/js/home-quant-planet-scene.js `
  src/test/js/home-quant-planet-composition.test.mjs `
  src/test/js/home-quant-planet-runtime.test.mjs
git commit -m "feat: integrate independent planet and glyph rigs"
```

### Task 5: Rebuild the planet as a dense full-rotation miniature

**Files:**
- Create: `src/main/resources/static/js/home-quant-planet-world-layout.js`
- Modify: `src/main/resources/static/js/home-quant-planet-world.js`
- Modify: `src/test/js/home-quant-planet-world.test.mjs`
- Modify: `src/test/js/home-quant-planet-composition.test.mjs`

**Interfaces:**
- Produces: immutable `WORLD_DISTRICTS`, `TERRAIN_PATCHES`,
  `VILLAGE_PLACEMENTS`, `FOREST_BELTS`, and `DEBRIS_PLACEMENTS`.
- `createQuantPlanetWorld` keeps `{ group, update, getFramingEnvelope, getRenderStats, dispose }`.
- `world.update` consumes `{ worldAngle, elapsedMs, reduceMotion }` only.

- [ ] **Step 1: Write failing full-rotation density tests**

Add helpers that collect named landmark anchors and convert `(x, z)` to eight
azimuth bins. Test:

```js
test("populates every azimuth sector with authored landmarks", (t) => {
    const world = createWorld(t);
    const bins = new Set();
    world.group.traverse((object) => {
        if (!object.userData?.landmarkAnchor) return;
        const angle = Math.atan2(object.position.z, object.position.x);
        bins.add(Math.floor((angle + Math.PI) / (Math.PI * 2) * 8) % 8);
    });
    assert.deepEqual([...bins].sort(), [0, 1, 2, 3, 4, 5, 6, 7]);
});

test("planet yaw is unaffected by pointer-shaped extra input", (t) => {
    const world = createWorld(t);
    world.update({
        worldAngle: .7,
        pointer: { x: -1, y: 1 },
        elapsedMs: 1000,
        reduceMotion: false
    });
    const first = world.group.quaternion.clone();
    world.update({
        worldAngle: .7,
        pointer: { x: 1, y: -1 },
        elapsedMs: 1100,
        reduceMotion: false
    });
    assert.deepEqual(world.group.quaternion.toArray(), first.toArray());
});

test("keeps a rounded occupied silhouette at eight yaw samples", (t) => {
    const world = createWorld(t);
    for (let index = 0; index < 8; index += 1) {
        world.update({
            worldAngle: index * Math.PI / 4,
            elapsedMs: index * 1000,
            reduceMotion: false
        });
        const coverage = projectedOccupancy(world.group);
        assert.ok(coverage.widthToHeight >= .72);
        assert.ok(coverage.widthToHeight <= 1.28);
        assert.ok(coverage.occupiedQuadrants >= 4);
    }
});
```

Retain existing instancing, resource rollback, envelope, and render-budget tests.

- [ ] **Step 2: Run world tests and verify they fail**

```powershell
node --test src/test/js/home-quant-planet-world.test.mjs
```

Expected: missing authored anchors in reverse-side azimuth bins and old drag-angle expectations fail.

- [ ] **Step 3: Define deterministic spherical layout data**

Create eight district anchors at `45deg` yaw intervals:

```js
export const WORLD_DISTRICTS = Object.freeze([
    ["summit-shrine", 0, .86, "temple"],
    ["mountain-village", 45, .55, "village"],
    ["utility-ridge", 90, .24, "utility"],
    ["forest-hamlet", 135, -.16, "forest"],
    ["root-gardens", 180, -.58, "forest"],
    ["backside-houses", 225, -.12, "village"],
    ["industrial-yard", 270, .18, "industrial"],
    ["water-works", 315, .48, "water"]
].map(([id, yaw, latitude, type]) => Object.freeze({
    id, yawDegrees: yaw, latitude, type
})));
```

Add at least 18 terrain patches, 12 crooked house placements, three forest
belts totaling the profile foliage count, and 16 high-profile debris
placements. Store position, scale, and Euler rotation explicitly so tests and
visual review remain deterministic.

- [ ] **Step 4: Recenter and wrap the world**

Use a moderately subdivided irregular icosahedron for
`ReferencePlanetBody`. Apply deterministic vertex displacement from normalized
position instead of creating a front-facing slab. Build terrain patches
tangent to spherical district anchors using:

```js
function pointOnPlanet(radius, yawDegrees, latitude) {
    const yaw = yawDegrees * Math.PI / 180;
    const horizontal = Math.cos(latitude);
    return new THREE.Vector3(
        Math.cos(yaw) * horizontal * radius,
        Math.sin(latitude) * radius,
        Math.sin(yaw) * horizontal * radius
    );
}

function orientToSurface(group, position) {
    group.position.copy(position);
    group.quaternion.setFromUnitVectors(
        new THREE.Vector3(0, 1, 0),
        position.clone().normalize()
    );
}
```

Place the summit shrine and pale peaks above the upper core, water and bridges
across multiple sectors, orange-red machinery around the industrial sector,
utility antennas opposite it, crooked villages on both visible and reverse
sides, and overlapping forest canopy around the lower hemisphere.

Mark each district root with:

```js
district.userData.landmarkAnchor = true;
district.userData.landmarkType = districtConfig.type;
```

Translate assembled content by its framing center so the public `QuantPlanet`
root rotates around the actual miniature center. Recalculate the public
envelope after recentering.

- [ ] **Step 5: Preserve cel materials and budgets**

Reuse geometry for roofs, trunks, houses, rocks, and debris. Use
`InstancedMesh` for repeated foliage, terrain rocks, roof pieces, and fragments.
Keep separate groups only for the shrine, industrial machinery, utility ridge,
water works, and four village clusters. Preserve the existing toon gradient,
graphite back-face outline, and authored color palette without scene fog.

If the combined scene exceeds the budget, reduce hidden subdivisions and
ambient debris instances before removing any named district.

- [ ] **Step 6: Run world and composition tests, then commit**

```powershell
node --test src/test/js/home-quant-planet-world.test.mjs `
  src/test/js/home-quant-planet-composition.test.mjs
git add src/main/resources/static/js/home-quant-planet-world-layout.js `
  src/main/resources/static/js/home-quant-planet-world.js `
  src/test/js/home-quant-planet-world.test.mjs `
  src/test/js/home-quant-planet-composition.test.mjs
git commit -m "feat: rebuild dense full-rotation miniature planet"
```

### Task 6: Preserve the same composition in runtime and fallback modes

**Files:**
- Modify: `src/main/resources/templates/index.html`
- Modify: `src/main/resources/static/css/home-quant-planet.css`
- Modify: `src/main/resources/static/img/home-quant-planet-fallback.svg`
- Modify: `src/main/resources/static/js/home-quant-planet.js`
- Modify: `src/main/resources/static/js/home-quant-planet-runtime.js`
- Modify: `src/test/js/home-quant-planet-runtime.test.mjs`
- Modify: `src/test/java/com/kingyurina/demo/web/HomePageTemplateContractTest.java`

**Interfaces:**
- The DOM image remains `[data-reference-title]` and fallback-only.
- `.planet-webgl-ready` reveals the canvas and hides the fallback title image.
- Static/reduced/failed modes keep the image and SVG visible.
- `ENTER` remains a native `/quant` anchor outside both Three.js rigs.

- [ ] **Step 1: Write failing shell and fallback contracts**

Update the Java contract:

```java
assertTrue(template.contains(
        "class=\"reference-title reference-title--fallback\""));
assertTrue(css.contains(
        ".planet-webgl-ready .reference-title--fallback"));
assertEquals(
        "0",
        cssDeclaration(
                cssRule(css,
                        ".planet-webgl-ready .reference-title--fallback"),
                "opacity"));
assertTrue(entry.contains("./home-quant-planet-scene.js"));
assertTrue(scene.contains("./home-quant-planet-glyphs.js"));
assertTrue(glyphs.contains("./home-quant-planet-glyph-contours.js"));
assertFalse(css.contains("cursor: grab"));
assertFalse(css.contains("cursor: grabbing"));
```

Extend fallback SVG checks with these required IDs:

```java
for (String landmark : List.of(
        "fallback-summit-shrine",
        "fallback-mountain-village",
        "fallback-industrial-yard",
        "fallback-utility-ridge",
        "fallback-water-works",
        "fallback-backside-houses",
        "fallback-forest-belt",
        "fallback-floating-debris")) {
    assertNotNull(elementWithId(svg, landmark));
}
```

- [ ] **Step 2: Run focused Java and runtime tests and verify they fail**

```powershell
node --test src/test/js/home-quant-planet-runtime.test.mjs
mvn.cmd "-Dmaven.repo.local=C:\dev\codex-demo\.m2\repository" `
  "-Dtest=HomePageTemplateContractTest" test
```

Expected: missing fallback-only class, new module references, dense SVG IDs, and old grab cursor behavior.

- [ ] **Step 3: Update template and runtime-ready title behavior**

Keep the title source unchanged:

```html
<img class="reference-title reference-title--fallback"
     data-reference-title
     src="/img/home-messenger-reference-title.png"
     alt="">
```

Keep `ENTER` exactly:

```html
<a class="planet-primary-action"
   data-enter-quant href="/quant">ENTER</a>
```

Bump CSS and entry module version parameters. Do not add header, navigation,
route carousel, metric cards, or secondary links.

- [ ] **Step 4: Update CSS for dual runtime/fallback ownership**

Add:

```css
.planet-webgl-ready .reference-title--fallback {
    opacity: 0;
}

.planet-static-mode .reference-title--fallback,
.planet-reduced-motion .reference-title--fallback,
.planet-webgl-failed .reference-title--fallback {
    opacity: var(--intro-title-progress);
}

[data-home-canvas] {
    cursor: default;
    touch-action: none;
}
```

The image retains current responsive widths for fallback states. The WebGL
glyph layout is camera-fitted by the scene. Keep the button at the current safe
area, maintain `44px` minimum height, and keep all document scrolling disabled.

- [ ] **Step 5: Redraw the static fallback as a dense rounded planet**

Retain SVG-only local vectors. Build a round irregular core with overlapping
terrain masses, a summit shrine, mountain village, industrial yard, utility
ridge, water works, reverse-side house silhouettes, lower forest belt, and
exterior debris. Use the same turquoise, cream, gray, forest, water, and
rust-orange palette. Keep `viewBox="0 0 800 800"` and no `<image>` elements.

- [ ] **Step 6: Run focused verification and commit**

Run the Step 2 commands. Expected: all tests pass.

```powershell
git add src/main/resources/templates/index.html `
  src/main/resources/static/css/home-quant-planet.css `
  src/main/resources/static/img/home-quant-planet-fallback.svg `
  src/main/resources/static/js/home-quant-planet.js `
  src/main/resources/static/js/home-quant-planet-runtime.js `
  src/test/js/home-quant-planet-runtime.test.mjs `
  src/test/java/com/kingyurina/demo/web/HomePageTemplateContractTest.java
git commit -m "feat: align dual-rig runtime and fallback composition"
```

### Task 7: Calibrate against the supplied video and complete verification

**Files:**
- Modify only if measurements require tuning:
  - `src/main/resources/static/js/home-quant-planet-composition.js`
  - `src/main/resources/static/js/home-quant-planet-glyphs.js`
  - `src/main/resources/static/js/home-quant-planet-world-layout.js`
  - `src/main/resources/static/css/home-quant-planet.css`
- Create: `docs/superpowers/verification/2026-07-26-messenger-dual-rig-planet.md`

**Interfaces:**
- Consumes: local homepage at `http://localhost:8081/`.
- Produces: recorded evidence for entry, autonomous rotation, five pointer poses, mobile, reduced-motion, failure fallback, and budgets.

- [ ] **Step 1: Run the complete automated suite**

```powershell
node --test src/test/js/home-quant-planet-composition.test.mjs `
  src/test/js/home-quant-planet-glyph-contours.test.mjs `
  src/test/js/home-quant-planet-glyphs.test.mjs `
  src/test/js/home-quant-planet-input.test.mjs `
  src/test/js/home-quant-planet-motion.test.mjs `
  src/test/js/home-quant-planet-runtime.test.mjs `
  src/test/js/home-quant-planet-world.test.mjs
mvn.cmd "-Dmaven.repo.local=C:\dev\codex-demo\.m2\repository" test
```

Expected: zero Node failures and zero Maven failures/errors.

- [ ] **Step 2: Build and restart the isolated preview**

```powershell
mvn.cmd "-Dmaven.repo.local=C:\dev\codex-demo\.m2\repository" `
  -DskipTests package
```

Restart only the feature preview on port `8081`; do not stop or replace the
user's port `8080` server.

- [ ] **Step 3: Capture timed autonomous-rotation evidence**

Open `http://localhost:8081/` at a `1242x1270` viewport. Capture frames after
the intro settles and again at `3`, `6`, and `9` seconds without moving the
pointer. Confirm different districts enter view while the title remains in its
rest pose.

Then hold the elapsed planet time constant in the local diagnostic harness and
move the pointer to center, left, right, upper, and lower positions. Confirm:

- planet quaternion and camera position remain numerically unchanged;
- all nine glyph pivots remain present;
- glyph side faces change visibly;
- the three-by-three wording remains readable.

- [ ] **Step 4: Calibrate video-relative values**

Compare the local captures with `C:\Users\user\Downloads\Messenger.mp4`.
Adjust only these declared constants when measurement requires it:

- `REFERENCE_HERO.worldRevolutionMs`;
- `glyphYawRadians`;
- `glyphPitchRadians`;
- `glyphResponsePerSecond`;
- world scale/center CSS variables;
- deterministic landmark transforms.

Require idle angular displacement over nine seconds to be within `8%` of the
reference estimate and settled planet bounds to occupy `72–78%` of the usable
viewport.

- [ ] **Step 5: Verify fallback, mobile, accessibility, and budgets**

Check:

- `http://localhost:8081/?motion=reduce`;
- `http://localhost:8081/?render=static`;
- a mobile portrait viewport of `390x844`;
- keyboard focus and activation of `ENTER`;
- `canvas.dataset.drawCalls <= 75`;
- `canvas.dataset.triangles <= 140000`;
- `canvas.dataset.renderMs <= 12.00` after the diagnostic sample fills;
- no vertical or horizontal document scroll;
- no runtime request to the reference domain or any other external asset host.

- [ ] **Step 6: Record evidence**

Create the verification document after the measurements exist. Start it with
`# Messenger Dual-Rig Planet Verification`, then record the exact observed Node
pass count, Maven pass/failure/error counts, revolution milliseconds,
nine-second reference error percentage, desktop and mobile viewport
percentages, five-pose quaternion/camera comparison, glyph pivot count, draw
calls, triangles, average render milliseconds, reduced-motion result, and
static/WebGL-failure result. Every line must contain the observed value from
Steps 1–5; do not commit an empty field or example value.

- [ ] **Step 7: Re-run verification and commit calibrated results**

Repeat Step 1 after every calibration edit. Then:

```powershell
git add src/main/resources/static/js/home-quant-planet-composition.js `
  src/main/resources/static/js/home-quant-planet-glyphs.js `
  src/main/resources/static/js/home-quant-planet-world-layout.js `
  src/main/resources/static/css/home-quant-planet.css `
  docs/superpowers/verification/2026-07-26-messenger-dual-rig-planet.md
git commit -m "test: verify messenger dual-rig planet experience"
```

If a listed source file has no calibration change, omit it from `git add`.

## Completion Gate

Before claiming completion, invoke `superpowers:verification-before-completion`
and verify the current working tree contains no uncommitted task changes.
Then invoke `superpowers:requesting-code-review`; require both spec-compliance
and code-quality review to pass before offering branch integration or push.
