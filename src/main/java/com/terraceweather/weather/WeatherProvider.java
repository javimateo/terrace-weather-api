package com.terraceweather.weather;

/** Port for any hourly weather source, so providers can be swapped (Open-Meteo, AEMET...). */
public interface WeatherProvider {

    WeatherForecast fetch(double latitude, double longitude);
}
