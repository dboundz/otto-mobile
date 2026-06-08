package to.ottomot.driftd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KlipyModelsTest {
    @Test
    fun parseListResponsePrefersSmallPreviewAndMediumSendGif() {
        val body =
            """
            {
              "result": true,
              "data": {
                "current_page": 1,
                "per_page": 24,
                "has_next": true,
                "data": [
                  {
                    "id": 42,
                    "slug": "turbo-wave",
                    "title": "Turbo wave",
                    "file": {
                      "sm": {
                        "gif": { "url": "https://static.klipy.com/sm.gif", "width": 160, "height": 90 }
                      },
                      "md": {
                        "gif": { "url": "https://static.klipy.com/md.gif", "width": 320, "height": 180 }
                      }
                    }
                  }
                ]
              }
            }
            """.trimIndent()

        val page = KlipyAPIClient.parseListResponse(body)

        assertTrue(page.hasMore)
        assertEquals(1, page.items.size)
        val item = page.items.first()
        assertEquals(42L, item.id)
        assertEquals("turbo-wave", item.slug)
        assertEquals("Turbo wave", item.title)
        assertEquals("https://static.klipy.com/sm.gif", item.previewUrl)
        assertEquals("https://static.klipy.com/md.gif", item.sendUrl)
        assertEquals(320, item.width)
        assertEquals(180, item.height)
    }

    @Test
    fun parseListResponseFallsBackToWebpAssets() {
        val body =
            """
            {
              "result": true,
              "data": {
                "current_page": 2,
                "per_page": 24,
                "has_next": false,
                "data": [
                  {
                    "slug": "webp-only",
                    "file": {
                      "sm": {
                        "webp": { "url": "https://static.klipy.com/sm.webp", "width": 120, "height": 120 }
                      },
                      "hd": {
                        "webp": { "url": "https://static.klipy.com/hd.webp", "width": 480, "height": 480 }
                      }
                    }
                  }
                ]
              }
            }
            """.trimIndent()

        val page = KlipyAPIClient.parseListResponse(body)

        assertEquals(false, page.hasMore)
        assertEquals("https://static.klipy.com/sm.webp", page.items.first().previewUrl)
        assertEquals("https://static.klipy.com/hd.webp", page.items.first().sendUrl)
    }
}
