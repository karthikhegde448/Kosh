package com.rejowan.pdfreaderpro.data.repository

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.onDownload
import io.ktor.client.request.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

// ── Result model ──────────────────────────────────────────────────────────────

data class DictionaryResult(
    val word: String,
    val phonetic: String? = null,
    val meanings: List<Meaning>
) {
    data class Meaning(
        val partOfSpeech: String,
        val definitions: List<String>,
        val example: String? = null,
        val synonyms: List<String> = emptyList()
    )
}

sealed class DictionaryState {
    object Idle : DictionaryState()
    object Loading : DictionaryState()
    data class Success(val result: DictionaryResult) : DictionaryState()
    object NotFound : DictionaryState()
    object NotDownloaded : DictionaryState()  // Dictionary not yet downloaded
    data class Error(val message: String) : DictionaryState()
}

sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(val progress: Float, val bytesDownloaded: Long = 0L) : DownloadState()
    object Done : DownloadState()
    data class Error(val message: String) : DownloadState()
}

// ── Repository ────────────────────────────────────────────────────────────────

class DictionaryRepository(
    private val context: Context,
    private val httpClient: HttpClient
) {
    companion object {
        // Webster's Dictionary as SQLite - public domain, reliable schema
        // Hosted on GitHub releases - ~10MB
        private const val DICT_DOWNLOAD_URL =
            "https://github.com/johnpena/offline-dict/raw/master/db/WordNet_3.1.db"
        private const val DB_FILE = "kosh_dictionary.db"
    }

    private val dbFile get() = File(context.filesDir, DB_FILE)
    val isOfflineAvailable get() = dbFile.exists() && dbFile.length() > 100_000L

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: Flow<DownloadState> = _downloadState.asStateFlow()

    // ── Lookup — offline only ─────────────────────────────────────────────────

    suspend fun lookup(word: String): DictionaryState = withContext(Dispatchers.IO) {
        if (!isOfflineAvailable) return@withContext DictionaryState.NotDownloaded

        val clean = word.trim().lowercase().replace(Regex("[^a-z'-]"), "")
        if (clean.isBlank()) return@withContext DictionaryState.NotFound

        return@withContext lookupOffline(clean)
    }

    private fun lookupOffline(word: String): DictionaryState {
        return try {
            SQLiteDatabase.openDatabase(
                dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY
            ).use { db ->
                // Try multiple common schemas used by open dictionary SQLite files
                val schemas = listOf(
                    Triple("entries", "word", "definition"),
                    Triple("words",   "word", "definition"),
                    Triple("dictionary", "word", "definition"),
                    Triple("words",   "word", "meaning"),
                    Triple("wordnet", "lemma", "definition")
                )
                for ((table, wordCol, defCol) in schemas) {
                    val result = tryQuery(db, word, table, wordCol, defCol)
                    if (result != null) return DictionaryState.Success(result)
                }
                DictionaryState.NotFound
            }
        } catch (e: Exception) {
            Timber.e(e, "Offline lookup failed: $word")
            DictionaryState.Error("Dictionary error: ${e.message}")
        }
    }

    private fun tryQuery(
        db: SQLiteDatabase, word: String,
        table: String, wordCol: String, defCol: String
    ): DictionaryResult? {
        return try {
            db.rawQuery(
                "SELECT $defCol FROM `$table` WHERE $wordCol = ? LIMIT 1",
                arrayOf(word)
            ).use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) {
                    val raw = cursor.getString(0).trim()
                    if (raw.isBlank()) return null
                    // Split multi-definition strings by semicolon or newline
                    val defs = raw.split(Regex("[;\\n]"))
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                        .take(5)
                    DictionaryResult(
                        word = word,
                        meanings = listOf(
                            DictionaryResult.Meaning(
                                partOfSpeech = "",
                                definitions = defs
                            )
                        )
                    )
                } else null
            }
        } catch (e: Exception) { null }
    }

    // ── Download ──────────────────────────────────────────────────────────────

    suspend fun downloadDictionary() = withContext(Dispatchers.IO) {
        if (_downloadState.value is DownloadState.Downloading) return@withContext
        _downloadState.value = DownloadState.Downloading(0f, 0L)

        val tempFile = File(context.filesDir, "dict_temp.db")
        try {
            val bytes: ByteArray = httpClient.get(DICT_DOWNLOAD_URL) {
                onDownload { bytesSent, contentLength ->
                    val progress = if (contentLength != null && contentLength > 0)
                        bytesSent.toFloat() / contentLength.toFloat() else 0f
                    _downloadState.value = DownloadState.Downloading(progress, bytesSent)
                }
            }.body()

            tempFile.writeBytes(bytes)

            if (tempFile.length() > 100_000L) {
                if (dbFile.exists()) dbFile.delete()
                tempFile.renameTo(dbFile)
                _downloadState.value = DownloadState.Done
            } else {
                tempFile.delete()
                _downloadState.value = DownloadState.Error("Download incomplete — try again")
            }
        } catch (e: Exception) {
            tempFile.delete()
            Timber.e(e, "Dictionary download failed")
            _downloadState.value = DownloadState.Error("Download failed: check your internet connection")
        }
    }

    fun deleteDictionary() {
        dbFile.delete()
        _downloadState.value = DownloadState.Idle
    }

    fun resetState() {
        if (_downloadState.value !is DownloadState.Downloading) {
            _downloadState.value = DownloadState.Idle
        }
    }
}
