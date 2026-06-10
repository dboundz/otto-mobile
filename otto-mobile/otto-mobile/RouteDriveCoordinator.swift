import CoreLocation
import AVFoundation
import Combine
import Foundation

// MARK: - Turn-by-turn voice guidance

@MainActor
final class TurnByTurnVoiceGuidance {
    private static let minimumSpokenMessageGapSeconds: TimeInterval = 3.5

    private let synthesizer = AVSpeechSynthesizer()
    private var announcedThresholds: Set<String> = []
    private var announcementDeduper = TurnByTurnAnnouncementDeduper()
    private var lastSpokenAt: Date?
    private var pendingSpeechTask: Task<Void, Never>?

    func reset() {
        announcedThresholds.removeAll()
        announcementDeduper.reset()
        pendingSpeechTask?.cancel()
        pendingSpeechTask = nil
        if synthesizer.isSpeaking {
            synthesizer.stopSpeaking(at: .immediate)
        }
    }

    func clearAnnouncedThresholds() {
        announcedThresholds.removeAll()
        announcementDeduper.reset()
        pendingSpeechTask?.cancel()
        pendingSpeechTask = nil
    }

    func stop() {
        reset()
    }

    func speakDriveStart() {
        speak(String(localized: "turn_by_turn_drive_start"))
    }

    func speakReadyWhenYouAre() {
        speak(String(localized: "turn_by_turn_ready_when_you_are"))
    }

    func speakDestinationReached() {
        speak(String(localized: "turn_by_turn_destination_reached"))
    }

    func handleGuidanceUpdate(
        stepIndex: Int,
        distanceToManeuverMeters: Double,
        voiceStep: NavigationStep?,
        maneuverStep: NavigationStep?,
        speedMps: Double,
        phase: TurnByTurnGuidanceState.Phase
    ) {
        guard phase == .navigating, let maneuverStep else { return }
        let voiceStep = voiceStep ?? maneuverStep
        for threshold in NavigationVoiceThreshold.announcementOrder {
            let key = "\(stepIndex)-\(threshold.rawValue)"
            guard !announcedThresholds.contains(key) else { continue }
            guard threshold.shouldAnnounce(distanceToManeuverMeters: distanceToManeuverMeters, speedMps: speedMps) else {
                continue
            }
            let announcement = preferredAnnouncement(
                voiceStep: voiceStep,
                maneuverStep: maneuverStep,
                threshold: threshold,
                distanceToManeuverMeters: distanceToManeuverMeters
            )
            guard !announcement.isEmpty else { continue }
            announcedThresholds.insert(key)
            guard announcementDeduper.shouldSpeak(announcement) else { continue }
            speak(announcement)
            return
        }
    }

    private func preferredAnnouncement(
        voiceStep: NavigationStep,
        maneuverStep: NavigationStep,
        threshold: NavigationVoiceThreshold,
        distanceToManeuverMeters: Double
    ) -> String {
        let voiceTolerance = max(threshold.toleranceMeters, threshold == .speedLead ? 120 : 0)
        if let mapboxVoice = voiceStep.voiceInstructions.first(where: { voice in
            abs(voice.distanceAlongStepMeters - distanceToManeuverMeters) <= voiceTolerance
                || (threshold.distanceMeters > 0 && abs(voice.distanceAlongStepMeters - threshold.distanceMeters) <= voiceTolerance)
        }) {
            return mapboxVoice.announcement
        }
        switch threshold {
        case .atManeuver:
            return maneuverStep.maneuver.instruction
        case .halfMile, .twoTenthsMile, .twoHundredFeet:
            let distanceText = TurnByTurnDistanceFormatter.formatMeters(threshold.distanceMeters)
            return "In \(distanceText), \(maneuverStep.maneuver.instruction.lowercased())"
        case .speedLead, .speedClose:
            let distanceText = TurnByTurnDistanceFormatter.formatMeters(distanceToManeuverMeters)
            return "In \(distanceText), \(maneuverStep.maneuver.instruction.lowercased())"
        }
    }

    private func speak(_ text: String) {
        pendingSpeechTask?.cancel()
        let now = Date()
        if let lastSpokenAt {
            let elapsed = now.timeIntervalSince(lastSpokenAt)
            let remaining = Self.minimumSpokenMessageGapSeconds - elapsed
            if remaining > 0 {
                pendingSpeechTask = Task { [weak self] in
                    let nanoseconds = UInt64(remaining * 1_000_000_000)
                    try? await Task.sleep(nanoseconds: nanoseconds)
                    guard !Task.isCancelled else { return }
                    await MainActor.run {
                        self?.speakImmediately(text)
                    }
                }
                return
            }
        }
        speakImmediately(text)
    }

