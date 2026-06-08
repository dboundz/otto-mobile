import Foundation

/// Display titles for drives — drive names are independent of route names.
enum DriveDisplayNaming {
    private static func isGenericDisplayName(_ name: String) -> Bool {
        let normalized = name.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        return normalized.isEmpty || normalized == "route drive" || normalized == "route drive session"
    }

    /// Default when starting a route drive (e.g. "Ocean Pass" → "Ocean Pass Drive").
    static func defaultTitle(fromRouteName routeName: String?) -> String {
        let route = routeName?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if route.isEmpty || isGenericDisplayName(route) { return "Route Drive" }
        if route.lowercased().hasSuffix(" drive") { return route }
        return "\(route) Drive"
    }

    static func listTitle(routeName: String?, driveTitle: String?) -> String {
        let snapshotRoute = routeName?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        let raw = driveTitle?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if !raw.isEmpty, !isGenericDisplayName(raw) {
            if !snapshotRoute.isEmpty,
               raw == snapshotRoute,
               !raw.lowercased().hasSuffix(" drive") {
                return defaultTitle(fromRouteName: snapshotRoute)
            }
            return raw
        }
        return defaultTitle(fromRouteName: snapshotRoute.isEmpty ? routeName : snapshotRoute)
    }
}
