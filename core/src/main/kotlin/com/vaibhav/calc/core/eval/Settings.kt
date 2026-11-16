package com.vaibhav.calc.core.eval

import java.math.MathContext
import java.math.RoundingMode
import com.vaibhav.calc.core.num.NumericLimits

enum class AngleMode { DEG, RAD, GRAD }

/** How decimal output is presented (spec 1.7 — scientific & engineering). */
enum class NotationMode { AUTO, PLAIN, SCIENTIFIC, ENGINEERING }

/**
 * Which exact form a rational is shown in, when it has two (spec 1.4 — the `S⇔D`
 * toggle, fx-991 parity).
 *
 * **Exact-first means the most UNDERSTOOD exact form, not the most canonical one**
 * (D-025). `53/10 km` is mathematically perfect and no human would ever write it.
 */
enum class ExactForm {
    /**
     * Terminating expansion → the decimal (`3/10` → `0.3`). Non-terminating → the
     * fraction (`1/3` stays `1/3`, because the fraction *is* the exact form).
     * Deferred to the input's own notation where it has one — see
     * `Formatter.preferredForm`.
     */
    AUTO,

    /** Force the fraction — the `S` side of `S⇔D`. */
    FRACTION,

    /** Force the decimal — the `D` side of `S⇔D`. */
    DECIMAL,
}

/**
 * How the integer part of a rendered number is grouped for reading (spec 14.4, G-5).
 *
 * The design ships this as a user toggle and is explicit that it is *"the same
 * engine"* regrouping — so it lives here, in the one settings object every
 * renderer already consults, rather than in a display layer that the tape, the
 * graph tick labels, the matrix cells and the unit browser would each re-implement
 * (Article 12).
 *
 * **Presentation only.** Grouping changes a string and never a value.
 */
enum class GroupingMode {
    /**
     * No separators — the default, and deliberately so: a grouped string contains
     * commas, and a comma is an argument separator in this grammar, so grouped
     * output is **not** valid input. Keeping NONE as the default is what leaves
     * Article 12's render→parse round-trip intact on every internal path.
     */
    NONE,

    /** `12,34,567` — three from the right, then pairs (the Indian system). */
    INDIAN,

    /** `1,234,567` — groups of three throughout. */
    WESTERN,
}

/**
 * Which digit glyphs a rendered number uses (spec 14.4, M-3 / D-035).
 *
 * The design's language toggle is labelled **"Numerals"** and switches exactly this:
 * `dig(s)` maps `0-9 → ०१२३४५६७८९` when the language is Hindi (D-031.5). It is a
 * sibling of [GroupingMode], and for the same reason — the tape, the graph labels,
 * the matrix cells and the unit browser must not each grow their own mapping
 * (Article 12).
 *
 * **Digits only.** Never operators, never unit names, never the stored value. The
 * engine already *accepts* Devanagari input (D-028/D-030); this is the output half.
 */
enum class NumeralSystem(internal val zero: Char) {
    /** `0123456789` — the default, and what the parser round-trips. */
    WESTERN('0'),

    /** `०१२३४५६७८९` — Devanagari, U+0966…U+096F. */
    DEVANAGARI('०'),
}

/**
 * Explicit precision and rounding — never accidents of representation
 * (Constitution, Article 3). [computePrecision] is the internal working
 * precision for irrational fallbacks; [displayPrecision] is what the user sees.
 */
data class Settings(
    val angle: AngleMode = AngleMode.DEG,        // matches a Casio's out-of-box default
    val computePrecision: Int = 40,
    val displayPrecision: Int = 12,
    val rounding: RoundingMode = RoundingMode.HALF_EVEN,
    val notation: NotationMode = NotationMode.AUTO,
    /** Which exact form a rational takes when it has two (the `S⇔D` toggle, D-025). */
    val exactForm: ExactForm = ExactForm.AUTO,
    /** Digit grouping for display (spec 14.4). Presentation only — never a value. */
    val grouping: GroupingMode = GroupingMode.NONE,
    /** Which digit glyphs are rendered (spec 14.4). Presentation only — never a value. */
    val numerals: NumeralSystem = NumeralSystem.WESTERN,
    /** When true, real functions extend to complex (e.g. `sqrt(-4) = 2i`). */
    val complexEnabled: Boolean = false,
) {
    init {
        require(computePrecision in 5..NumericLimits.MAX_SESSION_PRECISION) {
            "computePrecision must be in 5..${NumericLimits.MAX_SESSION_PRECISION} digits"
        }
        require(displayPrecision in 1..NumericLimits.MAX_SESSION_PRECISION - 4) {
            "displayPrecision is outside the supported digit budget"
        }
        require(computePrecision >= displayPrecision + 4) {
            "computePrecision must exceed displayPrecision by a guard margin"
        }
        require(displayPrecision >= 1) { "displayPrecision must be ≥ 1" }
        // UNNECESSARY makes every non-terminating division throw at the display
        // boundary (e.g. 1/3); it is not a usable rounding mode here.
        require(rounding != RoundingMode.UNNECESSARY) {
            "RoundingMode.UNNECESSARY is not a usable rounding mode"
        }
    }

    val computeMc: MathContext get() = MathContext(computePrecision, rounding)
    val displayMc: MathContext get() = MathContext(displayPrecision, rounding)
}
