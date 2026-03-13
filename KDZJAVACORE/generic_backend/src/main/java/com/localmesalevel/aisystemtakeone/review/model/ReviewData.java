package com.localmesalevel.aisystemtakeone.review.model;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "reviews")
public class ReviewData {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long articleId;

    @Column(nullable = false)
    private String reviewerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReviewDecision decision;

    @Column(columnDefinition = "TEXT")
    private String feedback;

    @Column
    private Integer qualityScore;

    @Column
    private String revisionNotes;

    @Column
    private LocalDateTime reviewedAt = LocalDateTime.now();

    @Column
    private LocalDateTime revisionDue;

    @Column
    private Integer revisionCount = 0;

    public enum ReviewDecision {
        APPROVED,
        APPROVED_WITH_EDITS,
        NEEDS_REVISION,
        REJECTED
    }

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getArticleId() { return articleId; }
    public void setArticleId(Long articleId) { this.articleId = articleId; }
    public String getReviewerId() { return reviewerId; }
    public void setReviewerId(String reviewerId) { this.reviewerId = reviewerId; }
    public ReviewDecision getDecision() { return decision; }
    public void setDecision(ReviewDecision decision) { this.decision = decision; }
    public String getFeedback() { return feedback; }
    public void setFeedback(String feedback) { this.feedback = feedback; }
    public Integer getQualityScore() { return qualityScore; }
    public void setQualityScore(Integer qualityScore) { this.qualityScore = qualityScore; }
    public String getRevisionNotes() { return revisionNotes; }
    public void setRevisionNotes(String revisionNotes) { this.revisionNotes = revisionNotes; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
    public LocalDateTime getRevisionDue() { return revisionDue; }
    public void setRevisionDue(LocalDateTime revisionDue) { this.revisionDue = revisionDue; }
    public Integer getRevisionCount() { return revisionCount; }
    public void setRevisionCount(Integer revisionCount) { this.revisionCount = revisionCount; }
}
