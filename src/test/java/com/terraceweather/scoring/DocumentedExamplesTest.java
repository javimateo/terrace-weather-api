package com.terraceweather.scoring;

import static org.assertj.core.api.Assertions.assertThat;

import com.terraceweather.weather.HourlyForecast;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/**
 * Every scenario in docs/scoring.md is asserted here, so the documentation cannot drift from the code.
 * If you change the scoring, this test fails until the documentation table is updated too.
 */
class DocumentedExamplesTest {

    private final TerraceScorer scorer = new TerraceScorer();

    private HourScore score(TerraceRules rules, double feelsLikeC, Integer rainPct, double mm, double gustsKmh) {
        HourlyForecast f = new HourlyForecast(LocalDateTime.of(2026, 9, 22, 14, 0),
                feelsLikeC, feelsLikeC, rainPct, mm, gustsKmh / 2, gustsKmh);
        return scorer.score(f, rules);
    }

    private void expect(HourScore s, int score, TerraceVerdict verdict) {
        assertThat(s.score()).isEqualTo(score);
        assertThat(s.verdict()).isEqualTo(verdict);
    }

    private final TerraceRules temperate = TerraceProfile.TEMPERATE.rules();

    @Test
    void scenario1PerfectDay() {
        expect(score(temperate, 24, 5, 0, 15), 100, TerraceVerdict.OPEN);
    }

    @Test
    void scenario2HotAfternoon() {
        expect(score(temperate, 37, 0, 0, 20), 88, TerraceVerdict.OPEN);
    }

    @Test
    void scenario3CloudyWithRainLikely() {
        expect(score(temperate, 22, 55, 0, 15), 60, TerraceVerdict.CAUTION);
    }

    @Test
    void scenario4LightDrizzle() {
        expect(score(temperate, 22, 30, 0.3, 15), 94, TerraceVerdict.OPEN);
    }

    @Test
    void scenario5StrongGustsButNotExtreme() {
        expect(score(temperate, 24, 0, 0, 50), 70, TerraceVerdict.OPEN);
    }

    @Test
    void scenario6ExtremeGustsForceClosure() {
        expect(score(temperate, 24, 0, 0, 55), 20, TerraceVerdict.CLOSED);
    }

    @Test
    void scenario7ColdEvening() {
        expect(score(temperate, 5, 0, 0, 10), 64, TerraceVerdict.CAUTION);
    }

    @Test
    void scenario8MeasurableRainForcesClosure() {
        expect(score(temperate, 22, 70, 1.0, 15), 20, TerraceVerdict.CLOSED);
    }

    @Test
    void scenario9BangkokAfternoonTemperateVersusTropical() {
        expect(score(temperate, 33, 60, 0.4, 15), 47, TerraceVerdict.CAUTION);
        expect(score(TerraceProfile.TROPICAL.rules(), 33, 60, 0.4, 15), 92, TerraceVerdict.OPEN);
    }

    @Test
    void scenario10UnknownRainProbability() {
        expect(score(temperate, 24, null, 0, 15), 100, TerraceVerdict.OPEN);
    }
}
