package com.terraceweather.scoring;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.terraceweather.weather.WeatherForecast;
import com.terraceweather.weather.WeatherProvider;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class TerraceService {

    private static final int MAX_ALTERNATIVES = 3;

    public record Forecast(ZoneId timezone, List<HourScore> hours) {
    }

    public record Window(
            ZoneId timezone,
            LocalDateTime start,
            LocalDateTime end,
            TerraceVerdict verdict,
            int minScore,
            int averageScore,
            int recommendedCapacityPercent,
            List<String> reasons,
            List<HourScore> hours) {
    }

    /** One requested service window; {@code id} is an optional caller label echoed back in the result. */
    public record WindowSpec(String id, LocalDateTime start, LocalDateTime end) {
    }

    /** Outcome of one window in a batch: either {@code window} or {@code error} is set, never both. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record WindowOutcome(String id, Window window, String error) {
    }

    public record WindowsResult(ZoneId timezone, List<WindowOutcome> results) {
    }

    public record BestWindow(
            ZoneId timezone, LocalDate date, int durationHours, Window best, List<Window> alternatives) {
    }

    private final WeatherProvider weather;
    private final TerraceScorer scorer;
    private final Clock clock;

    public TerraceService(WeatherProvider weather, TerraceScorer scorer, Clock clock) {
        this.weather = weather;
        this.scorer = scorer;
        this.clock = clock;
    }

    /** Scored hours starting from the current hour at the location, up to {@code hours} entries. */
    public Forecast forecast(double lat, double lon, int hours, TerraceRules rules) {
        WeatherForecast wf = weather.fetch(lat, lon);
        LocalDateTime now = LocalDateTime.now(clock.withZone(wf.timezone())).truncatedTo(ChronoUnit.HOURS);
        List<HourScore> scored = wf.hours().stream()
                .filter(h -> !h.time().isBefore(now))
                .limit(hours)
                .map(h -> scorer.score(h, rules))
                .toList();
        return new Forecast(wf.timezone(), scored);
    }

    /** Aggregates a service window (e.g. 13:00-16:00) using its worst hour, so one storm closes the terrace. */
    public Window window(double lat, double lon, LocalDateTime start, LocalDateTime end, TerraceRules rules) {
        checkOrder(start, end);
        WeatherForecast wf = weather.fetch(lat, lon);
        return evaluate(wf, start, end, rules).orElseThrow(TerraceService::noForecastData);
    }

    /** Evaluates many windows of the same location with a single weather fetch; each fails independently. */
    public WindowsResult windows(double lat, double lon, List<WindowSpec> specs, TerraceRules rules) {
        WeatherForecast wf = weather.fetch(lat, lon);
        List<WindowOutcome> outcomes = new ArrayList<>(specs.size());
        for (int i = 0; i < specs.size(); i++) {
            WindowSpec spec = specs.get(i);
            String id = spec.id() != null ? spec.id() : String.valueOf(i);
            try {
                checkOrder(spec.start(), spec.end());
                Window w = evaluate(wf, spec.start(), spec.end(), rules).orElseThrow(TerraceService::noForecastData);
                outcomes.add(new WindowOutcome(id, w, null));
            } catch (InvalidRequestException e) {
                outcomes.add(new WindowOutcome(id, null, e.getMessage()));
            }
        }
        return new WindowsResult(wf.timezone(), outcomes);
    }

    /**
     * Best {@code durationHours}-long window inside the service hours of {@code date}, plus up to three
     * non-overlapping alternatives. Ranked by worst hour, then average, then earliest start.
     */
    public BestWindow bestWindow(double lat, double lon, LocalDate date, int durationHours,
                                 int fromHour, int toHour, TerraceRules rules) {
        if (toHour - fromHour < durationHours) {
            throw new InvalidRequestException("Service hours (fromHour to toHour) must span at least durationHours");
        }
        WeatherForecast wf = weather.fetch(lat, lon);
        List<Window> candidates = new ArrayList<>();
        for (int h = fromHour; h + durationHours <= toHour; h++) {
            LocalDateTime start = date.atTime(h, 0);
            evaluate(wf, start, start.plusHours(durationHours), rules)
                    .filter(w -> w.hours().size() == durationHours) // skip windows with forecast gaps
                    .ifPresent(candidates::add);
        }
        if (candidates.isEmpty()) {
            throw new InvalidRequestException(
                    "No complete forecast data for that date (forecast covers the next 3 days)");
        }
        candidates.sort(Comparator.comparingInt(Window::minScore).reversed()
                .thenComparing(Comparator.comparingInt(Window::averageScore).reversed())
                .thenComparing(Window::start));

        Window best = candidates.get(0);
        List<Window> chosen = new ArrayList<>(List.of(best));
        List<Window> alternatives = new ArrayList<>();
        for (Window c : candidates.subList(1, candidates.size())) {
            if (alternatives.size() == MAX_ALTERNATIVES) {
                break;
            }
            // Never suggest an unusable slot as an alternative to the best one.
            if (c.verdict() != TerraceVerdict.CLOSED && chosen.stream().noneMatch(o -> overlaps(o, c))) {
                chosen.add(c);
                alternatives.add(c);
            }
        }
        return new BestWindow(wf.timezone(), date, durationHours, best, alternatives);
    }

    private Optional<Window> evaluate(WeatherForecast wf, LocalDateTime start, LocalDateTime end,
                                      TerraceRules rules) {
        LocalDateTime from = start.truncatedTo(ChronoUnit.HOURS);
        List<HourScore> scored = wf.hours().stream()
                .filter(h -> !h.time().isBefore(from) && h.time().isBefore(end))
                .map(h -> scorer.score(h, rules))
                .toList();
        if (scored.isEmpty()) {
            return Optional.empty();
        }
        TerraceVerdict worst = scored.stream().map(HourScore::verdict)
                .max(Comparator.naturalOrder()).orElseThrow();
        int min = scored.stream().mapToInt(HourScore::score).min().orElseThrow();
        int avg = (int) Math.round(scored.stream().mapToInt(HourScore::score).average().orElseThrow());
        List<String> reasons = scored.stream().flatMap(s -> s.reasons().stream()).distinct().toList();
        return Optional.of(new Window(wf.timezone(), start, end, worst, min, avg,
                worst.capacityPercent(), reasons, scored));
    }

    private static boolean overlaps(Window a, Window b) {
        return a.start().isBefore(b.end()) && b.start().isBefore(a.end());
    }

    private static void checkOrder(LocalDateTime start, LocalDateTime end) {
        if (!end.isAfter(start)) {
            throw new InvalidRequestException("'end' must be after 'start'");
        }
    }

    private static InvalidRequestException noForecastData() {
        return new InvalidRequestException(
                "No forecast data for the requested window (forecast covers the next 3 days)");
    }
}
