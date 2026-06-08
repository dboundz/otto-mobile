package to.ottomot.driftd.core.permissions

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.mutableStateOf
import to.ottomot.driftd.appContainer

/**
 * Requests declared dangerous/runtime permissions once when [OttoRoot] is shown.
 * Location is requested from Map/Events, and activity recognition is requested from sharing.
 */
@Composable
fun OttoLaunchRuntimePermissions() {
    val ctx = LocalContext.current
    var didStartOrchestration by rememberSaveable { mutableStateOf(false) }

    val multiLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) {
            ctx.applicationContext.appContainer().deviceLocationTracker.tryStartListening()
        }

    LaunchedEffect(Unit) {
        if (didStartOrchestration) return@LaunchedEffect
        didStartOrchestration = true

        val perms =
            buildList {
                if (shouldRequestPostNotifications(ctx)) {
                    add(Manifest.permission.POST_NOTIFICATIONS)
                }
            }

        if (perms.isNotEmpty()) {
            multiLauncher.launch(perms.toTypedArray())
        } else {
            ctx.applicationContext.appContainer().deviceLocationTracker.tryStartListening()
        }
    }
}
