# AGENTS.md — Comic TV theme system (Android TV, Jetpack Compose)

## 1. Mission

Rebuild the Home screen of Comic TV so that each of the 5 themes has its own
layout, navigation, hero, cards and focus behavior, matching the approved mockups
pixel-for-pixel in structure and style.

Themes: `ComicTv` (amber), `Flix` (red), `Cinema` (gray, serif), `Max` (violet),
`Prime` (blue/cyan).

Today the themes only swap colors. That is the bug. A theme must change the
**structure** (navigation position, hero variant, card style), not just the palette.

## 2. Inputs (read before writing code)

- `/docs/design/THEMES_SPEC.md` — measured values per theme. Source of truth.
- `/docs/refs/<theme>/target.png` — approved mockups (`comictv`, `flix`,
  `cinema`, `max`, `prime`).
- `/docs/refs/<theme>/current.png` — current app screenshots, if present.

The mockup art (city, hooded character) is **placeholder**. Never ship it as an asset.
Mockup coordinates are in a 1280×720 px canvas. Conversion: `dp = px * 0.75`.

## 3. Target device

- Xiaomi TV Box S 4K (2nd Gen, Google TV) on a 55" 4K TV. Output is 4K; video plays
  at 4K. Design the UI in dp: the layout is 960×540 dp whether the system renders the
  UI framebuffer at 1080p or 4K. Do not hardcode pixel sizes.
- Hardware is modest: quad Cortex-A55, Mali-G31 MP2 GPU, 2 GB RAM. Treat GPU fill-rate
  and memory as the main constraints (blur, glow, large bitmaps).
- Viewing distance ~3 m. Input is D-pad only. No touch, no mouse.
- Do not rely on `RenderEffect` or other API 31+ visuals without a fallback.
- First step of Phase 0: log the real values at runtime (`Display` size, `densityDpi`,
  `Configuration.screenWidthDp/screenHeightDp`) and include them in the report.
- Safe area: 48 dp horizontal, 27 dp vertical.

## 4. Non-negotiables

1. No `if (theme == ...)` scattered through screens. Behavior comes from `ThemeSpec`.
2. Text never below 14sp. The mockup's 13–16 px text is a proportion reference, not a
   size to copy literally. Keep the hierarchy, scale up where needed.
3. Navigation must never overlap content. Reserve its space in layout.
4. Every focusable element has a visible focus state defined by the theme's `FocusStyle`.
5. BACK always goes up one level: close drawer → return to Home → exit app.
6. If a data field does not exist in the app (synopsis, edition, Favorites, History),
   omit the element. Never fake data in production code.
7. Keep existing features working: reading progress, import, extensions, catalog,
   downloads, backup, reader. This task only changes Home/Library presentation.
8. Do not add heavy dependencies. Embed only OFL fonts: Inter, Bebas Neue,
   Barlow Condensed, Cinzel.
9. Do not reinterpret the design. If something is ambiguous, pick the option closest
   to the mockup and list it under "Open questions" in your report.

## 5. Phase 0 — Discovery (no code changes)

The app is compiled; these class names were observed in the APK and are probably the
relevant files. Verify against source and adapt:

`LibraryScreen`, `LibraryHeader`, `LibraryHero`, `PrimeLibraryHero`, `PrimeEditionCard`,
`FeaturedComic`, `LibraryControls`, `CinemaBackdrop`, `CoverBackdrop`, `BackdropBlur`,
`LibraryBackdropImage`, `CinemaArtworkStore`, `CinemaImagePolicy`, `DetailsScreen`,
package `com.pelican.comictv`.

Deliver a short report:

- Where themes are defined and how they are selected/persisted.
- Which composables render nav, hero, rows and cards, and how they currently branch by theme.
- Which data is available per comic: cover, title, page progress, collection,
  history, optional horizontal artwork (`CinemaArtworkStore`).
- Current image loading path (library, sizes, caching, blur).

Stop after the report only if blocked; otherwise continue to Phase 1.

## 6. Phase 1 — Refactor to `ThemeSpec` (no visual change)

Create a single spec object per theme. Suggested shape (adapt names to the codebase):

