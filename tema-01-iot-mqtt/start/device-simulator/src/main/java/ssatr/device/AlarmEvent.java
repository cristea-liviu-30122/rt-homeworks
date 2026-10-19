package ssatr.device;

/** Eveniment de alarmă: temperatura și pragul depășit, capturate în momentul detecției. */
public class AlarmEvent {
    final double temperature;
    final double threshold;

    public AlarmEvent(double temperature, double threshold) {
        this.temperature = temperature;
        this.threshold = threshold;
    }
}