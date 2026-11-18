package com.vaibhav.calc.android

import com.vaibhav.calc.core.name.Functions
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Pixel coordinates in one shared image/canvas coordinate system. */
data class MathBox(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
}

/** [matchDistance] is a geometric mismatch score, NOT a probability or OCR confidence. */
data class MathToken(
    val text: String,
    val bounds: MathBox,
    val alternatives: List<String> = emptyList(),
    val matchDistance: Float? = null,
    val uncertain: Boolean = false,
    val sourceIndex: Int = -1,
)

sealed interface MathLayoutResult {
    /** A proposed transcription. Always show it and allow editing before saving a calculation. */
    data class Draft(
        val expression: String,
        val warnings: List<String>,
        val ambiguous: Boolean,
        val sourceTokens: List<MathToken>,
    ) : MathLayoutResult
    data class Rejected(val reason: String) : MathLayoutResult
}

/** Bounded geometry rules, shared by image OCR tokens and locally matched ink glyphs. */
object MathLayout {
    const val MAX_TOKENS = 64
    const val MAX_CHARACTERS = 2048
    const val MAX_DEPTH = 8
    private val superscript = mapOf('⁰' to '0', '¹' to '1', '²' to '2', '³' to '3', '⁴' to '4',
        '⁵' to '5', '⁶' to '6', '⁷' to '7', '⁸' to '8', '⁹' to '9', '⁻' to '-', '⁺' to '+')
    private class LayoutFault(message: String) : RuntimeException(message)
    private enum class Kind { TEXT, FRACTION, RADICAL }
    private data class Node(
        val text: String, val box: MathBox, val kind: Kind = Kind.TEXT,
        val nominalHeight: Float = box.height, val baseline: Float = box.bottom,
        val explicitExponent: Boolean = false,
    ) {
        val centerY: Float get() = baseline - nominalHeight / 2
    }

    fun read(tokens: List<MathToken>): MathLayoutResult {
        if (tokens.isEmpty()) return MathLayoutResult.Rejected("No mathematical symbols were found.")
        if (tokens.size > MAX_TOKENS || tokens.sumOf { it.text.length.toLong() } > MAX_CHARACTERS)
            return MathLayoutResult.Rejected("Use at most $MAX_TOKENS symbols and $MAX_CHARACTERS characters.")
        if (tokens.any { token ->
                val b = token.bounds
                listOf(b.left, b.top, b.right, b.bottom).any { !it.isFinite() || abs(it) > 1_000_000f } ||
                    b.left > b.right || b.top > b.bottom || token.alternatives.size > 8 ||
                    token.alternatives.any { it.length > 64 } ||
                    token.matchDistance?.let { !it.isFinite() || it < 0f } == true
            }) return MathLayoutResult.Rejected("The symbol positions or candidate data are invalid.")
        val source = tokens.mapIndexed { index, t -> if (t.sourceIndex < 0) t.copy(sourceIndex = index) else t }
        val warnings = linkedSetOf("Review the transcription: image and handwriting recognition can make mistakes.")
        if (source.any { it.uncertain }) warnings += "Some symbols have close alternatives. Check the highlighted candidates."
        return try {
            val nodes = source.map {
                val raised = it.text.trim().isNotEmpty() && it.text.trim().all { char -> char in superscript }
                Node(normalize(it.text, raised), it.bounds, explicitExponent = raised)
            }
            val expression = Reader(warnings).region(nodes, 0)
            if (expression.length > MAX_CHARACTERS) throw LayoutFault("The interpreted expression is too long.")
            MathLayoutResult.Draft(expression, warnings.toList(), source.any { it.uncertain }, source)
        } catch (fault: LayoutFault) {
            MathLayoutResult.Rejected(fault.message ?: "The layout is unclear. Edit the expression manually.")
        }
    }

