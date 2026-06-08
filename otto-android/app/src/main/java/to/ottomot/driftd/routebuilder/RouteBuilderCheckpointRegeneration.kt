package to.ottomot.driftd.routebuilder

import kotlin.math.abs
import to.ottomot.driftd.routebuilder.engine.CheckpointDensityTier
import to.ottomot.driftd.routebuilder.engine.RouteAutoCheckpointGenerator
import to.ottomot.driftd.routebuilder.engine.RouteLatLng

internal data class PendingCheckpointRegeneration(
    val spacingMeters: Double?,
    val densityTier: CheckpointDensityTier,
)

internal object RouteBuilderCheckpointRegeneration {
    /**
     * Schedules checkpoint replacement after start/finish move.
     * Does not mutate [state] or strip waypoints — existing checkpoints stay visible until regen runs.
     */
    fun computePendingAfterEndpointChange(state: RouteBuilderScreenState): PendingCheckpointRegeneration? {
        val hadCheckpoints = state.checkpointCount > 0
        if (!hadCheckpoints && state.activeCheckpointSpacingMeters == null && !state.hasCompletedGuidedGeneration) {
            return null
        }
        var spacing = state.activeCheckpointSpacingMeters
        if (spacing == null && hadCheckpoints) {
            spacing = inferredCheckpointSpacingMeters(state.checkpointCount, state.roadCoordinates)
        }
        return PendingCheckpointRegeneration(
            spacingMeters = spacing,
            densityTier = state.selectedCheckpointDensity,
        )
    }
}

internal fun inferredCheckpointSpacingMeters(
    checkpointCount: Int,
    roadCoordinates: List<RouteLatLng>,
): Double? {
    if (checkpointCount <= 0 || roadCoordinates.size < 2) return null
    return RouteAutoCheckpointGenerator.viableIntervals(roadCoordinates)
        .minByOrNull { abs(it.checkpointCount - checkpointCount) }
        ?.spacingMeters
}
