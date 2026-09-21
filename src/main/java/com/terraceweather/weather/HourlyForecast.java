package com.terraceweather.weather;

import java.time.LocalDateTime;

/**
 * Weather conditions for one hour, in the local time of the requested location.
 *
 * @param precipitationProbability percent, or {@code null} when the weather model does not provide it
 */
public record HourlyForecast(
        LocalDateTime time,
        double temperatureC,
        double apparentTemperatureC,
        Integer precipitationProbability,
        double precipitationMm,
        double windSpeedKmh,
        double windGustsKmh) {
}
