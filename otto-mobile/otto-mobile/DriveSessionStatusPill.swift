import SwiftUI

struct DriveSessionStatusPill: View {
    let presentation: DriveSessionPillPresentation
    let onTap: () -> Void
    let onStop: () -> Void

    private var sessionIsActive: Bool {
        presentation != .idle
    }

    var body: some View {
        HStack(alignment: .center, spacing: 10) {
            Button(action: onTap) {
                HStack(alignment: .center, spacing: 9) {
                    statusDots

                    if sessionIsActive {
                        activeContent
                            .frame(maxWidth: .infinity, alignment: .leading)
                    } else {
                        idleContent
                    }
                }
            }
            .buttonStyle(.plain)

            if presentation.showsStopButton {
                Button(action: onStop) {
                    Text("Stop")
                        .font(.caption.weight(.heavy))
                        .foregroundStyle(.red)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 8)
                        .background(Color.white.opacity(0.10))
                        .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                        .overlay(
                            RoundedRectangle(cornerRadius: 10, style: .continuous)
                                .stroke(Color.white.opacity(0.14), lineWidth: 1)
                        )
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 10)
        .background(Color.black.opacity(0.82))
        .overlay(Capsule().stroke(presentation.pillBorderColor.opacity(0.35), lineWidth: 1))
        .clipShape(Capsule())
        .shadow(color: presentation.pillBorderColor.opacity(0.18), radius: 12, y: 4)
    }

    private var statusDotColor: Color {
        presentation.statusIndicatorColor ?? DriveSessionPalette.idleMuted
    }

    @ViewBuilder
    private var statusDots: some View {
        Circle()
            .fill(statusDotColor)
            .frame(width: 9, height: 9)
    }

    private var idleContent: some View {
        HStack(spacing: 10) {
            VStack(alignment: .leading, spacing: 2) {
                Text("No Active Drive")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.white)
                Text("Tap to start")
                    .font(.caption)
                    .foregroundStyle(.white.opacity(0.72))
            }
            Image(systemName: "chevron.down")
                .font(.caption.weight(.bold))
                .foregroundStyle(.white.opacity(0.8))
        }
    }

    @ViewBuilder
    private var activeContent: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack(spacing: 6) {
                Text(primaryTitle)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.white)
                Spacer(minLength: 0)
                Image(systemName: "chevron.down")
                    .font(.caption.weight(.bold))
                    .foregroundStyle(.white.opacity(0.75))
            }

            Text(secondaryLine)
                .font(.caption.weight(.medium))
                .foregroundStyle(.white.opacity(0.55))
                .lineLimit(2)

            if case .pausedSharing = presentation {
                Text("Not live until driving is detected")
                    .font(.caption2)
                    .foregroundStyle(.white.opacity(0.52))
            }
        }
    }

    private var primaryTitle: String {
        switch presentation {
        case .idle: return "No Active Drive"
        case .pausedSharing: return "Sharing paused"
        case .recording: return "Recording Drive"
        case .route: return "Route Drive"
        case .sharing: return "Sharing Live"
        case .recordingAndSharing: return "Recording + Sharing"
        }
    }

    private var secondaryLine: String {
        switch presentation {
        case .idle:
            return "Tap to start"
        case .pausedSharing:
            return "Session active"
        case .recording(let time, let distance):
            return "\(time) • \(distance)"
        case .route(let name, let completed, let total):
            return "\(name) · \(completed)/\(total) checkpoints"
        case .sharing(let squad, let viewers, let remaining):
            var parts = [squad]
            if let viewers, viewers > 0 { parts.append("\(viewers)") }
            if let remaining { parts.append(remaining) }
            return parts.joined(separator: " · ")
        case .recordingAndSharing(let time, let distance, let squad, let viewers, let remaining):
            var tail = [squad]
            if let viewers, viewers > 0 { tail.append("\(viewers)") }
            if let remaining { tail.append(remaining) }
            return "\(time) • \(distance) · \(tail.joined(separator: " · "))"
        }
    }
}

enum NavigationManeuverIcon {
    static func systemImageName(for maneuver: NavigationManeuver?) -> String {
        guard let maneuver else { return "arrow.up" }
        let type = maneuver.type.lowercased()
        let modifier = maneuver.modifier?.lowercased() ?? ""
        switch type {
        case "arrive": return "flag.checkered"
        case "depart": return "arrow.up"
        case "roundabout", "rotary": return modifier.contains("left") ? "arrow.triangle.turn.up.left.circle" : "arrow.triangle.turn.up.right.circle"
        case "merge": return "arrow.merge"
        case "fork": return "arrow.triangle.branch"
        case "end of road", "end_of_road": return modifier.contains("right") ? "arrow.turn.up.right" : "arrow.turn.up.left"
        case "turn":
            if modifier.contains("sharp left") { return "arrow.turn.up.left" }
            if modifier.contains("sharp right") { return "arrow.turn.up.right" }
            if modifier.contains("slight left") { return "arrow.up.left" }
            if modifier.contains("slight right") { return "arrow.up.right" }
            if modifier.contains("left") { return "arrow.turn.up.left" }
            if modifier.contains("right") { return "arrow.turn.up.right" }
            if modifier.contains("uturn") || modifier.contains("u-turn") { return "arrow.uturn.left" }
            return "arrow.up"
        default:
            if modifier.contains("left") { return "arrow.turn.up.left" }
            if modifier.contains("right") { return "arrow.turn.up.right" }
            return "arrow.up"
        }
    }
}

