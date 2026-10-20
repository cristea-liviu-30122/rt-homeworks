// Device-ul IoT din tema 01, de data aceasta pe un ESP32-C3 Super Mini.
//
// Aceeași logică și același protocol ca device-simulator din tema 01, cu trei
// taskuri FreeRTOS în loc de trei thread-uri Java:
//
//   task periodic --(coada de alarme)--> task sporadic
//        |                                     |
//        +---- telemetry ---> MQTT <--- alarm -+
//                              |
//                          commands --> command_received() --(coada de comenzi)--> task aperiodic ---> status
//
// Gata implementate (nu trebuie modificate):
//   config.h         setările: Wi-Fi, broker, STUDENT, valorile inițiale; DEVICE_ID
//                    se citește din adresa MAC a plăcii
//   connection.h/cpp Wi-Fi, ora exactă (SNTP), clientul MQTT, mqtt_publish(), epoch_ms()
//
// Așa cum este, schița se conectează la Wi-Fi și la broker și afișează pe
// Serial comenzile primite. Taskurile le scrieți voi: TODO 0 ... TODO 3.
// Exemplele de pornire sunt în laboratorul 0 din rt-labs-esp32c3; vezi
// exemple/README.md din folderul temei.
//
// Placa: ESP32C3 Dev Module, USB CDC On Boot: Enabled. Serial Monitor: 115200 baud.

#include "config.h"
#include "connection.h"

// Temperatura cipului, în °C, de la senzorul intern al ESP32-C3.
// Este temperatura siliciului, nu a camerei: imediat după pornirea Wi-Fi-ului
// poate fi în jur de 50 °C, apoi scade în câteva minute spre 35-40 °C.
// Rezoluția este de aproximativ 1 °C. Ca să provocați alarme, puneți pragul
// cu SET_THRESHOLD cu un grad sub valoarea citită.
float read_temperature(void) {
    return temperatureRead();
}


// Evenimentul de alarmă: temperatura și pragul depășit, capturate la detecție.
// Se pune în coadă prin valoare (xQueueSend copiază struct-ul).
typedef struct {
    float temperature;
    float threshold;
} AlarmEvent;

// O comandă primită: textul însuși, nu un pointer.
typedef struct {
    char text[COMMAND_MAX_LEN];
} Command;

static QueueHandle_t alarmQueue = NULL;
static QueueHandle_t commandQueue = NULL;

// citite de taskul periodic, modificate de taskul aperiodic
static volatile uint32_t periodMs = PERIOD_MS_INITIAL;
static volatile float threshold = THRESHOLD_INITIAL;

// Prioritățile, rate monotonic: perioada mai mică -> prioritate mai mare
static const UBaseType_t PRIO_PERIODIC  = 5;   // T = 2000 ms
static const UBaseType_t PRIO_SPORADIC  = 4;   // T_min = 5000 ms
static const UBaseType_t PRIO_APERIODIC = 3;   // fără T, termen soft
static const uint32_t TASK_STACK = 4096;       // octeți; snprintf cu %f cere stivă


// TODO 1: TASK PERIODIC, cu perioada T (inițial PERIOD_MS_INITIAL).
void periodic_task(void* param) {
    // r_0: prima eliberare este acum
    TickType_t lastWake = xTaskGetTickCount();
    char msg[96];

    for (;;) {
        // --- jobul k ---
        float t = read_temperature();
        float th = threshold;   // o singură citire per job

        // întâi alarma, fără blocare (timeout 0)
        if (t > th) {
            AlarmEvent ev = {t, th};
            if (xQueueSend(alarmQueue, &ev, 0) != pdTRUE) {
                Serial.println("[periodic] coada de alarme plina, eveniment pierdut");
            }
        }

        snprintf(msg, sizeof(msg), "%s;%lld;%.2f", DEVICE_ID, (long long)epoch_ms(), t);
        mqtt_publish("telemetry", msg);
        Serial.printf("[periodic] %s\n", msg);

        // --- așteptare absolută: r_{k+1} = r_k + T ---
        xTaskDelayUntil(&lastWake, pdMS_TO_TICKS(periodMs));
    }
}

// TODO 2: TASK SPORADIC, tratează alarmele de temperatură.
void sporadic_task(void* param) {
    bool released = false;     // a existat deja o alarmă publicată?
    uint32_t lastRelease = 0;  // millis() la ultima alarmă publicată
    AlarmEvent ev;
    char msg[96];

    for (;;) {
        xQueueReceive(alarmQueue, &ev, portMAX_DELAY);  // sosirea unui eveniment

        uint32_t now = millis();
        uint32_t since = now - lastRelease;  // fără semn: corect și la depășirea millis()

        if (released && since < MIN_INTERARRIVAL_MS) {
            Serial.printf("[sporadic] RESPINS %.2f (au trecut %lu ms < %lu ms)\n",
                          ev.temperature, (unsigned long)since, (unsigned long)MIN_INTERARRIVAL_MS);
            continue;
        }

        released = true;
        lastRelease = now;
        snprintf(msg, sizeof(msg), "%s;%lld;%.2f;%.2f",
                 DEVICE_ID, (long long)epoch_ms(), ev.temperature, ev.threshold);
        mqtt_publish("alarm", msg);
        Serial.printf("[sporadic] ALARMA %s\n", msg);
    }
}

