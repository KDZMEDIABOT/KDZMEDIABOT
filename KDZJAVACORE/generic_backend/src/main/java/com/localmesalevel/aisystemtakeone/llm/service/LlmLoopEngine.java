package com.localmesalevel.aisystemtakeone.llm.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.localmesalevel.aisystemtakeone.llm.model.LlmEndpointCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

@Service
public class LlmLoopEngine {
    private static final Logger logger = LoggerFactory.getLogger(LlmLoopEngine.class);

    private static final String DEFAULT_PROTOCOL_VERSION = "2024-11-05";
    private static final int DEFAULT_MAX_STEPS = 8;
    private static final double DEFAULT_TEMPERATURE = 0.1;
    private static final int DEFAULT_MAX_TOKENS = 2048;

    private final LlmConnectionFactory llmConnectionFactory;
    private final RestTemplateBuilder restTemplateBuilder;
    private final ObjectMapper objectMapper;

    public LlmLoopEngine(
        LlmConnectionFactory llmConnectionFactory,
        RestTemplateBuilder restTemplateBuilder,
        ObjectMapper objectMapper
    ) {
        this.llmConnectionFactory = llmConnectionFactory;
        this.restTemplateBuilder = restTemplateBuilder;
        this.objectMapper = objectMapper;
    }

    public LoopResult run(
        LlmEndpointCredentials llmCredentials,
        String model,
        String systemPrompt,
        String userPrompt,
        List<McpServerConfig> mcpServers
    ) {
        return run(llmCredentials, model, systemPrompt, userPrompt, mcpServers, DEFAULT_MAX_STEPS);
    }

    public LoopResult run(
        LlmEndpointCredentials llmCredentials,
        String model,
        String systemPrompt,
        String userPrompt,
        List<McpServerConfig> mcpServers,
        List<InMemoryMcpTool> inMemoryTools
    ) {
        return run(llmCredentials, model, systemPrompt, userPrompt, mcpServers, DEFAULT_MAX_STEPS, inMemoryTools);
    }

    public LoopResult run(
        LlmEndpointCredentials llmCredentials,
        String model,
        String systemPrompt,
        String userPrompt,
        List<McpServerConfig> mcpServers,
        int maxSteps
    ) {
        return run(llmCredentials, model, systemPrompt, userPrompt, mcpServers, maxSteps, null);
    }

    public LoopResult run(
        LlmEndpointCredentials llmCredentials,
        String model,
        String systemPrompt,
        String userPrompt,
        List<McpServerConfig> mcpServers,
        int maxSteps,
        List<InMemoryMcpTool> inMemoryTools
    ) {
        if (llmCredentials == null) {
            throw new IllegalArgumentException("llmCredentials is required");
        }
        if (isBlank(model)) {
            throw new IllegalArgumentException("model is required");
        }
        if (isBlank(userPrompt)) {
            throw new IllegalArgumentException("userPrompt is required");
        }
        if (mcpServers == null || mcpServers.isEmpty()) {
            throw new IllegalArgumentException("At least one MCP server is required");
        }

        int effectiveMaxSteps = Math.max(1, maxSteps);
        logger.trace(
            "LlmLoopEngine.run start: model='{}', maxSteps={}, mcpServers={}, userPromptChars={}, systemPromptChars={}",
            model == null ? null : model.trim(),
            effectiveMaxSteps,
            mcpServers.size(),
            userPrompt.length(),
            systemPrompt == null ? 0 : systemPrompt.length()
        );
        LlmConnection llmConnection = llmConnectionFactory.create(llmCredentials);

        ToolRegistry toolRegistry = buildToolRegistry(mcpServers, inMemoryTools);
        try {
            if (toolRegistry.toolsByQualifiedName.isEmpty()) {
                throw new IllegalStateException("No MCP tools found across configured servers");
            }
            logger.trace(
                "LlmLoopEngine.run registry ready: servers={}, tools={}",
                toolRegistry.clientsByServer.size(),
                toolRegistry.toolsByQualifiedName.keySet()
            );

            List<LoopStep> executedSteps = new ArrayList<>();
            StringBuilder conversationState = new StringBuilder();

            for (int stepIndex = 1; stepIndex <= effectiveMaxSteps; stepIndex++) {
                String loopPrompt = buildLoopPrompt(userPrompt, toolRegistry, conversationState.toString());
                logger.trace(
                    "LlmLoopEngine step {} start: loopPromptChars={}, conversationStateChars={}",
                    stepIndex,
                    loopPrompt.length(),
                    conversationState.length()
                );
                final Iterator<String> aiContext = List.of(loopPrompt).iterator();
                String llmRawResponse = llmConnection.complete(
                    systemPrompt,
                    aiContext,
                    DEFAULT_TEMPERATURE,
                    DEFAULT_MAX_TOKENS
                );
                logger.trace(
                    "LlmLoopEngine step {} LLM response chars={}, preview='{}'",
                    stepIndex,
                    llmRawResponse == null ? 0 : llmRawResponse.length(),
                    preview(llmRawResponse, 400)
                );

                LoopInstruction instruction = parseInstruction(llmRawResponse);
                logger.trace(
                    "LlmLoopEngine step {} parsed instruction: action='{}', tool='{}', argumentsChars={}, finalChars={}",
                    stepIndex,
                    instruction.action,
                    instruction.tool,
                    instruction.arguments == null ? 0 : instruction.arguments.toString().length(),
                    instruction.finalAnswer == null ? 0 : instruction.finalAnswer.length()
                );
                if ("final".equalsIgnoreCase(instruction.action)) {
                    if (isBlank(instruction.finalAnswer)) {
                        throw new IllegalStateException("LLM returned final action without final answer");
                    }
                    logger.trace(
                        "LlmLoopEngine finished at step {} with final answer chars={}",
                        stepIndex,
                        instruction.finalAnswer.trim().length()
                    );
                    return new LoopResult(instruction.finalAnswer.trim(), executedSteps);
                }


        String action = instruction.action == null ? "" : instruction.action.trim().toLowerCase();
        if (!action.equals("tool_call") && !action.equals("tool") && !action.equals("call_tool")) {
            throw new IllegalStateException("Unsupported action from LLM: " + instruction.action);
        }
        if (isBlank(instruction.tool)) {
            throw new IllegalStateException("LLM returned tool_call action without tool name");
        }

                ToolRoute route = resolveTool(toolRegistry, instruction.tool.trim());
                JsonNode toolArguments = instruction.arguments == null ? objectMapper.createObjectNode() : instruction.arguments;
                logger.trace(
                    "LlmLoopEngine step {} calling tool='{}' args='{}'",
                    stepIndex,
                    route.tool.qualifiedName,
                    preview(toJson(toolArguments), 800)
                );
                JsonNode toolResult;
                if ("local".equals(route.tool.serverName) && route.client == null) {
                    InMemoryMcpTool inMemoryTool = findInMemoryTool(toolRegistry, route.tool.name);
                    toolResult = inMemoryTool.execute(toolArguments);
                } else {
                    toolResult = route.client.callTool(route.tool.name, toolArguments);
                }
                logger.trace(
                    "LlmLoopEngine step {} tool='{}' completed resultChars={}",
                    stepIndex,
                    route.tool.qualifiedName,
                    toolResult == null ? 0 : toolResult.toString().length()
                );

                String resultAsText = toJson(toolResult);
                executedSteps.add(new LoopStep(stepIndex, route.tool.qualifiedName, toolArguments, toolResult, llmRawResponse));
                conversationState.append("Step ").append(stepIndex).append('\n');
                conversationState.append("Tool called: ").append(route.tool.qualifiedName).append('\n');
                conversationState.append("Tool arguments: ").append(toJson(toolArguments)).append('\n');
                conversationState.append("Tool result: ").append(resultAsText).append("\n\n");
            }

            logger.trace("LlmLoopEngine reached max steps={} without final answer", effectiveMaxSteps);
            throw new IllegalStateException("LLM loop reached max steps (" + effectiveMaxSteps + ") without final answer");
        } finally {
            logger.trace("LlmLoopEngine closing MCP clients");
            toolRegistry.closeAll();
        }
    }