struct DriveNavigationTopCard: View {
    private enum Mode {
        case guidance(TurnByTurnGuidanceState)
        case waitingForDriveStart
    }

    private let mode: Mode
    var onRecalculate: () -> Void
    var onRetry: (() -> Void)?

    init(guidance: TurnByTurnGuidanceState, onRecalculate: @escaping () -> Void, onRetry: (() -> Void)? = nil) {
        mode = .guidance(guidance)
        self.onRecalculate = onRecalculate
        self.onRetry = onRetry
    }

    init(waitingForDriveStart: Bool) {
        mode = .waitingForDriveStart
        onRecalculate = {}
        onRetry = nil
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            topCard
            if case .guidance(let guidance) = mode, guidance.phase == .offRoute {
                offRouteBanner
            }
        }
        .allowsHitTesting(allowsHitTesting)
    }

    private var allowsHitTesting: Bool {
        switch mode {
        case .guidance(let guidance): return guidance.phase != .loading
        case .waitingForDriveStart: return false
        }
    }

    private var topCard: some View {
        HStack(alignment: .center, spacing: 14) {
            Image(systemName: iconName)
                .font(.system(size: 28, weight: .bold))
                .foregroundStyle(.white)
                .frame(width: 52, height: 52)
                .background(Circle().fill(RouteMapMarkerColors.pathPurple))

            VStack(alignment: .leading, spacing: 4) {
                switch mode {
                case .waitingForDriveStart:
                    Text(String(localized: "turn_by_turn_ready_when_you_are"))
                        .font(.headline.weight(.bold))
                        .foregroundStyle(.white)
                    Text(String(localized: "turn_by_turn_waiting_for_drive_start"))
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(.white.opacity(0.75))
                case .guidance(let guidance):
                    guidanceContent(guidance)
                }
            }
            Spacer(minLength: 0)
        }
        .padding(14)
        .background(
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .fill(Color.black.opacity(0.88))
        )
    }

    @ViewBuilder
    private func guidanceContent(_ guidance: TurnByTurnGuidanceState) -> some View {
        if guidance.phase == .loading {
            Text(String(localized: "turn_by_turn_loading"))
                .font(.headline.weight(.bold))
                .foregroundStyle(.white)
        } else if case .failed = guidance.phase {
            Text(String(localized: "turn_by_turn_failed"))
                .font(.headline.weight(.bold))
                .foregroundStyle(.white)
            Text(guidance.nextInstruction)
                .font(.subheadline)
                .foregroundStyle(.white.opacity(0.7))
                .lineLimit(2)
            if let onRetry {
                Button(String(localized: "turn_by_turn_retry"), action: onRetry)
                    .font(.caption.weight(.bold))
                    .foregroundStyle(.white)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 7)
                    .background(Capsule().fill(RouteMapMarkerColors.pathPurple))
                    .padding(.top, 4)
            }
        } else {
            Text(guidance.nextInstruction)
                .font(.headline.weight(.bold))
                .foregroundStyle(.white)
                .lineLimit(3)
            if guidance.phase == .navigating || guidance.phase == .offRoute {
                Text(
                    String(
                        format: String(localized: "turn_by_turn_distance_to_maneuver_format"),
                        TurnByTurnDistanceFormatter.formatMeters(guidance.distanceToManeuverMeters)
                    )
                )
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(.white.opacity(0.75))
            }
            if let road = guidance.currentRoadName, !road.isEmpty {
                Text(road)
                    .font(.caption)
                    .foregroundStyle(.white.opacity(0.55))
                    .lineLimit(1)
            }
        }
    }

    private var iconName: String {
        switch mode {
        case .guidance(let guidance): return NavigationManeuverIcon.systemImageName(for: guidance.nextManeuver)
        case .waitingForDriveStart: return "steeringwheel"
        }
    }

    private var offRouteBanner: some View {
        HStack(spacing: 12) {
            Text(String(localized: "turn_by_turn_off_route"))
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(.white)
            Spacer(minLength: 0)
            Button(String(localized: "turn_by_turn_recalculate"), action: onRecalculate)
                .font(.subheadline.weight(.bold))
                .foregroundStyle(.white)
                .padding(.horizontal, 14)
                .padding(.vertical, 8)
                .background(Capsule().fill(RouteMapMarkerColors.pathPurple))
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 12)
        .background(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .fill(Color.orange.opacity(0.22))
        )
    }
}
