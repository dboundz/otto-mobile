package to.ottomot.driftd

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun KlipyGifPickerSheet(
    visible: Boolean,
    customerId: String,
    onSelect: (KlipyGifSelection, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var searchText by remember { mutableStateOf("") }
    var items by remember { mutableStateOf<List<KlipyGifItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var page by remember { mutableIntStateOf(1) }
    var hasMore by remember { mutableStateOf(true) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    val locale = remember { KlipyAPIClient.defaultLocale() }
    val normalizedCustomerId = remember(customerId) { customerId.trim().ifBlank { "otto-anonymous" } }
    val genericErrorMessage = stringResource(R.string.klipy_picker_error)

    fun loadPage(targetPage: Int, append: Boolean) {
        if (append && (!hasMore || isLoading || isLoadingMore)) return
        searchJob?.cancel()
        scope.launch {
            if (append) {
                isLoadingMore = true
            } else {
                isLoading = true
                errorMessage = null
                hasMore = true
            }
            val query = searchText.trim()
            try {
                val result =
                    if (query.isEmpty()) {
                        KlipyAPIClient.fetchTrending(
                            customerId = normalizedCustomerId,
                            locale = locale,
                            page = targetPage,
                        )
                    } else {
                        KlipyAPIClient.search(
                            query = query,
                            customerId = normalizedCustomerId,
                            locale = locale,
                            page = targetPage,
                        )
                    }
                items = if (append) items + result.items else result.items
                hasMore = result.hasMore
                page = targetPage + 1
                errorMessage = null
            } catch (t: Throwable) {
                if (!append) {
                    items = emptyList()
                    errorMessage = t.message ?: genericErrorMessage
                } else {
                    hasMore = false
                }
            } finally {
                isLoading = false
                isLoadingMore = false
            }
        }
    }

    LaunchedEffect(Unit) {
        loadPage(targetPage = 1, append = false)
    }

    LaunchedEffect(searchText) {
        searchJob?.cancel()
        searchJob =
            scope.launch {
                delay(350)
                loadPage(targetPage = 1, append = false)
            }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.Black,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 480.dp)
                    .padding(bottom = 16.dp),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.klipy_picker_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.klipy_picker_cancel))
                }
            }

            TextField(
                value = searchText,
                onValueChange = { searchText = it },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(12.dp)),
                leadingIcon = {
                    Icon(Icons.Outlined.Search, contentDescription = null, tint = Color.White.copy(alpha = 0.55f))
                },
                trailingIcon = {
                    if (searchText.isNotEmpty()) {
                        IconButton(onClick = { searchText = "" }) {
                            Icon(Icons.Outlined.Close, contentDescription = null, tint = Color.White.copy(alpha = 0.55f))
                        }
                    }
                },
                placeholder = {
                    Text(stringResource(R.string.klipy_picker_search_placeholder), color = Color.White.copy(alpha = 0.55f))
                },
                singleLine = true,
                colors =
                    TextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color.White.copy(alpha = 0.08f),
                        unfocusedContainerColor = Color.White.copy(alpha = 0.08f),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
            )

            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    isLoading && items.isEmpty() -> CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    errorMessage != null && items.isEmpty() -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(horizontal = 24.dp),
                        ) {
                            Text(
                                text = errorMessage.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.75f),
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(12.dp))
                            TextButton(onClick = { loadPage(targetPage = 1, append = false) }) {
                                Text(stringResource(R.string.klipy_picker_retry))
                            }
                        }
                    }
                    items.isEmpty() -> {
                        Text(
                            text = stringResource(R.string.klipy_picker_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.65f),
                            textAlign = TextAlign.Center,
                        )
                    }
                    else -> {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                                if (index == items.lastIndex) {
                                    LaunchedEffect(item.id, hasMore) {
                                        loadPage(targetPage = page, append = true)
                                    }
                                }
                                KlipyGifGridCell(
                                    item = item,
                                    onClick = {
                                        val query = searchText.trim().takeIf { it.isNotEmpty() }
                                        onSelect(item.selection, query)
                                    },
                                )
                            }
                            if (isLoadingMore) {
                                item {
                                    Box(
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 12.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KlipyGifGridCell(
    item: KlipyGifItem,
    onClick: () -> Unit,
) {
    val ratio = (item.width.toFloat() / item.height.coerceAtLeast(1).toFloat()).coerceAtLeast(0.5f)
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .aspectRatio(ratio)
                .heightIn(min = 120.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.08f))
                .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = item.previewUrl,
            contentDescription = item.title.takeIf { it.isNotBlank() },
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
