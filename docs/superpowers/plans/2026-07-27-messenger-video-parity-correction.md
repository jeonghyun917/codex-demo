# Messenger Video Parity Correction Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> superpowers:subagent-driven-development (recommended) or
> superpowers:executing-plans to implement this plan task-by-task. Steps use
> checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the homepage's nine title objects react independently and keep
the autonomously rotating miniature planet densely detailed at every yaw.

**Architecture:** The glyph rig remains a layout parent while a pure
per-glyph pose function produces nine distinct target transforms. The planet
keeps eight semantic districts but expands repeated surface detail through
existing instanced mesh families and a testable sector-density description.

**Tech Stack:** JavaScript ES modules, Three.js, Node test runner, Spring Boot,
Thymeleaf, Maven, MariaDB.

## Global Constraints

- Work only on `codex/quant-planet-homepage` in the existing linked worktree.
- Do not add React, an external model pipeline, remote runtime assets, or page
  request API calls.
- Planet yaw never consumes pointer, drag, or touch coordinates.
- `ENTER` remains the only visible navigation action and targets `/quant`.
- Draw calls plus shadows stay `<= 75`, triangles stay `<= 140000`, and the
  established desktop diagnostic render time stays `<= 12ms`.
- Use `mvn.cmd "-Dmaven.repo.local=C:\dev\codex-demo\.m2\repository" test`.

---

### Task 1: Independent nine-glyph target poses

**Files:**
- Modify: `src/test/js/home-quant-planet-glyphs.test.mjs`
- Modify: `src/main/resources/static/js/home-quant-planet-glyphs.js`

**Interfaces:**
- Consumes: normalized pointer `{x, y}` and elapsed milliseconds.
- Produces: nine independent pivot rotations and local positions from immutable
  response profiles.

- [ ] **Step 1: Write the failing test**

Add a test that advances a rig at `{x: 0.9, y: -0.7}` and records each pivot's
rounded `[rotation.x, rotation.y, rotation.z, position.x delta, position.y
delta, position.z]`. Assert nine unique signatures, nonzero roll on multiple
glyphs, both cross-axis and direct-axis behavior, and at least three strong
side angles. The production change caught by this test is restoring a shared
`targetYaw`/`targetPitch` for every glyph.

- [ ] **Step 2: Run test to verify it fails**

Run:
`node --test src/test/js/home-quant-planet-glyphs.test.mjs`

Expected: FAIL because current glyphs share direction, have zero roll, and do
not translate independently.

- [ ] **Step 3: Implement the minimal independent pose field**

Replace tuple gain records with named immutable profiles containing direct and
cross yaw/pitch coefficients, roll coefficients, response speed, rest pose,
translation coefficients, and rotation limits. Save each pivot's immutable
layout anchor. Compute and damp nine target poses independently inside
`update()`. Do not rotate the parent group.

- [ ] **Step 4: Run focused tests**

Run:
`node --test src/test/js/home-quant-planet-glyphs.test.mjs`

Expected: PASS with no warnings.

- [ ] **Step 5: Commit**

Stage only the glyph implementation, glyph test, approved spec, and this plan.
Commit message: `feat: articulate nine independent title glyphs`.

### Task 2: Center return, reduced motion, and input invariants

**Files:**
- Modify: `src/test/js/home-quant-planet-glyphs.test.mjs`
- Modify: `src/test/js/home-quant-planet-composition.test.mjs`
- Modify: `src/main/resources/static/js/home-quant-planet-glyphs.js`

**Interfaces:**
- Consumes: the per-glyph profiles from Task 1.
- Produces: proximity-weighted glyph velocity impulses, deterministic rest
  restoration, an unchanged layout parent, and an unchanged planet pose.

- [ ] **Step 1: Write failing invariant tests**

Assert center-return settles all six pose components to each authored rest
value, reduced motion restores the same values immediately, and two renders
with identical `worldAngle` but opposite pointers produce an identical planet
matrix while producing different glyph child matrices. Add a timestamped fast
row sweep fixture whose literal expectations require at least one nearby glyph
to exceed 90 degrees while at least six remain within 25 degrees, followed by
pointer release that returns all nine within 12 degrees of rest between 1.2 and
1.8 seconds.

- [ ] **Step 2: Verify RED**

Run:
`node --test src/test/js/home-quant-planet-glyphs.test.mjs src/test/js/home-quant-planet-composition.test.mjs`

Expected: the velocity sweep assertion fails because Task 1 has independent
targets and phase damping but no pointer-velocity impulse state.

- [ ] **Step 3: Complete the minimal rest and clamping behavior**

Clamp every pointer input and per-axis target. Track normalized pointer velocity
from timestamped updates, apply a glyph-anchor-distance falloff to angular
impulses, integrate independent per-glyph angular velocity with frame-rate
independent damping and a rest spring, and set the complete rest pose and
velocity state directly under reduced motion. Pointer release must not affect
planet yaw, camera, or the glyph parent transform.

- [ ] **Step 4: Verify GREEN**

Re-run the two focused test files. Expected: PASS.

- [ ] **Step 5: Commit**

Commit message: `test: lock dual-rig motion invariants`.

### Task 3: Dense 360-degree surface-detail contract

**Files:**
- Modify: `src/test/js/home-quant-planet-world.test.mjs`
- Modify: `src/main/resources/static/js/home-quant-planet-world-layout.js`
- Modify: `src/main/resources/static/js/home-quant-planet-world.js`

**Interfaces:**
- Consumes: eight semantic district definitions and repeated placement arrays.
- Produces: a `getSurfaceDensity()` diagnostic with eight sector records whose
  category counts describe rendered, front-relevant detail.

- [ ] **Step 1: Write the failing density test**

