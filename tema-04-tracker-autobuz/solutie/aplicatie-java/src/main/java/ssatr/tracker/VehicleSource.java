package ssatr.tracker;

import java.util.function.Consumer;

/** De unde vin pozițiile vehiculelor: RabbitMQ (RabbitVehicleSource) sau simularea (SimVehicleSource). */
public interface VehicleSource extends AutoCloseable {

    /** Pornește primirea pozițiilor; fiecare poziție nouă este dată lui sink, pe thread-ul sursei. */
    void start(Consumer<Vehicle> sink) throws Exception;

    @Override
    void close();
}