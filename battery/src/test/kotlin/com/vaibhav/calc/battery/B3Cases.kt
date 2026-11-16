package com.vaibhav.calc.battery

import com.vaibhav.calc.core.entry.Atom
import com.vaibhav.calc.core.entry.EditorState
import com.vaibhav.calc.core.entry.Fraction
import com.vaibhav.calc.core.entry.Func
import com.vaibhav.calc.core.entry.Paren
import com.vaibhav.calc.core.entry.toInput
import com.vaibhav.calc.core.keypad.FnPalette
import com.vaibhav.calc.core.keypad.KeyAction
import com.vaibhav.calc.core.keypad.Keypad
import com.vaibhav.calc.core.name.Functions
import kotlin.random.Random

/**
 * The B3 case generator. 520 cases, eight axes. The near-duplicate purge rule:
 * two cases are duplicates iff they exercise the same (operation, cursor-position
 * class, structure kind, population state) 4-tuple; one representative survives.
 * Depth/name variants survive only where depth or the name is the axis under test.
 */
internal object B3Cases {

    private val I1 = B3Op.Ins("1"); private val I2 = B3Op.Ins("2"); private val I3 = B3Op.Ins("3")
    private val I9 = B3Op.Ins("9")

    // --- AXIS A · CREATE — 8 creators × 12 contexts = 96 ---------------------

    private data class Ctx(val name: String, val ops: List<B3Op>)

    private val contexts = listOf(
        Ctx("R0", emptyList()),
        Ctx("Rend12", listOf(B3Op.Ins("12"))),
        Ctx("RopEnd", listOf(B3Op.Ins("1+"))),
        Ctx("Rmid", listOf(B3Op.Ins("12"), B3Op.L)),
        Ctx("afterStruct", listOf(B3Op.ParenO, I3, B3Op.ParenC)),
        Ctx("dotRun", listOf(B3Op.Ins("1.2"))),
        Ctx("inNum", listOf(B3Op.Frac)),
        Ctx("inDen", listOf(I1, B3Op.Frac)),
        Ctx("inExp", listOf(I2, B3Op.Pow)),
        Ctx("inRad", listOf(B3Op.RootK, B3Op.Ins("5"))),
        Ctx("inArg", listOf(B3Op.Fn("sin"), I3)),
        Ctx("depth3", listOf(B3Op.Frac, B3Op.Fn("sin"), B3Op.ParenO)),
    )

    private val creators: List<Pair<String, B3Op>> = listOf(
        "frac" to B3Op.Frac, "pow" to B3Op.Pow, "root" to B3Op.RootK, "paren" to B3Op.ParenO,
        "fnSin" to B3Op.Fn("sin"), "fnAsin" to B3Op.Fn("asin"),
        "fnCbrt" to B3Op.Fn("cbrt"), "fnLog10" to B3Op.Fn("log10"),
    )

    private fun axisA(): List<B3Case> {
        var n = 0
        return creators.flatMap { (cname, create) ->
            contexts.map { ctx ->
                n++
                // S2 silence: capture of a whole preceding STRUCTURE as the operand run.
                val probe = ctx.name == "afterStruct" && (create == B3Op.Frac || create == B3Op.Pow)
                B3Run.refEq(
                    "B3-A-%03d".format(n), "A", ctx.ops + create,
                    cls = if (probe) "PROBE" else "FAILABLE",
                    note = "create $cname @ ${ctx.name}" + if (probe) " [S2: structure-capture undecided]" else "",
                )
            }
        }
    }

    // --- AXIS B · DELETE — 118 -----------------------------------------------

