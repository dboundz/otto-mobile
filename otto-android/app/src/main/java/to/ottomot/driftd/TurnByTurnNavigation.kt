package to.ottomot.driftd

import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import to.ottomot.driftd.core.network.dto.SavedRouteDto
import to.ottomot.driftd.routebuilder.engine.RouteLatLng
import to.ottomot.driftd.routebuilder.engine.RoutePolylineGeometry

enum class TurnByTurnGuidancePhase {
    LOADING,
    NAVIGATING,
    OFF_ROUTE,
    ARRIVED,
    FAILED,
}

data class NavigationManeuver(
    val type: String,
    val modifier: String?,
    val instruction: String,
)

data class TurnByTurnGuidanceStep(
    val index: Int,
    val instruction: String,
    val roadName: String?,
    val distanceMeters: Double,
    val durationSeconds: Double,
    val maneuver: NavigationManeuver,
    val coordinate: RouteLatLng,
    val distanceToManeuverMeters: Double,
)

data class TurnByTurnGuidanceState(
    val phase: TurnByTurnGuidancePhase,
    val nextInstruction: String,
    val nextManeuver: NavigationManeuver?,
    val distanceToManeuverMeters: Double,
    val currentRoadName: String?,
    val remainingDistanceMeters: Double,
    val remainingDurationSeconds: Double,
    val currentStepIndex: Int,
    val totalSteps: Int,
    val currentGuidanceStep: TurnByTurnGuidanceStep? = null,
    val upcomingGuidanceStep: TurnByTurnGuidanceStep? = null,
    val failureMessage: String? = null,
)

data class NavigationVoiceInstruction(
    val distanceAlongStepMeters: Double,
    val announcement: String,
)

data class NavigationStep(
    val instruction: String,
    val name: String?,
    val distanceMeters: Double,
    val durationSeconds: Double,
    val maneuver: NavigationManeuver,
    val maneuverCoordinate: RouteLatLng,
    val voiceInstructions: List<NavigationVoiceInstruction>,
    val geometryCoordinates: List<RouteLatLng>,
    val maneuverArcLengthMeters: Double,
)

data class NavigationRoute(
    val coordinates: List<RouteLatLng>,
    val steps: List<NavigationStep>,
    val totalDistanceMeters: Double,
    val totalDurationSeconds: Double,
    val finishCoordinate: RouteLatLng?,
)

