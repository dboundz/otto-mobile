import SDWebImageSwiftUI
import SwiftUI

struct KlipyGifPickerSheet: View {
    let customerId: String
    let onSelect: (KlipyGifSelection, String?) -> Void
    let onCancel: () -> Void

    @State private var searchText = ""
    @State private var items: [KlipyGifItem] = []
    @State private var isLoading = false
    @State private var isLoadingMore = false
    @State private var errorMessage: String?
    @State private var page = 1
    @State private var hasMore = true
    @State private var searchTask: Task<Void, Never>?

    private var locale: String {
        let code = Locale.current.region?.identifier ?? "us"
        return code.lowercased()
    }

    private let columns = [
        GridItem(.flexible(), spacing: 8),
        GridItem(.flexible(), spacing: 8),
    ]

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                searchField
                    .padding(.horizontal, 16)
                    .padding(.vertical, 10)

                content
            }
            .background(Color.black)
            .navigationTitle(String(localized: "klipy_picker_title"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(String(localized: "klipy_picker_cancel")) {
                        onCancel()
                    }
                }
            }
            .task {
                KlipyConfiguration.assertConfiguredForPicker()
                await reload()
            }
            .onChange(of: searchText) { _, _ in
                scheduleSearchReload()
            }
        }
        .presentationDetents([.large])
        .presentationDragIndicator(.visible)
    }

    private var searchField: some View {
        HStack(spacing: 8) {
            Image(systemName: "magnifyingglass")
                .foregroundStyle(.white.opacity(0.5))
            TextField(String(localized: "klipy_picker_search_placeholder"), text: $searchText)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .foregroundStyle(.white)
            if !searchText.isEmpty {
                Button {
                    searchText = ""
                } label: {
                    Image(systemName: "xmark.circle.fill")
                        .foregroundStyle(.white.opacity(0.45))
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
        .background(Color.white.opacity(0.08), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
    }

    @ViewBuilder
    private var content: some View {
        if isLoading, items.isEmpty {
            Spacer()
            ProgressView()
                .tint(.purple)
            Spacer()
        } else if let errorMessage, items.isEmpty {
            Spacer()
            Text(errorMessage)
                .font(.subheadline)
                .foregroundStyle(.white.opacity(0.75))
                .multilineTextAlignment(.center)
                .padding(.horizontal, 24)
            Button(String(localized: "klipy_picker_retry")) {
                Task { await reload() }
            }
            .buttonStyle(.borderedProminent)
            .tint(.purple)
            .padding(.top, 12)
            Spacer()
        } else if items.isEmpty {
            Spacer()
            Text(String(localized: "klipy_picker_empty"))
                .font(.subheadline)
                .foregroundStyle(.white.opacity(0.6))
            Spacer()
        } else {
            ScrollView {
                LazyVGrid(columns: columns, spacing: 8) {
                    ForEach(items) { item in
                        Button {
                            let query = searchText.trimmingCharacters(in: .whitespacesAndNewlines)
                            onSelect(item.selection, query.isEmpty ? nil : query)
                        } label: {
                            KlipyGifGridCell(item: item)
                        }
                        .buttonStyle(.plain)
                        .onAppear {
                            if item.id == items.last?.id {
                                Task { await loadMoreIfNeeded() }
                            }
                        }
                    }
                }
                .padding(.horizontal, 12)
                .padding(.bottom, 16)

                if isLoadingMore {
                    ProgressView()
                        .tint(.purple)
                        .padding(.vertical, 12)
                }
            }
        }
    }

    private func scheduleSearchReload() {
        searchTask?.cancel()
        searchTask = Task {
            try? await Task.sleep(for: .milliseconds(350))
            guard !Task.isCancelled else { return }
            await reload()
        }
    }

    @MainActor
    private func reload() async {
        isLoading = true
        errorMessage = nil
        page = 1
        hasMore = true
        defer { isLoading = false }
        do {
            let result = try await fetchPage(1)
            items = result.items
            hasMore = result.hasMore
            page = 2
        } catch {
            items = []
            errorMessage = (error as? LocalizedError)?.errorDescription ?? error.localizedDescription
        }
    }

    @MainActor
    private func loadMoreIfNeeded() async {
        guard hasMore, !isLoading, !isLoadingMore else { return }
        isLoadingMore = true
        defer { isLoadingMore = false }
        do {
            let result = try await fetchPage(page)
            items.append(contentsOf: result.items)
            hasMore = result.hasMore
            page += 1
        } catch {
            hasMore = false
        }
    }

    private func fetchPage(_ page: Int) async throws -> KlipyGifListPage {
        let query = searchText.trimmingCharacters(in: .whitespacesAndNewlines)
        let customer = customerId.isEmpty ? "otto-anonymous" : customerId
        if query.isEmpty {
            return try await KlipyAPIClient.fetchTrending(
                customerId: customer,
                locale: locale,
                page: page
            )
        }
        return try await KlipyAPIClient.search(
            query: query,
            customerId: customer,
            locale: locale,
            page: page
        )
    }
}

private struct KlipyGifGridCell: View {
    let item: KlipyGifItem

    var body: some View {
        WebImage(url: item.previewURL) { image in
            image.resizable().scaledToFill()
        } placeholder: {
            Color.white.opacity(0.08)
                .overlay {
                    ProgressView().tint(.purple)
                }
        }
        .frame(minHeight: 120)
        .frame(maxWidth: .infinity)
        .clipped()
        .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
        .aspectRatio(max(0.5, CGFloat(item.width) / CGFloat(max(item.height, 1))), contentMode: .fit)
    }
}
