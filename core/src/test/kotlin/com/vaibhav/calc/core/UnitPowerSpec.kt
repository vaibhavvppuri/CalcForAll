package com.vaibhav.calc.core

import com.vaibhav.calc.core.entry.Editor
import com.vaibhav.calc.core.entry.EditorState
import com.vaibhav.calc.core.eval.QuantityValue
import com.vaibhav.calc.core.format.Formatter
import com.vaibhav.calc.core.num.toBigIntegerExactOrNull
import com.vaibhav.calc.core.route.Outcome
import com.vaibhav.calc.core.route.RouteResult
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * A power written on a unit belongs to the unit. Before 1.3 it took the number in front
 * with it: `5 m^2` was 25 m², and the engine's own rendering `6 s²` read back as 36 s²
 * (found by the fourth review pass; Article 12).
 */
class UnitPowerSpec : StringSpec({
    val c = Calculator()
    fun holds(equation: String): Boolean = when (val r = c.route(equation)) {
        is RouteResult.Ok -> (r.outcome as? Outcome.Verdict)?.holds ?: throw AssertionError("'$equation' did not compare: ${r.outcome}")
        else -> throw AssertionError("'$equation' did not evaluate: $r")
    }

    "five square metres is five of the squared unit, however the power is written" {
        listOf(
            "5 m^2 = 5 m²",
            "5 m^2 = 5 m * 1 m",
            "2 km^2 = 2000000 m²",
            "3 s^2 = 3 s * 1 s",
            "3 s² = 3 s * 1 s",
            "4 kg^2 = 2 kg * 2 kg",
            "5 s^-1 = 5 / (1 s)",
            "2 m^3 = 2 m³",
        ).forEach { withClue(it) { holds(it) shouldBe true } }
    }

    "brackets still raise the whole quantity, and a unit after a power is unchanged" {
        listOf(
            "(5 m)^2 = 25 m²",
            "(2 s)^2 = 4 s * 1 s",
            "10^3 kg = 1000 kg",
            "2^10 m = 1024 m",
            "9.8 m/s^2 = 9.8 m / (1 s * 1 s)",
            "6 kg·m/s² = 6 kg * 1 m / (1 s * 1 s)",
        ).forEach { withClue(it) { holds(it) shouldBe true } }
        // the old reading is gone: 5 m^2 is not 25 m²
        holds("5 m^2 = 25 m²") shouldBe false
    }

    "a number word is part of the number, so a power after it raises the whole number" {
        // `5 lakh` IS 500000 (D-016): lakh, crore and dozen carry no dimension. The fifth review
        // pass caught the unit rule above reaching them, which made 5 lakh^2 five times too small.
        listOf(
            "5 lakh^2 = 500000^2",
            "5 lakh² = 500000^2",
            "2 dozen^2 = 576",
            "3 crore^2 = 30000000^2",
            "sqrt(3 lakh^2 + 4 lakh^2) = 5 lakh",
        ).forEach { withClue(it) { holds(it) shouldBe true } }
    }

    "the power key raises a number word together with its number, as the typed text does" {
        // The sixth review pass: the key captured only the word, so `5 lakh` drawn with a raised 2
        // evaluated as 5 × (1 lakh)² while the typed `5 lakh^2` was 500000².
        fun keyed(text: String): String =
            Editor.insert(Editor.typePower(Editor.insert(EditorState(), text)), "2").toInput()
        keyed("5 lakh") shouldBe "((5 lakh)^(2))"
        listOf(
            "${keyed("5 lakh")} = 500000^2",
            "${keyed("2 dozen")} = 576",
            "${keyed("3 crore")} = 30000000^2",
            "${keyed("5lakh")} = 500000^2",       // without the space it always was one operand
            "${keyed("5 m")} = 5 m * 1 m",        // a dimensioned unit keeps the unit rule
            "${keyed("7")} = 49",
        ).forEach { withClue(it) { holds(it) shouldBe true } }
        keyed("lakh") shouldBe "((lakh)^(2))"        // nothing in front of the word: raised by itself
        keyed("3+lakh") shouldBe "3+((lakh)^(2))"    // an operator in front is not an operand
        // The seventh review pass: the word's operand is whatever the PARSER gives it, so a root, a
        // function call or a factorial in front of the number goes into the base with it. The first
        // version took only the digits and `√4 lakh` squared came out as 400000.
        keyed("√4 lakh") shouldBe "((√4 lakh)^(2))"
        keyed("sqrt(4) lakh") shouldBe "((sqrt(4) lakh)^(2))"
        keyed("3+4 lakh") shouldBe "3+((4 lakh)^(2))"
        keyed("-4 lakh") shouldBe "-((4 lakh)^(2))"
        listOf(
            "${keyed("√4 lakh")} = 40000000000",
            "${keyed("sqrt(4) lakh")} = 40000000000",
            "${keyed("3! lakh")} = 600000^2",
            "${keyed("√4 lakh")} = √4 lakh^2",     // the key and the typed text agree
            "${keyed("3! lakh")} = 3! lakh^2",
            "${keyed("√4")} = 4",                  // no number word: the plain rule, √(4²)
        ).forEach { withClue(it) { holds(it) shouldBe true } }
        // The eighth review pass: an operand in its own brackets, and a row that is a statement.
        // The parser's span for `(2+3)` starts inside the bracket, and `x = …` is not a bare
        // expression; both had fallen back to raising the word alone.
        keyed("(2+3) lakh") shouldBe "(((2+3) lakh)^(2))"
        keyed("250000000000 = 5 lakh") shouldBe "250000000000 = ((5 lakh)^(2))"
        keyed("2 = √4 lakh") shouldBe "2 = ((√4 lakh)^(2))"
        listOf(
            "${keyed("(2+3) lakh")} = 250000000000",
            "${keyed("(2+3) lakh")} = (2+3) lakh^2",
            keyed("250000000000 = 5 lakh"),        // the comparison itself holds
        ).forEach { withClue(it) { holds(it) shouldBe true } }
        // A power structure as the operand: 10, the power key, 3, step out, " lakh", the power key, 2.
        val nested = Editor.insert(Editor.typePower(Editor.insert(Editor.moveRight(
            Editor.insert(Editor.typePower(Editor.insert(EditorState(), "10")), "3")), " lakh")), "2").toInput()
        withClue(nested) { holds("$nested = (1000 lakh)^2") shouldBe true }
        // Text that is not a complete entry falls back to the structural reading, never to the bare word.
        keyed("= 5 lakh") shouldBe "= ((5 lakh)^(2))"
    }

    "a keyed fraction or power is one value, whatever is typed after it" {
        // Built as on the keypad: the top, the fraction key, the bottom, step out, then what follows.
        fun fraction(top: String, bottom: String, after: String): String = Editor.insert(Editor.moveRight(
            Editor.insert(Editor.typeFraction(Editor.insert(EditorState(), top)), bottom)), after).toInput()
        fun power(base: String, exponent: String, after: String): String = Editor.insert(Editor.moveRight(
            Editor.insert(Editor.typePower(Editor.insert(EditorState(), base)), exponent)), after).toInput()
        fraction("1", "2", " lakh") shouldBe "((1)/(2)) lakh"
        power("2", "3", "!") shouldBe "((2)^(3))!"
        listOf(
            "${fraction("1", "2", " lakh")} = 50000",       // was 1/(2 lakh): a tenth of a millionth of it
            "${fraction("3", "4", " km")} = 750 m",          // was 3/(4 km)
            "${fraction("1", "2", "%")} = 0.005",            // was 1/(2%) = 50
            "${fraction("7", "2", "")} = 3.5",
            "${fraction("1", "2", "+1")} = 1.5",
            "2${fraction("3", "4", "")} = 1.5",              // a number in front still multiplies
            "${power("2", "3", "!")} = 40320",               // was 2^(3!) = 64
            "${power("2", "3", "%")} = 0.08",
            "${power("10", "3", " kg")} = 1000 kg",
            "-${power("2", "2", "")} = -4",                  // −2² is still −(2²)
        ).forEach { withClue(it) { holds(it) shouldBe true } }
    }

    "the whole part of an exact irrational is taken from as many digits as it needs" {
        fun whole(text: String): java.math.BigInteger =
            (c.evaluate(text).getOrThrow() as com.vaibhav.calc.core.eval.RealValue).num.toBigIntegerExactOrNull()!!
        val piE50 = java.math.BigInteger("314159265358979323846264338327950288419716939937510")
        whole("floor(pi*10^50)") shouldBe piE50
        whole("ceil(pi*10^50)") shouldBe piE50.add(java.math.BigInteger.ONE)   // fifty digits gave …510
        whole("floor(-pi*10^50)") shouldBe piE50.add(java.math.BigInteger.ONE).negate()
        whole("ceil(-pi*10^50)") shouldBe piE50.negate()
        val tenTo25 = java.math.BigInteger.TEN.pow(25)
        whole("floor(sqrt(10^50+1))") shouldBe tenTo25
        whole("ceil(sqrt(10^50+1))") shouldBe tenTo25.add(java.math.BigInteger.ONE) // fifty digits gave 10^25
        // ordinary values are untouched
        mapOf("floor(pi)" to 3, "ceil(pi)" to 4, "floor(-pi)" to -4, "ceil(-pi)" to -3, "ceil(sqrt(2))" to 2,
            "floor(pi/10^60)" to 0, "ceil(pi/10^60)" to 1, "floor(-pi/10^60)" to -1, "floor(100*e)" to 271,
        ).forEach { (text, expected) -> withClue(text) { whole(text) shouldBe java.math.BigInteger.valueOf(expected.toLong()) } }
    }

    "every SI base symbol the engine can write is a unit it can read" {
        com.vaibhav.calc.core.units.BaseDim.entries.forEach { base ->
            val unit = com.vaibhav.calc.core.units.Units.get(base.symbol)
            withClue(base.symbol) {
                (unit != null) shouldBe true
                unit!!.dimension shouldBe com.vaibhav.calc.core.units.Dimension.base(base)
            }
        }
        // No base symbol may also be a memory register: a rendered `s·A` used to read back as
        // seconds times whatever register A held.
        com.vaibhav.calc.core.units.BaseDim.entries.none { com.vaibhav.calc.core.name.ReservedNames.isRegister(it.symbol) } shouldBe true
    }

    "constants measured in amperes or moles read back as themselves, whatever register A holds" {
        val session = Calculator()
        session.route("2 → A")
        fun same(source: String) {
            val value = session.evaluate(source).getOrThrow()
            val shown = session.exact(value)
            withClue("$source → '$shown'") {
                val verdict = (session.route("($shown) = ($source)") as RouteResult.Ok).outcome as Outcome.Verdict
                verdict.holds shouldBe true
                val pasted = (session.route("(${Formatter.input(value)}) = ($source)") as RouteResult.Ok).outcome as Outcome.Verdict
                pasted.holds shouldBe true
            }
        }
        listOf("e_charge", "NA", "R", "2 amp * 3 s", "5 mol / 2 s", "3 cd").forEach { same(it) }
        (session.evaluate("e_charge").getOrThrow() as QuantityValue).q.unit.name shouldBe "s·amp"
    }

    "every derived unit the engine writes reads back as the same quantity" {
        listOf(
            "3 s * 2 s",                 // 6 s²   — used to read back as 36 s²
            "2 kg * 3 kg",               // 6 kg²
            "2 m * 3 m * 1 m * 1 m",     // 6 m⁴
            "6 kg / 2 m / 1 s",          // 3 kg/(m·s) — used to read back as kg·s/m
            "10 m / 2 s / 1 kg",         // 5 m/(kg·s)
            "100 kg / (2 m2 * 5 s)",     // 10 kg/(m²·s)
            "1 / (2 m * 1 s)",           // 1/2 1/(m·s)
            "5 kg * 3 m / (2 s * 1 s)",  // kg·m/s²
            "1 m / 3 s",                 // a coefficient that is a fraction
            "(1/3) km",
            "2 km / 3",
        ).forEach { source ->
            val value = c.evaluate(source).getOrThrow()
            withClue("$source → display '${c.exact(value)}', input '${Formatter.input(value)}'") {
                holds("(${c.exact(value)}) = ($source)") shouldBe true
                holds("(${Formatter.input(value)}) = ($source)") shouldBe true
            }
        }
    }

    "a two-unit denominator is written with brackets" {
        val value = c.evaluate("6 kg / 2 m / 1 s").getOrThrow() as QuantityValue
        value.q.unit.name shouldBe "kg/(m·s)"
        (c.evaluate("1 / (2 m * 1 s)").getOrThrow() as QuantityValue).q.unit.name shouldBe "1/(m·s)"
        (c.evaluate("5 m / 1 s").getOrThrow() as QuantityValue).q.unit.name shouldBe "m/s"
        // one unit below the line needs no brackets, with or without a power (base units are written in SI order)
        (c.evaluate("5 kg * 3 m / (2 s * 1 s)").getOrThrow() as QuantityValue).q.unit.name shouldBe "m·kg/s²"
    }
})
