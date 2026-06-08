import SwiftUI
import UIKit

enum ProfileListPreview {
    static let limit = 3
}

enum ProfileListHaptics {
    static func actionSelected() {
        let generator = UIImpactFeedbackGenerator(style: .light)
        generator.prepare()
        if #available(iOS 13.0, *) {
            generator.impactOccurred(intensity: 0.78)
        } else {
            generator.impactOccurred()
        }
    }

    static func deleteConfirmed() {
        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
    }
}

enum ProfileDriveShareFormatting {
    static func title(for drive: DriveDTO) -> String {
        ProfileDriveListFormatting.title(for: drive)
    }

    static func shareContext(for drive: DriveDTO) -> DriveChatShareContext? {
        guard drive.status == "completed" || drive.endTime != nil else { return nil }
        let end = drive.endTime ?? drive.startTime
        let seconds = max(0, end.timeIntervalSince(drive.startTime))
        return DriveChatShareContext(
            driveId: drive.id,
            previewTitle: title(for: drive),
            previewDistanceMeters: drive.distanceMeters,
            previewDriveTimeSeconds: seconds,
            previewCompletedAt: end,
            mapPreviewSnapshotInput: DriveMapPreviewSnapshotInput(route: drive.route)
        )
    }

    static func externalShareText(for context: DriveChatShareContext) -> String {
        let miles = context.previewDistanceMeters / 1609.344
        let distance = miles < 10 ? String(format: "%.1f mi", miles) : "\(Int(miles.rounded())) mi"
        let minutes = max(1, Int((context.previewDriveTimeSeconds / 60).rounded()))
        let time = minutes >= 60 ? "\(minutes / 60)h \(minutes % 60)m" : "\(minutes)m"
        let formatter = DateFormatter()
        formatter.dateFormat = "MMM d, yyyy • h:mm a"
        return "\(context.previewTitle)\n\(formatter.string(from: context.previewCompletedAt))\n\(distance) • \(time)"
    }
}

struct ProfileDrivesListSheet: View {
    @EnvironmentObject private var appState: AppState
    @Environment(\.dismiss) private var dismiss

    let isOwner: Bool
    let onShare: (DriveDTO) -> Void
    let onRenameSave: @MainActor (DriveDTO, String) async -> DriveDTO?
    let onDeleteConfirmed: @MainActor (DriveDTO) async -> Void
    let onDriveUpdated: (DriveDTO) -> Void
    let onDriveDeleted: (String) -> Void

    @State private var drives: [DriveDTO]
    @State private var navigationPath = NavigationPath()
    @State private var renameTarget: DriveDTO?
    @State private var renameDraft = ""
    @State private var isRenaming = false
    @State private var deleteTarget: DriveDTO?
    @State private var selectedPendingArchive: PendingDriveArchive?

    private var sortedPendingArchives: [PendingDriveArchive] {
        appState.pendingDriveArchives.sorted { $0.endedAt > $1.endedAt }
    }

    init(
        drives: [DriveDTO],
        isOwner: Bool,
        onShare: @escaping (DriveDTO) -> Void,
        onRenameSave: @escaping @MainActor (DriveDTO, String) async -> DriveDTO?,
        onDeleteConfirmed: @escaping @MainActor (DriveDTO) async -> Void,
        onDriveUpdated: @escaping (DriveDTO) -> Void,
        onDriveDeleted: @escaping (String) -> Void
    ) {
        _drives = State(initialValue: drives)
        self.isOwner = isOwner
        self.onShare = onShare
        self.onRenameSave = onRenameSave
        self.onDeleteConfirmed = onDeleteConfirmed
        self.onDriveUpdated = onDriveUpdated
        self.onDriveDeleted = onDriveDeleted
    }

