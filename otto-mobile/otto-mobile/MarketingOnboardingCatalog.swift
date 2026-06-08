import Foundation

/// Central copy + layout metadata for the premium marketing onboarding carousel.
/// Update slides here only; visuals live in `Assets.xcassets` (`OnboardingSlide0`…`5`).
enum MarketingOnboardingCatalog {
    struct Slide: Identifiable, Equatable {
        struct HeadlinePart: Equatable {
            let text: String
            /// When true, render with the Driftd gradient; otherwise solid white.
            let useGradient: Bool
        }

        struct Bullet: Equatable {
            let title: String
            let subtitle: String
            let systemImage: String
        }

        enum Kind: Equatable {
            case welcome
            case feature
        }

        let id: String
        let kind: Kind
        /// `OnboardingSlide0` … `OnboardingSlide5`
        let backgroundAssetName: String
        let headlineParts: [HeadlinePart]
        let body: String
        let bullets: [Bullet]
    }

    typealias HeadlinePart = Slide.HeadlinePart
    typealias Bullet = Slide.Bullet

    static let slides: [Slide] = [
        Slide(
            id: "welcome",
            kind: .welcome,
            backgroundAssetName: "OnboardingSlide0",
            headlineParts: [
                HeadlinePart(text: "Drive ", useGradient: false),
                HeadlinePart(text: "together.", useGradient: true),
            ],
            body: "Private squads, live maps, events, and real-world driving culture.",
            bullets: []
        ),
        Slide(
            id: "squads",
            kind: .feature,
            backgroundAssetName: "OnboardingSlide1",
            headlineParts: [
                HeadlinePart(text: "Your real-life ", useGradient: false),
                HeadlinePart(text: "crew.", useGradient: true),
            ],
            body:
                "Small private groups for the people you actually drive with. Coordinate quickly, stay connected, and know where everyone is without broadcasting to the world.",
            bullets: [
                Bullet(title: "Squad chat", subtitle: "Talk, plan, and coordinate with the people you roll with.", systemImage: "bubble.left.and.bubble.right.fill"),
                Bullet(title: "Private groups", subtitle: "Squads stay between you and your friends — not the whole internet.", systemImage: "lock.fill"),
                Bullet(title: "Built for meets & convoys", subtitle: "Keep the crew in sync before, during, and after the drive.", systemImage: "car.2.fill"),
            ]
        ),
        Slide(
            id: "map",
            kind: .feature,
            backgroundAssetName: "OnboardingSlide2",
            headlineParts: [
                HeadlinePart(text: "See the squad ", useGradient: false),
                HeadlinePart(text: "moving.", useGradient: true),
            ],
            body: "The live map helps your crew coordinate in real time — rallies, convoys, meetups, and late-night drives.",
            bullets: [
                Bullet(title: "Live squad locations", subtitle: "Friends who choose to share appear on the map when it matters.", systemImage: "location.fill.viewfinder"),
                Bullet(title: "Saved spots", subtitle: "Pin the places you care about so everyone can rally to the same corner.", systemImage: "bookmark.fill"),
                Bullet(title: "Real-time updates", subtitle: "Start, pause, or stop sharing — the map reflects the moment.", systemImage: "arrow.triangle.2.circlepath"),
            ]
        ),
        Slide(
            id: "garage",
            kind: .feature,
            backgroundAssetName: "OnboardingSlide4",
            headlineParts: [
                HeadlinePart(text: "Your cars, ", useGradient: false),
                HeadlinePart(text: "your identity.", useGradient: true),
            ],
            body:
                "Build your garage with the cars you actually drive — and see what your friends are bringing too.",
            bullets: [
                Bullet(title: "Add multiple cars", subtitle: "Photos, nicknames, colors, and details that feel like your build.", systemImage: "plus.rectangle.on.rectangle.fill"),
                Bullet(title: "Set a featured car", subtitle: "The ride that represents you across your profile and the map.", systemImage: "star.fill"),
                Bullet(title: "View friends’ garages", subtitle: "See what the crew is running and discover new favorites.", systemImage: "person.2.fill"),
            ]
        ),
        Slide(
            id: "events",
            kind: .feature,
            backgroundAssetName: "OnboardingSlide3",
            headlineParts: [
                HeadlinePart(text: "From chat to ", useGradient: false),
                HeadlinePart(text: "real life.", useGradient: true),
            ],
            body: "See upcoming events, RSVP, and check in when your crew actually shows up.",
            bullets: [
                Bullet(title: "Upcoming meets", subtitle: "Discover what’s on the calendar and what’s worth the drive.", systemImage: "calendar"),
                Bullet(title: "RSVP", subtitle: "See who’s going before you roll out.", systemImage: "hand.thumbsup.fill"),
                Bullet(title: "Automatic check-ins", subtitle: "Let Driftd check you in when you reach the venue during the window.", systemImage: "mappin.and.ellipse"),
            ]
        ),
        Slide(
            id: "progression",
            kind: .feature,
            backgroundAssetName: "OnboardingSlide5",
            headlineParts: [
                HeadlinePart(text: "Earn your ", useGradient: false),
                HeadlinePart(text: "place.", useGradient: true),
            ],
            body: "Driftd rewards participation, activity, and showing up with your crew.",
            bullets: [
                Bullet(title: "XP and levels", subtitle: "Stay active and climb with a system tuned for drivers, not grind.", systemImage: "chevron.up.circle.fill"),
                Bullet(title: "Named tiers", subtitle: "From Rookie through Legend — clear ranks, serious energy.", systemImage: "hexagon.fill"),
                Bullet(title: "Progression badges", subtitle: "Wear your tier with understated, premium badge art.", systemImage: "medal.fill"),
            ]
        ),
    ]
}
