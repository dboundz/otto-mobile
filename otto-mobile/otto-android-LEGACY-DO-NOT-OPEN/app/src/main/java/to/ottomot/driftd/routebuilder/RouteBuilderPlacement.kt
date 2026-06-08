package to.ottomot.driftd.routebuilder

import com.mapbox.maps.MapboxMap
import com.mapbox.maps.ScreenCoordinate
import to.ottomot.driftd.core.event.haversineMeters
import to.ottomot.driftd.routebuilder.engine.RouteLatLng
import to.ottomot.driftd.routebuilder.engine.lat
import to.ottomot.driftd.routebuilder.engine.lng
import kotlin.math.cos
import kotlin.math.pow

/** Rejects continental mis-placements before auto-fit camera flies to route center. */
object RouteBuilderPlacementSanity {
    /** ~50 miles — local guided routes should be well below this. */
    const val MAX_GUIDED_START_FINISH_METERS = 80_467.2

    fun isImplausibleGuidedSpan(
        start: RouteLatLng?,
        finish: RouteLatLng?,
    ): Boolean {
        if (start == null || finish == null) return false
        return haversineMeters(start.lat, start.lng, finish.lat, finish.lng) > MAX_GUIDED_START_FINISH_METERS
    }
}

object RouteBuilderPlacement {
    fun crosshairCenter(
        mapWidthPx: Float,
        mapHeightPx: Float,
        sheetVisibleHeightPx: Float,
        bottomSafeAreaPx: Float,
    ): Pair<Float, Float> {
        val visibleMapBottom = (mapHeightPx - sheetVisibleHeightPx - bottomSafeAreaPx).coerceAtLeast(0f)
        return mapWidthPx / 2f to visibleMapBottom / 2f
    }

    /** Matches iOS `OttoMapboxCamera.region(for:)` — fallback before Mapbox is ready. */
    fun cameraRegionFromMapbox(
        centerLat: Double,
        centerLng: Double,
        zoom: Double,
    ): RouteBuilderCameraRegion {
        val clampedZoom = zoom.coerceIn(4.0, 21.0)
        val longitudeDelta = 360.0 / 2.0.pow(clampedZoom)
        val cosLat = cos(Math.toRadians(centerLat)).coerceAtLeast(0.2)
        val latitudeDelta = longitudeDelta / cosLat
        return RouteBuilderCameraRegion(
            centerLat = centerLat,
            centerLng = centerLng,
            latitudeDelta = latitudeDelta.coerceAtLeast(0.000001),
            longitudeDelta = longitudeDelta.coerceAtLeast(0.000001),
        )
    }

    /** Mapbox screen projection — authoritative crosshair coordinate once the map is loaded. */
    fun resolveCrosshairCoordinate(
        mapboxMap: MapboxMap?,
        mapWidthPx: Float,
        mapHeightPx: Float,
        sheetVisibleHeightPx: Float,
        bottomSafeAreaPx: Float = 0f,
    ): RouteLatLng? {
        if (mapboxMap == null || mapWidthPx <= 0f || mapHeightPx <= 0f) return null
        val (crosshairX, crosshairY) =
            crosshairCenter(
                mapWidthPx = mapWidthPx,
                mapHeightPx = mapHeightPx,
                sheetVisibleHeightPx = sheetVisibleHeightPx,
                bottomSafeAreaPx = bottomSafeAreaPx,
            )
        val point =
            mapboxMap.coordinateForPixel(
                ScreenCoordinate(crosshairX.toDouble(), crosshairY.toDouble()),
            )
        return point.latitude() to point.longitude()
    }

    fun coordinateAtCrosshair(
        region: RouteBuilderCameraRegion,
        mapWidthPx: Float,
        mapHeightPx: Float,
        crosshairX: Float,
        crosshairY: Float,
    ): RouteLatLng {
        if (mapWidthPx <= 0f || mapHeightPx <= 0f) return region.centerLat to region.centerLng
        val mapCenterX = mapWidthPx / 2f
        val mapCenterY = mapHeightPx / 2f
        val offsetX = crosshairX - mapCenterX
        val offsetY = crosshairY - mapCenterY
        val latOffset = -offsetY * (region.latitudeDelta / mapHeightPx)
        val lngOffset = offsetX * (region.longitudeDelta / mapWidthPx)
        return (region.centerLat + latOffset) to (region.centerLng + lngOffset)
    }
}
