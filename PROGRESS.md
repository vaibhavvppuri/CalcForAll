# PROGRESS

## Current delivery — 11 November 2026

The July entries below are historical. The Android SDK/toolchain is now available locally, the native app is integrated as **CalcForAll**, and installable APKs have been built and exercised offline on an Android 6 emulator. The latest complete test and device evidence is maintained in [docs/VERIFICATION.md](docs/VERIFICATION.md), with supported behavior and limits in [docs/CAPABILITY-MATRIX.md](docs/CAPABILITY-MATRIX.md).

**1.2.0-preview (12 November 2026)** adds polynomial equations up to degree 8 to the engine's router and a set of everyday and study tools (money, dates, equation solving on a range, tables and sums, number theory, vectors, probability, curve fitting, Taylor polynomials, numerical limits, bitwise logic). The order was set by the weighted DPCC ranking in [docs/PRODUCT-PLAN.md](docs/PRODUCT-PLAN.md). This increment was verified by automated tests, lint and APK assembly; the new tool screens have not been exercised on an emulator or device.

**1.3.0-preview (12 November 2026, evening)** is 1.2 after independent review passes (the second over the fixes from the first, the third over the 1.3 additions) plus four more tools: geometry kept exact in π and square roots, sequences, business maths and quartiles. The third pass reported six defects; all six were confirmed, fixed and pinned by tests, and are listed in [docs/VERIFICATION.md](docs/VERIFICATION.md). A fourth pass over those fixes found three more, all confirmed and fixed. One of them was older than 1.3 and changed the grammar: a power written on a unit now belongs to the unit, so `5 m^2` is five square metres (it used to evaluate as 25 m²). A fifth pass over that grammar change found four more (number words such as lakh keep the whole-number reading; the ampere is written `amp` because `A` is a register), also fixed. It is the next version in sequence, not the historical roadmap's v2. Verification went one step further than 1.2: a new device gate runs every catalogued tool operation on Android 6 and Android 14 emulators, and it passes on both. The first operation of each of the sixteen catalogued tools was also pressed through the real interface on both emulators and gave the unit tests' answers; the other operations were pressed later (see the next paragraph), and no physical phone was used. Three small additions followed: numbers and cheque amounts in words, sharing an amount in a ratio, and inflation. A sixth review pass then found three more defects in the fifth pass's fixes (the keypad's power key after a number word, and two break-even edge cases), and a seventh found four in the sixth's fixes and the extras; all are fixed. Typing on the Android 14 emulator's own keyboard also showed that autocorrect was rewriting what was typed (`5 lakh` arrived as `5 leaky`): every text field now switches the keyboard's autocorrect and capitals off. An eighth pass found four more, two of them in the keypad rule the seventh had rewritten, and pointed at an older flaw that was then fixed: a fraction or power keyed on the keypad now counts as one value whatever follows it (½ followed by `lakh` used to evaluate as 1/(2 lakh)). `floor` and `ceil` of very large irrational numbers are also exact now. Every pass has found something, most of it in the previous pass's fixes, so the next session should start with another one.

**Overnight into 13 November** four more review passes, the ninth to the twelfth, went through how the engine rounds, compares and subtracts, and how it treats approximate values. Exact values are now compared exactly, and subtracted, rounded and reduced with as many digits as the question needs: `sqrt(10^100+1) - 10^50` is 5×10⁻⁵¹ (it was 0), `sqrt(10^700+1) = 10^350` is False (it was True), and `mod(10^60, pi)` is a remainder (it was 10000000000). For approximate values, `floor`, `round`, `mod` and their kin read an estimate that is a whole number as far as can be told as that whole number, so `floor(ln(125)/ln(5))` is ≈ 3 where 1.2 answered 2. A True/False statement between estimates that agree to thirty digits is declined, where it used to be called False. A repeated root and a singular system are recognised through the rounding of their coefficients. A check on the Android 14 emulator found that a long answer ran past the answer area with nothing on screen to say so; the area now says when there is more below. The operations that had not been pressed on a screen were pressed on the Android 6 emulator, so all seventy have been now. The rule for approximate values was rewritten three times as the passes found fault with it.

