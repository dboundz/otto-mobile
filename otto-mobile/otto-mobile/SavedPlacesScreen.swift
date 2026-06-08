import SwiftUI

struct SavedPlacesScreen: View {
    @EnvironmentObject private var appState: AppState
    @State private var placePendingDelete: SavedPlaceDTO?
    @State private var isDeleting = false

    var body: some View {
        ZStack {
            LinearGradient(
                colors: [Color.black, Color(red: 0.05, green: 0.06, blue: 0.10), Color.black],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            .ignoresSafeArea()

            if appState.savedPlaces.isEmpty {
                ContentUnavailableView(
                    "No saved places",
                    systemImage: "mappin.slash",
                    description: Text("On the map, tap a restaurant, gas station, or any spot to save it here.")
                )
                .foregroundStyle(.white.opacity(0.9))
            } else {
                ScrollView {
                    LazyVStack(spacing: 10) {
                        ForEach(appState.savedPlaces) { place in
                            Button {
                                appState.requestMapTabCenteredOn(
                                    latitude: place.latitude,
                                    longitude: place.longitude,
                                    savedPlaceID: place.id
                                )
                            } label: {
                                VStack(alignment: .leading, spacing: 4) {
                                    Text(place.name)
                                        .font(.headline.weight(.semibold))
                                        .foregroundStyle(.white)
                                    if let kind = place.placeKind, !kind.isEmpty {
                                        Text(kind.replacingOccurrences(of: "_", with: " ").capitalized)
                                            .font(.caption2.weight(.medium))
                                            .foregroundStyle(.purple.opacity(0.85))
                                    }
                                    if let summary = place.addressSummary, !summary.isEmpty {
                                        Text(summary)
                                            .font(.caption)
                                            .foregroundStyle(.secondary)
                                    }
                                    Text(coordinateLine(place))
                                        .font(.caption2.monospaced())
                                        .foregroundStyle(.tertiary)
                                }
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .contentShape(Rectangle())
                                .padding(14)
                                .background(Color.white.opacity(0.06))
                                .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                            }
                            .buttonStyle(.plain)
                            .contextMenu {
                                Button(role: .destructive) {
                                    placePendingDelete = place
                                } label: {
                                    Label("Delete", systemImage: "trash")
                                }
                            }
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.top, 14)
                    .padding(.bottom, 24)
                }
            }
        }
        .navigationTitle("My places")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar(.visible, for: .navigationBar)
        .task {
            await appState.refreshSavedPlaces()
        }
        .confirmationDialog(
            "Delete this place?",
            isPresented: Binding(
                get: { placePendingDelete != nil },
                set: { if !$0 { placePendingDelete = nil } }
            ),
            presenting: placePendingDelete
        ) { place in
            Button("Delete", role: .destructive) {
                Task { await deletePlace(place) }
            }
            Button("Cancel", role: .cancel) {
                placePendingDelete = nil
            }
        } message: { place in
            Text(place.name)
        }
        .overlay {
            if isDeleting {
                Color.black.opacity(0.35).ignoresSafeArea()
                ProgressView("Deleting…")
                    .tint(.white)
                    .padding(20)
                    .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 14))
            }
        }
    }

    private func coordinateLine(_ place: SavedPlaceDTO) -> String {
        String(format: "%.5f°, %.5f°", place.latitude, place.longitude)
    }

    private func deletePlace(_ place: SavedPlaceDTO) async {
        isDeleting = true
        placePendingDelete = nil
        do {
            try await appState.deleteSavedPlace(placeId: place.id)
        } catch {
            appState.errorMessage = "Couldn’t delete that place."
        }
        isDeleting = false
    }
}
