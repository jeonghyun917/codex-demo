# Contact 4 Living Meadow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build `/contact4` as an asset-free, full-screen, highly detailed living meadow animated by a coherent procedural wind field.

**Architecture:** A small lifecycle controller drives a Three.js world whose grass, flowers, dew and pollen are GPU-friendly procedural layers. HTML/CSS remain responsible for semantic content, editorial art direction and the complete non-WebGL fallback.

**Tech Stack:** Java 21, Spring Boot, Thymeleaf, CSS, vanilla ES modules, local Three.js.

## Global Constraints

- Preserve all unrelated dirty files.
- Do not use external images, video, React or new dependencies.
- Keep animation deterministic and pause it when hidden.
- Support reduced motion, touch, keyboard and WebGL failure.
- Verify one desktop, one mobile and one short landscape viewport.

---

### Task 1: Route and page contract

**Files:**
- Modify: `src/main/java/com/kingyurina/demo/web/HomeController.java`
- Modify: `src/main/java/com/kingyurina/demo/config/SecurityConfig.java`
- Modify: `src/test/java/com/kingyurina/demo/web/HomePageTemplateContractTest.java`
- Create: `src/main/resources/templates/contact4.html`

**Interfaces:** Produces public `GET /contact4` and semantic `data-nature-stage`, `data-nature-canvas`, `data-nature-loader` hooks.

- [ ] Add a failing HTTP/template contract test.
- [ ] Run the focused Maven test and confirm the route is absent.
- [ ] Add the route, public matcher and semantic template.
- [ ] Run the focused test and confirm it passes.

### Task 2: Deterministic living meadow core

**Files:**
- Create: `src/test/js/contact-nature-core.test.mjs`
- Create: `src/main/resources/static/js/contact-nature-core.js`

**Interfaces:** Produces `sampleWind`, `selectNatureProfile` and `createLivingMeadowWorld`; the world exposes named layers, `update` and `dispose`.

- [ ] Write failing tests for wind determinism, bounded profiles and required procedural layers.
- [ ] Run the focused Node test and confirm the missing module failure.
- [ ] Implement seeded placement, shader wind deformation, terrain, sky, grass, flowers, dew, pollen and atmosphere.
- [ ] Run the focused Node test and confirm it passes.

### Task 3: Lifecycle, responsive behavior and editorial UI

**Files:**
- Create: `src/main/resources/static/js/contact-nature.js`
- Create: `src/main/resources/static/css/contact-nature.css`
- Modify: `src/main/resources/templates/contact4.html`

**Interfaces:** Consumes the core world; publishes `data-render-state=ready|fallback`, accessible loader progress and stable pointer/reduced-motion behavior.

- [ ] Extend the route contract with required lifecycle and accessibility markers and confirm RED.
- [ ] Implement the renderer lifecycle, responsive profiles, CSS art direction and fallback.
- [ ] Confirm the focused Java and Node tests are GREEN.

### Task 4: Visual and regression verification

**Files:** No new production files.

**Interfaces:** Verifies the finished page at representative viewports and the full repository test suites.

- [ ] Build once and restart the 8080 server.
- [ ] Inspect 1440×900, 375×812 and 812×375 plus reduced motion.
- [ ] Perform at most one acceptance-focused correction round.
- [ ] Run the full JavaScript and Maven test suites once.
- [ ] Confirm `/contact4` returns HTTP 200 and contains no image/video media.

