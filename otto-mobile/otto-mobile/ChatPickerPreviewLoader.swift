import Photos
import PhotosUI
import SwiftUI
import UIKit
import UniformTypeIdentifiers

struct ChatPendingComposerAttachment: Identifiable {
    enum MediaKind: Equatable {
        case photo
        case video
    }

    enum Kind: Equatable {
        case media(MediaKind)
        case place(MapMarkerSharePayload)
        case event(eventId: String, eventName: String)
        case klipyGif(KlipyGifSelection)
    }

    let id: UUID
    let kind: Kind
    var previewImage: UIImage?
    var mapPreviewJPEG: Data?
    var pickerItem: PhotosPickerItem?
    var event: EventDTO?
    /// Last KLIPY search query when picked from search (for share-trigger attribution).
    var klipySearchQuery: String?

    init(
        id: UUID = UUID(),
        kind: Kind,
        previewImage: UIImage? = nil,
        mapPreviewJPEG: Data? = nil,
        pickerItem: PhotosPickerItem? = nil,
        event: EventDTO? = nil,
        klipySearchQuery: String? = nil
    ) {
        self.id = id
        self.kind = kind
        self.previewImage = previewImage
        self.mapPreviewJPEG = mapPreviewJPEG
        self.pickerItem = pickerItem
        self.event = event
        self.klipySearchQuery = klipySearchQuery
    }

    var isVideo: Bool {
        if case .media(.video) = kind { return true }
        return false
    }

    var isPhoto: Bool {
        if case .media(.photo) = kind { return true }
        return false
    }

    var isPlace: Bool {
        if case .place = kind { return true }
        return false
    }

    var isEvent: Bool {
        if case .event = kind { return true }
        return false
    }

    var isKlipyGif: Bool {
        if case .klipyGif = kind { return true }
        return false
    }

    var klipySelection: KlipyGifSelection? {
        if case .klipyGif(let selection) = kind { return selection }
        return nil
    }

    var placePayload: MapMarkerSharePayload? {
        if case .place(let payload) = kind { return payload }
        return nil
    }

    static func == (lhs: ChatPendingComposerAttachment, rhs: ChatPendingComposerAttachment) -> Bool {
        lhs.id == rhs.id
            && lhs.kind == rhs.kind
            && lhs.previewImage === rhs.previewImage
            && lhs.mapPreviewJPEG == rhs.mapPreviewJPEG
            && lhs.event?.id == rhs.event?.id
            && lhs.klipySearchQuery == rhs.klipySearchQuery
    }
}

extension ChatPendingComposerAttachment: Equatable {}

enum ChatComposerAttachmentAction: Hashable, CaseIterable {
    case photo
    case video
    case gif
    case location
    case createEvent

    static var directChatActions: Set<ChatComposerAttachmentAction> {
        [.photo, .gif, .video, .location]
    }

    static var squadChatActions: Set<ChatComposerAttachmentAction> {
        Set(ChatComposerAttachmentAction.allCases)
    }

    var systemImage: String {
        switch self {
        case .photo: return "photo"
        case .gif: return "face.smiling"
        case .video: return "video"
        case .location: return "location"
        case .createEvent: return "calendar.badge.plus"
        }
    }

    var accessibilityLabelKey: String {
        switch self {
        case .photo: return "chat_composer_attach_photo"
        case .gif: return "chat_composer_attach_gif"
        case .video: return "chat_composer_attach_video"
        case .location: return "chat_composer_attach_location"
        case .createEvent: return "chat_composer_attach_create_event"
        }
    }

    var labelKey: String { accessibilityLabelKey }
}

enum ChatPickerPreviewLoader {
    struct LoadedAttachment {
        let kind: ChatPendingComposerAttachment.MediaKind
        let previewImage: UIImage?
    }

    enum Error: LocalizedError {
        case unreadable
        case tooLong

        var errorDescription: String? {
            switch self {
            case .unreadable: return "Couldn't read that attachment."
            case .tooLong: return "This video is too long. Chat videos must be 60 seconds or shorter."
            }
        }
    }

    private static let previewMaxSide: CGFloat = 240
    private static let maxDurationSeconds: TimeInterval = 60

