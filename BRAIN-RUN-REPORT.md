# BRAIN RUN REPORT — B3 (EDITOR) + B2 (TAPE)

> Engine at `c32176a`. B3 battery committed at `1608264`, B2 at `3eabe46`.
> **Nothing was applied.** Every finding below is triaged and waits for an owner
> ruling; the failing battery rows are left red on purpose — they are the evidence.

## 0 · Totals

| Battery | Cases | PASS | FAIL | PROBE | CRITICAL | Chunk gate |
|---|---|---|---|---|---|---|
| **B3 EDITOR** | 520 | 509 | **0** | 11 | **0** | chunk 1: 98/0/2, 0 critical → proceed; chunk 2: 411/0/9 |
| **B2 TAPE** | 500 | 462 | **3** | 35 | **2** | chunk 1: 99/0/1, 0 critical → proceed; chunk 2: 363/3/34 |

Every event/op of every case ran under always-on invariants (B3: I-CURSOR, I-PAREN,
I-FLAT, I-SHRINK; B2: I-DEPS, I-IDS, I-SILENT, I-FORBIDDEN, I-CLASS). **Zero
crashes, zero hangs, zero silent value changes, zero forbidden states, zero cursor
corruptions** across 1020 cases and roughly 40 000 individual operations/events.
Three earlier chunk-2 reds were the harness's own arithmetic (triangle is 2^28, not
2^27; mixed-graph cascade is 9, not 10) — corrected against the engine's derivation
and re-run; they are not findings. Results: `battery/build/battery/{b3,b2}-chunk-*.json`.

