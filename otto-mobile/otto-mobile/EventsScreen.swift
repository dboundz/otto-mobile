import CoreLocation
import MapKit
import SwiftUI
import UIKit

private let eventDistancePresets = [25, 50, 100]

struct EventsScreen: View {
    var isActive: Bool = true
    @EnvironmentObject private var appState: AppState
    @EnvironmentObject private var locationService: LocationService
    @State private var selectedTab: EventsTab = .upcoming
    @State private var navigationPath: [String] = []
    @State private var showCustomDistanceSheet = false
    @State private var showEventsLocationPrimer = false
    @State private var pendingEventsLocationPermission = false
    @State private var showEventsLocationDeniedModal = false
    @State private var customDraftMiles: Double = 50
    @State private var lastCustomSnapHaptic: Int?
    @State private var squadGoingEvents: [EventDTO] = []
    @State private var allSquadEvents: [EventDTO] = []
    @State private var isLoadingSquadEvents = false
    @State private var squadEventsErrorMessage: String?
    /// Last known user location so radius filtering survives brief GPS gaps during refresh.
    @State private var cachedUserLocationForEvents: CLLocation?

    /// Straight-line search radius in miles; persisted. Range 5…200 (presets + custom sheet).
    @AppStorage("selected_event_distance") private var selectedEventDistance: Int = 50

    private var clampedEventDistance: Int {
        min(200, max(5, selectedEventDistance))
    }

    private var isCustomDistance: Bool {
        !eventDistancePresets.contains(clampedEventDistance)
    }

    private var searchRadiusMeters: Double { Double(clampedEventDistance) * 1609.34 }

    private var userLocationForNearby: CLLocation? {
        if let live = locationService.latestSample ?? locationService.lastLocation {
            return live
        }
        return cachedUserLocationForEvents
    }

    private var communityEventsWithDistance: [(event: EventDTO, miles: Double)] {
        guard let user = userLocationForNearby else { return [] }
        let maxMeters = searchRadiusMeters
        return appState.communityEvents.compactMap { event -> (EventDTO, Double)? in
            guard let coord = event.geoCoordinate else { return nil }
            let eventLoc = CLLocation(latitude: coord.latitude, longitude: coord.longitude)
            let meters = user.distance(from: eventLoc)
            guard meters <= maxMeters else { return nil }
            return (event, meters / 1609.34)
        }
        .sorted { lhs, rhs in lhs.event.startsAt < rhs.event.startsAt }
    }

    private var upcomingEventsWithDistance: [(event: EventDTO, miles: Double)] {
        guard let user = userLocationForNearby else { return [] }
        let maxMeters = searchRadiusMeters
        return appState.upcomingEvents.compactMap { event -> (EventDTO, Double)? in
            if event.adminOnly == true {
                if let coord = event.geoCoordinate {
                    let eventLoc = CLLocation(latitude: coord.latitude, longitude: coord.longitude)
                    let meters = user.distance(from: eventLoc)
                    return (event, meters / 1609.34)
                }
                return (event, 0)
            }
            guard let coord = event.geoCoordinate else { return nil }
            let eventLoc = CLLocation(latitude: coord.latitude, longitude: coord.longitude)
            let meters = user.distance(from: eventLoc)
            guard meters <= maxMeters else { return nil }
            return (event, meters / 1609.34)
        }
        .sorted { lhs, rhs in lhs.event.startsAt < rhs.event.startsAt }
    }

    private var upcomingMergedEventsWithDistance: [(event: EventDTO, miles: Double)] {
        var byID: [String: (event: EventDTO, miles: Double)] = [:]
        for item in upcomingEventsWithDistance {
            byID[item.event.id] = item
        }
        for item in communityEventsWithDistance where byID[item.event.id] == nil {
            byID[item.event.id] = item
        }
        return byID.values.sorted { $0.event.startsAt < $1.event.startsAt }
    }

    private var myEvents: [EventDTO] {
        let publicRsvps = (appState.upcomingEvents + appState.communityEvents).filter {
            ["going", "interested"].contains($0.currentUserRsvp ?? "")
        }
        var eventsByID: [String: EventDTO] = [:]
        for event in publicRsvps + squadGoingEvents {
            eventsByID[event.id] = event
        }
        return eventsByID.values.sorted(by: eventListSort)
    }

