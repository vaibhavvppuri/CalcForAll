package com.vaibhav.calc.core

import com.vaibhav.calc.core.constants.Constants
import com.vaibhav.calc.core.eval.Environment
import com.vaibhav.calc.core.eval.QuantityValue
import com.vaibhav.calc.core.eval.RealValue
import com.vaibhav.calc.core.eval.Settings
import com.vaibhav.calc.core.name.NameKind
import com.vaibhav.calc.core.name.ReservedNames
import com.vaibhav.calc.core.route.ExpressionRouter
import com.vaibhav.calc.core.route.Outcome
import com.vaibhav.calc.core.route.RouteResult
import com.vaibhav.calc.core.units.Dimension
import com.vaibhav.calc.core.units.Units
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * **D-026 — one name table, not two.**
 *
 * *"What symbol does the engine know"* is one question (design §09 note 3). It had
 * two answers: `Units` was reachable from the expression language and `Constants` was
 * not — it was exposed only through `Calculator.physicalConstant()`, a typed API the
 * router never consulted. So the reference browser taught `c`, `φ` and `Nₐ` while the
 * engine answered `UnknownSymbol`, and taught `g`/`h` while the engine answered gram
 * and hour.
 *
 * *"Wrong meaning, silently"* is the D-014 failure mode, and it is the worst of the
 * three: `phi` at least fails loudly.
 */
