package com.vaibhav.calc.android

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaibhav.calc.android.theme.*
import com.vaibhav.calc.core.eval.*
import com.vaibhav.calc.core.format.OutcomeFormatter
import com.vaibhav.calc.core.keypad.*
import com.vaibhav.calc.core.name.Autocomplete
import com.vaibhav.calc.core.tape.*

/** Keep the sheet outside system bars, including the side navigation bar in landscape. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CalcModalSheet(onDismissRequest: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val hostView = LocalView.current
    // Material3 1.2.1 uses a layout-no-limits popup, not a Dialog. Its inherited
    // Compose insets can already be consumed by the fitted activity window.
    // Read the host's raw insets and pad outside the sheet Surface so the
    // activity's black navigation bar remains visible, including on API 23.
    fun hostSystemBars(): WindowInsets {
        val bars = androidx.core.view.ViewCompat.getRootWindowInsets(hostView)
            ?.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        return if (bars == null) WindowInsets(0, 0, 0, 0)
            else WindowInsets(bars.left, bars.top, bars.right, bars.bottom)
    }
    // This Material3 version remembers its initial WindowInsets instance.
    val sheetInsets = remember(hostView) { MutableWindowInsets(hostSystemBars()) }
    DisposableEffect(hostView, sheetInsets) {
        val observer = hostView.viewTreeObserver
        val listener = android.view.ViewTreeObserver.OnGlobalLayoutListener {
            sheetInsets.insets = hostSystemBars()
        }
        observer.addOnGlobalLayoutListener(listener)
        sheetInsets.insets = hostSystemBars()
        onDispose {
            if (observer.isAlive) observer.removeOnGlobalLayoutListener(listener)
            else hostView.viewTreeObserver.removeOnGlobalLayoutListener(listener)
        }
    }
    ModalBottomSheet(onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        windowInsets = sheetInsets) {
        val popupView = LocalView.current
        DisposableEffect(popupView) {
            fun fitPopupWidth() {
                val root = popupView.rootView
                if (!root.isAttachedToWindow) return
                val params = root.layoutParams as? android.view.WindowManager.LayoutParams
                    ?: return
                if (params.type != android.view.WindowManager.LayoutParams.TYPE_APPLICATION_PANEL ||
                    params.width == android.view.WindowManager.LayoutParams.MATCH_PARENT) return
                // Material3 1.2.1 freezes the initial display width. Let this
                // popup's window resize across rotation without recreating the
                // composition (and losing entered tool values).
                val resized = android.view.WindowManager.LayoutParams().apply {
                    copyFrom(params)
                    width = android.view.WindowManager.LayoutParams.MATCH_PARENT
                }
                val manager = root.context.getSystemService(android.content.Context.WINDOW_SERVICE)
                    as android.view.WindowManager
                manager.updateViewLayout(root, resized)
            }
            val listener = object : android.view.View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(view: android.view.View) = fitPopupWidth()
                override fun onViewDetachedFromWindow(view: android.view.View) = Unit
            }
            popupView.addOnAttachStateChangeListener(listener)
            fitPopupWidth()
            onDispose { popupView.removeOnAttachStateChangeListener(listener) }
        }
        content()
    }
}

internal fun tapeText(entry: TapeEntry, settings: Settings): String = when (val v = entry.value) {
    is EntryValue.Computed -> OutcomeFormatter.render(v.outcome, entry.source.toInput(), settings).let {
        (if(it.approximate) "≈ " else "") + it.primary
    }
    is EntryValue.Failed -> v.error.message
    is EntryValue.Blocked -> v.description
    is EntryValue.Cycle -> "Reference cycle: ${v.members.joinToString()}"
    is EntryValue.Ambiguous -> "More than one interpretation"
    is EntryValue.Stale -> "Updating…"
    EntryValue.Empty -> "Empty"
}

@Composable
fun CompactTape(ui: CalcUiState, settings: Settings, reuse: (EntryId) -> Unit, reference: (EntryId) -> Unit) {
    val p = LocalCalcPalette.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
        ui.entries.takeLast(2).forEach { entry ->
            Row(Modifier.fillMaxWidth().heightIn(min = 36.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                TextButton(onClick = { reference(entry.id) }, contentPadding = PaddingValues(4.dp)) {
                    Text(entry.id.toString(), fontSize = 11.sp)
                }
                Text(entry.source.toInput(), color = p.ink3, fontSize = 11.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).clickable { reuse(entry.id) })
                Text(tapeText(entry, settings), color = if (entry.value is EntryValue.Computed) p.ink2 else p.amber2,
                    fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 150.dp).padding(start = 8.dp).clickable { reuse(entry.id) })
            }
        }
    }
}

@Composable
fun FunctionSheet(onSelect: (PaletteEntry) -> Unit) {
    var query by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Text("Functions", style = MaterialTheme.typography.headlineSmall)
        Text("Tap to insert. Swipe ↑ on a keypad key for its second action.", fontSize = 12.sp,
            modifier = Modifier.padding(vertical = 8.dp))
        OutlinedTextField(query, { query = it.take(80) }, label = { Text("Find a function") },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 470.dp).padding(top = 12.dp)) {
            if (query.isNotBlank()) {
                val matches = FnPalette.search(query, 60)
                if (matches.isEmpty()) item { Text("No matching function. Try sin, root, log or gcd.", modifier = Modifier.padding(16.dp)) }
                items(matches) { FunctionItem(it, onSelect) }
            } else FnPalette.categories.forEach { category ->
                item { Text(category.title, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 16.dp, bottom = 6.dp)) }
                items(category.entries) { FunctionItem(it, onSelect) }
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun FunctionItem(entry: PaletteEntry, choose: (PaletteEntry) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { choose(entry) }.padding(vertical = 12.dp)) {
        Text(entry.label, modifier = Modifier.width(70.dp), fontFamily = CalcFonts.JetBrainsMono)
        Text(entry.summary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
    }
}

@Composable
fun TypedEntrySheet(initial: String, onUse: (String) -> Unit, onPaste: () -> String, onUndo: () -> Unit, onRedo: () -> Unit) {
    var value by remember(initial) { mutableStateOf(TextFieldValue(initial, TextRange(initial.length))) }
    val suggestions = remember(value) { Autocomplete.suggestAt(value.text, value.selection.end, limit = 6) }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(20.dp)) {
        Text("Edit expression", style = MaterialTheme.typography.headlineSmall)
        Text("Equations: x²−5x+6=0 · Units: 5 km+300 m · Store: 12→A · Link: #1*2",
            fontSize = 12.sp, modifier = Modifier.padding(vertical = 12.dp))
        OutlinedTextField(value, { if (it.text.length <= CalcSession.MAX_ENTRY_CHARS) value = it },
            label = { Text("Expression") }, modifier = Modifier.fillMaxWidth().heightIn(min = 110.dp), maxLines = 6)
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            suggestions.forEach { suggestion ->
                SuggestionChip(onClick = {
                    val cursor = value.selection.end
                    val prefix = Autocomplete.prefixAt(value.text, cursor)
                    val next = value.text.replaceRange(cursor - prefix.length, cursor, suggestion.insert)
                    value = TextFieldValue(next, TextRange(cursor - prefix.length + suggestion.insert.length))
                }, label = { Text(suggestion.text) }, modifier = Modifier.padding(end = 6.dp))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = {
                val pasted = onPaste().take(CalcSession.MAX_ENTRY_CHARS)
                value = TextFieldValue(pasted, TextRange(pasted.length))
            }) { Text("Paste") }
            TextButton(onClick = onUndo) { Text("Undo entry") }
            TextButton(onClick = onRedo) { Text("Redo") }
        }
        Button(onClick = { onUse(value.text) }, modifier = Modifier.fillMaxWidth()) { Text("Use expression") }
        Text("Press = on the calculator to calculate or save an edit.", fontSize = 12.sp,
            modifier = Modifier.padding(vertical = 12.dp))
    }
}

@Composable
fun HistorySheet(ui: CalcUiState, settings: Settings, onReuse: (EntryId) -> Unit, onRef: (EntryId) -> Unit,
    onEdit: (EntryId) -> Unit, onDelete: (EntryId) -> Unit, onClear: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("History", style = MaterialTheme.typography.headlineSmall)
            if (ui.entries.isNotEmpty()) TextButton(onClick = { confirmClear = true }) { Text("Clear") }
        }
        OutlinedTextField(query, { query = it.take(80) }, label = { Text("Search calculations") },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        val entries = ui.entries.asReversed().filter { query.isBlank() || it.source.toInput().contains(query, true) || it.id.toString().contains(query) }
        LazyColumn(Modifier.heightIn(max = 530.dp)) {
            if (entries.isEmpty()) item { Text("Your calculations will appear here. Tap a # number to use a live link.", modifier = Modifier.padding(vertical = 24.dp)) }
            items(entries, key = { it.id.n }) { entry ->
                Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                    Text("${entry.id} · ${entry.settings?.angle?.name ?: settings.angle.name}",
                        color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                    SelectionContainer { Text(entry.source.toInput(), modifier = Modifier.padding(top = 8.dp), fontFamily = CalcFonts.JetBrainsMono) }
                    SelectionContainer { Text(tapeText(entry, settings), modifier = Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.titleMedium) }
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        TextButton(onClick = { onReuse(entry.id) }) { Text("Reuse") }
                        TextButton(onClick = { onRef(entry.id) }) { Text("Link ${entry.id}") }
                        TextButton(onClick = { onEdit(entry.id) }) { Text("Edit") }
                        TextButton(onClick = { onDelete(entry.id) }) { Text("Delete") }
                    }
                    HorizontalDivider()
                }
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }
    if (confirmClear) AlertDialog(onDismissRequest = { confirmClear = false }, title = { Text("Clear history?") },
        text = { Text("This clears calculations and their links. You can undo immediately afterward.") },
        confirmButton = { TextButton(onClick = { confirmClear = false; onClear() }) { Text("Clear history") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } })
}

@Composable
internal fun ChoiceRow(label: String, choices: List<String>, selected: String, choose: (String) -> Unit) {
    Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp))
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        choices.forEach { option -> FilterChip(selected == option, onClick = { choose(option) }, label = { Text(option.lowercase().replaceFirstChar { it.uppercase() }) }) }
    }
}

@Composable
fun SettingsSheet(settings: Settings, theme: String, onSettings: ((Settings) -> Settings) -> Unit, onTheme: (String) -> Unit, onDeleteData: () -> Unit, onLegal: () -> Unit) {
    var confirmDelete by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)
        ChoiceRow("Appearance", listOf("SYSTEM", "LIGHT", "DARK", "AMOLED"), theme, onTheme)
        ChoiceRow("Angles for new calculations", AngleMode.entries.map { it.name }, settings.angle.name) { value -> onSettings { it.copy(angle = AngleMode.valueOf(value)) } }
        Text("Saved calculations keep the angle mode they were created with.", fontSize = 12.sp)
        ChoiceRow("Digit grouping", GroupingMode.entries.map { it.name }, settings.grouping.name) { value -> onSettings { it.copy(grouping = GroupingMode.valueOf(value)) } }
        ChoiceRow("Numerals", NumeralSystem.entries.map { it.name }, settings.numerals.name) { value -> onSettings { it.copy(numerals = NumeralSystem.valueOf(value)) } }
        ChoiceRow("Notation", NotationMode.entries.map { it.name }, settings.notation.name) { value -> onSettings { it.copy(notation = NotationMode.valueOf(value)) } }
        ChoiceRow("Exact answers", ExactForm.entries.map { it.name }, settings.exactForm.name) { value -> onSettings { it.copy(exactForm = ExactForm.valueOf(value)) } }
        ChoiceRow("Displayed significant digits", listOf("8", "12", "16", "24", "32"), settings.displayPrecision.toString()) { value ->
            onSettings { it.copy(displayPrecision = value.toInt(), computePrecision = maxOf(40, value.toInt() + 12)) }
        }
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("Complex numbers"); Text("For example, √(−4) = 2i", fontSize = 12.sp) }
            Switch(settings.complexEnabled, onCheckedChange = { value -> onSettings { it.copy(complexEnabled = value) } },
                modifier = Modifier.semantics { contentDescription = "Complex numbers" })
        }
        HorizontalDivider(Modifier.padding(vertical = 20.dp))
        Text("Private by design", style = MaterialTheme.typography.titleMedium)
        Text("Calculations and settings stay on this device. No account, ads, paid API or image upload. The app has no Internet permission. Deleting app data removes the saved session.",
            fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
        Text("Photo text uses a bundled Google ML Kit model. Google's SDK terms describe performance and usage metrics; this build blocks direct app networking. Images and recognized text are processed on-device. See the included privacy policy for details.",
            fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        Text("CalcForAll · MIT source license. Bundled fonts and libraries retain their own licenses. See the source distribution for notices.",
            fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
        TextButton(onClick = onLegal) { Text("Privacy policy & open-source licenses") }
        TextButton(onClick = { confirmDelete = true }) { Text("Delete local data") }
        Spacer(Modifier.height(24.dp))
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("Delete local data?") },
        text = { Text("This permanently deletes the saved session and history on this device.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; onDeleteData() }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } })
}
