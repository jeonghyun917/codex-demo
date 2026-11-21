# Fullscreen Quant Planet Homepage Design

**Date:** 2026-07-26  
**Status:** Approved direction; written specification pending user review  
**Scope:** Public homepage only (`/`)  
**Supersedes:** `2026-07-25-quant-planet-homepage-design.md`

## 1. Product Decision

Replace the scroll-driven Quant Planet homepage with one fullscreen interactive
WebGL experience. The page should have the same immediate, toy-like scale and
input model that the user values in Messenger:

- a deliberate fullscreen entry sequence;
- one large miniature planet filling most of the viewport;
- no vertical page scroll;
- a continuously rotating central type ring;
- pointer, drag, and touch reactions;
- a courier and authored landmarks that make the world feel alive;
- minimal interface chrome above the 3D world.

This is not a code, asset, model, character, dialogue, sound, or brand copy of
Messenger. The composition, timing language, fullscreen interaction model, and
perceived scale should be closely matched, while every visible Quant object and
word remains original to King Yurina.

## 2. Success Criteria

The redesign succeeds when:

1. The homepage reads as a fullscreen 3D experience rather than a long landing
   page with a 3D background.
2. The initial reveal feels intentional before the user interacts.
3. The planet occupies approximately 70–85% of the desktop viewport and remains
   the dominant object on mobile.
4. The page has no document wheel scroll or hidden content below the fold.
5. Six rotating words are real links to the six product pages.
6. Pointer, drag, and touch input add presence without requiring game controls.
7. The Quant Courier continuously travels between Observe Station, Resolve
   Works, and Decision Gate.
8. Reduced-motion, WebGL-failure, no-JavaScript, keyboard, mobile, and low-power
   modes remain intentional and usable.
9. Existing subpages and their controllers are not changed.

## 3. Fullscreen Composition

### Viewport Contract

- `html`, `body`, and the homepage application occupy `100dvh`.
- The homepage uses `overflow: hidden`; the document has no vertical journey.
- Safe-area insets are respected on mobile.
- The 3D canvas and CSS fallback cover the viewport.
- The main scene is centered rather than laid out as a content column.
- No nested scroll area is introduced.

### Visual Scale

Desktop:

- planet diameter: approximately `min(82vh, 72vw)`;
- world center: near `50vw / 54vh`;
- courier and landmarks remain large enough to read without zooming;
- the type ring crosses in front of and behind the visual center through
  perspective, but never blocks the primary action.

Mobile:

- planet diameter: approximately `92vw`, capped by available height;
- world center moves slightly upward to leave room for navigation hints;
- the scene uses the balanced quality profile;
- the same six product links remain reachable without opening another panel.

## 4. Entry Sequence

The entry sequence is a state machine, not a fixed blocking video.

| State | Target timing | Visual |
|---|---:|---|
| `boot` | immediate | Paper-colored fullscreen loader and original courier-line icon |
| `mark` | `0.20–0.85s` | `KING YURINA / QUANT FIELD` assembles at center |
| `split` | `0.85–1.25s` | The mark separates horizontally and reveals the world |
| `reveal` | `1.10–1.70s` | Planet scales from `0.88` to `1`; lighting and type ring resolve |
| `ready` | after scene readiness | Controls activate and the rotating navigation begins |

Rules:

- The loader reflects actual scene readiness and may not hide a failed scene.
- A minimum display of roughly `700ms` prevents a flash on fast machines.
- A maximum of `4s` exits to the static composition if initialization stalls.
- Input is not trapped after the ready state.
- Returning through BFCache does not replay the full loader.
- Reduced motion uses a short opacity crossfade without split, scale, or orbit
  motion.
- With JavaScript disabled, the finished static composition is visible
  immediately; there is no unresolved loader.

## 5. Rotating Product Navigation

### Route Mapping

The central ring contains six real product anchors:

| Ring label | Destination | Accessible name |
|---|---|---|
| `QUANT` | `/quant` | Open Quant Intelligence |
| `STOCKS` | `/stocks` | Open Stocks |
| `HEATMAP` | `/stocks/heatmap?index=SP500` | Open Market Heatmap |
| `ETF` | `/etfs` | Open ETF Radar |
| `ATELIER` | `/atelier` | Open Strategy Atelier |
| `SIGNAL` | `/signals/backtest` | Open Signal Backtest |

### Rendering Model

The ring is semantic DOM synchronized with the scene, not inaccessible text
painted only into WebGL:

- each word is an ordinary `<a>`;
- six anchors are distributed around a circular 3D transform;
- perspective, scale, opacity, and stacking make the front word dominant;
- the back half remains visible but subdued;
- the ring completes a slow revolution in approximately `24–32s`;
- the movement pauses while an item is hovered, focused, pressed, or dragged;
- focus brings the selected link to the front without moving keyboard focus;
- clicking or pressing Enter performs normal browser navigation;
- visited state does not reduce text contrast.

The ring itself replaces the previous product grid. It is not duplicated below
the viewport.

### Input

- The mouse position adds bounded camera parallax.
- Pointer drag rotates the planet and navigation ring together.
- Touch horizontal drag performs the same bounded rotation.
- Arrow Left/Right moves focus and rotates the neighboring ring item forward.
- Escape releases an active drag/focus presentation but does not change pages.
- Wheel input does not scroll, zoom, or rotate the scene.
- A short `DRAG TO ORBIT / SELECT A FIELD` hint is visible but secondary.

## 6. World and Courier

Reuse and reframe the authored Quant world rather than rebuilding generic
geometry:

- Observe Station, Resolve Works, and Decision Gate are visible together on
  the active hemisphere;
- the planet receives a stronger centered camera composition;
- the current faceted land, water shell, toon ramp, outlines, asymmetric
  architecture, instanced props, and resource ownership remain;
- Signal, 20D Alpha, and Risk instruments remain integrated into Resolve
  Works, but do not become large dashboard cards;
- the route lights animate spatially around the courier;
- the courier loops continuously through the three landmarks, slows at each
  station, and idles briefly at Decision Gate;
- no scroll progress is required to move the courier;
- pointer/drag input changes presentation angle, not the courier's route.

No external model, texture, font, audio, or runtime data request is introduced.

## 7. Minimal Interface

The fullscreen interface contains only:

- top-left King Yurina mark linked to `/`;
- top-right `Enter Quant` primary action linked to `/quant`;
- the central rotating six-link navigation;
- a compact bottom-left status:
  `QUANT FIELD / ONLINE`;
- a compact bottom-right interaction hint;
- one visually hidden but keyboard-reachable skip link to the product
  navigation.

The previous long hero copy, three scroll chapters, product grid, final CTA,
and footer are removed from the visible fullscreen composition.

Essential meaning remains available to assistive technology:

- one `h1`: `Market intelligence, in motion.`;
- narrative: `Observe the market. Resolve the signal. Act with context.`;
- illustrative values:
  `Signal 79 / 100`, `20D Alpha +2.4%`, `Risk Medium`;
- label: `Illustrative model view`;
- all six product destinations.

This semantic summary may be visually hidden in the enhanced experience, but
must become visible in static, reduced-capability, or no-JavaScript modes.

## 8. Motion Architecture

### Pure State

Replace scroll-derived progress with time and direct manipulation:

- `createIntroState(time, ready, reducedMotion)`
- `createOrbitPose(time, dragOffset, pointer)`
- `createCourierLoopPose(time)`
- `createNavigationRingState(time, selectedIndex, interaction)`
- `shouldRenderFrame(environment)`

All functions return finite, bounded values and remain Node-testable.

### Runtime Ownership

- one page coordinator owns one `requestAnimationFrame` lifecycle;
- the Three.js scene and DOM ring consume the same orbit state;
- Motion may animate loader and DOM opacity/transform only;
- Lenis is not constructed and its stylesheet/script are removed from the
  homepage;
- no `setInterval`;
- page visibility and BFCache rules remain idempotent;
- rendering pauses while the document is hidden;
- rendering continues while visible because the courier, type ring, and
  ambient world are intentionally alive.

### Direct Manipulation

Drag uses pointer capture:

1. `pointerdown` records origin and pauses auto rotation;
2. `pointermove` updates a bounded angular offset;
3. `pointerup` releases capture and eases momentum to rest;
4. inactivity gradually resumes auto rotation;
5. `pointercancel` follows the same cleanup path.

The experience does not implement walking, collision, quests, free camera,
keyboard game controls, multiplayer, or audio.

## 9. Quality and Performance

High profile:

- device pixel ratio capped at `1.5`;
- draw calls no more than `75`;
- visible triangles no more than `140,000`;
- 60-frame average scene submission no more than `12ms` on the development
  desktop;
- planet fills the viewport through camera framing rather than higher geometry
  density.

Balanced profile:

- device pixel ratio capped at `1.25`;
- pointer parallax disabled;
- fewer clouds, foliage, outlines, and shadow detail;
- DOM ring remains fully interactive.

Static profile:

- no WebGL renderer;
- CSS miniature world and real product anchors remain;
- no loader loop, courier walk, auto rotation, parallax, or momentum.

