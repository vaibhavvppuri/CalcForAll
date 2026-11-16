# CalcForAll local API

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

Build dependencies are downloaded during the first development build. Once built, calculation itself requires no internet access. Keep the Gradle dependency cache for later `--offline` builds.

## Request and result contract

Use `Content-Type: application/json` for all versioned POST endpoints. Send mathematical values as strings to preserve their digits and exact expressions. Ordinary JSON numbers are accepted in numeric fields, but JavaScript callers may round their own numbers before sending them. Configuration integers such as `samples` and `from` are JSON integers.

```json
{"expr":"9007199254740993+1"}
```

The response contains `ok: true`, `result.kind: "scalar"`, and `result.value.exact: "9007199254740994"`. Numeric results include `exact`, `decimal`, and `approximate` fields. The `decimal` field is subject to the requested display precision and notation. Always consume `exact` when lossless numeric output is needed. The `exact` machine field uses ungrouped Western digits and preserves rational fractions regardless of display settings. For an approximate `BigDecimal` result, it preserves all stored digits while `approximate: true` explicitly identifies the value as an approximation. Exact symbolic numbers such as `pi` may also have a rounded decimal companion. Quantity coefficients and complex components are parenthesized so their machine strings can be evaluated again.

Evaluated outcomes additionally expose `result.approximate` for the displayed primary answer. This is true for approximate engine results and rounded decimal presentations, even when an exact fraction remains in `result.value.exact`. Clients should show an approximation indicator with that display.

When reusing an approximate real value as calculator input, `approx(value)` preserves its status as an estimate at the new request's compute precision. A bare copied decimal literal is otherwise an exact rational input. Retain approximation metadata in integrations instead of treating the decimal digits alone as a proof of exactness.

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

Matrix operations are `add`, `subtract`, `multiply`, `determinant`, `inverse`, `transpose`, `rank`, and `scale`. Binary operations require `b`; `scale` requires `scalar`. Matrices are rectangular arrays of rows. `rank` returns a JSON integer; determinant returns a numeric result; other operations return `{rows, columns, cells}`.

For a one-value statistics dataset, sample variance and sample standard deviation are `null`, because they are undefined. They are never replaced by zero.

For linear regression with constant Y values and varying X, the fitted horizontal line is valid, but correlation and r² are undefined. The response keeps the slope/intercept and returns `null` for `r` and `rSquared`. A dataset with no X variation cannot determine a unique fitted slope and returns a typed error.

Graph samples default to 201 points over `[-6.6, 6.6]`; root search defaults to 200 samples. A verified numeric root means the original function’s residual passed the engine’s tolerance. Numeric interval scanning is **not a proof that every root was found**. An asymptote or domain error is never emitted as a successful root. An unverified response retains both verified roots and unresolved candidates.

Root responses carry `approximate` both for the search result and each root/coordinate. Numeric bisection is marked approximate even when the sampled coordinate is stored as an exact rational. An unverified candidate is always marked approximate; retained verified roots are also marked approximate when their residual is nonzero or their coordinate is an approximate decimal. A zero residual from a numeric sampling method does not turn that method into an exact symbolic proof.

Root searches permit at most 10,000 evaluations and use a five-second cooperative time budget. Budget/cancellation checks run around operations and evaluations; an individual bounded arithmetic/extraction operation is not forcibly interrupted mid-call. Unresolved brackets, unevaluated samples, or budget exhaustion return a typed unsupported result instead of a successful incomplete list.

Numeric root fallback accepts a conservative continuity-safe expression subset: sums/products, closed nonzero denominators, nonnegative integer powers of variable expressions, positive closed-base exponentials, and supported continuous functions such as `sin`, `cos`, `atan`, `abs`, `cbrt`, `exp`, hyperbolic functions, `hypot`, `min`, and `max`. Variable denominators, negative powers, dependent `ln`/`sqrt`/`tan`/`asin`, and discontinuous operations are rejected for numeric root finding; plotting and tracing remain available. Exact low-degree polynomial solving is unaffected. Numeric candidates must satisfy both absolute and relative residual checks; sampling still cannot prove a complete root set.

Surface sampling defaults to a 21 × 21 grid over x/y in `[-5,5]`. The server allows 2–32 rows and columns and a four-second sampling budget. Points are returned in row-major order: X varies across each row, then Y advances to the next row. Every point has exact string-valued coordinates and a separate defined, undefined, or unevaluated state. Min/max Z are `null` when no valid heights were found. Surface bounds have magnitude at most `1e6`, each span is at least `1e-12`, and heights above `1e100` are marked unevaluated. Keep the sampling warnings with visualisations; a wireframe does not prove continuity between samples.

Symbolic calculus always uses **radians**, independently of numeric settings. Its output includes `expression`, `display`, `steps`, `conditions`, `angleMode`, `constantOfIntegration`, and `domainRestricted`. Conditions belong to the answer and must be shown to users; when `domainRestricted` is true, do not reuse the simplified expression without carrying those restrictions. Antiderivatives have `constantOfIntegration: true`; the parseable `expression` omits `+ C`, while `display` includes it. Unsupported algebra returns a typed error rather than an invented solution. See [SYMBOLIC.md](SYMBOLIC.md) for the supported mathematical scope.

Definite numerical integration also uses radians. It returns `value`, `estimatedError`, `evaluations`, `method`, `approximate: true`, and `warnings`. The error estimate is **not a rigorous bound**. The engine uses adaptive Gauss–Kronrod quadrature with conservative domain checks, and rejects improper integrals, unproven singularities, and failure to converge. Bounds may be reversed. Both tolerances default to `1e-10` and must be in `1e-20..0.01`. Numerical working `precision` defaults to 48 and must be in `32..50`; formatting uses `settings`. The server caps evaluations at 4,096, subdivision depth at 20, and elapsed calculation time at four seconds. Very narrow features may escape finite sampling; display the warnings with the result.

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

The API tests exercise actual Ktor HTTP requests, exact large integers, localized display with lossless machine values, fractions, angle settings, register isolation, equations, unit conversion, matrices, statistics, regression, base conversion, graph holes and verified roots, 3D surface grids, symbolic metadata, numerical integration and its error estimates, malformed JSON, input limits, content types, and response security headers. Separate codec tests cover strict JSON and loopback configuration. Test results are generated under `api/build/reports/tests/test`.

