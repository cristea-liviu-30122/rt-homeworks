// Tracker personal de autobuz pe ESP32-C3 Super Mini.
//
// Aplicația Java urmărește autobuzul și trimite, la fiecare 5 s, pe topic-ul
// "status": ETA-ul, stațiile rămase și timpul de mers al utilizatorului. Placa
// decide ce arată LED-ul și trimite BOARDED când utilizatorul apasă BOOT.
//
//   MQTT "status" --> command_received --(coada status)--> task logică (aperiodic)
//                                                             |  marja = ETA - mers
//                                                             v
//                                                  [ modul LED-ului ] <-- mutex
//                                                             |
//                                                             v
//                                          task LED (periodic, T = 50 ms) --> GPIO8
//
//   ISR buton BOOT --(coada butonului)--> task buton (sporadic) --> MQTT "events": BOARDED
//
// Placa: ESP32C3 Dev Module, USB CDC On Boot: Enabled. Serial Monitor: 115200 baud.

#include "config.h"
#include "connection.h"
#include "tracker_types.h"

// ---------------------------------------------------------------------------
// Mesajul "status" primit de la aplicația Java
// ---------------------------------------------------------------------------

static QueueHandle_t status_queue = NULL;  // elementul: StatusText (tracker_types.h)

// "TRACK;3;400;240" / "NONE;240" / "ACK" -> Status
static Status parse_status(const char* text) {
    Status s = {ST_INVALID, 0, 0, 0};
    long a, b, c;  // sscanf cu %ld scrie long
    if (sscanf(text, "TRACK;%ld;%ld;%ld", &a, &b, &c) == 3) {
        s = {ST_TRACK, (int32_t)a, (int32_t)b, (int32_t)c};
    } else if (sscanf(text, "NONE;%ld", &c) == 1) {
        s = {ST_NONE, 0, 0, (int32_t)c};
    } else if (strcmp(text, "ACK") == 0) {
        s.kind = ST_ACK;
    }
    return s;
}

// Apelată de clientul MQTT pentru fiecare mesaj "status" (vezi connection.h).
// Rulează pe taskul clientului MQTT: doar copiază mesajul în coadă.
void command_received(const char* text) {
    StatusText m;
    strncpy(m.text, text, sizeof(m.text) - 1);
    m.text[sizeof(m.text) - 1] = '\0';
    if (xQueueSend(status_queue, &m, 0) != pdTRUE) {  // timeout 0: nu blocăm clientul MQTT
        Serial.println("[mqtt] coada status plină, mesaj pierdut");
    }
}

// ---------------------------------------------------------------------------
// Starea tracker-ului: modul LED-ului, partajat de taskuri, protejat de mutex
// ---------------------------------------------------------------------------

const char* const LED_NAMES[] = {"IDLE", "SLOW", "MEDIUM", "FAST", "AT_STOP", "STALE", "ACK"};

static TrackerState state = {LED_IDLE, 0, false};
static SemaphoreHandle_t state_mutex = NULL;

// schimbă modul; mode_start_ms se resetează doar la o schimbare reală, ca tiparul să nu sacadeze
static void set_mode(LedMode mode, bool tracking) {
    bool changed;
    xSemaphoreTake(state_mutex, portMAX_DELAY);
    changed = (state.mode != mode);
    if (changed) {
        state.mode = mode;
        state.mode_start_ms = millis();
    }
    state.tracking = tracking;
    xSemaphoreGive(state_mutex);
    if (changed) Serial.printf("[logica] LED: %s\n", LED_NAMES[mode]);
}

// marja = cât timp mai ai până trebuie să pleci -> modul LED-ului
static LedMode mode_for(const Status& s) {
    if (s.kind == ST_NONE) return LED_IDLE;
    if (s.stops == 0) return LED_AT_STOP;
    int32_t margin = s.eta_s - s.walk_s;
    if (margin > MARGIN_RELAXED_S) return LED_SLOW;
    if (margin > MARGIN_LEAVE_S) return LED_MEDIUM;
    return LED_FAST;
}

