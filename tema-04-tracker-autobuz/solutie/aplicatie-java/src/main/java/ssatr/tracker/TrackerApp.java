package ssatr.tracker;

import java.util.Scanner;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Tracker personal de autobuz: aplicația Java.
 *
 *   API REST ---> linii, stații ---> alegerea utilizatorului (Selection)
 *   RabbitMQ ---> poziții vehicule ---> Tracker: ETA până la stație
 *   MQTT ------> placa: ssatr/<student>/tracker/status   (la fiecare 5 s)
 *   MQTT <------ placa: ssatr/<student>/tracker/events   (BOARDED, de la butonul BOOT)
 *
 * Mesajele "status" (aplicație -> placă), text separat prin ';':
 *   TRACK;<stații>;<eta_s>;<mers_s>   un autobuz vine spre stație
 *   NONE;<mers_s>                      niciun autobuz spre stație
 *   ACK                                confirmarea lui BOARDED: urmărirea s-a încheiat
 *
 * Mesajele "events" (placă -> aplicație):
 *   BOARDED;<deviceId>;<epoch_ms>      utilizatorul a apăsat BOOT: a urcat în autobuz
 *
 * Rulare (IntelliJ): TrackerApp, VM options -Dsim=true pentru simulare.
 */
public class TrackerApp {

    /** la câte secunde se recalculează estimarea și se trimite plăcii */
    static final int REPORT_PERIOD_S = 5;

    public static void main(String[] args) throws Exception {
        LineCatalog catalog = Config.SIMULATION ? new SimCatalog() : new ApiClient();
        System.out.println(Config.SIMULATION ? "=== MOD SIMULARE ===" : "=== date reale CTP Cluj ===");

        Scanner in = new Scanner(System.in);
        Selection sel = Selection.ask(catalog, in);
        System.out.println("Urmăresc: " + sel);
        int walkS = sel.walkMinutes() * 60;

        // MQTT: abonarea la evenimentele plăcii, înainte de primul status
        MqttConnection mqtt = new MqttConnection("tracker-app-" + Config.STUDENT);
        mqtt.connect();
        BlockingQueue<String> events = new LinkedBlockingQueue<>();
        mqtt.subscribe(Config.EVENTS_TOPIC, events);

        Tracker tracker = new Tracker(sel);
        VehicleSource source = Config.SIMULATION ? new SimVehicleSource(sel) : new RabbitVehicleSource();
        source.start(tracker::update);
        System.out.println("Pozițiile sosesc treptat: imaginea flotei se adună în ~1 minut.");

        // la oprire (■ sau Ctrl+C): placa trece în repaus, conexiunile se închid
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            source.close();
            mqtt.disconnect();
        }));

        // bucla periodică: estimare -> status, la fiecare REPORT_PERIOD_S secunde.
        // Între două rapoarte, aplicația așteaptă evenimente de la placă (poll cu timeout),
        // deci BOARDED este tratat imediat, nu la următorul raport.
        Tracker.Estimate last = null;
        long nextReport = System.currentTimeMillis();
        while (true) {
            long wait = nextReport - System.currentTimeMillis();
            String event = events.poll(Math.max(0, wait), TimeUnit.MILLISECONDS);

            if (event != null) {
                System.out.println("[mqtt] eveniment de la placă: " + event);
                if (event.startsWith("BOARDED")) {
                    mqtt.publish(Config.STATUS_TOPIC, "ACK");
                    System.out.println("Ai urcat" + (last != null ? " în " + last.label() : "")
                            + ". Urmărire încheiată.");
                    break;
                }
                continue;  // alt eveniment: îl ignorăm și așteptăm mai departe
            }

            // a venit momentul raportului periodic
            nextReport += REPORT_PERIOD_S * 1000L;
            Tracker.Estimate e = tracker.estimate();
            String status;
            if (e == null) {
                status = "NONE;" + walkS;
                System.out.println("[tracker] niciun autobuz spre stație (vehicule pe sens: "
                        + tracker.knownVehicles() + ")");
            } else {
                status = "TRACK;" + e.stopsAway() + ";" + e.etaS() + ";" + walkS;
                System.out.printf("[tracker] %s: %d stații, %d m, ETA %d s (poziție veche de %d s)%n",
                        e.label(), e.stopsAway(), e.distanceM(), e.etaS(), e.ageS());
            }
            last = e;
            mqtt.publish(Config.STATUS_TOPIC, status);
        }

        Thread.sleep(500);  // timp ca ACK să plece înainte de închidere
        System.exit(0);
    }
}