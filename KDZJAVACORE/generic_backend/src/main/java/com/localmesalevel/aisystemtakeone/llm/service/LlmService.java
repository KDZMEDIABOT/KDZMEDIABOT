package com.localmesalevel.aisystemtakeone.llm.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.localmesalevel.aisystemtakeone.llm.model.LlmEndpointCredentials;

import javax.annotation.PostConstruct;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Service for LLM interactions.
 * Wraps the existing LlmConnection to provide async-friendly interface.
 * Supports MCP tool calling via LlmLoopEngine for bot queries.
 */
@Service
public class LlmService {

    private static final Logger logger = LoggerFactory.getLogger(LlmService.class);

    private final LlmLoopEngine llmLoopEngine;
    private List<LlmLoopEngine.McpServerConfig> defaultBotMcpServers;

    @Value("${ai.temperature:0}")
    private double temperature;

    @Value("${ai.max-tokens:300}")
    private int maxTokens;

    @Value("${bot.mcp.max-steps:4}")
    private int botMcpMaxSteps;

    @Value("${bot.mcp.openserp.server-name:websearch}")
    private String openserpServerName;

    @Value("${bot.mcp.openserp.container-name:${OPENSERP_MCP_CONTAINER_NAME:aisystem-openserp-mcp-sidecar-dev}}")
    private String openserpContainerName;

    @Autowired
    public LlmService(LlmLoopEngine llmLoopEngine) {
        this.llmLoopEngine = llmLoopEngine;
    }

    @PostConstruct
    public void init() {
        // Build default MCP servers list for bot queries
        this.defaultBotMcpServers = buildDefaultBotMcpServers();
        logger.info("LlmService initialized with {} default bot MCP servers", defaultBotMcpServers.size());
    }

    /**
     * Build default MCP servers list for bot queries.
     * Always includes OpenSERP websearch MCP server.
     */
    private List<LlmLoopEngine.McpServerConfig> buildDefaultBotMcpServers() {
        List<LlmLoopEngine.McpServerConfig> servers = new ArrayList<>();

        // Add OpenSERP MCP server for web search
        String serverName = (openserpServerName != null && !openserpServerName.isEmpty())
                ? openserpServerName : "websearch";
        String containerName = (openserpContainerName != null && !openserpContainerName.isEmpty())
                ? openserpContainerName : "aisystem-openserp-mcp-sidecar-dev";

        LlmLoopEngine.McpServerConfig openserpConfig = LlmLoopEngine.McpServerConfig.stdioLocal(
                serverName,
                List.of("docker", "exec", "-i", containerName, "python", "-u", "/opt/openmcp_server.py"),
                LlmLoopEngine.McpServerConfig.StdioMessageFraming.NEWLINE_DELIMITED_JSON
        );

        servers.add(openserpConfig);
        logger.debug("Default bot MCP server configured: {} (container: {})", serverName, containerName);

        return servers;
    }

    /**
     * Get default MCP servers list for bot queries.
     * Always includes OpenSERP websearch.
     */
    public List<LlmLoopEngine.McpServerConfig> getDefaultBotMcpServers() {
        return new ArrayList<>(defaultBotMcpServers);
    }

    /**
     * Process a bot query asynchronously.
     * This method always uses MCP servers if configured, or falls back to direct LLM call.
     *
     * @param llmConnection LLM connection (passed as parameter, not a bean)
     * @param systemPrompt System prompt
     * @param userQuery User's query
     * @return CompletableFuture with the response
     */
    public CompletableFuture<String> processBotQuery(
            LlmConnection llmConnection,
            String systemPrompt,
            Iterator<JsonNode> aiContext) {
        // Use default empty MCP servers list - implementation will fall back to direct LLM call
        return processBotQuery(llmConnection, systemPrompt, aiContext, getDefaultBotMcpServers());
    }

