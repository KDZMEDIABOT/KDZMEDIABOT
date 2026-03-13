package com.customer1org.aisystem.service;

import com.localmesalevel.aisystemtakeone.websocket.AiRequestCallback;
import com.localmesalevel.aisystemtakeone.websocket.BotWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.function.Consumer;

/**
 * Service that handles AI requests from the bot.
 * Implements AiRequestCallback to receive requests from WebSocket.
 */
@Service
public class BotAiService implements AiRequestCallback {

    private static final Logger logger = LoggerFactory.getLogger(BotAiService.class);

    private final BotWebSocketHandler webSocketHandler;
    private final LlmService llmService;

    public BotAiService(BotWebSocketHandler webSocketHandler, LlmService llmService) {
        this.webSocketHandler = webSocketHandler;
        this.llmService = llmService;
    }

    @PostConstruct
    public void init() {
        webSocketHandler.setAiRequestCallback(this);
        logger.info("BotAiService initialized and registered callback");
    }

    @Override
    public void onAiRequest(
            String requestId,
            String userId,
            String channel,
            String platform,
            String systemPrompt,
            String userQuery,
            Consumer<String> onSuccess,
            Consumer<String> onError) {

        logger.debug("Processing AI request {} from {} on {}", requestId, userId, platform);

        // Process in async way using CompletableFuture
        llmService.processBotQuery(systemPrompt, userQuery)
                .thenAccept(response -> {
                    logger.info("AI request {} completed, response length: {}",
                            requestId, response.length());
                    onSuccess.accept(response);
                })
                .exceptionally(throwable -> {
                    logger.error("AI request {} failed", requestId, throwable);
                    onError.accept("Error: " + throwable.getMessage());
                    return null;
                });
    }
}
