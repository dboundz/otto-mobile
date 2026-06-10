package to.ottomot.driftd

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import to.ottomot.driftd.core.network.dto.CircleSharedGalleryItemDto
import to.ottomot.driftd.core.network.dto.CircleSharedItemsSummaryResponseDto
import to.ottomot.driftd.core.network.dto.CircleSharedItemsSummarySectionDto

private enum class SquadSharedFilter(val apiType: String?) {
    All(null),
    Photo("photo"),
    Video("video"),
    Route("route"),
    Place("place"),
    Link("link"),
}

@Composable
internal fun SquadSharedTab(
    circleId: String,
    isTabSelected: Boolean = true,
    canModerate: Boolean,
    onFetchSummary: suspend (String) -> Result<CircleSharedItemsSummaryResponseDto>,
    onFetchList: suspend (String, String, Int) -> Result<List<CircleSharedGalleryItemDto>>,
    onOpenPhoto: (List<CircleSharedGalleryItemDto>, Int) -> Unit,
    onOpenVideo: (CircleSharedGalleryItemDto) -> Unit,
    onOpenRoute: (CircleSharedGalleryItemDto) -> Unit,
    onOpenPlace: (CircleSharedGalleryItemDto) -> Unit,
    onOpenLink: (CircleSharedGalleryItemDto) -> Unit,
    onDeleteItem: suspend (CircleSharedGalleryItemDto) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedFilter by remember(circleId) { mutableStateOf(SquadSharedFilter.All) }
    var summary by remember(circleId) { mutableStateOf<CircleSharedItemsSummaryResponseDto?>(null) }
    var listItems by remember(circleId) { mutableStateOf<List<CircleSharedGalleryItemDto>>(emptyList()) }
    var isLoading by remember(circleId) { mutableStateOf(false) }
    var loadError by remember(circleId) { mutableStateOf<String?>(null) }
    var deleteConfirmItem by remember(circleId) { mutableStateOf<CircleSharedGalleryItemDto?>(null) }
    var scrollToTopToken by remember(circleId) { mutableIntStateOf(0) }
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

    suspend fun buildAllTabSummaryFallback(): CircleSharedItemsSummaryResponseDto =
        coroutineScope {
            val kinds =
                listOf(
                    SquadSharedFilter.Photo,
                    SquadSharedFilter.Video,
                    SquadSharedFilter.Route,
                    SquadSharedFilter.Place,
                    SquadSharedFilter.Link,
                )
            val sections =
                kinds
                    .map { kind ->
                        async {
                            val type = kind.apiType!!
                            val limit = previewLimit(kind)
                            val items =
                                onFetchList(circleId, type, limit).getOrElse { emptyList() }
                            val total = if (items.size >= limit) limit + 1 else items.size
                            type to CircleSharedItemsSummarySectionDto(total = total, items = items)
                        }
                    }.awaitAll()
                    .associate { it.first to it.second }
            CircleSharedItemsSummaryResponseDto(sections = sections, canModerate = canModerate)
        }

    fun summaryHasContent(summary: CircleSharedItemsSummaryResponseDto): Boolean =
        SquadSharedFilter.entries
            .filter { it != SquadSharedFilter.All }
            .any { kind ->
                val section = summary.sections[kind.apiType]
                val total = section?.total ?: 0
                val items = section?.items.orEmpty()
                total > 0 || items.isNotEmpty()
            }

    suspend fun reloadCurrentView() {
        isLoading = true
        loadError = null
        if (selectedFilter == SquadSharedFilter.All) {
            var fetchedSummary: CircleSharedItemsSummaryResponseDto? = null
            var summaryFetchFailed = false

            onFetchSummary(circleId).fold(
                onSuccess = { fetchedSummary = it },
                onFailure = { summaryFetchFailed = true },
            )

            if (fetchedSummary != null && summaryHasContent(fetchedSummary!!)) {
                summary = fetchedSummary
                listItems = emptyList()
            } else {
                runCatching { buildAllTabSummaryFallback() }
                    .fold(
                        onSuccess = { fallback ->
                            summary =
                                if (summaryHasContent(fallback)) {
                                    fallback
                                } else {
                                    fetchedSummary ?: fallback
                                }
                            listItems = emptyList()
                        },
                        onFailure = {
                            if (summaryFetchFailed) {
                                loadError = ctx.getString(R.string.squad_shared_load_error)
                            }
                            summary = fetchedSummary
                            listItems = emptyList()
                        },
                    )
            }
        } else {
            val type = selectedFilter.apiType
            if (type == null) {
                isLoading = false
                return
            }
            onFetchList(circleId, type, 50).fold(
                onSuccess = {
                    listItems = it
                    summary = null
                },
                onFailure = {
                    listItems = emptyList()
                    summary = null
                    loadError = ctx.getString(R.string.squad_shared_load_error)
                },
            )
        }
        isLoading = false
    }

    LaunchedEffect(circleId, isTabSelected) {
        if (isTabSelected) {
            reloadCurrentView()
        }
    }

    LaunchedEffect(selectedFilter, circleId, isTabSelected) {
        if (isTabSelected) {
            reloadCurrentView()
        }
    }

    LaunchedEffect(scrollToTopToken) {
        if (scrollToTopToken > 0) {
            scrollState.animateScrollTo(0)
        }
    }

    val isEmptyForCurrentFilter =
        if (selectedFilter == SquadSharedFilter.All) {
            val s = summary
            if (s == null) !isLoading
            else {
                SquadSharedFilter.entries
                    .filter { it != SquadSharedFilter.All }
                    .all { kind ->
                        val section = s.sections[kind.apiType]
                        val total = section?.total ?: 0
                        val items = section?.items.orEmpty()
                        total == 0 && items.isEmpty()
                    }
            }
        } else {
            listItems.isEmpty() && !isLoading
        }

    Column(
        modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        SharedFilterChips(
            selectedFilter = selectedFilter,
            onSelect = { filter ->
                selectedFilter = filter
                scrollToTopToken++
            },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp, bottom = 12.dp),
        )

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            when {
                isLoading && summary == null && listItems.isEmpty() && loadError == null ->
                    SharedTabSkeleton(filter = selectedFilter)

                loadError != null && summary == null && listItems.isEmpty() ->
                    OttoEmptyState(
                        title = loadError.orEmpty(),
                        icon = Icons.Outlined.Image,
                        actionLabel = stringResource(R.string.retry),
                        onAction = { scope.launch { reloadCurrentView() } },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(320.dp),
                    )

                isEmptyForCurrentFilter ->
                    SharedEmptyState(filter = selectedFilter)

                selectedFilter == SquadSharedFilter.All && summary != null ->
                    SharedAllSectionsView(
                        summary = summary!!,
                        canModerate = canModerate,
                        onSeeAll = { filter ->
                            selectedFilter = filter
                            scrollToTopToken++
                        },
                        onOpenPhoto = onOpenPhoto,
                        onOpenVideo = onOpenVideo,
                        onOpenRoute = onOpenRoute,
                        onOpenPlace = onOpenPlace,
                        onOpenLink = onOpenLink,
                        onLongPress = { item ->
                            if (canModerate) deleteConfirmItem = item
                        },
                    )

                else ->
                    SharedFilteredListView(
                        filter = selectedFilter,
                        items = listItems,
                        canModerate = canModerate,
                        onOpenPhoto = onOpenPhoto,
                        onOpenVideo = onOpenVideo,
                        onOpenRoute = onOpenRoute,
                        onOpenPlace = onOpenPlace,
                        onOpenLink = onOpenLink,
                        onLongPress = { item ->
                            if (canModerate) deleteConfirmItem = item
                        },
                    )
            }
        }
    }

    ChatDeleteSystemAlertEffect(
        victimMessageId = deleteConfirmItem?.messageId,
        title = stringResource(R.string.chat_delete_message_confirm_title),
        message = stringResource(R.string.chat_delete_message_confirm_body),
        deleteLabel = stringResource(R.string.chat_delete_message),
        cancelLabel = stringResource(R.string.chat_cancel_edit),
        onDelete = {
            val item = deleteConfirmItem ?: return@ChatDeleteSystemAlertEffect
            scope.launch {
                onDeleteItem(item)
                deleteConfirmItem = null
                reloadCurrentView()
            }
        },
        onDismiss = { deleteConfirmItem = null },
    )
}

