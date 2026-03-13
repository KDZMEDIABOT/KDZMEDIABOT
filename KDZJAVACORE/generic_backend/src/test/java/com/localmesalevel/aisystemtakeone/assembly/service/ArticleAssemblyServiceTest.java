package com.localmesalevel.aisystemtakeone.assembly.service;

import com.localmesalevel.aisystemtakeone.assembly.model.Article;
import com.localmesalevel.aisystemtakeone.image.model.ImageData;
import com.localmesalevel.aisystemtakeone.research.model.ResearchData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("dev")
class ArticleAssemblyServiceTest {

    @Autowired
    private ArticleAssemblyService assemblyService;

    @Test
    void assembleArticle() {
        Long topicId = 1L;
        String topicTitle = "Mindfulness-Based Stress Reduction";
        ArticleAssemblyService.TopicCategory category = ArticleAssemblyService.TopicCategory.ART_THERAPY;

        ResearchData research = new ResearchData();
        research.setSummary("Test summary of research data");
        research.setExpertQuotes("Dr. Smith (2024): This is important");
        research.setStatistics("85% effectiveness");
        research.setCitations(List.of("Citation 1", "Citation 2"));
        research.setSources(List.of());

        List<ImageData> images = new ArrayList<>();

        Article article = assemblyService.assembleArticle(topicId, topicTitle, category, research, images);

        assertNotNull(article);
        assertEquals(topicId, article.getTopicId());
        assertNotNull(article.getTitle());
        assertTrue(article.getTitle().contains("Mindfulness-Based Stress Reduction"));
        assertNotNull(article.getSlug());
        assertNotNull(article.getMetaDescription());
        assertFalse(article.getSections().isEmpty());
        assertNotNull(article.getSeoMetadata());
        assertNotNull(article.getFaqSection());
    }

    @Test
    void assembledArticleHasH2Sections() {
        Article article = createTestArticle(ArticleAssemblyService.TopicCategory.PSYCHOLOGY_TEST);

        long h2Count = article.getSections().stream()
            .filter(s -> "H2".equals(s.getType()))
            .count();

        assertTrue(h2Count > 0);
    }

    @Test
    void assembledArticleHasSeoMetadata() {
        Article article = createTestArticle(ArticleAssemblyService.TopicCategory.ART_THERAPY);

        assertNotNull(article.getSeoMetadata());
        assertTrue(article.getSeoMetadata().getSeoTitle().length() <= 60);
        assertTrue(article.getSeoMetadata().getMetaDescription().length() <= 160);
    }

    @Test
    void assembledArticleHasFaqSection() {
        Article article = createTestArticle(ArticleAssemblyService.TopicCategory.RECOMMENDATION_LIST);

        assertNotNull(article.getFaqSection());
        assertNotNull(article.getFaqSection().getItems());
        assertFalse(article.getFaqSection().getItems().isEmpty());
    }

    private Article createTestArticle(ArticleAssemblyService.TopicCategory category) {
        ResearchData research = new ResearchData();
        research.setSummary("Summary");
        research.setExpertQuotes("Expert quote");
        research.setStatistics("Stats");

        return assemblyService.assembleArticle(
            1L, "Test Topic", category, research, new ArrayList<>()
        );
    }
}
