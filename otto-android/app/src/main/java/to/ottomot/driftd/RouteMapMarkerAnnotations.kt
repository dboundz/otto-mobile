package to.ottomot.driftd

import com.mapbox.geojson.Point
import com.mapbox.maps.ViewAnnotationAnchor
import com.mapbox.maps.viewannotation.annotationAnchor
import com.mapbox.maps.viewannotation.geometry
import com.mapbox.maps.viewannotation.viewAnnotationOptions
import kotlin.math.roundToLong
import to.ottomot.driftd.routebuilder.RouteBuilderMapMarkerPresentation

private const val ENDPOINT_MARKER_PRIORITY_BOOST = 200_000_000L
private const val PRESENCE_MARKER_PRIORITY_BOOST = 150_000_000L
/** Route Builder editor: all intentional markers need high overlap priority (not just start/finish). */
private const val ROUTE_BUILDER_EDITOR_MARKER_PRIORITY_BOOST = 200_000_000L

internal fun mapMarkerOverlapPriority(
    lat: Double,
    markerType: String? = null,
    tieBreaker: Int = 0,
): Long {
    if (!lat.isFinite()) return tieBreaker.toLong()
    val latMicrodegrees = (lat * 1_000_000).roundToLong()
    var priority = -latMicrodegrees + tieBreaker
    if (markerType == "start" || markerType == "finish") {
        priority += ENDPOINT_MARKER_PRIORITY_BOOST
    }
    return priority
}

internal fun routeMapMarkerOverlapPriority(
    lat: Double,
    markerType: String? = null,
    tieBreaker: Int = 0,
    distanceMeters: Double? = null,
    useDriveHorizon: Boolean = false,
): Long {
    if (useDriveHorizon && distanceMeters != null && distanceMeters.isFinite()) {
        return MapDriveHorizonDepth.driveRouteOverlapPriority(distanceMeters, markerType, tieBreaker)
    }
    return mapMarkerOverlapPriority(lat, markerType, tieBreaker)
}

internal fun mapPresenceOverlapPriority(
    lat: Double,
    tieBreaker: Int = 0,
    distanceMeters: Double? = null,
    useDriveHorizon: Boolean = false,
): Long {
    if (useDriveHorizon && distanceMeters != null && distanceMeters.isFinite()) {
        return MapDriveHorizonDepth.drivePresenceOverlapPriority(distanceMeters, tieBreaker)
    }
    return mapMarkerOverlapPriority(lat, tieBreaker = tieBreaker) + PRESENCE_MARKER_PRIORITY_BOOST
}

internal fun routeBuilderMarkerAnnotationAnchor(
    presentation: RouteBuilderMapMarkerPresentation,
    markerType: String?,
): ViewAnnotationAnchor =
    when {
        presentation == RouteBuilderMapMarkerPresentation.DOT -> ViewAnnotationAnchor.CENTER
        markerType == "start" || markerType == "finish" -> ViewAnnotationAnchor.BOTTOM
        else -> ViewAnnotationAnchor.CENTER
    }

/** Route Builder editor: interior markers need higher overlap priority than endpoints/line. */
private const val ROUTE_BUILDER_INTERIOR_MARKER_PRIORITY_BOOST = 50_000_000L

internal fun routeBuilderMarkerAnnotationPriority(
    lat: Double,
    tieBreaker: Int = 0,
    markerType: String? = null,
): Long {
    val latMicrodegrees = (lat * 1_000_000).roundToLong()
    var priority = -latMicrodegrees + tieBreaker + ROUTE_BUILDER_EDITOR_MARKER_PRIORITY_BOOST
    if (markerType == "waypoint" || markerType == "stop" || markerType == "path") {
        priority += ROUTE_BUILDER_INTERIOR_MARKER_PRIORITY_BOOST
    }
    return priority
}

internal fun routeBuilderMarkerAnnotationOptions(
    point: Point,
    markerType: String?,
    presentation: RouteBuilderMapMarkerPresentation,
    tieBreaker: Int = 0,
) =
    viewAnnotationOptions {
        geometry(point)
        annotationAnchor { anchor(routeBuilderMarkerAnnotationAnchor(presentation, markerType)) }
        allowOverlap(true)
        ignoreCameraPadding(true)
        priority(routeBuilderMarkerAnnotationPriority(point.latitude(), tieBreaker, markerType))
    }

internal fun routeMapMarkerAnnotationOptions(
    point: Point,
    markerType: String? = null,
    tieBreaker: Int = 0,
    distanceMeters: Double? = null,
    useDriveHorizon: Boolean = false,
) =
    viewAnnotationOptions {
        geometry(point)
        annotationAnchor { anchor(ViewAnnotationAnchor.CENTER) }
        allowOverlap(true)
        ignoreCameraPadding(true)
        allowOverlapWithPuck(useDriveHorizon)
        priority(
            routeMapMarkerOverlapPriority(
                point.latitude(),
                markerType,
                tieBreaker,
                distanceMeters,
                useDriveHorizon,
            ),
        )
    }

internal fun mapDiscoveryMarkerAnnotationOptions(
    point: Point,
    tieBreaker: Int = 0,
) =
    viewAnnotationOptions {
        geometry(point)
        annotationAnchor { anchor(ViewAnnotationAnchor.BOTTOM) }
        allowOverlap(true)
        priority(mapMarkerOverlapPriority(point.latitude(), tieBreaker = tieBreaker))
    }

internal fun mapPresenceMarkerAnnotationOptions(
    point: Point,
    tieBreaker: Int = 0,
    distanceMeters: Double? = null,
    useDriveHorizon: Boolean = false,
) =
    viewAnnotationOptions {
        geometry(point)
        annotationAnchor { anchor(ViewAnnotationAnchor.BOTTOM) }
        allowOverlap(true)
        if (useDriveHorizon) {
            ignoreCameraPadding(true)
            allowOverlapWithPuck(true)
        }
        priority(
            mapPresenceOverlapPriority(
                point.latitude(),
                tieBreaker,
                distanceMeters,
                useDriveHorizon,
            ),
        )
    }
