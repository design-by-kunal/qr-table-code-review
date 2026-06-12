package com.gulfnet.restaurantmanagement;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

@SpringBootApplication(scanBasePackages = {
    "com.gulfnet.restaurantmanagement", 
    "com.gulfnet.shared_library"
})
@EnableJpaRepositories(basePackages = "com.gulfnet.shared_library.repository")
@EntityScan(basePackages = "com.gulfnet.shared_library.entity")
@EnableAsync
public class RestaurantManagementApplication {
    
    static {
        // Set UTF-8 encoding BEFORE Spring Boot starts loading properties
        // This is critical for reading Unicode characters in application.properties
        String utf8 = StandardCharsets.UTF_8.name();
        System.setProperty("file.encoding", utf8);
        System.setProperty("sun.jnu.encoding", utf8);
    }
    
    public static void main(String[] args) {
        // Ensure UTF-8 is set before Spring Boot initialization
        if (!Charset.defaultCharset().equals(StandardCharsets.UTF_8)) {
            String utf8 = StandardCharsets.UTF_8.name();
            System.setProperty("file.encoding", utf8);
            System.setProperty("sun.jnu.encoding", utf8);
        }
        
        SpringApplication.run(RestaurantManagementApplication.class, args);
    }
}
