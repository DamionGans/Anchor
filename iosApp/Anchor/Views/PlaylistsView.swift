import AnchorShared
import SwiftUI

/// Playlists & Song Library sheet (mirrors `PlaylistsBottomSheet` in `sharedUi`).
struct PlaylistsView: View {
    @EnvironmentObject private var store: MetronomeStore
    @Environment(\.dismiss) private var dismiss

    @State private var selectedTab: Int = 0
    @State private var newPlaylistName: String = ""
    @State private var quickSongName: String = ""
    @State private var showCustomSongCreator: Bool = false
    @State private var editingSongId: String? = nil
    @State private var renamingPlaylistId: String? = nil
    @State private var renamePlaylistDraft: String = ""

    var body: some View {
        let state = store.state
        let app = store.app
        let accent = state.accentColor.swiftUIColor

        NavigationStack {
            VStack(spacing: 0) {
                Picker("View", selection: $selectedTab) {
                    Text(state.playlists.isEmpty ? "Playlists" : "Playlists (\(state.playlists.count))")
                        .tag(0)
                    Text(state.songs.isEmpty ? "Song Library" : "Song Library (\(state.songs.count))")
                        .tag(1)
                }
                .pickerStyle(.segmented)
                .padding(.horizontal, 16)
                .padding(.vertical, 10)

                if selectedTab == 0 {
                    PlaylistsTabSection(
                        state: state,
                        app: app,
                        accent: accent,
                        newPlaylistName: $newPlaylistName,
                        renamingPlaylistId: $renamingPlaylistId,
                        renamePlaylistDraft: $renamePlaylistDraft,
                        onOpenLibrary: { selectedTab = 1 }
                    )
                } else {
                    LibraryTabSection(
                        state: state,
                        app: app,
                        accent: accent,
                        quickSongName: $quickSongName,
                        showCustomSongCreator: $showCustomSongCreator,
                        editingSongId: $editingSongId
                    )
                }
            }
            .navigationTitle("Playlists & Library")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
        }
        .tint(accent)
    }
}

private struct PlaylistsTabSection: View {
    let state: MetronomeUiState
    let app: AnchorIosApp
    let accent: Color
    @Binding var newPlaylistName: String
    @Binding var renamingPlaylistId: String?
    @Binding var renamePlaylistDraft: String
    let onOpenLibrary: () -> Void

