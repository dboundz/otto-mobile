import SwiftUI

enum OttoScreenChrome {
    static let horizontalPadding: CGFloat = 22
    static let topPadding: CGFloat = 18
    static let bottomPadding: CGFloat = 24
    static let stackSpacing: CGFloat = 16
    static let accentColor = Color.purple
}

protocol OttoTabItem: CaseIterable, Hashable {
    var title: String { get }
}

struct OttoScreenHeader: View {
    let title: String
    var trailingTitle: String?
    var trailingAction: (() -> Void)?
    var actionSystemImage: String?
    var actionAccessibilityLabel: String?
    var action: (() -> Void)?

    var body: some View {
        HStack {
            Text(title)
                .font(.system(size: 28, weight: .bold, design: .rounded))
                .foregroundStyle(.white)

            Spacer()

            if let trailingTitle, let trailingAction {
                Button(trailingTitle, action: trailingAction)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.white)
                    .buttonStyle(.plain)
            } else if let actionSystemImage, let action {
                Button(action: action) {
                    OttoIconButtonLabel(systemImage: actionSystemImage)
                }
                .buttonStyle(.plain)
                .accessibilityLabel(actionAccessibilityLabel ?? actionSystemImage)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

struct OttoIconButtonLabel: View {
    let systemImage: String

    var body: some View {
        Image(systemName: systemImage)
            .font(.headline)
            .foregroundStyle(.white.opacity(0.9))
            .frame(width: 32, height: 32)
    }
}

struct OttoTabBar<Tab: OttoTabItem>: View {
    @Binding var selectedTab: Tab

    var body: some View {
        HStack(spacing: 0) {
            ForEach(Array(Tab.allCases), id: \.self) { tab in
                Button {
                    selectedTab = tab
                } label: {
                    VStack(spacing: 10) {
                        Text(tab.title)
                            .font(.caption.weight(.medium))
                            .foregroundStyle(selectedTab == tab ? OttoScreenChrome.accentColor : Color.white.opacity(0.72))
                        Rectangle()
                            .fill(selectedTab == tab ? OttoScreenChrome.accentColor : Color.clear)
                            .frame(height: 2)
                    }
                    .frame(maxWidth: .infinity)
                }
                .buttonStyle(.plain)
            }
        }
        .overlay(alignment: .bottom) {
            Rectangle()
                .fill(Color.white.opacity(0.1))
                .frame(height: 1)
        }
    }
}

enum OttoTabbedPagerMode {
    /// TabView page style — each tab may remount when off-screen.
    case paging
    /// All tabs stay mounted (no swipe — SwiftUI drag blocks nested scroll). Use `.paging` when swipe is required.
    case retainState
}

/// Tab pager. Use `.paging` for swipeable tabs (Events, squads, squad detail). When the tab bar is external
/// (e.g. squad detail `safeAreaInset`), pass `EmptyView()` for `tabBar`. `.retainState` keeps tabs mounted without swipe.
struct OttoTabbedPager<Tab: OttoTabItem, TabBar: View, Content: View>: View {
    @Binding var selectedTab: Tab
    let mode: OttoTabbedPagerMode
    @ViewBuilder var tabBar: () -> TabBar
    @ViewBuilder var content: (Tab) -> Content

    private static var orderedTabs: [Tab] { Array(Tab.allCases) }

    var body: some View {
        VStack(spacing: 0) {
            tabBar()
            switch mode {
            case .paging:
                pagingBody
            case .retainState:
                retainStateBody
            }
        }
    }

    private var pagingBody: some View {
        TabView(selection: $selectedTab) {
            ForEach(Self.orderedTabs, id: \.self) { tab in
                content(tab)
                    .tag(tab)
            }
        }
        .tabViewStyle(.page(indexDisplayMode: .never))
    }

    private var retainStateBody: some View {
        ZStack(alignment: .top) {
            ForEach(Self.orderedTabs, id: \.self) { tab in
                content(tab)
                    .opacity(selectedTab == tab ? 1 : 0)
                    .allowsHitTesting(selectedTab == tab)
                    .accessibilityHidden(selectedTab != tab)
                    .zIndex(selectedTab == tab ? 1 : 0)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

/// Two-tab segmented filter with horizontal swipe (e.g. Squad / Featured on squad detail Events).
struct OttoTwoTabSwipeFilter<Tab: Hashable, FilterBar: View, Content: View>: View {
    @Binding var selectedTab: Tab
    let tabs: [Tab]
    @ViewBuilder var filterBar: () -> FilterBar
    @ViewBuilder var content: (Tab) -> Content

    var body: some View {
        VStack(spacing: 0) {
            filterBar()
            if tabs.count == 2 {
                TabView(selection: $selectedTab) {
                    content(tabs[0]).tag(tabs[0])
                    content(tabs[1]).tag(tabs[1])
                }
                .tabViewStyle(.page(indexDisplayMode: .never))
            } else {
                content(selectedTab)
            }
        }
    }
}

struct OttoSearchBar: View {
    @Binding var text: String
    let placeholder: String
    var actionSystemImage: String = "slider.horizontal.3"
    var action: (() -> Void)?
    var showsAction: Bool = true

    init(
        text: Binding<String>,
        placeholder: String,
        actionSystemImage: String = "slider.horizontal.3",
        showsAction: Bool = true,
        action: (() -> Void)? = nil
    ) {
        self._text = text
        self.placeholder = placeholder
        self.actionSystemImage = actionSystemImage
        self.showsAction = showsAction
        self.action = action
    }

    var body: some View {
        HStack(spacing: 12) {
            HStack(spacing: 10) {
                Image(systemName: "magnifyingglass")
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(.white.opacity(0.45))
                TextField(placeholder, text: $text)
                    .textInputAutocapitalization(.words)
                    .foregroundStyle(.white)
                    .tint(OttoScreenChrome.accentColor)
            }
            .padding(.horizontal, 14)
            .frame(height: 48)
            .background(Color.white.opacity(0.045))
            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .stroke(Color.white.opacity(0.09), lineWidth: 1)
            }

            if showsAction {
                Button {
                    action?()
                } label: {
                    OttoIconButtonLabel(systemImage: actionSystemImage)
                        .frame(width: 48, height: 48)
                }
                .buttonStyle(.plain)
            }
        }
    }
}
