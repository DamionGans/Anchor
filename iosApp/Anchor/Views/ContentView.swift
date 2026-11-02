import AnchorShared
import SwiftUI

/// Single, non-scrolling metronome screen (mirrors the Android `MetronomeScreen`).
struct ContentView: View {
    @EnvironmentObject private var store: MetronomeStore
    @State private var showSettings = false
    @State private var showTimeSignature = false

    var body: some View {
        let state = store.state
        let accent = state.accentColor.swiftUIColor

        ZStack {
            Color(.systemBackground).ignoresSafeArea()

            VStack(spacing: 14) {
                TopBar(state: state, accent: accent, onSettings: { showSettings = true })

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

            // Screen flash overlay (same playhead-anchored schedule as the beat bar & pendulum)
            if state.isFlashEnabled && state.flashPulse {
                (state.isCurrentBeatAccent ? AnchorPalette.accentDownbeat : accent)
                    .opacity(0.35)
                    .ignoresSafeArea()
                    .allowsHitTesting(false)
            }
        }
        .tint(accent)
        .preferredColorScheme(state.themeMode.colorScheme)
        .sheet(isPresented: $showSettings) {
            SettingsView()
                .environmentObject(store)
        }
        .sheet(isPresented: $showTimeSignature) {
            TimeSignatureView(state: state, app: store.app, accent: accent)
                .presentationDetents([.medium, .large])
        }
        .alert(
            "Update available",
            isPresented: Binding(
                get: { store.state.showUpdateDialog && store.state.availableUpdate != nil },
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

private struct TopBar: View {
    let state: MetronomeUiState
    let accent: Color
    let onSettings: () -> Void

    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text("ANCHOR")
                    .font(.system(size: 20, weight: .heavy, design: .rounded))
                    .tracking(4)
                Text("Rock-solid time for serious play")
                    .font(.caption2)
                    .foregroundStyle(.secondary)
            }
            Spacer()
            Label(state.audioInfo.deviceName, systemImage: outputIcon)
                .font(.caption)
                .lineLimit(1)
                .padding(.horizontal, 10)
                .padding(.vertical, 6)
                .background(Capsule().fill(Color(.secondarySystemBackground)))
            Button(action: onSettings) {
                Image(systemName: "gearshape.fill")
                    .font(.title3)
                    .frame(width: 48, height: 48)
            }
            .accessibilityLabel("Settings")
        }
    }

    private var outputIcon: String {
        switch state.audioInfo.deviceType.name {
        case "BLUETOOTH": return "airpodspro"
        case "WIRED_HEADPHONES": return "headphones"
        case "USB_AUDIO": return "cable.connector"
        default: return "speaker.wave.2.fill"
        }
    }
}

private struct TempoSection: View {
    let state: MetronomeUiState
    let accent: Color
    let app: AnchorIosApp

    var body: some View {
        VStack(spacing: 6) {
            Text(state.tempoMarking.uppercased())
                .font(.caption.weight(.semibold))
                .tracking(2)
                .foregroundStyle(accent)

            HStack(spacing: 10) {
                StepButton(label: "-5") { app.adjustBpm(delta: -5) }
                StepButton(label: "-1") { app.adjustBpm(delta: -1) }

                VStack(spacing: 0) {
                    Text("\(state.bpm)")
                        .font(.system(size: 64, weight: .bold, design: .rounded))
                        .monospacedDigit()
                        .contentTransition(.numericText())
                    Text("BPM")
                        .font(.caption.weight(.medium))
                        .foregroundStyle(.secondary)
                }
                .frame(minWidth: 130)

                StepButton(label: "+1") { app.adjustBpm(delta: 1) }
                StepButton(label: "+5") { app.adjustBpm(delta: 5) }
            }

            Slider(
                value: Binding(
                    get: { Double(state.bpm) },
                    set: { app.changeBpm(bpm: Int32($0.rounded())) }
                ),
                in: Double(app.minBpm)...Double(app.maxBpm),
                step: 1
            )
            .accessibilityLabel("Tempo")
        }
    }
}

private struct StepButton: View {
    let label: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(label)
                .font(.headline.monospacedDigit())
                .frame(width: 48, height: 48)
                .background(Circle().fill(Color(.secondarySystemBackground)))
        }
        .buttonStyle(.plain)
    }
}

struct PendulumView: View {
    let angle: Double
    let isPlaying: Bool
    let accent: Color

    var body: some View {
        GeometryReader { geo in
            let length = min(geo.size.height * 0.9, 220)
            ZStack(alignment: .top) {
                Capsule()
                    .fill(accent.opacity(isPlaying ? 1 : 0.5))
                    .frame(width: 4, height: length)
                    .overlay(alignment: .bottom) {
                        Circle()
                            .fill(accent)
                            .frame(width: 22, height: 22)
                            .offset(y: 11)
                    }
                    .rotationEffect(.degrees(angle), anchor: .top)
                Circle()
                    .fill(Color.secondary)
                    .frame(width: 10, height: 10)
                    .offset(y: -5)
            }
            .frame(width: geo.size.width, height: geo.size.height, alignment: .top)
        }
        .accessibilityHidden(true)
    }
}

struct BeatBarView: View {
    let state: MetronomeUiState
    let accent: Color
    let onBeatTap: (Int) -> Void

