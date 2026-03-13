package com.localmesalevel.aisystemtakeone.service;

import com.localmesalevel.aisystemtakeone.assembly.model.Article;
import com.localmesalevel.aisystemtakeone.assembly.service.ArticleAssemblyService;
import com.localmesalevel.aisystemtakeone.contenttypes.service.ArtTherapyGenerator;
import com.localmesalevel.aisystemtakeone.contenttypes.service.PsychologyTestGenerator;
import com.localmesalevel.aisystemtakeone.contenttypes.service.RecommendationListGenerator;
import com.localmesalevel.aisystemtakeone.image.model.ImageData;
import com.localmesalevel.aisystemtakeone.image.service.ImageService;
import com.localmesalevel.aisystemtakeone.research.model.ResearchData;
import com.localmesalevel.aisystemtakeone.research.service.DeepResearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Generic article generation implementation based on the documented flow:
 * topic -> deep research -> E-E-A-T validation -> image selection/fallback ->
 * article assembly -> content-type enrichment -> SEO/FAQ finalization.
 */
@Service
public class ArticleGeneratorImpl {

    private static final Logger logger = LoggerFactory.getLogger(ArticleGeneratorImpl.class);
    private static final int DEFAULT_WORD_COUNT = 1800;

    private final DeepResearchService deepResearchService;
    private final ImageService imageService;
    private final ArticleAssemblyService articleAssemblyService;
    private final PsychologyTestGenerator psychologyTestGenerator;
    private final ArtTherapyGenerator artTherapyGenerator;
    private final RecommendationListGenerator recommendationListGenerator;

    public ArticleGeneratorImpl(
            DeepResearchService deepResearchService,
            ImageService imageService,
            ArticleAssemblyService articleAssemblyService,
            PsychologyTestGenerator psychologyTestGenerator,
            ArtTherapyGenerator artTherapyGenerator,
            RecommendationListGenerator recommendationListGenerator
    ) {
        this.deepResearchService = deepResearchService;
        this.imageService = imageService;
        this.articleAssemblyService = articleAssemblyService;
        this.psychologyTestGenerator = psychologyTestGenerator;
        this.artTherapyGenerator = artTherapyGenerator;
        this.recommendationListGenerator = recommendationListGenerator;
    }

    public Article generateArticle(GenerationRequest request) {
        GenerationRequest normalized = normalize(request);
        logger.info("Generating article for topic '{}' as {}", normalized.getTopic(), normalized.getContentType());

        // 1) Deep research (authoritative sources + citations).
        ResearchData research = deepResearchService.conductResearch(
                normalized.getTopicId(),
                normalized.getTopic(),
                normalized.getRequesterUserId()
        );
        if (!deepResearchService.validateEEAT(normalized.getTopicId(), research)) {
            throw new IllegalStateException("Research data failed E-E-A-T validation checks");
        }

        // 2) Image selection (Pexels first) with AI fallback.
        List<ImageData> selectedImages = new ArrayList<>(
                imageService.searchPexelsImages(normalized.getTopicId(), normalized.getPrimaryKeyword())
        );
        if (selectedImages.isEmpty()) {
            String fallbackPrompt = "Warm, empathetic mental health illustration for topic: " + normalized.getTopic();
            selectedImages.add(imageService.generateAiImage(normalized.getTopicId(), fallbackPrompt));
        }

        // 3) Base SEO-aware article assembly from generic layer.
        Article article = articleAssemblyService.assembleArticle(
                normalized.getTopicId(),
                normalized.getTopic(),
                toAssemblyCategory(normalized.getContentType()),
                research,
                selectedImages
        );

        // 4) Content-type enrichment (psych tests / art therapy / recommendations).
        Article typedTemplate = generateTypedTemplate(normalized);
        article.setSections(mergeSections(buildTableOfContentsSection(article), article.getSections(), typedTemplate.getSections()));

        // 5) Build text payload and finalize metadata/FAQ constraints.
        article.setContent(renderArticleContent(article, research, selectedImages, normalized));
        finalizeSeoMetadata(article, normalized);
        if (!normalized.isIncludeFaq()) {
            article.setFaqSection(null);
        }

        return article;
    }

