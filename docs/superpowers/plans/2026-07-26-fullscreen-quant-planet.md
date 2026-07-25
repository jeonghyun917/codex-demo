# Fullscreen Quant Planet Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the scroll-driven Quant Planet homepage with one fullscreen, no-scroll Three.js experience whose rotating six-word navigation links to the existing product pages.

**Architecture:** Retain the authored Three.js world, renderer ownership, quality profiles, resource rollback, and semantic fallback. Replace scroll-derived state with pure time/orbit/courier functions, synchronize a semantic DOM navigation ring with the scene through one page coordinator, and isolate pointer/keyboard behavior in a small input controller.

**Tech Stack:** Java 21, Spring Boot 4.0.6, Thymeleaf, vanilla ES modules, Three.js r165, Motion 12.29.2, Instrument Sans, IBM Plex Mono, JUnit 5, Node test runner.

## Global Constraints

- Implement `docs/superpowers/specs/2026-07-26-fullscreen-quant-planet-design.md`.
- Continue on `codex/quant-planet-homepage` inside the existing isolated worktree.
- Preserve every unrelated tracked and untracked user file in the primary checkout.
- Redesign only `/`; do not modify `/quant` or another subpage.
- Keep `HomeController#index` unchanged and add no database or external API dependency to the homepage request.
- Reuse the existing authored Quant planet, Observe Station, Resolve Works, Decision Gate, Quant Courier, self-hosted Three.js r165, Motion 12.29.2, fonts, and licenses.
- Do not copy Messenger code, assets, characters, models, textures, copy, music, quests, or branding.
- Do not introduce React, Svelte, npm runtime code, a frontend build pipeline, remote runtime assets, game controls, multiplayer, or autoplay audio.
- Use one fullscreen `100dvh` composition with no document scroll or nested scroll region.
- Wheel input must not scroll, zoom, or rotate the homepage.
- Keep exactly one primary `Enter Quant` action resolving to `/quant`.
- Keep the exact headline `Market intelligence, in motion.`
- Keep the exact narrative `Observe the market. Resolve the signal. Act with context.`
- Keep `Signal 79 / 100`, `20D Alpha +2.4%`, and `Risk Medium` with `Illustrative model view` in semantic HTML.
- Map the rotating anchors exactly:
  - `QUANT` → `/quant`
  - `STOCKS` → `/stocks`
  - `HEATMAP` → `/stocks/heatmap?index=SP500`
  - `ETF` → `/etfs`
  - `ATELIER` → `/atelier`
  - `SIGNAL` → `/signals/backtest`
- The six rotating words must remain ordinary keyboard-focusable `<a>` elements.
- Planet diameter targets approximately `min(82vh, 72vw)` on desktop and `92vw` on mobile.
- Intro maximum wait is `4000ms`; returning through BFCache does not replay it.
- Automatic ring revolution lasts `28000ms`.
- Pause ring motion while an anchor is hovered, focused, pressed, or dragged.
- Cap pixel ratio at `1.5`, high-profile draw calls at `75`, visible triangles at `140000`, and loopback average scene submission at `12.0ms`.
- Keep interactive targets at least `44×44px` with visible `:focus-visible`.
- Validate `375×812`, `768×1024`, `1024×768`, and `1440×900` without horizontal or vertical document overflow.
- Reduced motion stops loader choreography, automatic ring rotation, courier walking, ambient orbit, parallax, and momentum.
- WebGL failure, context loss, no JavaScript, and missing Motion must leave the static planet and all six routes usable.
- Use `mvn.cmd "-Dmaven.repo.local=.m2/repository" test` for final Java verification. In the isolated worktree, the equivalent absolute workspace-local cache `C:\dev\codex-demo\.m2\repository` is permitted when the ignored worktree cache is absent.

## File Map

### Create

- `src/main/resources/static/js/home-quant-planet-input.js`
- `src/test/js/home-quant-planet-input.test.mjs`

### Modify

- `src/main/resources/templates/index.html`
- `src/main/resources/static/css/home-quant-planet.css`
- `src/main/resources/static/js/home-quant-planet.js`
- `src/main/resources/static/js/home-quant-planet-motion.js`
- `src/main/resources/static/js/home-quant-planet-world.js`
- `src/main/resources/static/js/home-quant-planet-scene.js`
- `src/test/js/home-quant-planet-motion.test.mjs`
- `src/test/js/home-quant-planet-world.test.mjs`
- `src/test/java/com/kingyurina/demo/web/HomePageTemplateContractTest.java`

### Delete after migration

- `src/main/resources/static/js/home-quant-planet-scroll.js`

### Preserve

- `src/main/java/com/kingyurina/demo/web/HomeController.java`
- `src/main/java/com/kingyurina/demo/menu/MenuService.java`
- all subpage controllers, templates, CSS, and JavaScript;
- `src/main/resources/static/js/vendor/three.module.js`;
- `src/main/resources/static/vendor/motion/*`;
- `src/main/resources/static/vendor/lenis/*` and its license, even though the homepage stops referencing Lenis;
- `src/main/resources/static/fonts/*`;
- world resource rollback and exactly-once disposal behavior.

---

### Task 1: Pure Intro, Orbit, Ring, and Courier Loop State

**Files:**

- Modify: `src/test/js/home-quant-planet-motion.test.mjs`
- Modify: `src/main/resources/static/js/home-quant-planet-motion.js`

**Interfaces:**

- Consumes: elapsed milliseconds, scene readiness, reduced-motion state, pointer coordinates, drag angle, selected navigation index, and interaction pause state.
- Produces:
  - `wrapIndex(index, count = 6): number`
  - `createIntroState({ elapsed, ready, readyElapsed, timedOut, reduceMotion }): IntroState`
  - `createOrbitPose({ time, dragAngle, pointer, reduceMotion }): OrbitPose`
  - `advanceRingAngle(angle, deltaMs, paused, reduceMotion): number`
  - `createNavigationRingState({ rotationAngle, dragAngle, selectedIndex, snapToSelected, itemCount }): NavigationRingState`
  - `createCourierLoopPose(time, reduceMotion): CourierPose`
  - existing `normalizePointer`, `resolveLocalTestOverrides`, `selectPlanetQualityProfile`, `shouldRenderFrame`, and `createPageLifecycle`.

