# CalcForAll local API

Release **1.7** keeps the additive **v1** wire protocol. `/v1/capabilities` distinguishes `releaseVersion` from `apiVersion`; existing endpoint paths and numeric machine fields remain available. The previous code-9 build is publicly cataloged as Version 1.6 with its original internal 2.0 identity preserved. Final Version 2.0 remains reserved; earlier development labels remain in historical verification records.

The optional JVM server uses the **same `:core` calculator as the Android app**. It does not call an external service, require an account, upload inputs, or charge for calculations. The Android app does not need this server to run.

The server is stateless: every calculation receives a fresh calculator. A register supplied by one request is never available to another. There is no database or API history. Application error logs omit formulas, bodies, and register values.

## Run locally

Install a JDK 17 or newer, then run from the repository root:

```powershell
.\gradlew.bat :api:run
```

```sh
./gradlew :api:run
```

The default listener is `127.0.0.1:8080`. `CALC_PORT` may be set to a port in `1..65535`. `CALC_HOST` changes the bind address only when explicitly supplied. The server has no authentication or TLS, and is designed for local use. A public deployment needs a separately configured authenticated HTTPS gateway and operational limits; no public deployment is part of this project.

To create an installable server distribution:

```sh
./gradlew :api:installDist
```

Alternatively, `:api:distZip` creates `api/build/distributions/api-1.7.zip`. Extract it and run `api-1.7/bin/api.bat` on Windows or `api-1.7/bin/api` on Linux/macOS with JDK 17 or newer available. The handoff may name the same ZIP `CalcForAll - Version 1.7 - Local API.zip`; its internal directory stays `api-1.7`.

Build dependencies are downloaded during the first development build. Once built, calculation itself requires no internet access. Keep the Gradle dependency cache for later `--offline` builds.

## Request and result contract

Use `Content-Type: application/json` for all versioned POST endpoints. Send mathematical values as strings to preserve their digits and exact expressions. Ordinary JSON numbers are accepted in numeric fields, but JavaScript callers may round their own numbers before sending them. Configuration integers such as `samples`, base-conversion `from`, `months`, and `order` are JSON integers. Finite-series bounds named `from`/`to` are mathematical whole numbers and may instead be expression strings.

```json
{"expr":"9007199254740993+1"}
```

The response contains `ok: true`, `result.kind: "scalar"`, and `result.value.exact: "9007199254740994"`. Numeric results include `exact`, `decimal`, and `approximate` fields. The `decimal` field is subject to the requested display precision and notation. Always consume `exact` when lossless numeric output is needed. The `exact` machine field uses ungrouped Western digits and preserves rational fractions regardless of display settings. For an approximate `BigDecimal` result, it preserves all stored digits while `approximate: true` explicitly identifies the value as an approximation. Exact symbolic numbers such as `pi` may also have a rounded decimal companion. Quantity coefficients and complex components are parenthesized so their machine strings can be evaluated again.

Evaluated outcomes additionally expose `result.approximate` for the displayed primary answer. This is true for approximate engine results and rounded decimal presentations, even when an exact fraction remains in `result.value.exact`. Clients should show an approximation indicator with that display.

Use the new `input` field when reusing a result. It preserves approximation provenance with `approx(value)` for unknown uncertainty or `approx(value,error)` for a bounded estimate. The existing `exact` field retains the stored point value and does not, by itself, encode uncertainty. A bare copied decimal literal is an exact rational input.

Every numeric object includes `uncertainty`:

- `{"kind":"exact"}`: an exact number, even if its decimal presentation is rounded.
- `{"kind":"unknown"}`: an estimate without a certified error bound; its displayed digits do not prove accuracy.
- `{"kind":"bounded","absoluteError":"0.001"}`: the mathematical value is enclosed by stored value ± the given nonnegative absolute error. A zero error remains approximate in provenance but denotes a known point.

