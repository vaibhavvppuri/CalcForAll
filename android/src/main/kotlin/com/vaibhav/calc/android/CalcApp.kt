package com.vaibhav.calc.android

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaibhav.calc.android.theme.*
import com.vaibhav.calc.core.Calculator
import com.vaibhav.calc.core.keypad.*
import com.vaibhav.calc.core.tape.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

private data class RecognitionSaveRequest(val text: String, val from: String)
private data class SessionAction(val run: () -> CalcUiState, val resetStorage: Boolean = false,
                                 val persist: Boolean = true, val recognition: RecognitionSaveRequest? = null)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalcApp(workspaces: RecognitionWorkspaces) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val store = remember { SessionStore(context) }
    val session = remember { CalcSession() }
    var ui by remember { mutableStateOf(session.ui) }
    var theme by remember { mutableStateOf("SYSTEM") }
    var screen by rememberSaveable { mutableStateOf<String?>(null) }
    var toolInsertionError by remember { mutableStateOf<String?>(null) }
    var initialized by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var persistenceAllowed by remember { mutableStateOf(true) }
    val actions = remember { Channel<SessionAction>(Channel.UNLIMITED) }
    val scope = rememberCoroutineScope()
    var saveJob by remember { mutableStateOf<Job?>(null) }
    var latestSnapshot by remember { mutableStateOf<SessionSnapshot?>(null) }
    var graphEnvironment by remember { mutableStateOf(com.vaibhav.calc.core.eval.Environment()) }
    var openEdit by remember { mutableStateOf(false) }
    var recognitionSaving by workspaces::saving
    var recognitionQueued by remember { mutableStateOf(false) }
    fun act(action: () -> CalcUiState) { actions.trySend(SessionAction(action)) }
    fun saveRecognition(text: String, from: String) {
        if (recognitionSaving) return
        recognitionSaving = true
        recognitionQueued = true
        workspaces.saveError = null
        if (actions.trySend(SessionAction({ session.ui }, persist = false,
                recognition = RecognitionSaveRequest(text, from))).isFailure) {
            recognitionSaving = false
            recognitionQueued = false
            workspaces.saveError = "The calculation could not be queued. Your draft is still available."
        }
    }

    DisposableEffect(actions) {
        onDispose {
            // A queued action may never enter its NonCancellable transaction when the
            // activity is recreated. Keep the draft retryable instead of leaving it locked.
            if (recognitionQueued) {
                recognitionQueued = false
                workspaces.saving = false
                workspaces.saveError = "The screen changed before saving started. Your draft is still available; review and save it again."
            }
            actions.close()
        }
    }

    LaunchedEffect(session) {
        val saved = withContext(Dispatchers.IO) { store.load() }
        when (saved) {
            is SnapshotRead.Restored -> {
                theme = saved.snapshot.theme
                try {
                    ui = withContext(Dispatchers.Default) {
                        session.updateSettings(saved.snapshot.settings)
                        session.restore(saved.snapshot.tape, saved.snapshot.editor)
                    }
                    latestSnapshot = saved.snapshot
                    graphEnvironment = session.snapshotEnvironment()
                } catch (e: CancellationException) { throw e }
                  catch (e: Exception) {
                    persistenceAllowed = false
                    ui = session.report("Could not restore this session: ${e.message}. The saved file was preserved.")
                }
            }
            is SnapshotRead.Invalid -> {
                persistenceAllowed = false
                ui = session.report(saved.reason + " The saved file was preserved. Use Settings → Delete local data to reset.")
            }
            null -> Unit
        }
        initialized = true
        for (action in actions) {
            busy = true
            try {
                val recognition = action.recognition
                if (recognition != null) {
                    recognitionQueued = false
                    // Finish disk commit/rollback and UI publication together even if the
                    // composition is disposed. A recreated store invalidates this owner.
                    withContext(NonCancellable) {
                        var ticket: Long? = null
                        try {
                            if (!persistenceAllowed) {
                                workspaces.saveError = "The saved session needs recovery before a reviewed calculation can be saved. Your draft is still available."
                            } else {
                                saveJob?.cancelAndJoin()
                                val reserved = store.beginDurableWrite()
                                ticket = reserved
                                val result = withContext(Dispatchers.IO) {
                                    session.saveRecognitionDurably(recognition.text, theme) { candidate ->
                                        store.saveDurably(candidate, reserved)
                                    }
                                }
                                ui = result.state
                                if (result.accepted) {
                                    latestSnapshot = result.snapshot!!
                                    graphEnvironment = result.environment!!
                                    runCatching {
                                        if (recognition.from == "ink") workspaces.ink.clear() else workspaces.photo.clear()
                                    }.onFailure {
                                        // Persistence already succeeded: never offer a blind retry
                                        // merely because an optional draft cleanup failed.
                                        toolInsertionError = "The calculation was saved, but its draft could not be cleared. Check History before saving it again."
                                    }
                                    if (screen == recognition.from) screen = null
                                } else {
                                    workspaces.saveError = result.error ?: "The calculation could not be saved. Your draft is still available."
                                }
                            }
                        } catch (problem: Exception) {
                            workspaces.saveError = "Could not save locally: ${problem.message ?: problem.javaClass.simpleName}. Your draft is still available."
                        } finally {
                            ticket?.let(store::endDurableWrite)
                            recognitionSaving = false
                        }
                    }
                    continue
                }
                val result = withContext(Dispatchers.Default) {
                    val next = action.run()
                    Triple(next, SessionSnapshot(session.settings, next.editor, session.archive(), theme), session.snapshotEnvironment())
                }
                ui = result.first
                latestSnapshot = result.second
                graphEnvironment = result.third
                if (action.resetStorage) persistenceAllowed = true
                if (persistenceAllowed && action.persist) {
                    val ticket = store.newWriteTicket()
                    saveJob?.cancel()
                    saveJob = scope.launch {
                        delay(350)
                        try { withContext(Dispatchers.IO) { store.save(result.second, ticket) } }
                        catch (e: java.io.IOException) {
                            if(latestSnapshot === result.second) actions.trySend(SessionAction({ session.report("Could not save locally: " + e.message) }, persist = false))
                        }
                        catch (e: IllegalArgumentException) {
                            if(latestSnapshot === result.second) actions.trySend(SessionAction({ session.report("Could not save this session: " + e.message) }, persist = false))
                        }
                    }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { ui = session.report("Could not complete that action: " + (e.message ?: e.javaClass.simpleName)) }
            finally { busy = false }
        }
    }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, store) {
        fun flush() {
            // Read the state directly: a rememberUpdatedState value can still be one
            // composition behind immediately after a durable transaction publishes.
            val snapshot = latestSnapshot
            if (persistenceAllowed && !recognitionSaving && snapshot != null) {
                // Capture both snapshot and ticket now; acquiring a ticket later on
                // IO could incorrectly supersede a newer reviewed calculation.
                try { store.enqueueLifecycleSave(snapshot) }
                catch (_: java.io.IOException) { /* The prior atomic snapshot is retained. */ }
                catch (_: IllegalArgumentException) { /* Oversize data cannot replace a valid snapshot. */ }
            }
        }
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) flush() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); flush() }
    }
    LaunchedEffect(ui.editingId, openEdit) {
        if(openEdit && ui.editingId != null) { screen = "type"; openEdit = false }
    }
    LaunchedEffect(ui.notice) {
        if (ui.notice != null && ui.noticeUndo) {
            delay(4200)
            act { session.dismissNotice() }
        }
    }
    val dark = isSystemInDarkTheme()
    val mode = when (theme) {
        "LIGHT" -> CalcThemeMode.LIGHT
        "DARK" -> CalcThemeMode.DARK
        "AMOLED" -> CalcThemeMode.AMOLED
        else -> if (dark) CalcThemeMode.DARK else CalcThemeMode.LIGHT
    }
    CalcTheme(mode) {
        val p = LocalCalcPalette.current
        if (!initialized) {
            Box(Modifier.fillMaxSize().background(p.paper), contentAlignment = Alignment.Center) {
                Text("Restoring your calculations…", color = p.ink)
            }
        } else BoxWithConstraints(Modifier.fillMaxSize().paperGrid(p)) {
            val landscape = maxWidth > maxHeight && maxWidth > 560.dp
            val compact = maxHeight < 700.dp
            val tightLandscape = landscape && maxHeight < 380.dp
            val keypad: @Composable () -> Unit = {
                Column {
                    UtilityRow(ui.angle.name, { act { session.cycleAngle() } }, { act { session.insertAns() } },
                        { act { session.left() } }, { act { session.right() } }, { screen = "type" })
                    KeypadGrid(ui.editor.selection != null, { key ->
                        if (key.tap == KeyAction.OpenPalette) screen = "palette" else act { session.press(key) }
                    }, { key -> act { session.flickUp(key) } }, { act { session.backspaceRepeat() } },
                        keyHeight = if(compact) 48.dp else 56.dp, verticalGap = if(tightLandscape) 0.dp else 8.dp,
                        verticalPadding = if(tightLandscape) 0.dp else 8.dp)
                }
            }
            val display: @Composable ColumnScope.() -> Unit = {
                AppHeader(ui.entries.size, { screen = "history" }, { screen = "graph" }, { screen = "tools" }, { screen = "settings" })
                if(!compact) CompactTape(ui, session.settings, { id -> act { session.reuse(id) } }, { id -> act { session.insertReference(id.n) } })
                DisplayStrip(ui, { act { session.sdToggle() } }, { act { session.moveCursor(it) } },
                    { clipboard.setText(AnnotatedString(ui.clipboardResult ?: ui.exact.orEmpty())); act { session.report("Result copied") } },
                    { screen = "steps" }, Modifier.weight(1f).fillMaxWidth())
                ui.notice?.let { message ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp).background(p.ink, RoundedCornerShape(9.dp))
                        .padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(message, color = p.sheet, fontSize = 12.sp, modifier = Modifier.weight(1f).padding(vertical = 8.dp))
                        if (ui.noticeUndo) TextButton(onClick = { act { session.undoLastNotice() } }) {
                            Text("UNDO", color = p.amber, fontSize = 11.sp)
                        } else TextButton(onClick = { act { session.dismissNotice() } }) { Text("×", color = p.sheet) }
                    }
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp), color = p.blue)
            }
            if (landscape) Row(Modifier.fillMaxSize()) {
                Column(Modifier.weight(1f).fillMaxHeight(), content = display)
                Box(Modifier.widthIn(max = 430.dp).weight(1f).align(Alignment.Bottom)) { keypad() }
            } else Column(Modifier.fillMaxSize()) {
                Column(Modifier.weight(1f).fillMaxWidth(), content = display)
                keypad()
            }
        }

        when (screen) {
            "palette" -> CalcModalSheet(onDismissRequest = { screen = null }) {
                FunctionSheet { entry -> act { session.paletteInsert(entry) }; screen = null }
            }
            "type" -> CalcModalSheet(onDismissRequest = { screen = null }) {
                TypedEntrySheet(ui.entryText, onUse = { text -> act { session.replaceText(text) }; screen = null },
                    onPaste = { clipboard.getText()?.text.orEmpty() },
                    onUndo = { act { session.undoEntry() } }, onRedo = { act { session.redoEntry() } })
            }
            "history" -> CalcModalSheet(onDismissRequest = { screen = null }) {
                HistorySheet(ui, session.settings, onReuse = { act { session.reuse(it) }; screen = null },
                    onRef = { act { session.insertReference(it.n) }; screen = null },
                    onEdit = { openEdit = true; act { session.beginEdit(it) } },
                    onDelete = { act { session.deleteEntry(it) } },
                    onClear = { act { session.clearHistory() }; screen = null })
            }
            "settings" -> CalcModalSheet(onDismissRequest = { screen = null }) {
                SettingsSheet(session.settings, theme,
                    onSettings = { update -> act { session.updateSettings(update(session.settings)) } },
                    onTheme = { value -> theme = value; act { session.ui } },
                    onDeleteData = {
                        saveJob?.cancel()
                        persistenceAllowed = false
                        latestSnapshot = null
                        // Invalidate every pending write before removing the file. A queued
                        // action cannot reactivate persistence until this reset is processed.
                        store.delete()
                        theme = "SYSTEM"
                        actions.trySend(SessionAction({
                            session.updateSettings(com.vaibhav.calc.core.eval.Settings())
                            session.restore(TapeArchive())
                            session.report("Local calculations deleted")
                        }, resetStorage = true))
                    }, onLegal = { screen = "legal" })
            }
            "steps" -> CalcModalSheet(onDismissRequest = { screen = null }) {
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp)) {
                    Text("Working", style = MaterialTheme.typography.titleLarge)
                    ui.steps.forEachIndexed { i, line -> Text("${i + 1}. $line", modifier = Modifier.padding(top = 16.dp)) }
                    Spacer(Modifier.height(24.dp))
                }
            }
            "tools" -> CalcModalSheet(onDismissRequest = { screen = null }) {
                ToolsSheet(session.settings, onInsert = { text, angle, notice ->
                    toolInsertionError = null
                    act {
                        val replacement = session.replaceToolText(text, angle, notice)
                        // Session work runs off the UI thread; surface its explicit
                        // outcome on the composition's scope without masking errors.
                        scope.launch {
                            if (screen == "tools") {
                                if (replacement.accepted) screen = null
                                else toolInsertionError = replacement.state.notice ?: "The expression could not be inserted."
                            }
                        }
                        replacement.state
                    }
                },
                    onHandwriting = { screen = "ink" }, onPhoto = { screen = "photo" }, onSurface = { screen = "surface" })
            }
            "graph" -> GraphScreen(session.settings, ui.entryText, graphEnvironment, onClose = { screen = null })
            "ink" -> HandwritingScreen(session.settings, graphEnvironment, workspace = workspaces.ink, saving = recognitionSaving, onClose = { if (!recognitionSaving) screen = null }, onUse = { saveRecognition(it, "ink") })
            "photo" -> PhotoScreen(session.settings, graphEnvironment, workspace = workspaces.photo, saving = recognitionSaving, onClose = { if (!recognitionSaving) screen = null }, onUse = { saveRecognition(it, "photo") })
            "legal" -> LegalScreen(onClose = { screen = "settings" })
            "surface" -> SurfaceScreen(session.settings, onClose = { screen = null }, environment = graphEnvironment)
        }
        (workspaces.saveError ?: toolInsertionError)?.let { message ->
            fun dismissError() { workspaces.saveError = null; toolInsertionError = null }
            AlertDialog(onDismissRequest = ::dismissError,
                title = { Text(if (workspaces.saveError != null) "Calculation was not saved" else "Could not use result") }, text = { Text(message) },
                confirmButton = { TextButton(onClick = ::dismissError) { Text("OK") } })
        }
    }
}

