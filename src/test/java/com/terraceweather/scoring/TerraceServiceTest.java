package com.terraceweather.scoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.terraceweather.weather.HourlyForecast;
import com.terraceweather.weather.WeatherForecast;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class TerraceServiceTest {

    private static final ZoneId BANGKOK = ZoneId.of("Asia/Bangkok");
    private static final LocalDateTime DAY = LocalDateTime.of(2026, 9, 18, 0, 0);

    // 2026-09-18T06:30Z is 13:30 in Bangkok
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-18T06:30:00Z"), ZoneOffset.UTC);

    private static HourlyForecast hour(int h, int rainProb) {
        return new HourlyForecast(DAY.plusHours(h), 24, 24, rainProb, 0, 5, 10);
    }

    private TerraceService serviceWith(List<HourlyForecast> hours) {
        return new TerraceService((lat, lon) -> new WeatherForecast(BANGKOK, hours), new TerraceScorer(), clock);
    }

    private final List<HourlyForecast> fullDay = IntStream.range(0, 24).mapToObj(h -> hour(h, 0)).toList();

    @Test
    void forecastStartsAtTheCurrentHourInTheLocationsTimezone() {
        TerraceService.Forecast f = serviceWith(fullDay).forecast(13.7, 100.5, 3, TerraceRules.defaults());

        assertThat(f.hours()).hasSize(3);
        assertThat(f.hours().get(0).conditions().time()).isEqualTo(DAY.plusHours(13));
        assertThat(f.timezone()).isEqualTo(BANGKOK);
    }

    @Test
    void forecastNeverReturnsMoreThanWhatIsAvailable() {
        TerraceService.Forecast f = serviceWith(fullDay).forecast(13.7, 100.5, 72, TerraceRules.defaults());

        assertThat(f.hours()).hasSize(11); // 13:00 .. 23:00
    }

    @Test
    void windowIsHalfOpenAndIncludesTheHourContainingStart() {
        TerraceService.Window w = serviceWith(fullDay).window(13.7, 100.5,
                DAY.plusHours(13).plusMinutes(30), DAY.plusHours(15), TerraceRules.defaults());

        assertThat(w.hours()).extracting(h -> h.conditions().time().getHour()).containsExactly(13, 14);
    }

    @Test
    void windowTakesTheWorstHourAndItsReasons() {
        List<HourlyForecast> hours = List.of(hour(13, 0), hour(14, 55), hour(15, 0));

        TerraceService.Window w = serviceWith(hours).window(13.7, 100.5,
                DAY.plusHours(13), DAY.plusHours(16), TerraceRules.defaults());

        assertThat(w.verdict()).isEqualTo(TerraceVerdict.CAUTION);
        assertThat(w.recommendedCapacityPercent()).isEqualTo(50);
        assertThat(w.minScore()).isLessThan(w.averageScore());
        assertThat(w.reasons()).containsExactly("Rain probability 55%");
    }

    private static final TerraceRules RULES = TerraceRules.defaults();

    /** Clear all day except heavy rain 13:00-15:00. */
    private List<HourlyForecast> rainyAfternoon() {
        return IntStream.range(0, 24).mapToObj(h -> hour(h, h >= 13 && h <= 15 ? 90 : 0)).toList();
    }

    @Test
    void batchEvaluatesAllWindowsWithASingleWeatherFetch() {
        java.util.concurrent.atomic.AtomicInteger fetches = new java.util.concurrent.atomic.AtomicInteger();
        TerraceService service = new TerraceService((lat, lon) -> {
            fetches.incrementAndGet();
            return new WeatherForecast(BANGKOK, rainyAfternoon());
        }, new TerraceScorer(), clock);

        TerraceService.WindowsResult r = service.windows(13.7, 100.5, List.of(
                new TerraceService.WindowSpec("lunch", DAY.plusHours(13), DAY.plusHours(15)),
                new TerraceService.WindowSpec(null, DAY.plusHours(19), DAY.plusHours(21))), RULES);

        assertThat(fetches).hasValue(1);
        assertThat(r.results()).extracting(TerraceService.WindowOutcome::id).containsExactly("lunch", "1");
        assertThat(r.results().get(0).window().verdict()).isEqualTo(TerraceVerdict.CLOSED);
        assertThat(r.results().get(1).window().verdict()).isEqualTo(TerraceVerdict.OPEN);
    }

    @Test
    void batchFailsEachWindowIndependently() {
        TerraceService.WindowsResult r = serviceWith(fullDay).windows(13.7, 100.5, List.of(
                new TerraceService.WindowSpec("ok", DAY.plusHours(14), DAY.plusHours(16)),
                new TerraceService.WindowSpec("backwards", DAY.plusHours(16), DAY.plusHours(14)),
                new TerraceService.WindowSpec("far", DAY.plusDays(20), DAY.plusDays(20).plusHours(2))), RULES);

        assertThat(r.results().get(0).window()).isNotNull();
        assertThat(r.results().get(0).error()).isNull();
        assertThat(r.results().get(1).window()).isNull();
        assertThat(r.results().get(1).error()).contains("'end' must be after 'start'");
        assertThat(r.results().get(2).error()).contains("No forecast data");
    }

    @Test
    void bestWindowPicksTheEarliestPerfectSlotAndNonOverlappingAlternatives() {
        TerraceService.BestWindow b = serviceWith(rainyAfternoon())
                .bestWindow(13.7, 100.5, DAY.toLocalDate(), 2, 12, 22, RULES);

        assertThat(b.best().start().getHour()).isEqualTo(16);
        assertThat(b.best().verdict()).isEqualTo(TerraceVerdict.OPEN);
        assertThat(b.alternatives()).extracting(w -> w.start().getHour()).containsExactly(18, 20);
    }

    @Test
    void bestWindowNeverOffersClosedAlternatives() {
        TerraceService.BestWindow b = serviceWith(rainyAfternoon())
                .bestWindow(13.7, 100.5, DAY.toLocalDate(), 2, 12, 22, RULES);

        assertThat(b.alternatives()).allMatch(w -> w.verdict() != TerraceVerdict.CLOSED);
    }

    @Test
    void bestWindowOnAFullyClosedDayStillAnswersButOffersNoAlternatives() {
        List<HourlyForecast> storm = IntStream.range(0, 24).mapToObj(h -> hour(h, 95)).toList();

        TerraceService.BestWindow b = serviceWith(storm)
                .bestWindow(13.7, 100.5, DAY.toLocalDate(), 2, 12, 22, RULES);

        assertThat(b.best().verdict()).isEqualTo(TerraceVerdict.CLOSED);
        assertThat(b.alternatives()).isEmpty();
    }

    @Test
    void bestWindowRanksByWorstHourNotByAverage() {
        // 12-14 has one closed hour (13); 10-12 is clear but outside the service hours, so 14+ must win
        TerraceService.BestWindow b = serviceWith(rainyAfternoon())
                .bestWindow(13.7, 100.5, DAY.toLocalDate(), 3, 12, 22, RULES);

        assertThat(b.best().minScore()).isEqualTo(100);
        assertThat(b.best().start().getHour()).isGreaterThanOrEqualTo(16);
    }

    @Test
    void bestWindowRejectsServiceHoursShorterThanTheDuration() {
        assertThatThrownBy(() -> serviceWith(fullDay)
                .bestWindow(13.7, 100.5, DAY.toLocalDate(), 6, 12, 15, RULES))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void bestWindowWithoutCompleteForecastForTheDateIsRejected() {
        assertThatThrownBy(() -> serviceWith(fullDay)
                .bestWindow(13.7, 100.5, DAY.toLocalDate().plusDays(9), 2, 12, 22, RULES))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("No complete forecast data");
    }

    @Test
    void windowWithEndNotAfterStartIsRejected() {
        assertThatThrownBy(() -> serviceWith(fullDay).window(13.7, 100.5,
                DAY.plusHours(15), DAY.plusHours(15), TerraceRules.defaults()))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void windowOutsideTheForecastIsRejected() {
        assertThatThrownBy(() -> serviceWith(fullDay).window(13.7, 100.5,
                DAY.plusDays(10), DAY.plusDays(10).plusHours(2), TerraceRules.defaults()))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("No forecast data");
    }
}
