# Messenger Dual-Rig Planet Homepage Design

Date: 2026-07-26

Branch: `codex/quant-planet-homepage`

Status: Approved concept; detailed specification awaiting review

## Objective

Refine the fullscreen Messenger-inspired homepage so its motion model and
miniature-world density match the user-supplied `Messenger.mp4` reference.

The page remains a locally authored Three.js experience. It must not copy or
load the reference site's source code, models, textures, shaders, or font
files. The only intentional product adaptation remains the yellow `ENTER`
action, which navigates to `/quant`.

## Video Evidence

The supplied reference is approximately 14.4 seconds at 29.93 frames per
second. Sampled frames show two visually independent motion systems:

1. the miniature planet turns continuously at a stable horizontal rate,
   regardless of pointer position;
2. the nine central extruded glyph objects respond to pointer position while
   retaining their three-by-three layout.

The reference planet fills roughly 70–80% of the viewport and presents a dense,
rounded silhouette. Visible landmarks include a summit shrine, pale mountain
forms, water, crooked houses, orange-red industrial machinery, pipes,
antennas, a dense lower forest, irregular earth and rock surfaces, and floating
debris. The rendering combines restrained cel shading with dark graphite-like
outlines.

## Selected Approach

Use two independent Three.js rigs and reconstruct the central title as nine
real extruded meshes.

This approach was selected over:

- nine cropped DOM images, which cannot produce convincing side depth,
  lighting, or per-object perspective;
- an externally authored GLB workflow, which would add a separate content
  pipeline and create avoidable asset-provenance and maintenance risks.

The selected approach preserves the exact visible glyph silhouettes while
keeping all runtime geometry, materials, outlines, and motion locally owned.

## Scene Architecture

The runtime scene graph becomes:

```text
SceneRoot
├── PlanetRoot
│   ├── PlanetCore
│   ├── TerrainAndWater
│   ├── LandmarkGroups
│   └── AttachedDebris
├── GlyphRig
│   ├── GlyphMesh 1
│   ├── GlyphMesh 2
│   ├── GlyphMesh 3
│   ├── GlyphMesh 4
│   ├── GlyphMesh 5
│   ├── GlyphMesh 6
│   ├── GlyphMesh 7
│   ├── GlyphMesh 8
│   └── GlyphMesh 9
└── AmbientDebris
```

`PlanetRoot` and `GlyphRig` must never share a pointer-controlled parent.
Camera fitting may respond to viewport changes but not to live pointer input.

### PlanetRoot

`PlanetRoot` owns one monotonic autonomous yaw angle:

```text
planetYaw = initialYaw + elapsedSeconds * calibratedAngularVelocity
```

Pointer coordinates, pointer capture, drag deltas, and release inertia are not
inputs to this calculation. The video-sampled apparent speed provides the
initial calibration, followed by visual comparison at matching timestamps.
Starting, leaving, clicking, or dragging the pointer must not cause a rotation
phase jump or speed change.

### GlyphRig

The existing title image is a construction source, not the final rendered
object. Its alpha silhouette is split into nine glyph regions. Each region is
traced into one or more closed contours and built as a locally generated
`THREE.ExtrudeGeometry` mesh with:

- warm off-white front faces;
- muted gray-green side faces;
- a dark graphite outline;
- sufficient depth to expose its sides under pointer rotation;
- a local pivot centered on the individual glyph.

The nine meshes retain the exact three-row, three-column wording and spacing
visible in the reference. They are children of a common `GlyphRig`, but each
glyph receives a small individual response gain, delay, depth offset, and
rest-angle variation so the group feels physical rather than like one flat
card.

The title remains decorative and does not become nine navigation targets.

## Interaction Model

### Pointer response

Pointer position is normalized to `[-1, 1]` relative to the viewport.

- horizontal pointer position targets glyph yaw;
- vertical pointer position targets glyph pitch;
- response is damped and frame-rate independent;
- each glyph follows the shared target with a narrowly varied gain and delay;
- translation is restricted to subtle depth and settling movement so the
  three-by-three grid remains legible;
- when the pointer leaves, the glyphs ease back to their authored front-facing
  rest poses.

Target limits are tuned against the video and begin in these restrained ranges:

- group yaw: approximately `±20deg`;
- group pitch: approximately `±12deg`;
- per-glyph variation: no more than approximately `±4deg` beyond the group
  target.

No pointer input changes planet rotation, camera orbit, page position, or
button placement.

### Touch

A primary touch position drives the same normalized glyph target while active.
Release returns the glyphs to rest. There is no page scroll, pinch zoom, or
planet drag interaction on the homepage.

### ENTER action

The yellow `ENTER` plaque remains a native anchor to `/quant`. It stays outside
both 3D motion rigs, keeps a minimum `44x44px` target, and receives only local
hover, focus, and press feedback.

## Planet Art Direction

Replace the sparse or planar construction with a rounded, densely occupied
miniature planet.

### Silhouette and mass

- The core reads as a compact irregular sphere or rounded island from all
  sampled yaw angles.
- Terrain wraps around the sides instead of ending at a large flat front face.
- Landmarks break the circumference without creating rectangular clipping.
- The complete world occupies approximately 72–78% of the usable viewport
  while keeping summit, machinery, forest, debris, and `ENTER` visible.

