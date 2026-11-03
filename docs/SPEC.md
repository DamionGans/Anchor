# Anchor Metronome — Functional Specification (`docs/SPEC.md`)

This document defines the canonical, testable functional specification for **Anchor Metronome** (**Anchor**, `com.damiongans.anchor`) across Android, iPhone (iOS), and Windows (`x64` & `arm64`).

Every requirement has a stable ID (`SPEC-<AREA>-<NNN>`). Automated tests cite these IDs in their KDoc or test name (e.g. `/** SPEC-PLAYLIST-003 */`), and `SpecCoverageTest` verifies 1-to-1 coverage across the test suite.

---

## 1. Product & Distribution (`SPEC-PROD`)

| ID | Requirement |
| --- | --- |
| `SPEC-PROD-001` | **Product Identity**: Full name is `"Anchor Metronome"` (≤30 characters, no emojis); short display name is `"Anchor"`; tagline is `"Rock-solid time for serious play"`. |
| `SPEC-PROD-002` | **Distribution Channels**: `DistributionChannel` supports `GITHUB`, `FDROID`, `PLAY_STORE`, and `APP_STORE`. Only `DistributionChannel.GITHUB` enables self-update checks (`canCheckForUpdates == true`, `updateStoreName == null`). Store channels (`FDROID`, `PLAY_STORE`, `APP_STORE`) disable in-app GitHub update checks (`canCheckForUpdates == false`) and expose their store display name (`"F-Droid"`, `"Google Play"`, `"the App Store"`). |
| `SPEC-PROD-003` | **Offline Store Builds & Least Privilege**: The `fdroid` and `play` flavors remove `INTERNET` (`tools:node="remove"`), so they make no network calls; the main manifest requests only `INTERNET` (GitHub build), `VIBRATE`, `WAKE_LOCK`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK` and `POST_NOTIFICATIONS`, disables cleartext traffic, and never requests install-packages or storage permissions. |

---

## 2. Tempo & Tap Tempo (`SPEC-TEMPO`)

| ID | Requirement |
| --- | --- |
| `SPEC-TEMPO-001` | **BPM Bounds & Default**: Tempo is an integer clamped to `20..300` BPM (`MetronomeController.MIN_BPM`/`MAX_BPM`), defaulting to `120` BPM on first launch. |
| `SPEC-TEMPO-002` | **BPM Adjustments**: `setBpm(bpm)` clamps to `20..300`, updates the engine and persists immediately; `adjustBpm(delta)` and `multiplyBpm(factor)` derive from the current BPM and go through the same clamp. |
| `SPEC-TEMPO-003` | **Tempo Markings**: Classical Italian tempo markings map deterministically across `20..300` BPM: `Larghissimo` (20–39), `Grave` (40–49), `Largo` (50–59), `Adagio` (60–71), `Andante` (72–87), `Moderato` (88–107), `Allegretto` (108–119), `Allegro` (120–155), `Vivace` (156–175), `Presto` (176–199), `Prestissimo` (200–300). |
| `SPEC-TEMPO-004` | **Tap Tempo**: `TapTempoCalculator` needs at least 2 taps to produce a BPM (`bpmToApply`), filters outlier intervals once 4+ intervals exist, reports a confidence value and locks at 3+ taps with confidence ≥ 70; the controller applies `bpmToApply` (clamped to `20..300`) and resets the tap state after `TAP_RESET_MS` (2500 ms) without taps. |
| `SPEC-TEMPO-005` | **Button Trigger Mode**: Play/Pause and Tap Tempo buttons support `ButtonTriggerMode.ON_PRESS` (fires on initial press down, default) and `ButtonTriggerMode.ON_RELEASE` (fires on pointer release). |
| `SPEC-TRANSPORT-001` | **Play / Pause**: `togglePlayPause()`, `play()` and `pause()` start and stop the audio engine and update `isPlaying` and the media session; pausing cancels pending haptics and resets the visual beat, flash and pendulum. Output standby (`setOutputStandby`) is passed straight to the engine and never starts playback by itself. |
| `SPEC-FEEDBACK-001` | **Visual & Haptic Feedback**: Each played beat updates `currentBeat`/`currentSubdivisionIndex` and the accent flag; with flash enabled, the first tick of a beat raises `flashPulse` for `FLASH_DURATION_MS` (65 ms); with vibration enabled, beats trigger haptics. Flash and vibration are off by default, toggled with `toggleFlash()` / `toggleVibration()`, and persisted. |

---

## 3. Time Signatures, Subdivisions & Beat Accents (`SPEC-METER`, `SPEC-ACCENT`)

| ID | Requirement |
| --- | --- |
| `SPEC-METER-001` | **Beat Count Bounds & Default**: Beats per bar (`totalBeats`) is clamped to `1..16` (`ISettingsRepository.MAX_BEATS = 16`), defaulting to `4`. |
| `SPEC-METER-002` | **Subdivisions**: Supported subdivisions are `QUARTER` (1 tick/beat), `EIGHTH` (2 ticks/beat), `TRIPLET` (3 ticks/beat), and `SIXTEENTH` (4 ticks/beat). |
| `SPEC-METER-003` | **Meter Display & Presets**: The meter is shown as `totalBeats/8` when the subdivision is `EIGHTH` and `totalBeats/4` otherwise (`MetronomeUiState.timeSignatureDenominator`, `Song.timeSignatureDisplay`). `TimeSignaturePreset` (1/4, 2/4, 3/4, 4/4, 5/4, 6/8, 7/8, 9/8, 12/8) sets beat count and subdivision together. |
| `SPEC-ACCENT-001` | **Beat Accent Cycling**: Each beat has a `BeatAccent` state that cycles `ACCENT -> REGULAR -> SUB -> MUTE -> ACCENT` when tapped via `cycleBeatAccent(index)`. Default pattern for `N` beats is `[ACCENT, REGULAR, REGULAR, ...]`. |
| `SPEC-ACCENT-002` | **16-Beat Accent Memory**: Modifying a beat's accent updates the 16-entry `rememberedAccents` list at that index. Changing `totalBeats` restores `rememberedAccents.take(newBeats)` so switching meters preserves custom accent patterns. |

---

## 4. Audio Synthesis, Sequencer & Live Pulse Invariants (`SPEC-AUDIO`, `SPEC-PULSE`)

| ID | Requirement |
| --- | --- |
| `SPEC-AUDIO-001` | **Click Sounds & Pitch**: `ClickSynthesizer` pre-renders 16-bit mono PCM (`ShortArray`) clicks for every `SoundSet` (`WOODBLOCK` default, `MECHANICAL`, `DIGITAL`, `RIMSHOT`, `ELECTRO_808`) and accent level (`ACCENT`, `REGULAR`, `SUB`; `MUTE` is silent), scaled by `pitchMultiplier` (`0.5..2.0`, default `1.0`), with no allocations on the audio thread. |
| `SPEC-AUDIO-002` | **Warmup Dither**: On a cold start the sequencer writes at least 55 ms of ±1 LSB dither (`warmupChunkCount`, `fillWarmupDither`) before Beat 1 so output hardware is awake when Beat 1 plays. |
| `SPEC-AUDIO-003` | **Standby Alignment**: With output standby active, Play aligns Beat 1 to the next output chunk (`alignToOutputFrame`) and adds warmup only for frames not already written since the stream started. |
| `SPEC-AUDIO-004` | **Audio Route Selection**: `AudioRouteSelector.normalize` de-duplicates outputs and always includes the Built-in Speaker; `selectActive` picks the preferred device if still connected, else the platform-routed device, else USB > wired > Bluetooth > first available. |
| `SPEC-AUDIO-005` | **Live Sound Changes**: `setSoundSet` and `setPitchMultiplier` (clamped to `0.5..2.0`) apply to the engine immediately (also while playing) and persist. |
| `SPEC-AUDIO-006` | **Manual Output Selection**: `selectAudioOutputDevice(id)` asks the engine to route to that output; the engine reports the resulting route back and `MetronomeUiState.audioInfo` follows it (including the Bluetooth offset rule of `SPEC-TIMING-003`); `null` re-detects the system route. |
| `SPEC-PULSE-001` | **Single Beat-Phase Accumulator & Slew-Limited BPM**: `MetronomeSequencer` advances one continuous `beatPhase += bpm / (60 * sampleRate)` accumulator (never a per-subdivision counter); BPM moves toward the target by at most `MAX_BPM_STEP_PER_CHUNK` (2.0 BPM) per chunk. |
| `SPEC-PULSE-002` | **Glitch-Free Live Subdivision Change**: Changing subdivision while playing takes effect at the next subdivision boundary without firing late retroactive ticks or resetting `currentBeat` / `currentSubdivisionIndex`. |
| `SPEC-PULSE-003` | **Live Time-Signature Change**: Changing `totalBeats` while playing applies at the next beat (a bar that becomes shorter than the current position starts a new bar) without resetting the phase mid-beat or resetting `currentBeat`/`currentSubdivisionIndex`. |
| `SPEC-PULSE-004` | **Pendulum Phase Interpolation**: The sequencer records per-chunk phase checkpoints (`recordPhaseCheckpoint`, allocation-free ring buffer) and `processPlayhead` interpolates the pendulum phase from them, so the swing stays smooth through tempo and meter changes. |

---

## 5. Hardware Playhead & Delay Compensation (`SPEC-TIMING`)

| ID | Requirement |
| --- | --- |
| `SPEC-TIMING-001` | **Hardware Playhead Synchronization**: Visual beat, flash, pendulum and vibration events fire from the smoothed hardware playhead (`PlayheadEstimator`), at `playheadFrame >= beatAudioFrame` for a 0 ms offset — never from the write head and with no hidden latency constants. |
| `SPEC-TIMING-002` | **Offsets & Ranges**: Visual (`flashDelayMs`) and vibration (`vibrationDelayMs`) offsets are clamped to `-200..300` ms; the Bluetooth offset (`bluetoothCompensationMs`) to `-100..400` ms (`DelayCompensationEngine` constants). Negative values fire earlier (using warmup lookahead), positive later; `resetTimingOffsets()` sets all three to 0. |
| `SPEC-TIMING-003` | **Bluetooth Offset Only on Bluetooth**: `effectiveVisualDelayMs = flashDelayMs + bluetoothCompensationMs` and `effectiveVibrationDelayMs = vibrationDelayMs + bluetoothCompensationMs` only while the active output is `AudioDeviceType.BLUETOOTH`; otherwise the Bluetooth offset is ignored. |

---

## 6. Playlists & Song Library (`SPEC-PLAYLIST`)

| ID | Requirement |
| --- | --- |
| `SPEC-PLAYLIST-001` | **Song Model & Normalization**: `Song(id, name, bpm, totalBeats, subdivision, accents)`; saving trims the name, clamps `bpm` to `20..300` and `totalBeats` to `1..16`, and sizes `accents` to `totalBeats` (missing beats default to `ACCENT` on beat 1 and `REGULAR` after). Songs live once in the library; `Playlist(id, name, songIds)` references them by id. |
| `SPEC-PLAYLIST-002` | **Library CRUD**: `saveCurrentAsSong(name)` (snapshot of live settings, becomes the active song), `createSong(...)`, `updateSong(...)` (live metronome unchanged), `updateSongFromCurrent(songId)`, `renameSong(songId, name)`, `deleteSong(songId)` (also removes it from every playlist and clears it as active song). |
| `SPEC-PLAYLIST-003` | **Playlist CRUD & Ordering**: `createPlaylist`, `renamePlaylist`, `deletePlaylist` (clears it if active), `addSongToPlaylist` (duplicates allowed), `removeSongFromPlaylist(index)` and `moveSongInPlaylist(from, to)`; the active position follows the loaded song through moves and removals and is cleared when the loaded entry is removed. |
| `SPEC-PLAYLIST-004` | **Live Loading & Non-Wrapping Stepping**: `loadSong`, `selectPlaylist` (loads the first song), `selectPlaylistSong`, `nextSong` and `previousSong` apply BPM, meter and accents live (also into the per-count accent memory) and never start or stop playback; stepping does not wrap at either end. |
| `SPEC-PLAYLIST-005` | **Modified State Detection**: `MetronomeUiState.isActiveSongModified` is `true` if and only if an active song is loaded and live `bpm`, `totalBeats`, `subdivision`, or `accents` differ from that song's saved values; `updateSongFromCurrent` clears the modified state. |
| `SPEC-PLAYLIST-006` | **Library Persistence**: `KeyValueSongLibraryRepository` (shared by every platform via `KeyValueSettingsRepository`) stores songs and playlists as percent-escaped `|`-delimited lines in ≤4000-char chunks (`<key>_<n>` + `<key>_chunks`; Windows prefs cap values at 8192 chars), plus `active_playlist_id`; unreadable lines are skipped, and playlist entries pointing at missing songs are dropped on load. |

---

## 7. Playback Session, Persistence & Updates (`SPEC-SESSION`, `SPEC-PERSIST`, `SPEC-UPDATE`)

| ID | Requirement |
| --- | --- |
| `SPEC-SESSION-001` | **Media / Now Playing Session**: `play()` starts the audio engine before syncing `IPlaybackSession`; the session title is `"<BPM> BPM • <Marking>"` and the subtitle `"Playing • <beats>/<denominator> Time"` or `"Paused • …"`; BPM/meter updates are debounced by `SESSION_SYNC_DEBOUNCE_MS` (150 ms) while play/pause sync immediately; remote commands map to play, pause, toggle, stop (= pause) and BPM ± delta. |
| `SPEC-SESSION-002` | **Platform Media Surfaces (manual)**: Android shows a `mediaPlayback` foreground-service notification with −1 BPM / Play-Pause / +1 BPM that stays while paused; iOS shows Now Playing with remote play/pause and next/previous = ±1 BPM; Windows has no media session (`NoOpPlaybackSession`). Verified on devices; the shared contract is `SPEC-SESSION-001`. |
| `SPEC-PERSIST-001` | **Settings Round-Trip & Clamping**: `KeyValueSettingsRepository` persists bpm, beats, subdivision, sound set, pitch, accents and the 16-entry accent memory, button trigger mode, flash/vibration toggles, visual/vibration/Bluetooth offsets, theme and accent colour; values are clamped on read and write, unknown enum strings fall back to defaults, and the legacy single timing offset seeds the visual/vibration offsets. |
| `SPEC-UPDATE-001` | **Release Selection**: `ReleaseVersioning.isVersionNewer` compares SemVer tags; `buildReleaseInfo` picks the `.apk` (Android), `.ipa` (iOS) or architecture-matched `-windows-x64.msi`/`-windows-arm64.msi` (`WindowsArch.fromOsArch`) asset; update checks fall back from `DamionGans/Anchor` to the legacy `DamionGans/PulseMetronome` repository. |
| `SPEC-UPDATE-002` | **Update Flow**: GitHub builds check for a newer release on launch and on demand; a newer release opens the update dialog with Update (opens the download URL, or the release page when there is no matching asset), Ask Later (hides it for this session) and Decline (remembered for that release tag); a manual check with nothing newer reports that the app is up to date. Store builds never check. |

---

## 8. User Interface & Cross-Platform Parity (`SPEC-UI`)

| ID | Requirement |
| --- | --- |
| `SPEC-UI-001` | **Single-Screen Layout & Studio Split**: Main screen (`MetronomeScreen.kt`, `ContentView.swift`) uses a non-scrolling 1-column layout on compact viewports and a 2-pane studio split on wide viewports (`width >= 680.dp && height >= 480.dp`). |
| `SPEC-UI-002` | **Setlist Bar Visibility & Stepping**: `SetlistBar` is rendered on the main screen only when `activePlaylist != null`, displays the active playlist/song position and modified indicator, and triggers `previousSong()`, `nextSong()`, and the Playlists sheet. |
| `SPEC-UI-003` | **Bottom Sheets & Dialogs**: Top bar buttons open Audio Output menu, Playlists sheet (`PlaylistsBottomSheet`), Changelog dialog (`ChangelogDialog`), and Settings sheet (`SettingsBottomSheet` with Sound, Visual & Haptic, Timing, Theme, and About tabs). |
| `SPEC-UI-004` | **Desktop Keyboard Shortcuts**: On Windows, `Space` toggles Play/Pause, `Up`/`+` and `Down`/`-` adjust BPM by `±1` (`±5` with `Shift`), `T` triggers Tap Tempo, and `PageUp`/`[` / `PageDown`/`]` step to the previous/next song in the active playlist. |
