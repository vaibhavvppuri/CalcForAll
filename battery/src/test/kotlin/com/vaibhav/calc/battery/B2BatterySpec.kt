package com.vaibhav.calc.battery

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.io.File

/**
 * The B2 TAPE-brain battery. Run together with B1 and B3 for release verification:
 *
 * ```
 * gradle :battery:test
 * ```
 *
 * The default executes all 500 cases. Coverage and record counts are asserted,
 * then FAIL must be zero. PROBE observations remain separate from passes. Every
 * result is written before asserting the verdict gate so failures are inspectable.
 */
class B2BatterySpec : StringSpec({

    val mode = (System.getProperty("battery.chunk") ?: "").ifBlank { "all" }
    val outDir = (System.getProperty("battery.out") ?: "").ifBlank { "build/battery" }

    fun coverage(cases: List<B2Case>): String {
        val byAxis = cases.groupingBy { it.axis }.eachCount()
        val byCls = cases.groupingBy { it.cls }.eachCount().toSortedMap()
        val sb = StringBuilder()
        sb.append("=== B2 COVERAGE (printed before execution) ===\n")
        sb.append("axis  cases  declared\n")
        for ((axis, declared) in B2Cases.declared) sb.append("  %-3s %5d  %8d\n".format(axis, byAxis[axis] ?: 0, declared))
        sb.append("  total ${cases.size} (declared ${B2Cases.declared.values.sum()})\n")
        sb.append("class histogram: $byCls\n")
        sb.append("chunk 1 = cases 1..100, chunk 2 = 101..${cases.size}\n")
        return sb.toString()
    }

    if (mode == "print") {
        "B2 coverage table (no execution)" {
            val cases = B2Cases.all()
            println(coverage(cases))
            cases.groupingBy { it.axis }.eachCount() shouldBe B2Cases.declared
        }
    } else {
        "B2 battery chunk $mode" {
            val cases = B2Cases.all()
            println(coverage(cases))
            cases.groupingBy { it.axis }.eachCount() shouldBe B2Cases.declared
            cases.map { it.id }.toSet().size shouldBe cases.size

            val selected = when (mode) {
                "1" -> cases.take(100)
                "2" -> cases.drop(100)
                "all" -> cases
                else -> error("bad chunk '$mode'")
            }
            println("=== B2 CHUNK $mode: ${selected.size} cases ===")

            val rows = selected.map { c ->
                val r = try { c.runner() } catch (t: Throwable) {
                    B2Result("FAIL", "CRITICAL", "HARNESS THREW ${t::class.java.simpleName}: ${t.message?.take(160)}", 0)
                }
                c to r
            }
            rows.size shouldBe selected.size

            File(outDir).mkdirs()
            B2Emit.write(File(outDir, "b2-chunk-$mode.json"), rows)

            val pass = rows.count { it.second.verdict == "PASS" }
            val fail = rows.count { it.second.verdict == "FAIL" }
            val probe = rows.count { it.second.verdict == "PROBE" }
            val critical = rows.count { it.second.severity == "CRITICAL" }
            println("B2 CHUNK $mode SUMMARY: pass=$pass fail=$fail probe=$probe critical=$critical")
            rows.filter { it.second.verdict == "FAIL" }.forEach { (c, r) ->
                println("  FAIL ${c.id} [${r.severity}] ${c.note}")
                println("       ${r.detail?.take(240)}")
            }
            rows.filter { it.second.verdict == "PROBE" }.forEach { (c, r) ->
                println("  PROBE ${c.id} ${c.note} :: ${r.detail?.take(180)}")
            }
            (pass + fail + probe) shouldBe rows.size
            fail shouldBe 0
        }
    }
})
