import SwiftUI
import UIKit
import CoreLocation
import MessageUI
import MapKit
import os
import Photos
import PhotosUI

private enum CircleNavigationRoute {
    static func circle(_ id: String) -> String { "circle:\(id)" }
    static func direct(_ id: String) -> String { "direct:\(id)" }
    static func directConversation(_ id: String) -> String { "directConversation:\(id)" }

    static func circleID(from route: String) -> String? {
        guard route.hasPrefix("circle:") else { return nil }
        return String(route.dropFirst("circle:".count))
    }

    static func directUserID(from route: String) -> String? {
        guard route.hasPrefix("direct:"), !route.hasPrefix("directConversation:") else { return nil }
        return String(route.dropFirst("direct:".count))
    }

    static func directConversationID(from route: String) -> String? {
        guard route.hasPrefix("directConversation:") else { return nil }
        return String(route.dropFirst("directConversation:".count))
    }
}

private enum SquadScreenTab: OttoTabItem {
    case squads
    case dms
    case invites

    var title: String {
        switch self {
        case .squads: return "Squads"
        case .dms: return "DMs"
        case .invites: return "Invites"
        }
    }
}

struct CirclesScreen: View {
    @EnvironmentObject private var appState: AppState
    @State private var isShowingAddCircle = false
    @State private var newCircleName = ""
    @State private var searchText = ""
    @State private var navigationPath: [String] = []
    @State private var selectedTab: SquadScreenTab = .squads
    @State private var showNewDMSheet = false
    @State private var pendingComposeDirectRecipientUserID: String?

    private var filteredCircles: [DriveCircle] {
        let trimmedSearch = searchText.trimmingCharacters(in: .whitespacesAndNewlines)
        let recencySortedCircles = appState.circlesSortedByRecentAccess(appState.circles)
        guard !trimmedSearch.isEmpty else { return recencySortedCircles }
        return recencySortedCircles.filter {
            $0.name.localizedCaseInsensitiveContains(trimmedSearch)
                || $0.subtitle.localizedCaseInsensitiveContains(trimmedSearch)
        }
    }

    private var allDirectContacts: [FriendLocation] {
        appState.circles
            .flatMap(\.members)
            .reduce(into: [String: FriendLocation]()) { partialResult, member in
                guard member.id != appState.currentUserID else { return }
                if partialResult[member.id] == nil {
                    partialResult[member.id] = member
                }
            }
            .values
            .sorted { lhs, rhs in
                let lhsDate = appState.directConversationsByUserID[lhs.id]?.lastMessageAt
                let rhsDate = appState.directConversationsByUserID[rhs.id]?.lastMessageAt
                switch (lhsDate, rhsDate) {
                case let (lhsDate?, rhsDate?) where lhsDate != rhsDate:
                    return lhsDate > rhsDate
                case (_?, nil):
                    return true
                case (nil, _?):
                    return false
                default:
                    return lhs.name.localizedCaseInsensitiveCompare(rhs.name) == .orderedAscending
                }
            }
    }

    private var directContacts: [FriendLocation] {
        let trimmedSearch = searchText.trimmingCharacters(in: .whitespacesAndNewlines)
        return allDirectContacts.filter { member in
            guard !trimmedSearch.isEmpty else { return true }
            return member.name.localizedCaseInsensitiveContains(trimmedSearch)
                || appState.sharedSquads(with: member.id).contains {
                    $0.name.localizedCaseInsensitiveContains(trimmedSearch)
                }
        }
    }

    private var filteredDirectConversations: [DirectConversationDTO] {
        let list = appState.sortedDirectConversations
        let trimmedSearch = searchText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedSearch.isEmpty else { return list }
        return list.filter { conv in
            guard let other = conv.otherUser else { return false }
            let name = other.displayName ?? ""
            let preview = dmPreviewSubtitle(conversation: conv)
            let squadHint = appState.sharedSquads(with: other.id).map(\.name).joined(separator: " ")
            return name.localizedCaseInsensitiveContains(trimmedSearch)
                || preview.localizedCaseInsensitiveContains(trimmedSearch)
                || squadHint.localizedCaseInsensitiveContains(trimmedSearch)
        }
    }

    private func dmPreviewSubtitle(conversation: DirectConversationDTO) -> String {
        guard let lm = conversation.lastMessage else { return "" }
        let me = appState.currentUserID
        if lm.senderUserId == me {
            return lm.hasImage && lm.bodyPreview.isEmpty ? "You: Photo" : "You: \(lm.bodyPreview)"
        }
        if lm.hasImage && lm.bodyPreview.isEmpty { return "Photo" }
        return lm.bodyPreview
    }

    private func friendLocationForUser(id: String) -> FriendLocation? {
        appState.circles.flatMap(\.members).first { $0.id == id }
    }

    private func hasTabIndicator(for tab: SquadScreenTab) -> Bool {
        switch tab {
        case .squads:
            return appState.unreadChatCountsByCircleID.values.contains { $0 > 0 }
        case .dms:
            return appState.unreadDirectMessageCountsByConversationID.values.contains { $0 > 0 }
        case .invites:
            return !appState.myCircleInvites.isEmpty
        }
    }

    private var searchPlaceholder: String {
        switch selectedTab {
        case .squads: return "Search your squads"
        case .dms: return "Search conversations"
        case .invites: return "Search invites"
        }
    }

    var body: some View {
        NavigationStack(path: $navigationPath) {
            VStack(spacing: 0) {
                VStack(alignment: .leading, spacing: OttoScreenChrome.stackSpacing) {
                    header
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, OttoScreenChrome.horizontalPadding)
                .padding(.top, OttoScreenChrome.topPadding)
                .padding(.bottom, 8)

                OttoTabbedPager(selectedTab: $selectedTab, mode: .paging) {
                    squadTabBar
                        .padding(.horizontal, OttoScreenChrome.horizontalPadding)
                } content: { tab in
                    ScrollView {
                        VStack(alignment: .leading, spacing: OttoScreenChrome.stackSpacing) {
                            if tab != .invites {
                                OttoSearchBar(text: $searchText, placeholder: searchPlaceholder, showsAction: false) {
                                    searchText = ""
                                }
                            }
                            content(for: tab)
                        }
                        .padding(.horizontal, OttoScreenChrome.horizontalPadding)
                        .padding(.top, OttoScreenChrome.stackSpacing)
                        .padding(.bottom, OttoScreenChrome.bottomPadding)
                    }
                    .scrollDismissesKeyboard(.interactively)
                    .refreshable {
                        await withTaskGroup(of: Void.self) { group in
                            group.addTask { await appState.refreshCircles() }
                            group.addTask { await appState.refreshMyCircleInvites() }
                            group.addTask { await appState.refreshDirectConversations() }
                        }
                        await appState.reconcileChatUnreadStateFromNetworkIfNeeded()
                    }
                }
            }
            .background(Color.black.ignoresSafeArea())
            .toolbar(.hidden, for: .navigationBar)
            .navigationDestination(for: String.self) { route in
                if let circleID = CircleNavigationRoute.circleID(from: route) {
                    CircleDetailScreen(circleID: circleID)
                        .id("circle-detail-\(circleID)")
                } else {
                    directMessageNavigationDestination(for: route)
                }
            }
            .onAppear {
                appState.refreshCirclesAsync()
                Task {
                    await appState.refreshDirectConversations()
                    await appState.refreshMyCircleInvites()
                }
                applyPendingNavigationFocusIfNeeded()
            }
            .onChange(of: appState.pendingCircleFocus) { _, _ in
                applyPendingNavigationFocusIfNeeded()
            }
            .onChange(of: appState.pendingSquadsInvitesFocus) { _, _ in
                applyPendingNavigationFocusIfNeeded()
            }
            .onChange(of: appState.pendingDirectMessageFocus) { _, _ in
                applyPendingNavigationFocusIfNeeded()
            }
            .onChange(of: appState.circles.count) { _, _ in
                applyPendingNavigationFocusIfNeeded()
            }
            .onReceive(NotificationCenter.default.publisher(for: .ottoCirclesTabReselected)) { _ in
                guard !isAtSquadsListRoot else { return }
                resetToSquadsListRoot()
            }
            .alert("Create Squad", isPresented: $isShowingAddCircle) {
                TextField("Squad name", text: $newCircleName)
                Button("Cancel", role: .cancel) {
                    newCircleName = ""
                }
                Button("Create") {
                    let name = newCircleName
                    newCircleName = ""
                    Task { await appState.createCircleOnServer(named: name) }
                }
            } message: {
                Text("Set up a new squad for a different crew.")
            }
            .sheet(isPresented: $showNewDMSheet, onDismiss: {
                guard let recipientUserID = pendingComposeDirectRecipientUserID else { return }
                pendingComposeDirectRecipientUserID = nil
                navigationPath.append(CircleNavigationRoute.direct(recipientUserID))
            }) {
                NewDMComposeSheet(isPresented: $showNewDMSheet) { recipientUserID in
                    pendingComposeDirectRecipientUserID = recipientUserID
                }
                .environmentObject(appState)
                .presentationBackground(Color(red: 0.025, green: 0.025, blue: 0.035))
            }
        }
    }

    @ViewBuilder
    private func content(for tab: SquadScreenTab) -> some View {
        switch tab {
        case .squads:
            squadsContent
        case .dms:
            directMessagesContent
        case .invites:
            invitesContent
        }
    }

    private var header: some View {
        switch selectedTab {
        case .squads:
            OttoScreenHeader(
                title: "Squads",
                actionSystemImage: "plus",
                actionAccessibilityLabel: "Add Squad"
            ) {
                isShowingAddCircle = true
            }
        case .dms:
            OttoScreenHeader(
                title: "Squads",
                actionSystemImage: "square.and.pencil",
                actionAccessibilityLabel: "New message"
            ) {
                showNewDMSheet = true
            }
        case .invites:
            OttoScreenHeader(title: "Squads")
        }
    }

    private var squadTabBar: some View {
        HStack(spacing: 0) {
            ForEach(Array(SquadScreenTab.allCases), id: \.self) { tab in
                Button {
                    selectedTab = tab
                    if tab == .invites {
                        Task { await appState.refreshMyCircleInvites() }
                    }
                } label: {
                    VStack(spacing: 10) {
                        HStack(spacing: 6) {
                            Text(tab.title)
                                .font(.caption.weight(.medium))
                            if tab == .invites, !appState.myCircleInvites.isEmpty {
                                Text("\(min(appState.myCircleInvites.count, 9))")
                                    .font(.system(size: 10, weight: .bold))
                                    .foregroundStyle(.white)
                                    .padding(.horizontal, 5)
                                    .padding(.vertical, 2)
                                    .background(Color.green)
                                    .clipShape(Capsule())
                            } else if hasTabIndicator(for: tab) {
                                Circle()
                                    .fill(Color.green)
                                    .frame(width: 7, height: 7)
                            }
                        }
                        .foregroundStyle(selectedTab == tab ? OttoScreenChrome.accentColor : Color.white.opacity(0.72))

                        Rectangle()
                            .fill(selectedTab == tab ? OttoScreenChrome.accentColor : Color.clear)
                            .frame(height: 2)
                    }
                    .frame(maxWidth: .infinity)
                }
                .buttonStyle(.plain)
            }
        }
        .overlay(alignment: .bottom) {
            Rectangle()
                .fill(Color.white.opacity(0.1))
                .frame(height: 1)
        }
    }

    @ViewBuilder
    private var squadsContent: some View {
        if appState.circles.isEmpty {
            VStack(spacing: 12) {
                if !appState.circlesFetchFailed {
                    createSquadListRow
                }
                if appState.circlesFetchFailed {
                    UnifiedEmptyStateView(
                        title: String(localized: "fetch_error_squads_title"),
                        message: String(localized: "fetch_error_refresh_body"),
                        systemImage: "exclamationmark.triangle",
                        actionTitle: String(localized: "fetch_error_refresh_action"),
                        action: {
                            Task { await appState.refreshCircles() }
                        }
                    )
                    .frame(minHeight: 320)
                } else {
                    UnifiedEmptyStateView(
                        title: "No squads",
                        message: "Pull down to refresh if you are expecting squads you already belong to.",
                        systemImage: "person.3"
                    )
                    .frame(minHeight: 320)
                }
            }
        } else if filteredCircles.isEmpty {
            UnifiedEmptyStateView(
                title: "No Matches",
                message: "Try another search.",
                systemImage: "person.3"
            )
            .frame(minHeight: 420)
        } else {
            LazyVStack(spacing: 12) {
                ForEach(filteredCircles) { circle in
                    NavigationLink(value: CircleNavigationRoute.circle(circle.id)) {
                        CircleRowCard(
                            circle: circle,
                            unreadCount: appState.unreadChatCountsByCircleID[circle.id] ?? 0
                        )
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }

    private var createSquadListRow: some View {
        CreateSquadListRow {
            isShowingAddCircle = true
        }
    }

    @ViewBuilder
    private var directMessagesContent: some View {
        if appState.circlesFetchFailed {
            UnifiedEmptyStateView(
                title: String(localized: "fetch_error_squads_title"),
                message: String(localized: "fetch_error_refresh_body"),
                systemImage: "exclamationmark.triangle",
                actionTitle: String(localized: "fetch_error_refresh_action"),
                action: {
                    Task { await appState.refreshCircles() }
                }
            )
            .frame(minHeight: 420)
        } else if appState.circles.isEmpty {
            UnifiedEmptyStateView(
                title: "No squads",
                message: "Join or create a Squad to message other members.",
                systemImage: "bubble.left.and.bubble.right"
            )
            .frame(minHeight: 420)
        } else if appState.sortedDirectConversations.isEmpty {
            UnifiedEmptyStateView(
                title: "No conversations yet",
                message: "Tap the compose button to start a private message with someone you share a Squad with.",
                systemImage: "bubble.left.and.bubble.right"
            )
            .frame(minHeight: 420)
        } else if filteredDirectConversations.isEmpty {
            UnifiedEmptyStateView(
                title: "No matches",
                message: "Try another search.",
                systemImage: "bubble.left.and.bubble.right"
            )
            .frame(minHeight: 420)
        } else {
            LazyVStack(spacing: 12) {
                ForEach(filteredDirectConversations) { conversation in
                    if let other = conversation.otherUser {
                        NavigationLink(value: CircleNavigationRoute.directConversation(conversation.id)) {
                            DirectConversationInboxRowCard(
                                other: other,
                                sharedSquads: appState.sharedSquads(with: other.id),
                                previewLine: dmPreviewSubtitle(conversation: conversation),
                                lastMessageAt: conversation.lastMessageAt,
                                unreadCount: appState.unreadDirectCount(for: conversation),
                                presenceFriend: friendLocationForUser(id: other.id)
                            )
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
    }

    @ViewBuilder
    private var invitesContent: some View {
        if appState.myCircleInvites.isEmpty {
            UnifiedEmptyStateView(
                title: "No Invites",
                message: "Squad invites will show up here when someone asks you to join. Pull down to refresh or tap below to check now.",
                systemImage: "envelope.open",
                actionTitle: "Refresh",
                action: {
                    Task { await appState.refreshMyCircleInvites() }
                }
            )
            .frame(minHeight: 420)
        } else {
            LazyVStack(spacing: 12) {
                ForEach(appState.myCircleInvites) { invite in
                    squadInviteCard(invite)
                }
            }
        }
    }

    private func squadInviteCard(_ invite: CircleInviteDTO) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .top, spacing: 12) {
                Image(systemName: "person.3.fill")
                    .font(.headline.weight(.semibold))
                    .foregroundStyle(.purple)
                    .frame(width: 42, height: 42)
                    .background(Color.purple.opacity(0.16))
                    .clipShape(Circle())

                VStack(alignment: .leading, spacing: 4) {
                    Text("Invited to \(invite.circle?.name ?? "a squad")")
                        .font(.headline.weight(.semibold))
                        .foregroundStyle(.white)
                    if let inviter = invite.invitedByUser {
                        Text("\(inviter.displayName) invited you")
                            .font(.subheadline)
                            .foregroundStyle(.white.opacity(0.68))
                    }
                    Text("Sent to \(invite.phoneNumber)")
                        .font(.caption)
                        .foregroundStyle(.white.opacity(0.46))
                }

                Spacer(minLength: 0)
            }

            HStack(spacing: 10) {
                Button("Decline") {
                    Task { await appState.respondToCircleInvite(inviteID: invite.id, accept: false) }
                }
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(.white.opacity(0.84))
                .frame(maxWidth: .infinity)
                .padding(.vertical, 11)
                .background(Color.white.opacity(0.08))
                .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))

                Button("Accept") {
                    Task { await appState.respondToCircleInvite(inviteID: invite.id, accept: true) }
                }
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(.white)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 11)
                .background(Color.purple)
                .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
            }
        }
        .padding(14)
        .background(Color.white.opacity(0.05))
        .overlay(
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .stroke(Color.white.opacity(0.12), lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
    }

    private func navigationShowsCircle(_ circleID: String) -> Bool {
        navigationPath.contains { CircleNavigationRoute.circleID(from: $0) == circleID }
    }

    private var isAtSquadsListRoot: Bool {
        navigationPath.isEmpty && selectedTab == .squads
    }

    private func resetToSquadsListRoot() {
        navigationPath = []
        selectedTab = .squads
        showNewDMSheet = false
        pendingComposeDirectRecipientUserID = nil
    }

    private func applyPendingNavigationFocusIfNeeded() {
        if appState.pendingSquadsInvitesFocus != nil {
            _ = appState.consumePendingSquadsInvitesFocus()
            selectedTab = .invites
            navigationPath = []
            Task { await appState.refreshMyCircleInvites() }
            return
        }

        if let focus = appState.pendingCircleFocus,
           appState.circles.contains(where: { $0.id == focus.circleID }) {
            if focus.openChatTab, navigationShowsCircle(focus.circleID) {
                return
            }
            _ = appState.consumePendingCircleFocus()
            selectedTab = .squads
            Task { @MainActor in
                await Task.yield()
                navigationPath = [CircleNavigationRoute.circle(focus.circleID)]
            }
            return
        }

        if let focus = appState.pendingDirectMessageFocus {
            _ = appState.consumePendingDirectMessageFocus()
            selectedTab = .dms

            if let cid = focus.conversationID?.trimmingCharacters(in: .whitespacesAndNewlines),
               !cid.isEmpty,
               let conv = appState.directConversation(conversationID: cid),
               conv.otherUser != nil {
                Task { @MainActor in
                    await Task.yield()
                    navigationPath = [CircleNavigationRoute.directConversation(cid)]
                }
                return
            }

            if let uid = focus.userID?.trimmingCharacters(in: .whitespacesAndNewlines),
               !uid.isEmpty,
               appState.canDirectMessage(userID: uid) {
                Task { @MainActor in
                    await Task.yield()
                    navigationPath = [CircleNavigationRoute.direct(uid)]
                }
            }
        }
    }

    @ViewBuilder
    private func directMessageNavigationDestination(for route: String) -> some View {
        if let conversationID = CircleNavigationRoute.directConversationID(from: route) {
            if let conv = appState.directConversation(conversationID: conversationID),
               let other = conv.otherUser {
                DirectChatView(
                    recipientUserID: other.id,
                    recipientName: other.displayName ?? "Friend",
                    recipientAvatarURL: other.avatarUrl,
                    recipientAccentColor: MapAccentPalette.resolvedColor(mapAccentKey: other.mapAccentKey, userId: other.id),
                    prefetchedConversation: conv
                )
                .id("direct-chat-\(conversationID)")
                .dmNavBranchLogged("directConversation_ok", route: route)
            } else if let recipientUserID = appState.directConversationRecipientUserID(conversationID: conversationID) {
                directChatView(
                    forRecipientUserID: recipientUserID,
                    prefetchedConversation: appState.directConversation(conversationID: conversationID),
                    route: route,
                    navBranch: "directConversation_fallback_user",
                    navDetail: "recipient=\(DMNavigationDiagnostics.idPrefix(recipientUserID))"
                )
            } else {
                dmNavigationUnavailableFallback(route: route)
                    .onAppear {
                        DMNavigationDiagnostics.logDirectConversationMiss(
                            route: route,
                            conversationID: conversationID,
                            appState: appState
                        )
                    }
            }
        } else if let userID = CircleNavigationRoute.directUserID(from: route) {
            directChatView(forRecipientUserID: userID, route: route)
        } else {
            dmNavigationUnavailableFallback(route: route)
        }
    }

    @ViewBuilder
    private func directChatView(
        forRecipientUserID userID: String,
        prefetchedConversation: DirectConversationDTO? = nil,
        route: String,
        navBranch: String = "direct_user_ok",
        navDetail: String = ""
    ) -> some View {
        if let contact = allDirectContacts.first(where: { $0.id == userID }) {
            DirectChatView(
                recipientUserID: contact.id,
                recipientName: contact.name,
                recipientAvatarURL: contact.avatarUrl,
                recipientAccentColor: contact.accentColor,
                prefetchedConversation: prefetchedConversation
            )
            .id("direct-chat-user-\(userID)")
            .dmNavBranchLogged(navBranch, route: route, detail: navDetail)
        } else if appState.canDirectMessage(userID: userID),
                  let conv = prefetchedConversation ?? appState.directConversationsByUserID[userID],
                  let other = conv.otherUser {
            DirectChatView(
                recipientUserID: other.id,
                recipientName: other.displayName ?? "Friend",
                recipientAvatarURL: other.avatarUrl,
                recipientAccentColor: MapAccentPalette.resolvedColor(mapAccentKey: other.mapAccentKey, userId: other.id),
                prefetchedConversation: conv
            )
            .id("direct-chat-\(conv.id)")
            .dmNavBranchLogged(navBranch, route: route, detail: navDetail)
        } else if appState.canDirectMessage(userID: userID) {
            DirectChatView(
                recipientUserID: userID,
                recipientName: "Friend",
                recipientAvatarURL: nil,
                recipientAccentColor: MapAccentPalette.resolvedColor(mapAccentKey: nil, userId: userID),
                prefetchedConversation: prefetchedConversation
            )
            .id("direct-chat-user-\(userID)")
            .dmNavBranchLogged(navBranch, route: route, detail: navDetail)
        } else {
            Text("DM unavailable")
                .foregroundStyle(.white)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(Color.black.ignoresSafeArea())
                .dmNavBranchLogged("direct_user_denied", route: route)
        }
    }

    private func dmNavigationUnavailableFallback(route: String) -> some View {
        DMNavigationUnavailableFallbackView(route: route)
    }
}

private struct DMNavigationUnavailableFallbackView: View {
    let route: String

    var body: some View {
        VStack(spacing: 8) {
            Text("Unavailable")
                .foregroundStyle(.white)
            #if DEBUG
            Text(DMNavigationDiagnostics.lastBranchDescription)
                .font(.caption)
                .foregroundStyle(.white.opacity(0.5))
                .multilineTextAlignment(.center)
                .padding(.horizontal, 24)
            #endif
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.black.ignoresSafeArea())
        .onAppear {
            DMNavigationDiagnostics.logNavigationBranch("unavailable_fallback", route: route)
        }
    }
}

private struct DirectConversationInboxRowCard: View {
    let other: DirectConversationDTO.UserSummaryDTO
    let sharedSquads: [DriveCircle]
    let previewLine: String
    let lastMessageAt: Date?
    var unreadCount = 0
    let presenceFriend: FriendLocation?

    private var titleName: String {
        let trimmed = other.displayName?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        return trimmed.isEmpty ? other.id : trimmed
    }

    private var squadSubtitle: String {
        let squadNames = sharedSquads.prefix(2).map(\.name).joined(separator: ", ")
        guard !squadNames.isEmpty else { return "Direct message" }
        if sharedSquads.count > 2 {
            return "\(squadNames) +\(sharedSquads.count - 2)"
        }
        return squadNames
    }

    var body: some View {
        HStack(spacing: 12) {
            AvatarView(
                name: titleName,
                avatarUrl: other.avatarUrl,
                size: 48,
                accentColor: MapAccentPalette.resolvedColor(mapAccentKey: other.mapAccentKey, userId: other.id),
                accentRingWidth: 2,
                whiteRingWidth: 0
            )
            .overlay(alignment: .bottomTrailing) {
                Circle()
                    .fill((presenceFriend?.presenceStatus.color) ?? Color.white.opacity(0.22))
                    .frame(width: 10, height: 10)
                    .overlay(Circle().stroke(Color.black, lineWidth: 2))
            }

            VStack(alignment: .leading, spacing: 4) {
                Text(titleName)
                    .font(.system(size: 17, weight: .semibold, design: .rounded))
                    .foregroundStyle(.white)
                    .lineLimit(1)
                Text(previewLine.isEmpty ? squadSubtitle : previewLine)
                    .font(.caption)
                    .foregroundStyle(.white.opacity(0.65))
                    .lineLimit(1)
            }

            Spacer()

            if let lastMessageAt {
                Text(Self.timeText(lastMessageAt))
                    .font(.caption2.weight(.medium))
                    .foregroundStyle(.white.opacity(0.45))
            }

            UnreadCountBadge(count: unreadCount)

            Image(systemName: "chevron.right")
                .font(.caption.weight(.bold))
                .foregroundStyle(.white.opacity(0.45))
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 14)
        .background(Color.white.opacity(0.055))
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .stroke(Color.white.opacity(0.1), lineWidth: 1)
        }
    }

    private static func timeText(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.timeStyle = .short
        formatter.dateStyle = .none
        return formatter.string(from: date)
    }
}

private struct NewDMComposeSheet: View {
    @EnvironmentObject private var appState: AppState
    @Binding var isPresented: Bool
    var onOpenConversation: (String) -> Void

    @State private var query = ""
    @State private var nextBusy = false
    @State private var composeError: String?

    private var squadMateFriends: [FriendLocation] {
        let myId = appState.currentUserID
        var seen = Set<String>()
        var out: [FriendLocation] = []
        for circle in appState.circles {
            for m in circle.members {
                guard m.id != myId else { continue }
                if seen.insert(m.id).inserted {
                    out.append(m)
                }
            }
        }
        return out.sorted { $0.name.localizedCaseInsensitiveCompare($1.name) == .orderedAscending }
    }

    private var filteredSquadMates: [FriendLocation] {
        let q = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !q.isEmpty else { return squadMateFriends }
        return squadMateFriends.filter { m in
            m.name.localizedCaseInsensitiveContains(q)
                || appState.sharedSquads(with: m.id).contains { $0.name.localizedCaseInsensitiveContains(q) }
        }
    }

    private var recentConversations: [DirectConversationDTO] {
        appState.sortedDirectConversations
    }

    private func previewForInbox(conversation: DirectConversationDTO) -> String {
        guard let lm = conversation.lastMessage else { return "" }
        let me = appState.currentUserID
        if lm.senderUserId == me {
            return lm.hasImage && lm.bodyPreview.isEmpty ? "You: Photo" : "You: \(lm.bodyPreview)"
        }
        if lm.hasImage && lm.bodyPreview.isEmpty { return "Photo" }
        return lm.bodyPreview
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                OttoSearchBar(text: $query, placeholder: "Search people…", showsAction: false) {
                    query = ""
                }
                .disabled(nextBusy)
                .padding(.horizontal, OttoScreenChrome.horizontalPadding)
                .padding(.vertical, 10)

                List {
                    Section {
                        if filteredSquadMates.isEmpty {
                            Text("No matches")
                                .foregroundStyle(.white.opacity(0.55))
                                .listRowBackground(Color.white.opacity(0.04))
                        } else {
                            ForEach(filteredSquadMates) { mate in
                                Button {
                                    Task { await openNewDm(withRecipientUserId: mate.id) }
                                } label: {
                                    HStack(spacing: 12) {
                                        AvatarView(
                                            name: mate.name,
                                            avatarUrl: mate.avatarUrl,
                                            size: 44,
                                            accentColor: mate.accentColor,
                                            accentRingWidth: 2,
                                            whiteRingWidth: 0
                                        )
                                        VStack(alignment: .leading, spacing: 4) {
                                            Text(mate.name)
                                                .font(.body.weight(.semibold))
                                                .foregroundStyle(.white)
                                            let squads = appState.sharedSquads(with: mate.id)
                                            let squadNames = squads.prefix(2).map(\.name).joined(separator: ", ")
                                            if !squadNames.isEmpty {
                                                Text(squadNames + (squads.count > 2 ? " +\(squads.count - 2)" : ""))
                                                    .font(.caption)
                                                    .foregroundStyle(.white.opacity(0.55))
                                                    .lineLimit(1)
                                            }
                                        }
                                        Spacer()
                                        Image(systemName: "chevron.right")
                                            .font(.caption.weight(.bold))
                                            .foregroundStyle(.white.opacity(0.35))
                                    }
                                    .contentShape(Rectangle())
                                }
                                .buttonStyle(.plain)
                                .disabled(nextBusy)
                                .listRowBackground(Color.white.opacity(0.04))
                            }
                        }
                    } header: {
                        Text("Suggested")
                            .foregroundStyle(.white.opacity(0.72))
                    }

                    Section {
                        if recentConversations.isEmpty {
                            Text("No recent conversations")
                                .foregroundStyle(.white.opacity(0.45))
                                .listRowBackground(Color.white.opacity(0.04))
                        } else {
                            ForEach(recentConversations) { conv in
                                if let other = conv.otherUser {
                                    Button {
                                        onOpenConversation(other.id)
                                        isPresented = false
                                    } label: {
                                        HStack(spacing: 12) {
                                            AvatarView(
                                                name: other.displayName ?? other.id,
                                                avatarUrl: other.avatarUrl,
                                                size: 44,
                                                accentColor: MapAccentPalette.resolvedColor(mapAccentKey: other.mapAccentKey, userId: other.id),
                                                accentRingWidth: 2,
                                                whiteRingWidth: 0
                                            )
                                            VStack(alignment: .leading, spacing: 4) {
                                                Text(other.displayName ?? other.id)
                                                    .font(.body.weight(.semibold))
                                                    .foregroundStyle(.white)
                                                Text(previewForInbox(conversation: conv))
                                                    .font(.caption)
                                                    .foregroundStyle(.white.opacity(0.55))
                                                    .lineLimit(1)
                                            }
                                            Spacer()
                                            Image(systemName: "chevron.right")
                                                .font(.caption.weight(.bold))
                                                .foregroundStyle(.white.opacity(0.35))
                                        }
                                        .contentShape(Rectangle())
                                    }
                                    .buttonStyle(.plain)
                                    .disabled(nextBusy)
                                    .listRowBackground(Color.white.opacity(0.04))
                                }
                            }
                        }
                    } header: {
                        Text("Recent")
                            .foregroundStyle(.white.opacity(0.72))
                    }
                }
                .listStyle(.plain)
                .scrollContentBackground(.hidden)
            }
            .background(Color.black.ignoresSafeArea())
            .navigationTitle("New DM")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") {
                        isPresented = false
                    }
                    .foregroundStyle(.white)
                    .disabled(nextBusy)
                }
            }
            .alert(
                "Error",
                isPresented: Binding(
                    get: { composeError != nil },
                    set: { if !$0 { composeError = nil } }
                )
            ) {
                Button("OK", role: .cancel) { composeError = nil }
            } message: {
                Text(composeError ?? "")
            }
        }
    }

    @MainActor
    private func openNewDm(withRecipientUserId rawId: String) async {
        let uid = rawId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !uid.isEmpty else { return }
        guard appState.canDirectMessage(userID: uid) else {
            composeError = "You can only message people who share a Squad with you."
            return
        }
        let attempt = DMNavigationDiagnostics.nextComposeAttempt()
        nextBusy = true
        defer { nextBusy = false }
        do {
            let conv = try await APIClient.shared.getOrCreateDirectConversation(recipientUserId: uid)
            DMNavigationDiagnostics.logOpenNewDmSuccess(attempt: attempt, recipientUserId: uid, conversation: conv)
            appState.registerDirectConversation(conv)
            let route = CircleNavigationRoute.direct(uid)
            let cacheHit = appState.directConversation(conversationID: conv.id) != nil
            DMNavigationDiagnostics.logOpenNewDmNavigate(
                attempt: attempt,
                route: route,
                cacheHit: cacheHit,
                sheetPresented: isPresented
            )
            onOpenConversation(uid)
            isPresented = false
        } catch {
            composeError = "Could not start this conversation. Try again."
        }
    }
}

