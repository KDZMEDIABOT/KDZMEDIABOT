package com.localmesalevel.aisystemtakeone.assembly.repository;

import com.localmesalevel.aisystemtakeone.assembly.model.Article;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ArticleRepository extends JpaRepository<Article, Long> {
    List<Article> findByStatus(Article.ArticleStatus status);
    List<Article> findByTopicId(Long topicId);
}
