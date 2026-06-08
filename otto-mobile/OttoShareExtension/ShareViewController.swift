import UIKit
import UniformTypeIdentifiers

private struct SharedSquad {
    let id: String
    let name: String
    let subtitle: String
    let photoUrl: String?
    let icon: String?
}

private final class SquadDestinationCell: UITableViewCell {
    private static let imageCache = NSCache<NSURL, UIImage>()

    private let cardView = UIView()
    private let avatarView = UIImageView()
    private let titleLabel = UILabel()
    private let subtitleLabel = UILabel()
    private let selectionView = UIImageView()
    private var representedPhotoURL: URL?

    override init(style: UITableViewCell.CellStyle, reuseIdentifier: String?) {
        super.init(style: style, reuseIdentifier: reuseIdentifier)
        configureUI()
    }

    required init?(coder: NSCoder) {
        super.init(coder: coder)
        configureUI()
    }

    override func prepareForReuse() {
        super.prepareForReuse()
        representedPhotoURL = nil
        avatarView.image = nil
    }

    func configure(with squad: SharedSquad, isSelected: Bool) {
        titleLabel.text = squad.name
        subtitleLabel.text = squad.subtitle

        cardView.backgroundColor = UIColor.white.withAlphaComponent(isSelected ? 0.075 : 0.045)
        cardView.layer.borderWidth = isSelected ? 1 : 0
        cardView.layer.borderColor = UIColor.systemPurple.withAlphaComponent(0.95).cgColor

        selectionView.image = UIImage(systemName: isSelected ? "checkmark.circle.fill" : "circle")
        selectionView.tintColor = isSelected ? UIColor.systemGreen : UIColor.white.withAlphaComponent(0.28)

        configureAvatar(icon: squad.icon)
        if let url = Self.imageFetchURL(from: squad.photoUrl) {
            loadAvatar(from: url)
        }
    }

    private func configureUI() {
        backgroundColor = .clear
        contentView.backgroundColor = .clear
        selectionStyle = .none

        cardView.translatesAutoresizingMaskIntoConstraints = false
        cardView.layer.cornerRadius = 14
        cardView.layer.masksToBounds = true
        contentView.addSubview(cardView)

        avatarView.translatesAutoresizingMaskIntoConstraints = false
        avatarView.contentMode = .scaleAspectFill
        avatarView.clipsToBounds = true
        avatarView.layer.cornerRadius = 24
        avatarView.backgroundColor = UIColor.systemPurple.withAlphaComponent(0.28)

        titleLabel.font = .systemFont(ofSize: 17, weight: .semibold)
        titleLabel.textColor = .white
        titleLabel.lineBreakMode = .byTruncatingTail

        subtitleLabel.font = .systemFont(ofSize: 14, weight: .regular)
        subtitleLabel.textColor = UIColor.white.withAlphaComponent(0.62)
        subtitleLabel.lineBreakMode = .byTruncatingTail

        let textStack = UIStackView(arrangedSubviews: [titleLabel, subtitleLabel])
        textStack.axis = .vertical
        textStack.spacing = 3

        selectionView.translatesAutoresizingMaskIntoConstraints = false
        selectionView.contentMode = .scaleAspectFit

        let row = UIStackView(arrangedSubviews: [avatarView, textStack, UIView(), selectionView])
        row.axis = .horizontal
        row.alignment = .center
        row.spacing = 12
        row.translatesAutoresizingMaskIntoConstraints = false
        cardView.addSubview(row)

        NSLayoutConstraint.activate([
            cardView.leadingAnchor.constraint(equalTo: contentView.leadingAnchor),
            cardView.trailingAnchor.constraint(equalTo: contentView.trailingAnchor),
            cardView.topAnchor.constraint(equalTo: contentView.topAnchor, constant: 4),
            cardView.bottomAnchor.constraint(equalTo: contentView.bottomAnchor, constant: -4),

            row.leadingAnchor.constraint(equalTo: cardView.leadingAnchor, constant: 12),
            row.trailingAnchor.constraint(equalTo: cardView.trailingAnchor, constant: -12),
            row.topAnchor.constraint(equalTo: cardView.topAnchor, constant: 12),
            row.bottomAnchor.constraint(equalTo: cardView.bottomAnchor, constant: -12),

            avatarView.widthAnchor.constraint(equalToConstant: 48),
            avatarView.heightAnchor.constraint(equalToConstant: 48),
            selectionView.widthAnchor.constraint(equalToConstant: 24),
            selectionView.heightAnchor.constraint(equalToConstant: 24),
        ])
    }

