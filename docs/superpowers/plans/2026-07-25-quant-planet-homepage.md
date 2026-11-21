# Quant Planet Homepage Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the Quant Observatory homepage with an original, guided Three.js miniature Quant world featuring an animated analysis courier, three authored landmarks, and accessible Observe → Resolve → Act storytelling.

**Architecture:** Keep `HomeController#index` and all subpages unchanged. Replace only homepage-owned Thymeleaf, CSS, and vanilla ES modules; preserve the existing self-hosted Three.js, Lenis, Motion, and fonts; keep scroll/camera/courier calculations pure and separately tested while one scene controller owns rendering and disposal.

**Tech Stack:** Java 21, Spring Boot 4.0.6, Thymeleaf, vanilla ES modules, Three.js r165, Lenis 1.3.25, Motion 12.29.2, Instrument Sans, IBM Plex Mono, JUnit 5, Node test runner.

## Global Constraints

- Implement the approved spec at `docs/superpowers/specs/2026-07-25-quant-planet-homepage-design.md`.
- At execution time use `superpowers:using-git-worktrees`; branch from `main` into `codex/quant-planet-homepage`.
- Preserve every unrelated tracked and untracked user file in the primary checkout.
- Redesign only `/`; do not modify `/quant` or another subpage.
- Keep `HomeController#index` unchanged and do not add a database or external API dependency to the homepage request.
- Reuse the existing self-hosted Three.js r165, Lenis 1.3.25, Motion 12.29.2, Instrument Sans, IBM Plex Mono, and their licenses.
- Do not introduce React, npm runtime code, a frontend build pipeline, remote runtime assets, Blender, multiplayer, audio autoplay, game controls, or copied Messenger assets.
- Use the exact headline `Market intelligence, in motion.`
- Use the exact narrative `Observe the market. Resolve the signal. Act with context.`
- Show `Signal 79 / 100`, `20D Alpha +2.4%`, and `Risk Medium` only with `Illustrative model view`.
- Use `Enter Quant` as the only primary CTA label; every instance resolves to `/quant`.
- Cap pixel ratio at `1.5`, high-profile draw calls at `75`, visible triangles at `140000`, and loopback average scene submission at `12.0ms`.
- Pause rendering when the scene is outside its visible range or the page is hidden.
- Keep all content usable with JavaScript disabled, WebGL unavailable, and `prefers-reduced-motion: reduce`.
- Validate 375px, 768px, 1024px, and 1440px layouts with no body horizontal overflow.
- Keep interactive targets at least 44×44px with visible `:focus-visible`.
- Use `mvn.cmd "-Dmaven.repo.local=.m2/repository" test` for full Java verification.

## File Map

### Create

- `src/main/resources/static/css/home-quant-planet.css`
- `src/main/resources/static/js/home-quant-planet.js`
- `src/main/resources/static/js/home-quant-planet-motion.js`
- `src/main/resources/static/js/home-quant-planet-scroll.js`
- `src/main/resources/static/js/home-quant-planet-world.js`
- `src/main/resources/static/js/home-quant-planet-scene.js`
- `src/test/js/home-quant-planet-motion.test.mjs`

### Modify

- `src/main/resources/templates/index.html`
- `src/test/java/com/kingyurina/demo/web/HomePageTemplateContractTest.java`

### Delete after all references migrate

- `src/main/resources/static/css/home-observatory.css`
- `src/main/resources/static/js/home-observatory.js`
- `src/main/resources/static/js/home-observatory-motion.js`
- `src/main/resources/static/js/home-observatory-scroll.js`
- `src/main/resources/static/js/home-observatory-scene.js`
- `src/test/js/home-observatory-motion.test.mjs`

### Preserve

- `src/main/java/com/kingyurina/demo/web/HomeController.java`
- `src/test/java/com/kingyurina/demo/web/HomeControllerIndexTest.java`
- `src/main/resources/static/js/vendor/three.module.js`
- `src/main/resources/static/js/vendor/three.LICENSE`
- `src/main/resources/static/vendor/lenis/*`
- `src/main/resources/static/vendor/motion/*`
- `src/main/resources/static/fonts/*`
- `src/main/resources/static/js/home-nature-3d.js`

---

### Task 1: Pure Journey, Courier, Quality, and Lifecycle Contracts

**Files:**

- Create: `src/test/js/home-quant-planet-motion.test.mjs`
- Create: `src/main/resources/static/js/home-quant-planet-motion.js`
- Create: `src/main/resources/static/js/home-quant-planet-scroll.js`

**Interfaces:**

