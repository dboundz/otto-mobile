import SwiftUI
import UIKit

// MARK: - UIImage helpers

private extension UIImage {
    /// Draws the image upright so `cgImage` width/height match the pixel grid implied by `size`×`scale`.
    /// Without this, `cg.cropping(to:)` uses wrong coordinates for typical Photos/camera EXIF orientations.
    func ottoNormalizedForPixelCrop() -> UIImage {
        guard imageOrientation != .up else { return self }
        let format = UIGraphicsImageRendererFormat()
        format.scale = scale
        format.opaque = false
        let renderer = UIGraphicsImageRenderer(size: size, format: format)
        return renderer.image { _ in
            draw(in: CGRect(origin: .zero, size: size))
        }
    }
}

// MARK: - SwiftUI entry

/// Presents a zoom/pan crop UI. `cropAspect` is **width ÷ height** (1 = square, 16/9 = wide landscape).
struct OttoImageCropperSheet: View {
    let image: UIImage
    let cropAspect: CGFloat
    let onComplete: (Data) -> Void
    let onCancel: () -> Void

    var body: some View {
        OttoImageCropperRepresentable(
            image: image,
            cropAspect: cropAspect,
            onComplete: onComplete,
            onCancel: onCancel,
        )
        .ignoresSafeArea()
        .background(Color.black)
    }
}

private struct OttoImageCropperRepresentable: UIViewControllerRepresentable {
    let image: UIImage
    let cropAspect: CGFloat
    let onComplete: (Data) -> Void
    let onCancel: () -> Void

    func makeUIViewController(context: Context) -> OttoImageCropViewController {
        OttoImageCropViewController(
            image: image,
            cropAspect: cropAspect,
            onComplete: onComplete,
            onCancel: onCancel,
        )
    }

    func updateUIViewController(_ uiViewController: OttoImageCropViewController, context: Context) {}
}

// MARK: - UIKit crop controller

final class OttoImageCropViewController: UIViewController, UIScrollViewDelegate {
    private let image: UIImage
    private let cropAspect: CGFloat
    private let onComplete: (Data) -> Void
    private let onCancel: () -> Void

    private let scrollView = UIScrollView()
    private let imageView = UIImageView()
    private let dimmingLayer = CAShapeLayer()
    private let cropBorder = CAShapeLayer()

    private var cropFrameInView: CGRect = .zero
    private var hasAppliedInitialCropLayout = false

