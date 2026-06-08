import AppIntents
import SwiftUI
import WidgetKit

// MARK: - Design

private enum SharingWidgetPalette {
    /// Soft orange–red for inactive (avoid “error” red).
    static let inactive = Color(red: 0.9, green: 0.48, blue: 0.42)
    static let active = Color(red: 0.20, green: 0.78, blue: 0.35)
    static let paused = Color(red: 0.98, green: 0.68, blue: 0.22)
    static let accent = Color(red: 0.52, green: 0.38, blue: 0.88)
    /// Live headline — high-contrast, slightly minted white (hero).
    static let primaryLive = Color(red: 0.94, green: 0.99, blue: 0.96)
    /// Secondary squad / context line — readable but clearly below the hero.
    static let subtitle = Color.white.opacity(0.50)
    /// Tertiary place line — quiet; must not compete with the status stack.
    static let placeLine = Color.white.opacity(0.20)
    static let placeLineIcon = Color.white.opacity(0.18)
    static let ambientPurple = Color(red: 0.32, green: 0.18, blue: 0.48)
}

private extension URL {
    /// Opens the map tab and presents the location-sharing sheet (home screen widget tap / “View details”).
    static let ottoShare = URL(string: "otto://share")!
}

// MARK: - Intent

struct ToggleSharingIntent: AppIntent {
    static var title: LocalizedStringResource = "Toggle location sharing"
    static var isDiscoverable: Bool = false

    func perform() async throws -> some IntentResult {
        let suite = OttoAppGroup.suite
        guard let token = suite.string(forKey: OttoShareExtensionAuthKeys.authToken), !token.isEmpty,
              let userId = suite.string(forKey: OttoShareExtensionAuthKeys.currentUserID), !userId.isEmpty else {
            return .result()
        }

        let enabled = suite.bool(forKey: OttoSharingUserDefaultsKeys.sharingEnabled)

        if enabled {
            let ids = (suite.array(forKey: OttoSharingUserDefaultsKeys.sharingCircleIDs) as? [String])?
                .filter { !$0.isEmpty } ?? []
            for circleId in Set(ids) {
                try? await WidgetPresenceClient.markInactive(userId: userId, circleId: circleId, token: token)
            }
            suite.set(false, forKey: OttoSharingUserDefaultsKeys.sharingEnabled)
            suite.removeObject(forKey: OttoSharingUserDefaultsKeys.sharingSessionStartedAt)
            suite.set(false, forKey: OttoSharingUserDefaultsKeys.sharingDrivingOnlyPaused)
        } else {
            let ids = (suite.array(forKey: OttoSharingUserDefaultsKeys.sharingCircleIDs) as? [String])?
                .filter { !$0.isEmpty } ?? []
            guard !ids.isEmpty else {
                return .result()
            }
            suite.set(true, forKey: OttoSharingUserDefaultsKeys.sharingEnabled)
            suite.set(Date().timeIntervalSince1970, forKey: OttoSharingUserDefaultsKeys.sharingSessionStartedAt)
            suite.set(false, forKey: OttoSharingUserDefaultsKeys.sharingDrivingOnlyPaused)
        }

        OttoSharingPersistence.bumpRevision(in: suite)
        OttoSharingPersistence.mirrorSharingKeysToStandard(from: suite)
        WidgetCenter.shared.reloadTimelines(ofKind: OttoSharingWidgetKind.control)
        return .result()
    }
}

// MARK: - Timeline entry

struct SharingControlEntry: TimelineEntry {
    let date: Date
    let isAuthenticated: Bool
    let isSharingEnabled: Bool
    let isDrivingOnly: Bool
    let drivingOnlyPaused: Bool
    let squadSummary: String
    let placeLabel: String
    let sessionStartedAt: Date?
    let remainingSessionSummary: String?
    /// Distinct squads selected for sharing (for bottom stats pill).
    let sharingSquadCount: Int
    /// Short remaining time for the pill, e.g. "42m", "1h". Nil when session has no fixed end (`sharingDurationSeconds` unset).
    let sessionTimeRemainingCompact: String?

    /// True when presence is actively publishing (matches in-app pill “live”).
    var isLivePublishing: Bool {
        guard isSharingEnabled else { return false }
        if isDrivingOnly, drivingOnlyPaused { return false }
        return true
    }

    var headlineTitle: String {
        guard isAuthenticated else { return "Not Sharing" }
        if !isSharingEnabled { return "Not Sharing" }
        if isDrivingOnly, drivingOnlyPaused { return "Paused" }
        if isDrivingOnly { return "Sharing Live" }
        return "Sharing Live"
    }

    var headlineSubtitle: String {
        guard isAuthenticated else { return "Open Driftd to sign in" }
        if !isSharingEnabled { return "Tap to start" }
        if isDrivingOnly, drivingOnlyPaused { return "Live when you’re driving" }
        if !squadSummary.isEmpty { return squadSummary }
        return "Sharing with squads"
    }

    var mediumSubtitle: String {
        guard isAuthenticated else { return "Open Driftd to sign in" }
        if !isSharingEnabled { return "Tap to start sharing" }
        return headlineSubtitle
    }

