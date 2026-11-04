package com.zero.minidrama.core

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.SecureRandom

class NetShortClient {
    private val baseUrl = "https://appsecapi.netshort.com"
    private val appVer = "2.0.3"
    @Volatile
    var token: String? = null
    val deviceCode: String = randomHex(8)
    val userAgent: String = buildUserAgent()

    private val langMap = mapOf("tr" to "tr_TR", "en" to "en_US")

    suspend fun ensureLogin(lang: String, force: Boolean = false) {
        if (!force && token != null) return
        token = null
        val payload = JSONObject().apply {
            put("os", "Android")
            put("appVer", appVer)
            put("identity", 0)
            put("model", "Pixel 6")
            put("deviceCode", deviceCode)
            put("source", "visitor")
            put("osVer", "12")
        }
        val result = apiCall("/prod-app-api/auth/login", payload, lang, loginHeaders = true, allowRetry = false)
        token = result.optJSONObject("data")?.optString("token")?.takeIf { it.isNotBlank() }
        if (token == null) {
            throw IllegalStateException(result.optString("msg").ifBlank { "NetShort login basarisiz" })
        }
    }

    companion object {
        val BROWSE_SEEDS = mapOf(
            "tr" to listOf("aşk", "intikam", "gizli", "ceo", "aile", "yeniden"),
            "en" to listOf("love", "revenge", "ceo", "secret", "family", "billionaire")
        )
    }

    fun search(q: String, page: Int, lang: String, category: String = ""): JSONObject {
        val seeds = BROWSE_SEEDS[lang] ?: BROWSE_SEEDS.getValue("en")
        val keyword = q.trim().ifBlank {
            category.trim().ifBlank { seeds.first() }
        }
        val payload = JSONObject().apply {
            put("id", 0)
            put("shortPlayId", 0)
            put("searchCode", keyword)
            put("pageNo", page)
            put("pageSize", 20)
            put("searchFlag", 1)
        }
        return apiCall("/prod-app-api/video/shortPlay/search/searchByKeyword", payload, lang)
    }

    fun categoryChips(lang: String): List<Pair<String, String>> {
        val seeds = BROWSE_SEEDS[lang] ?: BROWSE_SEEDS.getValue("en")
        val allLabel = if (lang == "tr") "Tümü" else "All"
        return listOf("" to allLabel) + seeds.map { it to it.replaceFirstChar { c -> c.uppercase() } }
    }

    fun bookDetail(bookId: String, lang: String): JSONObject {
        val payload = JSONObject().apply {
            put("codec", "")
            put("playClarity", "1080p")
            putId("shortPlayId", bookId)
        }
        return apiCall("/prod-app-api/video/shortPlay/base/detail_info/V2", payload, lang)
    }

    fun episodeVideo(dramaId: String, episodeId: String, episodeNo: Int, lang: String, isLock: Boolean): String {
        if (isLock) {
            val unlock = JSONObject().apply {
                putId("shortPlayId", dramaId)
                putId("shortPlayEpisodeId", episodeId)
                put("shortPlayEpisodeNo", episodeNo)
            }
            apiCall("/prod-app-api/user/shortPlay/userBase/unlock_free_ad_episode", unlock, lang)
        }
        val payload = JSONObject().apply {
            putId("shortPlayId", dramaId)
            putId("episodeId", episodeId)
            put("episodeNo", episodeNo)
            put("episodeType", 1)
            put("clarity", "1080p")
        }
        val result = apiCall("/prod-app-api/video/shortPlay/base/episode/detail_info", payload, lang)
        if (result.optInt("code", 200) !in listOf(0, 200) && result.optJSONObject("data") == null) {
            throw IllegalStateException(result.optString("msg").ifBlank { "NetShort bolum hatasi" })
        }
        val list = result.optJSONObject("data")?.optJSONArray("episodeList")
            ?: throw IllegalStateException("Bolum bulunamadi")
        for (i in 0 until list.length()) {
            val ep = list.getJSONObject(i)
            if (ep.optInt("episodeNo") == episodeNo) {
                return ep.optString("playVoucher").takeIf { it.isNotBlank() }
                    ?: throw IllegalStateException("Video URL yok")
            }
        }
        val first = list.getJSONObject(0)
        return first.optString("playVoucher").takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Video URL yok")
    }