@Composable
private fun SharedFilterChips(
    selectedFilter: SquadSharedFilter,
    onSelect: (SquadSharedFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SquadSharedFilter.entries.forEach { filter ->
            val selected = filter == selectedFilter
            Text(
                text = sharedFilterTitle(filter),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = if (selected) Color.White else Color.White.copy(alpha = 0.82f),
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (selected) Color(0xFF7B3DFF) else Color.White.copy(alpha = 0.1f))
                        .clickable { onSelect(filter) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun sharedFilterTitle(filter: SquadSharedFilter): String =
    when (filter) {
        SquadSharedFilter.All -> stringResource(R.string.squad_shared_filter_all)
        SquadSharedFilter.Photo -> stringResource(R.string.squad_shared_filter_photos)
        SquadSharedFilter.Video -> stringResource(R.string.squad_shared_filter_videos)
        SquadSharedFilter.Route -> stringResource(R.string.squad_shared_filter_routes)
        SquadSharedFilter.Place -> stringResource(R.string.squad_shared_filter_places)
        SquadSharedFilter.Link -> stringResource(R.string.squad_shared_filter_links)
    }

@Composable
private fun SharedEmptyState(filter: SquadSharedFilter) {
    val title =
        if (filter == SquadSharedFilter.All) {
            stringResource(R.string.squad_shared_empty_title)
        } else {
            stringResource(R.string.squad_shared_empty_filter_title, sharedFilterTitle(filter))
        }
    val body =
        if (filter == SquadSharedFilter.All) {
            stringResource(R.string.squad_shared_empty_message)
        } else {
            null
        }
    OttoEmptyState(
        title = title,
        body = body,
        icon = Icons.Outlined.Image,
        modifier =
            Modifier
                .fillMaxWidth()
                .height(320.dp),
    )
}

@Composable
private fun SharedTabSkeleton(filter: SquadSharedFilter) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (filter == SquadSharedFilter.All) {
            Box(Modifier.fillMaxWidth().height(72.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.08f)))
            Box(Modifier.fillMaxWidth().height(96.dp).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.06f)))
            Box(Modifier.fillMaxWidth().height(72.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.08f)))
        } else {
            Box(Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.06f)))
            Box(Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.06f)))
        }
    }
}

