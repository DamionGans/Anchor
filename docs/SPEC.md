# Anchor Metronome — Functional Specification (`docs/SPEC.md`)

This document defines the canonical, testable functional specification for **Anchor Metronome** (**Anchor**, `com.damiongans.anchor`) across Android, iPhone (iOS), and Windows (`x64` & `arm64`).

Every requirement has a stable ID (`SPEC-<AREA>-<NNN>`). Automated tests cite these IDs in their KDoc or test name (e.g. `/** SPEC-PLAYLIST-003 */`), and `SpecCoverageTest` verifies 1-to-1 coverage across the test suite.

---

## 1. Product & Distribution (`SPEC-PROD`)

| ID | Requirement |
| --- | --- |
| `SPEC-PROD-001` | **Product Identity**: Full name is `"Anchor Metronome"` (≤30 characters, no emojis); short display name is `"Anchor"`; tagline is `"Rock-solid time for serious play"`. |
| `SPEC-PROD-002` | **Distribution Channels**: `DistributionChannel` supports `GITHUB`, `FDROID`, `PLAY_STORE`, and `APP_STORE`. Only `DistributionChannel.GITHUB` enables self-update checks (`canCheckForUpdates == true`, `updateStoreName == null`). Store channels (`FDROID`, `PLAY_STORE`, `APP_STORE`) disable in-app GitHub update checks (`canCheckForUpdates == false`) and expose their store display name (`"F-Droid"`, `"Google Play"`, `"the App Store"`). |

---

## 2. Tempo & Tap Tempo (`SPEC-TEMPO`)

| ID | Requirement |
| --- | --- |
| `SPEC-TEMPO-001` | **BPM Bounds & Default**: Tempo is an integer clamped to `20..300` BPM (`MetronomeUiState.MIN_BPM = 20`, `MAX_BPM = 300`), defaulting to `120` BPM. |
| `SPEC-TEMPO-002` | **BPM Adjustments**: `setBpm(target)` clamps to `20..300` and persists immediately; `adjustBpm(delta)` adjusts relative to current BPM within `20..300`. |
| `SPEC-TEMPO-003` | **Tempo Markings**: Classical Italian tempo markings map deterministically across `20..300` BPM: `Larghissimo` (20–39), `Grave` (40–49), `Largo` (50–59), `Adagio` (60–71), `Andante` (72–87), `Moderato` (88–107), `Allegretto` (108–119), `Allegro` (120–155), `Vivace` (156–175), `Presto` (176–199), `Prestissimo` (200–300). |
| `SPEC-TEMPO-004` | **Tap Tempo Calculation**: `TapTempoCalculator` computes BPM from recent tap timestamps, resets after a pause exceeding the reset threshold, clamps calculated BPM to `20..300`, and updates controller BPM from the second tap onward. |
| `SPEC-TEMPO-005` | **Button Trigger Mode**: Play/Pause and Tap Tempo buttons support `ButtonTriggerMode.ON_PRESS` (fires on initial press down, default) and `ButtonTriggerMode.ON_RELEASE` (fires on pointer release). |

---

## 3. Time Signatures, Subdivisions & Beat Accents (`SPEC-METER`, `SPEC-ACCENT`)

| ID | Requirement |
| --- | --- |
| `SPEC-METER-001` | **Beat Count Bounds & Default**: Beats per bar (`totalBeats`) is clamped to `1..16` (`MIN_BEATS = 1`, `MAX_BEATS = 16`), defaulting to `4` (`4/4` time). |
| `SPEC-METER-002` | **Subdivisions**: Supported subdivisions are `QUARTER` (1 tick/beat), `EIGHTH` (2 ticks/beat), `TRIPLET` (3 ticks/beat), and `SIXTEENTH` (4 ticks/beat). |
| `SPEC-METER-003` | **Time Signature Display & Presets**: `timeSignatureDisplay` renders `"$totalBeats/8"` when `subdivision == Subdivision.EIGHTH` and `"$totalBeats/4"` otherwise. Standard `TimeSignaturePreset` entries configure both beat count and subdivision. |
| `SPEC-ACCENT-001` | **Beat Accent Cycling**: Each beat has a `BeatAccent` state that cycles `ACCENT -> REGULAR -> SUB -> MUTE -> ACCENT` when tapped via `cycleBeatAccent(index)`. Default pattern for `N` beats is `[ACCENT, REGULAR, REGULAR, ...]`. |
| `SPEC-ACCENT-002` | **16-Beat Accent Memory**: Modifying a beat's accent updates the 16-entry `rememberedAccents` list at that index. Changing `totalBeats` restores `rememberedAccents.take(newBeats)` so switching meters preserves custom accent patterns. |

---

