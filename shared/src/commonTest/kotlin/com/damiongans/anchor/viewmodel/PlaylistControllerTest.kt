package com.damiongans.anchor.viewmodel

import com.damiongans.anchor.fakes.FakeAudioEngine
import com.damiongans.anchor.fakes.FakeHapticsManager
import com.damiongans.anchor.fakes.FakeUpdateRepository
import com.damiongans.anchor.model.BeatAccent
import com.damiongans.anchor.model.Subdivision
import com.damiongans.anchor.repository.ISettingsRepository
import com.damiongans.anchor.repository.InMemoryKeyValueStore
import com.damiongans.anchor.repository.KeyValueSettingsRepository
import com.damiongans.anchor.update.AppVersionInfo
import kotlinx.coroutines.test.TestScope
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlaylistControllerTest {

    private lateinit var store: InMemoryKeyValueStore
    private lateinit var audio: FakeAudioEngine
    private lateinit var controller: MetronomeController

    private val state get() = controller.uiState.value

    @BeforeTest
    fun setUp() {
        store = InMemoryKeyValueStore()
        audio = FakeAudioEngine()
        controller = newController()
    }

    private fun newController(settings: ISettingsRepository = KeyValueSettingsRepository(store)) = MetronomeController(
        audioEngine = audio,
        hapticsManager = FakeHapticsManager(),
        settingsRepository = settings,
        updateRepository = FakeUpdateRepository(),
        appVersion = AppVersionInfo("1.7.0"),
        scope = TestScope(),
        clockMs = { 0L },
        autoCheckForUpdates = false
    )

    private val waltz = listOf(BeatAccent.ACCENT, BeatAccent.SUB, BeatAccent.SUB)

    /** SPEC-PLAYLIST-001, SPEC-PLAYLIST-006 */
    @Test
    fun createSong_normalizesValuesAndPersists() {
        val song = controller.createSong("  Waltz ", 999, 3, Subdivision.QUARTER, listOf(BeatAccent.ACCENT))

        assertEquals("Waltz", song.name)
        assertEquals(300, song.bpm)
        assertEquals(listOf(BeatAccent.ACCENT, BeatAccent.REGULAR, BeatAccent.REGULAR), song.accents)
        assertEquals("3/4", song.timeSignatureDisplay)
        assertEquals(listOf(song), newController().uiState.value.songs)
    }

    /** SPEC-PLAYLIST-004, SPEC-PLAYLIST-005, SPEC-PLAYLIST-002 */
    @Test
    fun loadSong_appliesBpmMeterAndAccentsWithoutChangingTransport() {
        val song = controller.createSong("Waltz", 96, 3, Subdivision.EIGHTH, waltz)
        controller.loadSong(song.id)

        assertEquals(96, state.bpm)
        assertEquals(3, state.totalBeats)
        assertEquals(Subdivision.EIGHTH, state.currentSubdivision)
        assertEquals(waltz, state.accents)
        assertEquals(waltz, audio.accents)
        assertEquals(song.id, state.activeSongId)
        assertFalse(state.isActiveSongModified)
        assertFalse(state.isPlaying)

        controller.adjustBpm(1)
        assertTrue(state.isActiveSongModified)
        controller.updateSongFromCurrent(song.id)
        assertFalse(state.isActiveSongModified)
        assertEquals(97, state.activeSong?.bpm)
    }

    /** SPEC-PLAYLIST-002 */
    @Test
    fun saveCurrentAsSong_snapshotsLiveSettings() {
        controller.setBpm(140)
        controller.setTimeSignature(7, Subdivision.EIGHTH)
        val song = controller.saveCurrentAsSong("Odd")

        assertEquals(140, song.bpm)
        assertEquals(7, song.totalBeats)
        assertEquals("7/8", song.timeSignatureDisplay)
        assertEquals(song.id, state.activeSongId)
    }

    /** SPEC-PLAYLIST-004, SPEC-PLAYLIST-003 */
    @Test
    fun playlistNavigation_stepsThroughSongsWithoutWrapping() {
        val a = controller.createSong("A", 80, 4, Subdivision.QUARTER, emptyList())
        val b = controller.createSong("B", 120, 3, Subdivision.QUARTER, waltz)
        val set = controller.createPlaylist("Gig")
        controller.addSongToPlaylist(set.id, a.id)
        controller.addSongToPlaylist(set.id, b.id)

        controller.selectPlaylist(set.id)
        assertEquals(set.id, state.activePlaylistId)
        assertEquals(0, state.activeSongIndex)
        assertEquals(80, state.bpm)

        controller.previousSong()
        assertEquals(0, state.activeSongIndex)
        controller.nextSong()
        assertEquals(1, state.activeSongIndex)
        assertEquals(120, state.bpm)
        assertEquals(3, state.totalBeats)
        controller.nextSong()
        assertEquals(1, state.activeSongIndex)
        assertEquals(set.id, newController().uiState.value.activePlaylistId)
    }

    /** SPEC-PLAYLIST-003 */
    @Test
    fun moveAndRemove_keepActivePositionOnTheLoadedSong() {
        val ids = (1..3).map { controller.createSong("S$it", 60 + it, 4, Subdivision.QUARTER, emptyList()).id }
        val set = controller.createPlaylist("Set")
        ids.forEach { controller.addSongToPlaylist(set.id, it) }
        controller.selectPlaylist(set.id)
        controller.selectPlaylistSong(1)

        controller.moveSongInPlaylist(set.id, 1, 2)
        assertEquals(2, state.activeSongIndex)
        controller.removeSongFromPlaylist(set.id, 0)
        assertEquals(1, state.activeSongIndex)
        assertEquals(ids[1], state.activePlaylist?.songIds?.get(1))
        controller.removeSongFromPlaylist(set.id, 1)
        assertNull(state.activeSongIndex)
    }

    /** SPEC-PLAYLIST-002 */
    @Test
    fun deleteSong_removesItFromEveryPlaylist() {
        val a = controller.createSong("A", 80, 4, Subdivision.QUARTER, emptyList())
        val one = controller.createPlaylist("One")
        val two = controller.createPlaylist("Two")
        controller.addSongToPlaylist(one.id, a.id)
        controller.addSongToPlaylist(two.id, a.id)
        controller.addSongToPlaylist(two.id, a.id)
        controller.loadSong(a.id)

        controller.deleteSong(a.id)
        assertTrue(state.songs.isEmpty())
        assertTrue(state.playlists.all { it.songIds.isEmpty() })
        assertNull(state.activeSongId)
    }

    /** SPEC-PLAYLIST-003 */
    @Test
    fun deletePlaylist_clearsActivePlaylist() {
        val set = controller.createPlaylist("Set")
        controller.selectPlaylist(set.id)
        controller.deletePlaylist(set.id)
        assertNull(state.activePlaylistId)
        assertNull(newController().uiState.value.activePlaylistId)
    }
}