    var body: some View {
        NavigationStack(path: $navigationPath) {
            VStack(alignment: .leading, spacing: 0) {
                VStack(alignment: .leading, spacing: OttoScreenChrome.stackSpacing) {
                    header
                }
                .padding(.horizontal, OttoScreenChrome.horizontalPadding)
                .padding(.top, OttoScreenChrome.topPadding)
                .padding(.bottom, 8)

                OttoTabbedPager(selectedTab: $selectedTab, mode: .paging) {
                    OttoTabBar(selectedTab: $selectedTab)
                        .padding(.horizontal, OttoScreenChrome.horizontalPadding)
                } content: { tab in
                    content(for: tab)
                        .padding(.top, 8)
                        .padding(.bottom, OttoScreenChrome.bottomPadding)
                        .refreshable {
                            await refresh()
                        }
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
            .background(Color.black.ignoresSafeArea())
            .toolbar(.hidden, for: .navigationBar)
            .onChange(of: isActive) { _, active in
                appState.isEventsScreenActive = active
            }
            .onAppear {
                if isActive {
                    appState.isEventsScreenActive = true
                }
            }
            .task {
                migrateEventDistanceUserDefaultsIfNeeded()
                maybePresentEventsLocationPrimer()
                let c = min(200, max(5, selectedEventDistance))
                if selectedEventDistance != c {
                    selectedEventDistance = c
                }
                await refresh()
                applyPendingEventFocusIfNeeded()
                applyPendingEventsMyEventsFocusIfNeeded()
            }
            .onChange(of: selectedEventDistance) { _, _ in
                Task { await refresh() }
            }
            .onChange(of: locationService.authorizationStatus) { _, status in
                if status == .authorizedWhenInUse || status == .authorizedAlways {
                    pendingEventsLocationPermission = false
                    appState.requestLocationSessionSync()
                } else if pendingEventsLocationPermission && (status == .denied || status == .restricted) {
                    pendingEventsLocationPermission = false
                    withAnimation(.easeInOut(duration: 0.18)) {
                        showEventsLocationDeniedModal = true
                    }
                }
            }
            .onChange(of: appState.pendingEventFocus) { _, _ in
                Task {
                    await refresh()
                    applyPendingEventFocusIfNeeded()
                }
            }
            .onChange(of: appState.pendingEventsMyEventsFocus) { _, _ in
                applyPendingEventsMyEventsFocusIfNeeded()
            }
            .onChange(of: selectedTab) { _, _ in
                navigationPath = []
            }
            .onChange(of: locationService.lastLocation) { _, location in
                if let location {
                    cachedUserLocationForEvents = location
                }
            }
            .onChange(of: locationService.mapLocationDisplayTick) { _, _ in
                if let sample = locationService.latestSample {
                    cachedUserLocationForEvents = sample
                }
            }
            .navigationDestination(for: String.self) { eventID in
                if let event = eventForNavigation(eventID) {
                    EventDetailView(
                        event: event,
                        onEventUpdated: handleMyEventUpdated,
                        onBack: popEventNavigation
                    )
                        .id(eventID)
                        .environmentObject(appState)
                        .environmentObject(locationService)
                } else {
                    VStack(spacing: 20) {
                        UnifiedEmptyStateView(
                            title: "Event unavailable",
                            message: "This event could not be loaded.",
                            systemImage: "calendar.badge.exclamationmark"
                        )
                        Button(action: popEventNavigation) {
                            Label("Back", systemImage: "chevron.left")
                                .font(.headline.weight(.semibold))
                                .foregroundStyle(.white)
                                .padding(.horizontal, 18)
                                .padding(.vertical, 12)
                                .background(Color.white.opacity(0.08))
                                .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                        }
                        .buttonStyle(.plain)
                    }
                    .padding()
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .background(Color.black.ignoresSafeArea())
                }
            }
            .sheet(isPresented: $showCustomDistanceSheet) {
                eventDistanceCustomSheet
            }
            .overlay {
                ZStack {
                    if showEventsLocationPrimer {
                        OttoEducationDialog(
                            allowsUnconfirmedDismiss: false,
                            onDismissUnconfirmed: {},
                            hero: { OttoEducationLocationHero() },
                            title: NSLocalizedString("events_location_primer_title", comment: ""),
                            bodyText: NSLocalizedString("events_location_primer_body", comment: ""),
                            bulletSectionTitle: nil,
                            bullets: [],
                            footer: NSLocalizedString("events_location_primer_footer", comment: ""),
                            primaryTitle: NSLocalizedString("events_location_primer_continue", comment: ""),
                            onPrimary: {
                                requestLocationFromEventsPrimer()
                            },
                            secondaryTitle: NSLocalizedString("events_location_primer_not_now", comment: ""),
                        )
                        .transition(.opacity.combined(with: .scale(scale: 0.96)))
                    }

                    if showEventsLocationDeniedModal {
                        OttoEducationDialog(
                            onDismissUnconfirmed: {
                                withAnimation(.easeInOut(duration: 0.18)) {
                                    showEventsLocationDeniedModal = false
                                }
                            },
                            hero: { OttoEducationLocationHero() },
                            title: NSLocalizedString("events_location_permission_modal_title", comment: ""),
                            bodyText: NSLocalizedString("events_location_permission_modal_body", comment: ""),
                            bulletSectionTitle: nil,
                            bullets: [],
                            footer: nil,
                            primaryTitle: NSLocalizedString("location_permission_enable", comment: ""),
                            onPrimary: {
                                if let url = URL(string: UIApplication.openSettingsURLString) {
                                    UIApplication.shared.open(url)
                                }
                                withAnimation(.easeInOut(duration: 0.18)) {
                                    showEventsLocationDeniedModal = false
                                }
                            },
                            secondaryTitle: NSLocalizedString("location_permission_modal_dismiss", comment: ""),
                        )
                        .transition(.opacity.combined(with: .scale(scale: 0.96)))
                    }
                }
            }
        }
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 12) {
            OttoScreenHeader(title: "Events")
        }
    }

    private var radiusPicker: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Search within")
                .font(.caption.weight(.medium))
                .foregroundStyle(.white.opacity(0.65))

            HStack(spacing: 7) {
                ForEach(eventDistancePresets, id: \.self) { miles in
                    eventDistancePill(
                        title: "\(miles) mi",
                        systemImage: nil,
                        isSelected: !isCustomDistance && clampedEventDistance == miles
                    ) {
                        withAnimation(.spring(response: 0.28, dampingFraction: 0.78)) {
                            selectedEventDistance = miles
                        }
                        selectionHaptic()
                    }
                }
                eventDistancePill(
                    title: "Custom",
                    systemImage: "slider.horizontal.3",
                    isSelected: isCustomDistance
                ) {
                    customDraftMiles = Double(clampedEventDistance)
                    lastCustomSnapHaptic = nil
                    showCustomDistanceSheet = true
                    selectionHaptic()
                }
                .accessibilityLabel("Custom distance")
            }
            .frame(maxWidth: .infinity)
            .padding(.horizontal, 12)
            .padding(.vertical, 10)
            .background(Color.white.opacity(0.045))
            .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 20, style: .continuous)
                    .stroke(Color.white.opacity(0.12), lineWidth: 1)
            )
            .contentShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func eventDistancePill(
        title: String?,
        systemImage: String?,
        isSelected: Bool,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            HStack(spacing: systemImage != nil && title != nil ? 4 : 0) {
                if let systemImage {
                    Image(systemName: systemImage)
                        .font(.subheadline.weight(.semibold))
                }
                if let title {
                    Text(title)
                        .lineLimit(1)
                        .minimumScaleFactor(0.8)
                }
            }
            .font(.subheadline.weight(.semibold))
            .foregroundStyle(isSelected ? Color.white : Color.white.opacity(0.72))
            .frame(maxWidth: .infinity)
            .padding(.horizontal, 6)
            .padding(.vertical, 6)
            .frame(height: 32)
            .background(isSelected ? OttoScreenChrome.accentColor : Color.white.opacity(0.10))
            .clipShape(Capsule())
            .overlay(
                Capsule()
                    .stroke(Color.white.opacity(isSelected ? 0 : 0.08), lineWidth: 1)
            )
            .shadow(
                color: isSelected ? OttoScreenChrome.accentColor.opacity(0.35) : .clear,
                radius: 10
            )
            .scaleEffect(isSelected ? 1.0 : 0.96)
        }
        .buttonStyle(.plain)
        .animation(.spring(response: 0.28, dampingFraction: 0.78), value: isSelected)
    }

    private var eventDistanceCustomSheet: some View {
        NavigationStack {
            VStack(spacing: 28) {
                Text("\(Int(customDraftMiles.rounded())) mi")
                    .font(.system(size: 44, weight: .bold, design: .rounded))
                    .foregroundStyle(.white)
                    .padding(.top, 8)

                Slider(value: $customDraftMiles, in: 5...200, step: 1)
                    .tint(OttoScreenChrome.accentColor)
                    .onChange(of: customDraftMiles) { _, new in
                        handleCustomSliderChange(new)
                    }

                Spacer(minLength: 0)

                Button {
                    applyCustomDistanceFromSheet()
                } label: {
                    Text("Apply")
                        .font(.headline.weight(.semibold))
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 16)
                        .background(OttoScreenChrome.accentColor)
                        .foregroundStyle(.white)
                        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                }
                .padding(.bottom, 8)
            }
            .padding(.horizontal, 24)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(Color.black.ignoresSafeArea())
            .navigationTitle("Distance")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Cancel") {
                        showCustomDistanceSheet = false
                    }
                    .foregroundStyle(.white.opacity(0.85))
                }
            }
            .toolbarBackground(.black, for: .navigationBar)
            .toolbarColorScheme(.dark, for: .navigationBar)
        }
        .presentationDetents([.medium])
        .presentationDragIndicator(.visible)
    }

    private func migrateEventDistanceUserDefaultsIfNeeded() {
        let ud = UserDefaults.standard
        let legacyKey = "eventsSearchRadiusMiles"
        guard ud.object(forKey: legacyKey) != nil else { return }
        let legacy = ud.integer(forKey: legacyKey)
        let migrated = min(200, max(5, legacy == 0 ? 50 : legacy))
        ud.set(migrated, forKey: "selected_event_distance")
        ud.removeObject(forKey: legacyKey)
    }

    private func maybePresentEventsLocationPrimer() {
        switch locationService.authorizationStatus {
        case .notDetermined:
            withAnimation(.spring(response: 0.38, dampingFraction: 0.86)) {
                showEventsLocationPrimer = true
            }
        case .authorizedAlways, .authorizedWhenInUse:
            appState.requestLocationSessionSync()
        case .denied, .restricted:
            break
        @unknown default:
            break
        }
    }

    private func requestLocationFromEventsPrimer() {
        withAnimation(.easeInOut(duration: 0.18)) {
            showEventsLocationPrimer = false
        }
        switch locationService.authorizationStatus {
        case .notDetermined:
            pendingEventsLocationPermission = true
            locationService.requestPermissionIfNeeded()
        case .authorizedAlways, .authorizedWhenInUse:
            pendingEventsLocationPermission = false
            appState.requestLocationSessionSync()
        case .denied, .restricted:
            pendingEventsLocationPermission = false
            withAnimation(.easeInOut(duration: 0.18)) {
                showEventsLocationDeniedModal = true
            }
        @unknown default:
            break
        }
    }

    private func selectionHaptic() {
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
    }

    private func snapCustomMiles(_ raw: Double) -> Double {
        let v = min(200, max(5, raw))
        for p in [25, 50, 100] {
            if abs(v - Double(p)) <= 2 {
                return Double(p)
            }
        }
        return v
    }

    private func handleCustomSliderChange(_ new: Double) {
        let snapped = snapCustomMiles(new)
        if abs(snapped - new) > 0.25 {
            customDraftMiles = snapped
        }
        for p in [25, 50, 100] {
            if abs(customDraftMiles - Double(p)) < 1.15 {
                if lastCustomSnapHaptic != p {
                    lastCustomSnapHaptic = p
                    selectionHaptic()
                }
                return
            }
        }
    }

    private func applyCustomDistanceFromSheet() {
        let v = min(200, max(5, Int(customDraftMiles.rounded())))
        withAnimation(.spring(response: 0.28, dampingFraction: 0.78)) {
            selectedEventDistance = v
        }
        showCustomDistanceSheet = false
        selectionHaptic()
    }

    @ViewBuilder
    private func content(for tab: EventsTab) -> some View {
        switch tab {
        case .upcoming:
            upcomingTabContent
        case .squads:
            squadsEventsContent
        case .mine:
            myEventsContent
        }
    }

    @ViewBuilder
    private var myEventsContent: some View {
        if myEvents.isEmpty {
            UnifiedEmptyStateView(
                title: "No RSVPs Yet",
                message: "Events you’re going to or interested in will appear here.",
                systemImage: "person.crop.rectangle.stack"
            )
            .frame(minHeight: 420)
            .padding(.horizontal, OttoScreenChrome.horizontalPadding)
        } else {
            EventListSectionedList(
                events: sortEventsForSectionedList(myEvents),
                presentation: .compact
            ) { event, groupedInSection in
                eventNavigationButton(for: event, showBanner: false, groupedInSection: groupedInSection)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        }
    }

    @ViewBuilder
    private var squadsEventsContent: some View {
        if appState.circles.isEmpty {
            UnifiedEmptyStateView(
                title: "No Squads",
                message: "Create or join a squad to see squad events.",
                systemImage: "person.3"
            )
            .frame(minHeight: 420)
            .padding(.horizontal, OttoScreenChrome.horizontalPadding)
        } else if isLoadingSquadEvents, allSquadEvents.isEmpty {
            ProgressView()
                .tint(.purple)
                .frame(maxWidth: .infinity)
                .frame(minHeight: 280)
        } else if allSquadEvents.isEmpty {
            if squadEventsErrorMessage != nil {
                UnifiedEmptyStateView(
                    title: String(localized: "fetch_error_squad_events_title"),
                    message: String(localized: "fetch_error_refresh_body"),
                    systemImage: "exclamationmark.triangle",
                    actionTitle: String(localized: "fetch_error_refresh_action"),
                    action: {
                        Task { await refreshAllSquadEvents() }
                    }
                )
                .frame(minHeight: 420)
                .padding(.horizontal, OttoScreenChrome.horizontalPadding)
            } else {
                UnifiedEmptyStateView(
                    title: "No Events",
                    message: "Official squad events appear here when your squads schedule them.",
                    systemImage: "calendar"
                )
                .frame(minHeight: 420)
                .padding(.horizontal, OttoScreenChrome.horizontalPadding)
            }
        } else {
            EventListSectionedList(
                events: sortEventsForSectionedList(allSquadEvents),
                presentation: .compact
            ) { event, groupedInSection in
                eventNavigationButton(
                    for: event,
                    squadName: squadName(for: event),
                    showBanner: false,
                    groupedInSection: groupedInSection
                )
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        }
    }

    private func squadName(for event: EventDTO) -> String? {
        guard appState.circles.count > 1 else { return nil }
        guard let circleID = event.circleId?.trimmingCharacters(in: .whitespacesAndNewlines),
              !circleID.isEmpty else { return nil }
        return appState.circles.first(where: { $0.id == circleID })?.name
    }

    @ViewBuilder
    private var upcomingTabContent: some View {
        switch locationService.authorizationStatus {
        case .denied, .restricted:
            UnifiedEmptyStateView(
                title: "Location Off",
                message:
                    "Allow location access in Settings to see upcoming events with a map pin within \(clampedEventDistance) miles.",
                systemImage: "location.slash"
            )
            .frame(minHeight: 420)
            .padding(.horizontal, OttoScreenChrome.horizontalPadding)
        case .notDetermined:
            UnifiedEmptyStateView(
                title: "Location Needed",
                message:
                    "Driftd uses your location to list events that have coordinates within your chosen distance (straight-line, not driving miles).",
                systemImage: "location"
            )
            .frame(minHeight: 420)
            .padding(.horizontal, OttoScreenChrome.horizontalPadding)
        case .authorizedAlways, .authorizedWhenInUse:
            if userLocationForNearby == nil {
                ProgressView("Finding your location…")
                    .tint(.purple)
                    .foregroundStyle(.white.opacity(0.85))
                    .frame(maxWidth: .infinity)
                    .frame(minHeight: 280)
            } else if appState.featuredEventsFetchFailed,
                      appState.communityEventsFetchFailed,
                      upcomingMergedEventsWithDistance.isEmpty,
                      appState.myCircleInvites.isEmpty {
                UnifiedEmptyStateView(
                    title: String(localized: "fetch_error_events_title"),
                    message: String(localized: "fetch_error_refresh_body"),
                    systemImage: "exclamationmark.triangle",
                    actionTitle: String(localized: "fetch_error_refresh_action"),
                    action: {
                        Task { await refresh() }
                    }
                )
                .frame(minHeight: 420)
                .padding(.horizontal, OttoScreenChrome.horizontalPadding)
            } else if upcomingMergedEventsWithDistance.isEmpty, appState.myCircleInvites.isEmpty {
                UnifiedEmptyStateView(
                    title: String(localized: "events_upcoming_empty_title"),
                    message: String(
                        format: String(localized: "events_upcoming_empty_in_range_format"),
                        clampedEventDistance
                    ),
                    systemImage: "location.magnifyingglass"
                )
                .frame(minHeight: 420)
                .padding(.horizontal, OttoScreenChrome.horizontalPadding)
            } else {
                let distanceByEventID = Dictionary(
                    uniqueKeysWithValues: upcomingMergedEventsWithDistance.map { ($0.event.id, $0.miles) }
                )
                EventListSectionedList(
                    events: upcomingMergedEventsWithDistance.map(\.event),
                    presentation: .featured,
                    hasListHeader: true,
                    showFooter: !appState.myCircleInvites.isEmpty,
                    header: {
                        VStack(alignment: .leading, spacing: 16) {
                            radiusPicker
                            if upcomingMergedEventsWithDistance.isEmpty {
                                UnifiedEmptyStateView(
                                    title: String(localized: "events_upcoming_empty_title"),
                                    message: String(
                                        format: String(localized: "events_upcoming_empty_in_range_format"),
                                        clampedEventDistance
                                    ),
                                    systemImage: "location.magnifyingglass"
                                )
                                .frame(minHeight: 220)
                            }
                        }
                    },
                    footer: {
                        circleInvites
                    }
                ) { event, groupedInSection in
                    eventNavigationButton(
                        for: event,
                        distanceMiles: distanceByEventID[event.id],
                        showBanner: event.eventType != "community",
                        groupedInSection: groupedInSection
                    )
                }
            }
        @unknown default:
            UnifiedEmptyStateView(
                title: "Location Unavailable",
                message: "Allow location access to see nearby events.",
                systemImage: "location"
            )
            .frame(minHeight: 420)
            .padding(.horizontal, OttoScreenChrome.horizontalPadding)
        }
    }

    @ViewBuilder
    private var circleInvites: some View {
        if !appState.myCircleInvites.isEmpty {
            VStack(alignment: .leading, spacing: 12) {
                Text("Squad Invites")
                    .font(.headline)
                    .foregroundStyle(.white)

                ForEach(appState.myCircleInvites) { invite in
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Invited to \(invite.circle?.name ?? "a squad")")
                            .font(.headline)
                            .foregroundStyle(.white)
                        if let by = invite.invitedByUser {
                            Text("\(by.displayName) (@\(by.handle)) invited you")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                        HStack(spacing: 10) {
                            Button("Decline") {
                                Task { await appState.respondToCircleInvite(inviteID: invite.id, accept: false) }
                            }
                            .buttonStyle(.bordered)

                            Button("Accept") {
                                Task { await appState.respondToCircleInvite(inviteID: invite.id, accept: true) }
                            }
                            .buttonStyle(.borderedProminent)
                            .tint(.purple)
                        }
                    }
                    .padding(14)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color.white.opacity(0.05))
                    .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
                }
            }
            .padding(.top, 8)
        }
    }

    @ViewBuilder
    private func eventNavigationButton(
        for event: EventDTO,
        distanceMiles: Double? = nil,
        squadName: String? = nil,
        showBanner: Bool = true,
        goingCountOverride: Int? = nil,
        groupedInSection: Bool = false
    ) -> some View {
        let row = EventRow(
            event: event,
            distanceMiles: distanceMiles,
            squadName: squadName,
            showBanner: showBanner,
            goingCountOverride: goingCountOverride,
            groupedInSection: groupedInSection
        )

        if groupedInSection {
            row
                .onTapGesture {
                    navigationPath.append(event.id)
                }
                .accessibilityAddTraits(.isButton)
        } else {
            NavigationLink(value: event.id) {
                row
            }
            .buttonStyle(.plain)
        }
    }

    private func refresh() async {
        appState.requestLocationSessionSync()
        if let live = locationService.latestSample ?? locationService.lastLocation {
            cachedUserLocationForEvents = live
        }
        await appState.refreshUpcomingEvents()
        await appState.refreshCommunityEvents()
        await appState.refreshMyCircleInvites()
        if appState.circles.isEmpty {
            await appState.refreshCircles()
        }
        await refreshSquadGoingEvents()
        await refreshAllSquadEvents()
    }

    private func applyPendingEventsMyEventsFocusIfNeeded() {
        guard appState.pendingEventsMyEventsFocus != nil else { return }
        _ = appState.consumePendingEventsMyEventsFocus()
        selectedTab = .mine
        navigationPath = []
    }

    private func applyPendingEventFocusIfNeeded() {
        guard let focus = appState.pendingEventFocus else { return }
        if let event = appState.upcomingEvents.first(where: { $0.id == focus.eventRef || $0.slug == focus.eventRef })
            ?? appState.communityEvents.first(where: { $0.id == focus.eventRef || $0.slug == focus.eventRef }) {
            selectedTab = .upcoming
            navigationPath = [event.id]
            _ = appState.consumePendingEventFocus()
        } else {
            Task {
                guard let fetched = try? await APIClient.shared.fetchEvent(eventRef: focus.eventRef) else { return }
                if !appState.upcomingEvents.contains(where: { $0.id == fetched.id })
                    && !appState.communityEvents.contains(where: { $0.id == fetched.id }) {
                    appState.upsertUpcomingEvent(fetched)
                }
                selectedTab = .upcoming
                navigationPath = [fetched.id]
                _ = appState.consumePendingEventFocus()
            }
        }
    }

    private func refreshSquadGoingEvents() async {
        let circleIDs = appState.circles.map(\.id)
        guard !circleIDs.isEmpty else {
            squadGoingEvents = []
            return
        }

        let now = Date()
        var eventsByID: [String: EventDTO] = [:]
        for circleID in circleIDs {
            do {
                let events = try await APIClient.shared.fetchEvents(
                    scope: "all",
                    limit: 100,
                    visibility: "circle",
                    circleId: circleID
                )
                for event in events where event.currentUserRsvp == "going" && event.eventCheckInWindowEnd >= now {
                    eventsByID[event.id] = event
                }
            } catch {
                continue
            }
        }
        squadGoingEvents = eventsByID.values.sorted(by: eventListSort)
    }

    private func refreshAllSquadEvents() async {
        let circleIDs = appState.circles.map(\.id)
        guard !circleIDs.isEmpty else {
            allSquadEvents = []
            squadEventsErrorMessage = nil
            return
        }

        isLoadingSquadEvents = true
        squadEventsErrorMessage = nil
        defer { isLoadingSquadEvents = false }

        let now = Date()
        var eventsByID: [String: EventDTO] = [:]
        var sawFailure = false
        for circleID in circleIDs {
            do {
                let events = try await APIClient.shared.fetchEvents(
                    scope: "all",
                    limit: 100,
                    visibility: "official",
                    circleId: circleID
                )
                for event in events where event.eventCheckInWindowEnd >= now {
                    eventsByID[event.id] = event
                }
            } catch is CancellationError {
                return
            } catch let urlError as URLError where urlError.code == .cancelled {
                return
            } catch {
                sawFailure = true
                continue
            }
        }
        allSquadEvents = eventsByID.values.sorted(by: eventListSort)
        if allSquadEvents.isEmpty, sawFailure {
            squadEventsErrorMessage = "Couldn't load squad events."
        }
    }

    private func eventForNavigation(_ eventID: String) -> EventDTO? {
        myEvents.first(where: { $0.id == eventID || $0.slug == eventID })
            ?? appState.upcomingEvents.first(where: { $0.id == eventID || $0.slug == eventID })
            ?? appState.communityEvents.first(where: { $0.id == eventID || $0.slug == eventID })
            ?? allSquadEvents.first(where: { $0.id == eventID || $0.slug == eventID })
            ?? squadGoingEvents.first(where: { $0.id == eventID || $0.slug == eventID })
    }

    private func popEventNavigation() {
        guard !navigationPath.isEmpty else { return }
        navigationPath.removeLast()
    }

    private func handleMyEventUpdated(_ updated: EventDTO) {
        upsertAllSquadEvent(updated)
        if updated.visibility == "circle" {
            if updated.currentUserRsvp == "going", updated.eventCheckInWindowEnd >= Date() {
                upsertSquadGoingEvent(updated)
            } else {
                squadGoingEvents.removeAll { $0.id == updated.id }
            }
        }
    }

    private func upsertSquadGoingEvent(_ event: EventDTO) {
        if let index = squadGoingEvents.firstIndex(where: { $0.id == event.id }) {
            squadGoingEvents[index] = event
        } else {
            squadGoingEvents.append(event)
        }
        squadGoingEvents.sort(by: eventListSort)
    }

    private func upsertAllSquadEvent(_ event: EventDTO) {
        guard event.eventCheckInWindowEnd >= Date() else {
            allSquadEvents.removeAll { $0.id == event.id }
            return
        }
        if let index = allSquadEvents.firstIndex(where: { $0.id == event.id }) {
            allSquadEvents[index] = event
        } else if event.visibility == "circle" || event.circleId != nil {
            allSquadEvents.append(event)
        }
        allSquadEvents.sort(by: eventListSort)
    }

    private func eventListSort(_ lhs: EventDTO, _ rhs: EventDTO) -> Bool {
        let now = Date()
        let lhsStarted = lhs.startsAt <= now
        let rhsStarted = rhs.startsAt <= now
        if lhsStarted != rhsStarted { return lhsStarted }
        if lhsStarted && rhsStarted { return lhs.startsAt > rhs.startsAt }
        return lhs.startsAt < rhs.startsAt
    }
}

