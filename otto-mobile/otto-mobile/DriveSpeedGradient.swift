import CoreLocation
import MapboxMaps
import SwiftUI
import Turf
import UIKit

/// GPS sample used to render a driven path with speed-based color.
struct DrivePathSample: Equatable {
    let coordinate: CLLocationCoordinate2D
    let speedMph: Double
    let capturedAt: Date?

    init(coordinate: CLLocationCoordinate2D, speedMph: Double, capturedAt: Date? = nil) {
        self.coordinate = coordinate
        self.speedMph = speedMph
        self.capturedAt = capturedAt
    }

    init(location: CLLocation, speedMph: Double) {
        self.coordinate = location.coordinate
        self.speedMph = speedMph
        self.capturedAt = location.timestamp
    }
}

extension DrivePathSample {
    static func from(dto: DrivePathPointDTO) -> DrivePathSample? {
        guard dto.lat.isFinite, dto.lng.isFinite else { return nil }
        let coordinate = CLLocationCoordinate2D(latitude: dto.lat, longitude: dto.lng)
        guard CLLocationCoordinate2DIsValid(coordinate) else { return nil }
        return DrivePathSample(
            coordinate: coordinate,
            speedMph: dto.speedMph,
            capturedAt: dto.capturedAt
        )
    }
}

enum DriveSpeedGradient {
    private static let maxSpeedMph = 200.0
    private static let maxRenderVertices = 720
    private static let maxGradientStops = 64

    private static let speedStops: [(mph: Double, color: UIColor)] = [
        (0, UIColor(red: 0, green: 209 / 255, blue: 255 / 255, alpha: 1)),
        (40, UIColor(red: 0, green: 136 / 255, blue: 255 / 255, alpha: 1)),
        (80, UIColor(red: 91 / 255, green: 91 / 255, blue: 255 / 255, alpha: 1)),
        (120, UIColor(red: 176 / 255, green: 38 / 255, blue: 255 / 255, alpha: 1)),
        (160, UIColor(red: 255 / 255, green: 106 / 255, blue: 0, alpha: 1)),
        (200, UIColor(red: 255 / 255, green: 213 / 255, blue: 0, alpha: 1)),
    ]

    struct RenderVertex: Equatable {
        let coordinate: CLLocationCoordinate2D
        let speedMph: Double
        let lineProgress: Double
    }

    struct LineSegment: Identifiable, Equatable {
        let id: String
        let coordinates: [CLLocationCoordinate2D]
        let color: UIColor
    }

    static func clampSpeed(_ speed: Double) -> Double {
        guard speed.isFinite else { return 0 }
        return min(maxSpeedMph, max(0, speed))
    }

    static func interpolateSpeed(_ startSpeed: Double, _ endSpeed: Double, t: Double) -> Double {
        let clampedT = min(1, max(0, t))
        let start = clampSpeed(startSpeed)
        let end = clampSpeed(endSpeed)
        return start + (end - start) * clampedT
    }

    static func interpolateColor(_ colorA: UIColor, _ colorB: UIColor, t: Double) -> UIColor {
        let clampedT = CGFloat(min(1, max(0, t)))
        var r1: CGFloat = 0
        var g1: CGFloat = 0
        var b1: CGFloat = 0
        var a1: CGFloat = 0
        var r2: CGFloat = 0
        var g2: CGFloat = 0
        var b2: CGFloat = 0
        var a2: CGFloat = 0
        colorA.getRed(&r1, green: &g1, blue: &b1, alpha: &a1)
        colorB.getRed(&r2, green: &g2, blue: &b2, alpha: &a2)
        return UIColor(
            red: r1 + (r2 - r1) * clampedT,
            green: g1 + (g2 - g1) * clampedT,
            blue: b1 + (b2 - b1) * clampedT,
            alpha: a1 + (a2 - a1) * clampedT
        )
    }

    static func getSpeedColor(_ speed: Double) -> UIColor {
        let mph = clampSpeed(speed)
        guard let first = speedStops.first else {
            return UIColor.white
        }
        if mph <= first.mph {
            return first.color
        }
        for index in 1..<speedStops.count {
            let upper = speedStops[index]
            if mph <= upper.mph {
                let lower = speedStops[index - 1]
                let span = max(upper.mph - lower.mph, 0.0001)
                let t = (mph - lower.mph) / span
                return interpolateColor(lower.color, upper.color, t: t)
            }
        }
        return speedStops.last?.color ?? first.color
    }