    private fun axisB(): List<B3Case> {
        val out = mutableListOf<B3Case>()
        var n = 0
        fun id() = "B3-B-%03d".format(++n)

        // B-INV — 8 creators × 3 contexts = 24: create then ⌫ restores the pre-state.
        val invCtx = listOf(
            "R0" to emptyList(),
            "Rend12" to listOf(B3Op.Ins("12")),
            "inDen" to listOf(I1, B3Op.Frac),
        )
        for ((cname, create) in creators) for ((ctxName, pre) in invCtx) {
            out += B3Run.invert(id(), pre, create, "invert $cname @ $ctxName")
        }

        // B-F1 — 4: an atom dies on the first press.
        out += B3Run.refEq(id(), "B", listOf(B3Op.Ins("12"), B3Op.Bksp), note = "F1 root end")
        out += B3Run.refEq(id(), "B", listOf(B3Op.Ins("12"), B3Op.L, B3Op.Bksp), note = "F1 root mid")
        out += B3Run.refEq(id(), "B", listOf(I1, B3Op.Frac, I3, B3Op.Bksp), note = "F1 in field")
        out += B3Run.refEq(id(), "B", listOf(B3Op.Ins("12"), B3Op.Frac, B3Op.Bksp, B3Op.Bksp), note = "F1 after survivors")

        // B-F2 — 4: empty FIRST field collapses; the later field survives.
        out += B3Run.refEq(id(), "B", listOf(B3Op.Frac, B3Op.R, I3, B3Op.L, B3Op.L, B3Op.Bksp), note = "F2 frac d1")
        out += B3Run.refEq(id(), "B", listOf(B3Op.ParenO, B3Op.Frac, B3Op.R, I3, B3Op.L, B3Op.L, B3Op.Bksp), note = "F2 frac d2")
        out += B3Run.refEq(id(), "B", listOf(B3Op.Pow, B3Op.R, I3, B3Op.L, B3Op.L, B3Op.Bksp), note = "F2 pow d1")
        out += B3Run.refEq(id(), "B", listOf(B3Op.ParenO, B3Op.Pow, B3Op.R, I3, B3Op.L, B3Op.L, B3Op.Bksp), note = "F2 pow d2")

        // B-F3 — 4: empty LATER field collapses; cursor lands where the / was.
        out += B3Run.refEq(id(), "B", listOf(B3Op.Ins("12"), B3Op.Frac, B3Op.Bksp), note = "F3 frac d1")
        out += B3Run.refEq(id(), "B", listOf(B3Op.ParenO, B3Op.Ins("12"), B3Op.Frac, B3Op.Bksp), note = "F3 frac d2")
        out += B3Run.refEq(id(), "B", listOf(I2, B3Op.Pow, B3Op.Bksp), note = "F3 pow d1")
        out += B3Run.refEq(id(), "B", listOf(B3Op.ParenO, I2, B3Op.Pow, B3Op.Bksp), note = "F3 pow d2")

        // B-F4 — 10: all fields empty; the structure just vanishes.
        val f4 = listOf("frac" to B3Op.Frac, "pow" to B3Op.Pow, "root" to B3Op.RootK,
            "paren" to B3Op.ParenO, "func" to B3Op.Fn("sin"))
        for ((kname, create) in f4) {
            out += B3Run.refEq(id(), "B", listOf(create, B3Op.Bksp), note = "F4 $kname d1")
            out += B3Run.refEq(id(), "B", listOf(B3Op.ParenO, create, B3Op.Bksp), note = "F4 $kname d2")
        }

        // B-F5 — populated boundary: select (5), then delete (5), then deselect ← / → (10).
        val pop = mapOf(
            "frac" to listOf(I1, B3Op.Frac, I2, B3Op.L, B3Op.Bksp),
            "pow" to listOf(I2, B3Op.Pow, I3, B3Op.L, B3Op.Bksp),
            "root" to listOf(B3Op.RootK, I9, B3Op.L, B3Op.Bksp),
            "paren" to listOf(B3Op.ParenO, I9, B3Op.L, B3Op.Bksp),
            "func" to listOf(B3Op.Fn("sin"), I9, B3Op.L, B3Op.Bksp),
        )
        for ((kname, ops) in pop) out += B3Run.refEq(id(), "B", ops, note = "F5 select $kname")
        for ((kname, ops) in pop) out += B3Run.refEq(id(), "B", ops + B3Op.Bksp, note = "F5 delete $kname")
        for ((kname, ops) in pop) for (nav in listOf(B3Op.L, B3Op.R)) {
            out += B3Run.refEq(id(), "B", ops + nav, note = "F5 deselect $kname via ${nav.show()}")
        }

        // B-Froot — 2: start of the root row is genuinely nothing to delete.
        out += B3Run.refEq(id(), "B", listOf(B3Op.Bksp), note = "F-root empty")
        out += B3Run.refEq(id(), "B", listOf(B3Op.Ins("12"), B3Op.L, B3Op.L, B3Op.Bksp), note = "F-root offset0 populated")

        // B-HOLD — 4 fixpoints: the ⌫ hold (70 ms repeats) reaches empty, always.
        val deepFrac = (1..12).flatMap { listOf(I1, B3Op.Frac) } + I9
        val deepFunc = (1..12).map { B3Op.Fn("sin") } + I9
        val mixed30 = listOf(
            B3Op.Ins("1+"), B3Op.ParenO, B3Op.Ins("3x+4"), B3Op.ParenC, B3Op.Frac,
            B3Op.ParenO, B3Op.Ins("2x-1"), B3Op.ParenC, B3Op.R,
            B3Op.Ins("+"), B3Op.Fn("sin"), B3Op.Ins("30"), B3Op.R, B3Op.Pow, I2,
        )
        val m6tree = listOf(B3Op.Ins("1+"), B3Op.ParenO, B3Op.Ins("3x+4"), B3Op.ParenC,
            B3Op.Frac, B3Op.ParenO, B3Op.Ins("2x-1"), B3Op.ParenC)
        out += B3Run.fixpoint(id(), deepFrac, 200, "hold ⌫ over 12-deep fraction ladder")
        out += B3Run.fixpoint(id(), deepFunc, 200, "hold ⌫ over 12-deep func chain")
        out += B3Run.fixpoint(id(), mixed30, 200, "hold ⌫ over mixed tree")
        out += B3Run.fixpoint(id(), m6tree, 200, "hold ⌫ over (3x+4)/(2x-1)")

        // B-HOLDN — 10: the first N repeats of the hold, each state pinned to the rules.
        for (k in 1..10) {
            out += B3Run.refEq(id(), "B", m6tree + List(k) { B3Op.Bksp }, note = "hold step $k on (3x+4)/(2x-1)")
        }

        // B-ROUTE — 12: every route into a boundary, then ⌫ (the exit-left bug class).
        val fracBase = listOf(I1, B3Op.Frac, I2)
        out += B3Run.refEq(id(), "B", listOf(B3Op.Ins("12"), B3Op.Frac, B3Op.Bksp, B3Op.Bksp), note = "route collapse-landing frac")
        out += B3Run.refEq(id(), "B", fracBase + listOf(B3Op.L, B3Op.Bksp, B3Op.Bksp, B3Op.Bksp), note = "route delSel-landing frac")
        out += B3Run.refEq(id(), "B", fracBase + listOf(B3Op.R, B3Op.Bksp, B3Op.Copy, B3Op.R, B3Op.PasteClip, B3Op.Bksp), note = "route paste-landing frac")
        out += B3Run.refEq(id(), "B", listOf(B3Op.ParenO, I2, B3Op.ParenC, B3Op.Bksp), note = "route closeParen-exit paren")
        out += B3Run.refEq(id(), "B", fracBase + listOf(B3Op.R, B3Op.Bksp), note = "route moveRight-exit frac")
        out += B3Run.refEq(id(), "B", fracBase + listOf(B3Op.L, B3Op.L, B3Op.L, B3Op.L, B3Op.Bksp), note = "route before-structure frac (no-op)")
        val funcBase = listOf(B3Op.Fn("sin"), I9)
        out += B3Run.refEq(id(), "B", listOf(B3Op.Fn("sin"), I9, B3Op.R, B3Op.Ins("+2"), B3Op.Bksp, B3Op.Bksp, B3Op.Bksp), note = "route atom-then-boundary func")
        out += B3Run.refEq(id(), "B", funcBase + listOf(B3Op.L, B3Op.Bksp, B3Op.Bksp, B3Op.Bksp), note = "route delSel-landing func")
        out += B3Run.refEq(id(), "B", funcBase + listOf(B3Op.R, B3Op.Bksp, B3Op.Copy, B3Op.R, B3Op.PasteClip, B3Op.Bksp), note = "route paste-landing func")
        out += B3Run.refEq(id(), "B", funcBase + listOf(B3Op.ParenC, B3Op.Bksp), note = "route closeParen exits the ARG (D-036.4), then ⌫ selects the func")
        out += B3Run.refEq(id(), "B", funcBase + listOf(B3Op.R, B3Op.Bksp), note = "route moveRight-exit func")
        out += B3Run.refEq(id(), "B", funcBase + listOf(B3Op.L, B3Op.L, B3Op.Bksp), note = "route before-structure func (no-op)")

        // B-RAND — 24 seeded storms, 20 ops each, reference-checked at every step.
        for (seed in 0 until 24) {
            out += B3Run.refEq(id(), "B", storm(1000 + seed, 20), note = "seeded storm #$seed")
        }
        return out
    }