@Composable
private fun AppHeader(count: Int, history: () -> Unit, graph: () -> Unit, tools: () -> Unit, settings: () -> Unit) {
    val p = LocalCalcPalette.current
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("CalcForAll", fontFamily = CalcFonts.PlexSans, color = p.ink, fontSize = 17.sp, maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Text("OFFLINE · EXACT FIRST", color = p.ink3, fontSize = 8.sp, lineHeight = 11.sp, letterSpacing = 1.sp, maxLines = 1)
        }
        TextButton(onClick = history, modifier = Modifier.width(48.dp).semantics { contentDescription = "History, $count calculations" }, contentPadding = PaddingValues(4.dp)) { Text("#$count", fontSize = 12.sp, maxLines = 1) }
        TextButton(onClick = graph, modifier = Modifier.width(56.dp), contentPadding = PaddingValues(4.dp)) { Text("Graph", fontSize = 12.sp, maxLines = 1) }
        TextButton(onClick = tools, modifier = Modifier.width(56.dp), contentPadding = PaddingValues(4.dp)) { Text("Tools", fontSize = 12.sp, maxLines = 1) }
        TextButton(onClick = settings, modifier = Modifier.width(48.dp).semantics { contentDescription = "Settings" }, contentPadding = PaddingValues(0.dp)) { Text("⋮", fontSize = 24.sp) }
    }
}

@Composable
private fun DisplayStrip(ui: CalcUiState, onSd: () -> Unit, onCursor: (com.vaibhav.calc.core.entry.Cursor) -> Unit,
    onCopy: () -> Unit, onSteps: () -> Unit, modifier: Modifier) {
    val p = LocalCalcPalette.current
    val scroll = rememberScrollState()
    // A new answer is read from its first digit.
    LaunchedEffect(ui.exact, ui.error) { scroll.scrollTo(0) }
    // A long answer runs past the strip and scrolls. It has to say so: found on a device, where
    // the 51 digits of floor(pi×10^50) ended in a line cut off by the keys with nothing to show
    // that the answer, and its decimal reading, continued below. The cues have rows of their own
    // so that they never lie over a digit.
    Column(modifier) {
    if (scroll.canScrollBackward) ScrollCue("▴ more above")
    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).padding(horizontal = 14.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.End) {
        ui.editingId?.let { Text("Editing $it", color = p.amber2, fontSize = 12.sp) }
        MathEntry(ui.editor, onCursor, Modifier.heightIn(min = 32.dp))
        val fullAnswer = ui.exact.orEmpty()
        val visibleAnswer = if(fullAnswer.length > 1200) fullAnswer.take(1200) + "…" else fullAnswer
        Text(if (ui.error != null) "—" else (if(ui.approximate && visibleAnswer.isNotEmpty()) "≈ " else "") + visibleAnswer, style = CalcType.Result, color = p.amber2,
            textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(enabled = ui.exact != null && ui.error == null, onClick = onCopy)
                .semantics { contentDescription = ui.error ?: ((if(ui.approximate) "Approximately " else "") + ui.exact.orEmpty()); liveRegion = LiveRegionMode.Polite })
        if(fullAnswer.length > 1200) Text("Showing the first 1,200 characters. Tap the answer to copy the full result.", color = p.ink3, fontSize = 12.sp)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (ui.sdVisible) TextButton(onClick = onSd, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("S⇔D", style = CalcType.SdChip) }
            if (ui.error == null && ui.decimal != null && ui.decimal != ui.exact) {
                Text("≈ ${ui.decimal}", style = CalcType.Approx, color = p.ink3)
            }
            if (ui.steps.isNotEmpty()) TextButton(onClick = onSteps) { Text("Steps", fontSize = 12.sp) }
        }
        if (ui.error != null) Text(ui.error, color = p.amber2, fontSize = 12.sp, textAlign = TextAlign.End)
    }
    if (scroll.canScrollForward) ScrollCue("▾ more below")
    }
}

