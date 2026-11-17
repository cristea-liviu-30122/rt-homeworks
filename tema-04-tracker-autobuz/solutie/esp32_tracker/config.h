// Setările tracker-ului și numele topic-urilor MQTT.
//
// Brokerul este partajat de toți studenții: STUDENT este identic cu
// Config.STUDENT din aplicația Java.

#pragma once

#include <Arduino.h>

// Wi-Fi: WIFI_SSID și WIFI_PASSWORD, în secrets.h (ignorat de git;
// se creează din secrets.h.example)
#include "secrets.h"

const char* const BROKER_URI = "mqtt://control.aut.utcluj.ro:11188";

// serverul de la care placa ia ora exactă (SNTP), pentru timestamp-uri
const char* const NTP_SERVER = "pool.ntp.org";

const char* const STUDENT = "cristea-liviu";

// ID-ul device-ului: "esp32-" + ultimele 6 cifre hexa ale adresei MAC.
// Se citește la pornire (connection.cpp) și se afișează pe Serial.
extern const char* const DEVICE_ID;

// Pinii plăcii ESP32-C3 Super Mini
const int BUTTON_PIN = 9;  // butonul BOOT: LOW cât timp este apăsat (INPUT_PULLUP)
const int LED_PIN = 8;     // LED-ul albastru de pe placă: aprins pe LOW

// perioada taskului LED: rezoluția tiparelor de clipire
const uint32_t LED_PERIOD_MS = 50;

// Taskul de logică: dacă nu sosește niciun status, se trezește totuși la
// LOGIC_TIMEOUT_MS, ca să verifice dacă datele au devenit vechi.
const uint32_t LOGIC_TIMEOUT_MS = 1000;
// fără status de atâta timp, datele sunt considerate vechi (aplicația Java oprită?)
const uint32_t STALE_MS = 60000;

// Pragurile marjei = ETA - timpul de mers până la stație, în secunde
const int32_t MARGIN_RELAXED_S = 300;  // peste 5 min: ai timp
const int32_t MARGIN_LEAVE_S = 120;    // sub 2 min: pleacă acum

// intervalul minim între sosiri pentru taskul sporadic (apăsările butonului)
const uint32_t MIN_INTERARRIVAL_MS = 1000;

// Scrie în buf topic-ul "ssatr/<STUDENT>/tracker/<kind>". kind este:
//   "status"  de la aplicația Java: "TRACK;stații;eta_s;mers_s", "NONE;mers_s", "ACK"
//   "events"  de la placă: "BOARDED;deviceId;epoch_ms"
inline void make_topic(char* buf, size_t size, const char* kind) {
    snprintf(buf, size, "ssatr/%s/tracker/%s", STUDENT, kind);
}