- [ ] **Step 1: Replace scroll/chapter pose tests with failing fullscreen state tests**

  Replace imports of `normalizeScrollProgress`, `resolvePlanetChapter`,
  `createPlanetPose`, and `createCourierPose` with the new exports. Add literal,
  behavior-based tests:

  ```js
  test("progresses the intro only after readiness and exits on timeout", () => {
      assert.deepEqual(
          createIntroState({
              elapsed: 300, ready: false, readyElapsed: 0,
              timedOut: false, reduceMotion: false
          }),
          {
              phase: "mark", loaderOpacity: 1, markProgress: 0.08,
              splitProgress: 0, revealProgress: 0, worldScale: 0.88,
              interactionReady: false
          }
      );
      assert.equal(createIntroState({
          elapsed: 1700, ready: true, readyElapsed: 850,
          timedOut: false, reduceMotion: false
      }).phase, "ready");
      assert.equal(createIntroState({
          elapsed: 4000, ready: false, readyElapsed: 0,
          timedOut: true, reduceMotion: false
      }).interactionReady, true);
  });

  test("reduced motion resolves directly to a stable ready composition", () => {
      assert.deepEqual(createIntroState({
          elapsed: 20, ready: true, readyElapsed: 0,
          timedOut: false, reduceMotion: true
      }), {
          phase: "ready", loaderOpacity: 0, markProgress: 1,
          splitProgress: 0, revealProgress: 1, worldScale: 1,
          interactionReady: true
      });
  });

  test("keeps fullscreen orbit bounded under extreme input", () => {
      const pose = createOrbitPose({
          time: 28000, dragAngle: 100,
          pointer: { x: 8, y: -8 }, reduceMotion: false
      });
      assert.ok(pose.cameraDistance >= 8.2 && pose.cameraDistance <= 9.6);
      assert.ok(Math.abs(pose.cameraYaw) <= Math.PI * 2);
      assert.ok(Math.abs(pose.cameraPitch) <= .34);
      assert.ok(Math.abs(pose.planetPitch) <= .18);
      assert.ok(Number.isFinite(pose.planetYaw));
  });

  test("wraps six product links and brings the selected item forward", () => {
      assert.equal(wrapIndex(-1, 6), 5);
      assert.equal(wrapIndex(6, 6), 0);
      const state = createNavigationRingState({
          rotationAngle: 0, dragAngle: 0, selectedIndex: 2,
          snapToSelected: true, itemCount: 6
      });
      assert.equal(state.items.length, 6);
      assert.equal(state.frontIndex, 2);
      assert.ok(state.items[2].depth > state.items[1].depth);
      assert.ok(state.items.every((item) =>
          Number.isFinite(item.angle)
          && item.opacity >= .35 && item.opacity <= 1
          && item.scale >= .72 && item.scale <= 1.12
      ));
  });

  test("advances one 28-second revolution only while unpaused", () => {
      assert.equal(advanceRingAngle(1, 1000, true, false), 1);
      assert.equal(advanceRingAngle(1, 1000, false, true), 1);
      assert.ok(Math.abs(
          advanceRingAngle(0, 100, false, false) - Math.PI * 2 / 280
      ) < 1e-9);
  });

  test("loops the courier through all three landmarks with authored holds", () => {
      const observe = createCourierLoopPose(23000, false);
      const resolve = createCourierLoopPose(7000, false);
      const act = createCourierLoopPose(15000, false);
      assert.ok(observe.landmarkActivity.observe > .9);
      assert.ok(resolve.landmarkActivity.resolve > .9);
      assert.ok(act.landmarkActivity.act > .9);
      assert.ok(act.idleBlend > .8);
      assert.ok([observe, resolve, act].every((pose) =>
          pose.routeProgress >= 0 && pose.routeProgress <= 1
          && pose.walkPhase >= 0 && pose.walkPhase <= Math.PI * 2
      ));
  });
  ```

- [ ] **Step 2: Run the Node test and confirm RED**

  ```powershell
  node --test src/test/js/home-quant-planet-motion.test.mjs
  ```

  Expected: FAIL because the new exports do not exist.

