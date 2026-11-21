# Quant Planet Rear-Orbit Background and Detail Design

## Objective

Extend the current full-screen Quant Planet hero so the space beyond the
planet feels alive in the same way as the Messenger reference recording:

- the planet remains the dominant rotating object;
- a much wider field of fragments and atmospheric shapes circulates behind it;
- no background fragment ever travels in front of the planet, title, or CTA;
- background objects vary strongly in silhouette, size, density, and depth;
- the visible planet is `1.2x` its current desktop size, within a `±3%`
  measurement tolerance;
- the planet surface gains another pass of authored miniature detail.

The existing interaction contract remains unchanged. The planet and rear field
move autonomously. Only the nine foreground title glyphs respond to pointer or
touch input.

## Approved Direction

Use an independent, camera-facing rear-orbit rig rather than attaching larger
debris to the planet or animating a flat CSS background.

The rig is a true Three.js scene layer, but it is constrained to the rear depth
range. It creates orbital motion through camera-facing elliptical paths and
edge wrapping instead of moving objects around a complete sphere. This is the
key rule that prevents a fragment from coming around the front of the planet.

## Composition

### Planet scale

- Increase the visible planet diameter to a target of `1.2x` the current
  desktop composition.
- Apply the size increase through camera composition coverage, not only by
  scaling the world group, because the existing camera fitter would otherwise
  cancel the visible change.
- Keep the planet's complete authored silhouette inside the safe viewport at
  all tested quarter turns.
- Preserve the current title size and title-to-planet depth relationship.
- Recenter the enlarged composition slightly upward only when needed to
  preserve the ENTER control's safe area.
- On narrow portrait viewports, retain the `1.2x` intent but clamp only at the
  last safe size that prevents structural clipping and horizontal overflow.

### Rear field

The rear field spans `1.45x` to `2.45x` the visible planet radius and
is intentionally allowed to enter and leave through the viewport edges. It is
excluded from the planet camera-fitting envelope, so adding wide objects does
not make the planet smaller.

The field contains two depth bands:

1. **Rear-mid band**
   - More readable fragments near the planet silhouette.
   - Moderate scale and contrast.
   - One revolution-equivalent circulation every 48 seconds.

2. **Rear-far band**
   - Large atmospheric stains, small distant scraps, and sparse contour marks.
   - Lower contrast and slightly softer color.
   - One revolution-equivalent circulation every 64 seconds.

Both bands move in the same visual direction as the planet. Their different
periods create depth without looking disconnected.

## Rear-Only Motion Model

Each rear object owns a deterministic orbital phase, radius, vertical offset,
depth band, scale, and local spin rate.

- Screen-space circulation follows a wide camera-facing ellipse.
- Depth remains within the authored rear interval for the object's band.
- Depth may breathe by at most `8%` of its band thickness, but it is clamped
  behind the planet's rearmost safe plane.
- Objects fade down near their lateral wrap seam, reset on the opposite side,
  and fade back up. There is no visible teleport.
- Objects never cross the planet center plane and never require foreground
  occlusion hacks.
- The rear field is independent of pointer, drag, wheel, and page navigation.
- Local fragment spin ranges from 8 to 18 seconds and is deterministic.
- Motion stops completely under reduced-motion mode while retaining the same
  balanced composition.

This motion is driven by the existing single animation-frame owner. It must not
introduce a second requestAnimationFrame loop.

## Visual Families

The field must not read as one repeated debris asset. Use several deliberately
different silhouette families:

1. **Irregular rock chips**
   - Low-poly wedges, split stones, and blunt fragments.
   - Small to medium sizes.
   - Rock, sand, and muted green variations.

2. **Architectural scraps**
   - Thin slabs, bent beams, roof-like plates, and block fragments.
   - Medium to occasional large sizes.
   - Paper, coral, ink, and architecture tones.

3. **Ring and line remnants**
   - Broken hoops, narrow braces, and small mechanical marks.
   - Mostly small distant accents.
   - Low-contrast ink and teal outlines.

4. **Atmospheric cloud fields**
   - Broad, flattened, translucent low-poly blotches.
   - Large scale with irregular proportions.
   - Background-adjacent turquoise shades rather than white clouds.
   - Distributed asymmetrically so they feel painted into the wider world.

5. **Paper flecks**
   - Very small light-colored shards that punctuate empty areas.
   - Sparse enough to avoid visual noise around the title and CTA.

### Variation requirements

