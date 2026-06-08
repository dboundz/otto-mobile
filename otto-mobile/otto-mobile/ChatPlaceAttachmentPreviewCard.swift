import SwiftUI

/// Rich shared-place card for squad/DM chat (map preview + name/address + CTA).
struct ChatPlaceAttachmentPreviewCard: View {
    let attachment: CircleChatMessageDTO.PlaceAttachmentDTO
    let sharedByFirstName: String
    let messageCreatedAt: Date
    var messageId: String? = nil
    let cardWidth: CGFloat
    let suppressNavigation: Bool
    var onLongPress: (() -> Void)? = nil
    var onDoubleTapHeart: (() -> Void)? = nil
    let onNavigate: () -> Void

    private var mapPreviewHeight: CGFloat {
        max(118, (cardWidth - 24) * 0.44)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            headerRow
            mapHero
            detailsSection
            ctaRow
        }
        .padding(12)
        .frame(width: cardWidth, alignment: .leading)
        .background(Color.white.opacity(0.055))
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .stroke(RouteMapMarkerColors.discoverySavedPlaceTeal.opacity(0.42), lineWidth: 1)
        }
        .contentShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        .overlay {
            ChatUIKitRowGestureOverlay(
                onTap: {
                    guard !suppressNavigation else { return }
                    ChatMessageActionFeedback.lightImpact()
                    onNavigate()
                },
                onLongPress: onLongPress,
                onDoubleTapHeart: onDoubleTapHeart
            )
        }
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.isButton)
        .accessibilityLabel("\(sharedByFirstName) shared a place, \(attachment.displayTitle)")
    }

    private var headerRow: some View {
        HStack(spacing: 8) {
            ZStack {
                Circle()
                    .fill(RouteMapMarkerColors.discoverySavedPlaceTeal.opacity(0.22))
                Image(systemName: "mappin.circle.fill")
                    .font(.system(size: 13, weight: .bold))
                    .foregroundStyle(RouteMapMarkerColors.discoverySavedPlaceTeal.opacity(0.95))
            }
            .frame(width: 28, height: 28)

            Text(String(format: String(localized: "chat_place_shared_header"), sharedByFirstName))
                .font(.caption.weight(.semibold))
                .foregroundStyle(.white.opacity(0.88))
                .lineLimit(1)
                .minimumScaleFactor(0.85)

            Spacer(minLength: 4)

            Text(ChatRowTimeFormatter.string(from: messageCreatedAt))
                .font(.caption2)
                .foregroundStyle(.white.opacity(0.45))
        }
    }

    private var mapHero: some View {
        Group {
            if let mapPreviewURL = mapPreviewImageURL {
                CachedAsyncImage(url: mapPreviewURL, storageKey: mapPreviewImageStorageKey) { phase in
                    switch phase {
                    case .success(let image):
                        image
                            .resizable()
                            .scaledToFill()
                    case .empty, .failure:
                        mapFallback
                    @unknown default:
                        mapFallback
                    }
                }
            } else {
                mapFallback
            }
        }
        .frame(maxWidth: .infinity)
        .frame(height: mapPreviewHeight)
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
    }

    private var mapFallback: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .fill(Color.white.opacity(0.04))
            Image("map-point-saved")
                .resizable()
                .scaledToFit()
                .frame(width: 36, height: 54)
        }
    }

    @ViewBuilder
    private var detailsSection: some View {
        VStack(alignment: .leading, spacing: 6) {
            if let name = attachment.name?.trimmingCharacters(in: .whitespacesAndNewlines), !name.isEmpty {
                Text(name)
                    .font(.subheadline.weight(.bold))
                    .foregroundStyle(.white)
                    .lineLimit(2)
            }
            if let address = attachment.addressSummary?.trimmingCharacters(in: .whitespacesAndNewlines), !address.isEmpty {
                Label(address, systemImage: "mappin")
                    .font(.caption)
                    .foregroundStyle(.white.opacity(0.72))
                    .lineLimit(2)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var ctaRow: some View {
        HStack(spacing: 5) {
            Text(String(localized: "chat_place_view_on_map"))
            Text("→")
            Spacer(minLength: 0)
        }
        .font(.caption2.weight(.medium))
        .foregroundStyle(RouteMapMarkerColors.discoverySavedPlaceTeal.opacity(0.78))
        .lineLimit(1)
        .minimumScaleFactor(0.85)
    }

    private var mapPreviewImageURL: URL? {
        guard let raw = attachment.mapPreviewUrl?.trimmingCharacters(in: .whitespacesAndNewlines),
              !raw.isEmpty else {
            return nil
        }
        return APIConfig.imageFetchURL(from: raw)
    }

    private var mapPreviewImageStorageKey: String? {
        guard let messageId, !messageId.isEmpty,
              let raw = attachment.mapPreviewUrl?.trimmingCharacters(in: .whitespacesAndNewlines),
              !raw.isEmpty else {
            return nil
        }
        return RemoteImageStorageKey.stable(prefix: "chatPlaceMapPreview:\(messageId)", sourceUrlString: raw)
    }
}
