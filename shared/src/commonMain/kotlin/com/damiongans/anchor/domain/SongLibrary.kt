package com.damiongans.anchor.domain

import com.damiongans.anchor.model.Playlist
import com.damiongans.anchor.model.Song
import com.damiongans.anchor.repository.ISettingsRepository
import kotlin.random.Random

/**
 * Immutable song library plus the set-list position (active playlist, loaded song and its
 * index in that playlist).
 *
 * Every edit is a pure function returning a new library, so the rules (cascading deletes,
 * keeping the set-list position on the loaded song through reorders) are tested without a
 * controller, audio engine or storage. `MetronomeController` applies the result, persists it
 * and loads songs onto the metronome.
 */
data class SongLibrary(
    val songs: List<Song> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    /** Playlist the set-list bar steps through, or null when none is selected. */
    val activePlaylistId: String? = null,
    /** Position of the loaded song inside the active playlist, or null. */
    val activeSongIndex: Int? = null,
    /** Library song whose settings were last loaded or saved, or null. */
    val activeSongId: String? = null
) {
    val activePlaylist: Playlist?
        get() = playlist(activePlaylistId)

    val activeSong: Song?
        get() = song(activeSongId)

    fun song(id: String?): Song? = songs.firstOrNull { it.id == id }

    fun playlist(id: String?): Playlist? = playlists.firstOrNull { it.id == id }

    // --- Songs ---

    fun addSong(song: Song): SongLibrary = copy(songs = songs + song)

    /** Replaces the song with the same id; unknown ids are ignored. */
    fun replaceSong(song: Song): SongLibrary =
        if (song(song.id) == null) this else copy(songs = songs.map { if (it.id == song.id) song else it })

    /** Removes a song from the library and from every playlist; clears it if it is loaded. */
    fun deleteSong(songId: String): SongLibrary {
        if (song(songId) == null) return this
        val pruned = copy(
            songs = songs.filter { it.id != songId },
            playlists = playlists.map { p -> p.copy(songIds = p.songIds.filter { it != songId }) }
        )
        return if (activeSongId == songId) {
            pruned.copy(activeSongId = null, activeSongIndex = null)
        } else {
            pruned.withActiveSong(activeSongId)
        }
    }

    // --- Playlists ---

    fun addPlaylist(playlist: Playlist): SongLibrary = copy(playlists = playlists + playlist)

    fun renamePlaylist(playlistId: String, name: String): SongLibrary =
        editPlaylist(playlistId) { it.copy(name = name.trim()) }

    /** Removes a playlist; clears the set-list position if it was the active one. */
    fun deletePlaylist(playlistId: String): SongLibrary {
        if (playlist(playlistId) == null) return this
        val remaining = copy(playlists = playlists.filter { it.id != playlistId })
        return if (activePlaylistId == playlistId) remaining.withActivePlaylist(null) else remaining
    }

    /** Appends a library song to a playlist (a song may appear more than once). */
    fun addToPlaylist(playlistId: String, songId: String): SongLibrary =
        if (song(songId) == null) this else editPlaylist(playlistId) { it.copy(songIds = it.songIds + songId) }

    /**
     * Removes the entry at [index]. Removing the loaded entry keeps its settings live but clears
     * the set-list position; removing an earlier entry shifts the position down.
     */
    fun removeFromPlaylist(playlistId: String, index: Int): SongLibrary {
        val playlist = playlist(playlistId) ?: return this
        if (index !in playlist.songIds.indices) return this
        val edited = editPlaylist(playlistId) { p -> p.copy(songIds = p.songIds.filterIndexed { i, _ -> i != index }) }
        val current = activeSongIndex
        if (playlistId != activePlaylistId || current == null) return edited
        val shifted = when {
            index == current -> null
            index < current -> current - 1
            else -> current
        }
        return edited.copy(activeSongIndex = shifted)
    }

    /** Moves the entry at [fromIndex] to [toIndex]; the set-list position follows the loaded entry. */
    fun moveInPlaylist(playlistId: String, fromIndex: Int, toIndex: Int): SongLibrary {
        val playlist = playlist(playlistId) ?: return this
        if (fromIndex !in playlist.songIds.indices || toIndex !in playlist.songIds.indices) return this
        val edited = editPlaylist(playlistId) { p ->
            val ids = p.songIds.toMutableList()
            ids.add(toIndex, ids.removeAt(fromIndex))
            p.copy(songIds = ids)
        }
        val current = activeSongIndex
        if (playlistId != activePlaylistId || current == null) return edited
        val moved = when {
            current == fromIndex -> toIndex
            fromIndex < current && toIndex >= current -> current - 1
            fromIndex > current && toIndex <= current -> current + 1
            else -> current
        }
        return edited.copy(activeSongIndex = moved)
    }

    // --- Set-list position ---

    /** Selects a playlist (or none) without loading anything; the position starts empty. */
    fun withActivePlaylist(playlistId: String?): SongLibrary =
        copy(activePlaylistId = playlist(playlistId)?.id, activeSongIndex = null)

    /**
     * Marks [songId] as loaded and points the set-list position at it: [index] when given and it
     * matches, otherwise the current position if it still holds the song, otherwise its first
     * occurrence in the active playlist (null when the song is not in it).
     */
    fun withActiveSong(songId: String?, index: Int? = null): SongLibrary {
        if (songId == null) return copy(activeSongId = null, activeSongIndex = null)
        val ids = activePlaylist?.songIds.orEmpty()
        val position = when {
            index != null && ids.getOrNull(index) == songId -> index
            activeSongIndex != null && ids.getOrNull(activeSongIndex) == songId -> activeSongIndex
            else -> ids.indexOf(songId).takeIf { it >= 0 }
        }
        return copy(activeSongId = songId, activeSongIndex = position)
    }

    /** Drops playlist entries and the active playlist that point at nothing (e.g. after a corrupt load). */
    fun pruned(): SongLibrary {
        val ids = songs.mapTo(HashSet()) { it.id }
        val cleaned = copy(playlists = playlists.map { p -> p.copy(songIds = p.songIds.filter { it in ids }) })
        return if (cleaned.playlist(activePlaylistId) == null) cleaned.withActivePlaylist(null) else cleaned
    }

    /** A short random id not used by any song or playlist in this library. */
    fun newId(random: Random = Random.Default): String {
        val taken = songs.mapTo(HashSet()) { it.id } + playlists.map { it.id }
        while (true) {
            val id = random.nextLong().toULong().toString(36)
            if (id !in taken) return id
        }
    }

    companion object {
        /** Trims the name, clamps bpm (20..300) and beats (1..16), and sizes accents to the beats. */
        fun normalize(song: Song): Song {
            val beats = song.totalBeats.coerceIn(1, ISettingsRepository.MAX_BEATS)
            val defaults = ISettingsRepository.defaultAccentsForBeats(beats)
            return song.copy(
                name = song.name.trim(),
                bpm = song.bpm.coerceIn(20, 300),
                totalBeats = beats,
                accents = List(beats) { i -> song.accents.getOrNull(i) ?: defaults[i] }
            )
        }
    }

    private inline fun editPlaylist(playlistId: String, transform: (Playlist) -> Playlist): SongLibrary =
        if (playlist(playlistId) == null) this else copy(playlists = playlists.map { if (it.id == playlistId) transform(it) else it })
}
