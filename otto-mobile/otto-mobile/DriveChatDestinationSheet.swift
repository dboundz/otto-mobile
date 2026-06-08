import SwiftUI

struct DriveChatDestinationSheet: View {
    @EnvironmentObject private var appState: AppState
    @Environment(\.dismiss) private var dismiss

    let driveId: String
    let previewTitle: String
    let previewDistanceMeters: Double
    let previewDriveTimeSeconds: TimeInterval
    let previewCompletedAt: Date
    let mapPreviewSnapshotInput: DriveMapPreviewSnapshotInput?
    let circles: [DriveCircle]
    let lockedCircleID: String?
    /// Called after a successful post so parent share sheets can dismiss.
    var onPosted: (() -> Void)?
    /// Only the drive owner may post a drive attachment to squad chat.
    var canPost: Bool = false

    @State private var selectedCircleID: String
    @State private var isSending = false

    init(
        driveId: String,
        previewTitle: String,
        previewDistanceMeters: Double,
        previewDriveTimeSeconds: TimeInterval,
        previewCompletedAt: Date = Date(),
        mapPreviewSnapshotInput: DriveMapPreviewSnapshotInput? = nil,
        circles: [DriveCircle],
        lockedCircleID: String?,
        onPosted: (() -> Void)? = nil,
        canPost: Bool = false
    ) {
        self.driveId = driveId
        self.previewTitle = previewTitle
        self.previewDistanceMeters = previewDistanceMeters
        self.previewDriveTimeSeconds = previewDriveTimeSeconds
        self.previewCompletedAt = previewCompletedAt
        self.mapPreviewSnapshotInput = mapPreviewSnapshotInput
        self.circles = circles
        self.lockedCircleID = lockedCircleID
        self.onPosted = onPosted
        self.canPost = canPost
        let initialCircleID = lockedCircleID ?? ""
        _selectedCircleID = State(initialValue: initialCircleID)
    }

    private var availableCircles: [DriveCircle] {
        if let lockedCircleID {
            return circles.filter { $0.id == lockedCircleID }
        }
        return circles
    }

    private var selectedCircleName: String {
        availableCircles.first(where: { $0.id == selectedCircleID })?.name ?? "Squad"
    }

    private var canSend: Bool {
        canPost && !selectedCircleID.isEmpty && !isSending
    }

    var body: some View {
        ZStack(alignment: .bottom) {
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    header
                    postDrivePreview
                    destinationsSection
                }
                .padding(18)
                .padding(.bottom, 92)
            }

