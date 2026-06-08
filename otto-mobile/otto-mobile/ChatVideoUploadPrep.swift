import AVFoundation
import UIKit
import UniformTypeIdentifiers

/// Lightweight composer state after pick; full metadata/JPEG prep runs on Send only.
struct ChatPendingVideo: Equatable {
    let localVideoURL: URL
    let previewImage: UIImage?

    static func == (lhs: ChatPendingVideo, rhs: ChatPendingVideo) -> Bool {
        lhs.localVideoURL == rhs.localVideoURL && lhs.previewImage === rhs.previewImage
    }
}

struct ChatPreparedVideoUpload {
    let localVideoURL: URL
    let thumbnailJPEG: Data
    let thumbnailImage: UIImage
    let durationSeconds: Double
    let width: Int
    let height: Int
    let mimeType: String
    let fileSizeBytes: Int64
}

enum ChatVideoUploadPrepError: LocalizedError {
    case unreadable
    case tooLarge
    case tooLong
    case tooLargeAndTooLong
    case thumbnailFailed

    var errorDescription: String? {
        switch self {
        case .unreadable: return "Couldn't read that video."
        case .tooLarge: return "This video is too large. Chat videos must be 250MB or smaller."
        case .tooLong: return "This video is too long. Chat videos must be 60 seconds or shorter."
        case .tooLargeAndTooLong:
            return "This video is too large and too long. Chat videos must be 250MB or smaller and 60 seconds or shorter."
        case .thumbnailFailed: return "Couldn't prepare video thumbnail."
        }
    }
}

enum ChatVideoUploadPrep {
    private static let maxBytes: Int64 = 250 * 1024 * 1024
    private static let maxDurationSeconds: Double = 60
    private static let thumbnailMaxSide: CGFloat = 1600
    private static let thumbnailQuality: CGFloat = 0.84
    private static let composerPreviewMaxSide: CGFloat = 240

    /// Fast low-res frame for composer preview; does not validate size or produce upload JPEG.
    static func quickPreviewThumbnail(localVideoURL: URL) async -> UIImage? {
        let asset = AVURLAsset(url: localVideoURL)
        return try? await generateThumbnail(asset: asset, maxSide: composerPreviewMaxSide)
    }

    /// Validates file size and duration immediately after pick, before attaching to the composer.
    static func validateForPick(localVideoURL: URL) async throws {
        let attrs = try FileManager.default.attributesOfItem(atPath: localVideoURL.path)
        let size = (attrs[.size] as? NSNumber)?.int64Value ?? 0
        if size <= 0 { throw ChatVideoUploadPrepError.unreadable }

        let asset = AVURLAsset(url: localVideoURL)
        let duration = try await asset.load(.duration)
        let durationSeconds = max(0, CMTimeGetSeconds(duration))
        try throwIfLimitsExceeded(size: size, durationSeconds: durationSeconds)
    }

    static func prepare(localVideoURL: URL) async throws -> ChatPreparedVideoUpload {
        let attrs = try FileManager.default.attributesOfItem(atPath: localVideoURL.path)
        let size = (attrs[.size] as? NSNumber)?.int64Value ?? 0
        if size <= 0 { throw ChatVideoUploadPrepError.unreadable }

        let asset = AVURLAsset(url: localVideoURL)
        let duration = try await asset.load(.duration)
        let durationSeconds = max(0, CMTimeGetSeconds(duration))
        try throwIfLimitsExceeded(size: size, durationSeconds: durationSeconds)

        let tracks = try await asset.loadTracks(withMediaType: .video)
        guard let track = tracks.first else { throw ChatVideoUploadPrepError.unreadable }
        let naturalSize = try await track.load(.naturalSize)
        let transform = try await track.load(.preferredTransform)
        let transformed = naturalSize.applying(transform)
        let width = max(1, Int(abs(transformed.width.rounded())))
        let height = max(1, Int(abs(transformed.height.rounded())))

        let mimeType = mimeTypeForVideoURL(localVideoURL)
        guard let thumbnailImage = try await generateThumbnail(asset: asset, maxSide: thumbnailMaxSide) else {
            throw ChatVideoUploadPrepError.thumbnailFailed
        }
        guard let jpeg = jpegData(for: thumbnailImage) else {
            throw ChatVideoUploadPrepError.thumbnailFailed
        }

        return ChatPreparedVideoUpload(
            localVideoURL: localVideoURL,
            thumbnailJPEG: jpeg,
            thumbnailImage: thumbnailImage,
            durationSeconds: durationSeconds,
            width: width,
            height: height,
            mimeType: mimeType,
            fileSizeBytes: size
        )
    }

    private static func throwIfLimitsExceeded(size: Int64, durationSeconds: Double) throws {
        let tooLarge = size > maxBytes
        let tooLong = durationSeconds > maxDurationSeconds
        if tooLarge && tooLong {
            throw ChatVideoUploadPrepError.tooLargeAndTooLong
        } else if tooLarge {
            throw ChatVideoUploadPrepError.tooLarge
        } else if tooLong {
            throw ChatVideoUploadPrepError.tooLong
        }
    }

    private static func mimeTypeForVideoURL(_ url: URL) -> String {
        if let type = UTType(filenameExtension: url.pathExtension)?.preferredMIMEType {
            if type == "video/quicktime" || type == "video/mp4" { return type }
        }
        let ext = url.pathExtension.lowercased()
        if ext == "mov" { return "video/quicktime" }
        return "video/mp4"
    }

    private static func generateThumbnail(asset: AVAsset, maxSide: CGFloat) async throws -> UIImage? {
        let generator = AVAssetImageGenerator(asset: asset)
        generator.appliesPreferredTrackTransform = true
        generator.maximumSize = CGSize(width: maxSide, height: maxSide)
        let time = CMTime(seconds: 0, preferredTimescale: 600)
        if #available(iOS 18.0, *) {
            let (cgImage, _) = try await generator.image(at: time)
            return UIImage(cgImage: cgImage)
        }
        return try await withCheckedThrowingContinuation { continuation in
            generator.generateCGImagesAsynchronously(forTimes: [NSValue(time: time)]) { _, image, _, result, error in
                if result == .succeeded, let image {
                    continuation.resume(returning: UIImage(cgImage: image))
                } else if let error {
                    continuation.resume(throwing: error)
                } else {
                    continuation.resume(returning: nil)
                }
            }
        }
    }

    private static func jpegData(for image: UIImage) -> Data? {
        let maxSide = max(image.size.width, image.size.height)
        let scale = min(1, thumbnailMaxSide / max(maxSide, 1))
        let targetSize = CGSize(width: image.size.width * scale, height: image.size.height * scale)
        let renderer = UIGraphicsImageRenderer(size: targetSize)
        let scaled = renderer.image { _ in
            image.draw(in: CGRect(origin: .zero, size: targetSize))
        }
        return scaled.jpegData(compressionQuality: thumbnailQuality)
    }
}
