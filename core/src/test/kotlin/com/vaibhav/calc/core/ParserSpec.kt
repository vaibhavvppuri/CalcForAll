package com.vaibhav.calc.core

import com.vaibhav.calc.core.ast.Binary
import com.vaibhav.calc.core.ast.BinaryOp
import com.vaibhav.calc.core.ast.Unary
import com.vaibhav.calc.core.ast.UnaryOp
import com.vaibhav.calc.core.error.CalcResult
import com.vaibhav.calc.core.num.Num
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Precedence and associativity — especially the classic unary-minus-vs-power
 * trap that the brief calls out by name.
 */
class ParserSpec : StringSpec({

    val c = Calculator()

    infix fun String.is_(n: Long) {
        c.num(this).compareTo(Num.of(n)) shouldBe 0
    }

    "-2^2 == -4 (unary minus is looser than ^)" { "-2^2" is_ -4 }
    "(-2)^2 == 4" { "(-2)^2" is_ 4 }
    "2^-2 == 1/4" { c.num("2^-2").compareTo(Num.of(1, 4)) shouldBe 0 }
    "2^3^2 == 512 (^ is right-associative)" { "2^3^2" is_ 512 }
    "10 - 3 - 2 == 5 (- is left-associative)" { "10-3-2" is_ 5 }
    "2 + 3 * 4 == 14 (order of operations)" { "2+3*4" is_ 14 }
    "(2 + 3) * 4 == 20 (parentheses)" { "(2+3)*4" is_ 20 }

    "implicit multiply: 2(3) == 6" { "2(3)" is_ 6 }
    "implicit multiply: 2(3+4) == 14" { "2(3+4)" is_ 14 }
    "implicit multiply: (2)(3)(4) == 24" { "(2)(3)(4)" is_ 24 }
    "implicit multiply binds looser than ^: 2A^2 with A=3 == 18" {
        c.setVariable("A", Num.of(3)) // x is an equation unknown now (D-016.4); A is a register
        "2A^2" is_ 18
    }

    "factorial: 3! == 6" { "3!" is_ 6 }
    "factorial binds tighter than ^: 2^3! == 64" { "2^3!" is_ 64 }
    "factorial then power: 3!^2 == 36" { "3!^2" is_ 36 }
    "negated factorial: -3! == -6" { "-3!" is_ -6 }

    "AST of -2^2 is Neg(Pow(2, 2)), not (Neg 2)^2" {
        val ast = (c.parse("-2^2") as CalcResult.Ok).value
        val neg = ast.shouldBeInstanceOf<Unary>()
        neg.op shouldBe UnaryOp.NEG
        val pow = neg.operand.shouldBeInstanceOf<Binary>()
        pow.op shouldBe BinaryOp.POW
    }

    "the parser produces a tree, never a value (parse succeeds without evaluating 1/0)" {
        (c.parse("1/0") is CalcResult.Ok) shouldBe true // parsing 1/0 is fine; only evaluation errors
    }
})
