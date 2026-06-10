import CoreLocation
import XCTest
@testable import otto_mobile

@MainActor
final class TurnByTurnNavigationTests: XCTestCase {
    func testParseDirectionsResponse() throws {
        let data = try loadFixture(named: "mapbox-directions-sample")
        let finish = CLLocationCoordinate2D(latitude: 37.7779, longitude: -122.4164)
        let route = try TurnByTurnRouteService.parseResponse(data: data, fallbackFinish: finish)

        XCTAssertEqual(route.coordinates.count, 4)
        XCTAssertEqual(route.totalDistanceMeters, 1250.5, accuracy: 0.1)
        XCTAssertEqual(route.totalDurationSeconds, 180.2, accuracy: 0.1)
        XCTAssertEqual(route.flattenedSteps.count, 3)
        XCTAssertEqual(route.flattenedSteps[1].maneuver.type, "turn")
        XCTAssertEqual(route.flattenedSteps[1].maneuver.modifier, "right")
        XCTAssertEqual(route.flattenedSteps[0].voiceInstructions.count, 2)
        XCTAssertEqual(
            route.flattenedSteps[0].voiceInstructions[0].announcement,
            "In a half mile, turn right onto Oak Street"
        )
    }

    func testWaypointExtraction() {
        let route = SavedRouteDTO(
            id: "route-1",
            createdByUserId: "user-1",
            name: "Test Route",
            points: [
                RoutePointDTO(lat: 37.7700, lng: -122.4200, markerType: "start"),
                RoutePointDTO(lat: 37.7720, lng: -122.4190, markerType: "waypoint"),
                RoutePointDTO(lat: 37.7740, lng: -122.4180, markerType: "stop"),
                RoutePointDTO(lat: 37.7779, lng: -122.4164, markerType: "finish")
            ],
            roadCoordinates: [],
            distanceMeters: 1000,
            etaSeconds: 120,
            createdAt: nil,
            updatedAt: nil
        )

        let location = CLLocation(latitude: 37.7749, longitude: -122.4194)
        let waypoints = NavigationRouteWaypointBuilder.waypoints(for: route, at: location)

        XCTAssertEqual(waypoints.count, 3)
        XCTAssertEqual(waypoints[0].latitude, 37.7749, accuracy: 0.0001)
        XCTAssertEqual(waypoints[1].latitude, 37.7740, accuracy: 0.0001)
        XCTAssertEqual(waypoints[2].latitude, 37.7779, accuracy: 0.0001)
    }

    func testWaypointExtractionIgnoresCheckpointsAndPathPoints() {
        let route = SavedRouteDTO(
            id: "route-3",
            createdByUserId: "user-1",
            name: "Test Route",
            points: [
                RoutePointDTO(lat: 37.7700, lng: -122.4200, markerType: "start"),
                RoutePointDTO(lat: 37.7710, lng: -122.4195, markerType: "path"),
                RoutePointDTO(lat: 37.7720, lng: -122.4190, markerType: "waypoint"),
                RoutePointDTO(lat: 37.7779, lng: -122.4164, markerType: "finish")
            ],
            roadCoordinates: [],
            distanceMeters: 1000,
            etaSeconds: 120,
            createdAt: nil,
            updatedAt: nil
        )

        let location = CLLocation(latitude: 37.7749, longitude: -122.4194)
        let waypoints = NavigationRouteWaypointBuilder.waypoints(for: route, at: location)

        XCTAssertEqual(waypoints.count, 2)
        XCTAssertEqual(waypoints.last?.latitude ?? 0, 37.7779, accuracy: 0.0001)
    }

    func testStopPointInstructionRelabeling() {
        XCTAssertEqual(
            NavigationInstructionLabeling.relabeledForStopPoint("Your destination is on the right"),
            "your Stop Point is on the right"
        )
        XCTAssertEqual(
            NavigationInstructionLabeling.relabeledForStopPoint("In 200 feet, your destination will be on the left"),
            "In 200 feet, your Stop Point will be on the left"
        )
        XCTAssertEqual(
            NavigationInstructionLabeling.relabeledForStopPoint("Continue to destination"),
            "Continue to Stop Point"
        )
    }

