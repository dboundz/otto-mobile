import SwiftUI
#if canImport(UIKit)
import UIKit
#endif

enum RouteBuilderHaptics {
    static func buttonTap() {
        #if canImport(UIKit)
        let generator = UIImpactFeedbackGenerator(style: .light)
        generator.prepare()
        generator.impactOccurred()
        #endif
    }
}

struct RouteBuilderBottomSheet: View {
    let uiState: RouteBuilderUIState
    let checkpointCount: Int
    let isSaving: Bool
    let isInteractionDisabled: Bool
    let canDecreaseCheckpointDensity: Bool
    let canIncreaseCheckpointDensity: Bool
    let isMovingPoint: Bool

    var onSetStart: () -> Void
    var onSetFinish: () -> Void
    var onBuildManually: () -> Void
    var onBackFromSetFinish: () -> Void
    var onBackFromRouteReady: () -> Void
    var onLooksGood: () -> Void
    var onFewerCheckpoints: () -> Void
    var onMoreCheckpoints: () -> Void
    var onShapeRoute: () -> Void
    var onAddCheckpoint: () -> Void
    var onAddStop: () -> Void
    var onMoveHere: () -> Void
    var onCancelMove: () -> Void

    private let accentPurple = RouteMapMarkerColors.finishButton

    var body: some View {
        VStack(spacing: 0) {
            sheetHandle
                .padding(.bottom, 14)

            mainContent
        }
    }

    @ViewBuilder
    private var mainContent: some View {
        switch uiState {
        case .setStart:
            guidedSetStart
        case .setFinish:
            guidedSetFinish
        case .generatingRoute:
            generatingCopy
        case .routeReady:
            routeReadyContent
        case .manualPlot:
            manualPlotContent
        case .editRoute:
            editRouteContent
        }
    }

    private var sheetHandle: some View {
        Capsule()
            .fill(Color.white.opacity(0.22))
            .frame(width: 36, height: 4)
            .frame(maxWidth: .infinity)
    }

    private var guidedSetStart: some View {
        VStack(alignment: .leading, spacing: 16) {
            stepIndicator(step: RouteBuilderCopy.step1, showsCheckmark: false)

            Text(RouteBuilderCopy.setStartTitle)
                .font(.title2.weight(.bold))
                .foregroundStyle(.white)

            Text(RouteBuilderCopy.setStartHelper)
                .font(.subheadline)
                .foregroundStyle(.white.opacity(0.62))
                .fixedSize(horizontal: false, vertical: true)

            primaryButton(
                title: RouteBuilderCopy.setStartCTA,
                backgroundColor: RouteMapMarkerColors.startButton,
                foregroundColor: .white,
                systemImage: "play.fill",
                layout: .centered,
                showsTrailingChevron: false,
                action: onSetStart
            )

            Text(RouteBuilderCopy.setStartTip)
                .font(.caption)
                .foregroundStyle(.white.opacity(0.42))
                .frame(maxWidth: .infinity, alignment: .center)
                .padding(.top, 2)
        }
    }

    private var guidedSetFinish: some View {
        VStack(alignment: .leading, spacing: 16) {
            backButton(action: onBackFromSetFinish)

            stepIndicator(step: RouteBuilderCopy.step2, showsCheckmark: true)

            statusChip(RouteBuilderCopy.startSetChip)

            Text(RouteBuilderCopy.setFinishTitle)
                .font(.title2.weight(.bold))
                .foregroundStyle(.white)

            Text(RouteBuilderCopy.setFinishHelper)
                .font(.subheadline)
                .foregroundStyle(.white.opacity(0.62))
                .fixedSize(horizontal: false, vertical: true)

            primaryButton(
                title: RouteBuilderCopy.setFinishCTA,
                backgroundColor: RouteMapMarkerColors.finishButton,
                foregroundColor: .white,
                systemImage: "flag.checkered",
                layout: .centered,
                showsTrailingChevron: false,
                action: onSetFinish
            )

            Button(action: tap(onBuildManually)) {
                Text(RouteBuilderCopy.buildManually)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(accentPurple)
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.plain)
            .padding(.top, 2)
        }
    }

