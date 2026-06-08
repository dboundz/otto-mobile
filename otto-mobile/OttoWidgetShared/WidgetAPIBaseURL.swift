import Foundation

/// Matches ``APIConfig.baseURL`` in the main app (simulator → local backend).
public enum WidgetAPIBaseURL {
    public static var url: URL {
        #if targetEnvironment(simulator)
        URL(string: "http://localhost:4000")!
        #else
        URL(string: "https://api.ottomot.to")!
        #endif
    }
}
