package com.customer1org.aisystem.controller;

import com.localmesalevel.aisystemtakeone.workflow.model.ArticleGenerationJob;
import com.localmesalevel.aisystemtakeone.workflow.service.ArticleGenerationJobService;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/** Customer-specific article endpoints; unique bean name to avoid conflict with generic library's ArticleController. */
@RestController("customerArticleController")
@RequestMapping("/api/articles")
public class ArticleController {

    private final ArticleGenerationJobService articleGenerationJobService;

    public ArticleController(ArticleGenerationJobService articleGenerationJobService) {
        this.articleGenerationJobService = articleGenerationJobService;
    }

    @PostMapping("/generate")
    public Map<String, String> generateArticle(@RequestBody Map<String, String> request) {
        String topic = request.getOrDefault("topic", "default topic");
        String category = request.getOrDefault("category", "ART_THERAPY");
        String requesterRole = request.getOrDefault("requesterRole", "unknown");
        Long requesterUserId = parseLongOrNull(request.get("requesterUserId"));

        ArticleGenerationJob job = articleGenerationJobService.createJob(topic, category, requesterRole, requesterUserId);

        Map<String, String> response = new HashMap<>();
        response.put("status", "success");
        response.put("topic", topic);
        response.put("jobId", String.valueOf(job.getId()));
        response.put("jobStatus", job.getStatus().name());
        response.put("message", "Article generation job queued for topic: " + topic);
        return response;
    }

    private Long parseLongOrNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