    private var generatingCopy: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(RouteBuilderCopy.generatingTitle)
                .font(.headline.weight(.bold))
                .foregroundStyle(.white)
            Text(RouteBuilderCopy.generatingSubtitle)
                .font(.subheadline)
                .foregroundStyle(.white.opacity(0.62))
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var routeReadyContent: some View {
        VStack(alignment: .leading, spacing: 16) {
            backButton(action: onBackFromRouteReady)

            statusLabel(RouteBuilderCopy.routeReadyStatus)

            Text(RouteBuilderCopy.routeReadyTitle)
                .font(.title2.weight(.bold))
                .foregroundStyle(.white)

            Text("\(RouteBuilderCopy.routeReadyBody(count: checkpointCount)) \(RouteBuilderCopy.routeReadyFooter)")
                .font(.subheadline)
                .foregroundStyle(.white.opacity(0.72))
                .fixedSize(horizontal: false, vertical: true)

            primaryButton(
                title: RouteBuilderCopy.looksGood,
                backgroundColor: RouteMapMarkerColors.startButton,
                foregroundColor: .white,
                systemImage: "checkmark",
                layout: .centered,
                showsTrailingChevron: false,
                action: onLooksGood
            )

            HStack(spacing: 10) {
                densityStepButton(
                    title: RouteBuilderCopy.fewerCheckpoints,
                    systemImage: "minus",
                    iconColor: .white,
                    isEnabled: canDecreaseCheckpointDensity,
                    action: onFewerCheckpoints
                )
                densityStepButton(
                    title: RouteBuilderCopy.moreCheckpoints,
                    systemImage: "plus",
                    iconColor: accentPurple,
                    isEnabled: canIncreaseCheckpointDensity,
                    action: onMoreCheckpoints
                )
            }
        }
    }

    private var manualPlotContent: some View {
        VStack(alignment: .leading, spacing: 16) {
            statusLabel(RouteBuilderCopy.manualPlotStatus)

            Text(RouteBuilderCopy.manualPlotHelper)
                .font(.subheadline)
                .foregroundStyle(.white.opacity(0.62))
                .fixedSize(horizontal: false, vertical: true)

            if isMovingPoint {
                moveControlsRow
            } else {
                editToolsSection(usesMoveEndpointLabels: false)
            }

            tipFooter
        }
    }

    private var editRouteContent: some View {
        VStack(alignment: .leading, spacing: 16) {
            VStack(alignment: .leading, spacing: 6) {
                statusLabel(RouteBuilderCopy.editRouteStatus)
                Text(RouteBuilderCopy.editRouteHelper)
                    .font(.subheadline)
                    .foregroundStyle(.white.opacity(0.62))
                    .fixedSize(horizontal: false, vertical: true)
            }

            if isMovingPoint {
                moveControlsRow
            } else {
                editToolsSection(usesMoveEndpointLabels: true)
            }

            tipFooter
        }
    }

    private func editToolsSection(usesMoveEndpointLabels: Bool) -> some View {
        VStack(spacing: 14) {
            HStack(spacing: 10) {
                editActionCard(
                    title: RouteBuilderCopy.shapeRoute,
                    subtitle: RouteBuilderCopy.shapeRouteHelper,
                    systemImage: RouteMapMarkerAsset.pathInnerSymbolName,
                    accentColor: RouteMapMarkerColors.pathPurple,
                    action: onShapeRoute
                )
                editActionCard(
                    title: RouteBuilderCopy.addCheckpoint,
                    subtitle: RouteBuilderCopy.addCheckpointHelper,
                    systemImage: "flag.fill",
                    accentColor: RouteMapMarkerColors.checkpointBlue,
                    action: onAddCheckpoint
                )
                editActionCard(
                    title: RouteBuilderCopy.addStop,
                    subtitle: RouteBuilderCopy.addStopHelper,
                    systemImage: "octagon.fill",
                    accentColor: RouteMapMarkerColors.stopRed,
                    action: onAddStop
                )
            }

            sectionDivider

            HStack(spacing: 10) {
                if usesMoveEndpointLabels {
                    endpointMoveButton(
                        title: RouteBuilderCopy.moveStartCTA,
                        subtitle: RouteBuilderCopy.moveStartHelper,
                        systemImage: "play.fill",
                        iconColor: RouteMapMarkerColors.startAccent,
                        subtitleColor: RouteMapMarkerColors.startAccent,
                        backgroundColor: Color(red: 0.07, green: 0.20, blue: 0.16),
                        action: onSetStart
                    )
                    endpointMoveButton(
                        title: RouteBuilderCopy.moveFinishCTA,
                        subtitle: RouteBuilderCopy.moveFinishHelper,
                        systemImage: "flag.checkered",
                        iconColor: .white.opacity(0.88),
                        subtitleColor: .white.opacity(0.42),
                        backgroundColor: Color.white.opacity(0.08),
                        action: onSetFinish
                    )
                } else {
                    endpointMoveButton(
                        title: RouteBuilderCopy.setStartCTA,
                        subtitle: nil,
                        systemImage: "play.fill",
                        iconColor: RouteMapMarkerColors.startAccent,
                        subtitleColor: RouteMapMarkerColors.startAccent,
                        backgroundColor: Color(red: 0.07, green: 0.20, blue: 0.16),
                        action: onSetStart
                    )
                    endpointMoveButton(
                        title: RouteBuilderCopy.setFinishCTA,
                        subtitle: nil,
                        systemImage: "flag.checkered",
                        iconColor: .white.opacity(0.88),
                        subtitleColor: .white.opacity(0.42),
                        backgroundColor: Color.white.opacity(0.08),
                        action: onSetFinish
                    )
                }
            }
        }
    }

