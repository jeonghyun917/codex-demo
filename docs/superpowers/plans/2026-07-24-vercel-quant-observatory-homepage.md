# Vercel-Inspired Quant Observatory Homepage Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the Brik homepage with a premium monochrome Quant Observatory experience built around one physically convincing Quant Core, restrained Vercel-inspired composition, and an accessible Observe → Resolve → Act scroll narrative.

**Architecture:** Keep `HomeController#index` and its DB-isolated menu contract unchanged. Replace only homepage-owned Thymeleaf, CSS, and JavaScript assets; use locally vendored Lenis and Motion for scroll/DOM orchestration, the existing Three.js r165 module for one focused WebGL scene, and semantic HTML/CSS fallbacks for every enhanced state.

**Tech Stack:** Java 21, Spring Boot 4.0.6, Thymeleaf, vanilla ES modules, Lenis 1.3.25, Motion 12.29.2, Three.js r165, Instrument Sans 5.3.0, IBM Plex Mono 5.3.0, JUnit 5, Node test runner.

## Global Constraints

- Use the approved spec at `docs/superpowers/specs/2026-07-24-vercel-quant-observatory-homepage-design.md`.
- At execution time, invoke `superpowers:using-git-worktrees` and create an isolated `codex/vercel-quant-observatory` worktree from `main`; do not edit the dirty primary checkout.
- Preserve all unrelated tracked and untracked user files.
- Keep `HomeController#index` unchanged: call only `menuService.mainMenus()`, expose `mainMenus`, and return `index`.
- Redesign only `/`; do not change Quant or any other subpage.
- Do not introduce React, a package manifest, a frontend build step, external runtime requests, or an external market API.
- Self-host Lenis 1.3.25, Motion 12.29.2, Three.js r165, Instrument Sans 5.3.0, and IBM Plex Mono 5.3.0 with their licenses.
- Use the exact core copy `Market intelligence, resolved.` and the exact narrative `Observe the market. Resolve the signal. Act with context.`
- Show `Signal 79/100`, `20D Alpha +2.4%`, and `Risk Medium` only beside `Illustrative model view`.
- Use `Enter Quant` as the only primary action label; repeated instances must all resolve to `/quant`.
- The Quant Core is the only dominant 3D object. Do not add a full room, wire cage, loose ribbons, broad particle field, audio, video, custom cursor, or copied Vercel triangle.
- Cap WebGL pixel ratio at `1.5`; pause scene rendering when hidden or outside the scene range.
- Keep all content and routes usable with JavaScript disabled, WebGL unavailable, or `prefers-reduced-motion: reduce`.
- Validate 375px, 768px, 1024px, and 1440px layouts with no body-level horizontal overflow.
- Every interactive target is at least 44×44px, keyboard reachable, and has visible `:focus-visible`.
- Use `mvn.cmd "-Dmaven.repo.local=.m2/repository" test` for the full Java verification.

## File Map

### Create

- `src/main/resources/static/vendor/lenis/lenis.min.js` — pinned browser bundle.
- `src/main/resources/static/vendor/lenis/lenis.css` — official Lenis scroll-state CSS.
- `src/main/resources/static/vendor/lenis/LICENSE` — Lenis MIT license.
- `src/main/resources/static/vendor/motion/motion.js` — pinned browser bundle exposing `window.Motion`.
- `src/main/resources/static/vendor/motion/LICENSE.md` — Motion MIT license.
- `src/main/resources/static/fonts/instrument-sans-latin-wght-normal.woff2` — display/UI font.
- `src/main/resources/static/fonts/ibm-plex-mono-latin-400-normal.woff2` — data font regular.
- `src/main/resources/static/fonts/ibm-plex-mono-latin-500-normal.woff2` — data font medium.
- `src/main/resources/static/fonts/Instrument-Sans-LICENSE` — Instrument Sans license.
- `src/main/resources/static/fonts/IBM-Plex-Mono-LICENSE` — IBM Plex Mono license.
- `src/main/resources/static/css/home-observatory.css` — homepage tokens, layout, fallbacks, and responsive behavior.
- `src/main/resources/static/js/home-observatory-scroll.js` — Lenis lifecycle and normalized scroll state.
- `src/main/resources/static/js/home-observatory-motion.js` — pure motion helpers and DOM animation adapter.
- `src/main/resources/static/js/home-observatory-scene.js` — Three.js Quant Core scene.
- `src/main/resources/static/js/home-observatory.js` — progressive-enhancement coordinator.
- `src/test/js/home-observatory-motion.test.mjs` — pure JavaScript behavior tests.

### Modify

- `src/main/resources/templates/index.html` — semantic homepage and local asset references.
- `src/test/java/com/kingyurina/demo/web/HomePageTemplateContractTest.java` — homepage structure, accessibility, vendor, and retirement contracts.

### Delete after replacement is verified

- `src/main/resources/static/css/home-brik.css`
- `src/main/resources/static/js/home-brik.js`
- `src/main/resources/static/js/home-brik-motion.js`
- `src/test/js/home-brik-motion.test.mjs`

### Preserve

- `src/main/java/com/kingyurina/demo/web/HomeController.java`
- `src/test/java/com/kingyurina/demo/web/HomeControllerIndexTest.java`
- `src/main/resources/static/js/vendor/three.module.js`
- `src/main/resources/static/js/vendor/three.LICENSE`
- `src/main/resources/static/js/home-nature-3d.js` because it is not owned by the current homepage.

---

### Task 1: Pin Local Runtime and Font Assets

**Files:**

- Modify: `src/test/java/com/kingyurina/demo/web/HomePageTemplateContractTest.java`
- Create: all files under `static/vendor/lenis`, `static/vendor/motion`, and `static/fonts` listed in the file map.

**Interfaces:**

- Consumes: npm registry packages only during development-time installation.
- Produces:
  - `window.Lenis` from `/vendor/lenis/lenis.min.js`
  - `window.Motion` from `/vendor/motion/motion.js`
  - local CSS at `/vendor/lenis/lenis.css`
  - local `@font-face` source files with no runtime network request

