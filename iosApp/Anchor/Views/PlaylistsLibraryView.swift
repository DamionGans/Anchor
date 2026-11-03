import AnchorShared
import SwiftUI

struct LibraryTabSection: View {
    let state: MetronomeUiState
    let app: AnchorIosApp
    let accent: Color
    @Binding var quickSongName: String
    @Binding var showCustomSongCreator: Bool
    @Binding var editingSongId: String?

    var body: some View {
        Form {
            Section {
                VStack(alignment: .leading, spacing: 8) {
                    HStack {
                        Text("Current: \(state.bpm) BPM • \(state.totalBeats)/\(state.timeSignatureDenominator) (\(state.currentSubdivision.displayName))")
                            .font(.caption.weight(.semibold))
                            .monospacedDigit()
                            .foregroundStyle(accent)
                        Spacer()
                        Button(showCustomSongCreator ? "Quick Save" : "Custom Editor") {
                            showCustomSongCreator.toggle()
                        }
                        .font(.caption.weight(.semibold))
                    }

                    if !showCustomSongCreator {
                        HStack(spacing: 10) {
                            TextField("Song title…", text: $quickSongName)
                            Button("Save") {
                                let trimmed = quickSongName.trimmingCharacters(in: .whitespacesAndNewlines)
                                if !trimmed.isEmpty {
                                    _ = app.saveCurrentAsSong(name: trimmed)
                                    quickSongName = ""
                                }
                            }
                            .buttonStyle(.borderedProminent)
                            .disabled(quickSongName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                        }
                    } else {
                        SongEditorFormView(
                            app: app,
                            accent: accent,
                            initialName: quickSongName,
                            initialBpm: Int(state.bpm),
                            initialBeats: Int(state.totalBeats),
                            initialSubdivision: state.currentSubdivision,
                            initialAccents: state.accents,
                            confirmTitle: "Add to Library",
                            onConfirm: { name, bpm, beats, sub, accents in
                                _ = app.createSong(
                                    name: name,
                                    bpm: Int32(bpm),
                                    totalBeats: Int32(beats),
                                    subdivision: sub,
                                    accents: accents
                                )
                                quickSongName = ""
                                showCustomSongCreator = false
                            },
                            onCancel: {
                                showCustomSongCreator = false
                            }
                        )
                    }
                }
            } header: {
                Text("Save Current Metronome as Song")
            }

            if let activeSong = state.activeSong, state.isActiveSongModified {
                Section("Unsaved Changes") {
                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("'\(activeSong.name)' modified")
                                .font(.subheadline.weight(.bold))
                            Text("Stored: \(activeSong.bpm) BPM • \(activeSong.timeSignatureDisplay)")
                                .font(.caption)
                                .monospacedDigit()
                                .foregroundStyle(.secondary)
                        }
                        Spacer()
                        Button("Update Song") {
                            app.updateSongFromCurrent(songId: activeSong.id)
                        }
                        .buttonStyle(.borderedProminent)
                    }
                }
            }

            Section("Songs (\(state.songs.count))") {
                if state.songs.isEmpty {
                    Text("No songs in your library yet. Save the current metronome settings above or use the Custom Editor.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                } else {
                    ForEach(state.songs, id: \.id) { song in
                        let isLoaded = song.id == state.activeSongId

                        if editingSongId == song.id {
                            SongEditorFormView(
                                app: app,
                                accent: accent,
                                initialName: song.name,
                                initialBpm: Int(song.bpm),
                                initialBeats: Int(song.totalBeats),
                                initialSubdivision: song.subdivision,
                                initialAccents: song.accents,
                                confirmTitle: "Save Changes",
                                onConfirm: { name, bpm, beats, sub, accents in
                                    app.updateSong(
                                        songId: song.id,
                                        name: name,
                                        bpm: Int32(bpm),
                                        totalBeats: Int32(beats),
                                        subdivision: sub,
                                        accents: accents
                                    )
                                    editingSongId = nil
                                },
                                onCancel: {
                                    editingSongId = nil
                                }
                            )
                        } else {
                            HStack(spacing: 10) {
                                Button {
                                    app.loadSong(songId: song.id)
                                } label: {
                                    VStack(alignment: .leading, spacing: 3) {
                                        HStack(spacing: 6) {
                                            Text(song.name)
                                                .font(.subheadline.weight(isLoaded ? .bold : .semibold))
                                                .foregroundStyle(isLoaded ? accent : .primary)
                                            if isLoaded {
                                                Text("LOADED")
                                                    .font(.system(size: 9, weight: .heavy))
                                                    .padding(.horizontal, 5)
                                                    .padding(.vertical, 2)
                                                    .background(accent.opacity(0.2), in: Capsule())
                                                    .foregroundStyle(accent)
                                            }
                                        }
                                        Text("\(song.bpm) BPM • \(song.timeSignatureDisplay) (\(song.subdivision.displayName))")
                                            .font(.caption)
                                            .monospacedDigit()
                                            .foregroundStyle(.secondary)
                                    }
                                }
                                .buttonStyle(.plain)

                                Spacer()

                                if let activePlaylist = state.activePlaylist {
                                    Button {
                                        app.addSongToPlaylist(playlistId: activePlaylist.id, songId: song.id)
                                    } label: {
                                        Image(systemName: "plus.circle")
                                            .frame(minWidth: 34, minHeight: 34)
                                    }
                                    .buttonStyle(.borderless)
                                }

                                Button {
                                    editingSongId = song.id
                                } label: {
                                    Image(systemName: "pencil")
                                            .frame(minWidth: 34, minHeight: 34)
                                }
                                .buttonStyle(.borderless)

                                Button(role: .destructive) {
                                    app.deleteSong(songId: song.id)
                                } label: {
                                    Image(systemName: "trash")
                                        .frame(minWidth: 34, minHeight: 34)
                                }
                                .buttonStyle(.borderless)
                            }
                        }
                    }
                }
            }
        }
    }
}

