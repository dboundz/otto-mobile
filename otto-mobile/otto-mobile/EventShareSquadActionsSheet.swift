import SwiftUI

struct EventShareSquadActionsSheet: View {
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject private var appState: AppState

    let event: EventDTO
    var lockedCircleID: String? = nil
    var onAssociationsSaved: (([EventAttachedSquadDTO]) -> Void)? = nil
    /// Called after a successful squad chat post (parent may dismiss marker/event chrome).
    var onPostedToChat: (() -> Void)? = nil

    @State private var adminSquads: [AdminSquadDTO] = []
    @State private var selectedSquadIDs: Set<String> = []
    @State private var isLoadingAdminSquads = true
    @State private var togglingSquadIDs: Set<String> = []
    @State private var errorMessage: String?
    @State private var isShowingChatPostSheet = false

    private var isSquadNativeEvent: Bool {
        event.visibility == "circle"
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 24) {
                    shareSection
                    if !isSquadNativeEvent, !adminSquads.isEmpty {
                        addToSquadsSection
                    }
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
        .task {
            await loadAdminContext()
        }
        .sheet(isPresented: $isShowingChatPostSheet) {
            EventChatDestinationSheet(
                event: event,
                circles: appState.circles,
                lockedCircleID: lockedCircleID,
                onPosted: finishEventShareToChatFlow
            )
            .environmentObject(appState)
        }
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
            if !isSquadNativeEvent {
                ShareLink(
                    item: eventShareURL ?? URL(string: "https://driftd.com")!,
                    subject: Text("\(event.name) on Driftd"),
                    message: Text("View \(event.name) on Driftd.")
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

    private var addToSquadsSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Add to Squads")
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(.white.opacity(0.72))
            Text("Add or remove this event from squads you manage.")
                .font(.caption)
                .foregroundStyle(.secondary)
            if let errorMessage {
                Text(errorMessage)
                    .font(.caption)
                    .foregroundStyle(.red.opacity(0.9))
            }
            if isLoadingAdminSquads {
                ProgressView().tint(.purple)
            } else {
                ForEach(adminSquads) { squad in
                    let circle = appState.circles.first(where: { $0.id == squad.id })
                    SquadToggleSettingCardResolved(
                        squadId: squad.id,
                        name: squad.name,
                        photoUrl: circle?.photoUrl ?? squad.photoUrl,
                        memberCount: circle?.members.count ?? 0,
                        isOn: Binding(
                            get: { selectedSquadIDs.contains(squad.id) },
                            set: { isOn in
                                Task { await applySquadToggle(squad: squad, targetOn: isOn) }
                            }
                        ),
                        trailingText: squad.role,
                        enabled: !togglingSquadIDs.contains(squad.id)
                    )
                }
                Text("Only squads you own or manage are shown.")
                    .font(.caption2)
                    .foregroundStyle(.secondary)
            }
        }
    }

    private func finishEventShareToChatFlow() {
        isShowingChatPostSheet = false
        dismiss()
        onPostedToChat?()
    }

    private var eventShareURL: URL? {
        let ref = event.slug ?? event.id
        return WebsiteLinks.event(eventRef: ref)
    }

    private func loadAdminContext() async {
        isLoadingAdminSquads = true
        defer { isLoadingAdminSquads = false }
        do {
            async let squadsTask = APIClient.shared.fetchAdminSquads()
            async let attachedTask = APIClient.shared.fetchEventSquadAssociations(eventId: event.id)
            let (squads, attached) = try await (squadsTask, attachedTask)
            adminSquads = squads
            selectedSquadIDs = Set(attached.map(\.id))
        } catch {
            adminSquads = []
            errorMessage = "Couldn't load squad options."
        }
    }

    private func applySquadToggle(squad: AdminSquadDTO, targetOn: Bool) async {
        let alreadyOn = selectedSquadIDs.contains(squad.id)
        guard alreadyOn != targetOn, !togglingSquadIDs.contains(squad.id) else { return }

        let previous = selectedSquadIDs
        if targetOn {
            selectedSquadIDs.insert(squad.id)
        } else {
            selectedSquadIDs.remove(squad.id)
        }
        togglingSquadIDs.insert(squad.id)
        errorMessage = nil
        defer { togglingSquadIDs.remove(squad.id) }

        do {
            let updated = try await APIClient.shared.patchEventSquadAssociations(
                eventId: event.id,
                squadIds: Array(selectedSquadIDs)
            )
            selectedSquadIDs = Set(updated.map(\.id))
            onAssociationsSaved?(updated)
            let toastText =
                targetOn
                    ? String(format: String(localized: "event_squad_association_added_format"), squad.name)
                    : String(format: String(localized: "event_squad_association_removed_format"), squad.name)
            appState.activeToast = AppToast(
                text: toastText,
                systemImage: targetOn ? "checkmark.circle.fill" : "minus.circle.fill"
            )
        } catch {
            selectedSquadIDs = previous
            errorMessage = String(localized: "event_share_squad_actions_save_error")
        }
    }
}