- [ ] **Step 1: Write the failing local-asset contract test**

  Add this method to `HomePageTemplateContractTest`:

  ```java
  @Test
  void homepageRuntimeDependenciesAndFontsArePinnedLocally() throws IOException {
      assertTrue(new ClassPathResource("static/vendor/lenis/lenis.min.js").exists());
      assertTrue(new ClassPathResource("static/vendor/lenis/lenis.css").exists());
      assertTrue(new ClassPathResource("static/vendor/lenis/LICENSE").exists());
      assertTrue(new ClassPathResource("static/vendor/motion/motion.js").exists());
      assertTrue(new ClassPathResource("static/vendor/motion/LICENSE.md").exists());
      assertTrue(new ClassPathResource("static/js/vendor/three.module.js").exists());
      assertTrue(new ClassPathResource("static/js/vendor/three.LICENSE").exists());
      assertTrue(new ClassPathResource("static/fonts/instrument-sans-latin-wght-normal.woff2").exists());
      assertTrue(new ClassPathResource("static/fonts/ibm-plex-mono-latin-400-normal.woff2").exists());
      assertTrue(new ClassPathResource("static/fonts/ibm-plex-mono-latin-500-normal.woff2").exists());
      assertTrue(new ClassPathResource("static/fonts/Instrument-Sans-LICENSE").exists());
      assertTrue(new ClassPathResource("static/fonts/IBM-Plex-Mono-LICENSE").exists());

      assertTrue(resource("static/vendor/lenis/lenis.min.js").contains("1.3.25"));
      assertTrue(resource("static/vendor/motion/LICENSE.md").contains("MIT License"));
      assertTrue(resource("static/js/vendor/three.module.js").contains("const REVISION = '165'"));
  }
  ```

- [ ] **Step 2: Run the contract test and confirm RED**

  ```powershell
  mvn.cmd "-Dmaven.repo.local=.m2/repository" -Dtest=HomePageTemplateContractTest#homepageRuntimeDependenciesAndFontsArePinnedLocally test
  ```

  Expected: FAIL because Lenis, Motion, and font assets do not exist.

- [ ] **Step 3: Download exact package archives into ignored build output**

  ```powershell
  New-Item -ItemType Directory -Force target/vendor-downloads | Out-Null
  npm.cmd pack lenis@1.3.25 --pack-destination target/vendor-downloads
  npm.cmd pack motion@12.29.2 --pack-destination target/vendor-downloads
  npm.cmd pack '@fontsource-variable/instrument-sans@5.3.0' --pack-destination target/vendor-downloads
  npm.cmd pack '@fontsource/ibm-plex-mono@5.3.0' --pack-destination target/vendor-downloads
  ```

  Expected archive names:

  ```text
  lenis-1.3.25.tgz
  motion-12.29.2.tgz
  fontsource-variable-instrument-sans-5.3.0.tgz
  fontsource-ibm-plex-mono-5.3.0.tgz
  ```

- [ ] **Step 4: Verify the published Lenis and Motion archives before extraction**

  ```powershell
  (Get-FileHash target/vendor-downloads/lenis-1.3.25.tgz -Algorithm SHA1).Hash.ToLower()
  (Get-FileHash target/vendor-downloads/motion-12.29.2.tgz -Algorithm SHA1).Hash.ToLower()
  ```

  Expected:

  ```text
  9336d9a754d8d9c454b86e9576328f5448321bd7
  e820aaf7fb7424bb69cf45c7b641a777a96b2be0
  ```

- [ ] **Step 5: Extract and copy only required distributable files**

  ```powershell
  $root = Resolve-Path target/vendor-downloads
  $packages = @("lenis", "motion", "instrument", "plex")
  foreach ($name in $packages) {
      New-Item -ItemType Directory -Force "$root/$name" | Out-Null
  }
  tar -xzf "$root/lenis-1.3.25.tgz" -C "$root/lenis"
  tar -xzf "$root/motion-12.29.2.tgz" -C "$root/motion"
  tar -xzf "$root/fontsource-variable-instrument-sans-5.3.0.tgz" -C "$root/instrument"
  tar -xzf "$root/fontsource-ibm-plex-mono-5.3.0.tgz" -C "$root/plex"

  New-Item -ItemType Directory -Force src/main/resources/static/vendor/lenis | Out-Null
  New-Item -ItemType Directory -Force src/main/resources/static/vendor/motion | Out-Null
  New-Item -ItemType Directory -Force src/main/resources/static/fonts | Out-Null

  Copy-Item "$root/lenis/package/dist/lenis.min.js" src/main/resources/static/vendor/lenis/lenis.min.js
  Copy-Item "$root/lenis/package/dist/lenis.css" src/main/resources/static/vendor/lenis/lenis.css
  Copy-Item "$root/lenis/package/LICENSE" src/main/resources/static/vendor/lenis/LICENSE
  Copy-Item "$root/motion/package/dist/motion.js" src/main/resources/static/vendor/motion/motion.js
  Copy-Item "$root/motion/package/LICENSE.md" src/main/resources/static/vendor/motion/LICENSE.md
  Copy-Item "$root/instrument/package/files/instrument-sans-latin-wght-normal.woff2" src/main/resources/static/fonts/instrument-sans-latin-wght-normal.woff2
  Copy-Item "$root/instrument/package/LICENSE" src/main/resources/static/fonts/Instrument-Sans-LICENSE
  Copy-Item "$root/plex/package/files/ibm-plex-mono-latin-400-normal.woff2" src/main/resources/static/fonts/ibm-plex-mono-latin-400-normal.woff2
  Copy-Item "$root/plex/package/files/ibm-plex-mono-latin-500-normal.woff2" src/main/resources/static/fonts/ibm-plex-mono-latin-500-normal.woff2
  Copy-Item "$root/plex/package/LICENSE" src/main/resources/static/fonts/IBM-Plex-Mono-LICENSE
  ```

- [ ] **Step 6: Run the contract test and confirm GREEN**

  Run the command from Step 2.

  Expected: the pinned-local-assets test passes.

- [ ] **Step 7: Commit**

  ```powershell
  git add src/test/java/com/kingyurina/demo/web/HomePageTemplateContractTest.java src/main/resources/static/vendor/lenis src/main/resources/static/vendor/motion src/main/resources/static/fonts
  git commit -m "build: vendor homepage motion dependencies"
  ```

### Task 2: Build the Semantic Observatory Surface

**Files:**

