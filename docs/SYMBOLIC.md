# Symbolic algebra and calculus

The symbolic module is an offline, bounded real algebra engine built on the same parser and exact rational number tower as ordinary calculations. It introduces no service, model download, API key, or network dependency. Its public functions return `CalcResult`: unsupported mathematics and exhausted work budgets are explicit errors.

## Operations

`Symbolic.simplify(input, variable = "x")` combines exact rational coefficients and like polynomial terms, removes proven neutral terms, and retains variable denominators. `expand` distributes rational polynomials, including multivariate products. `factor` finds confirmed rational roots of univariate rational polynomials, divides exactly, and leaves an unfactored remainder when necessary. A remainder is not a claim of irreducibility over the reals or complex numbers.

`differentiate` supports sums, products, quotients, constant and variable powers, the chain rule, ordinary and inverse trigonometric/hyperbolic functions, exponential/logarithmic functions, square and cube roots, and absolute value with its qualification at zero. The variable can be x, y, or z; other accepted symbols are real parameters.

`integrate` supplies an antiderivative for rational polynomials, supported powers of linear expressions, reciprocals of linear expressions, common functions of linear expressions, 1/(1+x²), and polynomial times exp/sin/cos by finite integration by parts through degree 8. It returns a parseable expression separately from its `constantOfIntegration` flag; `display` appends `+ C`. Unsupported forms such as exp(x²) produce a typed error instead of an invented answer.

All calculus uses **radians**, regardless of the main calculator's current angle setting. `SymbolicAnswer` contains the expression, operation, variable, explanatory rule steps, domain conditions, angle mode, constant-of-integration flag, and a conservative `domainRestricted` signal. Rule steps describe the method; they are not a complete pedagogical proof trace.

## Domains and precision

The engine does not cancel x/x to 1, erase 0*(1/x), or drop an unproved domain hole in f(x)-f(x). Conditions accompany transformations, and derivative conditions may be conservative: a sufficient restricted domain can omit a point where a more sophisticated analysis would establish differentiability. Undefined closed subexpressions are checked before a rewrite can erase them.

Exact rational polynomial work stays exact. The result is not a general CAS normal form; unsupported function identities, complex symbolic algebra, piecewise calculus, arbitrary parameter assumptions, and general antiderivatives remain outside its supported subset. The core's documented convention 0^0 = 1 is retained.

The app adapter keeps domain-qualified results in the tool instead of offering automatic insertion that would discard their conditions. Its unrestricted symbolic insertion contract carries radians explicitly. Reusing an antiderivative chooses the representative with C = 0 and includes that notice. These restrictions are deliberately conservative: an unproved unrestricted domain can disable automatic reuse even when a more sophisticated proof would permit it.

Numerical tool results use the real-only `approx(value)` function when inserted into calculator text. It returns a `Dec` at the current compute precision, retaining the approximate marker rather than reparsing the estimate as an exact rational literal. The marker does not supply an uncertainty interval or make a numerical error estimate rigorous. Exact rational insertion remains unchanged even when its displayed decimal is rounded.

Resource budgets include 4096 input characters, 1024 input AST nodes, depth 96, 4096 result nodes, polynomial degree 32, 256 polynomial terms, and 8192-bit symbolic rational coefficients. Rational factor search is limited to degree 12 and a bounded candidate search. Results that would exceed these bounds return typed errors.

## Definite numerical integration

```kotlin
NumericCalculus.integrate("sin(x)", Num.ZERO, Num.ONE)
```

`NumericCalculus.integrate(input, lower, upper, variable, options)` evaluates finite, proper, real integrals in radians using adaptive Gauss–Kronrod 7/15 quadrature. Calculations use BigDecimal and the existing evaluator; there is no Double arithmetic in the integration engine. Four initial panels reduce simple sampling symmetry problems, followed by refinement of the panel with the largest estimated error. Bounds may be reversed.

`IntegralEstimate` reports `value`, `estimatedError`, `evaluations`, `method`, `approximate = true`, `warnings`, and `angleMode = "RAD"`. The error is an estimate, not a rigorous bound. Very narrow features and adversarial oscillations can escape any finite sampling strategy; results require that qualification even when a requested tolerance is met.

The Android adapter evaluates bound expressions in radians at the integration working precision too: for example, an upper bound of asin(1) means π/2 even when the scientific calculator was using degrees.

A separate conservative range pass checks supported functions for continuity and domain restrictions. It rejects possible internal poles before quadrature, so a symmetric sample cannot silently turn a divergent integral into a Cauchy principal value. It also rejects unsupported discontinuities and intervals whose safety it cannot establish. This can refuse valid integrals, for example when a dependency between interval terms would require deeper algebraic reasoning. Split or simplify such inputs. Infinite/improper integrals and principal values are not supported.

Exact polynomial collection occurs before numerical sampling, and sample coordinates enter the existing evaluator as exact rational values. Distinct bounds that collapse at the selected working precision are rejected. Intervals too narrow relative to their offset, and unresolved large intermediate rounding in sums, also require translating, simplifying, or rescaling the expression. These guards reduce avoidable cancellation; they do not turn an estimated quadrature error into a universal proof.

Defaults: absolute/relative tolerance 1e-10, working precision 48 digits, 4096 function evaluations, subdivision depth 20, and 4 seconds. Supported options: tolerances 1e-20 through 0.01; precision 32–50; evaluations 128–20000; depth 1–24; time 100–15000 ms. Bounds have magnitude at most 1e12; nonzero intervals must be at least 1e-24 wide. Function ranges and argument magnitudes have additional conservative guards. These are work budgets, not promises that every input inside them will converge. A budget failure returns an error and no misleading partial result.

Quadrature method and standard mathematical tables were checked against the [Boost.Math Gauss–Kronrod documentation](https://www.boost.org/doc/libs/1_85_0/libs/math/doc/html/math_toolkit/gauss_kronrod.html) and its [published GK15 table](https://raw.githubusercontent.com/boostorg/math/develop/include/boost/math/quadrature/gauss_kronrod.hpp). The Kotlin algorithm is independently implemented; no Boost code or binary is included.

## Verification

`SymbolicSpec` checks exact algebra/factorization, known derivatives, an independent finite-difference comparison, antiderivative differences against known primitives, preserved holes, unsupported forms, and resource limits. `NumericCalculusSpec` checks independent known integral values, reversed bounds, negative cube roots, radians, conservative singularity rejection, explicit work limits, and convergence failure. Run these with `:core:test`; the release handoff records actual completed test counts.

`SymbolicTransferSpec` checks the conservative domain metadata. `ApproxValueSpec` checks explicit real approximation, precision, arity and domain handling; Android's `ToolTransferSpec` covers radians bounds, withheld conditional insertion, exact versus rounded displays, and approximate result reuse through the calculator session. Completed release verification counts are recorded separately from these test descriptions.

Verification receipt, 10 November 2026 at 23:51 IST: the completed full core run passed **725 tests in 47 suites**, with zero failures, errors or skips. This includes **68 symbolic tests and 24 numerical-calculus tests**. The new tests include catastrophic cancellation, precision-collapsed bounds, nonpositive bases introduced by calculus rules, ordinary zero endpoints, odd symmetric integrals and exact large-polynomial cases. All XML result files postdated that run's start. The separate Android-boundary check passed after scanning 107 source/test files. These are JVM/core results; APK usability and physical-device compatibility have separate gates.
