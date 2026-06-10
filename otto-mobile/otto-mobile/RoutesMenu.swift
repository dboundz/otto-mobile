import SwiftUI

struct RoutesMenu: View {
    let onCreateRoute: () -> Void
    let onSelectRoute: (SavedRouteDTO) -> Void
    let onEditRoute: (SavedRouteDTO) -> Void
    let onDeleteRoute: (SavedRouteDTO) -> Void

    @EnvironmentObject private var appState: AppState
    @Environment(\.dismiss) private var dismiss
    @State private var routes: [SavedRouteDTO] = []
    @State private var isLoading = false
    @State private var errorMessage: String?
    @State private var routeToRename: SavedRouteDTO?
    @State private var routeNameDraft = ""
    @State private var routeToDelete: SavedRouteDTO?
    @State private var routeToShare: SavedRouteDTO?

    private var ownedRoutes: [SavedRouteDTO] {
        routes.filter(isOwned)
    }

    private var sharedRoutes: [SavedRouteDTO] {
        routes.filter { !isOwned($0) }
    }

    var body: some View {
        navigationStack
            .presentationDetents([.medium, .large])
            .presentationBackground(Color.black)
            .task {
                await loadRoutes()
            }
            .renameRouteAlert(
                routeToRename: $routeToRename,
                routeNameDraft: $routeNameDraft,
                onSave: { route, name in
                    Task { await renameRoute(route, to: name) }
                }
            )
            .deleteRouteConfirmation(
                routeToDelete: $routeToDelete,
                onDelete: { route in
                    Task { await deleteRoute(route) }
                }
            )
            .sheet(item: $routeToShare, content: routeShareSheet)
    }

