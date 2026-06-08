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
        assertNull(result.klipyShare)
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

    @Test
    fun pendingKlipyGifUsesSendUrlAndKeepsCaption() {
        val attachment =
            ChatPendingComposerAttachment(
                kind = ChatPendingComposerAttachmentKind.KlipyGif,
                klipyGif =
                    KlipyGifSelection(
                        slug = "funny-car",
                        title = "Funny car",
                        previewUrl = "https://static.klipy.com/preview.webp",
                        sendUrl = "https://static.klipy.com/send.gif",
                        width = 320,
                        height = 180,
                    ),
                klipySearchQuery = "car",
            )

        val result = ChatOutgoingImageUrlNormalizer.normalize("  nice one  ", attachment)

        assertEquals("nice one", result.body)
        assertEquals("https://static.klipy.com/send.gif", result.imageUrl)
        assertEquals("funny-car", result.klipyShare?.slug)
        assertEquals("car", result.klipyShare?.searchQuery)
    }
}
