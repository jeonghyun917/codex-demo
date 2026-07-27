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

## Fix round 3: Unlit panorama and subdued overlays

Code commit: `1342170` (`fix: preserve panorama artwork lighting`).

### RED

`node --test src/test/js/home-quant-planet-world.test.mjs` produced 19
passing and 2 failing tests after the new gate was added. The injected panorama
was still rendered by `MeshToonMaterial`, and the village outline family was
not transparent. The new test also decomposes actual crown matrices and caps
each axis at 75% of the corresponding authored foliage placement scale.

### GREEN and budget

- A mapped `ReferencePlanetBody` now swaps only to a tracked
  `MeshBasicMaterial` with the same panorama map, white tint, and
  `toneMapped: false`; no-texture and loader-failure worlds keep the existing
  toon ground fallback.
- The BackSide village outline material is transparent with opacity `0.30` and
  `depthWrite: false`; its geometry remains 1.035 wide.
- Crown matrices now use 72% of each authored placement scale. Trunks and all
  density/placement counts remain unchanged.
- Renderer-real diagnostics remain `69` calls and `23,337` triangles, inside
  the `75` / `140,000` budget.

| Check | Result |
| --- | --- |
| World GREEN | `node --test src/test/js/home-quant-planet-world.test.mjs`: 21 passing, 0 failing |
| Full Node regression | `node --test src/test/js/*.test.mjs`: 107 passing, 0 failing |
| Maven verification | 38 tests, 0 failures/errors/skips, `BUILD SUCCESS` |
| Replacement 8081 preview | Only the old 8081 preview was replaced. New worktree preview PID 6860 returned HTTP 200 for `/`. Port 8080 was not modified. |

### Follow-up

The root reviewer must make the final live browser visual and canvas-diagnostic
decision against the supplied video.

## Fix round 4: Macro-detail panorama replacement

### RED

The browser review found that the v1 panorama's micro-detail and hatching
collapsed into muddy brown noise on the live 400–600px sphere. The existing
browser-capable loader contract was changed first to require the exact local
`/images/quant-planet-panorama-v2.png` URL.

```text
node --test src/test/js/home-quant-planet-world.test.mjs
```

RED result: 20 passing, 1 failing. The sole failure reported the production
loader's actual v1 URL against the required v2 URL.

### GREEN

- Switched the one production panorama constant to the v2 local URL.
- Removed the superseded v1 bitmap and retained no v1 runtime or test-source
  references.
- Added the 1,774×887, 24-bit RGB v2 PNG (2:1 equirectangular ratio;
  2,551,357 bytes).
- No glyph motion, world topology, material, instance, draw-call, or triangle
  code changed.

Focused GREEN:

```text
node --test src/test/js/home-quant-planet-world.test.mjs
```

Result: 21 passing, 0 failing.

### Asset/tool and prompt provenance

The v2 bitmap was generated with Codex's built-in `image_gen.imagegen` tool.
No external model name was surfaced. Exact prompt:

```text
Use case: stylized-concept
Asset type: seamless equirectangular sphere texture for a 400–600px real-time Three.js miniature planet
Primary request: Create an original simplified cel-animation miniature world panorama that stays crisp and readable when wrapped around a small rotating sphere. Use only 25–35 large landmarks; every shape must remain recognizable at thumbnail size.
Scene/backdrop: edge-to-edge seamless 360-degree equirectangular world surface, no sky, no horizon, no frame. Left and right edges must tile.
Subject: large crooked cream houses with thick charcoal outlines, broad pale stone cliff plates with a few contour lines, wide turquoise water channels, chunky forest-green canopy clusters, one or two large rust-orange industrial engines and pipe networks per region, strong bridges, antennas, a summit pavilion, and scattered large rock fragments. Distribute these bold landmarks evenly through every longitude; no empty field.
Style/medium: flat hand-drawn cel animation background, bold graphite outlines, simplified graphic shapes, large color blocks, very limited internal hatching, high silhouette clarity, sophisticated animated-film art direction. Not isometric city concept art; not photorealistic; not noisy.
Composition/framing: true 2:1 equirectangular texture. Use large macro forms, few objects, broad paths and water shapes. Avoid tiny repeated windows, micro-buildings, fine paper grain, and dense brown line noise.
Lighting/mood: bright diffuse flat cel light, high readability.
Color palette: dominant turquoise water and soft green land, cream buildings and pale gray-beige cliffs, dark forest green, saturated rust orange-red machinery, charcoal ink, small yellow accents. Keep tan/brown below 20% of the image. No broad olive blank areas.
Constraints: no text, no letters, no UI, no people, no character, no logo, no watermark, no sky, no outer space, no border lines, no empty zones, no tiny detail, no photographic texture, seamless horizontal tiling.
```

