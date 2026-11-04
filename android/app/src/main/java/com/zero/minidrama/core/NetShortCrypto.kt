package com.zero.minidrama.core

import android.util.Base64
import org.json.JSONObject
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

object NetShortCrypto {
    private const val PUBLIC_KEY = """-----BEGIN PUBLIC KEY-----
MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA2poXMstZ8NCWE7915MXz
DWC5/t+oB2waGfskPqSZwLqxd4ZBR0H1cb1tAZRZcV7P+LmOd6SYNxhnELaWuKTD
+D3xkz8Tt1L5j/ynGqVt1MDbiQIEzXQKUkNDSH6T0A+Xzo/67/8QOQXlVJfW06re
sbaeNvibfx6Qc78j96bCIPlxPrtieilVTBHUFOXjirxK/ki/mO8P2smRbpt73fsQ
WdGmTGMfYGvfPApGyxbxLkL/qrBjU25XpM8a0MBqzFWUAchHmqSBJ6Mbfam1SSgf
3b2U28s67nOW+JiOrhd6iVLcsLFxXA54HX+Zbej3AbOB6jKaEmp/bz1amneE1NYX
wwIDAQAB
-----END PUBLIC KEY-----"""

    private const val PRIVATE_KEY = """-----BEGIN PRIVATE KEY-----
MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQCK0Tl1pd7bjTRU93bWoHW1hLCD
j2+9bg1MgY8j5C7xXaw6bJfToXhWbH1fXNbnFFVqxyYNErcuOUwJZxyDgcxUXM4yWnRseb2GF97G
OicAQ2keDzVYmwky4lrSRwvcXutJRLPUCRQNfc6upfk2G5TKh6/CcP4TV1eXTF7+vdEw2SHxAOIT
KbSfcaZXr/hVs6a1aRHsBF+7RG99ebwZIP6/AgIyqX9RbDVN6ixi1v2G3/bwAULHLSqGdSaqij/c
a17fbFGITaeCeEaZ6d/P4ZuOK+PEPdbPQt6SbY4lZaYwRvdrpH73kigPITgDzIDONFybJ1m7wRKl
q1wxWHwbimptAgMBAAECggEAPz3cYJXFtt5YphDrahJGLgEabYVOUc2ub1li/eX54OpdCWzpqneY
nD7myyg/m5zu4SuDUVdibsOZuXrpSZw7m3+ATP5apgS8bDe5vTNHC16qqBAjrI9NHIp09/F4HNh9
dq6/Am10XkUfgP+KTrU4DyDL2NijV+pltD8N1B5kDE1igokVcsavhnu2INoMRXYE78Wq6urNECuF
Ww9hldv81M9m2w56t1CQOUukpo4mfmLjZRe2s+kwtcBVefGHP8Cj0OeH2dGltjl2YSQMRBFUCVoix
YpOrcjIHoqzWri8IfUZ2tW+nUvHl5IZ9RVxefnFaLGnxiXd2sk6Sn4aD/l9YQKBgQDVv3HaOZxHRq
lNSPrNGqplGhE066HnDsq6MlPukiovxE43CRBmpTnk9zDCqrDh9t2HbJuao7nSq5WlBERWgwqXU/q
DpH43W7Y/lJfHkDv6A2m0viJa0a9x8+CJpNnCDu1ATo4/IQKwoXYice6JKnUyXgkGKn+HipiN6tO0
EtWHlQKBgQCmQfklKFtXtm/FZ6NIMs+d+EyvaE5xNLKGYQxmiCR10WGYd8ZV+K0Q6qXHS+a32TirW
B9F3TqPOklTytMrfPZB3BCXj4weEldb8W716G8FYf7LLhaT+MdpF7KDcruObwoQAvKV3N4eX6tUEM
mdrx9hpCmmIU5EeXUkhGdmwk7BeQKBgAIXMkThJV8pGMTRvuo8pYgBnkN3PoklAuSZU2rU8Sawc9d
j9k4atZtAs7BjvQEoyffmHwt/KHUgCoGnrgdulq7uOlgJRtbBxeGPUYC5L2z9lY4YAfwDawThTsPp
4dtdDAMCAbAqYX1axu4FUUD0MltAwjPWPJMVzvIsZs+vE3mVAoGAJPja3OaCmZjadj2709xoyypic
0dw2j/ry3JdfZec9A5h87P/CTNJ2U81GoLIhe3qakAohDLUSPGfSOD74NnjMXYswmeLs0xE3Q9tq4
XK2pmWPby8DJ/wSHCapByplN0gkbr2E1mQk5SW1xT8oPJGukH1eRpC+3s/D6XaEMH5HZECgYEAigo
X5l39LDsCgeaUcI4S9grkaas/WsKv37eqo3oD9Qk6VFiMM5L5Zig6aXJxuAPLVjb38caJRPmPmOXL
T2kEP1E1h6OJOhEhETwVIUtcBzsK25ju9LqL89bC+W0uS7BPvk6Tcws/tXHCkQCTgb9jVXceZ2ox+
6axvlW/5WgHt5Q=
-----END PRIVATE KEY-----"""

    private val publicKey: PublicKey by lazy { parsePublic(PUBLIC_KEY) }
    private val privateKey: PrivateKey by lazy { parsePrivate(PRIVATE_KEY) }

    data class EncryptedPayload(val bodyB64: String, val headerKeyB64: String)

