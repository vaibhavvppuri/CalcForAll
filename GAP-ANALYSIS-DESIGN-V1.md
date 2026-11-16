# Gap analysis — Design System v1 × `core/` engine

**Date:** 2026-07-17 · **Engine:** commit `de48cae` (M6 complete, 311 tests green)
**Design:** `Calculator App.html` — 13 sections, live demo engine, full rationale
**Author:** the engine side. The design was built by an agent that has never seen `core/`.
**Status when written:** REPORT ONLY. Nothing resolved, no code changed.

---

> ## Disposition — owner ruled 2026-07-17. Engine now at 419 tests, 0 failures.
>
> | # | Finding | Outcome |
> |---|---|---|
> | **F-1** | `sin(45) = √2/2` → FALSE | **FIXED** — `num/ExactTrig.kt`, a fast path pinned to the kernel to 40 digits (D-024 shape). The sweep found 3 more in the *inverse* direction (`asin(1/2) = 30` was FALSE). `tan(90)` is now a Domain fault, not `-4.08e47`. |
> | **C-5** | Constants orphaned; `g`/`h` lie | **FIXED** — D-026. One table; the unit wins; Planck is `h_planck`. A collision now fails at construction. |
> | **C-1** | `0.3` vs `3/10` | **FIXED** — D-025. Owner ruled for the design. Terminating → decimal; output form follows input form; `S⇔D` is the override. |
> | **C-2** | ⌫ top-right | **FIXED** — moved to the bottom arc, one slot left of `=`. |
> | **C-7** | Exact-trig table | **FIXED** — with F-1. |
> | **C-3 / F-2** | `(` is `Paren` or `Atom`; false invariant | **FIXED** — `insert` routes to `typeParen`; the invariant is now true and asserted over the tokenizer's own alphabet. |
> | **C-4 / G-1** | `scientific()` rebuilt the disqualified direction; no palette | **FIXED** — `Keypad.scientific()` deleted; `FnPalette` models the searchable contextual layer; one resting state. |
> | **G-2** | 4th flick `.↑π` | **FIXED** — the design is authoritative over the brief's three. |
> | **§3** | Article 11's 8 states → one `'—'` | **NOT MINE** — the design agent's. Do not invent visuals for Blocked/Cycle/Ambiguous. |
> | **G-3** | No `func` node | **OPEN** — not ruled on. |
> | **G-4** | 6 units absent | **OPEN**. |
> | **G-5** | No digit grouping | **OPEN**. |
> | **G-6** | No way to type `#n` | **OPEN**. |
> | **C-6** | Roots `3, 2` vs `2, 3` | **OPEN**. |
>
> **New, found while fixing** — `φ² = φ + 1` returns **false**. `φ = (1+√5)/2` is a
> *binomial* surd and the tower has no type for one, so it decays to `Dec`. Same class
> as F-1, different cause: not exactness discarded, but exactness the tower cannot
> express. The remedy is a quadratic-irrational type — the shape of the design's own
> `SurdSum` — which is an Article 3 decision about the number tower. Asserted as a test
> so it cannot be forgotten. **Owner ruling needed.**

---

## Method

The design ships a working demo engine (~560 lines of JS inside the bundle), so it
does not merely *describe* behaviour — it *asserts* it, executably. That makes this
checkable rather than interpretive, so I checked it rather than reading it.

Every behavioural claim below was run against the real engine through
`ExpressionRouter` — the same entry point the app will use — in two throwaway probes
(`DesignClaimProbe`, `DesignClaimProbe2`), which were **deleted afterwards**; the
working tree is clean and the 311-test suite is untouched and green. Where a row says
what the engine does, that is a **transcript**, not a recollection. Where I could not
run something, it is marked NOT VERIFIED rather than asserted (Article 6 / A2).

Two findings below are engine bugs, not gaps. I am reporting them here rather than
fixing them because you asked for a report — but F-1 in particular should not wait
for M7.

---

## Verdict, in one paragraph

**The design's thesis is sound and the engine already delivers it.** The headline
frame — `√8 → 2√2`, exact, in real mathematical type — is real: the engine returns
`2√2` today, and `√50 − √8 → 3√2`, `⅓×3 → 1`, `x²−5x+6=0 → x = 2, 3` all hold. The
architecture agrees at a level that is genuinely surprising for two components that
have never met: the design independently arrived at *snapshot-the-whole-state* undo
(D-023), *no C/AC key*, *hold-to-repeat*, *undo as snackbar*, and *"a mode is a
failure when the input has a natural text form"* — which is D-015's ruling, almost
verbatim, derived independently.

