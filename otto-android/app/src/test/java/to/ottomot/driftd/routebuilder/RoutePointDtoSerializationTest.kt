package to.ottomot.driftd.routebuilder

import com.google.gson.GsonBuilder
import org.junit.Assert.assertTrue
import org.junit.Test
import to.ottomot.driftd.core.network.dto.CreateRouteRequestDto
import to.ottomot.driftd.core.network.dto.RoutePointDto

class RoutePointDtoSerializationTest {
    private val gson = GsonBuilder().serializeNulls().create()

    @Test
    fun routePointDto_serializesMarkerTypeAsTypeKey() {
        val json =
            gson.toJson(
                RoutePointDto(lat = 37.0, lng = -122.0, markerType = "waypoint"),
            )
        assertTrue(json.contains("\"type\":\"waypoint\""))
    }

    @Test
    fun createRouteRequest_includesWaypointTypesInPointsArray() {
        val json =
            gson.toJson(
                CreateRouteRequestDto(
                    name = "Test",
                    points =
                        listOf(
                            RoutePointDto(lat = 37.0, lng = -122.0, markerType = "start"),
                            RoutePointDto(lat = 37.1, lng = -122.1, markerType = "waypoint"),
                            RoutePointDto(lat = 37.2, lng = -122.2, markerType = "finish"),
                        ),
                    roadCoordinates =
                        listOf(
                            RoutePointDto(lat = 37.0, lng = -122.0, markerType = "path"),
                            RoutePointDto(lat = 37.2, lng = -122.2, markerType = "path"),
                        ),
                    distanceMeters = 1000.0,
                    etaSeconds = 120.0,
                ),
            )
        assertTrue(json.contains("\"type\":\"waypoint\""))
        assertTrue(json.contains("\"type\":\"start\""))
    }
}
