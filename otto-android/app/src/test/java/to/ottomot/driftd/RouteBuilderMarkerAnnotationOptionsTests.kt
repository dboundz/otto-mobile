package to.ottomot.driftd

import com.mapbox.maps.ViewAnnotationAnchor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToLong
import to.ottomot.driftd.routebuilder.RouteBuilderMapMarkerPresentation

class RouteBuilderMarkerAnnotationOptionsTests {
    @Test
    fun routeBuilderMarkerAnnotationAnchor_isCenterForWaypointPin() {
        assertEquals(
            ViewAnnotationAnchor.CENTER,
            routeBuilderMarkerAnnotationAnchor(
                presentation = RouteBuilderMapMarkerPresentation.PIN,
                markerType = "waypoint",
            ),
        )
    }

    @Test
    fun routeBuilderMarkerAnnotationAnchor_isCenterForPathAndDot() {
        assertEquals(
            ViewAnnotationAnchor.CENTER,
            routeBuilderMarkerAnnotationAnchor(
                presentation = RouteBuilderMapMarkerPresentation.DOT,
                markerType = "waypoint",
            ),
        )
        assertEquals(
            ViewAnnotationAnchor.CENTER,
            routeBuilderMarkerAnnotationAnchor(
                presentation = RouteBuilderMapMarkerPresentation.PIN,
                markerType = "path",
            ),
        )
    }

    @Test
    fun routeBuilderMarkerAnnotationPriority_interiorTypesAboveBaseBoost() {
        val lat = 37.7749
        val tieBreaker = 3
        val waypointPriority =
            routeBuilderMarkerAnnotationPriority(lat, tieBreaker, markerType = "waypoint")
        val startPriority =
            routeBuilderMarkerAnnotationPriority(lat, tieBreaker, markerType = "start")
        assertTrue(waypointPriority > startPriority)
    }
}
