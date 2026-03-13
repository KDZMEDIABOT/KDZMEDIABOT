package com.localmesalevel.aisystemtakeone.workflow.web;

import com.localmesalevel.aisystemtakeone.assembly.model.Article;
import com.localmesalevel.aisystemtakeone.workflow.service.ContentWorkflowOrchestrator;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/workflow")
public class WorkflowController {

    private final ContentWorkflowOrchestrator orchestrator;

    public WorkflowController(ContentWorkflowOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @PostMapping("/execute/{topicId}")
    public ResponseEntity<Article> executeWorkflow(@PathVariable Long topicId) {
        Article article = orchestrator.executeWorkflow(topicId);
        return ResponseEntity.ok(article);
    }

    @PostMapping("/batch")
    public ResponseEntity<List<Article>> batchProcess(@RequestBody List<Long> topicIds) {
        List<Article> articles = orchestrator.batchProcess(topicIds);
        return ResponseEntity.ok(articles);
    }

    @PostMapping("/schedule")
    public ResponseEntity<Map<String, String>> scheduleDaily() {
        orchestrator.scheduleDailyPublishing();
        Map<String, String> response = new HashMap<>();
        response.put("status", "scheduled");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "UP");
        return ResponseEntity.ok(response);
    }
}
