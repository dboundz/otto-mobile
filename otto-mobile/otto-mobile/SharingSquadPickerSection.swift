import SwiftUI

struct SharingSquadPickerSection: View {
    let circles: [DriveCircle]
    @Binding var selectedCircleIDs: Set<String>
    var isActiveSession: Bool = false
    var onRemoveFromActiveSession: ((String) -> Void)?

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Share with")
                .font(.caption.weight(.heavy))
                .textCase(.uppercase)
                .foregroundStyle(.white.opacity(0.56))

            let visibleCircles =
                isActiveSession
                    ? circles.filter { selectedCircleIDs.contains($0.id) }
                    : circles

            if visibleCircles.isEmpty {
                Text(
                    isActiveSession
                        ? "No active squads."
                        : "Create or join a squad to start sharing."
                )
                .font(.subheadline)
                .foregroundStyle(.white.opacity(0.62))
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(16)
                .background(Color.white.opacity(0.055))
                .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
            } else {
                VStack(spacing: 12) {
                    ForEach(visibleCircles) { circle in
                        sharingSquadRow(circle)
                    }
                }
            }

            if isActiveSession {
                Text("Removing a squad stops sharing with that squad immediately.")
                    .font(.caption)
                    .foregroundStyle(.white.opacity(0.52))
            }
        }
    }

    private func sharingSquadRow(_ circle: DriveCircle) -> some View {
        Button {
            if isActiveSession {
                onRemoveFromActiveSession?(circle.id)
            } else if selectedCircleIDs.contains(circle.id) {
                selectedCircleIDs.remove(circle.id)
            } else {
                selectedCircleIDs.insert(circle.id)
            }
        } label: {
            SquadShareListRow(
                name: circle.name,
                photoUrl: circle.photoUrl,
                icon: circle.icon,
                memberCount: circle.members.count,
                cacheStorageKey: "squadAvatar:\(circle.id)"
            ) {
                if isActiveSession {
                    Image(systemName: "xmark")
                        .font(.caption.weight(.heavy))
                        .foregroundStyle(.white.opacity(0.76))
                        .frame(width: 30, height: 30)
                        .background(Color.white.opacity(0.08))
                        .clipShape(Circle())
                        .accessibilityLabel("Remove squad")
                } else {
                    ZStack {
                        Circle()
                            .fill(selectedCircleIDs.contains(circle.id) ? Color.purple : Color.clear)
                            .overlay(Circle().stroke(Color.white.opacity(0.18), lineWidth: 1))
                        if selectedCircleIDs.contains(circle.id) {
                            Image(systemName: "checkmark")
                                .font(.caption.weight(.heavy))
                                .foregroundStyle(.white)
                        }
                    }
                    .frame(width: 30, height: 30)
                }
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 14)
            .background(Color.white.opacity(0.055))
            .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .stroke(Color.white.opacity(0.1), lineWidth: 1)
            }
        }
        .buttonStyle(.plain)
    }
}
