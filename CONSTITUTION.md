# CONSTITUTION

**Package:** `com.vaibhav.calc` — permanent only once **published** to Play, not at M0. Display name deferred to **M7** by owner decision (see DECISIONS.md, amendment A4). The former "rename before M0 ships" rule is deleted.

This file is written before line one of code. It is the set of rules the build may not break. Claude Code reads this first, every session.

Amendments require an explicit decision logged in `DECISIONS.md`. Nothing here changes silently.

---

## Article 1 — The module boundary

```
calc/
├── core/      pure Kotlin. Parser, evaluator, CAS, units, solver.
├── android/   the app. Depends on core.
└── api/       Ktor server. Depends on core.
```

**`core/` must contain zero Android imports. Ever.** No `android.*`, no `androidx.*`, no `Context`, no `Resources`, no Android string resources.

If a class needs Android, it does not belong in `core`.

**Enforcement is mechanical, not moral.** CI builds `core` as a plain JVM module with no Android SDK on the classpath. If someone puts an Android import in `core`, the build fails. The boundary is not a guideline anyone has to remember — it's a wall that falls over on contact.

**Why this article exists first:** the app and the API are the same engine. If `core` picks up an Android dependency, the API stops being a wrapper and becomes a rewrite. That happens quietly, around month three, and the cost lands in v4. This article is the whole reason the API is nearly free.

## Article 2 — Scope lock

The **51 features in `calculator-v1-spec.md` are the complete scope of v1.** That file is the contract.

Not reasons to build something outside it:
- "It was easy"
- "I was already in that file"
- "It's only 20 lines"
- "It would be weird not to have it"

Anything outside the 51 goes in `BACKLOG.md` as one line. It does not get built, prototyped, stubbed, or scaffolded.

The `Won't` column in the v1 spec is **deleted, not deferred.** It exists so those ideas stop returning at milestone four wearing a disguise.

> **Amendment 2026-07-16 (A1):** Implementation dependencies are NOT Article-2 scope. A library needed to satisfy another article (e.g. big-math for BigDecimal transcendentals) is *how* the constitution is met, not a user-facing feature. Article 2 governs user-facing features only.

## Article 3 — Correctness (the calculator-specific one)

**The engine is exact-first.**

`0.1 + 0.2` returns `0.3`. Not `0.30000000000000004`. A calculator that fails this fails at being a calculator, and no feature above it can compensate.

- `core` stores numbers as exact rationals or `BigDecimal`. Never `Double` for storage.
- `Double` is an **output format**, not a data type. It appears at the display boundary and nowhere else.
- Exact forms survive: `√8` stays `2√2` until asked to decimalise. `1/3` stays `1/3`.
- Precision and rounding are explicit settings, never accidents of representation.

## Article 4 — Test standard

- **Property tests** on every engine function, not just examples. `∀ a,b: a+b == b+a` catches what three hand-picked cases never will.
- **Golden tests** on all formatting and display output.
- **A milestone is not done if tests are red.** Not "done with a known issue." Not done.
- Coverage is not a target. Untested arithmetic is the target — there is none of it.

> **Amendment 2026-07-16 (A3):** Every milestone also requires an **adversarial fuzz pass** whose alphabet is derived **mechanically** from the tokenizer's own accepted-character set (never a hand-written list), run until no new crash class appears. A hand-picked alphabet is itself the bug.

## Article 5 — Legal (non-removable)

Ships in v1, regardless of score, schedule, or preference — mine or Vaibhav's:

- Privacy policy, publicly hosted, linked in-app and in the Play listing
- Play Data Safety declaration, accurate to actual behaviour
- Camera permission rationale shown before the prompt, not after
- Third-party licence attribution screen
- A working data-deletion path

Photo-solve sends user images off-device. That is what makes these mandatory rather than nice. This article cannot be amended.

## Article 6 — Definition of done

A milestone is done when **all** of these are true:

1. Code written and building
2. Tests green — including new property tests for anything added
3. `PROGRESS.md` updated: what shipped, what changed, what's next, what's blocked
4. `DECISIONS.md` updated if any judgment call was made
5. Fresh zip produced for review

Four out of five is zero out of five.

> **Amendment 2026-07-16 (A2):** "4 of 5 is 0 of 5" distinguishes DONE from NOT DONE; it does **not** apply to a condition that cannot be executed in the current environment (e.g. the Android APK build, an ML Kit device run). Such a condition is met by correct config **plus an explicit honest flag** naming what was verified by config vs. execution. Faking execution to satisfy a checkbox is never allowed.

## Article 7 — Review protocol

After every milestone: **fresh zip + named screenshot.** Review runs from `git log`, the test output, and `PROGRESS.md` — not from a summary of what happened.

This exists because review throughput is the actual bottleneck on this project, not code generation. A 15-minute review that reads real artifacts beats a 2-hour one that reads prose.

## Article 8 — Model routing

| Work | Model | Effort |
|---|---|---|
| Main build — all v1 | Opus 4.8 | `/effort ultracode` |
| Handwriting / ML modules (v2+) | Fable 5 | `/effort max` |
| Everything else | Opus 4.8 | Max |

