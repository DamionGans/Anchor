import AnchorShared
import SwiftUI

/// Single, non-scrolling metronome screen (mirrors the Compose Multiplatform `MetronomeScreen`).
/// Adapts between a compact vertical stack on phones and a two-column studio layout on wide screens.
struct ContentView: View {
    @EnvironmentObject private var store: MetronomeStore
    @State private var showSettings = false
    @State private var showPlaylists = false
    @State private var showTimeSignature = false

    var body: some View {
        let state = store.state
        let accent = state.accentColor.swiftUIColor

        GeometryReader { geo in
            let isWide = geo.size.height >= 480 &&
                (geo.size.width >= 680 || (geo.size.width >= 560 && geo.size.width > geo.size.height * 1.15))

            ZStack {
                Color(.systemBackground).ignoresSafeArea()

                if isWide {
                    VStack(spacing: 14) {
                        TopBar(
                            state: state,
                            accent: accent,
                            onPlaylists: { showPlaylists = true },
                            onSettings: { showSettings = true }
                        )

                        if state.activePlaylist != nil || state.activeSong != nil {
                            SetlistBarView(
                                state: state,
                                accent: accent,
                                app: store.app,
                                onOpenPlaylists: { showPlaylists = true }
                            )
                        }

                        HStack(spacing: 24) {
                            VStack(spacing: 16) {
                                PendulumView(angle: Double(state.pendulumAngle), isPlaying: state.isPlaying, accent: accent)
                                    .frame(maxHeight: .infinity)

                                BeatBarView(state: state, accent: accent) { index in
                                    store.app.cycleBeatAccent(beatIndex: Int32(index))
                                }
                                .frame(height: 80)
                            }
                            .padding(20)
                            .frame(maxWidth: .infinity, maxHeight: .infinity)
                            .background(
                                RoundedRectangle(cornerRadius: 24)
                                    .fill(Color(.secondarySystemBackground).opacity(0.65))
                            )

                            VStack(spacing: 18) {
                                TempoSection(state: state, accent: accent, app: store.app)

                                QuickControlsView(
                                    state: state,
                                    accent: accent,
                                    app: store.app,
                                    onTimeSignature: { showTimeSignature = true }
                                )

                                TransportBar(state: state, accent: accent, app: store.app)
                            }
                            .frame(maxWidth: .infinity, maxHeight: .infinity)
                        }
                    }
                    .padding(.horizontal, 24)
                    .padding(.vertical, 14)
                    .frame(maxWidth: 1120, maxHeight: 760)
                    .frame(width: geo.size.width, height: geo.size.height, alignment: .center)
                } else {
                    VStack(spacing: 12) {
                        TopBar(
                            state: state,
                            accent: accent,
                            onPlaylists: { showPlaylists = true },
                            onSettings: { showSettings = true }
                        )

                        if state.activePlaylist != nil || state.activeSong != nil {
                            SetlistBarView(
                                state: state,
                                accent: accent,
                                app: store.app,
                                onOpenPlaylists: { showPlaylists = true }
                            )
                        }

                        TempoSection(state: state, accent: accent, app: store.app)

                        PendulumView(angle: Double(state.pendulumAngle), isPlaying: state.isPlaying, accent: accent)
                            .frame(maxHeight: .infinity)

                        BeatBarView(state: state, accent: accent) { index in
                            store.app.cycleBeatAccent(beatIndex: Int32(index))
                        }
                        .frame(height: 72)

                        QuickControlsView(
                            state: state,
                            accent: accent,
                            app: store.app,
                            onTimeSignature: { showTimeSignature = true }
                        )

                        TransportBar(state: state, accent: accent, app: store.app)
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 8)
                    .frame(maxWidth: 560, maxHeight: 920)
                    .frame(width: geo.size.width, height: geo.size.height, alignment: .center)
                }

                // Screen flash overlay (same playhead-anchored schedule as the beat bar & pendulum)
                if state.isFlashEnabled && state.flashPulse {
                    (state.isCurrentBeatAccent ? AnchorPalette.accentDownbeat : accent)
                        .opacity(0.35)
                        .ignoresSafeArea()
                        .allowsHitTesting(false)
                }
            }
        }
        .tint(accent)
        .preferredColorScheme(state.themeMode.colorScheme)
        .sheet(isPresented: $showSettings) {
            SettingsView()
                .environmentObject(store)
        }
        .sheet(isPresented: $showPlaylists) {
            PlaylistsView()
                .environmentObject(store)
        }
        .sheet(isPresented: $showTimeSignature) {
            TimeSignatureView(state: state, app: store.app, accent: accent)
                .presentationDetents([.medium, .large])
        }
        .alert(
            "Update available",
            isPresented: Binding(
                get: { store.state.canCheckForUpdates && store.state.showUpdateDialog && store.state.availableUpdate != nil },
                set: { presented in if !presented { store.app.askLater() } }
            ),
            presenting: state.availableUpdate
        ) { _ in
            Button("View release") { store.app.acceptUpdate() }
            Button("Later", role: .cancel) { store.app.askLater() }
            Button("Skip this version", role: .destructive) { store.app.declineUpdate() }
        } message: { release in
            Text("Anchor \(release.tagName) is available.")
        }
    }
}
