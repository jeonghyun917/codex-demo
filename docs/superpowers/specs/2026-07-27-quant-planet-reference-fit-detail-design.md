# Quant Planet Reference Fit and Detail Pass

Date: 2026-07-27  
Status: Approved design  
Route: `/` only

## Objective

Refine the existing Quant Planet homepage so its first stable frame matches the
supplied Messenger reference more closely in scale, negative space, and object
density while preserving the current original Quant AI world, nine independent
glyphs, ambient world rotation, pointer response, and `/quant` primary action.

This pass addresses three visible defects:

1. the live planet is substantially larger than the reference;
2. the square WebGL drawing surface clips long rotating landmarks, especially
   the coral industrial structure on the right;
3. major structures read as unfinished primitives instead of authored
   miniature objects.

## Reference Composition Contract

The supplied screenshots are the visual comparison source for this pass. The
stable desktop composition must have:

- a complete centered planet silhouette occupying approximately `68%` to
  `74%` of the usable viewport height;
- the planet center slightly above the viewport midpoint;
- clear turquoise negative space around every rotating landmark;
- the nine central glyphs occupying approximately `26%` to `32%` of the
  viewport width without overpowering the planet;
- one yellow `ENTER` action centered beneath the planet with a size and
  vertical gap comparable to the reference;
- no visible crop at the WebGL drawing-surface boundary during a complete
  world revolution.

The current world palette, glyph artwork, rotation speed, pointer-driven glyph
behavior, and action destination remain unchanged unless a small adjustment is
required to preserve the reference composition.

## Selected Approach

Use a full-viewport renderer with bound-based camera fitting and selectively
enrich the existing procedural landmarks.

This approach is preferred over enlarging the current square canvas because a
larger square still wastes horizontal viewport space and can crop a newly
extended prop. It is preferred over replacing the world with a large external
3D model because the current procedural world already supports deterministic
testing, fallbacks, resource disposal, and the project's render budgets.

## Renderer and Camera Architecture

### Full-Viewport Drawing Surface

`.planet-scene` and its canvas become full-viewport layers rather than a
`100dvh` square centered inside the page. The renderer therefore receives the
real viewport aspect ratio at every supported breakpoint.

The page remains fixed and non-scrolling. The turquoise field continues behind
the transparent renderer, and the title and primary action remain separate
foreground layers.

### Rotation-Safe Framing

The camera fit uses the complete authored world envelope returned by
`getFramingEnvelope()`. It must not multiply that envelope by the existing
landscape `0.56` hero-radius shortcut.

The fit calculation will:

1. use the complete rotation-invariant bounding radius;
2. account for both vertical and horizontal perspective limits;
3. reserve a small outer safety margin for outlines, antialiasing, and pointer
   response;
4. reserve the lower action zone so the planet never visually collides with
   `ENTER`;
5. target the reference viewport coverage rather than filling the renderer;
6. recalculate after resize or orientation change.

The same fit must keep all authored geometry visible at representative yaw
angles covering the full `0` to `2π` revolution. Adding a new landmark that
extends the envelope automatically moves the camera back rather than creating a
new clipping bug.

### Independent Foreground Scale

The nine glyphs are not scaled indirectly by the planet canvas. Their layout
uses a dedicated reference title scale derived from viewport dimensions and
the world envelope.

The glyph rig keeps:

- nine independent meshes;
- individual pointer-driven yaw and pitch variation;
- the existing response timing;
- current outline and extrusion treatment;
- reduced-motion behavior.

The DOM fallback title follows the same visible bounds as the live glyph rig.

## Landmark Detail Pass

Detail is added through small silhouette-changing geometry and high-contrast
mid-scale features. Repeated parts use instancing and shared geometry and
materials. Tiny decoration that disappears at the target camera distance is
excluded.

### Industrial Cluster

The coral engine assembly receives:

- contrasting barrel collars and end caps;
- repeated flange blocks or bolt heads;
- secondary nozzles and connecting pipe sections;
- a narrow support frame or catwalk;
- one readable gauge, hatch, or access plate;
- stronger separation between the engine body and its mount.

These additions must remain inside the public framing envelope and retain the
coral, paper, rock, and ink palette without introducing large crushed-black
surfaces.

### Village and Utility Buildings

Repeated buildings receive instanced detail families for:

- recessed dark windows;
- small door panels;
- roof overhangs or ridge caps;
- occasional chimneys or vents;
- base ledges that separate structures from terrain.

The utility landmark receives a clearer mast base, dish bracket, rotor hub, and
small cable or support elements. Details vary by deterministic placement index
so the settlements do not read as cloned boxes.

