package com.kyle.kinerary.sheet

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.roundToLong

/**
 * Just enough of an .xlsx reader for a Google Sheets export: cell text, background colours, links
 * and tick boxes. Element names are matched without their namespace prefix, so the same code runs
 * on Android's DOM and the JVM's (for tests).
 */
class Workbook(val sheets: List<Sheet>) {
    fun sheet(name: String) = sheets.firstOrNull { it.name.equals(name, ignoreCase = true) }

    companion object {
        fun read(bytes: ByteArray): Workbook {
            val parts = HashMap<String, ByteArray>()
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (!entry.isDirectory) parts[entry.name.removePrefix("/")] = zip.readBytes()
                }
            }
            fun xml(path: String) = parts[path]?.let(::parseXml)

            val workbook = xml("xl/workbook.xml") ?: error("Not a spreadsheet")
            val rels = xml("xl/_rels/workbook.xml.rels")?.children("Relationship")
                ?.associate { it.getAttribute("Id") to it.getAttribute("Target") }
                .orEmpty()
            val strings = xml("xl/sharedStrings.xml")?.children("si")?.map(::richText).orEmpty()
            val styles = xml("xl/styles.xml")?.let(::Styles) ?: Styles(null)

            val sheets = workbook.descendants("sheet").mapNotNull { sheet ->
                val target = rels[sheet.attr("id")] ?: return@mapNotNull null
                val path = target.removePrefix("/").let { if (it.startsWith("xl/")) it else "xl/$it" }
                val root = xml(path) ?: return@mapNotNull null
                val relsPath = path.replaceAfterLast('/', "_rels/" + path.substringAfterLast('/') + ".rels")
                val sheetRels = xml(relsPath)?.children("Relationship")
                    ?.associate { it.getAttribute("Id") to it.getAttribute("Target") }
                    .orEmpty()
                readSheet(sheet.getAttribute("name"), root, strings, styles, sheetRels)
            }
            return Workbook(sheets)
        }

        private fun readSheet(
            name: String,
            root: Element,
            strings: List<String>,
            styles: Styles,
            rels: Map<String, String>,
        ): Sheet {
            val links = HashMap<String, String>()
            for (link in root.descendants("hyperlink")) {
                val url = rels[link.attr("id")] ?: continue
                for (ref in expandRange(link.getAttribute("ref"))) links[ref] = url
            }
            val cells = root.descendants("c").mapNotNull { c ->
                val ref = c.getAttribute("r")
                val (row, col) = parseRef(ref) ?: return@mapNotNull null
                val raw = c.children("v").firstOrNull()?.textContent.orEmpty()
                val style = c.getAttribute("s").toIntOrNull() ?: 0
                val type = c.getAttribute("t")
                var bool: Boolean? = null
                val text = when (type) {
                    "s" -> raw.toIntOrNull()?.let(strings::getOrNull).orEmpty()
                    "inlineStr" -> c.children("is").firstOrNull()?.let(::richText).orEmpty()
                    "b" -> { bool = raw == "1"; "" }
                    "str", "e" -> raw
                    else -> raw.toDoubleOrNull()?.let { styles.format(style, it) } ?: raw
                }
                Cell(
                    row = row,
                    col = col,
                    text = text,
                    number = if (type.isEmpty() || type == "n") raw.toDoubleOrNull() else null,
                    bool = bool,
                    fill = styles.fill(style),
                    link = links[ref],
                )
            }
            return Sheet(name, cells)
        }
    }
}

class Sheet(val name: String, cells: List<Cell>) {
    private val byPos = cells.associateBy { it.row to it.col }
    val rowCount = (cells.maxOfOrNull { it.row } ?: -1) + 1
    val colCount = (cells.maxOfOrNull { it.col } ?: -1) + 1

    /** Rows and columns count from 0 */
    operator fun get(row: Int, col: Int): Cell? = byPos[row to col]
    val cells: Collection<Cell> get() = byPos.values
}

data class Cell(
    val row: Int,
    val col: Int,
    /** As shown in the sheet (dates and times formatted) */
    val text: String,
    val number: Double? = null,
    /** Set for tick boxes */
    val bool: Boolean? = null,
    /** Background colour as 0xAARRGGBB, if the cell has one */
    val fill: Long? = null,
    val link: String? = null,
) {
    val isBlank get() = text.isBlank() && bool == null
}

private class Styles(root: Element?) {
    private val fills: List<Long?>
    private val cellFills: List<Int>
    private val cellFormats: List<Int>
    private val formats: Map<Int, String>

    init {
        fills = root?.children("fills")?.firstOrNull()?.children("fill")?.map { fill ->
            val pattern = fill.children("patternFill").firstOrNull()
            pattern?.takeIf { it.getAttribute("patternType") != "none" }
                ?.children("fgColor")?.firstOrNull()
                ?.getAttribute("rgb")?.takeIf { it.length == 8 }
                ?.toLongOrNull(16)
        }.orEmpty()
        val xfs = root?.children("cellXfs")?.firstOrNull()?.children("xf").orEmpty()
        cellFills = xfs.map { it.getAttribute("fillId").toIntOrNull() ?: 0 }
        cellFormats = xfs.map { it.getAttribute("numFmtId").toIntOrNull() ?: 0 }
        formats = root?.children("numFmts")?.firstOrNull()?.children("numFmt")
            ?.associate { (it.getAttribute("numFmtId").toIntOrNull() ?: -1) to it.getAttribute("formatCode") }
            .orEmpty()
    }