    fun encryptPayload(payload: JSONObject): EncryptedPayload {
        val aesKeyStr = genAesKey(32)
        val aesKeyBytes = aesKeyStr.toByteArray(Charsets.UTF_8)
        val aesCipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        aesCipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(aesKeyBytes, "AES"))
        val encrypted = aesCipher.doFinal(payload.toString().toByteArray(Charsets.UTF_8))
        val bodyB64 = Base64.encodeToString(encrypted, Base64.NO_WRAP)
        val aesKeyB64 = Base64.encodeToString(aesKeyBytes, Base64.NO_WRAP)
        val rsaCipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        rsaCipher.init(Cipher.ENCRYPT_MODE, publicKey)
        val headerKeyB64 = Base64.encodeToString(
            rsaCipher.doFinal(aesKeyB64.toByteArray(Charsets.UTF_8)),
            Base64.NO_WRAP
        )
        return EncryptedPayload(bodyB64, headerKeyB64)
    }

    fun decryptResponse(serverKey: String, bodyText: String): JSONObject {
        val raw = bodyText.trim()
        if (raw.startsWith("{") || raw.startsWith("[")) {
            return runCatching { JSONObject(raw) }.getOrElse {
                // body may be a JSON-encoded base64 string
                val unquoted = runCatching { JSONObject("{\"v\":$raw}").optString("v") }.getOrNull()
                if (!unquoted.isNullOrBlank() && (unquoted.startsWith("{") || unquoted.startsWith("["))) {
                    return JSONObject(unquoted)
                }
                throw it
            }
        }

        return try {
            decryptEncrypted(serverKey, raw)
        } catch (first: Exception) {
            // Some responses wrap ciphertext in quotes
            val unwrapped = unwrapQuoted(raw)
            if (unwrapped != raw) {
                try {
                    return decryptEncrypted(serverKey, unwrapped)
                } catch (_: Exception) {
                }
            }
            throw IllegalStateException(friendlyError(first), first)
        }
    }

    private fun decryptEncrypted(serverKey: String, rawBody: String): JSONObject {
        val rsaCipher = Cipher.getInstance("RSA/ECB/NoPadding")
        rsaCipher.init(Cipher.DECRYPT_MODE, privateKey)
        val rsaDecrypted = rsaCipher.doFinal(decodeB64(serverKey))

        var start = -1
        for (i in 2 until rsaDecrypted.size) {
            if (rsaDecrypted[i] == 0.toByte()) {
                start = i + 1
                break
            }
        }
        if (start < 0) throw IllegalStateException("AES key parse failed")

        val aesKeyB64 = String(rsaDecrypted, start, rsaDecrypted.size - start, Charsets.UTF_8)
            .trim { it <= ' ' || it == '\u0000' }
            .replace("\u0000", "")
        val aesKey = decodeB64(aesKeyB64)
        if (aesKey.isEmpty()) throw IllegalStateException("AES key empty")

        val aesCipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        aesCipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(aesKey, "AES"))
        val plainBytes = aesCipher.doFinal(decodeB64(rawBody))
        val plain = String(plainBytes, Charsets.UTF_8).trim()
        if (plain.startsWith("{") || plain.startsWith("[")) {
            return JSONObject(plain)
        }
        throw IllegalStateException("NetShort gecersiz yanit")
    }

    private fun unwrapQuoted(text: String): String {
        var t = text.trim()
        if ((t.startsWith("\"") && t.endsWith("\"")) || (t.startsWith("'") && t.endsWith("'"))) {
            t = runCatching { JSONObject("{\"v\":$t}").getString("v") }.getOrElse {
                t.substring(1, t.length - 1)
            }
        }
        return t
    }

    private fun cleanB64(text: String): String {
        var t = unwrapQuoted(text).trim()
        // URL-safe -> standard
        t = t.replace('-', '+').replace('_', '/')
        // drop whitespace / invalid chars
        t = t.replace(Regex("[^A-Za-z0-9+/=]"), "")
        return padB64(t)
    }

    private fun padB64(text: String): String {
        val pad = text.length % 4
        return if (pad == 0) text else text + "=".repeat(4 - pad)
    }

    private fun decodeB64(text: String): ByteArray {
        val cleaned = cleanB64(text)
        if (cleaned.isBlank()) throw IllegalArgumentException("bad base-64")
        val attempts = intArrayOf(
            Base64.DEFAULT,
            Base64.NO_WRAP,
            Base64.URL_SAFE or Base64.NO_WRAP,
            Base64.URL_SAFE
        )
        var last: Exception? = null
        for (flags in attempts) {
            try {
                return Base64.decode(cleaned, flags)
            } catch (e: Exception) {
                last = e
            }
        }
        // final fallback: also try original with URL_SAFE without cleaning dashes
        try {
            return Base64.decode(text.trim(), Base64.URL_SAFE or Base64.NO_WRAP)
        } catch (e: Exception) {
            last = e
        }
        throw IllegalArgumentException(last?.message ?: "bad base-64")
    }

    private fun friendlyError(e: Exception): String {
        val msg = e.message.orEmpty()
        return when {
            msg.contains("bad base-64", ignoreCase = true) ||
                msg.contains("bad base64", ignoreCase = true) ->
                "NetShort oturum cozulemedi. Tekrar deneniyor..."
            else -> msg.ifBlank { "NetShort sifre cozme hatasi" }
        }
    }

    private fun genAesKey(length: Int): String {
        val alphabet = "abcdefghijklmnopqrstuvwxyz0123456789"
        val random = SecureRandom()
        return buildString(length) {
            repeat(length) { append(alphabet[random.nextInt(alphabet.length)]) }
        }
    }

    private fun parsePublic(pem: String): PublicKey {
        val raw = pem.replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace("\\s".toRegex(), "")
        val bytes = Base64.decode(raw, Base64.DEFAULT)
        return KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(bytes))
    }

    private fun parsePrivate(pem: String): PrivateKey {
        val raw = pem.replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("\\s".toRegex(), "")
        val bytes = Base64.decode(raw, Base64.DEFAULT)
        return KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(bytes))
    }
}