    func testIntermediateLegRelabelsDestinationToStopPoint() {
        let arriveStep = NavigationStep(
            instruction: "Your destination is on the right",
            name: "Oak Street",
            distanceMeters: 0,
            durationSeconds: 0,
            maneuver: NavigationManeuver(
                type: "arrive",
                modifier: "right",
                instruction: "Your destination is on the right"
            ),
            maneuverCoordinate: CLLocationCoordinate2D(latitude: 37.7740, longitude: -122.4180),
            voiceInstructions: [
                NavigationVoiceInstruction(
                    distanceAlongStepMeters: 60,
                    announcement: "Your destination is on the right"
                )
            ],
            geometryCoordinates: [],
            maneuverArcLengthMeters: 500
        )
        let approachStep = NavigationStep(
            instruction: "Turn right onto Oak Street",
            name: "Oak Street",
            distanceMeters: 500,
            durationSeconds: 60,
            maneuver: NavigationManeuver(type: "turn", modifier: "right", instruction: "Turn right onto Oak Street"),
            maneuverCoordinate: CLLocationCoordinate2D(latitude: 37.7720, longitude: -122.4190),
            voiceInstructions: [
                NavigationVoiceInstruction(
                    distanceAlongStepMeters: 60,
                    announcement: "In 200 feet, your destination will be on the right"
                )
            ],
            geometryCoordinates: [],
            maneuverArcLengthMeters: 0
        )
        let finishArriveStep = NavigationStep(
            instruction: "Your destination is on the right",
            name: "Pine Street",
            distanceMeters: 0,
            durationSeconds: 0,
            maneuver: NavigationManeuver(
                type: "arrive",
                modifier: "right",
                instruction: "Your destination is on the right"
            ),
            maneuverCoordinate: CLLocationCoordinate2D(latitude: 37.7779, longitude: -122.4164),
            voiceInstructions: [],
            geometryCoordinates: [],
            maneuverArcLengthMeters: 1_000
        )
        let legs = [
            NavigationLeg(steps: [approachStep, arriveStep], distanceMeters: 500, durationSeconds: 60),
            NavigationLeg(steps: [finishArriveStep], distanceMeters: 500, durationSeconds: 60),
        ]

        let relabeled = NavigationInstructionLabeling.relabelLegsForStopPoints(legs)
        let stopArrive = relabeled[0].steps.last
        let finishArrive = relabeled[1].steps.last

        XCTAssertEqual(stopArrive?.maneuver.instruction, "your Stop Point is on the right")
        XCTAssertEqual(
            relabeled[0].steps[0].voiceInstructions.first?.announcement,
            "In 200 feet, your Stop Point will be on the right"
        )
        XCTAssertEqual(finishArrive?.maneuver.instruction, "Your destination is on the right")
    }

    func testFinalLegKeepsDestinationCopy() throws {
        let data = try loadFixture(named: "mapbox-directions-sample")
        let finish = CLLocationCoordinate2D(latitude: 37.7779, longitude: -122.4164)
        let route = try TurnByTurnRouteService.parseResponse(data: data, fallbackFinish: finish)
        let arriveStep = route.flattenedSteps.last

        XCTAssertEqual(arriveStep?.maneuver.instruction, "Your destination is on the right")
    }

    func testWaypointExtractionSkipsCompletedStops() {
        let route = SavedRouteDTO(
            id: "route-2",
            createdByUserId: "user-1",
            name: "Test Route",
            points: [
                RoutePointDTO(lat: 37.7700, lng: -122.4200, markerType: "start"),
                RoutePointDTO(lat: 37.7720, lng: -122.4190, markerType: "stop"),
                RoutePointDTO(lat: 37.7779, lng: -122.4164, markerType: "finish")
            ],
            roadCoordinates: [],
            distanceMeters: 1000,
            etaSeconds: 120,
            createdAt: nil,
            updatedAt: nil
        )

        let location = CLLocation(latitude: 37.7749, longitude: -122.4194)
        let waypoints = NavigationRouteWaypointBuilder.waypoints(
            for: route,
            at: location,
            completedIndexes: [1]
        )

        XCTAssertEqual(waypoints.count, 2)
        XCTAssertEqual(waypoints.last?.latitude ?? 0, 37.7779, accuracy: 0.0001)
    }

