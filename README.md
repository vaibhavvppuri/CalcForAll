# CalcForAll

**An offline calculator for everyday questions and serious mathematics.**

CalcForAll is a native Android calculator with a shared Kotlin mathematics engine and an optional local HTTP API. It keeps exact fractions and supported symbolic values, explains errors, and brings equations, units, graphs and calculation history into one app.

**Application validation passed for Version 1.7 / code 10:** 1,546 host tests, 37/37 native tests on each API 23/34 emulator, 17 recorded manual observations (9 on API 23; 8 on API 34), and 19 packaged-API checks. Archive integrity and the final source commit are established separately by the release manifest and package audit. **Final Version 2.0 remains reserved.**

The same-path synthetic ink comparison recovers narrow 0/2/4/9 with explicit review, offers / instead of a mistaken root for the tested straight slash, and adds a single-symbol decimal candidate for a tap. Original continuous 9 still reads 9 but now needs review. The tested flat-top 3 still leads with incorrect 5; 3 is an alternative, so correction remains necessary. The same faint-fraction PNG changes from a rejected baseline layout to reviewed 1/2+1/3 = 5/6. Reduce shadows preserves the input and displays the processed image; the untreated manual fixture already read 46, so no shadow OCR accuracy gain is claimed. See the [verification record](docs/VERIFICATION.md) and [capability matrix](docs/CAPABILITY-MATRIX.md) for exact evidence and limits. The preceding public Version 1.6 retains its internal 2.0/code 9 identity and unchanged historical receipts.

Open **Tools → Photo calculator → Take photo / Choose image**, or **Tools → Write & calculate**. Clear supported transcriptions produce an automatic answer preview. Uncertain ink requires **I checked the expression · calculate** or an explicit correction; rejected layouts provide no automatic expression. Check the visible transcription before **Save reviewed calculation**. Processing stays on the device, without image uploads, paid APIs or runtime model downloads. Unsaved recognition drafts are memory-only; saving history is the durable action.

Recognition is an editable aid, not unrestricted cursive recognition or page understanding. Symbolic mathematics remains bounded rather than a full CAS. Physical phones, physical styluses, TalkBack and execution on a 16 KB-page Android runtime have not been tested.

This increment changes no bundled recognition model or runtime dependency. [Offline recognition model research](docs/RECOGNITION-MODELS.md) records possible future work and its licensing, Android-resource and validation requirements; no researched model is included in this release.

## What is included