**What it gets wrong is concentrated in three places.** (1) The **display contract**:
the design shows `0.3` and `5.3 km`; the engine renders `3/10` and `53/10 km`. Both
are "exact-first"; they disagree about what exact-first *looks like*, and the design's
own proof frame is the casualty. (2) The **entry model**: the design has a first-class
`func` node the engine does not have, and my keypad's function keys violate my own
D-018 axiom because of it. (3) **Article 11 has no surface at all** — the design has
exactly one failure state, `'—'`, for everything.

**The most dangerous single finding is not a gap — it is F-1**, a live engine bug the
design's exact-trig table exposed: the engine answers **`sin(45) = √2/2` → FALSE.**

---

## 1 · What the design asserts that the engine ALREADY does

Verified by execution. These need no work.

| Design claim | Where | Engine result (transcript) |
|---|---|---|
| `√8 → 2√2` — the signature proof frame | §02 callout 1, §03 frame ② | `2√2` ✓ |
| `√50 − √8 → 3√2` | §02 Try-it | `3√2` ✓ |
| `⅓ × 3 → 1` exactly | §02 callout 1 | `1` ✓ |
| `0.1 + 0.2` is exactly 0.3, not 0.30000000000000004 | §02 callout 1 | exact ✓ — but renders `3/10`, see **C-1** |
| `x²−5x+6=0` summons the solver from typed input | §03 frame ③ | `ROOTS x = 3, 2` ✓ — order differs, see **C-6** |
| `5!` → 120 | §12 landscape tape | `120` ✓ |
| `5 km + 300 m` evaluates inline in the main box | §09 | evaluates ✓ — renders `53/10 km`, see **C-1** |
| `2 crore + 5 lakh` evaluates inline | §09 unitNote | evaluates ✓ — renders `41/20 crore`, see **C-1** |
| Conversion is inline in the engine, not a converter screen | §09 thesis | D-016 exactly ✓ |
| "A mode is a failure when the input has a natural text form and correct when it has a natural structured one" | §08 | **This is D-015, independently derived.** Matrix/stats stay typed APIs; equations and units are inline ✓ |
| Matrix `det`, `A⁻¹`, `Aᵀ`, `rank`, live recompute on cell edit | §08 | `Matrix.kt` has all four, exact-rational ✓ |
| Stats `n`, `Σx`, `mean`, `median`, `σ` | §08 | `stats/Stats.kt` ✓ |
| Indian units are first-class: lakh, crore, tola, bigha | §09 | 4 of 7 present ✓ — 3 absent, see **G-4** |
| 2-D entry: fractions stack, exponents raise, √ contains | §02 callout 3 | `entry/Node.kt` ✓ |
| ⌫ tap = delete one; ⌫ hold = repeat (inherited convention) | §07 rescore | D-018 ✓, `KeyAction.BackspaceRepeat` |
| No C/AC key; "clear the tape" is a tape action | §02 | D-018 ✓ — engine has no clear key |
| Undo is a snackbar, at the site of the destructive act | §02 | D-018 ✓ |
| Undo restores expression **and** history together, atomically | §02 `fireUndo` | **D-023, independently derived** ✓ |
| Tape tap-to-reuse live-recomputes and raises undo | §02 | `Tape.edit` ✓ — but see **G-6** on dependencies |
| Exact-first with a decimal companion (`≈ 2.8284271`) | §02 | `Formatter.exact` + `Formatter.decimal` ✓ |
| Angle DEG default, DEG/RAD/GRAD | §10 | `Settings.angle` ✓ |

**The strongest agreement is `fireUndo`.** The design snapshots `expr` *and* `history`
as one JSON blob and restores both together. That is D-023 — the undo unit is the
transaction — reached from the design side with no knowledge of the tape's dependency
graph. Two independent derivations of the same rule is the best evidence either of us
has that the rule is right.

---

## 2 · What the design asserts that the engine CANNOT do

Named file, named missing capability.

### G-1 · The `fn` palette has no model. **Blocks the 8.9 thesis.**
`keypad/Keypad.kt` — there is no `fn` key and no palette concept.

The design's shipped answer to the 2nd-function problem is **"Flick + contextual fn
palette" (composite 8.9)**. It is *two* mechanisms. The engine models the flick and
**not the palette**. §07's own rationale is explicit that the palette carries Hick's
Law — "flick + palette keeps the pad tiny for Fitts and moves the fifty into a
searchable contextual layer for Hick" — and that the single visible `fn` doorway is
"the honest dent in User A's otherwise perfect surface", charged against the 41%
criterion.

