# Task 1 report: Independent nine-glyph target poses

## Changed files

- `src/main/resources/static/js/home-quant-planet-glyphs.js`
- `src/test/js/home-quant-planet-glyphs.test.mjs`
- `docs/superpowers/specs/2026-07-27-messenger-video-parity-correction-design.md`
- `docs/superpowers/plans/2026-07-27-messenger-video-parity-correction.md`
- `.superpowers/sdd/2026-07-27-messenger-video-parity-correction/task-1-report.md`

## TDD evidence

RED command:

```text
node --test src/test/js/home-quant-planet-glyphs.test.mjs
```

Result: 18 passing, 1 failing. The new independent-pose contract failed at its
nonzero-roll assertion because the prior shared-target implementation left
every glyph roll at zero.

GREEN command:

```text
node --test src/test/js/home-quant-planet-glyphs.test.mjs
```

Result: 19 passing, 0 failing, 0 warnings. `git diff --check` also completed
without whitespace errors.

## Design summary

- Replaced positional gain tuples with nine frozen named response profiles.
  Each profile supplies direct and cross yaw/pitch coefficients, roll,
  response speed, rest pose, local translation, rotation envelopes, and an
  independent temporal phase pulse.
- Every pivot retains a frozen layout anchor. Pointer input is converted to a
  local anchor-relative vector and distance falloff, so top and bottom rows
  react most strongly to a pointer near that row rather than sharing one title
  target.
- The yaw phase pulse is damped analytically, preserving frame-rate independent
  motion while allowing selected glyphs to expose 70–150 degree side/back
  poses at different phases. Pitch, roll, and three local position components
  are independently damped and clamped. The parent glyph group is never
  rotated.

## Self-review

- `createQuantGlyphRig` remains the public API and no parent-group pointer
  rotation was introduced.
- The focused test locks nine unique diagonal signatures, multiple rolls,
  direct and cross-axis responses, at least three 70-degree side angles, local
  position deltas, distance-falloff row behavior, reduced-motion restoration,
  capped elapsed steps, and frame-rate independent pose damping.
- Removed a per-frame helper closure during review; the pose update allocates
  no geometries, materials, or textures.

## Concern / follow-up

The profiles provide individual lag and phase-based side/back poses, but they
do not yet implement a separate high-velocity sweep impulse/overshoot model.
That input-velocity contract should be completed with Task 2's center-return
and motion-invariant coverage.

## Commit

Implementation commit SHA: `3d7cc3a`
