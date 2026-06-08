package to.ottomot.driftd

import to.ottomot.driftd.core.network.dto.EventDto

internal sealed class SquadEventSubmitOutcome {
    data class Created(val event: EventDto) : SquadEventSubmitOutcome()

    data object Updated : SquadEventSubmitOutcome()
}
