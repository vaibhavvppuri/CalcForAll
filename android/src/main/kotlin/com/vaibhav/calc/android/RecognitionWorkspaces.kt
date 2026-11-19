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
    var redo by mutableStateOf<List<List<InkPoint>>>(emptyList())
    var expression by mutableStateOf("")
    var stylusOnly by mutableStateOf(false)
    var mode by mutableStateOf("Math layout")
    var erase by mutableStateOf(false)
    var lineDraft by mutableStateOf<InkLineResult.Draft?>(null)
    var mathDraft by mutableStateOf<MathLayoutResult.Draft?>(null)
    var lineSymbols by mutableStateOf<List<String>>(emptyList())
    var candidates by mutableStateOf<List<InkCandidate>>(emptyList())
    var selectedGlyph by mutableStateOf<Int?>(null)
    var notice by mutableStateOf<String?>(null)
    var inkBlocked by mutableStateOf(false)
    var recognizedStrokes: List<List<InkPoint>>? = null
    var recognizedMode: String? = null
    var paperWidth by mutableFloatStateOf(0f)
    var paperHeight by mutableFloatStateOf(0f)
    fun invalidateRecognition() { recognizedStrokes = null; recognizedMode = null }
    fun clear() {
        strokes = emptyList(); redo = emptyList(); expression = ""
        lineDraft = null; mathDraft = null; lineSymbols = emptyList()
        candidates = emptyList(); selectedGlyph = null; notice = null; inkBlocked = false
        recognizedStrokes = null; recognizedMode = null
        paperWidth = 0f; paperHeight = 0f; erase = false
    }
}

data class PhotoRenderRequest(val region: PhotoRegion, val quarterTurns: Int)

class PhotoWorkspace {
    var source by mutableStateOf<PhotoSource?>(null)
    var bitmap by mutableStateOf<Bitmap?>(null)
    var region by mutableStateOf(PhotoRegion.FULL)
    var quarterTurns by mutableIntStateOf(0)
    var text by mutableStateOf("")
    var recognizedText by mutableStateOf("")
    var error by mutableStateOf<String?>(null)
    var warnings by mutableStateOf<List<String>>(emptyList())
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
        source = null; bitmap = null; region = PhotoRegion.FULL; quarterTurns = 0
        text = ""; recognizedText = ""; error = null; warnings = emptyList(); corrections = emptyList()
        editRejected = false; needsRead = false; pendingRender = null; crop = false; cropX = 0f..1f; cropY = 0f..1f
    }
}
