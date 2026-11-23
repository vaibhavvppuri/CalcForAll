package com.vaibhav.calc.android

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.sync.Mutex

/** Memory-only drafts. Never place bitmaps or large stroke arrays in a saved-state Bundle. */
class RecognitionWorkspaces : ViewModel() {
    var saving by mutableStateOf(false)
    var saveError by mutableStateOf<String?>(null)
    val ink = InkWorkspace()
    val photo = PhotoWorkspace()
    override fun onCleared() { ink.clear(); photo.clear() }
}

class InkWorkspace {
    val recognitionLock = Mutex()
    var strokes by mutableStateOf<List<List<InkPoint>>>(emptyList())
    private val history = InkEditHistory()
    var canUndoInk by mutableStateOf(false)
        private set
    var canRedoInk by mutableStateOf(false)
        private set
    var expression by mutableStateOf("")
    var stylusOnly by mutableStateOf(false)
    var mode by mutableStateOf("Math layout")
    var erase by mutableStateOf(false)
    var lineDraft by mutableStateOf<InkLineResult.Draft?>(null)
    var mathDraft by mutableStateOf<MathLayoutResult.Draft?>(null)
    /** Complete glyph groups stay reviewable even if their combined layout is rejected. */
    var reviewTokens by mutableStateOf<List<MathToken>>(emptyList())
    var lineSymbols by mutableStateOf<List<String>>(emptyList())
    var candidates by mutableStateOf<List<InkCandidate>>(emptyList())
    var selectedGlyph by mutableStateOf<Int?>(null)
    var notice by mutableStateOf<String?>(null)
    var inkBlocked by mutableStateOf(false)
    var recognizedStrokes: List<List<InkPoint>>? = null
    var recognizedMode: String? = null
    var recognizedRequest: Long = -1L
    var recognitionPaused by mutableStateOf(false)
        private set
    var recognitionRequest by mutableLongStateOf(0L)
        private set
    var inkRevision by mutableLongStateOf(0L)
        private set
    var reviewedLineSymbols by mutableStateOf<Set<Int>>(emptySet())
        private set
    private var expressionReview by mutableStateOf<InkExpressionReview?>(null)
    val expressionWasReviewed: Boolean
        get() = expressionReview?.matches(inkRevision, mode, expression, reviewTokens, lineSymbols) == true
    val needsExpressionReview: Boolean
        get() = expression.isNotBlank() && !expressionWasReviewed && when (mode) {
            "Math layout" -> InkRecovery.unclearMathSymbols(reviewTokens).isNotEmpty()
            "Spaced line" -> InkRecovery.unclearLineSymbols(lineDraft, reviewedLineSymbols).isNotEmpty()
            else -> false // Single-symbol mode already requires an explicit candidate tap.
        }
    var paperWidth by mutableFloatStateOf(0f)
    var paperHeight by mutableFloatStateOf(0f)
    /** A mode change rereads the drawing; it must not reclassify old machine text as a chosen symbol. */
    fun changeMode(next: String): Boolean {
        if (next !in listOf("Math layout", "Spaced line", "One symbol") || next == mode) return false
        expression = ""
        mode = next
        invalidateRecognition()
        notice = null
        return true
    }
    fun invalidateRecognition() {
        inkRevision++
        recognitionPaused = false
        expressionReview = null; reviewedLineSymbols = emptySet()
        recognizedStrokes = null; recognizedMode = null
        reviewTokens = emptyList(); mathDraft = null; selectedGlyph = null
        lineDraft = null; lineSymbols = emptyList(); candidates = emptyList()
        if (mode != "One symbol") expression = ""
    }
    fun pauseRecognition() { recognitionPaused = true; selectedGlyph = null }
    fun retryRecognition() { recognitionPaused = false; recognitionRequest++ }
    fun acknowledgeExpression(): Boolean {
        val reviewed = InkExpressionReview.capture(inkRevision, mode, expression, reviewTokens, lineSymbols) ?: return false
        expressionReview = reviewed
        return true
    }
    fun editExpression(text: String): Boolean {
        if (text.length > RecognitionCalculator.MAX_CHARS) return false
        expression = text; inkBlocked = false
        expressionReview = null
        acknowledgeExpression()
        return true
    }
    /** Wrapping uncertain machine text is not an acknowledgment of its glyphs. */
    fun transformExpression(text: String): Boolean {
        if (text.length > RecognitionCalculator.MAX_CHARS) return false
        val retainReview = expressionWasReviewed || expression.isBlank()
        expression = text; inkBlocked = false; expressionReview = null
        if (retainReview) acknowledgeExpression()
        return true
    }
    fun replaceInkSymbol(index: Int, symbol: String): Boolean {
        val edit = InkReview.replace(reviewTokens, index, symbol) ?: return false
        return applyInkEdit(inkRevision, reviewTokens, edit)
    }
    internal fun applyInkEdit(expectedRevision: Long, expectedTokens: List<MathToken>, edit: InkReview.Edit): Boolean {
        if (inkRevision != expectedRevision || reviewTokens != expectedTokens) return false
        expressionReview = null
        reviewTokens = edit.tokens
        when (val layout = edit.layout) {
            is MathLayoutResult.Draft -> {
                mathDraft = layout; expression = layout.expression
                notice = layout.warnings.joinToString(" ")
            }
            is MathLayoutResult.Rejected -> {
                mathDraft = null; expression = ""
                notice = layout.reason + " Your symbol choices are kept; correct another symbol or edit the expression."
            }
        }
        selectedGlyph = null
        return true
    }
    fun replaceLineSymbol(index: Int, symbol: String): Boolean {
        if (mode != "Spaced line" || lineDraft == null || index !in lineSymbols.indices || symbol !in InkReview.lineSymbols) return false
        lineSymbols = lineSymbols.toMutableList().also { it[index] = symbol }
        reviewedLineSymbols = reviewedLineSymbols + index
        expression = lineSymbols.joinToString(""); expressionReview = null; selectedGlyph = null
        return true
    }
    fun commitInk(next: List<List<InkPoint>>): Boolean {
        val copy = InkEditHistory.snapshot(next)
        if (!history.record(strokes, copy)) return false
        strokes = copy
        invalidateRecognition(); publishHistory()
        return true
    }
    /** Called once after a whole eraser gesture has already updated the visible ink. */
    fun recordInkEdit(before: List<List<InkPoint>>): Boolean {
        val copy = InkEditHistory.snapshot(strokes)
        if (!history.record(before, copy)) return false
        strokes = copy
        invalidateRecognition(); publishHistory()
        return true
    }
    fun undoInk(): Boolean {
        val restored = history.undo(strokes) ?: return false
        strokes = restored
        invalidateRecognition(); publishHistory()
        return true
    }
    fun redoInk(): Boolean {
        val restored = history.redo(strokes) ?: return false
        strokes = restored
        invalidateRecognition(); publishHistory()
        return true
    }
    fun resetInkHistory() { history.clear(); publishHistory() }
    private fun publishHistory() { canUndoInk = history.canUndo; canRedoInk = history.canRedo }
    fun clear() {
        invalidateRecognition()
        strokes = emptyList(); resetInkHistory(); expression = ""
        lineDraft = null; mathDraft = null; reviewTokens = emptyList(); lineSymbols = emptyList()
        candidates = emptyList(); selectedGlyph = null; notice = null; inkBlocked = false
        recognizedStrokes = null; recognizedMode = null
        paperWidth = 0f; paperHeight = 0f; erase = false
    }
}

