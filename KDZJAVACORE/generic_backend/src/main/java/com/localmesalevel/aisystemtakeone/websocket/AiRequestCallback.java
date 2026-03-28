package com.localmesalevel.aisystemtakeone.websocket;

import java.util.Iterator;
import java.util.function.Consumer;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Callback interface for AI requests from WebSocket.
 */
public interface AiRequestCallback {

    /**
     * Called when an AI request is received from the bot.
     *
     * @param requestId      Unique request ID
     * @param userId         User identifier (IRC nick or Telegram user)
     * @param channel        Channel/chat identifier
     * @param platform       Platform type ("irc" or "telegram")
     * @param systemPrompt   System prompt text
     * @param userQuery      User's query
     * @param onSuccess      Callback for successful response
     * @param onError        Callback for errors
     */
    void onAiRequest(
            String requestId,
            String userId,
            String channel,
            String platform,
            String systemPrompt,
            Consumer<String> onSuccess,
            Consumer<String> onError,
            Iterator<JsonNode> aiContext
    );
}
