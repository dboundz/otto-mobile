package to.ottomot.driftd

import to.ottomot.driftd.core.network.dto.DriveDto
import to.ottomot.driftd.core.network.dto.SavedRouteDto

data class DriveChatShareContext(
    val driveId: String,
    val previewTitle: String,
    val previewDistanceMeters: Double,
    val previewDriveTimeSeconds: Long,
    val previewCompletedAtIso: String?,
    val lockedCircleId: String?,
    val mapPreviewSnapshotInput: DriveMapPreviewSnapshotInput? = null,
)

data class DriveSummarySheetUi(
    val drive: DriveDto,
    val isOwner: Boolean,
    val lockedShareCircleId: String? = null,
)

data class SavedRouteDetailSheetUi(
    val route: SavedRouteDto,
)

/** Entry payload while Route Builder is presented (iOS Profile/Map fullScreenCover parity). */
sealed class RouteBuilderEntry {
    data class New(
        val centerLat: Double,
        val centerLng: Double,
    ) : RouteBuilderEntry()

    data class Edit(
        val route: SavedRouteDto,
    ) : RouteBuilderEntry()
}