    var liveForMinutes: Int? {
        guard isSharingEnabled, let sessionStartedAt else { return nil }
        let secs = date.timeIntervalSince(sessionStartedAt)
        guard secs > 30 else { return nil }
        return Int(secs / 60)
    }

    var liveForLabel: String? {
        guard let m = liveForMinutes, m > 0 else { return nil }
        return "Live for \(m)m"
    }

    /// Shorter, single-line friendly place copy for the widget (city-first, capped).
    var placeLineDisplay: String {
        let t = placeLabel.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !t.isEmpty else { return "" }
        if let comma = t.firstIndex(of: ",") {
            let city = t[..<comma].trimmingCharacters(in: .whitespaces)
            return Self.clipPlaceSegment(String(city))
        }
        return Self.clipPlaceSegment(t)
    }

    private static func clipPlaceSegment(_ s: String) -> String {
        let maxLen = 16
        guard s.count > maxLen else { return s }
        let idx = s.index(s.startIndex, offsetBy: maxLen - 1)
        return String(s[..<idx]) + "…"
    }
}

// MARK: - Small widget status phases (reference layout)

private enum SmallWidgetPhase: Equatable {
    /// Not signed in or sharing off — red hollow ring, OFF.
    case off
    /// Share-now (or any non–driving-only live session) — green LIVE.
    case live
    /// Driving-only session, broadcasting — purple SQUAD emphasis.
    case squad
    /// Driving-only, waiting for motion — amber PAUSED.
    case paused
}

extension SharingControlEntry {
    /// Maps app state to the four small-widget marketing states.
    fileprivate var smallWidgetPhase: SmallWidgetPhase {
        guard isAuthenticated else { return .off }
        guard isSharingEnabled else { return .off }
        if isDrivingOnly, drivingOnlyPaused { return .paused }
        if isDrivingOnly { return .squad }
        return .live
    }
}

private extension SharingControlEntry {
    /// Uppercase status word (LIVE / OFF / SQUAD / PAUSED).
    var smallPrimaryLabel: String {
        switch smallWidgetPhase {
        case .off: return "OFF"
        case .live: return "LIVE"
        case .squad: return "SQUAD"
        case .paused: return "PAUSED"
        }
    }

    var smallPrimaryColor: Color {
        switch smallWidgetPhase {
        case .off:
            return Color(red: 1.0, green: 0.35, blue: 0.38)
        case .live:
            return SharingWidgetPalette.active
        case .squad:
            return Color(red: 0.72, green: 0.52, blue: 1.0)
        case .paused:
            return SharingWidgetPalette.paused
        }
    }

    var smallSecondaryLabel: String {
        switch smallWidgetPhase {
        case .off:
            return isAuthenticated ? "Not Sharing" : "Not signed in"
        case .live:
            if !squadSummary.isEmpty { return squadSummary }
            return "Sharing live"
        case .squad:
            let n = sharingSquadCount
            if n == 1 { return "1 member" }
            if n > 1 { return "\(n) members" }
            return "Your squad"
        case .paused:
            return "Will resume"
        }
    }

    var smallLivePillMinutesText: String {
        if let m = liveForMinutes, m > 0 { return "\(m)m" }
        if let c = sessionTimeRemainingCompact { return c }
        return "Live"
    }
}

struct SharingControlProvider: TimelineProvider {
    func placeholder(in context: TimelineProviderContext) -> SharingControlEntry {
        SharingControlEntry(
            date: Date(),
            isAuthenticated: true,
            isSharingEnabled: true,
            isDrivingOnly: false,
            drivingOnlyPaused: false,
            squadSummary: "Cars & Cocktails",
            placeLabel: "San Francisco, CA",
            sessionStartedAt: Date().addingTimeInterval(-42 * 60),
            remainingSessionSummary: "≈ 45m left",
            sharingSquadCount: 2,
            sessionTimeRemainingCompact: "45m"
        )
    }

    func getSnapshot(in context: TimelineProviderContext, completion: @escaping (SharingControlEntry) -> Void) {
        completion(makeEntry())
    }

    func getTimeline(in context: TimelineProviderContext, completion: @escaping (Timeline<SharingControlEntry>) -> Void) {
        let entry = makeEntry()
        let refresh = Calendar.current.date(byAdding: .minute, value: 5, to: Date()) ?? entry.date.addingTimeInterval(300)
        completion(Timeline(entries: [entry], policy: .after(refresh)))
    }

