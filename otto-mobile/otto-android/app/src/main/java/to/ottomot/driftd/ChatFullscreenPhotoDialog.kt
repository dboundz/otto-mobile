package to.ottomot.driftd

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import to.ottomot.driftd.core.media.ChatPhotoSaveResult
import to.ottomot.driftd.core.media.saveChatPhotoToGallery
import to.ottomot.driftd.ui.components.OttoFullscreenDialog
import to.ottomot.driftd.ui.components.OttoFullscreenOpaqueTopBar

/** Matches iOS `ChatFullscreenPhotoView` zoom limits. */
private const val CHAT_FS_PHOTO_MAX_ZOOM = 5f
private const val CHAT_FS_PHOTO_DOUBLE_TAP_ZOOM = 2.5f

@Composable
internal fun ChatFullscreenPhotoDialog(
    url: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var imageLoaded by remember(url) { mutableStateOf(false) }
    var isSaving by remember(url) { mutableStateOf(false) }
    var showSaveError by remember { mutableStateOf(false) }
    var saveErrorMessage by remember { mutableStateOf("") }
    var showSavePermissionDenied by remember { mutableStateOf(false) }

    fun performSave() {
        if (isSaving || !imageLoaded) return
        scope.launch {
            isSaving = true
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            when (val result = saveChatPhotoToGallery(context, url)) {
                ChatPhotoSaveResult.Success -> {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
                is ChatPhotoSaveResult.Error -> {
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
        if (isSaving || !imageLoaded) return
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
                if (imageLoaded) {
                    IconButton(
                        onClick = ::onSaveClick,
                        enabled = !isSaving,
                        modifier = Modifier.size(44.dp),
                    ) {
                        Icon(
                            Icons.Outlined.Download,
                            contentDescription = stringResource(R.string.chat_photo_save_cd),
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
        ChatFullscreenPhotoZoomContent(
            url = url,
            contentPadding = contentPadding,
            onImageLoadedChange = { imageLoaded = it },
        )
    }

    if (showSaveError) {
        AlertDialog(
            onDismissRequest = { showSaveError = false },
            title = { Text(stringResource(R.string.chat_photo_save_error_title)) },
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
            title = { Text(stringResource(R.string.chat_photo_save_permission_title)) },
            text = { Text(stringResource(R.string.chat_photo_save_permission_body)) },
            confirmButton = {
                TextButton(onClick = { showSavePermissionDenied = false }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
        )
    }
}

@Composable
private fun ChatFullscreenPhotoZoomContent(
    url: String,
    contentPadding: PaddingValues,
    onImageLoadedChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val scaleState = remember(url) { mutableFloatStateOf(1f) }
    val offsetXState = remember(url) { mutableFloatStateOf(0f) }
    val offsetYState = remember(url) { mutableFloatStateOf(0f) }
    var layoutSize by remember(url) { mutableStateOf(IntSize.Zero) }
    val photoMaxW = layoutSize.width.toFloat().coerceAtLeast(1f)
    val photoMaxH = layoutSize.height.toFloat().coerceAtLeast(1f)

    var imageLoaded by remember(url) { mutableStateOf(false) }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .background(Color.Black)
                .padding(horizontal = 8.dp)
                .onGloballyPositioned { coordinates -> layoutSize = coordinates.size },
    ) {
        if (!imageLoaded) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color.White,
            )
        }
        AsyncImage(
            model = ottoImageRequest(context, url),
            contentDescription = stringResource(R.string.chat_photo_fullscreen_cd),
            onSuccess = {
                imageLoaded = true
                onImageLoadedChange(true)
            },
            onError = {
                imageLoaded = false
                onImageLoadedChange(false)
            },
            modifier =
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scaleState.floatValue
                        scaleY = scaleState.floatValue
                        translationX = offsetXState.floatValue
                        translationY = offsetYState.floatValue
                    }
                    .pointerInput(url, photoMaxW, photoMaxH) {
                        detectTransformGestures { _, panChange, zoomChange, _ ->
                            val curScale = scaleState.floatValue
                            val newScale = (curScale * zoomChange).coerceIn(1f, CHAT_FS_PHOTO_MAX_ZOOM)
                            if (newScale <= 1.001f) {
                                scaleState.floatValue = 1f
                                offsetXState.floatValue = 0f
                                offsetYState.floatValue = 0f
                            } else {
                                scaleState.floatValue = newScale
                                val nx = offsetXState.floatValue + panChange.x
                                val ny = offsetYState.floatValue + panChange.y
                                val (cx, cy) =
                                    chatFullscreenClampPan(
                                        nx,
                                        ny,
                                        newScale,
                                        photoMaxW,
                                        photoMaxH,
                                    )
                                offsetXState.floatValue = cx
                                offsetYState.floatValue = cy
                            }
                        }
                    }
                    .pointerInput(url) {
                        detectTapGestures(
                            onDoubleTap = {
                                if (scaleState.floatValue > 1.05f) {
                                    scaleState.floatValue = 1f
                                    offsetXState.floatValue = 0f
                                    offsetYState.floatValue = 0f
                                } else {
                                    scaleState.floatValue =
                                        CHAT_FS_PHOTO_DOUBLE_TAP_ZOOM.coerceIn(1f, CHAT_FS_PHOTO_MAX_ZOOM)
                                    offsetXState.floatValue = 0f
                                    offsetYState.floatValue = 0f
                                }
                            },
                        )
                    },
            contentScale = ContentScale.Fit,
        )
    }
}

private fun chatFullscreenClampPan(
    ox: Float,
    oy: Float,
    scale: Float,
    maxW: Float,
    maxH: Float,
): Pair<Float, Float> {
    if (scale <= 1.001f) return 0f to 0f
    val maxPanX = maxW * (scale - 1f) * 0.5f
    val maxPanY = maxH * (scale - 1f) * 0.5f
    return ox.coerceIn(-maxPanX, maxPanX) to oy.coerceIn(-maxPanY, maxPanY)
}