    private ToolRegistry buildToolRegistry(List<McpServerConfig> serverConfigs) {
        return buildToolRegistry(serverConfigs, null);
    }

    private ToolRegistry buildToolRegistry(List<McpServerConfig> serverConfigs, List<InMemoryMcpTool> inMemoryTools) {
        Map<String, McpClient> clientsByServer = new LinkedHashMap<>();
        Map<String, RegisteredTool> toolsByQualifiedName = new LinkedHashMap<>();
        Map<String, List<String>> toolQualifiedNamesBySimpleName = new LinkedHashMap<>();

        for (McpServerConfig config : serverConfigs) {
            validateServerConfig(config);
            logger.trace(
                "Initializing MCP server '{}' transport={} command={} url={}",
                config.serverName,
                config.transportType,
                config.stdioCommand,
                config.mcpUrl
            );
            McpClient client = createMcpClient(config);
            client.initialize();
            List<McpToolDescriptor> tools = client.listTools();
            logger.trace("MCP server '{}' listed {} tools", config.serverName, tools.size());

            for (McpToolDescriptor tool : tools) {
                String qualifiedName = config.serverName + "/" + tool.name;
                RegisteredTool registeredTool = new RegisteredTool(qualifiedName, config.serverName, tool.name, tool.description, tool.inputSchema);
                toolsByQualifiedName.put(qualifiedName, registeredTool);
                toolQualifiedNamesBySimpleName.computeIfAbsent(tool.name, key -> new ArrayList<>()).add(qualifiedName);
                logger.trace("Registered MCP tool '{}'", qualifiedName);
            }
            clientsByServer.put(config.serverName, client);
        }

        if (inMemoryTools != null) {
            for (InMemoryMcpTool tool : inMemoryTools) {
                String qualifiedName = "local/" + tool.getName();
                RegisteredTool registeredTool = new RegisteredTool(qualifiedName, "local", tool.getName(), tool.getDescription(), tool.getInputSchema());
                toolsByQualifiedName.put(qualifiedName, registeredTool);
                toolQualifiedNamesBySimpleName.computeIfAbsent(tool.getName(), key -> new ArrayList<>()).add(qualifiedName);
            }
        }

        return new ToolRegistry(clientsByServer, toolsByQualifiedName, toolQualifiedNamesBySimpleName, inMemoryTools);
    }