    var body: some View {
        NavigationStack(path: $navigationPath) {
            List {
                ForEach(sortedPendingArchives) { archive in
                    ProfilePendingDriveInteractiveRow(
                        archive: archive,
                        onOpen: { selectedPendingArchive = archive }
                    )
                    .listRowInsets(EdgeInsets(top: 5, leading: 16, bottom: 5, trailing: 16))
                    .listRowBackground(Color.clear)
                    .listRowSeparator(.hidden)
                }
                ForEach(drives) { drive in
                    ProfileInteractiveDriveRow(
                        drive: drive,
                        onOpen: { navigationPath.append(drive.id) },
                        onShare: { onShare(drive) },
                        onRename: {
                            renameTarget = drive
                            renameDraft = ProfileDriveShareFormatting.title(for: drive)
                        },
                        onDelete: { deleteTarget = drive }
                    )
                    .listRowInsets(EdgeInsets(top: 5, leading: 16, bottom: 5, trailing: 16))
                    .listRowBackground(Color.clear)
                    .listRowSeparator(.hidden)
                }
            }
            .listStyle(.plain)
            .scrollContentBackground(.hidden)
            .background(Color.black)
            .navigationTitle("My Drives")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Done") {
                        dismiss()
                    }
                    .font(.body.weight(.semibold))
                }
            }
            .navigationDestination(for: String.self) { driveID in
                if let drive = drives.first(where: { $0.id == driveID }) {
                    DriveSummaryScreen(
                        drive: drive,
                        isOwner: isOwner,
                        garageCars: appState.garageCars,
                        onDriveUpdated: { updated in
                            if let index = drives.firstIndex(where: { $0.id == updated.id }) {
                                drives[index] = updated
                            }
                            onDriveUpdated(updated)
                        },
                        onDriveDeleted: {
                            drives.removeAll { $0.id == driveID }
                            if !navigationPath.isEmpty {
                                navigationPath.removeLast()
                            }
                            onDriveDeleted(driveID)
                        }
                    )
                    .environmentObject(appState)
                } else {
                    UnifiedEmptyStateView(
                        title: "Drive unavailable",
                        message: "This drive could not be loaded.",
                        systemImage: "steeringwheel"
                    )
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .background(Color.black.ignoresSafeArea())
                    .onAppear {
                        if !navigationPath.isEmpty {
                            navigationPath.removeLast()
                        }
                    }
                }
            }
        }
        .preferredColorScheme(.dark)
        .sheet(item: $selectedPendingArchive) { archive in
            PendingDriveSummaryScreen(archive: archive)
                .environmentObject(appState)
        }
        .alert("Rename drive", isPresented: Binding(
            get: { renameTarget != nil },
            set: { if !$0 { renameTarget = nil } }
        )) {
            TextField("Drive name", text: $renameDraft)
            Button("Cancel", role: .cancel) {
                renameTarget = nil
            }
            Button(isRenaming ? "Saving..." : "Save") {
                guard let drive = renameTarget else { return }
                let draft = renameDraft.trimmingCharacters(in: .whitespacesAndNewlines)
                guard !draft.isEmpty else { return }
                Task { @MainActor in
                    isRenaming = true
                    defer {
                        isRenaming = false
                        renameTarget = nil
                    }
                    if let updated = await onRenameSave(drive, draft) {
                        if let index = drives.firstIndex(where: { $0.id == updated.id }) {
                            drives[index] = updated
                        }
                        onDriveUpdated(updated)
                    }
                }
            }
            .disabled(
                renameDraft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                    || isRenaming
            )
        } message: {
            Text("This updates how the drive appears on your profile and in squad chat shares.")
        }
        .confirmationDialog(
            "Delete this drive?",
            isPresented: Binding(
                get: { deleteTarget != nil },
                set: { if !$0 { deleteTarget = nil } }
            ),
            titleVisibility: .visible
        ) {
            Button("Delete Drive", role: .destructive) {
                guard let drive = deleteTarget else { return }
                Task { @MainActor in
                    await onDeleteConfirmed(drive)
                    drives.removeAll { $0.id == drive.id }
                    deleteTarget = nil
                }
            }
            Button("Cancel", role: .cancel) {
                deleteTarget = nil
            }
        } message: {
            Text("This permanently removes the drive from your profile.")
        }
    }
}

struct ProfileRoutesListSheet: View {
    @EnvironmentObject private var appState: AppState
    @EnvironmentObject private var locationService: LocationService
    @Environment(\.dismiss) private var dismiss

    let onCreateRoute: () -> Void
    let onStartDrive: (SavedRouteDTO) -> Void
    let onShare: (SavedRouteDTO) -> Void
    let onRenameSave: @MainActor (SavedRouteDTO, String) async -> SavedRouteDTO?
    let onDeleteConfirmed: @MainActor (SavedRouteDTO) async -> Void
    let onRouteUpdated: (SavedRouteDTO) -> Void
    let onRouteDeleted: (String) -> Void

    @State private var routes: [SavedRouteDTO]
    @State private var routeForEditor: SavedRouteDTO?
    @State private var renameTarget: SavedRouteDTO?
    @State private var renameDraft = ""
    @State private var isRenaming = false
    @State private var deleteTarget: SavedRouteDTO?

