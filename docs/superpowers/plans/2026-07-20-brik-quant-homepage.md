# Brik Quant Homepage Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the Aurora homepage with an original Brik-inspired, bright modular Quant AI landing page whose hero composer and product cards use real application routes.

**Architecture:** Keep `HomeController#index` and its DB-backed menu contract unchanged. Replace only the root template and homepage-owned static resources, using semantic Thymeleaf markup, a dedicated responsive stylesheet, a lightweight Canvas 2D runtime, and pure JavaScript motion helpers covered by Node tests.

**Tech Stack:** Java 21, Spring Boot, Thymeleaf, CSS Grid, vanilla ES modules, Canvas 2D, Web Animations API, JUnit 5, Mockito, Node test runner.

## Global Constraints

- Use the approved design spec at `docs/superpowers/specs/2026-07-20-brik-quant-homepage-design.md`.
- Do not use Brik branding, copy, media, code, or external assets.
- `HomeController#index` must call only `menuService.mainMenus()`, expose `mainMenus`, and return `index`.
- Homepage-owned resources must contain no external network calls or remote font/assets.
- Every chip and card must link to an existing controller route.
- JavaScript is progressive enhancement: form, navigation, chips, cards, and content remain usable without it.
- All interactive targets are at least 44px high and have visible `:focus-visible` states.
- Support 375px, 768px, 1024px, and 1280px layouts with no document-level horizontal overflow.
- Respect `prefers-reduced-motion: reduce`.
- Preserve unrelated tracked and untracked files.

---

### Task 1: Semantic Brik Homepage and Responsive Surface System

**Files:**
- Modify: `src/test/java/com/kingyurina/demo/web/HomePageTemplateContractTest.java`
- Modify: `src/main/resources/templates/index.html`
- Create: `src/main/resources/static/css/home-brik.css`

**Interfaces:**
- Consumes: Thymeleaf model attribute `mainMenus` whose entries expose `label` and `href`.
- Produces: stable selectors `data-home-nav`, `data-home-hero`, `data-home-composer`, `data-home-instrument`, `data-home-products`, `data-home-product-card`, and `data-home-reveal`.

- [ ] **Step 1: Replace Aurora template assertions with failing Brik structure assertions**

  Rewrite `HomePageTemplateContractTest` so separate tests assert:

  ```java
  assertTrue(template.contains("home-brik.css"));
  assertTrue(template.contains("class=\"home-page\""));
  assertTrue(template.contains("data-home-nav"));
  assertTrue(template.contains("th:each=\"menu : ${mainMenus}\""));
  assertTrue(template.contains("id=\"main-content\""));
  assertTrue(template.contains("data-home-composer"));
  assertTrue(template.contains("action=\"/stocks\""));
  assertTrue(template.contains("name=\"symbol\""));
  assertTrue(template.contains("data-home-instrument"));
  assertTrue(template.contains("data-home-metric=\"signal\""));
  assertTrue(template.contains("data-home-metric=\"alpha\""));
  assertTrue(template.contains("data-home-metric=\"risk\""));
  assertTrue(template.contains("id=\"systems\""));
  assertTrue(template.contains("data-home-product-card"));
  assertTrue(template.contains("id=\"contact\""));
  assertTrue(css.contains("@media (max-width: 767px)"));
  assertTrue(css.contains("@media (min-width: 768px)"));
  assertTrue(css.contains("@media (min-width: 1024px)"));
  assertTrue(css.contains("@media (prefers-reduced-motion: reduce)"));
  assertTrue(css.contains("min-height: 44px"));
  assertTrue(css.contains(":focus-visible"));
  assertFalse(template.contains("home-aurora"));
  assertFalse(template.contains("data-aurora"));
  assertFalse(template.contains("http://"));
  assertFalse(template.contains("https://"));
  ```

  Add a route-contract assertion for each required href from the design spec and assert one `<h1`.

- [ ] **Step 2: Run the contract test and confirm RED**

  Run:

  ```powershell
  mvn.cmd "-Dmaven.repo.local=.m2/repository" -Dtest=HomePageTemplateContractTest test
  ```

  Expected: FAIL because `home-brik.css`, Brik selectors, composer markup, and Bento routes do not exist.

