package com.localmesalevel.aisystemtakeone.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("dev")
class ArticleServiceTest {

    @Autowired
    private ArticleService articleService;

    @Test
    void generateArticleReturnsExpectedString() {
        String topic = "Test Topic";
        String result = articleService.generateArticle(topic);
        assertNotNull(result);
        assertTrue(result.contains(topic));
        assertTrue(result.startsWith("Generated article for topic:"));
    }

    @Test
    void generateArticleHandlesEmptyTopic() {
        String topic = "";
        String result = articleService.generateArticle(topic);
        assertNotNull(result);
        assertTrue(result.contains(topic));
    }
}