    init(
        routes: [SavedRouteDTO],
        onCreateRoute: @escaping () -> Void,
        onStartDrive: @escaping (SavedRouteDTO) -> Void,
        onShare: @escaping (SavedRouteDTO) -> Void,
        onRenameSave: @escaping @MainActor (SavedRouteDTO, String) async -> SavedRouteDTO?,
        onDeleteConfirmed: @escaping @MainActor (SavedRouteDTO) async -> Void,
        onRouteUpdated: @escaping (SavedRouteDTO) -> Void,
        onRouteDeleted: @escaping (String) -> Void
    ) {
        _routes = State(initialValue: routes)
        self.onCreateRoute = onCreateRoute
        self.onStartDrive = onStartDrive
        self.onShare = onShare
        self.onRenameSave = onRenameSave
        self.onDeleteConfirmed = onDeleteConfirmed
        self.onRouteUpdated = onRouteUpdated
        self.onRouteDeleted = onRouteDeleted
    }

    var body: some View {
        NavigationStack {
            List {
                CreateRouteListRow {
                    dismiss()
                    onCreateRoute()
                }
                .listRowInsets(EdgeInsets(top: 8, leading: 16, bottom: 8, trailing: 16))
                .listRowBackground(Color.clear)
                .listRowSeparator(.hidden)

                ForEach(routes) { route in
                    ProfileInteractiveRouteRow(
                        route: route,
                        onOpen: { presentRouteEditor(route) },
                        onEdit: { presentRouteEditor(route) },
                        onStartDrive: { onStartDrive(route) },
                        onShare: { onShare(route) },
                        onRename: {
                            renameTarget = route
                            renameDraft = route.name
                        },
                        onDelete: { deleteTarget = route }
                    )
                    .listRowInsets(EdgeInsets(top: 5, leading: 16, bottom: 5, trailing: 16))
                    .listRowBackground(Color.clear)
                    .listRowSeparator(.hidden)
                }
            }
            .listStyle(.plain)
            .scrollContentBackground(.hidden)
            .background(Color.black)
            .navigationTitle("My Routes")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Done") {
                        dismiss()
                    }
                    .font(.body.weight(.semibold))
                }
            }
        }
        .fullScreenCover(item: $routeForEditor) { route in
            RouteBuilderView(route: route) { updatedRoute in
                if let index = routes.firstIndex(where: { $0.id == updatedRoute.id }) {
                    routes[index] = updatedRoute
                }
                onRouteUpdated(updatedRoute)
                routeForEditor = nil
            }
            .environmentObject(appState)
            .environmentObject(locationService)
        }
        .preferredColorScheme(.dark)
        .alert("Rename route", isPresented: Binding(
            get: { renameTarget != nil },
            set: { if !$0 { renameTarget = nil } }
        )) {
            TextField("Route name", text: $renameDraft)
            Button("Cancel", role: .cancel) {
                renameTarget = nil
            }
            Button(isRenaming ? "Saving..." : "Save") {
                guard let route = renameTarget else { return }
                let draft = renameDraft.trimmingCharacters(in: .whitespacesAndNewlines)
                guard !draft.isEmpty else { return }
                Task { @MainActor in
                    isRenaming = true
                    defer {
                        isRenaming = false
                        renameTarget = nil
                    }
                    if let updated = await onRenameSave(route, draft) {
                        if let index = routes.firstIndex(where: { $0.id == updated.id }) {
                            routes[index] = updated
                        }
                        onRouteUpdated(updated)
                    }
                }
            }
            .disabled(
                renameDraft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                    || isRenaming
            )
        } message: {
            Text("This updates how the route appears on your profile.")
        }
        .confirmationDialog(
            "Delete this route?",
            isPresented: Binding(
                get: { deleteTarget != nil },
                set: { if !$0 { deleteTarget = nil } }
            ),
            titleVisibility: .visible
        ) {
            Button("Delete Route", role: .destructive) {
                guard let route = deleteTarget else { return }
                Task { @MainActor in
                    await onDeleteConfirmed(route)
                    routes.removeAll { $0.id == route.id }
                    deleteTarget = nil
                }
            }
            Button("Cancel", role: .cancel) {
                deleteTarget = nil
            }
        } message: {
            Text("This removes the route from your saved routes.")
        }
    }

    private func presentRouteEditor(_ route: SavedRouteDTO) {
        appState.prepareRouteBuilderPresentation()
        routeForEditor = route
    }
}

