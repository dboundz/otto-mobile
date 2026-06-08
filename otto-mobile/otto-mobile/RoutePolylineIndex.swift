import CoreLocation
import Foundation
import MapKit

struct RoutePolylineIndex {
    let lineCoordinates: [CLLocationCoordinate2D]
    private let cumulativeDistances: [Double]

    init(lineCoordinates: [CLLocationCoordinate2D]) {
        self.lineCoordinates = lineCoordinates
        if lineCoordinates.count >= 2 {
            var cumulative: [Double] = [0]
            cumulative.reserveCapacity(lineCoordinates.count)
            for index in 0..<(lineCoordinates.count - 1) {
                let start = MKMapPoint(lineCoordinates[index])
                let end = MKMapPoint(lineCoordinates[index + 1])
                cumulative.append(cumulative[index] + start.distance(to: end))
            }
            cumulativeDistances = cumulative
        } else {
            cumulativeDistances = []
        }
    }

    func projectOntoPolyline(_ coordinate: CLLocationCoordinate2D) -> RoutePolylineProjection? {
        guard lineCoordinates.count >= 2 else { return nil }

        if lineCoordinates.count <= 256 {
            return RouteMapGeometry.allProjectionsOntoPolyline(coordinate, lineCoordinates: lineCoordinates)
                .min(by: { $0.distanceMeters < $1.distanceMeters })
        }

        let target = MKMapPoint(coordinate)
        var best: RoutePolylineProjection?
        let segmentCount = lineCoordinates.count - 1
        let coarseStride = max(1, segmentCount / 200)

        var index = 0
        while index < segmentCount {
            if let projection = projection(forSegmentStartingAt: index, target: target),
               best == nil || projection.distanceMeters < best!.distanceMeters {
                best = projection
            }
            index += coarseStride
        }

        guard let coarseBest = best else {
            return RouteMapGeometry.allProjectionsOntoPolyline(coordinate, lineCoordinates: lineCoordinates)
                .min(by: { $0.distanceMeters < $1.distanceMeters })
        }

        let refineStart = max(0, coarseBest.segmentIndex - coarseStride)
        let refineEnd = min(segmentCount - 1, coarseBest.segmentIndex + coarseStride)
        for segmentIndex in refineStart...refineEnd {
            if let projection = projection(forSegmentStartingAt: segmentIndex, target: target),
               projection.distanceMeters < best!.distanceMeters {
                best = projection
            }
        }

        return best
    }

    func projectOntoPolyline(
        _ coordinate: CLLocationCoordinate2D,
        preferredArcLength: Double?,
        searchWindowMeters: CLLocationDistance = 350
    ) -> RoutePolylineProjection? {
        guard lineCoordinates.count >= 2 else { return nil }
        guard let preferredArcLength, !cumulativeDistances.isEmpty else {
            return projectOntoPolyline(coordinate)
        }

        let segmentIndex = segmentIndex(forArcLength: preferredArcLength)
        let searchRadius = max(8, Int((searchWindowMeters / 50).rounded()))
        let start = max(0, segmentIndex - searchRadius)
        let end = min(lineCoordinates.count - 2, segmentIndex + searchRadius)

        let target = MKMapPoint(coordinate)
        var best: RoutePolylineProjection?
        for index in start...end {
            if let projection = projection(forSegmentStartingAt: index, target: target) {
                if projection.arcLengthMeters < preferredArcLength - 50
                    || projection.arcLengthMeters > preferredArcLength + searchWindowMeters {
                    continue
                }
                if best == nil
                    || abs(projection.arcLengthMeters - preferredArcLength)
                        < abs(best!.arcLengthMeters - preferredArcLength) {
                    best = projection
                }
            }
        }

        return best ?? projectOntoPolyline(coordinate)
    }

    private func segmentIndex(forArcLength arcLength: Double) -> Int {
        guard cumulativeDistances.count >= 2 else { return 0 }
        var low = 0
        var high = cumulativeDistances.count - 1
        while low < high {
            let mid = (low + high) / 2
            if cumulativeDistances[mid] < arcLength {
                low = mid + 1
            } else {
                high = mid
            }
        }
        return max(0, min(lineCoordinates.count - 2, low - 1))
    }

    private func projection(
        forSegmentStartingAt index: Int,
        target: MKMapPoint
    ) -> RoutePolylineProjection? {
        guard lineCoordinates.indices.contains(index),
              lineCoordinates.indices.contains(index + 1) else {
            return nil
        }

        let startCoordinate = lineCoordinates[index]
        let endCoordinate = lineCoordinates[index + 1]
        let start = MKMapPoint(startCoordinate)
        let end = MKMapPoint(endCoordinate)
        let segmentDX = end.x - start.x
        let segmentDY = end.y - start.y
        let segmentLengthSquared = segmentDX * segmentDX + segmentDY * segmentDY
        let rawT: Double
        if segmentLengthSquared <= 0 {
            rawT = 0
        } else {
            rawT = ((target.x - start.x) * segmentDX + (target.y - start.y) * segmentDY) / segmentLengthSquared
        }
        let t = min(1, max(0, rawT))
        let projectedPoint = MKMapPoint(
            x: start.x + segmentDX * t,
            y: start.y + segmentDY * t
        )
        let segmentDistance = start.distance(to: end)
        let arcLength = cumulativeDistances[index] + (segmentDistance * t)
        return RoutePolylineProjection(
            coordinate: projectedPoint.coordinate,
            distanceMeters: target.distance(to: projectedPoint),
            arcLengthMeters: arcLength,
            segmentIndex: index,
            segmentBearingDegrees: RouteMapGeometry.bearingBetween(from: startCoordinate, to: endCoordinate)
        )
    }
}
