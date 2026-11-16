# BRAIN B2 — THE TAPE

> The system deciding **what the user believes ran**. The named harm this brain exists
> to prevent: **the user trusts a value whose meaning silently changed.** Weight:
> HEAVY. Engine at `c32176a`. Zero main-source changes — every finding is triaged in
> `BRAIN-RUN-REPORT.md` and waits for an owner ruling.

---

## 1 · GROUND TRUTH — the files, named

| File | Role |
|---|---|
| `core/src/main/kotlin/com/vaibhav/calc/core/tape/Tape.kt` | `TapeEntry(id, source, value, dependsOn)`; `TapeState` (the undo unit, D-023); `Transaction(state, updated)` — the snackbar's count; `append`/`edit`/`delete`/`undo`/`redo`; **`recompute` = total re-derivation** via Kahn's sort; per-entry `Environment` built from dependencies' `Computed` values plus `ans` from the row above |
| `core/src/main/kotlin/com/vaibhav/calc/core/tape/EntryValue.kt` | The SEVEN states: `Computed(outcome)` (`.value` null for roots/verdicts), `Empty`, `Stale(previous)`, `Failed(error, previous)`, `Blocked(by, previous)`, `Cycle(members)`, `Ambiguous(candidates)`; `EntryId` (`#n`) |
| `core/src/main/kotlin/com/vaibhav/calc/core/tape/Registers.kt` | Read-only view over a live `Environment`; empty ≠ zero; A–F,M order |
| `core/src/test/kotlin/com/vaibhav/calc/core/TapeTransitionSpec.kt` | D-032's measured evidence: the 25 ordered pairs, blocker naming, cycle membership, unreachability of `Stale`/`Ambiguous`, D-033's cycle-of-one |
| `DECISIONS.md` D-032 | The transition graph **derived from the engine**: recompute is total re-derivation; derivation table (7 rules, precedence order); no forbidden transitions between reachable states; `Stale`/`Ambiguous`/`Unverified` unreachable or absent |
| `DECISIONS.md` D-033 | Self-reference is `Cycle([#n])`; `#99` (not on the tape) stays `Failed` — two different faults, pinned apart |
| `DECISIONS.md` D-031.7 | `#n` insertion: **tap the badge inserts the reference; tap the row body copies the nodes** — the two must not be conflated |
| `DESIGN-HANDOFF-M7-2.md` §2.3, §4.2, §4.3 | The 8-state gallery and its per-state UX contracts; the live tape's `slice(-6)` cap **in the demo**; `reuse()` = deep copy, no dependency edge |

### 1.1 THE CAP — measured first, as briefed