    var body: some View {
        let songsById = Dictionary(uniqueKeysWithValues: state.songs.map { ($0.id, $0) })
        let activeIndex = state.activeSongIndex?.intValue

        Form {
            Section("Create Playlist") {
                HStack(spacing: 10) {
                    TextField("New playlist / setlist name…", text: $newPlaylistName)
                    Button("Create") {
                        let trimmed = newPlaylistName.trimmingCharacters(in: .whitespacesAndNewlines)
                        if !trimmed.isEmpty {
                            _ = app.createPlaylist(name: trimmed)
                            newPlaylistName = ""
                        }
                    }
                    .buttonStyle(.borderedProminent)
                    .disabled(newPlaylistName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
            }

            if let activePlaylist = state.activePlaylist {
                Section("Active Setlist") {
                    HStack {
                        Label(activePlaylist.name, systemImage: "music.note.list")
                            .font(.subheadline.weight(.bold))
                        Spacer()
                        Button("Clear") {
                            app.clearActivePlaylist()
                        }
                        .font(.caption.weight(.semibold))
                    }
                }
            }

            if state.playlists.isEmpty {
                Section {
                    VStack(alignment: .leading, spacing: 6) {
                        Text("No playlists yet")
                            .font(.subheadline.weight(.bold))
                        Text("Create a playlist above, then add songs from your Song Library to step through them on the main screen.")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                    .padding(.vertical, 4)
                }
            } else {
                ForEach(state.playlists, id: \.id) { playlist in
                    let isActive = playlist.id == state.activePlaylistId

                    Section {
                        if renamingPlaylistId == playlist.id {
                            HStack {
                                TextField("Playlist name", text: $renamePlaylistDraft)
                                Button("Save") {
                                    let trimmed = renamePlaylistDraft.trimmingCharacters(in: .whitespacesAndNewlines)
                                    if !trimmed.isEmpty {
                                        app.renamePlaylist(playlistId: playlist.id, name: trimmed)
                                    }
                                    renamingPlaylistId = nil
                                }
                                .buttonStyle(.borderedProminent)
                                Button("Cancel", role: .cancel) {
                                    renamingPlaylistId = nil
                                }
                            }
                        } else {
                            HStack {
                                Button {
                                    app.selectPlaylist(playlistId: playlist.id)
                                } label: {
                                    HStack(spacing: 6) {
                                        Image(systemName: isActive ? "checkmark.circle.fill" : "circle")
                                            .foregroundStyle(isActive ? accent : .secondary)
                                        Text(isActive ? "Active Setlist" : "Select Setlist")
                                            .font(.subheadline.weight(.semibold))
                                    }
                                }
                                .buttonStyle(.plain)

                                Spacer()

                                Button {
                                    renamePlaylistDraft = playlist.name
                                    renamingPlaylistId = playlist.id
                                } label: {
                                    Image(systemName: "pencil")
                                        .frame(minWidth: 36, minHeight: 36)
                                }
                                .buttonStyle(.borderless)

                                Button(role: .destructive) {
                                    app.deletePlaylist(playlistId: playlist.id)
                                } label: {
                                    Image(systemName: "trash")
                                        .frame(minWidth: 36, minHeight: 36)
                                }
                                .buttonStyle(.borderless)
                            }
                        }

                        if playlist.songIds.isEmpty {
                            Text("No songs in this playlist yet.")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        } else {
                            ForEach(Array(playlist.songIds.enumerated()), id: \.offset) { idx, songId in
                                if let song = songsById[songId] {
                                    let isCurrent = isActive && activeIndex == idx
                                    HStack(spacing: 10) {
                                        Button {
                                            if !isActive {
                                                app.selectPlaylist(playlistId: playlist.id)
                                            }
                                            app.selectPlaylistSong(index: Int32(idx))
                                        } label: {
                                            HStack(spacing: 8) {
                                                Text("\(idx + 1).")
                                                    .font(.subheadline.weight(.bold))
                                                    .monospacedDigit()
                                                    .foregroundStyle(isCurrent ? accent : .secondary)

                                                VStack(alignment: .leading, spacing: 2) {
                                                    Text(song.name)
                                                        .font(.subheadline.weight(isCurrent ? .bold : .semibold))
                                                        .foregroundStyle(isCurrent ? accent : .primary)
                                                    Text("\(song.bpm) BPM • \(song.timeSignatureDisplay)")
                                                        .font(.caption)
                                                        .monospacedDigit()
                                                        .foregroundStyle(.secondary)
                                                }
                                            }
                                        }
                                        .buttonStyle(.plain)

                                        Spacer()

                                        Button {
                                            app.moveSongInPlaylist(
                                                playlistId: playlist.id,
                                                fromIndex: Int32(idx),
                                                toIndex: Int32(idx - 1)
                                            )
                                        } label: {
                                            Image(systemName: "arrow.up")
                                                .frame(minWidth: 32, minHeight: 32)
                                        }
                                        .buttonStyle(.borderless)
                                        .disabled(idx == 0)

                                        Button {
                                            app.moveSongInPlaylist(
                                                playlistId: playlist.id,
                                                fromIndex: Int32(idx),
                                                toIndex: Int32(idx + 1)
                                            )
                                        } label: {
                                            Image(systemName: "arrow.down")
                                                .frame(minWidth: 32, minHeight: 32)
                                        }
                                        .buttonStyle(.borderless)
                                        .disabled(idx >= playlist.songIds.count - 1)

                                        Button(role: .destructive) {
                                            app.removeSongFromPlaylist(playlistId: playlist.id, index: Int32(idx))
                                        } label: {
                                            Image(systemName: "xmark.circle")
                                                .frame(minWidth: 32, minHeight: 32)
                                        }
                                        .buttonStyle(.borderless)
                                    }
                                }
                            }
                        }

                        if state.songs.isEmpty {
                            Button("Open Song Library to create songs") {
                                onOpenLibrary()
                            }
                            .font(.caption.weight(.semibold))
                        } else {
                            Menu {
                                ForEach(state.songs, id: \.id) { song in
                                    Button {
                                        app.addSongToPlaylist(playlistId: playlist.id, songId: song.id)
                                    } label: {
                                        Text("\(song.name) (\(song.bpm) BPM • \(song.timeSignatureDisplay))")
                                    }
                                }
                            } label: {
                                Label("Add song from library…", systemImage: "plus.circle")
                                    .font(.caption.weight(.semibold))
                            }
                        }
                    } header: {
                        Text("\(playlist.name) (\(playlist.songIds.count))")
                            .monospacedDigit()
                    }
                }
            }
        }
    }
}

private struct LibraryTabSection: View {
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

private struct SongEditorFormView: View {
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
