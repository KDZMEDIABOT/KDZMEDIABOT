package com.localmesalevel.aisystemtakeone.topic.model;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "topics")
public class Topic {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TopicCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TopicStatus status = TopicStatus.PENDING;

    @Column
    private String primaryKeyword;

    @Column
    private String secondaryKeywords;

    @Column
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column
    private LocalDateTime scheduledDate;

    @Column
    private Integer batchNumber;

    public Topic() {}

    public Topic(String title, TopicCategory category, String primaryKeyword) {
        this.title = title;
        this.category = category;
        this.primaryKeyword = primaryKeyword;
    }

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public TopicCategory getCategory() { return category; }
    public void setCategory(TopicCategory category) { this.category = category; }

    public TopicStatus getStatus() { return status; }
    public void setStatus(TopicStatus status) { this.status = status; }

    public String getPrimaryKeyword() { return primaryKeyword; }
    public void setPrimaryKeyword(String primaryKeyword) { this.primaryKeyword = primaryKeyword; }

    public String getSecondaryKeywords() { return secondaryKeywords; }
    public void setSecondaryKeywords(String secondaryKeywords) { this.secondaryKeywords = secondaryKeywords; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getScheduledDate() { return scheduledDate; }
    public void setScheduledDate(LocalDateTime scheduledDate) { this.scheduledDate = scheduledDate; }

    public Integer getBatchNumber() { return batchNumber; }
    public void setBatchNumber(Integer batchNumber) { this.batchNumber = batchNumber; }

    public enum TopicCategory {
        PSYCHOLOGY_TEST,
        ART_THERAPY,
        RECOMMENDATION_LIST
    }

    public enum TopicStatus {
        PENDING,
        APPROVED,
        REJECTED,
        IN_PROGRESS,
        COMPLETED
    }
}
