package to.ottomot.driftd.car

import android.content.Context
import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.ScreenManager
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.model.DateTimeWithZone
import androidx.car.app.model.Distance
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.SearchTemplate
import androidx.car.app.model.Template
import androidx.car.app.navigation.NavigationManager
import androidx.car.app.navigation.NavigationManagerCallback
import androidx.car.app.navigation.model.Destination
import androidx.car.app.navigation.model.Maneuver
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.car.app.navigation.model.RoutingInfo
import androidx.car.app.navigation.model.Step
import androidx.car.app.navigation.model.TravelEstimate
import androidx.car.app.navigation.model.Trip
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.mapbox.maps.extension.androidauto.MapboxCarMap
import com.mapbox.maps.extension.androidauto.mapboxMapInstaller
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import to.ottomot.driftd.AndroidAutoNavLogTag
import to.ottomot.driftd.OttoShellUiState
import to.ottomot.driftd.OttoShellViewModel
import to.ottomot.driftd.R
import to.ottomot.driftd.DriveSessionKind
import to.ottomot.driftd.TurnByTurnGuidancePhase
import to.ottomot.driftd.TurnByTurnGuidanceStep
import to.ottomot.driftd.TurnByTurnGuidanceState
import to.ottomot.driftd.core.data.OttoDataRepository
import to.ottomot.driftd.core.network.dto.NavigationSearchResultDto
import to.ottomot.driftd.logAndroidAutoNavigationState
import java.util.TimeZone
import kotlin.math.abs
import org.json.JSONArray
import org.json.JSONObject

