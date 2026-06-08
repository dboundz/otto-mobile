import SwiftUI

// MARK: - Squad Grid

struct SquadGridView: View {
    let metrics: [SquadGridMetricDTO]
    let isLoading: Bool
    let errorMessage: String?
    /// True when every metric has no leaders — full-page empty state.
    let isPageEmpty: Bool
    let onSelectLeader: (SquadGridLeaderDTO) -> Void
    var onRefresh: (() async -> Void)?

    @State private var cardsRevealed = false

    var body: some View {
        Group {
            if isLoading && metrics.isEmpty {
                ProgressView()
                    .tint(OttoScreenChrome.accentColor)
                    .frame(maxWidth: .infinity)
                    .padding(.top, 48)
            } else if let errorMessage, !errorMessage.isEmpty, metrics.isEmpty {
                UnifiedEmptyStateView(
                    title: String(localized: "fetch_error_squad_grid_title"),
                    message: String(localized: "fetch_error_refresh_body"),
                    systemImage: "exclamationmark.triangle",
                    actionTitle: String(localized: "fetch_error_refresh_action"),
                    action: {
                        if let onRefresh {
                            Task { await onRefresh() }
                        }
                    }
                )
                .frame(minHeight: 360)
            } else if isPageEmpty {
                emptyPage
            } else {
                scrollContent
            }
        }
        .onAppear {
            cardsRevealed = false
            withAnimation(.spring(response: 0.52, dampingFraction: 0.88)) {
                cardsRevealed = true
            }
        }
        .onChange(of: metrics.map(\.id)) { _, _ in
            cardsRevealed = false
            withAnimation(.spring(response: 0.48, dampingFraction: 0.9)) {
                cardsRevealed = true
            }
        }
    }

    private var scrollContent: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 22) {
                ForEach(Array(metrics.enumerated()), id: \.element.id) { index, metric in
                    SquadGridMetricCard(
                        metric: metric,
                        onSelectLeader: onSelectLeader
                    )
                    .opacity(cardsRevealed ? 1 : 0)
                    .offset(y: cardsRevealed ? 0 : 10)
                    .animation(
                        .spring(response: 0.5, dampingFraction: 0.86)
                            .delay(Double(index) * 0.055),
                        value: cardsRevealed
                    )
                }
            }
            .padding(.horizontal, OttoScreenChrome.horizontalPadding)
            .padding(.top, 12)
            .padding(.bottom, 40)
        }
    }

    private var emptyPage: some View {
        VStack(spacing: 12) {
            Image(systemName: "flag.checkered.2.crossed")
                .font(.system(size: 40))
                .foregroundStyle(OttoScreenChrome.accentColor.opacity(0.55))
            Text("Nothing on the Grid yet")
                .font(.title3.weight(.bold))
                .foregroundStyle(.white)
            Text("Start driving, chatting, and checking into events to populate squad standings.")
                .font(.subheadline)
                .foregroundStyle(.white.opacity(0.55))
                .multilineTextAlignment(.center)
        }
        .padding(.horizontal, 28)
        .padding(.top, 48)
        .frame(maxWidth: .infinity)
    }
}

// MARK: - Metric card (same frosted panel as squad member list)

private struct SquadGridMetricCard: View {
    let metric: SquadGridMetricDTO
    let onSelectLeader: (SquadGridLeaderDTO) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(alignment: .top, spacing: 9) {
                metricIcon
                VStack(alignment: .leading, spacing: 1) {
                    Text(metric.label)
                        .font(.subheadline.weight(.bold))
                        .foregroundStyle(.white)
                    Text(metric.subtitle)
                        .font(.caption2)
                        .foregroundStyle(.white.opacity(0.34))
                }
                Spacer(minLength: 0)
            }