Complex results expose numeric `real` and `imaginary` objects; quantities expose a numeric `coefficient`. Their top-level `input` preserves each component's metadata. Numeric graph-root coordinates, sampled limit estimates, and quadrature estimates use unknown uncertainty: a point's arithmetic bound is not a rigorous bound on a root, limit, or integral. Quadrature's separate `estimatedError` remains a heuristic error estimate, never a certified enclosure.

Point-only tool parameters (finance, BMI, vector inputs, probability, curve fitting, quartiles, Taylor/limit centers, and table bounds) reject estimates with unknown or nonzero uncertainty as `UNSUPPORTED` instead of silently discarding it. Explicit zero-error estimates are permitted subject to the core's arithmetic checks. Integer/discrete inputs require exact rational whole numbers. Calculator expressions and register values can carry estimate metadata across ordinary supported arithmetic.

The constants catalog retains its original reference fields. Mathematical entries additionally carry `input`, `approximate`, and numeric `uncertainty`; legacy catalog `exact` remains a formatted reference string, so use `input` for reuse. Physical entries retain the separate standard-uncertainty string and exact-definition flag, and add `uncertaintyMeaning`, reusable quantity `input`, and `evaluationUncertainty`. A measured constant's standard statistical uncertainty is not a hard enclosure: G, electron mass, and proton mass therefore evaluate as estimates with unknown rigorous bounds.

Settings accepted by numeric operations:

```json
{
  "angle":"DEG",
  "computePrecision":40,
  "displayPrecision":12,
  "rounding":"HALF_EVEN",
  "notation":"AUTO",
  "exactForm":"AUTO",
  "grouping":"NONE",
  "numerals":"WESTERN",
  "complexEnabled":false
}
```

`angle` is `DEG`, `RAD`, or `GRAD`. `notation` is `AUTO`, `PLAIN`, `SCIENTIFIC`, or `ENGINEERING`. `exactForm` is `AUTO`, `FRACTION`, or `DECIMAL`. `grouping` is `NONE`, `INDIAN`, or `WESTERN`. `numerals` is `WESTERN` or `DEVANAGARI`. Enum names are case-insensitive. Rounding modes use Java names; `UNNECESSARY` is rejected. Compute precision must be at least four digits above display precision. Grouping and numeral choices affect presentation, not the stored value. For machine consumption, keep grouping `NONE` and numerals `WESTERN`.

Unknown fields, duplicate JSON keys, invalid UTF-8, unpaired Unicode surrogates, and incorrect value types are rejected instead of silently ignored. Compressed request bodies are not accepted. Versioned JSON responses use `Cache-Control: no-store` and `X-Content-Type-Options: nosniff`.

## Endpoints

| Method and path | Purpose and request fields |
| --- | --- |
| `GET /health` | Plain text `ok` for process health. |
| `GET /` or `/v1/capabilities` | Operation names, stateless behavior, and limits. |
| `GET /v1/functions` | Function names, aliases, arities, display names, and descriptions. |
| `GET /v1/constants` | Mathematical and physical constants, units, uncertainty, and exactness. |
| `GET /v1/tool-catalog` | New tool families, operation names, required fields, and optional fields. |
| `GET /v1/units` | Unit aliases, canonical names, dimensions, factors, and temperature offsets. |
| `POST /v1/evaluate` | `expr`, optional `settings`, optional `registers`. Routes arithmetic, units, stored values, equations, identities, no-solution cases, and small linear systems. |
| `POST /v1/convert` | `value`, `from`, `to`, optional `settings`. Converts compatible units, including affine temperature. |
| `POST /v1/matrix` | `operation`, matrix `a`, optional matrix `b`, optional `scalar`, optional `settings`. |
| `POST /v1/statistics` | `data`, optional `settings`. Count, sum, extrema, range, mean, median, population/sample variance and standard deviation. |
| `POST /v1/regression` | `points` as `[[x,y], ...]`, optional `settings`. Linear slope, intercept, correlation, and r². |
| `POST /v1/base` | Integer string `value`, optional integer `from` (default 10), optional integer `to` (default 16). Bases 2–36. |
| `POST /v1/graph/sample` | `expr`, optional `variable`, `xMin`, `xMax`, `samples`, `settings`, `registers`. Returns distinct defined, undefined, and unevaluated samples. |
| `POST /v1/graph/roots` | Same graph fields. Returns roots verified against the original function, or an explicit unverified/error result. |
| `POST /v1/graph/intersections` | Root fields plus `other`, the second expression. |
| `POST /v1/graph/surface` | `expr` as z=f(x,y), optional `xMin`, `xMax`, `yMin`, `yMax`, `columns`, `rows`, `settings`, `registers`. Returns a bounded 3D surface grid. |
| `POST /v1/symbolic` | `operation`, `expr`, optional `variable`. Bounded simplify, expand, factor, differentiate, and integrate. |
| `POST /v1/integrate` | `expr`, `lower`, `upper`, optional `variable`, `absoluteTolerance`, `relativeTolerance`, `maxEvaluations`, `precision`, `settings`. Finite proper numerical integration. |

