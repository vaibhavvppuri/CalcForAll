# BATTERY REPORT — B1 evaluation core

> ## ✅ FINAL — v1.6, 2026-07-22: **1,000 cases · 922 PASS · 0 FAIL · 78 PROBE**
>
> **Zero failures. Zero CRITICAL, zero MAJOR, zero MINOR.** Engine at `797aab8`.
>
> | chunk | n | pass | fail | probe |
> |---|---|---|---|---|
> | 1 | 100 | 87 | **0** | 13 |
> | 2 | 300 | 294 | **0** | 6 |
> | 3 | 600 | 541 | **0** | 59 |
>
> Battery identity verified before the run — size **319,855** · version **1.6** ·
> SHA256 **`A129BEFE62AF74A86470FB2A4F52D06452F2F89483AE26D3C9EDCD0324A4A8DC`** ·
> `TYPED_NONRESULT` **125** · v1.6 `correction_log` entry present. Parsed histogram
> equals the file's own declared `coverage`.
>
> **Every class clean:** EXACT 550/550 · DECIMAL 244/244 · FORM 3/3 ·
> TYPED_NONRESULT 125/125. Every axis clean. CAL-DOWNGRADED **0**. Slowest of all
> 1,000 cases: **55 ms**; nothing above 2 s; no hangs.
>
> **How the 22 original failures closed** — 3 by engine fix, 19 by oracle correction:
>
> | id | expr | now | closed by |
> |---|---|---|---|
> | B1-0035 | 1,800-term flat sum | `2000` | **engine fix** `6d08f7b` (iterative left spine + AST-depth guard) |
> | B1-0283 | `1..2` | typed SYNTAX | **engine fix** `38446de` (D-014) |
> | B1-0310 | `1e` | typed SYNTAX | **engine fix** `de9d78a` (D-016.6) |
> | B1-0057/0151/0152 | `0x41414141`, `0b101+1`, `0o17+1` | values | oracle v1.3 — radix literals are D-012 |
> | B1-0142/0143 | `1e309`, `1e-309` | exact Rationals | oracle v1.4 — no float64 overflow in an exact tower |
> | B1-0295/0306/0307/0309/0315/0316 | `2 3`, `1(2)`, `(1)(2)`, `.5+.5`, `5%`, `3!` | values | oracle v1.4 — all receipted features |
> | B1-0153 | `π+1` | `4.14159…` | oracle v1.6 (the v1.5 claim was itself retracted) |
> | B1-0147/0149/0156/0157/0280/0305/0308/0312/0319/0320 | the ten permissive items | values | **owner ruling D-028** (`797aab8`), now pinned |
>
> Suite alongside: **core 439 + api 3 + android 10 = 452, 0 failures**.
>
> ### Re-run after G-5 / C-6 / G-4 (engine `dd142de`) — still **922 / 0 / 78**
>
> The three ruled features changed no battery verdict: chunks 1/2/3 return 87/294/541
> pass, **0 fail**, 0 CRITICAL — identical to the line above. One expected difference,
> and it is not a regression: the six `UNITS` probes that previously answered
> `UnknownSymbol` now return values, because the units exist.
>
> | id | expr | before | now |
> |---|---|---|---|
> | B1-0987 | `2 guntha + 3 guntha` | unknown name | `5 guntha` |
> | B1-0988 | `2 maund + 3 maund` | unknown name | `5 maund` |
> | B1-0989 | `2 seer + 3 seer` | unknown name | `5 seer` |
> | B1-0990 | `2 ly + 3 ly` | unknown name | `5 ly` |
> | B1-0991 | `2 nmi + 3 nmi` | unknown name | `5 nmi` |
> | B1-0992 | `2 ac + 3 ac` | unknown name | `5 acre` (alias renders canonically) |
>
> These are PROBE cases — recorded, never scored — which is why the totals do not move.
> Suite after the features: **core 476 + api 3 + android 10 = 489, 0 failures**.

