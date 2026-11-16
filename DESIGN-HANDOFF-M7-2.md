# DESIGN HANDOFF — M7-2

**Source:** `Calculator App.html` · **1,194,014 bytes** · self-contained
HTML5 · SHA256 `024097D0C7FFBFF1…` · saved 2026-07-28 01:20:28
**Engine at extraction:** `81783bc` (core 477 + api 3 + android 10 = 490 tests, 0 failures)
**Extracted:** 2026-07-28

> **How to read this file.** Every value below was copied from the bundle. Where the bundle
> does not state something, it appears in **OPEN** — blank, not guessed. Nothing here is
> inferred from the earlier gap analysis, from `Calculator App (1).html` (the older
> 1,155,026-byte version), or from what the engine happens to do.

**Decoding note.** The design document is nested: the outer file's 4th `<script>` block is a
JSON-escaped string containing the real 211,792-char design document. Findings cite that
decoded document. `Calculator App (2).html` is byte-identical to the source (duplicate
download); `Calculator App (1).html` is an **older, different** build and was not used.

**Extraction coverage — stated so a gap cannot hide.** Extraction ran as a 7-lens parallel
sweep over the decoded bundle plus first-hand verification. **One lens (`check:gaps`) died on a
connection error and returned nothing.** Its scope was the engine side of `#n`, the `Func`
node, grouping, tape states, angle/`S⇔D`/percent, and root order — **all of which were
verified by hand against `core/` before this document was written**, so its death left no
unchecked claim. Recorded rather than silently absorbed: a dead agent is NEVER-CHECKED, not
CHECKED-AND-PASSED (Article 11).

---

## 0 · The two lists that define M7-2

Full detail in §4. Stated first because they are the deliverable.

### MISSING — design assumes it, engine does not have it

| # | Requirement | Engine today | Severity |
|---|---|---|---|
| **M-1** | **`Func` node in the 2-D entry model** with an `arg` field, and a display name distinct from the engine spelling (`asin` shows as `sin⁻¹`) | `entry/Node.kt` has `Atom │ Fraction │ Power │ Root │ Paren` — **no `Func`** | **HIGH** — this is gap **G-3**, and the design now specifies it completely |
| **M-2** | **`Unverified` as a TAPE state** — "honest partial knowledge": some roots proven, one unconfirmed | `tape/EntryValue.kt` has **7** states; `Unverified` exists only on `graph/RootResult`, not on `EntryValue` | **HIGH** — the design's tape is an **8**-state machine |
| **M-3** | **Devanagari numeral OUTPUT** — every rendered digit becomes `०१२३४५६७८९` when language is Hindi | `Formatter` emits ASCII only. The engine *accepts* Devanagari input (D-028/D-030) but cannot *render* it | **MEDIUM** |
| **M-4** | **Stale carries the previous value** so the row reads "was, now updating" — never blank | `EntryValue.Stale(previous: Value?)` exists ✔, but nothing computes/holds a pending recompute | **LOW — mostly present** |
| **M-5** | **Ambiguity resolved in place** via two chips that re-run the entry as equation *or* assignment | `RouteResult.Ambiguous(candidates)` exists (D-016.5) but **has no producer** — the grammar removes the ambiguity, so `x = 5` is an equation, never ambiguous | **MEDIUM — design and engine genuinely disagree** |
| **M-6** | Angle-mode toggle surfaced as a chip cycling **DEG/RAD/GRAD** | `Settings.angle` exists ✔ — no engine work, M7-2 UI only | **NONE (UI only)** |

### OPEN — the bundle does not settle it

| # | Question | Status in the bundle |
|---|---|---|
| **O-1** | **How does a user TYPE a `#n` reference?** | **NOT SPECIFIED.** Gap **G-6 stays open** — see §4.3. The design *renders* `#n` and *reuses by copying nodes*, but no key, gesture, or insertion path for `#` exists anywhere in the bundle. |
| ~~O-2~~ | ~~Disabled key state~~ | **CLOSED — confirmed absent.** Zero occurrences of `disabled`, `aria-disabled`, `cursor:not-allowed`. The two `pointer-events:none` are decorative overlays in the thumb-arc diagram. Only `default` and `pressed` exist. Ruled by **D-031.1**. |
| ~~O-3~~ | ~~Error state for the keypad~~ | **CLOSED — confirmed absent.** The demo's entire error path is `} catch(e) { this.result = '—'; }`. The keypad never changes; the result line shows an em-dash in its normal style. Ruled by **D-031.2**. |
| ~~O-4~~ | ~~Tape max height / cap~~ | **ANSWERED:** `max-height:78px; overflow:auto; padding:10px 14px 2px; gap:6px`, and a hard cap of **6 entries** (`.slice(-6)`). The transition graph is **not** in the bundle — derived from the engine instead, as **D-032**. |
| **O-5** | Portrait app-shell width | All mockups are drawn inside a **fixed 330 px** (once 360 px) phone frame with 11 px padding. The real target width is never stated — but the *consequence* is now computed, §2.1. |
| **O-6** | Dark/AMOLED elevation | `--shadow` becomes `0 0 0` in both dark themes, so shadows vanish — but no replacement elevation (border/tint) is specified. Ruled by **D-031.3** (accept the surface ladder; revisit on device). |
| **O-8** | Hindi UI string table | Only unit names carry Hindi (`लाख`, `करोड़`, `तोला`, `बीघा`, `गुंठा`). No translations for keys, chips, settings, or tape labels. Ruled by **D-031.5** (numerals only). |
| ~~O-9~~ | ~~Marker shapes for plot curves~~ | **CORRECTED — markers do not exist.** The section heading claims *"hue + dash + marker"*, but `curveTokens` carries only `{name, hex, dash}`, the word "marker" appears **once in the entire bundle (in that heading)**, and nothing renders one. The real third channel is the **curve exit tag**. |
| **O-10** | Touch target ≥ 48 dp compliance | Key height is 56 px ✔, but chips are 34 px and drawer buttons 40 px — under 48 dp, with no hit-area expansion anywhere. Ruled by **D-031.4** (keep visuals, add 48 dp hit areas). |
| ~~O-11~~ | ~~Landscape numbers~~ | **ANSWERED — landscape IS designed**, full geometry in §2.4. It also reintroduces six scientific keys the portrait surface hides. Ruled by **D-031.6** (deferred; portrait only). |

---

## 1 · TOKENS

### 1.1 Palette — all three themes

Verbatim from the design's `:root` / `[data-theme="dark"]` / `[data-theme="amoled"]` block.
**Every token is defined in all three themes** — no gaps.

