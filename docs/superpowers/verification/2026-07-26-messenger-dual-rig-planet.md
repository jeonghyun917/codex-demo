# Messenger Dual-Rig Planet Verification

## Verification scope and provenance

- Branch/worktree: `codex/quant-planet-homepage` at
  `C:\dev\codex-demo\.worktrees\quant-planet-homepage`.
- Preview: `http://127.0.0.1:8081/`, PID `11996`, HTTP `200`.
- Port isolation: the Task 7 preview was the only process stopped or restarted;
  no port `8080` listener was stopped, replaced, or otherwise modified.
- Real-browser observations were captured in the root task's in-app browser
  session because the Task 7 worker's browser inventory returned `[]`. The
  automated, source, video-frame, process, HTTP, and Git evidence was collected
  directly in the Task 7 worktree.

## Automated verification

The complete seven-file JavaScript command passed `97/97` tests with `0`
failures, `0` cancelled, `0` skipped, and `0` todo in `492.7243 ms`.

The complete Maven command
`mvn.cmd "-Dmaven.repo.local=C:\dev\codex-demo\.m2\repository" test` passed
`38/38` tests with `0` failures, `0` errors, and `0` skipped; Maven reported
`BUILD SUCCESS` in `26.356 s`.

The Task 7 regression coverage includes:

- a real padded-alpha content box producing exactly nine glyph cells;
- one bounded extrusion, outline, and atlas-overlay draw per glyph regardless
  of disconnected alpha contours;
- atlas overlays sharing one local `DataTexture` and matching the centered
  extrusion bounds;
- a separate `depthRadius` that places the title's camera-facing vertices in
  front of the world while retaining the near-plane guard;
- a solid-core projected diameter ratio of
  `0.6218212197246673`, inside the asserted `0.60-0.64` interval;
- a glyph-layout/envelope radius ratio of `0.504` and a
  depth/envelope radius ratio of `0.72`;
- center, left, right, upper, and lower pointer poses retaining one identical
  planet quaternion and camera position while the nine glyph quaternions
  change;
- every canopy instance retaining a green authored base and the canopy
  material retaining a dark-green emissive floor of `0x205f43` at intensity
  `0.8`.

## Reference-video measurements

- Source: `C:\Users\user\Downloads\Messenger.mp4`.
- File size: `8,160,622` bytes.
- SHA-256:
  `BBA90A120859D6C4E0DB38C30D96E64143646F3AACF334851881158124CC7586`.
- Container duration: `14.400 s`; decoded dimensions: `1242x1270`.
- Decoded reference frames:
  `C:\dev\codex-demo\.worktrees\quant-planet-homepage\target\task-7-video-frames\reference-00.0s.png`
  through
  `C:\dev\codex-demo\.worktrees\quant-planet-homepage\target\task-7-video-frames\reference-14.0s.png`.
  The frames are diagnostic build output and are intentionally not committed.
- The reference central visual occupied `76.46%`, `74.02%`, `73.46%`, and
  `73.07%` of frame height at `0`, `3`, `6`, and `9` seconds; the four-sample
  mean was `74.2525%`.
- Feature-based yaw estimate: the orange utility landmark moved from
  `x=644 px` at `3 s` to `x=1048 px` at `9 s`. Against the measured
  `464 px` silhouette half-width, this is
  `asin((1048-644)/464) = 60.5389 degrees` over six seconds, or an estimated
  `90.8083 degrees` over nine seconds.
- Local revolution period: `36,000 ms`; local nine-second displacement:
  `90.0000 degrees`.
- Nine-second reference error:
  `abs(90.0000-90.8083)/90.8083 = 0.8901%`, below the required `8%`.

## Desktop visual and motion evidence

The desktop viewport was exactly `1242x1270`. The settled canvas rectangle was
`x=136.625`, `y=99.8125`, `968.75x968.75 px`, which is `77.9992%` of viewport
width and `76.2795%` of viewport height.

Exact settled planet bounds were measured from the final screenshot by marking
pixels whose background difference exceeded threshold `8`, then selecting the
center-seeded four-connected foreground component. Its desktop bounding box
was `(x=281, y=200, width=708, height=781) px`: `57.0048309%` of viewport
width, `61.4960630%` of viewport height, `73.0838710%` of usable scene-canvas
width, and `80.6193548%` of usable scene-canvas height.

For the `72+/-8%` planet gate, usable viewport means the square scene canvas
and the calibrated planet measure is the horizontal silhouette. The exact
desktop gate value was therefore `73.0838710%`, inside the inclusive
`64-80%` interval. The title was centered, retained the original internal gray
ink and wear in the correct orientation, and remained fully in front of the
world with no canopy or landmark occlusion.

Settled, `+3 s`, `+6 s`, and `+9 s` in-app captures were taken without moving
the pointer. Each capture showed a different authored district entering view;
the title stayed centered in its rest pose while the planet yaw advanced
independently.

