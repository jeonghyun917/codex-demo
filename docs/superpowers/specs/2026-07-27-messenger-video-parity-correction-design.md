# Messenger Video Parity Correction Design

Date: 2026-07-27

Status: Approved by the user's 2026-07-27 directive to match the supplied video
without further design questions.

Route: `/`

## Problem

The current implementation does not match `Messenger.mp4`.

- All nine glyph pivots follow one shared yaw/pitch target with only small gain
  differences. They therefore read as one soft card rather than nine physical
  objects.
- The world distributes one primary authored landmark to each of eight sectors.
  During rotation, large areas of the core remain visually bare.

The supplied video instead shows nine independently articulated objects. At
different pointer positions, individual glyphs expose different side faces,
rotate on different axis mixtures and directions, and settle with different
inertia. The planet remains densely occupied throughout its autonomous
rotation.

## Selected Correction

### Independent glyph pose field

Keep the common `GlyphRig` only for layout and reveal. Give every glyph an
immutable response profile containing:

- independent yaw, pitch, and roll contributions from both pointer axes;
- an individual maximum rotation envelope;
- independent response speed;
- rest yaw, pitch, and roll;
- pointer-driven local X, Y, and Z translation.

Each glyph computes its own target pose directly from the normalized pointer.
There is no shared target rotation and no pointer rotation on the parent group.
The response remains frame-rate independent. Pointer velocity adds a
proximity-weighted angular impulse to independent per-glyph velocity state so a
fast pass can flip one nearby glyph without flipping the complete grid.
Independent damping and a front-facing rest spring return the objects after the
pointer slows or leaves. Reduced motion restores all nine authored rest poses
immediately.

The movement may expose a narrow side or near-edge-on face, as the video does,
but the grid anchors stay fixed enough that the 3-by-3 wording remains
recognizable when the pointer returns to center.

### Continuous miniature density

Retain eight semantic districts, but replace the single-landmark interpretation
with a continuous surface-detail field. Every 45-degree sector must contain:

- terrain mass;
- at least one architectural or mechanical cluster;
- vegetation or rock detail;
- small silhouette detail such as debris, roof pieces, pipes, or utility
  elements.

Repeated detail uses the existing shared geometries and instanced meshes.
Instance counts and placements may grow without adding a draw call for each
object. Major shrine, industrial, water, utility, village, and forest
silhouettes remain authored groups.

The detailed field wraps across sector boundaries so no rotation sample reveals
a large undressed core face.

### Original panoramic surface art

The counted placement field and protruding Three.js meshes are not sufficient
when their gaps still reveal a uniform primitive core. The irregular rotating
core therefore uses the locally authored
`/images/quant-planet-panorama-v2.png` as an equirectangular color map.

The panorama is original Quant artwork containing 25–35 large, high-contrast
houses, cliffs, waterways, forest clusters, utility towers, and rust machinery
distributed across every longitude. Its macro forms and restrained hatching
remain readable when the planet is rendered at 400–600px instead of collapsing
into brown micro-detail. It contains no text, copied brand asset, remote
dependency, or runtime API request. The texture rotates only because it is
attached to the autonomous `PlanetRoot`; pointer input never changes it.

Three-dimensional terrain, village, shrine, industrial, utility, canopy, and
debris meshes remain above the mapped core to provide real silhouette,
parallax, side depth, and shadows. Their outlines are restrained so they do not
become black blocks over the detailed map.

The panorama is rendered with an unlit, non-tone-mapped material so its authored
cream, teal, rust, and forest palette is not crushed into brown by scene
lighting. Village outlines remain transparent with opacity at or below 0.35,
and high-profile canopy crowns use at most 75% of their authored scale so the
surface drawing remains visible between protruding silhouettes.

At a 16:9 desktop viewport, the solid planet body occupies 75–82% of viewport
height, matching the Messenger video's dominant hero scale. The nine-glyph
title grows with the same camera move and remains centered over the world.
The desktop square scene therefore uses `min(100dvh, 78vw)`; the ratio is
measured against the outer viewport, not merely against the internal canvas.
Large-desktop, tablet-landscape, and short-landscape overrides preserve the
same `100dvh` height basis and vary only their width caps.
Because the panorama already supplies hand-drawn ink, repeated village backing
outlines retain their diagnostic transforms but are not rendered, while canopy
crowns use no more than 55% of their authored scale. This prevents overlapping
instances from forming a black ring around the mapped artwork.