- [ ] **Step 3: Replace `index.html` with the approved semantic structure**

  Implement this exact hierarchy:

  ```html
  <body class="home-page">
    <a class="home-skip" href="#main-content">Skip to content</a>
    <header class="home-nav" data-home-nav>...</header>
    <main id="main-content">
      <section class="home-hero" data-home-hero>
        <canvas data-home-canvas aria-hidden="true"></canvas>
        <div class="home-hero-copy">...</div>
        <div class="home-tool-stage">
          <form action="/stocks" method="get" data-home-composer>...</form>
          <aside data-home-instrument aria-labelledby="home-instrument-title">...</aside>
        </div>
      </section>
      <section id="systems" data-home-products>...</section>
      <section id="how-it-works">...</section>
      <section class="home-final-cta">...</section>
    </main>
    <footer id="contact">...</footer>
  </body>
  ```

  Use the exact copy, routes, labels, metrics, and six product cards from the design spec. Use inline local SVG path markup only for arrows and decorative charts, always with `aria-hidden="true"`.

- [ ] **Step 4: Implement the full responsive design system in `home-brik.css`**

  Define semantic tokens for every color, spacing tier, radius, z-index, typography tier, and easing curve from the design spec. Implement:

  - sticky segmented navigation;
  - hero canvas and content stacking contexts;
  - functional white composer and graphite instrument;
  - 12-column desktop Bento layout with named card spans;
  - two-column tablet and one-column mobile layouts;
  - native horizontal navigation scrolling only below 768px;
  - focus, hover, pressed, noscript, and reduced-motion states;
  - `overflow-x: clip` on the page shell while keeping the mobile nav scroller usable.

- [ ] **Step 5: Run the contract test and confirm GREEN**

  Run the same Maven command from Step 2.

  Expected: `HomePageTemplateContractTest` passes with zero failures.

- [ ] **Step 6: Commit**

  ```powershell
  git add src/test/java/com/kingyurina/demo/web/HomePageTemplateContractTest.java src/main/resources/templates/index.html src/main/resources/static/css/home-brik.css
  git commit -m "feat: build brik quant homepage structure"
  ```

### Task 2: Progressive Canvas and Motion Runtime

**Files:**
- Create: `src/test/js/home-brik-motion.test.mjs`
- Create: `src/main/resources/static/js/home-brik-motion.js`
- Create: `src/main/resources/static/js/home-brik.js`
- Modify: `src/test/java/com/kingyurina/demo/web/HomePageTemplateContractTest.java`
- Modify: `src/main/resources/templates/index.html`

**Interfaces:**
- Consumes: selectors produced by Task 1.
- Produces:
  - `clamp(value, min, max): number`
  - `normalizePointer(clientX, clientY, rect): {x:number,y:number}`
  - `selectHomeMotionProfile(environment): {name,pixelRatio,pointerEnabled,animate}`
  - `staggerDelay(index, step = 48): number`

- [ ] **Step 1: Write failing Node tests for pure motion helpers**

  Cover:

  ```javascript
  assert.deepEqual(normalizePointer(50, 25, { left: 0, top: 0, width: 100, height: 100 }), { x: 0, y: -0.5 });
  assert.deepEqual(normalizePointer(-20, 150, { left: 0, top: 0, width: 100, height: 100 }), { x: -1, y: 1 });
  assert.deepEqual(selectHomeMotionProfile({
    reduceMotion: true, mobile: false, cores: 16, devicePixelRatio: 3
  }), { name: "reduced", pixelRatio: 1, pointerEnabled: false, animate: false });
  assert.equal(selectHomeMotionProfile({
    reduceMotion: false, mobile: true, cores: 8, devicePixelRatio: 3
  }).name, "low");
  assert.equal(staggerDelay(4), 192);
  ```

- [ ] **Step 2: Run Node tests and confirm RED**

  ```powershell
  node --test src/test/js/home-brik-motion.test.mjs
  ```

  Expected: FAIL because `home-brik-motion.js` does not exist.

