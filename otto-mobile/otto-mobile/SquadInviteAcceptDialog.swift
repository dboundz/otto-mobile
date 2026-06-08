import SwiftUI

/// Confirmation sheet after opening a squad invite link (`driftd.com/invite/{code}`).
struct SquadInviteAcceptDialog: View {
    let resolve: InviteLinkResolveDTO
    var isAccepting: Bool
    var onAccept: () -> Void
    var onDecline: () -> Void

    private var squadName: String {
        resolve.circle?.name ?? String(localized: "squad_invite_default_squad_name")
    }

    private var inviterName: String {
        resolve.invitedBy?.displayName ?? String(localized: "squad_invite_default_inviter_name")
    }

    var body: some View {
        OttoEducationDialog(
            allowsUnconfirmedDismiss: true,
            onDismissUnconfirmed: onDecline,
            hero: {
                OttoEducationSquadHero()
            },
            title: String(format: String(localized: "squad_invite_prompt_title"), squadName),
            bodyText: String(format: String(localized: "squad_invite_prompt_body"), inviterName, squadName),
            bulletSectionTitle: nil,
            bullets: [],
            footer: String(localized: "squad_invite_prompt_footer"),
            primaryTitle: String(localized: "squad_invite_prompt_accept"),
            onPrimary: onAccept,
            secondaryTitle: String(localized: "squad_invite_prompt_decline")
        )
        .overlay {
            if isAccepting {
                ProgressView()
                    .tint(.white)
            }
        }
        .allowsHitTesting(!isAccepting)
    }
}

private struct OttoEducationSquadHero: View {
    var body: some View {
        ZStack {
            Circle()
                .fill(Color(red: 0.482, green: 0.239, blue: 1.0).opacity(0.22))
                .frame(width: 72, height: 72)
            Image(systemName: "person.3.fill")
                .font(.system(size: 28, weight: .semibold))
                .foregroundStyle(Color(red: 0.482, green: 0.239, blue: 1.0))
        }
    }
}
