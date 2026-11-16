# DECISIONS

## Current owner direction — 10–11 November 2026

The owner's current instructions authorize autonomous implementation, delegate naming/license/priorities, require free offline operation without paid APIs or image uploads, and request a local source handoff for their later GitHub upload. These supersede approval handshakes and deferred-milestone assumptions in the older discussions. [PRODUCT-PLAN.md](docs/PRODUCT-PLAN.md) records the weighted naming decision (**CalcForAll**) and DPCC-informed priorities; [VERIFICATION.md](docs/VERIFICATION.md) records evidence rather than treating historical milestones as proof of completion.

Original code uses MIT; third-party SDKs, models, fonts and libraries retain their terms. The minimum Android target is API 23, with tested versions distinguished from the compatibility target. The local API binds to loopback by default and is optional; the phone app does not require it. Recognition uses a bundled Latin OCR model plus an experimental geometry-based single-symbol pad, with review before insertion. Full handwritten layout and general photo solving are not claimed. Symbolic/calculus support is explicitly bounded, carries domain conditions, and distinguishes exact answers from estimates. The technical distribution is named **1.0.0-preview** instead of equating the historical chat titles “v2/v3/v4” with independently finished releases.

The following decision history is retained for context.

Judgment calls made during the build. Per Constitution Article 6, any decision
that wasn't dictated by the brief is logged here with its reasoning.

---

## Constitutional amendments — adopted 2026-07-16 (owner)

Logged per Article 6; the Constitution text is updated to match.

**A1 — Article 2 (scope).** Implementation dependencies are NOT Article-2 scope. A
library needed to satisfy an article (e.g. big-math for BigDecimal transcendentals,
D-003) is *how* the constitution is met, not a feature. Article 2 governs
USER-FACING features only. **Closed — not re-litigated at any milestone.**

**A2 — Article 6 (done).** "4 of 5 is 0 of 5" separates DONE from NOT DONE; it does
NOT apply to NOT-VERIFIABLE-IN-THIS-ENVIRONMENT. A condition that cannot execute
here (the Android APK build; the ML Kit device run) is met by correct config plus
an explicit honest flag naming what was verified by config vs. execution. Flagging
that is compliance. **Faking execution to satisfy a checkbox is never allowed.**

**A3 — Article 4 (hardened).** Every milestone requires: (1) property tests, (2)
golden tests, (3) an ADVERSARIAL FUZZ PASS whose alphabet is derived MECHANICALLY
from the tokenizer's own accepted-character set (never hand-written), and (4)
fuzzing until no new crash class appears. Retro-applied to M1 — `PropertySpec`
sources `Tokenizer.ACCEPTED_CHARACTERS`.

**A4 — Name (top-matter deleted).** The package id is permanent only once PUBLISHED
to Play, not at M0. Package is **`com.vaibhav.calc`** and stays so until
publication; display name is deferred to **M7** by owner decision. Naming is CLOSED,
not raised again before M7.

**A5 — The milestone map (new Article 10).** The spec said *what* ships, never in
*what order* — the same class of gap D-016 closed for invocation. The map:
M5 = D10 (graphing core, the wedge) · M6 = D11+D12 (input & session core, models
only) · M7 = Android UI in Compose (first Android milestone) · M8 = D14 (platform,
legal, accessibility, localisation, Play prep; Article 5 lands here, non-removable;
Indian digit grouping moves out of BACKLOG to here).

> **The ordering is the substance, not the schedule.** Every `core/` milestone
> finishes before the first Android milestone starts — **Article 1 expressed as a
> schedule rather than only as a build rule**. A provably complete, Android-free
> engine makes the app a *view over a finished engine* instead of a place where
> logic quietly leaks. The boundary wall stops leaks at compile time; this stops
> them at planning time.

**A6 — Article 11: silent failure is a critical bug.** No error handler may turn a
FAILURE into a NON-RESULT; CHECKED-AND-PASSED and NEVER-CHECKED must be
structurally distinct, and the second reported as loudly as a failure. Born from
the review harness reporting `confirmed = 1 of 10` while nine findings were never
checked (dead agents → `.catch` → `null` → filtered out). All nine were real. **A
count that cannot tell them apart is a lie with a number on it.** Enforcement: audit
every `catch`, aggregate and summary for the shape.

**A7 — Article 12: one rule, one implementation.** If two components need the same
rule they call the same function — not the same logic, the same code. Three of the
four serious D-016 findings were duplicated-rule drift (percent: evaluator vs
extractor; unit promotion: arithmetic vs comparison), and both drifts produced
confident wrong answers. Enforcement: **round-trip property tests** on every
renderer/parser pair — the engine must be able to re-read everything it renders.

**A8 — Article 13: review agents are read-only.** Ratified from this build's own
root cause. Never `git add -A` while agents run; stage explicit paths. **Manual
empirical verification is now the default method** — it scored 10/10 where a
15-agent harness scored 1/10. Agent review needs explicit owner approval.

---

## D-000 · Package + name — CLOSED (amendment A4)

**Decision (final):** Package id is **`com.vaibhav.calc`**; Kotlin modules are
`com.vaibhav.calc.core` / `.api` / `.android`; Android `applicationId =
"com.vaibhav.calc"`. Display name deferred to **M7**. The Constitution's "rename
before M0 ships" top-matter is deleted. Not revisited before M7.

---

## D-001 · `core` targets JVM 17 bytecode while building on JDK 21

**Decision:** `core` and `api` compile with the installed JDK 21 but emit Java 17
bytecode (`jvmTarget = JVM_17`, `sourceCompatibility = 17`).

**Why:** 17 is what AGP 8 consumes cleanly for the Android app, and it avoids a
Gradle *toolchain* download (only JDK 21 is installed locally; asking for a 17
toolchain would trigger provisioning). Same source, Android-safe output.

---

## D-002 · `:android` is a *conditional* Gradle include

**Decision:** `settings.gradle.kts` includes `:android` **only when an Android
SDK is present** (`ANDROID_HOME`/`ANDROID_SDK_ROOT`, or a `local.properties` with
`sdk.dir`). `:core` and `:api` are always included.

**Why:** This makes Article 1 structural rather than merely enforced. On a machine
or CI job with no SDK, the Android module isn't even configured, so `core`/`api`
build with zero Android anything on the classpath. It also lets the boundary-wall
CI job run without an SDK by construction. Trade-off: a local build without an SDK
genuinely cannot build the app — which matches the chosen approach ("core+api
real locally, android via CI").

---

## D-003 · `ch.obermuhlner:big-math` for BigDecimal transcendentals

**Decision:** `core` depends on `ch.obermuhlner:big-math:2.3.2` (pure JVM, no
Android) for the BigDecimal kernels of `exp/ln/log/sin/cos/tan/asin.../sinh...
/pow/root/sqrt/pi/e/atan2`.

**Why:** Article 3 forbids `Double` in computation, so `Math.sin(...)` and
`kotlin.math` are off the table — trig/log must be done in `BigDecimal`.
Hand-rolling correct, well-tested transcendental series is a large bug surface;
big-math is reputable, widely used, and pure-JVM (keeps Article 1 intact). The
*engine* — number tower, tokenizer, parser, evaluator, surd/rational algebra,
formatting — is all hand-written; big-math only supplies the decimal kernels.
This is an implementation dependency, **not** a v1 feature (Article 2 clear).

---

## D-004 · Exact-form scope: single surds + rational multiples of π/e — NOT a CAS

**Decision:** The exact number tower is `Rational | Surd(c·√r) | SymConst(c·π | c·e)
| Dec`. Exactness is preserved for these closed forms and their in-family
operations; anything that escapes them (e.g. `1 + √2`, `sin(1)`) falls back to
`Dec` (a finite-precision `BigDecimal`, never `Double`).

**Why:** M1.1 and Article 3 require `√8 → 2√2` and `π stays π` — a *bounded*
symbolic capability. A general symbolic sum of unlike terms with like-term
collection is CAS machinery, which Article 2 explicitly defers to v2. This scope
passes every stated acceptance test while staying firmly on the non-CAS side of
the line.

---

## D-005 · Default angle mode is DEG

**Decision:** `Settings.angle` defaults to `DEG`.

**Why:** The project's north star (Article 9) is "everything a Casio fx-991 does."
A Casio boots in Degree mode, so `sin(30) = 0.5` out of the box matches user
expectation. `RAD` and `GRAD` are first-class and selectable. (Property tests for
trig identities set `RAD` explicitly.)

---

## D-006 · `0^0 = 1`

**Decision:** `0^0` evaluates to `1` (documented, not an accident).

**Why:** It's the convention most useful for a calculator (binomial/series
contexts) and matches Kotlin/Java `pow` for integer 0. Some calculators error
instead; if the reviewer prefers a domain error, it's a one-line change in
`NumMath.intPow`. Flagged rather than hidden.

