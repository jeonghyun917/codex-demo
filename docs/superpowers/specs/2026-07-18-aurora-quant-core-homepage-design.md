# Aurora Quant Core Homepage Design

## Summary

Replace the current procedural laboratory homepage with a premium, full-screen
Aurora Quant Core experience. The page uses a restrained real-time WebGL
aurora as its primary visual, with semantic HTML for the King Yurina brand,
navigation, Quant metrics, and the `/quant` handoff.

This design supersedes the literal metal laboratory and ring-machine visual
direction for the homepage. The existing Spring Boot, Thymeleaf, DB-backed menu,
security, and Quant dashboard remain unchanged.

## Goals

- Make the first viewport feel complete, cinematic, and premium rather than
  assembled from visible 3D primitives.
- Turn the aurora itself into the Quant intelligence core: layered, luminous,
  responsive, and controlled.
- Preserve Signal, 20D Alpha, Upside, and Risk as crisp product-specific HTML.
- Keep the interaction fluid and magnetic without making the page noisy,
  oversaturated, or difficult to read.
- Retain the previously approved scroll-into-the-core narrative in a shorter,
  denser sequence.
- Provide intentional mobile, reduced-motion, WebGL-failure, keyboard, and
  no-JavaScript experiences.

## Non-goals

- Introducing React, React Three Fiber, Tailwind, npm, or a frontend bundler.
- Rebuilding the Quant dashboard or any non-home route.
- Adding an external API call to the homepage request path.
- Presenting illustrative metric values as live market data.
- Recreating a literal research laboratory, manufactured machine, or
  photorealistic GLTF scene.
- Adding generalized animation or UI dependencies when the same behavior can
  be implemented in the existing local stack.

## Source Prompt Adaptation

The supplied Aurora Gradient prompt is translated to the current application
stack as follows:

| Prompt concept | Project implementation |
| --- | --- |
| React component | Thymeleaf template and focused ES modules |
| Tailwind utilities | Homepage-specific semantic CSS and custom properties |
| React state/effects | Cached DOM references and a single animation lifecycle |
| React Three Fiber | Existing local Three.js r165 module |
| Spring-based cursor response | Small damped-spring integrator in vanilla JavaScript |
| WebGL/GLSL mesh gradient | Full-screen `ShaderMaterial` on one render plane |
| Bloom | Controlled in-shader cursor and aurora radiance, without a post-processing dependency |
| Animated grain | Low-amplitude shader grain tied to elapsed time |
| Parallax | Layered noise fields moving at different spatial and temporal frequencies |
| Mobile degradation | Capability profile controlling DPR, layer count, motion, and pointer response |

## Visual Direction

### Palette

The base is a deep blue-black rather than pure black:

- Void: `#02040B`
- Elevated void: `#07101F`
- Primary violet: `#776BFF`
- Cobalt: `#4C6FFF`
- Cyan: `#32D8FF`
- Emerald: `#3DFFD0`
- Rose highlight: `#FF6FC8`
- Primary text: `#F7FAFF`
- Muted text: `#A7B4C8`
- Hairline: `rgba(205, 225, 255, 0.16)`

Color intensity is clamped in the shader. Cyan and violet carry the visual
identity, emerald marks positive signal energy, and rose appears only as a
small interference highlight. No full-screen rainbow sweep or neon cyberpunk
styling is used.

### Composition

The first viewport contains:

1. A minimal floating navigation capsule with the King Yurina wordmark,
   DB-backed menu links, and a Dashboard action.
2. A centered glass intelligence panel with the status label
   `QUANT INTELLIGENCE / AURORA CORE ONLINE`.
3. The primary statement `See the signal beneath the noise.`
4. Supporting copy that connects the visual to Signal, Alpha, Upside, and Risk.
5. One primary `Enter Quant Intelligence` action linking to `/quant`.
6. A connected Quant metric rail that reads as precision instrumentation,
   not four unrelated floating cards.
7. A concise disclosure that the displayed outputs are illustrative.

The glass panel remains translucent enough for the aurora to illuminate its
edges. It uses one controlled backdrop blur, a bright top-edge reflection, a
subtle internal radial highlight, and a soft shadow. Large opaque rectangles,
thick glowing borders, and exaggerated rounded corners are avoided.

### Typography

- Display: a local high-contrast serif stack led by Georgia.
- UI and body: the local system sans stack led by Segoe UI.
- Data labels and numeric annotations: the system monospace stack.

No font is fetched at runtime. The hierarchy and editorial contrast come from
the local type stacks, scale, spacing, and weight rather than a remote asset.

The headline uses balanced line wrapping, a compact line height, and no
gradient text. Data labels are uppercase with moderate tracking; long body copy
is avoided.

## Aurora Renderer

The WebGL renderer uses one full-screen plane and a custom shader rather than a
collection of visible geometric objects.

The fragment shader produces:

- several broad noise-warped luminous bands;
- a dark atmospheric base with controlled additive color mixing;
- three apparent depth layers moving at different speeds;
- a soft cursor radial field;
- local brightness and band expansion near the pointer;
- restrained in-shader radiance around bright regions;
- subtle time-varying film grain and edge vignette.

The aurora never tracks the cursor exactly. Pointer position and energy travel
through a damped spring before reaching shader uniforms. Pointer speed briefly
raises local energy, then decays smoothly. When the pointer leaves, the spring
returns to center without snapping.

The renderer:

- uses `requestAnimationFrame`;
- allocates no per-frame objects;
- caches all DOM references;
- caps device pixel ratio by quality tier;
- pauses rendering when the page is hidden or outside the viewport;
- recalculates uniforms on resize without resetting scroll state;
- uses a transparent canvas over a matching CSS fallback;
- makes no network request during the animation lifecycle.

