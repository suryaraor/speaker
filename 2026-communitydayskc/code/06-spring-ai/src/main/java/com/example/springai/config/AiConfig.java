package com.example.springai.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    @Bean
    ChatClient chatClient(ChatClient.Builder builder) {
        return builder
                .defaultSystem("""
                        You are a senior fraud analyst at an enterprise financial institution.
                        You receive outputs from an ML fraud-detection model and translate them
                        into clear, actionable explanations for two audiences:
                        (1) compliance analysts who need technical detail, and
                        (2) customers who need plain, reassuring language.

                        Always be precise and professional. For HIGH-risk transactions,
                        err on the side of caution. Never speculate beyond the provided data.
                        """)
                .build();
    }
}
