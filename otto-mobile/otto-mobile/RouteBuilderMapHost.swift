import Combine
import CoreLocation
import MapboxMaps
import MapKit
import SwiftUI

struct RouteBuilderMapMarkerSnapshot: Identifiable, Equatable {
    let id: UUID
    let coordinate: CLLocationCoordinate2D
    let markerType: String
    let isAutoShape: Bool
    let presentation: RouteBuilderMapMarkerPresentation
    let pinScale: CGFloat
    let dotColor: Color
    let accessibilityTitle: String
    let refreshID: String
    let originalIndex: Int
}

enum RouteBuilderMapMarkerPresentation: Equatable {
    case endpointPin
    case dot
    case pin
}

struct RouteBuilderMapContent: Equatable {
    let lineFingerprint: String
    let lineCoordinates: [CLLocationCoordinate2D]
    let markers: [RouteBuilderMapMarkerSnapshot]
    let nativePathDotsFingerprint: String
    let nativePathDots: RouteBuilderNativePathDotsState
    let allowsInteraction: Bool

    static func == (lhs: Self, rhs: Self) -> Bool {
        lhs.lineFingerprint == rhs.lineFingerprint
            && lhs.nativePathDotsFingerprint == rhs.nativePathDotsFingerprint
            && lhs.markers == rhs.markers
            && lhs.allowsInteraction == rhs.allowsInteraction
    }
}

/// Route Builder map — imperative route line + annotations; owns viewport so the editor shell does not rebuild while panning.
struct RouteBuilderMapHost: View, Equatable {
    let initialViewport: Viewport
    let programmaticViewport: Viewport?
    let mapContent: RouteBuilderMapContent
    let diagnostics: RouteBuilderPerfDiagnostics
    let onCameraChanged: (MKCoordinateRegion) -> Void
    let onGestureEnded: () -> Void
    let onLongPress: (CLLocationCoordinate2D) -> Void
    let onMarkerLongPress: (UUID) -> Void

    @State private var viewport: Viewport
    @StateObject private var routeLineController = RouteBuilderImperativeRouteLineHolder()
    @StateObject private var pathMarkersController = RouteBuilderNativePathMarkersHolder()
    @State private var debugMapHostInstance = OttoRouteBuilderDebugLog.nextMapHostInstance()

    init(
        initialViewport: Viewport,
        programmaticViewport: Viewport?,
        mapContent: RouteBuilderMapContent,
        diagnostics: RouteBuilderPerfDiagnostics,
        onCameraChanged: @escaping (MKCoordinateRegion) -> Void,
        onGestureEnded: @escaping () -> Void,
        onLongPress: @escaping (CLLocationCoordinate2D) -> Void,
        onMarkerLongPress: @escaping (UUID) -> Void
    ) {
        self.initialViewport = initialViewport
        self.programmaticViewport = programmaticViewport
        self.mapContent = mapContent
        self.diagnostics = diagnostics
        self.onCameraChanged = onCameraChanged
        self.onGestureEnded = onGestureEnded
        self.onLongPress = onLongPress
        self.onMarkerLongPress = onMarkerLongPress
        _viewport = State(initialValue: initialViewport)
    }

    static let routeLineSourceID = RouteBuilderImperativeRouteLineController.sourceID

    private var lineRenderState: RouteBuilderLineRenderState {
        RouteBuilderLineRenderState(
            fingerprint: mapContent.lineFingerprint,
            coordinates: mapContent.lineCoordinates
        )
    }

    private var pathDotsRenderState: RouteBuilderNativePathDotsState {
        mapContent.nativePathDots
    }

    private var viewportBinding: Binding<Viewport> {
        Binding(
            get: { viewport },
            set: { newValue in
                viewport = newValue
                diagnostics.recordViewportUpdate()
            }
        )
    }

