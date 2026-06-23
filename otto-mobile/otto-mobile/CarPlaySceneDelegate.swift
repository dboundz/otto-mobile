import CarPlay
import os
import SwiftUI
import UIKit

private func carPlayLog(_ message: String) {
    print(message)
    OttoLog.carPlay.info("\(message, privacy: .public)")
}

@MainActor
final class CarPlaySceneDelegate: UIResponder, CPTemplateApplicationSceneDelegate, CPInterfaceControllerDelegate {
    private var interfaceController: CPInterfaceController?
    private var mapTemplate: CPMapTemplate?
    private var mapController: CarPlayMapController?
    private var hostingController: UIViewController?
    private weak var carPlayWindow: CPWindow?
    private weak var carPlayScene: UIScene?
    private var phoneAppReadyObserver: NSObjectProtocol?
    private var pendingMapHostInstallReason: String?

    func templateApplicationScene(
        _ templateApplicationScene: CPTemplateApplicationScene,
        didConnect interfaceController: CPInterfaceController,
        to window: CPWindow
    ) {
        carPlayLog("[CarPlayMap] Scene connected")
        self.interfaceController = interfaceController
        interfaceController.delegate = self
        carPlayLog("[CarPlayMap] Interface controller available")
        carPlayLog("[CarPlay] didConnect windowBounds=\(window.bounds)")

        self.carPlayWindow = window
        self.carPlayScene = templateApplicationScene
        observePhoneAppReadyForCarPlayIfNeeded()

        guard OttoCarPlayAppBridge.shared.isPhoneAppReadyForCarPlay else {
            installWaitingForPhoneTemplate()
            return
        }
        installCarPlayMap(reason: "did-connect")
    }

    func templateApplicationScene(
        _ templateApplicationScene: CPTemplateApplicationScene,
        didDisconnect interfaceController: CPInterfaceController,
        from window: CPWindow
    ) {
        carPlayLog("[CarPlay] didDisconnect")
        mapController?.endNativeNavigationIfNeeded(reason: "disconnect")
        if self.interfaceController === interfaceController {
            interfaceController.delegate = nil
        }
        if mapController != nil || OttoCarPlayAppBridge.shared.isConfigured {
            OttoCarPlayAppBridge.shared.markCarPlayMapConnected(false)
            OttoCarPlayAppBridge.shared.markCarPlayMapActive(false)
        }
        if let phoneAppReadyObserver {
            NotificationCenter.default.removeObserver(phoneAppReadyObserver)
            self.phoneAppReadyObserver = nil
        }
        window.rootViewController = nil
        hostingController = nil
        mapController = nil
        mapTemplate = nil
        pendingMapHostInstallReason = nil
        carPlayWindow = nil
        carPlayScene = nil
        self.interfaceController = nil
    }

    func sceneDidBecomeActive(_ scene: UIScene) {
        updateCarPlaySceneFocus(active: true, phase: "sceneDidBecomeActive")
    }

    func sceneWillResignActive(_ scene: UIScene) {
        updateCarPlaySceneFocus(active: false, phase: "sceneWillResignActive")
    }

    func sceneDidEnterBackground(_ scene: UIScene) {
        updateCarPlaySceneFocus(active: false, phase: "sceneDidEnterBackground")
    }

    func templateWillAppear(_ aTemplate: CPTemplate, animated: Bool) {
        updateCarPlayMapFocus(for: aTemplate, visible: true, phase: "willAppear")
    }

    func templateDidAppear(_ aTemplate: CPTemplate, animated: Bool) {
        updateCarPlayMapFocus(for: aTemplate, visible: true, phase: "didAppear")
        guard aTemplate === mapTemplate else { return }
        installPendingCarPlayMapHostIfNeeded(reason: "template-did-appear")
    }

    func templateWillDisappear(_ aTemplate: CPTemplate, animated: Bool) {
        updateCarPlayMapFocus(for: aTemplate, visible: false, phase: "willDisappear")
    }

    func templateDidDisappear(_ aTemplate: CPTemplate, animated: Bool) {
        updateCarPlayMapFocus(for: aTemplate, visible: false, phase: "didDisappear")
    }

    private func updateCarPlayMapFocus(for template: CPTemplate, visible: Bool, phase: String) {
        guard template === mapTemplate else {
            #if DEBUG
            carPlayLog("[CarPlayMap] template \(phase) ignored type=\(type(of: template))")
            #endif
            return
        }
        #if DEBUG
        carPlayLog("[CarPlayMap] template focus \(phase) visible=\(visible)")
        #endif
        OttoCarPlayAppBridge.shared.markCarPlayMapActive(visible)
    }

    private func updateCarPlaySceneFocus(active: Bool, phase: String) {
        let hasMapTemplate = mapTemplate != nil
        let hasInterfaceController = interfaceController != nil
        let hasWindow = carPlayWindow != nil
        #if DEBUG
        carPlayLog(
            "[CarPlayMap] scene focus \(phase) active=\(active) " +
            "hasMapTemplate=\(hasMapTemplate) hasInterfaceController=\(hasInterfaceController) hasWindow=\(hasWindow)"
        )
        #endif
        guard hasMapTemplate, hasInterfaceController, hasWindow else {
            if OttoCarPlayAppBridge.shared.isConfigured {
                OttoCarPlayAppBridge.shared.markCarPlayMapActive(false)
            }
            return
        }
        OttoCarPlayAppBridge.shared.markCarPlayMapActive(active)
    }

