package com.kyle.kinerary.ui.notes

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyle.kinerary.data.TripRepository
import com.kyle.kinerary.notes.Block
import com.kyle.kinerary.notes.Page
import com.kyle.kinerary.notes.Section
import com.kyle.kinerary.notes.newId
import com.kyle.kinerary.sheet.placeKey
import com.kyle.kinerary.ui.Palette
import com.kyle.kinerary.ui.locationColor
import com.kyle.kinerary.ui.locationNamed
import kotlinx.coroutines.launch

private val PlainPage = Color(0xFFB0B4BC)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(
    repository: TripRepository,
    section: String,
    title: String,
    modifier: Modifier,
    onOpenSettings: () -> Unit,
    openRequest: String? = null,
    onOpenRequestHandled: () -> Unit = {},
) {
    val allPages by repository.pages.collectAsStateWithLifecycle()
    val itinerary by repository.itinerary.collectAsStateWithLifecycle()
    val settings by repository.settings.collectAsStateWithLifecycle()
    val pages = remember(allPages, section) {
        allPages.filter { it.section == section && !it.deleted }.sortedWith(compareBy({ it.order }, { it.title }))
    }
    var editing by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var pageDialog by remember { mutableStateOf<Page?>(null) }
    var deleting by remember { mutableStateOf<Page?>(null) }
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val current = pages.getOrNull(pager.currentPage)
    val colorOf = { page: Page ->
        locationColor(locationNamed(page.title, itinerary), settings, itinerary)
            ?: page.color?.let(::Color)
            ?: PlainPage
    }

    // "See Index" from the itinerary: open the page for that location
    LaunchedEffect(openRequest, pages.size) {
        if (openRequest == null || pages.isEmpty()) return@LaunchedEffect
        val index = pages.indexOfFirst { placeKey(it.title) == placeKey(openRequest) }
        if (index >= 0) pager.scrollToPage(index)
        onOpenRequestHandled()
    }

    Column(modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(title) },
            actions = {
                if (current != null) {
                    IconButton(onClick = { editing = !editing }) {
                        if (editing) {
                            Icon(Icons.Outlined.Check, "Done", tint = MaterialTheme.colorScheme.primary)
                        } else {
                            Icon(Icons.Outlined.Edit, "Edit")
                        }
                    }
                }
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Outlined.MoreVert, "More") }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("New page") }, onClick = {
                        menuOpen = false
                        pageDialog = Page(newId(), section, "", (pages.maxOfOrNull { it.order } ?: -1) + 1, modified = 0)
                    })
                    if (current != null) {
                        DropdownMenuItem(text = { Text("Rename & colour") }, onClick = {
                            menuOpen = false
                            pageDialog = current
                        })
                        val index = pages.indexOf(current)
                        if (index > 0) {
                            DropdownMenuItem(text = { Text("Move left") }, onClick = {
                                menuOpen = false
                                swap(repository, pages, index, index - 1)
                                scope.launch { pager.scrollToPage(index - 1) }
                            })
                        }
                        if (index < pages.lastIndex) {
                            DropdownMenuItem(text = { Text("Move right") }, onClick = {
                                menuOpen = false
                                swap(repository, pages, index, index + 1)
                                scope.launch { pager.scrollToPage(index + 1) }
                            })
                        }
                        DropdownMenuItem(text = { Text("Delete page") }, onClick = {
                            menuOpen = false
                            deleting = current
                        })
                    }
                    DropdownMenuItem(
                        text = { Text("Settings") },
                        leadingIcon = { Icon(Icons.Outlined.Settings, null) },
                        onClick = {
                            menuOpen = false
                            onOpenSettings()
                        },
                    )
                }
            },
        )

        if (pages.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    if (section == Section.INDEX) {
                        "Pages of notes about each place: hotels, opening hours, links."
                    } else {
                        "Packing lists and things to sort out before you go."
                    },
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = {
                    pageDialog = Page(newId(), section, "", 0, modified = 0)
                }) { Text("New page") }
            }
        } else {
            val selectedColor = current?.let(colorOf) ?: MaterialTheme.colorScheme.primary
            PrimaryScrollableTabRow(
                selectedTabIndex = pager.currentPage.coerceIn(pages.indices),
                edgePadding = 12.dp,
                containerColor = MaterialTheme.colorScheme.background,
                indicator = {
                    TabRowDefaults.PrimaryIndicator(
                        Modifier.tabIndicatorOffset(pager.currentPage.coerceIn(pages.indices), matchContentSize = true),
                        width = Dp.Unspecified,
                        color = selectedColor,
                    )
                },
            ) {
                pages.forEachIndexed { i, page ->
                    val color = colorOf(page)
                    Tab(
                        selected = i == pager.currentPage,
                        onClick = { scope.launch { pager.animateScrollToPage(i) } },
                        selectedContentColor = color,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(8.dp).background(color, CircleShape))
                                Spacer(Modifier.width(6.dp))
                                Text(page.title.ifBlank { "Untitled" })
                            }
                        },
                    )
                }
            }
            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), key = { pages.getOrNull(it)?.id ?: it }) { i ->
                val page = pages.getOrNull(i) ?: return@HorizontalPager
                PageContent(
                    page,
                    colorOf(page),
                    editing,
                    checklists = section == Section.PREP,
                    onEdit = { transform -> repository.editPage(page.id, transform) },
                )
            }
        }
    }

    pageDialog?.let { page ->
        PageDialog(
            page = page,
            isNew = pages.none { it.id == page.id },
            linkedColor = locationColor(locationNamed(page.title, itinerary), settings, itinerary),
            onDismiss = { pageDialog = null },
            onSave = { name, color ->
                pageDialog = null
                if (pages.any { it.id == page.id }) {
                    repository.editPage(page.id) { it.copy(title = name, color = color) }
                } else {
                    repository.addPage(page.copy(title = name, color = color, modified = System.currentTimeMillis()))
                    editing = true
                    scope.launch { pager.scrollToPage(pages.size) }
                }
            },
        )
    }
    deleting?.let { page ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete \"${page.title}\"?") },
            text = { Text("The page and everything on it will be deleted on every device.") },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    repository.deletePage(page.id)
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

