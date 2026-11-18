package com.vaibhav.calc.core

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldNotBeNull
import java.io.File

/**
 * `core` is a plain JVM module: it compiles against JDK 21 and its tests run there, so
 * a Java member that Android only gained recently compiles, passes every test, and then
 * crashes on an older phone. Android lint cannot help — it does not scan this module.
 *
 * This happened in 1.2: `BigInteger.TWO` (Android 13 / API 33) reached `NumberTheory`
 * while the app's floor is Android 6 (API 23). Lint caught the same constant in the
 * Android adapter; this tripwire is what watches the engine.
 *
 * It is a denylist of known offenders, not a proof of compatibility: add a member here
 * whenever one is found.
 */
class AndroidApiFloorSpec : StringSpec({

    val banned = mapOf(
        "BigInteger.TWO (Android 13+)" to Regex("""BigInteger\.TWO\b"""),
        "BigInteger.sqrt() (Android 13+)" to Regex("""\.sqrt\(\s*\)"""),
        "BigDecimal.sqrt(MathContext) (Android 13+)" to Regex("""\.sqrt\(\s*(mc|wmc|wp|MathContext\b)"""),
        "List.of / Set.of / Map.of (Android 11+)" to Regex("""\b(List|Set|Map)\.of\("""),
    )

    "the engine uses no Java member that is missing on the app's oldest supported Android" {
        // Gradle runs tests from the module directory; the fallback covers a repo-root run.
        val root = sequenceOf(File("src/main/kotlin"), File("core/src/main/kotlin")).firstOrNull { it.isDirectory }
        root.shouldNotBeNull()
        val sources = root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        // A scan of nothing must fail, not pass quietly (Article 11).
        sources.size shouldBeGreaterThan 50

        val offenders = sources.flatMap { file ->
            file.readLines().withIndex().flatMap { (index, line) ->
                val code = line.substringBefore("//")
                banned.filter { (_, pattern) -> pattern.containsMatchIn(code) }
                    .map { (name, _) -> "${file.relativeTo(root)}:${index + 1}: $name" }
            }
        }
        offenders.shouldBeEmpty()
    }
})
