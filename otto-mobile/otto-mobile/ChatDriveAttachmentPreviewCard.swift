import CoreLocation
import SwiftUI

/// Rich shared-drive card for squad chat (route preview + summary stats + CTA).
struct ChatDriveAttachmentPreviewCard: View {
    let attachment: CircleChatMessageDTO.DriveAttachmentDTO
    let sharedByFirstName: String
    let messageCreatedAt: Date
    /// Stable cache scope for presigned map preview URLs (same pattern as chat photo attachments).
    var messageId: String? = nil
    let lineSourceID: String
    let cardWidth: CGFloat
    let suppressNavigation: Bool
    var onLongPress: (() -> Void)? = nil
    var onDoubleTapHeart: (() -> Void)? = nil
    let onNavigate: () -> Void

    private var title: String {
        DriveDisplayNaming.listTitle(routeName: nil, driveTitle: attachment.title)
    }

    private var completedDate: Date {
        attachment.completedAt ?? messageCreatedAt
    }

    /// Wide hero map under the header (~2.2:1 on the card content width).
    private var mapPreviewHeight: CGFloat {
        max(118, (cardWidth - 24) * 0.44)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            headerRow
            routeMapHero
            driveDetailsSection
            ctaRow
        }
        .padding(12)
        .frame(width: cardWidth, alignment: .leading)
        .background(Color.white.opacity(0.055))
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .stroke(Color.purple.opacity(0.42), lineWidth: 1)
        }
        .contentShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        .overlay {
            ChatUIKitRowGestureOverlay(
                onTap: {
                    guard !suppressNavigation else { return }
                    ChatMessageActionFeedback.lightImpact()
                    onNavigate()
                },
                onLongPress: onLongPress,
                onDoubleTapHeart: onDoubleTapHeart
            )
        }
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.isButton)
        .accessibilityLabel("\(sharedByFirstName) shared a drive, \(title)")
    }

    private var headerRow: some View {
        HStack(spacing: 8) {
            ZStack {
                Circle()
                    .fill(Color.purple.opacity(0.22))
                Image(systemName: "steeringwheel")
                    .font(.system(size: 13, weight: .bold))
                    .foregroundStyle(Color.purple.opacity(0.95))
            }
            .frame(width: 28, height: 28)

            Text("\(sharedByFirstName) shared a drive")
                .font(.caption.weight(.semibold))
                .foregroundStyle(.white.opacity(0.88))
                .lineLimit(1)
                .minimumScaleFactor(0.85)

            Spacer(minLength: 4)

            Text(ChatRowTimeFormatter.string(from: messageCreatedAt))
                .font(.caption2)
                .foregroundStyle(.white.opacity(0.45))
        }
    }

    private var routeMapHero: some View {
        Group {
            if let mapPreviewURL = mapPreviewImageURL {
                CachedAsyncImage(url: mapPreviewURL, storageKey: mapPreviewImageStorageKey) { phase in
                    switch phase {
                    case .success(let image):
                        image
                            .resizable()
                            .scaledToFill()
                    case .empty, .failure:
                        routeMapInteractiveFallback
                    @unknown default:
                        routeMapInteractiveFallback
                    }
                }
            } else {
                routeMapInteractiveFallback
            }
        }
        .frame(maxWidth: .infinity)
        .frame(height: mapPreviewHeight)
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
    }

    @ViewBuilder
    private var routeMapInteractiveFallback: some View {
        if lineCoordinates.count >= 2 {
            DriveRouteMapPreview(
                lineCoordinates: lineCoordinates,
                mapPoints: mapPoints,
                completedWaypointIndexes: completedWaypointIndexes,
                height: mapPreviewHeight,
                markerScale: 0.52,
                lineSourceID: lineSourceID,
                animateOnAppear: false
            )
        } else {
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .fill(Color.white.opacity(0.04))
                .overlay {
                    Image(systemName: "map")
                        .font(.title3)
                        .foregroundStyle(.white.opacity(0.35))
                }
        }
    }

    private var mapPreviewImageURL: URL? {
        guard let raw = attachment.mapPreviewUrl?.trimmingCharacters(in: .whitespacesAndNewlines),
              !raw.isEmpty else {
            return nil
        }
        return APIConfig.imageFetchURL(from: raw)
    }

    private var mapPreviewImageStorageKey: String? {
        guard let messageId, !messageId.isEmpty,
              let raw = attachment.mapPreviewUrl?.trimmingCharacters(in: .whitespacesAndNewlines),
              !raw.isEmpty else {
            return nil
        }
        return RemoteImageStorageKey.stable(prefix: "chatDriveMapPreview:\(messageId)", sourceUrlString: raw)
    }

    private var driveDetailsSection: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title)
                .font(.subheadline.weight(.bold))
                .foregroundStyle(.white)
                .lineLimit(2)
                .fixedSize(horizontal: false, vertical: true)

            Text(completedAtText)
                .font(.caption2)
                .foregroundStyle(.white.opacity(0.52))
                .lineLimit(2)

            distanceStatRow
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var distanceStatRow: some View {
        HStack(spacing: 6) {
            Image(systemName: "road.lanes")
                .font(.caption2)
                .foregroundStyle(.white.opacity(0.42))
                .frame(width: 14)
            HStack(spacing: 4) {
                Text(distanceText)
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.white)
                Text("Distance")
                    .font(.caption2)
                    .foregroundStyle(.white.opacity(0.48))
            }
            .lineLimit(1)
            Spacer(minLength: 0)
        }
    }

    private var ctaRow: some View {
        HStack(spacing: 5) {
            Text("View Drive Summary")
            Text("→")
            Spacer(minLength: 0)
        }
        .font(.caption2.weight(.medium))
        .foregroundStyle(Color.purple.opacity(0.58))
        .lineLimit(1)
        .minimumScaleFactor(0.85)
    }

    // MARK: - Map data

    private var lineCoordinates: [CLLocationCoordinate2D] {
        let road = (attachment.roadCoordinates ?? []).map {
            CLLocationCoordinate2D(latitude: $0.lat, longitude: $0.lng)
        }
        if road.count >= 2 { return road }
        let path = (attachment.routePoints ?? [])
            .filter { ($0.type ?? "path") == "path" }
            .map { CLLocationCoordinate2D(latitude: $0.lat, longitude: $0.lng) }
        if path.count >= 2 { return path }
        return (attachment.routePoints ?? [])
            .map { CLLocationCoordinate2D(latitude: $0.lat, longitude: $0.lng) }
    }

    private var mapPoints: [RouteMapPointModel] {
        (attachment.routePoints ?? []).enumerated().compactMap { offset, point in
            let type = point.type ?? "path"
            guard type == "start" || type == "finish" else { return nil }
            return RouteMapPointModel(
                id: "chat-drive-\(lineSourceID)-\(offset)",
                coordinate: CLLocationCoordinate2D(latitude: point.lat, longitude: point.lng),
                markerType: type,
                index: offset
            )
        }
    }

    private var completedWaypointIndexes: Set<Int> {
        Set(attachment.completedWaypointIndexes ?? [])
    }

    // MARK: - Formatting

    private var completedAtText: String {
        let formatter = DateFormatter()
        formatter.dateFormat = "MMM d, yyyy • h:mm a"
        return formatter.string(from: completedDate)
    }

    private var distanceText: String {
        let meters = attachment.distanceMeters ?? 0
        let miles = meters / 1609.344
        if miles < 10 { return String(format: "%.1f mi", miles) }
        return "\(Int(miles.rounded())) mi"
    }
}
/// Rich shared-route card for squad chat (map preview + summary stats + CTA).
struct ChatRouteAttachmentPreviewCard: View {
    let attachment: CircleChatMessageDTO.RouteAttachmentDTO
    let sharedByFirstName: String
    let messageCreatedAt: Date
    var messageId: String? = nil
    let cardWidth: CGFloat
    let suppressNavigation: Bool
    var onLongPress: (() -> Void)? = nil
    var onDoubleTapHeart: (() -> Void)? = nil
    let onNavigate: () -> Void