## 4. Audio Synthesis, Sequencer & Live Pulse Invariants (`SPEC-AUDIO`, `SPEC-PULSE`)

| ID | Requirement |
| --- | --- |
| `SPEC-AUDIO-001` | **Click Sound Sets & Pitch**: `ClickSynthesizer` pre-renders 16-bit mono PCM (`ShortArray`) for all 6 `SoundSet`s (`TICK`, `CLICK`, `WOODBLOCK`, `COWBELL`, `BEEP`, `HI_HAT`) across `ACCENT`, `REGULAR`, and `SUB` click types with configurable `pitchMultiplier` (`0.5f..2.0f`, default `1.0f`) and zero allocations on the audio thread. |
| `SPEC-AUDIO-002` | **Warmup Dither**: On cold starts, `MetronomeSequencer` writes ≥55 ms (`warmupChunkCount(sampleRate, chunkFrames)`) of ±1 LSB dither (`fillWarmupDither`) before Beat 1 so OS/Bluetooth DACs wake up without clipping Beat 1. |
| `SPEC-AUDIO-003` | **Standby Alignment (`alignToOutputFrame`)**: When output standby is active, pressing Play aligns Beat 1 to the next output chunk (`alignToOutputFrame`) and only adds warmup frames if fewer than `warmupFrames` have been written since stream start. |
| `SPEC-AUDIO-004` | **Audio Route Selection**: `AudioRouteSelector.normalize` always includes the Built-in Speaker even when empty or only external routes are reported; selecting a disconnected route falls back to Built-in Speaker; Bluetooth status is tracked from the active route. |
| `SPEC-PULSE-001` | **Single Beat-Phase Accumulator & Slew-Limited BPM**: `MetronomeSequencer` advances a single continuous `beatPhase += currentBpm / (60.0 * sampleRate)` accumulator, slewing `currentBpm` toward `targetBpm` by at most `MAX_BPM_STEP_PER_CHUNK` (`5.0` BPM/chunk) so rapid tempo adjustments never click, stutter, or jump backwards. |
| `SPEC-PULSE-002` | **Glitch-Free Live Subdivision Change**: Changing subdivision while playing takes effect at the next subdivision boundary without firing late retroactive ticks or resetting `currentBeat` / `currentSubdivisionIndex`. |
| `SPEC-PULSE-003` | **Glitch-Free Live Time-Signature Change**: Changing `totalBeats` while playing applies at the next beat boundary (wrapping to Beat 1 if the new bar is shorter than the next beat index) without resetting phase mid-beat. |
| `SPEC-PULSE-004` | **Continuous Pendulum Phase Interpolation**: `MetronomeSequencer` records per-chunk `(audioFrame, totalBeatsElapsed)` phase checkpoints in a lock-free ring buffer (`interpolateTotalBeatsAtFrame`) so the pendulum swings smoothly through tempo and meter changes. |

---

## 5. Hardware Playhead & Delay Compensation (`SPEC-TIMING`)

| ID | Requirement |
| --- | --- |
| `SPEC-TIMING-001` | **Hardware Playhead Synchronization**: Visuals, screen flash, pendulum, and haptics fire when smoothed hardware playhead frame reaches `beatAudioFrame` (`playheadFrame >= targetFrame` at `0` ms offset), never from write-head time and with zero hidden latency constants. |
| `SPEC-TIMING-002` | **Independent Visual, Vibration & Bluetooth Offsets**: Visual (`flashDelayMs`), Vibration (`vibrationDelayMs`), and Bluetooth (`bluetoothDelayMs`) offsets are clamped to `-200..300` ms (`MIN_DELAY_MS = -200`, `MAX_DELAY_MS = 300`). Negative offsets schedule events earlier (using warmup lookahead); positive offsets schedule events later. |
| `SPEC-TIMING-003` | **Bluetooth Offset Conditional Application**: `bluetoothDelayMs` shifts visual and vibration targets together (`effectiveFlashDelayMs = flashDelayMs - bluetoothDelayMs`) **only** when `isBluetoothConnected == true` (active output route is Bluetooth). |

---

## 6. Playlists & Song Library (`SPEC-PLAYLIST`)

