package to.ottomot.driftd.map

import java.time.Instant
import to.ottomot.driftd.core.network.dto.CircleDto
import to.ottomot.driftd.core.network.dto.PresenceMemberDto

/** iOS layer sheet scope + realtime filtering. */
internal object OttoMapLayerIds {
    const val PUBLIC: String = "public"

    /**
     * When map layers aren't narrowed, merge presence from each membership under this cap so nearby
     * friends sharing on another squad still load; very large memberships fall back to one squad.
     */
    const val MAX_SQUADS_FULL_PRESENCE_FETCH: Int = 24
}

internal fun effectiveMapLayerCircleIds(
    circles: List<CircleDto>,
    preferredScopeId: String,
    selectedLayerIds: Set<String>,
): Set<String> {
    val valid = circles.map { it.id }.filter { it.isNotBlank() }.toSet()
    val chosen = selectedLayerIds.filter { it in valid }.toSet()
    if (chosen.isNotEmpty()) return chosen
    val pref = preferredScopeId.trim()
    if (pref == OttoMapLayerIds.PUBLIC) {
        return setOf(OttoMapLayerIds.PUBLIC)
    }
    if (valid.isEmpty()) return emptySet()
    return if (valid.size <= OttoMapLayerIds.MAX_SQUADS_FULL_PRESENCE_FETCH) {
        valid
    } else {
        val prefSquad = pref.takeIf { it.isNotBlank() && it in valid }
        if (prefSquad != null) setOf(prefSquad) else setOf(valid.first())
    }
}

internal fun mergePresenceLists(lists: List<List<PresenceMemberDto>>): List<PresenceMemberDto> {
    val merged = LinkedHashMap<String, PresenceMemberDto>()
    for (m in lists.flatten()) {
        val uid = m.userId.trim().ifBlank { continue }
        val prev = merged[uid]
        merged[uid] =
            if (prev == null) {
                m
            } else {
                pickRicherPresence(prev, m)
            }
    }
    return merged.values.toList()
}

internal fun mergePresenceMemberUpdate(
    existing: PresenceMemberDto?,
    incoming: PresenceMemberDto,
): PresenceMemberDto {
    if (existing == null) return incoming
    return pickRicherPresence(existing, incoming)
}

private fun pickRicherPresence(
    a: PresenceMemberDto,
    b: PresenceMemberDto,
): PresenceMemberDto {
    if (a.isActive != b.isActive) return if (a.isActive) a else b
    val aIn = a.inApp != false
    val bIn = b.inApp != false
    if (aIn != bIn) return if (aIn) a else b
    val ta = parseIso(a.updatedAt)
    val tb = parseIso(b.updatedAt)
    return when {
        ta != null && tb != null -> if (ta >= tb) a else b
        ta != null -> a
        tb != null -> b
        else -> a
    }
}

private fun parseIso(raw: String?): Instant? =
    raw
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let { runCatching { Instant.parse(it) }.getOrNull() }

internal fun normalizePresenceMovementMode(member: PresenceMemberDto): String {
    val raw = member.movementMode?.trim()?.lowercase()
    if (raw == "driving" || raw == "walking") return raw
    if (!member.isActive) return "unknown"
    val s = member.speedMph ?: return "unknown"
    if (s >= 10.0) return "driving"
    if (s > 1.0 && s < 7.0) return "walking"
    return "unknown"
}
