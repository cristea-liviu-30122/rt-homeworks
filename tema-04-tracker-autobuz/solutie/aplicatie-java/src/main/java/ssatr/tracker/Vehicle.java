package ssatr.tracker;

import java.time.Instant;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * O poziție a unui vehicul, dintr-un mesaj RabbitMQ (sau din simulare).
 * Datele reale au câmpuri lipsă, deci totul se citește cu valori implicite.
 *
 * @param id        identificatorul stabil al vehiculului
 * @param label     numărul de parc, scris pe vehicul
 * @param routeId   linia (route_id); -1 dacă lipsește
 * @param tripId    sensul (trip_id); null dacă lipsește
 * @param timestamp momentul raportării poziției, în UTC
 */
public record Vehicle(int id, String label, int routeId, String tripId,
                      double lat, double lon, double speed, Instant timestamp) {

    /** Mesajul JSON -> Vehicle; null dacă lipsește ceva esențial (id sau poziția). */
    public static Vehicle fromJson(JsonNode n) {
        if (!n.hasNonNull("id") || !n.hasNonNull("lat") || !n.hasNonNull("lon")) return null;
        Instant ts;
        try {
            ts = Instant.parse(n.path("timestamp").asText());
        } catch (Exception e) {
            ts = Instant.now();  // fără timestamp valid: considerăm poziția de acum
        }
        String trip = n.hasNonNull("trip_id") ? n.get("trip_id").asText() : null;
        return new Vehicle(n.get("id").asInt(), n.path("label").asText("?"),
                n.path("route_id").asInt(-1), trip,
                n.get("lat").asDouble(), n.get("lon").asDouble(),
                n.path("speed").asDouble(0), ts);
    }

    /** vechimea poziției, în secunde */
    public long ageSeconds() {
        return Math.max(0, Instant.now().getEpochSecond() - timestamp.getEpochSecond());
    }
}