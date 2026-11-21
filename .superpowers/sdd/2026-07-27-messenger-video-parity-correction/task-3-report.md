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

## Fix round 1: balanced foliage distribution

### Finding and correction

The non-high foliage path selected the first `foliageCount` entries from the
flattened belts. The real balanced profile (`foliageCount: 18`) therefore
rendered only one foliage/rock placement in sector 0 and deleted detail from
other later sectors instead of reducing it evenly.

Non-high selection now prioritizes the authored continuous-canopy belt, groups
candidate placements by their rendered `sectorIndex`, and selects them in
round-robin sector order. The balanced profile enforces the contract minimum
of six placements in each of eight sectors (48 total); additional requested
placements remain evenly distributed. A zero-foliage static profile remains
empty, while high still renders all authored foliage placements.

### TDD evidence

RED command:

```text
node --test src/test/js/home-quant-planet-world.test.mjs
```

Result: 16 passing, 1 failing. The new real-balanced-profile test failed with
`balanced sector 0 has only 1 foliage/rock details`, reproducing the prefix
slice bias.

GREEN command:

```text
node --test src/test/js/home-quant-planet-world.test.mjs
```

Result: 17 passing, 0 failing, 0 warnings. Balanced sector foliage counts are
`6, 6, 6, 6, 6, 6, 6, 6`; all eight 22.5-degree boundary samples also contain
six foliage/rock details.

Related homepage regression:

```text
node --test src/test/js/home-quant-planet-*.test.mjs
```

Result: 102 passing, 1 failing. The sole failure remains the previously
documented Task 4 exact composition fixture (`72 / 9,465` expected versus
renderer-real `68 / 22,041`, still `withinBudget: true`). No new related
homepage regression was introduced by this fix.

### Balanced and high budgets

- Balanced: `38` mesh calls, `0` shadow calls, `17,374` triangles, `195`
  rendered density placements, front-view totals `93` to `102`, maximum gap
  `9 degrees`.
- High: `39` mesh calls, `3` shadow calls, `21,725` triangles, `240` rendered
  density placements, front-view totals `118` to `122`, maximum gap
  `6.67 degrees`.
- Both profiles remain below `75` calls including shadows and `140,000`
  triangles.