    private Article generateTypedTemplate(GenerationRequest request) {
        switch (request.getContentType()) {
            case PSYCHOLOGY_TEST:
                return psychologyTestGenerator.generateTestArticle(request.getTopic(), "Customer-specific psychological self-assessment");
            case ART_THERAPY:
                return artTherapyGenerator.generateActivityArticle(request.getTopic(), "mental wellbeing");
            case RECOMMENDATION_LIST:
                return recommendationListGenerator.generateListArticle(request.getTopic(), "2026");
            default:
                throw new IllegalArgumentException("Unsupported content type: " + request.getContentType());
        }
    }

    private List<Article.Section> buildTableOfContentsSection(Article article) {
        if (article.getSections() == null || article.getSections().isEmpty()) {
            return Collections.emptyList();
        }

        List<String> headers = article.getSections().stream()
                .filter(Objects::nonNull)
                .filter(section -> "H2".equals(section.getType()) || "H3".equals(section.getType()))
                .map(Article.Section::getContent)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (headers.isEmpty()) {
            return Collections.emptyList();
        }

        Article.Section tocTitle = new Article.Section();
        tocTitle.setType("H2");
        tocTitle.setContent("Table of Contents");
        tocTitle.setOrderIndex(-2);

        Article.Section tocItems = new Article.Section();
        tocItems.setType("LIST");
        tocItems.setContent(headers.stream().map(h -> "- " + h).collect(Collectors.joining("\n")));
        tocItems.setOrderIndex(-1);

        List<Article.Section> result = new ArrayList<>();
        result.add(tocTitle);
        result.add(tocItems);
        return result;
    }

    private List<Article.Section> mergeSections(
            List<Article.Section> first,
            List<Article.Section> second,
            List<Article.Section> third
    ) {
        List<Article.Section> merged = new ArrayList<>();
        addSections(merged, first);
        addSections(merged, second);
        addSections(merged, third);

        int index = 0;
        for (Article.Section section : merged) {
            section.setOrderIndex(index++);
        }
        return merged;
    }

    private void addSections(List<Article.Section> target, List<Article.Section> source) {
        if (source == null) {
            return;
        }
        source.stream()
                .filter(Objects::nonNull)
                .forEach(target::add);
    }

    private String renderArticleContent(
            Article article,
            ResearchData research,
            List<ImageData> images,
            GenerationRequest request
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("Topic: ").append(request.getTopic()).append("\n");
        sb.append("Search intent: ").append(request.getSearchIntent()).append("\n");
        sb.append("Target word count: ").append(request.getTargetWordCount()).append("\n\n");

        if (article.getSections() != null) {
            for (Article.Section section : article.getSections()) {
                if (section == null || section.getContent() == null) {
                    continue;
                }
                sb.append(section.getContent()).append("\n\n");
            }
        }

        sb.append("Sources and citations:\n");
        if (research.getCitations() != null) {
            for (String citation : research.getCitations()) {
                sb.append("- ").append(citation).append("\n");
            }
        }

        if (images != null && !images.isEmpty()) {
            sb.append("\nImage assets:\n");
            for (ImageData image : images) {
                sb.append("- ").append(image.getUrl());
                if (image.getAltText() != null) {
                    sb.append(" (alt: ").append(image.getAltText()).append(")");
                }
                sb.append("\n");
            }
        }

        return sb.toString();
    }

    private void finalizeSeoMetadata(Article article, GenerationRequest request) {
        Article.SeoMetadata seo = article.getSeoMetadata() != null ? article.getSeoMetadata() : new Article.SeoMetadata();

        String baseTitle = request.getTopic();
        String seoTitle = baseTitle.length() > 60 ? baseTitle.substring(0, 60) : baseTitle;
        if (seoTitle.length() < 50) {
            seoTitle = (seoTitle + " | Mental Health Guide").substring(0, Math.min(60, (seoTitle + " | Mental Health Guide").length()));
        }

        String metaDescription = seo.getMetaDescription();
        if (metaDescription == null || metaDescription.isBlank()) {
            metaDescription = "Explore " + request.getTopic() + " with evidence-informed guidance, practical steps, and compassionate support.";
        }
        if (metaDescription.length() > 160) {
            metaDescription = metaDescription.substring(0, 160);
        }

        seo.setSeoTitle(seoTitle);
        seo.setMetaDescription(metaDescription);
        seo.setUrlSlug(slugify(request.getTopic()));
        seo.setPrimaryKeyword(request.getPrimaryKeyword());
        seo.setSecondaryKeywords(String.join(",", request.getSecondaryKeywords()));

        article.setSeoMetadata(seo);
        article.setSlug(seo.getUrlSlug());
        article.setTitle(seo.getSeoTitle());
        article.setMetaDescription(seo.getMetaDescription());
    }

