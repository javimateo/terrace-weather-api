package com.terraceweather.api;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Public API without credentials: allow browser apps from any origin (listed as CORS "Yes" in public-apis). */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**").allowedOrigins("*").allowedMethods("GET", "POST", "OPTIONS");
    }
}