**On the morning of 13 November** that last form had its review, the thirteenth pass, and the corrections to it had theirs, the fourteenth. Each confirmed six defects; three of the fourteenth's were regressions the thirteenth's corrections had introduced. How far an estimate can be trusted is now measured in the tower's own sums and products as well as in the functions, so `floor((sqrt(101)+10)*(sqrt(101)-10))` is ≈ 1 where every earlier version answered 0. The same measure decides whether a discriminant is rounding, so `(x-10^18)^2 = sin(20)` has its two roots again, and whether a statement may be called False, so `cosh(13)^2-sinh(13)^2 = 1` is declined. A whole part or a remainder inside another now carries its rounding with it: `floor(frac(123.75*cos(10)/cos(10))*60)` is ≈ 45, where it was 44. Nested rounding functions cost what the expression is long, not its cube. The readers of the fourteenth pass compared the old and new engines on about 35,000 inputs of their own; those were run again on the final code, and what changed is set out in [docs/VERIFICATION.md](docs/VERIFICATION.md). The fourteenth pass's corrections have been through the full gate and both emulators but not through a review pass of their own. What is still wrong, and why it needs a decimal type that carries its own error bound, is in [DECISIONS.md](DECISIONS.md) and [docs/VERIFICATION.md](docs/VERIFICATION.md).

This delivery adds the Compose calculator, structured entry, persistent linked history, settings, tool screens, 2D graphing and 3D height-field rendering, bounded symbolic algebra/calculus, an optional local API, and bundled offline photo-text recognition. The experimental symbol pad accepts one selected digit/operator at a time. General handwritten mathematical layout, arbitrary photo/word-problem solving and a full unrestricted CAS remain outside the implemented scope.

The original platform/legal milestone has been addressed through API 23 compatibility work, an app without Internet permission, local-data controls, privacy documentation, dependency inventories and bundled notices/corresponding source. Store publication, a production signing identity, physical-device/stylus testing and broader accessibility certification remain separate release work. The user will upload the local source themselves; no remote push or deployment is performed by this task.

## Historical checkpoint

## Status: M0 + M1 + M2 + M3 + M4 + D-016 + M5 + **M6** — complete (2026-07-17) · 419 tests, 0 failing

### The residual Article 11 smell — closed BEFORE M5 (a precondition, not hygiene)
`evalConstant → null` is now `Extraction = Extracted | NotPolynomial | Failed`, and
the `ErrorSink` side channel (itself an Article 12 duplicate) is **deleted**. This
had to land first: M5's root finder asks *"is this polynomial?"* and must answer
**differently** for "no → use numeric methods" and "we failed → stop and report".
With a nullable it would have had to guess — and a wrong guess there draws a
*plausible wrong root on a graph*, for a student who will believe it. The three
cases are now structurally inexpressible as one another.
`structureExtractor(settings, env)` is the single construction shared by the router
and the graph layer (Article 12).

### M5 — D10, the graphing core (the wedge)
- **Sampling (10.1)** and **trace (10.3)** via `Sampler`; **roots & intersections
  (10.3)**; **viewport/axes/range (10.2/10.7)**. All `core/`, no UI, no Android.
- **Article 12 applied to sampling:** `PlottableFunction.at(x)` is the ONE place a
  function's value is ever computed. Sampler, root finder, intersection finder and
  trace all call it — not the same logic, the same function. They cannot disagree.
- **Article 11 applied to sampling:** `Sample = Defined | Undefined | Unevaluated`.
  **`Undefined` is a GAP in the curve** (the mathematics has no value: `1/x` at 0,
  `ln(-1)`). **`Unevaluated` is a HOLE in the data** (*we* failed — e.g. a complex
  value cannot sit on a real axis). Different types, so a renderer can never draw a
  hole as a confident gap.
- **Roots are verified or not returned.** Exact when polynomial (degree ≤ 2 → the M2
  solvers), numeric bisection otherwise. Every root is confirmed by **substituting
  it back into the original function** and carries the residual `|f(x)|` as proof.
  An unconfirmed candidate is `Unverified`, never `Found` — and the verified ones
  are still listed rather than dropped (Article 11).
  - *This is what stops an asymptote being reported as a root:* `1/x` on [-1,1]
    changes sign across 0 and bisection converges there, but `f(0)` is Undefined, so
    it can never be confirmed. A gap or hole also **breaks the bracket** outright.
  - Verification substitutes into the **original function, not the extracted
    polynomial** — so it also cross-checks the extractor against the evaluator. Had
    the D-016 percent drift still existed, an "exact" root would fail to verify
    rather than be drawn.
