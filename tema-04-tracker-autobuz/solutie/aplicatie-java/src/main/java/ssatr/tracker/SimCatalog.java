package ssatr.tracker;

import java.util.ArrayList;
import java.util.List;

import ssatr.tracker.Model.Direction;
import ssatr.tracker.Model.Line;
import ssatr.tracker.Model.Stop;

/**
 * Modul simulare (-Dsim=true): o linie fictivă "SIM", cu 10 stații la
 * aproximativ 500 m una de alta, pe direcția est-vest prin Cluj.
 * Permite testarea aplicației și a plăcii fără cheie API și fără RabbitMQ.
 */
public class SimCatalog implements LineCatalog {

    public static final int ROUTE_ID = 9999;

    private final Line line;

    public SimCatalog() {
        // 0.0065 grade longitudine ≈ 500 m la latitudinea Clujului
        List<Stop> dus = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            dus.add(new Stop(9000 + i, "Statia simulata " + (i + 1), 46.7700, 23.5500 + 0.0065 * i));
        }
        List<Stop> intors = new ArrayList<>();
        for (int i = 9; i >= 0; i--) {
            intors.add(new Stop(9100 + i, "Statia simulata " + (i + 1) + " (intors)", 46.7702, 23.5500 + 0.0065 * i));
        }
        line = new Line(ROUTE_ID, "SIM", "Linie simulata", "autobuz", List.of(
                new Direction("SIM_0", 0, "Statia simulata 10", dus),
                new Direction("SIM_1", 1, "Statia simulata 1", intors)));
    }

    @Override
    public List<Line> lines() {
        return List.of(line);
    }

    @Override
    public Line line(int routeId) {
        return line;
    }
}