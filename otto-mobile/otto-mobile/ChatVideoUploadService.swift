import Combine
import Foundation
import UIKit
import os

struct ChatVideoUploadURLsResponse: Decodable {
    struct Part: Decodable {
        let uploadUrl: String
        let fileUrl: String
    }

    let video: Part
    let thumbnail: Part
    let expiresIn: Int?
}

@MainActor
final class ChatVideoUploadCoordinator: ObservableObject {
    enum Phase: Equatable {
        case preparing
        case uploading
        case failed
    }

    struct PendingUpload {
        let clientMessageId: String
        let circleId: String?
        let conversationId: String?
        var thumbnail: UIImage
        var durationSeconds: Double
        var width: Int
        var height: Int
        var progress: Double
        var phase: Phase
    }

    static let shared = ChatVideoUploadCoordinator()

    @Published private(set) var pendingByClientMessageId: [String: PendingUpload] = [:]
    private var uploadTasks: [String: Task<Void, Never>] = [:]

    func pending(for clientMessageId: String?) -> PendingUpload? {
        guard let clientMessageId, !clientMessageId.isEmpty else { return nil }
        return pendingByClientMessageId[clientMessageId]
    }

    func cancel(clientMessageId: String) {
        uploadTasks[clientMessageId]?.cancel()
        uploadTasks[clientMessageId] = nil
        pendingByClientMessageId[clientMessageId] = nil
    }

    func clear(clientMessageId: String) {
        uploadTasks[clientMessageId] = nil
        pendingByClientMessageId[clientMessageId] = nil
    }

    func startCircleUpload(
        circleId: String,
        pending: ChatPendingVideo,
        body: String,
        clientMessageId: String,
        replyToMessageId: String?,
        mentions: [CircleChatMentionSpanDTO],
        senderUserId: String,
        sender: CircleChatMessageDTO.SenderDTO?,
        onOptimisticMessage: @escaping (CircleChatMessageDTO) -> Void,
        onComplete: @escaping (Result<CircleChatMessageDTO, Error>) -> Void
    ) {
        beginPendingUpload(
            clientMessageId: clientMessageId,
            pending: pending,
            circleId: circleId,
            conversationId: nil,
            onOptimisticMessage: {
                onOptimisticMessage(
                    CircleChatMessageDTO.pendingVideo(
                        clientMessageId: clientMessageId,
                        circleId: circleId,
                        body: body,
                        senderUserId: senderUserId,
                        sender: sender,
                        videoAttachment: $0,
                        replyToMessageId: replyToMessageId,
                        replyTo: nil
                    )
                )
            }
        )

        uploadTasks[clientMessageId] = Task {
            do {
                let prepared = try await ChatVideoUploadPrep.prepare(localVideoURL: pending.localVideoURL)
                applyPreparedMetadata(clientMessageId: clientMessageId, prepared: prepared)
                setPhase(clientMessageId: clientMessageId, phase: .uploading, progress: 0)
                let message = try await self.uploadPreparedCircleVideo(
                    circleId: circleId,
                    prepared: prepared,
                    body: body,
                    clientMessageId: clientMessageId,
                    replyToMessageId: replyToMessageId,
                    mentions: mentions
                )
                clear(clientMessageId: clientMessageId)
                onComplete(.success(message))
            } catch {
                if !Task.isCancelled {
                    markFailed(clientMessageId: clientMessageId, error: error)
                    OttoLog.api.error(
                        "Squad chat video upload failed circle=\(circleId, privacy: .public) clientMessageId=\(clientMessageId, privacy: .public) error=\(String(describing: error), privacy: .public)"
                    )
                    onComplete(.failure(error))
                }
            }
        }
    }

