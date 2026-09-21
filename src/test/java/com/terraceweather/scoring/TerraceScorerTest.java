package com.terraceweather.scoring;

import static org.assertj.core.api.Assertions.assertThat;

import com.terraceweather.weather.HourlyForecast;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class TerraceScorerTest {

    private final TerraceScorer scorer = new TerraceScorer();
    private final TerraceRules rules = TerraceRules.defaults();

    private static HourlyForecast hour(double apparent, int rainProb, double mm, double gusts) {
        return new HourlyForecast(LocalDateTime.of(2026, 9, 18, 13, 0), apparent, apparent, rainProb, mm, gusts / 2, gusts);
    }

    @Test
    void sunnyMildDayIsOpen() {
        HourScore s = scorer.score(hour(24, 5, 0, 10), rules);
        assertThat(s.verdict()).isEqualTo(TerraceVerdict.OPEN);
        assertThat(s.score()).isEqualTo(100);
        assertThat(s.reasons()).isEmpty();
    }

    @Test
    void measurablePrecipitationClosesTerraceRegardlessOfScore() {
        HourScore s = scorer.score(hour(24, 30, 2.0, 5), rules);
        assertThat(s.verdict()).isEqualTo(TerraceVerdict.CLOSED);
        assertThat(s.score()).isLessThanOrEqualTo(20);
        assertThat(s.reasons()).anyMatch(r -> r.contains("precipitation"));
    }

    @Test
    void moderateRainProbabilityIsCaution() {
        HourScore s = scorer.score(hour(24, 55, 0, 10), rules);
        assertThat(s.verdict()).isEqualTo(TerraceVerdict.CAUTION);
        assertThat(s.reasons()).anyMatch(r -> r.contains("Rain probability 55%"));
    }

    @Test
    void extremeGustsCloseTerrace() {
        HourScore s = scorer.score(hour(24, 0, 0, 60), rules);
        assertThat(s.verdict()).isEqualTo(TerraceVerdict.CLOSED);
    }

    @Test
    void coldFeelsLikeLowersScore() {
        HourScore s = scorer.score(hour(6, 0, 0, 10), rules);
        assertThat(s.score()).isLessThan(TerraceScorer.OPEN_THRESHOLD);
        assertThat(s.reasons()).anyMatch(r -> r.contains("too cold"));
    }

    @Test
    void unknownRainProbabilityIsNotTreatedAsRain() {
        HourlyForecast f = new HourlyForecast(LocalDateTime.of(2026, 9, 18, 13, 0), 24, 24, null, 0, 5, 10);

        HourScore s = scorer.score(f, rules);

        assertThat(s.verdict()).isEqualTo(TerraceVerdict.OPEN);
        assertThat(s.reasons()).isEmpty();
    }

    @Test
    void reasonsDoNotDependOnTheServerLocale() {
        java.util.Locale previous = java.util.Locale.getDefault();
        java.util.Locale.setDefault(java.util.Locale.forLanguageTag("es-ES"));
        try {
            HourScore s = scorer.score(hour(24, 30, 2.7, 5), rules);
            assertThat(s.reasons()).contains("Expected precipitation 2.7 mm");
        } finally {
            java.util.Locale.setDefault(previous);
        }
    }

    @Test
    void customRulesAreRespected() {
        TerraceRules tolerant = new TerraceRules(80, 0.5, 35, 14, 34);
        assertThat(scorer.score(hour(24, 55, 0, 10), tolerant).verdict()).isEqualTo(TerraceVerdict.OPEN);
    }
}
