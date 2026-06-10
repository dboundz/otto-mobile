package to.ottomot.driftd.core.auth

import to.ottomot.driftd.core.network.dto.OnboardingTestSummaryDto

fun formatOnboardingTestSummaryMessage(summary: OnboardingTestSummaryDto): String {
    val lines = mutableListOf<String>()
    val inviteCode = summary.inviteCode?.trim().orEmpty()
    if (inviteCode.isNotEmpty()) {
        lines += "Invite code: $inviteCode"
        val creator = summary.inviteCreatorDisplayName?.trim().orEmpty()
        when {
            creator.isNotEmpty() -> lines += "From: $creator"
            summary.inviteCreatorUserId.isNullOrBlank() -> lines += "From: Otto staff"
        }
    } else {
        lines += "Invite code: (none)"
    }

    val outcome = summary.squadJoinOutcome ?: "not_applicable"
    val squadName = summary.squadName?.trim().orEmpty().ifEmpty { "Squad" }
    val squadLine =
        when (outcome) {
            "joined" -> "Squad: Added you to \"$squadName\""
            "already_member" -> "Squad: Already a member of \"$squadName\""
            "not_found" -> "Squad: Bound to missing squad (id ${summary.squadId ?: "unknown"})"
            "error" -> "Squad: Join failed for \"$squadName\""
            else -> "Squad: This invite would not add you to a squad"
        }
    lines += squadLine
    return lines.joinToString("\n")
}
