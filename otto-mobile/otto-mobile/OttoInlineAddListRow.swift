import SwiftUI

/// Default pattern for inline add/create rows in lists: filled icon, title, subtitle, chevron card.
struct OttoInlineAddListRow<Icon: View>: View {
    let title: String
    let subtitle: String
    let action: () -> Void
    @ViewBuilder var icon: () -> Icon

    var body: some View {
        Button(action: action) {
            HStack(spacing: 12) {
                icon()

                VStack(alignment: .leading, spacing: 4) {
                    Text(title)
                        .font(.headline.weight(.bold))
                        .foregroundStyle(.white)
                    Text(subtitle)
                        .font(.subheadline)
                        .foregroundStyle(.white.opacity(0.58))
                        .fixedSize(horizontal: false, vertical: true)
                }

                Spacer(minLength: 0)

                Image(systemName: "chevron.right")
                    .font(.caption.weight(.bold))
                    .foregroundStyle(.white.opacity(0.35))
            }
            .padding(14)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color.white.opacity(0.055))
            .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        }
        .buttonStyle(.plain)
    }
}

/// Filled-circle glyph for inline add/create list rows (48pt default).
struct OttoInlineAddListIcon: View {
    enum Style {
        case createRoute
        case createEvent
    }

    var style: Style
    var size: CGFloat = 48

    var body: some View {
        Image(systemName: systemImageName)
            .font(.system(size: glyphPointSize, weight: .bold))
            .foregroundStyle(.white)
            .frame(width: size, height: size)
            .background(Circle().fill(backgroundGradient))
    }

    private var systemImageName: String {
        switch style {
        case .createRoute:
            "plus"
        case .createEvent:
            "calendar.badge.plus"
        }
    }

    private var glyphPointSize: CGFloat {
        switch style {
        case .createRoute:
            size >= 48 ? 22 : size * 0.46
        case .createEvent:
            size >= 48 ? 20 : size * 0.42
        }
    }

    private var backgroundGradient: LinearGradient {
        switch style {
        case .createRoute:
            LinearGradient(
                colors: [Color.purple, Color.blue.opacity(0.85)],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
        case .createEvent:
            LinearGradient(
                colors: [Color.purple, Color.orange.opacity(0.88)],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
        }
    }
}

struct CreateRouteListRow: View {
    let action: () -> Void

    var body: some View {
        OttoInlineAddListRow(
            title: "Create Route",
            subtitle: "Build a new rally, cruise, or drive",
            action: action
        ) {
            OttoInlineAddListIcon(style: .createRoute)
        }
    }
}

struct CreateEventListRow: View {
    let action: () -> Void

    var body: some View {
        OttoInlineAddListRow(
            title: "Add Event",
            subtitle: "Schedule a meet, drive, or hangout for your squad",
            action: action
        ) {
            OttoInlineAddListIcon(style: .createEvent)
        }
    }
}
