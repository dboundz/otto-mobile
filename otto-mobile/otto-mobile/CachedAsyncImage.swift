import CryptoKit
import ImageIO
import os
import SwiftUI
import UIKit

enum CachedAsyncImagePhase {
    case empty
    case success(Image)
    case failure(Error)
}

/// Stable storage identity for `RemoteImageCache` / `CachedAsyncImage` when API URLs are presigned (query string changes on every API response).
enum RemoteImageStorageKey {
    static func stable(prefix: String, sourceUrlString: String) -> String {
        let canonical = sourceUrlString.split(separator: "?").first.map(String.init) ?? sourceUrlString
        return "\(prefix)|\(canonical)"
    }
}

struct CachedAsyncImage<Content: View>: View {
    let url: URL?
    /// When set, memory/disk cache uses this stable key instead of the full URL string (needed for presigned URLs that change on every API response).
    var storageKey: String? = nil
    /// Optional decoded pixel bounds. Disk keeps original bytes, but memory stores a display-sized image variant for dense feeds.
    var targetPixelSize: CGSize? = nil
    /// Called on the main actor when a decoded image is available (memory hit or fresh load).
    var onImageDecoded: ((UIImage) -> Void)? = nil
    @ViewBuilder var content: (CachedAsyncImagePhase) -> Content

    @State private var phase: CachedAsyncImagePhase = .empty
    @State private var successfulLoadIdentity: String?

    /// Identity for `.task` reloads: when `storageKey` is set it is stable across presigned URL refreshes; otherwise follow the URL.
    private var loadIdentity: String {
        if let storageKey, !storageKey.isEmpty {
            return "\(storageKey)|\(Self.targetIdentity(for: targetPixelSize))"
        }
        return "\(url?.absoluteString ?? "")|\(Self.targetIdentity(for: targetPixelSize))"
    }

    var body: some View {
        content(phase)
            .task(id: loadIdentity) {
                await loadImage()
            }
    }

    @MainActor
    private func loadImage() async {
        let currentLoadIdentity = loadIdentity
        guard let url else {
            phase = .empty
            successfulLoadIdentity = nil
            return
        }

        if let cached = RemoteImageCache.shared.memoryCachedImage(for: url, storageKey: storageKey, targetPixelSize: targetPixelSize) {
            onImageDecoded?(cached)
            phase = .success(Image(uiImage: cached))
            successfulLoadIdentity = currentLoadIdentity
            return
        }

        if successfulLoadIdentity != currentLoadIdentity {
            phase = .empty
        }

        do {
            let image = try await RemoteImageCache.shared.image(for: url, storageKey: storageKey, targetPixelSize: targetPixelSize)
            guard !Task.isCancelled else { return }
            onImageDecoded?(image)
            phase = .success(Image(uiImage: image))
            successfulLoadIdentity = currentLoadIdentity
        } catch {
            guard !Task.isCancelled else { return }
            if successfulLoadIdentity != currentLoadIdentity {
                phase = .failure(error)
            }
        }
    }

    private static func targetIdentity(for targetPixelSize: CGSize?) -> String {
        guard let targetPixelSize else { return "original" }
        let width = max(1, Int(targetPixelSize.width.rounded(.up)))
        let height = max(1, Int(targetPixelSize.height.rounded(.up)))
        return "target:\(width)x\(height)"
    }
}

/// Serializes in-flight image loads without `NSLock` in async code (Swift 6–friendly).
private actor InFlightImageLoads {
    private var tasks: [String: Task<UIImage, Error>] = [:]

    func result(for key: String, operation: @Sendable @escaping () async throws -> UIImage) async throws -> UIImage {
        if let existing = tasks[key] {
            return try await existing.value
        }
        let task = Task {
            try await operation()
        }
        tasks[key] = task
        defer { tasks[key] = nil }
        return try await task.value
    }
}

