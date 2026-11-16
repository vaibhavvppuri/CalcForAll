package com.vaibhav.calc.core

import com.vaibhav.calc.core.error.CalcResult
import com.vaibhav.calc.core.error.EvalError
import com.vaibhav.calc.core.eval.*
import com.vaibhav.calc.core.graph.*
import com.vaibhav.calc.core.num.*
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class SurfaceSpec : StringSpec({
    val square = SurfaceBounds(Num.of(-1),Num.ONE,Num.of(-1),Num.ONE)
    fun z(point: SurfaceSample?) = point.shouldBeInstanceOf<SurfaceSample.Defined>().z

    "a plane preserves both independent coordinates and its exact height range" {
        val bounds = SurfaceBounds(Num.of(-1),Num.ONE,Num.of(-2),Num.TWO)
        val grid = SurfaceSampler.sample("x+2*y",bounds,3,3).getOrThrow()
        grid.samples.size shouldBe 9
        grid.definedCount shouldBe 9
        grid.minZ shouldBe Num.of(-5)
        grid.maxZ shouldBe Num.of(5)
        grid.point(0,0) shouldBe SurfaceSample.Defined(Num.of(-1),Num.of(-2),Num.of(-5))
        grid.point(2,2) shouldBe SurfaceSample.Defined(Num.ONE,Num.TWO,Num.of(5))
        for (point in grid.samples) {
            val p = point as SurfaceSample.Defined
            p.z shouldBe (p.x + Num.TWO * p.y)
        }
    }

    "a paraboloid includes its exact minimum and symmetric corners" {
        val grid = SurfaceSampler.sample("x^2+y^2",square,3,3).getOrThrow()
        z(grid.point(1,1)) shouldBe Num.ZERO
        grid.minZ shouldBe Num.ZERO
        grid.maxZ shouldBe Num.TWO
        for ((x,y) in listOf(0 to 0,2 to 0,0 to 2,2 to 2)) z(grid.point(x,y)) shouldBe Num.TWO
    }

    "rational grid interpolation includes both endpoints without binary floating drift" {
        val grid = SurfaceSampler.sample("x+y",SurfaceBounds(Num.ZERO,Num.ONE,Num.ZERO,Num.ONE),6,5).getOrThrow()
        for (row in 0 until 5) for (column in 0 until 6) {
            val point = grid.point(column,row)!!
            point.x shouldBe Num.of(column.toLong(),5)
            point.y shouldBe Num.of(row.toLong(),4)
        }
        grid.point(-1,0) shouldBe null
        grid.point(6,0) shouldBe null
        grid.point(0,5) shouldBe null
    }

    "a pole is a mathematical gap and never silently becomes a zero height" {
        val grid = SurfaceSampler.sample("1/(x-y)",square,3,3).getOrThrow()
        grid.undefinedCount shouldBe 3
        grid.definedCount shouldBe 6
        grid.unevaluatedCount shouldBe 0
        for (i in 0..2) grid.point(i,i).shouldBeInstanceOf<SurfaceSample.Undefined>().error.kind shouldBe EvalError.Kind.DIVIDE_BY_ZERO
    }

    "complex extension changes unsupported real plotting into a hole rather than a fabricated surface" {
        val real = SurfaceSampler.sample("sqrt(x-y)",square,3,3).getOrThrow()
        real.undefinedCount shouldBe 3
        val complex = SurfaceSampler.sample("sqrt(x-y)",square,3,3,Settings(complexEnabled=true)).getOrThrow()
        complex.unevaluatedCount shouldBe 3
        complex.definedCount shouldBe 6
    }

    "wholly unevaluated surfaces have no invented height range" {
        for (expression in listOf("missing_memory", "5 km", "1e101")) {
            val grid = SurfaceSampler.sample(expression,square,2,2).getOrThrow()
            grid.definedCount shouldBe 0
            grid.unevaluatedCount shouldBe 4
            grid.minZ shouldBe null
            grid.maxZ shouldBe null
            grid.warnings.isNotEmpty() shouldBe true
        }
    }

    "surface settings retain the selected angle mode and trace uses the same evaluator" {
        val degrees = SurfaceSampler.sample("sin(x)+cos(y)",SurfaceBounds(Num.ZERO,Num.of(90),Num.ZERO,Num.of(90)),2,2).getOrThrow()
        degrees.angleMode shouldBe AngleMode.DEG
        z(degrees.point(1,0)) shouldBe Num.TWO
        val radians = PlottableSurface.of("sin(x)+cos(y)",Settings(angle=AngleMode.RAD)).getOrThrow()
        z(radians.at(SymConst.of(Num.HALF,Konst.PI),Num.ZERO)) shouldBe Num.TWO
        val surface = PlottableSurface.of("x^2-y^2").getOrThrow()
        val grid = SurfaceSampler.sample(surface,square,4,5).getOrThrow()
        for (point in grid.samples) surface.at(point.x,point.y) shouldBe point
    }

    "a surface snapshots ordinary registers but binds x and y explicitly" {
        val env = Environment()
        env.set("A",RealValue(Num.of(7)))
        val surface = PlottableSurface.of("A+x+10*y",environment=env).getOrThrow()
        env.set("A",RealValue(Num.of(99)))
        z(surface.at(Num.TWO,Num.of(3))) shouldBe Num.of(39)
    }

    "grid range and work limits reject before allocating an unbounded mesh" {
        for ((columns,rows) in listOf(1 to 2,2 to 1,41 to 2,2 to 41,Int.MAX_VALUE to Int.MAX_VALUE)) {
            SurfaceSampler.sample("x+y",square,columns,rows).shouldBeInstanceOf<CalcResult.Err>().error.kind shouldBe EvalError.Kind.UNSUPPORTED
        }
        SurfaceSampler.sample("x+y",square,2,2,maxMillis=0).shouldBeInstanceOf<CalcResult.Err>()
        SurfaceSampler.sample("x+y",square,2,2,maxMillis=15001).shouldBeInstanceOf<CalcResult.Err>()
        SurfaceSampler.sample("x",SurfaceBounds(Num.ZERO,Num.ZERO,Num.ZERO,Num.ONE)).shouldBeInstanceOf<CalcResult.Err>()
        SurfaceSampler.sample("x",SurfaceBounds(Num.ZERO,Num.of(10_000_000),Num.ZERO,Num.ONE)).shouldBeInstanceOf<CalcResult.Err>()
        SurfaceSampler.sample("x",SurfaceBounds(Num.ZERO,Num.ofDecimal("1e-13"),Num.ZERO,Num.ONE)).shouldBeInstanceOf<CalcResult.Err>()
        PlottableSurface.of("x").getOrThrow().at(Num.of(10_000_000),Num.ZERO).shouldBeInstanceOf<SurfaceSample.Unevaluated>()
    }

    "expression limits and malformed syntax stay typed" {
        SurfaceSampler.sample("z").shouldBeInstanceOf<CalcResult.Err>().error.kind shouldBe EvalError.Kind.UNSUPPORTED
        SurfaceSampler.sample("x".repeat(4097)).shouldBeInstanceOf<CalcResult.Err>()
        SurfaceSampler.sample("sin(".repeat(65)+"x"+")".repeat(65)).shouldBeInstanceOf<CalcResult.Err>()
        SurfaceSampler.sample("1e1300").shouldBeInstanceOf<CalcResult.Err>()
        SurfaceSampler.sample("(").shouldBeInstanceOf<CalcResult.Err>().error.kind shouldBe EvalError.Kind.SYNTAX
    }

    "the maximum supported mesh completes with exactly 1600 real samples" {
        val grid = SurfaceSampler.sample("x+y",square,40,40).getOrThrow()
        grid.samples.size shouldBe 1600
        grid.definedCount shouldBe 1600
    }
})
