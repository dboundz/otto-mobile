package to.ottomot.driftd

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.launch
import to.ottomot.driftd.core.media.ChatVideoSaveResult
import to.ottomot.driftd.core.media.saveChatVideoToGallery
import to.ottomot.driftd.ui.components.OttoFullscreenDialog
import to.ottomot.driftd.ui.components.OttoFullscreenOpaqueTopBar

@Composable
fun ChatFullscreenVideoDialog(
    videoUrl: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var isLoading by remember(videoUrl) { mutableStateOf(true) }
    var loadFailed by remember(videoUrl) { mutableStateOf(false) }
    var playerReady by remember(videoUrl) { mutableStateOf(false) }
    var isSaving by remember(videoUrl) { mutableStateOf(false) }
    var showSaveError by remember { mutableStateOf(false) }
    var saveErrorMessage by remember { mutableStateOf("") }
    var showSavePermissionDenied by remember { mutableStateOf(false) }

    val player =
        remember(videoUrl) {
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(videoUrl))
                prepare()
                playWhenReady = true
            }
        }

    DisposableEffect(videoUrl, player) {
        val listener =
            object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_READY -> {
                            isLoading = false
                            loadFailed = false
                            playerReady = true
                        }
                        Player.STATE_BUFFERING -> {
                            if (player.currentPosition == 0L && !playerReady) {
                                isLoading = true
                            }
                        }
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    isLoading = false
                    loadFailed = true
                    playerReady = false
                }
            }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    fun performSave() {
        if (isSaving || !playerReady) return
        scope.launch {
            isSaving = true
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            when (val result = saveChatVideoToGallery(context, videoUrl)) {
                ChatVideoSaveResult.Success -> {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
                is ChatVideoSaveResult.Error -> {
                    saveErrorMessage = result.message
                    showSaveError = true
                }
            }
            isSaving = false
        }
    }

    val legacyStorageLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                performSave()
            } else {
                showSavePermissionDenied = true
            }
        }

    fun onSaveClick() {
        if (isSaving || !playerReady) return
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            when {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                ) == PackageManager.PERMISSION_GRANTED -> performSave()
                else -> legacyStorageLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        } else {
            performSave()
        }
    }

    OttoFullscreenDialog(
        onDismissRequest = onDismiss,
        topBar = {
            OttoFullscreenOpaqueTopBar {
                if (playerReady) {
                    IconButton(
                        onClick = ::onSaveClick,
                        enabled = !isSaving,
                        modifier = Modifier.size(44.dp),
                    ) {
                        Icon(
                            Icons.Outlined.Download,
                            contentDescription = stringResource(R.string.chat_video_save_cd),
                            tint = Color.White.copy(alpha = if (isSaving) 0.45f else 1f),
                            modifier = Modifier.size(22.dp),
                        )
                    }
                } else {
                    Spacer(Modifier.size(44.dp))
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.messages_close),
                        tint = Color.White,
                    )
                }
            }
        },
    ) { contentPadding ->
        ChatFullscreenVideoContent(
            contentPadding = contentPadding,
            player = player,
            isLoading = isLoading,
            loadFailed = loadFailed,
        )
    }

    if (showSaveError) {
        AlertDialog(
            onDismissRequest = { showSaveError = false },
            title = { Text(stringResource(R.string.chat_video_save_error_title)) },
            text = { Text(saveErrorMessage) },
            confirmButton = {
                TextButton(onClick = { showSaveError = false }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
        )
    }

    if (showSavePermissionDenied) {
        AlertDialog(
            onDismissRequest = { showSavePermissionDenied = false },
            title = { Text(stringResource(R.string.chat_video_save_permission_title)) },
            text = { Text(stringResource(R.string.chat_video_save_permission_body)) },
            confirmButton = {
                TextButton(onClick = { showSavePermissionDenied = false }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
        )
    }
}

@Composable
private fun ChatFullscreenVideoContent(
    contentPadding: PaddingValues,
    player: ExoPlayer,
    isLoading: Boolean,
    loadFailed: Boolean,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .background(Color.Black),
    ) {
        if (!loadFailed) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = player
                        useController = true
                    }
                },
            )
        } else {
            Text(
                stringResource(R.string.chat_video_load_failed),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.65f),
                modifier = Modifier.align(Alignment.Center),
            )
        }

        if (isLoading && !loadFailed) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color.White,
            )
        }
    }
}