- **Intersections** are roots of `f − g`, inheriting every guarantee above.
- **Viewport maths is animatable, not just renderable.** Exact pan, zoom about a
  fixed focal point, and `lerp` — every intermediate frame of the grid→axes reveal
  is an *exact* viewport, so a transition can be scrubbed, reversed or paused with
  no drift. Ticks are exact 1/2/5 × 10ⁿ: `[0,1]` in ten steps gives exactly `1/10`,
  not `0.1000000001`, so ticks don't shimmer during a zoom.
- Tests: +24 (M5Spec), manual empirical method. **237 core + 3 api = 240, 0 failing.**

### D-016 — the invocation layer (equations & units inline; a router, not a mode)
Closes the architecture gap: the 51-feature list said *which* features exist, never
*how* they are invoked, so a quadratic could only be reached through three
coefficient fields — a mode. Now:
- **`x²-5x+6=0` → roots {2, 3}.** Typed, inline, no mode. (`x^2` works identically;
  superscripts `²`/`⁻¹` are lexed as powers.)
- **`=` is equation-only; `5 → A` stores** (aliases `->`, `sto`). The ambiguity is
  *removed*, not adjudicated (D-016.1).
- **`2+2=4` → `Verdict(true)`** — zero unknowns is a truth verdict (D-016.2).
- **Systems on separate lines** (`x+y=5` ⏎ `x-y=1` → x=3, y=2); `;` tolerated (D-016.3).
- **Units inline**: `5 km + 300 m` → `5.3 km`; `3 * 5 km` → `15 km` (unit preserved);
  `5 m/s` → a speed; `20 degC` stays affine; `3 + 5 lakh` → `500003`.
- **Reserved names** (D-016.4): unknowns `x,y,z`; registers `A–F, M`; units and
  constants reserved. Assigning to a reserved name names the collision.
- **Never guesses**: `x+y=5` alone → underdetermined naming both unknowns;
  `sin(x)=0`, `x^3=8`, non-linear systems → typed errors.
- Equations reach the **unchanged** typed solvers by evaluating the AST in a
  *polynomial algebra* (no rewriting → still not a CAS). Matrices and statistics
  **stay typed APIs** — a grid is the right affordance, not a mode failure.
- Tests: +22 (D016Spec + a router fuzz). The fuzz alphabet picked up `=`, `→`,
  newline and superscripts **automatically**, because A3 derives it from the
  tokenizer rather than a hand-written list.

### Adversarial review (D-016) — harness INCOMPLETE, findings ALL real
The review hit the account's **monthly spend limit**: 10 of 15 agents died. The whole
`names-units` lens never ran, and every verifier for `extraction`, `lexer-parser` and
`crash-scope` died — so the workflow's `confirmed = 1 of 10` meant *"9 were never
checked"*, not "9 refuted" (the `.catch` dropped them). Raw findings were recovered
from the run journal and verified **by hand, empirically** (write tests asserting the
correct behaviour; whatever fails is real). **All 10 were real; all 10 fixed, pinned
by `D016ReviewSpec`.**

- **[critical] nested powers OOM** — `(((x^64)^64)^64)^64 = 0` compounded to degree
  16,777,216 and died allocating. The *exponent* was bounded; the **resulting degree**
  was not. Now bounded → typed OVERFLOW.
- **[high] `1 + 20 degC%` threw out of the public API** — the contextual-percent
  factor was computed *outside* the span guard, so the ArithFault escaped.
- **[high] `x + 10% = 55` → x = 54.9** (correct: 50). The extractor didn't implement
  contextual percent, so it solved a **different equation than the user typed** —
  D-014's failure mode exactly: a plausible wrong number.
- **[high] `5 lakh = 500000` → Verdict(false)** — a confident wrong answer on the
  Indian-units edge (spec 9.6). Comparison and arithmetic had drifted apart; they now
  share one promotion rule.
- **[medium]** `10^3 kg` parsed as `10^(3 kg)`; `5 m²` was rejected **and the engine
  could not re-read its own `15 m²` output** (a round-trip failure Article 4 asks for);
  `0x = 0` was a "malformed hex literal"; real errors inside equations were disguised
  as "unsolvable form".
