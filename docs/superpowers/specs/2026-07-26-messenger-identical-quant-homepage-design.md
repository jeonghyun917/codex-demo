# Messenger-Identical Quant Homepage Design

Date: 2026-07-26  
Branch: `codex/quant-planet-homepage`  
Status: Approved

## Objective

Replace the current six-route Quant Planet homepage with a single-purpose,
fullscreen Three.js experience that matches the supplied
`https://messenger.abeto.co/` reference as closely as practical in visible
composition, entry choreography, world motion, typography, interaction rhythm,
and responsive behavior.

The only intentional product difference is the lower yellow `ENTER` action:
it navigates to `/quant`.

## Reference Contract

The supplied screenshot and visible reference experience are the primary
art-direction source. The implementation must reproduce the following:

- a flat turquoise fullscreen field with no page scroll;
- one centered, nearly viewport-filling miniature planet;
- the reference planet's irregular silhouette, dense Japanese-inspired
  architecture, mountains, trees, pipes, antennas, water band, and floating
  debris distribution;
- the same central three-line Korean block-letter wording shown in the
  reference, preserving its visible glyph sequence;
- off-white extruded letter faces, dark graphite outlines, side depth, and
  object-like shadows;
- a single yellow, dark-outlined lower `ENTER` button;
- the same visual hierarchy: typography first, planet second, action third;
- first-entry loading, reveal, scale, and settling rhythm equivalent to the
  reference;
- equivalent idle rotation speed, pointer response, drag response, and release
  settling.

No navigation header, product carousel, route labels, metric cards, supporting
copy, or secondary actions remain on the homepage.

## Original Asset Boundary

The implementation may reproduce the reference's visible composition and exact
central wording, but it must not download, embed, or copy the reference site's
private source code, proprietary 3D models, textures, shaders, or font files.

All planet geometry, materials, outlines, typography meshes, particles, and
motion code are rebuilt inside this repository. The supplied screenshot is a
visual measurement reference, not a runtime dependency.

## Scene Architecture

### 1. Fullscreen shell

`index.html` contains only:

- the accessible main experience container;
- the intro/loading layer;
- one Three.js canvas and one static fallback composition;
- one central three-line title;
- one `ENTER` anchor pointing to `/quant`;
- a visually hidden semantic heading and description.

The document remains `100dvh`, `overflow: hidden`, and has no wheel-driven page
navigation.

### 2. Reference world

The Three.js world is rebuilt as one authored scene graph with:

- an irregular rounded planet core rather than a perfect sphere;
- a central water channel and stepped terrain;
- upper mountain and temple silhouette;
- left-side red industrial machinery and pipe cluster;
- right-side antenna, cable, and utility structures;
- lower dense tree canopy;
- scattered houses, rocks, floating fragments, and atmosphere marks;
- ink-like outlines and restrained cel-shaded materials.

Repeated trees, rocks, roof pieces, and debris use instancing. Large landmarks
remain separate authored groups so their silhouettes can be tuned directly
against the reference.

### 3. Central title

The reference's three-line Korean wording is rebuilt as a dedicated title
object, not as six selectable links.

The title:

- stays centered and camera-facing;
- occupies the same world-relative width and vertical stack as the reference;
- has thick extrusion and dark edge lines;
- remains visually in front of the rotating planet;
- receives only subtle settle/parallax movement and never becomes navigation.

The title silhouette is traced and reconstructed from the supplied visual
reference so the visible glyph sequence remains unchanged without importing
the reference font.

### 4. Primary action

One yellow `ENTER` anchor sits below the planet/title stack:

- exact destination: `/quant`;
- minimum target: `44x44px`;
- dark outline and short extrusion shadow;
- hover/focus: small lift and shadow compression;
- activation remains native anchor navigation.

No other homepage element links to product pages.

## Motion and Entry

### Intro sequence

The intro uses one finite timeline:

1. turquoise field and compact loader mark;
2. loader mark separates and clears;
3. complete planet scales and settles into the center;
4. three-line title resolves from depth into its final front-facing position;
5. yellow `ENTER` action rises into place;
6. idle world rotation starts without a visible phase jump.

The complete final composition must appear before a four-second hard deadline,
independent of animation-frame availability.

### Idle and pointer motion

- The planet rotates continuously at the reference's measured apparent speed.
- Pointer movement produces restrained camera/world response on capable desktop
  devices.
- Primary drag temporarily owns rotation and releases into a short inertial
  settle.
- Secondary pointers do not affect the scene.
- Wheel input does not navigate, zoom, or scroll the page.
- Touch behavior is explicitly owned by the canvas interaction surface.

There is no product snapping, sector selection, title cycling, or idle route
promotion.

## Responsive Composition

The same composition is preserved at all viewports:

- desktop landscape: planet/title stack fills roughly the central 75–85% of
  the viewport height;
- tablet portrait: planet is width-bound and remains fully visible;
- mobile portrait: title remains dominant, planet edges remain readable, and
  `ENTER` stays above the lower safe area;
- short landscape: title and action scale as one group without clipping.

The upper landmark, left machinery, right antenna, lower canopy, and yellow
action must all remain inside the visible frame.

## Fallbacks and Accessibility

- `prefers-reduced-motion`: final composition appears immediately and remains
  static except for focus feedback.
- `?render=static` and `?motion=reduce`: deterministic complete fallback.
- missing Motion, WebGL construction failure, or context loss: the same final
  composition is shown as a static miniature.
- no JavaScript: the central title and `/quant` action remain visible.
- semantic heading and description remain available to assistive technology.
- the title is decorative and does not create duplicate navigation targets.

## Performance Targets

- one runtime animation-frame owner;
- device pixel ratio capped at `1.5`;
- average steady render time at or below `12ms` on the established desktop
  diagnostic path;
- no more than `75` draw calls;
- no more than `140,000` triangles;
- all renderer, geometry, material, texture, timer, and input resources
  disposed exactly once.

## Acceptance Criteria

The homepage is accepted when:

- no six-route carousel or product-title navigation remains;
- exactly one visible action exists and it links to `/quant`;
- the supplied screenshot's planet/title/button composition is matched at
  desktop and mobile;
- the central three-line wording matches the reference glyph sequence;
- entry ordering and perceived duration match the reference;
- ten-second idle angular displacement is within approximately `8%` of the
  reference when same-browser evidence is available;
- planet, title, and action viewport-relative bounds are within approximately
  `5%` of the reference when measurable;
- there is no page scroll or wheel behavior;
- automated lifecycle, fallback, accessibility, and performance contracts pass;
- no proprietary reference code or assets are introduced.

## Removal Scope

The implementation removes or retires:

- `PRODUCT_SCENES` route metadata from the homepage;
- six product-title anchors and no-JS route grid;
- selected/previous/next/hidden title state;
- arrow-key product selection;
- drag-to-sector snapping;
- product revolution timing;
- idle route resume behavior;
- related product-orbit CSS and tests.

The underlying Quant destination pages and their routes are unchanged.
