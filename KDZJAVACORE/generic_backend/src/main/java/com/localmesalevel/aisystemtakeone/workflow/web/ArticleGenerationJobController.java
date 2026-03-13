package com.localmesalevel.aisystemtakeone.workflow.web;

import com.localmesalevel.aisystemtakeone.workflow.model.ArticleGenerationJob;
import com.localmesalevel.aisystemtakeone.workflow.service.ArticleGenerationJobService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/article-generation-jobs")
public class ArticleGenerationJobController {

    private final ArticleGenerationJobService service;

    public ArticleGenerationJobController(ArticleGenerationJobService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<ArticleGenerationJobService.ArticleGenerationJobListItem>> listJobs() {
        return ResponseEntity.ok(service.listJobs());
    }

    @GetMapping("/running-kie-processes")
    public ResponseEntity<List<ArticleGenerationJobService.RunningKieProcessItem>> listRunningKieProcesses() {
        return ResponseEntity.ok(service.listRunningKieProcesses());
    }

    @PostMapping
    public ResponseEntity<?> createJob(@RequestBody CreateJobRequest request) {
        if (request == null || request.getTopic() == null || request.getTopic().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "topic is required"));
        }
        ArticleGenerationJob job = service.createJob(
            request.getTopic(),
            request.getContentType(),
            request.getRequesterRole(),
            request.getRequesterUserId()
        );
        return ResponseEntity.ok(job);
    }

    @PostMapping("/{jobId}/cancel")
    public ResponseEntity<?> cancelJob(@PathVariable Long jobId) {
        return service.cancelRunningJob(jobId)
            .<ResponseEntity<?>>map(job -> ResponseEntity.ok(job))
            .orElseGet(() -> ResponseEntity.status(404).body(Map.of("error", "job not found")));
    }

    @PostMapping("/{jobId}/restart")
    public ResponseEntity<?> restartCancelledJob(@PathVariable Long jobId) {
        ArticleGenerationJobService.RestartResult result = service.restartCancelledJob(jobId);
        if (result.getOutcome() == ArticleGenerationJobService.RestartResult.Outcome.NOT_FOUND) {
            return ResponseEntity.status(404).body(Map.of("error", "job not found"));
        }
        if (result.getOutcome() == ArticleGenerationJobService.RestartResult.Outcome.NOT_RESTARTABLE) {
            return ResponseEntity.status(409).body(Map.of("error", "only CANCELLED jobs can be restarted"));
        }
        return ResponseEntity.ok(result.getJob());
    }

    @PostMapping("/{jobId}/retry")
    public ResponseEntity<?> retryFailedJob(@PathVariable Long jobId) {
        ArticleGenerationJobService.RestartResult result = service.retryFailedJob(jobId);
        if (result.getOutcome() == ArticleGenerationJobService.RestartResult.Outcome.NOT_FOUND) {
            return ResponseEntity.status(404).body(Map.of("error", "job not found"));
        }
        if (result.getOutcome() == ArticleGenerationJobService.RestartResult.Outcome.NOT_RESTARTABLE) {
            return ResponseEntity.status(409).body(Map.of("error", "only FAILED jobs can be retried"));
        }
        return ResponseEntity.ok(result.getJob());
    }

    @DeleteMapping("/completed")
    public ResponseEntity<Map<String, Object>> forgetCompletedJobs() {
        long deleted = service.forgetCompletedJobs();
        return ResponseEntity.ok(Map.of("deletedCount", deleted));
    }

    public static class CreateJobRequest {
        private String topic;
        private String contentType;
        private String requesterRole;
        private Long requesterUserId;

        public String getTopic() {
            return topic;
        }

        public void setTopic(String topic) {
            this.topic = topic;
        }

        public String getContentType() {
            return contentType;
        }

        public void setContentType(String contentType) {
            this.contentType = contentType;
        }

        public String getRequesterRole() {
            return requesterRole;
        }

        public void setRequesterRole(String requesterRole) {
            this.requesterRole = requesterRole;
        }

        public Long getRequesterUserId() {
            return requesterUserId;
        }

        public void setRequesterUserId(Long requesterUserId) {
            this.requesterUserId = requesterUserId;
        }
    }
}
