package to.ottomot.driftd

import to.ottomot.driftd.core.network.dto.DriveDto

/** Display titles for drives — drive names are independent of route names (iOS `DriveDisplayNaming`). */
object DriveDisplayNaming {
    private fun isGenericDisplayName(name: String): Boolean {
        val normalized = name.trim().lowercase()
        return normalized.isEmpty() || normalized == "route drive" || normalized == "route drive session"
    }

    fun defaultTitle(fromRouteName: String?): String {
        val route = fromRouteName?.trim().orEmpty()
        if (route.isEmpty() || isGenericDisplayName(route)) return "Route Drive"
        if (route.lowercase().endsWith(" drive")) return route
        return "$route Drive"
    }

    fun listTitle(routeName: String?, driveTitle: String?): String {
        val snapshotRoute = routeName?.trim().orEmpty()
        val raw = driveTitle?.trim().orEmpty()
        if (raw.isNotEmpty() && !isGenericDisplayName(raw)) {
            if (
                snapshotRoute.isNotEmpty() &&
                raw == snapshotRoute &&
                !raw.lowercase().endsWith(" drive")
            ) {
                return defaultTitle(snapshotRoute)
            }
            return raw
        }
        return defaultTitle(snapshotRoute.ifEmpty { routeName })
    }

    fun listTitle(drive: DriveDto): String =
        listTitle(routeName = drive.route?.name, driveTitle = drive.title)

    fun squadChatListTitle(driveTitle: String?): String =
        listTitle(routeName = null, driveTitle = driveTitle)
}
