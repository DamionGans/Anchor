package com.damiongans.anchor.audio

import com.damiongans.anchor.model.AudioDeviceType
import com.damiongans.anchor.model.AudioOutputDevice

/**
 * Platform-agnostic audio output list normalization and default route selection.
 * Platform engines only discover raw outputs; ordering, de-duplication, the always-present
 * Built-in Speaker entry, and the fallback priority are shared.
 */
object AudioRouteSelector {

    const val SPEAKER_DEVICE_ID = -1

    val BUILT_IN_SPEAKER = AudioOutputDevice(
        id = SPEAKER_DEVICE_ID,
        deviceType = AudioDeviceType.SPEAKER,
        name = "Built-in Speaker"
    )

    /**
     * De-duplicates outputs by type + name, guarantees the Built-in Speaker entry, and sorts
     * Speaker, Wired, USB, Bluetooth, Other.
     */
    fun normalize(discovered: List<AudioOutputDevice>): List<AudioOutputDevice> {
        val unique = mutableListOf<AudioOutputDevice>()
        for (device in discovered) {
            val alreadyListed = unique.any {
                it.deviceType == device.deviceType && it.name.equals(device.name, ignoreCase = true)
            }
            if (!alreadyListed) unique.add(device)
        }
        if (unique.none { it.deviceType == AudioDeviceType.SPEAKER }) {
            unique.add(0, BUILT_IN_SPEAKER)
        }
        return unique.sortedBy { sortRank(it.deviceType) }
    }

    /**
     * Picks the active output: the explicitly preferred device if still connected, otherwise the
     * platform-reported routed device, otherwise USB > Wired > Bluetooth > first available.
     */
    fun selectActive(
        sortedDevices: List<AudioOutputDevice>,
        preferredDeviceId: Int?,
        routedDevice: AudioOutputDevice?
    ): AudioOutputDevice {
        val devices = sortedDevices.ifEmpty { listOf(BUILT_IN_SPEAKER) }
        if (preferredDeviceId != null) {
            devices.firstOrNull { it.id == preferredDeviceId }?.let { return it }
        }
        if (routedDevice != null) {
            return devices.firstOrNull { it.id == routedDevice.id }
                ?: devices.firstOrNull { it.deviceType == routedDevice.deviceType }
                ?: routedDevice
        }
        return devices.firstOrNull { it.deviceType == AudioDeviceType.USB_AUDIO }
            ?: devices.firstOrNull { it.deviceType == AudioDeviceType.WIRED_HEADPHONES }
            ?: devices.firstOrNull { it.deviceType == AudioDeviceType.BLUETOOTH }
            ?: devices.first()
    }

    private fun sortRank(type: AudioDeviceType): Int = when (type) {
        AudioDeviceType.SPEAKER -> 0
        AudioDeviceType.WIRED_HEADPHONES -> 1
        AudioDeviceType.USB_AUDIO -> 2
        AudioDeviceType.BLUETOOTH -> 3
        AudioDeviceType.OTHER -> 4
    }
}