- Consumes: numeric scroll/pointer/environment state and optional `window.Motion`/`window.Lenis`.
- Produces:
  - `clampUnit(value): number`
  - `normalizePointer(clientX, clientY, rect): { x, y }`
  - `normalizeScrollProgress(top, height, viewportHeight): number`
  - `resolveLocalTestOverrides({ hostname, search }): { forceStatic, forceReduced }`
  - `selectPlanetQualityProfile(environment): PlanetQualityProfile`
  - `resolvePlanetChapter(progress): "observe" | "resolve" | "act"`
  - `createPlanetPose(progress, pointer?): PlanetPose`
  - `createCourierPose(progress, time?): CourierPose`
  - `shouldRenderFrame(state): boolean`
  - `createPageLifecycle(options): PageLifecycle`
  - `createDomMotion(options): DomMotion`
  - `createScrollController(options): ScrollController`

- [ ] **Step 1: Write failing quality, chapter, and pose tests**

  Create the Node test with these assertions:

  ```js
  import assert from "node:assert/strict";
  import test from "node:test";
  import {
    createCourierPose,
    createPlanetPose,
    resolvePlanetChapter,
    selectPlanetQualityProfile
  } from "../../main/resources/static/js/home-quant-planet-motion.js";

  test("selects bounded planet quality profiles", () => {
    assert.equal(selectPlanetQualityProfile({
      reduceMotion: true, webgl: true, mobile: false, cores: 16, dpr: 3
    }).name, "static");
    assert.deepEqual(selectPlanetQualityProfile({
      reduceMotion: false, webgl: true, mobile: true, cores: 8, dpr: 3
    }), {
      name: "balanced", webgl: true, pixelRatio: 1.25,
      pointerEnabled: false, shadows: false, outlineLevel: 1,
      foliageCount: 18, cloudCount: 5, planetScale: 0.82
    });
    assert.equal(selectPlanetQualityProfile({
      reduceMotion: false, webgl: true, mobile: false, cores: 16, dpr: 3
    }).pixelRatio, 1.5);
  });

  test("uses overlapping authored chapters without gaps", () => {
    assert.equal(resolvePlanetChapter(0), "observe");
    assert.equal(resolvePlanetChapter(0.33), "resolve");
    assert.equal(resolvePlanetChapter(0.69), "act");
    assert.equal(resolvePlanetChapter(1), "act");
  });

  test("keeps camera and courier inside the authored world envelope", () => {
    for (const progress of [0, .15, .32, .5, .7, .86, 1]) {
      const pose = createPlanetPose(progress, { x: 1, y: -1 });
      assert.ok(pose.cameraDistance >= 7.1 && pose.cameraDistance <= 10.8);
      assert.ok(Math.abs(pose.cameraYaw) <= 1.45);
      assert.ok(Math.abs(pose.cameraPitch) <= .62);
      const courier = createCourierPose(progress, 1250);
      assert.ok(Number.isFinite(courier.routeAngle));
      assert.ok(courier.walkPhase >= 0 && courier.walkPhase <= Math.PI * 2);
    }
  });
  ```

- [ ] **Step 2: Run the Node test and confirm RED**

  ```powershell
  node --test src/test/js/home-quant-planet-motion.test.mjs
  ```

  Expected: FAIL with `ERR_MODULE_NOT_FOUND`.

- [ ] **Step 3: Implement the pure profile and pose functions**

  Implement bounded helpers with this shape:

  ```js
  export function resolvePlanetChapter(progress) {
    const p = clampUnit(progress);
    return p < .30 ? "observe" : p < .66 ? "resolve" : "act";
  }

  export function selectPlanetQualityProfile(environment = {}) {
    if (environment.reduceMotion || !environment.webgl) {
      return {
        name: "static", webgl: false, pixelRatio: 1,
        pointerEnabled: false, shadows: false, outlineLevel: 0,
        foliageCount: 0, cloudCount: 0, planetScale: 1
      };
    }
    if (environment.mobile || Number(environment.cores) <= 4) {
      return {
        name: "balanced", webgl: true, pixelRatio: 1.25,
        pointerEnabled: false, shadows: false, outlineLevel: 1,
        foliageCount: 18, cloudCount: 5, planetScale: .82
      };
    }
    return {
      name: "high", webgl: true,
      pixelRatio: Math.min(Math.max(Number(environment.dpr) || 1, 1), 1.5),
      pointerEnabled: true, shadows: true, outlineLevel: 2,
      foliageCount: 34, cloudCount: 8, planetScale: 1
    };
  }

  export function createCourierPose(progress, time = 0) {
    const p = clampUnit(progress);
    const routeAngle = -.9 + p * 2.18;
    const walkPhase = ((p * 20) + time / 330) % (Math.PI * 2);
    return {
      routeAngle,
      elevation: .17 + Math.sin(p * Math.PI) * .025,
      heading: routeAngle + Math.PI / 2,
      walkPhase,
      idleBlend: smoothStep(resolveRange(p, .9, 1))
    };
  }

  export function createPlanetPose(progress, pointer = { x: 0, y: 0 }) {
    const p = clampUnit(progress);
    const resolve = smoothStep(resolveRange(p, .24, .70));
    const act = smoothStep(resolveRange(p, .62, 1));
    const pointerX = Math.max(-1, Math.min(1, Number(pointer.x) || 0));
    const pointerY = Math.max(-1, Math.min(1, Number(pointer.y) || 0));
    return {
      cameraDistance: 10.8 - smoothStep(p) * 3.2,
      cameraYaw: -.95 + p * 1.9 + pointerX * .0698,
      cameraPitch: .32 - resolve * .18 + pointerY * .052,
      planetYaw: -.48 + p * 1.35,
      planetPitch: -.1 + resolve * .08,
      planetRoll: -.04 + act * .07,
      routeActivity: .28 + Math.sin(p * Math.PI) * .72,
      landmarkActivity: {
        observe: 1 - resolve,
        resolve: resolve * (1 - act),
        act
      },
      metricOpacity: clampUnit(resolve * (1 - act * .72))
    };
  }
  ```

  Keep all segments continuous, cap pointer influence at four degrees, and
  return only finite values.