    init(
        image: UIImage,
        cropAspect: CGFloat,
        onComplete: @escaping (Data) -> Void,
        onCancel: @escaping () -> Void
    ) {
        self.image = image.ottoNormalizedForPixelCrop()
        self.cropAspect = max(0.25, cropAspect)
        self.onComplete = onComplete
        self.onCancel = onCancel
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .black

        scrollView.delegate = self
        scrollView.showsHorizontalScrollIndicator = false
        scrollView.showsVerticalScrollIndicator = false
        scrollView.decelerationRate = .fast
        scrollView.alwaysBounceVertical = true
        scrollView.alwaysBounceHorizontal = true
        view.addSubview(scrollView)

        imageView.image = image
        imageView.contentMode = .scaleAspectFit
        scrollView.addSubview(imageView)

        let dim = UIView()
        dim.isUserInteractionEnabled = false
        dim.layer.addSublayer(dimmingLayer)
        dim.layer.addSublayer(cropBorder)
        dim.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(dim)
        NSLayoutConstraint.activate([
            dim.topAnchor.constraint(equalTo: view.topAnchor),
            dim.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            dim.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            dim.bottomAnchor.constraint(equalTo: view.bottomAnchor),
        ])

        let topBar = UIView()
        topBar.backgroundColor = UIColor.black.withAlphaComponent(0.82)
        topBar.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(topBar)

        let cancel = UIButton(type: .system)
        var cancelTitle = AttributedString("Cancel")
        cancelTitle.font = .systemFont(ofSize: 17, weight: .semibold)
        var cancelConfiguration = UIButton.Configuration.filled()
        cancelConfiguration.attributedTitle = cancelTitle
        cancelConfiguration.baseForegroundColor = .white
        cancelConfiguration.baseBackgroundColor = UIColor.white.withAlphaComponent(0.14)
        cancelConfiguration.contentInsets = NSDirectionalEdgeInsets(top: 8, leading: 14, bottom: 8, trailing: 14)
        cancelConfiguration.background.cornerRadius = 14
        cancel.configuration = cancelConfiguration
        cancel.addAction(UIAction { [weak self] _ in self?.onCancel() }, for: .touchUpInside)
        cancel.translatesAutoresizingMaskIntoConstraints = false
        topBar.addSubview(cancel)

        let use = UIButton(type: .system)
        var useTitle = AttributedString("Use Photo")
        useTitle.font = .systemFont(ofSize: 17, weight: .bold)
        var useConfiguration = UIButton.Configuration.filled()
        useConfiguration.attributedTitle = useTitle
        useConfiguration.baseForegroundColor = .white
        useConfiguration.baseBackgroundColor = UIColor.systemGreen.withAlphaComponent(0.92)
        useConfiguration.contentInsets = NSDirectionalEdgeInsets(top: 8, leading: 14, bottom: 8, trailing: 14)
        useConfiguration.background.cornerRadius = 14
        use.configuration = useConfiguration
        use.addAction(UIAction { [weak self] _ in self?.cropAndFinish() }, for: .touchUpInside)
        use.translatesAutoresizingMaskIntoConstraints = false
        topBar.addSubview(use)

        NSLayoutConstraint.activate([
            topBar.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            topBar.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            topBar.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            topBar.heightAnchor.constraint(equalToConstant: 58),

            cancel.leadingAnchor.constraint(equalTo: topBar.leadingAnchor, constant: 16),
            cancel.centerYAnchor.constraint(equalTo: topBar.centerYAnchor),

            use.trailingAnchor.constraint(equalTo: topBar.trailingAnchor, constant: -16),
            use.centerYAnchor.constraint(equalTo: topBar.centerYAnchor),
        ])

        scrollView.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            scrollView.topAnchor.constraint(equalTo: view.topAnchor),
            scrollView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            scrollView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            scrollView.bottomAnchor.constraint(equalTo: view.bottomAnchor),
        ])
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        layoutCropAndZoom(preserveOffset: hasAppliedInitialCropLayout)
        hasAppliedInitialCropLayout = true
    }

    private func layoutCropAndZoom(preserveOffset: Bool) {
        let prevOffset = scrollView.contentOffset
        let prevZoom = scrollView.zoomScale

        let margin: CGFloat = 20
        let maxW = view.bounds.width - margin * 2
        let maxH = view.bounds.height * 0.64
        var cropW = maxW
        var cropH = cropW / cropAspect
        if cropH > maxH {
            cropH = maxH
            cropW = cropH * cropAspect
        }

        let originX = (view.bounds.width - cropW) / 2
        let originY = (view.bounds.height - cropH) / 2
        cropFrameInView = CGRect(x: originX, y: originY, width: cropW, height: cropH)

        // Dimming mask
        let path = UIBezierPath(rect: view.bounds)
        let hole = UIBezierPath(roundedRect: cropFrameInView, cornerRadius: 6)
        path.append(hole.reversing())
        dimmingLayer.path = path.cgPath
        dimmingLayer.fillRule = .evenOdd
        dimmingLayer.fillColor = UIColor.black.withAlphaComponent(0.52).cgColor

        cropBorder.path = UIBezierPath(roundedRect: cropFrameInView, cornerRadius: 6).cgPath
        cropBorder.strokeColor = UIColor.white.withAlphaComponent(0.92).cgColor
        cropBorder.fillColor = UIColor.clear.cgColor
        cropBorder.lineWidth = 2

        let imageSize = image.size
        guard imageSize.width > 0, imageSize.height > 0 else { return }

        imageView.frame = CGRect(origin: .zero, size: imageSize)
        scrollView.contentSize = imageSize

        let scaleW = cropW / imageSize.width
        let scaleH = cropH / imageSize.height
        let minZoom = max(scaleW, scaleH)
        let maxZoom = minZoom * 8

        scrollView.minimumZoomScale = minZoom
        scrollView.maximumZoomScale = maxZoom

        scrollView.zoomScale = preserveOffset ? ottoClamp(prevZoom, minZoom, maxZoom) : minZoom
        recenterScrollInsets()
        scrollView.contentOffset = preserveOffset ? clampedContentOffset(prevOffset) : centeredContentOffsetForCrop()
    }

    private func recenterScrollInsets() {
        let crop = cropFrameInView
        scrollView.contentInset = UIEdgeInsets(
            top: max(0, crop.minY),
            left: max(0, crop.minX),
            bottom: max(0, view.bounds.height - crop.maxY),
            right: max(0, view.bounds.width - crop.maxX)
        )
    }

    private func centeredContentOffsetForCrop() -> CGPoint {
        let scaledSize = CGSize(
            width: image.size.width * scrollView.zoomScale,
            height: image.size.height * scrollView.zoomScale
        )
        return clampedContentOffset(
            CGPoint(
                x: scaledSize.width / 2 - cropFrameInView.midX,
                y: scaledSize.height / 2 - cropFrameInView.midY
            )
        )
    }

    private func clampedContentOffset(_ offset: CGPoint) -> CGPoint {
        let scaledSize = CGSize(
            width: image.size.width * scrollView.zoomScale,
            height: image.size.height * scrollView.zoomScale
        )
        let inset = scrollView.contentInset
        let minX = -inset.left
        let minY = -inset.top
        let maxX = max(minX, scaledSize.width - scrollView.bounds.width + inset.right)
        let maxY = max(minY, scaledSize.height - scrollView.bounds.height + inset.bottom)
        return CGPoint(
            x: ottoClamp(offset.x, minX, maxX),
            y: ottoClamp(offset.y, minY, maxY)
        )
    }

    func viewForZooming(in scrollView: UIScrollView) -> UIView? {
        imageView
    }

    func scrollViewDidZoom(_ scrollView: UIScrollView) {
        recenterScrollInsets()
    }

    func scrollViewDidEndZooming(_ scrollView: UIScrollView, with view: UIView?, atScale scale: CGFloat) {
        recenterScrollInsets()
    }

    private func cropAndFinish() {
        let cropInScroll = scrollView.convert(cropFrameInView, from: view)
        let visibleInImageView = scrollView.convert(cropInScroll, to: imageView)

        let scale = image.scale
        let r = CGRect(
            x: visibleInImageView.origin.x * scale,
            y: visibleInImageView.origin.y * scale,
            width: visibleInImageView.size.width * scale,
            height: visibleInImageView.size.height * scale
        )

        guard let cg = image.cgImage else { return }
        let imagePixelWidth = CGFloat(cg.width)
        let imagePixelHeight = CGFloat(cg.height)

        let clipped = r.intersection(CGRect(x: 0, y: 0, width: imagePixelWidth, height: imagePixelHeight))
        guard clipped.width >= 2, clipped.height >= 2,
              let croppedCg = cg.cropping(to: clipped) else { return }

        let out = UIImage(cgImage: croppedCg, scale: scale, orientation: image.imageOrientation)
        guard let jpeg = out.jpegData(compressionQuality: 0.88) else { return }
        onComplete(jpeg)
    }
}

private func ottoClamp(_ value: CGFloat, _ low: CGFloat, _ high: CGFloat) -> CGFloat {
    min(max(value, low), high)
}
