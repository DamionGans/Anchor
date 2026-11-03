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