### Verification

| Check | Result |
| --- | --- |
| World GREEN | `node --test src/test/js/home-quant-planet-world.test.mjs`: 21 passing, 0 failing |
| Renderer budget | `node --test src/test/js/home-quant-planet-composition.test.mjs`: 13 passing, 0 failing; renderer-real budget contract passed |
| Full Node regression | `node --test src/test/js/*.test.mjs`: 107 passing, 0 failing |
| Maven verification | `mvn.cmd "-Dmaven.repo.local=C:\dev\codex-demo\.m2\repository" test`: 38 tests, 0 failures/errors/skips, `BUILD SUCCESS` |
| v1 reference audit | No v1 reference remains under `src/main` or `src/test`; only this report's historical round-2 record names the superseded draft |

The preview was not restarted for this asset-only URL replacement. The root
reviewer must make the final live 400–600px macro-detail comparison at several
autonomous yaw timestamps before accepting Task 4 visually. Port 8080 was not
modified.

## Fix round 5: Video framing and panorama exposure

### Composition RED/GREEN

The existing square-only framing fixture was replaced with a real 1280×720
projection contract. It requires the solid body to occupy 75–82% of viewport
height, keeps the glyph center on the camera axis, retains the title's 0.504
world-envelope radius, and leaves the existing 375×812 finite clip-safety
contract active.

```text
node --test src/test/js/home-quant-planet-composition.test.mjs
```

RED result: 12 passing, 1 failing. The production 16:9 body ratio was
`0.6218212197246673`, below the required range.

A first minimal `.55` landscape camera ratio was intentionally verified before
acceptance. It produced a body ratio of `0.8320620662755016` and clipped the
full portrait envelope, so the final fit uses `.56` for landscape and retains
the proven `.72` portrait fit. The glyph physical layout now derives directly
from `0.504` of the world envelope, so it grows with the landscape camera move
instead of shrinking with the camera-fit radius.

Focused GREEN: 13 passing, 0 failing.

### Surface-overlay RED/GREEN

The real material/matrix test was tightened first to cap the repeated village
backing opacity at `0.08` and each canopy crown axis at `0.55` of its authored
placement scale.

```text
node --test src/test/js/home-quant-planet-world.test.mjs
```

RED result: 20 passing, 1 failing. The first failure exposed the production
village backing opacity of `0.30`; the crown matrices were independently
traced to `0.72` of authored scale. The minimal implementation changed only
those two values. No glyph response logic, texture, topology, or instance
count changed.

Focused GREEN: 21 passing, 0 failing.

### Verification

| Check | Result |
| --- | --- |
| Combined world/composition | 34 passing, 0 failing |
| Renderer budget | 69 draw calls and 23,337 triangles, within 75 / 140,000 |
| Full Node regression | 107 passing, 0 failing |
| Maven verification | 38 tests, 0 failures/errors/skips, `BUILD SUCCESS` |
| Diff hygiene | `git diff --check` reported no whitespace errors |

Per the fix-round directive, no server was restarted and neither port 8080 nor
8081 was touched. The root reviewer owns the final live 1280×720 comparison
and browser acceptance against `Messenger.mp4`.
