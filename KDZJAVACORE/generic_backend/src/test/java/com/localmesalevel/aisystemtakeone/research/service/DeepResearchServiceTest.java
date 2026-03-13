package com.localmesalevel.aisystemtakeone.research.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.localmesalevel.aisystemtakeone.llm.model.LlmApiType;
import com.localmesalevel.aisystemtakeone.llm.model.LlmEndpointCredentials;
import com.localmesalevel.aisystemtakeone.llm.repository.LlmEndpointCredentialsRepository;
import com.localmesalevel.aisystemtakeone.llm.service.LlmLoopEngine;
import com.localmesalevel.aisystemtakeone.research.model.ResearchData;
import com.localmesalevel.aisystemtakeone.user.model.UserAccount;
import com.localmesalevel.aisystemtakeone.user.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeepResearchServiceTest {
    private static final String ENDPOINT_MODEL = "nvidia/llama-3.1-nemotron-70b-instruct";

    @Mock
    private LlmLoopEngine llmLoopEngine;

    @Mock
    private LlmEndpointCredentialsRepository llmEndpointCredentialsRepository;

    @Mock
    private UserAccountRepository userAccountRepository;

    @Test
    void conductResearch() {
        DeepResearchService researchService = createService();
        stubLlmLoopJson(mockLoopJson("Dr. A", "85% improvement"));

        ResearchData research = researchService.conductResearch(1L, "Mindfulness-Based Stress Reduction", 1L);

        assertNotNull(research);
        assertEquals(1L, research.getTopicId());
        assertEquals("Research summary", research.getSummary());
        assertEquals("Dr. A", research.getExpertQuotes());
        assertEquals("85% improvement", research.getStatistics());
        assertFalse(research.getCitations().isEmpty());
        assertFalse(research.getSources().isEmpty());
        verify(llmLoopEngine).run(any(), eq(ENDPOINT_MODEL), anyString(), anyString(), anyList(), anyInt());
    }

    @Test
    void validateEEAT() {
        DeepResearchService researchService = createService();
        stubLlmLoopJson(mockLoopJson("Dr. B", "70% improvement"));
        ResearchData research = researchService.conductResearch(1L, "Test Topic", 1L);

        boolean isValid = researchService.validateEEAT(1L, research);

        assertTrue(isValid);
    }

    @Test
    void researchContainsExpertQuotes() {
        DeepResearchService researchService = createService();
        stubLlmLoopJson(mockLoopJson("Dr. Expert (2024)", "72% improvement"));
        ResearchData research = researchService.conductResearch(1L, "Anxiety Management", 1L);

        assertTrue(research.getExpertQuotes().contains("Dr."));
        assertTrue(research.getExpertQuotes().contains("2024"));
    }

    @Test
    void researchContainsStatistics() {
        DeepResearchService researchService = createService();
        stubLlmLoopJson(mockLoopJson("Dr. C", "85% response rate"));
        ResearchData research = researchService.conductResearch(1L, "Meditation Benefits", 1L);

        assertTrue(research.getStatistics().contains("85%"));
    }

    @Test
    void conductResearchRequiresCredentialModelName() {
        DeepResearchService researchService = createServiceWithoutModelName();
        IllegalStateException ex = assertThrows(
            IllegalStateException.class,
            () -> researchService.conductResearch(1L, "Model Required Topic", 1L)
        );
        assertTrue(ex.getMessage().contains("No model configured for current LLM endpoint"));
    }

    private DeepResearchService createService() {
        UserAccount user = new UserAccount();
        user.setId(1L);
        user.setUsername("admin");
        user.setRole("admin");

        LlmEndpointCredentials endpoint = new LlmEndpointCredentials();
        endpoint.setId(10L);
        endpoint.setLlmApiType(LlmApiType.AnthropicCompatible);
        endpoint.setBaseURL("https://mock-llm.example.com");
        endpoint.setApiKey("mock-key");
        endpoint.setEndpointDisplayName("Mock endpoint");
        endpoint.setModelName(ENDPOINT_MODEL);
        user.setCurrentLlmEndpoint(endpoint);

        when(userAccountRepository.findById(1L)).thenReturn(Optional.of(user));
        when(llmEndpointCredentialsRepository.findById(10L)).thenReturn(Optional.of(endpoint));

        return new DeepResearchService(
            llmLoopEngine,
            llmEndpointCredentialsRepository,
            userAccountRepository,
            new ObjectMapper(),
            "readingplus-deepresearch",
            "",
            "aisystem-readingplus-mcp-sidecar-dev",
            8
        );
    }

    private DeepResearchService createServiceWithoutModelName() {
        UserAccount user = new UserAccount();
        user.setId(1L);
        user.setUsername("admin");
        user.setRole("admin");

        LlmEndpointCredentials endpoint = new LlmEndpointCredentials();
        endpoint.setId(10L);
        endpoint.setLlmApiType(LlmApiType.AnthropicCompatible);
        endpoint.setBaseURL("https://mock-llm.example.com");
        endpoint.setApiKey("mock-key");
        endpoint.setEndpointDisplayName("Mock endpoint");
        endpoint.setModelName(null);
        user.setCurrentLlmEndpoint(endpoint);

        when(userAccountRepository.findById(1L)).thenReturn(Optional.of(user));
        when(llmEndpointCredentialsRepository.findById(10L)).thenReturn(Optional.of(endpoint));

        return new DeepResearchService(
            llmLoopEngine,
            llmEndpointCredentialsRepository,
            userAccountRepository,
            new ObjectMapper(),
            "readingplus-deepresearch",
            "",
            "aisystem-readingplus-mcp-sidecar-dev",
            8
        );
    }

    private void stubLlmLoopJson(String json) {
        when(llmLoopEngine.run(any(), anyString(), anyString(), anyString(), anyList(), anyInt()))
            .thenReturn(new LlmLoopEngine.LoopResult(json, List.of()));
    }

    private String mockLoopJson(String expertQuotes, String statistics) {
        return "{\n" +
            "  \"summary\": \"Research summary\",\n" +
            "  \"expertQuotes\": \"" + expertQuotes + "\",\n" +
            "  \"statistics\": \"" + statistics + "\",\n" +
            "  \"citations\": [\"Citation One\", \"Citation Two\"],\n" +
            "  \"sources\": [\n" +
            "    {\"url\":\"https://example.org/a\",\"title\":\"Source A\",\"author\":\"Author A\",\"year\":\"2024\"}\n" +
            "  ]\n" +
            "}";
    }
}
