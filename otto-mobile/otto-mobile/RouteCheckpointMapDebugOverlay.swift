import CoreLocation
import SwiftUI

/// Per-checkpoint map debug readout (Settings → Debug → Route checkpoint map debug).
struct RouteCheckpointMapDebugSnapshot: Equatable {
    let pointIndex: Int
    let distanceMeters: Double?
    let horizonScale: CGFloat
    let lodPresentation: RouteMapMarkerLODPresentation
    let currentLatitudeDelta: Double
    let markerLODLatitudeDelta: Double
    let overlapPriority: Int
    let wouldStrip: Bool
    let inSelectedRouteMapPoints: Bool
    let withinOneMile: Bool
    let usesDriveCameraPitch: Bool

    static func formatDistance(_ meters: Double?) -> String {
        guard let meters, meters.isFinite else { return "—" }
        if meters >= 1000 {
            return String(format: "%.1fkm", meters / 1000)
        }
        return String(format: "%.0fm", meters)
    }

    func labelLines(mapboxVisible: Bool?) -> [String] {
        let lod = lodPresentation.debugLabel
        let h = String(format: "%.2f", Double(horizonScale))
        let dCam = String(format: "%.4f", currentLatitudeDelta)
        let dLod = String(format: "%.4f", markerLODLatitudeDelta)
        let strip = wouldStrip ? "Y" : "N"
        let inMap = inSelectedRouteMapPoints ? "Y" : "N"
        let pitch = usesDriveCameraPitch ? "Y" : "N"
        let vis =
            mapboxVisible.map { $0 ? "Y" : "N" }
            ?? "?"
        let mi1 = withinOneMile ? "Y" : "N"
        return [
            "#\(pointIndex) \(Self.formatDistance(distanceMeters))",
            "h\(h) lod:\(lod)",
            "Δcam\(dCam) Δlod\(dLod)",
            "pri:\(overlapPriority) strip:\(strip)",
            "inMap:\(inMap) 1mi:\(mi1) pitch:\(pitch)",
            "vis:\(vis)",
        ]
    }
}

private extension RouteMapMarkerLODPresentation {
    var debugLabel: String {
        switch self {
        case .dot: "dot"
        case .pin: "pin"
        case .endpointPin: "end"
        }
    }
}

struct RouteCheckpointMapDebugLabel: View {
    let snapshot: RouteCheckpointMapDebugSnapshot
    var mapboxVisible: Bool?

    var body: some View {
        VStack(spacing: 4) {
            Circle()
                .fill(Color.green)
                .frame(width: 10, height: 10)
                .overlay(Circle().stroke(Color.white, lineWidth: 1.5))
                .shadow(color: .black.opacity(0.45), radius: 2, y: 1)

            VStack(alignment: .leading, spacing: 1) {
                ForEach(Array(snapshot.labelLines(mapboxVisible: mapboxVisible).enumerated()), id: \.offset) { _, line in
                    Text(line)
                        .font(.system(size: 9, weight: .medium, design: .monospaced))
                        .foregroundStyle(.white)
                }
            }
            .padding(.horizontal, 5)
            .padding(.vertical, 4)
            .background(Color.black.opacity(0.82), in: RoundedRectangle(cornerRadius: 5))
        }
        .fixedSize()
        .allowsHitTesting(false)
    }
}
