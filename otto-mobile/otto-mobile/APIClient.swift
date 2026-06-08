import Foundation
import CoreLocation
import os

extension Notification.Name {
    /// Posted on the main queue when an authenticated HTTP response is 401; `AppState` listens and calls `logout()`.
    static let ottoSessionUnauthorized = Notification.Name("ottoSessionUnauthorized")
}

/// Raised when POST `/api/circles/:id/leave` returns 403 because the owner must transfer ownership first.
struct OttoLeaveCircleOwnershipRequiredError: Error {}

struct APIConfig {
    static let baseURL: URL = {
        #if targetEnvironment(simulator)
        return URL(string: "http://localhost:4000")!
        #else
        return URL(string: "https://api.ottomot.to")!
        #endif
    }()

    static let websocketURL: URL = {
        #if targetEnvironment(simulator)
        return URL(string: "ws://localhost:4001/ws")!
        #else
        return URL(string: "wss://rt.ottomot.to/ws")!
        #endif
    }()

    /// Builds a fetchable URL for media returned by the API: absolute `http`/`https`, root-relative paths (`/uploads/...`),
    /// or protocol-relative URLs (`//host/...`). Plain `URL(string:)` is nil for `/path`, which breaks image loading.
    static func imageFetchURL(from raw: String?) -> URL? {
        guard let raw = raw?.trimmingCharacters(in: .whitespacesAndNewlines), !raw.isEmpty else { return nil }
        if raw.hasPrefix("//") {
            return URL(string: "https:\(raw)")
        }
        if raw.hasPrefix("/") {
            var components = URLComponents()
            components.scheme = baseURL.scheme
            components.host = baseURL.host
            components.port = baseURL.port
            components.path = raw
            return components.url
        }
        if let url = URL(string: raw), let scheme = url.scheme, scheme == "http" || scheme == "https" {
            let host = url.host?.lowercased()
            if host == "localhost" || host == "127.0.0.1" || host == "::1" {
                guard var parts = URLComponents(url: url, resolvingAgainstBaseURL: false) else {
                    return url
                }
                parts.scheme = baseURL.scheme
                parts.host = baseURL.host
                parts.port = baseURL.port
                return parts.url ?? url
            }
            return url
        }
        return URL(string: raw)
    }
}

/// Production website URLs (legal, marketing). Same in all build configurations; not tied to `APIConfig.baseURL`.
enum WebsiteLinks {
    /// Canonical public web origin for member profiles and events (`/m/*`, `/e/*`).
    private static let publicWebOrigin = "https://driftd.com"

    static let privacyPolicy = URL(string: "https://driftd.com/privacy")!
    /// Store listings use `/tos`; site may redirect to `/terms`.
    static let termsOfUse = URL(string: "https://driftd.com/tos")!

    /// In-app “report a concern” uses the same inbox cited in the Privacy Policy.
    static var reportConcernMailto: URL {
        var c = URLComponents()
        c.scheme = "mailto"
        c.path = "legal@ottomot.to"
        c.queryItems = [URLQueryItem(name: "subject", value: "Driftd — Report a concern")]
        return c.url ?? URL(string: "mailto:legal@ottomot.to")!
    }

    static func profile(userId: String) -> URL {
        URL(string: "\(publicWebOrigin)/m/\(userId)")!
    }

    static func event(eventRef: String) -> URL {
        URL(string: "\(publicWebOrigin)/e/\(eventRef)")!
    }

    private static let publicEventLinkHosts: Set<String> = ["driftd.com", "ottomot.to"]

    /// Slug or id from a public event URL (`https://driftd.com/e/{ref}` or legacy `ottomot.to`, including `www`).
    static func eventRef(fromPublicEventURL url: URL) -> String? {
        guard let host = url.host(percentEncoded: false)?.lowercased() else { return nil }
        let hostNoWww = host.hasPrefix("www.") ? String(host.dropFirst(4)) : host
        guard publicEventLinkHosts.contains(hostNoWww) else { return nil }
        let parts = url.path.split(separator: "/").map(String.init).filter { !$0.isEmpty }
        guard parts.count >= 2, parts[0].lowercased() == "e" else { return nil }
        let ref = parts[1].trimmingCharacters(in: .whitespacesAndNewlines)
        return ref.isEmpty ? nil : ref
    }
}

struct CircleDTO: Decodable {
    struct MemberDTO: Decodable {
        let userId: String
        let role: String
    }

    let id: String
    let name: String
    let description: String?
    let ownerId: String
    let members: [MemberDTO]
    let photoUrl: String?

    enum CodingKeys: String, CodingKey {
        case id = "_id"
        case name
        case description
        case ownerId
        case members
        case photoUrl
    }

    private enum LoosePhotoKeys: String, CodingKey {
        case photoURL
        case photo_url
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id)
        name = try c.decode(String.self, forKey: .name)
        description = try c.decodeIfPresent(String.self, forKey: .description)
        ownerId = try c.decode(String.self, forKey: .ownerId)
        members = try c.decode([MemberDTO].self, forKey: .members)

        if let s = try? c.decodeIfPresent(String.self, forKey: .photoUrl), !s.isEmpty {
            photoUrl = s
        } else if let loose = try? decoder.container(keyedBy: LoosePhotoKeys.self) {
            let fromAlt = (try? loose.decodeIfPresent(String.self, forKey: .photoURL))
                ?? (try? loose.decodeIfPresent(String.self, forKey: .photo_url))
            let trimmed = fromAlt?.trimmingCharacters(in: .whitespacesAndNewlines)
            photoUrl = (trimmed?.isEmpty == false) ? trimmed : nil
        } else {
            photoUrl = nil
        }
    }
}

/// Stored as `public` | `squads` | `private` on the user record.
enum DriveStatsVisibilitySetting: String, Codable, CaseIterable, Identifiable {
    case `public` = "public"
    case squads = "squads"
    case `private` = "private"

    var id: String { rawValue }

    var settingsMenuLabel: String {
        switch self {
        case .public: return "Public"
        case .squads: return "Squads only"
        case .private: return "Private"
        }
    }

    var settingsFootnote: String {
        switch self {
        case .public:
            return "Anyone can see your driving stats on your profile and on the web."
        case .squads:
            return "Only Driftd users who share a squad with you can see your driving stats."
        case .private:
            return "Only you can see your driving stats."
        }
    }
}

struct UserDTO: Decodable {
    struct VehicleDTO: Decodable {
        let displayName: String?
        let make: String?
        let model: String?
    }

    let id: String
    let displayName: String
    let handle: String
    let avatarUrl: String?
    /// Server: `violet` | `blue` | `amber` | … — resolved via `MapAccentPalette`.
    let mapAccentKey: String?
    let phoneNumber: String?
    let vehicle: VehicleDTO?
    let lastPresenceAt: String?
    /// Stored on server; default true when absent.
    let autoEventCheckInEnabled: Bool?
    /// Stored on server after the user accepts the first-share safety reminder.
    let sharingSafetyDisclaimerAcknowledged: Bool?
    /// When false, hide upcoming public “going” RSVPs from profile / web / public going lists.
    let showPublicGoingEventsOnProfile: Bool?
    /// `public` | `squads` (default) | `private` — who can see aggregated driving stats / progression on your profile.
    let driveStatsVisibility: String?
    /// Private Routes product access. Missing/null means no access.
    let routesAccessEnabled: Bool?

    /// Accounts this user has blocked (only present on your own session user).
    let blockedUserIds: [String]?
    /// IANA time zone for server-side local-time notifications.
    let timeZone: String?
    let timeZoneUpdatedAt: String?

    enum CodingKeys: String, CodingKey {
        case id = "_id"
        case displayName
        case handle
        case avatarUrl
        case mapAccentKey
        case phoneNumber
        case vehicle
        case lastPresenceAt
        case autoEventCheckInEnabled
        case sharingSafetyDisclaimerAcknowledged
        case showPublicGoingEventsOnProfile
        case driveStatsVisibility
        case routesAccessEnabled
        case blockedUserIds
        case timeZone
        case timeZoneUpdatedAt
    }

    var resolvedAutoEventCheckInEnabled: Bool {
        autoEventCheckInEnabled ?? true
    }

    var resolvedShowPublicGoingEventsOnProfile: Bool {
        showPublicGoingEventsOnProfile ?? true
    }

    var resolvedSharingSafetyDisclaimerAcknowledged: Bool {
        sharingSafetyDisclaimerAcknowledged == true
    }

    var resolvedDriveStatsVisibility: DriveStatsVisibilitySetting {
        guard let raw = driveStatsVisibility?.trimmingCharacters(in: .whitespacesAndNewlines),
              !raw.isEmpty,
              let v = DriveStatsVisibilitySetting(rawValue: raw)
        else { return .squads }
        return v
    }

    var resolvedBlockedUserIds: [String] {
        blockedUserIds ?? []
    }

    init(
        id: String,
        displayName: String,
        handle: String,
        avatarUrl: String?,
        mapAccentKey: String?,
        phoneNumber: String?,
        vehicle: VehicleDTO?,
        lastPresenceAt: String?,
        autoEventCheckInEnabled: Bool?,
        sharingSafetyDisclaimerAcknowledged: Bool?,
        showPublicGoingEventsOnProfile: Bool?,
        driveStatsVisibility: String?,
        routesAccessEnabled: Bool?,
        blockedUserIds: [String]?,
        timeZone: String? = nil,
        timeZoneUpdatedAt: String? = nil
    ) {
        self.id = id
        self.displayName = displayName
        self.handle = handle
        self.avatarUrl = avatarUrl
        self.mapAccentKey = mapAccentKey
        self.phoneNumber = phoneNumber
        self.vehicle = vehicle
        self.lastPresenceAt = lastPresenceAt
        self.autoEventCheckInEnabled = autoEventCheckInEnabled
        self.sharingSafetyDisclaimerAcknowledged = sharingSafetyDisclaimerAcknowledged
        self.showPublicGoingEventsOnProfile = showPublicGoingEventsOnProfile
        self.driveStatsVisibility = driveStatsVisibility
        self.routesAccessEnabled = routesAccessEnabled
        self.blockedUserIds = blockedUserIds
        self.timeZone = timeZone
        self.timeZoneUpdatedAt = timeZoneUpdatedAt
    }
}

func canAccessRoutes(_ user: UserDTO?) -> Bool {
    true
}

struct AuthVerifyOTPResponseDTO: Decodable {
    let token: String?
    let user: UserDTO?
    let isNewUser: Bool?
    let signupChallengeToken: String?
    let needsInviteCode: Bool?
}

/// Successful login or completed signup (`complete-signup` / legacy `verify-otp` returning a session).
struct AuthSessionDTO: Decodable {
    let token: String
    let user: UserDTO
    let isNewUser: Bool
}

struct InviteLinkDTO: Decodable {
    struct CircleSummary: Decodable {
        let id: String
        let name: String
    }
    let token: String
    let url: String
    let circle: CircleSummary
    let remainingUses: Int?
    let personalRemainingUses: Int?
}

struct SignupInviteBalanceDTO: Decodable {
    let remainingUses: Int
    let maxUses: Int
    let usesCount: Int
    let invitesPerLevelUp: Int?
    let nextLevelDisplayName: String?
}

struct InviteLinkResolveDTO: Decodable {
    struct CircleSummary: Decodable {
        let id: String
        let name: String
        let description: String?
    }
    struct InviterSummary: Decodable {
        let id: String
        let displayName: String
    }
    let token: String
    let circle: CircleSummary?
    let invitedBy: InviterSummary?
    let remainingUses: Int?
}

struct PresenceCircleResponseDTO: Decodable {
    struct PresenceDTO: Decodable {
        let userId: String
        let circleId: String
        let isActive: Bool
        /// False = client left app / backgrounded this channel. Omitted in older payloads — treat as in-app.
        let inApp: Bool?
        let speedMph: Double
        let movementMode: String?
        let lat: Double?
        let lng: Double?
        let updatedAt: String?
        let carId: String?
        let logoSlug: String?

        enum CodingKeys: String, CodingKey {
            case userId
            case circleId
            case isActive
            case inApp
            case speedMph
            case movementMode
            case lat
            case lng
            case updatedAt
            case carId
            case logoSlug
        }

        init(from decoder: Decoder) throws {
            let c = try decoder.container(keyedBy: CodingKeys.self)
            userId = try c.decode(String.self, forKey: .userId)
            circleId = try c.decode(String.self, forKey: .circleId)
            isActive = try c.decode(Bool.self, forKey: .isActive)
            inApp = try c.decodeIfPresent(Bool.self, forKey: .inApp)
            speedMph = try c.decodeIfPresent(Double.self, forKey: .speedMph) ?? 0
            movementMode = try c.decodeIfPresent(String.self, forKey: .movementMode)
            lat = try c.decodeIfPresent(Double.self, forKey: .lat)
            lng = try c.decodeIfPresent(Double.self, forKey: .lng)
            updatedAt = try c.decodeIfPresent(String.self, forKey: .updatedAt)
            carId = try c.decodeIfPresent(String.self, forKey: .carId)
            logoSlug = try c.decodeIfPresent(String.self, forKey: .logoSlug)
        }
    }

    let members: [PresenceDTO]
}

struct CircleInviteDTO: Decodable, Identifiable {
    struct CircleSummaryDTO: Decodable {
        let id: String
        let name: String
        let description: String?

        enum CodingKeys: String, CodingKey {
            case id = "_id"
            case name
            case description
        }
    }

    struct InviterSummaryDTO: Decodable {
        let id: String
        let displayName: String
        let handle: String

        enum CodingKeys: String, CodingKey {
            case id = "_id"
            case displayName
            case handle
        }
    }

    let id: String
    let circleId: String
    let circle: CircleSummaryDTO?
    let phoneNumber: String
    let invitedByUserId: String
    let invitedByUser: InviterSummaryDTO?
    let status: String

    enum CodingKeys: String, CodingKey {
        case id = "_id"
        case circleId
        case phoneNumber
        case invitedByUserId
        case status
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = try container.decode(String.self, forKey: .id)
        phoneNumber = try container.decode(String.self, forKey: .phoneNumber)
        status = try container.decode(String.self, forKey: .status)

        if let circleObj = try? container.decode(CircleSummaryDTO.self, forKey: .circleId) {
            circle = circleObj
            circleId = circleObj.id
        } else {
            circleId = try container.decode(String.self, forKey: .circleId)
            circle = nil
        }

        if let inviterObj = try? container.decode(InviterSummaryDTO.self, forKey: .invitedByUserId) {
            invitedByUser = inviterObj
            invitedByUserId = inviterObj.id
        } else {
            invitedByUserId = try container.decode(String.self, forKey: .invitedByUserId)
            invitedByUser = nil
        }
    }
}

struct CircleChatMentionSpanDTO: Codable, Equatable, Hashable {
    let userId: String
    let start: Int
    let length: Int
}

struct CircleChatMessageDTO: Codable, Identifiable, Equatable {
    struct LinkPreviewDTO: Codable, Equatable {
        let status: String?
        let url: String?
        let finalUrl: String?
        let title: String?
        let description: String?
        let imageUrl: String?
        let siteName: String?
        let faviconUrl: String?
    }

    struct SenderDTO: Codable, Equatable {
        let id: String
        let displayName: String
        let avatarUrl: String?
        let mapAccentKey: String?

        enum CodingKeys: String, CodingKey {
            case id = "_id"
            case displayName
            case avatarUrl
            case mapAccentKey
        }

        init(id: String, displayName: String, avatarUrl: String?, mapAccentKey: String?) {
            self.id = id
            self.displayName = displayName
            self.avatarUrl = avatarUrl
            self.mapAccentKey = mapAccentKey
        }

        init(from decoder: Decoder) throws {
            let container = try decoder.container(keyedBy: CodingKeys.self)
            id = try container.decode(String.self, forKey: .id)
            displayName = try container.decodeIfPresent(String.self, forKey: .displayName) ?? "Someone"
            avatarUrl = try container.decodeIfPresent(String.self, forKey: .avatarUrl)
            mapAccentKey = try container.decodeIfPresent(String.self, forKey: .mapAccentKey)
        }

        func encode(to encoder: Encoder) throws {
            var container = encoder.container(keyedBy: CodingKeys.self)
            try container.encode(id, forKey: .id)
            try container.encode(displayName, forKey: .displayName)
            try container.encodeIfPresent(avatarUrl, forKey: .avatarUrl)
            try container.encodeIfPresent(mapAccentKey, forKey: .mapAccentKey)
        }
    }

    struct MessageReactionDTO: Codable, Equatable {
        let userId: String
        let emoji: String
        let user: SenderDTO?
        let createdAt: Date?

        enum CodingKeys: String, CodingKey {
            case userId
            case emoji
            case user
            case createdAt
        }

        init(from decoder: Decoder) throws {
            let container = try decoder.container(keyedBy: CodingKeys.self)
            userId = try container.decode(String.self, forKey: .userId)
            emoji = try container.decode(String.self, forKey: .emoji)
            user = try container.decodeIfPresent(SenderDTO.self, forKey: .user)
            if let raw = try container.decodeIfPresent(String.self, forKey: .createdAt) {
                createdAt = CircleChatMessageDTO.parseDate(raw)
            } else {
                createdAt = nil
            }
        }
    }

    struct ReplyPreviewDTO: Codable, Equatable {
        let id: String
        let body: String
        let imageUrl: String?
        let videoAttachment: VideoAttachmentDTO?
        let messageType: String?
        let systemKind: String?
        let senderUserId: String?
        let sender: SenderDTO?
        let deletedAt: Date?
        let createdAt: Date?

        enum CodingKeys: String, CodingKey {
            case id = "_id"
            case body
            case imageUrl
            case videoAttachment
            case messageType
            case systemKind
            case senderUserId
            case sender
            case deletedAt
            case createdAt
        }

        init(from decoder: Decoder) throws {
            let container = try decoder.container(keyedBy: CodingKeys.self)
            id = try container.decode(String.self, forKey: .id)
            body = try container.decode(String.self, forKey: .body)
            imageUrl = try container.decodeIfPresent(String.self, forKey: .imageUrl)
            videoAttachment = VideoAttachmentDTO.decodeIfValid(from: container, forKey: .videoAttachment)
            messageType = try container.decodeIfPresent(String.self, forKey: .messageType)
            systemKind = try container.decodeIfPresent(String.self, forKey: .systemKind)
            senderUserId = try container.decodeIfPresent(String.self, forKey: .senderUserId)
            sender = try container.decodeIfPresent(SenderDTO.self, forKey: .sender)
            if let raw = try container.decodeIfPresent(String.self, forKey: .deletedAt) {
                deletedAt = CircleChatMessageDTO.parseDate(raw)
            } else {
                deletedAt = nil
            }
            if let raw = try container.decodeIfPresent(String.self, forKey: .createdAt) {
                createdAt = CircleChatMessageDTO.parseDate(raw)
            } else {
                createdAt = nil
            }
        }
    }

    struct VideoAttachmentDTO: Codable, Equatable {
        let videoUrl: String
        let thumbnailUrl: String
        let durationSeconds: Double
        let width: Int
        let height: Int
        let mimeType: String

        enum CodingKeys: String, CodingKey {
            case videoUrl
            case thumbnailUrl
            case durationSeconds
            case width
            case height
            case mimeType
        }

