import SwiftUI
import WidgetKit

@main
struct OttoSharingWidgetBundle: WidgetBundle {
    var body: some Widget {
        // Shipped only when `OttoSharingWidgetConfiguration.isEnabled` and the extension is embedded in the app target.
        SharingControlWidget()
    }
}