    private func speakImmediately(_ text: String) {
        pendingSpeechTask?.cancel()
        pendingSpeechTask = nil
        lastSpokenAt = Date()
        let session = AVAudioSession.sharedInstance()
        try? session.setCategory(.playback, mode: .spokenAudio, options: [.duckOthers, .mixWithOthers])
        try? session.setActive(true)
        let utterance = AVSpeechUtterance(string: text)
        utterance.voice = AVSpeechSynthesisVoice(language: "en-US") ?? AVSpeechSynthesisVoice()
        utterance.rate = AVSpeechUtteranceDefaultSpeechRate
        synthesizer.speak(utterance)
    }
}

private struct NavigationGuidanceSelection {
    let displayIndex: Int
    let displayStep: NavigationStep
    let voiceIndex: Int
    let voiceStep: NavigationStep
    let distanceToManeuverMeters: Double
}

@MainActor
final class TurnByTurnNavigationManager: ObservableObject, NavigationGuidancePublishing {
    @Published private(set) var guidance: TurnByTurnGuidanceState?

    var onStateChange: ((TurnByTurnGuidanceState?, [CLLocationCoordinate2D]?) -> Void)?

    private let routeService: TurnByTurnRouteService
    private let voiceGuidance = TurnByTurnVoiceGuidance()
    var isVoiceGuidanceEnabled = true

    private var activeRoute: SavedRouteDTO?
    private var navigationRoute: NavigationRoute?
    private var polylineIndex: RoutePolylineIndex?
    private var flattenedSteps: [NavigationStep] = []
    private var currentStepIndex = 0
    private var routeProgressMeters: Double?
    private var offRouteTracker = TurnByTurnOffRouteTracker()
    private var completedWaypointIndexes: Set<Int> = []
    private var hasSpokenDestinationArrival = false
    private var fetchTask: Task<Void, Never>?

    init(routeService: TurnByTurnRouteService) {
        self.routeService = routeService
    }

    func speakReadyWhenYouAreNow() {
        guard isVoiceGuidanceEnabled else { return }
        voiceGuidance.speakReadyWhenYouAre()
    }

    func speakDriveStartNow() {
        guard isVoiceGuidanceEnabled else { return }
        voiceGuidance.speakDriveStart()
    }

    func speakDestinationReachedNow() {
        guard isVoiceGuidanceEnabled, !hasSpokenDestinationArrival else { return }
        hasSpokenDestinationArrival = true
        voiceGuidance.speakDestinationReached()
    }

    func start(route: SavedRouteDTO, at location: CLLocation, completedIndexes: Set<Int> = []) {
        activeRoute = route
        completedWaypointIndexes = completedIndexes
        currentStepIndex = 0
        routeProgressMeters = nil
        offRouteTracker = TurnByTurnOffRouteTracker()
        hasSpokenDestinationArrival = false
        voiceGuidance.reset()
        publish(
            TurnByTurnGuidanceState(
                phase: .loading,
                nextInstruction: String(localized: "turn_by_turn_loading"),
                nextManeuver: nil,
                distanceToManeuverMeters: 0,
                currentRoadName: nil,
                remainingDistanceMeters: 0,
                remainingDurationSeconds: 0,
                eta: Date(),
                currentStepIndex: 0,
                totalSteps: 0
            ),
            lineCoordinates: nil
        )
        fetchTask?.cancel()
        fetchTask = Task { [weak self] in
            await self?.loadRoute(at: location)
        }
    }

    func recalculate(from location: CLLocation) {
        guard let route = activeRoute else { return }
        start(route: route, at: location, completedIndexes: completedWaypointIndexes)
    }

    func updateCompletedWaypointIndexes(_ indexes: Set<Int>) {
        completedWaypointIndexes = indexes
    }

