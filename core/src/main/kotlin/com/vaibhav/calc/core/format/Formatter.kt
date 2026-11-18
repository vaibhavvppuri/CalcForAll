package com.vaibhav.calc.core.format

import com.vaibhav.calc.core.ast.Assignment
import com.vaibhav.calc.core.ast.Binary
import com.vaibhav.calc.core.ast.BinaryOp
import com.vaibhav.calc.core.ast.Call
import com.vaibhav.calc.core.ast.Equation
import com.vaibhav.calc.core.ast.Expr
import com.vaibhav.calc.core.ast.ExprStatement
import com.vaibhav.calc.core.ast.NameRef
import com.vaibhav.calc.core.ast.NumberLit
import com.vaibhav.calc.core.ast.Postfix
import com.vaibhav.calc.core.ast.Statement
import com.vaibhav.calc.core.ast.Unary
import com.vaibhav.calc.core.ast.UnitApply
import com.vaibhav.calc.core.error.CalcResult
import com.vaibhav.calc.core.eval.ComplexValue
import com.vaibhav.calc.core.eval.ExactForm
import com.vaibhav.calc.core.eval.GroupingMode
import com.vaibhav.calc.core.eval.NotationMode
import com.vaibhav.calc.core.eval.QuantityValue
import com.vaibhav.calc.core.eval.RealValue
import com.vaibhav.calc.core.eval.Settings
import com.vaibhav.calc.core.eval.Value
import com.vaibhav.calc.core.parse.Parser
import com.vaibhav.calc.core.num.Complex
import com.vaibhav.calc.core.num.Dec
import com.vaibhav.calc.core.num.Konst
import com.vaibhav.calc.core.num.Num
import com.vaibhav.calc.core.num.Rational
import com.vaibhav.calc.core.num.Surd
import com.vaibhav.calc.core.num.SymConst
import com.vaibhav.calc.core.num.abs
import com.vaibhav.calc.core.num.signum
import java.math.BigDecimal
import java.math.BigInteger

/**
 * Renders a [Num] for display. This is the ONE place `Double`-shaped decimal
 * strings are produced — via [BigDecimal], and only when asked. Exact shapes
 * (fractions, surds, π) render as themselves: `1/3`, `2√2`, `π/6`.
 */
object Formatter {

    /** Parser text for copying/inserting a stored value, independent of display policy.
     * A Dec retains all stored digits; this does not turn an approximation into an exact result.
     *
     * A multiple of e is written with its product sign. To the tokenizer a lowercase e straight
     * after a digit is an exponent marker (D-016.6), so the display form "2e" is refused as an
     * incomplete exponent when read back, and "2e" followed by a typed "+3" would read as 2000.
     */
    fun input(x: Num): String = if(x is Dec) x.value.toString() else exact(x, Settings(
        exactForm = ExactForm.FRACTION, grouping = GroupingMode.NONE,
        numerals = com.vaibhav.calc.core.eval.NumeralSystem.WESTERN)).let {
        if (x is SymConst && x.konst == Konst.E) EULER_AFTER_DIGIT.replace(it, "\$1*e") else it
    }

    private val EULER_AFTER_DIGIT = Regex("(\\d)e")

    fun input(v: Value): String = when(v) {
        is RealValue -> input(v.num)
        is ComplexValue -> "(${input(v.cx.re)})+(${input(v.cx.im)})i"
        is QuantityValue -> "(${input(v.q.value)}) ${v.q.unit.name}"
    }

    /** Calculator input that preserves an estimate's uncertainty instead of promoting its digits to exact data. */
    fun reusableInput(x: Num): String = if (x is Dec) {
        val error = x.absoluteError
        if (error == null) "approx(${input(x)})" else "approx(${input(x)},${error.toString()})"
    } else input(x)

    fun reusableInput(v: Value): String = when (v) {
        is RealValue -> reusableInput(v.num)
        is ComplexValue -> "(${reusableInput(v.cx.re)})+(${reusableInput(v.cx.im)})i"
        is QuantityValue -> "(${reusableInput(v.q.value)}) ${v.q.unit.name}"
    }

