package com.vaibhav.calc.android

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaibhav.calc.android.theme.LocalCalcPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** License text is bundled and paged locally; opening it never follows a web link. */
@Composable
fun LegalScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val p = LocalCalcPalette.current
    var files by remember { mutableStateOf<List<String>>(emptyList()) }
    var selected by remember { mutableStateOf<String?>(null) }
    var page by remember { mutableStateOf(0) }
    var body by remember { mutableStateOf("") }
    var hasNext by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    BackHandler { if(selected != null) selected = null else onClose() }
    LaunchedEffect(Unit) { files = withContext(Dispatchers.IO) { context.assets.list("legal")?.sorted().orEmpty() } }
    LaunchedEffect(selected, page) {
        val name = selected ?: return@LaunchedEffect
        loading = true
        try {
            val result = withContext(Dispatchers.IO) {
                context.assets.open("legal/$name").bufferedReader().use { reader ->
                    var remaining = page * 20000L
                    while(remaining > 0) {
                        val skipped = reader.skip(remaining)
                        if(skipped == 0L) break
                        remaining -= skipped
                    }
                    val buffer = CharArray(20001)
                    var count = 0
                    while(count < buffer.size) {
                        val n = reader.read(buffer, count, buffer.size - count)
                        if(n < 0) break
                        count += n
                    }
                    String(buffer, 0, minOf(count, 20000)) to (count > 20000)
                }
            }
            body = result.first; hasNext = result.second
        } catch(e: java.io.IOException) { body = "Could not read this bundled document: ${e.message}"; hasNext = false }
        finally { loading = false }
    }
    Surface(Modifier.fillMaxSize(), color = p.paper) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if(selected == null) "Privacy & licenses" else "Document", style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = { if(selected != null) selected = null else onClose() }) { Text(if(selected == null) "Settings" else "All documents") }
            }
            if(selected == null) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    Text("All documents are included in the app and available offline.", fontSize = 13.sp, modifier = Modifier.padding(bottom = 12.dp))
                    files.forEach { name ->
                        TextButton(onClick = { page = 0; selected = name }, modifier = Modifier.fillMaxWidth()) { Text(name) }
                    }
                    if(files.isEmpty()) Text("Documents are not available in this build. They are included in the source distribution.")
                }
            } else {
                Text(selected.orEmpty(), fontSize = 12.sp)
                if(loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                key(selected, page) {
                    SelectionContainer(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 12.dp)) {
                        Text(body, fontSize = 13.sp)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(enabled = page > 0 && !loading, onClick = { page-- }) { Text("Previous") }
                    Text("Page ${page + 1}", modifier = Modifier.padding(12.dp), fontSize = 12.sp)
                    TextButton(enabled = hasNext && !loading, onClick = { page++ }) { Text("Next") }
                }
            }
        }
    }
}