    func update(location: CLLocation, speedMps: Double) {
        guard let navigationRoute, let polylineIndex else { return }
        guard guidance?.phase == .navigating || guidance?.phase == .offRoute else { return }
        if let distanceToDestination = distanceToFinalDestinationMeters(from: location, route: navigationRoute),
           distanceToDestination <= TurnByTurnNavigationConstants.arrivalDistanceMeters {
            publish(makeArrivedState(route: navigationRoute), lineCoordinates: navigationRoute.coordinates)
            return
        }
        let projection = polylineIndex.projectOntoPolyline(
            location.coordinate,
            preferredArcLength: routeProgressMeters,
            searchWindowMeters: 350
        ) ?? polylineIndex.projectOntoPolyline(location.coordinate)
        let lateralDistance = projection?.distanceMeters ?? .greatestFiniteMagnitude
        let isOffRoute = offRouteTracker.recordSample(lateralDistanceMeters: lateralDistance)
        if isOffRoute {
            publish(
                makeGuidanceState(
                    route: navigationRoute,
                    progressMeters: routeProgressMeters ?? 0,
                    phase: .offRoute,
                    speedMps: speedMps,
                    currentLocation: location
                ),
                lineCoordinates: navigationRoute.coordinates
            )
            return
        }
        if let projection {
            routeProgressMeters = max(routeProgressMeters ?? 0, projection.arcLengthMeters)
        }
        advanceStepIfNeeded(location: location)
        let progress = routeProgressMeters ?? 0
        let navigatingState = makeGuidanceState(
            route: navigationRoute,
            progressMeters: progress,
            phase: .navigating,
            speedMps: speedMps,
            currentLocation: location
        )
        publish(navigatingState, lineCoordinates: navigationRoute.coordinates)
        if isVoiceGuidanceEnabled {
            let selection = guidanceSelection(progressMeters: progress, currentLocation: location)
            voiceGuidance.handleGuidanceUpdate(
                stepIndex: selection?.displayIndex ?? currentStepIndex,
                distanceToManeuverMeters: navigatingState.distanceToManeuverMeters,
                voiceStep: selection?.voiceStep,
                maneuverStep: selection?.displayStep,
                speedMps: speedMps,
                phase: .navigating
            )
        }
    }

    func applyNavigationRouteForTesting(_ route: NavigationRoute, savedRoute: SavedRouteDTO, initialLocation: CLLocation? = nil) {
        isVoiceGuidanceEnabled = false
        activeRoute = savedRoute
        navigationRoute = route
        polylineIndex = route.polylineIndex
        flattenedSteps = route.flattenedSteps
        currentStepIndex = 0
        routeProgressMeters = nil
        offRouteTracker = TurnByTurnOffRouteTracker()
        publish(makeGuidanceState(route: route, progressMeters: 0, phase: .navigating, speedMps: 0, currentLocation: initialLocation), lineCoordinates: route.coordinates)
    }

    func stop() {
        fetchTask?.cancel()
        fetchTask = nil
        voiceGuidance.stop()
        activeRoute = nil
        navigationRoute = nil
        polylineIndex = nil
        flattenedSteps = []
        currentStepIndex = 0
        routeProgressMeters = nil
        offRouteTracker = TurnByTurnOffRouteTracker()
        completedWaypointIndexes = []
        hasSpokenDestinationArrival = false
        publish(nil, lineCoordinates: nil)
    }

    private func loadRoute(at location: CLLocation) async {
        guard let route = activeRoute else { return }
        let waypoints = NavigationRouteWaypointBuilder.waypoints(for: route, at: location, completedIndexes: completedWaypointIndexes)
        do {
            let fetched = try await routeService.fetchRoute(waypoints: waypoints)
            guard !Task.isCancelled else { return }
            navigationRoute = fetched
            polylineIndex = fetched.polylineIndex
            flattenedSteps = fetched.flattenedSteps
            currentStepIndex = 0
            routeProgressMeters = nil
            offRouteTracker = TurnByTurnOffRouteTracker()
            voiceGuidance.clearAnnouncedThresholds()
            publish(
                makeGuidanceState(route: fetched, progressMeters: 0, phase: .navigating, speedMps: 0, currentLocation: location),
                lineCoordinates: fetched.coordinates
            )
        } catch {
            guard !Task.isCancelled else { return }
            let message = (error as? LocalizedError)?.errorDescription ?? error.localizedDescription
            publish(
                TurnByTurnGuidanceState(
                    phase: .failed(message),
                    nextInstruction: String(localized: "turn_by_turn_failed"),
                    nextManeuver: nil,
                    distanceToManeuverMeters: 0,
                    currentRoadName: nil,
                    remainingDistanceMeters: 0,
                    remainingDurationSeconds: 0,
                    eta: Date(),
                    currentStepIndex: 0,
                    totalSteps: 0
                ),
                lineCoordinates: nil
            )
        }
    }

