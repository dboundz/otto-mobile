import SwiftUI

struct RouteAutoCheckpointOfferDialog: View {
    let intervals: [RouteAutoCheckpointGenerator.IntervalOption]
    @Binding var selectedSpacingMeters: Double
    var recommendedSpacingMeters: Double? = nil
    let onAdd: () -> Void
    let onSkip: () -> Void

    private var selectedInterval: RouteAutoCheckpointGenerator.IntervalOption? {
        intervals.first { $0.spacingMeters == selectedSpacingMeters }
    }

    var body: some View {
        ZStack(alignment: .bottom) {
            Color.black.opacity(0.58)
                .ignoresSafeArea()
                .contentShape(Rectangle())
                .onTapGesture(perform: onSkip)

            sheetCard
        }
    }

    private var sheetCard: some View {
        VStack(spacing: 20) {
            heroIcon
            headerCopy
            chooseSpacingLabel
            intervalList
            infoBox
            addButton
            notNowButton
        }
        .padding(.horizontal, 22)
        .padding(.top, 28)
        .padding(.bottom, 28)
        .frame(maxWidth: .infinity)
        .background(
            RoundedRectangle(cornerRadius: 28, style: .continuous)
                .fill(Color(red: 0.086, green: 0.082, blue: 0.110).opacity(0.98))
        )
        .overlay(
            RoundedRectangle(cornerRadius: 28, style: .continuous)
                .stroke(Color(red: 0.686, green: 0.322, blue: 0.871).opacity(0.35), lineWidth: 1)
        )
        .padding(.horizontal, 12)
        .padding(.bottom, 8)
    }

    private var heroIcon: some View {
        ZStack {
            Circle()
                .fill(
                    LinearGradient(
                        colors: [
                            Color(red: 0.58, green: 0.24, blue: 0.98),
                            Color(red: 0.44, green: 0.16, blue: 0.88),
                        ],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                )
                .frame(width: 76, height: 76)
                .shadow(color: Color.purple.opacity(0.45), radius: 16, y: 6)

            Image(systemName: "flag.fill")
                .font(.system(size: 30, weight: .semibold))
                .foregroundStyle(.white)

            sparkle(at: CGSize(width: -38, height: -34), size: 11)
            sparkle(at: CGSize(width: 36, height: -30), size: 9)
            sparkle(at: CGSize(width: -34, height: 32), size: 8)
            sparkle(at: CGSize(width: 38, height: 28), size: 10)
        }
        .frame(height: 88)
    }

    private func sparkle(at offset: CGSize, size: CGFloat) -> some View {
        Image(systemName: "sparkle")
            .font(.system(size: size, weight: .bold))
            .foregroundStyle(.white.opacity(0.92))
            .offset(offset)
    }

    private var headerCopy: some View {
        VStack(spacing: 10) {
            Text("Add Route Checkpoints?")
                .font(.title2.weight(.bold))
                .foregroundStyle(.white)
                .multilineTextAlignment(.center)

            Text("Checkpoints help you track progress, arrivals, and completion along the route.")
                .font(.subheadline)
                .multilineTextAlignment(.center)
                .foregroundStyle(.white.opacity(0.62))
                .lineSpacing(3)
        }
    }

    private var chooseSpacingLabel: some View {
        Text("Choose spacing")
            .font(.subheadline.weight(.semibold))
            .foregroundStyle(.white.opacity(0.48))
            .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var intervalList: some View {
        VStack(spacing: 10) {
            ForEach(intervals) { interval in
                RouteAutoCheckpointIntervalRow(
                    interval: interval,
                    isSelected: selectedSpacingMeters == interval.spacingMeters,
                    isRecommended: recommendedSpacingMeters == interval.spacingMeters,
                    onSelect: { selectedSpacingMeters = interval.spacingMeters }
                )
            }
        }
    }

    private var infoBox: some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: "info.circle.fill")
                .font(.body.weight(.semibold))
                .foregroundStyle(Color(red: 0.38, green: 0.62, blue: 1.0))
            Text("You can edit, move, or remove checkpoints anytime after they're added.")
                .font(.footnote)
                .foregroundStyle(.white.opacity(0.58))
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .fill(Color.white.opacity(0.04))
        )
        .overlay(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .stroke(Color.white.opacity(0.08), lineWidth: 1)
        )
    }

    private var addButton: some View {
        Button(action: onAdd) {
            HStack(spacing: 8) {
                Image(systemName: "sparkles")
                    .font(.subheadline.weight(.bold))
                Text(addButtonTitle)
                    .font(.subheadline.weight(.bold))
            }
            .foregroundStyle(.white)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 16)
            .background(
                LinearGradient(
                    colors: [
                        Color(red: 0.52, green: 0.20, blue: 0.95),
                        Color(red: 0.86, green: 0.22, blue: 0.95),
                    ],
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                )
            )
            .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
            .shadow(color: Color.purple.opacity(0.42), radius: 14)
        }
        .buttonStyle(.plain)
    }

    private var addButtonTitle: String {
        let count = selectedInterval?.checkpointCount ?? 0
        let noun = count == 1 ? "Checkpoint" : "Checkpoints"
        return "Add \(count) \(noun)"
    }

    private var notNowButton: some View {
        Button(action: onSkip) {
            Text("Not now")
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(Color(red: 0.78, green: 0.48, blue: 1.0))
                .frame(maxWidth: .infinity)
                .padding(.vertical, 4)
        }
        .buttonStyle(.plain)
    }
}

