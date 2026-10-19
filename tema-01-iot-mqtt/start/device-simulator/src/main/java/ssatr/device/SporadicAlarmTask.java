package ssatr.device;

import java.util.concurrent.BlockingQueue;
import java.util.Locale;

/**
 * TASK SPORADIC: tratează alarmele de temperatură.
 *
 * Evenimentele sosesc în coada alarmEvents (puse de taskul periodic).
 * Intervalul minim între sosiri este MIN_INTERARRIVAL_MS: între două eliberări
 * ale taskului (două alarme publicate) trebuie să treacă cel puțin atât.
 * Intervalul este impus de cod: evenimentele care sosesc mai des sunt
 * respinse, ca să nu fie inundat brokerul.
 */
public class SporadicAlarmTask implements Runnable {

    static final long MIN_INTERARRIVAL_MS = 5000;

    private final String deviceId;
    private final MqttConnection mqtt;
    private final BlockingQueue<AlarmEvent> alarmEvents;

    public SporadicAlarmTask(String deviceId, MqttConnection mqtt, BlockingQueue<AlarmEvent> alarmEvents) {
        this.deviceId = deviceId;
        this.mqtt = mqtt;
        this.alarmEvents = alarmEvents;
    }

    @Override
    public void run() {
        long lastRelease = Long.MIN_VALUE / 2;   // nicio eliberare anterioară

        while (true) {
            AlarmEvent event;
            try {
                event = alarmEvents.take();       // sosirea unui eveniment
            } catch (InterruptedException e) {
                return;
            }

            long now = System.nanoTime();
            long sinceLastMs = (now - lastRelease) / 1_000_000L;

            if (sinceLastMs < MIN_INTERARRIVAL_MS) {
                // încalcă intervalul minim între sosiri -> respins
                System.out.printf(Locale.US, "[sporadic] RESPINS %.2f (au trecut %d ms < %d ms)%n",
                        event.temperature, sinceLastMs, MIN_INTERARRIVAL_MS);
                continue;
            }

            // eliberare acceptată
            lastRelease = now;
            String msg = deviceId + ";" + System.currentTimeMillis() + ";"
                    + String.format(Locale.US, "%.2f;%.2f", event.temperature, event.threshold);
            mqtt.publish(Config.alarmTopic(deviceId), msg);
            System.out.println("[sporadic] ALARMA " + msg);
        }
    }
}
