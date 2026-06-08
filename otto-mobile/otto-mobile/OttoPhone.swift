import Foundation

/// US phone normalization helpers (matches backend `normalizeUSPhoneNumber` last-10 shape).
enum OttoPhone {
    /// Fixed OTP demo line (`555-555-5555`); see backend `DEMO_NANP_LAST_10`.
    private static let demoBypassLast10 = "5555555555"

    static func normalizedUS10Digits(_ raw: String?) -> String? {
        let digits = String(raw ?? "").filter(\.isNumber)
        if digits.count == 10 {
            return digits
        }
        if digits.count == 11, digits.hasPrefix("1") {
            return String(digits.suffix(10))
        }
        return nil
    }

    static func isDemoBypassPhone(_ phoneNumber: String?) -> Bool {
        normalizedUS10Digits(phoneNumber) == demoBypassLast10
    }
}
