package br.com.specvora_service.config;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableMongoAuditing
public class MongoConfig {

    @Value("${spring.data.mongodb.uri}")
    private String mongoUri;

    @Bean
    public MongoClient mongoClient() {

        ConnectionString connectionString =
                new ConnectionString(mongoUri);

        MongoClientSettings settings =
                MongoClientSettings.builder()
                        .applyConnectionString(connectionString)
                        .applyToConnectionPoolSettings(builder ->
                                builder
                                        .maxSize(50)
                                        .minSize(5)
                                        .maxWaitTime(5, TimeUnit.SECONDS)
                        )
                        .applyToSocketSettings(builder ->
                                builder.connectTimeout(10, TimeUnit.SECONDS)
                        )
                        .retryWrites(true)
                        .build();

        return MongoClients.create(settings);
    }
}