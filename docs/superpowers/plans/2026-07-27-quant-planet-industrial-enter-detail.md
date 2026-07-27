# Quant Planet Industrial ENTER and Detail Pass Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the approved industrial hatch ENTER control with a one-second mechanical twitch and add a final visible detail pass to the Quant Planet.

**Architecture:** Keep navigation as one semantic DOM anchor and construct the hatch entirely with CSS layers so it remains responsive and accessible. Add planet detail through existing colored instanced-mesh families so visual density increases without adding draw calls or texture requests.

**Tech Stack:** Java 21, Spring Boot, Thymeleaf, CSS, vanilla JavaScript, Three.js, Node test runner, JUnit 5.

## Global Constraints

- Preserve the single `data-enter-quant` anchor and `/quant` destination.
- Use the exact visible label `ENTER`.
- Idle twitch cycle is exactly `1s`.
- Hover lift is `3px`; active press travel is `6px`.
- Minimum interactive height is `48px`.
- Disable idle animation under `prefers-reduced-motion: reduce`.
- Complete high-profile scene stays at or below 75 draw calls and 140,000 triangles.
- Add no texture requests and no animation-frame owner.
- Keep uniform X/Y/Z planet scaling and all current responsive framing behavior.

---

### Task 1: Industrial Hatch ENTER Control

**Files:**
- Modify: `src/main/resources/templates/index.html`
- Modify: `src/main/resources/static/css/home-quant-planet.css`
- Test: `src/test/java/com/kingyurina/demo/web/HomePageTemplateContractTest.java`

**Interfaces:**
- Consumes: the existing `a.planet-primary-action[data-enter-quant]` runtime and `/quant` route.
- Produces: `span.planet-primary-action__label`, `@keyframes enter-hatch-twitch`, and CSS-only hatch layers on `::before` and `::after`.

- [ ] **Step 1: Write the failing markup and CSS contract tests**

Add assertions to `homepageContainsOneFocusedEntryAction`:

```java
Element label = elementsWithTag(actions.getFirst(), "span").getFirst();
assertEquals(
        "planet-primary-action__label",
        label.getAttribute("class"));
assertEquals("ENTER", label.getTextContent().strip());
```

Add a new test:

```java
@Test
void homepageEnterActionUsesTheApprovedIndustrialHatchMotion()
        throws IOException {
    String css = resource("static/css/home-quant-planet.css");
    String action = cssRule(css, ".planet-primary-action");
    String before = cssRule(css, ".planet-primary-action::before");
    String after = cssRule(css, ".planet-primary-action::after");
    String hover = cssRuleContainingSelector(
            css, ".planet-primary-action:hover");
    String active = cssRule(css, ".planet-primary-action:active");
    String reduced = cssRuleContainingSelector(
            css, "@media (prefers-reduced-motion: reduce)");

    assertEquals("1s", cssDeclaration(action, "animation-duration"));
    assertEquals(
            "enter-hatch-twitch",
            cssDeclaration(action, "animation-name"));
    assertEquals(
            "polygon(8% 0, 92% 0, 100% 24%, 100% 76%, 92% 100%, 8% 100%, 0 76%, 0 24%)",
            cssDeclaration(action, "clip-path"));
    assertTrue(before.contains("radial-gradient"));
    assertTrue(after.contains("var(--reference-ink)"));
    assertTrue(hover.contains("translate(-50%, -3px)"));
    assertTrue(active.contains("translate(-50%, 6px)"));
    assertTrue(css.contains("@keyframes enter-hatch-twitch"));
    assertTrue(reduced.contains("animation: none"));
}
```

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```powershell
mvn.cmd "-Dmaven.repo.local=C:\dev\codex-demo\.m2\repository" -Dtest=HomePageTemplateContractTest test
```

Expected: failures for the missing label span, pseudo-element hatch layers, and `enter-hatch-twitch`.

- [ ] **Step 3: Add the semantic label wrapper**

Change the anchor in `index.html` to:

