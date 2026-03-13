package com.customer1org.aisystem.service;

import com.localmesalevel.aisystemtakeone.llm.model.LlmEndpointCredentials;
import com.localmesalevel.aisystemtakeone.llm.service.LlmConnection;
import com.localmesalevel.aisystemtakeone.llm.service.LlmConnectionFactory;
import com.localmesalevel.aisystemtakeone.user.model.UserAccount;
import com.localmesalevel.aisystemtakeone.user.repository.UserAccountRepository;
import com.localmesalevel.aisystemtakeone.websocket.AiRequestCallback;
import com.localmesalevel.aisystemtakeone.websocket.BotWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
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
    private final UserAccountRepository userAccountRepository;
    private final LlmConnectionFactory llmConnectionFactory;

    public BotAiService(@Lazy BotWebSocketHandler webSocketHandler,
                        LlmService llmService,
                        UserAccountRepository userAccountRepository,
                        LlmConnectionFactory llmConnectionFactory) {
        this.webSocketHandler = webSocketHandler;
        this.llmService = llmService;
        this.userAccountRepository = userAccountRepository;
        this.llmConnectionFactory = llmConnectionFactory;
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

        // Get KDZMEDIABOT user's LLM connection
        LlmConnection llmConnection = getKdmediabotConnection();
        if (llmConnection == null) {
            logger.error("Failed to get LLM connection for KDZMEDIABOT user");
            onError.accept("Error: LLM connection not configured for bot");
            return;
        }

        // Process in async way using CompletableFuture
        llmService.processBotQuery(llmConnection, systemPrompt, userQuery)
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

    /**
     * Get LLM connection for user "KDZMEDIABOT".
     * Extracts credentials from the user's current_llm_endpoint.
     */
    private LlmConnection getKdmediabotConnection() {
        try {
            java.util.Optional<UserAccount> botUserOpt = userAccountRepository.findByUsername("KDZMEDIABOT");
            if (botUserOpt.isEmpty()) {
                logger.error("User KDZMEDIABOT not found");
                return null;
            }

            UserAccount botUser = botUserOpt.get();
            LlmEndpointCredentials credentials = botUser.getCurrentLlmEndpoint();
            if (credentials == null) {
                logger.error("User KDZMEDIABOT has no current LLM endpoint configured");
                return null;
            }

            return llmConnectionFactory.create(credentials);
        } catch (Exception e) {
            logger.error("Failed to create LLM connection for KDZMEDIABOT", e);
            return null;
        }
    }
}
