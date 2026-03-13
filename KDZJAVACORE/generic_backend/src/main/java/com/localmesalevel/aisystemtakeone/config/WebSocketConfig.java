package com.localmesalevel.aisystemtakeone.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.localmesalevel.aisystemtakeone.websocket.BotWebSocketHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket configuration for bot connections.
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final ObjectMapper objectMapper;

    public WebSocketConfig(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Bean
    public BotWebSocketHandler botWebSocketHandler() {
        return new BotWebSocketHandler(objectMapper);
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(botWebSocketHandler(), "/ws/bot")
                .setAllowedOrigins("*");
    }
}
