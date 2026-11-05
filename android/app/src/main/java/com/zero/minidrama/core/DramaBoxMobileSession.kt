package com.zero.minidrama.core

import android.content.Context
import java.util.UUID
import kotlin.random.Random

internal class DramaBoxMobileSession private constructor(
    val deviceId: String,
    val androidId: String,
    val spoffer: String,
    var token: String,
    var uid: String,
    var expiryMs: Long,
    val lang: String
) {
    val payChapterByBook = mutableMapOf<String, Int>()

    fun isValid(): Boolean = token.isNotBlank() && expiryMs > System.currentTimeMillis() + 5 * 60_000

    fun persist(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(key("device"), deviceId)
            .putString(key("android"), androidId)
            .putString(key("spoffer"), spoffer)
            .putString(key("token"), token)
            .putString(key("uid"), uid)
            .putLong(key("expiry"), expiryMs)
            .apply()
    }

    fun clear(context: Context) {
        token = ""
        uid = ""
        expiryMs = 0L
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(key("token"))
            .remove(key("uid"))
            .remove(key("expiry"))
            .apply()
    }

    private fun key(suffix: String) = "db_${lang}_$suffix"

    companion object {
        private const val PREFS = "dramabox_mobile"

        fun load(context: Context, lang: String): DramaBoxMobileSession {
            val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val apiLang = lang.ifBlank { "tr" }
            fun k(s: String) = "db_${apiLang}_$s"
            val deviceId = p.getString(k("device"), null) ?: UUID.randomUUID().toString()
            val androidId = p.getString(k("android"), null) ?: randomAndroidId()
            val spoffer = p.getString(k("spoffer"), null) ?: randomIp()
            return DramaBoxMobileSession(
                deviceId = deviceId,
                androidId = androidId,
                spoffer = spoffer,
                token = p.getString(k("token"), "").orEmpty(),
                uid = p.getString(k("uid"), "").orEmpty(),
                expiryMs = p.getLong(k("expiry"), 0L),
                lang = apiLang
            )
        }

        fun randomAndroidId(): String {
            val hex = Random.nextBytes(4).joinToString("") { "%02x".format(it) }
            return "ffffffff$hex" + "0".repeat(9)
        }

        fun randomIp(): String = List(4) { Random.nextInt(256) }.joinToString(".")
    }
}