**The engine's `Tape` does not cap. At all.** `Tape.append` is
`current.entries + TapeEntry(...)` — no slice, no window, no eviction, and `nextId`
only ever increments. The `slice(-6)` in the handoff (§2.3, *"Retained entries are
hard-capped at 6"*) is the **design demo's** history array, not the engine. The demo
also caps by *discarding the data*; the engine keeps every entry and the design's
78 px container merely **scrolls**. Battery case B2-K-001 measures this (50 appends →
50 entries; 200 appends → 200 entries) rather than trusting the reading.

Consequences, each battery-measured:

- **"Evicted" can only mean `delete(id)`** — there is no engine path by which the cap
  removes an entry. A `#n` whose target was deleted resolves as an unknown name →
  `Failed`, never a silent dangle (D-033's second half).
- **If the UI truncates its LIST to 6 rows** (per the demo), a `#n` reference to a row
  outside the visible window still computes — the engine's truth and the visible tape
  diverge in *presentation only*. Whether an invisible-but-live reference target is
  acceptable UX is an owner question, not an engine defect.
- **Id re-use is impossible by construction**: `nextId` is monotonic and — measured —
  **not restored by `undo()`** (the snapshot is `TapeState` alone). Delete `#5`,
  append: the new entry is *not* `#5`.
- **BUT `nextId` eventually catches a dangling reference up.** `#7` typed when the
  tape has 4 entries is `Failed` — until three more appends make a *brand-new,
  unrelated* entry `#7`, at which point the old expression **silently binds to it**
  and computes. This is the **rebound reference**, the mission's CRITICAL class, and
  axis R proves whether it is reachable (B2-R-016..019).

### 1.2 Load-bearing facts pinned from the reading

1. **Recompute is a total re-derivation** (D-032). Prior state feeds only
   `previous` on `Failed`/`Blocked`.
2. **`ans` is positional.** `previousEntry` = the row above *in list order*. Deleting
   a middle row **rebinds every `ans` below it** — the dependency edge is re-derived
   each transaction. The value updates honestly (it is in `Transaction.updated`), but
   the *meaning* of an unchanged-looking expression changed. Axis S measures that the
   update list always names such entries.
3. **`Transaction.updated` = entries whose VALUE differs.** An edit that changes the
   source but lands on the same value (`2` → `1+1`) reports **0 updated** — the
   snackbar would say "0 results updated" for a real edit. Probe.
4. **Kahn's leftover is the whole non-orderable set.** An entry *depending on* a cycle
   but not in it cannot drain either — the code classifies it `Cycle(members)` with
   itself in `members`. The design says Blocked *"points at the culprit"* and Cycle
   *"can name every entry in the loop"*. Whether a bystander belongs in `members` is
   exactly the kind of divergence axis C measures (engine table vs rule text).
5. **Two disjoint cycles share one leftover** — `Cycle.members` for `#1↔#2` would
   also carry `#3↔#4` if both loops coexist. Measured in axis C.
6. **Per-entry `Environment`.** `evaluate` builds a **fresh** `Environment` per entry:
   only `#n` values and `ans` are injected. An assignment in `#1` (`5 → A`) does
   **not** make `A` visible to `#2` *through the tape* — registers live in the
   session's environment, not the tape's. Axis V measures the actual behaviour.
7. **Blocked's test is "handed me no value"**, not "failed": a dependency that is
   `Failed`, `Empty`, or `Computed`-holding-roots all block (D-032, measured).

---

## 2 · EXTREME USERS

| # | Actor | What they do to the tape |
|---|---|---|
| U1 | **The cap prober** | Runs 50, 200 entries; references `#1` from `#200`; deletes rows the UI would have scrolled away; asks what `#n` means when its target is out of sight or gone |
| U2 | **The chain builder** | `#k = #(k−1)+1` to depth 50, then edits `#1` and expects every downstream number to move — and the snackbar to say exactly how many did |
| U3 | **The fan-out author** | One input cell referenced by 50 result cells (the spreadsheet pattern); edits the input; deletes the input |
| U4 | **The cycle knotter** | Ties loops of length 2–6, self-loops, two loops at once, loops through `ans`, loops formed by *editing* a previously honest entry; then unties them one edit at a time |
| U5 | **The reference archaeologist** | Badge-taps `#3` into new work (D-031.7), deletes `#3`, un-deletes it, appends until a *new* entry wears the old number — hunting a dangle or a rebind |
| U6 | **The copy/reference confuser** | Taps the row body (copy) believing it made a link; taps the badge (reference) believing it took a snapshot; edits the original and trusts whichever number they see |
| U7 | **The undo weaver** | Edits, undoes, redoes, appends mid-history (killing the redo stack), undoes past the beginning, undoes a 50-entry cascade and expects every value back exactly |
| U8 | **The eclectic author** | Tape sources that are equations, assignments (`5 → x`), Devanagari (`१+२`), units (`5 km + #1`), percent/factorial, solve calls — the whole language, *through* the tape |

---

## 3 · FAILURE MODES — mapped to the battery axes that detect them

| # | Failure mode | Harm | Axis |
|---|---|---|---|
| FM-1 | **Silent value change**: an entry's value changes in a transaction whose `updated` list does not name it | THE named harm — the user trusts a number whose meaning moved under them | **global invariant I-SILENT**, checked on every event of every case |
| FM-2 | **Dangling reference**: `#n` resolves against nothing and produces anything but an honest typed state | Phantom numbers | **R** |
| FM-3 | **Rebound reference**: a dangling `#n` silently binds to a *later, unrelated* entry that happens to receive the old id | The worst version of FM-1 — an old formula quietly computes against new data | **R** (B2-R-016..019) |
| FM-4 | Wrong recompute order / wrong values in chains, fans, diamonds | Arithmetic the user checks by hand is wrong | **D** (value oracles: `#k = k` etc.) |
| FM-5 | Cycle misclassification: members wrong, bystanders swallowed, disjoint loops merged, length-1 special-cased | "It can name every entry in the loop" becomes false | **C** |
| FM-6 | Blocked pointing at the wrong culprit, or conflated with Failed | Two errors shown, zero causes | **S**, **T** |
| FM-7 | Undo/redo restoring a partial or wrong snapshot; redo surviving a new transaction | The one recovery tool corrupts instead | **E** |
| FM-8 | `ans` rebinding silently on delete; `ans` after a valueless row misbehaving | Positional meaning drifts | **S**, **A** |
| FM-9 | Forbidden states appearing: `Stale`, `Ambiguous` constructed by any event sequence (D-031.8/D-032 say they cannot) | A state the transition tables forbid | **U** |
| FM-10 | Crash/hang in `recompute` on any event sequence (incl. 200-entry graphs, dense triangles) | The tape dies with the user's whole session on it | all axes (catching harness), **X** |
| FM-11 | Copy-vs-reference conflation (D-031.7): a copy that tracks, or a reference that snapshots | The user's mental model of "linked vs frozen" inverts | **R** |
| FM-12 | Environment leakage or isolation surprises: `5 → A` in one entry visible/invisible to the next, inconsistently | Stored registers lie | **V** |

---

## 4 · DECISION TABLES

### 4.1 D-032's derivation table (the seven rules) — re-measured as the battery's classifier

The battery's independent classifier (`B2Reference`) re-implements this table from its
text: own AST walk for `#n`/`ans` names, own dependency set, own Kahn, own per-entry
environment, own route call. Engine-vs-classifier divergence on any entry after any
event is a finding.

| # | Condition (in precedence order) | Destination |
|---|---|---|
| 1 | id in Kahn's non-orderable remainder | `Cycle(members)` |
| 2 | source blank | `Empty` |
| 3 | parse `Err` | `Failed(error, previous)` |
| 4 | any dependency handed no value | `Blocked(that dep, previous)` |
| 5 | route `Ok` | `Computed(outcome)` |
| 6 | route `Err` | `Failed(error, previous)` |
| 7 | route `Ambiguous` | `Ambiguous` — no producer (D-031.8) |

### 4.2 The events D-032 never covered — the extension matrix, measured not drawn

Rows: the target entry's state before the event. Columns: the NEW events. Every cell
is a battery case (axis T); values shown are the *derived expectations* from table
4.1 — the battery measures whether the engine agrees.

| start ↓ · event → | delete a dep | delete self | badge-ref appended (new dependent) | dep edited → good | dep edited → bad | dep edited → blank | edit forms cycle | undo | redo |
|---|---|---|---|---|---|---|---|---|---|
| **Computed** | dep ref now unknown → `Failed` | entry gone; dependents `Failed` | new entry `Computed`, edge recorded | recompute, may change value | `Blocked(dep)` | `Blocked(dep)` (Empty hands no value) | `Cycle` | snapshot back | snapshot forward |
| **Empty** | still `Empty` | gone | new dependent `Blocked(empty dep)` | still `Empty` | still `Empty` | — | — | back | forward |
| **Failed** | (dep was the failure source?) re-derived | gone; dependents re-derived | new dependent `Blocked(failed dep)` | may become `Computed` (recovery) | stays `Failed`/re-derived | re-derived | `Cycle` | back | forward |
| **Blocked** | blocker deleted → ref unknown → `Failed` | gone | new dependent `Blocked(this)` | may become `Computed` | stays `Blocked` (new blocker named) | stays `Blocked` | `Cycle` | back | forward |
| **Cycle** | edge cut → may compute | gone; rest of loop re-derived | new dependent joins the leftover (measured!) | loop broken → `Computed` | loop broken → other state | loop broken → `Empty` dep | stays `Cycle` | back | forward |

### 4.3 D-031.7's two taps

| Gesture | Inserts | Dependency edge? | After the original changes |
|---|---|---|---|
| **badge tap** | the token `#3` (via the editor, like any keystroke) | **YES** — `recompute` extracts it | the referencing entry **updates** (and is named in `updated`) |
| **row-body tap** | a deep **copy of the nodes** | **NO** | the copy **does not move** |

The battery proves both columns and the difference between them (axis R).

---

## 5 · PRE-MORTEM — it failed in front of a judge; why?

| # | Story | Coverage |
|---|---|---|
| P1 | *"Row 3 changed and nothing said so."* A delete rebound every `ans` below it; one value moved without its row flashing. | I-SILENT on every event; axis S ans-rebind block |
| P2 | *"An old formula grabbed new data."* `#7` written early, target never existed, three appends later a fresh entry became `#7` and the formula lit up with a stranger's number. | axis R rebound block — the mission's CRITICAL |
| P3 | *"It said 0 results updated after I edited."* Source changed, value happened to match, snackbar counted values not edits. | axis S updated-contract probes |
| P4 | *"Two separate loops, one accusation."* `#1↔#2` and `#3↔#4` knotted independently; every badge listed all four. | axis C disjoint-loop cases |
| P5 | *"A row that merely USED the loop got branded as the loop."* `#3 = #1+1` with `#1↔#2` cycling — `#3` shown as cycle member, not as blocked-by. | axis C bystander cases |
| P6 | *"Undo brought back half the story."* 50-entry cascade undone; one value came back different. | axis E deep-equality restores |
| P7 | *"The register vanished between rows."* `5 → A` on the tape, `A+1` on the next row: unbound. The panel says A holds 5; the tape says A is unknown. | axis V environment cases |
| P8 | *"The tape froze at 30 rows."* Dense reference triangle made recompute quadratic-explosive. | axis X dense graphs, wall-clock caps |

---

## 6 · BATTERY — the axis matrix

Implementation: `battery/src/test/kotlin/com/vaibhav/calc/battery/B2Battery.kt` +
`B2Reference.kt` + `B2Cases.kt` + `B2BatterySpec.kt`. Cases drive the real
`Tape`/`Registers` API (sources built through `Editor.insert`, the same door
`TapeTransitionSpec` uses). After **every event** of **every case** the harness asserts:

- **I-DEPS** — every entry's `dependsOn` ⊆ ids on the tape
- **I-IDS** — entry ids strictly ascending in list order; never re-issued
- **I-CLASS** — every entry's state class, `Blocked.by`, `Cycle.members`, dep-set, and
  (scalar) `Computed` value agree with the independent classifier (`B2Reference`,
  written from table 4.1's text)
- **I-SILENT** — any entry whose value changed in a transaction appears in that
  transaction's `updated` list (the named-harm tripwire, FM-1)
- crash/hang caught at the API boundary (CRITICAL)

| Axis | What it exercises | Cases |
|---|---|---|
| **K CAP** | the cap question measured first: 50/200-entry retention, no engine cap; long-range references across any UI window; delete-as-the-only-eviction; id monotonicity across delete and undo | 16 |
| **T TRANSITIONS** | D-032's 25 ordered pairs re-measured through the battery, then the §4.2 extension matrix: 5 start states × 9 uncovered events, plus edit-self-to-{good, bad, blank, cycle, self} from each state | 95 |
| **D DEPENDENCIES** | chains (len 2,5,10,25,50,100 — build / edit-cascade / undo / updated-ids each), fans (2,5,10,25,50 × 4 oracles), diamonds and fan-ins, ans-chains, forward references and their recovery, equation/assignment sources | 69 |
| **C CYCLES** | lengths 2–6 × {formation, exact members, bystander dependent, break-by-edit, break-by-delete, undo re-forms}; the self-loop ×4 (D-033); two disjoint loops ×3; cycle-by-edit ×3; **ans-loops** ×2; 2D-structure sources holding `#n` ×4 | 49 |
| **E EDIT / UNDO across recompute** | edit with N dependents (count + values), cascade undo, redo, stack exhaustion (`NoMoreUndos`), redo cleared by new transaction, edit-to-same-value, Empty↔value flips, interleavings, mid-chain edits, cumulative counts | 50 |
| **R REFERENCES / BADGE (D-031.7)** | badge-insert at 4 editor contexts; row-tap copy vs badge reference under edit-of-original (the divergence, measured); the dangle proof: delete → `Failed`, id never re-issued, undo doesn't rewind `nextId`; **the rebound-reference hunt** (`#7` before `#7` exists, then append until it does); self-badge; multi-ref expressions; mixed copy+ref | 42 |
| **S STATE HONESTY** | ans-rebind on delete always named in `updated`; exact updated-id sets; `previous` retention on Failed/Blocked; immediate-blocker naming; blocked-by-valueless-Computed probe; Blocked→Failed on eviction; per-state carried-data pins ×15; updated-contract probes | 45 |
| **A ANS SEMANTICS** | ans on the first row; ans after Failed / Empty / valueless rows; ans chains under delete and undo; `ANS`/`ans` case; ans-vs-#n equivalence and difference | 14 |
| **V LANGUAGE × TAPE** | sources that are assignments (`5 → A` — does A reach the next row?), registers panel vs tape env, Devanagari, units with `#n`, solve outcomes as dependencies, percent/factorial, base-n | 36 |
| **I DETERMINISM** | no-op edit idempotence across 20 scenario states; append-order independence of the same logical graph ×6 | 26 |
| **X STRESS** | 200-entry chain cascade, 100-fan, 50 undos, append/delete alternation ×100, 20-ref rows, dense 30-entry triangle, empty-tape edge ops, seeded event storms ×12 with all invariants live | 50 |
| **U FORBIDDEN STATES** | `Stale` and `Ambiguous` never constructed across sweeps and storms (D-032/D-031.8 re-pinned); the missing 8th state (`Unverified`, M-2) recorded as a gap probe | 8 |
| | **Total** | **500** |

**Near-duplicate purge rule:** duplicates iff same (event-sequence shape, start-state
class, graph shape, oracle) 4-tuple; representatives kept, size/length variants kept
only where size is the axis under test.

**Chunk gate:** chunk 1 = the first 100 in axis order (K complete + T's first 84).
HALT on any untriaged CRITICAL. Chunk 2 = the remaining 400.

**Severity ladder** (B1's, instantiated for B2): **CRITICAL** = silent value change
(I-SILENT), dangling or rebound reference reaching a computed value, crash/hang,
`Stale`/`Ambiguous` constructed, wrong arithmetic in a dependency graph. **MAJOR** =
classifier divergence without a silent change; wrong `updated` sets; blocker/member
misnaming. **MINOR/PROBE** = contract oddities awaiting a ruling (0-updated edits,
bystander-in-members, merged loops, environment isolation).
