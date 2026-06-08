import SwiftUI

/// Map pin for upcoming events — pink calendar asset with optional cluster badge.
struct OttoMapEventMarker: View {
    var isSelected: Bool = false
    /// Total events represented when clustered (`> 1` shows badge).
    var clusterCount: Int? = nil
    var isUserGoing: Bool = false
    var pinScale: CGFloat = 1

    private var pinWidth: CGFloat { 56 * pinScale }
    private var pinHeight: CGFloat { 84 * pinScale }
    private var pinFrameWidth: CGFloat { max(pinWidth, 72 * pinScale) }
    private var pinFrameHeight: CGFloat { pinHeight * 2 }

    var body: some View {
        VStack(spacing: 0) {
            ZStack(alignment: .topTrailing) {
                Image("map-point-event")
                    .resizable()
                    .scaledToFit()
                    .frame(width: pinWidth, height: pinHeight)
                    .shadow(color: .black.opacity(0.4), radius: 4 * pinScale, y: 2 * pinScale)

                if let clusterCount, clusterCount > 1 {
                    Text("\(clusterCount)")
                        .font(.system(size: 11 * pinScale, weight: .heavy, design: .rounded))
                        .foregroundStyle(.black)
                        .frame(minWidth: 20 * pinScale, minHeight: 20 * pinScale)
                        .padding(.horizontal, 4 * pinScale)
                        .background(
                            Circle()
                                .fill(Color.white)
                                .shadow(color: .black.opacity(0.25), radius: 3 * pinScale, y: 1 * pinScale)
                        )
                        .overlay(Circle().stroke(Color.black.opacity(0.35), lineWidth: 1))
                        .offset(x: 6 * pinScale, y: -4 * pinScale)
                        .allowsHitTesting(false)
                }
            }
            Spacer(minLength: 0)
        }
        .frame(width: pinFrameWidth, height: pinFrameHeight)
        .scaleEffect(isSelected ? 1.06 : 1)
        .animation(.spring(response: 0.28, dampingFraction: 0.82), value: isSelected)
        .accessibilityLabel("Event")
    }
}

#if DEBUG
#Preview {
    ZStack {
        Color.black.ignoresSafeArea()
        VStack(spacing: 40) {
            OttoMapEventMarker(isSelected: false, clusterCount: nil)
            OttoMapEventMarker(isSelected: true, clusterCount: 4)
            OttoMapEventMarker(isSelected: false, clusterCount: nil, pinScale: 0.55)
        }
    }
}
#endif
