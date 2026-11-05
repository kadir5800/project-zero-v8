package com.zero.minidrama.core

import android.content.Context
import android.util.Base64
import org.spongycastle.asn1.pkcs.RSAPrivateKey
import org.spongycastle.jce.provider.BouncyCastleProvider
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.Security
import java.security.Signature
import java.security.spec.RSAPrivateKeySpec

/** RSA-SHA256 request signing (DramaBox Android app). */
object DramaBoxSign {
    private const val PROVIDER = "SC"

    init {
        if (Security.getProvider(PROVIDER) == null) {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    private val keyCache = mutableMapOf<Context, PrivateKey>()

    fun sign(context: Context, payload: String): String {
        val app = context.applicationContext
        val key = keyCache.getOrPut(app) { loadPrivateKey(app) }
        val sig = Signature.getInstance("SHA256withRSA", PROVIDER)
        sig.initSign(key)
        sig.update(payload.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(sig.sign(), Base64.NO_WRAP)
    }

    private fun loadPrivateKey(context: Context): PrivateKey {
        val der = context.assets.open("dramabox_pkcs1.der").use { it.readBytes() }
        val rsa = RSAPrivateKey.getInstance(der)
        val spec = RSAPrivateKeySpec(rsa.modulus, rsa.privateExponent)
        return KeyFactory.getInstance("RSA", PROVIDER).generatePrivate(spec)
    }
}