    private func advanceStepIfNeeded(location: CLLocation) {
        guard !flattenedSteps.isEmpty else { return }
        let progress = routeProgressMeters ?? 0
        while currentStepIndex < flattenedSteps.count - 1 {
            let step = flattenedSteps[currentStepIndex]
            let nextStep = flattenedSteps[currentStepIndex + 1]
            let maneuverLocation = CLLocation(latitude: nextStep.maneuverCoordinate.latitude, longitude: nextStep.maneuverCoordinate.longitude)
            let passedManeuver = progress >= nextStep.maneuverArcLengthMeters - 10
            let closeEnough = location.distance(from: maneuverLocation) <= TurnByTurnNavigationConstants.stepAdvanceDistanceMeters
            if passedManeuver || closeEnough {
                currentStepIndex += 1
                continue
            }
            if progress >= step.maneuverArcLengthMeters + step.distanceMeters - TurnByTurnNavigationConstants.stepAdvanceDistanceMeters {
                currentStepIndex += 1
                continue
            }
            break
        }
    }

    private func guidanceSelection(progressMeters: Double, currentLocation: CLLocation?) -> NavigationGuidanceSelection? {
        guard !flattenedSteps.isEmpty else { return nil }
        let startIndex = min(currentStepIndex + 1, flattenedSteps.count - 1)
        let currentRoad = currentRoadStep()
        for index in startIndex..<flattenedSteps.count {
            let step = flattenedSteps[index]
            if shouldSuppressArriveStep(step, index: index, currentLocation: currentLocation, currentRoadStep: currentRoad, progressMeters: progressMeters) {
                continue
            }
            let voiceIndex = max(0, index - 1)
            let approachStep = flattenedSteps[voiceIndex]
            return NavigationGuidanceSelection(
                displayIndex: index,
                displayStep: step,
                voiceIndex: voiceIndex,
                voiceStep: approachStep.voiceInstructions.isEmpty ? step : approachStep,
                distanceToManeuverMeters: max(0, step.maneuverArcLengthMeters - progressMeters)
            )
        }
        return nil
    }

    private func currentRoadStep() -> NavigationStep? {
        guard !flattenedSteps.isEmpty else { return nil }
        return flattenedSteps[min(currentStepIndex, flattenedSteps.count - 1)]
    }

    private func displayCurrentRoadStep(currentLocation: CLLocation?, progressMeters: Double) -> NavigationStep? {
        guard let step = currentRoadStep(), !flattenedSteps.isEmpty else { return nil }
        let index = min(currentStepIndex, flattenedSteps.count - 1)
        return shouldSuppressArriveStep(step, index: index, currentLocation: currentLocation, currentRoadStep: step, progressMeters: progressMeters) ? nil : step
    }

    private func shouldSuppressArriveStep(
        _ step: NavigationStep,
        index: Int,
        currentLocation: CLLocation?,
        currentRoadStep: NavigationStep?,
        progressMeters: Double
    ) -> Bool {
        let distance = currentLocation.flatMap { location in
            navigationRoute.flatMap { route in distanceToFinalDestinationMeters(from: location, route: route) }
        }
        return step.shouldSuppressArrivePresentation(
            stepIndex: index,
            totalSteps: flattenedSteps.count,
            distanceToFinalDestinationMeters: distance,
            currentRoadName: currentRoadStep?.name,
            hasPassedFinalTurn: hasPassedFinalTurnBeforeArrival(arrivalStepIndex: index, progressMeters: progressMeters)
        )
    }

    private func hasPassedFinalTurnBeforeArrival(arrivalStepIndex: Int, progressMeters: Double) -> Bool {
        guard flattenedSteps.indices.contains(arrivalStepIndex),
              flattenedSteps[arrivalStepIndex].maneuver.type.lowercased() == "arrive" else {
            return true
        }
        guard let finalTurnIndex = (0..<arrivalStepIndex).last(where: { index in
            let type = flattenedSteps[index].maneuver.type.lowercased()
            return type != "arrive" && type != "depart"
        }) else {
            return true
        }
        return progressMeters >= flattenedSteps[finalTurnIndex].maneuverArcLengthMeters
            + TurnByTurnNavigationConstants.postTurnArrivalPresentationDistanceMeters
    }

