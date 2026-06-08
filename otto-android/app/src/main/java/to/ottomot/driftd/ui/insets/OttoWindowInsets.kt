package to.ottomot.driftd.ui.insets

import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogProperties

/**
 * Shared inset contracts for Otto Android edge-to-edge surfaces.
 *
 * IME policy:
 * - Activity tab content (soft-input resize): do not apply root [imePadding]; lift controls may
 *   use IME padding only when they are not already shifted by window resize.
 * - Fullscreen dialog/overlay ([fullscreenDialogProperties]): pass [includeIme] on
 *   [to.ottomot.driftd.ui.components.OttoFullscreenDialog] when the surface hosts text fields.
 */
object OttoWindowInsets {
    fun fullscreenDialogProperties(
        dismissOnClickOutside: Boolean = true,
        dismissOnBackPress: Boolean = true,
    ) =
        DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnClickOutside = dismissOnClickOutside,
            dismissOnBackPress = dismissOnBackPress,
        )

    /** Main shell scaffold: clear status bar only; bottom nav handles gesture/3-button nav. */
    fun Modifier.ottoShellScaffold(): Modifier = statusBarsPadding()

    /** Bottom tab bar column: sit above system navigation bar. */
    fun Modifier.ottoShellBottomBar(): Modifier = navigationBarsPadding()

    /** Modal bottom sheet scrollable content. */
    fun Modifier.ottoBottomSheetContent(): Modifier = navigationBarsPadding()
}
