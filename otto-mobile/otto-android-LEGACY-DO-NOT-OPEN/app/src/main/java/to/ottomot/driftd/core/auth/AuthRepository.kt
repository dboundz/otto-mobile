package to.ottomot.driftd.core.auth

import com.google.gson.JsonObject
import okhttp3.ResponseBody
import org.json.JSONObject
import retrofit2.HttpException
import retrofit2.Response
import to.ottomot.driftd.core.analytics.OttoAnalytics
import to.ottomot.driftd.core.network.OttoHttpApi
import to.ottomot.driftd.core.network.dto.AuthVerifyResponseDto
import to.ottomot.driftd.core.network.dto.CheckSignupInviteRequestDto
import to.ottomot.driftd.core.network.dto.CompleteSignupRequestDto
import to.ottomot.driftd.core.network.dto.RequestOtpRequest
import to.ottomot.driftd.core.network.dto.UserDto
import to.ottomot.driftd.core.network.dto.VerifyOtpRequest
import to.ottomot.driftd.core.session.SessionRepository
import java.io.IOException
import kotlinx.coroutines.CancellationException

class AuthRepository internal constructor(
    private val api: OttoHttpApi,
    private val sessionRepository: SessionRepository,
) {

    suspend fun requestOtp(phoneNumber: String) =
        try {
            val normalized = normalizePhoneNumber(phoneNumber)
            val resp =
                api.requestOtp(RequestOtpRequest(normalized))
            consumeResponse(resp) { "OTP request failed ($it)." }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            throw AuthFailure("Network error: ${e.message ?: "could not reach server"}")
        }

    suspend fun verifyOtp(
        phoneNumber: String,
        code: String,
    ): VerifyOtpOutcome =
        try {
            val raw =
                api.verifyOtp(
                    VerifyOtpRequest(
                        phoneNumber = normalizePhoneNumber(phoneNumber),
                        code = code.trim(),
                    ),
                )
            when {
                raw.token != null && raw.user != null -> {
                    val isNew = raw.isNewUser == true
                    sessionRepository.setCredentials(token = raw.token, userId = raw.user.id)
                    sessionRepository.setRequiresOnboardingName(isNew)
                    VerifyOtpOutcome.SignedIn(
                        token = raw.token,
                        user = raw.user,
                        isNewUser = isNew,
                    )
                }
                !raw.signupChallengeToken.isNullOrBlank() -> {
                    VerifyOtpOutcome.SignupChallenge(
                        signupChallengeToken = raw.signupChallengeToken,
                        needsInviteCode = raw.needsInviteCode == true,
                    )
                }
                else -> throw AuthFailure("Unexpected sign-in response. Update the app or try again.")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            throw AuthFailure("Sign-in failed (${e.code()}).")
        } catch (e: IOException) {
            throw AuthFailure("Network error: ${e.message ?: "could not reach server"}")
        } catch (e: Exception) {
            throw AuthFailure(e.message ?: "Sign-in failed.")
        }

    suspend fun completeSignup(
        signupChallengeToken: String,
        displayName: String,
        inviteCode: String?,
    ): AuthVerifyResponseDto =
        try {
            val trimmedName = displayName.trim()
            if (trimmedName.isEmpty()) {
                throw AuthFailure("Please enter your name.")
            }
            val trimmedInvite = inviteCode?.trim()?.takeIf { it.isNotEmpty() }
            val dto =
                api.completeSignup(
                    CompleteSignupRequestDto(
                        signupChallengeToken = signupChallengeToken,
                        displayName = trimmedName,
                        inviteCode = trimmedInvite,
                    ),
                )
            sessionRepository.setCredentials(token = dto.token, userId = dto.user.id)
            sessionRepository.setRequiresOnboardingName(false)
            OttoAnalytics.logSignUpComplete()
            dto
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            throw AuthFailure("Could not finish signup (${e.code()}).")
        } catch (e: IOException) {
            throw AuthFailure("Network error: ${e.message ?: "could not reach server"}")
        } catch (e: Exception) {
            throw AuthFailure(e.message ?: "Could not finish signup.")
        }

    suspend fun checkSignupInvite(
        signupChallengeToken: String,
        inviteCode: String,
    ) {
        try {
            val trimmed = inviteCode.trim()
            if (trimmed.isEmpty()) {
                throw AuthFailure("Enter your invite code.")
            }
            val resp =
                api.checkSignupInvite(
                    CheckSignupInviteRequestDto(
                        signupChallengeToken = signupChallengeToken,
                        inviteCode = trimmed,
                    ),
                )
            try {
                if (resp.isSuccessful) return
                val raw = resp.errorBody()?.use { it.string() }?.trim().orEmpty()
                val msg =
                    runCatching { JSONObject(raw).optString("error") }
                        .getOrNull()
                        ?.trim()
                        ?.takeIf { it.isNotEmpty() }
                        ?: raw.takeIf { it.isNotBlank() }
                        ?: "Invalid or expired invite code."
                throw AuthFailure(msg)
            } finally {
                resp.body()?.close()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: AuthFailure) {
            throw e
        } catch (e: IOException) {
            throw AuthFailure("Network error: ${e.message ?: "could not reach server"}")
        } catch (e: Exception) {
            throw AuthFailure(e.message ?: "Could not verify invite code.")
        }
    }

    /**
     * New users: save display name then clear [SessionRepository.requiresOnboardingNameState] so [OttoRoot] can
     * enter the shell (iOS `completeOnboardingName`).
     */
    suspend fun completeOnboardingName(displayName: String) {
        try {
            val userId =
                sessionRepository.authUserIdState.value?.trim()?.takeIf { it.isNotEmpty() }
                    ?: throw AuthFailure("User session not ready. Try signing in again.")
            val trimmed = displayName.trim()
            if (trimmed.isEmpty()) {
                throw AuthFailure("Please enter your name.")
            }
            val body = JsonObject()
            body.addProperty("displayName", trimmed)
            api.patchUser(userId, body)
            sessionRepository.setRequiresOnboardingName(false)
            OttoAnalytics.logOnboardingNameComplete()
        } catch (e: CancellationException) {
            throw e
        } catch (e: AuthFailure) {
            throw e
        } catch (e: HttpException) {
            throw AuthFailure("Could not save your name (${e.code()}).")
        } catch (e: IOException) {
            throw AuthFailure("Network error: ${e.message ?: "could not reach server"}")
        } catch (e: Exception) {
            throw AuthFailure(e.message ?: "Could not save your name.")
        }
    }

    suspend fun fetchMe(): UserDto =
        try {
            api.fetchMe()
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            throw AuthFailure("Could not load account (${e.code()}).")
        } catch (e: IOException) {
            throw AuthFailure("Network error: ${e.message ?: "could not reach server"}")
        } catch (e: Exception) {
            throw AuthFailure(e.message ?: "Could not load account.")
        }

    suspend fun signOut() {
        sessionRepository.clearCredentials()
    }

    private fun consumeResponse(
        resp: Response<ResponseBody>,
        statusMessage: (code: Int) -> String,
    ) {
        try {
            if (resp.isSuccessful) return
            val body =
                resp.errorBody()?.use { bytes ->
                    bytes.string().takeIf { it.isNotBlank() }
                }
            throw AuthFailure((body ?: statusMessage(resp.code())).trim())
        } finally {
            resp.body()?.close()
        }
    }

    companion object {
        fun normalizePhoneNumber(raw: String): String = raw.trim()
    }
}

/** Result of `verify-otp`: existing session vs signup challenge before `complete-signup`. */
sealed class VerifyOtpOutcome {
    data class SignedIn(
        val token: String,
        val user: UserDto,
        val isNewUser: Boolean,
    ) : VerifyOtpOutcome()

    data class SignupChallenge(
        val signupChallengeToken: String,
        val needsInviteCode: Boolean,
    ) : VerifyOtpOutcome()
}

class AuthFailure(message: String) : Exception(message)
