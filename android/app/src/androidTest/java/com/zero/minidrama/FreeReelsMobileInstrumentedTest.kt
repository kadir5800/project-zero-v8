package com.zero.minidrama

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zero.minidrama.core.DramaWaveClient
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FreeReelsMobileInstrumentedTest {
    private fun client(): DramaWaveClient {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        ctx.getSharedPreferences("freereels_client", android.content.Context.MODE_PRIVATE).edit().clear().apply()
        return DramaWaveClient(ctx, DramaWaveClient.Brand.FREE_REELS)
    }

    @Test
    fun loginAndSearch() {
        val c = client()
        val (items, pages) = c.search("love", 1, "tr")
        Log.e("FR_TEST", "pages=$pages count=${items.size}")
        assertTrue("search empty", items.isNotEmpty())
    }

    @Test
    fun firstAndLastEpisodePlayUrl() {
        val c = client()
        val (items, _) = c.search("love", 1, "tr")
        assertTrue("search empty", items.isNotEmpty())
        val seriesId = items[0].optString("key").ifBlank {
            items[0].optString("id").ifBlank { items[0].optString("series_id") }
        }
        assertTrue("series id missing", seriesId.isNotBlank())
        val detail = c.bookDetail(seriesId, "tr")
        val eps = detail.optJSONArray("episodes")
        assertTrue("no episodes", eps != null && eps.length() > 0)
        val firstId = eps!!.getJSONObject(0).optString("chapter_id")
        val lastId = eps.getJSONObject(eps.length() - 1).optString("chapter_id")
        val urlFirst = c.episodeVideo(seriesId, firstId, "tr")
        Log.e("FR_TEST", "first=$urlFirst")
        assertTrue("ep1 url", urlFirst.startsWith("http"))
        runCatching { c.episodeVideo(seriesId, lastId, "tr") }
            .onSuccess { urlLast ->
                Log.e("FR_TEST", "last=$urlLast")
                assertTrue("last ep url", urlLast.startsWith("http"))
            }
            .onFailure { Log.e("FR_TEST", "last ep unlock: ${it.message}") }
    }
}