    private func makeEntry(date: Date = Date()) -> SharingControlEntry {
        let suite = OttoAppGroup.suite
        let token = suite.string(forKey: OttoShareExtensionAuthKeys.authToken) ?? ""
        let authed = !token.isEmpty
        let enabled = suite.bool(forKey: OttoSharingUserDefaultsKeys.sharingEnabled)
        let modeRaw = suite.string(forKey: OttoSharingUserDefaultsKeys.sharingSessionMode) ?? "share_now"
        let drivingOnly = modeRaw == "driving_only"
        let paused = suite.bool(forKey: OttoSharingUserDefaultsKeys.sharingDrivingOnlyPaused)
        let duration = suite.double(forKey: OttoSharingUserDefaultsKeys.sharingDurationSeconds)
        let started = suite.double(forKey: OttoSharingUserDefaultsKeys.sharingSessionStartedAt)
        let sessionStartedAt = started > 0 ? Date(timeIntervalSince1970: started) : nil
        let squadSummary = suite.string(forKey: OttoSharingUserDefaultsKeys.widgetSquadSummary) ?? ""
        let placeLabel = suite.string(forKey: OttoSharingUserDefaultsKeys.widgetPlaceLabel) ?? ""
        let circleIDs = (suite.array(forKey: OttoSharingUserDefaultsKeys.sharingCircleIDs) as? [String])?
            .filter { !$0.isEmpty } ?? []
        let squadCount = Set(circleIDs).count

        var remaining: String?
        var remainingCompact: String?
        if enabled, let sessionStartedAt, duration > 0 {
            let expires = sessionStartedAt.addingTimeInterval(duration)
            let rem = expires.timeIntervalSince(date)
            if rem > 0 {
                let minutes = Int(rem / 60)
                if minutes >= 120 {
                    remaining = "≈ \(minutes / 60)h left"
                    remainingCompact = "\(minutes / 60)h"
                } else if minutes >= 60 {
                    remaining = "≈ 1h left"
                    remainingCompact = "1h"
                } else if minutes > 0 {
                    remaining = "≈ \(minutes)m left"
                    remainingCompact = "\(minutes)m"
                } else {
                    remaining = "< 1m left"
                    remainingCompact = "<1m"
                }
            }
        }

        return SharingControlEntry(
            date: date,
            isAuthenticated: authed,
            isSharingEnabled: enabled,
            isDrivingOnly: drivingOnly,
            drivingOnlyPaused: paused,
            squadSummary: squadSummary,
            placeLabel: placeLabel,
            sessionStartedAt: sessionStartedAt,
            remainingSessionSummary: remaining,
            sharingSquadCount: squadCount,
            sessionTimeRemainingCompact: remainingCompact
        )
    }
}

// MARK: - Atmosphere (full-surface, Find My–like depth)

/// Marketing map raster — wide art with focal point trailing/bottom; bias crop so small & large squares keep the glow.
private struct SharingWidgetMapBackdrop: View {
    var body: some View {
        GeometryReader { geo in
            let w = geo.size.width
            let h = geo.size.height
            Image("SharingWidgetMapBackground")
                .resizable()
                .interpolation(.high)
                .antialiased(true)
                .scaledToFill()
                .frame(width: w * 1.34, height: h * 1.12, alignment: .bottomTrailing)
                .frame(width: w, height: h, alignment: .bottomTrailing)
                .clipped()
        }
        .accessibilityHidden(true)
    }
}

private struct WidgetAtmosphericBackground: View {
    var body: some View {
        GeometryReader { geo in
            let w = geo.size.width
            let h = geo.size.height
            let cornerR = max(w, h) * 0.45
            ZStack {
                // Base — slightly richer mid-tone so art never clips poorly on light edges.
                LinearGradient(
                    colors: [
                        Color(red: 0.07, green: 0.072, blue: 0.095),
                        Color(red: 0.055, green: 0.056, blue: 0.075),
                        Color(red: 0.042, green: 0.04, blue: 0.07),
                    ],
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                )

                SharingWidgetMapBackdrop()
                    .opacity(0.94)

                // Soft center / upper wash — keeps leading content legible.
                RadialGradient(
                    colors: [
                        Color(red: 0.08, green: 0.08, blue: 0.11).opacity(0.45),
                        Color(red: 0.06, green: 0.06, blue: 0.08).opacity(0.18),
                        Color.clear,
                    ],
                    center: UnitPoint(x: 0.32, y: 0.35),
                    startRadius: max(w, h) * 0.06,
                    endRadius: max(w, h) * 0.58
                )

                LinearGradient(
                    colors: [
                        Color.white.opacity(0.05),
                        Color.white.opacity(0.015),
                        Color.clear,
                    ],
                    startPoint: .topLeading,
                    endPoint: UnitPoint(x: 0.48, y: 0.55)
                )

                RadialGradient(
                    colors: [
                        SharingWidgetPalette.ambientPurple.opacity(0.12),
                        SharingWidgetPalette.ambientPurple.opacity(0.04),
                        Color.clear,
                    ],
                    center: UnitPoint(x: 0.9, y: 0.1),
                    startRadius: 0,
                    endRadius: cornerR * 1.05
                )

                RadialGradient(
                    colors: [
                        Color(red: 0.22, green: 0.12, blue: 0.38).opacity(0.1),
                        Color.clear,
                    ],
                    center: UnitPoint(x: 0.68, y: 1.02),
                    startRadius: 24,
                    endRadius: max(w, h) * 0.8
                )

                SubtleMapGridTexture()
                    .opacity(0.18)
                    .blendMode(.overlay)

                // Edge vignette — darker frame, brighter interior (glanceable depth).
                RadialGradient(
                    colors: [
                        Color.clear,
                        Color.black.opacity(0.34),
                    ],
                    center: UnitPoint(x: 0.5, y: 0.48),
                    startRadius: min(w, h) * 0.22,
                    endRadius: max(w, h) * 0.95
                )
                .blendMode(.multiply)
                .opacity(0.82)
            }
        }
    }
}