    /**
     * Process a bot query asynchronously with MCP tools support.
     * Uses LlmLoopEngine for multi-step tool calling. Falls back to direct LLM call
     * if MCP servers are empty or MCP execution fails.
     *
     * @param llmConnection LLM connection
     * @param systemPrompt System prompt
     * @param aiContext User's query context
     * @param mcpServers List of MCP server configs for bot queries (kdzBotMcpServersList)
     * @return CompletableFuture with the response
     */
    public CompletableFuture<String> processBotQuery(
            LlmConnection llmConnection,
            String systemPrompt,
            Iterator<JsonNode> aiContext,
            List<LlmLoopEngine.McpServerConfig> mcpServers) {
        return CompletableFuture.supplyAsync(() -> {
            // Convert aiContext iterator to single prompt first
            List<String> aiContextAsList = new LinkedList<String>();
            while(aiContext.hasNext())
                aiContextAsList.add(aiContext.next().asText(""));
            String userPrompt = String.join("\n", aiContextAsList);

            // Try with MCP tools if servers are configured
            if (mcpServers != null && !mcpServers.isEmpty()) {
                try {
                    logger.debug("Calling LLM with MCP tools: system prompt [{} chars], mcpServers={}",
                            systemPrompt.length(), mcpServers.size());

                    String modelName = llmConnection.getModelName();
                    if (modelName == null || modelName.trim().isEmpty()) {
                        throw new RuntimeException("Model name not set in LlmConnection");
                    }

                    LlmLoopEngine.LoopResult loopResult = llmLoopEngine.run(
                            llmConnection.getCredentials(),
                            modelName,
                            systemPrompt,
                            userPrompt,
                            mcpServers,
                            botMcpMaxSteps
                    );

                    return loopResult.getFinalAnswer();

                } catch (Exception e) {
                    logger.error("MCP tools execution failed, falling back to direct LLM call", e);
                }
            }

            // Fall back to direct LLM call
            return executeDirectLlmCall(llmConnection, systemPrompt, userPrompt);
        });
    }

    /**
     * Process a bot query asynchronously with MCP tools support using explicit credentials.
     * Uses LlmLoopEngine for multi-step tool calling. Falls back to direct LLM call
     * if MCP execution fails.
     *
     * @param credentials LLM endpoint credentials
     * @param systemPrompt System prompt
     * @param aiContext User's query context
     * @param mcpServers List of MCP server configs for bot queries (kdzBotMcpServersList)
     * @return CompletableFuture with the response
     */
    public CompletableFuture<String> processBotQuery(
            LlmEndpointCredentials credentials,
            String systemPrompt,
            Iterator<JsonNode> aiContext,
            List<LlmLoopEngine.McpServerConfig> mcpServers) {
        return CompletableFuture.supplyAsync(() -> {
            // Convert aiContext iterator to single prompt
            List<String> aiContextAsList = new LinkedList<String>();
            while(aiContext.hasNext())
                aiContextAsList.add(aiContext.next().asText(""));
            String userPrompt = String.join("\n", aiContextAsList);

            // Try with MCP tools if servers are configured
            if (mcpServers != null && !mcpServers.isEmpty() && credentials != null) {
                try {
                    logger.debug("Calling LLM with MCP tools: system prompt [{} chars], mcpServers={}",
                            systemPrompt.length(), mcpServers.size());

                    String modelName = credentials.getModelName();
                    if (modelName == null || modelName.trim().isEmpty()) {
                        throw new RuntimeException("Model name not set in credentials");
                    }

                    LlmLoopEngine.LoopResult loopResult = llmLoopEngine.run(
                            credentials,
                            modelName,
                            systemPrompt,
                            userPrompt,
                            mcpServers,
                            botMcpMaxSteps
                    );

                    return loopResult.getFinalAnswer();

                } catch (Exception e) {
                    logger.warn("MCP tools execution failed: {}", e.getMessage());
                    // If we have credentials, we could try creating an LlmConnection and doing direct call
                    // But since we don't have connection factory here, just propagate the error
                    throw new RuntimeException("Failed to process with MCP tools: " + e.getMessage(), e);
                }
            }

            throw new IllegalArgumentException(
                "Credentials-based processBotQuery requires non-null credentials and MCP servers, " +
                "or use LlmConnection-based overload for direct LLM calls"
            );
        });
    }

    /**
     * Execute direct LLM call without MCP tools.
     */
    private String executeDirectLlmCall(
            LlmConnection llmConnection,
            String systemPrompt,
            String userPrompt) {
        try {
            logger.debug("Executing direct LLM call: system prompt [{} chars], user prompt [{} chars]",
                    systemPrompt.length(), userPrompt.length());

            return llmConnection.complete(
                    systemPrompt,
                    List.of(userPrompt).iterator(),
                    temperature,
                    maxTokens
            );
        } catch (Exception e) {
            logger.error("Direct LLM call failed", e);
            throw new RuntimeException("Failed to get AI response: " + e.getMessage(), e);
        }
    }

    /**
     * Create synthetic credentials from LlmConnection for MCP tool execution.
     * This is a workaround - ideally LlmConnection should expose its credentials.
     */
    private LlmEndpointCredentials createSyntheticCredentials(LlmConnection llmConnection) {
        // Unfortunately LlmConnection doesn't expose its internal state
        // This method should be removed once LlmConnection stores credentials reference
        // For now, throw exception to trigger fallback
        throw new UnsupportedOperationException(
            "LlmConnection-based MCP execution requires credentials. " +
            "Use credentials-based overload or ensure LlmConnection stores credentials reference."
        );
    }
}
