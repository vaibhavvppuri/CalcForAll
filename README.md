# CalcForAll

**An offline calculator for everyday questions and serious mathematics.**

CalcForAll is a native Android calculator with a shared Kotlin mathematics engine and an optional local HTTP API. It keeps exact fractions and supported symbolic values, explains errors, and makes equations, units, graphs, and calculation history available in one app.

This repository is a **1.0 preview**. Its core, graphing, algebra, and input-recognition capabilities have different limits; the [capability matrix](docs/CAPABILITY-MATRIX.md) and [verification record](docs/VERIFICATION.md) distinguish implemented behavior from remaining release work. It is not a claim that every calculator or general computer-algebra feature is complete.

## What is included

- Scientific arithmetic: fractions, powers and roots, trigonometry, DEG/RAD/GRAD modes, logarithms, hyperbolic functions, combinatorics, complex values, and scientific/engineering notation.
- Direct equation and small linear-system solving, with explicit identities, no-solution results, and supported solution steps.
- Structured entry for fractions, powers, roots and functions; an atomic Ans chip; searchable functions; editing and clipboard controls.
- Local calculation history with linked references, transactional edits, undo, and persistence. Changing the angle mode does not reinterpret earlier rows.
- Graphs with multiple curves, pan/pinch zoom, editable ranges, trace, roots and intersections. Undefined samples remain gaps; numerical root search reports its limits.
- A bounded 3D height-field grid for `z = f(x,y)`, with a rotatable wireframe and point tracing. Surface gaps and incomplete evaluations are reported. The Android screen is implemented; device verification is recorded separately in `docs/VERIFICATION.md`.
- Unit arithmetic in main entry and conversions through the Units tool/API, Indian units and digit grouping, Devanagari numerals, physical constants with uncertainty, matrices up to 6 × 6, descriptive statistics, linear regression, and integer base conversion.
- Bounded symbolic simplification, expansion, factorisation, differentiation and supported antiderivatives. Finite proper numerical integration is available in the shared core, local API and Tools, with an approximation marker and estimated error. Native adapter/device verification is recorded in `docs/VERIFICATION.md`.
- Light, dark, AMOLED and system themes, plus portrait and landscape layouts.
- Optional **experimental** finger/stylus symbol entry and photo text recognition. The symbol pad works one digit/operator at a time. Photo text uses a bundled on-device OCR model and requires reviewing the recognised expression before calculation. Neither feature is a general handwritten-equation or word-problem solver.

The calculator does not need an account, a subscription, a paid API, or a running server. Arithmetic and the supplied OCR model run locally. The Android manifest removes internet and network-state permissions and disables automatic app backup. Google's ML Kit SDK has its own terms and general metrics behavior, described in the [privacy policy](docs/PRIVACY.md); those terms are not replaced by this project's license.

## Quick start

Install the supplied debug APK on a compatible Android device, or build it from source below. This preview targets Android 6.0 / API 23 and newer; check the verification record for the Android versions actually tested. It is not an assertion of testing on every phone.

Type with the keypad, or use **Edit** for text entry. Press **=** to calculate. Examples:

```text
0.1+0.2          → 0.3 exactly
1/3+1/6          → 1/2
sqrt(8)          → 2√2
sin(30)          → 0.5 in DEG mode
x^2-5x+6=0       → x = 2, 3
5 km+300 m       → 5.3 km
5→A             → store 5 in register A
A+2             → 7
```

Flick upward on **÷** for a root, **×** for a power, **−** for a fraction, or **.** for π. The functions sheet also offers structures without gestures. History entries can be copied for editing or inserted as live `#n` references. Graph and Tools are available from the main screen. Calculus uses radians even when ordinary scientific evaluation uses degrees.

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

# Optional local server
./gradlew :api:run
```

The debug APK is produced at `android/build/outputs/apk/debug/android-debug.apk`. It is signed with a development key, not a Play Store production signing identity. The API defaults to `127.0.0.1:8080`; `:api:distZip` creates `api/build/distributions/api-1.0.0-preview.zip`. Its complete contract and local distribution commands are in [API.md](docs/API.md).

The B1 fixture is included at `battery/fixtures/B1-v1.6.json`; tests no longer depend on a developer's Downloads folder. All three batteries run by default. A case classified `PROBE` records an exploratory/unsupported comparison and is not a passing assertion. Asserted failures fail the Gradle task. Read the generated battery reports alongside the JUnit reports rather than treating a harness invocation as proof that every mathematical case passed.

When no Android SDK is configured, `settings.gradle.kts` excludes `:android`; `:core`, `:api`, and `:battery` still build as plain JVM modules. CI explicitly removes Android SDK environment variables for that boundary check and separately runs Android tests, lint, and APK assembly. CI does not deploy or publish the app.

## Mathematical scope

The engine stores exact rationals, supported surds, and rational multiples of π/e; approximate computations use `BigDecimal` with explicit precision. Floating-point geometry is confined to the visual/ink boundary. “Exact-first” does not mean every result has a symbolic closed form.

Symbolic operations deliberately support a bounded subset of real algebra and calculus. Unsupported expressions return a typed explanation. Domain conditions remain part of symbolic answers. Definite integration estimates, 2D/3D graph sampling and numerical root searches are not proofs of completeness or rigorous error bounds. The 3D view represents a single real height `z` at each sampled `(x,y)`; general implicit or parametric surfaces are outside this preview. See [SYMBOLIC.md](docs/SYMBOLIC.md) and [NUMERIC-LIMITS.md](docs/NUMERIC-LIMITS.md).

The Latin OCR model recognises printed text, not general two-dimensional mathematical layout. Review signs, exponents, decimal points and fractions before using the text. Handwriting candidates are suggestions that require a selection; the app does not silently trust a match.

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
