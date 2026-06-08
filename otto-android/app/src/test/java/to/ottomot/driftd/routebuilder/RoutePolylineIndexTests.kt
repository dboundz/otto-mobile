package to.ottomot.driftd.routebuilder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import to.ottomot.driftd.routebuilder.engine.RoutePolylineGeometry
import to.ottomot.driftd.routebuilder.engine.RoutePolylineIndex
import to.ottomot.driftd.routebuilder.engine.lat
import to.ottomot.driftd.routebuilder.engine.lng

class RoutePolylineIndexTests {
    @Test
    fun indexedProjectionMatchesNaiveProjectionOnStraightLine() {
        val line = straightPolyline(lengthMeters = 25_000.0, pointCount = 500)
        val queryLat = line[120].lat + 0.0004
        val queryLng = line[120].lng + 0.0004
        val query = queryLat to queryLng

        val indexed = RoutePolylineIndex(lineCoordinates = line).projectOntoPolyline(query)
        val naive = RoutePolylineGeometry.projectOntoPolyline(query, line)

        assertNotNull(indexed)
        assertNotNull(naive)
        assertEquals(naive!!.segmentIndex, indexed!!.segmentIndex)
        assertEquals(naive.distanceMeters, indexed.distanceMeters, 1.0)
    }

    @Test
    fun preferredArcLengthSearchUsesNearbySegment() {
        val line = straightPolyline(lengthMeters = 12_000.0, pointCount = 120)
        val index = RoutePolylineIndex(lineCoordinates = line)
        val targetArcLength = RoutePolylineGeometry.polylineTotalLength(line) * 0.55
        val query =
            RoutePolylineGeometry.coordinateAtArcLength(targetArcLength, line)
                ?: line[line.size / 2]

        val projection =
            index.projectOntoPolyline(
                coordinate = (query.lat + 0.0008) to (query.lng + 0.0008),
                preferredArcLength = targetArcLength,
            )

        assertNotNull(projection)
        assertEquals(targetArcLength, projection!!.arcLengthMeters, 250.0)
    }
}