    /** The most faithful representation: exact form when there is one, else decimal. */
    fun exact(x: Num, settings: Settings = Settings()): String = numerals(
        when (x) {
            is Rational -> rational(x, settings)
            is Surd -> renderSurd(x)
            is SymConst -> renderConst(x)
            is Dec -> estimated(x, settings)
        },
        settings,
    )

    private fun estimated(x: Dec, settings: Settings): String {
        val text = decimal(x, settings)
        val error = x.absoluteError ?: return text
        if (error.signum() == 0) return text
        val unit = BigDecimal.ONE.scaleByPowerOfTen(adjustedExponent(x.value) - settings.displayPrecision + 1)
        if (x.value.signum() != 0 && error < unit.divide(BigDecimal(2))) return text
        val shownError = error.round(java.math.MathContext(2, java.math.RoundingMode.CEILING)).stripTrailingZeros()
        return "$text ± ${shownError.toString()}"
    }

    // -----------------------------------------------------------------------
    // Numeral system (spec 14.4 / M-3) — presentation, never value.
    // -----------------------------------------------------------------------

    /**
     * Transliterate ASCII digits into [Settings.numerals] (D-035).
     *
     * Applied at the two `Num`-level entry points, [exact] and [decimal], which is
     * precisely what keeps it off everything that is not a number: a quantity's unit
     * name is appended *after* this runs, so `15 m2` renders as `१५ m2` — the value
     * in Devanagari, the SI symbol untouched. Operators (`+`, `√`, `/`, `π`, `i`) are
     * not digits and pass through by construction.
     *
     * **Idempotent**: only `'0'..'9'` are mapped, so a string that already carries
     * Devanagari digits is returned unchanged — `exact` may call `decimal` internally
     * without double-converting.
     *
     * The exponent of scientific notation IS transliterated, unlike grouping, which
     * deliberately skips it. A separator inside an exponent would stop it being a
     * number; a digit glyph does not, and the design's own `dig()` converts the whole
     * rendered string.
     */
    private fun numerals(s: String, settings: Settings): String {
        val zero = settings.numerals.zero
        if (zero == '0') return s
        val sb = StringBuilder(s.length)
        for (ch in s) sb.append(if (ch in '0'..'9') zero + (ch - '0') else ch)
        return sb.toString()
    }

    /**
     * A rational's exact form — **the most understood one, not the most canonical**
     * (D-025).
     *
     * Article 3's acceptance test is `0.1 + 0.2 == 0.3`. Rendering `3/10` passes the
     * engine's own test and fails the user's: the whole point was a human seeing
     * `0.3`. A calculator that answers `3/10` has not demonstrated it beat floating
     * point — it has demonstrated that it is strange.
     *
     *  - terminating expansion → the decimal (`53/10` → `5.3`, `41/20` → `2.05`)
     *  - non-terminating → the fraction (`1/3` stays `1/3`; the fraction IS the exact form)
     *  - integers → themselves
     *
     * Surds are untouched: `2√2` is not `2.828`. The exactness is the point there,
     * and no decimal expresses it at all.
     */
    private fun rational(x: Rational, settings: Settings): String {
        if (x.isInteger) return group(x.num.toString(), settings)
        return when (settings.exactForm) {
            ExactForm.FRACTION -> fraction(x)
            // The user asked for the decimal; a non-terminating one has to be rounded,
            // and `decimal` is the one place that is allowed to say so.
            ExactForm.DECIMAL -> terminatingDecimalOrNull(x, settings) ?: decimal(x, settings)
            ExactForm.AUTO -> terminatingDecimalOrNull(x, settings) ?: fraction(x)
        }
    }

    private fun fraction(x: Rational): String = "${x.num}/${x.den}"

