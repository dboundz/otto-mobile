package to.ottomot.driftd.routebuilder

import androidx.compose.runtime.Immutable

@Immutable
data class RouteBuilderMapInputs(
    val mapContent: RouteBuilderMapContentState,
    val programmaticCameraTarget: RouteBuilderCameraTarget?,
    val allowsInteraction: Boolean,
)
