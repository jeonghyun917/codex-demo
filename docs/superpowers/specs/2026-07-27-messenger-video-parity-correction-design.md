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
The response remains frame-rate independent. Reduced motion restores all nine
authored rest poses immediately.

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

## Behavioral Contracts

- Planet yaw is a pure function of elapsed time and never consumes pointer,
  drag, or touch coordinates.
- All nine glyph target poses are distinct for an off-center pointer.
- At least three glyphs use a different yaw direction or cross-axis response
  from their neighbors at a diagonal pointer sample.
- At least three glyphs expose a strong side angle at the video-calibrated
  pointer extremes.
- Pointer return to center settles each glyph to its own rest pose.
- Eight yaw samples at 45-degree intervals each expose dense detail from at
  least three visual categories on the front hemisphere.
- The existing yellow `ENTER` anchor remains the only navigation control and
  continues to target `/quant`.

## Visual Acceptance

At desktop and portrait mobile sizes:

- the planet fills the same dominant central area as the reference;
- the world silhouette stays round, layered, and detailed through a full turn;
- nine glyphs visibly move as separate solid objects rather than a shared
  plane;
- the strongest pointer poses show visibly different faces and depths per
  glyph;
- the turquoise field, paper faces, graphite outlines, forest, stone, water,
  and rust machinery retain the reference's cel-ink hierarchy;
- the planet continues rotating when the pointer is stationary and while
  glyphs react.

## Performance and Fallbacks

- steady-state draw calls, including shadows, remain at or below `75`;
- rendered triangles remain at or below `140000`;
- the established desktop diagnostic render time remains at or below `12ms`;
- no per-frame geometry, material, or texture allocation is introduced;
- reduced-motion and WebGL-failure screens retain the same final composition;
- all owned resources remain exactly-once disposable.

## Verification

Automated tests cover independent glyph pose signatures, damping, rest
restoration, pointer-independent planet yaw, sector density, render budgets,
and disposal. Browser verification compares center and extreme pointer poses,
multiple autonomous rotation timestamps, desktop, mobile, reduced motion, and
the final navigation target against the supplied video frames.
