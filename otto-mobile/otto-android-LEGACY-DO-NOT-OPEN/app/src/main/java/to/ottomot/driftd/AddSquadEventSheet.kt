package to.ottomot.driftd

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import to.ottomot.driftd.ui.insets.OttoWindowInsets.ottoBottomSheetContent
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.launch
import to.ottomot.driftd.core.network.MediaUrlResolver
import to.ottomot.driftd.core.network.dto.EventDto
import to.ottomot.driftd.core.event.parseEventInstant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddSquadEventBottomSheet(
    visible: Boolean,
    squadDisplayName: String,
    event: EventDto? = null,
    onDismiss: () -> Unit,
    onDelete: (suspend () -> Result<Unit>)? = null,
    /** Invoked after a successful create, before [onDismiss]. When non-null, overlay should offer sharing to chat. */
    onCreatedEventAwaitingSharePrompt: ((EventDto) -> Unit)? = null,
    onSubmit: suspend (
        name: String,
        description: String?,
        startsAt: Instant,
        endsAt: Instant,
        addressLabel: String?,
        streetAddress: String?,
        imageBytes: ByteArray?,
        imageContentType: String?,
    ) -> Result<SquadEventSubmitOutcome>,
) {
    if (!visible) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val zone = remember { ZoneId.systemDefault() }

    var name by rememberSaveable(event?.id) { mutableStateOf(event?.name.orEmpty()) }
    var addressLabel by rememberSaveable(event?.id) { mutableStateOf(event?.address?.label.orEmpty()) }
    var streetAddress by rememberSaveable(event?.id) { mutableStateOf(event?.address?.street1.orEmpty()) }
    var description by rememberSaveable(event?.id) { mutableStateOf(event?.description.orEmpty()) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    var selectedImageBytes by remember(event?.id) { mutableStateOf<ByteArray?>(null) }
    var selectedImageContentType by remember(event?.id) { mutableStateOf<String?>(null) }

    var startZdt by remember {
        val s =
            event?.startsAt?.let { parseEventInstant(it)?.atZone(zone) }
                ?: ZonedDateTime.now(zone).plusHours(1).withSecond(0).withNano(0)
        mutableStateOf(s)
    }
    var endZdt by remember {
        mutableStateOf(event?.endsAt?.let { parseEventInstant(it)?.atZone(zone) } ?: startZdt.plusHours(2))
    }

    val cropEventPhoto =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val outUri = OttoImageCrop.parseOutputUri(result.resultCode, result.data) ?: return@rememberLauncherForActivityResult
            val mime = ctx.contentResolver.getType(outUri) ?: "image/jpeg"
            val bytes = ctx.contentResolver.openInputStream(outUri)?.use { it.readBytes() } ?: return@rememberLauncherForActivityResult
            selectedImageBytes = bytes
            selectedImageContentType = mime
        }
    val pickEventPhoto =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            try {
                cropEventPhoto.launch(OttoImageCrop.uCropIntent(ctx, uri, 16f, 9f))
            } catch (e: Exception) {
                Log.e("OttoImageCrop", "Event uCrop launch failed", e)
            }
        }

    LaunchedEffect(startZdt) {
        if (!endZdt.isAfter(startZdt)) {
            endZdt = startZdt.plusHours(2)
        }
    }

    fun showDateTimePicker(initial: ZonedDateTime, onPick: (ZonedDateTime) -> Unit) {
        DatePickerDialog(
            ctx,
            { _, year1, monthOfYear, dayOfMonth ->
                TimePickerDialog(
                    ctx,
                    { _, hourOfDay, minute ->
                        onPick(
                            ZonedDateTime.of(
                                year1,
                                monthOfYear + 1,
                                dayOfMonth,
                                hourOfDay,
                                minute,
                                0,
                                0,
                                zone,
                            ),
                        )
                    },
                    initial.hour,
                    initial.minute,
                    false,
                ).show()
            },
            initial.year,
            initial.monthValue - 1,
            initial.dayOfMonth,
        ).show()
    }

    val shortFmt = remember(zone) { DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withZone(zone) }

    ModalBottomSheet(
        onDismissRequest = {
            if (!creating) onDismiss()
        },
        sheetState = sheetState,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .ottoBottomSheetContent()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                stringResource(if (event == null) R.string.squad_add_event_title else R.string.squad_edit_event_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 12.dp),
            )

            val bannerUrl = event?.bannerImage?.url?.let { MediaUrlResolver.resolve(it)?.toString() }
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable(enabled = !creating) {
                            pickEventPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                contentAlignment = Alignment.Center,
            ) {
                if (selectedImageBytes != null) {
                    Text(stringResource(R.string.squad_event_image_selected))
                } else if (bannerUrl != null) {
                    AsyncImage(
                        model = ottoImageRequest(ctx, bannerUrl),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.PhotoCamera, contentDescription = null)
                        Text(stringResource(R.string.squad_event_image_add))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.squad_add_event_name_label)) },
                singleLine = true,
                keyboardOptions =
                    KeyboardOptions.Default.copy(
                        capitalization = KeyboardCapitalization.Words,
                    ),
            )
            OutlinedTextField(
                value = addressLabel,
                onValueChange = { addressLabel = it },
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                label = { Text(stringResource(R.string.squad_add_event_address_label)) },
                singleLine = true,
                keyboardOptions =
                    KeyboardOptions.Default.copy(
                        capitalization = KeyboardCapitalization.Words,
                    ),
            )
            OutlinedTextField(
                value = streetAddress,
                onValueChange = { streetAddress = it },
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                label = { Text(stringResource(R.string.squad_add_event_street_address_label)) },
                singleLine = true,
                keyboardOptions =
                    KeyboardOptions.Default.copy(
                        capitalization = KeyboardCapitalization.Words,
                    ),
            )

            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = { showDateTimePicker(startZdt) { startZdt = it } },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Outlined.CalendarMonth, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Column {
                        Text(stringResource(R.string.squad_add_event_starts), style = MaterialTheme.typography.labelSmall)
                        Text(shortFmt.format(startZdt), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                OutlinedButton(
                    onClick = { showDateTimePicker(endZdt) { endZdt = it } },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Outlined.CalendarMonth, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Column {
                        Text(stringResource(R.string.squad_add_event_ends), style = MaterialTheme.typography.labelSmall)
                        Text(shortFmt.format(endZdt), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                label = { Text(stringResource(R.string.squad_add_event_description_label)) },
                minLines = 3,
                maxLines = 6,
                keyboardOptions =
                    KeyboardOptions.Default.copy(
                        capitalization = KeyboardCapitalization.Sentences,
                    ),
            )

            Text(
                stringResource(R.string.squad_add_event_footer, squadDisplayName),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
            )

            errorText?.let { err ->
                Text(
                    err,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = { if (!creating) onDismiss() },
                    enabled = !creating,
                ) {
                    Text(stringResource(android.R.string.cancel))
                }
                Button(
                    onClick = {
                        val trimmed = name.trim()
                        if (trimmed.isEmpty() || creating) return@Button
                        if (!endZdt.isAfter(startZdt)) {
                            errorText = ctx.getString(R.string.squad_add_event_error)
                            return@Button
                        }
                        errorText = null
                        creating = true
                        scope.launch {
                            val startI = startZdt.toInstant()
                            val endI = endZdt.toInstant()
                            val desc = description.trim().takeIf { it.isNotEmpty() }
                            val addr = addressLabel.trim().takeIf { it.isNotEmpty() }
                            val street = streetAddress.trim().takeIf { it.isNotEmpty() }
                            val result =
                                onSubmit(trimmed, desc, startI, endI, addr, street, selectedImageBytes, selectedImageContentType)
                            creating = false
                            if (result.isFailure || result.getOrNull() == null) {
                                errorText = ctx.getString(R.string.squad_add_event_error)
                                return@launch
                            }
                            when (val outcome = result.getOrNull()!!) {
                                is SquadEventSubmitOutcome.Created -> {
                                    if (event == null) {
                                        onCreatedEventAwaitingSharePrompt?.invoke(outcome.event)
                                    }
                                    onDismiss()
                                }
                                SquadEventSubmitOutcome.Updated -> onDismiss()
                            }
                        }
                    },
                    enabled = !creating && name.trim().isNotEmpty() && endZdt.isAfter(startZdt),
                ) {
                    Text(
                        if (creating) {
                            stringResource(if (event == null) R.string.squad_add_event_creating else R.string.squad_edit_event_saving)
                        } else {
                            stringResource(if (event == null) R.string.squad_add_event_create else R.string.squad_edit_event_save)
                        },
                    )
                }
            }
            if (event != null && onDelete != null) {
                TextButton(
                    onClick = {
                        if (creating) return@TextButton
                        creating = true
                        scope.launch {
                            val result = onDelete()
                            creating = false
                            if (result.isSuccess) onDismiss() else errorText = ctx.getString(R.string.squad_add_event_error)
                        }
                    },
                    enabled = !creating,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp),
                ) {
                    Text(stringResource(R.string.squad_edit_event_delete), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
