package com.kyle.kinerary

import com.kyle.kinerary.data.TripRepository
import com.kyle.kinerary.notes.Block
import com.kyle.kinerary.notes.Section
import com.kyle.kinerary.notes.importPages
import com.kyle.kinerary.sheet.Workbook
import com.kyle.kinerary.sheet.itinerarySheet
import com.kyle.kinerary.sheet.parseItinerary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

/**
 * Reads a real export of the trip sheet (kept outside the repo, as it's personal). Set
 * KINERARY_TEST_XLSX to point elsewhere.
 */
class SheetTest {
    private val file = File(System.getenv("KINERARY_TEST_XLSX") ?: "C:/Users/kleccese/dev/tools/kinerary-test/sheet.xlsx")

    private fun workbook(): Workbook {
        assumeTrue("No test sheet at $file", file.exists())
        return Workbook.read(file.readBytes())
    }

    @Test
    fun itinerary() {
        val trip = parseItinerary(workbook().itinerarySheet()!!)
        println("Title: ${trip.title}")
        trip.locations.forEach { println("Location ${it.name} ${it.sheetColor?.toString(16)}") }
        trip.days.forEach { day ->
            println("${day.date} ${day.label} @${day.location}")
            day.items.forEach { println("    ${it.text} ${it.tags} ${if (it.seeIndex) "[index]" else ""}") }
        }
        assertEquals("Japan 2027", trip.title)
        assertEquals(15, trip.days.size)
        assertEquals(LocalDate.of(2027, 2, 20), trip.start)
        assertEquals(listOf("Tokyo", "Fuji", "Osaka/Kyoto", "Takayama", "Narita"), trip.locations.map { it.name })
        assertEquals("Fuji", trip.days[5].location)
        assertTrue(trip.days[1].items.any { it.seeIndex && it.tags == listOf("Book") })
    }

    @Test
    fun notes() {
        val pages = importPages(workbook(), modified = 1)
        val json = TripRepository.json
        pages.forEach { page ->
            println("== ${page.section} ${page.title} ${page.color?.toString(16)}")
            page.blocks.forEach { block ->
                when (block) {
                    is Block.Heading -> println("  # ${block.text}")
                    is Block.Text -> println("  ${block.text.replace("\n", " / ")}")
                    is Block.Table -> block.rows.forEach { println("  | ${it.joinToString(" | ")}") }
                    is Block.Checklist -> block.items.forEach { println("  [${if (it.checked) "x" else " "}] ${it.text}") }
                }
            }
        }
        assertEquals(listOf("Tokyo", "Fuji", "Osaka/Kyoto", "Takayama", "Narita"), pages.filter { it.section == Section.INDEX }.map { it.title })
        assertEquals(listOf("Kyle", "Vince"), pages.filter { it.section == Section.PREP }.map { it.title })
        val kyle = pages.first { it.title == "Kyle" }
        assertTrue(kyle.blocks.any { it is Block.Checklist && it.items.any { item -> item.text == "Passport" } })
        // Survives a round trip through the notes file format
        val encoded = json.encodeToString(com.kyle.kinerary.notes.Page.serializer(), kyle)
        assertEquals(kyle, json.decodeFromString(com.kyle.kinerary.notes.Page.serializer(), encoded))
    }

    @Test
    fun exportUrls() {
        assertEquals(
            "https://docs.google.com/spreadsheets/d/1AbCdEfGhIjKlMnOpQrStUvWxYz0123456789abcd/export?format=xlsx",
            TripRepository.exportUrl("https://docs.google.com/spreadsheets/d/1AbCdEfGhIjKlMnOpQrStUvWxYz0123456789abcd/edit?usp=sharing"),
        )
        assertEquals(null, TripRepository.exportUrl("hello"))
    }
}