    func startCircleUpload(
        circleId: String,
        prepared: ChatPreparedVideoUpload,
        body: String,
        clientMessageId: String,
        replyToMessageId: String?,
        mentions: [CircleChatMentionSpanDTO],
        senderUserId: String,
        sender: CircleChatMessageDTO.SenderDTO?,
        onOptimisticMessage: @escaping (CircleChatMessageDTO) -> Void,
        onComplete: @escaping (Result<CircleChatMessageDTO, Error>) -> Void
    ) {
        startUpload(
            clientMessageId: clientMessageId,
            circleId: circleId,
            conversationId: nil,
            prepared: prepared,
            requestUploadURLs: {
                try await APIClient.shared.requestCircleChatVideoUploadURLs(
                    circleId: circleId,
                    videoContentType: prepared.mimeType
                )
            },
            finalize: { attachment in
                try await APIClient.shared.sendCircleChatVideoMessage(
                    circleId: circleId,
                    body: body,
                    clientMessageId: clientMessageId,
                    replyToMessageId: replyToMessageId,
                    mentions: mentions,
                    videoAttachment: attachment
                )
            },
            makeOptimistic: { attachment in
                CircleChatMessageDTO.pendingVideo(
                    clientMessageId: clientMessageId,
                    circleId: circleId,
                    body: body,
                    senderUserId: senderUserId,
                    sender: sender,
                    videoAttachment: attachment,
                    replyToMessageId: replyToMessageId,
                    replyTo: nil
                )
            },
            onOptimisticMessage: onOptimisticMessage,
            onComplete: onComplete
        )
    }

    func startDirectUpload(
        conversationId: String,
        pending: ChatPendingVideo,
        body: String,
        clientMessageId: String,
        replyToMessageId: String?,
        senderUserId: String,
        sender: DirectConversationDTO.UserSummaryDTO?,
        onOptimisticMessage: @escaping (DirectMessageDTO) -> Void,
        onComplete: @escaping (Result<DirectMessageDTO, Error>) -> Void
    ) {
        beginPendingUpload(
            clientMessageId: clientMessageId,
            pending: pending,
            circleId: nil,
            conversationId: conversationId,
            onOptimisticMessage: {
                onOptimisticMessage(
                    DirectMessageDTO.pendingVideo(
                        clientMessageId: clientMessageId,
                        conversationId: conversationId,
                        body: body,
                        senderUserId: senderUserId,
                        sender: sender,
                        videoAttachment: $0,
                        replyToMessageId: replyToMessageId,
                        replyTo: nil
                    )
                )
            }
        )

        uploadTasks[clientMessageId] = Task {
            do {
                let prepared = try await ChatVideoUploadPrep.prepare(localVideoURL: pending.localVideoURL)
                applyPreparedMetadata(clientMessageId: clientMessageId, prepared: prepared)
                setPhase(clientMessageId: clientMessageId, phase: .uploading, progress: 0)
                let message = try await self.uploadPreparedDirectVideo(
                    conversationId: conversationId,
                    prepared: prepared,
                    body: body,
                    clientMessageId: clientMessageId,
                    replyToMessageId: replyToMessageId
                )
                clear(clientMessageId: clientMessageId)
                onComplete(.success(message))
            } catch {
                if !Task.isCancelled {
                    markFailed(clientMessageId: clientMessageId, error: error)
                    OttoLog.api.error(
                        "Direct chat video upload failed conversation=\(conversationId, privacy: .public) clientMessageId=\(clientMessageId, privacy: .public) error=\(String(describing: error), privacy: .public)"
                    )
                    onComplete(.failure(error))
                }
            }
        }
    }

    func startDirectUpload(
        conversationId: String,
        prepared: ChatPreparedVideoUpload,
        body: String,
        clientMessageId: String,
        replyToMessageId: String?,
        senderUserId: String,
        sender: DirectConversationDTO.UserSummaryDTO?,
        onOptimisticMessage: @escaping (DirectMessageDTO) -> Void,
        onComplete: @escaping (Result<DirectMessageDTO, Error>) -> Void
    ) {
        startDirectUploadInternal(
            conversationId: conversationId,
            prepared: prepared,
            body: body,
            clientMessageId: clientMessageId,
            replyToMessageId: replyToMessageId,
            senderUserId: senderUserId,
            sender: sender,
            onOptimisticMessage: onOptimisticMessage,
            onComplete: onComplete
        )
    }

    private func beginPendingUpload(
        clientMessageId: String,
        pending: ChatPendingVideo,
        circleId: String?,
        conversationId: String?,
        onOptimisticMessage: @escaping (CircleChatMessageDTO.VideoAttachmentDTO) -> Void
    ) {
        let preview = pending.previewImage ?? Self.placeholderPreviewImage
        let width = max(1, Int(preview.size.width.rounded()))
        let height = max(1, Int(preview.size.height.rounded()))
        pendingByClientMessageId[clientMessageId] = PendingUpload(
            clientMessageId: clientMessageId,
            circleId: circleId,
            conversationId: conversationId,
            thumbnail: preview,
            durationSeconds: 0,
            width: width,
            height: height,
            progress: 0,
            phase: .preparing
        )
        let placeholder = CircleChatMessageDTO.VideoAttachmentDTO(
            videoUrl: "",
            thumbnailUrl: "",
            durationSeconds: 0,
            width: width,
            height: height,
            mimeType: "video/mp4"
        )
        onOptimisticMessage(placeholder)
    }