        init(
            videoUrl: String,
            thumbnailUrl: String,
            durationSeconds: Double,
            width: Int,
            height: Int,
            mimeType: String
        ) {
            self.videoUrl = videoUrl
            self.thumbnailUrl = thumbnailUrl
            self.durationSeconds = durationSeconds
            self.width = width
            self.height = height
            self.mimeType = mimeType
        }

        init(from decoder: Decoder) throws {
            let container = try decoder.container(keyedBy: CodingKeys.self)
            videoUrl = try container.decode(String.self, forKey: .videoUrl)
            thumbnailUrl = try container.decode(String.self, forKey: .thumbnailUrl)
            durationSeconds = try container.decodeIfPresent(Double.self, forKey: .durationSeconds) ?? 0
            width = try container.decodeIfPresent(Int.self, forKey: .width) ?? 1
            height = try container.decodeIfPresent(Int.self, forKey: .height) ?? 1
            mimeType = try container.decodeIfPresent(String.self, forKey: .mimeType) ?? "video/mp4"
        }

        static func decodeIfValid<K: CodingKey>(
            from container: KeyedDecodingContainer<K>,
            forKey key: K
        ) -> VideoAttachmentDTO? {
            guard container.contains(key) else { return nil }
            return try? container.decode(VideoAttachmentDTO.self, forKey: key)
        }
    }

    struct EventAttachmentDTO: Codable, Equatable, Hashable, Identifiable {
        var id: String { eventId }

        let eventId: String
        let name: String?
        let startsAt: Date?
        let addressLabel: String?
        let bannerImageUrl: String?
        let visibility: String?
        let circleId: String?
        let parentDeletedAt: Date?

        enum CodingKeys: String, CodingKey {
            case eventId
            case name
            case startsAt
            case addressLabel
            case bannerImageUrl
            case visibility
            case circleId
            case parentDeletedAt
        }

        init(from decoder: Decoder) throws {
            let container = try decoder.container(keyedBy: CodingKeys.self)
            eventId = try EventAttachmentDTO.decodeEventId(from: container)
            name = try container.decodeIfPresent(String.self, forKey: .name)
            addressLabel = try container.decodeIfPresent(String.self, forKey: .addressLabel)
            bannerImageUrl = try container.decodeIfPresent(String.self, forKey: .bannerImageUrl)
            visibility = try container.decodeIfPresent(String.self, forKey: .visibility)
            circleId = try container.decodeIfPresent(String.self, forKey: .circleId)
            if let rawStartsAt = try container.decodeIfPresent(String.self, forKey: .startsAt) {
                startsAt = CircleChatMessageDTO.parseDate(rawStartsAt)
            } else {
                startsAt = nil
            }
            if let raw = try container.decodeIfPresent(String.self, forKey: .parentDeletedAt) {
                parentDeletedAt = CircleChatMessageDTO.parseDate(raw)
            } else {
                parentDeletedAt = nil
            }
        }

        var isParentDeleted: Bool { parentDeletedAt != nil }

        private static func decodeEventId(from container: KeyedDecodingContainer<CodingKeys>) throws -> String {
            if let s = try container.decodeIfPresent(String.self, forKey: .eventId) {
                let trimmed = s.trimmingCharacters(in: .whitespacesAndNewlines)
                if !trimmed.isEmpty { return trimmed }
            }
            struct MongoOid: Decodable {
                let oid: String
                enum CodingKeys: String, CodingKey {
                    case oid = "$oid"
                }
            }
            if let oid = try container.decodeIfPresent(MongoOid.self, forKey: .eventId) {
                let trimmed = oid.oid.trimmingCharacters(in: .whitespacesAndNewlines)
                if !trimmed.isEmpty { return trimmed }
            }
            throw DecodingError.dataCorruptedError(
                forKey: .eventId,
                in: container,
                debugDescription: "eventAttachment.eventId missing or not a string"
            )
        }

        static func decodeIfValid<K: CodingKey>(
            from container: KeyedDecodingContainer<K>,
            forKey key: K
        ) -> EventAttachmentDTO? {
            guard container.contains(key) else { return nil }
            return try? container.decode(EventAttachmentDTO.self, forKey: key)
        }
    }

    struct DriveAttachmentRoutePointDTO: Codable, Equatable {
        let lat: Double
        let lng: Double
        let type: String?
    }

    struct DriveAttachmentDTO: Codable, Equatable, Identifiable {
        var id: String { driveId }

        let driveId: String
        let title: String?
        let driveTypeLabel: String?
        let completedAt: Date?
        let distanceMeters: Double?
        let driveTimeSeconds: Int?
        let markerCount: Int?
        let routePoints: [DriveAttachmentRoutePointDTO]?
        let roadCoordinates: [DriveAttachmentRoutePointDTO]?
        let completedWaypointIndexes: [Int]?
        let parentDeletedAt: Date?
        let mapPreviewUrl: String?

        enum CodingKeys: String, CodingKey {
            case driveId
            case title
            case driveTypeLabel
            case completedAt
            case distanceMeters
            case driveTimeSeconds
            case markerCount
            case routePoints
            case roadCoordinates
            case completedWaypointIndexes
            case parentDeletedAt
            case mapPreviewUrl
        }

        init(from decoder: Decoder) throws {
            let container = try decoder.container(keyedBy: CodingKeys.self)
            driveId = try Self.decodeDriveId(from: container)
            title = try container.decodeIfPresent(String.self, forKey: .title)
            driveTypeLabel = try container.decodeIfPresent(String.self, forKey: .driveTypeLabel)
            distanceMeters = try container.decodeIfPresent(Double.self, forKey: .distanceMeters)
            driveTimeSeconds = try container.decodeIfPresent(Int.self, forKey: .driveTimeSeconds)
            markerCount = try container.decodeIfPresent(Int.self, forKey: .markerCount)
            routePoints = try container.decodeIfPresent([DriveAttachmentRoutePointDTO].self, forKey: .routePoints)
            roadCoordinates = try container.decodeIfPresent([DriveAttachmentRoutePointDTO].self, forKey: .roadCoordinates)
            completedWaypointIndexes = try container.decodeIfPresent([Int].self, forKey: .completedWaypointIndexes)
            mapPreviewUrl = try container.decodeIfPresent(String.self, forKey: .mapPreviewUrl)
            if let raw = try container.decodeIfPresent(String.self, forKey: .completedAt) {
                completedAt = CircleChatMessageDTO.parseDate(raw)
            } else {
                completedAt = nil
            }
            if let raw = try container.decodeIfPresent(String.self, forKey: .parentDeletedAt) {
                parentDeletedAt = CircleChatMessageDTO.parseDate(raw)
            } else {
                parentDeletedAt = nil
            }
        }

        var isParentDeleted: Bool { parentDeletedAt != nil }

        private static func decodeDriveId(from container: KeyedDecodingContainer<CodingKeys>) throws -> String {
            if let s = try container.decodeIfPresent(String.self, forKey: .driveId) {
                let trimmed = s.trimmingCharacters(in: .whitespacesAndNewlines)
                if !trimmed.isEmpty { return trimmed }
            }
            struct MongoOid: Decodable {
                let oid: String
                enum CodingKeys: String, CodingKey {
                    case oid = "$oid"
                }
            }
            if let oid = try container.decodeIfPresent(MongoOid.self, forKey: .driveId) {
                let trimmed = oid.oid.trimmingCharacters(in: .whitespacesAndNewlines)
                if !trimmed.isEmpty { return trimmed }
            }
            throw DecodingError.dataCorruptedError(
                forKey: .driveId,
                in: container,
                debugDescription: "driveAttachment.driveId missing or not a string"
            )
        }

        static func decodeIfValid<K: CodingKey>(
            from container: KeyedDecodingContainer<K>,
            forKey key: K
        ) -> DriveAttachmentDTO? {
            guard container.contains(key) else { return nil }
            return try? container.decode(DriveAttachmentDTO.self, forKey: key)
        }
    }

    struct PlaceAttachmentDTO: Codable, Equatable, Identifiable {
        var id: String { placeId ?? "\(latitude),\(longitude)" }

        let placeId: String?
        let name: String?
        let addressSummary: String?
        let latitude: Double
        let longitude: Double
        let parentDeletedAt: Date?
        let mapPreviewUrl: String?

        enum CodingKeys: String, CodingKey {
            case placeId
            case name
            case addressSummary
            case latitude
            case longitude
            case parentDeletedAt
            case mapPreviewUrl
        }

        init(from decoder: Decoder) throws {
            let container = try decoder.container(keyedBy: CodingKeys.self)
            placeId = try container.decodeIfPresent(String.self, forKey: .placeId)
                .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
                .flatMap { $0.isEmpty ? nil : $0 }
            name = try container.decodeIfPresent(String.self, forKey: .name)
            addressSummary = try container.decodeIfPresent(String.self, forKey: .addressSummary)
            latitude = try container.decode(Double.self, forKey: .latitude)
            longitude = try container.decode(Double.self, forKey: .longitude)
            mapPreviewUrl = try container.decodeIfPresent(String.self, forKey: .mapPreviewUrl)
            if let raw = try container.decodeIfPresent(String.self, forKey: .parentDeletedAt) {
                parentDeletedAt = CircleChatMessageDTO.parseDate(raw)
            } else {
                parentDeletedAt = nil
            }
        }

        init(
            placeId: String?,
            name: String?,
            addressSummary: String?,
            latitude: Double,
            longitude: Double,
            parentDeletedAt: Date? = nil,
            mapPreviewUrl: String? = nil
        ) {
            self.placeId = placeId
            self.name = name
            self.addressSummary = addressSummary
            self.latitude = latitude
            self.longitude = longitude
            self.parentDeletedAt = parentDeletedAt
            self.mapPreviewUrl = mapPreviewUrl
        }

        var isParentDeleted: Bool { parentDeletedAt != nil }

        var displayTitle: String {
            let trimmedName = name?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            if !trimmedName.isEmpty { return trimmedName }
            let trimmedAddress = addressSummary?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            if !trimmedAddress.isEmpty { return trimmedAddress }
            return String(localized: "chat_place_attachment_fallback_title")
        }

        static func decodeIfValid<K: CodingKey>(
            from container: KeyedDecodingContainer<K>,
            forKey key: K
        ) -> PlaceAttachmentDTO? {
            guard container.contains(key) else { return nil }
            return try? container.decode(PlaceAttachmentDTO.self, forKey: key)
        }

        func savedPlaceSnapshot(fallbackID: String) -> SavedPlaceDTO {
            SavedPlaceDTO(
                id: placeId ?? fallbackID,
                name: displayTitle,
                latitude: latitude,
                longitude: longitude,
                placeKind: placeId == nil ? "coordinates" : "other",
                poiCategory: nil,
                addressSummary: addressSummary,
                source: nil,
                location: SavedPlaceLocationDTO(type: "Point", coordinates: [longitude, latitude]),
                createdAt: nil
            )
        }
    }

    let id: String
    let circleId: String
    let senderUserId: String?
    let sender: SenderDTO?
    let body: String
    let messageType: String
    let systemKind: String?
    let clientMessageId: String?
    let linkPreview: LinkPreviewDTO?
    let eventAttachment: EventAttachmentDTO?
    let driveAttachment: DriveAttachmentDTO?
    let placeAttachment: PlaceAttachmentDTO?
    /// Present when the server stored a rich attachment (`drive`, `event`, …). Clients show a fallback card if they cannot render it.
    let richAttachmentType: String?
    let imageUrl: String?
    let videoAttachment: VideoAttachmentDTO?
    let replyToMessageId: String?
    let replyTo: ReplyPreviewDTO?
    let reactions: [MessageReactionDTO]
    let mentions: [CircleChatMentionSpanDTO]
    let createdAt: Date
    let deletedAt: Date?
    let editedAt: Date?
    let updatedAt: Date?

    enum CodingKeys: String, CodingKey {
        case id = "_id"
        case circleId
        case senderUserId
        case sender
        case body
        case messageType
        case systemKind
        case clientMessageId
        case linkPreview
        case eventAttachment
        case driveAttachment
        case placeAttachment
        case richAttachmentType
        case imageUrl
        case videoAttachment
        case replyToMessageId
        case replyTo
        case reactions
        case mentions
        case createdAt
        case deletedAt
        case editedAt
        case updatedAt
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = try container.decode(String.self, forKey: .id)
        circleId = try container.decode(String.self, forKey: .circleId)
        let decodedSenderUserId = try container.decodeIfPresent(String.self, forKey: .senderUserId)
        sender = try container.decodeIfPresent(SenderDTO.self, forKey: .sender)
        senderUserId = decodedSenderUserId ?? sender?.id
        body = try container.decode(String.self, forKey: .body)
        messageType = try container.decodeIfPresent(String.self, forKey: .messageType) ?? "user"
        systemKind = try container.decodeIfPresent(String.self, forKey: .systemKind)
        clientMessageId = try container.decodeIfPresent(String.self, forKey: .clientMessageId)
        linkPreview = try container.decodeIfPresent(LinkPreviewDTO.self, forKey: .linkPreview)
        eventAttachment = EventAttachmentDTO.decodeIfValid(from: container, forKey: .eventAttachment)
        driveAttachment = DriveAttachmentDTO.decodeIfValid(from: container, forKey: .driveAttachment)
        placeAttachment = PlaceAttachmentDTO.decodeIfValid(from: container, forKey: .placeAttachment)
        richAttachmentType = try container.decodeIfPresent(String.self, forKey: .richAttachmentType)
        imageUrl = try container.decodeIfPresent(String.self, forKey: .imageUrl)
        videoAttachment = VideoAttachmentDTO.decodeIfValid(from: container, forKey: .videoAttachment)
        replyToMessageId = try container.decodeIfPresent(String.self, forKey: .replyToMessageId)
        replyTo = try container.decodeIfPresent(ReplyPreviewDTO.self, forKey: .replyTo)
        reactions = try container.decodeIfPresent([MessageReactionDTO].self, forKey: .reactions) ?? []
        mentions = try container.decodeIfPresent([CircleChatMentionSpanDTO].self, forKey: .mentions) ?? []
        let rawCreatedAt = try container.decode(String.self, forKey: .createdAt)
        createdAt = Self.parseDate(rawCreatedAt) ?? Date()
        if let raw = try container.decodeIfPresent(String.self, forKey: .deletedAt) {
            deletedAt = Self.parseDate(raw)
        } else {
            deletedAt = nil
        }
        if let raw = try container.decodeIfPresent(String.self, forKey: .editedAt) {
            editedAt = Self.parseDate(raw)
        } else {
            editedAt = nil
        }
        if let raw = try container.decodeIfPresent(String.self, forKey: .updatedAt) {
            updatedAt = Self.parseDate(raw)
        } else {
            updatedAt = nil
        }
    }

    init(
        id: String,
        circleId: String,
        senderUserId: String?,
        sender: SenderDTO?,
        body: String,
        messageType: String = "user",
        systemKind: String? = nil,
        clientMessageId: String? = nil,
        linkPreview: LinkPreviewDTO? = nil,
        eventAttachment: EventAttachmentDTO? = nil,
        driveAttachment: DriveAttachmentDTO? = nil,
        placeAttachment: PlaceAttachmentDTO? = nil,
        richAttachmentType: String? = nil,
        imageUrl: String? = nil,
        videoAttachment: VideoAttachmentDTO? = nil,
        replyToMessageId: String? = nil,
        replyTo: ReplyPreviewDTO? = nil,
        reactions: [MessageReactionDTO] = [],
        mentions: [CircleChatMentionSpanDTO] = [],
        createdAt: Date = Date(),
        deletedAt: Date? = nil,
        editedAt: Date? = nil,
        updatedAt: Date? = nil
    ) {
        self.id = id
        self.circleId = circleId
        self.senderUserId = senderUserId
        self.sender = sender
        self.body = body
        self.messageType = messageType
        self.systemKind = systemKind
        self.clientMessageId = clientMessageId
        self.linkPreview = linkPreview
        self.eventAttachment = eventAttachment
        self.driveAttachment = driveAttachment
        self.placeAttachment = placeAttachment
        self.richAttachmentType = richAttachmentType
        self.imageUrl = imageUrl
        self.videoAttachment = videoAttachment
        self.replyToMessageId = replyToMessageId
        self.replyTo = replyTo
        self.reactions = reactions
        self.mentions = mentions
        self.createdAt = createdAt
        self.deletedAt = deletedAt
        self.editedAt = editedAt
        self.updatedAt = updatedAt
    }

    static func pendingVideo(
        clientMessageId: String,
        circleId: String,
        body: String,
        senderUserId: String?,
        sender: SenderDTO?,
        videoAttachment: VideoAttachmentDTO,
        replyToMessageId: String?,
        replyTo: ReplyPreviewDTO?
    ) -> CircleChatMessageDTO {
        CircleChatMessageDTO(
            id: "pending-\(clientMessageId)",
            circleId: circleId,
            senderUserId: senderUserId,
            sender: sender,
            body: body,
            clientMessageId: clientMessageId,
            videoAttachment: videoAttachment,
            replyToMessageId: replyToMessageId,
            replyTo: replyTo
        )
    }

    static func parseDate(_ raw: String) -> Date? {
        let fractional = ISO8601DateFormatter()
        fractional.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        if let date = fractional.date(from: raw) { return date }
        return ISO8601DateFormatter().date(from: raw)
    }

    /// Stable sender id for UI, unread, and cache indexing when API omits top-level senderUserId.
    var resolvedSenderUserId: String {
        sender?.id ?? senderUserId ?? ""
    }
}

struct CircleChatMessagesResponseDTO: Decodable {
    let messages: [CircleChatMessageDTO]
}

struct CircleChatMessageResponseDTO: Decodable {
    let message: CircleChatMessageDTO
}

struct NextUpEventDismissalDTO: Decodable, Equatable {
    let userId: String?
    let squadId: String?
    let circleId: String?
    let eventId: String
    let dismissedAt: Date?
    let dismissedContext: String

    enum CodingKeys: String, CodingKey {
        case userId
        case squadId
        case circleId
        case eventId
        case dismissedAt
        case dismissedContext
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        userId = try container.decodeIfPresent(String.self, forKey: .userId)
        squadId = try container.decodeIfPresent(String.self, forKey: .squadId)
        circleId = try container.decodeIfPresent(String.self, forKey: .circleId)
        eventId = try container.decode(String.self, forKey: .eventId)
        dismissedContext = try container.decode(String.self, forKey: .dismissedContext)
        if let raw = try container.decodeIfPresent(String.self, forKey: .dismissedAt) {
            dismissedAt = CircleChatMessageDTO.parseDate(raw)
        } else {
            dismissedAt = nil
        }
    }
}

private struct NextUpEventDismissalsResponseDTO: Decodable {
    let dismissals: [NextUpEventDismissalDTO]
}

private struct NextUpEventDismissalResponseDTO: Decodable {
    let dismissal: NextUpEventDismissalDTO
}

struct DirectConversationDTO: Codable, Identifiable, Equatable {
    struct UserSummaryDTO: Codable, Equatable {
        let id: String
        let displayName: String?
        let avatarUrl: String?
        let mapAccentKey: String?

        enum CodingKeys: String, CodingKey {
            case id = "_id"
            case displayName
            case avatarUrl
            case mapAccentKey
        }

