import Photos
import PhotosUI
import SwiftUI
import UIKit
import UniformTypeIdentifiers

private enum GarageInsertionMark: Equatable {
    case before(String)
    case after(String)
}

private let garageCardDropHitHeight: CGFloat = 206

private func garageInvokeListMoveIfMatching(
    newOrder: [GarageCar],
    original: [GarageCar],
    move: (IndexSet, Int) -> Void
) {
    guard newOrder.count == original.count else { return }
    guard newOrder.map(\.id).sorted() == original.map(\.id).sorted() else { return }
    for from in original.indices {
        for dest in 0...original.count {
            var trial = original
            trial.move(fromOffsets: IndexSet(integer: from), toOffset: dest)
            if trial.map(\.id) == newOrder.map(\.id) {
                move(IndexSet(integer: from), dest)
                return
            }
        }
    }
}

private func garageReorderedByDrop(
    draggedId: String,
    targetId: String,
    items: [GarageCar],
    insertAfter: Bool
) -> [GarageCar]? {
    var order = items
    guard let from = order.firstIndex(where: { $0.id == draggedId }),
          let t = order.firstIndex(where: { $0.id == targetId }) else { return nil }
    if from == t { return nil }
    let item = order.remove(at: from)
    let tAfterRemove = from < t ? t - 1 : t
    let insertAt = insertAfter ? min(tAfterRemove + 1, order.count) : tAfterRemove
    order.insert(item, at: insertAt)
    return order
}

private func garageInsertionLine() -> some View {
    Capsule()
        .fill(Color.purple.opacity(0.95))
        .frame(height: 4)
        .shadow(color: .purple.opacity(0.35), radius: 4, y: 1)
        .padding(.horizontal, 4)
}

private struct GarageRowDropDelegate: DropDelegate {
    let targetCarId: String
    let cardHeight: CGFloat
    @Binding var insertionMark: GarageInsertionMark?
    let displayedCars: [GarageCar]
    let move: (IndexSet, Int) -> Void

    func validateDrop(info: DropInfo) -> Bool {
        info.hasItemsConforming(to: [UTType.plainText])
    }

    func dropUpdated(info: DropInfo) -> DropProposal? {
        guard info.hasItemsConforming(to: [UTType.plainText]) else {
            return DropProposal(operation: .forbidden)
        }
        let y = info.location.y
        if y < cardHeight / 2 {
            insertionMark = .before(targetCarId)
        } else {
            insertionMark = .after(targetCarId)
        }
        return DropProposal(operation: .move)
    }

    func dropExited(info: DropInfo) {
        switch insertionMark {
        case .before(let id) where id == targetCarId,
             .after(let id) where id == targetCarId:
            insertionMark = nil
        default:
            break
        }
    }

    func performDrop(info: DropInfo) -> Bool {
        guard let mark = insertionMark else { return false }
        guard let provider = info.itemProviders(for: [.plainText]).first else { return false }
        provider.loadObject(ofClass: NSString.self) { object, _ in
            guard let dragged = object as? String, dragged != targetCarId else { return }
            DispatchQueue.main.async {
                let insertAfter: Bool
                switch mark {
                case .before(let id):
                    guard id == targetCarId else { insertionMark = nil; return }
                    insertAfter = false
                case .after(let id):
                    guard id == targetCarId else { insertionMark = nil; return }
                    insertAfter = true
                }
                guard let newOrder = garageReorderedByDrop(
                    draggedId: dragged,
                    targetId: targetCarId,
                    items: displayedCars,
                    insertAfter: insertAfter
                ) else {
                    insertionMark = nil
                    return
                }
                garageInvokeListMoveIfMatching(
                    newOrder: newOrder,
                    original: displayedCars,
                    move: move
                )
                insertionMark = nil
            }
        }
        return true
    }
}

