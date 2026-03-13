package com.localmesalevel.aisystemtakeone.workflow.service;

import com.localmesalevel.aisystemtakeone.assembly.model.Article;
import com.localmesalevel.aisystemtakeone.topic.model.Topic;
import com.localmesalevel.aisystemtakeone.topic.service.TopicService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("dev")
class ContentWorkflowOrchestratorTest {

    @Autowired
    private ContentWorkflowOrchestrator orchestrator;

    @Autowired
    private TopicService topicService;

    private Topic testTopic;

    @BeforeEach
    void setUp() {
        testTopic = topicService.createTopic(
                "Workflow Test Topic",
                Topic.TopicCategory.PSYCHOLOGY_TEST,
                "workflow-test"
        );
        topicService.approveTopic(testTopic.getId());
    }

    @Test
    void executeCompleteWorkflow() {
        Article article = orchestrator.executeWorkflow(testTopic.getId());

        assertNotNull(article);
        assertNotNull(article.getId());
        assertNotNull(article.getTitle());
        assertFalse(article.getTitle().trim().isEmpty());
        assertNotNull(article.getSlug());
        assertNotNull(article.getMetaDescription());
        assertNotNull(article.getSections());
        assertFalse(article.getSections().isEmpty());
    }

    @Test
    void generatePsychologyTest() {
        Article article = orchestrator.generateContentByType(testTopic);

        assertNotNull(article);
        assertTrue(article.getTitle().contains("Test") || article.getTitle().contains("Assessment"));
    }

    @Test
    void generateArtTherapyContent() {
        Topic artTopic = topicService.createTopic(
                "Art Therapy Activity",
                Topic.TopicCategory.ART_THERAPY,
                "art-therapy"
        );

        Article article = orchestrator.generateContentByType(artTopic);

        assertNotNull(article);
        assertTrue(article.getTitle().contains("Art") || article.getTitle().contains("H2"));
    }

    @Test
    void generateRecommendationList() {
        Topic recTopic = topicService.createTopic(
                "Best Apps",
                Topic.TopicCategory.RECOMMENDATION_LIST,
                "best-apps"
        );

        Article article = orchestrator.generateContentByType(recTopic);

        assertNotNull(article);
        assertTrue(article.getTitle().contains("Best"));
    }

    @Test
    void batchProcess() {
        Article result = orchestrator.executeWorkflow(testTopic.getId());
        List<Article> results = orchestrator.batchProcess(List.of(testTopic.getId()));

        assertFalse(results.isEmpty());
    }
}