/// Snapshot for `.sheet(item:)` so edit UI doesn’t rely on `currentEvent` recomputation while the sheet attaches.
private struct SquadEventEditPresentation: Identifiable {
    let id: UUID
    let event: EventDTO
    let circleName: String

    init(snapshotEvent: EventDTO, circleName: String) {
        id = UUID()
        event = snapshotEvent
        self.circleName = circleName
    }
}

/// Makes raw URLs and email addresses in event descriptions tappable (opens Safari / Mail).
private enum EventDetailDescriptionLinks {
    /// Avoid multi‑second main‑thread scans / AttributeGraph churn on pathological descriptions.
    private static let maxCharactersForLinkDetection = 48_000

    struct Match: Comparable {
        let range: Range<String.Index>
        let url: URL

        static func < (lhs: Match, rhs: Match) -> Bool {
            lhs.range.lowerBound < rhs.range.lowerBound
        }
    }

    static func attributedString(for plain: String) -> AttributedString {
        guard !plain.isEmpty else { return AttributedString() }
        if plain.count > maxCharactersForLinkDetection {
            var plainAttr = AttributedString(plain)
            plainAttr.foregroundColor = Color.white.opacity(0.72)
            plainAttr.font = .system(size: 15)
            return plainAttr
        }

        var matches: [Match] = []
        let nsString = plain as NSString
        let fullNsRange = NSRange(location: 0, length: nsString.length)

        if let detector = try? NSDataDetector(types: NSTextCheckingResult.CheckingType.link.rawValue) {
            detector.enumerateMatches(in: plain, options: [], range: fullNsRange) { result, _, _ in
                guard let result, let url = result.url,
                      let range = Range(result.range, in: plain) else { return }
                matches.append(Match(range: range, url: url))
            }
        }

        let emailPattern = "[A-Z0-9a-z._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"
        if let emailRegex = try? NSRegularExpression(pattern: emailPattern, options: .caseInsensitive) {
            emailRegex.enumerateMatches(in: plain, options: [], range: fullNsRange) { result, _, _ in
                guard let result, let range = Range(result.range, in: plain) else { return }
                let token = String(plain[range])
                guard let mailURL = URL(string: "mailto:\(token)") else { return }
                let insideLink = matches.contains { urlMatch in
                    urlMatch.range.lowerBound <= range.lowerBound && range.upperBound <= urlMatch.range.upperBound
                }
                if insideLink { return }
                matches.append(Match(range: range, url: mailURL))
            }
        }

        let merged = mergeNonOverlapping(matches.sorted())
        if merged.isEmpty {
            var plainAttr = AttributedString(plain)
            plainAttr.foregroundColor = Color.white.opacity(0.72)
            plainAttr.font = .system(size: 15)
            return plainAttr
        }

        var result = AttributedString()
        var cursor = plain.startIndex
        for match in merged.sorted(by: { $0.range.lowerBound < $1.range.lowerBound }) {
            if cursor < match.range.lowerBound {
                var segment = AttributedString(String(plain[cursor..<match.range.lowerBound]))
                segment.foregroundColor = Color.white.opacity(0.72)
                segment.font = .system(size: 15)
                result.append(segment)
            }
            var linkSegment = AttributedString(String(plain[match.range]))
            linkSegment.foregroundColor = Color.purple
            linkSegment.font = .system(size: 15)
            linkSegment.link = match.url
            linkSegment.underlineStyle = .single
            result.append(linkSegment)
            cursor = match.range.upperBound
        }
        if cursor < plain.endIndex {
            var tail = AttributedString(String(plain[cursor...]))
            tail.foregroundColor = Color.white.opacity(0.72)
            tail.font = .system(size: 15)
            result.append(tail)
        }
        return result
    }

