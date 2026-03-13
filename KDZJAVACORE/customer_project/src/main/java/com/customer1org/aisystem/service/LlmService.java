package com.customer1org.aisystem.service;

import com.localmesalevel.aisystemtakeone.llm.service.LlmConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.concurrent.CompletableFuture;

/**
 * Service for LLM interactions.
 * Wraps the existing LlmConnection to provide async-friendly interface.
 */
@Service
public class LlmService {

    private static final Logger logger = LoggerFactory.getLogger(LlmService.class);

    private final com.localmesalevel.aisystemtakeone.llm.service.LlmConnection llmConnection;

    @Value("${ai.model:claude-3-5-sonnet-20241022}")
    private String defaultModel;

    @Value("${ai.temperagure:0.7}")
    private double temperature;

    @Value("${ai.max-tokens:2048}")
    private int maxTokens;

    public LlmService(com.localmesalevel.aisystemtakeone.llm.service.LlmConnection llmConnection) {
        this.llmConnection = llmConnection;
    }

    @PostConstruct
    public void init() {
        logger.info("LlmService initialized with model: {}", defaultModel);
    }

    /**
     * Process a bot query asynchronously.
     *
     * @param systemPrompt System prompt
     * @param userQuery    User's query
     * @return CompletableFuture with the response
     */
    public CompletableFuture<String> processBotQuery(String systemPrompt, String userQuery) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.debug("Calling LLM with system prompt [{} chars] and user query [{} chars]",
                        systemPrompt.length(), userQuery.length());

                return llmConnection.complete(
                        defaultModel,
                        systemPrompt,
                        userQuery,
                        temperature,
                        maxTokens
                );
            } catch (Exception e) {
                logger.error("LLM call failed", e);
                throw new RuntimeException("Failed to get AI response: " + e.getMessage(), e);
            }
        });
    }
}
