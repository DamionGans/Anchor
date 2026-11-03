package com.damiongans.anchor.repository

import com.damiongans.anchor.model.BeatAccent
import com.damiongans.anchor.model.Playlist
import com.damiongans.anchor.model.Song
import com.damiongans.anchor.model.Subdivision
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class KeyValueSongLibraryRepositoryTest {

    /** SPEC-PLAYLIST-006 */
    @Test
    fun roundTripsNamesWithSeparatorsAndEscapes() {
        val store = InMemoryKeyValueStore()
        val names = listOf("Take | Five", "A, B\nC", "100% %7C %25 %2C", "")
        val songs = names.mapIndexed { i, name ->
            Song("id$i", name, 100 + i, 5, Subdivision.TRIPLET, List(5) { BeatAccent.entries[it % 4] })
        }
        val playlists = listOf(Playlist("p1", "Set | 1, %2C", songs.map { it.id }))

        KeyValueSongLibraryRepository(store).apply {
            setSongs(songs)
            setPlaylists(playlists)
            setActivePlaylistId("p1")
        }
        val restored = KeyValueSongLibraryRepository(store)

        assertEquals(songs, restored.getSongs())
        assertEquals(playlists, restored.getPlaylists())
        assertEquals("p1", restored.getActivePlaylistId())
    }

    /** SPEC-PLAYLIST-006 */
    @Test
    fun largeLibrariesAreChunkedBelowStoreLimits() {
        val store = InMemoryKeyValueStore()
        val songs = (0 until 400).map { Song("song$it", "Song number $it", 120, 4, Subdivision.QUARTER, List(4) { BeatAccent.REGULAR }) }
        KeyValueSongLibraryRepository(store).setSongs(songs)

        val chunks = store.getInt("${KeyValueSongLibraryRepository.KEY_SONGS}_chunks", 0)
        assertEquals(true, chunks > 1)
        for (i in 0 until chunks) {
            assertEquals(true, store.getString("${KeyValueSongLibraryRepository.KEY_SONGS}_$i")!!.length <= 8192)
        }
        assertEquals(songs, KeyValueSongLibraryRepository(store).getSongs())
    }

    /** SPEC-PLAYLIST-006 */
    @Test
    fun skipsCorruptRecordsAndClearsActivePlaylist() {
        val store = InMemoryKeyValueStore()
        store.putInt("${KeyValueSongLibraryRepository.KEY_SONGS}_chunks", 1)
        store.putString(
            "${KeyValueSongLibraryRepository.KEY_SONGS}_0",
            "ok|Fine|90|3|QUARTER|ACCENT,REGULAR,REGULAR\nbad|Broken|x|3|QUARTER|\nodd|Odd|90|3|NOPE|"
        )
        val repo = KeyValueSongLibraryRepository(store)
        assertEquals(listOf("ok"), repo.getSongs().map { it.id })

        repo.setActivePlaylistId(null)
        assertNull(repo.getActivePlaylistId())
    }
}