    /// Keeps earlier matches when ranges overlap (URLs win over duplicate detection).
    private static func mergeNonOverlapping(_ sorted: [Match]) -> [Match] {
        var output: [Match] = []
        for candidate in sorted {
            let overlaps = output.contains { existing in existing.range.overlaps(candidate.range) }
            if overlaps { continue }
            output.append(candidate)
        }
        return output
    }
}

/// Linkification runs `NSDataDetector` / regex; wrapping it in an `Equatable` view avoids redoing that work on every
/// layout pass while SwiftUI measures RSVP rows and other siblings (which can correlate with traps under load).
private struct EquatableLinkifiedEventDescription: View, Equatable {
    let plain: String
    let lineLimit: Int?

    static func == (lhs: Self, rhs: Self) -> Bool {
        lhs.plain == rhs.plain && lhs.lineLimit == rhs.lineLimit
    }

    var body: some View {
        Text(EventDetailDescriptionLinks.attributedString(for: plain))
            .lineSpacing(3)
            .multilineTextAlignment(.leading)
            .lineLimit(lineLimit)
            .truncationMode(.tail)
            .fixedSize(horizontal: false, vertical: true)
            .frame(maxWidth: .infinity, alignment: .leading)
    }
}

struct EventDetailView: View {
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject private var appState: AppState
    @EnvironmentObject private var locationService: LocationService
    let event: EventDTO
    var sourceCircleID: String? = nil
    var onEventUpdated: ((EventDTO) -> Void)? = nil
    var onEventDeleted: ((String) -> Void)? = nil
    var onBack: (() -> Void)? = nil
    /// Called after a successful squad chat post (e.g. dismiss squad Events event sheet).
    var onPostedToChat: (() -> Void)? = nil
    @State private var isShowingShareSquadActionsSheet = false
    @State private var isShowingSharedWithSheet = false
    @State private var isShowingGoingUsersSheet = false
    @State private var squadEventEditPresentation: SquadEventEditPresentation?
    @State private var localEventOverride: EventDTO?
    @State private var checkInInFlight = false
    @State private var isDescriptionExpanded = false
    @State private var showEventDetailLocationPrimer = false
    @State private var pendingEventDetailCheckInAfterLocation = false
    @State private var showEventDetailLocationDeniedModal = false

    private static let descriptionPlaceholder = "Event details will be posted soon."
    /// Matches Android collapsed body (`maxLines ≈ 6` + overflow / min-length heuristics).
    private static let descriptionCollapsedLineLimit = 6
    private static let descriptionReadMoreMinCharacterCount = 220

    private static let checkInRadiusMeters: Double = 150

    fileprivate enum RsvpChoice: String, CaseIterable {
        case going
        case maybe = "interested"
        case notGoing = "not_going"

        var title: String {
            switch self {
            case .going: return "Going"
            case .maybe: return "Maybe"
            case .notGoing: return "Not Going"
            }
        }

        var systemImage: String {
            switch self {
            case .going: return "checkmark.circle.fill"
            case .maybe: return "questionmark.circle"
            case .notGoing: return "xmark.circle"
            }
        }

        var sortOrder: Int {
            switch self {
            case .going: return 0
            case .notGoing: return 1
            case .maybe: return 2
            }
        }
    }

    private var currentEvent: EventDTO {
        if let localEventOverride {
            return localEventOverride
        }
        guard
            let cached =
                appState.upcomingEvents.first(where: { $0.id == event.id })
                ?? appState.communityEvents.first(where: { $0.id == event.id })
        else {
            return event
        }
        let eventRsvpCount = event.contactsRsvps?.count ?? event.contactsGoing.count
        let cachedRsvpCount = cached.contactsRsvps?.count ?? cached.contactsGoing.count
        return eventRsvpCount > cachedRsvpCount ? event : cached
    }

    private enum ScheduleFormatters {
        static let month: DateFormatter = {
            let f = DateFormatter()
            f.locale = .autoupdatingCurrent
            f.timeZone = .autoupdatingCurrent
            f.calendar = .autoupdatingCurrent
            f.setLocalizedDateFormatFromTemplate("MMM")
            return f
        }()

        static let day: DateFormatter = {
            let f = DateFormatter()
            f.locale = .autoupdatingCurrent
            f.timeZone = .autoupdatingCurrent
            f.calendar = .autoupdatingCurrent
            f.dateFormat = "dd"
            return f
        }()

        static let fullDate: DateFormatter = {
            let f = DateFormatter()
            f.locale = .autoupdatingCurrent
            f.timeZone = .autoupdatingCurrent
            f.calendar = .autoupdatingCurrent
            f.dateStyle = .full
            f.timeStyle = .none
            return f
        }()

        static let shortTime: DateFormatter = {
            let f = DateFormatter()
            f.locale = .autoupdatingCurrent
            f.timeZone = .autoupdatingCurrent
            f.calendar = .autoupdatingCurrent
            f.dateStyle = .none
            f.timeStyle = .short
            return f
        }()
    }

    /// Rejects non-finite dates and absurd calendar values (e.g. `Date.distantFuture`) that can crash formatters.
    private var safeEventStartDate: Date? {
        let d = currentEvent.startsAt
        let ti = d.timeIntervalSinceReferenceDate
        guard ti.isFinite, !ti.isNaN else { return nil }
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = .autoupdatingCurrent
        guard let year = cal.dateComponents([.year], from: d).year else { return nil }
        guard (1970...2100).contains(year) else { return nil }
        return d
    }

    private var isGoing: Bool {
        currentEvent.currentUserRsvp == "going"
    }

    private var currentRsvpChoice: RsvpChoice? {
        guard let raw = currentEvent.currentUserRsvp else { return nil }
        return RsvpChoice(rawValue: raw)
    }

    private var goingCount: Int {
        rsvpCount(for: .going)
    }

    private var rsvpRows: [(choice: RsvpChoice, users: [UserDTO], count: Int)] {
        [RsvpChoice.going, RsvpChoice.notGoing, RsvpChoice.maybe].map { choice in
            let users = rsvpUsers(for: choice)
            return (choice, users, rsvpCount(for: choice, cachedUsers: users))
        }
    }

    private var allVisibleRsvpUsers: [EventRsvpUserEntry] {
        rsvpRows.flatMap { row in row.users.map { EventRsvpUserEntry(choice: row.choice, user: $0) } }
    }

    private var isCheckedIn: Bool {
        currentEvent.currentUserCheckIn != nil
    }

    private var isInCheckInWindow: Bool {
        currentEvent.isInEventCheckInWindow
    }

    /// After check-in window end, RSVP choices are read-only (matches Android / product).
    private var isRsvpInteractionEnabled: Bool {
        Date() <= currentEvent.eventCheckInWindowEnd
    }

    private var eventHasGeo: Bool {
        currentEvent.geoCoordinate != nil
    }

    private var isSquadEvent: Bool {
        currentEvent.visibility == "circle"
    }

