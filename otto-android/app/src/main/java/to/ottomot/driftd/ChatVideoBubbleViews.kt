package to.ottomot.driftd

import android.graphics.Bitmap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import to.ottomot.driftd.core.network.MediaUrlResolver
import to.ottomot.driftd.core.network.dto.ChatVideoAttachmentDto
import kotlin.math.max

internal fun formatChatVideoDuration(seconds: Double): String {
    val total = max(0, seconds.toInt())
    val mins = total / 60
    val secs = total % 60
    return if (mins >= 60) {
        val hours = mins / 60
        val remMins = mins % 60
        String.format("%d:%02d:%02d", hours, remMins, secs)
    } else {
        String.format("%d:%02d", mins, secs)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ChatFeedVideoAttachmentView(
    attachment: ChatVideoAttachmentDto,
    messageId: String,
    localThumbnail: Bitmap? = null,
    uploadProgress: Float? = null,
    isUploadPending: Boolean = false,
    onCancelUpload: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    var showFullscreen by remember(messageId) { mutableStateOf(false) }
    val screenHeightDp = LocalConfiguration.current.screenHeightDp.toFloat()
    val bubbleWidth = 292.dp
    val aspectHeight =
        remember(attachment.width, attachment.height, screenHeightDp) {
            ChatFeedMediaDisplay.displayHeightDp(
                containerWidthDp = bubbleWidth.value,
                sourceWidth = attachment.width,
                sourceHeight = attachment.height,
                screenHeightDp = screenHeightDp,
            )
        }
    val thumbUrl =
        attachment.thumbnailUrl
            .trim()
            .takeIf { it.isNotEmpty() }
            ?.let { MediaUrlResolver.resolve(it)?.toString() }
    val videoUrl =
        attachment.videoUrl
            .trim()
            .takeIf { it.isNotEmpty() }
            ?.let { MediaUrlResolver.resolve(it)?.toString() }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(aspectHeight)
                .clip(RoundedCornerShape(10.dp)),
    ) {
        when {
            localThumbnail != null ->
                Image(
                    bitmap = localThumbnail.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )

            !thumbUrl.isNullOrBlank() ->
                AsyncImage(
                    model = ottoImageRequest(ctx, thumbUrl),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )

            else ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.White.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Video", color = Color.White.copy(alpha = 0.35f))
                }
        }

        if (isUploadPending && uploadProgress != null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center,
            ) {
                ChatVideoUploadProgressRing(
                    progress = uploadProgress,
                    onCancel = onCancelUpload,
                )
            }
        } else if (!videoUrl.isNullOrBlank()) {
            Icon(
                Icons.Filled.PlayCircle,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.92f),
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .size(44.dp),
            )
        }

        Text(
            formatChatVideoDuration(attachment.durationSeconds),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                    .padding(horizontal = 6.dp, vertical = 3.dp),
        )

        if (!isUploadPending && !videoUrl.isNullOrBlank()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .combinedClickable(
                        onClick = {
                            showFullscreen = true
                        },
                        onLongClick = onLongPress,
                    ),
            )
        } else if (onLongPress != null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .combinedClickable(onClick = {}, onLongClick = onLongPress),
            )
        }
    }

    if (showFullscreen && !videoUrl.isNullOrBlank()) {
        ChatFullscreenVideoDialog(
            videoUrl = videoUrl,
            onDismiss = { showFullscreen = false },
        )
    }
}

@Composable
private fun ChatVideoUploadProgressRing(
    progress: Float,
    onCancel: (() -> Unit)?,
) {
    Box(contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier.size(56.dp),
            color = Color.White,
            trackColor = Color.White.copy(alpha = 0.25f),
            strokeWidth = 3.dp,
        )
        if (onCancel != null) {
            IconButton(onClick = onCancel, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Cancel upload",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
