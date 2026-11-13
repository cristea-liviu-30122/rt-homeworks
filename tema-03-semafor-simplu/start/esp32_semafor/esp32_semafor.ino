// Semafor pentru pietoni, cu buton, pe ESP32-C3 Super Mini.
//
// Placa este controlerul semaforului; lămpile sunt desenate de dashboard, care
// le aprinde după mesajele "lights" publicate de placă. Patru activități:
//
//   ISR buton BOOT ----+
//                      +--(coada butonului)--> task sporadic
//   command_received --+   (BUTTON)                 |
//                                                   v
//                                         [ starea semaforului ] <-- mutex
//                                            ^          |
//                                            |          v
//                         task periodic de control   task periodic de ieșire
//                         (fazele, T = 100 ms)       (lămpile, T = 500 ms) --> lights
//
// Gata implementate (nu trebuie modificate):
//   config.h         setările: Wi-Fi, broker, STUDENT, pinii, perioadele, duratele fazelor
//   connection.h/cpp Wi-Fi, ora exactă (SNTP), clientul MQTT, mqtt_publish(), epoch_ms()
//
// Așa cum este, schița se conectează la Wi-Fi și la broker și afișează pe
// Serial comenzile primite. Restul îl scrieți voi: TODO 0 ... TODO 3.
//
// Placa: ESP32C3 Dev Module, USB CDC On Boot: Enabled. Serial Monitor: 115200 baud.

#include "config.h"
#include "connection.h"

// Fazele semaforului (vezi tabelul și diagrama din README.md). Numele din
// PHASE_NAMES sunt cele din mesajul "lights", în aceeași ordine ca în enum.
enum Phase { CAR_GREEN, CAR_YELLOW, ALL_RED, PED_GREEN };
const char* const PHASE_NAMES[] = {"CAR_GREEN", "CAR_YELLOW", "ALL_RED", "PED_GREEN"};

// Culorile unei lămpi, cu numele din mesajul "lights".
enum Light { RED, YELLOW, GREEN };
const char* const LIGHT_NAMES[] = {"RED", "YELLOW", "GREEN"};

// TODO 0 (c): STAREA SEMAFORULUI, partajată de taskuri și protejată de un mutex (xSemaphoreCreateMutex)
struct SemaforState {
    Phase phase;              // faza curentă
    uint32_t phase_start_ms;  // millis() la intrarea în fază
    bool ped_request;         // cererea pietonului (lampa AȘTEPTAȚI)
};
static SemaforState state = {CAR_GREEN, 0, false};
static SemaphoreHandle_t state_mutex = NULL;

// elementul cozii butonului: o apăsare, cu momentul sosirii
enum PressSource : uint8_t { SRC_BOOT, SRC_DASHBOARD };
struct ButtonPress {
    uint32_t t_ms;       // momentul apăsării (ms de la pornire), pus de cel care trimite
    PressSource source;  // BOOT sau dashboard
};
static QueueHandle_t button_queue = NULL;

// TODO 1: TASK PERIODIC DE CONTROL, cu perioada CONTROL_PERIOD_MS.
//   La fiecare job, cu mutexul luat, decide dacă faza curentă s-a terminat și
//   trece în faza următoare, după tabelul din README.md:
//     CAR_GREEN  -> CAR_YELLOW  după CAR_GREEN_MIN_MS, doar dacă există o cerere
//     CAR_YELLOW -> ALL_RED     după CAR_YELLOW_MS
//     ALL_RED    -> PED_GREEN   după ALL_RED_MS; aici se șterge cererea pietonului
//     PED_GREEN  -> CAR_GREEN   după PED_GREEN_MS
//   La fiecare schimbare de fază notați momentul intrării în faza nouă.
//   Taskul nu publică nimic: doar actualizează starea. După ce eliberează
//   mutexul, afișează pe Serial faza nouă, dacă s-a schimbat.
//   Eliberările respectă grila r_k = r_0 + kT: așteptare absolută (xTaskDelayUntil).
//   Durata unei faze se măsoară cu un timp monoton (millis()), nu cu epoch_ms().

