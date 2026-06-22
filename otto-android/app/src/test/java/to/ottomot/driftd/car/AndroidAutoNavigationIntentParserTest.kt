package to.ottomot.driftd.car

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AndroidAutoNavigationIntentParserTest {
    @Test
    fun parsesQueryOnlyNavigationIntent() {
        val request =
            parseAndroidAutoNavigationRequest(
                action = ACTION_CAR_NAVIGATE,
                dataString = "geo:0,0?q=Googleplex",
            )

        assertEquals(AndroidAutoNavigationIntentKind.Navigation, request?.kind)
        assertEquals("Googleplex", request?.query)
        assertNull(request?.latitude)
        assertNull(request?.longitude)
        assertEquals("Googleplex", request?.displayName)
    }

    @Test
    fun parsesCoordinatesAndQueryNavigationIntent() {
        val request =
            parseAndroidAutoNavigationRequest(
                action = ACTION_CAR_NAVIGATE,
                dataString = "geo:1.1,2.2?q=Starbucks&intent=navigation",
            )

        assertEquals(AndroidAutoNavigationIntentKind.Navigation, request?.kind)
        assertEquals("Starbucks", request?.query)
        assertEquals(1.1, request?.latitude ?: 0.0, 0.0)
        assertEquals(2.2, request?.longitude ?: 0.0, 0.0)
        assertEquals("Starbucks", request?.displayName)
    }

    @Test
    fun parsesCoordinateOnlyNavigationIntent() {
        val request =
            parseAndroidAutoNavigationRequest(
                action = ACTION_CAR_NAVIGATE,
                dataString = "geo:1.1,2.2?mode=w&intent=navigation",
            )

        assertEquals(AndroidAutoNavigationIntentKind.Navigation, request?.kind)
        assertNull(request?.query)
        assertEquals(1.1, request?.latitude ?: 0.0, 0.0)
        assertEquals(2.2, request?.longitude ?: 0.0, 0.0)
        assertEquals("1.10000, 2.20000", request?.displayName)
    }

    @Test
    fun parsesAddStopIntent() {
        val request =
            parseAndroidAutoNavigationRequest(
                action = ACTION_CAR_NAVIGATE,
                dataString = "geo:0,0?q=1600+Amphitheatre+Parkway&intent=add_a_stop",
            )

        assertEquals(AndroidAutoNavigationIntentKind.AddStop, request?.kind)
        assertEquals("1600 Amphitheatre Parkway", request?.query)
        assertNull(request?.latitude)
        assertNull(request?.longitude)
    }

    @Test
    fun parsesActionViewAsSearchIntent() {
        val request =
            parseAndroidAutoNavigationRequest(
                action = Intent.ACTION_VIEW,
                dataString = "geo:0,0?q=Golden+Gate+Bridge",
            )

        assertEquals(AndroidAutoNavigationIntentKind.Search, request?.kind)
        assertEquals("Golden Gate Bridge", request?.query)
    }

    @Test
    fun rejectsUnsupportedAction() {
        val request =
            parseAndroidAutoNavigationRequest(
                action = Intent.ACTION_SEND,
                dataString = "geo:0,0?q=Googleplex",
            )

        assertNull(request)
    }

    @Test
    fun rejectsMalformedCoordinatesWithoutQuery() {
        val request =
            parseAndroidAutoNavigationRequest(
                action = ACTION_CAR_NAVIGATE,
                dataString = "geo:not-a-coordinate?q=",
            )

        assertNull(request)
    }

    @Test
    fun rejectsUnsupportedScheme() {
        val request =
            parseAndroidAutoNavigationRequest(
                action = ACTION_CAR_NAVIGATE,
                dataString = "https://example.com/search?q=Googleplex",
            )

        assertNull(request)
    }
}