private struct RouteAutoCheckpointIntervalRow: View {
    let interval: RouteAutoCheckpointGenerator.IntervalOption
    let isSelected: Bool
    var isRecommended: Bool = false
    let onSelect: () -> Void

    private var accent: Color { Color(red: 0.78, green: 0.48, blue: 1.0) }

    var body: some View {
        Button(action: onSelect) {
            HStack(spacing: 12) {
                radioIndicator
                VStack(alignment: .leading, spacing: 4) {
                    HStack(spacing: 8) {
                        Text("Every \(offerIntervalLabel(miles: interval.miles))")
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(.white)
                        if isRecommended {
                            recommendedBadge
                        }
                    }
                    Text("~\(interval.checkpointCount) checkpoint\(interval.checkpointCount == 1 ? "" : "s")")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(isSelected ? accent : .white.opacity(0.48))
                }
                Spacer(minLength: 4)
                RouteCheckpointSpacingPreview(checkpointCount: interval.checkpointCount)
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 13)
            .background(rowBackground)
            .overlay(rowBorder)
        }
        .buttonStyle(.plain)
    }

    private var radioIndicator: some View {
        ZStack {
            Circle()
                .stroke(isSelected ? accent : Color.white.opacity(0.28), lineWidth: 2)
                .frame(width: 22, height: 22)
            if isSelected {
                Circle()
                    .fill(accent)
                    .frame(width: 12, height: 12)
            }
        }
    }

    private var recommendedBadge: some View {
        Text("Recommended")
            .font(.caption2.weight(.bold))
            .foregroundStyle(accent)
            .padding(.horizontal, 8)
            .padding(.vertical, 3)
            .background(
                Capsule(style: .continuous)
                    .fill(accent.opacity(0.16))
            )
            .overlay(
                Capsule(style: .continuous)
                    .stroke(accent.opacity(0.35), lineWidth: 1)
            )
    }

    private var rowBackground: some View {
        RoundedRectangle(cornerRadius: 16, style: .continuous)
            .fill(isSelected ? Color.purple.opacity(0.14) : Color.white.opacity(0.05))
    }

    private var rowBorder: some View {
        RoundedRectangle(cornerRadius: 16, style: .continuous)
            .stroke(isSelected ? accent.opacity(0.65) : Color.white.opacity(0.1), lineWidth: isSelected ? 1.5 : 1)
    }

    private func offerIntervalLabel(miles: Double) -> String {
        if miles == 0.5 { return "1/2 mile" }
        if miles == floor(miles) {
            let whole = Int(miles)
            return whole == 1 ? "1 mile" : "\(whole) miles"
        }
        return String(format: "%.1f miles", miles)
    }
}

private struct RouteCheckpointSpacingPreview: View {
    let checkpointCount: Int

    private var flagCount: Int {
        switch checkpointCount {
        case ..<4: return 2
        case ..<12: return 3
        case ..<25: return 4
        default: return 5
        }
    }

    var body: some View {
        ZStack {
            previewPath
            HStack(spacing: previewSpacing) {
                ForEach(0..<flagCount, id: \.self) { index in
                    Image(systemName: "flag.fill")
                        .font(.system(size: 7, weight: .bold))
                        .foregroundStyle(Color(red: 0.72, green: 0.42, blue: 1.0).opacity(index == flagCount - 1 ? 1 : 0.75))
                }
            }
        }
        .frame(width: 52, height: 30)
    }

    private var previewSpacing: CGFloat {
        switch flagCount {
        case 2: return 14
        case 3: return 9
        case 4: return 5
        default: return 3
        }
    }

    private var previewPath: some View {
        Path { path in
            path.move(to: CGPoint(x: 4, y: 22))
            path.addCurve(
                to: CGPoint(x: 48, y: 8),
                control1: CGPoint(x: 16, y: 6),
                control2: CGPoint(x: 34, y: 24)
            )
        }
        .stroke(
            Color.white.opacity(0.22),
            style: StrokeStyle(lineWidth: 1.5, lineCap: .round, dash: [2, 3])
        )
    }
}
