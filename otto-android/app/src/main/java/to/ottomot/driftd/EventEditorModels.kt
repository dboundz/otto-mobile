package to.ottomot.driftd

import android.content.Context
import java.time.Instant
import to.ottomot.driftd.core.location.geocodeSquadEventAddressIfResolvable

internal data class EventEditorAddressPayload(
    val label: String?,
    val streetAddress: String?,
    val city: String?,
    val region: String?,
    val postalCode: String?,
) {
    val hasStructuredDetails: Boolean
        get() = !streetAddress.isNullOrBlank() || !city.isNullOrBlank() || !region.isNullOrBlank() || !postalCode.isNullOrBlank()

    fun geocodeQuery(): String? =
        listOfNotNull(
            label.trimmedOrNull(),
            streetAddress.trimmedOrNull(),
            city.trimmedOrNull(),
            region.trimmedOrNull(),
            postalCode.trimmedOrNull(),
        )
            .joinToString(", ")
            .takeIf { it.isNotEmpty() }
}

internal data class EventEditorSubmitPayload(
    val name: String,
    val description: String?,
    val startsAt: Instant,
    val endsAt: Instant,
    val address: EventEditorAddressPayload,
    val imageBytes: ByteArray?,
    val imageContentType: String?,
)

internal suspend fun geocodeEventEditorAddressIfResolvable(
    context: Context,
    address: EventEditorAddressPayload,
): Pair<Double, Double>? {
    val query = address.geocodeQuery() ?: return null
    return geocodeSquadEventAddressIfResolvable(context, query)
}

private fun String?.trimmedOrNull(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