            if metric.leaders.isEmpty {
                Text("No activity yet")
                    .font(.caption.weight(.medium))
                    .foregroundStyle(.white.opacity(0.40))
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 6)
            } else {
                SquadGridPodium(metricKey: metric.key, unit: metric.unit, leaders: metric.leaders, onSelect: onSelectLeader)
                    .padding(.top, 0)
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 11)
        .background(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .fill(Color.white.opacity(0.05))
        )
        .overlay(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .stroke(Color.white.opacity(0.12), lineWidth: 1)
        )
    }

    @ViewBuilder
    private var metricIcon: some View {
        let name: String = {
            switch metric.key {
            case "distance_driven": return "road.lanes"
            case "top_speed": return "gauge.medium"
            case "messages_posted": return "waveform.path"
            case "events_attended": return "flag.checkered"
            default: return "star.fill"
            }
        }()
        ZStack {
            Circle()
                .fill(
                    RadialGradient(
                        colors: [OttoScreenChrome.accentColor.opacity(0.09), OttoScreenChrome.accentColor.opacity(0.03)],
                        center: .center,
                        startRadius: 2,
                        endRadius: 18
                    )
                )
                .frame(width: 32, height: 32)
            Image(systemName: name)
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(OttoScreenChrome.accentColor.opacity(0.9))
        }
    }
}

/// Extra scale for rank-1 (center) avatars vs #2 / #3 — two stacked +15% steps (1.15²).
private let squadGridWinnerAvatarScale: CGFloat = 1.15 * 1.15

// MARK: - Podium

/// Scales avatars, typography, and vertical rhythm so the 3-across podium fits narrow phones.
/// Rank 1 (center when 2–3 leaders) uses a larger photo than #2 / #3; row height follows the winner.
private struct SquadGridPodiumLayoutMetrics {
    let scale: CGFloat
    let hStackSpacing: CGFloat
    let avatarAlignHeight: CGFloat
    let avatarSize: CGFloat
    let ringOutset: CGFloat
    let badgeLift: CGFloat
    let rankBadgeDiameter: CGFloat
    let valueFont: CGFloat
    let nameFont: CGFloat
    /// Nudge winner stat/name down slightly after unifying type sizes (visual balance).
    let winnerStatBlockTopPadding: CGFloat
    let rowHeight: CGFloat

    init(availableWidth: CGFloat, leaderCount: Int) {
        let w = max(availableWidth, 1)
        let reference: CGFloat = 332
        let rawScale = w / reference
        scale = min(1.08, max(0.62, rawScale))
        let count = max(leaderCount, 1)
        hStackSpacing = max(2, min(10, w * 0.018 / CGFloat(max(count - 1, 1))))

        avatarSize = (58 * scale).rounded()
        ringOutset = max(2.5, 3 * scale)
        let winnerRingDiameter = avatarSize * squadGridWinnerAvatarScale + ringOutset * 2
        badgeLift = (13 * scale).rounded()
        rankBadgeDiameter = max(22, (24 * scale).rounded())
        avatarAlignHeight = winnerRingDiameter + badgeLift + 4

        valueFont = max(12, min(16, 15 * scale))
        nameFont = max(8, min(11, 10 * scale))
        winnerStatBlockTopPadding = max(4, 6 * scale)

        let textBlock = valueFont * 1.35 + nameFont * 1.25 + 8 + winnerStatBlockTopPadding
        rowHeight = avatarAlignHeight + textBlock
    }
}

private struct SquadGridPodiumWidthKey: PreferenceKey {
    static var defaultValue: CGFloat = 0
    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) {
        let n = nextValue()
        value = max(value, n)
    }
}

private struct SquadGridPodium: View {
    let metricKey: String
    let unit: String
    let leaders: [SquadGridLeaderDTO]
    let onSelect: (SquadGridLeaderDTO) -> Void

    @State private var contentWidth: CGFloat = 0

