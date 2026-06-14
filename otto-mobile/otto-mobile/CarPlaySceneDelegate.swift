import CarPlay
import SwiftUI
import UIKit

@MainActor
final class CarPlaySceneDelegate: UIResponder, CPTemplateApplicationSceneDelegate {
    private var interfaceController: CPInterfaceController?
    private var mapTemplate: CPMapTemplate?
    private var mapController: CarPlayMapController?
    private var hostingController: UIHostingController<CarPlayMapView>?

    func templateApplicationScene(
        _ templateApplicationScene: CPTemplateApplicationScene,
        didConnect interfaceController: CPInterfaceController,
        to window: CPWindow
    ) {
        self.interfaceController = interfaceController

        let controller = CarPlayMapController()
        controller.interfaceController = interfaceController
        controller.configure(
            appState: OttoCarPlayAppBridge.shared.appState,
            locationService: OttoCarPlayAppBridge.shared.locationService
        )
        let mapTemplate = makeMapTemplate(controller: controller)
        let mapView = CarPlayMapView(
            appState: OttoCarPlayAppBridge.shared.appState,
            locationService: OttoCarPlayAppBridge.shared.locationService,
            raceTracksDatasetStore: OttoCarPlayAppBridge.shared.raceTracksDatasetStore,
            controller: controller
        )
        let hostingController = UIHostingController(rootView: mapView)
        hostingController.view.backgroundColor = .black

        self.mapController = controller
        self.mapTemplate = mapTemplate
        self.hostingController = hostingController
        window.rootViewController = hostingController

        OttoCarPlayAppBridge.shared.markCarPlayMapActive(true)
        interfaceController.setRootTemplate(mapTemplate, animated: false, completion: nil)
    }

    func templateApplicationScene(
        _ templateApplicationScene: CPTemplateApplicationScene,
        didDisconnect interfaceController: CPInterfaceController,
        from window: CPWindow
    ) {
        OttoCarPlayAppBridge.shared.markCarPlayMapActive(false)
        window.rootViewController = nil
        hostingController = nil
        mapController = nil
        mapTemplate = nil
        self.interfaceController = nil
    }

    private func makeMapTemplate(controller: CarPlayMapController) -> CPMapTemplate {
        let template = CPMapTemplate()
        template.tabTitle = "Map"
        template.automaticallyHidesNavigationBar = false
        template.hidesButtonsWithNavigationBar = false
        template.mapButtons = [
            makeHazardMapButton(controller: controller),
        ]
        return template
    }

    private func makeHazardMapButton(controller: CarPlayMapController) -> CPMapButton {
        let button = CPMapButton { [weak controller] _ in
            controller?.presentHazardReportTemplate()
        }
        let image = makeHazardButtonImage()
        button.image = image
        button.focusedImage = image
        return button
    }

    private func makeHazardButtonImage() -> UIImage {
        let size = CGSize(width: 56, height: 56)
        let renderer = UIGraphicsImageRenderer(size: size)
        return renderer.image { context in
            let edgeOffset: CGFloat = 8
            let rect = CGRect(
                x: 3,
                y: 3,
                width: size.width - 6 - edgeOffset,
                height: size.height - 6 - edgeOffset
            )
            context.cgContext.setShouldAntialias(true)
            UIColor.black.withAlphaComponent(0.86).setFill()
            UIBezierPath(ovalIn: rect).fill()
            UIColor.systemYellow.setStroke()
            let path = UIBezierPath(ovalIn: rect)
            path.lineWidth = 4.5
            path.stroke()

            let configuration = UIImage.SymbolConfiguration(pointSize: 26, weight: .semibold)
            let symbol = UIImage(systemName: "exclamationmark.triangle", withConfiguration: configuration)?
                .withTintColor(.systemYellow, renderingMode: .alwaysOriginal)
            let symbolSide = 30.0
            let symbolRect = CGRect(
                x: rect.midX - symbolSide / 2,
                y: rect.midY - symbolSide / 2,
                width: symbolSide,
                height: symbolSide
            )
            symbol?.draw(in: symbolRect)
        }.withRenderingMode(.alwaysOriginal)
    }
}