| Token | Light | Dark | AMOLED |
|---|---|---|---|
| `--paper` | `#EDF0EA` | `#0E141E` | `#000000` |
| `--sheet` | `#F8FAF5` | `#161D2A` | `#080B10` |
| `--sheet-2` | `#FFFFFF` | `#1C2431` | `#0D1119` |
| `--sheet-edge` | `#E0E6DB` | `#2A3341` | `#1B222E` |
| `--ink` | `#182338` | `#E9EDEF` | `#EEF1F3` |
| `--ink-2` | `#59617A` | `#9AA4B4` | `#8B95A5` |
| `--ink-3` | `#98A0B1` | `#616B7D` | `#525C6E` |
| `--grid` | `#DBE5DE` | `#1B2330` | `#12181F` |
| `--grid-2` | `#C6D5CB` | `#2A3547` | `#212B39` |
| `--amber` | `#DA8618` | `#F0A63E` | `#F2A93F` |
| `--amber-2` | `#9C5C05` | `#D98A1E` | `#D98A1E` |
| `--amber-soft` | `#FBEFD8` | `#2C2415` | `#211a0e` |
| `--blue` | `#2F6DB0` | `#5BA1E1` | `#5BA1E1` |
| `--blue-2` | `#194E86` | `#8CC0F0` | `#8CC0F0` |
| `--blue-soft` | `#E4EDF6` | `#16273A` | `#0c1a29` |
| `--key` | `#FFFFFF` | `#1C2431` | `#0D1119` |
| `--key-edge` | `#E5EAE0` | `#2B3546` | `#1D2531` |
| `--key-press` | `#EDF1E9` | `#242E3E` | `#161C27` |
| `--key-ink` | `#1B2740` | `#E9EDEF` | `#EEF1F3` |
| `--shadow` | `30 45 40` | `0 0 0` | `0 0 0` |

`--shadow` is an **RGB triplet, not a colour** — consumed as `rgb(var(--shadow)/.05)`. Setting it
to `0 0 0` in both dark themes is how shadows are neutralised (see **O-6**).

Two hard-coded hexes escape the token system, both intentional-looking:
`#fff` on the `=` key glyph and on the `Failed` badge glyph.

### 1.2 Plot-curve set

`const curveTokens=[…]` — hue **and** dash, so curves stay distinguishable without colour.
The five hues are the **Okabe–Ito** colour-blind-safe palette.

| Name | Hex | Dash pattern | Marker |
|---|---|---|---|
| primary | `#0072B2` | `0` (solid) | **none — do not implement** |
| secondary | `#E69F00` | `8 5` | none |
| tertiary | `#009E73` | `2 4` | none |
| quaternary | `#CC79A7` | `10 4 2 4` | none |
| quinary | `#D55E00` | `1 5` | none |

> **Markers do not exist — the bundle over-promises its own colour-blind story.** The section
> heading reads *"Plot curves — Okabe-Ito, colourblind-safe (**hue + dash + marker**)"*, but
> `curveTokens` carries only `{name, hex, dash}`; the word "marker" appears **exactly once in
> the whole export — in that heading**; and no marker geometry, size, or draw call exists.
>
> **The actual third channel is the curve exit tag** — the curve's label drawn at the right
> edge in `600 10px 'JetBrains Mono'`, clamped vertically to the plot area. So identification
> is **hue + dash + end label**. Build that, not markers.

The live demo binds only three (`f₁` solid `#0072B2`, `f₂` `dash:'7 5'`, `f₃` `dash:'2 4'`
`#009E73`) — note `f₂`'s `7 5` differs from the token table's `8 5`. **The token table is
authoritative**; the demo's is a local literal.

Identification is reinforced by a **curve exit tag** — `font "600 10px 'JetBrains Mono'"`
drawn at the right edge — so a curve is identifiable by hue, dash, *and* label.

### 1.3 Type scale

Two families, three roles. Global UI stack: **`'IBM Plex Sans', system-ui, sans-serif`**.
Numeric/data type is **`'JetBrains Mono', monospace`** with
**`font-feature-settings:'tnum' 1,'zero' 1`** (tabular figures + slashed zero) — mandatory on
every numeric surface.