@Composable
private fun ScrollCue(text: String) {
    val p = LocalCalcPalette.current
    Text(text, color = p.amber2, fontSize = 11.sp, maxLines = 1, textAlign = TextAlign.End,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp))
}

@Composable
private fun UtilityRow(angle: String, onAngle: () -> Unit, onAns: () -> Unit, left: () -> Unit, right: () -> Unit, type: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        UtilityChip(angle, onAngle, Modifier.weight(1f))
        UtilityChip("Ans", onAns, Modifier.weight(1f))
        UtilityChip("←", left, Modifier.width(48.dp))
        UtilityChip("→", right, Modifier.width(48.dp))
        UtilityChip("Edit", type, Modifier.width(52.dp))
    }
}

@Composable
private fun UtilityChip(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val p = LocalCalcPalette.current
    Box(modifier.height(48.dp).clickable(onClick = onClick).semantics {
        role = Role.Button
        contentDescription = when(label) { "←" -> "Move cursor left"; "→" -> "Move cursor right"; "Ans" -> "Insert last answer"; "Edit" -> "Edit expression"; else -> "Angle mode $label" }
    },
        contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxWidth().height(34.dp).background(p.sheet2, RoundedCornerShape(9.dp))
            .border(1.dp, p.sheetEdge, RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) {
            Text(label, style = CalcType.UtilityChip, color = p.ink2)
        }
    }
}