private struct SubtleMapGridTexture: View {
    var body: some View {
        Canvas { ctx, size in
            let step: CGFloat = 20
            var diag = Path()
            var x: CGFloat = -size.height
            while x <= size.width + size.height {
                diag.move(to: CGPoint(x: x, y: 0))
                diag.addLine(to: CGPoint(x: x + size.height * 0.92, y: size.height))
                x += step
            }
            ctx.stroke(diag, with: .color(Color.white.opacity(0.032)), lineWidth: 0.45)

            var ortho = Path()
            var hx: CGFloat = 0
            while hx <= size.width + step {
                ortho.move(to: CGPoint(x: hx, y: 0))
                ortho.addLine(to: CGPoint(x: hx, y: size.height))
                hx += step * 1.35
            }
            var hy: CGFloat = 0
            while hy <= size.height + step {
                ortho.move(to: CGPoint(x: 0, y: hy))
                ortho.addLine(to: CGPoint(x: size.width, y: hy))
                hy += step * 1.35
            }
            ctx.stroke(ortho, with: .color(Color.white.opacity(0.022)), lineWidth: 0.38)
        }
    }
}

/// Whisper of purple on the trailing side — no hard panel edge.
private struct TrailingAmbientBloom: View {
    var body: some View {
        GeometryReader { geo in
            let w = geo.size.width
            LinearGradient(
                colors: [
                    Color.clear,
                    SharingWidgetPalette.ambientPurple.opacity(0.09),
                    SharingWidgetPalette.ambientPurple.opacity(0.14),
                ],
                startPoint: .leading,
                endPoint: .trailing
            )
            .frame(width: w * 0.58)
            .frame(maxWidth: .infinity, alignment: .trailing)
            .mask(
                LinearGradient(
                    colors: [.clear, .white.opacity(0.85), .white],
                    startPoint: .leading,
                    endPoint: .trailing
                )
            )
            .allowsHitTesting(false)
        }
    }
}

/// Radial wash behind the status column — ties the left content into the glass (live = soft green energy).
private struct HeroLeadGlow: View {
    let isLive: Bool

    var body: some View {
        GeometryReader { g in
            let r = max(g.size.width, g.size.height) * 0.72
            RadialGradient(
                colors: isLive
                    ? [
                        SharingWidgetPalette.active.opacity(0.16),
                        SharingWidgetPalette.active.opacity(0.06),
                        Color(red: 0.08, green: 0.22, blue: 0.18).opacity(0.04),
                        Color.clear,
                    ]
                    : [
                        Color.white.opacity(0.065),
                        Color.white.opacity(0.02),
                        Color.clear,
                    ],
                center: UnitPoint(x: 0.04, y: 0.48),
                startRadius: 8,
                endRadius: r
            )
        }
        .allowsHitTesting(false)
    }
}

// MARK: - Shared chrome

private struct SharingStatusRing: View {
    let color: Color
    var isInactive: Bool = false

    var body: some View {
        ZStack {
            Circle()
                .strokeBorder(
                    color.opacity(isInactive ? 0.38 : 0.68),
                    lineWidth: isInactive ? 1.1 : 1.65
                )
                .frame(width: isInactive ? 18 : 20, height: isInactive ? 18 : 20)
            Circle()
                .fill(color.opacity(isInactive ? 0.62 : 0.92))
                .frame(width: isInactive ? 6.5 : 8.5, height: isInactive ? 6.5 : 8.5)
        }
        .accessibilityHidden(true)
    }
}

/// Live presence anchor: larger core, soft halos, gentle pulse (Timeline-driven for widgets).
private struct SharingStatusIndicator: View {
    let color: Color
    var isInactive: Bool = false
    var isLive: Bool = false

    var body: some View {
        Group {
            if isLive && !isInactive {
                TimelineView(.periodic(from: .now, by: 0.45)) { context in
                    liveBody(phase: Self.pulsePhase(context.date))
                }
            } else {
                SharingStatusRing(color: color, isInactive: isInactive)
            }
        }
    }

    private func liveBody(phase: CGFloat) -> some View {
        let haloBoost = 0.75 + 0.25 * phase
        let innerPulse: CGFloat = 1.0 + 0.07 * phase
        return ZStack {
            Circle()
                .fill(color.opacity(0.12 * haloBoost))
                .frame(width: 42, height: 42)
                .blur(radius: 10)
            Circle()
                .fill(color.opacity(0.1 + 0.06 * phase))
                .frame(width: 30, height: 30)
                .blur(radius: 5)
            Circle()
                .strokeBorder(
                    LinearGradient(
                        colors: [
                            color.opacity(0.75),
                            color.opacity(0.45),
                        ],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    ),
                    lineWidth: 1.85
                )
                .frame(width: 25, height: 25)
            Circle()
                .fill(
                    RadialGradient(
                        colors: [
                            Color.white.opacity(0.35),
                            color.opacity(0.98),
                        ],
                        center: .center,
                        startRadius: 0,
                        endRadius: 8
                    )
                )
                .frame(width: 11 * innerPulse, height: 11 * innerPulse)
        }
        .frame(width: 44, height: 44)
        .accessibilityHidden(true)
    }

    private static func pulsePhase(_ date: Date) -> CGFloat {
        let t = date.timeIntervalSinceReferenceDate
        let s = sin(t * 2 * .pi / 2.4)
        return CGFloat((s + 1) * 0.5)
    }
}