    private String slugify(String value) {
        String slug = value == null ? "article" : value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
        return slug.isBlank() ? "article" : slug;
    }

    private ArticleAssemblyService.TopicCategory toAssemblyCategory(ContentType type) {
        switch (type) {
            case PSYCHOLOGY_TEST:
                return ArticleAssemblyService.TopicCategory.PSYCHOLOGY_TEST;
            case ART_THERAPY:
                return ArticleAssemblyService.TopicCategory.ART_THERAPY;
            case RECOMMENDATION_LIST:
                return ArticleAssemblyService.TopicCategory.RECOMMENDATION_LIST;
            default:
                throw new IllegalArgumentException("Unsupported content type: " + type);
        }
    }

    private GenerationRequest normalize(GenerationRequest request) {
        if (request == null || request.getTopic() == null || request.getTopic().isBlank()) {
            throw new IllegalArgumentException("Topic is required");
        }

        GenerationRequest normalized = new GenerationRequest();
        normalized.setTopicId(request.getTopicId() == null ? System.currentTimeMillis() : request.getTopicId());
        normalized.setTopic(request.getTopic().trim());
        normalized.setPrimaryKeyword(
                request.getPrimaryKeyword() == null || request.getPrimaryKeyword().isBlank()
                        ? request.getTopic().trim().toLowerCase(Locale.ROOT)
                        : request.getPrimaryKeyword().trim().toLowerCase(Locale.ROOT)
        );
        normalized.setSecondaryKeywords(
                request.getSecondaryKeywords() == null || request.getSecondaryKeywords().isEmpty()
                        ? List.of("mental health", "wellbeing", "self care")
                        : request.getSecondaryKeywords()
        );
        normalized.setSearchIntent(
                request.getSearchIntent() == null || request.getSearchIntent().isBlank()
                        ? "Informational"
                        : request.getSearchIntent().trim()
        );
        normalized.setTargetWordCount(
                request.getTargetWordCount() == null || request.getTargetWordCount() < 1500
                        ? DEFAULT_WORD_COUNT
                        : request.getTargetWordCount()
        );
        normalized.setContentType(request.getContentType() == null ? ContentType.ART_THERAPY : request.getContentType());
        normalized.setRequesterUserId(request.getRequesterUserId());
        normalized.setIncludeFaq(request.isIncludeFaq());
        return normalized;
    }

    public enum ContentType {
        PSYCHOLOGY_TEST,
        ART_THERAPY,
        RECOMMENDATION_LIST
    }

    public static class GenerationRequest {
        private Long topicId;
        private String topic;
        private String primaryKeyword;
        private List<String> secondaryKeywords;
        private String searchIntent;
        private Integer targetWordCount;
        private ContentType contentType;
        private Long requesterUserId;
        private boolean includeFaq = true;

        public Long getTopicId() {
            return topicId;
        }

        public void setTopicId(Long topicId) {
            this.topicId = topicId;
        }

        public String getTopic() {
            return topic;
        }

        public void setTopic(String topic) {
            this.topic = topic;
        }

        public String getPrimaryKeyword() {
            return primaryKeyword;
        }

        public void setPrimaryKeyword(String primaryKeyword) {
            this.primaryKeyword = primaryKeyword;
        }

        public List<String> getSecondaryKeywords() {
            return secondaryKeywords;
        }

        public void setSecondaryKeywords(List<String> secondaryKeywords) {
            this.secondaryKeywords = secondaryKeywords;
        }

        public String getSearchIntent() {
            return searchIntent;
        }

        public void setSearchIntent(String searchIntent) {
            this.searchIntent = searchIntent;
        }

        public Integer getTargetWordCount() {
            return targetWordCount;
        }

        public void setTargetWordCount(Integer targetWordCount) {
            this.targetWordCount = targetWordCount;
        }

        public ContentType getContentType() {
            return contentType;
        }

        public void setContentType(ContentType contentType) {
            this.contentType = contentType;
        }

        public Long getRequesterUserId() {
            return requesterUserId;
        }

        public void setRequesterUserId(Long requesterUserId) {
            this.requesterUserId = requesterUserId;
        }

        public boolean isIncludeFaq() {
            return includeFaq;
        }

        public void setIncludeFaq(boolean includeFaq) {
            this.includeFaq = includeFaq;
        }
    }
}
