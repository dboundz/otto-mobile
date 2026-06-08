import SwiftUI

/// Long-press map action sheet: share to chat, share externally, or save to My Places.
struct MapPlaceLongPressActionSheet: View {
    @Environment(\.dismiss) private var dismiss

    let isResolving: Bool
    let previewName: String?
    let previewAddress: String?
    let sharePayload: MapMarkerSharePayload
    let onShareToChat: () -> Void
    let onSave: () -> Void

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    locationPreview
                    VStack(spacing: 8) {
                        ShareSheetActionRow(
                            title: String(localized: "event_share_to_squad_chat"),
                            subtitle: String(localized: "event_share_to_squad_chat_subtitle"),
                            systemImage: "bubble.left.and.bubble.right.fill"
                        ) {
                            onShareToChat()
                        }
                        ShareLink(
                            item: sharePayload.externalShareText,
                            subject: Text(sharePayload.title),
                            message: Text(sharePayload.externalShareText)
                        ) {
                            ShareSheetActionRowLabel(
                                title: String(localized: "event_share_external"),
                                subtitle: String(localized: "event_share_external_subtitle"),
                                systemImage: "square.and.arrow.up"
                            )
                        }
                        .buttonStyle(.plain)
                        .frame(maxWidth: .infinity)
                        ShareSheetActionRow(
                            title: String(localized: "map_place_action_save"),
                            subtitle: String(localized: "map_place_action_save_subtitle"),
                            systemImage: "bookmark.fill"
                        ) {
                            onSave()
                        }
                    }
                }
                .padding(20)
            }
            .background(Color.black.ignoresSafeArea())
            .navigationTitle(String(localized: "map_place_action_title"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
            }
        }
        .presentationDetents([.medium, .large])
    }

    @ViewBuilder
    private var locationPreview: some View {
        VStack(alignment: .leading, spacing: 8) {
            if isResolving {
                HStack(spacing: 10) {
                    ProgressView()
                    Text(String(localized: "map_place_action_resolving"))
                        .font(.subheadline)
                        .foregroundStyle(.white.opacity(0.72))
                }
            } else {
                if let previewName, !previewName.isEmpty {
                    Text(previewName)
                        .font(.headline.weight(.semibold))
                        .foregroundStyle(.white)
                        .lineLimit(2)
                }
                if let previewAddress, !previewAddress.isEmpty {
                    Label(previewAddress, systemImage: "mappin")
                        .font(.caption)
                        .foregroundStyle(.white.opacity(0.72))
                        .lineLimit(3)
                }
                if (previewName?.isEmpty ?? true) && (previewAddress?.isEmpty ?? true) {
                    Text(String(localized: "map_place_action_resolving_fallback"))
                        .font(.subheadline)
                        .foregroundStyle(.white.opacity(0.62))
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(14)
        .background(Color.white.opacity(0.055))
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .stroke(Color.white.opacity(0.10), lineWidth: 1)
        }
    }
}