        init(id: String, displayName: String?, avatarUrl: String?, mapAccentKey: String?) {
            self.id = id
            self.displayName = displayName
            self.avatarUrl = avatarUrl
            self.mapAccentKey = mapAccentKey
        }

        init(from decoder: Decoder) throws {
            let container = try decoder.container(keyedBy: CodingKeys.self)
            id = try container.decode(String.self, forKey: .id)
            displayName = try container.decodeIfPresent(String.self, forKey: .displayName)
            avatarUrl = try container.decodeIfPresent(String.self, forKey: .avatarUrl)
            mapAccentKey = try container.decodeIfPresent(String.self, forKey: .mapAccentKey)
        }
    }

    /// Denormalized row preview from GET `/api/direct/conversations`.
    struct LastMessagePreviewDTO: Codable, Equatable {
        let bodyPreview: String
        let senderUserId: String
        let hasImage: Bool
        let hasEventAttachment: Bool

        enum CodingKeys: String, CodingKey {
            case bodyPreview
            case senderUserId
            case hasImage
            case hasEventAttachment
        }

        init(from decoder: Decoder) throws {
            let container = try decoder.container(keyedBy: CodingKeys.self)
            bodyPreview = try container.decodeIfPresent(String.self, forKey: .bodyPreview) ?? ""
            senderUserId = try container.decode(String.self, forKey: .senderUserId)
            hasImage = try container.decodeIfPresent(Bool.self, forKey: .hasImage) ?? false
            hasEventAttachment = try container.decodeIfPresent(Bool.self, forKey: .hasEventAttachment) ?? false
        }

        func encode(to encoder: Encoder) throws {
            var container = encoder.container(keyedBy: CodingKeys.self)
            try container.encode(bodyPreview, forKey: .bodyPreview)
            try container.encode(senderUserId, forKey: .senderUserId)
            try container.encode(hasImage, forKey: .hasImage)
            try container.encode(hasEventAttachment, forKey: .hasEventAttachment)
        }
    }

    let id: String
    let participantUserIds: [String]
    let otherUser: UserSummaryDTO?
    let lastMessageAt: Date?
    /// Always `"direct"` when present (private 1:1 threads only).
    let conversationType: String?
    let lastMessage: LastMessagePreviewDTO?

    enum CodingKeys: String, CodingKey {
        case id = "_id"
        case participantUserIds
        case otherUser
        case lastMessageAt
        case conversationType
        case lastMessage
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = try container.decode(String.self, forKey: .id)
        participantUserIds = try container.decode([String].self, forKey: .participantUserIds)
        otherUser = try container.decodeIfPresent(UserSummaryDTO.self, forKey: .otherUser)
        if let rawLastMessageAt = try container.decodeIfPresent(String.self, forKey: .lastMessageAt) {
            lastMessageAt = CircleChatMessageDTO.parseDate(rawLastMessageAt)
        } else {
            lastMessageAt = nil
        }
        conversationType = try container.decodeIfPresent(String.self, forKey: .conversationType)
        lastMessage = try container.decodeIfPresent(LastMessagePreviewDTO.self, forKey: .lastMessage)
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(id, forKey: .id)
        try container.encode(participantUserIds, forKey: .participantUserIds)
        try container.encodeIfPresent(otherUser, forKey: .otherUser)
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        if let lastMessageAt {
            try container.encode(formatter.string(from: lastMessageAt), forKey: .lastMessageAt)
        }
        try container.encodeIfPresent(conversationType, forKey: .conversationType)
        try container.encodeIfPresent(lastMessage, forKey: .lastMessage)
    }
}

extension CircleChatMessageDTO.EventAttachmentDTO {
    /// Builds attachment-shaped metadata for navigation (e.g. squad chat “Next up”) when there is no backing message.
    init(previewFrom event: EventDTO, squadCircleId: String) {
        eventId = event.id
        name = event.name
        startsAt = event.startsAt
        let label = event.address?.label?.trimmingCharacters(in: .whitespacesAndNewlines)
        addressLabel = (label?.isEmpty == false) ? label : nil
        bannerImageUrl = event.bannerImage?.url
        visibility = event.visibility
        circleId = event.circleId ?? squadCircleId
        parentDeletedAt = nil
    }

    /// When a message only has a link preview to `driftd.com/e/...` or legacy `ottomot.to/e/...` (older shares / pasted links) and no `eventAttachment` payload.
    init(inferredEventRef: String, squadCircleId: String?) {
        let trimmed = inferredEventRef.trimmingCharacters(in: .whitespacesAndNewlines)
        eventId = trimmed
        name = nil
        startsAt = nil
        addressLabel = nil
        bannerImageUrl = nil
        visibility = nil
        circleId = squadCircleId
        parentDeletedAt = nil
    }
}

struct DirectMessageDTO: Codable, Identifiable, Equatable {
    let id: String
    let conversationId: String
    let senderUserId: String
    let sender: DirectConversationDTO.UserSummaryDTO?
    let body: String
    let imageUrl: String?
    let videoAttachment: CircleChatMessageDTO.VideoAttachmentDTO?
    let clientMessageId: String?
    let linkPreview: CircleChatMessageDTO.LinkPreviewDTO?
    let eventAttachment: CircleChatMessageDTO.EventAttachmentDTO?
    let placeAttachment: CircleChatMessageDTO.PlaceAttachmentDTO?
    let replyToMessageId: String?
    let replyTo: CircleChatMessageDTO.ReplyPreviewDTO?
    let reactions: [CircleChatMessageDTO.MessageReactionDTO]
    let createdAt: Date
    let messageType: String
    let deletedAt: Date?
    let editedAt: Date?
    let updatedAt: Date?

    enum CodingKeys: String, CodingKey {
        case id = "_id"
        case conversationId
        case senderUserId
        case sender
        case body
        case imageUrl
        case videoAttachment
        case clientMessageId
        case linkPreview
        case eventAttachment
        case placeAttachment
        case replyToMessageId
        case replyTo
        case reactions
        case createdAt
        case messageType
        case deletedAt
        case editedAt
        case updatedAt
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = try container.decode(String.self, forKey: .id)
        conversationId = try container.decode(String.self, forKey: .conversationId)
        senderUserId = try container.decode(String.self, forKey: .senderUserId)
        sender = try container.decodeIfPresent(DirectConversationDTO.UserSummaryDTO.self, forKey: .sender)
        body = try container.decode(String.self, forKey: .body)
        imageUrl = try container.decodeIfPresent(String.self, forKey: .imageUrl)
        videoAttachment = CircleChatMessageDTO.VideoAttachmentDTO.decodeIfValid(from: container, forKey: .videoAttachment)
        clientMessageId = try container.decodeIfPresent(String.self, forKey: .clientMessageId)
        linkPreview = try container.decodeIfPresent(CircleChatMessageDTO.LinkPreviewDTO.self, forKey: .linkPreview)
        eventAttachment = CircleChatMessageDTO.EventAttachmentDTO.decodeIfValid(from: container, forKey: .eventAttachment)
        placeAttachment = CircleChatMessageDTO.PlaceAttachmentDTO.decodeIfValid(from: container, forKey: .placeAttachment)
        replyToMessageId = try container.decodeIfPresent(String.self, forKey: .replyToMessageId)
        replyTo = try container.decodeIfPresent(CircleChatMessageDTO.ReplyPreviewDTO.self, forKey: .replyTo)
        reactions = try container.decodeIfPresent([CircleChatMessageDTO.MessageReactionDTO].self, forKey: .reactions) ?? []
        let rawCreatedAt = try container.decode(String.self, forKey: .createdAt)
        createdAt = CircleChatMessageDTO.parseDate(rawCreatedAt) ?? Date()
        messageType = try container.decodeIfPresent(String.self, forKey: .messageType) ?? "user"
        if let raw = try container.decodeIfPresent(String.self, forKey: .deletedAt) {
            deletedAt = CircleChatMessageDTO.parseDate(raw)
        } else {
            deletedAt = nil
        }
        if let raw = try container.decodeIfPresent(String.self, forKey: .editedAt) {
            editedAt = CircleChatMessageDTO.parseDate(raw)
        } else {
            editedAt = nil
        }
        if let raw = try container.decodeIfPresent(String.self, forKey: .updatedAt) {
            updatedAt = CircleChatMessageDTO.parseDate(raw)
        } else {
            updatedAt = nil
        }
    }

    init(
        id: String,
        conversationId: String,
        senderUserId: String,
        sender: DirectConversationDTO.UserSummaryDTO?,
        body: String,
        imageUrl: String? = nil,
        videoAttachment: CircleChatMessageDTO.VideoAttachmentDTO? = nil,
        clientMessageId: String? = nil,
        linkPreview: CircleChatMessageDTO.LinkPreviewDTO? = nil,
        eventAttachment: CircleChatMessageDTO.EventAttachmentDTO? = nil,
        placeAttachment: CircleChatMessageDTO.PlaceAttachmentDTO? = nil,
        replyToMessageId: String? = nil,
        replyTo: CircleChatMessageDTO.ReplyPreviewDTO? = nil,
        reactions: [CircleChatMessageDTO.MessageReactionDTO] = [],
        createdAt: Date = Date(),
        messageType: String = "user",
        deletedAt: Date? = nil,
        editedAt: Date? = nil,
        updatedAt: Date? = nil
    ) {
        self.id = id
        self.conversationId = conversationId
        self.senderUserId = senderUserId
        self.sender = sender
        self.body = body
        self.imageUrl = imageUrl
        self.videoAttachment = videoAttachment
        self.clientMessageId = clientMessageId
        self.linkPreview = linkPreview
        self.eventAttachment = eventAttachment
        self.placeAttachment = placeAttachment
        self.replyToMessageId = replyToMessageId
        self.replyTo = replyTo
        self.reactions = reactions
        self.createdAt = createdAt
        self.messageType = messageType
        self.deletedAt = deletedAt
        self.editedAt = editedAt
        self.updatedAt = updatedAt
    }

    static func pendingVideo(
        clientMessageId: String,
        conversationId: String,
        body: String,
        senderUserId: String,
        sender: DirectConversationDTO.UserSummaryDTO?,
        videoAttachment: CircleChatMessageDTO.VideoAttachmentDTO,
        replyToMessageId: String?,
        replyTo: CircleChatMessageDTO.ReplyPreviewDTO?
    ) -> DirectMessageDTO {
        DirectMessageDTO(
            id: "pending-\(clientMessageId)",
            conversationId: conversationId,
            senderUserId: senderUserId,
            sender: sender,
            body: body,
            videoAttachment: videoAttachment,
            clientMessageId: clientMessageId,
            replyToMessageId: replyToMessageId,
            replyTo: replyTo
        )
    }
}

struct DirectConversationResponseDTO: Decodable {
    let conversation: DirectConversationDTO
}

struct DirectConversationsResponseDTO: Decodable {
    let conversations: [DirectConversationDTO]
}

struct DirectMessagesResponseDTO: Decodable {
    let messages: [DirectMessageDTO]
}

struct DirectMessageResponseDTO: Decodable {
    let message: DirectMessageDTO
}

struct GarageCarDTO: Decodable {
    struct PhotoDTO: Decodable {
        let url: String
        let width: Int?
        let height: Int?
        let aspectRatio: Double?
    }

    let id: String
    let nickname: String?
    let make: String
    let makeId: String?
    let model: String
    let year: Int?
    let color: String?
    let logoSlug: String?
    let isPrimary: Bool
    let sortOrder: Int?
    let photo: PhotoDTO?

    enum CodingKeys: String, CodingKey {
        case id = "_id"
        case nickname
        case make
        case makeId
        case model
        case year
        case color
        case logoSlug
        case isPrimary
        case sortOrder
        case photo
    }
}

struct DriveRouteDTO: Decodable {
    let id: String
    let name: String
    let points: [RoutePointDTO]
    let roadCoordinates: [RoutePointDTO]
    let distanceMeters: Double
    let etaSeconds: Double
    let totalCheckpoints: Int?
    let completedWaypointIndexes: [Int]?
    let completedCheckpoints: Int?
    let completionReason: String?

    enum CodingKeys: String, CodingKey {
        case id = "_id"
        case name
        case points
        case roadCoordinates
        case distanceMeters
        case etaSeconds
        case totalCheckpoints
        case completedWaypointIndexes
        case completedCheckpoints
        case completionReason
    }
}

struct DrivePathPointDTO: Decodable {
    let lat: Double
    let lng: Double
    let speedMph: Double
    let capturedAt: Date?
}

struct DrivePathPointsResponseDTO: Decodable {
    let driveId: String
    let points: [DrivePathPointDTO]
}

struct DriveDTO: Decodable, Identifiable {
    let id: String
    let userId: String
    let circleId: String?
    let garageCarId: String?
    let title: String?
    let status: String
    let startTime: Date
    let endTime: Date?
    let distanceMeters: Double
    let maxSpeedMph: Double
    let avgSpeedMph: Double
    let pointsCount: Int
    let sharingAudience: String?
    let route: DriveRouteDTO?
    let garageCar: GarageCarDTO?

    enum CodingKeys: String, CodingKey {
        case id = "_id"
        case userId
        case circleId
        case garageCarId
        case title
        case status
        case startTime
        case endTime
        case distanceMeters
        case maxSpeedMph
        case avgSpeedMph
        case pointsCount
        case sharingAudience
        case route
        case garageCar
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = try container.decode(String.self, forKey: .id)
        userId = try container.decode(String.self, forKey: .userId)
        circleId = try container.decodeIfPresent(String.self, forKey: .circleId)
        garageCarId = try container.decodeIfPresent(String.self, forKey: .garageCarId)
        title = try container.decodeIfPresent(String.self, forKey: .title)
        status = try container.decode(String.self, forKey: .status)
        startTime = try container.decode(Date.self, forKey: .startTime)
        endTime = try container.decodeIfPresent(Date.self, forKey: .endTime)
        distanceMeters = try Self.decodeFlexibleDouble(from: container, forKey: .distanceMeters)
        maxSpeedMph = try Self.decodeFlexibleDouble(from: container, forKey: .maxSpeedMph)
        avgSpeedMph = try Self.decodeFlexibleDouble(from: container, forKey: .avgSpeedMph)
        pointsCount = try container.decode(Int.self, forKey: .pointsCount)
        sharingAudience = try container.decodeIfPresent(String.self, forKey: .sharingAudience)
        route = try container.decodeIfPresent(DriveRouteDTO.self, forKey: .route)
        garageCar = try container.decodeIfPresent(GarageCarDTO.self, forKey: .garageCar)
    }

    private static func decodeFlexibleDouble(
        from container: KeyedDecodingContainer<CodingKeys>,
        forKey key: CodingKeys
    ) throws -> Double {
        if let value = try container.decodeIfPresent(Double.self, forKey: key) {
            return value
        }
        if let value = try container.decodeIfPresent(Int.self, forKey: key) {
            return Double(value)
        }
        return 0
    }
}

struct ProfileProgressionDTO: Codable, Equatable {
    let points: Int
    let level: Int
    let tierId: String
    let tierName: String
    let levelImageName: String
    let currentLevelStartPoints: Int
    let nextLevelAt: Int?
    let pointsIntoLevel: Int
    let pointsRequiredForLevel: Int?
    let progress: Double
    let isMaxLevel: Bool

    static let starting = ProfileProgressionDTO(
        points: 1,
        level: 1,
        tierId: "rookie",
        tierName: "Rookie",
        levelImageName: "Level1",
        currentLevelStartPoints: 0,
        nextLevelAt: 250,
        pointsIntoLevel: 1,
        pointsRequiredForLevel: 250,
        progress: 1.0 / 250.0,
        isMaxLevel: false
    )

    /// Bundle asset name for the level badge (`Level1` … `Level20`). Prefer this for UI; it follows `level` so the icon matches even when `levelImageName` is missing or wrong in a cached/push payload.
    var levelBadgeAssetName: String {
        "Level\(min(20, max(1, level)))"
    }
}

struct ProfileLevelUpDTO: Codable, Equatable {
    let eventType: String?
    let pointsAwarded: Int
    let previousProgression: ProfileProgressionDTO
    let progression: ProfileProgressionDTO
    let nextProgression: ProfileProgressionDTO?
    let reachedDisplayName: String
    let nextDisplayName: String?
    let unlockedNewTier: Bool
}

struct ProgressionEventResponseDTO: Decodable, Equatable {
    let awarded: Bool
    let eventType: String?
    let dedupeKey: String?
    let pointsAwarded: Int
    let profilePoints: Int
    let progression: ProfileProgressionDTO
    let levelUp: ProfileLevelUpDTO?
}

struct DrivingStatsDTO: Decodable, Equatable {
    let userId: String
    let driveCount: Int
    let totalMilesDriven: Double
    let totalDriveTimeSeconds: Double
    let avgSpeedMph: Double
    let topSpeedMph: Double
    let accelerationScore: Int?
    let lastDriveAt: Date?
    let circleSharingSeconds: Double
    let publicSharingSeconds: Double
    let milesDrivenWhileSharing: Double
    let mostActiveTimeOfDay: String?
    let eventsAttended: Int
    /// When false, the viewer is not allowed to see real stats / progression; payload is redacted server-side.
    let driveStatsVisible: Bool
    let progression: ProfileProgressionDTO?

    enum CodingKeys: String, CodingKey {
        case userId
        case driveCount
        case totalMilesDriven
        case totalDriveTimeSeconds
        case avgSpeedMph
        case topSpeedMph
        case accelerationScore
        case lastDriveAt
        case lastActiveAt
        case circleSharingSeconds
        case publicSharingSeconds
        case milesDrivenWhileSharing
        case mostActiveTimeOfDay
        case eventsAttended
        case driveStatsVisible
        case progression
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        userId = try container.decode(String.self, forKey: .userId)
        driveCount = try container.decode(Int.self, forKey: .driveCount)
        totalMilesDriven = try container.decode(Double.self, forKey: .totalMilesDriven)
        totalDriveTimeSeconds = try container.decode(Double.self, forKey: .totalDriveTimeSeconds)
        avgSpeedMph = try container.decode(Double.self, forKey: .avgSpeedMph)
        topSpeedMph = try container.decode(Double.self, forKey: .topSpeedMph)
        accelerationScore = try container.decodeIfPresent(Int.self, forKey: .accelerationScore)
        lastDriveAt = try container.decodeIfPresent(Date.self, forKey: .lastDriveAt)
            ?? container.decodeIfPresent(Date.self, forKey: .lastActiveAt)
        circleSharingSeconds = try container.decode(Double.self, forKey: .circleSharingSeconds)
        publicSharingSeconds = try container.decode(Double.self, forKey: .publicSharingSeconds)
        milesDrivenWhileSharing = try container.decode(Double.self, forKey: .milesDrivenWhileSharing)
        mostActiveTimeOfDay = try container.decodeIfPresent(String.self, forKey: .mostActiveTimeOfDay)
        eventsAttended = try container.decodeIfPresent(Int.self, forKey: .eventsAttended) ?? 0
        driveStatsVisible = try container.decodeIfPresent(Bool.self, forKey: .driveStatsVisible) ?? true
        var decodedProgression = try container.decodeIfPresent(ProfileProgressionDTO.self, forKey: .progression)
        if driveStatsVisible, decodedProgression == nil {
            decodedProgression = .starting
        }
        progression = decodedProgression
    }
}

