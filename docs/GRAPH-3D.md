# Three-dimensional surface sampling

The core supports real scalar surfaces `z = f(x, y)` through `SurfaceSampler.sample`. It uses the ordinary parser and evaluator, with explicit independent bindings for `x` and `y`. There is no second expression language, remote evaluation, or floating-point geometry in the calculation engine. The supplied angle setting applies; the default is degrees, matching the calculator.

Enter the height expression alone, such as `x^2+y^2`. A free `z` in the expression is explicitly unsupported because `z` is the output height, not a third independently bound coordinate.

```kotlin
val grid = SurfaceSampler.sample(
    input = "x^2 + y^2",
    bounds = SurfaceBounds(Num.of(-3), Num.of(3), Num.of(-3), Num.of(3)),
    columns = 21,
    rows = 21,
    settings = Settings(angle = AngleMode.RAD),
)
```

`PlottableSurface.of` parses once and snapshots ordinary register bindings. Its `at(x, y)` method is the single evaluation path for sampling and point tracing. A sampler overload accepts that object when the same expression is sampled repeatedly.

`SurfaceGrid.samples` is row-major: x changes within a row and y changes between rows. Coordinates are `Num` values, exact for rational input bounds. Both endpoints are included. `point(column, row)` returns a sample or null for an out-of-range index. A defined point contains its real z value. An undefined point records a mathematical domain gap or division by zero. An unevaluated point records an unsupported result or a computation limitation, including a complex/unit result that cannot be placed on a real scalar surface. These states must remain distinguishable in the interface. No invalid point is replaced by zero.

`minZ` and `maxZ` cover only defined points and are both null when none were obtained. A constant surface has equal minimum and maximum; a renderer must supply a nonzero display scale in that case. Count properties report defined, undefined and unevaluated points. Convert to Float only after normalizing world coordinates for drawing. Do not connect an edge through an undefined or unevaluated sample.

The mesh is sampled, not a rigorous continuity certificate. A pole or narrow feature can lie between samples. A wireframe can therefore miss behavior or suggest connections that do not exist; the returned warning states this limitation. Rotation and perspective change the view of the sampled mesh, not the underlying function. This is a height-field surface, not a general implicit or parametric 3D solver.

Limits are explicit: 2–40 samples per axis (maximum 1,600 points); expression length 4,096, AST 512 nodes and depth 64; literal rational components at most 4,096 bits; coordinate magnitude at most 1e6; each domain span at least 1e-12; defined height magnitude at most 1e100. Larger heights become unevaluated points. The sampling time budget defaults to 5 seconds and may be set to 100–15,000 milliseconds. Time/interruption is checked around each sample; an individual evaluator call is subject to the numeric engine's work limits and cannot be preempted inside a kernel call. Exhaustion returns a typed error rather than a partial grid presented as complete.

Tests cover independent plane/paraboloid values, exact rational coordinates, domain holes, complex and unit rejection, angle modes, register snapshotting, tracing agreement, invalid limits and a complete 40×40 grid.
