import SwiftUI

struct EventChatDestinationSheet: View {
    @EnvironmentObject private var appState: AppState
    @Environment(\.dismiss) private var dismiss

    let event: EventDTO
    let circles: [DriveCircle]
    let lockedCircleID: String?
    /// Called after a successful post so parent share sheets can dismiss.
    var onPosted: (() -> Void)?

    @State private var selectedCircleID: String
    @State private var isSending = false

    init(
        event: EventDTO,
        circles: [DriveCircle],
        lockedCircleID: String?,
        onPosted: (() -> Void)? = nil
    ) {
        self.event = event
        self.circles = circles
        self.lockedCircleID = lockedCircleID
        self.onPosted = onPosted
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
        !selectedCircleID.isEmpty && !isSending
    }

    var body: some View {
        ZStack(alignment: .bottom) {
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    header
                    postEventPreview
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
                Image(systemName: "bubble.left.fill")
                    .font(.system(size: 17, weight: .bold))
                    .foregroundStyle(.purple)
            }
            .frame(width: 38, height: 38)

            VStack(alignment: .leading, spacing: 2) {
                Text("Post to Chat")
                    .font(.headline.weight(.bold))
                    .foregroundStyle(.white)
                Text("Share this event to one squad")
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
                Text("Create or join a Squad before posting this event to chat.")
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

    private var postButton: some View {
        Button {
            beginPost()
        } label: {
            OttoGradientButtonLabel(
                title: isSending ? "Posting..." : postButtonTitle,
                systemImage: "paperplane.fill",
                height: 58
            )
        }
        .buttonStyle(.plain)
        .disabled(!canSend)
        .opacity(canSend ? 1 : 0.45)
    }

    private var postEventPreview: some View {
        HStack(spacing: 12) {
            dateBadge
            VStack(alignment: .leading, spacing: 6) {
                Text(event.name)
                    .font(.headline.weight(.bold))
                    .foregroundStyle(.white)
                    .lineLimit(1)
                Label(locationText, systemImage: "mappin")
                    .font(.caption)
                    .foregroundStyle(.white.opacity(0.72))
                Text(eventDetailLine)
                    .font(.caption2)
                    .foregroundStyle(.white.opacity(0.62))
                    .lineLimit(1)
            }
            Spacer()
            if let urlString = event.bannerImage?.url, let url = URL(string: urlString) {
                CachedAsyncImage(url: url) { phase in
                    switch phase {
                    case .success(let image):
                        image.resizable().scaledToFill()
                    case .empty, .failure:
                        Color.clear
                    }
                }
                .frame(width: 132, height: 92)
                .clipped()
            }
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

    private var dateBadge: some View {
        VStack(spacing: 1) {
            Text(eventMonthText)
                .font(.caption2.weight(.bold))
            Text(eventDayText)
                .font(.title2.weight(.medium))
        }
        .foregroundStyle(.purple)
        .frame(width: 56, height: 66)
        .background(Color.black.opacity(0.35))
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .stroke(Color.purple.opacity(0.9), lineWidth: 1)
        }
    }

    private func squadDestinationRow(_ circle: DriveCircle) -> some View {
        Button {
            selectCircle(circle.id)
        } label: {
            SquadShareListRow(
                name: circle.name,
                photoUrl: circle.photoUrl,
                icon: circle.icon,
                memberCount: circle.members.count,
                cacheStorageKey: "squadAvatar:\(circle.id)"
            ) {
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

    private var postButtonTitle: String {
        if selectedCircleID.isEmpty {
            return "Choose a squad"
        }
        return "Post to \(selectedCircleName)"
    }

    private func selectCircle(_ circleID: String) {
        if lockedCircleID != nil {
            selectedCircleID = circleID
            return
        }
        selectedCircleID = circleID
    }

    private func sectionLabel(_ text: String) -> some View {
        Text(text.uppercased())
            .font(.caption2.weight(.bold))
            .foregroundStyle(.white.opacity(0.56))
    }

    private var locationText: String {
        if let label = event.address?.label, !label.isEmpty { return label }
        let cityRegion = [event.address?.city, event.address?.region]
            .compactMap { $0?.isEmpty == false ? $0 : nil }
            .joined(separator: ", ")
        return cityRegion.isEmpty ? "Location TBD" : cityRegion
    }

    private var eventDetailLine: String {
        let formatter = DateFormatter()
        formatter.dateFormat = "EEE, MMM d, yyyy"
        let time = DateFormatter()
        time.timeStyle = .short
        time.dateStyle = .none
        return "\(formatter.string(from: event.startsAt)) · \(time.string(from: event.startsAt))"
    }

    private var eventMonthText: String {
        let formatter = DateFormatter()
        formatter.dateFormat = "MMM"
        return formatter.string(from: event.startsAt).uppercased()
    }

    private var eventDayText: String {
        let formatter = DateFormatter()
        formatter.dateFormat = "dd"
        return formatter.string(from: event.startsAt)
    }

    private func beginPost() {
        guard canSend else { return }
        let circleID = selectedCircleID.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !circleID.isEmpty else { return }

        isSending = true

        Task {
            do {
                try await APIClient.shared.postCircleChatEventMessage(
                    circleId: circleID,
                    eventId: event.id
                )
                await MainActor.run {
                    if event.visibility != "circle" {
                        appState.upsertUpcomingEvent(event)
                    }
                    appState.requestSquadChatFocus(circleID: circleID)
                    dismiss()
                    onPosted?()
                    appState.activeToast = AppToast(
                        text: "Posted to chat",
                        systemImage: "bubble.left.and.bubble.right.fill"
                    )
                }
            } catch {
                await MainActor.run {
                    isSending = false
                    appState.errorMessage = "Couldn't post to squad chat."
                }
            }
        }
    }
}
