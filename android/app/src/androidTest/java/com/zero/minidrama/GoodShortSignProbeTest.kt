package com.zero.minidrama

import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.GsonBuilder
import com.zero.minidrama.core.GoodShortSign
import com.zero.minidrama.core.HttpClient
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class GoodShortSignProbeTest {
    private val gson = GsonBuilder().disableHtmlEscaping().create()
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    @Test
    fun probeBootstrapSignVariants() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val androidId = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID).orEmpty()
        val deviceIds = listOf("", UUID.randomUUID().toString())
        val bodyText = gson.toJson(
            linkedMapOf(
                "addWidgets" to 0,
                "pushAuthorization" to 0,
                "firstInstall" to true,
                "anonymousId" to UUID.randomUUID().toString(),
                "distinctId" to UUID.randomUUID().toString(),
                "os" to Build.VERSION.RELEASE,
                "sysModel" to Build.MODEL,
                "userAgent" to (System.getProperty("http.agent") ?: "-1"),
                "startupType" to 0,
            ),
        )
        val suffixes = listOf("-1", "com.newreading.goodreels", "")
        val base = "https://api.good-short.com/"
        val successes = mutableListOf<String>()
        for (deviceId in deviceIds) {
            for (suffix in suffixes) {
                for (libB64 in listOf(true, false)) {
                    val ts = System.currentTimeMillis()
                    val sign = GoodShortSign.sign(ts, bodyText, deviceId, androidId, "", suffix, libB64)
                    val url = base.toHttpUrl().newBuilder()
                        .addPathSegments("hwycclientreels/app/bootstrap")
                        .addQueryParameter("timestamp", ts.toString())
                        .build()
                    val req = Request.Builder()
                        .url(url)
                        .post(bodyText.toRequestBody(jsonMedia))
                        .header("Content-Type", "application/json; charset=utf-8")
                        .header("sign", sign)
                        .header("Authorization", "")
                        .header("platform", "ANDROID")
                        .header("appVersion", "3252125")
                        .header("channelCode", "GRA00001")
                        .header("pname", "com.newreading.goodreels")
                        .header("apn", "2")
                        .header("language", "en")
                        .header("currentLanguage", "en")
                        .header("localTime", SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z", Locale.ENGLISH).format(Date()))
                        .header("p", "233")
                        .header("deviceId", deviceId)
                        .header("androidId", androidId)
                        .header("os", Build.VERSION.RELEASE)
                        .header("startType", "1")
                        .header("isLogin", "false")
                        .build()
                    HttpClient.client.newCall(req).execute().use { resp ->
                        val text = resp.body?.string().orEmpty()
                        val msg = runCatching { JSONObject(text).optString("message", text) }.getOrDefault(text)
                        Log.e("GS_PROBE", "deviceId=${deviceId.take(8)} suffix=$suffix libB64=$libB64 -> $msg")
                        if (msg != "Invalid sign" && resp.isSuccessful) {
                            successes.add("deviceId=${deviceId.isEmpty()} suffix=$suffix libB64=$libB64 msg=$msg")
                        }
                    }
                    Thread.sleep(200)
                }
            }
        }
        if (successes.isEmpty()) {
            fail("No sign variant worked; see logcat GS_PROBE")
        }
        Log.e("GS_PROBE", "SUCCESS: ${successes.joinToString()}")
    }
}
