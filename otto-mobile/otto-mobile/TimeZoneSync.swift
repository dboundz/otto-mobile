import Foundation

/// Reports the device IANA time zone to the server when it changes (push register + foreground).
enum TimeZoneSync {
    private static let lastReportedKey = "otto.lastReportedTimeZone"

    static var systemIANAIdentifier: String {
        TimeZone.current.identifier
    }

    static func syncIfNeeded(isAuthenticated: Bool) async {
        guard isAuthenticated else { return }
        let current = systemIANAIdentifier
        guard !current.isEmpty else { return }
        let cached = UserDefaults.standard.string(forKey: lastReportedKey)
        guard cached != current else { return }
        do {
            try await APIClient.shared.patchMeTimeZone(timeZone: current)
            UserDefaults.standard.set(current, forKey: lastReportedKey)
        } catch {
            // Retry on next foreground; do not block UI.
        }
    }

    /// Call after auth/me when server has a different TZ than device (reinstall).
    static func primeCacheFromServerTimeZone(_ serverTimeZone: String?) {
        guard let serverTimeZone, !serverTimeZone.isEmpty else { return }
        if UserDefaults.standard.string(forKey: lastReportedKey) == nil {
            UserDefaults.standard.set(serverTimeZone, forKey: lastReportedKey)
        }
    }
}