struct SquadGridLeaderDTO: Decodable, Identifiable, Equatable {
    let userId: String
    let displayName: String
    let avatarUrl: String?
    let mapAccentKey: String?
    let progressionTier: String
    let progressionLevel: Int
    let rank: Int
    let value: Double

    var id: String { userId }

    enum CodingKeys: String, CodingKey {
        case userId
        case displayName
        case avatarUrl
        case mapAccentKey
        case progressionTier
        case progressionLevel
        case rank
        case value
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        userId = try c.decode(String.self, forKey: .userId)
        displayName = try c.decode(String.self, forKey: .displayName)
        avatarUrl = try c.decodeIfPresent(String.self, forKey: .avatarUrl)
        mapAccentKey = try c.decodeIfPresent(String.self, forKey: .mapAccentKey)
        progressionTier = try c.decodeIfPresent(String.self, forKey: .progressionTier) ?? "rookie"
        progressionLevel = try c.decodeIfPresent(Int.self, forKey: .progressionLevel) ?? 1
        rank = try c.decode(Int.self, forKey: .rank)
        if let d = try? c.decode(Double.self, forKey: .value) {
            value = d
        } else if let i = try? c.decode(Int.self, forKey: .value) {
            value = Double(i)
        } else {
            value = 0
        }
    }
}

struct SquadGridMetricDTO: Decodable, Identifiable, Equatable {
    let key: String
    let label: String
    let subtitle: String
    let unit: String
    let leaders: [SquadGridLeaderDTO]

    var id: String { key }
}

struct SquadGridResponseDTO: Decodable, Equatable {
    let circleId: String
    let range: String
    let metrics: [SquadGridMetricDTO]
}

struct DriveLineCoordinateDTO: Codable {
    let lat: Double
    let lng: Double
    let markerType: String?

    enum CodingKeys: String, CodingKey {
        case lat
        case lng
        case markerType = "type"
    }
}

struct DriveLineDTO: Decodable, Identifiable {
    let id: String
    let circleId: String
    let createdByUserId: String
    let name: String
    let colorKey: String?
    let points: [DriveLineCoordinateDTO]
    let roadCoordinates: [DriveLineCoordinateDTO]
    let distanceMeters: Double
    let etaSeconds: Double

    enum CodingKeys: String, CodingKey {
        case id = "_id"
        case circleId
        case createdByUserId
        case name
        case colorKey
        case points
        case roadCoordinates
        case distanceMeters
        case etaSeconds
    }
}

struct RoutePointDTO: Codable, Hashable {
    let lat: Double
    let lng: Double
    let markerType: String?

    enum CodingKeys: String, CodingKey {
        case lat
        case lng
        case markerType = "type"
    }
}

struct SavedRouteDTO: Decodable, Identifiable, Hashable {
    let id: String
    let createdByUserId: String
    let name: String
    let points: [RoutePointDTO]
    let roadCoordinates: [RoutePointDTO]
    let distanceMeters: Double
    let etaSeconds: Double
    let createdAt: String?
    let updatedAt: String?

    enum CodingKeys: String, CodingKey {
        case id = "_id"
        case createdByUserId
        case name
        case points
        case roadCoordinates
        case distanceMeters
        case etaSeconds
        case createdAt
        case updatedAt
    }
}

struct RouteDriveSessionDTO: Decodable, Identifiable, Hashable {
    let id: String
    let routeId: String
    let driveId: String?
    let status: String
    let armedAt: Date?
    let startedAt: Date?
    let endedAt: Date?
    let completedWaypointIndexes: [Int]
    let currentProgress: Double
    let currentSpeedMph: Double
    let maxSpeedMph: Double
    let avgSpeedMph: Double
    let lastTriggeredWaypointIndex: Int?
    let stopReason: String?

    enum CodingKeys: String, CodingKey {
        case id = "_id"
        case routeId
        case driveId
        case status
        case armedAt
        case startedAt
        case endedAt
        case completedWaypointIndexes
        case currentProgress
        case currentSpeedMph
        case maxSpeedMph
        case avgSpeedMph
        case lastTriggeredWaypointIndex
        case stopReason
    }
}

struct SavedPlaceLocationDTO: Decodable, Hashable {
    let type: String?
    let coordinates: [Double]?
}

struct SavedPlaceDTO: Decodable, Identifiable, Hashable {
    let id: String
    let name: String
    let latitude: Double
    let longitude: Double
    /// Portable category for all platforms (`restaurant`, `gas_station`, `address`, `coordinates`, `other`).
    let placeKind: String?
    let poiCategory: String?
    let addressSummary: String?
    let source: String?
    let location: SavedPlaceLocationDTO?
    let createdAt: Date?

    enum CodingKeys: String, CodingKey {
        case id = "_id"
        case name
        case latitude
        case longitude
        case placeKind
        case poiCategory
        case addressSummary
        case source
        case location
        case createdAt
    }

    init(
        id: String,
        name: String,
        latitude: Double,
        longitude: Double,
        placeKind: String? = nil,
        poiCategory: String? = nil,
        addressSummary: String? = nil,
        source: String? = nil,
        location: SavedPlaceLocationDTO? = nil,
        createdAt: Date? = nil
    ) {
        self.id = id
        self.name = name
        self.latitude = latitude
        self.longitude = longitude
        self.placeKind = placeKind
        self.poiCategory = poiCategory
        self.addressSummary = addressSummary
        self.source = source
        self.location = location
        self.createdAt = createdAt
    }
}

struct EventCheckInDTO: Decodable, Equatable, Sendable {
    let id: String
    let eventId: String
    let userId: String
    let method: String
    let checkedInAt: Date
    let distanceMeters: Double?
    let source: String?
    let createdAt: Date?
    let updatedAt: Date?

    enum CodingKeys: String, CodingKey {
        case id
        case eventId
        case userId
        case method
        case checkedInAt
        case distanceMeters
        case source
        case createdAt
        case updatedAt
    }
}

struct EventAttachedSquadDTO: Decodable, Identifiable, Hashable {
    let id: String
    let name: String
    let photoUrl: String?
    let addedByUserId: String?
    let addedByDisplayName: String?
}

struct AdminSquadDTO: Decodable, Identifiable {
    let id: String
    let name: String
    let photoUrl: String?
    let role: String?
}

struct SquadAssociationsResponseDTO: Decodable {
    let squads: [EventAttachedSquadDTO]
}

struct AdminSquadsResponseDTO: Decodable {
    let squads: [AdminSquadDTO]
}

struct EventDTO: Decodable, Identifiable {
    struct AddressDTO: Decodable {
        let label: String?
        let street1: String?
        let street2: String?
        let city: String?
        let region: String?
        let postalCode: String?
        let country: String?
    }

    struct BannerImageDTO: Decodable {
        let url: String
        let aspectRatio: Double?
    }

    struct LocationDTO: Decodable {
        let type: String?
        let coordinates: [Double]?
    }

    struct RsvpCountsDTO: Decodable {
        let interested: Int
        let going: Int
        let notGoing: Int
    }

    struct ContactRsvpDTO: Decodable {
        let status: String
        let respondedAt: Date?
        let user: UserDTO
    }

    let id: String
    let slug: String?
    let visibility: String?
    let eventType: String?
    let circleId: String?
    let createdByUserId: String?
    let name: String
    let description: String?
    let startsAt: Date
    let endsAt: Date?
    let address: AddressDTO?
    let location: LocationDTO?
    let bannerImage: BannerImageDTO?
    let rsvpCounts: RsvpCountsDTO?
    let contactsGoing: [UserDTO]
    let contactsRsvps: [ContactRsvpDTO]?
    let currentUserRsvp: String?
    let currentUserCheckIn: EventCheckInDTO?
    let attachedSquads: [EventAttachedSquadDTO]
    let isOfficialForCircle: Bool?
    /// IANA time zone when set by partner/import flows.
    let timeZone: String?
    /// Internal featured test events visible only to admin accounts.
    let adminOnly: Bool?

    init(
        id: String,
        slug: String?,
        visibility: String?,
        eventType: String? = nil,
        circleId: String?,
        createdByUserId: String? = nil,
        name: String,
        description: String?,
        startsAt: Date,
        endsAt: Date?,
        address: AddressDTO?,
        location: LocationDTO?,
        bannerImage: BannerImageDTO?,
        rsvpCounts: RsvpCountsDTO?,
        contactsGoing: [UserDTO],
        contactsRsvps: [ContactRsvpDTO]? = nil,
        currentUserRsvp: String?,
        currentUserCheckIn: EventCheckInDTO?,
        attachedSquads: [EventAttachedSquadDTO] = [],
        isOfficialForCircle: Bool? = nil,
        timeZone: String? = nil,
        adminOnly: Bool? = nil
    ) {
        self.id = id
        self.slug = slug
        self.visibility = visibility
        self.eventType = eventType
        self.circleId = circleId
        self.createdByUserId = createdByUserId
        self.name = name
        self.description = description
        self.startsAt = startsAt
        self.endsAt = endsAt
        self.address = address
        self.location = location
        self.bannerImage = bannerImage
        self.rsvpCounts = rsvpCounts
        self.contactsGoing = contactsGoing
        self.contactsRsvps = contactsRsvps
        self.currentUserRsvp = currentUserRsvp
        self.currentUserCheckIn = currentUserCheckIn
        self.attachedSquads = attachedSquads
        self.isOfficialForCircle = isOfficialForCircle
        self.timeZone = timeZone
        self.adminOnly = adminOnly
    }

    enum CodingKeys: String, CodingKey {
        case id = "_id"
        case slug
        case visibility
        case eventType
        case circleId
        case createdByUserId
        case name
        case description
        case startsAt
        case endsAt
        case address
        case location
        case bannerImage
        case rsvpCounts
        case contactsGoing
        case contactsRsvps
        case currentUserRsvp
        case currentUserCheckIn
        case attachedSquads
        case isOfficialForCircle
        case timeZone
        case adminOnly
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id)
        slug = try c.decodeIfPresent(String.self, forKey: .slug)
        visibility = try c.decodeIfPresent(String.self, forKey: .visibility)
        eventType = try c.decodeIfPresent(String.self, forKey: .eventType)
        circleId = try c.decodeIfPresent(String.self, forKey: .circleId)
        createdByUserId = try c.decodeIfPresent(String.self, forKey: .createdByUserId)
        name = try c.decode(String.self, forKey: .name)
        description = try c.decodeIfPresent(String.self, forKey: .description)
        startsAt = try c.decode(Date.self, forKey: .startsAt)
        endsAt = try c.decodeIfPresent(Date.self, forKey: .endsAt)
        address = try c.decodeIfPresent(AddressDTO.self, forKey: .address)
        location = try c.decodeIfPresent(LocationDTO.self, forKey: .location)
        bannerImage = try c.decodeIfPresent(BannerImageDTO.self, forKey: .bannerImage)
        rsvpCounts = try c.decodeIfPresent(RsvpCountsDTO.self, forKey: .rsvpCounts)
        contactsGoing = try c.decodeIfPresent([UserDTO].self, forKey: .contactsGoing) ?? []
        contactsRsvps = try c.decodeIfPresent([ContactRsvpDTO].self, forKey: .contactsRsvps)
        currentUserRsvp = try c.decodeIfPresent(String.self, forKey: .currentUserRsvp)
        currentUserCheckIn = try c.decodeIfPresent(EventCheckInDTO.self, forKey: .currentUserCheckIn)
        attachedSquads = try c.decodeIfPresent([EventAttachedSquadDTO].self, forKey: .attachedSquads) ?? []
        isOfficialForCircle = try c.decodeIfPresent(Bool.self, forKey: .isOfficialForCircle)
        timeZone = try c.decodeIfPresent(String.self, forKey: .timeZone)
        adminOnly = try c.decodeIfPresent(Bool.self, forKey: .adminOnly)
    }
}

extension EventDTO {
    /// Matches server: explicit `endsAt`, otherwise start + 2 hours.
    var eventCheckInWindowEnd: Date {
        if let endsAt { return endsAt }
        return startsAt.addingTimeInterval(7200)
    }

    var isInEventCheckInWindow: Bool {
        let now = Date()
        return now >= startsAt && now <= eventCheckInWindowEnd
    }

    /// Map layer beacons: has coordinates, not ended, and starts within the display horizon.
    func isEligibleForMapDisplay(
        at now: Date = Date(),
        horizonDays: Int = AppState.mapEventDisplayHorizonDays
    ) -> Bool {
        guard eventGeoCoordinate != nil else { return false }
        guard eventCheckInWindowEnd > now else { return false }
        let horizon = now.addingTimeInterval(TimeInterval(horizonDays) * 24 * 60 * 60)
        return startsAt <= horizon
    }

    /// GeoJSON coordinates are `[longitude, latitude]`.
    var eventGeoCoordinate: CLLocationCoordinate2D? {
        guard let coordinates = location?.coordinates, coordinates.count >= 2 else { return nil }
        let lng = coordinates[0]
        let lat = coordinates[1]
        guard (-90...90).contains(lat), (-180...180).contains(lng) else { return nil }
        return CLLocationCoordinate2D(latitude: lat, longitude: lng)
    }

    /// Memberwise helper for updating check-in after JSON decode does not expose `init`.
    func withCurrentUserCheckIn(_ checkIn: EventCheckInDTO?) -> EventDTO {
        EventDTO(
            id: id,
            slug: slug,
            visibility: visibility,
            eventType: eventType,
            circleId: circleId,
            createdByUserId: createdByUserId,
            name: name,
            description: description,
            startsAt: startsAt,
            endsAt: endsAt,
            address: address,
            location: location,
            bannerImage: bannerImage,
            rsvpCounts: rsvpCounts,
            contactsGoing: contactsGoing,
            contactsRsvps: contactsRsvps,
            currentUserRsvp: currentUserRsvp,
            currentUserCheckIn: checkIn,
            attachedSquads: attachedSquads,
            isOfficialForCircle: isOfficialForCircle,
            timeZone: timeZone
        )
    }
}

struct EventCheckInResultDTO: Decodable, Sendable {
    let checkedIn: Bool
    let alreadyCheckedIn: Bool
    let checkIn: EventCheckInDTO?
    let event: EventDTO?
}

/// Upcoming public events a user is “going” to (`GET /api/public/m/:userId`).
struct PublicGoingEventDTO: Decodable, Identifiable, Hashable {
    let id: String
    let slug: String?
    let name: String
    let startsAt: Date
    let addressLabel: String?
    let bannerImageUrl: String?

    enum CodingKeys: String, CodingKey {
        case id = "_id"
        case slug
        case name
        case startsAt
        case addressLabel
        case bannerImageUrl
    }

    /// Minimal `EventDTO` for navigation to `EventDetailView`.
    func asEventStub() -> EventDTO {
        EventDTO(
            id: id,
            slug: slug,
            visibility: "public",
            circleId: nil,
            name: name,
            description: nil,
            startsAt: startsAt,
            endsAt: nil,
            address: addressLabel.map {
                EventDTO.AddressDTO(
                    label: $0,
                    street1: nil,
                    street2: nil,
                    city: nil,
                    region: nil,
                    postalCode: nil,
                    country: nil
                )
            },
            location: nil,
            bannerImage: bannerImageUrl.map { EventDTO.BannerImageDTO(url: $0, aspectRatio: nil) },
            rsvpCounts: nil,
            contactsGoing: [],
            currentUserRsvp: nil,
            currentUserCheckIn: nil
        )
    }
}

private struct PublicMemberProfileGoingEventsResponse: Decodable {
    let publicGoingEvents: [PublicGoingEventDTO]?
}

final class APIClient {
    static let shared = APIClient()

    private let session: URLSession
    private(set) var authToken: String?
    private let decoder = JSONDecoder()
    private let encoder = JSONEncoder()

    private init() {
        let config = URLSessionConfiguration.ephemeral
        // When the API is reachable but slow, default waits can make the app feel frozen; fail fast instead.
        config.timeoutIntervalForRequest = 15
        config.timeoutIntervalForResource = 45
        config.waitsForConnectivity = false
        config.httpMaximumConnectionsPerHost = 8
        self.session = URLSession(configuration: config)
        decoder.dateDecodingStrategy = .iso8601
    }

    private static let clientTelemetryPlatformKey = "app_platform"
    private static let clientTelemetryVersionKey = "app_version"

    private static func clientTelemetryQueryItems() -> [URLQueryItem] {
        let short =
            (Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String) ?? "0"
        let build = (Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String) ?? ""
        let version = build.isEmpty ? short : "\(short) (\(build))"
        return [
            URLQueryItem(name: clientTelemetryPlatformKey, value: "ios"),
            URLQueryItem(name: clientTelemetryVersionKey, value: version),
        ]
    }

    private static func urlAppendingClientTelemetry(_ url: URL) -> URL {
        guard var components = URLComponents(url: url, resolvingAgainstBaseURL: false) else {
            return url
        }
        var items = components.queryItems ?? []
        items.removeAll { $0.name == clientTelemetryPlatformKey || $0.name == clientTelemetryVersionKey }
        items.append(contentsOf: clientTelemetryQueryItems())
        components.queryItems = items
        return components.url ?? url
    }

    private func iso8601APIString(from date: Date) -> String {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return formatter.string(from: date)
    }

    func setAuthToken(_ token: String?) {
        authToken = token
    }

    /// True when the outbound request carries a non-empty `Authorization: Bearer …` (after `performRaw` wiring).
    private static func requestHadNonEmptyBearer(_ request: URLRequest) -> Bool {
        guard let value = request.value(forHTTPHeaderField: "Authorization")?.trimmingCharacters(in: .whitespacesAndNewlines),
              !value.isEmpty
        else { return false }
        let prefix = "Bearer "
        guard value.lowercased().hasPrefix(prefix.lowercased()), value.count > prefix.count else { return false }
        let tokenPart = String(value.dropFirst(prefix.count)).trimmingCharacters(in: .whitespacesAndNewlines)
        return !tokenPart.isEmpty
    }

    func requestOTP(phoneNumber: String) async throws {
        var request = URLRequest(url: APIConfig.baseURL.appending(path: "/api/auth/request-otp"))
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try encoder.encode(["phoneNumber": phoneNumber])
        _ = try await performRaw(request)
    }

    func verifyOTP(phoneNumber: String, code: String) async throws -> AuthVerifyOTPResponseDTO {
        var request = URLRequest(url: APIConfig.baseURL.appending(path: "/api/auth/verify-otp"))
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        let payload: [String: String] = [
            "phoneNumber": phoneNumber,
            "code": code
        ]
        request.httpBody = try encoder.encode(payload)
        return try await perform(request)
    }

    func completeSignup(signupChallengeToken: String, displayName: String, inviteCode: String?) async throws -> AuthSessionDTO {
        var request = URLRequest(url: APIConfig.baseURL.appending(path: "/api/auth/complete-signup"))
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        var payload: [String: String] = [
            "signupChallengeToken": signupChallengeToken,
            "displayName": displayName
        ]
        if let inviteCode, !inviteCode.isEmpty {
            payload["inviteCode"] = inviteCode
        }
        request.httpBody = try encoder.encode(payload)
        return try await perform(request)
    }

