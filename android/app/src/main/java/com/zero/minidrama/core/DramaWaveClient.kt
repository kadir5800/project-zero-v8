package com.zero.minidrama.core

import android.content.Context
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.net.URLEncoder
import java.util.LinkedHashMap
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

class DramaWaveClient(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val seriesCache = ConcurrentHashMap<String, JSONObject>()
    private val unlockedUrls = ConcurrentHashMap<String, String>()

    private var oauthToken: String? = null
    private var oauthSecret: String? = null
    private var catalogCache: List<JSONObject>? = null
    private var catalogCacheAt = 0L

    private var deviceId: String = prefs.getString(KEY_DEVICE_ID, null)
        ?: UUID.randomUUID().toString().also { prefs.edit().putString(KEY_DEVICE_ID, it).apply() }

    private var sessionId: String = prefs.getString(KEY_SESSION_ID, null)
        ?: UUID.randomUUID().toString().replace("-", "").also {
            prefs.edit().putString(KEY_SESSION_ID, it).apply()
        }

    private var adUnlockCount = 0
    private var forceServerUnlock = false
    private var lastRotateAt = 0L
    private var lastApiAt = 0L
    private var androidId: String = prefs.getString(KEY_ANDROID_ID, null)
        ?: Random.nextBytes(8).joinToString("") { "%02x".format(it) }.also {
            prefs.edit().putString(KEY_ANDROID_ID, it).apply()
        }
    private var firebaseId: String = prefs.getString(KEY_FIREBASE_ID, null)
        ?: UUID.randomUUID().toString().also { prefs.edit().putString(KEY_FIREBASE_ID, it).apply() }
    private var appsflyerId: String = prefs.getString(KEY_APPSFLYER_ID, null)
        ?: UUID.randomUUID().toString().also { prefs.edit().putString(KEY_APPSFLYER_ID, it).apply() }
    private var gaid: String = prefs.getString(KEY_GAID, null)
        ?: UUID.randomUUID().toString().also { prefs.edit().putString(KEY_GAID, it).apply() }

    private val langMap = mapOf(
        "tr" to LocaleConfig("TR", "tr", "286", "+3"),
        "en" to LocaleConfig("US", "en", "310", "-5")
    )

    fun ensureLogin(lang: String) {
        if (oauthToken != null && oauthSecret != null) return
        val savedToken = prefs.getString(KEY_TOKEN, null)
        val savedSecret = prefs.getString(KEY_SECRET, null)
        if (!savedToken.isNullOrBlank() && !savedSecret.isNullOrBlank()) {
            oauthToken = savedToken
            oauthSecret = savedSecret
            return
        }
        var lastErr: Exception? = null
        repeat(4) { attempt ->
            try {
                if (attempt > 0) {
                    deviceId = UUID.randomUUID().toString()
                    androidId = Random.nextBytes(8).joinToString("") { "%02x".format(it) }
                    firebaseId = UUID.randomUUID().toString()
                    appsflyerId = UUID.randomUUID().toString()
                    gaid = UUID.randomUUID().toString()
                    prefs.edit()
                        .putString(KEY_DEVICE_ID, deviceId)
                        .putString(KEY_ANDROID_ID, androidId)
                        .putString(KEY_FIREBASE_ID, firebaseId)
                        .putString(KEY_APPSFLYER_ID, appsflyerId)
                        .putString(KEY_GAID, gaid)
                        .apply()
                    Thread.sleep(minOf(4000L * (1 shl (attempt - 1)), 20000L))
                }
                val body = JSONObject().put("device_id", deviceId)
                val resp = apiCall("POST", "/anonymous/login", body, lang, auth = false)
                val data = resp.optJSONObject("data") ?: throw IllegalStateException("DramaWave login basarisiz")
                oauthToken = data.optString("auth_key").takeIf { it.isNotBlank() }
                oauthSecret = data.optString("auth_secret").takeIf { it.isNotBlank() }
                if (oauthToken == null || oauthSecret == null) throw IllegalStateException("DramaWave login basarisiz")
                prefs.edit()
                    .putString(KEY_TOKEN, oauthToken)
                    .putString(KEY_SECRET, oauthSecret)
                    .apply()
                return
            } catch (e: Exception) {
                lastErr = e
                if (!e.message.orEmpty().contains("permission denied", ignoreCase = true) &&
                    !e.message.orEmpty().contains("403", ignoreCase = true)
                ) {
                    throw e
                }
            }
        }
        throw IllegalStateException("DramaWave gecici erisim engeli. 2-3 dakika bekleyip tekrar deneyin.", lastErr)
    }

    fun search(q: String, page: Int, lang: String): Pair<List<JSONObject>, Int> {
        ensureLogin(lang)
        val keyword = q.trim()
        val fromApi = if (keyword.isNotEmpty()) searchApi(keyword, lang) else emptyList()
        val merged = if (fromApi.isNotEmpty()) {
            fromApi
        } else {
            val catalog = loadCatalog(lang)
            if (keyword.isEmpty()) catalog else filterCatalog(catalog, keyword)
        }
        val pageSize = 20
        val start = (page - 1).coerceAtLeast(0) * pageSize
        val slice = merged.drop(start).take(pageSize)
        val totalPages = ((merged.size + pageSize - 1) / pageSize).coerceAtLeast(1)
        return slice to totalPages
    }

    fun bookDetail(bookKey: String, lang: String): JSONObject {
        ensureLogin(lang)
        val detailResp = apiCall("GET", "/drama/info_v2?series_id=$bookKey&scene=detail", null, lang)
        val info = detailResp.optJSONObject("data")?.optJSONObject("info")
        if (info != null && info.length() > 0) {
            seriesCache[bookKey] = info
            return buildDetailFromSeries(bookKey, info)
        }

        seriesCache[bookKey]?.let { cached ->
            return buildDetailFromSeries(bookKey, cached)
        }

        val fromCatalog = loadCatalog(lang).firstOrNull { seriesKey(it) == bookKey }
            ?: throw IllegalStateException("DramaWave dizi bulunamadi")
        seriesCache[bookKey] = fromCatalog
        return buildDetailFromSeries(bookKey, fromCatalog)
    }

    fun episodeVideo(seriesKey: String, episodeKey: String, lang: String): String {
        ensureLogin(lang)
        if (episodeKey.contains("_ep_")) {
            throw IllegalStateException("Bu bolum henuz yayinlanmadi")
        }

        var series = seriesCache[seriesKey]
        if (series?.optJSONArray("episode_list") == null) {
            bookDetail(seriesKey, lang)
            series = seriesCache[seriesKey]
        }
        getEpisodeUrl(seriesKey, series, episodeKey)?.let { return it }
        unlockEpisodeChain(seriesKey, episodeKey, lang)?.let { return it }

        getEpisodeUrl(
            seriesKey,
            seriesCache[seriesKey],
            episodeKey
        )?.let { return it }

        throw IllegalStateException("DramaWave video URL alinamadi")
    }

    private fun unlockEpisodeChain(seriesKey: String, targetEpisodeKey: String, lang: String): String? {
        var lastErr: Exception? = null
        repeat(2) { attempt ->
            try {
                return unlockEpisodeChainOnce(seriesKey, targetEpisodeKey, lang)
            } catch (e: Exception) {
                lastErr = e
                if (attempt < 1 && shouldRetryWithNewAccount(e.message)) {
                    rotateAccount(lang)
                } else {
                    throw IllegalStateException(mapEpisodeError(e.message))
                }
            }
        }
        throw IllegalStateException(mapEpisodeError(lastErr?.message))
    }

    private fun unlockEpisodeChainOnce(seriesKey: String, targetEpisodeKey: String, lang: String): String? {
        var series = seriesCache[seriesKey] ?: bookDetail(seriesKey, lang).let { seriesCache[seriesKey] }
            ?: return null
        val list = series.optJSONArray("episode_list") ?: return null
        var targetIdx = -1
        for (i in 0 until list.length()) {
            if (list.getJSONObject(i).optString("id") == targetEpisodeKey) {
                targetIdx = i
                break
            }
        }
        if (targetIdx < 0) return null

        var i = 0
        while (i <= targetIdx) {
            val currentSeries = seriesCache[seriesKey] ?: bookDetail(seriesKey, lang).let { seriesCache[seriesKey] }
            if (currentSeries == null) return null
            series = currentSeries
            val arr = currentSeries.optJSONArray("episode_list")
            if (arr == null) return null
            val ep = arr.optJSONObject(i)
            if (ep == null) {
                i++
            } else {
                val epId = ep.optString("id")
                if (epId.isBlank()) {
                    i++
                } else {
                    val embedded = episodeUrlFromMeta(ep)
                    val skip = (i <= 10 && !embedded.isNullOrBlank()) ||
                        (!forceServerUnlock && getEpisodeUrl(seriesKey, currentSeries, epId) != null)
                    if (skip) {
                        i++
                    } else {
                        adUnlockWithRetry(seriesKey, epId, i, lang)
                        i++
                    }
                }
            }
        }

        forceServerUnlock = false
        bookDetail(seriesKey, lang)
        return getEpisodeUrl(seriesKey, seriesCache[seriesKey], targetEpisodeKey)
    }

    private fun rotateAccount(lang: String) {
        val now = System.currentTimeMillis()
        if (lastRotateAt > 0 && now - lastRotateAt < ROTATE_COOLDOWN_MS) {
            throw IllegalStateException("DramaWave gecici erisim engeli. 1-2 dakika bekleyip tekrar deneyin.")
        }
        lastRotateAt = now
        deviceId = UUID.randomUUID().toString()
        sessionId = UUID.randomUUID().toString().replace("-", "")
        androidId = Random.nextBytes(8).joinToString("") { "%02x".format(it) }
        firebaseId = UUID.randomUUID().toString()
        appsflyerId = UUID.randomUUID().toString()
        gaid = UUID.randomUUID().toString()
        oauthToken = null
        oauthSecret = null
        adUnlockCount = 0
        forceServerUnlock = true
        prefs.edit()
            .putString(KEY_DEVICE_ID, deviceId)
            .putString(KEY_SESSION_ID, sessionId)
            .putString(KEY_ANDROID_ID, androidId)
            .putString(KEY_FIREBASE_ID, firebaseId)
            .putString(KEY_APPSFLYER_ID, appsflyerId)
            .putString(KEY_GAID, gaid)
            .remove(KEY_TOKEN)
            .remove(KEY_SECRET)
            .apply()
        Thread.sleep(2000)
        ensureLogin(lang)
    }

    private fun shouldRetryWithNewAccount(message: String?): Boolean {
        val msg = message.orEmpty()
        return msg.contains("balance not enough", ignoreCase = true) ||
            msg.contains("ad group not exist", ignoreCase = true) ||
            msg.contains("12002", ignoreCase = true) ||
            msg.contains("reklam anahtari", ignoreCase = true)
    }

    private fun adUnlockWithRetry(seriesKey: String, episodeKey: String, lastIndex: Int, lang: String) {
        val modes = listOf("ad", "free", "reward", "diamond")
        var lastErr: Exception? = null
        for (mode in modes) {
            try {
                adUnlockEpisode(seriesKey, episodeKey, lastIndex, lang, mode)?.let { return }
            } catch (e: Exception) {
                lastErr = e
            }
        }
        throw lastErr ?: IllegalStateException("DramaWave reklam kilidi acilamadi")
    }

    private fun searchApi(keyword: String, lang: String): List<JSONObject> {
        val body = JSONObject()
            .put("next", "")
            .put("keyword", keyword)
            .put("timestamp", System.currentTimeMillis().toString())
        val resp = apiCall("POST", "/search/drama", body, lang)
        val data = resp.optJSONObject("data") ?: return emptyList()
        val items = data.optJSONArray("items") ?: JSONArray()
        return jsonArrayToList(items).onEach { cacheSeries(it) }
    }

    private fun loadCatalog(lang: String): List<JSONObject> {
        val now = System.currentTimeMillis()
        catalogCache?.let { cached ->
            if (now - catalogCacheAt < CATALOG_TTL_MS) return cached
        }

        ensureLogin(lang)
        val merged = LinkedHashMap<String, JSONObject>()

        var next = ""
        repeat(FORYOU_MAX_PAGES) {
            val resp = apiCall("GET", "/foryou/feed?next=$next", null, lang)
            val data = resp.optJSONObject("data") ?: return@repeat
            jsonArrayToList(data.optJSONArray("items") ?: JSONArray()).forEach { addSeries(merged, it) }
            val pageInfo = data.optJSONObject("page_info")
            next = pageInfo?.optString("next").orEmpty()
            val hasMore = pageInfo?.optBoolean("has_more") ?: next.isNotBlank()
            if (!hasMore || next.isBlank()) return@repeat
        }

        runCatching {
            val hot = apiCall("POST", "/search/hot-list", JSONObject(), lang)
            jsonArrayToList(hot.optJSONObject("data")?.optJSONArray("items") ?: JSONArray())
                .forEach { addSeries(merged, it) }
        }

        runCatching {
            var soonNext = ""
            repeat(3) {
                val soon = apiCall("GET", "/coming-soon/list?next=$soonNext", null, lang)
                val data = soon.optJSONObject("data") ?: return@runCatching
                for (group in jsonArrayToList(data.optJSONArray("items") ?: JSONArray())) {
                    jsonArrayToList(group.optJSONArray("items") ?: JSONArray()).forEach { addSeries(merged, it) }
                }
                soonNext = data.optJSONObject("page_info")?.optString("next").orEmpty()
                if (soonNext.isBlank()) return@runCatching
            }
        }

        val list = merged.values.toList()
        catalogCache = list
        catalogCacheAt = now
        return list
    }

    private fun addSeries(merged: LinkedHashMap<String, JSONObject>, item: JSONObject) {
        val key = seriesKey(item)
        if (key.isBlank()) return
        cacheSeries(item)
        merged[key] = item
    }

    private fun normalizeText(text: String): String = text.lowercase()
        .replace('ı', 'i').replace('ğ', 'g').replace('ü', 'u')
        .replace('ş', 's').replace('ö', 'o').replace('ç', 'c')
        .replace('â', 'a').replace('î', 'i').replace('û', 'u')

    private fun filterCatalog(items: List<JSONObject>, keyword: String): List<JSONObject> {
        val q = normalizeText(keyword)
        return items.filter { item ->
            val title = normalizeText(seriesTitle(item))
            val title2 = normalizeText(item.optString("title2"))
            val desc = normalizeText(item.optString("desc", item.optString("description")))
            val tags = buildList {
                item.optJSONArray("content_tags")?.let { arr ->
                    for (i in 0 until arr.length()) add(arr.optString(i))
                }
                item.optJSONArray("series_tag")?.let { arr ->
                    for (i in 0 until arr.length()) add(arr.optString(i))
                }
            }.joinToString(" ").let(::normalizeText)
            title.contains(q) || title2.contains(q) || desc.contains(q) || tags.contains(q)
        }
    }

    private fun buildDetailFromSeries(bookKey: String, series: JSONObject): JSONObject {
        val episodesArr = series.optJSONArray("episode_list")
        val episodes = if (episodesArr != null && episodesArr.length() > 0) {
            buildList {
                for (i in 0 until episodesArr.length()) {
                    val ep = episodesArr.getJSONObject(i)
                    val videoUrl = episodeUrlFromMeta(ep).orEmpty()
                    add(JSONObject().apply {
                        put("serial", ep.optInt("index", i + 1).let { if (it <= 0) i + 1 else it })
                        put("chapter_id", ep.optString("id"))
                        put("cover", ep.optString("cover", series.optString("cover")))
                        put("video_url", videoUrl)
                        put("isLock", videoUrl.isBlank() && !ep.optBoolean("unlock", false))
                    })
                }
            }
        } else {
            val preview = series.optJSONObject("episode_info") ?: series.optJSONObject("episode")
            if (preview != null && preview.optString("id").isNotBlank()) {
                val videoUrl = episodeUrlFromMeta(preview).orEmpty()
                listOf(
                    JSONObject().apply {
                        put("serial", 1)
                        put("chapter_id", preview.optString("id"))
                        put("cover", preview.optString("cover", series.optString("cover")))
                        put("video_url", videoUrl)
                        put("isLock", videoUrl.isBlank())
                    }
                )
            } else {
                emptyList()
            }
        }
        if (episodes.isEmpty()) throw IllegalStateException("DramaWave bolum listesi bos")

        return JSONObject().apply {
            put("bookId", bookKey)
            put("bookName", seriesTitle(series))
            put("alias", series.optString("key", bookKey))
            put("description", series.optString("desc", series.optString("description")))
            put("readCount", series.optLong("view_count"))
            put("collectCount", series.optLong("follow_count"))
            put("tags", series.optJSONArray("content_tags") ?: JSONArray())
            put("episodes", JSONArray(episodes))
        }
    }

    private fun seriesKey(item: JSONObject): String =
        item.optString("key").ifBlank { item.optString("id") }

    private fun seriesTitle(item: JSONObject): String =
        item.optString("title").ifBlank { item.optString("name") }

    private fun cacheSeries(item: JSONObject) {
        val key = seriesKey(item)
        if (key.isNotBlank()) seriesCache[key] = item
    }

    private fun jsonArrayToList(arr: JSONArray): List<JSONObject> =
        buildList { for (i in 0 until arr.length()) add(arr.getJSONObject(i)) }

    private fun firstNonBlank(vararg values: String?): String? =
        values.firstOrNull { !it.isNullOrBlank() }

    private fun unlockedKey(seriesKey: String, episodeKey: String) = "$seriesKey:$episodeKey"

    private fun getEpisodeUrl(seriesKey: String, series: JSONObject?, episodeKey: String): String? {
        unlockedUrls[unlockedKey(seriesKey, episodeKey)]?.let { return it }
        return episodeUrlFromMeta(findEpisodeMeta(series, episodeKey))
    }

    private fun episodeUrlFromMeta(ep: JSONObject?): String? = firstNonBlank(
        ep?.optString("external_audio_h264_m3u8"),
        ep?.optString("m3u8_url"),
        ep?.optString("video_url")
    )

    private fun findEpisodeMeta(series: JSONObject?, episodeKey: String): JSONObject? {
        if (series == null) return null
        series.optJSONArray("episode_list")?.let { arr ->
            for (i in 0 until arr.length()) {
                val ep = arr.getJSONObject(i)
                if (ep.optString("id") == episodeKey) return ep
            }
        }
        val preview = series.optJSONObject("episode_info") ?: series.optJSONObject("episode")
        return if (preview?.optString("id") == episodeKey) preview else null
    }

    private fun episodeIndex(series: JSONObject?, episodeKey: String): Int {
        val arr = series?.optJSONArray("episode_list") ?: return 0
        for (i in 0 until arr.length()) {
            if (arr.getJSONObject(i).optString("id") == episodeKey) return i
        }
        return 0
    }

    private fun adUnlockEpisode(
        seriesKey: String,
        episodeKey: String,
        lastIndex: Int,
        lang: String,
        payMode: String = "ad"
    ): String? {
        val enc = { s: String -> URLEncoder.encode(s, "UTF-8") }
        val q = "series_key=${enc(seriesKey)}&episode_key=${enc(episodeKey)}" +
            "&scene=player&pay_mode=${enc(payMode)}&ad_platform=admob"
        val adResp = apiCall("GET", "/ad/get?$q", null, lang)
        val data = adResp.optJSONObject("data") ?: return null
        val adGroup = data.optJSONArray("ad_group")?.optJSONObject(0)
        var adKey = adGroup?.optString("ad_key").orEmpty()
        if (adKey.isBlank()) adKey = data.optJSONObject("ad_info")?.optString("ad_key").orEmpty()
        if (adKey.isBlank()) throw IllegalStateException("DramaWave reklam anahtari alinamadi")
        if (!adKey.endsWith("_group")) adKey = "${adKey}_group"
        val finishBody = JSONObject()
            .put("ad_key", adKey)
            .put("series_key", seriesKey)
            .put("episode_key", episodeKey)
            .put("lastIndex", lastIndex)
        val finishResp = apiCall("POST", "/ad/finish", finishBody, lang)
        val unlocked = finishResp.optJSONObject("data")
            ?.optJSONArray("unlock_episodes")
            ?.optJSONObject(0)
        val url = episodeUrlFromMeta(unlocked)
        if (!url.isNullOrBlank()) {
            unlockedUrls[unlockedKey(seriesKey, episodeKey)] = url
            adUnlockCount++
            seriesCache.remove(seriesKey)
            return url
        }
        throw IllegalStateException("DramaWave reklam sonrasi video URL alinamadi")
    }

    private fun mapEpisodeError(message: String?): String {
        val msg = message.orEmpty()
        return when {
            msg.contains("permission denied", ignoreCase = true) ||
                msg.contains("403", ignoreCase = true) ||
                msg.contains("gecici erisim", ignoreCase = true) ->
                "DramaWave gecici erisim engeli. 2-3 dakika bekleyip tekrar deneyin."
            msg.contains("balance not enough", ignoreCase = true) ->
                "Bolum acilamadi. Lutfen onceki bolumu izleyip tekrar deneyin."
            msg.contains("stories in between", ignoreCase = true) ->
                "Onceki bolumler sirayla acilmali. Lutfen once onceki bolume gecin."
            msg.contains("ad group not exist", ignoreCase = true) ->
                "DramaWave reklam kilidi acilamadi. Tekrar deneyin."
            msg.isNotBlank() -> msg
            else -> "DramaWave video acilamadi"
        }
    }

    private fun throttle() {
        val wait = 450L - (System.currentTimeMillis() - lastApiAt)
        if (wait > 0) Thread.sleep(wait)
        lastApiAt = System.currentTimeMillis()
    }

    private fun apiCall(
        method: String,
        path: String,
        body: JSONObject?,
        lang: String,
        auth: Boolean = true,
        retried403: Boolean = false
    ): JSONObject {
        throttle()
        val locale = langMap[lang] ?: langMap["en"]!!
        val builder = Request.Builder()
            .url(BASE + path)
            .header("User-Agent", UA)
            .header("Accept", "application/json")
            .header("device-id", deviceId)
            .header("device-country", locale.country)
            .header("device-language", "${locale.appLang}-${locale.country}")
            .header("app-version", APP_VERSION)
            .header("app-language", locale.appLang)
            .header("device", "android")
            .header("device-version", "31")
            .header("language", locale.appLang)
            .header("country", locale.country)
            .header("app-name", APP_NAME)
            .header("session-id", sessionId)
            .header("timezone", locale.timezone)
            .header("is-mainland", "false")
            .header("mcc-country", locale.mcc)
            .header("network-type", "wifi")
            .header("screen-width", "1080")
            .header("screen-height", "2400")
            .header("x-device-model", "2201117TG")
            .header("x-device-manufacturer", "Xiaomi")
            .header("x-device-brand", "Redmi")
            .header("x-device-product", "2201117TG")
            .header("x-device-fingerprint", "Redmi/note_tr/note:12/SP1A.210812.015:user/release-keys")
            .header("X-Timezone", locale.timezone)
            .header("firebase-id", firebaseId)
            .header("android-id", androidId)
            .header("gaid", gaid)
            .header("appsflyer-id", appsflyerId)
            .header("X-Appsflyer_Id", appsflyerId)

        if (auth) {
            if (oauthToken == null || oauthSecret == null) ensureLogin(lang)
            val token = oauthToken ?: throw IllegalStateException("DramaWave oturumu yok")
            val secret = oauthSecret ?: throw IllegalStateException("DramaWave oturumu yok")
            val ts = System.currentTimeMillis()
            val sig = md5(SIGN_PREFIX + secret)
            builder.header("Authorization", "oauth_signature=$sig,oauth_token=$token,ts=$ts")
        }

        if (method == "POST") {
            val payload = (body ?: JSONObject()).toString()
            builder.post(payload.toRequestBody(JSON_MEDIA))
            builder.header("Content-Type", "application/json")
        }

        HttpClient.client.newCall(builder.build()).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful && text.isBlank()) {
                throw IllegalStateException("DramaWave HTTP ${resp.code}")
            }
            val json = runCatching { JSONObject(text) }.getOrElse {
                throw IllegalStateException("DramaWave gecersiz yanit: ${text.take(120)}")
            }
            val code = json.optInt("code", resp.code)
            if (code == 403 && auth && !retried403) {
                oauthToken = null
                oauthSecret = null
                prefs.edit().remove(KEY_TOKEN).remove(KEY_SECRET).apply()
                ensureLogin(lang)
                return apiCall(method, path, body, lang, auth, true)
            }
            if (code != 200) {
                throw IllegalStateException(json.optString("message", "DramaWave hata $code"))
            }
            return json
        }
    }

    private fun md5(input: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private data class LocaleConfig(
        val country: String,
        val appLang: String,
        val mcc: String,
        val timezone: String
    )

    companion object {
        private const val BASE = "https://api.mydramawave.com/dm-api"
        private const val APP_VERSION = "1.9.10"
        private const val APP_NAME = "com.dramawave.app"
        private const val SIGN_PREFIX = "8IAcbWyCsVhYv82S2eofRqK1DF3nNDAv&"
        private const val UA =
            "Mozilla/5.0 (Linux; Android 12; Redmi Note 11) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36 DramaWave/1.9.10"
        private const val ROTATE_COOLDOWN_MS = 45_000L
        private const val PREFS = "dramawave_client"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_SESSION_ID = "session_id"
        private const val KEY_TOKEN = "oauth_token"
        private const val KEY_SECRET = "oauth_secret"
        private const val KEY_ANDROID_ID = "android_id"
        private const val KEY_FIREBASE_ID = "firebase_id"
        private const val KEY_APPSFLYER_ID = "appsflyer_id"
        private const val KEY_GAID = "gaid"
        private const val CATALOG_TTL_MS = 5 * 60 * 1000L
        private const val FORYOU_MAX_PAGES = 10
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}
