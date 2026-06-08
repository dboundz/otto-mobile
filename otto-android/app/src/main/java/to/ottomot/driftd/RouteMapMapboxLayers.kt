package to.ottomot.driftd

import androidx.compose.runtime.Composable
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.maps.MapboxMap
import com.mapbox.maps.Style
import com.mapbox.maps.extension.compose.DisposableMapEffect
import com.mapbox.maps.extension.style.StyleContract
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.addLayerBelow
import com.mapbox.maps.extension.style.layers.generated.lineLayer
import com.mapbox.maps.extension.style.layers.properties.generated.LineCap
import com.mapbox.maps.extension.style.layers.properties.generated.LineJoin
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.geoJsonSource

internal enum class RouteMapLinePalette {
    LivePurple,
    BuilderCyan,
}

@Composable
internal fun RouteMapLineMapEffect(
    sourceId: String,
    lineCoordinates: List<Point>,
    palette: RouteMapLinePalette = RouteMapLinePalette.LivePurple,
    lineFingerprint: String? = null,
    belowLayerId: String? = null,
    onLineLayersInstalled: ((Style) -> Unit)? = null,
) {
    val coordinatesKey = lineFingerprint ?: lineCoordinates.joinToString { "${it.latitude()},${it.longitude()}" }
    DisposableMapEffect(sourceId, coordinatesKey, palette, belowLayerId, onLineLayersInstalled) { mapView ->
        val mapboxMap = mapView.mapboxMap
        val install: (Style) -> Unit = { style ->
            installPurpleRouteLine(style, sourceId, lineCoordinates, palette, belowLayerId)
            onLineLayersInstalled?.invoke(style)
        }
        mapboxMap.whenStyleReady(install)
        onDispose {
            mapboxMap.whenStyleReady { style ->
                removeRouteLineLayers(style, sourceId)
            }
        }
    }
}

@Composable
internal fun RouteSpeedGradientMapEffect(
    sourceId: String,
    pathSamples: List<DrivePathSample>,
) {
    val samplesKey =
        pathSamples.joinToString(limit = 8) { "${it.lat},${it.lng},${it.speedMph}" }
    DisposableMapEffect(sourceId, samplesKey) { mapView ->
        val mapboxMap = mapView.mapboxMap
        val install: (Style) -> Unit = { style ->
            installSpeedGradientLine(style, sourceId, pathSamples)
        }
        mapboxMap.whenStyleReady(install)
        onDispose {
            mapboxMap.whenStyleReady { style ->
                removeSpeedGradientLayers(style, sourceId)
            }
        }
    }
}

private fun MapboxMap.whenStyleReady(block: (Style) -> Unit) {
    getStyle(block)
}

private fun installPurpleRouteLine(
    style: Style,
    sourceId: String,
    coordinates: List<Point>,
    palette: RouteMapLinePalette,
    belowLayerId: String? = null,
) {
    if (coordinates.size < 2) return
    removeRouteLineLayers(style, sourceId)
    val line = LineString.fromLngLats(coordinates)
    style.addSource(geoJsonSource(sourceId) { geometry(line) })
    val glowColor = if (palette == RouteMapLinePalette.BuilderCyan) "#00EAFF" else "#7B3DFF"
    val mainColor = if (palette == RouteMapLinePalette.BuilderCyan) "#00B8FF" else "#7B3DFF"
    val coreColor = if (palette == RouteMapLinePalette.BuilderCyan) "#E8FFFF" else "#E8D9FF"
    val glowWidth = if (palette == RouteMapLinePalette.BuilderCyan) 17.0 else 15.0
    val mainWidth = if (palette == RouteMapLinePalette.BuilderCyan) 11.0 else 9.0
    val coreWidth = if (palette == RouteMapLinePalette.BuilderCyan) 4.0 else 3.0
    val glowOpacity = if (palette == RouteMapLinePalette.BuilderCyan) 0.62 else 0.42

    style.addRouteLineLayer(
        lineLayer("${sourceId}-glow", sourceId) {
            lineCap(LineCap.ROUND)
            lineJoin(LineJoin.ROUND)
            lineColor(glowColor)
            lineOpacity(glowOpacity)
            lineBlur(if (palette == RouteMapLinePalette.BuilderCyan) 3.0 else 4.0)
            lineWidth(glowWidth)
            lineEmissiveStrength(if (palette == RouteMapLinePalette.BuilderCyan) 1.8 else 1.5)
        },
        belowLayerId,
    )
    style.addRouteLineLayer(
        lineLayer("${sourceId}-main", sourceId) {
            lineCap(LineCap.ROUND)
            lineJoin(LineJoin.ROUND)
            lineColor(mainColor)
            lineOpacity(1.0)
            lineWidth(mainWidth)
            lineEmissiveStrength(if (palette == RouteMapLinePalette.BuilderCyan) 2.4 else 2.0)
        },
        belowLayerId,
    )
    style.addRouteLineLayer(
        lineLayer("${sourceId}-core", sourceId) {
            lineCap(LineCap.ROUND)
            lineJoin(LineJoin.ROUND)
            lineColor(coreColor)
            lineOpacity(1.0)
            lineWidth(coreWidth)
            lineEmissiveStrength(if (palette == RouteMapLinePalette.BuilderCyan) 2.4 else 2.0)
        },
        belowLayerId,
    )
}

