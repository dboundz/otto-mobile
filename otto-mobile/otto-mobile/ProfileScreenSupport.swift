import CoreLocation
import SwiftUI
import UIKit

// MARK: - Profile screen helpers (split out of `body` for Swift type-checker / runtime limits)

struct ProfileScreenScrollSections: View {
    let isCurrentUserProfile: Bool
    let profileCard: () -> AnyView
    let garagePreviewCard: () -> AnyView
    let drivingStatsCard: () -> AnyView
    let myDrivesSection: () -> AnyView
    let myRoutesSection: () -> AnyView
    let myPlacesSection: () -> AnyView
    let publicGoingEventsSection: () -> AnyView
    let viewedProfileSafetySection: () -> AnyView
    let menuList: () -> AnyView

    var body: some View {
        VStack(spacing: 14) {
            profileCard()
            garagePreviewCard()
            drivingStatsCard()
            if isCurrentUserProfile {
                myDrivesSection()
                myRoutesSection()
                myPlacesSection()
            }
            publicGoingEventsSection()
            if !isCurrentUserProfile {
                viewedProfileSafetySection()
            }
            if isCurrentUserProfile {
                menuList()
            }
        }
        .frame(maxWidth: .infinity, alignment: .center)
        .padding(.horizontal, 16)
        .padding(.top, 18)
        .padding(.bottom, 110)
    }
}

struct ProfileScreenSheetsModifier: ViewModifier {
    @EnvironmentObject private var appState: AppState
    @EnvironmentObject private var locationService: LocationService

    @Binding var isShowingSettings: Bool
    @Binding var selectedProfileDrive: DriveDTO?
    @Binding var selectedProfileRoute: SavedRouteDTO?
    @Binding var profileRoutes: [SavedRouteDTO]
    @Binding var isShowingAllProfileDrives: Bool
    @Binding var isShowingAllProfileRoutes: Bool
    @Binding var isShowingAllProfilePlaces: Bool
    @Binding var isShowingNewProfileRouteBuilder: Bool
    @Binding var profileDriveShareContext: DriveChatShareContext?
    @Binding var profileRouteShareRoute: SavedRouteDTO?
    @Binding var profilePhotoToCrop: UIImage?

    let sortedProfileDrivesForDisplay: [DriveDTO]
    let sortedProfileRoutesForDisplay: [SavedRouteDTO]
    let sortedProfilePlacesForDisplay: [SavedPlaceDTO]
    let isCurrentUserProfile: Bool
    let profileRouteBuilderInitialCenter: CLLocationCoordinate2D
    let driveSummarySheet: (DriveDTO) -> AnyView
    let presentNewProfileRouteBuilder: () -> Void
    let startProfileRouteDrive: (SavedRouteDTO) -> Void
    let presentProfileRouteShare: (SavedRouteDTO) -> Void
    let saveProfileDriveRename: @MainActor (DriveDTO, String) async -> DriveDTO?
    let confirmProfileDriveDeletion: @MainActor (DriveDTO) async -> Void
    let applyProfileDriveListUpdate: (DriveDTO) -> Void
    let handleProfileDriveDeleted: (String) -> Void
    let saveProfileRouteRename: @MainActor (SavedRouteDTO, String) async -> SavedRouteDTO?
    let confirmProfileRouteDeletion: @MainActor (SavedRouteDTO) async -> Void
    let applyProfileRouteListUpdate: (SavedRouteDTO) -> Void
    let handleProfileRouteDeleted: (String) -> Void
    let handleProfileRouteSaved: (SavedRouteDTO) -> Void
    let saveProfilePlaceRename: @MainActor (SavedPlaceDTO, String) async -> SavedPlaceDTO?
    let confirmProfilePlaceDeletion: @MainActor (SavedPlaceDTO) async -> Void
    let openProfilePlaceOnMap: (SavedPlaceDTO) -> Void
    let presentProfileDriveShare: (DriveDTO) -> Void

