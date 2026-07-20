# Brik-Inspired Quant Homepage Design

## Intent

Replace the Aurora homepage completely with an original Quant AI landing page that uses the interaction language visible on Brik: a bright neutral canvas, segmented navigation, oversized direct typography, a central tool-like composer, floating instrumentation, and a modular gallery of working product routes.

This is an adaptation, not a copy. No Brik logo, copy, media, code, or branded asset is used.

## Product Promise

The homepage should make Quant AI feel usable before the visitor leaves the first screen.

- Primary headline: `See the market. Shape the signal.`
- Supporting copy: `Search a symbol, scan the market, and move from raw data to a decision-ready view.`
- Primary interaction: a real GET form to `/stocks` with `name="symbol"`.
- Primary CTA: `/quant`.
- Every prompt chip and product card links to an existing application route.

## Visual Direction

### Surfaces

- Page background: warm light gray `#e8e9e6`.
- Main surface: white `#ffffff`.
- Dark instrument surface: graphite `#5d5f5b`.
- Primary text: near-black `#0b0b0a`.
- Secondary text: `#5e605b`.
- Border: `#cccec8`.
- Signal accents: coral `#ff4f45`, hot pink `#ff3dbf`, electric lime `#d9ff57`, and sky blue `#77a8ff`.
- Use accents in contained visuals and one closing CTA; keep reading surfaces neutral.

### Typography

- Use local system fonts only.
- Display: Arial/Helvetica/system sans, `font-weight: 800`, tight tracking.
- Body: Arial/Helvetica/system sans, `font-weight: 400–600`.
- Numbers use `font-variant-numeric: tabular-nums`.
- Hero headline uses `clamp(3.4rem, 7.4vw, 7.2rem)` and a maximum line length around 15 characters per line.

### Geometry

- Global page gutter: `20px` desktop, `12px` mobile.
- Card radius: `14px`.
- Nested control radius: `10px`.
- Segmented navigation cells share borders and are not floating glass pills.
- Bento gap: `10px`.
- Use a restrained shadow scale; separation comes mainly from borders and surface contrast.

## Page Structure

### 1. Sticky segmented navigation

- Brand segment links to `/`.
- Dynamic `mainMenus` remain server-rendered from the existing DB-backed menu service.
- Dashboard segment links to `/quant`.
- Desktop: single row.
- Tablet/mobile: brand and CTA remain visible while the menu row becomes a native horizontal scroller.
- All interactive targets are at least `44px` tall.

### 2. Tool-like hero

- Oversized centered headline and concise supporting copy.
- A real composer form:
  - `action="/stocks"`
  - `method="get"`
  - visible label
  - `input#home-symbol[name="symbol"]`
  - submit button with text plus a local SVG arrow
- Prompt links:
  - `/quant?index=SP500`
  - `/stocks`
  - `/stocks/heatmap?index=SP500`
  - `/etfs`
  - `/signals/backtest`
- Hero atmosphere is an original Canvas 2D signal field: broad blurred ribbons, monochrome at rest, more saturated with pointer energy. It must never obscure content.

### 3. Floating Quant instrument

- A contained graphite panel beside the composer on desktop and below it on mobile.
- Three clearly labelled illustrative values:
  - Signal `79 / 100`
  - 20D Alpha `+2.4%`
  - Risk `Medium`
- Visible qualifier: `Illustrative model view · dashboard data remains authoritative`.
- Static semantic content; no `aria-live`.

### 4. Product Bento gallery

Six working route cards:

1. Quant Intelligence → `/quant`
2. Stocks → `/stocks`
3. Market Heatmap → `/stocks/heatmap`
4. ETF Radar → `/etfs`
5. Strategy Atelier → `/atelier`
6. Signal Backtest → `/signals/backtest`

Each card contains a heading, concise description, SVG arrow, and an original CSS/Canvas visual. Cards are links, not nested collections of controls.

Desktop grid uses 12 columns with an asymmetric mix of wide and compact cards. Tablet uses two columns. Mobile uses one column.

### 5. Method and closing CTA

- A large answer-first statement explains the flow from market data to a decision-ready view.
- Three compact method steps: `Observe`, `Resolve`, `Act`.
- The closing CTA uses a coral-to-pink signal gradient and links to `/quant`.
- Footer retains `id="contact"` so the DB fallback menu anchor remains valid.
- Product gallery retains `id="systems"` as an alias target for the existing fallback menu.

## Interaction and Motion

- Motion communicates state:
  - hero content reveals once on load;
  - Bento cards reveal as they enter the viewport;
  - pointer energy changes the Canvas field;
  - card hover moves only decorative layers, never layout bounds.
- Micro-interactions: `150–300ms`.
- Section reveal: up to `420ms`, staggered by `40–60ms`.
- Animate only `transform` and `opacity`.
- Use the browser Web Animations API with Motion-style easing; do not add React or a frontend build pipeline.
- With `prefers-reduced-motion: reduce`:
  - render one static Canvas frame;
  - disable reveals, drift, hover translation, and smooth scroll;
  - keep all content visible.

## Responsive Rules

- `>= 1024px`: hero composer and instrument form a two-column stage; Bento uses 12 columns.
- `768–1023px`: hero stacks; Bento uses two columns.
- `< 768px`: menu horizontally scrolls; hero, instrument, Bento, method, and CTA are one column.
- At `375px`, the document must not scroll horizontally.
- Input and submit controls stack if needed; neither may overlap.
- No text smaller than `12px`; body copy is at least `16px` on mobile.

## Accessibility

- Skip link targets `#main-content`.
- One `h1`; sequential heading levels.
- Visible form label; placeholder does not replace the label.
- Every icon is `aria-hidden="true"`.
- `:focus-visible` uses a 3px near-black ring with offset on light surfaces and a white ring on dark/gradient surfaces.
- Native links, form, and `details/summary` semantics are preferred.
- No positive `tabindex`, focus trap, or hover-only action.
- Normal text contrast meets WCAG AA.

## Architecture and Data Constraints

- Stack remains Java 21, Spring Boot, Thymeleaf, CSS, and vanilla JavaScript.
- `HomeController#index` keeps its existing contract: call `menuService.mainMenus()`, expose `mainMenus`, return `index`.
- The homepage must not call `StockDashboardViewSnapshotService`.
- No external request is added to the page render path.
- Homepage CSS, JS, fonts, SVGs, and Canvas work are local.
- No `fetch`, `XMLHttpRequest`, `WebSocket`, `EventSource`, remote font stylesheet, or protocol-relative asset URL in homepage-owned resources.

## Acceptance

- The homepage is visually recognisable as the new bright, modular direction rather than Aurora.
- The composer works with JavaScript disabled.
- Every prominent card and chip reaches an existing route.
- Desktop, tablet, and 375px mobile screenshots have complete, unclipped content.
- Keyboard focus is visible and follows DOM order.
- Reduced-motion keeps identical information with no decorative movement.
- Maven tests, homepage Node tests, and browser console checks pass.
