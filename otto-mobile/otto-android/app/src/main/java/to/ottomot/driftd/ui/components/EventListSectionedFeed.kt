package to.ottomot.driftd.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.ZoneId
import to.ottomot.driftd.R
import to.ottomot.driftd.core.event.EventListSectionGroup
import to.ottomot.driftd.core.event.EventListSectionId
import to.ottomot.driftd.core.event.EventListSectionedPresentation
import to.ottomot.driftd.core.event.eventListCalendarMonthTitle
import to.ottomot.driftd.core.event.groupEventsByListSection
import to.ottomot.driftd.core.network.dto.EventDto

@Composable
fun EventListSectionHeader(
    title: String,
    isFirst: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(Color.Black)
                .padding(top = if (isFirst) 0.dp else 14.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = 0.42f),
        )
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.12f)),
        )
    }
}

@Composable
fun eventListSectionTitle(
    section: EventListSectionId,
    now: Instant = Instant.now(),
    zone: ZoneId = ZoneId.systemDefault(),
): String {
    return when (section) {
        EventListSectionId.Today -> stringResource(R.string.events_section_today).uppercase()
        EventListSectionId.ThisWeek -> stringResource(R.string.events_section_this_week).uppercase()
        EventListSectionId.NextWeek -> stringResource(R.string.events_section_next_week).uppercase()
        EventListSectionId.ThisMonth -> stringResource(R.string.events_section_this_month).uppercase()
        is EventListSectionId.CalendarMonth ->
            eventListCalendarMonthTitle(section.year, section.month, now, zone, java.util.Locale.getDefault())
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EventListSectionedLazyColumn(
    events: List<EventDto>,
    presentation: EventListSectionedPresentation,
    onEventClick: (EventDto) -> Unit,
    modifier: Modifier = Modifier,
    horizontalPadding: androidx.compose.ui.unit.Dp = 16.dp,
    hasListHeader: Boolean = false,
    showFooter: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(bottom = 16.dp),
    header: @Composable () -> Unit = {},
    footer: @Composable () -> Unit = {},
    row: @Composable (event: EventDto, groupedInSection: Boolean) -> Unit,
) {
    val zone = remember { ZoneId.systemDefault() }
    val now = remember { Instant.now() }
    val groups = remember(events, now, zone) { groupEventsByListSection(events, now = now, zone = zone) }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = contentPadding,
    ) {
        if (hasListHeader) {
            item(key = "events-list-header") {
                Box(
                    Modifier
                        .padding(horizontal = horizontalPadding)
                        .padding(bottom = 8.dp),
                ) {
                    header()
                }
            }
        }

        for (index in groups.indices) {
            val group = groups[index]
            stickyHeader(key = "events-section-${group.id}") {
                EventListSectionHeader(
                    title = eventListSectionTitle(group.section, now = now, zone = zone),
                    isFirst = index == 0 && !hasListHeader,
                    modifier = Modifier.padding(horizontal = horizontalPadding),
                )
            }

            when (presentation) {
                EventListSectionedPresentation.Featured -> {
                    items(group.items, key = { it.id }) { event ->
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = horizontalPadding, vertical = 7.dp)
                                    .clickable { onEventClick(event) },
                        ) {
                            row(event, false)
                        }
                    }
                }

                EventListSectionedPresentation.Compact -> {
                    item(key = "events-group-${group.id}") {
                        EventListGroupedSectionContainer(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = horizontalPadding)
                                    .padding(bottom = 7.dp),
                        ) {
                            group.items.forEachIndexed { itemIndex, event ->
                                Box(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable { onEventClick(event) },
                                ) {
                                    row(event, true)
                                }
                                if (itemIndex < group.items.lastIndex) {
                                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showFooter) {
            item(key = "events-list-footer") {
                Box(
                    Modifier
                        .padding(horizontal = horizontalPadding)
                        .padding(top = 8.dp),
                ) {
                    footer()
                }
            }
        }
    }
}

@Composable
private fun EventListGroupedSectionContainer(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.055f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            content()
        }
    }
}
