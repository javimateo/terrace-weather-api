package com.terraceweather.scoring;

/** The caller asked for something that cannot be answered (bad thresholds, window outside the forecast...). */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
