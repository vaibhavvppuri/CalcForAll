package com.vaibhav.calc.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vaibhav.calc.core.Calculator
import com.vaibhav.calc.core.eval.Settings
import com.vaibhav.calc.core.route.Outcome
import com.vaibhav.calc.core.route.RouteResult
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The device gate. The engine is plain JVM code that Android lint never scans, so a Java
 * member newer than the app's oldest Android compiles, passes every JVM test, and then
 * fails on a phone (`BigInteger.TWO` did exactly that in 1.2). This runs the same code on
 * a real Android runtime: every catalogued tool operation with its own default values,
 * the older tools, and the engine's main routes. Failures are collected, so one run names
 * every broken path. Run it on the oldest supported Android (API 23).
 */
@RunWith(AndroidJUnit4::class)
class DeviceGateTest {
    private val today: LocalDate = LocalDate.of(2026, 11, 12)

    @Test
    fun everyCataloguedOperationComputesItsDefaults() {
        val failures = ArrayList<String>()
        var count = 0
        for (spec in MoreTools.catalog(today)) for (operation in spec.operations) {
            count++
            val label = "${spec.name} / ${operation.name}"
            try {
                val answer = MoreTools.calculate(spec.name, operation.name, operation.fields.map { it.default }, Settings(), today)
                if (answer.error || answer.text.isBlank()) failures += "$label: ${answer.text}"
            } catch (problem: Throwable) {
                failures += "$label: threw $problem"
            }
        }
        assertTrue("catalogue is empty", count > 0)
        assertTrue("${failures.size} of $count operations failed on this device:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun knownAnswersAreTheSameOnThisDevice() {
        fun first(tool: String, operation: String, vararg values: String): String =
            MoreTools.calculate(tool, operation, values.toList(), Settings(), today).text.lines().first()
        assertEquals("Monthly payment: 8678.23", first("Money", "Loan EMI", "1000000", "8.5", "240"))
        assertEquals("P(X = 3): 15/128 (0.1171875)", first("Probability", "Binomial", "10", "3", "1/2"))
        assertEquals("315 days", first("Dates", "Days between", "2026-01-01", "12/11/2026").substringBefore(" ("))
        assertTrue(first("Geometry", "Circle", "5").startsWith("Area: 25π"))
        assertEquals("Whole units needed: 44", MoreTools.calculate("Business", "Break-even", listOf("1000", "30", "7"), Settings(), today).text.lines()[1])
        assertEquals("(55*e)", MoreTools.calculate("Sequences", "Arithmetic", listOf("e", "e", "10"), Settings(), today).insert)
        assertEquals("Rupees one lakh twenty-three thousand four hundred and fifty-six and paise fifty only",
            MoreTools.calculate("Money", "Amount in words", listOf("123456.50"), Settings(), today).text)
        assertEquals("Simplest ratio: 2 : 3 : 5", MoreTools.calculate("Everyday", "Share in a ratio", listOf("1200", "2:3:5"), Settings(), today).text.lines().last())
    }

    @Test
    fun theOlderToolsCompute() {
        fun tool(name: String, operation: String, a: String, b: String = "", extra: String = "") =
            ToolCalculator.calculate(name, operation, a, b, extra, Settings())
        assertEquals("(2/3)", tool("Matrices", "Determinant", "1/3, 0; 0, 2").insert)
        assertEquals("((0) degC)", tool("Units", "Convert", "32", "degF", "degC").insert)
        assertTrue(tool("Statistics", "Summary", "1, 2\n3").text.contains("Population variance: 2/3"))
        assertTrue(tool("Statistics", "Linear regression", "0 1 2", "1 3 5").text.contains("y = (2)x + (1)"))
        assertTrue(tool("Number bases", "Convert", "9007199254740993", "10", "16").text.contains("Base 16: 20000000000001"))
        assertTrue(!tool("Algebra & calculus", "Differentiate", "x^3", "x").error)
        assertTrue(tool("Algebra & calculus", "Integrate", "2*x", "x").text.contains("+ C"))
        assertTrue(!tool("Matrices", "Eigenvalues", "2, 0; 0, 3").error)
        assertTrue(!tool("Matrices", "Solve system", "2, 1; 1, 3", "3; 5").error)
    }

    @Test
    fun theEngineAgreesWithItselfOnThisDevice() {
        val calculator = Calculator()
        val claims = listOf(
            "1/3 + 1/6 = 1/2",
            "0.1 + 0.2 = 0.3",
            "sqrt(8) = 2*sqrt(2)",
            "sqrt(2)^2 = 2",
            "2^100 = 1267650600228229401496703205376",
            "10! = 3628800",
            "pi/6 * 6 = pi",
            "2*e - e = e",
            "(2+3i)*(1-i) = 5+i",
            "sin(30)^2 + cos(30)^2 = 1",
            "5 km + 300 m = 5300 m",
            "5 m^2 = 5 m * 1 m",
            "3 s * 2 s = 6 s^2",
            "6 kg / 2 m / 1 s = 3 kg/(m·s)",
            "5 lakh^2 = 500000^2",
            "2 amp * 3 s = 6 s·amp",
            "50 + 20% = 60",
        )
        val failures = ArrayList<String>()
        for (claim in claims) {
            try {
                val verdict = (calculator.route(claim) as? RouteResult.Ok)?.outcome as? Outcome.Verdict
                if (verdict == null || !verdict.holds) failures += "$claim → ${calculator.route(claim)}"
            } catch (problem: Throwable) {
                failures += "$claim: threw $problem"
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
        // A control, so that the loop above cannot pass by never saying no.
        val control = (calculator.route("1/3 + 1/6 = 1/3") as? RouteResult.Ok)?.outcome as? Outcome.Verdict
        assertTrue("a false claim must be reported as false", control != null && !control.holds)
        assertEquals(17, claims.size)
    }

    @Test
    fun polynomialEquationsAreSolvedOnThisDevice() {
        val calculator = Calculator()
        fun roots(equation: String): Int {
            val result = calculator.route(equation)
            return when (val outcome = (result as? RouteResult.Ok)?.outcome) {
                is Outcome.Roots -> outcome.roots.size
                else -> throw AssertionError("Expected roots for '$equation'; actual RouteResult: $result")
            }
        }
        assertEquals(3, roots("x^3-6x^2+11x-6=0"))      // exact rational roots
        assertEquals(5, roots("x^5-x-1=0"))            // one real root by bisection, four complex numerically
        assertEquals(3, roots("x^3=8"))                // an exact complex pair
        assertEquals(2, roots("(x^2+π)^2=0"))          // a repeated complex root, reported once
    }
}
