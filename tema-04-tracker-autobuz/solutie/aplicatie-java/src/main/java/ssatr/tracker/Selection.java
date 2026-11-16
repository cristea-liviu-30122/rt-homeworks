package ssatr.tracker;

import java.util.List;
import java.util.Scanner;

import ssatr.tracker.Model.Direction;
import ssatr.tracker.Model.Line;
import ssatr.tracker.Model.Stop;

/**
 * Ce urmărește utilizatorul: linia, sensul, stația lui și câte minute merge
 * pe jos până la ea. Se alege în consolă, din datele reale (sau simulate).
 */
public record Selection(Line line, Direction direction, int stopIndex, int walkMinutes) {

    public Stop stop() {
        return direction.stops().get(stopIndex);
    }

    @Override
    public String toString() {
        return "linia " + line.line() + " spre " + direction.headsign()
                + ", stația " + stop().name() + " (" + (stopIndex + 1) + "/" + direction.stops().size() + ")"
                + ", " + walkMinutes + " min de mers";
    }

    /** Dialogul din consolă: linie -> sens -> stație -> minute de mers. */
    public static Selection ask(LineCatalog catalog, Scanner in) throws Exception {
        List<Line> lines = catalog.lines();
        System.out.println("Linii disponibile: " + lines.size());

        // 1. linia, după numărul afișat; numărul nu e unic (două linii "12")
        Line chosen = null;
        while (chosen == null) {
            System.out.print("Numărul liniei (ex. 24B, M26): ");
            String wanted = in.nextLine().trim();
            List<Line> matches = lines.stream().filter(l -> l.line().equalsIgnoreCase(wanted)).toList();
            if (matches.isEmpty()) {
                System.out.println("  nu există linia " + wanted);
            } else if (matches.size() == 1) {
                chosen = matches.get(0);
            } else {
                for (int i = 0; i < matches.size(); i++) {
                    System.out.printf("  %d) %s %s (%s)%n", i + 1, matches.get(i).line(),
                            matches.get(i).name(), matches.get(i).type());
                }
                chosen = matches.get(readInt(in, "Care dintre ele? ", 1, matches.size()) - 1);
            }
        }

        // 2. stațiile vin doar din /api/line?route_id=N
        Line full = catalog.line(chosen.routeId());
        List<Direction> dirs = full.directions();
        System.out.println("Linia " + full.line() + ": " + chosen.name());
        for (int i = 0; i < dirs.size(); i++) {
            System.out.printf("  %d) spre %s (%d stații)%n", i + 1, dirs.get(i).headsign(), dirs.get(i).stops().size());
        }
        Direction dir = dirs.get(readInt(in, "Sensul: ", 1, dirs.size()) - 1);

        // 3. stația, în ordinea parcurgerii
        List<Stop> stops = dir.stops();
        for (int i = 0; i < stops.size(); i++) {
            System.out.printf("  %2d) %s%n", i + 1, stops.get(i).name());
        }
        // prima stație nu are sens: autobuzul pleacă de acolo, nu "se apropie"
        int stop = readInt(in, "Stația ta (2-" + stops.size() + "): ", 2, stops.size()) - 1;

        // 4. timpul de mers pe jos, folosit de placă pentru "când pleci"
        int walk = readInt(in, "Câte minute mergi pe jos până la stație? ", 0, 60);

        return new Selection(full, dir, stop, walk);
    }

    private static int readInt(Scanner in, String prompt, int min, int max) {
        while (true) {
            System.out.print(prompt);
            try {
                int v = Integer.parseInt(in.nextLine().trim());
                if (v >= min && v <= max) return v;
            } catch (NumberFormatException e) {
                // cade mai jos
            }
            System.out.println("  un număr între " + min + " și " + max);
        }
    }
}