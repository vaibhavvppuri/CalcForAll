package com.vaibhav.calc.core.units

/**
 * The seven SI base dimensions. Each symbol is also a registered unit name, so that a
 * derived unit written from these symbols can be read back (Article 12).
 *
 * The ampere is written `amp`, not `A`: `A` is a memory register (D-016.4), and a rendered
 * `s·A` read back as seconds times whatever register A held. One name, one meaning — the
 * same choice that keeps `h` the hour and names Planck's constant `h_planck`.
 */
enum class BaseDim(val symbol: String) {
    LENGTH("m"), MASS("kg"), TIME("s"), CURRENT("amp"),
    TEMPERATURE("K"), AMOUNT("mol"), LUMINOUS("cd"),
}

/**
 * A physical dimension as a vector of base-dimension exponents. This is what
 * makes dimensional analysis (spec 9.1) mechanical: quantities add only when
 * their [Dimension]s are equal; multiplying/dividing combines them.
 */
data class Dimension(val exponents: List<Int>) {
    init { require(exponents.size == 7) { "a dimension has 7 base exponents" } }

    operator fun times(o: Dimension) = Dimension(exponents.indices.map { exponents[it] + o.exponents[it] })
    operator fun div(o: Dimension) = Dimension(exponents.indices.map { exponents[it] - o.exponents[it] })
    fun pow(n: Int) = Dimension(exponents.map { it * n })

    val isDimensionless: Boolean get() = exponents.all { it == 0 }

    /**
     * SI symbol like `m`, `m²`, `kg·m/s²`, `kg/(m·s)` (empty string when dimensionless).
     *
     * A denominator of more than one unit is bracketed. `/` and `·` have equal precedence and
     * read left to right, so an unbracketed `kg/m·s` reads back as kg·s/m — a different
     * dimension, silently (Article 12: the engine must be able to re-read what it wrote).
     */
    fun siSymbol(): String {
        val num = StringBuilder()
        val den = StringBuilder()
        for (d in BaseDim.entries) {
            val e = exponents[d.ordinal]
            when {
                e > 0 -> appendUnit(num, d.symbol, e)
                e < 0 -> appendUnit(den, d.symbol, -e)
            }
        }
        val below = if (den.contains('·')) "($den)" else den.toString()
        return when {
            num.isEmpty() && den.isEmpty() -> ""
            den.isEmpty() -> num.toString()
            num.isEmpty() -> "1/$below"
            else -> "$num/$below"
        }
    }

    private fun appendUnit(sb: StringBuilder, symbol: String, exp: Int) {
        if (sb.isNotEmpty()) sb.append("·")
        sb.append(symbol)
        if (exp != 1) sb.append(superscript(exp))
    }

    companion object {
        val NONE = Dimension(List(7) { 0 })
        fun base(d: BaseDim) = Dimension(List(7) { if (it == d.ordinal) 1 else 0 })

        val LENGTH = base(BaseDim.LENGTH)
        val MASS = base(BaseDim.MASS)
        val TIME = base(BaseDim.TIME)
        val TEMPERATURE = base(BaseDim.TEMPERATURE)
        val CURRENT = base(BaseDim.CURRENT)
        val AMOUNT = base(BaseDim.AMOUNT)
        val LUMINOUS = base(BaseDim.LUMINOUS)
        val AREA = LENGTH.pow(2)
        val VOLUME = LENGTH.pow(3)
        val SPEED = LENGTH / TIME
    }
}

private fun superscript(n: Int): String =
    n.toString().map { "⁰¹²³⁴⁵⁶⁷⁸⁹"[it - '0'] }.joinToString("")
