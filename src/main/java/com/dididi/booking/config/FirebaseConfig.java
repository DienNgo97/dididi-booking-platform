package com.dididi.booking.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.FileInputStream;
import java.io.InputStream;

/**
 * Khoi tao Firebase Admin SDK cho push (FCM) — CHI khi app.firebase.enabled=true va co
 * service-account JSON (app.firebase.credentials-path). Khong bat -> khong co bean nay ->
 * PushSender bo qua (app van chay binh thuong).
 */
@Configuration
@ConditionalOnProperty(name = "app.firebase.enabled", havingValue = "true")
public class FirebaseConfig {

    private static final Logger log = LoggerFactory.getLogger(FirebaseConfig.class);

    @Bean
    public FirebaseApp firebaseApp(@Value("${app.firebase.credentials-path}") String path) throws Exception {
        try (InputStream in = new FileInputStream(path)) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(in))
                    .build();
            FirebaseApp app = FirebaseApp.getApps().isEmpty()
                    ? FirebaseApp.initializeApp(options)
                    : FirebaseApp.getInstance();
            log.info("Firebase Admin SDK đã khởi tạo (push FCM bật).");
            return app;
        }
    }
}
