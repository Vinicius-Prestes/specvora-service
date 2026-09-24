package br.com.specvora_service.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI specvoraOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Specvora Service API")
                        .description("API de consulta de especificações técnicas de veículos")
                        .version("1.0.0"));
    }
}