The engine instead has `Keypad.scientific()`, a **second static layout with 12 function
keys**. That is not the palette. Measured against the design's own redline, an
always-on scientific pad is `DISQUALIFIED` — "User A meets 30+ scientific keys on the
default surface. The fx-991's answer — **and our own first build**." The engine
independently rebuilt the disqualified direction.

Missing: a `Palette` model (categorised, **searchable** — `name/Autocomplete.kt` is
the obvious substrate and already ranks over the real registries), a transient-overlay
state, and an `fn` key whose action opens it. Note §04's engineer's test: *"if you can
screenshot the keypad in two materially different resting states, we have failed."*
`Keypad.basic()` and `Keypad.scientific()` are exactly two resting states.

### G-2 · The fourth flick, `.↑π`, does not exist.
`keypad/Keypad.kt` — `key("point").flickUp == null` (verified).

The design commits **four** flicks, not three: `÷↑√ · ×↑xʸ · −↑a⁄b · .↑π`
(§02 strip; `['.','num',ic('.'),ic('π')]` in `buildKeys`). The brief to me named
three. The engine implements three. The fourth is the only flick that inserts a
*constant* rather than a *structure* — which is why it was probably lost: it does not
fit the "operator pairs with the structure you reach for next to it" story that
generated the other three.

### G-3 · No `func` node — so function keys are text, and D-018 breaks.
`entry/Node.kt` — `Node = Atom | Fraction | Power | Root | Paren`. There is no `Func`.

The design has `{t:'func', name, arg:[]}` as a first-class structure with an `arg`
*field*. Consequences, all verified:

```
sin key -> toInput = 'sin('  nodes = [Atom(s), Atom(i), Atom(n), Atom(()]
then ONE backspace -> 'sin'  nodes = [Atom(s), Atom(i), Atom(n)]
```

- **One keystroke produces four atoms and needs four backspaces to undo.** D-018's
  axiom is *"each structure was created by exactly ONE keystroke — which is precisely
  what backspace inverts."* Every function key on my keypad violates it. The design's
  model does not: all fields empty → the whole `func` node goes.
- **The engine cannot render `asin` as `sin⁻¹`.** The design maps
  `{asin:'sin⁻¹', acos:'cos⁻¹', atan:'tan⁻¹', cbrt:'∛'}` — a *display form distinct
  from the input form*. `Atom('a'),Atom('s'),Atom('i'),Atom('n')` cannot carry that.
  §11 teaches `sin⁻¹` on the drawer; §02 renders it in the expression. Not expressible.
- **`sin(` does not parse.** Transiently fine — but see **F-2**, because it also
  falsifies an invariant `Node.kt` claims in writing.

### G-4 · Six units in the browser do not exist. Three are Indian.
`units/Units.kt` — verified absent: **`guntha`, `maund`, `seer`** (§09 *indian*),
**`ly`, `nmi`** (§09 *length*), **`ac`** (§09 *area*).

§09's entire job is *"learning that 'crore' and 'ly' and 'tola' are words the engine
parses. Once you know, you type them."* A browser that teaches `ly` and `guntha` to a
user who then types them and gets `UnknownSymbol` inverts the section's purpose — it
teaches vocabulary the engine does not speak. `ly` and `guntha` are named in the
design's own prose (§09 note 1, note 2).

Note the design's honesty about `bigha`: *"≈ 2500 m² · varies"*. The engine has
`2529.2851 m²` flagged *"pucca; region-dependent"*. Both are right and they disagree
by 1.2%; §09's "honest 'varies by state' notes where the truth is regional" is the
better instinct and the engine's single number cannot express it.

### G-5 · No digit grouping anywhere in `core/`. **`12,34,567` is unimplemented.**
`format/Formatter.kt` — `exact()` returns `x.num.toString()`. Verified:
`12345*6789 => 83810205`, not `8,38,10,205`.

§02 callout 4 is *"Indian grouping, native — 12,34,567 — three from the right, then
pairs. **Flip GROUPING up top and the same engine regroups** to 1,234,567. Built for
the harder system first."* §10 ships it as a live setting. The engine has neither
grouping mode, no `locale` setting, and no Devanagari numeral mapping (`०१२३४५६७८९`,
§10 *Numerals*).

The design's `groupInt`/`dig` are 2 lines of JS. The gap is not difficulty — it is
**ownership**: "the same engine regroups" asserts this lives in the engine. If M7
implements grouping in Compose, the tape, the graph tick labels (`graph/Axes.kt`), the
matrix cells and the unit browser each need it, and Article 12 says that is four
copies of one rule. `Formatter` is the only place it can live.

