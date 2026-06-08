package to.ottomot.driftd

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min

/** Shared sizing for chat feed photos and videos: full bubble width, natural aspect height, cap then center-crop. */
object ChatFeedMediaDisplay {
    private const val legacyMaxDisplayHeight = 280f

    /** Tall enough for typical 3:4 phone portraits at bubble width; still caps very long panos. */
    fun maxHeightDp(screenHeightDp: Float): Dp {
        val capped = min(480f, max(360f, screenHeightDp * 0.52f))
        return capped.dp
    }

    /** Previous fixed thumbnail floor — used for landscape photos and unknown-dimension placeholders. */
    fun minDisplayHeightDp(containerWidthDp: Float): Dp {
        return min(containerWidthDp * 0.75f, legacyMaxDisplayHeight).dp
    }

    fun displayHeightDp(
        containerWidthDp: Float,
        sourceWidth: Int?,
        sourceHeight: Int?,
        screenHeightDp: Float,
    ): Dp {
        val width = sourceWidth?.takeIf { it > 0 }
        val height = sourceHeight?.takeIf { it > 0 }
        val maxHeight = maxHeightDp(screenHeightDp).value
        if (width == null || height == null) {
            return min(minDisplayHeightDp(containerWidthDp).value, maxHeight).dp
        }

        val naturalHeight = containerWidthDp * (height.toFloat() / width)
        if (height > width) {
            return min(naturalHeight, maxHeight).dp
        }
        val minHeight = minDisplayHeightDp(containerWidthDp).value
        return min(max(naturalHeight, minHeight), maxHeight).dp
    }

    fun cropsToMaxHeight(
        containerWidthDp: Float,
        sourceWidth: Int?,
        sourceHeight: Int?,
        screenHeightDp: Float,
    ): Boolean {
        val width = sourceWidth?.takeIf { it > 0 } ?: return false
        val height = sourceHeight?.takeIf { it > 0 } ?: return false
        val naturalHeight = containerWidthDp * (height.toFloat() / width)
        val displayHeight = displayHeightDp(containerWidthDp, width, height, screenHeightDp).value
        return kotlin.math.abs(displayHeight - naturalHeight) > 0.5f
    }
}

@Composable
fun rememberChatFeedMediaMaxHeightDp(): Dp {
    val screenHeightDp = LocalConfiguration.current.screenHeightDp.toFloat()
    return ChatFeedMediaDisplay.maxHeightDp(screenHeightDp)
}