- [ ] **Step 4: Add lifecycle, reduced-motion, Lenis, and BFCache tests**

  Port the existing observatory tests for normalization, local-only
  `?render=static`/`?motion=reduce`, `shouldRenderFrame`, `createDomMotion`,
  `createPageLifecycle`, and `createScrollController`. Change the root state
  attribute to `data-planet-chapter` and keep the reveal selector
  `[data-home-reveal]`.

- [ ] **Step 5: Implement lifecycle and scroll adapters**

  Preserve one `requestAnimationFrame` owner and the Lenis options:

  ```js
  const lenis = new LenisClass({
    autoRaf: false,
    anchors: true,
    lerp: .085,
    smoothWheel: true,
    syncTouch: false,
    wheelMultiplier: .9
  });
  ```

  Reduced motion must return a disabled controller without constructing Lenis.

- [ ] **Step 6: Run the Node test and confirm GREEN**

  Run the command from Step 2.

  Expected: all pure JavaScript tests pass with zero failures.

- [ ] **Step 7: Commit**

  ```powershell
  git add src/test/js/home-quant-planet-motion.test.mjs src/main/resources/static/js/home-quant-planet-motion.js src/main/resources/static/js/home-quant-planet-scroll.js
  git commit -m "feat: define quant planet journey"
  ```

---

### Task 2: Authored Planet, Landmarks, Courier, and Scene Controller

**Files:**

- Create: `src/main/resources/static/js/home-quant-planet-world.js`
- Create: `src/main/resources/static/js/home-quant-planet-scene.js`
- Modify: `src/test/java/com/kingyurina/demo/web/HomePageTemplateContractTest.java`

**Interfaces:**

- Consumes: Three.js r165, `PlanetQualityProfile`, `PlanetPose`, and `CourierPose`.
- Produces:
  - `createQuantPlanetWorld(THREE, { profile }): QuantPlanetWorld`
  - `QuantPlanetWorld.update({ planetPose, courierPose, time })`
  - `QuantPlanetWorld.dispose()`
  - `createQuantPlanetScene({ canvas, profile }): Promise<SceneController>`
  - scene controller methods from the approved spec.

- [ ] **Step 1: Write failing local-world source contracts**

  Add this JUnit test:

  ```java
  @Test
  void homepageBuildsAnOriginalLocalQuantPlanetWorld() throws IOException {
      String world = resource("static/js/home-quant-planet-world.js");
      String scene = resource("static/js/home-quant-planet-scene.js");

      assertTrue(world.contains("createQuantPlanetWorld"));
      assertTrue(world.contains("ObserveStation"));
      assertTrue(world.contains("ResolveWorks"));
      assertTrue(world.contains("DecisionGate"));
      assertTrue(world.contains("QuantCourier"));
      assertTrue(world.contains("MeshToonMaterial"));
      assertTrue(world.contains("CatmullRomCurve3"));
      assertTrue(world.contains("InstancedMesh"));
      assertTrue(world.contains("BackSide"));
      assertTrue(scene.contains("/js/vendor/three.module.js"));
      assertTrue(scene.contains("createQuantPlanetScene"));
      assertTrue(scene.contains("setPixelRatio(profile.pixelRatio)"));
      assertTrue(scene.contains("getDiagnostics"));
      assertTrue(scene.contains("dispose()"));
      assertFalse(world.contains("fetch("));
      assertFalse(scene.contains("fetch("));
      assertFalse(world.contains("http://"));
      assertFalse(scene.contains("https://"));
  }
  ```

- [ ] **Step 2: Run the JUnit method and confirm RED**

  ```powershell
  mvn.cmd "-Dmaven.repo.local=.m2/repository" -Dtest=HomePageTemplateContractTest#homepageBuildsAnOriginalLocalQuantPlanetWorld test
  ```

  Expected: FAIL because the world and scene modules do not exist.