`ultracode` and `max` are **session-only** — they die when the session ends. Retype them.

**Switching model resets effort to that model's default.** After any switch to Fable 5, retype `/effort max` or you're silently running lower than you think.

## Article 9 — What this project is

v1 is: **everything a Casio fx-991 does, plus graphing, plus Indian numbering, on a phone.**

When a decision is genuinely ambiguous and no article above settles it, that sentence settles it. If a change doesn't serve that sentence, it isn't a v1 change.

## Article 10 — The milestone map

*Added by amendment A5 (2026-07-16). The 51-feature spec said WHAT ships; it never said in WHAT ORDER. This closes that gap.*

| Milestone | Scope | Module |
|---|---|---|
| M0 | Skeleton, boundary wall, CI, ML Kit spike | infra |
| M1 | D1 + D3 — the exact-first engine | `core` |
| M2 | D2 + D5 — base conversion, complex, equation solvers | `core` |
| M3 | D7 + D8 — matrices, statistics, regression | `core` |
| M4 | D9 — units + dimensional analysis, constants, Indian units | `core` |
| D-016 | The invocation layer — equations & units inline, router | `core` |
| **M5** | **D10 — the graphing core (the wedge)** | `core` |
| M6 | D11 + D12 — input & session core (models, not views) | `core` |
| M7 | Android UI (Compose) — the first Android milestone | `android` |
| M8 | D14 — platform, legal, accessibility, localisation, Play prep | `android` |

**The ordering rule — this is the point of the map.** Every `core/` milestone
finishes before the first Android milestone starts. **This is Article 1 as a
schedule, not just as a build rule.** If the whole v1 engine is provably complete
and Android-free, the app becomes a *view over a finished engine* rather than a
place where logic quietly leaks.

**Why M5 is the graphing core:**
- **It is the wedge.** The fx-991 cannot graph. At all. Everything else in v1 is
  parity; this is the only thing that beats the incumbent rather than matching it.
- It is `core/` only — function sampling, root & intersection finding, trace,
  viewport/axes/range math. No UI, no Android, no device.
- D-016 just made the engine modeless enough to feed it.
- The finished UI design makes the grid→axes reveal its signature moment; the
  graph engine must exist before anything can animate into it.

**M8 carries Article 5.** The legal set (privacy policy, Data Safety, permission
rationale, licence attribution, data deletion) lands at M8 and is non-removable.
Indian digit grouping (spec 14.4) is scheduled here, not parked.

## Article 11 — Silent failure is a critical bug

*Added by amendment A6 (2026-07-16).*

**No error handler may convert a FAILURE into a NON-RESULT.** Every harness,
workflow, aggregate and summary must distinguish **CHECKED-AND-PASSED** from
**NEVER-CHECKED**, structurally, and report the second as loudly as a failure.

**A count that cannot tell them apart is a lie with a number on it.**

Why this outranks the bugs it was looking for: a review harness reported
`confirmed = 1 of 10` while nine findings had never been checked at all — their
verifier agents had died, and a `.catch` mapped each dead agent to `null`, which
the aggregate then filtered out. The report looked clean. **All nine were real.**
That defect is worse than the OOM it was hunting, because it made nine real bugs
invisible.

`null` / empty / zero is a legitimate ANSWER only when it means *"the question has
no answer"*. It is never acceptable when it means *"we failed to ask"*.

**Enforcement:** audit every `catch`, every aggregate, and every summary line for
this shape. A failure path must produce a value that a caller cannot mistake for
a clean negative.

## Article 12 — One rule, one implementation

*Added by amendment A7 (2026-07-16).*

If two components need the same rule, they **call the same function.** Not "the
same logic" — the same code. **Duplicated logic is drift waiting for a release.**

Three of the four serious D-016 findings were this exact shape: contextual percent
lived in both the evaluator and the extractor; unit promotion lived in both the
arithmetic and the comparison. Both pairs drifted. Both produced *confident wrong
answers* — `x + 10% = 55` → 54.9, and `5 lakh = 500000` → false.

**Enforcement — round-trip property tests.** Every value the engine renders must
parse back to the same value. *"The engine could not re-read its own `15 m²`
output"* is precisely the drift this catches. Every renderer/parser pair carries a
round-trip test.

## Article 13 — Review agents are read-only

*Added by amendment A8 (2026-07-16).*

A review is a read-only task and gets **read-only agents**. Review agents may not
write to the repository. **Never `git add -A` while any agent is running** — stage
explicit paths only. A commit described as doc-only once swept in three scratch
spec files and Kotlin daemon droppings because agents were writing to the tree as
it was made.

**Manual empirical verification is the default method** (owner ruling): write tests
that assert the correct behaviour and run them — whatever fails is real. On this
build that method confirmed **10 of 10** findings where a 15-agent harness confirmed
**1 of 10**. It is not a fallback; it is the method that worked. Agent review
requires explicit owner approval.