    private func makeGuidanceState(
        route: NavigationRoute,
        progressMeters: Double,
        phase: TurnByTurnGuidanceState.Phase,
        speedMps: Double,
        currentLocation: CLLocation? = nil
    ) -> TurnByTurnGuidanceState {
        let totalDistance = max(route.totalDistanceMeters, 1)
        let remainingDistance = max(0, totalDistance - progressMeters)
        let progressRatio = min(1, max(0, progressMeters / totalDistance))
        var remainingDuration = max(0, route.totalDurationSeconds * (1 - progressRatio))
        if speedMps > 1.5, remainingDistance > 0 {
            remainingDuration = min(remainingDuration, (remainingDistance / speedMps) * 1.15)
        }
        let selection = guidanceSelection(progressMeters: progressMeters, currentLocation: currentLocation)
        let currentStep = displayCurrentRoadStep(currentLocation: currentLocation, progressMeters: progressMeters)
        let distanceToManeuver = selection?.distanceToManeuverMeters
            ?? currentLocation.flatMap { distanceToFinalDestinationMeters(from: $0, route: route) }
            ?? remainingDistance
        return TurnByTurnGuidanceState(
            phase: phase,
            nextInstruction: selection?.displayStep.instruction ?? currentStep?.instruction ?? String(localized: "turn_by_turn_continue"),
            nextManeuver: selection?.displayStep.maneuver,
            distanceToManeuverMeters: distanceToManeuver,
            currentRoadName: currentStep?.name,
            remainingDistanceMeters: remainingDistance,
            remainingDurationSeconds: remainingDuration,
            eta: Date().addingTimeInterval(remainingDuration),
            currentStepIndex: currentStepIndex,
            totalSteps: flattenedSteps.count
        )
    }

    private func makeArrivedState(route: NavigationRoute) -> TurnByTurnGuidanceState {
        TurnByTurnGuidanceState(
            phase: .arrived,
            nextInstruction: String(localized: "turn_by_turn_destination_reached"),
            nextManeuver: NavigationManeuver(type: "arrive", modifier: nil, instruction: String(localized: "turn_by_turn_destination_reached")),
            distanceToManeuverMeters: 0,
            currentRoadName: nil,
            remainingDistanceMeters: 0,
            remainingDurationSeconds: 0,
            eta: Date(),
            currentStepIndex: max(0, flattenedSteps.count - 1),
            totalSteps: flattenedSteps.count
        )
    }

    private func finalDestinationCoordinate(route: NavigationRoute) -> CLLocationCoordinate2D? {
        if let finish = activeRoute?.points.last(where: { ($0.markerType?.lowercased() ?? "") == "finish" }) {
            let coordinate = CLLocationCoordinate2D(latitude: finish.lat, longitude: finish.lng)
            if CLLocationCoordinate2DIsValid(coordinate), finish.lat.isFinite, finish.lng.isFinite { return coordinate }
        }
        return route.finishCoordinate
    }

    private func distanceToFinalDestinationMeters(from location: CLLocation, route: NavigationRoute) -> Double? {
        guard let coordinate = finalDestinationCoordinate(route: route) else { return nil }
        return location.distance(from: CLLocation(latitude: coordinate.latitude, longitude: coordinate.longitude))
    }

    private func publish(_ state: TurnByTurnGuidanceState?, lineCoordinates: [CLLocationCoordinate2D]?) {
        guidance = state
        onStateChange?(state, lineCoordinates)
    }
}

enum NavigationRouteWaypointBuilder {
    static func waypoints(
        for route: SavedRouteDTO,
        at location: CLLocation,
        completedIndexes: Set<Int> = []
    ) -> [CLLocationCoordinate2D] {
        var waypoints = [location.coordinate]
        for (index, point) in route.points.enumerated() {
            let type = point.markerType?.lowercased() ?? ""
            // Checkpoints (waypoint) and path shaping points are not navigation destinations.
            guard type == "stop" || type == "finish" else { continue }
            guard type != "stop" || !completedIndexes.contains(index) else { continue }
            let coordinate = CLLocationCoordinate2D(latitude: point.lat, longitude: point.lng)
            guard CLLocationCoordinate2DIsValid(coordinate), point.lat.isFinite, point.lng.isFinite else { continue }
            waypoints.append(coordinate)
        }
        if waypoints.count < 2, let fallback = route.toMapLineCoordinates().last {
            waypoints.append(fallback)
        }
        return waypoints
    }
}

// MARK: - Route drive lifecycle (global GPS ingestion)

