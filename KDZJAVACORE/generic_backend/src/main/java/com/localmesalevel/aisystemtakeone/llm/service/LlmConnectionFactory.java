package com.localmesalevel.aisystemtakeone.llm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.localmesalevel.aisystemtakeone.llm.model.LlmEndpointCredentials;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class LlmConnectionFactory {

    private final RestTemplateBuilder restTemplateBuilder;
    private final ObjectMapper objectMapper;

    public LlmConnectionFactory(RestTemplateBuilder restTemplateBuilder, ObjectMapper objectMapper) {
        this.restTemplateBuilder = restTemplateBuilder;
        this.objectMapper = objectMapper;
    }

    public LlmConnection create(LlmEndpointCredentials credentials) {
        if (credentials == null) {
            throw new IllegalArgumentException("LlmEndpointCredentials is required");
        }

        RestTemplate restTemplate = restTemplateBuilder.build();
        return new LlmConnection(
            credentials.getLlmApiType(),
            credentials.getBaseURL(),
            credentials.getApiKey(),
            credentials.getModelName(),
            restTemplate,
            objectMapper,
                credentials
        );
    }
}
