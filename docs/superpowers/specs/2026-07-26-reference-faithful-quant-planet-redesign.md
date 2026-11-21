# Reference-Faithful Quant Planet Redesign

Date: 2026-07-26  
Status: Approved direction, written specification pending final user review  
Route: `/` only

## Objective

Replace the current macro-cropped Quant Planet composition with a coherent
fullscreen artwork that delivers the same immediate visual hierarchy and
interaction rhythm as the supplied Messenger reference while using only
original Quant AI branding, copy, geometry, and product routes.

The first stable frame must read in this order:

1. one complete miniature planet silhouette;
2. one dominant central product title;
3. one clear primary action;
4. subtle surrounding world detail and navigation affordances.

The result must not look like a website with a 3D background. It must look like
one intentional, centered interactive title artwork.

## Reference Parity Contract

The supplied Messenger experience is the behavioral and compositional
reference. The Quant homepage must match its perceived:

- first-entry staging and reveal rhythm;
- complete centered-world scale and surrounding negative space;
- slow continuous world rotation;
- central title scale, depth, and selection behavior;
- lower-center primary-button size and placement;
- drag response, selection snapping, and idle-resume rhythm.

Parity is evaluated by synchronized side-by-side recordings, not by the
presence of similarly named CSS classes. Over a ten-second comparison:

- world angular displacement should remain within approximately `8%` of the
  reference;
- the complete world silhouette should remain within approximately `5%` of the
  reference's viewport-relative width and height;
- central title and lower button bounding boxes should remain within
  approximately `5%` of the reference's viewport-relative placement.

Quant AI uses original geometry, lettering, colors, copy, and routes. No
reference code, model, texture, sound, logo, or other proprietary asset is
copied.

## Diagnosis of the Rejected Composition

The rejected implementation fails for structural reasons:

- The camera's visible vertical half-height is smaller than the authored planet
  plus its upper landmarks, so the world is guaranteed to crop.
- The square canvas fills approximately `82vh`, but the camera looks near the
  planet center instead of framing the complete world bounds.
- Successful WebGL initialization visually hides the semantic headline, even
  though the reference makes its central title the primary focal layer.
- Product routes are presented as small detached pill buttons instead of a
  large typographic object integrated with the world.
- The world's most detailed landmarks sit outside or near the crop boundary,
  while the visible foreground is mostly empty land surface.

The correction therefore changes camera fitting, world composition, typography,
and navigation presentation together. A CSS scale tweak alone is not accepted.

## Visual Direction

### Composition

- Background: a clean turquoise field with restrained atmospheric marks and
  no horizontal landscape bands.
- Planet: a complete, compact, irregular circular silhouette centered slightly
  above the viewport midpoint.
- Desktop world envelope: approximately `58vh` to `68vh` tall and never
  cropped.
- Mobile world envelope: approximately `68vw` to `82vw`, with the full
  silhouette still visible.
- Negative space: at least `8vw` or `6vh` around the planet silhouette,
  whichever is visually larger.
- Header: minimal brand at top left only; no competing top-right CTA.
- Primary action: exactly one strong yellow, outlined `ENTER QUANT` button
  centered below the planet and linked to `/quant`.

### Color and Rendering

- Keep the current Quant palette: turquoise, paper, coral, blue, ink, foliage.
- Increase ink-outline visibility and local contrast around silhouettes.
- Use toon shading, stepped light, and restrained paper-like texture or
  dither—not glossy photorealism.
- Increase perceived detail through silhouette density and layering, not
  excessive polygon count.
- Do not copy reference textures, geometry, characters, logos, lettering, or
  proprietary assets.

## Camera and World Framing

### Bound-Based Camera Fit

The renderer must frame the authored world rather than rely on a hard-coded
camera distance.

After world construction:

1. compute a `THREE.Box3` around the complete visible world;
2. derive its bounding sphere and vertical extent;
3. include a framing margin of approximately `1.18`;
4. calculate the required camera distance from vertical FOV and aspect ratio;
5. use the stricter horizontal or vertical fit;
6. look at the bounds center, biased upward only enough to keep the lower planet
   and upper landmarks visually balanced.

Pointer parallax and drag orbit may adjust yaw and pitch, but may not change the
fit distance enough to crop the world. Every permitted camera pose must retain
the complete primary silhouette.

### World Silhouette

- Keep the existing original planet, facilities, Courier, route, foliage, and
  clouds as the starting asset set.
- Rebalance the land sphere toward a rounder miniature-planet form.
- Move facilities and major props closer to the visible perimeter so their
  silhouettes remain readable at a distance.
- Add original low-poly debris, signal fragments, small structures, rocks, and
  cloud clusters around the outer contour.
- Use instancing and shared materials/geometries for repeated details.
- Preserve existing draw-call, triangle, rollback, and exactly-once disposal
  safeguards.
- Empty land surface must not dominate the visible front hemisphere.

## Central Product Typography

### Primary Title

The central typography is the dominant foreground object and remains visible
after WebGL initialization.

- It occupies approximately `45%` to `62%` of the planet width on desktop.
- It uses large, heavy, block-like Quant typography with an ink outline and
  layered extrusion/shadow.
- It is rendered as a DOM overlay with perspective and layered depth so it
  remains crisp, accessible, and independent of GPU font support.
- It uses two or three balanced lines depending on the selected product.
- It must visually overlap the planet while staying legible against every
  camera pose.

The six title states are:

| Route | Primary title | Supporting line |
|---|---|---|
| `/quant` | `QUANT` / `INTELLIGENCE` | `Observe · Resolve · Act` |
| `/stocks` | `STOCK` / `SIGNALS` | `Market field` |
| `/stocks/heatmap?index=SP500` | `MARKET` / `HEATMAP` | `S&P 500 field` |
| `/etfs` | `ETF` / `RADAR` | `Allocation field` |
| `/atelier` | `STRATEGY` / `ATELIER` | `Research field` |
| `/signals/backtest` | `SIGNAL` / `LAB` | `Backtest field` |

All copy is original Quant AI copy. The title itself is a native anchor for the
selected route.

### Secondary Route Navigation

- Remove the six detached pill buttons.
- Maintain six native anchors in the DOM for accessibility and keyboard use.
- The selected route becomes the dominant central title.
- Neighboring routes appear as small depth-faded labels near the left and right
  edges of the planet, indicating rotational direction.
- Arrow keys and drag rotate selection with deterministic wrapping.
- Focus and hover bring a route to the central title before activation.
- Pressing Enter or clicking the central title navigates to that route.

## Motion and Interaction

### Entry

- Start with a short full-field Quant mark.
- Split or lift the mark while the complete planet scales from approximately
  `0.82` to `1`.
- Reveal the central title as the world settles.
- The loader must dismiss within `4000ms` under every runtime outcome.
- BFCache return must not replay the intro.
- Final intro phase durations are calibrated against a synchronized reference
  recording; the `4000ms` ceiling remains absolute even if the reference stalls.

### Continuous Motion

- The planet has restrained ambient rotation.
- The Courier continues its Observe → Resolve → Act loop.
- Small clouds, signal fragments, and peripheral props create subtle secondary
  motion.
- Product selection advances slowly and automatically when the user is idle.
- World and product-selection angular velocities are calibrated against the
  reference recording and stored as named timing constants.
- Automatic product rotation pauses on hover, focus, press, drag, or reduced
  motion.

### Direct Manipulation

- Horizontal drag rotates the world and product selection together.
- Selection snaps to the nearest of the six routes after release.
- Pointer movement adds bounded parallax without changing the framing fit.
- Wheel input performs no scroll, zoom, or rotation.
- Touch uses the same horizontal drag model and retains primary-pointer
  ownership.

## Responsive Behavior

### Desktop and Landscape Tablet

- Complete planet centered slightly above mid-screen.
- Central title remains within the planet's safe central region.
- The single `ENTER QUANT` action is centered below the planet.
- Side route labels remain inside viewport bounds.

### Mobile and Portrait Tablet

- Complete planet remains visible; it must not become a macro crop.
- Central title scales down by line length while preserving the same hierarchy.
- Supporting copy may reduce to one short line.
- Side route labels move closer to the planet or become compact previous/next
  labels.
- All interactive targets remain at least `44×44px`.

Required validation viewports:

- `375×812`
- `768×1024`
- `1024×768`
- `1440×900`

No viewport may have horizontal or vertical document overflow.

## Accessibility and Fallbacks

- The selected central title is a native anchor.
- All six routes remain keyboard-focusable native anchors.
- A semantic `h1`, narrative, and illustrative metrics remain in the document.
- Visually hidden content must not remove the central visible title.
- `:focus-visible` must be obvious against the turquoise and planet colors.
- Reduced motion stops loader choreography, auto selection, ambient orbit,
  Courier walking, parallax, and momentum while preserving explicit route
  navigation.
- WebGL failure, context loss, missing Motion, and no JavaScript show a complete
  static planet, visible central title, primary action, and usable six-route
  navigation.

## Performance Constraints

- Pixel ratio remains capped at `1.5`.
- High-profile draw calls remain at or below `75`.
- Visible triangles remain at or below `140000`.
- Loopback average scene submission remains at or below `12.0ms`.
- Added silhouette details use instancing and existing shared geometry where
  possible.
- No remote runtime assets, external API calls, npm frontend runtime, React,
  Svelte, multiplayer, game controls, or autoplay audio.

## Acceptance Criteria

The redesign is accepted only when all of the following are true:

1. A comparison capture shows the complete planet silhouette rather than a
   cropped land surface.
2. The central block title is the first visual focus at all four viewports.
3. No detached pill-button constellation remains.
4. The selected title and side labels navigate to the exact six product routes.
5. Drag changes both world orientation and selected route, then snaps cleanly.
6. The lower primary action is centered and visually prominent.
7. Upper landmarks, lower planet edge, and surrounding silhouette details are
   simultaneously visible.
8. No runtime state hides the visible central title.
9. Static and reduced-motion fallbacks preserve the same overall composition.
10. Automated tests, Maven tests, render budgets, and browser screenshots are
    reviewed before the result is described as complete.

## Non-Goals

- Pixel-for-pixel copying of the reference.
- Reusing or extracting reference source code or assets.
- Rebuilding subpages.
- Introducing a general-purpose game engine or asset pipeline.
- Adding quant data fetching to the homepage request.
- Increasing visual density by uncontrolled polygon growth.

## Verification Strategy

- Pure tests for camera-fit calculations, selection snapping, route wrapping,
  intro deadline, and capability selection.
- World tests for bounds, contour density, budgets, rollback, and disposal.
- Template contracts for native anchors, central title states, exact routes,
  primary action, fallback markup, and no-scroll behavior.
- Browser capture comparison at all required viewports.
- Browser interaction checks for loader, drag, touch, keyboard, hover/focus,
  reduced motion, no JavaScript, missing Motion, WebGL failure, and context
  loss.
- Console/network inspection and loopback diagnostics for render time, draw
  calls, and triangles.
