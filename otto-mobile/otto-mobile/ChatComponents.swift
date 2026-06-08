import SDWebImageSwiftUI
import SwiftUI
import UIKit
import os
import AVKit
import PhotosUI
import Photos

@MainActor
enum ChatRowTimeFormatter {
    private static let formatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.timeStyle = .short
        formatter.dateStyle = .none
        return formatter
    }()

    static func string(from date: Date) -> String {
        formatter.string(from: date)
    }
}

// MARK: - Composer focus / keyboard (squad @-mentions)

enum ChatComposerFieldFocusHelper {
    /// Returns the key window's current first-responder text field or text view, if any.
    static func findFirstResponderView() -> UIView? {
        guard let scene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
              let window = scene.windows.first(where: { $0.isKeyWindow }) ?? scene.windows.first
        else {
            return nil
        }
        return findFirstResponder(in: window)
    }

    /// Puts the caret at a UTF-16 offset and nudges the keyboard back to the default type (e.g. after inserting `@` from the symbols layout).
    static func applyCaretAndDefaultKeyboard(utf16Offset: Int) {
        guard let fr = findFirstResponderView(),
              let input = fr as? UITextInput
        else { return }

        let doc = input.beginningOfDocument
        let len = (input as? UITextView)?.text.map { ($0 as NSString).length }
            ?? (input as? UITextField)?.text.map { ($0 as NSString).length }
        let maxOffset = len ?? utf16Offset
        let safeOffset = min(max(0, utf16Offset), maxOffset)
        guard let pos = input.position(from: doc, offset: safeOffset) else { return }
        input.selectedTextRange = input.textRange(from: pos, to: pos)

        if let tf = fr as? UITextField {
            tf.keyboardType = .default
            tf.reloadInputViews()
        } else if let tv = fr as? UITextView {
            tv.keyboardType = .default
            tv.reloadInputViews()
        }
    }

    /// Aligns the key-window first-responder field with the SwiftUI binding (e.g. after send clears draft while dictation left text visible).
    static func syncNativeComposerText(_ text: String) {
        guard let fr = findFirstResponderView() else { return }
        if let tf = fr as? UITextField {
            if tf.text != text {
                tf.text = text
            }
            if text.isEmpty {
                tf.unmarkText()
            }
        } else if let tv = fr as? UITextView {
            if tv.text != text {
                tv.text = text
            }
            if text.isEmpty {
                tv.unmarkText()
            }
        }
    }

    /// True when the native first-responder field still shows text after a SwiftUI binding clear (dictation ghost).
    static func nativeComposerHasNonEmptyText() -> Bool {
        guard let fr = findFirstResponderView() else { return false }
        if let tf = fr as? UITextField {
            return !(tf.text?.isEmpty ?? true)
        }
        if let tv = fr as? UITextView {
            return !(tv.text?.isEmpty ?? true)
        }
        return false
    }

    private static func findFirstResponder(in view: UIView?) -> UIView? {
        guard let view else { return nil }
        if view.isFirstResponder { return view }
        for sub in view.subviews {
            if let match = findFirstResponder(in: sub) { return match }
        }
        return nil
    }
}

// MARK: - Long-press message actions (reply / reactions)

enum ChatLongPressTiming {
    /// UIKit long-press before reply/react chrome — lower feels much snappier than system default (~0.5s).
    static let minimumPressDuration: TimeInterval = 0.28
    /// Slightly longer than the gesture so attachment taps still suppress after a successful long-press.
    static let attachmentSuppressDelayNanoseconds: UInt64 = 400_000_000
}

private enum ChatLongPressChromePalette {
    static let pillFill = Color(red: 28 / 255, green: 28 / 255, blue: 30 / 255).opacity(0.92)
    static let pillBorder = Color.white.opacity(0.05)
    static let dimOverlay = Color.black.opacity(0.58)
}

struct ChatMessageFrameKey: PreferenceKey {
    static var defaultValue: [String: CGRect] = [:]
    static func reduce(value: inout [String: CGRect], nextValue: () -> [String: CGRect]) {
        value.merge(nextValue(), uniquingKeysWith: { $1 })
    }
}

// MARK: - Scroll position (UIKit-backed pinning for chat; see ChatScrollDistanceFromBottomReporter)

/// Uses the underlying `UIScrollView` so pinning stays correct while dragging; SwiftUI global geometry on `ScrollView`
/// often does not track visible viewport vs content reliably during scroll.
enum ChatUIKitScrollPinning {
    static func isPinnedToLatest(distanceFromBottom: CGFloat, threshold: CGFloat = 120, epsilon: CGFloat = 12) -> Bool {
        let d = max(0, distanceFromBottom)
        return d <= threshold + epsilon
    }

    static func isBottomSentinelVisible(distanceFromBottom: CGFloat, isLayoutReady: Bool, threshold: CGFloat = 24, epsilon: CGFloat = 10) -> Bool {
        guard isLayoutReady else { return false }
        return isPinnedToLatest(distanceFromBottom: distanceFromBottom, threshold: threshold, epsilon: epsilon)
    }

    /// Avoid calling store pinning updates on every KVO frame when pinned state and read cursor are unchanged.
    static func shouldUpdateBottomPinning(
        oldDistance: CGFloat,
        newDistance: CGFloat,
        newestMessageID: String?,
        lastReadMessageID: String?,
        threshold: CGFloat = 120,
        epsilon: CGFloat = 12
    ) -> Bool {
        let oldPinned = isPinnedToLatest(distanceFromBottom: oldDistance, threshold: threshold, epsilon: epsilon)
        let newPinned = isPinnedToLatest(distanceFromBottom: newDistance, threshold: threshold, epsilon: epsilon)
        if oldPinned != newPinned { return true }
        if newPinned, newestMessageID != lastReadMessageID { return true }
        return false
    }

    static func shouldShowSquadDateHeader(for message: CircleChatMessageDTO, previous: CircleChatMessageDTO?) -> Bool {
        guard let previous else { return true }
        return !Calendar.current.isDate(message.createdAt, inSameDayAs: previous.createdAt)
    }

    static func previousMessage<T: Identifiable>(before message: T, in messages: [T]) -> T? where T.ID == String {
        guard let index = messages.firstIndex(where: { $0.id == message.id }), index > 0 else { return nil }
        return messages[index - 1]
    }
}

/// Weak handle to the chat transcript `UIScrollView` for momentum cancel + bottom jumps.
@MainActor
final class ChatScrollViewHandle {
    weak var scrollView: UIScrollView?

    func stopMomentum() {
        guard let scrollView else { return }
        scrollView.setContentOffset(scrollView.contentOffset, animated: false)
    }

    func scrollToBottom(animated: Bool) {
        guard let scrollView, scrollView.ottoIsScrollLayoutReady else { return }
        let targetY = scrollView.ottoMaxContentOffsetY
        scrollView.setContentOffset(CGPoint(x: scrollView.contentOffset.x, y: targetY), animated: animated)
    }
}

extension UIScrollView {
    /// True once content and viewport metrics are meaningful for scroll verification.
    var ottoIsScrollLayoutReady: Bool {
        guard contentSize.height > 0, bounds.height > 0 else { return false }
        let insetTop = adjustedContentInset.top
        let insetBottom = adjustedContentInset.bottom
        let visibleContentHeight = bounds.height - insetTop - insetBottom
        guard visibleContentHeight > 0 else { return false }
        return true
    }

    /// Distance from the visible bottom edge to the end of the scrolled content (along Y). ~0 at the newest end.
    fileprivate var ottoDistanceFromBottomEdge: CGFloat {
        guard ottoIsScrollLayoutReady else { return .greatestFiniteMagnitude }
        let insetTop = adjustedContentInset.top
        let insetBottom = adjustedContentInset.bottom
        let visibleContentHeight = bounds.height - insetTop - insetBottom
        if contentSize.height <= visibleContentHeight + 1 {
            return 0
        }
        let visibleBottomY = contentOffset.y + bounds.height - insetBottom
        return contentSize.height - visibleBottomY
    }

    fileprivate var ottoMaxContentOffsetY: CGFloat {
        let insetTop = adjustedContentInset.top
        let insetBottom = adjustedContentInset.bottom
        let visibleContentHeight = bounds.height - insetTop - insetBottom
        if contentSize.height <= visibleContentHeight + 1 {
            return -insetTop
        }
        return max(-insetTop, contentSize.height - visibleContentHeight - insetBottom)
    }
}

extension UIView {
    fileprivate var ottoEnclosingScrollView: UIScrollView? {
        var current: UIView? = self
        while let node = current {
            if let scroll = node as? UIScrollView {
                return scroll
            }
            current = node.superview
        }
        return nil
    }
}

final class ChatScrollReporterAnchorView: UIView {
    var onHierarchyChange: (() -> Void)?

    override func layoutSubviews() {
        super.layoutSubviews()
        onHierarchyChange?()
    }

    override func didMoveToWindow() {
        super.didMoveToWindow()
        onHierarchyChange?()
    }
}

/// Bridges scroll metrics from UIKit for chat transcript pinning / jump affordance visibility.
struct ChatScrollDistanceFromBottomReporter: UIViewRepresentable {
    @Binding var distanceFromBottom: CGFloat
    @Binding var isLayoutReady: Bool
    @Binding var isScrollUserInteracting: Bool
    var scrollViewHandle: ChatScrollViewHandle? = nil

    init(
        distanceFromBottom: Binding<CGFloat>,
        isLayoutReady: Binding<Bool>,
        isScrollUserInteracting: Binding<Bool> = .constant(false),
        scrollViewHandle: ChatScrollViewHandle? = nil
    ) {
        _distanceFromBottom = distanceFromBottom
        _isLayoutReady = isLayoutReady
        _isScrollUserInteracting = isScrollUserInteracting
        self.scrollViewHandle = scrollViewHandle
    }

    func makeCoordinator() -> Coordinator {
        Coordinator(
            distanceBinding: $distanceFromBottom,
            layoutReadyBinding: $isLayoutReady,
            isScrollUserInteractingBinding: $isScrollUserInteracting,
            scrollViewHandle: scrollViewHandle
        )
    }

    func makeUIView(context: Context) -> ChatScrollReporterAnchorView {
        let view = ChatScrollReporterAnchorView(frame: .zero)
        view.isUserInteractionEnabled = false
        view.backgroundColor = .clear
        let coordinator = context.coordinator
        view.onHierarchyChange = { [weak coordinator] in
            coordinator?.attach(from: view)
        }
        return view
    }

    func updateUIView(_ uiView: ChatScrollReporterAnchorView, context: Context) {
        let coordinator = context.coordinator
        uiView.onHierarchyChange = { [weak coordinator] in
            coordinator?.attach(from: uiView)
        }
        coordinator.attach(from: uiView)
    }

    static func dismantleUIView(_ uiView: ChatScrollReporterAnchorView, coordinator: Coordinator) {
        uiView.onHierarchyChange = nil
        coordinator.detach()
    }

    final class Coordinator {
        private var distanceBinding: Binding<CGFloat>
        private var layoutReadyBinding: Binding<Bool>
        private var isScrollUserInteractingBinding: Binding<Bool>
        private weak var scrollViewHandle: ChatScrollViewHandle?
        private weak var scrollView: UIScrollView?
        private var observations: [NSKeyValueObservation] = []
        private var lastInsetBottom: CGFloat?
        private var lastPublishedDistance: CGFloat = .greatestFiniteMagnitude

        init(
            distanceBinding: Binding<CGFloat>,
            layoutReadyBinding: Binding<Bool>,
            isScrollUserInteractingBinding: Binding<Bool>,
            scrollViewHandle: ChatScrollViewHandle?
        ) {
            self.distanceBinding = distanceBinding
            self.layoutReadyBinding = layoutReadyBinding
            self.isScrollUserInteractingBinding = isScrollUserInteractingBinding
            self.scrollViewHandle = scrollViewHandle
        }

        func detach() {
            observations.forEach { $0.invalidate() }
            observations.removeAll()
            scrollView = nil
            scrollViewHandle?.scrollView = nil
            lastInsetBottom = nil
            lastPublishedDistance = .greatestFiniteMagnitude
        }

        func attach(from anchor: UIView) {
            guard let sv = anchor.ottoEnclosingScrollView else { return }
            if sv === scrollView {
                publish(sv)
                return
            }
            detach()
            scrollView = sv
            scrollViewHandle?.scrollView = sv
            let opts: NSKeyValueObservingOptions = [.initial, .new]
            observations = [
                sv.observe(\.contentOffset, options: opts) { [weak self] scroll, _ in
                    self?.publish(scroll)
                },
                sv.observe(\.contentSize, options: opts) { [weak self] scroll, _ in
                    self?.publish(scroll)
                },
                sv.observe(\.contentInset, options: opts) { [weak self] scroll, _ in
                    self?.publish(scroll)
                },
                sv.observe(\.bounds, options: opts) { [weak self] scroll, _ in
                    self?.publish(scroll)
                },
            ]
        }

        private func publish(_ scroll: UIScrollView) {
            let interacting = scroll.isDragging || scroll.isTracking
            if isScrollUserInteractingBinding.wrappedValue != interacting {
                isScrollUserInteractingBinding.wrappedValue = interacting
            }

            let ready = scroll.ottoIsScrollLayoutReady
            if layoutReadyBinding.wrappedValue != ready {
                layoutReadyBinding.wrappedValue = ready
            }

            let insetBottom = scroll.adjustedContentInset.bottom
            if ready,
               ChatScrollLogic.shouldCompensateScrollForBottomInsetChange(
                   previousInsetBottom: lastInsetBottom,
                   newInsetBottom: insetBottom,
                   lastPublishedDistance: lastPublishedDistance,
                   isScrollUserInteracting: interacting,
                   isDecelerating: scroll.isDecelerating
               ) {
                scrollViewHandle?.scrollToBottom(animated: false)
            }
            lastInsetBottom = insetBottom

            let next = scroll.ottoDistanceFromBottomEdge
            lastPublishedDistance = next
            guard distanceBinding.wrappedValue != next else { return }
            distanceBinding.wrappedValue = next
        }
    }
}

// MARK: - Shared chat scroll intent execution (Squad + DM)

@MainActor
enum ChatScrollIntentExecutor {
    static let maxSettleAttempts = 10
    static let settleStepNanoseconds: UInt64 = 40_000_000
    static let stableCheckNanoseconds: UInt64 = 36_000_000
    static let stableChecksRequired = 2
    static let distanceStabilityEpsilon: CGFloat = 8

    struct Context {
        var intent: ScrollIntent
        var bottomSentinelID: String
        var newestMessageID: String?
        var anchorMessageIDs: Set<String>
        var requiresStableSettle: Bool
        var isPinnedToBottom: Bool
        var intentSource: ChatScrollIntentSource?
        var scrollViewHandle: ChatScrollViewHandle?
        var distanceFromBottom: () -> CGFloat
        var isLayoutReady: () -> Bool
    }

    struct Outcome {
        var shouldMarkHandled: Bool
    }

    static func execute(context: Context, proxy: ScrollViewProxy) async -> Outcome {
        switch context.intent {
        case .none:
            return Outcome(shouldMarkHandled: false)
        case .scrollToBottom(let animated):
            if context.intentSource == .userAction {
                context.scrollViewHandle?.stopMomentum()
            }
            if animated {
                if context.intentSource == .userAction {
                    applyScrollIntent(context: context, proxy: proxy)
                    let pinned = await verifyUserJumpPinned(context: context)
                    return Outcome(shouldMarkHandled: pinned)
                }
                context.scrollViewHandle?.scrollToBottom(animated: false)
                applyScrollIntent(context: context, proxy: proxy)
                let pinned = await verifyUserJumpPinned(context: context)
                return Outcome(shouldMarkHandled: pinned)
            }
            if context.requiresStableSettle {
                let stable = await settleToBottom(context: context, proxy: proxy)
                return Outcome(shouldMarkHandled: stable)
            }
            applyScrollToBottom(context: context, proxy: proxy)
            return Outcome(
                shouldMarkHandled: ChatUIKitScrollPinning.isBottomSentinelVisible(
                    distanceFromBottom: context.distanceFromBottom(),
                    isLayoutReady: context.isLayoutReady()
                )
            )
        case .restore(let anchorMessageId, let anchor):
            if context.isPinnedToBottom {
                OttoLog.chat.debug("blocked restore while pinned anchor=\(anchorMessageId)")
                return Outcome(shouldMarkHandled: false)
            }
            guard context.anchorMessageIDs.contains(anchorMessageId) else {
                if context.requiresStableSettle {
                    let stable = await settleToBottom(context: context, proxy: proxy)
                    return Outcome(shouldMarkHandled: stable)
                }
                applyScrollToBottom(context: context, proxy: proxy)
                return Outcome(shouldMarkHandled: true)
            }
            if context.requiresStableSettle {
                let stable = await settleToPosition(
                    context: context,
                    proxy: proxy
                ) {
                    applyRestore(anchorMessageId: anchorMessageId, anchor: anchor, proxy: proxy)
                } satisfies: { ctx in
                    ctx.isLayoutReady() && distanceIsStable(ctx.distanceFromBottom())
                }
                return Outcome(shouldMarkHandled: stable)
            }
            applyRestore(anchorMessageId: anchorMessageId, anchor: anchor, proxy: proxy)
            return Outcome(shouldMarkHandled: true)
        case .scrollToMessage:
            applyScrollIntent(context: context, proxy: proxy)
            return Outcome(shouldMarkHandled: true)
        }
    }

    private static func settleToBottom(context: Context, proxy: ScrollViewProxy) async -> Bool {
        await settleToPosition(
            context: context,
            proxy: proxy,
            acceptBestEffortOnExhaustion: true
        ) {
            applyScrollToBottom(context: context, proxy: proxy)
        } satisfies: { ctx in
            ChatUIKitScrollPinning.isBottomSentinelVisible(
                distanceFromBottom: ctx.distanceFromBottom(),
                isLayoutReady: ctx.isLayoutReady()
            )
        }
    }

