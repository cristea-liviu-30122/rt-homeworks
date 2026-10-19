package ssatr.device;

import java.util.concurrent.BlockingQueue;
import java.util.Locale;

/**
 * TASK PERIODIC, cu perioada T = periodMs: fiecare job citește senzorul și
 * publică valoarea pe topic-ul de telemetrie.
 *
 * Dacă temperatura depășește pragul, taskul NU tratează el alarma, ci doar
 * semnalează un eveniment în coada alarmEvents. Alarma este tratată de
 * taskul sporadic.
 */
public class PeriodicTelemetryTask implements Runnable {

    private final String deviceId;
    private final TemperatureSensor sensor;
    private final MqttConnection mqtt;
    private final BlockingQueue<AlarmEvent> alarmEvents;

    // volatile: sunt citite aici, dar modificate de taskul aperiodic (alt thread)
    private volatile long periodMs = 2000;
    private volatile double threshold = 28.0;

    public PeriodicTelemetryTask(String deviceId, TemperatureSensor sensor, MqttConnection mqtt,
                                 BlockingQueue<AlarmEvent> alarmEvents) {
        this.deviceId = deviceId;
        this.sensor = sensor;
        this.mqtt = mqtt;
        this.alarmEvents = alarmEvents;
    }

    @Override
    public void run() {
        // r_0: prima eliberare este acum; grila se calculează pe timp monoton
        long nextRelease = System.nanoTime();

        while (!Thread.currentThread().isInterrupted()) {
            // --- jobul k ---
            double t = sensor.read();
            long timestamp = System.currentTimeMillis();
            String msg = deviceId + ";" + timestamp + ";" + String.format(Locale.US, "%.2f", t);
            mqtt.publish(Config.telemetryTopic(deviceId), msg);
            System.out.println("[periodic] " + msg);

            double currentThreshold = threshold;
            if (t > currentThreshold) {
                // nu tratăm alarma aici, doar semnalăm evenimentul taskului sporadic
                alarmEvents.offer(new AlarmEvent(t, currentThreshold));
            }

            // --- așteptare absolută: r_{k+1} = r_k + T ---
            nextRelease += periodMs * 1_000_000L;   // periodMs se citește la fiecare job (SET_INTERVAL)
            long now = System.nanoTime();
            if (nextRelease < now) {
                // jobul a depășit perioada: sărim eliberările pierdute, nu le recuperăm în rafală
                nextRelease = now;
            }
            long waitNs = nextRelease - now;
            try {
                Thread.sleep(waitNs / 1_000_000L, (int) (waitNs % 1_000_000L));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public void setPeriodMs(long periodMs) {
        this.periodMs = periodMs;
    }

    public void setThreshold(double threshold) {
        this.threshold = threshold;
    }

    public long getPeriodMs() {
        return periodMs;
    }

    public double getThreshold() {
        return threshold;
    }
}