    /**
     * The **exact** decimal expansion, or null when there is not a readable one.
     *
     * A rational terminates iff its reduced denominator is 2^a·5^b. But terminating is
     * not sufficient: `1/2^128` terminates after 39 significant digits, and 39 digits
     * is not more understood than the fraction — it is less. So the guard is
     * [Settings.displayPrecision], the number of digits this engine has already
     * decided a human reads, rather than an invented magic bound.
     */
    private fun terminatingDecimalOrNull(x: Rational, settings: Settings): String? {
        var d = x.den
        while (d.mod(BigInteger.valueOf(2)).signum() == 0) d = d.divide(BigInteger.valueOf(2))
        while (d.mod(FIVE).signum() == 0) d = d.divide(FIVE)
        if (d != BigInteger.ONE) return null
        // Exact: divide with no MathContext throws on a non-terminating quotient, and
        // we have just proved this one terminates.
        val bd = BigDecimal(x.num).divide(BigDecimal(x.den)).stripTrailingZeros()
        if (bd.precision() > settings.displayPrecision) return null
        return group(bd.toPlainString(), settings)
    }

    private val FIVE: BigInteger = BigInteger.valueOf(5)

    // -----------------------------------------------------------------------
    // Digit grouping (spec 14.4 / G-5) — presentation, never value.
    // -----------------------------------------------------------------------

    /**
     * Group the **integer part** of a positional number string.
     *
     * Applied to integers and to plain decimals. Deliberately NOT applied to:
     *  - the **fractional digits** (`1,000.123456`, never `1,000.123,456`) — they
     *    are a positional tail read left-to-right, not a magnitude;
     *  - **exponents** (`1e12345`) — an exponent is a count, and grouping one
     *    produces a string that is not a number at all;
     *  - a **fraction's** numerator/denominator (`1234567/3`) — a ratio is not a
     *    magnitude the eye scans in lakhs, and `1,23,456/7` would read as an
     *    argument list in this grammar;
     *  - **surd and constant coefficients**, for the same reason.
     *
     * The input is a plain string of the shape `-?digits(.digits)?`; anything else
     * (already-grouped, scientific, symbolic) is returned untouched, so this can
     * never corrupt a form it does not understand.
     */
    private fun group(plain: String, settings: Settings): String {
        if (settings.grouping == GroupingMode.NONE) return plain
        if (!PLAIN_NUMBER.matches(plain)) return plain
        val negative = plain.startsWith("-")
        val body = if (negative) plain.substring(1) else plain
        val dot = body.indexOf('.')
        val intPart = if (dot < 0) body else body.substring(0, dot)
        val rest = if (dot < 0) "" else body.substring(dot) // keeps the '.'
        val grouped = when (settings.grouping) {
            GroupingMode.INDIAN -> groupIndian(intPart)
            GroupingMode.WESTERN -> chunkFromRight(intPart, 3)
            GroupingMode.NONE -> intPart
        }
        return (if (negative) "-" else "") + grouped + rest
    }

    /** `12,34,567` — the last three digits, then pairs all the way up. */
    private fun groupIndian(digits: String): String {
        if (digits.length <= 3) return digits
        val last3 = digits.takeLast(3)
        val head = digits.dropLast(3)
        return chunkFromRight(head, 2) + "," + last3
    }

    /** Split into groups of [size] counted from the right, joined with commas. */
    private fun chunkFromRight(digits: String, size: Int): String {
        if (digits.length <= size) return digits
        val out = StringBuilder()
        val firstGroup = digits.length % size
        var i = 0
        if (firstGroup != 0) {
            out.append(digits, 0, firstGroup)
            i = firstGroup
        }
        while (i < digits.length) {
            if (out.isNotEmpty()) out.append(',')
            out.append(digits, i, i + size)
            i += size
        }
        return out.toString()
    }

    /**
     * The exact form the **input itself** asks for — one rule, one place (Article 12),
     * so the router, the tape and the UI cannot each invent their own answer.
     *
     * *"The user's own notation is evidence of what they want back"* (D-025): type
     * `1/2`, see `1/2`; type `0.5`, see `0.5`. A fraction written in the input is a
     * request for fractions in the output; anything else defers to [ExactForm.AUTO].
     *
     * Returns [ExactForm.AUTO] when the input cannot be read — a formatting
     * preference is not the place to report a syntax error.
     */
    fun preferredForm(input: String): ExactForm =
        when (val p = Parser.parseProgram(input)) {
            is CalcResult.Ok -> if (p.value.statements.any { statementHasFractionLiteral(it) }) {
                ExactForm.FRACTION
            } else {
                ExactForm.AUTO
            }
            is CalcResult.Err -> ExactForm.AUTO
        }