    /// Drops consecutive samples at essentially the same location (breaks line-gradient / segment paint).
    static func deduplicatedSamples(_ samples: [DrivePathSample]) -> [DrivePathSample] {
        var kept: [DrivePathSample] = []
        for sample in samples {
            if let last = kept.last {
                let distanceMeters = coordinateDistanceMeters(last.coordinate, sample.coordinate)
                if distanceMeters < 1 {
                    continue
                }
            }
            kept.append(sample)
        }
        return kept
    }

    /// Derives movement speed from GPS spacing when device-reported speed is missing or stuck at zero.
    static func enrichedSamples(_ samples: [DrivePathSample]) -> [DrivePathSample] {
        let samples = deduplicatedSamples(samples)
        guard !samples.isEmpty else { return [] }

        var enriched: [DrivePathSample] = []
        enriched.reserveCapacity(samples.count)

        for index in 0..<samples.count {
            let sample = samples[index]
            let reported = clampSpeed(sample.speedMph)
            var effective = reported

            if index > 0 {
                let previous = samples[index - 1]
                let distanceMeters = coordinateDistanceMeters(previous.coordinate, sample.coordinate)

                if let previousAt = previous.capturedAt, let currentAt = sample.capturedAt {
                    let elapsed = currentAt.timeIntervalSince(previousAt)
                    if elapsed >= 0.35, elapsed <= 180 {
                        let inferredMph = clampSpeed((distanceMeters / elapsed) * 2.23694)
                        if distanceMeters < 5, inferredMph < 3 {
                            effective = 0
                        } else if reported < 3 || reported < inferredMph * 0.5 {
                            effective = inferredMph
                        } else if abs(inferredMph - reported) >= 4 {
                            effective = inferredMph
                        } else {
                            effective = max(reported, inferredMph)
                        }
                    }
                } else if distanceMeters < 4 {
                    effective = 0
                }
            }

            enriched.append(
                DrivePathSample(
                    coordinate: sample.coordinate,
                    speedMph: clampSpeed(effective),
                    capturedAt: sample.capturedAt
                )
            )
        }

        return enriched
    }

    /// Maps this drive's actual speed range onto the color scale so low-speed drives still show visible ramps.
    /// Absolute 0–200 mph is used when the drive actually reaches highway speeds.
    static func samplesForRendering(_ samples: [DrivePathSample]) -> [DrivePathSample] {
        let enriched = enrichedSamples(samples)
        guard enriched.count >= 2 else { return enriched }

        let speeds = enriched.map(\.speedMph)
        guard let minSpeed = speeds.min(), let maxSpeed = speeds.max() else { return enriched }
        let span = maxSpeed - minSpeed
        guard span >= 2 else { return enriched }

        // Very fast drives: keep absolute mph → color (spec 0–200).
        guard maxSpeed <= 95 else { return enriched }

        // Typical drives: stretch [min, max] across cyan → indigo/purple for visible ramps.
        let displayTop = min(110, max(52, span * 6, maxSpeed + 12))
        return enriched.map { sample in
            let t = (sample.speedMph - minSpeed) / span
            let displaySpeed = t * displayTop
            return DrivePathSample(
                coordinate: sample.coordinate,
                speedMph: clampSpeed(displaySpeed),
                capturedAt: sample.capturedAt
            )
        }
    }

    static func coordinates(from samples: [DrivePathSample]) -> [CLLocationCoordinate2D] {
        buildRenderVertices(from: samples).map(\.coordinate)
    }

    /// Raw GPS coordinates in capture order (for path length checks and under-strokes).
    static func pathCoordinates(from samples: [DrivePathSample]) -> [CLLocationCoordinate2D] {
        enrichedSamples(samples).compactMap { sample in
            let coordinate = sample.coordinate
            guard CLLocationCoordinate2DIsValid(coordinate),
                  coordinate.latitude.isFinite,
                  coordinate.longitude.isFinite else {
                return nil
            }
            return coordinate
        }
    }

    /// Sum of segment lengths along the path (meters), using deduplicated coordinates.
    static func polylineDistanceMeters(from samples: [DrivePathSample]) -> Double {
        let coordinates = pathCoordinates(from: samples)
        guard coordinates.count >= 2 else { return 0 }
        var total = 0.0
        for index in 1..<coordinates.count {
            total += coordinateDistanceMeters(coordinates[index - 1], coordinates[index])
        }
        return total
    }