    private var eventCircle: DriveCircle? {
        guard let circleID = currentEvent.circleId ?? sourceCircleID else { return nil }
        return appState.circles.first(where: { $0.id == circleID })
    }

    private var canEditSquadEvent: Bool {
        guard isSquadEvent, let circle = eventCircle else { return false }
        let myID = appState.currentUserID
        guard !myID.isEmpty else { return false }
        if currentEvent.createdByUserId == myID || circle.ownerId == myID {
            return true
        }
        let role = circle.members.first(where: { $0.id == myID })?.clubRole.lowercased()
        return role == "admin" || role == "owner"
    }

    private var directChatCircleID: String? {
        if isSquadEvent {
            return currentEvent.circleId
        }
        return sourceCircleID
    }

    private var squadScopeCircleID: String? {
        if let sourceCircleID, !sourceCircleID.isEmpty {
            return sourceCircleID
        }
        if isSquadEvent, let circleId = currentEvent.circleId, !circleId.isEmpty {
            return circleId
        }
        return nil
    }

    private var isSquadScopedDetail: Bool {
        squadScopeCircleID != nil
    }

    private var squadScopeMemberIDs: Set<String>? {
        guard let squadScopeCircleID else { return nil }
        let members = appState.circles.first(where: { $0.id == squadScopeCircleID })?.members ?? []
        return Set(members.map { normalizedUserID($0.id) }.filter { !$0.isEmpty })
    }

    private func normalizedUserID(_ raw: String) -> String {
        raw.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private var crewUsers: [UserDTO] {
        rsvpUsers(for: .going)
    }

    private func rsvpUsers(for choice: RsvpChoice) -> [UserDTO] {
        let event = currentEvent
        let memberScope = squadScopeMemberIDs
        var seenIDs = Set<String>()
        var users: [UserDTO] = []

        if let explicit = event.contactsRsvps, !explicit.isEmpty {
            for rsvp in explicit {
                guard rsvp.status == choice.rawValue else { continue }
                let uid = normalizedUserID(rsvp.user.id)
                guard !uid.isEmpty else { continue }
                guard !seenIDs.contains(uid) else { continue }
                if let memberScope, !memberScope.contains(uid) { continue }
                seenIDs.insert(uid)
                users.append(rsvp.user)
            }
        } else if choice == .going {
            for contact in event.contactsGoing {
                let uid = normalizedUserID(contact.id)
                guard !uid.isEmpty else { continue }
                guard !seenIDs.contains(uid) else { continue }
                if let memberScope, !memberScope.contains(uid) { continue }
                seenIDs.insert(uid)
                users.append(contact)
            }
        }

        let myID = normalizedUserID(appState.currentUserID)
        if event.currentUserRsvp == choice.rawValue,
           !myID.isEmpty,
           let currentUser = appState.allUsers.first(where: { normalizedUserID($0.id) == myID }),
           memberScope?.contains(myID) ?? true,
           !users.contains(where: { normalizedUserID($0.id) == myID }) {
            users.insert(currentUser, at: 0)
        }
        return users
    }

    private func rsvpCount(for choice: RsvpChoice, cachedUsers: [UserDTO]? = nil) -> Int {
        let users = cachedUsers ?? rsvpUsers(for: choice)
        return users.count
    }

    private var emptyRsvpText: String {
        "None of your squad members yet."
    }

    private var monthText: String {
        guard let start = safeEventStartDate else { return "—" }
        return ScheduleFormatters.month.string(from: start).uppercased()
    }

    private var dayText: String {
        guard let start = safeEventStartDate else { return "—" }
        return ScheduleFormatters.day.string(from: start)
    }

    private var dateText: String {
        guard let start = safeEventStartDate else { return "Date TBD" }
        return ScheduleFormatters.fullDate.string(from: start)
    }

    private var timeText: String {
        guard let start = safeEventStartDate else { return "—" }
        return ScheduleFormatters.shortTime.string(from: start)
    }

    private var locationText: String {
        if let label = currentEvent.address?.label, !label.isEmpty { return label }
        let cityRegion = [currentEvent.address?.city, currentEvent.address?.region]
            .compactMap { $0?.isEmpty == false ? $0 : nil }
            .joined(separator: ", ")
        return cityRegion.isEmpty ? "Location TBD" : cityRegion
    }

    private var eventCoordinate: CLLocationCoordinate2D? {
        currentEvent.geoCoordinate
    }

    private func distanceToEventMeters() -> Double? {
        guard let ec = currentEvent.geoCoordinate else { return nil }
        guard let loc = locationService.latestSample ?? locationService.lastLocation else { return nil }
        guard Date().timeIntervalSince(loc.timestamp) <= 30 else { return nil }
        return Self.haversineMeters(
            lat1: loc.coordinate.latitude,
            lon1: loc.coordinate.longitude,
            lat2: ec.latitude,
            lon2: ec.longitude
        )
    }

    private var shouldShowManualCheckIn: Bool {
        guard isGoing, !isCheckedIn, isInCheckInWindow, eventHasGeo else { return false }
        switch locationService.authorizationStatus {
        case .authorizedAlways, .authorizedWhenInUse:
            return (distanceToEventMeters() ?? .greatestFiniteMagnitude) <= Self.checkInRadiusMeters
        default:
            return false
        }
    }

    private var shouldShowCheckInSection: Bool {
        isGoing && (isInCheckInWindow || isCheckedIn)
    }

    private static func haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double) -> Double {
        let earthRadiusMeters = 6_371_000.0
        let r1 = lat1 * Double.pi / 180
        let r2 = lat2 * Double.pi / 180
        let dLat = (lat2 - lat1) * Double.pi / 180
        let dLon = (lon2 - lon1) * Double.pi / 180
        let a =
            sin(dLat / 2) * sin(dLat / 2) +
            cos(r1) * cos(r2) * sin(dLon / 2) * sin(dLon / 2)
        let c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadiusMeters * c
    }

    private var canOpenLocationInMaps: Bool {
        eventCoordinate != nil || locationText != "Location TBD"
    }

    private func openEventOnMap() {
        guard let coord = eventCoordinate else { return }
        appState.requestMapTabCenteredOn(
            latitude: coord.latitude,
            longitude: coord.longitude,
            eventID: currentEvent.id,
            eventPreview: currentEvent
        )
        navigateBack()
    }

