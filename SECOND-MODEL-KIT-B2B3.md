# SECOND-MODEL KIT — B3 (EDITOR) + B2 (TAPE)

Purpose: hand this run to an **independent, non-Claude model** (GPT-5.x class,
Gemini 3 class, or a strong open model) and get an adversarial second opinion —
not a re-run, a *cross-examination*. One kit covers both brains. Engine at
`c32176a`; batteries at `1608264` (B3) and `3eabe46` (B2); triage at `4249cc8`.

---

## 1 · WHAT TO ATTACH (exact file list, in this order)

Ground truth (the code IS the truth):
1. `core/src/main/kotlin/com/vaibhav/calc/core/entry/Node.kt`
2. `core/src/main/kotlin/com/vaibhav/calc/core/entry/Editor.kt`
3. `core/src/main/kotlin/com/vaibhav/calc/core/entry/EditorState.kt`
4. `core/src/main/kotlin/com/vaibhav/calc/core/entry/Clipboard.kt`
5. `core/src/main/kotlin/com/vaibhav/calc/core/keypad/Keypad.kt`
6. `core/src/main/kotlin/com/vaibhav/calc/core/keypad/FnPalette.kt`
7. `core/src/main/kotlin/com/vaibhav/calc/core/tape/Tape.kt`
8. `core/src/main/kotlin/com/vaibhav/calc/core/tape/EntryValue.kt`
9. `core/src/main/kotlin/com/vaibhav/calc/core/tape/Registers.kt`

Law and evidence:
10. `DECISIONS.md` (at minimum D-016, D-018, D-019, D-021..D-023, D-031, D-032, D-033, D-034)
11. `DESIGN-HANDOFF-M7-2.md` §2.1–§2.3, §4.2–§4.4

The run under examination:
12. `BRAIN-B3-EDITOR.md` and `BRAIN-B2-TAPE.md` (the two brain docs)
13. `BRAIN-RUN-REPORT.md` (the triage)
14. `battery/build/battery/b3-chunk-1.json`, `b3-chunk-2.json`,
    `b2-chunk-1.json`, `b2-chunk-2.json` (raw verdicts)
15. The four battery sources, so the harness itself can be attacked:
    `battery/src/test/kotlin/com/vaibhav/calc/battery/B3Reference.kt`,
    `B3Battery.kt`, `B3Cases.kt`, `B2Reference.kt`, `B2Battery.kt`, `B2Cases.kt`

Do NOT attach: `BATTERY-REPORT-B1.md` or B1 sources (different brain; would anchor
the model), or any `build/` outputs beyond the four JSONs.

---

## 2 · THE EXACT PROMPT (paste verbatim)

```
You are auditing a verification run performed by another AI on a calculator
engine's two "brains": B3, the 2D expression EDITOR (what expression did the
user actually type), and B2, the history TAPE with live dependency recompute
(what does the user believe ran). You have: the engine source for both
subsystems, the project's decision log and design handoff, the two brain
documents, a triage report, the battery harness sources, and the raw
per-case verdict JSONs.

Your job is to BREAK the run, not to summarise it. The named harms are:
B3 — the expression on screen differs from the expression evaluated;
B2 — the user trusts a value whose meaning silently changed.

Work these five attacks, in order:

A1. VERDICT AUDIT. The run claims: B3 520 cases / 0 fail; B2 500 cases /
    3 fails (two CRITICAL "rebound reference", one MAJOR "fraction key
    splits a badge-inserted #1"), plus 46 probes. Re-derive from the engine
    source whether each of the three findings is real, mis-attributed, or
    an artifact of the harness. Say explicitly if any CRITICAL should be
    downgraded or any PASS should have been a finding.

A2. HARNESS ATTACK. The batteries use two "independent" reference models
    (B3Reference, B2Reference) that were written by the same AI that wrote
    the cases, in the same session, after reading Editor.kt and Tape.kt.
    Identify every place where reference and engine could be WRONG TOGETHER
    — shared blind spots that make an equality check vacuous. Rank the three
    worst, with a concrete input each that would expose them.

A3. MISSING BRANCHES. From the decision tables in the two brain docs and
    from your own reading of Editor.kt's backspace/collapse/wrap logic and
    Tape.kt's recompute, list concrete cases the 1020 do NOT cover. For each:
    the exact keystroke script or event sequence, the branch it exercises,
    and your predicted outcome. Prioritise: (a) cursor-path arithmetic in
    collapse/deleteSelection with nested selections; (b) wrapTrailingOperand
    interactions with multi-char tokens (#12, function names typed as text,
    Devanagari digits); (c) Tape recompute with duplicate-looking sources
    and ans/#n mixtures; (d) undo-stack aliasing (does any path mutate a
    snapshotted TapeState?).

A4. RULING STRESS. The triage proposes 12 owner rulings. For each: state
    whether the recommendation is right, and name one consequence of it the
    triage did not mention. Where you disagree, give the ruling you would
    make and why.

A5. THE FIVE WORST INPUTS. Give the five single inputs (keystroke script or
    tape event sequence) most likely to embarrass this engine in front of an
    examiner, ranked, with predicted behaviour from source. They must not
    duplicate battery cases.

Constraints: reason from the ATTACHED SOURCE, not from what the run's
documents claim about it — the documents are testimony, the code is truth.
Quote line-level evidence for every claim. If you cannot establish something
from the attachments, say UNKNOWN, never guess. You may not run code; this
is a static cross-examination.
```

---

## 3 · REQUIRED RETURN FORMAT (demand this structure back)

```
## VERDICT
One of: RUN SOUND / RUN SOUND WITH GAPS / RUN UNSOUND — one paragraph, no hedging.

## FINDING-BY-FINDING
F-1 rebound reference:   CONFIRM / REFUTE / REATTRIBUTE — evidence (file:line)
F-2 reference splitting: CONFIRM / REFUTE / REATTRIBUTE — evidence
F-3 D-016.1 vs D-016.4:  CONFIRM / REFUTE / REATTRIBUTE — evidence

## DISAGREEMENTS
Numbered. Each: what the run claims, what the code shows, file:line, severity
(CRITICAL/MAJOR/MINOR) under the run's own ladder.

## SHARED-BLIND-SPOT RISKS (from A2)
Top 3, each with the concrete exposing input.

## MISSING BRANCHES (from A3)
Table: # | script/events | branch | predicted outcome | severity if wrong.
Minimum 10 rows or an explicit statement that fewer exist.

## RULINGS (from A4)
12 rows: ruling # | AGREE/DISAGREE | the unstated consequence | your ruling if different.

## FIVE WORST INPUTS (from A5)
Ranked 1–5 with predicted behaviour.

## CONFIDENCE
Per section: HIGH / MEDIUM / LOW, with the single fact that would most change it.
```

---

## 4 · SCORING THE RETURN (for the owner, when it comes back)

- A return that only paraphrases `BRAIN-RUN-REPORT.md` has failed A1–A3; discard.
- Any REFUTE of F-1/F-2/F-3 must quote `Tape.kt`/`Editor.kt` lines; check the
  quote before believing it — both findings reproduce in two battery cases each.
- MISSING-BRANCH rows are the valuable output: convert credible rows into new
  battery cases in `:battery` (never into `:core` — the 545 baseline is receipted
  evidence and must not move).
- Treat its A5 list as the seed of the next battery chunk regardless of verdict.
```
