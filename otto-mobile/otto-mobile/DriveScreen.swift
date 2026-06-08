import SwiftUI

struct DriveScreen: View {
    @EnvironmentObject private var appState: AppState
    @State private var isShowingAddCar = false

    var body: some View {
        NavigationStack {
            List {
                Section("Live Session") {
                    if appState.garageCars.isEmpty {
                        VStack(alignment: .leading, spacing: 8) {
                            Text("No garage car selected (optional).")
                                .font(.subheadline.weight(.semibold))
                            Button {
                                isShowingAddCar = true
                            } label: {
                                Label("Add Car", systemImage: "plus.circle")
                            }
                            .buttonStyle(.bordered)
                        }
                    } else {
                        Picker(
                            "Sharing Car",
                            selection: Binding(
                                get: { appState.selectedSharingCarID },
                                set: { appState.selectSharingCar($0) }
                            )
                        ) {
                            ForEach(appState.garageCars) { car in
                                Text(car.displayName).tag(car.id)
                            }
                        }
                    }

                    Toggle(
                        "Sharing Enabled",
                        isOn: Binding(
                            get: { appState.isSharingEnabled },
                            set: { appState.setSharingEnabled($0) }
                        )
                    )

                    Text(appState.isSharingEnabled ? "Sharing with \(appState.sharingAudienceLabel)" : "Start a timed sharing session from the map.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }

                Section("Share With") {
                    ForEach(appState.circles) { circle in
                        HStack {
                            Label(circle.name, systemImage: circle.icon)
                            Spacer()
                            Toggle(
                                "",
                                isOn: Binding(
                                    get: { appState.isCircleShared(circle.id) },
                                    set: { _ in appState.toggleSharing(for: circle.id) }
                                )
                            )
                            .labelsHidden()
                        }
                    }
                }
            }
            .navigationTitle("Sharing")
            .scrollContentBackground(.hidden)
            .background(Color.black)
        }
        .sheet(isPresented: $isShowingAddCar) {
            AddGarageCarSheet(appState: appState)
        }
        .task {
            appState.refreshGarageAsync()
        }
    }
}
