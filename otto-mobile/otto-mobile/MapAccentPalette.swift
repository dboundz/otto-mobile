import SwiftUI

/// Keys stored on the user account (`mapAccentKey`). Colors are defined only here so the palette is easy to retune.
enum MapAccentKey: String, CaseIterable, Identifiable, Codable {
    case violet, blue, amber, mint, rose, coral, sky, lime

    var id: String { rawValue }

    var color: Color {
        switch self {
        case .violet: return Color(red: 0.62, green: 0.38, blue: 0.98)
        case .blue: return Color(red: 0.28, green: 0.55, blue: 1.0)
        case .amber: return Color(red: 1.0, green: 0.72, blue: 0.22)
        case .mint: return Color(red: 0.32, green: 0.88, blue: 0.62)
        case .rose: return Color(red: 0.98, green: 0.42, blue: 0.55)
        case .coral: return Color(red: 1.0, green: 0.45, blue: 0.38)
        case .sky: return Color(red: 0.38, green: 0.75, blue: 1.0)
        case .lime: return Color(red: 0.62, green: 0.92, blue: 0.32)
        }
    }
}

enum MapAccentPalette {
    /// When `mapAccentKey` is set on the user, it wins. Otherwise a stable key is chosen from `userId` (matches server `defaultKeyForUserId` logic).
    static func resolvedColor(mapAccentKey: String?, userId: String) -> Color {
        if let mapAccentKey, let key = MapAccentKey(rawValue: mapAccentKey) {
            return key.color
        }
        return color(fromStableSeed: userId)
    }

    static func color(fromStableSeed seed: String) -> Color {
        let keys = MapAccentKey.allCases
        // Use wrapping arithmetic to keep hashing deterministic without debug overflow traps.
        var h: UInt64 = 0
        for u in seed.unicodeScalars {
            h = (h &* 31) &+ UInt64(u.value)
        }
        return keys[Int(h % UInt64(keys.count))].color
    }
}

struct MapAccentPickerSheet: View {
    @Environment(\.dismiss) private var dismiss
    let onSelect: (String) -> Void

    var body: some View {
        NavigationStack {
            LazyVGrid(columns: [GridItem(.adaptive(minimum: 72), spacing: 16)], spacing: 16) {
                ForEach(MapAccentKey.allCases) { key in
                    Button {
                        onSelect(key.rawValue)
                        dismiss()
                    } label: {
                        ZStack {
                            Circle()
                                .fill(key.color)
                                .frame(width: 56, height: 56)
                            Circle()
                                .strokeBorder(Color.white.opacity(0.35), lineWidth: 2)
                                .frame(width: 56, height: 56)
                        }
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(Text(key.rawValue))
                }
            }
            .padding(24)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
            .background(Color.black)
            .navigationTitle("Map pin color")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
            }
        }
        .presentationDetents([.medium, .large])
    }
}