    /**
     * Format a value the way its input asks for it.
     *
     * An explicit [Settings.exactForm] wins: that is the user pressing `S⇔D`, and a
     * deliberate choice outranks an inferred one.
     */
    fun forInput(v: Value, input: String, settings: Settings): String {
        val form = if (settings.exactForm == ExactForm.AUTO) preferredForm(input) else settings.exactForm
        return exact(v, settings.copy(exactForm = form))
    }

    /**
     * Whether `S⇔D` has anything to toggle *to* — i.e. this value has two genuinely
     * different exact forms. The key should not be offered when both sides read the
     * same (the design's `sdVisible`).
     */
    fun hasDistinctForms(v: Value, settings: Settings): Boolean {
        val s = exact(v, settings.copy(exactForm = ExactForm.FRACTION))
        val d = exact(v, settings.copy(exactForm = ExactForm.DECIMAL))
        return s != d
    }

    /** `x/y` written as an integer-over-integer division — a fraction, as typed. */
    private fun statementHasFractionLiteral(s: Statement): Boolean = when (s) {
        is ExprStatement -> hasFractionLiteral(s.expr)
        is Equation -> hasFractionLiteral(s.lhs) || hasFractionLiteral(s.rhs)
        is Assignment -> hasFractionLiteral(s.value)
    }

    private fun hasFractionLiteral(e: Expr): Boolean {
        val pending = ArrayDeque<Expr>()
        pending.addLast(e)
        while(pending.isNotEmpty()) {
            when(val node = pending.removeLast()) {
                is Binary -> {
                    if(node.op == BinaryOp.DIV && isIntegerLiteral(node.left) && isIntegerLiteral(node.right)) return true
                    pending.addLast(node.left); pending.addLast(node.right)
                }
                is Unary -> pending.addLast(node.operand)
                is Postfix -> pending.addLast(node.operand)
                is Call -> node.args.forEach { pending.addLast(it) }
                is UnitApply -> pending.addLast(node.value)
                is NumberLit, is NameRef -> Unit
            }
        }
        return false
    }

    private fun isIntegerLiteral(e: Expr): Boolean = when (e) {
        is NumberLit -> (e.value as? Rational)?.isInteger == true
        is Unary -> isIntegerLiteral(e.operand)
        else -> false
    }

    /** Always a decimal string, honouring the notation mode and display precision. */
    fun decimal(x: Num, settings: Settings): String = numerals(decimalAscii(x, settings), settings)

    private fun decimalAscii(x: Num, settings: Settings): String {
        val bd = x.toBigDecimal(settings.displayMc).stripTrailingZeros()
        if (bd.signum() == 0) return "0"
        return when (settings.notation) {
            // Only the PLAIN renderings group: scientific and engineering carry an
            // exponent, and a separator must never reach one.
            NotationMode.PLAIN -> group(bd.toPlainString(), settings)
            NotationMode.SCIENTIFIC -> scientific(bd)
            NotationMode.ENGINEERING -> engineering(bd)
            NotationMode.AUTO -> {
                val adjExp = adjustedExponent(bd)
                // Switch to scientific once the integer part needs more digits than
                // we can show significantly — otherwise plain notation pads
                // fabricated trailing zeros and hides that rounding happened.
                if (adjExp < -4 || adjExp >= settings.displayPrecision) {
                    scientific(bd)
                } else {
                    group(bd.toPlainString(), settings)
                }
            }
        }
    }

    /** Exact form of a [Value] (real, complex, or a quantity with units). */
    fun exact(v: Value, settings: Settings): String = when (v) {
        is RealValue -> exact(v.num, settings)
        is ComplexValue -> complexString(v.cx, settings, useExact = true)
        is QuantityValue -> "${coefficient(exact(v.q.value, settings))} ${v.q.unit.name}"
    }

    /** Decimal form of a [Value]. */
    fun decimal(v: Value, settings: Settings): String = when (v) {
        is RealValue -> decimal(v.num, settings)
        is ComplexValue -> complexString(v.cx, settings, useExact = false)
        is QuantityValue -> "${coefficient(decimal(v.q.value, settings))} ${v.q.unit.name}"
    }