    private func openLocationInMaps() {
        guard canOpenLocationInMaps else { return }
        if let coord = eventCoordinate {
            let location = CLLocation(latitude: coord.latitude, longitude: coord.longitude)
            let address: MKAddress? =
                if locationText != "Location TBD" {
                    MKAddress(fullAddress: locationText, shortAddress: nil)
                } else {
                    nil
                }
            let mapItem = MKMapItem(location: location, address: address)
            mapItem.name = locationText == "Location TBD" ? currentEvent.name : locationText
            mapItem.openInMaps(launchOptions: nil)
            return
        }
        guard let encoded = locationText.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) else { return }
        guard let url = URL(string: "maps://?q=\(encoded)") else { return }
        UIApplication.shared.open(url)
    }

    var body: some View {
        ZStack(alignment: .bottom) {
            VStack(spacing: 0) {
                topBar
                    .padding(.horizontal, 18)
                    .padding(.top, 12)

                ScrollView(.vertical) {
                    VStack(alignment: .leading, spacing: 18) {
                        heroHeader
                        sharedWithSection
                        banner
                        descriptionSection
                        factsCard
                        mapPreview
                        checkInSection
                        crewSection
                    }
                    .frame(minWidth: 0, maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 18)
                    .padding(.top, 18)
                    .padding(.bottom, 110)
                }
                .scrollBounceBehavior(.basedOnSize, axes: .vertical)
                .scrollClipDisabled(false)
                .clipped()
                .scrollDismissesKeyboard(.interactively)
                .onChange(of: currentEvent.id) { _, _ in
                    isDescriptionExpanded = false
                }
            }
            .frame(minWidth: 0, maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
            .clipped()

            actionBar
                .frame(maxWidth: .infinity)
        }
        .frame(minWidth: 0, maxWidth: .infinity, maxHeight: .infinity)
        .clipped()
        .background(Color.black.ignoresSafeArea())
        .toolbar(.hidden, for: .navigationBar)
        .onAppear {
            syncEventDetailCheckInWatch()
        }
        .onDisappear {
            appState.requestLocationSessionSync()
        }
        .onChange(of: currentEvent.id) { _, _ in
            syncEventDetailCheckInWatch()
        }
        .onChange(of: currentEvent.currentUserRsvp) { _, _ in
            syncEventDetailCheckInWatch()
        }
        .onChange(of: currentEvent.currentUserCheckIn?.id) { _, _ in
            syncEventDetailCheckInWatch()
        }
        .onChange(of: locationService.mapLocationDisplayTick) { _, _ in
            Task { await appState.attemptForegroundAutoCheckInIfNeeded(locationService: locationService) }
        }
        .onChange(of: locationService.authorizationStatus) { _, status in
            guard pendingEventDetailCheckInAfterLocation else { return }
            switch status {
            case .authorizedAlways, .authorizedWhenInUse:
                pendingEventDetailCheckInAfterLocation = false
                appState.requestLocationSessionSync()
                Task { await performManualCheckIn() }
            case .denied, .restricted:
                pendingEventDetailCheckInAfterLocation = false
                withAnimation(.easeInOut(duration: 0.18)) {
                    showEventDetailLocationDeniedModal = true
                }
            default:
                break
            }
        }
        .sheet(isPresented: $isShowingShareSquadActionsSheet) {
            EventShareSquadActionsSheet(
                event: currentEvent,
                lockedCircleID: directChatCircleID,
                onAssociationsSaved: { squads in
                    let updated = EventDTO(
                        id: currentEvent.id,
                        slug: currentEvent.slug,
                        visibility: currentEvent.visibility,
                        circleId: currentEvent.circleId,
                        createdByUserId: currentEvent.createdByUserId,
                        name: currentEvent.name,
                        description: currentEvent.description,
                        startsAt: currentEvent.startsAt,
                        endsAt: currentEvent.endsAt,
                        address: currentEvent.address,
                        location: currentEvent.location,
                        bannerImage: currentEvent.bannerImage,
                        rsvpCounts: currentEvent.rsvpCounts,
                        contactsGoing: currentEvent.contactsGoing,
                        contactsRsvps: currentEvent.contactsRsvps,
                        currentUserRsvp: currentEvent.currentUserRsvp,
                        currentUserCheckIn: currentEvent.currentUserCheckIn,
                        attachedSquads: squads,
                        isOfficialForCircle: currentEvent.isOfficialForCircle
                    )
                    localEventOverride = updated
                    onEventUpdated?(updated)
                    appState.upsertUpcomingEvent(updated)
                },
                onPostedToChat: {
                    isShowingShareSquadActionsSheet = false
                    onPostedToChat?()
                }
            )
            .environmentObject(appState)
        }
        .sheet(isPresented: $isShowingSharedWithSheet) {
            EventSharedWithSheet(
                squads: currentEvent.attachedSquads,
                circles: appState.circles
            )
        }
        .sheet(isPresented: $isShowingGoingUsersSheet) {
            EventRsvpUsersSheet(entries: allVisibleRsvpUsers)
                .environmentObject(appState)
        }
        .sheet(item: $squadEventEditPresentation) { presentation in
            let editEventID = presentation.event.id
            AddSquadEventSheet(
                circleName: presentation.circleName,
                event: presentation.event,
                onDelete: {
                    try await SquadEventSaveCoordinator.deleteEditedEvent(
                        appState: appState,
                        eventID: editEventID,
                        onDeleted: onEventDeleted
                    )
                    dismiss()
                },
                onSave: { payload, completion in
                    Task { @MainActor in
                        do {
                            try await SquadEventSaveCoordinator.applyEditedEvent(
                                appState: appState,
                                eventID: editEventID,
                                payload: payload,
                                onEventUpdated: onEventUpdated
                            )
                            completion(.success(.updated))
                        } catch {
                            completion(.failure(error))
                        }
                    }
                }
            )
            .environmentObject(appState)
        }
        .overlay {
            ZStack {
                if showEventDetailLocationPrimer {
                    OttoEducationDialog(
                        allowsUnconfirmedDismiss: false,
                        onDismissUnconfirmed: {},
                        hero: { OttoEducationLocationHero() },
                        title: NSLocalizedString("events_location_primer_title", comment: ""),
                        bodyText: NSLocalizedString("events_location_primer_body", comment: ""),
                        bulletSectionTitle: nil,
                        bullets: [],
                        footer: NSLocalizedString("events_location_primer_footer", comment: ""),
                        primaryTitle: NSLocalizedString("events_location_primer_continue", comment: ""),
                        onPrimary: { requestLocationForEventDetailCheckIn() },
                        secondaryTitle: NSLocalizedString("events_location_primer_not_now", comment: ""),
                    )
                    .transition(.opacity.combined(with: .scale(scale: 0.96)))
                }

                if showEventDetailLocationDeniedModal {
                    OttoEducationDialog(
                        onDismissUnconfirmed: {
                            withAnimation(.easeInOut(duration: 0.18)) {
                                showEventDetailLocationDeniedModal = false
                            }
                        },
                        hero: { OttoEducationLocationHero() },
                        title: NSLocalizedString("events_location_permission_modal_title", comment: ""),
                        bodyText: NSLocalizedString("events_location_permission_modal_body", comment: ""),
                        bulletSectionTitle: nil,
                        bullets: [],
                        footer: nil,
                        primaryTitle: NSLocalizedString("location_permission_enable", comment: ""),
                        onPrimary: {
                            if let url = URL(string: UIApplication.openSettingsURLString) {
                                UIApplication.shared.open(url)
                            }
                            withAnimation(.easeInOut(duration: 0.18)) {
                                showEventDetailLocationDeniedModal = false
                            }
                        },
                        secondaryTitle: NSLocalizedString("location_permission_modal_dismiss", comment: ""),
                    )
                    .transition(.opacity.combined(with: .scale(scale: 0.96)))
                }
            }
        }
    }

    private func syncEventDetailCheckInWatch() {
        appState.watchEventDetailForCheckIn(currentEvent, locationService: locationService)
        appState.requestLocationSessionSync()
        Task { await appState.attemptForegroundAutoCheckInIfNeeded(locationService: locationService) }
    }

    private func navigateBack() {
        if let onBack {
            onBack()
        } else {
            dismiss()
        }
    }

    private var topBar: some View {
        HStack {
            Button(action: navigateBack) {
                Image(systemName: "chevron.left")
                    .font(.system(size: 17, weight: .bold))
                    .foregroundStyle(.white)
                    .frame(width: 38, height: 38)
                    .background(Color.white.opacity(0.06))
                    .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
            }
            Spacer()
            if canEditSquadEvent {
                Button {
                    squadEventEditPresentation = SquadEventEditPresentation(
                        snapshotEvent: currentEvent,
                        circleName: eventCircle?.name ?? "Squad"
                    )
                } label: {
                    Image(systemName: "pencil")
                        .font(.system(size: 17, weight: .bold))
                        .foregroundStyle(.white)
                        .frame(width: 38, height: 38)
                        .background(Color.white.opacity(0.06))
                        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                }
            }
            Button {
                isShowingShareSquadActionsSheet = true
            } label: {
                OttoGlassIconButtonLabel(
                    systemImage: "square.and.arrow.up",
                    size: CGSize(width: 38, height: 38),
                    cornerRadius: 12,
                    font: .system(size: 17, weight: .bold)
                )
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Share and squad actions")
        }
    }

    @ViewBuilder
    private var sharedWithSection: some View {
        if !currentEvent.attachedSquads.isEmpty {
            EventSharedWithModule(
                squads: currentEvent.attachedSquads,
                circles: appState.circles
            ) {
                isShowingSharedWithSheet = true
            }
        }
    }

    private var heroHeader: some View {
        HStack(alignment: .center, spacing: 18) {
            dateBadge
            VStack(alignment: .leading, spacing: 6) {
                Text(currentEvent.name)
                    .font(.system(size: 25, weight: .bold, design: .rounded))
                    .foregroundStyle(.white)
                    .lineLimit(2)
                    .fixedSize(horizontal: false, vertical: true)

                Label(locationText, systemImage: "mappin")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .lineLimit(2)
                    .fixedSize(horizontal: false, vertical: true)

                HStack(spacing: 8) {
                    pill(icon: "person.2", text: "\(goingCount) going", color: .purple)
                }
            }
            .frame(minWidth: 0, maxWidth: .infinity, alignment: .leading)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    @ViewBuilder
    private var banner: some View {
        if let urlString = currentEvent.bannerImage?.url, !urlString.isEmpty {
            EventBannerImage(urlString: urlString)
                .frame(height: 158)
                .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                .overlay {
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .stroke(Color.white.opacity(0.14), lineWidth: 1)
                }
        }
    }

    private var normalizedDescriptionDisplayText: String {
        if let raw = currentEvent.description?.trimmingCharacters(in: .whitespacesAndNewlines),
           !raw.isEmpty {
            return raw
                .replacingOccurrences(of: "\r\n", with: "\n")
                .replacingOccurrences(of: "\r", with: "\n")
        }
        return Self.descriptionPlaceholder
    }

    private func descriptionShowsReadMoreControl(for text: String) -> Bool {
        if text == Self.descriptionPlaceholder { return false }
        if text.count >= Self.descriptionReadMoreMinCharacterCount { return true }
        return text.components(separatedBy: "\n").count > Self.descriptionCollapsedLineLimit
    }

    private var descriptionSection: some View {
        let bodyText = normalizedDescriptionDisplayText
        let showToggle = descriptionShowsReadMoreControl(for: bodyText)
        let lineCap: Int? = {
            guard showToggle else { return nil }
            return isDescriptionExpanded ? nil : Self.descriptionCollapsedLineLimit
        }()

        return VStack(alignment: .leading, spacing: 6) {
            EquatableLinkifiedEventDescription(plain: bodyText, lineLimit: lineCap)
                .equatable()

            if showToggle {
                Button {
                    withAnimation(.easeInOut(duration: 0.2)) {
                        isDescriptionExpanded.toggle()
                    }
                } label: {
                    Text(isDescriptionExpanded ? "Show less" : "Read more")
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundStyle(Color.purple)
                }
                .buttonStyle(.plain)
            }
        }
    }

    private var factsCard: some View {
        VStack(spacing: 0) {
            factRow(icon: "calendar", title: "Date", value: dateText)
            divider
            factRow(icon: "clock", title: "Time", value: timeText)
            divider
            Group {
                if canOpenLocationInMaps {
                    Button {
                        openLocationInMaps()
                    } label: {
                        factRow(icon: "mappin.and.ellipse", title: "Location", value: locationText, chevron: true)
                    }
                    .buttonStyle(.plain)
                } else {
                    factRow(icon: "mappin.and.ellipse", title: "Location", value: locationText, chevron: false)
                }
            }
        }
        .padding(.horizontal, 16)
        .background(Color.white.opacity(0.045))
        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .stroke(Color.white.opacity(0.08), lineWidth: 1)
        }
    }

    @ViewBuilder
    private var mapPreview: some View {
        if let eventCoordinate {
            ZStack {
                OttoMapboxEventPreview(coordinate: eventCoordinate, title: locationText)
                    .frame(minWidth: 0, maxWidth: .infinity, maxHeight: .infinity)
                    .allowsHitTesting(false)
                Color.clear
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .contentShape(Rectangle())
                    .accessibilityLabel("Show on map")
                    .accessibilityAddTraits(.isButton)
                    .onTapGesture {
                        openEventOnMap()
                    }
            }
            .frame(minWidth: 0, maxWidth: .infinity)
            .frame(height: 165)
            .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
            .clipped()
            .overlay {
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .stroke(Color.white.opacity(0.12), lineWidth: 1)
            }
        }
    }

    private var crewSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text("RSVPs")
                    .font(.headline)
                    .foregroundStyle(.white)
                Spacer()
                if !allVisibleRsvpUsers.isEmpty {
                    Button("View all") {
                        isShowingGoingUsersSheet = true
                    }
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.purple)
                }
            }

            VStack(alignment: .leading, spacing: 16) {
                ForEach([RsvpChoice.going, RsvpChoice.notGoing, RsvpChoice.maybe], id: \.rawValue) { choice in
                    let users = rsvpUsers(for: choice)
                    VStack(alignment: .leading, spacing: 8) {
                        HStack(spacing: 6) {
                            Text(choice.title)
                                .font(.subheadline.weight(.semibold))
                                .foregroundStyle(.white)
                            Text("(\(rsvpCount(for: choice, cachedUsers: users)))")
                                .font(.subheadline.weight(.semibold))
                                .foregroundStyle(.secondary)
                        }

                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 14) {
                                ForEach(Array(users.prefix(8).enumerated()), id: \.offset) { _, contact in
                                    AvatarView(
                                        name: contact.displayName,
                                        avatarUrl: contact.avatarUrl,
                                        size: 54,
                                        accentColor: MapAccentPalette.resolvedColor(mapAccentKey: contact.mapAccentKey, userId: contact.id)
                                    )
                                    .overlay(alignment: .bottomTrailing) {
                                        Circle()
                                            .fill(appState.avatarPresenceDotColor(forUserID: contact.id))
                                            .frame(width: 11, height: 11)
                                            .overlay(Circle().stroke(Color.black, lineWidth: 2))
                                    }
                                }

                                if users.isEmpty {
                                    Text(emptyRsvpText)
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                }
                            }
                        }
                        .scrollBounceBehavior(.basedOnSize, axes: .horizontal)
                        .frame(minWidth: 0, maxWidth: .infinity, alignment: .leading)
                        .clipped()
                    }
                }
            }
        }
    }

    @ViewBuilder
    private var checkInSection: some View {
        if shouldShowCheckInSection {
            VStack(alignment: .leading, spacing: 14) {
                Text(String(localized: "event_check_in_heading"))
                    .font(.headline)
                    .foregroundStyle(.white)

                HStack(alignment: .center, spacing: 12) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(String(localized: "event_auto_check_in_label"))
                            .font(.system(size: 16, weight: .semibold))
                            .foregroundStyle(.white)
                        Text(String(localized: "event_auto_check_in_hint"))
                            .font(.caption)
                            .foregroundStyle(.secondary)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    Spacer(minLength: 12)
                    Toggle(
                        "",
                        isOn: Binding(
                            get: { appState.autoEventCheckInEnabled },
                            set: { appState.setAutoEventCheckInEnabled($0) }
                        )
                    )
                    .labelsHidden()
                    .tint(.purple)
                }
                .padding(14)
                .background(Color.white.opacity(0.045))
                .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                .overlay {
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .stroke(Color.white.opacity(0.08), lineWidth: 1)
                }

                if isCheckedIn {
                    VStack(spacing: 8) {
                        OttoGradientButtonLabel(title: String(localized: "event_checked_in_button"), systemImage: "checkmark.circle.fill")
                            .accessibilityElement(children: .combine)
                        Text(String(localized: "event_checked_in_caption"))
                            .font(.caption)
                            .foregroundStyle(.secondary)
                            .multilineTextAlignment(.center)
                            .frame(maxWidth: .infinity)
                    }
                } else if shouldShowManualCheckIn {
                    Button {
                        Task { await performManualCheckIn() }
                    } label: {
                        OttoGradientButtonLabel(title: String(localized: "event_check_in_button"), systemImage: "mappin.circle.fill")
                    }
                    .buttonStyle(.plain)
                    .disabled(checkInInFlight)
                }
            }
        }
    }

    private var actionBar: some View {
        HStack(alignment: .center, spacing: 10) {
            rsvpChoiceGroup
                .frame(maxWidth: .infinity)
        }
        .padding(.horizontal, 18)
        .padding(.top, 14)
        .padding(.bottom, 18)
        .background(.black.opacity(0.92))
    }

    private var rsvpChoiceGroup: some View {
        HStack(spacing: 8) {
            ForEach(RsvpChoice.allCases, id: \.rawValue) { choice in
                rsvpChoiceButton(choice)
            }
        }
    }

    private func rsvpChoiceButton(_ choice: RsvpChoice) -> some View {
        let isSelected = currentRsvpChoice == choice
        return Button {
            guard isRsvpInteractionEnabled else { return }
            rsvpSelectionHaptic()
            Task {
                await updateEventRsvp(status: choice.rawValue)
            }
        } label: {
            VStack(spacing: 4) {
                Image(systemName: choice.systemImage)
                    .font(.system(size: 18, weight: .semibold))
                Text(choice.title)
                    .font(.system(size: 11, weight: .semibold))
                    .lineLimit(1)
                    .minimumScaleFactor(0.72)
            }
            .foregroundStyle(isSelected ? .white : .white.opacity(0.76))
            .frame(maxWidth: .infinity)
            .frame(height: 56)
            .background(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(isSelected ? Color.purple.opacity(0.82) : Color.white.opacity(0.08))
            )
            .overlay(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .stroke(Color.white.opacity(isSelected ? 0.16 : 0.08), lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
        .disabled(checkInInFlight || !isRsvpInteractionEnabled)
        .accessibilityLabel(choice.title)
    }

    private func updateEventRsvp(status: String) async {
        guard let updated = await appState.setEventRsvp(eventID: currentEvent.id, status: status) else { return }
        localEventOverride = updated
        onEventUpdated?(updated)
    }

    private func rsvpSelectionHaptic() {
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
    }

    private func performManualCheckIn() async {
        guard !checkInInFlight else { return }
        checkInInFlight = true
        defer { checkInInFlight = false }
        var lat: Double?
        var lng: Double?
        if currentEvent.geoCoordinate != nil {
            if locationService.authorizationStatus == .notDetermined {
                pendingEventDetailCheckInAfterLocation = true
                withAnimation(.spring(response: 0.38, dampingFraction: 0.86)) {
                    showEventDetailLocationPrimer = true
                }
                return
            }
            if locationService.authorizationStatus == .denied || locationService.authorizationStatus == .restricted {
                withAnimation(.easeInOut(duration: 0.18)) {
                    showEventDetailLocationDeniedModal = true
                }
                return
            }
            guard let loc = locationService.latestSample ?? locationService.lastLocation else {
                appState.errorMessage = "Couldn’t read your location yet. Try again in a moment."
                return
            }
            guard Date().timeIntervalSince(loc.timestamp) <= 30 else {
                appState.errorMessage = "Couldn’t read your location yet. Try again in a moment."
                return
            }
            guard let eventCoord = currentEvent.geoCoordinate else { return }
            let d = Self.haversineMeters(
                lat1: loc.coordinate.latitude,
                lon1: loc.coordinate.longitude,
                lat2: eventCoord.latitude,
                lon2: eventCoord.longitude
            )
            if d > Self.checkInRadiusMeters {
                return
            }
            lat = loc.coordinate.latitude
            lng = loc.coordinate.longitude
        }
        await appState.postManualEventCheckIn(eventId: currentEvent.id, latitude: lat, longitude: lng)
    }

    private func requestLocationForEventDetailCheckIn() {
        withAnimation(.easeInOut(duration: 0.18)) {
            showEventDetailLocationPrimer = false
        }
        switch locationService.authorizationStatus {
        case .notDetermined:
            pendingEventDetailCheckInAfterLocation = true
            locationService.requestPermissionIfNeeded()
        case .authorizedAlways, .authorizedWhenInUse:
            pendingEventDetailCheckInAfterLocation = false
            appState.requestLocationSessionSync()
            Task { await performManualCheckIn() }
        case .denied, .restricted:
            pendingEventDetailCheckInAfterLocation = false
            withAnimation(.easeInOut(duration: 0.18)) {
                showEventDetailLocationDeniedModal = true
            }
        @unknown default:
            break
        }
    }

    private var dateBadge: some View {
        VStack(spacing: 2) {
            Text(monthText)
                .font(.system(size: 13, weight: .bold))
            Text(dayText)
                .font(.system(size: 28, weight: .medium, design: .rounded))
        }
        .foregroundStyle(.purple)
        .frame(width: 58, height: 76)
        .background(Color.black.opacity(0.5))
        .overlay {
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .stroke(Color.purple, lineWidth: 1.3)
        }
    }

    private var divider: some View {
        Rectangle()
            .fill(Color.white.opacity(0.08))
            .frame(height: 1)
            .padding(.leading, 38)
    }

    private func pill(icon: String, text: String, color: Color) -> some View {
        Label(text, systemImage: icon)
            .font(.caption2.weight(.semibold))
            .foregroundStyle(color)
            .padding(.horizontal, 8)
            .padding(.vertical, 5)
            .background(Color.white.opacity(0.055))
            .clipShape(Capsule())
            .overlay(Capsule().stroke(Color.white.opacity(0.08), lineWidth: 1))
    }

    private func factRow(icon: String, title: String, value: String, chevron: Bool = false) -> some View {
        HStack(spacing: 14) {
            Image(systemName: icon)
                .font(.system(size: 19, weight: .semibold))
                .foregroundStyle(.purple)
                .frame(width: 24)
            Text(title)
                .foregroundStyle(.white)
                .lineLimit(1)
            Spacer(minLength: 8)
            Text(value)
                .foregroundStyle(.white.opacity(0.7))
                .multilineTextAlignment(.trailing)
                .lineLimit(2)
                .truncationMode(.tail)
                .fixedSize(horizontal: false, vertical: true)
                .layoutPriority(1)
            if chevron {
                Image(systemName: "chevron.right")
                    .foregroundStyle(.white.opacity(0.8))
            }
        }
        .font(.system(size: 15))
        .frame(maxWidth: .infinity, minHeight: 50, alignment: .leading)
    }

}

private struct EventRsvpUserEntry: Identifiable {
    let choice: EventDetailView.RsvpChoice
    let user: UserDTO

    var id: String {
        let uid = user.id.trimmingCharacters(in: .whitespacesAndNewlines)
        return "\(choice.rawValue):\(uid)"
    }
}

private struct EventRsvpUsersSheet: View {
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject private var appState: AppState
    let entries: [EventRsvpUserEntry]

    private var sortedEntries: [EventRsvpUserEntry] {
        var seen = Set<String>()
        let deduped = entries.filter { seen.insert($0.id).inserted }
        return deduped.sorted {
            if $0.choice.sortOrder != $1.choice.sortOrder {
                return $0.choice.sortOrder < $1.choice.sortOrder
            }
            return $0.user.displayName.localizedCaseInsensitiveCompare($1.user.displayName) == .orderedAscending
        }
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 0) {
                    ForEach(sortedEntries) { entry in
                        HStack(spacing: 12) {
                            AvatarView(
                                name: entry.user.displayName,
                                avatarUrl: entry.user.avatarUrl,
                                size: 44,
                                accentColor: MapAccentPalette.resolvedColor(mapAccentKey: entry.user.mapAccentKey, userId: entry.user.id)
                            )
                            .overlay(alignment: .bottomTrailing) {
                                Circle()
                                    .fill(appState.avatarPresenceDotColor(forUserID: entry.user.id))
                                    .frame(width: 10, height: 10)
                                    .overlay(Circle().stroke(Color.black, lineWidth: 2))
                            }

                            Text(entry.user.displayName)
                                .font(.system(size: 16, weight: .semibold))
                                .foregroundStyle(.white)

                            Spacer()

                            Text(entry.choice.title)
                                .font(.caption.weight(.semibold))
                                .foregroundStyle(.white.opacity(0.78))
                                .padding(.horizontal, 10)
                                .padding(.vertical, 6)
                                .background(.white.opacity(0.10), in: Capsule())
                        }
                        .padding(.horizontal, 18)
                        .padding(.vertical, 12)
                    }
                }
            }
            .background(Color.black)
            .navigationTitle("RSVPs")
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
    }
}