    private func configureAvatar(icon: String?) {
        let symbolName = icon.flatMap { UIImage(systemName: $0) == nil ? nil : $0 } ?? "person.3.fill"
        let configuration = UIImage.SymbolConfiguration(pointSize: 19, weight: .semibold)
        avatarView.image = UIImage(systemName: symbolName, withConfiguration: configuration)
        avatarView.tintColor = .white
        avatarView.contentMode = .center
    }

    private func loadAvatar(from url: URL) {
        representedPhotoURL = url
        if let cachedImage = Self.imageCache.object(forKey: url as NSURL) {
            avatarView.contentMode = .scaleAspectFill
            avatarView.image = cachedImage
            return
        }

        URLSession.shared.dataTask(with: url) { [weak self] data, _, _ in
            guard let data, let image = UIImage(data: data) else { return }
            Self.imageCache.setObject(image, forKey: url as NSURL)
            DispatchQueue.main.async {
                guard self?.representedPhotoURL == url else { return }
                self?.avatarView.contentMode = .scaleAspectFill
                self?.avatarView.image = image
            }
        }.resume()
    }

    private static var baseURL: URL {
        #if targetEnvironment(simulator)
        URL(string: "http://localhost:4000")!
        #else
        URL(string: "https://api.ottomot.to")!
        #endif
    }

    private static func imageFetchURL(from raw: String?) -> URL? {
        guard let raw = raw?.trimmingCharacters(in: .whitespacesAndNewlines), !raw.isEmpty else { return nil }
        if raw.hasPrefix("//") {
            return URL(string: "https:\(raw)")
        }
        if raw.hasPrefix("/") {
            var components = URLComponents()
            components.scheme = baseURL.scheme
            components.host = baseURL.host
            components.port = baseURL.port
            components.path = raw
            return components.url
        }
        if let url = URL(string: raw), let scheme = url.scheme, scheme == "http" || scheme == "https" {
            let host = url.host?.lowercased()
            if host == "localhost" || host == "127.0.0.1" || host == "::1" {
                guard var parts = URLComponents(url: url, resolvingAgainstBaseURL: false) else {
                    return url
                }
                parts.scheme = baseURL.scheme
                parts.host = baseURL.host
                parts.port = baseURL.port
                return parts.url ?? url
            }
            return url
        }
        return URL(string: raw)
    }
}

private final class GradientCTAButton: UIButton {
    private let gradientLayer = CAGradientLayer()
    private let iconView = UIImageView(image: UIImage(systemName: "paperplane.fill"))
    private let ctaTitleLabel = UILabel()

    override init(frame: CGRect) {
        super.init(frame: frame)
        configureGradient()
    }

    required init?(coder: NSCoder) {
        super.init(coder: coder)
        configureGradient()
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        gradientLayer.frame = bounds
    }

