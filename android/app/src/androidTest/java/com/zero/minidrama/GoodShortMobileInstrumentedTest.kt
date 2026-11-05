package com.zero.minidrama

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zero.minidrama.core.GoodShortClient
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GoodShortMobileInstrumentedTest {
    private fun client(): GoodShortClient {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        ctx.getSharedPreferences("goodshort_client", android.content.Context.MODE_PRIVATE).edit().clear().apply()
        return GoodShortClient(ctx)
    }

    @Test
    fun bootstrapSearchDetailAndPlayUrl() {
        val c = client()
        val (books, pages) = c.search("love", 1, "tr")
        Log.e("GS_TEST", "pages=$pages count=${books.size}")
        assertTrue("search empty", books.isNotEmpty())
        val bookId = books[0].optString("bookId")
        assertTrue("bookId missing", bookId.isNotBlank())
        val detail = c.bookDetail(bookId, "tr")
        val eps = detail.optJSONArray("episodes")
        assertTrue("no episodes", eps != null && eps.length() > 0)
        val first = eps!!.getJSONObject(0)
        val ch = first.optString("chapter_id")
        val node = first.optString("node_id", "0")
        val url = first.optString("filePath").takeIf { it.startsWith("http") }
            ?: c.playUrl(bookId, ch, node, "tr")
        Log.e("GS_TEST", "ep1 url=$url")
        assertTrue("ep1 play url", url.startsWith("http"))
        if (eps.length() > 1) {
            val last = eps.getJSONObject(eps.length() - 1)
            runCatching {
                c.playUrl(bookId, last.optString("chapter_id"), last.optString("node_id", "0"), "tr")
            }.onSuccess { Log.e("GS_TEST", "last url=$it") }
                .onFailure { Log.e("GS_TEST", "last ep: ${it.message}") }
        }
    }
}
