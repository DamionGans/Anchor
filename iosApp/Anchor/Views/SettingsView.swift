import AnchorShared
import SwiftUI

/// Settings sheet (mirrors the Android `SettingsBottomSheet`): sound, feedback, calibration,
/// appearance, controls, audio output, and about / updates.
struct SettingsView: View {
    @EnvironmentObject private var store: MetronomeStore
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        let state = store.state
        let app = store.app

        NavigationStack {
            Form {
                Section("Sound") {
                    Picker("Sound set", selection: Binding(
                        get: { state.currentSoundSet.name },
                        set: { name in
                            if let sound = app.soundSets.first(where: { $0.name == name }) {
                                app.changeSoundSet(soundSet: sound)
                            }
                        }
                    )) {
                        ForEach(app.soundSets, id: \.name) { sound in
                            VStack(alignment: .leading) {
                                Text(sound.displayName)
                                Text(app.soundSetSummary(soundSet: sound))
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                            .tag(sound.name)
                        }
                    }
                    .pickerStyle(.navigationLink)

                    VStack(alignment: .leading) {
                        HStack {
                            Text("Pitch")
                            Spacer()
                            Text(String(format: "%.2f×", state.pitchMultiplier))
                                .monospacedDigit()
                                .foregroundStyle(.secondary)
                        }
                        Slider(
                            value: Binding(
                                get: { Double(state.pitchMultiplier) },
                                set: { app.changePitch(pitch: Float($0)) }
                            ),
                            in: 0.5...2.0,
                            step: 0.05
                        )
                    }
                    Text("Loudness follows the iPhone volume buttons.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }

                Section("Feedback") {
                    Toggle("Screen flash", isOn: Binding(
                        get: { state.isFlashEnabled },
                        set: { _ in app.toggleFlash() }
                    ))
                    Toggle("Vibration", isOn: Binding(
                        get: { state.isVibrationEnabled },
                        set: { _ in app.toggleVibration() }
                    ))
                }

                Section {
                    OffsetControl(
                        title: "Visual & screen flash",
                        value: Int(state.flashDelayMs),
                        range: Int(app.minOffsetMs)...Int(app.maxOffsetMs),
                        onChange: { app.changeFlashDelay(delayMs: Int32($0)) }
                    )
                    OffsetControl(
                        title: "Vibration",
                        value: Int(state.vibrationDelayMs),
                        range: Int(app.minOffsetMs)...Int(app.maxOffsetMs),
                        onChange: { app.changeVibrationDelay(delayMs: Int32($0)) }
                    )
                    OffsetControl(
                        title: "Bluetooth audio",
                        value: Int(state.bluetoothCompensationMs),
                        range: Int(app.minBluetoothOffsetMs)...Int(app.maxBluetoothOffsetMs),
                        onChange: { app.changeBluetoothCompensation(offsetMs: Int32($0)) }
                    )
                    Button("Reset all offsets to 0 ms") { app.resetTimingOffsets() }
                } header: {
                    Text("Calibration")
                } footer: {
                    Text("Negative values fire visuals or vibration earlier than the click, positive values later. The Bluetooth offset applies only while Bluetooth audio is active.")
                }

                Section("Appearance") {
                    Picker("Theme", selection: Binding(
                        get: { state.themeMode.name },
                        set: { name in
                            if let mode = app.themeModes.first(where: { $0.name == name }) {
                                app.changeThemeMode(mode: mode)
                            }
                        }
                    )) {
                        ForEach(app.themeModes, id: \.name) { mode in
                            Text(mode.displayName).tag(mode.name)
                        }
                    }
                    .pickerStyle(.segmented)

                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 12) {
                            ForEach(app.accentColors, id: \.name) { option in
                                Button {
                                    app.changeAccentColor(accent: option)
                                } label: {
                                    VStack(spacing: 4) {
                                        Circle()
                                            .fill(option.swiftUIColor)
                                            .frame(width: 36, height: 36)
                                            .overlay(
                                                Circle().stroke(Color.primary, lineWidth: option == state.accentColor ? 3 : 0)
                                            )
                                        Text(option.displayName)
                                            .font(.caption2)
                                            .lineLimit(1)
                                    }
                                    .frame(minWidth: 64, minHeight: 48)
                                }
                                .buttonStyle(.plain)
                            }
                        }
                        .padding(.vertical, 4)
                    }
                }

                Section("Controls") {
                    Picker("Play & Tap trigger", selection: Binding(
                        get: { state.buttonTriggerMode.name },
                        set: { name in
                            if let mode = app.buttonTriggerModes.first(where: { $0.name == name }) {
                                app.changeButtonTriggerMode(mode: mode)
                            }
                        }
                    )) {
                        ForEach(app.buttonTriggerModes, id: \.name) { mode in
                            Text(mode.shortLabel).tag(mode.name)
                        }
                    }
                    Text(app.buttonTriggerModeSummary(mode: state.buttonTriggerMode))
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }

                Section {
                    LabeledContent("Current output", value: state.audioInfo.deviceName)
                    LabeledContent("Sample rate", value: "\(state.audioInfo.sampleRate) Hz")
                    Button("Refresh output") { app.refreshAudioRoute() }
                } header: {
                    Text("Audio output")
                } footer: {
                    Text("Choose speakers, headphones, or AirPlay from Control Center. All outputs are listed automatically.")
                }

                Section("About") {
                    LabeledContent("Anchor Metronome", value: "v\(app.versionName)")
                    Text("Rock-solid time for serious play")
                        .foregroundStyle(.secondary)
                    if state.canCheckForUpdates {
                        Button(state.isCheckingForUpdates ? "Checking…" : "Check for updates") {
                            app.checkForUpdates()
                        }
                        .disabled(state.isCheckingForUpdates)
                        if let message = state.updateCheckMessage {
                            Text(message)
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                        if let release = state.availableUpdate {
                            Button("View release \(release.tagName)") { app.acceptUpdate() }
                        }
                    } else {
                        Text("Updates are delivered automatically by \(state.updateStoreName ?? "the App Store").")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }
            }
            .navigationTitle("Settings")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
        }
        .tint(state.accentColor.swiftUIColor)
        .preferredColorScheme(state.themeMode.colorScheme)
    }
}

/// Offset slider (-200 ms ... +300 ms) with fine-tuning step buttons (-10, -1, 0, +1, +10 ms).
private struct OffsetControl: View {
    let title: String
    let value: Int
    let range: ClosedRange<Int>
    let onChange: (Int) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text(title)
                Spacer()
                Text("\(value > 0 ? "+" : "")\(value) ms")
                    .monospacedDigit()
                    .foregroundStyle(.secondary)
            }
            Slider(
                value: Binding(
                    get: { Double(value) },
                    set: { onChange(Int($0.rounded())) }
                ),
                in: Double(range.lowerBound)...Double(range.upperBound),
                step: 1
            )
            HStack(spacing: 6) {
                step("-10") { onChange(clamp(value - 10)) }
                step("-1") { onChange(clamp(value - 1)) }
                step("0") { onChange(0) }
                step("+1") { onChange(clamp(value + 1)) }
                step("+10") { onChange(clamp(value + 10)) }
            }
        }
        .padding(.vertical, 4)
    }

    private func clamp(_ newValue: Int) -> Int {
        min(max(newValue, range.lowerBound), range.upperBound)
    }

    private func step(_ label: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(label)
                .font(.caption.weight(.semibold).monospacedDigit())
                .frame(maxWidth: .infinity, minHeight: 44)
                .background(RoundedRectangle(cornerRadius: 8).fill(Color(.tertiarySystemFill)))
        }
        .buttonStyle(.borderless)
    }
}
