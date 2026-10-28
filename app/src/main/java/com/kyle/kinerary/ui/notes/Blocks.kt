package com.kyle.kinerary.ui.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyle.kinerary.notes.Block
import com.kyle.kinerary.notes.CheckItem
import com.kyle.kinerary.notes.newId
import com.kyle.kinerary.ui.LinkText
import com.kyle.kinerary.ui.contentOn

// Reading

@Composable
fun BlockView(block: Block, color: Color, onChange: (Block) -> Unit) {
    when (block) {
        is Block.Heading -> Text(
            block.text,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = color,
            modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
        )
        is Block.Text -> LinkText(block.text, style = MaterialTheme.typography.bodyLarge)
        is Block.Table -> TableView(block, color)
        is Block.Checklist -> ChecklistView(block, color, onChange)
    }
}

@Composable
private fun TableView(table: Block.Table, color: Color) {
    if (table.rows.isEmpty()) return
    val columns = table.columns
    // The table fits the screen width: columns share it by their longest line, within limits, so
    // short columns stay narrow and long text wraps downwards
    val weights = (0 until columns).map { c ->
        val longest = table.rows.maxOf { row ->
            row.getOrNull(c).orEmpty().replace(Regex("""\[([^\]]+)]\([^)]+\)"""), "$1").length
        }
        longest.coerceIn(6, 30).toFloat()
    }
    val border = MaterialTheme.colorScheme.outlineVariant
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, border, RoundedCornerShape(10.dp))
    ) {
        table.rows.forEachIndexed { r, row ->
            val header = r == 0
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .background(
                        when {
                            header -> color.copy(alpha = 0.22f)
                            r % 2 == 0 -> MaterialTheme.colorScheme.surfaceContainerLow
                            else -> Color.Transparent
                        }
                    )
            ) {
                for (c in 0 until columns) {
                    if (c > 0) Box(Modifier.width(1.dp).fillMaxHeight().background(border))
                    LinkText(
                        row.getOrNull(c).orEmpty(),
                        Modifier.weight(weights[c]).padding(horizontal = 8.dp, vertical = 8.dp),
                        style = if (header) {
                            MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                        } else {
                            MaterialTheme.typography.bodyMedium
                        },
                    )
                }
            }
            if (r < table.rows.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(border))
        }
    }
}

@Composable
private fun ChecklistView(list: Block.Checklist, color: Color, onChange: (Block) -> Unit) {
    val done = list.items.count { it.checked }
    Column {
        if (list.items.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                LinearProgressIndicator(
                    progress = { done / list.items.size.toFloat() },
                    color = color,
                    trackColor = color.copy(alpha = 0.2f),
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "$done / ${list.items.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 10.dp),
                )
            }
        } else {
            Text("Empty list. Tap edit to add items.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        list.items.forEach { item ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onChange(list.toggle(item.id)) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = item.checked,
                    onCheckedChange = { onChange(list.toggle(item.id)) },
                    colors = CheckboxDefaults.colors(checkedColor = color, checkmarkColor = contentOn(color)),
                )
                LinkText(
                    item.text,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        textDecoration = if (item.checked) TextDecoration.LineThrough else null,
                    ),
                    color = if (item.checked) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
                )
            }
        }
    }
}

private fun Block.Checklist.toggle(id: String) =
    copy(items = items.map { if (it.id == id) it.copy(checked = !it.checked) else it })

// Editing

@Composable
fun BlockEditor(block: Block, color: Color, onChange: (Block) -> Unit) {
    when (block) {
        is Block.Heading -> EditField(
            block.text,
            { onChange(block.copy(text = it)) },
            Modifier.fillMaxWidth(),
            label = "Heading",
            singleLine = true,
            textStyle = MaterialTheme.typography.titleMedium,
        )
        is Block.Text -> EditField(
            block.text,
            { onChange(block.copy(text = it)) },
            Modifier.fillMaxWidth(),
            label = "Text",
            hint = "Links work as plain URLs or [name](url)",
        )
        is Block.Table -> TableEditor(block, color, onChange)
        is Block.Checklist -> ChecklistEditor(block, color, onChange)
    }
}

private val CellWidth = 150.dp

@Composable
private fun TableEditor(table: Block.Table, color: Color, onChange: (Block) -> Unit) {
    val columns = table.columns.coerceAtLeast(1)
    val rows = table.rows.ifEmpty { listOf(List(columns) { "" }) }
    fun update(transform: (MutableList<MutableList<String>>) -> Unit) {
        val grid = rows.map { row -> MutableList(columns) { row.getOrNull(it).orEmpty() } }.toMutableList()
        transform(grid)
        onChange(table.copy(rows = grid))
    }
    Column {
        Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            Column {
                // Remove-column buttons over each column
                Row {
                    for (c in 0 until columns) {
                        Box(Modifier.width(CellWidth), contentAlignment = Alignment.Center) {
                            if (columns > 1) {
                                SmallIcon(Icons.Outlined.Close, "Remove column") { update { grid -> grid.forEach { it.removeAt(c) } } }
                            }
                        }
                    }
                }
                rows.forEachIndexed { r, row ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        for (c in 0 until columns) {
                            CellField(
                                row.getOrNull(c).orEmpty(),
                                header = r == 0,
                                color = color,
                                onValueChange = { text -> update { it[r][c] = text } },
                            )
                        }
                        if (rows.size > 1) {
                            SmallIcon(Icons.Outlined.Close, "Remove row") { update { it.removeAt(r) } }
                        } else {
                            Spacer(Modifier.size(32.dp))
                        }
                    }
                }
            }
        }
        Row {
            TextButton(onClick = { update { it.add(MutableList(columns) { "" }) } }) {
                Icon(Icons.Outlined.Add, null, Modifier.size(18.dp))
                Text("Row")
            }
            TextButton(onClick = { update { grid -> grid.forEach { it.add("") } } }) {
                Icon(Icons.Outlined.Add, null, Modifier.size(18.dp))
                Text("Column")
            }
        }
    }
}