`variable` is `x`, `y`, or `z` and defaults to `x`. Registers are uppercase `A`, `B`, `C`, `D`, `E`, `F`, or `M`; values are dimensionless real expressions. A request may provide them with `"registers":{"A":"1/3","B":"sqrt(2)"}`. Unset registers are errors. Server requests never share `ans` or register state.

Evaluation result `kind` is `scalar`, `stored`, `verdict`, `roots`, `system`, `identity`, or `noSolution`. Each variant includes its relevant structured fields plus a display string. Equation roots are ordered as in the app.

Matrix operations are `add`, `subtract`, `multiply`, `determinant`, `inverse`, `transpose`, `rank`, `scale`, `eigenvalues`, and `characteristicPolynomial`. Binary operations require `b`; `scale` requires `scalar`. Matrices are rectangular arrays of rows. `rank` returns a JSON integer; determinant returns a numeric result; ordinary matrix-valued operations return `{rows, columns, cells}`. `characteristicPolynomial` returns numeric `coefficients` in ascending powers of λ for `det(λI-A)`. `eigenvalues` returns distinct numeric/complex `values`, that same `polynomial`, and `eigenvectors: false`; repeated eigenvalues are listed once. The API matrix cap remains 6 × 6. Higher-degree polynomial equations use `/v1/evaluate` directly, with the core's degree-eight and coefficient/work limits; numerical roots remain approximate and unsupported cases return typed refusals.

For a one-value statistics dataset, sample variance and sample standard deviation are `null`, because they are undefined. They are never replaced by zero.

For linear regression with constant Y values and varying X, the fitted horizontal line is valid, but correlation and r² are undefined. The response keeps the slope/intercept and returns `null` for `r` and `rSquared`. A dataset with no X variation cannot determine a unique fitted slope and returns a typed error.

Graph samples default to 201 points over `[-6.6, 6.6]`; root search defaults to 200 samples. A verified numeric root means an established upper bound for the original function’s absolute residual passed the engine’s tolerance. Sample signs and closed denominators must be established from error bounds; unknown uncertainty or a wide interval crossing zero cannot supply a sign. Relative residual targets use lower bounds for the original endpoint magnitudes. Within a sufficiently narrow continuity bracket, either qualifying endpoint residual bounds or a qualifying midpoint residual bound can complete bisection. Numeric interval scanning is **not a proof that every root was found**. An asymptote or domain error is never emitted as a successful root. An unverified response retains both verified roots and unresolved candidates.

Root responses carry `approximate` both for the search result and each root/coordinate. Numeric bisection is marked approximate even when the sampled coordinate is stored as an exact rational. An unverified candidate is always marked approximate; retained verified roots are also marked approximate when their residual is nonzero or their coordinate is an approximate decimal. A zero residual from a numeric sampling method does not turn that method into an exact symbolic proof.

