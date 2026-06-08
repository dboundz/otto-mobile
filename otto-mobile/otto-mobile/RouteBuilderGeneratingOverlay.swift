import SwiftUI

struct RouteBuilderGeneratingOverlay: View {
    var body: some View {
        ZStack {
            Color.black.opacity(0.42)
                .ignoresSafeArea()

            VStack(spacing: 16) {
                ProgressView()
                    .progressViewStyle(.circular)
                    .tint(RouteMapMarkerColors.startButton)
                    .scaleEffect(1.2)

                VStack(spacing: 8) {
                    Text(RouteBuilderCopy.generatingTitle)
                        .font(.title3.weight(.bold))
                        .foregroundStyle(.white)

                    Text(RouteBuilderCopy.generatingSubtitle)
                        .font(.subheadline)
                        .foregroundStyle(.white.opacity(0.72))
                        .multilineTextAlignment(.center)

                    Text(RouteBuilderCopy.generatingHint)
                        .font(.caption)
                        .foregroundStyle(.white.opacity(0.48))
                        .multilineTextAlignment(.center)
                }
            }
            .padding(.horizontal, 28)
            .padding(.vertical, 32)
            .background(
                RoundedRectangle(cornerRadius: 24, style: .continuous)
                    .fill(Color.black.opacity(0.78))
            )
            .overlay(
                RoundedRectangle(cornerRadius: 24, style: .continuous)
                    .stroke(Color.white.opacity(0.12), lineWidth: 1)
            )
            .padding(.horizontal, 40)
        }
        .allowsHitTesting(true)
    }
}

enum RouteBuilderCopy {
    static func loc(_ key: String) -> String {
        NSLocalizedString(key, bundle: .main, value: key, comment: "")
    }

    static var setStartTitle: String { loc("route_builder_set_start_title") }
    static var setStartHelper: String { loc("route_builder_set_start_helper") }
    static var setStartTip: String { loc("route_builder_set_start_tip") }
    static var setStartCTA: String { loc("route_builder_set_start_cta") }
    static var setFinishTitle: String { loc("route_builder_set_finish_title") }
    static var setFinishHelper: String { loc("route_builder_set_finish_helper") }
    static var setFinishCTA: String { loc("route_builder_set_finish_cta") }
    static var moveStartCTA: String { loc("route_builder_move_start_cta") }
    static var moveFinishCTA: String { loc("route_builder_move_finish_cta") }
    static var moveStartHelper: String { loc("route_builder_move_start_helper") }
    static var moveFinishHelper: String { loc("route_builder_move_finish_helper") }
    static var startSetChip: String { loc("route_builder_start_set_chip") }
    static var buildManually: String { loc("route_builder_build_manually") }
    static var back: String { loc("route_builder_back") }
    static var step1: String { loc("route_builder_step_1") }
    static var step2: String { loc("route_builder_step_2") }
    static var generatingTitle: String { loc("route_builder_generating_title") }
    static var generatingSubtitle: String { loc("route_builder_generating_subtitle") }
    static var generatingHint: String { loc("route_builder_generating_hint") }
    static var routeReadyStatus: String { loc("route_builder_route_ready_status") }
    static var routeReadyTitle: String { loc("route_builder_route_ready_title") }
    static func routeReadyBody(count: Int) -> String {
        String(format: loc("route_builder_route_ready_body"), count)
    }
    static var routeReadyFooter: String { loc("route_builder_route_ready_footer") }
    static var looksGood: String { loc("route_builder_looks_good") }
    static var fewerCheckpoints: String { loc("route_builder_fewer_checkpoints") }
    static var moreCheckpoints: String { loc("route_builder_more_checkpoints") }
    static var adjustCheckpoints: String { loc("route_builder_adjust_checkpoints") }
    static var editRouteStatus: String { loc("route_builder_edit_route_status") }
    static var editRouteHelper: String { loc("route_builder_edit_route_helper") }
    static var editRouteTip: String { loc("route_builder_edit_route_tip") }
    static var shapeRoute: String { loc("route_builder_shape_route") }
    static var shapeRouteHelper: String { loc("route_builder_shape_route_helper") }
    static var addPath: String { loc("route_builder_add_path") }
    static var addCheckpoint: String { loc("route_builder_add_checkpoint") }
    static var addCheckpointHelper: String { loc("route_builder_add_checkpoint_helper") }
    static var addStop: String { loc("route_builder_add_stop") }
    static var addStopHelper: String { loc("route_builder_add_stop_helper") }
    static var undo: String { loc("route_builder_undo") }
    static var moveHere: String { loc("route_builder_move_here") }
    static var manualPlotStatus: String { loc("route_builder_manual_plot_status") }
    static var manualPlotHelper: String { loc("route_builder_manual_plot_helper") }
    static var densityAdjustTitle: String { loc("route_builder_density_adjust_title") }
    static var densityAdjustSubtitle: String { loc("route_builder_density_adjust_subtitle") }
    static var densityInfoTitle: String { loc("route_builder_density_info_title") }
    static var densityInfoBody: String { loc("route_builder_density_info_body") }
    static var densityFewerHint: String { loc("route_builder_density_fewer_hint") }
    static var densityRecommendedHint: String { loc("route_builder_density_recommended_hint") }
    static var densityMoreHint: String { loc("route_builder_density_more_hint") }
    static var densityMaximumHint: String { loc("route_builder_density_maximum_hint") }
    static var densityFooter: String { loc("route_builder_density_footer") }
    static var recommendedBadge: String { loc("route_builder_recommended_badge") }

    static func densityTierTitle(_ tier: CheckpointDensityTier) -> String {
        loc(tier.titleKey)
    }

    static func averageSpacing(miles: Double) -> String {
        String(format: loc("route_builder_avg_spacing"), miles)
    }
}