### Landmark distribution

- **Summit:** shrine or pavilion, pale peaks, rocks, and tall narrow trees.
- **Upper and middle terrain:** stepped ground, water pockets, bridges, paths,
  and crooked small buildings.
- **Industrial sector:** layered orange-red engines, pipes, tanks, braces, and
  mechanical joints.
- **Utility sector:** antennas, wires, poles, platforms, and small service
  structures.
- **Lower hemisphere:** dense overlapping forest canopy, exposed rock, roots,
  and earth.
- **Atmosphere:** sparse fragments, leaves, chips, and hand-drawn flecks around
  the planet.

The planet must remain visually interesting through a full rotation rather
than presenting one highly detailed facade and an empty reverse side.

### Materials and outlines

- Use a turquoise background sampled toward the reference's apparent hue.
- Use low-saturation cream, stone, forest green, water teal, and rust-orange
  landmark colors.
- Preserve authored material colors under lighting; fog must not wash the
  planet into the background color.
- Use flat or restrained toon lighting with dark graphite outlines.
- Avoid glossy science-fiction surfaces, bloom-heavy effects, perfect
  primitives, or low-detail cuboid placeholders.

Repeated trees, rocks, roof modules, and debris should use shared geometry or
instancing. Major landmarks remain separately authored groups for silhouette
tuning.

## Entry Sequence

The existing fullscreen loader contract remains, but the reveal resolves into
the new dual-rig scene:

1. turquoise field and compact loader;
2. planet appears and settles at final scale;
3. nine glyph meshes resolve from compressed depth into the three-by-three
   arrangement;
4. yellow `ENTER` plaque rises into place;
5. autonomous planet rotation continues without a phase jump;
6. glyph pointer response becomes active only after the title is readable.

The complete final composition must appear within the existing four-second
hard deadline.

## Responsive, Reduced-Motion, and Failure Behavior

- Desktop, tablet, and mobile preserve the same planet/title/button hierarchy.
- Camera fitting is recalculated only for viewport size and safe-area changes.
- Mobile reduces minor debris and outline detail before reducing landmark
  silhouette or title quality.
- `prefers-reduced-motion`, `?motion=reduce`, and the static rendering path show
  the final composition immediately. Planet rotation is disabled and glyphs
  remain in their authored rest poses.
- WebGL construction failure or context loss shows a static composition with
  the exact title and working `/quant` action.
- No JavaScript still exposes an accessible title and `/quant` anchor.

## Performance Budget

- one animation-frame owner;
- device pixel ratio capped at `1.5`;
- no more than `75` steady-state draw calls;
- no more than `140,000` rendered triangles;
- average steady render time at or below `12ms` on the established desktop
  diagnostic path;
- no per-frame geometry, material, texture, or vector allocation in the main
  render loop;
- all renderer, input, geometry, material, texture, outline, timer, and context
  resources disposed exactly once.

If denser art exceeds a budget, use instancing, geometry reuse, lower hidden
surface detail, or profile-based debris reduction before removing a primary
landmark.

## Verification Strategy

Automated tests must prove:

- the runtime creates exactly nine independent glyph meshes;
- changing pointer coordinates changes glyph targets and poses;
- the same pointer changes do not change `PlanetRoot` rotation;
- elapsed time advances planet rotation at a stable rate;
- pointer leave and touch release restore the glyph rest target;
- reduced-motion disables both autonomous and pointer-driven motion;
- `ENTER` remains the only visible action and targets `/quant`;
- there is no wheel, page-scroll, planet-drag, or glyph-navigation behavior;
- scene creation, failure fallback, context recovery, and disposal remain
  deterministic;
- draw-call and triangle budgets are enforced by the existing diagnostics.

Visual review must compare at least:

- the initial settled frame;
- two matched idle timestamps showing different planet landmarks;
- pointer at center, left, right, upper, and lower positions;
- desktop landscape and mobile portrait;
- reduced-motion and WebGL-failure fallbacks.

## Acceptance Criteria

The refinement is accepted when:

- the planet visibly rotates without any mouse or touch input;
- moving, dragging, entering, or leaving the pointer cannot alter the planet's
  angular phase, speed, tilt, or camera;
- nine real extruded central objects visibly expose different side faces as the
  pointer moves;
- the central objects remain arranged as the exact reference wording and return
  smoothly to their rest composition;
- the planet reads as a dense rounded miniature at every tested rotation
  timestamp and contains the specified shrine, peaks, water, housing,
  machinery, utility, forest, earth, and debris language;
- viewport-relative bounds, idle angular displacement, title response, entry
  timing, and palette are calibrated against `Messenger.mp4`;
- only the yellow `ENTER` action is interactive navigation and it opens
  `/quant`;
- accessibility, fallback, lifecycle, automated test, and performance
  contracts pass;
- no proprietary reference code or assets are introduced.

## Replacement Scope

This design supersedes the earlier specification's pointer-controlled
camera/world response and drag-owned planet rotation. It also replaces the
single rendered title image with nine runtime glyph meshes while retaining the
image as a trace/fallback source.

Quant destination pages and their routes remain unchanged.