| ID | Requirement |
| --- | --- |
| `SPEC-PLAYLIST-001` | **Song Model & Normalization**: Every `Song(id, name, bpm, totalBeats, subdivision, accents)` normalizes blank names to `"Untitled"`, clamps `bpm` to `20..300` and `totalBeats` to `1..16`, and sizes `accents` to `totalBeats` (padding with `ACCENT` on beat 0 and `REGULAR` on remaining beats). |
| `SPEC-PLAYLIST-002` | **Library CRUD**: Controller supports `saveCurrentAsSong(name)`, `createSong(...)`, `updateSong(...)`, `updateSongFromCurrent(songId)`, `renameSong(songId, name)`, and `deleteSong(songId)`. Deleting a song removes its ID from all playlists and adjusts `activeSongIndex` / `activeSongId` cleanly. |
| `SPEC-PLAYLIST-003` | **Playlist CRUD & Ordering**: Controller supports `createPlaylist(name)`, `renamePlaylist(id, name)`, `deletePlaylist(id)`, `addSongToPlaylist(playlistId, songId)`, `removeSongFromPlaylist(playlistId, index)`, and `moveSongInPlaylist(playlistId, fromIndex, toIndex)`, keeping the active song tracked across reorder/removal. |
| `SPEC-PLAYLIST-004` | **Live Song Loading & Non-Wrapping Stepping**: `loadSong`, `selectPlaylist`, `selectPlaylistSong`, `nextSong`, and `previousSong` apply the song's BPM, time signature, subdivision, and accents live (updating `rememberedAccents`) without starting or stopping playback. `nextSong()` and `previousSong()` do not wrap at playlist ends. |
| `SPEC-PLAYLIST-005` | **Modified State Detection**: `MetronomeUiState.isActiveSongModified` is `true` if and only if an active song is loaded and live `bpm`, `totalBeats`, `subdivision`, or `accents` differ from that song's saved values; `updateSongFromCurrent` clears the modified state. |
| `SPEC-PLAYLIST-006` | **Chunked Escaped Persistence**: `KeyValueSongLibraryRepository` persists songs and playlists using percent-escaped `|`-delimited lines split into ≤4000-character chunks (`<key>_<n>` + `<key>_chunks`) so libraries of 200+ songs survive Windows `java.util.prefs` 8192-char limits, and gracefully skips malformed lines. |

---

## 7. Playback Session, Persistence & Updates (`SPEC-SESSION`, `SPEC-PERSIST`, `SPEC-UPDATE`)

| ID | Requirement |
| --- | --- |
| `SPEC-SESSION-001` | **Media / Now Playing Session Sync**: `MetronomeController` starts the audio engine **before** syncing `IPlaybackSession` on `play()`, formats session title as `"<BPM> BPM • <Marking>"` and subtitle as `"Playing • <meter> Time"` / `"Paused • <meter> Time"`, debounces BPM/meter session updates by `150` ms while keeping Play/Pause immediate, and maps remote Next/Previous commands to `+1` / `-1` BPM. |
| `SPEC-PERSIST-001` | **Settings Round-Trip & Clamping**: `KeyValueSettingsRepository` persists and clamps all user settings (`bpm`, `timeSignature`, `subdivision`, `soundSet`, `pitchMultiplier`, `accents`, `rememberedAccents`, `buttonTriggerMode`, `flashEnabled`, `vibrationEnabled`, `flashDelayMs`, `vibrationDelayMs`, `bluetoothDelayMs`, `themeMode`, `accentColor`) and recovers safely from corrupt stored strings. |
| `SPEC-UPDATE-001` | **SemVer Comparison & Asset Selection**: `ReleaseVersioning` compares semantic versions (`isNewerVersion`), selects platform-appropriate release assets (`.apk` on Android, `.ipa` on iOS, architecture-matched `-windows-x64.msi` / `-windows-arm64.msi` on Windows via `WindowsArch.fromOsArch`), and falls back from `DamionGans/Anchor` to `DamionGans/PulseMetronome`. |

---

## 8. User Interface & Cross-Platform Parity (`SPEC-UI`)

| ID | Requirement |
| --- | --- |
| `SPEC-UI-001` | **Single-Screen Layout & Studio Split**: Main screen (`MetronomeScreen.kt`, `ContentView.swift`) uses a non-scrolling 1-column layout on compact viewports and a 2-pane studio split on wide viewports (`width >= 680.dp && height >= 480.dp`). |
| `SPEC-UI-002` | **Setlist Bar Visibility & Stepping**: `SetlistBar` is rendered on the main screen only when `activePlaylist != null`, displays the active playlist/song position and modified indicator, and triggers `previousSong()`, `nextSong()`, and the Playlists sheet. |
| `SPEC-UI-003` | **Bottom Sheets & Dialogs**: Top bar buttons open Audio Output menu, Playlists sheet (`PlaylistsBottomSheet`), Changelog dialog (`ChangelogDialog`), and Settings sheet (`SettingsBottomSheet` with Sound, Visual & Haptic, Timing, Theme, and About tabs). |
| `SPEC-UI-004` | **Desktop Keyboard Shortcuts**: On Windows, `Space` toggles Play/Pause, `Up`/`+` and `Down`/`-` adjust BPM by `±1` (`±5` with `Shift`), `T` triggers Tap Tempo, and `PageUp`/`[` / `PageDown`/`]` step to the previous/next song in the active playlist. |