## Cinematic Scroll Sequence

The page uses approximately `280svh` on desktop and a shorter range on mobile.
The viewport remains sticky while one normalized `0..1` progress value drives
the scene. Browser scrolling is never trapped.

### Phase 1 — Signal field (`0.00..0.32`)

The headline, primary action, and main glass panel are fully usable in the
first viewport. Aurora layers drift slowly behind the content. Pointer response
is available immediately.

### Phase 2 — Quant resolution (`0.32..0.74`)

The camera illusion moves into the aurora by changing shader scale, band depth,
and parallax speed rather than moving literal geometry. The hero panel becomes
slightly more transparent and compact. Signal, Alpha, Upside, and Risk resolve
sequentially along the connected metric rail.

### Phase 3 — Intelligence handoff (`0.74..1.00`)

The aurora converges into a calm luminous aperture. Visual noise and motion
reduce, the product statement becomes concise, and the `/quant` action gains
stronger contrast. Reverse scrolling reverses every progress-derived state.

Time-based ambient drift continues independently but remains subtle enough that
scroll direction is always legible.

## Content and Interaction

Primary copy:

- Status: `QUANT INTELLIGENCE / AURORA CORE ONLINE`
- Heading: `See the signal beneath the noise.`
- Supporting copy:
  `Signal, alpha, upside and risk—resolved into one adaptive market view.`
- Resolved-state copy: `Market intelligence, resolved.`
- Primary action: `Enter Quant Intelligence`
- Scroll cue: `Scroll to resolve`

Metric presentation values remain:

- Signal: `79 / 100`, `Strong`
- 20D Alpha: `+2.4%`, `vs. benchmark`
- Upside: `58%`, `Probability`
- Risk: `Medium`, `Controlled`

The primary action is visible above the fold and remains keyboard accessible.
It gains emphasis at the final scroll phase but is never withheld from the
visitor.

All interactive targets are at least `44px` high. Hover, focus-visible, and
pressed states are distinct. Focus indication does not depend on the animated
canvas.

## Responsive and Adaptive Quality

### Desktop

- Full shader layer count and pointer bloom.
- DPR cap based on measured capability, never above `1.75`.
- Full `280svh` narrative.

### Tablet and mobile

- Lower DPR cap and fewer shader octaves.
- Reduced blur radius and grain amplitude.
- No hover-dependent information.
- Touch movement does not create a persistent pointer glow.
- Metric rail becomes a compact two-column grid while retaining reading order.
- Navigation collapses without hiding the primary Dashboard action.
- Scroll range is shortened to approximately `220svh`.

### Reduced motion

- A composed, mostly static aurora frame is rendered.
- Pointer and continuous parallax response are disabled.
- All content and metrics are immediately visible.
- The page uses a normal `100svh` hero instead of an extended cinematic scroll.

### WebGL or JavaScript failure

- Layered CSS radial gradients provide the same dark violet/cyan/emerald art
  direction.
- Semantic content, menu links, metrics, disclosure, and `/quant` action remain
  visible and functional.

## Accessibility

- The WebGL canvas is decorative and carries `aria-hidden="true"`.
- The page has one clear `h1` and semantic navigation, main, section, and list
  structures.
- Text contrast is evaluated against both the darkest and brightest aurora
  states.
- `prefers-reduced-motion` removes the extended scroll choreography.
- Keyboard focus is visible at all times.
- The page does not override browser scrolling, pointer behavior, or history.
- Status motion is not the only carrier of meaning; the same information is
  present as text.

## Application Boundaries

- `GET /` continues through the existing controller.
- `MenuService.mainMenus()` remains the source for homepage navigation.
- No live market API or new database query is introduced.
- `/quant` and other application pages are unchanged.
- Existing security behavior remains unchanged.
- Three.js continues to be served locally.

## Testing and Validation

Automated contract coverage verifies:

- the Aurora canvas and CSS fallback are present;
- the heading, status, four metrics, disclosure, and `/quant` action exist;
- menu items remain Thymeleaf-driven;
- the page loads only local homepage CSS and JavaScript;
- the renderer references the local Three.js module;
- the shader exposes time, pointer, energy, resolution, scroll, and quality
  uniforms;
- reduced-motion and WebGL fallback hooks are present;
- legacy laboratory, engine, and vortex modules are no longer loaded.

Browser validation covers:

- desktop viewports at `1440×900` and `1920×1080`;
- tablet at `768×1024`;
- mobile at `390×844`;
- pointer movement, pointer leave, resize, forward scroll, and reverse scroll;
- reduced-motion emulation;
- WebGL fallback;
- keyboard traversal and visible focus;
- no content overlap at the beginning, middle, and end of the timeline;
- stable frame pacing and bounded device pixel ratio.

The repository-standard regression command remains:

`mvn.cmd "-Dmaven.repo.local=.m2/repository" test`

## Success Criteria

- The first viewport reads as one finished Aurora Quant product scene, not a
  collage of geometric primitives.
- Pointer interaction feels magnetic, soft, and physically damped.
- Aurora color remains sophisticated and text remains readable at peak energy.
- Scroll creates a dense, reversible three-act journey without empty stretches.
- Signal, Alpha, Upside, and Risk remain crisp and product-relevant.
- The primary `/quant` action is immediately available and becomes the clear
  final handoff.
- Mobile, reduced-motion, no-JavaScript, and WebGL-failure experiences are
  intentional rather than broken reductions.
- Existing DB-backed rendering and all non-home routes remain unchanged.