class OttoCarMapScreen(
    carContext: CarContext,
    private val mapboxCarMap: MapboxCarMap,
    private val viewModel: OttoShellViewModel,
    private val dataRepository: OttoDataRepository,
) : Screen(carContext) {
    private val navigationManager = carContext.getCarService(NavigationManager::class.java)
    private val screenScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mapObserver =
        OttoCarMapObserver(
            context = carContext.applicationContext,
            state = viewModel.state,
            visibleAreaProvider = { mapboxCarMap.visibleArea },
        )
    private var latestState: OttoShellUiState = viewModel.state.value
    private var projectedNavigationActive = false
    private var lastProjectedTripFingerprint: Int? = null
    private var lastNavigationSessionFingerprint: Int? = null

    init {
        Log.d("OttoCarMapObserver", "Android Auto map screen init")
        navigationManager.setNavigationManagerCallback(
            ContextCompat.getMainExecutor(carContext),
            object : NavigationManagerCallback {
                override fun onStopNavigation() {
                    viewModel.requestStopDriveSessionFromAndroidAuto()
                    endProjectedNavigationSession()
                }
            },
        )
        installMapObserver()
        ensureMapObserverStarted(reason = "init", requestInitialInvalidate = true)
        screenScope.launch {
            viewModel.state.collect { snapshot ->
                syncProjectedNavigationSession(snapshot)
                updateProjectedTrip(snapshot)
            }
        }
        lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    Log.d("OttoCarMapObserver", "Android Auto map screen start")
                    viewModel.setAndroidAutoMapActive(true)
                    ensureMapObserverStarted(reason = "screen-start", requestInitialInvalidate = false)
                    mapObserver.ensureAndroidAutoMapLoaded(reason = "screen-start")
                }

                override fun onStop(owner: LifecycleOwner) {
                    Log.d("OttoCarMapObserver", "Android Auto map screen stop")
                    viewModel.setAndroidAutoMapActive(false)
                }

                override fun onDestroy(owner: LifecycleOwner) {
                    Log.d("OttoCarMapObserver", "Android Auto map screen destroy")
                    viewModel.setAndroidAutoMapActive(false)
                    mapObserver.stop()
                    screenScope.cancel()
                    endProjectedNavigationSession()
                    navigationManager.clearNavigationManagerCallback()
                }
            },
        )
    }

    private fun installMapObserver() {
        mapboxMapInstaller(mapboxCarMap)
            .onCreated(mapObserver)
            .install()
        Log.d("OttoCarMapObserver", "Android Auto map observer installed")
    }

    private fun ensureMapObserverStarted(
        reason: String,
        requestInitialInvalidate: Boolean,
    ) {
        val started = mapObserver.start(this@OttoCarMapScreen::invalidate)
        Log.d(
            "OttoCarMapObserver",
            "Android Auto map observer ensure reason=$reason started=$started requestInitialInvalidate=$requestInitialInvalidate",
        )
        if (started && requestInitialInvalidate) {
            invalidate()
        }
    }

    override fun onGetTemplate(): Template {
        Log.d("AndroidAutoMap", "onGetTemplate called")
        latestState = viewModel.state.value
        ensureMapObserverStarted(reason = "template", requestInitialInvalidate = false)
        mapObserver.ensureAndroidAutoMapLoaded(reason = "template")
        Log.d(
            "OttoCarMapObserver",
            "Android Auto map template requested hasDrive=${latestState.hasActiveDriveSession} " +
                "routeStatus=${latestState.activeRouteDriveSession?.status} " +
                "driveKind=${latestState.activeDriveSession?.kind} hasRoute=${latestState.mapSelectedRoute != null} " +
                "guidance=${latestState.turnByTurnGuidance?.phase} hasFix=${latestState.deviceLocationFix != null}",
        )
        val routingInfo = routingInfoFor(latestState)
        val travelEstimate = travelEstimateFor(latestState)
        val tripBuild = buildProjectedTrip(latestState)
        val guidance = latestState.turnByTurnGuidance
        val currentGuidanceStep = guidance?.currentGuidanceStep
        val androidAutoStepCount = tripBuild?.stepCount ?: guidance?.androidAutoStepCount() ?: 0
        reassertNavigationSessionForTemplate(latestState, tripBuild)
        logAndroidAutoNavigationState(
            reason = "onGetTemplate",
            isNavigating = projectedNavigationActive || hasProjectedRouteDrive(latestState),
            routeId = latestState.activeRouteDriveSession?.activeRouteId ?: latestState.activeDriveSession?.routeId,
            rawRouteStepCount = guidance?.totalSteps ?: 0,
            androidAutoStepCount = androidAutoStepCount,
            currentStepIndex = guidance?.currentStepIndex ?: 0,
            currentInstruction =
                currentGuidanceStep?.instruction
                    ?: guidance?.nextInstruction,
            currentManeuverType =
                currentGuidanceStep?.maneuver?.type
                    ?: guidance?.nextManeuver?.type,
            currentManeuverModifier =
                currentGuidanceStep?.maneuver?.modifier
                    ?: guidance?.nextManeuver?.modifier,
            distanceToNextManeuverMeters = guidance?.distanceToManeuverMeters,
            remainingDistanceMeters = guidance?.remainingDistanceMeters,
            remainingTimeSeconds = guidance?.remainingDurationSeconds,
            arrivalTimeMillis = guidance?.arrivalTimeMillis(),
            travelEstimatePresent = travelEstimate != null,
            tripPresent = tripBuild != null,
        )
        val template =
            NavigationTemplate.Builder()
            .setActionStrip(
                ActionStrip.Builder()
                    .addAction(searchAction())
                    .build(),
            )
            .setMapActionStrip(
                ActionStrip.Builder()
                    .addAction(zoomInAction())
                    .addAction(zoomOutAction())
                    .addAction(reportHazardAction())
                    .build(),
            )
            .apply {
                routingInfo?.let { setNavigationInfo(it) }
                travelEstimate?.let { setDestinationTravelEstimate(it) }
            }
            .build()
        Log.d(AndroidAutoNavLogTag, "onGetTemplate called")
        Log.d(AndroidAutoNavLogTag, "Template class: ${template::class.java}")
        Log.d(AndroidAutoNavLogTag, "Navigation active: ${projectedNavigationActive || hasProjectedRouteDrive(latestState)}")
        Log.d(AndroidAutoNavLogTag, "RoutingInfo attached to template: ${routingInfo != null}")
        Log.d(AndroidAutoNavLogTag, "Trip present: ${tripBuild != null}")
        Log.d(AndroidAutoNavLogTag, "Trip attached to template: false; Trip is sent with NavigationManager.updateTrip")
        Log.d(AndroidAutoNavLogTag, "Android Auto step count: $androidAutoStepCount")
        Log.d(AndroidAutoNavLogTag, "Current step index: ${guidance?.currentStepIndex ?: 0}")
        Log.d(AndroidAutoNavLogTag, "Current instruction: ${currentGuidanceStep?.instruction ?: guidance?.nextInstruction}")
        Log.d(AndroidAutoNavLogTag, "Current maneuver type: ${currentGuidanceStep?.maneuver?.type ?: guidance?.nextManeuver?.type}")
        Log.d(AndroidAutoNavLogTag, "Distance to next maneuver: ${guidance?.distanceToManeuverMeters}")
        Log.d(AndroidAutoNavLogTag, "Remaining distance: ${guidance?.remainingDistanceMeters}")
        Log.d(AndroidAutoNavLogTag, "Remaining time: ${guidance?.remainingDurationSeconds}")
        Log.d(AndroidAutoNavLogTag, "TravelEstimate present: ${travelEstimate != null}")
        return template
    }

    private fun reassertNavigationSessionForTemplate(
        snapshot: OttoShellUiState,
        tripBuild: AndroidAutoTripBuild?,
    ) {
        val shouldBeActive = hasProjectedRouteDrive(snapshot) || routingInfoFor(snapshot) != null
        if (!shouldBeActive) return
        if (!projectedNavigationActive) {
            projectedNavigationActive = true
        }
        navigationManager.navigationStarted()
        Log.d(AndroidAutoNavLogTag, "NavigationManager.navigationStarted reasserted from onGetTemplate")
        tripBuild?.let {
            navigationManager.updateTrip(it.trip)
            Log.d(AndroidAutoNavLogTag, "NavigationManager.updateTrip reasserted from onGetTemplate")
        }
    }

    private fun routingInfoFor(snapshot: OttoShellUiState): RoutingInfo? {
        val state = snapshot.turnByTurnGuidance
        if (state == null) {
            return when {
                snapshot.activeRouteDriveSession?.isArmed == true -> waitingForRouteDriveStartRoutingInfo()
                hasProjectedRouteDrive(snapshot) -> startingRouteGuidanceRoutingInfo(snapshot)
                else -> null
            }
        }
        if (state.phase == TurnByTurnGuidancePhase.ARRIVED) return null
        if (state.phase == TurnByTurnGuidancePhase.LOADING) {
            val step =
                buildAndroidAutoStep(
                    cue = state.nextInstruction.ifBlank { "Loading route guidance" },
                    road = "Preparing turn-by-turn directions",
                    rawType = "depart",
                    rawModifier = null,
                    index = 0,
                    distanceMeters = state.distanceToManeuverMeters,
                )
            return RoutingInfo.Builder()
                .setCurrentStep(step.step, carDistance(step.distanceMeters))
                .build()
        }
        if (state.phase == TurnByTurnGuidancePhase.FAILED) {
            val failedStep =
                buildAndroidAutoStep(
                    cue = state.failureMessage ?: state.nextInstruction.ifBlank { "Route guidance unavailable" },
                    road = null,
                    rawType = null,
                    rawModifier = null,
                    index = state.currentStepIndex,
                    distanceMeters = 0.0,
                )
            return RoutingInfo.Builder()
                .setCurrentStep(
                    failedStep.step,
                    carDistance(0.0),
                )
                .build()
        }
        val currentStep =
            buildAndroidAutoStep(
                guidanceStep = state.currentGuidanceStep,
                fallbackCue = state.nextInstruction,
                fallbackRoad = state.currentRoadName,
                fallbackType = state.nextManeuver?.type,
                fallbackModifier = state.nextManeuver?.modifier,
                fallbackIndex = state.currentStepIndex,
                fallbackDistanceMeters = state.distanceToManeuverMeters,
            )
        val nextStep =
            state.upcomingGuidanceStep?.let { step ->
                buildAndroidAutoStep(
                    guidanceStep = step,
                    fallbackCue = step.instruction,
                    fallbackRoad = step.roadName,
                    fallbackType = step.maneuver.type,
                    fallbackModifier = step.maneuver.modifier,
                    fallbackIndex = step.index,
                    fallbackDistanceMeters = step.distanceToManeuverMeters,
                )
            }
        Log.d(
            AndroidAutoNavLogTag,
            """
            RoutingInfo built:
            Current step cue: ${currentStep.cue}
            Next step cue: ${nextStep?.cue}
            Android Auto step count: ${listOfNotNull(currentStep, nextStep).size}
            TravelEstimate present: ${travelEstimateFor(snapshot) != null}
            """.trimIndent(),
        )
        logAndroidAutoNavigationState(
            reason = "After internal steps to Android Auto Steps conversion",
            isNavigating = true,
            routeId = snapshot.activeRouteDriveSession?.activeRouteId ?: snapshot.activeDriveSession?.routeId,
            rawRouteStepCount = state.totalSteps,
            androidAutoStepCount = listOfNotNull(currentStep, nextStep).size,
            currentStepIndex = state.currentStepIndex,
            currentInstruction = currentStep.cue,
            currentManeuverType = state.currentGuidanceStep?.maneuver?.type ?: state.nextManeuver?.type,
            currentManeuverModifier = state.currentGuidanceStep?.maneuver?.modifier ?: state.nextManeuver?.modifier,
            distanceToNextManeuverMeters = currentStep.distanceMeters,
            remainingDistanceMeters = state.remainingDistanceMeters,
            remainingTimeSeconds = state.remainingDurationSeconds,
            arrivalTimeMillis = state.arrivalTimeMillis(),
            travelEstimatePresent = state.phase !in setOf(TurnByTurnGuidancePhase.LOADING, TurnByTurnGuidancePhase.FAILED),
            tripPresent = true,
        )
        return RoutingInfo.Builder()
            .setCurrentStep(currentStep.step, carDistance(currentStep.distanceMeters))
            .apply {
                nextStep?.let { setNextStep(it.step) }
            }
            .build()
    }

    private data class AndroidAutoStepBuild(
        val step: Step,
        val cue: String,
        val distanceMeters: Double,
    )

    private fun buildAndroidAutoStep(
        guidanceStep: TurnByTurnGuidanceStep?,
        fallbackCue: String,
        fallbackRoad: String?,
        fallbackType: String?,
        fallbackModifier: String?,
        fallbackIndex: Int,
        fallbackDistanceMeters: Double,
    ): AndroidAutoStepBuild =
        buildAndroidAutoStep(
            cue = guidanceStep?.instruction ?: fallbackCue,
            road = guidanceStep?.roadName ?: fallbackRoad,
            rawType = guidanceStep?.maneuver?.type ?: fallbackType,
            rawModifier = guidanceStep?.maneuver?.modifier ?: fallbackModifier,
            index = guidanceStep?.index ?: fallbackIndex,
            distanceMeters = guidanceStep?.distanceToManeuverMeters ?: fallbackDistanceMeters,
        )

    private fun buildAndroidAutoStep(
        cue: String,
        road: String?,
        rawType: String?,
        rawModifier: String?,
        index: Int,
        distanceMeters: Double,
    ): AndroidAutoStepBuild {
        val resolvedCue = cue.ifBlank { "Continue" }
        val maneuverType = androidAutoManeuverType(rawType, rawModifier)
        val maneuver =
            Maneuver.Builder(maneuverType)
                .setIcon(defaultManeuverIcon())
                .build()
        Log.d(
            AndroidAutoNavLogTag,
            """
            Android Auto step index: $index
            Cue: $resolvedCue
            Has maneuver: true
            Distance to step: $distanceMeters
            """.trimIndent(),
        )
        return AndroidAutoStepBuild(
            step =
                Step.Builder(resolvedCue)
                    .setManeuver(maneuver)
                    .apply {
                        road?.takeIf { it.isNotBlank() }?.let { setRoad(it) }
                    }
                    .build(),
            cue = resolvedCue,
            distanceMeters = distanceMeters,
        )
    }

    private data class AndroidAutoTripBuild(
        val trip: Trip,
        val stepCount: Int,
        val destinationEstimatePresent: Boolean,
        val currentCue: String?,
        val nextCue: String?,
    )

    private fun updateProjectedTrip(snapshot: OttoShellUiState) {
        val shouldBeActive = hasProjectedRouteDrive(snapshot) || routingInfoFor(snapshot) != null
        if (!shouldBeActive) {
            lastProjectedTripFingerprint = null
            return
        }
        val tripBuild = buildProjectedTrip(snapshot)
        val fingerprint =
            listOf(
                shouldBeActive,
                snapshot.activeRouteDriveSession?.activeRouteId,
                snapshot.activeDriveSession?.routeId,
                snapshot.mapSelectedRoute?.name,
                snapshot.turnByTurnGuidance,
                snapshot.activeRouteDriveSession?.isArmed,
            ).hashCode()
        if (fingerprint == lastProjectedTripFingerprint) return
        lastProjectedTripFingerprint = fingerprint
        if (tripBuild != null) {
            navigationManager.updateTrip(tripBuild.trip)
            Log.d(
                AndroidAutoNavLogTag,
                """
                Trip built:
                Current step cue: ${tripBuild.currentCue}
                Next step cue: ${tripBuild.nextCue}
                Android Auto step count: ${tripBuild.stepCount}
                TravelEstimate present: ${tripBuild.destinationEstimatePresent}
                """.trimIndent(),
            )
            Log.d(AndroidAutoNavLogTag, "NavigationManager.updateTrip sent")
        }
    }

    private fun buildProjectedTrip(snapshot: OttoShellUiState): AndroidAutoTripBuild? {
        val state = snapshot.turnByTurnGuidance
        val destinationName =
            snapshot.mapSelectedRoute?.name
                ?: snapshot.activeDriveSession?.routeName
                ?: "Destination"
        val builder = Trip.Builder()
            .addDestination(
                Destination.Builder()
                    .setName(destinationName.ifBlank { "Destination" })
                    .build(),
                destinationTravelEstimateFor(snapshot),
            )
        if (state == null) {
            if (snapshot.activeRouteDriveSession?.isArmed == true) {
                val waitingStep =
                    buildAndroidAutoStep(
                        cue = "Ready when you are",
                        road = "Waiting for drive start",
                        rawType = "depart",
                        rawModifier = null,
                        index = 0,
                        distanceMeters = 0.0,
                    )
                builder
                    .setCurrentRoad("Waiting for drive start")
                    .addStep(waitingStep.step, stepTravelEstimate(waitingStep.distanceMeters, 0.0))
                return AndroidAutoTripBuild(
                    trip = builder.build(),
                    stepCount = 1,
                    destinationEstimatePresent = true,
                    currentCue = waitingStep.cue,
                    nextCue = null,
                )
            }
            return if (hasProjectedRouteDrive(snapshot)) {
                val startingStep =
                    buildAndroidAutoStep(
                        cue = "Starting route guidance",
                        road = snapshot.mapSelectedRoute?.name ?: "Preparing turn-by-turn directions",
                        rawType = "depart",
                        rawModifier = null,
                        index = 0,
                        distanceMeters = snapshot.mapSelectedRoute?.distanceMeters ?: 0.0,
                    )
                builder.addStep(
                    startingStep.step,
                    stepTravelEstimate(
                        distanceMeters = startingStep.distanceMeters,
                        remainingSeconds = snapshot.mapSelectedRoute?.etaSeconds ?: 0.0,
                    ),
                )
                AndroidAutoTripBuild(
                    trip = builder.build(),
                    stepCount = 1,
                    destinationEstimatePresent = true,
                    currentCue = startingStep.cue,
                    nextCue = null,
                )
            } else {
                null
            }
        }
        if (state.phase == TurnByTurnGuidancePhase.LOADING) {
            val loadingStep =
                buildAndroidAutoStep(
                    cue = state.nextInstruction.ifBlank { "Loading route guidance" },
                    road = "Preparing turn-by-turn directions",
                    rawType = "depart",
                    rawModifier = null,
                    index = 0,
                    distanceMeters = state.distanceToManeuverMeters,
                )
            builder.addStep(loadingStep.step, stepTravelEstimate(loadingStep.distanceMeters, 0.0))
            return AndroidAutoTripBuild(
                trip = builder.build(),
                stepCount = 1,
                destinationEstimatePresent = true,
                currentCue = loadingStep.cue,
                nextCue = null,
            )
        }
        if (state.phase == TurnByTurnGuidancePhase.FAILED) {
            val failedStep =
                buildAndroidAutoStep(
                    cue = state.failureMessage ?: state.nextInstruction.ifBlank { "Route guidance unavailable" },
                    road = null,
                    rawType = null,
                    rawModifier = null,
                    index = state.currentStepIndex,
                    distanceMeters = 0.0,
                )
            builder.addStep(failedStep.step, stepTravelEstimate(0.0, 0.0))
            return AndroidAutoTripBuild(
                trip = builder.build(),
                stepCount = 1,
                destinationEstimatePresent = true,
                currentCue = failedStep.cue,
                nextCue = null,
            )
        }
        val currentStep =
            buildAndroidAutoStep(
                guidanceStep = state.currentGuidanceStep,
                fallbackCue = state.nextInstruction,
                fallbackRoad = state.currentRoadName,
                fallbackType = state.nextManeuver?.type,
                fallbackModifier = state.nextManeuver?.modifier,
                fallbackIndex = state.currentStepIndex,
                fallbackDistanceMeters = state.distanceToManeuverMeters,
            )
        val nextStep =
            state.upcomingGuidanceStep?.let { step ->
                buildAndroidAutoStep(
                    guidanceStep = step,
                    fallbackCue = step.instruction,
                    fallbackRoad = step.roadName,
                    fallbackType = step.maneuver.type,
                    fallbackModifier = step.maneuver.modifier,
                    fallbackIndex = step.index,
                    fallbackDistanceMeters = step.distanceToManeuverMeters,
                )
            }
        builder
            .setCurrentRoad(state.currentRoadName ?: currentStep.cue)
            .addStep(
                currentStep.step,
                stepTravelEstimate(
                    currentStep.distanceMeters,
                    state.currentGuidanceStep?.durationSeconds ?: state.remainingDurationSeconds,
                ),
            )
        nextStep?.let {
            builder.addStep(
                it.step,
                stepTravelEstimate(
                    it.distanceMeters,
                    state.upcomingGuidanceStep?.durationSeconds ?: 0.0,
                ),
            )
        }
        return AndroidAutoTripBuild(
            trip = builder.build(),
            stepCount = listOfNotNull(currentStep, nextStep).size,
            destinationEstimatePresent = true,
            currentCue = currentStep.cue,
            nextCue = nextStep?.cue,
        )
    }

    private fun destinationTravelEstimateFor(snapshot: OttoShellUiState): TravelEstimate {
        val state = snapshot.turnByTurnGuidance
        val distanceMeters =
            state?.remainingDistanceMeters
                ?: snapshot.mapSelectedRoute?.distanceMeters
                ?: 0.0
        val remainingSeconds =
            state?.remainingDurationSeconds
                ?: snapshot.mapSelectedRoute?.etaSeconds?.toDouble()
                ?: 0.0
        return travelEstimate(
            distanceMeters = distanceMeters,
            remainingSeconds = remainingSeconds,
        )
    }

    private fun stepTravelEstimate(
        distanceMeters: Double,
        remainingSeconds: Double,
    ): TravelEstimate =
        travelEstimate(
            distanceMeters = distanceMeters,
            remainingSeconds = remainingSeconds,
        )

    private fun travelEstimate(
        distanceMeters: Double,
        remainingSeconds: Double,
    ): TravelEstimate {
        val seconds = remainingSeconds.coerceAtLeast(0.0).toLong()
        val arrivalMillis = System.currentTimeMillis() + seconds * 1_000L
        return TravelEstimate.Builder(
            carDistance(distanceMeters),
            DateTimeWithZone.create(arrivalMillis, TimeZone.getDefault()),
        )
            .setRemainingTimeSeconds(seconds)
            .build()
    }

    private fun waitingForRouteDriveStartRoutingInfo(): RoutingInfo {
        val step =
            Step.Builder("Ready when you are")
                .setManeuver(
                    Maneuver.Builder(Maneuver.TYPE_DEPART)
                        .setIcon(defaultManeuverIcon())
                        .build(),
                )
                .setRoad("Waiting for drive start")
                .build()
        return RoutingInfo.Builder()
            .setCurrentStep(step, carDistance(0.0))
            .build()
    }

    private fun startingRouteGuidanceRoutingInfo(snapshot: OttoShellUiState): RoutingInfo {
        val step =
            buildAndroidAutoStep(
                cue = "Starting route guidance",
                road = snapshot.mapSelectedRoute?.name ?: "Preparing turn-by-turn directions",
                rawType = "depart",
                rawModifier = null,
                index = 0,
                distanceMeters = snapshot.mapSelectedRoute?.distanceMeters ?: 0.0,
            )
        return RoutingInfo.Builder()
            .setCurrentStep(step.step, carDistance(step.distanceMeters))
            .build()
    }

    private fun syncProjectedNavigationSession(snapshot: OttoShellUiState) {
        val shouldBeActive = hasProjectedRouteDrive(snapshot) || routingInfoFor(snapshot) != null
        val fingerprint =
            listOf(
                shouldBeActive,
                snapshot.turnByTurnGuidance?.phase,
                snapshot.turnByTurnGuidance?.currentStepIndex,
                snapshot.turnByTurnGuidance?.distanceToManeuverMeters?.toInt(),
                snapshot.turnByTurnGuidance?.nextInstruction,
                snapshot.activeRouteDriveSession?.status,
            ).hashCode()
        if (fingerprint == lastNavigationSessionFingerprint) return
        lastNavigationSessionFingerprint = fingerprint
        val navigationActiveChanged = shouldBeActive != projectedNavigationActive
        if (navigationActiveChanged) {
            projectedNavigationActive = shouldBeActive
            if (shouldBeActive) {
                Log.d(AndroidAutoNavLogTag, "Navigation started")
                Log.d(
                    AndroidAutoNavLogTag,
                    "Route id: ${snapshot.activeRouteDriveSession?.activeRouteId ?: snapshot.activeDriveSession?.routeId}",
                )
                Log.d(AndroidAutoNavLogTag, "Is navigating: true")
                Log.d(AndroidAutoNavLogTag, "Route step count: ${snapshot.turnByTurnGuidance?.totalSteps ?: 0}")
                Log.d("OttoCarMapObserver", "Android Auto projected navigation started")
                mapObserver.ensureAndroidAutoMapLoaded(reason = "navigation-start")
                navigationManager.navigationStarted()
            } else {
                Log.d("OttoCarMapObserver", "Android Auto projected navigation ended")
                navigationManager.navigationEnded()
            }
        } else if (shouldBeActive) {
            navigationManager.navigationStarted()
        }
        Log.d(AndroidAutoNavLogTag, "Invalidating Android Auto template because navigation state changed")
        invalidate()
    }

    private fun hasProjectedRouteDrive(snapshot: OttoShellUiState): Boolean {
        if (snapshot.activeRouteDriveSession != null) return true
        return snapshot.activeDriveSession?.kind == DriveSessionKind.ROUTE && snapshot.mapSelectedRoute != null
    }

    private fun endProjectedNavigationSession() {
        if (!projectedNavigationActive) return
        projectedNavigationActive = false
        lastNavigationSessionFingerprint = null
        navigationManager.navigationEnded()
    }

    private fun travelEstimateFor(snapshot: OttoShellUiState): TravelEstimate? {
        if (!hasProjectedRouteDrive(snapshot) && routingInfoFor(snapshot) == null) return null
        val guidance = snapshot.turnByTurnGuidance
        if (guidance != null &&
            guidance.phase != TurnByTurnGuidancePhase.LOADING &&
            guidance.phase != TurnByTurnGuidancePhase.FAILED
        ) {
            val remainingSeconds = guidance.remainingDurationSeconds.coerceAtLeast(0.0).toLong()
            val arrivalMillis = System.currentTimeMillis() + remainingSeconds * 1_000L
            Log.d(
                AndroidAutoNavLogTag,
                """
                TravelEstimate:
                Remaining distance meters: ${guidance.remainingDistanceMeters}
                Remaining time seconds: $remainingSeconds
                Arrival time millis: $arrivalMillis
                """.trimIndent(),
            )
            logAndroidAutoNavigationState(
                reason = "TravelEstimate updates",
                isNavigating = true,
                routeId = latestState.activeRouteDriveSession?.activeRouteId ?: latestState.activeDriveSession?.routeId,
                rawRouteStepCount = guidance.totalSteps,
                androidAutoStepCount = guidance.androidAutoStepCount(),
                currentStepIndex = guidance.currentStepIndex,
                currentInstruction = guidance.currentGuidanceStep?.instruction ?: guidance.nextInstruction,
                currentManeuverType = guidance.currentGuidanceStep?.maneuver?.type ?: guidance.nextManeuver?.type,
                currentManeuverModifier = guidance.currentGuidanceStep?.maneuver?.modifier ?: guidance.nextManeuver?.modifier,
                distanceToNextManeuverMeters = guidance.distanceToManeuverMeters,
                remainingDistanceMeters = guidance.remainingDistanceMeters,
                remainingTimeSeconds = remainingSeconds.toDouble(),
                arrivalTimeMillis = arrivalMillis,
                travelEstimatePresent = true,
                tripPresent = false,
            )
            return TravelEstimate.Builder(
                carDistance(guidance.remainingDistanceMeters),
                DateTimeWithZone.create(arrivalMillis, TimeZone.getDefault()),
            )
                .setRemainingTimeSeconds(remainingSeconds)
                .build()
        }
        return destinationTravelEstimateFor(snapshot)
    }

    private fun TurnByTurnGuidanceState.androidAutoStepCount(): Int =
        listOfNotNull(currentGuidanceStep, upcomingGuidanceStep).size

    private fun TurnByTurnGuidanceState.arrivalTimeMillis(): Long =
        System.currentTimeMillis() + remainingDurationSeconds.coerceAtLeast(0.0).toLong() * 1_000L

    private fun carDistance(meters: Double): Distance {
        val clampedMeters = meters.coerceAtLeast(0.0)
        val feet = clampedMeters * 3.28084
        return if (feet < 950.0) {
            Distance.create(maxOf(50.0, (feet / 50.0).toInt() * 50.0), Distance.UNIT_FEET)
        } else {
            Distance.create(clampedMeters / 1609.344, Distance.UNIT_MILES_P1)
        }
    }

    private fun androidAutoManeuverType(type: String?, modifier: String?): Int {
        val t = type?.lowercase().orEmpty()
        val m = modifier?.lowercase().orEmpty()
        return when {
            t == "arrive" -> Maneuver.TYPE_DESTINATION
            t == "depart" -> Maneuver.TYPE_DEPART
            t == "merge" -> Maneuver.TYPE_MERGE_SIDE_UNSPECIFIED
            t == "fork" && m.contains("right") -> Maneuver.TYPE_FORK_RIGHT
            t == "fork" && m.contains("left") -> Maneuver.TYPE_FORK_LEFT
            t == "fork" -> {
                Log.w(AndroidAutoNavLogTag, "Unknown maneuver type: $type / $modifier")
                Maneuver.TYPE_UNKNOWN
            }
            t == "roundabout" || t == "rotary" -> Maneuver.TYPE_ROUNDABOUT_ENTER_AND_EXIT_CW
            t == "continue" || t == "new name" || t == "notification" -> Maneuver.TYPE_STRAIGHT
            (m.contains("uturn") || m.contains("u-turn")) && m.contains("right") -> Maneuver.TYPE_U_TURN_RIGHT
            m.contains("uturn") || m.contains("u-turn") -> Maneuver.TYPE_U_TURN_LEFT
            m.contains("sharp left") -> Maneuver.TYPE_TURN_SHARP_LEFT
            m.contains("sharp right") -> Maneuver.TYPE_TURN_SHARP_RIGHT
            m.contains("slight left") -> Maneuver.TYPE_TURN_SLIGHT_LEFT
            m.contains("slight right") -> Maneuver.TYPE_TURN_SLIGHT_RIGHT
            m.contains("straight") -> Maneuver.TYPE_STRAIGHT
            m.contains("left") -> Maneuver.TYPE_TURN_NORMAL_LEFT
            m.contains("right") -> Maneuver.TYPE_TURN_NORMAL_RIGHT
            t == "turn" || t == "end of road" -> Maneuver.TYPE_STRAIGHT
            else -> {
                Log.w(AndroidAutoNavLogTag, "Unknown maneuver type: $type / $modifier")
                Maneuver.TYPE_STRAIGHT
            }
        }
    }

    private fun defaultManeuverIcon(): CarIcon =
        CarIcon.Builder(
            IconCompat.createWithResource(carContext, R.drawable.ic_otto_steering_wheel),
        ).build()

    private fun searchAction(): Action =
        Action.Builder()
            .setIcon(
                CarIcon.Builder(
                    IconCompat.createWithResource(carContext, R.drawable.ic_android_auto_search_white),
                ).build(),
            )
            .setOnClickListener {
                Log.d("OttoCarMapObserver", "Android Auto search action clicked")
                push(
                    OttoCarDestinationSearchScreen(
                        carContext = carContext,
                        viewModel = viewModel,
                        dataRepository = dataRepository,
                        mapScreen = this,
                    ),
                )
            }
            .build()

    private fun zoomInAction(): Action =
        Action.Builder()
            .setIcon(
                CarIcon.Builder(
                    IconCompat.createWithResource(carContext, R.drawable.ic_android_auto_zoom_in_white),
                ).build(),
            )
            .setOnClickListener {
                mapObserver.zoomIn()
            }
            .build()

    private fun zoomOutAction(): Action =
        Action.Builder()
            .setIcon(
                CarIcon.Builder(
                    IconCompat.createWithResource(carContext, R.drawable.ic_android_auto_zoom_out_white),
                ).build(),
            )
            .setOnClickListener {
                mapObserver.zoomOut()
            }
            .build()

    private fun reportHazardAction(): Action =
        Action.Builder()
            .setIcon(
                CarIcon.Builder(
                    IconCompat.createWithResource(carContext, R.drawable.ic_android_auto_hazard_yellow),
                ).setTint(AndroidAutoControlAccent).build(),
            )
            .setOnClickListener {
                if (viewModel.state.value.deviceLocationFix == null) {
                    CarToast
                        .makeText(
                            carContext,
                            carContext.getString(R.string.android_auto_status_location_unavailable),
                            CarToast.LENGTH_SHORT,
                        ).show()
                } else {
                    push(OttoCarHazardPickerScreen(carContext, viewModel))
                }
            }
            .build()

    private fun push(screen: Screen) {
        carContext.getCarService(ScreenManager::class.java).push(screen)
    }

    private companion object {
        val AndroidAutoControlAccent: CarColor =
            CarColor.createCustom(0xFFFFC71F.toInt(), 0xFFFFC71F.toInt())
    }
}