    private fun normalize(input: String, exponentToken: Boolean): String {
        val text = input.trim().replace('−', '-').replace('–', '-').replace('—', '-')
            .replace('×', '*').replace('·', '*').replace('÷', '/').replace('＝', '=')
            .replace('（', '(').replace('）', ')')
        if (text.isEmpty() || text.any { !it.isLetterOrDigit() && it !in ".,+-*/^()=!%π√ ⁰¹²³⁴⁵⁶⁷⁸⁹⁻⁺" })
            throw LayoutFault("A symbol contains unsupported text. Correct it before calculating.")
        if (exponentToken) return text.map { superscript.getValue(it) }.joinToString("")
        val out = StringBuilder()
        var index = 0
        while (index < text.length) {
            if (text[index] in superscript) {
                if (out.isEmpty()) throw LayoutFault("A superscript needs a base.")
                out.append("^(")
                while (index < text.length && text[index] in superscript) out.append(superscript.getValue(text[index++]))
                out.append(')')
            } else out.append(text[index++])
        }
        return out.toString()
    }

    private fun union(boxes: List<MathBox>) = MathBox(boxes.minOf { it.left }, boxes.minOf { it.top },
        boxes.maxOf { it.right }, boxes.maxOf { it.bottom })
    private fun height(nodes: List<Node>): Float {
        val heights = nodes.filter { it.text !in setOf("-", "=", ".", "*") }
            .map { it.nominalHeight }.filter { it > 1f }.sorted()
        return heights.getOrNull(heights.size / 2) ?: max(8f, nodes.maxOf { it.box.width } * .7f)
    }
    private val number = Regex("(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?")
    private val numberPrefix = Regex("(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]*)(?:[eE][+-]?[0-9]*)?")
    private fun numeric(text: String) = text.matches(number)
    private fun variable(text: String) = text in setOf("x", "y", "z", "π", "pi", "e")
    private fun variablePower(text: String) = text.matches(Regex("(?:[xyzπe]|pi)\\^.*"))
    private fun powerBase(text: String) = text.lastOrNull()?.let { it.isLetterOrDigit() || it == ')' || it == 'π' || it == '%' || it == '!' } == true