    func testStepManeuverArcLengthsIncrease() throws {
        let data = try loadFixture(named: "mapbox-directions-sample")
        let finish = CLLocationCoordinate2D(latitude: 37.7779, longitude: -122.4164)
        let navigationRoute = try TurnByTurnRouteService.parseResponse(data: data, fallbackFinish: finish)
        let steps = navigationRoute.flattenedSteps

        XCTAssertGreaterThan(steps.count, 1)
        for pair in zip(steps, steps.dropFirst()) {
            XCTAssertLessThanOrEqual(pair.0.maneuverArcLengthMeters, pair.1.maneuverArcLengthMeters)
        }
    }

    func testApproachStepVoiceInstructionAnnouncesUpcomingTurn() throws {
        let data = try loadFixture(named: "mapbox-directions-sample")
        let finish = CLLocationCoordinate2D(latitude: 37.7779, longitude: -122.4164)
        let navigationRoute = try TurnByTurnRouteService.parseResponse(data: data, fallbackFinish: finish)
        let steps = navigationRoute.flattenedSteps

        XCTAssertEqual(steps[1].maneuver.type, "turn")
        XCTAssertEqual(steps[1].maneuver.modifier, "right")
        XCTAssertTrue(steps[0].voiceInstructions.contains { voice in
            voice.announcement == "In a half mile, turn right onto Oak Street"
        })
    }

    func testOffRouteDetection() {
        var tracker = TurnByTurnOffRouteTracker()
        XCTAssertFalse(tracker.recordSample(lateralDistanceMeters: 30))
        XCTAssertFalse(tracker.recordSample(lateralDistanceMeters: 80))
        XCTAssertFalse(tracker.recordSample(lateralDistanceMeters: 80))
        XCTAssertFalse(tracker.recordSample(lateralDistanceMeters: 80))
        XCTAssertTrue(tracker.recordSample(lateralDistanceMeters: 80))
        XCTAssertFalse(tracker.recordSample(lateralDistanceMeters: 20))
    }

    func testOnRouteDensePolylineStaysOnRouteAndNearUpcomingTurn() {
        let coordinates = denseRouteWithRightTurn()
        let polylineIndex = RoutePolylineIndex(lineCoordinates: coordinates)
        let turnCoordinate = coordinates[340]
        let turnArcLength = polylineIndex.projectOntoPolyline(turnCoordinate)?.arcLengthMeters ?? 0
        let beforeTurn = RouteMapGeometry.coordinateAtArcLength(turnArcLength - 45, on: coordinates) ?? coordinates[330]

        let route = NavigationRoute(
            coordinates: coordinates,
            legs: [
                NavigationLeg(
                    steps: [
                        NavigationStep(
                            instruction: "Drive east on Deer Haven Drive.",
                            name: "Deer Haven Drive",
                            distanceMeters: turnArcLength,
                            durationSeconds: 60,
                            maneuver: NavigationManeuver(
                                type: "depart",
                                modifier: nil,
                                instruction: "Drive east on Deer Haven Drive."
                            ),
                            maneuverCoordinate: coordinates[0],
                            voiceInstructions: [],
                            geometryCoordinates: Array(coordinates[0...340]),
                            maneuverArcLengthMeters: 0
                        ),
                        NavigationStep(
                            instruction: "Turn right onto Buck Meadow Drive.",
                            name: "Buck Meadow Drive",
                            distanceMeters: 300,
                            durationSeconds: 40,
                            maneuver: NavigationManeuver(
                                type: "turn",
                                modifier: "right",
                                instruction: "Turn right onto Buck Meadow Drive."
                            ),
                            maneuverCoordinate: turnCoordinate,
                            voiceInstructions: [],
                            geometryCoordinates: Array(coordinates[340...]),
                            maneuverArcLengthMeters: turnArcLength
                        )
                    ],
                    distanceMeters: RouteMapGeometry.polylineTotalLength(coordinates),
                    durationSeconds: 100
                )
            ],
            totalDistanceMeters: RouteMapGeometry.polylineTotalLength(coordinates),
            totalDurationSeconds: 100,
            finishCoordinate: coordinates.last!
        )
        let projection = route.polylineIndex.projectOntoPolyline(beforeTurn)
        var offRouteTracker = TurnByTurnOffRouteTracker()
        let lateralDistance = projection?.distanceMeters ?? .greatestFiniteMagnitude

        XCTAssertLessThan(lateralDistance, TurnByTurnNavigationConstants.offRouteDistanceMeters)
        XCTAssertFalse(offRouteTracker.recordSample(lateralDistanceMeters: lateralDistance))
        XCTAssertEqual(route.flattenedSteps[1].maneuver.type, "turn")
        XCTAssertEqual(route.flattenedSteps[1].maneuver.modifier, "right")
        XCTAssertLessThan(
            route.flattenedSteps[1].maneuverArcLengthMeters - (projection?.arcLengthMeters ?? 0),
            70
        )
    }