    private static func settleToPosition(
        context: Context,
        proxy: ScrollViewProxy,
        acceptBestEffortOnExhaustion: Bool = false,
        applyScroll: () -> Void,
        satisfies: (Context) -> Bool
    ) async -> Bool {
        for attempt in 0..<maxSettleAttempts {
            if context.isLayoutReady() {
                applyScroll()
                await Task.yield()
            }
            if await isStable(context: context, satisfies: satisfies) {
                return true
            }
            if attempt == maxSettleAttempts - 1 {
                applyScroll()
                await Task.yield()
                try? await Task.sleep(nanoseconds: settleStepNanoseconds)
                if satisfies(context) {
                    return true
                }
                let distance = context.distanceFromBottom()
                if ChatUIKitScrollPinning.isPinnedToLatest(distanceFromBottom: distance) {
                    return true
                }
                if acceptBestEffortOnExhaustion {
                    OttoLog.chat.warning(
                        "settleToPosition accepting best-effort reveal distance=\(distance, privacy: .public)"
                    )
                    return true
                }
                OttoLog.chat.warning("settleToPosition exhausted attempts distance=\(distance, privacy: .public)")
                return false
            }
            try? await Task.sleep(nanoseconds: settleStepNanoseconds)
        }
        return false
    }

    private static func isStable(
        context: Context,
        satisfies: (Context) -> Bool
    ) async -> Bool {
        guard context.isLayoutReady() else { return false }
        var lastDistance = context.distanceFromBottom()
        for _ in 0..<stableChecksRequired {
            if !satisfies(context) {
                return false
            }
            try? await Task.sleep(nanoseconds: stableCheckNanoseconds)
            let nextDistance = context.distanceFromBottom()
            if abs(nextDistance - lastDistance) > distanceStabilityEpsilon {
                return false
            }
            lastDistance = nextDistance
        }
        return satisfies(context)
    }

    private static func distanceIsStable(_ distance: CGFloat) -> Bool {
        distance.isFinite && distance >= 0
    }

    private static func verifyUserJumpPinned(context: Context) async -> Bool {
        for _ in 0..<8 {
            context.scrollViewHandle?.stopMomentum()
            if context.isLayoutReady(),
               ChatUIKitScrollPinning.isPinnedToLatest(distanceFromBottom: context.distanceFromBottom()) {
                return true
            }
            try? await Task.sleep(nanoseconds: settleStepNanoseconds)
        }
        return ChatUIKitScrollPinning.isPinnedToLatest(distanceFromBottom: context.distanceFromBottom())
    }

    private static func applyScrollToBottom(context: Context, proxy: ScrollViewProxy) {
        if context.intentSource == .userAction {
            context.scrollViewHandle?.stopMomentum()
        }
        if let newestMessageID = context.newestMessageID {
            scrollToID(newestMessageID, anchor: .bottom, animated: false, proxy: proxy)
        }
        scrollToID(context.bottomSentinelID, anchor: .bottom, animated: false, proxy: proxy)
    }

    private static func applyRestore(anchorMessageId: String, anchor: UnitPoint, proxy: ScrollViewProxy) {
        scrollToID(anchorMessageId, anchor: anchor, animated: false, proxy: proxy)
    }

    private static func applyScrollIntent(context: Context, proxy: ScrollViewProxy) {
        if context.intentSource == .userAction {
            context.scrollViewHandle?.stopMomentum()
        }
        let intent = context.intent
        let action = {
            switch intent {
            case .none:
                break
            case .scrollToBottom:
                applyScrollToBottom(context: context, proxy: proxy)
            case .restore(let anchorMessageId, let anchor):
                applyRestore(anchorMessageId: anchorMessageId, anchor: anchor, proxy: proxy)
            case .scrollToMessage(let messageId, _):
                scrollToID(messageId, anchor: .center, animated: false, proxy: proxy)
            }
        }
        switch intent {
        case .scrollToBottom(let animated), .scrollToMessage(_, let animated):
            if animated {
                withAnimation(.easeOut(duration: 0.2), action)
            } else {
                var transaction = Transaction()
                transaction.disablesAnimations = true
                withTransaction(transaction, action)
            }
        case .restore, .none:
            var transaction = Transaction()
            transaction.disablesAnimations = true
            withTransaction(transaction, action)
        }
    }

    private static func scrollToID(_ id: String, anchor: UnitPoint, animated: Bool, proxy: ScrollViewProxy) {
        let action = {
            proxy.scrollTo(id, anchor: anchor)
        }
        if animated {
            withAnimation(.easeOut(duration: 0.2), action)
        } else {
            var transaction = Transaction()
            transaction.disablesAnimations = true
            withTransaction(transaction, action)
        }
    }
}

enum ChatScrollToLatestLayout {
    /// Space reserved above the composer for the floating jump control (typical single-line `ChatComposerBar` + gap).
    static let composerReservePoints: CGFloat = 96
    /// Scrollable gap below the newest message (reactions strip + breathing room above the composer).
    static let transcriptBottomPadding: CGFloat = 36
}

/// Floating “jump to latest” affordance; visuals match squad **member list** rows (`CirclesScreen` / Android `SquadMembersListRow` container).
struct ChatScrollToLatestFloatingButton: View {
    let visible: Bool
    var badgeCount: Int? = nil
    var bottomPadding: CGFloat
    var trailingPadding: CGFloat = 18
    let action: () -> Void

    private let side: CGFloat = 46
    private let cornerRadius: CGFloat = 14
    /// Matches `circleMemberRow` chrome in `CirclesScreen`.
    private let rowFill = Color.white.opacity(0.055)
    private let rowStroke = Color.white.opacity(0.12)
    private let iconTint = Color.white.opacity(0.65)

    var body: some View {
        Button {
            ChatMessageActionFeedback.lightImpact()
            action()
        } label: {
            ZStack(alignment: .topTrailing) {
                Image(systemName: "chevron.down")
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(iconTint)
                    .frame(width: side, height: side)

                if let raw = badgeCount, raw > 0 {
                    let text = raw > 99 ? "99+" : "\(raw)"
                    Text(text)
                        .font(.caption2.weight(.bold))
                        .foregroundStyle(.white)
                        .padding(.horizontal, 5)
                        .padding(.vertical, 2)
                        .background(Color.red.opacity(0.92))
                        .clipShape(Capsule())
                        .offset(x: 10, y: -8)
                }
            }
            .background(
                RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                    .fill(rowFill)
            )
            .overlay {
                RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                    .stroke(rowStroke, lineWidth: 1)
            }
        }
        .buttonStyle(.plain)
        .opacity(visible ? 1 : 0)
        .scaleEffect(visible ? 1 : 0.92)
        .animation(.spring(response: 0.34, dampingFraction: 0.82), value: visible)
        .allowsHitTesting(visible)
        .accessibilityLabel("Jump to latest messages")
        .padding(.trailing, trailingPadding)
        .padding(.bottom, bottomPadding)
    }
}

enum ChatBottomPullToRefreshMetrics {
    /// Pull distance (points) past the newest message required to trigger refresh.
    static let triggerDistance: CGFloat = 60
}

/// Latest merged bubble frames from layout. Updated on every `ChatMessageFrameKey` pass **without** `@State` Updated on every `ChatMessageFrameKey` pass **without** `@State`
/// so we avoid scroll jank; used to restore long-press chrome when `onPreferenceChange` does not fire again
/// after frames were cleared on dismiss (preference value unchanged).
@MainActor
enum ChatBubbleFrameScratchpad {
    static var latestByMessageId: [String: CGRect] = [:]

    static func updateIfChanged(_ frames: [String: CGRect]) {
        guard latestByMessageId != frames else { return }
        latestByMessageId = frames
    }
}

/// `ChatMessageFrameKey` merges many bubble `GeometryReader`s per frame while scrolling; writing `@State`
/// from `onPreferenceChange` in the same pass triggers "Bound preference … tried to update multiple times per frame"
/// and can contribute to instability. Coalesce to one main-queue update after layout.
@MainActor
enum ChatLongPressFrameStateScheduler {
    private static var workItem: DispatchWorkItem?

    static func schedule(filtered: [String: CGRect], apply: @escaping ([String: CGRect]) -> Void) {
        workItem?.cancel()
        let captured = filtered
        let item = DispatchWorkItem {
            apply(captured)
        }
        workItem = item
        DispatchQueue.main.async(execute: item)
    }
}

/// Bottom overlap of the software keyboard (in window coordinates) for laying out chat chrome above it.
@MainActor
enum ChatKeyboardMetrics {
    private(set) static var lastKeyboardHeight: CGFloat = 291

    static func overlapHeight(from notification: Notification) -> CGFloat {
        keyboardFrameHeight(from: notification)
    }

    static func keyboardFrameHeight(from notification: Notification) -> CGFloat {
        guard let frame = notification.userInfo?[UIResponder.keyboardFrameEndUserInfoKey] as? CGRect else {
            return 0
        }
        guard
            let scene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
            let window = scene.windows.first(where: \.isKeyWindow) ?? scene.windows.first
        else {
            return 0
        }
        let converted = window.convert(frame, from: nil)
        return max(0, window.bounds.height - converted.minY)
    }

    static func recordKeyboardHeight(from notification: Notification) {
        let overlap = keyboardFrameHeight(from: notification)
        guard overlap > 0 else { return }
        lastKeyboardHeight = overlap
    }

    static func keyWindowWidth() -> CGFloat {
        guard let scene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
              let window = scene.windows.first(where: \.isKeyWindow) ?? scene.windows.first
        else {
            return UIScreen.main.bounds.width
        }
        return window.bounds.width
    }

    static func keyWindowHeight() -> CGFloat? {
        guard let scene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
              let window = scene.windows.first(where: \.isKeyWindow) ?? scene.windows.first
        else {
            return nil
        }
        return window.bounds.height
    }

    /// Distance from the bottom of the key window to the bottom edge of `globalFrame`.
    static func gapFromWindowBottom(globalFrame: CGRect) -> CGFloat {
        guard let height = keyWindowHeight() else { return 0 }
        return max(0, height - globalFrame.maxY)
    }

    /// Extra bottom padding when SwiftUI keyboard avoidance under-lifts the composer.
    static func composerBottomPadding(
        overlap: CGFloat,
        windowBottomGap: CGFloat,
        safeBottom: CGFloat
    ) -> CGFloat {
        guard overlap > 0.5 else { return 0 }
        let keyboardIntrusion = max(0, overlap - windowBottomGap)
        if keyboardIntrusion <= 16 {
            return 0
        }
        return max(safeBottom, keyboardIntrusion)
    }
}

enum ChatKeyboardDismissZoneMetrics {
    static let topStripHeight: CGFloat = 48
    static let composerStripHeight: CGFloat = 36
    static let dismissDragThreshold: CGFloat = 24
}

enum ChatComposerKeyboardDismiss {
    static func isActive(keyboardOverlap: CGFloat, composerFocused: Bool) -> Bool {
        keyboardOverlap > 0.5 || composerFocused
    }

    static func dismiss(composerFocused: FocusState<Bool>.Binding) {
        composerFocused.wrappedValue = false
        ChatMessageActionFeedback.dismissKeyboard()
    }
}

/// Invisible swipe-down strip for WhatsApp-style keyboard dismiss (top of chat or above composer).
struct ChatKeyboardDismissZone: View {
    let height: CGFloat
    var isActive: Bool
    var onDismiss: () -> Void

    var body: some View {
        Color.clear
            .contentShape(Rectangle())
            .frame(height: height)
            .frame(maxWidth: .infinity)
            .allowsHitTesting(isActive)
            .gesture(
                DragGesture(minimumDistance: 8)
                    .onEnded { value in
                        guard isActive,
                              value.translation.height >= ChatKeyboardDismissZoneMetrics.dismissDragThreshold
                        else { return }
                        onDismiss()
                    }
            )
    }
}

extension View {
    /// Keeps `overlap` in sync with the keyboard so chat chrome stays in the visible area above it.
    func chatKeyboardOverlapTracking(_ overlap: Binding<CGFloat>) -> some View {
        onReceive(NotificationCenter.default.publisher(for: UIResponder.keyboardWillChangeFrameNotification)) { note in
            ChatKeyboardMetrics.recordKeyboardHeight(from: note)
            overlap.wrappedValue = ChatKeyboardMetrics.overlapHeight(from: note)
        }
        .onReceive(NotificationCenter.default.publisher(for: UIResponder.keyboardWillHideNotification)) { _ in
            overlap.wrappedValue = 0
        }
    }

    /// Lifts the composer above the keyboard when nested layouts under-lift (typing / multiline / tab-bar race).
    ///
    /// Recomputes padding on keyboard frame changes and when the composer grows (multiline draft, reply/edit strips).
    func chatComposerKeyboardLift(_ overlap: Binding<CGFloat>) -> some View {
        modifier(ChatComposerKeyboardLiftModifier(overlap: overlap))
    }

    /// Hides scroll transcript content during programmatic scroll settle without affecting sibling/inset chrome.
    func chatScrollSettleTranscriptVisibility(isHidden: Bool) -> some View {
        opacity(isHidden ? 0 : 1)
            .allowsHitTesting(!isHidden)
    }
}

private struct ChatComposerKeyboardLiftModifier: ViewModifier {
    @Binding var overlap: CGFloat
    @State private var bottomPadding: CGFloat = 0

    func body(content: Content) -> some View {
        content
            .padding(.bottom, bottomPadding)
            .background {
                GeometryReader { geo in
                    Color.clear
                        .onAppear {
                            refreshPadding(geo: geo)
                        }
                        .onChange(of: overlap) { _, _ in
                            refreshPadding(geo: geo)
                        }
                        .onChange(of: geo.frame(in: .global).maxY) { _, _ in
                            refreshPadding(geo: geo)
                        }
                        .onChange(of: geo.size.height) { _, _ in
                            refreshPadding(geo: geo)
                        }
                }
            }
            .chatKeyboardOverlapTracking($overlap)
    }

    private func refreshPadding(geo: GeometryProxy) {
        let gap = ChatKeyboardMetrics.gapFromWindowBottom(globalFrame: geo.frame(in: .global))
        bottomPadding = ChatKeyboardMetrics.composerBottomPadding(
            overlap: overlap,
            windowBottomGap: gap,
            safeBottom: geo.safeAreaInsets.bottom
        )
    }
}

extension View {
    /// Reports each message row’s frame in global coordinates (for anchoring action pills).
    ///
    /// The `GeometryReader` is a **background of the wrapped view**, not of the `Text` inside
    /// `ChatMessageTextBubble`. Putting it directly behind multi-line `Text` fights intrinsic sizing
    /// and can pin the bubble to a single-line height. Callers should attach this to a **single-child
    /// `ZStack`** that only contains the bubble, e.g. `ZStack { ChatMessageTextBubble(...) }.chatMessageFrameReporting(...)`.
    func chatMessageFrameReporting(messageId: String) -> some View {
        background(
            GeometryReader { geo in
                Color.clear.preference(
                    key: ChatMessageFrameKey.self,
                    value: [messageId: geo.frame(in: .global)]
                )
            }
        )
    }

    @ViewBuilder
    func conditionalChatMessageFrameReporting(messageId: String, enabled: Bool) -> some View {
        if enabled {
            chatMessageFrameReporting(messageId: messageId)
        } else {
            self
        }
    }
}

enum ChatMessageActionFeedback {
    /// Dismisses the software keyboard before showing long-press chrome so layout matches bubble frames.
    static func dismissKeyboard() {
        UIApplication.shared.sendAction(
            #selector(UIResponder.resignFirstResponder),
            to: nil,
            from: nil,
            for: nil
        )
    }

    /// Shared “soft tick” for reply/react chrome — long-press to open; emoji / Reply use the same impulse.
    static func lightImpact() {
        let fire: () -> Void = {
            let gen = UIImpactFeedbackGenerator(style: .light)
            gen.prepare()
            if #available(iOS 13.0, *) {
                gen.impactOccurred(intensity: 0.68)
            } else {
                gen.impactOccurred()
            }
        }
        if Thread.isMainThread {
            fire()
        } else {
            DispatchQueue.main.async(execute: fire)
        }
    }
}

/// Shared frosted pill used for emoji + reply actions (aligned with dark premium chat UI).
private struct ChatActionPillBackground: ViewModifier {
    func body(content: Content) -> some View {
        content
            .background {
                ZStack {
                    Capsule(style: .continuous)
                        .fill(.ultraThinMaterial)
                    Capsule(style: .continuous)
                        .fill(ChatLongPressChromePalette.pillFill)
                }
            }
            .clipShape(Capsule(style: .continuous))
            .overlay(
                Capsule(style: .continuous)
                    .stroke(ChatLongPressChromePalette.pillBorder, lineWidth: 1)
            )
    }
}

private extension View {
    func chatActionPillBackground() -> some View {
        modifier(ChatActionPillBackground())
    }
}

private struct ChatEmojiPressStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 1.1 : 1.0)
            .shadow(
                color: configuration.isPressed ? Color.purple.opacity(0.42) : Color.clear,
                radius: configuration.isPressed ? 10 : 0
            )
            .animation(.easeOut(duration: 0.16), value: configuration.isPressed)
    }
}

struct ChatReactionEmojiBar: View {
    static let defaultEmojis = ["👍", "❤️", "😂", "😮", "😢", "🙏", "🔥"]
    /// Same as the strip’s heart; double-tap on a bubble uses this so API + UI stay aligned.
    static let quickReactionHeartEmoji = "❤️"

    let onSelect: (String) -> Void

    var body: some View {
        HStack(spacing: 2) {
            ForEach(Self.defaultEmojis, id: \.self) { emoji in
                Button {
                    ChatMessageActionFeedback.lightImpact()
                    onSelect(emoji)
                } label: {
                    Text(emoji)
                        .font(.system(size: 21))
                        .frame(minWidth: 36, minHeight: 32)
                        .contentShape(Rectangle())
                }
                .buttonStyle(ChatEmojiPressStyle())
            }
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 5)
        .chatActionPillBackground()
    }
}

struct ChatReplyActionPill: View {
    let onReply: () -> Void

    var body: some View {
        Button {
            ChatMessageActionFeedback.lightImpact()
            onReply()
        } label: {
            HStack(spacing: 10) {
                Text("Reply")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.white)
                Image(systemName: "arrowshape.turn.up.left.fill")
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(.purple)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
        }
        .buttonStyle(.plain)
        .chatActionPillBackground()
    }
}

struct ChatEditActionPill: View {
    let onEdit: () -> Void

    var body: some View {
        Button {
            ChatMessageActionFeedback.lightImpact()
            onEdit()
        } label: {
            HStack(spacing: 10) {
                Text("Edit")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.white)
                Image(systemName: "square.and.pencil")
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(.purple)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
        }
        .buttonStyle(.plain)
        .chatActionPillBackground()
    }
}

struct ChatDeleteActionPill: View {
    let onDelete: () -> Void

    var body: some View {
        Button {
            ChatMessageActionFeedback.lightImpact()
            onDelete()
        } label: {
            HStack(spacing: 10) {
                Text("Delete")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(Color(red: 1, green: 0.42, blue: 0.42))
                Image(systemName: "trash")
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(Color(red: 1, green: 0.42, blue: 0.42))
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
        }
        .buttonStyle(.plain)
        .chatActionPillBackground()
    }
}

private struct ChatChromeHorizontalActionButton: View {
    let title: String
    let systemImage: String
    var accent: Color = .purple
    var titleUsesAccent: Bool = false
    /// Slightly larger icon in the wide “Reply” slot.
    var iconPointSize: CGFloat = 18
    let action: () -> Void

    var body: some View {
        Button {
            ChatMessageActionFeedback.lightImpact()
            action()
        } label: {
            VStack(spacing: 6) {
                Image(systemName: systemImage)
                    .font(.system(size: iconPointSize, weight: .semibold))
                    .foregroundStyle(accent)
                Text(title)
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(titleUsesAccent ? accent : .white)
                    .lineLimit(1)
                    .minimumScaleFactor(0.85)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 10)
            .padding(.horizontal, 4)
        }
        .buttonStyle(.plain)
        .chatActionPillBackground()
    }
}

/// Reply uses **2×** the width of edit/delete (1 : 2 : 1); the cluster is **horizontally centered** in the overlay.
private struct ChatChromeReplyCenteredActionsRow: View {
    let onReply: () -> Void
    let onDismiss: () -> Void
    var onEdit: (() -> Void)? = nil
    var onDelete: (() -> Void)? = nil

    private let rowGap: CGFloat = 8
    private let rowMinHeight: CGFloat = 72
    private let clusterCap: CGFloat = 320

    var body: some View {
        GeometryReader { geo in
            let w = max(geo.size.width, 1)
            let cluster = min(w, clusterCap)
            let side = max((cluster - 2 * rowGap) / 4, 1)
            let centerW = side * 2

            HStack {
                Spacer(minLength: 0)
                HStack(spacing: rowGap) {
                    Group {
                        if let onEdit {
                            ChatChromeHorizontalActionButton(title: "Edit", systemImage: "square.and.pencil") {
                                onEdit()
                                onDismiss()
                            }
                        } else {
                            Color.clear
                        }
                    }
                    .frame(width: side, height: rowMinHeight)

                    ChatChromeHorizontalActionButton(
                        title: "Reply",
                        systemImage: "arrowshape.turn.up.left.fill",
                        iconPointSize: 20
                    ) {
                        onReply()
                    }
                    .frame(width: centerW, height: rowMinHeight)

                    Group {
                        if let onDelete {
                            ChatChromeHorizontalActionButton(
                                title: "Delete",
                                systemImage: "trash",
                                accent: Color(red: 1, green: 0.42, blue: 0.42),
                                titleUsesAccent: true
                            ) {
                                onDelete()
                                onDismiss()
                            }
                        } else {
                            Color.clear
                        }
                    }
                    .frame(width: side, height: rowMinHeight)
                }
                .frame(width: cluster)
                Spacer(minLength: 0)
            }
            .frame(width: w, height: rowMinHeight)
        }
        .frame(height: rowMinHeight)
    }
}

struct ChatMessageReactionsStrip: View {
    let reactions: [CircleChatMessageDTO.MessageReactionDTO]
    var alignment: HorizontalAlignment = .leading
    var onTap: (() -> Void)? = nil

    private var tallies: [(emoji: String, count: Int)] {
        let grouped = Dictionary(grouping: reactions, by: \.emoji)
        return grouped.map { (emoji: $0.key, count: $0.value.count) }.sorted { $0.emoji < $1.emoji }
    }

    private var strip: some View {
        HStack(spacing: 6) {
            ForEach(tallies, id: \.emoji) { item in
                HStack(spacing: 3) {
                    Text(item.emoji)
                        .font(.caption)
                    if item.count > 1 {
                        Text("\(item.count)")
                            .font(.caption2)
                            .foregroundStyle(.white.opacity(0.65))
                    }
                }
                .padding(.horizontal, 8)
                .padding(.vertical, 4)
                .background(Color.white.opacity(0.1))
                .clipShape(Capsule())
            }
        }
        .frame(maxWidth: .infinity, alignment: alignment == .trailing ? .trailing : .leading)
    }

    var body: some View {
        if tallies.isEmpty {
            EmptyView()
        } else if let onTap {
            Button(action: onTap) {
                strip
            }
            .buttonStyle(.plain)
            .accessibilityLabel("View who reacted")
        } else {
            strip
        }
    }
}

/// Matches map `mapPreviewDetents(.user)` / squad `memberProfileDetent`: fixed height from row count, capped (~620pt).
func chatMessageReactionsSheetDetent(reactionCount: Int) -> PresentationDetent {
    let n = max(1, reactionCount)
    let base: CGFloat = 108
    let perRow: CGFloat = 64
    let height = min(620, base + CGFloat(n) * perRow)
    return .height(height)
}

struct ChatMessageReactionsDetailSheet: View {
    @EnvironmentObject private var appState: AppState
    let reactions: [CircleChatMessageDTO.MessageReactionDTO]
    let resolveDisplayName: (String) -> String

    private var sortedReactions: [CircleChatMessageDTO.MessageReactionDTO] {
        reactions.sorted {
            resolveDisplayName($0.userId).localizedCaseInsensitiveCompare(resolveDisplayName($1.userId)) == .orderedAscending
        }
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                Capsule()
                    .fill(Color.white.opacity(0.35))
                    .frame(width: 36, height: 4)
                    .padding(.top, 8)

                Text("Reactions")
                    .font(.caption2.weight(.bold))
                    .textCase(.uppercase)
                    .foregroundStyle(.white.opacity(0.45))
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 16)
                    .padding(.top, 16)
                    .padding(.bottom, 4)

                ScrollView {
                    VStack(alignment: .leading, spacing: 0) {
                        ForEach(sortedReactions, id: \.userId) { reaction in
                            reactionRow(reaction)
                        }
                    }
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
            .background(Color(red: 0.025, green: 0.025, blue: 0.035).ignoresSafeArea())
            .toolbar(.hidden, for: .navigationBar)
        }
        .preferredColorScheme(.dark)
    }

    @ViewBuilder
    private func reactionRow(_ reaction: CircleChatMessageDTO.MessageReactionDTO) -> some View {
        let name = resolveDisplayName(reaction.userId)
        HStack(spacing: 12) {
            AvatarView(
                name: name,
                avatarUrl: reaction.user?.avatarUrl,
                size: 44,
                accentColor: MapAccentPalette.resolvedColor(mapAccentKey: reaction.user?.mapAccentKey, userId: reaction.userId)
            )
            .overlay(alignment: .bottomTrailing) {
                Circle()
                    .fill(appState.avatarPresenceDotColor(forUserID: reaction.userId))
                    .frame(width: 10, height: 10)
                    .overlay(Circle().stroke(Color.black, lineWidth: 2))
            }

            Text(name)
                .font(.system(size: 16, weight: .semibold))
                .foregroundStyle(.white)

            Spacer()

            Text(reaction.emoji)
                .font(.title2)
        }
        .padding(.horizontal, 18)
        .padding(.vertical, 12)
    }
}

struct ChatMessageLongPressChrome: View {
    let bubbleFrame: CGRect
    /// Animate this from `0` to the value returned from `onMeasurePresentationLift` so the message row
    /// moves with the cutout (WhatsApp-style) when the bubble is too close to the top or bottom.
    @Binding var presentationLiftY: CGFloat
    /// Software keyboard overlap from the bottom of the window; keeps reply/emoji chrome above the keyboard.
    var keyboardOverlap: CGFloat = 0
    let onDismiss: () -> Void
    let onReply: () -> Void
    let onReaction: (String) -> Void

    @State private var dragTranslation: CGFloat = 0
    @State private var hasScheduledPresentationLift = false
    @State private var initialBubbleForLift: CGRect?

    private enum Layout {
        static let emojiBarHeight: CGFloat = 34
        static let replyPillHeight: CGFloat = 40
        static let edgeGap: CGFloat = 11
        /// Space from bubble bottom to the top of the Reply pill (long-press chrome).
        static let replyGapBelowBubble: CGFloat = 18
        /// Approximate full width of `ChatReactionEmojiBar` (used to keep the strip on-screen when the bubble hugs an edge).
        static let emojiBarEstimatedWidth: CGFloat = 292
        /// Reported global frames are often slightly inside the drawn bubble; outsets keep the dim punch from eating text (especially the last line).
        static let cutoutHorizontalOutset: CGFloat = 7
        static let cutoutTopOutset: CGFloat = 5
        static let cutoutBottomOutset: CGFloat = 14
    }

    var body: some View {
        GeometryReader { geo in
            let size = geo.size
            let safe = geo.safeAreaInsets
            let overlayGlobal = geo.frame(in: .global)
            let windowBottomGap = Self.gapFromWindowBottomToOverlayBottom(overlayGlobal: overlayGlobal)
            let bubbleLocal = bubbleFrame.offsetBy(
                dx: -overlayGlobal.minX,
                dy: -overlayGlobal.minY
            )
            let positions = Self.pillCenters(
                bubbleFrame: bubbleLocal,
                containerSize: size,
                safe: safe,
                keyboardOverlap: keyboardOverlap,
                windowBottomGap: windowBottomGap
            )
            let dragFade = 1.0 - min(dragTranslation / 180.0, 0.28)
            let cutoutRect = Self.dimCutoutRect(from: bubbleLocal)
            let cutoutIsValid = cutoutRect.width > 1 && cutoutRect.height > 1

            ZStack {
                ZStack {
                    ZStack {
                        Rectangle()
                            .fill(.ultraThinMaterial)
                        Rectangle()
                            .fill(ChatLongPressChromePalette.dimOverlay)
                    }
                    .ignoresSafeArea()

                    if cutoutIsValid {
                        RoundedRectangle(cornerRadius: 24, style: .continuous)
                            .fill(Color.white)
                            .frame(width: cutoutRect.width, height: cutoutRect.height)
                            .position(x: cutoutRect.midX, y: cutoutRect.midY)
                            .blendMode(.destinationOut)
                    }
                }
                .compositingGroup()
                .contentShape(Rectangle())
                .onTapGesture(perform: onDismiss)

                ChatReactionEmojiBar(
                    onSelect: { emoji in
                        onReaction(emoji)
                        onDismiss()
                    }
                )
                .opacity(dragFade)
                .offset(y: dragTranslation * 0.12)
                .position(x: positions.emoji.x, y: positions.emoji.y)

                ChatReplyActionPill {
                    onReply()
                    onDismiss()
                }
                .opacity(dragFade)
                .offset(y: dragTranslation * 0.18)
                .position(x: positions.reply.x, y: positions.reply.y)
            }
            .simultaneousGesture(
                DragGesture(minimumDistance: 18)
                    .onChanged { value in
                        let h = value.translation.height
                        if h > 0 {
                            dragTranslation = h
                        }
                    }
                    .onEnded { value in
                        if value.translation.height > 56 {
                            onDismiss()
                        }
                        withAnimation(.spring(response: 0.22, dampingFraction: 0.86)) {
                            dragTranslation = 0
                        }
                    }
            )
            .onAppear {
                guard !hasScheduledPresentationLift else { return }
                if initialBubbleForLift == nil {
                    initialBubbleForLift = bubbleLocal
                }
                let anchor = initialBubbleForLift ?? bubbleLocal
                let target = Self.targetPresentationLiftY(
                    bubbleFrame: anchor,
                    containerSize: size,
                    safe: safe,
                    keyboardOverlap: keyboardOverlap,
                    windowBottomGap: windowBottomGap
                )
                hasScheduledPresentationLift = true
                if abs(target) < 0.5 {
                    return
                }
                withAnimation(.spring(response: 0.22, dampingFraction: 0.84)) {
                    presentationLiftY = target
                }
            }
            .onChange(of: keyboardOverlap) { _, newOverlap in
                let gap = Self.gapFromWindowBottomToOverlayBottom(overlayGlobal: overlayGlobal)
                let target = Self.targetPresentationLiftY(
                    bubbleFrame: bubbleLocal,
                    containerSize: size,
                    safe: safe,
                    keyboardOverlap: newOverlap,
                    windowBottomGap: gap
                )
                withAnimation(.spring(response: 0.22, dampingFraction: 0.84)) {
                    presentationLiftY = target
                }
            }
            .onDisappear {
                hasScheduledPresentationLift = false
                initialBubbleForLift = nil
            }
        }
    }

    private static func dimCutoutRect(from bubbleLocal: CGRect) -> CGRect {
        CGRect(
            x: bubbleLocal.minX - Layout.cutoutHorizontalOutset,
            y: bubbleLocal.minY - Layout.cutoutTopOutset,
            width: bubbleLocal.width + Layout.cutoutHorizontalOutset * 2,
            height: bubbleLocal.height + Layout.cutoutTopOutset + Layout.cutoutBottomOutset
        )
    }

    private static func keyWindowHeight() -> CGFloat? {
        guard let scene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
              let window = scene.windows.first(where: \.isKeyWindow) ?? scene.windows.first
        else {
            return nil
        }
        return window.bounds.height
    }

    /// Distance from the bottom of the key window to the bottom edge of this overlay (window/screen coordinates).
    private static func gapFromWindowBottomToOverlayBottom(overlayGlobal: CGRect) -> CGFloat {
        guard let h = keyWindowHeight() else { return 0 }
        return max(0, h - overlayGlobal.maxY)
    }

    /// Effective bottom inset for reply/emoji chrome within this overlay.
    ///
    /// When the squad/DM layout already ends above the software keyboard, `windowBottomGap` is large
    /// (roughly keyboard height plus any chrome below the chat) and only the home-indicator inset matters.
    ///
    /// When the overlay’s bottom still aligns with the **window** bottom, `windowBottomGap` is near zero
    /// while `keyboardOverlap` is the keyboard height — we must reserve that space so pills are not
    /// positioned in the occluded region (they would appear to “jump” off-screen).
    private static func bottomInsetForChrome(
        safe: EdgeInsets,
        keyboardOverlap: CGFloat,
        windowBottomGap: CGFloat
    ) -> CGFloat {
        if keyboardOverlap <= 0.5 {
            return safe.bottom
        }
        let keyboardIntrusion = max(0, keyboardOverlap - windowBottomGap)
        if keyboardIntrusion <= 16 {
            return max(safe.bottom, 12)
        }
        return max(safe.bottom, keyboardIntrusion)
    }

    private static func targetPresentationLiftY(
        bubbleFrame: CGRect,
        containerSize: CGSize,
        safe: EdgeInsets,
        keyboardOverlap: CGFloat,
        windowBottomGap: CGFloat
    ) -> CGFloat {
        let bottomInset = bottomInsetForChrome(
            safe: safe,
            keyboardOverlap: keyboardOverlap,
            windowBottomGap: windowBottomGap
        )
        let gTop = Layout.edgeGap + Layout.emojiBarHeight / 2
        let gBottom = Layout.replyGapBelowBubble + Layout.replyPillHeight / 2
        let minEmojiY = safe.top + Layout.emojiBarHeight / 2 + 6
        let maxReplyY = containerSize.height - bottomInset - 96

        let emojiCenterY = bubbleFrame.minY - gTop
        let replyCenterY = bubbleFrame.maxY + gBottom

        let dyLow = minEmojiY - emojiCenterY
        let dyHigh = maxReplyY - replyCenterY

        if dyLow <= 0, 0 <= dyHigh {
            return 0
        }
        if dyLow > dyHigh {
            return (dyLow + dyHigh) / 2
        }
        if 0 < dyLow {
            return dyLow
        }
        if dyHigh < 0 {
            return dyHigh
        }
        return 0
    }

    /// Places emoji / reply pill centers on the bubble’s axis when there is room; otherwise clamps so the wide
    /// emoji strip stays inside the safe rect (edge bubbles), or centers in the safe area on very narrow widths.
    /// Vertical position still follows the bubble — only `presentationLiftY` animates the row for top/bottom fit.
    private static func pillCenters(
        bubbleFrame: CGRect,
        containerSize: CGSize,
        safe: EdgeInsets,
        keyboardOverlap: CGFloat,
        windowBottomGap: CGFloat
    ) -> (emoji: CGPoint, reply: CGPoint) {
        let bottomInset = bottomInsetForChrome(
            safe: safe,
            keyboardOverlap: keyboardOverlap,
            windowBottomGap: windowBottomGap
        )
        let halfBar = Layout.emojiBarEstimatedWidth / 2
        let inset = Layout.edgeGap
        let innerLeft = safe.leading + halfBar + inset
        let innerRight = containerSize.width - safe.trailing - halfBar - inset
        let preferredX = bubbleFrame.midX
        let midX: CGFloat
        if innerLeft > innerRight {
            midX = safe.leading + (containerSize.width - safe.leading - safe.trailing) / 2
        } else {
            midX = min(max(preferredX, innerLeft), innerRight)
        }
        let gTop = Layout.edgeGap + Layout.emojiBarHeight / 2
        let gBottom = Layout.replyGapBelowBubble + Layout.replyPillHeight / 2
        let emojiY = bubbleFrame.minY - gTop
        var replyY = bubbleFrame.maxY + gBottom
        let minReplyTop = containerSize.height - bottomInset - Layout.replyPillHeight - inset
        replyY = min(replyY, minReplyTop + Layout.replyPillHeight / 2)
        return (CGPoint(x: midX, y: emojiY), CGPoint(x: midX, y: replyY))
    }
}

struct ChatMessageActionOverlay: View {
    let onDismiss: () -> Void
    let onReply: () -> Void
    let onReaction: (String) -> Void
    var onEdit: (() -> Void)? = nil
    var onDelete: (() -> Void)? = nil

    var body: some View {
        ZStack {
            ChatLongPressChromePalette.dimOverlay
                .ignoresSafeArea()
                .contentShape(Rectangle())
                .onTapGesture(perform: onDismiss)

            VStack(spacing: 12) {
                ChatReactionEmojiBar { emoji in
                    onReaction(emoji)
                    onDismiss()
                }

                ChatChromeReplyCenteredActionsRow(
                    onReply: onReply,
                    onDismiss: onDismiss,
                    onEdit: onEdit,
                    onDelete: onDelete
                )
            }
            .padding(.horizontal, 18)
            .padding(.vertical, 16)
            .background {
                RoundedRectangle(cornerRadius: 24, style: .continuous)
                    .fill(Color.black.opacity(0.72))
                    .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
            }
            .overlay {
                RoundedRectangle(cornerRadius: 24, style: .continuous)
                    .stroke(Color.white.opacity(0.08), lineWidth: 1)
            }
            .shadow(color: .black.opacity(0.35), radius: 22, y: 12)
            .transition(.scale(scale: 0.96).combined(with: .opacity))
        }
    }
}

enum ChatScrollHelper {
    static func jumpToBottom(
        animated: Bool,
        resetTarget: @escaping @MainActor () -> Void,
        setBottomTarget: @escaping @MainActor () -> Void
    ) {
        resetTarget()
        Task { @MainActor in
            await Task.yield()
            await Task.yield()
            if animated {
                withAnimation(.easeOut(duration: 0.2)) {
                    setBottomTarget()
                }
            } else {
                var transaction = Transaction()
                transaction.disablesAnimations = true
                withTransaction(transaction) {
                    setBottomTarget()
                }
            }
        }
    }
}

enum ChatTextFormatter {
    static let mentionUserIDAttribute = NSAttributedString.Key("otto.mentionUserID")
    private static let linkDetector = try? NSDataDetector(types: NSTextCheckingResult.CheckingType.link.rawValue)

    private static func applyLinkAttributes(to mutable: NSMutableAttributedString, baseForeground: UIColor) {
        let full = mutable.string
        guard let detector = linkDetector else { return }
        let fullRange = NSRange(location: 0, length: (full as NSString).length)
        for match in detector.matches(in: full, options: [], range: fullRange) {
            guard let url = match.url else { continue }
            mutable.addAttribute(.link, value: url, range: match.range)
            mutable.addAttribute(.foregroundColor, value: baseForeground, range: match.range)
            mutable.addAttribute(.underlineStyle, value: NSUnderlineStyle.single.rawValue, range: match.range)
        }
    }

    static func attributedMessageBody(_ body: String, foregroundColor: Color) -> AttributedString {
        var attributed = AttributedString(body)
        attributed.foregroundColor = foregroundColor
        guard let detector = try? NSDataDetector(types: NSTextCheckingResult.CheckingType.link.rawValue) else {
            return attributed
        }
        let nsRange = NSRange(body.startIndex..<body.endIndex, in: body)
        for match in detector.matches(in: body, options: [], range: nsRange) {
            guard let url = match.url,
                  let range = Range(match.range, in: body),
                  let attributedRange = Range(range, in: attributed) else { continue }
            attributed[attributedRange].link = url
            attributed[attributedRange].foregroundColor = foregroundColor
            attributed[attributedRange].underlineStyle = .single
        }
        return attributed
    }

    /// UIKit copy of `attributedMessageBody` so bubble layout uses TextKit (`UITextView`), which sizes multiline link text correctly.
    static func nsAttributedMessageBody(_ body: String, foregroundColor: UIColor = .white) -> NSAttributedString {
        let font = UIFont.preferredFont(forTextStyle: .subheadline)
        let mutable = NSMutableAttributedString(string: body, attributes: [
            .font: font,
            .foregroundColor: foregroundColor
        ])
        applyLinkAttributes(to: mutable, baseForeground: foregroundColor)
        return mutable
    }

    static func nsAttributedMessageBody(
        _ body: String,
        mentions: [CircleChatMentionSpanDTO],
        mentionLabel: (String) -> String,
        baseForeground: UIColor,
        mentionForeground: UIColor
    ) -> NSAttributedString {
        let font = UIFont.preferredFont(forTextStyle: .subheadline)
        let boldFont =
            font.fontDescriptor.withSymbolicTraits(.traitBold)
                .map { UIFont(descriptor: $0, size: font.pointSize) } ?? font
        let sorted = mentions
            .filter { $0.length > 0 && $0.start >= 0 }
            .sorted { $0.start < $1.start }
        guard !sorted.isEmpty else {
            return nsAttributedMessageBody(body, foregroundColor: baseForeground)
        }

        let nsBody = body as NSString
        let len = nsBody.length
        let mutable = NSMutableAttributedString()
        var cursor = 0

        for m in sorted {
            guard m.start + m.length <= len, m.start >= cursor else { continue }
            if m.start > cursor {
                let slice = nsBody.substring(with: NSRange(location: cursor, length: m.start - cursor))
                mutable.append(NSAttributedString(string: slice, attributes: [
                    .font: font,
                    .foregroundColor: baseForeground
                ]))
            }

            let label = mentionLabel(m.userId).trimmingCharacters(in: .whitespacesAndNewlines)
            let nameLen = max(0, m.length - 1)
            let fallback =
                nameLen > 0
                    ? nsBody.substring(with: NSRange(location: m.start + 1, length: min(nameLen, len - (m.start + 1))))
                    : ""
            let shown = label.isEmpty ? fallback : label
            let mentionText = "@" + shown
            let mentionRun = NSAttributedString(string: mentionText, attributes: [
                .font: boldFont,
                .foregroundColor: mentionForeground,
                Self.mentionUserIDAttribute: m.userId
            ])
            mutable.append(mentionRun)
            cursor = m.start + m.length
        }

        if cursor < len {
            let slice = nsBody.substring(with: NSRange(location: cursor, length: len - cursor))
            mutable.append(NSAttributedString(string: slice, attributes: [
                .font: font,
                .foregroundColor: baseForeground
            ]))
        }

        applyLinkAttributes(to: mutable, baseForeground: baseForeground)
        return mutable
    }
}

/// Quoted message block inside a reply bubble (parent message preview).
struct ChatMessageReplyQuote: Equatable {
    let authorName: String
    let authorAccent: Color
    let avatarUrl: String?
    let quotedBody: String
}

extension ChatMessageReplyQuote {
    /// Builds from API `replyTo` when the parent message was populated on the server.
    init?(replyTo: CircleChatMessageDTO.ReplyPreviewDTO?) {
        guard let replyTo else { return nil }
        let name = replyTo.sender?.displayName ?? "Member"
        let uid = replyTo.sender?.id ?? replyTo.senderUserId ?? ""
        let trimmedBody = replyTo.body.trimmingCharacters(in: .whitespacesAndNewlines)
        let quoted: String
        if !trimmedBody.isEmpty {
            quoted = trimmedBody
        } else if replyTo.imageUrl != nil {
            quoted = "Photo"
        } else if replyTo.videoAttachment != nil {
            quoted = "Video"
        } else {
            quoted = trimmedBody
        }
        self.init(
            authorName: name,
            authorAccent: MapAccentPalette.resolvedColor(mapAccentKey: replyTo.sender?.mapAccentKey, userId: uid),
            avatarUrl: replyTo.sender?.avatarUrl,
            quotedBody: quoted
        )
    }
}

// MARK: - Chat photo attachment (feed + fullscreen)

/// Displays a shared photo in the feed at its natural aspect ratio, capped at `ChatFeedMediaDisplay.maxHeight` (then center-cropped).
private struct ChatFeedPhotoAttachmentView: View {
    let urlString: String
    /// Width comes from the parent bubble (`ChatMessageTextBubble.bubbleInteriorWidth`); avoids UIScreen inside the view.
    let width: CGFloat
    /// Stable scope for cache (e.g. `chatMessagePhoto` + message id). Required so presigned image URLs hit the same disk/memory entry after refresh.
    var imageCacheKeyPrefix: String? = nil
    /// Reply / react — UIKit overlay so scrolling isn’t blocked (see `ChatUIKitRowGestureOverlay`).
    var onLongPress: (() -> Void)? = nil
    var onDoubleTapHeart: (() -> Void)? = nil
    @State private var showFullscreen = false
    @State private var sourcePixelSize: CGSize?

    private var displayHeight: CGFloat {
        ChatFeedMediaDisplay.displayHeight(
            containerWidth: width,
            sourceWidth: sourcePixelSize?.width,
            sourceHeight: sourcePixelSize?.height
        )
    }

    private var resolvedImageStorageKey: String? {
        guard let imageCacheKeyPrefix, !imageCacheKeyPrefix.isEmpty else { return nil }
        return RemoteImageStorageKey.stable(prefix: imageCacheKeyPrefix, sourceUrlString: urlString)
    }

    private var isAnimated: Bool {
        ChatImageURLDisplay.isAnimatedImageURL(urlString)
    }

    private var accessibilityMediaLabel: String {
        isAnimated
            ? String(localized: "chat_attachment_accessibility_gif")
            : String(localized: "chat_attachment_accessibility_photo")
    }

    var body: some View {
        Group {
            if let url = APIConfig.imageFetchURL(from: urlString) {
                Group {
                    if isAnimated {
                        ChatFeedAnimatedImage(
                            url: url,
                            width: width,
                            displayHeight: displayHeight,
                            imageCacheKeyPrefix: imageCacheKeyPrefix,
                            onImageDecoded: { size in
                                sourcePixelSize = size
                            }
                        )
                    } else {
                        CachedAsyncImage(
                            url: url,
                            storageKey: resolvedImageStorageKey,
                            onImageDecoded: { uiImage in
                                sourcePixelSize = ChatFeedMediaDisplay.displayPixelSize(for: uiImage)
                            }
                        ) { phase in
                            switch phase {
                            case .success(let image):
                                image
                                    .resizable()
                                    .scaledToFill()
                                    .frame(width: width, height: displayHeight)
                                    .clipped()
                            case .empty, .failure:
                                Color.white.opacity(0.08)
                                    .frame(width: width, height: displayHeight)
                                    .overlay {
                                        Image(systemName: "photo")
                                            .foregroundStyle(.white.opacity(0.35))
                                    }
                            @unknown default:
                                Color.white.opacity(0.08)
                                    .frame(width: width, height: displayHeight)
                            }
                        }
                    }
                }
                .frame(width: width, height: displayHeight)
                .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                .contentShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                .overlay {
                    ChatUIKitRowGestureOverlay(
                        onTap: {
                            ChatMessageActionFeedback.lightImpact()
                            showFullscreen = true
                        },
                        onLongPress: onLongPress,
                        onDoubleTapHeart: onDoubleTapHeart
                    )
                }
                .accessibilityLabel(accessibilityMediaLabel)
                .accessibilityHint("Opens full screen. Pinch or double-tap to zoom. Close to exit.")
                .accessibilityAddTraits(.isButton)
                .fullScreenCover(isPresented: $showFullscreen) {
                    if isAnimated {
                        ChatFullscreenAnimatedImageView(url: url)
                    } else {
                        ChatFullscreenPhotoView(url: url, cacheStorageKey: resolvedImageStorageKey)
                    }
                }
                .onChange(of: resolvedImageStorageKey) { _, _ in
                    sourcePixelSize = nil
                }
            }
        }
    }
}

private struct ChatFullscreenAnimatedImageView: View {
    let url: URL
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            WebImage(url: url) { image in
                image
                    .resizable()
                    .scaledToFit()
            } placeholder: {
                ProgressView().tint(.purple)
            }
            .padding(16)
        }
        .overlay(alignment: .topTrailing) {
            Button {
                dismiss()
            } label: {
                Image(systemName: "xmark.circle.fill")
                    .font(.title2)
                    .symbolRenderingMode(.palette)
                    .foregroundStyle(.white, .black.opacity(0.35))
            }
            .padding(16)
        }
    }
}

private struct ChatFullscreenPhotoView: View {
    let url: URL
    var cacheStorageKey: String? = nil
    @Environment(\.dismiss) private var dismiss

    /// Same max zoom factor as Android fullscreen chat photo.
    private static let maxZoomScale: CGFloat = 5
    private static let doubleTapZoomScale: CGFloat = 2.5
    private static let horizontalPadding: CGFloat = 8

    @State private var loadedImage: UIImage?
    @State private var loadFailed = false
    @State private var isSavingPhoto = false
    @State private var showSaveErrorAlert = false
    @State private var saveErrorMessage = ""
    @State private var showPhotosPermissionDenied = false