## 10. Responsive and Accessibility Requirements

Required viewports:

- `375×812`
- `768×1024`
- `1024×768`
- `1440×900`

At every viewport:

- no body horizontal or vertical overflow;
- the planet and ring do not clip essential active content;
- `Enter Quant` remains visible;
- each interactive target is at least `44×44px`;
- focus is visible at a contrast of at least `3:1`;
- body text meets WCAG AA contrast;
- browser zoom remains enabled;
- touch gestures do not block system edge gestures;
- the ring can be traversed without pointer input;
- canvas content is decorative and `aria-hidden`;
- meaning and destinations are present in HTML.

`prefers-reduced-motion: reduce` stops loader choreography, automatic ring
rotation, courier walking, ambient orbit, and parallax. The six-link navigation
becomes a stable, readable arrangement.

## 11. Failure Handling

- WebGL unavailable: show the CSS miniature world and static six-link ring.
- WebGL initialization rejection: roll back renderer/world resources, dismiss
  the loader into the static experience, and keep links active.
- Context loss: switch to the static experience without a reload loop.
- JavaScript disabled: display the final static composition and all six links.
- Missing Motion global: use CSS state changes without hiding content.
- Page hidden: pause the render loop.
- BFCache restore: resize once and resume one loop without replaying intro.

## 12. File Boundaries

Expected retained/refactored units:

| Unit | Responsibility |
|---|---|
| `templates/index.html` | Fullscreen semantic surface and six rotating anchors |
| `static/css/home-quant-planet.css` | Fullscreen layout, loader, ring, fallback, responsive states |
| `static/js/home-quant-planet.js` | Entry state, events, lifecycle, progressive enhancement |
| `static/js/home-quant-planet-motion.js` | Pure intro/orbit/ring/courier state |
| `static/js/home-quant-planet-world.js` | Authored world and looping courier updates |
| `static/js/home-quant-planet-scene.js` | Camera, renderer, quality, diagnostics, disposal |

Expected retirement:

- `static/js/home-quant-planet-scroll.js`;
- homepage Lenis stylesheet/script references;
- scroll chapter markup and product grid;
- scroll normalization and Lenis-only tests.

Pinned Three.js, Motion, fonts, licenses, controllers, menu service, and all
subpages remain unchanged.

## 13. Testing Strategy

### Node

- intro state reaches ready and obeys reduced motion;
- orbit/ring values remain finite under pointer and drag extremes;
- ring order and selected item remain stable through wraparound;
- courier loop slows and idles at authored landmarks;
- no wheel-derived state exists;
- static profile does not request animation;
- visibility and BFCache retain one lifecycle owner;
- world construction rollback and normal disposal remain exactly-once.

### Java

- body/fullscreen classes and new selectors exist;
- Lenis is absent from the homepage;
- one `h1`, exact narrative, metrics, disclaimer, and six routes exist;
- rotating labels map to the exact destinations;
- the canvas is decorative;
- no scroll chapter/product-grid/old Observatory reference remains;
- all runtime assets are local.

### Browser

- entry sequence at desktop and mobile sizes;
- visual scale and ring layering;
- link click, keyboard order, focus pause, drag, touch, and pointer behavior;
- wheel does not move the page;
- no horizontal or vertical document overflow;
- reduced motion, static, WebGL failure, no JavaScript, and context loss;
- console, network, BFCache, and diagnostics budgets.

## 14. Acceptance Checklist

- [ ] Homepage is one fullscreen composition with no document scroll.
- [ ] The entry loader transitions into the world without a blank frame.
- [ ] The planet dominates 70–85% of the desktop viewport.
- [ ] Six rotating labels are real links to the approved product routes.
- [ ] Drag, touch, pointer parallax, arrow keys, and normal link activation work.
- [ ] Wheel input does not scroll, zoom, or rotate.
- [ ] Courier and three landmarks remain authored and visible.
- [ ] Minimal brand, status, hint, and `Enter Quant` UI remain legible.
- [ ] Reduced-motion and static modes are fully usable.
- [ ] Performance budgets remain unchanged.
- [ ] Existing subpages are unchanged.
- [ ] No Messenger code, asset, character, copy, sound, or branding is reused.

## 15. Non-Goals

- Copying proprietary Messenger assets or source code.
- Recreating Messenger quests, dialogue, multiplayer, wardrobe, sound, or
  character controls.
- Adding document scrolling, Lenis, scroll-jacking, or wheel zoom.
- Redesigning any subpage.
- Introducing React, Svelte, a frontend build pipeline, external 3D assets, or
  autoplay audio.