internal class OttoCarNavigationIntentScreen(
    carContext: CarContext,
    private val request: AndroidAutoNavigationIntentRequest,
    private val viewModel: OttoShellViewModel,
    private val dataRepository: OttoDataRepository,
    private val mapScreen: Screen,
) : Screen(carContext) {
    private val navigationManager = carContext.getCarService(NavigationManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val recentDestinations = AndroidAutoRecentDestinations(carContext.applicationContext)
    private var navigationActive = false
    private var searchJob: Job? = null
    private var routeJob: Job? = null
    private var searchStarted = false
    private var searchLoading = false
    private var searchFailed = false
    private var isRouting = false
    private var autoStartAttempted = false
    private var pendingNavigationDestinationName: String? = null
    private var searchResults: List<AndroidAutoNavigationDestination> = emptyList()

    init {
        navigationManager.setNavigationManagerCallback(
            ContextCompat.getMainExecutor(carContext),
            object : NavigationManagerCallback {
                override fun onStopNavigation() {
                    viewModel.requestStopDriveSessionFromAndroidAuto()
                    endNavigation()
                }

                override fun onAutoDriveEnabled() {
                    if (!navigationActive && request.kind != AndroidAutoNavigationIntentKind.AddStop) {
                        startNavigation(primaryDestination())
                    }
                }
            },
        )
        lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onDestroy(owner: LifecycleOwner) {
                    endNavigation()
                    searchJob?.cancel()
                    routeJob?.cancel()
                    navigationManager.clearNavigationManagerCallback()
                }
            },
        )
    }

    override fun onGetTemplate(): Template {
        if (request.kind == AndroidAutoNavigationIntentKind.AddStop) {
            return MessageTemplate.Builder(
                carContext.getString(R.string.android_auto_nav_add_stop_unsupported_body),
            )
                .setTitle(carContext.getString(R.string.android_auto_nav_add_stop_unsupported_title))
                .addAction(
                    Action.Builder()
                        .setTitle(carContext.getString(R.string.android_auto_view_map))
                        .setOnClickListener { showMapOnly() }
                        .build(),
                )
                .build()
        }

        if (isRouting) {
            return MessageTemplate.Builder(
                carContext.getString(
                    R.string.android_auto_nav_started_format,
                    pendingNavigationDestinationName ?: request.displayName,
                ),
            )
                .setTitle(carContext.getString(R.string.android_auto_nav_starting_title))
                .build()
        }

        val directDestination = request.directDestination()
        if (directDestination != null) {
            autoStartNavigationIfNeeded(directDestination)
            return destinationListTemplate(
                title = carContext.getString(R.string.android_auto_nav_request_title),
                destinations = listOf(directDestination),
                subtitle = request.detailText(),
            )
        }

        startSearchIfNeeded()
        if (searchLoading) {
            return MessageTemplate.Builder(
                carContext.getString(R.string.android_auto_nav_searching_body, request.displayName),
            )
                .setTitle(carContext.getString(R.string.android_auto_nav_searching_title))
                .build()
        }
        if (searchFailed) {
            return MessageTemplate.Builder(
                carContext.getString(R.string.android_auto_nav_lookup_failed_body),
            )
                .setTitle(carContext.getString(R.string.android_auto_nav_lookup_failed_title))
                .addAction(
                    Action.Builder()
                        .setTitle(carContext.getString(R.string.retry))
                        .setOnClickListener {
                            searchStarted = false
                            searchFailed = false
                            startSearchIfNeeded()
                            invalidate()
                        }
                        .build(),
                )
                .build()
        }
        if (searchResults.isEmpty()) {
            return MessageTemplate.Builder(
                carContext.getString(R.string.android_auto_nav_no_results_body, request.displayName),
            )
                .setTitle(carContext.getString(R.string.android_auto_nav_no_results_title))
                .build()
        }

        return destinationListTemplate(
            title = carContext.getString(R.string.android_auto_nav_results_title),
            destinations = searchResults,
            subtitle = request.detailText(),
        )
    }

    private fun destinationListTemplate(
        title: String,
        destinations: List<AndroidAutoNavigationDestination>,
        subtitle: String,
    ): Template {
        val list =
            ItemList.Builder()
                .apply {
                    destinations.forEach { destination ->
                        addItem(
                            Row.Builder()
                                .setTitle(carContext.getString(R.string.android_auto_nav_route_to_format, destination.name))
                                .apply {
                                    destination.address?.takeIf { it.isNotBlank() }?.let { addText(it) }
                                    addText(subtitle)
                                }
                                .setOnClickListener { startNavigation(destination) }
                                .build(),
                        )
                    }
                }.build()
        return ListTemplate.Builder()
            .setSingleList(list)
            .setTitle(title)
            .setActionStrip(
                ActionStrip.Builder()
                    .addAction(
                        Action.Builder()
                            .setTitle(carContext.getString(R.string.android_auto_nav_start))
                            .setOnClickListener { startNavigation(destinations.firstOrNull()) }
                            .build(),
                    )
                    .build(),
            )
            .build()
    }

    private fun AndroidAutoNavigationIntentRequest.detailText(): String =
        when {
            latitude != null && longitude != null ->
                carContext.getString(R.string.android_auto_nav_coordinates_format, latitude, longitude)
            kind == AndroidAutoNavigationIntentKind.Search ->
                carContext.getString(R.string.android_auto_nav_search_result_body)
            kind == AndroidAutoNavigationIntentKind.Directions ->
                carContext.getString(R.string.android_auto_nav_directions_body)
            else ->
                carContext.getString(R.string.android_auto_nav_destination_body)
        }

    private fun startSearchIfNeeded() {
        if (searchStarted || request.query.isNullOrBlank()) return
        searchStarted = true
        searchLoading = true
        searchFailed = false
        val fix = viewModel.state.value.deviceLocationFix
        searchJob =
            scope.launch {
                val result =
                    dataRepository.navigationSearch(
                        query = request.query,
                        latitude = fix?.latitude,
                        longitude = fix?.longitude,
                        limit = 3,
                    )
                result
                    .onSuccess { response ->
                        searchResults =
                            response.results
                                .orEmpty()
                                .map { it.toAndroidAutoDestination() }
                        if (searchResults.size == 1) {
                            autoStartNavigationIfNeeded(searchResults.first())
                        }
                    }
                    .onFailure {
                        searchFailed = true
                    }
                searchLoading = false
                invalidate()
            }
    }

    private fun primaryDestination(): AndroidAutoNavigationDestination? =
        request.directDestination() ?: searchResults.firstOrNull()

    private fun AndroidAutoNavigationIntentRequest.directDestination(): AndroidAutoNavigationDestination? {
        val lat = latitude ?: return null
        val lng = longitude ?: return null
        return AndroidAutoNavigationDestination(
            name = displayName,
            address = null,
            latitude = lat,
            longitude = lng,
        )
    }

    private fun NavigationSearchResultDto.toAndroidAutoDestination(): AndroidAutoNavigationDestination =
        AndroidAutoNavigationDestination(
            name = name,
            address = address,
            latitude = latitude,
            longitude = longitude,
        )

    private fun autoStartNavigationIfNeeded(destination: AndroidAutoNavigationDestination) {
        if (autoStartAttempted || request.kind == AndroidAutoNavigationIntentKind.Search) return
        autoStartAttempted = true
        startNavigation(destination)
    }

    private fun startNavigation(destination: AndroidAutoNavigationDestination?) {
        val target = destination ?: primaryDestination() ?: return
        if (isRouting) return
        pendingNavigationDestinationName = target.name
        routeJob =
            startAndroidAutoDestinationRoute(
                carContext = carContext,
                viewModel = viewModel,
                dataRepository = dataRepository,
                mapScreen = mapScreen,
                destination = target,
                recentDestinations = recentDestinations,
                scope = scope,
                onRoutingStarted = {
                    isRouting = true
                    navigationActive = true
                    navigationManager.navigationStarted()
                    invalidate()
                },
                onRouteDriveStarted = {
                    showMapAfterRouteStarted()
                    navigationActive = false
                },
                onRoutingFinished = {
                    isRouting = false
                    pendingNavigationDestinationName = null
                    invalidate()
                },
            )
    }

    private fun showMapOnly() {
        carContext.getCarService(ScreenManager::class.java).push(mapScreen)
    }

    private fun showMapAfterRouteStarted() {
        val screenManager = carContext.getCarService(ScreenManager::class.java)
        if (screenManager.screenStack.firstOrNull() === this) {
            screenManager.push(mapScreen)
        } else {
            screenManager.popToRoot()
        }
    }

    private fun endNavigation() {
        if (navigationActive) {
            navigationActive = false
            navigationManager.navigationEnded()
        }
    }
}