- Modify: `src/test/java/com/kingyurina/demo/web/HomePageTemplateContractTest.java`
- Modify: `src/main/resources/templates/index.html`
- Create: `src/main/resources/static/css/home-observatory.css`

**Interfaces:**

- Consumes: Thymeleaf `mainMenus`, whose items expose `label` and `href`.
- Produces stable selectors:
  - `data-home-nav`
  - `data-home-story`
  - `data-home-scene`
  - `data-home-canvas`
  - `data-home-core-fallback`
  - `data-home-chapter`
  - `data-home-metric`
  - `data-home-product-link`
  - `data-home-reveal`

- [ ] **Step 1: Replace Brik-only assertions with failing Observatory contracts**

  Keep reusable helpers in `HomePageTemplateContractTest`, and assert:

  ```java
  String template = resource("templates/index.html");
  String css = resource("static/css/home-observatory.css");

  assertTrue(template.contains("home-observatory.css"));
  assertTrue(template.contains("class=\"home-page home-observatory\""));
  assertTrue(template.contains("th:each=\"menu : ${mainMenus}\""));
  assertTrue(template.contains("id=\"main-content\""));
  assertTrue(template.contains("data-home-story"));
  assertTrue(template.contains("data-home-scene"));
  assertTrue(template.contains("data-home-canvas"));
  assertTrue(template.contains("data-home-core-fallback"));
  assertEquals(3, occurrences(template, "data-home-chapter"));
  assertEquals(3, occurrences(template, "data-home-metric"));
  assertEquals(6, occurrences(template, "data-home-product-link"));
  assertEquals(1, occurrences(template, "<h1"));
  assertTrue(template.contains("Market intelligence, resolved."));
  assertTrue(template.contains("Observe the market. Resolve the signal. Act with context."));
  assertTrue(template.contains("Signal"));
  assertTrue(template.contains("79 <small>/ 100</small>"));
  assertTrue(template.contains("20D Alpha"));
  assertTrue(template.contains("+2.4%"));
  assertTrue(template.contains("Risk"));
  assertTrue(template.contains("Medium"));
  assertTrue(template.contains("Illustrative model view"));
  assertTrue(template.contains("href=\"/quant\""));
  assertTrue(template.contains("href=\"/stocks\""));
  assertTrue(template.contains("href=\"/stocks/heatmap?index=SP500\""));
  assertTrue(template.contains("href=\"/etfs\""));
  assertTrue(template.contains("href=\"/atelier\""));
  assertTrue(template.contains("href=\"/signals/backtest\""));
  assertFalse(template.contains("http://"));
  assertFalse(template.contains("https://"));
  ```

  Add CSS contracts for the exact semantic tokens, four breakpoints, 44px targets, visible focus, no body horizontal overflow, and reduced motion:

  ```java
  assertTrue(css.contains("--home-bg-deep: #030405"));
  assertTrue(css.contains("--home-fg-primary: #f5f7fa"));
  assertTrue(css.contains("--home-signal: #6ea8ff"));
  assertTrue(css.contains("@media (max-width: 767px)"));
  assertTrue(css.contains("@media (min-width: 768px)"));
  assertTrue(css.contains("@media (min-width: 1024px)"));
  assertTrue(css.contains("@media (min-width: 1440px)"));
  assertTrue(css.contains("@media (prefers-reduced-motion: reduce)"));
  assertTrue(css.contains("min-height: 44px"));
  assertTrue(css.contains(":focus-visible"));
  assertTrue(css.contains("overflow-x: clip"));
  ```

- [ ] **Step 2: Run the template contract test and confirm RED**

  ```powershell
  mvn.cmd "-Dmaven.repo.local=.m2/repository" -Dtest=HomePageTemplateContractTest test
  ```

  Expected: FAIL because the approved template and stylesheet do not exist.

- [ ] **Step 3: Replace `index.html` with the approved semantic hierarchy**

  Use this structure and exact visible copy:

  ```html
  <body class="home-page home-observatory">
    <a class="home-skip" href="#main-content">Skip to content</a>
    <header class="home-nav" data-home-nav>
      <a class="home-brand" href="/" aria-label="King Yurina home">
        <svg viewBox="0 0 32 32" aria-hidden="true">
          <circle cx="16" cy="16" r="11"></circle>
          <circle cx="16" cy="16" r="4"></circle>
          <path d="M16 5v6M16 21v6M5 16h6M21 16h6"></path>
        </svg>
        <span>King Yurina</span>
      </a>
      <nav aria-label="Primary navigation">
        <a th:each="menu : ${mainMenus}"
           th:href="${menu.href}"
           th:text="${menu.label}"
           href="/quant">Quant</a>
      </nav>
      <a class="home-primary-action" th:href="@{/quant}" href="/quant">Enter Quant</a>
    </header>

    <main id="main-content">
      <section class="home-story" data-home-story aria-labelledby="home-title">
        <div class="home-scene" data-home-scene aria-hidden="true">
          <div class="home-core-fallback" data-home-core-fallback>
            <span class="home-core-ring home-core-ring--outer"></span>
            <span class="home-core-ring home-core-ring--middle"></span>
            <span class="home-core-ring home-core-ring--inner"></span>
            <span class="home-core-iris"></span>
            <span class="home-core-lens"></span>
            <span class="home-core-emitter"></span>
          </div>
          <canvas data-home-canvas></canvas>
        </div>
        <section class="home-chapter home-chapter--observe" data-home-chapter="observe">
          <p class="home-eyebrow">QUANT INTELLIGENCE / SYSTEM ONLINE</p>
          <h1 id="home-title">Market intelligence,<br>resolved.</h1>
          <p>Observe the market. Resolve the signal. Act with context.</p>
          <a class="home-primary-action" href="/quant">Enter Quant</a>
          <a class="home-story-link" href="#resolve">Explore the system</a>
        </section>
        <section id="resolve" class="home-chapter home-chapter--resolve" data-home-chapter="resolve">
          <p class="home-chapter-index">02 / RESOLVE</p>
          <h2>Three readings.<br>One market view.</h2>
          <dl class="home-metrics" aria-label="Illustrative model view">
            <div data-home-metric="signal"><dt>Signal</dt><dd>79 <small>/ 100</small></dd></div>
            <div data-home-metric="alpha"><dt>20D Alpha</dt><dd>+2.4%</dd></div>
            <div data-home-metric="risk"><dt>Risk</dt><dd>Medium</dd></div>
          </dl>
          <p>Illustrative model view</p>
        </section>
        <section id="act" class="home-chapter home-chapter--act" data-home-chapter="act">
          <p class="home-chapter-index">03 / ACT</p>
          <h2>Turn market noise<br>into a decision.</h2>
          <ol><li>Scan the market.</li><li>Resolve competing signals.</li><li>Open the authoritative dashboard.</li></ol>
          <a class="home-primary-action" href="/quant">Enter Quant</a>
        </section>
      </section>
      <section class="home-products" aria-labelledby="products-title">
        <div class="home-section-heading">
          <p class="home-eyebrow">PRODUCT SURFACES</p>
          <h2 id="products-title">Six ways into the market.</h2>
        </div>
        <div class="home-product-list">
          <a data-home-product-link href="/quant"><span>01</span><h3>Quant Intelligence</h3><p>Resolve signal, alpha, and risk.</p></a>
          <a data-home-product-link href="/stocks"><span>02</span><h3>Stocks</h3><p>Inspect one symbol in market context.</p></a>
          <a data-home-product-link href="/stocks/heatmap?index=SP500"><span>03</span><h3>Market Heatmap</h3><p>See leadership, pressure, and rotation.</p></a>
          <a data-home-product-link href="/etfs"><span>04</span><h3>ETF Radar</h3><p>Compare diversified market exposures.</p></a>
          <a data-home-product-link href="/atelier"><span>05</span><h3>Strategy Atelier</h3><p>Shape a thesis into a testable workflow.</p></a>
          <a data-home-product-link href="/signals/backtest"><span>06</span><h3>Signal Backtest</h3><p>Measure signal behavior through time.</p></a>
        </div>
      </section>
      <section class="home-final-cta" aria-labelledby="final-title">
        <p class="home-eyebrow">DECISION READY</p>
        <h2 id="final-title">Turn market noise into a decision.</h2>
        <a class="home-primary-action" href="/quant">Enter Quant</a>
      </section>
    </main>
    <footer id="contact" class="home-footer">
      <a href="/" aria-label="King Yurina home">King Yurina</a>
      <p>Quant intelligence for decision-ready market views.</p>
      <a href="#main-content">Back to top</a>
    </footer>
    <noscript><p class="home-noscript">The complete Quant experience remains available without motion.</p></noscript>
  </body>
  ```

  Render the six approved product destinations as editorial rows with `data-home-product-link`. Use inline SVG arrows with `aria-hidden="true"`; do not use copied brand media.

