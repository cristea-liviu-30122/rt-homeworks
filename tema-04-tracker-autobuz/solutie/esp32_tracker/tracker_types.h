// Tipurile tracker-ului, într-un header separat.
//
// Arduino IDE generează automat prototipuri pentru funcțiile din .ino și le
// pune la începutul fișierului, înaintea tipurilor declarate acolo. O funcție
// cu un parametru LedMode sau Status ar da eroarea "'LedMode' was not declared".
// Tipurile dintr-un header inclus sunt cunoscute înaintea acestor prototipuri.

#pragma once

#include <Arduino.h>

#include "connection.h"  // COMMAND_MAX_LEN

// elementul cozii status: textul mesajului, copiat (xQueueSend copiază elementul)
struct StatusText {
    char text[COMMAND_MAX_LEN];
};

// mesajul "status", după parsare
enum StatusKind { ST_TRACK, ST_NONE, ST_ACK, ST_INVALID };
struct Status {
    StatusKind kind;
    int32_t stops;   // TRACK: stații rămase (0 = autobuzul e în stație)
    int32_t eta_s;   // TRACK: în câte secunde ajunge autobuzul în stație
    int32_t walk_s;  // TRACK, NONE: câte secunde merge utilizatorul până la stație
};

// ce arată LED-ul
enum LedMode {
    LED_IDLE,    // stins: nu urmărește nimic / niciun autobuz spre stație
    LED_SLOW,    // un puls scurt la 2 s: ai timp (marja > 5 min)
    LED_MEDIUM,  // clipire 1 Hz: pregătește-te (marja 2..5 min)
    LED_FAST,    // clipire 4 Hz: pleacă acum (marja < 2 min)
    LED_AT_STOP, // aprins continuu: autobuzul e în stația ta
    LED_STALE,   // dublu puls la 3 s: niciun status de STALE_MS (aplicația oprită?)
    LED_ACK      // 3 clipiri, apoi stins: BOARDED confirmat de aplicație
};

// starea partajată de taskuri, protejată de state_mutex
struct TrackerState {
    LedMode mode;
    uint32_t mode_start_ms;  // millis() la intrarea în mod (pentru tiparul de clipire)
    bool tracking;           // true între primul TRACK/NONE și ACK
};