package ssatr.tracker;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Urmărirea propriu-zisă: ține ultima poziție a fiecărui vehicul de pe sensul
 * ales și calculează, la cerere, cel mai apropiat autobuz care încă NU a trecut
 * de stația utilizatorului.
 *
 * update() este apelat pe thread-ul sursei (RabbitMQ), estimate() pe thread-ul
 * aplicației, deci harta vehiculelor este un ConcurrentHashMap.
 */
public class Tracker {

    /** viteza medie a unui autobuz în oraș, cu tot cu opririle în stații: ~18 km/h */
    static final double AVG_SPEED_MPS = 5.0;
    /** o poziție mai veche de atât nu mai contează (vehicul retras sau fără semnal) */
    static final long STALE_S = 180;
    /** un vehicul mai departe de atât de traseu nu e pe sensul ăsta (GPS greșit, depou) */
    static final double MAX_OFF_ROUTE_M = 300;
    /** sub această distanță, autobuzul e "în stație" */
    static final double AT_STOP_M = 100;

    /**
     * Rezultatul unei estimări.
     *
     * @param label     vehiculul urmărit
     * @param stopsAway câte stații mai are până la stația utilizatorului (0 = e în stație)
     * @param distanceM distanța pe traseu, în metri
     * @param etaS      timpul estimat până ajunge în stație, în secunde
     * @param ageS      vechimea poziției pe care se bazează estimarea
     */
    public record Estimate(String label, int stopsAway, int distanceM, int etaS, long ageS) { }

    private final Selection sel;
    private final RouteGeometry geo;
    private final Map<Integer, Vehicle> vehicles = new ConcurrentHashMap<>();

    public Tracker(Selection sel) {
        this.sel = sel;
        this.geo = new RouteGeometry(sel.direction().stops());
    }

    /** O poziție nouă, de la sursă. Se păstrează doar vehiculele de pe sensul ales. */
    public void update(Vehicle v) {
        if (v.routeId() != sel.line().routeId()) return;
        // fără trip_id nu știm sensul; ignorăm vehiculul (rar, dar apare în datele reale)
        if (v.tripId() == null || !v.tripId().equals(sel.direction().tripId())) return;
        vehicles.put(v.id(), v);  // ultima poziție a fiecărui vehicul, cheia = id
    }

    /** Cel mai apropiat autobuz care vine spre stație, sau null dacă nu e niciunul. */
    public Estimate estimate() {
        vehicles.values().removeIf(v -> v.ageSeconds() > STALE_S);

        double stopAlong = geo.alongStop(sel.stopIndex());
        Estimate best = null;
        for (Vehicle v : vehicles.values()) {
            double[] p = geo.project(v.lat(), v.lon());
            double along = p[0], offRoute = p[1];
            int segment = (int) p[2];
            if (offRoute > MAX_OFF_ROUTE_M) continue;

            double remaining = stopAlong - along;
            if (remaining < -AT_STOP_M) continue;  // a trecut deja de stație

            // poziția e veche de ageS secunde: între timp autobuzul a mai înaintat
            int eta = (int) Math.max(0, remaining / AVG_SPEED_MPS - v.ageSeconds());
            int stops = remaining <= AT_STOP_M ? 0 : Math.max(1, sel.stopIndex() - segment);
            int dist = (int) Math.max(0, remaining);

            if (best == null || dist < best.distanceM()) {
                best = new Estimate(v.label(), stops, dist, eta, v.ageSeconds());
            }
        }
        return best;
    }

    /** câte vehicule de pe sensul ales sunt cunoscute acum */
    public int knownVehicles() {
        return vehicles.size();
    }
}