    var body: some View {
        OttoMapboxMapView(
            viewport: viewportBinding,
            allowsInteraction: mapContent.allowsInteraction,
            onCameraChanged: onCameraChanged,
            onUserGesture: {
                diagnostics.setGesturing(true)
                #if DEBUG
                OttoRouteBuilderDebugLog.gestureBegan()
                #endif
            },
            onGestureEnd: {
                diagnostics.setGesturing(false)
                #if DEBUG
                OttoRouteBuilderDebugLog.gestureEnded()
                #endif
                onGestureEnded()
            },
            onMapLoaded: {
                routeLineController.controller.update(lineRenderState, diagnostics: diagnostics)
                pathMarkersController.controller.update(pathDotsRenderState)
            },
            onMapboxMapReady: { map in
                #if DEBUG
                OttoRouteBuilderDebugLog.routeBuilderEditorMapReady(
                    instance: debugMapHostInstance,
                    style: map.styleURI?.rawValue ?? "default"
                )
                #endif
                routeLineController.controller.attach(map: map)
                pathMarkersController.controller.attach(map: map)
                routeLineController.controller.update(lineRenderState, diagnostics: diagnostics)
                pathMarkersController.controller.update(pathDotsRenderState)
            },
            onMapLongPress: onLongPress
        ) {
            ForEvery(mapContent.markers) { marker in
                MapViewAnnotation(coordinate: marker.coordinate) {
                    RouteBuilderMapMarkerAnnotation(
                        marker: marker,
                        onLongPress: { onMarkerLongPress(marker.id) }
                    )
                    .id(marker.refreshID)
                }
                .allowOverlap(true)
                .variableAnchors(Self.variableAnchors(for: marker))
                .priority(RouteMapGeometry.mapMarkerOverlapPriority(
                    for: marker.coordinate,
                    markerType: marker.markerType,
                    tieBreaker: marker.originalIndex
                ))
            }
        }
        .onAppear {
            #if DEBUG
            OttoRouteBuilderDebugLog.routeBuilderMapHostAppeared(instance: debugMapHostInstance)
            #endif
        }
        .onDisappear {
            #if DEBUG
            OttoRouteBuilderDebugLog.routeBuilderMapHostDisappeared(instance: debugMapHostInstance)
            #endif
        }
        .onChange(of: mapContent.lineFingerprint) { _, _ in
            routeLineController.controller.update(lineRenderState, diagnostics: diagnostics)
        }
        .onChange(of: mapContent.nativePathDotsFingerprint) { _, _ in
            pathMarkersController.controller.update(pathDotsRenderState)
        }
        .onChange(of: mapContent.markers) { _, markers in
            #if DEBUG
            OttoRouteBuilderDebugLog.mapContentSnapshotChanged(
                markerCount: markers.count,
                lineFingerprint: mapContent.lineFingerprint,
                pathFingerprint: mapContent.nativePathDotsFingerprint,
                allowsInteraction: mapContent.allowsInteraction
            )
            #endif
        }
        .onChange(of: programmaticViewport) { _, next in
            guard let next else { return }
            #if DEBUG
            OttoRouteBuilderDebugLog.mapHostEquatableUpdate(programmaticViewportChanged: true)
            #endif
            withViewportAnimation(.easeOut(duration: 0.25)) {
                viewport = next
            }
        }
    }

    static func == (lhs: RouteBuilderMapHost, rhs: RouteBuilderMapHost) -> Bool {
        lhs.mapContent == rhs.mapContent && lhs.programmaticViewport == rhs.programmaticViewport
    }

    private static func variableAnchors(for marker: RouteBuilderMapMarkerSnapshot) -> [ViewAnnotationAnchorConfig] {
        switch marker.presentation {
        case .dot:
            return [ViewAnnotationAnchorConfig(anchor: .center)]
        case .endpointPin:
            return [ViewAnnotationAnchorConfig(anchor: .bottom)]
        case .pin:
            if marker.markerType == "path" {
                return [ViewAnnotationAnchorConfig(anchor: .center)]
            }
            return [ViewAnnotationAnchorConfig(anchor: .bottom)]
        }
    }
}

@MainActor
private final class RouteBuilderImperativeRouteLineHolder: ObservableObject {
    let controller = RouteBuilderImperativeRouteLineController()
}

private struct RouteBuilderMapMarkerAnnotation: View {
    let marker: RouteBuilderMapMarkerSnapshot
    let onLongPress: () -> Void

    var body: some View {
        Group {
            switch marker.presentation {
            case .dot:
                RouteMapMarkerDotView(
                    color: marker.dotColor,
                    subdued: marker.isAutoShape,
                    onLongPress: onLongPress
                )
            case .endpointPin, .pin:
                RouteMapMarkerView(
                    markerType: marker.markerType,
                    isCompleted: false,
                    scale: marker.pinScale,
                    subdued: marker.isAutoShape,
                    usesBottomAnnotationAnchor: marker.markerType != "path",
                    onLongPress: onLongPress
                )
            }
        }
        .accessibilityLabel(marker.accessibilityTitle)
    }
}