// TODO 3: TASK APERIODIC, execută comenzile primite de la business service.
// Execută o comandă și scrie rezultatul în result ("OK ..." sau "ERROR ...").
static void execute_command(const char* text, char* result, size_t size) {
    char name[24] = "";
    char arg[32] = "";
    int n = sscanf(text, "%23s %31s", name, arg);

    if (n < 1) {
        snprintf(result, size, "ERROR comanda goala");
    } else if (strcasecmp(name, "SET_INTERVAL") == 0) {
        char* end;
        long ms = strtol(arg, &end, 10);
        if (n < 2) {
            snprintf(result, size, "ERROR lipseste parametrul pentru SET_INTERVAL");
        } else if (*end != '\0' || ms <= 0) {
            snprintf(result, size, "ERROR parametru invalid: %s", arg);
        } else {
            periodMs = (uint32_t)ms;
            snprintf(result, size, "OK SET_INTERVAL %ld", ms);
        }
    } else if (strcasecmp(name, "SET_THRESHOLD") == 0) {
        char* end;
        float value = strtof(arg, &end);
        if (n < 2) {
            snprintf(result, size, "ERROR lipseste parametrul pentru SET_THRESHOLD");
        } else if (*end != '\0') {
            snprintf(result, size, "ERROR parametru invalid: %s", arg);
        } else {
            threshold = value;
            snprintf(result, size, "OK SET_THRESHOLD %.2f", value);
        }
    } else if (strcasecmp(name, "RESET") == 0) {
        periodMs = PERIOD_MS_INITIAL;
        threshold = THRESHOLD_INITIAL;
        snprintf(result, size, "OK RESET");
    } else if (strcasecmp(name, "STATUS") == 0) {
        snprintf(result, size, "OK STATUS interval=%lu prag=%.2f",
                 (unsigned long)periodMs, threshold);
    } else {
        snprintf(result, size, "ERROR comanda necunoscuta: %s", name);
    }
}

void aperiodic_task(void* param) {
    Command cmd;
    char result[80];
    char msg[128];

    for (;;) {
        xQueueReceive(commandQueue, &cmd, portMAX_DELAY);  // eliberarea unui job aperiodic

        execute_command(cmd.text, result, sizeof(result));

        snprintf(msg, sizeof(msg), "%s;%lld;%s", DEVICE_ID, (long long)epoch_ms(), result);
        mqtt_publish("status", msg);
        Serial.printf("[aperiodic] %s\n", msg);
    }
}

// Apelată de clientul MQTT pentru fiecare comandă primită (vezi connection.h).
void command_received(const char* text) {
    Serial.printf("[mqtt] comandă primită: %s\n", text);
    // TODO 3: puneți comanda în coada de comenzi, fără să vă blocați
    Command cmd;
    strncpy(cmd.text, text, sizeof(cmd.text) - 1);
    cmd.text[sizeof(cmd.text) - 1] = '\0';
    if (xQueueSend(commandQueue, &cmd, 0) != pdTRUE) {
        Serial.println("[mqtt] coada de comenzi plina, comanda pierduta");
    }
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
    Serial.printf("=== tema 02: STUDENT %s, DEVICE_ID %s ===\n", STUDENT, DEVICE_ID);

    wifi_connect();
    time_sync();

    // TODO 0 (a): creați cele două cozi (xQueueCreate): coada de alarme și
    alarmQueue = xQueueCreate(5, sizeof(AlarmEvent));
    commandQueue = xQueueCreate(10, sizeof(Command));

    mqtt_start();

    // TODO 0 (b): porniți cele trei taskuri (xTaskCreate). După mqtt_start(),
    xTaskCreate(periodic_task, "periodic", TASK_STACK, NULL, PRIO_PERIODIC, NULL);
    xTaskCreate(sporadic_task,  "sporadic",  TASK_STACK, NULL, PRIO_SPORADIC,  NULL);
    xTaskCreate(aperiodic_task, "aperiodic", TASK_STACK, NULL, PRIO_APERIODIC, NULL);
}

void loop() {
    // Totul se face în taskuri: loop() nu are nimic de făcut.
    delay(1000);
}
