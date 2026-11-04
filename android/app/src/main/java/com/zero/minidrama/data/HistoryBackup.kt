package com.zero.minidrama.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

/**
 * Copies watch history to app-private storage and Downloads/ZERO so it can survive APK reinstall.
 */
object HistoryBackup {
    private const val INTERNAL_NAME = "zero_watch_history_backup.json"
    private const val PUBLIC_NAME = "zero_watch_history.json"
    private const val PUBLIC_DIR = "ZERO"
    private val gson = Gson()
    private val listType = object : TypeToken<List<HistoryItem>>() {}.type

    fun write(context: Context, items: List<HistoryItem>) {
        val json = gson.toJson(items)
        runCatching {
            context.openFileOutput(INTERNAL_NAME, Context.MODE_PRIVATE).use {
                it.write(json.toByteArray(Charsets.UTF_8))
            }
        }
        runCatching { writePublicCopy(context, json) }
    }

    fun load(context: Context): List<HistoryItem> {
        readInternal(context)?.let { if (it.isNotEmpty()) return it }
        return readPublic(context).orEmpty()
    }

    fun publicBackupHint(): String = "Indirilenler/$PUBLIC_DIR/$PUBLIC_NAME"

    private fun readInternal(context: Context): List<HistoryItem>? {
        return runCatching {
            context.openFileInput(INTERNAL_NAME).bufferedReader().use { it.readText() }
        }.getOrNull()?.let(::parseList)?.takeIf { it.isNotEmpty() }
    }

    private fun parseList(raw: String): List<HistoryItem> =
        runCatching { gson.fromJson<List<HistoryItem>>(raw, listType) }.getOrDefault(emptyList())

    private fun writePublicCopy(context: Context, json: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            findPublicUri(context)?.let { existing ->
                resolver.delete(existing, null, null)
            }
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, PUBLIC_NAME)
                put(MediaStore.Downloads.MIME_TYPE, "application/json")
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$PUBLIC_DIR")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val uri = resolver.insert(collection, values) ?: return
            resolver.openOutputStream(uri)?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } else {
            @Suppress("DEPRECATION")
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                PUBLIC_DIR
            )
            if (!dir.exists()) dir.mkdirs()
            File(dir, PUBLIC_NAME).writeText(json, Charsets.UTF_8)
        }
    }

    private fun findPublicUri(context: Context): Uri? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val projection = arrayOf(MediaStore.Downloads._ID)
            val selection = "${MediaStore.Downloads.DISPLAY_NAME}=?"
            val args = arrayOf(PUBLIC_NAME)
            context.contentResolver.query(collection, projection, selection, args, null)?.use { c ->
                if (c.moveToFirst()) {
                    val id = c.getLong(0)
                    return ContentUris.withAppendedId(collection, id)
                }
            }
        } else {
            @Suppress("DEPRECATION")
            val file = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "$PUBLIC_DIR/$PUBLIC_NAME"
            )
            if (file.isFile) return Uri.fromFile(file)
        }
        return null
    }

    private fun readPublic(context: Context): List<HistoryItem>? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val uri = findPublicUri(context) ?: return null
            val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: return null
            return parseList(raw).takeIf { it.isNotEmpty() }
        }
        @Suppress("DEPRECATION")
        val file = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "$PUBLIC_DIR/$PUBLIC_NAME"
        )
        if (!file.isFile) return null
        return parseList(file.readText()).takeIf { it.isNotEmpty() }
    }
}