extension AppState {
    func clearRouteDriveSessionState() {
        turnByTurnNavigationManager.stop()
        activeRouteDriveSession = nil
        activeRouteDriveRoute = nil
        resetRouteDrivePathSamples()
        isActivatingRouteDriveSession = false
        lastRouteDriveProgressWriteAt = .distantPast
        routeDriveProgressTask?.cancel()
        routeDriveProgressTask = nil
    }

    func ingestRouteDriveLocationSample(
        location: CLLocation,
        speedMetersPerSecond: Double,
        movementMode: FriendMovementMode
    ) async {
        guard let route = activeRouteDriveRoute,
              let session = activeRouteDriveSession,
              session.activeRouteId == route.id else { return }

        let speedMps = max(speedMetersPerSecond, location.speed >= 0 ? location.speed : 0, 0)

        if session.isArmed {
            guard !isActivatingRouteDriveSession else { return }
            guard RouteCheckpointDetector.indicatesDriveMovement(
                location: location,
                speedMetersPerSecond: speedMps,
                movementMode: movementMode
            ) else { return }
            await activateRouteDriveSession(route: route, location: location, speedMps: speedMps)
            return
        }

        guard session.isActive else { return }
        await updateActiveRouteDriveSession(
            route: route,
            location: location,
            speedMps: speedMps,
            forceWrite: false
        )
    }

    @MainActor
    func stopRouteDriveSession(location: CLLocation?) async -> DriveCompleteSummary? {
        guard let session = activeRouteDriveSession else { return nil }
        let route = activeRouteDriveRoute
        routeDriveProgressTask?.cancel()
        isActivatingRouteDriveSession = false

        let speedMph = max(location?.speed ?? 0, 0) * 2.23694
        let completed = Array(session.completedWaypointIndexes).sorted()
        let endedDto = try? await APIClient.shared.stopRouteDriveSession(
            sessionId: session.sessionId,
            location: location,
            speedMph: speedMph,
            completedWaypointIndexes: completed,
            currentProgress: session.currentProgress,
            lastTriggeredWaypointIndex: session.lastTriggeredWaypointIndex
        )

        let summary: DriveCompleteSummary?
        if let route {
            summary = buildRouteDriveCompleteSummary(
                route: route,
                session: session,
                driveId: endedDto?.driveId ?? session.driveId,
                completedIndexes: session.completedWaypointIndexes,
                endedAt: Date(),
                reason: "stopped"
            )
        } else {
            summary = nil
        }

        clearRouteDriveSessionState()
        activeDriveSession = nil
        await refreshRecentDrives()

        if let summary {
            routeDriveFeedbackEvent = RouteDriveFeedbackEvent(kind: .stopped(summary: summary))
        }
        return summary
    }

    // MARK: - Private

    private func activateRouteDriveSession(
        route: SavedRouteDTO,
        location: CLLocation,
        speedMps: Double
    ) async {
        guard let session = activeRouteDriveSession else { return }
        guard !isActivatingRouteDriveSession else { return }
        isActivatingRouteDriveSession = true
        routeDriveProgressTask?.cancel()

        let speedMph = speedMps * 2.23694
        do {
            let dto = try await APIClient.shared.activateRouteDriveSession(
                sessionId: session.sessionId,
                location: location,
                speedMph: speedMph,
                garageCarId: selectedSharingCarID
            )
            await MainActor.run {
                var state = RouteDriveSessionState(dto: dto, routeId: route.id, currentLocation: location)
                state.driveId = dto.driveId ?? state.driveId
                applyStartCheckpointIfNeeded(to: &state, route: route, location: location)
                activeRouteDriveSession = state
                recordLocalRouteDriveSpeedSample(location: location, speedMph: speedMph)
                setRouteDrivePathSamples([DrivePathSample(location: location, speedMph: speedMph)])
                routeDriveFeedbackEvent = RouteDriveFeedbackEvent(kind: .activated)
                isActivatingRouteDriveSession = false
            }
            turnByTurnNavigationManager.start(
                route: route,
                at: location,
                completedIndexes: activeRouteDriveSession?.completedWaypointIndexes ?? []
            )
            turnByTurnNavigationManager.speakDriveStartNow()
            await updateActiveRouteDriveSession(
                route: route,
                location: location,
                speedMps: speedMps,
                forceWrite: true
            )
        } catch {
            await MainActor.run {
                isActivatingRouteDriveSession = false
                routeDriveFeedbackEvent = RouteDriveFeedbackEvent(kind: .activationFailed)
            }
        }
    }

