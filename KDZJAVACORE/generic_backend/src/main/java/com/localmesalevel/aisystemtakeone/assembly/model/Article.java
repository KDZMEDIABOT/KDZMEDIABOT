package com.localmesalevel.aisystemtakeone.assembly.model;

import com.localmesalevel.aisystemtakeone.image.model.ImageData;
import com.localmesalevel.aisystemtakeone.topic.model.Topic;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "articles")
public class Article {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long topicId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String slug;

    @Column(columnDefinition = "TEXT")
    private String content;

    @ElementCollection
    @CollectionTable(name = "article_sections", joinColumns = @JoinColumn(name = "article_id"))
    private List<Section> sections;

    @Column
    private String internalLinks;

    @Column
    private String externalLinks;

    @Embedded
    @AttributeOverride(name = "metaDescription", column = @Column(name = "meta_description", columnDefinition = "TEXT"))
    private SeoMetadata seoMetadata;

    @Embedded
    private FaqSection faqSection;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ArticleStatus status = ArticleStatus.DRAFT;

    @Column
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column
    private LocalDateTime publishedAt;

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTopicId() { return topicId; }
    public void setTopicId(Long topicId) { this.topicId = topicId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    /** Delegates to embedded seoMetadata (single DB column meta_description). */
    public String getMetaDescription() { return seoMetadata != null ? seoMetadata.getMetaDescription() : null; }
    public void setMetaDescription(String metaDescription) {
        if (seoMetadata == null) seoMetadata = new SeoMetadata();
        seoMetadata.setMetaDescription(metaDescription);
    }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public List<Section> getSections() { return sections; }
    public void setSections(List<Section> sections) { this.sections = sections; }
    public String getInternalLinks() { return internalLinks; }
    public void setInternalLinks(String internalLinks) { this.internalLinks = internalLinks; }
    public String getExternalLinks() { return externalLinks; }
    public void setExternalLinks(String externalLinks) { this.externalLinks = externalLinks; }
    public SeoMetadata getSeoMetadata() { return seoMetadata; }
    public void setSeoMetadata(SeoMetadata seoMetadata) { this.seoMetadata = seoMetadata; }
    public FaqSection getFaqSection() { return faqSection; }
    public void setFaqSection(FaqSection faqSection) { this.faqSection = faqSection; }
    public ArticleStatus getStatus() { return status; }
    public void setStatus(ArticleStatus status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(LocalDateTime publishedAt) { this.publishedAt = publishedAt; }

    public enum ArticleStatus {
        DRAFT,
        UNDER_REVIEW,
        APPROVED,
        PUBLISHED,
        REJECTED
    }

    @Embeddable
    public static class Section {
        private String type; // H2, H3, LIST, QUOTE, PARAGRAPH
        @Column(columnDefinition = "TEXT")
        private String content;
        private Integer orderIndex;

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public Integer getOrderIndex() { return orderIndex; }
        public void setOrderIndex(Integer orderIndex) { this.orderIndex = orderIndex; }
    }

    @Embeddable
    public static class SeoMetadata {
        private String seoTitle;
        private String metaDescription;
        private String urlSlug;
        private String primaryKeyword;
        private String secondaryKeywords;

        public String getSeoTitle() { return seoTitle; }
        public void setSeoTitle(String seoTitle) { this.seoTitle = seoTitle; }
        public String getMetaDescription() { return metaDescription; }
        public void setMetaDescription(String metaDescription) { this.metaDescription = metaDescription; }
        public String getUrlSlug() { return urlSlug; }
        public void setUrlSlug(String urlSlug) { this.urlSlug = urlSlug; }
        public String getPrimaryKeyword() { return primaryKeyword; }
        public void setPrimaryKeyword(String primaryKeyword) { this.primaryKeyword = primaryKeyword; }
        public String getSecondaryKeywords() { return secondaryKeywords; }
        public void setSecondaryKeywords(String secondaryKeywords) { this.secondaryKeywords = secondaryKeywords; }
    }

    @Embeddable
    public static class FaqSection {
        @ElementCollection
        private List<FaqItem> items;

        public List<FaqItem> getItems() { return items; }
        public void setItems(List<FaqItem> items) { this.items = items; }
    }

    @Embeddable
    public static class FaqItem {
        private String question;
        private String answer;

        public String getQuestion() { return question; }
        public void setQuestion(String question) { this.question = question; }
        public String getAnswer() { return answer; }
        public void setAnswer(String answer) { this.answer = answer; }
    }
}
