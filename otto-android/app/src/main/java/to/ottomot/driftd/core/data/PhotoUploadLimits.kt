package to.ottomot.driftd.core.data

/** Matches server multer cap across avatar, garage, squad, event banner, and chat photos. */
internal const val PhotoUploadMaxBytes: Int = 24 * 1024 * 1024

/** Slightly below [PhotoUploadMaxBytes] for multipart overhead (parity with iOS). */
internal const val PhotoUploadClientTargetMaxBytes: Int = PhotoUploadMaxBytes - 500_000
