package com.localmesalevel.aisystemtakeone.assembly.web;

import com.localmesalevel.aisystemtakeone.assembly.model.Article;
import com.localmesalevel.aisystemtakeone.assembly.repository.ArticleRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/articles")
public class ArticleController {

    private final ArticleRepository articleRepository;

    public ArticleController(ArticleRepository articleRepository) {
        this.articleRepository = articleRepository;
    }

    @GetMapping
    public ResponseEntity<List<Article>> getAllArticles() {
        return ResponseEntity.ok(articleRepository.findAll());
    }

    @GetMapping("/{status}")
    public ResponseEntity<List<Article>> getArticlesByStatus(@PathVariable String status) {
        try {
            Article.ArticleStatus articleStatus = Article.ArticleStatus.valueOf(status.toUpperCase());
            return ResponseEntity.ok(articleRepository.findByStatus(articleStatus));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(List.of());
        }
    }

    @PostMapping
    public ResponseEntity<Article> createArticle(@RequestBody Article article) {
        return ResponseEntity.ok(articleRepository.save(article));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<Article> approveArticle(@PathVariable Long id) {
        Article article = articleRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Article not found: " + id));
        article.setStatus(Article.ArticleStatus.APPROVED);
        return ResponseEntity.ok(articleRepository.save(article));
    }

    @PostMapping("/{id}/revision")
    public ResponseEntity<Map<String, String>> requestRevision(@PathVariable Long id, @RequestBody Map<String, String> request) {
        Map<String, String> response = new HashMap<>();
        response.put("articleId", id.toString());
        response.put("message", "Revision requested");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "UP");
        return ResponseEntity.ok(response);
    }
}
