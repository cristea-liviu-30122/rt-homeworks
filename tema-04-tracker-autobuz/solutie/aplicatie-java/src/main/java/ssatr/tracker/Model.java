package ssatr.tracker;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Datele statice ale liniilor, așa cum vin din API-ul REST.
 * Câmpurile pe care nu le folosim (de exemplu shape) sunt ignorate.
 */
public final class Model {

    private Model() { }

    /** o stație: id, nume, poziție GPS */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Stop(int id, String name, double lat, double lon) { }

    /**
     * Un sens al liniei. În /api/lines, stops lipsește (null);
     * în /api/line?route_id=N, stops sunt în ordinea parcurgerii.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Direction(
            @JsonProperty("trip_id") String tripId,
            @JsonProperty("direction_id") int directionId,
            String headsign,
            List<Stop> stops) { }

    /** o linie: route_id e identificatorul unic, line e numărul afișat (nu e unic) */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Line(
            @JsonProperty("route_id") int routeId,
            String line,
            String name,
            String type,
            List<Direction> directions) { }
}