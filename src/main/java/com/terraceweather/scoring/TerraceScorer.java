package com.terraceweather.scoring;

import com.terraceweather.weather.HourlyForecast;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

/** Turns raw weather into a 0-100 terrace-viability score plus a verdict and human-readable reasons. */
@Service
public class TerraceScorer {

    static final int OPEN_THRESHOLD = 70;
    static final int CAUTION_THRESHOLD = 40;
    private static final int CLOSED_SCORE_CAP = 20;

    // Rain probability above the tolerated maximum costs a flat base plus one point per excess point.
    private static final int RAIN_BASE_PENALTY = 25;
    private static final int RAIN_MAX_PENALTY = 65;
    // Drizzle below the hard-stop precipitation still hurts comfort.
    private static final double PENALTY_PER_MM = 20;
    private static final int WIND_MAX_PENALTY = 40;
    private static final double WIND_PENALTY_PER_KMH = 2;
    /** Gusts this many times over the tolerated maximum close the terrace outright. */
    private static final double WIND_HARD_CLOSE_FACTOR = 1.5;
    private static final int TEMP_MAX_PENALTY = 40;
    private static final double TEMP_PENALTY_PER_DEGREE = 4;

    public HourScore score(HourlyForecast f, TerraceRules rules) {
        List<String> reasons = new ArrayList<>();
        boolean hardClose = false;
        double penalty = 0;

        // Rain: probability is a soft penalty, measurable precipitation is a hard stop.
        // Some weather models do not provide probability; then only measured precipitation counts.
        Integer probability = f.precipitationProbability();
        if (probability != null && probability > rules.maxRainProbability()) {
            int excess = probability - rules.maxRainProbability();
            penalty += Math.min(RAIN_MAX_PENALTY, RAIN_BASE_PENALTY + excess);
            reasons.add("Rain probability %d%%".formatted(probability));
        }
        if (f.precipitationMm() >= rules.maxPrecipitationMm()) {
            hardClose = true;
            reasons.add(format("Expected precipitation %.1f mm", f.precipitationMm()));
        } else if (f.precipitationMm() > 0) {
            penalty += f.precipitationMm() * PENALTY_PER_MM;
        }

        // Wind: gusts matter more than mean speed for parasols and tableware.
        if (f.windGustsKmh() > rules.maxGustsKmh()) {
            penalty += Math.min(WIND_MAX_PENALTY, (f.windGustsKmh() - rules.maxGustsKmh()) * WIND_PENALTY_PER_KMH);
            reasons.add(format("Wind gusts %.0f km/h", f.windGustsKmh()));
            if (f.windGustsKmh() >= rules.maxGustsKmh() * WIND_HARD_CLOSE_FACTOR) {
                hardClose = true;
            }
        }

        // Comfort uses apparent temperature (wind chill / heat index included).
        if (f.apparentTemperatureC() < rules.minApparentTempC()) {
            penalty += Math.min(TEMP_MAX_PENALTY,
                    (rules.minApparentTempC() - f.apparentTemperatureC()) * TEMP_PENALTY_PER_DEGREE);
            reasons.add(format("Feels like %.0f C (too cold)", f.apparentTemperatureC()));
        } else if (f.apparentTemperatureC() > rules.maxApparentTempC()) {
            penalty += Math.min(TEMP_MAX_PENALTY,
                    (f.apparentTemperatureC() - rules.maxApparentTempC()) * TEMP_PENALTY_PER_DEGREE);
            reasons.add(format("Feels like %.0f C (too hot)", f.apparentTemperatureC()));
        }

        int score = (int) Math.max(0, Math.round(100 - penalty));
        if (hardClose) {
            return new HourScore(Math.min(score, CLOSED_SCORE_CAP), TerraceVerdict.CLOSED, reasons, f);
        }
        TerraceVerdict verdict = score >= OPEN_THRESHOLD ? TerraceVerdict.OPEN
                : score >= CAUTION_THRESHOLD ? TerraceVerdict.CAUTION : TerraceVerdict.CLOSED;
        return new HourScore(score, verdict, reasons, f);
    }

    /** Fixed locale: API output must not change with the server's language (no "2,7 mm"). */
    private static String format(String pattern, Object... args) {
        return String.format(Locale.ROOT, pattern, args);
    }
}