    private var sectionDivider: some View {
        Rectangle()
            .fill(Color.white.opacity(0.1))
            .frame(height: 1)
    }

    private func editActionCard(
        title: String,
        subtitle: String,
        systemImage: String,
        accentColor: Color,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: tap(action)) {
            VStack(spacing: 0) {
                VStack(spacing: 8) {
                    Image(systemName: systemImage)
                        .font(.title3.weight(.bold))
                        .foregroundStyle(.white)
                        .frame(width: 44, height: 44)
                        .background(Circle().fill(accentColor))
                        .padding(.top, 2)

                    Text(title)
                        .font(.caption.weight(.bold))
                        .foregroundStyle(.white)
                        .multilineTextAlignment(.center)
                        .lineLimit(2)
                        .minimumScaleFactor(0.82)

                    Text(subtitle)
                        .font(.caption2)
                        .foregroundStyle(.white.opacity(0.42))
                        .multilineTextAlignment(.center)
                        .lineLimit(3)
                        .minimumScaleFactor(0.82)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .padding(.horizontal, 6)
                .padding(.top, 10)
                .padding(.bottom, 12)
                .frame(maxWidth: .infinity)

                Rectangle()
                    .fill(accentColor)
                    .frame(height: 3)
            }
            .background(
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .fill(Color.white.opacity(0.06))
            )
            .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
        }
        .buttonStyle(.plain)
        .disabled(isSaving || isInteractionDisabled)
    }