struct ProfileInteractiveDriveRow: View {
    let drive: DriveDTO
    let onOpen: () -> Void
    let onShare: () -> Void
    let onRename: () -> Void
    let onDelete: () -> Void

    var body: some View {
        Button(action: onOpen) {
            ProfileDriveListRowContent(drive: drive)
        }
        .buttonStyle(.plain)
        .contextMenu {
            Button {
                ProfileListHaptics.actionSelected()
                onRename()
            } label: {
                Label("Rename", systemImage: "pencil")
            }
            Button {
                ProfileListHaptics.actionSelected()
                onShare()
            } label: {
                Label("Share", systemImage: "square.and.arrow.up")
            }
            Button(role: .destructive) {
                ProfileListHaptics.actionSelected()
                onDelete()
            } label: {
                Label("Delete", systemImage: "trash")
            }
        }
    }
}

struct ProfilePendingDriveInteractiveRow: View {
    @EnvironmentObject private var appState: AppState
    let archive: PendingDriveArchive
    let onOpen: () -> Void

    var body: some View {
        Button(action: onOpen) {
            ProfilePendingDriveListRowContent(archive: archive)
        }
        .buttonStyle(.plain)
        .contextMenu {
            Button {
                ProfileListHaptics.actionSelected()
                Task { await appState.retryPendingDriveSave(localId: archive.id) }
            } label: {
                Label(String(localized: "drive_pending_retry_save"), systemImage: "arrow.clockwise")
            }
            Button(role: .destructive) {
                ProfileListHaptics.actionSelected()
                appState.deletePendingDrive(localId: archive.id)
            } label: {
                Label(String(localized: "drive_pending_delete"), systemImage: "trash")
            }
        }
    }
}

struct ProfilePendingDriveListRowContent: View {
    let archive: PendingDriveArchive

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: "steeringwheel")
                .font(.system(size: 22, weight: .bold))
                .foregroundStyle(.white)
                .frame(width: 48, height: 48)
                .background(
                    Circle().fill(
                        LinearGradient(
                            colors: [Color.orange.opacity(0.85), Color.red.opacity(0.65)],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        )
                    )
                )

            VStack(alignment: .leading, spacing: 4) {
                HStack(spacing: 8) {
                    Text(ProfileDriveListFormatting.title(for: archive))
                        .font(.system(size: 17, weight: .semibold, design: .rounded))
                        .foregroundStyle(.white)
                        .lineLimit(1)
                    Text(String(localized: "drive_pending_not_saved_badge"))
                        .font(.caption2.weight(.bold))
                        .foregroundStyle(.orange)
                        .padding(.horizontal, 7)
                        .padding(.vertical, 3)
                        .background(Capsule().fill(Color.orange.opacity(0.18)))
                }
                Text(ProfileDriveListFormatting.subtitle(for: archive))
                    .font(.caption)
                    .foregroundStyle(.white.opacity(0.62))
                    .lineLimit(2)
            }

            Spacer(minLength: 8)

            Image(systemName: "chevron.right")
                .font(.caption.weight(.bold))
                .foregroundStyle(.white.opacity(0.42))
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 14)
        .profileListItemChrome()
    }
}

struct PendingDriveSummaryScreen: View {
    @EnvironmentObject private var appState: AppState
    @Environment(\.dismiss) private var dismiss
    let archive: PendingDriveArchive
    @State private var isRetrying = false

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    Text(ProfileDriveListFormatting.title(for: archive))
                        .font(.title2.weight(.bold))
                        .foregroundStyle(.white)
                    Text(ProfileDriveListFormatting.subtitle(for: archive))
                        .font(.subheadline)
                        .foregroundStyle(.white.opacity(0.62))
                    Text(String(localized: "drive_pending_not_saved_badge"))
                        .font(.caption.weight(.bold))
                        .foregroundStyle(.orange)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 5)
                        .background(Capsule().fill(Color.orange.opacity(0.18)))

                    Button {
                        guard !isRetrying else { return }
                        isRetrying = true
                        Task {
                            await appState.retryPendingDriveSave(localId: archive.id)
                            await MainActor.run {
                                isRetrying = false
                                if !appState.pendingDriveArchives.contains(where: { $0.id == archive.id }) {
                                    dismiss()
                                }
                            }
                        }
                    } label: {
                        Label(String(localized: "drive_pending_retry_save"), systemImage: "arrow.clockwise")
                            .font(.headline.weight(.semibold))
                            .foregroundStyle(.white)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 14)
                            .background(Color.purple)
                            .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                    }
                    .buttonStyle(.plain)
                    .disabled(isRetrying)
                }
                .padding(20)
            }
            .background(Color.black.ignoresSafeArea())
            .navigationTitle(String(localized: "drive_pending_summary_title"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Done") { dismiss() }
                }
            }
        }
        .preferredColorScheme(.dark)
    }
}

