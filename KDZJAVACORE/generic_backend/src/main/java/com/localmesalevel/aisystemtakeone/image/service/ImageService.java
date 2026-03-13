package com.localmesalevel.aisystemtakeone.image.service;

import com.localmesalevel.aisystemtakeone.image.model.ImageData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ImageService {

    private static final Logger logger = LoggerFactory.getLogger(ImageService.class);

    public List<ImageData> searchPexelsImages(Long topicId, String query) {
        logger.info("Searching Pexels images for topic: {} with query: {}", topicId, query);

        // Simulated Pexels API results
        List<ImageData> images = new ArrayList<>();

        ImageData img1 = new ImageData();
        img1.setTopicId(topicId);
        img1.setSource(ImageData.ImageSource.PEXELS);
        img1.setUrl("https://images.pexels.com/photos/sample1.jpg");
        img1.setAltText("Person practicing mindfulness meditation");
        img1.setCaption("Mindfulness practices can reduce stress by 40%");
        img1.setPexelsId("12345");
        img1.setPhotographerName("John Smith");
        img1.setRelevanceScore(95);
        images.add(img1);

        ImageData img2 = new ImageData();
        img2.setTopicId(topicId);
        img2.setSource(ImageData.ImageSource.PEXELS);
        img2.setUrl("https://images.pexels.com/photos/sample2.jpg");
        img2.setAltText("Calm and serene therapy session");
        img2.setPhotographerName("Jane Doe");
        img2.setRelevanceScore(88);
        images.add(img2);

        return images;
    }

    public ImageData generateAiImage(Long topicId, String prompt) {
        logger.info("Generating AI image for topic: {} with prompt: {}", topicId, prompt);

        ImageData image = new ImageData();
        image.setTopicId(topicId);
        image.setSource(ImageData.ImageSource.AI_GENERATED);
        image.setUrl("https://ai-generated.example.com/image-" + System.currentTimeMillis() + ".jpg");
        image.setAltText(prompt.substring(0, Math.min(prompt.length(), 100)));
        image.setRelevanceScore(100);

        return image;
    }

    public int calculateRelevanceScore(String topic, String imageDescription) {
        // Simple keyword matching for relevance
        int score = 70;
        String[] topicWords = topic.toLowerCase().split("\\s+");
        String descLower = imageDescription.toLowerCase();

        for (String word : topicWords) {
            if (word.length() > 3 && descLower.contains(word)) {
                score += 10;
            }
        }
        return Math.min(score, 100);
    }
}