For every sector, assert total detail `>= 20`, terrain count `>= 6`,
architecture/mechanical count `>= 3`, vegetation/rock count `>= 6`, and
utility/silhouette-detail count `>= 5`. Assert upper, middle, and lower latitude
bands all contain authored detail, the maximum neighboring azimuth gap is
`<= 18 degrees`, and adjacent sectors share boundary detail so the minimum
category total never collapses at 22.5-degree offset samples. For eight yaw
samples, assert the front hemisphere contains at least 70 details, four or more
visible categories, and all four projected quadrants are occupied. The
production change caught by this test is returning to one representative
landmark per sector or counting invisible back-side geometry as foreground.

- [ ] **Step 2: Verify RED**

Run:
`node --test src/test/js/home-quant-planet-world.test.mjs`

Expected: FAIL because the current world exposes only one district
representative and sparse repeated placements.

- [ ] **Step 3: Implement the density diagnostic from rendered placements**

Tag placement records by category and derive an immutable
`getSurfaceDensity()` result from the same placement data used to create
geometry. Return eight sector summaries, eight yaw-view front-hemisphere
summaries, latitude-band counts, projected quadrant occupancy, and maximum
azimuth gap. Do not create a second visual-only source of truth.

- [ ] **Step 4: Expand existing instanced families**

Add terrain, village, canopy/rock, roof, path/frame, pipe/pole, and debris
placements across all eight sectors. Consolidate the four village
`InstancedMesh` objects into one global building family and replace individual
cable meshes with one instanced segment family before adding path/frame and
pipe/pole families. Reuse existing shared box, cylinder, sphere, cone, torus,
and toon materials; increase instance counts rather than creating per-detail
meshes. Keep the major authored landmark groups intact. Target approximately
39 world mesh calls plus at most 3 shadow calls, leaving total scene headroom
below 75.

- [ ] **Step 5: Verify GREEN and budgets**

Run:
`node --test src/test/js/home-quant-planet-world.test.mjs`

Expected: density and budget tests PASS.

- [ ] **Step 6: Commit**

Commit message: `feat: fill quant planet across every yaw sector`.

### Task 4: Scene regression and video-calibrated browser review

**Files:**
- Modify only if a failing integration contract requires it:
  `src/main/resources/static/js/home-quant-planet-scene.js`
- Test:
  `src/test/js/home-quant-planet-*.test.mjs`

**Interfaces:**
- Consumes: completed glyph and world rigs.
- Produces: the same loader, fallback, `/quant` action, responsive framing, and
  diagnostics with corrected visuals.

- [ ] **Step 1: Run all JavaScript tests**

Run:
`node --test src/test/js/*.test.mjs`

Expected: all tests PASS.

- [ ] **Step 2: Build and restart the isolated preview**

Run the Maven package/test workflow with the workspace-local repository, then
restart only the port `8081` preview. Never stop or alter port `8080`.

- [ ] **Step 3: Compare live poses**

Capture desktop and `375x812` frames at center, left/up, right/down, and at
several autonomous world timestamps. Confirm that individual glyph faces and
depths differ while the planet phase is unchanged by pointer motion. Confirm
there is no large bare core face at any sampled yaw. Across eight 45-degree yaw
captures, measure planet-ring foreground occupancy `>= 40%`, detail-edge
density `>= 22%`, sector variation `<= 15%`, and largest bare connected region
`<= 12%` of the silhouette.

If the numeric placement density passes but the browser still shows a broad
uniform core, enter a visual-exposure RED/GREEN loop before continuing:

1. add a real-instance test that inspects village body and terrain instance
   matrices per sector;
2. verify RED when any sector has fewer than three village bodies with
   width `>= 0.48`, height `>= 0.50`, and center radius `>= 3.55`, or fewer
   than six terrain instances with center radius `>= 3.48` and broad axes
   `>= 0.48` and `>= 0.38`;
3. enlarge and radially expose the existing placement families, add
   vertex-colored terrain variation, and add one matching instanced graphite
   outline family for villages;
4. verify the exposure test, world tests, full Node tests, and scene budget;
5. rebuild and repeat the browser comparison before accepting the task.

If exposed primitives still read as sparse blocks rather than the reference's
continuous illustrated world, use the approved hybrid surface-art correction:

1. add the original project asset
   `src/main/resources/static/images/quant-planet-panorama-v1.png`;
2. write a failing world test that supplies a real Three.js texture and
   requires `ReferencePlanetBody` to use it with sRGB color, horizontal repeat,
   no vertical repeat, neutral material tint, and exactly-once disposal;
3. write a failing scene/browser contract for the exact local `/images/` URL
   and ensure Node/static test environments retain the plain-color fallback;
4. implement one-time local texture loading and apply it only to the rotating
   core while keeping all protruding Three.js detail meshes;
5. reduce the village outline inflation until it reads as a thin graphite edge
   rather than a black backing plate;
6. run world, full Node, Maven, lifecycle, and renderer-budget verification;
7. rebuild 8081 and visually compare the mapped core at several autonomous yaw
   timestamps before accepting the task.

- [ ] **Step 4: Check runtime diagnostics**

Read the page's diagnostic object and confirm draw calls plus shadows `<= 75`,
triangles `<= 140000`, and average render time `<= 12ms`. Check console errors,
overflow, reduced motion, WebGL failure fallback, and the `/quant` target.

- [ ] **Step 5: Run full Maven verification**

Run:
`mvn.cmd "-Dmaven.repo.local=C:\dev\codex-demo\.m2\repository" test`

Expected: BUILD SUCCESS.

- [ ] **Step 6: Commit verified integration**

Commit message: `fix: match messenger video motion and density`.
