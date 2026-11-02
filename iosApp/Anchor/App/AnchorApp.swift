import SwiftUI
import UIKit

@main
struct AnchorApp: App {
    @StateObject private var store = MetronomeStore()

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(store)
                .onAppear {
                    // Keep the screen awake during practice so the visual metronome stays visible.
                    UIApplication.shared.isIdleTimerDisabled = true
                }
        }
    }
}
