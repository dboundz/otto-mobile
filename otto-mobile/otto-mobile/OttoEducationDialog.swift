import SwiftUI

private enum OttoEducationDialogChrome {
    static let cardFill = Color(red: 0.086, green: 0.082, blue: 0.110).opacity(0.96)
    static let strokePurple = Color(red: 0.686, green: 0.322, blue: 0.871).opacity(0.48)
    static let scrimOpacity = 0.58
}

/// Bullet-list education / disclaimer card matching Android `OttoEducationDialog`.
struct OttoEducationDialog<Hero: View>: View {
    /// When false, the dialog cannot be dismissed except via the primary action (required before system permission prompts).
    var allowsUnconfirmedDismiss: Bool = true
    var onDismissUnconfirmed: () -> Void
    @ViewBuilder var hero: () -> Hero
    var title: String
    var bodyText: String
    var bulletSectionTitle: String?
    var bullets: [(systemImage: String, text: String)]
    var footer: String?
    var primaryTitle: String
    var onPrimary: () -> Void
    var secondaryTitle: String

    var body: some View {
        ZStack {
            Color.black.opacity(OttoEducationDialogChrome.scrimOpacity)
                .ignoresSafeArea()
                .contentShape(Rectangle())
                .onTapGesture {
                    if allowsUnconfirmedDismiss {
                        onDismissUnconfirmed()
                    }
                }

            VStack(spacing: 18) {
                if allowsUnconfirmedDismiss {
                    HStack {
                        Spacer(minLength: 0)
                        Button {
                            onDismissUnconfirmed()
                        } label: {
                            OttoGlassIconButtonLabel(
                                systemImage: "xmark",
                                size: CGSize(width: 36, height: 36),
                                cornerRadius: 18,
                                font: .system(size: 13, weight: .bold),
                                foregroundStyle: .white.opacity(0.72),
                                backgroundOpacity: 0.08,
                                strokeOpacity: 0
                            )
                        }
                        .buttonStyle(.plain)
                    }
                }

                hero()
                    .padding(.top, 4)

                VStack(spacing: 10) {
                    Text(title)
                        .font(.title2.weight(.bold))
                        .foregroundStyle(.white)
                        .multilineTextAlignment(.center)

                    Text(bodyText)
                        .font(.subheadline)
                        .multilineTextAlignment(.center)
                        .foregroundStyle(.white.opacity(0.68))
                        .lineSpacing(3)
                }

                if let section = bulletSectionTitle, !bullets.isEmpty {
                    VStack(alignment: .leading, spacing: 14) {
                        Text(section)
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(Color(red: 0.76, green: 0.36, blue: 1.0))

                        VStack(alignment: .leading, spacing: 12) {
                            ForEach(Array(bullets.enumerated()), id: \.offset) { _, item in
                                HStack(spacing: 12) {
                                    Image(systemName: item.systemImage)
                                        .font(.system(size: 14, weight: .semibold))
                                        .foregroundStyle(Color(red: 0.82, green: 0.46, blue: 1.0))
                                        .frame(width: 30, height: 30)
                                        .background(Color.purple.opacity(0.18))
                                        .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))

                                    Text(item.text)
                                        .font(.subheadline)
                                        .foregroundStyle(.white.opacity(0.76))
                                        .fixedSize(horizontal: false, vertical: true)
                                }
                            }
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }

                if let footer, !footer.isEmpty {
                    Text(footer)
                        .font(.caption)
                        .multilineTextAlignment(.center)
                        .foregroundStyle(.white.opacity(0.58))
                        .padding(.top, 2)
                }

                Group {
                    if allowsUnconfirmedDismiss {
                        HStack(spacing: 12) {
                            secondaryButton
                            primaryButton
                        }
                    } else {
                        primaryButton
                    }
                }
            }
            .padding(.horizontal, 22)
            .padding(.vertical, 24)
            .frame(maxWidth: 360)
            .background(
                RoundedRectangle(cornerRadius: 28, style: .continuous)
                    .fill(OttoEducationDialogChrome.cardFill)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 28, style: .continuous)
                    .stroke(OttoEducationDialogChrome.strokePurple, lineWidth: 1)
            )
            .shadow(color: Color.black.opacity(0.45), radius: 24, y: 12)
            .padding(.horizontal, 24)
        }
    }

    private var secondaryButton: some View {
        Button(action: onDismissUnconfirmed) {
            Text(secondaryTitle)
                .font(.subheadline.weight(.bold))
                .foregroundStyle(.white)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 15)
                .background(Color.white.opacity(0.07))
                .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                .overlay(
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .stroke(Color.white.opacity(0.13), lineWidth: 1)
                )
        }
        .buttonStyle(.plain)
    }

    private var primaryButton: some View {
        Button(action: onPrimary) {
            Text(primaryTitle)
                .font(.subheadline.weight(.bold))
                .foregroundStyle(.white)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 15)
                .background(
                    LinearGradient(
                        colors: [
                            Color(red: 0.52, green: 0.20, blue: 0.95),
                            Color(red: 0.86, green: 0.22, blue: 0.95),
                        ],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                )
                .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                .shadow(color: Color.purple.opacity(0.42), radius: 14)
        }
        .buttonStyle(.plain)
    }
}

struct OttoEducationShieldHero: View {
    var body: some View {
        Image(systemName: "checkmark.shield.fill")
            .font(.system(size: 32, weight: .semibold))
            .foregroundStyle(.white)
            .frame(width: 64, height: 64)
            .background(
                Circle()
                    .fill(
                        LinearGradient(
                            colors: [
                                Color(red: 0.52, green: 0.18, blue: 0.88),
                                Color(red: 0.72, green: 0.20, blue: 1.0),
                            ],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        )
                    )
                    .shadow(color: Color.purple.opacity(0.55), radius: 18)
            )
    }
}

struct OttoEducationLocationHero: View {
    var body: some View {
        Image(systemName: "location.circle.fill")
            .font(.system(size: 32, weight: .semibold))
            .foregroundStyle(.white)
            .frame(width: 64, height: 64)
            .background(
                Circle()
                    .fill(
                        LinearGradient(
                            colors: [
                                Color(red: 0.52, green: 0.18, blue: 0.88),
                                Color(red: 0.72, green: 0.20, blue: 1.0),
                            ],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        )
                    )
                    .shadow(color: Color.purple.opacity(0.55), radius: 18)
            )
    }
}

struct OttoEducationRouteHero: View {
    var body: some View {
        Image(systemName: SavedRouteIcon.systemImageName)
            .font(.system(size: 32, weight: .semibold))
            .foregroundStyle(.white)
            .frame(width: 64, height: 64)
            .background(
                Circle()
                    .fill(
                        LinearGradient(
                            colors: [
                                Color(red: 0.52, green: 0.18, blue: 0.88),
                                Color(red: 0.72, green: 0.20, blue: 1.0),
                            ],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        )
                    )
                    .shadow(color: Color.purple.opacity(0.55), radius: 18)
            )
    }
}

struct OttoEducationMotionHero: View {
    var body: some View {
        Image(systemName: "figure.walk.motion")
            .font(.system(size: 32, weight: .semibold))
            .foregroundStyle(.white)
            .frame(width: 64, height: 64)
            .background(
                Circle()
                    .fill(
                        LinearGradient(
                            colors: [
                                Color(red: 0.52, green: 0.18, blue: 0.88),
                                Color(red: 0.72, green: 0.20, blue: 1.0),
                            ],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        )
                    )
                    .shadow(color: Color.purple.opacity(0.55), radius: 18)
            )
    }
}