    var body: some View {
        let accents = state.accents
        HStack(alignment: .bottom, spacing: 6) {
            ForEach(0..<accents.count, id: \.self) { index in
                let beatAccent = accents[index]
                let isActive = state.isPlaying && Int(state.currentBeat) == index
                Button {
                    onBeatTap(index)
                } label: {
                    VStack(spacing: 4) {
                        GeometryReader { geo in
                            VStack {
                                Spacer(minLength: 0)
                                RoundedRectangle(cornerRadius: 6)
                                    .fill(fill(for: beatAccent, active: isActive))
                                    .frame(height: max(8, geo.size.height * beatAccent.level))
                            }
                        }
                        Text("\(index + 1)")
                            .font(.caption2.monospacedDigit())
                            .foregroundStyle(isActive ? accent : Color.secondary)
                    }
                    .frame(minWidth: 18, maxWidth: .infinity)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Beat \(index + 1), \(beatAccent.label)")
            }
        }
    }

    private func fill(for beatAccent: BeatAccent, active: Bool) -> Color {
        if beatAccent.isMuted { return AnchorPalette.accentMute }
        let base = beatAccent.isAccent ? AnchorPalette.accentDownbeat : accent
        return active ? base : base.opacity(0.35)
    }
}

private struct QuickControlsView: View {
    let state: MetronomeUiState
    let accent: Color
    let app: AnchorIosApp
    let onTimeSignature: () -> Void

    var body: some View {
        HStack(spacing: 10) {
            Button(action: onTimeSignature) {
                VStack(spacing: 0) {
                    Text("\(state.totalBeats)/\(state.timeSignatureDenominator)")
                        .font(.title3.weight(.bold).monospacedDigit())
                    Text("Time")
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                }
                .frame(minWidth: 64, minHeight: 48)
                .background(RoundedRectangle(cornerRadius: 12).fill(Color(.secondarySystemBackground)))
            }
            .buttonStyle(.plain)

            Picker("Subdivision", selection: Binding(
                get: { state.currentSubdivision.name },
                set: { name in
                    if let sub = app.subdivisions.first(where: { $0.name == name }) {
                        app.changeSubdivision(subdivision: sub)
                    }
                }
            )) {
                ForEach(app.subdivisions, id: \.name) { sub in
                    Text(sub.symbol).tag(sub.name)
                }
            }
            .pickerStyle(.segmented)
            .frame(minHeight: 48)

            Menu {
                ForEach(app.soundSets, id: \.name) { sound in
                    Button {
                        app.changeSoundSet(soundSet: sound)
                    } label: {
                        if sound == state.currentSoundSet {
                            Label(sound.displayName, systemImage: "checkmark")
                        } else {
                            Text(sound.displayName)
                        }
                    }
                }
            } label: {
                Image(systemName: "music.note")
                    .font(.title3)
                    .frame(width: 48, height: 48)
                    .background(RoundedRectangle(cornerRadius: 12).fill(Color(.secondarySystemBackground)))
            }
            .accessibilityLabel("Sound: \(state.currentSoundSet.displayName)")
        }
    }
}

private struct TransportBar: View {
    let state: MetronomeUiState
    let accent: Color
    let app: AnchorIosApp

    var body: some View {
        let onPress = state.buttonTriggerMode.registersOnPress
        HStack(spacing: 14) {
            TriggerButton(registersOnPress: onPress, action: { app.tapTempo() }) {
                VStack(spacing: 2) {
                    Text("TAP")
                        .font(.headline)
                    Text(tapCaption)
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }
                .frame(maxWidth: .infinity, minHeight: 64)
                .background(RoundedRectangle(cornerRadius: 16).fill(Color(.secondarySystemBackground)))
            }

            TriggerButton(registersOnPress: onPress, action: { app.togglePlayPause() }) {
                Image(systemName: state.isPlaying ? "pause.fill" : "play.fill")
                    .font(.system(size: 34, weight: .bold))
                    .foregroundStyle(Color(.systemBackground))
                    .frame(width: 88, height: 88)
                    .background(Circle().fill(accent))
            }
            .accessibilityLabel(state.isPlaying ? "Pause" : "Play")

            VStack(spacing: 8) {
                Button("½×") { app.multiplyBpm(factor: 0.5) }
                    .frame(maxWidth: .infinity, minHeight: 28)
                Button("2×") { app.multiplyBpm(factor: 2.0) }
                    .frame(maxWidth: .infinity, minHeight: 28)
            }
            .font(.headline)
            .frame(maxWidth: .infinity, minHeight: 64)
            .background(RoundedRectangle(cornerRadius: 16).fill(Color(.secondarySystemBackground)))
        }
    }

    private var tapCaption: String {
        let status = state.tapStatus
        if status.tapCount == 0 { return "Tap tempo" }
        return status.pulseDescription
    }
}

/// Button that fires either on touch-down (`ON_PRESS`) or on release (`ON_RELEASE`).
struct TriggerButton<Label: View>: View {
    let registersOnPress: Bool
    let action: () -> Void
    @ViewBuilder let label: () -> Label

    @State private var isPressed = false

    var body: some View {
        if registersOnPress {
            label()
                .scaleEffect(isPressed ? 0.95 : 1)
                .contentShape(Rectangle())
                .gesture(
                    DragGesture(minimumDistance: 0)
                        .onChanged { _ in
                            if !isPressed {
                                isPressed = true
                                action()
                            }
                        }
                        .onEnded { _ in isPressed = false }
                )
                .accessibilityAddTraits(.isButton)
                .accessibilityAction { action() }
        } else {
            Button(action: action, label: label)
                .buttonStyle(.plain)
        }
    }
}
