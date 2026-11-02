package com.damiongans.anchor.repository

import com.damiongans.anchor.model.Playlist
import com.damiongans.anchor.model.Song

/**
 * Persistence contract for the song library and playlists.
 */
interface ISongLibraryRepository {
    fun getSongs(): List<Song>
    fun setSongs(songs: List<Song>)

    fun getPlaylists(): List<Playlist>
    fun setPlaylists(playlists: List<Playlist>)

    fun getActivePlaylistId(): String?
    fun setActivePlaylistId(id: String?)
}

/**
 * Non-persistent [ISongLibraryRepository] (unit tests, previews).
 */
class InMemorySongLibraryRepository : ISongLibraryRepository {
    private var songs: List<Song> = emptyList()
    private var playlists: List<Playlist> = emptyList()
    private var activePlaylistId: String? = null

    override fun getSongs(): List<Song> = songs
    override fun setSongs(songs: List<Song>) { this.songs = songs }
    override fun getPlaylists(): List<Playlist> = playlists
    override fun setPlaylists(playlists: List<Playlist>) { this.playlists = playlists }
    override fun getActivePlaylistId(): String? = activePlaylistId
    override fun setActivePlaylistId(id: String?) { activePlaylistId = id }
}