Severity ladder (B1's, as instantiated in each brain doc): **CRITICAL** =
screen-vs-evaluated divergence / silent value change / dangling-or-rebound
reference / crash / forbidden state. **MAJOR** = rule divergence without content
loss, contract breach, misnamed blocker/member. **MINOR/PROBE** = decided-silence
observations.

---

## 1 · FINDINGS

### F-1 · CRITICAL — the REBOUND REFERENCE is real
**Evidence:** `B2-R-025` (`#3+1` committed when the tape has 1 entry → honestly
`Failed`; two appends later a brand-new, unrelated entry receives id `#3` and the
old expression **silently computes 21 against it**), `B2-R-026` (same across a
larger gap, adopted value 100), `B2-R-027` (after adoption, edits of the stranger
keep flowing into the old formula), `B2-R-028` (undoing the adopting append
restores the dangle).

**Root cause:** `Tape.referencedEntries` filters `#n` by *current existence only*
(`s.entries.any { it.id == id }`). A reference is a bare integer with no notion of
"the entry I meant", so the first future entry that wears the number is adopted.
Note the deletion path does **not** rebind (`B2-X-044`: ids are never re-issued, so
a reference orphaned by delete stays `Failed` forever) — only the type-a-number-
too-early path rebinds.

**Fix options:**
(a) commit-time guard — reject/flag committing an expression whose `#n` does not
exist yet (UI: the badge only inserts existing ids, so honest users never type
future refs; hostile ones get a typed refusal);
(b) generation-tag references (store `#n@gen`) so adoption is structurally
impossible;
(c) accept and document (ids as positional labels).
**Recommendation:** (a) now — one check in the session's commit path, no schema
change; (b) if `#n` ever becomes persistent across sessions. Against (c): this is
the named harm verbatim — a value whose meaning changed silently.

### F-2 · MAJOR — the fraction key SPLITS a badge-inserted reference
**Evidence:** `B2-R-030`: insert `#1` (the D-031.7 badge route), press a⁄b →
`wrapTrailingOperand` captures only the digit (`'#'` fails `isOperandChar`), tree
becomes `[Atom('#'), Fraction([1],[2])]` → linearises `#(1)/(2)` → parse error →
`Failed`. The user watched their reference go into a fraction and got a broken
entry. Same applies to `^` (power), and to multi-digit refs (`#12` splits as
`#1|2`... i.e. `#` + captured `12`).

**Root cause:** `Editor.isOperandChar` (letters/digits/`.`) predates entry
references; the capture run has no concept of `#`-led tokens. Contrast: typing the
ref *inside* an already-open field is fine (`B2-C-042`, `1/(#1)` = 0.2 passed).

**Fix options:** (a) extend `trailingOperandStart` to absorb a `#` immediately
preceding a captured digit-run; (b) make the badge insert an atomic `Ref` node
(a new `Node` case, one keystroke = one node, D-018-clean, deletes whole);
(c) forbid structure keys right after a ref (dead keypress — the class D-019 killed).
**Recommendation:** (b) is the honest model (a reference is one thing, not three
characters) and gives Rule-3 deletion for free; (a) is the two-line stopgap. Not (c).

### F-3 · MAJOR (documentation) — D-016.1's own example is refused by the engine
**Evidence:** `B2-U-004`: `5→x` → `Failed` ("x is an equation unknown and cannot be
assigned; registers are A…F, M"). D-016.1 and D-031.8 both write *"storing is
`5 → x`"* as the canonical example; `ReservedNames` (D-016.4) correctly refuses
unknowns. The engine is self-consistent; the decision text contradicts it.
`5→A` behaves exactly as promised (`B2-D-066`, `B2-A-006`, `B2-S-035` — Stored
carries value 5, dependents and `ans` compute).

**Fix options:** (a) amend the D-016.1/D-031.8 example text to `5 → A`;
(b) widen assignment to free lowercase names. **Recommendation:** (a) — x/y/z as
solver property is load-bearing (D-016.4's whole point); the text is what drifted.

---

## 2 · PROBE CATALOG — measured observations awaiting rulings

### B3 (11 probes, none red)
| Probe | Observation |
|---|---|
| S1 (`B3-D-060`, B-route cases) | `)` is a **silent no-op** everywhere except directly inside a `Paren` BODY — including inside `sin(…)`'s ARG. The exact "dead keypress" shape D-019 killed for ⌫. |
| S2 (`B3-A-005/017`) | a⁄b / xʸ after a parenthesised group captures the WHOLE structure — `((3))/()` — coherent, but undecided in any D-number |
| S3 (`B3-D-006..010`) | a structure key over a SELECTION deletes the selection first, then captures from what remains (observed `()/()` — nothing left to capture). Visible per Rule 3, but nowhere ruled |
| FM-11 (`B3-E-083`) | pasted `sin(30)` = 4 atoms + `Paren`; the sin key = one `Func`. **Identical linearisation, identical pixels, different ⌫ cost (5 vs 2)** |
| B1-0035 family (`B3-E-084`, `B3-F-022`) | a 2000-term flat chain through `pasteText` **survives** — `parseProgram` handles what B1 saw the *evaluator* die on. The editor's paste door is safe at this size |

Also measured green and worth naming: depth-500 keystroke nesting builds,
linearises, validates, navigates and unwinds cleanly on every chain type
(`B3-F-001..014`); the D-018 inversion axiom holds exactly (24/24); the revised
F5/F6 select-then-delete holds on every route into a boundary (12/12); hold-⌫
reaches the empty state on every tree tried; →/← are mutual inverses everywhere.

### B2 (35 probes)
| Cluster | Observation |
|---|---|
| Bystander-in-leftover (`B2-C-003/009/015/021/027`, `B2-T-058`, `B2-R-035`, `B2-S-033`) | an entry that merely *depends on* a cycle lands in Kahn's leftover and is labelled `Cycle`, members including itself. D-032-as-written says yes; the design blurbs ("Blocked points at the culprit", "Cycle names the loop") say no |
| Disjoint loops merged (`B2-C-035/036/037`) | `#1↔#2` and `#3↔#4` produce ONE members list `[1,2,3,4]` on every badge |
| Updated-contract (`B2-E-024/025`, `B2-S-024/025`, `B2-V-027`) | `Transaction.updated` counts VALUE changes: editing `2` to `1+1` reports **0 results updated** — the D-018 snackbar would say so after a real edit |
| `previous` does not survive (`B2-S-011/012`) | `Failed.previous` is populated on the breaking transaction and **null one transaction later** (`previousOf` reads the already-broken value); `Cycle` never carries previous. Design: *"keeps the previous number so you read 'was, now updating'"* |
| Blocked-by-success (`B2-S-016/017`, `B2-D-068`) | a dependent of a *successful* solve/verdict is `Blocked(by)` — the design's `why:'it failed'` copy would be a lie for this cause |
| Register isolation (`B2-V-001/003`) | `5→A` then `A+1` on the next row → `Failed`: `Tape.evaluate` builds a **fresh Environment per entry**; only `#n` and `ans` flow. The registers panel (session env) and the tape disagree about what `A` means |
| M-2's exemplar cannot run (`B2-U-005`) | the design's `Unverified` example `x³−4x²+x+6=0` is **`Failed`** — no cubic solver. M-2 needs an engine capability, not just an enum case |
| Undo-of-append (`B2-E-040`) | `UndoOutcome.restored = 0` (the vanished row is absent from the restored state, so `changedIds` counts nothing) |
| ans-rebind (`B2-K-007`, `B2-S-001..004`, `B2-A-014`) | deleting a middle row rebinds every `ans` below — always **named in `updated`** (I-SILENT held everywhere), but the *meaning* moved; `#n` and `ans` diverge exactly here |
| Misc | `i*i` computes (−1) under defaults; `sqrt(-1)` is a typed refusal (D-011 gate); `" 1 "` parses; `x=#1` solves with a live dependency edge; equation-with-zero-unknowns referencing the tape computes a verdict |

---

## 3 · WHAT THE BATTERIES DID **NOT** FIND

No crash on any keystroke-reachable editor state to depth 500. No cursor
invalidity. No paren-atom leak. No `toInput` divergence from the independent
linearizer. No screen-vs-evaluated value divergence on 72 hand-computed
constructions (including fraction-adjacency precedence, power towers, the
`-2^3` reading, implicit multiplication, Devanagari round-trips). No silent value
change on any tape event (the I-SILENT tripwire never fired — every changed value
was named in its transaction). No `Stale` or `Ambiguous` ever constructed. No id
ever re-issued, including across undo. `NoMoreUndos` at exactly the right depth;
undo/redo round-trips are byte-exact snapshots at depth 50.

---

## 4 · OWNER RULINGS REQUIRED (numbered, for the close-out)

1. **F-1 rebound reference** — adopt the commit-time guard (reject/flag a `#n`
   that does not exist at commit)? Recommended: yes; optionally generation-tags later.
2. **F-2 reference splitting** — atomic `Ref` node in the entry model (preferred)
   or extend the operand-run to absorb `#`? Either way, a ruling on what a⁄b/xʸ
   capture does to a reference.
3. **F-3 D-016.1 text** — amend the decision's `5 → x` examples to `5 → A` (engine
   behaviour keeps D-016.4)? Recommended: yes.
4. **S1 `)` dead keypress** — should `)` close/exit a `Func` ARG (and other
   fields), or stay Paren-only? The D-019 argument (no invisible keypresses) cuts
   against the current no-op.
5. **Cycle membership semantics** — move to SCC-based members (a bystander becomes
   `Blocked(by cycle-member)`; disjoint loops get separate member lists), or ratify
   leftover-as-written into D-032? The design blurbs favour SCC.
6. **Updated-contract** — is "0 results updated" acceptable snackbar copy for a
   same-value edit, or should the count be source-based?
7. **`previous` retention** — should `Failed/Blocked.previous` persist across
   subsequent transactions (carry-forward when the prior value was already broken)?
   Design intent ("was, now updating") says yes.
8. **Blocked-by-success wording** — the tape row's `why` copy must distinguish "it
   failed" from "it produced no referenceable value" (solve/verdict deps).
9. **Hold-⌫ vs Rule 3** — during 70 ms repeats, a populated structure's selection
   flash is sub-perceptual. Should hold-repeat pause (re-arm at 380 ms) when it
   selects a structure? (B3 measured the mechanics; the visibility question is UX.)
10. **FM-11 dual representation** — normalise pasted function text into `Func`
    nodes, or accept two trees with one look and two ⌫ costs?
11. **Register scope on the tape** — is per-entry environment isolation (registers
    do not flow row-to-row through the tape's own recompute) the intended model,
    with persistence owned solely by the session env? Needs an explicit statement.
12. **M-2 `Unverified`** — requires a cubic/numeric root path before the 8th state
    can ever be produced; decide the capability, not just the enum.

---

*Method note: every FAIL above survived the independent classifier (B2Reference,
written from D-032's text) agreeing with the engine — i.e. the reds are semantic
findings above the transition table, not classifier noise. The two corrected
harness errors are recorded in this file and in the B2 commit message.*
