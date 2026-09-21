package com.terraceweather.api;

import com.terraceweather.scoring.InvalidRequestException;
import com.terraceweather.scoring.TerraceProfile;
import com.terraceweather.scoring.TerraceRules;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Optional query-string tolerances: pick a climate {@code profile} as the base
 * (default TEMPERATE) and override any single threshold on top of it.
 */
public record RulesQuery(
        @Schema(description = "Climate preset used as the base for all thresholds. Default TEMPERATE. "
                + "See GET /profiles for the values.", example = "MEDITERRANEAN")
        TerraceProfile profile,
        @Schema(description = "Rain probability (%) above which points are lost. Overrides the profile.",
                example = "40")
        @Min(0) @Max(100) Integer maxRainProbability,
        @Schema(description = "Expected precipitation (mm/hour) at which the terrace closes outright. "
                + "Overrides the profile.", example = "0.5")
        @DecimalMin("0") Double maxPrecipitationMm,
        @Schema(description = "Wind gusts (km/h) above which points are lost; 1.5x closes outright. "
                + "Overrides the profile.", example = "35")
        @DecimalMin("0") Double maxGustsKmh,
        @Schema(description = "Feels-like temperature (C) below which it is too cold. Must be lower than "
                + "maxApparentTempC. Overrides the profile.", example = "14")
        @DecimalMin("-40") @DecimalMax("50") Double minApparentTempC,
        @Schema(description = "Feels-like temperature (C) above which it is too hot. Overrides the profile.",
                example = "34")
        @DecimalMin("-40") @DecimalMax("60") Double maxApparentTempC) {

    public TerraceRules toRules() {
        TerraceRules base = (profile != null ? profile : TerraceProfile.TEMPERATE).rules();
        TerraceRules rules = new TerraceRules(
                maxRainProbability != null ? maxRainProbability : base.maxRainProbability(),
                maxPrecipitationMm != null ? maxPrecipitationMm : base.maxPrecipitationMm(),
                maxGustsKmh != null ? maxGustsKmh : base.maxGustsKmh(),
                minApparentTempC != null ? minApparentTempC : base.minApparentTempC(),
                maxApparentTempC != null ? maxApparentTempC : base.maxApparentTempC());
        if (rules.minApparentTempC() >= rules.maxApparentTempC()) {
            throw new InvalidRequestException("minApparentTempC must be lower than maxApparentTempC");
        }
        return rules;
    }
}
