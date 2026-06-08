import SwiftUI

private enum OttoCenteredChoiceDialogChrome {
    static let cardFill = Color(red: 0.086, green: 0.082, blue: 0.110).opacity(0.96)
    static let strokePurple = Color(red: 0.686, green: 0.322, blue: 0.871).opacity(0.48)
    static let scrimOpacity = 0.58
}

/// Dimmed scrim + centered purple-bordered card with optional hero, title, body, gradient primary,
/// bordered secondary, and optional info footer. Used for squad share prompts and similar confirmations.
struct OttoCenteredChoiceDialog<Hero: View>: View {
    var isBusy: Bool
    /// Close button, scrim tap, and secondary action should typically route here (without committing primary).
    var onDismissUnconfirmed: () -> Void
    @ViewBuilder var hero: () -> Hero
    var title: Text
    var message: Text
    var primaryTitle: String
    var primaryBusyTitle: String?
    var primarySystemImage: String?
    var onPrimary: () -> Void
    var secondaryTitle: String
    var secondarySystemImage: String?
    var footerMessage: String? = nil

    var body: some View {
        ZStack {
            Color.black.opacity(OttoCenteredChoiceDialogChrome.scrimOpacity)
                .ignoresSafeArea()
                .contentShape(Rectangle())
                .onTapGesture {
                    if !isBusy {
                        onDismissUnconfirmed()
                    }
                }

            VStack(spacing: 0) {
                HStack {
                    Spacer(minLength: 0)
                    Button {
                        if !isBusy {
                            onDismissUnconfirmed()
                        }
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
                    .disabled(isBusy)
                }

                VStack(spacing: 10) {
                    hero()
                        .padding(.top, 4)

                    title
                        .font(.title3.weight(.semibold))
                        .foregroundStyle(.white)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 8)

                    message
                        .font(.subheadline)
                        .foregroundStyle(.white.opacity(0.62))
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 6)
                }
                .padding(.bottom, 20)

                VStack(spacing: 12) {
                    Button {
                        onPrimary()
                    } label: {
                        Group {
                            if isBusy {
                                OttoGradientButtonLabel(
                                    title: primaryBusyTitle ?? primaryTitle,
                                    systemImage: nil,
                                    height: 56,
                                    cornerRadius: 14
                                )
                                .opacity(0.85)
                            } else {
                                OttoGradientButtonLabel(
                                    title: primaryTitle,
                                    systemImage: primarySystemImage,
                                    height: 56,
                                    cornerRadius: 14
                                )
                            }
                        }
                    }
                    .buttonStyle(.plain)
                    .disabled(isBusy)

                    Button {
                        if !isBusy {
                            onDismissUnconfirmed()
                        }
                    } label: {
                        HStack(spacing: 10) {
                            if let secondarySystemImage {
                                Image(systemName: secondarySystemImage)
                                    .font(.body.weight(.semibold))
                            }
                            Text(secondaryTitle)
                                .font(.subheadline.weight(.bold))
                        }
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
                    .disabled(isBusy)
                }

                if let footerMessage, !footerMessage.isEmpty {
                    HStack(alignment: .top, spacing: 12) {
                        Image(systemName: "info.circle.fill")
                            .font(.title3)
                            .foregroundStyle(Color.purple.opacity(0.92))
                        Text(footerMessage)
                            .font(.caption)
                            .foregroundStyle(.white.opacity(0.62))
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    .padding(14)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color.white.opacity(0.055))
                    .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                    .padding(.top, 18)
                }
            }
            .padding(.horizontal, 22)
            .padding(.vertical, 24)
            .frame(maxWidth: 360)
            .background(
                RoundedRectangle(cornerRadius: 28, style: .continuous)
                    .fill(OttoCenteredChoiceDialogChrome.cardFill)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 28, style: .continuous)
                    .stroke(OttoCenteredChoiceDialogChrome.strokePurple, lineWidth: 1)
            )
            .shadow(color: Color.black.opacity(0.45), radius: 24, y: 12)
            .padding(.horizontal, 24)
        }
    }
}

/// Hero graphic for “share to squad chat” flows (squad + chat badge + sparkles).
struct OttoSquadChatShareHeroGraphic: View {
    var body: some View {
        ZStack {
            Image(systemName: "sparkle")
                .font(.system(size: 11, weight: .bold))
                .foregroundStyle(Color.purple.opacity(0.9))
                .offset(x: -34, y: -26)
            Image(systemName: "sparkle")
                .font(.system(size: 9, weight: .bold))
                .foregroundStyle(Color.purple.opacity(0.65))
                .offset(x: 38, y: -22)

            ZStack {
                Circle()
                    .fill(Color.purple.opacity(0.14))
                Circle()
                    .stroke(Color.purple.opacity(0.55), lineWidth: 2)
                    .shadow(color: Color.purple.opacity(0.35), radius: 12)

                Image(systemName: "person.3.fill")
                    .font(.system(size: 28, weight: .semibold))
                    .foregroundStyle(
                        LinearGradient(
                            colors: [Color.purple.opacity(0.95), Color(red: 0.72, green: 0.35, blue: 0.98)],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        )
                    )

                Image(systemName: "ellipsis.bubble.fill")
                    .font(.system(size: 13, weight: .bold))
                    .foregroundStyle(.white)
                    .padding(7)
                    .background(
                        Circle()
                            .fill(Color.purple.opacity(0.92))
                    )
                    .offset(x: 26, y: 24)
            }
            .frame(width: 76, height: 76)
        }
        .padding(.vertical, 10)
    }
}
