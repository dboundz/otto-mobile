package to.ottomot.driftd

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Matches iOS `ChatUIKitScrollPinning` default threshold (120pt). */
const val CHAT_PIN_TO_LATEST_THRESHOLD_DP = 120f

fun chatPinToLatestThresholdPx(density: Float): Float = CHAT_PIN_TO_LATEST_THRESHOLD_DP * density

/**
 * Matches iOS `ChatUIKitScrollPinning`: distance from visible bottom to end of content within ~120dp ⇒ "at latest".
 */
fun LazyListState.isPinnedToLatestChat(thresholdPx: Float): Boolean {
    val info = layoutInfo
    val n = info.totalItemsCount
    if (n == 0) return true
    val lastIndex = n - 1
    val lastVisible = info.visibleItemsInfo.lastOrNull() ?: return false
    if (lastVisible.index < lastIndex) return false
    val itemBottom = lastVisible.offset + lastVisible.size
    val gap = info.viewportEndOffset - itemBottom
    return gap <= thresholdPx
}

/** Bottom-align [index] in the viewport (last bubble flush above composer padding). */
fun LazyListState.chatLatestBottomScrollOffset(index: Int): Int {
    val info = layoutInfo
    if (index < 0 || index >= info.totalItemsCount) return 0
    val viewportHeight = info.viewportEndOffset - info.viewportStartOffset
    if (viewportHeight <= 0) return 0
    val itemSize = info.visibleItemsInfo.find { it.index == index }?.size ?: return 0
    return itemSize - viewportHeight
}

/**
 * Scroll after the lazy list has laid out new items. Bottom-aligns the target row.
 * Unconditional [LazyListState.scrollToItem] on every realtime message can throw while composing.
 */
suspend fun LazyListState.scrollToChatLatestBottom(
    index: Int,
    animate: Boolean,
) {
    if (index < 0) return
    val delaysMs = longArrayOf(48L, 32L, 32L, 32L, 32L)
    for (delayMs in delaysMs) {
        val count = layoutInfo.totalItemsCount
        if (count > 0 && index < count) {
            val scrollBlock: suspend (Int) -> Boolean = { offset ->
                runCatching {
                    if (animate) {
                        animateScrollToItem(index, scrollOffset = offset)
                    } else {
                        scrollToItem(index, scrollOffset = offset)
                    }
                }.isSuccess
            }
            if (!scrollBlock(0)) {
                delay(delayMs)
                continue
            }
            val bottomOffset = chatLatestBottomScrollOffset(index)
            if (bottomOffset != 0) {
                scrollBlock(bottomOffset)
            }
            return
        }
        delay(delayMs)
    }
}

/** @see scrollToChatLatestBottom */
suspend fun LazyListState.scrollToChatIndexSafely(
    index: Int,
    animate: Boolean,
) {
    scrollToChatLatestBottom(index, animate)
}

private val ChatJumpToLatestCorner = RoundedCornerShape(14.dp)

/**
 * Jump control styled like squad **member list** rows (`surfaceContainerHigh` + subtle hairline; icon like row chevron).
 * Sits above the composer ([bottomAboveComposer]) and lifts with IME.
 */
@Composable
fun ChatJumpToLatestFloatingButton(
    visible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    bottomAboveComposer: Dp = 96.dp,
    end: Dp = 18.dp,
    applyNavigationBarsPadding: Boolean = true,
) {
    val cd = stringResource(R.string.chat_jump_to_latest)
    val rowBorder = Color.White.copy(alpha = 0.12f)
    AnimatedVisibility(
        visible = visible,
        modifier =
            modifier
                .then(if (applyNavigationBarsPadding) Modifier.navigationBarsPadding() else Modifier)
                .imePadding()
                .padding(end = end, bottom = bottomAboveComposer),
        enter =
            fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                scaleIn(
                    initialScale = 0.92f,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                ),
        exit =
            fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                scaleOut(
                    targetScale = 0.92f,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                ),
    ) {
        Surface(
            modifier =
                Modifier
                    .size(46.dp)
                    .clip(ChatJumpToLatestCorner)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick,
                    ).semantics {
                        contentDescription = cd
                        role = Role.Button
                    },
            shape = ChatJumpToLatestCorner,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = BorderStroke(1.dp, rowBorder),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