// ---------------------------------------------------------------------------
// Task de logică (aperiodic): consumă mesajele status și decide modul LED-ului.
// Așteaptă în coadă cel mult LOGIC_TIMEOUT_MS, ca să observe și lipsa mesajelor.
// ---------------------------------------------------------------------------
void logic_task(void* arg) {
    StatusText m;
    uint32_t last_status_ms = 0;
    bool tracking = false;
    for (;;) {
        if (xQueueReceive(status_queue, &m, pdMS_TO_TICKS(LOGIC_TIMEOUT_MS)) == pdTRUE) {
            Status s = parse_status(m.text);
            switch (s.kind) {
                case ST_TRACK:
                case ST_NONE:
                    tracking = true;
                    last_status_ms = millis();
                    if (s.kind == ST_TRACK) {
                        Serial.printf("[logica] %s -> %ld stații, ETA %ld s, mers %ld s, marja %ld s\n",
                                      m.text, (long)s.stops, (long)s.eta_s, (long)s.walk_s,
                                      (long)(s.eta_s - s.walk_s));
                    } else {
                        Serial.printf("[logica] %s -> niciun autobuz\n", m.text);
                    }
                    set_mode(mode_for(s), true);
                    break;
                case ST_ACK:
                    tracking = false;
                    Serial.println("[logica] ACK: urcarea confirmată, urmărire încheiată");
                    set_mode(LED_ACK, false);
                    break;
                default:
                    Serial.printf("[logica] mesaj necunoscut ignorat: %s\n", m.text);
            }
        }
        // fără mesaje de STALE_MS cât urmărim: datele nu mai sunt de încredere
        if (tracking && millis() - last_status_ms > STALE_MS) {
            set_mode(LED_STALE, true);
        }
    }
}

// === PASUL 5: taskul LED (aici) ===
// ---------------------------------------------------------------------------
// Task LED (periodic, T = LED_PERIOD_MS): generează tiparul modului curent.
// ---------------------------------------------------------------------------

// true dacă LED-ul trebuie aprins la t ms după intrarea în mod
static bool led_on(LedMode mode, uint32_t t) {
    switch (mode) {
        case LED_SLOW:    return (t % 2000) < 100;                 // puls 100 ms la 2 s
        case LED_MEDIUM:  return (t % 1000) < 500;                 // 1 Hz
        case LED_FAST:    return (t % 250) < 125;                  // 4 Hz
        case LED_AT_STOP: return true;                             // continuu
        case LED_STALE: {                                          // două pulsuri la 3 s
            uint32_t p = t % 3000;
            return p < 100 || (p >= 250 && p < 350);
        }
        case LED_ACK:     return t < 900 && (t % 300) < 150;       // 3 clipiri, apoi stins
        default:          return false;                            // IDLE
    }
}

void led_task(void* arg) {
    TickType_t last_wake = xTaskGetTickCount();
    for (;;) {
        // copia stării, cu mutexul ținut cât mai puțin
        xSemaphoreTake(state_mutex, portMAX_DELAY);
        TrackerState s = state;
        xSemaphoreGive(state_mutex);

        bool on = led_on(s.mode, millis() - s.mode_start_ms);
        digitalWrite(LED_PIN, on ? LOW : HIGH);  // LED-ul plăcii se aprinde pe LOW

        xTaskDelayUntil(&last_wake, pdMS_TO_TICKS(LED_PERIOD_MS));
    }
}

// === PASUL 6: butonul BOOT (aici) ===

void setup() {
    Serial.begin(115200);
    // Fără așteptare la scriere: fără Serial Monitor deschis, Serial.printf()
    // ar întârzia taskul care scrie.
    Serial.setTxTimeoutMs(0);
    uint32_t t = millis();
    while (!Serial && millis() - t < 3000) delay(10);  // așteptăm Serial Monitor, cel mult 3 s
    delay(200);
    Serial.println();
    Serial.printf("=== tema 04: STUDENT %s, DEVICE_ID %s ===\n", STUDENT, DEVICE_ID);

    pinMode(LED_PIN, OUTPUT);
    digitalWrite(LED_PIN, HIGH);  // LED stins
    pinMode(BUTTON_PIN, INPUT_PULLUP);

    wifi_connect();
    time_sync();

    // mutexul și cozile, înainte de mqtt_start(): command_received() folosește coada status
    state_mutex = xSemaphoreCreateMutex();
    status_queue = xQueueCreate(10, sizeof(StatusText));
    // === PASUL 6: coada butonului (aici) ===

    mqtt_start();

    // priorități: sporadic (buton) 5 > LED periodic (T = 50 ms) 4 > logică aperiodică 3
    // === PASUL 6: taskul butonului (aici) ===
    // === PASUL 5: taskul LED (aici) ===
    xTaskCreate(led_task, "led", 2048, NULL, 4, NULL);

    xTaskCreate(logic_task, "logic", 4096, NULL, 3, NULL);

    // === PASUL 6: întreruperea butonului (aici) ===
}

void loop() {
    // Totul se face în taskuri: loop() nu are nimic de făcut.
    delay(1000);
}