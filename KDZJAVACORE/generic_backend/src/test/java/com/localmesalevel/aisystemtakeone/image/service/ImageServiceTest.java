package com.localmesalevel.aisystemtakeone.image.service;

import com.localmesalevel.aisystemtakeone.image.model.ImageData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("dev")
class ImageServiceTest {

    @Autowired
    private ImageService imageService;

    @Test
    void searchPexelsImages() {
        Long topicId = 1L;
        String query = "mindfulness meditation";

        List<ImageData> images = imageService.searchPexelsImages(topicId, query);

        assertNotNull(images);
        assertFalse(images.isEmpty());

        ImageData firstImage = images.get(0);
        assertEquals(topicId, firstImage.getTopicId());
        assertEquals(ImageData.ImageSource.PEXELS, firstImage.getSource());
        assertNotNull(firstImage.getUrl());
        assertNotNull(firstImage.getAltText());
        assertNotNull(firstImage.getRelevanceScore());
    }

    @Test
    void generateAiImage() {
        Long topicId = 1L;
        String prompt = "Calm meditation scene with nature elements";

        ImageData image = imageService.generateAiImage(topicId, prompt);

        assertNotNull(image);
        assertEquals(topicId, image.getTopicId());
        assertEquals(ImageData.ImageSource.AI_GENERATED, image.getSource());
        assertTrue(image.getUrl().contains("ai-generated"));
        assertEquals(100, image.getRelevanceScore());
    }

    @Test
    void calculateRelevanceScore() {
        String topic = "Mindfulness Meditation";
        String imageDescription = "Person practicing mindfulness meditation outdoors";

        int score = imageService.calculateRelevanceScore(topic, imageDescription);

        assertTrue(score >= 70);
        assertTrue(score <= 100);
    }

    @Test
    void calculateRelevanceScoreWithNoKeywords() {
        String topic = "Unknown Specific";
        String imageDescription = "Generic photo";

        int score = imageService.calculateRelevanceScore(topic, imageDescription);

        assertEquals(70, score);
    }
}
