package com.localmesalevel.aisystemtakeone.review.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.localmesalevel.aisystemtakeone.review.model.ReviewData;

@Service
public class ReviewService {

    private static final Logger logger = LoggerFactory.getLogger(ReviewService.class);
    private final Map<Long, List<ReviewData>> reviewHistory = new HashMap<>();

    public ReviewData submitForReview(Long articleId, String reviewerId) {
        logger.info("Article {} submitted for review by {}", articleId, reviewerId);

        ReviewData review = new ReviewData();
        review.setArticleId(articleId);
        review.setReviewerId(reviewerId);
        review.setDecision(ReviewData.ReviewDecision.NEEDS_REVISION);

        storeReview(review);
        return review;
    }

    public ReviewData approveArticle(Long articleId, String reviewerId) {
        logger.info("Article {} approved by {}", articleId, reviewerId);

        ReviewData review = new ReviewData();
        review.setArticleId(articleId);
        review.setReviewerId(reviewerId);
        review.setDecision(ReviewData.ReviewDecision.APPROVED);
        review.setQualityScore(85 + (int)(Math.random() * 15));

        storeReview(review);
        return review;
    }

    public ReviewData requestRevision(Long articleId, String reviewerId, String feedback) {
        logger.info("Article {} needs revision by {}: {}", articleId, reviewerId, feedback);

        ReviewData review = new ReviewData();
        review.setArticleId(articleId);
        review.setReviewerId(reviewerId);
        review.setDecision(ReviewData.ReviewDecision.NEEDS_REVISION);
        review.setFeedback(feedback);
        review.setRevisionNotes("Revision cycle started");
        review.setRevisionCount(getRevisionCount(articleId) + 1);

        storeReview(review);
        return review;
    }

    public boolean shouldTriggerAiIteration(Long articleId) {
        int revisions = getRevisionCount(articleId);
        logger.info("Article {} has {} revision cycles", articleId, revisions);
        return revisions > 0 && revisions <= 3; // Allow up to 3 AI iterations
    }

    public String generateRevisionPrompt(Long articleId) {
        List<ReviewData> history = reviewHistory.get(articleId);
        if (history == null || history.isEmpty()) {
            return "";
        }

        ReviewData lastReview = history.get(history.size() - 1);
        StringBuilder prompt = new StringBuilder();
        prompt.append("Please revise the article based on the following feedback:\n");
        prompt.append("Previous Feedback: ").append(lastReview.getFeedback()).append("\n");
        prompt.append("Revision Cycle: ").append(lastReview.getRevisionCount()).append("\n");
        prompt.append("Focus on improving: tone, accuracy, and engagement.");

        return prompt.toString();
    }

    public ReviewData approveWithEdits(Long articleId, String reviewerId, String editNotes) {
        logger.info("Article {} approved with edits by {}", articleId, reviewerId);

        ReviewData review = new ReviewData();
        review.setArticleId(articleId);
        review.setReviewerId(reviewerId);
        review.setDecision(ReviewData.ReviewDecision.APPROVED_WITH_EDITS);
        review.setRevisionNotes(editNotes);

        storeReview(review);
        return review;
    }

    public List<ReviewData> getReviewHistory(Long articleId) {
        return reviewHistory.getOrDefault(articleId, new ArrayList<>());
    }

    private void storeReview(ReviewData review) {
        reviewHistory.computeIfAbsent(review.getArticleId(), k -> new ArrayList<>()).add(review);
    }

    private int getRevisionCount(Long articleId) {
        List<ReviewData> history = reviewHistory.get(articleId);
        if (history == null) return 0;
        return (int) history.stream()
                .filter(r -> r.getDecision() == ReviewData.ReviewDecision.NEEDS_REVISION)
                .count();
    }
}