- [ ] **Step 3: Build the world material and outline factories**

  In `home-quant-planet-world.js`, create:

  ```js
  function createToonMaterial(THREE, color, gradientMap) {
    return new THREE.MeshToonMaterial({ color, gradientMap });
  }

  function addOutlinedMesh(THREE, parent, geometry, material, outlineMaterial, {
    position = [0, 0, 0], rotation = [0, 0, 0], scale = [1, 1, 1]
  } = {}) {
    const mesh = new THREE.Mesh(geometry, material);
    const outline = new THREE.Mesh(geometry, outlineMaterial);
    outline.material.side = THREE.BackSide;
    outline.scale.set(1.035, 1.035, 1.035);
    mesh.add(outline);
    parent.add(mesh);
    mesh.position.set(...position);
    mesh.rotation.set(...rotation);
    mesh.scale.set(...scale);
    return mesh;
  }
  ```

  Use a 4-step `DataTexture` gradient map with nearest filtering. Limit outline
  duplication according to `profile.outlineLevel`.

- [ ] **Step 4: Build the authored miniature world**

  Start the authored layout with named groups and an explicit route:

  ```js
  const planet = new THREE.Group();
  planet.name = "QuantPlanet";
  const landmarks = new THREE.Group();
  landmarks.name = "Landmarks";
  const observe = new THREE.Group();
  observe.name = "ObserveStation";
  const resolve = new THREE.Group();
  resolve.name = "ResolveWorks";
  const act = new THREE.Group();
  act.name = "DecisionGate";
  const courier = new THREE.Group();
  courier.name = "QuantCourier";

  const routeCurve = new THREE.CatmullRomCurve3([
    new THREE.Vector3(-2.5, 2.65, .4),
    new THREE.Vector3(-1.05, 3.02, 1.4),
    new THREE.Vector3(.7, 2.92, 1.55),
    new THREE.Vector3(2.25, 2.52, .62)
  ], false, "catmullrom", .38);

  observe.position.set(-2.1, 2.45, .1);
  resolve.position.set(.1, 2.65, 1.15);
  act.position.set(2.15, 2.22, .28);
  landmarks.add(observe, resolve, act);
  planet.add(landmarks, courier);
  ```

  Complete the named groups with:

  - faceted land sphere and shallow-water shell;
  - raised route from one `CatmullRomCurve3`;
  - Observe Station dish, mast, wires, signs, and calibration props;
  - Resolve Works with three named instruments and moving sorter pieces;
  - Decision Gate with route lamps and product portal;
  - clustered rocks, foliage, poles, clouds, and distant silhouettes;
  - one articulated `QuantCourier` group with named body, head, antenna,
    canister, arms, thighs, and shins.

  Repeated foliage, rocks, poles, and route lamps use `InstancedMesh`.
  Landmarks use asymmetric roof, pipe, vent, stair, dish, and cable geometry so
  they do not read as undecorated boxes.

- [ ] **Step 5: Implement deterministic world updates and disposal**

  Drive all movement from supplied poses rather than reading DOM or scroll
  state inside the world:

  ```js
  function update({ planetPose, courierPose, time = 0 }) {
    planet.rotation.set(
      planetPose.planetPitch,
      planetPose.planetYaw,
      planetPose.planetRoll
    );
    const routeProgress = Math.min(Math.max(
      (courierPose.routeAngle + .9) / 2.18, 0
    ), 1);
    const point = routeCurve.getPointAt(routeProgress);
    courier.position.copy(point);
    courier.rotation.set(0, courierPose.heading, 0);
    leftThigh.rotation.x = Math.sin(courierPose.walkPhase) * .5
      * (1 - courierPose.idleBlend);
    rightThigh.rotation.x = -leftThigh.rotation.x;
    leftArm.rotation.x = -leftThigh.rotation.x * .72;
    rightArm.rotation.x = -rightThigh.rotation.x * .72;
    observeDish.rotation.y = time * .00018;
    resolveSorter.position.y = resolveSorterBaseY
      + planetPose.landmarkActivity.resolve * Math.sin(time * .003) * .08;
    routeLights.material.emissiveIntensity = .35
      + planetPose.routeActivity * 1.4;
  }
  ```

  This update must also:

  - place the courier on the route by `courierPose.routeAngle`;
  - align its local up vector to the planet normal;
  - animate legs and arms from `walkPhase`;
  - blend into idle at the Decision Gate;
  - rotate the Observe dish;
  - move Resolve sorter parts only while Resolve is active;
  - illuminate route lamps only near the active chapter;
  - move clouds slowly only when full motion is enabled.

  Track every geometry, material, and generated texture in sets and dispose
  each resource exactly once in `world.dispose()`.

