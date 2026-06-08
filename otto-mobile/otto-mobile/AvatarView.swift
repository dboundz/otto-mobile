import SwiftUI

/// Avatar: remote image when `avatarUrl` is set, otherwise initials on accent-color fill.
struct AvatarView: View {
    enum AvatarShape {
        case circle
        case roundedSquare(cornerRadius: CGFloat)
    }

    let name: String
    let avatarUrl: String?
    var size: CGFloat = 48
    var accentColor: Color = .white.opacity(0.3)
    var accentRingWidth: CGFloat = 0
    var whiteRingWidth: CGFloat = 0
    var shape: AvatarShape = .circle
    private var resolvedImageStorageKey: String? {
        guard let avatarUrl, !avatarUrl.isEmpty else { return nil }
        return RemoteImageStorageKey.stable(prefix: "avatar", sourceUrlString: avatarUrl)
    }
    private var insettableShape: AnyInsettableShape {
        switch shape {
        case .circle:
            return AnyInsettableShape(Circle())
        case .roundedSquare(let cornerRadius):
            return AnyInsettableShape(RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
        }
    }

    private var initials: String {
        let parts = name.split(separator: " ")
        let letters = parts.prefix(2).compactMap(\.first)
        let s = letters.map(String.init).joined()
        return s.isEmpty ? "?" : s
    }

    var body: some View {
        ZStack {
            if let urlString = avatarUrl, let url = APIConfig.imageFetchURL(from: urlString) {
                CachedAsyncImage(url: url, storageKey: resolvedImageStorageKey) { phase in
                    switch phase {
                    case .empty:
                        Color(white: 0.1)
                    case .success(let image):
                        image
                            .resizable()
                            .scaledToFill()
                    case .failure:
                        initialsOnly
                    @unknown default:
                        Color(white: 0.1)
                    }
                }
            } else {
                initialsOnly
            }
        }
        .frame(width: size, height: size)
        .clipped()
        .clipShape(insettableShape)
        .shadow(color: accentColor.opacity(0.35), radius: size > 40 ? 10 : 4, y: 2)
        .overlay {
            if accentRingWidth > 0 {
                insettableShape.strokeBorder(accentColor, lineWidth: accentRingWidth)
            }
        }
        .overlay {
            if whiteRingWidth > 0 {
                insettableShape.strokeBorder(Color.white.opacity(0.92), lineWidth: whiteRingWidth)
            }
        }
    }

    private var initialsOnly: some View {
        Text(initials)
            .font(.system(size: max(12, size * 0.32), weight: .heavy, design: .rounded))
            .foregroundStyle(.white)
            .frame(width: size, height: size)
            .background(accentColor)
    }
}

private struct AnyInsettableShape: InsettableShape {
    private let _path: @Sendable (CGRect) -> Path
    private let _inset: @Sendable (CGFloat) -> AnyInsettableShape

    init<S: InsettableShape>(_ wrapped: S) {
        _path = { rect in wrapped.path(in: rect) }
        _inset = { amount in AnyInsettableShape(wrapped.inset(by: amount)) }
    }

    func path(in rect: CGRect) -> Path { _path(rect) }
    func inset(by amount: CGFloat) -> AnyInsettableShape { _inset(amount) }
}