Root searches permit at most 10,000 evaluations and use a five-second cooperative time budget. Budget/cancellation checks run around operations and evaluations; an individual bounded arithmetic/extraction operation is not forcibly interrupted mid-call. Unresolved brackets, unevaluated samples, or budget exhaustion return a typed unsupported result instead of a successful incomplete list.

Numeric root fallback accepts a conservative continuity-safe expression subset: sums/products, closed nonzero denominators, nonnegative integer powers of variable expressions, positive closed-base exponentials, and supported continuous functions such as `sin`, `cos`, `atan`, `abs`, `cbrt`, `exp`, hyperbolic functions, `hypot`, `min`, and `max`. Variable denominators, negative powers, dependent `ln`/`sqrt`/`tan`/`asin`, and discontinuous operations are rejected for numeric root finding; plotting and tracing remain available. Exact low-degree polynomial solving is unaffected. Numeric candidates must satisfy both absolute and relative residual checks; sampling still cannot prove a complete root set.

Surface sampling defaults to a 21 × 21 grid over x/y in `[-5,5]`. The server allows 2–32 rows and columns and a four-second sampling budget. Points are returned in row-major order: X varies across each row, then Y advances to the next row. Every point has exact string-valued coordinates and a separate defined, undefined, or unevaluated state. Min/max Z are `null` when no valid heights were found. Surface bounds have magnitude at most `1e6`, each span is at least `1e-12`, and heights above `1e100` are marked unevaluated. Keep the sampling warnings with visualisations; a wireframe does not prove continuity between samples.

Symbolic calculus always uses **radians**, independently of numeric settings. Its output includes `expression`, `display`, `steps`, `conditions`, `angleMode`, `constantOfIntegration`, and `domainRestricted`. Conditions belong to the answer and must be shown to users; when `domainRestricted` is true, do not reuse the simplified expression without carrying those restrictions. Antiderivatives have `constantOfIntegration: true`; the parseable `expression` omits `+ C`, while `display` includes it. Unsupported algebra returns a typed error rather than an invented solution. See [SYMBOLIC.md](SYMBOLIC.md) for the supported mathematical scope.

Definite numerical integration also uses radians. It returns `value`, `estimatedError`, `evaluations`, `method`, `approximate: true`, and `warnings`. The error estimate is **not a rigorous bound**. The engine uses adaptive Gauss–Kronrod quadrature with conservative domain checks, and rejects improper integrals, unproven singularities, and failure to converge. Bounds may be reversed. Both tolerances default to `1e-10` and must be in `1e-20..0.01`. Numerical working `precision` defaults to 48 and must be in `32..50`; formatting uses `settings`. The server caps evaluations at 4,096, subdivision depth at 20, and elapsed calculation time at four seconds. Very narrow features may escape finite sampling; display the warnings with the result.

### Additional shared-core tools

All paths below are POST endpoints under `/v1/`. They accept optional `settings`, return `{ok:true, operation, result}`, and reject unknown fields. Names are case-sensitive. An `operation` is required for multi-operation families; omit it entirely for `bmi`, `quartiles`, `taylor`, `limit`, and `table`. The authoritative field inventory is `GET /v1/tool-catalog`. These wrappers call shared core modules; Android-only geometry, business, and sequence forms are not duplicated in the server.

