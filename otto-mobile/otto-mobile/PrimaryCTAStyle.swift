import SwiftUI

private enum PrimaryCTATheme {
    static let cornerRadius: CGFloat = 12
    static let gradient = LinearGradient(
        colors: [
            .purple,
            .blue.opacity(0.8),
        ],
        startPoint: .leading,
        endPoint: .trailing
    )
    static let shadowColor = Color.purple.opacity(0.38)
}

struct OttoGradientButtonLabel: View {
    let title: String
    var systemImage: String?
    var height: CGFloat = 56
    var cornerRadius: CGFloat = PrimaryCTATheme.cornerRadius

    var body: some View {
        Group {
            if let systemImage {
                Label(title, systemImage: systemImage)
            } else {
                Text(title)
            }
        }
        .font(.headline)
        .foregroundStyle(.white)
        .frame(maxWidth: .infinity)
        .frame(height: height)
        .background(PrimaryCTATheme.gradient)
        .clipShape(RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
    }
}

struct OttoGlassIconButtonLabel: View {
    let systemImage: String
    var size = CGSize(width: 58, height: 56)
    var cornerRadius: CGFloat = 12
    var font: Font = .title3.weight(.semibold)
    var foregroundStyle = Color.white
    var backgroundOpacity: Double = 0.055
    var strokeOpacity: Double = 0.12

    var body: some View {
        Image(systemName: systemImage)
            .font(font)
            .foregroundStyle(foregroundStyle)
            .frame(width: size.width, height: size.height)
            .background(Color.white.opacity(backgroundOpacity))
            .clipShape(RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                    .stroke(Color.white.opacity(strokeOpacity), lineWidth: 1)
            }
    }
}

struct OttoHeaderIconButtonLabel: View {
    let systemImage: String
    var size: CGFloat = 42
    var cornerRadius: CGFloat = 14
    var font: Font = .headline.weight(.bold)
    var foregroundStyle = Color.white
    var backgroundColor = Color.black.opacity(0.78)
    var strokeOpacity: Double = 0.10

    var body: some View {
        Image(systemName: systemImage)
            .font(font)
            .foregroundStyle(foregroundStyle)
            .frame(width: size, height: size)
            .background(backgroundColor)
            .clipShape(RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                    .stroke(Color.white.opacity(strokeOpacity), lineWidth: 1)
            }
    }
}

struct OttoHeaderTextButtonLabel: View {
    let title: String
    var isEnabled = true
    var height: CGFloat = 42
    var horizontalPadding: CGFloat = 16
    var cornerRadius: CGFloat = 14

    var body: some View {
        Text(title)
            .font(.subheadline.weight(.bold))
            .foregroundStyle(.white)
            .padding(.horizontal, horizontalPadding)
            .frame(height: height)
            .background(isEnabled ? Color.purple : Color.gray.opacity(0.6))
            .clipShape(RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
    }
}

struct PrimaryCTAButtonStyle: ViewModifier {
    var horizontalPadding: CGFloat = 16
    var verticalPadding: CGFloat = 10

    func body(content: Content) -> some View {
        content
            .font(.subheadline.weight(.semibold))
            .foregroundStyle(.white)
            .padding(.horizontal, horizontalPadding)
            .padding(.vertical, verticalPadding)
            .background(PrimaryCTATheme.gradient)
            .clipShape(RoundedRectangle(cornerRadius: PrimaryCTATheme.cornerRadius, style: .continuous))
            .shadow(color: PrimaryCTATheme.shadowColor, radius: 14, y: 4)
    }
}

extension View {
    func primaryCTAButtonStyle(horizontalPadding: CGFloat = 16, verticalPadding: CGFloat = 10) -> some View {
        modifier(
            PrimaryCTAButtonStyle(
                horizontalPadding: horizontalPadding,
                verticalPadding: verticalPadding
            )
        )
    }
}
