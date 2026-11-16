# BACKLOG

Things noticed while building M0+M1 that are **out of scope** for the current
milestone. One line each, per Constitution Article 2. Noticed, parked, not built.

> These are NOT commitments. Later-milestone/later-version features from the v1
> spec (complex numbers, base conversion, matrices, stats, equation solvers,
> graphing, units, handwriting, photo-solve, the full API) live in the spec, not
> here — this file is only for things that surfaced *during* the engine build.

- Exact special-angle trig table so `sin(30)`/`cos(60)`/`tan(45)` return exact `1/2`, `√3/2`, `1` instead of a decimal that rounds to them.
- Mixed-number display option: render `5/2` as `2 1/2` (improper ↔ mixed toggle).
- ~~Indian digit grouping (lakh/crore) and Hindi numerals for display (spec 14.4)~~ — **scheduled to M8** by amendment A5 (Article 10). No longer parked.
- SI-prefix engineering display (`12.345e3` → `12.345k`).
- Variadic `min`/`max`/`gcd`/`lcm`/`hypot` (currently strictly binary).
- More constants: golden ratio φ, plus CODATA physical constants with uncertainty (spec 9.4/9.5).
- `tan(90°)` (and cot/sec/csc poles) → return a typed "undefined" instead of a very large finite decimal.
- DMS *output* formatting: decimal degrees → `d°m′s″` string (the inverse of the `dms()` input function already built).
- ~~Assignment statements in the parser~~ — **closed by D-016**: `=` is equation-only, `5 → A` stores.
- Cubic and higher-degree equations, and numeric root-finding for non-polynomial ones (`sin(x)=0`) — D-016 routes linear + quadratic only, and reports anything else as a typed error rather than guessing.
- Systems larger than 3×3, and non-linear systems.
- `x + y = 5` as a *relation* — its real home is graphing (10.1); D-016 reports it as underdetermined and never picks a variable.
- `RouteResult.Ambiguous` currently has **no producer** — the D-016 grammar removes the known ambiguities by construction, which is the goal (D-016.5 priority 1). The state exists as the contract for a future feature that introduces a genuine one.
- Result-form preference in `Settings` (prefer exact vs prefer decimal) and a fraction/decimal (`S⇔D`) toggle.
- `sec`/`csc`/`cot` and their inverses/hyperbolics as first-class functions.
- Rationalise-denominator vs leave-as-is display policy for surds.
- Configurable factorial/exponent limits surfaced in `Settings` rather than hard constants in `NumMath`.
- Parser recovery that reports *multiple* syntax errors in one pass (currently first-error).
- Complex transcendental functions (`sin`/`cos`/`ln`/`exp` of a complex), complex-base non-integer powers, and nth-roots of a complex (M2 covers arithmetic + rect/polar + integer powers).
- Exact surd-sum roots (`(−3+√5)/2` kept symbolic) — needs the CAS layer deferred to v2 (D-004/D-010).
- Base display for negative/fractional values and grouped base output.
- Matrix literals in the expression grammar (`[[1,2],[3,4]]`) — M3 matrices are an API, not inline expression values.
- Eigenvalues/eigenvectors, LU/QR decomposition, trace, adjugate (M3 covers add/mul/det/inverse/rank).
- More statistics: mode, quartiles/percentiles/IQR, skewness/kurtosis, weighted mean; non-linear regression (quadratic/exponential/power/log) — M3 regression is least-squares linear.
- Inline unit expressions (`5 km + 300 m` in one box) + unit-suffix parsing — M4 units are an API.
- SI prefix auto-scaling (kilo/mega/milli…), a larger unit table, and configurable/regional `bigha` variants.
- More physical constants + a full CODATA table with correlation; currency units remain v3 (needs a live rates provider).
