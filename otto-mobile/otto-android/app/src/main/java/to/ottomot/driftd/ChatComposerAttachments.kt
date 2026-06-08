package to.ottomot.driftd

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Gif
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import to.ottomot.driftd.core.location.ApproximateLocationReader
import to.ottomot.driftd.core.media.ChatPreparedVideoUpload
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import to.ottomot.driftd.BuildConfig
import to.ottomot.driftd.core.network.dto.EventDto
import to.ottomot.driftd.core.network.MediaUrlResolver
import to.ottomot.driftd.ui.dialog.OttoEducationDialog
import to.ottomot.driftd.ui.dialog.OttoEducationLocationHero

enum class ChatComposerAttachmentAction {
    Photo,
    Video,
    Gif,
    Location,
    CreateEvent,
    ;

    companion object {
        val directChatActions: Set<ChatComposerAttachmentAction> =
            setOf(Photo, Gif, Video, Location)

        val squadChatActions: Set<ChatComposerAttachmentAction> =
            setOf(Photo, Gif, Video, Location, CreateEvent)
    }
}

enum class ChatPendingComposerAttachmentKind {
    Photo,
    KlipyGif,
    Video,
    Place,
    Event,
}

data class ChatPendingComposerAttachment(
    val id: String = UUID.randomUUID().toString(),
    val kind: ChatPendingComposerAttachmentKind,
    val photo: ChatSendPhotoAttachment? = null,
    val klipyGif: KlipyGifSelection? = null,
    val klipySearchQuery: String? = null,
    val video: ChatPreparedVideoUpload? = null,
    val placePayload: MapMarkerSharePayload? = null,
    val mapPreviewBytes: ByteArray? = null,
    val event: EventDto? = null,
    val eventPreviewUrl: String? = null,
) {
    val isPhoto: Boolean get() = kind == ChatPendingComposerAttachmentKind.Photo
    val isKlipyGif: Boolean get() = kind == ChatPendingComposerAttachmentKind.KlipyGif
    val isVideo: Boolean get() = kind == ChatPendingComposerAttachmentKind.Video
    val isPlace: Boolean get() = kind == ChatPendingComposerAttachmentKind.Place
    val isEvent: Boolean get() = kind == ChatPendingComposerAttachmentKind.Event
}

object ChatComposerLocationAttachmentLoader {
    sealed class Error {
        data object PermissionDenied : Error()
        data object LocationUnavailable : Error()
    }

    private class LocationAttachmentFailure(val error: Error) : Exception()

    internal fun failureKind(throwable: Throwable): Error? = (throwable as? LocationAttachmentFailure)?.error

    suspend fun buildPendingAttachment(
        context: Context,
        locationReader: ApproximateLocationReader,
    ): Result<ChatPendingComposerAttachment> =
        withContext(Dispatchers.IO) {
            val fineGranted =
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED
            val coarseGranted =
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED
            if (!fineGranted && !coarseGranted) {
                return@withContext Result.failure(LocationAttachmentFailure(Error.PermissionDenied))
            }

            val fix = locationReader.currentFixHighAccuracyOrNull()
            val latLng =
                if (fix != null) {
                    fix.latitude to fix.longitude
                } else {
                    locationReader.currentLatLngOrNull()
                }
            val lat = latLng?.first
            val lng = latLng?.second
            if (lat == null || lng == null || !lat.isFinite() || !lng.isFinite()) {
                return@withContext Result.failure(LocationAttachmentFailure(Error.LocationUnavailable))
            }

            val label = MapPlaceLabelResolver.resolve(context, lat, lng)
            val payload =
                mapMarkerSharePayloadForAdhocPlace(
                    name = label.name,
                    addressSummary = label.addressSummary,
                    latitude = lat,
                    longitude = lng,
                )
            val mapPreviewBytes =
                PlaceMapSnapshotGenerator.jpegBytes(
                    latitude = lat,
                    longitude = lng,
                    accessToken = BuildConfig.MAPBOX_ACCESS_TOKEN,
                    resources = context.resources,
                )

            Result.success(
                ChatPendingComposerAttachment(
                    kind = ChatPendingComposerAttachmentKind.Place,
                    placePayload = payload,
                    mapPreviewBytes = mapPreviewBytes,
                ),
            )
        }
}

