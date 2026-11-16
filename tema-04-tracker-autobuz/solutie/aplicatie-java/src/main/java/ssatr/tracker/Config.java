package ssatr.tracker;

/**
 * Setările aplicației: adresele serverului cursului și topic-urile MQTT.
 *
 * Secretele (cheia API, contul RabbitMQ) NU sunt aici: se citesc din
 * secrets.properties, ignorat de git (vezi Secrets).
 */
public class Config {

    /** identic cu STUDENT din config.h al plăcii */
    public static final String STUDENT = "cristea-liviu";

    // API REST: liniile și stațiile (cheia în headerul X-API-Key)
    public static final String API_URL = "https://control.aut.utcluj.ro/publictransport/api";

    // RabbitMQ: pozițiile vehiculelor, exchange fanout nedurabil
    public static final String RABBIT_HOST = "control.aut.utcluj.ro";
    public static final int RABBIT_PORT = 11156;
    public static final String VEHICLES_EXCHANGE = "cluj.vehicles";

    // MQTT: brokerul cursului, fără autentificare
    public static final String MQTT_BROKER = "tcp://control.aut.utcluj.ro:11188";

    private static final String TRACKER = "ssatr/" + STUDENT + "/tracker/";

    /** aplicația -> placă: starea urmăririi (ETA, stații rămase, timpul de mers) */
    public static final String STATUS_TOPIC = TRACKER + "status";

    /** placă -> aplicație: evenimente (BOARDED la apăsarea butonului BOOT) */
    public static final String EVENTS_TOPIC = TRACKER + "events";

    /** modul simulare: pornit cu -Dsim=true, fără API și fără RabbitMQ */
    public static final boolean SIMULATION = Boolean.getBoolean("sim");
}