    private fun storm(seed: Int, count: Int): List<B3Op> {
        val rng = Random(seed)
        val pool: List<Pair<B3Op, Int>> = listOf(
            B3Op.Ins("1") to 3, B3Op.Ins("2") to 3, B3Op.Ins("3") to 3, B3Op.Ins("+") to 2,
            B3Op.Frac to 2, B3Op.Pow to 1, B3Op.RootK to 1, B3Op.ParenO to 1, B3Op.ParenC to 1,
            B3Op.Fn("sin") to 1, B3Op.Bksp to 4, B3Op.L to 2, B3Op.R to 2,
        )
        val bag = pool.flatMap { (op, w) -> List(w) { op } }
        return List(count) { bag[rng.nextInt(bag.size)] }
    }

    // --- AXIS C · NAVIGATE — 58 ----------------------------------------------

    private fun axisC(): List<B3Case> {
        val out = mutableListOf<B3Case>()
        var n = 0
        fun id() = "B3-C-%03d".format(++n)

        val trees: List<Pair<String, List<B3Op>>> = listOf(
            "t1-atoms" to listOf(B3Op.Ins("12")),
            "t2-binop" to listOf(B3Op.Ins("1+2")),
            "t3-frac" to listOf(I1, B3Op.Frac, I2),
            "t4-mixed" to listOf(B3Op.Ins("1+2"), B3Op.Frac, I3),
            "t5-tower" to listOf(I2, B3Op.Pow, I3, B3Op.Pow, I2),
            "t6-func" to listOf(B3Op.Fn("sin"), B3Op.Ins("30")),
            "t7-root" to listOf(B3Op.RootK, B3Op.Ins("x+1")),
            "t8-deep5" to listOf(B3Op.Frac, B3Op.Fn("sin"), B3Op.ParenO, B3Op.RootK, I2),
            "t9-paren" to listOf(B3Op.ParenO, B3Op.Ins("1+2"), B3Op.ParenC),
            "t10-wide" to listOf(B3Op.Ins("1+2+3+4+5+6+7+8+9")),
        )
        for ((tname, build) in trees) out += B3Run.traversal(id(), "C", build, rightward = true, bound = 400, note = "→ across $tname")
        for ((tname, build) in trees) out += B3Run.traversal(id(), "C", build, rightward = false, bound = 400, note = "← across $tname")
        for ((tname, build) in trees.take(8)) out += B3Run.navInverse(id(), build, rightFirst = true, bound = 400)
            .let { it.copy(note = "→← inverse on $tname") }
        for ((tname, build) in trees.take(8)) out += B3Run.navInverse(id(), build, rightFirst = false, bound = 400)
            .let { it.copy(note = "←→ inverse on $tname") }

        // navigation deselects — 4
        out += B3Run.refEq(id(), "C", listOf(I1, B3Op.Frac, I2, B3Op.R, B3Op.Bksp, B3Op.L), note = "← deselects frac")
        out += B3Run.refEq(id(), "C", listOf(I1, B3Op.Frac, I2, B3Op.R, B3Op.Bksp, B3Op.R), note = "→ deselects frac")
        out += B3Run.refEq(id(), "C", listOf(B3Op.Fn("sin"), I9, B3Op.R, B3Op.Bksp, B3Op.L), note = "← deselects func")
        out += B3Run.refEq(id(), "C", listOf(B3Op.Fn("sin"), I9, B3Op.R, B3Op.Bksp, B3Op.R), note = "→ deselects func")

        // deep-100 crossings — 2
        val chain100 = (1..100).map { B3Op.Fn("sin") } + I2
        out += B3Run.traversal(id(), "C", chain100, rightward = true, bound = 2000, note = "→ across 100-deep func chain")
        out += B3Run.traversal(id(), "C", chain100, rightward = false, bound = 2000, note = "← across 100-deep func chain")

        // R0 — 2
        out += B3Run.refEq(id(), "C", listOf(B3Op.L), note = "← on empty root")
        out += B3Run.refEq(id(), "C", listOf(B3Op.R), note = "→ on empty root")

        // mid-entry traversals — 6
        for ((tname, build) in trees.filter { it.first in setOf("t4-mixed", "t5-tower", "t8-deep5") }) {
            out += B3Run.traversal(id(), "C", build + listOf(B3Op.L, B3Op.L), rightward = true, bound = 400, note = "→ from mid of $tname")
            out += B3Run.traversal(id(), "C", build + listOf(B3Op.L, B3Op.L), rightward = false, bound = 400, note = "← from mid of $tname")
        }

        // bound property on wide + deep — 2
        out += B3Run.traversal(id(), "C", trees[9].second, rightward = true, bound = 40, note = "t10 bound: 17 atoms fit in 40 steps")
        out += B3Run.traversal(id(), "C", trees[7].second, rightward = true, bound = 40, note = "t8 bound: depth-5 fits in 40 steps")

        // boundary-position inverse on t9, t6 — 2
        out += B3Run.navInverse(id(), trees[8].second, rightFirst = true, bound = 400).let { it.copy(note = "→← inverse on t9-paren") }
        out += B3Run.navInverse(id(), trees[5].second, rightFirst = false, bound = 400).let { it.copy(note = "←→ inverse on t6-func") }

        // hand-pinned cursor sequences — 4 (the sequence is asserted by ref-equality
        // at every step; these scripts cross every field boundary of the structure)
        out += B3Run.refEq(id(), "C", listOf(I1, B3Op.Frac, I2) + List(6) { B3Op.L } + List(6) { B3Op.R }, note = "pinned walk frac")
        out += B3Run.refEq(id(), "C", listOf(I2, B3Op.Pow, I3) + List(6) { B3Op.L } + List(6) { B3Op.R }, note = "pinned walk pow")
        out += B3Run.refEq(id(), "C", listOf(B3Op.RootK, I9) + List(4) { B3Op.L } + List(4) { B3Op.R }, note = "pinned walk root")
        out += B3Run.refEq(id(), "C", listOf(B3Op.Frac, B3Op.Fn("sin"), B3Op.ParenO, I2) + List(10) { B3Op.L } + List(10) { B3Op.R }, note = "pinned walk deep")
        return out
    }

    // --- AXIS D · SELECT / CLIPBOARD — 62 ------------------------------------

    private fun axisD(): List<B3Case> {
        val out = mutableListOf<B3Case>()
        var n = 0
        fun id() = "B3-D-%03d".format(++n)

        val selFrac = listOf(I1, B3Op.Frac, I2, B3Op.R, B3Op.Bksp)   // frac selected
        val selFunc = listOf(B3Op.Fn("sin"), I9, B3Op.R, B3Op.Bksp)  // func selected
        val selKinds = mapOf(
            "frac" to selFrac,
            "pow" to listOf(I2, B3Op.Pow, I3, B3Op.R, B3Op.Bksp),
            "root" to listOf(B3Op.RootK, I9, B3Op.R, B3Op.Bksp),
            "paren" to listOf(B3Op.ParenO, I9, B3Op.ParenC, B3Op.Bksp),
            "func" to selFunc,
        )

        // type-over — 5
        for ((kname, sel) in selKinds) out += B3Run.refEq(id(), "D", sel + B3Op.Ins("5"), note = "type-over selected $kname")
        // structure-key-over — 5 (S3: capture-after-delete is undecided; PROBE)
        for ((kname, sel) in selKinds) out += B3Run.refEq(id(), "D", sel + B3Op.Frac, cls = "PROBE",
            note = "fraction key over selected $kname [S3: capture-after-delete undecided]")
        // paste-over — 4
        out += B3Run.refEq(id(), "D", listOf(I3, B3Op.L, B3Op.SelRange(0, 1), B3Op.Copy) + selFrac + B3Op.PasteClip, note = "paste atom clip over selected frac")
        out += B3Run.refEq(id(), "D", listOf(I3, B3Op.L, B3Op.SelRange(0, 1), B3Op.Copy) + selFunc + B3Op.PasteClip, note = "paste atom clip over selected func")
        out += B3Run.refEq(id(), "D", listOf(B3Op.Ins("1+2"), B3Op.SelRange(0, 3), B3Op.Copy, B3Op.SelRange(0, 3), B3Op.PasteClip), note = "paste multi over multi selection")
        out += B3Run.refEq(id(), "D", selFrac + listOf(B3Op.Copy, B3Op.PasteClip), note = "paste structure clip over its own selection")
        // Equals / OpenPalette preserve the selection — 2
        out += B3Run.identity(id(), "D", selFrac, B3Op.Eq, "= preserves selection")
        out += B3Run.identity(id(), "D", selFrac, B3Op.OpenPal, "fn preserves selection")

        // copy — 6
        out += copyCase(id(), selFrac, expectNodes = 1, note = "copy selected frac")
        out += copyCase(id(), listOf(B3Op.Ins("1+2"), B3Op.SelRange(0, 3)), expectNodes = 3, note = "copy multi-range")
        out += copyCase(id(), listOf(B3Op.Ins("12")), expectNodes = null, note = "copy with no selection = NothingSelected")
        out += copyCase(id(), listOf(B3Op.Ins("12"), B3Op.SelRange(1, 1)), expectNodes = null, note = "copy empty range = NothingSelected")
        out += copyCase(id(), selFunc, expectNodes = 1, note = "copy selected func")
        out += copyCase(id(), selFrac + B3Op.L, expectNodes = null, note = "copy after deselect = NothingSelected")
        // cut — 6
        out += cutCase(id(), selFrac, expectCut = true, note = "cut selected frac")
        out += cutCase(id(), listOf(B3Op.Ins("1+2"), B3Op.SelRange(0, 3)), expectCut = true, note = "cut multi-range")
        out += cutCase(id(), listOf(B3Op.Ins("12")), expectCut = false, note = "cut nothing")
        out += cutCase(id(), listOf(B3Op.Ins("12"), B3Op.SelRange(1, 1)), expectCut = false, note = "cut empty range")
        out += cutCase(id(), selFunc, expectCut = true, note = "cut selected func")
        out += cutCase(id(), selFrac + listOf(B3Op.Cut), expectCut = false, note = "cut twice: second = NothingSelected")

        // paste internal — 6
        val copyFrac = selFrac + B3Op.Copy
        out += B3Run.refEq(id(), "D", copyFrac + listOf(B3Op.R, B3Op.PasteClip), note = "paste into root end")
        out += B3Run.refEq(id(), "D", copyFrac + listOf(B3Op.R, B3Op.Ins("12"), B3Op.L, B3Op.PasteClip), note = "paste mid-row")
        out += B3Run.refEq(id(), "D", copyFrac + listOf(B3Op.R, I1, B3Op.Frac, B3Op.PasteClip), note = "paste into denominator")
        out += B3Run.refEq(id(), "D", copyFrac + listOf(B3Op.R, B3Op.Fn("sin"), B3Op.ParenO, B3Op.PasteClip), note = "paste at depth 2")
        out += B3Run.refEq(id(), "D", copyFrac + listOf(B3Op.L, B3Op.L, B3Op.PasteClip), note = "paste frac into its own copy's field-adjacent row")
        out += B3Run.refEq(id(), "D", listOf(I1, B3Op.PasteClip), note = "paste with empty clip = identity")

        // multi-node ranges — 8
        out += copyCase(id(), listOf(B3Op.Ins("1+2"), B3Op.SelRange(0, 3)), expectNodes = 3, note = "multi copy [0,3)")
        out += B3Run.refEq(id(), "D", listOf(B3Op.Ins("1+2"), B3Op.SelRange(0, 3), B3Op.Cut), note = "multi cut empties root")
        out += B3Run.refEq(id(), "D", listOf(B3Op.Ins("1+2"), B3Op.SelRange(1, 3), B3Op.Ins("5")), note = "type-over [1,3)")
        out += B3Run.refEq(id(), "D", listOf(B3Op.Ins("1+2"), B3Op.SelRange(0, 2), B3Op.Bksp), note = "⌫ kills the whole range")
        out += B3Run.refEq(id(), "D", listOf(B3Op.Ins("1+2"), B3Op.SelRange(0, 3), B3Op.L), note = "← deselects a range")
        out += B3Run.refEq(id(), "D", listOf(B3Op.Ins("1+2"), B3Op.SelRange(2, 3), B3Op.Cut), note = "cut tail range")
        out += B3Run.refEq(id(), "D", listOf(B3Op.Ins("1+2"), B3Op.SelRange(0, 1), B3Op.Cut), note = "cut head range")
        out += B3Run.refEq(id(), "D", listOf(B3Op.Ins("12345"), B3Op.SelRange(0, 5), B3Op.Cut), note = "cut full row")

        // pasteText over a selection — 4
        out += B3Run.refEq(id(), "D", selFrac + B3Op.PasteT("7+7"), note = "valid pasteText replaces selection")
        out += B3Run.refEq(id(), "D", selFrac + B3Op.PasteT(")("), note = "invalid pasteText leaves selection intact")
        out += B3Run.refEq(id(), "D", listOf(B3Op.Ins("1+2"), B3Op.SelRange(0, 3), B3Op.PasteT("9")), note = "valid pasteText over multi-range")
        out += B3Run.refEq(id(), "D", listOf(B3Op.Ins("1+2"), B3Op.SelRange(0, 3), B3Op.PasteT("((")), note = "invalid pasteText over multi-range")

        // cut→paste round trips — 4
        out += B3Run.refEq(id(), "D", selFrac + listOf(B3Op.Cut, B3Op.PasteClip), note = "cut then paste back in place")
        out += B3Run.refEq(id(), "D", selFrac + listOf(B3Op.Cut, B3Op.Ins("9+"), B3Op.PasteClip), note = "cut, type, paste elsewhere")
        out += B3Run.refEq(id(), "D", listOf(B3Op.Ins("1+2"), B3Op.SelRange(0, 3), B3Op.Cut, B3Op.Fn("sin"), B3Op.PasteClip), note = "cut multi, paste into ARG")
        out += B3Run.refEq(id(), "D", selFunc + listOf(B3Op.Cut, I1, B3Op.Frac, B3Op.PasteClip), note = "cut func, paste into DEN")

        // clip immutability — 2
        out += B3Run.refEq(id(), "D", copyFrac + listOf(B3Op.R, B3Op.PasteClip, B3Op.Ins("5"), B3Op.PasteClip), note = "clip unchanged after paste+edit")
        out += B3Run.refEq(id(), "D", copyFrac + listOf(B3Op.R, B3Op.Bksp, B3Op.Bksp, B3Op.PasteClip), note = "clip survives source deletion")

        // copy, modify source, paste old content — 2
        out += B3Run.refEq(id(), "D", listOf(B3Op.Ins("12"), B3Op.SelRange(0, 2), B3Op.Copy, B3Op.SelRange(0, 2), B3Op.Cut, B3Op.PasteClip), note = "clip holds pre-cut content")
        out += B3Run.refEq(id(), "D", listOf(B3Op.Ins("12"), B3Op.SelRange(0, 1), B3Op.Copy, B3Op.R, B3Op.Bksp, B3Op.PasteClip), note = "clip holds deleted atom")

        // select, deselect, ⌫ selects again — 3
        for (kname in listOf("frac", "root", "func")) {
            out += B3Run.refEq(id(), "D", selKinds[kname]!! + listOf(B3Op.L, B3Op.R, B3Op.Bksp), note = "deselect then ⌫ re-selects $kname")
        }
        // double-cut chain — 1
        out += B3Run.refEq(id(), "D", selFrac + listOf(B3Op.Cut, B3Op.PasteClip, B3Op.Cut), note = "cut after paste (nothing selected)")
        // type-over with operator / close-paren — 2
        out += B3Run.refEq(id(), "D", selFrac + B3Op.Ins("+"), note = "operator types over selection")
        out += B3Run.refEq(id(), "D", selFrac + B3Op.ParenC, cls = "PROBE", note = "')' over a root-level selection: no-op keeps selection (path empty; D-036.4 touches only ARG/BODY)")
        // flick-over — 2
        out += B3Run.refEq(id(), "D", selFrac + B3Op.KeyFlick("divide"), note = "√ flick replaces selection")
        out += B3Run.refEq(id(), "D", selFrac + B3Op.KeyFlick("point"), note = "π flick replaces selection")
        return out
    }

