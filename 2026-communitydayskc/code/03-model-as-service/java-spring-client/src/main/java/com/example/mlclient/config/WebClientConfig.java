package com.example.mlclient.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Value("${ml.service.url:http://localhost:8000}")
    private String mlServiceUrl;

    @Bean
    public WebClient mlWebClient(WebClient.Builder builder) {
        return builder
            .baseUrl(mlServiceUrl)
            .defaultHeader("Content-Type", "application/json")
            .build();
    }
}