struct ProfileInteractiveRouteRow: View {
    let route: SavedRouteDTO
    let onOpen: () -> Void
    let onEdit: () -> Void
    let onStartDrive: () -> Void
    let onShare: () -> Void
    let onRename: () -> Void
    let onDelete: () -> Void

    var body: some View {
        Button(action: onOpen) {
            ProfileRouteListRowContent(route: route)
        }
        .buttonStyle(.plain)
        .contextMenu {
            Button {
                ProfileListHaptics.actionSelected()
                onStartDrive()
            } label: {
                Label("Start Drive", systemImage: "location.north.fill")
            }
            Button {
                ProfileListHaptics.actionSelected()
                onRename()
            } label: {
                Label("Rename", systemImage: "character.cursor.ibeam")
            }
            Button {
                ProfileListHaptics.actionSelected()
                onEdit()
            } label: {
                Label("Edit", systemImage: "pencil")
            }
            Button {
                ProfileListHaptics.actionSelected()
                onShare()
            } label: {
                Label("Share", systemImage: "square.and.arrow.up")
            }
            Button(role: .destructive) {
                ProfileListHaptics.actionSelected()
                onDelete()
            } label: {
                Label("Delete", systemImage: "trash")
            }
        }
    }
}

@ViewBuilder
func profileDriveList<Row: View>(
    drives: [DriveDTO],
    @ViewBuilder row: @escaping (DriveDTO) -> Row
) -> some View {
    List {
        ForEach(drives) { drive in
            row(drive)
                .listRowInsets(EdgeInsets(top: 5, leading: 16, bottom: 5, trailing: 16))
                .listRowBackground(Color.clear)
                .listRowSeparator(.hidden)
        }
    }
    .listStyle(.plain)
    .scrollContentBackground(.hidden)
    .background(Color.black)
}

@ViewBuilder
func profileRouteList<Row: View>(
    routes: [SavedRouteDTO],
    @ViewBuilder row: @escaping (SavedRouteDTO) -> Row
) -> some View {
    List {
        ForEach(routes) { route in
            row(route)
                .listRowInsets(EdgeInsets(top: 5, leading: 16, bottom: 5, trailing: 16))
                .listRowBackground(Color.clear)
                .listRowSeparator(.hidden)
        }
    }
    .listStyle(.plain)
    .scrollContentBackground(.hidden)
    .background(Color.black)
}

@ViewBuilder
func profileEmbeddedDriveList<Row: View>(
    drives: [DriveDTO],
    @ViewBuilder row: @escaping (DriveDTO) -> Row
) -> some View {
    List {
        ForEach(drives) { drive in
            row(drive)
                .listRowInsets(EdgeInsets(top: 5, leading: 0, bottom: 5, trailing: 0))
                .listRowBackground(Color.clear)
                .listRowSeparator(.hidden)
        }
    }
    .listStyle(.plain)
    .scrollContentBackground(.hidden)
    .scrollDisabled(true)
    .scrollBounceBehavior(.basedOnSize)
    .frame(minHeight: profileEmbeddedListHeight(itemCount: drives.count))
}

@ViewBuilder
func profileEmbeddedRouteList<Row: View>(
    routes: [SavedRouteDTO],
    @ViewBuilder row: @escaping (SavedRouteDTO) -> Row
) -> some View {
    List {
        ForEach(routes) { route in
            row(route)
                .listRowInsets(EdgeInsets(top: 5, leading: 0, bottom: 5, trailing: 0))
                .listRowBackground(Color.clear)
                .listRowSeparator(.hidden)
        }
    }
    .listStyle(.plain)
    .scrollContentBackground(.hidden)
    .scrollDisabled(true)
    .scrollBounceBehavior(.basedOnSize)
    .frame(minHeight: profileEmbeddedListHeight(itemCount: routes.count))
}

private func profileEmbeddedListHeight(itemCount: Int) -> CGFloat {
    max(0, CGFloat(itemCount) * ProfileListRowLayout.minHeight)
}

enum ProfileListRowLayout {
    static let minHeight: CGFloat = 86
}

private extension View {
    func profileListItemChrome() -> some View {
        background(Color.white.opacity(0.055))
            .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .stroke(Color.white.opacity(0.1), lineWidth: 1)
            }
    }
}

