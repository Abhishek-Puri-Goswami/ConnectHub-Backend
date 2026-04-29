package com.connecthub.notification.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.util.Base64;

/**
 * FirebaseConfig — Conditionally initialises the Firebase Admin SDK.
 *
 * Only activates when the FIREBASE_SERVICE_ACCOUNT_JSON environment variable is set.
 * The variable must contain the service account JSON encoded in Base64. If the variable
 * is absent or blank, Firebase is silently skipped and FcmService becomes a no-op.
 */
@Configuration
@Slf4j
public class FirebaseConfig {

    @Bean
    public FirebaseApp firebaseApp() {
        String encoded = System.getenv("FIREBASE_SERVICE_ACCOUNT_JSON");
        if (encoded == null || encoded.isBlank()) {
            log.info("FIREBASE_SERVICE_ACCOUNT_JSON not set — FCM push notifications disabled");
            return null;
        }
        try {
            byte[] json = Base64.getDecoder().decode(encoded.trim());
            GoogleCredentials credentials = GoogleCredentials.fromStream(new ByteArrayInputStream(json));
            FirebaseOptions options = FirebaseOptions.builder().setCredentials(credentials).build();
            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp app = FirebaseApp.initializeApp(options);
                log.info("Firebase Admin SDK initialised");
                return app;
            }
            return FirebaseApp.getInstance();
        } catch (Exception e) {
            log.error("Failed to initialise Firebase Admin SDK: {}", e.getMessage());
            return null;
        }
    }
}