    func body(content: Content) -> some View {
        content
            .sheet(isPresented: $isShowingSettings) {
                ProfileSettingsSheet()
            }
            .sheet(item: $selectedProfileDrive) { drive in
                driveSummarySheet(drive)
            }
            .fullScreenCover(item: $selectedProfileRoute) { route in
                RouteBuilderView(route: route) { updatedRoute in
                    if let index = profileRoutes.firstIndex(where: { $0.id == updatedRoute.id }) {
                        profileRoutes[index] = updatedRoute
                    }
                    selectedProfileRoute = nil
                }
                .environmentObject(appState)
                .environmentObject(locationService)
            }
            .sheet(isPresented: $isShowingAllProfileDrives) {
                ProfileDrivesListSheet(
                    drives: sortedProfileDrivesForDisplay,
                    isOwner: isCurrentUserProfile,
                    onShare: presentProfileDriveShare,
                    onRenameSave: { drive, title in
                        await saveProfileDriveRename(drive, title)
                    },
                    onDeleteConfirmed: confirmProfileDriveDeletion,
                    onDriveUpdated: applyProfileDriveListUpdate,
                    onDriveDeleted: { handleProfileDriveDeleted($0) }
                )
                .environmentObject(appState)
                .presentationDetents([.large])
                .presentationBackground(Color.black)
            }
            .sheet(isPresented: $isShowingAllProfileRoutes) {
                ProfileRoutesListSheet(
                    routes: sortedProfileRoutesForDisplay,
                    onCreateRoute: presentNewProfileRouteBuilder,
                    onStartDrive: startProfileRouteDrive,
                    onShare: presentProfileRouteShare,
                    onRenameSave: { route, title in
                        await saveProfileRouteRename(route, title)
                    },
                    onDeleteConfirmed: confirmProfileRouteDeletion,
                    onRouteUpdated: applyProfileRouteListUpdate,
                    onRouteDeleted: { handleProfileRouteDeleted($0) }
                )
                .environmentObject(appState)
                .environmentObject(locationService)
                .presentationDetents([.large])
                .presentationBackground(Color.black)
            }
            .sheet(isPresented: $isShowingAllProfilePlaces) {
                ProfilePlacesListSheet(
                    places: sortedProfilePlacesForDisplay,
                    onRenameSave: { place, name in
                        await saveProfilePlaceRename(place, name)
                    },
                    onDeleteConfirmed: confirmProfilePlaceDeletion,
                    onOpenPlace: openProfilePlaceOnMap
                )
                .environmentObject(appState)
                .presentationDetents([.large])
                .presentationBackground(Color.black)
            }
            .fullScreenCover(isPresented: $isShowingNewProfileRouteBuilder) {
                if appState.hasRoutesAccess {
                    RouteBuilderView(initialCenter: profileRouteBuilderInitialCenter) { savedRoute in
                        handleProfileRouteSaved(savedRoute)
                    }
                    .environmentObject(appState)
                    .environmentObject(locationService)
                } else {
                    Color.clear
                        .onAppear { isShowingNewProfileRouteBuilder = false }
                }
            }
            .sheet(item: $profileDriveShareContext) { context in
                DriveShareSquadActionsSheet(
                    context: context,
                    externalShareText: ProfileDriveShareFormatting.externalShareText(for: context),
                    externalShareSubject: context.previewTitle,
                    canShare: isCurrentUserProfile
                )
                .environmentObject(appState)
            }
            .sheet(item: $profileRouteShareRoute) { route in
                RouteShareSquadActionsSheet(
                    route: route,
                    externalShareText: ProfileRouteShareFormatting.externalShareText(for: route),
                    externalShareSubject: route.name,
                    canShare: isCurrentUserProfile
                )
                .environmentObject(appState)
            }
            .fullScreenCover(isPresented: Binding(
                get: { profilePhotoToCrop != nil },
                set: { if !$0 { profilePhotoToCrop = nil } }
            )) {
                if let image = profilePhotoToCrop {
                    OttoImageCropperSheet(
                        image: image,
                        cropAspect: 1,
                        onComplete: { jpeg in
                            profilePhotoToCrop = nil
                            Task { await appState.uploadProfilePhoto(jpeg) }
                        },
                        onCancel: { profilePhotoToCrop = nil }
                    )
                }
            }
    }
}

struct ProfileScreenErrorAlertModifier: ViewModifier {
    @EnvironmentObject private var appState: AppState
    let profileErrorAlertTitle: String

    func body(content: Content) -> some View {
        content.alert(
            profileErrorAlertTitle,
            isPresented: Binding(
                get: { appState.errorMessage != nil },
                set: { isPresented in
                    if !isPresented { appState.errorMessage = nil }
                }
            )
        ) {
            Button("OK", role: .cancel) {
                appState.errorMessage = nil
            }
        } message: {
            Text(appState.errorMessage ?? "Unknown error.")
        }
    }
}

struct ProfileScreenDeleteConfirmationsModifier: ViewModifier {
    @Binding var profileDrivePendingDelete: DriveDTO?
    @Binding var profileRoutePendingDelete: SavedRouteDTO?
    @Binding var profilePlacePendingDelete: SavedPlaceDTO?
    let confirmProfileDriveDeletion: @MainActor (DriveDTO) async -> Void
    let confirmProfileRouteDeletion: @MainActor (SavedRouteDTO) async -> Void
    let confirmProfilePlaceDeletion: @MainActor (SavedPlaceDTO) async -> Void

