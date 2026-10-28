package com.kyle.kinerary.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyle.kinerary.BuildConfig
import com.kyle.kinerary.data.UpdateState
import com.kyle.kinerary.data.Updater
import kotlinx.coroutines.launch

/** Offers a newer version when one is found, and shows its download */
@Composable
fun UpdateDialog(updater: Updater) {
    val state by updater.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val release = when (val s = state) {
        is UpdateState.Available -> s.release
        is UpdateState.Downloading -> s.release
        else -> return
    }
    val downloading = state as? UpdateState.Downloading
    AlertDialog(
        onDismissRequest = { if (downloading == null) updater.later(release) },
        title = { Text("Kinerary ${release.version} is available") },
        text = {
            Column {
                Text(
                    "You have ${BuildConfig.VERSION_NAME}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (release.notes.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(release.notes, Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState()))
                }
                if (downloading != null) {
                    Spacer(Modifier.height(16.dp))
                    LinearProgressIndicator(progress = { downloading.progress }, modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { scope.launch { updater.install(release) } },
                enabled = downloading == null,
            ) { Text(if (downloading != null) "Downloading…" else "Update") }
        },
        dismissButton = {
            TextButton(onClick = { updater.later(release) }, enabled = downloading == null) { Text("Later") }
        },
    )
}
