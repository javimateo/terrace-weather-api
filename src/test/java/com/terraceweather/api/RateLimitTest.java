package com.terraceweather.api;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.terraceweather.ClockConfig;
import com.terraceweather.scoring.TerraceScorer;
import com.terraceweather.scoring.TerraceService;
import com.terraceweather.weather.WeatherForecast;
import com.terraceweather.weather.WeatherProvider;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(value = TerraceController.class, properties = "terrace.rate-limit.requests-per-minute=2")
@Import({TerraceService.class, TerraceScorer.class, ClockConfig.class, RateLimitFilter.class})
class RateLimitTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    WeatherProvider weather;

    @Test
    void requestsOverTheLimitGet429WithRetryAfter() throws Exception {
        when(weather.fetch(anyDouble(), anyDouble()))
                .thenReturn(new WeatherForecast(ZoneId.of("UTC"), List.of()));

        mvc.perform(get("/api/v1/terrace/profiles")).andExpect(status().isOk())
                .andExpect(header().string("X-RateLimit-Remaining", "1"));
        mvc.perform(get("/api/v1/terrace/profiles")).andExpect(status().isOk())
                .andExpect(header().string("X-RateLimit-Remaining", "0"));
        mvc.perform(get("/api/v1/terrace/profiles")).andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }
}
