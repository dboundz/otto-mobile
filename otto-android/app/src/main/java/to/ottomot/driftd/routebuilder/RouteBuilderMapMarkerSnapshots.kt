package to.ottomot.driftd.routebuilder

import to.ottomot.driftd.core.network.dto.RoutePointDto
import to.ottomot.driftd.routebuilder.engine.RouteLatLng
import to.ottomot.driftd.routebuilder.engine.RoutePolylineGeometry
import to.ottomot.driftd.routebuilder.engine.lat
import to.ottomot.driftd.routebuilder.engine.lng

/** Pure marker/save helpers extracted for unit tests and ViewModel reuse. */
internal object RouteBuilderMapMarkerSnapshots {
    /** Route Builder editor always shows all intentional markers regardless of zoom. */
    fun mapPointsForDisplay(state: RouteBuilderScreenState): List<Pair<RouteBuilderPoint, Int>> =
        state.points.withIndex().map { it.value to it.index }

    fun buildMarkerSnapshots(state: RouteBuilderScreenState): List<RouteBuilderMapMarkerSnapshot> {
        val snapshots =
            mapPointsForDisplay(state).map { (point, originalIndex) ->
                val presentation = RouteBuilderMarkerLod.markerPresentation(point, state.mapMarkerLodTier)
                val display = coordinateOnRouteLine(point, state.roadCoordinates)
                RouteBuilderMapMarkerSnapshot(
                    id = point.id,
                    lat = display.lat,
                    lng = display.lng,
                    markerType = point.type.rawValue,
                    isAutoShape = point.isAutoShape,
                    presentation = presentation,
                    pinScale = RouteBuilderMarkerLod.markerPinScale(point, state.mapVisibleLatitudeDelta),
                    dotColor = point.type.dotColor,
                    accessibilityTitle = point.displayTitle,
                    refreshId =
                        RouteBuilderMarkerLod.markerRefreshId(
                            point = point,
                            latitudeDelta = state.mapVisibleLatitudeDelta,
                            lodTier = state.mapMarkerLodTier,
                        ),
                    originalIndex = originalIndex,
                )
            }
        return snapshots
    }

    /**
     * Map pin position on the snapped purple line for interior markers only.
     * Start/finish keep stored coords so endpoint pins stay where the user placed them.
     */
    internal fun coordinateOnRouteLine(
        point: RouteBuilderPoint,
        roadCoordinates: List<RouteLatLng>,
    ): RouteLatLng {
        if (roadCoordinates.size < 2) return point.lat to point.lng
        if (point.type == RouteBuilderPointType.START || point.type == RouteBuilderPointType.FINISH) {
            return point.lat to point.lng
        }
        val projection =
            RoutePolylineGeometry
                .allProjectionsOntoPolyline(point.lat to point.lng, roadCoordinates)
                .minByOrNull { it.distanceMeters }
        return projection?.coordinate ?: (point.lat to point.lng)
    }
}

internal object RouteBuilderSavePayload {
    fun intentionalPoints(points: List<RouteBuilderPoint>): List<RouteBuilderPoint> =
        points.filter { !it.isAutoShape }

    fun savePointDtos(state: RouteBuilderScreenState): List<RoutePointDto> {
        val snapped =
            if (state.roadCoordinates.size >= 2) {
                state.roadCoordinates
            } else {
                state.points.map { it.lat to it.lng }
            }
        val savePoints = intentionalPoints(state.points)
        return savePoints.map {
            RoutePointDto(lat = it.lat, lng = it.lng, markerType = it.type.rawValue)
        }
    }

    fun roadCoordinateDtos(state: RouteBuilderScreenState): List<RoutePointDto> {
        val snapped: List<RouteLatLng> =
            if (state.roadCoordinates.size >= 2) {
                state.roadCoordinates
            } else {
                state.points.map { it.lat to it.lng }
            }
        return snapped.map {
            RoutePointDto(lat = it.lat, lng = it.lng, markerType = RouteBuilderPointType.PATH.rawValue)
        }
    }
}
