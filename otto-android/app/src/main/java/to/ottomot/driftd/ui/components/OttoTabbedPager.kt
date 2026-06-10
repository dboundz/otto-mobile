package to.ottomot.driftd.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Swipeable tab content synced with an external tab bar.
 *
 * @param retainOffscreenPages When true, keeps all pages composed (squad detail chat scroll).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OttoTabbedPager(
    pageCount: Int,
    selectedIdx: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    retainOffscreenPages: Boolean = false,
    pageContent: @Composable (page: Int) -> Unit,
) {
    if (pageCount <= 0) return
    val pagerState =
        rememberPagerState(
            initialPage = selectedIdx.coerceIn(0, pageCount - 1),
            pageCount = { pageCount },
        )

    LaunchedEffect(selectedIdx, pageCount) {
        val target = selectedIdx.coerceIn(0, maxOf(0, pageCount - 1))
        if (pagerState.currentPage != target) {
            pagerState.animateScrollToPage(target)
        }
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collectLatest { page ->
                if (page != selectedIdx) {
                    onSelect(page)
                }
            }
    }

    HorizontalPager(
        state = pagerState,
        modifier = modifier,
        beyondViewportPageCount = if (retainOffscreenPages) maxOf(0, pageCount - 1) else 0,
    ) { page ->
        pageContent(page)
    }
}

/**
 * Tab bar + swipeable pages. Pass [tabBar] for custom chrome (e.g. [to.ottomot.driftd.OttoIosUnderlineTabBar]).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OttoTabbedPagerWithBar(
    pageCount: Int,
    selectedIdx: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    retainOffscreenPages: Boolean = false,
    tabBar: @Composable () -> Unit,
    pageContent: @Composable (page: Int) -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        tabBar()
        OttoTabbedPager(
            pageCount = pageCount,
            selectedIdx = selectedIdx,
            onSelect = onSelect,
            modifier = Modifier.fillMaxSize(),
            retainOffscreenPages = retainOffscreenPages,
            pageContent = pageContent,
        )
    }
}