- [ ] **Step 3: Implement bounded fullscreen state**

  Keep quality selection, local override, pointer normalization, and lifecycle
  behavior. Implement the new state with these constants and formulas:

  ```js
  const TAU = Math.PI * 2;
  const RING_DURATION = 28000;
  const clamp = (value, min, max) =>
      Math.min(Math.max(Number(value) || 0, min), max);

  export function wrapIndex(index, count = 6) {
      const length = Math.max(1, Math.trunc(Number(count)) || 1);
      return ((Math.trunc(Number(index)) || 0) % length + length) % length;
  }

  export function advanceRingAngle(
      angle = 0, deltaMs = 0, paused = false, reduceMotion = false
  ) {
      const current = Number.isFinite(angle) ? angle : 0;
      if (paused || reduceMotion) return current;
      const delta = clamp(deltaMs, 0, 100);
      return current + delta / RING_DURATION * TAU;
  }

  export function createNavigationRingState({
      rotationAngle = 0, dragAngle = 0, selectedIndex = 0,
      snapToSelected = false, itemCount = 6
  } = {}) {
      const count = Math.max(1, Math.trunc(Number(itemCount)) || 1);
      const selected = wrapIndex(selectedIndex, count);
      const step = TAU / count;
      const automatic = snapToSelected
          ? -selected * step : Number(rotationAngle) || 0;
      const baseAngle = automatic
          + (snapToSelected ? 0 : clamp(dragAngle, -TAU * 2, TAU * 2));
      const items = Array.from({ length: count }, (_, index) => {
          const angle = baseAngle + index * step;
          const depth = (Math.cos(angle) + 1) / 2;
          return {
              index, angle, depth,
              opacity: .35 + depth * .65,
              scale: .72 + depth * .40,
              zIndex: Math.round(depth * 100)
          };
      });
      const frontIndex = items.reduce((front, item) =>
          item.depth > items[front].depth ? item.index : front, 0);
      return { baseAngle, frontIndex, items };
  }
  ```

  Implement the intro and orbit with exact bounded state:

  ```js
  export function createIntroState({
      elapsed = 0, ready = false, readyElapsed = 0,
      timedOut = false, reduceMotion = false
  } = {}) {
      const total = Math.max(0, Number(elapsed) || 0);
      const sinceReady = Math.max(0, Number(readyElapsed) || 0);
      if (timedOut || (reduceMotion && ready)) {
          return {
              phase: "ready", loaderOpacity: 0, markProgress: 1,
              splitProgress: 0, revealProgress: 1, worldScale: 1,
              interactionReady: true
          };
      }
      if (reduceMotion) {
          return {
              phase: "boot", loaderOpacity: 1, markProgress: 1,
              splitProgress: 0, revealProgress: 0, worldScale: 1,
              interactionReady: false
          };
      }
      const markProgress = clamp((total - 250) / 625, 0, 1);
      const splitProgress = ready ? smoothStep(sinceReady / 400) : 0;
      const revealProgress = ready
          ? smoothStep((sinceReady - 250) / 600) : 0;
      const interactionReady = ready && revealProgress >= 1;
      const phase = interactionReady ? "ready"
          : ready ? (sinceReady < 250 ? "split" : "reveal")
              : total < 250 ? "boot" : "mark";
      return {
          phase,
          loaderOpacity: 1 - revealProgress,
          markProgress,
          splitProgress,
          revealProgress,
          worldScale: .88 + revealProgress * .12,
          interactionReady
      };
  }

  export function createOrbitPose({
      time = 0, dragAngle = 0, pointer = { x: 0, y: 0 },
      reduceMotion = false
  } = {}) {
      const pointerX = clamp(pointer?.x, -1, 1);
      const pointerY = clamp(pointer?.y, -1, 1);
      const drag = clamp(dragAngle, -TAU * 2, TAU * 2);
      const ambient = reduceMotion ? 0
          : (Math.max(0, Number(time) || 0) % RING_DURATION)
              / RING_DURATION * TAU;
      return {
          cameraDistance: 8.9,
          cameraYaw: clamp(drag * .18 + pointerX * .0698, -TAU, TAU),
          cameraPitch: clamp(.12 + pointerY * .052, -.34, .34),
          planetYaw: drag + ambient * .08,
          planetPitch: clamp(-.08 + pointerY * .02, -.18, .18),
          planetRoll: pointerX * .018
      };
  }
  ```

  `createCourierLoopPose` uses a `24000ms` loop:

  - `0..6000`: route `0 → .5`;
  - `6000..8000`: hold Resolve at `.5`;
  - `8000..14000`: route `.5 → 1`;
  - `14000..16000`: hold Act at `1`;
  - `16000..22000`: route `1 → 0`;
  - `22000..24000`: hold Observe at `0`.

  Return the current world-compatible fields plus `routeProgress`,
  `routeActivity`, and chapter activity:

  ```js
  export function createCourierLoopPose(time = 0, reduceMotion = false) {
      if (reduceMotion) {
          return {
              routeProgress: .5, routeAngle: .19, elevation: .195,
              heading: .19 + Math.PI / 2, walkPhase: 0, idleBlend: 1,
              routeActivity: .45,
              landmarkActivity: { observe: 0, resolve: 1, act: 0 }
          };
      }
      const elapsed = Math.max(0, Number(time) || 0);
      const cycle = elapsed % 24000;
      let routeProgress;
      let direction = 1;
      let idleBlend = 0;
      if (cycle < 6000) {
          routeProgress = .5 * smoothStep(cycle / 6000);
      } else if (cycle < 8000) {
          routeProgress = .5;
          idleBlend = 1;
      } else if (cycle < 14000) {
          routeProgress = .5 + .5 * smoothStep((cycle - 8000) / 6000);
      } else if (cycle < 16000) {
          routeProgress = 1;
          idleBlend = 1;
      } else if (cycle < 22000) {
          direction = -1;
          routeProgress = 1 - smoothStep((cycle - 16000) / 6000);
      } else {
          direction = -1;
          routeProgress = 0;
          idleBlend = 1;
      }
      const routeAngle = -.9 + routeProgress * 2.18;
      const observe = 1 - smoothStep(resolveRange(routeProgress, 0, .25));
      const resolve = 1 - smoothStep(
          clamp(Math.abs(routeProgress - .5) / .25, 0, 1)
      );
      const act = smoothStep(resolveRange(routeProgress, .75, 1));
      const phase = (elapsed / 260) % TAU;
      return {
          routeProgress,
          routeAngle,
          elevation: .17 + Math.sin(routeProgress * Math.PI) * .025,
          heading: routeAngle + (direction > 0 ? Math.PI / 2 : -Math.PI / 2),
          walkPhase: phase < 0 ? phase + TAU : phase,
          idleBlend,
          routeActivity: 1 - idleBlend * .55,
          landmarkActivity: { observe, resolve, act }
      };
  }
  ```

  Reduced motion returns a stable Resolve pose with `walkPhase: 0`,
  `idleBlend: 1`, and no time-derived movement.

- [ ] **Step 4: Remove scroll-only pure exports**

  Delete `normalizeScrollProgress`, `resolvePlanetChapter`,
  `createPlanetPose`, and `createCourierPose` only after no test imports them.
  Update `shouldRenderFrame` so visible WebGL frames render when either dirty or
  ambient:

  ```js
  export function shouldRenderFrame(state) {
      return Boolean(
          state?.visible && !state.hidden && state.webgl
          && (state.dirty || state.ambient)
      );
  }
  ```

- [ ] **Step 5: Run the Node test and confirm GREEN**

  Run the command from Step 2.

  Expected: every motion/lifecycle test passes.

- [ ] **Step 6: Commit**

  ```powershell
  git add src/main/resources/static/js/home-quant-planet-motion.js src/test/js/home-quant-planet-motion.test.mjs
  git commit -m "feat: define fullscreen quant motion"
  ```

---

### Task 2: Fullscreen World Framing and Continuous Courier

**Files:**

- Modify: `src/main/resources/static/js/home-quant-planet-world.js`
- Modify: `src/main/resources/static/js/home-quant-planet-scene.js`
- Modify: `src/test/js/home-quant-planet-world.test.mjs`
- Modify: `src/test/java/com/kingyurina/demo/web/HomePageTemplateContractTest.java`

**Interfaces:**

- Consumes: `OrbitPose` and `CourierPose` from Task 1 and the existing quality profile.
- Produces:
  - `QuantPlanetWorld.update({ orbitPose, courierPose, time })`
  - scene methods:
    `resize()`, `setPointer(value)`, `setDragAngle(value)`,
    `setVisible(value)`, `render(time)`, `getDiagnostics()`, `dispose()`.

- [ ] **Step 1: Write failing world-loop and scene interface tests**

  Change the world test helper to:

  ```js
  function update(world, courierPose, time = 1000) {
      world.update({
          orbitPose: {
              planetPitch: -.08,
              planetYaw: .42,
              planetRoll: .02
          },
          courierPose,
          time
      });
  }
  ```

  Add:

  ```js
  test("moves the courier continuously and lights its nearest landmark", (t) => {
      const world = createWorld(t);
      const courier = world.group.getObjectByName("QuantCourier");
      update(world, createCourierLoopPose(7000, false), 7000);
      const resolvePosition = courier.position.clone();
      const portal = world.group.getObjectByName("QuantProductPortal");
      const inactivePortalScale = portal.scale.x;
      update(world, createCourierLoopPose(15000, false), 15000);
      assert.ok(courier.position.distanceTo(resolvePosition) > 1);
      assert.ok(portal.scale.x > inactivePortalScale);
  });
  ```

  Extend the Java scene source contract:

  ```java
  assertTrue(scene.contains("setDragAngle(value)"));
  assertTrue(scene.contains("createOrbitPose"));
  assertTrue(scene.contains("createCourierLoopPose"));
  assertFalse(scene.contains("setProgress(value)"));
  assertFalse(scene.contains("createPlanetPose"));
  ```