@Composable
private fun SharedAllSectionsView(
    summary: CircleSharedItemsSummaryResponseDto,
    canModerate: Boolean,
    onSeeAll: (SquadSharedFilter) -> Unit,
    onOpenPhoto: (List<CircleSharedGalleryItemDto>, Int) -> Unit,
    onOpenVideo: (CircleSharedGalleryItemDto) -> Unit,
    onOpenRoute: (CircleSharedGalleryItemDto) -> Unit,
    onOpenPlace: (CircleSharedGalleryItemDto) -> Unit,
    onOpenLink: (CircleSharedGalleryItemDto) -> Unit,
    onLongPress: (CircleSharedGalleryItemDto) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        SharedSectionBlock(
            kind = SquadSharedFilter.Photo,
            summary = summary,
            canModerate = canModerate,
            onSeeAll = onSeeAll,
            onOpenPhoto = onOpenPhoto,
            onOpenVideo = onOpenVideo,
            onOpenRoute = onOpenRoute,
            onOpenPlace = onOpenPlace,
            onOpenLink = onOpenLink,
            onLongPress = onLongPress,
        )
        SharedSectionBlock(
            kind = SquadSharedFilter.Route,
            summary = summary,
            canModerate = canModerate,
            onSeeAll = onSeeAll,
            onOpenPhoto = onOpenPhoto,
            onOpenVideo = onOpenVideo,
            onOpenRoute = onOpenRoute,
            onOpenPlace = onOpenPlace,
            onOpenLink = onOpenLink,
            onLongPress = onLongPress,
        )
        SharedSectionBlock(
            kind = SquadSharedFilter.Video,
            summary = summary,
            canModerate = canModerate,
            onSeeAll = onSeeAll,
            onOpenPhoto = onOpenPhoto,
            onOpenVideo = onOpenVideo,
            onOpenRoute = onOpenRoute,
            onOpenPlace = onOpenPlace,
            onOpenLink = onOpenLink,
            onLongPress = onLongPress,
        )
        SharedSectionBlock(
            kind = SquadSharedFilter.Place,
            summary = summary,
            canModerate = canModerate,
            onSeeAll = onSeeAll,
            onOpenPhoto = onOpenPhoto,
            onOpenVideo = onOpenVideo,
            onOpenRoute = onOpenRoute,
            onOpenPlace = onOpenPlace,
            onOpenLink = onOpenLink,
            onLongPress = onLongPress,
        )
        SharedSectionBlock(
            kind = SquadSharedFilter.Link,
            summary = summary,
            canModerate = canModerate,
            onSeeAll = onSeeAll,
            onOpenPhoto = onOpenPhoto,
            onOpenVideo = onOpenVideo,
            onOpenRoute = onOpenRoute,
            onOpenPlace = onOpenPlace,
            onOpenLink = onOpenLink,
            onLongPress = onLongPress,
        )
    }
}