struct GarageScreen: View {
    var viewedUserID: String? = nil
    var viewedDisplayName: String? = nil
    /// When pushed from Profile, show a back affordance instead of relying on the root tab bar.
    var showsBackButton: Bool = false
    @EnvironmentObject private var appState: AppState
    @State private var isShowingAddCar = false
    @State private var editingCar: GarageCar?
    @State private var viewedGarageCars: [GarageCar] = []
    @State private var isLoadingViewedCars = false

    private var resolvedUserID: String {
        viewedUserID ?? appState.currentUserID
    }

    private var isReadOnly: Bool {
        !resolvedUserID.isEmpty && resolvedUserID != appState.currentUserID
    }

    private var cars: [GarageCar] {
        isReadOnly ? viewedGarageCars : appState.garageCars
    }

    private var titleText: String {
        if isReadOnly {
            let name = viewedDisplayName?.trimmingCharacters(in: .whitespacesAndNewlines)
            if let name, !name.isEmpty {
                return "\(name)'s Garage"
            }
            return "Garage"
        }
        return "Garage"
    }

    var body: some View {
        GarageMainScrollPanel(
            cars: cars,
            isReadOnly: isReadOnly,
            isLoadingViewedCars: isLoadingViewedCars,
            titleText: titleText,
            showsBackButton: showsBackButton,
            onAddCar: { isShowingAddCar = true },
            onEditCar: { editingCar = $0 },
            onRemoveCar: { car in Task { await appState.removeGarageCar(car.id) } },
            onSelectSharing: { appState.selectSharingCar($0.id) },
            onReorder: isReadOnly
                ? nil
                : { source, dest in Task { await appState.reorderGarageCars(from: source, to: dest) } }
        )
        .background(Color.black.ignoresSafeArea())
        .toolbar(.hidden, for: .navigationBar)
        .sheet(isPresented: $isShowingAddCar) {
            AddGarageCarSheet(appState: appState)
        }
        .sheet(item: $editingCar) { car in
            AddGarageCarSheet(car: car, appState: appState)
        }
        .task {
            if isReadOnly {
                await loadViewedGarageCars()
            } else {
                #if !targetEnvironment(simulator)
                appState.refreshGarageAsync()
                #endif
            }
        }
    }

    private func loadViewedGarageCars() async {
        guard !resolvedUserID.isEmpty else {
            viewedGarageCars = []
            return
        }
        isLoadingViewedCars = true
        defer { isLoadingViewedCars = false }
        do {
            let cars = try await APIClient.shared.fetchGarageCars(userId: resolvedUserID)
            viewedGarageCars = cars.map {
                GarageCar(
                    id: $0.id,
                    nickname: $0.nickname ?? "",
                    make: $0.make,
                    makeId: $0.makeId,
                    model: $0.model,
                    year: $0.year,
                    color: $0.color,
                    logoSlug: $0.logoSlug,
                    isPrimary: $0.isPrimary,
                    sortOrder: $0.sortOrder,
                    photoUrl: $0.photo?.url
                )
            }
        } catch {
            viewedGarageCars = []
        }
    }
}

/// Owns the full scroll surface + search field so unrelated `AppState` publishes do not recreate `ScrollView` (which resets scroll offset).
private struct GarageMainScrollPanel: View {
    let cars: [GarageCar]
    let isReadOnly: Bool
    let isLoadingViewedCars: Bool
    let titleText: String
    var showsBackButton: Bool = false
    let onAddCar: () -> Void
    let onEditCar: (GarageCar) -> Void
    let onRemoveCar: (GarageCar) -> Void
    let onSelectSharing: (GarageCar) -> Void
    /// Drag-to-reorder; nil when read-only. Full-card drag with drop targets.
    let onReorder: ((IndexSet, Int) -> Void)?

    @Environment(\.dismiss) private var dismiss
    @State private var searchText = ""
    @State private var garageInsertionMark: GarageInsertionMark?

