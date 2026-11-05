package com.zero.minidrama.core

import android.util.Base64
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import kotlin.random.Random

/** StoryReel API uses RC4 + RSA (same as com.storyreel.tv.android). */
object StoryReelRc4 {
    private const val RSA_PUBLIC_B64 =
        "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDG3JTiyF3BfPcr/PqkJYKq6lIquEnqWlYDgg7uhPew8eYT08ob0hhxUqzUrAvtIqce0NcOUPrHSi3zj6pRGLSRK6EzC/GKYMqLP3M415ghFXKGtjpEAVohhdxHPMg40e6nZXFALrn9fIFEWuFYLLHDGObKEBVC8i8xq8R/b+zdvQIDAQAB"

    fun newApiKey(): String {
        val alphabet = "abcdefghijklmnopqrstuvwxyz0123456789"
        return buildString(32) { repeat(32) { append(alphabet[Random.nextInt(alphabet.length)]) } }
    }

    fun rsaSecretKey(apiRc4Key: String): String {
        val pub = KeyFactory.getInstance("RSA")
            .generatePublic(X509EncodedKeySpec(Base64.decode(RSA_PUBLIC_B64, Base64.NO_WRAP)))
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, pub)
        val data = apiRc4Key.toByteArray(Charsets.UTF_8)
        val out = java.io.ByteArrayOutputStream()
        var off = 0
        while (off < data.size) {
            val chunk = data.copyOfRange(off, minOf(off + 117, data.size))
            out.write(cipher.doFinal(chunk))
            off += 117
        }
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    fun md5Hex(input: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun encryptToHex(plain: String, key: String): String {
        return rc4(plain.toByteArray(Charsets.UTF_8), key).toHex()
    }

    fun decryptFromHex(hex: String, key: String): String {
        val bytes = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        return String(rc4(bytes, key), Charsets.UTF_8)
    }

    private fun rc4(data: ByteArray, key: String): ByteArray {
        val keyBytes = key.toByteArray(Charsets.UTF_8)
        val s = IntArray(256) { it }
        var j = 0
        for (i in 0 until 256) {
            j = (j + s[i] + (keyBytes[i % keyBytes.size].toInt() and 0xff)) and 0xff
            val tmp = s[i]; s[i] = s[j]; s[j] = tmp
        }
        val out = ByteArray(data.size)
        var i = 0
        j = 0
        for (n in data.indices) {
            i = (i + 1) and 0xff
            j = (j + s[i]) and 0xff
            val tmp = s[i]; s[i] = s[j]; s[j] = tmp
            val k = s[(s[i] + s[j]) and 0xff]
            out[n] = (data[n].toInt() xor k).toByte()
        }
        return out
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