- [ ] **Step 4: Implement `home-observatory.css` as the static-first design system**

  Start with these exact font and token declarations:

  ```css
  @font-face {
      font-family: "Instrument Sans";
      src: url("/fonts/instrument-sans-latin-wght-normal.woff2") format("woff2-variations");
      font-weight: 400 700;
      font-style: normal;
      font-display: swap;
  }

  @font-face {
      font-family: "IBM Plex Mono";
      src: url("/fonts/ibm-plex-mono-latin-400-normal.woff2") format("woff2");
      font-weight: 400;
      font-style: normal;
      font-display: swap;
  }

  @font-face {
      font-family: "IBM Plex Mono";
      src: url("/fonts/ibm-plex-mono-latin-500-normal.woff2") format("woff2");
      font-weight: 500;
      font-style: normal;
      font-display: swap;
  }

  :root {
      --home-bg-deep: #030405;
      --home-bg-elevated: #090b0e;
      --home-fg-primary: #f5f7fa;
      --home-fg-muted: #969da8;
      --home-hairline: rgba(255, 255, 255, .12);
      --home-metal: #9298a0;
      --home-signal: #6ea8ff;
      --home-positive: #86e3b0;
      --home-negative: #ff8a8a;
      --home-font-display: "Instrument Sans", Arial, Helvetica, sans-serif;
      --home-font-data: "IBM Plex Mono", Consolas, monospace;
      --home-ease-out: cubic-bezier(.16, 1, .3, 1);
      --home-nav-z: 30;
      --home-scene-z: 0;
      --home-content-z: 2;
  }
  ```

  Implement:

  - near-black page and fixed hairline navigation;
  - a `300svh` story with a sticky `100svh` scene;
  - three content chapters distributed across the story height;
  - hero type at weight 400 and `font-size: clamp(3.6rem, 7vw, 7.75rem)`;
  - a CSS aperture fallback made from concentric metal rings, iris blades, radial highlights, and one cobalt emissive core;
  - asymmetric desktop composition and readable mobile stacking;
  - editorial product rows with no colorful card mosaic;
  - opacity/transform-ready reveal states only after `.home-motion-enabled` is set;
  - `.home-webgl-ready` and `.home-webgl-failed` visibility states that never produce a blank hero;
  - no-JavaScript, reduced-motion, focus, hover, active, and high-contrast states.

- [ ] **Step 5: Run the contract test and confirm GREEN**

  Run the command from Step 2.

  Expected: all current homepage template contracts pass.

- [ ] **Step 6: Commit**

  ```powershell
  git add src/test/java/com/kingyurina/demo/web/HomePageTemplateContractTest.java src/main/resources/templates/index.html src/main/resources/static/css/home-observatory.css
  git commit -m "feat: build quant observatory homepage surface"
  ```

### Task 3: Implement Tested Scroll and DOM Motion Primitives

**Files:**

- Create: `src/test/js/home-observatory-motion.test.mjs`
- Create: `src/main/resources/static/js/home-observatory-scroll.js`
- Create: `src/main/resources/static/js/home-observatory-motion.js`

**Interfaces:**

- `clampUnit(value: number): number`
- `normalizePointer(clientX: number, clientY: number, rect: DOMRectLike): {x:number,y:number}`
- `normalizeScrollProgress(top: number, height: number, viewportHeight: number): number`
- `resolveLocalTestOverrides({hostname, search}): {forceStatic:boolean,forceReduced:boolean}`
- `selectHomeQualityProfile(environment): {name,pixelRatio,pointerEnabled,webgl,shadows,segments}`
- `createScenePose(progress: number, pointer: {x:number,y:number}): ScenePose`
- `shouldRenderFrame(state): boolean`
- `createScrollController({LenisClass, reduceMotion, onScroll}): ScrollController`
- `createDomMotion({MotionAPI, root, reduceMotion}): DomMotionController`

