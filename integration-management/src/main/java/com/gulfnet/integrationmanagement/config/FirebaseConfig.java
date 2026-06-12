package com.gulfnet.integrationmanagement.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;



@Configuration
public class FirebaseConfig {
    @Value("${app.firebase-configuration-file}")
    private String firebaseConfigPath;
    
    /**
     * Creates and initializes the primary {@link FirebaseApp} instance using the
     * configuration file path defined in the application properties.
     * If an instance already exists, that instance is returned.
     *
     * @return the initialized or existing {@link FirebaseApp}
     * @throws IOException if the Firebase configuration file cannot be read
     */
    @Bean
  public FirebaseApp firebaseApp() throws IOException {
    try (var is = new ClassPathResource(firebaseConfigPath).getInputStream()) {
      var options = FirebaseOptions.builder()
          .setCredentials(GoogleCredentials.fromStream(is))
          .build();
      if (FirebaseApp.getApps().isEmpty()) {
        return FirebaseApp.initializeApp(options);
      }
      return FirebaseApp.getInstance();
    }
  }
}

