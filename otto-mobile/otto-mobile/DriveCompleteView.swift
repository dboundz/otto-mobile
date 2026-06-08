import CoreLocation
import SwiftUI

struct DriveCompleteSummary {
    let driveId: String?
    let routeName: String
    let routeCoordinates: [CLLocationCoordinate2D]
    let checkpointCoordinates: [CLLocationCoordinate2D]
    let distanceMeters: Double
    let driveTimeSeconds: TimeInterval
    let averageSpeedMph: Double
    let maxSpeedMph: Double
    let completedCheckpoints: Int
    let totalCheckpoints: Int
    let completionReason: String
}

struct DriveCompleteView: View {
    let summary: DriveCompleteSummary
    let onViewSummary: () -> Void
    let onDone: () -> Void

    @State private var hasAppeared = false
    @GestureState private var dragOffset: CGFloat = 0

    var body: some View {
        ZStack {
            Color.black.opacity(0.58)
                .ignoresSafeArea()

            ScrollView(showsIndicators: false) {
                completionCard
                    .padding(.horizontal, 6)
                    .padding(.top, 10)
                    .padding(.bottom, 10)
                    .scaleEffect(hasAppeared ? 1 : 0.96)
                    .opacity(hasAppeared ? 1 : 0)
                    .offset(y: hasAppeared ? 0 : 18)
                    .offset(y: max(0, dragOffset))
            }
            .safeAreaInset(edge: .bottom, spacing: 0) {
                Color.clear.frame(height: 8)
            }
        }
        .gesture(
            DragGesture(minimumDistance: 16)
                .updating($dragOffset) { value, state, _ in
                    state = max(0, value.translation.height)
                }
                .onEnded { value in
                    if value.translation.height > 90 {
                        onDone()
                    }
                }
        )
        .onAppear {
            withAnimation(.spring(response: 0.52, dampingFraction: 0.86)) {
                hasAppeared = true
            }
        }
    }

    private var completionCard: some View {
        VStack(spacing: 18) {
            HStack {
                Spacer()
                Button(action: onDone) {
                    Image(systemName: "xmark")
                        .font(.headline.weight(.bold))
                        .foregroundStyle(.white.opacity(0.9))
                        .frame(width: 42, height: 42)
                        .background(Circle().fill(Color.white.opacity(0.08)))
                        .overlay(Circle().stroke(Color.white.opacity(0.12), lineWidth: 1))
                }
                .buttonStyle(.plain)
            }

            header
            routeHero
            statsRow
            actionButtons
        }
        .padding(20)
        .frame(maxWidth: 460)
        .background(cardBackground)
        .overlay {
            RoundedRectangle(cornerRadius: 30, style: .continuous)
                .stroke(Color.white.opacity(0.12), lineWidth: 1)
        }
        .shadow(color: Color.purple.opacity(0.25), radius: 34, y: 16)
        .padding(.vertical, 18)
    }

