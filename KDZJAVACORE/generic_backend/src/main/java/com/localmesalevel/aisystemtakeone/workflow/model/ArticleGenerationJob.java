package com.localmesalevel.aisystemtakeone.workflow.model;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "article_generation_jobs")
public class ArticleGenerationJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String topic;

    @Column(nullable = false, length = 64)
    private String contentType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private JobStatus status = JobStatus.QUEUED;

    @Column(nullable = false, length = 128)
    private String processDefinitionId = "article.generation.v1";

    @Column
    private Long processInstanceId;

    @Column
    private Long articleId;

    @Column(nullable = false, length = 64)
    private String requesterRole;

    @Column
    private Long requesterUserId;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column
    private LocalDateTime startedAt;

    @Column
    private LocalDateTime completedAt;

    @Column(length = 2000)
    private String errorMessage;

    public enum JobStatus {
        QUEUED,
        RUNNING,
        CANCELLED,
        COMPLETED,
        FAILED
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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

    public JobStatus getStatus() {
        return status;
    }

    public void setStatus(JobStatus status) {
        this.status = status;
    }

    public String getProcessDefinitionId() {
        return processDefinitionId;
    }

    public void setProcessDefinitionId(String processDefinitionId) {
        this.processDefinitionId = processDefinitionId;
    }

    public Long getProcessInstanceId() {
        return processInstanceId;
    }

    public void setProcessInstanceId(Long processInstanceId) {
        this.processInstanceId = processInstanceId;
    }

    public Long getArticleId() {
        return articleId;
    }

    public void setArticleId(Long articleId) {
        this.articleId = articleId;
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    /**
     * Complete the generation job synchronously in the current worker thread.
     * This method intentionally encapsulates state transitions so the service
     * can call it in a single place for both startup recovery and new launches.
     */
    public void runToCompletion(Runnable generationAction) {
        if (startedAt == null) {
            startedAt = LocalDateTime.now();
        }
        status = JobStatus.RUNNING;
        if (generationAction != null) {
            generationAction.run();
        }
        completedAt = LocalDateTime.now();
        status = JobStatus.COMPLETED;
        errorMessage = null;
    }

    public void markFailed(String message) {
        if (startedAt == null) {
            startedAt = LocalDateTime.now();
        }
        completedAt = LocalDateTime.now();
        status = JobStatus.FAILED;
        errorMessage = message;
    }

    public void markCancelled(String message) {
        if (startedAt == null) {
            startedAt = LocalDateTime.now();
        }
        completedAt = LocalDateTime.now();
        status = JobStatus.CANCELLED;
        errorMessage = message;
    }

    public void prepareForRestart() {
        status = JobStatus.QUEUED;
        startedAt = null;
        completedAt = null;
        errorMessage = null;
        processInstanceId = null;
        articleId = null;
    }
}
