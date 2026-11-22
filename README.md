# CalcForAll

**An offline calculator for everyday questions and serious mathematics.**

CalcForAll is a native Android calculator with a shared Kotlin mathematics engine and an optional local HTTP API. It keeps exact fractions and supported symbolic values, explains errors, and brings equations, units, graphs and calculation history into one app.

**Version 2.3.0 is an unaccepted candidate**, starting from delivered 2.2 source `8dc315d`. It adds explicit photo suggestions for two or three geometrically separated equation rows, a bounded ink-symbol correction palette, and recognition of a separately drawn root hook/overbar and dotted division sign. Complete ink groups remain available for correction when their layout is rejected; whole-drawing validation failures do not expose a partially recognized expression.

**The device acceptance gate has failed.** The current APK passed 1,481 host tests, lint with no errors, signature/permission and packaged-notice audits, static native-library checks, and 19 packaged-API smoke checks. The same APK passed **30 of 31 native checks on each of API 23 and API 34**: the default DEG `cos(x)=x` numerical range operation reached its existing five-second work budget and returned a typed refusal. Three completed API 23 runs reproduced that failure; the completed API 34 run also used the same installed test-APK bytes. Photo and ink checks passed in these runs. Nine recorded manual observations passed: four on API 23 and five on API 34, including reviewed three-row photo calculation, correction retention, one-save persistence and refusal to discard an extra heading. No numerical checks or time budgets were relaxed, and no causal explanation or physical-device performance claim is established. Candidate packaging records `releaseAccepted:false`; successful file-integrity checks do not override this failed gate.

Open **Tools → Photo calculator → Take photo / Choose image**, or **Tools → Write & calculate**. The visible transcription produces an automatic answer preview; check it before choosing **Save reviewed calculation**. Processing stays on the device, without image uploads, paid APIs or runtime model downloads. Unsaved recognition drafts are memory-only; saving history is the durable action.

**Historical version 2.2 checks passed:** 1,448 host tests, 27/27 native tests on each of Android 6 / API 23 and Android 14 / API 34 emulators, and 19/19 packaged-API smoke checks. Both native runs used the same 2.2 APK, verified its installed bytes and confirmed offline network state before and after testing. Combined device QA includes eight API 23 and nineteen API 34 manual observations, covering ink edits, photo treatment/selection, cancellation/retry, failed replacement and explicit system review/save/restart. Signature, permission, static native-library and packaged-notice audits passed. These results do not certify 2.3. Source/archive integrity is recorded separately in each release's manifest and package audit. See the [verification record](docs/VERIFICATION.md) and [capability matrix](docs/CAPABILITY-MATRIX.md) for exact scope and limits.

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
- **Write & calculate:** draw with a finger or stylus. Math layout proposes an editable expression and calculates it after pen-up; powers, stacked fractions, square-root scope and equations have bounded geometry support. Version 2.3 adds a separate root hook/overbar and dotted `÷`, with topology checks that retain their complete strokes. Numbered ink groups offer suggested matches and **Choose another symbol**; unknown groups show `?`, and correcting one group preserves earlier choices and geometry. An unresolved or rejected layout supplies no automatic expression. Neat `%`/`!`, hooked 1, continuous 8, detached tops on 5/7 and crossed 7 retain conservative candidates. **Undo ink / Redo ink** cover drawing, erasing and Clear pad, within 32 retained snapshots and 96,000 total live/history points. Function buttons wrap recognized text, such as `30 → sin(30)`; handwriting function words is unsupported. Recognition is local template matching, not a general trained model.
- **Photo calculator:** take a photo or choose an image, crop/rotate it, and inspect the editable transcription. A replacement image is staged before it replaces the old draft. **Boost contrast** and **Light text on dark** change the displayed preview before reading. **Try other orientations** offers completed choices progressively and keeps them if the search is interrupted; a mathematically valid answer never selects a rotation automatically. Version 2.3 can offer two or three complete equation rows as an explicit correction, reconstructing each row's powers/fractions and retaining every detected token. Ambiguous rows, marks that cannot be assigned safely, or unsupported syntax receive no such suggestion. Rejected layouts or incomplete OCR leave automatic expression entry empty; complete raw text remains available through an explicit review action. Crops decode retained source detail. Photo/ink corrections and pending crop requests survive tool reopening and activity recreation in memory. Only **Save reviewed calculation** writes history; a failed save retains the draft for retry.
- The local API retains its additive `/v1` contract and now exposes finance, dates/time, words, vectors, number theory, probability, curve fitting, quartiles, Taylor/limits, tables/series, bitwise operations and matrix eigenvalue operations.

