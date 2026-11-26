package com.vaibhav.calc.android

/** Review decisions are tied to detached snapshots, never to an in-progress pen path. */
internal data class InkExpressionReview private constructor(
    val revision: Long,
    val mode: String,
    val expression: String,
    val tokens: List<MathToken>,
    val lineSymbols: List<String>,
) {
    fun matches(revision: Long, mode: String, expression: String,
                tokens: List<MathToken>, lineSymbols: List<String>) =
        this.revision == revision && this.mode == mode && this.expression == expression &&
            this.tokens == tokens && this.lineSymbols == lineSymbols

    companion object {
        fun capture(revision: Long, mode: String, expression: String,
                    tokens: List<MathToken>, lineSymbols: List<String>): InkExpressionReview? {
            if (expression.isBlank() || expression.length > RecognitionCalculator.MAX_CHARS ||
                tokens.size > MathLayout.MAX_TOKENS || lineSymbols.size > InkLineRecognizer.MAX_SYMBOLS ||
                tokens.sumOf { it.text.length.toLong() } > MathLayout.MAX_CHARACTERS ||
                lineSymbols.sumOf { it.length.toLong() } > RecognitionCalculator.MAX_CHARS ||
                tokens.any { it.alternatives.size > 8 || it.alternatives.any { s -> s.length > 64 } }) return null
            return InkExpressionReview(revision, mode, expression,
                tokens.map { it.copy(alternatives = it.alternatives.toList()) }, lineSymbols.toList())
        }
    }
}

/** Guidance and explicit-choice preservation only; never infer a symbol from its answer. */
internal object InkRecovery {
    data class Guidance(val message: String, val firstSymbol: Int? = null)

    fun unclearMathSymbols(tokens: List<MathToken>): List<Int> = tokens.indices.filter {
        tokens[it].text == "?" || tokens[it].uncertain
    }

    fun unclearLineSymbols(draft: InkLineResult.Draft?, reviewed: Set<Int>): List<Int> {
        if (draft == null) return emptyList()
        return draft.glyphs.indices.filter { index ->
            if (index in reviewed) false else {
                val candidates = draft.glyphs[index].candidates
                val best = candidates.firstOrNull()
                best == null || best.requiresReview || !best.distance.isFinite() || best.distance > .075f ||
                    candidates.drop(1).any { it.symbol != best.symbol &&
                        (!it.distance.isFinite() || it.distance - best.distance < .018f) }
            }
        }
    }

    fun guidance(hasInk: Boolean, mode: String, tokens: List<MathToken>,
                 math: MathLayoutResult.Draft?, line: InkLineResult.Draft?,
                 reviewedLineSymbols: Set<Int> = emptySet()): Guidance? {
        if (!hasInk) return null
        if (mode == "One symbol") return Guidance(
            "Choose a matching symbol below. If none fits, erase only that mark and draw it larger, or type it in the expression.")
        val unclear = if (mode == "Math layout") unclearMathSymbols(tokens)
            else unclearLineSymbols(line, reviewedLineSymbols)
        val unknown = tokens.indexOfFirst { it.text == "?" }.takeIf { it >= 0 }
        if (unclear.isNotEmpty()) {
            val first = unknown ?: unclear.first()
            return Guidance("Check symbol ${first + 1}. Choose its intended match, or erase and redraw just that group with a gap around it. You can also edit the expression.", first)
        }
        if (mode == "Math layout" && math == null && tokens.isNotEmpty()) return Guidance(
            "The symbols are kept, but their arrangement is unclear. Check the numbered symbols, redraw the overlapping part, or type the complete expression.", 0)
        if ((mode == "Math layout" && math == null) || (mode == "Spaced line" && line == null)) return Guidance(
            "Your drawing is still on the pad. Leave gaps between symbols, use Math layout for fractions and powers, or enter the calculation in the expression box.")
        return null
    }

    /** Only a retry of the SAME ink may call this. A complete matching geometry is required.
     * A null score with uncertain=false identifies an explicit InkReview replacement. */
    fun retainExplicitChoices(previous: List<MathToken>, reading: MathInkReading): MathInkReading {
        val next = reading.tokens
        if (previous.isEmpty() || previous.size > MathLayout.MAX_TOKENS || previous.size != next.size ||
            previous.zip(next).any { (a, b) -> a.sourceIndex != b.sourceIndex || a.bounds != b.bounds }) return reading
        val merged = next.mapIndexed { index, token ->
            val old = previous[index]
            if (!old.uncertain && old.matchDistance == null && old.text in InkReview.symbols)
                old.copy(alternatives = old.alternatives.toList())
            else token.copy(alternatives = token.alternatives.toList())
        }
        return if (merged == next) reading else MathInkReading(MathLayout.read(merged), merged)
    }
}
