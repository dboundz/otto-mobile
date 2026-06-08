import Foundation

enum RouteBuilderUIState: Equatable {
    case setStart
    case setFinish
    case generatingRoute
    case routeReady
    case manualPlot
    case editRoute
}

enum RouteBuilderUIStateResolver {
    struct Inputs: Equatable {
        var hasStart: Bool
        var hasFinish: Bool
        var isRunningGuidedGeneration: Bool
        var isSnapping: Bool
        var hasRoutePath: Bool
        var checkpointCount: Int
        var isManualMode: Bool
        var isEditMode: Bool
        var hasCompletedGuidedGeneration: Bool
        var guidedBackToStartStep: Bool
    }

    static func resolve(_ inputs: Inputs) -> RouteBuilderUIState {
        if inputs.isRunningGuidedGeneration || (inputs.isSnapping && inputs.hasStart && inputs.hasFinish && !inputs.isManualMode && !inputs.isEditMode && !inputs.hasCompletedGuidedGeneration) {
            return .generatingRoute
        }

        if inputs.isEditMode {
            return .editRoute
        }

        if inputs.isManualMode {
            if inputs.hasStart && inputs.hasFinish && inputs.hasRoutePath {
                return .editRoute
            }
            return .manualPlot
        }

        if inputs.hasCompletedGuidedGeneration && inputs.hasStart && inputs.hasFinish && inputs.hasRoutePath {
            return .routeReady
        }

        if inputs.guidedBackToStartStep || !inputs.hasStart {
            return .setStart
        }

        if !inputs.hasFinish {
            return .setFinish
        }

        return .setFinish
    }
}