- [ ] **Step 2: Run focused tests and confirm RED**

  ```powershell
  node --test src/test/js/home-quant-planet-world.test.mjs
  mvn.cmd "-Dmaven.repo.local=C:\dev\codex-demo\.m2\repository" -Dtest=HomePageTemplateContractTest#homepageBuildsAnOriginalLocalQuantPlanetWorld test
  ```

  Expected: FAIL on the new update shape and missing scene method/imports.

- [ ] **Step 3: Adapt world updates without rebuilding geometry**

  Change the update boundary:

  ```js
  function update({ orbitPose, courierPose, time = 0 }) {
      if (disposed || !orbitPose || !courierPose) return;
      planet.rotation.set(
          orbitPose.planetPitch,
          orbitPose.planetYaw,
          orbitPose.planetRoll
      );
      const routeProgress = Math.min(Math.max(
          Number(courierPose.routeProgress) || 0, 0
      ), 1);
      routeCurve.getPointAt(routeProgress, routePoint);
      normal.copy(routePoint).normalize();
      courier.position.copy(routePoint)
          .addScaledVector(normal, courierPose.elevation || 0);
      alignToNormal.setFromUnitVectors(worldUp, normal);
      headingRotation.setFromAxisAngle(worldUp, courierPose.heading);
      courier.quaternion.copy(alignToNormal).multiply(headingRotation);

      const stride = Math.sin(courierPose.walkPhase) * .5
          * (1 - courierPose.idleBlend);
      left.thigh.rotation.x = stride;
      right.thigh.rotation.x = -stride;
      left.shin.rotation.x = Math.max(0, -stride) * .7;
      right.shin.rotation.x = Math.max(0, stride) * .7;
      left.arm.rotation.x = -stride * .72;
      right.arm.rotation.x = stride * .72;

      const activity = courierPose.landmarkActivity;
      const boundedActivity = (value) =>
          Math.min(Math.max(Number(value) || 0, 0), 1);
      const observeActivity = boundedActivity(activity.observe);
      const resolveActivity = boundedActivity(activity.resolve);
      const actActivity = boundedActivity(activity.act);
      observeDish.rotation.y = time * .00018 * observeActivity;
      resolveSorter.position.y = resolveSorterBaseY
          + resolveActivity * Math.sin(time * .003) * .08;
      productPortal.scale.copy(productPortalBaseScale).multiplyScalar(
          1 + actActivity * (.04 + Math.sin(time * .003) * .025)
      );
      for (let index = 0; index < routeLights.count; index += 1) {
          const localActivity = index < 3
              ? observeActivity : index < 7 ? resolveActivity : actActivity;
          lampColor.copy(lampDim).lerp(
              lampBright, .15 + localActivity * .85
          );
          routeLights.setColorAt(index, lampColor);
      }
      routeLights.instanceColor.needsUpdate = true;
  }
  ```

  Read landmark activity from `courierPose.landmarkActivity` and route activity
  from `courierPose.routeActivity`. Keep the existing named objects, authored
  geometry, instancing, high-profile budget, construction rollback, and
  exactly-once disposal unchanged.

- [ ] **Step 4: Reframe the scene controller**

  Import:

  ```js
  import {
      createCourierLoopPose,
      createOrbitPose
  } from "./home-quant-planet-motion.js";
  ```

  Replace progress state with:

  ```js
  let pointer = { x: 0, y: 0 };
  let dragAngle = 0;

  setDragAngle(value) {
      const next = Math.max(-Math.PI * 4, Math.min(
          Math.PI * 4, Number(value) || 0
      ));
      if (next !== dragAngle) {
          dragAngle = next;
          markDirty();
      }
  }
  ```

  In `render(time)`:

  ```js
  const orbitPose = createOrbitPose({
      time, dragAngle, pointer,
      reduceMotion: profile.name === "static"
  });
  const courierPose = createCourierLoopPose(time, false);
  world.update({ orbitPose, courierPose, time });
  ```

  Position the camera from `orbitPose.cameraDistance`, `cameraYaw`, and
  `cameraPitch`, look at `(0, .35, 0)`, and retain diagnostics/disposal.
  Fullscreen scale comes from camera framing; do not increase geometry density.

- [ ] **Step 5: Run focused and regression tests**

  ```powershell
  node --test src/test/js/home-quant-planet-motion.test.mjs src/test/js/home-quant-planet-world.test.mjs
  mvn.cmd "-Dmaven.repo.local=C:\dev\codex-demo\.m2\repository" -Dtest=HomePageTemplateContractTest#homepageBuildsAnOriginalLocalQuantPlanetWorld test
  ```

  Expected: all pass, including budget and disposal tests.

- [ ] **Step 6: Commit**

  ```powershell
  git add src/main/resources/static/js/home-quant-planet-world.js src/main/resources/static/js/home-quant-planet-scene.js src/test/js/home-quant-planet-world.test.mjs src/test/java/com/kingyurina/demo/web/HomePageTemplateContractTest.java
  git commit -m "feat: reframe the quant planet world"
  ```

---

### Task 3: Fullscreen Semantic Surface, Intro, and Rotating Links

**Files:**

- Modify: `src/main/resources/templates/index.html`
- Modify: `src/main/resources/static/css/home-quant-planet.css`
- Modify: `src/test/java/com/kingyurina/demo/web/HomePageTemplateContractTest.java`

**Interfaces:**

- Consumes: no runtime data beyond stable local routes.
- Produces selectors:
  - `data-home-experience`
  - `data-home-scene`
  - `data-home-canvas`
  - `data-planet-fallback`
  - `data-intro-loader`
  - `data-intro-mark`
  - `data-product-orbit`
  - `data-product-orbit-link`
  - `data-home-semantic-summary`
  - `data-interaction-hint`

