# Task 4 report: Scene regression and preview verification

## Integration-contract correction

The renderer-real composition fixture was intentionally reproduced before its
literal was changed:

```text
node --test src/test/js/home-quant-planet-composition.test.mjs
```

RED result: 12 passing, 1 failing. The only failure expected `72` draw calls
and `9,465` triangles, while the actual dual-rig renderer reported `68` draw
calls and `22,041` triangles, still within the `75` / `140,000` budget.

Root cause: Task 3's dense, evenly distributed foliage/detail placements
changed the rendered world totals, but this exact composition-fixture literal
was still pinned to the pre-density contract. Only that literal was updated.

Code commit: `f5c496d` (`fix: match messenger video motion and density`).

## Verification evidence

| Check | Result |
| --- | --- |
| Focused GREEN | `node --test src/test/js/home-quant-planet-composition.test.mjs`: 13 passing, 0 failing |
| Full Node regression | `node --test src/test/js/*.test.mjs`: 103 passing, 0 failing |
| Maven verification | `mvn.cmd "-Dmaven.repo.local=C:\dev\codex-demo\.m2\repository" test`: 38 tests, 0 failures/errors/skips, `BUILD SUCCESS` |
| 8081 preview | Replaced the old port-8081 preview only; `mvn spring-boot:run` served this worktree's `target/classes`. `GET http://127.0.0.1:8081/` returned HTTP 200 with title `Messenger | Quant Intelligence`. Port 8080 was not modified. |

## Follow-up / concern

- Browser video calibration is left for the root reviewer, as directed: desktop
  and 375x812 pose/timestamp captures, yaw occupancy/density/bare-region
  measurements, runtime render-time diagnostics, console/overflow, fallback,
  reduced-motion, and `/quant` target checks.
- `mvn ... package -DskipTests` was attempted before the restart but its
  Spring Boot repackage rename was blocked by the then-running old 8081 JAR.
  The required Maven test command passed before that attempt, and the preview
  was restarted from the newly compiled worktree classes after the old 8081
  process was released.

## Fix round 1: Real-instance visual exposure

Code commit: `1e58de5` (`fix: expose quant planet surface detail`).

### RED

The browser review found that the numeric density contract hid undersized
structures and terrain inside the core. A new real-instance test decomposes
the `InstancedMesh` matrices by sector and requires, per sector, three village
bodies with width `>= 0.48`, height `>= 0.50`, and center radius `>= 3.55`,
plus six terrain shelves with center radius `>= 3.48` and two broad axes
`>= 0.48` and `>= 0.38`. It also requires the graphite outline family to use
the same building-body matrices.

```text
node --test src/test/js/home-quant-planet-world.test.mjs
```

RED result: 17 passing, 1 failing. The expected failure was the missing
`ReferenceVillageOutlineFamily`; the prior dense placement data also had
terrain radius `3.34` / narrow axes and village radius `3.50` / small bodies,
which the following sector assertions would reject.

### GREEN and budget

- Raised dense terrain to radius `3.52` with broad shelf axes, and dense
  village bodies to radius `3.62` with readable body dimensions.
- Added stone, earth, and pale per-instance terrain colors through the
  vertex-colored terrain material.
- Added one BackSide graphite `ReferenceVillageOutlineFamily` using an
  enlarged box geometry and the exact building matrices. It adds exactly one
  scene draw call rather than one call per building.
- Updated the renderer-real composition literal to `69` calls and `23,337`
  triangles. The outline accounts for the one additional call and 1,296
  triangles; the total remains below the `75` / `140,000` limits.

Verification:

| Check | Result |
| --- | --- |
| Exposure/world GREEN | `node --test src/test/js/home-quant-planet-world.test.mjs`: 18 passing, 0 failing |
| Full Node regression | `node --test src/test/js/*.test.mjs`: 104 passing, 0 failing |
| Maven verification | 38 tests, 0 failures/errors/skips, `BUILD SUCCESS` |
| Replacement 8081 preview | Existing port-8081 process only was replaced. New worktree `target/classes` preview PID 22156 returned HTTP 200 for `/` (`Messenger | Quant Intelligence`). Port 8080 was not modified. |

### Follow-up

The root reviewer must repeat the browser visual comparison against the video;
the automated exposure contract now prevents regression to a broad core with
buried or tiny village and terrain instances.

## Fix round 2: Original panoramic surface art

Code commit: `cf6108c` (`fix: map original quant planet panorama`).

### Asset and RED

- Asset: `src/main/resources/static/images/quant-planet-panorama-v1.png`
  (built-in imagegen original panorama).
- `node --test src/test/js/home-quant-planet-world.test.mjs` initially had
  18 passing and 2 failing tests. The injected real `THREE.Texture` remained
  absent from `ReferencePlanetBody.material.map`, and the browser-capable
  loader made no request for the exact local panorama URL.
- A second RED then covered a loader that invokes its error callback before
  returning its placeholder texture. The core incorrectly retained that
  placeholder instead of restoring the plain ground fallback.

### GREEN and budget

- `surfaceTexture` accepts an injected texture for deterministic tests. In a
  browser-capable world, the only default loader URL is
  `/images/quant-planet-panorama-v1.png`; Node/document-less worlds do not
  create a loader and retain the original plain ground material.
- A loaded texture is configured for `SRGBColorSpace`, horizontal
  `RepeatWrapping`, vertical `ClampToEdgeWrapping`, a neutral white core tint,
  and exactly-once world disposal. Loader throws and callback failures leave
  the same plain ground fallback without aborting construction.
- The local map applies only to `ReferencePlanetBody`; the rotating core still
  keeps all protruding terrain, village, shrine, industrial, utility, canopy,
  and debris meshes. Village outline geometry was reduced from 1.12 to 1.035
  so it reads as a thin graphite edge.
- No mesh topology changed: renderer-real diagnostics remain `69` calls and
  `23,337` triangles, within `75` calls and `140,000` triangles.

| Check | Result |
| --- | --- |
| Texture/world GREEN | `node --test src/test/js/home-quant-planet-world.test.mjs`: 20 passing, 0 failing |
| Full Node regression | `node --test src/test/js/*.test.mjs`: 106 passing, 0 failing |
| Maven verification | 38 tests, 0 failures/errors/skips, `BUILD SUCCESS` |
| Replacement 8081 preview | Only the old 8081 preview was replaced. New worktree preview PID 24752 returned HTTP 200 for `/` and HTTP 200 for the panorama asset (3,858,423 bytes). Port 8080 was not modified. |

### Follow-up

The root reviewer must perform the final browser visual comparison at multiple
autonomous yaw timestamps. Existing static, reduced-motion, and WebGL-failure
fallback contracts remain covered by the regression suite.
