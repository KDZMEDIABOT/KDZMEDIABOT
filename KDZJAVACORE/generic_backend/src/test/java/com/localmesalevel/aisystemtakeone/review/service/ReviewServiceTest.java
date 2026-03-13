package com.localmesalevel.aisystemtakeone.review.service;

import com.localmesalevel.aisystemtakeone.review.model.ReviewData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("dev")
class ReviewServiceTest {

    @Autowired
    private ReviewService reviewService;

    @BeforeEach
    void clearHistory() {
        // Service uses in-memory map, tests are isolated
    }

    @Test
    void submitForReview() {
        Long articleId = 1L;
        String reviewerId = "editor-1";

        ReviewData review = reviewService.submitForReview(articleId, reviewerId);

        assertNotNull(review);
        assertEquals(articleId, review.getArticleId());
        assertEquals(reviewerId, review.getReviewerId());
        assertEquals(ReviewData.ReviewDecision.NEEDS_REVISION, review.getDecision());
    }

    @Test
    void approveArticle() {
        Long articleId = 1L;
        reviewService.submitForReview(articleId, "editor-1");

        ReviewData review = reviewService.approveArticle(articleId, "editor-2");

        assertNotNull(review);
        assertEquals(ReviewData.ReviewDecision.APPROVED, review.getDecision());
        assertNotNull(review.getQualityScore());
        assertTrue(review.getQualityScore() >= 85);
    }

    @Test
    void requestRevision() {
        Long articleId = 1L;
        reviewService.submitForReview(articleId, "editor-1");

        ReviewData review = reviewService.requestRevision(articleId, "editor-1", "Needs more detail");

        assertNotNull(review);
        assertEquals(ReviewData.ReviewDecision.NEEDS_REVISION, review.getDecision());
        assertEquals("Needs more detail", review.getFeedback());
        assertEquals(1, review.getRevisionCount());
    }

    @Test
    void shouldTriggerAiIteration() {
        Long articleId = 1L;
        reviewService.submitForReview(articleId, "editor-1");

        // First revision
        reviewService.requestRevision(articleId, "editor-1", "First revision");

        boolean shouldTrigger = reviewService.shouldTriggerAiIteration(articleId);
        assertTrue(shouldTrigger);
    }

    @Test
    void generateRevisionPrompt() {
        Long articleId = 1L;
        reviewService.submitForReview(articleId, "editor-1");
        reviewService.requestRevision(articleId, "editor-1", "Add more statistics");

        String prompt = reviewService.generateRevisionPrompt(articleId);

        assertNotNull(prompt);
        assertTrue(prompt.contains("Add more statistics"));
        assertTrue(prompt.contains("Revision Cycle"));
    }

    @Test
    void approveWithEdits() {
        Long articleId = 1L;
        reviewService.submitForReview(articleId, "editor-1");

        ReviewData review = reviewService.approveWithEdits(articleId, "editor-1", "Minor grammar fixes");

        assertEquals(ReviewData.ReviewDecision.APPROVED_WITH_EDITS, review.getDecision());
        assertEquals("Minor grammar fixes", review.getRevisionNotes());
    }

    @Test
    void getReviewHistory() {
        Long articleId = 1L;
        reviewService.submitForReview(articleId, "editor-1");
        reviewService.approveArticle(articleId, "editor-1");

        List<ReviewData> history = reviewService.getReviewHistory(articleId);

        assertFalse(history.isEmpty());
        assertTrue(history.size() >= 2);
    }
}