private fun installSpeedGradientLine(
    style: Style,
    sourceId: String,
    pathSamples: List<DrivePathSample>,
) {
    if (!DriveSpeedGradient.hasUsableSpeedPathData(pathSamples)) return
    val vertices = DriveSpeedGradient.buildRenderVertices(pathSamples)
    if (vertices.size < 2) return
    val gradient = DriveSpeedGradient.trailGradientExpression(vertices) ?: return
    removeSpeedGradientLayers(style, sourceId)

    val coordinates = vertices.map { Point.fromLngLat(it.lng, it.lat) }
    val line = LineString.fromLngLats(coordinates)
    style.addSource(
        geoJsonSource(sourceId) {
            geometry(line)
            lineMetrics(true)
        },
    )

    style.addLayer(
        lineLayer("${sourceId}-speed-under", sourceId) {
            lineCap(LineCap.ROUND)
            lineJoin(LineJoin.ROUND)
            lineColor("#060A12")
            lineOpacity(0.9)
            lineBlur(1.0)
            lineWidth(7.0)
        },
    )
    style.addLayer(
        lineLayer("${sourceId}-speed-line", sourceId) {
            lineCap(LineCap.ROUND)
            lineJoin(LineJoin.ROUND)
            lineGradient(gradient)
            lineOpacity(1.0)
            lineBlur(0.0)
            lineWidth(4.5)
            lineEmissiveStrength(1.0)
        },
    )
}

private fun Style.addRouteLineLayer(
    layer: StyleContract.StyleLayerExtension,
    belowLayerId: String?,
) {
    if (belowLayerId != null && styleLayerExists(belowLayerId)) {
        addLayerBelow(layer, belowLayerId)
    } else {
        addLayer(layer)
    }
}

internal const val ROUTE_BUILDER_MAP_LINE_SOURCE = "route-builder-line"

internal fun routeBuilderLineCoreLayerId(): String = "$ROUTE_BUILDER_MAP_LINE_SOURCE-core"

internal fun Style.installRouteBuilderMapLine(lineCoordinates: List<Point>) {
    if (lineCoordinates.size < 2) {
        removeRouteBuilderMapLine()
        return
    }
    installPurpleRouteLine(this, ROUTE_BUILDER_MAP_LINE_SOURCE, lineCoordinates, RouteMapLinePalette.LivePurple)
}

internal fun Style.removeRouteBuilderMapLine() {
    removeRouteLineLayers(this, ROUTE_BUILDER_MAP_LINE_SOURCE)
}

private fun removeRouteLineLayers(style: Style, sourceId: String) {
    listOf("${sourceId}-glow", "${sourceId}-main", "${sourceId}-core").forEach { layerId ->
        runCatching { style.removeStyleLayer(layerId) }
    }
    runCatching { style.removeStyleSource(sourceId) }
}

private fun removeSpeedGradientLayers(style: Style, sourceId: String) {
    listOf(
        "${sourceId}-speed-under",
        "${sourceId}-speed-line",
        // Legacy glow stack (removed in legend-matched trail refresh)
        "${sourceId}-speed-outer-glow",
        "${sourceId}-speed-glow",
        "${sourceId}-speed-main",
        "${sourceId}-speed-core",
    ).forEach { layerId ->
        runCatching { style.removeStyleLayer(layerId) }
    }
    runCatching { style.removeStyleSource(sourceId) }
}