### Temple and Mountain

The summit landmark receives:

- roof ridge and eave accents;
- column bases and a visible front sill;
- a small entrance recess;
- a few layered rock ledges around the mountain base.

The temple remains a compact top silhouette and must not grow enough to change
the reference scale target.

### Water, Bridges, Terrain, and Foliage

The water district receives:

- defined bridge decks and low rails;
- visible bank stones or retaining edges;
- restrained foam or light water-edge accents.

Terrain receives a limited number of stepped cliff plates and rock clusters.
Foliage keeps its current color recovery and adds crown-scale and crown-shape
variation without increasing canopy darkness. Trunks, branches, and crowns
must remain legible at the final camera distance.

## Responsive Composition

Required validation viewports remain:

- `375×812`;
- `768×1024`;
- `1024×768`;
- `1440×900`.

Desktop and landscape targets:

- world height approximately `68%` to `74%` of the usable viewport;
- at least `4vw` visible space beyond the leftmost and rightmost landmark at
  all tested yaw angles;
- title width approximately `26vw` to `32vw`;
- primary action fully inside the bottom safe area.

Portrait targets:

- complete planet silhouette with no macro crop;
- at least `4vw` horizontal safe space;
- title remains dominant but does not exceed `84vw`;
- action retains a minimum `44×44px` interactive target;
- no horizontal or vertical document overflow.

Extremely short landscape viewports may reduce world coverage further to
protect the action and browser safe areas.

## Interaction and Motion

This pass does not change the interaction model:

- the world rotates continuously without pointer input;
- the nine glyphs react independently to pointer or touch movement;
- wheel input does not scroll or zoom the page;
- the yellow action links to `/quant`;
- the intro sequence keeps its current deadline and staging;
- reduced motion stops ambient rotation and pointer parallax while preserving
  the final composition.

Camera fitting may not introduce camera zoom, orbit acceleration, or layout
movement during normal pointer interaction.

## Fallback and Failure Behavior

The static SVG, reduced-motion state, WebGL failure state, and no-JavaScript
experience must use the same smaller reference composition and safe margins as
the live renderer.

If live detail construction fails, existing resource rollback and exactly-once
disposal behavior remains authoritative. No remote model, texture, API call, or
runtime package is added.

## Performance Constraints

The existing high-profile limits remain:

- total draw calls, including shadow calls: at most `75`;
- visible triangles: at most `140000`;
- pixel ratio cap: `1.5`;
- loopback average scene submission: at most `12ms`.

New repeated windows, doors, roof trim, fasteners, rocks, and foliage accents
must use instanced meshes where practical. Shared primitive geometry and
materials remain owned and disposed by the world resource registry.

## Verification Strategy

### Automated Tests

Add or update tests that prove:

- the renderer uses the real viewport aspect ratio;
- camera fitting uses the complete public world envelope;
- representative desktop, tablet, portrait, and short-landscape aspects retain
  the required safe margin;
- the projected world cannot clip at representative yaw angles across a full
  revolution;
- glyph sizing is independent of the old square canvas;
- named industrial, village, temple, water, and terrain detail families exist;
- repeated detail families are instanced;
- render budgets remain within limits;
- new resources are disposed exactly once after success and construction
  failure;
- fallback and reduced-motion composition contracts remain intact.

### Browser Review

Capture the reference and local page at matching viewport sizes and compare:

1. planet height and center;
2. surrounding negative space;
3. glyph width and height;
4. primary-action size and position;
5. landmark readability;
6. clipping at multiple world angles.

Review at least one full world revolution or deterministic yaw snapshots at
`0°`, `90°`, `180°`, and `270°`. Inspect console output and renderer
diagnostics after the visual comparison.

## Acceptance Criteria

The pass is complete only when:

1. the local stable frame has the same compact centered hierarchy as the
   supplied reference;
2. the coral industrial landmark and every other protruding structure remain
   visible throughout a full revolution;
3. the nine glyphs no longer appear oversized relative to the planet;
4. the primary action matches the reference's visual weight and spacing;
5. major landmarks contain readable mid-scale detail rather than only primitive
   silhouettes;
6. no added detail creates dark masses, unstable flicker, or z-fighting;
7. all required responsive viewports preserve the complete composition;
8. automated tests, Maven tests, render budgets, and browser screenshots pass
   review.

## Non-Goals

- copying reference source code, models, textures, or proprietary assets;
- changing the central glyph copy or routing;
- redesigning Quant subpages;
- adding external 3D asset dependencies;
- adding scroll, zoom, game controls, audio, or new navigation;
- pursuing microscopic geometry that is invisible at the final camera scale.
