package com.portfolioiq.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Cross-origin access is off unless CORS_ALLOWED_ORIGINS lists origins. The web app and installed PWA use a
 * same-origin proxy and need none. Set it for native shells (for example Capacitor) that call the API directly.
 */
@Configuration
public class CorsConfig {
    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${app.cors.allowed-origins:}") List<String> origins) {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        List<String> allowed = origins.stream().map(String::trim).filter(o -> !o.isEmpty()).toList();
        if (!allowed.isEmpty()) {
            CorsConfiguration c = new CorsConfiguration();
            c.setAllowedOrigins(allowed);
            c.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
            c.setAllowedHeaders(List.of("Authorization", "Content-Type"));
            c.setExposedHeaders(List.of("Retry-After"));
            c.setAllowCredentials(false);
            c.setMaxAge(3600L);
            source.registerCorsConfiguration("/api/**", c);
        }
        return source;
    }
}