    private var navigationStack: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: OttoScreenChrome.stackSpacing) {
                    OttoMapSheetHeader(title: "Routes", onDone: { dismiss() })
                    createRouteButton
                    routesMainContent
                }
                .padding(.horizontal, OttoScreenChrome.horizontalPadding)
                .padding(.top, OttoScreenChrome.topPadding)
                .padding(.bottom, OttoScreenChrome.bottomPadding)
            }
            .scrollDismissesKeyboard(.interactively)
            .background(Color.black.ignoresSafeArea())
        }
    }

    @ViewBuilder
    private var routesMainContent: some View {
        if isLoading {
            loadingCard
        } else if let errorMessage {
            errorCard(message: errorMessage)
        } else if routes.isEmpty {
            UnifiedEmptyStateView(
                title: "No routes yet",
                message: "Create a route for a drive, rally, or cruise.",
                systemImage: SavedRouteIcon.systemImageName
            )
            .frame(minHeight: 220)
        } else {
            if !ownedRoutes.isEmpty {
                routeSection(title: "My Routes", routes: ownedRoutes)
            }
            if !sharedRoutes.isEmpty {
                routeSection(title: "Shared With You", routes: sharedRoutes)
            }
        }
    }

    private var loadingCard: some View {
        ProgressView()
            .frame(maxWidth: .infinity, alignment: .center)
            .padding(22)
            .background(listCardBackground)
    }

    private func errorCard(message: String) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(message)
                .foregroundStyle(.white.opacity(0.64))
            Button("Try Again") {
                Task { await loadRoutes() }
            }
            .font(.subheadline.weight(.semibold))
            .foregroundStyle(.purple)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(listCardBackground)
    }

    private var listCardBackground: some View {
        RoundedRectangle(cornerRadius: 16, style: .continuous)
            .fill(Color.white.opacity(0.05))
            .overlay(
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .stroke(Color.white.opacity(0.12), lineWidth: 1)
            )
    }

    @ViewBuilder
    private func routeShareSheet(route: SavedRouteDTO) -> some View {
        RouteShareSquadActionsSheet(
            route: route,
            externalShareText: ProfileRouteShareFormatting.externalShareText(for: route),
            externalShareSubject: route.name,
            canShare: isOwned(route)
        )
        .environmentObject(appState)
        .presentationDetents([.medium, .large])
    }

    private var createRouteButton: some View {
        CreateRouteListRow {
            dismiss()
            onCreateRoute()
        }
    }

    private func routeSection(title: String, routes: [SavedRouteDTO]) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(title)
                .font(.caption.weight(.heavy))
                .textCase(.uppercase)
                .foregroundStyle(.white.opacity(0.56))

            routesListCard(routes)
        }
    }

    private func routesListCard(_ routes: [SavedRouteDTO]) -> some View {
        LazyVStack(spacing: 0) {
            ForEach(Array(routes.enumerated()), id: \.element.id) { index, route in
                routeRow(route)

                if index < routes.count - 1 {
                    Divider().overlay(Color.white.opacity(0.08))
                        .padding(.leading, 72)
                }
            }
        }
        .background(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .fill(Color.white.opacity(0.05))
        )
        .overlay(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .stroke(Color.white.opacity(0.12), lineWidth: 1)
        )
    }

    private func routeRow(_ route: SavedRouteDTO) -> some View {
        HStack(spacing: 12) {
            Button {
                dismiss()
                onSelectRoute(route)
            } label: {
                HStack(spacing: 12) {
                    SavedRouteListIcon()
                    routeRowText(route)
                    Spacer()
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)

            routeOptionsMenu(route)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 11)
        .frame(maxWidth: .infinity, alignment: .leading)
        .contentShape(Rectangle())
    }

    private func routeRowText(_ route: SavedRouteDTO) -> some View {
        VStack(alignment: .leading, spacing: 3) {
            Text(route.name)
                .font(.headline)
                .foregroundStyle(.white)
                .lineLimit(1)

            Text(routePrimaryMetadata(for: route))
                .font(.subheadline)
                .foregroundStyle(Color.white.opacity(0.64))
                .lineLimit(1)

            if let updated = formattedDate(route.updatedAt) {
                Text(isOwned(route) ? "Updated \(updated)" : "Shared by \(routeOwnerDisplayName(for: route))")
                    .font(.caption2)
                    .foregroundStyle(Color.white.opacity(0.38))
                    .lineLimit(1)
            }
        }
    }

    private func routeOptionsMenu(_ route: SavedRouteDTO) -> some View {
        Menu {
            if isOwned(route) {
                Button {
                    dismiss()
                    onEditRoute(route)
                } label: {
                    Label("Edit Route", systemImage: "pencil")
                }
                Button {
                    routeNameDraft = route.name
                    routeToRename = route
                } label: {
                    Label("Rename Route", systemImage: "text.cursor")
                }
                Button {
                    routeToShare = route
                } label: {
                    Label("Share", systemImage: "square.and.arrow.up")
                }
                Button(role: .destructive) {
                    routeToDelete = route
                } label: {
                    Label("Delete Route", systemImage: "trash")
                }
            } else {
                Button {
                    errorMessage = "Only route owners can share to chat."
                } label: {
                    Label("Share", systemImage: "square.and.arrow.up")
                }
            }
        } label: {
            Image(systemName: "ellipsis")
                .font(.subheadline.weight(.bold))
                .foregroundStyle(.white.opacity(0.62))
                .frame(width: 34, height: 34)
                .background(Circle().fill(Color.white.opacity(0.045)))
        }
        .buttonStyle(.plain)
    }

    private func loadRoutes() async {
        isLoading = true
        errorMessage = nil
        do {
            async let ownedTask = APIClient.shared.fetchRoutes()
            async let sharedTask = APIClient.shared.fetchSharedWithMeRoutes()
            let ownedRoutes = try await ownedTask
            let sharedResponse = try await sharedTask
            let ownedIDs = Set(ownedRoutes.map(\.id))
            let sharedRoutes = sharedResponse.routes.filter { !ownedIDs.contains($0.id) }
            routes = ownedRoutes + sharedRoutes
        } catch {
            errorMessage = "Couldn't load routes."
        }
        isLoading = false
    }

    private func renameRoute(_ route: SavedRouteDTO, to draftName: String) async {
        let trimmedName = draftName.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedName.isEmpty else { return }
        do {
            let updated = try await APIClient.shared.updateRoute(
                routeId: route.id,
                name: trimmedName,
                points: route.points,
                roadCoordinates: route.roadCoordinates,
                distanceMeters: route.distanceMeters,
                etaSeconds: route.etaSeconds
            )
            if let index = routes.firstIndex(where: { $0.id == route.id }) {
                routes[index] = updated
            }
            routeToRename = nil
        } catch {
            errorMessage = "Couldn't rename this route."
        }
    }

    private func deleteRoute(_ route: SavedRouteDTO) async {
        do {
            try await APIClient.shared.deleteRoute(routeId: route.id)
            routes.removeAll { $0.id == route.id }
            routeToDelete = nil
            onDeleteRoute(route)
            appState.presentDeletedToast(for: "Route")
        } catch {
            appState.presentDeleteFailedToast(for: "route")
        }
    }

    private func isOwned(_ route: SavedRouteDTO) -> Bool {
        route.createdByUserId == appState.currentUserID
    }

    private func routePrimaryMetadata(for route: SavedRouteDTO) -> String {
        var parts: [String] = []
        if route.distanceMeters > 0 {
            parts.append(Self.distanceFormatter.string(from: Measurement(value: route.distanceMeters, unit: UnitLength.meters)))
        }
        parts.append("\(route.points.count) points")
        return parts.joined(separator: " • ")
    }

    private func routeOwnerDisplayName(for route: SavedRouteDTO) -> String {
        let ownerId = route.createdByUserId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !ownerId.isEmpty else { return "another driver" }
        if ownerId == appState.currentUserID { return "you" }
        return appState.allUsers.first(where: { $0.id == ownerId })?.displayName ?? "another driver"
    }

    private func formattedDate(_ raw: String?) -> String? {
        guard let raw else { return nil }
        let date = Self.isoFormatter.date(from: raw) ?? Self.isoFormatterWithoutFractions.date(from: raw)
        guard let date else { return nil }
        return Self.relativeFormatter.localizedString(for: date, relativeTo: Date())
    }

    private static let distanceFormatter: MeasurementFormatter = {
        let formatter = MeasurementFormatter()
        formatter.unitOptions = .naturalScale
        formatter.unitStyle = .medium
        formatter.numberFormatter.maximumFractionDigits = 1
        return formatter
    }()

    private static let isoFormatter: ISO8601DateFormatter = {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return formatter
    }()

    private static let isoFormatterWithoutFractions: ISO8601DateFormatter = {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        return formatter
    }()

    private static let relativeFormatter: RelativeDateTimeFormatter = {
        let formatter = RelativeDateTimeFormatter()
        formatter.unitsStyle = .abbreviated
        return formatter
    }()
}

