# Vercel-Inspired Quant Observatory Homepage Design

**Date:** 2026-07-24
**Status:** Approved design direction
**Scope:** Public homepage only (`/`)
**Supersedes:** Earlier Aurora, cinematic laboratory, and Brik homepage directions

## 1. Product Intent

The homepage should make King Yurina feel like a credible, premium quant-intelligence product before a visitor enters the working application. It should attract attention through restraint, material realism, and controlled motion rather than filling the screen with decorative 3D geometry.

The approved direction combines:

- Vercel's monochrome restraint, oversized typography, asymmetric composition, and short copy;
- a distinctive King Yurina "Quant Core" instead of Vercel's triangle;
- Signal, Alpha, and Risk language from the product;
- a cinematic scroll sequence that remains native, interruptible, and accessible.

This is an adaptation of design principles, not a copy of Vercel's assets, logo, source code, exact layout, or brand language.

## 2. Success Criteria

The page succeeds when:

1. The first viewport reads as premium fintech/quant software within three seconds.
2. One highly finished physical object is the visual focus; no element looks like placeholder 3D.
3. The primary action, **Enter Quant**, is obvious without competing calls to action.
4. Scrolling reveals a clear three-part narrative: **Observe → Resolve → Act**.
5. Motion is smooth on capable desktop devices and degrades cleanly on mobile, low-power hardware, reduced-motion settings, WebGL failure, and JavaScript-disabled browsers.
6. Existing Quant, Stocks, Heatmap, ETF, Atelier, and Backtest destinations remain reachable.
7. No React runtime or external market API is introduced.

## 3. Chosen Direction

### Quant Observatory

The page uses a near-black canvas with a single machined-metal optical instrument at its center. The object resembles a precision aperture or analytical sensor: layered brushed-metal rings, an iris-like core, dark glass, restrained cobalt emission, fine machining marks, and physically plausible reflections.

The instrument is not a literal laboratory room. Constraining the scene to one hero artifact keeps the visual believable, gives lighting and materials enough attention, and avoids the unfinished appearance of the earlier full-lab experiment.

Recommended visual balance:

- **70% Vercel-inspired restraint:** black space, typography, grid discipline, minimal copy;
- **30% King Yurina identity:** Quant Core, cobalt signal light, market metrics, analytical narrative.

## 4. Visual System

### Color

Use semantic tokens and keep the palette narrow:

| Role | Value | Purpose |
|---|---:|---|
| Deep background | `#030405` | Main page canvas |
| Elevated background | `#090B0E` | Subtle section and navigation separation |
| Primary foreground | `#F5F7FA` | Headlines and primary controls |
| Muted foreground | `#969DA8` | Supporting copy |
| Hairline | `rgba(255,255,255,.12)` | Grid and dividers |
| Core metal | `#9298A0` | PBR metal base |
| Signal accent | `#6EA8FF` | One controlled brand accent |
| Positive data | `#86E3B0` | Semantic positive state only |
| Negative data | `#FF8A8A` | Semantic negative state only |

The accent should appear as emitted light, focus, and active data—not as broad decorative gradients.

### Typography

- Display and UI: self-hosted **Instrument Sans**.
- Data labels and values: self-hosted **IBM Plex Mono**.
- Fallbacks: system sans-serif and system monospace.
- Hero headline: weight 400, tight tracking, and `font-size: clamp(3.6rem, 7vw, 7.75rem)`.
- Body copy: minimum 16px with readable line height.
- Numbers use tabular figures to prevent visual movement.

The typography should feel engineered and editorial. Heavy all-caps poster styling and luxury serif headlines are excluded.

### Geometry

- Mostly square or gently rounded controls; maximum 10px radius.
- Hairline borders instead of floating glass cards.
- No neon wire cages, floating black ribbons, random particle fields, or stacks of translucent panels.
- Grid lines may be used sparingly to create measurement and calibration cues.

## 5. Page Structure and Copy

### 5.1 Navigation

A fixed, transparent-to-solid navigation bar contains:

- King Yurina mark and name;
- compact access to existing product destinations;
- one high-contrast **Enter Quant** action.

The navigation becomes slightly more opaque after the hero begins to scroll. Mobile uses a compact menu without hiding the primary Quant route.

