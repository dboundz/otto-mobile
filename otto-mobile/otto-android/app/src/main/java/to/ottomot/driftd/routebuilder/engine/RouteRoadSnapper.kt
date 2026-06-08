package to.ottomot.driftd.routebuilder.engine

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import to.ottomot.driftd.core.event.haversineMeters
import java.util.concurrent.TimeUnit

/**
 * Snaps route control points to roads via Mapbox Directions (ported from iOS [RouteRoadSnapper]).
 */
class RouteRoadSnapper(
    private val accessToken: String,
    private val httpClient: OkHttpClient = defaultHttpClient,
) {
    data class Result(
        val coordinates: List<RouteLatLng>,
        val distanceMeters: Double,
        val travelTimeSeconds: Double,
        val didSnapToRoad: Boolean,
        val turnManeuverCoordinates: List<RouteLatLng>,
    )

    suspend fun buildRoute(points: List<RouteLatLng>): Result =
        withContext(Dispatchers.IO) {
            buildRouteBlocking(points)
        }

    private fun buildRouteBlocking(points: List<RouteLatLng>): Result {
        if (points.size < 2) {
            return Result(
                coordinates = points,
                distanceMeters = 0.0,
                travelTimeSeconds = 0.0,
                didSnapToRoad = false,
                turnManeuverCoordinates = emptyList(),
            )
        }

        var allCoordinates = mutableListOf<RouteLatLng>()
        val allTurnCoordinates = mutableListOf<RouteLatLng>()
        var totalMeters = 0.0
        var totalSeconds = 0.0
        var didSnapAllSegments = true

        for (index in 0 until points.size - 1) {
            val segment = routeSegment(points[index], points[index + 1])
            if (allCoordinates.isEmpty()) {
                allCoordinates.addAll(segment.coordinates)
            } else {
                allCoordinates.addAll(segment.coordinates.drop(1))
            }
            allTurnCoordinates.addAll(segment.turnManeuverCoordinates)
            totalMeters += segment.distanceMeters
            totalSeconds += segment.travelTimeSeconds
            didSnapAllSegments = didSnapAllSegments && segment.didSnapToRoad
        }

        if (allCoordinates.isEmpty()) {
            allCoordinates = points.toMutableList()
        }

        return Result(
            coordinates = allCoordinates,
            distanceMeters = totalMeters,
            travelTimeSeconds = totalSeconds,
            didSnapToRoad = didSnapAllSegments,
            turnManeuverCoordinates = dedupeNearbyCoordinates(allTurnCoordinates, withinMeters = 40.0),
        )
    }

    private fun routeSegment(start: RouteLatLng, end: RouteLatLng): Result {
        if (!isValidCoordinate(start) || !isValidCoordinate(end)) {
            return fallbackSegment(start, end)
        }

        val coordPath = "${start.lng},${start.lat};${end.lng},${end.lat}"
        val url =
            "https://api.mapbox.com/directions/v5/mapbox/driving/$coordPath" +
                "?geometries=geojson&overview=full&steps=true&access_token=${accessToken.trim()}"

        return try {
            val request =
                Request.Builder()
                    .url(url)
                    .header("Accept", "application/json")
                    .get()
                    .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return fallbackSegment(start, end)
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) return fallbackSegment(start, end)
                parseDirectionsResponse(body, start, end)
            }
        } catch (_: Exception) {
            fallbackSegment(start, end)
        }
    }

    private fun parseDirectionsResponse(
        body: String,
        start: RouteLatLng,
        end: RouteLatLng,
    ): Result {
        val root = JsonParser.parseString(body).asJsonObject
        val routes = root.getAsJsonArray("routes") ?: return fallbackSegment(start, end)
        if (routes.size() == 0) return fallbackSegment(start, end)

        val route = routes[0].asJsonObject
        val distanceMeters = route.get("distance")?.asDouble ?: 0.0
        val durationSeconds = route.get("duration")?.asDouble ?: 0.0
        val geometry = route.getAsJsonObject("geometry")
        val coordinates = parseGeoJsonCoordinates(geometry?.getAsJsonArray("coordinates"))
        val snappedCoordinates = if (coordinates.isEmpty()) listOf(start, end) else coordinates

        val turnCoordinates = mutableListOf<RouteLatLng>()
        val legs = route.getAsJsonArray("legs")
        if (legs != null) {
            for (legIndex in 0 until legs.size()) {
                val steps = legs[legIndex].asJsonObject.getAsJsonArray("steps") ?: continue
                turnCoordinates.addAll(turnManeuverCoordinates(steps))
            }
        }

        return Result(
            coordinates = snappedCoordinates,
            distanceMeters = distanceMeters,
            travelTimeSeconds = durationSeconds,
            didSnapToRoad = snappedCoordinates.isNotEmpty(),
            turnManeuverCoordinates = turnCoordinates,
        )
    }

    private fun turnManeuverCoordinates(steps: JsonArray): List<RouteLatLng> {
        val coordinates = mutableListOf<RouteLatLng>()
        for (index in 0 until steps.size()) {
            if (index == 0) continue
            val step = steps[index].asJsonObject
            val distance = step.get("distance")?.asDouble ?: 0.0
            if (distance < MINIMUM_TURN_STEP_METERS) continue

            val instructions =
                step.getAsJsonObject("maneuver")
                    ?.get("instruction")
                    ?.asString
                    ?.lowercase()
                    .orEmpty()
            if (instructions.contains("continue") && !instructions.contains("turn")) {
                continue
            }

            val stepCoords =
                parseGeoJsonCoordinates(
                    step.getAsJsonObject("geometry")?.getAsJsonArray("coordinates"),
                )
            val first = stepCoords.firstOrNull() ?: continue
            if (!isValidCoordinate(first)) continue
            coordinates.add(first)
        }
        return coordinates
    }

    private fun parseGeoJsonCoordinates(array: JsonArray?): List<RouteLatLng> {
        if (array == null || array.size() == 0) return emptyList()
        val coordinates = ArrayList<RouteLatLng>(array.size())
        for (index in 0 until array.size()) {
            val pair = array[index].asJsonArray
            if (pair.size() < 2) continue
            val lng = pair[0].asDouble
            val lat = pair[1].asDouble
            coordinates.add(lat to lng)
        }
        return coordinates
    }

    private fun fallbackSegment(start: RouteLatLng, end: RouteLatLng): Result =
        Result(
            coordinates = listOf(start, end),
            distanceMeters = 0.0,
            travelTimeSeconds = 0.0,
            didSnapToRoad = false,
            turnManeuverCoordinates = emptyList(),
        )

    private fun dedupeNearbyCoordinates(
        coordinates: List<RouteLatLng>,
        withinMeters: Double,
    ): List<RouteLatLng> {
        val deduped = mutableListOf<RouteLatLng>()
        for (coordinate in coordinates) {
            val isDuplicate =
                deduped.any { existing ->
                    haversineMeters(
                        coordinate.lat,
                        coordinate.lng,
                        existing.lat,
                        existing.lng,
                    ) <= withinMeters
                }
            if (!isDuplicate) {
                deduped.add(coordinate)
            }
        }
        return deduped
    }

    private fun isValidCoordinate(coordinate: RouteLatLng): Boolean {
        val lat = coordinate.lat
        val lng = coordinate.lng
        return lat.isFinite() && lng.isFinite() && lat in -90.0..90.0 && lng in -180.0..180.0
    }

    private companion object {
        private const val MINIMUM_TURN_STEP_METERS = 25.0

        private val defaultHttpClient: OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
    }
}