    private var lineSourceID: String {
        if let messageId, !messageId.isEmpty {
            return "chat-route-\(messageId)"
        }
        return "chat-route-\(attachment.routeId)"
    }

    private var mapPreviewHeight: CGFloat {
        max(118, (cardWidth - 24) * 0.44)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            headerRow
            mapHero
            detailsSection
            ctaRow
        }
        .padding(12)
        .frame(width: cardWidth, alignment: .leading)
        .background(Color.white.opacity(0.055))
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .stroke(Color.purple.opacity(0.42), lineWidth: 1)
        }
        .contentShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        .overlay {
            ChatUIKitRowGestureOverlay(
                onTap: {
                    guard !suppressNavigation else { return }
                    ChatMessageActionFeedback.lightImpact()
                    onNavigate()
                },
                onLongPress: onLongPress,
                onDoubleTapHeart: onDoubleTapHeart
            )
        }
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.isButton)
        .accessibilityLabel("\(sharedByFirstName) shared a route, \(attachment.displayTitle)")
    }

    private var headerRow: some View {
        HStack(spacing: 8) {
            ZStack {
                Circle()
                    .fill(Color.purple.opacity(0.22))
                Image(systemName: "point.topleft.down.curvedto.point.bottomright.up")
                    .font(.system(size: 13, weight: .bold))
                    .foregroundStyle(Color.purple.opacity(0.95))
            }
            .frame(width: 28, height: 28)

            Text(String(format: String(localized: "chat_route_shared_header"), sharedByFirstName))
                .font(.caption.weight(.semibold))
                .foregroundStyle(.white.opacity(0.88))
                .lineLimit(1)
                .minimumScaleFactor(0.85)

            Spacer(minLength: 4)

            Text(ChatRowTimeFormatter.string(from: messageCreatedAt))
                .font(.caption2)
                .foregroundStyle(.white.opacity(0.45))
        }
    }

    private var mapHero: some View {
        Group {
            if let mapPreviewURL = mapPreviewImageURL {
                CachedAsyncImage(url: mapPreviewURL, storageKey: mapPreviewImageStorageKey) { phase in
                    switch phase {
                    case .success(let image):
                        image
                            .resizable()
                            .scaledToFill()
                    case .empty, .failure:
                        routeMapInteractiveFallback
                    @unknown default:
                        routeMapInteractiveFallback
                    }
                }
            } else {
                routeMapInteractiveFallback
            }
        }
        .frame(maxWidth: .infinity)
        .frame(height: mapPreviewHeight)
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
    }

    @ViewBuilder
    private var routeMapInteractiveFallback: some View {
        if lineCoordinates.count >= 2 {
            DriveRouteMapPreview(
                lineCoordinates: lineCoordinates,
                mapPoints: mapPoints,
                completedWaypointIndexes: [],
                height: mapPreviewHeight,
                markerScale: 0.52,
                lineSourceID: lineSourceID,
                animateOnAppear: false
            )
        } else {
            mapFallback
        }
    }

    private var mapFallback: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .fill(Color.white.opacity(0.04))
            Image(systemName: "map")
                .font(.title3)
                .foregroundStyle(.white.opacity(0.35))
        }
    }

    @ViewBuilder
    private var detailsSection: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(attachment.displayTitle)
                .font(.subheadline.weight(.bold))
                .foregroundStyle(.white)
                .lineLimit(2)
            if !routeStatsText.isEmpty {
                Text(routeStatsText)
                    .font(.caption)
                    .foregroundStyle(.white.opacity(0.72))
                    .lineLimit(1)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var ctaRow: some View {
        HStack(spacing: 5) {
            Text(String(localized: "chat_route_view_on_map"))
            Text("→")
            Spacer(minLength: 0)
        }
        .font(.caption2.weight(.medium))
        .foregroundStyle(Color.purple.opacity(0.78))
        .lineLimit(1)
        .minimumScaleFactor(0.85)
    }

    private var mapPreviewImageURL: URL? {
        guard let raw = attachment.mapPreviewUrl?.trimmingCharacters(in: .whitespacesAndNewlines),
              !raw.isEmpty else {
            return nil
        }
        return APIConfig.imageFetchURL(from: raw)
    }

    private var mapPreviewImageStorageKey: String? {
        guard let messageId, !messageId.isEmpty,
              let raw = attachment.mapPreviewUrl?.trimmingCharacters(in: .whitespacesAndNewlines),
              !raw.isEmpty else {
            return nil
        }
        return RemoteImageStorageKey.stable(prefix: "chatRouteMapPreview:\(messageId)", sourceUrlString: raw)
    }

    private var lineCoordinates: [CLLocationCoordinate2D] {
        let road = (attachment.roadCoordinates ?? []).map {
            CLLocationCoordinate2D(latitude: $0.lat, longitude: $0.lng)
        }
        if road.count >= 2 { return road }
        let path = (attachment.routePoints ?? [])
            .filter { ($0.type ?? "path") == "path" }
            .map { CLLocationCoordinate2D(latitude: $0.lat, longitude: $0.lng) }
        if path.count >= 2 { return path }
        return (attachment.routePoints ?? [])
            .map { CLLocationCoordinate2D(latitude: $0.lat, longitude: $0.lng) }
    }

    private var mapPoints: [RouteMapPointModel] {
        (attachment.routePoints ?? []).enumerated().compactMap { offset, point in
            let type = point.type ?? "path"
            guard type == "start" || type == "finish" else { return nil }
            return RouteMapPointModel(
                id: "chat-route-\(lineSourceID)-\(offset)",
                coordinate: CLLocationCoordinate2D(latitude: point.lat, longitude: point.lng),
                markerType: type,
                index: offset
            )
        }
    }

    private var routeStatsText: String {
        var parts: [String] = []
        if let meters = attachment.distanceMeters, meters > 0 {
            let miles = meters / 1609.344
            let distance = miles < 10 ? String(format: "%.1f mi", miles) : "\(Int(miles.rounded())) mi"
            parts.append(distance)
        }
        if let eta = attachment.etaSeconds, eta > 0 {
            let minutes = max(1, Int((eta / 60).rounded()))
            parts.append("\(minutes) min")
        }
        return parts.joined(separator: " · ")
    }
}
