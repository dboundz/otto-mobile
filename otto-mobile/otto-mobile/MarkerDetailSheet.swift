import CoreLocation
import SwiftUI
import UIKit

// MARK: - Style

enum MarkerDetailStyle {
    static let sheetBackground = Color(red: 0.025, green: 0.025, blue: 0.035)
    static let cardFill = Color.white.opacity(0.06)
    static let cardStroke = Color.white.opacity(0.10)
    static let secondaryActionFill = Color.white.opacity(0.055)
    static let secondaryActionStroke = Color.white.opacity(0.06)
    static let labelSecondary = Color.white.opacity(0.45)
    static let textSecondary = Color.white.opacity(0.78)
    static let statusChipFill = Color.white.opacity(0.08)
}

enum MarkerDetailType: String {
    case savedPlace
    case event
    case raceTrack
    case route
}

enum MarkerDetailIcon: Equatable {
    case systemImage(String)
    case asset(String)
}

struct MarkerStatusChip: Equatable {
    let text: String
}

struct MarkerInfoItem: Identifiable, Equatable {
    let id: String
    let icon: String
    let label: String
    let value: String

    init(icon: String, label: String, value: String) {
        self.id = "\(label)-\(value)"
        self.icon = icon
        self.label = label
        self.value = value
    }
}

enum MarkerDetailActionStyle: Equatable {
    case primary
    case secondary
    case destructive
}

struct MarkerDetailAction: Identifiable, Equatable {
    let id: String
    let title: String
    let systemImage: String
    let style: MarkerDetailActionStyle
    let isEnabled: Bool

    static func == (lhs: MarkerDetailAction, rhs: MarkerDetailAction) -> Bool {
        lhs.id == rhs.id && lhs.title == rhs.title && lhs.style == rhs.style && lhs.isEnabled == rhs.isEnabled
    }
}

struct MarkerDetailSectionLink: Equatable {
    let title: String
}

enum MarkerDetailSectionContent: Equatable {
    case upcomingEvents([UpcomingEventItem], maxVisible: Int = 2)
    case footerButton(title: String, systemImage: String, style: FooterButtonStyle)
    case mapMarkerShare(MapMarkerSharePayload)
    case eventShare

    enum FooterButtonStyle: Equatable {
        case neutral
        case accentOutline
    }
}

struct MarkerDetailSection: Identifiable, Equatable {
    let id: String
    let title: String?
    let trailingLink: MarkerDetailSectionLink?
    let content: MarkerDetailSectionContent
}

struct UpcomingEventItem: Identifiable, Equatable {
    let id: String
    let title: String
    let monthAbbrev: String
    let dayText: String
    let timeLine: String
    let goingCount: Int
}

struct MarkerDetailSheetModel: Equatable {
    let markerType: MarkerDetailType
    let accentColor: Color
    let headerTitle: String
    let icon: MarkerDetailIcon
    let title: String
    let subtitle: String?
    let categoryLabel: String?
    let locationLine: String?
    let statusChip: MarkerStatusChip?
    let infoItems: [MarkerInfoItem]
    let actions: [MarkerDetailAction]
    let sections: [MarkerDetailSection]
    let mapMarkerSharePayload: MapMarkerSharePayload?

    static func == (lhs: MarkerDetailSheetModel, rhs: MarkerDetailSheetModel) -> Bool {
        lhs.markerType == rhs.markerType
            && lhs.headerTitle == rhs.headerTitle
            && lhs.title == rhs.title
            && lhs.subtitle == rhs.subtitle
            && lhs.categoryLabel == rhs.categoryLabel
            && lhs.locationLine == rhs.locationLine
            && lhs.statusChip == rhs.statusChip
            && lhs.infoItems == rhs.infoItems
            && lhs.actions == rhs.actions
            && lhs.sections == rhs.sections
            && lhs.icon == rhs.icon
            && lhs.mapMarkerSharePayload == rhs.mapMarkerSharePayload
    }
}

// MARK: - Shell components

struct MarkerDetailSheetHeader: View {
    let title: String
    let onDone: () -> Void

    var body: some View {
        VStack(spacing: 10) {
            Capsule()
                .fill(Color.white.opacity(0.35))
                .frame(width: 36, height: 4)

            ZStack {
                Text(title)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(Color.white.opacity(0.72))
                    .frame(maxWidth: .infinity)

                HStack {
                    Spacer()
                    Button(String(localized: "marker_detail_done"), action: onDone)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(.white)
                        .buttonStyle(.plain)
                }
            }
        }
    }
}