---

## D-007 · `%` is percent; modulo is `mod(a,b)`

**Decision:** The `%` token is Casio-style **percent** (contextual: `a+b%`,
`a-b%`, `a*b%`, `a/b%`, standalone `b%`), never the C-style modulo operator.
Modulo is the function `mod(a, b)` (floored; result carries the divisor's sign).

**Why:** Percent is a spec 1.6 requirement and the Casio behavior; overloading
`%` as modulo would collide with it. Documented so `5 % 3` isn't mistaken for
`2`.

---

## D-008 · Root argument order: `root(x, n)` = the n-th root of x

**Decision:** `root(x, n)` returns the n-th root of `x` (value first, index
second). `sqrt(x)`, `cbrt(x)`, and `√x` are the common shortcuts.

**Why:** A convention had to be chosen; value-then-index reads naturally next to
`pow(x, n)`. Documented to avoid `root(27, 3)` vs `root(3, 27)` confusion.

---

## D-009 · Complex via a `Value` layer (M2)

**Decision:** The evaluator produces a `Value` = `RealValue(Num) | ComplexValue(Complex)`.
Real is the common path and delegates straight to the M1 `Num` tower (nothing
about M1 changes). A complex whose imaginary part is exactly zero demotes to real,
so `i*i` is the real `-1`. Matrices/quantities are NOT folded into this expression
value — they get their own APIs (see M3/M4).

**Why:** `i` must flow through `+`/`*` in expressions, so the evaluator has to be
value-aware. A bounded two-case `Value` does that without destabilising the exact
real tower.

## D-010 · Solver root exactness + typed API (M2)

**Decision:** Linear/quadratic/systems solutions stay exact where the tower allows:
rational (systems, repeated roots), exact surds (`x²−2 → ±√2`), or complex with an
exact rational real part. An irrational root that is a *sum* of rational + surd
(e.g. `(−3+√5)/2`) falls back to decimal — consistent with D-004, still not a CAS.
Solvers are a typed `CalcResult` API (not inline scalar functions, since they
return multiple roots / vectors).

## D-011 · `i` reserved; `complexEnabled` gates sqrt-of-negative (M2)

**Decision:** `i` always resolves to the imaginary unit (it shadows any variable
named `i`). Real functions extend to complex only when `Settings.complexEnabled`
is true (default false), so `sqrt(-4)` is a DOMAIN error by default and `2i` with
complex mode on — preserving M1 behaviour unless explicitly opted in.

## D-012 · Base conversion on integers only (M2)

**Decision:** Base conversion (radix 2–36) is defined on integers: `0x`/`0b`/`0o`
literals in expressions, and `toBase`/`fromBase` for arbitrary radix. Non-integers
have no single canonical base form, so they're rejected.

---

## D-013 · Matrices/stats are exact-rational and a typed API (M3)

**Decision:** `Matrix` stores exact `Num`s and does determinant (Gaussian),
inverse (Gauss–Jordan) and rank (RREF) with rational pivots — no `Double`, and
because the field is exact **no numerical pivoting strategy is needed**. Stats
sums/means/variances/regression coefficients stay exact; only √-based quantities
(stdev, Pearson r) decimalise. Matrices and stats are typed `CalcResult` APIs
(like the solvers), not inline expression values — matrix/stats "mode" is separate
on a Casio too, and folding matrix literals into the expression grammar is a
bigger parser change parked for later.

---

## D-014 · Unit engine: dimensional analysis + affine temperature (M4)

**Decision:** Units are an affine map to base SI (`base = value·factor + offset`),
with a 7-exponent `Dimension`. Conversion factors are exact rationals. Add/sub/
mul/div enforce dimensional analysis. **Affine units (°C/°F) support conversion
only** — arithmetic on them is refused, because "20°C + 5°C" is ambiguous
(temperature vs. temperature-difference); guessing would be worse than erroring.
Like matrices/solvers, units are a typed API, not inline expression syntax (that
sugar — `5 km + 300 m` in one box — is in BACKLOG).

**bigha is region-dependent.** No single legal value exists; the engine uses the
pucca/standard `bigha = 2529.2851 m²` and this is documented as configurable
later (BACKLOG). `lakh`/`crore` are modelled as dimensionless ×10⁵/×10⁷ units.

## D-015 · Constants: post-2019-SI exactness (M4)

**Decision:** Constants defined exactly by the 2019 SI redefinition (c, h, e, k,
Nₐ, and derived R, g₀) carry zero uncertainty; measured constants (G, mₑ, mₚ)
carry their CODATA standard uncertainty. Values are stored as exact `Num`s.

---

## D-016 · The invocation layer — equations & units go inline; a router, not a mode

**The gap this closes.** The 51-feature list said *which* features exist, never
*how* they are invoked. The engine could solve a quadratic only via
`solveQuadratic(a, b, c)` — three fields. That is a MODE, and the UI commits to
contextual disclosure with zero modes.

**The rule that resolves it:** *a mode is a FAILURE when the input has a natural
text form; a mode is CORRECT when the input has a natural structured form.*
- Equations → **inline** (required; this is the thesis).
- Unit expressions (`5 km + 300 m`) → **inline** (natural to type).
- Matrices → **stay a typed API.** A grid beats typing `[[1,2],[3,4]]` on a phone.
- Statistics → **stay a typed API.** Same reasoning.

This is an invocation layer, not a feature expansion. The typed APIs do not change;
the router only routes.

### D-016.1 · `=` is equation-only; assignment is `→` (Article 9 / Casio parity)

`x = 5` is not disambiguated — the ambiguity is **removed**. `=` always means
equation; storing is a distinct gesture: `5 → A` (aliases `->`, `sto`; the keypad
emits `→`). Rejected: reserved-name assignment (Desmos) — breaks spec 12.3 and
hides a *letter* mode; `=` vs `==` — programmer syntax, and a mode in disguise
since the user must pre-declare intent. Rejected absolutely: "assignment if
undefined, equation if defined" — meaning that depends on invisible state.
**Invariant: the same keystrokes always mean the same thing.**

> *Amended 2026-08-02 (D-036.3).* The example here originally read `5 → x` — but
> `x` is an equation UNKNOWN and D-016.4 rightly refuses to assign it (the B2
> battery measured the contradiction, BRAIN-RUN-REPORT F-3). The assignable names
> are the registers `A–F, M`; the engine was always right, the example drifted.

### D-016.2 · An equation with ZERO unknowns is a truth verdict

`2+2=4` → `Verdict(true)`. Never "underdetermined" — a child checking arithmetic
and a student asserting an identity want the same answer from the same input.

### D-016.3 · NEWLINE is the canonical system separator

Two equations on two lines is how a human writes a system on paper, it matches the
2D natural-entry editor, and it needs no key that a calculator keypad lacks. `;`
is a tolerated alias (it costs nothing); newline is canonical.

### D-016.4 · Reserved names + a register set (hard constraint)

Units go inline, so `km` cannot also be a variable. Identifier namespaces are
therefore partitioned, **statically** — never by stored state:
- **Unknowns:** `x`, `y`, `z` — reserved; cannot be assigned.
- **Registers:** `A`–`F`, `M` — storable, and they substitute *everywhere*,
  including inside equations (so `3 → A`, then `2A + x = 10` gives `x = 4`).
- **Units** (`km`, `m`, `s`, `min`, …) and **constants** (`pi`, `e`, `i`, `tau`):
  reserved.
Assigning to a reserved name returns a typed error naming the collision.

> **Deviation from the owner's initial ruling — surfaced, and CONFIRMED by the
> owner (2026-07-16).** The initial ruling was registers `A–F, X, Y, M`. `X`/`Y`
> are omitted because they collide with `x`/`y` as equation unknowns, and the
> collision is unresolvable statically: if `x` is a register, then inside an
> equation it is either an unknown (which also makes `A` an unknown, so
> `2A + x = 10` becomes underdetermined) or substituted-when-stored (hidden state
> — banned by D-016.1). **`X`/`Y` are registers on the fx-991 *because* equations
> are a mode there; deleting the mode deletes the reason.** A–F and M preserve the
> muscle memory that survives. Both sets are single lists in `ReservedNames`.

### D-016.5 · `Ambiguous` is a peer of `Ok` and `Err` — ambiguity is a state

The router returns `Ok | Ambiguous(candidates) | Err`. D-014's refusal principle
generalises: **never guess**, because a plausible-looking wrong number is the worst
outcome. But the remedy differs — D-014 errors because *no correct answer exists*;
router ambiguity has a correct answer we simply don't know, so erroring would
discard recoverable information. Priority: (1) design the ambiguity out of the
grammar, (2) show both readings, (3) ask — a transient chip, not a mode (it holds
no state and cannot change how the next input is read).

`Ambiguous` lives on the **router's** result type, not on `CalcResult`: the typed
APIs can never produce it, and a type that can express a state its producer cannot
produce is a type that lies.

### D-016.6 · Permanently ambiguous inputs — resolved by fixed rule, never by state

A design constraint, not a bug list:

| Input | Collision | Fixed rule |
|---|---|---|
| `xy` | variable `xy` vs `x·y` | one identifier; write `x y` or `x*y` |
| `x(2)` | call vs `x·2` | `IDENT(` is **always** a call |
| `2e5` | scientific vs `2·e·5` | numeric-position `e` is always an exponent |
| `1/2x` | `(1/2)·x` vs `1/(2x)` | precedence fixed: `(1/2)·x` |
| `5 m` | metre vs variable | units are reserved (D-016.4) |

Underdetermined ≠ ambiguous: `x + y = 5` is known to be an equation; the **target**
is unknown. With ≥2 unknowns and one equation the router reports underdetermined
and **never silently picks a variable** (D-014 generalised). Its real home is
graphing (10.1).

---

## D-017 · Census correction — v1 is 53 features, not 51 (owner, 2026-07-16)

Two Kano **Basic** features were missing from the original census and are therefore
**auto-Must**:

| # | Feature |
|---|---|
| **1.9** | **Entry correction** — backspace at cursor, clear entry |
| **1.10** | **Undo / redo** |

**v1 spec is now 53 features; the census is 792 leaves** (was 51 / 780).
`calculator-v1-spec.md` is updated to match, since Article 2 names that file as the
contract and a stale contract is worse than none.

**Why they were missed (worth keeping):** they are *furniture*. Furniture sits below
the altitude at which products describe themselves — nobody writes "and it has a
backspace" — which is exactly why it goes uncensused and then unbuilt. Both land in
M6 (D11+D12).

## D-018 · M6 input & session rulings (owner — decided, not re-opened)

**CLEAR: there is no C key, no AC, no long-press-to-clear.**
`⌫` tap deletes one; **`⌫` hold repeat-deletes**. Hold-to-repeat is an *inherited
convention* (every text field on earth), not a hidden gesture. Long-press-to-clear is
**dangerous**: it detonates the user's expression against keyboard muscle memory.
Other calculators need a C key for one of three reasons — no backspace, no hold
gesture, or a hidden accumulator. We have none of the three. **"Clear the tape" is a
TAPE action, not a keypad key.**

**UNDO: not a key, not a gesture — a snackbar.** A destructive action (a tape edit
that recomputes dependents) shows `3 results updated · UNDO`. This is contextual
disclosure applied to undo: the control exists *because you did the thing that needs
it*, and is invisible otherwise.

**2D DELETE — the principle; the cases are derived from it, not enumerated by hand:**
> **Backspace inverts structure creation.** If typing `/` created the fraction, then
> backspace undoes the `/`: the fraction collapses and the surviving side stays
> inline.
> **Collapse never destroys content. Ever.** If either side has content, it survives
> the collapse.

See `PROGRESS.md` for the derived case table.

---

## D-019 · Rule 3 — destruction requires visibility (owner overrule of F5/F6)

**Rule 3: A structure may only be destroyed while it is visibly SELECTED.
Selection is the confirmation.**

Revised **F5/F6**: ⌫ at a *populated* structure boundary **selects the whole
structure**; a second ⌫ deletes it; `←`/any navigation deselects. Applies
identically to `Fraction`, `Power`, `Root`, `Paren`.

**Why the original F5/F6 ("exit left, no deletion") was wrong.** ⌫ is a destructive
key, so exit-left makes it a **dead keypress**. Trace `1+2/3` with the cursor at the
start of the numerator: ⌫ moves the cursor invisibly, nothing appears to happen, so
the user presses ⌫ again — **and deletes the `+`**. They pressed backspace twice
aiming *inside* the fraction and destroyed something *outside* it that they were not
looking at. Exit-left does not prevent unintended destruction; it **relocates it to
something the user isn't watching.**

Select-then-delete instead: the first press is **visible** (no dead keypress); the
user **sees what is about to be destroyed before it is**; deleting `(3x+4)/(2x-1)`
costs 2 keystrokes rather than 14; and **nothing dies unseen**.

**The axiom hole this closed.** Rules 1 and 2 govern **COLLAPSE** — remove the
structure, keep the content. There was no rule for **DELETE** — remove the structure
*and* its content. With no case for it, the derivation defaulted to "don't", which is
unsafe in the opposite direction. Rule 3 is that missing axiom.

**Selection is not new complexity.** 11.7 (clipboard) needs it regardless — a
sub-expression cannot be copied without it. Built once, used for both: a selection is
a half-open range in a row, and a selected structure is simply a range of length one.

## D-020 · Zero undo depth across a committed `=` — because `=` is not destructive

The editor's undo stack is scoped to the *uncommitted* entry; `=` commits and resets
it to depth 0.

**The reason is not blast radius, it is that nothing is lost.** Undo exists to recover
from loss. `=` loses nothing: the entry is on the tape, visible, one tap from reuse
(12.1). There is no loss to recover from, so there is nothing for undo to do.

Editing a *committed* entry IS destructive (dependents recompute) — and that is
exactly the action that raises the snackbar (`3 results updated · UNDO`, D-018). Two
mechanisms, cleanly separated by the commit boundary; one gesture never carries two
blast radii.

---

## D-021 · BLOCKED ≠ FAILED (Article 11, fifth instance)

`#3 = 1/0` **Failed** — it ran and it broke. `#5 = #3*2` did **not** fail: it never
ran. Its dependency is broken. Calling both "Failed" paints two entries red and
hides which one is actually wrong — the user has to work out the cause the tape
already knows.

`EntryValue = Computed | Empty | Stale | Failed | Blocked(by) | Cycle(members) | Ambiguous`.

`Blocked` **carries which entry blocked it**, so the UI can point at `#3` and grey
`#5`. *A Blocked entry is not broken — it is waiting.* Fix `#3` and `#5` recovers
with no action of its own (`M6TapeSpec` asserts exactly this).

**Implementation note.** The blocking test is "the dependency handed me no value",
not "the dependency failed" — so an entry referencing one whose result is *roots*
rather than a number is Blocked too, which is honest: `#5` never ran, because `#3`
gave it nothing to run on.

**Two states added beyond the ratified five, for the same reason:** `Cycle` (D-022)
and `Ambiguous`. Mapping `RouteResult.Ambiguous` onto `Empty` would have said
"nothing to compute" about an entry with *two* things to compute — Article 11's
exact shape, and inconsistent with D-016.5, where ambiguity is already a peer of
Ok and Err rather than a flavour of error.

## D-022 · Cycles are a state, not a failure

Recompute order is a topological sort. `#1 = #2+1` with `#2 = #1*2` is not "failed
to compute" — it is **there is no computation to do**. No order exists, so the
question has no answer rather than a broken one. Distinct state, distinct message,
naming the entries in the cycle (a cycle report without members is unactionable).

**Cycle detection falls out of the sort for free.** Kahn's algorithm drains every
node whose dependencies are resolved; whatever remains cannot be ordered, and that
remainder *is* the cycle. It is obtained by doing the sort correctly, not by adding
a check — so there is no separate detector to fall out of step with the sort.

Entries outside the cycle are unaffected and still compute; breaking the cycle
restores computation with no further action. Both asserted.

## D-023 · The undo unit is the TRANSACTION, not the edit

One tape edit **plus all cascading recomputes** = one undoable transaction. Editing
`#3` recomputes `#5` and `#7`; the snackbar's `3 results updated · UNDO` undoes all
four or none.

**Partial undo is corruption, and it is worse than a crash because it looks fine.**
Restoring `#3` while `#5` keeps its recomputed value leaves the tape displaying a
result derived from an expression that is no longer there — internally inconsistent,
with nothing on screen to say so. A crash at least announces itself.

**Made unrepresentable, not merely tested:** `Tape` snapshots the whole `TapeState`
before every mutation, so undo restores an entire consistent tape by construction —
there is no code path that could restore one entry and leave another. The explicit
test is kept anyway (`M6TapeSpec`: "undo after a cascade restores EVERY dependent"),
and it is non-vacuous because it first asserts `updatedCount == 3`, proving the
cascade actually happened before checking that undo reversed all of it.

`UndoOutcome = Undone | NoMoreUndos`. There is deliberately **no `UndoFailed`**:
undo restores a snapshot, which cannot fail. Undoing past the start is "no more
undos" — an answer, not an error (Article 11: never convert a non-failure into a
failure, nor a failure into a non-result).