private struct DirectContactRowCard: View {
    let contact: FriendLocation
    let sharedSquads: [DriveCircle]
    let lastMessageAt: Date?
    var unreadCount = 0

    private var subtitle: String {
        let squadNames = sharedSquads.prefix(2).map(\.name).joined(separator: ", ")
        guard !squadNames.isEmpty else { return "Direct message" }
        if sharedSquads.count > 2 {
            return "\(squadNames) +\(sharedSquads.count - 2)"
        }
        return squadNames
    }

    var body: some View {
        HStack(spacing: 12) {
            AvatarView(
                name: contact.name,
                avatarUrl: contact.avatarUrl,
                size: 48,
                accentColor: contact.accentColor,
                accentRingWidth: 2,
                whiteRingWidth: 0
            )
            .overlay(alignment: .bottomTrailing) {
                Circle()
                    .fill(contact.presenceStatus.color)
                    .frame(width: 10, height: 10)
                    .overlay(Circle().stroke(Color.black, lineWidth: 2))
            }

            VStack(alignment: .leading, spacing: 4) {
                Text(contact.name)
                    .font(.system(size: 17, weight: .semibold, design: .rounded))
                    .foregroundStyle(.white)
                    .lineLimit(1)
                Text(subtitle)
                    .font(.caption)
                    .foregroundStyle(.white.opacity(0.65))
                    .lineLimit(1)
            }

            Spacer()

            if let lastMessageAt {
                Text(Self.timeText(lastMessageAt))
                    .font(.caption2.weight(.medium))
                    .foregroundStyle(.white.opacity(0.45))
            }

            UnreadCountBadge(count: unreadCount)

            Image(systemName: "chevron.right")
                .font(.caption.weight(.bold))
                .foregroundStyle(.white.opacity(0.45))
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 14)
        .background(Color.white.opacity(0.055))
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .stroke(Color.white.opacity(0.1), lineWidth: 1)
        }
    }

    private static func timeText(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.timeStyle = .short
        formatter.dateStyle = .none
        return formatter.string(from: date)
    }
}

private struct EventEditorFormContent: View {
    @Binding var photoPickerItem: PhotosPickerItem?
    let hasSelectedImage: Bool
    let hasExistingImage: Bool
    @Binding var name: String
    @Binding var location: String
    @Binding var streetAddress: String
    @Binding var city: String
    @Binding var region: String
    @Binding var postalCode: String
    @Binding var showAddressFields: Bool
    @Binding var startsAt: Date
    @Binding var endsAt: Date
    @Binding var description: String
    let eventFooter: String

    var body: some View {
        Section("Image") {
            PhotosPicker(selection: $photoPickerItem, matching: .images) {
                HStack {
                    Label(hasSelectedImage ? "Change event image" : "Choose event image", systemImage: "photo")
                    Spacer()
                    Image(systemName: "chevron.right")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(.secondary)
                }
            }
            if hasSelectedImage {
                Text("Image selected")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            } else if hasExistingImage {
                Text("Current event image will stay unless you choose a new one.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }

        Section {
            TextField("Event name", text: $name)
                .textInputAutocapitalization(.words)
            TextField("Venue / location name (optional)", text: $location)
                .textInputAutocapitalization(.words)
            if showAddressFields {
                TextField("Street address (optional)", text: $streetAddress)
                    .textInputAutocapitalization(.words)
                TextField("City", text: $city)
                    .textInputAutocapitalization(.words)
                TextField("State / region", text: $region)
                    .textInputAutocapitalization(.words)
                TextField("Postal code", text: $postalCode)
                    .textInputAutocapitalization(.characters)
            } else {
                Button {
                    showAddressFields = true
                } label: {
                    Label("Add address", systemImage: "mappin.and.ellipse")
                }
            }
            DatePicker("Starts", selection: $startsAt, displayedComponents: [.date, .hourAndMinute])
            DatePicker("Ends", selection: $endsAt, displayedComponents: [.date, .hourAndMinute])
        } header: {
            Text("Event")
        } footer: {
            Text(eventFooter)
        }

        Section("Details") {
            TextField("Description", text: $description, axis: .vertical)
                .lineLimit(3...6)
                .textInputAutocapitalization(.sentences)
        }
    }
}

struct AddSquadEventSheet: View {
    @EnvironmentObject private var appState: AppState
    @Environment(\.dismiss) private var dismiss
    /// Squad id for sharing new events into chat; nil skips the share prompt (e.g. edit-only contexts).
    var circleID: String?
    let circleName: String
    var event: EventDTO?
    var onDelete: (() async throws -> Void)?
    /// When set with `circleID`, after creating an event the sheet asks whether to post it to squad chat.
    var onShareCreatedEventInChat: (@MainActor (EventDTO) async throws -> Void)?
    /// When set, newly created events return to the chat composer as a pending attachment (no share prompt).
    var onCreatedForComposer: ((EventDTO) -> Void)?
    let onSave: (SquadEventSavePayload, @escaping SquadEventSheetSaveCompletion) -> Void

    private static var defaultStartsAt: Date {
        Calendar.current.date(byAdding: .hour, value: 1, to: Date()) ?? Date()
    }

    @State private var name: String
    @State private var description: String
    @State private var startsAt: Date
    @State private var endsAt: Date
    @State private var location: String
    @State private var streetAddress: String
    @State private var city: String
    @State private var region: String
    @State private var postalCode: String
    @State private var showAddressFields: Bool
    @State private var photoPickerItem: PhotosPickerItem?
    @State private var eventPhotoToCrop: UIImage?
    @State private var selectedImageData: Data?
    @State private var isSaving = false
    @State private var errorMessage: String?
    @State private var isConfirmingDelete = false
    @State private var pendingShareAfterCreate: EventDTO?
    @State private var isSharingCreatedEventToChat = false

    init(
        circleID: String? = nil,
        circleName: String,
        event: EventDTO? = nil,
        onDelete: (() async throws -> Void)? = nil,
        onShareCreatedEventInChat: (@MainActor (EventDTO) async throws -> Void)? = nil,
        onCreatedForComposer: ((EventDTO) -> Void)? = nil,
        onSave: @escaping (SquadEventSavePayload, @escaping SquadEventSheetSaveCompletion) -> Void
    ) {
        self.circleID = circleID
        self.circleName = circleName
        self.event = event
        self.onDelete = onDelete
        self.onShareCreatedEventInChat = onShareCreatedEventInChat
        self.onCreatedForComposer = onCreatedForComposer
        self.onSave = onSave
        _name = State(initialValue: event?.name ?? "")
        _description = State(initialValue: event?.description ?? "")
        _startsAt = State(initialValue: event?.startsAt ?? Self.defaultStartsAt)
        _endsAt = State(initialValue: event?.endsAt ?? (event?.startsAt.addingTimeInterval(7200) ?? Self.defaultStartsAt.addingTimeInterval(7200)))
        _location = State(initialValue: event?.address?.label ?? "")
        _streetAddress = State(initialValue: event?.address?.street1 ?? "")
        _city = State(initialValue: event?.address?.city ?? "")
        _region = State(initialValue: event?.address?.region ?? "")
        _postalCode = State(initialValue: event?.address?.postalCode ?? "")
        let hasAddressDetails =
            event?.address?.street1?.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty == false ||
            event?.address?.city?.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty == false ||
            event?.address?.region?.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty == false ||
            event?.address?.postalCode?.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty == false
        _showAddressFields = State(initialValue: hasAddressDetails)
    }

    private var canCreate: Bool {
        !name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && !isSaving && endsAt > startsAt
    }

    private var isEditing: Bool {
        event != nil
    }

    var body: some View {
        NavigationStack {
            Form {
                EventEditorFormContent(
                    photoPickerItem: $photoPickerItem,
                    hasSelectedImage: selectedImageData != nil,
                    hasExistingImage: event?.bannerImage?.url != nil,
                    name: $name,
                    location: $location,
                    streetAddress: $streetAddress,
                    city: $city,
                    region: $region,
                    postalCode: $postalCode,
                    showAddressFields: $showAddressFields,
                    startsAt: $startsAt,
                    endsAt: $endsAt,
                    description: $description,
                    eventFooter:
                        "This event is private to \(circleName) and will not appear on the public website. " +
                        "If you add an address, we try to place a map pin for check-in (you can still create the event if lookup misses)."
                )

                if let errorMessage {
                    Section {
                        Text(errorMessage)
                            .foregroundStyle(.red)
                    }
                }

                if isEditing, onDelete != nil {
                    Section {
                        Button("Delete Event", role: .destructive) {
                            isConfirmingDelete = true
                        }
                        .disabled(isSaving)
                    }
                }
            }
            .navigationTitle(isEditing ? "Edit Event" : "Add Event")
            .navigationBarTitleDisplayMode(.inline)
            .onChange(of: startsAt) { _, newStart in
                if endsAt <= newStart {
                    endsAt = newStart.addingTimeInterval(7200)
                }
            }
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                        .disabled(isSaving)
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(isSaving ? "Saving..." : (isEditing ? "Save" : "Create")) {
                        Task { await create() }
                    }
                    .disabled(!canCreate)
                }
            }
            .alert("Delete event?", isPresented: $isConfirmingDelete) {
                Button("Cancel", role: .cancel) {}
                Button("Delete", role: .destructive) {
                    Task { await deleteEvent() }
                }
            } message: {
                Text("This removes the event, RSVPs, and check-ins for everyone in the squad.")
            }
            .fullScreenCover(isPresented: Binding(
                get: { eventPhotoToCrop != nil },
                set: { if !$0 { eventPhotoToCrop = nil } }
            )) {
                if let image = eventPhotoToCrop {
                    OttoImageCropperSheet(
                        image: image,
                        cropAspect: 16 / 9,
                        onComplete: { jpeg in
                            eventPhotoToCrop = nil
                            selectedImageData = jpeg
                        },
                        onCancel: { eventPhotoToCrop = nil }
                    )
                }
            }
            .onChange(of: photoPickerItem) { _, newItem in
                Task {
                    guard let newItem else { return }
                    if let data = try? await newItem.loadTransferable(type: Data.self),
                       let ui = UIImage(data: data) {
                        await MainActor.run {
                            eventPhotoToCrop = ui
                            photoPickerItem = nil
                        }
                    }
                }
            }
        }
        .overlay {
            if pendingShareAfterCreate != nil {
                OttoCenteredChoiceDialog(
                    isBusy: isSharingCreatedEventToChat,
                    onDismissUnconfirmed: {
                        if !isSharingCreatedEventToChat {
                            pendingShareAfterCreate = nil
                            dismiss()
                        }
                    },
                    hero: { OttoSquadChatShareHeroGraphic() },
                    title: shareNewEventWithSquadPromptTitle(),
                    message: Text("Post this event in your squad chat so everyone sees it in the conversation."),
                    primaryTitle: "Share in chat",
                    primaryBusyTitle: "Sharing…",
                    primarySystemImage: "ellipsis.bubble.fill",
                    onPrimary: { Task { await confirmShareCreatedEventInChat() } },
                    secondaryTitle: "Not now",
                    secondarySystemImage: "person.3.fill",
                    footerMessage: "This won't post anywhere publicly. It's only visible in your squad chat."
                )
            }
        }
        .presentationDetents([.large])
    }

    private func shareNewEventWithSquadPromptTitle() -> Text {
        let squadTitle = circleName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "Squad" : circleName
        return Text("Share with \(squadTitle)?")
    }

    private func create() async {
        let trimmedName = String(name.trimmingCharacters(in: .whitespacesAndNewlines))
        let trimmedDescription = String(description.trimmingCharacters(in: .whitespacesAndNewlines))
        let trimmedLocation = String(location.trimmingCharacters(in: .whitespacesAndNewlines))
        let trimmedStreetAddress = String(streetAddress.trimmingCharacters(in: .whitespacesAndNewlines))
        let trimmedCity = String(city.trimmingCharacters(in: .whitespacesAndNewlines))
        let trimmedRegion = String(region.trimmingCharacters(in: .whitespacesAndNewlines))
        let trimmedPostalCode = String(postalCode.trimmingCharacters(in: .whitespacesAndNewlines))
        let startCopy = startsAt
        let endCopy = endsAt
        let imageCopy = selectedImageData.map { Data($0) }
        guard !trimmedName.isEmpty, !isSaving, endCopy > startCopy else { return }
        isSaving = true
        errorMessage = nil
        defer { isSaving = false }

        do {
            let payload = SquadEventSavePayload(
                name: trimmedName,
                description: trimmedDescription,
                startsAt: startCopy,
                endsAt: endCopy,
                location: trimmedLocation,
                streetAddress: trimmedStreetAddress,
                city: trimmedCity,
                region: trimmedRegion,
                postalCode: trimmedPostalCode,
                imageData: imageCopy
            )
            let payloadPtr = UInt(bitPattern: Unmanaged.passUnretained(payload).toOpaque())
            OttoLog.squadEvent.debug(
                "AddSquadEventSheet.create invoke onSave editing=\(isEditing, privacy: .public) payloadPtr=0x\(String(payloadPtr, radix: 16, uppercase: false), privacy: .public) nameLen=\(trimmedName.count, privacy: .public) descLen=\(trimmedDescription.count, privacy: .public) locLen=\(trimmedLocation.count, privacy: .public) imageBytes=\(imageCopy?.count ?? 0, privacy: .public) start=\(startCopy.timeIntervalSince1970, privacy: .public) end=\(endCopy.timeIntervalSince1970, privacy: .public)"
            )
            let outcome = try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<SquadEventSheetSaveOutcome, Error>) in
                onSave(payload) { result in
                    switch result {
                    case .success(let outcome):
                        continuation.resume(returning: outcome)
                    case .failure(let error):
                        continuation.resume(throwing: error)
                    }
                }
            }
            OttoLog.squadEvent.debug("AddSquadEventSheet.create onSave finished OK")
            switch outcome {
            case .updated:
                dismiss()
            case .created(let created):
                if let attach = onCreatedForComposer {
                    attach(created)
                    dismiss()
                    return
                }
                let trimmedCircle = circleID?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
                let shouldOfferShare =
                    !trimmedCircle.isEmpty && onShareCreatedEventInChat != nil && !isEditing
                if shouldOfferShare {
                    pendingShareAfterCreate = created
                } else {
                    dismiss()
                }
            }
        } catch {
            OttoLog.squadEvent.error("AddSquadEventSheet.create onSave error: \(String(describing: error), privacy: .public)")
            errorMessage = "Couldn't save this event. Try again."
        }
    }

    private func confirmShareCreatedEventInChat() async {
        guard let created = pendingShareAfterCreate else {
            dismiss()
            return
        }
        guard let share = onShareCreatedEventInChat else {
            pendingShareAfterCreate = nil
            dismiss()
            return
        }
        guard !isSharingCreatedEventToChat else { return }
        isSharingCreatedEventToChat = true
        defer { isSharingCreatedEventToChat = false }
        do {
            try await share(created)
            pendingShareAfterCreate = nil
            dismiss()
        } catch {
            OttoLog.squadEvent.error("AddSquadEventSheet share to chat failed: \(String(describing: error), privacy: .public)")
            appState.presentUserToast(
                text: "Couldn't post to squad chat. You can share the event from its detail screen.",
                systemImage: "exclamationmark.triangle.fill"
            )
        }
    }

    private func deleteEvent() async {
        guard let onDelete, !isSaving else {
            OttoLog.squadEvent.debug("AddSquadEventSheet.deleteEvent skip (no handler or already saving)")
            return
        }
        isSaving = true
        errorMessage = nil
        defer { isSaving = false }
        OttoLog.squadEvent.debug("AddSquadEventSheet.deleteEvent begin")
        do {
            try await onDelete()
            OttoLog.squadEvent.debug("AddSquadEventSheet.deleteEvent OK")
            dismiss()
        } catch {
            OttoLog.squadEvent.error("AddSquadEventSheet.deleteEvent error: \(String(describing: error), privacy: .public)")
            appState.presentDeleteFailedToast(for: "event")
        }
    }
}