    var body: some View {
        let top = Array(leaders.prefix(3))
        let effectiveWidth = contentWidth > 1 ? contentWidth : 320
        let m = SquadGridPodiumLayoutMetrics(availableWidth: effectiveWidth, leaderCount: top.count)
        HStack(alignment: .bottom, spacing: m.hStackSpacing) {
            switch top.count {
            case 0:
                EmptyView()
            case 1:
                Spacer(minLength: 0)
                leaderColumn(top[0], rank: 1, metrics: m)
                Spacer(minLength: 0)
            case 2:
                leaderColumn(top[1], rank: 2, metrics: m)
                leaderColumn(top[0], rank: 1, metrics: m)
                Spacer(minLength: 0)
            default:
                leaderColumn(top[1], rank: 2, metrics: m)
                leaderColumn(top[0], rank: 1, metrics: m)
                leaderColumn(top[2], rank: 3, metrics: m)
            }
        }
        .padding(.top, 2)
        .frame(minHeight: m.rowHeight)
        .frame(maxWidth: .infinity)
        .background(
            GeometryReader { geo in
                Color.clear.preference(key: SquadGridPodiumWidthKey.self, value: geo.size.width)
            }
        )
        .onPreferenceChange(SquadGridPodiumWidthKey.self) { w in
            guard w > 0.5, abs(w - contentWidth) > 0.5 else { return }
            contentWidth = w
        }
    }