- [ ] **Step 1: Write failing pure-behavior tests**

  Create `home-observatory-motion.test.mjs` with these contracts:

  ```javascript
  import assert from "node:assert/strict";
  import test from "node:test";

  import {
      clampUnit,
      createScenePose,
      normalizePointer,
      normalizeScrollProgress,
      resolveLocalTestOverrides,
      selectHomeQualityProfile,
      shouldRenderFrame
  } from "../../main/resources/static/js/home-observatory-motion.js";
  import { createScrollController } from "../../main/resources/static/js/home-observatory-scroll.js";

  test("normalizes pointer and story progress into finite unit ranges", () => {
      assert.deepEqual(
          normalizePointer(50, 25, { left: 0, top: 0, width: 100, height: 100 }),
          { x: 0, y: -0.5 }
      );
      assert.equal(normalizeScrollProgress(0, 3000, 1000), 0);
      assert.equal(normalizeScrollProgress(-1000, 3000, 1000), 0.5);
      assert.equal(normalizeScrollProgress(-2000, 3000, 1000), 1);
      assert.equal(clampUnit(Number.NaN), 0);
  });

  test("selects accessible bounded quality profiles", () => {
      assert.deepEqual(selectHomeQualityProfile({
          reduceMotion: true, mobile: false, cores: 16, dpr: 3, webgl: true
      }), {
          name: "static", pixelRatio: 1, pointerEnabled: false,
          webgl: false, shadows: false, segments: 0
      });
      assert.equal(selectHomeQualityProfile({
          reduceMotion: false, mobile: true, cores: 8, dpr: 3, webgl: true
      }).name, "low");
      assert.deepEqual(selectHomeQualityProfile({
          reduceMotion: false, mobile: false, cores: 16, dpr: 3, webgl: true
      }), {
          name: "high", pixelRatio: 1.5, pointerEnabled: true,
          webgl: true, shadows: true, segments: 96
      });
  });

  test("accepts deterministic fallback overrides only on loopback", () => {
      assert.deepEqual(resolveLocalTestOverrides({
          hostname: "127.0.0.1", search: "?render=static&motion=reduce"
      }), { forceStatic: true, forceReduced: true });
      assert.deepEqual(resolveLocalTestOverrides({
          hostname: "kingyurina.example", search: "?render=static&motion=reduce"
      }), { forceStatic: false, forceReduced: false });
  });

  test("maps scroll into the three approved mechanical states", () => {
      assert.deepEqual(createScenePose(0, { x: 0, y: 0 }), {
          cameraZ: 8.8, cameraY: 0.3, housingOpen: 0,
          irisRotation: 0, metricOpacity: 0, productOpacity: 0
      });
      assert.equal(createScenePose(0.52, { x: 0, y: 0 }).housingOpen, 1);
      assert.equal(createScenePose(0.52, { x: 0, y: 0 }).metricOpacity, 1);
      assert.equal(createScenePose(1, { x: 0, y: 0 }).productOpacity, 1);
  });

  test("renders only when the scene can contribute a visible frame", () => {
      assert.equal(shouldRenderFrame({
          visible: true, hidden: false, webgl: true, reducedMotion: false, dirty: true
      }), true);
      assert.equal(shouldRenderFrame({
          visible: false, hidden: false, webgl: true, reducedMotion: false, dirty: true
      }), false);
      assert.equal(shouldRenderFrame({
          visible: true, hidden: false, webgl: true, reducedMotion: true, dirty: false
      }), false);
  });

  test("does not construct Lenis when reduced motion is active", () => {
      let constructed = 0;
      class FakeLenis {
          constructor() { constructed += 1; }
      }
      const controller = createScrollController({
          LenisClass: FakeLenis,
          reduceMotion: true,
          onScroll() {}
      });
      assert.equal(controller.isEnabled, false);
      assert.equal(constructed, 0);
  });
  ```

- [ ] **Step 2: Run Node tests and confirm RED**

  ```powershell
  node --test src/test/js/home-observatory-motion.test.mjs
  ```

  Expected: FAIL because both modules are missing.

- [ ] **Step 3: Implement deterministic motion helpers**

  In `home-observatory-motion.js`, implement the exact exports from the Interfaces block. Use these piecewise ranges:

  ```javascript
  const resolveRange = (value, start, end) =>
      clampUnit((value - start) / Math.max(end - start, Number.EPSILON));

  export function createScenePose(progress, pointer = { x: 0, y: 0 }) {
      const p = clampUnit(progress);
      const resolve = resolveRange(p, 0.18, 0.52);
      const act = resolveRange(p, 0.68, 1);
      return {
          cameraZ: 8.8 - p * 2.4,
          cameraY: 0.3 - p * 0.22 + Math.max(-1, Math.min(1, pointer.y || 0)) * 0.08,
          housingOpen: resolve,
          irisRotation: resolve * Math.PI * 0.42,
          metricOpacity: clampUnit(resolve * (1 - act)),
          productOpacity: act
      };
  }
  ```

  `selectHomeQualityProfile` must choose:

  ```text
  static: reduced motion or no WebGL
  low: mobile or cores <= 4; DPR 1; shadows false; segments 48
  high: other capable devices; DPR min(device DPR, 1.5); shadows true; segments 96
  ```

  `shouldRenderFrame` returns:

  ```javascript
  return Boolean(state?.visible && !state.hidden && state.webgl && state.dirty);
  ```

  A reduced-motion environment selects the static profile with `webgl: false`, so it never enters the continuous WebGL loop.

  `resolveLocalTestOverrides` must ignore all query parameters unless `hostname` is exactly `127.0.0.1`, `localhost`, or `::1`. On loopback only, `render=static` sets `forceStatic` and `motion=reduce` sets `forceReduced`.

  Implement `createDomMotion` so it:

  - returns a no-op controller when reduced motion is active;
  - uses `MotionAPI.animate` only for opacity and transform;
  - exposes `reveal(element)`, `setChapter(name)`, and `destroy()`;
  - leaves content visible when Motion is missing.