private fun swap(repository: TripRepository, pages: List<Page>, a: Int, b: Int) {
    // Renumber the whole section so pages that shared an order get distinct ones
    val reordered = pages.toMutableList().apply { add(b, removeAt(a)) }
    reordered.forEachIndexed { i, page ->
        if (page.order != i) repository.editPage(page.id) { it.copy(order = i) }
    }
}

@Composable
private fun PageContent(
    page: Page,
    color: Color,
    editing: Boolean,
    checklists: Boolean,
    onEdit: ((Page) -> Page) -> Unit,
) {
    fun editBlock(id: String, block: Block) = onEdit { p -> p.copy(blocks = p.blocks.map { if (it.id == id) block else it }) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(if (editing) 12.dp else 8.dp),
    ) {
        if (page.blocks.isEmpty() && !editing) {
            item {
                Text(
                    "Nothing here yet. Tap the pencil to add headings, text, tables" +
                        if (checklists) " and checklists." else ".",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 24.dp),
                )
            }
        }
        itemsIndexed(page.blocks, key = { _, block -> block.id }) { i, block ->
            if (editing) {
                EditFrame(
                    block,
                    canMoveUp = i > 0,
                    canMoveDown = i < page.blocks.lastIndex,
                    onMove = { delta ->
                        onEdit { p ->
                            val blocks = p.blocks.toMutableList()
                            val from = blocks.indexOfFirst { it.id == block.id }
                            val to = from + delta
                            if (from >= 0 && to in blocks.indices) blocks.add(to, blocks.removeAt(from))
                            p.copy(blocks = blocks)
                        }
                    },
                    onDelete = { onEdit { p -> p.copy(blocks = p.blocks.filter { it.id != block.id }) } },
                ) {
                    BlockEditor(block, color) { editBlock(block.id, it) }
                }
            } else {
                // Long-press to select and copy
                SelectionContainer {
                    BlockView(block, color) { editBlock(block.id, it) }
                }
            }
        }
        if (editing) {
            item(key = "add") {
                AddBlock(checklists) { kind -> onEdit { p -> p.copy(blocks = p.blocks + newBlock(kind)) } }
            }
        }
    }
}

@Composable
private fun EditFrame(
    block: Block,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMove: (Int) -> Unit,
    onDelete: () -> Unit,
    content: @Composable () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 12.dp, end = 4.dp, bottom = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when (block) {
                        is Block.Heading -> "Heading"
                        is Block.Text -> "Text"
                        is Block.Table -> "Table"
                        is Block.Checklist -> "Checklist"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                SmallIcon(Icons.Outlined.ArrowUpward, "Move up", size = 40.dp, enabled = canMoveUp) { onMove(-1) }
                SmallIcon(Icons.Outlined.ArrowDownward, "Move down", size = 40.dp, enabled = canMoveDown) { onMove(1) }
                SmallIcon(Icons.Outlined.DeleteOutline, "Delete", size = 40.dp, onClick = onDelete)
            }
            Box(Modifier.padding(end = 8.dp)) { content() }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddBlock(checklists: Boolean, onAdd: (String) -> Unit) {
    Column {
        Text("Add", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AddButton("Heading", Icons.Outlined.Title) { onAdd("heading") }
            AddButton("Text", Icons.AutoMirrored.Outlined.Notes) { onAdd("text") }
            AddButton("Table", Icons.Outlined.TableChart) { onAdd("table") }
            if (checklists) AddButton("Checklist", Icons.Outlined.CheckBox) { onAdd("checklist") }
        }
    }
}

@Composable
private fun AddButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)) {
        Icon(icon, null, Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PageDialog(
    page: Page,
    isNew: Boolean,
    linkedColor: Color?,
    onDismiss: () -> Unit,
    onSave: (String, Long?) -> Unit,
) {
    var name by remember { mutableStateOf(page.title) }
    var color by remember { mutableStateOf(page.color) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "New page" else "Rename page") },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                Spacer(Modifier.height(16.dp))
                if (linkedColor != null) {
                    Text(
                        "Uses this location's colour. Change it in Settings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    ColorChoices(color?.let(::Color)) { color = it?.toArgb()?.toLong()?.and(0xFFFFFFFFL) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name.trim(), color) }, enabled = name.isNotBlank()) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** The palette as tappable swatches; null is "no colour" */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorChoices(selected: Color?, allowNone: Boolean = true, onSelect: (Color?) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (allowNone) {
            Swatch(PlainPage.copy(alpha = 0.25f), selected == null) { onSelect(null) }
        }
        Palette.forEach { color ->
            Swatch(color, selected?.toArgb() == color.toArgb()) { onSelect(color) }
        }
    }
}

@Composable
private fun Swatch(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(color)
            .then(if (selected) Modifier.border(3.dp, Color.White, CircleShape) else Modifier)
            .clickable(onClick = onClick),
    )
}
