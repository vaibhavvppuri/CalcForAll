package com.vaibhav.calc.android

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaibhav.calc.android.theme.CalcFonts
import com.vaibhav.calc.core.constants.Constants
import com.vaibhav.calc.core.eval.*
import com.vaibhav.calc.core.format.Formatter
import com.vaibhav.calc.core.units.Units
import java.time.LocalDate
import kotlinx.coroutines.*

@Composable
fun ToolsSheet(settings: Settings, onInsert: (String, AngleMode?, String?) -> Unit, onHandwriting: () -> Unit, onPhoto: () -> Unit, onSurface: () -> Unit) {
    var tool by remember { mutableStateOf<String?>(null) }
    val catalog = remember { MoreTools.catalog(LocalDate.now()) }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(20.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(tool ?: "Tools", style = MaterialTheme.typography.headlineSmall)
            if (tool != null) TextButton(onClick = { tool = null }) { Text("All tools") }
        }
        if (tool == null) {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onPhoto, modifier = Modifier.weight(1f)) { Text("Photo calculator") }
                OutlinedButton(onClick = onHandwriting, modifier = Modifier.weight(1f)) { Text("Write & calculate") }
            }
            // 1.2: everyday tools lead; the study tools follow the existing ones.
            val added = catalog.map { it.name to it.summary }
            val tools = added.take(EVERYDAY_TOOLS) + listOf("Units" to "Length, area, temperature, Indian units and more",
                "Matrices" to "Exact arithmetic, determinants, inverses and rank",
                "Statistics" to "Summary statistics and linear regression",
                "Number bases" to "Convert integers between bases 2–36",
                "Algebra & calculus" to "Simplify, factor, differentiate and integrate",
                "Constants" to "Physical constants with units and uncertainty") + added.drop(EVERYDAY_TOOLS)
            tools.forEach { (name, description) ->
                OutlinedCard(onClick = { tool = name }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                    Column(Modifier.padding(16.dp)) { Text(name, style = MaterialTheme.typography.titleMedium); Text(description, fontSize = 12.sp) }
                }
            }
            OutlinedButton(onClick = onSurface, modifier = Modifier.fillMaxWidth()) { Text("3D surface · z = f(x, y)") }
            Text("Also type equations or unit expressions directly in the calculator, then press =. Use the Units tool to convert between units.",
                fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
        } else if (tool == "Constants") ConstantBrowser(settings) { onInsert(it, null, null) }
        else {
            val spec = catalog.firstOrNull { it.name == tool }
            if (spec != null) key(tool) { SpecToolForm(spec, settings, onInsert) }
            else key(tool) { ToolForm(tool!!, settings, onInsert) }
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun ToolForm(tool: String, settings: Settings, onInsert: (String, AngleMode?, String?) -> Unit) {
    val operations = when (tool) {
        "Matrices" -> listOf("Determinant", "Inverse", "Transpose", "Rank", "Add", "Subtract", "Multiply", "Scale", "Solve system", "Eigenvalues")
        "Statistics" -> listOf("Summary", "Linear regression")
        "Algebra & calculus" -> listOf("Simplify", "Expand", "Factor", "Differentiate", "Integrate", "Definite integral")
        else -> listOf("Convert")
    }
    var operation by remember { mutableStateOf(operations.first()) }
    var a by remember { mutableStateOf(when(tool) { "Matrices" -> "1, 2; 3, 4"; "Statistics" -> "1, 2, 3, 4, 5"; "Algebra & calculus" -> "x^2+2*x+1"; else -> "10" }) }
    var b by remember { mutableStateOf(when(tool) { "Units" -> "km"; "Matrices" -> "1, 0; 0, 1"; "Number bases" -> "10"; "Algebra & calculus" -> "x"; else -> "2, 4, 6, 8, 10" }) }
    var extra by remember { mutableStateOf(if (tool == "Units") "m" else "2") }
    var lower by remember { mutableStateOf("0") }
    var upper by remember { mutableStateOf("1") }
    var scalar by remember { mutableStateOf("2") }
    var answer by remember { mutableStateOf<ToolAnswer?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    if (operations.size > 1) ChoiceRow("Operation", operations, operation) { operation = it; answer = null }
    val help = when (tool) {
        "Matrices" -> "Separate cells with commas and rows with semicolons or new lines. Up to 6 × 6; fractions such as 1/3 are exact. " +
            "Solve system finds X with A·X = B: enter the constants as a column, one row per equation. Eigenvalues lists each distinct value once."
        "Statistics" -> "Separate values with commas, spaces or new lines. Up to 1,000 values. Use ungrouped numbers."
        "Number bases" -> "Whole numbers only. Bases 2–36; letters A–Z represent digits 10–35."
        "Algebra & calculus" -> "Bounded real algebra. Unsupported forms return an explanation. Calculus uses radians; conditions remain attached to the answer."
        else -> "Unit names are case-sensitive where their meanings differ. For example: km, m, acre, bigha, degC, degF."
    }
    Text(help, fontSize = 12.sp, modifier = Modifier.padding(vertical = 12.dp))
    if(tool == "Matrices") MatrixInput("Matrix A", a) { a = it; answer = null }
    else ToolField(when(tool) { "Statistics" -> if(operation == "Summary") "Data" else "X values"; "Algebra & calculus" -> "Expression"; else -> "Value" }, a) { a = it; answer = null }
    val showB = tool != "Matrices" && !(tool == "Statistics" && operation == "Summary") || operation in listOf("Add", "Subtract", "Multiply", "Solve system")
    if (showB) {
        if(tool == "Matrices") MatrixInput(if (operation == "Solve system") "Right-hand side B" else "Matrix B", b) { b = it; answer = null }
        else ToolField(when(tool) { "Statistics" -> "Y values"; "Units" -> "From unit"; "Number bases" -> "From base"; else -> "Variable" }, b) { b = it; answer = null }
    }
    if(tool == "Matrices" && operation == "Scale") ToolField("Multiply every cell by", scalar) { scalar = it; answer = null }
    if (tool in listOf("Units", "Number bases")) ToolField(if(tool == "Units") "To unit" else "To base", extra) { extra = it; answer = null }
    if (tool == "Algebra & calculus" && operation == "Definite integral") {
        ToolField("Lower bound", lower) { lower = it; answer = null }
        ToolField("Upper bound", upper) { upper = it; answer = null }
    }
    if (tool == "Units") {
        var browse by remember { mutableStateOf(false) }
        TextButton(onClick = { browse = !browse }) { Text(if(browse) "Hide unit names" else "Browse unit names") }
        if (browse) SelectionContainer { Text(Units.names().sorted().joinToString(" · "), fontSize = 12.sp) }
    }
    Button(enabled = !busy, onClick = {
        val current = listOf(operation, a, if(operation == "Scale") scalar else b, if(operation == "Definite integral") "$lower;$upper" else extra)
        busy = true
        scope.launch {
            try {
                val computed = withContext(Dispatchers.Default) { ToolCalculator.calculate(tool, current[0], current[1], current[2], current[3], settings) }
                val now = listOf(operation, a, if(operation == "Scale") scalar else b, if(operation == "Definite integral") "$lower;$upper" else extra)
                if(now == current) answer = computed
            } catch(e: CancellationException) { throw e }
              catch(e: Exception) {
                val now = listOf(operation, a, if(operation == "Scale") scalar else b, if(operation == "Definite integral") "$lower;$upper" else extra)
                if(now == current) answer = ToolAnswer(e.message ?: "This calculation could not be completed.", error = true)
            }
            finally { busy = false }
        }
    }, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) { Text(if (busy) "Calculating…" else operation) }
    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    answer?.let { result ->
        Card(Modifier.fillMaxWidth().padding(top = 16.dp)) {
            SelectionContainer { Text(result.text, modifier = Modifier.padding(16.dp), fontFamily = CalcFonts.JetBrainsMono,
                color = if(result.error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface) }
            result.insert?.let { insertion -> TextButton(onClick = { onInsert(insertion, result.insertAngle, result.insertNotice) }, modifier = Modifier.padding(horizontal = 8.dp)) { Text("Use in calculator") } }
        }
    }
}

/** Money, Dates, Everyday and Time are listed before the original tools; the rest of the catalogue after them. */
private const val EVERYDAY_TOOLS = 4

/** One form for every catalogued 1.2 tool: operation chips, the operation's own labelled fields, one answer card. */
@Composable
private fun SpecToolForm(spec: ToolSpec, settings: Settings, onInsert: (String, AngleMode?, String?) -> Unit) {
    var operation by remember { mutableStateOf(spec.operations.first()) }
    var answer by remember { mutableStateOf<ToolAnswer?>(null) }
    var busy by remember { mutableStateOf(false) }
    // Bumped by every edit and every operation change. A calculation's answer is shown only
    // if nothing has changed since it started — comparing field text is not enough, because
    // switching away and back recreates the fields while the old run still holds the old ones.
    var generation by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    if (spec.operations.size > 1) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            spec.operations.forEach { option ->
                FilterChip(option.name == operation.name, onClick = { operation = option; answer = null; generation++ }, label = { Text(option.name) })
            }
        }
    }
    key(operation.name) {
        val values = remember { mutableStateListOf(*operation.fields.map { it.default }.toTypedArray()) }
        if (operation.help.isNotEmpty()) Text(operation.help, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
        operation.fields.forEachIndexed { index, field ->
            ToolField(field.label, values[index]) { values[index] = it; answer = null; generation++ }
        }
        Button(enabled = !busy, onClick = {
            val snapshot = values.toList()
            val chosen = operation.name
            val started = generation
            busy = true
            scope.launch {
                try {
                    val computed = withContext(Dispatchers.Default) { MoreTools.calculate(spec.name, chosen, snapshot, settings, LocalDate.now()) }
                    if (generation == started) answer = computed
                } catch(e: CancellationException) { throw e }
                  catch(e: Exception) {
                    if (generation == started) answer = ToolAnswer(e.message ?: "This calculation could not be completed.", error = true)
                }
                finally { busy = false }
            }
        }, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) { Text(if (busy) "Calculating…" else operation.name) }
    }
    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    answer?.let { result ->
        Card(Modifier.fillMaxWidth().padding(top = 16.dp)) {
            SelectionContainer { Text(result.text, modifier = Modifier.padding(16.dp), fontFamily = CalcFonts.JetBrainsMono,
                color = if(result.error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface) }
            result.insert?.let { insertion -> TextButton(onClick = { onInsert(insertion, result.insertAngle, result.insertNotice) }, modifier = Modifier.padding(horizontal = 8.dp)) { Text("Use in calculator") } }
        }
    }
}

@Composable
private fun MatrixInput(label: String, value: String, onChange: (String) -> Unit) {
    var grid by remember { mutableStateOf(true) }
    val cells = remember(value) {
        value.split(Regex("[;\\n]")).map { row ->
            if(row.contains(',')) row.split(',').map { it.trim() } else row.trim().split(Regex("\\s+"))
        }.takeIf { rows -> rows.size in 1..6 && rows.first().size in 1..6 && rows.all { it.size == rows.first().size } }
    }
    fun write(rows: List<List<String>>) = onChange(rows.joinToString("; ") { it.joinToString(", ") })
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        TextButton(onClick = { grid = !grid }) { Text(if(grid) "Text input" else "Grid input") }
    }
    if(grid && cells != null) {
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            TextButton(enabled = cells.size > 1, onClick = { write(cells.dropLast(1)) }) { Text("− row") }
            TextButton(enabled = cells.size < 6, onClick = { write(cells + listOf(List(cells.first().size) { "0" })) }) { Text("+ row") }
            TextButton(enabled = cells.first().size > 1, onClick = { write(cells.map { it.dropLast(1) }) }) { Text("− col") }
            TextButton(enabled = cells.first().size < 6, onClick = { write(cells.map { it + "0" }) }) { Text("+ col") }
        }
        Column(Modifier.horizontalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            cells.forEachIndexed { r, row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEachIndexed { c, cell ->
                        OutlinedTextField(cell, { next ->
                            if(next.length <= 128 && next.none { it == ',' || it == ';' || it == '\n' }) {
                                write(cells.mapIndexed { ri, values -> values.mapIndexed { ci, old -> if(ri == r && ci == c) next else old } })
                            }
                        }, singleLine = true, keyboardOptions = MathKeyboard, label = { Text("${r + 1},${c + 1}", fontSize = 10.sp) }, modifier = Modifier.width(84.dp))
                    }
                }
            }
        }
    } else {
        if(grid) Text("Use a rectangular matrix with up to six rows and columns to switch to the grid.", fontSize = 12.sp)
        ToolField(label, value, onChange)
    }
}

