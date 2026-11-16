package com.vaibhav.calc.core

import com.vaibhav.calc.core.entry.Editor
import com.vaibhav.calc.core.entry.EditorState
import com.vaibhav.calc.core.tape.EntryId
import com.vaibhav.calc.core.tape.EntryValue
import com.vaibhav.calc.core.tape.Tape
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * **D-036.8 — the dependency-wait wording is "waiting on #n", everywhere.**
 *
 * The design mock carried `why:'it failed'` on Blocked rows — a lie for the
 * measured causes that are not failures at all: a dependency that is Empty, or
 * `Computed` holding roots/a verdict with no referenceable value (D-021, probes
 * B2-S-016/017). The one user-facing string lives HERE, on the state itself, so
 * the M7-2 tape row cannot invent a second wording (Article 12).
 */
class D036WordingSpec : StringSpec({

    fun src(text: String): EditorState = Editor.insert(EditorState(), text)

    "D-036.8: a blocked row reads 'waiting on #n'" {
        val t = Tape()
        t.append(src("1/0")); t.append(src("#1+1"))
        t.entry(EntryId(2))!!.value.shouldBeInstanceOf<EntryValue.Blocked>()
            .description shouldBe "waiting on #1"
    }

    "D-036.8: the wording names the IMMEDIATE cause in a chain" {
        val t = Tape()
        t.append(src("1/0")); t.append(src("#1+1")); t.append(src("#2+1"))
        t.entry(EntryId(3))!!.value.shouldBeInstanceOf<EntryValue.Blocked>()
            .description shouldBe "waiting on #2"
    }

    "D-036.8: blocked by a SUCCESSFUL solve still says waiting — never 'it failed'" {
        val t = Tape()
        t.append(src("x^2-5x+6=0")); t.append(src("#1*2"))
        val b = t.entry(EntryId(2))!!.value.shouldBeInstanceOf<EntryValue.Blocked>()
        b.description shouldBe "waiting on #1"
    }

    "D-036.8: blocked by an Empty row says waiting" {
        val t = Tape()
        t.append(src("")); t.append(src("#1*2"))
        t.entry(EntryId(2))!!.value.shouldBeInstanceOf<EntryValue.Blocked>()
            .description shouldBe "waiting on #1"
    }
})