private extension String {
    var nilIfEmpty: String? {
        isEmpty ? nil : self
    }
}

/// Single item-backed sheet for event detail. Multiple `.sheet(item:)` on one view is unreliable in SwiftUI (often only the last presents).
private enum CircleDetailEventPresentation: Identifiable {
    case attachment(CircleChatMessageDTO.EventAttachmentDTO)
    case rosterEvent(EventDTO)

    var id: String {
        switch self {
        case .attachment(let a): return "att-\(a.eventId)"
        case .rosterEvent(let e): return "rost-\(e.id)"
        }
    }
}

private struct SquadChatMessageRow<EventCard: View, DriveCard: View, PlaceCard: View>: View {
    let squadCircleId: String
    let circleMembers: [FriendLocation]
    let message: CircleChatMessageDTO
    let currentUserID: String
    let mentionDisplayNameByUserId: [String: String]
    var localVideoThumbnail: UIImage? = nil
    var videoUploadProgress: Double? = nil
    var videoUploadPhase: ChatVideoUploadCoordinator.Phase? = nil
    @Binding var longPressChatMessage: CircleChatMessageDTO?
    @Binding var reactionsDetailMessage: CircleChatMessageDTO?
    @Binding var suppressAttachmentNavigationForMessageID: String?
    @Binding var longPressChromeLiftY: CGFloat
    @Binding var presentedEventDetail: CircleDetailEventPresentation?
    let onAvatarTap: () -> Void
    let onMentionTap: (String) -> Void
    let onDoubleTapHeartReaction: (CircleChatMessageDTO) -> Void
    let onJumpToQuotedMessage: (String) -> Void
    var onRemovePendingMessage: ((String) -> Void)? = nil
    let reportsFrame: Bool
    @ViewBuilder var eventCard: (Bool) -> EventCard
    @ViewBuilder var driveCard: (Bool) -> DriveCard
    @ViewBuilder var placeCard: (Bool) -> PlaceCard

    private func rowTimeText(_ date: Date) -> String {
        ChatRowTimeFormatter.string(from: date)
    }

    var body: some View {
        let senderName = message.sender?.displayName ?? circleMembers.first(where: { $0.id == message.resolvedSenderUserId })?.name ?? "Someone"
        let senderAccentColor = MapAccentPalette.resolvedColor(
            mapAccentKey: message.sender?.mapAccentKey,
            userId: message.resolvedSenderUserId
        )
        let isMine = message.resolvedSenderUserId == currentUserID
        let suppressEventAttachmentNav = suppressAttachmentNavigationForMessageID == message.id

        HStack(alignment: .top, spacing: isMine ? 8 : 12) {
            if isMine { Spacer(minLength: 44) }

            if !isMine {
                Button(action: onAvatarTap) {
                    AvatarView(
                        name: senderName,
                        avatarUrl: message.sender?.avatarUrl,
                        size: 34,
                        accentColor: senderAccentColor,
                        accentRingWidth: 2,
                        whiteRingWidth: 0
                    )
                }
                .buttonStyle(.plain)
            }

            VStack(alignment: isMine ? .trailing : .leading, spacing: isMine ? 4 : 8) {
                if !isMine {
                    HStack(spacing: 6) {
                        Text(senderName)
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(senderAccentColor)
                        Text(rowTimeText(message.createdAt))
                            .font(.caption2)
                            .foregroundStyle(.white.opacity(0.45))
                    }
                }

                VStack(alignment: isMine ? .trailing : .leading, spacing: 8) {
                    HStack(alignment: .top, spacing: 0) {
                        if isMine { Spacer(minLength: 0) }
                        ZStack {
                            ChatMessageTextBubble(
                                bodyText: message.body,
                                imageURLString: message.imageUrl,
                                videoAttachment: message.videoAttachment,
                                localVideoThumbnail: localVideoThumbnail,
                                videoUploadProgress: videoUploadProgress,
                                videoUploadPhase: videoUploadPhase,
                                isVideoUploadPending: message.id.hasPrefix("pending-"),
                                onCancelVideoUpload: message.id.hasPrefix("pending-") ? {
                                    onRemovePendingMessage?(message.id)
                                } : nil,
                                isMine: isMine,
                                replyQuote: ChatMessageReplyQuote(replyTo: message.replyTo),
                                messageId: message.id,
                                isTextSelectable: longPressChatMessage?.id == message.id,
                                onLongPress: {
                                    ChatMessageActionFeedback.dismissKeyboard()
                                    withAnimation(.easeOut(duration: 0.12)) {
                                        longPressChromeLiftY = 0
                                        longPressChatMessage = message
                                    }
                                    suppressAttachmentNavigationForMessageID = message.id
                                    let id = message.id
                                    Task { @MainActor in
                                        try? await Task.sleep(nanoseconds: ChatLongPressTiming.attachmentSuppressDelayNanoseconds)
                                        if suppressAttachmentNavigationForMessageID == id {
                                            suppressAttachmentNavigationForMessageID = nil
                                        }
                                    }
                                },
                                onDoubleTapHeart: {
                                    onDoubleTapHeartReaction(message)
                                },
                                onTapReplyQuote: message.replyTo != nil && !(message.replyToMessageId ?? "").isEmpty
                                    ? {
                                        guard let parentId = message.replyToMessageId,
                                              !parentId.isEmpty,
                                              parentId != message.id else { return }
                                        onJumpToQuotedMessage(parentId)
                                    }
                                    : nil,
                                onMentionTap: onMentionTap,
                                mentions: message.mentions,
                                mentionDisplayNameByUserId: mentionDisplayNameByUserId
                            )
                        }
                        .conditionalChatMessageFrameReporting(messageId: message.id, enabled: reportsFrame)
                        if !isMine { Spacer(minLength: 0) }
                    }
                    .frame(maxWidth: .infinity, alignment: isMine ? .trailing : .leading)

                    if ChatMessageTextBubble.messageHasRichTail(
                        linkPreview: message.linkPreview,
                        eventAttachment: message.eventAttachment,
                        driveAttachment: message.driveAttachment,
                        placeAttachment: message.placeAttachment
                    ) {
                        VStack(alignment: .leading, spacing: 8) {
                            ChatLinkPreviewCard(
                                preview: message.linkPreview,
                                messageId: message.id,
                                fixedWidth: ChatMessageTextBubble.standardLayoutWidth,
                                onOttoEventDeepLink: { ref in
                                    presentedEventDetail = .attachment(
                                        CircleChatMessageDTO.EventAttachmentDTO(
                                            inferredEventRef: ref,
                                            squadCircleId: squadCircleId
                                        )
                                    )
                                },
                                onLongPress: {
                                    ChatMessageActionFeedback.dismissKeyboard()
                                    withAnimation(.easeOut(duration: 0.12)) {
                                        longPressChromeLiftY = 0
                                        longPressChatMessage = message
                                    }
                                    suppressAttachmentNavigationForMessageID = message.id
                                    let id = message.id
                                    Task { @MainActor in
                                        try? await Task.sleep(nanoseconds: ChatLongPressTiming.attachmentSuppressDelayNanoseconds)
                                        if suppressAttachmentNavigationForMessageID == id {
                                            suppressAttachmentNavigationForMessageID = nil
                                        }
                                    }
                                },
                                onDoubleTapHeart: {
                                    onDoubleTapHeartReaction(message)
                                }
                            )
                            eventCard(suppressEventAttachmentNav)
                            driveCard(suppressEventAttachmentNav)
                            placeCard(suppressEventAttachmentNav)
                        }
                        .frame(
                            width: ChatMessageTextBubble.standardLayoutWidth,
                            alignment: .leading
                        )
                        .frame(maxWidth: .infinity, alignment: isMine ? .trailing : .leading)
                    }
                }

                if !message.reactions.isEmpty {
                    ChatMessageReactionsStrip(
                        reactions: message.reactions,
                        alignment: isMine ? .trailing : .leading,
                        onTap: {
                            reactionsDetailMessage = message
                        }
                    )
                }
            }

            if !isMine { Spacer(minLength: 44) }
        }
    }
}

/// Holds draft + @-mention state so keystrokes do not invalidate the squad chat message list (`ForEach`).
private struct SquadChatComposerIsland: View {
    @EnvironmentObject private var appState: AppState
    @EnvironmentObject private var locationService: LocationService

    let circleID: String
    let circleMembers: [FriendLocation]
    let squadDisplayName: String
    @ObservedObject var store: SquadChatThreadStore
    var composerFocused: FocusState<Bool>.Binding

    @State private var isMentionPickerVisible = false
    @State private var mentionPickerAnchorUTF16 = 0
    @State private var mentionPickerFilter = ""
    @State private var attachmentLimitAlertMessage: String?
    @State private var showComposerEventSheet = false
    @ObservedObject private var videoUploads = ChatVideoUploadCoordinator.shared

    private var canSendChat: Bool {
        if store.isSendingMessage { return false }
        if let editId = store.editingMessageId {
            let t = store.draft.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !t.isEmpty else { return false }
            let baseline =
                store.messages.first(where: { $0.id == editId })?.body
                    .trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            return t != baseline
        }
        let hasText = !store.draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        let hasAttachment = !store.pendingAttachments.isEmpty
        return hasText || hasAttachment
    }

    private var editingPreviewBody: String {
        guard let id = store.editingMessageId else { return "" }
        return store.messages.first(where: { $0.id == id })?.body ?? ""
    }

    var body: some View {
        VStack(spacing: 0) {
            squadChatMentionPickerOverlay
                .animation(.easeOut(duration: 0.18), value: isMentionPickerVisible)

            ChatComposerBar(
                placeholder: "Message \(squadDisplayName)...",
                text: $store.draft,
                isSending: store.isSendingMessage,
                canSend: canSendChat,
                showsAttachmentButton: store.editingMessageId == nil,
                enabledAttachmentActions: ChatComposerAttachmentAction.squadChatActions,
                pendingAttachments: $store.pendingAttachments,
                attachmentLimitAlertMessage: $attachmentLimitAlertMessage,
                onSend: {
                    Task { await sendChatMessage() }
                },
                onCreateEvent: {
                    showComposerEventSheet = true
                },
                composerFocused: composerFocused,
                replyToAuthorName: store.replyDraft.authorName,
                replyToSnippet: store.replyDraft.snippet,
                replyToAvatarURL: store.replyDraft.avatarURL,
                onCancelReply: store.replyDraft.messageId == nil
                    ? nil
                    : {
                        store.replyDraft = .empty
                    },
                onTapReplyTo: store.replyDraft.messageId == nil
                    ? nil
                    : {
                        composerFocused.wrappedValue = false
                        if let messageId = store.replyDraft.messageId {
                            store.jumpToQuotedMessage(messageId, appState: appState)
                        }
                    },
                isEditingMessage: store.editingMessageId != nil,
                editingPreviewText: store.editingMessageId != nil ? editingPreviewBody : nil,
                onCancelEditing: store.editingMessageId == nil
                    ? nil
                    : {
                        store.cancelEditingMessage(appState: appState, clearDraft: true)
                    },
                klipyCustomerId: appState.currentUserID
            )
            .environmentObject(locationService)
        }
        .sheet(isPresented: $showComposerEventSheet) {
            AddSquadEventSheet(
                circleID: circleID,
                circleName: squadDisplayName,
                onCreatedForComposer: { event in
                    attachCreatedEventToComposer(event)
                },
                onSave: { payload, completion in
                    Task { @MainActor in
                        do {
                            let event = try await SquadEventSaveCoordinator.createEventInCircle(
                                appState: appState,
                                circleID: circleID,
                                payload: payload
                            )
                            completion(.success(.created(event)))
                        } catch {
                            completion(.failure(error))
                        }
                    }
                }
            )
            .environmentObject(appState)
        }
        .onChange(of: store.editingMessageId) { _, new in
            if new != nil {
                composerFocused.wrappedValue = true
            }
        }
        .onChange(of: store.draft) { _, new in
            refreshMentionPickerState(new)
        }
        .alert(
            "Video can't be attached",
            isPresented: Binding(
                get: { attachmentLimitAlertMessage != nil },
                set: { if !$0 { attachmentLimitAlertMessage = nil } }
            )
        ) {
            Button("OK", role: .cancel) {
                attachmentLimitAlertMessage = nil
            }
        } message: {
            Text(attachmentLimitAlertMessage ?? "")
        }
    }

