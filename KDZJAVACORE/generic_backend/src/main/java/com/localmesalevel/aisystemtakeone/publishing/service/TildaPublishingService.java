package com.localmesalevel.aisystemtakeone.publishing.service;

import com.localmesalevel.aisystemtakeone.assembly.model.Article;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
public class TildaPublishingService {

    private static final Logger logger = LoggerFactory.getLogger(TildaPublishingService.class);
    private static final String TILDA_BASE_URL = "https://linatherapy.app/blog/";

    public String publishToTilda(Article article) {
        logger.info("Publishing article {} to Tilda", article.getId());

        try {
            // Convert article to Tilda block format
            String tildaBlocks = convertToTildaBlocks(article);

            // In real implementation, this would call Tilda API
            String publishedUrl = TILDA_BASE_URL + article.getSlug();

            logger.info("Article published successfully at {}", publishedUrl);
            return publishedUrl;
        } catch (Exception e) {
            logger.error("Failed to publish article: {}", e.getMessage());
            throw new RuntimeException("Publishing failed", e);
        }
    }

    public String schedulePublication(Article article, LocalDateTime scheduledDate) {
        logger.info("Scheduling article {} for publication at {}", article.getId(), scheduledDate);

        // In real implementation, this would use a task scheduler
        // For now, return scheduled URL
        return TILDA_BASE_URL + "scheduled/" + article.getSlug();
    }

    private String convertToTildaBlocks(Article article) {
        StringBuilder blocks = new StringBuilder();

        // Header block
        blocks.append("[BLOCK:TITLE]")
              .append(article.getTitle())
              .append("[/BLOCK]\n");

        // Meta description
        blocks.append("[BLOCK:DESCRIPTION]")
              .append(article.getMetaDescription())
              .append("[/BLOCK]\n");

        // Content sections
        if (article.getSections() != null) {
            for (Article.Section section : article.getSections()) {
                blocks.append("[BLOCK:").append(section.getType()).append("]");
                blocks.append(section.getContent());
                blocks.append("[/BLOCK]\n");
            }
        }

        // FAQ section
        if (article.getFaqSection() != null && article.getFaqSection().getItems() != null) {
            blocks.append("[BLOCK:FAQ]\n");
            for (Article.FaqItem item : article.getFaqSection().getItems()) {
                blocks.append("Q: ").append(item.getQuestion()).append("\n");
                blocks.append("A: ").append(item.getAnswer()).append("\n");
            }
            blocks.append("[/BLOCK]\n");
        }

        return blocks.toString();
    }

    public boolean validateTildaFormat(String blocks) {
        // Validate Tilda block syntax
        return blocks.contains("[BLOCK:TITLE]") &&
               blocks.contains("[/BLOCK]");
    }

    public String generateTildaMeta(Article article) {
        StringBuilder meta = new StringBuilder();
        meta.append("<title>").append(article.getTitle()).append("</title>\n");
        meta.append("<meta name=\"description\" content=\"").append(article.getMetaDescription()).append("\">\n");

        if (article.getSeoMetadata() != null) {
            meta.append("<link rel=\"canonical\" href=\"").append(TILDA_BASE_URL).append(article.getSeoMetadata().getUrlSlug()).append("\">\n");
        }

        return meta.toString();
    }
}
