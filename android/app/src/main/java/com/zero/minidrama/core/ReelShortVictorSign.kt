package com.zero.minidrama.core

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.newleaf.app.android.victor.util.SBUtil
import java.util.UUID
import java.util.zip.Inflater

internal object ReelShortVictorSign {
    private const val CHANNEL_ID = "AVG10003"
    private const val API_VERSION = "1.4.25"
    /** Matches ReelShort 4.2.20 clientVer sent to v-api. */
    private const val CLIENT_VER = "4220"

    fun signedHeaders(
        context: Context,
        session: ReelShortMobileSession,
        formFields: Map<String, String>
    ): Map<String, String> {
        val ts = (System.currentTimeMillis() / 1000).toString()
        val traceId = System.currentTimeMillis().toString() + (1000..9999).random()
        val requestTime = runCatching {
            SBUtil.getSystemLaunchTime(context.applicationInfo.sourceDir)
        }.getOrElse { System.currentTimeMillis().toString() }

        val params = linkedMapOf<String, String>(
            "uid" to session.uid.toString(),
            "channelId" to CHANNEL_ID,
            "ts" to ts,
            "apiVersion" to API_VERSION,
            "session" to session.session,
            "lang" to session.apiLang,
            "devId" to session.devId,
            "clientVer" to CLIENT_VER,
            "clientTraceId" to traceId,
            "requestTime" to requestTime,
            "deviceVersion" to Build.VERSION.SDK_INT.toString()
        )
        formFields.forEach { (k, v) -> if (v.isNotEmpty()) params[k] = v }

        val signPayload = buildString {
            for (key in params.keys.sorted()) {
                val value = params[key].orEmpty()
                if (value.isEmpty()) continue
                append(key).append('=').append(value).append('&')
            }
            if (isNotEmpty()) deleteCharAt(length - 1)
        }

        val sign = SBUtil.sha256Encrypt(signPayload, SBUtil.normalSignKey)

        return mapOf(
            "uid" to session.uid.toString(),
            "channelId" to CHANNEL_ID,
            "ts" to ts,
            "apiVersion" to API_VERSION,
            "session" to session.session,
            "lang" to session.apiLang,
            "devId" to session.devId,
            "clientVer" to CLIENT_VER,
            "clientTraceId" to traceId,
            "requestTime" to requestTime,
            "deviceVersion" to Build.VERSION.SDK_INT.toString(),
            "sign" to sign
        )
    }

    fun decryptPlayInfo(encrypted: String): String {
        if (encrypted.isBlank()) return ""
        val decrypted = SBUtil.decryptStr(
            encrypted,
            SBUtil.PRIVATE_KEY_VERSION
        )
        if (decrypted.isBlank()) return ""
        val raw = SBUtil.base64Decode(decrypted) ?: return ""
        return inflate(raw).toString(Charsets.UTF_8)
    }

    private fun inflate(input: ByteArray): ByteArray {
        val inflater = Inflater()
        inflater.setInput(input)
        val out = java.io.ByteArrayOutputStream(input.size.coerceAtLeast(256))
        val buf = ByteArray(1024)
        try {
            while (!inflater.finished()) {
                val n = inflater.inflate(buf)
                if (n > 0) out.write(buf, 0, n)
            }
        } finally {
            inflater.end()
        }
        return out.toByteArray()
    }
}

internal class ReelShortMobileSession private constructor(
    val devId: String,
    var uid: Long,
    var session: String,
    val apiLang: String
) {
    companion object {
        private const val PREFS = "reelshort_mobile"

        fun load(context: Context, lang: String): ReelShortMobileSession {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            var devId = prefs.getString("devId", null)
            if (devId.isNullOrBlank()) {
                devId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
                    ?.takeIf { it.isNotBlank() && it != "9774d56d682e549c" }
                    ?: UUID.randomUUID().toString().replace("-", "")
                prefs.edit().putString("devId", devId).apply()
            }
            val uid = prefs.getLong("uid", 0L)
            val session = prefs.getString("session", "").orEmpty()
            val apiLang = if (lang == "en") "en" else "tr"
            return ReelShortMobileSession(devId, uid, session, apiLang)
        }

        fun appendDeviceTelemetry(context: Context, fields: MutableMap<String, String>) {
            val dm = context.resources.displayMetrics
            fields["_cpu_hertz"] = "0"
            fields["_device_rom"] = "0"
            fields["_device_ram"] = "0"
            fields["_device_screen_h"] = dm.heightPixels.toString()
            fields["_device_screen_w"] = dm.widthPixels.toString()
            fields["os_version"] = Build.VERSION.RELEASE ?: ""
        }
    }

    fun persist(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong("uid", uid)
            .putString("session", session)
            .apply()
    }

    fun clearSession(context: Context) {
        uid = 0L
        session = ""
        persist(context)
    }

    fun timeZone(): String = java.util.TimeZone.getDefault().id

    fun devModel(): String = Build.MODEL ?: "Android"
}
