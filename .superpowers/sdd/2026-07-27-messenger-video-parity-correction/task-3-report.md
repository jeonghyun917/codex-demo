# Task 3 report: Dense 360-degree surface-detail contract

## Changed files

- `docs/superpowers/plans/2026-07-27-messenger-video-parity-correction.md`
- `docs/superpowers/specs/2026-07-27-messenger-video-parity-correction-design.md`
- `src/main/resources/static/js/home-quant-planet-world-layout.js`
- `src/main/resources/static/js/home-quant-planet-world.js`
- `src/test/js/home-quant-planet-world.test.mjs`
- `.superpowers/sdd/2026-07-27-messenger-video-parity-correction/task-3-report.md`

## TDD evidence

RED command:

```text
node --test src/test/js/home-quant-planet-world.test.mjs
```

Result: 14 passing, 2 failing. The failures were the intended missing
contracts: no global village building instance family and no
`getSurfaceDensity()` diagnostic. The new density test also requires rendered
placement IDs to equal diagnostic IDs and requires opposite yaw front-view
totals to partition the complete placement set, preventing rear geometry from
being counted as foreground.

GREEN command:

```text
node --test src/test/js/home-quant-planet-world.test.mjs
```

Result: 16 passing, 0 failing, 0 warnings.

Broader JavaScript regression:

```text
node --test src/test/js/home-quant-planet-*.test.mjs
```

Result: 101 passing, 1 failing. The only failure is the Task 4 composition
fixture that still expects the pre-Task-3 exact diagnostic of 72 calls and
9,465 triangles. The renderer-real result is 68 calls and 22,041 triangles,
with `withinBudget: true`; the integration test's exact literal must be
updated during Task 4. All other homepage JavaScript contracts pass.

`node --check` passed for both changed production modules, and
`git diff --check` reported no whitespace errors.

## Render and density budgets

- World mesh calls: `39`
- World shadow calls: `3`
- World calls including shadows: `42` (limit `75`)
- World triangles: `21,725` (limit `140,000`)
- Rendered density placements: `240`
- Sector totals: `25, 31, 26, 33, 30, 32, 28, 35`
- Front-view totals: `120, 120, 121, 122, 120, 120, 119, 118`
- Maximum neighboring azimuth gap: `6.67 degrees` (limit `18`)
- Latitude bands: upper `70`, middle `73`, lower `97`

## Design summary

- Placement records in the layout module now carry frozen category, family,
  sector, district, position, scale, and rotation data. The renderer and
  `getSurfaceDensity()` consume the same selected records for the active
  profile.
- Every 45-degree district receives continuous terrain, structure,
  foliage/rock, cable, path/frame, and pipe/pole placements. Existing debris
  is tagged as utility/silhouette detail across the full azimuth.
- Four village building meshes were consolidated into one vertex-colored
  `InstancedMesh` that contains house bodies and paired roof pieces. The four
  semantic village groups remain available as authored district landmarks.
- Four individually allocated tube cables were replaced by one shared
  cylinder segment family. Global path/frame and pipe/pole families add detail
  without per-object draw calls.
- The immutable diagnostic reports eight sector summaries, eight 22.5-degree
  boundary samples, three latitude bands, maximum azimuth gap, and eight true
  front-hemisphere yaw summaries with category totals and projected quadrant
  occupancy.

## Self-review

- The rendered placement-ID union exactly equals the diagnostic ID set; no
  separate visual-only count source remains.
- Opposite yaw pairs total exactly `240`, so each placement belongs to one of
  the two opposing front hemispheres and rear geometry cannot inflate both.
- Every sector and every offset boundary sample meets terrain `>= 6`,
  structure `>= 3`, foliage/rock `>= 6`, and utility/silhouette `>= 5`.
- All eight front samples contain four categories and all four projected
  quadrants. The least populated front view has `118` details.
- Major temple, industrial, utility, water, canopy, debris, and semantic
  village groups remain present. No per-frame geometry, material, texture, or
  placement allocation was added.
- Disposal and construction-rollback tests still pass. Read-only diff review
  found no Critical or Important Task 3 issues.

## Concern / follow-up

Task 4 must update the composition test's intentional exact renderer
diagnostic from the pre-density value to the new verified value. Browser/video
calibration and the complete Maven verification also remain Task 4 work.

## Commit

Implementation commit SHA: `cdffd23`
