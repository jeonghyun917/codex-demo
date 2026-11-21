# Contact4 Photoreal Meadow Rebuild Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild `/contact4` as a photorealistic, layered scanned-asset meadow without changing its existing UI overlay.

**Architecture:** Keep `contact-nature.js` as the lifecycle owner, move scanned vegetation construction and deterministic distribution into `contact-nature-assets.js`, and add a focused `contact-nature-post.js` render-pass module. The runtime loads only local Poly Haven assets and retains the existing procedural fallback.

**Tech Stack:** Vanilla JavaScript ES modules, Three.js WebGLRenderer, GLTFLoader, RGBELoader, GLSL shader injection, Spring Boot static resources, Node test runner, Maven/JUnit.

## Global Constraints

- Preserve the current navigation, typography, copy, CTA, loader, reduced-motion behavior, and `/contact4` route.
- Use only local `/assets/contact4/` resources at runtime.
- Keep page rendering independent of external APIs and databases.
- Treat vegetation counts as total family budgets distributed across source variants.
- Verify one desktop and one 390x844 mobile viewport after focused tests stabilize.

---

### Task 1: Lock scanned-asset contracts

**Files:**
- Modify: `src/test/js/contact-nature-assets.test.mjs`
- Modify: `src/main/resources/static/js/contact-nature-assets.js`

**Interfaces:**
- Consumes: `NATURE_ASSETS`, `createPhotorealMeadowLayer(THREE, options)`
- Produces: correctly centered scanned geometries and total-budget instancing

- [ ] **Step 1: Write failing tests**

Assert that the production diffuse map is `leafy_grass_diff_2k.jpg`, source-node
X/Z offsets are removed, and a family count of 25 split across five source meshes
creates exactly 25 instances in total.

- [ ] **Step 2: Verify the tests fail**

Run: `node --test src/test/js/contact-nature-assets.test.mjs`

Expected: failures for the old generated ground, uncentered geometry, and per-mesh
count multiplication.

- [ ] **Step 3: Implement the asset contract**

Center each cloned geometry with:

```js
const centerX = (box.min.x + box.max.x) * 0.5;
const centerZ = (box.min.z + box.max.z) * 0.5;
geometry.translate(-centerX, -box.min.y, -centerZ);
```

Distribute `family.count` using quotient and remainder so the sum across variants
equals the authored budget.

- [ ] **Step 4: Verify focused tests pass**

Run: `node --test src/test/js/contact-nature-assets.test.mjs`

Expected: all focused asset tests pass.

### Task 2: Replace procedural flowers with scanned vegetation layers

**Files:**
- Modify: `src/main/resources/static/js/contact-nature.js`
- Modify: `src/main/resources/static/js/contact-nature-assets.js`
- Modify: `src/test/js/contact-nature-assets.test.mjs`

**Interfaces:**
- Consumes: local dandelion, grass, wildflower GLTF scenes and alpha maps
- Produces: `createPhotorealMeadowLayer` vegetation families named `grass-near`,
  `grass-mid`, `dandelion-colonies`, and `secondary-flowers`

- [ ] **Step 1: Write failing distribution tests**

Add a test proving `clusterCount`, `clusterSpread`, and `quietZone` generate
deterministic, non-uniform matrices while retaining instances near every depth band.

- [ ] **Step 2: Verify the tests fail**

Run: `node --test src/test/js/contact-nature-assets.test.mjs`

Expected: clustering metadata or matrix distribution assertion fails.

- [ ] **Step 3: Implement deterministic colony sampling**

Generate seeded colony centers in normalized X/depth space, choose a center per
instance, add triangular jitter, clamp to the terrain, and attenuate density—not
visibility—inside the CTA zone.

- [ ] **Step 4: Load and configure scanned families**

Load `grass_medium_01`, `grass_medium_02`, `dandelion_01`, and
`flower_empodium` with their alpha textures. Remove the call to
`createVolumetricDandelionField` from production and create family budgets tuned
separately for desktop and mobile.

- [ ] **Step 5: Verify focused tests pass**

Run: `node --test src/test/js/contact-nature-assets.test.mjs`

Expected: all focused tests pass with exact total budgets.

### Task 3: Add multi-scale botanical wind

**Files:**
- Modify: `src/main/resources/static/js/contact-nature-assets.js`
- Modify: `src/test/js/contact-nature-assets.test.mjs`

