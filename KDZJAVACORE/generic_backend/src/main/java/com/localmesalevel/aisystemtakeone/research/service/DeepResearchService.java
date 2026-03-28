package com.localmesalevel.aisystemtakeone.research.service;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.localmesalevel.aisystemtakeone.llm.model.LlmEndpointCredentials;
import com.localmesalevel.aisystemtakeone.llm.repository.LlmEndpointCredentialsRepository;
import com.localmesalevel.aisystemtakeone.llm.service.LlmLoopEngine;
import com.localmesalevel.aisystemtakeone.research.model.ResearchData;
import com.localmesalevel.aisystemtakeone.user.model.UserAccount;
import com.localmesalevel.aisystemtakeone.user.repository.UserAccountRepository;

@Service
public class DeepResearchService {

    private static final Logger logger = LoggerFactory.getLogger(DeepResearchService.class);
    private static final String DEFAULT_SERVER_NAME = "readingplus-deepresearch";
    private static final String DEFAULT_STDIN_CONTAINER = "aisystem-readingplus-mcp-sidecar-dev";

    private final LlmLoopEngine llmLoopEngine;
    private final LlmEndpointCredentialsRepository llmEndpointCredentialsRepository;
    private final UserAccountRepository userAccountRepository;
    private final ObjectMapper objectMapper;
    private final String mcpServerName;
    private final String mcpBearerToken;
    private final String mcpStdioContainerName;
    private final int maxSteps;

    public DeepResearchService(
        LlmLoopEngine llmLoopEngine,
        LlmEndpointCredentialsRepository llmEndpointCredentialsRepository,
        UserAccountRepository userAccountRepository,
        ObjectMapper objectMapper,
        @Value("${research.deepresearch.mcp.server-name:" + DEFAULT_SERVER_NAME + "}") String mcpServerName,
        @Value("${research.deepresearch.mcp.bearer-token:}") String mcpBearerToken,
        @Value("${research.deepresearch.mcp.stdio.container-name:${READINGPLUS_MCP_CONTAINER_NAME:" + DEFAULT_STDIN_CONTAINER + "}}") String mcpStdioContainerName,
        @Value("${research.deepresearch.max-steps:8}") int maxSteps
    ) {
        this.llmLoopEngine = llmLoopEngine;
        this.llmEndpointCredentialsRepository = llmEndpointCredentialsRepository;
        this.userAccountRepository = userAccountRepository;
        this.objectMapper = objectMapper;
        this.mcpServerName = mcpServerName;
        this.mcpBearerToken = mcpBearerToken;
        this.mcpStdioContainerName = mcpStdioContainerName;
        this.maxSteps = maxSteps;
    }

    public ResearchData conductResearch(Long topicId, String topicTitle, Long requesterUserId) {
        logger.info("Conducting deep research for topic: {} as userId={}", topicTitle, requesterUserId);
        logger.trace(
            "DeepResearchService.conductResearch entry: topicId={}, topicTitleChars={}, requesterUserId={}",
            topicId,
            topicTitle == null ? 0 : topicTitle.length(),
            requesterUserId
        );

        LlmEndpointCredentials llmCredentials = resolveLlmCredentials(requesterUserId);
        String modelForRequest = resolveModelFromCredentials(llmCredentials);
        logger.trace(
            "DeepResearchService resolved credentials: endpointId={}, apiType={}, baseUrl='{}', model='{}'",
            llmCredentials.getId(),
            llmCredentials.getLlmApiType(),
            llmCredentials.getBaseURL(),
            modelForRequest
        );
        return conductResearch(topicId, topicTitle, llmCredentials, modelForRequest);
    }