    var body: some View {
        ZStack {
            Color.black
                .ignoresSafeArea()

            Group {
                if loadFailed {
                    VStack(spacing: 12) {
                        Image(systemName: "photo")
                            .font(.largeTitle)
                            .foregroundStyle(.white.opacity(0.5))
                        Text("Couldn't load image")
                            .font(.subheadline)
                            .foregroundStyle(.white.opacity(0.65))
                    }
                } else if let loadedImage {
                    ZoomableFullscreenPhotoScrollView(
                        image: loadedImage,
                        horizontalPadding: Self.horizontalPadding,
                        maxZoomScale: Self.maxZoomScale,
                        doubleTapZoomScale: Self.doubleTapZoomScale
                    )
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .ignoresSafeArea()
                } else {
                    ProgressView()
                        .tint(.white)
                }
            }

            // Only the close control should receive touches; a full-screen overlay would steal pinch/pan from UIKit.
            VStack(spacing: 0) {
                HStack(spacing: 0) {
                    if let loadedImage {
                        Button {
                            saveImageToPhotosLibrary(loadedImage)
                        } label: {
                            Image(systemName: "square.and.arrow.down")
                                .font(.system(size: 22, weight: .semibold))
                                .foregroundStyle(.white)
                                .frame(width: 44, height: 44)
                                .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .disabled(isSavingPhoto || loadFailed)
                        .opacity(isSavingPhoto ? 0.45 : 1)
                        .accessibilityLabel("Save to Photos")
                        .padding(.leading, 14)
                        .padding(.top, 12)
                    } else {
                        Color.clear
                            .frame(width: 44, height: 44)
                            .padding(.leading, 14)
                            .padding(.top, 12)
                            .allowsHitTesting(false)
                    }
                    Spacer(minLength: 0)
                        .allowsHitTesting(false)
                    Button {
                        dismiss()
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .font(.system(size: 30))
                            .symbolRenderingMode(.palette)
                            .foregroundStyle(.white, .white.opacity(0.25))
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Close")
                    .padding(.trailing, 18)
                    .padding(.top, 12)
                }
                Spacer(minLength: 0)
                    .allowsHitTesting(false)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .alert("Couldn’t save photo", isPresented: $showSaveErrorAlert) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(saveErrorMessage)
        }
        .alert("Photo access needed", isPresented: $showPhotosPermissionDenied) {
            Button("OK", role: .cancel) {}
        } message: {
            Text("Allow Driftd to add photos in Settings so you can save this image.")
        }
        .task(id: url) {
            loadFailed = false
            loadedImage = nil
            do {
                let ui = try await RemoteImageCache.shared.image(for: url, storageKey: cacheStorageKey)
                loadedImage = ui
            } catch {
                loadFailed = true
            }
        }
    }

    private func saveImageToPhotosLibrary(_ image: UIImage) {
        guard !isSavingPhoto else { return }
        isSavingPhoto = true
        ChatMessageActionFeedback.lightImpact()
        PHPhotoLibrary.requestAuthorization(for: .addOnly) { status in
            Task { @MainActor in
                guard status == .authorized || status == .limited else {
                    isSavingPhoto = false
                    showPhotosPermissionDenied = true
                    UINotificationFeedbackGenerator().notificationOccurred(.error)
                    return
                }
                PHPhotoLibrary.shared().performChanges {
                    PHAssetChangeRequest.creationRequestForAsset(from: image)
                } completionHandler: { success, error in
                    Task { @MainActor in
                        isSavingPhoto = false
                        if success {
                            UINotificationFeedbackGenerator().notificationOccurred(.success)
                        } else {
                            saveErrorMessage = error?.localizedDescription ?? "Something went wrong."
                            showSaveErrorAlert = true
                            UINotificationFeedbackGenerator().notificationOccurred(.error)
                        }
                    }
                }
            }
        }
    }
}

// MARK: - Chat video attachment (feed + fullscreen)

enum ChatVideoDurationFormatter {
    static func label(seconds: Double) -> String {
        let total = max(0, Int(seconds.rounded()))
        let minutes = total / 60
        let remainder = total % 60
        return String(format: "%d:%02d", minutes, remainder)
    }
}

private struct ChatFeedVideoAttachmentView: View {
    let attachment: CircleChatMessageDTO.VideoAttachmentDTO
    let width: CGFloat
    var messageCacheKeyPrefix: String? = nil
    var localThumbnail: UIImage? = nil
    var uploadProgress: Double? = nil
    var uploadPhase: ChatVideoUploadCoordinator.Phase? = nil
    var isUploadPending: Bool = false
    var onCancelUpload: (() -> Void)? = nil
    var onLongPress: (() -> Void)? = nil
    var onDoubleTapHeart: (() -> Void)? = nil

    @State private var showFullscreen = false

    private var aspectHeight: CGFloat {
        ChatFeedMediaDisplay.displayHeight(
            containerWidth: width,
            sourceWidth: CGFloat(attachment.width),
            sourceHeight: CGFloat(attachment.height)
        )
    }

    private var thumbnailCacheKey: String? {
        guard let messageCacheKeyPrefix, !messageCacheKeyPrefix.isEmpty else { return nil }
        return RemoteImageStorageKey.stable(prefix: messageCacheKeyPrefix, sourceUrlString: attachment.thumbnailUrl)
    }

    private var resolvedVideoURL: URL? {
        guard !attachment.videoUrl.isEmpty else { return nil }
        return APIConfig.imageFetchURL(from: attachment.videoUrl)
    }

    var body: some View {
        ZStack(alignment: .bottomLeading) {
            Group {
                if let localThumbnail {
                    Image(uiImage: localThumbnail)
                        .resizable()
                        .scaledToFill()
                        .frame(width: width, height: aspectHeight)
                        .clipped()
                } else if let url = APIConfig.imageFetchURL(from: attachment.thumbnailUrl) {
                    CachedAsyncImage(url: url, storageKey: thumbnailCacheKey) { phase in
                        switch phase {
                        case .success(let image):
                            image
                                .resizable()
                                .scaledToFill()
                                .frame(width: width, height: aspectHeight)
                                .clipped()
                        case .empty, .failure:
                            Color.white.opacity(0.08)
                                .overlay {
                                    Image(systemName: "video")
                                        .foregroundStyle(.white.opacity(0.35))
                                }
                        @unknown default:
                            Color.white.opacity(0.08)
                        }
                    }
                } else {
                    Color.white.opacity(0.08)
                        .overlay {
                            Image(systemName: "video")
                                .foregroundStyle(.white.opacity(0.35))
                        }
                }
            }
            .frame(width: width, height: aspectHeight)
            .clipped()
            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))

            if isUploadPending, let uploadPhase {
                Color.black.opacity(uploadPhase == .failed ? 0.5 : 0.35)
                    .frame(width: width, height: aspectHeight)
                    .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                Group {
                    switch uploadPhase {
                    case .preparing:
                        ProgressView()
                            .progressViewStyle(.circular)
                            .tint(.white)
                    case .uploading:
                        if let uploadProgress {
                            ChatVideoUploadProgressRing(progress: uploadProgress, onCancel: onCancelUpload)
                        } else {
                            ProgressView()
                                .progressViewStyle(.circular)
                                .tint(.white)
                        }
                    case .failed:
                        VStack(spacing: 6) {
                            Image(systemName: "exclamationmark.triangle.fill")
                                .font(.title3)
                                .foregroundStyle(.red.opacity(0.95))
                            Text("Upload failed")
                                .font(.caption2.weight(.semibold))
                                .foregroundStyle(.white)
                                .multilineTextAlignment(.center)
                                .lineLimit(2)
                                .padding(.horizontal, 8)
                            if onCancelUpload != nil {
                                Button("Remove") {
                                    onCancelUpload?()
                                }
                                .font(.caption2.weight(.semibold))
                                .foregroundStyle(.white.opacity(0.9))
                                .buttonStyle(.plain)
                            }
                        }
                    }
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if resolvedVideoURL != nil {
                Image(systemName: "play.circle.fill")
                    .font(.system(size: 44))
                    .foregroundStyle(.white.opacity(0.92))
                    .shadow(color: .black.opacity(0.35), radius: 8, y: 2)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            }

            Text(ChatVideoDurationFormatter.label(seconds: attachment.durationSeconds))
                .font(.caption2.weight(.semibold))
                .foregroundStyle(.white)
                .padding(.horizontal, 6)
                .padding(.vertical, 3)
                .background(Color.black.opacity(0.55), in: Capsule())
                .padding(8)
        }
        .frame(width: width, height: aspectHeight)
        .contentShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        .overlay {
            ChatUIKitRowGestureOverlay(
                onTap: {
                    guard resolvedVideoURL != nil, !isUploadPending else { return }
                    ChatMessageActionFeedback.lightImpact()
                    showFullscreen = true
                },
                onLongPress: onLongPress,
                onDoubleTapHeart: onDoubleTapHeart
            )
        }
        .accessibilityLabel(
            isUploadPending
                ? (uploadPhase == .failed ? "Video upload failed" : uploadPhase == .preparing ? "Preparing video" : "Uploading video")
                : "Video"
        )
        .accessibilityHint(
            isUploadPending
                ? (uploadPhase == .failed ? "Upload failed" : uploadPhase == .preparing ? "Video is being prepared" : "Upload in progress")
                : "Opens full screen video player"
        )
        .accessibilityAddTraits(.isButton)
        .fullScreenCover(isPresented: $showFullscreen) {
            if let url = resolvedVideoURL {
                ChatFullscreenVideoView(url: url)
            }
        }
    }
}

private struct ChatVideoUploadProgressRing: View {
    let progress: Double
    var onCancel: (() -> Void)?

    var body: some View {
        ZStack {
            Circle()
                .stroke(Color.white.opacity(0.25), lineWidth: 3)
                .frame(width: 56, height: 56)
            Circle()
                .trim(from: 0, to: min(max(progress, 0), 1))
                .stroke(Color.white, style: StrokeStyle(lineWidth: 3, lineCap: .round))
                .rotationEffect(.degrees(-90))
                .frame(width: 56, height: 56)
            Button(action: { onCancel?() }) {
                RoundedRectangle(cornerRadius: 3, style: .continuous)
                    .fill(Color.white.opacity(0.85))
                    .frame(width: 14, height: 14)
            }
            .buttonStyle(.plain)
            .disabled(onCancel == nil)
        }
    }
}

private struct ChatFullscreenVideoView: View {
    let url: URL
    @Environment(\.dismiss) private var dismiss

    @State private var isSavingVideo = false
    @State private var showSaveErrorAlert = false
    @State private var saveErrorMessage = ""
    @State private var showPhotosPermissionDenied = false

    var body: some View {
        ZStack {
            Color.black
                .ignoresSafeArea()

            ChatInlineAVPlayerView(url: url)
                .ignoresSafeArea()

            VStack(spacing: 0) {
                HStack(spacing: 0) {
                    Button {
                        saveVideoToPhotosLibrary(url: url)
                    } label: {
                        Image(systemName: "square.and.arrow.down")
                            .font(.system(size: 22, weight: .semibold))
                            .foregroundStyle(.white)
                            .frame(width: 44, height: 44)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .disabled(isSavingVideo)
                    .opacity(isSavingVideo ? 0.45 : 1)
                    .accessibilityLabel("Save to Photos")
                    .padding(.leading, 14)
                    .padding(.top, 12)

                    Spacer(minLength: 0)
                        .allowsHitTesting(false)

                    Button {
                        dismiss()
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .font(.system(size: 30))
                            .symbolRenderingMode(.palette)
                            .foregroundStyle(.white, .white.opacity(0.25))
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Close")
                    .padding(.trailing, 18)
                    .padding(.top, 12)
                }
                Spacer(minLength: 0)
                    .allowsHitTesting(false)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .alert("Couldn’t save video", isPresented: $showSaveErrorAlert) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(saveErrorMessage)
        }
        .alert("Photo access needed", isPresented: $showPhotosPermissionDenied) {
            Button("OK", role: .cancel) {}
        } message: {
            Text("Allow Driftd to add videos in Settings so you can save this video.")
        }
    }

    private func saveVideoToPhotosLibrary(url: URL) {
        guard !isSavingVideo else { return }
        isSavingVideo = true
        ChatMessageActionFeedback.lightImpact()
        PHPhotoLibrary.requestAuthorization(for: .addOnly) { status in
            Task { @MainActor in
                guard status == .authorized || status == .limited else {
                    isSavingVideo = false
                    showPhotosPermissionDenied = true
                    UINotificationFeedbackGenerator().notificationOccurred(.error)
                    return
                }
                do {
                    let (tempURL, _) = try await URLSession.shared.download(from: url)
                    let ext = url.pathExtension.isEmpty ? "mp4" : url.pathExtension
                    let destURL = FileManager.default.temporaryDirectory
                        .appendingPathComponent("chat-video-\(UUID().uuidString).\(ext)")
                    try? FileManager.default.removeItem(at: destURL)
                    try FileManager.default.moveItem(at: tempURL, to: destURL)
                    PHPhotoLibrary.shared().performChanges {
                        PHAssetChangeRequest.creationRequestForAssetFromVideo(atFileURL: destURL)
                    } completionHandler: { success, error in
                        try? FileManager.default.removeItem(at: destURL)
                        Task { @MainActor in
                            isSavingVideo = false
                            if success {
                                UINotificationFeedbackGenerator().notificationOccurred(.success)
                            } else {
                                saveErrorMessage = error?.localizedDescription ?? "Something went wrong."
                                showSaveErrorAlert = true
                                UINotificationFeedbackGenerator().notificationOccurred(.error)
                            }
                        }
                    }
                } catch {
                    isSavingVideo = false
                    saveErrorMessage = error.localizedDescription
                    showSaveErrorAlert = true
                    UINotificationFeedbackGenerator().notificationOccurred(.error)
                }
            }
        }
    }
}

private struct ChatInlineAVPlayerView: UIViewControllerRepresentable {
    let url: URL

    func makeUIViewController(context: Context) -> AVPlayerViewController {
        let controller = AVPlayerViewController()
        controller.player = AVPlayer(url: url)
        controller.showsPlaybackControls = true
        controller.videoGravity = .resizeAspect
        controller.player?.play()
        return controller
    }

    func updateUIViewController(_ uiViewController: AVPlayerViewController, context: Context) {}

    static func dismantleUIViewController(_ uiViewController: AVPlayerViewController, coordinator: ()) {
        uiViewController.player?.pause()
        uiViewController.player = nil
    }
}

// MARK: - Fullscreen photo zoom (UIKit)

/// Relays `layoutSubviews` so we fit the image as soon as the scroll view has non-zero bounds (`updateUIView` can run too early).
private final class FullscreenPhotoZoomScrollView: UIScrollView {
    var onLayoutSubviews: ((FullscreenPhotoZoomScrollView) -> Void)?

    override func layoutSubviews() {
        super.layoutSubviews()
        onLayoutSubviews?(self)
    }
}

/// Pinch / pan / double-tap zoom with `UIScrollView`, matching system photo viewers.
private struct ZoomableFullscreenPhotoScrollView: UIViewRepresentable {
    let image: UIImage
    var horizontalPadding: CGFloat
    var maxZoomScale: CGFloat
    var doubleTapZoomScale: CGFloat

    func makeCoordinator() -> Coordinator {
        Coordinator(
            horizontalPadding: horizontalPadding,
            maxZoomScale: maxZoomScale,
            doubleTapZoomScale: doubleTapZoomScale
        )
    }

    func makeUIView(context: Context) -> FullscreenPhotoZoomScrollView {
        let scroll = FullscreenPhotoZoomScrollView()
        scroll.backgroundColor = .black
        scroll.delegate = context.coordinator
        scroll.showsHorizontalScrollIndicator = false
        scroll.showsVerticalScrollIndicator = false
        scroll.bouncesZoom = true
        scroll.delaysContentTouches = false
        scroll.canCancelContentTouches = true
        scroll.alwaysBounceVertical = false
        scroll.alwaysBounceHorizontal = false

        let iv = UIImageView(image: image)
        iv.contentMode = .scaleAspectFit
        iv.isUserInteractionEnabled = false
        iv.clipsToBounds = false
        // Avoid intrinsic content size (full pixel dimensions) blowing out layout before the first fit pass.
        iv.frame = CGRect(x: 0, y: 0, width: 1, height: 1)
        scroll.addSubview(iv)
        context.coordinator.imageView = iv
        context.coordinator.scrollView = scroll

        let coord = context.coordinator
        scroll.onLayoutSubviews = { [weak coord] sv in
            coord?.layoutImageViewIfNeeded(in: sv)
        }

        let doubleTap = UITapGestureRecognizer(target: context.coordinator, action: #selector(Coordinator.handleDoubleTap(_:)))
        doubleTap.numberOfTapsRequired = 2
        scroll.addGestureRecognizer(doubleTap)

        return scroll
    }

    func updateUIView(_ scrollView: FullscreenPhotoZoomScrollView, context: Context) {
        context.coordinator.horizontalPadding = horizontalPadding
        context.coordinator.maxZoomScale = maxZoomScale
        context.coordinator.doubleTapZoomScale = doubleTapZoomScale
        context.coordinator.imageView.image = image
        context.coordinator.layoutImageViewIfNeeded(in: scrollView)
    }

    final class Coordinator: NSObject, UIScrollViewDelegate {
        var imageView: UIImageView!
        weak var scrollView: UIScrollView?
        var horizontalPadding: CGFloat
        var maxZoomScale: CGFloat
        var doubleTapZoomScale: CGFloat

        private var lastInner: CGSize = .zero
        private var lastImageId: ObjectIdentifier?

        init(
            horizontalPadding: CGFloat,
            maxZoomScale: CGFloat,
            doubleTapZoomScale: CGFloat
        ) {
            self.horizontalPadding = horizontalPadding
            self.maxZoomScale = maxZoomScale
            self.doubleTapZoomScale = doubleTapZoomScale
        }

        func viewForZooming(in scrollView: UIScrollView) -> UIView? {
            imageView
        }

        func scrollViewDidZoom(_ scrollView: UIScrollView) {
            applyZoomInsets(scrollView)
        }

        func layoutImageViewIfNeeded(in scrollView: UIScrollView) {
            let inner = scrollView.bounds.insetBy(dx: horizontalPadding, dy: 0)
            guard inner.width > 1, inner.height > 1 else { return }

            guard let img = imageView.image else { return }
            let raw = img.size
            guard raw.width > 0, raw.height > 0 else { return }

            let oid = ObjectIdentifier(img)
            if inner.size == lastInner, oid == lastImageId, imageView.bounds.width > 1 { return }
            lastInner = inner.size
            lastImageId = oid

            let innerAspect = inner.width / inner.height
            let imgAspect = raw.width / raw.height
            let fitted: CGSize
            if imgAspect > innerAspect {
                fitted = CGSize(width: inner.width, height: inner.width / imgAspect)
            } else {
                fitted = CGSize(width: inner.height * imgAspect, height: inner.height)
            }

            scrollView.minimumZoomScale = 1
            scrollView.maximumZoomScale = maxZoomScale
            scrollView.zoomScale = 1
            imageView.frame = CGRect(origin: .zero, size: fitted)
            scrollView.contentSize = fitted
            scrollView.contentOffset = .zero
            applyZoomInsets(scrollView)
        }

        /// Centers content smaller than the viewport when zoomed (standard `UIScrollView` pattern).
        private func applyZoomInsets(_ scrollView: UIScrollView) {
            let offsetX = max((scrollView.bounds.width - scrollView.contentSize.width) * 0.5, 0)
            let offsetY = max((scrollView.bounds.height - scrollView.contentSize.height) * 0.5, 0)
            scrollView.contentInset = UIEdgeInsets(top: offsetY, left: offsetX, bottom: offsetY, right: offsetX)
        }

        @objc func handleDoubleTap(_ gr: UITapGestureRecognizer) {
            guard let scrollView else { return }
            let point = gr.location(in: imageView)
            if scrollView.zoomScale > scrollView.minimumZoomScale + 0.05 {
                scrollView.setZoomScale(scrollView.minimumZoomScale, animated: true)
            } else {
                let target = min(max(doubleTapZoomScale, scrollView.minimumZoomScale), scrollView.maximumZoomScale)
                let w = scrollView.bounds.width / target
                let h = scrollView.bounds.height / target
                let rect = CGRect(x: point.x - w * 0.5, y: point.y - h * 0.5, width: w, height: h)
                scrollView.zoom(to: rect, animated: true)
            }
        }
    }
}

// MARK: - Scroll-friendly row gestures (UIKit)

/// SwiftUI gestures (including `LongPressGesture` marked `simultaneous`) often prevent the chat `ScrollView` from
/// recognizing drags that start on message content. UIKit recognizers with `cancelsTouchesInView = false` let the
/// scroll pan run in parallel.
struct ChatUIKitRowGestureOverlay: UIViewRepresentable {
    var onTap: (() -> Void)?
    var onLongPress: (() -> Void)?
    var onDoubleTapHeart: (() -> Void)?

    final class Coordinator: NSObject, UIGestureRecognizerDelegate {
        var onTap: (() -> Void)?
        var onLongPress: (() -> Void)?
        var onDoubleTapHeart: (() -> Void)?

        @objc func handleTap() {
            onTap?()
        }

        @objc func handleLongPress(_ gr: UILongPressGestureRecognizer) {
            guard gr.state == .began else { return }
            ChatMessageActionFeedback.lightImpact()
            onLongPress?()
        }

        @objc func handleDoubleTapHeart() {
            guard onDoubleTapHeart != nil else { return }
            ChatMessageActionFeedback.lightImpact()
            onDoubleTapHeart?()
        }

        func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer) -> Bool {
            if otherGestureRecognizer.view is UIScrollView { return true }
            if otherGestureRecognizer is UIPanGestureRecognizer { return true }
            return false
        }
    }

    func makeCoordinator() -> Coordinator {
        Coordinator()
    }

    func makeUIView(context: Context) -> UIView {
        let view = UIView()
        view.backgroundColor = .clear
        view.isUserInteractionEnabled = true

        let tap = UITapGestureRecognizer(target: context.coordinator, action: #selector(Coordinator.handleTap))
        tap.cancelsTouchesInView = false
        tap.delegate = context.coordinator
        view.addGestureRecognizer(tap)

        let doubleTap = UITapGestureRecognizer(target: context.coordinator, action: #selector(Coordinator.handleDoubleTapHeart))
        doubleTap.numberOfTapsRequired = 2
        doubleTap.cancelsTouchesInView = false
        doubleTap.delegate = context.coordinator
        view.addGestureRecognizer(doubleTap)
        tap.require(toFail: doubleTap)

        let longPress = UILongPressGestureRecognizer(target: context.coordinator, action: #selector(Coordinator.handleLongPress(_:)))
        longPress.minimumPressDuration = ChatLongPressTiming.minimumPressDuration
        longPress.allowableMovement = 14
        longPress.cancelsTouchesInView = false
        longPress.delegate = context.coordinator
        view.addGestureRecognizer(longPress)

        return view
    }

    func updateUIView(_ uiView: UIView, context: Context) {
        context.coordinator.onTap = onTap
        context.coordinator.onLongPress = onLongPress
        context.coordinator.onDoubleTapHeart = onDoubleTapHeart
    }
}

// MARK: - Chat bubble body text (UIKit)

/// SwiftUI `Text` + `AttributedString` link runs often report a **single-line** intrinsic height; `UITextView` + TextKit sizes multiline URLs and paragraphs reliably.
private final class ChatBubbleSizingTextView: UITextView, UIGestureRecognizerDelegate {
    var layoutWidth: CGFloat = 280 {
        didSet {
            textContainer.size = CGSize(width: layoutWidth, height: .greatestFiniteMagnitude)
            invalidateIntrinsicContentSize()
        }
    }

    /// Reply / react chrome — installed here (not as SwiftUI `LongPressGesture`) so chat `ScrollView` can still pan.
    var onChatLongPress: (() -> Void)?
    /// Double-tap quick heart (Instagram-style); disabled while text selection chrome is active.
    var onDoubleTapHeart: (() -> Void)?
    /// Opens `.link` attributed ranges while keeping the text view non-selectable for normal chat gestures.
    var onLinkTap: ((URL) -> Void)?
    /// Opens a member profile for `@mentions` while keeping normal row long-press behavior intact.
    var onMentionTap: ((String) -> Void)?

    private var rowLongPressGR: UILongPressGestureRecognizer!
    private var doubleTapHeartGR: UITapGestureRecognizer!
    private var linkTapGR: UITapGestureRecognizer!

    override init(frame: CGRect, textContainer: NSTextContainer?) {
        super.init(frame: frame, textContainer: textContainer)
        configure()
    }

    required init?(coder: NSCoder) {
        super.init(coder: coder)
        configure()
    }

    private func configure() {
        isEditable = false
        // UIKit defaults `isSelectable` to true; chat rows turn this on only for the reply/text-selection chrome.
        isSelectable = false
        isScrollEnabled = false
        backgroundColor = .clear
        textContainerInset = .zero
        textContainer.lineFragmentPadding = 0
        textContainer.lineBreakMode = .byWordWrapping
        textContainer.widthTracksTextView = false
        textContainer.heightTracksTextView = false
        // Let ancestor long-press (reply/react) recognize while the finger is still down.
        delaysContentTouches = false
        canCancelContentTouches = true
        setContentCompressionResistancePriority(.required, for: .vertical)
        setContentHuggingPriority(.required, for: .vertical)
        setContentHuggingPriority(.defaultLow, for: .horizontal)
        setContentCompressionResistancePriority(.defaultLow, for: .horizontal)

        let lp = UILongPressGestureRecognizer(target: self, action: #selector(handleRowLongPress(_:)))
        lp.minimumPressDuration = ChatLongPressTiming.minimumPressDuration
        lp.allowableMovement = 14
        lp.cancelsTouchesInView = false
        lp.delegate = self
        addGestureRecognizer(lp)
        rowLongPressGR = lp

        let dt = UITapGestureRecognizer(target: self, action: #selector(handleDoubleTapHeart(_:)))
        dt.numberOfTapsRequired = 2
        dt.cancelsTouchesInView = false
        dt.delegate = self
        addGestureRecognizer(dt)
        doubleTapHeartGR = dt

        let linkTap = UITapGestureRecognizer(target: self, action: #selector(handleLinkTap(_:)))
        linkTap.cancelsTouchesInView = false
        linkTap.delegate = self
        linkTap.require(toFail: dt)
        addGestureRecognizer(linkTap)
        linkTapGR = linkTap

        reconcileChatRowGestures()
    }

    @objc private func handleRowLongPress(_ gr: UILongPressGestureRecognizer) {
        guard gr.state == .began else { return }
        ChatMessageActionFeedback.lightImpact()
        onChatLongPress?()
    }

    @objc private func handleDoubleTapHeart(_ gr: UITapGestureRecognizer) {
        guard gr.state == .ended else { return }
        guard onDoubleTapHeart != nil, !isSelectable else { return }
        ChatMessageActionFeedback.lightImpact()
        onDoubleTapHeart?()
    }

    @objc private func handleLinkTap(_ gr: UITapGestureRecognizer) {
        guard gr.state == .ended else { return }
        let point = gr.location(in: self)
        if let url = linkURL(at: point) {
            onLinkTap?(url)
        } else if let userID = mentionUserID(at: point) {
            onMentionTap?(userID)
        }
    }

    /// Non-selectable bubbles only need taps/links; `UITextView`’s built-in pan recognizer still steals vertical
    /// drags from the chat `ScrollView` even when `isScrollEnabled` is false—disable it unless selecting text.
    func reconcileChatRowGestures() {
        panGestureRecognizer.isEnabled = isSelectable
        rowLongPressGR.isEnabled = !isSelectable
        doubleTapHeartGR.isEnabled = !isSelectable && onDoubleTapHeart != nil
    }

    override func gestureRecognizerShouldBegin(_ gestureRecognizer: UIGestureRecognizer) -> Bool {
        if gestureRecognizer === doubleTapHeartGR {
            return onDoubleTapHeart != nil && !isSelectable
        }
        if gestureRecognizer === linkTapGR {
            let point = gestureRecognizer.location(in: self)
            return linkURL(at: point) != nil || mentionUserID(at: point) != nil
        }
        // When not selecting text, block the text view’s own long-press (loupe/selection) but keep our row chrome gesture.
        if !isSelectable, gestureRecognizer is UILongPressGestureRecognizer {
            return gestureRecognizer === rowLongPressGR
        }
        return super.gestureRecognizerShouldBegin(gestureRecognizer)
    }

    func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer) -> Bool {
        if gestureRecognizer === rowLongPressGR {
            if otherGestureRecognizer.view is UIScrollView { return true }
            if otherGestureRecognizer is UIPanGestureRecognizer { return true }
            return false
        }
        if gestureRecognizer === doubleTapHeartGR {
            if otherGestureRecognizer.view is UIScrollView { return true }
            if otherGestureRecognizer is UIPanGestureRecognizer { return true }
            return false
        }
        return false
    }

    override var intrinsicContentSize: CGSize {
        textContainer.size = CGSize(width: layoutWidth, height: .greatestFiniteMagnitude)
        layoutManager.ensureLayout(for: textContainer)
        let fitted = sizeThatFits(CGSize(width: layoutWidth, height: .greatestFiniteMagnitude))
        let width = min(fitted.width, layoutWidth)
        let height = fitted.height
        return CGSize(width: max(width, 1), height: max(height, 1))
    }

    private func linkURL(at rawPoint: CGPoint) -> URL? {
        attributedValue(at: rawPoint, key: .link) as? URL
    }

    private func mentionUserID(at rawPoint: CGPoint) -> String? {
        attributedValue(at: rawPoint, key: ChatTextFormatter.mentionUserIDAttribute) as? String
    }

    private func attributedValue(at rawPoint: CGPoint, key: NSAttributedString.Key) -> Any? {
        guard let attributedText, attributedText.length > 0 else { return nil }
        var point = rawPoint
        point.x -= textContainerInset.left
        point.y -= textContainerInset.top
        guard point.x >= 0, point.y >= 0 else { return nil }

        layoutManager.ensureLayout(for: textContainer)
        let glyphIndex = layoutManager.glyphIndex(for: point, in: textContainer)
        guard glyphIndex < layoutManager.numberOfGlyphs else { return nil }
        let lineRect = layoutManager.lineFragmentUsedRect(forGlyphAt: glyphIndex, effectiveRange: nil)
        guard lineRect.insetBy(dx: -4, dy: -4).contains(point) else { return nil }

        let characterIndex = layoutManager.characterIndex(for: point, in: textContainer, fractionOfDistanceBetweenInsertionPoints: nil)
        guard characterIndex < attributedText.length else { return nil }
        return attributedText.attribute(key, at: characterIndex, effectiveRange: nil)
    }
}

private struct ChatBubbleBodyText: UIViewRepresentable {
    var bodyText: String
    var maxLayoutWidth: CGFloat
    var isSelectable: Bool = false
    var onLongPress: (() -> Void)?
    var onDoubleTapHeart: (() -> Void)?
    var onMentionTap: ((String) -> Void)?
    var mentions: [CircleChatMentionSpanDTO] = []
    var mentionDisplayNameByUserId: [String: String] = [:]
    var isMine: Bool = false

    final class Coordinator: NSObject, UITextViewDelegate {
        var renderedBodyText: String?
        var renderedMentions: [CircleChatMentionSpanDTO] = []
        var renderedMentionDisplayNameByUserId: [String: String] = [:]
        var renderedIsMine: Bool?

        func textView(_ textView: UITextView, primaryActionFor textItem: UITextItem, defaultAction: UIAction) -> UIAction? {
            guard case let .link(url) = textItem.content else { return defaultAction }
            return UIAction { _ in
                Task { @MainActor in
                    UIApplication.shared.open(url)
                }
            }
        }
    }

    func makeCoordinator() -> Coordinator {
        Coordinator()
    }

    func makeUIView(context: Context) -> ChatBubbleSizingTextView {
        let tv = ChatBubbleSizingTextView()
        tv.delegate = context.coordinator
        tv.isSelectable = isSelectable
        tv.reconcileChatRowGestures()
        tv.linkTextAttributes = [
            .foregroundColor: UIColor.white,
            .underlineStyle: NSUnderlineStyle.single.rawValue
        ]
        return tv
    }

    func updateUIView(_ uiView: ChatBubbleSizingTextView, context: Context) {
        if uiView.isSelectable != isSelectable {
            uiView.isSelectable = isSelectable
        }
        uiView.reconcileChatRowGestures()
        uiView.onChatLongPress = onLongPress
        uiView.onDoubleTapHeart = onDoubleTapHeart
        uiView.onLinkTap = { url in
            Task { @MainActor in
                UIApplication.shared.open(url)
            }
        }
        uiView.onMentionTap = onMentionTap

        let widthChanged = abs(uiView.layoutWidth - maxLayoutWidth) > 0.5
        if widthChanged {
            uiView.layoutWidth = maxLayoutWidth
        }

        let contentChanged =
            context.coordinator.renderedBodyText != bodyText ||
            context.coordinator.renderedMentions != mentions ||
            context.coordinator.renderedMentionDisplayNameByUserId != mentionDisplayNameByUserId ||
            context.coordinator.renderedIsMine != isMine
        if contentChanged {
            let baseColor = UIColor.white
            let mentionColor: UIColor =
                isMine
                    ? UIColor(red: 0.78, green: 1, blue: 0.86, alpha: 1)
                    : UIColor(red: 0.18, green: 0.62, blue: 0.28, alpha: 1)
            let attr: NSAttributedString
            if mentions.isEmpty {
                attr = ChatTextFormatter.nsAttributedMessageBody(bodyText, foregroundColor: baseColor)
            } else {
                attr = ChatTextFormatter.nsAttributedMessageBody(
                    bodyText,
                    mentions: mentions,
                    mentionLabel: { uid in mentionDisplayNameByUserId[uid] ?? "" },
                    baseForeground: baseColor,
                    mentionForeground: mentionColor
                )
            }
            uiView.attributedText = attr
            context.coordinator.renderedBodyText = bodyText
            context.coordinator.renderedMentions = mentions
            context.coordinator.renderedMentionDisplayNameByUserId = mentionDisplayNameByUserId
            context.coordinator.renderedIsMine = isMine
        }

        if contentChanged || widthChanged {
            uiView.textContainer.size = CGSize(width: maxLayoutWidth, height: .greatestFiniteMagnitude)
            uiView.invalidateIntrinsicContentSize()
        }
    }

    static func dismantleUIView(_ uiView: ChatBubbleSizingTextView, coordinator: Coordinator) {
        uiView.delegate = nil
        uiView.onChatLongPress = nil
        uiView.onDoubleTapHeart = nil
    }
}

struct ChatMessageTextBubble: View {
    let bodyText: String
    /// Resolved with `APIConfig.imageFetchURL` like avatars (handles `/uploads/...`, localhost URLs on device, etc.).
    var imageURLString: String?
    var videoAttachment: CircleChatMessageDTO.VideoAttachmentDTO?
    var localVideoThumbnail: UIImage? = nil
    var videoUploadProgress: Double? = nil
    var videoUploadPhase: ChatVideoUploadCoordinator.Phase? = nil
    var isVideoUploadPending: Bool = false
    var onCancelVideoUpload: (() -> Void)? = nil
    let isMine: Bool
    var replyQuote: ChatMessageReplyQuote?
    /// Pass the server message id so chat photo URLs (often presigned) reuse one `RemoteImageCache` entry per attachment.
    let messageId: String?
    /// `UITextView` is selectable by default, which steals long-press for the reply/react chrome. Off until that chrome is up for this row.
    var isTextSelectable: Bool = false
    /// Reply / react chrome (UIKit inside bubble + photo; avoids SwiftUI `LongPressGesture` blocking `ScrollView`).
    var onLongPress: (() -> Void)? = nil
    /// Double-tap to send the picker’s ❤️ reaction (matches `ChatReactionEmojiBar.quickReactionHeartEmoji`).
    var onDoubleTapHeart: (() -> Void)? = nil
    /// Tap the quoted-reply strip to scroll the transcript to the parent message (when set).
    var onTapReplyQuote: (() -> Void)? = nil
    /// Tap a rendered `@mention` to open the same profile action as tapping an avatar.
    var onMentionTap: ((String) -> Void)? = nil
    var mentions: [CircleChatMentionSpanDTO] = []
    var mentionDisplayNameByUserId: [String: String] = [:]

    // MARK: - Sizing
    //
    // Plain text only: shrink-wrap (`plainTextBubble` + `fixedSize`). Reply / image / mixed: fixed width.

    /// Max width for bubble chrome and for link/event stack in the same message.
    /// Subtracts horizontal safe-area insets plus typical scroll padding so bubbles don't clip on notched phones.
    static var standardLayoutWidth: CGFloat {
        let window = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap(\.windows)
            .first(where: \.isKeyWindow)
        let screenW = window?.windowScene?.screen.bounds.width ?? 390
        let lateralInset = (window?.safeAreaInsets.left ?? 0) + (window?.safeAreaInsets.right ?? 0)
        // Keep aligned with `OttoScreenChrome.horizontalPadding` on squad + DM message scroll stacks.
        let scrollHorizontalPaddingTotal = OttoScreenChrome.horizontalPadding * 2
        /// Avatar column + spacer (approximately); matches legacy fixed subtraction intent.
        let bubbleRowChrome: CGFloat = 72
        let raw = screenW - lateralInset - scrollHorizontalPaddingTotal - bubbleRowChrome
        return min(max(raw, 160), 320)
    }

    init(
        bodyText: String,
        imageURLString: String? = nil,
        videoAttachment: CircleChatMessageDTO.VideoAttachmentDTO? = nil,
        localVideoThumbnail: UIImage? = nil,
        videoUploadProgress: Double? = nil,
        videoUploadPhase: ChatVideoUploadCoordinator.Phase? = nil,
        isVideoUploadPending: Bool = false,
        onCancelVideoUpload: (() -> Void)? = nil,
        isMine: Bool,
        replyQuote: ChatMessageReplyQuote? = nil,
        messageId: String? = nil,
        isTextSelectable: Bool = false,
        onLongPress: (() -> Void)? = nil,
        onDoubleTapHeart: (() -> Void)? = nil,
        onTapReplyQuote: (() -> Void)? = nil,
        onMentionTap: ((String) -> Void)? = nil,
        mentions: [CircleChatMentionSpanDTO] = [],
        mentionDisplayNameByUserId: [String: String] = [:]
    ) {
        self.bodyText = bodyText
        self.imageURLString = imageURLString
        self.videoAttachment = videoAttachment
        self.localVideoThumbnail = localVideoThumbnail
        self.videoUploadProgress = videoUploadProgress
        self.videoUploadPhase = videoUploadPhase
        self.isVideoUploadPending = isVideoUploadPending
        self.onCancelVideoUpload = onCancelVideoUpload
        self.isMine = isMine
        self.replyQuote = replyQuote
        self.messageId = messageId
        self.isTextSelectable = isTextSelectable
        self.onLongPress = onLongPress
        self.onDoubleTapHeart = onDoubleTapHeart
        self.onTapReplyQuote = onTapReplyQuote
        self.onMentionTap = onMentionTap
        self.mentions = mentions
        self.mentionDisplayNameByUserId = mentionDisplayNameByUserId
    }

    private static let replyAccentBar = Color(red: 0.62, green: 0.38, blue: 0.98)

    private static var maxBubbleWidth: CGFloat { standardLayoutWidth }

    private static var bubbleInteriorWidth: CGFloat {
        maxBubbleWidth - 24
    }

    /// Quote row: avatar (28) + spacing (10) subtracted from interior width.
    private static var replyQuoteColumnMaxWidth: CGFloat {
        bubbleInteriorWidth - 38
    }

    private var hasImage: Bool {
        imageURLString.map { !$0.isEmpty } ?? false
    }

    private var hasVideo: Bool {
        videoAttachment != nil
    }

    private var chatPhotoCacheKeyPrefix: String? {
        guard let messageId, !messageId.isEmpty else { return nil }
        return "chatMessagePhoto:\(messageId)"
    }

    private var chatVideoCacheKeyPrefix: String? {
        guard let messageId, !messageId.isEmpty else { return nil }
        return "chatMessageVideoThumb:\(messageId)"
    }

    @ViewBuilder
    private func videoAttachmentView(_ attachment: CircleChatMessageDTO.VideoAttachmentDTO) -> some View {
        ChatFeedVideoAttachmentView(
            attachment: attachment,
            width: Self.bubbleInteriorWidth,
            messageCacheKeyPrefix: chatVideoCacheKeyPrefix,
            localThumbnail: localVideoThumbnail,
            uploadProgress: videoUploadProgress,
            uploadPhase: videoUploadPhase,
            isUploadPending: isVideoUploadPending,
            onCancelUpload: onCancelVideoUpload,
            onLongPress: onLongPress,
            onDoubleTapHeart: onDoubleTapHeart
        )
    }

    private func quoteInsetFill(isMine: Bool) -> Color {
        isMine ? Color.black.opacity(0.4) : Color.black.opacity(0.32)
    }

    @ViewBuilder
    private func replyQuoteBodyText(_ body: String) -> some View {
        if isTextSelectable {
            Text(body)
                .font(.caption)
                .foregroundStyle(.white.opacity(0.66))
                .lineLimit(5)
                .multilineTextAlignment(.leading)
                .frame(maxWidth: Self.replyQuoteColumnMaxWidth, alignment: .leading)
                .fixedSize(horizontal: false, vertical: true)
                .textSelection(.enabled)
        } else {
            Text(body)
                .font(.caption)
                .foregroundStyle(.white.opacity(0.66))
                .lineLimit(5)
                .multilineTextAlignment(.leading)
                .frame(maxWidth: Self.replyQuoteColumnMaxWidth, alignment: .leading)
                .fixedSize(horizontal: false, vertical: true)
                .textSelection(.disabled)
        }
    }

    @ViewBuilder
    var body: some View {
        if let quote = replyQuote {
            replyBubble(quote: quote)
                .frame(width: Self.maxBubbleWidth, alignment: .leading)
        } else if hasVideo, let attachment = videoAttachment {
            richPlainBubble(video: attachment)
                .frame(width: Self.maxBubbleWidth, alignment: .leading)
        } else if hasImage {
            richPlainBubble
                .frame(width: Self.maxBubbleWidth, alignment: .leading)
        } else if !bodyText.isEmpty {
            plainTextBubble
        } else {
            Color.clear.frame(width: 0, height: 0)
        }
    }

    @ViewBuilder
    private func replyQuoteVisual(quote: ChatMessageReplyQuote) -> some View {
        HStack(alignment: .top, spacing: 10) {
            AvatarView(
                name: quote.authorName,
                avatarUrl: quote.avatarUrl,
                size: 28,
                accentColor: quote.authorAccent,
                accentRingWidth: 1,
                whiteRingWidth: 0
            )

            VStack(alignment: .leading, spacing: 3) {
                Text(quote.authorName)
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(quote.authorAccent)
                replyQuoteBodyText(quote.quotedBody)
            }
            .frame(maxWidth: Self.replyQuoteColumnMaxWidth, alignment: .leading)
        }
        .padding(10)
        .padding(.leading, 5)
        .background(quoteInsetFill(isMine: isMine))
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        .overlay(alignment: .leading) {
            Rectangle()
                .fill(Self.replyAccentBar)
                .frame(width: 3)
                .clipShape(RoundedRectangle(cornerRadius: 1.5, style: .continuous))
                .padding(.vertical, 8)
        }
        .frame(maxWidth: Self.bubbleInteriorWidth, alignment: .leading)
    }

    private func replyQuotePreview(quote: ChatMessageReplyQuote) -> some View {
        Group {
            if let onTapReplyQuote {
                Button {
                    ChatMessageActionFeedback.lightImpact()
                    onTapReplyQuote()
                } label: {
                    replyQuoteVisual(quote: quote)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Quoted message")
                .accessibilityHint("Scrolls to quoted message in the chat")
                .accessibilityAddTraits(.isButton)
            } else {
                replyQuoteVisual(quote: quote)
            }
        }
    }

    private func replyBubble(quote: ChatMessageReplyQuote) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            replyQuotePreview(quote: quote)

            if hasVideo, let attachment = videoAttachment {
                videoAttachmentView(attachment)
            } else if hasImage, let url = imageURLString, !url.isEmpty {
                ChatFeedPhotoAttachmentView(
                    urlString: url,
                    width: Self.bubbleInteriorWidth,
                    imageCacheKeyPrefix: chatPhotoCacheKeyPrefix,
                    onLongPress: onLongPress,
                    onDoubleTapHeart: onDoubleTapHeart
                )
            }

            if !bodyText.isEmpty {
                ChatBubbleBodyText(
                    bodyText: bodyText,
                    maxLayoutWidth: Self.bubbleInteriorWidth,
                    isSelectable: isTextSelectable,
                    onLongPress: onLongPress,
                    onDoubleTapHeart: onDoubleTapHeart,
                    onMentionTap: onMentionTap,
                    mentions: mentions,
                    mentionDisplayNameByUserId: mentionDisplayNameByUserId,
                    isMine: isMine
                )
                    .frame(width: Self.bubbleInteriorWidth, alignment: .leading)
            }
        }
        .padding(12)
        .frame(width: Self.maxBubbleWidth, alignment: .leading)
        .background(isMine ? Color.purple.opacity(0.85) : Color.white.opacity(0.075))
        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .stroke(Color.white.opacity(isMine ? 0.0 : 0.12), lineWidth: 1)
        )
    }

    private var richPlainBubble: some View {
        richPlainBubbleContent(includeVideo: true)
    }

    private func richPlainBubble(video attachment: CircleChatMessageDTO.VideoAttachmentDTO) -> some View {
        richPlainBubbleContent(includeVideo: true, forcedVideoAttachment: attachment)
    }

    private func richPlainBubbleContent(
        includeVideo: Bool,
        forcedVideoAttachment: CircleChatMessageDTO.VideoAttachmentDTO? = nil
    ) -> some View {
        let attachment = forcedVideoAttachment ?? videoAttachment
        return VStack(alignment: .leading, spacing: 8) {
            if includeVideo, let attachment {
                videoAttachmentView(attachment)
            } else if let url = imageURLString, !url.isEmpty {
                ChatFeedPhotoAttachmentView(
                    urlString: url,
                    width: Self.bubbleInteriorWidth,
                    imageCacheKeyPrefix: chatPhotoCacheKeyPrefix,
                    onLongPress: onLongPress,
                    onDoubleTapHeart: onDoubleTapHeart
                )
            }
            if !bodyText.isEmpty {
                ChatBubbleBodyText(
                    bodyText: bodyText,
                    maxLayoutWidth: Self.bubbleInteriorWidth,
                    isSelectable: isTextSelectable,
                    onLongPress: onLongPress,
                    onDoubleTapHeart: onDoubleTapHeart,
                    onMentionTap: onMentionTap,
                    mentions: mentions,
                    mentionDisplayNameByUserId: mentionDisplayNameByUserId,
                    isMine: isMine
                )
                    .frame(width: Self.bubbleInteriorWidth, alignment: .leading)
            }
        }
        .padding(12)
        .frame(width: Self.maxBubbleWidth, alignment: .leading)
        .background(isMine ? Color.purple.opacity(0.85) : Color.white.opacity(0.075))
        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .stroke(Color.white.opacity(isMine ? 0.0 : 0.12), lineWidth: 1)
        )
    }

    private var plainTextBubble: some View {
        ChatBubbleBodyText(
            bodyText: bodyText,
            maxLayoutWidth: Self.bubbleInteriorWidth,
            isSelectable: isTextSelectable,
            onLongPress: onLongPress,
            onDoubleTapHeart: onDoubleTapHeart,
            onMentionTap: onMentionTap,
            mentions: mentions,
            mentionDisplayNameByUserId: mentionDisplayNameByUserId,
            isMine: isMine
        )
            .padding(.horizontal, 12)
            .padding(.vertical, 12)
            .background(plainBubbleFill, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .strokeBorder(Color.white.opacity(isMine ? 0.0 : 0.12), lineWidth: 1)
            }
            .fixedSize(horizontal: true, vertical: true)
    }

    private var plainBubbleFill: Color {
        isMine ? Color.purple.opacity(0.85) : Color.white.opacity(0.075)
    }
}

// MARK: - Rich message tail (link preview + event card)

extension ChatMessageTextBubble {
    /// Whether `ChatLinkPreviewCard` would show a tappable card for this preview.
    static func linkPreviewIsRenderable(_ preview: CircleChatMessageDTO.LinkPreviewDTO?) -> Bool {
        guard let preview,
              preview.status == "ready",
              let urlString = preview.finalUrl ?? preview.url,
              !urlString.isEmpty,
              URL(string: urlString) != nil else { return false }
        return true
    }

    static func messageHasRichTail(
        linkPreview: CircleChatMessageDTO.LinkPreviewDTO?,
        eventAttachment: CircleChatMessageDTO.EventAttachmentDTO?,
        driveAttachment: CircleChatMessageDTO.DriveAttachmentDTO? = nil,
        placeAttachment: CircleChatMessageDTO.PlaceAttachmentDTO? = nil
    ) -> Bool {
        linkPreviewIsRenderable(linkPreview) || eventAttachment != nil || driveAttachment != nil || placeAttachment != nil
    }
}

/// OG image for link preview cards — center-cropped; taller 4:5 frame for Instagram.
private struct ChatLinkPreviewThumbnail: View {
    let preview: CircleChatMessageDTO.LinkPreviewDTO
    let imageUrl: URL
    var storageKey: String?

    private var usesPortraitFrame: Bool {
        ChatLinkPreviewDisplay.usesPortraitThumbnail(preview: preview)
    }

    var body: some View {
        ZStack {
            CachedAsyncImage(url: imageUrl, storageKey: storageKey) { phase in
                switch phase {
                case .success(let image):
                    image
                        .resizable()
                        .scaledToFill()
                case .empty, .failure:
                    linkPreviewImagePlaceholder
                @unknown default:
                    linkPreviewImagePlaceholder
                }
            }
        }
        .frame(maxWidth: .infinity)
        .modifier(LinkPreviewThumbnailFrameModifier(portrait: usesPortraitFrame))
        .clipped()
    }

    private var linkPreviewImagePlaceholder: some View {
        LinearGradient(
            colors: [Color.purple.opacity(0.4), Color.black.opacity(0.85)],
            startPoint: .topLeading,
            endPoint: .bottomTrailing
        )
    }
}

private struct LinkPreviewThumbnailFrameModifier: ViewModifier {
    let portrait: Bool

    func body(content: Content) -> some View {
        if portrait {
            content.aspectRatio(ChatLinkPreviewDisplay.portraitAspectRatio, contentMode: .fit)
        } else {
            content.frame(height: ChatLinkPreviewDisplay.defaultThumbnailHeight)
        }
    }
}

struct ChatLinkPreviewCard: View {
    let preview: CircleChatMessageDTO.LinkPreviewDTO?
    /// Stable cache scope for presigned OG image URLs (same pattern as chat photo attachments).
    var messageId: String? = nil
    /// When set (e.g. `ChatMessageTextBubble.standardLayoutWidth`), card matches bubble column width.
    var fixedWidth: CGFloat? = nil
    /// If the preview URL is `https://driftd.com/e/{ref}` or legacy `ottomot.to/e/{ref}`, open in-app instead of Safari (fixes older chat shares with link preview only).
    var onOttoEventDeepLink: ((String) -> Void)? = nil
    /// Reply / react — same UIKit overlay as `ChatFeedPhotoAttachmentView` so long-press doesn’t fight `ScrollView` pan.
    var onLongPress: (() -> Void)? = nil
    var onDoubleTapHeart: (() -> Void)? = nil

    var body: some View {
        if let preview,
           preview.status == "ready",
           let urlString = preview.finalUrl ?? preview.url,
           let url = URL(string: urlString) {
            let ottoEventRef = WebsiteLinks.eventRef(fromPublicEventURL: url)
            let opensInApp = ottoEventRef != nil && onOttoEventDeepLink != nil

            let openDestination: () -> Void = {
                ChatMessageActionFeedback.lightImpact()
                if let ref = ottoEventRef, let onOttoEventDeepLink {
                    onOttoEventDeepLink(ref)
                } else {
                    Task { @MainActor in
                        UIApplication.shared.open(url)
                    }
                }
            }

            styledCard(preview: preview, destinationURL: url)
                .contentShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                .overlay {
                    ChatUIKitRowGestureOverlay(
                        onTap: openDestination,
                        onLongPress: onLongPress,
                        onDoubleTapHeart: onDoubleTapHeart
                    )
                }
                .accessibilityAddTraits(opensInApp ? .isButton : .isLink)
        }
    }

    @ViewBuilder
    private func styledCard(preview: CircleChatMessageDTO.LinkPreviewDTO, destinationURL: URL) -> some View {
        Group {
            if let w = fixedWidth {
                linkPreviewInner(preview: preview, destinationURL: destinationURL)
                    .frame(width: w, alignment: .leading)
            } else {
                linkPreviewInner(preview: preview, destinationURL: destinationURL)
                    .frame(maxWidth: 320, alignment: .leading)
            }
        }
        .background(Color.white.opacity(0.055))
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .stroke(Color.white.opacity(0.12), lineWidth: 1)
        }
    }

    private func linkPreviewImageStorageKey(sourceUrlString: String) -> String? {
        guard let messageId, !messageId.isEmpty else { return nil }
        return RemoteImageStorageKey.stable(
            prefix: "chatLinkPreview:\(messageId)",
            sourceUrlString: sourceUrlString
        )
    }

    @ViewBuilder
    private func linkPreviewInner(preview: CircleChatMessageDTO.LinkPreviewDTO, destinationURL: URL) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            if let imageUrlString = preview.imageUrl,
               let imageUrl = APIConfig.imageFetchURL(from: imageUrlString) {
                ChatLinkPreviewThumbnail(
                    preview: preview,
                    imageUrl: imageUrl,
                    storageKey: linkPreviewImageStorageKey(sourceUrlString: imageUrlString)
                )
            }

            VStack(alignment: .leading, spacing: 7) {
                if let title = preview.title, !title.isEmpty {
                    Text(title)
                        .font(.system(size: 16, weight: .semibold, design: .rounded))
                        .foregroundStyle(.white)
                        .lineLimit(2)
                }

                if let description = preview.description, !description.isEmpty {
                    Text(description)
                        .font(.subheadline)
                        .foregroundStyle(.white.opacity(0.66))
                        .lineLimit(3)
                }

                HStack(spacing: 8) {
                    Text(preview.siteName ?? destinationURL.host() ?? "Link")
                        .font(.caption)
                        .foregroundStyle(.white.opacity(0.52))
                        .lineLimit(1)
                    Spacer()
                    Text("Open")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(.purple)
                    Image(systemName: "arrow.up.right")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(.purple)
                }
                .padding(.top, 4)
            }
            .padding(12)
        }
    }

}

/// Reply banner isolated so typing the draft does not tear down `AvatarView` / `CachedAsyncImage` every keystroke.
private struct ChatComposerReplyStrip: View, Equatable {
    let author: String
    let snippet: String
    let avatarURL: String?
    let onCancel: () -> Void
    let onTapReplyTo: (() -> Void)?

    static func == (lhs: ChatComposerReplyStrip, rhs: ChatComposerReplyStrip) -> Bool {
        lhs.author == rhs.author
            && lhs.snippet == rhs.snippet
            && lhs.avatarURL == rhs.avatarURL
            && (lhs.onTapReplyTo != nil) == (rhs.onTapReplyTo != nil)
    }

    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            AvatarView(
                name: author,
                avatarUrl: avatarURL,
                size: 30,
                accentColor: .purple.opacity(0.45),
                accentRingWidth: 0,
                whiteRingWidth: 0
            )

            Group {
                if let onTapReplyTo {
                    Button {
                        ChatMessageActionFeedback.lightImpact()
                        onTapReplyTo()
                    } label: {
                        replyPreviewContent
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Quoted message")
                    .accessibilityHint(String(localized: "chat_reply_quote_go_to_message"))
                    .accessibilityAddTraits(.isButton)
                } else {
                    replyPreviewContent
                }
            }

            Spacer(minLength: 0)

            Button(action: onCancel) {
                Image(systemName: "xmark.circle.fill")
                    .font(.title3)
                    .foregroundStyle(.white.opacity(0.42))
            }
            .buttonStyle(.plain)
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .fill(Color.white.opacity(0.07))
        )
        .overlay(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .stroke(Color.white.opacity(0.1), lineWidth: 1)
        )
        .padding(.horizontal, 12)
        .padding(.top, 8)
        .padding(.bottom, 6)
    }

    private var replyPreviewContent: some View {
        HStack(alignment: .top, spacing: 10) {
            RoundedRectangle(cornerRadius: 1.5)
                .fill(Color.purple)
                .frame(width: 3, height: 44)

            VStack(alignment: .leading, spacing: 4) {
                Text("Replying to \(author)")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.purple)
                Text(snippet)
                    .font(.caption)
                    .foregroundStyle(.white.opacity(0.85))
                    .lineLimit(2)
                    .multilineTextAlignment(.leading)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }
}

/// Compact “editing message” context above the composer (cancel does not send).
private struct ChatComposerEditStrip: View {
    let preview: String
    let onCancel: () -> Void

    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: "pencil.circle.fill")
                .font(.title3)
                .foregroundStyle(Color.purple)

            VStack(alignment: .leading, spacing: 4) {
                Text("Editing message")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(Color.purple.opacity(0.95))
                Text(preview)
                    .font(.caption)
                    .foregroundStyle(.white.opacity(0.82))
                    .lineLimit(2)
                    .truncationMode(.tail)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }

            Spacer(minLength: 0)

            Button(action: onCancel) {
                Image(systemName: "xmark.circle.fill")
                    .font(.title3)
                    .foregroundStyle(.white.opacity(0.42))
            }
            .buttonStyle(.plain)
        }
        .padding(10)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .fill(Color.white.opacity(0.06))
        )
        .overlay(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .stroke(Color.purple.opacity(0.38), lineWidth: 1)
        )
        .padding(.horizontal, 12)
        .padding(.top, 6)
        .padding(.bottom, 6)
    }
}

struct ChatComposerBar: View {
    let placeholder: String
    @Binding var text: String
    let isSending: Bool
    let canSend: Bool
    let showsAttachmentButton: Bool
    var enabledAttachmentActions: Set<ChatComposerAttachmentAction> = ChatComposerAttachmentAction.squadChatActions
    @Binding var pendingAttachments: [ChatPendingComposerAttachment]
    @Binding var attachmentLimitAlertMessage: String?
    let onSend: () -> Void
    var onCreateEvent: (() -> Void)? = nil
    /// Wired to the parent text field so callers can focus programmatically (e.g. after choosing Reply).
    var composerFocused: FocusState<Bool>.Binding
    var replyToAuthorName: String?
    var replyToSnippet: String?
    var replyToAvatarURL: String?
    var onCancelReply: (() -> Void)?
    var onTapReplyTo: (() -> Void)?
    var isEditingMessage: Bool
    var editingPreviewText: String?
    var onCancelEditing: (() -> Void)?
    var klipyCustomerId: String = ""

