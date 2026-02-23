package com.elkbledom.ble

import java.util.UUID

object ElkProtocol {
    val SERVICE_UUID: UUID = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")
    val WRITE_CHAR_UUID: UUID = UUID.fromString("0000fff3-0000-1000-8000-00805f9b34fb")
    val NOTIFY_CHAR_UUID: UUID = UUID.fromString("0000fff4-0000-1000-8000-00805f9b34fb")

    fun cmdColor(r: Int, g: Int, b: Int): ByteArray =
        byteArrayOf(0x7E, 0x00, 0x05, 0x03, r.toByte(), g.toByte(), b.toByte(), 0x00, 0xEF.toByte())

    fun cmdBrightness(level: Int): ByteArray =
        byteArrayOf(0x7E, 0x00, 0x01, level.toByte(), 0x00, 0x00, 0x00, 0x00, 0xEF.toByte())

    fun cmdPower(on: Boolean): ByteArray =
        byteArrayOf(0x7E, 0x00, 0x04, if (on) 0x01 else 0x00, 0x00, 0x00, 0x00, 0x00, 0xEF.toByte())

    fun cmdEffect(code: Int, speed: Int = 3): ByteArray =
        byteArrayOf(0x7E, 0x00, 0x03, code.toByte(), speed.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x00, 0xEF.toByte())

    fun cmdSpeed(speed: Int): ByteArray =
        byteArrayOf(0x7E, 0x00, 0x02, speed.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x00, 0xEF.toByte())

    fun cmdColorTemp(warm: Int, cold: Int): ByteArray =
        byteArrayOf(0x7E, 0x00, 0x05, 0x02, warm.toByte(), cold.toByte(), 0x00, 0x00, 0xEF.toByte())

    val EFFECTS = linkedMapOf(
        "Static" to 0x80,
        "Jump RGB" to 0x81,
        "Jump RGBYCMW" to 0x82,
        "Crossfade RGB" to 0x83,
        "Crossfade RGBYCMW" to 0x84,
        "Breathe RGB" to 0x85,
        "Breathe RGBYCMW" to 0x86,
        "Rainbow Fade" to 0x87,
        "Rainbow Pulse" to 0x88,
        "Rainbow Strobe" to 0x89,
        "Color Cycle" to 0x8A,
        "RGB Chase" to 0x8B,
        "Rainbow Chase" to 0x8C,
        "Red Pulse" to 0x8D,
        "Green Pulse" to 0x8E,
        "Blue Pulse" to 0x8F,
        "Yellow Pulse" to 0x90,
        "Cyan Pulse" to 0x91,
        "Magenta Pulse" to 0x92,
        "White Pulse" to 0x93,
        "RG Chase" to 0x94,
        "RB Chase" to 0x95,
        "GB Chase" to 0x96,
        "RG Breathe" to 0x97,
        "RB Breathe" to 0x98,
        "GB Breathe" to 0x99,
        "RGB Breathe Chase" to 0x9A,
    )
}
