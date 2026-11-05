package com.zero.minidrama.core

import android.content.Context
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class StoryReelClient(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private var authToken: String? = prefs.getString(KEY_AUTH, null)
    private var apiRc4Key: String = prefs.getString(KEY_RC4, null) ?: StoryReelRc4.newApiKey().also {
        prefs.edit().putString(KEY_RC4, it).apply()
    }
    private var deviceId: String = prefs.getString(KEY_DEVICE, null)
        ?: UUID.randomUUID().toString().also { prefs.edit().putString(KEY_DEVICE, it).apply() }

    private val langMap = mapOf("tr" to Pair("tr", "TR"), "en" to Pair("en", "US"))

    fun ensureLogin(lang: String) {
        if (!authToken.isNullOrBlank()) return
        val seq = UUID.randomUUID().toString()
        val secretKey = StoryReelRc4.rsaSecretKey(apiRc4Key)
        val androidVer = "12"
        // Official app uses @FormUrlEncoded in Retrofit, but ApiMiddleware rewrites FormBody → JSON.
        val formValueConcat = deviceId + seq + secretKey
        val traceParam = StoryReelRc4.encryptToHex(
            StoryReelRc4.md5Hex(formValueConcat + androidVer),
            apiRc4Key,
        )
        val json = apiCall(
            "prod-api/16/app/login/v4/initLogin",
            mapOf("deviceCode" to deviceId, "seq" to seq, "secretKey" to secretKey),
            encrypt = false,
            extraHeaders = mapOf("TraceParam" to traceParam),
            lang = lang,
        )
        val data = json.optJSONObject("data") ?: throw IllegalStateException("StoryReel giris basarisiz")
        authToken = data.optString("token").takeIf { it.isNotBlank() }
            ?: data.optJSONObject("userResponse")?.optString("token")?.takeIf { it.isNotBlank() }
        if (authToken.isNullOrBlank()) throw IllegalStateException("StoryReel token alinamadi")
        prefs.edit().putString(KEY_AUTH, authToken).apply()
    }

    fun search(q: String, page: Int, lang: String): Pair<List<JSONObject>, Int> {
        ensureLogin(lang)
        val keyword = q.trim()
        if (keyword.isEmpty()) {
            val home = apiCall(
                "prod-api/16/app/hiShortPlay/seriesHomePage",
                emptyMap(),
                lang = lang,
            )
            val list = home.optJSONObject("data")?.optJSONArray("shortPlayList")
                ?: home.optJSONArray("data")
                ?: JSONArray()
            return flattenShorts(list) to 1
        }
        val res = apiCall(
            "prod-api/16/app/search/searchPage",
            mapOf(
                "pageNum" to page,
                "pageSize" to 20,
                "searchText" to keyword,
            ),
            lang = lang,
        )
        val data = res.optJSONObject("data")
        val arr = when {
            data == null -> res.optJSONArray("data") ?: JSONArray()
            else -> data.optJSONArray("shortPlayResponseList")
                ?: data.optJSONArray("list")
                ?: JSONArray()
        }
        val total = (data?.optInt("total", arr.length()) ?: arr.length()).coerceAtLeast(arr.length())
        val pages = ((total + 19) / 20).coerceAtLeast(1)
        return flattenShorts(arr) to pages
    }

    fun bookDetail(shortPlayId: String, lang: String): JSONObject {
        ensureLogin(lang)
        val id = shortPlayId.toIntOrNull() ?: throw IllegalStateException("Gecersiz dizi id")
        val listRes = apiCall(
            "prod-api/16/app/hiDrama/dramaListV6",
            mapOf("shortPlayId" to id),
            lang = lang,
        )
        val episodes = JSONArray()
        val listData = listRes.optJSONObject("data")
        val dramaArr = listData?.optJSONArray("dramaListResponseList")
            ?: listData?.optJSONArray("dramaList")
            ?: listData?.optJSONArray("list")
            ?: JSONArray()
        for (i in 0 until dramaArr.length()) {
            val d = dramaArr.getJSONObject(i)
            episodes.put(
                JSONObject().apply {
                    put("serial", d.optInt("episodeNum", d.optInt("episode", i + 1)))
                    put("chapter_id", d.optString("id", d.optString("dramaId", d.optString("businessId"))))
                    put("cover", d.optString("coverId", d.optString("cover")))
                    put("isLock", d.optBoolean("lock", false))
                },
            )
        }
        val first = dramaArr.optJSONObject(0)
        val title = first?.optString("shortPlayName").orEmpty()
            .ifBlank { listData?.optString("shortPlayName").orEmpty() }
        return JSONObject().apply {
            put("bookName", title.ifBlank { shortPlayId })
            put("alias", title)
            put("description", listData?.optString("summary").orEmpty())
            put("episodes", episodes)
        }
    }

    fun episodeVideo(shortPlayId: String, dramaId: String, episodeNum: Int, lang: String, isLock: Boolean): String {
        ensureLogin(lang)
        val businessId = dramaId.toIntOrNull() ?: throw IllegalStateException("Bolum id gecersiz")
        if (isLock) {
            runCatching {
                apiCall(
                    "prod-api/16/app/hiDrama/dramaUnLockByGoldV3",
                    mapOf("businessId" to businessId, "autoUnlock" to true),
                    lang = lang,
                )
            }
        }
        val slide = apiCall(
            "prod-api/16/app/forYou/playSlideV3",
            mapOf(
                "scene" to "detail",
                "dreamId" to businessId,
                "previousEpisodes" to 0,
                "nextEpisodes" to 0,
                "videoType" to 1,
            ),
            lang = lang,
        )
        val data = slide.optJSONObject("data") ?: slide
        val current = data.optJSONObject("currentEpisodesReponse")
            ?: data.optJSONObject("currentEpisodesResponse")
        val url = findVideoUrl(current ?: data)
            ?: throw IllegalStateException("StoryReel video URL yok")
        return url
    }

    private fun findVideoUrl(node: JSONObject): String? {
        listOf("videoUrl", "playUrl", "url", "mp4", "m3u8Url", "hlsUrl").forEach { k ->
            parseMediaUrl(node.optString(k))?.let { return it }
        }
        node.optJSONArray("dramaListResponseList")?.let { arr ->
            for (i in 0 until arr.length()) {
                findVideoUrl(arr.getJSONObject(i))?.let { return it }
            }
        }
        node.optJSONArray("dramaList")?.let { arr ->
            for (i in 0 until arr.length()) {
                findVideoUrl(arr.getJSONObject(i))?.let { return it }
            }
        }
        node.optJSONArray("list")?.let { arr ->
            for (i in 0 until arr.length()) {
                findVideoUrl(arr.getJSONObject(i))?.let { return it }
            }
        }
        val keys = node.keys()
        while (keys.hasNext()) {
            val v = node.opt(keys.next())
            if (v is JSONObject) findVideoUrl(v)?.let { return it }
        }
        return null
    }

    private fun parseMediaUrl(raw: String): String? {
        val s = raw.trim()
        if (s.startsWith("http")) return s
        if (s.startsWith("{")) {
            runCatching {
                val o = JSONObject(s)
                listOf("video_1080", "video_720", "video_480", "videoUrl", "url").forEach { k ->
                    o.optString(k).takeIf { it.startsWith("http") }?.let { return it }
                }
            }
        }
        return null
    }

    private fun flattenShorts(arr: JSONArray): List<JSONObject> = buildList {
        for (i in 0 until arr.length()) {
            val raw = arr.getJSONObject(i)
            val sp = raw.optJSONObject("shortPlay") ?: raw
            val spId = when {
                sp.has("shortPlayId") -> sp.optInt("shortPlayId", 0).takeIf { it != 0 }?.toString()
                    ?: sp.optString("shortPlayId")
                else -> sp.optString("id")
            }
            add(
                JSONObject().apply {
                    put("shortPlayId", spId)
                    put("shortPlayName", sp.optString("shortPlayName", sp.optString("name")))
                    put("shortPlayCover", sp.optString("coverId", sp.optString("cover")))
                    put("shortPlayEpisodeCount", sp.optInt("totalEpisodes", sp.optInt("episodeCount")))
                    put("shotIntroduce", sp.optString("summary", sp.optString("introduce")))
                },
            )
        }
    }

    private fun apiCallForm(
        path: String,
        fields: List<Pair<String, String>>,
        extraHeaders: Map<String, String> = emptyMap(),
        lang: String,
    ): JSONObject {
        val locale = langMap[lang] ?: langMap.getValue("en")
        val form = FormBody.Builder()
        fields.forEach { (k, v) -> form.add(k, v) }
        val body = form.build()
        val builder = baseRequestBuilder(path, locale)
        authToken?.let { builder.header("Authorization", it) }
        extraHeaders.forEach { (k, v) -> builder.header(k, v) }
        builder.post(body).header("isEncrypt", "false")
        return executeApiResponse(builder.build())
    }

    private fun apiCall(
        path: String,
        fields: Map<String, Any?>,
        encrypt: Boolean = true,
        extraHeaders: Map<String, String> = emptyMap(),
        lang: String,
    ): JSONObject {
        val locale = langMap[lang] ?: langMap.getValue("en")
        val bodyJson = JSONObject()
        fields.forEach { (k, v) ->
            if (v == null) return@forEach
            when (v) {
                is JSONArray -> bodyJson.put(k, v)
                else -> bodyJson.put(k, v)
            }
        }
        val bodyText = if (encrypt) {
            StoryReelRc4.encryptToHex(bodyJson.toString(), apiRc4Key)
        } else {
            bodyJson.toString()
        }
        val builder = baseRequestBuilder(path, locale)
        authToken?.let { builder.header("Authorization", it) }
        extraHeaders.forEach { (k, v) -> builder.header(k, v) }
        builder.post(bodyText.toRequestBody(JSON_MEDIA)).header("isEncrypt", if (encrypt) "true" else "false")
        return executeApiResponse(builder.build())
    }

    private fun baseRequestBuilder(path: String, locale: Pair<String, String>): Request.Builder =
        Request.Builder()
            .url("${BASE_URLS.first().trimEnd('/')}/$path")
            .header("channel", "Google")
            .header("gaid", deviceId)
            .header("deviceId", deviceId)
            .header("clientPlatform", "android")
            .header("TraceId", UUID.randomUUID().toString())
            .header("language", locale.first)
            .header("locale", locale.first)
            .header("timeZone", "+3")
            .header("model", "Pixel 6")
            .header("systemVersion", "12")
            .header("androidVersion", "12")
            .header("mcc", "286")
            .header("environment", "prod")
            .header("buildValue", "100")
            .header("hasProxy", "false")
            .header("resolution", "1080x2400")
            .header("session_id", UUID.randomUUID().toString())
            .header("system_country_code", locale.second)
            .header("isR", "false")
            .header("isE", "false")
            .header("isD", "false")
            .header("vefCode", StoryReelRc4.md5Hex(deviceId))
            .header("appId", "16")
            .header("User-Agent", UA)

    private fun executeApiResponse(request: Request): JSONObject {
        HttpClient.client.newCall(request).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            val encrypted = resp.header("isEncrypt")?.equals("true", true) == true
            val text = if (encrypted) {
                val payload = if (raw.trimStart().startsWith("{")) {
                    JSONObject(raw).optString("result", raw)
                } else raw
                StoryReelRc4.decryptFromHex(payload.trim('"'), apiRc4Key)
            } else raw
            val json = JSONObject(text)
            val status = json.optInt("status", json.optInt("code", 0))
            if (status != 0 && status != 200 && json.optJSONObject("data") == null) {
                val msg = json.optString("message").ifBlank { json.optString("error") }
                throw IllegalStateException(msg.ifBlank { "StoryReel hata $status" })
            }
            return json
        }
    }

    companion object {
        private val BASE_URLS = listOf(
            "https://prod-api.storyreel.life/",
            "https://api.shorttv.app/",
        )
        private const val PREFS = "storyreel_client"
        private const val KEY_AUTH = "auth"
        private const val KEY_RC4 = "rc4_key"
        private const val KEY_DEVICE = "device_id"
        private const val UA =
            "Mozilla/5.0 (Linux; Android 12; Pixel 6) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36 StoryReel/1.0"
        private val JSON_MEDIA = "application/json;charset=utf-8".toMediaType()
    }
}