    func body(content: Content) -> some View {
        content
            .confirmationDialog(
                "Delete this drive?",
                isPresented: Binding(
                    get: { profileDrivePendingDelete != nil },
                    set: { if !$0 { profileDrivePendingDelete = nil } }
                ),
                titleVisibility: .visible
            ) {
                Button("Delete Drive", role: .destructive) {
                    guard let drive = profileDrivePendingDelete else { return }
                    Task { await confirmProfileDriveDeletion(drive) }
                }
                Button("Cancel", role: .cancel) {
                    profileDrivePendingDelete = nil
                }
            } message: {
                Text("This permanently removes the drive from your profile.")
            }
            .confirmationDialog(
                "Delete this route?",
                isPresented: Binding(
                    get: { profileRoutePendingDelete != nil },
                    set: { if !$0 { profileRoutePendingDelete = nil } }
                ),
                titleVisibility: .visible
            ) {
                Button("Delete Route", role: .destructive) {
                    guard let route = profileRoutePendingDelete else { return }
                    Task { await confirmProfileRouteDeletion(route) }
                }
                Button("Cancel", role: .cancel) {
                    profileRoutePendingDelete = nil
                }
            } message: {
                Text("This removes the route from your saved routes.")
            }
            .confirmationDialog(
                String(localized: "marker_detail_delete_place_title"),
                isPresented: Binding(
                    get: { profilePlacePendingDelete != nil },
                    set: { if !$0 { profilePlacePendingDelete = nil } }
                ),
                titleVisibility: .visible
            ) {
                Button(String(localized: "marker_detail_action_remove"), role: .destructive) {
                    guard let place = profilePlacePendingDelete else { return }
                    Task { await confirmProfilePlaceDeletion(place) }
                }
                Button(String(localized: "marker_detail_cancel"), role: .cancel) {
                    profilePlacePendingDelete = nil
                }
            } message: {
                if let place = profilePlacePendingDelete {
                    Text(place.name)
                }
            }
    }
}

struct ProfileScreenRenameAlertsModifier: ViewModifier {
    @Binding var profilePlaceRenameTarget: SavedPlaceDTO?
    @Binding var profilePlaceRenameDraft: String
    let isRenamingProfilePlace: Bool
    @Binding var profileDriveRenameTarget: DriveDTO?
    @Binding var profileDriveRenameDraft: String
    let isRenamingProfileDrive: Bool
    @Binding var profileRouteRenameTarget: SavedRouteDTO?
    @Binding var profileRouteRenameDraft: String
    let isRenamingProfileRoute: Bool
    let saveProfilePlaceRename: @MainActor (SavedPlaceDTO, String) async -> SavedPlaceDTO?
    let saveProfileDriveRename: @MainActor (DriveDTO, String) async -> DriveDTO?
    let saveProfileRouteRename: @MainActor (SavedRouteDTO, String) async -> SavedRouteDTO?

    func body(content: Content) -> some View {
        content
            .alert("Rename place", isPresented: Binding(
                get: { profilePlaceRenameTarget != nil },
                set: { if !$0 { profilePlaceRenameTarget = nil } }
            )) {
                TextField("Place name", text: $profilePlaceRenameDraft)
                Button("Cancel", role: .cancel) {
                    profilePlaceRenameTarget = nil
                }
                Button(isRenamingProfilePlace ? "Saving..." : "Save") {
                    guard let place = profilePlaceRenameTarget else { return }
                    let draft = profilePlaceRenameDraft.trimmingCharacters(in: .whitespacesAndNewlines)
                    guard !draft.isEmpty else { return }
                    Task { @MainActor in
                        _ = await saveProfilePlaceRename(place, draft)
                    }
                }
                .disabled(
                    profilePlaceRenameDraft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                        || isRenamingProfilePlace
                )
            }
            .alert("Rename drive", isPresented: Binding(
                get: { profileDriveRenameTarget != nil },
                set: { if !$0 { profileDriveRenameTarget = nil } }
            )) {
                TextField("Drive name", text: $profileDriveRenameDraft)
                Button("Cancel", role: .cancel) {
                    profileDriveRenameTarget = nil
                }
                Button(isRenamingProfileDrive ? "Saving..." : "Save") {
                    guard let drive = profileDriveRenameTarget else { return }
                    let draft = profileDriveRenameDraft.trimmingCharacters(in: .whitespacesAndNewlines)
                    guard !draft.isEmpty else { return }
                    Task { @MainActor in
                        _ = await saveProfileDriveRename(drive, draft)
                    }
                }
                .disabled(
                    profileDriveRenameDraft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                        || isRenamingProfileDrive
                )
            } message: {
                Text("This updates how the drive appears on your profile and in squad chat shares.")
            }
            .alert("Rename route", isPresented: Binding(
                get: { profileRouteRenameTarget != nil },
                set: { if !$0 { profileRouteRenameTarget = nil } }
            )) {
                TextField("Route name", text: $profileRouteRenameDraft)
                Button("Cancel", role: .cancel) {
                    profileRouteRenameTarget = nil
                }
                Button(isRenamingProfileRoute ? "Saving..." : "Save") {
                    guard let route = profileRouteRenameTarget else { return }
                    let draft = profileRouteRenameDraft.trimmingCharacters(in: .whitespacesAndNewlines)
                    guard !draft.isEmpty else { return }
                    Task { @MainActor in
                        _ = await saveProfileRouteRename(route, draft)
                    }
                }
                .disabled(
                    profileRouteRenameDraft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                        || isRenamingProfileRoute
                )
            } message: {
                Text("Give this route a new name.")
            }
    }
}