    private var cardBackground: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 30, style: .continuous)
                .fill(Color.black.opacity(0.78))
            RoundedRectangle(cornerRadius: 30, style: .continuous)
                .fill(
                    LinearGradient(
                        colors: [Color.purple.opacity(0.23), Color.clear, Color.blue.opacity(0.08)],
                        startPoint: .top,
                        endPoint: .bottom
                    )
                )
            particleField
        }
    }

    private var header: some View {
        VStack(spacing: 10) {
            Image(systemName: "checkmark.circle.fill")
                .font(.system(size: 44, weight: .bold))
                .foregroundStyle(.white)
                .frame(width: 88, height: 88)
                .background(
                    Circle()
                        .fill(Color.purple.opacity(0.22))
                        .shadow(color: .purple.opacity(0.9), radius: 18)
                )
                .overlay(Circle().stroke(Color.purple.opacity(0.8), lineWidth: 1))

            Text("Drive Complete!")
                .font(.largeTitle.weight(.bold))
                .foregroundStyle(.white)

            Text("Great run. See you on the next one.")
                .font(.subheadline)
                .foregroundStyle(.white.opacity(0.68))
        }
    }

    private var routeHero: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 22, style: .continuous)
                .fill(Color.white.opacity(0.035))

            RoutePreviewShape(coordinates: summary.routeCoordinates)
                .stroke(Color.purple.opacity(0.34), style: StrokeStyle(lineWidth: 13, lineCap: .round, lineJoin: .round))
                .blur(radius: 8)
                .padding(.horizontal, 24)
                .padding(.vertical, 20)

            RoutePreviewShape(coordinates: summary.routeCoordinates)
                .trim(from: 0, to: hasAppeared ? 1 : 0)
                .stroke(Color.purple, style: StrokeStyle(lineWidth: 5, lineCap: .round, lineJoin: .round))
                .padding(.horizontal, 24)
                .padding(.vertical, 20)
                .animation(.easeOut(duration: 0.9).delay(0.18), value: hasAppeared)

            checkpointMarkers
                .padding(.horizontal, 24)
                .padding(.vertical, 20)
        }
        .frame(height: 124)
    }

    private var checkpointMarkers: some View {
        GeometryReader { proxy in
            let points = RoutePreviewShape.normalizedPoints(coordinates: summary.checkpointCoordinates, in: proxy.size)
            ForEach(Array(points.enumerated()), id: \.offset) { index, point in
                checkpointMarker(index: index, count: points.count)
                    .position(point)
                    .scaleEffect(hasAppeared ? 1 : 0.4)
                    .opacity(hasAppeared ? 1 : 0)
                    .animation(.spring(response: 0.34, dampingFraction: 0.72).delay(0.34 + Double(index) * 0.06), value: hasAppeared)
            }
        }
    }

    private func checkpointMarker(index: Int, count: Int) -> some View {
        Group {
            if index == count - 1 {
                Image(systemName: "flag.checkered")
                    .font(.caption2.weight(.bold))
                    .foregroundStyle(.white)
            } else {
                Text("\(index + 1)")
                    .font(.caption2.weight(.bold))
                    .foregroundStyle(.white)
            }
        }
        .frame(width: index == count - 1 ? 27 : 24, height: index == count - 1 ? 27 : 24)
        .background(Circle().fill(Color.purple))
        .overlay(Circle().stroke(Color.white.opacity(0.7), lineWidth: 1))
        .shadow(color: .purple.opacity(0.75), radius: 9)
    }

    private var statsRow: some View {
        HStack(spacing: 0) {
            statCell(icon: "point.3.connected.trianglepath.dotted", title: "Distance", value: distanceValue, unit: distanceUnit)
            Divider().background(Color.white.opacity(0.08))
            statCell(icon: "clock", title: "Drive Time", value: driveTimeText, unit: "")
            Divider().background(Color.white.opacity(0.08))
            statCell(
                icon: "flag.checkered",
                title: "Checkpoints",
                value: "\(summary.completedCheckpoints)/\(max(summary.totalCheckpoints, 1))",
                unit: summary.completedCheckpoints >= summary.totalCheckpoints && summary.totalCheckpoints > 0
                    ? "All Passed!"
                    : ""
            )
        }
        .frame(height: 146)
        .background(Color.white.opacity(0.055))
        .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 20, style: .continuous).stroke(Color.white.opacity(0.09)))
        .offset(y: hasAppeared ? 0 : 14)
        .opacity(hasAppeared ? 1 : 0)
        .animation(.easeOut(duration: 0.42).delay(0.42), value: hasAppeared)
    }

    private func statCell(icon: String, title: String, value: String, unit: String) -> some View {
        VStack(spacing: 7) {
            Image(systemName: icon)
                .font(.title3.weight(.semibold))
                .foregroundStyle(.purple)
                .frame(width: 42, height: 42)
                .background(Circle().fill(Color.purple.opacity(0.16)))
                .overlay(Circle().stroke(Color.purple.opacity(0.55), lineWidth: 1))
            Text(title)
                .font(.caption)
                .foregroundStyle(.white.opacity(0.58))
            Text(value)
                .font(.system(size: 24, weight: .bold, design: .rounded))
                .foregroundStyle(.white)
                .minimumScaleFactor(0.72)
                .lineLimit(1)
            if !unit.isEmpty {
                Text(unit)
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(unit == "All Passed!" ? .purple : .white.opacity(0.72))
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private var actionButtons: some View {
        VStack(spacing: 10) {
            Button(action: onViewSummary) {
                HStack(spacing: 8) {
                    Text("View Summary")
                        .font(.headline.weight(.heavy))
                    Image(systemName: "chevron.right")
                        .font(.subheadline.weight(.bold))
                }
                .foregroundStyle(.white)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 17)
                .background(
                    LinearGradient(
                        colors: [
                            Color(red: 0.20, green: 0.15, blue: 1.0),
                            Color(red: 0.72, green: 0.20, blue: 0.88)
                        ],
                        startPoint: .leading,
                        endPoint: .trailing
                    )
                )
                .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                .shadow(color: Color.purple.opacity(0.45), radius: 16)
            }
            .buttonStyle(.plain)

            Button(action: onDone) {
                Text("Done")
                    .font(.headline.weight(.bold))
                    .foregroundStyle(.white.opacity(0.92))
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 17)
                    .background(Color.white.opacity(0.06))
                    .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                    .overlay(
                        RoundedRectangle(cornerRadius: 14, style: .continuous)
                            .stroke(Color.white.opacity(0.16), lineWidth: 1)
                    )
            }
            .buttonStyle(.plain)
        }
    }

    private var particleField: some View {
        GeometryReader { proxy in
            ForEach(0..<22, id: \.self) { index in
                RoundedRectangle(cornerRadius: 2)
                    .fill(Color.purple.opacity(index.isMultiple(of: 3) ? 0.42 : 0.24))
                    .frame(width: CGFloat(4 + (index % 3) * 2), height: CGFloat(4 + (index % 2) * 3))
                    .rotationEffect(.degrees(Double(index * 23)))
                    .position(
                        x: proxy.size.width * CGFloat((index * 37 % 100)) / 100,
                        y: proxy.size.height * CGFloat((index * 53 % 44)) / 100
                    )
                    .opacity(hasAppeared ? 1 : 0)
                    .animation(.easeOut(duration: 0.7).delay(Double(index) * 0.015), value: hasAppeared)
            }
        }
        .clipShape(RoundedRectangle(cornerRadius: 30, style: .continuous))
    }
}

private extension DriveCompleteView {
    var distanceValue: String {
        let miles = summary.distanceMeters / 1609.344
        return String(format: "%.1f", miles)
    }

    var distanceUnit: String { "mi" }

    var driveTimeText: String {
        let totalMinutes = max(0, Int((summary.driveTimeSeconds / 60).rounded()))
        let hours = totalMinutes / 60
        let minutes = totalMinutes % 60
        if hours > 0 {
            return "\(hours)h \(minutes)m"
        }
        return "\(max(1, minutes))m"
    }
}