struct ProfileDriveListRowContent: View {
    let drive: DriveDTO

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: "steeringwheel")
                .font(.system(size: 22, weight: .bold))
                .foregroundStyle(.white)
                .frame(width: 48, height: 48)
                .background(
                    Circle().fill(
                        LinearGradient(
                            colors: [Color.purple, Color.blue.opacity(0.85)],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        )
                    )
                )
                .shadow(color: .purple.opacity(0.35), radius: 10, y: 3)

            VStack(alignment: .leading, spacing: 4) {
                Text(ProfileDriveListFormatting.title(for: drive))
                    .font(.system(size: 17, weight: .semibold, design: .rounded))
                    .foregroundStyle(.white)
                    .lineLimit(1)
                Text(ProfileDriveListFormatting.subtitle(for: drive))
                    .font(.caption)
                    .foregroundStyle(.white.opacity(0.62))
                    .lineLimit(1)
            }

            Spacer(minLength: 8)

            Image(systemName: "chevron.right")
                .font(.caption.weight(.bold))
                .foregroundStyle(.white.opacity(0.42))
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 14)
        .profileListItemChrome()
    }
}

struct ProfileRouteListRowContent: View {
    let route: SavedRouteDTO

    var body: some View {
        HStack(spacing: 12) {
            SavedRouteListIcon(size: 48)

            VStack(alignment: .leading, spacing: 4) {
                Text(route.name)
                    .font(.system(size: 17, weight: .semibold, design: .rounded))
                    .foregroundStyle(.white)
                    .lineLimit(1)
                Text(ProfileRouteListFormatting.subtitle(for: route))
                    .font(.caption)
                    .foregroundStyle(.white.opacity(0.62))
                    .lineLimit(1)
            }

            Spacer(minLength: 8)

            Image(systemName: "chevron.right")
                .font(.caption.weight(.bold))
                .foregroundStyle(.white.opacity(0.42))
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 14)
        .profileListItemChrome()
    }
}

enum ProfileDriveListFormatting {
    static func title(for drive: DriveDTO) -> String {
        DriveDisplayNaming.listTitle(routeName: drive.route?.name, driveTitle: drive.title)
    }

    static func subtitle(for drive: DriveDTO) -> String {
        [
            driveDistanceText(drive.distanceMeters),
            driveDurationText(drive),
            relativeDateText(drive.startTime)
        ]
        .filter { !$0.isEmpty }
        .joined(separator: " • ")
    }

    static func title(for archive: PendingDriveArchive) -> String {
        let trimmed = archive.title.trimmingCharacters(in: .whitespacesAndNewlines)
        if !trimmed.isEmpty { return trimmed }
        if let routeName = archive.routeName?.trimmingCharacters(in: .whitespacesAndNewlines), !routeName.isEmpty {
            return routeName
        }
        switch archive.kind {
        case DriveSessionKind.route.rawValue: return "Route Drive"
        case DriveSessionKind.live.rawValue: return "Live Drive Session"
        default: return "Quick Drive"
        }
    }

    static func subtitle(for archive: PendingDriveArchive) -> String {
        let seconds = max(0, archive.endedAt.timeIntervalSince(archive.startedAt))
        let duration = durationText(seconds: seconds)
        let expiry = String(
            format: String(localized: "drive_pending_expires_format"),
            locale: Locale.current,
            archive.daysUntilExpiry
        )
        return [
            driveDistanceText(archive.distanceMeters),
            duration,
            expiry
        ]
        .filter { !$0.isEmpty }
        .joined(separator: " • ")
    }

    private static func durationText(seconds: TimeInterval) -> String {
        guard seconds > 0 else { return "" }
        let hours = Int(seconds) / 3600
        let minutes = (Int(seconds) % 3600) / 60
        if hours > 0 {
            return "\(hours)h \(minutes)m"
        }
        return "\(minutes)m"
    }

    private static func driveDistanceText(_ meters: Double) -> String {
        let miles = meters / 1609.344
        if miles < 10 {
            return String(format: "%.1f mi", miles)
        }
        return "\(Int(miles.rounded())) mi"
    }

    private static func driveDurationText(_ drive: DriveDTO) -> String {
        guard let endTime = drive.endTime else { return "" }
        let seconds = max(0, endTime.timeIntervalSince(drive.startTime))
        guard seconds > 0 else { return "0m" }
        let hours = Int(seconds) / 3600
        let minutes = (Int(seconds) % 3600) / 60
        if hours > 0 {
            return "\(hours)h \(minutes)m"
        }
        return "\(minutes)m"
    }