    private ResearchData conductResearch(
        Long topicId,
        String topicTitle,
        LlmEndpointCredentials llmCredentials,
        String modelName
    ) {
        if (isBlank(modelName)) {
            throw new IllegalArgumentException("modelName is required for conductResearch");
        }
        logger.trace(
            "DeepResearchService executing LLM loop: topicId={}, model='{}', maxSteps={}, mcpServer='{}'",
            topicId,
            modelName,
            maxSteps,
            isBlank(mcpServerName) ? DEFAULT_SERVER_NAME : mcpServerName.trim()
        );
        String finalJson = llmLoopEngine.run(
            llmCredentials,
            modelName,
            buildSystemPrompt(),
            buildUserPrompt(topicTitle),
            List.of(buildMcpServerConfig()),
            maxSteps
        ).getFinalAnswer();
        logger.trace(
            "DeepResearchService loop completed: topicId={}, finalJsonChars={}, finalJsonPreview='{}'",
            topicId,
            finalJson == null ? 0 : finalJson.length(),
            preview(finalJson, 600)
        );

        ResearchData research = new ResearchData();
        research.setTopicId(topicId);
        applyLoopOutput(research, finalJson);
        logger.trace(
            "DeepResearchService parsed output: topicId={}, citations={}, sources={}",
            topicId,
            research.getCitations() == null ? 0 : research.getCitations().size(),
            research.getSources() == null ? 0 : research.getSources().size()
        );
        return research;
    }

    private LlmLoopEngine.McpServerConfig buildMcpServerConfig() {
        String serverName = isBlank(mcpServerName) ? DEFAULT_SERVER_NAME : mcpServerName.trim();
        String containerName = trimToNull(mcpStdioContainerName);
        if (containerName == null) {
            throw new IllegalStateException(
                "research.deepresearch.mcp.stdio.container-name is required for stdio MCP deep research"
            );
        }
        logger.trace(
            "DeepResearchService MCP config: server='{}', container='{}', transport=STDIO_LOCAL, framing={}",
            serverName,
            containerName,
            LlmLoopEngine.McpServerConfig.StdioMessageFraming.NEWLINE_DELIMITED_JSON
        );
        return LlmLoopEngine.McpServerConfig.stdioLocal(
            serverName,
            List.of("docker", "exec", "-i", containerName, "mcp-server-ds"),
            LlmLoopEngine.McpServerConfig.StdioMessageFraming.NEWLINE_DELIMITED_JSON
        );
    }

    private LlmEndpointCredentials resolveLlmCredentials(Long requesterUserId) {
        if (requesterUserId == null) {
            throw new IllegalArgumentException("requesterUserId is required for deep research");
        }
        logger.trace("Resolving LLM credentials for requesterUserId={}", requesterUserId);
        UserAccount user = userAccountRepository.findById(requesterUserId)
            .orElseThrow(() -> new IllegalStateException("Deep research user not found for id " + requesterUserId));

        LlmEndpointCredentials current = user.getCurrentLlmEndpoint();
        if (current == null || current.getId() == null) {
            throw new IllegalStateException(
                "No LLM connection found. Tap Settings and specify LLM API Key and select it as current LLM endpoint."
            );
        }

        Long endpointId = current.getId();
        logger.trace("Requester user {} points to current LLM endpoint id={}", requesterUserId, endpointId);
        return llmEndpointCredentialsRepository.findById(endpointId)
            .orElseThrow(() -> new IllegalStateException(
                "No LLM connection found. Tap Settings and specify LLM API Key and select it as current LLM endpoint."
            ));
    }

    private String resolveModelFromCredentials(LlmEndpointCredentials credentials) {
        String modelName = trimToNull(credentials.getModelName());
        if (modelName == null) {
            throw new IllegalStateException(
                "No model configured for current LLM endpoint. Open Settings and set Model name."
            );
        }
        logger.trace("Resolved model '{}' from endpoint id={}", modelName, credentials.getId());
        return modelName;
    }

    private void applyLoopOutput(ResearchData research, String responseJson) {
        JsonNode root = parseJson(responseJson);
        logger.trace("Applying loop output JSON to ResearchData: jsonChars={}", responseJson == null ? 0 : responseJson.length());
        research.setSummary(readRequiredText(root, "summary"));
        research.setExpertQuotes(readRequiredText(root, "expertQuotes"));
        research.setStatistics(readRequiredText(root, "statistics"));
        research.setCitations(readStringArray(root.path("citations"), "citations"));
        research.setSources(readSources(root.path("sources")));
    }