```html
<a class="planet-primary-action" data-enter-quant href="/quant">
    <span class="planet-primary-action__label">ENTER</span>
</a>
```

Increment the stylesheet query version so browsers receive the new CSS.

- [ ] **Step 4: Implement the CSS hatch and interaction states**

Use these declarations as the implementation contract:

```css
.planet-primary-action {
    --hatch-lift: 0px;
    --hatch-press: 0px;
    min-width: clamp(156px, 11vw, 196px);
    min-height: 54px;
    border: 4px solid var(--reference-ink);
    border-radius: 0;
    clip-path: polygon(8% 0, 92% 0, 100% 24%, 100% 76%, 92% 100%, 8% 100%, 0 76%, 0 24%);
    background: #e2aa22;
    box-shadow: 0 8px 0 var(--reference-ink);
    animation-name: enter-hatch-twitch;
    animation-duration: 1s;
    animation-timing-function: linear;
    animation-iteration-count: infinite;
}

.planet-primary-action::before {
    background:
        radial-gradient(circle at 9% 26%, #fff2ba 0 3px, #253238 4px 6px, transparent 7px),
        radial-gradient(circle at 91% 26%, #fff2ba 0 3px, #253238 4px 6px, transparent 7px),
        radial-gradient(circle at 9% 74%, #fff2ba 0 3px, #253238 4px 6px, transparent 7px),
        radial-gradient(circle at 91% 74%, #fff2ba 0 3px, #253238 4px 6px, transparent 7px),
        linear-gradient(#f7c83c, #dc9d1e);
}

.planet-primary-action::after {
    background: linear-gradient(90deg, #b96a24 0 12%, var(--reference-ink) 12% 88%, #b96a24 88%);
}

.planet-primary-action:hover,
.planet-primary-action:focus-visible {
    animation-play-state: paused;
    transform: translate(-50%, -3px);
}

.planet-primary-action:active {
    animation-play-state: paused;
    box-shadow: 0 1px 0 var(--reference-ink);
    transform: translate(-50%, 6px);
}

@keyframes enter-hatch-twitch {
    0%, 72%, 75%, 80%, 100% {
        transform: translateX(-50%);
    }
    73% {
        transform: translate(-50%, -1px) rotate(.4deg);
    }
    77% {
        transform: translate(-50%, .5px) rotate(-.2deg);
    }
}
```

Keep the existing focus ring, intro opacity/translation choreography, locked state, safe-area positioning, compact breakpoints, and reduced-motion transition rules. Add `animation: none` for the action inside the reduced-motion media query.

- [ ] **Step 5: Run the focused test and verify GREEN**

Run the same focused Maven command.

Expected: all `HomePageTemplateContractTest` tests pass.

- [ ] **Step 6: Commit the industrial hatch**

```powershell
git add src/main/resources/templates/index.html src/main/resources/static/css/home-quant-planet.css src/test/java/com/kingyurina/demo/web/HomePageTemplateContractTest.java
git commit -m "feat: add industrial enter hatch"
```

---

### Task 2: Final Quant Planet Detail Pass

**Files:**
- Modify: `src/main/resources/static/js/home-quant-planet-world.js`
- Test: `src/test/js/home-quant-planet-world.test.mjs`

**Interfaces:**
- Consumes: `buildColoredInstanceFamily(THREE, { name, geometry, material, placements })`.
- Produces: additional placements in the existing `ReferenceIndustrialBoxDetails`, `ReferenceUtilityBoxDetails`, `ReferenceTempleBoxDetails`, `ReferenceVillageFacadeDetails`, `ReferenceWaterEdgeDetails`, and `ReferenceTerrainReliefDetails` meshes.

- [ ] **Step 1: Write failing minimum-density assertions**

Extend the existing detail-family tests:

```js
assert.ok(industrialBoxes.userData.detailFamilyCounts.pipeClamps >= 8);
assert.ok(industrialBoxes.userData.detailFamilyCounts.ladderRails >= 2);
assert.ok(utilityBoxes.userData.detailFamilyCounts.connectorBlocks >= 6);
assert.ok(utilityBoxes.userData.detailFamilyCounts.roofBrackets >= 4);
assert.ok(templeBoxes.userData.detailFamilyCounts.eaveTeeth >= 8);
assert.ok(templeBoxes.userData.detailFamilyCounts.stairBands >= 3);
assert.ok(village.userData.detailFamilyCounts.windowCrossbars >= 20);
assert.ok(village.userData.detailFamilyCounts.canopySupports >= 20);
assert.ok(water.userData.detailFamilyCounts.bridgePlanks >= 20);
assert.ok(water.userData.detailFamilyCounts.foamAccents >= 12);
assert.ok(terrain.userData.detailFamilyCounts.secondaryStrata >= 8);
assert.ok(terrain.userData.detailFamilyCounts.pathChips >= 12);
```

Retain the scene-budget assertions:

```js
assert.ok(stats.drawCalls + stats.shadowCalls <= 75);
assert.ok(stats.triangles <= 140000);
```

- [ ] **Step 2: Run the world test and verify RED**

Run:

```powershell
node --test src/test/js/home-quant-planet-world.test.mjs
```

Expected: failures begin at missing `pipeClamps` and `windowCrossbars`.

- [ ] **Step 3: Add the approved detail placements**

Append box-geometry placements to the existing arrays:

- 8 `pipeClamps` and 2 `ladderRails` around the industrial engines;
- 6 `connectorBlocks` and 4 `roofBrackets` around the utility mast and house;
- 8 `eaveTeeth` and 3 `stairBands` at the temple;
- two `windowCrossbars` and two `canopySupports` per authored village;
- five `bridgePlanks` per bridge and three `foamAccents` per water patch;
- one `secondaryStrata` per large relief source and 12 `pathChips`.

Every placement uses existing `resources.geometry.box`, existing palette colors,
and `surface: true` where it follows the planet curvature. Do not create a new
mesh, material, texture, or update loop.

- [ ] **Step 4: Run the world test and verify GREEN**

Run the focused Node command.

Expected: all world tests pass and render budgets remain green.

- [ ] **Step 5: Commit the detail pass**

```powershell
git add src/main/resources/static/js/home-quant-planet-world.js src/test/js/home-quant-planet-world.test.mjs
git commit -m "feat: deepen quant planet surface detail"
```

---

### Task 3: Integrated Verification

**Files:**
- Verify only; no planned source changes.

**Interfaces:**
- Consumes: the completed hatch and planet detail commits.
- Produces: fresh automated and browser evidence.

- [ ] **Step 1: Run all JavaScript tests**

```powershell
node --test src/test/js/*.test.mjs
```

Expected: zero failures.

- [ ] **Step 2: Run the complete Maven suite**

```powershell
mvn.cmd "-Dmaven.repo.local=C:\dev\codex-demo\.m2\repository" test
```

Expected: `BUILD SUCCESS` and zero failures.

- [ ] **Step 3: Rebuild and restart the local preview**

Stop the current port-8081 Java process, package with the workspace-local Maven
repository, and restart the MariaDB-profile JAR on port 8081 with startup signal
refresh disabled.

- [ ] **Step 4: Verify the live browser**

At `http://127.0.0.1:8081/`, confirm:

- hatch face, clipped corners, four rivets, side depth, and lower extrusion;
- one-second idle twitch with long stationary phases;
- hover lifts 3px and pauses twitch;
- active press travels 6px and collapses the extrusion;
- keyboard focus ring remains visible;
- reduced motion disables idle animation;
- `/quant` remains the only action destination;
- canvas reports at most 75 draw calls and 140,000 triangles;
- no horizontal or vertical document overflow;
- details remain visible during one full 36-second revolution.

- [ ] **Step 5: Confirm a clean feature branch**

```powershell
git diff --check
git status --short
```

Expected: no whitespace errors and no uncommitted implementation files.