    private func updateActiveRouteDriveSession(
        route: SavedRouteDTO,
        location: CLLocation,
        speedMps: Double,
        forceWrite: Bool
    ) async {
        guard var session = activeRouteDriveSession, session.isActive else { return }
        turnByTurnNavigationManager.updateCompletedWaypointIndexes(session.completedWaypointIndexes)
        turnByTurnNavigationManager.update(location: location, speedMps: speedMps)

        let detection = RouteCheckpointDetector.evaluate(
            routePoints: route.points,
            roadCoordinates: route.toMapLineCoordinates(),
            location: location,
            previousLocation: session.currentLocation ?? session.previousRouteDriveLocation,
            speedMetersPerSecond: speedMps,
            completedIndexes: session.completedWaypointIndexes,
            lastRouteProgressMeters: session.lastRouteProgressMeters
        )
        let speedMph = speedMps * 2.23694
        let hadTrigger = !detection.newlyTriggeredIndexes.isEmpty

        session.completedWaypointIndexes = detection.completedIndexes
        session.currentProgress = detection.currentProgress
        session.previousRouteDriveLocation = session.currentLocation
        session.currentLocation = location
        session.currentSpeedMph = speedMph
        session.maxSpeedMph = max(session.maxSpeedMph, speedMph)
        session.speedSampleCount += 1
        let previousSampleTotal = session.avgSpeedMph * Double(max(0, session.speedSampleCount - 1))
        session.avgSpeedMph = (previousSampleTotal + speedMph) / Double(max(1, session.speedSampleCount))
        session.lastTriggeredWaypointIndex = detection.lastTriggeredWaypointIndex
        session.lastRouteProgressMeters = detection.updatedRouteProgressMeters
        activeRouteDriveSession = session

        let checkpointTotal = RouteCheckpointDetector.routeCheckpointTotal(pointCount: route.points.count)
        syncRouteProgress(from: session, routeName: route.name, totalCheckpoints: checkpointTotal)
        appendRouteDrivePathSample(location: location, speedMph: speedMph)

        if hadTrigger && !detection.didTriggerFinalWaypoint {
            routeDriveFeedbackEvent = RouteDriveFeedbackEvent(
                kind: .checkpointReached(isFinish: detection.didTriggerFinalWaypoint)
            )
        }

        if detection.didTriggerFinalWaypoint {
            await completeRouteDriveSession(
                route: route,
                location: location,
                speedMph: speedMph,
                detection: detection
            )
            return
        }

        let shouldWrite = forceWrite || hadTrigger || Date().timeIntervalSince(lastRouteDriveProgressWriteAt) >= 5
        guard shouldWrite else { return }
        lastRouteDriveProgressWriteAt = Date()

        let sessionId = session.sessionId
        routeDriveProgressTask = Task {
            _ = try? await APIClient.shared.updateRouteDriveSessionProgress(
                sessionId: sessionId,
                location: location,
                speedMph: speedMph,
                completedWaypointIndexes: Array(detection.completedIndexes).sorted(),
                currentProgress: detection.currentProgress,
                lastTriggeredWaypointIndex: detection.lastTriggeredWaypointIndex,
                nearestRouteIndex: detection.nearestRouteIndex
            )
        }
    }

    private func completeRouteDriveSession(
        route: SavedRouteDTO,
        location: CLLocation,
        speedMph: Double,
        detection: RouteCheckpointDetectionResult
    ) async {
        guard let session = activeRouteDriveSession else { return }
        routeDriveProgressTask?.cancel()
        let completedIndexes = detection.completedIndexes
        let completedIndexesArray = Array(completedIndexes).sorted()
        let lastTriggeredWaypointIndex = detection.lastTriggeredWaypointIndex

        let summary = buildRouteDriveCompleteSummary(
            route: route,
            session: session,
            driveId: session.driveId,
            completedIndexes: completedIndexes,
            endedAt: Date(),
            reason: "completed"
        )

        await MainActor.run {
            clearRouteDriveSessionState()
            activeDriveSession = nil
            turnByTurnNavigationManager.speakDestinationReachedNow()
            routeDriveFeedbackEvent = RouteDriveFeedbackEvent(kind: .completed(summary: summary))
        }

        _ = try? await APIClient.shared.completeRouteDriveSession(
            sessionId: session.sessionId,
            location: location,
            speedMph: speedMph,
            completedWaypointIndexes: completedIndexesArray,
            currentProgress: 1,
            lastTriggeredWaypointIndex: lastTriggeredWaypointIndex
        )
        await refreshRecentDrives()
    }