    /// True when we have enough GPS points along a real path to render a speed-colored trail.
    static func hasUsableSpeedPathData(_ samples: [DrivePathSample]) -> Bool {
        let coordinates = pathCoordinates(from: samples)
        guard coordinates.count >= 2 else { return false }

        var totalDistanceMeters = 0.0
        for index in 1..<coordinates.count {
            totalDistanceMeters += coordinateDistanceMeters(coordinates[index - 1], coordinates[index])
        }
        return totalDistanceMeters >= 20
    }

    static func colorHexString(_ color: UIColor) -> String {
        var red: CGFloat = 0
        var green: CGFloat = 0
        var blue: CGFloat = 0
        var alpha: CGFloat = 0
        color.getRed(&red, green: &green, blue: &blue, alpha: &alpha)
        return String(
            format: "#%02X%02X%02X",
            Int((red * 255).rounded()),
            Int((green * 255).rounded()),
            Int((blue * 255).rounded())
        )
    }

    static func buildRenderVertices(from samples: [DrivePathSample]) -> [RenderVertex] {
        let samples = samplesForRendering(samples)
        guard samples.count >= 2 else { return [] }

        var densified: [(coordinate: CLLocationCoordinate2D, speedMph: Double)] = [
            (samples[0].coordinate, clampSpeed(samples[0].speedMph)),
        ]

        for index in 0..<(samples.count - 1) {
            let start = samples[index]
            let end = samples[index + 1]
            let distanceMeters = coordinateDistanceMeters(start.coordinate, end.coordinate)
            let startSpeed = clampSpeed(start.speedMph)
            let endSpeed = clampSpeed(end.speedMph)
            let speedDelta = abs(endSpeed - startSpeed)
            let steps = subdivisionCount(
                distanceMeters: distanceMeters,
                speedDelta: speedDelta,
                startSpeed: startSpeed,
                endSpeed: endSpeed
            )

            if steps <= 1 {
                densified.append((end.coordinate, endSpeed))
                continue
            }

            for step in 1...steps {
                let t = Double(step) / Double(steps)
                let coordinate = interpolateCoordinate(start.coordinate, end.coordinate, t: t)
                let speed = interpolateSpeed(startSpeed, endSpeed, t: t)
                densified.append((coordinate, speed))
            }
        }

        let thinned = thinVerticesPreservingSpeed(densified, maxCount: maxRenderVertices)
        return verticesWithLineProgress(thinned)
    }

    static func buildGradientSegments(
        from samples: [DrivePathSample],
        idPrefix: String = "speed-segment",
        maxCount: Int = 500
    ) -> [LineSegment] {
        let vertices = buildRenderVertices(from: samples)
        guard vertices.count >= 2 else { return [] }

        let segments = (0..<(vertices.count - 1)).map { index in
            let start = vertices[index]
            let end = vertices[index + 1]
            let startColor = getSpeedColor(start.speedMph)
            let endColor = getSpeedColor(end.speedMph)
            return LineSegment(
                id: "\(idPrefix)-\(index)",
                coordinates: [start.coordinate, end.coordinate],
                color: interpolateColor(startColor, endColor, t: 0.5)
            )
        }

        return thinSegmentsPreservingColor(segments, maxCount: maxCount)
    }

    static func lineGradientExpression(from samples: [DrivePathSample]) -> Exp? {
        let vertices = buildRenderVertices(from: samples)
        return lineGradientExpression(vertices: vertices, colorForSpeed: getSpeedColor)
    }

    static func trailGradientExpression(from samples: [DrivePathSample]) -> Exp? {
        let vertices = buildRenderVertices(from: samples)
        return trailGradientExpression(vertices: vertices)
    }

    static func trailGradientExpression(vertices: [RenderVertex]) -> Exp? {
        lineGradientExpression(vertices: vertices, colorForSpeed: getSpeedColor)
    }

    static func lineGradientExpression(vertices: [RenderVertex]) -> Exp? {
        lineGradientExpression(vertices: vertices, colorForSpeed: getSpeedColor)
    }

    static func lineGradientExpression(
        vertices: [RenderVertex],
        colorForSpeed: (Double) -> UIColor
    ) -> Exp? {
        guard vertices.count >= 2 else { return nil }

        let stops = gradientStops(from: vertices)
        guard stops.count >= 2 else { return nil }

        var arguments: [Exp.Argument] = []
        arguments.append(contentsOf: Exp(.linear).expressionArguments)
        arguments.append(contentsOf: Exp(.lineProgress).expressionArguments)
        for stop in stops {
            arguments.append(.number(stop.lineProgress))
            arguments.append(contentsOf: colorForSpeed(stop.speedMph).expressionArguments)
        }
        return Exp(operator: .interpolate, arguments: arguments)
    }