**The six named steps** (the document's only named scale): Display 34 · Title 26 · Body 15 ·
Label 13 · Micro 11 · Numeral 27 px.

**Roles as actually used** (all sizes literal px = FIXED):

| Role | Family | Weight | Size | Line-height | Colour |
|---|---|---|---|---|---|
| Page H1 / Display | IBM Plex Sans | 700 | 34 px | 1.05 | `--ink` |
| Lede / Body | IBM Plex Sans | 400 | 15 px | 1.5 | `--ink-2` |
| Kicker / eyebrow | JetBrains Mono | 600 | 12 px | — (ls `.16em`) | `--amber-2` |
| Caption / token label | JetBrains Mono | 400 | 10 px | — (ls `.1em`) | `--ink-3` |
| **Entry line** | JetBrains Mono | 500 | **19 px** | 1.2 | `--ink-2` |
| **Result (main)** | JetBrains Mono | 600 | **33 px** | 1.08 (ls `-.01em`) | `--amber-2` |
| **Result ≈ (decimal)** | JetBrains Mono | 400 | **13 px** | — | `--ink-3` |
| Key — numeric | JetBrains Mono | 500 | 22 px | 1 | `--key-ink` |
| Key — operator | JetBrains Mono | 600 | 23 px | 1 | `--ink` |
| Key — utility `( )` | JetBrains Mono | 600 | 19 px | 1 | `--ink-2` |
| Key — `fn` | IBM Plex Sans | 600 | 15 px | 1 | `--amber-2` |
| Key — `⌫` | IBM Plex Sans | 600 | 19 px | 1 | `--amber-2` |
| Key — `=` | JetBrains Mono | 700 | 25 px | 1 | `#fff` |
| Utility chip (DEG/Ans) | JetBrains Mono | 600 | 12 px | — | `--ink-2` |
| Tab / setting chip | IBM Plex Sans | 600 | 12 px | — (ls `.01em`) | varies |
| Tape state badge | JetBrains Mono | 700 | 12 px | — | per state |
| Graph tick label | JetBrains Mono | 400 | 10 px | — | `--ink-3` |
| Reveal axis numeral | JetBrains Mono | 400 | 11 px | — | `--ink-3` |

**Devanagari pairing — SPECIFIED.** The font is **`IBM Plex Sans Devanagari`**, the same
superfamily as the Latin UI face, and the choice is deliberate: *"Its Devanagari sibling
carries Hindi without a second type voice."*

| Use | Stack |
|---|---|
| Display specimen | `font-family:'IBM Plex Sans Devanagari'` — `रेखागणित` |
| Unit-browser names | `font-family:'IBM Plex Sans Devanagari','IBM Plex Sans'` at 13.5 px, `--ink` |

So the pairing is **IBM Plex Sans (Latin) + IBM Plex Sans Devanagari (Devanagari)**, with the
Latin face as the fallback in the stack. M7-2 must bundle the Devanagari family — it is not a
system font on all Android versions.

### 1.4 Spacing scale

Stated verbatim: **"8 px minor cell · 40 px major. Keys, gaps and margins are integer
multiples — the layout literally sits on the paper."**

The paper grid is a real CSS background on the root container — two `linear-gradient`s at
`--grid` (minor) plus `--grid-2` (major). This is the same substrate the graph reveal turns
into axes (§2.5).

| Step | Value | Observed use |
|---|---|---|
| ×1 | **8 px** | keypad gap; reveal keypad gap |
| — | 5, 6, 7 px | sub-grid: diagram gaps, chip row gap (6), drawer gap (7) |
| — | 9, 10, 11, 12 px | tape row gap (11), tape padding (12/14), chip padding |
| — | 14, 18, 22 px | card padding, section gaps |
| ×5 | **40 px** | major cell; drawer button height (40) |

Not every value is a multiple of 8 — 5/7/9/11 px appear in diagrams and chips. **The 8/40
claim holds for the keypad and layout, not for every inner padding.**

### 1.5 Kotlin constants (ready for a Compose theme file)

```kotlin
package com.vaibhav.calc.android.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Design tokens extracted verbatim from "Calculator App.html" (2026-07-28). */
object CalcTokens {

    // ---- palette: three themes -------------------------------------------
    data class Palette(
        val paper: Color, val sheet: Color, val sheet2: Color, val sheetEdge: Color,
        val ink: Color, val ink2: Color, val ink3: Color,
        val grid: Color, val grid2: Color,
        val amber: Color, val amber2: Color, val amberSoft: Color,
        val blue: Color, val blue2: Color, val blueSoft: Color,
        val key: Color, val keyEdge: Color, val keyPress: Color, val keyInk: Color,
        /** RGB triplet for shadow; 0,0,0 means "no shadow" in the dark themes. */
        val shadowRgb: Triple<Int, Int, Int>,
    )

    val Light = Palette(
        paper = Color(0xFFEDF0EA), sheet = Color(0xFFF8FAF5), sheet2 = Color(0xFFFFFFFF),
        sheetEdge = Color(0xFFE0E6DB),
        ink = Color(0xFF182338), ink2 = Color(0xFF59617A), ink3 = Color(0xFF98A0B1),
        grid = Color(0xFFDBE5DE), grid2 = Color(0xFFC6D5CB),
        amber = Color(0xFFDA8618), amber2 = Color(0xFF9C5C05), amberSoft = Color(0xFFFBEFD8),
        blue = Color(0xFF2F6DB0), blue2 = Color(0xFF194E86), blueSoft = Color(0xFFE4EDF6),
        key = Color(0xFFFFFFFF), keyEdge = Color(0xFFE5EAE0), keyPress = Color(0xFFEDF1E9),
        keyInk = Color(0xFF1B2740),
        shadowRgb = Triple(30, 45, 40),
    )

    val Dark = Palette(
        paper = Color(0xFF0E141E), sheet = Color(0xFF161D2A), sheet2 = Color(0xFF1C2431),
        sheetEdge = Color(0xFF2A3341),
        ink = Color(0xFFE9EDEF), ink2 = Color(0xFF9AA4B4), ink3 = Color(0xFF616B7D),
        grid = Color(0xFF1B2330), grid2 = Color(0xFF2A3547),
        amber = Color(0xFFF0A63E), amber2 = Color(0xFFD98A1E), amberSoft = Color(0xFF2C2415),
        blue = Color(0xFF5BA1E1), blue2 = Color(0xFF8CC0F0), blueSoft = Color(0xFF16273A),
        key = Color(0xFF1C2431), keyEdge = Color(0xFF2B3546), keyPress = Color(0xFF242E3E),
        keyInk = Color(0xFFE9EDEF),
        shadowRgb = Triple(0, 0, 0),
    )

    val Amoled = Palette(
        paper = Color(0xFF000000), sheet = Color(0xFF080B10), sheet2 = Color(0xFF0D1119),
        sheetEdge = Color(0xFF1B222E),
        ink = Color(0xFFEEF1F3), ink2 = Color(0xFF8B95A5), ink3 = Color(0xFF525C6E),
        grid = Color(0xFF12181F), grid2 = Color(0xFF212B39),
        amber = Color(0xFFF2A93F), amber2 = Color(0xFFD98A1E), amberSoft = Color(0xFF211A0E),
        blue = Color(0xFF5BA1E1), blue2 = Color(0xFF8CC0F0), blueSoft = Color(0xFF0C1A29),
        key = Color(0xFF0D1119), keyEdge = Color(0xFF1D2531), keyPress = Color(0xFF161C27),
        keyInk = Color(0xFFEEF1F3),
        shadowRgb = Triple(0, 0, 0),
    )

    // ---- plot curves: Okabe-Ito, hue + dash --------------------------------
    data class CurveToken(val name: String, val color: Color, val dash: List<Float>)

    val Curves = listOf(
        CurveToken("primary",    Color(0xFF0072B2), emptyList()),
        CurveToken("secondary",  Color(0xFFE69F00), listOf(8f, 5f)),
        CurveToken("tertiary",   Color(0xFF009E73), listOf(2f, 4f)),
        CurveToken("quaternary", Color(0xFFCC79A7), listOf(10f, 4f, 2f, 4f)),
        CurveToken("quinary",    Color(0xFFD55E00), listOf(1f, 5f)),
    )

    // ---- spacing: 8px minor cell, 40px major ------------------------------
    object Space {
        val minor = 8.dp
        val major = 40.dp
        val keypadGap = 8.dp
        val chipGap = 6.dp
        val drawerGap = 7.dp
        val tapeRowGap = 11.dp
    }

    // ---- type: two families, tabular figures on all numerics --------------
    object Type {
        // family = 'IBM Plex Sans' (UI) / 'JetBrains Mono' (data)
        // NOTE: every numeric surface needs tnum + slashed-zero features.
        val displaySize = 34.sp;  val displayWeight = FontWeight.Bold
        val titleSize   = 26.sp
        val bodySize    = 15.sp
        val labelSize   = 13.sp
        val microSize   = 11.sp
        val numeralSize = 27.sp

        val entrySize   = 19.sp;  val entryWeight  = FontWeight.Medium
        val resultSize  = 33.sp;  val resultWeight = FontWeight.SemiBold
        val approxSize  = 13.sp
        val keyNumSize  = 22.sp;  val keyNumWeight = FontWeight.Medium
        val keyOpSize   = 23.sp;  val keyOpWeight  = FontWeight.SemiBold
        val keyUtilSize = 19.sp
        val keyFnSize   = 15.sp
        val keyEqSize   = 25.sp;  val keyEqWeight  = FontWeight.Bold
        val chipSize    = 12.sp
    }

    // ---- component geometry ------------------------------------------------
    object Dim {
        val keyHeight = 56.dp          // FIXED
        val keyRadius = 13.dp
        val keypadColumns = 4          // each 1fr -> width SCALES
        val chipHeight = 34.dp
        val drawerButtonHeight = 40.dp
        val tapeBadge = 20.dp
        val tapeBadgeRadius = 6.dp
        val entryMinHeight = 28.dp
        val resultMinHeight = 44.dp
        val flickThresholdPx = 14f     // upward dy that selects the flick action
        val pressTranslateY = 1.5f
        val backspaceHoldDelayMs = 380L
        val backspaceRepeatMs = 70L
        val revealDurationMs = 1800L
    }
}
```

---

## 2 · COMPONENT SPECS

### 2.1 Keypad

**Container:** `display:grid; grid-template-columns:repeat(4,1fr); gap:8px; padding:8px 12px 18px;`

- **4 columns × 5 rows = 20 keys.**
- **Column width SCALES** (`1fr`). **Key height is FIXED at 56 px.** Gap fixed 8 px.
- Responsive rule, verbatim: *"The grid is the constant across every size — the layout
  reflows on it, never off it. Landscape puts the tape and keypad side by side so the thumb
  still owns the digits; **the extra width becomes history, not bigger keys.**"*

> **Read that quote carefully — it is scoped to LANDSCAPE.** It is the sentence immediately
> after "Landscape puts the tape and keypad side by side." **In portrait the keys DO get
> wider**, because the grid is `repeat(4,1fr)` and the tape is stacked above, not beside.

**Portrait key width, computed** — `key = (inner − 24 px padding − 24 px gaps) ÷ 4`:

| Inner width | Key width | Key height |
|---|---|---|
| **308 px** (the 330 px mockup frame, less 11 px padding each side) | **65 px** | **56 px** |
| **360 dp** | **78 dp** | **56 dp** |
| **430 dp** | **95.5 dp** | **56 dp** |

**Fixed at every width:** key height 56, gap 8, radius 13, keypad padding 8/12/18, every font
size, tape `max-height` 78. **Scaling:** key width only. Keys therefore grow **wider, never
taller**, and in portrait the tape does **not** absorb the extra width.

**Base style (every key):** `border-radius:13px; height:56px; border:1px solid var(--key-edge);
transition:transform .05s; touch-action:none; user-select:none;`

**Layout** — exactly as `defs` orders it:

| Row | Col 1 | Col 2 | Col 3 | Col 4 |
|---|---|---|---|---|
| 0 | `fn` | `(` | `)` | `÷` ↑ **√** |
| 1 | `7` | `8` | `9` | `×` ↑ **xʸ** |
| 2 | `4` | `5` | `6` | `−` ↑ **a⁄b** |
| 3 | `1` | `2` | `3` | `+` |
| 4 | `0` | `.` ↑ **π** | `⌫` | `=` |

This matches the engine's `Keypad.basic()` exactly — including `⌫` in the bottom arc and
**no `Ans` key** (Ans is a chip, §2.2).

**Per-kind styling**

| Kind | Background | Colour | Font |
|---|---|---|---|
| num | `--key` | `--key-ink` | 500 22px JetBrains Mono |
| op | `--sheet-2` | `--ink` | 600 23px JetBrains Mono |
| util `( )` | `--sheet-2` | `--ink-2` | 600 19px JetBrains Mono |
| fnkey | `--amber-soft`, border `--amber` | `--amber-2` | 600 15px IBM Plex Sans |
| del `⌫` | `--sheet-2` | `--amber-2` | 600 19px IBM Plex Sans |
| eq `=` | `--blue`, border `--blue-2` | `#fff` | 700 25px JetBrains Mono |

**States**

| State | Treatment |
|---|---|
| **default** | as the table above |
| **pressed** | `transform:translateY(1.5px)` + `filter:brightness(.95)`, applied on pointer-down and cleared on pointer-up |
| **disabled** | **OPEN (O-2)** — not specified |
| **error** | **OPEN (O-3)** — keys have no error state; errors live on the tape row |

**The four flick affordances** — all **upward**, threshold **`dy < -14` px**, resolved once at
pointer-up (`if(alt && dy<-14) alt(); else prim();`), with `setPointerCapture` on down.

| Key | Tap | Flick ↑ inserts | Visible label? |
|---|---|---|---|
| `÷` | `/` | `{t:'sqrt',rad:[]}` | **no** |
| `×` | `*` | `{t:'pow',exp:[]}` | **no** |
| `−` | `-` | `{t:'frac',num:[],den:[]}` | **no** |
| `.` | `.` | `π` (a constant, not a structure) | **no** |

> ⚠️ **Conflict with the shipped M7-1 scaffold.** The design is explicit: *"flick has **NO
> visible label**, so nothing scientific shows"*, and every key renders `alt:''` with
> `ghostStyle:'display:none;'`. **M7-1's `KeyButton` renders `key.flickUpLabel` as a visible
> mini-label — that must be removed in M7-2.** The engine's `Key.flickUpLabel` stays as data
> (it feeds discoverability elsewhere); the *default surface* shows nothing.

**`⌫` hold-to-repeat:** delete once on pointer-down, then after **380 ms** repeat every
**70 ms** (`setTimeout(…380)` → `setInterval(…70)`).

**`fn` drawer:** opens a bottom sheet — overlay `rgba(8,12,18,.34)`, sheet `width:100%`,
`border-radius:18px 18px 0 0`, palette grid **4 columns, gap 7 px, buttons 40 px high,
radius 8 px**, `500 13px JetBrains Mono`. Entries include `sin⁻¹ cos⁻¹ tan⁻¹ ∛` mapped to
`asin acos atan cbrt`.

### 2.2 Entry / display strip

Container `padding:8px 14px 15px; text-align:right`. Three stacked lines, all right-aligned,
all `JetBrains Mono` with `tnum`+`zero`:

| Line | Spec |
|---|---|
| **Entry** | `min-height:28px`, 19 px / 500, `--ink-2`, line-height 1.2, `flex-wrap:wrap` |
| **Result** | `min-height:44px`, 33 px / 600, **`--amber-2`**, ls `-.01em`, line-height 1.08, `margin-top:6px` |
| **≈ decimal** | 13 px, `--ink-3`, row `gap:10px; margin-top:5px; min-height:18px` |

**`S⇔D` chip** — rendered only when `sdVisible`: `600 10px JetBrains Mono`, ls `.06em`,
`--blue-2` on `--blue-soft`, 1 px `--blue` border, radius 6 px, padding 2 px 8 px.
*(The engine already exposes exactly this predicate as `Formatter.hasDistinctForms`.)*

**Utility chip row** (above the keypad, below the display): `display:flex; gap:6px;
padding:12px 12px 6px;` — two chips, each `flex:1`, **height 34 px**, radius 9 px,
`600 12px JetBrains Mono`, `--sheet-2` on `--sheet-edge`:
1. **angle toggle** (`{{ angleLabel }}`)
2. **`Ans`** (`insertAns`) — confirming Ans is a chip, never a key.

**Undo snackbar:** `margin:2px 12px 0`, background `--ink`, text `--sheet`, radius 9 px,
padding 8 px 12 px, message 12 px/500, and an **UNDO** button `700 11px IBM Plex Sans`,
ls `.08em`, colour `--amber`. Auto-dismisses after **4200 ms**.

### 2.3 The tape — an EIGHT-state machine

> **Two distinct renderings, do not conflate them.** The bundle draws the tape twice:
> **(a) the LIVE tape** inside the phone mockup — the running history you tap to reuse; and
> **(b) the STATE GALLERY** in §03 — one exemplar row per state, carrying the badge and blurb.
> The eight states and their badges come from (b); the everyday row metrics come from (a).

**Container (the live tape):** `max-height:78px; overflow:auto; padding:10px 14px 2px;
display:flex; flex-direction:column; gap:6px;` — **fixed height, then scrolls.**
**Retained entries are hard-capped at 6:** `[…this.history, {…}].slice(-6)`.

**(a) Live tape row** (the shipping component): a full-width reuse button —
`display:flex; justify-content:flex-end; align-items:baseline; gap:8px;
font-family:'JetBrains Mono',monospace; text-align:right; opacity:.62;`
Expression `12px --ink-2`, `=` separator `--ink-3`, result `12px/600 --ink`. Right-aligned,
baseline-aligned, and **dimmed to .62** so history recedes behind the current line.

**(b) State-gallery row** (the eight-state legend, and the source of the badge spec):
`display:flex; gap:11px; padding:12px 14px 12px 12px; border-bottom:1px solid var(--grid);`
- **selected/pulsing row:** `background:var(--amber-soft); box-shadow:inset 3px 0 0 var(--amber); border-radius:6px;`
- **failed row:** `background:color-mix(in srgb, var(--amber-soft) 45%, transparent);`

**State badge** (`gl(glyph, fg, bg, border, pulse)`): **20 × 20 px**, radius 6 px,
`700 12px "JetBrains Mono"`, 1 px border; `pulse` adds `animation:cblink 1.05s steps(1,end)
infinite` (`@keyframes cblink{0%,48%{opacity:1}52%,100%{opacity:0}}`).

| # | State | Glyph | Fg / Bg / Border | Example | Carries |
|---|---|---|---|---|---|
| 1 | **Computed** | `✓` | `--ink-3` / transparent / `--sheet-edge` | `5!` → 120 | the value |
| 2 | **Failed** | `✗` | `#fff` / `--amber` / `--amber` | `1 ÷ 0` | `reason:'can't divide by zero'` |
| 3 | **Blocked** | `↑` | `--ink-3` / `--sheet-2` / `--sheet-edge` | `#2 × 2` | **`by:'#2'`, `why:'it failed'`** |
| 4 | **Cycle** | `↻` | `--amber-2` / `--amber-soft` / `--amber` | `#4 = #5 + 1` | **`names:'#4 ↔ #5'`** |
| 5 | **Stale** | `◴` | `--blue-2` / `--blue-soft` / `--blue` · **pulse** | `sin(a) · a changed` | **`old:0.5`** (previous value) |
| 6 | **Ambiguous** | `?` | `--blue-2` / `--blue-soft` / `--blue` | `x = 5` | two resolve **chips** |
| 7 | **Unverified** | `◑` | `--blue-2` / `--blue-soft` / `--blue` | `solve x³−4x²+x+6` | `proven:'x = −1, x = 2'` + `unproven:'1 more root found — could not confirm by substitution'` |
| 8 | **Empty** | *(none)* | `--ink-3` / transparent / `--grid-2` | `` | — |

**Design intent, quoted per state** — these are the UX contracts M7-2 must honour:

- **Blocked** — *"Never ran. Its dependency broke — grey, and it points at the culprit
  instead of reddening itself."* Grey, **not** red: only the true culprit reddens.
- **Cycle** — *"An impossibility, not a failure. It can name every entry in the loop."*
- **Stale** — *"It keeps the previous number so you read 'was, now updating' — not a blank."*
- **Ambiguous** — *"A question, not an error. The engine understood — and refuses to guess
  between valid readings. You choose, in place."* Chips: **`Solve as equation`** /
  **`Assign x ← 5`**; picking one sets `resolvedValue` (`x = 5` or `x ← 5`) and
  `resolvedNote` (`read as equation` / `stored as assignment`), with a `reset`.
- **Unverified** — *"Honest partial knowledge. It reports only what it [could prove]."*

**Transitions the bundle states:** `Failed → Blocked` propagates to dependents (`#3` points at
`#2`); fixing the blocker recovers the dependent; `Ambiguous → resolved` happens **in place**
via a chip; editing an entry raises the undo snackbar with *"1 result reused · dependents
recomputed"*. The bundle presents the eight states as a **gallery with per-state blurbs**, not
as an explicit transition graph — see **O-4** and the note in §4.2.

**Tap-to-reuse:** copies the entry's **nodes** into the current expression
(`this.expr = JSON.parse(JSON.stringify(h.nodes))`), snapshots `{expr, history}` for undo, and
raises the snackbar. **It does not insert a `#n` reference** — see §4.3.

### 2.4 Landscape — fully specified, and it contradicts the portrait thesis

Deferred by **D-031.6**, recorded here so the deferral is informed rather than blind.

| Element | Value |
|---|---|
| Frame | `width:640px; max-width:100%` |
| Inner shell | `height:290px`, `border-radius:18px`, `overflow:hidden`, `display:flex`, bg `--sheet`, `border:1px solid rgba(0,0,0,.4)` |
| Left pane | `flex:1.15`, `border-right:1px solid --sheet-edge`, bg `--sheet-2` **+ paper grid at `background-size:16px 16px`** |
| "HISTORY" label | JetBrains Mono **9 px**, `--ink-3`, ls `.1em`, padding `10px 16px 4px` |
| Tape rows | `flex:1; padding:2px 16px; gap:7px; overflow:hidden`; each row space-between, baseline, `gap:10px`, **`opacity:.7`**; expr 11 px `--ink-2`, value 11 px/600 `--ink` |
| Display | `padding:6px 16px 16px`, right-aligned, `border-top:1px solid --grid`, entry 14 px `--ink-2` |
| Keypad | **6 columns × 4 rows = 24 keys**, `border-radius:8px`, `border:1px solid --key-edge` |
| Key type | num `500 15px` JBM on `--key` · op `600 16px` JBM · **sci `500 11px` IBM Plex Sans, `--ink-2`** · fn amber · eq `700 17px` JBM on `--blue` |

Key order: `sin cos 7 8 9 ÷ / tan √ 4 5 6 × / ln xʸ 1 2 3 − / fn ⌫ 0 . + =`

> ⚠️ **The contradiction.** Six of those 24 keys are **`sin cos tan √ ln xʸ`** — scientific
> keys the portrait surface deliberately hides (*"flick has NO visible label, so nothing
> scientific shows"*, and an always-on scientific pad is the direction the design's own
> redline **DISQUALIFIED**). Landscape reinstates them with **no stated rationale**, and also
> shrinks key type from 22 px to 15 px and radius from 13 to 8. So "the extra width becomes
> history" is only half true — it becomes history **and** a scientific row. This is the
> evidence behind D-031.6's deferral.

### 2.5 Graph view + the grid→axes reveal

| Item | Value |
|---|---|
| Default viewport | `xmin:-6.6, xmax:6.6, ymin:-4.2, ymax:4.2` — **identical to the engine's `Viewport.DEFAULT` (D-027)** ✔ |
| Tick step | `niceStep`: `n<1.5?1 : n<3?2 : n<7?5 : 10`, × decade — the **1/2/5/10 ladder**, matching `graph/Axes.kt` |
| Target divisions | x ÷ 7, y ÷ 5 → step **2** on both axes at the default view |
| Tick label decimals | `step<1 ? (step<0.1 ? 2 : 1) : 0`, trailing zeros stripped |
| Gridlines | one per tick step, from `ceil(min/step)*step`, **line nearest zero skipped**; colour `--grid-2`, `lineWidth 1` |
| Axes | colour `--ink`, `lineWidth 1.6`, drawn **only when zero is inside range** |
| Tick labels | `--ink-3`, `10px JetBrains Mono` |
| Canvas background | `--sheet-2` |
| Curve chips | height 32 px, padding 0 11 px, radius 9 px, gap 9 px; active = `--amber` border + `--amber-soft` bg + `0 0 0 2px` ring; disabled = `opacity .42` |

**The reveal** — `dur = 1800 ms`, driven by `requestAnimationFrame` **or** scrubbed
(`onScrub` sets `t` directly, so every frame is seekable). Easing is smoothstep `ss(a,b,t)`
over staggered sub-intervals:

| Element | Envelope | Wall-clock | Detail |
|---|---|---|---|
| Axes fade in | `ss(0.12, 0.72, t)` | 216 – 1296 ms | `lineWidth = 1 + 1.4·α` |
| Axis numerals | `ss(0.5, 1, t)` | 900 – 1800 ms | 11 px mono, `--ink-3`, integers only, zero skipped |
| Equation label | same `tA` envelope | 900 – 1800 ms | `f(x) = 0.4x² − 2` at (12,12), `--blue`, 600 13 px mono |
| Keypad falls away | `1 − ss(0.05, 0.62, t)` | 90 – 1116 ms | drops `ss(0,1,t)·60` px while fading |
| Curve draws | `(t − 0.24) / 0.7` | 432 – 1692 ms | left→right |

**The grid never moves.** Verbatim from the extraction: all four grid loops use literal
constants; the animation variable `t` appears nowhere in the grid block and `globalAlpha` is 1
there — the grid is drawn **identically at t=0 and t=1**. Prose agrees: *"the grid resolves
into the coordinate plane rather than being replaced"* and *"The paper stays put; the axes
arrive on top of it."* Reveal grid: minor **0.5 units** `--grid`, major **1.0 units**
`--grid-2`, both `lineWidth 1`.

**This independently confirms D-027** — the engine's comment correction ("the reveal's grid
never moves") was right, and `Viewport.lerp` is **not** what powers the reveal.

**Reduce motion:** a settings toggle, **default OFF**, described as
*"Grid→axes cross-dissolves instead of animating."*

---

## 3 · VARIANT MATRIX

### 3.1 Themes — Light / Dark / AMOLED

Driven by `data-theme` on the root. Every one of the 20 tokens is redefined (§1.1). Beyond
raw colour:

| Element | What shifts |
|---|---|
| **Shadows** | `--shadow` `30 45 40` → `0 0 0`. Cards use `box-shadow:0 1px 3px rgb(var(--shadow)/.05), 0 8px 24px rgb(var(--shadow)/.05)` — so in Dark/AMOLED **shadows resolve to fully transparent black and disappear**. Replacement elevation is **OPEN (O-6)**; borders (`--sheet-edge`) carry separation. |
| **Paper grid** | The root background gradients use `--grid`/`--grid-2`, so the paper substrate darkens with the theme and never disappears. |
| **Key surface** | Light: `--key` `#FFFFFF` sits *above* `--paper`. Dark/AMOLED: `--key` equals `--sheet-2`, so keys read by border rather than by lift. |
| **AMOLED vs Dark** | `--paper` becomes true `#000000` (pixel-off). `--amber` brightens `#F0A63E`→`#F2A93F`; `--blue`/`--blue-2` are **identical** to Dark; the soft tints darken (`--blue-soft` `#16273A`→`#0c1a29`). |
| **Selection** | `::selection{background:var(--amber);color:#fff;}` — amber in all themes. |

### 3.2 Digit grouping — Indian vs Western

Setting: `{label:'Digit grouping', seg(['12,34,567','1,234,567'], …)}` → `locale` = `'in'` | `'intl'`.

```js
groupInt(s){ if(this.state.locale==='intl') return s.replace(/\B(?=(\d{3})+(?!\d))/g,',');
             if(s.length<=3) return s; const l=s.slice(-3); const r=s.slice(0,-3).replace(…) }
```

- **Western (`intl`)** — groups of three throughout.
- **Indian (`in`)** — last three, then pairs.
- Applied to the **integer part only**: `groupInt(ip) + (fp ? '.'+fp : '')`.

**Elements that re-render:** the result line, the ≈ decimal line, tape entry values, matrix
determinant readouts (`_fmtI`), stats summaries, and the unit-browser examples
(`10⁵ = 1,00,000`). Grouping is applied at *every* numeric render site through one helper —
matching the engine's single-`Formatter` decision (G-5).

**Engine parity: SUPPORTED.** `GroupingMode { NONE, INDIAN, WESTERN }` shipped in `3842290`,
with the same integer-part-only rule.

### 3.3 EN vs हिन्दी

Driven by `data-lang` / `state.lang === 'hi'`.

| Element | What shifts |
|---|---|
| **Every rendered digit** | `dig(s)` maps `0-9 → ०१२३४५६७८९`. Applied to results, tape values, matrix cells, stats, tick labels — every numeric surface. |
| **Operator glyphs** | `dchar(v)` maps `*→×`, `/→÷`, `-→−` (language-independent), **and** digits→Devanagari when `hi`. |
| **Indian unit names** | Unit browser carries Devanagari: `lakh/लाख`, `crore/करोड़`, `tola/तोला`, `bigha/बीघा`, `guntha/गुंठा`. Design prose: *"with Devanagari names and honest 'varies by state' notes where the truth is regional."* |
| **UI strings** | **OPEN (O-8)** — no translation table for keys, chips, settings or state labels. |
| **Font** | **`IBM Plex Sans Devanagari`**, falling back to `IBM Plex Sans` — the same superfamily, *"without a second type voice."* Must be bundled with the app. |

> **Grouping and language are INDEPENDENT axes.** `locale` ('in'/'intl') drives separators;
> `lang` ('en'/'hi') drives numerals. Indian grouping with ASCII digits is a valid combination
> — the engine's `GroupingMode` matches this separation exactly.

---

## 4 · ENGINE REQUIREMENTS — checked against `core/`

Checked against the engine at `81783bc`. **SUPPORTED** = found in code, file named.

### 4.1 The main table

| # | Design assumes | Status | Engine evidence |
|---|---|---|---|
| 1 | Exact-first arithmetic; `0.1+0.2` is exactly `0.3` | **SUPPORTED** | `num/Num.kt` tower; `AcceptanceSpec` |
| 2 | Exact surds kept symbolic (`√8 → 2√2`) | **SUPPORTED** | `num/Num.kt` `Surd`; `Formatter.renderSurd` |
| 3 | Terminating rational → decimal; non-terminating → fraction | **SUPPORTED** | `Formatter.rational` (D-025) |
| 4 | `S⇔D` toggle, shown only when the forms differ | **SUPPORTED** | `Settings.exactForm`, `Formatter.hasDistinctForms` |
| 5 | Angle modes DEG/RAD/GRAD, DEG default | **SUPPORTED** | `Settings.angle`, `AngleMode` (D-005) |
| 6 | Exact trig at special angles | **SUPPORTED** | `num/ExactTrig.kt` |
| 7 | Percent (`5%` → 0.05) and factorial (`3!` → 6) | **SUPPORTED** | `Evaluator.evalPostfix`; D-007 |
| 8 | Implicit multiplication (`2(3)`) | **SUPPORTED** | `parse/Parser.kt`; `ParserSpec` |
| 9 | Inline units, left operand's unit kept | **SUPPORTED** | `units/Units.kt`, `Quantity.plus` |
| 10 | Indian units incl. **guntha, maund, seer** + lakh/crore/tola/bigha | **SUPPORTED** | `units/Units.kt` (G-4, `dd142de`; D-029/D-030) |
| 11 | Constants browser vocabulary (`c`, `g`, `h`, `Nₐ`, `φ`) | **SUPPORTED** | `constants/Constants.kt` (D-026) |
| 12 | Equation solving from typed input, roots **ascending** | **SUPPORTED** | `solve/Solvers.kt` ascending (C-6, `a8ffb00`) |
| 13 | Graph sampling, viewport `±6.6 × ±4.2`, 1/2/5 ticks | **SUPPORTED** | `graph/Viewport.kt` `DEFAULT`, `graph/Axes.kt` (D-027) |
| 14 | Roots verified by substitution; unverified never shown as found | **SUPPORTED** | `graph/RootFinder.kt` `RootResult` |
| 15 | Digit grouping, Indian + Western, integer part only | **SUPPORTED** | `GroupingMode`, `Formatter.group` (G-5) |
| 16 | Tape with dependency recompute + topological order | **SUPPORTED** | `tape/Tape.kt` (D-021/D-022) |
| 17 | Transactional undo restoring expression **and** history together | **SUPPORTED** | `tape/Tape.kt` snapshot (D-023) |
| 18 | 2-D entry: fraction / power / root containers | **SUPPORTED** | `entry/Node.kt` `Fraction`/`Power`/`Root` |
| 19 | Four flicks: ÷↑√, ×↑xʸ, −↑a⁄b, .↑π | **SUPPORTED** | `keypad/Keypad.kt` |
| 20 | `fn` palette, searchable, categorised | **SUPPORTED** | `keypad/FnPalette.kt` |
| 21 | Backspace hold-to-repeat | **SUPPORTED** | `KeyAction.BackspaceRepeat` (D-018) |
| 22 | **`Func` entry node with `arg`, display ≠ spelling** | **MISSING (M-1)** | `entry/Node.kt` has no `Func` case |
| 23 | **`Unverified` as a tape state** | **MISSING (M-2)** | `tape/EntryValue.kt` has 7 states, no `Unverified` |
| 24 | **Devanagari numeral output** | **MISSING (M-3)** | `Formatter` emits ASCII only |
| 25 | **Ambiguity resolvable in place (`x = 5`)** | **MISSING (M-5)** | `RouteResult.Ambiguous` has **no producer**; D-016.1 makes `x = 5` an equation by design |

### 4.2 The eight-state tape — design vs engine

The design's eight and the engine's seven line up **except for `Unverified`**:

| Design state | `EntryValue` | Note |
|---|---|---|
| Computed | `Computed(outcome)` | ✔ |
| Failed | `Failed(error, previous)` | ✔ |
| Blocked | `Blocked(by, previous)` | ✔ — engine already carries **which** entry blocked it (D-021), exactly what the design's `by:'#2'` needs |
| Cycle | `Cycle(members)` | ✔ — engine names members (D-022), matching `names:'#4 ↔ #5'` |
| Stale | `Stale(previous)` | ✔ — engine already keeps the previous value, matching *"was, now updating"* |
| Ambiguous | `Ambiguous(candidates)` | ✔ type exists — but see **M-5**: nothing produces it |
| **Unverified** | *(none)* | **MISSING (M-2)** |
| Empty | `Empty` | ✔ |

**Honest caveat on "verified end to end".** The bundle presents the eight states as a
**gallery** — one exemplar row per state with a blurb, plus two interactions (tap `#3`'s
pointer to travel to its blocker; tap a `#6` chip to resolve in place). It does **not**
contain a transition table or a state-machine diagram. The transitions listed in §2.3 are the
ones the bundle actually states; anything beyond them is **OPEN (O-4)**. The engine's own
`M6TapeSpec` is the stronger artifact here — it asserts recovery, transitivity, and cascade
undo, which the design demo does not implement.

### 4.3 G-6 — `#n` references: **the design does NOT settle it**

Answering precisely, as asked.

- **Rendered?** **YES.** `#n` appears in tape expressions: `expr:'#2 × 2'` (Blocked),
  `expr:'#4 = #5 + 1'` (Cycle), and as row tags `tag:'#1'` … `tag:'#8'`.
- **Typable?** **NO — not specified anywhere.** There is **no key, gesture, or code path that
  inserts a `#` character**. Searches for `insChar('#')`, `insRef`, `refKey` return nothing.
- **What "reuse" actually does:** deep-copies the entry's **nodes** into the current
  expression — `this.expr = JSON.parse(JSON.stringify(h.nodes))` — then re-evaluates. It
  produces a *copy of the expression*, not a *reference to the entry*.
- Consequence: the snackbar *"1 result reused · dependents recomputed"* over-promises in the
  demo, since copying creates no dependency edge.

> **Verdict: G-6 remains OPEN (O-1).** The engine can already lex `#3`
> (`lex/Tokenizer.kt::entryReference`) and the tape resolves dependencies — the missing piece
> is a **UI affordance**, and the design does not provide one. M7-2 must either (a) ship
> tap-to-reuse as *copy* per the design and accept that `#n` stays unreachable, or (b) get an
> owner ruling on a `#n` insertion gesture. **This needs a decision, not code.**

### 4.4 G-3 — the `func` node: **the design DOES settle it**

Fully specified, quoted:

```js
insFunc(name){ this.insCont({t:'func',name,arg:[]}); }
isCont(n){ return n && (n.t==='frac'||n.t==='sqrt'||n.t==='pow'||n.t==='func'); }
fields(n){ … n.t==='func'?[n.arg] : [] }
if(n.t==='func'){ const nm={asin:'sin⁻¹',acos:'cos⁻¹',atan:'tan⁻¹',cbrt:'∛',ln:'ln',log:'log'}[n.name]||n.name;
else if(n.t==='func'){ s += n.name+'('+this.flatten(n.arg)+')'; }
```

Four properties M7-2 must implement:

1. `func` is a **container** with a single `arg` field — so **one keystroke creates it and one
   backspace removes it**, satisfying D-018's axiom that every structure is created by exactly
   one keystroke.
2. **Display name ≠ engine spelling**: `asin` renders as `sin⁻¹`, `acos`→`cos⁻¹`,
   `atan`→`tan⁻¹`, `cbrt`→`∛`; `ln`/`log` render as themselves.
3. **Flatten emits the engine's spelling**: `name + '(' + flatten(arg) + ')'` → `asin(…)`.
   So the display layer and the engine text stay separate, and round-trip is preserved.
4. The `fn` drawer inserts by engine name while labelling by display name:
   `mk('sin⁻¹', insF('asin'))`.

> **Verdict: G-3 is SETTLED by the design.** Add `Func(name, arg)` to `entry/Node.kt` with a
> display-name map. **This is buildable now** — it needs no further ruling.

---

## 5 · THE OPEN LIST — 9 of 10 CLOSED by D-031/D-032, 1 remaining

Restated from §0 — these are unanswered by the bundle and need a decision before or during M7-2.

| # | Open question |
|---|---|
| ~~O-1~~ | ~~How a user types a `#n` reference (G-6)~~ — **RULED, D-031.7:** tapping a row's `#n` badge inserts the reference; tapping the row body still copies |
| ~~O-2~~ | ~~Disabled key state~~ — **RULED, D-031.1:** no disabled state; unavailable actions no-op silently |
| ~~O-3~~ | ~~Keypad error state~~ — **RULED, D-031.2:** display keeps the em-dash; the typed error kind surfaces in the tape row |
| ~~O-4~~ | ~~Tape height, cap, transition graph~~ — **ANSWERED** (78 px / 6 entries) and **RULED as D-032** (graph derived from the engine, 35 tests) |
| **O-5** | Portrait app-shell target width (all mockups are a fixed 330 px frame). The key-width consequence is computed in §2.1; only the *target* width is unstated |
| ~~O-6~~ | ~~Dark/AMOLED elevation~~ — **RULED, D-031.3:** no substitute; the surface ladder + 1 px edge carries depth, revisit on device |
| ~~O-7~~ | ~~Devanagari font family~~ — **CLOSED during extraction: `IBM Plex Sans Devanagari`** (§1.3). Kept numbered so the ids stay stable. |
| ~~O-8~~ | ~~Hindi UI string table~~ — **RULED, D-031.5:** numerals only; labels/menus/errors stay English; RTL deferred |
| ~~O-9~~ | ~~Plot-curve point markers~~ — **CORRECTED: markers do not exist.** The third channel is the curve **exit tag** |
| ~~O-10~~ | ~~Touch targets under 48 dp~~ — **RULED, D-031.4:** visuals stay; 48 dp minimum hit areas via delegate/invisible padding |
| ~~O-11~~ | ~~Landscape dimensions~~ — **ANSWERED** (§2.4) and **RULED, D-031.6:** deferred beyond M7-2, portrait only |

**Only O-5 remains genuinely open**, and it is narrow: the mockups' 330 px frame is a drawing
convention, not a target width. Everything that *depends* on the target width is already
computed (§2.1), so this blocks nothing — it just needs a device decision.

## 6 · THE MISSING LIST (6)

| # | Missing capability | Where it lands |
|---|---|---|
| **M-1** | `Func` node in the entry model (**G-3 — specified, buildable now**) | `core/entry/Node.kt` + `Editor` + `Formatter` display map |
| **M-2** | `Unverified` tape state (8th state) | `core/tape/EntryValue.kt` + the M7-2 tape row |
| **M-3** | Devanagari numeral output | `core/format/Formatter.kt` (a numeral-set setting beside `GroupingMode`) |
| **M-4** | Stale pending-recompute value | mostly present — `Stale(previous)` exists |
| ~~M-5~~ | ~~In-place ambiguity resolution~~ | **RULED OUT, D-031.8:** D-016.1 stands, the chips are not built, and `RouteResult.Ambiguous` gets no producer in v1. `EntryValue.Ambiguous` is proven unreachable by `TapeTransitionSpec` |
| **M-6** | Angle chip cycling DEG/RAD/GRAD | UI only — `Settings.angle` already exists |

### One conflict to resolve before building

**M7-1 renders the flick label; the design forbids it.** `CalcApp.kt`'s `KeyButton` draws
`key.flickUpLabel` above the key label. The design states the flick has **no visible label**
and renders `alt:''` with `ghostStyle:'display:none;'`. Remove the visible label in M7-2.

### One place the design contradicts a receipted engine decision

**M-5 / tape state 6.** The design's `Ambiguous` exemplar is `x = 5` with chips
*"Solve as equation"* / *"Assign x ← 5"*. **D-016.1 removed exactly this ambiguity**: `=` is
equation-only and storing is `5 → x`, on the stated principle that *"the same keystrokes
always mean the same thing."* The engine therefore cannot produce `Ambiguous` for `x = 5`, and
`RouteResult.Ambiguous` has no producer at all. Either the design's example is illustrative
(and `Ambiguous` needs a different trigger), or D-016.1 is reopened. **Owner ruling required.**