struct SongEditorFormView: View {
    let app: AnchorIosApp
    let accent: Color
    @State private var name: String
    @State private var bpm: Int
    @State private var beats: Int
    @State private var subdivision: Subdivision
    @State private var accents: [BeatAccent]
    let confirmTitle: String
    let onConfirm: (String, Int, Int, Subdivision, [BeatAccent]) -> Void
    let onCancel: () -> Void

    init(
        app: AnchorIosApp,
        accent: Color,
        initialName: String,
        initialBpm: Int,
        initialBeats: Int,
        initialSubdivision: Subdivision,
        initialAccents: [BeatAccent],
        confirmTitle: String,
        onConfirm: @escaping (String, Int, Int, Subdivision, [BeatAccent]) -> Void,
        onCancel: @escaping () -> Void
    ) {
        self.app = app
        self.accent = accent
        _name = State(initialValue: initialName)
        _bpm = State(initialValue: min(max(initialBpm, Int(app.minBpm)), Int(app.maxBpm)))
        let clampedBeats = min(max(initialBeats, 1), Int(app.maxBeats))
        _beats = State(initialValue: clampedBeats)
        _subdivision = State(initialValue: initialSubdivision)
        _accents = State(initialValue: Self.resizeAccents(initialAccents, count: clampedBeats))
        self.confirmTitle = confirmTitle
        self.onConfirm = onConfirm
        self.onCancel = onCancel
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            TextField("Song Title", text: $name)

            Stepper(value: $bpm, in: Int(app.minBpm)...Int(app.maxBpm)) {
                Text("Tempo: \(bpm) BPM")
                    .font(.subheadline.weight(.semibold))
                    .monospacedDigit()
            }

            Stepper(
                value: Binding(
                    get: { beats },
                    set: { newBeats in
                        beats = newBeats
                        accents = Self.resizeAccents(accents, count: newBeats)
                    }
                ),
                in: 1...Int(app.maxBeats)
            ) {
                let denom = subdivision.name == Subdivision.eighth.name ? 8 : 4
                Text("Meter: \(beats)/\(denom)")
                    .font(.subheadline.weight(.semibold))
                    .monospacedDigit()
            }

            Picker("Subdivision", selection: Binding(
                get: { subdivision.name },
                set: { name in
                    if let sub = app.subdivisions.first(where: { $0.name == name }) {
                        subdivision = sub
                    }
                }
            )) {
                ForEach(app.subdivisions, id: \.name) { sub in
                    Text("\(sub.symbol) \(sub.displayName)").tag(sub.name)
                }
            }
            .pickerStyle(.segmented)

            VStack(alignment: .leading, spacing: 6) {
                Text("Beat Accents (tap to cycle)")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 6) {
                        ForEach(0..<accents.count, id: \.self) { idx in
                            let item = accents[idx]
                            Button {
                                var copy = accents
                                copy[idx] = item.next()
                                accents = copy
                            } label: {
                                VStack(spacing: 2) {
                                    Text("\(idx + 1)")
                                        .font(.caption.weight(.bold))
                                        .monospacedDigit()
                                    Text(item.label)
                                        .font(.system(size: 9))
                                }
                                .padding(.horizontal, 8)
                                .padding(.vertical, 6)
                                .background(
                                    RoundedRectangle(cornerRadius: 8)
                                        .fill(accent.opacity(0.16))
                                )
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
            }

            HStack {
                Button("Cancel", role: .cancel) {
                    onCancel()
                }
                Spacer()
                Button(confirmTitle) {
                    let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
                    if !trimmed.isEmpty {
                        onConfirm(trimmed, bpm, beats, subdivision, accents)
                    }
                }
                .buttonStyle(.borderedProminent)
                .disabled(name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            }
        }
        .padding(.vertical, 4)
    }

    private static func resizeAccents(_ current: [BeatAccent], count: Int) -> [BeatAccent] {
        (0..<count).map { idx in
            if idx < current.count {
                return current[idx]
            }
            return idx == 0 ? BeatAccent.accent : BeatAccent.regular
        }
    }
}
