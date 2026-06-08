package to.ottomot.driftd

enum class MapMarkerSharePreviewKind {
    SavedPlace,
    RaceTrack,
}

data class MapMarkerSharePayload(
    val title: String,
    val subtitle: String?,
    val latitude: Double?,
    val longitude: Double?,
    val savedPlaceId: String? = null,
    val externalShareText: String,
    val previewKind: MapMarkerSharePreviewKind,
)

internal fun mapMarkerSharePayloadForSavedPlace(
    id: String? = null,
    name: String,
    addressSummary: String?,
    latitude: Double,
    longitude: Double,
): MapMarkerSharePayload =
    MapMarkerSharePayload(
        title = name,
        subtitle = addressSummary,
        latitude = latitude,
        longitude = longitude,
        savedPlaceId = id?.trim()?.takeIf { it.isNotEmpty() },
        externalShareText = mapMarkerShareText(name, addressSummary, latitude, longitude),
        previewKind = MapMarkerSharePreviewKind.SavedPlace,
    )

internal fun mapMarkerSharePayloadForAdhocPlace(
    name: String?,
    addressSummary: String?,
    latitude: Double,
    longitude: Double,
): MapMarkerSharePayload {
    val title =
        name?.trim()?.takeIf { it.isNotEmpty() }
            ?: addressSummary?.trim()?.takeIf { it.isNotEmpty() }
            ?: "Shared place"
    return MapMarkerSharePayload(
        title = title,
        subtitle = addressSummary,
        latitude = latitude,
        longitude = longitude,
        savedPlaceId = null,
        externalShareText = mapMarkerShareText(title, addressSummary, latitude, longitude),
        previewKind = MapMarkerSharePreviewKind.SavedPlace,
    )
}

internal fun mapMarkerSharePayloadForRaceTrack(
    name: String,
    locationLine: String?,
    latitude: Double?,
    longitude: Double?,
): MapMarkerSharePayload =
    MapMarkerSharePayload(
        title = name,
        subtitle = locationLine,
        latitude = latitude,
        longitude = longitude,
        savedPlaceId = null,
        externalShareText = mapMarkerShareText(name, locationLine, latitude, longitude),
        previewKind = MapMarkerSharePreviewKind.RaceTrack,
    )

internal fun mapMarkerShareText(
    title: String,
    subtitle: String?,
    lat: Double?,
    lng: Double?,
): String {
    val lines = mutableListOf(title)
    subtitle?.trim()?.takeIf { it.isNotEmpty() }?.let { lines.add(it) }
    if (lat != null && lng != null) {
        lines.add(String.format(java.util.Locale.US, "%.5f, %.5f", lat, lng))
    }
    return lines.joinToString("\n")
}
