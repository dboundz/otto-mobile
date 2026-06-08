import Foundation

/// Minimal presence client for the widget extension (Bearer token + JSON POST).
public enum WidgetPresenceClient {
    public enum WidgetPresenceError: Error {
        case badStatus(Int)
    }

    public static func markInactive(userId: String, circleId: String, token: String) async throws {
        var request = URLRequest(url: WidgetAPIBaseURL.url.appending(path: "/api/presence"))
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        let payload: [String: Any] = [
            "userId": userId,
            "circleId": circleId,
            "isActive": false,
            "speedMph": 0,
            "movementMode": "unknown",
        ]
        request.httpBody = try JSONSerialization.data(withJSONObject: payload)
        let (_, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse else { throw WidgetPresenceError.badStatus(-1) }
        guard (200...299).contains(http.statusCode) else {
            throw WidgetPresenceError.badStatus(http.statusCode)
        }
    }
}
