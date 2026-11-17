package ssatr.tracker;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import ssatr.tracker.Model.Stop;

/**
 * Modul simulare: un autobuz care parcurge sensul ales cu SPEED_MPS, de la prima
 * stație la ultima, apoi o ia de la capăt (ca un autobuz nou, cu alt id).
 * Raportează poziția la fiecare PERIOD_S secunde, ca un vehicul real.
 */
public class SimVehicleSource implements VehicleSource {

    // aceeași viteză pe care o presupune Tracker, ca ETA-ul simulat să fie exact
    private static final double SPEED_MPS = Tracker.AVG_SPEED_MPS;
    private static final int PERIOD_S = 5;

    private final Selection sel;
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "sim-vehicle");
        t.setDaemon(true);
        return t;
    });

    public SimVehicleSource(Selection sel) {
        this.sel = sel;
    }

    @Override
    public void start(Consumer<Vehicle> sink) {
        List<Stop> stops = sel.direction().stops();
        RouteGeometry geo = new RouteGeometry(stops);
        long[] startMs = {System.currentTimeMillis()};
        int[] busNo = {1};

        timer.scheduleAtFixedRate(() -> {
            double along = (System.currentTimeMillis() - startMs[0]) / 1000.0 * SPEED_MPS;
            if (along > geo.length()) {  // a ajuns la capăt: pornește autobuzul următor
                startMs[0] = System.currentTimeMillis();
                busNo[0]++;
                along = 0;
            }
            double[] p = geo.pointAt(along);
            sink.accept(new Vehicle(busNo[0], "SIM" + busNo[0], sel.line().routeId(),
                    sel.direction().tripId(), p[0], p[1], SPEED_MPS * 3.6, Instant.now()));
        }, 0, PERIOD_S, TimeUnit.SECONDS);
        System.out.println("[sim] autobuz simulat pe " + sel.direction().tripId() + ", poziție la " + PERIOD_S + " s");
    }

    @Override
    public void close() {
        timer.shutdownNow();
    }
}