    @EnvironmentObject private var locationService: LocationService

    @State private var showKlipyPicker = false

    private var sendIconName: String {
        if isSending { return "hourglass" }
        if isEditingMessage { return "checkmark.circle.fill" }
        return "arrow.up.circle.fill"
    }

    private var sortedAttachmentActions: [ChatComposerAttachmentAction] {
        ChatComposerAttachmentAction.allCases.filter { enabledAttachmentActions.contains($0) }
    }

    @State private var isAttachmentTrayVisible = false
    @State private var showPhotoPicker = false
    @State private var showVideoPicker = false
    @State private var photoPickerSelection: [PhotosPickerItem] = []
    @State private var videoPickerSelection: [PhotosPickerItem] = []
    @State private var showLocationPrimer = false
    @State private var showLocationDeniedModal = false
    @State private var isLoadingLocationAttachment = false
    @State private var pendingLocationRequestAfterAuth = false
    @State private var composerFieldGeneration = 0

    var body: some View {
        composerStack
            .animation(.easeOut(duration: 0.25), value: pendingAttachments.count)
            .animation(.easeOut(duration: 0.2), value: isEditingMessage)
            .animation(.easeOut(duration: 0.2), value: isAttachmentTrayVisible)
            .modifier(ChatComposerMediaPickerModifier(
                showPhotoPicker: $showPhotoPicker,
                showVideoPicker: $showVideoPicker,
                photoPickerSelection: $photoPickerSelection,
                videoPickerSelection: $videoPickerSelection,
                onPhotoSelection: { handleMediaPickerSelection($0, expectedKind: .photo) },
                onVideoSelection: { handleMediaPickerSelection($0, expectedKind: .video) }
            ))
            .background(Color.black.opacity(0.96))
            .overlay(alignment: .top) {
                if replyToAuthorName == nil {
                    Rectangle()
                        .fill(Color.white.opacity(0.08))
                        .frame(height: 1)
                }
            }
            .overlay {
                ChatComposerLocationPermissionOverlay(
                    showLocationPrimer: $showLocationPrimer,
                    showLocationDeniedModal: $showLocationDeniedModal,
                    pendingLocationRequestAfterAuth: $pendingLocationRequestAfterAuth,
                    isLoadingLocationAttachment: $isLoadingLocationAttachment,
                    locationService: locationService,
                    onAuthorized: beginLocationAttachmentIfAuthorized
                )
            }
            .onChange(of: showsAttachmentButton) { _, shows in
                if !shows {
                    dismissAttachmentTray()
                }
            }
            .onChange(of: pendingAttachments.first?.id) { old, new in
                guard let new, new != old else { return }
                focusComposerInput()
            }
            .onDisappear {
                dismissAttachmentTray()
            }
            .sheet(isPresented: $showKlipyPicker) {
                KlipyGifPickerSheet(
                    customerId: klipyCustomerId,
                    onSelect: { selection, searchQuery in
                        showKlipyPicker = false
                        applyKlipySelection(selection, searchQuery: searchQuery)
                    },
                    onCancel: {
                        showKlipyPicker = false
                    }
                )
            }
    }

