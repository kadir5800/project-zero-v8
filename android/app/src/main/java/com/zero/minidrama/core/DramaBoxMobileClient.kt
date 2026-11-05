package com.zero.minidrama.core

import android.content.Context
import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * DramaBox Android API (sapi.dramaboxdb.com) for episodes where the web player omits mp4 (paywall).
 */
class DramaBoxMobileClient(private val context: Context) {
    fun playUrl(
        bookId: String,
        chapterId: String,
        episodeIndex: Int,
        lang: String
    ): String? {
        lastDiagnostic = ""
        if (bookId.isBlank() || chapterId.isBlank() || episodeIndex < 1) {
            appendDiag("gecersiz parametre")
            return null
        }
        val session = ensureToken(lang)
        var payNum = session.payChapterByBook[bookId] ?: 0
        if (payNum <= 0) {
            val meta = batchLoad(session, bookId, index = 1, enterReaderIndex = 0, refreshTokenOnFail = true)
            payNum = meta?.optInt("payChapterNum", 0) ?: 0
            if (payNum > 0) session.payChapterByBook[bookId] = payNum
        }
        if (payNum > 0 && episodeIndex > payNum) {
            batchLoad(session, bookId, index = payNum, enterReaderIndex = (payNum - 1).coerceAtLeast(0), refreshTokenOnFail = false)
        }

        val idx = episodeIndex.coerceAtLeast(1)
        val data = batchLoad(
            session,
            bookId,
            index = idx,
            enterReaderIndex = (idx - 1).coerceAtLeast(0),
            refreshTokenOnFail = true
        ) ?: run {
            appendDiag("batch/load bos")
            return null
        }
        val list = data.optJSONArray("chapterList") ?: JSONArray()
        for (i in 0 until list.length()) {
            val ch = list.getJSONObject(i)
            if (ch.optString("chapterId") == chapterId || ch.optInt("chapterIndex") == idx) {
                val url = extractVideoPath(ch)
                if (!url.isNullOrBlank()) {
                    Log.i(TAG, "mobile OK book=$bookId ep=$idx")
                    return url
                }
            }
        }
        appendDiag("cdnList bos (bolum $idx, ${list.length()} kayit)")
        return null
    }

    private fun ensureToken(lang: String): DramaBoxMobileSession {
        val session = DramaBoxMobileSession.load(context, lang)
        if (session.isValid()) return session
        bootstrap(session)
        session.persist(context)
        return session
    }