private enum EventsTab: OttoTabItem {
    case upcoming
    case squads
    case mine

    var title: String {
        switch self {
        case .upcoming: return String(localized: "events_tab_upcoming")
        case .squads: return "Squads"
        case .mine: return "My Events"
        }
    }
}

struct EventBannerImage: View {
    let urlString: String?

    var body: some View {
        if let urlString, let url = URL(string: urlString) {
            CachedAsyncImage(url: url) { phase in
                switch phase {
                case .success(let image):
                    image
                        .resizable()
                        .scaledToFill()
                case .failure:
                    placeholder
                case .empty:
                    placeholder.overlay { ProgressView() }
                @unknown default:
                    placeholder
                }
            }
            .clipped()
        } else {
            placeholder
        }
    }

    private var placeholder: some View {
        LinearGradient(
            colors: [Color.purple.opacity(0.55), Color.black.opacity(0.8)],
            startPoint: .topLeading,
            endPoint: .bottomTrailing
        )
    }
}

struct EventRowTextScrim: View {
    var body: some View {
        LinearGradient(
            stops: [
                .init(color: .clear, location: 0),
                .init(color: .black.opacity(0.42), location: 0.5),
                .init(color: .black.opacity(0.92), location: 1),
            ],
            startPoint: .top,
            endPoint: .bottom
        )
        .frame(maxWidth: .infinity)
        .frame(height: 88)
    }
}

