package com.zero.minidrama

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zero.minidrama.core.DramaBoxClient
import com.zero.minidrama.core.DramaBoxMobileClient
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DramaBoxMobileInstrumentedTest {
    @Test
    fun ep14MobilePlayUrl() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        ctx.getSharedPreferences("dramabox_mobile", android.content.Context.MODE_PRIVATE).edit().clear().apply()
        val bookId = "42000033343"
        val chapterId = "701901599"
        val url = DramaBoxMobileClient(ctx).playUrl(bookId, chapterId, 14, "tr")
        Log.e("DB_TEST", "mobile=$url diag=${DramaBoxMobileClient.lastDiagnostic}")
        assertTrue(
            "ep14 url missing: ${DramaBoxMobileClient.lastDiagnostic}",
            !url.isNullOrBlank() && url.startsWith("http")
        )
    }

    @Test
    fun ep14PlayUrlViaClient() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val client = DramaBoxClient()
        val url = client.playUrl(ctx, "42000033343", "701901599", "tr")
        Log.e("DB_TEST", "playUrl=$url")
        assertTrue("playUrl empty", url.isNotBlank())
    }
}