class TurnByTurnRouteService(
    private val mapboxAccessToken: String,
) {
    suspend fun fetchRoute(waypoints: List<RouteLatLng>): NavigationRoute =
        withContext(Dispatchers.IO) {
            if (waypoints.size < 2) error("Not enough waypoints")
            val token = mapboxAccessToken.trim()
            if (token.isEmpty()) error("Missing Mapbox token")
            val coordinatePath =
                waypoints.joinToString(";") { coordinate ->
                    "${coordinate.second.formatCoordinate()},${coordinate.first.formatCoordinate()}"
                }
            val query =
                listOf(
                    "geometries=geojson",
                    "overview=full",
                    "steps=true",
                    "voice_instructions=true",
                    "banner_instructions=true",
                    "voice_units=imperial",
                    "annotations=distance,duration",
                    "access_token=${URLEncoder.encode(token, "UTF-8")}",
                ).joinToString("&")
            val url = URL("https://api.mapbox.com/directions/v5/mapbox/driving/$coordinatePath?$query")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
                connectTimeout = 10_000
                readTimeout = 15_000
            }
            try {
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val body = stream.bufferedReader().use { it.readText() }
                if (code !in 200..299) error("Directions HTTP $code")
                parseResponse(body, waypoints.last())
            } finally {
                connection.disconnect()
            }
        }

    fun parseResponse(body: String, fallbackFinish: RouteLatLng): NavigationRoute {
        val root = JsonParser.parseString(body).asJsonObject
        val route =
            root.getAsJsonArray("routes")
                ?.firstOrNull()
                ?.asJsonObject
                ?: error("No route")
        val totalDistance = route.double("distance") ?: 0.0
        val totalDuration = route.double("duration") ?: 0.0
        val coordinates = route.obj("geometry")?.array("coordinates").parseCoordinates()
        if (coordinates.size < 2) error("Invalid route geometry")
        val steps =
            route.array("legs")
                .flatMap { leg ->
                    leg.asJsonObject.array("steps").mapNotNull { step ->
                        parseStep(step.asJsonObject, coordinates)
                    }
                }
        Log.d(
            AndroidAutoNavLogTag,
            "Mapbox route received distanceMeters=$totalDistance durationSeconds=$totalDuration " +
                "coordinateCount=${coordinates.size} stepCount=${steps.size}",
        )
        if (steps.isEmpty()) {
            Log.w(AndroidAutoNavLogTag, "Mapbox route returned zero route steps")
        }
        if (steps.all { it.maneuver.type.lowercase() in setOf("depart", "arrive") }) {
            Log.w(AndroidAutoNavLogTag, "Mapbox route has only start/arrive steps")
        }
        steps.forEachIndexed { index, step ->
            logRawRouteStep(index, step)
        }
        return NavigationRoute(
            coordinates = coordinates,
            steps = steps,
            totalDistanceMeters = totalDistance,
            totalDurationSeconds = totalDuration,
            finishCoordinate = coordinates.lastOrNull() ?: fallbackFinish,
        )
    }

    private fun parseStep(step: JsonObject, routeCoordinates: List<RouteLatLng>): NavigationStep? {
        val maneuver = step.obj("maneuver") ?: JsonObject()
        val geometryCoordinates = step.obj("geometry")?.array("coordinates").parseCoordinates()
        val maneuverCoordinate =
            maneuver.array("location")?.let { location ->
                if (location.size() >= 2) {
                    location[1].asDouble to location[0].asDouble
                } else {
                    null
                }
            } ?: geometryCoordinates.firstOrNull() ?: return null
        val instruction = maneuver.string("instruction")?.plainNavigationText() ?: "Continue"
        val maneuverArcLength =
            RoutePolylineGeometry.projectOntoPolyline(maneuverCoordinate, routeCoordinates)
                ?.arcLengthMeters
                ?: 0.0
        val voiceInstructions =
            step.array("voiceInstructions").mapNotNull { item ->
                val obj = item.asJsonObject
                val announcement = obj.string("announcement")?.plainNavigationText() ?: return@mapNotNull null
                NavigationVoiceInstruction(
                    distanceAlongStepMeters = obj.double("distanceAlongGeometry") ?: 0.0,
                    announcement = announcement,
                )
            }
        return NavigationStep(
            instruction = instruction,
            name = step.string("name")?.takeIf { it.isNotBlank() },
            distanceMeters = step.double("distance") ?: 0.0,
            durationSeconds = step.double("duration") ?: 0.0,
            maneuver =
                NavigationManeuver(
                    type = maneuver.string("type") ?: "turn",
                    modifier = maneuver.string("modifier"),
                    instruction = instruction,
                ),
            maneuverCoordinate = maneuverCoordinate,
            voiceInstructions = voiceInstructions,
            geometryCoordinates = geometryCoordinates,
            maneuverArcLengthMeters = maneuverArcLength,
        )
    }
}

