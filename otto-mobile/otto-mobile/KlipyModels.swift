import Foundation

struct KlipyGifSelection: Equatable {
    let slug: String
    let title: String
    let previewURL: URL
    let sendURL: URL
    let width: Int
    let height: Int
}

struct KlipyGifItem: Identifiable, Equatable {
    let id: Int64
    let slug: String
    let title: String
    let previewURL: URL
    let sendURL: URL
    let width: Int
    let height: Int

    var selection: KlipyGifSelection {
        KlipyGifSelection(
            slug: slug,
            title: title,
            previewURL: previewURL,
            sendURL: sendURL,
            width: width,
            height: height
        )
    }
}

struct KlipyGifListPage: Equatable {
    let items: [KlipyGifItem]
    let hasMore: Bool
}

private struct KlipyAPIEnvelope<T: Decodable>: Decodable {
    let result: Bool
    let data: T?
}

private struct KlipyGifListData: Decodable {
    let data: [KlipyGifRecord]?
    let currentPage: Int?
    let perPage: Int?
    let hasNext: Bool?

    enum CodingKeys: String, CodingKey {
        case data
        case currentPage = "current_page"
        case perPage = "per_page"
        case hasNext = "has_next"
    }
}

private struct KlipyGifRecord: Decodable {
    let id: Int64?
    let slug: String?
    let title: String?
    let file: KlipyGifFileBundle?
}

private struct KlipyGifFileBundle: Decodable {
    let md: KlipyGifSizeFormats?
    let sm: KlipyGifSizeFormats?
    let hd: KlipyGifSizeFormats?
}

private struct KlipyGifSizeFormats: Decodable {
    let gif: KlipyGifFormatAsset?
    let webp: KlipyGifFormatAsset?
    let jpg: KlipyGifFormatAsset?
}

private struct KlipyGifFormatAsset: Decodable {
    let url: String?
    let width: Int?
    let height: Int?
}

enum KlipyAPIClient {
    enum Error: LocalizedError {
        case notConfigured
        case invalidURL
        case httpStatus(Int)
        case decodeFailed
        case emptyResults

        var errorDescription: String? {
            switch self {
            case .notConfigured:
                return "GIF search isn't available right now."
            case .invalidURL:
                return "Couldn't reach GIF search."
            case .httpStatus:
                return "GIF search failed. Try again."
            case .decodeFailed, .emptyResults:
                return "Couldn't load GIFs. Try again."
            }
        }
    }

    private static let baseURL = URL(string: "https://api.klipy.com")!

    static func fetchTrending(
        customerId: String,
        locale: String,
        page: Int,
        perPage: Int = 24
    ) async throws -> KlipyGifListPage {
        try await fetchList(
            pathComponents: ["api", "v1", KlipyConfiguration.appKey, "gifs", "trending"],
            queryItems: listQueryItems(
                customerId: customerId,
                locale: locale,
                page: page,
                perPage: perPage,
                searchQuery: nil
            )
        )
    }

    static func search(
        query: String,
        customerId: String,
        locale: String,
        page: Int,
        perPage: Int = 24
    ) async throws -> KlipyGifListPage {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            return try await fetchTrending(customerId: customerId, locale: locale, page: page, perPage: perPage)
        }
        return try await fetchList(
            pathComponents: ["api", "v1", KlipyConfiguration.appKey, "gifs", "search"],
            queryItems: listQueryItems(
                customerId: customerId,
                locale: locale,
                page: page,
                perPage: perPage,
                searchQuery: trimmed
            )
        )
    }

    static func reportShare(
        slug: String,
        customerId: String,
        searchQuery: String?
    ) async {
        guard KlipyConfiguration.isConfigured else { return }
        var url = baseURL
        for component in ["api", "v1", KlipyConfiguration.appKey, "gifs", "share", slug] {
            url = url.appendingPathComponent(component)
        }
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        var body: [String: String] = ["customer_id": customerId]
        if let searchQuery, !searchQuery.isEmpty {
            body["q"] = searchQuery
        } else {
            body["q"] = ""
        }
        request.httpBody = try? JSONSerialization.data(withJSONObject: body)
        _ = try? await URLSession.shared.data(for: request)
    }

    private static func listQueryItems(
        customerId: String,
        locale: String,
        page: Int,
        perPage: Int,
        searchQuery: String?
    ) -> [URLQueryItem] {
        var items: [URLQueryItem] = [
            URLQueryItem(name: "page", value: String(max(1, page))),
            URLQueryItem(name: "per_page", value: String(min(50, max(1, perPage)))),
            URLQueryItem(name: "customer_id", value: customerId),
            URLQueryItem(name: "locale", value: locale),
            URLQueryItem(name: "content_filter", value: "medium"),
            URLQueryItem(name: "format_filter", value: "gif,webp"),
        ]
        if let searchQuery {
            items.append(URLQueryItem(name: "q", value: searchQuery))
        }
        return items
    }

    private static func fetchList(
        pathComponents: [String],
        queryItems: [URLQueryItem]
    ) async throws -> KlipyGifListPage {
        guard KlipyConfiguration.isConfigured else { throw Error.notConfigured }
        var url = baseURL
        for component in pathComponents {
            url = url.appendingPathComponent(component)
        }
        guard var components = URLComponents(url: url, resolvingAgainstBaseURL: false) else {
            throw Error.invalidURL
        }
        components.queryItems = queryItems
        guard let finalURL = components.url else { throw Error.invalidURL }

        var request = URLRequest(url: finalURL)
        request.httpMethod = "GET"
        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse else { throw Error.decodeFailed }
        guard (200 ... 299).contains(http.statusCode) else { throw Error.httpStatus(http.statusCode) }

        let decoder = JSONDecoder()
        let envelope = try decoder.decode(KlipyAPIEnvelope<KlipyGifListData>.self, from: data)
        guard envelope.result, let payload = envelope.data else { throw Error.decodeFailed }
        let records = payload.data ?? []
        let items = records.compactMap(parseItem(record:))
        let currentPage = payload.currentPage ?? 1
        if items.isEmpty, currentPage == 1 { throw Error.emptyResults }
        let hasMore = payload.hasNext ?? (items.count >= (payload.perPage ?? 24))
        return KlipyGifListPage(items: items, hasMore: hasMore)
    }

    private static func parseItem(record: KlipyGifRecord) -> KlipyGifItem? {
        guard let slug = record.slug?.trimmingCharacters(in: .whitespacesAndNewlines), !slug.isEmpty else {
            return nil
        }
        let bundle = record.file
        let previewAsset =
            bundle?.sm?.gif
            ?? bundle?.sm?.webp
            ?? bundle?.md?.gif
            ?? bundle?.md?.webp
        let sendAsset =
            bundle?.md?.gif
            ?? bundle?.md?.webp
            ?? bundle?.hd?.gif
            ?? bundle?.hd?.webp
            ?? previewAsset
        guard let previewAsset,
              let sendAsset,
              let previewURL = url(from: previewAsset.url),
              let sendURL = url(from: sendAsset.url)
        else { return nil }
        return KlipyGifItem(
            id: record.id ?? Int64(slug.hashValue),
            slug: slug,
            title: record.title?.trimmingCharacters(in: .whitespacesAndNewlines) ?? "",
            previewURL: previewURL,
            sendURL: sendURL,
            width: sendAsset.width ?? previewAsset.width ?? 200,
            height: sendAsset.height ?? previewAsset.height ?? 200
        )
    }

    private static func url(from raw: String?) -> URL? {
        guard let raw else { return nil }
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return nil }
        return URL(string: trimmed)
    }
}
