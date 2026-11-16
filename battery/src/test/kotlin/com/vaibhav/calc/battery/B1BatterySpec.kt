package com.vaibhav.calc.battery

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.io.File

/**
 * The B1 evaluation-core battery, driven against the real engine.
 *
 * Zero production code is touched: this module has no main source set, and the
 * engine is reached only through its public door (see [Runner]).
 *
 * Run the full release gate from the repository root:
 * ```
 * gradle :battery:test
 * ```
 *
 * The default run executes all 1000 cases and is a regression gate: every selected
 * case must produce a record and FAIL must be zero. PROBE remains an observation,
 * never a passing assertion; a crash or hang still fails even on a probe. Full
 * records are written before the verdict assertion for reproducible triage.
 */
class B1BatterySpec : StringSpec({

    val mode = (System.getProperty("battery.chunk") ?: "").ifBlank { "all" }
    val path = (System.getProperty("battery.file") ?: "")
        .ifBlank { "fixtures/B1-v1.6.json" }
    val outDir = (System.getProperty("battery.out") ?: "").ifBlank { "build/battery" }
    val unsupported = (System.getProperty("battery.unsupported") ?: "")
        .split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    if (mode == "cal") {
        "calibration — 17 cases plus the tokens the 17 do not cover" {
            val battery = Battery.load(path)
            battery.version shouldBe "1.6"
            battery.caseCount shouldBe 1000

            // The six `requires` tokens with no calibration case of their own. Each
            // is the literal expression its cases use, so probing it here answers
            // the same question CAL-05..CAL-17 answer for the others. Marked as an
            // extension, never passed off as part of the briefed 17.
            val extended = listOf("c_light", "eps_0", "G_grav", "k_B", "m_e", "N_A")
            // Diagnostics: the engine's own spellings, to tell "the engine lacks this
            // constant" apart from "the battery spells it differently" when triaging.
            val diagnostics = listOf("c", "G", "NA", "N_A", "k_B", "me", "m_e", "eps0", "h_planck", "g0", "phi", "tau")

            val lines = StringBuilder()
            fun probe(tag: String, id: String, expr: String, purpose: String) {
                val (outcome, ms) = Runner.run(expr)
                val v = (outcome as? Outcome.Ok)?.answer
                val supported = outcome is Outcome.Ok
                lines.append(
                    "%-9s %-10s %-14s %-9s %-46s %s\n".format(
                        tag, id, expr.take(14), if (supported) "SUPPORTED" else "unsupported",
                        Runner.describe(outcome).take(46),
                        (v?.let { Runner.exactnessOf(it.value) } ?: "") + " ${ms}ms",
                    ),
                )
                println("[$tag] $id  expr=<$expr>  purpose=$purpose")
                println("        -> ${if (supported) "SUPPORTED" else "UNSUPPORTED"} | ${Runner.describe(outcome)}" +
                    (v?.let { " | exactness=${Runner.exactnessOf(it.value)}" } ?: "") + " | ${ms}ms")
            }

            println("=== CALIBRATION (door: Calculator.evaluateAnswer) ===")
            battery.calibration.forEach { (id, expr, purpose) -> probe("CAL", id, expr, purpose) }
            println("=== EXTENDED (tokens the 17 do not cover; this harness's addition) ===")
            extended.forEach { probe("EXT-CAL", "EXT-$it", it, "requires-token '$it'") }
            println("=== DIAGNOSTIC (engine's own spellings; informational only) ===")
            diagnostics.forEach { probe("DIAG", "DIAG-$it", it, "spelling probe") }

            File(outDir).mkdirs()
            File(outDir, "calibration.txt").writeText(lines.toString(), Charsets.UTF_8)

            // Non-vacuous: the calibration set must have been read and run in full.
            battery.calibration.size shouldBe 17
        }
    } else if (mode == "diag") {
        // Triage support for chunk-1's two CRITICALs. Kept in the runner rather than
        // done in a throwaway, so the owner can reproduce the evidence the report
        // cites by running one command.
        "diagnostic — flat-chain depth (parse vs evaluate) and hex-literal scope" {
            val calc = com.vaibhav.calc.core.Calculator()

            println("=== flat left-associative chain: where does it break, and in WHICH phase? ===")
            for (n in listOf(500, 1000, 1200, 1400, 1500, 1600, 1800, 2000)) {
                val expr = List(n) { "1" }.joinToString("+")
                val parse = try {
                    when (calc.parse(expr)) {
                        is com.vaibhav.calc.core.error.CalcResult.Ok -> "Ok"
                        is com.vaibhav.calc.core.error.CalcResult.Err -> "Err(typed)"
                    }
                } catch (t: Throwable) { "THREW ${t::class.java.simpleName}" }
                val eval = try {
                    when (val r = calc.evaluate(expr)) {
                        is com.vaibhav.calc.core.error.CalcResult.Ok -> "Ok ${r.value}"
                        is com.vaibhav.calc.core.error.CalcResult.Err -> "Err(typed) ${r.error.kind}"
                    }
                } catch (t: Throwable) { "THREW ${t::class.java.simpleName}" }
                println("  terms=%-5d parse=%-22s evaluate=%s".format(n, parse, eval.take(40)))
            }

            println("=== nesting depth, for contrast (the guarded path) ===")
            for (d in listOf(200, 500, 1000, 2000)) {
                val expr = "(".repeat(d) + "1" + ")".repeat(d)
                val eval = try {
                    when (val r = calc.evaluate(expr)) {
                        is com.vaibhav.calc.core.error.CalcResult.Ok -> "Ok"
                        is com.vaibhav.calc.core.error.CalcResult.Err -> "Err(typed) ${r.error.kind}"
                    }
                } catch (t: Throwable) { "THREW ${t::class.java.simpleName}" }
                println("  depth=%-5d evaluate=%s".format(d, eval))
            }

            println("=== hex/base literals: is 0x41414141 garbage, or a supported literal (D-012)? ===")
            for (e in listOf("0x41414141", "0xFF", "0b1010", "0o17", "0x", "0xZZ", "0xDEADBEEF")) {
                val out = try {
                    when (val r = calc.evaluateAnswer(e)) {
                        is com.vaibhav.calc.core.error.CalcResult.Ok -> "Ok ${r.value.exact}"
                        is com.vaibhav.calc.core.error.CalcResult.Err -> "Err ${r.error.kind}: ${r.error.message}"
                    }
                } catch (t: Throwable) { "THREW ${t::class.java.simpleName}" }
                println("  %-12s -> %s".format(e, out))
            }
        }
    } else {
        "battery chunk $mode" {
            val battery = Battery.load(path)
            battery.version shouldBe "1.6"
            battery.caseCount shouldBe 1000
            val chunk = if (mode == "all") 0 else mode.toInt()
            val selected = if (chunk == 0) battery.cases else battery.cases.filter { it.chunk == chunk }
            val expectedSize = when (chunk) { 0 -> 1000; 1 -> 100; 2 -> 300; 3 -> 600; else -> error("bad chunk $chunk") }
            selected.size shouldBe expectedSize

            println("=== CHUNK $chunk: ${selected.size} cases; unsupported tokens = ${unsupported.ifEmpty { setOf("(none)") }} ===")
            val records = selected.map { case ->
                try { Runner.record(case, unsupported) } catch (t: Throwable) {
                    // Keep a failed record if comparison or result rendering fails,
                    // just as B2/B3 do; later cases must still be accounted for.
                    val downgraded = case.cls != "PROBE" && case.requires.any { it in unsupported }
                    val outcome = Outcome.Crash(t::class.java.name, t.message)
                    Record(case, if (downgraded) "PROBE" else case.cls, downgraded, outcome,
                        Judgement("FAIL", "CRITICAL", case.numeric30 ?: case.expectedForm,
                            Runner.describe(outcome), "battery harness threw while recording this case"),
                        0, null, null, null)
                }
            }

            // Non-vacuous: every selected case produced a record. A harness that
            // skipped work must not be able to report a clean run.
            records.size shouldBe selected.size

            File(outDir).mkdirs()
            Emit.write(File(outDir, "chunk-$chunk.json"), records)

            val pass = records.count { it.judgement.verdict == "PASS" }
            val fail = records.count { it.judgement.verdict == "FAIL" }
            val probe = records.count { it.judgement.verdict == "PROBE" }
            val critical = records.count { it.judgement.severity == "CRITICAL" }
            println("CHUNK $chunk SUMMARY: pass=$pass fail=$fail probe=$probe critical=$critical")
            println("TIMING: slowOver2s=${records.count { Runner.isSlow(it.ms) }} maxMs=${records.maxOfOrNull { it.ms } ?: 0}")
            records.filter { it.judgement.verdict == "FAIL" }.forEach {
                println("  FAIL ${it.case.id} [${it.judgement.severity}] <${it.case.expr.take(60)}> " +
                    "expected=${it.judgement.expected} actual=${it.judgement.actual}")
            }
            (pass + fail + probe) shouldBe records.size
            fail shouldBe 0
        }
    }
})
