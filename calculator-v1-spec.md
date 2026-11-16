# Calculator — v1 Specification

**53 features · ~55 work-hours · the "beat the Casio" release**
*(amended by D-017: +1.9 entry correction, +1.10 undo/redo — census 792 leaves)*
Vaibhav Puri · v1.0 spec · 16 July 2026

---

## The thesis

> **Everything a Casio fx-991 does, plus graphing, plus Indian numbering, on the phone already in their hand.**

That is the whole v1. It is defensible in one sentence, it is shippable, and it wins against the actual competitor — not against Photomath or Desmos, but against the ₹1,200 plastic slab sitting on every Indian student's desk right now.

Handwriting and photo-solve are **not** in v1. They are the delighters, and delighters land harder on a product that already works than on a demo that doesn't.

---

## Weights — locked

You delegated the ranking, so I ranked. Order, then the ROC weights it produces:

| Rank | Axis | Weight | Why here |
|---|---|---|---|
| 1 | **User value** | **52%** | You have real users — yourself and the Play Store. A feature nobody reaches for is dead weight no matter what it teaches or how cheap it is. |
| 2 | **Build cost** *(inverse)* | **27%** | Your attention is ~100 hours across the whole project. Cost is the binding constraint — it is what turns 780 leaves into something that ships. |
| 3 | **Learning value** | **15%** | Your primary *motivation*, but it belongs as a tiebreaker between similar features, not as the thing choosing what ships. Rank it 1st and you get a beautiful education and a bad calculator. |
| 4 | **Differentiation** | **6%** | Low because it is already double-counted inside user value, and because Kano's two-delighter cap does this job structurally — better than a weight can. |

Weights are ROC output (Barron & Barrett, *Management Science* 42, 1996), not my invention. **Override the order and every number below recomputes.**

**Learning multiplier** — derived from your own words rather than a ranking you didn't give: you have returned to **handwriting** in every single message, and explicitly named graphs, equation solving, scientific and image understanding. Those domains carry the multiplier. That's revealed preference, not my guess about your interests.

---

## Why Kano does most of the work

The floor is not scored. Every calculator on earth has order-of-operations and error handling — those are **Basic** class, and Basic is auto-Must regardless of what any weight says. Score them and you'd be pretending to decide something that was never a decision.

So the weights only fight over the discretionary middle. That is by design, and it is why 52% on user value isn't as dominant as it looks.

---

## The v1 cut — 51 features

### Engine floor — Basic class, auto-Must (10)
| # | Feature |
|---|---|
| 1.1 | Basic operations |
| 1.2 | Order of operations, parens, implicit multiply |
| 1.3 | Precision & rounding |
| 1.4 | Fractions — proper, improper, mixed, auto-simplify |
| 1.5 | Negative & absolute |
| 1.6 | Percent semantics |
| 1.7 | Scientific & engineering notation |
| 1.8 | Error handling with inline position |
| **1.9** | **Entry correction — backspace at cursor, clear entry** *(added by D-017)* |
| **1.10** | **Undo / redo** *(added by D-017)* |

### Scientific set — Basic class (8)
| # | Feature |
|---|---|
| 3.1 | Powers & roots |
| 3.2 | Logarithms |
| 3.3 | Trigonometry + angle modes |
| 3.4 | Inverse trig |
| 3.5 | Hyperbolic |
| 3.6 | Combinatorics — factorial, nPr, nCr |
| 3.8 | DMS↔decimal, rect↔polar |
| 3.9 | Rounding family — int, frac, mod, min, max |

### Casio parity — Performance class (11)
| # | Feature |
|---|---|
| 2.1 | Base conversion |
| 2.5 | Complex numbers |
| 5.1 | Linear equations |
| 5.2 | Quadratic — with steps |
| 5.4 | Systems 2×2, 3×3 |
| 7.1 | Matrix entry |
| 7.2 | Matrix basic ops |
| 7.3 | Determinant, inverse, rank |
| 8.1 | Stats data entry |
| 8.2 | Descriptive statistics |
| 8.4 | Regression |

### The wedge — where you beat the Casio (7)
| # | Feature | Why it wins |
|---|---|---|
| 10.1 | 2D function plot | fx-991 cannot graph. At all. |
| 10.2 | Pan / pinch zoom | Physical calculators have no touchscreen |
| 10.3 | Trace, roots, intersections | The thing students actually need for homework |
| 10.7 | Axes, grid, custom range | |
| 9.1 | Unit engine w/ dimensional analysis | fx-991 has no real unit engine |
| 9.2 | Unit categories | |
| 9.6 | **Indian units — lakh, crore, tola, bigha** | Nobody serves this. Your single sharpest edge. |

### Input & session — Basic class (7)
| # | Feature |
|---|---|
| 11.1 | Keypad — basic + scientific layouts, 2nd function |
| 11.2 | Natural 2D math entry |
| 11.5 | Typed entry with autocomplete |
| 11.7 | Clipboard — copy/paste expression + result |
| 12.1 | History tape — tap to reuse, live recompute |
| 12.2 | Memory registers |
| 12.3 | Variables + ANS |

### Constants (2)
| # | Feature |
|---|---|
| 9.4 | Physical constants with CODATA uncertainty |
| 9.5 | Math constants |

### Platform — Basic class, mostly invisible (8)
| # | Feature |
|---|---|
| 14.1 | Themes — light/dark/system/AMOLED |
| 14.2 | Portrait, landscape, tablet |
| 14.3 | Accessibility — TalkBack, dynamic type, colourblind-safe curves |
| 14.4 | **Localisation — Hindi, Indian digit grouping** |
| 14.6 | Performance budgets — cold start, calc latency, APK size |
| 14.8 | **Legal — non-removable** |
| 14.9 | Tests — unit, property, golden |
| 14.11 | Settings |

**Total: 53** *(51 as originally censused; 1.9 and 1.10 added by D-017 — both Kano Basic, auto-Must. Census: 792 leaves.)*

---

## Cut from v1 — and where they went

| Feature | Verdict | Version |
|---|---|---|
| Stylus handwriting | Delighter — cap is 2/version, and it needs the engine underneath it first | **v2** |
| Photo solve | Delighter, and needs network + privacy policy shipped first | **v3** |
| Full CAS (D4) | High cost, and the fx-991 doesn't have it either — parity doesn't demand it | **v2** |
| Calculus (D6) | Same | **v2** |
| 3D plots, sliders, animation | Performance class, mid score, high cost | **v3** |
| Public API (13.4) | You want it, but `core/` must stabilise before anything wraps it | **v4** |
| Scripting, plugins (13.1–13.3) | Could class | **backlog** |
| Notebook mode (12.5) | Could class | **backlog** |
| Currency (9.3) | Needs network + a rates provider + staleness UX | **v3** |
| Voice input (11.6) | Indifferent class — nobody dictates maths | **Won't** |
| Roman numerals (2.8) | Indifferent | **Won't** |
| Special functions — Bessel, zeta, Airy (3.7) | High learning value, near-zero user value. 15% doesn't beat 52%. | **backlog** |

That last row is the weights doing their job on you specifically. Bessel functions would teach you something real. Nobody would ever press the button.

---

## What v1 is NOT allowed to become

The Won't column is deleted, not deferred. It exists so those ideas stop resurfacing at milestone four disguised as good ones.

If a feature is not in the 51 above, Claude Code does not build it in v1 — regardless of how easy it turns out to be. Easy is not a reason. That rule goes in the constitution.
