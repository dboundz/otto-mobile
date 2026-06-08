import SwiftUI

/// Shared chrome for full-screen settings sheets (profile settings, squad settings, …).
enum SettingsSheetChrome {
    static let settingsBackgroundGradient = LinearGradient(
        colors: [Color.black, Color(red: 0.06, green: 0.04, blue: 0.08), Color.black],
        startPoint: .topLeading,
        endPoint: .bottomTrailing
    )
}

extension View {
    func settingsCardStyle() -> some View {
        padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color.black.opacity(0.55))
            .overlay(
                RoundedRectangle(cornerRadius: 20)
                    .stroke(Color.white.opacity(0.10), lineWidth: 1)
            )
            .clipShape(RoundedRectangle(cornerRadius: 20))
    }
}