private enum EventListTimingCallout {
    case happeningNow

    static func forStartDate(_ date: Date, now: Date = Date()) -> EventListTimingCallout? {
        if date <= now { return .happeningNow }
        return nil
    }

    var label: String {
        switch self {
        case .happeningNow: return "HAPPENING NOW"
        }
    }

    var color: Color {
        switch self {
        case .happeningNow: return Color.green.opacity(0.88)
        }
    }
}

private struct EventRowStatusCallout: View {
    let icon: String
    let label: String
    let color: Color

    var body: some View {
        HStack(spacing: 3) {
            Image(systemName: icon)
                .font(.system(size: 9, weight: .semibold))
            Text(label)
                .font(.system(size: 10, weight: .semibold))
        }
        .foregroundStyle(color)
    }
}

private struct EventRowTypePill: View {
    let icon: String
    let label: String
    let color: Color

    var body: some View {
        HStack(spacing: 3) {
            Image(systemName: icon)
                .font(.system(size: 9, weight: .semibold))
            Text(label)
                .font(.system(size: 10, weight: .semibold))
        }
        .foregroundStyle(color)
        .padding(.horizontal, 8)
        .padding(.vertical, 4)
        .background(Color.white.opacity(0.055))
        .clipShape(Capsule())
        .overlay {
            Capsule()
                .stroke(Color.white.opacity(0.08), lineWidth: 1)
        }
    }
}

struct EventRow: View {
    let event: EventDTO
    /// When set, shown under the location line (distance from you).
    var distanceMiles: Double?
    /// When set (multi-squad feeds), shown under the event name.
    var squadName: String?
    var showBanner: Bool = true
    var goingCountOverride: Int?
    var groupedInSection: Bool = false

    private var timingCallout: EventListTimingCallout? {
        EventListTimingCallout.forStartDate(event.startsAt)
    }

    private var isUserGoing: Bool {
        event.currentUserRsvp == "going"
    }

    private var isCommunityEvent: Bool {
        event.eventType == "community"
    }

    private var dateBadgeForeground: Color {
        isCommunityEvent ? Color.cyan.opacity(0.82) : Color.purple.opacity(0.82)
    }

    private var dateBadgeStroke: Color {
        isCommunityEvent ? Color.cyan.opacity(0.50) : Color.purple.opacity(0.50)
    }

    private var locationText: String {
        if let label = event.address?.label, !label.isEmpty { return label }
        let cityRegion = [event.address?.city, event.address?.region]
            .compactMap { $0?.isEmpty == false ? $0 : nil }
            .joined(separator: ", ")
        return cityRegion.isEmpty ? "Location TBD" : cityRegion
    }

    private var monthText: String {
        let formatter = DateFormatter()
        formatter.dateFormat = "MMM"
        return formatter.string(from: event.startsAt).uppercased()
    }

    private var dayText: String {
        let formatter = DateFormatter()
        formatter.dateFormat = "dd"
        return formatter.string(from: event.startsAt)
    }

    private var goingCount: Int {
        goingCountOverride ?? event.rsvpCounts?.going ?? 0
    }

    var body: some View {
        Group {
            if groupedInSection {
                rowContent
                    .padding(12)
                    .frame(maxWidth: .infinity, alignment: .leading)
            } else if showBanner {
                bannerRow
            } else {
                compactRow
            }
        }
        .modifier(EventRowOuterChrome(showBanner: showBanner, groupedInSection: groupedInSection))
        .frame(maxWidth: .infinity, alignment: .leading)
        .contentShape(Rectangle())
    }

    private struct EventRowOuterChrome: ViewModifier {
        let showBanner: Bool
        let groupedInSection: Bool

        func body(content: Content) -> some View {
            if groupedInSection {
                content
            } else {
                content
                    .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                    .overlay {
                        RoundedRectangle(cornerRadius: 16, style: .continuous)
                            .stroke(Color.white.opacity(0.10), lineWidth: 1)
                            .allowsHitTesting(false)
                    }
            }
        }
    }

    private var bannerRow: some View {
        ZStack(alignment: .bottomLeading) {
            banner
                .frame(height: 132)
                .overlay {
                    LinearGradient(
                        colors: [.black.opacity(0.05), .black.opacity(0.88)],
                        startPoint: .top,
                        endPoint: .bottom
                    )
                    .allowsHitTesting(false)
                }

            EventRowTextScrim()
                .allowsHitTesting(false)

            rowContent
                .padding(12)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var compactRow: some View {
        rowContent
            .padding(12)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color.white.opacity(0.055))
    }

    private var rowContent: some View {
        HStack(alignment: .center, spacing: 12) {
            dateBadge

            VStack(alignment: .leading, spacing: 4) {
                if timingCallout != nil || isUserGoing {
                    HStack(spacing: 10) {
                        if let timingCallout {
                            EventRowStatusCallout(
                                icon: "clock.fill",
                                label: timingCallout.label,
                                color: timingCallout.color
                            )
                        }
                        if isUserGoing {
                            EventRowStatusCallout(
                                icon: "star.fill",
                                label: "GOING",
                                color: Color.purple.opacity(0.88)
                            )
                        }
                    }
                }

                if isCommunityEvent, !showBanner {
                    EventRowTypePill(
                        icon: "person.2",
                        label: String(localized: "events_row_type_community"),
                        color: Color.cyan.opacity(0.88)
                    )
                }

                Text(event.name)
                    .font(.system(size: 17, weight: .semibold, design: .rounded))
                    .foregroundStyle(.white.opacity(0.96))
                    .lineLimit(1)

                if let squadName {
                    Text(squadName)
                        .font(.system(size: 11, weight: .medium))
                        .foregroundStyle(.white.opacity(0.50))
                        .lineLimit(1)
                }

                HStack(spacing: 4) {
                    Image(systemName: "mappin")
                        .font(.system(size: 11, weight: .regular))
                        .foregroundStyle(.white.opacity(0.38))
                    Text(locationText)
                        .font(.system(size: 12, weight: .regular))
                        .foregroundStyle(.white.opacity(0.56))
                        .lineLimit(1)
                }

                if let distanceMiles {
                    Text(formatDistanceMiles(distanceMiles))
                        .font(.system(size: 11, weight: .regular))
                        .foregroundStyle(.white.opacity(0.50))
                }
            }

            Spacer(minLength: 0)

            VStack(alignment: .trailing, spacing: 0) {
                Text("\(goingCount)")
                    .font(.system(size: 18, weight: .medium, design: .rounded))
                    .foregroundStyle(.white.opacity(0.92))
                Text("going")
                    .font(.system(size: 11))
                    .foregroundStyle(.white.opacity(0.50))
            }
        }
    }

    @ViewBuilder
    private var banner: some View {
        EventBannerImage(urlString: event.bannerImage?.url)
            .frame(maxWidth: .infinity)
            .clipped()
    }

    private var dateBadge: some View {
        VStack(spacing: 1) {
            Text(monthText)
                .font(.system(size: 10, weight: .semibold))
            Text(dayText)
                .font(.system(size: 19, weight: .medium, design: .rounded))
        }
        .foregroundStyle(dateBadgeForeground)
        .frame(width: 42, height: 54)
        .background(Color.black.opacity(0.78))
        .overlay {
            RoundedRectangle(cornerRadius: 8, style: .continuous)
                .stroke(dateBadgeStroke, lineWidth: 1)
        }
        .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
    }

    private func formatDistanceMiles(_ miles: Double) -> String {
        if miles < 10 {
            return String(format: "%.1f mi away (approx.)", miles)
        }
        return String(format: "%.0f mi away (approx.)", miles)
    }
}

private extension EventDTO {
    /// GeoJSON point: `[longitude, latitude]`
    var geoCoordinate: CLLocationCoordinate2D? {
        guard let coordinates = location?.coordinates, coordinates.count >= 2 else { return nil }
        let lng = coordinates[0]
        let lat = coordinates[1]
        guard (-90...90).contains(lat), (-180...180).contains(lng) else { return nil }
        return CLLocationCoordinate2D(latitude: lat, longitude: lng)
    }
}