- [ ] **Step 6: Implement the scene controller**

  In `home-quant-planet-scene.js`:

  ```js
  export async function createQuantPlanetScene({ canvas, profile } = {}) {
    if (!canvas || !profile?.webgl || profile.name === "static") {
      return createStaticSceneController();
    }
    const THREE = await import("/js/vendor/three.module.js");
    const renderer = new THREE.WebGLRenderer({
      canvas, alpha: true, antialias: profile.name === "high",
      powerPreference: "high-performance"
    });
    renderer.outputColorSpace = THREE.SRGBColorSpace;
    renderer.toneMapping = THREE.ACESFilmicToneMapping;
    renderer.setPixelRatio(profile.pixelRatio);
    const world = createQuantPlanetWorld(THREE, { profile });
    let progress = 0;
    let pointer = { x: 0, y: 0 };
    let visible = true;
    let disposed = false;
    return {
      resize() {
        const width = Math.max(1, canvas.clientWidth);
        const height = Math.max(1, canvas.clientHeight);
        camera.aspect = width / height;
        camera.updateProjectionMatrix();
        renderer.setSize(width, height, false);
      },
      setProgress(value) { progress = clampUnit(value); },
      setPointer(value) { pointer = value || { x: 0, y: 0 }; },
      setVisible(value) { visible = Boolean(value); },
      render(time) {
        if (!visible || disposed) return false;
        const planetPose = createPlanetPose(progress, pointer);
        const courierPose = createCourierPose(progress, time);
        world.update({ planetPose, courierPose, time });
        renderer.render(scene, camera);
        return true;
      },
      getDiagnostics() {
        return {
          drawCalls: renderer.info.render.calls,
          triangles: renderer.info.render.triangles
        };
      },
      dispose() {
        if (disposed) return;
        disposed = true;
        world.dispose();
        renderer.dispose();
      }
    };
  }
  ```

  Use one key directional light, hemisphere fill, a warm rim light, and
  restrained fog. Render only when visible and dirty or when ambient animation
  is enabled. Enforce the spec's draw-call and triangle budgets through
  diagnostics, not by silently dropping semantic content.

- [ ] **Step 7: Run the focused JUnit test and Node tests**

  ```powershell
  mvn.cmd "-Dmaven.repo.local=.m2/repository" -Dtest=HomePageTemplateContractTest#homepageBuildsAnOriginalLocalQuantPlanetWorld test
  node --test src/test/js/home-quant-planet-motion.test.mjs
  ```

  Expected: both commands pass.

- [ ] **Step 8: Commit**

  ```powershell
  git add src/main/resources/static/js/home-quant-planet-world.js src/main/resources/static/js/home-quant-planet-scene.js src/test/java/com/kingyurina/demo/web/HomePageTemplateContractTest.java
  git commit -m "feat: build the quant planet world"
  ```

---

### Task 3: Semantic Homepage, Static Fallback, and Responsive Art Direction

**Files:**

- Modify: `src/main/resources/templates/index.html`
- Create: `src/main/resources/static/css/home-quant-planet.css`
- Modify: `src/test/java/com/kingyurina/demo/web/HomePageTemplateContractTest.java`

**Interfaces:**

- Consumes: Thymeleaf `mainMenus`.
- Produces stable selectors:
  - `data-home-story`
  - `data-home-scene`
  - `data-home-canvas`
  - `data-planet-fallback`
  - `data-home-chapter="observe|resolve|act"`
  - `data-home-metric="signal|alpha|risk"`
  - `data-home-reveal`
  - `data-home-product-link`

- [ ] **Step 1: Replace Observatory markup assertions with failing Quant Planet contracts**

  Assert the exact stylesheet/body/module names, one `h1`, three chapters,
  three metrics, six product links, decorative canvas, local-only assets, and
  approved copy:

  ```java
  assertTrue(template.contains("home-quant-planet.css"));
  assertTrue(template.contains("class=\"home-page home-quant-planet\""));
  assertTrue(template.contains("data-planet-fallback"));
  assertTrue(template.contains("aria-hidden=\"true\""));
  assertEquals(3, occurrences(template, "data-home-chapter"));
  assertEquals(3, occurrences(template, "data-home-metric"));
  assertEquals(6, occurrences(template, "data-home-product-link"));
  assertEquals(1, occurrences(template, "<h1"));
  assertTrue(template.contains("Market intelligence, in motion."));
  assertTrue(template.contains("Observe the market. Resolve the signal. Act with context."));
  assertTrue(template.contains("<dt>Signal</dt><dd>79 <small>/ 100</small></dd>"));
  assertTrue(template.contains("<dt>20D Alpha</dt><dd>+2.4%</dd>"));
  assertTrue(template.contains("<dt>Risk</dt><dd>Medium</dd>"));
  assertTrue(template.contains("Illustrative model view"));
  assertFalse(template.contains("home-observatory"));
  assertFalse(template.contains("http://"));
  assertFalse(template.contains("https://"));
  ```

- [ ] **Step 2: Run the template contract test and confirm RED**

  ```powershell
  mvn.cmd "-Dmaven.repo.local=.m2/repository" -Dtest=HomePageTemplateContractTest test
  ```

  Expected: FAIL on the new planet surface assertions.

