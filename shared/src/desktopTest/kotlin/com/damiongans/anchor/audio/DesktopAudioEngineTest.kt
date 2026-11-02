package com.damiongans.anchor.audio

import com.damiongans.anchor.model.AudioDeviceType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopAudioEngineTest {

    @Test
    fun lifecycleNeverThrows_evenWithoutAnAudioDevice() {
        val engine = DesktopAudioEngine()
        val devices = engine.currentAudioInfo.availableDevices
        assertTrue(devices.isNotEmpty())
        assertEquals(AudioDeviceType.SPEAKER, devices.first().deviceType) // system default first
        assertEquals(DesktopAudioEngine.DEFAULT_SAMPLE_RATE, engine.currentAudioInfo.sampleRate)

        engine.bpm = 180.0
        engine.start()      // plays if a device exists, otherwise returns quietly
        Thread.sleep(150)
        engine.selectAudioOutputDevice(null)
        engine.stop()
        engine.stop()       // idempotent
        engine.release()
    }
}