- [ ] **Step 4: Implement the Lenis lifecycle adapter**

  `createScrollController` must use:

  ```javascript
  const lenis = new LenisClass({
      autoRaf: false,
      anchors: true,
      lerp: 0.085,
      smoothWheel: true,
      syncTouch: false,
      wheelMultiplier: 0.9
  });
  ```

  Return:

  ```javascript
  {
      isEnabled: true,
      raf(time) { lenis.raf(time); },
      stop() { lenis.stop(); },
      start() { lenis.start(); },
      destroy() { lenis.destroy(); }
  }
  ```

  Register one `scroll` callback that forwards Lenis scroll state to `onScroll`. In reduced motion or when `LenisClass` is absent, return a no-op controller with `isEnabled: false`.

- [ ] **Step 5: Run Node tests and confirm GREEN**

  Run the Step 2 command.

  Expected: all motion and scroll tests pass.

- [ ] **Step 6: Commit**

  ```powershell
  git add src/test/js/home-observatory-motion.test.mjs src/main/resources/static/js/home-observatory-scroll.js src/main/resources/static/js/home-observatory-motion.js
  git commit -m "feat: add observatory scroll and motion primitives"
  ```

### Task 4: Build the Physical Quant Core Scene

**Files:**

- Create: `src/main/resources/static/js/home-observatory-scene.js`
- Modify: `src/test/java/com/kingyurina/demo/web/HomePageTemplateContractTest.java`
- Modify: `src/test/js/home-observatory-motion.test.mjs`

**Interfaces:**

- Consumes:
  - Three.js r165 from `/js/vendor/three.module.js`
  - quality profile from `selectHomeQualityProfile`
  - pose from `createScenePose`
- Produces:

  ```javascript
  createQuantCoreScene({ canvas, profile }): Promise<{
      resize(): void,
      setProgress(progress: number): void,
      setPointer(pointer: {x:number,y:number}): void,
      setVisible(visible: boolean): void,
      render(time: number): boolean,
      getDiagnostics(): {drawCalls:number,triangles:number},
      dispose(): void
  }>
  ```

- [ ] **Step 1: Add failing scene contracts**

  In the Java contract test:

  ```java
  String scene = resource("static/js/home-observatory-scene.js");
  assertTrue(scene.contains("/js/vendor/three.module.js"));
  assertTrue(scene.contains("createQuantCoreScene"));
  assertTrue(scene.contains("MeshPhysicalMaterial"));
  assertTrue(scene.contains("InstancedMesh"));
  assertTrue(scene.contains("ACESFilmicToneMapping"));
  assertTrue(scene.contains("setPixelRatio(profile.pixelRatio)"));
  assertTrue(scene.contains("dispose()"));
  assertFalse(scene.contains("fetch("));
  assertFalse(scene.contains("http://"));
  assertFalse(scene.contains("https://"));
  ```

  Extend the Node pose test with:

  ```javascript
  const pose = createScenePose(0.35, { x: 1, y: -1 });
  assert.ok(pose.cameraZ >= 6.4 && pose.cameraZ <= 8.8);
  assert.ok(pose.housingOpen >= 0 && pose.housingOpen <= 1);
  assert.ok(pose.metricOpacity >= 0 && pose.metricOpacity <= 1);
  ```

- [ ] **Step 2: Run targeted tests and confirm RED**

  ```powershell
  node --test src/test/js/home-observatory-motion.test.mjs
  mvn.cmd "-Dmaven.repo.local=.m2/repository" -Dtest=HomePageTemplateContractTest test
  ```

  Expected: Node remains green for bounded pose values; Java fails because the scene module is missing.

- [ ] **Step 3: Implement the renderer and physical material system**

  In `home-observatory-scene.js`:

  - dynamically import `/js/vendor/three.module.js`;
  - create `WebGLRenderer({ alpha: true, antialias: profile.name === "high", powerPreference: "high-performance" })`;
  - set sRGB output, ACES filmic tone mapping, exposure `1.05`, and `renderer.setPixelRatio(profile.pixelRatio)`;
  - use a transparent scene and perspective camera at `(0, .3, 8.8)`;
  - create one `QuantCore` group containing:
    - three machined housing cylinders;
    - one knurled outer ring using `InstancedMesh`;
    - twelve iris blades using one `InstancedMesh`;
    - one dark transmissive lens;
    - one cobalt emissive inner aperture;
    - one calibration tick ring using `InstancedMesh`;
    - one flat product-representation plane that remains hidden until Act;
  - use a key directional light, a cold rim light, a soft fill, and one cobalt point light;
  - create a repeatable 256×64 grayscale CanvasTexture for brushed-metal roughness; do not allocate textures per frame;
  - create the product plane from a local CanvasTexture containing only grid lines and abstract chart geometry; keep all readable product copy and metrics in semantic DOM;
  - keep the complete scene under 20 draw calls by sharing geometry/materials and instancing repeated details.

  Material baselines:

  ```javascript
  const housingMaterial = new THREE.MeshPhysicalMaterial({
      color: 0x9298a0,
      metalness: 0.92,
      roughness: 0.24,
      clearcoat: 0.22,
      clearcoatRoughness: 0.3,
      roughnessMap: brushedMetalTexture
  });

  const lensMaterial = new THREE.MeshPhysicalMaterial({
      color: 0x0a111d,
      metalness: 0,
      roughness: 0.08,
      transmission: 0.72,
      thickness: 0.8,
      ior: 1.46,
      transparent: true,
      opacity: 0.82
  });

  const signalMaterial = new THREE.MeshStandardMaterial({
      color: 0x6ea8ff,
      emissive: 0x2458a8,
      emissiveIntensity: 2.2,
      metalness: 0.1,
      roughness: 0.2
  });
  ```

- [ ] **Step 4: Implement scroll poses without free-spinning decoration**

  `setProgress` stores a clamped value. `render` calls `createScenePose`, then applies:

  ```text
  Observe 0.00–0.18: object mostly shadowed; pointer highlight ±3 degrees.
  Resolve 0.18–0.52: outer housing halves separate by 0.46 units; iris opens; metrics fade to 1.
  Hold 0.52–0.68: metrics remain readable; camera movement slows.
  Act 0.68–1.00: metrics recede; product plane opacity rises; core moves 0.35 units left.
  ```

  Do not rotate the root continuously. Pointer response must be damped and bounded to `0.052` radians.

