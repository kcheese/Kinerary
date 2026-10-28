package com.kyle.kinerary.notes

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

/** Which tab a page belongs to */
object Section {
    const val INDEX = "index"
    const val PREP = "prep"
}

/**
 * One nested tab of notes. Pages are synced whole, newest "modified" winning; a deleted page stays as
 * a tombstone so the deletion reaches other devices.
 */
@Serializable
data class Page(
    val id: String,
    val section: String,
    val title: String,
    val order: Int,
    /** 0xAARRGGBB; pages named after a location use that location's colour instead */
    val color: Long? = null,
    val blocks: List<Block> = emptyList(),
    val modified: Long,
    val deleted: Boolean = false,
)

@Serializable
sealed interface Block {
    val id: String

    @Serializable
    @SerialName("heading")
    data class Heading(override val id: String, val text: String) : Block

    /** Plain text; URLs and [label](url) become links */
    @Serializable
    @SerialName("text")
    data class Text(override val id: String, val text: String) : Block

    /** First row is the header */
    @Serializable
    @SerialName("table")
    data class Table(override val id: String, val rows: List<List<String>>) : Block {
        val columns get() = rows.maxOfOrNull { it.size } ?: 0
    }

    @Serializable
    @SerialName("checklist")
    data class Checklist(override val id: String, val items: List<CheckItem>) : Block
}

@Serializable
data class CheckItem(val id: String, val text: String, val checked: Boolean = false)

/** The settings shared between devices along with the notes */
@Serializable
data class TripSettings(
    val sheetUrl: String = "",
    /** Location name → 0xAARRGGBB chosen in the app (otherwise the sheet's colour is used) */
    val colors: Map<String, Long> = emptyMap(),
    val modified: Long = 0,
)

fun newId(): String = UUID.randomUUID().toString()