    private class Reader(private val warnings: MutableSet<String>) {
        fun region(original: List<Node>, depth: Int): String {
            if (depth > MAX_DEPTH) throw LayoutFault("The mathematical layout is nested too deeply.")
            if (original.isEmpty()) throw LayoutFault("A fraction or root has a missing argument.")
            for (i in original.indices) for (j in i + 1 until original.size) {
                val a = original[i]; val b = original[j]
                if (a.kind != Kind.TEXT || b.kind != Kind.TEXT || a.text == "√" || b.text == "√") continue
                val width = min(a.box.width, b.box.width); val h = min(a.box.height, b.box.height)
                if (width > 0f && h > 0f && min(a.box.right, b.box.right) - max(a.box.left, b.box.left) > width * .65f &&
                    min(a.box.bottom, b.box.bottom) - max(a.box.top, b.box.top) > h * .65f)
                    throw LayoutFault("Two symbol boxes overlap too much to order reliably. Crop a clearer image or correct the expression.")
            }
            val initial = numberWords(functionWords(original))
            val nominal = height(initial)
            val nodes = initial.toMutableList()
            // Resolve the widest bar first: nested fractions then recurse inside its sides.
            while (true) {
                var found = false
                val bars = nodes.filter { it.kind == Kind.TEXT && it.text == "-" &&
                    it.box.width >= nominal * .55f && it.box.height <= nominal * .28f }
                    .sortedByDescending { it.box.width }
                for (bar in bars) {
                    val margin = nominal * .12f
                    val covered = nodes.filter { it !== bar &&
                        !(it.text == "√" && it.box.top <= bar.box.top && it.box.left <= bar.box.left && it.box.right >= bar.box.right) &&
                        it.box.centerX >= bar.box.left - margin && it.box.centerX <= bar.box.right + margin }
                    val above = covered.filter { it.box.bottom < bar.box.centerY - nominal * .04f }
                    val below = covered.filter { it.box.top > bar.box.centerY + nominal * .04f }
                    if (above.isEmpty() || below.isEmpty()) continue
                    if (covered.size != above.size + below.size || covered.any {
                            it.box.left < bar.box.left - margin || it.box.right > bar.box.right + margin
                        }) throw LayoutFault("A fraction bar crosses other symbols. Separate or edit the layout.")
                    val numerator = region(above, depth + 1)
                    val denominator = region(below, depth + 1)
                    val box = union((covered + bar).map { it.box })
                    nodes.removeAll(covered.toSet()); nodes.remove(bar)
                    nodes += Node("(($numerator)/($denominator))", box, Kind.FRACTION, nominal,
                        bar.box.centerY + nominal / 2)
                    warnings += "A stacked fraction was interpreted from its bar; check the numerator and denominator."
                    found = true
                    break
                }
                if (!found) break
            }

            // One-sided rules are incomplete fractions/overlines, not a minus that can be
            // moved in front of the glyph above it. Ordinary unary/subtraction signs have
            // their operands beside the sign or crossing its horizontal baseline.
            for (bar in nodes.filter { it.kind == Kind.TEXT && it.text == "-" &&
                it.box.width >= nominal * .55f && it.box.height <= nominal * .28f }) {
                if (nodes.any { it.text == "√" && rootBar(it, bar, nominal) }) continue
                val margin = nominal * .12f
                val covered = nodes.filter { it !== bar && it.text != "√" &&
                    it.box.left >= bar.box.left - margin && it.box.right <= bar.box.right + margin }
                val above = covered.filter { it.box.bottom < bar.box.centerY - nominal * .04f }
                val below = covered.filter { it.box.top > bar.box.centerY + nominal * .04f }
                if (covered.isNotEmpty() && covered.size == above.size + below.size &&
                    (above.isEmpty() != below.isEmpty()))
                    throw LayoutFault("A horizontal bar has symbols on only one side. Complete the fraction or correct the expression.")
            }

            // A drawn radical's bounding box includes its overbar. Only symbols lying under
            // that bar are included. A standalone printed √ consumes one following atom.
            for (root in nodes.filter { it.kind == Kind.TEXT && it.text == "√" }) {
                val bars = nodes.filter { it !== root && rootBar(root, it, nominal) }
                if (bars.size == 1) {
                    nodes.remove(root); nodes.remove(bars.single())
                    nodes += root.copy(box = union(listOf(root.box, bars.single().box)))
                } else if (bars.size > 1) throw LayoutFault("The radical has more than one possible overbar. Correct its scope.")
            }
            for (root in nodes.filter { it.kind == Kind.TEXT && it.text == "√" }.sortedBy { it.box.width }) {
                if (root !in nodes) continue
                val under = nodes.filter { it !== root && it.box.centerX > root.box.left + root.box.width * .28f &&
                    it.box.right <= root.box.right + nominal * .12f && it.box.top >= root.box.top - nominal * .12f &&
                    it.box.bottom <= root.box.bottom + nominal * .3f }
                if (under.isNotEmpty()) {
                    val argument = region(under, depth + 1)
                    nodes.removeAll(under.toSet()); nodes.remove(root)
                    nodes += Node("sqrt($argument)", union((under + root).map { it.box }), Kind.RADICAL, nominal,
                        under.maxOf { it.baseline })
                    warnings += "A radical's overbar determined its argument; check the root scope."
                }
            }

            val ordered = nodes.sortedWith(compareBy<Node> { it.box.left }.thenBy { it.box.top })
            val emitted = mutableListOf<Node>()
            var index = 0
            while (index < ordered.size) {
                var base = ordered[index++]
                if (base.explicitExponent) throw LayoutFault("A superscript has no clear preceding base.")
                if (base.text == "√") {
                    if (index < ordered.size && ordered[index].text == "(") {
                        val argument = mutableListOf<Node>()
                        var brackets = 0
                        do {
                            if (index >= ordered.size) throw LayoutFault("The square root's parentheses are incomplete.")
                            val token = ordered[index++]
                            argument += token
                            if (token.text == "(") brackets++
                            if (token.text == ")") brackets--
                        } while (brackets > 0)
                        base = Node("sqrt(${region(argument, depth + 1)})", union((argument + base).map { it.box }),
                            Kind.RADICAL, nominal, argument.maxOf { it.baseline })
                    } else {
                    if (index >= ordered.size || ordered[index].explicitExponent || !powerBase(ordered[index].text))
                        throw LayoutFault("The square root needs a clear following argument.")
                    val next = ordered[index++]
                    if (next.box.left - base.box.right > nominal * 1.3f)
                        throw LayoutFault("The root and its argument are too far apart.")
                    if (numeric(next.text) && index < ordered.size && numeric(ordered[index].text) &&
                        !isRaised(next, ordered[index], nominal))
                        throw LayoutFault("Separated numbers after a root need an explicit operator or a clearer overbar.")
                    val raised = mutableListOf<Node>()
                    while (index < ordered.size && isRaised(next, ordered[index], nominal)) raised += ordered[index++]
                    val arg = next.text + if (raised.isEmpty()) "" else "^(${region(raised.map { it.copy(explicitExponent = false) }, depth + 1)})"
                    base = Node("sqrt($arg)", union((listOf(base, next) + raised).map { it.box }), Kind.RADICAL,
                        nominal, next.baseline)
                    warnings += "The square root was applied to the next atom. Check its scope."
                    }
                }
                val raised = mutableListOf<Node>()
                if (powerBase(base.text)) {
                    while (index < ordered.size && isRaised(base, ordered[index], nominal)) raised += ordered[index++]
                }
                if (raised.isNotEmpty()) {
                    base = base.copy(text = base.text + "^(${region(raised.map { it.copy(explicitExponent = false) }, depth + 1)})",
                        box = union((raised + base).map { it.box }))
                    warnings += "Raised symbols were read as a power. Check the exponent."
                }
                emitted += base
            }
            val centers = emitted.filter { it.text != "." }.map { it.centerY }.sorted()
            val center = centers.getOrNull(centers.size / 2) ?: emitted.first().centerY
            for (node in emitted) {
                // Operators can be very thin. Their actual centre, not a fictitious baseline,
                // is used; a decimal point is allowed at the common baseline.
                val y = if (node.box.height < nominal * .25f && node.kind == Kind.TEXT) node.box.centerY else node.centerY
                if (node.text == ".") {
                    if (y < center - nominal * .15f || y > center + nominal * .8f)
                        throw LayoutFault("A dot outside the baseline is ambiguous; use × for multiplication or edit it.")
                } else if (abs(y - center) > nominal * .6f)
                    throw LayoutFault("This looks like multiple lines or an unsupported subscript. Select one expression or edit it.")
            }
            val text = StringBuilder()
            for ((i, node) in emitted.withIndex()) {
                val previous = emitted.getOrNull(i - 1)
                if (previous != null) {
                    if (numeric(previous.text) && node.kind == Kind.FRACTION)
                        throw LayoutFault("A number beside a fraction can mean a mixed number or multiplication. Insert + or × explicitly.")
                    if (numeric(previous.text) && numeric(node.text) && node.box.left - previous.box.right > nominal * .65f)
                        throw LayoutFault("Separated numbers need an explicit operator. They were not joined into one number.")
                    if (((variable(previous.text) || previous.kind != Kind.TEXT || previous.text.endsWith(')')) &&
                        (variable(node.text) || variablePower(node.text) || numeric(node.text) || node.kind != Kind.TEXT || node.text == "(")) ||
                        (numeric(previous.text) && (variable(node.text) || variablePower(node.text)))) text.append('*')
                }
                text.append(node.text)
                if (text.length > MAX_CHARACTERS) throw LayoutFault("The interpreted expression is too long.")
            }
            return text.toString()
        }