    private var composerStack: some View {
        VStack(spacing: 0) {
            if let author = replyToAuthorName,
               let snippet = replyToSnippet,
               let onCancel = onCancelReply {
                ChatComposerReplyStrip(
                    author: author,
                    snippet: snippet,
                    avatarURL: replyToAvatarURL,
                    onCancel: onCancel,
                    onTapReplyTo: onTapReplyTo
                )
                .equatable()
            }

            if isEditingMessage, let onCancelEdit = onCancelEditing {
                ChatComposerEditStrip(
                    preview: editingPreviewText ?? "",
                    onCancel: onCancelEdit
                )
                .transition(.opacity.combined(with: .move(edge: .top)))
            }

            composerAttachmentStrip

            if isAttachmentTrayVisible {
                ChatComposerAttachmentTrayBar(
                    actions: sortedAttachmentActions,
                    isLoadingLocation: isLoadingLocationAttachment,
                    onAction: handleAttachmentAction
                )
                .transition(.move(edge: .bottom).combined(with: .opacity))
            }

            HStack(spacing: 10) {
                if showsAttachmentButton {
                    attachmentControls
                }

                composerTextField

                Button(action: {
                    onSend()
                    preserveComposerFocusAfterSend()
                }) {
                    Image(systemName: sendIconName)
                        .font(.title2)
                        .foregroundStyle(canSend ? Color.purple : Color.white.opacity(0.35))
                }
                .buttonStyle(.plain)
                .disabled(!canSend)
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 10)
        }
    }

