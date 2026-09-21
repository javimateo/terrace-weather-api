package com.terraceweather.weather;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class OpenMeteoWeatherProviderTest {

    private static final String JSON = """
            {"timezone":"Asia/Bangkok","utc_offset_seconds":25200,"hourly":{
              "time":["2026-09-18T00:00","2026-09-18T01:00","2026-09-18T02:00"],
              "temperature_2m":[26.1,25.9,null],
              "apparent_temperature":[29.0,28.5,28.0],
              "precipitation_probability":[80,null,10],
              "precipitation":[0.4,0.0,0.0],
              "wind_speed_10m":[5.0,4.0,3.0],
              "wind_gusts_10m":[12.0,10.0,9.0]}}
            """;

    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-09-18T00:00:00Z"));
    private final Clock clock = new Clock() {
        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    };

    private MockRestServiceServer server;
    private OpenMeteoWeatherProvider provider;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://open-meteo.test");
        server = MockRestServiceServer.bindTo(builder).build();
        provider = new OpenMeteoWeatherProvider(builder.build(), Duration.ofMinutes(15), clock);
    }

    @Test
    void parsesTimezoneAndHourlyValues() {
        server.expect(once(), request -> assertThat(request.getURI().getQuery())
                        .contains("latitude=40.4").contains("longitude=-3.7").contains("timezone=auto"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(JSON, MediaType.APPLICATION_JSON));

        WeatherForecast f = provider.fetch(40.4, -3.7);

        assertThat(f.timezone()).isEqualTo(ZoneId.of("Asia/Bangkok"));
        HourlyForecast first = f.hours().get(0);
        assertThat(first.time()).isEqualTo(LocalDateTime.of(2026, 9, 18, 0, 0));
        assertThat(first.temperatureC()).isEqualTo(26.1);
        assertThat(first.precipitationProbability()).isEqualTo(80);
        assertThat(first.windGustsKmh()).isEqualTo(12.0);
    }

    @Test
    void hoursMissingARequiredMeasurementAreDroppedButMissingProbabilityIsKeptAsUnknown() {
        server.expect(once(), requestTo(org.hamcrest.Matchers.startsWith("http://open-meteo.test/v1/forecast")))
                .andRespond(withSuccess(JSON, MediaType.APPLICATION_JSON));

        WeatherForecast f = provider.fetch(40.4, -3.7);

        // hour 02:00 has no temperature -> dropped; hour 01:00 has no probability -> kept with null
        assertThat(f.hours()).hasSize(2);
        assertThat(f.hours().get(1).precipitationProbability()).isNull();
    }

    @Test
    void secondCallWithinTtlIsServedFromCacheAndExpiredEntriesAreRefetched() {
        server.expect(times(2), requestTo(org.hamcrest.Matchers.startsWith("http://open-meteo.test/v1/forecast")))
                .andRespond(withSuccess(JSON, MediaType.APPLICATION_JSON));

        provider.fetch(40.4, -3.7);
        provider.fetch(40.4, -3.7);                       // cached: no second HTTP call yet
        now.set(now.get().plus(Duration.ofMinutes(16)));
        provider.fetch(40.4, -3.7);                       // expired: second HTTP call

        server.verify();
    }

    @Test
    void nearbyCoordinatesShareACacheCell() {
        server.expect(once(), requestTo(org.hamcrest.Matchers.startsWith("http://open-meteo.test/v1/forecast")))
                .andRespond(withSuccess(JSON, MediaType.APPLICATION_JSON));

        provider.fetch(40.4001, -3.7001);
        provider.fetch(40.4004, -3.7004);

        server.verify();
    }

    @Test
    void upstreamErrorBecomesWeatherUnavailable() {
        server.expect(once(), requestTo(org.hamcrest.Matchers.startsWith("http://open-meteo.test/v1/forecast")))
                .andRespond(withServerError());

        assertThatThrownBy(() -> provider.fetch(40.4, -3.7)).isInstanceOf(WeatherUnavailableException.class);
    }

    @Test
    void emptyPayloadBecomesWeatherUnavailable() {
        server.expect(once(), requestTo(org.hamcrest.Matchers.startsWith("http://open-meteo.test/v1/forecast")))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> provider.fetch(40.4, -3.7)).isInstanceOf(WeatherUnavailableException.class);
    }
}
