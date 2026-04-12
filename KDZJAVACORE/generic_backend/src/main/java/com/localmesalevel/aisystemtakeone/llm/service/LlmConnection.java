package com.localmesalevel.aisystemtakeone.llm.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.localmesalevel.aisystemtakeone.llm.model.LlmApiType;
import com.localmesalevel.aisystemtakeone.llm.model.LlmEndpointCredentials;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.client.ResponseExtractor;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;

public class LlmConnection {

    private static final String ANTHROPIC_VERSION = "2023-06-01";
    private static final Logger logger = LoggerFactory.getLogger(LlmConnection.class);

    private final LlmApiType llmApiType;
    private final String baseURL;
    private final String apiKey;
    private final String model;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
	private final LlmEndpointCredentials endpointCredentials;

    public LlmConnection(
        LlmApiType llmApiType,
        String baseURL,
        String apiKey,
        String model,
        RestTemplate restTemplate,
        ObjectMapper objectMapper,
        LlmEndpointCredentials endpointCredentials
    ) {
        this.llmApiType = llmApiType;
        this.baseURL = normalizeBaseURL(baseURL);
        this.apiKey = apiKey;
        this.model = normalizeModelName(model);
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.endpointCredentials = endpointCredentials;
    }

    public String complete(Iterator<String> prompt) {
        return complete(null, prompt, 0.2, 1024);
    }

    public String complete(String systemPrompt, Iterator<String> aiContext, double temperature, int maxTokens) {
        String effectiveModel = resolveEffectiveModel(model);
        logger.trace(
            "LlmConnection.complete called: apiType='{}', requestedModel='{}', effectiveModel='{}', baseURL='{}', hasSystemPrompt={}, userPromptLength={}, temperature={}, maxTokens={}",
            llmApiType,
            model,
            effectiveModel,
            baseURL,
            !isBlank(systemPrompt),
            !aiContext.hasNext() ? 0 : 1,
            temperature,
            maxTokens
        );
        if (!aiContext.hasNext()) {
            throw new IllegalArgumentException("User prompt is required");
        }

        if (llmApiType == LlmApiType.OpenAICompatible) {
            return callOpenAICompatibleStreaming(effectiveModel, systemPrompt, aiContext, temperature, maxTokens);
        }
        if (llmApiType == LlmApiType.AnthropicCompatible) {
            return callAnthropicCompatible(effectiveModel, systemPrompt, aiContext, temperature, maxTokens);
        }
        throw new IllegalArgumentException("Unsupported API type: " + llmApiType);
    }

