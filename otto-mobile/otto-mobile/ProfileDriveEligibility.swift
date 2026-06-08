import Foundation

enum ProfileDriveEligibility {
    /// Filters legacy auto-sharing drive records from profile lists while keeping intentional saves.
    static func isEligibleForProfileList(_ drive: DriveDTO) -> Bool {
        if drive.route != nil {
            return true
        }
        if drive.sharingAudience == "onlyMe" {
            return true
        }
        if drive.pointsCount >= 2 {
            return true
        }
        if drive.sharingAudience == "circles" {
            return false
        }
        return true
    }
}
