package com.kyle.kinerary.ui.settings

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyle.kinerary.BuildConfig
import com.kyle.kinerary.data.TripRepository
import com.kyle.kinerary.data.UpdateState
import com.kyle.kinerary.data.Updater
import com.kyle.kinerary.ui.locationColor
import com.kyle.kinerary.ui.notes.ColorChoices
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(repository: TripRepository, updater: Updater, onBack: () -> Unit) {
    val settings by repository.settings.collectAsStateWithLifecycle()
    val itinerary by repository.itinerary.collectAsStateWithLifecycle()
    val sheetStatus by repository.sheetStatus.collectAsStateWithLifecycle()
    val account by repository.account.collectAsStateWithLifecycle()
    val syncStatus by repository.syncStatus.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Sheet
            Heading("Itinerary sheet")
            var link by remember(settings.sheetUrl) { mutableStateOf(settings.sheetUrl) }
            OutlinedTextField(
                link,
                { link = it },
                label = { Text("Google Sheets link") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                supportingText = {
                    Text("Share the sheet as \"Anyone with the link can view\" and paste its link here.")
                },
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = {
                        repository.saveSettings { it.copy(sheetUrl = link.trim()) }
                        scope.launch { repository.refreshSheet() }
                    },
                    enabled = !sheetStatus.loading && link.isNotBlank(),
                ) { Text(if (link.trim() == settings.sheetUrl) "Reload" else "Save & load") }
                Spacer(Modifier.width(12.dp))
                Text(
                    when {
                        sheetStatus.loading -> "Loading…"
                        sheetStatus.error != null -> sheetStatus.error!!
                        sheetStatus.fetched > 0 -> "Updated ${ago(sheetStatus.fetched)}"
                        else -> ""
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (sheetStatus.error != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Colours
            val locations = itinerary?.locations.orEmpty()
            if (locations.isNotEmpty()) {
                HorizontalDivider()
                Heading("Location colours")
                var open by remember { mutableStateOf<String?>(null) }
                locations.forEach { location ->
                    val color = locationColor(location.name, settings, itinerary) ?: Color.Gray
                    Column {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { open = if (open == location.name) null else location.name }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(24.dp).background(color, CircleShape))
                            Spacer(Modifier.width(12.dp))
                            Text(location.name, Modifier.weight(1f))
                            if (location.name in settings.colors) {
                                Text("Custom", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (open == location.name) {
                            ColorChoices(color, allowNone = false) { chosen ->
                                val argb = chosen!!.toArgb().toLong() and 0xFFFFFFFFL
                                repository.saveSettings { it.copy(colors = it.colors + (location.name to argb)) }
                            }
                            if (location.name in settings.colors) {
                                TextButton(onClick = {
                                    repository.saveSettings { it.copy(colors = it.colors - location.name) }
                                }) { Text("Use the sheet's colour") }
                            }
                        }
                    }
                }
            }

            // Notes import
            HorizontalDivider()
            Heading("Index & packing from the sheet")
            Text(
                "The sheet's Index tabs and packing tabs were copied in as pages when it was first loaded. " +
                    "Copy them again to bring in changes made in the sheet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            var confirmImport by remember { mutableStateOf(false) }
            OutlinedButton(onClick = { confirmImport = true }, enabled = repository.hasWorkbook) { Text("Copy from sheet again") }
            if (confirmImport) {
                AlertDialog(
                    onDismissRequest = { confirmImport = false },
                    title = { Text("Copy from the sheet?") },
                    text = { Text("Pages that came from the sheet are replaced by the sheet's current version, losing edits made in the app. Pages you made yourself aren't touched.") },
                    confirmButton = {
                        TextButton(onClick = {
                            confirmImport = false
                            scope.launch { repository.importNotes(replace = true) }
                        }) { Text("Replace") }
                    },
                    dismissButton = { TextButton(onClick = { confirmImport = false }) { Text("Cancel") } },
                )
            }

            // Sync
            HorizontalDivider()
            Heading("Sync")
            val signedIn = account
            if (signedIn != null) {
                Text("Signed in as ${signedIn.username}", style = MaterialTheme.typography.bodyLarge)
                Text(
                    when {
                        syncStatus.syncing -> "Syncing…"
                        syncStatus.error != null -> "Last sync failed: ${syncStatus.error}"
                        syncStatus.synced > 0 -> "Synced ${ago(syncStatus.synced)}"
                        else -> "Not synced yet"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (syncStatus.error != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row {
                    Button(onClick = repository::syncNow) { Text("Sync now") }
                    Spacer(Modifier.width(12.dp))
                    OutlinedButton(onClick = repository::logOut) { Text("Sign out") }
                }
            } else {
                Text(
                    "Sign in with your K-Reader sync account. Notes, checklists, location colours and the sheet " +
                        "link are shared between phones signed in to the same account.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                var server by remember { mutableStateOf(BuildConfig.SYNC_SERVER) }
                var username by remember { mutableStateOf("") }
                var password by remember { mutableStateOf("") }
                var busy by remember { mutableStateOf(false) }
                var error by remember { mutableStateOf<String?>(null) }
                OutlinedTextField(server, { server = it }, label = { Text("Server") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(username, { username = it }, label = { Text("Username") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    password,
                    { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }
                Button(
                    onClick = {
                        busy = true
                        error = null
                        scope.launch {
                            error = repository.logIn(server, username, password)
                            busy = false
                        }
                    },
                    enabled = !busy && server.isNotBlank() && username.isNotBlank() && password.isNotEmpty(),
                ) { Text(if (busy) "Signing in…" else "Sign in") }
            }

            HorizontalDivider()
            Heading("App")
            Text("Kinerary ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyLarge)
            if (updater.enabled) {
                val update by updater.state.collectAsStateWithLifecycle()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = { scope.launch { updater.check() } },
                        enabled = update !is UpdateState.Checking && update !is UpdateState.Downloading,
                    ) { Text("Check for updates") }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        when (val u = update) {
                            UpdateState.Checking -> "Checking…"
                            UpdateState.UpToDate -> "Up to date"
                            is UpdateState.Failed -> "Couldn't check: ${u.message}"
                            else -> ""
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
}

private fun ago(time: Long): String =
    if (System.currentTimeMillis() - time < DateUtils.MINUTE_IN_MILLIS) {
        "just now"
    } else {
        DateUtils.getRelativeTimeSpanString(time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
    }
