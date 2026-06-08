package to.ottomot.driftd

import androidx.compose.runtime.Composable
import com.mapbox.maps.MapboxMap
import com.mapbox.maps.Style
import com.mapbox.maps.extension.compose.DisposableMapEffect
import com.mapbox.maps.extension.style.expressions.dsl.generated.eq
import com.mapbox.maps.extension.style.expressions.dsl.generated.get
import com.mapbox.maps.extension.style.expressions.dsl.generated.literal
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.generated.lineLayer
import com.mapbox.maps.extension.style.layers.properties.generated.LineCap
import com.mapbox.maps.extension.style.layers.properties.generated.LineJoin
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.vectorSource

private const val TRAFFIC_SOURCE_ID = "otto-map-traffic"
private const val TRAFFIC_SOURCE_URL = "mapbox://mapbox.mapbox-traffic-v1"
private const val TRAFFIC_SOURCE_LAYER = "traffic"

private data class TrafficCongestionStyle(
    val suffix: String,
    val value: String,
    val colorHex: String,
)

/** Omit `low` (green free-flow) — only show delays and worse (iOS parity). */
private val trafficCongestionStyles =
    listOf(
        TrafficCongestionStyle("moderate", "moderate", "#FFC800"),
        TrafficCongestionStyle("heavy", "heavy", "#FF6400"),
        TrafficCongestionStyle("severe", "severe", "#E63737"),
    )

@Composable
internal fun MapboxTrafficMapEffect(showTraffic: Boolean) {
    DisposableMapEffect(showTraffic) { mapView ->
        val mapboxMap = mapView.mapboxMap
        val install: (Style) -> Unit = { style ->
            if (showTraffic) {
                installTrafficLayers(style)
            } else {
                removeTrafficLayers(style)
            }
        }
        mapboxMap.whenStyleReady(install)
        onDispose {
            mapboxMap.whenStyleReady { style ->
                removeTrafficLayers(style)
            }
        }
    }
}

private fun MapboxMap.whenStyleReady(block: (Style) -> Unit) {
    getStyle(block)
}

private fun installTrafficLayers(style: Style) {
    removeTrafficLayers(style)
    style.addSource(
        vectorSource(TRAFFIC_SOURCE_ID) {
            url(TRAFFIC_SOURCE_URL)
        },
    )
    trafficCongestionStyles.forEach { congestionStyle ->
        val layerId = "$TRAFFIC_SOURCE_ID-${congestionStyle.suffix}"
        style.addLayer(
            lineLayer(layerId, TRAFFIC_SOURCE_ID) {
                sourceLayer(TRAFFIC_SOURCE_LAYER)
                filter(
                    eq {
                        get("congestion")
                        literal(congestionStyle.value)
                    },
                )
                lineCap(LineCap.ROUND)
                lineJoin(LineJoin.ROUND)
                lineColor(congestionStyle.colorHex)
                lineOpacity(0.92)
                lineWidth(3.0)
                lineOffset(2.0)
            },
        )
    }
}

private fun removeTrafficLayers(style: Style) {
    trafficCongestionStyles.forEach { congestionStyle ->
        runCatching { style.removeStyleLayer("$TRAFFIC_SOURCE_ID-${congestionStyle.suffix}") }
    }
    runCatching { style.removeStyleSource(TRAFFIC_SOURCE_ID) }
}
