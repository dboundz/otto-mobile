package to.ottomot.driftd.ui.components

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import to.ottomot.driftd.ui.insets.OttoWindowInsets

private fun ensureFullscreenDialogWindow(view: android.view.View) {
    val window = (view.parent as? DialogWindowProvider)?.window ?: return
    window.setLayout(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT,
    )
    WindowCompat.setDecorFitsSystemWindows(window, false)
}

@Composable
private fun fullscreenBottomClearance(contentPadding: PaddingValues): Dp {
    val scaffoldBottom = contentPadding.calculateBottomPadding()
    val insetBottom =
        WindowInsets.safeDrawing
            .union(WindowInsets.navigationBars)
            .asPaddingValues()
            .calculateBottomPadding()
    // Dialog windows often report 0 insets; keep a floor so gesture nav never covers content.
    return maxOf(scaffoldBottom, insetBottom, 48.dp)
}

@Composable
private fun fullscreenScrollBottomPadding(extraBottom: Dp): Dp = extraBottom

/** Opaque black top chrome for fullscreen surfaces over photos, gradients, or maps. */
@Composable
fun OttoFullscreenOpaqueTopBar(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(Color.Black)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OttoFullscreenDarkTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        modifier = modifier.background(Color.Black),
        title = title,
        navigationIcon = navigationIcon,
        actions = actions,
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Black,
                scrolledContainerColor = Color.Black,
                titleContentColor = Color.White,
                navigationIconContentColor = Color.White,
                actionIconContentColor = Color.White,
            ),
    )
}

/**
 * Edge-to-edge fullscreen dialog with deterministic [WindowInsets.safeDrawing] content padding.
 */
@Composable
fun OttoFullscreenDialog(
    onDismissRequest: () -> Unit,
    topBar: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    includeIme: Boolean = false,
    dismissOnClickOutside: Boolean = true,
    dismissOnBackPress: Boolean = true,
    content: @Composable (PaddingValues) -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties =
            OttoWindowInsets.fullscreenDialogProperties(
                dismissOnClickOutside = dismissOnClickOutside,
                dismissOnBackPress = dismissOnBackPress,
            ),
    ) {
        val dialogView = LocalView.current
        SideEffect { ensureFullscreenDialogWindow(dialogView) }
        OttoFullscreenScaffold(
            modifier = modifier,
            includeIme = includeIme,
            topBar = topBar,
            content = content,
        )
    }
}

/**
 * Edge-to-edge fullscreen overlay (sibling of main shell, not a Dialog window).
 */
@Composable
fun OttoFullscreenOverlay(
    modifier: Modifier = Modifier,
    includeIme: Boolean = false,
    topBar: @Composable () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    OttoFullscreenScaffold(
        modifier = modifier,
        includeIme = includeIme,
        topBar = topBar,
        content = content,
    )
}

@Composable
private fun OttoFullscreenScaffold(
    modifier: Modifier,
    includeIme: Boolean,
    topBar: @Composable () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier =
            modifier
                .fillMaxSize()
                .then(if (includeIme) Modifier.imePadding() else Modifier),
        topBar = topBar,
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        contentWindowInsets = WindowInsets.safeDrawing,
        content = content,
    )
}

/**
 * Scrollable fullscreen content with safe-area [contentPadding] applied to scroll extent.
 */
@Composable
fun OttoFullscreenScrollContent(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    extraBottom: Dp = 28.dp,
    horizontalPadding: Dp = 18.dp,
    content: LazyListScope.() -> Unit,
) {
    val layoutDirection = LocalLayoutDirection.current
    val bottomClearance = fullscreenBottomClearance(contentPadding)
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(bottom = bottomClearance),
        contentPadding =
            PaddingValues(
                start = contentPadding.calculateStartPadding(layoutDirection) + horizontalPadding,
                end = contentPadding.calculateEndPadding(layoutDirection) + horizontalPadding,
                top = contentPadding.calculateTopPadding(),
                bottom = fullscreenScrollBottomPadding(extraBottom),
            ),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        content = content,
    )
}

/**
 * Scrollable column body for fullscreen surfaces that are not a [LazyColumn] list.
 */
@Composable
fun OttoFullscreenScrollColumn(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    extraBottom: Dp = 28.dp,
    horizontalPadding: Dp = 0.dp,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable ColumnScope.() -> Unit,
) {
    val layoutDirection = LocalLayoutDirection.current
    val bottomClearance = fullscreenBottomClearance(contentPadding)
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(bottom = bottomClearance)
                .verticalScroll(rememberScrollState())
                .padding(
                    start = contentPadding.calculateStartPadding(layoutDirection) + horizontalPadding,
                    end = contentPadding.calculateEndPadding(layoutDirection) + horizontalPadding,
                    top = contentPadding.calculateTopPadding(),
                    bottom = fullscreenScrollBottomPadding(extraBottom),
                ),
        verticalArrangement = verticalArrangement,
        content = content,
    )
}

/**
 * Non-scrolling fullscreen body with safe-area padding applied to a single child.
 */
@Composable
fun OttoFullscreenBody(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier.fillMaxSize().padding(contentPadding)) {
        content()
    }
}
