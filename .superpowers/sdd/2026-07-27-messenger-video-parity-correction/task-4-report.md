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
