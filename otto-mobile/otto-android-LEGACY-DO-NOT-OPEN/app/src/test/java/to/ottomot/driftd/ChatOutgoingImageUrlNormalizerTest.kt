package to.ottomot.driftd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatOutgoingImageUrlNormalizerTest {
    @Test
    fun promotesDirectGifAndStripsBody() {
        val url = "https://static.klipy.com/example/test.gif"
        val result = ChatOutgoingImageUrlNormalizer.normalize(url)
        assertEquals(url, result.imageUrl)
        assertEquals("", result.body)
    }

    @Test
    fun keepsCaptionWhenGifUrlAndText() {
        val url = "https://cdn.example.com/a.gif"
        val result = ChatOutgoingImageUrlNormalizer.normalize("$url nice one")
        assertEquals(url, result.imageUrl)
        assertEquals("nice one", result.body)
    }

    @Test
    fun nonImageUrlStaysInBody() {
        val result = ChatOutgoingImageUrlNormalizer.normalize("https://example.com/article")
        assertNull(result.imageUrl)
        assertEquals("https://example.com/article", result.body)
    }
}
