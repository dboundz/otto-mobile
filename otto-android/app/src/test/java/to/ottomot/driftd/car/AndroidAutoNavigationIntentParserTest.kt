package to.ottomot.driftd.car

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import to.ottomot.driftd.ACTION_PHONE_NAVIGATE
import to.ottomot.driftd.NavigationIntentKind
import to.ottomot.driftd.parseNavigationIntentRequest

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

    @Test
    fun parsesPhoneNavigationIntent() {
        val request =
            parseNavigationIntentRequest(
                action = ACTION_PHONE_NAVIGATE,
                dataString = "geo:0,0?q=Golden+Gate+Bridge&intent=navigation",
                acceptedNavigateActions = setOf(ACTION_PHONE_NAVIGATE),
                acceptsSearchAction = false,
            )

        assertEquals(NavigationIntentKind.Navigation, request?.kind)
        assertEquals("Golden Gate Bridge", request?.query)
        assertNull(request?.latitude)
        assertNull(request?.longitude)
    }

    @Test
    fun rejectsPhoneActionViewGeoIntent() {
        val request =
            parseNavigationIntentRequest(
                action = Intent.ACTION_VIEW,
                dataString = "geo:0,0?q=Golden+Gate+Bridge",
                acceptedNavigateActions = setOf(ACTION_PHONE_NAVIGATE),
                acceptsSearchAction = false,
            )

        assertNull(request)
    }

    @Test
    fun selectsConfirmedFullAddressNavigationResult() {
        val selected =
            selectAndroidAutoNavigationDestination(
                kind = AndroidAutoNavigationIntentKind.Navigation,
                query = "Starbucks Coffee Company, 2 Fairfield Boulevard, Ponte Vedra Beach, FL, United States",
                destinations =
                    listOf(
                        AndroidAutoNavigationDestination(
                            id = "starbucks-fairfield",
                            name = "Starbucks Coffee Company",
                            address = "2 Fairfield Boulevard, Ponte Vedra Beach, FL, United States",
                            latitude = 30.2154,
                            longitude = -81.3852,
                            confidence = 0.72,
                            source = "stadia",
                        ),
                        AndroidAutoNavigationDestination(
                            id = "ponte-vedra",
                            name = "Ponte Vedra Beach",
                            address = "Ponte Vedra Beach, FL, USA",
                            latitude = 30.2397,
                            longitude = -81.3856,
                            confidence = 0.99,
                            source = "stadia",
                        ),
                    ),
            )

        assertEquals("starbucks-fairfield", selected?.id)
    }

    @Test
    fun keepsGenericNavigationQueryAmbiguous() {
        val selected =
            selectAndroidAutoNavigationDestination(
                kind = AndroidAutoNavigationIntentKind.Navigation,
                query = "Starbucks",
                destinations =
                    listOf(
                        AndroidAutoNavigationDestination(
                            id = "starbucks-a",
                            name = "Starbucks",
                            address = "1 Main Street",
                            latitude = 30.0,
                            longitude = -81.0,
                        ),
                        AndroidAutoNavigationDestination(
                            id = "starbucks-b",
                            name = "Starbucks",
                            address = "2 Main Street",
                            latitude = 30.1,
                            longitude = -81.1,
                        ),
                    ),
            )

        assertNull(selected)
    }

    @Test
    fun doesNotAutoStartConfirmedQueryWhenTopResultDoesNotMatch() {
        val selected =
            selectAndroidAutoNavigationDestination(
                kind = AndroidAutoNavigationIntentKind.Navigation,
                query = "Starbucks Coffee Company, 2 Fairfield Boulevard, Ponte Vedra Beach, FL, United States",
                destinations =
                    listOf(
                        AndroidAutoNavigationDestination(
                            id = "ponte-vedra",
                            name = "Ponte Vedra Beach",
                            address = "Ponte Vedra Beach, FL, USA",
                            latitude = 30.2397,
                            longitude = -81.3856,
                            confidence = 0.99,
                            source = "stadia",
                        ),
                        AndroidAutoNavigationDestination(
                            id = "south-ponte-vedra",
                            name = "South Ponte Vedra Beach",
                            address = "South Ponte Vedra Beach, FL, USA",
                            latitude = 30.102,
                            longitude = -81.389,
                            confidence = 0.98,
                            source = "stadia",
                        ),
                    ),
            )

        assertNull(selected)
    }
}