- [ ] **Step 3: Replace the homepage semantic surface**

  Keep the existing dynamic navigation and product destinations. Use this hero
  hierarchy:

  ```html
  <section class="planet-story" data-home-story aria-labelledby="home-title">
    <div class="planet-scene" data-home-scene aria-hidden="true">
      <div class="planet-fallback" data-planet-fallback>
        <span class="planet-fallback__world"></span>
        <span class="planet-fallback__route"></span>
        <span class="planet-fallback__station"></span>
        <span class="planet-fallback__courier"></span>
      </div>
      <canvas data-home-canvas></canvas>
    </div>
    <section class="planet-chapter planet-chapter--observe"
             data-home-chapter="observe">
      <p class="planet-chapter__index">01 / OBSERVE</p>
      <p class="planet-eyebrow">QUANT PLANET / FIELD ONLINE</p>
      <h1 id="home-title">Market intelligence,<br>in motion.</h1>
      <p>Observe the market. Resolve the signal. Act with context.</p>
      <a class="planet-primary-action" href="/quant">Enter Quant</a>
      <a class="planet-story-link" href="#resolve">Follow the courier</a>
    </section>
  </section>
  ```

  Add the exact Resolve and Act sections:

  ```html
  <section id="resolve" class="planet-chapter planet-chapter--resolve"
           data-home-chapter="resolve" data-home-reveal>
    <p class="planet-chapter__index">02 / RESOLVE</p>
    <h2>Three readings.<br>One market view.</h2>
    <dl class="planet-metrics" aria-label="Illustrative model view">
      <div data-home-metric="signal"><dt>Signal</dt><dd>79 <small>/ 100</small></dd></div>
      <div data-home-metric="alpha"><dt>20D Alpha</dt><dd>+2.4%</dd></div>
      <div data-home-metric="risk"><dt>Risk</dt><dd>Medium</dd></div>
    </dl>
    <p>Illustrative model view</p>
  </section>
  <section id="act" class="planet-chapter planet-chapter--act"
           data-home-chapter="act" data-home-reveal>
    <p class="planet-chapter__index">03 / ACT</p>
    <h2>Arrive at a decision.</h2>
    <ol>
      <li>Scan the market.</li>
      <li>Resolve competing signals.</li>
      <li>Open the authoritative dashboard.</li>
    </ol>
    <a class="planet-primary-action" href="/quant">Enter Quant</a>
  </section>
  ```

  Preserve the six current product routes, final CTA, footer, skip link, and
  no-script fallback verbatim except for planet-owned class names.

- [ ] **Step 4: Build the static-first CSS design system**

  Define exact semantic tokens:

  ```css
  :root {
    --planet-sky: #9ed9d5;
    --planet-haze: #d9efe8;
    --planet-ground: #b8c989;
    --planet-warm: #f1d7a4;
    --planet-coral: #e8755d;
    --planet-blue: #2357a5;
    --planet-ink: #173149;
    --planet-paper: #fff9ea;
    --planet-positive: #2f8c73;
    --planet-risk: #c85a5a;
    --layer-world: 0;
    --layer-content: 10;
    --layer-nav: 30;
    --layer-skip: 50;
  }
  ```

  CSS must render a deliberate miniature planet, route, station, and courier
  before JavaScript. Use pseudo-elements and transforms only; do not hide the
  fallback until `.planet-webgl-ready` exists.

- [ ] **Step 5: Implement responsive, focus, and reduced-motion contracts**

  Include exact breakpoint blocks at 767px, 768px, 1024px, and 1440px.
  Ensure:

  - `overflow-x: clip`;
  - every link/CTA has `min-height: 44px`;
  - visible `:focus-visible`;
  - mobile headline precedes a centered active hemisphere;
  - metric overlays stack below the world at 375px;
  - short landscape viewports retain the CTA;
  - `@media (prefers-reduced-motion: reduce)` disables smooth behavior and
    transitions;
  - no content starts hidden unless `.planet-motion-enabled` is present.

- [ ] **Step 6: Run the full template contract test and confirm GREEN**

  Run the command from Step 2.

- [ ] **Step 7: Commit**

  ```powershell
  git add src/main/resources/templates/index.html src/main/resources/static/css/home-quant-planet.css src/test/java/com/kingyurina/demo/web/HomePageTemplateContractTest.java
  git commit -m "feat: compose the quant planet homepage"
  ```

---

### Task 4: Progressive Enhancement and Runtime Migration

**Files:**

- Create: `src/main/resources/static/js/home-quant-planet.js`
- Modify: `src/main/resources/templates/index.html`
- Modify: `src/test/java/com/kingyurina/demo/web/HomePageTemplateContractTest.java`
- Delete: all homepage-owned `home-observatory*` files from the file map.

**Interfaces:**