## D-024 · The function registry is the evaluator's gate (Article 12)

Autocomplete (11.5) and the keypad (11.1) both need the list of callable function
names. The evaluator held that list inside two `when` blocks over string literals,
readable by nothing. Re-typing it into an autocomplete file is one rule in two
places — and it rots *silently*: add a function and autocomplete never offers it;
remove one and autocomplete offers a name that errors.

`name/Functions.kt` is now the single declaration, and it **gates** dispatch:

- **accepted ⊆ registry** — by construction; the gate runs before either `when`.
- **registry ⊆ accepted** — by test (`M6NamesSpec`: every listed spelling resolves,
  and every declared arity is accepted, so the arity data is not decorative either).

Two inclusions, therefore equality. A keypad key naming a function the engine lacks
fails at layout construction; a suggestion the engine would reject is unreachable.

**Verified empirically, not assumed.** `sinh` was removed from the registry with its
evaluator branch left intact: `sinh(1)` then returned `UnknownSymbol`. The gate is
load-bearing — had it been decorative, the two-inclusion proof above would have been
a lie with a QED on it, which is precisely Article 11's shape. Restored after the
probe; suite green at 311.

---

## D-025 · Exact-first means the most UNDERSTOOD exact form, not the most canonical

**Owner ruling. The design was right; the engine was wrong.**

