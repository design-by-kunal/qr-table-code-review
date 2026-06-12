package com.gulfnet.restaurantmanagement.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SwaggerConfig {

    /**
     * Creates and configures the OpenAPI (Swagger) documentation for the Restaurant Management API.
     * Defines API metadata including title, version, description, contact information, license,
     * and server URLs for API documentation.
     *
     * @return configured OpenAPI instance with API documentation metadata
     */
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Restaurant Management API")
                        .version("1.0")
                        .description("API for managing restaurants, menu structures, and items in the QR Table system")
                        .contact(new Contact()
                                .name("GulfNet Team")
                                .email("support@gulfnet.com"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(List.of(
                        new Server().url("/restaurant").description("Gateway Server")
                ));
    }
} 