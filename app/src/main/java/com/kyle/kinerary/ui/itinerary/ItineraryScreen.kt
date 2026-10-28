package com.kyle.kinerary.ui.itinerary

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyle.kinerary.data.TripRepository
import com.kyle.kinerary.notes.TripSettings
import com.kyle.kinerary.sheet.Day
import com.kyle.kinerary.sheet.Item
import com.kyle.kinerary.sheet.Itinerary
import com.kyle.kinerary.ui.KRed
import com.kyle.kinerary.ui.contentOn
import com.kyle.kinerary.ui.locationColor
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

private val NoLocation = Color(0xFF8A8D96)
private val BookColor = Color(0xFFFFC857)
private val longDate = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ENGLISH)
private val shortDate = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItineraryScreen(
    repository: TripRepository,
    modifier: Modifier,
    onOpenSettings: () -> Unit,
    onOpenIndex: (String?) -> Unit,
) {
    val itinerary by repository.itinerary.collectAsStateWithLifecycle()
    val status by repository.sheetStatus.collectAsStateWithLifecycle()
    val settings by repository.settings.collectAsStateWithLifecycle()
    var listMode by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable { mutableIntStateOf(-1) }
    val scope = rememberCoroutineScope()
    val trip = itinerary
    val days = trip?.days.orEmpty()

    // Start on today during the trip, else the first day
    if (selected !in days.indices && days.isNotEmpty()) {
        val today = LocalDate.now()
        selected = days.indexOfFirst { it.date == today }.takeIf { it >= 0 } ?: 0
    }
    val colorOf = { day: Day -> locationColor(day.location, settings, trip) ?: NoLocation }

    Column(modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text(trip?.title?.ifBlank { null } ?: "Kinerary", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    countdown(trip)?.let {
                        Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            actions = {
                if (days.isNotEmpty()) {
                    IconButton(onClick = { listMode = !listMode }) {
                        Icon(
                            if (listMode) Icons.Outlined.CalendarMonth else Icons.AutoMirrored.Outlined.ViewList,
                            contentDescription = if (listMode) "Calendar" else "Whole itinerary",
                        )
                    }
                }
                IconButton(onClick = onOpenSettings) { Icon(Icons.Outlined.Settings, contentDescription = "Settings") }
            },
        )
        status.error?.let { error ->
            Surface(color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Couldn't update: $error" + if (trip != null) " Showing the saved copy." else "",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
        PullToRefreshBox(
            isRefreshing = status.loading,
            onRefresh = { scope.launch { repository.refreshSheet() } },
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                settings.sheetUrl.isBlank() && trip == null -> Empty(
                    "Add the Google Sheet your itinerary is in to get started.",
                    "Add sheet",
                    onOpenSettings,
                )
                trip == null && status.loading -> Box(Modifier.fillMaxSize())
                days.isEmpty() -> Empty(
                    "No days found in the sheet. Each day needs a date in column A.",
                    "Settings",
                    onOpenSettings,
                )
                listMode -> WholeTrip(trip!!, settings, selected, colorOf, onOpenIndex) { index ->
                    selected = index
                    listMode = false
                }
                else -> CalendarView(trip!!, settings, selected, { selected = it }, colorOf, onOpenIndex)
            }
        }
    }
}

@Composable
private fun Empty(message: String, action: String, onAction: () -> Unit) {
    // Scrollable so pull to refresh still works
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(32.dp)) {
        item {
            Column(Modifier.fillMaxWidth().padding(top = 80.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(message, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                Button(onClick = onAction) { Text(action) }
            }
        }
    }
}

private fun countdown(trip: Itinerary?): String? {
    val start = trip?.start ?: return null
    val end = trip.end ?: return null
    val today = LocalDate.now()
    val total = ChronoUnit.DAYS.between(start, end) + 1
    return when {
        today < start -> ChronoUnit.DAYS.between(today, start).let { if (it == 1L) "Tomorrow!" else "In $it days · $total days" }
        today > end -> "$total days · ${start.format(shortDate)} – ${end.format(shortDate)}"
        else -> "Day ${ChronoUnit.DAYS.between(start, today) + 1} of $total"
    }
}

@Composable
private fun CalendarView(
    trip: Itinerary,
    settings: TripSettings,
    selected: Int,
    onSelect: (Int) -> Unit,
    colorOf: (Day) -> Color,
    onOpenIndex: (String?) -> Unit,
) {
    val days = trip.days
    val pager = rememberPagerState(initialPage = selected.coerceIn(days.indices)) { days.size }
    val scope = rememberCoroutineScope()
    LaunchedEffect(pager.currentPage) { onSelect(pager.currentPage) }
    val goTo = { index: Int -> scope.launch { pager.animateScrollToPage(index) }; Unit }

    Column(Modifier.fillMaxSize()) {
        TripCalendar(days, pager.currentPage, colorOf, goTo)
        Legend(trip, settings) { location ->
            days.indexOfFirst { it.location == location }.takeIf { it >= 0 }?.let(goTo)
        }
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), beyondViewportPageCount = 1) { page ->
            DayPage(days[page], colorOf(days[page]), onOpenIndex)
        }
    }
}

@Composable
private fun TripCalendar(days: List<Day>, selected: Int, colorOf: (Day) -> Color, onSelect: (Int) -> Unit) {
    val byDate = remember(days) { days.withIndex().associate { (i, day) -> day.date to i } }
    val first = days.first().date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val last = days.last().date.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
    val weeks = (ChronoUnit.DAYS.between(first, last).toInt() + 1) / 7
    val today = LocalDate.now()

    Column(Modifier.padding(horizontal = 12.dp)) {
        Row(Modifier.fillMaxWidth()) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                Text(
                    it,
                    Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        for (week in 0 until weeks) {
            Row(Modifier.fillMaxWidth()) {
                for (d in 0 until 7) {
                    val date = first.plusDays((week * 7 + d).toLong())
                    val index = byDate[date]
                    val day = index?.let(days::get)
                    val color = day?.let(colorOf)
                    val isSelected = index == selected
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1.25f)
                            .padding(2.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .then(
                                when {
                                    color == null -> Modifier
                                    isSelected -> Modifier.background(color)
                                    else -> Modifier.background(color.copy(alpha = 0.22f))
                                }
                            )
                            .then(if (index != null) Modifier.clickable { onSelect(index) } else Modifier),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            if (date.dayOfMonth == 1 || date == first) {
                                Text(
                                    date.month.getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH),
                                    fontSize = 9.sp,
                                    lineHeight = 9.sp,
                                    color = when {
                                        isSelected && color != null -> contentOn(color)
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                            Text(
                                date.dayOfMonth.toString(),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (day != null) FontWeight.SemiBold else FontWeight.Normal,
                                color = when {
                                    isSelected && color != null -> contentOn(color)
                                    day != null -> MaterialTheme.colorScheme.onSurface
                                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                },
                            )
                        }
                        if (date == today) {
                            Box(
                                Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 3.dp)
                                    .size(5.dp)
                                    .background(if (isSelected) contentOn(color!!) else KRed, CircleShape)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Legend(trip: Itinerary, settings: TripSettings, onClick: (String) -> Unit) {
    if (trip.locations.isEmpty()) return
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(trip.locations) { location ->
            val color = locationColor(location.name, settings, trip) ?: NoLocation
            Row(
                Modifier
                    .clip(CircleShape)
                    .border(1.dp, color.copy(alpha = 0.5f), CircleShape)
                    .clickable { onClick(location.name) }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(8.dp).background(color, CircleShape))
                Spacer(Modifier.width(6.dp))
                Text(location.name, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun DayPage(day: Day, color: Color, onOpenIndex: (String?) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp)) {
        item {
            DayHeader(day, color, Modifier.padding(bottom = 12.dp))
        }
        if (day.items.isEmpty()) {
            item {
                Text("Nothing planned yet", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp))
            }
        }
        itemsIndexed(day.items) { i, item ->
            ItemRow(item, color, first = i == 0, last = i == day.items.lastIndex) { onOpenIndex(day.location) }
        }
    }
}

@Composable
private fun DayHeader(day: Day, color: Color, modifier: Modifier = Modifier) {
    Surface(
        color = color.copy(alpha = 0.16f),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(6.dp).height(64.dp).background(color))
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp).weight(1f)) {
                Text(day.date.format(longDate), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    listOfNotNull(day.label.ifBlank { null }, day.location).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = color,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ItemRow(item: Item, color: Color, first: Boolean, last: Boolean, onOpenIndex: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
        // Timeline: a line through dots
        Box(Modifier.width(24.dp).fillMaxHeight()) {
            val line = color.copy(alpha = 0.35f)
            if (!first) {
                Box(Modifier.align(Alignment.TopCenter).width(2.dp).height(18.dp).background(line))
            }
            if (!last) {
                Box(Modifier.align(Alignment.TopCenter).padding(top = 18.dp).width(2.dp).fillMaxHeight().background(line))
            }
            Box(Modifier.align(Alignment.TopCenter).padding(top = 13.dp).size(10.dp).background(color, CircleShape))
        }
        Column(Modifier.weight(1f).padding(start = 8.dp, top = 8.dp, bottom = 10.dp)) {
            Text(
                item.text,
                style = MaterialTheme.typography.bodyLarge,
                modifier = if (item.link != null) Modifier.clickable { uriHandler.openUri(item.link) } else Modifier,
            )
            if (item.tags.isNotEmpty() || item.seeIndex || item.link != null) {
                FlowRow(
                    Modifier.padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    item.tags.forEach { tag ->
                        val isBooking = tag.equals("book", ignoreCase = true)
                        Tag(
                            if (isBooking) "Needs booking" else tag,
                            if (isBooking) BookColor else MaterialTheme.colorScheme.onSurfaceVariant,
                            if (isBooking) Icons.Outlined.EventAvailable else null,
                        )
                    }
                    if (item.seeIndex) {
                        Tag("See Index ›", color, null, onClick = onOpenIndex)
                    }
                    if (item.link != null) {
                        Tag("Open link", color, Icons.Outlined.Link) { uriHandler.openUri(item.link) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Tag(
    text: String,
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    onClick: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.14f))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = color, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(text, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WholeTrip(
    trip: Itinerary,
    settings: TripSettings,
    selected: Int,
    colorOf: (Day) -> Color,
    onOpenIndex: (String?) -> Unit,
    onOpenDay: (Int) -> Unit,
) {
    // Rows: a location title when the location changes, then each day's header and items
    val rows = remember(trip) {
        buildList {
            var location: String? = null
            trip.days.forEachIndexed { i, day ->
                if (day.location != null && day.location != location) add(ListRow.Place(day.location))
                location = day.location
                add(ListRow.DayHead(i))
                day.items.forEachIndexed { j, item -> add(ListRow.Entry(i, j, item)) }
            }
        }
    }
    val start = remember(rows) { rows.indexOfFirst { it is ListRow.DayHead && it.index == selected }.coerceAtLeast(0) }
    val state = rememberLazyListState(initialFirstVisibleItemIndex = start)

    LazyColumn(state = state, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        rows.forEachIndexed { position, row ->
            when (row) {
                is ListRow.Place -> item(key = "place-$position") {
                    val color = locationColor(row.name, settings, trip) ?: NoLocation
                    Text(
                        row.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = color,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
                    )
                }
                is ListRow.DayHead -> stickyHeader(key = "day-${row.index}") {
                    val day = trip.days[row.index]
                    val color = colorOf(day)
                    Surface(color = MaterialTheme.colorScheme.background) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onOpenDay(row.index) }
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(color.copy(alpha = 0.18f)),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.width(5.dp).height(36.dp).background(color))
                            Text(
                                day.date.format(shortDate),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(start = 10.dp).weight(1f),
                            )
                            Text(
                                day.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = color,
                                modifier = Modifier.padding(end = 12.dp),
                            )
                        }
                    }
                }
                is ListRow.Entry -> item(key = "item-${row.day}-${row.index}") {
                    val day = trip.days[row.day]
                    Box(Modifier.padding(horizontal = 16.dp)) {
                        ItemRow(
                            row.item,
                            colorOf(day),
                            first = row.index == 0,
                            last = row.index == day.items.lastIndex,
                        ) { onOpenIndex(day.location) }
                    }
                }
            }
        }
    }
}

private sealed interface ListRow {
    data class Place(val name: String) : ListRow
    data class DayHead(val index: Int) : ListRow
    data class Entry(val day: Int, val index: Int, val item: Item) : ListRow
}
