package com.vaibhav.calc.android

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaibhav.calc.android.theme.CalcType
import com.vaibhav.calc.android.theme.LocalCalcPalette
import com.vaibhav.calc.core.entry.*
import com.vaibhav.calc.core.entry.Row as MathRow

/** Typesets the editor tree directly; its grammar string remains the accessible text. */
@Composable
fun MathEntry(editor: EditorState, onCursor: (Cursor) -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp)
        .semantics { contentDescription = editor.toInput().ifEmpty { "Empty expression" } }) {
        if (editor.toInput().length > 350) {
            Text(editor.toInput(), style = CalcType.Entry.copy(fontSize = 16.sp),
                modifier = Modifier.clickable { onCursor(Cursor(offset = editor.root.size)) })
        } else MathRowView(editor.root, emptyList(), editor, onCursor, 19f, 0)
    }
}

@Composable
private fun MathRowView(row: MathRow, path: List<PathStep>, editor: EditorState,
    onCursor: (Cursor) -> Unit, size: Float, depth: Int) {
    val colors = LocalCalcPalette.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = (size + 8).dp)) {
        fun here(i: Int) = editor.cursor.path == path && editor.cursor.offset == i
        if (row.isEmpty) {
            Box(Modifier.width(14.dp).height((size + 5).dp)
                .background(colors.blueSoft, RoundedCornerShape(3.dp)).clickable { onCursor(Cursor(path, 0)) },
                contentAlignment = Alignment.Center) {
                if (here(0)) Caret(size, editor.cursor) else Text("□", style = CalcType.Entry.copy(fontSize = size.sp), color = colors.ink3)
            }
        } else {
            row.nodes.forEachIndexed { index, node ->
                if (here(index)) Caret(size, editor.cursor)
                val sel = editor.selection
                val selected = sel != null && sel.path == path && index in sel.from until sel.to
                Box(Modifier.background(if (selected) colors.amberSoft else androidx.compose.ui.graphics.Color.Transparent,
                    RoundedCornerShape(4.dp))) {
                    NodeView(node, index, path, editor, onCursor, size, depth)
                }
            }
            if (here(row.size)) Caret(size, editor.cursor)
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun Caret(size: Float, cursor: Cursor) {
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(cursor) { withFrameNanos { }; requester.bringIntoView() }
    Box(Modifier.bringIntoViewRequester(requester).padding(horizontal = 1.dp).width(2.dp).height((size + 5).dp)
        .background(LocalCalcPalette.current.blue))
}

@Composable
private fun NodeView(node: Node, index: Int, path: List<PathStep>, editor: EditorState,
    onCursor: (Cursor) -> Unit, size: Float, depth: Int) {
    val colors = LocalCalcPalette.current
    val style = CalcType.Entry.copy(fontSize = size.sp)
    val small = (size * .78f).coerceAtLeast(10f)
    @Composable fun child(field: Field, childSize: Float = size) {
        MathRowView(node.field(field), path + PathStep(index, field), editor, onCursor, childSize, depth + 1)
    }
    @Composable fun token(text: String) {
        Text(text, style = style, color = colors.ink2,
            modifier = Modifier.clickable { onCursor(Cursor(path, index + 1)) })
    }
    if (depth > 10) { token(node.toInput()); return }
    when (node) {
        is Atom -> token(when (node.char) { '*' -> "×"; '-' -> "−"; else -> node.char.toString() })
        Ans -> token("Ans")
        is Ref -> Text("#${node.n}", style = style.copy(fontSize = small.sp), color = colors.blue2,
            modifier = Modifier.background(colors.blueSoft, RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 2.dp)
                .clickable { onCursor(Cursor(path, index + 1)) })
        is Fraction -> Column(Modifier.width(IntrinsicSize.Min).padding(horizontal = 3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            child(Field.NUMERATOR, small)
            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.ink2))
            child(Field.DENOMINATOR, small)
        }
        is Power -> Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.padding(top = 8.dp)) { child(Field.BASE) }
            child(Field.EXPONENT, small)
        }
        is Root -> Row(verticalAlignment = Alignment.CenterVertically) {
            node.index?.let { child(Field.INDEX, small) }
            token("√")
            Column(Modifier.width(IntrinsicSize.Min)) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(colors.ink2))
                child(Field.RADICAND)
            }
        }
        is Paren -> Row(verticalAlignment = Alignment.CenterVertically) {
            token("("); child(Field.BODY); token(")")
        }
        is Func -> Row(verticalAlignment = Alignment.CenterVertically) {
            token(node.displayName + "("); child(Field.ARG); token(")")
        }
    }
}
