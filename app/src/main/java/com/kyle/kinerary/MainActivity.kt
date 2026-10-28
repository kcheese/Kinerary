package com.kyle.kinerary

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Luggage
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.kyle.kinerary.data.TripRepository
import com.kyle.kinerary.data.Updater
import com.kyle.kinerary.notes.Section
import com.kyle.kinerary.ui.UpdateDialog
import com.kyle.kinerary.ui.KineraryTheme
import com.kyle.kinerary.ui.itinerary.ItineraryScreen
import com.kyle.kinerary.ui.notes.NotesScreen
import com.kyle.kinerary.ui.settings.SettingsScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        val app = application as KineraryApp
        setContent {
            KineraryTheme {
                Main(app.repository, app.updater)
                UpdateDialog(app.updater)
            }
        }
    }
}

private enum class Tab(val label: String, val icon: ImageVector) {
    Itinerary("Itinerary", Icons.Outlined.CalendarMonth),
    Index("Index", Icons.AutoMirrored.Outlined.MenuBook),
    Prep("Prep", Icons.Outlined.Luggage),
}

@Composable
private fun Main(repository: TripRepository, updater: Updater) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    // A page the Index tab should open, asked for from the itinerary ("See Index")
    var indexRequest by rememberSaveable { mutableStateOf<String?>(null) }

    if (settingsOpen) {
        BackHandler { settingsOpen = false }
        SettingsScreen(repository, updater, onBack = { settingsOpen = false })
        return
    }
    BackHandler(enabled = tab != 0) { tab = 0 }

    // While typing, the keyboard takes the tab bar's place
    @OptIn(ExperimentalLayoutApi::class)
    val typing = WindowInsets.isImeVisible
    Scaffold(
        bottomBar = {
            if (!typing) NavigationBar {
                Tab.entries.forEachIndexed { i, entry ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(entry.icon, contentDescription = null) },
                        label = { Text(entry.label) },
                    )
                }
            }
        },
    ) { padding ->
        val modifier = Modifier
            .padding(bottom = padding.calculateBottomPadding())
            .consumeWindowInsets(PaddingValues(bottom = padding.calculateBottomPadding()))
            .imePadding()
        when (Tab.entries[tab]) {
            Tab.Itinerary -> ItineraryScreen(
                repository,
                modifier,
                onOpenSettings = { settingsOpen = true },
                onOpenIndex = { location ->
                    indexRequest = location
                    tab = Tab.Index.ordinal
                },
            )
            Tab.Index -> NotesScreen(
                repository,
                Section.INDEX,
                "Index",
                modifier,
                onOpenSettings = { settingsOpen = true },
                openRequest = indexRequest,
                onOpenRequestHandled = { indexRequest = null },
            )
            Tab.Prep -> NotesScreen(
                repository,
                Section.PREP,
                "Prep & Packing",
                modifier,
                onOpenSettings = { settingsOpen = true },
            )
        }
    }
}
