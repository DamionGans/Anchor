package com.damiongans.anchor.domain

import com.damiongans.anchor.model.BeatAccent
import com.damiongans.anchor.model.Playlist
import com.damiongans.anchor.model.Song
import com.damiongans.anchor.model.Subdivision
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SongLibraryTest {

    private fun song(id: String) = Song(id, id.uppercase(), 100, 4, Subdivision.QUARTER, List(4) { BeatAccent.REGULAR })

    private val a = song("a")
    private val b = song("b")
    private val c = song("c")
    private val gig = Playlist("gig", "Gig", listOf("a", "b", "c", "a"))
    private val library = SongLibrary(songs = listOf(a, b, c), playlists = listOf(gig))

    /** SPEC-PLAYLIST-001 */
    @Test
    fun normalize_trimsClampsAndSizesAccents() {
        val raw = Song("x", "  Tune ", 5, 40, Subdivision.EIGHTH, listOf(BeatAccent.MUTE))
        val normalized = SongLibrary.normalize(raw)
        assertEquals("Tune", normalized.name)
        assertEquals(20, normalized.bpm)
        assertEquals(16, normalized.totalBeats)
        assertEquals(16, normalized.accents.size)
        assertEquals(BeatAccent.MUTE, normalized.accents[0])
        assertTrue(normalized.accents.drop(1).all { it == BeatAccent.REGULAR })
        assertEquals(BeatAccent.ACCENT, SongLibrary.normalize(raw.copy(accents = emptyList())).accents[0])
    }

    /** SPEC-PLAYLIST-002 */
    @Test
    fun deleteSong_cascadesToPlaylistsAndKeepsPositionOnTheLoadedSong() {
        val playing = library.withActivePlaylist("gig").withActiveSong("c", 2)
        val result = playing.deleteSong("a")
        assertEquals(listOf("b", "c"), result.playlist("gig")!!.songIds)
        assertEquals("c", result.activeSongId)
        assertEquals(1, result.activeSongIndex)

        val cleared = playing.deleteSong("c")
        assertNull(cleared.activeSongId)
        assertNull(cleared.activeSongIndex)
    }

    /** SPEC-PLAYLIST-003 */
    @Test
    fun invalidEdits_returnTheSameLibrary() {
        assertSame(library, library.replaceSong(song("zzz")))
        assertSame(library, library.deleteSong("zzz"))
        assertSame(library, library.addToPlaylist("gig", "zzz"))
        assertSame(library, library.removeFromPlaylist("gig", 9))
        assertSame(library, library.moveInPlaylist("gig", 0, 9))
        assertSame(library, library.renamePlaylist("nope", "x"))
        assertSame(library, library.deletePlaylist("nope"))
    }

    /** SPEC-PLAYLIST-003 */
    @Test
    fun moveAndRemove_followTheLoadedEntryEvenWithDuplicates() {
        val atLastA = library.withActivePlaylist("gig").withActiveSong("a", 3)
        assertEquals(3, atLastA.activeSongIndex)

        val moved = atLastA.moveInPlaylist("gig", 3, 0)
        assertEquals(listOf("a", "a", "b", "c"), moved.playlist("gig")!!.songIds)
        assertEquals(0, moved.activeSongIndex)

        val removedBefore = atLastA.removeFromPlaylist("gig", 1)
        assertEquals(2, removedBefore.activeSongIndex)
        assertEquals("a", removedBefore.activePlaylist!!.songIds[2])
    }

    /** SPEC-PLAYLIST-003 */
    @Test
    fun deletingTheActivePlaylist_clearsThePosition() {
        val active = library.withActivePlaylist("gig").withActiveSong("b", 1)
        val result = active.deletePlaylist("gig")
        assertNull(result.activePlaylistId)
        assertNull(result.activeSongIndex)
        assertEquals("b", result.activeSongId)
    }

    /** SPEC-PLAYLIST-006 */
    @Test
    fun pruned_dropsDanglingEntriesAndUnknownActivePlaylist() {
        val corrupt = SongLibrary(
            songs = listOf(a),
            playlists = listOf(Playlist("p", "P", listOf("a", "ghost"))),
            activePlaylistId = "missing"
        ).pruned()
        assertEquals(listOf("a"), corrupt.playlist("p")!!.songIds)
        assertNull(corrupt.activePlaylistId)
    }

    @Test
    fun newId_neverCollidesWithExistingIds() {
        val seeded = Random(42)
        val firstId = Random(42).nextLong().toULong().toString(36)
        val crowded = library.addSong(song(firstId))
        val id = crowded.newId(seeded)
        assertTrue(id != firstId && crowded.song(id) == null && crowded.playlist(id) == null)
    }
}
