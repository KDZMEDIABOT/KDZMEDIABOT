package com.localmesalevel.aisystemtakeone.workflow.service;

import com.localmesalevel.aisystemtakeone.assembly.model.Article;
import com.localmesalevel.aisystemtakeone.assembly.service.ArticleAssemblyService;
import com.localmesalevel.aisystemtakeone.contenttypes.service.ArtTherapyGenerator;
import com.localmesalevel.aisystemtakeone.contenttypes.service.PsychologyTestGenerator;
import com.localmesalevel.aisystemtakeone.contenttypes.service.RecommendationListGenerator;
import com.localmesalevel.aisystemtakeone.image.model.ImageData;
import com.localmesalevel.aisystemtakeone.image.service.ImageService;
import com.localmesalevel.aisystemtakeone.publishing.service.TildaPublishingService;
import com.localmesalevel.aisystemtakeone.research.model.ResearchData;
import com.localmesalevel.aisystemtakeone.research.service.DeepResearchService;
import com.localmesalevel.aisystemtakeone.review.model.ReviewData;
import com.localmesalevel.aisystemtakeone.review.service.ReviewService;
import com.localmesalevel.aisystemtakeone.topic.model.Topic;
import com.localmesalevel.aisystemtakeone.topic.service.TopicService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class ContentWorkflowOrchestrator {

    private static final Logger logger = LoggerFactory.getLogger(ContentWorkflowOrchestrator.class);

    private final TopicService topicService;
    private final DeepResearchService researchService;
    private final ImageService imageService;
    private final ArticleAssemblyService assemblyService;
    private final ReviewService reviewService;
    private final TildaPublishingService publishingService;
    private final PsychologyTestGenerator testGenerator;
    private final ArtTherapyGenerator artTherapyGenerator;
    private final RecommendationListGenerator recommendationGenerator;

    public ContentWorkflowOrchestrator(
            TopicService topicService,
            DeepResearchService researchService,
            ImageService imageService,
            ArticleAssemblyService assemblyService,
            ReviewService reviewService,
            TildaPublishingService publishingService,
            PsychologyTestGenerator testGenerator,
            ArtTherapyGenerator artTherapyGenerator,
            RecommendationListGenerator recommendationGenerator) {
        this.topicService = topicService;
        this.researchService = researchService;
        this.imageService = imageService;
        this.assemblyService = assemblyService;
        this.reviewService = reviewService;
        this.publishingService = publishingService;
        this.testGenerator = testGenerator;
        this.artTherapyGenerator = artTherapyGenerator;
        this.recommendationGenerator = recommendationGenerator;
    }

    public Article executeWorkflow(Long topicId) {
        logger.info("=== Starting Content Workflow for topic: {} ===", topicId);

        try {
            // Step 1: Get Topic
            List<Topic> topics = topicService.getTopicsByStatus(Topic.TopicStatus.APPROVED);
            Topic topic = topics.stream()
                    .filter(t -> t.getId().equals(topicId))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Topic not found: " + topicId));

            logger.info("[1/7] Topic loaded: {} ({})", topic.getTitle(), topic.getCategory());

            // Step 2: Deep Research
            logger.info("[2/7] Conducting deep research...");
            ResearchData research = researchService.conductResearch(
                    topicId, topic.getTitle(), null);

            // Step 3: Image Selection
            logger.info("[3/7] Selecting images...");
            List<ImageData> images = imageService.searchPexelsImages(
                    topicId, topic.getPrimaryKeyword());

            // Step 4: Article Assembly
            logger.info("[4/7] Assembling article...");
            Article article = assemblyService.assembleArticle(
                    topicId, topic.getTitle(),
                    convertCategory(topic.getCategory()),
                    research, images);

            // Step 5: Review
            logger.info("[5/7] Submitting for review...");
            ReviewData review = reviewService.submitForReview(
                    article.getId(), "editor-1");

            // Simulate review completion
            review = reviewService.approveArticle(article.getId(), "editor-1");
            article.setStatus(Article.ArticleStatus.APPROVED);

            // Step 6: Schedule Publication
            logger.info("[6/7] Scheduling publication...");
            LocalDateTime scheduled = topic.getScheduledDate() != null
                    ? topic.getScheduledDate()
                    : LocalDateTime.now().plusDays(1);
            publishingService.schedulePublication(article, scheduled);

            // Step 7: Publish
            logger.info("[7/7] Publishing to Tilda...");
            String publishedUrl = publishingService.publishToTilda(article);
            article.setStatus(Article.ArticleStatus.PUBLISHED);
            article.setPublishedAt(LocalDateTime.now());

            logger.info("=== Workflow Complete: Published at {} ===", publishedUrl);

            return article;

        } catch (Exception e) {
            logger.error("Workflow failed: {}", e.getMessage(), e);
            throw new RuntimeException("Content workflow failed", e);
        }
    }

    public Article generateContentByType(Topic topic) {
        logger.info("Generating content for type: {} - {}", topic.getCategory(), topic.getTitle());

        switch (topic.getCategory()) {
            case PSYCHOLOGY_TEST:
                return testGenerator.generateTestArticle(
                        topic.getTitle(), topic.getDescription());
            case ART_THERAPY:
                return artTherapyGenerator.generateActivityArticle(
                        topic.getTitle(), "anxiety");
            case RECOMMENDATION_LIST:
                return recommendationGenerator.generateListArticle(
                        topic.getTitle(), "2025");
            default:
                throw new IllegalArgumentException("Unknown category: " + topic.getCategory());
        }
    }

    private ArticleAssemblyService.TopicCategory convertCategory(Topic.TopicCategory category) {
        switch (category) {
            case PSYCHOLOGY_TEST:
                return ArticleAssemblyService.TopicCategory.PSYCHOLOGY_TEST;
            case ART_THERAPY:
                return ArticleAssemblyService.TopicCategory.ART_THERAPY;
            case RECOMMENDATION_LIST:
                return ArticleAssemblyService.TopicCategory.RECOMMENDATION_LIST;
            default:
                throw new IllegalArgumentException("Unknown category");
        }
    }

    public List<Article> batchProcess(List<Long> topicIds) {
        List<Article> results = new ArrayList<>();
        for (Long topicId : topicIds) {
            try {
                Article article = executeWorkflow(topicId);
                results.add(article);
            } catch (Exception e) {
                logger.error("Failed to process topic {}: {}", topicId, e.getMessage());
            }
        }
        return results;
    }

    public void scheduleDailyPublishing() {
        logger.info("Scheduling daily publishing job");
        List<Topic> ready = topicService.getReadyForPublishing(LocalDateTime.now());
        logger.info("Found {} articles ready for publishing today", ready.size());

        for (Topic topic : ready) {
            try {
                executeWorkflow(topic.getId());
            } catch (Exception e) {
                logger.error("Failed to publish topic {}: {}", topic.getId(), e.getMessage());
            }
        }
    }
}