            postButton
                .padding(.horizontal, 18)
                .padding(.top, 12)
                .padding(.bottom, 18)
                .background(
                    LinearGradient(
                        colors: [Color.black.opacity(0), Color.black.opacity(0.96), Color.black],
                        startPoint: .top,
                        endPoint: .bottom
                    )
                    .ignoresSafeArea(edges: .bottom)
                )
        }
        .background(Color.black.ignoresSafeArea())
    }

    private var header: some View {
        HStack(spacing: 12) {
            ZStack {
                Circle()
                    .fill(Color.purple.opacity(0.25))
                Image(systemName: "steeringwheel")
                    .font(.system(size: 17, weight: .bold))
                    .foregroundStyle(.purple)
            }
            .frame(width: 38, height: 38)

            VStack(alignment: .leading, spacing: 2) {
                Text("Post to Chat")
                    .font(.headline.weight(.bold))
                    .foregroundStyle(.white)
                Text("Share this drive to one squad")
                    .font(.caption)
                    .foregroundStyle(.white.opacity(0.62))
            }

            Spacer()

            Button {
                dismiss()
            } label: {
                OttoGlassIconButtonLabel(
                    systemImage: "xmark",
                    size: CGSize(width: 32, height: 32),
                    cornerRadius: 16,
                    font: .system(size: 13, weight: .bold),
                    foregroundStyle: .white.opacity(0.72),
                    backgroundOpacity: 0.08,
                    strokeOpacity: 0
                )
            }
            .buttonStyle(.plain)
        }
    }

    private var destinationsSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            sectionLabel("Squad")
            if availableCircles.isEmpty {
                Text("Create or join a Squad before sharing this drive to chat.")
                    .font(.subheadline)
                    .foregroundStyle(.white.opacity(0.62))
                    .padding(14)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color.white.opacity(0.055))
                    .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
            } else {
                VStack(spacing: 8) {
                    ForEach(availableCircles) { circle in
                        squadDestinationRow(circle)
                    }
                }
            }
        }
    }

    private var postDrivePreview: some View {
        HStack(spacing: 12) {
            ZStack {
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(Color.purple.opacity(0.18))
                Image(systemName: "steeringwheel")
                    .font(.system(size: 22, weight: .semibold))
                    .foregroundStyle(Color.purple.opacity(0.92))
            }
            .frame(width: 56, height: 66)

            VStack(alignment: .leading, spacing: 6) {
                Text(previewTitle)
                    .font(.headline.weight(.bold))
                    .foregroundStyle(.white)
                    .lineLimit(2)
                Text(previewMetaLine)
                    .font(.caption)
                    .foregroundStyle(.white.opacity(0.72))
                    .lineLimit(2)
            }
            Spacer(minLength: 0)
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            LinearGradient(
                colors: [Color.white.opacity(0.08), Color.purple.opacity(0.12), Color.white.opacity(0.035)],
                startPoint: .leading,
                endPoint: .trailing
            )
        )
        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .stroke(Color.white.opacity(0.12), lineWidth: 1)
        }
    }

    private var postButton: some View {
        Button {
            beginPost()
        } label: {
            OttoGradientButtonLabel(
                title: isSending ? "Sharing..." : postButtonTitle,
                systemImage: "paperplane.fill",
                height: 58
            )
        }
        .buttonStyle(.plain)
        .disabled(!canSend)
        .opacity(canSend ? 1 : 0.45)
    }

    private var postButtonTitle: String {
        if selectedCircleID.isEmpty {
            return "Choose a squad"
        }
        return "Share to \(selectedCircleName)"
    }

    private var previewMetaLine: String {
        let miles = previewDistanceMeters / 1609.344
        let distance = miles < 10 ? String(format: "%.1f mi", miles) : "\(Int(miles.rounded())) mi"
        let minutes = max(1, Int((previewDriveTimeSeconds / 60).rounded()))
        let time = minutes >= 60 ? "\(minutes / 60)h \(minutes % 60)m" : "\(minutes)m"
        let formatter = DateFormatter()
        formatter.dateFormat = "MMM d, yyyy • h:mm a"
        return "\(distance) · \(time) · \(formatter.string(from: previewCompletedAt))"
    }

    private func squadDestinationRow(_ circle: DriveCircle) -> some View {
        Button {
            selectCircle(circle.id)
        } label: {
            HStack(spacing: 12) {
                SquadAvatarView(
                    name: circle.name,
                    imageUrl: circle.photoUrl,
                    icon: circle.icon,
                    size: 48,
                    cacheStorageKey: "squadAvatar:\(circle.id)"
                )

                VStack(alignment: .leading, spacing: 3) {
                    Text(circle.name)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(.white)
                    Text(memberCountLine(for: circle))
                        .font(.caption)
                        .foregroundStyle(.white.opacity(0.62))
                }

                Spacer()

                Image(systemName: selectedCircleID == circle.id ? "circle.inset.filled" : "circle")
                    .font(.title3)
                    .foregroundStyle(selectedCircleID == circle.id ? Color.purple : Color.white.opacity(0.28))
            }
            .padding(12)
            .background(Color.white.opacity(selectedCircleID == circle.id ? 0.075 : 0.045))
            .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .stroke(selectedCircleID == circle.id ? Color.purple.opacity(0.95) : Color.clear, lineWidth: 1)
            }
        }
        .buttonStyle(.plain)
        .disabled(lockedCircleID != nil)
    }

    private func selectCircle(_ circleID: String) {
        selectedCircleID = circleID
    }

    private func sectionLabel(_ text: String) -> some View {
        Text(text.uppercased())
            .font(.caption2.weight(.bold))
            .foregroundStyle(.white.opacity(0.56))
    }

    private func memberCountLine(for circle: DriveCircle) -> String {
        "\(circle.members.count) \(circle.members.count == 1 ? "member" : "members")"
    }

    private func beginPost() {
        guard canPost else {
            appState.errorMessage = "Only the drive owner can share this drive."
            return
        }
        guard canSend else { return }
        let circleID = selectedCircleID.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !circleID.isEmpty else { return }

        isSending = true

        Task {
            do {
                var mapPreviewJPEG: Data?
                let resolvedInput = await DriveMapPreviewSnapshotResolver.resolve(
                    preloaded: mapPreviewSnapshotInput,
                    driveId: driveId,
                    circleId: lockedCircleID
                )
                if let resolvedInput {
                    mapPreviewJPEG = await DriveRouteMapSnapshotGenerator.jpegData(input: resolvedInput)
                }
                try await APIClient.shared.postCircleChatDriveMessage(
                    circleId: circleID,
                    driveId: driveId,
                    mapPreviewJPEGData: mapPreviewJPEG
                )
                await MainActor.run {
                    appState.requestSquadChatFocus(circleID: circleID)
                    dismiss()
                    onPosted?()
                    appState.activeToast = AppToast(
                        text: "Drive shared to chat",
                        systemImage: "bubble.left.and.bubble.right.fill"
                    )
                }
            } catch {
                await MainActor.run {
                    isSending = false
                    appState.errorMessage = "Couldn't share drive to chat."
                }
            }
        }
    }
}