    private func configureGradient() {
        gradientLayer.colors = [
            UIColor.systemPurple.cgColor,
            UIColor.systemBlue.withAlphaComponent(0.8).cgColor,
        ]
        gradientLayer.startPoint = CGPoint(x: 0, y: 0.5)
        gradientLayer.endPoint = CGPoint(x: 1, y: 0.5)
        gradientLayer.cornerRadius = 12
        layer.insertSublayer(gradientLayer, at: 0)
        layer.cornerRadius = 12
        layer.masksToBounds = true

        iconView.tintColor = .white
        iconView.contentMode = .scaleAspectFit
        iconView.translatesAutoresizingMaskIntoConstraints = false

        ctaTitleLabel.textColor = .white
        ctaTitleLabel.font = .preferredFont(forTextStyle: .headline)
        ctaTitleLabel.adjustsFontForContentSizeCategory = true
        ctaTitleLabel.translatesAutoresizingMaskIntoConstraints = false

        let contentStack = UIStackView(arrangedSubviews: [iconView, ctaTitleLabel])
        contentStack.axis = .horizontal
        contentStack.alignment = .center
        contentStack.spacing = 10
        contentStack.isUserInteractionEnabled = false
        contentStack.translatesAutoresizingMaskIntoConstraints = false
        addSubview(contentStack)

        NSLayoutConstraint.activate([
            contentStack.centerXAnchor.constraint(equalTo: centerXAnchor),
            contentStack.centerYAnchor.constraint(equalTo: centerYAnchor),
            contentStack.leadingAnchor.constraint(greaterThanOrEqualTo: leadingAnchor, constant: 18),
            contentStack.trailingAnchor.constraint(lessThanOrEqualTo: trailingAnchor, constant: -18),
            iconView.widthAnchor.constraint(equalToConstant: 20),
            iconView.heightAnchor.constraint(equalToConstant: 20),
        ])
    }

    func setDisplayTitle(_ title: String) {
        ctaTitleLabel.text = title
    }
}

final class ShareViewController: UIViewController, UITableViewDataSource, UITableViewDelegate {
    private let appGroupID = "group.otto.otto-mobile"

    private let scrollView = UIScrollView()
    private let contentStack = UIStackView()
    private let previewCard = UIView()
    private let imagePreviewView = UIImageView()
    private let previewIconView = UIImageView()
    private let previewTitleLabel = UILabel()
    private let previewSubtitleLabel = UILabel()
    private let tableView = UITableView(frame: .zero, style: .plain)
    private let statusLabel = UILabel()
    private let bottomBar = UIView()
    private let sendButton = GradientCTAButton(type: .system)
    private var tableHeightConstraint: NSLayoutConstraint?

