package to.ottomot.driftd

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import to.ottomot.driftd.ui.insets.OttoWindowInsets.ottoBottomSheetContent
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mapbox.geojson.Point
import com.mapbox.maps.Style
import com.mapbox.maps.ViewAnnotationAnchor
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.maps.extension.compose.annotation.ViewAnnotation
import com.mapbox.maps.extension.compose.style.MapStyle
import com.mapbox.maps.viewannotation.annotationAnchor
import com.mapbox.maps.viewannotation.geometry
import com.mapbox.maps.viewannotation.viewAnnotationOptions
import to.ottomot.driftd.core.event.eventVenueLatLng
import to.ottomot.driftd.map.OttoMapEventMarkerContent
import to.ottomot.driftd.core.event.openMeetLocationInMaps
import to.ottomot.driftd.core.network.dto.EventDto
import to.ottomot.driftd.BuildConfig

@Composable
internal fun EventVenueMapScreen(
    event: EventDto,
    onClose: () -> Unit,
) {
    val ctx = LocalContext.current
    val venue = remember(event.id) { eventVenueLatLng(event) }
    val address = remember(event.id) { shortAddress(event) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (venue != null && BuildConfig.MAPBOX_ACCESS_TOKEN.isNotBlank()) {
            val (lat, lng) = venue
            val point = Point.fromLngLat(lng, lat)
            val mapViewportState = rememberMapViewportState()
            RouteMapFitCameraEffect(
                mapViewportState = mapViewportState,
                lineCoordinates = emptyList(),
                mapPoints =
                    listOf(
                        RouteMapPoint(
                            id = "event-venue-${event.id}",
                            lat = lat,
                            lng = lng,
                            markerType = null,
                            index = 0,
                        ),
                    ),
                paddingDp = 72.0,
            )
            MapboxMap(
                modifier = Modifier.fillMaxSize(),
                mapViewportState = mapViewportState,
                scaleBar = {},
                compass = {},
                attribution = {},
                style = { MapStyle(style = Style.DARK) },
            ) {
                ViewAnnotation(
                    options =
                        viewAnnotationOptions {
                            geometry(point)
                            annotationAnchor { anchor(ViewAnnotationAnchor.BOTTOM) }
                            allowOverlap(true)
                        },
                ) {
                    OttoMapEventMarkerContent(
                        modifier = Modifier,
                    )
                }
            }
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Outlined.Map,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.35f),
                    modifier = Modifier.size(48.dp),
                )
            }
        }

        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .background(Color.Black.copy(alpha = 0.78f))
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onClose,
                    modifier =
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.12f)),
                ) {
                    Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.settings_cancel), tint = Color.White)
                }
                Column(
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        event.name,
                        style = androidx.compose.material3.MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White,
                        maxLines = 2,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        address,
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.58f),
                        maxLines = 2,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.size(36.dp))
            }

            Spacer(Modifier.weight(1f))

            Column(
                Modifier
                    .fillMaxWidth()
                    .ottoBottomSheetContent()
                    .padding(horizontal = 12.dp, vertical = 12.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.78f))
                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                    .padding(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.LocationOn, contentDescription = null, tint = Color(0xFF7B3DFF), modifier = Modifier.size(20.dp))
                    Text(
                        stringResource(R.string.event_card_meet_label),
                        style = androidx.compose.material3.MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    address,
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.72f),
                )
                if (venue != null) {
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { openMeetLocationInMaps(ctx, address, venue.first, venue.second) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.event_venue_map_directions))
                    }
                }
            }
        }
    }
}