    func testVoiceThresholdDedupKeys() {
        var announced: Set<String> = []
        let stepIndex = 2
        let threshold = NavigationVoiceThreshold.speedLead
        let key = "\(stepIndex)-\(threshold.rawValue)"

        XCTAssertFalse(announced.contains(key))
        announced.insert(key)
        XCTAssertTrue(announced.contains(key))
        XCTAssertEqual(announced.count, 1)
    }

    func testAnnouncementDeduperSuppressesRepeatedMessage() {
        var deduper = TurnByTurnAnnouncementDeduper()

        XCTAssertTrue(deduper.shouldSpeak("In 0.2 mi, turn right onto Oak Street"))
        XCTAssertFalse(deduper.shouldSpeak("  In 0.2 mi, turn right onto Oak Street  "))
        XCTAssertFalse(deduper.shouldSpeak("in 0.2 mi, turn right onto oak street"))
        XCTAssertTrue(deduper.shouldSpeak("In 200 ft, turn right onto Oak Street"))

        deduper.reset()
        XCTAssertTrue(deduper.shouldSpeak("In 0.2 mi, turn right onto Oak Street"))
    }

    func testSpeedAwareVoiceThresholdsUseTimeToManeuver() {
        XCTAssertTrue(
            NavigationVoiceThreshold.speedLead.shouldAnnounce(
                distanceToManeuverMeters: 300,
                speedMps: 25
            )
        )
        XCTAssertFalse(
            NavigationVoiceThreshold.speedLead.shouldAnnounce(
                distanceToManeuverMeters: 300,
                speedMps: 10
            )
        )
        XCTAssertTrue(
            NavigationVoiceThreshold.speedClose.shouldAnnounce(
                distanceToManeuverMeters: 50,
                speedMps: 10
            )
        )
        XCTAssertFalse(
            NavigationVoiceThreshold.speedClose.shouldAnnounce(
                distanceToManeuverMeters: 50,
                speedMps: 0
            )
        )
    }

    func testSSMLCleanerStripsTags() {
        let raw = "<speak><amazon:effect name=\"drc\">Turn right</amazon:effect></speak>"
        XCTAssertEqual(NavigationSSMLCleaner.plainText(from: raw), "Turn right")
    }

    func testDriveStartVoiceCopy() {
        XCTAssertEqual(String(localized: "turn_by_turn_drive_start"), "Okay, let's go.")
    }

    func testReadyWhenYouAreVoiceCopy() {
        XCTAssertEqual(String(localized: "turn_by_turn_ready_when_you_are"), "Ready when you are.")
    }

    func testDestinationReachedVoiceCopy() {
        XCTAssertEqual(String(localized: "turn_by_turn_destination_reached"), "You've reached your destination.")
    }

    func testFinalArriveStepIsSuppressedUntilNearFinish() {
        let step = NavigationStep(
            instruction: "Your destination is on the right",
            name: nil,
            distanceMeters: 0,
            durationSeconds: 0,
            maneuver: NavigationManeuver(type: "arrive", modifier: "right", instruction: "Your destination is on the right"),
            maneuverCoordinate: CLLocationCoordinate2D(latitude: 37.7840, longitude: -122.4194),
            voiceInstructions: [],
            geometryCoordinates: [],
            maneuverArcLengthMeters: 1_000
        )

        XCTAssertTrue(
            step.shouldSuppressArrivePresentation(
                stepIndex: 1,
                totalSteps: 2,
                distanceToFinalDestinationMeters: 805,
                currentRoadName: "Oak Street"
            )
        )
        XCTAssertFalse(
            step.shouldSuppressArrivePresentation(
                stepIndex: 1,
                totalSteps: 2,
                distanceToFinalDestinationMeters: 40
            )
        )
        XCTAssertTrue(
            step.shouldSuppressArrivePresentation(
                stepIndex: 1,
                totalSteps: 3,
                distanceToFinalDestinationMeters: 40
            )
        )
    }