    func checkSignupInvite(signupChallengeToken: String, inviteCode: String) async throws {
        var request = URLRequest(url: APIConfig.baseURL.appending(path: "/api/auth/check-signup-invite"))
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        let trimmed = inviteCode.trimmingCharacters(in: .whitespacesAndNewlines)
        let payload: [String: String] = [
            "signupChallengeToken": signupChallengeToken,
            "inviteCode": trimmed
        ]
        request.httpBody = try encoder.encode(payload)
        let (data, response) = try await performRaw(request)
        guard let http = response as? HTTPURLResponse else {
            throw URLError(.badServerResponse)
        }
        if (200...299).contains(http.statusCode) {
            return
        }
        struct ErrDTO: Decodable { let error: String? }
        if let err = try? decoder.decode(ErrDTO.self, from: data), let msg = err.error?.trimmingCharacters(in: .whitespacesAndNewlines), !msg.isEmpty {
            throw NSError(domain: "OttoAPI", code: http.statusCode, userInfo: [NSLocalizedDescriptionKey: msg])
        }
        throw URLError(.badServerResponse)
    }

    func updateUserDisplayName(userId: String, displayName: String) async throws {
        var request = URLRequest(url: APIConfig.baseURL.appending(path: "/api/users/\(userId)"))
        request.httpMethod = "PATCH"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        let payload: [String: String] = ["displayName": displayName]
        request.httpBody = try encoder.encode(payload)
        _ = try await performRaw(request)
    }

    func registerPushDeviceToken(
        token: String,
        environment: String,
        bundleId: String,
        appVersion: String?,
        timeZone: String? = nil
    ) async throws {
        var request = URLRequest(url: APIConfig.baseURL.appending(path: "/api/notifications/devices"))
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        var payload: [String: String] = [
            "token": token,
            "platform": "ios",
            "environment": environment,
            "bundleId": bundleId
        ]
        if let appVersion, !appVersion.isEmpty {
            payload["appVersion"] = appVersion
        }
        if let timeZone, !timeZone.isEmpty {
            payload["timeZone"] = timeZone
        }
        request.httpBody = try encoder.encode(payload)
        _ = try await performRaw(request)
    }

    func patchMeTimeZone(timeZone: String) async throws {
        var request = URLRequest(url: APIConfig.baseURL.appending(path: "/api/users/me/time-zone"))
        request.httpMethod = "PATCH"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try encoder.encode(["timeZone": timeZone])
        _ = try await performRaw(request)
    }

    /// Align server `chatIconBadgeCount` with in-app unread after read/reconcile (home-screen badge uses push `aps.badge` when away).
    func patchMeChatIconBadge(count: Int) async throws {
        let clamped = min(max(0, count), 99_999)
        var request = URLRequest(url: APIConfig.baseURL.appending(path: "/api/users/me/chat-icon-badge"))
        request.httpMethod = "PATCH"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try encoder.encode(["count": clamped])
        _ = try await performRaw(request)
    }

    struct RegisteredPushDeviceDTO: Decodable {
        let id: String
        let platform: String
        let environment: String
        let bundleId: String
        let tokenPrefix: String
        let appVersion: String?
        let lastRegisteredAt: String?
        let disabledAt: String?
        let isCurrentDevice: Bool?

        func matchesToken(_ token: String) -> Bool {
            let trimmed = token.trimmingCharacters(in: .whitespacesAndNewlines)
            guard trimmed.count > 12 else { return tokenPrefix == trimmed }
            let expected = "\(trimmed.prefix(8))…\(trimmed.suffix(4))"
            return tokenPrefix == expected
        }
    }

    private struct RegisteredPushDevicesResponse: Decodable {
        let devices: [RegisteredPushDeviceDTO]
    }

    func fetchRegisteredPushDevices() async throws -> [RegisteredPushDeviceDTO] {
        var request = URLRequest(url: APIConfig.baseURL.appending(path: "/api/notifications/devices"))
        request.httpMethod = "GET"
        let response: RegisteredPushDevicesResponse = try await perform(request)
        return response.devices
    }

    func fetchMe() async throws -> UserDTO {
        var request = URLRequest(url: APIConfig.baseURL.appending(path: "/api/auth/me"))
        request.httpMethod = "GET"
        return try await perform(request)
    }

    func blockUser(targetUserId: String) async throws -> UserDTO {
        var request = URLRequest(
            url: APIConfig.baseURL.appending(path: "/api/users/me/blocked-users/\(targetUserId)")
        )
        request.httpMethod = "POST"
        return try await perform(request)
    }

    func unblockUser(targetUserId: String) async throws -> UserDTO {
        var request = URLRequest(
            url: APIConfig.baseURL.appending(path: "/api/users/me/blocked-users/\(targetUserId)")
        )
        request.httpMethod = "DELETE"
        return try await perform(request)
    }

    func recordProgressionEvent(type: String) async throws -> ProgressionEventResponseDTO {
        var request = URLRequest(url: APIConfig.baseURL.appending(path: "/api/progression/events"))
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONSerialization.data(withJSONObject: ["type": type], options: [])
        return try await perform(request)
    }

    func fetchContacts() async throws -> [UserDTO] {
        var request = URLRequest(url: APIConfig.baseURL.appending(path: "/api/contacts"))
        request.httpMethod = "GET"
        return try await perform(request)
    }

    func fetchSignupInviteBalance() async throws -> SignupInviteBalanceDTO {
        var request = URLRequest(url: APIConfig.baseURL.appending(path: "/api/invite-links/me/balance"))
        request.httpMethod = "GET"
        return try await perform(request)
    }

