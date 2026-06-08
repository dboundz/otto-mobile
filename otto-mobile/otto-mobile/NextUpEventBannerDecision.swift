import Foundation

enum NextUpDismissalContext {
    static let preEvent = "pre_event"
    static let eventDay = "event_day"
}

enum NextUpEventBannerDecision {
    static func eventIsNextUpPinnedForCircle(event: EventDTO, circleIdRaw: String) -> Bool {
        let circleId = circleIdRaw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !circleId.isEmpty else { return false }
        let eventCircle = event.circleId?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if event.visibility == "circle", !eventCircle.isEmpty {
            return eventCircle.caseInsensitiveCompare(circleId) == .orderedSame
        }
        return true
    }

    static func isEventOfficialForSquad(event: EventDTO, circleIdRaw: String) -> Bool {
        let circleId = circleIdRaw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !circleId.isEmpty else { return false }
        if event.isOfficialForCircle == true { return true }
        if event.visibility == "circle",
           event.circleId?.trimmingCharacters(in: .whitespacesAndNewlines)
               .caseInsensitiveCompare(circleId) == .orderedSame {
            return true
        }
        return event.attachedSquads.contains {
            $0.id.trimmingCharacters(in: .whitespacesAndNewlines)
                .caseInsensitiveCompare(circleId) == .orderedSame
        }
    }

    static func eventQualifiesForSquadNextUpPin(event: EventDTO, circleIdRaw: String) -> Bool {
        let circleId = circleIdRaw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !circleId.isEmpty else { return false }
        guard eventIsNextUpPinnedForCircle(event: event, circleIdRaw: circleId) else { return false }
        return isEventOfficialForSquad(event: event, circleIdRaw: circleId)
    }
}