    private JsonNode parseJson(String raw) {
        if (isBlank(raw)) {
            throw new IllegalStateException("Deep research loop returned empty response");
        }
        String normalized = stripMarkdownJsonFence(raw);
        logger.trace("Parsing deep-research JSON: chars={}, preview='{}'", normalized.length(), preview(normalized, 600));
        try {
            return objectMapper.readTree(normalized);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Deep research loop returned invalid JSON: " + normalized, ex);
        }
    }

    private String readRequiredText(JsonNode root, String field) {
        String value = root.path(field).asText(null);
        if (isBlank(value)) {
            throw new IllegalStateException("Deep research loop missing required field: " + field);
        }
        return value.trim();
    }

    private List<String> readStringArray(JsonNode node, String field) {
        if (!node.isArray() || node.isEmpty()) {
            throw new IllegalStateException("Deep research loop field '" + field + "' must be a non-empty array");
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            String value = trimToNull(item.asText(null));
            if (value != null) {
                values.add(value);
            }
        }
        if (values.isEmpty()) {
            throw new IllegalStateException("Deep research loop field '" + field + "' must contain non-empty values");
        }
        return values;
    }

    private List<ResearchData.Source> readSources(JsonNode node) {
        if (!node.isArray() || node.isEmpty()) {
            throw new IllegalStateException("Deep research loop field 'sources' must be a non-empty array");
        }
        List<ResearchData.Source> sources = new ArrayList<>();
        for (JsonNode item : node) {
            sources.add(new ResearchData.Source(
                requiredField(item, "url"),
                requiredField(item, "title"),
                requiredField(item, "author"),
                requiredField(item, "year")
            ));
        }
        return sources;
    }

    private String requiredField(JsonNode node, String field) {
        String value = trimToNull(node.path(field).asText(null));
        if (value == null) {
            throw new IllegalStateException("Deep research source item missing field: " + field);
        }
        return value;
    }

    private String buildSystemPrompt() {
        return "You are an evidence-focused mental-health research assistant. " +
            "Use available MCP tools to gather authoritative sources and produce structured research output.";
    }

    private String buildUserPrompt(String topicTitle) {
        return "Conduct deep research for topic: " + topicTitle + "\n" +
            "Requirements:\n" +
            "1) Use MCP tools for evidence gathering.\n" +
            "2) Prioritize authoritative, expert, and recent sources.\n" +
            "3) Return JSON only with this schema:\n" +
            "{\n" +
            "  \"summary\": \"string\",\n" +
            "  \"expertQuotes\": \"string\",\n" +
            "  \"statistics\": \"string\",\n" +
            "  \"citations\": [\"string\", \"...\"],\n" +
            "  \"sources\": [\n" +
            "    {\"url\":\"string\",\"title\":\"string\",\"author\":\"string\",\"year\":\"string\"}\n" +
            "  ]\n" +
            "}\n" +
            "4) Ensure all arrays are non-empty.";
    }

    private String stripMarkdownJsonFence(String raw) {
        String trimmed = raw.trim();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        int firstLineEnd = trimmed.indexOf('\n');
        if (firstLineEnd < 0) {
            return trimmed;
        }
        int closingFence = trimmed.lastIndexOf("```");
        if (closingFence <= firstLineEnd) {
            return trimmed;
        }
        return trimmed.substring(firstLineEnd + 1, closingFence).trim();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public boolean validateEEAT(Long topicId, ResearchData research) {
        logger.debug("Validating E-E-A-T compliance for topic {}", topicId);
        logger.trace(
            "EEAT input stats: topicId={}, hasCitations={}, citationsCount={}, hasExpertQuotes={}, hasStatistics={}",
            topicId,
            research.getCitations() != null,
            research.getCitations() == null ? 0 : research.getCitations().size(),
            research.getExpertQuotes() != null,
            research.getStatistics() != null
        );
        // Check Experience, Expertise, Authority, Trust signals
        return research.getCitations() != null && !research.getCitations().isEmpty()
            && research.getExpertQuotes() != null && research.getStatistics() != null;
    }

    private String preview(String value, int maxChars) {
        if (value == null) {
            return "null";
        }
        String normalized = value.replace('\n', ' ').replace('\r', ' ');
        if (normalized.length() <= maxChars) {
            return normalized;
        }
        return normalized.substring(0, Math.max(0, maxChars)) + "...(truncated)";
    }
}