| Path | Operations and fields |
| --- | --- |
| `finance` | Operations and exact parameter names below. Rates are percentage units: `18` means 18%, while `0.18` means 0.18%. No suffix removal or implicit monetary rounding. |
| `dates` | `between`: `from,to`; `shift`: `date,days`; `age`: `birth,asOf`; `weekday`: `date`. Dates are ISO `YYYY-MM-DD` or `DD/MM/YYYY`, years 0001–9999. No implicit clock or time zone. |
| `duration` | `add`/`subtract`: duration strings `a,b` as `H:MM[:SS]`; `between`: clock strings `from,to`; `format`: exact whole `seconds`. Clock end before start means next day; equal clocks mean zero elapsed time. |
| `bmi` | `weightKg,heightCm`. Returns numeric `index`, adult `band`, label and scope. Weight must be >0 and ≤1000 kg, height 30–300 cm. The bands are general screening categories, not a diagnosis or age/population-adjusted assessment. |
| `words` | `indian`/`international`: exact whole `value`; `rupees`: nonnegative whole `rupees`, JSON integer `paise` 0–99. English wording only; magnitudes below 10²¹. |
| `vector` | `dot`, `cross`, `projection`, `angle`: arrays `a,b`; `length`: `a`. Equal dimensions, 1–16 components; cross product requires 3. Projection is a onto b. Angles use `settings.angle` and return `angleMode`; zero-vector direction is refused. |
| `number-theory` | `factorize`, `isPrime`, `divisors`: exact whole `value`; `gcd`, `lcm`: 2–100 exact whole `values`. Factoring/primality are deterministic within magnitude 10¹⁴; divisor listing caps at 2000. |
| `probability` | `binomial`: JSON integers `n,k`, numeric `p`; `poisson`: JSON integer `k`, numeric `lambda`; `normal`: numeric `mean,sigma,x`. Returns `atMost` and `above`, plus `exactly` for discrete distributions. |
| `curve-fit` | `quadratic`, `exponential`, `power`, `logarithmic`: `points` as `[[x,y],...]`. Returns model coefficients and nullable `rSquared` with `fitSpace`. Quadratic requires ≥3 points, others ≥2; max 1000. |
| `quartiles` | `data`: 2–1000 values. Returns Q1/median/Q3, interquartile range, modes, frequency and method. Uses medians of halves, excluding the middle observation for odd counts; distinct data has no mode. |
| `taylor` | `expr,center,order`, optional `variable`. Order 0–10. Returns ascending coefficients in powers of `(variable-center)`, expression, and exact-coefficient metadata. A finite polynomial is a local approximation; no remainder bound is supplied. |
| `limit` | `expr,point`, optional `variable`. Point is numeric or the string `inf`, `+inf`, `-inf`. Returns left/right states, nullable `twoSided`, and explicit estimate warning. Finite sampling is not proof of a limit. |
| `table` | `expr,start,end,step`, optional `variable,registers`. Start ≤ end, step >0; at most 200 rows, bound magnitudes ≤10¹². Preserves separate defined/undefined/unevaluated states for every row. |
| `series` | `sum`/`product`: `expr,from,to`, optional `variable,registers`. Inclusive exact whole bounds, from ≤ to, magnitude ≤10⁹. At most 1000 sum terms or 200 product terms; any failed term rejects the whole calculation. |
| `bitwise` | `and`/`or`/`xor`: strings `a,b`; `shift`: `a`, JSON integer `places`; `not`: `a`, JSON integer `bits`. Optional integer `base` 2–36 defaults to 10. Returns decimal/binary/octal/hexadecimal strings. |

Taylor and limit calculations, including their numeric center/point expressions, always use **RAD**. Limit estimates compare agreement at the scale of the observed values, without a unit-sized tolerance or near-zero snapping. Two sides must round to the same value at their shared reported significant digits. A zero estimate is supported explicitly for a continuous-expression subset evaluating to zero at a finite point, or a proper rational function with exact coefficients at infinity; other unresolved approaches remain unsettled. All sampled limit outputs, including zero, retain unknown numerical uncertainty and approximate provenance. Table and finite-series evaluation use the requested angle mode and a per-request register snapshot. Table/series loops have a three-second cooperative budget: checks surround each bounded evaluation; a kernel call is not forcibly interrupted mid-operation. Tables retain calculation failures as unevaluated rows; sums/products never report partial totals as complete.