@Composable
fun ChatComposerAttachmentTrayBar(
    actions: List<ChatComposerAttachmentAction>,
    isLoadingLocation: Boolean,
    onAction: (ChatComposerAttachmentAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(Color.White.copy(alpha = 0.04f))
                .padding(horizontal = 12.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Top,
    ) {
        actions.forEach { action ->
            ChatComposerAttachmentTrayCell(
                action = action,
                isLoading = action == ChatComposerAttachmentAction.Location && isLoadingLocation,
                onClick = { onAction(action) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ChatComposerAttachmentTrayCell(
    action: ChatComposerAttachmentAction,
    isLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label =
        when (action) {
            ChatComposerAttachmentAction.Photo -> stringResource(R.string.chat_composer_attach_photo)
            ChatComposerAttachmentAction.Gif -> stringResource(R.string.chat_composer_attach_gif)
            ChatComposerAttachmentAction.Video -> stringResource(R.string.chat_composer_attach_video)
            ChatComposerAttachmentAction.Location -> stringResource(R.string.chat_composer_attach_location)
            ChatComposerAttachmentAction.CreateEvent -> stringResource(R.string.chat_composer_attach_create_event)
        }
    val icon: ImageVector =
        when (action) {
            ChatComposerAttachmentAction.Photo -> Icons.Outlined.AddAPhoto
            ChatComposerAttachmentAction.Gif -> Icons.Outlined.Gif
            ChatComposerAttachmentAction.Video -> Icons.Outlined.Videocam
            ChatComposerAttachmentAction.Location -> Icons.Outlined.LocationOn
            ChatComposerAttachmentAction.CreateEvent -> Icons.Outlined.CalendarMonth
        }
    val tint: Color =
        when (action) {
            ChatComposerAttachmentAction.Photo -> Color(0xFF599EFF)
            ChatComposerAttachmentAction.Gif -> Color(0xFFB178FF)
            ChatComposerAttachmentAction.Video -> Color(0xFFE073F2)
            ChatComposerAttachmentAction.Location -> Color(0xFF59D18C)
            ChatComposerAttachmentAction.CreateEvent -> Color(0xFFFF944F)
        }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconButton(
            onClick = onClick,
            enabled = !isLoading,
            modifier = Modifier.size(58.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(58.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center,
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(24.dp))
                }
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.82f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun ChatComposerAttachmentToggleButton(
    trayVisible: Boolean,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = if (trayVisible) Icons.Outlined.Close else Icons.Outlined.Add,
            contentDescription =
                stringResource(
                    if (trayVisible) {
                        R.string.chat_composer_close_attachment_menu
                    } else {
                        R.string.chat_composer_open_attachment_menu
                    },
                ),
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
fun ChatComposerPendingAttachmentChip(
    attachment: ChatPendingComposerAttachment,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (attachment.kind) {
            ChatPendingComposerAttachmentKind.Photo -> {
                Icon(Icons.Outlined.AddAPhoto, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.chat_photo_attached), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            }
            ChatPendingComposerAttachmentKind.KlipyGif -> {
                val gif = attachment.klipyGif
                if (gif?.previewUrl?.isNotBlank() == true) {
                    AsyncImage(
                        model = gif.previewUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)),
                    )
                } else {
                    Icon(Icons.Outlined.Gif, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    gif?.title?.trim()?.takeIf { it.isNotEmpty() } ?: stringResource(R.string.chat_gif_attached),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            ChatPendingComposerAttachmentKind.Video -> {
                Icon(Icons.Outlined.Videocam, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.chat_video_attached), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            }
            ChatPendingComposerAttachmentKind.Place -> {
                val previewBytes = attachment.mapPreviewBytes
                if (previewBytes != null) {
                    val bmp = remember(attachment.id, previewBytes.size) { BitmapFactory.decodeByteArray(previewBytes, 0, previewBytes.size) }
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)),
                        )
                    } else {
                        Icon(Icons.Outlined.Place, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                    }
                } else {
                    Icon(Icons.Outlined.Place, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    attachment.placePayload?.title ?: stringResource(R.string.chat_composer_attachment_location),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            ChatPendingComposerAttachmentKind.Event -> {
                val previewUrl = attachment.eventPreviewUrl
                if (!previewUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = previewUrl,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)),
                    )
                } else {
                    Icon(Icons.Outlined.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    attachment.event?.name?.trim()?.takeIf { it.isNotEmpty() }
                        ?: stringResource(R.string.squad_chat_event_untitled),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        TextButton(onClick = onRemove) {
            Text(stringResource(R.string.chat_attachment_remove))
        }
    }
}

@Composable
fun ChatComposerLocationPermissionHost(
    showLocationPrimer: Boolean,
    showLocationDeniedModal: Boolean,
    onDismissLocationPrimer: () -> Unit,
    onDismissLocationDenied: () -> Unit,
    onLocationPermissionResult: (Boolean) -> Unit,
) {
    val ctx = LocalContext.current

    val locationPermLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            onLocationPermissionResult(granted)
        }

    if (showLocationPrimer) {
        OttoEducationDialog(
            visible = true,
            busy = false,
            onDismissRequest = {},
            onCloseClick = {},
            hero = { OttoEducationLocationHero() },
            title = stringResource(R.string.chat_composer_location_primer_title),
            body = stringResource(R.string.chat_composer_location_primer_body),
            bulletSectionTitle = null,
            bullets = emptyList(),
            footer = stringResource(R.string.chat_composer_location_primer_footer),
            primaryLabel = stringResource(R.string.chat_composer_location_primer_continue),
            onPrimaryClick = {
                onDismissLocationPrimer()
                locationPermLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            },
            allowsUnconfirmedDismiss = false,
        )
    }

    if (showLocationDeniedModal) {
        OttoEducationDialog(
            visible = true,
            busy = false,
            onDismissRequest = onDismissLocationDenied,
            onCloseClick = onDismissLocationDenied,
            hero = { OttoEducationLocationHero() },
            title = stringResource(R.string.location_permission_map_modal_title),
            body = stringResource(R.string.location_permission_map_modal_body),
            bulletSectionTitle = null,
            bullets = emptyList(),
            footer = null,
            primaryLabel = stringResource(R.string.location_permission_enable),
            onPrimaryClick = {
                runCatching {
                    val intent =
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", ctx.packageName, null)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    ctx.startActivity(intent)
                }
                onDismissLocationDenied()
            },
            allowsUnconfirmedDismiss = true,
        )
    }
}

fun chatComposerEventPreviewUrl(event: EventDto): String? =
    event.bannerImage?.url?.let { MediaUrlResolver.resolve(it)?.toString() }

fun beginChatComposerLocationAttachment(
    scope: kotlinx.coroutines.CoroutineScope,
    context: Context,
    locationReader: ApproximateLocationReader,
    onLoadingChanged: (Boolean) -> Unit,
    onSuccess: (ChatPendingComposerAttachment) -> Unit,
    onError: (String) -> Unit,
) {
    scope.launch {
        onLoadingChanged(true)
        val result = ChatComposerLocationAttachmentLoader.buildPendingAttachment(context, locationReader)
        onLoadingChanged(false)
        result.fold(
            onSuccess = onSuccess,
            onFailure = { err ->
                val kind = ChatComposerLocationAttachmentLoader.failureKind(err)
                val message =
                    when (kind) {
                        ChatComposerLocationAttachmentLoader.Error.PermissionDenied ->
                            context.getString(R.string.chat_composer_location_permission_denied)
                        ChatComposerLocationAttachmentLoader.Error.LocationUnavailable ->
                            context.getString(R.string.chat_composer_location_unavailable)
                        else -> err.message ?: context.getString(R.string.chat_composer_location_unavailable)
                    }
                onError(message)
            },
        )
    }
}