@Composable
private fun KeypadGrid(selected: Boolean, onTap: (Key) -> Unit, onFlick: (Key) -> Unit, repeatDelete: () -> Unit,
    keyHeight: androidx.compose.ui.unit.Dp, verticalGap: androidx.compose.ui.unit.Dp, verticalPadding: androidx.compose.ui.unit.Dp) {
    val selection by rememberUpdatedState(selected)
    Column(Modifier.fillMaxWidth().padding(start = 12.dp, top = verticalPadding, end = 12.dp, bottom = verticalPadding),
        verticalArrangement = Arrangement.spacedBy(verticalGap)) {
        Keypad.basic().rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { key -> KeyButton(key, onTap, onFlick, repeatDelete, { selection }, Modifier.weight(1f), keyHeight) }
            }
        }
    }
}

@Composable
private fun KeyButton(key: Key, onTap: (Key) -> Unit, onFlick: (Key) -> Unit,
    repeatDelete: () -> Unit, selection: () -> Boolean, modifier: Modifier, keyHeight: androidx.compose.ui.unit.Dp) {
    val p = LocalCalcPalette.current
    val kind = keyKind(key)
    val bg = when (kind) { KeyKind.NUM -> p.key; KeyKind.FN -> p.amberSoft; KeyKind.EQ -> p.blue; else -> p.sheet2 }
    val fg = when (kind) { KeyKind.NUM -> p.keyInk; KeyKind.EQ -> Color.White; KeyKind.FN, KeyKind.DEL -> p.amber2; else -> p.ink }
    val border = when (kind) { KeyKind.FN -> p.amber; KeyKind.EQ -> p.blue2; else -> p.keyEdge }
    val style = when (kind) { KeyKind.NUM -> CalcType.KeyNum; KeyKind.OP -> CalcType.KeyOp; KeyKind.UTIL -> CalcType.KeyUtil
        KeyKind.FN -> CalcType.KeyFn; KeyKind.DEL -> CalcType.KeyDel; KeyKind.EQ -> CalcType.KeyEq }
    var pressed by remember { mutableStateOf(false) }
    val shift by animateFloatAsState(if (pressed) 1.5f else 0f, tween(50), label = "key press")
    val scope = rememberCoroutineScope()
    val tap by rememberUpdatedState(onTap)
    val flick by rememberUpdatedState(onFlick)
    val del by rememberUpdatedState(repeatDelete)
    val selected by rememberUpdatedState(selection)
    val shape = RoundedCornerShape(13.dp)
    Box(modifier.height(keyHeight).graphicsLayer { translationY = shift.dp.toPx() }.clip(shape)
        .background(bg).border(1.dp, border, shape).drawWithContent {
            drawContent(); if (pressed) drawRect(Color.Black.copy(alpha = .05f))
        }.semantics {
            role = Role.Button
            contentDescription = when (key.id) { "backspace" -> "Backspace"; "fn" -> "Functions"; "equals" -> "Calculate"; else -> key.label }
            onClick { tap(key); true }
            if (key.flickUp != null) customActions = listOf(CustomAccessibilityAction("Insert " + key.flickUpLabel) { flick(key); true })
        }.onKeyEvent { event ->
            if(event.type == KeyEventType.KeyUp && event.key in listOf(androidx.compose.ui.input.key.Key.Enter,
                    androidx.compose.ui.input.key.Key.NumPadEnter, androidx.compose.ui.input.key.Key.Spacebar)) {
                tap(key); true
            } else false
        }.focusable().pointerInput(key.id) {
            awaitEachGesture {
                val down = awaitFirstDown()
                pressed = true
                var repeating: Job? = null
                val isDelete = kind == KeyKind.DEL
                if (isDelete) {
                    tap(key)
                    repeating = scope.launch {
                        delay(380)
                        while (isActive) {
                            del()
                            delay(70)
                            if (selected()) delay(310) // A fresh structural selection must stay visible before deletion.
                        }
                    }
                }
                var dy = 0f
                var released = false
                try {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        dy = change.position.y - down.position.y
                        if (!change.pressed) { released = true; change.consume(); break }
                        if (event.changes.count { it.pressed } > 1) break
                        change.consume()
                    }
                } finally { pressed = false; repeating?.cancel() }
                if (!isDelete && released) {
                    if (key.flickUp != null && dy < -14.dp.toPx()) flick(key) else tap(key)
                }
            }
        }, contentAlignment = Alignment.Center) {
        Text(key.label, style = style, color = fg)
    }
}