- [ ] **Step 3: Implement pure helpers**

  Implement the four exported functions with clamped finite outputs. Reduced motion always wins; mobile or `cores <= 4` selects low; otherwise select high with pixel ratio capped at `1.5`.

- [ ] **Step 4: Run Node tests and confirm GREEN**

  Run the Step 2 command.

  Expected: all helper tests pass.

- [ ] **Step 5: Add failing Java runtime contract assertions**

  Assert that the template loads `/js/home-brik.js`, the entry imports `home-brik-motion.js`, uses Canvas 2D, `IntersectionObserver`, `requestAnimationFrame`, `visibilitychange`, `pointermove`, `pointerleave`, and Web Animations `element.animate`. Assert it contains no `fetch(`, `XMLHttpRequest`, `WebSocket`, `EventSource`, `http://`, or `https://`.

- [ ] **Step 6: Run Java contract test and confirm RED**

  Expected: FAIL because the entry module and script tag do not exist.

- [ ] **Step 7: Implement progressive enhancement runtime**

  In `home-brik.js`:

  - read the environment and motion profile once;
  - render an original Canvas 2D field using broad gradient ribbons and a subtle grid;
  - size Canvas using `ResizeObserver` or passive resize handling and the selected capped pixel ratio;
  - smooth pointer energy toward its target without allocating per frame;
  - pause animation while hidden or outside the hero;
  - render exactly one frame in reduced-motion mode;
  - reveal `[data-home-reveal]` elements with `IntersectionObserver` and `element.animate`, then mark them visible;
  - expose no globals and make missing Canvas/observer support a silent static fallback.

  Add the local module script tag to `index.html`.

- [ ] **Step 8: Run Node and Java tests and confirm GREEN**

  ```powershell
  node --test src/test/js/home-brik-motion.test.mjs
  mvn.cmd "-Dmaven.repo.local=.m2/repository" -Dtest=HomePageTemplateContractTest test
  ```

  Expected: both commands pass.

- [ ] **Step 9: Commit**

  ```powershell
  git add src/test/js/home-brik-motion.test.mjs src/main/resources/static/js/home-brik-motion.js src/main/resources/static/js/home-brik.js src/test/java/com/kingyurina/demo/web/HomePageTemplateContractTest.java src/main/resources/templates/index.html
  git commit -m "feat: animate brik quant homepage"
  ```

### Task 3: Controller Isolation and Aurora Retirement

**Files:**
- Create: `src/test/java/com/kingyurina/demo/web/HomeControllerIndexTest.java`
- Delete: `src/main/resources/static/css/home-aurora.css`
- Delete: `src/main/resources/static/js/home-aurora.js`
- Delete: `src/main/resources/static/js/home-aurora-field.js`
- Delete: `src/main/resources/static/js/home-aurora-motion.js`
- Delete: `src/main/resources/static/js/home-aurora-quality.js`
- Delete: `src/test/js/home-aurora-motion.test.mjs`
- Delete: `src/test/js/home-aurora-quality.test.mjs`
- Modify: `src/test/java/com/kingyurina/demo/web/HomePageTemplateContractTest.java`

**Interfaces:**
- Consumes: existing `HomeController(MenuService, StockDashboardViewSnapshotService)`.
- Produces: an explicit regression test protecting root-page render isolation.

- [ ] **Step 1: Write the controller isolation test**

  Using Mockito and `ExtendedModelMap`:

  ```java
  MenuService menuService = mock(MenuService.class);
  StockDashboardViewSnapshotService snapshots = mock(StockDashboardViewSnapshotService.class);
  List<MenuItem> menus = List.of(new MenuItem("Quant", "/quant"));
  when(menuService.mainMenus()).thenReturn(menus);
  HomeController controller = new HomeController(menuService, snapshots);
  ExtendedModelMap model = new ExtendedModelMap();

  assertEquals("index", controller.index(model));
  assertSame(menus, model.get("mainMenus"));
  verify(menuService).mainMenus();
  verifyNoInteractions(snapshots);
  ```

