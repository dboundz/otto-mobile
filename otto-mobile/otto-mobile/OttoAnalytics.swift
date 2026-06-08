import FirebaseAnalytics
import FirebaseCore
import FirebaseCrashlytics

enum OttoAnalytics {
    private static var isConfigured = false

    static func configure() {
        guard !isConfigured else { return }
        FirebaseApp.configure()
        #if DEBUG
        Analytics.setAnalyticsCollectionEnabled(true)
        Crashlytics.crashlytics().setCrashlyticsCollectionEnabled(false)
        #endif
        isConfigured = true
    }

    static func setUserID(_ userID: String) {
        guard !userID.isEmpty else { return }
        Analytics.setUserID(userID)
        Crashlytics.crashlytics().setUserID(userID)
    }

    static func clearUserID() {
        Analytics.setUserID(nil)
        Crashlytics.crashlytics().setUserID("")
    }

    static func logSignUpComplete() {
        Analytics.logEvent("sign_up_complete", parameters: nil)
    }

    static func logOnboardingNameComplete() {
        Analytics.logEvent("onboarding_name_complete", parameters: nil)
    }

    static func logLocationSharingEnabled() {
        Analytics.logEvent("location_sharing_enabled", parameters: ["source": "map"])
    }

    static func logLocationSharingDisabled(reason: String) {
        Analytics.logEvent("location_sharing_disabled", parameters: ["reason": reason])
    }

    static func logDriveStarted(kind: String, routeID: String? = nil) {
        var params: [String: Any] = ["kind": kind]
        if let routeID, !routeID.isEmpty {
            params["route_id"] = routeID
        }
        Analytics.logEvent("drive_started", parameters: params)
    }

    static func logDriveCompleted(kind: String, distanceMeters: Double) {
        let bucket = max(0, Int((distanceMeters / 100.0).rounded()) * 100)
        Analytics.logEvent(
            "drive_completed",
            parameters: [
                "kind": kind,
                "distance_meters": bucket,
            ]
        )
    }

    static func logEventCheckIn(eventID: String) {
        let trimmed = eventID.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        Analytics.logEvent("event_check_in", parameters: ["event_id": trimmed])
    }

    static func logAutoCheckInGeofenceRegistered(regionCount: Int) {
        Analytics.logEvent(
            "auto_checkin_geofence_registered",
            parameters: ["region_count": max(0, regionCount)]
        )
    }

    static func logAutoCheckInGeofenceEntered(eventID: String) {
        let trimmed = eventID.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        Analytics.logEvent("auto_checkin_geofence_entered", parameters: ["event_id": trimmed])
    }

    static func logAutoCheckInSkipped(eventID: String, reason: String, trigger: String) {
        let trimmed = eventID.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        Analytics.logEvent(
            "auto_checkin_skipped",
            parameters: [
                "event_id": trimmed,
                "reason": reason,
                "trigger": trigger,
            ]
        )
    }

    static func logAutoCheckInSuccess(eventID: String, trigger: String) {
        let trimmed = eventID.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        Analytics.logEvent(
            "auto_checkin_success",
            parameters: [
                "event_id": trimmed,
                "trigger": trigger,
            ]
        )
    }

    static func logSquadCreated() {
        Analytics.logEvent("squad_created", parameters: nil)
    }

    static func logSquadJoined(source: String) {
        Analytics.logEvent("squad_joined", parameters: ["source": source])
    }

    static func logChatMessageSent(channel: String, attachmentType: String) {
        Analytics.logEvent(
            "chat_message_sent",
            parameters: [
                "channel": channel,
                "attachment_type": attachmentType,
            ]
        )
    }

    static func logGarageCarAdded(hasPhoto: Bool) {
        Analytics.logEvent(
            "garage_car_added",
            parameters: ["has_photo": hasPhoto ? "true" : "false"]
        )
    }

    static func logRouteSaved() {
        Analytics.logEvent("route_saved", parameters: nil)
    }

    static func analyticsDriveKind(_ kind: DriveSessionKind) -> String {
        switch kind {
        case .quick, .live:
            return "quick"
        case .route:
            return "route"
        }
    }
}