    /** White counts as no colour: it's what an unfilled cell looks like */
    fun fill(style: Int): Long? = cellFills.getOrNull(style)?.let(fills::getOrNull)
        ?.takeIf { it and 0xFFFFFF != 0xFFFFFFL }

    fun format(style: Int, value: Double): String {
        val id = cellFormats.getOrNull(style) ?: 0
        val code = (formats[id] ?: BUILT_IN[id].orEmpty()).replace(Regex("\"[^\"]*\"|\\[[^]]*]"), "")
        val lower = code.lowercase(Locale.ROOT)
        val hasDate = 'd' in lower || 'y' in lower || (lower.contains('m') && !lower.contains('h') && !lower.contains('s'))
        val hasTime = 'h' in lower || 's' in lower
        return when {
            hasDate && code.isNotBlank() -> {
                val date = excelDate(value)
                val pattern = if (lower.contains("dddd")) "EEEE, MMMM d, yyyy" else "EEE d MMM yyyy"
                date.format(DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH))
            }
            hasTime -> excelTime(value).format(
                DateTimeFormatter.ofPattern(if (lower.contains("am/pm")) "h:mm a" else "HH:mm", Locale.ENGLISH)
            )
            value == Math.floor(value) && kotlin.math.abs(value) < 1e15 -> value.toLong().toString()
            else -> value.toString()
        }
    }

    companion object {
        private val BUILT_IN = mapOf(
            14 to "m/d/yyyy", 15 to "d-mmm-yy", 16 to "d-mmm", 17 to "mmm-yy",
            18 to "h:mm AM/PM", 19 to "h:mm:ss AM/PM", 20 to "h:mm", 21 to "h:mm:ss", 22 to "m/d/yyyy h:mm",
            45 to "mm:ss", 46 to "[h]:mm:ss", 47 to "mm:ss.0",
        )
    }
}

fun excelDate(serial: Double): LocalDate = LocalDate.of(1899, 12, 30).plusDays(Math.floor(serial).toLong())

fun excelTime(serial: Double): LocalTime {
    val seconds = ((serial - Math.floor(serial)) * 86400).roundToLong() % 86400
    return LocalTime.ofSecondOfDay(seconds)
}

private fun parseXml(bytes: ByteArray): Element {
    val factory = DocumentBuilderFactory.newInstance()
    factory.isNamespaceAware = false
    return factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes)).documentElement
}

private val Node.localTag get() = nodeName.substringAfter(':')

private fun Element.children(tag: String): List<Element> {
    val out = ArrayList<Element>()
    val nodes = childNodes
    for (i in 0 until nodes.length) {
        val node = nodes.item(i)
        if (node is Element && node.localTag == tag) out += node
    }
    return out
}

private fun Element.descendants(tag: String): List<Element> {
    val out = ArrayList<Element>()
    fun walk(element: Element) {
        val nodes = element.childNodes
        for (i in 0 until nodes.length) {
            val node = nodes.item(i)
            if (node is Element) {
                if (node.localTag == tag) out += node
                walk(node)
            }
        }
    }
    walk(this)
    return out
}

/** An attribute by its name without the prefix, e.g. "id" for r:id */
private fun Element.attr(name: String): String? {
    val attrs = attributes
    for (i in 0 until attrs.length) {
        val attr = attrs.item(i)
        if (attr.nodeName.substringAfter(':') == name && attr.nodeName.contains(':')) return attr.nodeValue
    }
    return getAttribute(name).ifEmpty { null }
}

/** Text of a shared string or inline string, leaving out phonetic guides */
private fun richText(si: Element): String = buildString {
    fun walk(element: Element) {
        val nodes = element.childNodes
        for (i in 0 until nodes.length) {
            val node = nodes.item(i) as? Element ?: continue
            when (node.localTag) {
                "t" -> append(node.textContent)
                "rPh" -> Unit
                else -> walk(node)
            }
        }
    }
    walk(si)
}

/** "B12" → (11, 1) */
fun parseRef(ref: String): Pair<Int, Int>? {
    val match = Regex("^([A-Z]+)(\\d+)$").find(ref.uppercase(Locale.ROOT)) ?: return null
    val col = match.groupValues[1].fold(0) { acc, ch -> acc * 26 + (ch - 'A' + 1) } - 1
    return match.groupValues[2].toInt() - 1 to col
}

private fun expandRange(range: String): List<String> {
    val parts = range.split(':')
    if (parts.size != 2) return listOf(range)
    val (r1, c1) = parseRef(parts[0]) ?: return listOf(range)
    val (r2, c2) = parseRef(parts[1]) ?: return listOf(range)
    if ((r2 - r1 + 1) * (c2 - c1 + 1) > 10_000) return listOf(parts[0])
    return (r1..r2).flatMap { r -> (c1..c2).map { c -> colName(c) + (r + 1) } }
}

private fun colName(col: Int): String {
    var n = col + 1
    val sb = StringBuilder()
    while (n > 0) {
        val rem = (n - 1) % 26
        sb.insert(0, 'A' + rem)
        n = (n - 1) / 26
    }
    return sb.toString()
}