struct ProfileScreenAccountAlertsModifier: ViewModifier {
    @EnvironmentObject private var appState: AppState
    let profileName: String
    let resolvedProfileUserID: String
    @Binding var isShowingNameEditor: Bool
    @Binding var profileNameDraft: String
    let isSavingProfileName: Bool
    @Binding var isShowingSignOutConfirmation: Bool
    @Binding var confirmBlockOtherUser: Bool
    let saveProfileName: () async -> Void
    let onUserBlocked: (() -> Void)?
    let dismiss: () -> Void

    func body(content: Content) -> some View {
        content
            .alert("Edit Name", isPresented: $isShowingNameEditor) {
                TextField("Name", text: $profileNameDraft)
                    .textInputAutocapitalization(.words)
                    .disableAutocorrection(false)
                Button("Cancel", role: .cancel) {}
                Button(isSavingProfileName ? "Saving..." : "Save") {
                    Task { await saveProfileName() }
                }
                .disabled(isSavingProfileName)
            } message: {
                Text("Update the name shown on your profile.")
            }
            .alert("Sign out?", isPresented: $isShowingSignOutConfirmation) {
                Button("Cancel", role: .cancel) {}
                Button("Sign Out", role: .destructive) {
                    appState.logout()
                    dismiss()
                }
            } message: {
                Text("You'll need to sign in again to use Driftd.")
            }
            .alert("Block \(profileName)?", isPresented: $confirmBlockOtherUser) {
                Button("Cancel", role: .cancel) {}
                Button("Block", role: .destructive) {
                    Task {
                        if await appState.blockUser(resolvedProfileUserID) {
                            await MainActor.run {
                                dismiss()
                                onUserBlocked?()
                            }
                        }
                    }
                }
            } message: {
                Text(
                    "You won't see them in contacts or direct messages. You may still share a squad and see each other there."
                )
            }
    }
}

struct ProfileScreenLifecycleModifier: ViewModifier {
    @EnvironmentObject private var appState: AppState
    let resolvedProfileUserID: String
    let isCurrentUserProfile: Bool
    @Binding var viewedProfileGarageCars: [GarageCar]
    let loadDrivingStatsForProfile: () async -> Void
    let loadProfileDrives: () async -> Void
    let loadProfileRoutes: () async -> Void
    let loadPublicGoingEventsForProfile: () async -> Void
    let loadViewedProfileGaragePreview: () async -> Void

    func body(content: Content) -> some View {
        content
            .task(id: resolvedProfileUserID) {
                if isCurrentUserProfile {
                    appState.refreshGarageAsync()
                    await appState.refreshSavedPlaces()
                    viewedProfileGarageCars = []
                    await loadDrivingStatsForProfile()
                    await loadProfileDrives()
                    await loadProfileRoutes()
                    await loadPublicGoingEventsForProfile()
                } else {
                    await loadViewedProfileGaragePreview()
                    await loadDrivingStatsForProfile()
                    await loadPublicGoingEventsForProfile()
                }
            }
            .onChange(of: appState.showPublicGoingEventsOnProfile) { _, _ in
                guard isCurrentUserProfile else { return }
                Task { await loadPublicGoingEventsForProfile() }
            }
            .onChange(of: appState.profileProgressionRefreshTick) { _, _ in
                guard isCurrentUserProfile else { return }
                Task {
                    await loadDrivingStatsForProfile()
                    await loadProfileDrives()
                }
            }
    }
}