- Scientific arithmetic: fractions, powers and roots, trigonometry, DEG/RAD/GRAD modes, logarithms, hyperbolic functions, combinatorics, complex values, and scientific/engineering notation.
- Direct equation and small linear-system solving, with explicit identities, no-solution results, and supported solution steps. Polynomial equations up to degree 8 — rational roots and quadratic factors stay exact, remaining real roots are counted with Sturm's theorem, complex roots are checked before they are shown, and anything approximate is marked `≈`.
- **Everyday tools:** loan EMI, compound and simple interest, SIP, GST added or removed (with the CGST + SGST split), discounts, percent change and bill splitting, a year-by-year loan repayment schedule, the loan a monthly payment can afford and the instalment a savings goal needs; days between dates, adding days, age and weekday; percent of an amount, unit-price comparison, fuel cost, proportion and BMI; adding or subtracting durations and clock times. Rates are always your input; nothing is fetched.
- Geometry with answers kept exact in π and square roots (a circle of radius 5 has area 25π, shown with its decimal beside it), arithmetic and geometric sequences, business maths (margin and markup, the price for a target margin, break-even, yearly growth rate), and quartiles with the interquartile range and mode. Also numbers and cheque amounts in words (in lakh and crore, and in millions), sharing an amount in a ratio, and inflation. A power written on a unit belongs to the unit (`5 m^2` is five square metres), and a device test exercises every catalogued tool operation. Current Android runtime acceptance is recorded separately; historical device passes do not verify this APK.
- **Study tools:** linear systems of up to six unknowns and matrix eigenvalues in the Matrices tool, numerical solving of supported real equations on a range, tables of values, sums (Σ) and products (Π), prime factors/GCD/LCM/divisors, vectors, exact binomial plus Poisson and normal probabilities, quadratic/exponential/power/logarithmic curve fits, Taylor polynomials, numerical limits (labelled as estimates, not proofs) and bitwise logic in any base.
- Structured entry for fractions, powers, roots and functions; an atomic Ans chip; searchable functions; editing and clipboard controls.
- Local calculation history with linked references, transactional edits, undo, and persistence. Changing the angle mode does not reinterpret earlier rows.
- Graphs with multiple curves, pan/pinch zoom, editable ranges, trace, roots and intersections. Undefined samples remain gaps; numerical root search reports its limits.
- A bounded 3D height-field grid for `z = f(x,y)`, with a rotatable wireframe and point tracing. Surface gaps and incomplete evaluations are reported. The Android screen is implemented; device verification is recorded separately in `docs/VERIFICATION.md`.
- Unit arithmetic in main entry and conversions through the Units tool/API, Indian units and digit grouping, Devanagari numerals, physical constants with uncertainty, matrices up to 6 × 6, descriptive statistics, linear regression, and integer base conversion.
- Bounded symbolic simplification, expansion, factorisation, differentiation and supported antiderivatives. Finite proper numerical integration is available in the shared core, local API and Tools, with an approximation marker and estimated error. Native adapter/device verification is recorded in `docs/VERIFICATION.md`.
- Light, dark, AMOLED and system themes, plus portrait and landscape layouts.
- **Write & calculate:** draw with a finger or stylus. Math layout supports bounded powers, stacked fractions, roots and equations, including a separate root hook/overbar and dotted `÷`. Confident readings calculate after pen-up; uncertain readings wait for explicit review. Numbered groups offer suggested matches and **Choose another symbol**; targeted guidance points to an unclear group or the expression editor. Complete groups and earlier corrections remain available after layout rejection and a same-drawing **Read again**. **Cancel reading** keeps the drawing; stale background results cannot replace newer ink. An unresolved or rejected layout supplies no automatic expression. Neat `%`/`!`, hooked 1, continuous 8, detached tops on 5/7 and crossed 7 retain conservative candidates. **Undo ink / Redo ink** cover drawing, erasing and Clear pad, within 32 snapshots and 96,000 total live/history points. Function buttons wrap the expression without approving uncertain symbols. This is local template matching; handwritten function words and general cursive are unsupported.
- **Photo calculator:** take or choose an image, crop/rotate it, and inspect the transcription. **Adjust angle / perspective** lets you position four corners and straighten by up to ±20°; **Apply corrections and read** displays and reads the transformed image. Bounded quality advice suggests lighting, crop or contrast changes without scoring recognition accuracy. **Boost contrast**, **Reduce shadows**, **Light text on dark** and explicit orientation choices remain available. The original image is retained, and failed replacement keeps the prior draft. Two or three complete equation rows may be offered as an explicit correction that retains every detected token. Ambiguous/incomplete layouts supply no automatic expression; complete raw text requires an explicit review action. **Type expression instead** provides a fallback. Photo/ink corrections and pending adjustments survive tool reopening and activity recreation in memory. Only **Save reviewed calculation** writes history; a failed save retains the draft for retry.
- The local API retains its additive `/v1` contract and now exposes finance, dates/time, words, vectors, number theory, probability, curve fitting, quartiles, Taylor/limits, tables/series, bitwise operations and matrix eigenvalue operations.

The calculator does not need an account, a subscription, a paid API, or a running server. Arithmetic and the supplied OCR model run locally. The Android manifest removes internet and network-state permissions and disables automatic app backup. Google's ML Kit SDK has its own terms and general metrics behavior, described in the [privacy policy](docs/PRIVACY.md); those terms are not replaced by this project's license.

## Quick start

The Version 1.7 handoff uses workspace `releases/Version 1.7`, containing `CalcForAll - Version 1.7.apk`, `CalcForAll - Version 1.7 - Source.zip`, `CalcForAll - Version 1.7 - Local API.zip` and `CalcForAll - Version 1.7 - Verification.zip`. Check the accompanying release manifest and passing package-audit receipt for the exact delivered files. The app targets Android 6.0 / API 23 and newer; emulator verification is not a claim of testing every phone.

Type with the keypad, or use **Edit** for text entry. Press **=** to calculate. Examples:

```text
0.1+0.2          → 0.3 exactly
1/3+1/6          → 1/2
sqrt(8)          → 2√2
sin(30)          → 0.5 in DEG mode
x^2-5x+6=0       → x = 2, 3
x^3-6x^2+11x-6=0 → x = 1, 2, 3
5 km+300 m       → 5.3 km
5→A             → store 5 in register A
A+2             → 7
```

Flick upward on **÷** for a root, **×** for a power, **−** for a fraction, or **.** for π. The functions sheet also offers structures without gestures. History entries can be copied for editing or inserted as live `#n` references. Graph and Tools are available from the main screen. Calculus uses radians even when ordinary scientific evaluation uses degrees.

For images, open **Tools → Photo calculator → Take photo / Choose image**. For ink, open **Tools → Write & calculate** and draw separate, neat symbols. Clear supported readings preview automatically; uncertain ink waits for review, and rejected layouts need correction or typed entry. Check the transcription before **Save reviewed calculation**. Previewing does not alter Ans, registers or existing history rows. The angle mode shown on these screens is the current calculator mode.

## Build and test

Use JDK 21, the checked-in Gradle wrapper, Android SDK platform 34, and the SDK build tools requested by Android Gradle Plugin. Dependency versions are pinned in [the version catalog](gradle/libs.versions.toml) and module build files. The first development build downloads dependencies; the built app then calculates offline. Later builds can use `--offline` when all dependencies are cached.

Set `ANDROID_HOME` to your Android SDK or create an untracked `local.properties` containing `sdk.dir=...`. On Windows use `gradlew.bat` in place of `./gradlew`.

```sh
# Pure JVM engine and API tests
./gradlew :core:test :api:test

# Portable B1/B2/B3 regression batteries
./gradlew :battery:test

# Android adapter tests, static checks, and installable debug APK
./gradlew :android:testDebugUnitTest :android:lintDebug :android:assembleDebug

# Device gate: engine/tool workflows, bundled OCR, capture URI and image decoding.
# Needs an emulator or phone attached; run it on the oldest supported Android.
./gradlew :android:connectedDebugAndroidTest

# Optional local server
./gradlew :api:run
```

The debug APK is produced at `android/build/outputs/apk/debug/android-debug.apk`. It is signed with a development key, not a Play Store production signing identity. The API defaults to `127.0.0.1:8080`; `:api:distZip` creates `api/build/distributions/api-1.7.zip`. Its complete contract and local distribution commands are in [API.md](docs/API.md).

The B1 fixture is included at `battery/fixtures/B1-v1.6.json`; tests no longer depend on a developer's Downloads folder. All three batteries run by default. A case classified `PROBE` records an exploratory/unsupported comparison and is not a passing assertion. Asserted failures fail the Gradle task. Read the generated battery reports alongside the JUnit reports rather than treating a harness invocation as proof that every mathematical case passed.

When no Android SDK is configured, `settings.gradle.kts` excludes `:android`; `:core`, `:api`, and `:battery` still build as plain JVM modules. CI explicitly removes Android SDK environment variables for that boundary check and separately runs Android tests, lint, and APK assembly. CI does not deploy or publish the app.

## Mathematical scope

The engine stores exact rationals, supported surds, and rational multiples of π/e; approximate computations use `BigDecimal` with explicit precision. Floating-point geometry is confined to the visual/ink boundary. “Exact-first” does not mean every result has a symbolic closed form.