- Consumes: all Task 1–3 selectors and module exports.
- Produces: root state classes/attributes:
  - `.planet-webgl-ready`
  - `.planet-webgl-failed`
  - `.planet-motion-enabled`
  - `data-planet-chapter="observe|resolve|act"`
  - loopback canvas diagnostics `data-render-ms`, `data-draw-calls`,
    `data-triangles`.

- [ ] **Step 1: Write failing orchestration and retirement contracts**

  Add assertions:

  ```java
  assertTrue(template.contains("/js/home-quant-planet.js"));
  assertTrue(entry.contains("./home-quant-planet-scroll.js"));
  assertTrue(entry.contains("./home-quant-planet-motion.js"));
  assertTrue(entry.contains("./home-quant-planet-scene.js"));
  assertTrue(entry.contains("createPageLifecycle"));
  assertTrue(entry.contains("IntersectionObserver"));
  assertTrue(entry.contains("visibilitychange"));
  assertTrue(entry.contains("pageshow"));
  assertTrue(entry.contains("prefers-reduced-motion: reduce"));
  assertFalse(entry.contains("setInterval"));
  assertFalse(entry.contains("fetch("));
  assertFalse(new ClassPathResource("static/css/home-observatory.css").exists());
  assertFalse(new ClassPathResource("static/js/home-observatory.js").exists());
  assertFalse(new ClassPathResource("static/js/home-observatory-motion.js").exists());
  assertFalse(new ClassPathResource("static/js/home-observatory-scroll.js").exists());
  assertFalse(new ClassPathResource("static/js/home-observatory-scene.js").exists());
  ```

- [ ] **Step 2: Run focused contract tests and confirm RED**

  ```powershell
  mvn.cmd "-Dmaven.repo.local=.m2/repository" -Dtest=HomePageTemplateContractTest test
  ```

  Expected: FAIL because the new entry module is absent and old assets remain.

- [ ] **Step 3: Implement one page coordinator**

  `home-quant-planet.js` must:

  ```js
  import {
    createDomMotion,
    createPageLifecycle,
    normalizePointer,
    normalizeScrollProgress,
    resolveLocalTestOverrides,
    resolvePlanetChapter,
    selectPlanetQualityProfile
  } from "./home-quant-planet-motion.js";
  import { createScrollController } from "./home-quant-planet-scroll.js";
  import { createQuantPlanetScene } from "./home-quant-planet-scene.js";
  ```

  Detect WebGL and reduced motion before constructing Lenis or the scene, then
  coordinate progress with these concrete handlers:

  ```js
  function updateStoryProgress() {
    if (!storyVisible) return;
    const rect = story.getBoundingClientRect();
    const progress = normalizeScrollProgress(
      rect.top, rect.height, window.innerHeight
    );
    scene?.setProgress(progress);
    const chapter = resolvePlanetChapter(progress);
    root.dataset.planetChapter = chapter;
    domMotion.setChapter(chapter);
  }

  function updateSceneVisibility() {
    scene?.setVisible(storyVisible && !document.hidden);
  }

  function handlePointer(event) {
    if (!profile.pointerEnabled) return;
    scene?.setPointer(normalizePointer(
      event.clientX,
      event.clientY,
      sceneElement.getBoundingClientRect()
    ));
  }

  function destroy() {
    storyObserver?.disconnect();
    revealObserver?.disconnect();
    scroll.destroy();
    domMotion.destroy();
    scene?.dispose();
    document.removeEventListener("visibilitychange", handleVisibilityChange);
    window.removeEventListener("resize", handleResize);
    window.removeEventListener("pagehide", lifecycle.handlePageHide);
    window.removeEventListener("pageshow", lifecycle.handlePageShow);
    sceneElement.removeEventListener("pointermove", handlePointer);
  }
  ```

  Use `createPageLifecycle` as the only animation-loop owner and use one
  `IntersectionObserver` for story visibility plus one for HTML reveals.

- [ ] **Step 4: Expose bounded loopback diagnostics**

  Only on `localhost`, `127.0.0.1`, or `::1`, keep a rolling 60-frame average:

  ```js
  canvas.dataset.renderMs = average.toFixed(2);
  canvas.dataset.drawCalls = String(scene.getDiagnostics().drawCalls);
  canvas.dataset.triangles = String(scene.getDiagnostics().triangles);
  ```

  Local query overrides may force static and reduced modes. Production hosts
  must ignore these query parameters.

- [ ] **Step 5: Switch template references and remove obsolete assets**

  Update the CSS and module asset versions, then delete only the files listed
  under Task 4. Confirm `home-nature-3d.js`, vendor assets, fonts, unrelated
  docs, and all user files remain untouched.

- [ ] **Step 6: Run Node and Java tests**

  ```powershell
  node --test src/test/js/home-quant-planet-motion.test.mjs
  mvn.cmd "-Dmaven.repo.local=.m2/repository" test
  ```

  Expected: zero failures.

