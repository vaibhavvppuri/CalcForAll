package com.vaibhav.calc.battery

import com.vaibhav.calc.core.Answer
import com.vaibhav.calc.core.Calculator
import com.vaibhav.calc.core.error.CalcResult
import com.vaibhav.calc.core.eval.ComplexValue
import com.vaibhav.calc.core.eval.QuantityValue
import com.vaibhav.calc.core.eval.RealValue
import com.vaibhav.calc.core.eval.Value
import java.io.File
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** One battery case, as read from the JSON. */
internal data class Case(
    val id: String,
    val chunk: Int,
    val axis: String,
    val cls: String,
    val expr: String,
    val oracleParent: String?,
    val numeric30: String?,
    val expectedForm: String?,
    val relTol: String?,
    val requires: List<String>,
    val errorFamily: String?,
    val note: String?,
    val timingCapture: Boolean,
)

internal class Battery(
    val version: String,
    val caseCount: Int,
    val cases: List<Case>,
    val calibration: List<Triple<String, String, String>>, // id, expr, purpose
) {
    companion object {
        fun load(path: String): Battery {
            val f = File(path)
            require(f.isFile) { "battery file not found: $path" }
            val root = MiniJson.obj(MiniJson.parse(f.readText(Charsets.UTF_8)))

            val cases = MiniJson.arr(root["cases"]).map { raw ->
                val m = MiniJson.obj(raw)
                Case(
                    id = MiniJson.str(m["id"])!!,
                    chunk = MiniJson.int(m["chunk"])!!,
                    axis = MiniJson.str(m["axis"])!!,
                    cls = MiniJson.str(m["class"])!!,
                    expr = MiniJson.str(m["expr"])!!,
                    oracleParent = MiniJson.str(m["oracle_parent"]),
                    numeric30 = MiniJson.str(m["numeric_30"]),
                    expectedForm = MiniJson.str(m["expected_form"]),
                    relTol = MiniJson.str(m["rel_tol"]),
                    requires = (m["requires"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                    errorFamily = MiniJson.str(m["error_family"]),
                    note = MiniJson.str(m["note"]),
                    timingCapture = m["timing_capture"] == true,
                )
            }
            val calibration = MiniJson.arr(root["calibration_set"]).map {
                val m = MiniJson.obj(it)
                Triple(MiniJson.str(m["id"])!!, MiniJson.str(m["expr"])!!, MiniJson.str(m["purpose"]) ?: "")
            }

            val declaredCount = MiniJson.int(root["case_count"])!!
            // --- Article 11 guards: the parser is verified, not trusted. A reader
            // --- that silently dropped cases would otherwise report a clean run
            // --- over work that never happened.
            check(cases.size == declaredCount) {
                "parsed ${cases.size} cases but the file declares case_count=$declaredCount"
            }
            check(cases.map { it.id }.toSet().size == cases.size) { "duplicate battery case IDs" }
            val coverage = MiniJson.obj(root["coverage"])
            val byClass = MiniJson.obj(coverage["class"]).mapValues { MiniJson.int(it.value)!! }
            val gotClass = cases.groupingBy { it.cls }.eachCount()
            check(gotClass == byClass) { "class histogram mismatch: parsed $gotClass vs declared $byClass" }
            val byAxis = MiniJson.obj(coverage["axis"]).mapValues { MiniJson.int(it.value)!! }
            val gotAxis = cases.groupingBy { it.axis }.eachCount()
            check(gotAxis == byAxis) { "axis histogram mismatch: parsed $gotAxis vs declared $byAxis" }

            return Battery(MiniJson.str(root["version"]) ?: "?", declaredCount, cases, calibration)
        }
    }
}

/** What the engine did, with every failure mode kept structurally distinct. */
internal sealed interface Outcome {
    data class Ok(val answer: Answer) : Outcome
    data class Err(val kind: String, val message: String) : Outcome
    data class Crash(val type: String, val message: String?) : Outcome
    object Hang : Outcome
}

internal data class Judgement(
    val verdict: String,   // PASS | FAIL | PROBE
    val severity: String?, // CRITICAL | MAJOR | MINOR | null
    val expected: String?,
    val actual: String?,
    val reason: String?,
)

internal data class Record(
    val case: Case,
    val effectiveClass: String,
    val downgraded: Boolean,
    val outcome: Outcome,
    val judgement: Judgement,
    val ms: Long,
    val exactness: String?,
    val display: String?,
    val decimal: String?,
)

/**
 * The evaluation door and the comparison contract.
 *
 * **Door:** [Calculator.evaluateAnswer] — the same entry point `TestHelpers.ansOf`
 * and `FormatterRulingSpec` drive in core's own suite. It is a strict superset of
 * the plain evaluator door (`Calculator.evaluate`, used by `TestHelpers.num`): it
 * calls it internally and adds the rendered display strings, so one call yields the
 * typed result, the value, the exactness class and the FORM string.
 */
internal object Runner {

    /** 30 digits: comfortably past the contract's "≥15 significant figures". */
    private val COMPARE_MC = MathContext(30, RoundingMode.HALF_EVEN)
    private val EXACT_TOL: BigDecimal = BigDecimal("1e-12")
    private val DEFAULT_DECIMAL_TOL: BigDecimal = BigDecimal("1e-9")
    const val CAP_MS: Long = 10_000
    private const val SLOW_MS: Long = 2_000

    private var exec: ExecutorService = newExec()

    private fun newExec(): ExecutorService =
        Executors.newSingleThreadExecutor { r -> Thread(r, "b1-case").apply { isDaemon = true } }

    private fun evaluateOnce(expr: String): Outcome {
        // A fresh Calculator per case: `ans` and the environment must not bleed
        // from one case into the next, or a failure could be caused by its predecessor.
        val calc = Calculator()
        return try {
            when (val r = calc.evaluateAnswer(expr)) {
                is CalcResult.Ok -> Outcome.Ok(r.value)
                is CalcResult.Err -> Outcome.Err(r.error.kind.name, r.error.message)
            }
        } catch (t: Throwable) {
            // The public API contract says this cannot happen. If it does, that is
            // the finding.
            Outcome.Crash(t::class.java.name, t.message)
        }
    }

    fun run(expr: String): Pair<Outcome, Long> {
        val start = System.nanoTime()
        val future = exec.submit(Callable { evaluateOnce(expr) })
        return try {
            val o = future.get(CAP_MS, TimeUnit.MILLISECONDS)
            o to (System.nanoTime() - start) / 1_000_000
        } catch (e: TimeoutException) {
            future.cancel(true)
            // The hung thread may still be spinning and cannot be safely killed;
            // hand the next case a fresh executor rather than queueing behind it.
            exec.shutdownNow()
            exec = newExec()
            Outcome.Hang to CAP_MS
        } catch (e: ExecutionException) {
            val c = e.cause
            Outcome.Crash(c?.let { it::class.java.name } ?: "?", c?.message) to
                (System.nanoTime() - start) / 1_000_000
        }
    }

    fun numericOf(v: Value): BigDecimal? = when (v) {
        is RealValue -> v.num.toBigDecimal(COMPARE_MC)
        // A quantity is compared in its own unit (5 km -> 5), which is the form the
        // oracle's numeric_30 carries; the unit itself is recorded separately.
        is QuantityValue -> v.q.value.toBigDecimal(COMPARE_MC)
        // A complex that survived demotion has a non-zero imaginary part and is not
        // comparable to a real oracle value.
        is ComplexValue -> null
    }

    fun exactnessOf(v: Value): String = when (v) {
        is RealValue -> v.num::class.java.simpleName
        is QuantityValue -> "Quantity<" + v.q.value::class.java.simpleName + ">[" + v.q.unit.name + "]"
        is ComplexValue -> "Complex"
    }

    private fun relClose(actual: BigDecimal, expected: BigDecimal, tol: BigDecimal): Boolean {
        val diff = actual.subtract(expected, COMPARE_MC).abs()
        // A relative tolerance is undefined at zero; fall back to absolute there.
        if (expected.compareTo(BigDecimal.ZERO) == 0) return diff.compareTo(tol) <= 0
        return diff.divide(expected.abs(), COMPARE_MC).compareTo(tol) <= 0
    }

    /**
     * Apply the comparison contract.
     *
     * Severity, as briefed: CRITICAL = a wrong finite value on EXACT/DECIMAL, any
     * crash or hang, any null escape, undefined leaking as finite. MAJOR = a FORM
     * mismatch or a typed refusal of a valued case. Timing over 2 s is recorded
     * separately; the enforced per-case wall-clock cap is 10 s.
     *
     * One reading had to be chosen and is stated so it can be re-ruled: an
     * EXACT/DECIMAL case that comes back as a *typed error* is scored **MAJOR**, not
     * CRITICAL. The engine declined rather than lied, and the contract reserves
     * CRITICAL for a wrong finite value. A wrong number is worse than a refusal.
     */
    fun judge(case: Case, effectiveClass: String, outcome: Outcome): Judgement {
        when (outcome) {
            is Outcome.Hang ->
                return Judgement("FAIL", "CRITICAL", expectedOf(case), "HANG > ${CAP_MS}ms", "wall-clock cap hit")
            is Outcome.Crash ->
                return Judgement(
                    "FAIL", "CRITICAL", expectedOf(case),
                    "CRASH ${outcome.type}: ${outcome.message}",
                    "an exception escaped the public API boundary",
                )
            else -> Unit
        }
        if (effectiveClass == "PROBE") {
            return Judgement("PROBE", null, null, describe(outcome), "probe: recorded, not scored")
        }

        return when (effectiveClass) {
            "TYPED_NONRESULT" -> when (outcome) {
                is Outcome.Err -> Judgement("PASS", null, "typed non-result", "${outcome.kind}: ${outcome.message}", null)
                is Outcome.Ok -> Judgement(
                    "FAIL", "CRITICAL", "typed non-result",
                    "Ok ${outcome.answer.exact}",
                    "a value was returned where a typed refusal was required (echo-through / undefined-leaks-finite)",
                )
                else -> error("unreachable")
            }

            "EXACT", "DECIMAL" -> {
                val expected = case.numeric30
                    ?: return Judgement("FAIL", "MAJOR", null, describe(outcome), "case carries no numeric_30 oracle")
                when (outcome) {
                    is Outcome.Err -> Judgement(
                        "FAIL", "MAJOR", expected, "${outcome.kind}: ${outcome.message}",
                        "engine declined a case the oracle says has a value",
                    )
                    is Outcome.Ok -> {
                        val got = numericOf(outcome.answer.value)
                            ?: return Judgement(
                                "FAIL", "MAJOR", expected, describe(outcome),
                                "result is not numerically comparable to a real oracle",
                            )
                        val tol = if (effectiveClass == "EXACT") EXACT_TOL
                        else case.relTol?.let { BigDecimal(it) } ?: DEFAULT_DECIMAL_TOL
                        val exp = BigDecimal(expected)
                        if (relClose(got, exp, tol)) {
                            Judgement("PASS", null, expected, got.toPlainString(), null)
                        } else {
                            Judgement(
                                "FAIL", "CRITICAL", expected, got.toPlainString(),
                                "wrong finite value (tolerance ${tol.toPlainString()})",
                            )
                        }
                    }
                    else -> error("unreachable")
                }
            }

            "FORM" -> when (outcome) {
                is Outcome.Ok ->
                    if (outcome.answer.exact == case.expectedForm) {
                        Judgement("PASS", null, case.expectedForm, outcome.answer.exact, null)
                    } else {
                        Judgement("FAIL", "MAJOR", case.expectedForm, outcome.answer.exact, "rendered form mismatch")
                    }
                is Outcome.Err -> Judgement(
                    "FAIL", "MAJOR", case.expectedForm, "${outcome.kind}: ${outcome.message}",
                    "engine declined a case with an expected display form",
                )
                else -> error("unreachable")
            }

            else -> Judgement("FAIL", "MINOR", null, describe(outcome), "unknown class '${effectiveClass}'")
        }
    }

    fun isSlow(ms: Long): Boolean = ms > SLOW_MS

    private fun expectedOf(case: Case): String? =
        case.numeric30 ?: case.expectedForm ?: if (case.cls == "TYPED_NONRESULT") "typed non-result" else null

    fun describe(o: Outcome): String = when (o) {
        is Outcome.Ok -> "Ok exact='${o.answer.exact}' decimal='${o.answer.decimal}'"
        is Outcome.Err -> "Err ${o.kind}: ${o.message}"
        is Outcome.Crash -> "CRASH ${o.type}: ${o.message}"
        Outcome.Hang -> "HANG"
    }

    fun record(case: Case, unsupported: Set<String>): Record {
        val downgraded = case.cls != "PROBE" && case.requires.any { it in unsupported }
        val effective = if (downgraded) "PROBE" else case.cls
        val (outcome, ms) = run(case.expr)
        val j = judge(case, effective, outcome)
        val v = (outcome as? Outcome.Ok)?.answer
        return Record(
            case = case,
            effectiveClass = effective,
            downgraded = downgraded,
            outcome = outcome,
            judgement = j,
            ms = ms,
            exactness = v?.let { exactnessOf(it.value) },
            display = v?.exact,
            decimal = v?.decimal,
        )
    }
}

internal object Emit {
    fun write(file: File, records: List<Record>) {
        file.parentFile?.mkdirs()
        val sb = StringBuilder("[\n")
        records.forEachIndexed { idx, r ->
            sb.append("  {")
            sb.append(MiniJson.field("id", r.case.id)).append(',')
            sb.append(MiniJson.rawField("chunk", r.case.chunk.toString())).append(',')
            sb.append(MiniJson.field("axis", r.case.axis)).append(',')
            sb.append(MiniJson.field("class", r.case.cls)).append(',')
            sb.append(MiniJson.field("effectiveClass", r.effectiveClass)).append(',')
            sb.append(MiniJson.rawField("downgraded", r.downgraded.toString())).append(',')
            sb.append(MiniJson.field("expr", r.case.expr)).append(',')
            sb.append(MiniJson.field("verdict", r.judgement.verdict)).append(',')
            sb.append(MiniJson.field("severity", r.judgement.severity)).append(',')
            sb.append(MiniJson.field("expected", r.judgement.expected)).append(',')
            sb.append(MiniJson.field("actual", r.judgement.actual)).append(',')
            sb.append(MiniJson.field("reason", r.judgement.reason)).append(',')
            sb.append(MiniJson.field("exactness", r.exactness)).append(',')
            sb.append(MiniJson.field("display", r.display)).append(',')
            sb.append(MiniJson.field("decimal", r.decimal)).append(',')
            sb.append(MiniJson.field("outcome", Runner.describe(r.outcome))).append(',')
            sb.append(MiniJson.rawField("ms", r.ms.toString())).append(',')
            sb.append(MiniJson.rawField("timing", r.case.timingCapture.toString())).append(',')
            sb.append(MiniJson.field("oracle", r.case.oracleParent)).append(',')
            sb.append(MiniJson.field("errorFamily", r.case.errorFamily)).append(',')
            sb.append(MiniJson.field("note", r.case.note))
            sb.append('}')
            if (idx != records.lastIndex) sb.append(',')
            sb.append('\n')
        }
        sb.append("]\n")
        file.writeText(sb.toString(), Charsets.UTF_8)
    }
}