private data class AndroidAutoNavigationDestination(
    val id: String? = null,
    val name: String,
    val address: String?,
    val latitude: Double,
    val longitude: Double,
    val source: String? = null,
)

private fun startAndroidAutoDestinationRoute(
    carContext: CarContext,
    viewModel: OttoShellViewModel,
    dataRepository: OttoDataRepository,
    mapScreen: Screen,
    destination: AndroidAutoNavigationDestination,
    recentDestinations: AndroidAutoRecentDestinations,
    scope: CoroutineScope,
    onRoutingStarted: () -> Unit,
    onRouteDriveStarted: () -> Unit,
    onRoutingFinished: () -> Unit,
): Job? {
    val snapshot = viewModel.state.value
    if (snapshot.hasActiveDriveSession) {
        CarToast
            .makeText(
                carContext,
                carContext.getString(R.string.android_auto_nav_active_drive_first),
                CarToast.LENGTH_SHORT,
            ).show()
        return null
    }
    val fix = snapshot.deviceLocationFix
    if (fix == null) {
        CarToast
            .makeText(
                carContext,
                carContext.getString(R.string.android_auto_status_location_unavailable),
                CarToast.LENGTH_SHORT,
            ).show()
        return null
    }
    recentDestinations.save(destination)
    CarToast
        .makeText(
            carContext,
            carContext.getString(R.string.android_auto_nav_started_format, destination.name),
            CarToast.LENGTH_SHORT,
        ).show()
    onRoutingStarted()
    Log.d(
        "OttoCarMapObserver",
        "Android Auto route request start destination=${destination.name} startLat=${fix.latitude} startLng=${fix.longitude} " +
            "destLat=${destination.latitude} destLng=${destination.longitude}",
    )
    return scope.launch {
        try {
            dataRepository.navigationRoute(
                name = destination.name,
                startLatitude = fix.latitude,
                startLongitude = fix.longitude,
                destinationLatitude = destination.latitude,
                destinationLongitude = destination.longitude,
            )
                .onSuccess { route ->
                    Log.d(
                        "OttoCarMapObserver",
                        "Android Auto route success destination=${destination.name} points=${route.roadCoordinates.size} " +
                            "distanceMeters=${route.distanceMeters} etaSeconds=${route.etaSeconds}",
                    )
                    dataRepository.createNavigationDestinationRoute(route)
                        .onSuccess { savedRoute ->
                            val started =
                                viewModel.startRouteDrive(
                                    route = savedRoute,
                                    saveToProfile = true,
                                    shareLive = false,
                                    usesAdhocAndroidAutoDestination = true,
                                )
                            if (started) {
                                Log.d(
                                    "OttoCarMapObserver",
                                    "Android Auto route drive armed destination=${destination.name} routeId=${savedRoute.id}",
                                )
                                onRouteDriveStarted()
                            } else {
                                Log.w(
                                    "OttoCarMapObserver",
                                    "Android Auto route drive refused destination=${destination.name} routeId=${savedRoute.id}",
                                )
                                CarToast
                                    .makeText(
                                        carContext,
                                        carContext.getString(R.string.android_auto_nav_start_failed),
                                        CarToast.LENGTH_SHORT,
                                    ).show()
                            }
                        }
                        .onFailure { error ->
                            Log.e(
                                "OttoCarMapObserver",
                                "Android Auto hidden route create failed destination=${destination.name} type=${error::class.java.simpleName} message=${error.message}",
                                error,
                            )
                            CarToast
                                .makeText(
                                    carContext,
                                    carContext.getString(R.string.android_auto_nav_route_failed),
                                    CarToast.LENGTH_SHORT,
                                ).show()
                        }
                }
                .onFailure { error ->
                    Log.e(
                        "OttoCarMapObserver",
                        "Android Auto route failed destination=${destination.name} type=${error::class.java.simpleName} message=${error.message}",
                        error,
                    )
                    CarToast
                        .makeText(
                            carContext,
                            carContext.getString(R.string.android_auto_nav_route_failed),
                            CarToast.LENGTH_SHORT,
                        ).show()
                }
        } finally {
            onRoutingFinished()
        }
    }
}

