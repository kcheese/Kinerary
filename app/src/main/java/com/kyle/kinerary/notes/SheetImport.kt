package com.kyle.kinerary.notes

import com.kyle.kinerary.sheet.Cell
import com.kyle.kinerary.sheet.Sheet
import com.kyle.kinerary.sheet.Workbook
import com.kyle.kinerary.sheet.itinerarySheet
import com.kyle.kinerary.sheet.placeKey

/**
 * Turns the workbook's other tabs into starting pages: tabs named "… Index" go to the Index, the rest
 * (packing lists) to Prep. Coloured cells are read as headings; a run of coloured cells side by side is
 * a table's header row, with the rows under it as the table; a heading followed by text with tick
 * boxes beside it becomes a checklist.
 *
 * Ids are made from the tab name, so importing on two devices gives the same pages rather than copies.
 */
fun importPages(workbook: Workbook, modified: Long): List<Page> {
    val itinerary = workbook.itinerarySheet()
    return workbook.sheets.filter { it !== itinerary }.mapIndexed { index, sheet ->
        val isIndex = sheet.name.trim().endsWith("index", ignoreCase = true)
        val titleCell = sheet[0, 0]
        val title = if (isIndex) {
            titleCell?.text?.trim()?.takeIf { it.isNotEmpty() } ?: sheet.name.trim().removeSuffix("Index").trim()
        } else {
            sheet.name.trim()
        }
        val key = placeKey(sheet.name)
        Page(
            id = "sheet-$key",
            section = if (isIndex) Section.INDEX else Section.PREP,
            title = title,
            order = index,
            color = titleCell?.fill,
            blocks = blocksOf(sheet, skip = titleCell).mapIndexed { i, block -> block.withId("sheet-$key-$i") },
            modified = modified,
        )
    }
}

private fun blocksOf(sheet: Sheet, skip: Cell?): List<Block> {
    val used = HashSet<Pair<Int, Int>>()
    skip?.let { used += it.row to it.col }
    fun filled(r: Int, c: Int) = sheet[r, c]?.let { it.fill != null && (r to c) !in used } == true
    fun blank(r: Int, c: Int) = sheet[r, c]?.isBlank ?: true

    val found = ArrayList<Triple<Int, Int, Block>>() // column, row, block
    for (r in 0 until sheet.rowCount) {
        var c = 0
        while (c < sheet.colCount) {
            if (!filled(r, c) || sheet[r, c]!!.isBlank) { c++; continue }
            // A run of coloured cells along the row
            var end = c
            while (end + 1 < sheet.colCount && filled(r, end + 1) && !blank(r, end + 1)) end++
            for (x in c..end) used += r to x
            if (end > c) {
                found += Triple(c, r, table(sheet, r, c..end, used))
            } else if (filled(r + 1, c)) {
                found += Triple(c, r, Block.Heading("", sheet[r, c]!!.text.trim()))
            } else {
                found += Triple(c, r, Block.Heading("", sheet[r, c]!!.text.trim()))
                body(sheet, r, c, used)?.let { found += Triple(c, r, it) }
            }
            c = end + 1
        }
    }
    // Each heading starts a section holding what's under it in its column. Sections go in reading
    // order (top to bottom, then left to right), so lists laid out side by side end up one after the other.
    val sections = ArrayList<MutableList<Triple<Int, Int, Block>>>()
    for (block in found.sortedWith(compareBy({ it.first }, { it.second }))) {
        val last = sections.lastOrNull()
        if (block.third is Block.Heading || last == null || last.first().first != block.first) {
            sections += mutableListOf(block)
        } else {
            last += block
        }
    }
    return sections.sortedWith(compareBy({ it.first().second }, { it.first().first })).flatMap { s -> s.map { it.third } }
}

/** Rows under a header row, until a blank row or another coloured cell */
private fun table(sheet: Sheet, headerRow: Int, cols: IntRange, used: MutableSet<Pair<Int, Int>>): Block.Table {
    val rows = mutableListOf(cols.map { sheet[headerRow, it]?.text?.trim().orEmpty() })
    var r = headerRow + 1
    while (r < sheet.rowCount) {
        val cells = cols.map { sheet[r, it] }
        if (cells.all { it == null || it.isBlank } || cells.any { it?.fill != null }) break
        rows += cells.map { it?.let(::cellText).orEmpty() }
        cols.forEach { used += r to it }
        r++
    }
    return Block.Table("", rows)
}

/** What sits under a lone heading: a checklist if there are tick boxes beside it, else text */
private fun body(sheet: Sheet, headerRow: Int, col: Int, used: MutableSet<Pair<Int, Int>>): Block? {
    val ticks = (headerRow + 1 until sheet.rowCount).any { sheet[it, col + 1]?.bool != null }
    val items = ArrayList<CheckItem>()
    val lines = ArrayList<String>()
    var r = headerRow + 1
    while (r < sheet.rowCount) {
        val cell = sheet[r, col]
        val tick = if (ticks) sheet[r, col + 1] else null
        if ((cell == null || cell.isBlank) && tick?.bool == null) break
        if (cell?.fill != null) break
        used += r to col
        if (ticks) used += r to col + 1
        val text = cell?.let(::cellText).orEmpty()
        if (ticks) {
            if (text.isNotEmpty()) items += CheckItem(newId(), text, tick?.bool == true)
        } else {
            lines += text
        }
        r++
    }
    return when {
        ticks -> Block.Checklist("", items)
        lines.isNotEmpty() -> Block.Text("", lines.joinToString("\n"))
        else -> null
    }
}

/** Cell text, with a link that isn't already written out kept as [text](url) */
private fun cellText(cell: Cell): String {
    val text = cell.text.trim()
    val link = cell.link ?: return text
    return when {
        text.isEmpty() -> link
        text.contains("http://") || text.contains("https://") -> text
        else -> "[$text]($link)"
    }
}

private fun Block.withId(id: String): Block = when (this) {
    is Block.Heading -> copy(id = id)
    is Block.Text -> copy(id = id)
    is Block.Table -> copy(id = id)
    is Block.Checklist -> copy(id = id, items = items.mapIndexed { i, item -> item.copy(id = "$id-$i") })
}
