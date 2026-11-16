package com.vaibhav.calc.core.error

import com.vaibhav.calc.core.source.SourceSpan

/**
 * A typed error result — never an exception thrown at the caller (Constitution
 * Article 4 / spec M1.5: "typed error results, not exceptions-as-control-flow").
 *
 * Every error carries three things the brief demands:
 *  - what went wrong ([detail] / [kind]),
 *  - where ([span], a position in the source), and
 *  - whether it's [recoverable] (a fixable typo vs. a hard math impossibility).
 */
sealed class EvalError {
    abstract val kind: Kind
    abstract val detail: String
    abstract val span: SourceSpan?
    abstract val recoverable: Boolean

    enum class Kind {
        SYNTAX, DIVIDE_BY_ZERO, DOMAIN, OVERFLOW, UNDERFLOW, UNKNOWN_SYMBOL, ARITY, UNSUPPORTED
    }

    /** Human-readable, position-aware one-liner. */
    val message: String
        get() = span?.let { "$detail (at ${it.start}${if (it.length > 0) "..${it.end}" else ""})" } ?: detail

    /** Render with a caret under the offending region of [source], if a span is known. */
    fun render(source: String): String =
        if (span == null) detail
        else "$detail\n  $source\n  ${span!!.caret()}"

    data class Syntax(
        override val detail: String,
        override val span: SourceSpan?,
        override val recoverable: Boolean = true,
    ) : EvalError() {
        override val kind = Kind.SYNTAX
    }

    data class DivideByZero(
        override val span: SourceSpan?,
        override val detail: String = "division by zero",
        override val recoverable: Boolean = false,
    ) : EvalError() {
        override val kind = Kind.DIVIDE_BY_ZERO
    }

    data class Domain(
        override val detail: String,
        override val span: SourceSpan?,
        val function: String? = null,
        override val recoverable: Boolean = false,
    ) : EvalError() {
        override val kind = Kind.DOMAIN
    }

    data class Overflow(
        override val detail: String = "result too large",
        override val span: SourceSpan?,
        override val recoverable: Boolean = false,
    ) : EvalError() {
        override val kind = Kind.OVERFLOW
    }

    data class Underflow(
        override val detail: String = "result too small",
        override val span: SourceSpan?,
        override val recoverable: Boolean = false,
    ) : EvalError() {
        override val kind = Kind.UNDERFLOW
    }

    data class UnknownSymbol(
        val name: String,
        override val span: SourceSpan?,
        override val recoverable: Boolean = true,
    ) : EvalError() {
        override val kind = Kind.UNKNOWN_SYMBOL
        override val detail = "unknown name '$name'"
    }

    data class Arity(
        val function: String,
        val expected: String,
        val actual: Int,
        override val span: SourceSpan?,
        override val recoverable: Boolean = true,
    ) : EvalError() {
        override val kind = Kind.ARITY
        override val detail = "'$function' expects $expected argument(s), got $actual"
    }

    data class Unsupported(
        override val detail: String,
        override val span: SourceSpan?,
        override val recoverable: Boolean = true,
    ) : EvalError() {
        override val kind = Kind.UNSUPPORTED
    }

    /**
     * A tape reference committed against an entry that did not exist (D-036.1).
     *
     * Distinct from [UnknownSymbol] on purpose: this verdict is **sticky**. The
     * missing ids are captured at commit time on the tape entry itself, so a later
     * entry that happens to receive one of these numbers can never be adopted —
     * the rebound-reference hazard the B2 battery proved (BRAIN-RUN-REPORT F-1)
     * is unrepresentable, not merely avoided. Recoverable: an EDIT is a fresh
     * commit and re-captures against the tape as it stands then.
     */
    data class MissingEntry(
        val ids: List<String>,
        override val span: SourceSpan?,
        override val recoverable: Boolean = true,
    ) : EvalError() {
        override val kind = Kind.UNKNOWN_SYMBOL
        override val detail =
            "no entry ${ids.joinToString(", ")} on the tape when this was committed"
    }
}
