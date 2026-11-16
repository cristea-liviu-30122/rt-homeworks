package ssatr.tracker;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ssatr.tracker.Model.Line;

/**
 * Clientul API-ului REST CTP Cluj live. Datele sunt statice (se schimbă rar),
 * deci se cer o singură dată, la pornire.
 */
public class ApiClient implements LineCatalog {

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper json = new ObjectMapper();
    private final String apiKey = Secrets.require("API_KEY");

    private JsonNode get(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(Config.API_URL + path))
                .header("X-API-Key", apiKey)
                .timeout(Duration.ofSeconds(15))
                .GET().build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() != 200) {
            // 401 = cheie greșită, 404 = route_id necunoscut, 502/503 = sursa indisponibilă
            throw new IllegalStateException("API " + path + ": HTTP " + resp.statusCode() + " " + resp.body());
        }
        return json.readTree(resp.body());
    }

    @Override
    public List<Line> lines() throws Exception {
        JsonNode root = get("/lines");
        return List.of(json.treeToValue(root.get("lines"), Line[].class));
    }

    @Override
    public Line line(int routeId) throws Exception {
        return json.treeToValue(get("/line?route_id=" + routeId), Line.class);
    }
}