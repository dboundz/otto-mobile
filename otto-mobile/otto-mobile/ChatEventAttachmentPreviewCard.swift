import SwiftUI

/// Lightweight inline preview for shared events in squad/DM chat — compact icon + title + metadata,
/// RSVP strip, and a subtle “View Event” affordance (full detail on the event screen).
struct ChatEventAttachmentPreviewCard: View {
    let attachment: CircleChatMessageDTO.EventAttachmentDTO
    let resolvedEvent: EventDTO?
    let cardWidth: CGFloat
    let suppressNavigation: Bool
    let rsvpSubmitting: Bool
    let meUser: UserDTO?
    let onRsvp: (String) -> Void
    let onNavigate: () -> Void
    /// Stable cache scope for presigned banner URLs (same pattern as chat photo attachments).
    var messageId: String? = nil
    var onLongPress: (() -> Void)? = nil
    var onDoubleTapHeart: (() -> Void)? = nil

    private var title: String {
        resolvedEvent?.name ?? attachment.name ?? "Event"
    }

    private var startsAt: Date? {
        resolvedEvent?.startsAt ?? attachment.startsAt
    }

    private var locationLine: String? {
        if let ev = resolvedEvent {
            if let label = ev.address?.label, !label.isEmpty { return label }
            let cityRegion = [ev.address?.city, ev.address?.region]
                .compactMap { ($0?.isEmpty == false) ? $0 : nil }
                .joined(separator: ", ")
            if !cityRegion.isEmpty { return cityRegion }
        }
        let raw = attachment.addressLabel?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        return raw.isEmpty ? nil : raw
    }

    private var compactMetaLine: String {
        var parts: [String] = []
        if let loc = locationLine, !loc.isEmpty { parts.append(loc) }
        if let date = startsAt {
            let dayFmt = DateFormatter()
            dayFmt.dateFormat = "EEE, MMM d"
            parts.append(dayFmt.string(from: date))
            let timeFmt = DateFormatter()
            timeFmt.timeStyle = .short
            timeFmt.dateStyle = .none
            parts.append(timeFmt.string(from: date))
        }
        return parts.joined(separator: " · ")
    }

    private var bannerImageSourceString: String? {
        let raw = resolvedEvent?.bannerImage?.url ?? attachment.bannerImageUrl
        guard let raw else { return nil }
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
    }

    private var bannerImageURL: URL? {
        guard let bannerImageSourceString else { return nil }
        return APIConfig.imageFetchURL(from: bannerImageSourceString)
    }

    private var bannerImageStorageKey: String? {
        guard let messageId, !messageId.isEmpty, let bannerImageSourceString else { return nil }
        return RemoteImageStorageKey.stable(
            prefix: "chatEventBanner:\(messageId)",
            sourceUrlString: bannerImageSourceString
        )
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            if let bannerImageURL {
                CachedAsyncImage(url: bannerImageURL, storageKey: bannerImageStorageKey) { phase in
                    switch phase {
                    case .success(let image):
                        image
                            .resizable()
                            .scaledToFill()
                    case .empty, .failure:
                        bannerImagePlaceholder
                    @unknown default:
                        bannerImagePlaceholder
                    }
                }
                .frame(height: 148)
                .frame(maxWidth: .infinity)
                .clipped()
                .contentShape(Rectangle())
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
                .accessibilityLabel(title)
                .accessibilityAddTraits(.isButton)
                .accessibilityHint("Opens event details")
            }

            cardBody
        }
        .frame(width: cardWidth, alignment: .leading)
        .background(Color.white.opacity(0.032))
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .stroke(Color.white.opacity(0.065), lineWidth: 1)
        }
        .accessibilityElement(children: .contain)
    }

    private var bannerImagePlaceholder: some View {
        LinearGradient(
            colors: [Color.purple.opacity(0.4), Color.black.opacity(0.85)],
            startPoint: .topLeading,
            endPoint: .bottomTrailing
        )
    }

    private var cardBody: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .top, spacing: 10) {
                ZStack {
                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                        .fill(Color.purple.opacity(0.18))
                    Image(systemName: "calendar")
                        .font(.system(size: 17, weight: .semibold))
                        .foregroundStyle(Color.purple.opacity(0.92))
                }
                .frame(width: 44, height: 44)
                .accessibilityHidden(true)

                VStack(alignment: .leading, spacing: 3) {
                    Button {
                        openEvent()
                    } label: {
                        Text(title)
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(.white)
                            .multilineTextAlignment(.leading)
                            .lineLimit(2)
                            .fixedSize(horizontal: false, vertical: true)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    .buttonStyle(.plain)
                    .disabled(suppressNavigation)
                    .accessibilityHint("Opens event details")

                    if !compactMetaLine.isEmpty {
                        Button {
                            openEvent()
                        } label: {
                            Text(compactMetaLine)
                                .font(.caption2)
                                .foregroundStyle(.white.opacity(0.5))
                                .lineLimit(2)
                                .frame(maxWidth: .infinity, alignment: .leading)
                        }
                        .buttonStyle(.plain)
                        .disabled(suppressNavigation)
                        .accessibilityHint("Opens event details")
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }

            if let ev = resolvedEvent {
                EventAttachmentRsvpStrip(
                    event: ev,
                    meUser: meUser,
                    submitting: rsvpSubmitting,
                    onSelect: onRsvp
                )
                .padding(.top, 7)
            }

            Button {
                openEvent()
            } label: {
                HStack(spacing: 5) {
                    Text("View Event")
                        .font(.caption2.weight(.medium))
                    Text("→")
                        .font(.caption2.weight(.medium))
                    Spacer(minLength: 0)
                }
                .foregroundStyle(Color.purple.opacity(0.58))
            }
            .buttonStyle(.plain)
            .padding(.top, 14)
            .disabled(suppressNavigation)
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 8)
    }

    private func openEvent() {
        guard !suppressNavigation else { return }
        onNavigate()
    }
}
