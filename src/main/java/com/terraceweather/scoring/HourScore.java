package com.terraceweather.scoring;

import com.terraceweather.weather.HourlyForecast;
import java.util.List;

public record HourScore(int score, TerraceVerdict verdict, List<String> reasons, HourlyForecast conditions) {
}
