import CoreLocation
import SwiftUI
import Photos
import PhotosUI
import UIKit

struct ProfileScreen: View {
    @EnvironmentObject private var appState: AppState
    @EnvironmentObject private var locationService: LocationService
    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL
    let profileUserID: String?
    /// Called after a successful block (e.g. dismiss the presenting mini-profile sheet).
    private let onUserBlocked: (() -> Void)?
    /// Called when leaving another member's profile (e.g. clear Profile tab focus user).
    private let onDismissPeerProfile: (() -> Void)?
    /// When full profile is presented as a sheet (e.g. map mini-profile), switch to Garage tab after dismissing the sheet.
    private let onOpenOwnGarage: (() -> Void)?
    @State private var isShowingSettings = false
    @State private var isShowingSignOutConfirmation = false
    @State private var isShowingProfilePhotoPicker = false
    @State private var photoPickerItem: PhotosPickerItem?
    @State private var profilePhotoToCrop: UIImage?
    @State private var viewedProfileGarageCars: [GarageCar] = []
    @State private var drivingStats: DrivingStatsDTO?
    @State private var isRefreshingDrivingStats = false
    @State private var selectedProfileDrive: DriveDTO?
    @State private var profileRoutes: [SavedRouteDTO] = []
    @State private var selectedProfileRoute: SavedRouteDTO?
    @State private var isShowingAllProfileDrives = false
    @State private var selectedPendingDriveArchive: PendingDriveArchive?
    @State private var isShowingAllProfileRoutes = false
    @State private var isShowingAllProfilePlaces = false
    @State private var isShowingNewProfileRouteBuilder = false
    @State private var profileDrivePendingDelete: DriveDTO?
    @State private var profileRoutePendingDelete: SavedRouteDTO?
    @State private var profileDriveShareContext: DriveChatShareContext?
    @State private var profileDriveRenameTarget: DriveDTO?
    @State private var profileDriveRenameDraft = ""
    @State private var isRenamingProfileDrive = false
    @State private var profileRouteRenameTarget: SavedRouteDTO?
    @State private var profileRouteRenameDraft = ""
    @State private var isRenamingProfileRoute = false
    @State private var profilePlacePendingDelete: SavedPlaceDTO?
    @State private var profilePlaceRenameTarget: SavedPlaceDTO?
    @State private var profilePlaceRenameDraft = ""
    @State private var isRenamingProfilePlace = false
    @State private var publicGoingEvents: [PublicGoingEventDTO] = []
    @State private var isShowingNameEditor = false
    @State private var profileNameDraft = ""
    @State private var isSavingProfileName = false
    @State private var profileHeroProgressRevealed = false
    @State private var confirmBlockOtherUser = false
    /// Recreates the profile `NavigationStack` when the Profile tab is tapped again while selected (see `RootTabView`).
    @State private var profileNavigationResetId = UUID()

    init(
        profileUserID: String? = nil,
        onUserBlocked: (() -> Void)? = nil,
        onDismissPeerProfile: (() -> Void)? = nil,
        onOpenOwnGarage: (() -> Void)? = nil
    ) {
        self.profileUserID = profileUserID
        self.onUserBlocked = onUserBlocked
        self.onDismissPeerProfile = onDismissPeerProfile
        self.onOpenOwnGarage = onOpenOwnGarage
    }

    private var resolvedProfileUserID: String {
        profileUserID ?? appState.currentUserID
    }

    private var isCurrentUserProfile: Bool {
        profileUserID == nil || profileUserID == appState.currentUserID
    }

    private var profileDrivesForDisplay: [DriveDTO] {
        appState.recentDrives.filter { drive in
            let completed = (drive.status == "completed" || drive.endTime != nil) && drive.distanceMeters > 0
            return completed && ProfileDriveEligibility.isEligibleForProfileList(drive)
        }
    }

    private var sortedProfileDrivesForDisplay: [DriveDTO] {
        profileDrivesForDisplay.sorted {
            ($0.endTime ?? $0.startTime) > ($1.endTime ?? $1.startTime)
        }
    }

    private var sortedPendingProfileDrives: [PendingDriveArchive] {
        appState.pendingDriveArchives.sorted { $0.endedAt > $1.endedAt }
    }

    private var profileDriveListItemCount: Int {
        sortedProfileDrivesForDisplay.count + sortedPendingProfileDrives.count
    }

    private var sortedProfileRoutesForDisplay: [SavedRouteDTO] {
        profileRoutes.sorted { profileRouteSortDate($0) > profileRouteSortDate($1) }
    }

    var body: some View {
        NavigationStack {
            profileScreenScrollHost
                .navigationTitle(isCurrentUserProfile ? "Profile" : profileName)
                .navigationBarTitleDisplayMode(.inline)
                .toolbar(.hidden, for: .navigationBar)
                .background(Color.black)
                .modifier(profileScreenSheetsModifier())
                .modifier(profileScreenErrorAlertModifier())
                .modifier(profileScreenDeleteConfirmationsModifier())
                .modifier(profileScreenRenameAlertsModifier())
                .modifier(profileScreenAccountAlertsModifier())
                .modifier(profileScreenLifecycleModifier())
        }
        .id(profileNavigationResetId)
        .photosPicker(
            isPresented: $isShowingProfilePhotoPicker,
            selection: $photoPickerItem,
            matching: .images,
            photoLibrary: PHPhotoLibrary.shared()
        )
        .onChange(of: photoPickerItem) { _, newItem in
            Task {
                guard let newItem else { return }
                if let data = try? await newItem.loadTransferable(type: Data.self),
                   let ui = UIImage(data: data) {
                    await MainActor.run {
                        profilePhotoToCrop = ui
                        photoPickerItem = nil
                    }
                }
            }
        }
        .sheet(item: $selectedPendingDriveArchive) { archive in
            PendingDriveSummaryScreen(archive: archive)
                .environmentObject(appState)
        }
        .onReceive(NotificationCenter.default.publisher(for: .ottoProfileTabReselected)) { _ in
            profileNavigationResetId = UUID()
            Task { await loadProfileDrives() }
        }
    }