    private McpClient createMcpClient(McpServerConfig config) {
        if (config.transportType == McpTransportType.STDIO_LOCAL) {
            logger.trace("Creating stdio MCP client for '{}'", config.serverName);
            return new McpStdioClient(config, objectMapper);
        }
        logger.trace("Creating HTTP MCP client for '{}'", config.serverName);
        return new McpHttpClient(config, restTemplateBuilder, objectMapper);
    }

    private void validateServerConfig(McpServerConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("MCP server config cannot be null");
        }
        if (isBlank(config.serverName)) {
            throw new IllegalArgumentException("MCP serverName is required");
        }
        if (config.transportType == McpTransportType.STDIO_LOCAL) {
            if (config.stdioCommand == null || config.stdioCommand.isEmpty()) {
                throw new IllegalArgumentException("MCP stdioCommand is required for stdio server " + config.serverName);
            }
            return;
        }
        if (isBlank(config.mcpUrl)) {
            throw new IllegalArgumentException("MCP mcpUrl is required for HTTP server " + config.serverName);
        }
    }

    private String buildLoopPrompt(String userPrompt, ToolRegistry toolRegistry, String state) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are an assistant that can use MCP tools.\n");
        sb.append("User request:\n").append(userPrompt).append("\n\n");
        sb.append("Available tools (use exact qualified names):\n");

        for (RegisteredTool tool : toolRegistry.toolsByQualifiedName.values()) {
            sb.append("- ").append(tool.qualifiedName);
            if (!isBlank(tool.description)) {
                sb.append(": ").append(tool.description.trim());
            }
            sb.append('\n');
            if (tool.inputSchema != null && !tool.inputSchema.isNull()) {
                sb.append("  input_schema=").append(toJson(tool.inputSchema)).append('\n');
            }
        }

        if (!isBlank(state)) {
            sb.append("\nCurrent tool-call state:\n").append(state).append('\n');
        }

        sb.append("\n=== RESPONSE INSTRUCTIONS ===\n");
        sb.append("You MUST respond with EXACTLY ONE JSON object.\n");
        sb.append("NO thinking, NO explanation, NO markdown, ONLY JSON.\n");
        sb.append("\nTwo valid response shapes:\n");
        sb.append("1) {\"action\":\"tool_call\",\"tool\":\"serverName/toolName\",\"arguments\":{...}}\n");
        sb.append("2) {\"action\":\"final\",\"final\":\"final answer text\"}\n");
        sb.append("\nSTART WITH { AND END WITH } NOTHING BEFORE OR AFTER.\n");
        return sb.toString();
    }

    private ToolRoute resolveTool(ToolRegistry registry, String requestedTool) {
        RegisteredTool qualified = registry.toolsByQualifiedName.get(requestedTool);
        if (qualified != null) {
            logger.trace("Resolved tool by qualified name '{}'", requestedTool);
            McpClient client = "local".equals(qualified.serverName) ? null : registry.clientsByServer.get(qualified.serverName);
            return new ToolRoute(qualified, client);
        }

        List<String> qualifiedNames = registry.toolQualifiedNamesBySimpleName.getOrDefault(requestedTool, List.of());
        if (qualifiedNames.size() == 1) {
            String resolvedQualifiedName = qualifiedNames.get(0);
            RegisteredTool resolved = registry.toolsByQualifiedName.get(resolvedQualifiedName);
            logger.trace("Resolved tool '{}' to '{}'", requestedTool, resolvedQualifiedName);
            McpClient client = "local".equals(resolved.serverName) ? null : registry.clientsByServer.get(resolved.serverName);
            return new ToolRoute(resolved, client);
        }
        if (qualifiedNames.size() > 1) {
            throw new IllegalStateException(
                "Ambiguous tool name '" + requestedTool + "'. Use a qualified name. Candidates: " + String.join(", ", qualifiedNames)
            );
        }
        throw new IllegalStateException("Unknown MCP tool requested by LLM: " + requestedTool);
    }

    private LoopInstruction parseInstruction(String rawResponse) {
        if (isBlank(rawResponse)) {
            throw new IllegalStateException("LLM returned empty response");
        }
        String normalized = stripMarkdownJsonFence(rawResponse);
        logger.trace("Parsing LLM instruction JSON chars={}, preview='{}'", normalized.length(), preview(normalized, 500));
        try {
            JsonNode root = objectMapper.readTree(normalized);
            String action = root.path("action").asText(null);
            if (isBlank(action)) {
                throw new IllegalStateException("LLM response did not include action");
            }

            // Handle case where LLM puts qualified tool name in action field
            // e.g., "websearch/webpage_fetch" instead of "tool_call"
            String tool = root.path("tool").asText(null);
            if (action.contains("/") && tool == null) {
                tool = action;
                action = "tool_call";
            }

            JsonNode args = root.path("arguments");
            if (args.isMissingNode() || args.isNull()) {
                args = objectMapper.createObjectNode();
            }
            String finalAnswer = root.path("final").asText(null);
            return new LoopInstruction(action, tool, args, finalAnswer);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("LLM response is not valid JSON: " + normalized, e);
        }
    }

    private String stripMarkdownJsonFence(String raw) {
        String trimmed = raw.trim();

        // First, try to extract JSON from within text by finding outermost braces
        int firstBrace = trimmed.indexOf('{');
        int lastBrace = trimmed.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            String jsonCandidate = trimmed.substring(firstBrace, lastBrace + 1);
            // Validate it looks like JSON by checking for action field
            if (jsonCandidate.contains("\"action\"") || jsonCandidate.contains("'action'")) {
                return jsonCandidate;
            }
        }

        // Fall back to markdown fence stripping
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        int firstLineEnd = trimmed.indexOf('\n');
        if (firstLineEnd < 0) {
            return trimmed;
        }
        String firstLine = trimmed.substring(0, firstLineEnd).toLowerCase(Locale.ROOT);
        if (!firstLine.startsWith("```json") && !firstLine.startsWith("```")) {
            return trimmed;
        }
        int closingFence = trimmed.lastIndexOf("```");
        if (closingFence <= firstLineEnd) {
            return trimmed;
        }
        return trimmed.substring(firstLineEnd + 1, closingFence).trim();
    }

    private InMemoryMcpTool findInMemoryTool(ToolRegistry registry, String name) {
        if (registry.inMemoryTools == null) {
            throw new IllegalStateException("In-memory tool not found: " + name);
        }
        for (InMemoryMcpTool tool : registry.inMemoryTools) {
            if (tool.getName().equals(name)) {
                return tool;
            }
        }
        throw new IllegalStateException("In-memory tool not found: " + name);
    }

    private String toJson(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize JSON node", e);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String preview(String value, int maxChars) {
        if (value == null) {
            return "null";
        }
        String normalized = value.replace('\n', ' ').replace('\r', ' ');
        if (normalized.length() <= maxChars) {
            return normalized;
        }
        return normalized.substring(0, Math.max(0, maxChars)) + "...(truncated)";
    }

    private static final class ToolRegistry {
        private final Map<String, McpClient> clientsByServer;
        private final Map<String, RegisteredTool> toolsByQualifiedName;
        private final Map<String, List<String>> toolQualifiedNamesBySimpleName;
        private final List<InMemoryMcpTool> inMemoryTools;

        private ToolRegistry(
            Map<String, McpClient> clientsByServer,
            Map<String, RegisteredTool> toolsByQualifiedName,
            Map<String, List<String>> toolQualifiedNamesBySimpleName,
            List<InMemoryMcpTool> inMemoryTools
        ) {
            this.clientsByServer = clientsByServer;
            this.toolsByQualifiedName = toolsByQualifiedName;
            this.toolQualifiedNamesBySimpleName = toolQualifiedNamesBySimpleName;
            this.inMemoryTools = inMemoryTools != null ? inMemoryTools : java.util.Collections.emptyList();
        }

        private void closeAll() {
            for (McpClient client : clientsByServer.values()) {
                try {
                    client.close();
                } catch (Exception ignored) {
                    // Best-effort client shutdown.
                }
            }
        }
    }

    private static final class ToolRoute {
        private final RegisteredTool tool;
        private final McpClient client;

        private ToolRoute(RegisteredTool tool, McpClient client) {
            this.tool = tool;
            this.client = client;
        }
    }

    private static final class RegisteredTool {
        private final String qualifiedName;
        private final String serverName;
        private final String name;
        private final String description;
        private final JsonNode inputSchema;

        private RegisteredTool(String qualifiedName, String serverName, String name, String description, JsonNode inputSchema) {
            this.qualifiedName = qualifiedName;
            this.serverName = serverName;
            this.name = name;
            this.description = description;
            this.inputSchema = inputSchema;
        }
    }

    private static final class LoopInstruction {
        private final String action;
        private final String tool;
        private final JsonNode arguments;
        private final String finalAnswer;

        private LoopInstruction(String action, String tool, JsonNode arguments, String finalAnswer) {
            this.action = action;
            this.tool = tool;
            this.arguments = arguments;
            this.finalAnswer = finalAnswer;
        }
    }

    public static abstract class InMemoryMcpTool {
        private final String name;
        private final String description;
        private final JsonNode inputSchema;

        public InMemoryMcpTool(String name, String description, JsonNode inputSchema) {
            this.name = name;
            this.description = description;
            this.inputSchema = inputSchema;
        }

        public String getName() { return name; }
        public String getDescription() { return description; }
        public JsonNode getInputSchema() { return inputSchema; }

        public abstract JsonNode execute(JsonNode arguments);
    }

    public enum McpTransportType {
        HTTP_JSONRPC,
        STDIO_LOCAL
    }

    public static final class McpServerConfig {
        public enum StdioMessageFraming {
            CONTENT_LENGTH,
            NEWLINE_DELIMITED_JSON
        }

        private final String serverName;
        private final McpTransportType transportType;
        private final String mcpUrl;
        private final List<String> stdioCommand;
        private final String stdioWorkingDirectory;
        private final Map<String, String> stdioEnv;
        private final StdioMessageFraming stdioMessageFraming;
        private final String bearerToken;
        private final Map<String, String> headers;
        private final Duration connectTimeout;
        private final Duration readTimeout;

        public McpServerConfig(String serverName, String mcpUrl) {
            this(
                serverName,
                McpTransportType.HTTP_JSONRPC,
                mcpUrl,
                List.of(),
                null,
                Map.of(),
                StdioMessageFraming.CONTENT_LENGTH,
                null,
                Map.of(),
                Duration.ofSeconds(15),
                Duration.ofSeconds(60)
            );
        }

        public static McpServerConfig stdioLocal(String serverName, List<String> stdioCommand) {
            return new McpServerConfig(
                serverName,
                McpTransportType.STDIO_LOCAL,
                null,
                stdioCommand,
                null,
                Map.of(),
                StdioMessageFraming.CONTENT_LENGTH,
                null,
                Map.of(),
                Duration.ofSeconds(15),
                Duration.ofSeconds(60)
            );
        }

        public static McpServerConfig stdioLocal(
            String serverName,
            List<String> stdioCommand,
            StdioMessageFraming stdioMessageFraming
        ) {
            return new McpServerConfig(
                serverName,
                McpTransportType.STDIO_LOCAL,
                null,
                stdioCommand,
                null,
                Map.of(),
                stdioMessageFraming,
                null,
                Map.of(),
                Duration.ofSeconds(15),
                Duration.ofSeconds(60)
            );
        }

        public McpServerConfig(
            String serverName,
            String mcpUrl,
            String bearerToken,
            Map<String, String> headers,
            Duration connectTimeout,
            Duration readTimeout
        ) {
            this(
                serverName,
                McpTransportType.HTTP_JSONRPC,
                mcpUrl,
                List.of(),
                null,
                Map.of(),
                StdioMessageFraming.CONTENT_LENGTH,
                bearerToken,
                headers,
                connectTimeout,
                readTimeout
            );
        }

        public McpServerConfig(
            String serverName,
            List<String> stdioCommand,
            String stdioWorkingDirectory,
            Map<String, String> stdioEnv
        ) {
            this(
                serverName,
                McpTransportType.STDIO_LOCAL,
                null,
                stdioCommand,
                stdioWorkingDirectory,
                stdioEnv,
                StdioMessageFraming.CONTENT_LENGTH,
                null,
                Map.of(),
                Duration.ofSeconds(15),
                Duration.ofSeconds(60)
            );
        }

        private McpServerConfig(
            String serverName,
            McpTransportType transportType,
            String mcpUrl,
            List<String> stdioCommand,
            String stdioWorkingDirectory,
            Map<String, String> stdioEnv,
            StdioMessageFraming stdioMessageFraming,
            String bearerToken,
            Map<String, String> headers,
            Duration connectTimeout,
            Duration readTimeout
        ) {
            this.serverName = serverName;
            this.transportType = transportType == null ? McpTransportType.HTTP_JSONRPC : transportType;
            this.mcpUrl = mcpUrl;
            this.stdioCommand = stdioCommand == null ? List.of() : List.copyOf(stdioCommand);
            this.stdioWorkingDirectory = stdioWorkingDirectory;
            this.stdioEnv = stdioEnv == null ? Map.of() : new LinkedHashMap<>(stdioEnv);
            this.stdioMessageFraming = stdioMessageFraming == null
                ? StdioMessageFraming.CONTENT_LENGTH
                : stdioMessageFraming;
            this.bearerToken = bearerToken;
            this.headers = headers == null ? Map.of() : new LinkedHashMap<>(headers);
            this.connectTimeout = connectTimeout == null ? Duration.ofSeconds(15) : connectTimeout;
            this.readTimeout = readTimeout == null ? Duration.ofSeconds(60) : readTimeout;
        }
    }

    public static final class LoopResult {
        private final String finalAnswer;
        private final List<LoopStep> steps;

        public LoopResult(String finalAnswer, List<LoopStep> steps) {
            this.finalAnswer = finalAnswer;
            this.steps = steps == null ? List.of() : List.copyOf(steps);
        }

        public String getFinalAnswer() {
            return finalAnswer;
        }

        public List<LoopStep> getSteps() {
            return steps;
        }
    }

    public static final class LoopStep {
        private final int step;
        private final String toolQualifiedName;
        private final JsonNode toolArguments;
        private final JsonNode toolResult;
        private final String llmRawResponse;

        public LoopStep(int step, String toolQualifiedName, JsonNode toolArguments, JsonNode toolResult, String llmRawResponse) {
            this.step = step;
            this.toolQualifiedName = toolQualifiedName;
            this.toolArguments = toolArguments;
            this.toolResult = toolResult;
            this.llmRawResponse = llmRawResponse;
        }

        public int getStep() {
            return step;
        }

        public String getToolQualifiedName() {
            return toolQualifiedName;
        }

        public JsonNode getToolArguments() {
            return toolArguments;
        }

        public JsonNode getToolResult() {
            return toolResult;
        }

        public String getLlmRawResponse() {
            return llmRawResponse;
        }
    }

    public static final class McpToolDescriptor {
        private final String name;
        private final String description;
        private final JsonNode inputSchema;

        public McpToolDescriptor(String name, String description, JsonNode inputSchema) {
            this.name = name;
            this.description = description;
            this.inputSchema = inputSchema;
        }
    }

    private interface McpClient extends AutoCloseable {
        void initialize();

        List<McpToolDescriptor> listTools();

        JsonNode callTool(String toolName, JsonNode arguments);

        @Override
        default void close() {
            // No-op for clients without lifecycle handles.
        }
    }

    private static final class McpHttpClient implements McpClient {
        private static final String USER_AGENT = "aisystem-customer1/mcp-client";

        private final McpServerConfig config;
        private final RestTemplate restTemplate;
        private final ObjectMapper objectMapper;
        private long idSeq = 1;

        private McpHttpClient(McpServerConfig config, RestTemplateBuilder restTemplateBuilder, ObjectMapper objectMapper) {
            this.config = config;
            this.objectMapper = objectMapper;
            this.restTemplate = restTemplateBuilder
                .setConnectTimeout(config.connectTimeout)
                .setReadTimeout(config.readTimeout)
                .build();
        }

        @Override
        public void initialize() {
            logger.trace("McpHttpClient.initialize server='{}'", config.serverName);
            ObjectNode params = objectMapper.createObjectNode();
            params.put("protocolVersion", DEFAULT_PROTOCOL_VERSION);
            ObjectNode capabilities = params.putObject("capabilities");
            capabilities.putObject("tools");
            ObjectNode clientInfo = params.putObject("clientInfo");
            clientInfo.put("name", "aisystem-customer1-llm-loop-engine");
            clientInfo.put("version", "0.1.0");

            sendRequest("initialize", params);
            sendNotification("notifications/initialized", objectMapper.createObjectNode());
        }

        @Override
        public List<McpToolDescriptor> listTools() {
            logger.trace("McpHttpClient.listTools server='{}'", config.serverName);
            JsonNode result = sendRequest("tools/list", objectMapper.createObjectNode());
            JsonNode toolsNode = result.path("tools");
            if (!toolsNode.isArray()) {
                throw new IllegalStateException("MCP tools/list result did not contain tools array");
            }

            List<McpToolDescriptor> tools = new ArrayList<>();
            for (JsonNode toolNode : toolsNode) {
                String name = toolNode.path("name").asText(null);
                if (name == null || name.trim().isEmpty()) {
                    continue;
                }
                String description = toolNode.path("description").asText(null);
                JsonNode inputSchema = toolNode.path("inputSchema");
                if (inputSchema.isMissingNode()) {
                    inputSchema = null;
                }
                tools.add(new McpToolDescriptor(name, description, inputSchema));
            }
            return tools;
        }

        @Override
        public JsonNode callTool(String toolName, JsonNode arguments) {
            logger.trace(
                "McpHttpClient.callTool server='{}' tool='{}' argsChars={}",
                config.serverName,
                toolName,
                arguments == null ? 0 : arguments.toString().length()
            );
            ObjectNode params = objectMapper.createObjectNode();
            params.put("name", toolName);
            if (arguments == null || arguments.isNull() || arguments.isMissingNode()) {
                params.set("arguments", objectMapper.createObjectNode());
            } else if (arguments.isObject()) {
                params.set("arguments", arguments);
            } else {
                throw new IllegalArgumentException("Tool arguments must be a JSON object");
            }
            return sendRequest("tools/call", params);
        }

        private JsonNode sendRequest(String method, JsonNode params) {
            logger.trace("McpHttpClient.sendRequest server='{}' method='{}'", config.serverName, method);
            ObjectNode body = objectMapper.createObjectNode();
            body.put("jsonrpc", "2.0");
            body.put("id", idSeq++);
            body.put("method", method);
            body.set("params", params == null ? objectMapper.createObjectNode() : params);

            JsonNode root = execute(body);
            if (root.path("error").isObject()) {
                throw new IllegalStateException(
                    "MCP server '" + config.serverName + "' error on method '" + method + "': " + root.path("error").toString()
                );
            }
            JsonNode result = root.path("result");
            if (result.isMissingNode()) {
                throw new IllegalStateException(
                    "MCP server '" + config.serverName + "' response for method '" + method + "' did not contain result"
                );
            }
            return result;
        }

        private void sendNotification(String method, JsonNode params) {
            logger.trace("McpHttpClient.sendNotification server='{}' method='{}'", config.serverName, method);
            ObjectNode body = objectMapper.createObjectNode();
            body.put("jsonrpc", "2.0");
            body.put("method", method);
            body.set("params", params == null ? objectMapper.createObjectNode() : params);
            execute(body);
        }

        private JsonNode execute(ObjectNode body) {
            logger.trace(
                "McpHttpClient.execute server='{}' url='{}' method='{}' bodyChars={}",
                config.serverName,
                config.mcpUrl,
                body.path("method").asText(null),
                body.toString().length()
            );
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            headers.set(HttpHeaders.USER_AGENT, USER_AGENT);
            if (config.bearerToken != null && !config.bearerToken.trim().isEmpty()) {
                headers.setBearerAuth(config.bearerToken.trim());
            }
            for (Map.Entry<String, String> entry : config.headers.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    headers.set(entry.getKey(), entry.getValue());
                }
            }

            try {
                ResponseEntity<String> response = restTemplate.exchange(
                    config.mcpUrl,
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    String.class
                );
                if (!response.getStatusCode().is2xxSuccessful()) {
                    throw new IllegalStateException("Non-success status from MCP server " + config.serverName + ": " + response.getStatusCode());
                }
                String responseBody = response.getBody();
                logger.trace(
                    "McpHttpClient.execute response server='{}' status={} bodyChars={} bodyPreview='{}'",
                    config.serverName,
                    response.getStatusCodeValue(),
                    responseBody == null ? 0 : responseBody.length(),
                    preview(responseBody, 500)
                );
                if (responseBody == null || responseBody.trim().isEmpty()) {
                    return objectMapper.createObjectNode();
                }
                return objectMapper.readTree(responseBody);
            } catch (RestClientException e) {
                throw new IllegalStateException("Failed to call MCP server " + config.serverName + ": " + e.getMessage(), e);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Invalid JSON response from MCP server " + config.serverName, e);
            }
        }
    }

    private static final class McpStdioClient implements McpClient {
        private final McpServerConfig config;
        private final ObjectMapper objectMapper;
        private final Process process;
        private final BufferedReader processStdout;
        private final BufferedOutputStream processStdin;
        private long idSeq = 1;

        private McpStdioClient(McpServerConfig config, ObjectMapper objectMapper) {
            this.config = config;
            this.objectMapper = objectMapper;
            this.process = startProcess(config);
            this.processStdout = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
            this.processStdin = new BufferedOutputStream(process.getOutputStream());
            logger.trace("McpStdioClient started server='{}' pid={}", config.serverName, process.pid());
        }

        @Override
        public void initialize() {
            logger.trace("McpStdioClient.initialize server='{}'", config.serverName);
            ObjectNode params = objectMapper.createObjectNode();
            params.put("protocolVersion", DEFAULT_PROTOCOL_VERSION);
            ObjectNode capabilities = params.putObject("capabilities");
            capabilities.putObject("tools");
            ObjectNode clientInfo = params.putObject("clientInfo");
            clientInfo.put("name", "aisystem-customer1-llm-loop-engine");
            clientInfo.put("version", "0.1.0");

            sendRequest("initialize", params);
            sendNotification("notifications/initialized", objectMapper.createObjectNode());
        }

        @Override
        public List<McpToolDescriptor> listTools() {
            logger.trace("McpStdioClient.listTools server='{}'", config.serverName);
            JsonNode result = sendRequest("tools/list", objectMapper.createObjectNode());
            JsonNode toolsNode = result.path("tools");
            if (!toolsNode.isArray()) {
                throw new IllegalStateException("MCP tools/list result did not contain tools array");
            }

            List<McpToolDescriptor> tools = new ArrayList<>();
            for (JsonNode toolNode : toolsNode) {
                String name = toolNode.path("name").asText(null);
                if (name == null || name.trim().isEmpty()) {
                    continue;
                }
                String description = toolNode.path("description").asText(null);
                JsonNode inputSchema = toolNode.path("inputSchema");
                if (inputSchema.isMissingNode()) {
                    inputSchema = null;
                }
                tools.add(new McpToolDescriptor(name, description, inputSchema));
            }
            return tools;
        }

        @Override
        public JsonNode callTool(String toolName, JsonNode arguments) {
            logger.trace(
                "McpStdioClient.callTool server='{}' tool='{}' argsChars={}",
                config.serverName,
                toolName,
                arguments == null ? 0 : arguments.toString().length()
            );
            ObjectNode params = objectMapper.createObjectNode();
            params.put("name", toolName);
            if (arguments == null || arguments.isNull() || arguments.isMissingNode()) {
                params.set("arguments", objectMapper.createObjectNode());
            } else if (arguments.isObject()) {
                params.set("arguments", arguments);
            } else {
                throw new IllegalArgumentException("Tool arguments must be a JSON object");
            }
            return sendRequest("tools/call", params);
        }

        @Override
        public void close() {
            logger.trace("McpStdioClient.close server='{}' pid={}", config.serverName, process.pid());
            try {
                processStdin.close();
            } catch (IOException ignored) {
            }
            try {
                processStdout.close();
            } catch (IOException ignored) {
            }
            process.destroy();
            try {
                if (!process.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
        }

        private JsonNode sendRequest(String method, JsonNode params) {
            long requestId = idSeq++;
            logger.trace("McpStdioClient.sendRequest server='{}' method='{}' id={}", config.serverName, method, requestId);

            ObjectNode body = objectMapper.createObjectNode();
            body.put("jsonrpc", "2.0");
            body.put("id", requestId);
            body.put("method", method);
            body.set("params", params == null ? objectMapper.createObjectNode() : params);
            sendMessage(body);

            while (true) {
                JsonNode root = readMessage();
                JsonNode responseId = root.path("id");
                if (responseId.isMissingNode() || responseId.isNull()) {
                    continue;
                }
                if (!idMatches(responseId, requestId)) {
                    logger.trace(
                        "McpStdioClient skipping unmatched response server='{}' expectedId={} actualId={}",
                        config.serverName,
                        requestId,
                        responseId
                    );
                    continue;
                }
                if (root.path("error").isObject()) {
                    throw new IllegalStateException(
                        "MCP stdio server '" + config.serverName + "' error on method '" + method + "': " + root.path("error").toString()
                    );
                }
                JsonNode result = root.path("result");
                if (result.isMissingNode()) {
                    throw new IllegalStateException(
                        "MCP stdio server '" + config.serverName + "' response for method '" + method + "' did not contain result"
                    );
                }
                return result;
            }
        }

        private void sendNotification(String method, JsonNode params) {
            logger.trace("McpStdioClient.sendNotification server='{}' method='{}'", config.serverName, method);
            ObjectNode body = objectMapper.createObjectNode();
            body.put("jsonrpc", "2.0");
            body.put("method", method);
            body.set("params", params == null ? objectMapper.createObjectNode() : params);
            sendMessage(body);
        }

        private void sendMessage(JsonNode message) {
            try {
                byte[] payload = objectMapper.writeValueAsBytes(message);
                logger.trace(
                    "McpStdioClient.sendMessage server='{}' method='{}' bytes={}",
                    config.serverName,
                    message.path("method").asText(null),
                    payload.length
                );
                if (config.stdioMessageFraming == McpServerConfig.StdioMessageFraming.CONTENT_LENGTH) {
                    String header = "Content-Length: " + payload.length + "\r\n\r\n";
                    processStdin.write(header.getBytes(StandardCharsets.US_ASCII));
                    processStdin.write(payload);
                } else {
                    processStdin.write(payload);
                    processStdin.write('\n');
                }
                processStdin.flush();
            } catch (IOException e) {
                throw new IllegalStateException("Failed to write MCP stdio request for server " + config.serverName, e);
            }
        }

        private JsonNode readMessage() {
            try {
                if (config.stdioMessageFraming == McpServerConfig.StdioMessageFraming.CONTENT_LENGTH) {
                    Map<String, String> headers = readHeaders(process.getInputStream());
                    String contentLengthRaw = headers.get("content-length");
                    if (contentLengthRaw == null) {
                        throw new IllegalStateException("MCP stdio response missing Content-Length header for server " + config.serverName);
                    }
                    int contentLength = Integer.parseInt(contentLengthRaw.trim());
                    byte[] payload = readExactly(process.getInputStream(), contentLength);
                    logger.trace(
                        "McpStdioClient.readMessage server='{}' payloadBytes={} framing={} headers={}",
                        config.serverName,
                        contentLength,
                        config.stdioMessageFraming,
                        headers
                    );
                    return objectMapper.readTree(payload);
                }
                while (true) {
                    String line = processStdout.readLine();
                    if (line == null) {
                        throw new EOFException("MCP stdio stream closed while reading response for server " + config.serverName);
                    }
                    String trimmed = line.trim();
                    if (trimmed.isEmpty()) {
                        continue;
                    }
                    logger.trace(
                        "McpStdioClient.readMessage server='{}' lineChars={} framing={} preview='{}'",
                        config.serverName,
                        trimmed.length(),
                        config.stdioMessageFraming,
                        preview(trimmed, 800)
                    );
                    return objectMapper.readTree(trimmed);
                }
            } catch (IOException e) {
                throw new IllegalStateException("Failed to read MCP stdio response for server " + config.serverName, e);
            }
        }

        private boolean idMatches(JsonNode responseId, long requestId) {
            if (responseId.isIntegralNumber()) {
                return responseId.asLong() == requestId;
            }
            if (responseId.isTextual()) {
                try {
                    return Long.parseLong(responseId.asText()) == requestId;
                } catch (NumberFormatException ignored) {
                    return false;
                }
            }
            return false;
        }

        private Process startProcess(McpServerConfig config) {
            try {
                logger.trace(
                    "McpStdioClient.startProcess server='{}' command={} framing={} workDir='{}' envKeys={}",
                    config.serverName,
                    config.stdioCommand,
                    config.stdioMessageFraming,
                    config.stdioWorkingDirectory,
                    config.stdioEnv == null ? Set.of() : config.stdioEnv.keySet()
                );
                ProcessBuilder processBuilder = new ProcessBuilder(config.stdioCommand);
                if (config.stdioWorkingDirectory != null && !config.stdioWorkingDirectory.trim().isEmpty()) {
                    processBuilder.directory(new File(config.stdioWorkingDirectory.trim()));
                }
                if (config.stdioEnv != null && !config.stdioEnv.isEmpty()) {
                    processBuilder.environment().putAll(config.stdioEnv);
                }
                // Don't combine stderr with stdout - MCP server logs to stderr\n        // processBuilder.redirectErrorStream(true);
                return processBuilder.start();
            } catch (IOException e) {
                throw new IllegalStateException(
                    "Failed to start MCP stdio process for server " + config.serverName + ": " + String.join(" ", config.stdioCommand),
                    e
                );
            }
        }

        private Map<String, String> readHeaders(InputStream inputStream) throws IOException {
            Map<String, String> headers = new LinkedHashMap<>();
            while (true) {
                String line = readAsciiLine(inputStream);
                if (line == null) {
                    throw new EOFException("MCP stdio stream closed while reading headers");
                }
                if (line.isEmpty()) {
                    return headers;
                }
                int separatorIndex = line.indexOf(':');
                if (separatorIndex <= 0) {
                    continue;
                }
                String key = line.substring(0, separatorIndex).trim().toLowerCase(Locale.ROOT);
                String value = line.substring(separatorIndex + 1).trim();
                headers.put(key, value);
            }
        }

        private String readAsciiLine(InputStream inputStream) throws IOException {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            int previous = -1;
            while (true) {
                int current = inputStream.read();
                if (current < 0) {
                    if (baos.size() == 0) {
                        return null;
                    }
                    break;
                }
                if (previous == '\r' && current == '\n') {
                    byte[] raw = baos.toByteArray();
                    int len = raw.length == 0 ? 0 : raw.length - 1;
                    return new String(raw, 0, len, StandardCharsets.US_ASCII);
                }
                baos.write(current);
                previous = current;
            }
            return baos.toString(StandardCharsets.US_ASCII);
        }

        private byte[] readExactly(InputStream inputStream, int length) throws IOException {
            byte[] payload = new byte[length];
            int offset = 0;
            while (offset < length) {
                int read = inputStream.read(payload, offset, length - offset);
                if (read < 0) {
                    throw new EOFException("MCP stdio stream closed while reading payload");
                }
                offset += read;
            }
            return payload;
        }
    }
}