    func testFinalArriveStepIsAllowedOnDestinationRoad() {
        let step = NavigationStep(
            instruction: "Your destination is on the right",
            name: "Pine Street",
            distanceMeters: 0,
            durationSeconds: 0,
            maneuver: NavigationManeuver(type: "arrive", modifier: "right", instruction: "Your destination is on the right"),
            maneuverCoordinate: CLLocationCoordinate2D(latitude: 37.7840, longitude: -122.4194),
            voiceInstructions: [],
            geometryCoordinates: [],
            maneuverArcLengthMeters: 1_000
        )

        XCTAssertFalse(
            step.shouldSuppressArrivePresentation(
                stepIndex: 1,
                totalSteps: 2,
                distanceToFinalDestinationMeters: 805,
                currentRoadName: "Pine St.",
                hasPassedFinalTurn: true
            )
        )
    }

    func testFinalArriveStepWaitsUntilFinalTurnHasOccurred() {
        let step = NavigationStep(
            instruction: "Your destination is on the right",
            name: "Pine Street",
            distanceMeters: 0,
            durationSeconds: 0,
            maneuver: NavigationManeuver(type: "arrive", modifier: "right", instruction: "Your destination is on the right"),
            maneuverCoordinate: CLLocationCoordinate2D(latitude: 37.7840, longitude: -122.4194),
            voiceInstructions: [],
            geometryCoordinates: [],
            maneuverArcLengthMeters: 1_000
        )

        XCTAssertTrue(
            step.shouldSuppressArrivePresentation(
                stepIndex: 1,
                totalSteps: 2,
                distanceToFinalDestinationMeters: 805,
                currentRoadName: "Pine St.",
                hasPassedFinalTurn: false
            )
        )
    }

    // MARK: - Helpers

    private func loadFixture(named name: String) throws -> Data {
        let bundle = Bundle(for: TurnByTurnNavigationTests.self)
        guard let url = bundle.url(forResource: name, withExtension: "json", subdirectory: "Fixtures")
            ?? bundle.url(forResource: name, withExtension: "json") else {
            throw NSError(domain: "TurnByTurnNavigationTests", code: 1)
        }
        return try Data(contentsOf: url)
    }

    private func sampleSavedRoute() -> SavedRouteDTO {
        sampleSavedRoute(
            start: CLLocationCoordinate2D(latitude: 37.7749, longitude: -122.4194),
            finish: CLLocationCoordinate2D(latitude: 37.7779, longitude: -122.4164)
        )
    }

    private func sampleSavedRoute(
        start: CLLocationCoordinate2D,
        finish: CLLocationCoordinate2D
    ) -> SavedRouteDTO {
        SavedRouteDTO(
            id: "route-test",
            createdByUserId: "user-1",
            name: "Sample",
            points: [
                RoutePointDTO(lat: start.latitude, lng: start.longitude, markerType: "start"),
                RoutePointDTO(lat: finish.latitude, lng: finish.longitude, markerType: "finish")
            ],
            roadCoordinates: [],
            distanceMeters: 1250,
            etaSeconds: 180,
            createdAt: nil,
            updatedAt: nil
        )
    }

    private func denseRouteWithRightTurn() -> [CLLocationCoordinate2D] {
        let start = CLLocationCoordinate2D(latitude: 37.0, longitude: -122.0)
        let eastbound = (0..<341).map { index in
            CLLocationCoordinate2D(
                latitude: start.latitude + sin(Double(index) / 28) * 0.00008,
                longitude: start.longitude + Double(index) * 0.000045
            )
        }
        let corner = eastbound.last!
        let southbound = (1..<220).map { index in
            CLLocationCoordinate2D(
                latitude: corner.latitude - Double(index) * 0.000045,
                longitude: corner.longitude + sin(Double(index) / 20) * 0.00008
            )
        }
        return eastbound + southbound
    }
}
