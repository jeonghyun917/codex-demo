# Cinematic 3D Laboratory Homepage Design

## Summary

Replace only the King Yurina landing page with a full real-time 3D experience. The new first impression is a dark, cinematic research laboratory containing a physically believable metal-and-glass Quant engine. Scrolling moves the camera deeper into the laboratory, activates the engine, reveals Quant metric panels, and ends at a clear path into the existing Quant dashboard.

The Quant dashboard and all other product pages remain out of scope.

## Goals

- Create an immediately striking, premium first impression.
- Make the 3D scene feel like a physical laboratory rather than an abstract CSS effect.
- Use scroll as a reversible cinematic timeline instead of a conventional long page.
- Keep Signal, 20D Alpha, upside probability, and Risk visible as product-specific information.
- Preserve access to the existing navigation and `/quant` route.
- Deliver a usable fallback on mobile, reduced-motion, and WebGL-constrained devices.

## Non-goals

- Redesigning the Quant dashboard or other application pages.
- Adding external API calls to the page-request path.
- Introducing React, shadcn, or a new frontend build system.
- Producing an offline film-quality render or a background video.
- Making the landing-page metric values live market claims.

## Visual Direction

The scene uses a near-black laboratory, gunmetal structures, dark glass, polished steel, and controlled cobalt illumination. Warm practical lights may be used sparingly to separate surfaces, but cobalt remains the primary energy color.

The central Quant engine is a manufactured object with visible thickness, seams, supports, layered rings, glass chambers, cables, and a contained luminous core. It should feel installed in a real facility. The visual hierarchy is:

1. Quant engine and its activation sequence.
2. King Yurina brand and positioning.
3. Quant metric panels.
4. Navigation and dashboard call to action.
5. Laboratory details that establish scale and depth.

Typography and interface chrome remain restrained so the scene, not decorative UI, carries the impact.

## Cinematic Scroll Sequence

### Phase 1: Establishing shot

The visitor arrives at a wide view of the dark laboratory. The inactive Quant engine is visible at center, outlined by narrow cobalt rim lights. The brand name, short positioning line, navigation, and a scroll cue are readable without obscuring the scene.

### Phase 2: Approach

As the visitor scrolls, the camera moves toward the engine with controlled acceleration. Foreground rails, cables, structural frames, and glass partitions move at different speeds to establish physical depth. Practical lights respond subtly as the camera crosses the room.

### Phase 3: Activation

The engine core brightens, mechanical rings begin rotating at different rates, and contained particles enter stable orbital paths. Signal, 20D Alpha, upside probability, and Risk panels activate sequentially around the machine. Their motion is tied to scroll progress so reversing the scroll reverses the sequence.

### Phase 4: Data intake

The camera reaches the engine chamber. Data particles and luminous traces accelerate toward the core, creating the selected Data Vortex behavior inside a clearly physical machine rather than a free-floating abstract tunnel.

### Phase 5: Product handoff

At the closest safe camera position, the scene stabilizes and the `Explore the Intelligence` action becomes prominent. It links to `/quant`. The user can reverse-scroll through the full sequence without a hard scene reset.

## Page Architecture

The existing Spring Boot and Thymeleaf structure remains in place.

### HTML overlay

`index.html` owns semantic and interactive content:

- site navigation;
- brand heading and positioning copy;
- scroll guidance;
- four Quant metric panels;
- engine status;
- `/quant` call to action;
- accessible fallback content.

The overlay remains usable without WebGL. Decorative 3D elements are not duplicated as accessibility content.

### 3D scene

The existing local Three.js module remains the renderer dependency. The scene is organized into focused factories:

- laboratory shell and structural foreground;
- central Quant engine;
- physical and volumetric lighting;
- particle and data-flow systems;
- camera and scroll timeline;
- adaptive quality controller;
- fallback renderer.

The implementation should keep these responsibilities separate even if they remain in one JavaScript module initially. Scene objects expose a small update interface driven by normalized scroll progress and elapsed time.