**Interfaces:**
- Consumes: normalized scanned geometry and per-instance translations
- Produces: `update({elapsed, reducedMotion})` with coherent travelling gusts

- [ ] **Step 1: Write a failing shader contract test**

Assert that compiled vegetation materials expose `uWindTime`, `uWindDirection`,
`uGustScale`, and an upper-vertex inertia term while reduced motion writes the fixed
authored time.

- [ ] **Step 2: Verify the test fails**

Run: `node --test src/test/js/contact-nature-assets.test.mjs`

Expected: the new wind uniforms are absent.

- [ ] **Step 3: Implement the wind shader**

Use world-position phase for the primary wave, a second higher-frequency gust, and
quadratic normalized height so roots remain fixed. Apply a small delayed term above
70% height for flower-head inertia.

- [ ] **Step 4: Verify focused tests pass**

Run: `node --test src/test/js/contact-nature-assets.test.mjs`

Expected: all focused tests pass.

### Task 4: Add depth finishing and integrate lifecycle

**Files:**
- Create: `src/main/resources/static/js/contact-nature-post.js`
- Create: `src/test/js/contact-nature-post.test.mjs`
- Modify: `src/main/resources/static/js/contact-nature.js`
- Modify: `src/main/resources/templates/contact4.html`

**Interfaces:**
- Produces: `createNaturePostProcessor(THREE, renderer, {width, height, pixelRatio})`
  returning `{render(scene, camera), resize(width, height, pixelRatio), dispose()}`
- Consumes: scene color and depth texture

- [ ] **Step 1: Write failing post-process tests**

Assert ownership of a depth render target, resize propagation, direct-render fallback,
and single disposal of the render target, fullscreen geometry, and material.

- [ ] **Step 2: Verify the tests fail**

Run: `node --test src/test/js/contact-nature-post.test.mjs`

Expected: module import fails because the post processor does not exist.

- [ ] **Step 3: Implement one bounded finishing pass**

Render scene color plus depth into one target. In a fullscreen fragment shader,
sample nearby depth for restrained contact occlusion, use depth distance for a
four-tap shallow-focus blur, then apply warm highlights, cool shadows, and a subtle
vignette. Fall back to `renderer.render(scene, camera)` if initialization fails.

- [ ] **Step 4: Integrate resize, render, and disposal**

Replace the direct frame render with `post.render(scene, camera)`, call `post.resize`
inside the existing resize handler, and call `post.dispose()` in the page lifecycle.
Bump the module cache key in `contact4.html`.

- [ ] **Step 5: Verify post-process and asset tests pass**

Run: `node --test src/test/js/contact-nature-post.test.mjs src/test/js/contact-nature-assets.test.mjs`

Expected: all focused tests pass.

### Task 5: Visual calibration and final verification

**Files:**
- Modify if required by direct acceptance criteria only:
  `src/main/resources/static/js/contact-nature.js`
  `src/main/resources/static/js/contact-nature-assets.js`
  `src/main/resources/static/js/contact-nature-post.js`

**Interfaces:**
- Consumes: completed scanned meadow and finishing pass
- Produces: verified `/contact4` desktop and mobile presentation

- [ ] **Step 1: Build and restart the local server**

Run: `mvn.cmd "-Dmaven.repo.local=.m2/repository" -DskipTests package`

Restart the existing hidden 8080 server with `.tools/start-server-javaw.cmd`.

- [ ] **Step 2: Inspect the desktop scene**

At 1440x900 verify textured scanned flowers, visible leaf silhouettes, irregular
colonies, layered depth, title readability, ready asset state, zero overflow, and no
console warnings.

- [ ] **Step 3: Inspect the mobile scene**

At 390x844 verify the same scene identity with reduced density, one-viewport height,
zero overflow, readable title/CTA, and no console warnings.

- [ ] **Step 4: Run the complete verification suite once**

Run:

```powershell
node --test src/test/js/*.test.mjs
mvn.cmd "-Dmaven.repo.local=.m2/repository" test
```

Expected: both commands exit 0 with zero failures.

- [ ] **Step 5: Smoke-test served resources**

Confirm HTTP 200 for `/`, `/contact4`, the four GLTF files, their alpha maps, the
HDRI, and the new post-processing module while port 8080 remains listening.

