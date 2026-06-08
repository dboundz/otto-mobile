import SwiftUI

/// Tombstone when a shared drive or event attachment's parent was deleted.
struct ChatUnavailableShareAttachmentCard: View {
    enum Kind {
        case drive
        case event
        case place

        var sharedHeader: String {
            switch self {
            case .drive: return "shared a drive"
            case .event: return "shared an event"
            case .place: return "shared a place"
            }
        }

        var deletedMessage: String {
            switch self {
            case .drive: return "This drive was deleted"
            case .event: return "This event was deleted"
            case .place: return "This place was deleted"
            }
        }

        var icon: String {
            switch self {
            case .drive: return "steeringwheel"
            case .event: return "calendar"
            case .place: return "mappin.circle.fill"
            }
        }
    }

    let kind: Kind
    let sharedByFirstName: String
    let messageCreatedAt: Date
    let cardWidth: CGFloat

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            headerRow
            deletedBody
        }
        .padding(12)
        .frame(width: cardWidth, alignment: .leading)
        .background(Color.white.opacity(0.04))
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .stroke(Color.white.opacity(0.14), lineWidth: 1)
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(sharedByFirstName) \(kind.sharedHeader). \(kind.deletedMessage)")
    }

    private var headerRow: some View {
        HStack(spacing: 8) {
            ZStack {
                Circle()
                    .fill(Color.white.opacity(0.08))
                Image(systemName: kind.icon)
                    .font(.system(size: 13, weight: .bold))
                    .foregroundStyle(.white.opacity(0.45))
            }
            .frame(width: 28, height: 28)

            Text("\(sharedByFirstName) \(kind.sharedHeader)")
                .font(.caption.weight(.semibold))
                .foregroundStyle(.white.opacity(0.62))
                .lineLimit(1)

            Spacer(minLength: 4)

            Text(ChatRowTimeFormatter.string(from: messageCreatedAt))
                .font(.caption2)
                .foregroundStyle(.white.opacity(0.38))
        }
    }

    private var deletedBody: some View {
        HStack(spacing: 10) {
            Image(systemName: "slash.circle")
                .font(.title3)
                .foregroundStyle(.white.opacity(0.38))
            Text(kind.deletedMessage)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(.white.opacity(0.52))
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.vertical, 14)
        .padding(.horizontal, 12)
        .background(Color.white.opacity(0.03))
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
    }
}
