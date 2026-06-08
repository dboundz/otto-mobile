import CoreLocation
import Foundation

enum CheckpointDensityTier: String, CaseIterable, Identifiable {
    case fewer
    case recommended
    case more
    case maximum

    var id: String { rawValue }

    var titleKey: String {
        switch self {
        case .fewer: return "route_builder_density_fewer"
        case .recommended: return "route_builder_density_recommended"
        case .more: return "route_builder_density_more"
        case .maximum: return "route_builder_density_maximum"
        }
    }
}

enum RouteAutoCheckpointGenerator {
    struct IntervalOption: Identifiable, Equatable {
        let miles: Double
        let spacingMeters: Double
        let checkpointCount: Int

        var id: Double { miles }

        var intervalLabel: String {
            RouteAutoCheckpointGenerator.intervalLabel(miles: miles)
        }

        var summaryText: String {
            "Every \(intervalLabel) · ~\(checkpointCount) checkpoint\(checkpointCount == 1 ? "" : "s")"
        }
    }

    struct DensityOption: Identifiable, Equatable {
        let tier: CheckpointDensityTier
        let spacingMeters: Double
        let checkpointCount: Int
        let averageSpacingMiles: Double

        var id: CheckpointDensityTier { tier }
    }

    enum Options {
        static let metersPerMile = 1609.344
        static let halfMileMeters = metersPerMile * 0.5
        static let finishBufferMeters = 100.0
        static let standardIntervalMiles: [Double] = [0.5, 1, 2, 5, 10, 100]
        /// Fun cap when auto-selecting a default interval on save (user may pick tighter manually).
        static let maxRecommendedCheckpoints = 12
        /// Turns per mile above which complexity nudges checkpoint count up.
        static let highComplexityTurnsPerMile = 4.0
    }

    static func spacingMeters(forMiles miles: Double) -> Double {
        miles * Options.metersPerMile
    }

    static func intervalLabel(miles: Double) -> String {
        if miles == 0.5 { return "½ mile" }
        if miles == floor(miles) {
            let whole = Int(miles)
            return whole == 1 ? "1 mile" : "\(whole) miles"
        }
        return String(format: "%.1f miles", miles)
    }

    static func canOfferAutoCheckpoints(roadCoordinates: [CLLocationCoordinate2D]) -> Bool {
        !viableIntervals(roadCoordinates: roadCoordinates).isEmpty
    }

    static func viableIntervals(roadCoordinates: [CLLocationCoordinate2D]) -> [IntervalOption] {
        Options.standardIntervalMiles.compactMap { miles in
            let spacingMeters = spacingMeters(forMiles: miles)
            let generated = generate(roadCoordinates: roadCoordinates, spacingMeters: spacingMeters)
            guard !generated.isEmpty else { return nil }
            return IntervalOption(
                miles: miles,
                spacingMeters: spacingMeters,
                checkpointCount: generated.count
            )
        }
    }

    static func routeLengthMiles(roadCoordinates: [CLLocationCoordinate2D]) -> Double {
        guard roadCoordinates.count >= 2 else { return 0 }
        return RouteMapGeometry.polylineTotalLength(roadCoordinates) / Options.metersPerMile
    }

    static func routeComplexityScore(roadCoordinates: [CLLocationCoordinate2D], turnCount: Int) -> Double {
        let miles = max(routeLengthMiles(roadCoordinates: roadCoordinates), 0.1)
        let turnsPerMile = Double(turnCount) / miles
        let turnFactor = min(1.0, turnsPerMile / Options.highComplexityTurnsPerMile)
        return turnFactor
    }

    static func targetCheckpointCount(routeMiles: Double, turnCount: Int = 0, roadCoordinates: [CLLocationCoordinate2D] = []) -> Int {
        let base: Int
        switch routeMiles {
        case ..<1.5: base = 2
        case ..<4: base = 3
        case ..<10: base = 5
        case ..<25: base = 7
        case ..<50: base = 9
        default: base = 11
        }
        let complexity = roadCoordinates.isEmpty
            ? min(1.0, Double(turnCount) / max(routeMiles, 0.1) / Options.highComplexityTurnsPerMile)
            : routeComplexityScore(roadCoordinates: roadCoordinates, turnCount: turnCount)
        let nudge = complexity >= 0.75 ? 1 : (complexity >= 0.4 ? 0 : 0)
        return min(base + nudge, Options.maxRecommendedCheckpoints)
    }

    static func recommendedDefaultInterval(
        roadCoordinates: [CLLocationCoordinate2D],
        turnCount: Int = 0
    ) -> IntervalOption? {
        let intervals = viableIntervals(roadCoordinates: roadCoordinates)
        guard !intervals.isEmpty else { return nil }

        let target = targetCheckpointCount(
            routeMiles: routeLengthMiles(roadCoordinates: roadCoordinates),
            turnCount: turnCount,
            roadCoordinates: roadCoordinates
        )
        let candidates = intervals.filter { $0.checkpointCount <= Options.maxRecommendedCheckpoints }
        let pool = candidates.isEmpty ? intervals : candidates

        return pool.min { lhs, rhs in
            let lhsDelta = abs(lhs.checkpointCount - target)
            let rhsDelta = abs(rhs.checkpointCount - target)
            if lhsDelta != rhsDelta { return lhsDelta < rhsDelta }
            return lhs.miles > rhs.miles
        }
    }