    @ViewBuilder
    private var composerAttachmentStrip: some View {
        if !pendingAttachments.isEmpty {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 12) {
                    ForEach(pendingAttachments) { attachment in
                        ChatComposerAttachmentChip(
                            attachment: attachment,
                            onRemove: {
                                pendingAttachments.removeAll { $0.id == attachment.id }
                                if pendingAttachments.isEmpty {
                                    photoPickerSelection = []
                                    videoPickerSelection = []
                                }
                            }
                        )
                    }
                }
                .padding(.horizontal, 14)
            }
            .padding(.top, replyToAuthorName == nil ? 8 : 6)
            .padding(.bottom, 8)
            .transition(.move(edge: .bottom).combined(with: .opacity))
        }
    }

    private var composerTextField: some View {
        TextField(placeholder, text: $text, axis: .vertical)
            .id(composerFieldGeneration)
            .focused(composerFocused)
            .lineLimit(1...4)
            .keyboardType(.default)
            .textContentType(nil)
            .textInputAutocapitalization(.sentences)
            .foregroundStyle(.white)
            .padding(.vertical, 8)
            .padding(.horizontal, 6)
            .background(
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .fill(Color.white.opacity(0.04))
            )
            .overlay(
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .stroke(
                        isEditingMessage ? Color.purple.opacity(0.55) : Color.clear,
                        lineWidth: 1.5
                    )
            )
            .onChange(of: text) { old, new in
                guard new.isEmpty, !old.isEmpty else { return }
                handleComposerDraftCleared()
            }
    }

    @ViewBuilder
    private var attachmentControls: some View {
        Button {
            toggleAttachmentTray()
        } label: {
            Image(systemName: isAttachmentTrayVisible ? "xmark" : "plus")
                .font(.system(size: isAttachmentTrayVisible ? 20 : 26, weight: .medium))
                .foregroundStyle(.purple)
                .frame(width: 32, height: 32)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(
            isAttachmentTrayVisible
                ? String(localized: "chat_composer_close_attachment_menu")
                : String(localized: "chat_composer_open_attachment_menu")
        )
    }

    private func toggleAttachmentTray() {
        withAnimation(.easeOut(duration: 0.2)) {
            isAttachmentTrayVisible.toggle()
        }
    }

    private func dismissAttachmentTray() {
        isAttachmentTrayVisible = false
    }

    private func focusComposerInput() {
        Task { @MainActor in
            // Defer until pickers/sheets finish dismissing so focus sticks.
            try? await Task.sleep(for: .milliseconds(100))
            composerFocused.wrappedValue = true
        }
    }

    private func preserveComposerFocusAfterSend() {
        Task { @MainActor in
            // Defer so focus wins over the send button resigning first responder.
            try? await Task.sleep(for: .milliseconds(50))
            composerFocused.wrappedValue = true
        }
    }

    private func handleComposerDraftCleared() {
        DispatchQueue.main.async {
            ChatComposerFieldFocusHelper.syncNativeComposerText("")
            preserveComposerFocusAfterSend()
            if ChatComposerFieldFocusHelper.nativeComposerHasNonEmptyText() {
                composerFieldGeneration += 1
                preserveComposerFocusAfterSend()
            }
        }
    }

    private func handleAttachmentAction(_ action: ChatComposerAttachmentAction) {
        dismissAttachmentTray()
        switch action {
        case .photo:
            showPhotoPicker = true
        case .gif:
            guard KlipyConfiguration.isConfigured else {
                attachmentLimitAlertMessage = String(localized: "klipy_picker_unavailable")
                return
            }
            showKlipyPicker = true
        case .video:
            showVideoPicker = true
        case .location:
            beginLocationAttachmentFlow()
        case .createEvent:
            onCreateEvent?()
        }
    }

    private func beginLocationAttachmentFlow() {
        switch locationService.authorizationStatus {
        case .notDetermined:
            showLocationPrimer = true
        case .denied, .restricted:
            showLocationDeniedModal = true
        default:
            beginLocationAttachmentIfAuthorized()
        }
    }

    private func beginLocationAttachmentIfAuthorized() {
        switch locationService.authorizationStatus {
        case .authorizedWhenInUse, .authorizedAlways:
            break
        case .denied, .restricted:
            showLocationDeniedModal = true
            return
        case .notDetermined:
            showLocationPrimer = true
            return
        @unknown default:
            showLocationDeniedModal = true
            return
        }

        guard !isLoadingLocationAttachment else { return }
        isLoadingLocationAttachment = true

        Task {
            do {
                let loaded = try await ChatComposerLocationAttachmentLoader.buildPendingAttachment(
                    locationService: locationService
                )
                await MainActor.run {
                    isLoadingLocationAttachment = false
                    pendingAttachments = [loaded]
                }
            } catch {
                await MainActor.run {
                    isLoadingLocationAttachment = false
                    pendingAttachments = []
                    if let loaderError = error as? ChatComposerLocationAttachmentLoader.Error,
                       loaderError == .permissionDenied {
                        showLocationDeniedModal = true
                    } else {
                        attachmentLimitAlertMessage = error.localizedDescription
                    }
                }
            }
        }
    }

    private func applyKlipySelection(_ selection: KlipyGifSelection, searchQuery: String?) {
        pendingAttachments = [
            ChatPendingComposerAttachment(
                kind: .klipyGif(selection),
                klipySearchQuery: searchQuery
            )
        ]
        focusComposerInput()
    }

    private func handleMediaPickerSelection(
        _ items: [PhotosPickerItem],
        expectedKind: ChatPendingComposerAttachment.MediaKind
    ) {
        guard let item = items.first else {
            pendingAttachments = []
            return
        }

        let attachmentID = UUID()
        pendingAttachments = [
            ChatPendingComposerAttachment(
                id: attachmentID,
                kind: .media(expectedKind),
                pickerItem: item
            )
        ]

        Task {
            do {
                let loaded = try await ChatPickerPreviewLoader.loadAttachment(from: item)
                await MainActor.run {
                    guard pendingAttachments.contains(where: { $0.id == attachmentID }) else { return }
                    pendingAttachments = [
                        ChatPendingComposerAttachment(
                            id: attachmentID,
                            kind: .media(loaded.kind),
                            previewImage: loaded.previewImage,
                            pickerItem: item
                        )
                    ]
                }
            } catch {
                await MainActor.run {
                    pendingAttachments = []
                    photoPickerSelection = []
                    videoPickerSelection = []
                    attachmentLimitAlertMessage = error.localizedDescription
                }
            }
        }
    }
}

private struct ChatComposerMediaPickerModifier: ViewModifier {
    @Binding var showPhotoPicker: Bool
    @Binding var showVideoPicker: Bool
    @Binding var photoPickerSelection: [PhotosPickerItem]
    @Binding var videoPickerSelection: [PhotosPickerItem]
    let onPhotoSelection: ([PhotosPickerItem]) -> Void
    let onVideoSelection: ([PhotosPickerItem]) -> Void

    func body(content: Content) -> some View {
        content
            .photosPicker(
                isPresented: $showPhotoPicker,
                selection: $photoPickerSelection,
                maxSelectionCount: 1,
                matching: .images,
                photoLibrary: .shared()
            )
            .photosPicker(
                isPresented: $showVideoPicker,
                selection: $videoPickerSelection,
                maxSelectionCount: 1,
                matching: .videos,
                photoLibrary: .shared()
            )
            .onChange(of: photoPickerSelection) { _, items in
                onPhotoSelection(items)
            }
            .onChange(of: videoPickerSelection) { _, items in
                onVideoSelection(items)
            }
    }
}

private struct ChatComposerLocationPermissionOverlay: View {
    @Binding var showLocationPrimer: Bool
    @Binding var showLocationDeniedModal: Bool
    @Binding var pendingLocationRequestAfterAuth: Bool
    @Binding var isLoadingLocationAttachment: Bool
    let locationService: LocationService
    let onAuthorized: () -> Void

    var body: some View {
        Group {
            if showLocationPrimer {
                OttoEducationDialog(
                    allowsUnconfirmedDismiss: false,
                    onDismissUnconfirmed: {},
                    hero: { OttoEducationLocationHero() },
                    title: String(localized: "chat_composer_location_primer_title"),
                    bodyText: String(localized: "chat_composer_location_primer_body"),
                    bulletSectionTitle: nil,
                    bullets: [],
                    footer: String(localized: "chat_composer_location_primer_footer"),
                    primaryTitle: String(localized: "chat_composer_location_primer_continue"),
                    onPrimary: {
                        showLocationPrimer = false
                        pendingLocationRequestAfterAuth = true
                        locationService.requestPermissionIfNeeded()
                    },
                    secondaryTitle: String(localized: "map_location_primer_not_now")
                )
                .transition(.opacity.combined(with: .scale(scale: 0.96)))
            }
            if showLocationDeniedModal {
                OttoEducationDialog(
                    onDismissUnconfirmed: {
                        withAnimation(.easeInOut(duration: 0.18)) {
                            showLocationDeniedModal = false
                        }
                    },
                    hero: { OttoEducationLocationHero() },
                    title: String(localized: "location_permission_map_modal_title"),
                    bodyText: String(localized: "location_permission_map_modal_body"),
                    bulletSectionTitle: nil,
                    bullets: [],
                    footer: nil,
                    primaryTitle: String(localized: "location_permission_enable"),
                    onPrimary: {
                        if let url = URL(string: UIApplication.openSettingsURLString) {
                            UIApplication.shared.open(url)
                        }
                        showLocationDeniedModal = false
                    },
                    secondaryTitle: String(localized: "location_permission_modal_dismiss")
                )
                .transition(.opacity.combined(with: .scale(scale: 0.96)))
            }
        }
        .onChange(of: locationService.authorizationStatus) { _, status in
            guard pendingLocationRequestAfterAuth else { return }
            switch status {
            case .authorizedWhenInUse, .authorizedAlways:
                pendingLocationRequestAfterAuth = false
                onAuthorized()
            case .denied, .restricted:
                pendingLocationRequestAfterAuth = false
                isLoadingLocationAttachment = false
                showLocationDeniedModal = true
            default:
                break
            }
        }
    }
}

private struct ChatComposerKlipyPreview: View {
    let url: URL

    var body: some View {
        ChatFeedAnimatedImage(
            url: url,
            width: 88,
            displayHeight: 88,
            imageCacheKeyPrefix: nil,
            onImageDecoded: nil
        )
    }
}

private struct ChatComposerAttachmentChip: View {
    let attachment: ChatPendingComposerAttachment
    let onRemove: () -> Void

    private var placeholderIcon: String {
        switch attachment.kind {
        case .media(.video): return "video"
        case .media(.photo): return "photo"
        case .klipyGif: return "face.smiling"
        case .place: return "mappin.circle.fill"
        case .event: return "calendar"
        }
    }

    private var badgeLabel: String? {
        switch attachment.kind {
        case .media(.video):
            return String(localized: "chat_composer_attachment_video")
        case .place:
            return String(localized: "chat_composer_attachment_location")
        case .event(_, let eventName):
            return eventName
        case .klipyGif:
            return String(localized: "chat_composer_attachment_gif")
        case .media(.photo):
            return nil
        }
    }

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            ZStack {
                if case .klipyGif(let selection) = attachment.kind {
                    ChatComposerKlipyPreview(url: selection.previewURL)
                } else if let preview = attachment.previewImage {
                    Image(uiImage: preview)
                        .resizable()
                        .scaledToFill()
                } else {
                    Color.white.opacity(0.08)
                        .overlay {
                            Image(systemName: placeholderIcon)
                                .font(.title2)
                                .foregroundStyle(.white.opacity(0.45))
                        }
                }
            }
            .frame(width: 88, height: 88)
            .clipped()
            .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .stroke(Color.white.opacity(0.14), lineWidth: 1)
            )
            .overlay(alignment: .bottomLeading) {
                if let badgeLabel, !badgeLabel.isEmpty {
                    Text(badgeLabel)
                        .font(.caption2.weight(.semibold))
                        .foregroundStyle(.white)
                        .lineLimit(1)
                        .padding(.horizontal, 6)
                        .padding(.vertical, 3)
                        .background(Color.black.opacity(0.55), in: Capsule())
                        .padding(6)
                }
            }

            Button(action: onRemove) {
                Image(systemName: "xmark.circle.fill")
                    .font(.title2)
                    .foregroundStyle(.white.opacity(0.48))
            }
            .buttonStyle(.plain)
        }
    }
}
