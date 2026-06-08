package to.ottomot.driftd.routebuilder

enum class RouteBuilderUiState {
    SET_START,
    SET_FINISH,
    GENERATING_ROUTE,
    ROUTE_READY,
    MANUAL_PLOT,
    EDIT_ROUTE,
}

object RouteBuilderUiStateResolver {
    data class Inputs(
        val hasStart: Boolean,
        val hasFinish: Boolean,
        val isRunningGuidedGeneration: Boolean,
        val isSnapping: Boolean,
        val hasRoutePath: Boolean,
        val checkpointCount: Int,
        val isManualMode: Boolean,
        val isEditMode: Boolean,
        val hasCompletedGuidedGeneration: Boolean,
        val guidedBackToStartStep: Boolean,
    )

    fun resolve(inputs: Inputs): RouteBuilderUiState {
        if (inputs.isRunningGuidedGeneration ||
            (
                inputs.isSnapping &&
                    inputs.hasStart &&
                    inputs.hasFinish &&
                    !inputs.isManualMode &&
                    !inputs.isEditMode &&
                    !inputs.hasCompletedGuidedGeneration
                )
        ) {
            return RouteBuilderUiState.GENERATING_ROUTE
        }

        if (inputs.isEditMode) {
            return RouteBuilderUiState.EDIT_ROUTE
        }

        if (inputs.isManualMode) {
            if (inputs.hasStart && inputs.hasFinish && inputs.hasRoutePath) {
                return RouteBuilderUiState.EDIT_ROUTE
            }
            return RouteBuilderUiState.MANUAL_PLOT
        }

        if (inputs.hasCompletedGuidedGeneration &&
            inputs.hasStart &&
            inputs.hasFinish &&
            inputs.hasRoutePath
        ) {
            return RouteBuilderUiState.ROUTE_READY
        }

        if (inputs.guidedBackToStartStep || !inputs.hasStart) {
            return RouteBuilderUiState.SET_START
        }

        if (!inputs.hasFinish) {
            return RouteBuilderUiState.SET_FINISH
        }

        return RouteBuilderUiState.SET_FINISH
    }
}
