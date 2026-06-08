package to.ottomot.driftd

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import coil.compose.AsyncImage
import to.ottomot.driftd.core.network.MediaUrlResolver

@Composable
internal fun UserProfileAvatar(
    displayName: String?,
    userId: String,
    avatarUrl: String?,
    mapAccentKey: String?,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
    textColor: Color = Color.White,
    contentDescription: String? = null,
) {
    val resolvedAvatarUrl = avatarUrl?.let { MediaUrlResolver.resolve(it)?.toString() }?.takeIf { it.isNotBlank() }
    val accent = mapAccentComposeColor(mapAccentKey)
    val ctx = LocalContext.current

    Box(
        modifier = modifier.background(accent),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            userProfileAvatarLetter(displayName, userId),
            style = textStyle,
            color = textColor,
            maxLines = 1,
        )
        if (resolvedAvatarUrl != null) {
            AsyncImage(
                model = ottoImageRequest(ctx, resolvedAvatarUrl),
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}
