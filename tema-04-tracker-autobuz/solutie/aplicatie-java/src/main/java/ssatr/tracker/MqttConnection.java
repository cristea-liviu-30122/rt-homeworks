package ssatr.tracker;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

/**
 * Conexiunea la brokerul MQTT (aceeași clasă ca în temele 01-03).
 *
 * Mesajele primite nu sunt prelucrate pe thread-ul bibliotecii Paho, ci sunt
 * puse într-o coadă (BlockingQueue), din care le preia aplicația.
 */
public class MqttConnection {

    private final MqttClient client;

    public MqttConnection(String clientName) throws MqttException {
        // ID-ul clientului trebuie să fie unic pe broker
        String clientId = clientName + "-" + System.currentTimeMillis();
        client = new MqttClient(Config.MQTT_BROKER, clientId, new MemoryPersistence());
    }

    public void connect() throws MqttException {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(true);
        options.setAutomaticReconnect(true);
        client.connect(options);
        System.out.println("[mqtt] conectat la " + Config.MQTT_BROKER);
    }

    /** Publică un mesaj text (QoS 1, fără retain). */
    public void publish(String topic, String text) {
        try {
            MqttMessage message = new MqttMessage(text.getBytes(StandardCharsets.UTF_8));
            message.setQos(1);
            client.publish(topic, message);
        } catch (MqttException e) {
            System.out.println("[mqtt] eroare la publicare pe " + topic + ": " + e.getMessage());
        }
    }

    /** Se abonează la un topic; textul fiecărui mesaj primit este pus în coada inbox. */
    public void subscribe(String topicFilter, BlockingQueue<String> inbox) throws MqttException {
        client.subscribe(topicFilter, 1,
                (topic, message) -> inbox.put(new String(message.getPayload(), StandardCharsets.UTF_8)));
        System.out.println("[mqtt] abonat la " + topicFilter);
    }

    public void disconnect() {
        try {
            client.disconnect();
            client.close();
        } catch (MqttException e) {
            // la oprire nu mai contează
        }
    }
}