@Composable
private fun CellField(value: String, header: Boolean, color: Color, onValueChange: (String) -> Unit) {
    val (field, change) = rememberSyncedField(value, onValueChange)
    BasicTextField(
        value = field,
        onValueChange = change,
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = if (header) FontWeight.SemiBold else FontWeight.Normal,
        ),
        cursorBrush = SolidColor(color),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier
            .width(CellWidth)
            .border(0.5.dp, MaterialTheme.colorScheme.outline)
            .background(if (header) color.copy(alpha = 0.22f) else Color.Transparent)
            .padding(horizontal = 8.dp, vertical = 10.dp),
    )
}

@Composable
private fun ChecklistEditor(list: Block.Checklist, color: Color, onChange: (Block) -> Unit) {
    Column {
        list.items.forEachIndexed { i, item -> key(item.id) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = item.checked,
                    onCheckedChange = { checked -> onChange(list.copy(items = list.items.map { if (it.id == item.id) it.copy(checked = checked) else it })) },
                    colors = CheckboxDefaults.colors(checkedColor = color, checkmarkColor = contentOn(color)),
                )
                EditField(
                    item.text,
                    { text -> onChange(list.copy(items = list.items.map { if (it.id == item.id) it.copy(text = text) else it })) },
                    Modifier.weight(1f),
                    singleLine = true,
                    dense = true,
                    onDone = {
                        // Enter adds the next item
                        val items = list.items.toMutableList()
                        items.add(i + 1, CheckItem(newId(), ""))
                        onChange(list.copy(items = items))
                    },
                )
                SmallIcon(Icons.Outlined.Close, "Remove item") {
                    onChange(list.copy(items = list.items.filter { it.id != item.id }))
                }
            }
        } }
        TextButton(onClick = { onChange(list.copy(items = list.items + CheckItem(newId(), ""))) }) {
            Icon(Icons.Outlined.Add, null, Modifier.size(18.dp))
            Text("Item")
        }
    }
}

/** A text field that keeps its own cursor while the saved text catches up */
@Composable
fun EditField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    hint: String? = null,
    singleLine: Boolean = false,
    dense: Boolean = false,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    onDone: (() -> Unit)? = null,
) {
    val (field, change) = rememberSyncedField(value, onValueChange)
    val keyboard = KeyboardOptions(
        capitalization = KeyboardCapitalization.Sentences,
        imeAction = if (onDone != null) ImeAction.Next else ImeAction.Default,
    )
    val actions = KeyboardActions(onNext = { onDone?.invoke() })
    if (dense) {
        BasicTextField(
            value = field,
            onValueChange = change,
            singleLine = singleLine,
            textStyle = textStyle.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = keyboard,
            keyboardActions = actions,
            modifier = modifier
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 10.dp, vertical = 10.dp),
        )
    } else {
        OutlinedTextField(
            value = field,
            onValueChange = change,
            modifier = modifier,
            label = label?.let { { Text(it) } },
            supportingText = hint?.let { { Text(it) } },
            singleLine = singleLine,
            textStyle = textStyle,
            keyboardOptions = keyboard,
            keyboardActions = actions,
        )
    }
}

/**
 * The field's own value (text plus cursor). The saved text arrives a frame or so after each key, so
 * it only replaces the field when it's something this field didn't send, e.g. an edit from sync.
 */
@Composable
private fun rememberSyncedField(value: String, onValueChange: (String) -> Unit): Pair<TextFieldValue, (TextFieldValue) -> Unit> {
    var field by remember { mutableStateOf(TextFieldValue(value)) }
    var sent by remember { mutableStateOf(value) }
    LaunchedEffect(value) {
        if (value != sent) {
            sent = value
            field = TextFieldValue(value, TextRange(value.length))
        }
    }
    return field to { it: TextFieldValue ->
        field = it
        if (it.text != sent) {
            sent = it.text
            onValueChange(it.text)
        }
    }
}

@Composable
fun SmallIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    size: Dp = 32.dp,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(size)) {
        Icon(icon, description, Modifier.size(18.dp))
    }
}

/** A fresh block of each kind, for the add buttons */
fun newBlock(kind: String): Block = when (kind) {
    "heading" -> Block.Heading(newId(), "")
    "table" -> Block.Table(newId(), listOf(listOf("", ""), listOf("", "")))
    "checklist" -> Block.Checklist(newId(), listOf(CheckItem(newId(), "")))
    else -> Block.Text(newId(), "")
}