- [ ] **Step 5: Implement lifecycle and GPU cleanup**

  `render(time)` returns `false` without drawing when the scene is invisible. `resize()` updates the renderer only when dimensions change. `dispose()`:

  ```javascript
  scene.traverse((object) => {
      object.geometry?.dispose?.();
      const materials = Array.isArray(object.material) ? object.material : [object.material];
      materials.filter(Boolean).forEach((material) => {
          Object.values(material).forEach((value) => value?.isTexture && value.dispose());
          material.dispose?.();
      });
  });
  renderer.dispose();
  ```

  `getDiagnostics()` returns `renderer.info.render.calls` and `renderer.info.render.triangles` after the most recent frame.

- [ ] **Step 6: Run targeted tests and confirm GREEN**

  Run the commands from Step 2.

  Expected: Node and Java targeted tests pass.

- [ ] **Step 7: Commit**

  ```powershell
  git add src/main/resources/static/js/home-observatory-scene.js src/test/java/com/kingyurina/demo/web/HomePageTemplateContractTest.java src/test/js/home-observatory-motion.test.mjs
  git commit -m "feat: render physical quant core scene"
  ```

### Task 5: Integrate Progressive Enhancement and Retire Brik

**Files:**

- Create: `src/main/resources/static/js/home-observatory.js`
- Modify: `src/main/resources/templates/index.html`
- Modify: `src/test/java/com/kingyurina/demo/web/HomePageTemplateContractTest.java`
- Delete: the four Brik files listed in the file map.

**Interfaces:**

- Consumes: `window.Lenis`, `window.Motion`, scene/scroll/motion modules, and Task 2 selectors.
- Produces: one coordinated RAF and root states `home-motion-enabled`, `home-webgl-ready`, `home-webgl-failed`, and `home-scene-visible`.

- [ ] **Step 1: Write failing integration and retirement contracts**

  Add:

  ```java
  String template = resource("templates/index.html");
  String entry = resource("static/js/home-observatory.js");

  assertTrue(template.contains("/vendor/lenis/lenis.css"));
  assertTrue(template.contains("/vendor/lenis/lenis.min.js"));
  assertTrue(template.contains("/vendor/motion/motion.js"));
  assertTrue(template.contains("/js/home-observatory.js"));
  assertTrue(entry.contains("./home-observatory-scroll.js"));
  assertTrue(entry.contains("./home-observatory-motion.js"));
  assertTrue(entry.contains("./home-observatory-scene.js"));
  assertTrue(entry.contains("requestAnimationFrame"));
  assertTrue(entry.contains("IntersectionObserver"));
  assertTrue(entry.contains("visibilitychange"));
  assertTrue(entry.contains("prefers-reduced-motion: reduce"));
  assertFalse(entry.contains("setInterval"));
  assertFalse(entry.contains("fetch("));
  assertFalse(template.contains("home-brik"));
  assertFalse(new ClassPathResource("static/css/home-brik.css").exists());
  assertFalse(new ClassPathResource("static/js/home-brik.js").exists());
  assertFalse(new ClassPathResource("static/js/home-brik-motion.js").exists());
  ```

- [ ] **Step 2: Run the contract test and confirm RED**

  ```powershell
  mvn.cmd "-Dmaven.repo.local=.m2/repository" -Dtest=HomePageTemplateContractTest test
  ```

  Expected: FAIL because the entry module is missing and Brik assets still exist.

- [ ] **Step 3: Add local styles and scripts to the template in deterministic order**

  In `head`, load:

  ```html
  <link rel="stylesheet"
        th:href="@{/vendor/lenis/lenis.css(v='1.3.25')}"
        href="../static/vendor/lenis/lenis.css?v=1.3.25">
  ```

  At the end of `body`, load:

  ```html
  <script th:src="@{/vendor/lenis/lenis.min.js(v='1.3.25')}"
          src="../static/vendor/lenis/lenis.min.js?v=1.3.25"></script>
  <script th:src="@{/vendor/motion/motion.js(v='12.29.2')}"
          src="../static/vendor/motion/motion.js?v=12.29.2"></script>
  <script type="module"
          th:src="@{/js/home-observatory.js(v='20260724')}"
          src="../static/js/home-observatory.js?v=20260724"></script>
  ```

  Keeping all scripts at the end of `body` ensures the two classic bundles initialize before the entry module.

- [ ] **Step 4: Implement `home-observatory.js` as the single coordinator**

  The entry module must:

  1. collect scene, story, chapter, and reveal nodes;
  2. read reduced motion, mobile, core count, DPR, WebGL capability, and loopback-only test overrides once;
  3. select the quality profile;
  4. create Lenis only when allowed;
  5. create DOM motion with `window.Motion`;
  6. initialize Three.js only when `profile.webgl` is true;
  7. observe story visibility and reveal nodes;
  8. calculate progress from the story rectangle;
  9. forward pointer only on pointer-enabled profiles;
  10. use one RAF that calls `scroll.raf(time)` and `scene.render(time)`;
  11. pause the scene on `visibilitychange` and outside the story;
  12. show the CSS core immediately if scene initialization rejects.

  On loopback only, measure the JavaScript render submission with `performance.now()` and copy the rolling 60-frame average plus `scene.getDiagnostics().drawCalls` to `canvas.dataset.renderMs` and `canvas.dataset.drawCalls`. Do not expose diagnostics on non-loopback hosts.

  Merge loopback overrides into environment detection exactly once:

  ```javascript
  const overrides = resolveLocalTestOverrides({
      hostname: window.location.hostname,
      search: window.location.search
  });
  const environment = {
      reduceMotion: overrides.forceReduced
          || window.matchMedia("(prefers-reduced-motion: reduce)").matches,
      mobile: window.matchMedia("(max-width: 767px)").matches,
      cores: navigator.hardwareConcurrency || 4,
      dpr: window.devicePixelRatio || 1,
      webgl: !overrides.forceStatic && detectWebGLSupport()
  };

  function detectWebGLSupport() {
      const probe = document.createElement("canvas");
      return Boolean(probe.getContext("webgl2") || probe.getContext("webgl"));
  }
  ```

  Use this failure boundary:

  ```javascript
  let scene = null;
  try {
      scene = profile.webgl
          ? await createQuantCoreScene({ canvas, profile })
          : null;
      root.classList.toggle("home-webgl-ready", Boolean(scene));
      root.classList.toggle("home-webgl-failed", !scene);
  } catch {
      root.classList.add("home-webgl-failed");
  }
  ```

  Do not log expected fallback failures or leave content opacity at zero.

