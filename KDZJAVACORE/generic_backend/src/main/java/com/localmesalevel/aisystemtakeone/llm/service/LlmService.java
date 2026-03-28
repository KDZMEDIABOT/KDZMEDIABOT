package com.localmesalevel.aisystemtakeone.llm.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;

import javax.annotation.PostConstruct;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Service for LLM interactions.
 * Wraps the existing LlmConnection to provide async-friendly interface.
 */
@Service
public class LlmService {

    private static final Logger logger = LoggerFactory.getLogger(LlmService.class);

    @Value("${ai.temperature:0}")
    private double temperature;

    @Value("${ai.max-tokens:300}")
    private int maxTokens;

    @PostConstruct
    public void init() {
        logger.info("LlmService initialized");
    }

    /**
     * Process a bot query asynchronously.
     *
     * @param llmConnection LLM connection (passed as parameter, not a bean)
     * @param systemPrompt System prompt
     * @param userQuery User's query
     * @return CompletableFuture with the response
     */
    public CompletableFuture<String> processBotQuery(
            com.localmesalevel.aisystemtakeone.llm.service.LlmConnection llmConnection,
            String systemPrompt,
            Iterator<JsonNode> aiContext) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.debug("Calling LLM with system prompt [{} chars] and user query [{} length]",
                        systemPrompt.length(), aiContext.hasNext()?1:0);

                List<String> aiContextAsList = new LinkedList<String>();
                while(aiContext.hasNext())
                	aiContextAsList.add(aiContext.next().asText(""));
                return llmConnection.complete(
                        systemPrompt,
                        aiContextAsList.iterator(),
                        temperature,
                        maxTokens
                );
            } catch (Throwable e) {
                logger.error("LLM call failed", e);
                throw new RuntimeException("Failed to get AI response: " + e, e);
            }
        });
    }
}
