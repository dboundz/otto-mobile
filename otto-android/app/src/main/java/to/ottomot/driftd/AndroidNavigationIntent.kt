package to.ottomot.driftd

import android.content.Intent
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import kotlin.math.abs

internal const val ACTION_ANDROID_AUTO_NAVIGATE = "androidx.car.app.action.NAVIGATE"
internal const val ACTION_PHONE_NAVIGATE = "android.intent.action.NAVIGATE"

internal enum class NavigationIntentKind {
    Navigation,
    Directions,
    AddStop,
    Search,
}

internal data class NavigationIntentRequest(
    val query: String?,
    val latitude: Double?,
    val longitude: Double?,
    val kind: NavigationIntentKind,
) {
    val displayName: String
        get() =
            query?.takeIf { it.isNotBlank() }
                ?: listOfNotNull(latitude, longitude)
                    .takeIf { it.size == 2 }
                    ?.joinToString(", ") { coordinate -> "%.5f".format(coordinate) }
                ?: ""

    val hasDestination: Boolean
        get() = displayName.isNotBlank()
}

internal fun Intent.toPhoneNavigationIntentRequest(): NavigationIntentRequest? =
    parseNavigationIntentRequest(
        action = action,
        dataString = data?.toString(),
        acceptedNavigateActions = setOf(ACTION_PHONE_NAVIGATE),
        acceptsSearchAction = false,
    )

internal fun parseNavigationIntentRequest(
    action: String?,
    dataString: String?,
    acceptedNavigateActions: Set<String>,
    acceptsSearchAction: Boolean,
): NavigationIntentRequest? {
    val rawUri = dataString?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val scheme = rawUri.substringBefore(":", missingDelimiterValue = "").lowercase()
        .takeIf { it == "geo" || it == "geo.offline" } ?: return null
    val isNavigateAction = action != null && action in acceptedNavigateActions
    val isSearchAction = acceptsSearchAction && action == Intent.ACTION_VIEW
    if (!isNavigateAction && !isSearchAction) return null

    val queryParameters = rawUri.queryParameters()
    val query = queryParameters["q"]?.trim()?.takeIf { it.isNotEmpty() }
    val coordinates = rawUri.geoCoordinates()
    val requestedIntent = queryParameters["intent"]?.trim()?.lowercase()
    val kind =
        when {
            isSearchAction -> NavigationIntentKind.Search
            requestedIntent == "add_a_stop" -> NavigationIntentKind.AddStop
            requestedIntent == "directions" -> NavigationIntentKind.Directions
            else -> NavigationIntentKind.Navigation
        }
    val request =
        NavigationIntentRequest(
            query = query,
            latitude = coordinates?.first,
            longitude = coordinates?.second,
            kind = kind,
        )
    return request.takeIf { it.hasDestination && scheme.isNotBlank() }
}

private fun String.queryParameters(): Map<String, String> {
    val query = substringAfter("?", missingDelimiterValue = "")
        .substringBefore("#")
        .takeIf { it.isNotEmpty() } ?: return emptyMap()
    return query
        .split("&")
        .mapNotNull { parameter ->
            val rawName = parameter.substringBefore("=", missingDelimiterValue = parameter)
            if (rawName.isEmpty()) return@mapNotNull null
            val rawValue = parameter.substringAfter("=", missingDelimiterValue = "")
            rawName.urlDecode() to rawValue.urlDecode()
        }.toMap()
}

private fun String.urlDecode(): String =
    URLDecoder.decode(this, StandardCharsets.UTF_8.name())

private fun String.geoCoordinates(): Pair<Double, Double>? {
    val raw = substringAfter(":", missingDelimiterValue = "")
        .substringBefore("?")
        .substringBefore("#")
        .trim()
    val parts = raw.split(",")
    if (parts.size < 2) return null
    val latitude = parts[0].toDoubleOrNull() ?: return null
    val longitude = parts[1].toDoubleOrNull() ?: return null
    if (!latitude.isFinite() || !longitude.isFinite()) return null
    if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
    return if (abs(latitude) < 0.000001 && abs(longitude) < 0.000001) {
        null
    } else {
        latitude to longitude
    }
}
