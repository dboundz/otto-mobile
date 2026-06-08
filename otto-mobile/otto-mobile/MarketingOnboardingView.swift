import SwiftUI

/// Full-screen, swipeable marketing onboarding. Dark, image-first, minimal chrome.
struct MarketingOnboardingView: View {
    @EnvironmentObject private var appState: AppState
    let onFinished: (_ wasReplay: Bool) -> Void

    private let slides = MarketingOnboardingCatalog.slides

    @State private var page: Int = 0
    @State private var backgroundNudge: CGFloat = 0

    private var wasReplay: Bool {
        appState.marketingOnboardingReplayRequested
    }

    private var ottoGradient: LinearGradient {
        LinearGradient(
            colors: [
                Color(red: 0.58, green: 0.22, blue: 0.98),
                Color(red: 0.98, green: 0.38, blue: 0.62),
            ],
            startPoint: .leading,
            endPoint: .trailing
        )
    }

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            TabView(selection: $page) {
                ForEach(Array(slides.enumerated()), id: \.element.id) { index, slide in
                    slidePage(slide)
                        .tag(index)
                }
            }
            .tabViewStyle(.page(indexDisplayMode: .never))
            .onChange(of: page) { _, newValue in
                bumpParallax(for: newValue)
            }
            .onAppear {
                bumpParallax(for: page)
            }

