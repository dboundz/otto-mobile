import SwiftUI

struct ChatComposerAttachmentTrayBar: View {
    let actions: [ChatComposerAttachmentAction]
    let isLoadingLocation: Bool
    let onAction: (ChatComposerAttachmentAction) -> Void

    var body: some View {
        HStack(spacing: 0) {
            ForEach(actions, id: \.self) { action in
                Button {
                    onAction(action)
                } label: {
                    ChatComposerAttachmentTrayCell(
                        action: action,
                        isLoading: action == .location && isLoadingLocation
                    )
                }
                .buttonStyle(.plain)
                .disabled(action == .location && isLoadingLocation)
                .frame(maxWidth: .infinity)
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 14)
        .background(Color.white.opacity(0.04))
    }
}

struct ChatComposerAttachmentTrayCell: View {
    let action: ChatComposerAttachmentAction
    let isLoading: Bool

    var body: some View {
        VStack(spacing: 8) {
            ZStack {
                Circle()
                    .fill(Color.white.opacity(0.1))
                if isLoading {
                    ProgressView()
                        .tint(.purple)
                } else {
                    Image(systemName: action.systemImage)
                        .font(.system(size: 22, weight: .medium))
                        .foregroundStyle(action.iconTint)
                }
            }
            .frame(width: 58, height: 58)

            Text(String(localized: String.LocalizationValue(action.labelKey)))
                .font(.caption)
                .foregroundStyle(.white.opacity(0.82))
                .lineLimit(1)
                .minimumScaleFactor(0.85)
        }
        .accessibilityLabel(String(localized: String.LocalizationValue(action.accessibilityLabelKey)))
    }
}

extension ChatComposerAttachmentAction {
    var iconTint: Color {
        switch self {
        case .photo: return Color(red: 0.35, green: 0.62, blue: 1.0)
        case .gif: return Color(red: 0.98, green: 0.78, blue: 0.28)
        case .video: return Color(red: 0.88, green: 0.45, blue: 0.95)
        case .location: return Color(red: 0.35, green: 0.82, blue: 0.55)
        case .createEvent: return Color(red: 1.0, green: 0.58, blue: 0.32)
        }
    }
}
