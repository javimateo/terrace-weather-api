package com.terraceweather.api;

import com.terraceweather.scoring.TerraceProfile;
import com.terraceweather.scoring.TerraceRules;
import com.terraceweather.scoring.TerraceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Terrace", description = "Terrace viability from the weather forecast. All date-times are local "
        + "time at the restaurant (no 'Z', no UTC offset).")
@ApiResponse(responseCode = "400", description = "Invalid request; 'detail' names the offending field",
        content = @Content(mediaType = "application/problem+json"))
@ApiResponse(responseCode = "429", description = "Rate limit (60 requests/minute per IP) exceeded; see the "
        + "Retry-After header",
        content = @Content(mediaType = "application/problem+json"))
@ApiResponse(responseCode = "502", description = "The weather provider is temporarily unavailable; retry with backoff",
        content = @Content(mediaType = "application/problem+json"))
@RestController
@RequestMapping("/api/v1/terrace")
public class TerraceController {

    /** Local date-time without zone or offset: a client sending "13:00Z" must get an error, not wrong hours. */
    static final String LOCAL_DATE_TIME = "yyyy-MM-dd'T'HH:mm[:ss]";

    private final TerraceService service;

    public TerraceController(TerraceService service) {
        this.service = service;
    }

    @Operation(summary = "Climate profiles and the thresholds they apply",
            description = "Presets to use as the `profile` parameter. Explicit thresholds override them.")
    @GetMapping("/profiles")
    public Map<TerraceProfile, TerraceRules> profiles() {
        Map<TerraceProfile, TerraceRules> all = new EnumMap<>(TerraceProfile.class);
        for (TerraceProfile p : TerraceProfile.values()) {
            all.put(p, p.rules());
        }
        return all;
    }

    @Operation(summary = "Hour-by-hour terrace score for the next hours (max 72)",
            description = "Starts at the current hour at the location, so the first entry is 'now'. "
                    + "Each hour has a 0-100 score, a verdict (OPEN, CAUTION, CLOSED) and the reasons that cost points.")
    @GetMapping("/forecast")
    public TerraceService.Forecast forecast(
            @Parameter(description = "Latitude of the restaurant (decimal degrees)", example = "40.4168")
            @RequestParam @DecimalMin("-90") @DecimalMax("90") double lat,
            @Parameter(description = "Longitude of the restaurant (decimal degrees)", example = "-3.7038")
            @RequestParam @DecimalMin("-180") @DecimalMax("180") double lon,
            @Parameter(description = "How many hours to return (1-72)", example = "12")
            @RequestParam(defaultValue = "24") @Min(1) @Max(72) int hours,
            @Valid @ParameterObject RulesQuery rules) {
        return service.forecast(lat, lon, hours, rules.toRules());
    }

    @Operation(summary = "Single verdict and suggested terrace capacity for a service window",
            description = "Uses the worst hour of the window, e.g. lunch 13:00-16:00: one storm closes the "
                    + "terrace. `start` is inclusive and `end` exclusive. Times are local to the restaurant.")
    @GetMapping("/window")
    public TerraceService.Window window(
            @Parameter(description = "Latitude of the restaurant (decimal degrees)", example = "40.4168")
            @RequestParam @DecimalMin("-90") @DecimalMax("90") double lat,
            @Parameter(description = "Longitude of the restaurant (decimal degrees)", example = "-3.7038")
            @RequestParam @DecimalMin("-180") @DecimalMax("180") double lon,
            @Parameter(description = "Start, local time at the restaurant (no 'Z' or offset)",
                    example = "2026-09-22T13:00:00")
            @RequestParam @DateTimeFormat(pattern = LOCAL_DATE_TIME) LocalDateTime start,
            @Parameter(description = "End (exclusive), local time at the restaurant (no 'Z' or offset)",
                    example = "2026-09-22T16:00:00")
            @RequestParam @DateTimeFormat(pattern = LOCAL_DATE_TIME) LocalDateTime end,
            @Valid @ParameterObject RulesQuery rules) {
        return service.window(lat, lon, start, end, rules.toRules());
    }

    @Operation(summary = "Verdicts for up to 25 service windows of the same location in one call",
            description = "Check every reservation of a day at once. Counts as a single request for the rate limit "
                    + "and a single weather lookup. Each window is evaluated independently: one with bad times "
                    + "returns an 'error' instead of failing the whole batch. Use 'id' to match results to your "
                    + "reservations (defaults to the position in the list).")
    @PostMapping("/windows")
    public TerraceService.WindowsResult windows(@Valid @RequestBody WindowsRequest request) {
        List<TerraceService.WindowSpec> specs = request.windows().stream()
                .map(i -> new TerraceService.WindowSpec(i.id(), i.start(), i.end()))
                .toList();
        return service.windows(request.lat(), request.lon(), specs, request.toRules());
    }

    @Operation(summary = "Best window of a given length on a day, plus non-overlapping alternatives",
            description = "Searches every start hour between fromHour and toHour (the restaurant's service hours) "
                    + "and ranks windows by their worst hour, then average, then earliest start. Alternatives never "
                    + "overlap the best window and are never CLOSED. Useful to propose a time to a customer.")
    @GetMapping("/best-window")
    public TerraceService.BestWindow bestWindow(
            @Parameter(description = "Latitude of the restaurant (decimal degrees)", example = "13.7563")
            @RequestParam @DecimalMin("-90") @DecimalMax("90") double lat,
            @Parameter(description = "Longitude of the restaurant (decimal degrees)", example = "100.5018")
            @RequestParam @DecimalMin("-180") @DecimalMax("180") double lon,
            @Parameter(description = "Day to search, local date (the forecast covers the next 3 days)",
                    example = "2026-09-22")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Parameter(description = "Length of the window in hours (1-12), e.g. how long a customer stays",
                    example = "2")
            @RequestParam(defaultValue = "2") @Min(1) @Max(12) int durationHours,
            @Parameter(description = "First hour the restaurant serves (0-23)", example = "12")
            @RequestParam(defaultValue = "12") @Min(0) @Max(23) int fromHour,
            @Parameter(description = "Hour service ends (1-24); windows must finish by then", example = "23")
            @RequestParam(defaultValue = "23") @Min(1) @Max(24) int toHour,
            @Valid @ParameterObject RulesQuery rules) {
        return service.bestWindow(lat, lon, date, durationHours, fromHour, toHour, rules.toRules());
    }
}