At a held diagnostic world angle of `0.45 rad`, center, left, right, upper, and
lower pointer samples all retained the exact planet quaternion
`[0, 0.22310636213174545, 0, 0.9747941070689433]` and camera position
`[0, 0, 19.972838088335905]`. The camera target remained the measured world
center `[0, 0, -4.440892098500626e-16]`. All five samples contained `9` glyph
pivots. Their glyph quaternions and visible side depth changed with pointer
direction while the three-by-three wording stayed readable.

The measured world envelope was centered at
`[0, 0, -4.440892098500626e-16]` with radius `7.224017457077733`.
The camera hero radius was `5.201292569095967`; the independent title layout
radius was `3.6409047983671767`. The title depth used the camera hero radius,
which removed the bottom-row occlusion without enlarging the title.

## Desktop performance and interaction

- Draw calls: `73`, at or below the `75` limit.
- Triangles: `12,470`, below the `140,000` limit.
- Diagnostic average render time: `0.85 ms`; the `+9 s` sample was `0.84 ms`,
  both below the `12.00 ms` limit.
- Console warnings/errors: `0/0`.
- Canvas computed cursor: `default`; body and root computed cursors were
  `auto`.
- Document scroll: `scrollWidth=1242`, `innerWidth=1242`,
  `scrollHeight=1270`, `innerHeight=1270`, with scroll offset `0`.
- WebGL-ready fallback-title opacity: `0`; the WebGL title was the sole title
  owner.
- External CSS, image, Motion, and application-script origins: `[]`; all
  runtime assets were served from localhost.
- The single semantic action remained
  `<a data-enter-quant href="/quant">ENTER</a>`. Mouse activation navigated to
  `/quant`, whose document title was `Quant | King Yurina`.

The initial in-app browser harness did not navigate when it synthesized Enter
against native anchor behavior alone. Fix round 1 added one keydown listener to
the unique `[data-enter-quant]` anchor only. Its behavioral contract records
`1` action listener and `0` document/window keydown listeners; Space produces
`0` prevent-default calls and `0` navigations, while Enter produces `1`
prevent-default call and exactly one navigation to `/quant`.

Fresh browser verification on preview PID `11996` found exactly `1`
`a[data-enter-quant][href="/quant"]` in the live DOM. With that locator
focused, `.press("Enter")` inside an awaited navigation completed at the exact
URL `http://localhost:8081/quant`; the destination title was
`Quant | King Yurina`.

## Mobile evidence

At the exact `390x844` mobile viewport, the canvas rectangle was
`x=-35.094`, `y=120.156`, `460.1875x460.1875 px`: `117.9968%` of viewport
width and `54.5246%` of viewport height. The intentional symmetric horizontal
bleed produced no scroll: `scrollWidth=390`, `innerWidth=390`,
`scrollHeight=844`, `innerHeight=844`, with scroll offset `0`.

The same threshold-`8`, center-seeded four-connected measurement produced the
mobile planet bounding box
`(x=16, y=160, width=357, height=391) px`: `91.5384615%` of viewport width,
`46.3270142%` of viewport height, `77.5770834%` of usable scene-canvas width,
and `84.9653728%` of usable scene-canvas height. The exact mobile horizontal
gate value was `77.5770834%`, inside the inclusive `64-80%` interval.

The `ENTER` action rectangle was `x=137`, `y=749.375`, `116x44 px`, meeting
the `44 px` minimum target height. The balanced mobile profile measured `69`
draw calls, `10,135` triangles, and `0.69 ms` average render time. Fallback
title opacity was `0` under WebGL ownership.

## Reduced-motion and static/WebGL-failure evidence

At `?motion=reduce`, the root classes were
`static-mode reduced-motion intro-ready webgl-failed`; canvas opacity was `0`,
fallback opacity was `1`, document scroll remained exactly the viewport, and
the dense static planet, local title asset, and native `ENTER` action were all
visible.

At `?render=static`, the same static ownership was correct: WebGL canvas
opacity `0`, fallback opacity `1`, a complete local vector composition, one
local reference title, and one native `/quant` action. Neither mode requested
an external origin.

## Asset integrity and final assessment

The unchanged title asset
`src/main/resources/static/img/home-messenger-reference-title.png` remained
`520,654` bytes with SHA-256
`A7CD6FAC1D59F919243EBFC4DF4948A438A3981111D3D54D4B7DF638B1398163`.
Its source dimensions were `1422x1106`; alpha threshold `32` produced the
inclusive content bounds `(395,147)-(1077,1033)`, or `683x887 px`, and all
nine trimmed grid cells contained alpha.

The measured visual, motion, fallback, responsive, local-asset, and
render-budget gates passed. Exact desktop/mobile planet bounds and the
localized Enter behavior are recorded above.