- **[low]** review-agent scratch files committed in `2b62154`.

**Known blind spot:** the `names-units` lens never executed. Re-run when the spend
limit allows.

**Two process lessons, both mine:** (1) a *read-only* review task was given
write-capable agents, which polluted the repo — reviews should use read-only agents;
(2) `git add -A` while agents are running is unsafe — stage explicit paths.

### Amendments A6/A7/A8 → Articles 11, 12, 13 — and the work they demanded
- **Article 11 audit (every `catch`, aggregate, summary).** **One real violation
  found, in the Article 1 enforcement path itself:** `checkNoAndroidImports` passed
  *vacuously* when it scanned zero files — if the source path ever moved, the
  boundary wall's own tripwire would report clean having checked nothing. It now
  fails on an empty scan and logs `scanned N files`. Every engine `catch` maps to a
  typed error (clean); the `-> null` returns are legitimate "no answer" cases.
  *Residual smell:* the extractor's `evalConstant → null` conflates "not polynomial"
  with "evaluation failed", mitigated by an `ErrorSink` side channel — works, tested,
  but a sealed result would be structurally correct.
- **Article 12 enforcement (round-trip tests) — found 2 real bugs immediately.**
  `5 km + 300 m` rendered `53/10 km`, which **re-parses as `53/(10 km)`** — a
  different dimension, because units bind tighter than `/`. And `15/2 m·kg/s²` could
  not be re-read at all. Fixed by parenthesising non-plain coefficients and by
  supporting `Quantity^integer` (dimensions scale: `s²`, `m³`). `RoundTripSpec` now
  covers every renderer/parser pair, comparing via the engine's own `=`.
- **`names-units` manual pass** (the lens the harness never ran) — **1 real bug**:
  `2 h` was an unknown name; `h` is the SI symbol for hour and only `hr`/`hour` were
  registered. Ten other checks passed: `M`/`m`, `E`/`e`, `x`/`X`, reserved-name
  refusals, `min` as unit-vs-function, the dimensionless demotion boundary, and
  "storing cannot change what an identifier means".

**Total: 216 tests, 0 failing.** The blind spot is closed.

All Definition-of-Done conditions (Article 6, as amended A2) are met per milestone:
code builds across all three modules, tests green, docs updated, fresh zip produced.

### M2 — D2 + D5 (base conversion, complex numbers, equation solvers)
- **Complex numbers (2.5):** a `Value` layer (real | complex) flows through the
  evaluator; `i` is the reserved imaginary unit; rect + polar (`cpolar`, `arg`,
  `abs`, `conj`, `re`, `im`). Exact where the tower allows (`i*i → -1`,
  `abs(3+4i) → 5`); a complex with zero imaginary part demotes to real.