    private func appendRouteDrivePathSample(location: CLLocation, speedMph: Double) {
        recordRouteDrivePathSample(location: location, speedMph: speedMph)
    }

    private func recordLocalRouteDriveSpeedSample(location: CLLocation, speedMph: Double) {
        guard var session = activeRouteDriveSession else { return }
        session.currentLocation = location
        session.currentSpeedMph = speedMph
        session.maxSpeedMph = max(session.maxSpeedMph, speedMph)
        if speedMph > 0 {
            session.speedSampleCount += 1
            let previousSampleTotal = session.avgSpeedMph * Double(max(0, session.speedSampleCount - 1))
            session.avgSpeedMph = (previousSampleTotal + speedMph) / Double(max(1, session.speedSampleCount))
        }
        activeRouteDriveSession = session
    }

    func buildRouteDriveCompleteSummary(
        route: SavedRouteDTO,
        session: RouteDriveSessionState,
        driveId: String?,
        completedIndexes: Set<Int>,
        endedAt: Date,
        reason: String
    ) -> DriveCompleteSummary {
        let startedAt = session.startedAt ?? endedAt
        let driveTime = max(0, endedAt.timeIntervalSince(startedAt))
        let averageSpeed = session.avgSpeedMph > 0 ? session.avgSpeedMph : session.currentSpeedMph
        let routeCoordinates = route.toMapLineCoordinates()
        let checkpointCoordinates = route.points.compactMap { point -> CLLocationCoordinate2D? in
            let coordinate = CLLocationCoordinate2D(latitude: point.lat, longitude: point.lng)
            guard CLLocationCoordinate2DIsValid(coordinate), point.lat.isFinite, point.lng.isFinite else { return nil }
            return coordinate
        }
        return DriveCompleteSummary(
            driveId: driveId,
            routeName: route.name,
            routeCoordinates: routeCoordinates,
            checkpointCoordinates: checkpointCoordinates,
            distanceMeters: route.distanceMeters,
            driveTimeSeconds: driveTime,
            averageSpeedMph: averageSpeed,
            maxSpeedMph: session.maxSpeedMph,
            completedCheckpoints: completedIndexes.count,
            totalCheckpoints: route.points.count,
            completionReason: reason
        )
    }

    func applyStartCheckpointIfNeeded(
        to state: inout RouteDriveSessionState,
        route: SavedRouteDTO,
        location: CLLocation?
    ) {
        guard !route.points.isEmpty, !state.completedWaypointIndexes.contains(0) else { return }
        guard let location, let startCoordinate = routeStartCoordinate(route) else { return }
        let start = CLLocation(latitude: startCoordinate.latitude, longitude: startCoordinate.longitude)
        guard location.distance(from: start) <= Self.routeStartDriveRangeMeters else { return }
        state.completedWaypointIndexes.insert(0)
        state.currentProgress = Double(state.completedWaypointIndexes.count) / Double(max(route.points.count, 1))
        state.lastTriggeredWaypointIndex = max(state.lastTriggeredWaypointIndex ?? 0, 0)
    }

    func routeStartCoordinate(_ route: SavedRouteDTO) -> CLLocationCoordinate2D? {
        if let explicitStart = route.points.first(where: { $0.markerType == "start" }) {
            let coordinate = CLLocationCoordinate2D(latitude: explicitStart.lat, longitude: explicitStart.lng)
            guard CLLocationCoordinate2DIsValid(coordinate) else { return nil }
            return coordinate
        }
        guard let first = route.points.first else { return nil }
        let coordinate = CLLocationCoordinate2D(latitude: first.lat, longitude: first.lng)
        guard CLLocationCoordinate2DIsValid(coordinate) else { return nil }
        return coordinate
    }

    func isWithinRouteStartDriveRange(_ route: SavedRouteDTO, currentLocation: CLLocation?) -> Bool {
        guard
            let startCoordinate = routeStartCoordinate(route),
            let currentLocation
        else { return false }
        let start = CLLocation(latitude: startCoordinate.latitude, longitude: startCoordinate.longitude)
        return currentLocation.distance(from: start) <= Self.routeStartDriveRangeMeters
    }
}
