package ssatr.tracker;

import java.util.List;

import ssatr.tracker.Model.Stop;

/**
 * Traseul unui sens, aproximat prin segmente drepte între stații consecutive.
 *
 * Fiecare punct al traseului are o "distanță de la capăt" (along, în metri).
 * Un vehicul se proiectează pe cel mai apropiat segment, iar distanța până la
 * stația utilizatorului este diferența dintre cele două valori along.
 *
 * Pe distanțe de câțiva km, Pământul se poate considera plan: lat/lon se
 * transformă în metri cu o proiecție echirectangulară în jurul primei stații.
 */
public class RouteGeometry {

    private static final double M_PER_DEG_LAT = 111_320.0;

    private final double lat0, mPerDegLon;
    private final double[] x, y;   // stațiile, în metri
    private final double[] cum;    // cum[i] = distanța de la prima stație până la stația i

    public RouteGeometry(List<Stop> stops) {
        int n = stops.size();
        lat0 = stops.get(0).lat();
        mPerDegLon = M_PER_DEG_LAT * Math.cos(Math.toRadians(lat0));
        x = new double[n];
        y = new double[n];
        cum = new double[n];
        for (int i = 0; i < n; i++) {
            x[i] = stops.get(i).lon() * mPerDegLon;
            y[i] = stops.get(i).lat() * M_PER_DEG_LAT;
            if (i > 0) cum[i] = cum[i - 1] + Math.hypot(x[i] - x[i - 1], y[i] - y[i - 1]);
        }
    }

    public double length() {
        return cum[cum.length - 1];
    }

    /** distanța de la capăt până la stația i */
    public double alongStop(int i) {
        return cum[i];
    }

    /**
     * Proiecția unui punct pe traseu.
     *
     * @return {along, distanța față de traseu, indexul segmentului (stația de dinainte)}
     */
    public double[] project(double lat, double lon) {
        double px = lon * mPerDegLon, py = lat * M_PER_DEG_LAT;
        double best = Double.MAX_VALUE, bestAlong = 0;
        int bestSeg = 0;
        for (int i = 0; i + 1 < x.length; i++) {
            double dx = x[i + 1] - x[i], dy = y[i + 1] - y[i];
            double len2 = dx * dx + dy * dy;
            // t = poziția proiecției pe segment, între 0 (stația i) și 1 (stația i+1)
            double t = len2 == 0 ? 0 : ((px - x[i]) * dx + (py - y[i]) * dy) / len2;
            t = Math.max(0, Math.min(1, t));
            double d = Math.hypot(px - (x[i] + t * dx), py - (y[i] + t * dy));
            if (d < best) {
                best = d;
                bestAlong = cum[i] + t * Math.sqrt(len2);
                bestSeg = i;
            }
        }
        return new double[] {bestAlong, best, bestSeg};
    }

    /** punctul {lat, lon} aflat la distanța along de la capăt (folosit de simulare) */
    public double[] pointAt(double along) {
        int i = 0;
        while (i + 2 < cum.length && cum[i + 1] < along) i++;
        double seg = cum[i + 1] - cum[i];
        double t = seg == 0 ? 0 : Math.max(0, Math.min(1, (along - cum[i]) / seg));
        double px = x[i] + t * (x[i + 1] - x[i]), py = y[i] + t * (y[i + 1] - y[i]);
        return new double[] {py / M_PER_DEG_LAT, px / mPerDegLon};
    }
}