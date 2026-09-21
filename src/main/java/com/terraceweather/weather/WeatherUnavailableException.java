package com.terraceweather.weather;

public class WeatherUnavailableException extends RuntimeException {

    public WeatherUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