- [ ] **Step 1: Replace scroll-surface contracts with failing fullscreen contracts**

  Add a new Java test and remove assertions that require chapters, product
  grid, footer, Lenis, or document scrolling:

  ```java
  @Test
  void homepageIsOneFullscreenProductOrbit() throws IOException {
      String template = resource("templates/index.html");
      String css = resource("static/css/home-quant-planet.css")
              .replaceAll("\\s+", " ");

      assertTrue(template.contains(
              "class=\"home-page home-quant-planet home-quant-fullscreen\""));
      assertTrue(template.contains("data-home-experience"));
      assertTrue(template.contains("data-intro-loader"));
      assertTrue(template.contains("data-intro-mark"));
      assertTrue(template.contains("data-product-orbit"));
      assertEquals(6, occurrences(template, "data-product-orbit-link"));
      assertEquals(1, occurrences(template, "<h1"));
      assertTrue(template.contains("Market intelligence, in motion."));
      assertTrue(template.contains(
              "Observe the market. Resolve the signal. Act with context."));
      assertTrue(template.contains(
              "<dt>Signal</dt><dd>79 / 100</dd>"));
      assertTrue(template.contains(
              "<dt>20D Alpha</dt><dd>+2.4%</dd>"));
      assertTrue(template.contains(
              "<dt>Risk</dt><dd>Medium</dd>"));
      assertTrue(template.contains("Illustrative model view"));
      assertEquals(1, occurrences(template, ">Enter Quant</a>"));
      assertFalse(template.contains("/vendor/lenis/"));
      assertFalse(template.contains("data-home-chapter"));
      assertFalse(template.contains("home-product-list"));
      assertFalse(template.contains("home-footer"));

      assertTrue(css.contains("height: 100dvh"));
      assertTrue(css.contains("overflow: hidden"));
      assertTrue(css.contains("--planet-size: min(82vh, 72vw)"));
      assertTrue(css.contains("--planet-size: min(92vw, 68vh)"));
      assertTrue(css.contains(".product-orbit__link"));
      assertTrue(css.contains("min-height: 44px"));
      assertTrue(css.contains(":focus-visible"));
      assertTrue(css.contains("@media (prefers-reduced-motion: reduce)"));
  }
  ```

  Add exact link assertions:

  ```java
  assertTrue(template.contains("href=\"/quant\">QUANT</a>"));
  assertTrue(template.contains("href=\"/stocks\">STOCKS</a>"));
  assertTrue(template.contains(
          "href=\"/stocks/heatmap?index=SP500\">HEATMAP</a>"));
  assertTrue(template.contains("href=\"/etfs\">ETF</a>"));
  assertTrue(template.contains("href=\"/atelier\">ATELIER</a>"));
  assertTrue(template.contains(
          "href=\"/signals/backtest\">SIGNAL</a>"));
  ```

- [ ] **Step 2: Run the template test and confirm RED**

  ```powershell
  mvn.cmd "-Dmaven.repo.local=C:\dev\codex-demo\.m2\repository" -Dtest=HomePageTemplateContractTest#homepageIsOneFullscreenProductOrbit test
  ```

  Expected: FAIL against the scroll-driven template.

- [ ] **Step 3: Replace the visible homepage surface**

  Use this semantic hierarchy:

  ```html
  <body class="home-page home-quant-planet home-quant-fullscreen">
  <a class="home-skip" href="#product-orbit">Skip to product navigation</a>
  <header class="home-nav" data-home-nav>
    <a class="home-brand" href="/" aria-label="King Yurina home">
      <span>King Yurina</span><small>QUANT FIELD</small>
    </a>
    <a class="planet-primary-action" href="/quant">Enter Quant</a>
  </header>
  <main id="main-content" class="planet-experience"
        data-home-experience aria-labelledby="home-title">
    <div class="planet-intro-loader" data-intro-loader aria-hidden="true">
      <span class="planet-intro-loader__courier"></span>
      <p data-intro-mark>KING YURINA / QUANT FIELD</p>
    </div>
    <div class="planet-scene" data-home-scene aria-hidden="true">
      <div class="planet-fallback" data-planet-fallback>...</div>
      <canvas data-home-canvas aria-hidden="true"></canvas>
    </div>
    <nav id="product-orbit" class="product-orbit"
         data-product-orbit aria-label="Product navigation">
      <a class="product-orbit__link" data-product-orbit-link
         aria-label="Open Quant Intelligence" href="/quant">QUANT</a>
      <a class="product-orbit__link" data-product-orbit-link
         aria-label="Open Stocks" href="/stocks">STOCKS</a>
      <a class="product-orbit__link" data-product-orbit-link
         aria-label="Open Market Heatmap"
         href="/stocks/heatmap?index=SP500">HEATMAP</a>
      <a class="product-orbit__link" data-product-orbit-link
         aria-label="Open ETF Radar" href="/etfs">ETF</a>
      <a class="product-orbit__link" data-product-orbit-link
         aria-label="Open Strategy Atelier" href="/atelier">ATELIER</a>
      <a class="product-orbit__link" data-product-orbit-link
         aria-label="Open Signal Backtest"
         href="/signals/backtest">SIGNAL</a>
    </nav>
    <section class="home-semantic-summary" data-home-semantic-summary>
      <h1 id="home-title">Market intelligence, in motion.</h1>
      <p>Observe the market. Resolve the signal. Act with context.</p>
      <dl aria-label="Illustrative model view">
        <div><dt>Signal</dt><dd>79 / 100</dd></div>
        <div><dt>20D Alpha</dt><dd>+2.4%</dd></div>
        <div><dt>Risk</dt><dd>Medium</dd></div>
      </dl>
      <p>Illustrative model view</p>
    </section>
    <p class="planet-status">QUANT FIELD / ONLINE</p>
    <p class="planet-interaction-hint" data-interaction-hint>
      DRAG TO ORBIT / SELECT A FIELD
    </p>
  </main>
  <noscript>
    <style>
      .planet-intro-loader { display: none !important; }
      .home-semantic-summary { visibility: visible !important; }
      .product-orbit {
        display: grid !important;
        grid-template-columns: repeat(3, minmax(5rem, 1fr));
        align-content: end;
        gap: .5rem;
        padding: 1rem;
      }
      .product-orbit__link {
        position: relative !important;
        inset: auto !important;
        transform: none !important;
        opacity: 1 !important;
      }
    </style>
  </noscript>
  ```

  Retain the existing CSS fallback child elements for world, route, station,
  and courier.