- [ ] **Step 5: Delete only retired Brik-owned resources**

  Delete:

  ```text
  src/main/resources/static/css/home-brik.css
  src/main/resources/static/js/home-brik.js
  src/main/resources/static/js/home-brik-motion.js
  src/test/js/home-brik-motion.test.mjs
  ```

  Confirm no remaining references:

  ```powershell
  rg -n "home-brik" src/main src/test
  ```

  Expected: no matches.

- [ ] **Step 6: Run targeted and full tests**

  ```powershell
  node --test src/test/js/home-observatory-motion.test.mjs
  mvn.cmd "-Dmaven.repo.local=.m2/repository" -Dtest=HomePageTemplateContractTest,HomeControllerIndexTest test
  mvn.cmd "-Dmaven.repo.local=.m2/repository" test
  ```

  Expected: Node passes, targeted Maven passes, and the full Maven suite reports zero failures.

- [ ] **Step 7: Commit**

  ```powershell
  git add src/main/resources/templates/index.html src/main/resources/static/js/home-observatory.js src/main/resources/static/css/home-brik.css src/main/resources/static/js/home-brik.js src/main/resources/static/js/home-brik-motion.js src/test/js/home-brik-motion.test.mjs src/test/java/com/kingyurina/demo/web/HomePageTemplateContractTest.java
  git commit -m "feat: launch quant observatory homepage"
  ```

### Task 6: Browser Quality, Accessibility, and Performance Pass

**Files:**

- Modify only when a verified defect requires it:
  - `src/main/resources/templates/index.html`
  - `src/main/resources/static/css/home-observatory.css`
  - `src/main/resources/static/js/home-observatory.js`
  - `src/main/resources/static/js/home-observatory-scroll.js`
  - `src/main/resources/static/js/home-observatory-motion.js`
  - `src/main/resources/static/js/home-observatory-scene.js`
  - `src/test/java/com/kingyurina/demo/web/HomePageTemplateContractTest.java`
  - `src/test/js/home-observatory-motion.test.mjs`

**Interfaces:**

- Consumes: completed homepage from Tasks 1–5.
- Produces: verified responsive, keyboard, fallback, and frame-lifecycle behavior.

- [ ] **Step 1: Start the isolated worktree server on port 8081**

  Build first:

  ```powershell
  mvn.cmd "-Dmaven.repo.local=.m2/repository" -DskipTests package
  ```

  Run with the existing local database configuration and `--server.port=8081`, leaving the primary checkout's port 8080 process untouched.

  Expected: `http://127.0.0.1:8081/` returns HTTP 200.

- [ ] **Step 2: Inspect the initial hero at 1440×900 and 1024×768**

  Using the in-app browser:

  - confirm the headline is readable within the first viewport;
  - confirm the Quant Core is one centered physical object with no wire cage or loose ribbons;
  - confirm metal edge highlights, dark lens, iris, and cobalt aperture read separately;
  - confirm `Enter Quant` is the only primary-looking action;
  - confirm navigation does not overlap the headline;
  - confirm the browser console has no homepage errors.

- [ ] **Step 3: Inspect the scroll story**

  At progress near `0`, `0.5`, and `1`:

  ```text
  0.0: housing closed, metrics hidden, Observe copy active.
  0.5: housing open, Signal/Alpha/Risk readable, Resolve copy active.
  1.0: product representation visible, metrics receded, Act copy active.
  ```

  Scroll input must remain interruptible and anchor navigation must land on `#resolve` without trapping the page.

  Read `data-render-ms` and `data-draw-calls` from the loopback canvas after 60 active frames. Confirm average render submission is at most `16.0`ms and draw calls are at most `20`.

- [ ] **Step 4: Inspect 768×1024 and 375×812**

  Verify:

  - headline and core do not obscure each other;
  - product links form one readable column at 375px;
  - every link/button has a 44px hit area;
  - mobile uses the low quality profile;
  - navigation remains reachable;
  - `document.documentElement.scrollWidth <= window.innerWidth`.

- [ ] **Step 5: Verify keyboard and static fallbacks**

  - Tab from the skip link through navigation, hero CTA, story link, product links, final CTA, and footer.
  - Confirm focus rings remain visible on near-black surfaces.
  - Disable JavaScript and reload: headline, CTAs, CSS Quant Core, all chapters, products, and footer remain visible.
  - Open `http://127.0.0.1:8081/?motion=reduce` and confirm Lenis/WebGL camera travel/reveal staggering are disabled while content remains visible.
  - Open `http://127.0.0.1:8081/?render=static` and confirm `.home-webgl-failed` displays the CSS core without a spinner or blank hero.

- [ ] **Step 6: Add a regression test before each browser-discovered fix**

  For a pure state issue, add a failing Node assertion. For markup, route, accessibility, or asset issues, add a failing Java contract assertion. Apply the smallest production fix, then rerun its covering test.

- [ ] **Step 7: Run final verification**

  ```powershell
  node --test src/test/js/home-observatory-motion.test.mjs
  mvn.cmd "-Dmaven.repo.local=.m2/repository" test
  rg -n "home-brik|http://|https://|fetch\\(" src/main/resources/templates/index.html src/main/resources/static/css/home-observatory.css src/main/resources/static/js/home-observatory*.js
  git diff --check
  git status --short
  ```

  Expected:

  - Node tests pass.
  - Maven reports zero failures.
  - `home-brik`, network transports, and remote URLs have no matches in homepage-owned production files.
  - `git diff --check` reports no whitespace errors.
  - only intentional Quant Observatory changes are present in the isolated worktree.

- [ ] **Step 8: Commit browser-pass fixes when any exist**

  ```powershell
  git add src/main/resources/templates/index.html src/main/resources/static/css/home-observatory.css src/main/resources/static/js/home-observatory.js src/main/resources/static/js/home-observatory-scroll.js src/main/resources/static/js/home-observatory-motion.js src/main/resources/static/js/home-observatory-scene.js src/test/java/com/kingyurina/demo/web/HomePageTemplateContractTest.java src/test/js/home-observatory-motion.test.mjs
  git commit -m "fix: polish quant observatory experience"
  ```

  If no browser defect required a file change, do not create an empty commit.
