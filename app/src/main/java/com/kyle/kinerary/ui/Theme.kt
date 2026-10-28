package com.kyle.kinerary.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.kyle.kinerary.notes.TripSettings
import com.kyle.kinerary.sheet.Itinerary
import com.kyle.kinerary.sheet.placeKey

val KRed = Color(0xFFFF2B2B)

private val colors = darkColorScheme(
    primary = KRed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF5C1515),
    onPrimaryContainer = Color(0xFFFFDAD6),
    secondary = Color(0xFFE7BDB8),
    secondaryContainer = Color(0xFF3A2A28),
    onSecondaryContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0E0F11),
    onBackground = Color(0xFFE6E1E3),
    surface = Color(0xFF0E0F11),
    onSurface = Color(0xFFE6E1E3),
    surfaceVariant = Color(0xFF26272B),
    onSurfaceVariant = Color(0xFFC4C6CC),
    surfaceContainerLowest = Color(0xFF0A0B0C),
    surfaceContainerLow = Color(0xFF16171A),
    surfaceContainer = Color(0xFF1B1C20),
    surfaceContainerHigh = Color(0xFF232428),
    surfaceContainerHighest = Color(0xFF2D2E33),
    outline = Color(0xFF55575E),
    outlineVariant = Color(0xFF34363B),
)

@Composable
fun KineraryTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, content = content)
}

/** Choices offered for a location's or page's colour; light enough to read as tints on black */
val Palette = listOf(
    0xFFE47F7F, 0xFFF4A261, 0xFFFFE599, 0xFFD9E46B, 0xFFB6D7A8, 0xFF6FCF97, 0xFF76D7C4, 0xFF9FC5E8,
    0xFF7FA7F0, 0xFFB4A7D6, 0xFFD58CE0, 0xFFF29BC1, 0xFFC9A27E, 0xFFB0B4BC, 0xFFFF5B5B, 0xFF4DD0E1,
).map { Color(it) }

/** A location's colour: chosen in the app, else from the sheet, else one from the palette */
fun locationColor(name: String?, settings: TripSettings, itinerary: Itinerary?): Color? {
    name ?: return null
    settings.colors[name]?.let { return Color(it) }
    val locations = itinerary?.locations.orEmpty()
    val index = locations.indexOfFirst { it.name == name }
    if (index < 0) return null
    locations[index].sheetColor?.let { return Color(it) }
    return Palette[index % Palette.size]
}

/** The location a page title refers to, if any ("OsakaKyoto" finds "Osaka/Kyoto") */
fun locationNamed(title: String, itinerary: Itinerary?): String? =
    itinerary?.locations?.firstOrNull { placeKey(it.name) == placeKey(title) }?.name

/** Text that reads on top of [background] */
fun contentOn(background: Color) = if (background.luminance() > 0.4f) Color(0xFF15161A) else Color.White