The calculator does not need an account, a subscription, a paid API, or a running server. Arithmetic and the supplied OCR model run locally. The Android manifest removes internet and network-state permissions and disables automatic app backup. Google's ML Kit SDK has its own terms and general metrics behavior, described in the [privacy policy](docs/PRIVACY.md); those terms are not replaced by this project's license.

## Quick start

The supplied debug APK is a candidate with the numerical range-search limitation above. Review the [verification record](docs/VERIFICATION.md) before installing it, or build it from source below. The app targets Android 6.0 / API 23 and newer; this is not an assertion of testing on every phone.

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

For images, open **Tools → Photo calculator → Take photo / Choose image**. For ink, open **Tools → Write & calculate** and draw separate, neat symbols. A preview appears automatically; check the transcription and choose **Save reviewed calculation**. Previewing does not alter Ans, registers or existing history rows. The angle mode shown on these screens is the current calculator mode.

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

The debug APK is produced at `android/build/outputs/apk/debug/android-debug.apk`. It is signed with a development key, not a Play Store production signing identity. The API defaults to `127.0.0.1:8080`; `:api:distZip` creates `api/build/distributions/api-2.3.0.zip`. Its complete contract and local distribution commands are in [API.md](docs/API.md).

The B1 fixture is included at `battery/fixtures/B1-v1.6.json`; tests no longer depend on a developer's Downloads folder. All three batteries run by default. A case classified `PROBE` records an exploratory/unsupported comparison and is not a passing assertion. Asserted failures fail the Gradle task. Read the generated battery reports alongside the JUnit reports rather than treating a harness invocation as proof that every mathematical case passed.

When no Android SDK is configured, `settings.gradle.kts` excludes `:android`; `:core`, `:api`, and `:battery` still build as plain JVM modules. CI explicitly removes Android SDK environment variables for that boundary check and separately runs Android tests, lint, and APK assembly. CI does not deploy or publish the app.

## Mathematical scope

The engine stores exact rationals, supported surds, and rational multiples of π/e; approximate computations use `BigDecimal` with explicit precision. Floating-point geometry is confined to the visual/ink boundary. “Exact-first” does not mean every result has a symbolic closed form.

Exact rationals and supported symbolic values retain their exact representation. A decimal estimate can carry a conservative absolute error bound; legacy or sampled estimates may instead have unknown accuracy. Arithmetic and supported elementary kernels propagate established bounds. When uncertainty affects the displayed digits, the result shows a visible error radius; a discontinuous decision such as rounding must be supported by the interval. Merely evaluating twice and obtaining similar digits is not a proof of accuracy. Reusable expression syntax and the API preserve known bounds rather than silently turning an estimate into an exact literal. See [NUMERIC-LIMITS.md](docs/NUMERIC-LIMITS.md) for the current scope and refusals.

Polynomial solving supports degree at most 8 with exact rational coefficients (including exact common-factor cancellation), plus explicitly written products of separately supported factors. Unsupported uncertain or irrational coefficients receive a typed explanation. Written positive powers preserve repeated factors; close roots are not merged by a numerical tolerance. Numerical solving on a range samples that range and can miss roots that lie very close together or only touch zero. Taylor polynomials approximate a function near one point, and numerical limits are estimates from sampled values — an oscillating function is reported as not settling rather than given a number. Money tools are currency-neutral arithmetic on the rates you enter; they are not financial advice, and real loans, taxes and investments have terms this app does not know.

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