    private var squads: [SharedSquad] = []
    private var selectedSquadID: String?
    private var sharedURL: String?
    private var sharedText: String?
    private var sharedImageJPEGData: Data?
    private var authToken: String?

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .black
        loadSharedState()
        configureUI()
        Task { await loadSharedPayload() }
    }

    private func loadSharedState() {
        let defaults = UserDefaults(suiteName: appGroupID)
        authToken = defaults?.string(forKey: "authToken")
        if let data = defaults?.data(forKey: "cachedSquads"),
           let rawSquads = try? JSONSerialization.jsonObject(with: data) as? [[String: String]] {
            squads = rawSquads.compactMap { raw in
                guard let id = raw["id"], let name = raw["name"] else { return nil }
                return SharedSquad(
                    id: id,
                    name: name,
                    subtitle: raw["subtitle"] ?? "",
                    photoUrl: raw["photoUrl"],
                    icon: raw["icon"]
                )
            }
        }
        selectedSquadID = squads.first?.id
    }

    private func configureUI() {
        configureScrollView()
        configureContentStack()
        configureHeader()
        configurePreviewCard()
        configureDestinations()
        configureStatusLabel()
        configureBottomBar()
        updatePreview()
        updateSendState()
    }

    private func configureScrollView() {
        scrollView.translatesAutoresizingMaskIntoConstraints = false
        scrollView.alwaysBounceVertical = true
        scrollView.keyboardDismissMode = .interactive
        scrollView.contentInset.bottom = 108
        view.addSubview(scrollView)

        NSLayoutConstraint.activate([
            scrollView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            scrollView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            scrollView.topAnchor.constraint(equalTo: view.topAnchor),
            scrollView.bottomAnchor.constraint(equalTo: view.bottomAnchor),
        ])
    }

    private func configureContentStack() {
        contentStack.axis = .vertical
        contentStack.spacing = 18
        contentStack.translatesAutoresizingMaskIntoConstraints = false
        scrollView.addSubview(contentStack)

        NSLayoutConstraint.activate([
            contentStack.leadingAnchor.constraint(equalTo: scrollView.contentLayoutGuide.leadingAnchor, constant: 18),
            contentStack.trailingAnchor.constraint(equalTo: scrollView.contentLayoutGuide.trailingAnchor, constant: -18),
            contentStack.topAnchor.constraint(equalTo: scrollView.contentLayoutGuide.topAnchor, constant: 18),
            contentStack.bottomAnchor.constraint(equalTo: scrollView.contentLayoutGuide.bottomAnchor, constant: -26),
            contentStack.widthAnchor.constraint(equalTo: scrollView.frameLayoutGuide.widthAnchor, constant: -36),
        ])
    }

    private func configureHeader() {
        let iconContainer = UIView()
        iconContainer.translatesAutoresizingMaskIntoConstraints = false
        iconContainer.backgroundColor = UIColor.systemPurple.withAlphaComponent(0.25)
        iconContainer.layer.cornerRadius = 19

        let icon = UIImageView(image: UIImage(systemName: "bubble.left.fill"))
        icon.tintColor = .systemPurple
        icon.translatesAutoresizingMaskIntoConstraints = false
        iconContainer.addSubview(icon)
        NSLayoutConstraint.activate([
            iconContainer.widthAnchor.constraint(equalToConstant: 38),
            iconContainer.heightAnchor.constraint(equalToConstant: 38),
            icon.centerXAnchor.constraint(equalTo: iconContainer.centerXAnchor),
            icon.centerYAnchor.constraint(equalTo: iconContainer.centerYAnchor),
        ])

        let titleLabel = UILabel()
        titleLabel.text = "Post to Chat"
        titleLabel.font = .systemFont(ofSize: 17, weight: .bold)
        titleLabel.textColor = .white

        let subtitleLabel = UILabel()
        subtitleLabel.text = "Share into a Squad conversation"
        subtitleLabel.font = .systemFont(ofSize: 12, weight: .regular)
        subtitleLabel.textColor = UIColor.white.withAlphaComponent(0.62)

        let textStack = UIStackView(arrangedSubviews: [titleLabel, subtitleLabel])
        textStack.axis = .vertical
        textStack.spacing = 2

        let closeButton = UIButton(type: .system)
        closeButton.setImage(UIImage(systemName: "xmark"), for: .normal)
        closeButton.tintColor = UIColor.white.withAlphaComponent(0.72)
        closeButton.backgroundColor = UIColor.white.withAlphaComponent(0.08)
        closeButton.layer.cornerRadius = 16
        closeButton.translatesAutoresizingMaskIntoConstraints = false
        closeButton.addTarget(self, action: #selector(cancelTapped), for: .touchUpInside)
        NSLayoutConstraint.activate([
            closeButton.widthAnchor.constraint(equalToConstant: 32),
            closeButton.heightAnchor.constraint(equalToConstant: 32),
        ])

        let row = UIStackView(arrangedSubviews: [iconContainer, textStack, UIView(), closeButton])
        row.axis = .horizontal
        row.alignment = .center
        row.spacing = 12
        contentStack.addArrangedSubview(row)
    }

    private func configurePreviewCard() {
        previewCard.backgroundColor = UIColor.white.withAlphaComponent(0.055)
        previewCard.layer.cornerRadius = 14
        previewCard.layer.borderWidth = 1
        previewCard.layer.borderColor = UIColor.white.withAlphaComponent(0.12).cgColor
        previewCard.translatesAutoresizingMaskIntoConstraints = false

        let previewStack = UIStackView()
        previewStack.axis = .horizontal
        previewStack.alignment = .center
        previewStack.spacing = 12
        previewStack.translatesAutoresizingMaskIntoConstraints = false
        previewCard.addSubview(previewStack)

        previewIconView.tintColor = .systemPurple
        previewIconView.contentMode = .scaleAspectFit
        previewIconView.backgroundColor = UIColor.systemPurple.withAlphaComponent(0.16)
        previewIconView.layer.cornerRadius = 12
        previewIconView.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            previewIconView.widthAnchor.constraint(equalToConstant: 48),
            previewIconView.heightAnchor.constraint(equalToConstant: 48),
        ])

        imagePreviewView.isHidden = true
        imagePreviewView.contentMode = .scaleAspectFill
        imagePreviewView.clipsToBounds = true
        imagePreviewView.layer.cornerRadius = 12
        imagePreviewView.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            imagePreviewView.widthAnchor.constraint(equalToConstant: 132),
            imagePreviewView.heightAnchor.constraint(equalToConstant: 92),
        ])

        previewTitleLabel.font = .systemFont(ofSize: 17, weight: .bold)
        previewTitleLabel.textColor = .white
        previewTitleLabel.numberOfLines = 2

        previewSubtitleLabel.font = .systemFont(ofSize: 12, weight: .regular)
        previewSubtitleLabel.textColor = UIColor.white.withAlphaComponent(0.62)
        previewSubtitleLabel.numberOfLines = 2

        let textStack = UIStackView(arrangedSubviews: [previewTitleLabel, previewSubtitleLabel])
        textStack.axis = .vertical
        textStack.spacing = 6

        previewStack.addArrangedSubview(previewIconView)
        previewStack.addArrangedSubview(textStack)
        previewStack.addArrangedSubview(UIView())
        previewStack.addArrangedSubview(imagePreviewView)

        NSLayoutConstraint.activate([
            previewStack.leadingAnchor.constraint(equalTo: previewCard.leadingAnchor, constant: 14),
            previewStack.trailingAnchor.constraint(equalTo: previewCard.trailingAnchor, constant: -14),
            previewStack.topAnchor.constraint(equalTo: previewCard.topAnchor, constant: 14),
            previewStack.bottomAnchor.constraint(equalTo: previewCard.bottomAnchor, constant: -14),
        ])

        contentStack.addArrangedSubview(previewCard)
    }

    private func configureDestinations() {
        contentStack.addArrangedSubview(sectionLabel("Post To Squads"))

        tableView.dataSource = self
        tableView.delegate = self
        tableView.backgroundColor = .clear
        tableView.separatorStyle = .none
        tableView.isScrollEnabled = false
        tableView.translatesAutoresizingMaskIntoConstraints = false
        tableHeightConstraint = tableView.heightAnchor.constraint(equalToConstant: tableHeight)
        tableHeightConstraint?.isActive = true
        contentStack.addArrangedSubview(tableView)
    }

    private func configureStatusLabel() {
        statusLabel.font = .systemFont(ofSize: 13)
        statusLabel.textColor = UIColor.white.withAlphaComponent(0.65)
        statusLabel.numberOfLines = 3
        statusLabel.text = statusText
        contentStack.addArrangedSubview(statusLabel)
    }

    private func configureBottomBar() {
        bottomBar.translatesAutoresizingMaskIntoConstraints = false
        bottomBar.backgroundColor = UIColor.black.withAlphaComponent(0.96)
        view.addSubview(bottomBar)

        sendButton.setDisplayTitle("Share")
        sendButton.semanticContentAttribute = .forceLeftToRight
        sendButton.translatesAutoresizingMaskIntoConstraints = false
        sendButton.addTarget(self, action: #selector(sendTapped), for: .touchUpInside)
        bottomBar.addSubview(sendButton)

        NSLayoutConstraint.activate([
            bottomBar.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            bottomBar.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            bottomBar.bottomAnchor.constraint(equalTo: view.bottomAnchor),
            bottomBar.heightAnchor.constraint(equalToConstant: 96),
            sendButton.leadingAnchor.constraint(equalTo: bottomBar.leadingAnchor, constant: 18),
            sendButton.trailingAnchor.constraint(equalTo: bottomBar.trailingAnchor, constant: -18),
            sendButton.topAnchor.constraint(equalTo: bottomBar.topAnchor, constant: 12),
            sendButton.heightAnchor.constraint(equalToConstant: 58),
        ])
    }

    private var tableHeight: CGFloat {
        if squads.isEmpty { return 72 }
        return CGFloat(squads.count) * 92
    }

    private var statusText: String {
        if authToken?.isEmpty != false {
            return "Open Driftd first to sign in."
        }
        if squads.isEmpty {
            return "Open Driftd to load your Squads first."
        }
        return "Choose a Squad."
    }

    private func sectionLabel(_ text: String) -> UILabel {
        let label = UILabel()
        label.text = text.uppercased()
        label.font = .systemFont(ofSize: 11, weight: .bold)
        label.textColor = UIColor.white.withAlphaComponent(0.56)
        return label
    }

    private func updatePreview() {
        if let imageData = sharedImageJPEGData, let image = UIImage(data: imageData) {
            previewTitleLabel.text = "Photo"
            previewSubtitleLabel.text = "Ready to share with your Squad"
            imagePreviewView.image = image
            imagePreviewView.isHidden = false
            previewIconView.image = UIImage(systemName: "photo.fill")
            return
        }

        imagePreviewView.isHidden = true
        if let sharedURL {
            previewTitleLabel.text = "Link"
            previewSubtitleLabel.text = sharedURL
            previewIconView.image = UIImage(systemName: "link")
        } else if let sharedText, !sharedText.isEmpty {
            previewTitleLabel.text = "Text"
            previewSubtitleLabel.text = sharedText
            previewIconView.image = UIImage(systemName: "text.quote")
        } else {
            previewTitleLabel.text = "Shared Item"
            previewSubtitleLabel.text = "Choose a Squad to continue"
            previewIconView.image = UIImage(systemName: "square.and.arrow.up")
        }
    }

    private func updateSendState() {
        let canSend = authToken?.isEmpty == false && selectedSquadID != nil && (sharedImageJPEGData != nil || !shareBody.isEmpty)
        // Keep UIKit from applying its disabled foreground dimming; we control disabled appearance with alpha.
        sendButton.isEnabled = true
        sendButton.isUserInteractionEnabled = canSend
        sendButton.alpha = canSend ? 1 : 0.45
        sendButton.setDisplayTitle(sendButtonTitle)
        statusLabel.text = statusText
    }

    private var sendButtonTitle: String {
        guard let selectedSquadID,
              let squad = squads.first(where: { $0.id == selectedSquadID }) else {
            return "Share"
        }
        return "Share to \(squad.name)"
    }

    private var shareBody: String {
        (sharedURL ?? sharedText ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private func loadSharedPayload() async {
        let providers = extensionContext?.inputItems
            .compactMap { $0 as? NSExtensionItem }
            .flatMap { $0.attachments ?? [] } ?? []

        for provider in providers {
            if provider.hasItemConformingToTypeIdentifier(UTType.image.identifier),
               let loadedImage = await loadImage(from: provider) {
                sharedImageJPEGData = shareExtensionJPEGData(for: loadedImage)
                break
            }
        }

        for provider in providers {
            if provider.hasItemConformingToTypeIdentifier(UTType.url.identifier),
               let item = try? await provider.loadItem(forTypeIdentifier: UTType.url.identifier),
               let url = item as? URL {
                sharedURL = url.absoluteString
                break
            }
        }

        for provider in providers where sharedText == nil {
            if provider.hasItemConformingToTypeIdentifier(UTType.plainText.identifier),
               let item = try? await provider.loadItem(forTypeIdentifier: UTType.plainText.identifier) {
                sharedText = (item as? String) ?? (item as? URL)?.absoluteString
            }
        }

        await MainActor.run {
            updatePreview()
            updateSendState()
        }
    }

    private func loadImage(from provider: NSItemProvider) async -> UIImage? {
        if let item = try? await provider.loadItem(forTypeIdentifier: UTType.image.identifier) {
            if let image = item as? UIImage {
                return image
            }
            if let data = item as? Data, let image = UIImage(data: data) {
                return image
            }
            if let url = item as? URL, let image = imageFromFileURL(url) {
                return image
            }
            if let url = item as? NSURL, let image = imageFromFileURL(url as URL) {
                return image
            }
        }
        if let data = try? await provider.loadDataRepresentation(forTypeIdentifier: UTType.image.identifier),
           let image = UIImage(data: data) {
            return image
        }
        return nil
    }

    private func shareExtensionJPEGData(for image: UIImage) -> Data? {
        let normalized = shareExtensionOrientationNormalized(image)
        let pixelSize = shareExtensionPixelSize(for: normalized)
        let longest = max(pixelSize.width, pixelSize.height)
        let maxPixelDimension: CGFloat = 1600
        let output: UIImage
        if longest > maxPixelDimension {
            let scale = maxPixelDimension / longest
            let targetSize = CGSize(
                width: max(1, floor(pixelSize.width * scale)),
                height: max(1, floor(pixelSize.height * scale))
            )
            let format = UIGraphicsImageRendererFormat.default()
            format.scale = 1
            format.opaque = true
            output = UIGraphicsImageRenderer(size: targetSize, format: format).image { _ in
                normalized.draw(in: CGRect(origin: .zero, size: targetSize))
            }
        } else {
            output = normalized
        }
        return output.jpegData(compressionQuality: 0.84)
    }

    private func shareExtensionOrientationNormalized(_ image: UIImage) -> UIImage {
        guard image.imageOrientation != .up else { return image }
        let format = UIGraphicsImageRendererFormat()
        format.scale = image.scale
        format.opaque = false
        let renderer = UIGraphicsImageRenderer(size: image.size, format: format)
        return renderer.image { _ in
            image.draw(in: CGRect(origin: .zero, size: image.size))
        }
    }

    private func shareExtensionPixelSize(for image: UIImage) -> CGSize {
        if let cgImage = image.cgImage {
            return CGSize(width: cgImage.width, height: cgImage.height)
        }
        return CGSize(width: image.size.width * image.scale, height: image.size.height * image.scale)
    }

    private func imageFromFileURL(_ url: URL) -> UIImage? {
        let didStartAccessing = url.startAccessingSecurityScopedResource()
        defer {
            if didStartAccessing {
                url.stopAccessingSecurityScopedResource()
            }
        }
        guard let data = try? Data(contentsOf: url) else { return nil }
        return UIImage(data: data)
    }

    @objc private func sendTapped() {
        guard let token = authToken, let circleId = selectedSquadID else { return }
        let body = shareBody
        let photoJPEGData = sharedImageJPEGData
        guard !body.isEmpty || photoJPEGData != nil else { return }
        statusLabel.text = "Sending..."
        sendButton.isEnabled = false

        Task {
            do {
                try await sendMessage(token: token, circleId: circleId, body: body, photoJPEGData: photoJPEGData)
                await MainActor.run {
                    extensionContext?.completeRequest(returningItems: [], completionHandler: nil)
                }
            } catch {
                await MainActor.run {
                    statusLabel.text = "Could not send. Open Driftd and try again."
                    updateSendState()
                }
            }
        }
    }

    @objc private func cancelTapped() {
        extensionContext?.cancelRequest(withError: NSError(domain: "OttoShareExtension", code: 1))
    }

    private func sendMessage(token: String, circleId: String, body: String, photoJPEGData: Data?) async throws {
        let baseURL: URL
        #if targetEnvironment(simulator)
        baseURL = URL(string: "http://localhost:4000")!
        #else
        baseURL = URL(string: "https://api.ottomot.to")!
        #endif
        var url = baseURL.appending(path: "/api/chat/circles/\(circleId)/messages")
        url = Self.appendClientTelemetryQueryItems(to: url)
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        if let photoJPEGData {
            let boundary = "Boundary-\(UUID().uuidString)"
            request.setValue("multipart/form-data; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")
            request.httpBody = Self.buildMultipartFormBody(
                boundary: boundary,
                fields: [
                    "body": body,
                    "clientMessageId": "share-\(UUID().uuidString)"
                ],
                fileFieldName: "photo",
                fileData: photoJPEGData,
                fileName: "photo.jpg",
                mimeType: "image/jpeg"
            )
        } else {
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            request.httpBody = try JSONSerialization.data(withJSONObject: [
                "body": body,
                "clientMessageId": "share-\(UUID().uuidString)"
            ])
        }
        let (_, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse, (200...299).contains(http.statusCode) else {
            throw URLError(.badServerResponse)
        }
    }

    private static func buildMultipartFormBody(
        boundary: String,
        fields: [String: String],
        fileFieldName: String,
        fileData: Data,
        fileName: String,
        mimeType: String
    ) -> Data {
        var data = Data()
        let crlf = "\r\n"
        for (key, value) in fields {
            data.append(Data("--\(boundary)\(crlf)".utf8))
            data.append(Data("Content-Disposition: form-data; name=\"\(key)\"\(crlf)\(crlf)".utf8))
            data.append(Data(value.utf8))
            data.append(Data(crlf.utf8))
        }
        data.append(Data("--\(boundary)\(crlf)".utf8))
        data.append(Data("Content-Disposition: form-data; name=\"\(fileFieldName)\"; filename=\"\(fileName)\"\(crlf)".utf8))
        data.append(Data("Content-Type: \(mimeType)\(crlf)\(crlf)".utf8))
        data.append(fileData)
        data.append(Data(crlf.utf8))
        data.append(Data("--\(boundary)--\(crlf)".utf8))
        return data
    }

    private static func appendClientTelemetryQueryItems(to url: URL) -> URL {
        let platformKey = "app_platform"
        let versionKey = "app_version"
        guard var components = URLComponents(url: url, resolvingAgainstBaseURL: false) else {
            return url
        }
        let short =
            (Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String) ?? "0"
        let build = (Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String) ?? ""
        let version = build.isEmpty ? short : "\(short) (\(build))"
        var items = components.queryItems ?? []
        items.removeAll { $0.name == platformKey || $0.name == versionKey }
        items.append(URLQueryItem(name: platformKey, value: "ios"))
        items.append(URLQueryItem(name: versionKey, value: version))
        components.queryItems = items
        return components.url ?? url
    }

    func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        max(squads.count, 1)
    }

    func tableView(_ tableView: UITableView, heightForRowAt indexPath: IndexPath) -> CGFloat {
        squads.isEmpty ? 72 : 92
    }

    func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        if squads.isEmpty {
            return emptyDestinationCell()
        }
        let cell = tableView.dequeueReusableCell(withIdentifier: "SquadDestinationCell") as? SquadDestinationCell
            ?? SquadDestinationCell(style: .default, reuseIdentifier: "SquadDestinationCell")
        let squad = squads[indexPath.row]
        let isSelected = selectedSquadID == squad.id
        cell.configure(with: squad, isSelected: isSelected)
        return cell
    }

    private func emptyDestinationCell() -> UITableViewCell {
        let cell = UITableViewCell(style: .default, reuseIdentifier: nil)
        cell.backgroundColor = .clear
        cell.contentView.backgroundColor = UIColor.white.withAlphaComponent(0.055)
        cell.contentView.layer.cornerRadius = 14
        cell.contentView.layer.masksToBounds = true
        cell.textLabel?.text = "Open Driftd to load your Squads first."
        cell.textLabel?.font = .systemFont(ofSize: 15, weight: .regular)
        cell.textLabel?.textColor = UIColor.white.withAlphaComponent(0.62)
        cell.textLabel?.numberOfLines = 2
        cell.selectionStyle = .none
        return cell
    }

    func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        guard squads.indices.contains(indexPath.row) else { return }
        selectedSquadID = squads[indexPath.row].id
        tableView.reloadData()
        updateSendState()
    }
}

private extension NSItemProvider {
    func loadItem(forTypeIdentifier typeIdentifier: String) async throws -> NSSecureCoding? {
        try await withCheckedThrowingContinuation { continuation in
            loadItem(forTypeIdentifier: typeIdentifier, options: nil) { item, error in
                if let error {
                    continuation.resume(throwing: error)
                } else {
                    continuation.resume(returning: item)
                }
            }
        }
    }

    func loadDataRepresentation(forTypeIdentifier typeIdentifier: String) async throws -> Data {
        try await withCheckedThrowingContinuation { continuation in
            loadDataRepresentation(forTypeIdentifier: typeIdentifier) { data, error in
                if let error {
                    continuation.resume(throwing: error)
                } else if let data {
                    continuation.resume(returning: data)
                } else {
                    continuation.resume(throwing: URLError(.badServerResponse))
                }
            }
        }
    }
}
