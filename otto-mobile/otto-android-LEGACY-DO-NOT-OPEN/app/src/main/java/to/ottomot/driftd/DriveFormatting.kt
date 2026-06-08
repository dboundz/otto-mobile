package to.ottomot.driftd

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.WbTwilight
import androidx.compose.ui.graphics.vector.ImageVector
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

internal fun formatDriveDistanceMiles(meters: Double?): String {
    val miles = (meters ?: 0.0) / 1609.344
    return if (miles < 10) {
        String.format(Locale.US, "%.1f mi", miles)
    } else {
        "${miles.roundToInt()} mi"
    }
}

internal fun formatDriveDurationSeconds(seconds: Double?): String {
    val total = maxOf(0, (seconds ?: 0.0).roundToInt())
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m"
        else -> "<1m"
    }
}

internal fun resolvedDriveAverageSpeedMph(
    storedAvgMph: Double?,
    distanceMeters: Double?,
    durationSeconds: Long,
): Double {
    val stored = storedAvgMph ?: 0.0
    if (stored.isFinite() && stored > 0) return stored
    val duration = maxOf(0L, durationSeconds)
    val distance = distanceMeters ?: 0.0
    if (duration <= 0 || distance <= 0) return 0.0
    return (distance / duration.toDouble()) * 2.23694
}

internal fun resolvedDriveAverageSpeedMph(drive: to.ottomot.driftd.core.network.dto.DriveDto): Double =
    resolvedDriveAverageSpeedMph(
        storedAvgMph = drive.avgSpeedMph,
        distanceMeters = drive.distanceMeters,
        durationSeconds = driveTimeSecondsBetween(drive.startTime, drive.endTime),
    )

internal fun formatDriveSpeedMph(mph: Double?): String {
    val v = mph ?: 0.0
    return if (v.isFinite() && v > 0) "${v.roundToInt()} mph" else "—"
}

internal fun formatDriveAverageSpeedMph(drive: to.ottomot.driftd.core.network.dto.DriveDto): String =
    formatDriveSpeedMph(resolvedDriveAverageSpeedMph(drive))

internal fun formatDriveCompletedAt(iso: String?): String {
    val raw = iso?.trim().orEmpty()
    if (raw.isEmpty()) return ""
    return runCatching {
        val inst = Instant.parse(raw)
        DateTimeFormatter.ofPattern("MMM d, yyyy • h:mm a", Locale.US)
            .withZone(ZoneId.systemDefault())
            .format(inst)
    }.getOrElse { raw }
}

internal fun driveShareExternalText(title: String, distance: String, duration: String): String =
    "$title — $distance · $duration"

internal fun driveTimeSecondsBetween(startIso: String?, endIso: String?): Long {
    val start = runCatching { Instant.parse(startIso?.trim().orEmpty()) }.getOrNull() ?: return 0
    val end = runCatching { Instant.parse(endIso?.trim().orEmpty()) }.getOrNull() ?: start
    return maxOf(0, end.epochSecond - start.epochSecond)
}

internal fun driveFlavorIconVector(kind: DriveFlavorIconKind): ImageVector =
    when (kind) {
        DriveFlavorIconKind.LateNight -> Icons.Outlined.DarkMode
        DriveFlavorIconKind.Morning -> Icons.Outlined.WbTwilight
        DriveFlavorIconKind.Afternoon -> Icons.Outlined.WbSunny
        DriveFlavorIconKind.Evening -> Icons.Outlined.WbTwilight
        DriveFlavorIconKind.Generic -> Icons.Outlined.Route
    }

internal fun profileDriveRowSubtitle(drive: to.ottomot.driftd.core.network.dto.DriveDto): String {
    val parts = mutableListOf<String>()
    parts += formatDriveDistanceMiles(drive.distanceMeters)
    profileDriveDurationLabel(drive)?.takeIf { it.isNotBlank() }?.let { parts += it }
    profileRelativeTimeLabel(drive.startTime)?.takeIf { it.isNotBlank() }?.let { parts += it }
    return parts.filter { it.isNotBlank() }.joinToString(" · ")
}

internal fun pendingDriveRowSubtitle(archive: PendingDriveArchiveDto): String {
    val parts = mutableListOf<String>()
    parts += formatDriveDistanceMiles(archive.distanceMeters)
    profileRelativeTimeLabel(archive.endedAt)?.takeIf { it.isNotBlank() }?.let { parts += it }
    val expiresAt = runCatching { Instant.parse(archive.expiresAt) }.getOrNull()
    if (expiresAt != null) {
        val days = maxOf(1, ((expiresAt.epochSecond - Instant.now().epochSecond) / 86400).toInt())
        parts += "Expires in $days days"
    }
    return parts.filter { it.isNotBlank() }.joinToString(" · ")
}

internal fun savedRouteSubtitle(route: to.ottomot.driftd.core.network.dto.SavedRouteDto): String {
    val parts = mutableListOf<String>()
    if ((route.distanceMeters ?: 0.0) > 0.0) {
        parts += formatDriveDistanceMiles(route.distanceMeters)
    }
    val pointCount = route.points.orEmpty().size
    parts += if (pointCount == 1) "1 point" else "$pointCount points"
    return parts.joinToString(" · ")
}

private fun profileDriveDurationLabel(drive: to.ottomot.driftd.core.network.dto.DriveDto): String? {
    val endTime = drive.endTime?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val seconds = driveTimeSecondsBetween(drive.startTime, endTime)
    if (seconds <= 0) return "0m"
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

private fun profileRelativeTimeLabel(iso: String?): String {
    val raw = iso?.trim().orEmpty()
    if (raw.isEmpty()) return ""
    val instant = runCatching { Instant.parse(raw) }.getOrNull() ?: return ""
    val seconds = maxOf(0, Instant.now().epochSecond - instant.epochSecond)
    if (seconds < 60) return "Just now"
    val minutes = seconds / 60
    if (minutes < 60) return "${minutes}m ago"
    val hours = minutes / 60
    if (hours < 48) return "${hours}h ago"
    return "${hours / 24}d ago"
}

internal fun driveChatShareContextFor(drive: to.ottomot.driftd.core.network.dto.DriveDto): DriveChatShareContext? {
    if (!drive.status.equals("completed", ignoreCase = true) && drive.endTime.isNullOrBlank()) {
        return null
    }
    return DriveChatShareContext(
        driveId = drive.id,
        previewTitle = DriveDisplayNaming.listTitle(drive),
        previewDistanceMeters = drive.distanceMeters ?: 0.0,
        previewDriveTimeSeconds = driveTimeSecondsBetween(drive.startTime, drive.endTime),
        previewCompletedAtIso = drive.endTime ?: drive.startTime,
        lockedCircleId = null,
        mapPreviewSnapshotInput = DriveMapPreviewSnapshotInput.fromRoute(drive.route, emptyList()),
    )
}