- [ ] **Step 7: Commit**

  ```powershell
  git add src/main/resources/templates/index.html src/main/resources/static/js/home-quant-planet.js src/test/java/com/kingyurina/demo/web/HomePageTemplateContractTest.java
  git add -u src/main/resources/static/css src/main/resources/static/js src/test/js
  git commit -m "feat: launch the quant planet experience"
  ```

---

### Task 5: Browser QA, Performance, and Regression Closure

**Files:**

- Modify only when a failing automated or browser check requires:
  - `src/main/resources/templates/index.html`
  - `src/main/resources/static/css/home-quant-planet.css`
  - `src/main/resources/static/js/home-quant-planet.js`
  - `src/main/resources/static/js/home-quant-planet-motion.js`
  - `src/main/resources/static/js/home-quant-planet-scroll.js`
  - `src/main/resources/static/js/home-quant-planet-world.js`
  - `src/main/resources/static/js/home-quant-planet-scene.js`
  - `src/test/js/home-quant-planet-motion.test.mjs`
  - `src/test/java/com/kingyurina/demo/web/HomePageTemplateContractTest.java`

**Interfaces:**

- Consumes: the complete local homepage.
- Produces: browser evidence for all acceptance criteria and a regression test
  for every discovered defect.

- [ ] **Step 1: Build and start an isolated local server**

  ```powershell
  mvn.cmd "-Dmaven.repo.local=.m2/repository" -DskipTests package
  ```

  Start the worktree artifact on port `8081` with startup Signal refresh
  disabled and the existing MariaDB profile. Do not stop or replace the
  user's port-8080 process.

- [ ] **Step 2: Verify the 1440px story**

  At `1440×900`, inspect progress `0`, `.33`, `.69`, and `1`:

  - Observe Station, Resolve Works, and Decision Gate read as distinct places;
  - the courier position and walk/idle state match the chapter;
  - HTML metrics align with their corresponding 3D instruments;
  - Enter Quant is visible in the initial viewport;
  - no object resembles an unfinished placeholder.

- [ ] **Step 3: Verify responsive layouts**

  At `1024×768`, `768×1024`, and `375×812`:

  - headline and planet do not obscure each other;
  - metric overlays do not cover the active landmark;
  - product links form one readable column at 375px;
  - every interactive target has at least a 44px hit area;
  - navigation remains reachable;
  - `document.documentElement.scrollWidth <= window.innerWidth`.

- [ ] **Step 4: Verify keyboard and fallbacks**

  - Tab through skip link, navigation, hero CTA, story link, product links,
    final CTA, and footer with visible focus.
  - Open `/?motion=reduce`; confirm no Lenis construction, camera journey,
    stagger, courier walking, or ambient loop while all content stays visible.
  - Open `/?render=static`; confirm the CSS planet is visible without a blank
    canvas or unresolved loader.
  - Disable JavaScript; confirm the headline, chapters, values, product routes,
    CTA, footer, and static planet remain usable.

- [ ] **Step 5: Verify performance and runtime hygiene**

  After at least 60 active frames on the high profile, assert:

  ```js
  Number(canvas.dataset.renderMs) <= 12
  Number(canvas.dataset.drawCalls) <= 75
  Number(canvas.dataset.triangles) <= 140000
  ```

  Confirm:

  - no console errors;
  - rendering pauses outside the story and when the page is hidden;
  - one animation loop resumes after BFCache restore;
  - no remote runtime request originates from homepage-owned code.

- [ ] **Step 6: Add a regression test before each browser-discovered fix**

  Pure state defects receive a failing Node assertion. Markup, routing,
  accessibility, asset, or CSS contract defects receive a failing JUnit
  assertion. Apply the smallest production fix and rerun the covering test.

- [ ] **Step 7: Run final verification**

  ```powershell
  node --test src/test/js/home-quant-planet-motion.test.mjs
  mvn.cmd "-Dmaven.repo.local=.m2/repository" test
  rg -n "home-observatory|http://|https://|fetch\\(" src/main/resources/templates/index.html src/main/resources/static/css/home-quant-planet.css src/main/resources/static/js/home-quant-planet*.js
  git diff --check
  git status --short
  ```

  Expected:

  - Node and Maven tests report zero failures.
  - old observatory references, remote URLs, and network transports have no
    matches in homepage-owned production files.
  - no whitespace errors exist.
  - only intentional Quant Planet files differ in the isolated worktree.

- [ ] **Step 8: Commit verified fixes when changes exist**

  ```powershell
  git add src/main/resources/templates/index.html src/main/resources/static/css/home-quant-planet.css src/main/resources/static/js/home-quant-planet*.js src/test/js/home-quant-planet-motion.test.mjs src/test/java/com/kingyurina/demo/web/HomePageTemplateContractTest.java
  git commit -m "fix: polish the quant planet experience"
  ```

  If no browser defect required a change, do not create an empty commit.
