import SwiftUI

struct DriveShareSquadActionsSheet: View {
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject private var appState: AppState

    let context: DriveChatShareContext
    let externalShareText: String
    let externalShareSubject: String
    /// Only the drive owner may share to squad chat or externally.
    var canShare: Bool = false

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
            .navigationTitle("Share Drive")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
            }
        }
        .presentationDetents([.medium, .large])
        .sheet(isPresented: $isShowingChatPostSheet) {
            DriveChatDestinationSheet(
                driveId: context.id,
                previewTitle: context.previewTitle,
                previewDistanceMeters: context.previewDistanceMeters,
                previewDriveTimeSeconds: context.previewDriveTimeSeconds,
                previewCompletedAt: context.previewCompletedAt,
                mapPreviewSnapshotInput: context.mapPreviewSnapshotInput,
                circles: appState.circles,
                lockedCircleID: context.lockedCircleID,
                onPosted: finishDriveShareFlow,
                canPost: canShare
            )
            .environmentObject(appState)
        }
    }

    private func finishDriveShareFlow() {
        isShowingChatPostSheet = false
        dismiss()
    }

    private var shareSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Share")
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(.white.opacity(0.72))
            if canShare {
                ShareSheetActionRow(
                    title: "Share to Squad Chat",
                    subtitle: "Post in a squad you're in.",
                    systemImage: "bubble.left.and.bubble.right.fill"
                ) {
                    isShowingChatPostSheet = true
                }
                ShareLink(
                    item: externalShareText,
                    subject: Text(externalShareSubject),
                    message: Text(externalShareText)
                ) {
                    ShareSheetActionRowLabel(
                        title: "Share Externally",
                        subtitle: "Share outside of the app.",
                        systemImage: "square.and.arrow.up"
                    )
                }
                .buttonStyle(.plain)
                .frame(maxWidth: .infinity)
            } else {
                Text("Only the drive owner can share this drive.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
    }
}
