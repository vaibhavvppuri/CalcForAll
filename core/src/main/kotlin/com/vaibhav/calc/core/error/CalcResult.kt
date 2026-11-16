package com.vaibhav.calc.core.error

/**
 * The result of any fallible engine operation: either an [Ok] value or a typed
 * [Err]. The public API returns this — exceptions never leak to the caller.
 */
sealed class CalcResult<out T> {
    data class Ok<T>(val value: T) : CalcResult<T>()
    data class Err(val error: EvalError) : CalcResult<Nothing>()

    val isOk: Boolean get() = this is Ok

    fun getOrNull(): T? = (this as? Ok)?.value
    fun errorOrNull(): EvalError? = (this as? Err)?.error

    inline fun <R> map(f: (T) -> R): CalcResult<R> = when (this) {
        is Ok -> Ok(f(value))
        is Err -> this
    }

    inline fun <R> flatMap(f: (T) -> CalcResult<R>): CalcResult<R> = when (this) {
        is Ok -> f(value)
        is Err -> this
    }

    fun getOrThrow(): T = when (this) {
        is Ok -> value
        is Err -> error("calc error: ${error.message}")
    }

    companion object {
        fun <T> ok(value: T): CalcResult<T> = Ok(value)
        fun err(error: EvalError): CalcResult<Nothing> = Err(error)
    }
}
