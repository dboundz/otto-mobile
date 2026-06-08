import SwiftUI

enum SharingStatusPillState: Equatable {
    case inactive
    /// Timed session is still running, but driving-only mode is not broadcasting (e.g. parked).
    case paused
    case live

    /// Dot color in the sharing pill and map tab bar cue (live green, paused yellow).
    var indicatorFillColor: Color {
        switch self {
        case .inactive: return .red
        case .paused: return Color(red: 1, green: 0.86, blue: 0.12)
        case .live: return Color(red: 0.28, green: 0.86, blue: 0.42)
        }
    }
}

struct SharingStatusPill: View {
    let state: SharingStatusPillState
    let squadSummary: String
    let remainingText: String?
    let onTap: () -> Void
    let onStop: () -> Void

    private var accent: Color { state.indicatorFillColor }

    private var sessionIsOn: Bool {
        state == .live || state == .paused
    }

    var body: some View {
        HStack(alignment: .center, spacing: 10) {
            Button(action: onTap) {
                HStack(alignment: .center, spacing: 9) {
                    Circle()
                        .fill(accent)
                        .frame(width: 9, height: 9)

                    if sessionIsOn {
                        VStack(alignment: .leading, spacing: 4) {
                            HStack(spacing: 6) {
                                Text(state == .paused ? "Sharing paused" : "Sharing Live")
                                    .font(.subheadline.weight(.semibold))
                                    .foregroundStyle(.white)
                                Spacer(minLength: 0)
                                Image(systemName: "chevron.down")
                                    .font(.caption.weight(.bold))
                                    .foregroundStyle(.white.opacity(0.75))
                            }

                            HStack(spacing: 6) {
                                Text(squadSummary)
                                    .font(.caption.weight(.medium))
                                    .foregroundStyle(.white.opacity(0.55))
                                    .lineLimit(1)
                                if let remainingText {
                                    Text("•")
                                        .font(.caption.weight(.medium))
                                        .foregroundStyle(.white.opacity(0.45))
                                    Text(remainingText)
                                        .font(.caption.weight(.semibold))
                                        .foregroundStyle(accent)
                                        .lineLimit(1)
                                }
                            }
                            if state == .paused {
                                Text("Not live until driving is detected")
                                    .font(.caption2)
                                    .foregroundStyle(.white.opacity(0.52))
                                    .fixedSize(horizontal: false, vertical: true)
                            }
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                    } else {
                        HStack(spacing: 10) {
                            VStack(alignment: .leading, spacing: 2) {
                                Text("Not Sharing")
                                    .font(.subheadline.weight(.semibold))
                                    .foregroundStyle(.white)
                                Text("Tap to start sharing")
                                    .font(.caption)
                                    .foregroundStyle(.white.opacity(0.72))
                            }

                            Image(systemName: "chevron.down")
                                .font(.caption.weight(.bold))
                                .foregroundStyle(.white.opacity(0.8))
                        }
                    }
                }
                .frame(maxWidth: sessionIsOn ? .infinity : nil, alignment: .leading)
            }
            .buttonStyle(.plain)

            if sessionIsOn {
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
        .overlay(
            Capsule()
                .stroke(accent.opacity(0.35), lineWidth: 1)
        )
        .clipShape(Capsule())
        .shadow(color: accent.opacity(0.25), radius: 8, y: 2)
        .frame(maxWidth: sessionIsOn ? .infinity : nil)
    }
}
