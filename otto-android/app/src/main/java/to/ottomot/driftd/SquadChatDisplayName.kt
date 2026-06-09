package to.ottomot.driftd

import to.ottomot.driftd.core.network.dto.CircleChatSenderDto
import to.ottomot.driftd.core.network.dto.UserDto

/** Squad chat / roster display name — never falls back to truncated user ids. */
internal fun resolveSquadMemberDisplayName(
    userId: String,
    sender: CircleChatSenderDto?,
    memberDisplayNamesByUserId: Map<String, String>,
    contacts: List<UserDto> = emptyList(),
    meUser: UserDto? = null,
    fallback: String = "Someone",
): String {
    val trimmedId = userId.trim().takeIf { it.isNotEmpty() } ?: return fallback
    sender
        ?.displayName
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let { return it }
    memberDisplayNamesByUserId.entries
        .firstOrNull { ottoUserIdsEqual(it.key, trimmedId) }
        ?.value
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let { return it }
    contacts
        .find { ottoUserIdsEqual(it.id, trimmedId) }
        ?.displayName
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let { return it }
    meUser
        ?.takeIf { ottoUserIdsEqual(it.id, trimmedId) }
        ?.displayName
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let { return it }
    return fallback
}
