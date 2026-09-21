package com.terraceweather.api;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI terraceWeatherOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Terrace Weather API")
                .version("v1")
                .description("Tells restaurants whether their terrace is viable for a given hour or "
                        + "service window, based on the weather forecast. "
                        + "Weather data by Open-Meteo.com (CC BY 4.0). Limit: 60 requests/minute per IP."));
    }
}
