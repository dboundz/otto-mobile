package to.ottomot.driftd

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import to.ottomot.driftd.core.network.dto.SavedRouteDto
import to.ottomot.driftd.ui.insets.OttoWindowInsets.ottoBottomSheetContent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RoutesMenuSheet(
    sheetState: SheetState,
    routes: List<SavedRouteDto>,
    myUserId: String?,
    onDismiss: () -> Unit,
    onCreateRoute: () -> Unit,
    onSelectRoute: (SavedRouteDto) -> Unit,
    onEditRoute: (SavedRouteDto) -> Unit,
    onDeleteRoute: (String) -> Unit,
    onRenameRoute: suspend (SavedRouteDto, String) -> Boolean,
    onShareRoute: (SavedRouteDto) -> Unit = {},
) {
    val uid = myUserId?.trim().orEmpty()
    val ownedRoutes = remember(routes, uid) { routes.filter { ottoUserIdsEqual(it.createdByUserId, uid) } }
    val sharedRoutes = remember(routes, uid) { routes.filter { !ottoUserIdsEqual(it.createdByUserId, uid) } }

    var routeToRename by remember { mutableStateOf<SavedRouteDto?>(null) }
    var routeNameDraft by remember { mutableStateOf("") }
    var routeRenameSaving by remember { mutableStateOf(false) }
    var routeToDelete by remember { mutableStateOf<SavedRouteDto?>(null) }
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.Black,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
                .ottoBottomSheetContent(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.map_routes_menu_title),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.map_layers_done), color = Color(0xFF7B3DFF))
                }
            }

            CreateRouteListRow(onClick = {
                onDismiss()
                onCreateRoute()
            })

            if (routes.isEmpty()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SavedRouteListIcon(size = 56.dp)
                    Text(
                        stringResource(R.string.map_routes_empty_title),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White,
                    )
                    Text(
                        stringResource(R.string.map_routes_empty_message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.62f),
                    )
                }
            } else {
                if (ownedRoutes.isNotEmpty()) {
                    RoutesMenuSection(
                        title = stringResource(R.string.map_routes_my_routes),
                        routes = ownedRoutes,
                        canManage = true,
                        onSelectRoute = {
                            onDismiss()
                            onSelectRoute(it)
                        },
                        onEditRoute = {
                            onDismiss()
                            onEditRoute(it)
                        },
                        onRename = { route ->
                            routeToRename = route
                            routeNameDraft = route.name.trim()
                        },
                        onDelete = { routeToDelete = it },
                        onShare = onShareRoute,
                    )
                }
                if (sharedRoutes.isNotEmpty()) {
                    RoutesMenuSection(
                        title = stringResource(R.string.map_routes_shared_with_you),
                        routes = sharedRoutes,
                        canManage = false,
                        onSelectRoute = {
                            onDismiss()
                            onSelectRoute(it)
                        },
                        onEditRoute = {},
                        onRename = {},
                        onDelete = {},
                    )
                }
            }
        }
    }

    routeToRename?.let { route ->
        AlertDialog(
            onDismissRequest = {
                if (!routeRenameSaving) routeToRename = null
            },
            title = { Text(stringResource(R.string.profile_rename_route_title)) },
            text = {
                OutlinedTextField(
                    value = routeNameDraft,
                    onValueChange = { routeNameDraft = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.profile_rename_route_hint)) },
                    singleLine = true,
                    enabled = !routeRenameSaving,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = routeNameDraft.trim()
                        if (trimmed.isEmpty()) return@TextButton
                        routeRenameSaving = true
                        scope.launch {
                            onRenameRoute(route, trimmed)
                            routeRenameSaving = false
                            routeToRename = null
                        }
                    },
                    enabled = routeNameDraft.trim().isNotEmpty() && !routeRenameSaving,
                ) {
                    Text(stringResource(R.string.places_rename_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { routeToRename = null },
                    enabled = !routeRenameSaving,
                ) {
                    Text(stringResource(R.string.settings_cancel))
                }
            },
        )
    }

    routeToDelete?.let { route ->
        AlertDialog(
            onDismissRequest = { routeToDelete = null },
            title = { Text(stringResource(R.string.profile_delete_route_title)) },
            text = { Text(stringResource(R.string.profile_delete_route_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteRoute(route.id)
                        routeToDelete = null
                    },
                ) {
                    Text(stringResource(R.string.profile_delete_route_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { routeToDelete = null }) {
                    Text(stringResource(R.string.settings_cancel))
                }
            },
        )
    }
}

@Composable
private fun RoutesMenuSection(
    title: String,
    routes: List<SavedRouteDto>,
    canManage: Boolean,
    onSelectRoute: (SavedRouteDto) -> Unit,
    onEditRoute: (SavedRouteDto) -> Unit,
    onRename: (SavedRouteDto) -> Unit,
    onDelete: (SavedRouteDto) -> Unit,
    onShare: (SavedRouteDto) -> Unit = {},
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = Color.White.copy(alpha = 0.56f),
        )
        Column(
            Modifier
                .fillMaxWidth()
                .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp)),
        ) {
            routes.forEachIndexed { index, route ->
                RoutesMenuRow(
                    route = route,
                    canManage = canManage,
                    onSelect = { onSelectRoute(route) },
                    onEdit = { onEditRoute(route) },
                    onRename = { onRename(route) },
                    onDelete = { onDelete(route) },
                    onShare = { onShare(route) },
                )
                if (index < routes.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 72.dp),
                        color = Color.White.copy(alpha = 0.08f),
                    )
                }
            }
        }
    }
}

@Composable
private fun RoutesMenuRow(
    route: SavedRouteDto,
    canManage: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit = {},
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .weight(1f)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onSelect,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SavedRouteListIcon(size = 48.dp)
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    route.name,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White,
                    maxLines = 1,
                )
                val distance = route.distanceMeters
                if (distance != null && distance > 0) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        formatRouteDistanceMiles(distance),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.58f),
                    )
                }
            }
        }
        if (canManage) {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(Icons.Outlined.MoreVert, contentDescription = null, tint = Color.White.copy(alpha = 0.72f))
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text("Edit") },
                    onClick = {
                        menuExpanded = false
                        onEdit()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.profile_list_rename)) },
                    onClick = {
                        menuExpanded = false
                        onRename()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.route_chat_share_action)) },
                    onClick = {
                        menuExpanded = false
                        onShare()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.drive_summary_delete)) },
                    onClick = {
                        menuExpanded = false
                        onDelete()
                    },
                )
            }
        }
    }
}

private fun formatRouteDistanceMiles(meters: Double): String {
    val miles = meters / 1609.344
    return if (miles >= 10) "%.0f mi".format(miles) else "%.1f mi".format(miles)
}