    private fun apiCall(
        endpoint: String,
        payload: JSONObject,
        lang: String,
        loginHeaders: Boolean = false,
        allowRetry: Boolean = true
    ): JSONObject {
        val encrypted = NetShortCrypto.encryptPayload(payload)
        val builder = Request.Builder()
            .url(baseUrl + endpoint)
            .post(encrypted.bodyB64.toRequestBody("application/json".toMediaType()))
            .header("Host", "appsecapi.netshort.com")
            .header("Canary", "v2")
            .header("Os", "1")
            .header("Version", appVer)
            .header("Encrypt-Key", encrypted.headerKeyB64)
            .header("Device-Code", deviceCode)
            .header("Content-Type", "application/json")
            .header("Content-Language", langMap[lang] ?: "tr_TR")
            .header("User-Agent", userAgent)
            .header("Timestamp", System.currentTimeMillis().toString())
            .header("Connection", "Keep-Alive")
        // Do not set Accept-Encoding manually; OkHttp manages gzip safely.
        token?.let { builder.header("Authorization", "Bearer $it") }
        if (loginHeaders) {
            builder.header("Start_type", "cold")
            builder.header("Network", "wifi,cold,true")
            builder.header("Push_switch", "true")
        }

        return try {
            HttpClient.client.newCall(builder.build()).execute().use { resp ->
                val body = resp.body?.string()?.trim().orEmpty()
                val serverKey = resp.header("encrypt-key")
                    ?: resp.header("Encrypt-Key")
                if (serverKey.isNullOrBlank()) {
                    if (body.startsWith("{") || body.startsWith("[")) {
                        val plain = JSONObject(body)
                        return@use handleAuthRetry(plain, endpoint, payload, lang, loginHeaders, allowRetry)
                    }
                    throw IllegalStateException("Encrypt-Key missing HTTP ${resp.code}")
                }
                val decrypted = NetShortCrypto.decryptResponse(serverKey, body)
                handleAuthRetry(decrypted, endpoint, payload, lang, loginHeaders, allowRetry)
            }
        } catch (e: Exception) {
            if (allowRetry && !loginHeaders && isDecryptOrAuthError(e)) {
                token = null
                // Blocking re-login then retry once
                val loginPayload = JSONObject().apply {
                    put("os", "Android")
                    put("appVer", appVer)
                    put("identity", 0)
                    put("model", "Pixel 6")
                    put("deviceCode", deviceCode)
                    put("source", "visitor")
                    put("osVer", "12")
                }
                val login = apiCall("/prod-app-api/auth/login", loginPayload, lang, loginHeaders = true, allowRetry = false)
                token = login.optJSONObject("data")?.optString("token")?.takeIf { it.isNotBlank() }
                if (token == null) throw IllegalStateException("NetShort yeniden login basarisiz")
                return apiCall(endpoint, payload, lang, loginHeaders = false, allowRetry = false)
            }
            throw e
        }
    }

    private fun handleAuthRetry(
        result: JSONObject,
        endpoint: String,
        payload: JSONObject,
        lang: String,
        loginHeaders: Boolean,
        allowRetry: Boolean
    ): JSONObject {
        val code = result.optInt("code", 200)
        if (allowRetry && !loginHeaders && (code == 401 || code == 403)) {
            token = null
            val loginPayload = JSONObject().apply {
                put("os", "Android")
                put("appVer", appVer)
                put("identity", 0)
                put("model", "Pixel 6")
                put("deviceCode", deviceCode)
                put("source", "visitor")
                put("osVer", "12")
            }
            val login = apiCall("/prod-app-api/auth/login", loginPayload, lang, loginHeaders = true, allowRetry = false)
            token = login.optJSONObject("data")?.optString("token")?.takeIf { it.isNotBlank() }
            if (token == null) throw IllegalStateException("NetShort yeniden login basarisiz")
            return apiCall(endpoint, payload, lang, loginHeaders = false, allowRetry = false)
        }
        return result
    }

    private fun isDecryptOrAuthError(e: Exception): Boolean {
        val msg = e.message.orEmpty()
        return msg.contains("bad base-64", ignoreCase = true) ||
            msg.contains("bad base64", ignoreCase = true) ||
            msg.contains("cozulemedi", ignoreCase = true) ||
            msg.contains("sifre", ignoreCase = true) ||
            msg.contains("AES", ignoreCase = true) ||
            msg.contains("Encrypt-Key", ignoreCase = true)
    }

    /** NetShort expects numeric ids when possible (large snowflake ids). */
    private fun JSONObject.putId(key: String, value: String) {
        val trimmed = value.trim()
        val asLong = trimmed.toLongOrNull()
        if (asLong != null) put(key, asLong) else put(key, trimmed)
    }

    private fun randomHex(len: Int): String {
        val bytes = ByteArray(len)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun buildUserAgent(): String {
        val models = listOf("Pixel 6", "Pixel 7", "Galaxy S21", "Redmi Note 11", "Pixel 8 Pro")
        val major = (85..99).random()
        val build = (1000..9000).random()
        return "Mozilla/5.0 (Linux; Android 12; ${models.random()} Build/SP1A.210812.015; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/$major.0.$build.120 Mobile Safari/537.36"
    }
}
