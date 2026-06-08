import Foundation

/// UserDefaults keys for internal diagnostics (toggle from Settings → Debug).
enum OttoDebugSettings {
    static let mapLocationOverlayKey = "otto.debug.mapLocationOverlay"
    static let routeBuilderPerfOverlayKey = "otto.debug.routeBuilderPerfOverlay"
    static let routeCheckpointMapOverlayKey = "otto.debug.routeCheckpointMapOverlay"

    /// US NANP numbers allowed to see Settings → Debug and the map location overlay.
    private static let allowedUS10DigitPhones: Set<String> = ["9042549763"]

    static var mapLocationOverlayEnabled: Bool {
        get { UserDefaults.standard.bool(forKey: mapLocationOverlayKey) }
        set { UserDefaults.standard.set(newValue, forKey: mapLocationOverlayKey) }
    }

    static var routeBuilderPerfOverlayEnabled: Bool {
        get { UserDefaults.standard.bool(forKey: routeBuilderPerfOverlayKey) }
        set { UserDefaults.standard.set(newValue, forKey: routeBuilderPerfOverlayKey) }
    }

    static var routeCheckpointMapOverlayEnabled: Bool {
        get {
            if UserDefaults.standard.object(forKey: routeCheckpointMapOverlayKey) == nil {
                return true
            }
            return UserDefaults.standard.bool(forKey: routeCheckpointMapOverlayKey)
        }
        set { UserDefaults.standard.set(newValue, forKey: routeCheckpointMapOverlayKey) }
    }

    static func isInternalDebugToolsAllowed(phoneNumber: String?) -> Bool {
        guard let digits = normalizedUS10Digits(phoneNumber) else { return false }
        return allowedUS10DigitPhones.contains(digits)
    }

    /// Last 10 US digits (matches backend `normalizeUSPhoneNumber` comparison shape).
    private static func normalizedUS10Digits(_ raw: String?) -> String? {
        let digits = String(raw ?? "").filter(\.isNumber)
        if digits.count == 10 {
            return digits
        }
        if digits.count == 11, digits.hasPrefix("1") {
            return String(digits.suffix(10))
        }
        return nil
    }
}