    private var displayedCars: [GarageCar] {
        let trimmed = searchText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return cars }
        return cars.filter {
            $0.displayName.localizedCaseInsensitiveContains(trimmed)
                || $0.detailLine.localizedCaseInsensitiveContains(trimmed)
        }
    }

    private var canReorder: Bool {
        onReorder != nil && searchText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && cars.count > 1
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: OttoScreenChrome.stackSpacing) {
                header
                OttoSearchBar(text: $searchText, placeholder: "Search your cars, bikes & boats", showsAction: false) {
                    searchText = ""
                }
                panelContent
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, OttoScreenChrome.horizontalPadding)
            .padding(.top, OttoScreenChrome.topPadding)
            .padding(.bottom, OttoScreenChrome.bottomPadding)
        }
        .scrollDismissesKeyboard(.interactively)
        .onChange(of: canReorder) { _, enabled in
            if !enabled { garageInsertionMark = nil }
        }
    }

    @ViewBuilder
    private var header: some View {
        if showsBackButton {
            HStack(spacing: 10) {
                Button(action: { dismiss() }) {
                    Image(systemName: "chevron.left")
                        .font(.body.weight(.semibold))
                        .foregroundStyle(.white.opacity(0.9))
                        .frame(width: 32, height: 32)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Back")

                Text(titleText)
                    .font(.system(size: 28, weight: .bold, design: .rounded))
                    .foregroundStyle(.white)
                    .lineLimit(1)
                    .minimumScaleFactor(0.85)

                Spacer(minLength: 0)

                Button(action: onAddCar) {
                    OttoIconButtonLabel(systemImage: "plus")
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Add car")
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        } else if isReadOnly {
            OttoScreenHeader(title: titleText, trailingTitle: "Done", trailingAction: { dismiss() })
        } else {
            OttoScreenHeader(title: titleText, actionSystemImage: "plus") {
                onAddCar()
            }
        }
    }

    @ViewBuilder
    private var panelContent: some View {
        if isLoadingViewedCars {
            ProgressView("Loading Garage...")
                .foregroundStyle(.white.opacity(0.75))
                .frame(maxWidth: .infinity, minHeight: 360)
        } else if cars.isEmpty {
            UnifiedEmptyStateView(
                title: "No Cars Yet",
                message: isReadOnly ? "This user has not added cars yet." : "Add your first car to your garage.",
                systemImage: "car",
                actionTitle: isReadOnly ? nil : "Add Car",
                action: isReadOnly ? nil : onAddCar
            )
            .frame(minHeight: 360)
        } else if displayedCars.isEmpty {
            UnifiedEmptyStateView(
                title: "No Matches",
                message: "Try another search.",
                systemImage: "car"
            )
            .frame(minHeight: 360)
        } else if canReorder, let move = onReorder {
            VStack(alignment: .leading, spacing: 4) {
                garageReorderHint
                LazyVStack(spacing: 12) {
                    ForEach(displayedCars) { car in
                        VStack(spacing: 0) {
                            if garageInsertionMark == .before(car.id) {
                                garageInsertionLine()
                                    .padding(.bottom, 6)
                            }
                            GarageCarCard(car: car, canEdit: !isReadOnly) {
                                onEditCar(car)
                            } onDelete: {
                                onRemoveCar(car)
                            }
                            .contentShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                            .onTapGesture {
                                guard !isReadOnly else { return }
                                onSelectSharing(car)
                            }
                            .onDrop(
                                of: [.plainText],
                                delegate: GarageRowDropDelegate(
                                    targetCarId: car.id,
                                    cardHeight: garageCardDropHitHeight,
                                    insertionMark: $garageInsertionMark,
                                    displayedCars: displayedCars,
                                    move: move
                                )
                            )
                            .draggable(car.id) {
                                garageDragPreviewLabel(car: car)
                            }
                            if garageInsertionMark == .after(car.id) {
                                garageInsertionLine()
                                    .padding(.top, 6)
                            }
                        }
                    }
                }
            }
            .frame(minHeight: CGFloat(displayedCars.count) * 220)
        } else {
            GarageCarScrollList(
                displayedCars: displayedCars,
                isReadOnly: isReadOnly,
                onEdit: onEditCar,
                onDelete: onRemoveCar,
                onSelectForSharing: onSelectSharing
            )
        }
    }

    private var garageReorderHint: some View {
        Text("Long-press to re-order")
            .font(.system(size: 12, weight: .medium))
            .foregroundStyle(.white.opacity(0.62))
            .frame(maxWidth: .infinity, alignment: .trailing)
    }
}

private func garageDragPreviewLabel(car: GarageCar) -> some View {
    Text(car.displayName)
        .font(.system(size: 15, weight: .semibold, design: .rounded))
        .foregroundStyle(.white)
        .padding(.horizontal, 14)
        .padding(.vertical, 10)
        .background(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .fill(Color(white: 0.14))
                .shadow(color: .black.opacity(0.45), radius: 12, y: 4)
        )
}

/// Isolated list so unrelated `AppState` publishes (presence, chat, invites) do not rebuild the scroll stack and reset offset.
private struct GarageCarScrollList: View, Equatable {
    let displayedCars: [GarageCar]
    let isReadOnly: Bool
    let onEdit: (GarageCar) -> Void
    let onDelete: (GarageCar) -> Void
    let onSelectForSharing: (GarageCar) -> Void

    static func == (lhs: GarageCarScrollList, rhs: GarageCarScrollList) -> Bool {
        lhs.displayedCars == rhs.displayedCars && lhs.isReadOnly == rhs.isReadOnly
    }

    var body: some View {
        LazyVStack(spacing: 12) {
            ForEach(displayedCars) { car in
                GarageCarCard(car: car, canEdit: !isReadOnly) {
                    onEdit(car)
                } onDelete: {
                    onDelete(car)
                }
                .contentShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                .onTapGesture {
                    guard !isReadOnly else { return }
                    onSelectForSharing(car)
                }
            }
        }
    }
}

private enum GarageCarCardOverlayMetrics {
    static let copyHorizontalPadding: CGFloat = 10
    static let copyVerticalPadding: CGFloat = 8
    static let copyLineSpacing: CGFloat = 5
    static let copyTitleFont = UIFont.systemFont(ofSize: 19, weight: .bold)
    static let copyDetailFont = UIFont.systemFont(ofSize: 13, weight: .medium)
    static let logoBadgePadding: CGFloat = 6

    /// Matches the two-line title block in `GarageCarCard.bottomCopy`.
    static var copyBlockHeight: CGFloat {
        copyVerticalPadding * 2
            + copyTitleFont.lineHeight
            + copyLineSpacing
            + copyDetailFont.lineHeight
    }

    static var logoImageSize: CGFloat {
        copyBlockHeight - logoBadgePadding * 2
    }
}

struct GarageCarCard: View {
    let car: GarageCar
    let canEdit: Bool
    let onEdit: () -> Void
    let onDelete: () -> Void
    private let overlayInset: CGFloat = 12

    var body: some View {
        ZStack(alignment: .topLeading) {
            backdrop
                .allowsHitTesting(false)

            topControls
                .padding(overlayInset)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)

            if car.photoUrl != nil, let logoURL = car.brandLogoURL {
                GarageCarBrandLogoBadge(
                    url: logoURL,
                    containerHeight: GarageCarCardOverlayMetrics.copyBlockHeight
                )
                    .padding(overlayInset)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomTrailing)
            }

            bottomCopy
                .padding(overlayInset)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomLeading)
        }
        .frame(maxWidth: .infinity)
        .frame(height: 206)
        /// Large decoded photos can report huge intrinsic sizes; clip before corner radius so layout can’t spill past the card.
        .clipped()
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .stroke(Color.white.opacity(0.11), lineWidth: 1)
        }
    }

    private var topControls: some View {
        HStack(alignment: .top) {
            if car.isPrimary {
                Label("Featured", systemImage: "star")
                    .font(.system(size: 10, weight: .bold))
                    .foregroundStyle(.white)
                    .padding(.horizontal, 9)
                    .padding(.vertical, 6)
                    .background(Color.black.opacity(0.225))
                    .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
            }

            Spacer()

            if canEdit {
                Menu {
                    Button(action: onEdit) {
                        Label("Edit", systemImage: "pencil")
                    }
                    Button(role: .destructive, action: onDelete) {
                        Label("Remove", systemImage: "trash")
                    }
                } label: {
                    Image(systemName: "ellipsis")
                        .font(.system(size: 15, weight: .bold))
                        .foregroundStyle(.white)
                        .frame(width: 32, height: 32)
                        .background(Color.black.opacity(0.58))
                        .clipShape(RoundedRectangle(cornerRadius: 9, style: .continuous))
                }
            }
        }
    }

    private var bottomCopy: some View {
        VStack(alignment: .leading, spacing: 5) {
            Text(car.displayName)
                .font(.system(size: 19, weight: .bold, design: .rounded))
                .foregroundStyle(.white)
                .lineLimit(1)
                .minimumScaleFactor(0.85)

            Text(car.detailLine)
                .font(.system(size: 13, weight: .medium))
                .foregroundStyle(.white.opacity(0.9))
                .lineLimit(1)
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 8)
        .background(Color.black.opacity(0.225))
        .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
        .shadow(color: .black.opacity(0.55), radius: 0, x: 0, y: 1)
        .shadow(color: .black.opacity(0.65), radius: 8, x: 0, y: 2)
    }

    @ViewBuilder
    private var backdrop: some View {
        Group {
            if let photoUrl = car.photoUrl, let url = APIConfig.imageFetchURL(from: photoUrl) {
                CachedAsyncImage(url: url) { phase in
                    switch phase {
                    case .success(let image):
                        image
                            .resizable()
                            .scaledToFill()
                    case .empty:
                        GarageCarBackdrop(car: car)
                    case .failure:
                        GarageCarBackdrop(car: car)
                    }
                }
            } else {
                GarageCarBackdrop(car: car)
            }
        }
        .frame(minWidth: 0, maxWidth: .infinity, minHeight: 0, maxHeight: .infinity)
        .clipped()
    }
}

