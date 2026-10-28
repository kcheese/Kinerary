package com.kyle.kinerary.data

import android.content.Context
import android.util.Log
import com.kyle.kinerary.BuildConfig
import com.kyle.kinerary.notes.Page
import com.kyle.kinerary.notes.TripSettings
import com.kyle.kinerary.notes.importPages
import com.kyle.kinerary.sheet.Itinerary
import com.kyle.kinerary.sheet.Workbook
import com.kyle.kinerary.sheet.itinerarySheet
import com.kyle.kinerary.sheet.parseItinerary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException

/** What the Itinerary tab shows about the sheet download */
data class SheetStatus(
    val loading: Boolean = false,
    val error: String? = null,
    val fetched: Long = 0,
)

data class Account(val server: String, val username: String, val key: String)

data class SyncStatus(val syncing: Boolean = false, val error: String? = null, val synced: Long = 0)

@Serializable
private data class NotesFile(val pages: List<Page> = emptyList(), val settings: TripSettings = TripSettings())

/**
 * Everything the app shows: the itinerary (downloaded from the Google Sheet and kept for offline use),
 * the notes pages and the shared settings (kept in notes.json and synced through the K-Reader sync
 * server), and the sync account.
 */
class TripRepository(context: Context) {
    private val dir = context.filesDir
    private val sheetFile = File(dir, "sheet.xlsx")
    private val notesFile = File(dir, "notes.json")
    private val prefs = context.getSharedPreferences("kinerary", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val saveLock = Mutex()
    private val syncLock = Mutex()

    private val _workbook = MutableStateFlow<Workbook?>(null)
    private val _itinerary = MutableStateFlow<Itinerary?>(null)
    val itinerary: StateFlow<Itinerary?> = _itinerary.asStateFlow()

    private val _sheetStatus = MutableStateFlow(SheetStatus(fetched = prefs.getLong(KEY_FETCHED, 0)))
    val sheetStatus: StateFlow<SheetStatus> = _sheetStatus.asStateFlow()

    private val _pages = MutableStateFlow<List<Page>>(emptyList())
    /** Includes deleted pages; screens filter them out */
    val pages: StateFlow<List<Page>> = _pages.asStateFlow()

    private val _settings = MutableStateFlow(TripSettings())
    val settings: StateFlow<TripSettings> = _settings.asStateFlow()

    private val _account = MutableStateFlow(loadAccount())
    val account: StateFlow<Account?> = _account.asStateFlow()

    private val _syncStatus = MutableStateFlow(SyncStatus(synced = prefs.getLong(KEY_SYNCED, 0)))
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    private var pendingSync: Job? = null
    private var foreground: Job? = null

    init {
        try {
            if (notesFile.exists()) {
                val file = json.decodeFromString<NotesFile>(notesFile.readText())
                _pages.value = file.pages
                _settings.value = file.settings
            }
        } catch (e: Exception) {
            Log.e(TAG, "Couldn't read notes", e)
        }
        try {
            if (sheetFile.exists()) showWorkbook(Workbook.read(sheetFile.readBytes()))
        } catch (e: Exception) {
            Log.e(TAG, "Couldn't read saved sheet", e)
        }
    }

    // Itinerary

    /** Downloads the sheet again; on failure the saved copy stays */
    suspend fun refreshSheet() {
        val url = exportUrl(_settings.value.sheetUrl)
        if (url == null) {
            _sheetStatus.update { it.copy(error = if (_settings.value.sheetUrl.isBlank()) null else "That doesn't look like a Google Sheets link") }
            return
        }
        _sheetStatus.update { it.copy(loading = true, error = null) }
        try {
            val bytes = withContext(Dispatchers.IO) { Http.download(url) }
            if (bytes.size < 4 || bytes[0] != 'P'.code.toByte() || bytes[1] != 'K'.code.toByte()) {
                throw IOException("Google didn't send the sheet. Check it's shared as \"Anyone with the link\".")
            }
            val workbook = withContext(Dispatchers.Default) { Workbook.read(bytes) }
            withContext(Dispatchers.IO) {
                val tmp = File(dir, "sheet.xlsx.tmp")
                tmp.writeBytes(bytes)
                tmp.renameTo(sheetFile)
            }
            showWorkbook(workbook)
            val now = System.currentTimeMillis()
            prefs.edit().putLong(KEY_FETCHED, now).apply()
            _sheetStatus.value = SheetStatus(fetched = now)
            if (!prefs.getBoolean(KEY_IMPORTED, false)) {
                importNotes(workbook, replace = false)
                prefs.edit().putBoolean(KEY_IMPORTED, true).apply()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Sheet download failed", e)
            _sheetStatus.update { it.copy(loading = false, error = e.message ?: e.javaClass.simpleName) }
        }
    }

    private fun showWorkbook(workbook: Workbook) {
        _workbook.value = workbook
        _itinerary.value = workbook.itinerarySheet()?.let(::parseItinerary)
    }

    /**
     * Adds the sheet's index and packing tabs as pages. The first, automatic import uses the oldest
     * possible time, so a page already edited on another device always wins when they sync; [replace]
     * (asked for in Settings) overwrites pages of the same name with the sheet's version.
     */
    suspend fun importNotes(workbook: Workbook? = _workbook.value, replace: Boolean) {
        workbook ?: return
        val imported = importPages(workbook, modified = if (replace) System.currentTimeMillis() else 1)
        editPages(stamp = false) { pages ->
            val byId = pages.associateBy { it.id }.toMutableMap()
            for (page in imported) {
                val existing = byId[page.id]
                if (existing == null || replace) {
                    byId[page.id] = if (existing != null) page.copy(order = existing.order) else page
                }
            }
            byId.values.toList()
        }
    }

    val hasWorkbook get() = _workbook.value != null

    // Notes

    fun addPage(page: Page) = editPages { it + page }

    /** Changes one page, starting from its latest version so quick edits build on each other */
    fun editPage(id: String, transform: (Page) -> Page) = editPages { pages ->
        pages.map { if (it.id == id) transform(it) else it }
    }

    fun deletePage(id: String) = editPage(id) { it.copy(deleted = true, blocks = emptyList()) }

    fun saveSettings(transform: (TripSettings) -> TripSettings) {
        _settings.update { transform(it).copy(modified = maxOf(System.currentTimeMillis(), it.modified + 1)) }
        persist()
    }

    /**
     * Applies a change in memory straight away (so edits stay in order) and saves it to disk shortly
     * after. Pages the change touched get a new "modified" time unless [stamp] is false.
     */
    private fun editPages(stamp: Boolean = true, transform: (List<Page>) -> List<Page>) {
        _pages.update { before ->
            val after = transform(before)
            if (!stamp) return@update after
            val old = before.associateBy { it.id }
            val now = System.currentTimeMillis()
            after.map { page ->
                val previous = old[page.id]
                if (previous == page) page else page.copy(modified = maxOf(now, (previous?.modified ?: 0) + 1))
            }
        }
        persist()
    }

    private var pendingWrite: Job? = null

    private fun persist() {
        pendingWrite?.cancel()
        pendingWrite = scope.launch {
            delay(300)
            saveLock.withLock { writeNotes() }
        }
        requestSync()
    }

    private fun writeNotes() {
        val tmp = File(dir, "notes.json.tmp")
        tmp.writeText(json.encodeToString(NotesFile.serializer(), NotesFile(_pages.value, _settings.value)))
        tmp.renameTo(notesFile)
    }

    // Sync

    /** Checks the account against the server, then keeps it */
    suspend fun logIn(server: String, username: String, password: String): String? {
        val account = Account(server.trim().trimEnd('/'), username.trim(), Http.md5(password))
        return try {
            withContext(Dispatchers.IO) { Http.checkLogin(account) }
            prefs.edit()
                .putString(KEY_SERVER, account.server)
                .putString(KEY_USER, account.username)
                .putString(KEY_KEY, account.key)
                .apply()
            _account.value = account
            syncNow()
            null
        } catch (e: Exception) {
            e.message ?: e.javaClass.simpleName
        }
    }

    fun logOut() {
        prefs.edit().remove(KEY_USER).remove(KEY_KEY).apply()
        _account.value = null
        _syncStatus.value = SyncStatus()
    }

    private fun loadAccount(): Account? {
        val user = prefs.getString(KEY_USER, null) ?: return null
        val key = prefs.getString(KEY_KEY, null) ?: return null
        return Account(prefs.getString(KEY_SERVER, null) ?: BuildConfig.SYNC_SERVER, user, key)
    }

    /** Syncs a couple of seconds after the last change, so typing doesn't send a request per letter */
    private fun requestSync() {
        if (_account.value == null) return
        pendingSync?.cancel()
        pendingSync = scope.launch {
            delay(2_000)
            sync()
        }
    }

    fun syncNow() {
        scope.launch { sync() }
    }

    private suspend fun sync() {
        val account = _account.value ?: return
        syncLock.withLock {
            _syncStatus.update { it.copy(syncing = true) }
            try {
                val sent = SyncPayload(TRIP, _pages.value, _settings.value)
                val received = Http.sync(account, json.encodeToString(SyncPayload.serializer(), sent))
                    .let { json.decodeFromString(SyncPayload.serializer(), it) }
                var sheetChanged = false
                saveLock.withLock {
                    // Keep whichever copy of each page is newer; edits made while the request was out stay
                    _pages.update { local ->
                        val merged = local.associateBy { it.id }.toMutableMap()
                        for (page in received.pages) {
                            val mine = merged[page.id]
                            if (mine == null || mine.modified < page.modified) merged[page.id] = page
                        }
                        merged.values.toList()
                    }
                    received.settings?.let { theirs ->
                        if (theirs.modified > _settings.value.modified) {
                            sheetChanged = theirs.sheetUrl != _settings.value.sheetUrl
                            _settings.value = theirs
                        }
                    }
                    writeNotes()
                }
                val now = System.currentTimeMillis()
                prefs.edit().putLong(KEY_SYNCED, now).apply()
                _syncStatus.value = SyncStatus(synced = now)
                if (sheetChanged) refreshSheet()
            } catch (e: Exception) {
                Log.w(TAG, "Sync failed", e)
                _syncStatus.update { it.copy(syncing = false, error = e.message ?: e.javaClass.simpleName) }
            }
        }
    }

    /** While the app is open: sync now and every minute after, and fetch the sheet */
    fun onForeground() {
        foreground?.cancel()
        foreground = scope.launch {
            launch { refreshSheet() }
            while (isActive) {
                sync()
                delay(60_000)
            }
        }
    }

    fun onBackground() {
        foreground?.cancel()
        foreground = null
        scope.launch {
            saveLock.withLock { writeNotes() }
            sync()
        }
    }

    companion object {
        private const val TAG = "Kinerary"
        private const val TRIP = "default"
        private const val KEY_FETCHED = "sheet_fetched"
        private const val KEY_IMPORTED = "notes_imported"
        private const val KEY_SYNCED = "synced"
        private const val KEY_SERVER = "sync_server"
        private const val KEY_USER = "sync_user"
        private const val KEY_KEY = "sync_key"

        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        /**
         * The .xlsx download address for a Google Sheets link: the normal share link (needs "Anyone with
         * the link") or a "Publish to web" link.
         */
        fun exportUrl(link: String): String? {
            val trimmed = link.trim()
            Regex("""docs\.google\.com/spreadsheets/d/e/([A-Za-z0-9_-]+)""").find(trimmed)?.let {
                return "https://docs.google.com/spreadsheets/d/e/${it.groupValues[1]}/pub?output=xlsx"
            }
            Regex("""docs\.google\.com/spreadsheets/d/([A-Za-z0-9_-]{20,})""").find(trimmed)?.let {
                return "https://docs.google.com/spreadsheets/d/${it.groupValues[1]}/export?format=xlsx"
            }
            return null
        }
    }
}

@Serializable
private data class SyncPayload(val trip: String, val pages: List<Page>, val settings: TripSettings? = null)
