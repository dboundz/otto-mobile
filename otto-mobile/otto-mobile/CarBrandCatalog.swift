import Foundation

struct CarBrandLogoVariant: Codable, Hashable {
    let slug: String
    let label: String
}

struct CarBrand: Codable, Identifiable, Hashable {
    let id: String
    let name: String
    let defaultLogoSlug: String? = nil
    let logoVariants: [CarBrandLogoVariant]? = nil

    var resolvedDefaultLogoSlug: String? {
        defaultLogoSlug ?? id
    }

    var hasLogoPickerOptions: Bool {
        guard let variants = logoVariants, !variants.isEmpty else { return false }
        return true
    }

    func logoPickerOptions() -> [(slug: String, label: String)] {
        var options: [(slug: String, label: String)] = []
        if let defaultSlug = resolvedDefaultLogoSlug {
            options.append((slug: defaultSlug, label: name))
        }
        for variant in logoVariants ?? [] {
            if !options.contains(where: { $0.slug == variant.slug }) {
                options.append((slug: variant.slug, label: variant.label))
            }
        }
        return options
    }
}

enum CarBrandCatalog {
    static let allBrands: [CarBrand] = {
        guard let url = Bundle.main.url(forResource: "carBrands", withExtension: "json") else {
            return []
        }
        guard let data = try? Data(contentsOf: url) else { return [] }
        return (try? JSONDecoder().decode([CarBrand].self, from: data)) ?? []
    }()

    static func brand(forMakeId makeId: String?) -> CarBrand? {
        guard let makeId = makeId?.trimmingCharacters(in: .whitespacesAndNewlines), !makeId.isEmpty else {
            return nil
        }
        return allBrands.first { $0.id == makeId }
    }

    static func brand(matchingMakeName name: String) -> CarBrand? {
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return nil }
        return allBrands.first { $0.name.caseInsensitiveCompare(trimmed) == .orderedSame }
    }
}

enum CarBrandLogoCatalog {
    static let publicBaseURL = URL(string: "https://otto-motto-upload.s3.us-east-1.amazonaws.com/car-brands")!

    /// Bump when S3 car-brand logos are reprocessed so existing installs refresh cached PNGs.
    static let assetCacheVersion = 1

    private static var cacheBuster: String {
        let appVersion = (Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String) ?? "0"
        let build = (Bundle.main.infoDictionary?["CFBundleVersion"] as? String) ?? "0"
        return "\(assetCacheVersion)-\(appVersion)-\(build)"
    }

    static func logoURL(slug: String?) -> URL? {
        guard let slug = slug?.trimmingCharacters(in: .whitespacesAndNewlines), !slug.isEmpty else {
            return nil
        }
        var components = URLComponents(
            url: publicBaseURL.appendingPathComponent("\(slug).png"),
            resolvingAgainstBaseURL: false
        )
        components?.queryItems = [URLQueryItem(name: "v", value: cacheBuster)]
        return components?.url
    }

    static func resolvedLogoSlug(
        logoSlug: String?,
        makeId: String?,
        makeName: String
    ) -> String? {
        if let logoSlug = logoSlug?.trimmingCharacters(in: .whitespacesAndNewlines), !logoSlug.isEmpty {
            return logoSlug
        }
        if let brand = CarBrandCatalog.brand(forMakeId: makeId) {
            return brand.resolvedDefaultLogoSlug
        }
        if let brand = CarBrandCatalog.brand(matchingMakeName: makeName) {
            return brand.resolvedDefaultLogoSlug
        }
        return nil
    }

    static func suggestedLogoSlug(makeId: String?, model: String) -> String? {
        guard let brand = CarBrandCatalog.brand(forMakeId: makeId) else { return nil }
        let trimmedModel = model.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedModel.isEmpty else { return brand.resolvedDefaultLogoSlug }

        for variant in brand.logoVariants ?? [] {
            if trimmedModel.localizedCaseInsensitiveContains(variant.label) {
                return variant.slug
            }
        }
        return brand.resolvedDefaultLogoSlug
    }

    static func defaultLogoSlug(forMakeId makeId: String?) -> String? {
        CarBrandCatalog.brand(forMakeId: makeId)?.resolvedDefaultLogoSlug
    }
}