    @ViewBuilder
    private var squadChatMentionPickerOverlay: some View {
        if isMentionPickerVisible {
            let q = mentionPickerFilter.lowercased()
            let includeAll = SquadChatAllMention.defaultWireLabel.lowercased().hasPrefix(q)
            let filteredMembers =
                circleMembers
                    .filter { $0.id != appState.currentUserID }
                    .filter {
                        mentionPickerFilter.isEmpty
                            || $0.name.localizedCaseInsensitiveContains(mentionPickerFilter)
                    }
                    .sorted {
                        $0.name.localizedCaseInsensitiveCompare($1.name) == .orderedAscending
                    }
            let candidates: [FriendLocation] =
                includeAll ? [SquadChatAllMention.pickerMember()] + filteredMembers : filteredMembers
            if !candidates.isEmpty {
                let rowHeight: CGFloat = 61
                let visibleRowCap = 5
                let measuredHeight =
                    min(CGFloat(visibleRowCap), CGFloat(candidates.count)) * rowHeight
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: 0) {
                        ForEach(candidates) { member in
                            Button {
                                insertMentionFromPicker(member)
                            } label: {
                                HStack(spacing: 12) {
                                    AvatarView(
                                        name: member.name,
                                        avatarUrl: member.avatarUrl,
                                        size: 40,
                                        accentColor: .purple.opacity(0.45),
                                        accentRingWidth: 0,
                                        whiteRingWidth: 0
                                    )
                                    Text(member.name)
                                        .font(.body)
                                        .foregroundStyle(.white)
                                        .lineLimit(1)
                                    Spacer(minLength: 0)
                                }
                                .padding(.horizontal, 14)
                                .padding(.vertical, 10)
                            }
                            .buttonStyle(.plain)
                            Divider()
                                .background(Color.white.opacity(0.08))
                        }
                    }
                }
                .frame(height: measuredHeight)
                .background(Color.black.opacity(0.92))
                .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                .overlay(
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .stroke(Color.white.opacity(0.12), lineWidth: 1)
                )
                .shadow(color: .black.opacity(0.45), radius: 18, y: 6)
                .padding(.horizontal, OttoScreenChrome.horizontalPadding)
            }
        }
    }

    private func attachCreatedEventToComposer(_ event: EventDTO) {
        let trimmedName = event.name.trimmingCharacters(in: .whitespacesAndNewlines)
        let attachmentID = UUID()
        store.pendingAttachments = [
            ChatPendingComposerAttachment(
                id: attachmentID,
                kind: .event(eventId: event.id, eventName: trimmedName.isEmpty ? "Event" : trimmedName),
                event: event
            )
        ]
        Task {
            let preview = await ChatPickerPreviewLoader.eventBannerPreview(for: event)
            await MainActor.run {
                guard store.pendingAttachments.contains(where: { $0.id == attachmentID }) else { return }
                store.pendingAttachments = [
                    ChatPendingComposerAttachment(
                        id: attachmentID,
                        kind: .event(eventId: event.id, eventName: trimmedName.isEmpty ? "Event" : trimmedName),
                        previewImage: preview,
                        event: event
                    )
                ]
            }
        }
    }

    private func sendChatMessage() async {
        let body = store.draft.trimmingCharacters(in: .whitespacesAndNewlines)
        let savedAttachments = store.pendingAttachments
        let pendingAttachment = savedAttachments.first
        guard !body.isEmpty || pendingAttachment != nil, !store.isSendingMessage else { return }
        if store.editingMessageId != nil, pendingAttachment != nil { return }
        if let eid = store.editingMessageId {
            let baseline =
                store.messages.first(where: { $0.id == eid })?.body
                    .trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            if body == baseline { return }
        }

        store.isSendingMessage = true
        store.errorMessage = nil
        let replyId = store.replyDraft.messageId
        let editingId = store.editingMessageId
        let isVideoSend = pendingAttachment?.isVideo == true && editingId == nil
        if editingId == nil {
            store.clearComposerForSend(appState: appState)
        } else {
            store.replyDraft = .empty
        }
        defer {
            if !isVideoSend || editingId != nil {
                store.isSendingMessage = false
            }
        }

        do {
            var namesById = Dictionary(uniqueKeysWithValues: circleMembers.map { ($0.id, $0.name) })
            namesById[SquadChatAllMention.userId] = "all"
            var memberIds = Set(circleMembers.map(\.id))
            memberIds.insert(SquadChatAllMention.userId)
            var mentions = squadMentionSpansForSend(body: body, memberIds: memberIds, displayNames: namesById)
            let me = appState.currentUserID
            if !me.isEmpty {
                mentions = mentions.filter { $0.userId != me }
            }
            if let editId = editingId {
                let message = try await APIClient.shared.patchCircleChatMessage(
                    circleId: circleID,
                    messageId: editId,
                    body: body,
                    mentions: mentions
                )
                store.cancelEditingMessage(appState: appState, clearDraft: true)
                store.upsert(message, appState: appState)
            } else if let attachment = pendingAttachment, attachment.isVideo,
                      let pickerItem = attachment.pickerItem {
                let prepared = try await ChatPickerPreviewLoader.preparedVideo(from: pickerItem)
                let clientMessageId = UUID().uuidString
                let sender = appState.allUsers.first(where: { $0.id == appState.currentUserID }).map {
                    CircleChatMessageDTO.SenderDTO(
                        id: $0.id,
                        displayName: $0.displayName,
                        avatarUrl: $0.avatarUrl,
                        mapAccentKey: $0.mapAccentKey
                    )
                }
                videoUploads.startCircleUpload(
                    circleId: circleID,
                    prepared: prepared,
                    body: body,
                    clientMessageId: clientMessageId,
                    replyToMessageId: replyId,
                    mentions: mentions,
                    senderUserId: appState.currentUserID,
                    sender: sender,
                    onOptimisticMessage: { optimistic in
                        appState.chatStore.insertPendingSquadMessage(optimistic)
                    },
                    onComplete: { result in
                        switch result {
                        case .success(let message):
                            OttoAnalytics.logChatMessageSent(channel: "squad", attachmentType: "video")
                            store.upsert(message, appState: appState)
                        case .failure:
                            break
                        }
                        store.isSendingMessage = false
                    }
                )
                return
                return
            } else if let attachment = pendingAttachment, attachment.isEvent,
                      let event = attachment.event {
                let message = try await APIClient.shared.sendCircleChatMessage(
                    circleId: circleID,
                    body: body,
                    eventId: event.id,
                    replyToMessageId: replyId,
                    mentions: mentions
                )
                OttoAnalytics.logChatMessageSent(channel: "squad", attachmentType: "event")
                store.upsert(message, appState: appState)
            } else if let attachment = pendingAttachment, attachment.isPlace,
                      let payload = attachment.placePayload,
                      let lat = payload.latitude,
                      let lng = payload.longitude {
                let message = try await APIClient.shared.postCircleChatPlaceMessage(
                    circleId: circleID,
                    body: body,
                    placeLatitude: lat,
                    placeLongitude: lng,
                    placeName: payload.title,
                    placeAddressSummary: payload.subtitle,
                    mapPreviewJPEGData: attachment.mapPreviewJPEG
                )
                OttoAnalytics.logChatMessageSent(channel: "squad", attachmentType: "place")
                store.upsert(message, appState: appState)
            } else {
                let normalized = ChatOutgoingImageURLNormalizer.normalize(
                    draft: body,
                    pendingAttachment: pendingAttachment
                )
                let photoJPEG: Data?
                if normalized.imageUrl == nil,
                   let attachment = pendingAttachment, attachment.isPhoto,
                   let pickerItem = attachment.pickerItem {
                    photoJPEG = try await ChatPickerPreviewLoader.photoJPEG(from: pickerItem)
                } else {
                    photoJPEG = nil
                }
                guard !normalized.body.isEmpty || photoJPEG != nil || normalized.imageUrl != nil else { return }
                let message = try await APIClient.shared.sendCircleChatMessage(
                    circleId: circleID,
                    body: normalized.body,
                    replyToMessageId: replyId,
                    mentions: mentions,
                    photoJPEGData: photoJPEG,
                    imageUrl: normalized.imageUrl
                )
                if let share = normalized.klipyShare {
                    await KlipyAPIClient.reportShare(
                        slug: share.slug,
                        customerId: appState.currentUserID,
                        searchQuery: share.searchQuery
                    )
                }
                let attachmentType: String = {
                    if normalized.klipyShare != nil { return "gif" }
                    if let imageUrl = normalized.imageUrl {
                        return ChatImageURLDisplay.isAnimatedImageURL(imageUrl) ? "gif" : "image_url"
                    }
                    if photoJPEG != nil { return "photo" }
                    return "none"
                }()
                OttoAnalytics.logChatMessageSent(channel: "squad", attachmentType: attachmentType)
                store.upsert(message, appState: appState)
            }
        } catch {
            OttoLog.api.error(
                "Squad chat send failed circle=\(circleID, privacy: .public) error=\(String(describing: error), privacy: .public)"
            )
            store.draft = body
            store.pendingAttachments = savedAttachments
            store.replyDraft.messageId = replyId
            if let id = replyId, let ref = store.messages.first(where: { $0.id == id }) {
                store.replyDraft.authorName = ref.sender?.displayName ?? circleMembers.first(where: { $0.id == ref.senderUserId })?.name ?? "Someone"
                let trimmed = ref.body.trimmingCharacters(in: .whitespacesAndNewlines)
                if !trimmed.isEmpty {
                    store.replyDraft.snippet = trimmed
                } else if ref.videoAttachment != nil {
                    store.replyDraft.snippet = "Video"
                } else if ref.imageUrl != nil {
                    store.replyDraft.snippet = ChatImageURLDisplay.replySnippet(for: ref.imageUrl)
                } else {
                    store.replyDraft.snippet = trimmed
                }
                store.replyDraft.avatarURL = ref.sender?.avatarUrl
            }
            if let prepError = error as? ChatVideoUploadPrepError {
                attachmentLimitAlertMessage = prepError.errorDescription
            } else if let pickerError = error as? ChatPickerPreviewLoader.Error {
                attachmentLimitAlertMessage = pickerError.errorDescription
            } else {
                store.errorMessage = editingId == nil ? "Couldn't send message. Try again." : "Couldn't update message."
            }
        }
    }

    private func refreshMentionPickerState(_ draftText: String) {
        let ns = draftText as NSString
        let r = ns.range(of: "@", options: .backwards)
        if r.location == NSNotFound {
            isMentionPickerVisible = false
            return
        }
        if r.location > 0 {
            let prev = ns.character(at: r.location - 1)
            if prev != 0x20 && prev != 0xA && prev != 0xD && prev != 0x9 {
                isMentionPickerVisible = false
                return
            }
        }
        let after = r.location + 1
        if after < ns.length {
            let ch = ns.character(at: after)
            if ch == 0x20 || ch == 0xA || ch == 0xD || ch == 0x9 {
                isMentionPickerVisible = false
                return
            }
        }
        var end = after
        while end < ns.length {
            let c = ns.character(at: end)
            if c == 0x20 || c == 0xA || c == 0xD || c == 0x9 { break }
            end += 1
        }
        let query = ns.substring(with: NSRange(location: after, length: max(0, end - after)))
        // Completed @mention (e.g. picker insert or typed name + space) — not an active query.
        if end < ns.length {
            isMentionPickerVisible = false
            return
        }
        mentionPickerAnchorUTF16 = r.location
        mentionPickerFilter = query
        isMentionPickerVisible = true
    }

    private func insertMentionFromPicker(_ member: FriendLocation) {
        let ns = store.draft as NSString
        let anchor = mentionPickerAnchorUTF16
        let end = mentionTokenEndUTF16(ns: ns, anchorUTF16: anchor)
        guard anchor >= 0, anchor <= ns.length, end >= anchor else { return }
        let replacement: String
        if member.id == SquadChatAllMention.userId {
            replacement = "@all "
        } else {
            replacement = "@\(member.name) "
        }
        let newText = ns.replacingCharacters(in: NSRange(location: anchor, length: end - anchor), with: replacement)
        let caretUTF16 = (newText as NSString).length
        store.draft = newText
        isMentionPickerVisible = false
        DispatchQueue.main.async {
            ChatComposerFieldFocusHelper.applyCaretAndDefaultKeyboard(utf16Offset: caretUTF16)
        }
    }

    private func mentionTokenEndUTF16(ns: NSString, anchorUTF16: Int) -> Int {
        let len = ns.length
        guard anchorUTF16 >= 0, anchorUTF16 < len else { return len }
        var i = anchorUTF16 + 1
        while i < len {
            let ch = ns.character(at: i)
            if ch == 0x20 || ch == 0xA || ch == 0xD || ch == 0x9 { break }
            i += 1
        }
        return i
    }

    private func squadMentionSpansForSend(body: String, memberIds: Set<String>, displayNames: [String: String]) -> [CircleChatMentionSpanDTO] {
        let ns = body as NSString
        let len = ns.length
        var i = 0
        var out: [CircleChatMentionSpanDTO] = []
        while i < len {
            if ns.character(at: i) != 0x40 {
                i += 1
                continue
            }
            let after = i + 1
            if after >= len { break }
            var bestUid: String?
            var bestLen = 0
            let ordered =
                displayNames
                    .sorted { a, b in
                        let aAll = a.key == SquadChatAllMention.userId
                        let bAll = b.key == SquadChatAllMention.userId
                        if aAll && !bAll { return false }
                        if !aAll && bAll { return true }
                        return a.key < b.key
                    }
            for (uid, rawName) in ordered where memberIds.contains(uid) && !rawName.isEmpty {
                let nl = (rawName as NSString).length
                guard after + nl <= len else { continue }
                let slice = ns.substring(with: NSRange(location: after, length: nl))
                if slice == rawName, nl > bestLen {
                    bestLen = nl
                    bestUid = uid
                }
            }
            if let uid = bestUid, bestLen > 0 {
                let total = 1 + bestLen
                out.append(CircleChatMentionSpanDTO(userId: uid, start: i, length: total))
                i += total
            } else {
                i += 1
            }
        }
        return out
    }
}

/// Isolated transcript so unrelated `AppState` publishes do not rebuild every message row while scrolling.
private struct SquadChatTranscriptList: View, Equatable {
    let messages: [CircleChatMessageDTO]
    let isLoadingMessages: Bool
    let isLoadingOlderMessages: Bool
    let longPressMessageID: String?
    let longPressChromeLiftY: CGFloat
    let currentUserID: String
    let oldestPrefetchMessageIDs: Set<String>
    let pendingUploadProgressByClientMessageId: [String: Double]
    let emptyChatState: () -> AnyView
    let chatMessageRow: (CircleChatMessageDTO) -> AnyView
    let chatDateHeader: (String) -> AnyView
    let dateHeaderText: (Date) -> String
    let onMessageAppear: (CircleChatMessageDTO) -> Void

    static func == (lhs: SquadChatTranscriptList, rhs: SquadChatTranscriptList) -> Bool {
        lhs.messages == rhs.messages
            && lhs.isLoadingMessages == rhs.isLoadingMessages
            && lhs.isLoadingOlderMessages == rhs.isLoadingOlderMessages
            && lhs.longPressMessageID == rhs.longPressMessageID
            && lhs.longPressChromeLiftY == rhs.longPressChromeLiftY
            && lhs.currentUserID == rhs.currentUserID
            && lhs.oldestPrefetchMessageIDs == rhs.oldestPrefetchMessageIDs
            && lhs.pendingUploadProgressByClientMessageId == rhs.pendingUploadProgressByClientMessageId
    }

    var body: some View {
        ZStack {
            if longPressMessageID != nil {
                Color.black.opacity(0.45)
                    .zIndex(1)
                    .allowsHitTesting(false)
            }

            LazyVStack(alignment: .leading, spacing: 16) {
                if messages.isEmpty && isLoadingMessages {
                    ProgressView()
                        .tint(.purple)
                        .frame(maxWidth: .infinity)
                        .padding(.top, 40)
                } else if messages.isEmpty {
                    emptyChatState()
                } else {
                    if isLoadingOlderMessages {
                        ProgressView()
                            .tint(.purple)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 8)
                    }

                    ForEach(messages) { message in
                        let previous = ChatUIKitScrollPinning.previousMessage(before: message, in: messages)
                        if ChatUIKitScrollPinning.shouldShowSquadDateHeader(for: message, previous: previous) {
                            chatDateHeader(dateHeaderText(message.createdAt))
                        }
                        chatMessageRow(message)
                            .id(message.id)
                            .offset(y: longPressMessageID == message.id ? longPressChromeLiftY : 0)
                            .scaleEffect(
                                longPressMessageID == message.id ? 1.03 : 1.0,
                                anchor: message.resolvedSenderUserId == currentUserID ? .trailing : .leading
                            )
                            .shadow(
                                color: .black.opacity(longPressMessageID == message.id ? 0.5 : 0),
                                radius: longPressMessageID == message.id ? 20 : 0,
                                y: 9
                            )
                            .zIndex(longPressMessageID == message.id ? 2 : 0)
                            .onAppear {
                                onMessageAppear(message)
                            }
                    }
                }
            }
        }
    }
}

private struct SquadChatTab: View {
    @EnvironmentObject private var appState: AppState
    @EnvironmentObject private var locationService: LocationService
    @ObservedObject var store: SquadChatThreadStore
    let circleID: String
    let circleMembers: [FriendLocation]
    let squadDisplayName: String
    let nextEventCandidate: EventDTO?
    let nextEvent: EventDTO?

    @Binding var longPressChatMessage: CircleChatMessageDTO?
    @Binding var chatMessageFrames: [String: CGRect]
    @Binding var longPressChromeLiftY: CGFloat
    @Binding var chatKeyboardOverlap: CGFloat
    @Binding var suppressAttachmentNavigationForMessageID: String?
    @Binding var presentedEventDetail: CircleDetailEventPresentation?
    @Binding var reactionsDetailMessage: CircleChatMessageDTO?
    var composerFocused: FocusState<Bool>.Binding

    let upcomingEventCard: (EventDTO) -> AnyView
    let emptyChatState: () -> AnyView
    let chatMessageRow: (CircleChatMessageDTO) -> AnyView
    let chatDateHeader: (String) -> AnyView
    let dateHeaderText: (Date) -> String
    let pendingUploadProgressByClientMessageId: [String: Double]
    let onReaction: (String, String) -> Void
    let onNextEventCandidateChange: (EventDTO?) -> Void
    let onNextEventBannerVisible: (EventDTO?) -> Void

    @State private var scrollDistanceFromBottom: CGFloat = 0
    @State private var isScrollLayoutReady = false
    @State private var isScrollUserInteracting = false
    @State private var scrollSettleTrigger = 0
    @State private var scrollViewInstanceId = UUID()
    @State private var scrollViewHasAppearedOnce = false
    @State private var chatScrollHandle = ChatScrollViewHandle()
    @State private var bottomPullPeakOverscroll: CGFloat = 0
    @State private var squadDeleteConfirm: CircleChatMessageDTO?

    private var bottomSentinelID: String {
        "chat-bottom-\(circleID)"
    }

    private var isHidingChatTranscriptForScrollSettle: Bool {
        ChatScrollLogic.shouldHideTranscriptForScrollSettle(
            isLoadingMessages: store.isLoadingMessages,
            messagesEmpty: store.messages.isEmpty,
            scrollState: store.scrollState
        )
    }

    private var showScrollSettleLoadingOverlay: Bool {
        ChatScrollLogic.shouldShowScrollSettleLoadingOverlay(
            isLoadingMessages: store.isLoadingMessages,
            messagesEmpty: store.messages.isEmpty,
            scrollState: store.scrollState
        )
    }

    private var jumpToLatestBadgeCount: Int? {
        let count = ChatScrollLogic.unreadCountBelowLastRead(
            messageIDs: store.messages.map(\.id),
            lastReadMessageId: store.scrollState.lastReadMessageId
        )
        return count > 0 ? count : nil
    }

    private var showScrollToLatestAffordance: Bool {
        ChatScrollLogic.shouldShowJumpToLatestAffordance(
            didInitialScrollToBottom: store.scrollState.didInitialScrollToBottom,
            isScrollLayoutReady: isScrollLayoutReady,
            distanceFromBottom: scrollDistanceFromBottom,
            messagesEmpty: store.messages.isEmpty,
            isHidingTranscriptForScrollSettle: isHidingChatTranscriptForScrollSettle
        )
    }

    private var squadOldestPrefetchMessageIDs: Set<String> {
        Set(store.messages.prefix(5).map(\.id))
    }

    @ViewBuilder
    private var squadChatComposerInset: some View {
        VStack(spacing: 0) {
            ChatKeyboardDismissZone(
                height: ChatKeyboardDismissZoneMetrics.composerStripHeight,
                isActive: ChatComposerKeyboardDismiss.isActive(
                    keyboardOverlap: chatKeyboardOverlap,
                    composerFocused: composerFocused.wrappedValue
                ),
                onDismiss: {
                    ChatComposerKeyboardDismiss.dismiss(composerFocused: composerFocused)
                }
            )

            if let statusMessage = squadChatComposerStatusMessage {
                Text(statusMessage)
                    .font(.caption)
                    .foregroundStyle(.white.opacity(0.52))
                    .multilineTextAlignment(.leading)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, OttoScreenChrome.horizontalPadding)
                    .padding(.bottom, 6)
            }

            SquadChatComposerIsland(
                circleID: circleID,
                circleMembers: circleMembers,
                squadDisplayName: squadDisplayName,
                store: store,
                composerFocused: composerFocused
            )
            .environmentObject(appState)
            .environmentObject(locationService)
        }
        .chatComposerKeyboardLift($chatKeyboardOverlap)
    }

    private var squadChatComposerStatusMessage: String? {
        if let errorMessage = store.errorMessage {
            if isSquadChatFetchError(errorMessage), store.messages.isEmpty {
                return nil
            }
            return errorMessage
        }
        return store.statusMessage
    }

    var body: some View {
        ZStack(alignment: .top) {
            VStack(spacing: 0) {
                ZStack {
                    ScrollViewReader { proxy in
                        ScrollView {
                            ChatScrollDistanceFromBottomReporter(
                                distanceFromBottom: $scrollDistanceFromBottom,
                                isLayoutReady: $isScrollLayoutReady,
                                isScrollUserInteracting: $isScrollUserInteracting,
                                scrollViewHandle: chatScrollHandle
                            )
                                .frame(width: 0, height: 0)
                            VStack(spacing: 0) {
                                SquadChatTranscriptList(
                                    messages: store.messages,
                                    isLoadingMessages: store.isLoadingMessages,
                                    isLoadingOlderMessages: store.isLoadingOlderMessages,
                                    longPressMessageID: longPressChatMessage?.id,
                                    longPressChromeLiftY: longPressChromeLiftY,
                                    currentUserID: appState.currentUserID,
                                    oldestPrefetchMessageIDs: squadOldestPrefetchMessageIDs,
                                    pendingUploadProgressByClientMessageId: pendingUploadProgressByClientMessageId,
                                    emptyChatState: emptyChatState,
                                    chatMessageRow: chatMessageRow,
                                    chatDateHeader: chatDateHeader,
                                    dateHeaderText: dateHeaderText,
                                    onMessageAppear: { message in
                                        store.updateVisibleMessage(message.id, appState: appState, scrollViewInstanceId: scrollViewInstanceId)
                                        if squadOldestPrefetchMessageIDs.contains(message.id) {
                                            store.loadOlderMessagesIfNeeded(
                                                appState: appState,
                                                thresholdMessageID: message.id
                                            )
                                        }
                                    }
                                )
                                .equatable()

                                Color.clear
                                    .frame(height: ChatScrollToLatestLayout.transcriptBottomPadding)
                                Color.clear
                                    .frame(height: 1)
                                    .id(bottomSentinelID)
                            }
                            .padding(.horizontal, OttoScreenChrome.horizontalPadding)
                            .padding(.top, 14)
                            .chatScrollSettleTranscriptVisibility(isHidden: isHidingChatTranscriptForScrollSettle)
                        }
                        .defaultScrollAnchor(.bottom)
                        .safeAreaInset(edge: .bottom, spacing: 0) {
                            // Inset keeps composer visible during scroll settle; opacity applies to transcript only.
                            squadChatComposerInset
                        }
                        .id("squad-chat-scroll-\(circleID)")
                        .scrollDismissesKeyboard(.never)
                        .onAppear {
                            let preserveOffset = scrollViewHasAppearedOnce
                            scrollViewHasAppearedOnce = true
                            store.scrollViewDidAppear(
                                appState: appState,
                                preserveScrollViewOffset: preserveOffset,
                                scrollViewInstanceId: scrollViewInstanceId
                            )
                        }
                        .onChange(of: store.messages.isEmpty) { wasEmpty, isEmpty in
                            if wasEmpty, !isEmpty, store.scrollState.pendingScrollIntent != nil {
                                scrollSettleTrigger &+= 1
                            }
                        }
                        .onChange(of: scrollDistanceFromBottom) { oldDistance, newDistance in
                            if ChatUIKitScrollPinning.shouldUpdateBottomPinning(
                                oldDistance: oldDistance,
                                newDistance: newDistance,
                                newestMessageID: store.messages.last?.id,
                                lastReadMessageID: store.scrollState.lastReadMessageId
                            ) {
                                updateBottomPinning()
                            }
                            handleBottomPullToRefresh(oldDistance: oldDistance, newDistance: newDistance)
                            if isHidingChatTranscriptForScrollSettle,
                               isScrollLayoutReady,
                               store.scrollState.pendingScrollIntent != nil {
                                scrollSettleTrigger &+= 1
                            }
                        }
                        .task(id: squadScrollSettleTaskID) {
                            await executePendingScrollIntent(proxy: proxy)
                        }
                        .task(id: squadScrollSettleWatchdogTaskID) {
                            guard store.scrollState.isSettlingScrollPosition, !store.messages.isEmpty else { return }
                            try? await Task.sleep(nanoseconds: 1_500_000_000)
                            guard !Task.isCancelled else { return }
                            if store.scrollState.isSettlingScrollPosition {
                                store.clearScrollSettle(appState: appState)
                                if !store.scrollState.didInitialScrollToBottom {
                                    store.markScrollIntentHandled(appState: appState)
                                }
                            }
                        }
                    }

                    if showScrollSettleLoadingOverlay {
                        ProgressView()
                            .tint(.purple)
                            .frame(maxWidth: .infinity, maxHeight: .infinity)
                            .background(Color.black.opacity(0.96))
                            .transition(.opacity)
                    }
                }
            }

            if let target = longPressChatMessage {
                ChatMessageActionOverlay(
                    onDismiss: clearLongPressChrome,
                    onReply: { beginReply(to: target) },
                    onReaction: { emoji in
                        onReaction(emoji, target.id)
                    },
                    onEdit: squadCanEditText(target)
                        ? {
                            store.beginEditingMessage(target, appState: appState)
                        }
                        : nil,
                    onDelete: squadOwnUserBubble(target)
                        ? {
                            squadDeleteConfirm = target
                            clearLongPressChrome()
                        }
                        : nil
                )
                .zIndex(50)
                .transition(.opacity)
            }

            NextUpBannerSlot(event: nextEvent, content: upcomingEventCard)
                .zIndex(30)
                .allowsHitTesting(nextEvent != nil)

            ChatKeyboardDismissZone(
                height: ChatKeyboardDismissZoneMetrics.topStripHeight,
                isActive: ChatComposerKeyboardDismiss.isActive(
                    keyboardOverlap: chatKeyboardOverlap,
                    composerFocused: composerFocused.wrappedValue
                ),
                onDismiss: {
                    ChatComposerKeyboardDismiss.dismiss(composerFocused: composerFocused)
                }
            )
            .zIndex(10)
        }
        .overlay(alignment: .bottomTrailing) {
            ChatScrollToLatestFloatingButton(
                visible: showScrollToLatestAffordance,
                badgeCount: jumpToLatestBadgeCount,
                bottomPadding: ChatScrollToLatestLayout.composerReservePoints,
                action: {
                    store.requestScrollToLatest(appState: appState, animated: true)
                }
            )
            .zIndex(45)
        }
        .onChange(of: longPressChatMessage) { _, new in
            guard new == nil else { return }
            chatMessageFrames = [:]
            longPressChromeLiftY = 0
        }
        .task(id: nextEventCandidate?.id) {
            onNextEventCandidateChange(nextEventCandidate)
        }
        .onAppear {
            onNextEventBannerVisible(nextEvent)
        }
        .onChange(of: nextEvent?.id) { _, _ in
            onNextEventBannerVisible(nextEvent)
        }
        .alert(
            "Delete this message?",
            isPresented: Binding(
                get: { squadDeleteConfirm != nil },
                set: { if !$0 { squadDeleteConfirm = nil } }
            )
        ) {
            Button("Delete", role: .destructive) {
                if let victim = squadDeleteConfirm {
                    Task { await deleteSquadChatMessage(victim) }
                }
                squadDeleteConfirm = nil
            }
            Button("Cancel", role: .cancel) {
                squadDeleteConfirm = nil
            }
        } message: {
            Text("This cannot be undone.")
        }
    }

    private func squadOwnUserBubble(_ message: CircleChatMessageDTO) -> Bool {
        message.resolvedSenderUserId == appState.currentUserID && message.messageType == "user"
    }

    private func squadCanEditText(_ message: CircleChatMessageDTO) -> Bool {
        guard squadOwnUserBubble(message) else { return false }
        if let u = message.imageUrl, !u.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { return false }
        if message.videoAttachment != nil { return false }
        if message.eventAttachment != nil || message.driveAttachment != nil || message.placeAttachment != nil { return false }
        return Date().timeIntervalSince(message.createdAt) <= 120
    }

    private func deleteSquadChatMessage(_ message: CircleChatMessageDTO) async {
        do {
            let tomb = try await APIClient.shared.deleteCircleChatMessage(circleId: circleID, messageId: message.id)
            appState.upsertSquadChatTranscript(with: tomb)
        } catch {
            store.errorMessage = "Couldn't delete message."
        }
    }

    private func clearLongPressChrome() {
        longPressChatMessage = nil
        longPressChromeLiftY = 0
        chatMessageFrames = [:]
    }

    private func updateBottomPinning() {
        let isPinned = ChatUIKitScrollPinning.isPinnedToLatest(distanceFromBottom: scrollDistanceFromBottom)
        store.updateBottomVisibility(
            isPinned: isPinned,
            appState: appState,
            scrollViewInstanceId: scrollViewInstanceId,
            isScrollUserInteracting: isScrollUserInteracting
        )
    }

    private func handleBottomPullToRefresh(oldDistance _: CGFloat, newDistance: CGFloat) {
        guard store.scrollState.isPinnedToBottom, !store.messages.isEmpty, !store.isRefreshingMessages else {
            if newDistance >= 0 {
                bottomPullPeakOverscroll = 0
            }
            return
        }

        let overscroll = max(0, -newDistance)
        if overscroll > 0 {
            bottomPullPeakOverscroll = max(bottomPullPeakOverscroll, overscroll)
            return
        }

        guard bottomPullPeakOverscroll > 0 else { return }
        if bottomPullPeakOverscroll >= ChatBottomPullToRefreshMetrics.triggerDistance {
            Task { await store.pullToRefresh(appState: appState) }
        }
        bottomPullPeakOverscroll = 0
    }

    private var squadScrollSettleTaskID: String {
        "\(store.scrollState.intentRevision)-\(scrollSettleTrigger)"
    }

    private var squadScrollSettleWatchdogTaskID: String {
        guard store.scrollState.isSettlingScrollPosition, !store.messages.isEmpty else { return "idle" }
        return "watch-\(store.scrollState.intentRevision)-\(scrollSettleTrigger)"
    }

    private func executePendingScrollIntent(proxy: ScrollViewProxy) async {
        let transcriptVisible = !isHidingChatTranscriptForScrollSettle
        guard let intent = appState.chatStore.squadValidatePendingScrollIntent(
            circleID: circleID,
            transcriptVisible: transcriptVisible
        ), intent != .none else {
            if store.scrollState.isSettlingScrollPosition {
                store.clearScrollSettle(appState: appState)
            }
            return
        }
        appState.chatStore.beginSquadProgrammaticScroll(circleID: circleID)
        defer { appState.chatStore.endSquadProgrammaticScroll(circleID: circleID) }

        await Task.yield()
        let requiresStableSettle = isHidingChatTranscriptForScrollSettle
        let intentSource = store.scrollState.pendingScrollIntentSource
        let context = ChatScrollIntentExecutor.Context(
            intent: intent,
            bottomSentinelID: bottomSentinelID,
            newestMessageID: store.messages.last?.id,
            anchorMessageIDs: Set(store.messages.map(\.id)),
            requiresStableSettle: requiresStableSettle,
            isPinnedToBottom: store.scrollState.isPinnedToBottom,
            intentSource: intentSource,
            scrollViewHandle: chatScrollHandle,
            distanceFromBottom: { scrollDistanceFromBottom },
            isLayoutReady: { isScrollLayoutReady }
        )
        let outcome = await ChatScrollIntentExecutor.execute(context: context, proxy: proxy)
        if outcome.shouldMarkHandled {
            store.markScrollIntentHandled(appState: appState)
        } else {
            store.clearScrollSettle(appState: appState)
            if !store.scrollState.didInitialScrollToBottom {
                store.markScrollIntentHandled(appState: appState)
            }
        }
    }

    private func beginReply(to message: CircleChatMessageDTO) {
        let name = message.sender?.displayName ?? circleMembers.first(where: { $0.id == message.resolvedSenderUserId })?.name ?? "Someone"
        store.replyDraft.messageId = message.id
        store.replyDraft.authorName = name
        let trimmed = message.body.trimmingCharacters(in: .whitespacesAndNewlines)
        store.replyDraft.snippet = trimmed.isEmpty && message.imageUrl != nil
            ? ChatImageURLDisplay.replySnippet(for: message.imageUrl)
            : trimmed
        store.replyDraft.avatarURL = message.sender?.avatarUrl
        clearLongPressChrome()
        Task { @MainActor in
            await Task.yield()
            composerFocused.wrappedValue = true
        }
    }
}