### Scroll and input model

The page supplies a deliberate scroll range while the WebGL canvas remains visually fixed. Scroll position is normalized to a `0..1` cinematic timeline. Camera movement, light activation, ring rotation, panel visibility, and data acceleration derive from that single timeline.

Pointer movement adds restrained parallax and inspection motion but never overrides the scroll-directed camera. Resize and orientation changes recalculate camera framing without resetting progress.

## Metric Panels

The four panels are real HTML so text stays crisp, accessible, and responsive:

- Signal: `79 / 100`, Strong
- 20D Alpha: `+2.4%`, vs. benchmark
- Upside: `58%`, Probability
- Risk: `Medium`, Controlled

These values are presentation examples, not live financial data. Their copy should make that context clear where necessary. Panels appear to be laboratory instrumentation through glass, blur, controlled reflections, and positional motion. They must not be baked into a background image.

## Rendering Quality

The desktop experience targets a high-quality real-time result:

- physically based metal, glass, and coated surfaces;
- local reflections or environment-assisted reflections;
- depth-aware lighting and shadows;
- restrained bloom around energy elements;
- fog or volumetric light to reveal space;
- anti-aliased output with capped device pixel ratio;
- layered geometry that holds up during the camera approach.

Post-processing is used only where it materially improves depth and energy. The scene must avoid excessive bloom, unreadable glare, random particles, and continuous camera shake.

## Adaptive Performance

Quality is selected from device capability rather than screen width alone.

- High: full laboratory detail, shadows, post-processing, and dense particles.
- Medium: reduced shadow resolution, geometry detail, and particle count.
- Low: simplified lighting, reduced effects, and a capped render scale.

The renderer pauses when the page is not visible. Device pixel ratio is capped. Expensive allocations do not occur inside the animation loop.

Mobile uses the same art direction with simplified geometry and a shorter camera path. The central engine, brand, metrics, and dashboard action remain present.

## Fallbacks and Accessibility

- `prefers-reduced-motion` presents a stable hero composition with minimal engine rotation and immediate access to all content.
- WebGL initialization failure activates a lightweight canvas or layered CSS fallback using the same dark laboratory direction.
- Navigation, metrics, and the call to action remain normal HTML and keyboard accessible.
- The canvas is decorative and hidden from assistive technology.
- Text maintains readable contrast regardless of the brightest scene state.
- The page must not trap scroll or prevent normal browser navigation.

## Data and Application Boundaries

The controller continues to provide the existing DB-backed menu model. No external market API is called during landing-page rendering. The new page remains compatible with the existing Spring Security, Thymeleaf, and route structure.

The landing-page metrics remain explicit presentation copy unless a later, separately approved change supplies them from an existing cached database view.

## Testing and Validation

Automated coverage should verify:

- `/` renders successfully through the existing controller;
- the primary heading, metric labels, and `/quant` action are present;
- required local scene assets resolve without external API dependencies;
- fallback markup remains present when JavaScript is unavailable.

Visual validation should cover:

- desktop high-quality rendering;
- tablet and mobile framing;
- forward and reverse scroll continuity;
- reduced-motion behavior;
- WebGL failure fallback;
- keyboard navigation and text contrast;
- no overlap between navigation, brand copy, metric panels, and CTA;
- acceptable frame pacing on high, medium, and low quality modes.

The repository-standard Maven test command is used for regression verification:

`mvn.cmd "-Dmaven.repo.local=.m2/repository" test`

## Success Criteria

- The desktop first viewport reads as a dark physical research laboratory, not a background image or CSS-only abstraction.
- Scrolling creates a continuous, reversible approach into the Quant engine.
- The metal-and-glass machine retains believable volume and material response during the camera move.
- All four Quant panels appear at intentional moments and remain readable.
- The `/quant` handoff is clear and functional.
- Mobile, reduced-motion, and WebGL fallback experiences retain the same brand and core information.
- Existing non-home routes and DB-backed rendering behavior are unchanged.