    static func loadAttachment(from item: PhotosPickerItem) async throws -> LoadedAttachment {
        guard let identifier = item.itemIdentifier else { throw Error.unreadable }
        let assets = PHAsset.fetchAssets(withLocalIdentifiers: [identifier], options: nil)
        guard let asset = assets.firstObject else { throw Error.unreadable }

        let kind = resolveAttachmentKind(for: item, asset: asset)
        if kind == .video, asset.duration > maxDurationSeconds {
            throw Error.tooLong
        }

        let preview = await requestThumbnail(for: asset)
        return LoadedAttachment(kind: kind, previewImage: preview)
    }

    static func photoJPEG(from item: PhotosPickerItem) async throws -> Data? {
        guard let image = await loadFullSizeImage(from: item) else {
            throw Error.unreadable
        }
        return ChatPhotoUploadNormalizer.jpegData(for: image)
    }

    static func eventBannerPreview(for event: EventDTO) async -> UIImage? {
        guard let raw = event.bannerImage?.url.trimmingCharacters(in: .whitespacesAndNewlines),
              !raw.isEmpty,
              let url = APIConfig.imageFetchURL(from: raw)
        else {
            return nil
        }
        do {
            let (data, response) = try await URLSession.shared.data(from: url)
            guard let http = response as? HTTPURLResponse, (200 ... 299).contains(http.statusCode) else {
                return nil
            }
            return UIImage(data: data)
        } catch {
            return nil
        }
    }

    private static func resolveAttachmentKind(
        for item: PhotosPickerItem,
        asset: PHAsset
    ) -> ChatPendingComposerAttachment.MediaKind {
        if asset.mediaSubtypes.contains(.photoLive) {
            return .photo
        }

        let types = item.supportedContentTypes
        let stillImageTypes: [UTType] = [.jpeg, .png, .heic, .gif, .webP]
        if types.contains(where: { type in
            stillImageTypes.contains(where: { type.conforms(to: $0) })
        }) {
            return .photo
        }

        if types.contains(where: { $0.conforms(to: .image) && !$0.conforms(to: .movie) }) {
            return .photo
        }

        return asset.mediaType == .video ? .video : .photo
    }

    private static func loadFullSizeImage(from item: PhotosPickerItem) async -> UIImage? {
        if let identifier = item.itemIdentifier {
            let assets = PHAsset.fetchAssets(withLocalIdentifiers: [identifier], options: nil)
            if let asset = assets.firstObject, asset.mediaType != .video {
                if let image = await requestFullImage(for: asset) {
                    return image
                }
            }
        }

        if let data = try? await item.loadTransferable(type: Data.self),
           let image = UIImage(data: data) {
            return image
        }
        return nil
    }

    private static func requestFullImage(for asset: PHAsset) async -> UIImage? {
        await withCheckedContinuation { continuation in
            let options = PHImageRequestOptions()
            options.deliveryMode = .highQualityFormat
            options.isNetworkAccessAllowed = true
            options.isSynchronous = false
            options.resizeMode = .none

            var resumed = false
            PHImageManager.default().requestImage(
                for: asset,
                targetSize: PHImageManagerMaximumSize,
                contentMode: .aspectFit,
                options: options
            ) { image, info in
                guard !resumed else { return }
                if (info?[PHImageResultIsDegradedKey] as? Bool) == true {
                    return
                }
                resumed = true
                continuation.resume(returning: image)
            }
        }
    }

    static func preparedVideo(from item: PhotosPickerItem) async throws -> ChatPreparedVideoUpload {
        guard let picked = try await item.loadTransferable(type: ChatPickedVideoFile.self) else {
            throw Error.unreadable
        }
        return try await ChatVideoUploadPrep.prepare(localVideoURL: picked.url)
    }

    private static func requestThumbnail(for asset: PHAsset) async -> UIImage? {
        await withCheckedContinuation { continuation in
            let scale = UIScreen.main.scale
            let targetSize = CGSize(
                width: previewMaxSide * scale,
                height: previewMaxSide * scale
            )
            let options = PHImageRequestOptions()
            options.deliveryMode = .fastFormat
            options.resizeMode = .fast
            options.isNetworkAccessAllowed = true
            options.isSynchronous = false

            var resumed = false
            PHImageManager.default().requestImage(
                for: asset,
                targetSize: targetSize,
                contentMode: .aspectFill,
                options: options
            ) { image, _ in
                guard !resumed else { return }
                resumed = true
                continuation.resume(returning: image)
            }
        }
    }
}