- [ ] **Step 4: Replace scroll layout with fullscreen art direction**

  Preserve the semantic palette and local fonts. Establish:

  ```css
  html, body {
    width: 100%;
    height: 100%;
    height: 100dvh;
    margin: 0;
    overflow: hidden;
    overscroll-behavior: none;
  }

  .planet-experience {
    --planet-size: min(82vh, 72vw);
    position: fixed;
    inset: 0;
    min-height: 100dvh;
    overflow: hidden;
    isolation: isolate;
  }

  .planet-scene {
    position: absolute;
    width: var(--planet-size);
    height: var(--planet-size);
    left: 50%;
    top: 54%;
    transform: translate(-50%, -50%)
      scale(var(--intro-world-scale, .88));
  }

  .product-orbit {
    position: absolute;
    inset: 0;
    perspective: 1100px;
    pointer-events: none;
  }

  .product-orbit__link {
    position: absolute;
    left: 50%;
    top: 50%;
    display: inline-flex;
    min-width: 44px;
    min-height: 44px;
    align-items: center;
    justify-content: center;
    pointer-events: auto;
    transform:
      translate(-50%, -50%)
      translate3d(var(--orbit-x), var(--orbit-y), var(--orbit-z))
      scale(var(--orbit-scale));
    opacity: var(--orbit-opacity);
    z-index: var(--orbit-z-index);
  }
  ```

  Loader defaults to visible for runtime startup and the no-script style hides
  it. `.planet-intro-ready` makes it noninteractive and invisible.
  `.planet-webgl-ready` hides only the CSS world, never the navigation.

  The semantic summary is a readable paper panel in the base/static
  composition. Only a successful animated scene converts it to a visually
  hidden accessible summary:

  ```css
  .planet-webgl-ready .home-semantic-summary {
    position: absolute;
    width: 1px;
    height: 1px;
    padding: 0;
    margin: -1px;
    overflow: hidden;
    clip: rect(0 0 0 0);
    clip-path: inset(50%);
    white-space: nowrap;
    border: 0;
  }
  ```

  At `max-width: 767px` set:

  ```css
  .planet-experience { --planet-size: min(92vw, 68vh); }
  .planet-scene { top: 49%; }
  ```

  Reduced motion removes transition/animation declarations and lays the six
  links into a stable two-row or elliptical arrangement without auto rotation.

- [ ] **Step 5: Run focused and full template tests**

  ```powershell
  mvn.cmd "-Dmaven.repo.local=C:\dev\codex-demo\.m2\repository" -Dtest=HomePageTemplateContractTest test
  ```

  Expected: all homepage template contracts pass after obsolete scroll
  assertions are removed or replaced.

- [ ] **Step 6: Commit**

  ```powershell
  git add src/main/resources/templates/index.html src/main/resources/static/css/home-quant-planet.css src/test/java/com/kingyurina/demo/web/HomePageTemplateContractTest.java
  git commit -m "feat: compose the fullscreen quant planet"
  ```

---

### Task 4: Direct Manipulation, Intro Runtime, and Scroll Retirement

**Files:**

- Create: `src/main/resources/static/js/home-quant-planet-input.js`
- Create: `src/test/js/home-quant-planet-input.test.mjs`
- Modify: `src/main/resources/static/js/home-quant-planet.js`
- Modify: `src/main/resources/templates/index.html`
- Modify: `src/test/java/com/kingyurina/demo/web/HomePageTemplateContractTest.java`
- Delete: `src/main/resources/static/js/home-quant-planet-scroll.js`

**Interfaces:**

- Consumes: fullscreen selectors and Task 1/2 state/scene interfaces.
- Produces:
  - `createOrbitInputController({ surface, links, onChange }): OrbitInputController`
  - runtime root classes:
    `.planet-runtime-starting`, `.planet-intro-ready`,
    `.planet-webgl-ready`, `.planet-webgl-failed`;
  - root attributes:
    `data-intro-phase`, `data-product-front`;
  - loopback canvas diagnostics:
    `data-render-ms`, `data-draw-calls`, `data-triangles`.

- [ ] **Step 1: Write failing input-controller tests**

  Use a small fake event target that stores listeners by type. Verify pointer
  capture, bounded drag, arrow wrap, focus pause, and cleanup:

  ```js
  function createFakeTarget() {
      const listeners = new Map();
      return {
          listeners,
          addEventListener(type, listener) {
              const group = listeners.get(type) || new Set();
              group.add(listener);
              listeners.set(type, group);
          },
          removeEventListener(type, listener) {
              listeners.get(type)?.delete(listener);
          },
          emit(type, event = {}) {
              [...(listeners.get(type) || [])]
                  .forEach((listener) => listener(event));
          },
          listenerCount() {
              return [...listeners.values()]
                  .reduce((sum, group) => sum + group.size, 0);
          },
          getBoundingClientRect() {
              return { left: 0, top: 0, width: 400, height: 300 };
          },
          setPointerCapture() {},
          releasePointerCapture() {}
      };
  }

  function createFocusableTarget() {
      const target = createFakeTarget();
      target.focusCalls = 0;
      target.focus = () => { target.focusCalls += 1; };
      return target;
  }

  test("drags the product orbit without registering wheel behavior", () => {
      const surface = createFakeTarget();
      const links = Array.from({ length: 6 }, createFocusableTarget);
      const states = [];
      const controller = createOrbitInputController({
          surface, links, onChange(state) { states.push(state); }
      });
      controller.connect();

      assert.equal(surface.listeners.has("wheel"), false);
      surface.emit("pointerdown", {
          pointerId: 4, clientX: 100, button: 0,
          preventDefault() {}
      });
      surface.emit("pointermove", {
          pointerId: 4, clientX: 260, preventDefault() {}
      });
      surface.emit("pointerup", { pointerId: 4 });

      assert.ok(states.some((state) => state.dragging));
      assert.ok(Math.abs(controller.getState().dragAngle) <= Math.PI * 4);
      controller.destroy();
      assert.equal(surface.listenerCount(), 0);
  });

  test("wraps arrow-key focus through all six product links", () => {
      const surface = createFakeTarget();
      const links = Array.from({ length: 6 }, createFocusableTarget);
      const controller = createOrbitInputController({
          surface, links, onChange() {}
      });
      controller.connect();
      links[5].emit("keydown", {
          key: "ArrowRight", preventDefault() {}
      });
      assert.equal(links[0].focusCalls, 1);
      links[0].emit("focus");
      assert.equal(controller.getState().selectedIndex, 0);
      assert.equal(controller.getState().paused, true);
  });
  ```

