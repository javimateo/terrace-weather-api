package com.terraceweather.scoring;

/** Per-restaurant tolerance thresholds. */
public record TerraceRules(
        int maxRainProbability,
        double maxPrecipitationMm,
        double maxGustsKmh,
        double minApparentTempC,
        double maxApparentTempC) {

    public static TerraceRules defaults() {
        return TerraceProfile.TEMPERATE.rules();
    }
}