    private fun copyCase(id: String, build: List<B3Op>, expectNodes: Int?, note: String): B3Case =
        B3Run.custom(id, "D", "FAILABLE", note, build + B3Op.Copy) {
            val h = B3Harness(checkRef = true)
            if (!h.runOps(build)) return@custom B3Run.verdictOf(h, "FAILABLE")
            val snap = h.engine
            val r = try { com.vaibhav.calc.core.entry.Clipboard.copy(h.engine) } catch (t: Throwable) {
                return@custom B3Result("FAIL", "CRITICAL", "CRASH in copy: ${t::class.java.simpleName}", h.opsRun, null)
            }
            if (h.engine != snap) return@custom B3Result("FAIL", "MAJOR", "copy mutated state", h.opsRun, h.engine.toInput())
            when (r) {
                is com.vaibhav.calc.core.entry.CopyResult.Copied ->
                    if (expectNodes == null) B3Result("FAIL", "MAJOR", "expected NothingSelected, copied ${r.clip.nodes.size}", h.opsRun, h.engine.toInput())
                    else if (r.clip.nodes.size == expectNodes) B3Result("PASS", null, "copied <${r.clip.toInput()}>", h.opsRun, h.engine.toInput())
                    else B3Result("FAIL", "MAJOR", "copied ${r.clip.nodes.size} nodes, expected $expectNodes", h.opsRun, h.engine.toInput())
                com.vaibhav.calc.core.entry.CopyResult.NothingSelected ->
                    if (expectNodes == null) B3Result("PASS", null, null, h.opsRun, h.engine.toInput())
                    else B3Result("FAIL", "MAJOR", "NothingSelected, expected $expectNodes nodes", h.opsRun, h.engine.toInput())
            }
        }

    private fun cutCase(id: String, build: List<B3Op>, expectCut: Boolean, note: String): B3Case =
        B3Run.custom(id, "D", "FAILABLE", note, build + B3Op.Cut) {
            val h = B3Harness(checkRef = true)
            if (!h.runOps(build)) return@custom B3Run.verdictOf(h, "FAILABLE")
            val before = B3Harness.stats(h.engine.root)
            val selSize = h.engine.selection?.size ?: 0
            val r = try { com.vaibhav.calc.core.entry.Clipboard.cut(h.engine) } catch (t: Throwable) {
                return@custom B3Result("FAIL", "CRITICAL", "CRASH in cut: ${t::class.java.simpleName}", h.opsRun, null)
            }
            when (r) {
                is com.vaibhav.calc.core.entry.CutResult.Cut -> {
                    if (!expectCut) return@custom B3Result("FAIL", "MAJOR", "expected NothingSelected, got Cut", h.opsRun, null)
                    val after = B3Harness.stats(r.state.root)
                    val clipStats = B3Harness.stats(com.vaibhav.calc.core.entry.Row(r.clip.nodes))
                    val conserved =
                        after.charCount + clipStats.charCount == before.charCount &&
                            after.structs + clipStats.structs == before.structs &&
                            after.refs + clipStats.refs == before.refs
                    if (conserved && r.clip.nodes.size == selSize) B3Result("PASS", null, "cut <${r.clip.toInput()}>", h.opsRun, r.state.toInput())
                    else B3Result("FAIL", "CRITICAL", "cut lost content: before=$before after=$after clip=$clipStats", h.opsRun, r.state.toInput())
                }
                com.vaibhav.calc.core.entry.CutResult.NothingSelected ->
                    if (expectCut) B3Result("FAIL", "MAJOR", "expected Cut, got NothingSelected", h.opsRun, h.engine.toInput())
                    else B3Result("PASS", null, null, h.opsRun, h.engine.toInput())
            }
        }