- [ ] **Step 2: Add failing Java orchestration/retirement contracts**

  ```java
  @Test
  void homepageCoordinatesFullscreenOrbitRuntime() throws IOException {
      String entry = resource("static/js/home-quant-planet.js");

      assertTrue(entry.contains("./home-quant-planet-input.js"));
      assertTrue(entry.contains("createIntroState"));
      assertTrue(entry.contains("createNavigationRingState"));
      assertTrue(entry.contains("createOrbitInputController"));
      assertTrue(entry.contains("webglcontextlost"));
      assertTrue(entry.contains("4000"));
      assertTrue(entry.contains("visibilitychange"));
      assertTrue(entry.contains("pagehide"));
      assertTrue(entry.contains("pageshow"));
      assertFalse(entry.contains("createScrollController"));
      assertFalse(entry.contains("normalizeScrollProgress"));
      assertFalse(entry.contains("IntersectionObserver"));
      assertFalse(entry.contains("setInterval"));
      assertFalse(entry.contains("fetch("));
      assertFalse(new ClassPathResource(
              "static/js/home-quant-planet-scroll.js").exists());
  }
  ```

- [ ] **Step 3: Run focused tests and confirm RED**

  ```powershell
  node --test src/test/js/home-quant-planet-input.test.mjs
  mvn.cmd "-Dmaven.repo.local=C:\dev\codex-demo\.m2\repository" -Dtest=HomePageTemplateContractTest#homepageCoordinatesFullscreenOrbitRuntime test
  ```

  Expected: FAIL because the input module and fullscreen coordinator are absent.

- [ ] **Step 4: Implement the input controller**

  `createOrbitInputController` owns only input listeners and state:

  ```js
  let dragAngle = 0;
  let selectedIndex = 0;
  let dragging = false;
  let focused = false;
  let hovered = false;
  let pointer = { x: 0, y: 0 };
  let startX = 0;
  let startAngle = 0;

  function snapshot() {
      return {
          dragAngle, selectedIndex, dragging, focused, hovered, pointer,
          paused: dragging || focused || hovered,
          snapToSelected: focused || hovered
      };
  }
  ```

  Pointer delta is `deltaX * .006`, bounded to `±Math.PI * 4`.
  Every `pointermove` normalizes the pointer against
  `surface.getBoundingClientRect()` with Task 1's `normalizePointer`; a
  non-drag move therefore drives bounded parallax without changing
  `dragAngle`. `pointerleave` resets pointer to `{ x: 0, y: 0 }` when no drag
  is active.
  Use `setPointerCapture`/`releasePointerCapture` when available.
  A `pointerdown` whose target is one of `links` does not start a drag, so a
  normal click always activates the anchor.
  `pointerup` and `pointercancel` share cleanup. Link `focus` sets the selected
  index and pauses; `blur` resumes only when focus has left the link set.
  Link `pointerenter`/`pointerleave` sets the selected index and hover pause
  state using the same cleanup rule.
  Arrow Left/Right wraps with `wrapIndex` and calls `.focus()`.
  Do not register `wheel`, keyboard walking controls, or global shortcuts.
  `destroy()` removes every listener and releases active pointer state.

- [ ] **Step 5: Rewrite the page coordinator**

  Imports:

  ```js
  import {
      advanceRingAngle,
      createIntroState,
      createNavigationRingState,
      createPageLifecycle,
      normalizePointer,
      resolveLocalTestOverrides,
      selectPlanetQualityProfile
  } from "./home-quant-planet-motion.js";
  import { createOrbitInputController }
      from "./home-quant-planet-input.js";
  import { createQuantPlanetScene }
      from "./home-quant-planet-scene.js";
  ```

  Runtime order:

  1. Query the experience, scene, canvas, loader, and six links.
  2. Detect reduced motion, WebGL, mobile, cores, DPR, and loopback overrides.
  3. Add `.planet-runtime-starting`.
  4. If the selected profile is static, apply the ready static intro state
     immediately. Otherwise start scene construction and record `sceneReadyAt`
     when it resolves.
  5. Create the input controller only after selectors are valid.
     Its `onChange` callback stores the latest state and immediately reapplies
     a paused ring state when no animation lifecycle is running, so keyboard
     focus and direct drag still update the static DOM navigation.
  6. In one lifecycle tick:
     - resolve timeout with `elapsed >= 4000 && !scene`;
     - compute/apply intro state;
     - update a coordinator-owned `ringAngle` with
       `advanceRingAngle(ringAngle, time - lastFrameTime,
       inputState.paused, motionDisabled)`;
     - compute/apply ring state with `rotationAngle: ringAngle`,
       `selectedIndex: inputState.selectedIndex`, and
       `snapToSelected: inputState.snapToSelected`;
     - pass `inputState.pointer` to `scene.setPointer()` and
       `inputState.dragAngle` to `scene.setDragAngle()`;
     - render and collect loopback diagnostics.
  7. Mark `.planet-intro-ready` after ready or timeout.
  8. On `webglcontextlost`, prevent default, set `runtimeFailed = true`, call
     `lifecycle.pause()`, remove the BFCache `pageshow` resume listener, dispose
     the scene, add `.planet-webgl-failed`, dismiss the loader, apply one paused
     DOM-ring state, and retain the connected native links/input controller.
  9. On hidden/pagehide/BFCache follow the existing idempotent lifecycle.

  Apply each ring item through CSS variables:

  ```js
  const radiusX = Math.min(window.innerWidth * .34, window.innerHeight * .48);
  const radiusY = Math.min(window.innerHeight * .10, 88);
  state.items.forEach((item, index) => {
      const link = productLinks[index];
      link.style.setProperty("--orbit-x",
          `${Math.sin(item.angle) * radiusX}px`);
      link.style.setProperty("--orbit-y",
          `${Math.sin(item.angle * 2) * radiusY}px`);
      link.style.setProperty("--orbit-z",
          `${(item.depth - .5) * 240}px`);
      link.style.setProperty("--orbit-scale", item.scale.toFixed(3));
      link.style.setProperty("--orbit-opacity", item.opacity.toFixed(3));
      link.style.setProperty("--orbit-z-index", String(item.zIndex));
  });
  root.dataset.productFront = String(state.frontIndex);
  ```

  On reduced motion or static profile, apply one stable ring state, do not
  start the animated lifecycle, and keep native links/input available. Focus
  changes call `createNavigationRingState` with `snapToSelected: true`;
  WebGL-unavailable direct drag may reposition the DOM ring, but it has no
  momentum or automatic continuation.

- [ ] **Step 6: Remove Lenis and scroll runtime**

  Remove Lenis CSS/script tags from `index.html`, remove the scroll import and
  all scroll/IntersectionObserver code, and delete only:

  ```text
  src/main/resources/static/js/home-quant-planet-scroll.js
  ```

  Keep the self-hosted Lenis files and license in the repository because other
  pages or future work may depend on them.