    @ViewBuilder
    private func leaderColumn(
        _ leader: SquadGridLeaderDTO,
        rank: Int,
        metrics m: SquadGridPodiumLayoutMetrics
    ) -> some View {
        let isWinner = rank == 1
        let avatarSize = isWinner ? m.avatarSize * squadGridWinnerAvatarScale : m.avatarSize
        let ringOutset = m.ringOutset
        let ringDiameter = avatarSize + ringOutset * 2
        let ringStyle = SquadGridRankRingStyle(rank: rank, tierHint: gridTierRingColor(leader.progressionTier), hero: isWinner)
        let badgeLift = m.badgeLift
        let pulseExtra = max(18, 26 * m.scale)
        let ringLineWidth: CGFloat = isWinner ? 2.35 : 1.75
        let ambientExtra: CGFloat = isWinner ? 14 : 11
        let badgeFont = max(10, min(12, 11 * m.scale))

        VStack(spacing: 3) {
            VStack {
                Spacer(minLength: 0)
                ZStack(alignment: .center) {
                    if isWinner {
                        SquadGridHeroPulseGlow(diameter: ringDiameter + pulseExtra, accent: ringStyle.outerGlow)
                    }

                    if isWinner {
                        CheckeredFlagBackdrop(diameter: ringDiameter + max(14, 18 * m.scale))
                            .opacity(0.28)
                    }

                    Circle()
                        .fill(
                            RadialGradient(
                                colors: [ringStyle.ambientGlow, Color.clear],
                                center: .center,
                                startRadius: 5,
                                endRadius: max(44, avatarSize * 0.62)
                            )
                        )
                        .frame(width: ringDiameter + ambientExtra, height: ringDiameter + ambientExtra)
                        .blur(radius: isWinner ? 0.85 : 0.45)

                    ZStack {
                        Circle()
                            .stroke(
                                ringStyle.primaryRing,
                                lineWidth: ringLineWidth
                            )
                            .frame(width: ringDiameter, height: ringDiameter)
                            .shadow(color: ringStyle.ringShadow, radius: isWinner ? 4.5 : 2.5, x: 0, y: 1.5)

                        if isWinner {
                            Circle()
                                .stroke(
                                    LinearGradient(
                                        colors: [
                                            Color(red: 1, green: 0.92, blue: 0.45).opacity(0.85),
                                            Color(red: 0.95, green: 0.65, blue: 0.12).opacity(0.5)
                                        ],
                                        startPoint: .topLeading,
                                        endPoint: .bottomTrailing
                                    ),
                                    lineWidth: 1
                                )
                                .frame(width: ringDiameter - 4, height: ringDiameter - 4)
                                .blur(radius: 0.25)
                        }

                        AvatarView(
                            name: leader.displayName,
                            avatarUrl: leader.avatarUrl,
                            size: avatarSize,
                            accentColor: ringStyle.avatarAccent,
                            accentRingWidth: 1.5,
                            whiteRingWidth: 0
                        )
                    }
                    .frame(width: ringDiameter, height: ringDiameter)
                    .overlay(alignment: .top) {
                        Text("\(leader.rank)")
                            .font(.system(size: badgeFont, weight: .black, design: .rounded))
                            .foregroundStyle(.white)
                            .minimumScaleFactor(0.65)
                            .lineLimit(1)
                            .frame(width: m.rankBadgeDiameter, height: m.rankBadgeDiameter)
                            .background(
                                Circle()
                                    .fill(rankCapsuleColor(rank).opacity(0.94))
                            )
                            .overlay(
                                Circle()
                                    .stroke(Color.black.opacity(0.58), lineWidth: 1)
                            )
                            .shadow(color: Color.black.opacity(0.22), radius: 1.5, x: 0, y: 1)
                            .offset(y: -badgeLift)
                    }
                }
                .onTapGesture { onSelect(leader) }
            }
            .frame(height: m.avatarAlignHeight, alignment: .bottom)

            VStack(spacing: 1) {
                Text(formattedValue(metricKey: metricKey, unit: unit, value: leader.value))
                    .font(.system(size: m.valueFont, weight: .bold, design: .rounded))
                    .foregroundStyle(
                        LinearGradient(
                            colors: [
                                OttoScreenChrome.accentColor,
                                OttoScreenChrome.accentColor.opacity(0.82)
                            ],
                            startPoint: .top,
                            endPoint: .bottom
                        )
                    )
                    .shadow(color: OttoScreenChrome.accentColor.opacity(isWinner ? 0.16 : 0), radius: isWinner ? 3 : 0, x: 0, y: 0)
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)

                Text(displayNameShort(leader.displayName))
                    .font(.system(size: m.nameFont, weight: .medium, design: .rounded))
                    .foregroundStyle(.white.opacity(0.5))
                    .lineLimit(1)
                    .minimumScaleFactor(0.75)
            }
            .frame(maxWidth: .infinity)
            .multilineTextAlignment(.center)
            .padding(.top, isWinner ? m.winnerStatBlockTopPadding : 0)
            .onTapGesture { onSelect(leader) }
        }
        .frame(maxWidth: .infinity)
    }

    private func rankCapsuleColor(_ rank: Int) -> Color {
        switch rank {
        case 1: return Color(red: 0.96, green: 0.77, blue: 0.18)
        case 2: return Color(red: 0.72, green: 0.68, blue: 0.88)
        default: return Color(red: 0.82, green: 0.52, blue: 0.32)
        }
    }

    private func displayNameShort(_ name: String) -> String {
        let t = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let space = t.firstIndex(of: " ") else { return t }
        return String(t[..<space])
    }

    private func formattedValue(metricKey: String, unit: String, value: Double) -> String {
        switch metricKey {
        case "distance_driven":
            if value < 10 {
                return String(format: "%.1f %@", value, unit)
            }
            return "\(Int(value.rounded())) \(unit)"
        case "top_speed":
            let mph = Int(value.rounded())
            return mph > 0 ? "\(mph) \(unit)" : "—"
        case "messages_posted", "events_attended":
            return "\(Int(value.rounded())) \(unit)"
        default:
            return "\(value)"
        }
    }
}

// MARK: - Rank ring styling

private struct SquadGridRankRingStyle {
    let primaryRing: LinearGradient
    let ambientGlow: Color
    let outerGlow: Color
    let ringShadow: Color
    let avatarAccent: Color

