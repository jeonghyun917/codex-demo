# Quant Planet Homepage Design

**Date:** 2026-07-25
**Status:** Approved direction delegated by the user
**Scope:** Public homepage only (`/`)
**Supersedes:** The Vercel-inspired Quant Observatory homepage direction

## 1. Product Intent

Replace the single mechanical Quant Core with an original, living miniature
world inspired by the craft and immediacy of Abeto's Messenger experience.
The result is not a copy of Messenger and is not a general-purpose browser
game. It is a guided interactive homepage that lets visitors travel through a
stylized Quant world while keeping the product, its evidence, and the primary
Quant route understandable within seconds.

The page must feel authored rather than assembled from generic primitives:

- the planet has a readable geography and three distinct landmarks;
- one animated Quant Courier gives the world scale and purpose;
- lighting, silhouettes, outlines, roads, props, foliage, and atmospheric
  layers form one coherent visual language;
- Signal, Alpha, and Risk appear as instruments inside the world, not as
  unrelated floating glass cards;
- the semantic HTML remains usable before the 3D scene loads and when it
  cannot run.

## 2. Decision and Alternatives

### Chosen: Guided Interactive Quant Planet

Scroll advances a deterministic journey around a small planet. A compact
analysis drone travels along the same route while the camera visits three
landmarks: **Observe**, **Resolve**, and **Act**. Pointer movement adds bounded
parallax on desktop, but the page never requires game controls.

This approach best balances:

- the emotional impact of a living 3D world;
- the homepage's conversion purpose;
- mobile and accessibility requirements;
- the existing Spring Boot and vanilla JavaScript architecture;
- a scope that can be completed and verified as one feature.

### Rejected: Fully Controllable Browser Game

Keyboard or virtual-joystick navigation, collision, quests, multiplayer, and
free camera movement would most closely resemble a game. They are excluded
because they hide the product CTA, create a much larger asset and testing
surface, and make the homepage harder for visitors who do not play games.

### Rejected: Passive Cinematic Planet

A noninteractive looping planet would be simpler and cheaper to render. It is
excluded because it would reproduce the appearance of the reference without
the sense of presence that makes the reference memorable.

## 3. Success Criteria

The page succeeds when:

1. The first viewport reads as a deliberate miniature world, not a Three.js
   geometry demo.
2. A visitor understands that King Yurina is a Quant intelligence product
   within three seconds.
3. The Quant Courier, route, and three landmarks create a clear visual story.
4. **Enter Quant** is visible immediately and remains the sole primary CTA.
5. Scrolling moves continuously and reversibly through Observe, Resolve, and
   Act without trapping or hijacking native input.
6. The page renders smoothly on current desktop hardware and uses a visibly
   intentional reduced profile on mobile and low-power devices.
7. Reduced-motion, WebGL failure, JavaScript-disabled, and keyboard-only modes
   retain all content and routes.
8. Existing Quant, Stocks, Heatmap, ETF, Atelier, and Backtest pages are not
   redesigned or functionally changed.

## 4. Visual Direction

### World Mood

The world is bright, calm, and tactile rather than dark and metallic. It uses
soft daylight, restrained cel shading, slightly imperfect outlines, and
graphic color blocking. It should feel like a premium animated miniature with
fintech precision, not a children's game or a sci-fi control room.

### Palette

| Role | Value | Use |
|---|---:|---|
| Sky | `#9ED9D5` | Full-page atmospheric background |
| Haze | `#D9EFE8` | Horizon and depth separation |
| Planet ground | `#B8C989` | Primary land mass |
| Warm architecture | `#F1D7A4` | Landmark walls and road details |
| Coral signal | `#E8755D` | Active route, courier accent, key state |
| Quant blue | `#2357A5` | Brand, data instruments, CTA |
| Deep ink | `#173149` | Outlines and primary text |
| Paper | `#FFF9EA` | UI surfaces and readable labels |
| Positive | `#2F8C73` | Positive semantic values |
| Risk | `#C85A5A` | Risk semantic value with text label |

