package com.kyle.kinerary.data

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import com.kyle.kinerary.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class Release(val version: String, val notes: String, val apkUrl: String)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: Release) : UpdateState
    data class Downloading(val release: Release, val progress: Float) : UpdateState
    data class Failed(val message: String) : UpdateState
}

/**
 * Looks for a newer release on the app's GitHub repo (tagged "v<version>", with the APK attached) and
 * installs it through Android's package installer. Updates install in place because every release is
 * signed with the same key.
 */
class Updater(private val context: Context) {
    private val prefs = context.getSharedPreferences("kinerary", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /** A version the user said "later" to, so the automatic check doesn't ask again */
    private var skipped: String?
        get() = prefs.getString(KEY_SKIPPED, null)
        set(value) = prefs.edit().putString(KEY_SKIPPED, value).apply()

    val enabled get() = BuildConfig.UPDATE_REPO.isNotBlank()

    /** The automatic check on opening the app, at most every few hours */
    suspend fun checkQuietly() {
        if (!enabled || _state.value !is UpdateState.Idle) return
        val last = prefs.getLong(KEY_CHECKED, 0)
        if (System.currentTimeMillis() - last < 6 * 60 * 60 * 1000L) return
        val release = try {
            withContext(Dispatchers.IO) { latest() }
        } catch (e: Exception) {
            Log.w(TAG, "Update check failed", e)
            return
        }
        prefs.edit().putLong(KEY_CHECKED, System.currentTimeMillis()).apply()
        if (release != null && isNewer(release.version, BuildConfig.VERSION_NAME) && release.version != skipped) {
            _state.value = UpdateState.Available(release)
        }
    }

    /** Asked for in Settings: always checks and reports the result */
    suspend fun check() {
        if (!enabled) return
        _state.value = UpdateState.Checking
        _state.value = try {
            val release = withContext(Dispatchers.IO) { latest() }
            prefs.edit().putLong(KEY_CHECKED, System.currentTimeMillis()).apply()
            if (release != null && isNewer(release.version, BuildConfig.VERSION_NAME)) {
                UpdateState.Available(release)
            } else {
                UpdateState.UpToDate
            }
        } catch (e: Exception) {
            UpdateState.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    fun later(release: Release) {
        skipped = release.version
        _state.value = UpdateState.Idle
    }

    suspend fun install(release: Release) {
        _state.value = UpdateState.Downloading(release, 0f)
        try {
            val file = withContext(Dispatchers.IO) { download(release) }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
            val intent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            _state.value = UpdateState.Available(release)
        } catch (e: Exception) {
            Log.w(TAG, "Update download failed", e)
            _state.value = UpdateState.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    private fun latest(): Release? {
        val connection = open("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest")
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        try {
            when (connection.responseCode) {
                200 -> Unit
                404 -> return null
                else -> throw IOException("GitHub answered ${connection.responseCode}")
            }
            val json = JSONObject(connection.inputStream.use { it.readBytes().decodeToString() })
            val assets = json.getJSONArray("assets")
            val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
                .firstOrNull { it.getString("name").endsWith(".apk") } ?: return null
            return Release(
                version = json.getString("tag_name").removePrefix("v"),
                notes = json.optString("body"),
                apkUrl = apk.getString("browser_download_url"),
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun download(release: Release): File {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "Kinerary-${release.version}.apk")
        val connection = open(release.apkUrl)
        try {
            if (connection.responseCode != 200) throw IOException("GitHub answered ${connection.responseCode}")
            val total = connection.contentLengthLong
            connection.inputStream.use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        done += read
                        if (total > 0) _state.value = UpdateState.Downloading(release, done / total.toFloat())
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
        return file
    }

    private fun open(url: String) = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 20_000
        readTimeout = 60_000
        instanceFollowRedirects = true
    }

    companion object {
        private const val TAG = "Kinerary"
        private const val KEY_SKIPPED = "update_skipped"
        private const val KEY_CHECKED = "update_checked"

        /** "1.10.0" is newer than "1.9.2" */
        fun isNewer(candidate: String, current: String): Boolean {
            val a = candidate.split('.', '-').map { it.toIntOrNull() ?: 0 }
            val b = current.split('.', '-').map { it.toIntOrNull() ?: 0 }
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrElse(i) { 0 }
                val y = b.getOrElse(i) { 0 }
                if (x != y) return x > y
            }
            return false
        }
    }
}
