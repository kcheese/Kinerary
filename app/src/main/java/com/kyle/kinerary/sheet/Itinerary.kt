package com.kyle.kinerary.sheet

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

data class Itinerary(
    val title: String,
    val days: List<Day>,
    /** Locations in trip order, with the colour each has in the sheet */
    val locations: List<Location>,
) {
    val start: LocalDate? get() = days.firstOrNull()?.date
    val end: LocalDate? get() = days.lastOrNull()?.date
}

data class Location(val name: String, val sheetColor: Long?)

data class Day(
    val date: LocalDate,
    /** "Day 3", as written in the sheet */
    val label: String,
    val location: String?,
    val items: List<Item>,
)

data class Item(
    val text: String,
    /** Other things written beside it, e.g. "Book" */
    val tags: List<String>,
    /** Ended in "- See Index" */
    val seeIndex: Boolean,
    val link: String? = null,
)

private val SEE_INDEX = Regex("""\s*[-–—]?\s*see\s+index\s*$""", RegexOption.IGNORE_CASE)
private val TEXT_DATE = listOf("EEEE, MMMM d, yyyy", "EEE, MMM d, yyyy", "d MMMM yyyy", "MMMM d, yyyy", "yyyy-MM-dd")
    .map { DateTimeFormatter.ofPattern(it, Locale.ENGLISH) }

/**
 * Reads the trip from a sheet laid out as rows down column A: a location row (coloured), then for each
 * day a date row ("Day N" beside it) followed by one row per activity. Anything in the other columns of
 * an activity row becomes a tag.
 */
fun parseItinerary(sheet: Sheet): Itinerary {
    val rows = (0 until sheet.rowCount).map { r -> (0 until sheet.colCount).mapNotNull { c -> sheet[r, c] }.filterNot { it.isBlank } }
        .filter { it.isNotEmpty() }

    fun dateOf(row: List<Cell>): LocalDate? {
        val first = row.firstOrNull { it.col == 0 } ?: return null
        first.number?.let { if (it in 20_000.0..80_000.0) return excelDate(it) }
        val text = first.text.trim()
        for (format in TEXT_DATE) {
            try {
                return LocalDate.parse(text, format)
            } catch (_: DateTimeParseException) {
            }
        }
        return null
    }

    val dates = rows.map(::dateOf)
    var title = ""
    var location: String? = null
    val locations = LinkedHashMap<String, Location>()
    val days = ArrayList<Day>()
    var current: Day? = null

    rows.forEachIndexed { i, row ->
        val date = dates[i]
        val first = row.first()
        if (date != null) {
            current?.let(days::add)
            val label = row.drop(1).joinToString(" ") { it.text.trim() }
            current = Day(date, label, location, emptyList())
            return@forEachIndexed
        }
        // A coloured line just before a date starts a new location
        val nextIsDate = dates.getOrNull(i + 1) != null
        if (row.size == 1 && first.fill != null && nextIsDate) {
            location = first.text.trim()
            locations.getOrPut(location!!) { Location(location!!, first.fill) }
            return@forEachIndexed
        }
        val day = current
        if (day == null) {
            if (title.isEmpty()) title = row.joinToString(" ") { it.text.trim() }
            return@forEachIndexed
        }
        val main = row.firstOrNull { it.col == 0 } ?: row.first()
        val seeIndex = SEE_INDEX.containsMatchIn(main.text)
        val item = Item(
            text = main.text.replace(SEE_INDEX, "").trim(),
            tags = row.filter { it !== main }.map { it.text.trim() }.filter { it.isNotEmpty() },
            seeIndex = seeIndex,
            link = main.link,
        )
        current = day.copy(items = day.items + item)
    }
    current?.let(days::add)
    return Itinerary(title, days.sortedBy { it.date }, locations.values.toList())
}

/** The sheet the itinerary is on: one with "itinerary" in its name, else the first */
fun Workbook.itinerarySheet(): Sheet? =
    sheets.firstOrNull { it.name.contains("itinerary", ignoreCase = true) } ?: sheets.firstOrNull()

/** Letters and digits only, lower case: "Osaka/Kyoto" and "OsakaKyoto Index" both start "osakakyoto" */
fun placeKey(name: String) = name.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }
