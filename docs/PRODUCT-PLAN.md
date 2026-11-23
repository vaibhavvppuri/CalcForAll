# CalcForAll — product decisions

Decision date: 10 November 2026. This is the build plan and naming rationale, not a claim that every planned feature has shipped. Consult the release notes and verification report for delivered capabilities.

**Current naming decision, 15 November 2026:** the user has named this public delivery **Version 2.0**, Android version code **9**. It continues development candidate `2.3.0 / 395f721`; the dated development labels below remain intact as historical provenance. The final section records the current scope, completed application validation and remaining product limits. Renaming alone is not evidence of completion; distribution integrity has separate release receipts.

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

## Version 1.2 increment — priorities (12 November 2026)

The owner asked for the next version "with more", said everything worth building should be built, and asked that the order be decided with the same DPCC methods. Evidence is still **Tier C**: one owner brief plus a source audit, with no survey or telemetry. So MoSCoW is the scope decision, Kano categories are hypotheses, and the scores are engineering judgments on the weights used above: `Score = 0.40U + 0.25Q + 0.20R + 0.15E`.

The release requirements at the top of this document are Musts and were not scored. Every addition ships with tests, runs offline, refuses what it cannot do with a typed message, and is described by its real limits.

| Rank | Work | U | Q | R | E | Weighted /10 | MoSCoW for 1.2 | Kano hypothesis | Outcome |
|---:|---|---:|---:|---:|---:|---:|---|---|---|
| 1 | Everyday money tools: EMI, interest, SIP, GST, discount, percent change, bill split | 9 | 9 | 6 | 8 | 8.25 | Should | Performance for everyday users | Built |
| 2 | Polynomial equations to degree 8, plus numerical solving on a range | 9 | 7 | 8 | 6 | 7.85 | Should | Performance for students | Built |
| 3 | Table of values, sums (Σ) and products (Π) | 7 | 9 | 6 | 9 | 7.60 | Should | Performance | Built |
| 4 | More everyday tools: loan affordability and savings goal, percent of, unit price, fuel cost, proportion, BMI, durations and clock times | 8 | 9 | 4 | 8 | 7.45 | Should | Performance for everyday users | Built |
| 5 | Linear systems up to six unknowns, matrix eigenvalues, loan repayment schedule | 7 | 9 | 6 | 7 | 7.30 | Should | Performance | Built |
| 6 | Date tools: days between, add days, age, weekday | 7 | 9 | 4 | 9 | 7.20 | Should | Attractive | Built |
| 7 | Vectors | 6 | 9 | 5 | 8 | 6.85 | Could | Performance for students | Built |
| 7 | Number theory: prime factors, GCD/LCM, divisors | 6 | 9 | 5 | 8 | 6.85 | Could | Attractive | Built |
| 9 | Inline unit conversion (`5 km to m`) | 8 | 6 | 7 | 3 | 6.55 | Could | Attractive | Deferred — see below |
| 10 | More regression models: quadratic, exponential, power, logarithmic | 6 | 8 | 5 | 7 | 6.45 | Could | Performance | Built |
| 10 | Probability distributions: binomial, Poisson, normal | 7 | 7 | 5 | 6 | 6.45 | Could | Performance | Built |
| 12 | Programmer bitwise logic | 5 | 9 | 4 | 8 | 6.25 | Could | Attractive | Built |
| 13 | Taylor polynomials and numerical limits | 7 | 5 | 7 | 3 | 5.90 | Could | Attractive | Built, as labelled approximations and estimates |
| 14 | Full Hindi interface | 7 | 5 | 4 | 4 | 5.45 | Won't in this increment | Performance for Hindi-first users | Deferred |
| 14 | General handwritten/photo mathematical layout recognition | 9 | 2 | 6 | 1 | 5.45 | Won't in this increment | Attractive | Deferred |
| 16 | Graph sliders, parametric and polar curves | 7 | 4 | 5 | 4 | 5.40 | Could, later | Attractive | Deferred |

What the ranking did and did not decide:

- **Inline conversion was first scored 7.50** (Q 8, E 6) and looked like the fourth item. Reading the code changed the estimate: history entries are parsed by the tape itself for dependency analysis before they reach the router, so `to` has to become grammar — handled by the parser, the tape, the formatter and the API — not a router shortcut. Re-scored honestly (Q 6, E 3) it is 6.55, and it was deferred rather than half-built. The Units tool still converts.
- **Graph upgrades and the Hindi interface lost on Q, not on value.** The owner chose tests + lint + APK as this increment's verification, with no emulator pass. Both are almost entirely screen work that unit tests cannot see, so their quality could not be established here.
- **General handwriting/photo recognition** is unchanged from the earlier assessment: valuable, but there is no offline trained model whose accuracy could be measured in this increment.
- Ties were broken by dependency and reuse. The data-driven tool catalogue was built once for item 1 and made the later items cheap, which is why so many "Could" items were delivered. The fourth and fifth rows were added during the build — the fourth once the first pack showed how little each further everyday tool cost, the fifth once the polynomial solver existed and eigenvalues became a matter of handing it a characteristic polynomial. Both were scored on the same scale rather than slipped in unranked.

Value-engineering cross-check (`value points / effort points`, the DPCC sheet's ratio; the points are assumptions, as above): money tools 13/3 = 4.33; table and series 8/2 = 4.00; dates 5/2 = 2.50; polynomial solver 13/8 = 1.63; Taylor and limits 8/8 = 1.00; inline conversion 8/13 = 0.62. The ratio agrees with the weighted order at both ends.

## Version 1.3 increment (12 November 2026, evening)

With about forty minutes of the session left, the owner asked for the next version to be started. What it holds was chosen the same way as before, from what could be built **and tested** in that time. Same weights, same Tier C caveat.

| Rank | Work | U | Q | R | E | Weighted /10 | MoSCoW for 1.3 | Kano hypothesis | Outcome |
|---:|---|---:|---:|---:|---:|---:|---|---|---|
| 1 | Geometry, exact in π and square roots | 8 | 8 | 5 | 8 | 7.40 | Should | Performance for students | Built |
| 2 | Business maths: margin, markup, break-even, growth rate | 7 | 9 | 4 | 9 | 7.20 | Should | Performance for shopkeepers and small businesses | Built |
| 3 | Sequences: arithmetic and geometric | 6 | 9 | 4 | 9 | 6.80 | Could | Performance for students | Built |
| 4 | Quartiles, interquartile range and mode | 6 | 8 | 4 | 8 | 6.40 | Could | Performance | Built |

The owner then told the session to carry on and improve 1.3 as far as it could. The time went first to correctness and verification, because a wrong answer costs more than a missing tool: three further review passes, a grammar correction for powers on units, and a device gate on Android 6 and Android 14 emulators (all in [VERIFICATION.md](VERIFICATION.md)). After that, three small additions were ranked and built the same way:

| Rank | Work | U | Q | R | E | Weighted /10 | MoSCoW for 1.3 | Kano hypothesis | Outcome |
|---:|---|---:|---:|---:|---:|---:|---|---|---|
| 1 | Numbers and cheque amounts in words, in lakh and crore | 7 | 9 | 3 | 8 | 6.85 | Could | Attractive for everyday users | Built |
| 2 | Share an amount in a ratio | 6 | 9 | 3 | 9 | 6.60 | Could | Performance for students | Built |
| 3 | Inflation: future price and buying power | 6 | 8 | 3 | 9 | 6.35 | Could | Attractive | Built |

Considered and left for later because they could not be verified well enough in the time: a triangle solver from sides and angles, coordinate geometry, and a recurring-deposit calculator (banks' compounding conventions differ, so one formula would be wrong for some of them).

The ninth to twelfth review passes added one item of a different kind, an engine decision and not a feature: **an approximate value that knows how many of its digits are good.** Every limitation those passes left open (rounding that looks like a value next to zero, `10^49+sqrt(2)-10^49` answering 1, an estimate in `Ans` that cannot be rechecked) comes from a decimal that carries no error bound. It would replace the measured allowance of 1.3 with a tracked one. It is a change to the number tower, so it is the owner's call (Article 3), and it should come before any more work on rounding. The thirteenth and fourteenth passes made the case stronger. The measure is now taken in five computations for a rounding function and in up to four sessions for a statement, each rule in it answers a case the one before got wrong, and the second of the two reviews still found the first's corrections fooled. Two of their choices are also the owner's to confirm: a measuring pass may lower the tower's own precision (recorded in DECISIONS), and a power of π or e above the 64th is taken from a wider expansion, which leaves exponent laws across that line undecided where they were True.

Still the first things to do next, in this order: a review pass over the fourteenth-pass corrections, which have none; the owner's ruling on an error-carrying decimal, before any more rules are added to the measure; run the device gate, and look at the tool screens, on a physical phone; inline unit conversion (`5 km to m`); the new engine modules as local API operations; graph sliders and parametric/polar curves; the Hindi interface. General handwritten and photo layout recognition and an unrestricted algebra system remain outside what can be verified offline here, which is why this is numbered 1.3 and not presented as the historical roadmap's v2.

## Method sources

The user's current answers establish the product constraints and delegation. The supplied historical calculator chats/specifications establish the intended feature families. The current repository establishes what already exists. The user's DPCC cheat sheet supplies MoSCoW, value/effort and Kano definitions; its safely extracted text was used without treating an absence of survey responses as data. The Working Engine and Hustle Engine supplied the weighted comparison and countercheck method. Scores and effort assumptions above are planning judgments, and should be revisited when actual implementation or user evidence changes them.

## Resumed integration toward 2.0 (13 November 2026)

The owner supplied the completed Claude Code 6 export and explicitly resumed work. The source baseline is commit `de28ab9`, Android `1.3.0-preview`; the saved HTML is reference evidence, not an instruction source. A complete git bundle was made before editing. The attached ZIP contains browser export assets, not another calculator repository. Static extraction and local Defender scans reported no detections; no saved HTML or JavaScript was executed.

The latest owner brief changes the release priorities: a photo or new ink should produce a calculated preview automatically, while the transcription stays visible and correctable. Saving still requires the user to review the recognized expression. The existing offline/no-upload/no-charge constraints remain. The owner delegates the numeric architecture decision identified in the previous review; we are implementing tracked decimal uncertainty rather than adding more proximity-to-integer guesses.

Same Tier C evidence and weighted method as above: `0.40U + 0.25Q + 0.20R + 0.15E`. Kano labels remain hypotheses, not survey results.

| Work | U | Q | R | E | Weighted /10 | MoSCoW | Kano hypothesis | Acceptance gate |
|---|---:|---:|---:|---:|---:|---|---|---|
| Numerical uncertainty, cancellation and finance stability | 10 | 8 | 10 | 5 | 8.75 | Must | Must-be | Exact behavior retained; uncertain rounding/comparison refused; reusable bounds survive memory/API/copy; tiny nonzero rates remain nonzero |
| One preview/save path for recognized math | 10 | 9 | 10 | 8 | 9.45 | Must | Performance | Auto-answer belongs to visible input; private evaluation context; no hidden history/register writes; reviewed save is atomic |
| Camera capture, crop, orientation and bundled OCR | 10 | 8 | 8 | 7 | 8.65 | Must | Performance | First-use offline recognition, capture/cancel/import/crop checks; only private capture directory shared; temporary files removed |
| Spatial ink and OCR layout | 10 | 6 | 9 | 4 | 7.85 | Must for documented grammar | Performance | Powers, fractions, roots and equations on fixtures; ambiguity/candidate correction; strict resource limits; unsupported layouts explicit |
| API parity with new reusable engine modules | 7 | 9 | 9 | 8 | 8.05 | Should | Attractive | Additive v1 endpoints, bounded inputs, error and known-answer integration tests; local distribution smoke test |
| Handwriting undo, redo, eraser and stylus filtering | 8 | 8 | 7 | 8 | 7.80 | Should | Performance | Actual touch workflow, mode changes, rejected input and stale-result clearing |
| General cursive/page/word-problem solving | 9 | 2 | 6 | 1 | 5.45 | Won't in this bounded release | Attractive | No licensed, verified, small offline model currently integrated; never advertise template matching as general recognition |

The higher preview score does not override its dependence on numerical correctness. Work proceeds in parallel where files and interfaces are independent. Internal milestones are **1.4 reliability**, **1.5 input workflows**, and **2.0 integrated delivery**. These are acceptance stages, not three renamed copies of the same APK. No milestone is marked complete before its test/device evidence exists. The old 11 November overnight deadline does not apply to this explicitly resumed session.

The number 2.0 denotes the new integrated workflow and release, not completion of every speculative v2-v4 feature in the historical chat. A bounded mathematical grammar, explicit uncertainty and documented unsupported inputs remain essential to a dependable calculator.

## Version 2.1: dependable recognition and recovery (14 November 2026)

The owner asked to continue beyond 2.0, especially toward more complete offline photo and finger/stylus calculation. The baseline is `f38a2ac`, the delivered 2.0 source. This increment improves the supported workflows; it does not redefine geometric templates or bundled text OCR as unrestricted handwriting recognition. No paid service, image upload, account or runtime model download is introduced.

Evidence remains **Tier C**: the owner brief, source review, reproducible geometry fixtures and the earlier emulator observations. There is no representative user survey or measured general recognition accuracy. Kano labels are hypotheses, MoSCoW defines scope, and the existing weighted method remains `0.40U + 0.25Q + 0.20R + 0.15E`. Scores guide order within dependencies; they cannot trade away a Must. Every row below is a planned acceptance gate, not a claim of completion.

| Rank | Work | U | Q | R | E | Weighted /10 | MoSCoW | Kano hypothesis | Acceptance gate |
|---:|---|---:|---:|---:|---:|---:|---|---|---|
| 1 | Refuse partial OCR and preserve every available source symbol | 10 | 9 | 10 | 7 | 9.30 | Must | Must-be | Incomplete symbol boxes fall back to complete element text or refuse; missing geometry and truncated pages cannot silently become prefix calculations; raw text remains auditable |
| 2 | Durable reviewed save with visible recovery | 10 | 9 | 10 | 7 | 9.30 | Must | Must-be | One explicit Save produces one history entry; failed persistence retains the draft and reports failure; successful save survives force-stop/restart |
| 3 | Erase stroke segments and preserve geometry when the canvas resizes | 9 | 9 | 8 | 8 | 8.65 | Must | Performance | Sparse fast strokes erase when the eraser crosses a segment; motion history and pen-up are included; resize/rotation keeps ink and candidate overlays in the same coordinate system |
| 4 | Cancellation, serialized work and stale-result rejection | 9 | 8 | 10 | 7 | 8.65 | Must | Must-be | Rapid edit/crop/close operations do not queue overlapping recognition or publish an answer from older input; cancellation does not save a partial stroke or calculation |
| 5 | Retain the editable ink/photo draft across supported lifecycle changes | 9 | 8 | 9 | 7 | 8.45 | Must | Must-be | Close/reopen and configuration changes preserve the intended draft, review state and coordinate relationships; an unavailable image or failed restoration is explicit |
| 6 | Precise crop and orientation handling | 9 | 8 | 8 | 7 | 8.25 | Must | Performance | Selected image bounds map to the actual cropped bitmap; edge rounding cannot remove a requested final row/column; rotate/crop/restore retain an inspectable source and correct transcription |
| 7 | Conservative percent and factorial handwriting | 8 | 7 | 7 | 8 | 7.55 | Should | Performance | Three-stroke percent and stem-plus-dot factorial reach `%`/`!`; `1.1`, baseline `0/0`, fraction bars and tap-only input are negative regressions; structural interpretations stay reviewable |
| — | Unrestricted cursive, arbitrary pages and word-problem interpretation | 9 | 2 | 6 | 1 | 5.45 | Won't in 2.1 | Attractive | Requires a separately licensed offline model and representative validation; neither synthetic templates nor a small photo corpus establish this capability |
| — | Unrestricted computer algebra | 8 | 2 | 8 | 1 | 5.45 | Won't in 2.1 | Attractive | Existing bounded mathematical contracts remain; no claim that every equation or symbolic operation is supported |

The priority countercheck is failure cost: adding symbols helps entry, but preserving the complete input and making save/recovery truthful are more important than a larger vocabulary. Numeric answers must still come from the visible transcription, and offered OCR corrections must remain explicit choices. Automatic matches and matches reached after a selected correction are reported separately.

Release acceptance requires fresh JVM/Android checks, offline device runs on the supported API floor and a newer Android runtime, actual camera/ink/crop/erase/save/restart workflows, and artifact hashes tied to those runs. Final test counts and device results are frozen only after execution; 2.0 receipts are retained separately and cannot stand in for 2.1 evidence. The release helpers under the external work folder deliberately refuse unresolved count or gate-log placeholders. README and verification claims are updated only after the resulting evidence exists.

The 2.1 outcome is now recorded in [VERIFICATION.md](VERIFICATION.md): Gate2 passed 1,384 host tests, both API 23/API 34 emulators passed 21 native tests, and the packaged API passed 19 smoke checks. The recorded manual workflows cover input, crop/reopen, erasing, reviewed-save failure/retry and restart. Cancellation/serialization passed unit tests; the manual read completed before the cancellation tap, so that specific manual gate is not claimed. Sideways OCR remained inaccurate with an explicit layout warning, and Restore original recovered the correct expression. These limits and the remaining physical-device/accessibility work remain visible; a release number does not convert them into completed general recognition.

## Version 2.2: recoverable edits and explicit recognition choices (14 November 2026)

The authorized next increment starts from delivered **2.1.0 / `6e82146`**. Evidence remains **Tier C**: the owner's priorities, source review, explicit 2.1 limitations and small synthetic/manual fixtures. No customer survey, measured population accuracy or model benchmark is invented. Kano labels remain hypotheses and the same weighted method applies: `0.40U + 0.25Q + 0.20R + 0.15E`. A high score cannot waive a Must or a negative regression.

| Rank | Work | U | Q | R | E | Weighted /10 | MoSCoW | Kano hypothesis | Acceptance requirement |
| ---: | --- | ---: | ---: | ---: | ---: | ---: | --- | --- | --- |
| 1 | Withhold automatic expressions from rejected/incomplete OCR | 10 | 9 | 10 | 8 | 9.45 | Must | Must-be | A flattened or truncated but calculable string cannot bypass missing geometry or incomplete coverage; complete raw text requires explicit review |
| 2 | Preserve the old photo draft until replacement decoding succeeds | 9 | 9 | 9 | 8 | 8.85 | Must | Must-be | Failure/cancellation before decoding succeeds retains the prior image and correction; successful replacement publishes consistent source, preview and text |
| 3 | Undo/redo complete drawing, erase and clear edits | 9 | 9 | 9 | 7 | 8.70 | Must | Performance | One gesture is one edit; cancelled erase rolls back; redo branches and 32-snapshot/96,000-point budgets are enforced; memory-only state is clear |
| 4 | Apply scientific functions to a reviewed expression | 8 | 9 | 8 | 9 | 8.40 | Should | Performance | `30 → sin(30)` retains visible syntax and angle mode; empty input begins typed entry; no claim of recognizing handwritten function words |
| 5 | Offer explicit alternative orientations | 9 | 8 | 8 | 7 | 8.25 | Should | Performance | Bounded rotated reads leave the old draft unchanged until selection; selected text matches the displayed rotation; no numerical-validity winner |
| 6 | Recognize common digit styles conservatively | 9 | 7 | 7 | 8 | 7.95 | Should | Performance | Hooked 1, continuous 8 and detached/crossed 7 or detached 5 have independent fixtures; fraction/overline/subtraction negatives prevent swallowed marks |
| 7 | Display contrast and light-on-dark treatment before OCR | 8 | 8 | 7 | 8 | 7.80 | Should | Performance | Preview and OCR use the same bounded image; original remains recoverable; failed transformations and lost details are explicit |
| — | Research a separately licensed offline learned model | 9 | 3 | 7 | 2 | 6.05 | Could research; won't bundle in 2.2 | Attractive | [Recognition model research](RECOGNITION-MODELS.md) identifies licensing, Android conversion, full offline assets, memory/latency and independent-data gates before integration |

This increment changes no bundled recognition model or runtime dependency. Unrestricted cursive, arbitrary pages/word problems and full CAS remain outside its scope. Better editing and image preparation reduce correction effort; a valid calculated expression is never evidence that a transcription is correct.

**Acceptance checkpoint, 15 November 2026:** current 2.2 passed **1,448 host tests** (1,043 core, 327 Android, 69 API, 9 battery), **27/27 native tests on each of API 23 and API 34** on the same exact APK offline, and **19/19 packaged-API smoke checks**. Security, static native and notice audits passed. Combined device QA passed with eight API 23 and nineteen API 34 observations. API 23 exercised emulator touch ink, whole eraser/clear undo/redo, function wrapping and reopening. API 34 confirmed explicit orientation selection, active-search cancellation/retry, synthetic camera capture, failed replacement preserving the draft, contrast/restoration, dark-image polarity, and explicit rejected-system review/save/restart. Progressive partial-choice behavior has host coverage; a late tap after a completed read is not counted as a cancellation pass.

The [verification record](VERIFICATION.md) identifies the exact APK, tests, manual observations and unexercised limits. Source/archive integrity is determined separately by the manifest and package-audit receipt, without inferring an archive pass from tests. Earlier candidate orientation timeouts and the numeric deadline failure remain preserved; their fixes did not increase deadlines, relax mathematical checks or establish a general performance gain. All 2.1 artifacts and receipts remain historical evidence. Physical phone/stylus, TalkBack, a 16 KB-page runtime and representative recognition-accuracy evaluation remain future work.

## Version 2.3: correctable ink tokens and explicit equation-layout review (15 November 2026)

This authorized increment starts from delivered **2.2.0 / `8dc315d`**. The focus remains offline, free photo and handwriting calculation with visible review. Evidence is **Tier C**: owner priorities, the actual 2.2 source and its limited synthetic/manual observations. Kano categories and ratings are hypotheses, not a customer survey or measured recognition accuracy. The existing score remains `0.40U + 0.25Q + 0.20R + 0.15E`; a score cannot waive a Must or a negative regression.

| Rank | Work | U | Q | R | E | Weighted /10 | MoSCoW | Kano hypothesis | Acceptance requirement |
| ---: | --- | ---: | ---: | ---: | ---: | ---: | --- | --- | --- |
| 1 | Retain complete ink tokens when layout cannot yet calculate | 10 | 9 | 10 | 8 | 9.45 | Must | Must-be | Rejected layouts keep every reviewable token, correction and spatial identity; incomplete/budget-limited recognition never becomes a usable prefix or answer |
| 2 | Validate correction, ambiguity and stale-result boundaries independently | 9 | 9 | 10 | 7 | 8.90 | Must | Must-be | Negative fixtures cover missing/overlapping marks and work limits; edit/erase/undo/mode/cancellation cannot apply an old correction to new ink; final native/manual checks use exact artifacts offline |
| 3 | Replace one ink glyph without typing the entire expression | 9 | 9 | 9 | 8 | 8.85 | Must | Performance | A bounded palette changes only the selected token; rejected rebuilds retain that edit and suppress the answer; a later correction can recover a full expression; x/× intent remains explicit |
| 4 | Offer explicit proposals for complete two/three-row equation images | 9 | 8 | 9 | 7 | 8.45 | Should | Performance | Every OCR token is accounted for exactly once; incomplete rows, extra text, stacked fractions, missing positions and ambiguous layouts refuse; no automatic raw-text fallback or numerical winner |
| 5 | Support conservative two-stroke radicals and dotted division | 9 | 8 | 8 | 8 | 8.40 | Should | Performance | Independent stroke paths, varied order and delayed bars work; minus/fraction/colon/decimal/neighboring-digit negatives prevent swallowed marks; grouping stays reviewable |

The baseline's identical x and multiplication templates cannot establish user intent from geometry alone. Explicit correction improves that workflow without claiming to solve the ambiguity. Likewise, the 2.2 two-line photo fixture required manual raw review; a separate complete-equation proposal must reduce review effort without silently flattening a rejected layout.

**Historical development 2.3 outcome:** source `395f721` was handed off as an unaccepted candidate. Its host/static/API checks passed, but all four completed final native runs reached the existing five-second default DEG `cos(x)=x` root-search budget and finished 30/31. Photo/ink cases and nine manual observations passed without overriding that failure. Candidate archive integrity and `releaseAccepted:false` were recorded separately. The [verification record](VERIFICATION.md) retains the exact failed runs, current-artifact observations and limitations. Automatic, explicitly assisted and raw-review outcomes remain separate; this candidate is the baseline for the current work below.

No bundled learned model, runtime download, paid service or image upload is implied by this increment. Unrestricted cursive, handwritten function-word recognition, arbitrary pages/word problems and full CAS remain outside scope. Physical phones/styluses, TalkBack, a 16 KB-page runtime and a representative recognition dataset remain separate validation work. Source/archive integrity will be documented by the release-specific manifest and audit rather than inferred from test declarations.

## Public Version 2.0: recover difficult input without guessing (15 November 2026)

The owner asked for the best feasible offline/free recovery from difficult photos and handwriting, clearer fallback when recognition cannot help, and a responsive calculator. The source baseline is development candidate **`395f721`**. The public name is **Version 2.0 / code 9**; internal working folders may retain the development label. Prior artifacts and failed receipts remain immutable. This increment adds no bundled model, runtime model download, paid API or image upload.

Evidence remains **Tier C**: the user brief, source review, bounded independent fixtures and recorded emulator failures. Kano classifications are hypotheses, not survey findings. The existing weighting is `0.40U + 0.25Q + 0.20R + 0.15E`; dependencies and Must requirements override ranking. No score substitutes for exact-artifact verification.

| Rank | Work | U | Q | R | E | Weighted /10 | MoSCoW | Kano hypothesis | Acceptance requirement |
| ---: | --- | ---: | ---: | ---: | ---: | ---: | --- | --- | --- |
| 1 | Require review of uncertain ink before calculation | 10 | 9 | 10 | 8 | 9.45 | Must | Must-be | Confident actual `1+1` remains automatic; uncertain output waits for exact-input acknowledgment or typed correction; function wrapping and mode changes cannot bypass review |
| 2 | Keep cancellation and background correction tied to the current drawing | 9 | 9 | 10 | 8 | 9.05 | Must | Must-be | Expensive reconstruction runs off the UI thread; new strokes, mode changes and newer edits reject stale publications; cancellation keeps ink and completed choices; work remains bounded |
| 3 | Provide targeted redraw, same-ink retry and typed fallback | 9 | 9 | 9 | 8 | 8.85 | Must | Performance | Guidance identifies an unclear group; retry retains explicit choices and reviewed text for unchanged ink; rejected grouping exposes no prefix answer; reopening and save behavior preserve intended state |
| 4 | Resolve avoidable root-search scanning with a mathematical proof | 9 | 9 | 9 | 7 | 8.70 | Must | Performance | A directed derivative enclosure proves strict monotonicity only for supported forms after continuity preflight; endpoint signs, bisection and residual checks retain precision and the five-second/evaluation budgets; unsupported cases fall back; independent bounds and final device default-operation checks pass |
| 5 | Offer explicit angle and perspective correction for photos | 9 | 8 | 8 | 7 | 8.25 | Should | Performance | Four corners and ±20° straightening act on retained source detail; reject crossed/collapsed/extreme selections; displayed pixels and OCR agree; reset/cancel/reopen preserve correct coordinates and draft; output remains bounded |
| 6 | Explain image limitations and the next useful action | 8 | 7 | 7 | 9 | 7.70 | Should | Performance | Low contrast/darkness/soft-edge advice is bounded and nonblocking; never call it a confidence score or blur diagnosis; offer crop, lighting, retake and typed entry without promising reconstruction of missing detail |
| — | Unrestricted cursive/page understanding or full CAS | 9 | 2 | 6 | 1 | 5.45 | Won't in this increment | Attractive | Requires separate model/grammar, licensing, resources and representative evaluation; templates and a small synthetic corpus cannot establish unrestricted support |

The failure-cost countercheck favors preserving intent over guessing an answer. An uncertain expression that happens to parse is still uncertain. Recognition review is scoped to an immutable input, while mathematical approval is scoped to established bounds. A successful root derivative proof can save work; an inconclusive proof uses the existing bounded path without loosening residual checks. The recorded desktop timings are scoped observations, not a general speed claim. Recognition-result caching is deferred because registers, Ans and history links require a complete environment key to prevent stale answers.

**Application validation passed.** The fresh forced offline Gate2 passed **1,521 host tests**, lint and assembly; current artifact audits and **19 packaged-API smoke checks** passed. The same exact app/test APKs passed **34/34 native checks on each of API 23 and API 34**, offline before and after. Combined device QA passed with **ten API 23 and nine API 34 manual observations**. These cover cold default roots after restart, retained/applied angle choices, photo save/restart, real drawn uncertain-ink review/retry/mode clearing, four-corner preview, crossed-corner refusal/cancel, low-contrast advice/treatment and explicit typed recovery from incomplete `12+`. Baseline OCR already read the geometric fixtures correctly; those checks establish displayed geometry and recovery behavior, not an accuracy increase.

Physical phones/styluses, TalkBack, a 16 KB-page runtime and representative recognition accuracy remain untested. A small desktop root comparison and emulator checks do not establish universal performance. Historical candidate failures remain intact in [VERIFICATION.md](VERIFICATION.md). General cursive/pages and full CAS remain outside scope.

The workspace delivery folder is `releases/Version 2.0`, with `CalcForAll - Version 2.0.apk`, `CalcForAll - Version 2.0 - Source.zip`, `CalcForAll - Version 2.0 - Local API.zip` and `CalcForAll - Version 2.0 - Verification.zip`. File identity, source/archive integrity and final file-scan results are established by separate release receipts; application validation does not imply those packaging checks have already passed.