Article 3's acceptance test is `0.1 + 0.2 == 0.3`. Rendering `3/10` **passes the
engine's own headline test and fails the user's** — the entire point of that test was
a human seeing `0.3`. `53/10 km` is mathematically perfect and no human would ever
write it.

The rule, in `Formatter`:

- a rational with a **terminating** expansion renders as the decimal — `53/10` → `5.3`,
  `41/20 crore` → `2.05 crore`, `3/10` → `0.3`;
- a rational that does **not** terminate stays a fraction — `1/3` is `1/3`, because
  there the fraction *is* the exact form;
- **surds stay surds** — `2√2` is not `2.828`; no decimal expresses it at all;
- **output form follows input form** where both are exact: type `1/2`, see `1/2`; type
  `0.5`, see `0.5`. *The user's own notation is evidence of what they want back.*
- **`S⇔D` is the override** (census 1.4, fx-991 parity), and an explicit press outranks
  what the input implied.

**Guard on "terminating".** `1/2^128` terminates — after 39 significant digits, which
is *less* understood than the fraction. The bound is `displayPrecision`, a number this
engine already decided a human reads, not an invented constant.

**One rule, one place (Article 12).** `Formatter.preferredForm` derives the preference
from the parsed AST, so the router, the tape and M7 cannot each answer differently.
`Calculator.evaluateAnswer` had the input string and was discarding it — that would
have had the typed API answer `2.5` to `10/4` while the screen said `5/2`.

**The evidence the rule is not ad hoc:** of the five rows in `FormatterSpec`'s "exact
fractions" golden, only `0.1+0.2` moved. The other four were written with a fraction in
the input, so output-follows-input reproduces goldens a human had already chosen.

## D-026 · One name table, not two — the constants were orphaned

The design's §09 says *"what symbol does the engine know is one question"*. It had two
answers: `Units` was reachable from the expression language; `constants/Constants.kt`
was reachable **only** through `Calculator.physicalConstant()`, a typed API the router
never consulted. So the browser taught `c`, `φ` and `Nₐ` while the engine answered
`UnknownSymbol` — and taught `g` and `h` while the engine answered **gram** and **hour**.

*Wrong meaning, silently* is the D-014 failure mode and the worst of the three: `phi`
at least fails loudly.

**Resolution — the unit wins, and it wins consistently.** `2 h` must parse (the
formatter has to re-read its own output), so `h` is the hour and Planck becomes
`h_planck` — the convention `e_charge` already set for the identical collision with
Euler's `e`. Standard gravity was already `g0`. Boltzmann's `k` → `k_B`.

- `Constants.SYMBOLS` is the one table; `Evaluator.resolveName` resolves through it;
  `ReservedNames.CONSTANTS` is **derived from it** rather than the hand-typed
  `setOf("pi","e","i","tau")` that let the drift exist.
- **A collision fails at construction**, loudly: reintroducing `h` as Planck throws
  `IllegalArgumentException` and takes 176 tests with it. A wrong meaning cannot reach
  a user because it cannot start the engine. (Verified by doing it.)
- Constants resolve to **quantities, not bare numbers**: `c` is a speed, so `c·2s` is a
  length and `c + 1 kg` is refused. A dimensionless constant exposed as a number would
  make every downstream dimensional check a lie.
- Case-sensitivity stays load-bearing (D-016.4): register `C` ≠ speed of light `c`,
  register `E` ≠ Euler's `e`, gravitation `G` ≠ gram `g`.

**Known gap, found by this work and left for the owner:** `φ = (1+√5)/2` is a *binomial*
surd, and the tower has no type for one — `Rational | Surd | SymConst | Dec` holds `√5`
but not `1+√5`. So φ decays to `Dec` and **`φ² = φ + 1` comes back false**. This is
`sin(45)`'s class with a different cause: there the engine discarded an exactness it
had; here it cannot express the value at all, so no fast path fixes it. The remedy is a
quadratic-irrational type (ℚ(√d) — the shape of the design's own `SurdSum`), which is an
Article 3 decision about the number tower. Asserted as a test so the day the tower gains
it, someone is told.

## D-027 · One opening frame

There were **three**, and none knew about the others: the engine's `Viewport.DEFAULT`
at `[-10,10]²`, the design's graph default at `[-6.6,6.6]×[-4.2,4.2]`, and its
grid→axes reveal at `[-5,5]×[-3.4,6.6]`. In the design's demo the disagreement is
invisible, because the reveal and the graph are separate canvases. In the app they are
**the same surface**, so the curve jumps the instant the reveal ends. Nobody had
noticed because the frames had never had to be one value.

`Viewport.DEFAULT` is now the design's frame, in exact rationals (`±33/5`, `±21/5` —
6.6 is not representable in binary floating point; as a rational it is). The engine's
square `[-10,10]²` was only ever a placeholder.

**Correction to a comment that was wrong:** `Viewport`'s docs claimed `lerp` existed
because *"the grid→axes reveal is a transition between viewport states"*. Reading the
design's implementation settled it — **the reveal's grid never moves.** Its frame is
fixed and the effect is opacity choreography over staggered smoothstep intervals, which
is precisely why the idea lands: the paper was always graph paper, so nothing needs to
travel. `lerp` is for pinch-zoom, pan, and any reveal whose frame differs from `DEFAULT`
and must therefore *land* on it rather than snap to it.

Tests that exercised `lerp`/`pan`/sampling were coupled to `DEFAULT`'s numbers, so a
product decision about what the graph shows broke tests about arithmetic. Decoupled;
the frame is now pinned by one deliberate test.

## D-028 · Permissive parsing is POLICY, not an accident (owner ruling, 2026-07-22)

**Owner ruled ACCEPT on all ten** permissive-parsing items the B1 battery surfaced.
They are now deliberate behaviour with pin tests, not tolerated leftovers.

| # | Input | Result | What it is |
|---|---|---|---|
| 1 | `2++3` | `5` | unary plus after a binary operator |
| 2 | `--1` | `1` | double unary minus |
| 3 | `+-1` | `-1` | mixed unary prefix |
| 4 | `+ 1` | `1` | leading unary plus |
| 5 | `1./2` | `1/2` | trailing-dot literal |
| 6 | `[1+2]` | `3` | bracket grouping |
| 7 | `{1+2}` | `3` | brace grouping |
| 8 | `⇥⏎1+1` | `2` | leading / embedded whitespace |
| 9 | `-0` | `0` | negative zero folds |
| 10 | `１+２` | `3` | fullwidth CJK digits — and in fact **every Unicode decimal digit**, see below |

**The criterion, and it is a single one:** *every one of these produces the
mathematically correct answer from forgiving input.* None is a guess, none is a
reinterpretation, and none produces a number a mathematician would dispute. That is
exactly what separates them from the three defects fixed in `6d08f7b`/`38446de`/`de9d78a`
— `1..2 → 0.2` was *wrong*, and `1e → e` meant *something else than typed*. Refusing
input is only virtuous when the alternative is being wrong; here the alternative is
being right, so refusing would be pedantry with a cost.

