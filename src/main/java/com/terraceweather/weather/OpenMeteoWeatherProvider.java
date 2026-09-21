package com.terraceweather.weather;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Open-Meteo client (free, no API key). Responses are cached per ~1 km grid cell in a bounded LRU cache,
 * so neither repeated nor scattered coordinates can exhaust memory or hammer the upstream quota.
 */
@Component
public class OpenMeteoWeatherProvider implements WeatherProvider {

    private static final String HOURLY_VARS = "temperature_2m,apparent_temperature,"
            + "precipitation_probability,precipitation,wind_speed_10m,wind_gusts_10m";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(8);
    private static final int MAX_CACHE_ENTRIES = 1_000;

    private record CacheEntry(WeatherForecast forecast, Instant expiresAt) {
    }

    private final RestClient client;
    private final Duration cacheTtl;
    private final Clock clock;
    private final Map<String, CacheEntry> cache = Collections.synchronizedMap(
            new LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {
                    return size() > MAX_CACHE_ENTRIES;
                }
            });

    @Autowired
    public OpenMeteoWeatherProvider(
            @Value("${terrace.weather.open-meteo.base-url:https://api.open-meteo.com}") String baseUrl,
            @Value("${terrace.weather.cache-ttl:15m}") Duration cacheTtl,
            Clock clock) {
        this(buildClient(baseUrl), cacheTtl, clock);
    }

    OpenMeteoWeatherProvider(RestClient client, Duration cacheTtl, Clock clock) {
        this.client = client;
        this.cacheTtl = cacheTtl;
        this.clock = clock;
    }

    private static RestClient buildClient(String baseUrl) {
        JdkClientHttpRequestFactory factory =
                new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build());
        factory.setReadTimeout(READ_TIMEOUT);
        return RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    @Override
    public WeatherForecast fetch(double latitude, double longitude) {
        String key = String.format(Locale.ROOT, "%.2f,%.2f", latitude, longitude);
        Instant now = clock.instant();
        CacheEntry cached = cache.get(key);
        if (cached != null && cached.expiresAt().isAfter(now)) {
            return cached.forecast();
        }
        WeatherForecast forecast = call(latitude, longitude);
        cache.put(key, new CacheEntry(forecast, now.plus(cacheTtl)));
        return forecast;
    }

    private WeatherForecast call(double latitude, double longitude) {
        Response response;
        try {
            response = client.get()
                    .uri(uri -> uri.path("/v1/forecast")
                            .queryParam("latitude", latitude)
                            .queryParam("longitude", longitude)
                            .queryParam("hourly", HOURLY_VARS)
                            .queryParam("wind_speed_unit", "kmh")
                            .queryParam("timezone", "auto")
                            .queryParam("forecast_days", 3)
                            .build())
                    .retrieve()
                    .body(Response.class);
        } catch (RestClientException e) {
            throw new WeatherUnavailableException("Weather provider request failed", e);
        }
        if (response == null || response.timezone() == null
                || response.hourly() == null || response.hourly().time() == null) {
            throw new WeatherUnavailableException("Weather provider returned no hourly data", null);
        }
        WeatherForecast forecast = toForecast(response);
        if (forecast.hours().isEmpty()) {
            throw new WeatherUnavailableException("Weather provider returned no usable hourly data", null);
        }
        return forecast;
    }

    /** Hours missing a required measurement are dropped: a gap must never read as "good weather". */
    private static WeatherForecast toForecast(Response response) {
        Hourly h = response.hourly();
        List<HourlyForecast> hours = new ArrayList<>(h.time().size());
        for (int i = 0; i < h.time().size(); i++) {
            Double temp = at(h.temperature(), i);
            Double apparent = at(h.apparentTemperature(), i);
            Double precip = at(h.precipitation(), i);
            Double wind = at(h.windSpeed(), i);
            Double gusts = at(h.windGusts(), i);
            if (temp == null || apparent == null || precip == null || wind == null || gusts == null) {
                continue;
            }
            Double probability = at(h.precipitationProbability(), i);
            hours.add(new HourlyForecast(
                    LocalDateTime.parse(h.time().get(i)),
                    temp, apparent,
                    probability == null ? null : (int) Math.round(probability),
                    precip, wind, gusts));
        }
        return new WeatherForecast(ZoneId.of(response.timezone()), List.copyOf(hours));
    }

    private static Double at(List<Double> values, int i) {
        return values != null && i < values.size() ? values.get(i) : null;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Response(String timezone, Hourly hourly) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Hourly(
            List<String> time,
            @JsonProperty("temperature_2m") List<Double> temperature,
            @JsonProperty("apparent_temperature") List<Double> apparentTemperature,
            @JsonProperty("precipitation_probability") List<Double> precipitationProbability,
            List<Double> precipitation,
            @JsonProperty("wind_speed_10m") List<Double> windSpeed,
            @JsonProperty("wind_gusts_10m") List<Double> windGusts) {
    }
}