    private func applyPreparedMetadata(clientMessageId: String, prepared: ChatPreparedVideoUpload) {
        guard var entry = pendingByClientMessageId[clientMessageId] else { return }
        entry.thumbnail = prepared.thumbnailImage
        entry.durationSeconds = prepared.durationSeconds
        entry.width = prepared.width
        entry.height = prepared.height
        pendingByClientMessageId[clientMessageId] = entry
    }

    private func setPhase(clientMessageId: String, phase: Phase, progress: Double? = nil) {
        guard var entry = pendingByClientMessageId[clientMessageId] else { return }
        entry.phase = phase
        if let progress {
            entry.progress = min(max(progress, 0), 1)
        }
        pendingByClientMessageId[clientMessageId] = entry
    }

    private func markFailed(clientMessageId: String, error: Error) {
        guard var entry = pendingByClientMessageId[clientMessageId] else { return }
        entry.phase = .failed
        pendingByClientMessageId[clientMessageId] = entry
        uploadTasks[clientMessageId] = nil
    }

    private func uploadPreparedCircleVideo(
        circleId: String,
        prepared: ChatPreparedVideoUpload,
        body: String,
        clientMessageId: String,
        replyToMessageId: String?,
        mentions: [CircleChatMentionSpanDTO]
    ) async throws -> CircleChatMessageDTO {
        let urls = try await APIClient.shared.requestCircleChatVideoUploadURLs(
            circleId: circleId,
            videoContentType: prepared.mimeType
        )
        try await put(data: prepared.thumbnailJPEG, to: urls.thumbnail.uploadUrl, contentType: "image/jpeg") { [weak self] p in
            Task { @MainActor in self?.updateProgress(clientMessageId: clientMessageId, progress: min(p * 0.08, 0.08)) }
        }
        try await put(fileURL: prepared.localVideoURL, to: urls.video.uploadUrl, contentType: prepared.mimeType) { [weak self] p in
            Task { @MainActor in self?.updateProgress(clientMessageId: clientMessageId, progress: 0.08 + p * 0.84) }
        }
        let attachment = CircleChatMessageDTO.VideoAttachmentDTO(
            videoUrl: urls.video.fileUrl,
            thumbnailUrl: urls.thumbnail.fileUrl,
            durationSeconds: prepared.durationSeconds,
            width: prepared.width,
            height: prepared.height,
            mimeType: prepared.mimeType
        )
        updateProgress(clientMessageId: clientMessageId, progress: 0.95)
        return try await APIClient.shared.sendCircleChatVideoMessage(
            circleId: circleId,
            body: body,
            clientMessageId: clientMessageId,
            replyToMessageId: replyToMessageId,
            mentions: mentions,
            videoAttachment: attachment
        )
    }

    private func uploadPreparedDirectVideo(
        conversationId: String,
        prepared: ChatPreparedVideoUpload,
        body: String,
        clientMessageId: String,
        replyToMessageId: String?
    ) async throws -> DirectMessageDTO {
        let urls = try await APIClient.shared.requestDirectChatVideoUploadURLs(
            conversationId: conversationId,
            videoContentType: prepared.mimeType
        )
        try await put(data: prepared.thumbnailJPEG, to: urls.thumbnail.uploadUrl, contentType: "image/jpeg") { [weak self] p in
            Task { @MainActor in self?.updateProgress(clientMessageId: clientMessageId, progress: min(p * 0.08, 0.08)) }
        }
        try await put(fileURL: prepared.localVideoURL, to: urls.video.uploadUrl, contentType: prepared.mimeType) { [weak self] p in
            Task { @MainActor in self?.updateProgress(clientMessageId: clientMessageId, progress: 0.08 + p * 0.84) }
        }
        let attachment = CircleChatMessageDTO.VideoAttachmentDTO(
            videoUrl: urls.video.fileUrl,
            thumbnailUrl: urls.thumbnail.fileUrl,
            durationSeconds: prepared.durationSeconds,
            width: prepared.width,
            height: prepared.height,
            mimeType: prepared.mimeType
        )
        updateProgress(clientMessageId: clientMessageId, progress: 0.95)
        return try await APIClient.shared.sendDirectChatVideoMessage(
            conversationId: conversationId,
            body: body,
            clientMessageId: clientMessageId,
            replyToMessageId: replyToMessageId,
            videoAttachment: attachment
        )
    }

