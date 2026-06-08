import SwiftUI

/// Shared Squad avatar: remote `imageUrl` when set, otherwise a branded placeholder.
struct SquadAvatarView: View {
    let name: String
    var imageUrl: String?
    var icon: String = "person.3.fill"
    var size: CGFloat = 48
    /// Pass the squad id so presigned `imageUrl` values still resolve to the same cache entry after each API refresh.
    var cacheStorageKey: String? = nil

    var body: some View {
        ZStack {
            if let imageUrl, let url = APIConfig.imageFetchURL(from: imageUrl) {
                CachedAsyncImage(url: url, storageKey: resolvedImageCacheKey(sourceUrlString: imageUrl)) { phase in
                    switch phase {
                    case .success(let image):
                        image
                            .resizable()
                            .scaledToFill()
                    case .empty, .failure:
                        placeholder
                    @unknown default:
                        placeholder
                    }
                }
            } else {
                placeholder
            }
        }
        .frame(width: size, height: size)
        .clipShape(Circle())
    }

    private var placeholder: some View {
        ZStack {
            Circle()
                .fill(
                    LinearGradient(
                        colors: [Color.purple.opacity(0.78), Color.blue.opacity(0.46)],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                )
            Image(systemName: icon)
                .font(.system(size: max(13, size * 0.38), weight: .semibold))
                .foregroundStyle(.white)
        }
        .accessibilityLabel("\(name) Squad")
    }

    /// Presigned URLs rotate on every API call; `storageKey` + canonical path (no query) keeps one cache entry per image revision.
    private func resolvedImageCacheKey(sourceUrlString: String) -> String? {
        guard let cacheStorageKey, !cacheStorageKey.isEmpty else { return nil }
        let canonical = sourceUrlString.split(separator: "?").first.map(String.init) ?? sourceUrlString
        return "\(cacheStorageKey)|\(canonical)"
    }
}