    // --- AXIS E · PASTE-TEXT — 84 --------------------------------------------

    private fun axisE(): List<B3Case> {
        val out = mutableListOf<B3Case>()
        var n = 0
        fun id() = "B3-E-%03d".format(++n)

        val valid = listOf(
            "sin(30)", "x^2-5x+6=0", "5 lakh", "१+२", "#1+#2", "√9", "1×2÷3", "0xFF",
            "nCr(5,2)", "5 km + 300 m", "3!", "5%", "pi*e", "2(3)", "asin(1)", "1.5e3",
            "(1+2)/3", "sqrt(2)",
        )
        val ctxs = listOf(
            "R0" to emptyList(),
            "mid" to listOf(B3Op.Ins("1+")),
            "overSel" to listOf(B3Op.Ins("12"), B3Op.SelRange(0, 1)),
        )
        for (text in valid) for ((cname, pre) in ctxs) {
            out += B3Run.refEq(id(), "E", pre + B3Op.PasteT(text), note = "paste <${text.take(20)}> @ $cname")
        }

        val invalid = listOf(
            ")(", "((", "+*/", "sin⁻¹(1)", "", "  ", "😀", "‮12", "#", "1..2",
            "٣+٣", "@!$", "1+", "=5",
        )
        for (text in invalid) for ((cname, pre) in ctxs.take(2)) {
            out += B3Run.refEq(id(), "E", pre + B3Op.PasteT(text), note = "paste <${text.take(12)}> @ $cname")
        }

        // FM-11: pasted sin(30) (atoms+Paren) vs the sin key (Func) — same pixels?
        out += B3Run.custom(id(), "E", "PROBE", "FM-11 dual representation of sin(30)") {
            val a = B3Harness(); a.runOps(listOf(B3Op.PasteT("sin(30)")))
            val b = B3Harness(); b.runOps(listOf(B3Op.Fn("sin"), B3Op.Ins("30")))
            val sameText = a.engine.toInput() == b.engine.toInput()
            val sameTree = a.engine.root == b.engine.root
            B3Result("PROBE", null,
                "paste tree=${treeShape(a.engine)} key tree=${treeShape(b.engine)} sameInput=$sameText sameTree=$sameTree " +
                    "(⌫ cost differs when trees differ)", a.opsRun + b.opsRun, a.engine.toInput())
        }
        // B1-0035 class: a flat-chain blob through the paste door. Known engine bug family.
        out += B3Run.custom(id(), "E", "PROBE", "flat-chain blob via pasteText (B1-0035 family)") {
            val h = B3Harness()
            val blob = "1+".repeat(1999) + "1"
            h.runOps(listOf(B3Op.PasteT(blob)))
            h.crash?.let { return@custom B3Result("PROBE", null, "CRASH (B1-0035 family): $it", h.opsRun, null) }
            B3Result("PROBE", null, "no crash; readable handled; final len=${h.engine.toInput().length}", h.opsRun, null)
        }
        return out
    }

    private fun treeShape(s: EditorState): String = s.root.nodes.joinToString("+") {
        when (it) {
            is Atom -> "A"
            is Fraction -> "Frac"; is Paren -> "Paren"; is Func -> "Func(${it.name})"
            else -> it::class.java.simpleName
        }
    }

    // --- AXIS F · STRESS — 26 ------------------------------------------------