    // MARK: - Private

    private static func gradientStops(from vertices: [RenderVertex]) -> [RenderVertex] {
        guard !vertices.isEmpty else { return [] }
        var stops: [RenderVertex] = [vertices[0]]

        for vertex in vertices.dropFirst() {
            guard let last = stops.last else {
                stops.append(vertex)
                continue
            }
            let progressDelta = abs(vertex.lineProgress - last.lineProgress)
            let speedDelta = abs(vertex.speedMph - last.speedMph)
            if progressDelta >= 0.002 || speedDelta >= 1.2 || stops.count == 1 {
                stops.append(vertex)
            }
        }

        if stops.last?.lineProgress != vertices.last?.lineProgress {
            stops.append(vertices[vertices.count - 1])
        }

        if stops.count > maxGradientStops {
            return thinEvenly(stops, maxCount: maxGradientStops)
        }

        return stops
    }

    private static func thinEvenly(_ stops: [RenderVertex], maxCount: Int) -> [RenderVertex] {
        guard stops.count > maxCount, maxCount >= 2 else { return stops }

        var result: [RenderVertex] = []
        let stride = Double(stops.count - 1) / Double(maxCount - 1)
        for index in 0..<maxCount {
            let sourceIndex = min(stops.count - 1, Int((Double(index) * stride).rounded()))
            result.append(stops[sourceIndex])
        }
        if result.first?.lineProgress != stops.first?.lineProgress {
            result[0] = stops[0]
        }
        if result.last?.lineProgress != stops.last?.lineProgress {
            result[result.count - 1] = stops[stops.count - 1]
        }
        return result
    }

    private static func thinSegmentsPreservingColor(_ segments: [LineSegment], maxCount: Int) -> [LineSegment] {
        guard segments.count > maxCount, maxCount >= 2 else { return segments }

        var kept: [LineSegment] = [segments[0]]
        for index in 1..<(segments.count - 1) {
            let candidate = segments[index]
            let lastColor = kept[kept.count - 1].color
            if !colorsAreSimilar(lastColor, candidate.color) {
                kept.append(candidate)
            }
        }
        kept.append(segments[segments.count - 1])

        guard kept.count > maxCount else { return kept }

        let step = Int(ceil(Double(kept.count) / Double(maxCount)))
        var thinned: [LineSegment] = []
        for index in Swift.stride(from: 0, to: kept.count, by: max(1, step)) {
            thinned.append(kept[index])
        }
        if thinned.last?.id != kept.last?.id {
            thinned.append(kept[kept.count - 1])
        }
        return thinned
    }

    private static func colorsAreSimilar(_ lhs: UIColor, _ rhs: UIColor) -> Bool {
        var r1: CGFloat = 0
        var g1: CGFloat = 0
        var b1: CGFloat = 0
        var a1: CGFloat = 0
        var r2: CGFloat = 0
        var g2: CGFloat = 0
        var b2: CGFloat = 0
        var a2: CGFloat = 0
        lhs.getRed(&r1, green: &g1, blue: &b1, alpha: &a1)
        rhs.getRed(&r2, green: &g2, blue: &b2, alpha: &a2)
        let delta = abs(r1 - r2) + abs(g1 - g2) + abs(b1 - b2)
        return delta < 0.08
    }

    private static func subdivisionCount(
        distanceMeters: Double,
        speedDelta: Double,
        startSpeed: Double,
        endSpeed: Double
    ) -> Int {
        let distanceSteps = Int(ceil(distanceMeters / 18))
        let speedSteps = Int(ceil(speedDelta / 3))
        var steps = min(32, max(1, distanceSteps, speedSteps))

        let nearStop = min(startSpeed, endSpeed) < 6
        let moving = max(startSpeed, endSpeed) > 12
        if nearStop, moving {
            steps = max(steps, Int(ceil(speedDelta / 3)))
        }

        return min(28, steps)
    }

