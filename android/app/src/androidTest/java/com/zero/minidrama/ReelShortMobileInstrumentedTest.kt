package com.zero.minidrama

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zero.minidrama.core.ReelShortMobileClient
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReelShortMobileInstrumentedTest {
    @Test
    fun ep16MobilePlayUrl() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        ctx.getSharedPreferences("reelshort_mobile", android.content.Context.MODE_PRIVATE).edit().clear().apply()
        val client = ReelShortMobileClient(ctx)
        val bookId = "6a1fe445bf438d57240af4fa"
        val chapterId = "qw1fwy3k3r"
        val url = client.playUrl(bookId, chapterId, "tr")
        Log.e("RS_TEST", "playUrl=$url err=${ReelShortMobileClient.lastDiagnostic}")
        assertNotNull("mobile url missing: ${ReelShortMobileClient.lastDiagnostic}", url)
    }
}