    private static func relativeDateText(_ date: Date) -> String {
        let seconds = max(0, Int(Date().timeIntervalSince(date)))
        if seconds < 60 { return "Just now" }
        let minutes = seconds / 60
        if minutes < 60 { return "\(minutes)m ago" }
        let hours = minutes / 60
        if hours < 48 { return "\(hours)h ago" }
        return "\(hours / 24)d ago"
    }
}

enum ProfileRouteListFormatting {
    static func subtitle(for route: SavedRouteDTO) -> String {
        var parts: [String] = []
        if route.distanceMeters > 0 {
            let measurement = Measurement(value: route.distanceMeters, unit: UnitLength.meters)
            parts.append(distanceFormatter.string(from: measurement))
        }
        parts.append("\(route.points.count) points")
        return parts.joined(separator: " • ")
    }

    private static let distanceFormatter: MeasurementFormatter = {
        let formatter = MeasurementFormatter()
        formatter.unitOptions = .naturalScale
        formatter.unitStyle = .medium
        formatter.numberFormatter.maximumFractionDigits = 1
        return formatter
    }()
}

@MainActor
enum ProfileDriveDeletion {
    static func delete(_ drive: DriveDTO, onDeleted: (String) -> Void) async -> String? {
        do {
            try await APIClient.shared.deleteDrive(driveId: drive.id)
            ProfileListHaptics.deleteConfirmed()
            onDeleted(drive.id)
            return nil
        } catch {
            return "Couldn't delete this drive."
        }
    }
}

@MainActor
enum ProfileRouteDeletion {
    static func delete(_ route: SavedRouteDTO, onDeleted: (String) -> Void) async -> String? {
        do {
            try await APIClient.shared.deleteRoute(routeId: route.id)
            ProfileListHaptics.deleteConfirmed()
            onDeleted(route.id)
            return nil
        } catch {
            return "Couldn't delete this route."
        }
    }
}

struct ProfilePlacesListSheet: View {
    @EnvironmentObject private var appState: AppState
    @Environment(\.dismiss) private var dismiss

    let onRenameSave: @MainActor (SavedPlaceDTO, String) async -> SavedPlaceDTO?
    let onDeleteConfirmed: @MainActor (SavedPlaceDTO) async -> Void
    let onOpenPlace: (SavedPlaceDTO) -> Void

    @State private var places: [SavedPlaceDTO]
    @State private var renameTarget: SavedPlaceDTO?
    @State private var renameDraft = ""
    @State private var isRenaming = false
    @State private var deleteTarget: SavedPlaceDTO?

    init(
        places: [SavedPlaceDTO],
        onRenameSave: @escaping @MainActor (SavedPlaceDTO, String) async -> SavedPlaceDTO?,
        onDeleteConfirmed: @escaping @MainActor (SavedPlaceDTO) async -> Void,
        onOpenPlace: @escaping (SavedPlaceDTO) -> Void
    ) {
        _places = State(initialValue: places)
        self.onRenameSave = onRenameSave
        self.onDeleteConfirmed = onDeleteConfirmed
        self.onOpenPlace = onOpenPlace
    }

    var body: some View {
        NavigationStack {
            profilePlaceList(places: places) { place in
                ProfileInteractivePlaceRow(
                    place: place,
                    onOpen: {
                        dismiss()
                        onOpenPlace(place)
                    },
                    onRename: {
                        renameTarget = place
                        renameDraft = place.name
                    },
                    onDelete: { deleteTarget = place }
                )
            }
            .navigationTitle(String(localized: "profile_my_places_heading"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Done") {
                        dismiss()
                    }
                    .font(.body.weight(.semibold))
                }
            }
        }
        .preferredColorScheme(.dark)
        .alert("Rename place", isPresented: Binding(
            get: { renameTarget != nil },
            set: { if !$0 { renameTarget = nil } }
        )) {
            TextField("Place name", text: $renameDraft)
            Button("Cancel", role: .cancel) {
                renameTarget = nil
            }
            Button(isRenaming ? "Saving..." : "Save") {
                guard let place = renameTarget else { return }
                let draft = renameDraft.trimmingCharacters(in: .whitespacesAndNewlines)
                guard !draft.isEmpty else { return }
                Task { @MainActor in
                    isRenaming = true
                    defer {
                        isRenaming = false
                        renameTarget = nil
                    }
                    if let updated = await onRenameSave(place, draft) {
                        if let index = places.firstIndex(where: { $0.id == updated.id }) {
                            places[index] = updated
                        }
                    }
                }
            }
            .disabled(
                renameDraft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                    || isRenaming
            )
        }
        .confirmationDialog(
            String(localized: "marker_detail_delete_place_title"),
            isPresented: Binding(
                get: { deleteTarget != nil },
                set: { if !$0 { deleteTarget = nil } }
            ),
            titleVisibility: .visible
        ) {
            Button(String(localized: "marker_detail_action_remove"), role: .destructive) {
                guard let place = deleteTarget else { return }
                Task { @MainActor in
                    await onDeleteConfirmed(place)
                    places.removeAll { $0.id == place.id }
                    deleteTarget = nil
                }
            }
            Button(String(localized: "marker_detail_cancel"), role: .cancel) {
                deleteTarget = nil
            }
        } message: {
            if let place = deleteTarget {
                Text(place.name)
            }
        }
    }
}

