#pragma once

#include <cstdint>
#include <NimBLEDevice.h>

// --- GATT UUIDs for ELK-BLEDOM ---
static const NimBLEUUID ELK_SERVICE_UUID("FFF0");
static const NimBLEUUID ELK_WRITE_CHAR_UUID("FFF3");

// --- Frame constants ---
static const uint8_t FRAME_START = 0x7E;
static const uint8_t FRAME_END   = 0xEF;

// --- Command builders ---
// All functions write exactly 9 bytes to `out`.

static inline void buildColorCmd(uint8_t r, uint8_t g, uint8_t b, uint8_t* out) {
    out[0] = FRAME_START;
    out[1] = 0x00;
    out[2] = 0x05;
    out[3] = 0x03;
    out[4] = r;
    out[5] = g;
    out[6] = b;
    out[7] = 0x00;
    out[8] = FRAME_END;
}

static inline void buildBrightnessCmd(uint8_t level, uint8_t* out) {
    out[0] = FRAME_START;
    out[1] = 0x00;
    out[2] = 0x01;
    out[3] = level;
    out[4] = 0x00;
    out[5] = 0x00;
    out[6] = 0x00;
    out[7] = 0x00;
    out[8] = FRAME_END;
}

static inline void buildPowerCmd(bool on, uint8_t* out) {
    out[0] = FRAME_START;
    out[1] = 0x00;
    out[2] = 0x04;
    out[3] = on ? 0x01 : 0x00;
    out[4] = 0x00;
    out[5] = 0x00;
    out[6] = on ? 0x00 : 0xFF;
    out[7] = 0x00;
    out[8] = FRAME_END;
}

static inline void buildEffectCmd(uint8_t code, uint8_t speed, uint8_t* out) {
    out[0] = FRAME_START;
    out[1] = 0x00;
    out[2] = 0x03;
    out[3] = code;
    out[4] = speed;
    out[5] = 0x00;
    out[6] = 0x00;
    out[7] = 0x00;
    out[8] = FRAME_END;
}