    /**
     * A quantity's coefficient needs parentheses unless it is a plain number:
     * a unit binds tighter than `/`, so `53/10 km` would re-parse as `53/(10 km)`
     * — a different dimension. Article 12: the engine must be able to re-read
     * exactly what it wrote.
     */
    private fun coefficient(rendered: String): String =
        // Separators are stripped before the test: `12,34,567` is still a bare
        // number, so grouping must not be what decides a quantity gets parentheses.
        if (PLAIN_NUMBER.matches(rendered.replace(",", ""))) rendered else "($rendered)"

    /**
     * "Is this a bare number?" — asked of a *rendered* string, so it must accept any
     * numeral system the formatter can emit (D-035). `\d` is ASCII-only in Java
     * regex, which would have made `१५ m2` fail the test and come back wrapped as
     * `(१५) m2`; `\p{Nd}` is every Unicode decimal digit, so the question stays the
     * one actually being asked rather than "is this a bare WESTERN number".
     */
    private val PLAIN_NUMBER = Regex("""-?\p{Nd}+(\.\p{Nd}+)?""")

    /** Renders a complex number as `a + bi` / `a - bi` / `bi` / `i`. */
    private fun complexString(cx: Complex, settings: Settings, useExact: Boolean): String {
        val fmt: (Num) -> String = { n -> if (useExact) exact(n, settings) else decimal(n, settings) }
        if (cx.im.isZero) return fmt(cx.re)
        if (cx.im is Dec && cx.im.absoluteError?.signum() != 0) {
            val imaginary = "(${fmt(cx.im)})i"
            if (cx.re.isZero) return imaginary
            val real = fmt(cx.re).let { if (it.contains(" ± ")) "($it)" else it }
            return "$real + $imaginary"
        }
        val imMagnitude = cx.im.abs()
        val knownMagnitude = imMagnitude !is Dec || imMagnitude.absoluteError?.signum() == 0
        val coefficient = if (knownMagnitude && imMagnitude.compareTo(Num.ONE) == 0) "" else fmt(imMagnitude)
        val imCoeff = if (coefficient.contains(" ± ")) "($coefficient)" else coefficient
        return if (cx.re.isZero) {
            (if (cx.im.signum() < 0) "-" else "") + imCoeff + "i"
        } else {
            val sign = if (cx.im.signum() < 0) " - " else " + "
            "${fmt(cx.re)}$sign${imCoeff}i"
        }
    }

    private fun renderSurd(x: Surd): String {
        val sign = if (x.coeff.num.signum() < 0) "-" else ""
        val p = x.coeff.num.abs()
        val q = x.coeff.den
        val coeffPart = if (p == BigInteger.ONE) "" else p.toString()
        val core = "$coeffPart√${x.radicand}"
        return if (q == BigInteger.ONE) "$sign$core" else "$sign($core)/$q"
    }

    private fun renderConst(x: SymConst): String {
        val sign = if (x.coeff.num.signum() < 0) "-" else ""
        val p = x.coeff.num.abs()
        val q = x.coeff.den
        val coeffPart = if (p == BigInteger.ONE) "" else p.toString()
        val core = "$coeffPart${x.konst.symbol}"
        return if (q == BigInteger.ONE) "$sign$core" else "$sign$core/$q"
    }

    private fun scientific(bd: BigDecimal): String {
        val adjExp = adjustedExponent(bd)
        val mantissa = bd.movePointLeft(adjExp).stripTrailingZeros()
        return "${mantissa.toPlainString()}e$adjExp"
    }

    private fun engineering(bd: BigDecimal): String {
        val adjExp = adjustedExponent(bd)
        val exp3 = ((adjExp.toLong() - if (adjExp < 0) 2 else 0) / 3 * 3).toInt()
        val mantissa = bd.movePointLeft(exp3).stripTrailingZeros()
        return "${mantissa.toPlainString()}e$exp3"
    }

    /** Power of ten of the most-significant digit (0 for values in [1, 10)). */
    private fun adjustedExponent(bd: BigDecimal): Int = bd.precision() - bd.scale() - 1
}