private struct NextUpBannerSlot: View {
    let event: EventDTO?
    let content: (EventDTO) -> AnyView
    @State private var renderedEvent: EventDTO?
    @State private var isPresented = false
    @State private var clearTask: Task<Void, Never>?

    var body: some View {
        Group {
            if let renderedEvent {
                content(renderedEvent)
                    .padding(.horizontal, OttoScreenChrome.horizontalPadding)
                    .padding(.top, 12)
                    .padding(.bottom, 8)
                    .background(Color.black.opacity(0.96))
                    .opacity(isPresented ? 1 : 0)
                    .offset(y: isPresented ? 0 : -12)
                    .scaleEffect(isPresented ? 1 : 0.985, anchor: .top)
                    .clipped()
            }
        }
        .onAppear {
            syncRenderedEvent()
        }
        .onChange(of: event?.id) { _, _ in
            syncRenderedEvent()
        }
    }

    private func syncRenderedEvent() {
        clearTask?.cancel()
        if let event {
            renderedEvent = event
            withAnimation(.spring(response: 0.34, dampingFraction: 0.9)) {
                isPresented = true
            }
            return
        }

        let clearingEventID = renderedEvent?.id
        withAnimation(.easeInOut(duration: 0.22)) {
            isPresented = false
        }
        clearTask = Task { @MainActor in
            try? await Task.sleep(nanoseconds: 260_000_000)
            guard !Task.isCancelled, event == nil, renderedEvent?.id == clearingEventID else { return }
            renderedEvent = nil
        }
    }
}

private struct SquadEventsTab: View {
    let filteredEvents: [EventDTO]
    let isLoading: Bool
    let errorMessage: String?
    let selectEvent: (EventDTO) -> Void
    let refresh: () async -> Void
    let filterPicker: () -> AnyView
    let eventRow: (EventDTO, Bool) -> AnyView

    var body: some View {
        Group {
            if isLoading && filteredEvents.isEmpty {
                ProgressView()
                    .tint(.purple)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if filteredEvents.isEmpty {
                VStack(alignment: .leading, spacing: 12) {
                    headerControls
                    if errorMessage != nil {
                        UnifiedEmptyStateView(
                            title: String(localized: "fetch_error_squad_events_title"),
                            message: String(localized: "fetch_error_refresh_body"),
                            systemImage: "exclamationmark.triangle",
                            actionTitle: String(localized: "fetch_error_refresh_action"),
                            action: {
                                Task { await refresh() }
                            }
                        )
                        .frame(minHeight: 360)
                    } else {
                        UnifiedEmptyStateView(
                            title: "No events",
                            message: "Official squad events appear here when your squad schedules them.",
                            systemImage: "calendar"
                        )
                        .frame(minHeight: 360)
                    }
                }
                .padding(16)
            } else {
                EventListSectionedList(
                    events: sortEventsForSectionedList(filteredEvents),
                    presentation: .compact,
                    horizontalPadding: 0,
                    hasListHeader: true,
                    headerBottomPadding: 0,
                    compactFirstSectionAfterHeader: true,
                    header: {
                        headerControls
                    }
                ) { event, groupedInSection in
                    eventRow(event, groupedInSection)
                        .onTapGesture {
                            selectEvent(event)
                        }
                        .accessibilityAddTraits(.isButton)
                }
                .refreshable {
                    await refresh()
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 16)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }

    private var headerControls: some View {
        filterPicker()
            .padding(.top, 20)
    }
}

private struct SquadGridTab: View {
    let grid: SquadGridResponseDTO?
    let isLoading: Bool
    let errorMessage: String?
    let isPageEmpty: Bool
    let onSelectLeader: (SquadGridLeaderDTO) -> Void
    let refresh: () async -> Void

    var body: some View {
        SquadGridView(
            metrics: grid?.metrics ?? [],
            isLoading: isLoading,
            errorMessage: errorMessage,
            isPageEmpty: isPageEmpty,
            onSelectLeader: onSelectLeader,
            onRefresh: refresh
        )
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.black)
    }
}

private func isSquadChatFetchError(_ message: String?) -> Bool {
    guard let message else { return false }
    return message == "Couldn't load squad chat." || message == "Couldn't refresh squad chat."
}

private func isDirectChatFetchError(_ message: String?) -> Bool {
    message == "Could not load messages."
}

private enum ShareInviteBusyAction {
    case prefetch
    case copy
    case sms
}

private enum SquadInviteSheetHaptics {
    static func buttonTap() {
        let generator = UIImpactFeedbackGenerator(style: .light)
        generator.prepare()
        if #available(iOS 13.0, *) {
            generator.impactOccurred(intensity: 0.78)
        } else {
            generator.impactOccurred()
        }
    }
}

private struct InviteSheetActionButton: View {
    let title: String
    let busyTitle: String
    let systemImage: String
    let isBusy: Bool
    let action: () -> Void

    private let accent = Color(red: 0.70, green: 0.25, blue: 1.0)