@Composable
private fun SharedSectionBlock(
    kind: SquadSharedFilter,
    summary: CircleSharedItemsSummaryResponseDto,
    canModerate: Boolean,
    onSeeAll: (SquadSharedFilter) -> Unit,
    onOpenPhoto: (List<CircleSharedGalleryItemDto>, Int) -> Unit,
    onOpenVideo: (CircleSharedGalleryItemDto) -> Unit,
    onOpenRoute: (CircleSharedGalleryItemDto) -> Unit,
    onOpenPlace: (CircleSharedGalleryItemDto) -> Unit,
    onOpenLink: (CircleSharedGalleryItemDto) -> Unit,
    onLongPress: (CircleSharedGalleryItemDto) -> Unit,
) {
    val section = summary.sections[kind.apiType]
    val total = section?.total ?: 0
    val items = section?.items.orEmpty()
    if (total <= 0 && items.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SharedSectionHeader(
            kind = kind,
            showSeeAll = total > previewLimit(kind),
            onSeeAll = { onSeeAll(kind) },
        )
        SharedSectionPreview(
            kind = kind,
            items = items,
            canModerate = canModerate,
            onOpenPhoto = onOpenPhoto,
            onOpenVideo = onOpenVideo,
            onOpenRoute = onOpenRoute,
            onOpenPlace = onOpenPlace,
            onOpenLink = onOpenLink,
            onLongPress = onLongPress,
        )
    }
}

@Composable
private fun SharedSectionHeader(
    kind: SquadSharedFilter,
    showSeeAll: Boolean,
    onSeeAll: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            sharedSectionIcon(kind),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            sharedFilterTitle(kind),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = Color.White,
            modifier = Modifier.weight(1f),
        )
        if (showSeeAll) {
            Text(
                stringResource(R.string.squad_shared_see_all),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = Color(0xFF7B3DFF),
                modifier = Modifier.clickable { onSeeAll() },
            )
        }
    }
}

