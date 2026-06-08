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