        private fun rootBar(root: Node, bar: Node, nominal: Float): Boolean {
            if (bar.kind != Kind.TEXT || bar.text != "-" || bar.box.height > nominal * .2f ||
                abs(bar.box.top - root.box.top) > nominal * .2f) return false
            val extends = abs(bar.box.left - root.box.right) <= nominal * .25f && bar.box.right > root.box.right + nominal * .3f
            val coversTop = bar.box.left >= root.box.left + nominal * .15f && bar.box.left < root.box.right &&
                bar.box.right >= root.box.right - nominal * .25f
            return extends || coversTop
        }

        /** OCR symbols in 16, 1.44 and 1e3 form one lexical atom before a root takes its argument. */
        private fun numberWords(nodes: List<Node>): List<Node> {
            val ordered = nodes.sortedWith(compareBy<Node> { it.box.left }.thenBy { it.box.top })
            val nominal = height(nodes)
            val out = ArrayList<Node>()
            var start = 0
            while (start < ordered.size) {
                val first = ordered[start]
                if (first.kind != Kind.TEXT || first.explicitExponent || !numberPrefix.matches(first.text)) {
                    out += first; start++; continue
                }
                val word = StringBuilder(first.text)
                var best = if (numeric(first.text)) start else -1
                var bestText = first.text
                var end = start + 1
                while (end < ordered.size) {
                    val next = ordered[end]; val previous = ordered[end - 1]
                    val h = max(nominal, max(first.box.height, next.box.height))
                    val exponentSign = next.text in setOf("+", "-") && word.lastOrNull() in listOf('e', 'E')
                    val aligned = abs(next.box.bottom - first.box.bottom) <= h * .3f ||
                        (exponentSign && abs(next.box.centerY - (first.box.bottom - h / 2)) <= h * .35f)
                    if (next.kind != Kind.TEXT || next.explicitExponent || next.box.left - previous.box.right > h * .65f ||
                        !aligned || !numberPrefix.matches(word.toString() + next.text)) break
                    word.append(next.text)
                    if (numeric(word.toString())) { best = end; bestText = word.toString() }
                    end++
                }
                if (best >= start) {
                    val parts = ordered.subList(start, best + 1)
                    out += Node(bestText, union(parts.map { it.box }))
                    start = best + 1
                } else { out += first; start++ }
            }
            return out
        }

