import CryptoKit
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
    /// Called on the main actor when a decoded image is available (memory hit or fresh load).
    var onImageDecoded: ((UIImage) -> Void)? = nil
    @ViewBuilder var content: (CachedAsyncImagePhase) -> Content

    @State private var phase: CachedAsyncImagePhase = .empty

    /// Identity for `.task` reloads: when `storageKey` is set it is stable across presigned URL refreshes; otherwise follow the URL.
    private var loadIdentity: String {
        if let storageKey, !storageKey.isEmpty {
            return storageKey
        }
        return url?.absoluteString ?? ""
    }

    var body: some View {
        content(phase)
            .task(id: loadIdentity) {
                await loadImage()
            }
    }

    @MainActor
    private func loadImage() async {
        guard let url else {
            phase = .empty
            return
        }

        if let cached = RemoteImageCache.shared.memoryCachedImage(for: url, storageKey: storageKey) {
            onImageDecoded?(cached)
            phase = .success(Image(uiImage: cached))
            return
        }

        phase = .empty

        do {
            let image = try await RemoteImageCache.shared.image(for: url, storageKey: storageKey)
            guard !Task.isCancelled else { return }
            onImageDecoded?(image)
            phase = .success(Image(uiImage: image))
        } catch {
            guard !Task.isCancelled else { return }
            phase = .failure(error)
        }
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
    func memoryCachedImage(for url: URL, storageKey: String? = nil) -> UIImage? {
        let key = cacheKey(for: url, storageKey: storageKey)
        return memoryCache.object(forKey: key as NSString)
    }

    func image(for url: URL, storageKey: String? = nil) async throws -> UIImage {
        let key = cacheKey(for: url, storageKey: storageKey)
        if let mem = memoryCache.object(forKey: key as NSString) {
            return mem
        }

        let diskDir = diskDirectory
        let urlSession = session

        return try await inFlight.result(for: key) {
            let fileURL = diskDir.appendingPathComponent(key, isDirectory: false)
            if let data = try? Data(contentsOf: fileURL) {
                if let image = await Self.decodeImage(data: data) {
                    Self.storeInSharedMemoryCache(key: key, image: image, cost: data.count)
                    return image
                }
            }

            var request = URLRequest(url: url)
            request.cachePolicy = .returnCacheDataElseLoad

            let (data, response) = try await urlSession.data(for: request)
            if let http = response as? HTTPURLResponse, !(200..<300).contains(http.statusCode) {
                throw URLError(.badServerResponse)
            }
            guard let image = await Self.decodeImage(data: data) else {
                throw URLError(.cannotDecodeContentData)
            }

            Self.storeInSharedMemoryCache(key: key, image: image, cost: data.count)
            await Self.writeAtomically(data, to: fileURL)
            return image
        }
    }

    private static func storeInSharedMemoryCache(key: String, image: UIImage, cost: Int) {
        shared.memoryCache.setObject(image, forKey: key as NSString, cost: cost)
    }

    private nonisolated static func decodeImage(data: Data) async -> UIImage? {
        await Task.detached(priority: .utility) {
            UIImage(data: data)
        }.value
    }

    private nonisolated static func writeAtomically(_ data: Data, to url: URL) async {
        await Task.detached(priority: .utility) {
            try? data.write(to: url, options: [.atomic])
        }.value
    }

    private func cacheKey(for url: URL, storageKey: String?) -> String {
        if let storageKey, !storageKey.isEmpty {
            let digest = SHA256.hash(data: Data("otto.remoteImage:\(storageKey)".utf8))
            return digest.map { String(format: "%02x", $0) }.joined()
        }
        let digest = SHA256.hash(data: Data(url.absoluteString.utf8))
        return digest.map { String(format: "%02x", $0) }.joined()
    }
}