    init(rank: Int, tierHint: Color, hero: Bool) {
        switch rank {
        case 1:
            primaryRing = LinearGradient(
                colors: [
                    Color(red: 1, green: 0.92, blue: 0.42),
                    Color(red: 0.96, green: 0.65, blue: 0.08),
                    Color(red: 0.88, green: 0.5, blue: 0.05).opacity(0.9)
                ],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            ambientGlow = Color(red: 1, green: 0.84, blue: 0.2).opacity(hero ? 0.28 : 0.2)
            outerGlow = Color(red: 1, green: 0.75, blue: 0.1)
            ringShadow = Color(red: 0.95, green: 0.7, blue: 0.15).opacity(0.28)
            avatarAccent = tierHint
        case 2:
            primaryRing = LinearGradient(
                colors: [
                    Color(red: 0.88, green: 0.85, blue: 0.98),
                    Color(red: 0.62, green: 0.55, blue: 0.9),
                    Color(red: 0.45, green: 0.4, blue: 0.72)
                ],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            ambientGlow = Color(red: 0.65, green: 0.55, blue: 0.95).opacity(0.18)
            outerGlow = Color(red: 0.7, green: 0.62, blue: 1)
            ringShadow = Color(red: 0.55, green: 0.45, blue: 0.95).opacity(0.18)
            avatarAccent = tierHint.opacity(0.92)
        default:
            primaryRing = LinearGradient(
                colors: [
                    Color(red: 0.95, green: 0.62, blue: 0.38),
                    Color(red: 0.78, green: 0.45, blue: 0.24),
                    Color(red: 0.55, green: 0.32, blue: 0.14)
                ],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            ambientGlow = Color(red: 0.92, green: 0.5, blue: 0.25).opacity(0.19)
            outerGlow = Color(red: 0.95, green: 0.55, blue: 0.3)
            ringShadow = Color(red: 0.8, green: 0.4, blue: 0.15).opacity(0.22)
            avatarAccent = tierHint
        }
    }
}

private struct CheckeredFlagBackdrop: View {
    let diameter: CGFloat
    var body: some View {
        Canvas { ctx, size in
            let n = 7
            let cell = size.width / CGFloat(n)
            for row in 0 ..< n {
                for col in 0 ..< n {
                    if (row + col) % 2 == 0 {
                        let r = CGRect(
                            x: CGFloat(col) * cell,
                            y: CGFloat(row) * cell,
                            width: cell,
                            height: cell
                        )
                        ctx.fill(Path(r), with: .color(Color.white.opacity(0.022)))
                    }
                }
            }
        }
        .frame(width: diameter, height: diameter)
        .blur(radius: 0.5)
        .opacity(0.65)
    }
}

private struct SquadGridHeroPulseGlow: View {
    let diameter: CGFloat
    let accent: Color
    @State private var pulse = false

    var body: some View {
        Circle()
            .fill(
                RadialGradient(
                    colors: [accent.opacity(pulse ? 0.11 : 0.06), Color.clear],
                    center: .center,
                    startRadius: 8,
                    endRadius: diameter * 0.48
                )
            )
            .frame(width: diameter, height: diameter)
            .onAppear {
                withAnimation(.easeInOut(duration: 2.4).repeatForever(autoreverses: true)) {
                    pulse = true
                }
            }
    }
}

private func gridTierRingColor(_ tierId: String) -> Color {
    switch tierId {
    case "rookie":
        return Color(red: 0.78, green: 0.48, blue: 0.27)
    case "qualifier":
        return Color(red: 0.78, green: 0.80, blue: 0.84)
    case "runner":
        return Color(red: 1.0, green: 0.84, blue: 0.22)
    case "pacer":
        return Color(red: 0.12, green: 0.66, blue: 1.0)
    case "apex":
        return Color(red: 0.66, green: 0.31, blue: 1.0)
    case "legend":
        return Color(red: 1.0, green: 0.36, blue: 0.46)
    default:
        return OttoScreenChrome.accentColor
    }
}
