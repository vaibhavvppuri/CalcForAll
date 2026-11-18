package com.vaibhav.calc.android

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import java.io.File

/**
 * A tripwire, not a UI test: every text field in the app must pass [MathKeyboard], which
 * switches the phone keyboard's autocorrect and capitalisation off. Without it a keyboard
 * rewrites what the user typed before the calculator sees it — on an Android 14 emulator
 * `5 lakh` arrived as `5 leaky`. The fields hold maths, units, names and dates, never prose.
 *
 * This reads the source. It can say that the option is passed; only a device can say that
 * a given keyboard honours it (recorded in docs/VERIFICATION.md).
 */
class TypedFieldsSpec : StringSpec({
    "every text field switches the keyboard's autocorrect and capitalisation off" {
        val root = sequenceOf(File("src/main/kotlin"), File("android/src/main/kotlin")).firstOrNull { it.isDirectory }
        withClue("android main sources not found from ${File(".").absolutePath}") { (root != null) shouldBe true }
        val call = Regex("""(?<![A-Za-z])(OutlinedTextField|BasicTextField|TextField)\(""")
        val offenders = ArrayList<String>()
        var fields = 0
        root!!.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
            val text = file.readText()
            val starts = call.findAll(text).map { it.range.first }.toList()
            starts.forEachIndexed { k, at ->
                fields++
                val end = minOf(starts.getOrElse(k + 1) { text.length }, at + 1500)
                if ("keyboardOptions = MathKeyboard" !in text.substring(at, end)) {
                    offenders += "${file.name}:${text.substring(0, at).count { it == '\n' } + 1}"
                }
            }
        }
        withClue("text fields without MathKeyboard: $offenders") { offenders shouldBe emptyList() }
        // The app has fifteen typed fields today; a scan that finds none is a broken scan.
        fields shouldBeGreaterThanOrEqual 15
    }

    /**
     * The same kind of tripwire for the answer area. A long answer runs past the display strip
     * and scrolls; on an Android 14 emulator the 51 digits of `floor(pi×10^50)` ended in a line
     * cut off by the keys, with nothing to say that the answer and its decimal reading went on
     * below. The strip must show a cue while there is more to scroll to, in both directions.
     */
    "the answer area says so when an answer continues beyond what is shown" {
        val file = sequenceOf(File("src/main/kotlin"), File("android/src/main/kotlin")).firstOrNull { it.isDirectory }!!
            .walkTopDown().first { it.name == "CalcApp.kt" }
        val text = file.readText()
        val strip = text.substring(text.indexOf("private fun DisplayStrip("))
            .let { it.substring(0, it.indexOf("@Composable", 1).takeIf { end -> end > 0 } ?: it.length) }
        withClue("DisplayStrip no longer scrolls") { ("verticalScroll(scroll)" in strip) shouldBe true }
        withClue("no cue for more below") { (Regex("""if \(scroll\.canScrollForward\) ScrollCue\(""").containsMatchIn(strip)) shouldBe true }
        withClue("no cue for more above") { (Regex("""if \(scroll\.canScrollBackward\) ScrollCue\(""").containsMatchIn(strip)) shouldBe true }
    }
})
