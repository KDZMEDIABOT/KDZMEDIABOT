package com.localmesalevel.aisystemtakeone.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * WebSocket handler for bot connections from KDZMEDIABOT.
 * Manages AI request routing and receives commands from Python bot.
 */
public class BotWebSocketHandler extends TextWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(BotWebSocketHandler.class);

    private final ObjectMapper objectMapper;
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, SessionInfo> sessionInfo = new ConcurrentHashMap<>();
    private AiRequestCallback aiRequestCallback;

    private final ScheduledExecutorService heartbeatExecutor = Executors.newSingleThreadScheduledExecutor();

    public BotWebSocketHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        // Start heartbeat checker
        heartbeatExecutor.scheduleAtFixedRate(this::checkHeartbeats, 30, 30, TimeUnit.SECONDS);
    }

    public void setAiRequestCallback(AiRequestCallback callback) {
        this.aiRequestCallback = callback;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String sessionId = session.getId();
        sessions.put(sessionId, session);
        sessionInfo.put(sessionId, new SessionInfo(sessionId, Instant.now()));
        logger.info("Bot WebSocket connection established: {} from {}",
                sessionId, session.getRemoteAddress());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String sessionId = session.getId();
        sessions.remove(sessionId);
        sessionInfo.remove(sessionId);
        logger.info("Bot WebSocket connection closed: {} (status: {})",
                sessionId, status);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        String sessionId = session.getId();

        try {
            JsonNode json = objectMapper.readTree(payload);
            String type = json.has("type") ? json.get("type").asText() : "unknown";

            SessionInfo info = sessionInfo.get(sessionId);
            if (info != null) {
                info.lastActivity = Instant.now();
            }

            switch (type) {
                case "identify":
                    handleIdentify(sessionId, json);
                    break;
                case "ping":
                    handlePing(session);
                    break;
                case "ai_request":
                    handleAiRequest(sessionId, json);
                    break;
                default:
                    logger.debug("Received unknown message type: {}", type);
            }
        } catch (Exception e) {
            logger.error("Error handling WebSocket message: {}", payload, e);
            sendError(session, "parse_error", "Failed to parse message");
        }
    }

    private void handleIdentify(String sessionId, JsonNode json) {
        SessionInfo info = sessionInfo.get(sessionId);
        if (info != null) {
            info.platform = json.has("platform") ? json.get("platform").asText() : "unknown";
            info.version = json.has("version") ? json.get("version").asText() : "unknown";
            logger.info("Session {} identified as: platform={}, version={}",
                    sessionId, info.platform, info.version);
        }
    }

    private void handlePing(WebSocketSession session) throws IOException {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("type", "pong");
        response.put("timestamp", Instant.now().getEpochSecond());
        session.sendMessage(new TextMessage(response.toString()));
    }

    private void handleAiRequest(String sessionId, JsonNode json) {
        if (aiRequestCallback == null) {
            WebSocketSession session = sessions.get(sessionId);
            if (session != null) {
                sendError(session, "ai_unavailable", "AI service not configured");
            }
            return;
        }

        String requestId = json.has("request_id") ? json.get("request_id").asText() : sessionId + "_" + System.currentTimeMillis();
        String userId = json.has("user_id") ? json.get("user_id").asText() : "unknown";
        String channel = json.has("channel") ? json.get("channel").asText() : "unknown";
        String platform = json.has("platform") ? json.get("platform").asText() : "unknown";
        String systemPrompt = json.has("system_prompt") ? json.get("system_prompt").asText() : "";
        String userQuery = json.has("user_query") ? json.get("user_query").asText() : "";

        if (userQuery.isEmpty()) {
            WebSocketSession session = sessions.get(sessionId);
            if (session != null) {
                sendError(session, "empty_query", "User query is empty");
            }
            return;
        }

        logger.info("AI request {} from {} on {}: {}",
                requestId, userId, platform, userQuery.substring(0, Math.min(100, userQuery.length())));

        Consumer<String> onSuccess = response -> sendAiResponse(sessionId, requestId, response, null);
        Consumer<String> onError = error -> sendAiResponse(sessionId, requestId, null, error);

        aiRequestCallback.onAiRequest(requestId, userId, channel, platform,
                systemPrompt, userQuery, onSuccess, onError);
    }

    private void sendAiResponse(String sessionId, String requestId, String response, String error) {
        WebSocketSession session = sessions.get(sessionId);
        if (session == null || !session.isOpen()) {
            logger.warn("Session {} closed, cannot send response", sessionId);
            return;
        }

        try {
            ObjectNode json = objectMapper.createObjectNode();
            json.put("type", "ai_response");
            json.put("request_id", requestId);
            json.put("timestamp", Instant.now().getEpochSecond());

            if (error != null) {
                json.put("status", "error");
                json.put("error", error);
            } else {
                json.put("status", "success");
                json.put("response", response);
            }

            session.sendMessage(new TextMessage(json.toString()));
            logger.debug("Sent AI response to session {} for request {}", sessionId, requestId);
        } catch (IOException e) {
            logger.error("Failed to send AI response to session {}", sessionId, e);
        }
    }

    private void sendError(WebSocketSession session, String code, String message) {
        try {
            ObjectNode error = objectMapper.createObjectNode();
            error.put("type", "error");
            error.put("code", code);
            error.put("message", message);
            error.put("timestamp", Instant.now().getEpochSecond());
            session.sendMessage(new TextMessage(error.toString()));
        } catch (IOException e) {
            logger.error("Failed to send error message", e);
        }
    }

    private void checkHeartbeats() {
        Instant timeoutThreshold = Instant.now().minusSeconds(120);
        for (Map.Entry<String, SessionInfo> entry : sessionInfo.entrySet()) {
            if (entry.getValue().lastActivity.isBefore(timeoutThreshold)) {
                WebSocketSession session = sessions.get(entry.getKey());
                if (session != null && session.isOpen()) {
                    try {
                        session.close(CloseStatus.SESSION_NOT_RELIABLE);
                    } catch (IOException e) {
                        logger.debug("Error closing stale session", e);
                    }
                }
            }
        }
    }

    private static class SessionInfo {
        final String sessionId;
        Instant lastActivity;
        String platform = "unknown";
        String version = "unknown";

        SessionInfo(String sessionId, Instant created) {
            this.sessionId = sessionId;
            this.lastActivity = created;
        }
    }
}