private class AndroidAutoRecentDestinations(
    context: Context,
) {
    private val preferences =
        context.getSharedPreferences("otto_android_auto_recent_destinations", Context.MODE_PRIVATE)

    fun load(): List<AndroidAutoNavigationDestination> {
        val raw = preferences.getString(StorageKey, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val latitude = item.optDouble("latitude", Double.NaN)
                    val longitude = item.optDouble("longitude", Double.NaN)
                    val name = item.optString("name").trim()
                    if (name.isEmpty() || !latitude.isFinite() || !longitude.isFinite()) continue
                    add(
                        AndroidAutoNavigationDestination(
                            id = item.optNullableString("id"),
                            name = name,
                            address = item.optNullableString("address"),
                            latitude = latitude,
                            longitude = longitude,
                            source = item.optNullableString("source"),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun save(destination: AndroidAutoNavigationDestination) {
        if (!destination.latitude.isFinite() || !destination.longitude.isFinite()) return
        val updated =
            buildList {
                add(destination)
                load()
                    .filterNot { it.matches(destination) }
                    .forEach { add(it) }
            }.take(MaxStoredCount)
        val array = JSONArray()
        updated.forEach { recent ->
            array.put(
                JSONObject()
                    .put("id", recent.id)
                    .put("name", recent.name)
                    .put("address", recent.address)
                    .put("latitude", recent.latitude)
                    .put("longitude", recent.longitude)
                    .put("source", recent.source),
            )
        }
        preferences.edit().putString(StorageKey, array.toString()).apply()
    }

    private fun AndroidAutoNavigationDestination.matches(other: AndroidAutoNavigationDestination): Boolean {
        val sameId = !id.isNullOrBlank() && id == other.id
        val sameName = name.equals(other.name, ignoreCase = true)
        val sameCoordinate = abs(latitude - other.latitude) < 0.000001 &&
            abs(longitude - other.longitude) < 0.000001
        return sameId || sameName || sameCoordinate
    }

    private fun JSONObject.optNullableString(name: String): String? =
        optString(name).trim().takeIf { it.isNotEmpty() && it != "null" }

    private companion object {
        const val StorageKey = "destinations"
        const val MaxStoredCount = 6
    }
}

private class OttoCarDestinationSearchScreen(
    carContext: CarContext,
    private val viewModel: OttoShellViewModel,
    private val dataRepository: OttoDataRepository,
    private val mapScreen: Screen,
) : Screen(carContext) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val recentDestinations = AndroidAutoRecentDestinations(carContext.applicationContext)
    private var searchJob: Job? = null
    private var routeJob: Job? = null
    private var currentQuery = ""
    private var isLoading = false
    private var isRouting = false
    private var hasSearched = false
    private var searchFailed = false
    private var searchResults: List<AndroidAutoNavigationDestination> = emptyList()

    init {
        lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onDestroy(owner: LifecycleOwner) {
                    searchJob?.cancel()
                    routeJob?.cancel()
                }
            },
        )
    }

    override fun onGetTemplate(): Template {
        val builder = SearchTemplate.Builder(
            object : SearchTemplate.SearchCallback {
                override fun onSearchTextChanged(searchText: String) {
                    updateSearchText(searchText)
                }

                override fun onSearchSubmitted(searchText: String) {
                    runSearch(searchText)
                }
            },
        )
            .setHeaderAction(Action.BACK)
            .setSearchHint(carContext.getString(R.string.android_auto_search_hint))
        if (isLoading) {
            builder.setLoading(true)
        } else if (isRouting) {
            builder.setLoading(true)
        } else {
            builder.setItemList(searchItemList())
        }
        return builder.build()
    }

    private fun updateSearchText(searchText: String) {
        val trimmed = searchText.trim()
        currentQuery = trimmed
        if (trimmed.isEmpty()) {
            searchJob?.cancel()
            isLoading = false
            hasSearched = false
            searchFailed = false
            searchResults = emptyList()
            invalidate()
        }
    }

    private fun runSearch(searchText: String) {
        val trimmed = searchText.trim()
        if (trimmed.isEmpty()) return
        currentQuery = trimmed
        searchJob?.cancel()
        isLoading = true
        hasSearched = true
        searchFailed = false
        searchResults = emptyList()
        invalidate()
        val fix = viewModel.state.value.deviceLocationFix
        Log.d(
            "OttoCarMapObserver",
            "Android Auto search submitted query=$trimmed hasFix=${fix != null} lat=${fix?.latitude} lng=${fix?.longitude}",
        )
        searchJob =
            scope.launch {
                Log.d(
                    "OttoCarMapObserver",
                    "Android Auto search request start query=$trimmed limit=3",
                )
                val result =
                    dataRepository.navigationSearch(
                        query = trimmed,
                        latitude = fix?.latitude,
                        longitude = fix?.longitude,
                        limit = 3,
                    )
                result
                    .onSuccess { response ->
                        searchResults =
                            response.results
                                .orEmpty()
                                .map { it.toAndroidAutoDestination() }
                        Log.d(
                            "OttoCarMapObserver",
                            "Android Auto search success query=$trimmed count=${searchResults.size} " +
                                "names=${searchResults.take(3).joinToString { it.name }}",
                        )
                    }
                    .onFailure { error ->
                        searchFailed = true
                        Log.e(
                            "OttoCarMapObserver",
                            "Android Auto search failed query=$trimmed type=${error::class.java.simpleName} message=${error.message}",
                            error,
                        )
                    }
                isLoading = false
                invalidate()
            }
    }

    private fun searchItemList(): ItemList {
        val builder = ItemList.Builder()
        when {
            currentQuery.isEmpty() -> {
                val recents = recentDestinations.load()
                if (recents.isEmpty()) {
                    builder.addItem(
                        Row.Builder()
                            .setTitle(carContext.getString(R.string.android_auto_search_empty_title))
                            .addText(carContext.getString(R.string.android_auto_search_empty_body))
                            .build(),
                    )
                } else {
                    addDestinationRows(builder, recents)
                }
            }
            searchFailed ->
                builder.addItem(
                    Row.Builder()
                        .setTitle(carContext.getString(R.string.android_auto_nav_lookup_failed_title))
                        .addText(carContext.getString(R.string.android_auto_nav_lookup_failed_body))
                        .build(),
                )
            hasSearched && !isLoading && searchResults.isEmpty() ->
                builder.addItem(
                    Row.Builder()
                        .setTitle(carContext.getString(R.string.android_auto_nav_no_results_title))
                        .addText(carContext.getString(R.string.android_auto_nav_no_results_body, currentQuery))
                        .build(),
                )
            else ->
                addDestinationRows(builder, searchResults)
        }
        return builder.build()
    }

    private fun addDestinationRows(
        builder: ItemList.Builder,
        destinations: List<AndroidAutoNavigationDestination>,
    ) {
        destinations.forEach { destination ->
            builder.addItem(
                Row.Builder()
                    .setTitle(carContext.getString(R.string.android_auto_nav_route_to_format, destination.name))
                    .apply {
                        destination.address?.takeIf { it.isNotBlank() }?.let { addText(it) }
                    }
                    .setOnClickListener { startNavigation(destination) }
                    .build(),
            )
        }
    }

    private fun NavigationSearchResultDto.toAndroidAutoDestination(): AndroidAutoNavigationDestination =
        AndroidAutoNavigationDestination(
            id = id,
            name = name,
            address = address,
            latitude = latitude,
            longitude = longitude,
            source = source,
        )

    private fun startNavigation(destination: AndroidAutoNavigationDestination) {
        if (isRouting) return
        routeJob =
            startAndroidAutoDestinationRoute(
                carContext = carContext,
                viewModel = viewModel,
                dataRepository = dataRepository,
                mapScreen = mapScreen,
                destination = destination,
                recentDestinations = recentDestinations,
                scope = scope,
                onRoutingStarted = {
                    isRouting = true
                    invalidate()
                },
                onRouteDriveStarted = {
                    carContext.getCarService(ScreenManager::class.java).popToRoot()
                },
                onRoutingFinished = {
                    isRouting = false
                    invalidate()
                },
            )
    }
}

private class OttoCarHazardPickerScreen(
    carContext: CarContext,
    private val viewModel: OttoShellViewModel,
) : Screen(carContext) {
    override fun onGetTemplate(): Template {
        val list =
            androidx.car.app.model.ItemList.Builder()
                .addHazardRow("police", R.string.map_hazard_type_police)
                .addHazardRow("traffic", R.string.map_hazard_type_traffic)
                .addHazardRow("crash", R.string.map_hazard_type_crash)
                .addHazardRow("hazard", R.string.map_hazard_type_hazard)
                .build()
        return ListTemplate.Builder()
            .setSingleList(list)
            .setTitle(carContext.getString(R.string.map_hazard_report_sheet_title))
            .setHeaderAction(Action.BACK)
            .build()
    }

    private fun androidx.car.app.model.ItemList.Builder.addHazardRow(
        type: String,
        labelRes: Int,
    ): androidx.car.app.model.ItemList.Builder =
        addItem(
            Row.Builder()
                .setTitle(carContext.getString(labelRes))
                .setOnClickListener {
                    val fix = viewModel.state.value.deviceLocationFix
                    if (fix == null) {
                        carContext.getCarService(ScreenManager::class.java).push(
                            OttoCarMessageScreen(
                                carContext = carContext,
                                title = carContext.getString(R.string.android_auto_status_location_unavailable),
                                body = carContext.getString(R.string.android_auto_location_unavailable_body),
                            ),
                        )
                    } else {
                        viewModel.reportMapHazard(type, fix.latitude, fix.longitude)
                        carContext.getCarService(ScreenManager::class.java).pop()
                    }
                }
                .build(),
        )
}

private class OttoCarMessageScreen(
    carContext: CarContext,
    private val title: String,
    private val body: String,
) : Screen(carContext) {
    override fun onGetTemplate(): Template =
        MessageTemplate.Builder(body)
            .setTitle(title)
            .setHeaderAction(Action.BACK)
            .build()
}
