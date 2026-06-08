import Foundation

enum PhotoUploadLimits {
    /// Matches server multer cap across avatar, garage, squad, event banner, and chat photos.
    static let serverMaxBytes = 24 * 1024 * 1024
    /// Keep below the server cap, leaving room for multipart overhead.
    static let clientTargetMaxBytes = serverMaxBytes - 500_000
}
