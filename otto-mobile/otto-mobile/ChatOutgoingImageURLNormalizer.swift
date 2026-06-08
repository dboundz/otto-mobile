import Foundation

struct ChatOutgoingImagePayload: Equatable {
    let body: String
    let imageUrl: String?
    let klipyShare: KlipyShareContext?

    struct KlipyShareContext: Equatable {
        let slug: String
        let searchQuery: String?
    }
}

enum ChatOutgoingImageURLNormalizer {
    private static let directImageExtensions: Set<String> = ["gif", "webp", "png", "jpg", "jpeg"]

    static func normalize(
        draft: String,
        pendingAttachment: ChatPendingComposerAttachment?
    ) -> ChatOutgoingImagePayload {
        if let pendingAttachment, case .klipyGif(let selection) = pendingAttachment.kind {
            let caption = draft.trimmingCharacters(in: .whitespacesAndNewlines)
            return ChatOutgoingImagePayload(
                body: caption,
                imageUrl: selection.sendURL.absoluteString,
                klipyShare: ChatOutgoingImagePayload.KlipyShareContext(
                    slug: selection.slug,
                    searchQuery: pendingAttachment.klipySearchQuery
                )
            )
        }

        if let promoted = promoteDirectImageURL(from: draft) {
            return ChatOutgoingImagePayload(
                body: promoted.body,
                imageUrl: promoted.imageUrl,
                klipyShare: nil
            )
        }

        return ChatOutgoingImagePayload(
            body: draft.trimmingCharacters(in: .whitespacesAndNewlines),
            imageUrl: nil,
            klipyShare: nil
        )
    }

    private static func promoteDirectImageURL(from draft: String) -> (body: String, imageUrl: String)? {
        let trimmed = draft.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return nil }

        guard let url = extractSingleImageURL(from: trimmed),
              isDirectImageURL(url)
        else { return nil }

        var body = trimmed
        if let range = body.range(of: url.absoluteString) {
            body.removeSubrange(range)
        }
        body = body.trimmingCharacters(in: .whitespacesAndNewlines)
        return (body, url.absoluteString)
    }

    private static func extractSingleImageURL(from text: String) -> URL? {
        guard let detector = try? NSDataDetector(types: NSTextCheckingResult.CheckingType.link.rawValue) else {
            return nil
        }
        let ns = text as NSString
        let range = NSRange(location: 0, length: ns.length)
        let matches = detector.matches(in: text, options: [], range: range)
            .filter { $0.resultType == .link && $0.url != nil }
        guard matches.count == 1, let match = matches.first, let url = match.url else { return nil }

        let linkRange = match.range
        let before = linkRange.location > 0
            ? ns.substring(with: NSRange(location: 0, length: linkRange.location))
            : ""
        let afterStart = linkRange.location + linkRange.length
        let after = afterStart < ns.length
            ? ns.substring(from: afterStart)
            : ""
        let extra = (before + after).trimmingCharacters(in: .whitespacesAndNewlines)
        if extra.contains("http://") || extra.contains("https://") {
            return nil
        }
        return url
    }

    private static func isDirectImageURL(_ url: URL) -> Bool {
        guard let scheme = url.scheme?.lowercased(), scheme == "https" || scheme == "http" else {
            return false
        }
        let path = url.path.lowercased()
        for ext in directImageExtensions {
            if path.hasSuffix(".\(ext)") { return true }
            if path.contains(".\(ext)?") { return true }
        }
        return false
    }
}

enum ChatImageURLDisplay {
    static func isAnimatedImageURL(_ urlString: String?) -> Bool {
        guard let urlString else { return false }
        let lower = urlString.lowercased()
        if lower.contains("static.klipy.com") { return true }
        if let url = URL(string: urlString) {
            let path = url.path.lowercased()
            if path.hasSuffix(".gif") || path.contains(".gif?") { return true }
            if path.hasSuffix(".webp") || path.contains(".webp?") { return true }
        }
        return false
    }

    static func replySnippet(for imageUrl: String?) -> String {
        if isAnimatedImageURL(imageUrl) {
            return String(localized: "chat_reply_snippet_gif")
        }
        return String(localized: "chat_reply_snippet_photo")
    }
}
