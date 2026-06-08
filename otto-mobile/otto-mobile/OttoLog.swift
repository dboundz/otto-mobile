import CoreLocation
import Foundation
import os

/// Centralized `Logger` for Xcode / Console; filter by **subsystem** (bundle id) or **category** `Map` / `API` / `Location` / `AppState` / `UI` / `SquadEvent`.
/// Run with the app attached; increase detail: **Debug → open system log** or in Console.app search for `Otto` / category.
enum OttoLog {
    /// Fixed bundle id — avoids `Bundle.main` so loggers stay callable from background tasks (Swift 6).
    nonisolated private static let subsystem = "otto.otto-mobile"

    nonisolated static let map = Logger(subsystem: subsystem, category: "Map")
    nonisolated static let location = Logger(subsystem: subsystem, category: "Location")
    nonisolated static let app = Logger(subsystem: subsystem, category: "AppState")
    nonisolated static let api = Logger(subsystem: subsystem, category: "API")
    nonisolated static let ui = Logger(subsystem: subsystem, category: "UI")
    nonisolated static let squadEvent = Logger(subsystem: subsystem, category: "SquadEvent")
    nonisolated static let chat = Logger(subsystem: subsystem, category: "Chat")
    nonisolated static let push = Logger(subsystem: subsystem, category: "Push")

    nonisolated static func describeAuth(_ s: CLAuthorizationStatus) -> String {
        switch s {
        case .notDetermined: "notDetermined"
        case .restricted: "restricted"
        case .denied: "denied"
        case .authorizedAlways: "authorizedAlways"
        case .authorizedWhenInUse: "authorizedWhenInUse"
        @unknown default: "unknown(\(s.rawValue))"
        }
    }
}