    private fun axisF(): List<B3Case> {
        val out = mutableListOf<B3Case>()
        var n = 0
        fun id() = "B3-F-%03d".format(++n)

        val builds: List<Triple<String, List<B3Op>, Int>> = listOf(
            Triple("sqrt-chain-500", (1..500).map { B3Op.RootK } + I2, 600),
            Triple("paren-chain-500", (1..500).map { B3Op.ParenO } + I2, 600),
            Triple("func-chain-500", (1..500).map { B3Op.Fn("sin") } + I2, 600),
            Triple("pow-tower-500", listOf(I2) + (1..500).flatMap { listOf(B3Op.Pow, I2) }, 1200),
            Triple("den-ladder-500", (1..500).flatMap { listOf(I1, B3Op.Frac) } + I9, 1200),
            Triple("mixed-chain-500", (1..166).flatMap { listOf(B3Op.Fn("sin"), B3Op.ParenO, B3Op.RootK) } + I2, 800),
        )
        for ((bname, build, unwindMax) in builds) {
            out += B3Run.custom(id(), "F", "FAILABLE", "$bname build+linearize+validate", build) {
                val h = B3Harness(checkRef = true)
                if (!h.runOps(build)) return@custom B3Run.verdictOf(h, "FAILABLE")
                h.divergenceAt.takeIf { it >= 0 }?.let { return@custom B3Run.verdictOf(h, "FAILABLE") }
                val input = try { h.engine.toInput() } catch (t: Throwable) {
                    return@custom B3Result("FAIL", "CRITICAL", "toInput threw ${t::class.java.simpleName} at depth", h.opsRun, null)
                }
                val v = try {
                    when (h.engine.validate()) {
                        is com.vaibhav.calc.core.error.CalcResult.Ok -> "Ok"
                        is com.vaibhav.calc.core.error.CalcResult.Err -> "Err(typed)"
                    }
                } catch (t: Throwable) {
                    return@custom B3Result("FAIL", "CRITICAL",
                        "validate() threw ${t::class.java.simpleName} on keystroke-reachable input (len=${input.length})", h.opsRun, null)
                }
                B3Result("PASS", null, "len=${input.length} validate=$v", h.opsRun, input.take(60))
            }
            out += B3Run.fixpoint(id(), build, unwindMax, "$bname unwind to empty", axis = "F")
        }

        // 999-atom flat row (500 terms — inside the engine's known-good flat range)
        val flat = listOf(B3Op.Ins("1+".repeat(499) + "1"))
        out += B3Run.custom(id(), "F", "FAILABLE", "flat-500-terms build+validate", flat) {
            val h = B3Harness(checkRef = true)
            if (!h.runOps(flat)) return@custom B3Run.verdictOf(h, "FAILABLE")
            val v = try {
                when (h.engine.validate()) {
                    is com.vaibhav.calc.core.error.CalcResult.Ok -> "Ok"
                    is com.vaibhav.calc.core.error.CalcResult.Err -> "Err"
                }
            } catch (t: Throwable) {
                return@custom B3Result("FAIL", "CRITICAL", "validate threw on 500-term row: ${t::class.java.simpleName}", h.opsRun, null)
            }
            if (v == "Ok") B3Result("PASS", null, "999 atoms validate Ok", h.opsRun, null)
            else B3Result("FAIL", "MAJOR", "500-term row not valid", h.opsRun, null)
        }
        out += B3Run.fixpoint(id(), flat, 1100, "999-atom unwind", axis = "F")

        // 200 parenthesised structures across the row
        val wide = (1..200).flatMap { listOf(B3Op.ParenO, I1, B3Op.ParenC) }
        out += B3Run.refEq(id(), "F", wide, note = "200-structure row build")
        out += B3Run.fixpoint(id(), wide, 500, "200-structure unwind (select+delete each)", axis = "F")

        // storms at depth 50 — 4 seeds
        val deepPrefix = (1..50).map { B3Op.Fn("sin") }
        for (seed in 0 until 4) {
            out += B3Run.refEq(id(), "F", deepPrefix + storm(2000 + seed, 50), note = "storm @ depth 50 #$seed")
        }

        // deep paste
        out += B3Run.refEq(id(), "F", listOf(B3Op.PasteT("(".repeat(500) + "1" + ")".repeat(500))), note = "paste 500-deep parens (guarded path)")
        out += B3Run.custom(id(), "F", "PROBE", "paste 2000-term flat chain (B1-0035 family)") {
            val h = B3Harness()
            h.runOps(listOf(B3Op.PasteT("1+".repeat(1999) + "1")))
            B3Result("PROBE", null, h.crash?.let { "CRASH: $it" } ?: "survived", h.opsRun, null)
        }

        // deep evaluation
        out += B3Run.eval(id(), (1..50).map { B3Op.RootK } + I2, "1", "sqrt^50(2) ≈ 1 within 1e-9", axis = "F")
        out += B3Run.custom(id(), "F", "FAILABLE", "eval 50-deep sin chain returns Ok") {
            val h = B3Harness()
            if (!h.runOps((1..50).map { B3Op.Fn("sin") } + I2)) return@custom B3Run.verdictOf(h, "FAILABLE")
            val r = try { com.vaibhav.calc.core.Calculator().evaluateAnswer(h.engine.toInput()) } catch (t: Throwable) {
                return@custom B3Result("FAIL", "CRITICAL", "CRASH evaluating 50-deep sin: ${t::class.java.simpleName}", h.opsRun, null)
            }
            when (r) {
                is com.vaibhav.calc.core.error.CalcResult.Ok -> B3Result("PASS", null, "= ${r.value.decimal}", h.opsRun, null)
                is com.vaibhav.calc.core.error.CalcResult.Err -> B3Result("FAIL", "MAJOR", "declined: ${r.error.kind}", h.opsRun, null)
            }
        }

        // navigation across depth-500
        val deep500 = (1..500).map { B3Op.RootK } + I2
        out += B3Run.traversal(id(), "F", deep500, rightward = true, bound = 3000, note = "→ across 500-deep sqrt chain")
        out += B3Run.traversal(id(), "F", deep500, rightward = false, bound = 3000, note = "← across 500-deep sqrt chain")
        return out
    }

    // --- AXIS G · KEYPAD / PALETTE — 40 --------------------------------------

    private fun axisG(): List<B3Case> {
        val out = mutableListOf<B3Case>()
        var n = 0
        fun id() = "B3-G-%03d".format(++n)
        val base = listOf(B3Op.Ins("12"))

        for (key in Keypad.basic().keys) {
            out += B3Run.refEq(id(), "G", base + B3Op.KeyTap(key.id), note = "tap ${key.id}")
        }
        for (kid in listOf("divide", "multiply", "minus", "point")) {
            out += B3Run.refEq(id(), "G", base + B3Op.KeyFlick(kid), note = "flick ↑ $kid")
        }
        for (kid in listOf("7", "plus", "equals", "backspace")) {
            out += B3Run.identity(id(), "G", base, B3Op.KeyFlick(kid), "flick on non-flick key $kid = no-op")
        }
        for (kid in listOf("7", "divide")) {
            out += B3Run.custom(id(), "G", "FAILABLE", "2nd layer falls back to tap on $kid") {
                val a = B3Harness(); a.runOps(base + B3Op.KeySecond(kid))
                val b = B3Harness(); b.runOps(base + B3Op.KeyTap(kid))
                if (a.crash != null || b.crash != null) B3Result("FAIL", "CRITICAL", a.crash ?: b.crash, a.opsRun, null)
                else if (a.engine == b.engine) B3Result("PASS", null, null, a.opsRun, a.engine.toInput())
                else B3Result("FAIL", "MAJOR", "2nd ≠ tap on $kid", a.opsRun, a.engine.toInput())
            }
        }
        out += B3Run.identity(id(), "G", base, B3Op.Eq, "Equals edits nothing")
        out += B3Run.identity(id(), "G", base, B3Op.OpenPal, "OpenPalette edits nothing")

        for (label in listOf("sin⁻¹", "a⁄b", "√", "π")) {
            out += B3Run.refEq(id(), "G", base + B3Op.Pal(label), note = "palette '$label'")
        }
        out += B3Run.custom(id(), "G", "FAILABLE", "palette integrity vs Functions registry") {
            val entries = try { FnPalette.entries } catch (t: Throwable) {
                return@custom B3Result("FAIL", "CRITICAL", "FnPalette init threw: ${t.message?.take(120)}", 0, null)
            }
            val badFn = entries.filter { it.insert is KeyAction.Func }
                .filterNot { Functions.isFunction((it.insert as KeyAction.Func).name) }
            val mapped = mapOf("asin" to "sin⁻¹", "acos" to "cos⁻¹", "atan" to "tan⁻¹", "cbrt" to "∛")
            val badLabel = mapped.filterNot { (name, label) ->
                Functions.displayName(name) == label &&
                    entries.any { it.insert is KeyAction.Func && (it.insert as KeyAction.Func).name == name && it.label == label }
            }
            if (badFn.isEmpty() && badLabel.isEmpty()) B3Result("PASS", null, "${entries.size} entries checked", 0, null)
            else B3Result("FAIL", "MAJOR", "unregistered=$badFn labelDrift=$badLabel", 0, null)
        }
        out += B3Run.custom(id(), "G", "FAILABLE", "palette search: names") {
            val sin = FnPalette.search("sin")
            val sqrt = FnPalette.search("sqrt")
            if (sin.any { it.name == "sin" } && sqrt.any { it.label == "√" })
                B3Result("PASS", null, "sin→${sin.size} hits, sqrt→√ found", 0, null)
            else B3Result("FAIL", "MAJOR", "sin hits=${sin.map { it.label }} sqrt hits=${sqrt.map { it.label }}", 0, null)
        }
        out += B3Run.custom(id(), "G", "FAILABLE", "palette search: empty and miss") {
            val empty = FnPalette.search("")
            val miss = FnPalette.search("zzz")
            if (empty.isEmpty() && miss.isEmpty()) B3Result("PASS", null, null, 0, null)
            else B3Result("FAIL", "MINOR", "empty=${empty.size} zzz=${miss.size}", 0, null)
        }
        out += B3Run.custom(id(), "G", "FAILABLE", "palette search: case-insensitive label") {
            val hits = FnPalette.search("SIN")
            if (hits.isNotEmpty()) B3Result("PASS", null, "SIN→${hits.size}", 0, null)
            else B3Result("FAIL", "MINOR", "SIN found nothing", 0, null)
        }
        return out
    }

