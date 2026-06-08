import SwiftUI

enum SavedRouteIcon {
    static let systemImageName = "arrow.trianglehead.swap"
}

/// Filled-circle list icon for saved routes and the Create Route CTA.
/// Matches drive list row sizing: 48pt circle, bold white glyph, no border or glow.
struct SavedRouteListIcon: View {
    enum Style {
        case route
        case create
    }

    var style: Style = .route
    var size: CGFloat = 48

    var body: some View {
        Image(systemName: systemImageName)
            .font(.system(size: glyphPointSize, weight: .bold))
            .foregroundStyle(.white)
            .frame(width: size, height: size)
            .background(Circle().fill(backgroundGradient))
    }

    private var systemImageName: String {
        switch style {
        case .route:
            SavedRouteIcon.systemImageName
        case .create:
            "plus"
        }
    }

    private var glyphPointSize: CGFloat {
        size >= 48 ? 22 : size * 0.46
    }

    private var backgroundGradient: LinearGradient {
        switch style {
        case .route:
            LinearGradient(
                colors: [RouteMapMarkerColors.startAccent, RouteMapMarkerColors.startButton],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
        case .create:
            LinearGradient(
                colors: [Color.purple, Color.blue.opacity(0.85)],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
        }
    }
}