- [ ] **Step 7: Run Node and Java suites**

  ```powershell
  node --test src/test/js/home-quant-planet-motion.test.mjs src/test/js/home-quant-planet-input.test.mjs src/test/js/home-quant-planet-world.test.mjs
  mvn.cmd "-Dmaven.repo.local=C:\dev\codex-demo\.m2\repository" test
  ```

  Expected: zero failures; no test references scroll chapters or Lenis.

- [ ] **Step 8: Commit**

  ```powershell
  git add src/main/resources/templates/index.html src/main/resources/static/js/home-quant-planet.js src/main/resources/static/js/home-quant-planet-input.js src/test/js/home-quant-planet-input.test.mjs src/test/java/com/kingyurina/demo/web/HomePageTemplateContractTest.java
  git add -u src/main/resources/static/js
  git commit -m "feat: launch the fullscreen quant planet"
  ```

---

### Task 5: Browser QA, Performance, and Regression Closure

**Files:**

- Modify only when a failing automated/browser check requires:
  - `src/main/resources/templates/index.html`
  - `src/main/resources/static/css/home-quant-planet.css`
  - `src/main/resources/static/js/home-quant-planet.js`
  - `src/main/resources/static/js/home-quant-planet-input.js`
  - `src/main/resources/static/js/home-quant-planet-motion.js`
  - `src/main/resources/static/js/home-quant-planet-world.js`
  - `src/main/resources/static/js/home-quant-planet-scene.js`
  - the three homepage Node tests;
  - `src/test/java/com/kingyurina/demo/web/HomePageTemplateContractTest.java`.

**Interfaces:**

- Consumes: complete local fullscreen homepage.
- Produces: browser evidence for every viewport, entry, input, fallback, and performance acceptance criterion.

- [ ] **Step 1: Run automated verification before browser work**

  ```powershell
  node --test src/test/js/home-quant-planet-motion.test.mjs src/test/js/home-quant-planet-input.test.mjs src/test/js/home-quant-planet-world.test.mjs
  mvn.cmd "-Dmaven.repo.local=C:\dev\codex-demo\.m2\repository" test
  git diff --check
  ```

- [ ] **Step 2: Build and start only the isolated server**

  ```powershell
  mvn.cmd "-Dmaven.repo.local=C:\dev\codex-demo\.m2\repository" -DskipTests package
  ```

  Start the worktree artifact on `127.0.0.1:8081`, hidden, with the existing
  MariaDB profile and startup Signal refresh disabled. Do not stop or replace a
  user's `8080` process.

- [ ] **Step 3: Verify desktop entry and fullscreen scale**

  At `1440×900`:

  - the loader appears without a blank frame;
  - mark → split → reveal → ready completes once;
  - planet diameter is visually within 70–85% of viewport height;
  - all three landmarks and courier read as authored objects;
  - the six-word ring crosses the world with correct front/back depth;
  - `Enter Quant` is visible;
  - `document.documentElement.scrollHeight <= window.innerHeight`;
  - `document.documentElement.scrollWidth <= window.innerWidth`;
  - a reload replays entry, BFCache return does not.

- [ ] **Step 4: Verify navigation and input**

  - Let the ring complete enough motion to confirm continuous order.
  - Hover and focus each anchor; rotation pauses.
  - Activate every anchor and confirm its exact destination.
  - Tab order reaches skip link, brand, Enter Quant, and all six links.
  - Arrow Left/Right wraps from first to last and last to first.
  - Drag horizontally; planet and ring respond together.
  - Touch emulation uses the same drag response.
  - Dispatch wheel input; scroll position, zoom, and orbit angle do not change.
  - No walking, free-camera, or game-control behavior exists.

- [ ] **Step 5: Verify responsive layouts**

  At `1024×768`, `768×1024`, and `375×812`:

  - no horizontal or vertical document overflow;
  - planet and front ring label do not cover Enter Quant;
  - ring words remain readable and clickable;
  - every measured interactive rectangle is at least `44×44px`;
  - safe-area/header/hint positioning remains within the viewport;
  - mobile uses the balanced profile.

- [ ] **Step 6: Verify fallbacks**

  - `/?motion=reduce`: no loader choreography, automatic ring rotation,
    courier walking, parallax, or momentum; all six links visible.
  - `/?render=static`: no WebGL canvas output; static planet and links usable.
  - Disable JavaScript: loader hidden, static composition, semantic summary,
    Enter Quant, and six links visible.
  - Force context loss through the supported WebGL extension; the page exits to
    the static composition without a blank frame or reload loop.
  - Confirm missing Motion still reveals the finished page.

- [ ] **Step 7: Verify performance and runtime hygiene**

  After at least 60 active frames:

  ```js
  Number(canvas.dataset.renderMs) <= 12
  Number(canvas.dataset.drawCalls) <= 75
  Number(canvas.dataset.triangles) <= 140000
  ```

  Confirm:

  - no console errors;
  - one rAF loop;
  - rendering pauses while the page is hidden;
  - no external homepage-owned runtime requests;
  - renderer/world/input cleanup remains idempotent.

- [ ] **Step 8: Add regression tests before browser-discovered fixes**

  Pure timing/input/state defects receive a failing Node assertion. Markup,
  route, accessibility, or CSS contract defects receive a failing JUnit
  assertion. Apply the smallest fix and rerun the covering test before
  repeating the browser check.

- [ ] **Step 9: Run final verification**

  ```powershell
  node --test src/test/js/home-quant-planet-motion.test.mjs src/test/js/home-quant-planet-input.test.mjs src/test/js/home-quant-planet-world.test.mjs
  mvn.cmd "-Dmaven.repo.local=C:\dev\codex-demo\.m2\repository" test
  rg -n "home-observatory|home-quant-planet-scroll|vendor/lenis|data-home-chapter|normalizeScrollProgress|fetch\\(|https?://" src/main/resources/templates/index.html src/main/resources/static/css/home-quant-planet.css src/main/resources/static/js -g "home-quant-planet*.js"
  git diff --check
  git status --short
  ```

  Expected:

  - Node and Maven suites pass;
  - forbidden homepage references have no matches;
  - no whitespace errors;
  - only intentional fullscreen Quant files differ.

- [ ] **Step 10: Commit verified fixes only when changes exist**

  ```powershell
  git add src/main/resources/templates/index.html src/main/resources/static/css/home-quant-planet.css src/main/resources/static/js/home-quant-planet*.js src/test/js/home-quant-planet*.mjs src/test/java/com/kingyurina/demo/web/HomePageTemplateContractTest.java
  git commit -m "fix: polish the fullscreen quant planet"
  ```

  Do not create an empty commit when browser QA requires no source change.
