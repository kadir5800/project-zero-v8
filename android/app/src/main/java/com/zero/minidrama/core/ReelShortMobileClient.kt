package com.zero.minidrama.core

import android.content.Context
import android.os.Build
import android.util.Log
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * ReelShort Android app backend (v-api.crazymaplestudios.com) for locked episodes.
 */
class ReelShortMobileClient(private val context: Context) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    fun playUrl(bookId: String, chapterId: String, lang: String): String? {
        lastDiagnostic = ""
        val s = ensureLoggedIn(lang)
        requestAdvUnlock(s, bookId, chapterId)
        val content = getChapterContent(s, bookId, chapterId) ?: return null
        val url = extractPlayUrl(content)
        if (url.isNullOrBlank()) {
            appendDiag("play_info/video_url bos (status=${content.optInt("status")} lock=${content.optInt("is_lock")})")
        }
        return url
    }

    private fun ensureLoggedIn(lang: String): ReelShortMobileSession {
        val s = ReelShortMobileSession.load(context, lang)
        if (s.uid > 0L && s.session.isNotBlank()) return s
        guestLogin(s)
        return s
    }

    private fun guestLogin(s: ReelShortMobileSession) {
        val fields = linkedMapOf(
            "uname" to "",
            "sid" to "0",
            "openid" to s.devId,
            "dev_model" to s.devModel(),
            "pic" to "",
            "email" to "",
            "network_operator" to "",
            "login_mode" to "1",
            "time_zone" to s.timeZone(),
            "reinstall" to "0"
        )
        ReelShortMobileSession.appendDeviceTelemetry(context, fields)
        val json = postForm(s, "api/video/user/userLogin", fields)
            ?: throw IllegalStateException("ReelShort giris basarisiz (ag)")
        val code = json.optInt("code", -1)
        if (code != 0) {
            val msg = json.optString("msg", "code $code")
            appendDiag("userLogin: $msg")
            throw IllegalStateException(msg)
        }
        val data = json.optJSONObject("data") ?: throw IllegalStateException("ReelShort giris yaniti bos")
        s.session = data.optString("session")
        val userInfo = data.optJSONObject("user_info")
        s.uid = userInfo?.optLong("uid") ?: data.optLong("uid", 0L)
        if (s.uid <= 0L || s.session.isBlank()) {
            appendDiag("userLogin: oturum parse edilemedi")
            throw IllegalStateException("ReelShort oturum alinamadi")
        }
        s.persist(context)
        appendDiag("userLogin ok uid=${s.uid}")
        Log.i(TAG, "userLogin ok uid=${s.uid}")
    }

    private fun requestAdvUnlock(s: ReelShortMobileSession, bookId: String, chapterId: String) {
        val fields = mapOf(
            "book_id" to bookId,
            "chapter_id" to chapterId
        )
        val json = postForm(s, "api/video/book/requestAdvUnlock", fields)
        if (json == null) {
            appendDiag("requestAdvUnlock: HTTP hata")
            return
        }
        val code = json.optInt("code", -1)
        if (code != 0) {
            appendDiag("requestAdvUnlock: ${json.optString("msg", "code $code")}")
        }
    }

    private fun getChapterContent(s: ReelShortMobileSession, bookId: String, chapterId: String): JSONObject? {
        val fields = linkedMapOf(
            "book_id" to bookId,
            "chapter_id" to chapterId,
            "isHandPay" to "0",
            "isAutoUnlock" to "0",
            "isWaitFreeUnlock" to "0",
            "isAdvUnlock" to "1",
            "isAccountBind" to "0",
            "isFirstloadChapter" to "1"
        )
        val json = postForm(s, "api/video/book/getChapterContent", fields) ?: return null
        val code = json.optInt("code", -1)
        if (code != 0) {
            val msg = json.optString("msg", "code $code")
            appendDiag("getChapterContent: $msg")
            if (code == 101 || msg.contains("Param", ignoreCase = true) || msg.contains("sign", ignoreCase = true)) {
                s.clearSession(context)
            }
            return null
        }
        return json.optJSONObject("data")
    }

    private fun postForm(
        s: ReelShortMobileSession,
        path: String,
        fields: Map<String, String>
    ): JSONObject? {
        val url = BASE + path.trimStart('/')
        val headers = runCatching {
            ReelShortVictorSign.signedHeaders(context, s, fields)
        }.getOrElse { e ->
            appendDiag("sign: ${e.message}")
            throw e
        }
        val body = FormBody.Builder().apply {
            fields.forEach { (k, v) -> add(k, v) }
        }.build()
        val req = Request.Builder()
            .url(url)
            .post(body)
            .header("Accept", "application/json")
            .header("User-Agent", userAgent())
            .apply { headers.forEach { (k, v) -> header(k, v) } }
            .build()
        return runCatching {
            http.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    appendDiag("$path HTTP ${resp.code}: ${text.take(120)}")
                    return@use null
                }
                JSONObject(text)
            }
        }.getOrElse { e ->
            appendDiag("$path: ${e.message}")
            null
        }
    }

    private fun extractPlayUrl(data: JSONObject): String? {
        val playInfoEnc = data.optString("play_info")
        if (playInfoEnc.isNotBlank()) {
            val plain = ReelShortVictorSign.decryptPlayInfo(playInfoEnc)
            if (plain.isBlank()) appendDiag("play_info cozulemedi")
            pickFromPlayInfoJson(plain)?.let { return it }
        }
        data.optString("video_url").takeIf { it.isNotBlank() }?.let { return it }
        data.optString("play_url").takeIf { it.isNotBlank() }?.let { return it }
        return null
    }

    private fun pickFromPlayInfoJson(json: String): String? {
        if (json.isBlank()) return null
        return runCatching {
            when {
                json.trimStart().startsWith("[") -> {
                    val arr = JSONArray(json)
                    var bestUrl: String? = null
                    var bestDpi = -1
                    for (i in 0 until arr.length()) {
                        val item = arr.optJSONObject(i) ?: continue
                        val url = item.optString("PlayURL").ifBlank { item.optString("play_url") }
                        if (url.isBlank()) continue
                        val dpi = item.optInt("Dpi", item.optInt("dpi", 0))
                        if (dpi >= bestDpi) {
                            bestDpi = dpi
                            bestUrl = url
                        }
                    }
                    bestUrl ?: arr.optJSONObject(0)?.optString("PlayURL")
                }
                json.trimStart().startsWith("{") -> {
                    val obj = JSONObject(json)
                    obj.optString("PlayURL").ifBlank { obj.optString("play_url") }.takeIf { it.isNotBlank() }
                }
                else -> null
            }
        }.getOrNull()
    }

    private fun appendDiag(line: String) {
        lastDiagnostic = if (lastDiagnostic.isEmpty()) line else "$lastDiagnostic | $line"
        Log.w(TAG, line)
    }

    companion object {
        private const val TAG = "ReelShortMobile"
        private const val BASE = "https://v-api.crazymaplestudios.com/"
        @Volatile
        var lastDiagnostic: String = ""

        private fun userAgent(): String =
            "ReelShort/4.2.20 (Linux; Android ${Build.VERSION.SDK_INT}; ${Build.MODEL})"
    }
}
