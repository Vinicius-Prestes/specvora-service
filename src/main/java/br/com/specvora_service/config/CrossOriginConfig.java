package br.com.specvora_service.config;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Configuration
public class CrossOriginConfig {

    @Bean
    public WebMvcConfigurer corsConfigurer(Properties properties) {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                List<String> origins = properties.allowedOrigins;
                registry.addMapping("/**")
                        .allowedOrigins(origins.toArray(new String[0]))
                        .allowedHeaders("Content-Type", "Accept", "Authorization")
                        .allowedMethods("GET", "POST")
                        .maxAge(3600);
                log.info("CORS: Allowing origins {}",
                        String.join(" | ", properties.allowedOrigins));
            }
        };
    }

    @Primary
    @Getter
    @Setter
    @Component
    @ConfigurationProperties(prefix = "http.cors")
    public static class Properties {
        private List<String> allowedOrigins = new ArrayList<>();
    }
}
