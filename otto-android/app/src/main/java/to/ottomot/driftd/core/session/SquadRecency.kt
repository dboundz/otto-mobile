package to.ottomot.driftd.core.session

import to.ottomot.driftd.core.network.dto.CircleDto

private fun lastAccessedEpochSec(circleId: String, lastAccessedEpochSecById: Map<String, Double>): Double {
    val trimmed = circleId.trim()
    if (trimmed.isEmpty()) return 0.0
    lastAccessedEpochSecById[trimmed]?.let { return it }
    return lastAccessedEpochSecById.entries
        .firstOrNull { (key, _) -> key.equals(trimmed, ignoreCase = true) }
        ?.value
        ?: 0.0
}

/** iOS `AppState.circlesSortedByRecentAccess` — most recently opened squads first; stable API order on ties. */
fun circlesSortedByRecentAccess(
    circles: List<CircleDto>,
    lastAccessedEpochSecById: Map<String, Double>,
): List<CircleDto> =
    circles
        .withIndex()
        .sortedWith(
            compareByDescending<IndexedValue<CircleDto>> { (_, circle) ->
                lastAccessedEpochSec(circle.id, lastAccessedEpochSecById)
            }.thenBy { (index, _) -> index },
        ).map { it.value }
