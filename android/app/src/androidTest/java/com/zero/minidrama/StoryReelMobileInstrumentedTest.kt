package com.zero.minidrama

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zero.minidrama.core.StoryReelClient
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StoryReelMobileInstrumentedTest {
    private fun client(): StoryReelClient {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        ctx.getSharedPreferences("storyreel_client", android.content.Context.MODE_PRIVATE).edit().clear().apply()
        return StoryReelClient(ctx)
    }

    @Test
    fun loginSearchDetailAndPlayUrl() {
        val c = client()
        val (items, pages) = c.search("love", 1, "tr")
        Log.e("SR_TEST", "pages=$pages count=${items.size}")
        assertTrue("search empty", items.isNotEmpty())
        val shortPlayId = items[0].optString("shortPlayId")
        assertTrue("shortPlayId missing", shortPlayId.isNotBlank())
        val detail = c.bookDetail(shortPlayId, "tr")
        val eps = detail.optJSONArray("episodes")
        assertTrue("no episodes", eps != null && eps.length() > 0)
        val first = eps!!.getJSONObject(0)
        val dramaId = first.optString("chapter_id")
        val epNum = first.optInt("serial", 1)
        val url = c.episodeVideo(shortPlayId, dramaId, epNum, "tr", first.optBoolean("isLock"))
        Log.e("SR_TEST", "ep1 url=$url")
        assertTrue("ep1 play url", url.startsWith("http"))
        if (eps.length() > 1) {
            val last = eps.getJSONObject(eps.length() - 1)
            runCatching {
                c.episodeVideo(
                    shortPlayId,
                    last.optString("chapter_id"),
                    last.optInt("serial", eps.length()),
                    "tr",
                    last.optBoolean("isLock"),
                )
            }.onSuccess { Log.e("SR_TEST", "last url=$it") }
                .onFailure { Log.e("SR_TEST", "last ep: ${it.message}") }
        }
    }
}