    private static let placeholderPreviewImage: UIImage = {
        let size = CGSize(width: 16, height: 9)
        let renderer = UIGraphicsImageRenderer(size: size)
        return renderer.image { ctx in
            UIColor(white: 0.12, alpha: 1).setFill()
            ctx.fill(CGRect(origin: .zero, size: size))
        }
    }()

    private func startDirectUploadInternal(
        conversationId: String,
        prepared: ChatPreparedVideoUpload,
        body: String,
        clientMessageId: String,
        replyToMessageId: String?,
        senderUserId: String,
        sender: DirectConversationDTO.UserSummaryDTO?,
        onOptimisticMessage: @escaping (DirectMessageDTO) -> Void,
        onComplete: @escaping (Result<DirectMessageDTO, Error>) -> Void
    ) {
        pendingByClientMessageId[clientMessageId] = PendingUpload(
            clientMessageId: clientMessageId,
            circleId: nil,
            conversationId: conversationId,
            thumbnail: prepared.thumbnailImage,
            durationSeconds: prepared.durationSeconds,
            width: prepared.width,
            height: prepared.height,
            progress: 0,
            phase: .uploading
        )

        let placeholderAttachment = CircleChatMessageDTO.VideoAttachmentDTO(
            videoUrl: "",
            thumbnailUrl: "",
            durationSeconds: prepared.durationSeconds,
            width: prepared.width,
            height: prepared.height,
            mimeType: prepared.mimeType
        )
        onOptimisticMessage(
            DirectMessageDTO.pendingVideo(
                clientMessageId: clientMessageId,
                conversationId: conversationId,
                body: body,
                senderUserId: senderUserId,
                sender: sender,
                videoAttachment: placeholderAttachment,
                replyToMessageId: replyToMessageId,
                replyTo: nil
            )
        )

        uploadTasks[clientMessageId] = Task {
            do {
                let message = try await self.uploadPreparedDirectVideo(
                    conversationId: conversationId,
                    prepared: prepared,
                    body: body,
                    clientMessageId: clientMessageId,
                    replyToMessageId: replyToMessageId
                )
                clear(clientMessageId: clientMessageId)
                onComplete(.success(message))
            } catch {
                if !Task.isCancelled {
                    markFailed(clientMessageId: clientMessageId, error: error)
                    onComplete(.failure(error))
                }
            }
        }
    }

    private func startUpload<T>(
        clientMessageId: String,
        circleId: String?,
        conversationId: String?,
        prepared: ChatPreparedVideoUpload,
        requestUploadURLs: @escaping () async throws -> ChatVideoUploadURLsResponse,
        finalize: @escaping (CircleChatMessageDTO.VideoAttachmentDTO) async throws -> T,
        makeOptimistic: @escaping (CircleChatMessageDTO.VideoAttachmentDTO) -> T,
        onOptimisticMessage: @escaping (T) -> Void,
        onComplete: @escaping (Result<T, Error>) -> Void
    ) {
        pendingByClientMessageId[clientMessageId] = PendingUpload(
            clientMessageId: clientMessageId,
            circleId: circleId,
            conversationId: conversationId,
            thumbnail: prepared.thumbnailImage,
            durationSeconds: prepared.durationSeconds,
            width: prepared.width,
            height: prepared.height,
            progress: 0,
            phase: .uploading
        )

        let placeholder = CircleChatMessageDTO.VideoAttachmentDTO(
            videoUrl: "",
            thumbnailUrl: "",
            durationSeconds: prepared.durationSeconds,
            width: prepared.width,
            height: prepared.height,
            mimeType: prepared.mimeType
        )
        onOptimisticMessage(makeOptimistic(placeholder))

        uploadTasks[clientMessageId] = Task {
            do {
                let urls = try await requestUploadURLs()
                try await put(data: prepared.thumbnailJPEG, to: urls.thumbnail.uploadUrl, contentType: "image/jpeg") { [weak self] p in
                    Task { @MainActor in self?.updateProgress(clientMessageId: clientMessageId, progress: min(p * 0.08, 0.08)) }
                }
                try await put(fileURL: prepared.localVideoURL, to: urls.video.uploadUrl, contentType: prepared.mimeType) { [weak self] p in
                    Task { @MainActor in self?.updateProgress(clientMessageId: clientMessageId, progress: 0.08 + p * 0.84) }
                }
                let attachment = CircleChatMessageDTO.VideoAttachmentDTO(
                    videoUrl: urls.video.fileUrl,
                    thumbnailUrl: urls.thumbnail.fileUrl,
                    durationSeconds: prepared.durationSeconds,
                    width: prepared.width,
                    height: prepared.height,
                    mimeType: prepared.mimeType
                )
                updateProgress(clientMessageId: clientMessageId, progress: 0.95)
                let message = try await finalize(attachment)
                clear(clientMessageId: clientMessageId)
                onComplete(.success(message))
            } catch {
                if !Task.isCancelled {
                    markFailed(clientMessageId: clientMessageId, error: error)
                    onComplete(.failure(error))
                }
            }
        }
    }

