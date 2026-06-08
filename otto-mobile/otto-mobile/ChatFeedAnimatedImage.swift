import SDWebImageSwiftUI
import SwiftUI
import UIKit

/// Animated or remote image for chat attachments (KLIPY GIF/WebP and direct image URLs).
struct ChatFeedAnimatedImage: View {
    let url: URL
    let width: CGFloat
    let displayHeight: CGFloat
    var imageCacheKeyPrefix: String? = nil
    var onImageDecoded: ((CGSize) -> Void)? = nil

    var body: some View {
        WebImage(url: url) { image in
            image
                .resizable()
                .scaledToFill()
        } placeholder: {
            Color.white.opacity(0.08)
                .overlay {
                    ProgressView().tint(.purple)
                }
        }
        .onSuccess { image, _, _ in
            let size = image.size
            guard size.width > 0, size.height > 0 else { return }
            onImageDecoded?(size)
        }
        .frame(width: width, height: displayHeight)
        .clipped()
    }
}