    /// Scroll stack only — keeps `body` type-checkable (long modifier chains blow the Swift runtime budget).
    private var profileScreenScrollHost: some View {
        ZStack(alignment: .topLeading) {
            LinearGradient(
                colors: [Color.black, Color(red: 0.05, green: 0.06, blue: 0.10), Color.black],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            .ignoresSafeArea()

            ScrollView(showsIndicators: false) {
                ProfileScreenScrollSections(
                    isCurrentUserProfile: isCurrentUserProfile,
                    profileCard: { AnyView(profileCard) },
                    garagePreviewCard: { AnyView(garagePreviewCard) },
                    drivingStatsCard: { AnyView(drivingStatsCard) },
                    myDrivesSection: { AnyView(myDrivesSection) },
                    myRoutesSection: { AnyView(myRoutesSection) },
                    myPlacesSection: { AnyView(myPlacesSection) },
                    publicGoingEventsSection: { AnyView(publicGoingEventsSection) },
                    viewedProfileSafetySection: { AnyView(viewedProfileSafetySection) },
                    menuList: { AnyView(menuList) }
                )
            }
            .scrollContentBackground(.hidden)
            .scrollDismissesKeyboard(.interactively)
        }
    }

    private func profileScreenSheetsModifier() -> ProfileScreenSheetsModifier {
        ProfileScreenSheetsModifier(
            isShowingSettings: $isShowingSettings,
            selectedProfileDrive: $selectedProfileDrive,
            selectedProfileRoute: $selectedProfileRoute,
            profileRoutes: $profileRoutes,
            isShowingAllProfileDrives: $isShowingAllProfileDrives,
            isShowingAllProfileRoutes: $isShowingAllProfileRoutes,
            isShowingAllProfilePlaces: $isShowingAllProfilePlaces,
            isShowingNewProfileRouteBuilder: $isShowingNewProfileRouteBuilder,
            profileDriveShareContext: $profileDriveShareContext,
            profilePhotoToCrop: $profilePhotoToCrop,
            sortedProfileDrivesForDisplay: sortedProfileDrivesForDisplay,
            sortedProfileRoutesForDisplay: sortedProfileRoutesForDisplay,
            sortedProfilePlacesForDisplay: sortedProfilePlacesForDisplay,
            isCurrentUserProfile: isCurrentUserProfile,
            profileRouteBuilderInitialCenter: profileRouteBuilderInitialCenter,
            driveSummarySheet: { AnyView(profileDriveSummarySheet($0)) },
            presentNewProfileRouteBuilder: presentNewProfileRouteBuilder,
            startProfileRouteDrive: startProfileRouteDrive,
            presentProfileRouteShare: presentProfileRouteShare,
            saveProfileDriveRename: saveProfileDriveRename,
            confirmProfileDriveDeletion: confirmProfileDriveDeletion,
            applyProfileDriveListUpdate: applyProfileDriveListUpdate,
            handleProfileDriveDeleted: handleProfileDriveDeleted,
            saveProfileRouteRename: saveProfileRouteRename,
            confirmProfileRouteDeletion: confirmProfileRouteDeletion,
            applyProfileRouteListUpdate: applyProfileRouteListUpdate,
            handleProfileRouteDeleted: handleProfileRouteDeleted,
            handleProfileRouteSaved: handleProfileRouteSaved,
            saveProfilePlaceRename: saveProfilePlaceRename,
            confirmProfilePlaceDeletion: confirmProfilePlaceDeletion,
            openProfilePlaceOnMap: openProfilePlaceOnMap,
            presentProfileDriveShare: presentProfileDriveShare
        )
    }

    private func profileScreenErrorAlertModifier() -> ProfileScreenErrorAlertModifier {
        ProfileScreenErrorAlertModifier(profileErrorAlertTitle: profileErrorAlertTitle)
    }

    private func profileScreenDeleteConfirmationsModifier() -> ProfileScreenDeleteConfirmationsModifier {
        ProfileScreenDeleteConfirmationsModifier(
            profileDrivePendingDelete: $profileDrivePendingDelete,
            profileRoutePendingDelete: $profileRoutePendingDelete,
            profilePlacePendingDelete: $profilePlacePendingDelete,
            confirmProfileDriveDeletion: confirmProfileDriveDeletion,
            confirmProfileRouteDeletion: confirmProfileRouteDeletion,
            confirmProfilePlaceDeletion: confirmProfilePlaceDeletion
        )
    }

    private func profileScreenRenameAlertsModifier() -> ProfileScreenRenameAlertsModifier {
        ProfileScreenRenameAlertsModifier(
            profilePlaceRenameTarget: $profilePlaceRenameTarget,
            profilePlaceRenameDraft: $profilePlaceRenameDraft,
            isRenamingProfilePlace: isRenamingProfilePlace,
            profileDriveRenameTarget: $profileDriveRenameTarget,
            profileDriveRenameDraft: $profileDriveRenameDraft,
            isRenamingProfileDrive: isRenamingProfileDrive,
            profileRouteRenameTarget: $profileRouteRenameTarget,
            profileRouteRenameDraft: $profileRouteRenameDraft,
            isRenamingProfileRoute: isRenamingProfileRoute,
            saveProfilePlaceRename: saveProfilePlaceRename,
            saveProfileDriveRename: saveProfileDriveRename,
            saveProfileRouteRename: saveProfileRouteRename
        )
    }

    private func profileScreenAccountAlertsModifier() -> ProfileScreenAccountAlertsModifier {
        ProfileScreenAccountAlertsModifier(
            profileName: profileName,
            resolvedProfileUserID: resolvedProfileUserID,
            isShowingNameEditor: $isShowingNameEditor,
            profileNameDraft: $profileNameDraft,
            isSavingProfileName: isSavingProfileName,
            isShowingSignOutConfirmation: $isShowingSignOutConfirmation,
            confirmBlockOtherUser: $confirmBlockOtherUser,
            saveProfileName: saveProfileName,
            onUserBlocked: onUserBlocked,
            dismiss: { dismiss() }
        )
    }

    private func profileScreenLifecycleModifier() -> ProfileScreenLifecycleModifier {
        ProfileScreenLifecycleModifier(
            resolvedProfileUserID: resolvedProfileUserID,
            isCurrentUserProfile: isCurrentUserProfile,
            viewedProfileGarageCars: $viewedProfileGarageCars,
            loadDrivingStatsForProfile: loadDrivingStatsForProfile,
            loadProfileDrives: loadProfileDrives,
            loadProfileRoutes: loadProfileRoutes,
            loadPublicGoingEventsForProfile: loadPublicGoingEventsForProfile,
            loadViewedProfileGaragePreview: loadViewedProfileGaragePreview
        )
    }

    private var profileErrorAlertTitle: String {
        let message = appState.errorMessage ?? ""
        if message.localizedCaseInsensitiveContains("photo") {
            return "Upload Failed"
        }
        if message.localizedCaseInsensitiveContains("account") {
            return "Account Error"
        }
        if message.localizedCaseInsensitiveContains("name") || message.localizedCaseInsensitiveContains("profile") {
            return "Profile Error"
        }
        return "Something Went Wrong"
    }

    private var profileName: String {
        appState.allUsers.first(where: { $0.id == resolvedProfileUserID })?.displayName ?? "Jake"
    }

    private var currentUserAvatarUrl: String? {
        appState.allUsers.first(where: { $0.id == resolvedProfileUserID })?.avatarUrl
    }

    private var isDriveStatsHiddenFromViewer: Bool {
        drivingStats?.driveStatsVisible == false
    }

    /// Progression info only when the API allows this viewer to see drive stats.
    private var visibleProgression: ProfileProgressionDTO? {
        guard !isDriveStatsHiddenFromViewer else { return nil }
        return drivingStats?.progression ?? .starting
    }

    private var profileTierStyle: ProfileTierStyle {
        if isDriveStatsHiddenFromViewer {
            return .privateStatsAccent
        }
        let tierId = (drivingStats?.progression ?? .starting).tierId
        return ProfileTierStyle.style(for: tierId)
    }

    private func profileTierOrdinalText(for progression: ProfileProgressionDTO) -> String {
        guard !progression.isMaxLevel else { return progression.tierName }
        let tierStartLevel: Int
        switch progression.tierId {
        case "rookie": tierStartLevel = 1
        case "qualifier": tierStartLevel = 5
        case "runner": tierStartLevel = 9
        case "pacer": tierStartLevel = 13
        case "apex": tierStartLevel = 17
        default: tierStartLevel = progression.level
        }
        let ordinal = max(1, progression.level - tierStartLevel + 1)
        return "\(progression.tierName) \(romanNumeral(ordinal))"
    }

    private var featuredCarName: String {
        featuredGarageCar?.displayName ?? (isCurrentUserProfile ? "No Car Selected" : "Tap to view garage")
    }

    private var featuredCarDetail: String {
        featuredGarageCar?.detailLine ?? (isCurrentUserProfile ? "Add your first car" : "Read-only view")
    }

    private var featuredGarageCar: GarageCar? {
        let list = isCurrentUserProfile ? appState.garageCars : viewedProfileGarageCars
        return list.first(where: \.isPrimary) ?? list.first
    }

    private var profileShareURL: URL {
        WebsiteLinks.profile(userId: resolvedProfileUserID)
    }

    @MainActor
    private var profileCard: some View {
        let tierStyle = profileTierStyle
        return ZStack(alignment: .top) {
            VStack(spacing: 12) {
                heroAvatarBlock(tierStyle: tierStyle)

                Text(profileName)
                    .font(.largeTitle.weight(.bold))
                    .foregroundStyle(.white)
                    .multilineTextAlignment(.center)
                    .lineLimit(2)
                    .minimumScaleFactor(0.82)
                    .padding(.horizontal, 8)

                if let prog = visibleProgression {
                    NavigationLink {
                        ProgressionTiersScreen()
                    } label: {
                        HStack(spacing: 6) {
                            Image(prog.levelBadgeAssetName)
                                .resizable()
                                .scaledToFit()
                                .frame(width: 22, height: 22)
                                .accessibilityHidden(true)

                            Text(profileTierOrdinalText(for: prog))
                                .font(.subheadline.weight(.semibold))
                                .foregroundStyle(tierStyle.color)

                            Text("·")
                                .font(.subheadline.weight(.bold))
                                .foregroundStyle(.white.opacity(0.36))

                            Text("Level \(prog.level)")
                                .font(.subheadline.weight(.semibold))
                                .foregroundStyle(.white.opacity(0.78))

                            Image(systemName: "chevron.right")
                                .font(.footnote.weight(.bold))
                                .foregroundStyle(.white.opacity(0.40))
                        }
                    }
                    .buttonStyle(.plain)
                    .accessibilityElement(children: .combine)
                    .accessibilityHint("Shows progression tiers")

                    heroXpProgressSection(progression: prog, tierStyle: tierStyle)
                        .padding(.horizontal, 4)
                } else if isDriveStatsHiddenFromViewer {
                    Text("Drive stats are private.")
                        .font(.subheadline.weight(.medium))
                        .foregroundStyle(.white.opacity(0.62))
                        .multilineTextAlignment(.center)
                }

                if !isCurrentUserProfile, appState.canDirectMessage(userID: resolvedProfileUserID) {
                    Button {
                        dismissViewedProfile()
                        appState.requestDirectMessageFocus(userID: resolvedProfileUserID)
                    } label: {
                        Label("Chat", systemImage: "bubble.left.and.bubble.right.fill")
                            .font(.subheadline.weight(.bold))
                            .foregroundStyle(.white)
                            .padding(.horizontal, 18)
                            .padding(.vertical, 10)
                            .background(Color.white.opacity(0.12))
                            .clipShape(Capsule())
                    }
                    .buttonStyle(.plain)
                }
            }
            .frame(maxWidth: .infinity)
            .padding(.horizontal, 16)
            .padding(.top, 44)
            .padding(.bottom, 20)

            profileHeroToolbar()
                .padding(12)
        }
        .background {
            heroCardBackgroundLayers(tierStyle: tierStyle)
        }
        .overlay {
            heroCardBorderStroke(tierStyle: tierStyle)
        }
        .clipShape(RoundedRectangle(cornerRadius: 28, style: .continuous))
        .onAppear {
            profileHeroProgressRevealed = false
            DispatchQueue.main.async {
                withAnimation(.easeOut(duration: 0.88)) {
                    profileHeroProgressRevealed = true
                }
            }
        }
    }

    @ViewBuilder
    private func heroAvatarBlock(tierStyle: ProfileTierStyle) -> some View {
        ZStack {
            Circle()
                .fill(
                    RadialGradient(
                        colors: [
                            tierStyle.color.opacity(0.36),
                            tierStyle.color.opacity(0.06),
                            Color.clear,
                        ],
                        center: .center,
                        startRadius: 5,
                        endRadius: 60,
                    ),
                )
                .frame(width: 118, height: 118)

            if isCurrentUserProfile {
                ZStack(alignment: .bottomTrailing) {
                    Button {
                        isShowingProfilePhotoPicker = true
                    } label: {
                        AvatarView(
                            name: profileName,
                            avatarUrl: currentUserAvatarUrl,
                            size: 110,
                            accentColor: tierStyle.color,
                            accentRingWidth: 4,
                            whiteRingWidth: 1,
                        )
                        .shadow(color: tierStyle.color.opacity(0.55), radius: 12, x: 0, y: 0)
                        .shadow(color: tierStyle.color.opacity(0.22), radius: 26, x: 0, y: 12)
                    }
                    .buttonStyle(.plain)

                    Menu {
                        profileEditMenuItems()
                    } label: {
                        Image(systemName: "pencil")
                            .font(.system(size: 13, weight: .bold))
                            .foregroundStyle(.white)
                            .frame(width: 34, height: 34)
                            .background(Color.black.opacity(0.74))
                            .clipShape(Circle())
                            .overlay(
                                Circle()
                                    .stroke(tierStyle.color.opacity(0.58), lineWidth: 1)
                            )
                    }
                    .offset(x: 5, y: 5)
                    .buttonStyle(.plain)
                    .accessibilityLabel("More profile actions")
                    .zIndex(1)
                }
            } else {
                AvatarView(
                    name: profileName,
                    avatarUrl: currentUserAvatarUrl,
                    size: 110,
                    accentColor: tierStyle.color,
                    accentRingWidth: 4,
                    whiteRingWidth: 1,
                )
                .shadow(color: tierStyle.color.opacity(0.5), radius: 12, x: 0, y: 0)
                .shadow(color: tierStyle.color.opacity(0.2), radius: 24, x: 0, y: 10)
            }
        }
        .accessibilityHint(isCurrentUserProfile ? "Opens photo picker for profile picture" : "")
    }

    @ViewBuilder
    private func heroXpProgressSection(progression: ProfileProgressionDTO, tierStyle: ProfileTierStyle) -> some View {
        let progress = CGFloat(
            profileHeroProgressRevealed
                ? min(1, max(0, progression.progress))
                : 0
        )

        VStack(spacing: 6) {
            GeometryReader { geo in
                let w = max(geo.size.width, 1)

                ZStack(alignment: .leading) {
                    Capsule(style: .continuous)
                        .fill(Color.black.opacity(0.54))
                        .overlay(
                            Capsule(style: .continuous)
                                .stroke(Color.white.opacity(0.07), lineWidth: 1)
                        )

                    Capsule(style: .continuous)
                        .fill(
                            LinearGradient(
                                colors: [
                                    tierStyle.color.opacity(0.95),
                                    tierStyle.secondary.opacity(0.92),
                                    tierStyle.color.opacity(0.82),
                                ],
                                startPoint: .leading,
                                endPoint: .trailing,
                            ),
                        )
                        .frame(width: max(0, w * progress))
                        .shadow(color: tierStyle.color.opacity(0.4), radius: 5, x: 0, y: 0)
                        .overlay(alignment: .trailing) {
                            if progress > 0.04 {
                                Capsule(style: .continuous)
                                    .fill(
                                        LinearGradient(
                                            colors: [
                                                Color.white.opacity(0.58),
                                                Color.white.opacity(0.08),
                                            ],
                                            startPoint: .leading,
                                            endPoint: .trailing,
                                        ),
                                    )
                                    .frame(width: 5, height: 12)
                                    .offset(x: -3)
                            }
                        }
                }
                .animation(.easeOut(duration: 0.88), value: profileHeroProgressRevealed)
                .animation(.easeOut(duration: 0.42), value: progression.progress)
            }
            .frame(height: 8)

            Text(profileProgressionSubtitle(progression))
                .font(.caption.weight(.semibold))
                .foregroundStyle(tierStyle.color)
                .multilineTextAlignment(.center)
        }
        .accessibilityElement(children: .combine)
    }

    private func dismissViewedProfile() {
        dismiss()
        onDismissPeerProfile?()
    }

    @ViewBuilder
    private func profileHeroToolbar() -> some View {
        HStack(spacing: 8) {
            if !isCurrentUserProfile {
                Button(action: dismissViewedProfile) {
                    Image(systemName: "chevron.left")
                        .font(.headline.weight(.semibold))
                        .foregroundStyle(.white)
                        .modifier(ProfileHeroIconChrome())
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Back")
            }

            Spacer(minLength: 0)

            ShareLink(
                item: profileShareURL,
                subject: Text("\(profileName) on Driftd"),
                message: Text("View \(profileName)'s Driftd profile."),
                preview: SharePreview(
                    "\(profileName) on Driftd",
                    image: Image(systemName: "person.crop.circle.fill")
                )
            ) {
                Image(systemName: "square.and.arrow.up")
                    .font(.headline.weight(.semibold))
                    .foregroundStyle(.white)
                    .modifier(ProfileHeroIconChrome())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Share profile")

            if isCurrentUserProfile {
                Menu {
                    profileEditMenuItems()
                } label: {
                    Image(systemName: "ellipsis")
                        .font(.headline.weight(.semibold))
                        .foregroundStyle(.white)
                        .modifier(ProfileHeroIconChrome())
                }
                .accessibilityLabel("More profile actions")
            }
        }
    }

    @ViewBuilder
    private func profileEditMenuItems() -> some View {
        Button("Edit profile photo") {
            isShowingProfilePhotoPicker = true
        }
        Button("Edit Name") {
            profileNameDraft = profileName
            isShowingNameEditor = true
        }
        .disabled(isSavingProfileName)
    }

    private func heroCardBackgroundLayers(tierStyle: ProfileTierStyle) -> some View {
        let rr = RoundedRectangle(cornerRadius: 28, style: .continuous)

        return ZStack {
            rr.fill(Color(red: 0.03, green: 0.034, blue: 0.055))

            rr.fill(
                LinearGradient(
                    colors: [
                        tierStyle.color.opacity(0.12),
                        Color.clear,
                        tierStyle.secondary.opacity(0.10),
                        Color.clear,
                    ],
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing,
                ),
            )

            rr.fill(
                LinearGradient(
                    colors: [
                        Color.white.opacity(0.068),
                        Color.clear,
                        Color.black.opacity(0.28),
                    ],
                    startPoint: .top,
                    endPoint: .bottom,
                ),
            )
        }
    }

    private func heroCardBorderStroke(tierStyle: ProfileTierStyle) -> some View {
        RoundedRectangle(cornerRadius: 28, style: .continuous)
            .strokeBorder(
                LinearGradient(
                    colors: [
                        tierStyle.color.opacity(0.44),
                        Color.white.opacity(0.085),
                        tierStyle.secondary.opacity(0.32),
                    ],
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing,
                ),
                lineWidth: 1,
            )
            .overlay {
                RoundedRectangle(cornerRadius: 28, style: .continuous)
                    .strokeBorder(Color.white.opacity(0.05), lineWidth: 1)
                    .padding(0.75)
                    .blendMode(.plusLighter)
            }
    }

    private var garagePreviewCard: some View {
        Group {
            if isCurrentUserProfile {
                Button {
                    if let onOpenOwnGarage {
                        onOpenOwnGarage()
                    } else {
                        appState.requestGarageTabFocus()
                    }
                } label: {
                    garagePreviewCardChrome
                }
                .buttonStyle(.plain)
            } else {
                NavigationLink {
                    GarageScreen(viewedUserID: resolvedProfileUserID, viewedDisplayName: profileName)
                } label: {
                    garagePreviewCardChrome
                }
                .buttonStyle(.plain)
            }
        }
    }

    private var garagePreviewCardChrome: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text(isCurrentUserProfile ? "My Garage" : "\(profileName)'s Garage")
                    .font(.headline.weight(.semibold))
                Spacer()
                HStack(spacing: 5) {
                    Text("View")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(.purple)
                    Image(systemName: "chevron.right")
                        .font(.caption2.weight(.bold))
                        .foregroundStyle(.purple)
                }
            }

            if let featuredGarageCar {
                GarageCarCard(
                    car: featuredGarageCar,
                    canEdit: false,
                    onEdit: {},
                    onDelete: {}
                )
            } else {
                RoundedRectangle(cornerRadius: 18)
                    .fill(
                        LinearGradient(
                            colors: [Color.white.opacity(0.10), Color.white.opacity(0.03)],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        )
                    )
                    .frame(height: 124)
                    .overlay {
                        VStack(spacing: 8) {
                            Image(systemName: "car.side.fill")
                                .font(.system(size: 44, weight: .semibold))
                                .foregroundStyle(.white.opacity(0.88))
                            Text(featuredCarName)
                                .font(.subheadline.weight(.semibold))
                                .foregroundStyle(.white)
                            Text(featuredCarDetail)
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                        .padding(.horizontal, 10)
                    }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(14)
        .background(Color.black.opacity(0.55))
        .overlay(
            RoundedRectangle(cornerRadius: 20)
                .stroke(Color.white.opacity(0.1), lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: 20))
    }

    private var drivingStatsCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text("Driving Stats")
                    .font(.headline.weight(.semibold))
                    .foregroundStyle(.white)
                Spacer()
                Button {
                    Task { await refreshDrivingStatsForProfile() }
                } label: {
                    HStack(spacing: 4) {
                        if isRefreshingDrivingStats {
                            ProgressView()
                                .controlSize(.small)
                                .tint(.purple)
                        } else {
                            Image(systemName: "arrow.clockwise")
                        }
                        Text("Refresh")
                    }
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.purple)
                }
                .buttonStyle(.plain)
                .disabled(isRefreshingDrivingStats)
                .accessibilityLabel("Refresh")
            }

            if let drivingStats {
                if drivingStats.driveStatsVisible {
                    LazyVGrid(
                        columns: [GridItem(.flexible(), spacing: 10), GridItem(.flexible(), spacing: 10)],
                        alignment: .leading,
                        spacing: 10
                    ) {
                        drivingStatTile(title: "Miles driven", value: milesText(drivingStats.totalMilesDriven), icon: "road.lanes")
                        drivingStatTile(title: "Drive time", value: durationText(drivingStats.totalDriveTimeSeconds), icon: "timer")
                        drivingStatTile(title: "Avg speed", value: speedText(drivingStats.avgSpeedMph), icon: "speedometer")
                        drivingStatTile(title: "Top speed", value: drivingStats.topSpeedMph > 0 ? speedText(drivingStats.topSpeedMph) : "—", icon: "gauge.high")
                        drivingStatTile(title: "Last drive", value: relativeDateText(drivingStats.lastDriveAt), icon: "clock")
                        drivingStatTile(
                            title: "Events attended",
                            value: drivingStats.eventsAttended > 0 ? "\(drivingStats.eventsAttended)" : "None",
                            icon: "ticket.fill"
                        )
                    }
                } else {
                    Text("This member’s driving stats are private.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            } else {
                Text("No driving stats yet.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(14)
        .background(Color.black.opacity(0.55))
        .overlay(
            RoundedRectangle(cornerRadius: 20)
                .stroke(Color.white.opacity(0.1), lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: 20))
    }

    private var myDrivesSection: some View {
        Group {
            if profileDriveListItemCount > 0 {
                VStack(alignment: .leading, spacing: 12) {
                    HStack {
                        Text("My Drives")
                            .font(.headline.weight(.semibold))
                            .foregroundStyle(.white)
                        Spacer()
                        if profileDriveListItemCount > ProfileListPreview.limit {
                            Button("View all") {
                                isShowingAllProfileDrives = true
                            }
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(.purple)
                        }
                        Text("\(profileDriveListItemCount)")
                            .font(.caption.weight(.bold))
                            .foregroundStyle(.purple)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 4)
                            .background(Capsule().fill(Color.purple.opacity(0.18)))
                    }

                    VStack(spacing: 10) {
                        ForEach(Array(sortedPendingProfileDrives.prefix(ProfileListPreview.limit))) { archive in
                            ProfilePendingDriveInteractiveRow(
                                archive: archive,
                                onOpen: { selectedPendingDriveArchive = archive }
                            )
                        }
                        ForEach(Array(sortedProfileDrivesForDisplay.prefix(ProfileListPreview.limit))) { drive in
                            ProfileInteractiveDriveRow(
                                drive: drive,
                                onOpen: { selectedProfileDrive = drive },
                                onShare: { presentProfileDriveShare(drive) },
                                onRename: { beginProfileDriveRename(drive) },
                                onDelete: { profileDrivePendingDelete = drive }
                            )
                        }
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(14)
                .background(Color.black.opacity(0.55))
                .overlay(
                    RoundedRectangle(cornerRadius: 20)
                        .stroke(Color.white.opacity(0.1), lineWidth: 1)
                )
                .clipShape(RoundedRectangle(cornerRadius: 20))
            }
        }
    }

    @ViewBuilder
    private func profileDriveSummarySheet(_ drive: DriveDTO) -> some View {
        DriveSummaryScreen(
            drive: drive,
            isOwner: isCurrentUserProfile,
            garageCars: appState.garageCars,
            onDriveUpdated: handleProfileDriveUpdated,
            onDriveDeleted: { handleProfileDriveDeleted(driveID: drive.id) }
        )
        .environmentObject(appState)
        .presentationDetents([.large])
        .presentationBackground(Color.black)
    }

    private func applyProfileDriveListUpdate(_ updatedDrive: DriveDTO) {
        appState.applyDriveUpdate(updatedDrive)
    }

    private func handleProfileDriveUpdated(_ updatedDrive: DriveDTO) {
        applyProfileDriveListUpdate(updatedDrive)
        if selectedProfileDrive?.id == updatedDrive.id {
            selectedProfileDrive = updatedDrive
        }
    }

    private func handleProfileDriveDeleted(driveID: String) {
        appState.removeDriveFromRecent(id: driveID)
        if selectedProfileDrive?.id == driveID {
            selectedProfileDrive = nil
        }
    }

    private func handleProfileRouteDeleted(routeID: String) {
        profileRoutes.removeAll { $0.id == routeID }
        if selectedProfileRoute?.id == routeID {
            selectedProfileRoute = nil
        }
    }

    private func presentProfileDriveShare(_ drive: DriveDTO) {
        guard let context = ProfileDriveShareFormatting.shareContext(for: drive) else {
            appState.activeToast = AppToast(
                text: "Only completed drives can be shared",
                systemImage: "exclamationmark.triangle.fill"
            )
            return
        }
        profileDriveShareContext = context
    }

    private func beginProfileDriveRename(_ drive: DriveDTO) {
        profileDriveRenameTarget = drive
        profileDriveRenameDraft = ProfileDriveShareFormatting.title(for: drive)
    }

    @MainActor
    private func saveProfileDriveRename(_ drive: DriveDTO, title: String) async -> DriveDTO? {
        let trimmed = title.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty, !isRenamingProfileDrive else { return nil }
        isRenamingProfileDrive = true
        defer { isRenamingProfileDrive = false }
        do {
            let updated = try await APIClient.shared.updateDriveTitle(driveId: drive.id, title: trimmed)
            applyProfileDriveListUpdate(updated)
            profileDriveRenameTarget = nil
            return updated
        } catch {
            appState.errorMessage = "Couldn't rename this drive."
            return nil
        }
    }

    @MainActor
    private func confirmProfileDriveDeletion(_ drive: DriveDTO) async {
        if await ProfileDriveDeletion.delete(drive) { driveID in
            handleProfileDriveDeleted(driveID: driveID)
        } != nil {
            appState.presentDeleteFailedToast(for: "drive")
        } else {
            appState.presentDeletedToast(for: "Drive")
        }
        profileDrivePendingDelete = nil
    }

    private func beginProfileRouteRename(_ route: SavedRouteDTO) {
        profileRouteRenameTarget = route
        profileRouteRenameDraft = route.name
    }

    @MainActor
    private func saveProfileRouteRename(_ route: SavedRouteDTO, title: String) async -> SavedRouteDTO? {
        let trimmed = title.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty, !isRenamingProfileRoute else { return nil }
        isRenamingProfileRoute = true
        defer { isRenamingProfileRoute = false }
        do {
            let updated = try await APIClient.shared.updateRoute(
                routeId: route.id,
                name: trimmed,
                points: route.points,
                roadCoordinates: route.roadCoordinates,
                distanceMeters: route.distanceMeters,
                etaSeconds: route.etaSeconds
            )
            applyProfileRouteListUpdate(updated)
            profileRouteRenameTarget = nil
            return updated
        } catch {
            appState.errorMessage = "Couldn't rename this route."
            return nil
        }
    }

    private func applyProfileRouteListUpdate(_ updatedRoute: SavedRouteDTO) {
        if let index = profileRoutes.firstIndex(where: { $0.id == updatedRoute.id }) {
            profileRoutes[index] = updatedRoute
        }
    }

    private func presentProfileRouteEditor(_ route: SavedRouteDTO) {
        appState.prepareRouteBuilderPresentation()
        selectedProfileRoute = route
    }

    private func presentNewProfileRouteBuilder() {
        appState.prepareRouteBuilderPresentation()
        isShowingNewProfileRouteBuilder = true
    }

    private func editProfileRoute(_ route: SavedRouteDTO) {
        presentProfileRouteEditor(route)
    }

    private func startProfileRouteDrive(_ route: SavedRouteDTO) {
        appState.requestMapTabRouteFocus(route: route, startDrive: true)
    }

    private func presentProfileRouteShare(_ route: SavedRouteDTO) {
        _ = route
        appState.activeToast = AppToast(text: "Route sharing is coming soon.", systemImage: "info.circle.fill")
    }

    private func handleProfileRouteSaved(_ route: SavedRouteDTO) {
        if let index = profileRoutes.firstIndex(where: { $0.id == route.id }) {
            profileRoutes[index] = route
        } else {
            profileRoutes.insert(route, at: 0)
        }
    }

    private var profileRouteBuilderInitialCenter: CLLocationCoordinate2D {
        if let coordinate = locationService.latestSample?.coordinate ?? locationService.lastLocation?.coordinate,
           CLLocationCoordinate2DIsValid(coordinate) {
            return coordinate
        }
        return CLLocationCoordinate2D(latitude: 37.7749, longitude: -122.4194)
    }

    @MainActor
    private func confirmProfileRouteDeletion(_ route: SavedRouteDTO) async {
        if await ProfileRouteDeletion.delete(route) { routeID in
            handleProfileRouteDeleted(routeID: routeID)
        } != nil {
            appState.presentDeleteFailedToast(for: "route")
        } else {
            appState.presentDeletedToast(for: "Route")
        }
        profileRoutePendingDelete = nil
    }

    private var myRoutesSection: some View {
        Group {
            if isCurrentUserProfile, appState.hasRoutesAccess {
                VStack(alignment: .leading, spacing: 12) {
                    HStack {
                        Text("My Routes")
                            .font(.headline.weight(.semibold))
                            .foregroundStyle(.white)
                        Spacer()
                        if sortedProfileRoutesForDisplay.count > ProfileListPreview.limit {
                            Button("View all") {
                                isShowingAllProfileRoutes = true
                            }
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(.purple)
                        }
                        Text("\(sortedProfileRoutesForDisplay.count)")
                            .font(.caption.weight(.bold))
                            .foregroundStyle(.purple)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 4)
                            .background(Capsule().fill(Color.purple.opacity(0.18)))
                    }

                    CreateRouteListRow {
                        presentNewProfileRouteBuilder()
                    }

                    if !sortedProfileRoutesForDisplay.isEmpty {
                        profileEmbeddedRouteList(
                            routes: Array(sortedProfileRoutesForDisplay.prefix(ProfileListPreview.limit))
                        ) { route in
                            ProfileInteractiveRouteRow(
                                route: route,
                                onOpen: { presentProfileRouteEditor(route) },
                                onEdit: { editProfileRoute(route) },
                                onStartDrive: { startProfileRouteDrive(route) },
                                onShare: { presentProfileRouteShare(route) },
                                onRename: { beginProfileRouteRename(route) },
                                onDelete: { profileRoutePendingDelete = route }
                            )
                        }
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(14)
                .background(Color.black.opacity(0.55))
                .overlay(
                    RoundedRectangle(cornerRadius: 20)
                        .stroke(Color.white.opacity(0.1), lineWidth: 1)
                )
                .clipShape(RoundedRectangle(cornerRadius: 20))
            }
        }
    }

    private var sortedProfilePlacesForDisplay: [SavedPlaceDTO] {
        appState.savedPlaces.sorted { lhs, rhs in
            switch (lhs.createdAt, rhs.createdAt) {
            case let (left?, right?):
                return left > right
            case (_?, nil):
                return true
            case (nil, _?):
                return false
            case (nil, nil):
                return lhs.name.localizedCaseInsensitiveCompare(rhs.name) == .orderedAscending
            }
        }
    }

    private var myPlacesSection: some View {
        Group {
            if isCurrentUserProfile, !sortedProfilePlacesForDisplay.isEmpty {
                VStack(alignment: .leading, spacing: 12) {
                    HStack {
                        Text(String(localized: "profile_my_places_heading"))
                            .font(.headline.weight(.semibold))
                            .foregroundStyle(.white)
                        Spacer()
                        if sortedProfilePlacesForDisplay.count > ProfileListPreview.limit {
                            Button("View all") {
                                isShowingAllProfilePlaces = true
                            }
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(.purple)
                        }
                        Text("\(sortedProfilePlacesForDisplay.count)")
                            .font(.caption.weight(.bold))
                            .foregroundStyle(.purple)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 4)
                            .background(Capsule().fill(Color.purple.opacity(0.18)))
                    }

                    profileEmbeddedPlaceList(
                        places: Array(sortedProfilePlacesForDisplay.prefix(ProfileListPreview.limit))
                    ) { place in
                        ProfileInteractivePlaceRow(
                            place: place,
                            onOpen: { openProfilePlaceOnMap(place) },
                            onRename: { beginProfilePlaceRename(place) },
                            onDelete: { profilePlacePendingDelete = place }
                        )
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(14)
                .background(Color.black.opacity(0.55))
                .overlay(
                    RoundedRectangle(cornerRadius: 20)
                        .stroke(Color.white.opacity(0.1), lineWidth: 1)
                )
                .clipShape(RoundedRectangle(cornerRadius: 20))
            }
        }
    }

    private func openProfilePlaceOnMap(_ place: SavedPlaceDTO) {
        appState.requestMapTabCenteredOn(
            latitude: place.latitude,
            longitude: place.longitude,
            savedPlaceID: place.id
        )
    }

    private func beginProfilePlaceRename(_ place: SavedPlaceDTO) {
        profilePlaceRenameTarget = place
        profilePlaceRenameDraft = place.name
    }

    @MainActor
    private func saveProfilePlaceRename(_ place: SavedPlaceDTO, name: String) async -> SavedPlaceDTO? {
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty, trimmed != place.name else { return nil }
        isRenamingProfilePlace = true
        defer {
            isRenamingProfilePlace = false
            profilePlaceRenameTarget = nil
        }
        do {
            let updated = try await APIClient.shared.updateSavedPlace(
                placeId: place.id,
                name: trimmed,
                addressSummary: place.addressSummary,
                placeKind: place.placeKind,
                latitude: nil,
                longitude: nil
            )
            await appState.refreshSavedPlaces()
            return updated
        } catch {
            appState.errorMessage = String(localized: "marker_detail_edit_place_error")
            return nil
        }
    }

    @MainActor
    private func confirmProfilePlaceDeletion(_ place: SavedPlaceDTO) async {
        do {
            try await appState.deleteSavedPlace(placeId: place.id)
        } catch {
            appState.errorMessage = String(localized: "marker_detail_delete_place_error")
        }
        profilePlacePendingDelete = nil
    }

    /// Public events this user is “going” to (`GET /api/public/m/:id`), when their sharing preference allows.
    private var publicGoingEventsSection: some View {
        Group {
            if !publicGoingEvents.isEmpty {
                VStack(alignment: .leading, spacing: 12) {
                    Text(isCurrentUserProfile ? "Events I'm Attending" : "Public events")
                        .font(.headline.weight(.semibold))
                        .foregroundStyle(.white)

                    VStack(spacing: 10) {
                        ForEach(publicGoingEvents) { item in
                            NavigationLink {
                                EventDetailView(event: item.asEventStub())
                                    .environmentObject(appState)
                                    .environmentObject(locationService)
                            } label: {
                                publicGoingEventRow(item)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(14)
                .background(Color.black.opacity(0.55))
                .overlay(
                    RoundedRectangle(cornerRadius: 20)
                        .stroke(Color.white.opacity(0.1), lineWidth: 1)
                )
                .clipShape(RoundedRectangle(cornerRadius: 20))
            }
        }
    }

    /// Report / block actions for another member’s profile (not shown on the mini profile sheet).
    private var viewedProfileSafetySection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Safety")
                .font(.headline.weight(.semibold))
                .foregroundStyle(.white)

            VStack(spacing: 10) {
                profileSafetyActionButton(
                    title: "Report concern",
                    systemImage: "exclamationmark.bubble.fill",
                    role: .neutral
                ) {
                    openURL(WebsiteLinks.reportConcernMailto)
                }

                if appState.blockedUserIDs.contains(resolvedProfileUserID) {
                    profileSafetyActionButton(
                        title: "Unblock",
                        systemImage: "person.crop.circle.badge.plus",
                        role: .neutral
                    ) {
                        Task { await appState.unblockUser(resolvedProfileUserID) }
                    }
                } else {
                    profileSafetyActionButton(
                        title: "Block…",
                        systemImage: "hand.raised.fill",
                        role: .destructive
                    ) {
                        confirmBlockOtherUser = true
                    }
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(14)
        .background(Color.black.opacity(0.55))
        .overlay(
            RoundedRectangle(cornerRadius: 20)
                .stroke(Color.white.opacity(0.1), lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: 20))
    }

    private enum ProfileSafetyActionRole {
        case neutral
        case destructive
    }

    private func profileSafetyActionButton(
        title: String,
        systemImage: String,
        role: ProfileSafetyActionRole,
        action: @escaping () -> Void,
    ) -> some View {
        Button(action: action) {
            HStack(spacing: 12) {
                Image(systemName: systemImage)
                    .font(.system(size: 20, weight: .semibold))
                    .foregroundStyle(role == .destructive ? Color.red.opacity(0.92) : Color.purple.opacity(0.92))
                Text(title)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.white)
                Spacer(minLength: 0)
                Image(systemName: "chevron.right")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.white.opacity(0.38))
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 17)
            .frame(minHeight: 52)
            .background(Color.white.opacity(0.055))
            .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .stroke(Color.white.opacity(0.08), lineWidth: 1)
            }
        }
        .buttonStyle(.plain)
    }

    private func publicGoingEventRow(_ item: PublicGoingEventDTO) -> some View {
        HStack(spacing: 12) {
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .fill(Color.white.opacity(0.08))
                .frame(width: 48, height: 48)
                .overlay {
                    if let urlStr = item.bannerImageUrl, let u = URL(string: urlStr) {
                        CachedAsyncImage(url: u) { phase in
                            switch phase {
                            case .success(let img):
                                img.resizable().scaledToFill()
                            default:
                                Image(systemName: "calendar")
                                    .font(.title3)
                                    .foregroundStyle(.white.opacity(0.55))
                            }
                        }
                        .frame(width: 48, height: 48)
                        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                    } else {
                        Image(systemName: "calendar")
                            .font(.title3)
                            .foregroundStyle(.white.opacity(0.55))
                    }
                }
                .clipped()

            VStack(alignment: .leading, spacing: 4) {
                Text(item.name)
                    .font(.system(size: 17, weight: .semibold, design: .rounded))
                    .foregroundStyle(.white)
                    .multilineTextAlignment(.leading)
                    .lineLimit(1)
                Text(publicGoingEventMeta(item))
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

    private func publicGoingEventMeta(_ item: PublicGoingEventDTO) -> String {
        let t = DateFormatter()
        t.dateStyle = .medium
        t.timeStyle = .short
        let when = t.string(from: item.startsAt)
        if let label = item.addressLabel, !label.isEmpty {
            return "\(when) · \(label)"
        }
        return when
    }

    private func drivingStatTile(title: String, value: String, icon: String) -> some View {
        VStack(alignment: .leading, spacing: 7) {
            Image(systemName: icon)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(.purple)
            Text(value)
                .font(.subheadline.weight(.bold))
                .foregroundStyle(.white)
                .lineLimit(1)
                .minimumScaleFactor(0.8)
            Text(title)
                .font(.caption2.weight(.semibold))
                .foregroundStyle(.white.opacity(0.55))
                .lineLimit(1)
        }
        .padding(10)
        .frame(maxWidth: .infinity, minHeight: 86, alignment: .leading)
        .background(Color.white.opacity(0.055))
        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
    }

    private var menuList: some View {
        VStack(spacing: 10) {
            if isCurrentUserProfile {
                profileSafetyActionButton(
                    title: "Settings",
                    systemImage: "gearshape",
                    role: .neutral
                ) {
                    isShowingSettings = true
                }

                profileSafetyActionButton(
                    title: "Sign out",
                    systemImage: "rectangle.portrait.and.arrow.right",
                    role: .destructive
                ) {
                    isShowingSignOutConfirmation = true
                }

                profileLegalFooter
            }
        }
    }

    private var profileLegalFooterText: String {
        let year = Calendar.current.component(.year, from: Date())
        let version = (Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String) ?? "0"
        let build = (Bundle.main.infoDictionary?["CFBundleVersion"] as? String) ?? "0"
        return "Driftd \(year) © Otto Motto, LLC v.\(version) (\(build))"
    }

    private var profileLegalFooter: some View {
        Text(profileLegalFooterText)
            .font(.caption2)
            .foregroundStyle(.white.opacity(0.35))
            .multilineTextAlignment(.center)
            .frame(maxWidth: .infinity)
            .padding(.top, 16)
    }

    private func initials(from name: String) -> String {
        let parts = name.split(separator: " ")
        let letters = parts.prefix(2).compactMap(\.first)
        let result = letters.map(String.init).joined()
        return result.isEmpty ? "DR" : result
    }

    private func loadViewedProfileGaragePreview() async {
        guard !resolvedProfileUserID.isEmpty else {
            viewedProfileGarageCars = []
            return
        }
        do {
            let cars = try await APIClient.shared.fetchGarageCars(userId: resolvedProfileUserID)
            viewedProfileGarageCars = cars.map {
                GarageCar(
                    id: $0.id,
                    nickname: $0.nickname ?? "",
                    make: $0.make,
                    makeId: $0.makeId,
                    model: $0.model,
                    year: $0.year,
                    color: $0.color,
                    logoSlug: $0.logoSlug,
                    isPrimary: $0.isPrimary,
                    sortOrder: $0.sortOrder,
                    photoUrl: $0.photo?.url
                )
            }
        } catch {
            viewedProfileGarageCars = []
        }
    }

    private func loadDrivingStatsForProfile() async {
        guard !resolvedProfileUserID.isEmpty else {
            drivingStats = nil
            return
        }
        do {
            drivingStats = try await APIClient.shared.fetchDrivingStats(userId: resolvedProfileUserID)
        } catch {
            drivingStats = nil
        }
    }

    @MainActor
    private func refreshDrivingStatsForProfile() async {
        guard !isRefreshingDrivingStats else { return }
        isRefreshingDrivingStats = true
        defer { isRefreshingDrivingStats = false }
        await loadDrivingStatsForProfile()
    }

    @MainActor
    private func loadProfileDrives() async {
        guard isCurrentUserProfile, !resolvedProfileUserID.isEmpty else { return }
        appState.purgeExpiredPendingDrives()
        await appState.refreshRecentDrives()
    }

    @MainActor
    private func loadProfileRoutes() async {
        guard isCurrentUserProfile, appState.hasRoutesAccess else {
            profileRoutes = []
            return
        }
        do {
            let routes = try await APIClient.shared.fetchRoutes()
            profileRoutes = routes.filter { $0.createdByUserId == appState.currentUserID }
        } catch {
            profileRoutes = []
        }
    }

    @MainActor
    private func loadPublicGoingEventsForProfile() async {
        guard !resolvedProfileUserID.isEmpty else {
            publicGoingEvents = []
            return
        }
        do {
            publicGoingEvents = try await APIClient.shared.fetchPublicProfileGoingEvents(userId: resolvedProfileUserID)
        } catch {
            publicGoingEvents = []
        }
    }

    private func milesText(_ miles: Double) -> String {
        if miles < 10 {
            return String(format: "%.1f mi", miles)
        }
        return "\(Int(miles.rounded())) mi"
    }

    private func speedText(_ mph: Double) -> String {
        guard mph > 0 else { return "—" }
        return "\(Int(mph.rounded())) mph"
    }

    private func durationText(_ seconds: Double) -> String {
        guard seconds > 0 else { return "0m" }
        let totalMinutes = max(1, Int((seconds / 60).rounded()))
        let hours = totalMinutes / 60
        let minutes = totalMinutes % 60
        if hours > 0 {
            return minutes > 0 ? "\(hours)h \(minutes)m" : "\(hours)h"
        }
        return "\(minutes)m"
    }

    private func profileProgressionSubtitle(_ progression: ProfileProgressionDTO) -> String {
        if progression.isMaxLevel {
            return "\(progression.points.formatted()) points · Final unlock"
        }
        if let nextLevelAt = progression.nextLevelAt {
            return "\(progression.points.formatted()) / \(nextLevelAt.formatted()) points"
        }
        return "\(progression.points.formatted()) points"
    }

    @MainActor
    private func saveProfileName() async {
        let trimmed = profileNameDraft.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            appState.errorMessage = "Please enter your name."
            return
        }
        isSavingProfileName = true
        let didSave = await appState.updateCurrentUserDisplayName(trimmed)
        isSavingProfileName = false
        if didSave {
            isShowingNameEditor = false
        }
    }

    private func romanNumeral(_ value: Int) -> String {
        switch value {
        case 1: return "I"
        case 2: return "II"
        case 3: return "III"
        case 4: return "IV"
        default: return "\(value)"
        }
    }

    private func profileRouteSortDate(_ route: SavedRouteDTO) -> Date {
        let candidates = [route.updatedAt, route.createdAt].compactMap { raw -> Date? in
            guard let raw else { return nil }
            return ProfileRouteFormatting.isoFormatter.date(from: raw)
                ?? ProfileRouteFormatting.isoFormatterWithoutFractions.date(from: raw)
        }
        return candidates.first ?? .distantPast
    }

    private func relativeDateText(_ date: Date?) -> String {
        guard let date else { return "—" }
        let seconds = max(0, Int(Date().timeIntervalSince(date)))
        if seconds < 60 { return "Just now" }
        let minutes = seconds / 60
        if minutes < 60 { return "\(minutes)m ago" }
        let hours = minutes / 60
        if hours < 48 { return "\(hours)h ago" }
        return "\(hours / 24)d ago"
    }

}

private struct ProfileTierStyle {
    let color: Color
    let secondary: Color

    /// Neutral accent when this viewer cannot see progression (no misleading tier color).
    static let privateStatsAccent = ProfileTierStyle(
        color: Color.white.opacity(0.55),
        secondary: Color.white.opacity(0.32)
    )

    static func style(for tierId: String) -> ProfileTierStyle {
        switch tierId {
        case "rookie":
            return ProfileTierStyle(
                color: Color(red: 0.78, green: 0.48, blue: 0.27),
                secondary: Color(red: 0.50, green: 0.28, blue: 0.12)
            )
        case "qualifier":
            return ProfileTierStyle(
                color: Color(red: 0.78, green: 0.80, blue: 0.84),
                secondary: Color(red: 0.48, green: 0.52, blue: 0.60)
            )
        case "runner":
            return ProfileTierStyle(
                color: Color(red: 1.0, green: 0.84, blue: 0.22),
                secondary: Color(red: 0.78, green: 0.55, blue: 0.12)
            )
        case "pacer":
            return ProfileTierStyle(
                color: Color(red: 0.12, green: 0.66, blue: 1.0),
                secondary: Color(red: 0.04, green: 0.38, blue: 0.86)
            )
        case "apex":
            return ProfileTierStyle(
                color: Color(red: 0.66, green: 0.31, blue: 1.0),
                secondary: Color(red: 0.40, green: 0.12, blue: 0.78)
            )
        case "legend":
            return ProfileTierStyle(
                color: Color(red: 1.0, green: 0.35, blue: 0.55),
                secondary: Color(red: 1.0, green: 0.45, blue: 0.20)
            )
        default:
            return ProfileTierStyle(
                color: .purple,
                secondary: Color(red: 0.45, green: 0.10, blue: 0.65)
            )
        }
    }
}

private struct ProfileHeroIconChrome: ViewModifier {
    func body(content: Content) -> some View {
        content
            .frame(width: 40, height: 40)
            .background(Color.white.opacity(0.06))
            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
    }
}

private enum ProfileRouteFormatting {
    static let distanceFormatter: MeasurementFormatter = {
        let formatter = MeasurementFormatter()
        formatter.unitOptions = .naturalScale
        formatter.unitStyle = .medium
        formatter.numberFormatter.maximumFractionDigits = 1
        return formatter
    }()

    static let isoFormatter: ISO8601DateFormatter = {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return formatter
    }()

    static let isoFormatterWithoutFractions: ISO8601DateFormatter = {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        return formatter
    }()
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

private extension RoutePointDTO {
    var coordinate: CLLocationCoordinate2D {
        CLLocationCoordinate2D(latitude: lat, longitude: lng)
    }
}

private struct ProgressionTierInfo: Identifiable {
    let id: String
    let name: String
    let levelsText: String
    let pointsPerLevelText: String
    let totalTierPointsText: String?
    let badgeImageName: String
}

private struct ProgressionPointRule: Identifiable {
    let id: String
    let label: String
    let points: Int
}

#if DEBUG
private struct ProgressionPreviewOption: Identifiable {
    let level: Int
    let label: String

    var id: Int { level }
}
#endif

private struct ProgressionTiersScreen: View {
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject private var appState: AppState

    private let tiers: [ProgressionTierInfo] = [
        ProgressionTierInfo(
            id: "rookie",
            name: "Rookie",
            levelsText: "Levels 1-4",
            pointsPerLevelText: "250 XP per level",
            totalTierPointsText: "1,000 total",
            badgeImageName: "Level1"
        ),
        ProgressionTierInfo(
            id: "qualifier",
            name: "Qualifier",
            levelsText: "Levels 5-8",
            pointsPerLevelText: "500 XP per level",
            totalTierPointsText: "2,000 total",
            badgeImageName: "Level5"
        ),
        ProgressionTierInfo(
            id: "runner",
            name: "Runner",
            levelsText: "Levels 9-12",
            pointsPerLevelText: "1,000 XP per level",
            totalTierPointsText: "4,000 total",
            badgeImageName: "Level9"
        ),
        ProgressionTierInfo(
            id: "pacer",
            name: "Pacer",
            levelsText: "Levels 13-16",
            pointsPerLevelText: "2,000 XP per level",
            totalTierPointsText: "8,000 total",
            badgeImageName: "Level13"
        ),
        ProgressionTierInfo(
            id: "apex",
            name: "Apex",
            levelsText: "Levels 17-19",
            pointsPerLevelText: "4,000 XP per level",
            totalTierPointsText: "12,000 total",
            badgeImageName: "Level17"
        ),
        ProgressionTierInfo(
            id: "legend",
            name: "Legend",
            levelsText: "Level 20",
            pointsPerLevelText: "Final unlock",
            totalTierPointsText: nil,
            badgeImageName: "Level20"
        ),
    ]

    #if DEBUG
    private let previewOptions: [ProgressionPreviewOption] = [
        ProgressionPreviewOption(level: 2, label: "Rookie II"),
        ProgressionPreviewOption(level: 5, label: "Qualifier I"),
        ProgressionPreviewOption(level: 9, label: "Runner I"),
        ProgressionPreviewOption(level: 13, label: "Pacer I"),
        ProgressionPreviewOption(level: 17, label: "Apex I"),
        ProgressionPreviewOption(level: 20, label: "Legend"),
    ]
    #endif

    var body: some View {
        ZStack {
            LinearGradient(
                colors: [
                    Color(red: 0.01, green: 0.01, blue: 0.05),
                    Color(red: 0.04, green: 0.03, blue: 0.12),
                    Color.black,
                ],
                startPoint: .top,
                endPoint: .bottom
            )
            .ignoresSafeArea()

            ScrollView(showsIndicators: false) {
                VStack(spacing: 12) {
                    header

                    VStack(spacing: 10) {
                        ForEach(tiers) { tier in
                            ProgressionTierCard(tier: tier)
                        }
                    }
                    .padding(.top, 10)

                    howItWorksCard
                        .padding(.top, 10)

                    #if DEBUG
                    previewToolsCard
                        .padding(.top, 10)
                    #endif
                }
                .padding(.horizontal, 22)
                .padding(.top, 20)
                .padding(.bottom, 34)
            }
        }
        .toolbar(.hidden, for: .navigationBar)
    }

    private var header: some View {
        ZStack(alignment: .leading) {
            Button {
                dismiss()
            } label: {
                Image(systemName: "chevron.left")
                    .font(.subheadline.weight(.bold))
                    .foregroundStyle(.white.opacity(0.9))
                    .frame(width: 34, height: 34)
                    .background(Color.white.opacity(0.08))
                    .overlay(Circle().stroke(Color.white.opacity(0.12), lineWidth: 1))
                    .clipShape(Circle())
            }
            .buttonStyle(.plain)

            VStack(spacing: 6) {
                Text("Progression")
                    .font(.system(size: 28, weight: .heavy, design: .rounded))
                    .foregroundStyle(.white)
                Text("Level up. Unlock tiers. Stand out.")
                    .font(.subheadline.weight(.medium))
                    .foregroundStyle(.white.opacity(0.62))
            }
            .frame(maxWidth: .infinity)
        }
        .padding(.bottom, 18)
    }

    private var howItWorksCard: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(spacing: 14) {
                ZStack {
                    Circle()
                        .fill(Color.purple.opacity(0.16))
                    Circle()
                        .stroke(Color.purple.opacity(0.62), lineWidth: 1)
                    Image(systemName: "sparkle")
                        .font(.title2.weight(.heavy))
                        .foregroundStyle(Color.purple.opacity(0.95))
                }
                .frame(width: 54, height: 54)

                VStack(alignment: .leading, spacing: 5) {
                    Text("How it works")
                        .font(.subheadline.weight(.heavy))
                        .foregroundStyle(.white)
                    Text(String(localized: "progression_how_body"))
                        .font(.caption.weight(.medium))
                        .foregroundStyle(.white.opacity(0.62))
                        .fixedSize(horizontal: false, vertical: true)
                }
                Spacer(minLength: 0)
            }

            VStack(spacing: 10) {
                ForEach(progressionPointRules) { rule in
                    HStack(alignment: .top, spacing: 10) {
                        Text(rule.label)
                            .font(.caption.weight(.medium))
                            .foregroundStyle(.white.opacity(0.72))
                            .fixedSize(horizontal: false, vertical: true)
                        Spacer(minLength: 8)
                        Text("+\(rule.points) XP")
                            .font(.caption.weight(.heavy))
                            .foregroundStyle(Color.purple.opacity(0.95))
                    }
                }
            }
        }
        .padding(16)
        .background(Color.white.opacity(0.045))
        .overlay(
            RoundedRectangle(cornerRadius: 18)
                .stroke(Color.white.opacity(0.10), lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
    }

    private var progressionPointRules: [ProgressionPointRule] {
        [
            ProgressionPointRule(
                id: "daily_launch",
                label: String(localized: "progression_points_daily_launch"),
                points: 20
            ),
            ProgressionPointRule(
                id: "daily_squad_location_share",
                label: String(localized: "progression_points_daily_squad_location_share"),
                points: 40
            ),
            ProgressionPointRule(
                id: "daily_first_chat_message",
                label: String(localized: "progression_points_daily_first_chat_message"),
                points: 20
            ),
            ProgressionPointRule(
                id: "event_check_in_public",
                label: String(localized: "progression_points_event_check_in_public"),
                points: 100
            ),
            ProgressionPointRule(
                id: "event_check_in_circle",
                label: String(localized: "progression_points_event_check_in_circle"),
                points: 20
            ),
            ProgressionPointRule(
                id: "signup_invite_redeemed",
                label: String(localized: "progression_points_signup_invite_redeemed"),
                points: 150
            ),
        ]
    }

    #if DEBUG
    private var previewToolsCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 10) {
                Image(systemName: "hammer.fill")
                    .font(.subheadline.weight(.bold))
                    .foregroundStyle(.yellow)
                Text("Temporary preview tools")
                    .font(.subheadline.weight(.heavy))
                    .foregroundStyle(.white)
            }

            Text("Use these to preview the level-up modal and notification tap flow. Remove before release.")
                .font(.caption.weight(.medium))
                .foregroundStyle(.white.opacity(0.58))
                .fixedSize(horizontal: false, vertical: true)

            Text("Show modal")
                .font(.caption.weight(.heavy))
                .textCase(.uppercase)
                .tracking(1.2)
                .foregroundStyle(.white.opacity(0.52))

            LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 8) {
                ForEach(previewOptions) { option in
                    Button {
                        appState.previewProfileLevelUpModal(level: option.level)
                    } label: {
                        Text(option.label)
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(ProgressionPreviewButtonStyle())
                }
            }

            Button {
                appState.schedulePreviewProfileLevelUpNotification(level: 17)
            } label: {
                Label("Send Apex I test notification", systemImage: "bell.badge.fill")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(ProgressionPreviewButtonStyle())
        }
        .padding(16)
        .background(Color.yellow.opacity(0.08))
        .overlay(
            RoundedRectangle(cornerRadius: 18)
                .stroke(Color.yellow.opacity(0.22), lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
    }
    #endif
}

#if DEBUG
private struct ProgressionPreviewButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.subheadline.weight(.heavy))
            .foregroundStyle(.white)
            .padding(.vertical, 12)
            .padding(.horizontal, 14)
            .background(Color.white.opacity(configuration.isPressed ? 0.18 : 0.10))
            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
    }
}
#endif

private struct ProgressionTierCard: View {
    let tier: ProgressionTierInfo

    private var tierColor: Color {
        ProfileTierStyle.style(for: tier.id).color
    }

    var body: some View {
        HStack(spacing: 14) {
            Circle()
                .fill(tierColor)
                .frame(width: 9, height: 9)
                .shadow(color: tierColor.opacity(0.8), radius: 7)
                .offset(x: -5)

            Image(tier.badgeImageName)
                .resizable()
                .scaledToFit()
                .frame(width: 58, height: 58)
                .shadow(color: tierColor.opacity(0.5), radius: 14)

            VStack(alignment: .leading, spacing: 4) {
                Text(tier.name)
                    .font(.headline.weight(.heavy))
                    .foregroundStyle(tierColor)
                Text(tier.levelsText)
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.white.opacity(0.72))
            }

            Spacer(minLength: 8)

            Rectangle()
                .fill(Color.white.opacity(0.13))
                .frame(width: 1, height: 44)

            VStack(alignment: .leading, spacing: 3) {
                Text(tier.pointsPerLevelText)
                    .font(.subheadline.weight(.heavy))
                    .foregroundStyle(tierColor)
                    .lineLimit(1)
                    .minimumScaleFactor(0.75)
                Text(tier.totalTierPointsText == nil ? "Status" : "Earning")
                    .font(.system(size: 8, weight: .black))
                    .textCase(.uppercase)
                    .foregroundStyle(.white.opacity(0.38))
                    .tracking(0.8)
                if let totalTierPointsText = tier.totalTierPointsText {
                    Text(totalTierPointsText)
                        .font(.subheadline.weight(.heavy))
                        .foregroundStyle(.white)
                        .lineLimit(1)
                }
            }
            .frame(width: 126, alignment: .leading)
        }
        .padding(.vertical, 14)
        .padding(.leading, 0)
        .padding(.trailing, 14)
        .background(
            ZStack(alignment: .leading) {
                Color.white.opacity(0.045)
                LinearGradient(
                    colors: [tierColor.opacity(0.16), .clear],
                    startPoint: .leading,
                    endPoint: .trailing
                )
            }
        )
        .overlay(
            RoundedRectangle(cornerRadius: 18)
                .stroke(tierColor.opacity(0.34), lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
    }
}

struct ProfileSettingsSheet: View {
    @EnvironmentObject private var appState: AppState
    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL
    @State private var isShowingDeletePhraseSheet = false
    @State private var confirmedPhrasePendingFinalAlert: String?
    @State private var isShowingFinalDeleteConfirmation = false
    @State private var phraseForDeletion: String?
    @State private var isDeletingAccount = false
    @State private var isShowingMapAccentPicker = false
    @AppStorage(OttoDebugSettings.mapLocationOverlayKey) private var mapLocationDiagnosticsEnabled = false
    @AppStorage(OttoDebugSettings.routeBuilderPerfOverlayKey) private var routeBuilderPerfOverlayEnabled = false
    @AppStorage(OttoDebugSettings.routeCheckpointMapOverlayKey) private var routeCheckpointMapDebugEnabled = true

    var body: some View {
        NavigationStack {
            ZStack {
                SettingsSheetChrome.settingsBackgroundGradient
                    .ignoresSafeArea()

                ScrollView(showsIndicators: false) {
                    VStack(alignment: .leading, spacing: 18) {
                        mapCard
                        eventsSettingsCard
                        driveStatsVisibilityCard
                        marketingOnboardingCard
                        settingsCard
                        legalCard
                        if appState.canAccessInternalDebugTools {
                            debugSettingsCard
                        }
                        deleteAccountCard
                    }
                    .padding(.horizontal, 18)
                    .padding(.top, 18)
                    .padding(.bottom, 28)
                }

                if isDeletingAccount {
                    Color.black.opacity(0.65)
                        .ignoresSafeArea()
                    VStack(spacing: 14) {
                        ProgressView()
                            .scaleEffect(1.15)
                            .tint(.white)
                        Text("Deleting account…")
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(.white)
                    }
                    .padding(28)
                    .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 18))
                }
            }
            .navigationTitle("Settings")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Done") { dismiss() }
                        .foregroundStyle(.white)
                        .disabled(isDeletingAccount)
                }
            }
        }
        .interactiveDismissDisabled(isDeletingAccount)
        .sheet(isPresented: $isShowingDeletePhraseSheet) {
            DeleteAccountPhraseConfirmationSheet { phrase in
                confirmedPhrasePendingFinalAlert = phrase
                isShowingDeletePhraseSheet = false
            }
            // Avoid `.medium` (or other compressed detents): with the keyboard up they force constant
            // relayout and the sheet feels sluggish / nearly frozen.
        }
        .sheet(isPresented: $isShowingMapAccentPicker) {
            MapAccentPickerSheet { key in
                Task { await appState.updateMapAccentKey(key) }
            }
        }
        .onChange(of: isShowingDeletePhraseSheet) { _, isPresented in
            guard !isPresented, let phrase = confirmedPhrasePendingFinalAlert else { return }
            confirmedPhrasePendingFinalAlert = nil
            phraseForDeletion = phrase
            isShowingFinalDeleteConfirmation = true
        }
        .alert("Delete account permanently?", isPresented: $isShowingFinalDeleteConfirmation) {
            Button("Cancel", role: .cancel) {
                phraseForDeletion = nil
            }
            Button("Delete account", role: .destructive) {
                Task { await deleteAccount() }
            }
        } message: {
            Text("This cannot be undone. Your profile, garage, drives, and related data will be removed.")
        }
    }

    private var mapCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Map")
                .font(.headline.weight(.semibold))
                .foregroundStyle(.white)

            Button {
                isShowingMapAccentPicker = true
            } label: {
                HStack(spacing: 12) {
                    Image(systemName: "mappin.circle")
                        .font(.subheadline.weight(.medium))
                        .frame(width: 22, alignment: .center)
                        .foregroundStyle(.white.opacity(0.9))
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Map pin color")
                            .font(.subheadline)
                            .foregroundStyle(.white)
                        Text("Choose your marker color on the map.")
                            .font(.caption)
                            .foregroundStyle(.white.opacity(0.62))
                    }
                    Spacer()
                    Circle()
                        .fill(appState.currentUserMapAccentColor)
                        .frame(width: 16, height: 16)
                    Image(systemName: "chevron.right")
                        .font(.caption.weight(.bold))
                        .foregroundStyle(.white.opacity(0.5))
                }
            }
            .buttonStyle(.plain)
        }
        .settingsCardStyle()
    }

    private var eventsSettingsCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Events")
                .font(.headline.weight(.semibold))
                .foregroundStyle(.white)

            Toggle(
                "Auto Check-In",
                isOn: Binding(
                    get: { appState.autoEventCheckInEnabled },
                    set: { appState.setAutoEventCheckInEnabled($0) }
                )
            )
            .tint(.purple)
            .font(.subheadline)
            .foregroundStyle(.white)

            Text("Automatically check me in when I arrive at events I’m going to.")
                .font(.caption)
                .foregroundStyle(.white.opacity(0.62))

            Toggle(
                "Show on my profile",
                isOn: Binding(
                    get: { appState.showPublicGoingEventsOnProfile },
                    set: { appState.setShowPublicGoingEventsOnProfile($0) }
                )
            )
            .tint(.purple)
            .font(.subheadline)
            .foregroundStyle(.white)
            .padding(.top, 6)

            Text("Let others see events you’re going to on your Driftd profile and your web page.")
                .font(.caption)
                .foregroundStyle(.white.opacity(0.62))
        }
        .settingsCardStyle()
    }

    private var driveStatsVisibilityCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Drive stats")
                .font(.headline.weight(.semibold))
                .foregroundStyle(.white)

            Picker("Visibility", selection: Binding(
                get: { appState.driveStatsVisibility },
                set: { appState.setDriveStatsVisibility($0) }
            )) {
                ForEach(DriveStatsVisibilitySetting.allCases) { option in
                    Text(option.settingsMenuLabel).tag(option)
                }
            }
            .pickerStyle(.segmented)

            Text(appState.driveStatsVisibility.settingsFootnote)
                .font(.caption)
                .foregroundStyle(.white.opacity(0.62))
        }
        .settingsCardStyle()
    }

    private var marketingOnboardingCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Driftd intro")
                .font(.headline.weight(.semibold))
                .foregroundStyle(.white)

            Button {
                appState.requestMarketingOnboardingReplay()
                dismiss()
            } label: {
                HStack(spacing: 12) {
                    Image(systemName: "arrow.counterclockwise.circle")
                        .font(.subheadline.weight(.medium))
                        .frame(width: 22, alignment: .center)
                        .foregroundStyle(.white.opacity(0.9))
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Replay welcome tour")
                            .font(.subheadline)
                            .foregroundStyle(.white)
                        Text("See the full-screen intro again anytime.")
                            .font(.caption)
                            .foregroundStyle(.white.opacity(0.62))
                    }
                    Spacer()
                    Image(systemName: "chevron.right")
                        .font(.caption.weight(.bold))
                        .foregroundStyle(.white.opacity(0.5))
                }
            }
            .buttonStyle(.plain)
        }
        .settingsCardStyle()
    }

    private var settingsCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Sound")
                .font(.headline.weight(.semibold))
                .foregroundStyle(.white)

            Toggle(
                "Sound effects",
                isOn: Binding(
                    get: { appState.soundEffectsEnabled },
                    set: { appState.setSoundEffectsEnabled($0) }
                )
            )
            .tint(.purple)
            .font(.subheadline)
            .foregroundStyle(.white)

            Text("Controls short app sounds like sharing notifications.")
                .font(.caption)
                .foregroundStyle(.white.opacity(0.62))
        }
        .settingsCardStyle()
    }

    private var legalCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Legal")
                .font(.headline.weight(.semibold))
                .foregroundStyle(.white)

            settingsLegalLink(
                title: "Privacy Policy",
                systemImage: "lock.document",
                url: WebsiteLinks.privacyPolicy
            )
            settingsLegalLink(
                title: "Terms of Use",
                systemImage: "doc.text",
                url: WebsiteLinks.termsOfUse
            )
            settingsLegalLink(
                title: "Report a safety concern",
                systemImage: "exclamationmark.bubble",
                url: WebsiteLinks.reportConcernMailto
            )
        }
        .settingsCardStyle()
    }

    private func settingsLegalLink(title: String, systemImage: String, url: URL) -> some View {
        Button {
            openURL(url)
        } label: {
            HStack(spacing: 12) {
                Image(systemName: systemImage)
                    .font(.subheadline.weight(.medium))
                    .frame(width: 22, alignment: .center)
                    .foregroundStyle(.white.opacity(0.9))
                Text(title)
                    .font(.subheadline)
                Spacer()
                Image(systemName: "arrow.up.right")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.white.opacity(0.5))
            }
        }
        .buttonStyle(.plain)
        .foregroundStyle(.white)
    }

    private var debugSettingsCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Debug")
                .font(.headline.weight(.semibold))
                .foregroundStyle(.orange)

            Toggle(
                "Map location overlay",
                isOn: $mapLocationDiagnosticsEnabled
            )
            .tint(.orange)
            .font(.subheadline)
            .foregroundStyle(.white)

            Toggle(
                "Route Builder perf overlay",
                isOn: $routeBuilderPerfOverlayEnabled
            )
            .tint(.orange)
            .font(.subheadline)
            .foregroundStyle(.white)

            Toggle(
                "Route checkpoint map debug",
                isOn: $routeCheckpointMapDebugEnabled
            )
            .tint(.orange)
            .font(.subheadline)
            .foregroundStyle(.white)

            Text(
                "Shows GPS, motion, and session flags on the Map tab. For troubleshooting pin and sharing issues."
            )
            .font(.caption)
            .foregroundStyle(.white.opacity(0.62))

            Text(
                "Shows live pan/zoom perf counters in Route Builder (camera rate, SwiftUI rebuilds, polyline size). Long-press the route title in DEBUG builds to toggle quickly."
            )
            .font(.caption)
            .foregroundStyle(.white.opacity(0.62))

            Text(
                "Green anchors and labels at each route checkpoint on the Map tab (distance, horizon scale, LOD, strip). Default on for internal debugging."
            )
            .font(.caption)
            .foregroundStyle(.white.opacity(0.62))
        }
        .settingsCardStyle()
    }

    private var deleteAccountCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Delete account")
                .font(.headline.weight(.semibold))
                .foregroundStyle(.red.opacity(0.95))

            Text(
                "Permanently removes your profile, garage, drives, squads you own, RSVPs, and other data tied to this account. You’ll be signed out when it’s done."
            )
            .font(.caption)
            .foregroundStyle(.white.opacity(0.68))

            Button {
                isShowingDeletePhraseSheet = true
            } label: {
                Text("Delete account")
                    .font(.subheadline.weight(.semibold))
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                    .background(Color.red.opacity(0.22))
                    .foregroundStyle(.white)
                    .clipShape(RoundedRectangle(cornerRadius: 14))
            }
            .buttonStyle(.plain)
            .disabled(isDeletingAccount)
        }
        .settingsCardStyle()
    }

    private func deleteAccount() async {
        guard let phrase = phraseForDeletion?.trimmingCharacters(in: .whitespacesAndNewlines),
              phrase == "delete account"
        else { return }
        isDeletingAccount = true
        phraseForDeletion = nil
        await appState.deleteAccount(confirmation: phrase)
        isDeletingAccount = false
        if !appState.isAuthenticated {
            dismiss()
        }
    }
}

