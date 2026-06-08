import SwiftUI

struct MapMarkerShareSquadActionsSheet: View {
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject private var appState: AppState

    let payload: MapMarkerSharePayload
    var onPostedToChat: (() -> Void)? = nil

    @State private var isShowingChatPostSheet = false

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 24) {
                    shareSection
                }
                .padding(20)
            }
            .background(Color.black.ignoresSafeArea())
            .navigationTitle("Share & Squad Actions")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
            }
        }
        .presentationDetents([.medium, .large])
        .sheet(isPresented: $isShowingChatPostSheet) {
            MapMarkerChatDestinationSheet(
                payload: payload,
                onPosted: finishMapMarkerShareToChatFlow
            )
            .environmentObject(appState)
        }
    }

    private func finishMapMarkerShareToChatFlow() {
        isShowingChatPostSheet = false
        dismiss()
        onPostedToChat?()
    }

    private var shareSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Share")
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(.white.opacity(0.72))
            ShareSheetActionRow(
                title: "Share to Squad Chat",
                subtitle: "Post in a squad you're in.",
                systemImage: "bubble.left.and.bubble.right.fill"
            ) {
                isShowingChatPostSheet = true
            }
            ShareLink(
                item: payload.externalShareText,
                subject: Text(payload.title),
                message: Text(payload.externalShareText)
            ) {
                ShareSheetActionRowLabel(
                    title: "Share Externally",
                    subtitle: "Share outside of the app.",
                    systemImage: "square.and.arrow.up"
                )
            }
            .buttonStyle(.plain)
            .frame(maxWidth: .infinity)
        }
    }
}
