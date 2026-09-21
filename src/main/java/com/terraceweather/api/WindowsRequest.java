package com.terraceweather.api;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.OptBoolean;
import com.terraceweather.scoring.TerraceRules;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

/** Body of {@code POST /api/v1/terrace/windows}: many service windows of one location, evaluated together. */
public record WindowsRequest(
        @Schema(description = "Latitude of the restaurant (decimal degrees)", example = "40.4168")
        @NotNull @DecimalMin("-90") @DecimalMax("90") Double lat,
        @Schema(description = "Longitude of the restaurant (decimal degrees)", example = "-3.7038")
        @NotNull @DecimalMin("-180") @DecimalMax("180") Double lon,
        @Schema(description = "Optional tolerances: a climate profile and/or individual thresholds")
        @Valid RulesQuery rules,
        @Schema(description = "Service windows to evaluate (1 to 25)")
        @NotNull @Size(min = 1, max = WindowsRequest.MAX_WINDOWS) List<@Valid @NotNull Item> windows) {

    public static final int MAX_WINDOWS = 25;

    /** One window; local time at the restaurant. {@code id} is an optional label echoed in the response. */
    public record Item(
            @Schema(description = "Your label for this window (e.g. the reservation id), echoed back in the "
                    + "result. Defaults to the position in the list.", example = "mesa-12")
            @Size(max = 64) String id,
            @Schema(description = "Start, local time at the restaurant, no 'Z' or UTC offset",
                    example = "2026-09-22T13:30:00", type = "string", format = "date-time")
            @NotNull @JsonFormat(lenient = OptBoolean.FALSE) LocalDateTime start,
            @Schema(description = "End (exclusive), local time at the restaurant, no 'Z' or UTC offset",
                    example = "2026-09-22T15:30:00", type = "string", format = "date-time")
            @NotNull @JsonFormat(lenient = OptBoolean.FALSE) LocalDateTime end) {
    }

    public TerraceRules toRules() {
        return (rules != null ? rules : new RulesQuery(null, null, null, null, null, null)).toRules();
    }
}