**Why this needed a decision rather than silence.** Nine of the ten fell out of the
implementation — `[`/`{` are aliased to `(` in the tokenizer's `SYMBOLS` map, whitespace
is skipped, unary operators nest naturally in a Pratt parser, and **fullwidth digits work
because Kotlin's `Char.isDigit()` returns true for `U+FF10`–`U+FF19`**. Behaviour that
arrives by accident leaves silently: any future tokenizer refactor could delete it and
every test would still pass. **Ratifying without pinning would change nothing.** Hence
`D028PermissiveSpec` — seven of the ten had no test at all, and only `1./2` was pinned
(by `B1RegressionSpec`, as a guard against Defect 2's fix over-reaching).

**Fullwidth digits are the one worth arguing about**, so the reasoning is recorded rather
than assumed: Article 9 aims this product at Indian users, M8 carries localisation, and a
user typing `１２３` on a CJK IME means one hundred twenty-three. Accepting it costs
nothing and rejecting it would be a locale-specific refusal of an unambiguous number.

> **Correction, found by writing the pin — the item is broader than the ruling described.**
> This decision first stated that *"Devanagari digits are NOT accepted today"*. That was
> **false**, and the pin test asserting it failed. `Char.isDigit()` is true for the entire
> Unicode **Nd** category and `java.math.BigDecimal(String)` decodes those digits as well,
> so the engine already accepts **every decimal script** — measured: Devanagari `१+२`,
> Arabic-Indic `١+٢`, Bengali `১+২` all evaluate to `3`, and `१२३` is `123`. Fullwidth was
> never the boundary; there is no boundary. This *strengthens* the ruling for this product
> — Devanagari numerals are spec 14.4's own subject — but it was ratified without anyone
> knowing its true extent, which is exactly what pinning is for.
>
> **One consequence is left open, deliberately:** scripts may be MIXED in one expression
> (`१+1` → `2`). That is mathematically correct, so it falls inside D-028's criterion and
> is pinned as current behaviour — but whether a single entry *should* mix numeral systems
> is an M8 localisation question, not one this decision settles.

**Scope, stated so it is not over-read.** This ratifies *these ten inputs*, not a general
principle that the parser should accept anything it can find a reading for. The `1..2`
ruling is the counterweight and stands: where a forgiving reading yields a **wrong**
value, the input is refused (D-014). Permissive on form, strict on meaning.

## D-029 · The maund is 40 seer — the RELATION wins over the Act's literal figure (owner ruling, 2026-07-22)

> ⚠️ **SUPERSEDED BY [D-030](#d-030--supersedes-d-029--the-seers-provenance-corrected-and-a-conflict-that-never-existed-withdrawn-owner-ruling-2026-07-22).**
> The reasoning below contains two sourcing errors: the seer's attribution to the
> 1956 Act, and a claimed 37.3242 kg maund figure that **no source supports**. The
> unit VALUES it records are correct and unchanged. The text is left intact as the
> record of what was decided and why it was wrong; **read D-030 for what governs.**

**The conflict, in the same statute.** The Standards of Weights and Measures Act
No. 89 of 1956 (India) defines the **seer as 0.93310 kg** and separately lists the
**maund as 37.3242 kg**. But a maund is forty seer, and `40 × 0.93310 = 37.3240`.
The Act therefore disagrees with itself by **0.0002 kg — 0.2 grams**. Neither figure
is a typo: both are independent decimal roundings of the same underlying tola-based
arithmetic, rounded at different points.

**Owner ruling: the 40:1 relation wins.** `maund` is defined as **exactly 40 seer =
37.3240 kg**, deviating from the Act's literal maund figure by **0.2 g**.

**Why the relation and not the literal.** A user who converts `1 maund` to seer must
get `40`, not `40.0002…`. The relation is the thing people actually compute with and
the thing a wrong answer would be visible in; the fourth decimal place of a kilogram
is not. Choosing the literal would mean `1 maund − 40 seer ≠ 0` — a non-zero answer
to a question whose answer is definitionally zero, which is precisely the class of
*confident wrong value* D-014 exists to prevent. The 0.2 g is stated here rather than
buried so that anyone reconciling against the Act's text finds the discrepancy already
accounted for.

**Made structural, not asserted.** `Units` defines `maund = Num.of(40) * SEER` from
the one `SEER` constant, so `1 maund − 40 seer == 0` holds **by construction** —
there is no second literal that could drift. `G4UnitsSpec` pins both the zero and the
0.2 g deviation, so the ruling cannot be quietly reversed by a later edit.

**Regional variants — these are the statutory values, not universal ones.**
`guntha`, `seer` and `maund` all vary by region and by era: the guntha is commonly
1/40 acre but land measure differs across states; pre-1956 seers and maunds varied
widely by province and by commodity. The engine ships the **statutory/standard**
definitions above. This is the same honesty `bigha` already carries (*"pucca;
region-dependent"*, D-014) — and the design's own instinct, which annotates `bigha`
as *"≈ 2500 m² · varies"*. A configurable regional table is BACKLOG, not v1.

**The other four units in this batch are unambiguous** and need no ruling: `ac` is
the symbol for the acre already registered (4840 yd²), `guntha` is 121 yd² = exactly
1/40 acre, `nmi` is 1852 m by international definition, and `ly` is the IAU
light-year, `299792458 × 86400 × 365.25 = 9 460 730 472 580 800 m` — an exact integer,
verified as that product in a test rather than copied as a constant.

## D-030 · **SUPERSEDES D-029** — the seer's provenance corrected, and a conflict that never existed withdrawn (owner ruling, 2026-07-22)

**D-029 shipped two sourcing errors. The unit VALUES were right and are unchanged;
the reasoning attached to them was wrong.** History is not edited — D-029 stands in
the log as written, and this decision is what governs.

### Correction 1 — the seer is the traditional 80-tola seer, not the 1956 Act's

D-029 attributed **seer = 0.93310 kg** to the Standards of Weights and Measures Act
No. 89 of 1956. **That citation is contested.** Multiple sources give the 1956 Act's
*metric* seer as exactly **1.25 kg**; the 0.93310 kg figure is the older
**British-Indian Government seer of 80 tolas**, as tabulated by BIS.

- **The value does not change:** `seer = 0.93310 kg` exactly, and `maund = 40 seer =
  37.3240 kg` exactly.
- **The attribution does:** it is the **traditional 80-tola seer (BIS tables)**, not
  a statutory metric definition.
- **The metric seer of 1.25 kg is PERMANENTLY OUT OF SCOPE** (owner ruling,
  2026-07-22). Not deferred, not awaiting a vocabulary decision — **closed.** It was
  a transitional metrication artifact, and **nobody typing `seer` means 1.25 kg**.
  Since the two cannot share one name, supporting it would mean a second spelling
  whose only effect is to make an unambiguous word ambiguous — cost with no
  benefit. `seer` is the traditional 80-tola seer, full stop. Pinned by a test
  asserting `seer ≠ 1.25 kg`, so the closure is enforced rather than merely
  recorded.

### Correction 2 — the 0.2 g "conflict" is withdrawn as unsupported

D-029 claimed the Act separately defines the maund as **37.3242 kg**, conflicting
with 40 × its own seer by 0.2 g, and presented `maund = 40 seer` as the *resolution*
of that conflict. **No source supports 37.3242.** Every source gives maund = 40
seers = 37.324 kg, consistently. There is no conflict, so there is nothing to
resolve.

**`maund = exactly 40 seer` stands — as a plain definition, not as a ruling between
competing figures.** All reasoning about a 0.2 g discrepancy is struck, and the test
that pinned it has been deleted rather than left asserting a fabricated statute.

### The rounding that IS real, recorded with the right unit

The tabulated seer is a *rounded* figure, and this is worth stating because it is the
one genuine imprecision here:

> 80 tolas at the BIS tola of **11.6638038 g** = **933.104304 g = 0.933104304 kg**.
> The tabulated seer is **0.93310 kg**. The difference is **0.004304 g — about
> 4.3 milligrams (4304 µg)**.

**This build uses the tabulated 0.93310 kg**, not the 80-tola product. Deriving from
the tola would make `seer` depend on which tola figure is chosen, and the engine's own
`tola` is itself the rounded 11.6638 g — against which the same subtraction gives
exactly **4.0 mg**. Two roundings, two answers; the tabulated value is the one
published as the seer, so it is the one registered.

> **A correction to the correction, surfaced rather than absorbed:** the brief that
> ordered this fix stated the difference as *"about 4.3 micrograms."* It is **4.3
> milligrams** — 4304 µg, a factor of 1000 larger. Verified by exact decimal
> arithmetic (`80 × 11.6638038 = 933.104304`; `933.104304 − 933.10 = 0.004304 g`).
> The figure 4.3 was right and only the unit was wrong, but publishing "micrograms"
> would have been a new sourcing error inside the decision that exists to remove one.

### What is unchanged and re-verified

`acre` (316160658/78125 m²), `guntha` (158080329/1562500 m², exactly acre/40), `nmi`
(1852 m) and `ly` (9460730472580800 m) were independently re-derived in exact rational
arithmetic and are **correct as shipped**. D-029's structural points also survive
intact: units defined in terms of one another are still *derived* from one shared root,
so `40 guntha == 1 acre` and `1 maund − 40 seer == 0` remain true **by construction**.

The regional-variants note stands and is if anything strengthened: `guntha`, `seer` and
`maund` all vary by region and era — and the seer demonstrably varies by *which
definition* is cited (0.93310 kg traditional vs 1.25 kg metric). These are the
traditional/tabulated values, not universal ones. **Which of those the engine speaks is
now settled, not open:** the traditional one, per the closure above.

## D-031 · Eight M7-2 rulings on what the design left unspecified (owner, 2026-07-28)

The v1 design export answered most of M7-2, but ten specs were absent or ambiguous
(`DESIGN-HANDOFF-M7-2.md`). Six were **never decided** by the design at all. These eight
rulings close nine of the ten; only the portrait target width stays open, and it blocks
nothing.

**The through-line:** where the design was silent, we do not invent a visual language for it.
Where the design was silent *and the engine knows something the design does not*, the engine's
information wins — but it surfaces where the design already put per-entry state, not by
inventing a new surface.

### D-031.1 · Disabled keys — there is no disabled state

Unavailable actions **no-op silently**. The design specifies exactly two key states, `default`
and `pressed`; there is no `disabled`, `aria-disabled`, or `cursor:not-allowed` anywhere in the
export. **Inventing a third visual state is not ours to invent.** A key that does nothing looks
like a key.

### D-031.2 · Error display — the dash stays, the kind goes to the tape

The result line keeps the design's em-dash `—`. **The typed error kind surfaces in the tape row
instead.** The design's calm dash is right for the display: a big red error on the main line
punishes a typo. But collapsing the engine's typed errors into one dash discards information
**the engine uniquely has** — `DivideByZero` vs `Domain` vs `Arity` vs `UnknownSymbol` — and
the tape already carries per-entry state (`Failed` with its `reason`). Two surfaces, two jobs:
the display stays calm, the tape stays specific. This is the §3 collapse the gap analysis
warned about, resolved without contradicting the design's display.

### D-031.3 · Dark/AMOLED elevation — no substitute for the vanished shadow

`--shadow` is `0 0 0` in both dark themes, so every `rgb(var(--shadow)/.05)` resolves to
transparent and shadows disappear. **Nothing replaces them.** Depth is carried by the
three-step surface ladder — `--paper` → `--sheet` → `--sheet-2` — plus the 1 px `--sheet-edge`,
all of which are already defined per theme. **Revisit only after on-device verification**: a
ladder that reads fine on a calibrated monitor may not on an OLED panel at low brightness, and
that is a judgement no amount of hex-reading settles.

### D-031.4 · Touch targets — visuals stay, hit areas grow

Visual sizes are unchanged (**34 px** chips, **40 px** drawer buttons, **20 px** tape badge),
but **every interactive element gets a 48 dp minimum hit area** via a touch delegate or
invisible padding. The export has no hit-area expansion anywhere, and whether 34/40 was
deliberate is not stated. Keeping the visual and growing the target is the only reading that
honours both the design's density and Android's accessibility floor — they were never actually
in conflict, because a hit area is not a pixel.

### D-031.5 · Hindi — numerals only, exactly as designed

The language toggle switches **numerals and nothing else**: `dig()` maps `0-9 → ०१२३४५६७८९`.
The setting is literally labelled **"Numerals"** with options `0–9` / `०–९`. **Button labels,
menus, tape labels and error messages stay English.** The only other Devanagari is static unit
vocabulary in the browser (`लाख करोड़ तोला बीघा गुंठा`), which renders regardless of language.
**RTL support is deferred** — the export carries an `Auto (LTR)` / `Force RTL` setting with the
note *"Structures are RTL-safe; keypad mirrors, display stays LTR"*, but no implementation.

This is not a reduced ambition: a Devanagari numeral set is the thing an Indian user actually
notices, and a half-translated UI reads worse than a clean English one.

### D-031.6 · Landscape — deferred beyond M7-2, portrait only

The export **does** specify landscape fully (640 px frame, 290 px shell, 6 × 4 = 24 keys —
`DESIGN-HANDOFF-M7-2.md` §2.4). It is deferred anyway, because **its 24 keys include
`sin cos tan √ ln xʸ` — six scientific keys the portrait surface deliberately hides.** The
portrait thesis is that nothing scientific shows until summoned; the always-on scientific pad
is the direction the design's own redline **DISQUALIFIED**. Landscape reinstates it with **no
stated rationale for the reversal**.

**Shipping one coherent surface beats shipping two that argue with each other.** Landscape
returns when someone rules on whether the reversal is intended.

### D-031.7 · `#n` insertion — tap the badge, not a long-press

**Tapping a tape row's `#n` badge inserts that reference into the expression. Tapping the row
body keeps the existing copy behaviour** (deep-copies the entry's nodes, as the design's
`reuse()` does).

This closes **G-6**, which the design did not settle: it *renders* `#n` but has no key,
gesture, or code path that inserts one.

**Why the badge and not a long-press.** The design twice rejected long-press on
discoverability — scoring it **3/10** there, and rejecting two-finger undo with *"Less
discoverable than the long-press we already killed — same mechanism, same verdict."* A badge
tap honours that: **the badge already displays the exact token it inserts**, so there is no
hidden gesture to discover and no affordance to teach — you tap `#3` and `#3` appears. It also
keeps one object with two obviously different targets rather than one target with two timings.

The badge is **20 × 20 px** and therefore **needs the 48 dp hit area from D-031.4**.

### D-031.8 · Ambiguous — D-016.1 stands; the chips are not built

The design's `Ambiguous` exemplar is `x = 5` with chips *"Solve as equation"* / *"Assign
x ← 5"*. **D-016.1 removed exactly that ambiguity** — `=` is equation-only, storing is `5 → A`
*(example amended by D-036.3; `x` is an unknown and cannot be assigned, D-016.4)*
— on the principle that *the same keystrokes always mean the same thing*. The engine therefore
computes `x = 5` and cannot produce `Ambiguous`.

**`RouteResult.Ambiguous` has no producer and none will be added in v1.** The type stays: it is
the contract for a genuine future ambiguity (D-016.5), and a state that cannot currently occur
is different from one that cannot be represented. `EntryValue.Ambiguous` is **proven
unreachable** by `TapeTransitionSpec`, not merely asserted in prose.

---

## D-032 · The tape transition graph — derived from the engine, not from the design gallery

**The design has no transition table.** Its eight-state tape is a *gallery*: one exemplar row
per state with a blurb, plus two demo interactions. Deriving a state machine from it would have
been fiction. So this graph is derived from `Tape.recompute` and **measured**, and every
transition in it is enforced by `TapeTransitionSpec` (35 tests).

### The structural fact that shapes everything else

**`Tape.recompute` is a TOTAL RE-DERIVATION, not a step function.** Every transaction
recomputes every entry from `(source, dependencies, cycle-membership)` alone. The prior state
is consulted for exactly one thing: `previousOf(e)` carries the last `Computed` value into
`Failed`/`Blocked` as `previous`, so a broken row can still show what it last held.

**Consequence: there are no forbidden transitions between reachable states.** Any reachable
state can follow any other. That is not laxity — it is what "recompute from source" means. The
spec therefore exhausts all **5 × 5 = 25 ordered pairs** instead of asserting a hand-drawn
arrow set that would have been weaker *and* unfalsifiable.

### Derivation table — precedence order, exactly as `recompute` applies it

| # | Condition | Destination |
|---|---|---|
| 1 | entry is in a dependency cycle — **it can reach itself**; members are its own SCC, tape order *(amended by D-036.5; was: "in the cyclic remainder of Kahn's sort")* | `Cycle(own SCC)` |
| 2 | `source.toInput()` is blank | `Empty` |
| 3 | `Parser.parseProgram` returns `Err` | `Failed(error, previous)` |
| 4 | any dependency handed this entry no value | `Blocked(by, previous)` |
| 5 | router returns `Ok` | `Computed(outcome)` |
| 6 | router returns `Err` | `Failed(error, previous)` |
| 7 | router returns `Ambiguous` | `Ambiguous(candidates)` — **unreachable, see below** |

Rule 4's test is *"the dependency handed me no value"*, **not** *"the dependency failed"*.
Measured, three distinct causes all block: the dependency is `Failed`, or `Empty`, or
**`Computed` but holding roots/a verdict rather than a value** (D-021's subtle case).

### Events

| Event | Effect |
|---|---|
| `append(source)` | new entry appended, then a full recompute |
| `edit(id, source)` | that entry's source replaced, then a full recompute |
| `delete(id)` | entry removed, then a full recompute — dependents may unblock or fail |
| `undo()` / `redo()` | **not transitions.** They restore a whole `TapeState` snapshot; the transaction is the undo unit (D-023) |

### Reachability in v1

| State | Reachable? | Why |
|---|---|---|
| `Computed` | **yes** | router `Ok` |
| `Empty` | **yes** | blank source |
| `Failed` | **yes** | parse error or router error |
| `Blocked` | **yes** | a dependency yielded no value |
| `Cycle` | **yes** | Kahn's remainder |
| `Stale` | **NO** | **Nothing in `Tape` ever constructs it.** The tape recomputes eagerly, so there is no window in which a value is known-old-and-pending. It exists for an async UI layer that does not exist yet. |
| `Ambiguous` | **NO** | `RouteResult.Ambiguous` has no producer (D-031.8) |
| `Unverified` | **N/A — does not exist** | The design's 8th state. `EntryValue` has seven cases; `Unverified` lives only on `graph/RootResult`. Adding it is M-2 in the handoff. |

Both unreachable states are asserted as unreachable by sweeping every operation the tape
exposes — an unreachable state that nothing *proves* unreachable is just an untested one.

### Two behaviours that are NOT what a reader would assume — both measured

1. **A reference to a non-existent entry is `Failed`, not `Blocked`.** `#99` is filtered out of
   the dependency set (it is not on the tape), so the name survives to the evaluator and
   resolves as an unknown symbol. Defensible: nothing is *waiting* on `#99`.
2. **A self-reference is `Failed`, not a `Cycle` of one.** `#1 = #1+1` yields `Failed`.
   **This contradicts the comment at `Tape.kt`'s `out.remove(self)`** — *"a self-reference is a
   cycle of one; the sort will catch it."* It does not: self is removed from the dependency set,
   so the entry is orderable and reaches the evaluator as an unbound name.

   **The comment is wrong, the behaviour may also be wrong, and this decision does not fix
   either** — this pass is documentation, decisions and tests only. The real behaviour is
   pinned so it cannot drift silently, and flagged for a ruling: `Cycle([#1])` is arguably the
   honest answer, since a self-reference genuinely has no computable order. **Owner ruling
   needed; no main source was touched.**

### Nothing is UNDETERMINED

Every transition in the table was measured against the real `Tape`. No cell required a guess.

> **Amended 2026-08-02 (D-036.5).** Rule 1's membership is now SCC-based: a
> bystander that merely depends on a loop is `Blocked(by a member)` — rule 4's own
> wording, once membership is honest — and disjoint loops keep separate member
> lists. The B2 battery had measured Kahn's merged leftover branding bystanders
> as members (probes C-003..037). The 25-pair matrix in `TapeTransitionSpec` holds
> unchanged; `D036SccCycleSpec` pins the new membership. Rule 3½ (D-036.1, between
> rules 3 and 4): refs missing at commit are a sticky `Failed(MissingEntry)`.
>
> **On `previous` retention (D-036.7, measured note).** `previousOf(e)` reads the
> value the entry held going INTO the transaction, so `Failed.previous` /
> `Blocked.previous` carry the "was" value only across the breaking transaction —
> one more transaction and it is null (B2-S-011); `Cycle` never carries one
> (B2-S-012). The design's *"was, now updating"* blurb belongs to **`Stale`**,
> which is UNREACHABLE in v1 (the tape recomputes eagerly; the state exists for an
> async UI layer that does not exist yet). Carrying `previous` further would be
> building `Stale`'s feature into the wrong states — moot until `Stale` is real,
> so this is recorded, not changed.

## D-033 · A self-reference is `Cycle([#n])`, not `Failed` (owner ruling, 2026-07-28)

`#1 = #1+1` returned **`Failed`**. It now returns **`Cycle([#1])`**.

**Why the relation, not the generic error.** A self-reference **is** a cycle — just a short
one. Classifying a cycle of length one differently from a cycle of length two is an arbitrary
boundary: nothing about `#1 → #1` is less circular than `#1 → #2 → #1`, and a user who wrote
one made the same mistake in both cases. `Cycle` also **names the actual fault** — it reports
which entries are in the loop — where `Failed` is generic and leaves the user to work out that
the problem is circularity rather than, say, a typo in a function name.

**This fixed the behaviour to match the comment, not the other way round.** `Tape.kt` read:

```kotlin
out.remove(self) // a self-reference is a cycle of one; the sort will catch it
```

The sort did **not** catch it: removing `self` from the dependency set made the entry
*orderable*, so it drained out of Kahn's algorithm normally and reached the evaluator as an
unbound name — hence `Failed`. The comment described the intent and the code did the opposite,
which D-032 found by measurement. Deleting the `remove` makes the entry undrainable, so it
lands in Kahn's remainder and **the sort does now catch it, exactly as the comment always
claimed**. Cycle detection stays a property of doing the sort correctly (D-022) rather than a
special case bolted on for self-reference.

**What deliberately did NOT change.** A reference to an entry that is *not on the tape* —
`#99` — stays **`Failed`**. That is a different fault: nothing is *waiting* on `#99`, so
"blocked" would be wrong and "cycle" would be a lie. It is filtered out of the dependency set
because it does not exist, not because it is self. Pinned by its own test so the two faults
cannot be collapsed later.

Enforced by `TapeTransitionSpec`: the cycle of one, the isolation of neighbouring entries, and
recovery when the self-reference is edited away. All 25 transition pairs still hold.

## D-034 · The `Func` entry node — one keystroke, one node (M-1 / G-3)

`entry/Node.kt` gains **`Func(name, arg)`**, a container with a single field. This closes
**G-3**, open since the gap analysis, which the v1 design specifies completely
(`DESIGN-HANDOFF-M7-2.md` §4.4).

**What was wrong.** Pressing `sin` inserted the *text* `sin(` — four atoms and an unbalanced
paren for one keystroke. That broke D-018's axiom outright (*"each structure was created by
exactly ONE keystroke — which is precisely what backspace inverts"*): four backspaces to undo
one press. It also made `Node.kt`'s balanced-paren invariant false, since `Editor.insert` built
an `Atom('(')`.

**The four properties, all implemented and pinned:**

1. **A container with one `arg` field.** One press builds it; one press removes it (empty), or
   selects-then-deletes it (populated, Rule 3 / D-019). Because the editor only collapses an
   *empty* row, collapsing from `ARG` can never destroy content — the same guarantee `Root` and
   `Paren` already have.
2. **Display name ≠ engine spelling.** The tree stores `asin`; a renderer draws `sin⁻¹`.
   Mapped: `asin→sin⁻¹`, `acos→cos⁻¹`, `atan→tan⁻¹`, `cbrt→∛`; everything else displays as it
   parses.
3. **`toInput()` emits the ENGINE form** — `asin(1)`, never `sin⁻¹(1)` — so the editor's output
   still round-trips through the real parser (Article 12). Storing the display form in the tree
   would have made that impossible.
4. **The palette inserts by engine name, labels by display name**, via the new
   `KeyAction.Func(name)` — replacing `Insert("$name(")`. Keypad and palette now build the same
   node, so a function reached either way backspaces in one press.

**One map, not two (Article 12).** The display names live in `name/Functions.kt`, the registry
that is already the single declaration of function names (D-024) — not in the entry model *and*
in the palette. A test asserts the palette's labels agree with the registry, so the two cannot
drift into showing different names for one function.

**Deliberately unchanged:** `sqrt` stays a `Root` structure and `pow` a `Power` — they have
their own 2-D forms and are not function calls on screen. `Func` is for the ones that are
written as `name(arg)`.

## D-035 · Devanagari numeral OUTPUT — the other half of D-028 (M-3)

`Settings.numerals: NumeralSystem` — **WESTERN** (`0123456789`, the default) or
**DEVANAGARI** (`०१२३४५६७८९`). Built exactly like `GroupingMode` (G-5): an explicit field on
an immutable settings object, never a global mutable default.

**This closes a one-sided asymmetry.** D-028/D-030 established that the engine *accepts*
Devanagari input — `१+२` is measurably `3` — but the `Formatter` could only emit ASCII. An
engine that reads a numeral system it cannot write is half-localised, and the design's language
toggle is *literally labelled "Numerals"* (D-031.5), so this is the whole of what Hindi mode
changes.

**Digits only, enforced by where it is applied.** Transliteration runs at the two `Num`-level
entry points, `exact` and `decimal`. A quantity's unit name is appended *after* that, so
`15 m2` renders as **`१५ m2`** — the value converted, the SI symbol untouched, including the
digit inside `m2`. Operators, `√`, `π`, `i`, the fraction slash and the decimal point are not
digits and pass through by construction rather than by an exclusion list.

**Idempotent by design:** only `'0'..'9'` are mapped, so a string already carrying Devanagari
digits is returned unchanged — which matters because `exact` delegates to `decimal` internally
and would otherwise convert twice.

**It composes with grouping on both axes**, because the two are independent: separators are
chosen by `GroupingMode`, glyphs by `NumeralSystem`. `१२,३४,५६७` is Indian grouping with
Devanagari digits; `12,34,567` is Indian grouping with Western digits. Both are pinned.

**One bug this surfaced and fixed.** `Formatter.coefficient` decides whether a quantity's
coefficient needs parentheses by asking "is this a bare number?" with `Regex("-?\\d+…")`. In
Java regex `\d` is **ASCII-only**, so `१५` failed the test and would have rendered as
`(१५) m` — parentheses appearing only in Hindi. The regex now uses `\p{Nd}`, every Unicode
decimal digit, so the question stays the one actually being asked instead of silently meaning
"is this a bare *Western* number". Pinned by its own test.

**Round-trip holds:** `९८७६५` re-parses to 98765, because the input half already existed. The
engine can now read back everything it writes in either numeral system.

**Scientific exponents ARE transliterated** (`१e१२३४५`), unlike grouping, which deliberately
skips them. A separator inside an exponent stops it being a number; a digit glyph does not, and
the design's own `dig()` converts the whole rendered string.

---

## D-036 · The twelve KB_14 rulings (owner, 2026-08-02) — BRAIN-RUN-REPORT §4 adjudicated

The B3/B2 brain batteries (1020 cases) produced three findings and nine
ruling-worthy probes. Each numbered item below is the owner's ruling with its
one-line rationale; sub-numbers reuse the report's numbering.

1. **Commit guard (APPLIED, `D036CommitGuardSpec`).** A `#n` referencing an entry
   that does not exist at commit is a sticky typed error naming the id
   (`EvalError.MissingEntry`); an edit re-captures. *A dangling reference that can
   silently adopt a future stranger's value is the named harm verbatim.*
2. **Atomic `Ref` node (APPLIED, `D036RefNodeSpec`).** `#n` is one node — badge,
   typed and pasted routes converge; structure keys capture it whole. *One thing on
   screen must be one thing in the tree, or ⌫ and a⁄b lie.*
3. **D-016.1 example fixed (APPLIED, text).** `5 → A`, not `5 → x` — `x` is an
   unknown (D-016.4). *The engine was right; the example drifted.*
4. **`)` exits a Func ARG (APPLIED, `D036CloseParenSpec`).** Exit-right, one level
   per press; other fields stay no-ops. *A dead keypress on a destructive-adjacent
   key is the exact class D-019 killed.*
5. **SCC cycle membership (APPLIED, `D036SccCycleSpec`).** `Cycle` = the entry's
   own SCC; bystanders are `Blocked(by a member)`; disjoint loops stay separate.
   *"Names every entry in the loop" must mean THE loop.*
6. **Updated-contract (NO CHANGE).** `Transaction.updated` stays value-based; a
   same-value edit reports 0. *The count answers "which numbers moved" — the
   snackbar copy is the UI's problem, not the transaction's.*
7. **`previous` retention (NO CHANGE, note added to D-032).** The one-transaction
   lifetime stands. *"Was, now updating" is `Stale`'s job, and `Stale` is
   unreachable in v1 — building its feature into `Failed`/`Blocked` would be the
   drift Article 12 forbids.*
8. **Wait wording (APPLIED, `D036WordingSpec`).** `Blocked.description` =
   `"waiting on #n"`, declared on the state. *"It failed" is a lie for an Empty or
   valueless-but-successful dependency.*
9. **Hold-⌫ pause (NO CHANGE — view-layer).** The 70 ms repeat blowing through a
   Rule-3 selection is real, but the repeat RATE is the view's (D-018); the model's
   select-then-delete is correct. *Ruled for M7-2's key handler: re-arm the 380 ms
   delay when a repeat lands on a fresh selection; nothing to change in core.*
10. **Dual representation (NO CHANGE).** Pasted `sin(30)` (atoms+paren) and the
    sin key (`Func`) stay distinct trees with one linearisation. *Paste-time
    normalisation would rewrite what the user pasted; the trees agree where it
    counts — the evaluated text.*
11. **Registers tape-global (APPLIED, `D036RegistersSpec`).** A register read
    depends on the nearest prior assigner, like `ans`. *The panel and the tape must
    not disagree about what `A` means.*
12. **`Unverified` / M-2 (NO CHANGE — capability gap).** The 8th state stays
    unbuilt: its design exemplar (`x³−4x²+x+6=0`) needs a cubic/numeric solve
    path the engine does not have (B2-U-005 measured `Failed`). *An enum case
    nothing can produce is D-031.8's own anti-pattern; the solver capability is a
    milestone decision, not a tape patch.*

---

# The M0.4 spike — ML Kit Digital Ink for handwritten math

**Verdict: FAIL (text only, no math structure). PREDICTED, not an on-device run.
Confidence: HIGH.**

## Why this is predicted, not measured — stated plainly

The brief says: *"Do not guess it — build the sketch and run it,"* and asks for
"the actual outputs you got, not a summary." This build environment has **no
Android device, no emulator, no touch input, and no ML Kit runtime**, so the six
handwritten inputs could not be run through the recognizer here. Fabricating
"outputs I saw" would poison the one decision this spike exists to make. Instead:

- the throwaway sketch is written and device-ready (`mlkit-spike/`), so the
  empirical run is a 30-minute confirmation on a phone; and
- the verdict below is derived from Google's **primary documentation** and the
  SDK's **type system**, which are unambiguous on the deciding facts.

Confidence is HIGH (not "certain") because it reasons from the shipped model
catalog and output types rather than a device trace of these exact inputs — but
an on-device run **cannot flip it**, because the data structure that would hold
"math structure" does not exist in the SDK. It can only reveal which flat string
each input degrades to.

## Evidence (primary sources)

1. **No math recognizer ships.** The official Base Models list contains
   handwritten **text** (300+ BCP-47 languages) plus four non-text models —
   emoji (`zxx-Zsye-x-emoji`), autodraw (`zxx-Zsym-x-autodraw`), basic shapes
   (`zxx-Zsym-x-shapes` → "RECTANGLE"/"TRIANGLE"/…), and per-language gesture
   classifiers (`-x-gesture`). No formula/equation/math model.
   <https://developers.google.com/ml-kit/vision/digital-ink-recognition/base-models>
2. **The identifier enum confirms it.** `DigitalInkRecognitionModelIdentifier`
   exposes language/script codes plus `AUTODRAW`, `EMOJI`, `SHAPES`,
   `*_GESTURE`. There is no `MATH`/`FORMULA`/`EQUATION` — you cannot name a math
   recognizer to download.
   <https://developers.google.com/android/reference/com/google/mlkit/vision/digitalink/recognition/DigitalInkRecognitionModelIdentifier>
3. **The output type cannot hold math layout.** `RecognitionResult.getCandidates()`
   yields `RecognitionCandidate` objects exposing only `getText(): String` and
   `getScore()`. No bounding boxes, no baseline/superscript position, no fraction
   bars, no MathML/LaTeX, no tree.
   <https://developers.google.com/ml-kit/vision/digital-ink-recognition/android>
4. **Google treats handwritten math as a separate, unsolved-by-text problem.**
   Google's 2024 *MathWriting* work builds a dedicated dataset because math has
   meaningful 2D position and cannot be treated as a 1-D sequence like text;
   its ground truth is **LaTeX**, not ML Kit-style strings.
   <https://arxiv.org/abs/2404.10690>
5. **Structured math handwriting exists — as a different class of engine.**
   MyScript iink SDK's Math Recognizer decodes strokes into a structured
   representation and exports **MathML and LaTeX**. ML Kit ships nothing
   comparable. <https://www.myscript.com/blog/iink-sdk-3-0-math/>
6. **CROHME sanity-check.** CROHME (online handwritten math recognition) is the
   standard benchmark; a CROHME/MathWriting-trained stroke→LaTeX encoder-decoder
   is the established route — i.e. the brief's ~120h alternative.

## Per-input prediction (default `en` text recognizer)

| Input | Most likely ML Kit return | Math structure preserved? |
|---|---|---|
| `2+2` | `"2+2"` (or misreads like `"242"`) | No structure (none needed; just glyphs) |
| `x²` | `"x2"` — superscript flattened; or `x` + `2` | **No** — exponent lost, never `x^2` |
| `√9` | `√` not in alphabet → dropped/misread (`"9"`, `"v9"`) | **No** — no radical relation |
| `∫x dx` | `∫` misread as a letter → `"fx dx"`, `"Sx dx"` | **No** — no operator/bounds |
| `1/2` (stacked) | `"12"`, or two split candidates `1`,`2` | **No** — no fraction relation |
| `sin(30)` | `"sin(30)"` — alphanumerics + parens in-set | plain string, no function/arg semantics |

The two linear inputs can come back correct *by accident of being 1-D*; every
genuinely 2-D input loses structure, and non-Latin operators (`√`, `∫`) are
outside the recognizer's alphabet.

## Implication

Points to the **~120h** estimate (CROHME/MathWriting → LiteRT stroke→LaTeX model),
not the ~30h SDK integration. Per Article 8, v2 handwriting therefore also moves
to **Fable 5**.
