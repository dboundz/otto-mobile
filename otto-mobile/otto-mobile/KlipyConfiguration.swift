import Foundation

enum KlipyConfiguration {
    static var appKey: String {
        let raw = Bundle.main.object(forInfoDictionaryKey: "KLIPY_APP_KEY") as? String
        return raw?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
    }

    static var isConfigured: Bool {
        !appKey.isEmpty
    }

    static func assertConfiguredForPicker() {
        #if DEBUG
        if !isConfigured {
            assertionFailure("KLIPY_APP_KEY is missing from Info.plist / Klipy.xcconfig")
        }
        #endif
    }
}
