package ssatr.service;

import java.util.concurrent.BlockingQueue;

/**
 * Preia mesajele de un anumit tip ("telemetry", "alarm" sau "status") din coada
 * inbox, le salvează și actualizează dashboard-ul.
 *
 * Formatul mesajelor (câmpuri separate prin ";"):
 *   telemetry:  deviceId;timestamp;temperatura
 *   alarm:      deviceId;timestamp;temperatura;prag
 *   status:     deviceId;timestamp;text
 */
public class MessageProcessor implements Runnable {

    private final String kind;
    private final BlockingQueue<String> inbox;
    private final CsvStorage storage;
    private final Dashboard dashboard;

    public MessageProcessor(String kind, BlockingQueue<String> inbox, CsvStorage storage, Dashboard dashboard) {
        this.kind = kind;
        this.inbox = inbox;
        this.storage = storage;
        this.dashboard = dashboard;
    }

    @Override
    public void run() {
        while (true) {
            String message;
            try {
                message = inbox.take();
            } catch (InterruptedException e) {
                return;
            }

            try {
                storage.append(kind, message);   // salvat exact cum a sosit
                process(message);
            } catch (Exception e) {
                // un mesaj invalid nu oprește thread-ul
                System.out.println("[" + kind + "] mesaj invalid ignorat: " + message + " (" + e.getMessage() + ")");
            }
        }
    }

    private void process(String message) {
        String[] f = message.split(";");
        if (f.length < 3) {
            throw new IllegalArgumentException("prea putine campuri");
        }
        String deviceId = f[0];
        long timestamp = Long.parseLong(f[1]);

        switch (kind) {
            case "telemetry":
                dashboard.updateTelemetry(deviceId, timestamp, Double.parseDouble(f[2]));
                break;
            case "alarm":
                if (f.length < 4) {
                    throw new IllegalArgumentException("alarma fara prag");
                }
                dashboard.updateAlarm(deviceId, timestamp);
                System.out.println(">>> ALARMA " + deviceId + ": temperatura " + f[2] + " > prag " + f[3]);
                break;
            case "status":
                dashboard.updateStatus(deviceId, timestamp);
                System.out.println(">>> " + deviceId + ": " + f[2]);
                break;
            default:
                throw new IllegalArgumentException("tip necunoscut " + kind);
        }
    }
}