private struct SoftRouteDecoration: View {
    /// Softer, secondary route + vehicle hint for large widget (blends into atmosphere).
    var body: some View {
        GeometryReader { geo in
            let w = geo.size.width
            let h = geo.size.height
            ZStack {
                Path { path in
                    path.move(to: CGPoint(x: w * 0.06, y: h * 0.76))
                    path.addQuadCurve(
                        to: CGPoint(x: w * 0.94, y: h * 0.2),
                        control: CGPoint(x: w * 0.52, y: h * 0.06)
                    )
                }
                .stroke(
                    SharingWidgetPalette.accent.opacity(0.32),
                    style: StrokeStyle(lineWidth: 2, lineCap: .round)
                )

                Image(systemName: "car.side.fill")
                    .font(.system(size: 10, weight: .semibold))
                    .foregroundStyle(.white.opacity(0.55))
                    .padding(5)
                    .background(
                        Circle()
                            .fill(SharingWidgetPalette.accent.opacity(0.38))
                    )
                    .position(x: w * 0.6, y: h * 0.4)
            }
        }
        .mask(
            LinearGradient(
                colors: [.clear, .white.opacity(0.5), .white.opacity(0.92)],
                startPoint: .leading,
                endPoint: .trailing
            )
        )
    }
}

// MARK: - Session stats (medium / large bottom pill)

/// Two-up status strip: remaining share window + squad count (matches marketing mock).
private struct SharingSessionStatsPill: View {
    let entry: SharingControlEntry

    private var timeCaption: String { "Time left" }
    private var timeValue: String { entry.sessionTimeRemainingCompact ?? "Open" }
    private var timeValueColor: Color {
        entry.sessionTimeRemainingCompact != nil
            ? SharingWidgetPalette.active
            : Color.white.opacity(0.42)
    }

    private var timeIconColor: Color {
        entry.sessionTimeRemainingCompact != nil
            ? SharingWidgetPalette.active.opacity(0.85)
            : Color.white.opacity(0.38)
    }

    private var squadsCaption: String { "Sharing with" }
    private var squadsTail: String { entry.sharingSquadCount == 1 ? "squad" : "squads" }