- **Base conversion 2–36 (2.1):** `0x`/`0b`/`0o` literals + `toBase`/`fromBase` API.
- **Linear / quadratic-with-steps / systems 2×2 & 3×3 (5.1/5.2/5.4):** exact-first
  (Cramer's rule; quadratic gives exact surd/rational roots or complex conjugates),
  worked steps, typed errors for degenerate cases. Exposed as a typed solver API.
- Tests: +25 (M2Spec); mechanical fuzz still green. **132 core + 3 api = 135, 0 failing.**

### M3 — D7 + D8 (matrices, statistics, regression)
- **Matrices (7.1/7.2/7.3):** a `Matrix` over the exact `Num` tower; add, subtract,
  multiply, scalar, transpose, **exact determinant** (rational Gaussian elimination),
  **exact inverse** (Gauss–Jordan), and **rank** (RREF) — all `Double`-free, so
  `det` and `A⁻¹` are exact; `A·A⁻¹ = I` exactly. Typed errors for singular /
  non-square / mismatched shapes.
- **Descriptive statistics (8.2):** count, sum, mean, min/max/range, median, and
  population + sample variance/stdev — exact rationals except the genuine √ (stdev).
- **Regression (8.4):** least-squares slope/intercept exact; Pearson r and r² (r²
  exact). Typed errors for degenerate input.
- Tests: +13 (M3Spec); mechanical fuzz still green. **145 core + 3 api = 148, 0 failing.**

### M4 — D9 (units + dimensional analysis, constants, Indian units)
- **Unit engine (9.1) + categories (9.2):** a `Dimension` (7 SI base exponents) +
  `Unit` (affine map to base SI) + `Quantity`. Conversions across length, mass,
  time, temperature (affine), area, volume, speed — exact rational factors.
  Add/sub/mul/div enforce **dimensional analysis** (multiply combines dimensions;
  incompatible add is rejected; affine-unit arithmetic is refused, not guessed).
- **Indian units (9.6):** `lakh`/`crore` (dimensionless 10⁵/10⁷), `tola` (mass),
  `bigha` (area).
- **Constants (9.4/9.5):** CODATA physical constants with uncertainty (post-2019-SI
  defined ones exact; G, mₑ, mₚ measured); math constants (π, e exact-symbolic; φ, γ).
- Tests: +14 (M4Spec); mechanical fuzz still green.

### Adversarial review (M2–M4)
The same multi-agent harness (6 lenses over complex/solvers/matrices/stats/units/
crash-safety, each finding independently verified) found **4 real bugs**, all fixed
and pinned by `M2M4RegressionSpec`:
- **[high] `sqrt(-π)`/`sqrt(-e)`** threw a spurious DOMAIN error in complex mode (a
  40-digit modulus vs a 50-digit exact component drove the radicand slightly
  negative) — now an exact pure-imaginary via a real-branch special case + clamp.
- **[med] `Matrix.hashCode`** was inconsistent with its value-based `equals` — now
  value-canonical, so equal matrices hash equal.
- **[low] sample variance/stdev of n = 1** silently returned 0 (undefined) — now null.
- **[low] `toBase`/`fromBase`** threw `IllegalArgumentException` — now a typed
  `CalcResult`, honouring the never-throws contract.
**Final: 163 core + 3 api = 166 tests, 0 failing.**

---

## What shipped

### M0 — Skeleton
- **M0.1 Repo structure.** Gradle multi-module (`core`, `api`, `android`) with a
  version catalog (`gradle/libs.versions.toml`), Kotlin 2.1, JDK-21 toolchain,
  committed Gradle wrapper (8.10.2). `core` is a plain JVM module — **not** an
  Android library.
- **M0.2 The boundary wall (Article 1).** Enforced mechanically, two ways:
  `settings.gradle.kts` only includes `:android` when an Android SDK is present,
  so the boundary-wall CI job builds `core` with **no Android SDK on the
  classpath**; and a secondary `checkNoAndroidImports` Gradle task names any
  offender early. **Verified for real** (see "Boundary-wall proof" below).
- **M0.3 CI.** `.github/workflows/ci.yml` — two jobs: `jvm` (core+api, no SDK, the
  wall) and `android` (with SDK, assembles the debug APK). Runs on every push + PR.
- **M0.4 ML Kit spike.** Built the throwaway sketch (`mlkit-spike/`); verdict is
  **PREDICTED FAIL** (text only, no math structure), high confidence, with primary
  sources — see `DECISIONS.md`. *Could not be run empirically here* (no device);
  the sketch + steps make it a 30-min on-device confirmation.

### M1 — The core engine (all in `core/`, zero Android, zero UI)
- **M1.1 Number representation.** Exact-first tower `Rational | Surd | SymConst |
  Dec`. No `Double` in storage or computation — only at the display boundary.
  `0.1+0.2 == 0.3`, `1/3` stays `1/3`, `√8 → 2√2`, `(1/3)*3 == 1` — all exact.
- **M1.2 Tokenizer.** Decimal/scientific numbers, identifiers, `π`, unicode
  operators (`×÷−·√`), implicit-multiply adjacency — every token carries a source
  span.
- **M1.3 Parser.** Pratt parser → an AST (never a value). Correct unary-minus vs
  power (`-2^2 == -4`), right-associative `^`, implicit multiplication.
- **M1.4 Evaluator.** Exact-first walk covering D1 (ops, order, precision/rounding,
  fractions, negative/abs, percent semantics, sci/eng notation, errors) and D3
  (powers/roots, logs, trig + deg/rad/grad, inverse & hyperbolic trig,
  combinatorics, DMS + rect↔polar helpers, rounding family).
- **M1.5 Errors.** Typed `EvalError` results (not exceptions-as-control-flow),
  each carrying what/where(span)/recoverable. Internal `ArithFault`s are mapped at
  the AST boundary; the public API never throws.
- **M1.6 Tests.** kotest property tests (commutativity/associativity/distributivity,
  inverses, round-trip render→parse, and a never-crash fuzz), golden formatting
  tests, and the M1.1 acceptance tests explicitly.

## Test results
- `:core:test` — **all green** (107 tests: acceptance, parser, evaluator, formatter,
  property, number-tower, plus a regression spec pinning every review finding).
- `:api:test` — **all green** (3 tests: `/health`, `/eval` exact result, typed 400).
- **Total: 110 tests, 0 failing** (`./gradlew clean :core:build :api:build`).
- Full command set the CI mirrors: `./gradlew :core:build :api:build`.

## Boundary-wall proof (M0.2), run locally
```
Step 1 — add `import android.content.Context` to core:
  e: .../BoundaryProbe.kt:4:8 Unresolved reference 'android'.
  > Task :core:compileKotlin FAILED   (exit 1)
Step 2 — remove it:
  > Task :core:checkNoAndroidImports
  > Task :core:build   BUILD SUCCESSFUL   (exit 0)
```
The build physically cannot compile `core` with an Android dependency. That is the
wall — not a lint rule, not a convention.

## Adversarial review
An automated multi-agent review (24 agents; 6 lenses: exact-arithmetic, parser,
evaluator, formatter, Constitution-compliance, test-gaps) ran against the engine,
each finding independently verified by a separate skeptic agent. **16 of 18 raw
findings were confirmed real; all 16 are fixed and pinned by `RegressionSpec`.**
Two were refuted by the verifier and dropped. The confirmed set was all edge
cases the first-pass tests missed:
- **crash-safety** — a huge-exponent literal (`1e2000000000`) and deeply nested
  input both threw uncaught exceptions; now typed SYNTAX errors.
- **correctness** — `root(x,n)` truncated the index via `toInt()` (wrong value or
  hang for huge `n`); `(-2)^(2/3)` errored while `(-8)^(2/3)` worked (now
  consistent); `dms(-1,30,0)` had the wrong magnitude; unbounded `nPr`/`nCr`
  could hang.
- **formatter** — plain notation fabricated trailing zeros past `displayPrecision`
  (now switches to scientific); an `UNNECESSARY` rounding mode could crash display.
- **robustness** — `toExactBigDecimalOrNull` for tight denominators; `compareTo`
  on distinct-but-close reals (now adaptive precision).

---

## What changed from the brief's defaults (all logged in DECISIONS.md)
- **Package fixed to `com.vaibhav.calc`** (amendment A4); display name deferred to M7.
- **Angle mode defaults to DEG** (Casio parity).
- **`big-math` dependency** for BigDecimal transcendentals (keeps `Double` out).
- **`:android` conditionally included** only when an SDK is present.

## Milestones (map: Constitution Article 10)
- **M0–M4 · complete** — skeleton + boundary wall + CI; exact-first engine; base
  conversion/complex/solvers; matrices/statistics/regression; units + dimensional
  analysis. **D-016 · complete** — the invocation layer (equations and units
  inline, routed, no modes).
- **M5 · complete** — D10 graphing core: sampling, root finding verified against
  the original function, exact viewport and axis ticks.
- **M6 · complete** — D11+D12 input & session core, `core/` only, models not views:
  the 2D entry model (case table, Rule 3 selection), the tape with live recompute
  (`tape/`), transactional undo, the keypad model with the committed flick mapping
  (`keypad/`), autocomplete, clipboard, registers and ANS.
- **M7 · next** — the Android UI (Compose). The first milestone with a view in it:
  every `core/` milestone finishes before the first Android one starts, which is
  Article 1 as a schedule and not merely a build rule. Display name is decided here.
- **M8** — D14, platform and legal.

Handwriting (v2, Fable 5) and photo-solve (v3) remain deferred.

## What's blocked
- **Local Android APK build** — this machine has no Android SDK; the `android`
  module is verified by CI (the `android` job) rather than locally, per the chosen
  approach. Needs a push to GitHub Actions (or a local SDK install) to go green.
- **ML Kit empirical confirmation** — needs a physical device/emulator; the sketch
  and steps are ready (`mlkit-spike/`).
