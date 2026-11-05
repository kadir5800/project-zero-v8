package com.zero.minidrama.core

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class GoodShortClient(private val context: Context) {
    private val gson: Gson = GsonBuilder().disableHtmlEscaping().create()
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private var token: String? = prefs.getString(KEY_TOKEN, null)
    private var userId: String? = prefs.getString(KEY_USER_ID, null)
    private val deviceId: String = prefs.getString(KEY_DEVICE, null)
        ?: UUID.randomUUID().toString().also { prefs.edit().putString(KEY_DEVICE, it).apply() }
    private val androidId: String = prefs.getString(KEY_ANDROID_ID, null)
        ?: Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?.takeIf { it.isNotBlank() }
            ?: UUID.randomUUID().toString().replace("-", "").take(16).also {
            prefs.edit().putString(KEY_ANDROID_ID, it).apply()
        }

    fun ensureBootstrap(lang: String) {
        if (!token.isNullOrBlank()) return
        val bodyText = gson.toJson(
            linkedMapOf(
                "addWidgets" to 0,
                "pushAuthorization" to 0,
                "firstInstall" to true,
                "anonymousId" to deviceId,
                "distinctId" to deviceId,
                "os" to Build.VERSION.RELEASE,
                "sysModel" to Build.MODEL,
                "userAgent" to (System.getProperty("http.agent") ?: "-1"),
                "startupType" to 0,
            ),
        )
        val json = post("hwycclientreels/app/bootstrap", bodyText, lang, auth = false)
        val data = json.optJSONObject("data") ?: throw IllegalStateException("GoodShort bootstrap basarisiz")
        val user = data.optJSONObject("user") ?: data
        val raw = user.optString("token").takeIf { it.isNotBlank() }
        token = raw?.let { if (it.startsWith("Bearer ")) it else "Bearer $it" }
        userId = user.optString("id", user.optString("userId")).takeIf { it.isNotBlank() }
        if (token.isNullOrBlank()) throw IllegalStateException("GoodShort oturum acilamadi")
        prefs.edit().putString(KEY_TOKEN, token).putString(KEY_USER_ID, userId).apply()
    }

    fun search(q: String, page: Int, lang: String): Pair<List<JSONObject>, Int> {
        ensureBootstrap(lang)
        val keyword = q.trim()
        if (keyword.isEmpty()) {
            val home = post("hwycclientreels/home/index", JSONObject(), lang)
            val data = home.optJSONObject("data") ?: JSONObject()
            val books = extractBooksFromStore(data)
            return books to 1
        }
        val body = JSONObject().apply {
            put("keyword", keyword)
            put("pageNo", page.toString())
            put("pageSize", "20")
        }
        val json = post("hwycclientreels/book/search1", body, lang)
        val data = json.optJSONObject("data") ?: JSONObject()
        val searchResult = data.optJSONObject("searchResult")
        val list = searchResult?.optJSONArray("records")
            ?: data.optJSONArray("books")
            ?: data.optJSONArray("bookList")
            ?: data.optJSONArray("list")
            ?: JSONArray()
        val books = buildList {
            for (i in 0 until list.length()) {
                val b = list.getJSONObject(i)
                add(
                    JSONObject().apply {
                        put("bookId", b.optString("bookId", b.optString("id")))
                        put("bookName", b.optString("bookName", b.optString("name")))
                        put("cover", b.optString("cover", b.optString("bookCover")))
                        put("introduction", b.optString("introduction", b.optString("desc")))
                        put("chapterCount", b.optInt("chapterCount", b.optInt("totalChapterNum")))
                    },
                )
            }
        }
        val total = searchResult?.optInt("total", books.size)
            ?: data.optInt("total", books.size)
        val pages = searchResult?.optInt("pages", 1)
            ?: ((total + 19) / 20).coerceAtLeast(1)
        return books to pages.coerceAtLeast(1)
    }

    fun bookDetail(bookId: String, lang: String): JSONObject {
        ensureBootstrap(lang)
        val body = JSONObject().apply {
            put("bookId", bookId)
            put("chapterCount", 0)
            put("latestChapterId", 0)
            put("needBookInfo", true)
        }
        val json = post("hwycclientreels/chapter/list", body, lang)
        val data = json.optJSONObject("data") ?: JSONObject()
        val chapters = data.optJSONArray("list") ?: JSONArray()
        val episodes = JSONArray()
        for (i in 0 until chapters.length()) {
            val c = chapters.getJSONObject(i)
            val chapterId = when {
                c.has("id") && !c.isNull("id") -> c.optLong("id").toString()
                else -> c.optString("chapterId", c.optString("id"))
            }
            episodes.put(
                JSONObject().apply {
                    put("serial", c.optInt("index", i + 1))
                    put("chapter_id", chapterId)
                    put("node_id", chapterNodeId(c))
                    put("cover", c.optString("image", c.optString("cover")))
                    put("isLock", c.optInt("charged", 0) == 1 && c.optInt("price", 0) > 0)
                    c.optString("filePath").takeIf { it.startsWith("http") }?.let { put("filePath", it) }
                },
            )
        }
        val book = data.optJSONObject("book") ?: JSONObject()
        return JSONObject().apply {
            put("bookName", book.optString("bookName", book.optString("name")))
            put("alias", book.optString("bookName"))
            put("description", book.optString("introduction", book.optString("desc")))
            put("episodes", episodes)
        }
    }

    fun playUrl(bookId: String, chapterId: String, nodeId: String, lang: String): String {
        ensureBootstrap(lang)
        val chapter = findChapterInList(bookId, chapterId, lang)
        chapter?.let { resolvePlayUrl(it) }?.let { return it }
        var node = nodeId.trim()
        if (node.isBlank() || node == "0") {
            node = chapter?.let { chapterNodeId(it) }.orEmpty()
        }
        val body = JSONObject().apply {
            put("bookId", bookId)
            put("chapterId", chapterId)
            put("nodeId", node.ifBlank { "0" })
            put("currency", "USD")
            put("source", "player")
        }
        val json = post("hwycclientreels/chapter/node/load", body, lang)
        val data = json.optJSONObject("data") ?: throw IllegalStateException("GoodShort bolum yuklenemedi")
        val loaded = data.optJSONObject("indexChapter")
            ?: data.optJSONArray("list")?.optJSONObject(0)
            ?: data
        resolvePlayUrl(loaded)?.let { return it }
        val nodes = data.optJSONArray("nodeList")
        if (nodes != null) {
            for (i in 0 until nodes.length()) {
                resolvePlayUrl(nodes.getJSONObject(i))?.let { return it }
            }
        }
        throw IllegalStateException("GoodShort video URL bulunamadi")
    }

    private fun findChapterInList(bookId: String, chapterId: String, lang: String): JSONObject? {
        val body = JSONObject().apply {
            put("bookId", bookId)
            put("chapterCount", 0)
            put("latestChapterId", 0)
            put("needBookInfo", false)
        }
        val json = post("hwycclientreels/chapter/list", body, lang)
        val list = json.optJSONObject("data")?.optJSONArray("list") ?: return null
        for (i in 0 until list.length()) {
            val c = list.getJSONObject(i)
            val id = when {
                c.has("id") && !c.isNull("id") -> c.optLong("id").toString()
                else -> c.optString("chapterId", c.optString("id"))
            }
            if (id == chapterId) return c
        }
        return null
    }

    private fun chapterNodeId(chapter: JSONObject): String {
        chapter.optString("currentNodeId").takeIf { it.isNotBlank() }?.let { return it }
        chapter.optJSONArray("nodes")?.let { arr ->
            if (arr.length() > 0) {
                arr.getJSONObject(0).optString("nodeId").takeIf { it.isNotBlank() }?.let { return it }
            }
        }
        val nodesJson = chapter.optString("nodesJsonString")
        if (nodesJson.isNotBlank()) {
            runCatching {
                val arr = JSONArray(nodesJson)
                if (arr.length() > 0) arr.getJSONObject(0).optString("nodeId") else ""
            }.getOrNull()?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return ""
    }

    private fun resolvePlayUrl(obj: JSONObject): String? {
        obj.optString("filePath").takeIf { it.startsWith("http") }?.let { return it }
        val cdn = obj.optString("cdn")
        obj.optJSONArray("cdnList")?.let { arr ->
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                val path = item.optString("videoPath", item.optString("path"))
                if (path.isNotBlank()) {
                    return if (path.startsWith("http")) path else cdn.trimEnd('/') + "/" + path.trimStart('/')
                }
            }
        }
        return null
    }

    private fun extractBooksFromStore(data: JSONObject): List<JSONObject> {
        val out = mutableListOf<JSONObject>()
        fun walk(node: Any?) {
            when (node) {
                is JSONObject -> {
                    if (node.has("bookId") || node.has("id")) {
                        out.add(
                            JSONObject().apply {
                                put("bookId", node.optString("bookId", node.optString("id")))
                                put("bookName", node.optString("bookName", node.optString("name")))
                                put("cover", node.optString("cover", node.optString("bookCover")))
                                put("introduction", node.optString("introduction"))
                                put("chapterCount", node.optInt("chapterCount"))
                            },
                        )
                    }
                    node.keys().forEach { k -> walk(node.get(k)) }
                }
                is JSONArray -> for (i in 0 until node.length()) walk(node.get(i))
            }
        }
        walk(data)
        return out.distinctBy { it.optString("bookId") }.take(40)
    }

    private fun post(path: String, body: JSONObject, lang: String, auth: Boolean = true): JSONObject =
        post(path, body.toString(), lang, auth)

    private fun post(path: String, bodyText: String, lang: String, auth: Boolean = true): JSONObject {
        val timestamp = System.currentTimeMillis()
        val authHeader = if (auth) token.orEmpty() else ""
        val sign = GoodShortSign.sign(timestamp, bodyText, deviceId, androidId, authHeader)
        val localTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z", Locale.ENGLISH).format(Date())
        val langCode = if (lang == "tr") "tr" else "en"
        var lastError: Exception? = null
        for (base in BASE_URLS) {
            val url = base.toHttpUrl().newBuilder()
                .addPathSegments(path.trimStart('/'))
                .addQueryParameter("timestamp", timestamp.toString())
                .build()
            val req = Request.Builder()
                .url(url)
                .post(bodyText.toRequestBody(JSON_MEDIA))
                .header("Content-Type", "application/json; charset=utf-8")
                .header("sign", sign)
                .header("Authorization", if (auth) token.orEmpty() else "")
                .header("platform", "ANDROID")
                .header("appVersion", APP_VERSION)
                .header("channelCode", CHANNEL_CODE)
                .header("pname", APP_PACKAGE)
                .header("apn", "2")
                .header("language", langCode)
                .header("currentLanguage", langCode)
                .header("localTime", localTime)
                .header("p", "233")
                .header("deviceId", deviceId)
                .header("androidId", androidId)
                .header("appType", "ANDROID")
                .header("os", Build.VERSION.RELEASE)
                .header("startType", "1")
                .header("isLogin", if (auth && !token.isNullOrBlank()) "true" else "false")
                .header("User-Agent", UA)
            if (auth) {
                userId?.let { req.header("userId", it) }
            }
            try {
                HttpClient.client.newCall(req.build()).execute().use { resp ->
                    val text = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        throw IllegalStateException("GoodShort HTTP ${resp.code}: ${text.take(120)}")
                    }
                    val json = JSONObject(text)
                    if (!json.optBoolean("success", json.optInt("status", 0) == 0 || json.optInt("code", 0) == 0)) {
                        throw IllegalStateException(json.optString("msg", json.optString("message", "GoodShort hata")))
                    }
                    return json
                }
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: IllegalStateException("GoodShort istek basarisiz")
    }

    companion object {
        private val BASE_URLS = listOf(
            "https://api.good-short.com/",
            "https://api2.good-short.com/",
            "https://api-akm.goodreels.com/",
            "https://api.goodreels.com/",
        )
        private const val CHANNEL_CODE = "GRA00001"
        private const val APP_PACKAGE = "com.newreading.goodreels"
        private const val APP_VERSION = "3252125"
        private const val PREFS = "goodshort_client"
        private const val KEY_TOKEN = "token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_DEVICE = "device_id"
        private const val KEY_ANDROID_ID = "android_id"
        private const val UA =
            "Mozilla/5.0 (Linux; Android 12; Pixel 6) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36 GoodShort/3.1.5"
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}
