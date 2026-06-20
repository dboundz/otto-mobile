import CarPlay
import SwiftUI
import UIKit

@MainActor
final class CarPlaySceneDelegate: UIResponder, CPTemplateApplicationSceneDelegate {
    private var interfaceController: CPInterfaceController?
    private var mapTemplate: CPMapTemplate?
    private var mapController: CarPlayMapController?
    private var hostingController: UIViewController?
    private weak var carPlayWindow: CPWindow?

    func templateApplicationScene(
        _ templateApplicationScene: CPTemplateApplicationScene,
        didConnect interfaceController: CPInterfaceController,
        to window: CPWindow
    ) {
        print("[CarPlayMap] Scene connected")
        self.interfaceController = interfaceController
        print("[CarPlayMap] Interface controller available")
        OttoMapboxRuntimeConfig.configureIfReady(tag: "CarPlayMap")

        let controller = CarPlayMapController()
        controller.interfaceController = interfaceController
        controller.fullMapHostReloadHandler = { [weak self] reason in
            self?.reloadCarPlayMapHost(reason: reason)
        }
        controller.configure(
            appState: OttoCarPlayAppBridge.shared.appState,
            locationService: OttoCarPlayAppBridge.shared.locationService
        )
        let mapTemplate = makeMapTemplate(controller: controller)
        print("[CarPlayMap] Map template created")
        controller.mapTemplate = mapTemplate
        print("[CarPlay] didConnect windowBounds=\(window.bounds)")

        self.mapController = controller
        self.mapTemplate = mapTemplate
        self.carPlayWindow = window
        installCarPlayMapHost(in: window, controller: controller, reason: "did-connect")

        OttoCarPlayAppBridge.shared.markCarPlayMapActive(true)
        interfaceController.setRootTemplate(mapTemplate, animated: false) { [weak self, weak controller] _, _ in
            guard let self, let controller else { return }
            DispatchQueue.main.async {
                mapTemplate.mapButtons = self.makeMapButtons(controller: controller)
            }
        }
    }

    func templateApplicationScene(
        _ templateApplicationScene: CPTemplateApplicationScene,
        didDisconnect interfaceController: CPInterfaceController,
        from window: CPWindow
    ) {
        print("[CarPlay] didDisconnect")
        mapController?.endNativeNavigationIfNeeded(reason: "disconnect")
        OttoCarPlayAppBridge.shared.markCarPlayMapActive(false)
        window.rootViewController = nil
        hostingController = nil
        mapController = nil
        mapTemplate = nil
        carPlayWindow = nil
        self.interfaceController = nil
    }

    private func reloadCarPlayMapHost(reason: String) {
        guard let window = carPlayWindow,
              let controller = mapController else {
            print("[CarPlayMap] Full host reload skipped reason=\(reason) missing window/controller")
            return
        }
        print("[CarPlayMap] Full host reload reason=\(reason)")
        installCarPlayMapHost(in: window, controller: controller, reason: "reload-\(reason)")
    }

    private func installCarPlayMapHost(
        in window: CPWindow,
        controller: CarPlayMapController,
        reason: String
    ) {
        controller.configure(
            appState: OttoCarPlayAppBridge.shared.appState,
            locationService: OttoCarPlayAppBridge.shared.locationService
        )
        let appState = OttoCarPlayAppBridge.shared.appState
        let locationService = OttoCarPlayAppBridge.shared.locationService
        let raceTracksDatasetStore = OttoCarPlayAppBridge.shared.raceTracksDatasetStore
        let mapView = CarPlayMapView(
            appState: appState,
            locationService: locationService,
            raceTracksDatasetStore: raceTracksDatasetStore,
            controller: controller
        )
        .environmentObject(appState)
        .environmentObject(locationService)
        .environmentObject(raceTracksDatasetStore)
        let hostingController = UIHostingController(rootView: mapView)
        hostingController.view.backgroundColor = .black
        hostingController.view.frame = window.bounds
        hostingController.view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        self.hostingController = hostingController
        window.rootViewController = hostingController
        controller.updateHostSurface(
            windowBounds: window.bounds,
            viewBounds: hostingController.view.bounds,
            windowAttached: hostingController.view.window != nil,
            reason: reason
        )
        DispatchQueue.main.async { [weak hostingController, weak window, weak controller] in
            guard let hostingController, let window, let controller else { return }
            hostingController.view.setNeedsLayout()
            hostingController.view.layoutIfNeeded()
            controller.updateHostSurface(
                windowBounds: window.bounds,
                viewBounds: hostingController.view.bounds,
                windowAttached: hostingController.view.window != nil,
                reason: "\(reason)-layout"
            )
        }
    }

    private func makeMapTemplate(controller: CarPlayMapController) -> CPMapTemplate {
        let template = CPMapTemplate()
        template.tabTitle = "Map"
        template.automaticallyHidesNavigationBar = true
        template.hidesButtonsWithNavigationBar = false
        return template
    }

    private func makeMapButtons(controller: CarPlayMapController) -> [CPMapButton] {
        [
            makeSearchMapButton(controller: controller),
            makeZoomMapButton(systemName: "plus", controller: controller, delta: 1),
            makeZoomMapButton(systemName: "minus", controller: controller, delta: -1),
            makeHazardMapButton(controller: controller),
        ]
    }

    private func makeSearchMapButton(controller: CarPlayMapController) -> CPMapButton {
        let button = CPMapButton { [weak controller] _ in
            controller?.presentDestinationLookupTemplate()
        }
        button.image = carPlayMapButtonSymbol("magnifyingglass")
        return button
    }

    private func makeZoomMapButton(systemName: String, controller: CarPlayMapController, delta: Int) -> CPMapButton {
        let button = CPMapButton { [weak controller] _ in
            controller?.requestZoom(delta: delta)
        }
        button.image = carPlayMapButtonSymbol(systemName)
        return button
    }

    private func makeHazardMapButton(controller: CarPlayMapController) -> CPMapButton {
        let button = CPMapButton { [weak controller] _ in
            controller?.presentHazardReportTemplate()
        }
        button.image = carPlayMapButtonSymbol("exclamationmark.triangle", color: .systemYellow)
        return button
    }

    private func carPlayMapButtonSymbol(
        _ systemName: String,
        color: UIColor = .white,
        pointSize: CGFloat = 17,
        canvasSize: CGFloat = 44
    ) -> UIImage {
        let configuration = UIImage.SymbolConfiguration(pointSize: pointSize, weight: .semibold)
        guard let symbol = UIImage(systemName: systemName, withConfiguration: configuration) else {
            return UIImage()
        }
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: canvasSize, height: canvasSize))
        return renderer.image { _ in
            let tinted = symbol.withTintColor(color, renderingMode: .alwaysOriginal)
            let size = tinted.size
            let rect = CGRect(
                x: (canvasSize - size.width) / 2,
                y: (canvasSize - size.height) / 2,
                width: size.width,
                height: size.height
            )
            tinted.draw(in: rect)
        }.withRenderingMode(.alwaysOriginal)
    }

}