- [ ] **Step 2: Run the controller test and verify it passes against existing production code**

  This is a characterization test, so a passing first run is expected and acceptable; it protects an existing requirement rather than introducing production behavior.

- [ ] **Step 3: Add failing retirement assertions**

  Assert the new template/test resources contain no `home-aurora` or `data-aurora`, and assert `ClassPathResource` does not find the six homepage-owned Aurora CSS/JS assets.

- [ ] **Step 4: Run the contract test and confirm RED**

  Expected: FAIL because Aurora assets still exist.

- [ ] **Step 5: Delete only the homepage-owned Aurora assets and obsolete Node tests**

  Retain `static/js/vendor/three.module.js`, `home-nature-3d.js`, shared CSS, and every non-home resource.

- [ ] **Step 6: Run targeted and full tests**

  ```powershell
  node --test src/test/js/home-brik-motion.test.mjs
  mvn.cmd "-Dmaven.repo.local=.m2/repository" test
  ```

  Expected: Node tests pass and Maven reports zero failures.

- [ ] **Step 7: Commit**

  ```powershell
  git add src/test/java/com/kingyurina/demo/web/HomeControllerIndexTest.java src/test/java/com/kingyurina/demo/web/HomePageTemplateContractTest.java src/main/resources/static/css/home-aurora.css src/main/resources/static/js/home-aurora.js src/main/resources/static/js/home-aurora-field.js src/main/resources/static/js/home-aurora-motion.js src/main/resources/static/js/home-aurora-quality.js src/test/js/home-aurora-motion.test.mjs src/test/js/home-aurora-quality.test.mjs
  git commit -m "test: retire aurora homepage runtime"
  ```

### Task 4: Browser Quality Pass

**Files:**
- Modify if required by observed failures: `src/main/resources/templates/index.html`
- Modify if required by observed failures: `src/main/resources/static/css/home-brik.css`
- Modify if required by observed failures: `src/main/resources/static/js/home-brik.js`
- Modify if a regression contract is needed: `src/test/java/com/kingyurina/demo/web/HomePageTemplateContractTest.java`
- Modify if a pure helper regression is needed: `src/test/js/home-brik-motion.test.mjs`

**Interfaces:**
- Consumes: completed homepage from Tasks 1–3.
- Produces: verified desktop, tablet, mobile, keyboard, and reduced-motion behavior.

- [ ] **Step 1: Start the worktree application on port 8081**

  Use the existing MariaDB and run the worktree build with `--server.port=8081`, keeping the current main checkout server untouched.

- [ ] **Step 2: Inspect 1280×720**

  Verify the segmented nav, headline, composer, instrument, and first Bento row have no overlap or clipping. Verify the browser console has no homepage errors.

- [ ] **Step 3: Inspect 768×1024 and 375×812**

  Verify stacking rules, nav scroller, input/submit layout, one-column mobile Bento, 44px targets, and `document.documentElement.scrollWidth <= innerWidth`.

- [ ] **Step 4: Verify real interactions**

  - Composer GET submission preserves the typed symbol.
  - All prompt and product routes resolve.
  - Keyboard focus order is logical and focus rings remain visible.
  - Reduced-motion renders all content without reveals or continuous Canvas animation.

- [ ] **Step 5: For every observed defect, add a failing regression contract when practical, then apply the smallest fix**

  Re-run the covering test after each fix. Do not make untested behavior changes unrelated to a visible defect.

- [ ] **Step 6: Run final verification**

  ```powershell
  node --test src/test/js/home-brik-motion.test.mjs
  mvn.cmd "-Dmaven.repo.local=.m2/repository" test
  git status --short
  ```

  Expected: all tests pass and only intentional homepage/spec/plan changes remain.

- [ ] **Step 7: Commit any browser-pass fixes**

  ```powershell
  git add src/main/resources/templates/index.html src/main/resources/static/css/home-brik.css src/main/resources/static/js/home-brik.js src/test/java/com/kingyurina/demo/web/HomePageTemplateContractTest.java src/test/js/home-brik-motion.test.mjs
  git commit -m "fix: polish brik homepage across viewports"
  ```