### G-6 · The design's tape has no dependency model; the engine's has no cap.
Design: `history` is `{nodes, ans, exactSurd, value}`, **capped at 6** (`.slice(-6)`),
with **no dependency edges**. Its reuse snackbar says *"1 result reused · dependents
recomputed"* — but nothing computes dependents; there is nothing to compute them from.
§02 promises *"Live-recomputes any dependents"* and the demo cannot deliver it. The
engine can (`tape/Tape.kt`, Kahn sort over `#n`/`ans` edges).

This is the one place the **engine is ahead and the design under-promises**. But it
cuts both ways: the design has **no `#n` reference syntax** anywhere — no key, no
gesture, no display of an entry's number. The engine's entire dependency graph is
reachable only by typing `#3`, and **the design provides no way to type it**. A tape
that live-recomputes dependents, in a UI where dependencies cannot be created, is
dead code. Someone has to decide whether `#n` is a v1 surface.

### G-7 · Ambiguity, `Unverified` roots, and `NotPolynomial` have no surface.
See §3 — the design has no widget for any of them.

### G-8 · Smaller, but real
- **No "is this exact form worth showing?" predicate.** The design gates the exact
  display on `_isNice` (≤3 surd terms, all of n/d/r ≤ 100 000) and falls back to
  decimal otherwise; `S⇔D` only appears when `exactInteresting`. The engine always
  returns the exact form, however monstrous. No equivalent of `_isNice` exists.
- **`Reduce motion`, `High contrast`, `Dynamic type`, `Force RTL`** (§10, group one —
  "accessibility is not a sub-page") have no representation in `eval/Settings.kt`.
  Arguably all M7-local; **`Reduce motion` is not**, because §04's reveal is a
  *viewport* animation and reduce-motion changes it to a 120 ms cross-dissolve.
- **`Ans` with nothing computed.** Design: `flatten` emits `'('+(this.ans||0)+')'` —
  **`ans` silently becomes `0`**. The engine returns `null` and `Registers` documents
  *"empty is not zero"* at length. The design does the thing Article 11 forbids, in
  one `||`. Low impact (you rarely press Ans first) but it is the exact shape.

---

## 3 · What the engine does that the design does not know about

**The design has exactly one failure state.** `evaluate()` ends:

```js
} catch(e) { this.result = '—'; }
```

One `catch`, one em-dash, for every way a calculation can fail. Its tape entries carry
`{nodes, ans, exactSurd, value}` — **no state field at all**. Every entry is Computed
or `'—'`. This is the collapse you predicted, and it is worse than "no visual for
Blocked": there is no visual for *anything*.

| Engine state | Where | Design surface |
|---|---|---|
| `Failed(error)` | `tape/EntryValue.kt` | `'—'` — no message, no span, no cause |
| `Blocked(by)` | `tape/EntryValue.kt` | **none** — would render `'—'`, identical to Failed |
| `Cycle(members)` | `tape/EntryValue.kt` | **none** |
| `Ambiguous(candidates)` | `route/RouteResult.kt` (D-016.5) | **none** |
| `Empty` / `Stale(previous)` | `tape/EntryValue.kt` | none / none |
| `Sample.Undefined(cause)` vs `Unevaluated(error)` | `graph/Sample.kt` | both → `y: '—'` |
| `RootResult.Unverified(...)` | `graph/RootFinder.kt` | **none — the graph shows no roots at all** |
| `Extraction.NotPolynomial(reason)` vs `Failed(error)` | `solve/Poly.kt` | `'—'` |
| `EvalError.{Domain, Overflow, Arity, UnknownSymbol, DivideByZero, Unsupported}` | `error/EvalError.kt` | `'—'` |
| `StoreResult.Blocked(reason)` (names the collision) | `tape/Registers.kt` | none — no register UI at all |
| Registers A–F, M | `tape/Registers.kt` (D-016.4, your ruling) | **none — no key, no panel, no surface** |
| `#n` tape references | `lex/Tokenizer.kt` | **none** |

Concretely, in the design's UI as specified, all of these render as the same dash:

- `1/0` → DivideByZero
- `#5 = #3*2` where `#3` broke → **Blocked** — *not broken, waiting*
- `#1 = #2+1, #2 = #1*2` → **Cycle** — *no computation exists*
- `sin(1,2,3)` → Arity
- `frobnicate(2)` → UnknownSymbol
- `x+y=5` → **Ambiguous** — *two readings, not an error* (D-016.5)
- `2^999999999` → Overflow
- `√(-1)` with complex off → Domain

Eight distinct engine states; one dash. The engine spent seven catches making these
distinguishable *because a count that cannot tell them apart is a lie with a number on
it* (Article 11). The design would put them back in the same box on the way to the
screen. **The engine's `Blocked` names its blocker precisely so the UI can point at #3
and grey #5 — the design has no widget that could accept that information.**

