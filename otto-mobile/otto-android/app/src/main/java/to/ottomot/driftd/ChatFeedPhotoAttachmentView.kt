package to.ottomot.driftd

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ChatFeedPhotoAttachmentView(
    url: String,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    onLongPress: (() -> Unit)? = null,
) {
    val ctx = LocalContext.current
    val screenHeightDp = LocalConfiguration.current.screenHeightDp.toFloat()
    val isAnimated = ChatImageUrlDisplay.isAnimatedImageUrl(url)
    val contentDescription =
        stringResource(
            if (isAnimated) {
                R.string.chat_attachment_accessibility_gif
            } else {
                R.string.chat_attachment_accessibility_photo
            },
        )
    var sourceSize by remember(url) { mutableStateOf<IntSize?>(null) }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val containerWidth = maxWidth.value
        val displayHeight =
            ChatFeedMediaDisplay.displayHeightDp(
                containerWidthDp = containerWidth,
                sourceWidth = sourceSize?.width,
                sourceHeight = sourceSize?.height,
                screenHeightDp = screenHeightDp,
            )

        AsyncImage(
            model = ottoImageRequest(ctx, url),
            contentDescription = contentDescription,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(displayHeight)
                    .clip(RoundedCornerShape(10.dp))
                    .combinedClickable(
                        onClick = onTap,
                        onLongClick = onLongPress,
                    ),
            contentScale = ContentScale.Crop,
            onSuccess = { state ->
                val drawable = state.result.drawable
                val width = drawable.intrinsicWidth
                val height = drawable.intrinsicHeight
                if (width > 0 && height > 0) {
                    sourceSize = IntSize(width, height)
                }
            },
        )
    }
}
