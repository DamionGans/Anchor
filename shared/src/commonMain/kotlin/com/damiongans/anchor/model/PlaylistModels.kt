package com.damiongans.anchor.model

/**
 * A library entry: a named tempo, meter and accent pattern that can be loaded onto the
 * metronome in one tap. The meter is `totalBeats` + `subdivision`, the same pair used by
 * `MetronomeController.setTimeSignature` and [TimeSignaturePreset].
 */
data class Song(
    val id: String,
    val name: String,
    val bpm: Int,
    val totalBeats: Int,
    val subdivision: Subdivision,
    val accents: List<BeatAccent>
) {
    /** Display label for the meter, e.g. "4/4" or "6/8" (same rule as `MetronomeUiState.timeSignatureDenominator`). */
    val timeSignatureDisplay: String
        get() = "$totalBeats/${if (subdivision == Subdivision.EIGHTH) 8 else 4}"
}

/**
 * An ordered set list. Songs live once in the library; playlists reference them by id, so a
 * song can appear in several playlists and edits to it show up everywhere.
 */
data class Playlist(
    val id: String,
    val name: String,
    val songIds: List<String>
)