@Composable
private fun ToolField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(value, { if(it.length <= 8192) onChange(it) }, keyboardOptions = MathKeyboard, label = { Text(label) }, maxLines = 6,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
}

@Composable
private fun ConstantBrowser(settings: Settings, onInsert: (String) -> Unit) {
    var search by remember { mutableStateOf("") }
    OutlinedTextField(search, { search = it.take(80) }, keyboardOptions = MathKeyboard, label = { Text("Search constants") }, modifier = Modifier.fillMaxWidth())
    Constants.allPhysical().filter { it.name.contains(search, true) || it.symbol.contains(search, true) }.forEach { c ->
        Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
            Text(c.name, style = MaterialTheme.typography.titleMedium)
            val value = Formatter.decimal(RealValue(c.value), settings)
            val decimalIsExact = (c.value as? com.vaibhav.calc.core.num.Rational)?.toExactBigDecimalOrNull()
                ?.compareTo(c.value.toBigDecimal(settings.displayMc)) == 0
            SelectionContainer { Text("${c.symbol} ${if(c.isExact && decimalIsExact) "=" else "≈"} $value ${c.unit}", modifier = Modifier.padding(top = 6.dp), fontSize = 13.sp) }
            Text(if(c.isExact) (if(decimalIsExact) "Defined exactly" else "Defined exactly; displayed value rounded") else "Standard uncertainty: ${Formatter.decimal(RealValue(c.uncertainty), settings)} ${c.unit}", fontSize = 12.sp)
            TextButton(onClick = { onInsert(c.symbol) }) { Text("Use ${c.symbol}") }
            HorizontalDivider()
        }
    }
}
