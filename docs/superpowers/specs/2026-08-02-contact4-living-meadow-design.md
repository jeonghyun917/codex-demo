# Contact 4 — Living Meadow Design

## Intent

`/contact4` is a full-viewport generative nature artwork: an alpine meadow at first light, animated by one coherent wind field. It must feel photographic at a glance while remaining entirely generated in the browser without `<img>` or `<video>` assets.

## Art Direction

- A low camera sits inside a meadow rather than above it.
- Foreground blades show veins, tapered silhouettes, translucent edges, dew and contact shadows.
- Mid-field grasses carry visible travelling wind waves. Wildflowers lag behind grass because their stems have greater inertia.
- Distant ridges, atmospheric haze and cloud shadows move much more slowly to establish scale.
- The palette moves from blue-green shadow through olive and hay to warm sunrise ivory. Saturation remains natural, never neon.
- Typography is editorial and quiet: `WILD / AIR`, the line “Where the wind remembers”, a short description and one contact action.

## Motion System

The wind is a deterministic sum of broad gusts, local ripples and small turbulence. Each plant receives a unique phase and stiffness. Movement starts at the tip, preserves a planted base and returns with damped inertia. Pointer input introduces a weak local eddy; it never controls the whole scene. Reduced-motion freezes the field at a composed breeze pose and disables parallax.

## Technical Design

- `contact4.html` owns semantic content, loader, navigation, CTA and accessible fallback copy.
- `contact-nature-core.js` owns deterministic wind math and the Three.js world. It exposes `createLivingMeadowWorld(THREE, options)`, `sampleWind(...)`, `update(...)` and `dispose()`.
- Grass, stems, petals and dew use instanced or point geometry to keep draw calls bounded.
- Custom shaders render the sky, terrain, blade translucency, atmospheric perspective and wind deformation without textures.
- `contact-nature.js` owns the renderer lifecycle, responsive quality profile, pointer easing, visibility pause, reduced-motion behavior and fallback activation.
- `contact-nature.css` owns the editorial overlay, film grain, optical vignette, loader and static fallback composition.

## Responsive and Accessibility

- Desktop renders the full density; mobile reduces instance counts and pixel ratio without changing composition.
- All interactive targets are at least 44px, keyboard focus is visible, and content remains readable without WebGL.
- `100dvh` and safe-area insets prevent mobile browser chrome clipping.
- WebGL failure shows a layered CSS dawn meadow with the same text and CTA.

## Acceptance Criteria

- `/contact4` and its CSS/JS assets return HTTP 200 without authentication.
- No external image or video assets are used.
- Grass, flowers, pollen, dew, terrain, sky and atmospheric layers are procedurally generated.
- Wind updates are deterministic for the same time and input.
- The page has no horizontal overflow at 375×812, 812×375 and 1440×900.
- Reduced-motion and WebGL-failure states preserve a finished composition.

