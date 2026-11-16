package com.vaibhav.calc.core

import com.vaibhav.calc.core.entry.Editor
import com.vaibhav.calc.core.entry.EditorState
import com.vaibhav.calc.core.entry.Field
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * **D-036.4 — `)` exits a `Func` ARG.**
 *
 * The B3 run recorded S1 (probe): `)` was a silent no-op everywhere except
 * directly inside a `Paren` BODY — including inside `sin(…)`'s argument, which is
 * exactly the "dead keypress" shape D-019 killed for ⌫: the user pressing `)` after
 * `sin(30` sees nothing happen, and every physical calculator taught them it would.
 * The ruling mirrors the exit-left resolution: `)` inside an ARG performs
 * exit-right — the cursor leaves the argument to the container's right.
 *
 * Other fields (numerator, exponent, radicand…) stay no-ops: they are not typed
 * with parens anywhere, so no muscle memory expects `)` to close them.
 */
class D036CloseParenSpec : StringSpec({

    ") exits a Func argument to the container's right" {
        var s = Editor.typeFunc(EditorState(), "sin")
        s = Editor.insert(s, "30")
        s = Editor.insert(s, ')')
        s.cursor.path shouldBe emptyList()
        s.cursor.offset shouldBe 1 // right of the sin(…) node
    }

    ") twice unwinds func-in-paren, innermost first — the Casio (sin(30)) keystrokes" {
        var s = Editor.typeParen(EditorState())
        s = Editor.typeFunc(s, "sin")
        s = Editor.insert(s, "30")
        s = Editor.insert(s, ')') // exits the ARG into the paren BODY
        s.cursor.path.size shouldBe 1
        s.cursor.path[0].field shouldBe Field.BODY
        s = Editor.insert(s, ')') // exits the BODY
        s.cursor.path shouldBe emptyList()
        s.toInput() shouldBe "(sin(30))"
    }

    ") exits nested Func args one level per press" {
        var s = Editor.typeFunc(EditorState(), "sin")
        s = Editor.typeFunc(s, "cos")
        s = Editor.insert(s, "2")
        s = Editor.insert(s, ')')
        s.cursor.path.size shouldBe 1
        s.cursor.path[0].field shouldBe Field.ARG // back inside sin's argument
        s = Editor.insert(s, ')')
        s.cursor.path shouldBe emptyList()
        s.toInput() shouldBe "sin(cos(2))"
    }

    ") inside a Func nested in a denominator exits ONLY the arg — the denominator stays" {
        var s = Editor.insert(EditorState(), "1")
        s = Editor.typeFraction(s)
        s = Editor.typeFunc(s, "sin")
        s = Editor.insert(s, "3")
        s = Editor.insert(s, ')') // exits the ARG into the DEN
        s.cursor.path.size shouldBe 1
        s.cursor.path[0].field shouldBe Field.DENOMINATOR
        val before = s
        s = Editor.insert(s, ')') // DEN is not paren-typed: still a no-op
        s shouldBe before
    }

    ") stays a no-op in non-ARG, non-BODY fields" {
        for (build in listOf(
            Editor.typeRoot(EditorState()),                       // RADICAND
            Editor.typePower(Editor.insert(EditorState(), "2")),  // EXPONENT
            Editor.typeFraction(Editor.insert(EditorState(), "1")), // DENOMINATOR
        )) {
            Editor.insert(build, ')') shouldBe build
        }
    }

    ") at the root is still a no-op, never an unbalanced atom" {
        val s = Editor.insert(EditorState(), "12")
        Editor.insert(s, ')') shouldBe s
    }
})
