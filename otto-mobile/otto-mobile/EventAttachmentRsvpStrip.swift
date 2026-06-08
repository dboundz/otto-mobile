import SwiftUI
import UIKit

/// Inline Going / Maybe / Not Going for chat event cards. Compact horizontal chips with secondary counts.
struct EventAttachmentRsvpStrip: View {
    let event: EventDTO
    let meUser: UserDTO?
    let submitting: Bool
    let onSelect: (String) -> Void

    private enum Choice: String, CaseIterable {
        case going = "going"
        case interested = "interested"
        case notGoing = "not_going"

        var title: String {
            switch self {
            case .going: return "Going"
            case .interested: return "Maybe"
            case .notGoing: return "Not Going"
            }
        }

        var systemImage: String {
            switch self {
            case .going: return "checkmark.circle.fill"
            case .interested: return "questionmark.circle"
            case .notGoing: return "xmark.circle"
            }
        }

        var accent: Color {
            switch self {
            case .going: return Color(red: 0.20, green: 0.78, blue: 0.35)
            case .interested: return Color(red: 1.0, green: 0.8, blue: 0.0)
            case .notGoing: return Color(red: 1.0, green: 0.27, blue: 0.23)
            }
        }
    }

    private var interactionsEnabled: Bool {
        Date() <= event.eventCheckInWindowEnd
    }

    private var orderedChoices: [Choice] { [.going, .interested, .notGoing] }

    private func rsvpUsers(for choice: Choice) -> [UserDTO] {
        let rsvps = event.contactsRsvps ?? event.contactsGoing.map {
            EventDTO.ContactRsvpDTO(status: Choice.going.rawValue, respondedAt: nil, user: $0)
        }
        var seen = Set<String>()
        var users = rsvps.compactMap { rsvp -> UserDTO? in
            guard rsvp.status == choice.rawValue else { return nil }
            let u = rsvp.user
            guard !u.id.isEmpty, !seen.contains(u.id) else { return nil }
            seen.insert(u.id)
            return u
        }
        if event.currentUserRsvp == choice.rawValue,
           let meUser,
           !users.contains(where: { $0.id == meUser.id }) {
            users.insert(meUser, at: 0)
        }
        return users
    }

    private func rsvpCount(for choice: Choice) -> Int {
        let users = rsvpUsers(for: choice)
        switch choice {
        case .going: return event.rsvpCounts?.going ?? users.count
        case .interested: return event.rsvpCounts?.interested ?? users.count
        case .notGoing: return event.rsvpCounts?.notGoing ?? users.count
        }
    }

    var body: some View {
        HStack(spacing: 5) {
            ForEach(orderedChoices, id: \.rawValue) { choice in
                let selected = event.currentUserRsvp == choice.rawValue
                let count = rsvpCount(for: choice)
                Button {
                    guard interactionsEnabled, !submitting else { return }
                    UIImpactFeedbackGenerator(style: .light).impactOccurred()
                    onSelect(choice.rawValue)
                } label: {
                    HStack(spacing: 4) {
                        Image(systemName: choice.systemImage)
                            .font(.system(size: 11, weight: .semibold))
                            .foregroundStyle(selected ? Color.white : choice.accent)
                        Text(choice.title)
                            .font(.caption2.weight(.semibold))
                            .foregroundStyle(selected ? Color.white : Color.white.opacity(0.86))
                            .lineLimit(1)
                            .minimumScaleFactor(0.85)
                        Text("\(count)")
                            .font(.system(size: 9, weight: .medium))
                            .foregroundStyle(selected ? Color.white.opacity(0.68) : Color.white.opacity(0.38))
                            .monospacedDigit()
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 8)
                    .padding(.horizontal, 5)
                    .background(
                        RoundedRectangle(cornerRadius: 8, style: .continuous)
                            .fill(selected ? Color.purple.opacity(0.52) : Color.white.opacity(0.055))
                    )
                    .overlay(
                        RoundedRectangle(cornerRadius: 8, style: .continuous)
                            .stroke(Color.white.opacity(selected ? 0.12 : 0.07), lineWidth: 1)
                    )
                }
                .buttonStyle(.plain)
                .disabled(!interactionsEnabled || submitting)
                .opacity((interactionsEnabled && !submitting) || selected ? 1 : 0.42)
            }
        }
    }
}
