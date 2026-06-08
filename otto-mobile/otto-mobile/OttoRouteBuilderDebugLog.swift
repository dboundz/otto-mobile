import Foundation

/// Console diagnostics for Route Builder perf investigations. Filter Xcode console with `RouteBuilder` or `MapScreen`.
enum OttoRouteBuilderDebugLog {
    #if DEBUG
    private static var mapHostInstanceCounter = 0
    private static var lastCameraLogAt = Date.distantPast
    private static var cameraEventsSinceLog = 0
    private static var isGesturing = false
    #endif

    #if DEBUG
    private static var editorOpenStartedAt: Date?
    #endif

    static func nextMapHostInstance() -> Int {
        #if DEBUG
        mapHostInstanceCounter += 1
        return mapHostInstanceCounter
        #else
        return 0
        #endif
    }

    // MARK: - Map lifecycle

    static func mapScreenTabMapMounted() {
        #if DEBUG
        print("[MapScreen] tab Mapbox map mounted (OttoMapboxMapView in tree)")
        #endif
    }

    static func mapScreenTabMapUnmounted() {
        #if DEBUG
        print("[MapScreen] tab Mapbox map unmounted (black placeholder in tree)")
        #endif
    }

    static func routeBuilderMapHostAppeared(instance: Int) {
        #if DEBUG
        print("[RouteBuilder] MapHost appeared instance=\(instance)")
        #endif
    }

    static func routeBuilderMapHostDisappeared(instance: Int) {
        #if DEBUG
        print("[RouteBuilder] MapHost disappeared instance=\(instance)")
        #endif
    }

    static func routeBuilderEditorMapReady(instance: Int, style: String) {
        #if DEBUG
        let elapsed = editorOpenStartedAt.map { String(format: "%.2fs", Date().timeIntervalSince($0)) } ?? "—"
        print("[RouteBuilder] editor Mapbox map ready instance=\(instance) style=\(style) elapsed=\(elapsed)")
        #endif
    }

    static func editorOpenBegan(routeId: String?, roadPointCount: Int) {
        #if DEBUG
        editorOpenStartedAt = Date()
        print("[RouteBuilder] editor open began routeId=\(routeId ?? "new") roadPts=\(roadPointCount)")
        #endif
    }

    static func editorOpenEnded() {
        #if DEBUG
        editorOpenStartedAt = nil
        #endif
    }

    static func polylineIndexBuilt(pointCount: Int) {
        #if DEBUG
        let elapsed = editorOpenStartedAt.map { String(format: "%.2fs", Date().timeIntervalSince($0)) } ?? "—"
        print("[RouteBuilder] polyline index built pts=\(pointCount) elapsed=\(elapsed)")
        #endif
    }

    static func autoPathBootstrap(source: String) {
        #if DEBUG
        let elapsed = editorOpenStartedAt.map { String(format: "%.2fs", Date().timeIntervalSince($0)) } ?? "—"
        print("[RouteBuilder] auto path bootstrap source=\(source) elapsed=\(elapsed)")
        #endif
    }

    // MARK: - Imperative map layers

    static func routeLineUpdated(pointCount: Int, fingerprint: String) {
        #if DEBUG
        let elapsed = editorOpenStartedAt.map { String(format: "%.2fs", Date().timeIntervalSince($0)) } ?? "—"
        print("[RouteBuilder] route line GeoJSON upload displayPts=\(pointCount) fp=\(fingerprint.prefix(20)) elapsed=\(elapsed)")
        #endif
    }

    static func displayLinePrepared(fullPoints: Int, displayPoints: Int) {
        #if DEBUG
        let elapsed = editorOpenStartedAt.map { String(format: "%.2fs", Date().timeIntervalSince($0)) } ?? "—"
        print("[RouteBuilder] display line prepared full=\(fullPoints) display=\(displayPoints) elapsed=\(elapsed)")
        #endif
    }

    static func pathDotsUpdated(userCount: Int, autoCount: Int, fingerprint: String) {
        #if DEBUG
        print("[RouteBuilder] path dots updated user=\(userCount) auto=\(autoCount) fp=\(fingerprint.prefix(12))")
        #endif
    }

    // MARK: - SwiftUI / content

    static func mapContentSnapshotChanged(
        markerCount: Int,
        lineFingerprint: String,
        pathFingerprint: String,
        allowsInteraction: Bool
    ) {
        #if DEBUG
        print(
            "[RouteBuilder] map content changed markers=\(markerCount) " +
            "lineFp=\(lineFingerprint.prefix(12)) pathFp=\(pathFingerprint.prefix(12)) " +
            "interaction=\(allowsInteraction)"
        )
        #endif
    }

    static func mapHostEquatableUpdate(programmaticViewportChanged: Bool) {
        #if DEBUG
        if programmaticViewportChanged {
            print("[RouteBuilder] MapHost equatable update (programmatic viewport changed)")
        }
        #endif
    }

    static func lodTierSettled(_ tier: String, latitudeDelta: Double) {
        #if DEBUG
        print("[RouteBuilder] LOD settled tier=\(tier) span=\(String(format: "%.5f", latitudeDelta))°")
        #endif
    }

    // MARK: - Gestures / camera

    static func gestureBegan() {
        #if DEBUG
        isGesturing = true
        cameraEventsSinceLog = 0
        print("[RouteBuilder] map gesture began")
        #endif
    }

    static func gestureEnded() {
        #if DEBUG
        isGesturing = false
        print("[RouteBuilder] map gesture ended (camera events this gesture=\(cameraEventsSinceLog))")
        cameraEventsSinceLog = 0
        #endif
    }

    static func cameraChangedWhileGesturing() {
        #if DEBUG
        guard isGesturing else { return }
        cameraEventsSinceLog += 1
        let now = Date()
        guard now.timeIntervalSince(lastCameraLogAt) >= 1 else { return }
        lastCameraLogAt = now
        print("[RouteBuilder] camera events ~\(cameraEventsSinceLog)/s while gesturing")
        cameraEventsSinceLog = 0
        #endif
    }
}
