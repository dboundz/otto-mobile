import SwiftUI

struct OttoMapSavedPlaceMarker: View {
    var isSelected: Bool = false
    var pinScale: CGFloat = 1

    private var pinWidth: CGFloat { 56 * pinScale }
    private var pinHeight: CGFloat { 84 * pinScale }
    private var pinFrameWidth: CGFloat { max(pinWidth, 72 * pinScale) }
    private var pinFrameHeight: CGFloat { pinHeight * 2 }

    var body: some View {
        VStack(spacing: 0) {
            Image("map-point-saved")
                .resizable()
                .scaledToFit()
                .frame(width: pinWidth, height: pinHeight)
                .shadow(color: .black.opacity(0.4), radius: 4 * pinScale, y: 2 * pinScale)
            Spacer(minLength: 0)
        }
        .frame(width: pinFrameWidth, height: pinFrameHeight)
        .scaleEffect(isSelected ? 1.06 : 1)
        .animation(.spring(response: 0.28, dampingFraction: 0.82), value: isSelected)
        .accessibilityLabel("Saved place")
    }
}

#if DEBUG
#Preview {
    ZStack {
        Color.black.ignoresSafeArea()
        VStack(spacing: 40) {
            OttoMapSavedPlaceMarker(isSelected: false)
            OttoMapSavedPlaceMarker(isSelected: true, pinScale: 0.55)
        }
    }
}
#endif