/// Disk + network image cache. Work is not confined to the main actor: disk IO and `UIImage` decode run off the main thread
/// so chat scroll stays smooth when rows with images appear.
nonisolated final class RemoteImageCache: @unchecked Sendable {
    static let shared = RemoteImageCache()

    private let memoryCache = NSCache<NSString, UIImage>()
    private let session: URLSession
    private let diskDirectory: URL
    private let inFlight = InFlightImageLoads()

    private init() {
        memoryCache.countLimit = 300
        memoryCache.totalCostLimit = 80 * 1024 * 1024

        let urlCache = URLCache(
            memoryCapacity: 40 * 1024 * 1024,
            diskCapacity: 250 * 1024 * 1024,
            diskPath: "RemoteImageURLCache"
        )
        let configuration = URLSessionConfiguration.default
        configuration.urlCache = urlCache
        configuration.requestCachePolicy = .returnCacheDataElseLoad
        configuration.timeoutIntervalForRequest = 20
        configuration.timeoutIntervalForResource = 60
        session = URLSession(configuration: configuration)

        let cachesDirectory = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0]
        diskDirectory = cachesDirectory.appendingPathComponent("RemoteImages", isDirectory: true)
        try? FileManager.default.createDirectory(at: diskDirectory, withIntermediateDirectories: true)
    }

    /// Memory hit only — cheap on the main thread when cells reappear while scrolling.
    func memoryCachedImage(for url: URL, storageKey: String? = nil, targetPixelSize: CGSize? = nil) -> UIImage? {
        let key = decodedImageCacheKey(for: url, storageKey: storageKey, targetPixelSize: targetPixelSize)
        if let image = memoryCache.object(forKey: key as NSString) {
            Self.logCacheEvent("memory", key: key, targetPixelSize: targetPixelSize)
            return image
        }
        return nil
    }

    func image(for url: URL, storageKey: String? = nil, targetPixelSize: CGSize? = nil) async throws -> UIImage {
        let dataKey = dataCacheKey(for: url, storageKey: storageKey)
        let decodedKey = decodedImageCacheKey(for: url, storageKey: storageKey, targetPixelSize: targetPixelSize)
        if let mem = memoryCache.object(forKey: decodedKey as NSString) {
            Self.logCacheEvent("memory", key: decodedKey, targetPixelSize: targetPixelSize)
            return mem
        }

        let diskDir = diskDirectory
        let urlSession = session

        return try await inFlight.result(for: decodedKey) {
            let fileURL = diskDir.appendingPathComponent(dataKey, isDirectory: false)
            if let data = try? Data(contentsOf: fileURL) {
                if let image = await Self.decodeImage(data: data, targetPixelSize: targetPixelSize) {
                    Self.storeInSharedMemoryCache(key: decodedKey, image: image)
                    Self.logCacheEvent("disk", key: decodedKey, targetPixelSize: targetPixelSize)
                    return image
                }
            }

            var request = URLRequest(url: url)
            request.cachePolicy = .returnCacheDataElseLoad

            let start = ContinuousClock.now
            let (data, response) = try await urlSession.data(for: request)
            if let http = response as? HTTPURLResponse, !(200..<300).contains(http.statusCode) {
                throw URLError(.badServerResponse)
            }
            guard let image = await Self.decodeImage(data: data, targetPixelSize: targetPixelSize) else {
                throw URLError(.cannotDecodeContentData)
            }

            Self.storeInSharedMemoryCache(key: decodedKey, image: image)
            await Self.writeAtomically(data, to: fileURL)
            Self.logNetworkLoad(key: decodedKey, targetPixelSize: targetPixelSize, duration: start.duration(to: ContinuousClock.now))
            return image
        }
    }

    private static func storeInSharedMemoryCache(key: String, image: UIImage) {
        shared.memoryCache.setObject(image, forKey: key as NSString, cost: decodedCost(for: image))
    }

    private nonisolated static func decodeImage(data: Data, targetPixelSize: CGSize?) async -> UIImage? {
        await Task.detached(priority: .utility) {
            guard let targetPixelSize else {
                return UIImage(data: data)
            }

            let maxPixelDimension = max(targetPixelSize.width, targetPixelSize.height).rounded(.up)
            guard maxPixelDimension > 1,
                  let source = CGImageSourceCreateWithData(data as CFData, nil),
                  let thumbnail = CGImageSourceCreateThumbnailAtIndex(
                    source,
                    0,
                    [
                        kCGImageSourceCreateThumbnailFromImageAlways: true,
                        kCGImageSourceCreateThumbnailWithTransform: true,
                        kCGImageSourceThumbnailMaxPixelSize: Int(maxPixelDimension)
                    ] as CFDictionary
                  ) else {
                return UIImage(data: data)
            }

            return UIImage(cgImage: thumbnail)
        }.value
    }

    private static func decodedCost(for image: UIImage) -> Int {
        guard let cgImage = image.cgImage else {
            let width = max(1, Int((image.size.width * image.scale).rounded(.up)))
            let height = max(1, Int((image.size.height * image.scale).rounded(.up)))
            return width * height * 4
        }
        return cgImage.bytesPerRow * cgImage.height
    }

    private nonisolated static func writeAtomically(_ data: Data, to url: URL) async {
        await Task.detached(priority: .utility) {
            try? data.write(to: url, options: [.atomic])
        }.value
    }

    private func dataCacheKey(for url: URL, storageKey: String?) -> String {
        cacheKey(for: url, storageKey: storageKey, variant: "data")
    }

    private func decodedImageCacheKey(for url: URL, storageKey: String?, targetPixelSize: CGSize?) -> String {
        let variant: String
        if let targetPixelSize {
            let width = max(1, Int(targetPixelSize.width.rounded(.up)))
            let height = max(1, Int(targetPixelSize.height.rounded(.up)))
            variant = "decoded:\(width)x\(height)"
        } else {
            variant = "decoded:original"
        }
        return cacheKey(for: url, storageKey: storageKey, variant: variant)
    }

    private func cacheKey(for url: URL, storageKey: String?, variant: String) -> String {
        let identity: String
        if let storageKey, !storageKey.isEmpty {
            identity = "otto.remoteImage:\(storageKey)"
        } else {
            identity = url.absoluteString
        }
        let digest = SHA256.hash(data: Data("\(identity)|\(variant)".utf8))
        return digest.map { String(format: "%02x", $0) }.joined()
    }

    private nonisolated static func logCacheEvent(_ source: String, key: String, targetPixelSize: CGSize?) {
        #if DEBUG
        imageCacheLogger.debug("remote-image source=\(source, privacy: .public) key=\(key, privacy: .public) target=\(targetDescription(targetPixelSize), privacy: .public)")
        #endif
    }

    private nonisolated static func logNetworkLoad(key: String, targetPixelSize: CGSize?, duration: Duration) {
        #if DEBUG
        imageCacheLogger.debug("remote-image source=network key=\(key, privacy: .public) target=\(targetDescription(targetPixelSize), privacy: .public) duration=\(duration.description, privacy: .public)")
        #endif
    }

    private nonisolated static func targetDescription(_ targetPixelSize: CGSize?) -> String {
        guard let targetPixelSize else { return "original" }
        return "\(Int(targetPixelSize.width.rounded(.up)))x\(Int(targetPixelSize.height.rounded(.up)))"
    }

    #if DEBUG
    private static let imageCacheLogger = Logger(subsystem: "to.ottomot.driftd", category: "RemoteImageCache")
    #endif
}