### 5.2 Hero — Observe

The first viewport contains:

- eyebrow: `QUANT INTELLIGENCE / SYSTEM ONLINE`;
- headline: `Market intelligence, resolved.`;
- supporting line: `Observe the market. Resolve the signal. Act with context.`;
- primary CTA: `Enter Quant`;
- a secondary text link to explore the story, visually subordinate;
- the central Quant Core WebGL scene;
- a restrained right-side index: `01 Observe / 02 Resolve / 03 Act`.

The core begins mostly in shadow. Pointer movement changes the highlight by only a few degrees; it does not spin the object freely.

### 5.3 Scroll Chapter — Resolve

A native tall-scroll section keeps the Quant Core visually anchored while the camera approaches it. The outer shell opens in a controlled sequence and reveals three analytical layers:

- **Signal** — strength and directional context;
- **Alpha** — performance relative to a benchmark;
- **Risk** — uncertainty and exposure context.

The panels show `Signal 79/100`, `20D Alpha +2.4%`, and `Risk Medium` with an explicit `Illustrative model view` label. The homepage must not imply that these decorative values are live or authoritative.

### 5.4 Scroll Chapter — Act

The physical instrument transitions into a flat, crisp product preview. This is a designed representation of the real Quant interface, not a screenshot pretending to be live.

The copy focuses on the action path:

1. Scan the market.
2. Resolve competing signals.
3. Open the authoritative dashboard.

The section ends with a second **Enter Quant** CTA.

### 5.5 Product Access

Below the cinematic sequence, existing products appear in a restrained editorial list instead of a colorful card mosaic:

- Quant Intelligence
- Stocks
- Market Heatmap
- ETF Radar
- Strategy Atelier
- Signal Backtest

Each row has a short outcome-oriented description, a consistent arrow, keyboard focus, and a subtle hover response.

### 5.6 Final CTA and Footer

Final headline: `Turn market noise into a decision.`
Primary action: `Enter Quant`

The footer retains core navigation, contact access, and a back-to-top link. It should be visually quiet.

## 6. Motion Design

### Principles

- Motion explains progression and depth; it is not constant decoration.
- The user's scroll remains native and immediately interruptible.
- Only transform, opacity, shader uniforms, and camera parameters animate continuously.
- The hero object and one text group are the primary animated elements in each viewport.
- UI micro-interactions stay within 150–300ms; cinematic scroll progress is tied directly to scroll position.

### Lenis

Lenis 1.3.25 will be pinned and self-hosted under the static vendor directory with its license. It provides smoothing while preserving native scrolling semantics.

- anchor navigation is enabled;
- nested horizontal navigation uses explicit Lenis-prevention attributes;
- no nested vertical smooth-scroll container is introduced;
- Lenis is disabled when reduced motion is requested;
- no CDN dependency is used at runtime.

### Motion

Motion 12.29.2 will be pinned and self-hosted with its license. Its vanilla JavaScript APIs coordinate:

- hero copy entrance;
- chapter label transitions;
- metric-panel reveals;
- navigation state transitions;
- scroll-linked progress values.

Motion must not duplicate the WebGL render loop or cause layout thrashing.

### Three.js

The existing self-hosted Three.js r165 module renders only the Quant Core scene. It does not render page text, navigation, or product lists.

- a single render coordinator owns timing;
- pixel ratio is capped at 1.5;
- pointer input is damped and bounded;
- rendering pauses when the hero/scene is not visible or the page is hidden;
- materials use physically based metal/roughness values;
- lighting uses a restrained key, rim, and cobalt core light;
- bloom, if used, is subtle and limited to emissive surfaces;
- quality profiles select geometry detail, shadows, and post-processing based on viewport and hardware.

## 7. Technical Architecture

The implementation remains Spring Boot + Thymeleaf + CSS + vanilla ES modules.

Proposed boundaries:

| Unit | Responsibility |
|---|---|
| `index.html` | Semantic content, links, accessible fallback order |
| `home-observatory.css` | Tokens, layout, responsive states, static fallback |
| `home-observatory.js` | Page orchestration and progressive enhancement |
| `home-observatory-scroll.js` | Lenis lifecycle and normalized scroll progress |
| `home-observatory-motion.js` | DOM motion timelines and reduced-motion behavior |
| `home-observatory-scene.js` | Three.js scene, materials, camera, quality profile |
| local vendor assets | Pinned Lenis, Motion, Three.js, licenses |
| local font assets | Self-hosted licensed fonts with `font-display` fallback |