```kotlin
private val Int.mp get() = (this * 0.75f).dp   // mockup px -> dp

enum class NavLayout { LEFT_RAIL, LEFT_SIDEBAR, RIGHT_DRAWER, BOTTOM_DOCK, TOP_BAR }
enum class HeroVariant { POSTER_LEFT, FULLBLEED_TEXT, FULLBLEED_CAROUSEL }
enum class ContinueCardStyle { COMPACT_LIST, ART_LARGE, GLASS_SERIF, GLASS, ART_16_9 }
enum class CollectionStyle { SQUARE_GRID, POSTER_ROW_CLIPPED, NONE, MINI_ROW_CLIPPED }

data class FocusStyle(
    val color: Color, val borderWidth: Dp, val scale: Float,
    val glow: Color?, val glowRadius: Dp, val durationMs: Int = 200,
)

data class ThemeSpec(
    val id: String,
    val navLayout: NavLayout,
    val heroVariant: HeroVariant,
    val continueCards: ContinueCardStyle,
    val collection: CollectionStyle,
    val focus: FocusStyle,
    val colors: ThemeColors,      // bg, surface, glass, accent, textPrimary, textSecondary, scrim stops
    val shapes: ThemeShapes,      // card radius, button radius, pill radius
    val type: ThemeTypography,    // display font, body font, sizes
    val motion: ThemeMotion,      // durations, easing
)
```

Per-theme core values:

| Theme | navLayout | heroVariant | continueCards | collection | accent | display font |
|---|---|---|---|---|---|---|
| ComicTv | LEFT_RAIL | POSTER_LEFT | COMPACT_LIST | SQUARE_GRID | `#F5B82E` | Inter 800 |
| Flix | LEFT_SIDEBAR | FULLBLEED_TEXT | ART_LARGE | POSTER_ROW_CLIPPED | `#E50914` | Bebas Neue |
| Cinema | RIGHT_DRAWER | FULLBLEED_TEXT | GLASS_SERIF | NONE | none (neutral) | Cinzel 600 |
| Max | BOTTOM_DOCK | FULLBLEED_TEXT | GLASS | NONE | `#9B6DFF` | Bebas Neue |
| Prime | TOP_BAR | FULLBLEED_CAROUSEL | ART_16_9 | MINI_ROW_CLIPPED | `#2AD4F5` | Barlow Condensed 800 |

Create `ThemedScaffold(spec)`:

- Renders the navigation according to `navLayout`.
- Computes the content insets so content never sits under the nav.
- Provides `LocalThemeSpec` via `CompositionLocal`.

Hero, rows, cards and buttons read from `LocalThemeSpec`. One implementation per
`HeroVariant` / card style, not per theme.

**Acceptance for Phase 1:** project builds; every theme still opens; D-pad and BACK
behave as before; screenshots before/after are visually equivalent.

## 7. Phases 2–6 — Implement one theme at a time

Order: **Max → Prime → Flix → ComicTv → Cinema.**
Use `/docs/design/THEMES_SPEC.md` for exact numbers. After each theme, run the
verification protocol (section 10) and wait for approval before the next.

### Shared building blocks (implement once, reuse)

- `HeroBackdrop`: full-bleed image + scrim layers (horizontal + vertical gradients
  from the spec) + optional blur.
- `ProgressBar(track = white @ 22%, fill = accent, height = 5 px*.75)`.
- `ThemedButton(primary/secondary)` with focus style.
- `ContinueRow`, `CollectionRow` driven by the card style enums.
- `GlassSurface(color, blur, border)` with a bitmap-based blur fallback.

### Max — acceptance checklist
- [ ] Bottom dock (x104,y596, 1070×82, radius 14, translucent violet, 6 items,
      active pill). Content never under the dock.
- [ ] Full-bleed hero, violet-tinted scrim left + bottom. Title Bebas ~60 px, caps.
- [ ] Primary button: violet 2 px border; secondary translucent.
- [ ] "Continuar lendo": 4 glass cards 270×128, thumbnail left, title, "Página X de Y", violet bar.
- [ ] Focus: violet border + scale 1.05. Logo top-left.

