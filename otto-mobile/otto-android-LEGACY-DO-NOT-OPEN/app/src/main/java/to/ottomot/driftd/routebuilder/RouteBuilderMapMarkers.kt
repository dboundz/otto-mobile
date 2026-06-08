package to.ottomot.driftd.routebuilder

import to.ottomot.driftd.core.event.haversineMeters

/** Compose / androidTest tags for Route Builder map host. */
object RouteBuilderMarkerTestTags {
    const val routeBuilderMap = "route-builder-map"
}

internal fun nearestRouteBuilderMarkerId(
    lat: Double,
    lng: Double,
    markers: List<RouteBuilderMapMarkerSnapshot>,
    maxDistanceMeters: Double = RouteBuilderConstants.ROUTE_MARKER_LONG_PRESS_EXCLUSION_METERS,
): String? {
    var nearest: RouteBuilderMapMarkerSnapshot? = null
    var nearestDistance = Double.MAX_VALUE
    markers.forEach { marker ->
        val distance = haversineMeters(lat, lng, marker.lat, marker.lng)
        if (distance <= maxDistanceMeters && distance < nearestDistance) {
            nearest = marker
            nearestDistance = distance
        }
    }
    return nearest?.id
}
