package com.vaibhav.calc.battery

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.io.File

/**
 * The B3 EDITOR-brain battery. Run together with B1 and B2 for release verification:
 *
 * ```
 * gradle :battery:test
 * ```
 *
 * The default executes all 520 cases. Generated coverage, unique IDs and complete
 * records are asserted, then FAIL must be zero. PROBE remains an observation.
 * Records are written to `build/battery/b3-chunk-N.json` before the verdict gate.
 */
class B3BatterySpec : StringSpec({

    val mode = (System.getProperty("battery.chunk") ?: "").ifBlank { "all" }
    val outDir = (System.getProperty("battery.out") ?: "").ifBlank { "build/battery" }

    fun coverage(cases: List<B3Case>): String {
        val byAxis = cases.groupingBy { it.axis }.eachCount().toSortedMap()
        val byCls = cases.groupingBy { it.cls }.eachCount().toSortedMap()
        val sb = StringBuilder()
        sb.append("=== B3 COVERAGE (printed before execution) ===\n")
        sb.append("axis  cases  declared\n")
        for ((axis, count) in byAxis) sb.append("  %-3s %5d  %8d\n".format(axis, count, B3Cases.declared[axis] ?: -1))
        sb.append("  total ${cases.size} (declared ${B3Cases.declared.values.sum()})\n")
        sb.append("class histogram: $byCls\n")
        sb.append("chunk 1 = cases 1..100, chunk 2 = 101..${cases.size}\n")
        return sb.toString()
    }

    if (mode == "print") {
        "B3 coverage table (no execution)" {
            val cases = B3Cases.all()
            println(coverage(cases))
            cases.groupingBy { it.axis }.eachCount() shouldBe B3Cases.declared
        }
    } else {
        "B3 battery chunk $mode" {
            val cases = B3Cases.all()
            println(coverage(cases))
            // The harness must generate exactly the declared battery, or nothing runs.
            cases.groupingBy { it.axis }.eachCount() shouldBe B3Cases.declared
            cases.map { it.id }.toSet().size shouldBe cases.size // ids unique

            val selected = when (mode) {
                "1" -> cases.take(100)
                "2" -> cases.drop(100)
                "all" -> cases
                else -> error("bad chunk '$mode'")
            }
            println("=== B3 CHUNK $mode: ${selected.size} cases ===")

            val rows = selected.map { c ->
                val r = try { c.runner() } catch (t: Throwable) {
                    B3Result("FAIL", "CRITICAL", "HARNESS THREW ${t::class.java.simpleName}: ${t.message?.take(160)}", 0, null)
                }
                c to r
            }
            rows.size shouldBe selected.size // non-vacuous: every case produced a record

            File(outDir).mkdirs()
            B3Emit.write(File(outDir, "b3-chunk-$mode.json"), rows)

            val pass = rows.count { it.second.verdict == "PASS" }
            val fail = rows.count { it.second.verdict == "FAIL" }
            val probe = rows.count { it.second.verdict == "PROBE" }
            val critical = rows.count { it.second.severity == "CRITICAL" }
            println("B3 CHUNK $mode SUMMARY: pass=$pass fail=$fail probe=$probe critical=$critical")
            rows.filter { it.second.verdict == "FAIL" }.forEach { (c, r) ->
                println("  FAIL ${c.id} [${r.severity}] ${c.note}")
                println("       ${r.detail?.take(220)}")
            }
            rows.filter { it.second.verdict == "PROBE" }.forEach { (c, r) ->
                println("  PROBE ${c.id} ${c.note} :: ${r.detail?.take(160)}")
            }
            (pass + fail + probe) shouldBe rows.size
            fail shouldBe 0
        }
    }
})
