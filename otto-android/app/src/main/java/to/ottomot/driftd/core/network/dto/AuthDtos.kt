package to.ottomot.driftd.core.network.dto

import com.google.gson.annotations.SerializedName

data class RequestOtpRequest(val phoneNumber: String)

data class VerifyOtpRequest(
    val phoneNumber: String,
    val code: String,
)

data class VerifyOtpResponseDto(
    val token: String? = null,
    val user: UserDto? = null,
    val isNewUser: Boolean? = null,
    val signupChallengeToken: String? = null,
    val needsInviteCode: Boolean? = null,
)

data class CompleteSignupRequestDto(
    val signupChallengeToken: String,
    val displayName: String,
    val inviteCode: String? = null,
)

data class CheckSignupInviteRequestDto(
    val signupChallengeToken: String,
    val inviteCode: String,
)

data class AuthVerifyResponseDto(
    val token: String,
    val user: UserDto,
    /** New sign-ups must set a display name before the app treats the session as complete. */
    val isNewUser: Boolean? = null,
)

enum class DriveStatsVisibilitySetting(val wireValue: String) {
    PUBLIC("public"),
    SQUADS("squads"),
    PRIVATE("private"),
}

fun UserDto.resolvedDriveStatsVisibility(): DriveStatsVisibilitySetting {
    val raw = driveStatsVisibility?.trim()?.lowercase().orEmpty()
    return when (raw) {
        "squads" -> DriveStatsVisibilitySetting.SQUADS
        "private" -> DriveStatsVisibilitySetting.PRIVATE
        else -> DriveStatsVisibilitySetting.SQUADS
    }
}

fun UserDto.canAccessRoutes(): Boolean = true

data class UserDto(
    @SerializedName("_id") val id: String,
    val displayName: String,
    val handle: String,
    val avatarUrl: String?,
    val mapAccentKey: String?,
    val phoneNumber: String?,
    val vehicle: UserVehicleDto?,
    val lastPresenceAt: String?,
    val autoEventCheckInEnabled: Boolean?,
    val sharingSafetyDisclaimerAcknowledged: Boolean? = null,
    val showPublicGoingEventsOnProfile: Boolean?,
    /** `public` | `squads` (default) | `private` — who can see drive stats / progression on profile & web. */
    val driveStatsVisibility: String? = null,
    /** Private Routes product access. Missing/null means no access. */
    val routesAccessEnabled: Boolean? = null,
    /** Present only on the signed-in user (`/api/auth/me`). */
    val blockedUserIds: List<String>? = null,
    /** IANA time zone for server-side local-time notifications. */
    val timeZone: String? = null,
    val timeZoneUpdatedAt: String? = null,
)

/** `GET /api/users/lookup/by-phone` */
data class UserLookupByPhoneResponseDto(
    val found: Boolean,
    val user: UserDto? = null,
)

data class UserVehicleDto(
    val displayName: String?,
    val make: String?,
    val model: String?,
)
