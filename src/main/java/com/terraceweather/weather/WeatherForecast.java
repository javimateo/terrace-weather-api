package com.terraceweather.weather;

import java.time.ZoneId;
import java.util.List;

public record WeatherForecast(ZoneId timezone, List<HourlyForecast> hours) {
}
