package com.damiongans.anchor.repository

import com.damiongans.anchor.model.BeatAccent
import com.damiongans.anchor.model.Playlist
import com.damiongans.anchor.model.Song
import com.damiongans.anchor.model.Subdivision

/**
 * Shared [ISongLibraryRepository] on top of an [IKeyValueStore].
 *
 * Format: one record per line, fields separated by `|`, text fields percent-escaped so names
 * may contain any character. Values are split into chunks because some stores cap string
 * length (Windows `java.util.prefs`: 8192 chars). Unreadable records are skipped.
 */
class KeyValueSongLibraryRepository(private val store: IKeyValueStore) : ISongLibraryRepository {

    override fun getSongs(): List<Song> = readChunked(KEY_SONGS).lines().mapNotNull(::parseSong)

    override fun setSongs(songs: List<Song>) =
        writeChunked(KEY_SONGS, songs.joinToString("\n", transform = ::formatSong))

    override fun getPlaylists(): List<Playlist> = readChunked(KEY_PLAYLISTS).lines().mapNotNull(::parsePlaylist)

    override fun setPlaylists(playlists: List<Playlist>) =
        writeChunked(KEY_PLAYLISTS, playlists.joinToString("\n", transform = ::formatPlaylist))

    override fun getActivePlaylistId(): String? = store.getString(KEY_ACTIVE_PLAYLIST)?.takeIf { it.isNotEmpty() }

    override fun setActivePlaylistId(id: String?) = store.putString(KEY_ACTIVE_PLAYLIST, id ?: "")

    private fun formatSong(song: Song): String = listOf(
        escape(song.id),
        escape(song.name),
        song.bpm.toString(),
        song.totalBeats.toString(),
        song.subdivision.name,
        song.accents.joinToString(",") { it.name }
    ).joinToString("|")

    private fun parseSong(line: String): Song? {
        val f = line.split("|")
        if (f.size != 6) return null
        val subdivision = Subdivision.entries.firstOrNull { it.name == f[4] } ?: return null
        val accents = if (f[5].isEmpty()) emptyList() else f[5].split(",").map { token ->
            BeatAccent.entries.firstOrNull { it.name == token } ?: return null
        }
        return normalizeSong(
            Song(
                id = unescape(f[0]).ifEmpty { return null },
                name = unescape(f[1]),
                bpm = f[2].toIntOrNull() ?: return null,
                totalBeats = f[3].toIntOrNull() ?: return null,
                subdivision = subdivision,
                accents = accents
            )
        )
    }

    private fun formatPlaylist(playlist: Playlist): String = listOf(
        escape(playlist.id),
        escape(playlist.name),
        playlist.songIds.joinToString(",", transform = ::escape)
    ).joinToString("|")

    private fun parsePlaylist(line: String): Playlist? {
        val f = line.split("|")
        if (f.size != 3) return null
        return Playlist(
            id = unescape(f[0]).ifEmpty { return null },
            name = unescape(f[1]),
            songIds = if (f[2].isEmpty()) emptyList() else f[2].split(",").map(::unescape)
        )
    }

    private fun readChunked(key: String): String {
        val chunks = store.getInt("${key}_chunks", 0)
        if (chunks <= 0) return ""
        return buildString { for (i in 0 until chunks) append(store.getString("${key}_$i") ?: "") }
    }

    private fun writeChunked(key: String, value: String) {
        val parts = value.chunked(CHUNK_LENGTH)
        parts.forEachIndexed { i, part -> store.putString("${key}_$i", part) }
        // Count last, so an interrupted write never points past the chunks just stored.
        store.putInt("${key}_chunks", parts.size)
    }

    companion object {
        const val KEY_SONGS = "song_library_v1"
        const val KEY_PLAYLISTS = "playlists_v1"
        const val KEY_ACTIVE_PLAYLIST = "active_playlist_id"
        private const val CHUNK_LENGTH = 4000

        /** Clamps a song to the ranges the metronome supports and sizes its accents to its beats. */
        fun normalizeSong(song: Song): Song {
            val beats = song.totalBeats.coerceIn(1, ISettingsRepository.MAX_BEATS)
            val defaults = ISettingsRepository.defaultAccentsForBeats(beats)
            return song.copy(
                name = song.name.trim(),
                bpm = song.bpm.coerceIn(20, 300),
                totalBeats = beats,
                accents = List(beats) { i -> song.accents.getOrNull(i) ?: defaults[i] }
            )
        }

        private fun escape(text: String): String = buildString {
            for (c in text) {
                when (c) {
                    '%' -> append("%25")
                    '|' -> append("%7C")
                    ',' -> append("%2C")
                    '\n' -> append("%0A")
                    '\r' -> append("%0D")
                    else -> append(c)
                }
            }
        }

        private fun unescape(text: String): String = text
            .replace("%0D", "\r")
            .replace("%0A", "\n")
            .replace("%2C", ",")
            .replace("%7C", "|")
            .replace("%25", "%")
    }
}
