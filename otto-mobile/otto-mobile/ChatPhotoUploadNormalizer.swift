import UIKit

enum ChatPhotoUploadNormalizer {
    private static let maxPixelDimension: CGFloat = 1600
    private static let targetMaxBytes = PhotoUploadLimits.clientTargetMaxBytes
    private static let qualities: [CGFloat] = [0.84, 0.78, 0.72, 0.66, 0.60, 0.54, 0.48]

    static func jpegData(for image: UIImage) -> Data? {
        let normalized = resizedImageIfNeeded(orientationNormalized(image))
        var smallest: Data?

        for quality in qualities {
            guard let data = normalized.jpegData(compressionQuality: quality) else { continue }
            smallest = data
            if data.count <= targetMaxBytes {
                return data
            }
        }

        return smallest
    }

    /// Draws the image upright so pixel dimensions match what the user sees (Photos/camera EXIF orientation).
    private static func orientationNormalized(_ image: UIImage) -> UIImage {
        guard image.imageOrientation != .up else { return image }
        let format = UIGraphicsImageRendererFormat()
        format.scale = image.scale
        format.opaque = false
        let renderer = UIGraphicsImageRenderer(size: image.size, format: format)
        return renderer.image { _ in
            image.draw(in: CGRect(origin: .zero, size: image.size))
        }
    }

    private static func resizedImageIfNeeded(_ image: UIImage) -> UIImage {
        let pixelSize = sourcePixelSize(for: image)
        let longest = max(pixelSize.width, pixelSize.height)
        guard longest > maxPixelDimension else {
            return image
        }

        let scale = maxPixelDimension / longest
        let targetSize = CGSize(
            width: max(1, floor(pixelSize.width * scale)),
            height: max(1, floor(pixelSize.height * scale))
        )

        let format = UIGraphicsImageRendererFormat.default()
        format.scale = 1
        format.opaque = true

        return UIGraphicsImageRenderer(size: targetSize, format: format).image { _ in
            image.draw(in: CGRect(origin: .zero, size: targetSize))
        }
    }

    private static func sourcePixelSize(for image: UIImage) -> CGSize {
        if let cgImage = image.cgImage {
            return CGSize(width: cgImage.width, height: cgImage.height)
        }
        return CGSize(width: image.size.width * image.scale, height: image.size.height * image.scale)
    }
}
