package br.com.specvora_service.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

@Configuration
public class FirebaseConfig {

    private static final Logger log = LoggerFactory.getLogger(FirebaseConfig.class);

    @Value("${firebase.credentials.path}")
    private String credentialsPath;

    @Bean
    public FirebaseApp firebaseApp() {
        if (!FirebaseApp.getApps().isEmpty()) {
            return FirebaseApp.getInstance();
        }
        File credentialsFile = new File(credentialsPath);
        if (!credentialsFile.exists()) {
            log.warn("Arquivo de credenciais do Firebase não encontrado em: {}. O serviço operará com autenticação JWT nativa.", credentialsPath);
            return null;
        }
        try (FileInputStream fis = new FileInputStream(credentialsFile)) {
            GoogleCredentials credentials = GoogleCredentials.fromStream(fis);
            return FirebaseApp.initializeApp(
                    FirebaseOptions.builder()
                            .setCredentials(credentials)
                            .build());
        } catch (IOException e) {
            log.error("Erro ao inicializar Firebase Admin SDK a partir de {}", credentialsPath, e);
            return null;
        }
    }
}