    private func endpointMoveButton(
        title: String,
        subtitle: String?,
        systemImage: String,
        iconColor: Color,
        subtitleColor: Color,
        backgroundColor: Color,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: tap(action)) {
            HStack(spacing: 10) {
                Image(systemName: systemImage)
                    .font(.body.weight(.semibold))
                    .foregroundStyle(iconColor)
                    .frame(width: 24)

                VStack(alignment: .leading, spacing: 2) {
                    Text(title)
                        .font(.subheadline.weight(.bold))
                        .foregroundStyle(.white)
                    if let subtitle {
                        Text(subtitle)
                            .font(.caption)
                            .foregroundStyle(subtitleColor)
                    }
                }

                Spacer(minLength: 0)

                Image(systemName: "chevron.right")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.white.opacity(0.32))
            }
            .padding(.horizontal, 14)
            .padding(.vertical, subtitle == nil ? 16 : 13)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .fill(backgroundColor)
            )
        }
        .buttonStyle(.plain)
        .disabled(isSaving || isInteractionDisabled)
    }

    private var moveControlsRow: some View {
        HStack(spacing: 10) {
            Button(action: tap(onMoveHere)) {
                Text(RouteBuilderCopy.moveHere)
                    .font(.subheadline.weight(.bold))
                    .foregroundStyle(.white)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 14)
                    .background(accentPurple)
                    .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
            }
            .buttonStyle(.plain)

            Button(action: tap(onCancelMove)) {
                Image(systemName: "xmark")
                    .font(.subheadline.weight(.bold))
                    .foregroundStyle(.white.opacity(0.9))
                    .frame(width: 48, height: 48)
                    .background(
                        RoundedRectangle(cornerRadius: 14, style: .continuous)
                            .fill(Color.white.opacity(0.08))
                    )
            }
            .buttonStyle(.plain)
        }
    }

    private var tipFooter: some View {
        VStack(spacing: 12) {
            sectionDivider

            HStack(alignment: .top, spacing: 8) {
                Image(systemName: "lightbulb")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.white.opacity(0.45))
                    .padding(.top, 1)
                Text(RouteBuilderCopy.editRouteTip)
                    .font(.caption)
                    .foregroundStyle(.white.opacity(0.52))
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .padding(.top, 2)
    }

    private func backButton(action: @escaping () -> Void) -> some View {
        Button(action: tap(action)) {
            HStack(spacing: 4) {
                Image(systemName: "chevron.left")
                    .font(.subheadline.weight(.semibold))
                Text(RouteBuilderCopy.back)
                    .font(.subheadline.weight(.semibold))
            }
            .foregroundStyle(.white.opacity(0.72))
        }
        .buttonStyle(.plain)
    }

    private func tap(_ action: @escaping () -> Void) -> () -> Void {
        {
            RouteBuilderHaptics.buttonTap()
            action()
        }
    }

    private func stepIndicator(step: String, showsCheckmark: Bool) -> some View {
        HStack(spacing: 6) {
            if showsCheckmark {
                Image(systemName: "checkmark.circle.fill")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(RouteMapMarkerColors.startAccent)
            }
            Text(step.uppercased())
                .font(.caption.weight(.semibold))
                .foregroundStyle(.white.opacity(0.45))
        }
    }

    private func statusChip(_ title: String) -> some View {
        HStack(spacing: 6) {
            Circle()
                .fill(RouteMapMarkerColors.startAccent)
                .frame(width: 7, height: 7)
            Text(title)
                .font(.caption.weight(.semibold))
                .foregroundStyle(RouteMapMarkerColors.startAccent)
        }
    }

    private func statusLabel(_ title: String) -> some View {
        Text(title.uppercased())
            .font(.caption.weight(.heavy))
            .foregroundStyle(RouteMapMarkerColors.startAccent)
    }

    private enum PrimaryButtonLayout {
        case leading
        case centered
    }

    private func primaryButton(
        title: String,
        backgroundColor: Color,
        foregroundColor: Color,
        systemImage: String?,
        layout: PrimaryButtonLayout = .leading,
        showsTrailingChevron: Bool,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: tap(action)) {
            Group {
                switch layout {
                case .centered:
                    HStack(spacing: 10) {
                        if let systemImage {
                            Image(systemName: systemImage)
                                .font(.title3.weight(.bold))
                        }
                        Text(title)
                            .font(.title3.weight(.bold))
                    }
                    .frame(maxWidth: .infinity, alignment: .center)
                case .leading:
                    HStack(spacing: 10) {
                        if let systemImage {
                            Image(systemName: systemImage)
                                .font(.headline.weight(.bold))
                        }
                        Text(title)
                            .font(.headline.weight(.bold))
                        Spacer(minLength: 0)
                        if showsTrailingChevron {
                            Image(systemName: "chevron.right")
                                .font(.subheadline.weight(.bold))
                        }
                    }
                }
            }
            .foregroundStyle(foregroundColor)
            .padding(.horizontal, 18)
            .padding(.vertical, layout == .centered ? 18 : 16)
            .background(backgroundColor)
            .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        }
        .buttonStyle(.plain)
        .disabled(isInteractionDisabled || isSaving)
    }

    private func densityStepButton(
        title: String,
        systemImage: String,
        iconColor: Color,
        isEnabled: Bool = true,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: tap(action)) {
            HStack(spacing: 10) {
                ZStack {
                    Circle()
                        .stroke(iconColor.opacity(isEnabled ? (iconColor == .white ? 0.55 : 1) : 0.25), lineWidth: 1.5)
                        .frame(width: 28, height: 28)
                    Image(systemName: systemImage)
                        .font(.caption.weight(.bold))
                        .foregroundStyle(isEnabled ? iconColor : iconColor.opacity(0.35))
                }
                Text(title)
                    .font(.caption.weight(.bold))
                    .foregroundStyle(isEnabled ? .white : .white.opacity(0.35))
                    .multilineTextAlignment(.leading)
                    .lineLimit(2)
                    .minimumScaleFactor(0.85)
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 14)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .fill(Color.white.opacity(isEnabled ? 0.08 : 0.04))
            )
        }
        .buttonStyle(.plain)
        .disabled(isInteractionDisabled || isSaving || !isEnabled)
    }
}
