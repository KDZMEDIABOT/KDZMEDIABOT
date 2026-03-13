package com.localmesalevel.aisystemtakeone.assembly.service;

import com.localmesalevel.aisystemtakeone.assembly.model.Article;
import com.localmesalevel.aisystemtakeone.image.model.ImageData;
import com.localmesalevel.aisystemtakeone.research.model.ResearchData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ArticleAssemblyService {

    private static final Logger logger = LoggerFactory.getLogger(ArticleAssemblyService.class);

    public Article assembleArticle(Long topicId, String topicTitle, TopicCategory category,
                                   ResearchData research, List<ImageData> images) {
        logger.info("Assembling article for topic: {}", topicTitle);

        Article article = new Article();
        article.setTopicId(topicId);
        article.setTitle(generateTitle(topicTitle));
        article.setSlug(generateSlug(topicTitle));
        article.setMetaDescription(generateMetaDescription(topicTitle, research));

        List<Article.Section> sections = new ArrayList<>();

        // Add sections based on template
        sections.add(createSection("H2", "Introduction", 0));
        sections.add(createSection("PARAGRAPH", "Hook opening...", 1));
        sections.add(createSection("H2", "What is " + topicTitle + "?", 2));
        sections.add(createSection("PARAGRAPH", research.getSummary(), 3));
        sections.add(createSection("H3", "Key Benefits", 4));
        sections.add(createSection("LIST", generateBenefitsList(category), 5));
        sections.add(createSection("QUOTE", research.getExpertQuotes(), 6));
        sections.add(createSection("H2", "How to Practice", 7));
        sections.add(createSection("PARAGRAPH", "Step-by-step guide...", 8));
        sections.add(createSection("H2", "Conclusion", 9));
        sections.add(createSection("PARAGRAPH", "Final thoughts...", 10));

        article.setSections(sections);
        article.setSeoMetadata(generateSeoMetadata(topicTitle, research));
        article.setFaqSection(generateFaqSection(topicTitle));
        article.setInternalLinks(generateInternalLinks(category));
        article.setExternalLinks(generateExternalLinks(research));

        return article;
    }

    private String generateTitle(String topic) {
        return topic + ": A Comprehensive Guide for Mental Health (2025)";
    }

    private String generateSlug(String title) {
        String candidate = title == null ? "" : title.toLowerCase()
            .replaceAll("[^a-z0-9\\s-]", "")
            .replaceAll("\\s+", "-")
            .replaceAll("-+", "-")
            .replaceAll("^-+", "")
            .replaceAll("-+$", "");

        if (candidate.isEmpty()) {
            // Non-latin-only titles can normalize to empty; keep slug generation resilient.
            return "article";
        }
        return candidate.substring(0, Math.min(candidate.length(), 60));
    }

    private String generateMetaDescription(String topic, ResearchData research) {
        String base = "Discover the benefits of " + topic +
                ". This comprehensive guide covers everything you need to know.";
        return base.substring(0, Math.min(base.length(), 160));
    }

    private Article.Section createSection(String type, String content, int order) {
        Article.Section section = new Article.Section();
        section.setType(type);
        section.setContent(content);
        section.setOrderIndex(order);
        return section;
    }

    private String generateBenefitsList(TopicCategory category) {
        switch (category) {
            case PSYCHOLOGY_TEST:
                return "- Self-awareness\\n- Early intervention\\n- Personal growth";
            case ART_THERAPY:
                return "- Stress reduction\\n- Emotional expression\\n- Creative outlet";
            case RECOMMENDATION_LIST:
                return "- Curated selection\\n- Expert reviewed\\n- Cost-effective";
            default:
                return "- General benefits";
        }
    }

    private Article.SeoMetadata generateSeoMetadata(String topic, ResearchData research) {
        Article.SeoMetadata metadata = new Article.SeoMetadata();
        metadata.setSeoTitle(topic.substring(0, Math.min(topic.length(), 60)));
        metadata.setMetaDescription(generateMetaDescription(topic, research));
        metadata.setUrlSlug(generateSlug(topic));
        metadata.setPrimaryKeyword(topic.toLowerCase().replace(" ", "-"));
        metadata.setSecondaryKeywords("mental-health,wellness,2025");
        return metadata;
    }

    private Article.FaqSection generateFaqSection(String topic) {
        Article.FaqSection faq = new Article.FaqSection();
        List<Article.FaqItem> items = new ArrayList<>();

        Article.FaqItem item1 = new Article.FaqItem();
        item1.setQuestion("What is " + topic + "?");
        item1.setAnswer("A therapeutic approach that helps with mental health and wellness.");
        items.add(item1);

        Article.FaqItem item2 = new Article.FaqItem();
        item2.setQuestion("How effective is " + topic + "?");
        item2.setAnswer("Research shows 85% of participants report positive outcomes.");
        items.add(item2);

        faq.setItems(items);
        return faq;
    }

    private String generateInternalLinks(TopicCategory category) {
        // Links to related articles
        return "/related-article-1,/related-article-2";
    }

    private String generateExternalLinks(ResearchData research) {
        // External authority sources
        return "https://apa.org,https://who.int";
    }

    public enum TopicCategory {
        PSYCHOLOGY_TEST,
        ART_THERAPY,
        RECOMMENDATION_LIST
    }
}