    private fun bootstrap(session: DramaBoxMobileSession) {
        val timestamp = System.currentTimeMillis()
        val body = """{"distinctId":null}"""
        val signPayload = "timestamp=$timestamp$body${session.deviceId}${session.androidId}"
        val req = Request.Builder()
            .url("$BASE/drama-box/ap001/bootstrap?timestamp=$timestamp")
            .post(body.toRequestBody(JSON))
            .header("Content-Type", "application/json; charset=UTF-8")
            .header("User-Agent", USER_AGENT)
            .header("version", VERSION_CODE)
            .header("vn", VERSION_NAME)
            .header("cid", CID)
            .header("package-Name", PACKAGE)
            .header("Apn", "1")
            .header("device-id", session.deviceId)
            .header("language", session.lang)
            .header("current-Language", session.lang)
            .header("p", P_CODE)
            .header("Time-Zone", "+0700")
            .header("md", "Redmi Note 8")
            .header("ov", "14")
            .header("over-flow", "new-fly")
            .header("android-id", session.androidId)
            .header("X-Forwarded-For", session.spoffer)
            .header("X-Real-IP", session.spoffer)
            .header("mf", "XIAOMI")
            .header("brand", "Xiaomi")
            .header("sn", DramaBoxSign.sign(context, signPayload))
            .build()

        HttpClient.client.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                appendDiag("bootstrap HTTP ${resp.code}: ${text.take(80)}")
                throw IllegalStateException("DramaBox mobil giris HTTP ${resp.code}")
            }
            val json = JSONObject(text)
            if (json.optBoolean("success", true) == false) {
                appendDiag("bootstrap: ${json.optString("message")}")
                throw IllegalStateException(json.optString("message", "bootstrap basarisiz"))
            }
            val user = json.optJSONObject("data")?.optJSONObject("user")
                ?: throw IllegalStateException("DramaBox bootstrap yaniti gecersiz")
            session.token = user.optString("token")
            session.uid = user.optString("uid")
            session.expiryMs = System.currentTimeMillis() + 24 * 60 * 60_000L
            if (session.token.isBlank()) throw IllegalStateException("DramaBox token bos")
            appendDiag("bootstrap ok")
        }
    }

    private fun batchLoad(
        session: DramaBoxMobileSession,
        bookId: String,
        index: Int,
        enterReaderIndex: Int,
        refreshTokenOnFail: Boolean
    ): JSONObject? {
        val payload = JSONObject().apply {
            put("boundaryIndex", 0)
            put("comingPlaySectionId", -1)
            put("index", index)
            put("currencyPlaySource", "discover_new_rec_new")
            put("needEndRecommend", 0)
            put("currencyPlaySourceName", "")
            put("preLoad", false)
            put("rid", "")
            put("pullCid", "")
            put("loadDirection", 0)
            put("bookId", bookId)
            put("enterReaderChapterIndex", enterReaderIndex)
        }
        return postSigned(session, "/drama-box/chapterv2/batch/load", payload, refreshTokenOnFail)
            ?.optJSONObject("data")
    }

    private fun postSigned(
        session: DramaBoxMobileSession,
        path: String,
        payload: JSONObject,
        refreshTokenOnFail: Boolean
    ): JSONObject? {
        fun doPost(): JSONObject? {
            val timestamp = System.currentTimeMillis()
            val body = payload.toString()
            val tn = "Bearer ${session.token}"
            val signPayload = "timestamp=$timestamp$body${session.deviceId}${session.androidId}$tn"
            val req = Request.Builder()
                .url("$BASE$path?timestamp=$timestamp")
                .post(body.toRequestBody(JSON))
                .header("Content-Type", "application/json; charset=UTF-8")
                .header("User-Agent", USER_AGENT)
                .header("tn", tn)
                .header("version", VERSION_CODE)
                .header("vn", VERSION_NAME)
                .header("cid", CID)
                .header("package-Name", PACKAGE)
                .header("Apn", "1")
                .header("device-id", session.deviceId)
                .header("language", session.lang)
                .header("current-Language", session.lang)
                .header("p", P_CODE)
                .header("Time-Zone", "+0700")
                .header("local-time", localTimeBangkok())
                .header("md", "Redmi Note 8")
                .header("ov", "14")
                .header("over-flow", "new-fly")
                .header("android-id", session.androidId)
                .header("X-Forwarded-For", session.spoffer)
                .header("X-Real-IP", session.spoffer)
                .header("mf", "XIAOMI")
                .header("brand", "Xiaomi")
                .header("userid", session.uid)
                .header("sn", DramaBoxSign.sign(context, signPayload))
                .build()
            return HttpClient.client.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    appendDiag("$path HTTP ${resp.code}")
                    return@use null
                }
                runCatching { JSONObject(text) }.getOrNull()?.also { json ->
                    if (json.optBoolean("success", true) == false) {
                        appendDiag(json.optString("message", "API hata"))
                    }
                }
            }?.takeIf { it.optBoolean("success", true) != false }
        }

        var json = doPost()
        if (json == null && refreshTokenOnFail) {
            session.clear(context)
            bootstrap(session)
            session.persist(context)
            json = doPost()
        }
        return json
    }

    private fun extractVideoPath(chapter: JSONObject): String? {
        val cdnList = chapter.optJSONArray("cdnList") ?: return null
        var cdn: JSONObject? = null
        for (i in 0 until cdnList.length()) {
            val c = cdnList.getJSONObject(i)
            if (c.optInt("isDefault") == 1) {
                cdn = c
                break
            }
        }
        if (cdn == null && cdnList.length() > 0) cdn = cdnList.getJSONObject(0)
        val paths = cdn?.optJSONArray("videoPathList") ?: return null
        val candidates = buildList {
            for (i in 0 until paths.length()) add(paths.getJSONObject(i))
        }
        val picked = candidates.firstOrNull { it.optInt("isDefault") == 1 }
            ?: candidates.firstOrNull { it.optInt("quality") == 720 }
            ?: candidates.firstOrNull { it.optInt("quality") == 1080 }
            ?: candidates.firstOrNull()
        val path = picked?.optString("videoPath").orEmpty()
        return path.takeIf { it.isNotBlank() && !path.equals("N/A", ignoreCase = true) }
    }

    private fun localTimeBangkok(): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("GMT+7")
        return fmt.format(Date())
    }

    private fun appendDiag(msg: String) {
        lastDiagnostic = if (lastDiagnostic.isBlank()) msg else "$lastDiagnostic; $msg"
    }

    companion object {
        private const val TAG = "DramaBoxMobile"
        private const val BASE = "https://sapi.dramaboxdb.com"
        private const val PACKAGE = "com.storymatrix.drama"
        private const val CID = "DALPF1068349"
        private const val VERSION_CODE = "492"
        private const val VERSION_NAME = "4.9.2"
        private const val P_CODE = "51"
        private const val USER_AGENT = "okhttp/4.10.0"
        private val JSON = "application/json; charset=UTF-8".toMediaType()

        @Volatile
        var lastDiagnostic: String = ""
            private set
    }
}