Two register-shaped notes: the design's `S⇔D` and `Ans` chips sit in the display, and
its keypad has no `Ans` key — the engine's `Keypad.basic()` has one (see C-4). And
A–F/M were *your* D-016.4 ruling; the design does not know they exist.

---

## 4 · Where the two CONTRADICT — the dangerous ones

Ranked by how wrong the user's screen gets.

### C-1 · **`0.1 + 0.2 = 0.3` vs `3/10`. The design's own proof frame breaks.** 🔴
The design (`formatExact` → `_terminates(d)`) renders a **terminating** rational as a
**decimal**, and only a non-terminating one as a stacked fraction:

> callout 1: *"√8 resolves to 2√2, ⅓×3 to exactly 1, **0.1+0.2 to exactly 0.3**"*
> §02 Try-it: `0.1 + 0.2 → 0.3` · §12 landscape tape: `0.1 + 0.2 | 0.3`

The engine renders **every** non-integer rational as `n/d` (verified):

| input | design shows | engine renders |
|---|---|---|
| `0.1 + 0.2` | `0.3` | **`3/10`** |
| `5 km + 300 m` | `5.3 km` (§09, in prose) | **`53/10 km`** |
| `2 crore + 5 lakh` | (implied `2.05 crore`) | **`41/20 crore`** |
| `1/3` | `⅓` stacked | `1/3` ✓ agree |

Both engines are exact. They disagree on what exact *looks like*, and the design's
headline teaching frame is the casualty: a calculator that answers `3/10` to
`0.1 + 0.2` has not demonstrated that it beat floating point — it has demonstrated
that it is strange. **`53/10 km` is the sharpest case**: it is mathematically perfect
and no human would write it. The design's rule — *terminating decimal in, terminating
decimal out* — is the better one, and it is a `Formatter` rule, so it is one function
in one file. But it is a **decision about the product's voice, not a bug**, which is
why it is yours and the design agent's, not mine.

### C-2 · **⌫ is in the top-right corner — the exact mistake §07 diagnosed and fixed.** 🔴
Verified: `Keypad.basic().rows[0] = [lparen, rparen, backspace, divide]`.

§07's rescore, in full:

> *"my first clean pad parked **⌫ at the top-right corner, the worst-reach row for a
> right thumb. Wrong for a top-5 key.** It now sits in the bottom arc, one slot left of
> `=`, where the thumb rests — Fitts holds at 9 because a frequent target moved toward
> the pivot, not away."*

The design **already made this mistake, caught it, and fixed it.** The engine's
keypad, built yesterday without sight of the design, made the identical mistake and
has not fixed it. `thumbFacts` names it as a design fact: *"⌫ in the bottom arc — not
the top corner."*

| slot | design | engine `basic()` |
|---|---|---|
| row 0 | `fn` `(` `)` `÷` | `(` `)` **`⌫`** `÷` |
| row 4 | `0` `.` **`⌫`** `=` | `0` `.` **`ans`** `=` |

Two independent errors that happen to cancel into a swap: the engine put `ans` where
`⌫` belongs and `⌫` where `fn` belongs. The design has **no `Ans` key on the pad** —
`Ans` is a chip in the display area, next to `S⇔D`. This is the cheapest fix on this
page and it is worth 9 points of Fitts.

### C-3 · **`(` is two different nodes depending on how you type it.** 🟠
Verified:

```
'(' key  -> nodes = [Paren(body=Row([]))]        // Editor.typeParen
Insert("sin(") -> nodes = [..., Atom(char=()]    // Editor.insert
```

`entry/Node.kt` states, in writing: *"**Paren** — Created by `(`. **Balanced by
construction — unbalanced parens are unrepresentable.**"* That claim is **false**.
`Editor.insert(s, '(')` makes an `Atom('(')`, and unbalanced parens are perfectly
representable — my own function keys do it on every press.

So one concept has two implementations inside one file — Article 12's exact sin, in
the module built to honour it. See **F-2**.

The design has no `Paren` node at all: `(` and `)` are `util` keys inserting plain
characters. So the two models also disagree *visibly*: the design's `(` shows `(`;
the engine's `(` shows `()` with the cursor inside. Which is right is a real decision
— balanced-by-construction is stronger, and `2*(3+4` is a state the design permits and
the engine (partly) forbids.

### C-4 · **The engine rebuilt a direction the design disqualified.** 🟠
`Keypad.scientific()` is an always-on scientific pad. The design's redline table:

> `Always-on scientific pad` — **DISQUALIFIED** — *"User A meets 30+ scientific keys on
> the default surface. The fx-991's answer — and our own first build."*
> *"Disqualified is dead — we did not let a strong Fitts score argue either back in."*

The engine offers `layouts() = [basic, scientific]` — two resting states, which §04's
engineer's test explicitly calls failure. In fairness the engine never decided
`scientific()` was the *default*; that is an M7 decision. But the model as it stands
makes the disqualified direction one function call away, and names it as a peer of
`basic()`. See G-1.

### C-5 · **The units browser teaches `g` and `h`, and the engine reads them as gram and hour.** 🔴
Verified:

```
c    => ERR UnknownSymbol
g    => 1 g      (GRAM — design's browser says "gravity 9.80665 m/s²")
h    => 1 hr     (HOUR — design's browser says "Planck 6.626×10⁻³⁴ J·s")
phi  => ERR UnknownSymbol
NA   => ERR UnknownSymbol
pi   => π ✓
e    => e ✓
```

§09 note 3: *"π, e, c, g, h, Nₐ, φ — same reference surface, because **'what symbol
does the engine know' is one question** whether the answer is a unit or a constant."*

In this engine that is **two questions with two different answers**. `Units` is
reachable from the expression language; `constants/Constants.kt` is **not** — it is
only exposed via `Calculator.physicalConstant()` / `mathConstant()`, a typed API the
router never consults. `Constants.math("phi")` exists and is unreachable by typing
`phi`. That is a registry orphaned from the language — Article 12's shape, in my
engine, and the design's browser is what exposed it.

**`g` and `h` are the dangerous two**, because they do not error. A student reads
"g = 9.80665 m/s²" in the browser, types `g`, and gets **1 gram** — a wrong meaning,
silently, with a plausible unit attached. `9.8*g` reads as a mass. Of the seven
symbols §09 promises, **two work, two lie, three are unknown**.

### C-6 · Root order: design shows `x = 2, 3`; engine returns `3, 2`. 🟡
Verified: `x^2-5x+6=0 => ROOTS x = 3, 2`. §03 frame ③ shows `x = 2, 3`. Cosmetic,
but it is in the design's hero frame, and ascending is what a student expects.

