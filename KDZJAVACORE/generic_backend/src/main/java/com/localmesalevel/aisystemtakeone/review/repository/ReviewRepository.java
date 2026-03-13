package com.localmesalevel.aisystemtakeone.review.repository;

import com.localmesalevel.aisystemtakeone.review.model.ReviewData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReviewRepository extends JpaRepository<ReviewData, Long> {
    List<ReviewData> findByArticleId(Long articleId);
}
