package to.ottomot.driftd.routebuilder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteBuilderMarkerLodTests {
    @Test
    fun startAndFinishStayFullEndpointPinsAtRegionalZoom() {
        val latitudeDelta = RouteBuilderConstants.REGIONAL_DOT_MIN_LATITUDE_DELTA * 2
        val start = RouteBuilderPoint(lat = 37.0, lng = -122.0, type = RouteBuilderPointType.START)
        val finish = RouteBuilderPoint(lat = 37.1, lng = -122.0, type = RouteBuilderPointType.FINISH)

        assertEquals(
            RouteBuilderMapMarkerPresentation.ENDPOINT_PIN,
            RouteBuilderMarkerLod.markerPresentation(start, RouteBuilderMapMarkerLodTier.from(latitudeDelta)),
        )
        assertEquals(1f, RouteBuilderMarkerLod.markerPinScale(start, latitudeDelta))
        assertEquals(
            RouteBuilderMapMarkerPresentation.ENDPOINT_PIN,
            RouteBuilderMarkerLod.markerPresentation(finish, RouteBuilderMapMarkerLodTier.from(latitudeDelta)),
        )
        assertEquals(1f, RouteBuilderMarkerLod.markerPinScale(finish, latitudeDelta))
    }

    @Test
    fun secondaryMarkersUseDotsAtRegionalZoom() {
        val latitudeDelta = RouteBuilderConstants.REGIONAL_DOT_MIN_LATITUDE_DELTA * 1.1
        val waypoint = RouteBuilderPoint(lat = 37.0, lng = -122.0, type = RouteBuilderPointType.WAYPOINT)
        val stop = RouteBuilderPoint(lat = 37.0, lng = -122.0, type = RouteBuilderPointType.STOP)
        val path = RouteBuilderPoint(lat = 37.0, lng = -122.0, type = RouteBuilderPointType.PATH)
        val tier = RouteBuilderMapMarkerLodTier.from(latitudeDelta)

        assertEquals(RouteBuilderMapMarkerPresentation.DOT, RouteBuilderMarkerLod.markerPresentation(waypoint, tier))
        assertEquals(RouteBuilderMapMarkerPresentation.DOT, RouteBuilderMarkerLod.markerPresentation(stop, tier))
        assertEquals(RouteBuilderMapMarkerPresentation.DOT, RouteBuilderMarkerLod.markerPresentation(path, tier))
    }

    @Test
    fun dotsUseCenterAnchorWhilePinShapedMarkersUseBottomAnchor() {
        assertTrue(routeBuilderMarkerUsesCenterAnchor(RouteBuilderMapMarkerPresentation.DOT, "waypoint"))
        assertTrue(routeBuilderMarkerUsesCenterAnchor(RouteBuilderMapMarkerPresentation.DOT, "stop"))
        assertTrue(routeBuilderMarkerUsesCenterAnchor(RouteBuilderMapMarkerPresentation.DOT, "path"))

        assertEquals(false, routeBuilderMarkerUsesCenterAnchor(RouteBuilderMapMarkerPresentation.PIN, "waypoint"))
        assertEquals(false, routeBuilderMarkerUsesCenterAnchor(RouteBuilderMapMarkerPresentation.PIN, "stop"))
        assertTrue(routeBuilderMarkerUsesCenterAnchor(RouteBuilderMapMarkerPresentation.PIN, "path"))
        assertEquals(false, routeBuilderMarkerUsesCenterAnchor(RouteBuilderMapMarkerPresentation.ENDPOINT_PIN, "start"))
    }

    @Test
    fun secondaryPinScaleInterpolatesBetweenRegionalAndCloseZoom() {
        val waypoint = RouteBuilderPoint(lat = 37.0, lng = -122.0, type = RouteBuilderPointType.WAYPOINT)
        val far = RouteBuilderConstants.REGIONAL_DOT_MIN_LATITUDE_DELTA
        val close = RouteBuilderConstants.PIN_FULL_SIZE_MAX_LATITUDE_DELTA
        val middle = (far + close) / 2.0

        assertEquals(RouteBuilderConstants.SUBTLE_MARKER_MIN_SCALE, RouteBuilderMarkerLod.markerPinScale(waypoint, far), 0.001f)
        assertEquals(1f, RouteBuilderMarkerLod.markerPinScale(waypoint, close), 0.001f)

        val middleScale = RouteBuilderMarkerLod.markerPinScale(waypoint, middle)
        assertTrue(middleScale > RouteBuilderConstants.SUBTLE_MARKER_MIN_SCALE)
        assertTrue(middleScale < 1f)
        assertEquals(
            RouteBuilderMarkerLod.pinScaleBucket(waypoint, middle),
            RouteBuilderMarkerLod.secondaryPinScaleBucket(middle),
        )
    }
}
