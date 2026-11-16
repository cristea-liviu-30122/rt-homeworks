package ssatr.tracker;

import java.util.Scanner;

/**
 * Tracker personal de autobuz: aplicația Java.
 *
 *   API REST ---> linii, stații ---> alegerea utilizatorului (Selection)
 *   RabbitMQ ---> poziții vehicule ---> ETA până la stație      (pasul 2)
 *   MQTT <------> placa ESP32-C3: status / BOARDED               (pasul 3)
 *
 * Rulare:  mvn -q compile exec:java                 (date reale)
 *          mvn -q compile exec:java -Dsim=true      (simulare)
 */
public class TrackerApp {

    public static void main(String[] args) throws Exception {
        LineCatalog catalog = Config.SIMULATION ? new SimCatalog() : new ApiClient();
        System.out.println(Config.SIMULATION ? "=== MOD SIMULARE ===" : "=== date reale CTP Cluj ===");

        Scanner in = new Scanner(System.in);
        Selection sel = Selection.ask(catalog, in);
        System.out.println("Urmăresc: " + sel);
    }
}