    func createCircleInviteLink(
        circleId: String,
        expiresInDays: Int = 14,
        phoneNumber: String? = nil
    ) async throws -> InviteLinkDTO {
        var request = URLRequest(url: APIConfig.baseURL.appending(path: "/api/invite-links"))
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        var body: [String: Any] = [
            "circleId": circleId,
            "expiresInDays": expiresInDays,
        ]
        if let phoneNumber, !phoneNumber.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            body["phoneNumber"] = phoneNumber
        }
        request.httpBody = try JSONSerialization.data(withJSONObject: body, options: [])
        do {
            let response: InviteLinkDTO = try await perform(request)
            #if DEBUG
            let urlHost = URL(string: response.url)?.host ?? "?"
            let hasSquadQuery = response.url.contains("?squad=") || response.url.contains("?circleId=")
            OttoLog.api.info(
                "InviteLinkAPI success squad=\(response.circle.name, privacy: .public) urlHost=\(urlHost, privacy: .public) legacyQuery=\(hasSquadQuery, privacy: .public)"
            )
            if hasSquadQuery {
                print("InviteLinkAPI ⚠ URL still has squad query param — expected record-bound invite")
            }
            #endif
            return response
        } catch {
            #if DEBUG
            print("InviteLinkAPI ✗ POST /api/invite-links error=\(error.localizedDescription)")
            OttoLog.api.error("InviteLinkAPI failed: \(String(describing: error), privacy: .public)")
            #endif
            throw error
        }
    }

    func rotateCircleInviteLink(circleId: String) async throws -> InviteLinkDTO {
        var request = URLRequest(url: APIConfig.baseURL.appending(path: "/api/invite-links/rotate"))
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONSerialization.data(
            withJSONObject: ["circleId": circleId],
            options: []
        )
        return try await perform(request)
    }

    func resolveInviteLink(token: String, squadId: String? = nil) async throws -> InviteLinkResolveDTO {
        var components = URLComponents(
            url: APIConfig.baseURL.appending(path: "/api/invite-links/\(token)"),
            resolvingAgainstBaseURL: false
        )!
        if let squadId, !squadId.isEmpty {
            components.queryItems = [URLQueryItem(name: "squad", value: squadId)]
        }
        var request = URLRequest(url: components.url!)
        request.httpMethod = "GET"
        return try await perform(request)
    }

    func acceptInviteLink(token: String, circleId: String? = nil) async throws {
        var request = URLRequest(url: APIConfig.baseURL.appending(path: "/api/invite-links/\(token)/accept"))
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        var payload: [String: String] = [:]
        if let circleId, !circleId.isEmpty {
            payload["circleId"] = circleId
        }
        request.httpBody = try JSONSerialization.data(withJSONObject: payload, options: [])
        _ = try await performRaw(request)
    }

    func fetchCircles(userId: String? = nil) async throws -> [CircleDTO] {
        var components = URLComponents(
            url: APIConfig.baseURL.appending(path: "/api/circles"),
            resolvingAgainstBaseURL: false
        )!
        if let userId, !userId.isEmpty {
            components.queryItems = [URLQueryItem(name: "userId", value: userId)]
        }
        var request = URLRequest(url: components.url!)
        request.httpMethod = "GET"
        return try await perform(request)
    }

    func fetchSquadGrid(circleId: String, range: String = "all_time") async throws -> SquadGridResponseDTO {
        var components = URLComponents(
            url: APIConfig.baseURL.appending(path: "/api/circles/\(circleId)/grid"),
            resolvingAgainstBaseURL: false
        )!
        components.queryItems = [URLQueryItem(name: "range", value: range)]
        var request = URLRequest(url: components.url!)
        request.httpMethod = "GET"
        return try await perform(request)
    }

    func fetchUser(id: String) async throws -> UserDTO {
        var request = URLRequest(url: APIConfig.baseURL.appending(path: "/api/users/\(id)"))
        request.httpMethod = "GET"
        return try await perform(request)
    }

    func updateUserMapAccentKey(userId: String, mapAccentKey: String) async throws {
        var request = URLRequest(url: APIConfig.baseURL.appending(path: "/api/users/\(userId)"))
        request.httpMethod = "PATCH"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        let payload: [String: String] = ["mapAccentKey": mapAccentKey]
        request.httpBody = try JSONSerialization.data(withJSONObject: payload, options: [])
        _ = try await performRaw(request)
    }

    /// Multipart field name must be `photo` (JPEG data).
    func uploadAvatar(userId: String, imageData: Data) async throws -> UserDTO {
        let boundary = "Boundary-\(UUID().uuidString)"
        var request = URLRequest(url: APIConfig.baseURL.appending(path: "/api/users/\(userId)/avatar"))
        request.httpMethod = "POST"
        request.setValue("multipart/form-data; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")
        let crlf = "\r\n"
        var body = Data()
        body.append("--\(boundary)\(crlf)".data(using: .utf8)!)
        body.append(
            "Content-Disposition: form-data; name=\"photo\"; filename=\"avatar.jpg\"\(crlf)".data(using: .utf8)!
        )
        body.append("Content-Type: image/jpeg\(crlf)\(crlf)".data(using: .utf8)!)
        body.append(imageData)
        body.append("\(crlf)--\(boundary)--\(crlf)".data(using: .utf8)!)
        request.httpBody = body
        return try await perform(request)
    }

    /// Multipart field name must be `photo` (JPEG data). Only the squad owner may call this endpoint.
    func uploadCirclePhoto(circleId: String, imageData: Data) async throws -> CircleDTO {
        let boundary = "Boundary-\(UUID().uuidString)"
        var request = URLRequest(url: APIConfig.baseURL.appending(path: "/api/circles/\(circleId)/photo"))
        request.httpMethod = "POST"
        request.setValue("multipart/form-data; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")
        let crlf = "\r\n"
        var body = Data()
        body.append("--\(boundary)\(crlf)".data(using: .utf8)!)
        body.append(
            "Content-Disposition: form-data; name=\"photo\"; filename=\"squad.jpg\"\(crlf)".data(using: .utf8)!
        )
        body.append("Content-Type: image/jpeg\(crlf)\(crlf)".data(using: .utf8)!)
        body.append(imageData)
        body.append("\(crlf)--\(boundary)--\(crlf)".data(using: .utf8)!)
        request.httpBody = body
        return try await perform(request)
    }

    func deleteAccount(userId: String, confirmation: String) async throws {
        var request = URLRequest(url: APIConfig.baseURL.appending(path: "/api/users/\(userId)"))
        request.httpMethod = "DELETE"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONSerialization.data(
            withJSONObject: ["confirmation": confirmation],
            options: []
        )
        _ = try await performRaw(request)
    }

    func fetchMySavedPlaces() async throws -> [SavedPlaceDTO] {
        var request = URLRequest(url: APIConfig.baseURL.appending(path: "/api/places/mine"))
        request.httpMethod = "GET"
        return try await perform(request)
    }

    func createSavedPlace(
        name: String,
        latitude: Double,
        longitude: Double,
        placeKind: String,
        source: String,
        poiCategory: String?,
        addressSummary: String?
    ) async throws -> SavedPlaceDTO {
        var request = URLRequest(url: APIConfig.baseURL.appending(path: "/api/places"))
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        var body: [String: Any] = [
            "name": name,
            "latitude": latitude,
            "longitude": longitude,
            "placeKind": placeKind,
            "source": source,
        ]
        if let poiCategory, !poiCategory.isEmpty { body["poiCategory"] = poiCategory }
        if let addressSummary, !addressSummary.isEmpty { body["addressSummary"] = addressSummary }
        request.httpBody = try JSONSerialization.data(withJSONObject: body, options: [])
        return try await perform(request)
    }

    func updateSavedPlace(
        placeId: String,
        name: String?,
        addressSummary: String?,
        placeKind: String?,
        latitude: Double?,
        longitude: Double?
    ) async throws -> SavedPlaceDTO {
        var request = URLRequest(url: APIConfig.baseURL.appending(path: "/api/places/\(placeId)"))
        request.httpMethod = "PATCH"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        var body: [String: Any] = [:]
        if let name { body["name"] = name }
        if let addressSummary { body["addressSummary"] = addressSummary }
        if let placeKind { body["placeKind"] = placeKind }
        if let latitude { body["latitude"] = latitude }
        if let longitude { body["longitude"] = longitude }
        request.httpBody = try JSONSerialization.data(withJSONObject: body, options: [])
        return try await perform(request)
    }

    func deleteSavedPlace(placeId: String) async throws {
        var request = URLRequest(url: APIConfig.baseURL.appending(path: "/api/places/\(placeId)"))
        request.httpMethod = "DELETE"
        _ = try await performRaw(request)
    }

    func fetchUsers() async throws -> [UserDTO] {
        var request = URLRequest(url: APIConfig.baseURL.appending(path: "/api/users"))
        request.httpMethod = "GET"
        return try await perform(request)
    }

    func createCircle(name: String, ownerId: String) async throws -> CircleDTO {
        var request = URLRequest(url: APIConfig.baseURL.appending(path: "/api/circles"))
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try encoder.encode(["name": name])
        return try await perform(request)
    }

    private struct PatchCircleBody: Encodable {
        var name: String?
    }

    func patchCircle(circleId: String, name: String) async throws -> CircleDTO {
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        var request = URLRequest(url: APIConfig.baseURL.appending(path: "/api/circles/\(circleId)"))
        request.httpMethod = "PATCH"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try encoder.encode(PatchCircleBody(name: trimmed))
        return try await perform(request)
    }

    struct LeaveCircleResponseDTO: Decodable {
        let deleted: Bool?
        let circleId: String?
        let circle: CircleDTO?
    }

    /// Owner leaving while others remain → HTTP 403 with server message (handled separately).
    func leaveCircle(circleId: String) async throws -> LeaveCircleResponseDTO {
        var request = URLRequest(url: APIConfig.baseURL.appending(path: "/api/circles/\(circleId)/leave"))
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = Data("{}".utf8)
        let (data, response) = try await performRaw(request)
        guard let http = response as? HTTPURLResponse else {
            throw URLError(.badServerResponse)
        }
        if http.statusCode == 403 {
            struct ErrBody: Decodable { let error: String? }
            let msg = (try? decoder.decode(ErrBody.self, from: data))?.error ?? ""
            if msg == "You must transfer ownership before leaving the squad." {
                throw OttoLeaveCircleOwnershipRequiredError()
            }
            throw URLError(.badServerResponse)
        }
        guard (200 ... 299).contains(http.statusCode) else {
            throw URLError(.badServerResponse)
        }
        return try decoder.decode(LeaveCircleResponseDTO.self, from: data)
    }

    func addMemberToCircle(circleId: String, userId: String, role: String = "member") async throws {
        var request = URLRequest(url: APIConfig.baseURL.appending(path: "/api/circles/\(circleId)/members"))
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try encoder.encode([
            "userId": userId,
            "role": role
        ])
        _ = try await performRaw(request)
    }

    func removeMemberFromCircle(circleId: String, userId: String) async throws {
        var request = URLRequest(url: APIConfig.baseURL.appending(path: "/api/circles/\(circleId)/members/\(userId)"))
        request.httpMethod = "DELETE"
        _ = try await performRaw(request)
    }

    func patchCircleMemberRole(circleId: String, userId: String, role: String) async throws {
        var request = URLRequest(url: APIConfig.baseURL.appending(path: "/api/circles/\(circleId)/members/\(userId)"))
        request.httpMethod = "PATCH"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try encoder.encode(["role": role])
        _ = try await performRaw(request)
    }

    func fetchCircleInvites(circleId: String) async throws -> [CircleInviteDTO] {
        var request = URLRequest(url: APIConfig.baseURL.appending(path: "/api/circles/\(circleId)/invites"))
        request.httpMethod = "GET"
        return try await perform(request)
    }

    func fetchCircleChatMessages(
        circleId: String,
        limit: Int = 50,
        before: Date? = nil,
        after: Date? = nil
    ) async throws -> [CircleChatMessageDTO] {
        var components = URLComponents(
            url: APIConfig.baseURL.appending(path: "/api/chat/circles/\(circleId)/messages"),
            resolvingAgainstBaseURL: false
        )!
        var queryItems = [URLQueryItem(name: "limit", value: String(limit))]
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        if let before {
            queryItems.append(URLQueryItem(name: "before", value: formatter.string(from: before)))
        }
        if let after {
            queryItems.append(URLQueryItem(name: "after", value: formatter.string(from: after)))
        }
        components.queryItems = queryItems
        var request = URLRequest(url: components.url!)
        request.httpMethod = "GET"
        let response: CircleChatMessagesResponseDTO = try await perform(request)
        return response.messages
    }

    func sendCircleChatMessage(
        circleId: String,
        body: String,
        clientMessageId: String = UUID().uuidString,
        eventId: String? = nil,
        replyToMessageId: String? = nil,
        mentions: [CircleChatMentionSpanDTO] = [],
        photoJPEGData: Data? = nil,
        imageUrl: String? = nil
    ) async throws -> CircleChatMessageDTO {
        if let photoJPEGData {
            var fields: [String: String] = [
                "body": body,
                "clientMessageId": clientMessageId
            ]
            if let eventId {
                fields["eventId"] = eventId
            }
            if let replyToMessageId {
                fields["replyToMessageId"] = replyToMessageId
            }
            if !mentions.isEmpty {
                let encoded = try JSONEncoder().encode(mentions)
                fields["mentions"] = String(data: encoded, encoding: .utf8) ?? "[]"
            }
            let boundary = "Boundary-\(UUID().uuidString)"
            var request = URLRequest(
                url: APIConfig.baseURL.appending(path: "/api/chat/circles/\(circleId)/messages")
            )
            request.httpMethod = "POST"
            request.setValue(
                "multipart/form-data; boundary=\(boundary)",
                forHTTPHeaderField: "Content-Type"
            )
            request.httpBody = buildMultipartFormBody(
                boundary: boundary,
                fields: fields,
                files: [
                    (
                        fieldName: "photo",
                        fileData: photoJPEGData,
                        fileName: "photo.jpg",
                        mimeType: "image/jpeg"
                    ),
                ]
            )
            let response: CircleChatMessageResponseDTO = try await perform(request)
            return response.message
        }

        var request = URLRequest(url: APIConfig.baseURL.appending(path: "/api/chat/circles/\(circleId)/messages"))
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        var payload: [String: Any] = [
            "body": body,
            "clientMessageId": clientMessageId
        ]
        if let eventId {
            payload["eventId"] = eventId
        }
        if let replyToMessageId {
            payload["replyToMessageId"] = replyToMessageId
        }
        if !mentions.isEmpty {
            payload["mentions"] = mentions.map { ["userId": $0.userId, "start": $0.start, "length": $0.length] }
        }
        if let imageUrl, !imageUrl.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            payload["imageUrl"] = imageUrl.trimmingCharacters(in: .whitespacesAndNewlines)
        }
        request.httpBody = try JSONSerialization.data(withJSONObject: payload, options: [])
        let response: CircleChatMessageResponseDTO = try await perform(request)
        return response.message
    }

    func requestCircleChatVideoUploadURLs(
        circleId: String,
        videoContentType: String
    ) async throws -> ChatVideoUploadURLsResponse {
        var request = URLRequest(
            url: APIConfig.baseURL.appending(path: "/api/chat/circles/\(circleId)/messages/upload-urls")
        )
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONSerialization.data(withJSONObject: [
            "kind": "video",
            "videoContentType": videoContentType,
            "thumbnailContentType": "image/jpeg",
        ])
        return try await perform(request)
    }

    func sendCircleChatVideoMessage(
        circleId: String,
        body: String,
        clientMessageId: String,
        replyToMessageId: String? = nil,
        mentions: [CircleChatMentionSpanDTO] = [],
        videoAttachment: CircleChatMessageDTO.VideoAttachmentDTO
    ) async throws -> CircleChatMessageDTO {
        var request = URLRequest(url: APIConfig.baseURL.appending(path: "/api/chat/circles/\(circleId)/messages"))
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        var payload: [String: Any] = [
            "body": body,
            "clientMessageId": clientMessageId,
            "videoAttachment": [
                "videoUrl": videoAttachment.videoUrl,
                "thumbnailUrl": videoAttachment.thumbnailUrl,
                "durationSeconds": videoAttachment.durationSeconds,
                "width": videoAttachment.width,
                "height": videoAttachment.height,
                "mimeType": videoAttachment.mimeType,
            ],
        ]
        if let replyToMessageId {
            payload["replyToMessageId"] = replyToMessageId
        }
        if !mentions.isEmpty {
            payload["mentions"] = mentions.map { ["userId": $0.userId, "start": $0.start, "length": $0.length] }
        }
        request.httpBody = try JSONSerialization.data(withJSONObject: payload)
        let response: CircleChatMessageResponseDTO = try await perform(request)
        return response.message
    }

    func setCircleChatMessageReaction(
        circleId: String,
        messageId: String,
        emoji: String
    ) async throws -> CircleChatMessageDTO {
        var request = URLRequest(
            url: APIConfig.baseURL.appending(
                path: "/api/chat/circles/\(circleId)/messages/\(messageId)/reactions"
            )
        )
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONSerialization.data(
            withJSONObject: ["emoji": emoji],
            options: []
        )
        let response: CircleChatMessageResponseDTO = try await perform(request)
        return response.message
    }

    func clearCircleChatMessageReaction(circleId: String, messageId: String) async throws -> CircleChatMessageDTO {
        var request = URLRequest(
            url: APIConfig.baseURL.appending(
                path: "/api/chat/circles/\(circleId)/messages/\(messageId)/reactions"
            )
        )
        request.httpMethod = "DELETE"
        let response: CircleChatMessageResponseDTO = try await perform(request)
        return response.message
    }

    func patchCircleChatMessage(
        circleId: String,
        messageId: String,
        body: String,
        mentions: [CircleChatMentionSpanDTO] = []
    ) async throws -> CircleChatMessageDTO {
        var request = URLRequest(
            url: APIConfig.baseURL.appending(
                path: "/api/chat/circles/\(circleId)/messages/\(messageId)"
            )
        )
        request.httpMethod = "PATCH"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        var payload: [String: Any] = ["body": body]
        if !mentions.isEmpty {
            payload["mentions"] = mentions.map { ["userId": $0.userId, "start": $0.start, "length": $0.length] }
        }
        request.httpBody = try JSONSerialization.data(withJSONObject: payload, options: [])
        let response: CircleChatMessageResponseDTO = try await perform(request)
        return response.message
    }

    func deleteCircleChatMessage(circleId: String, messageId: String) async throws -> CircleChatMessageDTO {
        var request = URLRequest(
            url: APIConfig.baseURL.appending(
                path: "/api/chat/circles/\(circleId)/messages/\(messageId)"
            )
        )
        request.httpMethod = "DELETE"
        let response: CircleChatMessageResponseDTO = try await perform(request)
        return response.message
    }

    func fetchNextUpEventDismissals(circleId: String, eventIds: [String] = []) async throws -> [NextUpEventDismissalDTO] {
        var components = URLComponents(
            url: APIConfig.baseURL.appending(path: "/api/chat/circles/\(circleId)/next-up-dismissals"),
            resolvingAgainstBaseURL: false
        )!
        let ids = eventIds.map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }.filter { !$0.isEmpty }
        if !ids.isEmpty {
            components.queryItems = [URLQueryItem(name: "eventIds", value: ids.joined(separator: ","))]
        }
        var request = URLRequest(url: components.url!)
        request.httpMethod = "GET"
        let response: NextUpEventDismissalsResponseDTO = try await perform(request)
        return response.dismissals
    }

    @discardableResult
    func dismissNextUpEventBanner(
        circleId: String,
        eventId: String,
        dismissedContext: String
    ) async throws -> NextUpEventDismissalDTO {
        var request = URLRequest(
            url: APIConfig.baseURL.appending(
                path: "/api/chat/circles/\(circleId)/next-up-dismissals/\(eventId)"
            )
        )
        request.httpMethod = "PUT"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONSerialization.data(
            withJSONObject: ["dismissedContext": dismissedContext],
            options: []
        )
        let response: NextUpEventDismissalResponseDTO = try await perform(request)
        return response.dismissal
    }

    func getOrCreateDirectConversation(recipientUserId: String) async throws -> DirectConversationDTO {
        var request = URLRequest(url: APIConfig.baseURL.appending(path: "/api/direct/conversations"))
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try encoder.encode(["recipientUserId": recipientUserId])
        let response: DirectConversationResponseDTO = try await perform(request)
        return response.conversation
    }

    func fetchDirectConversations() async throws -> [DirectConversationDTO] {
        let request = URLRequest(url: APIConfig.baseURL.appending(path: "/api/direct/conversations"))
        let response: DirectConversationsResponseDTO = try await perform(request)
        return response.conversations
    }

    func fetchDirectMessages(
        conversationId: String,
        limit: Int = 50,
        before: Date? = nil,
        after: Date? = nil
    ) async throws -> [DirectMessageDTO] {
        var components = URLComponents(
            url: APIConfig.baseURL.appending(path: "/api/direct/conversations/\(conversationId)/messages"),
            resolvingAgainstBaseURL: false
        )!
        var items = [URLQueryItem(name: "limit", value: String(limit))]
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        if let before {
            items.append(URLQueryItem(name: "before", value: formatter.string(from: before)))
        }
        if let after {
            items.append(URLQueryItem(name: "after", value: formatter.string(from: after)))
        }
        components.queryItems = items
        var request = URLRequest(url: components.url!)
        request.httpMethod = "GET"
        let response: DirectMessagesResponseDTO = try await perform(request)
        return response.messages
    }

    func sendDirectMessage(
        conversationId: String,
        body: String,
        clientMessageId: String = UUID().uuidString,
        eventId: String? = nil,
        replyToMessageId: String? = nil,
        photoJPEGData: Data? = nil,
        imageUrl: String? = nil
    ) async throws -> DirectMessageDTO {
        if let photoJPEGData {
            var fields: [String: String] = [
                "body": body,
                "clientMessageId": clientMessageId
            ]
            if let eventId {
                fields["eventId"] = eventId
            }
            if let replyToMessageId {
                fields["replyToMessageId"] = replyToMessageId
            }
            let boundary = "Boundary-\(UUID().uuidString)"
            var request = URLRequest(
                url: APIConfig.baseURL.appending(
                    path: "/api/direct/conversations/\(conversationId)/messages"
                )
            )
            request.httpMethod = "POST"
            request.setValue(
                "multipart/form-data; boundary=\(boundary)",
                forHTTPHeaderField: "Content-Type"
            )
            request.httpBody = buildMultipartFormBody(
                boundary: boundary,
                fields: fields,
                files: [
                    (
                        fieldName: "photo",
                        fileData: photoJPEGData,
                        fileName: "photo.jpg",
                        mimeType: "image/jpeg"
                    ),
                ]
            )
            let response: DirectMessageResponseDTO = try await perform(request)
            return response.message
        }

        var request = URLRequest(url: APIConfig.baseURL.appending(path: "/api/direct/conversations/\(conversationId)/messages"))
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        var payload: [String: String] = [
            "body": body,
            "clientMessageId": clientMessageId
        ]
        if let eventId {
            payload["eventId"] = eventId
        }
        if let replyToMessageId {
            payload["replyToMessageId"] = replyToMessageId
        }
        if let imageUrl, !imageUrl.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            payload["imageUrl"] = imageUrl.trimmingCharacters(in: .whitespacesAndNewlines)
        }
        request.httpBody = try encoder.encode(payload)
        let response: DirectMessageResponseDTO = try await perform(request)
        return response.message
    }

    func requestDirectChatVideoUploadURLs(
        conversationId: String,
        videoContentType: String
    ) async throws -> ChatVideoUploadURLsResponse {
        var request = URLRequest(
            url: APIConfig.baseURL.appending(
                path: "/api/direct/conversations/\(conversationId)/messages/upload-urls"
            )
        )
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONSerialization.data(withJSONObject: [
            "kind": "video",
            "videoContentType": videoContentType,
            "thumbnailContentType": "image/jpeg",
        ])
        return try await perform(request)
    }

    func sendDirectChatVideoMessage(
        conversationId: String,
        body: String,
        clientMessageId: String,
        replyToMessageId: String? = nil,
        videoAttachment: CircleChatMessageDTO.VideoAttachmentDTO
    ) async throws -> DirectMessageDTO {
        var request = URLRequest(
            url: APIConfig.baseURL.appending(path: "/api/direct/conversations/\(conversationId)/messages")
        )
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        var payload: [String: Any] = [
            "body": body,
            "clientMessageId": clientMessageId,
            "videoAttachment": [
                "videoUrl": videoAttachment.videoUrl,
                "thumbnailUrl": videoAttachment.thumbnailUrl,
                "durationSeconds": videoAttachment.durationSeconds,
                "width": videoAttachment.width,
                "height": videoAttachment.height,
                "mimeType": videoAttachment.mimeType,
            ],
        ]
        if let replyToMessageId {
            payload["replyToMessageId"] = replyToMessageId
        }
        request.httpBody = try JSONSerialization.data(withJSONObject: payload)
        let response: DirectMessageResponseDTO = try await perform(request)
        return response.message
    }

    func setDirectMessageReaction(
        conversationId: String,
        messageId: String,
        emoji: String
    ) async throws -> DirectMessageDTO {
        var request = URLRequest(
            url: APIConfig.baseURL.appending(
                path: "/api/direct/conversations/\(conversationId)/messages/\(messageId)/reactions"
            )
        )
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONSerialization.data(
            withJSONObject: ["emoji": emoji],
            options: []
        )
        let response: DirectMessageResponseDTO = try await perform(request)
        return response.message
    }

    func clearDirectMessageReaction(conversationId: String, messageId: String) async throws -> DirectMessageDTO {
        var request = URLRequest(
            url: APIConfig.baseURL.appending(
                path: "/api/direct/conversations/\(conversationId)/messages/\(messageId)/reactions"
            )
        )
        request.httpMethod = "DELETE"
        let response: DirectMessageResponseDTO = try await perform(request)
        return response.message
    }

    func patchDirectMessage(conversationId: String, messageId: String, body: String) async throws -> DirectMessageDTO {
        var request = URLRequest(
            url: APIConfig.baseURL.appending(
                path: "/api/direct/conversations/\(conversationId)/messages/\(messageId)"
            )
        )
        request.httpMethod = "PATCH"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONSerialization.data(withJSONObject: ["body": body], options: [])
        let response: DirectMessageResponseDTO = try await perform(request)
        return response.message
    }

    func deleteDirectMessage(conversationId: String, messageId: String) async throws -> DirectMessageDTO {
        var request = URLRequest(
            url: APIConfig.baseURL.appending(
                path: "/api/direct/conversations/\(conversationId)/messages/\(messageId)"
            )
        )
        request.httpMethod = "DELETE"
        let response: DirectMessageResponseDTO = try await perform(request)
        return response.message
    }

    /// Posts an event card to squad chat. Caption is optional; omit text by passing `body: ""`.
    func postCircleChatEventMessage(
        circleId: String,
        body: String = "",
        eventId: String,
        clientMessageId: String = UUID().uuidString
    ) async throws {
        let url = APIConfig.baseURL.appending(path: "/api/chat/circles/\(circleId)/messages")
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONSerialization.data(
            withJSONObject: [
                "body": body,
                "clientMessageId": clientMessageId,
                "eventId": eventId
            ],
            options: []
        )
        let (_, response) = try await performRaw(request)
        guard let http = response as? HTTPURLResponse, (200...299).contains(http.statusCode) else {
            throw URLError(.badServerResponse)
        }
    }

    /// Posts a completed drive card to squad chat. Caption is optional; omit text by passing `body: ""`.
    func postCircleChatDriveMessage(
        circleId: String,
        body: String = "",
        driveId: String,
        clientMessageId: String = UUID().uuidString,
        mapPreviewJPEGData: Data? = nil
    ) async throws {
        if let mapPreviewJPEGData {
            let boundary = "Boundary-\(UUID().uuidString)"
            var request = URLRequest(
                url: APIConfig.baseURL.appending(path: "/api/chat/circles/\(circleId)/messages")
            )
            request.httpMethod = "POST"
            request.setValue(
                "multipart/form-data; boundary=\(boundary)",
                forHTTPHeaderField: "Content-Type"
            )
            request.httpBody = buildMultipartFormBody(
                boundary: boundary,
                fields: [
                    "body": body,
                    "clientMessageId": clientMessageId,
                    "driveId": driveId,
                ],
                files: [
                    (
                        fieldName: "mapPreview",
                        fileData: mapPreviewJPEGData,
                        fileName: "map-preview.jpg",
                        mimeType: "image/jpeg"
                    ),
                ]
            )
            let (_, response) = try await performRaw(request)
            guard let http = response as? HTTPURLResponse, (200 ... 299).contains(http.statusCode) else {
                throw URLError(.badServerResponse)
            }
            return
        }

        let url = APIConfig.baseURL.appending(path: "/api/chat/circles/\(circleId)/messages")
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONSerialization.data(
            withJSONObject: [
                "body": body,
                "clientMessageId": clientMessageId,
                "driveId": driveId
            ],
            options: []
        )
        let (_, response) = try await performRaw(request)
        guard let http = response as? HTTPURLResponse, (200...299).contains(http.statusCode) else {
            throw URLError(.badServerResponse)
        }
    }

    /// Posts a place card to squad chat. Caption is optional; omit text by passing `body: ""`.
    func postCircleChatPlaceMessage(
        circleId: String,
        body: String = "",
        placeId: String? = nil,
        placeLatitude: Double? = nil,
        placeLongitude: Double? = nil,
        placeName: String? = nil,
        placeAddressSummary: String? = nil,
        clientMessageId: String = UUID().uuidString,
        mapPreviewJPEGData: Data? = nil
    ) async throws -> CircleChatMessageDTO {
        var fields: [String: String] = [
            "body": body,
            "clientMessageId": clientMessageId,
        ]
        if let placeId, !placeId.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            fields["placeId"] = placeId
        } else if let placeLatitude, let placeLongitude {
            fields["placeLatitude"] = String(placeLatitude)
            fields["placeLongitude"] = String(placeLongitude)
            if let placeName, !placeName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                fields["placeName"] = placeName
            }
            if let placeAddressSummary, !placeAddressSummary.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                fields["placeAddressSummary"] = placeAddressSummary
            }
        }

        let url = APIConfig.baseURL.appending(path: "/api/chat/circles/\(circleId)/messages")
        var request = URLRequest(url: url)
        request.httpMethod = "POST"

        if let mapPreviewJPEGData {
            let boundary = "Boundary-\(UUID().uuidString)"
            request.setValue(
                "multipart/form-data; boundary=\(boundary)",
                forHTTPHeaderField: "Content-Type"
            )
            request.httpBody = buildMultipartFormBody(
                boundary: boundary,
                fields: fields,
                files: [
                    (
                        fieldName: "mapPreview",
                        fileData: mapPreviewJPEGData,
                        fileName: "map-preview.jpg",
                        mimeType: "image/jpeg"
                    ),
                ]
            )
        } else {
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            var json: [String: Any] = [
                "body": body,
                "clientMessageId": clientMessageId,
            ]
            if let placeId, !placeId.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                json["placeId"] = placeId
            } else if let placeLatitude, let placeLongitude {
                json["placeLatitude"] = placeLatitude
                json["placeLongitude"] = placeLongitude
                if let placeName, !placeName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                    json["placeName"] = placeName
                }
                if let placeAddressSummary, !placeAddressSummary.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                    json["placeAddressSummary"] = placeAddressSummary
                }
            }
            request.httpBody = try JSONSerialization.data(withJSONObject: json, options: [])
        }

        let (data, response) = try await performRaw(request)
        guard let http = response as? HTTPURLResponse, (200 ... 299).contains(http.statusCode) else {
            throw URLError(.badServerResponse)
        }
        struct Wrapper: Decodable { let message: CircleChatMessageDTO }
        return try decoder.decode(Wrapper.self, from: data).message
    }

    /// Posts a place card to a direct conversation. Caption is optional.
    func postDirectChatPlaceMessage(
        conversationId: String,
        body: String = "",
        placeId: String? = nil,
        placeLatitude: Double? = nil,
        placeLongitude: Double? = nil,
        placeName: String? = nil,
        placeAddressSummary: String? = nil,
        clientMessageId: String = UUID().uuidString,
        mapPreviewJPEGData: Data? = nil
    ) async throws -> DirectMessageDTO {
        var fields: [String: String] = [
            "body": body,
            "clientMessageId": clientMessageId,
        ]
        if let placeId, !placeId.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            fields["placeId"] = placeId
        } else if let placeLatitude, let placeLongitude {
            fields["placeLatitude"] = String(placeLatitude)
            fields["placeLongitude"] = String(placeLongitude)
            if let placeName, !placeName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                fields["placeName"] = placeName
            }
            if let placeAddressSummary, !placeAddressSummary.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                fields["placeAddressSummary"] = placeAddressSummary
            }
        }

        let url = APIConfig.baseURL.appending(path: "/api/direct/conversations/\(conversationId)/messages")
        var request = URLRequest(url: url)
        request.httpMethod = "POST"

        if let mapPreviewJPEGData {
            let boundary = "Boundary-\(UUID().uuidString)"
            request.setValue(
                "multipart/form-data; boundary=\(boundary)",
                forHTTPHeaderField: "Content-Type"
            )
            request.httpBody = buildMultipartFormBody(
                boundary: boundary,
                fields: fields,
                files: [
                    (
                        fieldName: "mapPreview",
                        fileData: mapPreviewJPEGData,
                        fileName: "map-preview.jpg",
                        mimeType: "image/jpeg"
                    ),
                ]
            )
        } else {
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            var json: [String: Any] = [
                "body": body,
                "clientMessageId": clientMessageId,
            ]
            if let placeId, !placeId.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                json["placeId"] = placeId
            } else if let placeLatitude, let placeLongitude {
                json["placeLatitude"] = placeLatitude
                json["placeLongitude"] = placeLongitude
                if let placeName, !placeName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                    json["placeName"] = placeName
                }
                if let placeAddressSummary, !placeAddressSummary.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                    json["placeAddressSummary"] = placeAddressSummary
                }
            }
            request.httpBody = try JSONSerialization.data(withJSONObject: json, options: [])
        }

        let (data, response) = try await performRaw(request)
        guard let http = response as? HTTPURLResponse, (200 ... 299).contains(http.statusCode) else {
            throw URLError(.badServerResponse)
        }
        struct Wrapper: Decodable { let message: DirectMessageDTO }
        return try decoder.decode(Wrapper.self, from: data).message
    }

    func inviteByPhone(circleId: String, phoneNumber: String) async throws {
        var request = URLRequest(url: APIConfig.baseURL.appending(path: "/api/circles/\(circleId)/invites"))
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try encoder.encode(["phoneNumber": phoneNumber])
        let (data, response) = try await performRaw(request)
        guard let http = response as? HTTPURLResponse, (200...299).contains(http.statusCode) else {
            throw Self.apiError(from: data, statusCode: (response as? HTTPURLResponse)?.statusCode ?? -1)
        }
    }

    func lookupUserByPhone(phoneNumber: String) async throws -> UserDTO? {
        var components = URLComponents(
            url: APIConfig.baseURL.appending(path: "/api/users/lookup/by-phone"),
            resolvingAgainstBaseURL: false
        )!
        components.queryItems = [URLQueryItem(name: "phoneNumber", value: phoneNumber)]
        var request = URLRequest(url: components.url!)
        request.httpMethod = "GET"
        let response: [String: AnyDecodable] = try await perform(request)
        let found = response["found"]?.boolValue ?? false
        guard found, let userValue = response["user"]?.rawValue else { return nil }
        let data = try JSONSerialization.data(withJSONObject: userValue, options: [])
        return try decoder.decode(UserDTO.self, from: data)
    }

    func fetchMyCircleInvites() async throws -> [CircleInviteDTO] {
        var request = URLRequest(url: APIConfig.baseURL.appending(path: "/api/circle-invites/me"))
        request.httpMethod = "GET"
        return try await perform(request)
    }

    func respondToCircleInvite(inviteID: String, action: String) async throws {
        var request = URLRequest(url: APIConfig.baseURL.appending(path: "/api/circle-invites/\(inviteID)/respond"))
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONSerialization.data(withJSONObject: ["action": action], options: [])
        _ = try await performRaw(request)
    }

    func fetchPresence(circleId: String) async throws -> PresenceCircleResponseDTO {
        var request = URLRequest(url: APIConfig.baseURL.appending(path: "/api/presence/circle/\(circleId)"))
        request.httpMethod = "GET"
        return try await perform(request)
    }

    func updatePresence(payload: [String: Any]) async throws {
        var request = URLRequest(url: APIConfig.baseURL.appending(path: "/api/presence"))
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONSerialization.data(withJSONObject: payload, options: [])
        _ = try await performRaw(request)
    }

    func fetchGarageCars(userId: String) async throws -> [GarageCarDTO] {
        var request = URLRequest(url: APIConfig.baseURL.appending(path: "/api/garage/\(userId)/cars"))
        request.httpMethod = "GET"
        return try await perform(request)
    }

    func createGarageCar(
        userId: String,
        nickname: String,
        make: String,
        makeId: String?,
        model: String,
        year: Int?,
        color: String?,
        logoSlug: String?
    ) async throws -> GarageCarDTO {
        var request = URLRequest(url: APIConfig.baseURL.appending(path: "/api/garage/\(userId)/cars"))
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")

        var payload: [String: Any] = [
            "nickname": nickname,
            "make": make,
            "model": model,
        ]
        if let makeId, !makeId.isEmpty { payload["makeId"] = makeId }
        if let year { payload["year"] = year }
        if let color, !color.isEmpty { payload["color"] = color }
        if let logoSlug, !logoSlug.isEmpty { payload["logoSlug"] = logoSlug }

        request.httpBody = try JSONSerialization.data(withJSONObject: payload, options: [])
        return try await perform(request)
    }

    func updateGarageCar(
        userId: String,
        carId: String,
        nickname: String,
        make: String,
        makeId: String?,
        model: String,
        year: Int?,
        color: String?,
        logoSlug: String?,
        isPrimary: Bool
    ) async throws -> GarageCarDTO {
        var request = URLRequest(url: APIConfig.baseURL.appending(path: "/api/garage/\(userId)/cars/\(carId)"))
        request.httpMethod = "PATCH"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")

        var payload: [String: Any] = [
            "nickname": nickname,
            "make": make,
            "model": model,
            "isPrimary": isPrimary,
            "color": color ?? "",
        ]
        if let makeId, !makeId.isEmpty { payload["makeId"] = makeId }
        if let year { payload["year"] = year }
        if let logoSlug, !logoSlug.isEmpty { payload["logoSlug"] = logoSlug }

        request.httpBody = try JSONSerialization.data(withJSONObject: payload, options: [])
        return try await perform(request)
    }

    /// Multipart field name must be `photo` (JPEG data).
    func uploadGarageCarPhoto(userId: String, carId: String, imageData: Data) async throws -> GarageCarDTO {
        let boundary = "Boundary-\(UUID().uuidString)"
        var request = URLRequest(url: APIConfig.baseURL.appending(path: "/api/garage/\(userId)/cars/\(carId)/photo"))
        request.httpMethod = "POST"
        request.setValue("multipart/form-data; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")

        var body = Data()
        let crlf = "\r\n"
        body.append("--\(boundary)\(crlf)".data(using: .utf8)!)
        body.append(
            "Content-Disposition: form-data; name=\"photo\"; filename=\"garage-car.jpg\"\(crlf)".data(using: .utf8)!
        )
        body.append("Content-Type: image/jpeg\(crlf)\(crlf)".data(using: .utf8)!)
        body.append(imageData)
        body.append("\(crlf)--\(boundary)--\(crlf)".data(using: .utf8)!)
        request.httpBody = body

        return try await perform(request)
    }

    func deleteGarageCar(userId: String, carId: String) async throws {
        var request = URLRequest(url: APIConfig.baseURL.appending(path: "/api/garage/\(userId)/cars/\(carId)"))
        request.httpMethod = "DELETE"
        _ = try await performRaw(request)
    }

    func reorderGarageCars(userId: String, orderedCarIds: [String]) async throws {
        var request = URLRequest(url: APIConfig.baseURL.appending(path: "/api/garage/\(userId)/cars/reorder"))
        request.httpMethod = "PUT"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONSerialization.data(
            withJSONObject: ["orderedCarIds": orderedCarIds],
            options: []
        )
        _ = try await performRaw(request)
    }

    func startDrive(
        userId: String,
        circleId: String?,
        sharingAudience: String?,
        sharedCircleIds: [String],
        title: String?,
        location: (lat: Double, lng: Double)?
    ) async throws -> DriveDTO {
        var request = URLRequest(url: APIConfig.baseURL.appending(path: "/api/drives/start"))
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")

        var payload: [String: Any] = [
            "userId": userId,
            "startTime": ISO8601DateFormatter().string(from: Date()),
        ]
        if let circleId, !circleId.isEmpty { payload["circleId"] = circleId }
        if let sharingAudience, !sharingAudience.isEmpty { payload["sharingAudience"] = sharingAudience }
        if !sharedCircleIds.isEmpty { payload["sharedCircleIds"] = sharedCircleIds }
        if let title, !title.isEmpty { payload["title"] = title }
        if let location {
            payload["startLocation"] = ["lat": location.lat, "lng": location.lng]
        }

        request.httpBody = try JSONSerialization.data(withJSONObject: payload, options: [])
        return try await perform(request)
    }

    func appendDrivePoint(
        driveId: String,
        lat: Double,
        lng: Double,
        speedMph: Double,
        heading: Double?,
        accuracyMeters: Double?
    ) async throws {
        var request = URLRequest(url: APIConfig.baseURL.appending(path: "/api/drives/\(driveId)/points"))
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")

        var point: [String: Any] = [
            "lat": lat,
            "lng": lng,
            "speedMph": speedMph,
            "capturedAt": ISO8601DateFormatter().string(from: Date()),
        ]
        if let heading { point["heading"] = heading }
        if let accuracyMeters { point["accuracyMeters"] = accuracyMeters }
        request.httpBody = try JSONSerialization.data(withJSONObject: ["points": [point]], options: [])
        _ = try await performRaw(request)
    }

    func appendDrivePathSamples(driveId: String, samples: [DrivePathSample]) async throws {
        guard !samples.isEmpty else { return }
        let chunkSize = 50
        var index = 0
        while index < samples.count {
            let chunk = samples[index ..< min(index + chunkSize, samples.count)]
            var request = URLRequest(url: APIConfig.baseURL.appending(path: "/api/drives/\(driveId)/points"))
            request.httpMethod = "POST"
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")

            let points: [[String: Any]] = chunk.map { sample in
                var point: [String: Any] = [
                    "lat": sample.coordinate.latitude,
                    "lng": sample.coordinate.longitude,
                    "speedMph": sample.speedMph,
                ]
                if let capturedAt = sample.capturedAt {
                    point["capturedAt"] = ISO8601DateFormatter().string(from: capturedAt)
                }
                return point
            }
            request.httpBody = try JSONSerialization.data(withJSONObject: ["points": points], options: [])
            _ = try await performRaw(request)
            index += chunkSize
        }
    }

    func endDrive(
        driveId: String,
        location: (lat: Double, lng: Double)?,
        distanceMeters: Double? = nil,
        maxSpeedMph: Double? = nil,
        avgSpeedMph: Double? = nil
    ) async throws {
        var request = URLRequest(url: APIConfig.baseURL.appending(path: "/api/drives/\(driveId)/end"))
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")

        var payload: [String: Any] = [
            "endTime": ISO8601DateFormatter().string(from: Date()),
        ]
        if let location {
            payload["endLocation"] = ["lat": location.lat, "lng": location.lng]
        }
        if let distanceMeters, distanceMeters >= 0 {
            payload["distanceMeters"] = distanceMeters
        }
        if let maxSpeedMph, maxSpeedMph >= 0 {
            payload["maxSpeedMph"] = maxSpeedMph
        }
        if let avgSpeedMph, avgSpeedMph >= 0 {
            payload["avgSpeedMph"] = avgSpeedMph
        }

        request.httpBody = try JSONSerialization.data(withJSONObject: payload, options: [])
        _ = try await performRaw(request)
    }

    func fetchUserDrives(userId: String) async throws -> [DriveDTO] {
        var request = URLRequest(url: APIConfig.baseURL.appending(path: "/api/drives/user/\(userId)"))
        request.httpMethod = "GET"
        return try await perform(request)
    }

    func fetchDrive(driveId: String, circleId: String? = nil) async throws -> DriveDTO {
        var components = URLComponents(url: APIConfig.baseURL.appending(path: "/api/drives/\(driveId)"), resolvingAgainstBaseURL: false)!
        if let circleId, !circleId.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            components.queryItems = [URLQueryItem(name: "circleId", value: circleId)]
        }
        var request = URLRequest(url: components.url!)
        request.httpMethod = "GET"
        return try await perform(request)
    }

    func fetchDrivePoints(driveId: String, circleId: String? = nil, limit: Int = 1200) async throws -> [DrivePathPointDTO] {
        var components = URLComponents(
            url: APIConfig.baseURL.appending(path: "/api/drives/\(driveId)/points"),
            resolvingAgainstBaseURL: false
        )!
        var queryItems = [URLQueryItem(name: "limit", value: String(limit))]
        if let circleId, !circleId.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            queryItems.append(URLQueryItem(name: "circleId", value: circleId))
        }
        components.queryItems = queryItems
        var request = URLRequest(url: components.url!)
        request.httpMethod = "GET"
        let response: DrivePathPointsResponseDTO = try await perform(request)
        return response.points
    }

    func updateDriveGarageCar(driveId: String, garageCarId: String?) async throws -> DriveDTO {
        try await patchDrive(driveId: driveId, garageCarId: garageCarId, title: nil)
    }

    func updateDriveTitle(driveId: String, title: String) async throws -> DriveDTO {
        try await patchDrive(driveId: driveId, garageCarId: nil, title: title)
    }

    func deleteDrive(driveId: String) async throws {
        var request = URLRequest(url: APIConfig.baseURL.appending(path: "/api/drives/\(driveId)"))
        request.httpMethod = "DELETE"
        let (_, response) = try await performRaw(request)
        guard let http = response as? HTTPURLResponse, (200...299).contains(http.statusCode) else {
            throw URLError(.badServerResponse)
        }
    }

    private func patchDrive(driveId: String, garageCarId: String?, title: String?) async throws -> DriveDTO {
        var request = URLRequest(url: APIConfig.baseURL.appending(path: "/api/drives/\(driveId)"))
        request.httpMethod = "PATCH"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        var payload: [String: Any] = [:]
        if let garageCarId {
            let trimmed = garageCarId.trimmingCharacters(in: .whitespacesAndNewlines)
            payload["garageCarId"] = trimmed.isEmpty ? NSNull() : trimmed
        }
        if let title {
            let trimmed = title.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !trimmed.isEmpty else { throw URLError(.badURL) }
            payload["title"] = trimmed
        }
        request.httpBody = try JSONSerialization.data(withJSONObject: payload, options: [])
        return try await perform(request)
    }

    func fetchDrivingStats(userId: String) async throws -> DrivingStatsDTO {
        var request = URLRequest(url: APIConfig.baseURL.appending(path: "/api/drives/user/\(userId)/stats"))
        request.httpMethod = "GET"
        return try await perform(request)
    }

    func fetchEvents(
        scope: String = "all",
        limit: Int = 100,
        visibility: String? = nil,
        eventType: String? = nil,
        circleId: String? = nil
    ) async throws -> [EventDTO] {
        var components = URLComponents(
            url: APIConfig.baseURL.appending(path: "/api/events"),
            resolvingAgainstBaseURL: false
        )!
        var queryItems = [
            URLQueryItem(name: "scope", value: scope),
            URLQueryItem(name: "limit", value: String(limit)),
        ]
        if let visibility {
            queryItems.append(URLQueryItem(name: "visibility", value: visibility))
        }
        if let eventType {
            queryItems.append(URLQueryItem(name: "eventType", value: eventType))
        }
        if let circleId {
            queryItems.append(URLQueryItem(name: "circleId", value: circleId))
        }
        components.queryItems = queryItems
        var request = URLRequest(url: components.url!)
        request.httpMethod = "GET"
        return try await perform(request)
    }

    func fetchAdminSquads() async throws -> [AdminSquadDTO] {
        var request = URLRequest(url: APIConfig.baseURL.appending(path: "/api/me/admin-squads"))
        request.httpMethod = "GET"
        let response: AdminSquadsResponseDTO = try await perform(request)
        return response.squads
    }

    func fetchEventSquadAssociations(eventId: String) async throws -> [EventAttachedSquadDTO] {
        var request = URLRequest(
            url: APIConfig.baseURL.appending(path: "/api/events/\(eventId)/squad-associations")
        )
        request.httpMethod = "GET"
        let response: SquadAssociationsResponseDTO = try await perform(request)
        return response.squads
    }

    func patchEventSquadAssociations(eventId: String, squadIds: [String]) async throws -> [EventAttachedSquadDTO] {
        var request = URLRequest(
            url: APIConfig.baseURL.appending(path: "/api/events/\(eventId)/squad-associations")
        )
        request.httpMethod = "PATCH"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONSerialization.data(withJSONObject: ["squadIds": squadIds], options: [])
        let response: SquadAssociationsResponseDTO = try await perform(request)
        return response.squads
    }

    func createEvent(
        name: String,
        description: String?,
        startsAt: Date,
        endsAt: Date?,
        addressLabel: String?,
        streetAddress: String?,
        city: String?,
        region: String?,
        postalCode: String?,
        location: (latitude: Double, longitude: Double)?,
        visibility: String,
        eventType: String? = nil,
        circleId: String?
    ) async throws -> EventDTO {
        var request = URLRequest(url: APIConfig.baseURL.appending(path: "/api/events"))
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")

        var payload: [String: Any] = [
            "name": name,
            "startsAt": iso8601APIString(from: startsAt),
            "visibility": visibility,
        ]
        if let endsAt {
            payload["endsAt"] = iso8601APIString(from: endsAt)
        }
        if let description, !description.isEmpty {
            payload["description"] = description
        }
        var address: [String: String] = [:]
        if let addressLabel, !addressLabel.isEmpty {
            address["label"] = addressLabel
        }
        if let streetAddress, !streetAddress.isEmpty {
            address["street1"] = streetAddress
        }
        if let city, !city.isEmpty {
            address["city"] = city
        }
        if let region, !region.isEmpty {
            address["region"] = region
        }
        if let postalCode, !postalCode.isEmpty {
            address["postalCode"] = postalCode
        }
        if !address.isEmpty {
            payload["address"] = address
        }
        if let location {
            payload["location"] = ["lat": location.latitude, "lng": location.longitude]
        }
        if let eventType, !eventType.isEmpty {
            payload["eventType"] = eventType
        }
        if let circleId, !circleId.isEmpty {
            payload["circleId"] = circleId
        }
        request.httpBody = try JSONSerialization.data(withJSONObject: payload, options: [])
        return try await perform(request)
    }

    func fetchEvent(eventRef: String) async throws -> EventDTO {
        var request = URLRequest(url: APIConfig.baseURL.appending(path: "/api/events/\(eventRef)"))
        request.httpMethod = "GET"
        return try await perform(request)
    }

    func updateEvent(
        eventId: String,
        name: String,
        description: String?,
        startsAt: Date,
        endsAt: Date?,
        addressLabel: String?,
        streetAddress: String?,
        city: String?,
        region: String?,
        postalCode: String?,
        location: (latitude: Double, longitude: Double)?
    ) async throws -> EventDTO {
        var request = URLRequest(url: APIConfig.baseURL.appending(path: "/api/events/\(eventId)"))
        request.httpMethod = "PATCH"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        var payload: [String: Any] = [
            "name": name,
            "startsAt": iso8601APIString(from: startsAt),
        ]
        if let endsAt {
            payload["endsAt"] = iso8601APIString(from: endsAt)
        }
        if let description, !description.isEmpty {
            payload["description"] = description
        }
        var address: [String: String] = [:]
        if let addressLabel, !addressLabel.isEmpty {
            address["label"] = addressLabel
        }
        if let streetAddress, !streetAddress.isEmpty {
            address["street1"] = streetAddress
        }
        if let city, !city.isEmpty {
            address["city"] = city
        }
        if let region, !region.isEmpty {
            address["region"] = region
        }
        if let postalCode, !postalCode.isEmpty {
            address["postalCode"] = postalCode
        }
        if !address.isEmpty {
            payload["address"] = address
        }
        if let location {
            payload["location"] = ["lat": location.latitude, "lng": location.longitude]
        }
        request.httpBody = try JSONSerialization.data(withJSONObject: payload, options: [])
        return try await perform(request)
    }

    func deleteEvent(eventId: String) async throws {
        var request = URLRequest(url: APIConfig.baseURL.appending(path: "/api/events/\(eventId)"))
        request.httpMethod = "DELETE"
        _ = try await performRaw(request)
    }

    func uploadEventBanner(eventId: String, imageData: Data) async throws -> EventDTO {
        let boundary = "Boundary-\(UUID().uuidString)"
        var request = URLRequest(url: APIConfig.baseURL.appending(path: "/api/events/\(eventId)/banner"))
        request.httpMethod = "POST"
        request.setValue("multipart/form-data; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")
        var body = Data()
        body.append("--\(boundary)\r\n".data(using: .utf8)!)
        body.append("Content-Disposition: form-data; name=\"aspectRatio\"\r\n\r\n".data(using: .utf8)!)
        body.append("\(16.0 / 9.0)\r\n".data(using: .utf8)!)
        body.append("--\(boundary)\r\n".data(using: .utf8)!)
        body.append("Content-Disposition: form-data; name=\"photo\"; filename=\"event-banner.jpg\"\r\n".data(using: .utf8)!)
        body.append("Content-Type: image/jpeg\r\n\r\n".data(using: .utf8)!)
        body.append(imageData)
        body.append("\r\n--\(boundary)--\r\n".data(using: .utf8)!)
        request.httpBody = body
        return try await perform(request)
    }

    func updateEventRsvp(eventId: String, status: String) async throws -> EventDTO {
        var request = URLRequest(url: APIConfig.baseURL.appending(path: "/api/events/\(eventId)/rsvp"))
        request.httpMethod = "PUT"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try encoder.encode(["status": status])
        return try await perform(request)
    }

    func postEventCheckIn(
        eventId: String,
        method: String,
        latitude: Double? = nil,
        longitude: Double? = nil,
        distanceMeters: Double? = nil
    ) async throws -> EventCheckInResultDTO {
        var request = URLRequest(url: APIConfig.baseURL.appending(path: "/api/events/\(eventId)/check-ins"))
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        var payload: [String: Any] = ["method": method]
        if let latitude { payload["latitude"] = latitude }
        if let longitude { payload["longitude"] = longitude }
        if let distanceMeters { payload["distanceMeters"] = distanceMeters }
        request.httpBody = try JSONSerialization.data(withJSONObject: payload, options: [])
        return try await perform(request)
    }

    func updateUserAutoEventCheckIn(userId: String, enabled: Bool) async throws {
        var request = URLRequest(url: APIConfig.baseURL.appending(path: "/api/users/\(userId)"))
        request.httpMethod = "PATCH"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONSerialization.data(withJSONObject: ["autoEventCheckInEnabled": enabled], options: [])
        _ = try await performRaw(request)
    }

    func updateUserSharingSafetyDisclaimerAcknowledged(userId: String, acknowledged: Bool) async throws {
        var request = URLRequest(url: APIConfig.baseURL.appending(path: "/api/users/\(userId)"))
        request.httpMethod = "PATCH"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONSerialization.data(
            withJSONObject: ["sharingSafetyDisclaimerAcknowledged": acknowledged],
            options: []
        )
        _ = try await performRaw(request)
    }

    /// Public profile payload includes `publicGoingEvents` (no auth).
    func fetchPublicProfileGoingEvents(userId: String) async throws -> [PublicGoingEventDTO] {
        var request = URLRequest(url: APIConfig.baseURL.appending(path: "/api/public/m/\(userId)"))
        request.httpMethod = "GET"
        let decoded: PublicMemberProfileGoingEventsResponse = try await perform(request)
        return decoded.publicGoingEvents ?? []
    }

    func updateUserShowPublicGoingEventsOnProfile(userId: String, enabled: Bool) async throws {
        var request = URLRequest(url: APIConfig.baseURL.appending(path: "/api/users/\(userId)"))
        request.httpMethod = "PATCH"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONSerialization.data(
            withJSONObject: ["showPublicGoingEventsOnProfile": enabled],
            options: []
        )
        _ = try await performRaw(request)
    }

    func updateUserDriveStatsVisibility(userId: String, visibility: DriveStatsVisibilitySetting) async throws {
        var request = URLRequest(url: APIConfig.baseURL.appending(path: "/api/users/\(userId)"))
        request.httpMethod = "PATCH"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONSerialization.data(
            withJSONObject: ["driveStatsVisibility": visibility.rawValue],
            options: []
        )
        _ = try await performRaw(request)
    }

    func createDriveLine(
        circleId: String,
        name: String,
        colorKey: String,
        points: [DriveLineCoordinateDTO],
        roadCoordinates: [DriveLineCoordinateDTO],
        distanceMeters: Double,
        etaSeconds: Double
    ) async throws -> DriveLineDTO {
        var request = URLRequest(url: APIConfig.baseURL.appending(path: "/api/drive-lines"))
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONSerialization.data(withJSONObject: [
            "circleId": circleId,
            "name": name,
            "colorKey": colorKey,
            "points": points.map { ["lat": $0.lat, "lng": $0.lng, "type": $0.markerType ?? "waypoint"] },
            "roadCoordinates": roadCoordinates.map { ["lat": $0.lat, "lng": $0.lng] },
            "distanceMeters": distanceMeters,
            "etaSeconds": etaSeconds
        ], options: [])
        return try await perform(request)
    }

    /// Legacy squad route overlays; map clients skip fetching pending product refactor.
    func fetchDriveLines(circleId: String, limit: Int = 20) async throws -> [DriveLineDTO] {
        var components = URLComponents(
            url: APIConfig.baseURL.appending(path: "/api/drive-lines/circle/\(circleId)"),
            resolvingAgainstBaseURL: false
        )!
        components.queryItems = [URLQueryItem(name: "limit", value: String(limit))]
        var request = URLRequest(url: components.url!)
        request.httpMethod = "GET"
        return try await perform(request)
    }

    func updateDriveLine(
        driveLineId: String,
        name: String,
        colorKey: String,
        points: [DriveLineCoordinateDTO],
        roadCoordinates: [DriveLineCoordinateDTO],
        distanceMeters: Double,
        etaSeconds: Double
    ) async throws -> DriveLineDTO {
        var request = URLRequest(url: APIConfig.baseURL.appending(path: "/api/drive-lines/\(driveLineId)"))
        request.httpMethod = "PATCH"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONSerialization.data(withJSONObject: [
            "name": name,
            "colorKey": colorKey,
            "points": points.map { ["lat": $0.lat, "lng": $0.lng, "type": $0.markerType ?? "waypoint"] },
            "roadCoordinates": roadCoordinates.map { ["lat": $0.lat, "lng": $0.lng] },
            "distanceMeters": distanceMeters,
            "etaSeconds": etaSeconds
        ], options: [])
        return try await perform(request)
    }

    func fetchRoutes(limit: Int = 50) async throws -> [SavedRouteDTO] {
        var components = URLComponents(
            url: APIConfig.baseURL.appending(path: "/api/routes"),
            resolvingAgainstBaseURL: false
        )!
        components.queryItems = [URLQueryItem(name: "limit", value: String(limit))]
        var request = URLRequest(url: components.url!)
        request.httpMethod = "GET"
        return try await perform(request)
    }

    func createRoute(
        name: String,
        points: [RoutePointDTO],
        roadCoordinates: [RoutePointDTO],
        distanceMeters: Double,
        etaSeconds: Double
    ) async throws -> SavedRouteDTO {
        var request = URLRequest(url: APIConfig.baseURL.appending(path: "/api/routes"))
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try routeRequestBody(
            name: name,
            points: points,
            roadCoordinates: roadCoordinates,
            distanceMeters: distanceMeters,
            etaSeconds: etaSeconds
        )
        return try await perform(request)
    }

    func updateRoute(
        routeId: String,
        name: String,
        points: [RoutePointDTO],
        roadCoordinates: [RoutePointDTO],
        distanceMeters: Double,
        etaSeconds: Double
    ) async throws -> SavedRouteDTO {
        var request = URLRequest(url: APIConfig.baseURL.appending(path: "/api/routes/\(routeId)"))
        request.httpMethod = "PATCH"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try routeRequestBody(
            name: name,
            points: points,
            roadCoordinates: roadCoordinates,
            distanceMeters: distanceMeters,
            etaSeconds: etaSeconds
        )
        return try await perform(request)
    }

    func deleteRoute(routeId: String) async throws {
        var request = URLRequest(url: APIConfig.baseURL.appending(path: "/api/routes/\(routeId)"))
        request.httpMethod = "DELETE"
        let (_, response) = try await performRaw(request)
        guard let http = response as? HTTPURLResponse, (200...299).contains(http.statusCode) else {
            throw URLError(.badServerResponse)
        }
    }

    func startRouteDriveSession(routeId: String) async throws -> RouteDriveSessionDTO {
        var request = URLRequest(url: APIConfig.baseURL.appending(path: "/api/routes/\(routeId)/sessions/start"))
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONSerialization.data(withJSONObject: [:], options: [])
        return try await perform(request)
    }

    func activateRouteDriveSession(
        sessionId: String,
        location: CLLocation,
        speedMph: Double,
        garageCarId: String? = nil
    ) async throws -> RouteDriveSessionDTO {
        var request = URLRequest(url: APIConfig.baseURL.appending(path: "/api/routes/sessions/\(sessionId)/activate"))
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try routeDriveSessionBody(
            location: location,
            speedMph: speedMph,
            completedWaypointIndexes: nil,
            currentProgress: nil,
            lastTriggeredWaypointIndex: nil,
            nearestRouteIndex: nil,
            garageCarId: garageCarId
        )
        return try await perform(request)
    }

    func updateRouteDriveSessionProgress(
        sessionId: String,
        location: CLLocation,
        speedMph: Double,
        completedWaypointIndexes: [Int],
        currentProgress: Double,
        lastTriggeredWaypointIndex: Int?,
        nearestRouteIndex: Int?
    ) async throws -> RouteDriveSessionDTO {
        var request = URLRequest(url: APIConfig.baseURL.appending(path: "/api/routes/sessions/\(sessionId)/progress"))
        request.httpMethod = "PATCH"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try routeDriveSessionBody(
            location: location,
            speedMph: speedMph,
            completedWaypointIndexes: completedWaypointIndexes,
            currentProgress: currentProgress,
            lastTriggeredWaypointIndex: lastTriggeredWaypointIndex,
            nearestRouteIndex: nearestRouteIndex
        )
        return try await perform(request)
    }

    func completeRouteDriveSession(
        sessionId: String,
        location: CLLocation?,
        speedMph: Double,
        completedWaypointIndexes: [Int],
        currentProgress: Double,
        lastTriggeredWaypointIndex: Int?
    ) async throws -> RouteDriveSessionDTO {
        var request = URLRequest(url: APIConfig.baseURL.appending(path: "/api/routes/sessions/\(sessionId)/complete"))
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try routeDriveSessionBody(
            location: location,
            speedMph: speedMph,
            completedWaypointIndexes: completedWaypointIndexes,
            currentProgress: currentProgress,
            lastTriggeredWaypointIndex: lastTriggeredWaypointIndex,
            nearestRouteIndex: nil
        )
        return try await perform(request)
    }

    func stopRouteDriveSession(
        sessionId: String,
        location: CLLocation?,
        speedMph: Double,
        completedWaypointIndexes: [Int],
        currentProgress: Double,
        lastTriggeredWaypointIndex: Int?
    ) async throws -> RouteDriveSessionDTO {
        var request = URLRequest(url: APIConfig.baseURL.appending(path: "/api/routes/sessions/\(sessionId)/stop"))
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try routeDriveSessionBody(
            location: location,
            speedMph: speedMph,
            completedWaypointIndexes: completedWaypointIndexes,
            currentProgress: currentProgress,
            lastTriggeredWaypointIndex: lastTriggeredWaypointIndex,
            nearestRouteIndex: nil
        )
        return try await perform(request)
    }

    private func routeRequestBody(
        name: String,
        points: [RoutePointDTO],
        roadCoordinates: [RoutePointDTO],
        distanceMeters: Double,
        etaSeconds: Double
    ) throws -> Data {
        try JSONSerialization.data(withJSONObject: [
            "name": name,
            "points": points.map { ["lat": $0.lat, "lng": $0.lng, "type": $0.markerType ?? "path"] },
            "roadCoordinates": roadCoordinates.map { ["lat": $0.lat, "lng": $0.lng, "type": $0.markerType ?? "path"] },
            "distanceMeters": distanceMeters,
            "etaSeconds": etaSeconds,
        ], options: [])
    }

    private func routeDriveSessionBody(
        location: CLLocation?,
        speedMph: Double,
        completedWaypointIndexes: [Int]?,
        currentProgress: Double?,
        lastTriggeredWaypointIndex: Int?,
        nearestRouteIndex: Int?,
        garageCarId: String? = nil
    ) throws -> Data {
        var payload: [String: Any] = [:]
        if let location {
            var locationPayload: [String: Any] = [
                "lat": location.coordinate.latitude,
                "lng": location.coordinate.longitude,
                "speedMph": max(0, speedMph),
                "capturedAt": iso8601APIString(from: location.timestamp)
            ]
            if location.course >= 0 {
                locationPayload["heading"] = location.course
            }
            if location.horizontalAccuracy >= 0 {
                locationPayload["accuracyMeters"] = location.horizontalAccuracy
            }
            payload["location"] = locationPayload
        }
        if let completedWaypointIndexes {
            payload["completedWaypointIndexes"] = completedWaypointIndexes
        }
        if let currentProgress {
            payload["currentProgress"] = currentProgress
        }
        if let lastTriggeredWaypointIndex {
            payload["lastTriggeredWaypointIndex"] = lastTriggeredWaypointIndex
        }
        if let nearestRouteIndex {
            payload["nearestRouteIndex"] = nearestRouteIndex
        }
        if let garageCarId = garageCarId?.trimmingCharacters(in: .whitespacesAndNewlines), !garageCarId.isEmpty {
            payload["garageCarId"] = garageCarId
        }
        return try JSONSerialization.data(withJSONObject: payload, options: [])
    }

    private func buildMultipartFormBody(
        boundary: String,
        fields: [String: String],
        files: [(fieldName: String, fileData: Data, fileName: String, mimeType: String)]
    ) -> Data {
        var data = Data()
        let crlf = "\r\n"
        for (key, value) in fields {
            data.append(Data("--\(boundary)\(crlf)".utf8))
            data.append(Data("Content-Disposition: form-data; name=\"\(key)\"\(crlf)\(crlf)".utf8))
            data.append(Data(value.utf8))
            data.append(Data(crlf.utf8))
        }
        for file in files {
            data.append(Data("--\(boundary)\(crlf)".utf8))
            data.append(
                Data(
                    "Content-Disposition: form-data; name=\"\(file.fieldName)\"; filename=\"\(file.fileName)\"\(crlf)"
                        .utf8
                )
            )
            data.append(Data("Content-Type: \(file.mimeType)\(crlf)\(crlf)".utf8))
            data.append(file.fileData)
            data.append(Data(crlf.utf8))
        }
        data.append(Data("--\(boundary)--\(crlf)".utf8))
        return data
    }

    private static func apiError(from data: Data, statusCode: Int) -> Error {
        struct ErrDTO: Decodable { let error: String? }
        if let err = try? JSONDecoder().decode(ErrDTO.self, from: data),
           let msg = err.error?.trimmingCharacters(in: .whitespacesAndNewlines),
           !msg.isEmpty
        {
            return NSError(domain: "OttoAPI", code: statusCode, userInfo: [NSLocalizedDescriptionKey: msg])
        }
        return URLError(.badServerResponse)
    }

    private func perform<T: Decodable>(_ request: URLRequest) async throws -> T {
        let (data, response) = try await performRaw(request)
        guard let http = response as? HTTPURLResponse, (200...299).contains(http.statusCode) else {
            throw Self.apiError(from: data, statusCode: (response as? HTTPURLResponse)?.statusCode ?? -1)
        }
        do {
            return try decoder.decode(T.self, from: data)
        } catch {
            let snippet = String(data: data, encoding: .utf8).map { String($0.prefix(256)) } ?? ""
            OttoLog.api.error(
                "JSON decode failed path=\(request.url?.path ?? "?") error=\(String(describing: error)) bodyPrefix=\(snippet)"
            )
            throw error
        }
    }

    private func performRaw(_ request: URLRequest) async throws -> (Data, URLResponse) {
        var request = request
        if let url = request.url {
            request.url = Self.urlAppendingClientTelemetry(url)
        }
        if let authToken, !authToken.isEmpty {
            request.setValue("Bearer \(authToken)", forHTTPHeaderField: "Authorization")
        }
        #if DEBUG
        OttoLog.api.debug(
            "→ \(request.httpMethod ?? "GET") \(request.url?.absoluteString ?? "")"
        )
        #endif
        do {
            let (data, response) = try await session.data(for: request)
            if let http = response as? HTTPURLResponse {
                if http.statusCode == 401, Self.requestHadNonEmptyBearer(request) {
                    setAuthToken(nil)
                    DispatchQueue.main.async {
                        NotificationCenter.default.post(name: .ottoSessionUnauthorized, object: nil)
                    }
                }
                if !(200...299).contains(http.statusCode) {
                    let body = String(data: data, encoding: .utf8).map { String($0.prefix(300)) } ?? ""
                    OttoLog.api.error(
                        "HTTP \(http.statusCode) \(request.url?.path ?? "") bodyPrefix=\(body)"
                    )
                }
            }
            return (data, response)
        } catch {
            OttoLog.api.error(
                "Network error url=\(request.url?.absoluteString ?? "") \(String(describing: error))"
            )
            throw error
        }
    }
}

private struct AnyDecodable: Decodable {
    let rawValue: Any
    var boolValue: Bool? { rawValue as? Bool }

    init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()
        if let b = try? container.decode(Bool.self) { rawValue = b; return }
        if let i = try? container.decode(Int.self) { rawValue = i; return }
        if let d = try? container.decode(Double.self) { rawValue = d; return }
        if let s = try? container.decode(String.self) { rawValue = s; return }
        if let dict = try? container.decode([String: AnyDecodable].self) {
            rawValue = dict.mapValues(\.rawValue); return
        }
        if let arr = try? container.decode([AnyDecodable].self) {
            rawValue = arr.map(\.rawValue); return
        }
        rawValue = NSNull()
    }
}