        /** Character-level OCR must preserve registered words such as exp, not turn e·x into a product. */
        private fun functionWords(nodes: List<Node>): List<Node> {
            val ordered = nodes.sortedWith(compareBy<Node> { it.box.left }.thenBy { it.box.top })
            val out = ArrayList<Node>()
            var start = 0
            while (start < ordered.size) {
                var bestEnd = -1
                val word = StringBuilder()
                var end = start
                while (end < ordered.size && end - start < 16) {
                    val n = ordered[end]
                    if (n.kind != Kind.TEXT || n.explicitExponent || n.text.length != 1 || !n.text.single().isLetterOrDigit()) break
                    val previous = ordered.getOrNull(end - 1)
                    if (end > start && previous != null) {
                        val h = max(ordered[start].box.height, n.box.height)
                        if (n.box.left - previous.box.right > h * .65f || abs(n.box.bottom - ordered[start].box.bottom) > h * .35f) break
                    }
                    word.append(n.text)
                    if (Functions.isFunction(word.toString()) && ordered.getOrNull(end + 1)?.text == "(") bestEnd = end
                    end++
                }
                if (bestEnd >= start) {
                    val parts = ordered.subList(start, bestEnd + 1)
                    out += Node(parts.joinToString("") { it.text }.lowercase(), union(parts.map { it.box }))
                    start = bestEnd + 1
                } else out += ordered[start++]
            }
            return out
        }

        private fun isRaised(base: Node, next: Node, nominal: Float): Boolean {
            val h = max(base.nominalHeight, nominal * .7f)
            if (next.explicitExponent) return next.box.left >= base.box.right - min(base.box.width, next.box.width) * .35f &&
                next.box.left - base.box.right <= h * 1.15f
            return next.text != "." && next.box.left >= base.box.right - h * .18f &&
                next.box.left - base.box.right <= h * 1.15f && next.box.height <= h * .9f &&
                next.box.bottom <= base.baseline - h * .28f && next.box.centerY < base.centerY - h * .15f
        }
    }
}
