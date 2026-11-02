import AnchorShared
import QuartzCore
import SwiftUI

/// SwiftUI bridge to the shared Kotlin `MetronomeController` (via `AnchorIosApp`).
///
/// The shared audio loop updates state up to a few hundred times per second (pendulum phase);
/// this store keeps the latest value and publishes it at most once per display frame.
final class MetronomeStore: ObservableObject {
    let app: AnchorIosApp

    @Published private(set) var state: MetronomeUiState

    private var latest: MetronomeUiState
    private var observation: ObservationHandle?
    private var displayLink: CADisplayLink?

    init() {
        let app = AnchorIosApp()
        self.app = app
        self.state = app.state
        self.latest = app.state

        observation = app.observe { [weak self] newState in
            self?.latest = newState
        }

        let link = CADisplayLink(target: DisplayLinkTarget(store: self), selector: #selector(DisplayLinkTarget.tick))
        link.add(to: .main, forMode: .common)
        displayLink = link
    }

    deinit {
        displayLink?.invalidate()
        observation?.cancel()
        app.dispose()
    }

    fileprivate func publishLatest() {
        if latest !== state {
            state = latest
        }
    }
}

/// Weak trampoline so the display link does not retain the store.
private final class DisplayLinkTarget: NSObject {
    weak var store: MetronomeStore?

    init(store: MetronomeStore) {
        self.store = store
    }

    @objc func tick() {
        store?.publishLatest()
    }
}
