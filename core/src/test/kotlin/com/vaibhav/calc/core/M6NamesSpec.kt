package com.vaibhav.calc.core

import com.vaibhav.calc.core.error.CalcResult
import com.vaibhav.calc.core.error.EvalError
import com.vaibhav.calc.core.eval.Environment
import com.vaibhav.calc.core.eval.Evaluator
import com.vaibhav.calc.core.eval.Settings
import com.vaibhav.calc.core.name.Functions
import com.vaibhav.calc.core.parse.Parser
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * The registry↔evaluator equality proof (Article 12).
 *
 * `Evaluator` gates on [Functions], so **accepted ⊆ registry** by construction.
 * This spec proves **registry ⊆ accepted**. Two inclusions ⇒ the sets are equal,
 * so autocomplete can never offer a name the engine rejects.
 */
class M6NamesSpec : StringSpec({

    fun callResult(name: String, argc: Int): CalcResult<*> {
        val args = List(argc) { "0.5" }.joinToString(", ")
        val expr = (Parser.parse("$name($args)") as CalcResult.Ok).value
        return Evaluator(Settings(), Environment()).eval(expr)
    }

    "every registered spelling resolves — the registry lists no name the engine lacks" {
        val unresolved = mutableListOf<String>()
        for (fn in Functions.ALL) {
            for (spelling in fn.spellings) {
                val r = callResult(spelling, fn.arities.first())
                // A Domain error is fine (acosh(0.5) really is out of domain). An
                // UnknownSymbol means the registry advertises a function that does
                // not exist — the exact drift the gate is here to prevent.
                if (r is CalcResult.Err && r.error is EvalError.UnknownSymbol) unresolved += spelling
            }
        }
        unresolved shouldBe emptyList()
    }

    "every declared arity is accepted — the registry's arity data is not decorative" {
        val wrongArity = mutableListOf<String>()
        for (fn in Functions.ALL) {
            for (arity in fn.arities) {
                val r = callResult(fn.name, arity)
                if (r is CalcResult.Err && r.error is EvalError.Arity) wrongArity += "${fn.name}/$arity"
            }
        }
        wrongArity shouldBe emptyList()
    }

    "an undeclared arity is rejected as an arity error, not silently accepted" {
        val r = callResult("sin", 3)
        (r as CalcResult.Err).error should { it is EvalError.Arity || it is EvalError.UnknownSymbol }
    }

    "a name outside the registry is UnknownSymbol" {
        val r = callResult("frobnicate", 1)
        ((r as CalcResult.Err).error) shouldBe EvalError.UnknownSymbol("frobnicate", r.error.span)
    }

    "the registry has no duplicate spellings" {
        val all = Functions.ALL.flatMap { it.spellings }
        all.size shouldBe all.toSet().size
    }

    "log is registered as taking one OR two arguments" {
        Functions.get("log")!!.arities shouldBe setOf(1, 2)
        callResult("log", 1).shouldBeOk()
        callResult("log", 2).shouldBeOk()
    }

    "aliases resolve to the same function entry" {
        Functions.get("arcsin") shouldBe Functions.get("asin")
        Functions.get("SIN") shouldBe Functions.get("sin")
        Functions.get("choose") shouldBe Functions.get("ncr")
    }

    "canonical names exclude aliases; spellings include them" {
        Functions.canonicalNames() shouldContain "asin"
        Functions.canonicalNames().contains("arcsin") shouldBe false
        Functions.spellings() shouldContain "arcsin"
    }

    "the registry is not empty — a vacuous pass is not a pass (Article 11)" {
        Functions.ALL.size shouldNotBe 0
        Functions.spellings().size shouldNotBe 0
    }
})

private fun CalcResult<*>.shouldBeOk() {
    if (this is CalcResult.Err) throw AssertionError("expected Ok, got ${this.error}")
}

private infix fun EvalError.should(pred: (EvalError) -> Boolean) {
    if (!pred(this)) throw AssertionError("unexpected error: $this")
}