Probability bounds: binomial 1–2000 trials with 0 ≤ k ≤ n and 0 ≤ p ≤ 1; Poisson 0–5000 events and 0 < λ ≤ 10000; normal σ >0 and standardized score between −40 and 40. Normal tails are evaluated directly at opposite standardized scores to avoid subtracting a tiny tail from 1. Exponential fit uses `y=a*exp(b*x)` and ln(y) fit quality; power fit uses `y=a*x^b` and ln(x),ln(y) fit quality; logarithmic fit uses `y=a+b*ln(x)` and ln(x),y fit quality. A constant response can leave r² undefined (`null`).

Date spans report nonnegative `totalDays`, whole `weeks`, remainder, calendar years/months/days, and a separate `forward` direction. Age requires birth ≤ asOf; a February 29 birthday is treated as March 1 in non-leap years. Shifts are bounded to ±3,660,000 days and must remain in supported years. Duration input allows at most 1,000,000,000 hours per operand; formatted seconds have magnitude ≤7,200,000,007,198. Signed differences use a leading minus sign; raw duration inputs themselves are nonnegative.

Bitwise operations require nonnegative inputs. NOT is unsigned within an explicit 8-, 16-, 32-, or 64-bit word and refuses inputs that do not fit. Positive shift places shift left, negative places shift right. Inputs are capped at 512 characters; shifts and results are capped at 4096 bits.

#### Finance operation fields

| Operation | Required fields | Result fields |
| --- | --- | --- |
| `loan` | `principal,annualRate,months` | `payment,totalPaid,totalInterest` |
| `compound` | `principal,annualRate,years,compoundsPerYear` | `amount,interest` |
| `simple` | `principal,annualRate,years` | `amount,interest` |
| `sip` | `monthly,annualRate,months` | `futureValue,invested,gain,paymentTiming` |
| `schedule` | `principal,annualRate,months` | Yearly rows with `year,interest,principal,balance` |
| `affordableLoan` | `payment,annualRate,months` | `principal,totalPaid,totalInterest` |
| `savingsGoal` | `target,annualRate,months` | `monthly,invested,gain,paymentTiming` |
| `inflation` | `amount,annualRate,years` | `futurePrice,buyingPower` |
| `addTax` / `removeTax` | `amount,rate` | `net,tax,gross` |
| `discount` | `price,percent` | `saving,finalPrice` |
| `percentChange` | `from,to` | `difference,percent` |
| `portion` | `percent,amount` | Numeric result |
| `percentOf` | `part,whole` | Numeric result |
| `split` | `bill,tipPercent,people` | `tip,total,perPerson` |
| `shares` | `amount,ratio` array | `parts,simplestRatio` |
| `unitPrices` | `priceA,quantityA,priceB,quantityB` | `unitPriceA,unitPriceB,savingPercent` |
| `fuel` | `distance,distancePerUnitFuel,pricePerUnitFuel` | `fuel,cost` |
| `proportion` | `a,b,c` | Numeric missing value d from a:b = c:d |

`months`, `compoundsPerYear`, and `people` are JSON integers. Months are 1–12000 (schedule: 1–600); compounding frequency 1–365 with at most 12000 periods; split people 1–10000; ratio length 2–12. SIP and goal contributions occur at the start of each month. Finance computes mathematical scenarios, with no fees, taxes beyond explicitly requested tax operations, external rates, currency conversion, or lender-specific repayment rounding. Display precision never rounds the stored monetary result to two decimals.

Examples of new requests:

```json
{"operation":"loan","principal":"1200","annualRate":"0","months":12}
```

POST the above to `/v1/finance` for an exact monthly payment of 100; `/v1/series` accepts `{"operation":"sum","expr":"x^2","from":1,"to":10}` for 385. `/v1/evaluate` accepts `{"expr":"approx(10,0.1)+2"}` and returns reusable estimate input plus the propagated uncertainty metadata.

### Examples

