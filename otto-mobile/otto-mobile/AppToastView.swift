import SwiftUI

struct AppToast: Identifiable, Equatable {
    let id = UUID()
    let text: String
    let systemImage: String

    static func deleted(_ item: String) -> AppToast {
        AppToast(text: "\(item) deleted", systemImage: "trash.fill")
    }

    static func deleteFailed(_ item: String) -> AppToast {
        AppToast(text: "Couldn't delete \(item.lowercased())", systemImage: "exclamationmark.triangle.fill")
    }
}

struct AppToastView: View {
    let toast: AppToast

    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: toast.systemImage)
                .foregroundStyle(.white)
            Text(toast.text)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(.white)
                .lineLimit(2)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 11)
        .background(
            RoundedRectangle(cornerRadius: 13, style: .continuous)
                .fill(
                    LinearGradient(
                        colors: [Color.purple.opacity(0.95), Color.indigo.opacity(0.9)],
                        startPoint: .leading,
                        endPoint: .trailing
                    )
                )
        )
        .overlay(
            RoundedRectangle(cornerRadius: 13, style: .continuous)
                .stroke(Color.white.opacity(0.2), lineWidth: 1)
        )
        .shadow(color: Color.purple.opacity(0.35), radius: 10, y: 3)
    }
}

private struct AppToastOverlayModifier: ViewModifier {
    let toast: AppToast?
    let topPadding: CGFloat
    let onDismiss: () -> Void

    func body(content: Content) -> some View {
        content
            .overlay(alignment: .top) {
                if let toast {
                    AppToastView(toast: toast)
                        .padding(.top, topPadding)
                        .padding(.horizontal, 16)
                        .transition(.move(edge: .top).combined(with: .opacity))
                        .onTapGesture {
                            withAnimation(.spring(response: 0.3, dampingFraction: 0.85)) {
                                onDismiss()
                            }
                        }
                        .task(id: toast.id) {
                            try? await Task.sleep(nanoseconds: 3_200_000_000)
                            guard !Task.isCancelled else { return }
                            await MainActor.run {
                                withAnimation(.spring(response: 0.3, dampingFraction: 0.85)) {
                                    onDismiss()
                                }
                            }
                        }
                }
            }
    }
}

extension View {
    func appToastOverlay(
        toast: AppToast?,
        topPadding: CGFloat = 68,
        onDismiss: @escaping () -> Void
    ) -> some View {
        modifier(AppToastOverlayModifier(toast: toast, topPadding: topPadding, onDismiss: onDismiss))
    }
}
