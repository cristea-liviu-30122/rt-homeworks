package ssatr.service;

import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Ultima stare cunoscută a fiecărui device.
 * Actualizată de thread-urile MessageProcessor, citită de CommandConsole:
 * toate metodele publice sunt synchronized pe același obiect.
 */
public class Dashboard {

    private static class DeviceState {
        double lastTemperature = Double.NaN;   // NaN = nicio telemetrie încă
        long lastMessageMillis;
        int alarms;
    }

    // TreeMap: device-urile apar sortate după ID
    private final Map<String, DeviceState> devices = new TreeMap<>();

    private DeviceState state(String deviceId) {
        return devices.computeIfAbsent(deviceId, id -> new DeviceState());
    }

    public synchronized void updateTelemetry(String deviceId, long timestamp, double temperature) {
        DeviceState s = state(deviceId);
        s.lastTemperature = temperature;
        s.lastMessageMillis = Math.max(s.lastMessageMillis, timestamp);
    }

    public synchronized void updateAlarm(String deviceId, long timestamp) {
        DeviceState s = state(deviceId);
        s.alarms++;
        s.lastMessageMillis = Math.max(s.lastMessageMillis, timestamp);
    }

    public synchronized void updateStatus(String deviceId, long timestamp) {
        DeviceState s = state(deviceId);
        s.lastMessageMillis = Math.max(s.lastMessageMillis, timestamp);
    }

    public synchronized void print() {
        if (devices.isEmpty()) {
            System.out.println("(niciun device inca)");
            return;
        }
        long now = System.currentTimeMillis();
        System.out.printf("%-12s %8s   %-15s %6s%n", "DEVICE", "TEMP", "ULTIMUL MESAJ", "ALARME");
        for (Map.Entry<String, DeviceState> e : devices.entrySet()) {
            DeviceState s = e.getValue();
            String temp = Double.isNaN(s.lastTemperature) ? "-"
                    : String.format(Locale.US, "%.2f", s.lastTemperature);
            long ageSec = Math.max(0, (now - s.lastMessageMillis) / 1000);
            System.out.printf("%-12s %8s   %-15s %6d%n", e.getKey(), temp, "acum " + ageSec + " s", s.alarms);
        }
    }
}