data class PhotoRenderRequest(val region: PhotoRegion, val quarterTurns: Int,
                              val treatment: PhotoImageTreatment = PhotoImageTreatment.ORIGINAL,
                              val geometry: PhotoGeometryAdjustment = PhotoGeometryAdjustment.NONE)

class PhotoWorkspace {
    var source by mutableStateOf<PhotoSource?>(null)
    var bitmap by mutableStateOf<Bitmap?>(null)
    var region by mutableStateOf(PhotoRegion.FULL)
    var quarterTurns by mutableIntStateOf(0)
    var treatment by mutableStateOf(PhotoImageTreatment.ORIGINAL)
    var geometry by mutableStateOf(PhotoGeometryAdjustment.NONE)
    var geometryEditing by mutableStateOf(false)
    var geometryDraft by mutableStateOf(PhotoGeometryAdjustment.NONE)
    var geometryPreview by mutableStateOf<Bitmap?>(null)
    var text by mutableStateOf("")
    var recognizedText by mutableStateOf("")
    var error by mutableStateOf<String?>(null)
    var warnings by mutableStateOf<List<String>>(emptyList())
    var imageAdvice by mutableStateOf<List<String>>(emptyList())
    var corrections by mutableStateOf<List<PhotoMathCorrection>>(emptyList())
    var editRejected by mutableStateOf(false)
    var needsRead by mutableStateOf(false)
    var pendingRender by mutableStateOf<PhotoRenderRequest?>(null)
    var crop by mutableStateOf(false)
    var cropX by mutableStateOf(0f..1f)
    var cropY by mutableStateOf(0f..1f)
    var revision = 0
    fun clear() {
        revision++
        source = null; bitmap = null; region = PhotoRegion.FULL; quarterTurns = 0; treatment = PhotoImageTreatment.ORIGINAL
        geometry = PhotoGeometryAdjustment.NONE
        geometryEditing = false; geometryDraft = PhotoGeometryAdjustment.NONE; geometryPreview = null
        text = ""; recognizedText = ""; error = null; warnings = emptyList(); imageAdvice = emptyList(); corrections = emptyList()
        editRejected = false; needsRead = false; pendingRender = null; crop = false; cropX = 0f..1f; cropY = 0f..1f
    }
}