The existing homepage is replaced, while subpages and their styles remain untouched. The old `home-brik` assets are removed only after tests confirm they have no remaining consumers.

No external API call is added to the homepage request path. Existing DB-backed product pages remain authoritative.

## 8. Progressive Enhancement and Failure Handling

The semantic HTML, headline, CTA, navigation, and product links render before JavaScript.

Fallback order:

1. Full WebGL + Lenis + Motion on capable devices.
2. Reduced WebGL profile on mobile or low-core devices.
3. Static rendered core treatment when WebGL initialization fails.
4. Static CSS page with all content and links when JavaScript is unavailable.
5. No scroll smoothing and no parallax under `prefers-reduced-motion: reduce`.

Failures are silent from the visitor's perspective. The page must never show a blank hero, loading spinner that does not resolve, horizontal overflow, or inaccessible content hidden at zero opacity.

## 9. Responsive Behavior

Required validation widths:

- 375px
- 768px
- 1024px
- 1440px

Desktop uses the asymmetric headline/core/chapter-index composition. Mobile changes to:

- headline first;
- core centered below or behind the copy with safe contrast;
- chapter index reduced to a compact progress label;
- simplified camera path and post-processing;
- product links in one column;
- minimum 44×44px touch targets.

Landscape mobile must remain usable without placing the CTA under browser chrome.

## 10. Accessibility

- Preserve a visible skip link.
- Use one `h1` and sequential headings.
- Decorative WebGL canvas is hidden from assistive technology.
- Every interactive element has visible keyboard focus.
- Text contrast meets WCAG AA.
- Data meaning is never communicated by color alone.
- Primary navigation and CTA are usable without hover.
- Reduced-motion mode removes smoothing, camera travel, parallax, and staggered entrances.
- No content is gated behind animation completion.

## 11. Testing Strategy

### Automated

- Extend the homepage template contract test for semantic landmarks, CTA links, fallback copy, asset references, and reduced-motion hooks.
- Add JavaScript unit tests for quality-profile selection, scroll-progress normalization, reduced-motion selection, and render-loop continuation rules.
- Verify removed Brik assets have no references before deletion.
- Run the full Maven suite with the workspace-local repository.

### Browser

- Confirm initial, mid-scroll, and final states at all required breakpoints.
- Test keyboard navigation and focus order.
- Test reduced motion, disabled JavaScript, WebGL failure, and page visibility changes.
- Check for console errors, blank states, body horizontal scroll, and stuck Lenis state.
- Confirm all destination URLs.

### Performance

- Reserve hero dimensions to avoid layout shift.
- Lazy-load noncritical 3D and below-fold assets where possible.
- Keep frame work within the 16ms target on the desktop quality profile.
- Pause scene rendering outside its visible range.
- Avoid third-party runtime requests.

## 12. Acceptance Checklist

- [ ] The homepage uses the approved monochrome Quant Observatory direction.
- [ ] The Quant Core is the only dominant 3D object and has convincing metal/glass materials.
- [ ] The page does not resemble the previous unfinished laboratory scene.
- [ ] `Observe → Resolve → Act` is readable with or without motion.
- [ ] Signal, Alpha, and Risk are present and clearly labeled.
- [ ] Enter Quant is the sole primary CTA.
- [ ] Lenis, Motion, Three.js, and fonts are pinned/self-hosted with licenses.
- [ ] React is not introduced.
- [ ] Existing subpages are unchanged.
- [ ] Reduced-motion, mobile, WebGL-failure, and no-JavaScript fallbacks work.
- [ ] Automated tests and browser verification pass.

## 13. Non-Goals

- Redesigning Quant or other subpages.
- Building a navigable full 3D laboratory.
- Adding authentication, portfolio state, or new backend analytics.
- Loading live market data from an external API.
- Copying Vercel's triangle, logo, brand copy, source code, or proprietary assets.
- Adding audio, forced autoplay video, custom cursors, or scroll-jacking.
