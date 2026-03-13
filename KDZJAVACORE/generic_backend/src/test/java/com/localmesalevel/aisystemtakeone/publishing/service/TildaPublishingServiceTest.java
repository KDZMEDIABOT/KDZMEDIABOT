package com.localmesalevel.aisystemtakeone.publishing.service;

import com.localmesalevel.aisystemtakeone.assembly.model.Article;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("dev")
class TildaPublishingServiceTest {

    @Autowired
    private TildaPublishingService publishingService;

    @Test
    void publishToTilda() {
        Article article = new Article();
        article.setId(1L);
        article.setTitle("Test Article");
        article.setSlug("test-article");
        article.setMetaDescription("Test meta description");

        String url = publishingService.publishToTilda(article);

        assertNotNull(url);
        assertTrue(url.contains("test-article"));
    }

    @Test
    void schedulePublication() {
        Article article = new Article();
        article.setId(1L);
        article.setSlug("scheduled-article");

        LocalDateTime scheduled = LocalDateTime.now().plusDays(1);
        String url = publishingService.schedulePublication(article, scheduled);

        assertNotNull(url);
        assertTrue(url.contains("scheduled"));
    }

    @Test
    void validateTildaFormat() {
        String validBlocks = "[BLOCK:TITLE]Test[/BLOCK][BLOCK:CONTENT]Content[/BLOCK]";

        boolean isValid = publishingService.validateTildaFormat(validBlocks);

        assertTrue(isValid);
    }

    @Test
    void validateTildaFormatInvalid() {
        String invalidBlocks = "Invalid content without tags";

        boolean isValid = publishingService.validateTildaFormat(invalidBlocks);

        assertFalse(isValid);
    }

    @Test
    void generateTildaMeta() {
        Article article = new Article();
        article.setTitle("Test Article");
        article.setMetaDescription("Test description");
        Article.SeoMetadata seo = new Article.SeoMetadata();
        seo.setSeoTitle("SEO Title");
        seo.setUrlSlug("test-slug");
        article.setSeoMetadata(seo);

        String meta = publishingService.generateTildaMeta(article);

        assertNotNull(meta);
        assertTrue(meta.contains("<title>"));
        assertTrue(meta.contains("<meta name=\"description\""));
        assertTrue(meta.contains("<link rel=\"canonical\""));
    }
}
