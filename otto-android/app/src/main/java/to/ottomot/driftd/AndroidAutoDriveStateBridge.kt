package to.ottomot.driftd

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import to.ottomot.driftd.core.network.dto.GarageCarDto

enum class AndroidAutoDriveBridgeMode {
    Publish,
    Consume,
}

internal data class AndroidAutoDriveStateSnapshot(
    val activeRouteDriveSession: RouteDriveSessionState?,
    val routeDrivePathSamples: List<DrivePathSample>,
    val mapSelectedRoute: to.ottomot.driftd.core.network.dto.SavedRouteDto?,
    val mapRouteSessionActive: Boolean,
    val activeDriveSession: DriveSessionState?,
    val activeDrivePathSamples: List<DrivePathSample>,
    val mapSharingLocation: Boolean,
    val liveDriveRecordingActive: Boolean,
    val selectedSharingCarId: String,
    val garageCars: List<GarageCarDto>,
) {
    companion object {
        fun empty(): AndroidAutoDriveStateSnapshot =
            AndroidAutoDriveStateSnapshot(
                activeRouteDriveSession = null,
                routeDrivePathSamples = emptyList(),
                mapSelectedRoute = null,
                mapRouteSessionActive = false,
                activeDriveSession = null,
                activeDrivePathSamples = emptyList(),
                mapSharingLocation = false,
                liveDriveRecordingActive = false,
                selectedSharingCarId = "",
                garageCars = emptyList(),
            )
    }
}

internal class AndroidAutoDriveStateBridge {
    private val _state = MutableStateFlow(AndroidAutoDriveStateSnapshot.empty())
    val state: StateFlow<AndroidAutoDriveStateSnapshot> = _state.asStateFlow()
    private val _stopDriveRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val stopDriveRequests: SharedFlow<Unit> = _stopDriveRequests.asSharedFlow()

    fun publish(snapshot: AndroidAutoDriveStateSnapshot) {
        _state.value = snapshot
    }

    fun clear() {
        _state.value = AndroidAutoDriveStateSnapshot.empty()
    }

    fun requestStopDriveSession() {
        _stopDriveRequests.tryEmit(Unit)
    }
}