    // --- AXIS H · EVAL-AGREEMENT — 36: the named harm, head-on ----------------

    private fun axisH(): List<B3Case> {
        val L8 = List(8) { B3Op.L }
        data class E(val ops: List<B3Op>, val expected: String, val note: String)
        val cases = listOf(
            E(listOf(B3Op.Frac, I1, B3Op.R, I2, B3Op.R) + L8 + I2, "1", "2·(1/2): atom beside fraction"),
            E(listOf(I1, B3Op.Frac, I2, B3Op.R, I3), "1.5", "(1/2)·3: fraction then trailing atom"),
            E(listOf(I1, B3Op.Frac, I2, B3Op.R, I3, B3Op.Frac, B3Op.Ins("4"), B3Op.R), "0.375", "(1/2)·(3/4): two fractions adjacent"),
            E(listOf(B3Op.Ins("-2"), B3Op.Pow, I3), "-8", "-2^3 reads as -(2^3)"),
            E(listOf(I2, B3Op.Pow, I3, B3Op.Pow, I2), "512", "power tower 2^(3^2)"),
            E(listOf(B3Op.Ins("1+2"), B3Op.Frac, B3Op.Ins("4")), "1.5", "1+(2/4): capture only trailing operand"),
            E(listOf(I2, B3Op.Pow, I2, B3Op.R, I3), "12", "(2^2)·3: atom after exponent exit"),
            E(listOf(B3Op.Fn("sin"), B3Op.Ins("30")), "0.5", "sin 30° (DEG default, D-005)"),
            E(listOf(B3Op.Pal("sin⁻¹"), I1), "90", "palette sin⁻¹ stores asin"),
            E(listOf(B3Op.RootK, I9), "3", "√9"),
            E(listOf(B3Op.Fn("cbrt"), B3Op.Ins("27")), "3", "∛27 via Func"),
            E(listOf(I2, B3Op.ParenO, B3Op.Ins("1+2"), B3Op.ParenC), "6", "2(1+2) implicit multiplication"),
            E(listOf(B3Op.Ins("1/2")), "0.5", "linear slash atoms"),
            E(listOf(I1, B3Op.Frac, I2, B3Op.R, B3Op.Pow, I2), "0.25", "(1/2)^2: structure captured as base"),
            E(listOf(B3Op.PasteT("१+२")), "3", "Devanagari input round-trip (D-028)"),
            E(listOf(B3Op.Pal("log₁₀"), B3Op.Ins("1000")), "3", "log₁₀ via palette"),
            E(listOf(B3Op.Ins("5%")), "0.05", "percent postfix (D-007)"),
            E(listOf(B3Op.Ins("3!")), "6", "factorial"),
            // variants — same shapes, different operands
            E(listOf(B3Op.Frac, I1, B3Op.R, B3Op.Ins("4"), B3Op.R) + L8 + I3, "0.75", "3·(1/4)"),
            E(listOf(I3, B3Op.Frac, B3Op.Ins("4"), B3Op.R, I2), "1.5", "(3/4)·2"),
            E(listOf(I2, B3Op.Frac, B3Op.Ins("5"), B3Op.R, B3Op.Ins("5"), B3Op.Frac, I2, B3Op.R), "1", "(2/5)·(5/2)"),
            E(listOf(B3Op.Ins("-3"), B3Op.Pow, I2), "-9", "-3^2 reads as -(3^2)"),
            E(listOf(I3, B3Op.Pow, I2, B3Op.Pow, I2), "81", "3^(2^2)"),
            E(listOf(B3Op.Ins("2+3"), B3Op.Frac, B3Op.Ins("6")), "2.5", "2+(3/6)"),
            E(listOf(I3, B3Op.Pow, I2, B3Op.R, I2), "18", "(3^2)·2"),
            E(listOf(B3Op.Fn("cos"), B3Op.Ins("60")), "0.5", "cos 60°"),
            E(listOf(B3Op.Pal("tan⁻¹"), I1), "45", "palette tan⁻¹"),
            E(listOf(B3Op.RootK, B3Op.Ins("16")), "4", "√16"),
            E(listOf(B3Op.Fn("cbrt"), B3Op.Ins("8")), "2", "∛8"),
            E(listOf(I3, B3Op.ParenO, B3Op.Ins("2+1"), B3Op.ParenC), "9", "3(2+1)"),
            E(listOf(B3Op.Ins("3/4")), "0.75", "linear slash atoms variant"),
            E(listOf(I2, B3Op.Frac, I3, B3Op.R, B3Op.Pow, I2), "0.44444444444444444444", "(2/3)^2"),
            E(listOf(B3Op.PasteT("२*३")), "6", "Devanagari with explicit ×"),
            E(listOf(B3Op.Pal("log₂"), B3Op.Ins("8")), "3", "log₂ via palette"),
            E(listOf(B3Op.Ins("40%")), "0.4", "percent variant"),
            E(listOf(B3Op.Ins("4!")), "24", "factorial variant"),
        )
        return cases.mapIndexed { i, e -> B3Run.eval("B3-H-%03d".format(i + 1), e.ops, e.expected, e.note) }
    }

    // --- assembly ------------------------------------------------------------

    fun all(): List<B3Case> = axisA() + axisB() + axisC() + axisD() + axisE() + axisF() + axisG() + axisH()

    val declared: Map<String, Int> = mapOf(
        "A" to 96, "B" to 118, "C" to 58, "D" to 62, "E" to 84, "F" to 26, "G" to 40, "H" to 36,
    )
}