    private func updateProgress(clientMessageId: String, progress: Double) {
        guard var entry = pendingByClientMessageId[clientMessageId] else { return }
        entry.progress = min(max(progress, 0), 1)
        entry.phase = .uploading
        pendingByClientMessageId[clientMessageId] = entry
    }

    private func put(
        data: Data,
        to uploadURLString: String,
        contentType: String,
        progress: @escaping (Double) -> Void
    ) async throws {
        guard let url = URL(string: uploadURLString) else { throw URLError(.badURL) }
        var request = URLRequest(url: url)
        request.httpMethod = "PUT"
        request.setValue(contentType, forHTTPHeaderField: "Content-Type")
        request.setValue("\(data.count)", forHTTPHeaderField: "Content-Length")
        let delegate = ChatPUTProgressDelegate(totalBytes: Int64(data.count), progress: progress)
        let session = URLSession(configuration: .ephemeral, delegate: delegate, delegateQueue: nil)
        defer { session.invalidateAndCancel() }
        let (_, response) = try await session.upload(for: request, from: data)
        guard let http = response as? HTTPURLResponse, (200...299).contains(http.statusCode) else {
            throw URLError(.badServerResponse)
        }
    }

    private func put(
        fileURL: URL,
        to uploadURLString: String,
        contentType: String,
        progress: @escaping (Double) -> Void
    ) async throws {
        guard let url = URL(string: uploadURLString) else { throw URLError(.badURL) }
        let attrs = try FileManager.default.attributesOfItem(atPath: fileURL.path)
        let totalBytes = (attrs[.size] as? NSNumber)?.int64Value ?? 0
        var request = URLRequest(url: url)
        request.httpMethod = "PUT"
        request.setValue(contentType, forHTTPHeaderField: "Content-Type")
        if totalBytes > 0 {
            request.setValue("\(totalBytes)", forHTTPHeaderField: "Content-Length")
        }
        let delegate = ChatPUTProgressDelegate(totalBytes: totalBytes, progress: progress)
        let session = URLSession(configuration: .ephemeral, delegate: delegate, delegateQueue: nil)
        defer { session.invalidateAndCancel() }
        let (_, response) = try await session.upload(for: request, fromFile: fileURL)
        guard let http = response as? HTTPURLResponse, (200...299).contains(http.statusCode) else {
            throw URLError(.badServerResponse)
        }
    }
}

private final class ChatPUTProgressDelegate: NSObject, URLSessionTaskDelegate {
    private let totalBytes: Int64
    private let progress: (Double) -> Void

    init(totalBytes: Int64, progress: @escaping (Double) -> Void) {
        self.totalBytes = totalBytes
        self.progress = progress
    }

    func urlSession(
        _ session: URLSession,
        task: URLSessionTask,
        didSendBodyData bytesSent: Int64,
        totalBytesSent: Int64,
        totalBytesExpectedToSend: Int64
    ) {
        let expected = totalBytesExpectedToSend > 0 ? totalBytesExpectedToSend : totalBytes
        guard expected > 0 else { return }
        progress(Double(totalBytesSent) / Double(expected))
    }
}