    var body: some View {
        HStack(spacing: 0) {
            HStack(alignment: .center, spacing: 7) {
                Image(systemName: "clock.fill")
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundStyle(timeIconColor)
                VStack(alignment: .leading, spacing: 1) {
                    Text(timeCaption)
                        .font(.system(size: 9, weight: .medium, design: .rounded))
                        .foregroundStyle(SharingWidgetPalette.subtitle.opacity(0.92))
                    Text(timeValue)
                        .font(.system(size: 13, weight: .bold, design: .rounded))
                        .foregroundStyle(timeValueColor)
                }
                .lineLimit(1)
                .minimumScaleFactor(0.85)
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            Rectangle()
                .fill(Color.white.opacity(0.12))
                .frame(width: 1, height: 26)

            HStack(alignment: .center, spacing: 7) {
                Image(systemName: "person.3.fill")
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundStyle(SharingWidgetPalette.accent.opacity(0.9))
                VStack(alignment: .leading, spacing: 1) {
                    Text(squadsCaption)
                        .font(.system(size: 9, weight: .medium, design: .rounded))
                        .foregroundStyle(SharingWidgetPalette.subtitle.opacity(0.92))
                    HStack(alignment: .firstTextBaseline, spacing: 3) {
                        Text("\(entry.sharingSquadCount)")
                            .font(.system(size: 13, weight: .bold, design: .rounded))
                            .foregroundStyle(SharingWidgetPalette.accent)
                        Text(squadsTail)
                            .font(.system(size: 9, weight: .medium, design: .rounded))
                            .foregroundStyle(SharingWidgetPalette.subtitle.opacity(0.92))
                    }
                }
                .lineLimit(1)
                .minimumScaleFactor(0.85)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(.horizontal, 11)
        .padding(.vertical, 8)
        .background(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .fill(Color.black.opacity(0.28))
                .overlay(
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .strokeBorder(Color.white.opacity(0.08), lineWidth: 0.65)
                )
        )
        .accessibilityElement(children: .combine)
    }
}

// MARK: - Small widget (status-first, four visual phases)

private struct SmallHeroGlow: View {
    let phase: SmallWidgetPhase

    var body: some View {
        GeometryReader { g in
            let r = max(g.size.width, g.size.height) * 0.88
            RadialGradient(
                colors: gradientColors,
                center: UnitPoint(x: 0.5, y: 0.42),
                startRadius: 4,
                endRadius: r
            )
        }
        .allowsHitTesting(false)
    }

    private var gradientColors: [Color] {
        switch phase {
        case .off:
            return [
                Color(red: 0.55, green: 0.12, blue: 0.16).opacity(0.2),
                Color.clear,
            ]
        case .live:
            return [
                SharingWidgetPalette.active.opacity(0.14),
                Color.clear,
            ]
        case .squad:
            return [
                Color(red: 0.72, green: 0.52, blue: 1.0).opacity(0.16),
                SharingWidgetPalette.ambientPurple.opacity(0.06),
                Color.clear,
            ]
        case .paused:
            return [
                SharingWidgetPalette.paused.opacity(0.14),
                Color.clear,
            ]
        }
    }
}

private struct SmallWidgetStatusGlyph: View {
    let phase: SmallWidgetPhase

    var body: some View {
        Group {
            switch phase {
            case .live:
                SharingStatusIndicator(color: SharingWidgetPalette.active, isInactive: false, isLive: true)
            case .off:
                offGlyph
            case .squad:
                squadGlyph
            case .paused:
                pausedGlyph
            }
        }
        .frame(height: 48)
    }

    private var offGlyph: some View {
        let ring = Color(red: 1.0, green: 0.38, blue: 0.4)
        return ZStack {
            Circle()
                .fill(ring.opacity(0.12))
                .frame(width: 46, height: 46)
                .blur(radius: 11)
            Circle()
                .strokeBorder(ring, lineWidth: 2.8)
                .frame(width: 30, height: 30)
        }
        .accessibilityHidden(true)
    }

    private var squadGlyph: some View {
        let c = Color(red: 0.72, green: 0.52, blue: 1.0)
        return ZStack {
            Circle()
                .fill(c.opacity(0.14))
                .frame(width: 46, height: 46)
                .blur(radius: 9)
            ForEach(0..<4, id: \.self) { i in
                RoundedRectangle(cornerRadius: 1, style: .continuous)
                    .fill(c.opacity(0.92))
                    .frame(width: 2.2, height: 5)
                    .offset(y: -17.5)
                    .rotationEffect(.degrees(Double(i) * 90))
            }
            Circle()
                .strokeBorder(
                    LinearGradient(
                        colors: [c.opacity(0.92), c.opacity(0.5)],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    ),
                    lineWidth: 2.2
                )
                .frame(width: 32, height: 32)
            Circle()
                .fill(
                    RadialGradient(
                        colors: [Color.white.opacity(0.38), c],
                        center: .center,
                        startRadius: 0,
                        endRadius: 7
                    )
                )
                .frame(width: 12, height: 12)
        }
        .accessibilityHidden(true)
    }

    private var pausedGlyph: some View {
        let c = SharingWidgetPalette.paused
        return ZStack {
            Circle()
                .fill(c.opacity(0.13))
                .frame(width: 46, height: 46)
                .blur(radius: 9)
            Circle()
                .strokeBorder(c.opacity(0.75), lineWidth: 2.4)
                .frame(width: 32, height: 32)
            Image(systemName: "pause.fill")
                .font(.system(size: 12, weight: .bold))
                .foregroundStyle(c)
        }
        .accessibilityHidden(true)
    }
}

private struct SmallWidgetBottomPill: View {
    let entry: SharingControlEntry
    let phase: SmallWidgetPhase

    var body: some View {
        HStack(spacing: 4) {
            switch phase {
            case .live:
                Image(systemName: "antenna.radiowaves.left.and.right")
                    .font(.system(size: 10, weight: .semibold))
                    .foregroundStyle(SharingWidgetPalette.active.opacity(0.88))
                Text(entry.smallLivePillMinutesText)
                    .fontWeight(.bold)
                    .foregroundStyle(SharingWidgetPalette.active)
                Text("active")
                    .fontWeight(.semibold)
                    .foregroundStyle(Color.white.opacity(0.48))
            case .off:
                Text(entry.isAuthenticated ? "Tap to enable" : "Tap to sign in")
                    .fontWeight(.semibold)
                    .foregroundStyle(Color.white.opacity(0.44))
            case .squad:
                Image(systemName: "person.2.fill")
                    .font(.system(size: 10, weight: .semibold))
                    .foregroundStyle(Color(red: 0.72, green: 0.52, blue: 1.0))
                Text(squadNearbyLabel)
                    .fontWeight(.bold)
                    .foregroundStyle(Color(red: 0.72, green: 0.52, blue: 1.0))
            case .paused:
                Image(systemName: "car.side.fill")
                    .font(.system(size: 10, weight: .semibold))
                    .foregroundStyle(Color.white.opacity(0.4))
                Text("Parked")
                    .fontWeight(.semibold)
                    .foregroundStyle(Color.white.opacity(0.48))
            }
        }
        .font(.system(size: 9.5, design: .rounded))
        .padding(.horizontal, 11)
        .padding(.vertical, 6)
        .background(
            Capsule()
                .fill(Color.white.opacity(0.095))
                .overlay(
                    Capsule()
                        .strokeBorder(Color.white.opacity(0.11), lineWidth: 0.55)
                )
        )
        .lineLimit(1)
        .minimumScaleFactor(0.72)
    }

    private var squadNearbyLabel: String {
        let n = entry.sharingSquadCount
        if n <= 0 { return "Squad" }
        return "\(n) nearby"
    }
}

// MARK: - Family layouts

private struct SharingWidgetSmallView: View {
    let entry: SharingControlEntry

    private var phase: SmallWidgetPhase { entry.smallWidgetPhase }

    var body: some View {
        ZStack {
            SmallHeroGlow(phase: phase)
            VStack(spacing: 0) {
                Spacer()
                    .frame(height: 2)

                SmallWidgetStatusGlyph(phase: phase)

                Text(entry.smallPrimaryLabel)
                    .font(.system(size: 14.5, weight: .heavy, design: .rounded))
                    .kerning(1.35)
                    .foregroundStyle(entry.smallPrimaryColor)
                    .shadow(color: entry.smallPrimaryColor.opacity(0.4), radius: 9, y: 0)
                    .lineLimit(1)
                    .minimumScaleFactor(0.72)
                    .padding(.top, 0)

                Text(entry.smallSecondaryLabel)
                    .font(.system(size: 10, weight: .medium, design: .rounded))
                    .foregroundStyle(SharingWidgetPalette.subtitle)
                    .multilineTextAlignment(.center)
                    .lineLimit(2)
                    .minimumScaleFactor(0.82)
                    .padding(.top, 2)

                Spacer(minLength: 16)

                SmallWidgetBottomPill(entry: entry, phase: phase)
                    .padding(.bottom, 10)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .padding(.horizontal, 9)
    }
}

private struct SharingWidgetMediumView: View {
    let entry: SharingControlEntry

    private var ringColor: Color {
        if !entry.isAuthenticated || !entry.isSharingEnabled { return SharingWidgetPalette.inactive }
        if entry.isLivePublishing { return SharingWidgetPalette.active }
        return SharingWidgetPalette.paused
    }

    private var statusIsInactive: Bool {
        !entry.isAuthenticated || !entry.isSharingEnabled
    }

    private var heroHeadlineColor: Color {
        entry.isLivePublishing ? SharingWidgetPalette.primaryLive : .white
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            ZStack {
                HeroLeadGlow(isLive: entry.isLivePublishing)
                TrailingAmbientBloom()

                HStack(alignment: .center, spacing: 12) {
                    SharingStatusIndicator(
                        color: ringColor,
                        isInactive: statusIsInactive,
                        isLive: entry.isLivePublishing
                    )

                    VStack(alignment: .leading, spacing: 4) {
                        Text(entry.headlineTitle)
                            .font(.system(size: 21, weight: .bold, design: .rounded))
                            .kerning(-0.42)
                            .foregroundStyle(heroHeadlineColor)
                            .shadow(color: entry.isLivePublishing ? SharingWidgetPalette.active.opacity(0.38) : .clear, radius: 12, y: 0)
                            .lineLimit(1)
                            .minimumScaleFactor(0.82)
                        Text(entry.mediumSubtitle)
                            .font(.system(size: 12, weight: .medium, design: .default))
                            .foregroundStyle(SharingWidgetPalette.subtitle)
                            .lineLimit(2)
                            .minimumScaleFactor(0.9)

                        if !entry.placeLineDisplay.isEmpty {
                            HStack(spacing: 3) {
                                Image(systemName: "location.fill")
                                    .font(.system(size: 7.5, weight: .medium))
                                    .foregroundStyle(SharingWidgetPalette.placeLineIcon)
                                Text(entry.placeLineDisplay)
                                    .font(.system(size: 9.5, weight: .medium, design: .rounded))
                                    .foregroundStyle(SharingWidgetPalette.placeLine)
                                    .lineLimit(1)
                                    .truncationMode(.tail)
                                    .minimumScaleFactor(0.8)
                            }
                            .padding(.top, 1)
                        }
                    }

                    Spacer(minLength: 2)

                    if entry.isAuthenticated {
                        Button(intent: OpenURLIntent(.ottoShare)) {
                            Image(systemName: "chevron.right")
                                .font(.system(size: 11, weight: .semibold))
                                .foregroundStyle(
                                    LinearGradient(
                                        colors: [
                                            Color.white.opacity(0.48),
                                            Color.white.opacity(0.32),
                                        ],
                                        startPoint: .top,
                                        endPoint: .bottom
                                    )
                                )
                                .frame(width: 28, height: 34)
                                .background(
                                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                                        .fill(
                                            LinearGradient(
                                                colors: [
                                                    Color.white.opacity(0.1),
                                                    Color.white.opacity(0.03),
                                                    SharingWidgetPalette.ambientPurple.opacity(0.08),
                                                ],
                                                startPoint: .topLeading,
                                                endPoint: .bottomTrailing
                                            )
                                        )
                                        .overlay(
                                            RoundedRectangle(cornerRadius: 10, style: .continuous)
                                                .strokeBorder(
                                                    LinearGradient(
                                                        colors: [
                                                            Color.white.opacity(0.12),
                                                            Color.white.opacity(0.04),
                                                        ],
                                                        startPoint: .topLeading,
                                                        endPoint: .bottomTrailing
                                                    ),
                                                    lineWidth: 0.55
                                                )
                                        )
                                )
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.leading, 15)
                .padding(.trailing, 10)
                .padding(.vertical, 5)
            }

            if entry.isAuthenticated && entry.isSharingEnabled {
                Spacer(minLength: 0)
                SharingSessionStatsPill(entry: entry)
                    .padding(.horizontal, 11)
                    .padding(.bottom, 8)
            } else {
                Spacer(minLength: 0)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
    }
}

private struct SharingWidgetLargeView: View {
    let entry: SharingControlEntry

    private var ringColor: Color {
        if !entry.isAuthenticated || !entry.isSharingEnabled { return SharingWidgetPalette.inactive }
        if entry.isLivePublishing { return SharingWidgetPalette.active }
        return SharingWidgetPalette.paused
    }

    private var statusIsInactive: Bool {
        !entry.isAuthenticated || !entry.isSharingEnabled
    }

    private var heroHeadlineColor: Color {
        entry.isLivePublishing ? SharingWidgetPalette.primaryLive : .white
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            ZStack(alignment: .topLeading) {
                HeroLeadGlow(isLive: entry.isLivePublishing)
                TrailingAmbientBloom()
                    .frame(height: 148)

                HStack(alignment: .top, spacing: 12) {
                    VStack(alignment: .leading, spacing: 6) {
                        SharingStatusIndicator(
                            color: ringColor,
                            isInactive: statusIsInactive,
                            isLive: entry.isLivePublishing
                        )
                        Text(entry.headlineTitle)
                            .font(.system(size: 22, weight: .bold, design: .rounded))
                            .kerning(-0.4)
                            .foregroundStyle(heroHeadlineColor)
                            .shadow(color: entry.isLivePublishing ? SharingWidgetPalette.active.opacity(0.35) : .clear, radius: 14, y: 0)

                        if !entry.squadSummary.isEmpty {
                            Text(entry.squadSummary)
                                .font(.system(size: 13, weight: .medium, design: .default))
                                .foregroundStyle(SharingWidgetPalette.subtitle)
                                .lineLimit(1)
                                .truncationMode(.tail)
                        }

                        if !entry.placeLineDisplay.isEmpty {
                            HStack(spacing: 3) {
                                Image(systemName: "location.fill")
                                    .font(.system(size: 7.5, weight: .medium))
                                    .foregroundStyle(SharingWidgetPalette.placeLineIcon)
                                Text(entry.placeLineDisplay)
                                    .font(.system(size: 9.5, weight: .medium, design: .rounded))
                                    .foregroundStyle(SharingWidgetPalette.placeLine)
                                    .lineLimit(1)
                                    .truncationMode(.tail)
                                    .minimumScaleFactor(0.75)
                            }
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)

                    SoftRouteDecoration()
                        .frame(width: 124, height: 118)
                }
                .padding(.bottom, 11)
            }

            if entry.isSharingEnabled, entry.isAuthenticated {
                SharingSessionStatsPill(entry: entry)
                    .padding(.top, 2)
                    .padding(.bottom, 10)
            }

            Spacer(minLength: 5)

            if entry.isAuthenticated {
                HStack(spacing: 9) {
                    Button(intent: OpenURLIntent(.ottoShare)) {
                        HStack(spacing: 5) {
                            Image(systemName: "person.3.fill")
                                .font(.caption2.weight(.semibold))
                            Text("View Details")
                                .font(.caption.weight(.semibold))
                        }
                        .foregroundStyle(.white.opacity(0.78))
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 10)
                        .background(
                            RoundedRectangle(cornerRadius: 11, style: .continuous)
                                .fill(Color.white.opacity(0.085))
                        )
                    }
                    .buttonStyle(.plain)

                    if entry.isSharingEnabled {
                        Button(intent: ToggleSharingIntent()) {
                            HStack(spacing: 5) {
                                Image(systemName: "stop.fill")
                                    .font(.caption2.weight(.bold))
                                Text("Stop Sharing")
                                    .font(.caption.weight(.semibold))
                            }
                            .foregroundStyle(.white.opacity(0.95))
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 10)
                            .background(
                                RoundedRectangle(cornerRadius: 11, style: .continuous)
                                    .fill(SharingWidgetPalette.accent.opacity(0.78))
                            )
                        }
                        .buttonStyle(.plain)
                    } else {
                        Button(intent: ToggleSharingIntent()) {
                            HStack(spacing: 5) {
                                Image(systemName: "location.fill")
                                    .font(.caption2.weight(.semibold))
                                Text("Start Sharing")
                                    .font(.caption.weight(.semibold))
                            }
                            .foregroundStyle(.white.opacity(0.95))
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 10)
                            .background(
                                RoundedRectangle(cornerRadius: 11, style: .continuous)
                                    .fill(SharingWidgetPalette.accent.opacity(0.78))
                            )
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
        .padding(.leading, 15)
        .padding(.trailing, 12)
    }
}

// MARK: - Root view

struct SharingControlWidgetView: View {
    @Environment(\.widgetFamily) private var family
    var entry: SharingControlEntry

    var body: some View {
        Group {
            switch family {
            case .systemSmall:
                SharingWidgetSmallView(entry: entry)
            case .systemMedium:
                SharingWidgetMediumView(entry: entry)
            case .systemLarge:
                SharingWidgetLargeView(entry: entry)
            default:
                SharingWidgetMediumView(entry: entry)
            }
        }
        .widgetURL(.ottoShare)
    }
}

// MARK: - Widget

struct SharingControlWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: OttoSharingWidgetKind.control, provider: SharingControlProvider()) { entry in
            SharingControlWidgetView(entry: entry)
                .containerBackground(for: .widget) {
                    WidgetAtmosphericBackground()
                }
        }
        .configurationDisplayName("Driftd sharing")
        .description("Start or stop sharing your live location. Tap the widget to open sharing settings.")
        .supportedFamilies([.systemSmall, .systemMedium, .systemLarge])
        .contentMarginsDisabled()
    }
}
