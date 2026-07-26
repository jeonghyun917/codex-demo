# Task 2 report: Center return, reduced motion, and input invariants

## Changed files

- `docs/superpowers/plans/2026-07-27-messenger-video-parity-correction.md`
- `docs/superpowers/specs/2026-07-27-messenger-video-parity-correction-design.md`
- `src/main/resources/static/js/home-quant-planet-glyphs.js`
- `src/test/js/home-quant-planet-glyphs.test.mjs`
- `src/test/js/home-quant-planet-composition.test.mjs`
- `.superpowers/sdd/2026-07-27-messenger-video-parity-correction/task-2-report.md`

## TDD evidence

RED command:

```text
node --test src/test/js/home-quant-planet-glyphs.test.mjs src/test/js/home-quant-planet-composition.test.mjs
```

Result: 33 passing, 1 failing. The timestamped top-row sweep failed only at
the literal nearby-glyph `>= 90 degrees` assertion. Task 1's target damping had
no pointer-velocity impulse state, so the strongest top-row glyph never crossed
the required side angle.

GREEN command:

```text
node --test src/test/js/home-quant-planet-glyphs.test.mjs src/test/js/home-quant-planet-composition.test.mjs
```

Result: 34 passing, 0 failing, 0 warnings.

Full JavaScript regression:

```text
node --test src/test/js/*.test.mjs
```

Result: 101 passing, 0 failing, 0 warnings. `git diff --check` also completed
without whitespace errors.

## Design summary

- Timestamped normalized pointer updates produce bounded X/Y velocity. Each
  pointer segment is measured against immutable glyph anchors, and exponential
  distance falloff confines fast-row impulses to nearby glyphs.
- Every glyph owns independent pitch, yaw, and roll velocity and inertial
  offset state. A closed-form critically damped rest spring integrates those
  offsets without frame-step-dependent Euler damping while Task 1's authored
  base targets remain intact.
- Pointer release supplies the zero target without adding a reverse impulse.
  Center return damps all three rotations and all three anchor-relative
  translations back to the authored rest pose. Reduced motion directly resets
  the complete base pose, inertial offsets, and velocity state.
- Pointer inputs, angular targets, final rotations, and all three translation
  targets are bounded. The rig's existing public API is unchanged.
- Composition coverage compares exact planet, camera, and glyph-parent
  matrices across pointer poses while requiring different glyph-child
  matrices. The mobile touch path verifies the same invariants through the real
  glyph rig and settles within 12 degrees 1.45 seconds after release.

## Self-review

- The fast sweep records literal timestamps and thresholds: a top-row glyph
  crosses 90 degrees, at least six glyphs stay within 25 degrees, and all nine
  return within 12 degrees at the video-calibrated release interval.
- Task 1's three deferred minors are closed independently: opposite-Y center
  samples cancel the shared yaw phase pulse, opposite-X center samples remove
  row-local-Y pitch leakage, and pointer-driven Z is asserted relative to each
  authored anchor.
- No pointer value reaches the planet update, camera transform, or glyph parent
  transform. The new per-frame math allocates no geometry, material, texture,
  vector, or closure.
- Read-only review of `ea09fcd..d914695` found no Critical, Important, or Minor
  issues and assessed the implementation ready.

## Concern / follow-up

None for Task 2. Browser video calibration remains part of Task 4.

## Commit

Implementation commit SHA: `d914695`