Exact rationals and supported symbolic values retain their exact representation. A decimal estimate can carry a conservative absolute error bound; legacy or sampled estimates may instead have unknown accuracy. Arithmetic and supported elementary kernels propagate established bounds. When uncertainty affects the displayed digits, the result shows a visible error radius; a discontinuous decision such as rounding must be supported by the interval. Merely evaluating twice and obtaining similar digits is not a proof of accuracy. Reusable expression syntax and the API preserve known bounds rather than silently turning an estimate into an exact literal. See [NUMERIC-LIMITS.md](docs/NUMERIC-LIMITS.md) for the current scope and refusals.

Polynomial solving supports degree at most 8 with exact rational coefficients (including exact common-factor cancellation), plus explicitly written products of separately supported factors. Unsupported uncertain or irrational coefficients receive a typed explanation. Written positive powers preserve repeated factors; close roots are not merged by a numerical tolerance. Numerical range search can use endpoint signs when a bounded derivative enclosure proves strict monotonicity across an already continuity-checked interval. That optional path preserves angle mode, bisection, residual checks and the existing five-second budget. Unsupported or inconclusive derivatives use the existing scan, which can miss close or tangent roots; a refusal is not a no-solution result. Taylor polynomials approximate a function near one point, and numerical limits are sampled estimates. Money tools use the rates you enter; real loans, taxes and investments have terms this app does not know.

Symbolic operations deliberately support a bounded subset of real algebra and calculus. Unsupported expressions return a typed explanation. Domain conditions remain part of symbolic answers. Definite integration estimates, 2D/3D graph sampling and numerical root searches are not proofs of completeness or rigorous error bounds. The 3D view represents a single real height `z` at each sampled `(x,y)`; general implicit or parametric surfaces are outside this preview. See [SYMBOLIC.md](docs/SYMBOLIC.md) and [NUMERIC-LIMITS.md](docs/NUMERIC-LIMITS.md).

The Latin OCR model recognizes printed characters; a separate bounded layout reader interprets positions, powers, fraction rules and radical scope. Any transcription can be wrong. Close alternatives, unclear layouts and unsupported symbols are surfaced for correction; ambiguous O/0 is not silently repaired. Page rules can resemble fraction bars, and missing overbars can change a root's scope. Crop one expression or one clearly separated two/three-equation system and review it before saving. An equation-system suggestion is a transcription aid, not a promise that every proposed system can be solved.

Handwriting uses local geometric templates for digits, common operators, parentheses, square roots, π and x/y/z, with at most 64 strokes, 12,000 points and 64 spatial tokens. Clear separated writing is the intended input. General cursive, arbitrary handwritten words, diagrams, unrestricted page layouts and automatic natural-language word-problem solving are outside this release. Device fixture checks are not a population recognition-accuracy benchmark.

## Repository map

| Module | Responsibility |
| --- | --- |
| `core/` | Pure Kotlin arithmetic, parsing, evaluation, routing, units, matrices, statistics, symbolic calculus, graph sampling, editor, history and session codec. No Android dependency. |
| `android/` | Compose UI, local persistence, input adapters, graph rendering, symbol-pad geometry and bundled OCR integration. |
| `api/` | Stateless Ktor HTTP adapter over the same engine; loopback by default. |
| `battery/` | Test-only portable regression batteries and reference models. Nothing from this module ships in the app. |
| `mlkit-spike/` | Historical exploration, outside the build. It is not the active OCR implementation. |

## Documentation and licensing

- [User guide](docs/USER-GUIDE.md)
- [Product priorities and naming](docs/PRODUCT-PLAN.md)
- [Capability matrix](docs/CAPABILITY-MATRIX.md)
- [Verification and remaining release work](docs/VERIFICATION.md)
- [Privacy policy](docs/PRIVACY.md)
- [API contract](docs/API.md)
- [Third-party notices](THIRD-PARTY-NOTICES.md)

The original project code is licensed under the [MIT License](LICENSE), copyright 2026 Vaibhav. Third-party libraries, Google's ML Kit SDK and models, and bundled fonts retain their own licenses. The entire APK is therefore not described as “all MIT” or “all open source.”

The original constitution, decisions, specifications, handoff documents and battery reports remain as historical design evidence. Current implementation and verification documents take precedence when an older milestone description no longer reflects the app.
