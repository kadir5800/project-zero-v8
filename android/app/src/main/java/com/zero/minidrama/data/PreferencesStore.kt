package com.zero.minidrama.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("zero_prefs")

class PreferencesStore(private val context: Context) {
    private val gson = Gson()
    private val historyType = object : TypeToken<List<HistoryItem>>() {}.type

    val platform: Flow<String> = context.dataStore.data.map {
        it[KEY_PLATFORM] ?: "reelshort"
    }

    val lang: Flow<String> = context.dataStore.data.map {
        it[KEY_LANG] ?: "tr"
    }

    val history: Flow<List<HistoryItem>> = context.dataStore.data.map { prefs ->
        val raw = prefs[KEY_HISTORY] ?: "[]"
        runCatching { gson.fromJson<List<HistoryItem>>(raw, historyType) }.getOrDefault(emptyList())
    }

    suspend fun setPlatform(value: String) {
        context.dataStore.edit { it[KEY_PLATFORM] = value }
    }

    suspend fun setLang(value: String) {
        context.dataStore.edit { it[KEY_LANG] = value }
    }

    suspend fun addHistory(item: HistoryItem) {
        context.dataStore.edit { prefs ->
            val current = parseHistoryJson(prefs[KEY_HISTORY] ?: "[]")
            val updated = mergeHistoryItem(current, item)
            prefs[KEY_HISTORY] = gson.toJson(updated)
            HistoryBackup.write(context, updated)
        }
    }

    suspend fun restoreFromBackupIfEmpty(): Int {
        if (history.first().isNotEmpty()) return 0
        return restoreFromBackup(force = false)
    }

    /** @return restored item count, or 0 if nothing to restore */
    suspend fun restoreFromBackup(force: Boolean): Int {
        val backup = HistoryBackup.load(context)
        if (backup.isEmpty()) return 0
        if (!force && history.first().isNotEmpty()) return 0
        persistHistory(backup.take(40))
        return backup.size.coerceAtMost(40)
    }

    suspend fun clearHistory() {
        persistHistory(emptyList())
    }

    private suspend fun persistHistory(items: List<HistoryItem>) {
        context.dataStore.edit { it[KEY_HISTORY] = gson.toJson(items) }
        HistoryBackup.write(context, items)
    }

    private fun parseHistoryJson(raw: String): List<HistoryItem> =
        runCatching { gson.fromJson<List<HistoryItem>>(raw, historyType) }.getOrDefault(emptyList())

    private fun mergeHistoryItem(current: List<HistoryItem>, item: HistoryItem): List<HistoryItem> =
        listOf(item) + current.filterNot {
            it.bookId == item.bookId && it.platform == item.platform
        }.take(40)

    companion object {
        private val KEY_PLATFORM = stringPreferencesKey("platform")
        private val KEY_LANG = stringPreferencesKey("lang")
        private val KEY_HISTORY = stringPreferencesKey("history")
    }
}
