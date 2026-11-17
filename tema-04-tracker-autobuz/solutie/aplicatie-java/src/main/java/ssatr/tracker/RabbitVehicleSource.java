package ssatr.tracker;

import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

/**
 * Pozițiile reale, din exchange-ul fanout cluj.vehicles.
 *
 * Fiecare client își declară propria coadă temporară și o leagă de exchange,
 * deci primește TOATE mesajele. NU se folosește coada comună ctp.cluj.vehicles:
 * acolo mesajele se împart între colegii conectați în același timp.
 */
public class RabbitVehicleSource implements VehicleSource {

    private final ObjectMapper json = new ObjectMapper();
    private Connection connection;
    private Channel channel;

    @Override
    public void start(Consumer<Vehicle> sink) throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(Config.RABBIT_HOST);
        factory.setPort(Config.RABBIT_PORT);
        factory.setUsername(Secrets.require("RABBIT_USER"));
        factory.setPassword(Secrets.require("RABBIT_PASS"));
        factory.setAutomaticRecoveryEnabled(true);  // reconectare automată, cu coadă și binding refăcute

        connection = factory.newConnection("tracker-" + Config.STUDENT);
        channel = connection.createChannel();

        // exact ca pe server: fanout, nedurabil (altfel PRECONDITION_FAILED)
        channel.exchangeDeclare(Config.VEHICLES_EXCHANGE, "fanout");
        // coadă temporară: nume generat de broker, exclusivă, ștearsă la deconectare
        String queue = channel.queueDeclare().getQueue();
        channel.queueBind(queue, Config.VEHICLES_EXCHANGE, "");

        channel.basicConsume(queue, true, (tag, message) -> {
            try {
                Vehicle v = Vehicle.fromJson(json.readTree(new String(message.getBody(), StandardCharsets.UTF_8)));
                if (v != null) sink.accept(v);
            } catch (Exception e) {
                System.out.println("[rabbit] mesaj ignorat: " + e.getMessage());
            }
        }, tag -> System.out.println("[rabbit] consumator anulat"));

        System.out.println("[rabbit] conectat la " + Config.RABBIT_HOST + ":" + Config.RABBIT_PORT
                + ", coada " + queue + " legată de " + Config.VEHICLES_EXCHANGE);
    }

    @Override
    public void close() {
        try {
            if (channel != null && channel.isOpen()) channel.close();
            if (connection != null && connection.isOpen()) connection.close();
        } catch (Exception e) {
            // la oprire nu mai contează
        }
    }
}