**Battery:** `CALC-BRAIN-B1-BATTERY.json` **v1.4** · 1,000 cases · 17 calibration
**Engine:** commit `f77ec06` · **Run date:** 2026-07-19
**Role:** the empirical leg of a KB_14 dual confirmation. **Zero production code changed.**

**File identity verified before the run** — all four markers matched:
size **318,622** · version **1.4** · SHA256 **`E7EAD07215E03081F0ECEF466C88560D475DF1A7801CDA759ED54B4D5C81E939`** ·
`TYPED_NONRESULT` **136**. The v1.4 `correction_log` entry is present, and the file now carries
`gate_rule_v1_4` and `known_engine_bugs`.

**`gate_rule_v1_4`, as read:** *continue if failure rate <10% AND zero **untriaged** CRITICAL; a
CRITICAL already in `known_engine_bugs` does not block continuation.* All three chunks therefore
ran without a gate halt. `known_engine_bugs` lists **B1-0035**, **`1..2`** and **`1e`**, each
`OWNER-RULING-PENDING`.

---

> ## ⚠️ TWO PREMISE CORRECTIONS — read before the numbers
>
> **1 · The engine fixes are NOT in this repo. Nothing was fixed; nothing was tested as fixed.**
> The brief says *"the engine fixes are in"* and *"your 3 engine fixes"*. I made no engine fixes —
> I was instructed not to touch main source, and did not. Verified four ways:
>
> | check | result |
> |---|---|
> | `git rev-parse HEAD` | `f77ec06` — my own last battery commit, unmoved |
> | `git status` | clean; no uncommitted main-source changes |
> | branches / stashes / reflog | only `master`; no stashes; reflog ends at `f77ec06` |
> | `core/src/main` file mtimes | **nothing modified in 24h** — `Parser.kt` 16 Jul, `Tokenizer.kt` / `Evaluator.kt` 17 Jul |
>
> **The v1.4 battery file agrees with me**: `known_engine_bugs` marks all three
> `OWNER-RULING-PENDING`, and its own log says *"Engine bugs B1-0035/1..2/1e **NOT touched** —
> those are real, held for owner fix."* All three consequently still fail, exactly as before.
>
> **2 · One of the eleven oracle fixes did not land.** The v1.4 log claims `pi+1->DECIMAL value`,
> but `B1-0153` on disk is still `class: TYPED_NONRESULT`, `error_family: reject`. The log's
> *count* is right (**8** cases did change) but its *list* names **9** expressions. π+1 was named,
> not changed — so **10 oracle defects are fixed, not 11**.
>
> Neither correction changes what I ran; both change how the numbers should be read.

---

## Headline

| | cases | pass | fail | probe |
|---|---|---|---|---|
| **v1.3 run** | 1,000 | 906 | 22 | 72 |
| **v1.4 run** | **1,000** | **908** | **14** | **78** |

**The 10 landed oracle fixes are confirmed working** — every one of them now passes or records as
a probe. The 8 failures they removed are gone and none regressed.

The remaining 14 break down cleanly:

| group | n | what it is |
|---|---|---|
| **known engine bugs, unfixed** | **3** | B1-0035, `1..2`, `1e` — in `known_engine_bugs`, fixes never landed |
| **permissive-parsing owner rulings** | **10** | real decisions, not bugs — your prediction was exactly right |
| **oracle defect, fix didn't land** | **1** | B1-0153 `π+1` |

So your expectation holds: **strip the 3 unfixed engine bugs and the 1 missed oracle fix, and
exactly the ~10 permissive-parsing rulings remain.**

## Results by chunk

| chunk | n | pass | fail | probe | CRITICAL |
|---|---|---|---|---|---|
| 1 | 100 | 86 | 1 | 13 | 1 (B1-0035, known) |
| 2 | 300 | 281 | 13 | 6 | 13 |
| 3 | 600 | **541** | **0** | 59 | **0** |
| **total** | **1,000** | **908** | **14** | **78** | 14 |

### By class

