package org.traducao.projeto.animes.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "tradutor.llm")
public record LlmProperties(
    String baseUrl,
    String model,
    double temperature,
    int maxTokens,
    Duration connectTimeout,
    Duration readTimeout
) {
    public LlmProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "http://127.0.0.1:1234/v1";
        }
        if (model == null || model.isBlank()) {
            model = "mistralai/mistral-nemo-instruct-2407";
        }
        if (temperature <= 0) {
            temperature = 0.3;
        }
        if (maxTokens <= 0) {
            maxTokens = 2000;
        }
        if (connectTimeout == null) {
            connectTimeout = Duration.ofSeconds(5);
        }
        if (readTimeout == null) {
            readTimeout = Duration.ofSeconds(90);
        }
    }
}
