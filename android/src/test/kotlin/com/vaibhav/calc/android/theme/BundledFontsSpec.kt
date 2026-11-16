package com.vaibhav.calc.android.theme

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldNotBeNull
import java.io.File

/**
 * The handoff is explicit that none of the three families is reliably a system
 * face on Android (§1.3), so they must ship as resources. This is a plain-JVM
 * check on the module's res/font directory — resource LOADING needs a device,
 * but resource PRESENCE does not, and an APK cannot bundle a file that is not
 * there.
 */
class BundledFontsSpec : StringSpec({

    "all three families are bundled, in the weights the type scale uses" {
        // Gradle runs unit tests from the module directory; the fallback covers a
        // repo-root working directory. Article 11: finding NO directory must fail,
        // not pass over an empty scan.
        val dir = sequenceOf(File("src/main/res/font"), File("android/src/main/res/font"))
            .firstOrNull { it.isDirectory }
        dir.shouldNotBeNull()

        val names = dir.list()!!.toSet()
        names shouldContainAll setOf(
            // IBM Plex Sans — 400 body, 600 fn/⌫/chips, 700 display (§1.3)
            "ibm_plex_sans_regular.ttf",
            "ibm_plex_sans_semibold.ttf",
            "ibm_plex_sans_bold.ttf",
            // IBM Plex Sans Devanagari — the same superfamily, one type voice (§1.3)
            "ibm_plex_sans_devanagari_regular.ttf",
            // JetBrains Mono — 400/500/600/700 across entry, result, keys, badges
            "jetbrains_mono_regular.ttf",
            "jetbrains_mono_medium.ttf",
            "jetbrains_mono_semibold.ttf",
            "jetbrains_mono_bold.ttf",
        )

        dir.listFiles()!!.filter { it.extension == "ttf" }.forEach { ttf ->
            ttf.length() shouldBeGreaterThan 100_000L // a real face, not a stub
        }
    }
})