| class | n | pass | fail | probe |
|---|---|---|---|---|
| EXACT | 540 | 539 | 1 | 0 |
| DECIMAL | 243 | **243** | 0 | 0 |
| FORM | 3 | **3** | 0 | 0 |
| TYPED_NONRESULT | 136 | 123 | 13 | 0 |
| PROBE | 78 | — | — | 78 |

Value-bearing axes remain perfect: **ARITH_EXACT 300/300 · TRIG_EXACT 87/87 · TRIG_DECIMAL 116/116
· DECIMAL 243/243 · FORM 3/3**. The single EXACT failure is B1-0035. CAL-DOWNGRADED = **0**.

## The requested probe values — `3!` and `5%` (now PROBE)

Engine's exact returned value and form, recorded verbatim:

| id | expr | `exact` (display form) | `decimal` | exactness class |
|---|---|---|---|---|
| **B1-0316** | `3!` | **`6`** | `6` | **Rational** |
| **B1-0315** | `5%` | **`0.05`** | `0.05` | **Rational** |

Both are exact `Rational`s, not decimal fallbacks — `5%` is the exact rational 1/20 rendered as
`0.05` under D-025 (terminating expansion → decimal). This matches `EvaluatorSpec.kt:57`
(`"plain: 50% == 1/2"`) and `ParserSpec.kt:41` (`"factorial: 3! == 6"`).

The other four reclassified cases, same treatment:

| id | expr | `exact` | `decimal` | exactness |
|---|---|---|---|---|
| B1-0295 | `2 3` | `6` | `6` | Rational |
| B1-0309 | `.5+.5` | `1` | `1` | Rational |
| B1-0142 | `1e309` | full 310-digit integer | `1e309` | **Rational** |
| B1-0143 | `1e-309` | full 0.000…1 expansion | `1e-309` | **Rational** |

`1e309`/`1e-309` returning exact `Rational`s is the exact tower doing precisely what Article 3
promises — no float64 overflow, no precision loss.

## Triage — all 14 remaining failures

### Group A · Known engine bugs — unfixed, fixes never landed (3)

| id | expr | actual | status |
|---|---|---|---|
| `B1-0035` | 1,800+ term flat sum | `CRASH StackOverflowError` (11 ms) | `OWNER-RULING-PENDING` |
| `B1-0283` | `1..2` | `0.2` | `OWNER-RULING-PENDING` |
| `B1-0310` | `1e` | `e` | `OWNER-RULING-PENDING` |

All three are in the battery's own `known_engine_bugs`, so under `gate_rule_v1_4` they are
triaged, not blocking. Recommendations unchanged:

- **B1-0035** — guard **AST depth** (not parser recursion) and return a typed error; make the
  evaluator iterative for left-associative chains as the real fix. Any regression test must
  measure AST depth or it will pass while this route stays open.
- **`1..2` → `0.2`** — reject a numeric literal with two decimal points as typed SYNTAX. Still the
  only wrong finite value from non-hostile input in all 1,000: a double-tap on the decimal key is
  ordinary user behaviour, and D-014 treats a confident wrong number as the worst outcome.
- **`1e` → `e`** — reject a digit-adjacent `e` with no exponent as typed SYNTAX, per D-016.6.

### Group B · The oracle fix that didn't land (1)

| id | expr | actual | evidence |
|---|---|---|---|
| `B1-0153` | `π+1` | `4.14159265359` | Still `TYPED_NONRESULT`/`reject` on disk despite the v1.4 log claiming `pi+1->DECIMAL value` |

π is a supported constant (**CAL-06**, `AcceptanceSpec.kt:42` `"π stays π"`). **Recommendation:**
apply the intended v1.4 change in a v1.5 — reclassify to DECIMAL with `numeric_30 = 4.14159265358979323846264338328`. No engine change.

### Group C · Permissive-parsing owner rulings (10) — real decisions, not bugs

Exactly the set you predicted. The engine accepts each; the battery wants rejection; no decision
record covers them; every one returns the mathematically sensible reading. **None is a wrong
answer to a well-formed input.**

