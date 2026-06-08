import Foundation

public enum OttoAppGroup {
    public static let id = "group.otto.otto-mobile"

    public static var suite: UserDefaults {
        UserDefaults(suiteName: id) ?? .standard
    }
}

/// Keys for location sharing session state. Must match ``AppState`` persistence.
public enum OttoSharingUserDefaultsKeys {
    public static let sharingEnabled = "otto.sharingEnabled"
    public static let sharingCircleIDs = "otto.sharingCircleIDs"
    public static let sharingAudience = "otto.sharingAudience"
    public static let sharingDurationSeconds = "otto.sharingDurationSeconds"
    public static let sharingSessionStartedAt = "otto.sharingSessionStartedAt"
    public static let sharingSessionMode = "otto.sharingSessionMode"
    /// Monotonic-ish revision so the app can pick up widget-written changes on foreground.
    public static let sharingRevision = "otto.sharingRevision"
    /// Best-effort mirror of driving-only “paused until driving” for widget display.
    public static let sharingDrivingOnlyPaused = "otto.sharingDrivingOnlyPaused"
    /// Short label for widget map row (e.g. city), updated from the map when GPS moves.
    public static let widgetPlaceLabel = "otto.widgetPlaceLabel"
    /// Squad line for large/medium widget (mirrors in-app audience summary).
    public static let widgetSquadSummary = "otto.widgetSquadSummary"
}

/// Same keys as ``AppState.cacheShareExtensionAuth`` / share extension.
public enum OttoShareExtensionAuthKeys {
    public static let authToken = "authToken"
    public static let currentUserID = "currentUserID"
}

public enum OttoSharingWidgetKind {
    public static let control = "OttoSharingControlWidget"
}

public enum OttoSharingPersistence {
    public static func bumpRevision(in suite: UserDefaults = OttoAppGroup.suite) {
        suite.set(Date().timeIntervalSince1970, forKey: OttoSharingUserDefaultsKeys.sharingRevision)
    }

    /// Keeps `UserDefaults.standard` aligned for older code paths and extensions that still read standard.
    public static func mirrorSharingKeysToStandard(from suite: UserDefaults = OttoAppGroup.suite) {
        let standard = UserDefaults.standard
        if suite.object(forKey: OttoSharingUserDefaultsKeys.sharingEnabled) != nil {
            standard.set(suite.bool(forKey: OttoSharingUserDefaultsKeys.sharingEnabled), forKey: OttoSharingUserDefaultsKeys.sharingEnabled)
        }
        if let circles = suite.array(forKey: OttoSharingUserDefaultsKeys.sharingCircleIDs) {
            standard.set(circles, forKey: OttoSharingUserDefaultsKeys.sharingCircleIDs)
        }
        if let audience = suite.string(forKey: OttoSharingUserDefaultsKeys.sharingAudience) {
            standard.set(audience, forKey: OttoSharingUserDefaultsKeys.sharingAudience)
        }
        if suite.object(forKey: OttoSharingUserDefaultsKeys.sharingDurationSeconds) != nil {
            standard.set(suite.double(forKey: OttoSharingUserDefaultsKeys.sharingDurationSeconds), forKey: OttoSharingUserDefaultsKeys.sharingDurationSeconds)
        }
        if suite.object(forKey: OttoSharingUserDefaultsKeys.sharingSessionStartedAt) != nil {
            standard.set(suite.double(forKey: OttoSharingUserDefaultsKeys.sharingSessionStartedAt), forKey: OttoSharingUserDefaultsKeys.sharingSessionStartedAt)
        } else {
            standard.removeObject(forKey: OttoSharingUserDefaultsKeys.sharingSessionStartedAt)
        }
        if let mode = suite.string(forKey: OttoSharingUserDefaultsKeys.sharingSessionMode) {
            standard.set(mode, forKey: OttoSharingUserDefaultsKeys.sharingSessionMode)
        }
        if suite.object(forKey: OttoSharingUserDefaultsKeys.sharingDrivingOnlyPaused) != nil {
            standard.set(suite.bool(forKey: OttoSharingUserDefaultsKeys.sharingDrivingOnlyPaused), forKey: OttoSharingUserDefaultsKeys.sharingDrivingOnlyPaused)
        } else {
            standard.removeObject(forKey: OttoSharingUserDefaultsKeys.sharingDrivingOnlyPaused)
        }
        if let summary = suite.string(forKey: OttoSharingUserDefaultsKeys.widgetSquadSummary) {
            standard.set(summary, forKey: OttoSharingUserDefaultsKeys.widgetSquadSummary)
        } else {
            standard.removeObject(forKey: OttoSharingUserDefaultsKeys.widgetSquadSummary)
        }
        if let place = suite.string(forKey: OttoSharingUserDefaultsKeys.widgetPlaceLabel), !place.isEmpty {
            standard.set(place, forKey: OttoSharingUserDefaultsKeys.widgetPlaceLabel)
        } else {
            standard.removeObject(forKey: OttoSharingUserDefaultsKeys.widgetPlaceLabel)
        }
        if suite.object(forKey: OttoSharingUserDefaultsKeys.sharingRevision) != nil {
            standard.set(suite.double(forKey: OttoSharingUserDefaultsKeys.sharingRevision), forKey: OttoSharingUserDefaultsKeys.sharingRevision)
        }
    }
    public static func seedAppGroupFromStandardIfNeeded() {
        let suite = OttoAppGroup.suite
        guard suite.object(forKey: OttoSharingUserDefaultsKeys.sharingEnabled) == nil else { return }
        let standard = UserDefaults.standard
        guard standard.object(forKey: OttoSharingUserDefaultsKeys.sharingEnabled) != nil else { return }

        suite.set(standard.bool(forKey: OttoSharingUserDefaultsKeys.sharingEnabled), forKey: OttoSharingUserDefaultsKeys.sharingEnabled)
        if let circles = standard.array(forKey: OttoSharingUserDefaultsKeys.sharingCircleIDs) {
            suite.set(circles, forKey: OttoSharingUserDefaultsKeys.sharingCircleIDs)
        }
        if let audience = standard.string(forKey: OttoSharingUserDefaultsKeys.sharingAudience) {
            suite.set(audience, forKey: OttoSharingUserDefaultsKeys.sharingAudience)
        }
        suite.set(standard.double(forKey: OttoSharingUserDefaultsKeys.sharingDurationSeconds), forKey: OttoSharingUserDefaultsKeys.sharingDurationSeconds)
        if standard.object(forKey: OttoSharingUserDefaultsKeys.sharingSessionStartedAt) != nil {
            suite.set(standard.double(forKey: OttoSharingUserDefaultsKeys.sharingSessionStartedAt), forKey: OttoSharingUserDefaultsKeys.sharingSessionStartedAt)
        }
        if let mode = standard.string(forKey: OttoSharingUserDefaultsKeys.sharingSessionMode) {
            suite.set(mode, forKey: OttoSharingUserDefaultsKeys.sharingSessionMode)
        }
        if standard.object(forKey: OttoSharingUserDefaultsKeys.sharingDrivingOnlyPaused) != nil {
            suite.set(standard.bool(forKey: OttoSharingUserDefaultsKeys.sharingDrivingOnlyPaused), forKey: OttoSharingUserDefaultsKeys.sharingDrivingOnlyPaused)
        }
        bumpRevision(in: suite)
    }
}