    private func reloadCarPlayMapHost(reason: String) {
        if reason == "first-appear-no-render-source" {
            replayCarPlaySceneActivation(reason: reason)
            mapController?.completeFullMapHostReload()
            return
        }
        guard let window = carPlayWindow,
              let controller = mapController else {
            carPlayLog("[CarPlayMap] Full host reload skipped reason=\(reason) missing window/controller")
            return
        }
        carPlayLog("[CarPlayMap] Full host reload reason=\(reason)")
        installCarPlayMapHost(in: window, controller: controller, reason: "reload-\(reason)")
    }

    private func replayCarPlaySceneActivation(reason: String) {
        guard let scene = carPlayScene else {
            carPlayLog("[CarPlayMap] Scene activation replay skipped reason=\(reason) missing scene")
            return
        }
        carPlayLog("[CarPlayMap] Scene activation replay reason=\(reason) state=\(scene.activationState.rawValue)")
        NotificationCenter.default.post(name: UIScene.didActivateNotification, object: scene)
    }

    private func observePhoneAppReadyForCarPlayIfNeeded() {
        guard phoneAppReadyObserver == nil else { return }
        phoneAppReadyObserver = NotificationCenter.default.addObserver(
            forName: .ottoCarPlayPhoneAppReady,
            object: nil,
            queue: .main
        ) { [weak self] _ in
            Task { @MainActor [weak self] in
                self?.handlePhoneAppReadyForCarPlay()
            }
        }
    }

    private func handlePhoneAppReadyForCarPlay() {
        guard interfaceController != nil,
              carPlayWindow != nil else {
            return
        }
        guard mapController == nil else {
            carPlayLog("[CarPlayMap] Phone app ready; map already installed")
            return
        }
        installCarPlayMap(reason: "phone-app-ready")
    }

    private func installWaitingForPhoneTemplate() {
        carPlayLog("[CarPlayMap] Waiting for Driftd phone app before map initialization")
        interfaceController?.setRootTemplate(
            makeWaitingForPhoneTemplate(
                title: "Waiting for iPhone",
                detail: "Open Driftd on your iPhone to finish loading."
            ),
            animated: true,
            completion: nil
        )
    }

    private func makeWaitingForPhoneTemplate(title: String, detail: String) -> CPInformationTemplate {
        CPInformationTemplate(
            title: "Driftd Starting",
            layout: .leading,
            items: [
                CPInformationItem(title: title, detail: detail)
            ],
            actions: []
        )
    }

    private func installCarPlayMap(reason: String) {
        guard let interfaceController,
              let appState = OttoCarPlayAppBridge.shared.configuredAppStateIfAvailable,
              let locationService = OttoCarPlayAppBridge.shared.configuredLocationServiceIfAvailable,
              OttoCarPlayAppBridge.shared.isPhoneAppReadyForCarPlay else {
            carPlayLog("[CarPlayMap] Map install skipped reason=\(reason) waiting for phone app")
            installWaitingForPhoneTemplate()
            return
        }

        OttoMapboxRuntimeConfig.configureIfReady(tag: "CarPlayMap")
        let controller = CarPlayMapController()
        controller.interfaceController = interfaceController
        controller.fullMapHostReloadHandler = { [weak self] reason in
            self?.reloadCarPlayMapHost(reason: reason)
        }
        controller.configure(appState: appState, locationService: locationService)
        let mapTemplate = makeMapTemplate(controller: controller)
        carPlayLog("[CarPlayMap] Map template created reason=\(reason)")
        controller.mapTemplate = mapTemplate

        self.mapController = controller
        self.mapTemplate = mapTemplate
        pendingMapHostInstallReason = reason

        OttoCarPlayAppBridge.shared.markCarPlayMapConnected(true)
        OttoCarPlayAppBridge.shared.markCarPlayMapActive(false)
        interfaceController.setRootTemplate(mapTemplate, animated: false) { [weak self, weak controller] _, _ in
            guard let self, let controller else { return }
            DispatchQueue.main.async {
                mapTemplate.mapButtons = self.makeMapButtons(controller: controller)
            }
        }
    }

    private func installPendingCarPlayMapHostIfNeeded(reason: String) {
        guard hostingController == nil else { return }
        guard let window = carPlayWindow,
              let controller = mapController else {
            carPlayLog("[CarPlayMap] Pending host install skipped reason=\(reason) missing window/controller")
            return
        }
        let installReason = pendingMapHostInstallReason ?? reason
        pendingMapHostInstallReason = nil
        installCarPlayMapHost(in: window, controller: controller, reason: installReason)
    }

    private func installCarPlayMapHost(
        in window: CPWindow,
        controller: CarPlayMapController,
        reason: String
    ) {
        guard let appState = OttoCarPlayAppBridge.shared.configuredAppStateIfAvailable,
              let locationService = OttoCarPlayAppBridge.shared.configuredLocationServiceIfAvailable,
              let raceTracksDatasetStore = OttoCarPlayAppBridge.shared.configuredRaceTracksDatasetStoreIfAvailable else {
            carPlayLog("[CarPlayMap] Map host install skipped reason=\(reason) waiting for phone app")
            installWaitingForPhoneTemplate()
            return
        }
        controller.configure(appState: appState, locationService: locationService)
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
            self.replayCarPlaySceneActivation(reason: "\(reason)-layout")
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
