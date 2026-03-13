package com.localmesalevel.aisystemtakeone.image.model;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "images")
public class ImageData {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long topicId;

    @Column(nullable = false)
    private String url;

    @Column
    private String altText;

    @Column
    private String caption;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ImageSource source;

    @Column
    private String pexelsId;

    @Column
    private String photographerName;

    @Column
    private String photographerUrl;

    @Column
    private Integer relevanceScore;

    @Column
    private LocalDateTime createdAt = LocalDateTime.now();

    public enum ImageSource {
        PEXELS,
        AI_GENERATED,
        STOCK_OTHER
    }

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTopicId() { return topicId; }
    public void setTopicId(Long topicId) { this.topicId = topicId; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getAltText() { return altText; }
    public void setAltText(String altText) { this.altText = altText; }
    public String getCaption() { return caption; }
    public void setCaption(String caption) { this.caption = caption; }
    public ImageSource getSource() { return source; }
    public void setSource(ImageSource source) { this.source = source; }
    public String getPexelsId() { return pexelsId; }
    public void setPexelsId(String pexelsId) { this.pexelsId = pexelsId; }
    public String getPhotographerName() { return photographerName; }
    public void setPhotographerName(String photographerName) { this.photographerName = photographerName; }
    public String getPhotographerUrl() { return photographerUrl; }
    public void setPhotographerUrl(String photographerUrl) { this.photographerUrl = photographerUrl; }
    public Integer getRelevanceScore() { return relevanceScore; }
    public void setRelevanceScore(Integer relevanceScore) { this.relevanceScore = relevanceScore; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
