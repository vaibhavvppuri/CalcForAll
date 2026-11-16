# CalcForAll — product decisions

Decision date: 10 November 2026. This is the build plan and naming rationale, not a claim that every planned feature has shipped. Consult the release notes and verification report for delivered capabilities.

## Purpose and boundaries

**CalcForAll** is a free, offline calculator for everyday arithmetic, study and scientific work. Its short description is **“Your everyday and scientific calculator.”** The name expresses who the product welcomes; it does not claim to solve every mathematical problem.

The three primary tasks are: enter and correct a calculation quickly; understand and reuse a result without losing its context; solve or inspect scientific expressions, equations and graphs on a phone. Handwriting and photos are alternate entry methods into that same trusted calculation engine.

Current user decisions take precedence over proposals in the older chats: no paid services, image uploads, required account or runtime cloud dependency; optional finger and stylus input; a free local/self-hosted API; MIT for original code with third-party notices preserved. The Android target is 6.0/API 23 and newer, matching the minimum stated on the [official WhatsApp Android download page](https://www.whatsapp.com/download/android) when checked on 10 November 2026. This is a target requiring device/API testing, not a guarantee about every Android device.

The user delegated name and priority choices and requested uninterrupted implementation. The older Working Engine and Hustle Engine approval handshakes therefore do not apply. Their useful methods—wide ideation, weighted comparison, counterarguments and verification—do apply.

## Why integration comes first

The starting repository already contains an unusually broad pure Kotlin engine: exact-number representations; scalar and complex arithmetic; equation solving; unit conversion; matrices; statistics; number bases; graph sampling; structured-entry nodes; linked history; registers; and function search. The initial Android shell exposes much less of this. The largest immediate gain is making those capabilities discoverable and reliable in the app, rather than duplicating the engine.

This assessment comes from the source modules under `core/src/main/kotlin/com/vaibhav/calc/core/`, including `Calculator.kt`, `route`, `entry`, `tape`, `graph`, `matrix`, `stats` and `units`, and the initial Android session/UI. Historical progress notes are not proof of present build status. A feature present in the engine is not automatically a completed app feature.

## Priorities: DPCC methods applied honestly

Evidence tier: **Tier C**. There is one detailed user brief and a source-code audit, but no representative customer survey, measured demand, production telemetry or independent effort estimates. MoSCoW is the scope decision; Kano categories below are hypotheses about the intended user, not survey findings. No invented Kano response counts, Better/Worse coefficients, RICE reach or revenue forecasts are used.

These are non-negotiable release requirements, so they cannot be traded away for a higher feature score:

- Correct results, explicit unsupported/domain errors, and bounded work for extreme expressions. An error must not silently reuse an old answer.
- Offline operation and local data control. Camera/import permissions only when necessary; no uploads, advertising, analytics or required service account.
- Editable expressions, understandable exact/approximate results, undo, reusable history and recoverable saved state.
- Basic touch and keyboard operation, useful screen-reader labels, readable layouts and an honest compatibility statement.
- A reproducible build, meaningful tests, third-party notices, install instructions and a list of known limits.

For the remaining work, scores are engineering judgments on a 1–10 scale: **U** user value 40%; **Q** confidence in delivering dependable quality 25%; **R** reuse/foundation value 20%; **E** effort advantage 15% (higher means easier). Weights total 100%. `Score = 0.40U + 0.25Q + 0.20R + 0.15E`.

The last column cross-checks value engineering: `V/E = value points / effort points`, following the DPCC sheet. These relative points are explicit assumptions supplied under the user's delegated product-owner decision; they are not hours, measured velocity or a second developer's estimate. A high ratio does not override a dependency or the release requirements above.

| Work | U | Q | R | E | Weighted /10 | Assumed value/effort |
|---|---:|---:|---:|---:|---:|---:|
| Expose engine, route expressions, keep reusable history | 10 | 9 | 10 | 8 | 9.45 | 13/3 = 4.33 |
| Structured entry, searchable functions and settings | 9 | 8 | 9 | 7 | 8.45 | 13/5 = 2.60 |
| Units, matrices, statistics, complex and base tools | 8 | 9 | 8 | 8 | 8.25 | 8/3 = 2.67 |
| Graph, trace, roots and intersections | 9 | 8 | 8 | 7 | 8.25 | 13/5 = 2.60 |
| Bounded symbolic algebra and calculus | 8 | 7 | 9 | 5 | 7.50 | 8/8 = 1.00 |
| Local/self-hosted JSON API | 6 | 8 | 8 | 8 | 7.20 | 5/3 = 1.67 |
| Finger/stylus entry with editable recognition | 9 | 5 | 7 | 4 | 6.85 | 13/13 = 1.00 |
| On-device photo-to-expression import | 7 | 5 | 7 | 5 | 6.20 | 8/13 = 0.62 |
| 3D plots | 5 | 5 | 3 | 2 | 4.15 | 3/13 = 0.23 |

The exact decimals make the arithmetic auditable, not the judgments precise. Tools and graph are tied; their order can follow implementation dependencies. Value/effort favors the small local API over symbolic work, while the weighted model favors symbolic work because it helps the phone user more directly. Resolve this by giving the phone workflow the critical path and developing the independent API alongside it when capacity exists. Handwriting remains valuable, but recognition uncertainty lowers its immediate delivery score; a small verified recognizer is preferable to a broad inaccurate claim.

| Scope for this delivery | MoSCoW | Kano hypothesis | Completion evidence |
|---|---|---|---|
| Reliable arithmetic/scientific entry, error handling, formatting, history, persistence and offline privacy | Must | Must-be | Representative real workflows and regression tests, including invalid input and restart |
| Discoverable existing equation, unit, matrix, statistics, complex/base and 2D graph capabilities | Must for the integrated v1 target | Performance | Each supported tool is reachable in the app and agrees with independently known examples |
| Advanced entry polish, bounded symbolic operations and calculus | Should | Performance | Documented grammar/domain limits; known-answer and rejection cases |
| Finger/stylus recognition with editable candidates | Should | Attractive, possibly Performance for stylus users | Small recognition corpus, low-confidence correction path, no forced auto-evaluation |
| Local API using the same engine | Should; independent of APK | Attractive for developers | Loopback default, request limits, valid/error response contracts, integration tests |
| Offline photo import with review before evaluation | Could | Attractive | Bundled/local recognition, rotated/poor-input failures, editable extracted text, no upload |
| 3D graphing and broad handwritten mathematical layout recognition | Could in a later increment | Attractive | Separate scope and real correctness/usability evaluation before a public claim |
| Paid/cloud solving, image uploads, signup, tracking and live exchange-rate fetching | Won't in this product direction | User explicitly rejects these | No required service or hidden network dependency |

“Won't” normally means not this increment; the items rejected by the user's offline/free requirement remain excluded unless that requirement changes. Statutory and licensing requirements are Musts. This plan does not substitute a feature count for usable workflows.

### Version labels are acceptance gates

- **v1:** integrated scientific calculator, history, graph and the existing tools are usable and verified.
- **v2:** add documented symbolic/calculus operations and a verified handwriting workflow. Partial support is described by its actual grammar and limits; it is not called a full computer algebra system.
- **v3:** add local photo-to-expression import with an explicit review/correction step. Text recognition alone is not a general photo or word-problem solver.
- **v4:** supply the local API, documentation and a reproducible package. API delivery can happen before all v2/v3 work because it is largely independent; that does not make unfinished handwriting or photo work complete.

Aim for completion by 04:00 on 11 November. If work remains at the user's 04:33 stop point, preserve a tested checkpoint and concrete pending work for resumption. Never relabel incomplete work “final” to meet the clock.

## Naming decision

Weights: **C** immediate calculator clarity 30%; **S** short, speakable spelling 20%; **F** fit with the user's inclusive purpose without an unlimited-solving promise 25%; **D** provisional distinctiveness 25%. `Total = 0.30C + 0.20S + 0.25F + 0.25D`. Scores are editorial judgments, with 1 weak, 5 mixed and 10 strong. Distinctiveness includes the limited store screening below, not legal availability. There was no audience naming survey.

| Rank | Candidate | C | S | F | D | Total /10 |
|---:|---|---:|---:|---:|---:|---:|
| 1 | CalcForAll | 9 | 9 | 10 | 6 | 8.50 |
| 2 | Calc for Everyone | 9 | 7 | 10 | 6 | 8.10 |
| 3 | AllWays Calculator | 8 | 7 | 9 | 7 | 7.80 |
| 4 | Anyday Calc | 8 | 8 | 8 | 7 | 7.75 |
| 5 | MathForAll | 7 | 8 | 10 | 6 | 7.70 |
| 6 | EveryoneCalc | 8 | 7 | 10 | 5 | 7.55 |
| 7 | CommonCalc | 7 | 8 | 8 | 7 | 7.45 |
| 8 | AllSum | 7 | 9 | 7 | 7 | 7.40 |
| 9 | DailyMath | 7 | 9 | 7 | 7 | 7.40 |
| 10 | CalcTogether | 7 | 7 | 9 | 6 | 7.25 |
| 11 | Calculator for All | 10 | 6 | 10 | 2 | 7.20 |
| 12 | AllCalc | 9 | 10 | 9 | 1 | 7.20 |
| 13 | AnyMath | 7 | 9 | 8 | 5 | 7.15 |
| 14 | Everything Calculator | 10 | 3 | 7 | 7 | 7.10 |
| 15 | EveryCalc | 9 | 9 | 9 | 1 | 7.00 |
| 16 | FullCalc | 8 | 9 | 7 | 4 | 6.95 |
| 17 | TotalCalc | 8 | 9 | 7 | 4 | 6.95 |
| 18 | OneCalc | 8 | 10 | 7 | 3 | 6.90 |
| 19 | Universal Calculator | 10 | 4 | 7 | 5 | 6.80 |
| 20 | AllMath Calculator | 8 | 7 | 10 | 2 | 6.80 |
| 21 | PocketMath | 7 | 8 | 7 | 5 | 6.70 |
| 22 | PlainCalc | 8 | 9 | 5 | 5 | 6.70 |
| 23 | OpenCalc | 8 | 9 | 7 | 3 | 6.70 |
| 24 | OmniCalc | 6 | 9 | 9 | 3 | 6.60 |
| 25 | Everyday Calculator | 9 | 5 | 8 | 3 | 6.45 |

### Countercheck of the leading five

Exact-name searches restricted to the official Google Play and Apple App Store domains on 10 November 2026 did not surface exact matching listings for the five finalists. Search coverage is incomplete; this is not a trademark search or a claim of availability.

| Finalist | Strongest point | Attempt to disprove it | Decision |
|---|---|---|---|
| **CalcForAll** | Short and directly expresses inclusion | “For all” is a promise the accessibility work must earn; a close descriptive phrase already exists in “Calculator for All: Solves” | Choose as the working name; keep capability claims specific and disclose the descriptive neighbor |
| Calc for Everyone | Easy to understand | Longer launcher label; same broad accessibility expectation and descriptive crowding | Strong alternate, but no advantage over the shorter winner |
| AllWays Calculator | Suggests multiple methods | Spoken “always” and written “all ways” are easily confused; may imply unlimited methods | Loses spelling clarity |
| Anyday Calc | Friendly everyday use | Does not convey the scientific range or inclusion as clearly | Better for a basic calculator than this brief |
| MathForAll | Inclusive and broad | Could mean a course/community rather than a calculator | Loses immediate product clarity |

Among the five, CalcForAll and Calc for Everyone tie for best clarity and purpose fit; CalcForAll wins brevity. AllWays Calculator and Anyday Calc have the strongest provisional distinctiveness, while MathForAll is least clear about being a calculator. Moving five percentage points from clarity to distinctiveness leaves CalcForAll first (8.35 versus the next score, 7.95); the choice is not a consequence of one narrow weight setting.

Official listings confirming the crowded naming landscape are [Calculator for All: Solves](https://apps.apple.com/us/app/calculator-for-all-solves/id1394359548), [AllCalc - Calculator](https://play.google.com/store/apps/details?id=com.nervone.AllCalc), [EveryCalc](https://apps.apple.com/au/app/everycalc/id6759590989), and the [AllMath developer catalog](https://play.google.com/store/apps/developer?hl=en&id=AllMath). They establish existing names, not an assessment of those products' quality. AllCalc, EveryCalc and AllMath Calculator were weakened by this check. No app was installed, account created or store submission made for this research.

## Method sources

The user's current answers establish the product constraints and delegation. The supplied historical calculator chats/specifications establish the intended feature families. The current repository establishes what already exists. The user's DPCC cheat sheet supplies MoSCoW, value/effort and Kano definitions; its safely extracted text was used without treating an absence of survey responses as data. The Working Engine and Hustle Engine supplied the weighted comparison and countercheck method. Scores and effort assumptions above are planning judgments, and should be revisited when actual implementation or user evidence changes them.
