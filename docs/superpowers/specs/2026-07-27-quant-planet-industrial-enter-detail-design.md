# Quant Planet Industrial ENTER and Detail Pass Design

## Goal

Replace the flat ENTER link with the approved industrial hatch concept and
increase the visible authored detail of the Quant Planet without changing its
round proportions, navigation contract, autonomous rotation, or performance
budget.

## Approved Visual Direction

The ENTER control uses concept B:

- a compact wide hexagonal mustard-yellow metal face;
- clipped corners with four cream/gold rivets;
- a shallow inset center panel;
- a thick charcoal hand-drawn outline;
- orange-gold side plates;
- a separate dark lower extrusion that reads as physical depth;
- centered cream `ENTER` lettering with a dark pixel-style outline.

The control remains visually subordinate to the nine foreground glyphs while
being more tactile and authored than the current rectangular button.

## Button Structure

The existing semantic anchor remains the only navigation control:

```html
<a class="planet-primary-action" data-enter-quant href="/quant">
  <span class="planet-primary-action__label">ENTER</span>
</a>
```

CSS pseudo-elements construct the hatch without a new raster asset:

- `::before` draws the inset face and rivets;
- `::after` draws the lower extrusion and orange side depth;
- the anchor supplies the outline, clipped-corner silhouette, focus ring, and
  hit target;
- the label supplies the cream pixel-like text and ink shadow.

The link stays keyboard-accessible and continues to navigate to `/quant`.

## Interaction and Motion

### Idle

The button performs an infrequent mechanical twitch rather than continuous
bobbing:

1. remain still from 0% through 72% of a 5.6-second cycle;
2. at 73%, nudge up by 1px and rotate by 0.4 degrees;
3. settle at 75%;
4. at 77%, perform a 0.5px counter-nudge at -0.2 degrees;
5. return to rest at 80% and remain still through 100%.

The motion uses transforms only and never moves surrounding layout.

### Hover and Keyboard Focus

- lift the complete hatch by 3px;
- increase the visible lower extrusion;
- brighten the yellow face slightly;
- keep the transition between 150 and 220ms;
- pause the idle twitch while directly engaged;
- show a high-contrast cream focus ring for keyboard users.

### Press

- move the front control down by 6px;
- reduce the lower extrusion to 1px;
- remove the lifted shadow;
- complete the visual response within 130ms.

The active state must not alter the clickable bounds or cause layout shift.

### Reduced Motion

`prefers-reduced-motion: reduce` disables the idle twitch. Hover, focus, and
press retain immediate non-animated state changes so interaction feedback is
still clear.

## Responsive Sizing

- desktop visual width: `clamp(156px, 11vw, 196px)`;
- desktop minimum height: 54px;
- compact landscape width: at least 132px;
- mobile width: at least 126px;
- all hit targets remain at least 48px high;
- the existing safe-area-aware bottom position remains unchanged.

The clipped-corner geometry scales proportionally at every breakpoint.

## Planet Detail Pass

Add detail only through existing instanced families so the world does not gain
new draw calls.

### Industrial Cluster

- secondary panel rivets;
- pipe collars and clamp tabs;
- gauge markers;
- ladder side rails paired with the existing rungs.

### Utility Cluster

- antenna connector blocks;
- roof-edge brackets;
- cable anchor caps;
- window mullions.

### Temple Peak

- layered eave teeth;
- entrance lintel accents;
- stair edge bands;
- small gold finial braces.

### Villages and Water

- window crossbars and sill shadows;
- door handles and canopy supports;
- bridge deck planks;
- shoreline foam accents and retaining stones.

### Terrain and Foliage

- second cliff strata bands on large exposed shelves;
- small rock chips near paths;
- restrained foliage size/color variation for depth.

Details must remain legible at the normal hero camera distance and must not
make the silhouette noisy or obscure the foreground glyphs.

## Performance Constraints

- high-profile complete scene: at most 75 draw calls;
- high-profile complete scene: at most 140,000 triangles;
- no new texture requests;
- no new animation frame owner;
- idle button animation uses compositor-friendly transforms and opacity only;
- existing WebGL fallback and static composition remain intact.

## Accessibility

- preserve the semantic anchor and `/quant` route;
- maintain a minimum 48px target height;
- retain a visible `:focus-visible` ring;
- ensure `ENTER` has sufficient contrast on the yellow face;
- do not rely on motion or color alone to communicate hover/press state;
- disable the idle twitch under reduced motion.

## Automated Verification

Add tests that prove:

- the button keeps the single `/quant` navigation contract;
- the hatch exposes clipped corners, rivets, inset face, and lower extrusion;
- hover lifts and active press reduces the extrusion;
- idle animation contains long stationary phases and short twitch phases;
- reduced motion disables the idle animation;
- responsive variants preserve the 48px minimum target;
- every new planet detail family has the intended minimum instance count;
- scene render statistics stay within the existing budgets;
- the complete JavaScript and Maven suites remain green.

## Browser Verification

Verify the completed control at:

- 1920×1080 wide desktop;
- 1280×720 standard desktop;
- 900×1100 narrow portrait;
- 375×812 mobile;
- hover, keyboard focus, press, and reduced-motion states;
- at least one full planet revolution to confirm detail visibility and no
  clipping.
