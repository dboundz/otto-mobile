package to.ottomot.driftd.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MapDiscoveryMarkerLODTest {
    private val dotBoundary = MapDiscoveryMarkerLOD.regionalDotMinLatitudeDelta
    private val fullSizeBoundary = MapDiscoveryMarkerLOD.pinFullSizeMaxLatitudeDelta

    @Test
    fun presentationUsesDotBeyondRegionalBoundary() {
        assertEquals(
            MapDiscoveryMarkerPresentation.Dot,
            MapDiscoveryMarkerLOD.presentation(dotBoundary * 1.01),
        )
    }

    @Test
    fun presentationUsesPinAtRegionalBoundaryAndCloser() {
        assertEquals(MapDiscoveryMarkerPresentation.Pin, MapDiscoveryMarkerLOD.presentation(dotBoundary))
        assertEquals(MapDiscoveryMarkerPresentation.Pin, MapDiscoveryMarkerLOD.presentation(dotBoundary * 0.5))
    }

    @Test
    fun pinScaleAtZoomBoundaries() {
        assertEquals(1f, MapDiscoveryMarkerLOD.pinScale(fullSizeBoundary), 0.001f)
        assertEquals(1f, MapDiscoveryMarkerLOD.pinScale(fullSizeBoundary * 0.5), 0.001f)
        assertEquals(MapDiscoveryMarkerLOD.PIN_MIN_SCALE, MapDiscoveryMarkerLOD.pinScale(dotBoundary), 0.001f)
    }

    @Test
    fun pinScaleInterpolatesBetweenRegionalAndOneMile() {
        val midSpan = (dotBoundary + fullSizeBoundary) / 2
        val scale = MapDiscoveryMarkerLOD.pinScale(midSpan)
        assertTrue(scale > MapDiscoveryMarkerLOD.PIN_MIN_SCALE)
        assertTrue(scale < 1f)
    }

    @Test
    fun annotationRefreshIdChangesAcrossTiers() {
        val dotId =
            MapDiscoveryMarkerLOD.annotationRefreshId(
                "marker-1",
                MapDiscoveryMarkerKind.Event,
                dotBoundary * 2,
            )
        val pinId =
            MapDiscoveryMarkerLOD.annotationRefreshId(
                "marker-1",
                MapDiscoveryMarkerKind.Event,
                fullSizeBoundary,
            )
        assertNotEquals(dotId, pinId)
        assertTrue(dotId.contains("dot"))
    }

    @Test
    fun annotationRefreshIdStableWithinPinScaleBand() {
        val regionalPin =
            MapDiscoveryMarkerLOD.annotationRefreshId(
                "marker-1",
                MapDiscoveryMarkerKind.Event,
                dotBoundary * 0.99,
            )
        val midPin =
            MapDiscoveryMarkerLOD.annotationRefreshId(
                "marker-1",
                MapDiscoveryMarkerKind.Event,
                (dotBoundary + fullSizeBoundary) / 2,
            )
        assertEquals(regionalPin, midPin)
    }

    @Test
    fun pinScaleStartsAtMinAtRegionalBoundary() {
        assertEquals(MapDiscoveryMarkerLOD.PIN_MIN_SCALE, MapDiscoveryMarkerLOD.pinScale(dotBoundary), 0.001f)
    }
}
