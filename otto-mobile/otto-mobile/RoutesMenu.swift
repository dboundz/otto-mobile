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

    private var ownedRoutes: [SavedRouteDTO] {
        routes.filter(isOwned)
    }

    private var sharedRoutes: [SavedRouteDTO] {
        routes.filter { !isOwned($0) }
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: OttoScreenChrome.stackSpacing) {
                    OttoMapSheetHeader(title: "Routes", onDone: { dismiss() })

                    createRouteButton

                    if isLoading {
                        ProgressView()
                            .frame(maxWidth: .infinity, alignment: .center)
                            .padding(22)
                            .background(
                                RoundedRectangle(cornerRadius: 16, style: .continuous)
                                    .fill(Color.white.opacity(0.05))
                            )
                            .overlay(
                                RoundedRectangle(cornerRadius: 16, style: .continuous)
                                    .stroke(Color.white.opacity(0.12), lineWidth: 1)
                            )
                    } else if let errorMessage {
                        VStack(alignment: .leading, spacing: 8) {
                            Text(errorMessage)
                                .foregroundStyle(.white.opacity(0.64))
                            Button("Try Again") {
                                Task { await loadRoutes() }
                            }
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(.purple)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(16)
                        .background(
                            RoundedRectangle(cornerRadius: 16, style: .continuous)
                                .fill(Color.white.opacity(0.05))
                        )
                        .overlay(
                            RoundedRectangle(cornerRadius: 16, style: .continuous)
                                .stroke(Color.white.opacity(0.12), lineWidth: 1)
                        )
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
                .padding(.horizontal, OttoScreenChrome.horizontalPadding)
                .padding(.top, OttoScreenChrome.topPadding)
                .padding(.bottom, OttoScreenChrome.bottomPadding)
            }
            .scrollDismissesKeyboard(.interactively)
            .background(Color.black.ignoresSafeArea())
            .task {
                await loadRoutes()
            }
        }
        .presentationDetents([.medium, .large])
        .presentationBackground(Color.black)
        .alert("Rename Route", isPresented: Binding(
            get: { routeToRename != nil },
            set: { if !$0 { routeToRename = nil } }
        )) {
            TextField("Route name", text: $routeNameDraft)
            Button("Cancel", role: .cancel) {
                routeToRename = nil
            }
            Button("Save") {
                guard let route = routeToRename else { return }
                Task { await renameRoute(route, to: routeNameDraft) }
            }
        } message: {
            Text("Give this route a new name.")
        }
        .confirmationDialog(
            "Delete this route?",
            isPresented: Binding(
                get: { routeToDelete != nil },
                set: { if !$0 { routeToDelete = nil } }
            ),
            titleVisibility: .visible
        ) {
            Button("Delete Route", role: .destructive) {
                guard let route = routeToDelete else { return }
                Task { await deleteRoute(route) }
            }
            Button("Cancel", role: .cancel) {
                routeToDelete = nil
            }
        } message: {
            Text("This removes the route from your saved routes.")
        }
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
                    errorMessage = "Route sharing is coming soon."
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
                    errorMessage = "Route sharing is coming soon."
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
            routes = try await APIClient.shared.fetchRoutes()
        } catch {
            errorMessage = "Couldn’t load routes."
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
            errorMessage = "Couldn’t rename this route."
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