class ConstantsVocabularySpec : StringSpec({

    fun route(input: String): RouteResult = ExpressionRouter(Settings(), Environment()).route(input)

    fun value(input: String) = ((route(input) as RouteResult.Ok).outcome as Outcome.Scalar).value

    // --- the whole point: the browser's vocabulary IS the router's ------------

    "EVERY symbol the registry lists resolves in the expression language" {
        val unresolved = Constants.SYMBOLS.filter { route(it) !is RouteResult.Ok }
        unresolved shouldBe emptyList()
    }

    "NO constant symbol collides with a unit name — checked at construction, loudly" {
        val collisions = Constants.SYMBOLS.filter { Units.get(it) != null }
        collisions shouldBe emptyList()
    }

    "the reserved-name set is DERIVED from the registry, not hand-listed" {
        // It was setOf("pi","e","i","tau") while the registry held ten more.
        ReservedNames.CONSTANTS shouldBe (Constants.SYMBOLS + "i")
        for (s in Constants.SYMBOLS) ReservedNames.kindOf(s) shouldBe NameKind.CONSTANT
    }

    "a constant cannot be assigned, and the refusal names the collision" {
        for (s in listOf("c", "phi", "NA")) {
            ReservedNames.assignmentBlockedReason(s)!!.contains("reserved constant") shouldBe true
        }
    }

    // --- what the design's browser promised, now true -------------------------

    "the symbols the design's browser teaches all work — or are honestly renamed" {
        (route("c") is RouteResult.Ok) shouldBe true
        (route("phi") is RouteResult.Ok) shouldBe true
        (route("NA") is RouteResult.Ok) shouldBe true
        (route("pi") is RouteResult.Ok) shouldBe true
        (route("e") is RouteResult.Ok) shouldBe true
        (route("g0") is RouteResult.Ok) shouldBe true // standard gravity — NOT `g`
        (route("h_planck") is RouteResult.Ok) shouldBe true // Planck — NOT `h`
    }

    "g is the GRAM and h is the HOUR — the unit wins, and it wins consistently" {
        // The design's browser taught g = 9.80665 m/s² and h = 6.626e-34 J·s. The
        // engine cannot mean both. One name, one meaning: the units keep these, and
        // the constants take suffixed spellings (the `e_charge` convention).
        (value("g") as QuantityValue).q.unit.name shouldBe "g"
        (value("h") as QuantityValue).q.unit.name shouldBe "hr"
        Constants.SYMBOLS.contains("g") shouldBe false
        Constants.SYMBOLS.contains("h") shouldBe false
    }

    // --- constants are QUANTITIES, so dimensional analysis still holds --------

    "c is a speed, not a bare number — c·2s is a length" {
        val c = value("c") as QuantityValue
        c.q.unit.dimension shouldBe Dimension.SPEED
        val d = value("c * 2 s") as QuantityValue
        d.q.unit.dimension shouldBe Dimension.LENGTH
    }
    "adding a constant to the wrong dimension is refused, not silently allowed" {
        // If `c` were a bare number this would quietly succeed and be nonsense.
        (route("c + 1 kg") is RouteResult.Err) shouldBe true
    }
    "g0 is an acceleration" {
        (value("g0") as QuantityValue).q.unit.dimension shouldBe Dimension.LENGTH / Dimension.TIME.pow(2)
    }
    "a dimensionless math constant stays a plain number" {
        (value("phi") is RealValue) shouldBe true
        (value("pi") is RealValue) shouldBe true
    }
    /**
     * **A known limitation, asserted so it cannot be forgotten — see D-026's note.**
     *
     * `φ = (1+√5)/2` is a *binomial* surd, and the tower has no type for one:
     * `Rational | Surd | SymConst | Dec` holds `√5` but not `1+√5`. So φ decays to a
     * [com.vaibhav.calc.core.num.Dec], and `φ² = φ + 1` — the defining property of the
     * golden ratio — cannot be confirmed. Until 1.3 it came back **false**; since the tenth
     * review pass an estimate that agrees with the other side to every digit it can be trusted
     * to is neither confirmed nor denied, so the statement is now refused as undecidable.
     * That is the honest answer for a decimal, and still not the True the mathematics deserves.
     *
     * This is `sin(45)`'s class with a different cause. There, the engine threw away
     * an exactness it had; here the tower cannot express the value at all, so no fast
     * path can fix it. The remedy is a quadratic-irrational type (ℚ(√d) — the shape of
     * the design's own `SurdSum = Σ (n/d)·√r`), which is an Article 3 decision about
     * the number tower and is the owner's, not something to slip into a name-table fix.
     *
     * Recorded as a test rather than a comment so the day the tower gains that type,
     * this goes red and tells someone.
     */
    "KNOWN GAP: φ² = φ + 1 cannot be decided, because the tower cannot hold a binomial surd" {
        val r = route("phi^2 = phi + 1")
        // Not False any more (1.3), and not yet True: the day the tower can hold 1+√5 this goes red.
        ((r as RouteResult.Err).error.detail.contains("cannot be said whether they are equal")) shouldBe true
        // φ is honest about what it is — a decimal, not a fake exact form.
        (value("phi") is RealValue) shouldBe true
        (value("phi") as RealValue).num::class.simpleName shouldBe "Dec"
        // √5 alone IS exact, which is what makes the gap specifically about binomials.
        (value("sqrt2") as RealValue).num::class.simpleName shouldBe "Surd"
    }

    // --- case-sensitivity is load-bearing (D-016.4) ---------------------------

    "case still separates the register C from the speed of light c" {
        ReservedNames.kindOf("C") shouldBe NameKind.REGISTER
        ReservedNames.kindOf("c") shouldBe NameKind.CONSTANT
    }
    "register E is still not Euler's e, and G is not the gram g" {
        ReservedNames.kindOf("E") shouldBe NameKind.REGISTER
        ReservedNames.kindOf("e") shouldBe NameKind.CONSTANT
        ReservedNames.kindOf("G") shouldBe NameKind.CONSTANT // Newtonian gravitation
        ReservedNames.kindOf("g") shouldBe NameKind.UNIT // gram
    }

    "an unknown name is still UnknownSymbol — the gate did not open everything" {
        (route("frobnicate") is RouteResult.Err) shouldBe true
        (route("zz") is RouteResult.Err) shouldBe true
    }

    "the registry is not empty — a vacuous pass is not a pass (Article 11)" {
        (Constants.SYMBOLS.size >= 15) shouldBe true
        Constants.allPhysical().size shouldBe 10
    }
})