// TODO 2: TASK PERIODIC DE IEȘIRE, cu perioada T = OUTPUT_PERIOD_MS.
void output_task(void* arg) {
    TickType_t last_wake = xTaskGetTickCount();
    char msg[128];
    for (;;) {
        // 1. copia stării, cu mutexul ținut cât mai puțin
        xSemaphoreTake(state_mutex, portMAX_DELAY);
        SemaforState s = state;
        xSemaphoreGive(state_mutex);

        // 2. lămpile, din fază
        Light car = RED, ped = RED;
        switch (s.phase) {
            case CAR_GREEN:  car = GREEN;  ped = RED;   break;
            case CAR_YELLOW: car = YELLOW; ped = RED;   break;
            case ALL_RED:    car = RED;    ped = RED;   break;
            case PED_GREEN:  car = RED;    ped = GREEN; break;
        }

        // 3. numărătoarea inversă, rotunjită în sus; 0 în afara PED_GREEN
        uint32_t seconds = 0;
        if (s.phase == PED_GREEN) {
            uint32_t elapsed = millis() - s.phase_start_ms;
            uint32_t remaining = (elapsed < PED_GREEN_MS) ? PED_GREEN_MS - elapsed : 0;
            seconds = (remaining + 999) / 1000;
        }

        // 4. mesajul lights și LED-ul plăcii (aprins pe LOW)
        snprintf(msg, sizeof(msg), "%s;%lld;%s;%s;%s;%d;%lu",
                 DEVICE_ID, (long long)epoch_ms(), PHASE_NAMES[s.phase],
                 LIGHT_NAMES[car], LIGHT_NAMES[ped],
                 s.ped_request ? 1 : 0, (unsigned long)seconds);
        mqtt_publish("lights", msg);
        digitalWrite(LED_PIN, s.ped_request ? LOW : HIGH);

        xTaskDelayUntil(&last_wake, pdMS_TO_TICKS(OUTPUT_PERIOD_MS));
    }
}

// TODO 3: TASK SPORADIC, tratează apăsările butonului pentru pietoni.
//   Apăsările vin din două surse, în aceeași coadă (coada butonului):
//   - butonul BOOT de pe placă, printr-o rutină de întrerupere (ISR, mai jos)
//   - butonul virtual din dashboard: comanda "BUTTON", pusă în coadă de
//     command_received()
//   Taskul:
//   - așteaptă o apăsare din coada butonului
//   - dacă de la ultima apăsare acceptată au trecut mai puțin de
//     MIN_INTERARRIVAL_MS, o respinge (mesaj pe Serial). Așa se filtrează și
//     contactele imperfecte ale butonului (bounce), care produc mai multe
//     întreruperi la o singură apăsare.
//   - altfel înregistrează cererea pietonului în starea semaforului (cu mutexul)
//     și afișează pe Serial că a acceptat-o
//   Momentul sosirii se ia când apăsarea intră în coadă (în ISR sau în
//   command_received()), nu când o preia taskul, și se pune în elementul cozii.

// TODO 3: RUTINA DE ÎNTRERUPERE a butonului BOOT, înregistrată în setup() cu
//   attachInterrupt(digitalPinToInterrupt(BUTTON_PIN), button_isr, FALLING).
//   Scurtă: pune o apăsare în coada butonului cu xQueueSendFromISR() și atât.
//   Fără Serial, fără mqtt_publish(), fără mutex. Se declară cu IRAM_ATTR:
//
//   void IRAM_ATTR button_isr(void) { ... }

// Apelată de clientul MQTT pentru fiecare comandă primită (vezi connection.h).
void command_received(const char* text) {
    Serial.printf("[mqtt] comandă primită: %s\n", text);
    // TODO 3: "BUTTON" merge în coada butonului (o apăsare, la fel ca cea din
    //   ISR), fără să vă blocați (timeout 0). Celelalte comenzi se ignoră:
    //   în această temă placa nu are alte comenzi.
}

void setup() {
    Serial.begin(115200);
    // Fără așteptare la scriere: cu placa la USB, dar fără Serial Monitor deschis,
    // fiecare Serial.printf() ar aștepta până la 2 s să fie citit și ar întârzia
    // taskul care scrie. Așa, textul pe care nu-l citește nimeni se pierde.
    Serial.setTxTimeoutMs(0);
    uint32_t t = millis();
    while (!Serial && millis() - t < 3000) delay(10);  // așteptăm Serial Monitor, cel mult 3 s
    delay(200);
    Serial.println();
    Serial.printf("=== tema 03: STUDENT %s, DEVICE_ID %s ===\n", STUDENT, DEVICE_ID);

    pinMode(LED_PIN, OUTPUT);
    digitalWrite(LED_PIN, HIGH);  // LED stins
    pinMode(BUTTON_PIN, INPUT_PULLUP);

    wifi_connect();
    time_sync();

    // TODO 0 (a): creați mutexul stării (xSemaphoreCreateMutex) și coada butonului (xQueueCreate)
    state_mutex = xSemaphoreCreateMutex();
    button_queue = xQueueCreate(10, sizeof(ButtonPress));
    state.phase = CAR_GREEN;
    state.phase_start_ms = millis();
    state.ped_request = false;

    mqtt_start();

    // TODO 0 (b): porniți cele trei taskuri (xTaskCreate)
    // taskurile (stiva în octeți), apoi întreruperea
    // priorități: sporadic 5 > control 4 (T = 100 ms) > ieșire 3 (T = 500 ms), Rate Monotonic
    xTaskCreate(output_task, "output", 4096, NULL, 3, NULL);
}

void loop() {
    // Totul se face în taskuri: loop() nu are nimic de făcut.
    delay(1000);
}
