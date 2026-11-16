# Contact4 Photoreal Meadow Rebuild Design

## Goal

Replace the repeated procedural pinwheel flowers on `/contact4` with a layered,
photorealistic WebGL meadow while preserving the existing typography, navigation,
copy, CTA, loader, accessibility states, and full-viewport composition.

## Visual Direction

The camera sits at flower height instead of looking down at a textured floor. The
foreground contains a few sharply readable dandelions and bent grass blades, the
midground contains irregular flower colonies, and the background merges into the
existing bright meadow HDRI. The scene must read as one continuous landscape rather
than a flat floor placed in front of a photograph.

## Scene Architecture

1. A displaced PBR terrain uses the local leafy-grass diffuse, normal, roughness,
   ambient-occlusion, and displacement maps. No flowers are baked into the terrain
   color texture.
2. Two scanned grass families form the continuous volume above the terrain. Near
   and mid blades use separate density, scale, and wind settings.
3. The local Poly Haven `dandelion_01` asset supplies five botanical stages. Its
   original diffuse, normal, ARM, and separate alpha texture remain intact.
4. The local `flower_empodium` asset adds restrained secondary species so the field
   does not look cloned.
5. A screen-space finishing pass adds depth-based ambient occlusion, shallow focus,
   a restrained vignette, and warm color grading. It must degrade to direct rendering
   if depth textures are unavailable.

## Asset Fidelity

- Delete the runtime use of generated cone/sphere dandelions.
- Preserve complete scanned plants; do not crop their leaves or flower heads.
- Recenter every source mesh on X/Z and normalize its base to terrain height before
  instancing. Source-node lineup offsets from the GLTF must not leak into instances.
- Treat each configured family count as a total budget distributed across source
  variants, not as a per-variant multiplier.
- Keep all assets local under `/assets/contact4/`; no runtime external requests.

## Natural Distribution

- Use deterministic clustered sampling with seed `917`.
- Create 9–14 irregular colonies rather than a grid or uniform scatter.
- Vary scale, lean, heading, height, hue, and life-stage per instance.
- Keep a soft CTA readability zone without creating a visible empty circle.
- Use foreground, midground, and far-field density profiles so detail decreases with
  distance while apparent coverage remains continuous.

## Motion

- Grass bends from the base with a primary gust, secondary turbulence, and local
  per-instance phase.
- Dandelion upper vertices lag slightly behind lower stems, giving the flower head
  more inertia than the base.
- Motion is spatially coherent: neighboring plants share the travelling gust but
  retain small phase differences.
- Reduced-motion mode freezes the authored pose without removing vegetation.

## Lighting and Camera

- Keep the bright meadow HDRI and ACES tone mapping.
- Use a warm key light and cool sky fill with soft, bounded shadows.
- Lower the desktop camera enough that foreground flowers visibly overlap the
  horizon; preserve the mobile title and CTA safe areas.
- Depth-based focus keeps the title readable while retaining a few crisp foreground
  plants and softly merging the far field into the HDRI.

## Performance and Fallback

- Use `THREE.InstancedMesh` for repeated scanned variants and grass.
- Desktop target: no more than 40 vegetation draw calls and DPR capped at 1.5.
- Mobile target: reduced instance budgets and DPR capped by the existing profile.
- The existing procedural fallback remains available when GLTF, texture, HDRI, or
  WebGL initialization fails.
- All owned geometries, materials, textures, render targets, and animation loops must
  be disposed exactly once.

## Acceptance Criteria

- No procedural star/cone dandelions appear in production.
- A foreground screenshot visibly shows textured petals, stems, serrated leaves,
  grass volume, contact darkening, and non-uniform colonies.
- The ground does not present a repeated yellow-flower texture.
- The composition has distinct foreground, midground, and background depth.
- Wind travels across the field and bends upper plant regions more than bases.
- `/contact4` has no console warnings, no horizontal overflow, and remains one
  viewport tall at desktop and 390x844 mobile.
- Focused JavaScript tests, all JavaScript tests, and the Maven suite pass.

