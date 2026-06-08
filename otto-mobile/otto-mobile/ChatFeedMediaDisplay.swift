import CoreGraphics
import UIKit

/// Shared sizing for chat feed photos and videos: full bubble width, natural aspect height, cap then center-crop.
enum ChatFeedMediaDisplay {
    private static let legacyMaxDisplayHeight: CGFloat = 280

    /// Tall enough for typical 3:4 phone portraits at bubble width; still caps very long panos.
    static func maxHeight(screenHeight: CGFloat? = nil) -> CGFloat {
        let height = screenHeight ?? currentScreenHeight()
        return min(480, max(360, height * 0.52))
    }

    /// Previous fixed thumbnail floor — used for landscape photos and unknown-dimension placeholders.
    static func minDisplayHeight(containerWidth: CGFloat) -> CGFloat {
        min(containerWidth * 0.75, legacyMaxDisplayHeight)
    }

    /// Pixel dimensions as rendered on screen (accounts for EXIF orientation tags).
    static func displayPixelSize(for image: UIImage) -> CGSize {
        guard let cgImage = image.cgImage else {
            return CGSize(
                width: max(image.size.width, 0.1) * image.scale,
                height: max(image.size.height, 0.1) * image.scale
            )
        }
        let pixelWidth = CGFloat(cgImage.width)
        let pixelHeight = CGFloat(cgImage.height)
        switch image.imageOrientation {
        case .left, .leftMirrored, .right, .rightMirrored:
            return CGSize(width: pixelHeight, height: pixelWidth)
        default:
            return CGSize(width: pixelWidth, height: pixelHeight)
        }
    }

    static func displayHeight(
        containerWidth: CGFloat,
        sourceWidth: CGFloat?,
        sourceHeight: CGFloat?,
        screenHeight: CGFloat? = nil
    ) -> CGFloat {
        let cap = maxHeight(screenHeight: screenHeight)
        guard let sourceWidth, let sourceHeight, sourceWidth > 0, sourceHeight > 0 else {
            return min(minDisplayHeight(containerWidth: containerWidth), cap)
        }

        let naturalHeight = containerWidth * (sourceHeight / sourceWidth)
        if sourceHeight > sourceWidth {
            return min(naturalHeight, cap)
        }
        let minHeight = minDisplayHeight(containerWidth: containerWidth)
        return min(max(naturalHeight, minHeight), cap)
    }

    static func cropsToMaxHeight(
        containerWidth: CGFloat,
        sourceWidth: CGFloat?,
        sourceHeight: CGFloat?,
        screenHeight: CGFloat? = nil
    ) -> Bool {
        guard let sourceWidth, let sourceHeight, sourceWidth > 0, sourceHeight > 0 else {
            return false
        }
        let naturalHeight = containerWidth * (sourceHeight / sourceWidth)
        let height = displayHeight(
            containerWidth: containerWidth,
            sourceWidth: sourceWidth,
            sourceHeight: sourceHeight,
            screenHeight: screenHeight
        )
        return abs(height - naturalHeight) > 0.5
    }

    private static func currentScreenHeight() -> CGFloat {
        let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
        return scenes.first?.screen.bounds.height ?? 844
    }
}
