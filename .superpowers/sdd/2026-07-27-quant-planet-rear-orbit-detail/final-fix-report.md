# Quant Planet Rear Orbit Final Fix Report

## Scope

- Base HEAD: `e78d7ea9aff400db3dc7eac9b56de16c61cd4660`
- Branch: `codex/quant-planet-homepage`
- Scope: rendered seam opacity, responsive rear density, authored diversity and
  independent self-spin, ENTER quiet zone, and the `2026072801` scene import
  cache token.

## TDD RED

Focused JavaScript command:

```text
node --test src/test/js/home-quant-planet-rear-field-layout.test.mjs src/test/js/home-quant-planet-rear-field.test.mjs src/test/js/home-quant-planet-runtime.test.mjs src/test/js/home-quant-planet-composition.test.mjs
```

Result before production changes: 60 tests, 55 passed, 5 failed. The failures
were limited to the new contracts for rear-profile scene wiring, 8–18 second
self-spin, the ENTER quiet zone, rendered `instanceOpacity`, and responsive
rear capability selection.

Focused template command:

```text
mvn.cmd "-Dmaven.repo.local=.m2/repository" -Dtest=HomePageTemplateContractTest test
```

Result before production changes: 15 tests, 13 passed, 2 failed. The failures
were limited to the internal scene cache token and viewport/rear-profile
bootstrap wiring.

## Implementation

- Every rear family now owns an `instanceOpacity` attribute. A shared material
  shader hook multiplies `diffuseColor.a` by that value; cloud opacity remains
  `0.24`, so cloud base opacity and seam opacity multiply in the final fragment
  path. Rear transparent materials disable depth writes.
- Rear density is selected independently from world quality and flows through
  capability resolution, runtime, scene construction, and rear-field layout.
  The world profile, camera fit, and `planetScale` path are unchanged.
- Family, band, size, and self-spin are deterministic authored sequences rather
  than short modulo repetitions. Self-spin uses 8–18 second periods while
  circulation keeps its independent 48/64 second periods.
- Reduced motion remains frozen, and authored vertical offsets protect the
  bottom-center ENTER quiet zone.
- The entry module now imports the scene with `?v=2026072801`, matching the
  release token asserted by the template contract.

## GREEN Verification

- Focused JavaScript: 60/60 passed.
- Focused `HomePageTemplateContractTest`: 15/15 passed.
- Full JavaScript: 142/142 passed.
- Full Maven: 42/42 passed.
- `git diff --check`: passed; only the repository's existing LF-to-CRLF
  checkout warnings were reported.
- Disposal and construction rollback tests remain green.

## Responsive and Render Diagnostics

Production modules were executed directly with the production Three.js build:

| Viewport | World quality | Rear profile | Objects | Draw calls | Triangles |
| --- | --- | --- | ---: | ---: | ---: |
| 375×812 | balanced | low | 18 | 5 | 904 |
| 768×1024 | high | balanced | 30 | 5 | 1,380 |
| 1920×1080 | high | high | 42 | 5 | 1,775 |

Rendered rock-instance alpha at the seam was `0`; at both `±0.08 rad` it was
`0.255727`. Across the circulation wrap (`2π - 0.001` and `0.001`) alpha was
`1` on both sides and X positions were `-0.00497` / `0.00497`. The inspected
material was transparent with `depthWrite=false`.

The production scene budget tests also passed for high and balanced profiles.

## Browser Status

The local application responded with HTTP 200 on `127.0.0.1:8081`, but the
Codex browser runtime reported no available browser backends (`[]`). Per the
browser-control workflow, no unrelated automation backend was substituted.
Consequently, live canvas screenshots and browser-only diagnostics could not
be collected in this session. Responsive selection, seam/wrap behavior, scene
construction, canvas resize/framing, and production budgets remain covered by
the production-module diagnostics and full automated suite above.

## Remaining Concern

No functional regression is known. The only incomplete evidence is live
browser visual inspection because no browser backend was attached to this
session.