private struct GarageCarBackdrop: View {
    let car: GarageCar

    var body: some View {
        ZStack {
            LinearGradient(
                colors: gradientColors,
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            RadialGradient(
                colors: [Color.purple.opacity(0.36), .clear],
                center: .topTrailing,
                startRadius: 18,
                endRadius: 210
            )
            Image(systemName: "car.side.fill")
                .font(.system(size: 110, weight: .regular))
                .foregroundStyle(.white.opacity(0.16))
                .offset(x: 74, y: 20)

            if let logoURL = car.brandLogoURL {
                GarageCarBrandLogoBadge(url: logoURL, logoSize: 72)
                    .offset(x: -52, y: 0)
            }
        }
        .frame(minWidth: 0, maxWidth: .infinity, minHeight: 0, maxHeight: .infinity)
        .clipped()
    }

    private var gradientColors: [Color] {
        let seed = abs(car.id.hashValue) % 3
        switch seed {
        case 0:
            return [Color(red: 0.23, green: 0.05, blue: 0.32), Color(red: 0.02, green: 0.02, blue: 0.05)]
        case 1:
            return [Color(red: 0.08, green: 0.08, blue: 0.22), Color(red: 0.32, green: 0.05, blue: 0.11)]
        default:
            return [Color(red: 0.05, green: 0.14, blue: 0.20), Color(red: 0.02, green: 0.02, blue: 0.05)]
        }
    }
}

private struct CarBrandLogoPickerRow: View {
    let brand: CarBrand
    @Binding var logoSlug: String

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Brand logo")
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(.secondary)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 10) {
                    ForEach(brand.logoPickerOptions(), id: \.slug) { option in
                        Button {
                            logoSlug = option.slug
                        } label: {
                            VStack(spacing: 6) {
                                if let url = CarBrandLogoCatalog.logoURL(slug: option.slug) {
                                    CarBrandLogoThumb(url: url, size: 44)
                                }
                                Text(option.label)
                                    .font(.caption2.weight(.semibold))
                                    .foregroundStyle(.white)
                                    .lineLimit(1)
                            }
                            .padding(8)
                            .background(
                                RoundedRectangle(cornerRadius: 10, style: .continuous)
                                    .fill(logoSlug == option.slug ? Color.purple.opacity(0.35) : Color.white.opacity(0.06))
                            )
                            .overlay {
                                RoundedRectangle(cornerRadius: 10, style: .continuous)
                                    .stroke(logoSlug == option.slug ? Color.purple : Color.clear, lineWidth: 1.5)
                            }
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
        .listRowBackground(Color.white.opacity(0.06))
    }
}

struct GarageCarBrandLogoBadge: View {
    let url: URL
    var logoSize: CGFloat = 34
    var containerHeight: CGFloat? = nil

    private var resolvedContainerHeight: CGFloat {
        containerHeight ?? (logoSize + 12)
    }

    private var resolvedLogoSize: CGFloat {
        if let containerHeight {
            return max(containerHeight - 12, 20)
        }
        return logoSize
    }

    var body: some View {
        CarBrandLogoThumb(url: url, size: resolvedLogoSize)
            .padding(6)
            .frame(height: resolvedContainerHeight, alignment: .center)
            .background(Color.black.opacity(0.225))
            .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
            .shadow(color: .black.opacity(0.55), radius: 0, x: 0, y: 1)
            .shadow(color: .black.opacity(0.65), radius: 8, x: 0, y: 2)
    }
}

private struct CarBrandLogoThumb: View {
    let url: URL
    var size: CGFloat = 40

    var body: some View {
        CachedAsyncImage(url: url) { phase in
            switch phase {
            case .success(let image):
                image
                    .resizable()
                    .scaledToFit()
                    .frame(width: size, height: size)
            case .empty, .failure:
                Image(systemName: "car.fill")
                    .font(.system(size: size * 0.42, weight: .semibold))
                    .foregroundStyle(.white.opacity(0.75))
                    .frame(width: size, height: size)
                    .background(Color.white.opacity(0.12))
                    .clipShape(RoundedRectangle(cornerRadius: size * 0.18, style: .continuous))
            }
        }
    }
}

/// Full-screen list + search for makes. The `Form` + `.menu` picker reset scroll position when the form re-renders
/// (e.g. photo decode, background state) — a pushed `List` keeps a stable scroll surface.
private struct CarBrandPickerView: View {
    @Environment(\.dismiss) private var dismiss
    @Binding var selection: String
    @Binding var makeIdSelection: String
    @Binding var logoSlugSelection: String
    let car: GarageCar?

    @State private var searchText = ""

    private var brands: [CarBrand] {
        var list = CarBrandCatalog.allBrands
        if let car, !car.make.isEmpty,
           !list.contains(where: { $0.name.caseInsensitiveCompare(car.make) == .orderedSame }) {
            list.insert(CarBrand(id: "legacy-\(car.id)", name: car.make), at: 0)
        }
        return list
    }

    private var filteredBrands: [CarBrand] {
        let q = searchText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !q.isEmpty else { return brands }
        return brands.filter { $0.name.localizedCaseInsensitiveContains(q) }
    }

    var body: some View {
        List(filteredBrands) { brand in
            Button {
                selection = brand.name
                makeIdSelection = brand.id.hasPrefix("legacy-") ? "" : brand.id
                logoSlugSelection = CarBrandLogoCatalog.defaultLogoSlug(forMakeId: makeIdSelection) ?? ""
                dismiss()
            } label: {
                HStack {
                    Text(brand.name)
                        .foregroundStyle(.white)
                    Spacer()
                    if selection.caseInsensitiveCompare(brand.name) == .orderedSame {
                        Image(systemName: "checkmark")
                            .font(.body.weight(.semibold))
                            .foregroundStyle(Color.purple)
                    }
                }
            }
            .listRowBackground(Color.white.opacity(0.06))
        }
        .listStyle(.plain)
        .searchable(text: $searchText, prompt: "Search makes")
        .navigationTitle("Make")
        .navigationBarTitleDisplayMode(.inline)
        .scrollContentBackground(.hidden)
        .background(Color.black)
    }
}

struct AddGarageCarSheet: View {
    @Environment(\.dismiss) private var dismiss
    /// Pass explicitly — `EnvironmentObject` would re-render this form on every `AppState` publish (presence, chat, etc.),
    /// which resets `Form` / menu scroll (e.g. the long make list).
    let appState: AppState

    let car: GarageCar?
    @State private var photoPickerItem: PhotosPickerItem?
    @State private var selectedImageData: Data?
    @State private var garagePhotoToCrop: UIImage?
    @State private var nickname = ""
    @State private var make = ""
    @State private var makeId = ""
    @State private var logoSlug = ""
    @State private var model = ""
    @State private var year = ""
    @State private var color = ""
    @State private var isPrimary = false
    @State private var formErrorMessage: String?
    @State private var isShowingFormError = false

    private var isEditing: Bool {
        car != nil
    }

    private var trimmedNickname: String {
        nickname.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private var trimmedMake: String {
        make.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private var trimmedModel: String {
        model.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private var trimmedYear: String {
        year.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    init(car: GarageCar? = nil, appState: AppState) {
        self.appState = appState
        self.car = car
        _nickname = State(initialValue: car?.nickname ?? "")
        _make = State(initialValue: car?.make ?? "")
        _makeId = State(initialValue: car?.makeId ?? CarBrandCatalog.brand(matchingMakeName: car?.make ?? "")?.id ?? "")
        _logoSlug = State(initialValue: car?.logoSlug ?? car?.resolvedLogoSlug ?? "")
        _model = State(initialValue: car?.model ?? "")
        _year = State(initialValue: car?.year.map(String.init) ?? "")
        _color = State(initialValue: car?.color ?? "")
        _isPrimary = State(initialValue: car?.isPrimary ?? false)
    }

    @ViewBuilder
    private var carPhotoSection: some View {
        Section("Car Photo") {
            PhotosPicker(selection: $photoPickerItem, matching: .images, photoLibrary: PHPhotoLibrary.shared()) {
                ZStack {
                    if let selectedImageData, let image = UIImage(data: selectedImageData) {
                        Image(uiImage: image)
                            .resizable()
                            .scaledToFill()
                    } else if let photoUrl = car?.photoUrl, let url = APIConfig.imageFetchURL(from: photoUrl) {
                        CachedAsyncImage(url: url) { phase in
                            switch phase {
                            case .success(let image):
                                image
                                    .resizable()
                                    .scaledToFill()
                            case .empty:
                                photoPlaceholder
                            case .failure:
                                photoPlaceholder
                            }
                        }
                    } else {
                        photoPlaceholder
                    }
                }
                .frame(minWidth: 0, maxWidth: .infinity)
                .frame(height: 160)
                .clipped()
                .background(Color.white.opacity(0.06))
                .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
            }
            .buttonStyle(.plain)
        }
    }

    @ViewBuilder
    private var carInfoSection: some View {
        Section("Car Info") {
            TextField("Nickname (optional)", text: $nickname)
            NavigationLink {
                CarBrandPickerView(
                    selection: $make,
                    makeIdSelection: $makeId,
                    logoSlugSelection: $logoSlug,
                    car: car
                )
            } label: {
                HStack {
                    Text("Make")
                    Spacer()
                    Text(make.isEmpty ? "Select make" : make)
                        .foregroundStyle(make.isEmpty ? Color.secondary : Color.white)
                        .multilineTextAlignment(.trailing)
                        .lineLimit(2)
                }
            }
            TextField("Model", text: $model)
                .onChange(of: model) { _, newValue in
                    guard let suggested = CarBrandLogoCatalog.suggestedLogoSlug(makeId: makeId, model: newValue) else {
                        return
                    }
                    logoSlug = suggested
                }
            if let brand = CarBrandCatalog.brand(forMakeId: makeId), brand.hasLogoPickerOptions {
                CarBrandLogoPickerRow(brand: brand, logoSlug: $logoSlug)
            }
            TextField("Year", text: $year)
                .keyboardType(.numberPad)
            TextField("Color (optional)", text: $color)
            if isEditing {
                Toggle("Featured car", isOn: $isPrimary)
                    .disabled(car?.isPrimary == true)
            }
        }
    }

    var body: some View {
        NavigationStack {
            Form {
                carPhotoSection
                carInfoSection
            }
            .navigationTitle(isEditing ? "Edit Car" : "Add Car")
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Save") {
                        Task {
                            await saveCar()
                        }
                    }
                }
            }
            .scrollContentBackground(.hidden)
            .background(Color.black)
            .alert("Car Details Needed", isPresented: $isShowingFormError) {
                Button("OK", role: .cancel) {}
            } message: {
                Text(formErrorMessage ?? "Fill in the required car details before saving.")
            }
            .fullScreenCover(isPresented: Binding(
                get: { garagePhotoToCrop != nil },
                set: { if !$0 { garagePhotoToCrop = nil } }
            )) {
                if let image = garagePhotoToCrop {
                    OttoImageCropperSheet(
                        image: image,
                        cropAspect: 16 / 9,
                        onComplete: { jpeg in
                            garagePhotoToCrop = nil
                            selectedImageData = jpeg
                        },
                        onCancel: { garagePhotoToCrop = nil }
                    )
                }
            }
            .onChange(of: photoPickerItem) { _, newItem in
                Task {
                    guard let newItem else { return }
                    if let data = try? await newItem.loadTransferable(type: Data.self),
                       let ui = UIImage(data: data) {
                        await MainActor.run {
                            garagePhotoToCrop = ui
                            photoPickerItem = nil
                        }
                    }
                }
            }
        }
        .presentationDetents([.large])
    }

    private func validateForm() -> String? {
        var missing: [String] = []
        if trimmedMake.isEmpty { missing.append("make") }
        if trimmedModel.isEmpty { missing.append("model") }
        if trimmedYear.isEmpty {
            missing.append("year")
        } else if Int(trimmedYear) == nil {
            return "Year must be a number."
        }

        guard !missing.isEmpty else { return nil }
        if missing.count == 1 {
            return "Add a \(missing[0]) before saving."
        }
        let final = missing.removeLast()
        return "Add \(missing.joined(separator: ", ")) and \(final) before saving."
    }

    @MainActor
    private func saveCar() async {
        if let validation = validateForm() {
            formErrorMessage = validation
            isShowingFormError = true
            return
        }
        formErrorMessage = nil
        appState.errorMessage = nil

        if let car {
            await appState.updateGarageCar(
                carID: car.id,
                nickname: nickname,
                make: make,
                makeId: makeId.isEmpty ? nil : makeId,
                model: model,
                yearText: year,
                color: color,
                logoSlug: logoSlug.isEmpty ? nil : logoSlug,
                isPrimary: isPrimary,
                imageData: selectedImageData
            )
            if let error = appState.errorMessage, !error.isEmpty {
                formErrorMessage = error
                isShowingFormError = true
            } else {
                dismiss()
            }
        } else {
            let previousCount = appState.garageCars.count
            await appState.addGarageCar(
                nickname: nickname,
                make: make,
                makeId: makeId.isEmpty ? nil : makeId,
                model: model,
                yearText: year,
                color: color,
                logoSlug: logoSlug.isEmpty ? nil : logoSlug,
                imageData: selectedImageData
            )
            if appState.garageCars.count > previousCount {
                dismiss()
            } else {
                formErrorMessage = appState.errorMessage ?? "Couldn’t save car. Try again."
                isShowingFormError = true
            }
        }
    }

    private var photoPlaceholder: some View {
        VStack(spacing: 8) {
            Image(systemName: "photo.badge.plus")
                .font(.system(size: 26, weight: .semibold))
                .foregroundStyle(.white)
            Text(isEditing ? "Change Photo" : "Add Photo")
                .font(.headline)
                .foregroundStyle(.white)
            Text("We’ll crop it to fit the garage cards.")
                .font(.caption)
                .foregroundStyle(.white.opacity(0.55))
        }
    }
}
