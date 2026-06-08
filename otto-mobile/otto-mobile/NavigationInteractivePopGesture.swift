import SwiftUI
import UIKit

/// When a pushed screen uses a full‑height vertical `ScrollView` (DM / squad chat), UIKit often fails to start the
/// navigation interactive‑pop gesture because the scroll pan wins. Acting as the pop gesture's delegate and allowing
/// simultaneous recognition with `UIScrollView` restores the standard edge swipe back.
private struct NavigationInteractivePopGestureAttacher: UIViewRepresentable {
    func makeCoordinator() -> Coordinator {
        Coordinator()
    }

    func makeUIView(context: Context) -> UIView {
        let view = UIView(frame: .zero)
        view.isUserInteractionEnabled = false
        view.backgroundColor = .clear
        return view
    }

    func updateUIView(_ uiView: UIView, context: Context) {
        context.coordinator.attach(from: uiView)
    }

    static func dismantleUIView(_ uiView: UIView, coordinator: Coordinator) {
        coordinator.detachIfNeeded()
    }

    final class Coordinator: NSObject, UIGestureRecognizerDelegate {
        private weak var navigationController: UINavigationController?
        private weak var attachedPopGesture: UIGestureRecognizer?
        private weak var previousDelegate: NSObject?

        func attach(from view: UIView) {
            DispatchQueue.main.async { [weak self] in
                self?.attachOnMain(from: view)
            }
        }

        private func attachOnMain(from view: UIView) {
            guard let nav = view.nearestNavigationController(),
                  let pop = nav.interactivePopGestureRecognizer else { return }

            navigationController = nav

            if attachedPopGesture !== pop {
                detachIfNeeded()
                attachedPopGesture = pop
                previousDelegate = pop.delegate as? NSObject
                pop.delegate = self
            }

            pop.isEnabled = nav.viewControllers.count > 1
        }

        func detachIfNeeded() {
            guard let pop = attachedPopGesture else {
                navigationController = nil
                return
            }
            defer {
                attachedPopGesture = nil
                previousDelegate = nil
                navigationController = nil
            }
            guard pop.delegate === self else { return }
            pop.delegate = previousDelegate as? UIGestureRecognizerDelegate
        }

        func gestureRecognizerShouldBegin(_ gestureRecognizer: UIGestureRecognizer) -> Bool {
            guard let nav = navigationController else { return false }
            return nav.viewControllers.count > 1
        }

        func gestureRecognizer(
            _ gestureRecognizer: UIGestureRecognizer,
            shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer
        ) -> Bool {
            otherGestureRecognizer.view?.isEmbeddedInScrollView == true
        }
    }
}

// MARK: - UIView helpers

private extension UIView {
    /// SwiftUI often installs the representable view before `next` points at a `UIViewController`; walk superviews too.
    func nearestNavigationController() -> UINavigationController? {
        var chain: UIView? = self
        while let view = chain {
            var responder: UIResponder? = view
            while let current = responder {
                if let vc = current as? UIViewController {
                    if let nav = vc.navigationController { return nav }
                    var ancestor: UIViewController? = vc.parent
                    while let node = ancestor {
                        if let nav = node as? UINavigationController { return nav }
                        if let nav = node.navigationController { return nav }
                        ancestor = node.parent
                    }
                }
                responder = current.next
            }
            chain = view.superview
        }
        return nil
    }

    var isEmbeddedInScrollView: Bool {
        var chain: UIView? = self
        while let view = chain {
            if view is UIScrollView { return true }
            chain = view.superview
        }
        return false
    }
}

// MARK: - Narrow edge strip (fallback)

/// SwiftUI + hidden nav bar stacks sometimes still won't start UIKit's interactive pop; a thin leading strip catches
/// horizontal drags without covering normal chat taps (content is padded in from this zone).
private struct LeadingEdgeSwipeBackStrip: View {
    var onBack: (() -> Void)? = nil
    @Environment(\.dismiss) private var dismiss

    private let edgeWidth: CGFloat = 22
    private let minTravel: CGFloat = 64

    private func performBack() {
        if let onBack {
            onBack()
        } else {
            dismiss()
        }
    }

    var body: some View {
        Color.clear
            .frame(width: edgeWidth)
            .frame(maxHeight: .infinity)
            .contentShape(Rectangle())
            .highPriorityGesture(
                DragGesture(minimumDistance: 24, coordinateSpace: .global)
                    .onEnded { value in
                        guard value.startLocation.x <= edgeWidth + 8 else { return }
                        guard value.translation.width >= minTravel else { return }
                        guard value.translation.width >= abs(value.translation.height) + 12 else { return }
                        performBack()
                    }
            )
    }
}

extension View {
    /// Enables navigation edge‑swipe back on chat‑style screens where a vertical scroll view captures pans.
    /// Pass `onBack` when `@Environment(\\.dismiss)` does not pop the parent `NavigationStack` path.
    func chatNavigationInteractivePopSwipeEnabled(onBack: (() -> Void)? = nil) -> some View {
        background(NavigationInteractivePopGestureAttacher())
            .overlay(alignment: .leading) {
                LeadingEdgeSwipeBackStrip(onBack: onBack)
            }
    }
}