```powershell
$body = @{ expr = 'x^2-5x+6=0' } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri 'http://127.0.0.1:8080/v1/evaluate' -ContentType 'application/json' -Body $body
```

```sh
curl http://127.0.0.1:8080/v1/convert \
  -H 'Content-Type: application/json' \
  --data '{"value":"0","from":"degC","to":"degF"}'

curl http://127.0.0.1:8080/v1/matrix \
  -H 'Content-Type: application/json' \
  --data '{"operation":"inverse","a":[["2","0"],["0","4"]],"settings":{"exactForm":"FRACTION"}}'

curl http://127.0.0.1:8080/v1/symbolic \
  -H 'Content-Type: application/json' \
  --data '{"operation":"differentiate","expr":"sin(x^2)"}'
```

## Errors and limits

Errors use `ok: false`, `kind`, a readable `error`, optional source `position` and `end`, and `recoverable`. Syntax and mathematical errors use the engine’s typed categories. Positions refer to the submitted expression. HTTP status distinguishes invalid requests from valid requests whose mathematics cannot be completed.

| Status | Meaning |
| --- | --- |
| 400 | Invalid JSON/request, unknown fields, wrong types, or invalid setting values. |
| 409 | Ambiguous interpretation; response includes candidate readings. |
| 408 | Body was not received within five seconds. |
| 413 | Body exceeds 65,536 bytes. |
| 415 | Unsupported content type or content encoding. |
| 422 | Mathematical error, unsupported operation, resource limit, or unverified numeric result. |
| 429 | Two calculations are already active; `Retry-After: 1`. |
| 500 | Unexpected internal failure, with no stack trace or private input in the response. |

Limits: 4,096 expression characters, 512 expression AST nodes, depth 64, 256 characters per standalone numeric value, 6 × 6 matrices, 1,000 data points, 1,000 graph samples, 256 root-search samples, compute precision 16–50, and display precision 1–32. Graph bounds have magnitude at most `1e12` and width at least `1e-12`. JSON itself has a 32-level nesting and 10,000-value limit. Core arithmetic additionally enforces its own magnitude and allocation limits; these are intended to keep accidental extreme inputs from exhausting memory. They are not a substitute for an authenticated public-service deployment design.

The capabilities response advertises `expressionNodes: 512` and `expressionDepth: 64`; the API applies those request limits to scalar expressions as well as equations. Independently, core equation/system routing now performs an iterative preflight before recursive unknown discovery and polynomial extraction, counting both sides of all equations together against 512 nodes and depth 64. That core guard leaves direct core scalar evaluation's iterative long-sum path unchanged. Graph roots/intersections retain their separate structural preflight and cooperative evaluation/time budget described above. See [NUMERIC-LIMITS.md](NUMERIC-LIMITS.md) for the distinct numeric and history budgets.

The legacy `GET /eval?expr=...` remains compatible with the original scalar API and uses HTTP 400 for engine errors. Its strings retain the old display formatting and do not provide the versioned endpoint's lossless machine-value contract or approximation metadata. Prefer the versioned POST endpoint: it supports equation routing and keeps formulas out of URLs, which browsers and proxies may record.

## Verification

```sh
./gradlew :api:test
```

The API tests exercise actual Ktor HTTP requests, exact large integers, localized display with lossless machine values, fractions, angle settings, register isolation, equations, unit conversion, matrices, statistics, regression, base conversion, graph holes and verified roots, 3D surface grids, symbolic metadata, numerical integration and its error estimates, malformed JSON, input limits, content types, and response security headers. The added tool tests cover independent exact totals, tiny-rate finance stability, leap-day conventions, duration overflow boundaries, vector/eigen results, deterministic factors, probability tails, fit quality, quartile conventions, radians calculus, table holes, series completeness, bitwise boundaries, uncertainty metadata and malformed tool schemas. Separate codec tests cover strict JSON and loopback configuration. Test results are generated under `api/build/reports/tests/test`.