@Composable
private fun SharedSectionPreview(
    kind: SquadSharedFilter,
    items: List<CircleSharedGalleryItemDto>,
    canModerate: Boolean,
    onOpenPhoto: (List<CircleSharedGalleryItemDto>, Int) -> Unit,
    onOpenVideo: (CircleSharedGalleryItemDto) -> Unit,
    onOpenRoute: (CircleSharedGalleryItemDto) -> Unit,
    onOpenPlace: (CircleSharedGalleryItemDto) -> Unit,
    onOpenLink: (CircleSharedGalleryItemDto) -> Unit,
    onLongPress: (CircleSharedGalleryItemDto) -> Unit,
) {
    when (kind) {
        SquadSharedFilter.Photo -> {
            val preview = items.take(4)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                preview.forEachIndexed { index, item ->
                    SharedPhotoThumbnail(
                        item = item,
                        modifier = Modifier.weight(1f),
                        onClick = { onOpenPhoto(preview, index) },
                        onLongClick = { onLongPress(item) },
                    )
                }
                repeat(4 - preview.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }

        SquadSharedFilter.Video -> {
            val preview = items.take(4)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                preview.forEach { item ->
                    SharedVideoThumbnail(
                        item = item,
                        modifier = Modifier.weight(1f),
                        onClick = { onOpenVideo(item) },
                        onLongClick = { onLongPress(item) },
                    )
                }
                repeat(4 - preview.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }

        SquadSharedFilter.Route, SquadSharedFilter.Place, SquadSharedFilter.Link ->
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items.forEach { item ->
                    SharedEntityCard(
                        item = item,
                        onClick = { openSharedItem(item, items, onOpenPhoto, onOpenVideo, onOpenRoute, onOpenPlace, onOpenLink) },
                        onLongClick = { onLongPress(item) },
                    )
                }
            }

        SquadSharedFilter.All -> Unit
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SharedFilteredListView(
    filter: SquadSharedFilter,
    items: List<CircleSharedGalleryItemDto>,
    canModerate: Boolean,
    onOpenPhoto: (List<CircleSharedGalleryItemDto>, Int) -> Unit,
    onOpenVideo: (CircleSharedGalleryItemDto) -> Unit,
    onOpenRoute: (CircleSharedGalleryItemDto) -> Unit,
    onOpenPlace: (CircleSharedGalleryItemDto) -> Unit,
    onOpenLink: (CircleSharedGalleryItemDto) -> Unit,
    onLongPress: (CircleSharedGalleryItemDto) -> Unit,
) {
    when (filter) {
        SquadSharedFilter.Photo ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items.chunked(3).forEach { rowItems ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowItems.forEachIndexed { rowIndex, item ->
                            val index = items.indexOfFirst { it.messageId == item.messageId }
                            SharedPhotoThumbnail(
                                item = item,
                                modifier = Modifier.weight(1f),
                                onClick = { onOpenPhoto(items, index) },
                                onLongClick = { onLongPress(item) },
                            )
                        }
                        repeat(3 - rowItems.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }

        SquadSharedFilter.Video ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items.chunked(3).forEach { rowItems ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowItems.forEach { item ->
                            SharedVideoThumbnail(
                                item = item,
                                modifier = Modifier.weight(1f),
                                onClick = { onOpenVideo(item) },
                                onLongClick = { onLongPress(item) },
                            )
                        }
                        repeat(3 - rowItems.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }

        SquadSharedFilter.Route, SquadSharedFilter.Place, SquadSharedFilter.Link ->
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items.forEach { item ->
                    SharedEntityCard(
                        item = item,
                        onClick = { openSharedItem(item, items, onOpenPhoto, onOpenVideo, onOpenRoute, onOpenPlace, onOpenLink) },
                        onLongClick = { onLongPress(item) },
                    )
                }
            }

        SquadSharedFilter.All -> Unit
    }
}

private fun openSharedItem(
    item: CircleSharedGalleryItemDto,
    listItems: List<CircleSharedGalleryItemDto>,
    onOpenPhoto: (List<CircleSharedGalleryItemDto>, Int) -> Unit,
    onOpenVideo: (CircleSharedGalleryItemDto) -> Unit,
    onOpenRoute: (CircleSharedGalleryItemDto) -> Unit,
    onOpenPlace: (CircleSharedGalleryItemDto) -> Unit,
    onOpenLink: (CircleSharedGalleryItemDto) -> Unit,
) {
    when (item.sharedKind) {
        "photo" -> {
            val index = listItems.indexOfFirst { it.messageId == item.messageId }
            if (index >= 0) onOpenPhoto(listItems, index) else onOpenPhoto(listOf(item), 0)
        }
        "video" -> onOpenVideo(item)
        "route" -> onOpenRoute(item)
        "place" -> onOpenPlace(item)
        "link" -> onOpenLink(item)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SharedPhotoThumbnail(
    item: CircleSharedGalleryItemDto,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    SharedSquareMediaTile(
        modifier = modifier,
        onClick = onClick,
        onLongClick = onLongClick,
    ) {
        val ctx = LocalContext.current
        AsyncImage(
            model = ottoImageRequest(ctx, item.previewUrl),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SharedVideoThumbnail(
    item: CircleSharedGalleryItemDto,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    SharedSquareMediaTile(
        modifier = modifier,
        onClick = onClick,
        onLongClick = onLongClick,
    ) {
        Box(Modifier.fillMaxSize()) {
            val ctx = LocalContext.current
            AsyncImage(
                model = ottoImageRequest(ctx, item.previewUrl),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Icon(
                Icons.Outlined.Videocam,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.align(Alignment.Center).size(28.dp),
            )
            item.videoDurationSeconds?.takeIf { it > 0 }?.let { seconds ->
                Text(
                    formatVideoDuration(seconds),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color.Black.copy(alpha = 0.72f))
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SharedSquareMediaTile(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    Box(
        modifier =
            modifier
                .aspectRatio(1f)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.08f))
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongClick()
                    },
                ),
    ) {
        content()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SharedEntityCard(
    item: CircleSharedGalleryItemDto,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val accent = mapAccentComposeColor(item.sender?.mapAccentKey)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.06f))
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongClick()
                    },
                )
                .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(72.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (item.sharedKind == "route") {
                SavedRouteListIcon(size = 48.dp)
            } else {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (!item.previewUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = ottoImageRequest(ctx, item.previewUrl),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Icon(
                            sharedEntityFallbackIcon(item),
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.5f),
                        )
                    }
                }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                item.title ?: "Shared",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            item.subtitle?.trim()?.takeIf { it.isNotEmpty() }?.let { subtitle ->
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.62f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                item.sender?.displayName?.trim()?.takeIf { it.isNotEmpty() }?.let { name ->
                    Text(
                        stringResource(R.string.squad_shared_by_format, name),
                        style = MaterialTheme.typography.labelSmall,
                        color = accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                } ?: Spacer(Modifier.weight(1f))
                Text(
                    formatSharedRelativeDate(item.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.45f),
                )
            }
        }
    }
}

private fun previewLimit(kind: SquadSharedFilter): Int =
    when (kind) {
        SquadSharedFilter.Photo, SquadSharedFilter.Video -> 4
        SquadSharedFilter.Route, SquadSharedFilter.Place, SquadSharedFilter.Link -> 2
        SquadSharedFilter.All -> 0
    }

private fun sharedSectionIcon(kind: SquadSharedFilter): ImageVector =
    when (kind) {
        SquadSharedFilter.Photo -> Icons.Outlined.Photo
        SquadSharedFilter.Video -> Icons.Outlined.Videocam
        SquadSharedFilter.Route -> Icons.Outlined.Map
        SquadSharedFilter.Place -> Icons.Outlined.Place
        SquadSharedFilter.Link -> Icons.Outlined.Link
        SquadSharedFilter.All -> Icons.Outlined.Image
    }

private fun sharedEntityFallbackIcon(item: CircleSharedGalleryItemDto): ImageVector =
    when (item.sharedKind) {
        "route" -> Icons.Outlined.Map
        "place" -> Icons.Outlined.Place
        "link" -> Icons.Outlined.Link
        else -> Icons.Outlined.Photo
    }

private fun formatVideoDuration(seconds: Double): String {
    val total = seconds.roundToInt().coerceAtLeast(0)
    val m = total / 60
    val s = total % 60
    return if (m > 0) "%d:%02d".format(m, s) else "0:%02d".format(s)
}


private fun formatSharedRelativeDate(raw: String?): String {
    if (raw.isNullOrBlank()) return ""
    val instant =
        runCatching { Instant.parse(raw) }
            .recoverCatching {
                Instant.parse(
                    raw.replace(" ", "T").let { patched ->
                        if (patched.endsWith("Z")) patched else "${patched}Z"
                    },
                )
            }
            .getOrNull()
            ?: return ""
    val minutes = ChronoUnit.MINUTES.between(instant, Instant.now()).coerceAtLeast(0)
    return when {
        minutes < 1 -> "now"
        minutes < 60 -> "${minutes}m"
        minutes < 60 * 24 -> "${minutes / 60}h"
        minutes < 60 * 24 * 7 -> "${minutes / (60 * 24)}d"
        else -> "${minutes / (60 * 24 * 7)}w"
    }
}