class TurnByTurnNavigationManager(
    private val scope: CoroutineScope,
    private val routeService: TurnByTurnRouteService,
    private val onVoiceAnnouncement: (String) -> Unit = {},
    private val onStateChange: (TurnByTurnGuidanceState?, List<RouteLatLng>?) -> Unit,
) {
    private var activeRoute: SavedRouteDto? = null
    private var navigationRoute: NavigationRoute? = null
    private var completedWaypointIndexes: Set<Int> = emptySet()
    private var currentStepIndex = 0
    private var routeProgressMeters: Double? = null
    private var fetchJob: Job? = null
    private var lastGuidanceStepIndex: Int? = null
    private val voiceAnnouncer = TurnByTurnVoiceAnnouncer(onVoiceAnnouncement)

    fun start(
        route: SavedRouteDto,
        location: RouteDriveLocationSample,
        completedIndexes: Set<Int> = emptySet(),
    ) {
        activeRoute = route
        completedWaypointIndexes = completedIndexes
        navigationRoute = null
        currentStepIndex = 0
        routeProgressMeters = null
        lastGuidanceStepIndex = null
        voiceAnnouncer.reset()
        Log.d(AndroidAutoNavLogTag, "Navigation started")
        Log.d(AndroidAutoNavLogTag, "Route id: ${route.id}")
        Log.d(AndroidAutoNavLogTag, "Is navigating: true")
        logAndroidAutoNavigationState(
            reason = "navigation starts",
            isNavigating = true,
            routeId = route.id,
            rawRouteStepCount = 0,
            androidAutoStepCount = 0,
            currentStepIndex = currentStepIndex,
            currentInstruction = null,
            currentManeuverType = null,
            currentManeuverModifier = null,
            distanceToNextManeuverMeters = 0.0,
            remainingDistanceMeters = 0.0,
            remainingTimeSeconds = 0.0,
            arrivalTimeMillis = null,
            travelEstimatePresent = false,
            tripPresent = false,
        )
        publish(
            TurnByTurnGuidanceState(
                phase = TurnByTurnGuidancePhase.LOADING,
                nextInstruction = "Loading route",
                nextManeuver = null,
                distanceToManeuverMeters = 0.0,
                currentRoadName = null,
                remainingDistanceMeters = 0.0,
                remainingDurationSeconds = 0.0,
                currentStepIndex = 0,
                totalSteps = 0,
            ),
            null,
        )
        fetchJob?.cancel()
        fetchJob =
            scope.launch {
                runCatching {
                    routeService.fetchRoute(waypointsForRoute(route, location, completedIndexes))
                }.onSuccess { fetched ->
                    navigationRoute = fetched
                    currentStepIndex = 0
                    routeProgressMeters = null
                    Log.d(AndroidAutoNavLogTag, "Route step count: ${fetched.steps.size}")
                    logAndroidAutoNavigationState(
                        reason = "route data received",
                        isNavigating = true,
                        routeId = route.id,
                        rawRouteStepCount = fetched.steps.size,
                        androidAutoStepCount = 0,
                        currentStepIndex = currentStepIndex,
                        currentInstruction = fetched.steps.firstOrNull()?.instruction,
                        currentManeuverType = fetched.steps.firstOrNull()?.maneuver?.type,
                        currentManeuverModifier = fetched.steps.firstOrNull()?.maneuver?.modifier,
                        distanceToNextManeuverMeters = null,
                        remainingDistanceMeters = fetched.totalDistanceMeters,
                        remainingTimeSeconds = fetched.totalDurationSeconds,
                        arrivalTimeMillis = null,
                        travelEstimatePresent = false,
                        tripPresent = false,
                    )
                    publish(makeGuidanceState(fetched, 0.0, TurnByTurnGuidancePhase.NAVIGATING, location), fetched.coordinates)
                }.onFailure { error ->
                    Log.w(AndroidAutoNavLogTag, "Mapbox route fetch failed: ${error.message}", error)
                    publish(
                        TurnByTurnGuidanceState(
                            phase = TurnByTurnGuidancePhase.FAILED,
                            nextInstruction = error.message ?: "Could not load route",
                            nextManeuver = null,
                            distanceToManeuverMeters = 0.0,
                            currentRoadName = null,
                            remainingDistanceMeters = 0.0,
                            remainingDurationSeconds = 0.0,
                            currentStepIndex = 0,
                            totalSteps = 0,
                            failureMessage = error.message,
                        ),
                        null,
                    )
                }
            }
    }

    fun updateCompletedWaypointIndexes(indexes: Set<Int>) {
        completedWaypointIndexes = indexes
    }

    fun update(location: RouteDriveLocationSample, speedMps: Double) {
        val route = navigationRoute ?: return
        val projection =
            RoutePolylineGeometry.projectOntoPolyline(
                coordinate = location.latitude to location.longitude,
                lineCoordinates = route.coordinates,
                preferredArcLength = routeProgressMeters,
                searchWindowMeters = 350.0,
            ) ?: RoutePolylineGeometry.projectOntoPolyline(location.latitude to location.longitude, route.coordinates)
        val lateralDistance = projection?.distanceMeters ?: Double.MAX_VALUE
        val phase =
            if (lateralDistance > 80.0) {
                TurnByTurnGuidancePhase.OFF_ROUTE
            } else {
                TurnByTurnGuidancePhase.NAVIGATING
            }
        if (projection != null && phase == TurnByTurnGuidancePhase.NAVIGATING) {
            routeProgressMeters = maxOf(routeProgressMeters ?: 0.0, projection.arcLengthMeters)
            advanceStepIfNeeded(routeProgressMeters ?: 0.0)
        }
        val progress = routeProgressMeters ?: projection?.arcLengthMeters ?: 0.0
        val guidance = makeGuidanceState(route, progress, phase, location, speedMps)
        voiceAnnouncer.handleUpdate(
            route = route,
            stepIndex = guidance.currentGuidanceStep?.index ?: currentStepIndex,
            progressMeters = progress,
            phase = guidance.phase,
            speedMps = speedMps,
        )
        logAndroidAutoNavigationState(
            reason = "location update",
            isNavigating = phase != TurnByTurnGuidancePhase.FAILED,
            routeId = activeRoute?.id,
            rawRouteStepCount = route.steps.size,
            androidAutoStepCount = guidance.androidAutoStepCandidateCount(),
            currentStepIndex = guidance.currentStepIndex,
            currentInstruction = guidance.currentGuidanceStep?.instruction ?: guidance.nextInstruction,
            currentManeuverType = guidance.currentGuidanceStep?.maneuver?.type ?: guidance.nextManeuver?.type,
            currentManeuverModifier = guidance.currentGuidanceStep?.maneuver?.modifier ?: guidance.nextManeuver?.modifier,
            distanceToNextManeuverMeters = guidance.distanceToManeuverMeters,
            remainingDistanceMeters = guidance.remainingDistanceMeters,
            remainingTimeSeconds = guidance.remainingDurationSeconds,
            arrivalTimeMillis = null,
            travelEstimatePresent = guidance.phase !in setOf(TurnByTurnGuidancePhase.LOADING, TurnByTurnGuidancePhase.FAILED),
            tripPresent = false,
        )
        publish(guidance, route.coordinates)
    }

    fun stop() {
        fetchJob?.cancel()
        fetchJob = null
        activeRoute = null
        navigationRoute = null
        completedWaypointIndexes = emptySet()
        currentStepIndex = 0
        routeProgressMeters = null
        lastGuidanceStepIndex = null
        voiceAnnouncer.reset()
        logAndroidAutoNavigationState(
            reason = "navigation stopped",
            isNavigating = false,
            routeId = null,
            rawRouteStepCount = 0,
            androidAutoStepCount = 0,
            currentStepIndex = 0,
            currentInstruction = null,
            currentManeuverType = null,
            currentManeuverModifier = null,
            distanceToNextManeuverMeters = null,
            remainingDistanceMeters = null,
            remainingTimeSeconds = null,
            arrivalTimeMillis = null,
            travelEstimatePresent = false,
            tripPresent = false,
        )
        publish(null, null)
    }

    private fun makeGuidanceState(
        route: NavigationRoute,
        progressMeters: Double,
        phase: TurnByTurnGuidancePhase,
        location: RouteDriveLocationSample,
        speedMps: Double = 0.0,
    ): TurnByTurnGuidanceState {
        val totalDistance = maxOf(route.totalDistanceMeters, 1.0)
        val remainingDistance = maxOf(0.0, totalDistance - progressMeters)
        val baseDuration = route.totalDurationSeconds * (1.0 - (progressMeters / totalDistance).coerceIn(0.0, 1.0))
        val remainingDuration =
            if (speedMps > 1.5 && remainingDistance > 0.0) {
                minOf(baseDuration, (remainingDistance / speedMps) * 1.15)
            } else {
                baseDuration
            }
        val selection = guidanceSelection(route, progressMeters)
        val distanceToManeuver =
            selection?.let { maxOf(0.0, it.value.maneuverArcLengthMeters - progressMeters) }
                ?: remainingDistance
        val currentGuidanceStep =
            selection?.let { (index, step) ->
                step.toGuidanceStep(index, progressMeters)
            }
        val upcomingGuidanceStep =
            selection
                ?.let { upcomingStep(route, it.index + 1, progressMeters) }
        val selectedIndex = currentGuidanceStep?.index
        if (selectedIndex != null && selectedIndex != lastGuidanceStepIndex) {
            lastGuidanceStepIndex = selectedIndex
            Log.d(
                AndroidAutoNavLogTag,
                "Current step changed index=$selectedIndex instruction=${currentGuidanceStep.instruction} " +
                    "maneuver=${currentGuidanceStep.maneuver.type}/${currentGuidanceStep.maneuver.modifier}",
            )
            logAndroidAutoNavigationState(
                reason = "current step changes",
                isNavigating = phase != TurnByTurnGuidancePhase.FAILED,
                routeId = activeRoute?.id,
                rawRouteStepCount = route.steps.size,
                androidAutoStepCount = listOfNotNull(currentGuidanceStep, upcomingGuidanceStep).size,
                currentStepIndex = currentStepIndex,
                currentInstruction = currentGuidanceStep.instruction,
                currentManeuverType = currentGuidanceStep.maneuver.type,
                currentManeuverModifier = currentGuidanceStep.maneuver.modifier,
                distanceToNextManeuverMeters = currentGuidanceStep.distanceToManeuverMeters,
                remainingDistanceMeters = remainingDistance,
                remainingTimeSeconds = remainingDuration,
                arrivalTimeMillis = null,
                travelEstimatePresent = true,
                tripPresent = false,
            )
        }
        return TurnByTurnGuidanceState(
            phase = phase,
            nextInstruction = currentGuidanceStep?.instruction ?: currentRoadStep(route)?.instruction ?: "Continue",
            nextManeuver = currentGuidanceStep?.maneuver,
            distanceToManeuverMeters = distanceToManeuver,
            currentRoadName = currentRoadStep(route)?.name,
            remainingDistanceMeters = remainingDistance,
            remainingDurationSeconds = remainingDuration,
            currentStepIndex = currentStepIndex,
            totalSteps = route.steps.size,
            currentGuidanceStep = currentGuidanceStep,
            upcomingGuidanceStep = upcomingGuidanceStep,
        )
    }

    private fun guidanceSelection(route: NavigationRoute, progressMeters: Double): IndexedValue<NavigationStep>? {
        if (route.steps.isEmpty()) return null
        val startIndex = minOf(currentStepIndex + 1, route.steps.lastIndex)
        return (startIndex..route.steps.lastIndex)
            .map { IndexedValue(it, route.steps[it]) }
            .firstOrNull { (_, step) ->
                val type = step.maneuver.type.lowercase()
                !(type == "arrive" && progressMeters < step.maneuverArcLengthMeters - 120.0)
            }
    }

    private fun upcomingStep(
        route: NavigationRoute,
        startIndex: Int,
        progressMeters: Double,
    ): TurnByTurnGuidanceStep? {
        if (route.steps.isEmpty() || startIndex > route.steps.lastIndex) return null
        return (startIndex..route.steps.lastIndex)
            .map { IndexedValue(it, route.steps[it]) }
            .firstOrNull { (_, step) ->
                val type = step.maneuver.type.lowercase()
                !(type == "arrive" && progressMeters < step.maneuverArcLengthMeters - 120.0)
            }
            ?.let { (index, step) -> step.toGuidanceStep(index, progressMeters) }
    }

    private fun currentRoadStep(route: NavigationRoute): NavigationStep? =
        route.steps.getOrNull(currentStepIndex.coerceIn(0, maxOf(route.steps.lastIndex, 0)))

    private fun advanceStepIfNeeded(progressMeters: Double) {
        val route = navigationRoute ?: return
        while (currentStepIndex < route.steps.lastIndex) {
            val nextStep = route.steps[currentStepIndex + 1]
            if (progressMeters >= nextStep.maneuverArcLengthMeters - 10.0) {
                currentStepIndex += 1
            } else {
                break
            }
        }
    }

    private fun waypointsForRoute(
        route: SavedRouteDto,
        location: RouteDriveLocationSample,
        completedIndexes: Set<Int>,
    ): List<RouteLatLng> {
        val result = mutableListOf<RouteLatLng>()
        result.add(location.latitude to location.longitude)
        route.points.orEmpty().forEachIndexed { index, point ->
            val type = point.markerType?.lowercase().orEmpty()
            if ((type == "stop" || type == "finish") &&
                !completedIndexes.contains(index) &&
                point.lat.isFinite() &&
                point.lng.isFinite()
            ) {
                result.add(point.lat to point.lng)
            }
        }
        if (result.size < 2) {
            route.points.orEmpty()
                .lastOrNull { it.lat.isFinite() && it.lng.isFinite() }
                ?.let { result.add(it.lat to it.lng) }
        }
        return result.distinctBy { "${it.first.formatCoordinate()},${it.second.formatCoordinate()}" }
    }

    private fun publish(state: TurnByTurnGuidanceState?, lineCoordinates: List<RouteLatLng>?) {
        if (state != null) {
            Log.d(
                AndroidAutoNavLogTag,
                "After Mapbox route to internal steps conversion phase=${state.phase} " +
                    "routeSteps=${state.totalSteps} currentStep=${state.currentGuidanceStep?.index} " +
                    "upcomingStep=${state.upcomingGuidanceStep?.index} instruction=${state.nextInstruction}",
            )
            logAndroidAutoNavigationState(
                reason = "After Mapbox route to internal steps conversion",
                isNavigating = state.phase != TurnByTurnGuidancePhase.FAILED,
                routeId = activeRoute?.id,
                rawRouteStepCount = state.totalSteps,
                androidAutoStepCount = state.androidAutoStepCandidateCount(),
                currentStepIndex = state.currentStepIndex,
                currentInstruction = state.currentGuidanceStep?.instruction ?: state.nextInstruction,
                currentManeuverType = state.currentGuidanceStep?.maneuver?.type ?: state.nextManeuver?.type,
                currentManeuverModifier = state.currentGuidanceStep?.maneuver?.modifier ?: state.nextManeuver?.modifier,
                distanceToNextManeuverMeters = state.distanceToManeuverMeters,
                remainingDistanceMeters = state.remainingDistanceMeters,
                remainingTimeSeconds = state.remainingDurationSeconds,
                arrivalTimeMillis =
                    System.currentTimeMillis() +
                        state.remainingDurationSeconds.coerceAtLeast(0.0).toLong() * 1_000L,
                travelEstimatePresent = state.phase !in setOf(TurnByTurnGuidancePhase.LOADING, TurnByTurnGuidancePhase.FAILED),
                tripPresent = false,
            )
        }
        onStateChange(state, lineCoordinates)
    }
}

