import Foundation

/// Per-squad notification mute bucket (maps to push `type` from the server).
enum SquadNotificationMuteBucket: Sendable {
    case newMessages
    case mentionsAndReplies

    fileprivate func userDefaultsKey(circleId: String) -> String {
        let trimmed = circleId.trimmingCharacters(in: .whitespacesAndNewlines)
        switch self {
        case .newMessages:
            return "otto.squadMute.v1.new.\(trimmed)"
        case .mentionsAndReplies:
            return "otto.squadMute.v1.mention.\(trimmed)"
        }
    }

    static func bucket(forPushType type: String) -> SquadNotificationMuteBucket? {
        switch type {
        case "circle.chat.new_message":
            return .newMessages
        case "circle.chat.mention", "circle.chat.reply", "circle.chat.reaction":
            return .mentionsAndReplies
        default:
            return nil
        }
    }
}

/// User-facing mute duration (includes Off).
enum SquadNotificationMuteChoice: String, CaseIterable, Identifiable, Hashable {
    case off
    case endOfDay
    case twentyFourHours
    case oneWeek
    case always

    var id: String { rawValue }

    var displayTitle: String {
        switch self {
        case .off: return "Do not mute"
        case .endOfDay: return "Until end of day"
        case .twentyFourHours: return "24 hours"
        case .oneWeek: return "1 week"
        case .always: return "Always"
        }
    }
}

/// Internal persisted form (survives relaunch; timed entries carry expiry).
private enum SquadNotificationMuteStored: Equatable {
    case off
    case always
    /// Prefix + absolute expiry (epoch seconds).
    case timed(prefix: String, expiresAt: Date)

    func isActive(now: Date) -> Bool {
        switch self {
        case .off:
            return false
        case .always:
            return true
        case .timed(_, let expiresAt):
            return now < expiresAt
        }
    }

    func choiceForDisplay(now: Date) -> SquadNotificationMuteChoice {
        switch self {
        case .off:
            return .off
        case .always:
            return .always
        case .timed(let prefix, let expiresAt):
            guard now < expiresAt else { return .off }
            switch prefix {
            case "EOD": return .endOfDay
            case "H24": return .twentyFourHours
            case "W1": return .oneWeek
            default: return .off
            }
        }
    }

    static func decode(_ raw: String?) -> SquadNotificationMuteStored {
        guard let raw = raw?.trimmingCharacters(in: .whitespacesAndNewlines), !raw.isEmpty else {
            return .off
        }
        if raw == "A" { return .always }
        let parts = raw.split(separator: ":", maxSplits: 1, omittingEmptySubsequences: false).map(String.init)
        guard parts.count == 2,
              let epoch = TimeInterval(parts[1])
        else {
            return .off
        }
        let expiresAt = Date(timeIntervalSince1970: epoch)
        let prefix = parts[0]
        guard ["EOD", "H24", "W1"].contains(prefix) else { return .off }
        return .timed(prefix: prefix, expiresAt: expiresAt)
    }

    static func encode(choice: SquadNotificationMuteChoice, now: Date = Date()) -> String? {
        let cal = Calendar.current
        switch choice {
        case .off:
            return nil
        case .always:
            return "A"
        case .endOfDay:
            let sod = cal.startOfDay(for: now)
            guard let nextMidnight = cal.date(byAdding: .day, value: 1, to: sod) else { return nil }
            return "EOD:\(nextMidnight.timeIntervalSince1970)"
        case .twentyFourHours:
            let exp = now.addingTimeInterval(86_400)
            return "H24:\(exp.timeIntervalSince1970)"
        case .oneWeek:
            let exp = now.addingTimeInterval(86_400 * 7)
            return "W1:\(exp.timeIntervalSince1970)"
        }
    }
}

/// Reads / writes per-circle mute prefs (UserDefaults). Safe to call from `AppDelegate` on the main thread.
enum SquadNotificationMuteStore {
    private static let defaults = UserDefaults.standard

    static func loadChoice(circleId: String, bucket: SquadNotificationMuteBucket) -> SquadNotificationMuteChoice {
        let raw = defaults.string(forKey: bucket.userDefaultsKey(circleId: circleId))
        return SquadNotificationMuteStored.decode(raw).choiceForDisplay(now: Date())
    }

    static func saveChoice(_ choice: SquadNotificationMuteChoice, circleId: String, bucket: SquadNotificationMuteBucket) {
        let key = bucket.userDefaultsKey(circleId: circleId)
        if let encoded = SquadNotificationMuteStored.encode(choice: choice) {
            defaults.set(encoded, forKey: key)
        } else {
            defaults.removeObject(forKey: key)
        }
    }

    /// When `true`, drop `.sound` from `UNNotificationPresentationOptions` for this payload.
    static func shouldSuppressChatNotificationSound(circleId: String?, pushType: String?) -> Bool {
        guard let cid = circleId?.trimmingCharacters(in: .whitespacesAndNewlines), !cid.isEmpty else {
            return false
        }
        guard let pushType, let bucket = SquadNotificationMuteBucket.bucket(forPushType: pushType) else {
            return false
        }
        let raw = defaults.string(forKey: bucket.userDefaultsKey(circleId: cid))
        return SquadNotificationMuteStored.decode(raw).isActive(now: Date())
    }
}