- High profile: 36 to 48 total rear objects across all families.
- Balanced profile: 24 to 34 total rear objects.
- Low/mobile profile: 14 to 22 total rear objects.
- At least four visibly distinct silhouette families in high and balanced
  profiles.
- At least five discrete size tiers, with the largest background shapes at
  least six times the area of the smallest visible flecks.
- No regular radial spacing, mirrored placement, or evenly repeated scale
  sequence.
- Keep a quiet zone behind the ENTER control and a lower-density zone directly
  behind the central title counters.

## Planet Detail Pass

Increase authored detail without changing the current illustration language.

### Industrial district

- Add telescope barrel bands, support collars, narrow pipes, cable stays,
  platform braces, and small ladder or railing segments.
- Break large coral surfaces with inset panels and connector geometry.

### Villages

- Add varied window splits, roof caps, chimneys, awning supports, and facade
  ledges.
- Vary building height and roof overhang without changing district anchors.

### Temple and mountain

- Add roof-edge teeth, beam ends, stair bands, lantern-like accents, and more
  layered rock strata around the summit.
- Preserve the temple as a readable silhouette at every quarter turn.

### Water and terrain

- Add secondary foam strips, stepping stones, bridge members, path chips,
  retaining edges, and cliff ledges.
- Keep water cyan visible and prevent new geometry from crushing the palette
  into dark masses.

### Foliage

- Mix crown proportions and heights more aggressively.
- Add sparse trunk forks and under-canopy stones while retaining instancing.
- Preserve the current lighter green floor and avoid black-looking clusters.

## Rendering Architecture

Introduce a dedicated rear-field module with a narrow public contract:

```js
createQuantPlanetRearField(THREE, { profile })
```

It returns:

- `group`: the Three.js group added behind the planet;
- `update({ worldAngle, elapsedMs, reduceMotion })`;
- `getRenderStats()`;
- `dispose()`.

The scene controller owns the rear field alongside the world and glyph rig. The
rear field receives the same autonomous world angle but derives its slower,
rear-only phases internally. It never receives pointer data.

The rear field is added before the planet and remains physically behind it.
Normal depth testing provides the correct occlusion. The implementation must
not rely on drawing fragments over the title and hiding them with CSS.

## Performance Budget

- Use instanced meshes and shared materials for repeated families.
- Prefer four compact rear-field draw families plus one atmospheric family.
- Reuse existing primitive geometry and palette materials where ownership and
  disposal remain explicit.
- Keep the complete high-profile scene at or below 80 draw calls and 145,000
  triangles.
- Keep the balanced scene at or below 70 draw calls and 95,000 triangles.
- Planet detail additions must extend existing instance families wherever
  possible instead of creating one mesh per detail.
- Maintain the existing `1.5` device-pixel-ratio cap.

## Responsive and Fallback Behavior

- Desktop and landscape tablet show the full two-band rear field.
- Portrait layouts reduce object count before reducing the planet target size.
- Rear objects may clip at screen edges by design, but the planet's structural
  silhouette and ENTER control may not clip.
- Reduced motion freezes the planet, rear field, local fragment spin, and title
  response in a balanced authored pose.
- Static and WebGL-failure modes gain a matching wide rear-field silhouette so
  the composition does not collapse into a bare flat background.

## Verification

Automated tests must prove:

- the rear-mid and rear-far periods are independent from the 36-second planet
  revolution;
- every sampled rear-field object remains behind the planet safe plane over a
  complete circulation;
- pointer and drag input do not affect planet or rear-field transforms;
- reduced motion freezes all autonomous rear-field movement;
- the rear field is excluded from the planet framing envelope;
- the visible desktop planet coverage is `1.2x` the current baseline within
  the specified `±3%` tolerance;
- wide, portrait, and mobile layouts preserve roundness and structural
  visibility;
- required silhouette families, size tiers, and density profiles exist;
- the complete scene remains within draw-call and triangle budgets;
- all rear-field resources dispose exactly once and construction failures roll
  back completed allocations.

Browser verification must cover:

- `1440x900`, `1920x1080`, `1280x720`, `768x1024`, and `375x812`;
- at least four orbital phases, including edge wrapping;
- no rear object appearing over the planet, title, or CTA;
- no horizontal page overflow;
- the planet reads as visibly larger while remaining unclipped;
- the atmospheric shapes and varied fragments remain visible without
  competing with the nine title glyphs.

## Out of Scope

- Scroll-controlled motion;
- pointer-controlled planet or rear-field movement;
- foreground debris passing between the title glyphs;
- external 3D model downloads;
- React migration;
- changes to the Quant destination route.