    private static func thinVerticesPreservingSpeed(
        _ vertices: [(coordinate: CLLocationCoordinate2D, speedMph: Double)],
        maxCount: Int
    ) -> [(coordinate: CLLocationCoordinate2D, speedMph: Double)] {
        guard vertices.count > maxCount, maxCount >= 2 else { return vertices }

        var kept: [(coordinate: CLLocationCoordinate2D, speedMph: Double)] = [vertices[0]]
        for index in 1..<(vertices.count - 1) {
            let candidate = vertices[index]
            let last = kept[kept.count - 1]
            let speedDelta = abs(candidate.speedMph - last.speedMph)
            if speedDelta >= 2 {
                kept.append(candidate)
            }
        }
        kept.append(vertices[vertices.count - 1])

        guard kept.count > maxCount else { return kept }

        var thinned: [(coordinate: CLLocationCoordinate2D, speedMph: Double)] = []
        let stride = Double(kept.count - 1) / Double(maxCount - 1)
        for index in 0..<maxCount {
            let sourceIndex = min(kept.count - 1, Int((Double(index) * stride).rounded()))
            thinned.append(kept[sourceIndex])
        }
        if thinned.first?.coordinate.latitude != kept.first?.coordinate.latitude {
            thinned[0] = kept[0]
        }
        if thinned.last?.coordinate.latitude != kept.last?.coordinate.latitude {
            thinned[thinned.count - 1] = kept[kept.count - 1]
        }
        return thinned
    }

    private static func verticesWithLineProgress(
        _ vertices: [(coordinate: CLLocationCoordinate2D, speedMph: Double)]
    ) -> [RenderVertex] {
        guard vertices.count >= 2 else {
            guard let only = vertices.first else { return [] }
            return [RenderVertex(coordinate: only.coordinate, speedMph: only.speedMph, lineProgress: 0)]
        }

        var cumulative: [Double] = [0]
        for index in 1..<vertices.count {
            let segment = coordinateDistanceMeters(vertices[index - 1].coordinate, vertices[index].coordinate)
            cumulative.append(cumulative[index - 1] + segment)
        }

        let total = cumulative.last ?? 0
        let scale = total > 0 ? 1.0 / total : 0

        return zip(vertices, cumulative).map { vertex, distance in
            RenderVertex(
                coordinate: vertex.coordinate,
                speedMph: vertex.speedMph,
                lineProgress: min(1, max(0, distance * scale))
            )
        }
    }

    private static func interpolateCoordinate(
        _ start: CLLocationCoordinate2D,
        _ end: CLLocationCoordinate2D,
        t: Double
    ) -> CLLocationCoordinate2D {
        let clampedT = min(1, max(0, t))
        return CLLocationCoordinate2D(
            latitude: start.latitude + (end.latitude - start.latitude) * clampedT,
            longitude: start.longitude + (end.longitude - start.longitude) * clampedT
        )
    }

    private static func coordinateDistanceMeters(
        _ start: CLLocationCoordinate2D,
        _ end: CLLocationCoordinate2D
    ) -> Double {
        CLLocation(latitude: start.latitude, longitude: start.longitude)
            .distance(from: CLLocation(latitude: end.latitude, longitude: end.longitude))
    }

    /// Full 0–200 mph palette for UI legends (matches trail `line-gradient` stops).
    static var legendLinearGradient: LinearGradient {
        let sampleCount = 40
        let stops = (0..<sampleCount).map { index -> Gradient.Stop in
            let location = CGFloat(index) / CGFloat(max(1, sampleCount - 1))
            let mph = maxSpeedMph * Double(location)
            return Gradient.Stop(color: Color(getSpeedColor(mph)), location: location)
        }
        return LinearGradient(
            gradient: Gradient(stops: stops),
            startPoint: .leading,
            endPoint: .trailing
        )
    }
}

/// Speed scale ledge shown above drive trail stats (0–200+ mph, 50 mph ticks).
struct DriveSpeedGradientLegend: View {
    private static let mphTicks = [0, 50, 100, 150, 200]

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            RoundedRectangle(cornerRadius: 4, style: .continuous)
                .fill(DriveSpeedGradient.legendLinearGradient)
                .frame(height: 8)
                .overlay {
                    RoundedRectangle(cornerRadius: 4, style: .continuous)
                        .stroke(Color.white.opacity(0.12), lineWidth: 0.5)
                }

            HStack {
                ForEach(Array(Self.mphTicks.enumerated()), id: \.offset) { index, mph in
                    Text(label(for: mph))
                        .font(.system(size: 9, weight: .semibold))
                        .foregroundStyle(.white.opacity(0.55))
                        .lineLimit(1)
                        .minimumScaleFactor(0.75)
                    if index < Self.mphTicks.count - 1 {
                        Spacer(minLength: 0)
                    }
                }
            }
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel("Speed color scale from 0 to 200 plus miles per hour")
    }

    private func label(for mph: Int) -> String {
        mph >= 200 ? "200+ mph" : "\(mph) mph"
    }
}
