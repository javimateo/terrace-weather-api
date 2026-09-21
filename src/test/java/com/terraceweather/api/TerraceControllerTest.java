package com.terraceweather.api;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.terraceweather.ClockConfig;
import com.terraceweather.scoring.TerraceScorer;
import com.terraceweather.scoring.TerraceService;
import com.terraceweather.weather.HourlyForecast;
import com.terraceweather.weather.WeatherForecast;
import com.terraceweather.weather.WeatherProvider;
import com.terraceweather.weather.WeatherUnavailableException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TerraceController.class)
@Import({TerraceService.class, TerraceScorer.class, ClockConfig.class})
class TerraceControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    WeatherProvider weather;

    private static HourlyForecast hour(int h, int rainProb, double mm) {
        return new HourlyForecast(LocalDateTime.of(2026, 9, 18, h, 0), 24, 24, rainProb, mm, 5, 10);
    }

    @Test
    void windowUsesWorstHourAndSuggestsCapacity() throws Exception {
        when(weather.fetch(anyDouble(), anyDouble())).thenReturn(new WeatherForecast(
                ZoneId.of("Europe/Madrid"),
                List.of(hour(13, 0, 0), hour(14, 10, 0), hour(15, 90, 3.0), hour(16, 0, 0))));

        mvc.perform(get("/api/v1/terrace/window")
                        .param("lat", "40.4").param("lon", "-3.7")
                        .param("start", "2026-09-18T13:00:00").param("end", "2026-09-18T16:00:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verdict").value("CLOSED"))
                .andExpect(jsonPath("$.recommendedCapacityPercent").value(0))
                .andExpect(jsonPath("$.hours.length()").value(3));
    }

    @Test
    void windowWithClearWeatherIsOpen() throws Exception {
        when(weather.fetch(anyDouble(), anyDouble())).thenReturn(new WeatherForecast(
                ZoneId.of("Europe/Madrid"), List.of(hour(13, 0, 0), hour(14, 0, 0))));

        mvc.perform(get("/api/v1/terrace/window")
                        .param("lat", "40.4").param("lon", "-3.7")
                        .param("start", "2026-09-18T13:00:00").param("end", "2026-09-18T15:00:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verdict").value("OPEN"))
                .andExpect(jsonPath("$.recommendedCapacityPercent").value(100));
    }

    @Test
    void tropicalProfileToleratesRainThatClosesTemperate() throws Exception {
        when(weather.fetch(anyDouble(), anyDouble())).thenReturn(new WeatherForecast(
                ZoneId.of("Asia/Bangkok"), List.of(hour(13, 60, 0))));
        String url = "/api/v1/terrace/window";

        mvc.perform(get(url).param("lat", "13.7").param("lon", "100.5")
                        .param("start", "2026-09-18T13:00:00").param("end", "2026-09-18T14:00:00"))
                .andExpect(jsonPath("$.verdict").value("CAUTION"));
        mvc.perform(get(url).param("lat", "13.7").param("lon", "100.5").param("profile", "TROPICAL")
                        .param("start", "2026-09-18T13:00:00").param("end", "2026-09-18T14:00:00"))
                .andExpect(jsonPath("$.verdict").value("OPEN"));
    }

    @Test
    void explicitThresholdOverridesProfile() throws Exception {
        when(weather.fetch(anyDouble(), anyDouble())).thenReturn(new WeatherForecast(
                ZoneId.of("Asia/Bangkok"), List.of(hour(13, 60, 0))));

        mvc.perform(get("/api/v1/terrace/window").param("lat", "13.7").param("lon", "100.5")
                        .param("profile", "TROPICAL").param("maxRainProbability", "20")
                        .param("start", "2026-09-18T13:00:00").param("end", "2026-09-18T14:00:00"))
                .andExpect(jsonPath("$.verdict").value("CLOSED"));
    }

    @Test
    void unknownProfileIsBadRequest() throws Exception {
        mvc.perform(get("/api/v1/terrace/forecast").param("lat", "13.7").param("lon", "100.5")
                        .param("profile", "MARS"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void profilesEndpointListsAllPresets() throws Exception {
        mvc.perform(get("/api/v1/terrace/profiles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.TROPICAL.maxRainProbability").value(70))
                .andExpect(jsonPath("$.NORDIC.minApparentTempC").value(8.0));
    }

    @Test
    void inconsistentTemperatureRangeIsBadRequestProblemJson() throws Exception {
        mvc.perform(get("/api/v1/terrace/forecast").param("lat", "40").param("lon", "-3")
                        .param("minApparentTempC", "30").param("maxApparentTempC", "10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("minApparentTempC must be lower than maxApparentTempC"));
    }

    private void stubDay() {
        when(weather.fetch(anyDouble(), anyDouble())).thenReturn(new WeatherForecast(
                ZoneId.of("Europe/Madrid"),
                List.of(hour(13, 0, 0), hour(14, 0, 0), hour(15, 90, 3.0), hour(16, 0, 0), hour(17, 0, 0))));
    }

    @Test
    void batchReturnsOneOutcomePerWindowAndKeepsIds() throws Exception {
        stubDay();
        String body = """
                {"lat":40.4,"lon":-3.7,"rules":{"profile":"MEDITERRANEAN"},"windows":[
                  {"id":"mesa-12","start":"2026-09-18T13:00:00","end":"2026-09-18T15:00:00"},
                  {"id":"mesa-7","start":"2026-09-18T15:00:00","end":"2026-09-18T16:00:00"},
                  {"id":"rota","start":"2026-09-18T18:00:00","end":"2026-09-18T17:00:00"}]}
                """;

        mvc.perform(post("/api/v1/terrace/windows").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.length()").value(3))
                .andExpect(jsonPath("$.results[0].id").value("mesa-12"))
                .andExpect(jsonPath("$.results[0].window.verdict").value("OPEN"))
                .andExpect(jsonPath("$.results[1].window.verdict").value("CLOSED"))
                .andExpect(jsonPath("$.results[2].error").value("'end' must be after 'start'"))
                .andExpect(jsonPath("$.results[2].window").doesNotExist());
    }

    @Test
    void batchAcceptsExactlyTwentyFiveWindowsAndRejectsTwentySix() throws Exception {
        stubDay();
        String item = "{\"start\":\"2026-09-18T13:00:00\",\"end\":\"2026-09-18T14:00:00\"}";
        for (int n : new int[] {25, 26}) {
            String body = "{\"lat\":40.4,\"lon\":-3.7,\"windows\":["
                    + String.join(",", java.util.Collections.nCopies(n, item)) + "]}";
            var result = mvc.perform(post("/api/v1/terrace/windows")
                    .contentType(MediaType.APPLICATION_JSON).content(body));
            if (n == 25) {
                result.andExpect(status().isOk());
            } else {
                result.andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.detail").value("windows: size must be between 1 and 25"));
            }
        }
    }

    @Test
    void validationErrorsNameTheOffendingParameter() throws Exception {
        mvc.perform(get("/api/v1/terrace/forecast").param("lat", "999").param("lon", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("lat: ")));
        mvc.perform(get("/api/v1/terrace/forecast").param("lat", "40").param("lon", "0")
                        .param("maxRainProbability", "500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("maxRainProbability")));
    }

    @Test
    void batchRejectsEmptyListMissingCoordinatesAndBadRules() throws Exception {
        String[] bodies = {
                "{\"lat\":40.4,\"lon\":-3.7,\"windows\":[]}",
                "{\"windows\":[{\"start\":\"2026-09-18T13:00:00\",\"end\":\"2026-09-18T14:00:00\"}]}",
                "{\"lat\":40.4,\"lon\":-3.7,\"rules\":{\"maxRainProbability\":500},\"windows\":"
                        + "[{\"start\":\"2026-09-18T13:00:00\",\"end\":\"2026-09-18T14:00:00\"}]}",
                "{\"lat\":40.4,\"lon\":-3.7,\"windows\":[{\"start\":\"not-a-date\",\"end\":\"2026-09-18T14:00:00\"}]}"
        };
        for (String body : bodies) {
            mvc.perform(post("/api/v1/terrace/windows").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void bestWindowReturnsTheBestSlotOfTheDay() throws Exception {
        stubDay();

        mvc.perform(get("/api/v1/terrace/best-window").param("lat", "40.4").param("lon", "-3.7")
                        .param("date", "2026-09-18").param("durationHours", "2")
                        .param("fromHour", "13").param("toHour", "18"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.best.verdict").value("OPEN"))
                // 13-15 and 16-18 are both perfect; the earliest wins and the other is the alternative
                .andExpect(jsonPath("$.best.start").value("2026-09-18T13:00:00"))
                .andExpect(jsonPath("$.alternatives.length()").value(1))
                .andExpect(jsonPath("$.alternatives[0].start").value("2026-09-18T16:00:00"))
                .andExpect(jsonPath("$.durationHours").value(2));
    }

    @Test
    void bestWindowRejectsInvalidDuration() throws Exception {
        mvc.perform(get("/api/v1/terrace/best-window").param("lat", "40.4").param("lon", "-3.7")
                        .param("date", "2026-09-18").param("durationHours", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void timesWithZoneOrOffsetAreRejectedInsteadOfSilentlyIgnored() throws Exception {
        stubDay();
        for (String suffix : new String[] {"Z", "%2B02:00"}) {
            mvc.perform(get("/api/v1/terrace/window").param("lat", "40.4").param("lon", "-3.7")
                            .queryParam("start", "2026-09-18T13:00:00" + suffix.replace("%2B", "+"))
                            .param("end", "2026-09-18T15:00:00"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value(
                            org.hamcrest.Matchers.containsString("start: expected a local date-time")));
        }
    }

    @Test
    void secondsAreOptionalInLocalDateTimes() throws Exception {
        stubDay();
        mvc.perform(get("/api/v1/terrace/window").param("lat", "40.4").param("lon", "-3.7")
                        .param("start", "2026-09-18T13:00").param("end", "2026-09-18T15:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verdict").value("OPEN"));
    }

    @Test
    void batchRejectsTimesWithZoneOrOffsetAndUnknownProfile() throws Exception {
        String[] bodies = {
                "{\"lat\":40,\"lon\":-3,\"windows\":[{\"start\":\"2026-09-18T13:00:00Z\","
                        + "\"end\":\"2026-09-18T15:00:00Z\"}]}",
                "{\"lat\":40,\"lon\":-3,\"windows\":[{\"start\":\"2026-09-18T13:00:00+02:00\","
                        + "\"end\":\"2026-09-18T15:00:00+02:00\"}]}",
                "{\"lat\":40,\"lon\":-3,\"rules\":{\"profile\":\"MARS\"},\"windows\":"
                        + "[{\"start\":\"2026-09-18T13:00:00\",\"end\":\"2026-09-18T15:00:00\"}]}"
        };
        for (String body : bodies) {
            mvc.perform(post("/api/v1/terrace/windows").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value(
                            org.hamcrest.Matchers.containsString("no 'Z' and no UTC offset")));
        }
    }

    @Test
    void wrongTypesNameTheParameterAndWhatWasExpected() throws Exception {
        mvc.perform(get("/api/v1/terrace/best-window").param("lat", "40.4").param("lon", "-3.7")
                        .param("date", "tomorrow"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("date: expected a date like 2026-09-22"));
        mvc.perform(get("/api/v1/terrace/forecast").param("lat", "40.4").param("lon", "-3.7")
                        .param("profile", "MARS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(
                        org.hamcrest.Matchers.containsString("profile: invalid value 'MARS' (expected one of [TEMPERATE")));
        mvc.perform(get("/api/v1/terrace/forecast").param("lat", "40.4").param("lon", "-3.7")
                        .param("maxRainProbability", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("maxRainProbability: invalid value 'abc'"));
    }

    @Test
    void nanCoordinatesAreBadRequest() throws Exception {
        mvc.perform(get("/api/v1/terrace/forecast").param("lat", "NaN").param("lon", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidLatitudeIsBadRequest() throws Exception {
        mvc.perform(get("/api/v1/terrace/forecast").param("lat", "123").param("lon", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void windowWithoutCoverageIsBadRequest() throws Exception {
        when(weather.fetch(anyDouble(), anyDouble())).thenReturn(new WeatherForecast(
                ZoneId.of("Europe/Madrid"), List.of(hour(13, 0, 0))));

        mvc.perform(get("/api/v1/terrace/window")
                        .param("lat", "40.4").param("lon", "-3.7")
                        .param("start", "2030-01-01T13:00:00").param("end", "2030-01-01T15:00:00"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void providerFailureIsBadGateway() throws Exception {
        when(weather.fetch(anyDouble(), anyDouble()))
                .thenThrow(new WeatherUnavailableException("down", null));

        mvc.perform(get("/api/v1/terrace/forecast").param("lat", "40.4").param("lon", "-3.7"))
                .andExpect(status().isBadGateway());
    }
}