    private String callOpenAICompatible(String model, String systemPrompt, Iterator<String> aiContext, double temperature, int maxTokens) {
        String url = baseURL + "/v1/chat/completions";
        logger.trace(
            "OpenAI-compatible request: url='{}', model='{}', hasSystemPrompt={}, userPromptLength={}, temperature={}, maxTokens={}, auth='{}'",
            url,
            model,
            !isBlank(systemPrompt),
            aiContext.hasNext()?1:0,
            temperature,
            Math.max(maxTokens, 1),
            maskSecret(apiKey)
        );
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        List<Map<String, String>> messages = new ArrayList<>();
        if (!isBlank(systemPrompt)) {
            messages.add(Map.of("role", "system", "content", systemPrompt.trim()));
        }
        while(aiContext.hasNext()) {
        	messages.add(Map.of("role", "user", "content", aiContext.next()));
        }
        
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("temperature", temperature);
        body.put("max_tokens", Math.max(maxTokens, 1));
        
        logger.trace(
                "OpenAI-compatible request: headers='{}', body={}",
                headers,
                body
            );

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                String.class
            );
            logger.trace(
                "OpenAI-compatible response: status='{}', bodyLength={}",
                response.getStatusCode(),
                response.getBody() == null ? 0 : response.getBody().length()
            );
            LlmReply reply = extractOpenAIText(response.getBody());
            String selectedText = reply.replyType == LlmReplyType.CONTENT
                ? trimToNull(reply.contentText)
                : trimToNull(reply.reasoningText);
            if (selectedText == null) {
                throw new IllegalStateException("OpenAI-compatible response did not contain selected reply text");
            }
            logger.trace(
                "OpenAI-compatible selected reply: type={}, contentLength={}, reasoningLength={}",
                reply.replyType,
                reply.contentText == null ? 0 : reply.contentText.length(),
                reply.reasoningText == null ? 0 : reply.reasoningText.length()
            );
            return selectedText;
        } catch (RestClientException e) {
            logger.trace("OpenAI-compatible request failed for url='{}': {}", url, e.getMessage(), e);
            throw new IllegalStateException("OpenAI-compatible request failed: " + e.getMessage(), e);
        }
    }

    /**
     * Call OpenAI-compatible API with streaming.
     * Accumulates content tokens while skipping reasoning tokens until content arrives.
     */
    private String callOpenAICompatibleStreaming(String model, String systemPrompt, Iterator<String> aiContext,
                                                   double temperature, int maxTokens) {
        String url = baseURL + "/v1/chat/completions";
        logger.trace(
            "OpenAI-compatible streaming request: url='{}', model='{}', hasSystemPrompt={}, userPromptLength={}, temperature={}, maxTokens={}",
            url, model, !isBlank(systemPrompt), aiContext.hasNext() ? 1 : 0, temperature, Math.max(maxTokens, 1)
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        List<Map<String, String>> messages = new ArrayList<>();
        if (!isBlank(systemPrompt)) {
            messages.add(Map.of("role", "system", "content", systemPrompt.trim()));
        }
        while (aiContext.hasNext()) {
            messages.add(Map.of("role", "user", "content", aiContext.next()));
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("temperature", temperature);
        body.put("max_tokens", Math.max(maxTokens, 1));
        body.put("stream", true);

        ResponseStreamAccumulator accumulator = new ResponseStreamAccumulator();

        ResponseExtractor<Void> extractor = response -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(response.getBody(), java.nio.charset.StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    accumulator.processLine(line);
                }
            }
            return null;
        };

        try {
            restTemplate.execute(url, HttpMethod.POST, request -> {
                request.getHeaders().putAll(headers);
                String jsonBody;
                try {
                    jsonBody = objectMapper.writeValueAsString(body);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to serialize request body", e);
                }
                request.getBody().write(jsonBody.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }, extractor);

            String result = accumulator.getContent();
            if (isBlank(result)) {
                result = accumulator.getReasoning();
            }
            if (isBlank(result)) {
                throw new IllegalStateException("OpenAI-compatible streaming response did not contain content");
            }
            logger.trace("OpenAI-compatible streaming completed: contentChars={}, reasoningChars={}",
                accumulator.getContent().length(), accumulator.getReasoning().length());
            return result;
        } catch (RestClientException e) {
            logger.trace("OpenAI-compatible streaming request failed: {}", e.getMessage(), e);
            throw new IllegalStateException("OpenAI-compatible streaming request failed: " + e.getMessage(), e);
        }
    }

    /**
     * Accumulates streaming response tokens, tracking content vs reasoning separately.
     */
    private static class ResponseStreamAccumulator {
        private final StringBuilder contentBuilder = new StringBuilder();
        private final StringBuilder reasoningBuilder = new StringBuilder();
        private boolean contentStarted = false;
        private boolean inReasoningBlock = false;

        void processLine(String line) {
            if (isBlank(line) || !line.startsWith("data:")) {
                return;
            }

            String data = line.substring(5).trim();
            if ("[DONE]".equals(data)) {
                return;
            }

            try {
                JsonNode root = new ObjectMapper().readTree(data);
                JsonNode choices = root.path("choices");
                if (!choices.isArray() || choices.size() == 0) {
                    return;
                }

                JsonNode delta = choices.get(0).path("delta");
                if (delta.isMissingNode()) {
                    return;
                }

                // Check for reasoning content
                JsonNode reasoning = delta.path("reasoning");
                if (!reasoning.isMissingNode() && reasoning.isTextual()) {
                    String reasoningText = reasoning.asText();
                    if (!isBlank(reasoningText)) {
                        reasoningBuilder.append(reasoningText);
                        inReasoningBlock = true;
                    }
                }

                // Check for regular content
                JsonNode content = delta.path("content");
                if (!content.isMissingNode() && content.isTextual()) {
                    String contentText = content.asText();
                    if (!isBlank(contentText)) {
                        // If we were in reasoning and now got content, mark content as started
                        if (inReasoningBlock) {
                            contentStarted = true;
                            inReasoningBlock = false;
                        }
                        contentBuilder.append(contentText);
                    }
                }
            } catch (Exception e) {
                // Skip malformed lines
            }
        }

        String getContent() {
            return contentBuilder.toString();
        }

        String getReasoning() {
            return reasoningBuilder.toString();
        }

        private static boolean isBlank(String s) {
            return s == null || s.trim().isEmpty();
        }
    }

    private String callAnthropicCompatible(String model, String systemPrompt, Iterator<String> aiContext, double temperature, int maxTokens) {
        String url = baseURL + "/v1/messages";
        logger.trace(
            "Anthropic-compatible request: url='{}', model='{}', hasSystemPrompt={}, userPromptLength={}, temperature={}, maxTokens={}, auth='x-api-key:{}', anthropic-version='{}'",
            url,
            model,
            !isBlank(systemPrompt),
            aiContext.hasNext()?1:0,
            temperature,
            Math.max(maxTokens, 1),
            maskSecret(apiKey),
            ANTHROPIC_VERSION
        );
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", apiKey);
        headers.set("anthropic-version", ANTHROPIC_VERSION);

        List<Map<String, String>> messages = List.of();
        while(aiContext.hasNext())
        	messages.add(Map.of("role", "user", "content", aiContext.next()));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("max_tokens", Math.max(maxTokens, 1));
        body.put("temperature", temperature);
        if (!isBlank(systemPrompt)) {
            body.put("system", systemPrompt.trim());
        }

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                String.class
            );
            logger.trace(
                "Anthropic-compatible response: status='{}', bodyLength={}",
                response.getStatusCode(),
                response.getBody() == null ? 0 : response.getBody().length()
            );
            return extractAnthropicText(response.getBody());
        } catch (RestClientException e) {
            logger.trace("Anthropic-compatible request failed for url='{}': {}", url, e.getMessage(), e);
            throw new IllegalStateException("Anthropic-compatible request failed: " + e.getMessage(), e);
        }
    }

    private static String wrapNonNull(String s) {
    	if(s==null)return "null";
    	else return "'"+s+"'";
    }
    private LlmReply extractOpenAIText(String responseBody) {
        logger.trace("Parsing OpenAI-compatible response, bodyLength={}, responseBody={}", responseBody == null ? 0 : responseBody.length(), wrapNonNull(responseBody));
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode firstChoice = root.path("choices").path(0);
            JsonNode messageNode = firstChoice.path("message");
            JsonNode contentNode = messageNode.path("content");
            String contentText = extractOpenAIMessageContent(contentNode);
            String reasoningText = extractOpenAIReasoningText(messageNode, contentNode);
            if (!isBlank(contentText)) {
                logger.trace(
                    "Parsed OpenAI-compatible CONTENT reply length={}, text={}",
                    contentText.length(),
                    wrapNonNull(contentText)
                );
                return new LlmReply(LlmReplyType.CONTENT, contentText, reasoningText);
            }

            // Some providers may return plain text directly under choices[0].text
            String textFallback = trimToNull(firstChoice.path("text").asText(null));
            if (textFallback != null) {
                logger.trace(
                    "Parsed OpenAI-compatible CONTENT reply from choices[0].text length={}, text='{}'",
                    textFallback.length(),
                    textFallback
                );
                return new LlmReply(LlmReplyType.CONTENT, textFallback, reasoningText);
            }

            if (!isBlank(reasoningText)) {
                logger.trace(
                    "Parsed OpenAI-compatible REASONING reply length={}, text={}",
                    reasoningText.length(),
                    wrapNonNull(reasoningText)
                );
                return new LlmReply(LlmReplyType.REASONING, null, reasoningText);
            }

            String refusal = trimToNull(messageNode.path("refusal").asText(null));
            if (refusal != null) {
                throw new IllegalStateException("OpenAI-compatible model refusal: " + refusal);
            }

            throw new IllegalStateException(
                "OpenAI-compatible response did not include parseable text content under choices[0].message.content or choices[0].text"
            );
        } catch (Exception e) {
            logger.trace("Failed to parse OpenAI-compatible response", e);
            throw new IllegalStateException("Failed to parse OpenAI-compatible response: " + e.getMessage(), e);
        }
    }

    private String extractOpenAIMessageContent(JsonNode contentNode) {
        if (contentNode == null || contentNode.isMissingNode() || contentNode.isNull()) {
            return null;
        }
        if (contentNode.isTextual()) {
            return trimToNull(contentNode.asText());
        }
        if (contentNode.isObject()) {
            String textValue = trimToNull(contentNode.path("text").asText(null));
            if (textValue != null) {
                return textValue;
            }
            String outputText = trimToNull(contentNode.path("output_text").asText(null));
            if (outputText != null) {
                return outputText;
            }
            return null;
        }
        if (!contentNode.isArray()) {
            return null;
        }

        StringBuilder merged = new StringBuilder();
        for (JsonNode part : contentNode) {
            if (part == null || part.isNull() || part.isMissingNode()) {
                continue;
            }
            String piece = null;
            if (part.isTextual()) {
                piece = trimToNull(part.asText());
            } else if (part.isObject()) {
                piece = trimToNull(part.path("text").asText(null));
                if (piece == null) {
                    piece = trimToNull(part.path("output_text").asText(null));
                }
                if (piece == null) {
                    piece = trimToNull(part.path("content").asText(null));
                }
            }
            if (piece == null) {
                continue;
            }
            if (merged.length() > 0) {
                merged.append('\n');
            }
            merged.append(piece);
        }
        return trimToNull(merged.toString());
    }

    private String extractOpenAIReasoningText(JsonNode messageNode, JsonNode contentNode) {
        String directReasoning = trimToNull(messageNode.path("reasoning").asText(null));
        if (directReasoning != null) {
            return directReasoning;
        }
        String reasoningContent = trimToNull(messageNode.path("reasoning_content").asText(null));
        if (reasoningContent != null) {
            return reasoningContent;
        }
        if (contentNode == null || contentNode.isMissingNode() || contentNode.isNull()) {
            return null;
        }
        if (contentNode.isObject()) {
            String objectReasoning = trimToNull(contentNode.path("reasoning").asText(null));
            if (objectReasoning != null) {
                return objectReasoning;
            }
            return trimToNull(contentNode.path("reasoning_text").asText(null));
        }
        if (!contentNode.isArray()) {
            return null;
        }
        StringBuilder mergedReasoning = new StringBuilder();
        for (JsonNode part : contentNode) {
            if (part == null || !part.isObject()) {
                continue;
            }
            String partType = trimToNull(part.path("type").asText(null));
            String piece = null;
            if ("reasoning".equalsIgnoreCase(String.valueOf(partType))) {
                piece = trimToNull(part.path("text").asText(null));
                if (piece == null) {
                    piece = trimToNull(part.path("reasoning").asText(null));
                }
            }
            if (piece == null) {
                piece = trimToNull(part.path("reasoning").asText(null));
            }
            if (piece == null) {
                continue;
            }
            if (mergedReasoning.length() > 0) {
                mergedReasoning.append('\n');
            }
            mergedReasoning.append(piece);
        }
        return trimToNull(mergedReasoning.toString());
    }

    private String extractAnthropicText(String responseBody) {
        logger.trace("Parsing Anthropic-compatible response, bodyLength={}", responseBody == null ? 0 : responseBody.length());
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode textNode = root.path("content").path(0).path("text");
            if (textNode.isMissingNode() || textNode.isNull()) {
                throw new IllegalStateException("Anthropic-compatible response did not include content[0].text");
            }
            String parsed = textNode.asText();
            logger.trace("Parsed Anthropic-compatible response text length={}", parsed.length());
            return parsed;
        } catch (Exception e) {
            logger.trace("Failed to parse Anthropic-compatible response", e);
            throw new IllegalStateException("Failed to parse Anthropic-compatible response: " + e.getMessage(), e);
        }
    }

    private String normalizeBaseURL(String raw) {
        if (isBlank(raw)) {
            throw new IllegalArgumentException("baseURL is required");
        }
        String trimmed = raw.trim();
        String normalized = trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
        logger.trace("Normalized LLM baseURL from '{}' to '{}'", raw, normalized);
        return normalized;
    }

    private String normalizeModelName(String modelName) {
        if (isBlank(modelName)) {
            return null;
        }
        String normalized = modelName.trim();
        logger.trace("Normalized LLM default model name to '{}'", normalized);
        return normalized;
    }

    private String resolveEffectiveModel(String requestedModel) {
        if (!isBlank(requestedModel)) {
            return requestedModel.trim();
        }
        if (!isBlank(model)) {
            return model;
        }
        throw new IllegalArgumentException("Model is required");
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String maskSecret(String secret) {
        if (isBlank(secret)) {
            return "<empty>";
        }
        String trimmed = secret.trim();
        if (trimmed.length() <= 8) {
            return "***";
        }
        return trimmed.substring(0, 4) + "..." + trimmed.substring(trimmed.length() - 4);
    }

    public enum LlmReplyType {
        CONTENT,
        REASONING
    }

    public static final class LlmReply {
        private final LlmReplyType replyType;
        private final String contentText;
        private final String reasoningText;

        public LlmReply(LlmReplyType replyType, String contentText, String reasoningText) {
            this.replyType = replyType;
            this.contentText = contentText;
            this.reasoningText = reasoningText;
        }

        public LlmReplyType getReplyType() {
            return replyType;
        }

        public String getContentText() {
            return contentText;
        }

        public String getReasoningText() {
            return reasoningText;
        }
    }

	public String getModelName() {
		return resolveEffectiveModel(model);
	}

	public LlmEndpointCredentials getCredentials() {
		return endpointCredentials;
	}
}