Repeated terrain shelves, village faces, structural ink, and the nine title
glyph face/side colors use non-tone-mapped basic materials. Their authored
cream, rust, green, and gray bands therefore remain readable at every yaw
instead of turning into broad black silhouettes on faces opposite the key
light. Geometry, depth, independent glyph pivots, and ink edge lines remain
fully three-dimensional.

## Behavioral Contracts

- Planet yaw is a pure function of elapsed time and never consumes pointer,
  drag, or touch coordinates.
- All nine glyph target poses are distinct for an off-center pointer.
- At least three glyphs use a different yaw direction or cross-axis response
  from their neighbors at a diagonal pointer sample.
- At least three glyphs expose a strong side angle at the video-calibrated
  pointer extremes.
- A fast row sweep can push at least one nearby glyph beyond 90 degrees while
  at least six glyphs remain within 25 degrees.
- After pointer release, all nine glyphs return within 12 degrees of rest in
  1.2 to 1.8 seconds.
- Pointer return to center settles each glyph to its own rest pose.
- Eight yaw samples at 45-degree intervals each expose dense detail from at
  least three visual categories on the front hemisphere.
- Every authored 45-degree sector contains at least 20 surface details:
  terrain mass, three or more structures/mechanical pieces, six or more
  foliage/rock pieces, and five or more utility/silhouette details.
- The maximum authored azimuth gap between neighboring surface details is at
  most 18 degrees.
- Density counts alone do not qualify as visible detail. Every sector must have
  at least three structure bodies with a minimum world-space width of 0.48,
  height of 0.50, and center radius of 3.55 so the objects sit above the core
  rather than inside it.
- Every sector must expose at least six terrain masses whose center radius is
  at least 3.48 and whose two broad axes are at least 0.48 and 0.38. Terrain
  uses multiple stone, earth, and pale surface tones rather than one uniform
  olive material.
- The global village instance family has a matching instanced graphite outline
  family. The extra outline call must remain inside the 75-call scene budget.
- `getSurfaceDensity()` derives immutable sector, boundary, latitude-band, and
  front-hemisphere summaries from the same placement arrays that populate the
  rendered instanced families; opposite yaw samples partition, rather than
  double-count, front and rear detail.
- The existing yellow `ENTER` anchor remains the only navigation control and
  continues to target `/quant`.

## Visual Acceptance

At desktop and portrait mobile sizes:

- the planet fills the same dominant central area as the reference;
- the world silhouette stays round, layered, and detailed through a full turn;
- crooked houses, roof pieces, stone shelves, paths, pipes, and machinery read
  as distinct objects at normal viewport scale instead of disappearing into
  the core or a uniform tree ring;
- nine glyphs visibly move as separate solid objects rather than a shared
  plane;
- the strongest pointer poses show visibly different faces and depths per
  glyph;
- the turquoise field, paper faces, graphite outlines, forest, stone, water,
  and rust machinery retain the reference's cel-ink hierarchy;
- the planet continues rotating when the pointer is stationary and while
  glyphs react.

For eight matched yaw captures, the visible planet ring must keep foreground
occupancy at or above 40%, detail-edge density at or above 22%, density
variation at or below 15%, and the largest bare connected region at or below
12% of the planet silhouette.

## Performance and Fallbacks

- steady-state draw calls, including shadows, remain at or below `75`;
- rendered triangles remain at or below `140000`;
- the established desktop diagnostic render time remains at or below `12ms`;
- no per-frame geometry, material, or texture allocation is introduced;
- the local panorama is loaded once, uses sRGB color, wraps horizontally, and
  is disposed exactly once with the world;
- reduced-motion and WebGL-failure screens retain the same final composition;
- all owned resources remain exactly-once disposable.

## Verification

Automated tests cover independent glyph pose signatures, damping, rest
restoration, pointer-independent planet yaw, sector density, render budgets,
and disposal. Browser verification compares center and extreme pointer poses,
multiple autonomous rotation timestamps, desktop, mobile, reduced motion, and
the final navigation target against the supplied video frames.
