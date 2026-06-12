package com.gulfnet.integrationmanagement.config;

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
     * Creates and configures a custom OpenAPI specification bean for Swagger documentation.
     * Sets up API information including title, version, description, contact details, license,
     * and server configurations for the Integration Management API.
     *
     * @return OpenAPI object with configured API documentation details
     */
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Integration Management API")
                        .version("1.0")
                        .description("API for managing integrations and attachments in the QR Table system")
                        .contact(new Contact()
                                .name("GulfNet Team")
                                .email("support@gulfnet.com"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(List.of(
                        new Server().url("/integration").description("Gateway Server")
                ));
    }
} 