            VStack {
                Spacer(minLength: 0)
                bottomChrome
            }
            .padding(.horizontal, 22)
            .padding(.bottom, 6)
        }
        .preferredColorScheme(.dark)
    }

    @ViewBuilder
    private func slidePage(_ slide: MarketingOnboardingCatalog.Slide) -> some View {
        GeometryReader { geo in
            let h = geo.size.height
            let w = geo.size.width
            ZStack(alignment: .bottom) {
                Image(slide.backgroundAssetName)
                    .resizable()
                    .scaledToFill()
                    .frame(width: w, height: h)
                    .clipped()
                    .offset(y: backgroundNudge)
                    .scaleEffect(1.02)
                    .animation(.spring(response: 0.55, dampingFraction: 0.88), value: page)
                    .accessibilityHidden(true)

                LinearGradient(
                    colors: [
                        Color.black.opacity(0.12),
                        Color.black.opacity(0.5),
                        Color.black.opacity(0.92),
                    ],
                    startPoint: .top,
                    endPoint: .bottom
                )
                .allowsHitTesting(false)
                .opacity(slide.kind == .welcome ? 0 : 1)

                slideForeground(slide)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: slide.kind == .welcome ? .center : .bottom)
                    .padding(.horizontal, slide.kind == .welcome ? 0 : 24)
                    .padding(.bottom, slide.kind == .welcome ? 0 : 138)
            }
            .frame(width: w, height: h)
            .clipped()
        }
        .ignoresSafeArea()
    }

    @ViewBuilder
    private func slideForeground(_ slide: MarketingOnboardingCatalog.Slide) -> some View {
        switch slide.kind {
        case .welcome:
            Color.clear
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        case .feature:
            featureStack(slide)
        }
    }

    private func featureStack(_ slide: MarketingOnboardingCatalog.Slide) -> some View {
        VStack(alignment: .leading, spacing: 16) {
            headline(slide.headlineParts)
                .multilineTextAlignment(.leading)
                .frame(maxWidth: .infinity, alignment: .leading)

            Text(slide.body)
                .font(.subheadline.weight(.medium))
                .foregroundStyle(.white.opacity(0.76))
                .lineSpacing(3)
                .fixedSize(horizontal: false, vertical: true)

            VStack(alignment: .leading, spacing: 12) {
                ForEach(slide.bullets, id: \.title) { bullet in
                    HStack(alignment: .top, spacing: 12) {
                        Image(systemName: bullet.systemImage)
                            .font(.system(size: 15, weight: .semibold))
                            .foregroundStyle(ottoGradient)
                            .frame(width: 26, height: 26)
                            .background(
                                RoundedRectangle(cornerRadius: 8, style: .continuous)
                                    .fill(Color.white.opacity(0.08))
                            )
                        VStack(alignment: .leading, spacing: 2) {
                            Text(bullet.title)
                                .font(.subheadline.weight(.semibold))
                                .foregroundStyle(.white)
                            Text(bullet.subtitle)
                                .font(.caption)
                                .foregroundStyle(.white.opacity(0.62))
                                .fixedSize(horizontal: false, vertical: true)
                        }
                    }
                }
            }
            .padding(.top, 4)
        }
    }

    private func headline(_ parts: [MarketingOnboardingCatalog.Slide.HeadlinePart]) -> some View {
        Text(headlineAttributed(parts))
            .font(.system(size: 34, weight: .bold, design: .rounded))
            .lineLimit(4)
            .minimumScaleFactor(0.72)
    }

    /// Avoids deprecated `Text` + `Text` composition (iOS 26); gradient emphasis uses a single highlight color aligned to `ottoGradient`.
    private func headlineAttributed(_ parts: [MarketingOnboardingCatalog.Slide.HeadlinePart]) -> AttributedString {
        var result = AttributedString()
        let highlight = Color(red: 0.78, green: 0.30, blue: 0.80)
        for part in parts {
            var chunk = AttributedString(part.text)
            chunk.foregroundColor = part.useGradient ? highlight : .white
            result.append(chunk)
        }
        return result
    }

    private var bottomChrome: some View {
        VStack(spacing: 14) {
            if page == 0 {
                Button(action: advance) {
                    HStack(spacing: 8) {
                        Text("Continue")
                            .font(.headline.weight(.semibold))
                        Image(systemName: "chevron.right")
                            .font(.subheadline.weight(.bold))
                    }
                    .foregroundStyle(.white)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 16)
                    .background(ottoGradient, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier("onboarding_continue")
            } else {
                HStack(alignment: .center) {
                    Button("Skip") {
                        finish()
                    }
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.white.opacity(0.62))

                    Spacer()

                    Button(action: advanceOrFinish) {
                        HStack(spacing: 4) {
                            Text(page >= slides.count - 1 ? "Get Started" : "Next")
                                .font(.subheadline.weight(.bold))
                            if page < slides.count - 1 {
                                Image(systemName: "chevron.right")
                                    .font(.caption.weight(.bold))
                            }
                        }
                        .foregroundStyle(
                            LinearGradient(
                                colors: [
                                    Color(red: 0.72, green: 0.48, blue: 1.0),
                                    Color(red: 1.0, green: 0.52, blue: 0.72),
                                ],
                                startPoint: .leading,
                                endPoint: .trailing
                            )
                        )
                    }
                    .buttonStyle(.plain)
                }
            }

            HStack(spacing: 7) {
                ForEach(0..<slides.count, id: \.self) { i in
                    Circle()
                        .fill(
                            i == page
                                ? AnyShapeStyle(ottoGradient)
                                : AnyShapeStyle(Color.white.opacity(0.22))
                        )
                        .frame(width: i == page ? 8 : 6, height: i == page ? 8 : 6)
                        .animation(.spring(response: 0.4, dampingFraction: 0.78), value: page)
                }
            }
            .frame(maxWidth: .infinity)
            .padding(.bottom, 4)
        }
    }

    private func bumpParallax(for index: Int) {
        let delta = CGFloat(index) * 3 - 4
        withAnimation(.spring(response: 0.65, dampingFraction: 0.86)) {
            backgroundNudge = delta
        }
    }

    private func advance() {
        guard page < slides.count - 1 else { return }
        withAnimation(.spring(response: 0.42, dampingFraction: 0.86)) {
            page += 1
        }
    }

    private func advanceOrFinish() {
        if page >= slides.count - 1 {
            finish()
        } else {
            advance()
        }
    }

    private func finish() {
        onFinished(wasReplay)
    }
}