private extension View {
    func renameRouteAlert(
        routeToRename: Binding<SavedRouteDTO?>,
        routeNameDraft: Binding<String>,
        onSave: @escaping (SavedRouteDTO, String) -> Void
    ) -> some View {
        alert("Rename Route", isPresented: Binding(
            get: { routeToRename.wrappedValue != nil },
            set: { if !$0 { routeToRename.wrappedValue = nil } }
        )) {
            TextField("Route name", text: routeNameDraft)
            Button("Cancel", role: .cancel) {
                routeToRename.wrappedValue = nil
            }
            Button("Save") {
                guard let route = routeToRename.wrappedValue else { return }
                onSave(route, routeNameDraft.wrappedValue)
            }
        } message: {
            Text("Give this route a new name.")
        }
    }

    func deleteRouteConfirmation(
        routeToDelete: Binding<SavedRouteDTO?>,
        onDelete: @escaping (SavedRouteDTO) -> Void
    ) -> some View {
        confirmationDialog(
            "Delete this route?",
            isPresented: Binding(
                get: { routeToDelete.wrappedValue != nil },
                set: { if !$0 { routeToDelete.wrappedValue = nil } }
            ),
            titleVisibility: .visible
        ) {
            Button("Delete Route", role: .destructive) {
                guard let route = routeToDelete.wrappedValue else { return }
                onDelete(route)
            }
            Button("Cancel", role: .cancel) {
                routeToDelete.wrappedValue = nil
            }
        } message: {
            Text("This removes the route from your saved routes.")
        }
    }
}
