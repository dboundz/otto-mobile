import Foundation

struct GarageCar: Identifiable, Equatable, Codable {
    let id: String
    let nickname: String
    let make: String
    let makeId: String?
    let model: String
    let year: Int?
    let color: String?
    let logoSlug: String?
    let isPrimary: Bool
    /// Mirrors server `sortOrder`; lower values appear first.
    let sortOrder: Int?
    let photoUrl: String?

    var displayName: String {
        if nickname.isEmpty {
            return [make, model].joined(separator: " ")
        }
        return nickname
    }

    var detailLine: String {
        var parts: [String] = []
        if let year { parts.append(String(year)) }
        parts.append(make)
        parts.append(model)
        return parts.joined(separator: " ")
    }

    var resolvedLogoSlug: String? {
        CarBrandLogoCatalog.resolvedLogoSlug(
            logoSlug: logoSlug,
            makeId: makeId,
            makeName: make
        )
    }

    var brandLogoURL: URL? {
        CarBrandLogoCatalog.logoURL(slug: resolvedLogoSlug)
    }
}