    var body: some View {
        Button {
            SquadInviteSheetHaptics.buttonTap()
            action()
        } label: {
            VStack(spacing: 10) {
                Group {
                    if isBusy {
                        ProgressView()
                            .scaleEffect(0.95)
                            .tint(.white)
                    } else {
                        Image(systemName: systemImage)
                            .font(.system(size: 30, weight: .semibold))
                            .foregroundStyle(accent)
                    }
                }
                .frame(height: 34)

                Text(isBusy ? busyTitle : title)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.white)
                    .multilineTextAlignment(.center)
                    .lineLimit(2)
                    .minimumScaleFactor(0.85)
            }
            .frame(maxWidth: .infinity)
            .padding(.horizontal, 10)
            .padding(.vertical, 16)
            .background(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(Color.white.opacity(0.05))
            )
            .overlay(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .stroke(Color.white.opacity(0.16), lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
        .disabled(isBusy)
    }
}

private struct CircleDetailScreen: View {
    @EnvironmentObject private var appState: AppState
    @EnvironmentObject private var locationService: LocationService
    @Environment(\.dismiss) private var dismiss
    @Environment(\.scenePhase) private var scenePhase
    let circleID: String
    @StateObject private var chatStore: SquadChatThreadStore
    @StateObject private var nextUpBannerStore: NextUpEventBannerStore

    init(circleID: String) {
        self.circleID = circleID
        _chatStore = StateObject(wrappedValue: SquadChatThreadStore(circleID: circleID))
        _nextUpBannerStore = StateObject(wrappedValue: NextUpEventBannerStore(circleID: circleID))
    }

    private enum DetailTab: String, CaseIterable, OttoTabItem {
        case chat = "Chat"
        case events = "Events"
        case grid = "Grid"

        var title: String { rawValue }
    }
    @State private var isShowingAddMember = false
    @State private var isShowingAddEvent = false
    @State private var selectedTab: DetailTab = .chat
    @State private var squadEvents: [EventDTO] = []
    @State private var isLoadingSquadEvents = false
    @State private var squadEventsErrorMessage: String?
    @State private var squadGrid: SquadGridResponseDTO?
    @State private var isLoadingSquadGrid = false
    @State private var squadGridError: String?
    @State private var squadGridRange = "all_time"
    @State private var inviteSearchText = ""
    @State private var generatedInviteLink = ""
    @State private var smsInviteLinkByPhone: [String: String] = [:]
    @State private var lookupResultUser: UserDTO?
    @State private var lookupMessage: String?
    @State private var isLookupLoading = false
    @State private var invitingUserID: String?
    @State private var profileSheetMember: FriendLocation?
    @State private var presentedPeerProfileFocus: PresentedPeerProfileFocus?
    @State private var lookupTask: Task<Void, Never>?
    @State private var smsInviteTask: Task<Void, Never>?
    @State private var hasAttemptedLookup = false
    @State private var isOpeningSMSInvite = false
    @State private var shareInviteBusy: ShareInviteBusyAction?
    @State private var signupInviteRemaining: Int?
    @State private var signupInviteEarnAtNextLevelCount: Int?
    @State private var signupInviteNextLevelDisplayName: String?
    @State private var isLoadingSignupInviteBalance = false
    @State private var addMemberSheetToast: AppToast?
    @State private var smsInviteDelegate: SMSInviteMessageComposeDelegate?
    @State private var shouldShowAddMemberAfterSettingsDismiss = false
    @State private var squadPhotoPickerItem: PhotosPickerItem?
    @State private var longPressChatMessage: CircleChatMessageDTO?
    @State private var chatMessageFrames: [String: CGRect] = [:]
    @State private var longPressChromeLiftY: CGFloat = 0
    @State private var chatKeyboardOverlap: CGFloat = 0
    /// After a row long-press succeeds, ignore stray taps on the event card; see `suppressAttachmentNavigationForMessageID`.
    @State private var suppressAttachmentNavigationForMessageID: String?
    @State private var chatRsvpSubmittingEventId: String?
    @State private var presentedEventDetail: CircleDetailEventPresentation?
    @State private var presentedDriveSummary: DriveDTO?
    @State private var isLoadingSharedDriveSummary = false
    @State private var reactionsDetailMessage: CircleChatMessageDTO?
    @State private var isShowingSquadNotificationSettings = false
    @FocusState private var isChatComposerFocused: Bool
    @ObservedObject private var videoUploads = ChatVideoUploadCoordinator.shared

    var body: some View {
        OttoTabbedPager(selectedTab: $selectedTab, mode: .paging) {
            EmptyView()
        } content: { tab in
            switch tab {
            case .chat:
                chatTab
            case .events:
                eventsTab
            case .grid:
                gridTab
            }
        }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.black.ignoresSafeArea())
        /// Keep squad identity + tabs fixed while each selected tab owns its own scroll view.
        .safeAreaInset(edge: .top, spacing: 0) {
            fixedSquadChrome
        }
        .toolbar(.hidden, for: .navigationBar)
        .chatNavigationInteractivePopSwipeEnabled()
        .onAppear {
            appState.markCircleAccessed(circleID)
            applyPendingSquadChatTabFocusIfNeeded()
            if selectedTab == .chat {
                chatStore.activateChatTab(appState: appState)
            }
            Task {
                await refreshSquadEvents()
                await appState.refreshInvites(for: circleID)
                await appState.refreshPresence(for: circleID)
            }
        }
        .onDisappear {
            chatStore.detachChatTab(appState: appState)
            nextUpBannerStore.scheduleAutoHide(for: nil)
        }
        .onChange(of: selectedTab) { _, tab in
            if tab == .chat {
                chatStore.activateChatTab(appState: appState)
            } else {
                chatStore.pauseChatTab(appState: appState)
            }
            if tab == .grid {
                Task { await refreshSquadGrid() }
            }
        }
        .onChange(of: appState.circlesRootTabIsSelected) { _, isSelected in
            if isSelected {
                if selectedTab == .chat {
                    chatStore.activateChatTab(appState: appState)
                }
            } else {
                chatStore.pauseChatTab(appState: appState)
            }
        }
        .onChange(of: appState.pendingCircleFocus?.id) { _, _ in
            applyPendingSquadChatTabFocusIfNeeded()
        }
        .onChange(of: appState.latestCircleChatMessage) { _, message in
            guard let message, message.circleId == circleID else { return }
            chatStore.upsert(message, appState: appState)
        }
        .onChange(of: chatStore.messages.map(\.id).joined(separator: "\u{1e}")) { _, _ in
            let ids = chatStore.messages.compactMap { $0.eventAttachment?.eventId }
            appState.prefetchChatAttachmentEventsIfNeeded(eventIds: ids, squadEvents: squadEvents)
        }
        .onChange(of: squadEvents.map(\.id).joined(separator: "\u{1e}")) { _, _ in
            let ids = chatStore.messages.compactMap { $0.eventAttachment?.eventId }
            appState.prefetchChatAttachmentEventsIfNeeded(eventIds: ids, squadEvents: squadEvents)
        }
        .onChange(of: appState.isChatRealtimeConnected) { _, isConnected in
            chatStore.reconcilePolling(isRealtimeConnected: isConnected, appState: appState)
        }
        .onChange(of: scenePhase) { _, phase in
            guard phase == .active else { return }
            chatStore.sceneBecameActive(appState: appState)
        }
        .sheet(isPresented: $isShowingAddMember, onDismiss: {
            resetAddMemberSheetState()
        }) {
            addMemberSheet
        }
        .sheet(isPresented: $isShowingAddEvent) {
            AddSquadEventSheet(
                circleID: circleID,
                circleName: currentCircle?.name ?? "Squad",
                onShareCreatedEventInChat: { event in
                    let message = try await APIClient.shared.sendCircleChatMessage(
                        circleId: circleID,
                        body: "",
                        eventId: event.id
                    )
                    chatStore.upsert(message, appState: appState)
                },
                onSave: { payload, completion in
                    Task { @MainActor in
                        do {
                            let event = try await SquadEventSaveCoordinator.createEventInCircle(
                                appState: appState,
                                circleID: circleID,
                                payload: payload
                            )
                            squadEvents.append(event)
                            squadEvents.sort { $0.startsAt < $1.startsAt }
                            completion(.success(.created(event)))
                        } catch {
                            completion(.failure(error))
                        }
                    }
                }
            )
            .environmentObject(appState)
        }
        .sheet(item: $profileSheetMember) { member in
            MemberProfileActionSheet(
                friend: member,
                sharedCircles: appState.sharedSquads(with: member.id),
                squadContext: squadMemberProfileContext(for: member)
            )
                .environmentObject(appState)
                .presentationDetents([memberProfileDetent(for: member), .large])
                .presentationDragIndicator(.hidden)
                .presentationContentInteraction(.scrolls)
                .presentationBackground(Color(red: 0.025, green: 0.025, blue: 0.035))
        }
        .sheet(item: $presentedPeerProfileFocus) { focus in
            ProfileScreen(
                profileUserID: focus.id == appState.currentUserID ? nil : focus.id,
                onUserBlocked: { presentedPeerProfileFocus = nil }
            )
                .environmentObject(appState)
                .environmentObject(locationService)
                .presentationDetents([.large])
        }
        .sheet(item: $presentedEventDetail) { item in
            Group {
                switch item {
                case .attachment(let attachment):
                    eventDetailForAttachment(attachment)
                case .rosterEvent(let event):
                    EventDetailView(
                        event: event,
                        sourceCircleID: circleID,
                        onEventUpdated: { updated in
                            upsertSquadEvent(updated)
                        },
                        onEventDeleted: { eventID in
                            squadEvents.removeAll { $0.id == eventID }
                        },
                        onPostedToChat: { presentedEventDetail = nil }
                    )
                        .environmentObject(appState)
                        .environmentObject(locationService)
                }
            }
            .presentationDetents([.large])
            .presentationBackground(Color.black)
        }
        .sheet(item: $presentedDriveSummary) { drive in
            sharedDriveSummarySheet(drive)
        }
        .sheet(item: $reactionsDetailMessage) { msg in
            ChatMessageReactionsDetailSheet(
                reactions: msg.reactions,
                resolveDisplayName: { uid in
                    squadReactionParticipantName(userId: uid, message: msg)
                }
            )
            .environmentObject(appState)
            .presentationDetents([chatMessageReactionsSheetDetent(reactionCount: msg.reactions.count)])
            .presentationBackground(Color(red: 0.025, green: 0.025, blue: 0.035))
        }
        .sheet(isPresented: $isShowingSquadNotificationSettings, onDismiss: {
            if shouldShowAddMemberAfterSettingsDismiss {
                shouldShowAddMemberAfterSettingsDismiss = false
                isShowingAddMember = true
            }
        }) {
            SquadNotificationSettingsSheet(
                circleId: circleID,
                memberSubtitle: squadSettingsMemberSubtitle,
                onSuccessfullyLeftSquad: { dismiss() },
                onAddMember: {
                    shouldShowAddMemberAfterSettingsDismiss = true
                    isShowingSquadNotificationSettings = false
                },
                onMemberProfile: { profileSheetMember = $0 }
            )
            .environmentObject(appState)
        }
    }

    private var fixedSquadChrome: some View {
        VStack(spacing: 0) {
            squadHeader
                .padding(.horizontal, OttoScreenChrome.horizontalPadding)
                .padding(.bottom, 8)

            tabSelector
                .padding(.horizontal, OttoScreenChrome.horizontalPadding)

            Divider().overlay(Color.white.opacity(0.05))
        }
        .background {
            Color.black
                .ignoresSafeArea(edges: .top)
        }
    }

    private var currentCircle: DriveCircle? {
        appState.circles.first(where: { $0.id == circleID })
    }

    private var isCurrentUserSquadOwner: Bool {
        guard let circle = currentCircle else { return false }
        return circle.ownerId == appState.currentUserID
    }

    private var squadPresenceBaseSummary: String {
        let memberCount = circleMembers.count
        let onlineCount = circleMembers.filter { $0.isOnline || $0.isActive }.count
        let memberLabel = memberCount == 1 ? "member" : "members"
        guard onlineCount > 0 else {
            return "\(memberCount) \(memberLabel)"
        }
        let onlineLabel = onlineCount == 1 ? "online" : "online"
        return "\(memberCount) \(memberLabel) · \(onlineCount) \(onlineLabel)"
    }

    private var squadSharingCount: Int {
        circleMembers.filter(\.isActive).count
    }

    private var squadPresenceSummaryText: Text {
        var summary = AttributedString(squadPresenceBaseSummary)
        summary.foregroundColor = .white.opacity(0.65)
        guard squadSharingCount > 0 else { return Text(summary) }

        var separator = AttributedString(" · ")
        separator.foregroundColor = .white.opacity(0.65)
        var sharing = AttributedString("\(squadSharingCount) sharing")
        sharing.foregroundColor = .green
        summary.append(separator)
        summary.append(sharing)
        return Text(summary)
    }

    private func squadRole(for userId: String, in circle: DriveCircle) -> String {
        if circle.ownerId == userId { return "owner" }
        let raw = circle.members.first(where: { $0.id == userId })?.clubRole.lowercased() ?? "member"
        if raw == "owner" { return "owner" }
        if raw == "admin" { return "admin" }
        return "member"
    }

    private func squadMemberProfileContext(for member: FriendLocation) -> SquadMemberProfileContext? {
        guard let circle = currentCircle else { return nil }
        guard member.id != appState.currentUserID else { return nil }
        let viewer = squadRole(for: appState.currentUserID, in: circle)
        let target = squadRole(for: member.id, in: circle)
        return SquadMemberProfileContext(
            circleId: circle.id,
            viewerRoleLowercased: viewer,
            targetRoleLowercased: target
        )
    }

    private func openPeerProfile(userID: String) {
        presentedPeerProfileFocus = PresentedPeerProfileFocus(id: userID)
    }

    private var squadHeader: some View {
        HStack(spacing: 12) {
            Button {
                dismiss()
            } label: {
                Image(systemName: "chevron.left")
                    .font(.headline.weight(.semibold))
                    .foregroundStyle(.white)
                    .frame(width: 40, height: 40)
                    .background(Color.white.opacity(0.06))
                    .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
            }
            .buttonStyle(.plain)

            Group {
                if isCurrentUserSquadOwner {
                    PhotosPicker(
                        selection: $squadPhotoPickerItem,
                        matching: .images,
                        photoLibrary: PHPhotoLibrary.shared()
                    ) {
                        ZStack(alignment: .bottomTrailing) {
                            SquadAvatarView(
                                name: currentCircle?.name ?? "Squad",
                                imageUrl: currentCircle?.photoUrl,
                                icon: currentCircle?.icon ?? "person.3.fill",
                                size: 44,
                                cacheStorageKey: "squadAvatar:\(circleID)"
                            )
                            Image(systemName: "camera.circle.fill")
                                .font(.system(size: 22))
                                .symbolRenderingMode(.palette)
                                .foregroundStyle(.white, .black.opacity(0.55))
                                .offset(x: 2, y: 2)
                        }
                    }
                    .buttonStyle(.plain)
                    .onChange(of: squadPhotoPickerItem) { _, newItem in
                        Task {
                            guard let newItem else { return }
                            if let data = try? await newItem.loadTransferable(type: Data.self),
                               let ui = UIImage(data: data),
                               let jpeg = ui.jpegData(compressionQuality: 0.88) {
                                await appState.uploadSquadPhoto(circleId: circleID, imageData: jpeg)
                            }
                        }
                    }
                } else {
                    SquadAvatarView(
                        name: currentCircle?.name ?? "Squad",
                        imageUrl: currentCircle?.photoUrl,
                        icon: currentCircle?.icon ?? "person.3.fill",
                        size: 44,
                        cacheStorageKey: "squadAvatar:\(circleID)"
                    )
                }
            }

            Button {
                isShowingSquadNotificationSettings = true
            } label: {
                VStack(alignment: .leading, spacing: 3) {
                    Text(currentCircle?.name ?? "Squad")
                        .font(.title3.weight(.bold))
                        .foregroundStyle(.white)
                        .lineLimit(1)
                        .truncationMode(.tail)
                    squadPresenceSummaryText
                        .font(.caption)
                        .lineLimit(1)
                        .truncationMode(.tail)
                }
                .frame(minWidth: 0, maxWidth: .infinity, alignment: .leading)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityHint("Opens squad settings")

            Button {
                isShowingSquadNotificationSettings = true
            } label: {
                Image(systemName: "gearshape")
                    .font(.headline.weight(.semibold))
                    .foregroundStyle(.white)
                    .frame(width: 40, height: 40)
                    .background(Color.white.opacity(0.06))
                    .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Squad settings")
        }
        .padding(.vertical, 6)
    }

    private var squadChatTabUnreadCount: Int {
        appState.unreadChatCountsByCircleID[circleID] ?? 0
    }

    private var tabSelector: some View {
        HStack(spacing: 0) {
            ForEach(DetailTab.allCases, id: \.self) { tab in
                Button {
                    selectedTab = tab
                } label: {
                    VStack(spacing: 7) {
                        HStack(spacing: 6) {
                            Text(tab.rawValue)
                                .font(.subheadline.weight(selectedTab == tab ? .semibold : .regular))
                                .foregroundStyle(selectedTab == tab ? Color.purple : Color.white.opacity(0.52))
                            if tab == .chat, selectedTab != .chat, squadChatTabUnreadCount > 0 {
                                UnreadCountBadge(count: squadChatTabUnreadCount)
                            }
                        }
                        Capsule()
                            .fill(selectedTab == tab ? Color.purple : Color.clear)
                            .frame(height: 1.5)
                    }
                    .frame(maxWidth: .infinity)
                }
                .buttonStyle(.plain)
            }
        }
    }

    private var chatTab: some View {
        let nextEventCandidate = nextEventWithinSevenDays()
        let visibleNextEvent = nextUpBannerStore.visibleEvent(candidate: nextEventCandidate)
        let pendingUploadProgress = Dictionary(
            uniqueKeysWithValues: videoUploads.pendingByClientMessageId.map { ($0.key, $0.value.progress) }
        )
        return SquadChatTab(
            store: chatStore,
            circleID: circleID,
            circleMembers: circleMembers,
            squadDisplayName: currentCircle?.name ?? "Squad",
            nextEventCandidate: nextEventCandidate,
            nextEvent: visibleNextEvent,
            longPressChatMessage: $longPressChatMessage,
            chatMessageFrames: $chatMessageFrames,
            longPressChromeLiftY: $longPressChromeLiftY,
            chatKeyboardOverlap: $chatKeyboardOverlap,
            suppressAttachmentNavigationForMessageID: $suppressAttachmentNavigationForMessageID,
            presentedEventDetail: $presentedEventDetail,
            reactionsDetailMessage: $reactionsDetailMessage,
            composerFocused: $isChatComposerFocused,
            upcomingEventCard: { AnyView(upcomingEventCard($0)) },
            emptyChatState: { AnyView(emptyChatState) },
            chatMessageRow: { AnyView(chatMessageRow($0)) },
            chatDateHeader: { AnyView(chatDateHeader($0)) },
            dateHeaderText: dateHeaderText(for:),
            pendingUploadProgressByClientMessageId: pendingUploadProgress,
            onReaction: { emoji, messageId in
                Task {
                    await postCircleChatReaction(emoji: emoji, for: messageId)
                }
            },
            onNextEventCandidateChange: { event in
                Task {
                    await nextUpBannerStore.loadDismissals(for: event.map { [$0.id] } ?? [])
                }
            },
            onNextEventBannerVisible: { event in
                nextUpBannerStore.scheduleAutoHide(for: event)
            }
        )
    }

    private func nextEventWithinSevenDays(now: Date = Date()) -> EventDTO? {
        guard let sevenDaysFromNow = Calendar.current.date(byAdding: .day, value: 7, to: now) else {
            return nil
        }
        let eventsByID = Dictionary(
            (appState.upcomingEvents + squadEvents).map { ($0.id, $0) },
            uniquingKeysWith: { _, squadEvent in squadEvent }
        )
        let events = Array(eventsByID.values).filter {
            NextUpEventBannerDecision.eventQualifiesForSquadNextUpPin(event: $0, circleIdRaw: circleID)
        }
        let inProgress = events.filter { event in
            now >= event.startsAt && now <= event.eventCheckInWindowEnd
        }
        if let best = inProgress.min(by: { $0.startsAt < $1.startsAt }) {
            return best
        }
        return events
            .filter { $0.startsAt > now && $0.startsAt <= sevenDaysFromNow }
            .sorted { $0.startsAt < $1.startsAt }
            .first
    }

    private func upcomingEventCard(_ event: EventDTO) -> some View {
        HStack(spacing: 12) {
            Button {
                presentedEventDetail = .attachment(
                    CircleChatMessageDTO.EventAttachmentDTO(previewFrom: event, squadCircleId: circleID)
                )
            } label: {
                HStack(spacing: 12) {
                    Image(systemName: "pin.fill")
                        .font(.title3)
                        .foregroundStyle(.purple)
                        .frame(width: 42, height: 42)
                        .background(Color.purple.opacity(0.14))
                        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))

                    VStack(alignment: .leading, spacing: 4) {
                        Text("Next up: \(event.name)")
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(.white)
                            .lineLimit(1)
                        upcomingEventCardSubtitle(event, now: Date())
                    }

                    Spacer()
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Open next event")

            Button {
                withAnimation(.spring(response: 0.34, dampingFraction: 0.86)) {
                    nextUpBannerStore.dismiss(event)
                }
            } label: {
                Image(systemName: "xmark")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.white.opacity(0.68))
                    .frame(width: 32, height: 32)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Dismiss next event reminder")
        }
        .padding(14)
        .background(Color.white.opacity(0.045))
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .stroke(Color.white.opacity(0.12), lineWidth: 1)
        )
        .accessibilityAddTraits(.isButton)
    }

    @ViewBuilder
    private func upcomingEventCardSubtitle(_ event: EventDTO, now: Date) -> some View {
        let going = squadGoingCount(for: event)
        let muted = Color.white.opacity(0.62)
        if let phrase = chatPinnedEventStatusPhrase(for: event, now: now) {
            upcomingEventCardPinnedSubtitle(phrase: phrase, going: going, muted: muted)
                .font(.caption)
                .lineLimit(1)
        } else {
            Text(eventSummaryText(event))
                .font(.caption)
                .foregroundStyle(muted)
                .lineLimit(1)
        }
    }

    private func upcomingEventCardPinnedSubtitle(phrase: String, going: Int, muted: Color) -> Text {
        var subtitle = AttributedString(phrase)
        subtitle.foregroundColor = .green
        var goingText = AttributedString(" · \(going) going")
        goingText.foregroundColor = muted
        subtitle.append(goingText)
        return Text(subtitle)
    }

    /// When non-nil, this replaces the formatted start date · squad-going summary with green leading text.
    private func chatPinnedEventStatusPhrase(for event: EventDTO, now: Date) -> String? {
        let end = event.eventCheckInWindowEnd
        if now >= event.startsAt && now <= end {
            return "Happening now."
        }
        if now < event.startsAt {
            let secondsUntilStart = event.startsAt.timeIntervalSince(now)
            if secondsUntilStart > 0 && secondsUntilStart <= 5 * 3600 {
                let comps = Calendar.current.dateComponents([.hour, .minute], from: now, to: event.startsAt)
                let h = max(0, comps.hour ?? 0)
                let m = max(0, comps.minute ?? 0)
                if h > 0 {
                    return "Starts in \(h)h \(m)m"
                }
                if m > 0 {
                    return "Starts in \(m)m"
                }
                return "Starts in 1m"
            }
            if Calendar.current.isDate(event.startsAt, inSameDayAs: now) {
                return "Happening today."
            }
        }
        return nil
    }

    private var emptyChatState: some View {
        Group {
            if isSquadChatFetchError(chatStore.errorMessage) {
                UnifiedEmptyStateView(
                    title: String(localized: "fetch_error_squad_chat_title"),
                    message: String(localized: "fetch_error_refresh_body"),
                    systemImage: "exclamationmark.triangle",
                    actionTitle: String(localized: "fetch_error_refresh_action"),
                    action: {
                        Task { await chatStore.pullToRefresh(appState: appState) }
                    }
                )
                .frame(minHeight: 360)
            } else {
                VStack(spacing: 10) {
                    Image(systemName: "bubble.left.and.bubble.right")
                        .font(.largeTitle)
                        .foregroundStyle(.purple)
                    Text("Start the squad chat")
                        .font(.headline)
                        .foregroundStyle(.white)
                }
                .frame(maxWidth: .infinity)
                .padding(.top, 44)
            }
        }
    }

    private var chatMentionDisplayNameByUserId: [String: String] {
        var names = Dictionary(uniqueKeysWithValues: circleMembers.map { ($0.id, $0.name) })
        names[SquadChatAllMention.userId] = "All"
        return names
    }

    @ViewBuilder
    private func chatMessageRow(_ message: CircleChatMessageDTO) -> some View {
        let pendingUpload = videoUploads.pending(for: message.clientMessageId)
        if message.messageType == "system" {
            systemChatMessageRow(message)
        } else {
            SquadChatMessageRow(
                squadCircleId: circleID,
                circleMembers: circleMembers,
                message: message,
                currentUserID: appState.currentUserID,
                mentionDisplayNameByUserId: chatMentionDisplayNameByUserId,
                localVideoThumbnail: pendingUpload?.thumbnail,
                videoUploadProgress: pendingUpload?.progress,
                videoUploadPhase: pendingUpload?.phase,
                longPressChatMessage: $longPressChatMessage,
                reactionsDetailMessage: $reactionsDetailMessage,
                suppressAttachmentNavigationForMessageID: $suppressAttachmentNavigationForMessageID,
                longPressChromeLiftY: $longPressChromeLiftY,
                presentedEventDetail: $presentedEventDetail,
                onAvatarTap: { openPeerProfile(userID: message.resolvedSenderUserId) },
                onMentionTap: { userID in
                    guard userID != SquadChatAllMention.userId else { return }
                    openPeerProfile(userID: userID)
                },
                onDoubleTapHeartReaction: { msg in
                    Task {
                        await postCircleChatReaction(
                            emoji: ChatReactionEmojiBar.quickReactionHeartEmoji,
                            for: msg.id
                        )
                    }
                },
                onJumpToQuotedMessage: { targetId in
                    isChatComposerFocused = false
                    chatStore.jumpToQuotedMessage(targetId, appState: appState)
                },
                onRemovePendingMessage: { messageId in
                    guard let message = chatStore.messages.first(where: { $0.id == messageId }),
                          let clientMessageId = message.clientMessageId else { return }
                    ChatVideoUploadCoordinator.shared.cancel(clientMessageId: clientMessageId)
                    appState.chatStore.removePendingSquadMessage(circleID: circleID, clientMessageId: clientMessageId)
                },
                reportsFrame: false,
                eventCard: { suppress in
                    eventAttachmentCard(
                        message.eventAttachment,
                        message: message,
                        cardWidth: ChatMessageTextBubble.standardLayoutWidth,
                        suppressNavigation: suppress,
                        onNavigate: {
                            if let attachment = message.eventAttachment, !attachment.isParentDeleted {
                                presentedEventDetail = .attachment(attachment)
                            }
                        }
                    )
                },
                driveCard: { suppress in
                    driveAttachmentCard(
                        message: message,
                        attachment: message.driveAttachment,
                        cardWidth: ChatMessageTextBubble.standardLayoutWidth,
                        suppressNavigation: suppress,
                        onNavigate: {
                            if let attachment = message.driveAttachment {
                                openSharedDriveSummary(attachment: attachment)
                            }
                        }
                    )
                },
                placeCard: { suppress in
                    placeAttachmentCard(
                        message: message,
                        attachment: message.placeAttachment,
                        cardWidth: ChatMessageTextBubble.standardLayoutWidth,
                        suppressNavigation: suppress,
                        onNavigate: {
                            if let attachment = message.placeAttachment {
                                openSharedPlaceOnMap(attachment: attachment, messageId: message.id)
                            }
                        }
                    )
                }
            )
        }
    }

    private func chatDateHeader(_ title: String) -> some View {
        Text(title)
            .font(.caption2.weight(.semibold))
            .foregroundStyle(.white.opacity(0.45))
            .frame(maxWidth: .infinity)
            .padding(.vertical, 6)
    }

    private func attributedMessageBody(_ body: String, foregroundColor: Color) -> AttributedString {
        var attributed = AttributedString(body)
        attributed.foregroundColor = foregroundColor
        guard let detector = try? NSDataDetector(types: NSTextCheckingResult.CheckingType.link.rawValue) else {
            return attributed
        }
        let nsRange = NSRange(body.startIndex..<body.endIndex, in: body)
        for match in detector.matches(in: body, options: [], range: nsRange) {
            guard let url = match.url,
                  let range = Range(match.range, in: body),
                  let attributedRange = Range(range, in: attributed) else { continue }
            attributed[attributedRange].link = url
            attributed[attributedRange].foregroundColor = foregroundColor
            attributed[attributedRange].underlineStyle = .single
        }
        return attributed
    }

    private func submitChatAttachmentRsvp(eventId: String, status: String) {
        Task { @MainActor in
            chatRsvpSubmittingEventId = eventId
            await appState.setEventRsvp(eventID: eventId, status: status)
            chatRsvpSubmittingEventId = nil
            await refreshSquadEvents()
        }
    }

    @ViewBuilder
    private func eventAttachmentCard(
        _ attachment: CircleChatMessageDTO.EventAttachmentDTO?,
        message: CircleChatMessageDTO,
        cardWidth: CGFloat = 320,
        suppressNavigation: Bool = false,
        onNavigate: @escaping () -> Void
    ) -> some View {
        if let attachment {
            let senderName = message.sender?.displayName ?? memberName(for: message.resolvedSenderUserId)
            let firstName = senderName.split(separator: " ").first.map(String.init) ?? senderName
            if attachment.isParentDeleted {
                ChatUnavailableShareAttachmentCard(
                    kind: .event,
                    sharedByFirstName: firstName,
                    messageCreatedAt: message.createdAt,
                    cardWidth: cardWidth
                )
            } else {
                ChatEventAttachmentPreviewCard(
                    attachment: attachment,
                    resolvedEvent: eventForAttachment(attachment),
                    cardWidth: cardWidth,
                    suppressNavigation: suppressNavigation,
                    rsvpSubmitting: chatRsvpSubmittingEventId == attachment.eventId,
                    meUser: appState.allUsers.first(where: { $0.id == appState.currentUserID }),
                    onRsvp: { status in
                        submitChatAttachmentRsvp(eventId: attachment.eventId, status: status)
                    },
                    onNavigate: onNavigate,
                    messageId: message.id,
                    onLongPress: {
                        ChatMessageActionFeedback.dismissKeyboard()
                        withAnimation(.easeOut(duration: 0.12)) {
                            longPressChromeLiftY = 0
                            longPressChatMessage = message
                        }
                        suppressAttachmentNavigationForMessageID = message.id
                        let id = message.id
                        Task { @MainActor in
                            try? await Task.sleep(nanoseconds: ChatLongPressTiming.attachmentSuppressDelayNanoseconds)
                            if suppressAttachmentNavigationForMessageID == id {
                                suppressAttachmentNavigationForMessageID = nil
                            }
                        }
                    },
                    onDoubleTapHeart: {
                        Task {
                            await postCircleChatReaction(
                                emoji: ChatReactionEmojiBar.quickReactionHeartEmoji,
                                for: message.id
                            )
                        }
                    }
                )
            }
        }
    }

    @ViewBuilder
    private func driveAttachmentCard(
        message: CircleChatMessageDTO,
        attachment: CircleChatMessageDTO.DriveAttachmentDTO?,
        cardWidth: CGFloat = 320,
        suppressNavigation: Bool = false,
        onNavigate: @escaping () -> Void
    ) -> some View {
        if let attachment {
            let senderName = message.sender?.displayName ?? memberName(for: message.resolvedSenderUserId)
            let firstName = senderName.split(separator: " ").first.map(String.init) ?? senderName
            if attachment.isParentDeleted {
                ChatUnavailableShareAttachmentCard(
                    kind: .drive,
                    sharedByFirstName: firstName,
                    messageCreatedAt: message.createdAt,
                    cardWidth: cardWidth
                )
            } else {
                ChatDriveAttachmentPreviewCard(
                    attachment: attachment,
                    sharedByFirstName: firstName,
                    messageCreatedAt: message.createdAt,
                    messageId: message.id,
                    lineSourceID: "chat-drive-\(message.id)",
                    cardWidth: cardWidth,
                    suppressNavigation: suppressNavigation,
                    onLongPress: {
                        ChatMessageActionFeedback.dismissKeyboard()
                        withAnimation(.easeOut(duration: 0.12)) {
                            longPressChatMessage = message
                        }
                        suppressAttachmentNavigationForMessageID = message.id
                        let id = message.id
                        Task { @MainActor in
                            try? await Task.sleep(nanoseconds: ChatLongPressTiming.attachmentSuppressDelayNanoseconds)
                            if suppressAttachmentNavigationForMessageID == id {
                                suppressAttachmentNavigationForMessageID = nil
                            }
                        }
                    },
                    onDoubleTapHeart: {
                        Task {
                            await postCircleChatReaction(
                                emoji: ChatReactionEmojiBar.quickReactionHeartEmoji,
                                for: message.id
                            )
                        }
                    },
                    onNavigate: onNavigate
                )
            }
        }
    }

    @ViewBuilder
    private func sharedDriveSummarySheet(_ drive: DriveDTO) -> some View {
        DriveSummaryScreen(
            drive: drive,
            isOwner: drive.userId == appState.currentUserID,
            garageCars: drive.userId == appState.currentUserID ? appState.garageCars : [],
            lockedShareCircleID: circleID,
            onDriveUpdated: { updated in
                presentedDriveSummary = updated
            },
            onDriveDeleted: {
                presentedDriveSummary = nil
            }
        )
        .environmentObject(appState)
        .presentationDetents([.large])
        .presentationDragIndicator(.visible)
    }

    private func openSharedDriveSummary(attachment: CircleChatMessageDTO.DriveAttachmentDTO) {
        guard !attachment.isParentDeleted else { return }
        guard !isLoadingSharedDriveSummary else { return }
        isLoadingSharedDriveSummary = true
        Task { @MainActor in
            defer { isLoadingSharedDriveSummary = false }
            do {
                let drive = try await APIClient.shared.fetchDrive(
                    driveId: attachment.driveId,
                    circleId: circleID
                )
                presentedDriveSummary = drive
            } catch {
                appState.errorMessage = "Couldn't open drive summary."
            }
        }
    }

    private func openSharedPlaceOnMap(
        attachment: CircleChatMessageDTO.PlaceAttachmentDTO,
        messageId: String
    ) {
        guard !attachment.isParentDeleted else { return }
        let snapshot = attachment.savedPlaceSnapshot(fallbackID: "chat:\(messageId)")
        appState.requestMapTabCenteredOn(
            latitude: attachment.latitude,
            longitude: attachment.longitude,
            savedPlaceID: attachment.placeId,
            savedPlaceSnapshot: snapshot
        )
    }

    @ViewBuilder
    private func placeAttachmentCard(
        message: CircleChatMessageDTO,
        attachment: CircleChatMessageDTO.PlaceAttachmentDTO?,
        cardWidth: CGFloat = 320,
        suppressNavigation: Bool = false,
        onNavigate: @escaping () -> Void
    ) -> some View {
        if let attachment {
            let senderName = message.sender?.displayName ?? memberName(for: message.resolvedSenderUserId)
            let firstName = senderName.split(separator: " ").first.map(String.init) ?? senderName
            if attachment.isParentDeleted {
                ChatUnavailableShareAttachmentCard(
                    kind: .place,
                    sharedByFirstName: firstName,
                    messageCreatedAt: message.createdAt,
                    cardWidth: cardWidth
                )
            } else {
                ChatPlaceAttachmentPreviewCard(
                    attachment: attachment,
                    sharedByFirstName: firstName,
                    messageCreatedAt: message.createdAt,
                    messageId: message.id,
                    cardWidth: cardWidth,
                    suppressNavigation: suppressNavigation,
                    onLongPress: {
                        ChatMessageActionFeedback.dismissKeyboard()
                        withAnimation(.easeOut(duration: 0.12)) {
                            longPressChatMessage = message
                        }
                        suppressAttachmentNavigationForMessageID = message.id
                        let id = message.id
                        Task { @MainActor in
                            try? await Task.sleep(nanoseconds: ChatLongPressTiming.attachmentSuppressDelayNanoseconds)
                            if suppressAttachmentNavigationForMessageID == id {
                                suppressAttachmentNavigationForMessageID = nil
                            }
                        }
                    },
                    onDoubleTapHeart: {
                        Task {
                            await postCircleChatReaction(
                                emoji: ChatReactionEmojiBar.quickReactionHeartEmoji,
                                for: message.id
                            )
                        }
                    },
                    onNavigate: onNavigate
                )
            }
        }
    }

    private func clearSquadChatLongPressChrome() {
        longPressChatMessage = nil
        longPressChromeLiftY = 0
        chatMessageFrames = [:]
    }

    private func applyPendingSquadChatTabFocusIfNeeded() {
        guard let focus = appState.pendingCircleFocus,
              focus.circleID == circleID,
              focus.openChatTab else { return }
        _ = appState.consumePendingCircleFocus()
        presentedEventDetail = nil
        selectedTab = .chat
        chatStore.activateChatTab(appState: appState)
    }

    private func beginChatReply(to message: CircleChatMessageDTO) {
        let name = message.sender?.displayName ?? memberName(for: message.resolvedSenderUserId)
        chatStore.replyDraft.messageId = message.id
        chatStore.replyDraft.authorName = name
        let trimmed = message.body.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty, let driveAttachment = message.driveAttachment {
            chatStore.replyDraft.snippet = driveAttachment.isParentDeleted ? "Deleted drive" : "Drive"
        } else if trimmed.isEmpty, message.placeAttachment != nil {
            chatStore.replyDraft.snippet = message.placeAttachment?.isParentDeleted == true
                ? "Deleted place"
                : (message.placeAttachment?.displayTitle ?? String(localized: "chat_place_attachment_fallback_title"))
        } else if trimmed.isEmpty, message.imageUrl != nil {
            chatStore.replyDraft.snippet = ChatImageURLDisplay.replySnippet(for: message.imageUrl)
        } else if trimmed.isEmpty, message.videoAttachment != nil {
            chatStore.replyDraft.snippet = "Video"
        } else if trimmed.isEmpty, message.eventAttachment != nil {
            chatStore.replyDraft.snippet = "Event"
        } else {
            chatStore.replyDraft.snippet = trimmed
        }
        chatStore.replyDraft.avatarURL = message.sender?.avatarUrl
        Task { @MainActor in
            await Task.yield()
            isChatComposerFocused = true
        }
    }

    private func postCircleChatReaction(emoji: String, for messageId: String) async {
        do {
            let updated = try await APIClient.shared.setCircleChatMessageReaction(
                circleId: circleID,
                messageId: messageId,
                emoji: emoji
            )
            chatStore.upsert(updated, appState: appState)
            clearSquadChatLongPressChrome()
        } catch {
            clearSquadChatLongPressChrome()
        }
    }

    private var eventsTab: some View {
        SquadEventsTab(
            filteredEvents: squadEvents,
            isLoading: isLoadingSquadEvents,
            errorMessage: squadEventsErrorMessage,
            selectEvent: { presentedEventDetail = .rosterEvent($0) },
            refresh: {
                await refreshSquadEvents()
            },
            filterPicker: { AnyView(eventsTabAddEventHeader) },
            eventRow: { AnyView(squadEventRow($0, groupedInSection: $1)) }
        )
    }

    private var gridTab: some View {
        SquadGridTab(
            grid: squadGrid,
            isLoading: isLoadingSquadGrid,
            errorMessage: squadGridError,
            isPageEmpty: squadGridPageIsEmpty,
            onSelectLeader: { leader in
                openPeerProfile(userID: leader.userId)
            },
            refresh: {
                await refreshSquadGrid()
            }
        )
    }

    private var squadGridPageIsEmpty: Bool {
        guard let g = squadGrid else { return false }
        return !isLoadingSquadGrid && g.metrics.allSatisfy(\.leaders.isEmpty)
    }

    private func refreshSquadGrid() async {
        if isLoadingSquadGrid { return }
        isLoadingSquadGrid = true
        squadGridError = nil
        defer { isLoadingSquadGrid = false }
        do {
            let next = try await APIClient.shared.fetchSquadGrid(circleId: circleID, range: squadGridRange)
            squadGrid = next
        } catch {
            OttoLog.ui.error("refreshSquadGrid failed circle=\(circleID) \(String(describing: error))")
            if squadGrid == nil {
                squadGridError = "Couldn’t load Grid."
            } else {
                squadGridError = "Couldn’t refresh Grid."
            }
        }
    }

    private func friendLocationForGridLeader(_ leader: SquadGridLeaderDTO) -> FriendLocation {
        if let existing = circleMembers.first(where: { $0.id == leader.userId }) {
            return existing
        }
        let accent = MapAccentPalette.resolvedColor(
            mapAccentKey: leader.mapAccentKey,
            userId: leader.userId
        )
        return FriendLocation(
            id: leader.userId,
            name: leader.displayName,
            avatarName: leader.displayName,
            avatarUrl: leader.avatarUrl,
            car: "Unknown Car",
            clubRole: "Member",
            lastRun: "Recent drive",
            coordinate: CLLocationCoordinate2D(latitude: 0, longitude: 0),
            speedMph: 0,
            isOnline: false,
            isActive: false,
            accentColor: accent,
            movementMode: .unknown
        )
    }

    private var eventsTabAddEventHeader: some View {
        HStack {
            Spacer(minLength: 0)
            Button {
                isShowingAddEvent = true
            } label: {
                HStack(spacing: 4) {
                    Image(systemName: "plus")
                    Text("Add Event")
                }
                .font(.caption.weight(.semibold))
                .foregroundStyle(.white.opacity(0.88))
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
                .background(Color.white.opacity(0.08))
                .clipShape(Capsule())
                .contentShape(Capsule())
            }
            .buttonStyle(.plain)
        }
    }

    private func systemChatMessageRow(_ message: CircleChatMessageDTO) -> some View {
        HStack {
            Spacer(minLength: 24)
            Label(message.body, systemImage: systemImage(for: message.systemKind))
                .font(.caption.weight(.semibold))
                .foregroundStyle(.white.opacity(0.72))
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
                .background(Color.white.opacity(0.07))
                .clipShape(Capsule())
            Spacer(minLength: 24)
        }
        .padding(.vertical, 2)
    }

    private func systemImage(for systemKind: String?) -> String {
        switch systemKind {
        case "circle_member_joined":
            return "person.crop.circle.badge.checkmark"
        default:
            return "sparkles"
        }
    }

    private func sortedActiveEvents(_ events: [EventDTO], now: Date = Date()) -> [EventDTO] {
        events
            .filter { $0.eventCheckInWindowEnd >= now }
            .sorted { lhs, rhs in
                let lhsStarted = lhs.startsAt <= now
                let rhsStarted = rhs.startsAt <= now
                if lhsStarted != rhsStarted { return lhsStarted }
                if lhsStarted && rhsStarted { return lhs.startsAt > rhs.startsAt }
                return lhs.startsAt < rhs.startsAt
            }
    }

    private func refreshSquadEvents() async {
        isLoadingSquadEvents = true
        squadEventsErrorMessage = nil
        defer { isLoadingSquadEvents = false }

        do {
            squadEvents = sortedActiveEvents(
                try await APIClient.shared.fetchEvents(
                    scope: "all",
                    limit: 100,
                    visibility: "official",
                    circleId: circleID
                )
            )
        } catch is CancellationError {
            return
        } catch let urlError as URLError where urlError.code == .cancelled {
            return
        } catch {
            guard !Task.isCancelled else { return }
            squadEventsErrorMessage = "Couldn't load squad events."
        }
    }

    private func upsertSquadEvent(_ event: EventDTO) {
        if let index = squadEvents.firstIndex(where: { $0.id == event.id }) {
            squadEvents[index] = event
        } else {
            squadEvents.append(event)
        }
        squadEvents.sort { $0.startsAt < $1.startsAt }
    }

    private func memberName(for userID: String) -> String {
        circleMembers.first(where: { $0.id == userID })?.name ?? "Someone"
    }

    private func squadReactionParticipantName(userId: String, message: CircleChatMessageDTO) -> String {
        if let hydrated = message.reactions.first(where: { $0.userId == userId })?.user?.displayName,
           !hydrated.isEmpty {
            return hydrated
        }
        if userId == appState.currentUserID {
            return appState.allUsers.first(where: { $0.id == userId })?.displayName ?? "You"
        }
        return memberName(for: userId)
    }

    private func memberProfileLocation(for message: CircleChatMessageDTO) -> FriendLocation {
        if let existing = circleMembers.first(where: { $0.id == message.resolvedSenderUserId }) {
            return existing
        }
        let senderName = message.sender?.displayName ?? "Someone"
        let accent = MapAccentPalette.resolvedColor(
            mapAccentKey: message.sender?.mapAccentKey,
            userId: message.resolvedSenderUserId
        )
        return FriendLocation(
            id: message.resolvedSenderUserId,
            name: senderName,
            avatarName: senderName,
            avatarUrl: message.sender?.avatarUrl,
            car: "Unknown Car",
            clubRole: "Member",
            lastRun: "Recent drive",
            coordinate: CLLocationCoordinate2D(latitude: 0, longitude: 0),
            speedMph: 0,
            isOnline: false,
            isActive: false,
            accentColor: accent,
            movementMode: .unknown
        )
    }

    private func memberProfileLocation(forUserID userID: String) -> FriendLocation {
        if let existing = circleMembers.first(where: { $0.id == userID }) {
            return existing
        }
        let knownUser = appState.allUsers.first(where: { $0.id == userID })
        let name = knownUser?.displayName ?? memberName(for: userID)
        let accent = MapAccentPalette.resolvedColor(
            mapAccentKey: knownUser?.mapAccentKey,
            userId: userID
        )
        return FriendLocation(
            id: userID,
            name: name,
            avatarName: name,
            avatarUrl: knownUser?.avatarUrl,
            car: "Unknown Car",
            clubRole: "Member",
            lastRun: "Recent drive",
            coordinate: CLLocationCoordinate2D(latitude: 0, longitude: 0),
            speedMph: 0,
            isOnline: false,
            isActive: false,
            accentColor: accent,
            movementMode: .unknown
        )
    }

    private func timeText(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.timeStyle = .short
        formatter.dateStyle = .none
        return formatter.string(from: date)
    }

    private func dateHeaderText(for date: Date) -> String {
        let calendar = Calendar.current
        if calendar.isDateInToday(date) {
            return "Today"
        }
        if calendar.isDateInYesterday(date) {
            return "Yesterday"
        }
        let formatter = DateFormatter()
        formatter.dateFormat = "MMM d, yyyy"
        return formatter.string(from: date)
    }

    private func eventForAttachment(_ attachment: CircleChatMessageDTO.EventAttachmentDTO) -> EventDTO? {
        appState.resolvedEventForChatAttachment(eventId: attachment.eventId, squadEvents: squadEvents)
    }

    private func eventLocationText(_ event: EventDTO?) -> String? {
        guard let event else { return nil }
        if let label = event.address?.label, !label.isEmpty { return label }
        let cityRegion = [event.address?.city, event.address?.region]
            .compactMap { $0?.isEmpty == false ? $0 : nil }
            .joined(separator: ", ")
        return cityRegion.isEmpty ? nil : cityRegion
    }

    private func eventDetailForAttachment(_ attachment: CircleChatMessageDTO.EventAttachmentDTO) -> some View {
        let event = eventForAttachment(attachment)
        return EventDetailView(
            event: event ?? EventDTO(
                id: attachment.eventId,
                slug: nil,
                visibility: attachment.visibility,
                circleId: attachment.circleId,
                name: attachment.name ?? "Event",
                description: nil,
                startsAt: attachment.startsAt ?? Date(),
                endsAt: nil,
                address: EventDTO.AddressDTO(
                    label: attachment.addressLabel,
                    street1: nil,
                    street2: nil,
                    city: nil,
                    region: nil,
                    postalCode: nil,
                    country: nil
                ),
                location: nil,
                bannerImage: attachment.bannerImageUrl.map { EventDTO.BannerImageDTO(url: $0, aspectRatio: nil) },
                rsvpCounts: nil,
                contactsGoing: [],
                currentUserRsvp: nil,
                currentUserCheckIn: nil
            ),
            sourceCircleID: circleID
        )
        .environmentObject(appState)
        .environmentObject(locationService)
    }

    private func eventSummaryText(_ event: EventDTO) -> String {
        let formatter = DateFormatter()
        formatter.dateStyle = .medium
        formatter.timeStyle = .short
        let going = squadGoingCount(for: event)
        return "\(formatter.string(from: event.startsAt)) · \(going) going"
    }

    private func squadGoingCount(for event: EventDTO) -> Int {
        let squadMemberIDs = Set(circleMembers.map(\.id))
        var goingIDs = Set(event.contactsGoing.map(\.id)).intersection(squadMemberIDs)
        if event.currentUserRsvp == "going", squadMemberIDs.contains(appState.currentUserID) {
            goingIDs.insert(appState.currentUserID)
        }
        return goingIDs.count
    }

    private func squadEventRow(_ event: EventDTO, groupedInSection: Bool = false) -> some View {
        EventRow(
            event: event,
            showBanner: false,
            goingCountOverride: squadGoingCount(for: event),
            groupedInSection: groupedInSection
        )
    }

    private var shareSignupInviteActionsEnabled: Bool {
        guard let signupInviteRemaining else { return false }
        return signupInviteRemaining > 0
    }

    @ViewBuilder
    private var signupInviteBalanceCard: some View {
        VStack(alignment: .leading, spacing: 6) {
            if isLoadingSignupInviteBalance, signupInviteRemaining == nil {
                HStack(spacing: 8) {
                    ProgressView()
                        .scaleEffect(0.85)
                        .tint(.purple)
                    Text(String(localized: "squad_signup_invites_loading"))
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(.white.opacity(0.85))
                }
            } else if let signupInviteRemaining {
                Text(signupInviteBalanceTitle(remaining: signupInviteRemaining))
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(signupInviteRemaining > 0 ? .white : Color.orange.opacity(0.95))
                Text(String(localized: "squad_signup_invites_footnote"))
                    .font(.caption)
                    .foregroundStyle(.white.opacity(0.62))
                    .fixedSize(horizontal: false, vertical: true)
                if signupInviteRemaining == 0 {
                    if let earnMore = signupInviteEarnMoreMessage {
                        Text(earnMore)
                            .font(.caption)
                            .foregroundStyle(Color(red: 0.70, green: 0.25, blue: 1.0))
                    }
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 14)
        .padding(.vertical, 12)
        .background(
            RoundedRectangle(cornerRadius: 13, style: .continuous)
                .fill(Color.white.opacity(0.06))
        )
        .overlay(
            RoundedRectangle(cornerRadius: 13, style: .continuous)
                .stroke(Color.white.opacity(0.12), lineWidth: 1)
        )
    }

    private func signupInviteBalanceTitle(remaining: Int) -> String {
        if remaining == 0 {
            return String(localized: "squad_signup_invites_none")
        }
        let key = remaining == 1 ? "squad_signup_invites_available_one" : "squad_signup_invites_available_other"
        return String(format: String(localized: String.LocalizationValue(key)), Int64(remaining))
    }

    private var signupInviteEarnMoreMessage: String? {
        guard let count = signupInviteEarnAtNextLevelCount, count > 0 else { return nil }
        let levelName = signupInviteNextLevelDisplayName?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        guard !levelName.isEmpty else { return nil }
        let key = count == 1
            ? "squad_signup_invites_earn_at_level_one"
            : "squad_signup_invites_earn_at_level_other"
        return String(format: String(localized: String.LocalizationValue(key)), Int64(count), levelName)
    }

    private func applySignupInviteEarnAtNextLevel(from balance: SignupInviteBalanceDTO) {
        if let count = balance.invitesPerLevelUp, count > 0,
           let levelName = balance.nextLevelDisplayName?.trimmingCharacters(in: .whitespacesAndNewlines),
           !levelName.isEmpty {
            signupInviteEarnAtNextLevelCount = count
            signupInviteNextLevelDisplayName = levelName
        } else {
            signupInviteEarnAtNextLevelCount = nil
            signupInviteNextLevelDisplayName = nil
        }
    }

    private var addMemberSheet: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Add to Squad")
                            .font(.system(size: 50, weight: .bold, design: .default))
                            .minimumScaleFactor(0.6)
                            .foregroundStyle(.white)
                        Text("Invite friends to join your squad.")
                            .font(.subheadline)
                            .foregroundStyle(.white.opacity(0.75))
                    }

                    signupInviteBalanceCard

                    HStack(alignment: .top, spacing: 10) {
                        InviteSheetActionButton(
                            title: "Copy invite link",
                            busyTitle: "Copying…",
                            systemImage: "link",
                            isBusy: shareInviteBusy == .copy
                        ) {
                            Task { await copyInviteLink() }
                        }
                        .disabled(!shareSignupInviteActionsEnabled || (shareInviteBusy != nil && shareInviteBusy != .copy))

                        InviteSheetActionButton(
                            title: "Invite by SMS",
                            busyTitle: "Opening…",
                            systemImage: "message.fill",
                            isBusy: shareInviteBusy == .sms
                        ) {
                            Task { await openSMSWithShareLink() }
                        }
                        .disabled(!shareSignupInviteActionsEnabled || (shareInviteBusy != nil && shareInviteBusy != .sms))
                    }

                    VStack(alignment: .leading, spacing: 10) {
                        Text("Invite by Name or Phone")
                            .font(.title3.weight(.semibold))
                            .foregroundStyle(.white)
                        HStack(spacing: 10) {
                            Image(systemName: "magnifyingglass")
                                .foregroundStyle(Color(red: 0.70, green: 0.25, blue: 1.0))
                            TextField("Search by name or phone", text: $inviteSearchText)
                                .textInputAutocapitalization(.words)
                                .foregroundStyle(.white)
                            Spacer(minLength: 0)
                            ZStack {
                                RoundedRectangle(cornerRadius: 8, style: .continuous)
                                    .fill(Color.black.opacity(0.35))
                                Image(systemName: "person.badge.plus")
                                    .foregroundStyle(Color(red: 0.70, green: 0.25, blue: 1.0))
                                    .font(.subheadline.weight(.semibold))
                            }
                            .frame(width: 30, height: 30)
                            if isLookupLoading {
                                ProgressView()
                                    .scaleEffect(0.9)
                            }
                        }
                        .padding(.horizontal, 14)
                        .padding(.vertical, 12)
                        .background(
                            RoundedRectangle(cornerRadius: 13, style: .continuous)
                                .fill(Color.white.opacity(0.05))
                        )
                        .overlay(
                            RoundedRectangle(cornerRadius: 13, style: .continuous)
                                .stroke(Color.white.opacity(0.16), lineWidth: 1)
                        )
                    }

                    Group {
                        if isPhonePrimaryInviteQuery(trimmedInviteSearch) {
                            if let foundUser = lookupResultUser {
                                searchResultUserCard(foundUser)
                            } else if hasAttemptedLookup, !trimmedInviteSearch.isEmpty {
                                searchResultUnknownPhoneCard
                            }
                        } else if trimmedInviteSearch.count >= 2 {
                            let matches = inviteNameSearchMatches
                            if matches.isEmpty {
                                Text("No matches. Try a name from your contacts or a US phone number.")
                                    .font(.subheadline)
                                    .foregroundStyle(.white.opacity(0.65))
                                    .frame(maxWidth: .infinity, alignment: .leading)
                            } else {
                                ForEach(matches, id: \.id) { user in
                                    searchResultUserCard(user)
                                }
                            }
                        }
                    }

                    if let lookupMessage {
                        if lookupMessage.hasPrefix("Can't find") {
                            HStack(spacing: 0) {
                                Text("Can't find them? ")
                                    .foregroundStyle(.white.opacity(0.65))
                                Text("Check the number")
                                    .foregroundStyle(Color(red: 0.70, green: 0.25, blue: 1.0))
                                Text(" and try again.")
                                    .foregroundStyle(.white.opacity(0.65))
                            }
                            .font(.subheadline)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .fixedSize(horizontal: false, vertical: true)
                        } else {
                            Text(lookupMessage)
                                .font(.subheadline)
                                .foregroundStyle(.white.opacity(0.75))
                        }
                    }

                }
                .padding(.horizontal, 16)
                .padding(.top, 10)
                .padding(.bottom, 26)
            }
            .scrollDismissesKeyboard(.interactively)
            .background(Color.black)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button {
                        cancelSMSInviteFlow(reason: "back tapped")
                        isShowingAddMember = false
                    } label: {
                        Label("Back", systemImage: "chevron.left")
                            .font(.headline.weight(.semibold))
                    }
                }
                ToolbarItem(placement: .topBarTrailing) {
                    if isOpeningSMSInvite || shareInviteBusy == .sms {
                        Button("Cancel") {
                            cancelSMSInviteFlow(reason: "cancel tapped")
                            shareInviteBusy = nil
                        }
                        .font(.subheadline.weight(.semibold))
                    }
                }
            }
            .onAppear {
                Task {
                    if appState.contacts.isEmpty {
                        await appState.refreshContacts()
                    }
                    await refreshSignupInviteBalance()
                    if shareSignupInviteActionsEnabled {
                        await prefetchShareInviteLinkIfNeeded()
                    }
                }
            }
            .animation(.spring(response: 0.38, dampingFraction: 0.86), value: addMemberSheetToast)
            .appToastOverlay(toast: addMemberSheetToast, topPadding: 10) {
                addMemberSheetToast = nil
            }
            .onChange(of: inviteSearchText) { _, _ in
                scheduleLookup()
            }
            .onDisappear {
                lookupTask?.cancel()
            }
        }
    }

    private var circleMembers: [FriendLocation] {
        appState.circles.first(where: { $0.id == circleID })?.members ?? []
    }

    private var squadSettingsMemberSubtitle: String {
        let members = circleMembers
        let memberCount = members.count
        let onlineCount = members.filter { $0.isOnline || $0.isActive }.count
        let memberLabel = memberCount == 1 ? "member" : "members"
        guard onlineCount > 0 else {
            return "\(memberCount) \(memberLabel)"
        }
        let onlineLabel = onlineCount == 1 ? "online" : "online"
        return "\(memberCount) \(memberLabel) · \(onlineCount) \(onlineLabel)"
    }

    private func memberProfileDetent(for member: FriendLocation) -> PresentationDetent {
        let rowCount = max(1, appState.sharedSquads(with: member.id).count)
        var height = 354 + rowCount * 62
        if let ctx = squadMemberProfileContext(for: member) {
            height += Int(ctx.estimatedManagementChromeExtraPoints())
        }
        height = min(height, 720)
        return .height(CGFloat(height))
    }

    private var trimmedInviteSearch: String {
        inviteSearchText.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    /// Phone-only lookup path; any letters mean we treat the field as a name search across squad mates.
    private func isPhonePrimaryInviteQuery(_ raw: String) -> Bool {
        let t = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !t.isEmpty else { return false }
        if t.contains(where: { $0.isLetter }) { return false }
        return isValidNorthAmericanPhoneNumber(t)
    }

    private var uniqueSquadMatesFromMySquads: [FriendLocation] {
        let myId = appState.currentUserID
        var seen = Set<String>()
        var out: [FriendLocation] = []
        for circle in appState.circles {
            for m in circle.members {
                guard m.id != myId else { continue }
                if seen.insert(m.id).inserted {
                    out.append(m)
                }
            }
        }
        return out
    }

    private var inviteNameSearchMatches: [UserDTO] {
        let q = trimmedInviteSearch
        guard q.count >= 2, !isPhonePrimaryInviteQuery(q) else { return [] }
        let inSquad = Set(circleMembers.map(\.id))
        let myId = appState.currentUserID
        return appState.contacts
            .filter { user in
                guard user.id != myId, !inSquad.contains(user.id) else { return false }
                let nameMatch = user.displayName.range(
                    of: q,
                    options: [.caseInsensitive, .diacriticInsensitive]
                ) != nil
                let phoneMatch = (user.phoneNumber ?? "").contains(q.filter(\.isNumber))
                return nameMatch || phoneMatch
            }
            .sorted {
                $0.displayName.localizedCaseInsensitiveCompare($1.displayName) == .orderedAscending
            }
    }

    private func scheduleLookup() {
        lookupTask?.cancel()
        lookupResultUser = nil
        lookupMessage = nil
        isLookupLoading = false
        invitingUserID = nil
        isOpeningSMSInvite = false

        guard isPhonePrimaryInviteQuery(trimmedInviteSearch) else {
            hasAttemptedLookup = false
            return
        }

        let trimmed = trimmedInviteSearch
        let digitsOnly = trimmed.filter(\.isNumber)
        guard !digitsOnly.isEmpty else {
            hasAttemptedLookup = false
            return
        }
        guard isValidNorthAmericanPhoneNumber(trimmed) else {
            hasAttemptedLookup = false
            return
        }

        lookupTask = Task {
            try? await Task.sleep(nanoseconds: 450_000_000)
            guard !Task.isCancelled else { return }
            await lookupUserByPhone()
        }
    }

    private func lookupUserByPhone() async {
        isLookupLoading = true
        lookupMessage = nil
        lookupResultUser = nil
        hasAttemptedLookup = true
        defer { isLookupLoading = false }

        let phone = trimmedInviteSearch
        guard isValidNorthAmericanPhoneNumber(phone) else { return }

        do {
            if let user = try await APIClient.shared.lookupUserByPhone(phoneNumber: phone) {
                lookupResultUser = user
            } else {
                lookupMessage = "Can't find them? Double-check the number and try again."
            }
        } catch {
            lookupMessage = "Lookup failed. Try again."
        }
    }

    @ViewBuilder
    private func searchResultUserCard(_ user: UserDTO) -> some View {
        let memberAlreadyInCircle = circleMembers.contains(where: { $0.id == user.id })
        let isInvitingUser = invitingUserID == user.id

        HStack(spacing: 12) {
            AvatarView(
                name: user.displayName,
                avatarUrl: user.avatarUrl,
                size: 56,
                accentColor: .purple,
                accentRingWidth: 1.5,
                whiteRingWidth: 0
            )

            VStack(alignment: .leading, spacing: 5) {
                Text(user.displayName)
                    .font(.headline)
                    .foregroundStyle(.white)
                Text(user.phoneNumber ?? trimmedInviteSearch)
                    .font(.subheadline)
                    .foregroundStyle(.white.opacity(0.75))
                HStack(spacing: 6) {
                    Circle()
                        .fill(appState.avatarPresenceDotColor(forUserID: user.id))
                        .frame(width: 8, height: 8)
                    Text("On Driftd")
                        .font(.subheadline)
                        .foregroundStyle(.white.opacity(0.8))
                }
            }
            Spacer()
            if memberAlreadyInCircle {
                Text("In Squad")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.green)
            } else {
                Button {
                    SquadInviteSheetHaptics.buttonTap()
                    Task {
                        invitingUserID = user.id
                        lookupMessage = "Sending invite…"
                        let didSend = await appState.inviteMemberByPhone(
                            circleID: circleID,
                            phoneNumber: user.phoneNumber ?? trimmedInviteSearch
                        )
                        invitingUserID = nil
                        if didSend {
                            lookupMessage = "Invite sent. Waiting for response."
                        } else {
                            lookupMessage = appState.errorMessage ?? "Invite failed. Try again."
                        }
                    }
                } label: {
                    HStack(spacing: 7) {
                        if isInvitingUser {
                            ProgressView()
                                .scaleEffect(0.72)
                                .tint(.white)
                        }
                        Text(isInvitingUser ? "Sending" : "Invite")
                    }
                }
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(.white)
                .padding(.horizontal, 20)
                .padding(.vertical, 10)
                .background(
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .fill(Color(red: 0.50, green: 0.18, blue: 1.0))
                )
                .disabled(isInvitingUser)
                .opacity(isInvitingUser ? 0.82 : 1)
            }
        }
        .padding(12)
        .background(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .fill(Color.white.opacity(0.05))
        )
        .overlay(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .stroke(Color.white.opacity(0.14), lineWidth: 1)
        )
    }

    @ViewBuilder
    private func squadMateNameSearchRow(_ member: FriendLocation) -> some View {
        let memberAlreadyInCircle = circleMembers.contains(where: { $0.id == member.id })
        let isWorking = invitingUserID == member.id

        HStack(spacing: 12) {
            AvatarView(
                name: member.name,
                avatarUrl: member.avatarUrl,
                size: 56,
                accentColor: member.accentColor,
                accentRingWidth: 1.5,
                whiteRingWidth: 0
            )

            VStack(alignment: .leading, spacing: 5) {
                Text(member.name)
                    .font(.headline)
                    .foregroundStyle(.white)
                Text("Someone from your squads")
                    .font(.subheadline)
                    .foregroundStyle(.white.opacity(0.75))
                HStack(spacing: 6) {
                    Circle()
                        .fill(appState.avatarPresenceDotColor(forUserID: member.id))
                        .frame(width: 8, height: 8)
                    Text("On Driftd")
                        .font(.subheadline)
                        .foregroundStyle(.white.opacity(0.8))
                }
            }
            Spacer()
            if memberAlreadyInCircle {
                Text("In Squad")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.green)
            } else {
                Button {
                    SquadInviteSheetHaptics.buttonTap()
                    Task {
                        invitingUserID = member.id
                        lookupMessage = "Adding…"
                        appState.errorMessage = nil
                        await appState.addMember(to: circleID, userID: member.id)
                        invitingUserID = nil
                        if let err = appState.errorMessage, !err.isEmpty {
                            lookupMessage = err
                        } else {
                            lookupMessage = "\(member.name) joined the squad."
                            inviteSearchText = ""
                        }
                    }
                } label: {
                    HStack(spacing: 7) {
                        if isWorking {
                            ProgressView()
                                .scaleEffect(0.72)
                                .tint(.white)
                        }
                        Text(isWorking ? "Adding" : "Add")
                    }
                }
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(.white)
                .padding(.horizontal, 20)
                .padding(.vertical, 10)
                .background(
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .fill(Color(red: 0.50, green: 0.18, blue: 1.0))
                )
                .disabled(isWorking)
                .opacity(isWorking ? 0.82 : 1)
            }
        }
        .padding(12)
        .background(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .fill(Color.white.opacity(0.05))
        )
        .overlay(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .stroke(Color.white.opacity(0.14), lineWidth: 1)
        )
    }

    private var searchResultUnknownPhoneCard: some View {
        HStack(spacing: 12) {
            Circle()
                .fill(Color.white.opacity(0.12))
                .frame(width: 56, height: 56)
                .overlay {
                    Image(systemName: "person.fill.questionmark")
                        .foregroundStyle(.white.opacity(0.8))
                }

            VStack(alignment: .leading, spacing: 5) {
                Text(trimmedInviteSearch)
                    .font(.headline)
                    .foregroundStyle(.white)
                Text("Not on Driftd yet")
                    .font(.subheadline)
                    .foregroundStyle(.white.opacity(0.72))
            }
            Spacer()
            Button {
                SquadInviteSheetHaptics.buttonTap()
                #if DEBUG
                print("InviteLinkAPI user tapped Invite via SMS phone=\(trimmedInviteSearch)")
                #endif
                smsInviteTask?.cancel()
                smsInviteTask = Task {
                    await createAndOpenSMSInvite(to: trimmedInviteSearch)
                }
            } label: {
                HStack(spacing: 7) {
                    if isOpeningSMSInvite {
                        ProgressView()
                            .scaleEffect(0.72)
                            .tint(.white)
                    }
                    Text(isOpeningSMSInvite ? "Opening" : "Invite via SMS")
                }
            }
            .primaryCTAButtonStyle(horizontalPadding: 14, verticalPadding: 10)
            .disabled(isOpeningSMSInvite)
            .opacity(isOpeningSMSInvite ? 0.82 : 1)
        }
        .padding(12)
        .background(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .fill(Color.white.opacity(0.05))
        )
        .overlay(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .stroke(Color.white.opacity(0.14), lineWidth: 1)
        )
    }

    private func sectionHeader(title: String, trailing: String?) -> some View {
        HStack {
            Text(title)
                .font(.headline.weight(.semibold))
                .foregroundStyle(.white.opacity(0.94))
            Spacer()
            if let trailing {
                Text(trailing)
                    .font(.subheadline)
                    .foregroundStyle(.white.opacity(0.65))
            }
        }
    }

    private func circleMemberRow(_ member: FriendLocation) -> some View {
        HStack(spacing: 12) {
            AvatarView(
                name: member.name,
                avatarUrl: member.avatarUrl,
                size: 44,
                accentColor: member.accentColor,
                accentRingWidth: 1.25,
                whiteRingWidth: 0
            )
            .overlay(alignment: .bottomTrailing) {
                Circle()
                    .fill(member.presenceStatus.color)
                    .frame(width: 10, height: 10)
                    .overlay {
                        Circle().stroke(.black, lineWidth: 2)
                    }
            }

            VStack(alignment: .leading, spacing: 3) {
                Text(member.name)
                    .font(.headline)
                    .foregroundStyle(.white)
                Text(member.clubRole.capitalized)
                    .font(.subheadline)
                    .foregroundStyle(memberRoleColor(member.clubRole))
            }
            Spacer()
            Image(systemName: "ellipsis")
                .font(.headline.weight(.semibold))
                .foregroundStyle(.white.opacity(0.65))
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 11)
    }

    private func memberRoleColor(_ role: String) -> Color {
        switch role.lowercased() {
        case "admin", "owner":
            return Color(red: 0.70, green: 0.25, blue: 1.0)
        default:
            return Color.white.opacity(0.64)
        }
    }

    private func isValidNorthAmericanPhoneNumber(_ raw: String) -> Bool {
        let digits = raw.filter(\.isNumber)
        let normalized: Substring
        if digits.count == 11, digits.first == "1" {
            normalized = digits.dropFirst()
        } else if digits.count == 10 {
            normalized = Substring(digits)
        } else {
            return false
        }

        guard normalized.count == 10 else { return false }
        let chars = Array(normalized)
        // NANP: area code and central office code cannot start with 0 or 1.
        guard chars[0] >= "2", chars[0] <= "9" else { return false }
        guard chars[3] >= "2", chars[3] <= "9" else { return false }
        return true
    }

    private func squadInviteSMSBody(url: String) -> String {
        "Join my squad on Driftd: \(url)"
    }

    @MainActor
    private func refreshSignupInviteBalance() async {
        isLoadingSignupInviteBalance = true
        defer { isLoadingSignupInviteBalance = false }
        do {
            let balance = try await APIClient.shared.fetchSignupInviteBalance()
            signupInviteRemaining = balance.remainingUses
            applySignupInviteEarnAtNextLevel(from: balance)
        } catch {
            signupInviteRemaining = signupInviteRemaining ?? 0
        }
    }

    @MainActor
    private func applySignupInviteRemainingFromLinkResponse(_ remainingUses: Int?, personalRemainingUses: Int? = nil) {
        let balanceRemaining = personalRemainingUses ?? remainingUses
        if let balanceRemaining {
            signupInviteRemaining = max(0, balanceRemaining)
        }
    }

    @MainActor
    private func ensureShareInviteLink() async -> String? {
        let cached = generatedInviteLink.trimmingCharacters(in: .whitespacesAndNewlines)
        if !cached.isEmpty {
            return cached
        }

        do {
            let response = try await APIClient.shared.createCircleInviteLink(circleId: circleID)
            generatedInviteLink = response.url
            applySignupInviteRemainingFromLinkResponse(
                response.remainingUses,
                personalRemainingUses: response.personalRemainingUses
            )
            return response.url
        } catch {
            lookupMessage = inviteLinkErrorMessage(for: error)
            await refreshSignupInviteBalance()
            return nil
        }
    }

    @MainActor
    private func prefetchShareInviteLinkIfNeeded() async {
        guard generatedInviteLink.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return }
        shareInviteBusy = .prefetch
        defer {
            if shareInviteBusy == .prefetch {
                shareInviteBusy = nil
            }
        }
        _ = await ensureShareInviteLink()
    }

    @MainActor
    private func copyInviteLink() async {
        guard shareInviteBusy == nil else { return }
        shareInviteBusy = .copy
        defer { shareInviteBusy = nil }

        guard let url = await ensureShareInviteLink() else { return }
        UIPasteboard.general.string = url
        withAnimation(.spring(response: 0.38, dampingFraction: 0.86)) {
            addMemberSheetToast = AppToast(text: "Copied", systemImage: "doc.on.doc")
        }
    }

    @MainActor
    private func openSMSWithShareLink() async {
        guard shareInviteBusy == nil else { return }
        shareInviteBusy = .sms
        defer { shareInviteBusy = nil }

        guard let url = await ensureShareInviteLink() else { return }
        presentSMSInviteComposer(
            recipient: nil,
            body: squadInviteSMSBody(url: url),
            inviteLink: url
        )
    }

    @MainActor
    private func createAndOpenSMSInvite(to rawPhoneNumber: String) async {
        guard !isOpeningSMSInvite else { return }
        let phone = rawPhoneNumber.trimmingCharacters(in: .whitespacesAndNewlines)
        guard isValidNorthAmericanPhoneNumber(phone) else {
            lookupMessage = "Enter a valid US phone number."
            return
        }

        isOpeningSMSInvite = true
        lookupMessage = generatedInviteLink.isEmpty ? "Generating invite link…" : "Opening Messages…"
        defer {
            isOpeningSMSInvite = false
        }

        guard !Task.isCancelled else {
            lookupMessage = nil
            return
        }

        guard let inviteLink = await inviteLinkForSMSInvite(phone: phone) else {
            #if DEBUG
            print("InviteLinkAPI SMS aborted: no invite link (\(lookupMessage ?? "no message"))")
            #endif
            if Task.isCancelled {
                lookupMessage = nil
            }
            return
        }
        #if DEBUG
        print("InviteLinkAPI SMS opening Messages linkHost=\(URL(string: inviteLink)?.host ?? "?")")
        #endif

        guard !Task.isCancelled else {
            lookupMessage = nil
            return
        }

        let recipient = normalizedSMSRecipient(from: phone)
        presentSMSInviteComposer(
            recipient: recipient,
            body: squadInviteSMSBody(url: inviteLink),
            inviteLink: inviteLink
        )
    }

    @MainActor
    private func cancelSMSInviteFlow(reason: String) {
        smsInviteTask?.cancel()
        smsInviteTask = nil
        isOpeningSMSInvite = false
        if lookupMessage == "Generating invite link…" || lookupMessage == "Opening Messages…" {
            lookupMessage = nil
        }
    }

    @MainActor
    private func inviteLinkForSMSInvite(phone: String) async -> String? {
        let normalizedKey = phone.filter(\.isNumber)
        if !normalizedKey.isEmpty, let cached = smsInviteLinkByPhone[normalizedKey] {
            return cached
        }

        do {
            let response = try await APIClient.shared.createCircleInviteLink(
                circleId: circleID,
                phoneNumber: phone
            )
            guard !Task.isCancelled else {
                return nil
            }
            let url = response.url
            if !normalizedKey.isEmpty {
                smsInviteLinkByPhone[normalizedKey] = url
            }
            applySignupInviteRemainingFromLinkResponse(
                response.remainingUses,
                personalRemainingUses: response.personalRemainingUses
            )
            return url
        } catch {
            guard !Task.isCancelled else {
                return nil
            }
            lookupMessage = inviteLinkErrorMessage(for: error)
            await refreshSignupInviteBalance()
            return nil
        }
    }

    private func inviteLinkErrorMessage(for error: Error) -> String {
        let ns = error as NSError
        if ns.domain == "OttoAPI",
           let message = ns.userInfo[NSLocalizedDescriptionKey] as? String,
           !message.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            return message.trimmingCharacters(in: .whitespacesAndNewlines)
        }
        if let urlError = error as? URLError,
           [.timedOut, .notConnectedToInternet, .networkConnectionLost, .cannotConnectToHost].contains(urlError.code) {
            return "Couldn't generate invite link. Check your connection and try again."
        }
        return "Couldn't generate invite link. Try again."
    }

    private func normalizedSMSRecipient(from rawPhoneNumber: String) -> String {
        let digits = rawPhoneNumber.filter(\.isNumber)
        if digits.count == 11, digits.first == "1" {
            return String(digits)
        }
        if digits.count == 10 {
            return String(digits)
        }
        return rawPhoneNumber.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    @MainActor
    private func presentSMSInviteComposer(recipient: String?, body: String, inviteLink: String) {
        let canSendText = MFMessageComposeViewController.canSendText()
        let presenter = currentTopViewController()
        guard canSendText, let presenter else {
            copyInviteLinkFallback(inviteLink)
            return
        }

        let composer = MFMessageComposeViewController()
        if let recipient, !recipient.isEmpty {
            composer.recipients = [recipient]
        }
        composer.body = body
        let delegate = SMSInviteMessageComposeDelegate { result in
            switch result {
            case .sent:
                lookupMessage = "Invite sent."
            case .cancelled:
                lookupMessage = "SMS invite canceled."
            case .failed:
                copyInviteLinkFallback(inviteLink)
            @unknown default:
                copyInviteLinkFallback(inviteLink)
            }
            smsInviteDelegate = nil
        }
        smsInviteDelegate = delegate
        composer.messageComposeDelegate = delegate
        presenter.present(composer, animated: true)
        lookupMessage = "Opened Messages with your invite link."
    }

    @MainActor
    private func copyInviteLinkFallback(_ inviteLink: String) {
        let trimmed = inviteLink.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            lookupMessage = "Couldn't open Messages. Try again when you have a connection."
            return
        }
        generatedInviteLink = trimmed
        UIPasteboard.general.string = trimmed
        lookupMessage = "Couldn't open Messages. Invite link copied instead."
    }

    @MainActor
    private func currentTopViewController() -> UIViewController? {
        guard let scene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
              let root = scene.windows.first(where: \.isKeyWindow)?.rootViewController ?? scene.windows.first?.rootViewController
        else {
            return nil
        }
        var presenter = root
        while let presented = presenter.presentedViewController {
            presenter = presented
        }
        return presenter
    }

    private func resetAddMemberSheetState() {
        cancelSMSInviteFlow(reason: "sheet dismissed")
        lookupTask?.cancel()
        shouldShowAddMemberAfterSettingsDismiss = false
        inviteSearchText = ""
        generatedInviteLink = ""
        smsInviteLinkByPhone.removeAll()
        signupInviteRemaining = nil
        signupInviteEarnAtNextLevelCount = nil
        signupInviteNextLevelDisplayName = nil
        isLoadingSignupInviteBalance = false
        shareInviteBusy = nil
        addMemberSheetToast = nil
        lookupResultUser = nil
        lookupMessage = nil
        isLookupLoading = false
        invitingUserID = nil
        isOpeningSMSInvite = false
        smsInviteDelegate = nil
        hasAttemptedLookup = false
    }
}

private final class SMSInviteMessageComposeDelegate: NSObject, MFMessageComposeViewControllerDelegate {
    private let onFinish: (MessageComposeResult) -> Void

    init(onFinish: @escaping (MessageComposeResult) -> Void) {
        self.onFinish = onFinish
        super.init()
    }

    func messageComposeViewController(
        _ controller: MFMessageComposeViewController,
        didFinishWith result: MessageComposeResult
    ) {
        controller.dismiss(animated: true) {
            self.onFinish(result)
        }
    }
}