    static func densityOptions(
        roadCoordinates: [CLLocationCoordinate2D],
        turnCount: Int = 0
    ) -> [DensityOption] {
        let intervals = viableIntervals(roadCoordinates: roadCoordinates)
        guard !intervals.isEmpty else { return [] }

        let routeMiles = routeLengthMiles(roadCoordinates: roadCoordinates)
        let recommended = recommendedDefaultInterval(roadCoordinates: roadCoordinates, turnCount: turnCount)
        let recommendedIndex = recommended.flatMap { rec in intervals.firstIndex(where: { $0.spacingMeters == rec.spacingMeters }) } ?? 0

        func densityOption(tier: CheckpointDensityTier, interval: IntervalOption) -> DensityOption {
            let avg = interval.checkpointCount > 0
                ? routeMiles / Double(interval.checkpointCount + 1)
                : routeMiles
            return DensityOption(
                tier: tier,
                spacingMeters: interval.spacingMeters,
                checkpointCount: interval.checkpointCount,
                averageSpacingMiles: avg
            )
        }

        let fewerIndex = intervals.indices
            .filter { intervals[$0].checkpointCount <= (recommended?.checkpointCount ?? Int.max) }
            .max(by: { intervals[$0].miles > intervals[$1].miles }) ?? recommendedIndex

        let moreIndex = intervals.indices
            .filter { $0 < recommendedIndex }
            .max() ?? max(0, recommendedIndex - 1)

        let maximumInterval = intervals
            .filter { $0.checkpointCount <= Options.maxRecommendedCheckpoints }
            .min(by: { $0.miles < $1.miles }) ?? intervals[0]
        let maximumIndex = intervals.firstIndex(where: { $0.spacingMeters == maximumInterval.spacingMeters }) ?? 0

        let tiers: [(CheckpointDensityTier, Int)] = [
            (.fewer, fewerIndex),
            (.recommended, recommendedIndex),
            (.more, moreIndex),
            (.maximum, maximumIndex),
        ]

        var seen = Set<Double>()
        var options: [DensityOption] = []
        for (tier, index) in tiers {
            let interval = intervals[index]
            guard seen.insert(interval.spacingMeters).inserted else { continue }
            options.append(densityOption(tier: tier, interval: interval))
        }

        if !options.contains(where: { $0.tier == .recommended }), let recommended {
            options.append(densityOption(tier: .recommended, interval: recommended))
        }

        return options.sorted { lhs, rhs in
            tierOrder(lhs.tier) < tierOrder(rhs.tier)
        }
    }

    private static func tierOrder(_ tier: CheckpointDensityTier) -> Int {
        switch tier {
        case .fewer: return 0
        case .recommended: return 1
        case .more: return 2
        case .maximum: return 3
        }
    }

    static func densityOption(
        for tier: CheckpointDensityTier,
        roadCoordinates: [CLLocationCoordinate2D],
        turnCount: Int = 0
    ) -> DensityOption? {
        densityOptions(roadCoordinates: roadCoordinates, turnCount: turnCount).first { $0.tier == tier }
    }

    static func adjacentDensityOption(
        from tier: CheckpointDensityTier,
        roadCoordinates: [CLLocationCoordinate2D],
        turnCount: Int = 0,
        direction: Int
    ) -> DensityOption? {
        let options = densityOptions(roadCoordinates: roadCoordinates, turnCount: turnCount)
        guard options.count > 1 else { return nil }

        let currentIndex: Int
        if let current = densityOption(for: tier, roadCoordinates: roadCoordinates, turnCount: turnCount),
           let index = options.firstIndex(where: { $0.spacingMeters == current.spacingMeters }) {
            currentIndex = index
        } else if let index = options.firstIndex(where: { $0.tier == .recommended }) {
            currentIndex = index
        } else {
            currentIndex = 0
        }

        let nextIndex = currentIndex + direction
        guard options.indices.contains(nextIndex) else { return nil }
        let next = options[nextIndex]
        guard next.spacingMeters != options[currentIndex].spacingMeters else { return nil }
        return next
    }

    static func adjacentTier(from tier: CheckpointDensityTier, direction: Int) -> CheckpointDensityTier? {
        let all = CheckpointDensityTier.allCases
        guard let index = all.firstIndex(of: tier) else { return nil }
        let next = index + direction
        guard all.indices.contains(next) else { return nil }
        return all[next]
    }

    static func generate(
        roadCoordinates: [CLLocationCoordinate2D],
        spacingMeters: Double = Options.halfMileMeters
    ) -> [CLLocationCoordinate2D] {
        guard roadCoordinates.count >= 2 else { return [] }

        let totalLength = RouteMapGeometry.polylineTotalLength(roadCoordinates)
        let maxArcLength = max(0, totalLength - Options.finishBufferMeters)
        guard maxArcLength > spacingMeters * 0.5 else { return [] }

        var coordinates: [CLLocationCoordinate2D] = []
        var distance = spacingMeters
        while distance < maxArcLength {
            if let coordinate = RouteMapGeometry.coordinateAtArcLength(distance, on: roadCoordinates) {
                coordinates.append(coordinate)
            }
            distance += spacingMeters
        }
        return coordinates
    }
}
