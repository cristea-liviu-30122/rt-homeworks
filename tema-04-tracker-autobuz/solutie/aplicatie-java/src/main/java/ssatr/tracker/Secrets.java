package ssatr.tracker;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Secretele aplicației, citite din secrets.properties (în folderul aplicatie-java,
 * ignorat de git). O variabilă de mediu cu același nume are prioritate.
 */
public class Secrets {

    private static final Path FILE = Path.of("secrets.properties");
    private static final Properties props = new Properties();

    static {
        if (Files.exists(FILE)) {
            try (Reader r = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
                props.load(r);
            } catch (IOException e) {
                System.out.println("Nu pot citi " + FILE.toAbsolutePath() + ": " + e.getMessage());
            }
        } else {
            System.out.println("Lipsește " + FILE.toAbsolutePath() + " (copiați secrets.properties.example)");
        }
    }

    /** Valoarea cheii, sau "" dacă nu e setată nicăieri. */
    public static String get(String key) {
        String env = System.getenv(key);
        if (env != null && !env.isBlank()) return env.trim();
        return props.getProperty(key, "").trim();
    }

    /** Ca get(), dar oprește programul cu un mesaj clar dacă valoarea lipsește. */
    public static String require(String key) {
        String v = get(key);
        if (v.isEmpty()) {
            throw new IllegalStateException(key + " nu este setat în secrets.properties"
                    + " (sau porniți în modul simulare: -Dsim=true)");
        }
        return v;
    }
}