package com.localmesalevel.aisystemtakeone.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.localmesalevel.aisystemtakeone.llm.model.LlmApiType;
import com.localmesalevel.aisystemtakeone.llm.model.LlmEndpointCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Component
public class EmbeddingService {

    private static final Logger logger = LoggerFactory.getLogger(EmbeddingService.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public EmbeddingService() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    public float[] embed(String text, LlmEndpointCredentials credentials) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        try {
            if (credentials.getLlmApiType() == LlmApiType.OpenAICompatible) {
                return callOpenAIEmbeddings(text, credentials);
            }
            if (credentials.getLlmApiType() == LlmApiType.AnthropicCompatible) {
                return callOpenAIEmbeddings(text, credentials);
            }
            logger.warn("Unsupported API type for embeddings: {}", credentials.getLlmApiType());
            return null;
        } catch (Exception e) {
            logger.error("Failed to generate embeddings: {}", e.toString(), e);
            return null;
        }
    }

    private float[] callOpenAIEmbeddings(String text, LlmEndpointCredentials credentials) {
        final String url = normalizeBaseURL(credentials.getBaseURL()) + "/v1/embeddings";
    	try {
	        HttpHeaders headers = new HttpHeaders();
	        headers.setContentType(MediaType.APPLICATION_JSON);
	        headers.setBearerAuth(credentials.getApiKey());
	
	        Map<String, Object> body = Map.of(
	            "model", "nvidia/nv-embed-v1",
	            "input", text.trim(),
	            "encoding_format", "float",
	            "input_type", "query",
	            "truncate", "NONE"
	        );
	
	        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
	        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, request, JsonNode.class);
	
	        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
	            throw new Exception("Embedding API returned non-2xx: " + response.getStatusCode());
	        }
	
	        JsonNode data = response.getBody().get("data");
	        if (data == null || !data.isArray() || data.size() == 0) {
	            logger.warn("Embedding API returned empty data array");
	            return null;
	        }
	
	        JsonNode embeddingNode = data.get(0).get("embedding");
	        if (embeddingNode == null || !embeddingNode.isArray()) {
	        	throw new Exception("Embedding API returned invalid embedding format");
	        }
	
	        float[] embedding = new float[embeddingNode.size()];
	        for (int i = 0; i < embeddingNode.size(); i++) {
	            embedding[i] = (float) embeddingNode.get(i).asDouble();
	        }
	        return embedding;
    	} catch (Throwable tr) {
    		throw new RuntimeException("Error processing URL '"+url+"': "+tr, tr);
    	}
    }

    private static String normalizeBaseURL(String url) {
        if (url == null) {
            return "";
        }
        String trimmed = url.trim();
        if (trimmed.endsWith("/")) {
            return trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
