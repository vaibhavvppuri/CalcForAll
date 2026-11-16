package com.vaibhav.calc.core

import com.vaibhav.calc.core.format.Formatter
import com.vaibhav.calc.core.lex.Tokenizer
import com.vaibhav.calc.core.num.Num
import com.vaibhav.calc.core.num.div
import com.vaibhav.calc.core.num.minus
import com.vaibhav.calc.core.num.plus
import com.vaibhav.calc.core.num.times
import com.vaibhav.calc.core.num.unaryMinus
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.checkAll

/**
 * Property tests (Article 4): algebraic laws over the exact number tower,
 * round-trip render→parse, and the crash-safety guarantee — *any* input either
 * evaluates or returns a typed error, but never throws.
 */
class PropertySpec : StringSpec({

    val rat: Arb<Num> = Arb.bind(Arb.long(-200L..200L), Arb.long(1L..200L)) { n, d -> Num.of(n, d) }
    val nonzero: Arb<Num> =
        Arb.bind(Arb.long(-200L..200L).filter { it != 0L }, Arb.long(1L..200L)) { n, d -> Num.of(n, d) }

    "addition is commutative" {
        checkAll(rat, rat) { a, b -> (a + b).compareTo(b + a) shouldBe 0 }
    }
    "multiplication is commutative" {
        checkAll(rat, rat) { a, b -> (a * b).compareTo(b * a) shouldBe 0 }
    }
    "addition is associative" {
        checkAll(rat, rat, rat) { a, b, c -> ((a + b) + c).compareTo(a + (b + c)) shouldBe 0 }
    }
    "multiplication is associative" {
        checkAll(rat, rat, rat) { a, b, c -> ((a * b) * c).compareTo(a * (b * c)) shouldBe 0 }
    }
    "multiplication distributes over addition" {
        checkAll(rat, rat, rat) { a, b, c -> (a * (b + c)).compareTo(a * b + a * c) shouldBe 0 }
    }
    "additive inverse: a + (-a) == 0 and a - a == 0" {
        checkAll(rat) { a ->
            (a + (-a)).compareTo(Num.ZERO) shouldBe 0
            (a - a).compareTo(Num.ZERO) shouldBe 0
        }
    }
    "multiplicative inverse: a / a == 1 for a != 0" {
        checkAll(nonzero) { a -> (a / a).compareTo(Num.ONE) shouldBe 0 }
    }
    "subtraction undoes addition: (a + b) - b == a" {
        checkAll(rat, rat) { a, b -> ((a + b) - b).compareTo(a) shouldBe 0 }
    }

    "round-trip: render exact → parse → evaluate returns the same value" {
        checkAll(rat) { a ->
            val rendered = Formatter.exact(a)
            Calculator().num(rendered).compareTo(a) shouldBe 0
        }
    }

    // Article 4 (amended, A3): the fuzz alphabet is derived MECHANICALLY from the
    // tokenizer's own accepted-character set — never a hand-written list. If the
    // tokenizer accepts a character, the fuzzer emits it. (The earlier hand-picked
    // alphabet that excluded 'e' and '^' is exactly the bug this prevents.)
    val alphabet = Tokenizer.ACCEPTED_CHARACTERS
    val garbage: Arb<String> =
        Arb.list(Arb.int(alphabet.indices), 0..9).map { idxs -> idxs.joinToString("") { alphabet[it].toString() } }

    "fuzz (tokenizer-derived alphabet): no input ever throws, only typed results" {
        checkAll(2000, garbage) { s -> shouldNotThrowAny { Calculator().evaluate(s) } }
    }

    // The router is public surface too, so Article 4 (amended) fuzzes it as well.
    // The alphabet now carries '=', '→', newline and superscripts automatically,
    // because it is derived from the tokenizer rather than hand-written.
    "fuzz (tokenizer-derived alphabet): the router never throws either" {
        checkAll(2000, garbage) { s -> shouldNotThrowAny { Calculator().route(s) } }
    }

    "curated nasty inputs never throw" {
        val nasty = listOf(
            "", "   ", "1/0", ")(", "((()))", "2++2", "*3", "sin()", "sin(1,2)",
            "log()", "!5", "3!!", "2^^3", "1e", ".", "..", "1..2", "(-8)^(1/3)",
            "0^0", "0^-1", "tan(90)", "asin(2)", "sqrt(-1)", "ln(0)", "1/(1-1)",
            "999^999", "50!", "pi()", "e e", "2 2 2", "x", "%", "of", "20% of",
            "2(3)(4)", "----5", "+++5", "sqrt", "1+", "(1", "1)",
        )
        nasty.forEach { s -> shouldNotThrowAny { Calculator().evaluate(s) } }
    }
})
