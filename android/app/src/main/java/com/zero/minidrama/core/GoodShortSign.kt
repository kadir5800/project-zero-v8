package com.zero.minidrama.core

import com.lib.http.common.GoodShortHttpBase64
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec

/** RSA request sign used by GoodReels / GoodShort API (com.newreading.net.c.a). */
object GoodShortSign {
    /** Matches HttpGlobal sign suffix after package name is resolved (not "-1"). */
    private const val PACKAGE_SUFFIX = "com.newreading.goodreels"
    /** MD5 hex (uppercase) of the official GoodReels signing certificate. */
    private const val OFFICIAL_CERT_MD5 = "919326DD9A742D064502468E1BF11144"

    private const val PRIVATE_PKCS8_B64 =
        "MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQDLwHQE9g2+/DJmiVtNS6v0SmHKAEzGaClFMHMNszfi5GDkrA9SjT1Z87tScD2feSSuuKJIoSYL1o3mqgmX2pCN/5rPzOifJ6gQjGZjLuvuxmvSRk8jHqsPoqj/QH6qhF5n2Mpr5oirGD9IUg3RRtdy0RfRuXxHLCnYhKfXKP+TdcK/1Hi/vPONlCpJo7ph62KVeUD+qACYZUNjdiZxklZZASac24OjBdFZOd/ZtINM4wOaHFpwHmnoq89qM1MP09gJc7tPlPONFoWIAVpfwjtjRsCbFuGQdBf7FlEsxivef2nHFN8J9Q+aqE1wr+ouNbbUkEebYqbt2ROTPe7xCJnfAgMBAAECggEBAK3nFR8m45SerGXX1pWigKGA2vYOS3kMbi0frROEY67EPe7u7CUJZ9Pes4MpSW9TdnuqGtjishZoibTWbFmvsrF/+CJkQieVMVzueHUvFzA1KtHOML1I77fonVU/Nt1THUCFSD/QA9YEW/7eCe0VCc51qF7YcbpNd2nVz2tVEs5Hrb1Q0WSdxXaIIyFH4vNS9Xgx4ZY2ULzaJePCbEZcUwFLiJQtIWslGcCDALFyPMN6W9PKMFo96l0+KruleKfuiTCNzG94Vxe3ClAO64VIa65cSXu6DSUxiD1kedxDPRNEsZU9qfwNi2gFJCa97KUMBcL2M4VV8guIh4QXQwGoTAECgYEA896OhcFRQXryCAHR1+pLx3+aqM7qKf9qJWGh+lw/FWBB7cYQ3p+e9BomoQS5OdQcLGy2cTc38zMVvESNEk+VS81VnCnVDTKC9vGPvuRItas5RKjNxAQEMJdudGlweEwQqkDlFRktuXwXIRfmzoA2iV+OUkJW5CDw81+hqMMsRpsCgYEA1eMJiw98fdeya1FJ9PE5x9lZyjyFIQ3ydsRtSetmeDIETX2AlkHC0HqySbjsIyXsxmq8AajfHraShf6eZeOBsP+sfOPS6J+jN9reS7gpVbl7EYL3D6OVMBuwZYv9ILkWj6lpfrFBvK8v32eRXgJjJ9LBkAXROBpCQPMHvPwpTA0CgYA989cPIbpLwTkFUbkGeg4AQ2l94vrX6nwDvRbSLGcWPhrhlcSpWbGe35napAGOMFVr7741asq67MpjxqJz+WW7GRHbl0D5llBw/ZL/8qyKAlKNH7kOR9rsoTu9NSAOX3yIU+4eewQDsAOMM6893JJ+OZlFSncag0fS/ANshRCVawKBgQCfrNUNCcyorgS29YK+5+948RyFTFUe7iia3d2xF5nyFXT83LrIceOcfFzpiLJRMxjmr/wXSRj49tfATOu3qPbDSrxcqEBmBfd11WGrKZtCMixcUGddN4RC3Aj+ZlncuhDLw2/Mc0xeLnMQ12LAygt4SXDTsmQU/BWGI2kdfyrdaQKBgDdzsoRSOh+tBtSRqEyc4BgtfK/FyU7VQy3fUlaaBJEUY6FxE8Icn34VVEeN6YOEmAewEcyMcQQX2ZwQ2wITv4NssL8uDJ0y4K/TlL+2bomXri/nobvyaDsMfrX6grlQXe4YzVpzGUFbcW+QValHpacnAjGejZkLM7KD2XaK1Ppf"

    /** @param requestBodyJson exact JSON sent in POST body (included in sign per HttpGlobal). */
    fun sign(
        timestampMs: Long,
        requestBodyJson: String,
        deviceId: String,
        androidId: String,
        authToken: String,
        packageSuffix: String = PACKAGE_SUFFIX,
        useLibBase64: Boolean = true,
    ): String {
        val payload = buildString {
            append("timestamp=")
            append(timestampMs)
            append(requestBodyJson)
            append(deviceId)
            append(androidId)
            append(authToken)
            append(OFFICIAL_CERT_MD5)
            append(packageSuffix)
        }
        val privateKey = KeyFactory.getInstance("RSA")
            .generatePrivate(PKCS8EncodedKeySpec(android.util.Base64.decode(PRIVATE_PKCS8_B64, android.util.Base64.DEFAULT)))
        val signature = Signature.getInstance("SHA256WithRSA")
        signature.initSign(privateKey)
        signature.update(payload.toByteArray(Charsets.UTF_8))
        val raw = signature.sign()
        return if (useLibBase64) {
            GoodShortHttpBase64.encode(raw)
        } else {
            android.util.Base64.encodeToString(raw, android.util.Base64.NO_WRAP)
        }
    }
}