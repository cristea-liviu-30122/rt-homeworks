package ssatr.device;

import java.util.concurrent.BlockingQueue;
import java.util.Locale;

/**
 * TASK APERIODIC: execută comenzile primite de la business service.
 *
 * Comenzile sosesc oricând, pe MQTT, fără un interval minim între ele, și sunt
 * puse în coada commands. Fiecare comandă eliberează un job al taskului.
 * Comenzi care trebuie suportate:
 *
 *   SET_INTERVAL <ms>       schimbă perioada T a taskului periodic
 *   SET_THRESHOLD <valoare> schimbă pragul de alarmă
 *   RESET                   readuce senzorul la valoarea inițială
 *   STATUS                  raportează perioada și pragul curente
 *
 * După executarea fiecărei comenzi, device-ul publică pe Config.statusTopic(deviceId)
 * o confirmare: "deviceId;timestamp;text", de exemplu
 * "device-1;1727600000000;OK SET_INTERVAL 1000".
 */
public class AperiodicCommandTask implements Runnable {

    private final String deviceId;
    private final TemperatureSensor sensor;
    private final MqttConnection mqtt;
    private final BlockingQueue<String> commands;
    private final PeriodicTelemetryTask periodic;

    public AperiodicCommandTask(String deviceId, TemperatureSensor sensor, MqttConnection mqtt,
                                BlockingQueue<String> commands, PeriodicTelemetryTask periodic) {
        this.deviceId = deviceId;
        this.sensor = sensor;
        this.mqtt = mqtt;
        this.commands = commands;
        this.periodic = periodic;
    }

    @Override
    public void run() {
        while (true) {
            String command;
            try {
                command = commands.take();         // eliberarea unui job aperiodic
            } catch (InterruptedException e) {
                return;
            }

            String result;
            try {
                result = execute(command.trim());
            } catch (NumberFormatException e) {
                result = "ERROR parametru invalid in: " + command;
            } catch (Exception e) {
                // nicio comandă greșită nu oprește taskul
                result = "ERROR " + e.getMessage();
            }

            String msg = deviceId + ";" + System.currentTimeMillis() + ";" + result;
            mqtt.publish(Config.statusTopic(deviceId), msg);
            System.out.println("[aperiodic] " + msg);
        }
    }

    private String execute(String command) {
        String[] parts = command.split("\\s+");
        String name = parts[0].toUpperCase();

        switch (name) {
            case "SET_INTERVAL": {
                long ms = Long.parseLong(requireParam(parts));
                if (ms <= 0) {
                    throw new IllegalArgumentException("intervalul trebuie sa fie > 0");
                }
                periodic.setPeriodMs(ms);
                return "OK SET_INTERVAL " + ms;
            }
            case "SET_THRESHOLD": {
                double value = Double.parseDouble(requireParam(parts));
                periodic.setThreshold(value);
                return String.format(Locale.US, "OK SET_THRESHOLD %.2f", value);
            }
            case "RESET":
                sensor.reset();
                return "OK RESET";
            case "STATUS":
                return String.format(Locale.US, "OK STATUS interval=%d prag=%.2f",
                        periodic.getPeriodMs(), periodic.getThreshold());
            default:
                throw new IllegalArgumentException("comanda necunoscuta: " + command);
        }
    }

    private static String requireParam(String[] parts) {
        if (parts.length < 2) {
            throw new IllegalArgumentException("lipseste parametrul pentru " + parts[0]);
        }
        return parts[1];
    }
}
