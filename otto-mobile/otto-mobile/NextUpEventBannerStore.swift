import Foundation
import Combine

@MainActor
final class NextUpEventBannerStore: ObservableObject {
    private static let autoHideDelayNanoseconds: UInt64 = 30_000_000_000
    private static var localDismissalContextByCircleID: [String: [String: String]] = [:]

    private let circleID: String
    @Published private var dismissalContextByEventID: [String: String] = [:]
    @Published private var autoHiddenEventID: String?
    private var autoHideTask: Task<Void, Never>?

    init(circleID: String) {
        self.circleID = circleID
    }

    deinit {
        autoHideTask?.cancel()
    }

    func loadDismissals(for eventIDs: [String]) async {
        let ids = eventIDs.map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }.filter { !$0.isEmpty }
        guard !ids.isEmpty else { return }
        do {
            let records = try await APIClient.shared.fetchNextUpEventDismissals(circleId: circleID, eventIds: ids)
            for record in records {
                dismissalContextByEventID[record.eventId] = record.dismissedContext
                Self.localDismissalContextByCircleID[circleID, default: [:]][record.eventId] = record.dismissedContext
            }
        } catch {
            // Banner state is non-critical; leave the local UI usable if this read fails.
            print("Failed to load next-up banner dismissals: \(error)")
        }
    }

    func visibleEvent(candidate: EventDTO?, now: Date = Date(), calendar: Calendar = .current) -> EventDTO? {
        guard let candidate else { return nil }
        guard candidate.eventCheckInWindowEnd >= now else { return nil }
        guard autoHiddenEventID != candidate.id else { return nil }

        let dismissalContext =
            dismissalContextByEventID[candidate.id]
            ?? Self.localDismissalContextByCircleID[circleID]?[candidate.id]
        switch dismissalContext {
        case "pre_event":
            return calendar.isDate(candidate.startsAt, inSameDayAs: now) ? candidate : nil
        case "event_day":
            return nil
        default:
            return candidate
        }
    }

    func scheduleAutoHide(for event: EventDTO?) {
        autoHideTask?.cancel()
        guard let event else { return }
        autoHideTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: Self.autoHideDelayNanoseconds)
            guard !Task.isCancelled else { return }
            await MainActor.run {
                self?.autoHiddenEventID = event.id
            }
        }
    }

    func dismiss(_ event: EventDTO, now: Date = Date(), calendar: Calendar = .current) {
        autoHideTask?.cancel()
        let context = calendar.isDate(event.startsAt, inSameDayAs: now) ? "event_day" : "pre_event"
        dismissalContextByEventID[event.id] = context
        Self.localDismissalContextByCircleID[circleID, default: [:]][event.id] = context
        autoHiddenEventID = event.id

        Task {
            do {
                try await APIClient.shared.dismissNextUpEventBanner(
                    circleId: circleID,
                    eventId: event.id,
                    dismissedContext: context
                )
            } catch {
                print("Failed to persist next-up banner dismissal: \(error)")
            }
        }
    }
}
