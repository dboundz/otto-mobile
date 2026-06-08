import SwiftUI

/// Card-styled on/off toggle matching the sharing dialog “Save this Drive” row.
struct OttoToggleSettingCard: View {
    var title: String
    @Binding var isOn: Bool
    var systemImage: String?
    var helperText: String?
    var footnoteText: String?
    var trailingText: String?
    var enabled: Bool = true
    var onChange: ((Bool) -> Void)?

    var body: some View {
        HStack(alignment: .top, spacing: systemImage == nil ? 0 : 14) {
            if let systemImage {
                Image(systemName: systemImage)
                    .font(.title3.weight(.bold))
                    .foregroundStyle(.purple)
                    .frame(width: 34, height: 34)
            }

            VStack(alignment: .leading, spacing: 8) {
                HStack(alignment: .center, spacing: 10) {
                    Text(title)
                        .font(.headline.weight(.semibold))
                        .foregroundStyle(.white)
                        .multilineTextAlignment(.leading)

                    if let trailingText, !trailingText.isEmpty {
                        Text(trailingText)
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(.white.opacity(0.55))
                    }

                    Spacer(minLength: 8)

                    Toggle("", isOn: $isOn)
                        .labelsHidden()
                        .tint(.purple)
                        .disabled(!enabled)
                        .onChange(of: isOn) { _, newValue in
                            onChange?(newValue)
                        }
                }

                if let helperText {
                    Text(helperText)
                        .font(.caption)
                        .foregroundStyle(.white.opacity(0.56))
                        .fixedSize(horizontal: false, vertical: true)
                }

                if let footnoteText {
                    Text(footnoteText)
                        .font(.caption)
                        .foregroundStyle(.white.opacity(0.44))
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.white.opacity(0.055))
        .overlay(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .stroke(Color.white.opacity(0.08), lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        .opacity(enabled ? 1 : 0.6)
    }
}