### C-7 · Exact trig at special angles: the design has a table, the engine has a kernel. 🔴
The design carries `_sinDeg`: 16 exact entries (0°…330°), so `sin(45°)` is *exactly*
`{n:1,d:2,r:2}` = √2/2. The engine sends 45° through `SymConst(1/4, π)` into
big-math's BigDecimal kernel and gets a **`Dec`** (verified: `sin(45) => 0.707106781187,
num class = Dec`). See **F-1** — this is where it stops being cosmetic.

---

## Focus areas you named

### The flick — does the editor model support it?

**Yes. The gesture is not the problem; the palette is.**

The design's flick is:

```js
press(prim, alt) {
  down: e => { this._y = e.clientY }
  up:   e => { const dy = e.clientY - this._y; if (alt && dy < -14) alt(); else prim(); }
}
```

Pointer-down records Y; pointer-up compares. **A flick is not a gesture stream — it is
a binary choice resolved once, at lift** ("resolved the instant your thumb lifts",
§06). It commits to *either* `prim()` *or* `alt()`, exactly once, and both are ordinary
edit calls.

That maps onto `Keypad` cleanly and by construction:

- `Key.tap` ↔ `prim`, `Key.flickUp` ↔ `alt` — the same shape.
- `KeyAction` is closed over operations `Editor` actually has, so a flick cannot
  request an edit the editor lacks (`Keypad.apply` is total and exhaustively checked).
- The three structure flicks are already right: `÷↑Root`, `×↑Power`, `−↑Fraction`,
  each with its label (`√`, `xʸ`, `a⁄b`), verified against the design's `buildKeys`.
- `Keypad.flickUp` on a key with no flick is a **no-op, not a fallback to tap** —
  which matches `if (alt && dy < -14) alt(); else prim()` only when `alt` exists. The
  design falls back to `prim()`; the engine does nothing. **Minor divergence**: on a
  key with no alt, the design treats an upward swipe as a tap. Worth a decision.
- The 14 px threshold and the pointer-capture are view concerns; M7's, correctly.

So: **the editor model supports the flick, and does not assume an input model I didn't
build.** The design assumes a 2D math editor, and that is what `entry/` is.

**The failure is that the flick is only half the shipped answer.** The 8.9 composite
belongs to *"Flick + contextual fn palette"*, and its Hick score (8) and Recognition
score (8) — the two the palette carries — are what separate it from long-press (4 and
3). The engine models the flick and answers Hick with a **second static keypad**, which
is the redlined direction. **The gesture is fine; the doorway is missing** (G-1), and
the fourth flick is missing (G-2).

### The grid → axes reveal — do exact `lerp` and the design's scrub meet?

**Yes, and better than the design asks — but they do not meet where the design thinks.**

What the design specifies (`drawReveal`, `playReveal`):

- `t ∈ [0,1]`, driven by `requestAnimationFrame` over `dur = 1800 ms`, **or** by a
  drag on a range input (`onScrub`). *"Drag to freeze any frame."* So `t` is a pure
  function of scrub position — reversible, seekable, stateless.
- Smoothstep easing `ss(a,b,x) = u*u*(3-2*u)`, applied on **staggered sub-intervals**:
  axes fade `ss(0.12, 0.72, t)`, tick labels `ss(0.5, 1, t)`, keypad falls away
  `1 - ss(0.05, 0.62, t)`, the curve draws over `(t-0.24)/0.7`.
- The grid **never moves**. Verified in the code: `xmin=-5, xmax=5, ymin=-3.4, ymax=6.6`
  are **constants** inside `drawReveal`. Gridlines are drawn at fixed 0.5 and 1.0
  spacing on every frame regardless of `t`.

**That last point is the finding.** The reveal is *not a viewport transition at all.*
Nothing pans, nothing zooms, no coordinate system interpolates. The grid you read on
*is already the graph's grid*, at the graph's scale, the whole time — and the "reveal"
is **opacity choreography**: axes fade in, labels fade in, keypad fades/drops out,
curve draws left-to-right. That is precisely why the idea lands ("*it reveals it was
always graph paper*") and precisely why **`Viewport.lerp` is not what powers it**.

So they meet, but not as expected:

- `graph/Viewport.kt`'s exact `lerp`/`zoom`/`pan` is **not needed for §04**. The
  design's reveal needs no viewport interpolation whatsoever. It is unblocked.
- What §04 *does* need from `core/` is that the reveal's fixed frame and the graph's
  live frame **agree**, or the curve will jump the instant the reveal ends and the
  real viewport takes over. The design's reveal frame is `x[-5,5] y[-3.4,6.6]`; its
  graph default (`gvDefault`) is `x[-6.6,6.6] y[-4.2,4.2]`. **They already disagree.**
  In the demo this is invisible because they are separate canvases. In the app they are
  the same surface, so the curve will visibly snap at `t=1`. Nobody has noticed yet
  because the two frames have never had to be the same object.
- `Viewport.lerp` *is* exactly right for pinch-zoom and pan (§05), and for the
  reduce-motion cross-dissolve (§04 note ii), which is a 120 ms *state swap*, not an
  animation — so it needs the two endpoint viewports to be exact, which they are.
- Tick labels: `graph/Axes.kt` produces exact 1/2/5×10ⁿ ticks and throws past
  `MAX_TICKS`. The design's `niceStep` is the same 1/2/5 family (`n<1.5?1:n<3?2:n<7?5:10`)
  — **independent agreement**. But the design's reveal draws ticks at *hardcoded
  integer* steps, bypassing `niceStep` entirely, so at `t=1` the reveal's ticks and the
  live graph's `niceStep` ticks can differ. Same snap, second cause.

**Verdict: the viewport can express everything §04 wants; §04 wants less than the
viewport offers. The risk is not capability — it is that the reveal's frame and the
graph's frame are two hardcoded constants that must become one value, and today they
are different numbers.**

### Article 11 states — will the UI collapse them?

**As specified, yes — into one dash.** Fully answered in §3 above. Summary: eight
distinguishable engine states, one `catch { this.result = '—' }`, and tape entries with
no state field. The design was written before `Blocked`, `Cycle`, `Unverified` and
`Ambiguous` existed and has no widget that could accept the information they carry —
not even `Failed`'s message, span and cause, all of which the engine has and the design
drops. **`Blocked` carries *which* entry blocked it for the sole purpose of letting the
UI point at #3 and grey #5. There is currently nowhere for that to go.**

---

## Two engine bugs, found while probing. Not design gaps — mine.

### F-1 · 🔴 **`sin(45) = √2/2` answers FALSE.** The engine denies a true statement.

```
sin(45)              => 0.707106781187   [num class = Dec]
sin(45) = sqrt(2)/2  => VERDICT false        ← WRONG
sin(30) = 1/2        => VERDICT true         ← right, by luck: 0.5 is exact in binary/decimal
cos(60) = 1/2        => VERDICT true         ← same luck
```

Angle conversion is exact — 45° becomes `SymConst(1/4, π)` — and then `NumMath.sin`
runs it through big-math's BigDecimal kernel, which **returns a `Dec`**: a truncation
at `computeMc` digits. Compared against the exact `Surd` √2/2, the truncation is
genuinely unequal, so `valuesEqual` correctly reports `false` about two values that are
genuinely different — and the *engine* is what made them different.

This is the failure mode D-016.5 singles out as the worst: *"Comparing only same-type
pairs made a true statement return false — a confident wrong answer, which D-016.5
forbids above all."* That bug was fixed for units; **the same shape is alive in trig**,
reached by a different route. It is also an Article 3 hole: exact-first degrades to
`Dec` the moment a trig function touches a special angle, and `sin(30)` is a `Dec`
that merely *prints* as `0.5`.

The design's `_sinDeg` table (16 entries) is the fix, and the design got this right
where the engine did not. **This is a `sin(45)` a student types in week one.**

### F-2 · 🟠 `Node.kt` asserts an invariant that `Editor.insert` violates.
`entry/Node.kt` documents *"Balanced by construction — unbalanced parens are
unrepresentable."* `Editor.insert(state, '(')` produces `Atom('(')`. The invariant is
false, one concept has two implementations (C-3), and my M6 test *"every keypad key
produces something the editor accepts"* passed **vacuously** — it asserted only that
`press` doesn't throw, never that the result validates. `Keypad.press(sin)` yields
`sin(`, which does not parse. A weak test hid it.

*(Also flagged: `tan(90) => -4.08648721119e47`. A finite, negative, enormous number for
an undefined value. The design is wrong here too — `Math.tan(π/2)` gives `1.6e16` — so
neither side has a story for undefined-at-a-pole, and `graph/Sample.kt` has exactly the
vocabulary this needs. Not filed as a bug pending your call on whether `tan(90°)` should
be `Domain` or `Undefined`; a Casio says Math ERROR.)*

---

## What I did not verify

- **Every visual claim** — layout, colour, type, motion, thumb-arc geometry. Out of
  scope for an engine cross-check and not assessable here.
- **Okabe-Ito colourblind safety** (§01/§05) — the palette matches the published
  Okabe-Ito values by inspection; not independently tested.
- **§07's ROC weights and composites.** I checked the *arithmetic frame* (ROC for n=6
  → 41/24/16/10/6/3 is correct) but not the scores, which are judgement.
- **The reveal's perceptual quality.** I read its code; I have not seen it move.
- **CODATA values** in `constants/Constants.kt` against the design's browser values
  (`c`, `h`, `Nₐ` agree to the digits shown; `g` and `φ` are not comparable because the
  engine cannot reach them by name — C-5).

---

## Summary table

| # | Finding | Kind | Severity |
|---|---|---|---|
| F-1 | `sin(45) = √2/2` → **FALSE**; trig degrades exact→`Dec` | engine bug | 🔴 |
| C-5 | Units browser teaches `g`/`h`; engine reads gram/hour. Constants registry orphaned from the language | contradiction | 🔴 |
| C-1 | `0.3` vs `3/10`, `5.3 km` vs `53/10 km` — the proof frame breaks | contradiction | 🔴 |
| C-2 | ⌫ in the top-right corner — the mistake §07 already fixed | contradiction | 🔴 |
| C-7 | Design has an exact-trig table; engine has a kernel | contradiction | 🔴 |
| §3 | Eight Article 11 states → one `'—'`; no widget for Blocked/Cycle/Ambiguous/Unverified | design gap | 🔴 |
| G-1 | No `fn` palette model — half the 8.9 thesis | design gap | 🔴 |
| C-4 | `Keypad.scientific()` rebuilds the disqualified direction | contradiction | 🟠 |
| C-3 / F-2 | `(` is `Paren` or `Atom` depending on path; `Node.kt`'s invariant is false | engine bug | 🟠 |
| G-3 | No `func` node → one keystroke, four backspaces; `sin⁻¹` unrenderable | design gap | 🟠 |
| G-5 | No digit grouping in `core/` — `12,34,567` unimplemented | design gap | 🟠 |
| G-6 | Design's tape has no dependencies; engine has no way to type `#n` | mutual gap | 🟠 |
| G-4 | `guntha`, `maund`, `seer`, `ly`, `nmi`, `ac` absent | design gap | 🟡 |
| G-2 | Fourth flick `.↑π` missing | design gap | 🟡 |
| C-6 | Roots `3, 2` vs `2, 3` | contradiction | 🟡 |
| G-8 | No `_isNice` gate; `Ans||0`; reduce-motion absent from Settings | mixed | 🟡 |

**Not resolved. Not coded. The design agent and you decide what gives.**