Colors use semantic CSS variables. Signal and risk meaning never depends on
color alone.

### Typography

- Continue self-hosted Instrument Sans for display and interface copy.
- Continue self-hosted IBM Plex Mono for measurements and model values.
- Hero headline uses `clamp(3.2rem, 6.7vw, 7.2rem)`, moderate weight, and a
  compact line height.
- Body copy is at least 16px.
- Numeric data uses tabular figures.
- No new external font request is introduced.

### Geometry and Surface Language

- Use low-poly forms with intentional bevels and varied silhouettes.
- Use stepped toon ramps rather than physically realistic metal.
- Key foreground objects receive a dark, slightly irregular silhouette
  outline; distant props do not.
- Architecture combines asymmetric roofs, external pipes, dishes, cables,
  stairs, vents, and signs to avoid box-only buildings.
- Foliage uses clustered geometric canopies in two or three values rather than
  identical cones.
- A subtle full-screen grain layer unifies the WebGL and HTML surfaces.
- Avoid neon cages, generic particle storms, glossy glass cards, random
  floating rings, and broad gradients that obscure the world.

## 5. World Composition

### Planet

The planet is a compact sphere with a slightly faceted silhouette. A raised
route wraps across the visible hemisphere and connects the three landmarks.
Land, shallow water, rock clusters, utility poles, foliage, and clouds provide
depth without creating an open-world map.

The visible composition is handcrafted and deterministic. Procedural helpers
construct repeated details, but every major landmark and camera pose has an
authored position.

### Landmark 1: Observe Station

The entry view centers on a small hilltop observatory:

- a rotating directional dish;
- a segmented signal mast;
- paper-like windsocks and cables reacting subtly to time;
- a route sign labeled `01 / OBSERVE`;
- an instrument reading `MARKET FIELD / ONLINE`.

The hero copy reads:

> Market intelligence, in motion.

Supporting copy:

> Observe the market. Resolve the signal. Act with context.

### Landmark 2: Resolve Works

The route passes a compact analytical workshop. Three machines expose the
homepage's illustrative model view:

- `Signal 79 / 100`
- `20D Alpha +2.4%`
- `Risk Medium`

Each value is rendered in accessible HTML aligned with a corresponding 3D
instrument. The label `Illustrative model view` remains visible. Small
mechanical movements show data being sorted into one coherent output.

### Landmark 3: Decision Gate

The final landmark is a clean transit gate facing the `/quant` product:

1. Scan the market.
2. Resolve competing signals.
3. Open the authoritative dashboard.

The route light reaches the gate, the courier stops, and the final
**Enter Quant** action becomes visually dominant. The scene does not
automatically navigate.

### Quant Courier

The courier is an original compact bipedal analysis drone, not a human or a
copy of Messenger's character:

- rounded rectangular body;
- two articulated legs and short counterbalancing arms;
- antenna and compact parcel/data canister;
- coral jacket panel and Quant blue display;
- walk, idle, look, and antenna-react animation states.

The courier follows the authored route from normalized progress. Leg and arm
motion use phase-based procedural animation so no external rigged model or
animation file is required.

## 6. Interaction and Motion

### Scroll Journey

The native document scroll is normalized to `0..1` and divided into three
overlapping chapters:

| Progress | Chapter | Camera and World State |
|---:|---|---|
| `0.00..0.32` | Observe | Wide reveal, courier begins route |
| `0.24..0.70` | Resolve | Camera lowers and tracks workshop |
| `0.62..1.00` | Act | Camera rises toward decision gate |

Overlaps prevent hard cuts. All transitions are reversible and derived from a
single progress value.

### Pointer and Touch

- Desktop pointer movement adds at most four degrees of parallax.
- Pointer input never rotates the planet freely.
- Touch uses the native page scroll only; there is no hidden swipe or virtual
  joystick.
- Landmark labels and CTAs remain ordinary HTML links with 44px minimum hit
  targets.

### Ambient Motion

Ambient movement is restrained and independent of scroll:

- slow cloud drift;
- dish and antenna calibration;
- courier idle breathing and head turns;
- foliage motion with small amplitude;
- route lights pulsing only near the active chapter.

Ambient motion pauses when the page is hidden or the scene is outside its
visible range. Reduced-motion mode removes ambient loops and camera travel.

### DOM Motion

The existing self-hosted Motion and Lenis dependencies remain:

- Lenis smooths the main scroll only on capable, non-reduced-motion devices.
- Motion reveals copy and accessible metric overlays.
- Three.js owns only the 3D canvas and one render loop.
- DOM animation never duplicates the scene timeline or gates content.

## 7. Technical Architecture

The implementation remains Spring Boot, Thymeleaf, CSS, and vanilla ES
modules. React and a front-end build pipeline are not introduced.

The existing self-hosted Three.js r165 module remains the renderer. The
feature does not clone the complete upstream repository and does not require
an external CDN.

### File Boundaries

| Unit | Responsibility |
|---|---|
| `templates/index.html` | Semantic story, navigation, metrics, links, fallback order |
| `static/css/home-quant-planet.css` | Tokens, responsive layout, labels, static planet fallback |
| `static/js/home-quant-planet.js` | Page orchestration, lifecycle, progressive enhancement |
| `static/js/home-quant-planet-motion.js` | Pure progress, easing, courier phase, and camera pose functions |
| `static/js/home-quant-planet-scroll.js` | Lenis ownership and normalized native scroll |
| `static/js/home-quant-planet-world.js` | Planet, landmarks, props, courier, materials, and authored layout |
| `static/js/home-quant-planet-scene.js` | Renderer, camera, quality profile, render scheduling, disposal |
| `static/js/vendor/three.module.js` | Existing pinned Three.js r165 runtime |
| `static/vendor/lenis/*` | Existing pinned Lenis runtime and license |
| `static/vendor/motion/*` | Existing pinned Motion runtime and license |

The current `home-observatory` CSS and modules are removed only after all
template and test references have migrated.

### Render Contract

`createQuantPlanetScene({ canvas, profile })` returns:

```js
{
  resize(),
  setProgress(value),
  setPointer({ x, y }),
  setVisible(value),
  render(time),
  getDiagnostics(),
  dispose()
}
```

Pure world-state calculations remain independent of DOM and Three.js so Node
tests can verify them without WebGL.

### Data Flow

1. Thymeleaf returns semantic homepage HTML without a database or external API
   dependency.
2. The entry module detects reduced motion, WebGL, viewport, core count, and
   device pixel ratio.
3. A quality profile selects geometry detail, outlines, shadows, foliage, and
   pixel ratio.
4. Scroll progress feeds both accessible chapter state and the 3D scene.
5. The scene converts progress into camera, planet, courier, landmark, and
   route-light state.
6. The single page lifecycle schedules rendering only while a frame can
   contribute visible output.

## 8. Performance Budget

The page uses authored procedural geometry so it does not wait for remote
model downloads.

### High Profile

- pixel ratio capped at `1.5`;
- no more than 75 draw calls after warm-up;
- no more than 140,000 visible triangles;
- one directional shadow map capped at 1024px;
- outlines limited to hero objects and near landmarks;
- target scene render work below 12ms on the development desktop.

### Balanced Profile

- pixel ratio capped at `1.25`;
- simplified foliage and cloud count;
- 512px shadow map or blob shadows;
- outlines limited to courier and active landmark;
- no expensive transparent layering.

### Static Profile

- no WebGL renderer;
- fully visible CSS illustration and semantic content;
- no Lenis, parallax, stagger, or ambient animation.

No runtime request is made to a third-party asset, font, analytics, or market
data service.

## 9. Responsive Behavior

Required widths:

- 375px
- 768px
- 1024px
- 1440px

Desktop places copy in the left foreground while the planet occupies the
center and right. Metric overlays sit near their 3D instruments without
covering the courier route.

Mobile:

- places the headline above a centered planet;
- reduces the world to the active hemisphere;
- uses a simpler camera path and balanced/static quality profile;
- stacks metric overlays below the scene when they would obscure landmarks;
- retains a visible Enter Quant action;
- has no horizontal overflow;
- remains usable in short landscape viewports.

## 10. Accessibility and Failure Handling

- Preserve the skip link, one `h1`, sequential headings, and semantic lists.
- Keep the canvas decorative with `aria-hidden="true"`.
- Expose chapter and metric meaning through HTML, never canvas-only text.
- Keep all links reachable and visibly focused without motion or pointer input.
- Meet WCAG AA contrast for text and controls.
- Honor `prefers-reduced-motion: reduce` before creating Lenis or an animated
  scene.
- Do not hide content at zero opacity unless Motion successfully initializes.
- On WebGL initialization failure, activate the static planet composition.
- With JavaScript disabled, render the complete headline, chapters, product
  links, CTA, and static planet treatment.
- Never leave a blank canvas or unresolved loading screen.

## 11. Testing Strategy

### Java Contract Tests

Extend `HomePageTemplateContractTest` to verify:

- the new stylesheet and entry module are referenced;
- the semantic Observe, Resolve, and Act chapters exist;
- illustrative model values and disclaimer exist;
- the canvas is decorative;
- the primary and secondary product routes remain valid;
- the old observatory asset references are absent after migration;
- the existing Three.js, Motion, Lenis, font, and license assets remain local.

### Node Tests

Replace the observatory-specific JavaScript test with Quant Planet tests for:

- quality profile selection;
- finite `0..1` progress normalization;
- overlapping chapter selection;
- camera poses staying inside the authored envelope;
- courier route position and procedural walk phases;
- render continuation rules;
- reduced-motion and WebGL fallback selection;
- Lenis lifecycle;
- page visibility and back-forward cache lifecycle.

### Browser Verification

At every required width:

- inspect initial, Resolve, and Act states;
- confirm no horizontal overflow;
- verify primary navigation, anchor travel, and CTA destinations;
- verify keyboard focus order and visible focus;
- verify reduced motion and forced static/WebGL-failure modes;
- check console errors and scene diagnostics;
- confirm the scene pauses outside its visible range and when the page hides.

## 12. Delivery Sequence

1. Pin the new semantic and motion contracts with failing tests.
2. Build pure progress, camera, and courier state functions.
3. Build the authored planet, landmarks, courier, and toon materials.
4. Integrate the renderer and lifecycle with the existing performance profile.
5. Replace the template and CSS while preserving navigation and fallbacks.
6. Perform responsive, reduced-motion, static, and performance browser QA.
7. Remove obsolete observatory assets only after reference checks pass.

## 13. Acceptance Checklist

- [ ] The first viewport presents a coherent miniature Quant world.
- [ ] The world includes Observe Station, Resolve Works, and Decision Gate.
- [ ] The original Quant Courier visibly travels and animates.
- [ ] Signal, Alpha, and Risk appear as accessible, illustrative instruments.
- [ ] Enter Quant is visible immediately and remains the sole primary CTA.
- [ ] Scroll progress is native, reversible, and never traps the visitor.
- [ ] Desktop, mobile, reduced-motion, WebGL-failure, and no-JavaScript modes
      are intentional and usable.
- [ ] The scene remains inside the documented draw-call, triangle, pixel-ratio,
      and render-time budgets.
- [ ] Three.js, Lenis, Motion, fonts, and licenses remain self-hosted.
- [ ] React, multiplayer, game controls, audio autoplay, and external market
      API calls are not introduced.
- [ ] Existing subpages are unchanged.
- [ ] Automated tests and browser verification pass.

## 14. Non-Goals

- Copying Messenger's characters, planet, buildings, textures, music, source
  code, quests, dialogue, or brand language.
- Building free-roam controls, collisions, quests, multiplayer, chat, or
  character customization.
- Introducing Blender, Houdini, Unity, React, or a Node production runtime.
- Redesigning `/quant` or another subpage.
- Displaying live market values on the public homepage.
- Adding autoplay audio, a custom cursor, or scroll-jacking.