struct MarkerIdentityHeader: View {
    let accentColor: Color
    let icon: MarkerDetailIcon
    let title: String
    let subtitle: String?
    let categoryLabel: String?
    let locationLine: String?
    let statusChip: MarkerStatusChip?

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            markerIcon
                .frame(width: 56, height: 56)

            VStack(alignment: .leading, spacing: 4) {
                Text(title)
                    .font(.title3.weight(.bold))
                    .foregroundStyle(.white)
                    .lineLimit(2)

                if let subtitle, !subtitle.isEmpty {
                    Text(subtitle)
                        .font(.subheadline)
                        .foregroundStyle(MarkerDetailStyle.textSecondary)
                        .lineLimit(2)
                }

                if let categoryLabel, !categoryLabel.isEmpty {
                    Text(categoryLabel)
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(accentColor)
                        .lineLimit(2)
                }

                if let locationLine, !locationLine.isEmpty {
                    HStack(spacing: 4) {
                        Image(systemName: "mappin")
                            .font(.caption2.weight(.semibold))
                            .foregroundStyle(MarkerDetailStyle.labelSecondary)
                        Text(locationLine)
                            .font(.caption)
                            .foregroundStyle(MarkerDetailStyle.labelSecondary)
                            .lineLimit(2)
                    }
                    .padding(.top, 2)
                }
            }

            Spacer(minLength: 0)

            if let statusChip {
                MarkerStatusChipView(text: statusChip.text)
            }
        }
    }

    @ViewBuilder
    private var markerIcon: some View {
        ZStack {
            Circle()
                .fill(accentColor.opacity(0.18))
            switch icon {
            case .systemImage(let name):
                Image(systemName: name)
                    .font(.system(size: 24, weight: .semibold))
                    .foregroundStyle(accentColor)
            case .asset(let name):
                Image(name)
                    .resizable()
                    .scaledToFit()
                    .frame(width: 30, height: 44)
            }
        }
    }
}

struct MarkerStatusChipView: View {
    let text: String

    var body: some View {
        Text(text)
            .font(.caption2.weight(.bold))
            .foregroundStyle(.white.opacity(0.82))
            .padding(.horizontal, 10)
            .padding(.vertical, 6)
            .background(MarkerDetailStyle.statusChipFill)
            .clipShape(Capsule())
    }
}

struct MarkerInfoCard: View {
    let accentColor: Color
    let items: [MarkerInfoItem]

    private let columns = [
        GridItem(.flexible(minimum: 120), spacing: 12),
        GridItem(.flexible(minimum: 120), spacing: 12),
    ]