private class TurnByTurnVoiceAnnouncer(
    private val speak: (String) -> Unit,
) {
    private val spokenKeys = mutableSetOf<String>()
    private val spokenTexts = mutableSetOf<String>()
    private var lastSpokenAtMs = 0L

    fun reset() {
        spokenKeys.clear()
        spokenTexts.clear()
        lastSpokenAtMs = 0L
    }

    fun handleUpdate(
        route: NavigationRoute,
        stepIndex: Int,
        progressMeters: Double,
        phase: TurnByTurnGuidancePhase,
        speedMps: Double,
    ) {
        if (phase != TurnByTurnGuidancePhase.NAVIGATING) return
        val step = route.steps.getOrNull(stepIndex) ?: return
        val distanceToManeuver = (step.maneuverArcLengthMeters - progressMeters).coerceAtLeast(0.0)
        val announcement =
            voiceInstructionAnnouncement(step, stepIndex, distanceToManeuver)
                ?: fallbackAnnouncement(step, stepIndex, distanceToManeuver, speedMps)
                ?: return
        announce(announcement.key, announcement.text)
    }

    private fun voiceInstructionAnnouncement(
        step: NavigationStep,
        stepIndex: Int,
        distanceToManeuver: Double,
    ): VoiceAnnouncement? =
        step.voiceInstructions
            .withIndex()
            .filter { (_, instruction) -> distanceToManeuver <= instruction.distanceAlongStepMeters + 8.0 }
            .minByOrNull { (_, instruction) -> instruction.distanceAlongStepMeters }
            ?.let { (instructionIndex, instruction) ->
                VoiceAnnouncement(
                    key = "$stepIndex-mapbox-$instructionIndex",
                    text = instruction.announcement,
                )
            }

    private fun fallbackAnnouncement(
        step: NavigationStep,
        stepIndex: Int,
        distanceToManeuver: Double,
        speedMps: Double,
    ): VoiceAnnouncement? {
        val threshold =
            when {
                distanceToManeuver <= 18.0 -> "now"
                speedMps >= 2.5 && distanceToManeuver / speedMps <= 6.0 -> "speed-close"
                distanceToManeuver <= 65.0 -> "two-hundred-feet"
                speedMps >= 2.5 && distanceToManeuver / speedMps <= if (speedMps >= 15.0) 15.0 else 12.0 -> "speed-lead"
                distanceToManeuver <= 325.0 -> "two-tenths-mile"
                distanceToManeuver <= 810.0 -> "half-mile"
                else -> return null
            }
        val text =
            if (threshold == "now") {
                step.instruction.ifBlank { "Continue" }
            } else {
                "In ${TurnByTurnDistanceFormatter(distanceToManeuver)}, ${step.instruction.ifBlank { "continue" }}"
            }
        return VoiceAnnouncement(key = "$stepIndex-fallback-$threshold", text = text)
    }

    private fun announce(
        key: String,
        text: String,
    ) {
        val normalizedText = text.lowercase().replace(Regex("\\s+"), " ").trim()
        if (key in spokenKeys || normalizedText in spokenTexts) return
        val now = System.currentTimeMillis()
        if (now - lastSpokenAtMs < 3_500L) return
        spokenKeys.add(key)
        spokenTexts.add(normalizedText)
        lastSpokenAtMs = now
        speak(text)
    }

    private data class VoiceAnnouncement(
        val key: String,
        val text: String,
    )
}

