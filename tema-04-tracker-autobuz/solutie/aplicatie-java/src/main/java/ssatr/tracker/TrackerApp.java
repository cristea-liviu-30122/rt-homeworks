package ssatr.tracker;

import java.util.Scanner;

/**
 * Tracker personal de autobuz: aplicația Java.
 *
 *   API REST ---> linii, stații ---> alegerea utilizatorului (Selection)
 *   RabbitMQ ---> poziții vehicule ---> Tracker: ETA până la stație
 *   MQTT <------> placa ESP32-C3: status / BOARDED               (pasul 3)
 *
 * Rulare:  mvn -q compile exec:java                 (date reale)
 *          mvn -q compile exec:java -Dsim=true      (simulare)
 */
public class TrackerApp {

    /** la câte secunde se recalculează estimarea */
    static final int REPORT_PERIOD_S = 5;

    public static void main(String[] args) throws Exception {
        LineCatalog catalog = Config.SIMULATION ? new SimCatalog() : new ApiClient();
        System.out.println(Config.SIMULATION ? "=== MOD SIMULARE ===" : "=== date reale CTP Cluj ===");

        Scanner in = new Scanner(System.in);
        Selection sel = Selection.ask(catalog, in);
        System.out.println("Urmăresc: " + sel);

        Tracker tracker = new Tracker(sel);
        VehicleSource source = Config.SIMULATION ? new SimVehicleSource(sel) : new RabbitVehicleSource();
        source.start(tracker::update);
        Runtime.getRuntime().addShutdownHook(new Thread(source::close));  // închide conexiunea la oprire
        System.out.println("Pozițiile sosesc treptat: imaginea flotei se adună în ~1 minut.");

        // bucla periodică: estimarea la fiecare REPORT_PERIOD_S secunde
        while (true) {
            Tracker.Estimate e = tracker.estimate();
            if (e == null) {
                System.out.println("[tracker] niciun autobuz spre stație (vehicule pe sens: "
                        + tracker.knownVehicles() + ")");
            } else {
                System.out.printf("[tracker] %s: %d stații, %d m, ETA %d s (poziție veche de %d s)%n",
                        e.label(), e.stopsAway(), e.distanceM(), e.etaS(), e.ageS());
            }
            Thread.sleep(REPORT_PERIOD_S * 1000L);
        }
    }
}