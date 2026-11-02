import SwiftUI
import UIKit

@main
struct AnchorApp: App {
    @StateObject private var store = MetronomeStore()
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(store)
                .onAppear {
                    // Keep the screen awake during practice so the visual metronome stays visible.
                    UIApplication.shared.isIdleTimerDisabled = true
                    store.app.setOutputStandby(enabled: true)
                }
                .onChange(of: scenePhase) { phase in
                    // Keep the audio output warm while visible so Play sounds Beat 1 immediately;
                    // close it in the background (running playback continues).
                    store.app.setOutputStandby(enabled: phase != .background)
                }
        }
    }
}