/// Sheet: user must type exactly `delete account` before continuing to the final system confirmation.
private struct DeleteAccountPhraseConfirmationSheet: View {
    @Environment(\.dismiss) private var dismiss
    @FocusState private var isPhraseFieldFocused: Bool
    @State private var confirmationText = ""
    let onSubmitPhrase: (String) -> Void

    private var trimmed: String {
        confirmationText.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private var phraseMatches: Bool {
        trimmed == "delete account"
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Text("Type the phrase below exactly to confirm you want to delete your account.")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)

                    LabeledContent("Required phrase") {
                        Text("delete account")
                            .font(.body.monospaced())
                            .textSelection(.enabled)
                    }

                    TextField("Type phrase here", text: $confirmationText)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .submitLabel(.done)
                        .focused($isPhraseFieldFocused)
                        .onSubmit {
                            if phraseMatches { submit() }
                        }
                }

                Section {
                    Button {
                        submit()
                    } label: {
                        Text("Submit")
                            .font(.body.weight(.semibold))
                            .frame(maxWidth: .infinity)
                    }
                    .disabled(!phraseMatches)
                }
            }
            .scrollDismissesKeyboard(.interactively)
            .navigationTitle("Confirm deletion")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
            }
            .onAppear {
                // Focus after the sheet transition so the keyboard doesn’t fight layout / first responder.
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.45) {
                    isPhraseFieldFocused = true
                }
            }
        }
    }

    private func submit() {
        guard phraseMatches else { return }
        onSubmitPhrase(trimmed)
    }
}
