package to.ottomot.driftd.core.session

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import to.ottomot.driftd.core.network.dto.OnboardingTestSummaryDto
import kotlinx.coroutines.runBlocking
import to.ottomot.driftd.core.notify.SquadNotificationMuteBucket
import to.ottomot.driftd.core.notify.SquadNotificationMuteEvaluator

class SessionRepository internal constructor(
    private val datastore: DataStore<Preferences>,
    private val applicationScope: CoroutineScope,
    private val gson: Gson = Gson(),
) {
    private companion object Keys {
        val AuthTokenKey = stringPreferencesKey("otto.auth.token")
        val AuthUserIdKey = stringPreferencesKey("otto.auth.user.id")
        val RequiresOnboardingNameKey = booleanPreferencesKey("otto.auth.requires_onboarding_name")
        val MarketingOnboardingCompletedKey = booleanPreferencesKey("otto.marketing_onboarding.completed")
        /** iOS `UserDefaults` key `otto.soundEffectsEnabled` — short UI / sharing tones. */
        val SoundEffectsEnabledKey = booleanPreferencesKey("otto.soundEffectsEnabled")
        val SelectedEventDistanceKey = intPreferencesKey("selected_event_distance")
        /** @deprecated Read-only migration from earlier builds */
        val LegacyEventsSearchRadiusMilesKey = intPreferencesKey("otto.events.search_radius_miles")
        /** iOS `UserDefaults` key `otto.squadLastAccessedAt` — epoch seconds per squad id. */
        val SquadLastAccessedAtKey = stringPreferencesKey("otto.squadLastAccessedAt")

        private val squadLastAccessedAtJsonType = object : TypeToken<Map<String, Double>>() {}.type
    }

    /**
     * Events tab: straight-line radius in statute miles (clamped 5…200).
     * Prefers [SelectedEventDistanceKey], falls back to legacy storage once.
     */
    val selectedEventDistanceState: StateFlow<Int> =
        datastore.data
            .map { prefs ->
                prefs[SelectedEventDistanceKey]?.coerceIn(5, 200)
                    ?: prefs[LegacyEventsSearchRadiusMilesKey]?.coerceIn(5, 200)
                    ?: 50
            }
            .distinctUntilChanged()
            .stateIn(
                scope = applicationScope,
                started = SharingStarted.Eagerly,
                initialValue = 50,
            )

    /** Eager snapshot of the bearer token persisted in preferences (nullable). */
    val authTokenState =
        datastore.data
            .map { prefs ->
                prefs[AuthTokenKey]?.trim()?.takeIf { it.isNotEmpty() }
            }
            .distinctUntilChanged()
            .stateIn(
                scope = applicationScope,
                started = SharingStarted.Eagerly,
                initialValue = null,
            )

    val authUserIdState =
        datastore.data
            .map { prefs ->
                prefs[AuthUserIdKey]?.trim()?.takeIf { it.isNotEmpty() }
            }
            .distinctUntilChanged()
            .stateIn(
                scope = applicationScope,
                started = SharingStarted.Eagerly,
                initialValue = null,
            )

    /** Short app sounds (chat engagement tone, etc.). Default on; matches iOS default. */
    val soundEffectsEnabledState: StateFlow<Boolean> =
        datastore.data
            .map { prefs ->
                prefs[SoundEffectsEnabledKey] != false
            }
            .distinctUntilChanged()
            .stateIn(
                scope = applicationScope,
                started = SharingStarted.Eagerly,
                initialValue = true,
            )

    /** One-time full-screen marketing carousel; persists across sign-out on this device. */
    val marketingOnboardingCompletedState: StateFlow<Boolean> =
        datastore.data
            .map { prefs -> prefs[MarketingOnboardingCompletedKey] == true }
            .distinctUntilChanged()
            .stateIn(
                scope = applicationScope,
                started = SharingStarted.Eagerly,
                initialValue = false,
            )

    private val pendingOnboardingTestSummary = MutableStateFlow<OnboardingTestSummaryDto?>(null)

    /** One-shot QA summary after onboarding test signup (`555-555-1111`). */
    val pendingOnboardingTestSummaryState: StateFlow<OnboardingTestSummaryDto?> =
        pendingOnboardingTestSummary.asStateFlow()

    fun setPendingOnboardingTestSummary(summary: OnboardingTestSummaryDto?) {
        pendingOnboardingTestSummary.value = summary
    }

    fun clearPendingOnboardingTestSummary() {
        pendingOnboardingTestSummary.value = null
    }

    /** True after verify-otp for a new account until display name is saved (matches iOS `requiresOnboardingName`). */
    val requiresOnboardingNameState: StateFlow<Boolean> =
        datastore.data
            .map { prefs -> prefs[RequiresOnboardingNameKey] == true }
            .distinctUntilChanged()
            .stateIn(
                scope = applicationScope,
                started = SharingStarted.Eagerly,
                initialValue = false,
            )

    /** Squads tab recency sort — last opened epoch seconds keyed by squad id (iOS parity). */
    val squadLastAccessedAtState: StateFlow<Map<String, Double>> =
        datastore.data
            .map { prefs -> decodeSquadLastAccessedAt(prefs[SquadLastAccessedAtKey]) }
            .distinctUntilChanged()
            .stateIn(
                scope = applicationScope,
                started = SharingStarted.Eagerly,
                initialValue = emptyMap(),
            )

    val authToken: Flow<String?> = authTokenState

    val authUserId: Flow<String?> = authUserIdState

    suspend fun setCredentials(
        token: String?,
        userId: String?,
    ) {
        datastore.edit { prefs ->
            if (token.isNullOrBlank()) {
                prefs.remove(AuthTokenKey)
            } else {
                prefs[AuthTokenKey] = token.trim()
            }
            if (userId.isNullOrBlank()) {
                prefs.remove(AuthUserIdKey)
            } else {
                prefs[AuthUserIdKey] = userId.trim()
            }
        }
    }

    suspend fun clearCredentials() {
        clearPendingOnboardingTestSummary()
        datastore.edit { prefs ->
            prefs.remove(AuthTokenKey)
            prefs.remove(AuthUserIdKey)
            prefs.remove(RequiresOnboardingNameKey)
        }
    }

    /** Clears session off the caller thread (e.g. OkHttp); [OttoRoot] then shows sign-in. */
    fun clearCredentialsAsync() {
        applicationScope.launch {
            clearCredentials()
        }
    }

    suspend fun setMarketingOnboardingCompleted(completed: Boolean = true) {
        datastore.edit { prefs ->
            if (completed) {
                prefs[MarketingOnboardingCompletedKey] = true
            } else {
                prefs.remove(MarketingOnboardingCompletedKey)
            }
        }
    }

    suspend fun setRequiresOnboardingName(requires: Boolean) {
        datastore.edit { prefs ->
            if (requires) {
                prefs[RequiresOnboardingNameKey] = true
            } else {
                prefs.remove(RequiresOnboardingNameKey)
            }
        }
    }

    suspend fun setSelectedEventDistance(miles: Int) {
        val clamped = miles.coerceIn(5, 200)
        datastore.edit { prefs ->
            prefs[SelectedEventDistanceKey] = clamped
            prefs.remove(LegacyEventsSearchRadiusMilesKey)
        }
    }

    suspend fun setSoundEffectsEnabled(enabled: Boolean) {
        datastore.edit { prefs ->
            prefs[SoundEffectsEnabledKey] = enabled
        }
    }

    /** Records squad detail open for Squads tab recency ordering (iOS `markCircleAccessed`). */
    suspend fun markSquadAccessed(
        circleId: String,
        knownCircleIds: Set<String>,
    ) {
        val trimmed = circleId.trim()
        if (trimmed.isEmpty()) return
        if (knownCircleIds.none { it.equals(trimmed, ignoreCase = true) }) return
        val canonicalId = knownCircleIds.first { it.equals(trimmed, ignoreCase = true) }
        val nowEpochSec = System.currentTimeMillis() / 1000.0
        datastore.edit { prefs ->
            val current = decodeSquadLastAccessedAt(prefs[SquadLastAccessedAtKey]).toMutableMap()
            current[canonicalId] = nowEpochSec
            prefs[SquadLastAccessedAtKey] = gson.toJson(current)
        }
    }

    private fun decodeSquadLastAccessedAt(json: String?): Map<String, Double> {
        if (json.isNullOrBlank()) return emptyMap()
        return runCatching { gson.fromJson<Map<String, Double>>(json, Keys.squadLastAccessedAtJsonType) }
            .getOrNull()
            .orEmpty()
    }

    fun squadMuteFlow(circleId: String): Flow<Pair<String?, String?>> {
        val id = circleId.trim()
        if (id.isEmpty()) {
            return flowOf(null to null)
        }
        val newKey = SquadNotificationMuteBucket.preferenceKey(id, SquadNotificationMuteBucket.NEW_MESSAGES)
        val menKey = SquadNotificationMuteBucket.preferenceKey(id, SquadNotificationMuteBucket.MENTIONS_AND_REPLIES)
        return datastore.data.map { prefs -> prefs[newKey] to prefs[menKey] }.distinctUntilChanged()
    }

    suspend fun setSquadMuteEncoded(
        circleId: String,
        bucket: SquadNotificationMuteBucket,
        encoded: String?,
    ) {
        val id = circleId.trim()
        if (id.isEmpty()) return
        val key = SquadNotificationMuteBucket.preferenceKey(id, bucket)
        datastore.edit { prefs ->
            if (encoded.isNullOrBlank()) {
                prefs.remove(key)
            } else {
                prefs[key] = encoded.trim()
            }
        }
    }

    /** FCM path: blocking read of current mute flags. */
    fun shouldSuppressSquadChatPushSoundSync(circleId: String?, pushType: String?): Boolean =
        runBlocking(Dispatchers.IO) {
            val prefs = datastore.data.first()
            SquadNotificationMuteEvaluator.shouldSuppressChatNotificationSound(prefs, circleId, pushType)
        }

    fun shouldSuppressSquadMentionRealtimeToneSync(circleId: String?): Boolean =
        runBlocking(Dispatchers.IO) {
            val prefs = datastore.data.first()
            SquadNotificationMuteEvaluator.shouldSuppressMentionRealtimeTone(prefs, circleId)
        }
}
