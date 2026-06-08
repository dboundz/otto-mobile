import Foundation

/// Shared link-preview thumbnail sizing and Instagram detection for chat cards.
enum ChatLinkPreviewDisplay {
    /// Default OG thumbnail height (wide-short frame for generic links).
    static let defaultThumbnailHeight: CGFloat = 148

    /// Instagram feed portrait width:height.
    static let portraitAspectRatio: CGFloat = 4.0 / 5.0

    static func isInstagramStyleLink(url: URL?, siteName: String?) -> Bool {
        if let siteName, siteName.range(of: "instagram", options: .caseInsensitive) != nil {
            return true
        }
        guard let url else { return false }
        return isInstagramPublicURL(url)
    }

    static func usesPortraitThumbnail(
        url: URL?,
        siteName: String?
    ) -> Bool {
        isInstagramStyleLink(url: url, siteName: siteName)
    }

    static func usesPortraitThumbnail(preview: CircleChatMessageDTO.LinkPreviewDTO) -> Bool {
        let urlString = preview.finalUrl ?? preview.url
        let url = urlString.flatMap { URL(string: $0) }
        return usesPortraitThumbnail(url: url, siteName: preview.siteName)
    }

    private static func isInstagramPublicURL(_ url: URL) -> Bool {
        let host = (url.host ?? "").replacingOccurrences(of: "www.", with: "").lowercased()
        guard host == "instagram.com" else { return false }
        return parseInstagramPublicPath(url.path) != nil
    }

    /// Matches backend `parseInstagramPublicPath` — post, reel, reels, or IGTV.
    private static func parseInstagramPublicPath(_ pathname: String) -> (kind: String, shortcode: String)? {
        let pattern = #"^/(p|reel|reels|tv)/([^/?#]+)/?$"#
        guard let regex = try? NSRegularExpression(pattern: pattern, options: .caseInsensitive) else {
            return nil
        }
        let range = NSRange(pathname.startIndex..., in: pathname)
        guard let match = regex.firstMatch(in: pathname, options: [], range: range),
              match.numberOfRanges >= 3,
              let kindRange = Range(match.range(at: 1), in: pathname),
              let shortcodeRange = Range(match.range(at: 2), in: pathname) else {
            return nil
        }
        return (kind: String(pathname[kindRange]).lowercased(), shortcode: String(pathname[shortcodeRange]))
    }
}