struct ProfileInteractivePlaceRow: View {
    let place: SavedPlaceDTO
    let onOpen: () -> Void
    let onRename: () -> Void
    let onDelete: () -> Void

    var body: some View {
        Button(action: onOpen) {
            ProfilePlaceListRowContent(place: place)
        }
        .buttonStyle(.plain)
        .contextMenu {
            Button {
                ProfileListHaptics.actionSelected()
                onRename()
            } label: {
                Label("Rename", systemImage: "pencil")
            }
            Button(role: .destructive) {
                ProfileListHaptics.actionSelected()
                onDelete()
            } label: {
                Label(String(localized: "marker_detail_action_remove"), systemImage: "trash")
            }
        }
    }
}

struct ProfilePlaceListRowContent: View {
    let place: SavedPlaceDTO

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: "bookmark.fill")
                .font(.system(size: 20, weight: .bold))
                .foregroundStyle(.white)
                .frame(width: 48, height: 48)
                .background(
                    Circle().fill(RouteMapMarkerColors.discoverySavedPlaceTeal.opacity(0.92))
                )

            VStack(alignment: .leading, spacing: 4) {
                Text(place.name)
                    .font(.system(size: 17, weight: .semibold, design: .rounded))
                    .foregroundStyle(.white)
                    .lineLimit(1)
                Text(ProfilePlaceListFormatting.subtitle(for: place))
                    .font(.caption)
                    .foregroundStyle(.white.opacity(0.62))
                    .lineLimit(1)
            }

            Spacer(minLength: 8)

            Image(systemName: "chevron.right")
                .font(.caption.weight(.bold))
                .foregroundStyle(.white.opacity(0.42))
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 14)
        .profileListItemChrome()
    }
}

enum ProfilePlaceListFormatting {
    static func subtitle(for place: SavedPlaceDTO) -> String {
        if let address = place.addressSummary?.trimmingCharacters(in: .whitespacesAndNewlines), !address.isEmpty {
            return address
        }
        if let kind = place.placeKind?.replacingOccurrences(of: "_", with: " ").capitalized, !kind.isEmpty {
            return kind
        }
        return String(format: "%.4f, %.4f", place.latitude, place.longitude)
    }
}

@ViewBuilder
func profilePlaceList<Row: View>(
    places: [SavedPlaceDTO],
    @ViewBuilder row: @escaping (SavedPlaceDTO) -> Row
) -> some View {
    List {
        ForEach(places) { place in
            row(place)
                .listRowInsets(EdgeInsets(top: 5, leading: 16, bottom: 5, trailing: 16))
                .listRowBackground(Color.clear)
                .listRowSeparator(.hidden)
        }
    }
    .listStyle(.plain)
    .scrollContentBackground(.hidden)
    .background(Color.black)
}

@ViewBuilder
func profileEmbeddedPlaceList<Row: View>(
    places: [SavedPlaceDTO],
    @ViewBuilder row: @escaping (SavedPlaceDTO) -> Row
) -> some View {
    List {
        ForEach(places) { place in
            row(place)
                .listRowInsets(EdgeInsets(top: 5, leading: 0, bottom: 5, trailing: 0))
                .listRowBackground(Color.clear)
                .listRowSeparator(.hidden)
        }
    }
    .listStyle(.plain)
    .scrollContentBackground(.hidden)
    .scrollDisabled(true)
    .scrollBounceBehavior(.basedOnSize)
    .frame(minHeight: profileEmbeddedListHeight(itemCount: places.count))
}