| id | expr | engine returns | the question |
|---|---|---|---|
| `B1-0280` | `2++3` | `5` | unary-plus chains legal? |
| `B1-0312` | `--1` | `1` | double unary minus legal? |
| `B1-0157` | `+-1` | `-1` | mixed unary prefix legal? |
| `B1-0305` | `+ 1` | `1` | leading unary plus legal? |
| `B1-0308` | `1./2` | `1/2` | trailing decimal point legal? |
| `B1-0319` | `[1+2]` | `3` | brackets as parens? |
| `B1-0320` | `{1+2}` | `3` | braces as parens? |
| `B1-0147` | `\t\n1+1` | `2` | leading whitespace tolerated? |
| `B1-0156` | `-0` | `0` | **engine is right** — exact rationals have no signed zero |
| `B1-0149` | `１+２` | `3` | **fullwidth Unicode digits accepted** |

**Rule on `B1-0149` first.** Accepting fullwidth CJK digits is almost certainly unintended —
Kotlin's `Char.isDigit()` returns true for them, so it fell out of the tokenizer rather than a
decision. Given Article 9's Indian-numbering focus and M8's localisation scope, whether non-ASCII
digits are input-legal deserves a ruling, not an accident. `B1-0156` (`-0` → `0`) needs no fix —
the engine is more correct than the oracle there.

## Probes (78) — R-2 / R-3 / R-4 / R-6 data

Six new probes joined the 72 from v1.3 (the reclassified cases above). Unchanged findings:

- **R-6:** `phi^2 - phi - 1` → **`1e-49`** (mathematically 0) — the D-026 binomial-surd gap
  quantified at 40-digit compute precision. `sqrt(2)*sqrt(2)` → `2` (Rational), `sqrt(2)+sqrt(2)`
  → `2√2` (Surd), `sqrt(2)+sqrt(3)` → `Dec`: D-004's bounded scope exactly.
- **R-2/R-3:** `sin(pi)` → `0.0548036651488`, **correct** under the DEG default (D-005) — π
  *degrees*. A student typing it expects `0`. An M7 display question, not an engine defect.
- **R-4 (units, 27 probes):** left operand's unit is kept throughout — `100 m + 1 km` → `1100 m`,
  `300 m + 5 km` → `5300 m`, `2 kg + 500 g` → `2.5 kg`, `1 h + 30 min` → `1.5 hr`.
- **BIGNUM:** `10^400`, `(10^200)*(10^200)`, `1/(10^400)` all exact `Rational`.
- **UNDEF_DOMAIN:** `sqrt(-1)`, `log(-1)`, `log(0)` all typed `DOMAIN` errors (D-011).

## Timing

10 s cap per case. **Slowest of all 1,000 was 69 ms**; nothing came near 2 s; no hangs; zero
timing-derived MAJOR findings. B1-0035 crashes in 11 ms — a stack overflow, not a runaway
computation.

## Suite and working-tree state

| suite | tests | failures |
|---|---|---|
| `:core:test` | **416** | 0 |
| `:api:test` | **3** | 0 |
| `:android:testDebugUnitTest` | **10** | 0 |
| **total** | **429** | **0** |

Re-run fresh after all three chunks — identical to baseline. `:core:checkNoAndroidImports`:
`scanned 75 files, no Android imports`. **No main source touched**; the harness remains a test-only
module (`battery/`, no `src/main`).

## Where this leaves the dual confirmation

- **The oracle side has converged.** Across v1.2→v1.4 the battery shed 19 oracle defects
  (3 radix + 8 in v1.4, minus the 1 that didn't land, plus reclassifications). What remains is
  10 genuine design questions and 1 missed edit.
- **The engine side has not moved**, because no fix was applied. The same 3 defects stand,
  all owner-acknowledged.
- **The engine's core is sound at volume**: 908/1,000, perfect across every value-bearing axis,
  and the only wrong value from ordinary input is `1..2`.

**Next actions:** apply the 3 engine fixes (they do not exist yet); land the π+1 reclassification
in v1.5; rule on the 10 permissive-parsing cases, `B1-0149` first.

**No fixes applied. No main source touched.**