    var body: some View {
        LazyVGrid(columns: items.count <= 1 ? [GridItem(.flexible())] : columns, alignment: .leading, spacing: 14) {
            ForEach(items) { item in
                VStack(alignment: .leading, spacing: 6) {
                    Image(systemName: item.icon)
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(accentColor)
                    Text(item.label.uppercased())
                        .font(.caption2.weight(.semibold))
                        .foregroundStyle(MarkerDetailStyle.labelSecondary)
                    Text(item.value)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(.white)
                        .lineLimit(2)
                        .minimumScaleFactor(0.85)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(MarkerDetailStyle.cardFill)
        .overlay(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .stroke(MarkerDetailStyle.cardStroke, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
    }
}

struct MarkerActionGrid: View {
    let accentColor: Color
    let actions: [MarkerDetailAction]
    let onAction: (MarkerDetailAction) -> Void

    var body: some View {
        VStack(spacing: 10) {
            ForEach(Array(actions.chunked(into: 2).enumerated()), id: \.offset) { _, row in
                HStack(spacing: 10) {
                    ForEach(row) { action in
                        Button {
                            onAction(action)
                        } label: {
                            actionLabel(action)
                        }
                        .buttonStyle(.plain)
                        .disabled(!action.isEnabled)
                    }
                    if row.count == 1 {
                        Color.clear
                            .frame(maxWidth: .infinity)
                            .allowsHitTesting(false)
                    }
                }
            }
        }
    }

    @ViewBuilder
    private func actionLabel(_ action: MarkerDetailAction) -> some View {
        VStack(spacing: 8) {
            Image(systemName: action.systemImage)
                .font(.title3.weight(.semibold))
            Text(action.title)
                .font(.subheadline.weight(.semibold))
                .multilineTextAlignment(.center)
                .lineLimit(2)
                .minimumScaleFactor(0.85)
        }
        .foregroundStyle(foreground(for: action))
        .frame(maxWidth: .infinity)
        .frame(minHeight: 52)
        .padding(.vertical, 10)
        .padding(.horizontal, 6)
        .background(background(for: action))
        .overlay(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .stroke(border(for: action), lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
    }

    private func foreground(for action: MarkerDetailAction) -> Color {
        guard action.isEnabled else { return .white.opacity(0.35) }
        switch action.style {
        case .primary: return .white
        case .secondary: return .white.opacity(0.92)
        case .destructive: return Color.red.opacity(0.92)
        }
    }

    private func background(for action: MarkerDetailAction) -> Color {
        guard action.isEnabled else { return MarkerDetailStyle.secondaryActionFill.opacity(0.5) }
        switch action.style {
        case .primary: return accentColor
        case .secondary, .destructive: return MarkerDetailStyle.secondaryActionFill
        }
    }

    private func border(for action: MarkerDetailAction) -> Color {
        switch action.style {
        case .primary: return accentColor.opacity(0.35)
        case .destructive: return Color.red.opacity(0.35)
        case .secondary: return MarkerDetailStyle.secondaryActionStroke
        }
    }
}

private extension Array {
    func chunked(into size: Int) -> [[Element]] {
        guard size > 0, !isEmpty else { return isEmpty ? [] : [self] }
        return stride(from: 0, to: count, by: size).map {
            Array(self[$0 ..< Swift.min($0 + size, count)])
        }
    }
}

struct MarkerSectionCard: View {
    let accentColor: Color
    let section: MarkerDetailSection
    let onTrailingLink: (() -> Void)?
    let onUpcomingEventTap: ((UpcomingEventItem) -> Void)?
    let onFooterTap: ((MarkerDetailSection) -> Void)?

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            if section.title != nil || section.trailingLink != nil {
                HStack(alignment: .firstTextBaseline) {
                    if let title = section.title {
                        Text(title)
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(.white.opacity(0.88))
                    }
                    Spacer(minLength: 8)
                    if let link = section.trailingLink {
                        Button(link.title, action: { onTrailingLink?() })
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(accentColor)
                            .buttonStyle(.plain)
                    }
                }
            }

            switch section.content {
            case .upcomingEvents(let items, let maxVisible):
                VStack(spacing: 8) {
                    ForEach(Array(items.prefix(maxVisible))) { item in
                        Button {
                            onUpcomingEventTap?(item)
                        } label: {
                            UpcomingEventRow(item: item, accentColor: accentColor)
                        }
                        .buttonStyle(.plain)
                    }
                }
            case .footerButton(let title, let systemImage, let style):
                Button {
                    onFooterTap?(section)
                } label: {
                    HStack(spacing: 10) {
                        Image(systemName: systemImage)
                            .font(.body.weight(.semibold))
                        Text(title)
                            .font(.subheadline.weight(.semibold))
                        Spacer()
                    }
                    .foregroundStyle(style == .accentOutline ? accentColor : .white.opacity(0.88))
                    .padding(.horizontal, 14)
                    .padding(.vertical, 14)
                    .frame(maxWidth: .infinity)
                    .background(MarkerDetailStyle.cardFill)
                    .overlay(
                        RoundedRectangle(cornerRadius: 14, style: .continuous)
                            .stroke(style == .accentOutline ? accentColor.opacity(0.45) : MarkerDetailStyle.cardStroke, lineWidth: 1)
                    )
                    .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                }
                .buttonStyle(.plain)
            case .mapMarkerShare, .eventShare:
                EmptyView()
            }
        }
    }
}

struct UpcomingEventRow: View {
    let item: UpcomingEventItem
    let accentColor: Color

    var body: some View {
        HStack(spacing: 12) {
            VStack(spacing: 0) {
                Text(item.monthAbbrev)
                    .font(.caption2.weight(.bold))
                    .foregroundStyle(.white.opacity(0.72))
                Text(item.dayText)
                    .font(.headline.weight(.bold))
                    .foregroundStyle(.white)
            }
            .frame(width: 44)
            .padding(.vertical, 8)
            .background(Color.white.opacity(0.08))
            .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))

            VStack(alignment: .leading, spacing: 3) {
                Text(item.title)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.white)
                    .lineLimit(2)
                Text(item.timeLine)
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.white.opacity(0.62))
            }

            Spacer(minLength: 0)

            Text(String(format: String(localized: "marker_detail_going_count_format"), item.goingCount))
                .font(.caption2.weight(.semibold))
                .foregroundStyle(.white.opacity(0.55))

            Image(systemName: "chevron.right")
                .font(.caption.weight(.bold))
                .foregroundStyle(.white.opacity(0.45))
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
        .background(MarkerDetailStyle.cardFill)
        .overlay(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .stroke(MarkerDetailStyle.cardStroke, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
    }
}

struct MarkerDetailSheet: View {
    let model: MarkerDetailSheetModel
    let onAction: (MarkerDetailAction) -> Void
    let onDone: () -> Void
    let onTrailingLink: ((MarkerDetailSection) -> Void)?
    let onUpcomingEventTap: ((UpcomingEventItem) -> Void)?
    let onFooterTap: ((MarkerDetailSection) -> Void)?

    private var shouldScroll: Bool {
        MarkerDetailSheetHeight.reachesHeightCap(for: model)
    }

    var body: some View {
        Group {
            if shouldScroll {
                ScrollView(showsIndicators: false) {
                    sheetContent
                }
            } else {
                sheetContent
            }
        }
        .background(MarkerDetailStyle.sheetBackground.ignoresSafeArea())
    }

    private var sheetContent: some View {
        VStack(alignment: .leading, spacing: OttoScreenChrome.stackSpacing) {
            MarkerDetailSheetHeader(title: model.headerTitle, onDone: onDone)

            MarkerIdentityHeader(
                accentColor: model.accentColor,
                icon: model.icon,
                title: model.title,
                subtitle: model.subtitle,
                categoryLabel: model.categoryLabel,
                locationLine: model.locationLine,
                statusChip: model.statusChip
            )

            if !model.infoItems.isEmpty {
                MarkerInfoCard(accentColor: model.accentColor, items: model.infoItems)
            }

            if !model.actions.isEmpty {
                MarkerActionGrid(accentColor: model.accentColor, actions: model.actions, onAction: onAction)
            }

            ForEach(model.sections) { section in
                MarkerSectionCard(
                    accentColor: model.accentColor,
                    section: section,
                    onTrailingLink: { onTrailingLink?(section) },
                    onUpcomingEventTap: onUpcomingEventTap,
                    onFooterTap: onFooterTap
                )
            }
        }
        .padding(.horizontal, OttoScreenChrome.horizontalPadding)
        .padding(.top, 8)
        .padding(.bottom, OttoScreenChrome.bottomPadding)
    }
}

// MARK: - Map integration wrapper

struct MapMarkerDetailSheet: View {
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject private var appState: AppState

    enum Content {
        case savedPlace(SavedPlaceDTO)
        case event(primary: EventDTO, siblings: [EventDTO])
        case raceTrack(RaceTrackRecord)
    }

    let content: Content
    let distanceFromMe: String?

    @State private var primaryEvent: EventDTO?
    @State private var siblingEvents: [EventDTO] = []
    @State private var savedPlaceNameDraft = ""
    @State private var isEditingSavedPlace = false
    @State private var placePendingDelete: SavedPlaceDTO?
    @State private var isPerformingAction = false
    @State private var rsvpBusy = false
    @State private var isShowingShareSquadActionsSheet = false
    @State private var mapMarkerSharePayload: MapMarkerSharePayload?

    var body: some View {
        let model = buildModel()
        MarkerDetailSheet(
            model: model,
            onAction: handleAction,
            onDone: { dismiss() },
            onTrailingLink: handleTrailingLink,
            onUpcomingEventTap: handleUpcomingEventTap,
            onFooterTap: handleFooterTap
        )
        .presentationDetents(MarkerDetailSheetHeight.presentationDetents(for: model))
        .presentationDragIndicator(.hidden)
        .onAppear(perform: bootstrapEventState)
        .alert(String(localized: "marker_detail_edit_place_title"), isPresented: $isEditingSavedPlace) {
            TextField(String(localized: "marker_detail_place_name"), text: $savedPlaceNameDraft)
            Button(String(localized: "marker_detail_save")) {
                Task { await savePlaceRename() }
            }
            Button(String(localized: "marker_detail_cancel"), role: .cancel) {}
        }
        .alert(
            String(localized: "marker_detail_delete_place_title"),
            isPresented: Binding(
                get: { placePendingDelete != nil },
                set: { if !$0 { placePendingDelete = nil } }
            )
        ) {
            Button(String(localized: "marker_detail_action_remove"), role: .destructive) {
                guard let place = placePendingDelete else { return }
                Task { await deletePlace(place) }
            }
            Button(String(localized: "marker_detail_cancel"), role: .cancel) {
                placePendingDelete = nil
            }
        } message: {
            Text(String(localized: "marker_detail_delete_place_message"))
        }
        .sheet(isPresented: $isShowingShareSquadActionsSheet) {
            if let event = primaryEvent ?? content.eventPrimary {
                EventShareSquadActionsSheet(
                    event: event,
                    onAssociationsSaved: { squads in
                        let updated = EventDTO(
                            id: event.id,
                            slug: event.slug,
                            visibility: event.visibility,
                            circleId: event.circleId,
                            createdByUserId: event.createdByUserId,
                            name: event.name,
                            description: event.description,
                            startsAt: event.startsAt,
                            endsAt: event.endsAt,
                            address: event.address,
                            location: event.location,
                            bannerImage: event.bannerImage,
                            rsvpCounts: event.rsvpCounts,
                            contactsGoing: event.contactsGoing,
                            contactsRsvps: event.contactsRsvps,
                            currentUserRsvp: event.currentUserRsvp,
                            currentUserCheckIn: event.currentUserCheckIn,
                            attachedSquads: squads,
                            isOfficialForCircle: event.isOfficialForCircle
                        )
                        primaryEvent = updated
                        appState.upsertUpcomingEvent(updated)
                    },
                    onPostedToChat: { dismiss() }
                )
                .environmentObject(appState)
            } else if let payload = mapMarkerSharePayload {
                MapMarkerShareSquadActionsSheet(
                    payload: payload,
                    onPostedToChat: { dismiss() }
                )
                .environmentObject(appState)
            }
        }
    }

    private func bootstrapEventState() {
        guard case .event(let primary, let siblings) = content else { return }
        if primaryEvent == nil {
            primaryEvent = primary
            siblingEvents = siblings
        }
    }

    private func buildModel() -> MarkerDetailSheetModel {
        switch content {
        case .savedPlace(let place):
            let isOwned = appState.savedPlaces.contains(where: { $0.id == place.id })
            return MarkerDetailSheetModelBuilder.savedPlace(place: place, distance: distanceFromMe, isOwned: isOwned)
        case .event(let initialPrimary, let initialSiblings):
            let event = primaryEvent ?? initialPrimary
            let siblings = siblingEvents.isEmpty && primaryEvent == nil ? initialSiblings : siblingEvents
            return MarkerDetailSheetModelBuilder.event(
                event: event,
                siblings: siblings,
                distance: distanceFromMe,
                rsvpBusy: rsvpBusy
            )
        case .raceTrack(let track):
            return MarkerDetailSheetModelBuilder.raceTrack(track: track, distance: distanceFromMe)
        }
    }

    private func handleAction(_ action: MarkerDetailAction) {
        switch action.id {
        case "share":
            switch content {
            case .event:
                isShowingShareSquadActionsSheet = true
            case .savedPlace, .raceTrack:
                mapMarkerSharePayload = buildModel().mapMarkerSharePayload
                isShowingShareSquadActionsSheet = true
            }
        case "directions":
            openDirections()
        case "edit":
            beginEditSavedPlace()
        case "remove":
            if case .savedPlace(let place) = content {
                placePendingDelete = place
            }
        case "open_event":
            openEventDetail()
        case "rsvp":
            Task { await toggleRsvp() }
        case "add_to_places":
            Task { await addTrackToPlaces() }
        default:
            break
        }
    }

    private func handleTrailingLink(_ section: MarkerDetailSection) {
        guard section.trailingLink != nil, let event = primaryEvent ?? content.eventPrimary else { return }
        dismiss()
        Task { @MainActor in
            try? await Task.sleep(nanoseconds: 180_000_000)
            appState.navigateToEventDetail(eventRef: event.id)
        }
    }

    private func handleUpcomingEventTap(_ item: UpcomingEventItem) {
        let allEvents = appState.upcomingEvents + appState.communityEvents
        guard let event = siblingEvents.first(where: { $0.id == item.id })
            ?? allEvents.first(where: { $0.id == item.id }) else { return }
        if let current = primaryEvent {
            siblingEvents.removeAll { $0.id == event.id }
            siblingEvents.append(current)
            siblingEvents.sort { $0.startsAt < $1.startsAt }
        }
        primaryEvent = event
    }

    private func handleFooterTap(_ section: MarkerDetailSection) {
        guard case .footerButton = section.content else { return }
    }

    private func openDirections() {
        guard let url = directionsURL else { return }
        dismiss()
        UIApplication.shared.open(url)
    }

    private var directionsURL: URL? {
        switch content {
        case .savedPlace(let place):
            return URL(string: "http://maps.apple.com/?daddr=\(place.latitude),\(place.longitude)&dirflg=d")
        case .event:
            guard let event = primaryEvent ?? (content.eventPrimary), let coord = event.eventGeoCoordinate else { return nil }
            return URL(string: "http://maps.apple.com/?daddr=\(coord.latitude),\(coord.longitude)&dirflg=d")
        case .raceTrack(let track):
            guard let coord = track.coordinate else { return nil }
            return URL(string: "http://maps.apple.com/?daddr=\(coord.latitude),\(coord.longitude)&dirflg=d")
        }
    }

    private func openEventDetail() {
        guard let event = primaryEvent ?? content.eventPrimary else { return }
        dismiss()
        Task { @MainActor in
            try? await Task.sleep(nanoseconds: 180_000_000)
            appState.navigateToEventDetail(eventRef: event.id)
        }
    }

    private func beginEditSavedPlace() {
        guard case .savedPlace(let place) = content else { return }
        savedPlaceNameDraft = place.name
        isEditingSavedPlace = true
    }

    private func savePlaceRename() async {
        guard case .savedPlace(let place) = content else { return }
        let trimmed = savedPlaceNameDraft.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty, trimmed != place.name else { return }
        isPerformingAction = true
        defer { isPerformingAction = false }
        do {
            _ = try await APIClient.shared.updateSavedPlace(
                placeId: place.id,
                name: trimmed,
                addressSummary: place.addressSummary,
                placeKind: place.placeKind,
                latitude: nil,
                longitude: nil
            )
            await appState.refreshSavedPlaces()
            dismiss()
        } catch {
            appState.errorMessage = String(localized: "marker_detail_edit_place_error")
        }
    }

    private func deletePlace(_ place: SavedPlaceDTO) async {
        isPerformingAction = true
        placePendingDelete = nil
        defer { isPerformingAction = false }
        do {
            try await appState.deleteSavedPlace(placeId: place.id)
            dismiss()
        } catch {
            appState.errorMessage = String(localized: "marker_detail_delete_place_error")
        }
    }

    private func toggleRsvp() async {
        guard let event = primaryEvent ?? content.eventPrimary else { return }
        rsvpBusy = true
        defer { rsvpBusy = false }
        let next = event.currentUserRsvp == "going" ? "not_going" : "going"
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
        if let updated = await appState.setEventRsvp(eventID: event.id, status: next) {
            primaryEvent = updated
        }
    }

    private func addTrackToPlaces() async {
        guard case .raceTrack(let track) = content, let coord = track.coordinate else { return }
        await addPlace(name: track.name, latitude: coord.latitude, longitude: coord.longitude, address: track.locationLine)
    }

    private func addPlace(name: String, latitude: Double, longitude: Double, address: String?) async {
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
        isPerformingAction = true
        defer { isPerformingAction = false }
        do {
            _ = try await appState.createSavedPlace(
                name: name,
                latitude: latitude,
                longitude: longitude,
                placeKind: "other",
                poiCategory: nil,
                addressSummary: address
            )
            appState.activeToast = AppToast(
                text: String(localized: "marker_detail_added_to_places"),
                systemImage: "bookmark.fill"
            )
        } catch {
            appState.errorMessage = String(localized: "marker_detail_add_place_error")
        }
    }
}

private extension MapMarkerDetailSheet.Content {
    var eventPrimary: EventDTO? {
        if case .event(let primary, _) = self { return primary }
        return nil
    }
}

// MARK: - Model builders

enum MarkerDetailSheetHeight {
    static let cap: CGFloat = 720
    static let floor: CGFloat = 280

    static func estimatedHeightPoints(for model: MarkerDetailSheetModel) -> CGFloat {
        var height: CGFloat = 8 + 52
        height += 96
        height += OttoScreenChrome.stackSpacing

        if !model.infoItems.isEmpty {
            let columns = model.infoItems.count <= 1 ? 1 : 2
            let rows = ceil(Double(model.infoItems.count) / Double(columns))
            height += CGFloat(rows) * 72 + 28
            height += OttoScreenChrome.stackSpacing
        }

        if !model.actions.isEmpty {
            let actionRows = ceil(Double(model.actions.count) / 2.0)
            height += CGFloat(actionRows) * 72
            height += OttoScreenChrome.stackSpacing
        }

        for section in model.sections {
            switch section.content {
            case .mapMarkerShare, .eventShare:
                break
            case .upcomingEvents(let items, let maxVisible):
                height += 28
                let visibleCount = min(items.count, maxVisible)
                if visibleCount > 0 {
                    height += CGFloat(visibleCount) * 64
                    height += CGFloat(max(0, visibleCount - 1)) * 8
                }
                height += OttoScreenChrome.stackSpacing
            case .footerButton:
                height += 52
                height += OttoScreenChrome.stackSpacing
            }
        }

        height += OttoScreenChrome.bottomPadding
        return min(max(height, floor), cap)
    }

    static func reachesHeightCap(for model: MarkerDetailSheetModel) -> Bool {
        estimatedHeightPoints(for: model) >= cap - 1
    }

    static func presentationDetents(for model: MarkerDetailSheetModel) -> Set<PresentationDetent> {
        let estimated = estimatedHeightPoints(for: model)
        if estimated >= cap - 1 {
            return [.height(estimated), .large]
        }
        return [.height(estimated)]
    }
}

enum MarkerDetailSheetModelBuilder {
    static func savedPlace(place: SavedPlaceDTO, distance: String?, isOwned: Bool = true) -> MarkerDetailSheetModel {
        var info: [MarkerInfoItem] = []
        if let distance {
            info.append(.init(icon: "location.circle.fill", label: String(localized: "marker_detail_info_distance"), value: distance))
        }
        info.append(.init(icon: "mappin.and.ellipse", label: String(localized: "marker_detail_info_coordinates"), value: coordinateLine(lat: place.latitude, lng: place.longitude)))
        if isOwned {
            info.append(.init(icon: "bookmark.fill", label: String(localized: "marker_detail_info_saved_in"), value: String(localized: "marker_detail_my_places")))
            if let createdAt = place.createdAt {
                info.append(.init(icon: "calendar", label: String(localized: "marker_detail_info_added"), value: formattedAddedDate(createdAt)))
            }
        }

        let category = place.placeKind?
            .replacingOccurrences(of: "_", with: " ")
            .capitalized

        let sharePayload = MapMarkerSharePayload.savedPlace(
            id: place.id,
            name: place.name,
            addressSummary: place.addressSummary,
            latitude: place.latitude,
            longitude: place.longitude
        )

        var actions: [MarkerDetailAction] = [
            .init(id: "share", title: String(localized: "marker_detail_share"), systemImage: "square.and.arrow.up", style: .secondary, isEnabled: true),
            .init(id: "directions", title: String(localized: "marker_detail_action_directions"), systemImage: "location.fill", style: .primary, isEnabled: true),
        ]
        if isOwned {
            actions.append(contentsOf: [
                .init(id: "edit", title: String(localized: "marker_detail_action_edit"), systemImage: "pencil", style: .secondary, isEnabled: true),
                .init(id: "remove", title: String(localized: "marker_detail_action_remove"), systemImage: "trash", style: .destructive, isEnabled: true),
            ])
        }

        return MarkerDetailSheetModel(
            markerType: .savedPlace,
            accentColor: RouteMapMarkerColors.discoverySavedPlaceTeal,
            headerTitle: String(localized: "marker_detail_header_saved_place"),
            icon: .systemImage("mappin.circle.fill"),
            title: place.name,
            subtitle: place.addressSummary,
            categoryLabel: category,
            locationLine: nil,
            statusChip: nil,
            infoItems: info,
            actions: actions,
            sections: [],
            mapMarkerSharePayload: sharePayload
        )
    }

    static func raceTrack(track: RaceTrackRecord, distance: String?) -> MarkerDetailSheetModel {
        var info: [MarkerInfoItem] = []
        if let distance {
            info.append(.init(icon: "location.circle.fill", label: String(localized: "marker_detail_info_distance"), value: distance))
        }
        if let coord = track.coordinate {
            info.append(.init(icon: "mappin.and.ellipse", label: String(localized: "marker_detail_info_coordinates"), value: coordinateLine(lat: coord.latitude, lng: coord.longitude)))
        }
        if !track.formattedTypes.isEmpty {
            info.append(.init(icon: "road.lanes", label: String(localized: "marker_detail_info_surface"), value: track.formattedTypes))
        }

        let coord = track.coordinate
        let sharePayload = MapMarkerSharePayload.raceTrack(
            name: track.name,
            locationLine: track.locationLine,
            latitude: coord?.latitude,
            longitude: coord?.longitude
        )
        return MarkerDetailSheetModel(
            markerType: .raceTrack,
            accentColor: RouteMapMarkerColors.discoveryRaceTrackOrange,
            headerTitle: String(localized: "marker_detail_header_race_track"),
            icon: .asset("map-point-track"),
            title: track.name,
            subtitle: track.locationLine,
            categoryLabel: track.formattedTypes.nilIfEmpty,
            locationLine: nil,
            statusChip: nil,
            infoItems: info,
            actions: [
                .init(id: "share", title: String(localized: "marker_detail_share"), systemImage: "square.and.arrow.up", style: .secondary, isEnabled: true),
                .init(id: "directions", title: String(localized: "marker_detail_action_directions"), systemImage: "location.fill", style: .primary, isEnabled: coord != nil),
                .init(id: "add_to_places", title: String(localized: "marker_detail_action_add_to_places"), systemImage: "bookmark", style: .secondary, isEnabled: coord != nil),
            ],
            sections: [],
            mapMarkerSharePayload: sharePayload
        )
    }

    static func event(event: EventDTO, siblings: [EventDTO], distance: String?, rsvpBusy: Bool) -> MarkerDetailSheetModel {
        var info: [MarkerInfoItem] = []
        if let distance {
            info.append(.init(icon: "location.circle.fill", label: String(localized: "marker_detail_info_distance"), value: distance))
        }
        if let coord = event.eventGeoCoordinate {
            info.append(.init(icon: "mappin.and.ellipse", label: String(localized: "marker_detail_info_coordinates"), value: coordinateLine(lat: coord.latitude, lng: coord.longitude)))
        }
        let venue = eventVenueLine(event)
        if !venue.isEmpty {
            info.append(.init(icon: "building.2.fill", label: String(localized: "marker_detail_info_venue"), value: venue))
        }
        info.append(.init(icon: "calendar", label: String(localized: "marker_detail_info_date_time"), value: eventDateTimeLine(event)))
        if !siblings.isEmpty {
            info.append(.init(icon: "calendar.badge.clock", label: String(localized: "marker_detail_info_next_events"), value: String(format: String(localized: "marker_detail_next_events_count_format"), siblings.count + 1)))
        }

        let goingCount = event.rsvpCounts?.going ?? 0
        let isGoing = event.currentUserRsvp == "going"
        let rsvpTitle = isGoing ? String(localized: "marker_detail_action_going") : String(localized: "marker_detail_action_rsvp")

        var sections: [MarkerDetailSection] = []
        if !siblings.isEmpty {
            sections.append(
                .init(
                    id: "upcoming",
                    title: String(localized: "marker_detail_more_upcoming"),
                    trailingLink: .init(title: String(localized: "marker_detail_view_all_venue_events")),
                    content: .upcomingEvents(siblings.map { upcomingItem(from: $0) })
                )
            )
        }

        return MarkerDetailSheetModel(
            markerType: .event,
            accentColor: RouteMapMarkerColors.discoveryEventPink,
            headerTitle: String(localized: "marker_detail_header_event"),
            icon: .systemImage("calendar"),
            title: event.name,
            subtitle: eventScheduleSubtitle(event),
            categoryLabel: venue.nilIfEmpty,
            locationLine: venue.nilIfEmpty,
            statusChip: .init(text: String(format: String(localized: "marker_detail_going_count_format"), goingCount)),
            infoItems: info,
            actions: [
                .init(id: "share", title: String(localized: "marker_detail_share"), systemImage: "square.and.arrow.up", style: .secondary, isEnabled: true),
                .init(id: "directions", title: String(localized: "marker_detail_action_directions"), systemImage: "location.fill", style: .primary, isEnabled: event.eventGeoCoordinate != nil),
                .init(id: "open_event", title: String(localized: "marker_detail_action_open_event"), systemImage: "calendar.badge.clock", style: .secondary, isEnabled: true),
                .init(id: "rsvp", title: rsvpTitle, systemImage: isGoing ? "checkmark.circle.fill" : "hand.thumbsup.fill", style: .secondary, isEnabled: !rsvpBusy),
            ],
            sections: sections,
            mapMarkerSharePayload: nil
        )
    }

    private static func coordinateLine(lat: Double, lng: Double) -> String {
        String(format: "%.5f°, %.5f°", lat, lng)
    }

    private static func formattedAddedDate(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.dateStyle = .medium
        formatter.timeStyle = .none
        return formatter.string(from: date)
    }

    private static func eventVenueLine(_ event: EventDTO) -> String {
        if let label = event.address?.label, !label.isEmpty { return label }
        let cityRegion = [event.address?.city, event.address?.region]
            .compactMap { $0?.isEmpty == false ? $0 : nil }
            .joined(separator: ", ")
        return cityRegion
    }

    private static func eventScheduleSubtitle(_ event: EventDTO) -> String {
        let formatter = DateFormatter()
        formatter.dateFormat = "EEE, MMM d · h:mm a"
        return formatter.string(from: event.startsAt)
    }

    private static func eventDateTimeLine(_ event: EventDTO) -> String {
        eventScheduleSubtitle(event)
    }

    private static func upcomingItem(from event: EventDTO) -> UpcomingEventItem {
        let monthFormatter = DateFormatter()
        monthFormatter.dateFormat = "MMM"
        let dayFormatter = DateFormatter()
        dayFormatter.dateFormat = "dd"
        let timeFormatter = DateFormatter()
        timeFormatter.dateFormat = "EEE · h:mm a"
        return UpcomingEventItem(
            id: event.id,
            title: event.name,
            monthAbbrev: monthFormatter.string(from: event.startsAt).uppercased(),
            dayText: dayFormatter.string(from: event.startsAt),
            timeLine: timeFormatter.string(from: event.startsAt),
            goingCount: event.rsvpCounts?.going ?? 0
        )
    }

}

private extension String {
    var nilIfEmpty: String? {
        isEmpty ? nil : self
    }
}
