import AnchorShared
import SwiftUI

struct TopBar: View {
    let state: MetronomeUiState
    let accent: Color
    let onPlaylists: () -> Void
    let onSettings: () -> Void

    var body: some View {
        let hasSetlist = state.activePlaylist != nil || state.activeSong != nil

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
            Button(action: onPlaylists) {
                Image(systemName: "music.note.list")
                    .font(.title3)
                    .foregroundStyle(hasSetlist ? accent : .primary)
                    .frame(width: 48, height: 48)
            }
            .accessibilityLabel("Playlists & Song Library")
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

struct SetlistBarView: View {
    let state: MetronomeUiState
    let accent: Color
    let app: AnchorIosApp
    let onOpenPlaylists: () -> Void

    var body: some View {
        let playlist = state.activePlaylist
        let song = state.activeSong
        let idx = state.activeSongIndex?.intValue
        let total = playlist?.songIds.count ?? 0
        let canPrev = playlist != nil && idx != nil && idx! > 0
        let canNext = playlist != nil && total > 0 && (idx == nil || idx! < total - 1)

        HStack(spacing: 4) {
            if playlist != nil {
                Button {
                    app.previousSong()
                } label: {
                    Image(systemName: "chevron.left")
                        .font(.subheadline.weight(.bold))
                        .frame(width: 48, height: 44)
                }
                .disabled(!canPrev)
                .accessibilityLabel("Previous song")
            }

            Button(action: onOpenPlaylists) {
                VStack(spacing: 2) {
                    HStack(spacing: 5) {
                        if let idx, playlist != nil, total > 0 {
                            Text("\(idx + 1)/\(total) ·")
                                .font(.caption.weight(.bold))
                                .monospacedDigit()
                                .foregroundStyle(accent)
                        }
                        Text(song?.name ?? playlist?.name ?? "")
                            .font(.subheadline.weight(.bold))
                            .lineLimit(1)
                            .foregroundStyle(.primary)
                        if state.isActiveSongModified && song != nil {
                            Circle()
                                .fill(AnchorPalette.accentDownbeat)
                                .frame(width: 7, height: 7)
                        }
                    }
                    if let song {
                        Text("\(song.bpm) BPM • \(song.timeSignatureDisplay)\(state.isActiveSongModified ? " (edited)" : "")")
                            .font(.caption2)
                            .monospacedDigit()
                            .foregroundStyle(.secondary)
                    } else if playlist != nil && total == 0 {
                        Text("Empty playlist • Tap to add songs")
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                    }
                }
                .frame(maxWidth: .infinity, minHeight: 44)
            }
            .buttonStyle(.plain)

            if playlist != nil {
                Button {
                    app.nextSong()
                } label: {
                    Image(systemName: "chevron.right")
                        .font(.subheadline.weight(.bold))
                        .frame(width: 48, height: 44)
                }
                .disabled(!canNext)
                .accessibilityLabel("Next song")
            }
        }
        .padding(.horizontal, 6)
        .background(
            RoundedRectangle(cornerRadius: 14)
                .fill(Color(.secondarySystemBackground).opacity(0.85))
        )
    }
}

struct TempoSection: View {
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
            let length = min(geo.size.height * 0.88, min(geo.size.width * 0.75, 320))
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

struct QuickControlsView: View {
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

struct TransportBar: View {
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