private fun NavigationStep.toGuidanceStep(
    index: Int,
    progressMeters: Double,
): TurnByTurnGuidanceStep =
    TurnByTurnGuidanceStep(
        index = index,
        instruction = instruction.ifBlank { "Continue" },
        roadName = name,
        distanceMeters = distanceMeters,
        durationSeconds = durationSeconds,
        maneuver = maneuver,
        coordinate = maneuverCoordinate,
        distanceToManeuverMeters = maxOf(0.0, maneuverArcLengthMeters - progressMeters),
    )

private fun TurnByTurnGuidanceState.androidAutoStepCandidateCount(): Int =
    listOfNotNull(currentGuidanceStep, upcomingGuidanceStep).size

fun TurnByTurnDistanceFormatter(meters: Double): String {
    val clamped = meters.coerceAtLeast(0.0)
    val feet = clamped * 3.28084
    return when {
        feet < 950.0 -> "${maxOf(50, (feet / 50.0).toInt() * 50)} ft"
        clamped < 16_093.4 -> String.format("%.1f mi", clamped / 1609.344)
        else -> "${(clamped / 1609.344).toInt()} mi"
    }
}

private fun Double.formatCoordinate(): String = String.format(java.util.Locale.US, "%.6f", this)

private fun JsonObject.string(name: String): String? =
    get(name)?.takeUnless { it.isJsonNull }?.asString