### Prime — acceptance checklist
- [ ] Top bar: active pill with 2 px cyan border, tabs, logo right, dark search field.
- [ ] Hero with 72 px Barlow Condensed title, cyan progress, white primary button with play icon.
- [ ] Right-side carousel of tall covers: focused 184×298 with cyan border + glow;
      neighbors 172×280 at 85% opacity; page dots.
- [ ] "Continuar lendo" as 16:9 cards with title over art and cyan progress.
- [ ] "Sua coleção" mini row, clipped at the bottom edge.

### Flix — acceptance checklist
- [ ] Left sidebar 220 px: logo, icon+label items, 3 px red underline on active; search pill top-right.
- [ ] Hero: Bebas 76 px two-line title, red progress, red primary button with play (no border).
- [ ] "Continuar lendo": 4 art-only cards 220×138; focused = 3 px red border + red glow.
- [ ] "Sua coleção": posters clipped at the bottom.

### ComicTv — acceptance checklist
- [ ] Icon-only left rail 64 px; amber dot on active.
- [ ] Top: logo, tabs Todas/Favoritos/Coleções (amber underline), right: Buscar | Histórico
      (omit Favoritos/Histórico entries if the data does not exist).
- [ ] Poster-left hero: featured cover 196×226 with 2 px amber border; title, edition/page line, amber progress, buttons.
- [ ] "Continuar lendo" compact list cards 330×68; "Sua coleção" square grid 194×194.

### Cinema — acceptance checklist
- [ ] Right drawer (x953, 327×490): blur + translucent dark, search box with white
      2 px border when focused, check mark on active. Opens with D-pad RIGHT, closes with LEFT/BACK.
- [ ] Serif (Cinzel) uppercase title; neutral palette; no accent color.
- [ ] 4 glass cards 276×128 with serif titles and white progress.

## 8. Focus and D-pad model

| Nav layout | Opens with | Returns with |
|---|---|---|
| TOP_BAR | UP | DOWN |
| BOTTOM_DOCK | DOWN | UP |
| LEFT_RAIL / LEFT_SIDEBAR | LEFT | RIGHT |
| RIGHT_DRAWER | RIGHT | LEFT or BACK |

- Initial focus on first load: primary hero button.
- Remember the last focused item in each row when returning to it.
- Rows scroll horizontally keeping the focused card fully visible; the next card is
  partially visible (clipped) to signal more content.
- No focus traps. Every screen must be fully navigable with D-pad only.

## 9. Images and performance

- Hero backdrop: request at ≥1920×1080. Cards: request at displayed size × density.
  Never upscale thumbnails.
- If only a vertical cover exists: center-crop + blur + gradient. Never stretch.
  Prefer horizontal artwork from `CinemaArtworkStore` when available.
- Blur: precompute into a cached bitmap (reuse `BackdropBlur`). No per-frame blur.
- Focus animation 150–300 ms. No jank on the Mi Box. If a theme drops frames, reduce
  glow radius and scale first, then blur radius.
- No layout shifts when images load (reserve sizes, use placeholders).

## 10. Verification protocol (mandatory for each theme)

1. Build with the project's existing build command and run on an Android TV 1080p emulator.
2. Capture screenshots with `adb exec-out screencap -p > file.png` for:
   Home (default focus), Home with a card focused, nav open (or focused), a row scrolled.
3. Save to `/docs/result/<theme>/`.
4. Compare each against `/docs/refs/<theme>/target.png` using the checklist above.
5. Report, in this format:

```
THEME: <name>
STATUS: matches | partial
MATCHED: <list>
DIFFERENCES: <list with element, expected, actual>
DEVIATIONS (intentional): <e.g. font size raised to 14sp minimum>
OPEN QUESTIONS: <list>
FILES CHANGED: <list>
```

Do not claim "matches" if any checklist item is unchecked.

## 11. Definition of done (per theme)

- All checklist items for the theme verified with screenshots.
- No regression in the other themes' Home.
- D-pad navigation works end to end; BACK behaves as specified.
- No text below 14sp; nav never overlaps content.
- No placeholder mockup art in the app.

## 12. Do not

- Do not copy the mockup illustrations into resources.
- Do not change reader, import, backup or download logic.
- Do not rename or remove existing public classes without need.
- Do not implement several themes at once.
- Do not mark a theme done without screenshots.