private fun JsonObject.double(name: String): Double? =
    get(name)?.takeUnless { it.isJsonNull }?.asDouble

private fun JsonObject.obj(name: String): JsonObject? =
    get(name)?.takeUnless { it.isJsonNull }?.asJsonObject

private fun JsonObject.array(name: String): JsonArray =
    get(name)?.takeUnless { it.isJsonNull }?.asJsonArray ?: JsonArray()

private fun JsonArray?.parseCoordinates(): List<RouteLatLng> =
    (this ?: JsonArray()).mapNotNull { item ->
        val pair = item.asJsonArray
        if (pair.size() < 2) return@mapNotNull null
        val lng = pair[0].asDouble
        val lat = pair[1].asDouble
        if (lat.isFinite() && lng.isFinite()) lat to lng else null
    }

private fun String.plainNavigationText(): String =
    replace(Regex("<[^>]+>"), "")
        .replace("&amp;", "&")
        .replace("&nbsp;", " ")
        .trim()

internal const val AndroidAutoNavLogTag = "AndroidAutoNav"

internal fun logAndroidAutoNavigationState(
    reason: String,
    isNavigating: Boolean,
    routeId: String?,
    rawRouteStepCount: Int,
    androidAutoStepCount: Int,
    currentStepIndex: Int,
    currentInstruction: String?,
    currentManeuverType: String?,
    currentManeuverModifier: String?,
    distanceToNextManeuverMeters: Double?,
    remainingDistanceMeters: Double?,
    remainingTimeSeconds: Double?,
    arrivalTimeMillis: Long?,
    travelEstimatePresent: Boolean,
    tripPresent: Boolean,
) {
    Log.d(
        AndroidAutoNavLogTag,
        """
        ===== Android Auto Navigation State: $reason =====
        Navigation active: $isNavigating
        Route id: $routeId
        Raw route step count: $rawRouteStepCount
        Android Auto step count: $androidAutoStepCount
        Current step index: $currentStepIndex
        Current instruction: $currentInstruction
        Current maneuver type: $currentManeuverType
        Current maneuver modifier: $currentManeuverModifier
        Distance to next maneuver: $distanceToNextManeuverMeters
        Remaining distance meters: $remainingDistanceMeters
        Remaining time seconds: $remainingTimeSeconds
        Arrival time millis: $arrivalTimeMillis
        TravelEstimate present: $travelEstimatePresent
        Trip present: $tripPresent
        ================================================
        """.trimIndent(),
    )
}

private fun logRawRouteStep(
    index: Int,
    step: NavigationStep,
) {
    Log.d(
        AndroidAutoNavLogTag,
        """
        Raw route step index: $index
        Instruction: ${step.instruction}
        Distance meters: ${step.distanceMeters}
        Duration seconds: ${step.durationSeconds}
        Maneuver type: ${step.maneuver.type}
        Maneuver modifier: ${step.maneuver.modifier}
        Coordinate: ${step.maneuverCoordinate}
        """.trimIndent(),
    )
    if (step.instruction.isBlank()) {
        Log.w(AndroidAutoNavLogTag, "Raw route step $index has empty instruction")
    }
    if (step.distanceMeters <= 0.0) {
        Log.w(AndroidAutoNavLogTag, "Raw route step $index has non-positive distance=${step.distanceMeters}")
    }
    if (step.maneuver.type.isBlank()) {
        Log.w(AndroidAutoNavLogTag, "Raw route step $index has blank maneuver type")
    }
}
