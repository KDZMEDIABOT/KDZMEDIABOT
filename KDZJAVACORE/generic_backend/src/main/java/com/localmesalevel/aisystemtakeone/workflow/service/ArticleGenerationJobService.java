package com.localmesalevel.aisystemtakeone.workflow.service;

import com.localmesalevel.aisystemtakeone.assembly.model.Article;
import com.localmesalevel.aisystemtakeone.assembly.repository.ArticleRepository;
import com.localmesalevel.aisystemtakeone.workflow.model.ArticleGenerationJob;
import com.localmesalevel.aisystemtakeone.workflow.repository.ArticleGenerationJobRepository;
import com.localmesalevel.aisystemtakeone.user.repository.UserAccountRepository;
import com.localmesalevel.aisystemtakeone.service.ArticleGeneratorImpl;
import org.springframework.beans.factory.annotation.Value;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.process.ProcessInstance;
import org.kie.api.KieServices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
public class ArticleGenerationJobService {

    private static final Logger logger = LoggerFactory.getLogger(ArticleGenerationJobService.class);
    private static final String DEFAULT_PROCESS_DEFINITION = "article.generation.v1";
    private static final String DEFAULT_CONTENT_TYPE = "ART_THERAPY";

    private final ArticleGenerationJobRepository repository;
    private final ArticleRepository articleRepository;
    private final UserAccountRepository userAccountRepository;
    private final ArticleGeneratorImpl articleGenerator;
    private final ThreadPoolTaskExecutor articleGenerationJobExecutor;
    private final String jbpmRuntimeMarker;
    private final AtomicLong localProcessIdSequence = new AtomicLong(System.currentTimeMillis());
    private final ConcurrentHashMap<Long, Future<?>> runningFutures = new ConcurrentHashMap<>();
    private final int articleGenerationJobsPoolSize;
    private final Queue<Long> articleGenerationJobsQueue = new ConcurrentLinkedQueue<>();
    private final java.util.Set<Long> queuedJobIds = ConcurrentHashMap.newKeySet();
    private final Queue<Long> persistedQueuedJobsCache = new ConcurrentLinkedQueue<>();
    private final java.util.Set<Long> persistedQueuedJobIds = ConcurrentHashMap.newKeySet();
    private final AtomicInteger articleGenerationJobsPoolInUse = new AtomicInteger(0);
    private volatile boolean persistedQueuedJobsCacheValid = false;

    public ArticleGenerationJobService(
        ArticleGenerationJobRepository repository,
        ArticleRepository articleRepository,
        UserAccountRepository userAccountRepository,
        ArticleGeneratorImpl articleGenerator,
        @Qualifier("articleGenerationJobExecutor") ThreadPoolTaskExecutor articleGenerationJobExecutor,
        @Value("${article-generation.jobs.pool-size:5}") int articleGenerationJobsPoolSize
    ) {
        this.repository = repository;
        this.articleRepository = articleRepository;
        this.userAccountRepository = userAccountRepository;
        this.articleGenerator = articleGenerator;
        this.articleGenerationJobExecutor = articleGenerationJobExecutor;
        this.articleGenerationJobsPoolSize = Math.max(articleGenerationJobsPoolSize, 1);
        this.jbpmRuntimeMarker = detectJbpmRuntimeMarker();
    }

    public List<ArticleGenerationJobListItem> listJobs() {
        return repository.findAllByOrderByCreatedAtDesc().stream()
            .map(job -> new ArticleGenerationJobListItem(job, resolveRequesterUsername(job.getRequesterUserId())))
            .collect(Collectors.toList());
    }

    public List<RunningKieProcessItem> listRunningKieProcesses() {
        return repository.findByStatusOrderByStartedAtDesc(ArticleGenerationJob.JobStatus.RUNNING).stream()
            .map(job -> new RunningKieProcessItem(job, resolveRequesterUsername(job.getRequesterUserId())))
            .collect(Collectors.toList());
    }

    @Transactional
    public ArticleGenerationJob createJob(String topic, String contentType, String requesterRole, Long requesterUserId) {
        ArticleGenerationJob job = new ArticleGenerationJob();
        job.setTopic(topic == null ? "" : topic.trim());
        job.setContentType(normalizeOrDefault(contentType, DEFAULT_CONTENT_TYPE));
        job.setRequesterRole(normalizeOrDefault(requesterRole, "unknown"));
        job.setRequesterUserId(requesterUserId);
        job.setProcessDefinitionId(DEFAULT_PROCESS_DEFINITION);
        job.setStatus(ArticleGenerationJob.JobStatus.QUEUED);

        ArticleGenerationJob saved = repository.save(job);
        invalidatePersistedQueuedJobsCache();
        logger.info("Created ArticleGenerationJob id={} process={} jbpm={}",
            saved.getId(), saved.getProcessDefinitionId(), jbpmRuntimeMarker);

        if (!hasAvailablePoolSlot()) {
            // Keep it explicitly queued in DB when runtime pool has no free slot.
            saved.setStatus(ArticleGenerationJob.JobStatus.QUEUED);
            saved = repository.save(saved);
            invalidatePersistedQueuedJobsCache();
            logger.info("ArticleGenerationJob id={} persisted as QUEUED because no pool slot is available", saved.getId());
        }

        enqueueJob(saved.getId());
        return saved;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void submitPendingJobsOnStartup() {
        // On backend startup, force re-sync of persisted QUEUED jobs from DB.
        invalidatePersistedQueuedJobsCache();
        List<ArticleGenerationJob> pendingJobs = repository.findByStatusInOrderByCreatedAtAsc(
            EnumSet.of(ArticleGenerationJob.JobStatus.QUEUED, ArticleGenerationJob.JobStatus.RUNNING)
        );
        if (pendingJobs.isEmpty()) {
            return;
        }
        logger.info("Found {} pending ArticleGenerationJob entries at startup; submitting to jBPM", pendingJobs.size());
        for (ArticleGenerationJob job : pendingJobs) {
            enqueueJob(job.getId());
        }
    }

    private void scheduleJobExecution(Long jobId) {
        articleGenerationJobsPoolInUse.incrementAndGet();
        Future<?> future = articleGenerationJobExecutor.submit(() -> {
            try {
                executeJob(jobId);
            } finally {
                runningFutures.remove(jobId);
                articleGenerationJobsPoolInUse.updateAndGet(current -> Math.max(current - 1, 0));
                drainQueuedJobs();
            }
        });
        runningFutures.put(jobId, future);
    }

    private synchronized void enqueueJob(Long jobId) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    enqueueJobNow(jobId);
                }
            });
            return;
        }
        enqueueJobNow(jobId);
    }

    private synchronized void enqueueJobNow(Long jobId) {
        if (runningFutures.containsKey(jobId) || queuedJobIds.contains(jobId)) {
            return;
        }
        if (articleGenerationJobsPoolInUse.get() >= articleGenerationJobsPoolSize) {
            persistQueuedJobInDatabase(jobId);
            return;
        }
        // Launch immediately when a pool slot is available.
        scheduleJobExecution(jobId);
    }

    private synchronized void drainQueuedJobs() {
        while (articleGenerationJobsPoolInUse.get() < articleGenerationJobsPoolSize) {
            Long nextQueuedJobId = articleGenerationJobsQueue.poll();
            if (nextQueuedJobId == null) {
                nextQueuedJobId = pollPersistedQueuedJobId();
                if (nextQueuedJobId == null) {
                    return;
                }
            }
            queuedJobIds.remove(nextQueuedJobId);

            Optional<ArticleGenerationJob> maybeJob = repository.findById(nextQueuedJobId);
            if (maybeJob.isEmpty()) {
                continue;
            }

            ArticleGenerationJob job = maybeJob.get();
            if (job.getStatus() != ArticleGenerationJob.JobStatus.QUEUED
                && job.getStatus() != ArticleGenerationJob.JobStatus.RUNNING) {
                continue;
            }

            if (runningFutures.containsKey(nextQueuedJobId)) {
                continue;
            }
            scheduleJobExecution(nextQueuedJobId);
        }
    }

    private void persistQueuedJobInDatabase(Long jobId) {
        Optional<ArticleGenerationJob> maybeJob = repository.findById(jobId);
        if (maybeJob.isEmpty()) {
            return;
        }
        ArticleGenerationJob job = maybeJob.get();
        if (job.getStatus() != ArticleGenerationJob.JobStatus.QUEUED) {
            job.setStatus(ArticleGenerationJob.JobStatus.QUEUED);
            repository.save(job);
            invalidatePersistedQueuedJobsCache();
        }
        invalidatePersistedQueuedJobsCache();
        logger.info("ArticleGenerationJob id={} kept in DB queue until a pool slot is free", jobId);
    }

    private synchronized Long pollPersistedQueuedJobId() {
        refreshPersistedQueuedJobsCacheIfNeeded();
        Long queuedJobId = persistedQueuedJobsCache.poll();
        if (queuedJobId != null) {
            persistedQueuedJobIds.remove(queuedJobId);
        }
        return queuedJobId;
    }

    private synchronized void removePersistedQueuedJobId(Long jobId) {
        persistedQueuedJobIds.remove(jobId);
        persistedQueuedJobsCache.remove(jobId);
    }

    private synchronized void invalidatePersistedQueuedJobsCache() {
        persistedQueuedJobsCacheValid = false;
        persistedQueuedJobIds.clear();
        persistedQueuedJobsCache.clear();
    }

    private synchronized void refreshPersistedQueuedJobsCacheIfNeeded() {
        if (persistedQueuedJobsCacheValid) {
            return;
        }
        persistedQueuedJobsCache.clear();
        persistedQueuedJobIds.clear();
        List<ArticleGenerationJob> queuedFromDb = repository.findByStatusInOrderByCreatedAtAsc(
            EnumSet.of(ArticleGenerationJob.JobStatus.QUEUED)
        );
        for (ArticleGenerationJob queuedJob : queuedFromDb) {
            Long queuedJobId = queuedJob.getId();
            if (queuedJobId != null && persistedQueuedJobIds.add(queuedJobId)) {
                persistedQueuedJobsCache.offer(queuedJobId);
            }
        }
        persistedQueuedJobsCacheValid = true;
    }

    private synchronized boolean hasAvailablePoolSlot() {
        return articleGenerationJobsPoolInUse.get() < articleGenerationJobsPoolSize;
    }

    protected void executeJob(Long jobId) {
        Optional<ArticleGenerationJob> maybeJob = repository.findById(jobId);
        if (maybeJob.isEmpty()) {
            logger.warn("ArticleGenerationJob {} no longer exists; skipping background execution", jobId);
            return;
        }

        ArticleGenerationJob job = maybeJob.get();
        if (job.getStatus() == ArticleGenerationJob.JobStatus.COMPLETED
            || job.getStatus() == ArticleGenerationJob.JobStatus.FAILED
            || job.getStatus() == ArticleGenerationJob.JobStatus.CANCELLED) {
            return;
        }

        try {
            Long processInstanceId = startProcessInJbpm(job);
            job.setProcessInstanceId(processInstanceId);
            repository.save(job);

            if (job.getStatus() == ArticleGenerationJob.JobStatus.CANCELLED || Thread.currentThread().isInterrupted()) {
                job.markCancelled("Cancelled before completion");
                repository.save(job);
                return;
            }

            // Persist RUNNING before entering runToCompletion so UI/state reflects active execution immediately.
            if (job.getStartedAt() == null) {
                job.setStartedAt(java.time.LocalDateTime.now());
            }
            job.setStatus(ArticleGenerationJob.JobStatus.RUNNING);
            job.setCompletedAt(null);
            job.setErrorMessage(null);
            repository.save(job);

            AtomicReference<Long> generatedArticleId = new AtomicReference<>(null);
            job.runToCompletion(() -> {
                Article generatedArticle = articleGenerator.generateArticle(buildGenerationRequest(job));
                Article savedArticle = articleRepository.save(generatedArticle);
                generatedArticleId.set(savedArticle.getId());
            });
            job.setArticleId(generatedArticleId.get());
            repository.save(job);
        } catch (Exception ex) {
            logger.error("Failed to execute ArticleGenerationJob id={} via jBPM", job.getId(), ex);
            job.markFailed("jBPM execution failed: " + ex.getMessage());
            repository.save(job);
        }
    }

    @Transactional
    public Optional<ArticleGenerationJob> cancelRunningJob(Long jobId) {
        Optional<ArticleGenerationJob> maybeJob = repository.findById(jobId);
        if (maybeJob.isEmpty()) {
            return Optional.empty();
        }

        ArticleGenerationJob job = maybeJob.get();
        if (job.getStatus() != ArticleGenerationJob.JobStatus.RUNNING
            && job.getStatus() != ArticleGenerationJob.JobStatus.QUEUED) {
            return Optional.of(job);
        }

        Future<?> future = runningFutures.remove(jobId);
        if (future != null) {
            future.cancel(true);
        }
        queuedJobIds.remove(jobId);
        articleGenerationJobsQueue.remove(jobId);
        removePersistedQueuedJobId(jobId);

        job.markCancelled("Cancelled by user");
        ArticleGenerationJob saved = repository.save(job);
        drainQueuedJobs();
        return Optional.of(saved);
    }

    @Transactional
    public RestartResult restartCancelledJob(Long jobId) {
        Optional<ArticleGenerationJob> maybeJob = repository.findById(jobId);
        if (maybeJob.isEmpty()) {
            return RestartResult.notFound();
        }

        ArticleGenerationJob job = maybeJob.get();
        if (job.getStatus() != ArticleGenerationJob.JobStatus.CANCELLED) {
            return RestartResult.notRestartable(job);
        }

        job.prepareForRestart();
        ArticleGenerationJob saved = repository.save(job);
        invalidatePersistedQueuedJobsCache();
        enqueueJob(saved.getId());
        return RestartResult.restarted(saved);
    }

    @Transactional
    public RestartResult retryFailedJob(Long jobId) {
        Optional<ArticleGenerationJob> maybeJob = repository.findById(jobId);
        if (maybeJob.isEmpty()) {
            return RestartResult.notFound();
        }

        ArticleGenerationJob job = maybeJob.get();
        if (job.getStatus() != ArticleGenerationJob.JobStatus.FAILED) {
            return RestartResult.notRestartable(job);
        }

        job.prepareForRestart();
        ArticleGenerationJob saved = repository.save(job);
        invalidatePersistedQueuedJobsCache();
        enqueueJob(saved.getId());
        return RestartResult.restarted(saved);
    }

    @Transactional
    public long forgetCompletedJobs() {
        return repository.deleteByStatusIn(EnumSet.of(
            ArticleGenerationJob.JobStatus.COMPLETED,
            ArticleGenerationJob.JobStatus.CANCELLED,
            ArticleGenerationJob.JobStatus.FAILED
        ));
    }

    private Long startProcessInJbpm(ArticleGenerationJob job) {
        KieSession kieSession = null;
        try {
            KieServices kieServices = KieServices.Factory.get();
            KieContainer container = kieServices.getKieClasspathContainer();
            kieSession = container.newKieSession();
            Map<String, Object> params = new HashMap<>();
            params.put("jobId", job.getId());
            params.put("topic", job.getTopic());
            params.put("contentType", job.getContentType());
            params.put("requesterRole", job.getRequesterRole());
            params.put("requesterUserId", job.getRequesterUserId());

            ProcessInstance processInstance = kieSession.startProcess(job.getProcessDefinitionId(), params);
            return processInstance.getId();
        } catch (RuntimeException ex) {
            logger.warn("jBPM process '{}' did not start for job {}. Falling back to local process id.",
                job.getProcessDefinitionId(), job.getId(), ex);
            return localProcessIdSequence.incrementAndGet();
        } finally {
            if (kieSession != null) {
                try {
                    kieSession.dispose();
                } catch (Exception ignored) {
                    // No-op cleanup guard.
                }
            }
        }
    }

    private String detectJbpmRuntimeMarker() {
        try {
            KieServices kieServices = KieServices.Factory.get();
            return kieServices.getClass().getSimpleName();
        } catch (Exception ex) {
            logger.warn("jBPM/KIE runtime is unavailable: {}", ex.getMessage());
            return "unavailable";
        }
    }

    private String normalizeOrDefault(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String resolveRequesterUsername(Long requesterUserId) {
        if (requesterUserId == null) {
            return "unknown";
        }
        Optional<com.localmesalevel.aisystemtakeone.user.model.UserAccount> user = userAccountRepository.findById(requesterUserId);
        return user.map(com.localmesalevel.aisystemtakeone.user.model.UserAccount::getUsername).orElse("unknown");
    }

    private ArticleGeneratorImpl.GenerationRequest buildGenerationRequest(ArticleGenerationJob job) {
        ArticleGeneratorImpl.GenerationRequest request = new ArticleGeneratorImpl.GenerationRequest();
        request.setTopicId(job.getId());
        request.setTopic(job.getTopic());
        request.setPrimaryKeyword(job.getTopic());
        request.setContentType(mapContentType(job.getContentType()));
        request.setRequesterUserId(job.getRequesterUserId());
        request.setIncludeFaq(true);
        return request;
    }

    private ArticleGeneratorImpl.ContentType mapContentType(String value) {
        if (value == null) {
            return ArticleGeneratorImpl.ContentType.ART_THERAPY;
        }
        try {
            return ArticleGeneratorImpl.ContentType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return ArticleGeneratorImpl.ContentType.ART_THERAPY;
        }
    }

    public static class ArticleGenerationJobListItem {
        private final Long id;
        private final String topic;
        private final String contentType;
        private final ArticleGenerationJob.JobStatus status;
        private final String processDefinitionId;
        private final String requesterRole;
        private final Long requesterUserId;
        private final String requesterUsername;
        private final java.time.LocalDateTime createdAt;
        private final String errorMessage;
        private final Long articleId;

        public ArticleGenerationJobListItem(ArticleGenerationJob job, String requesterUsername) {
            this.id = job.getId();
            this.topic = job.getTopic();
            this.contentType = job.getContentType();
            this.status = job.getStatus();
            this.processDefinitionId = job.getProcessDefinitionId();
            this.requesterRole = job.getRequesterRole();
            this.requesterUserId = job.getRequesterUserId();
            this.requesterUsername = requesterUsername;
            this.createdAt = job.getCreatedAt();
            this.errorMessage = job.getErrorMessage();
            this.articleId = job.getArticleId();
        }

        public Long getId() { return id; }
        public String getTopic() { return topic; }
        public String getContentType() { return contentType; }
        public ArticleGenerationJob.JobStatus getStatus() { return status; }
        public String getProcessDefinitionId() { return processDefinitionId; }
        public String getRequesterRole() { return requesterRole; }
        public Long getRequesterUserId() { return requesterUserId; }
        public String getRequesterUsername() { return requesterUsername; }
        public java.time.LocalDateTime getCreatedAt() { return createdAt; }
        public String getErrorMessage() { return errorMessage; }
        public Long getArticleId() { return articleId; }
    }

    public static class RunningKieProcessItem {
        private final Long processInstanceId;
        private final String processDefinitionId;
        private final Long jobId;
        private final String topic;
        private final String contentType;
        private final String requesterRole;
        private final Long requesterUserId;
        private final String requesterUsername;
        private final java.time.LocalDateTime startedAt;

        public RunningKieProcessItem(ArticleGenerationJob job, String requesterUsername) {
            this.processInstanceId = job.getProcessInstanceId();
            this.processDefinitionId = job.getProcessDefinitionId();
            this.jobId = job.getId();
            this.topic = job.getTopic();
            this.contentType = job.getContentType();
            this.requesterRole = job.getRequesterRole();
            this.requesterUserId = job.getRequesterUserId();
            this.requesterUsername = requesterUsername;
            this.startedAt = job.getStartedAt();
        }

        public Long getProcessInstanceId() { return processInstanceId; }
        public String getProcessDefinitionId() { return processDefinitionId; }
        public Long getJobId() { return jobId; }
        public String getTopic() { return topic; }
        public String getContentType() { return contentType; }
        public String getRequesterRole() { return requesterRole; }
        public Long getRequesterUserId() { return requesterUserId; }
        public String getRequesterUsername() { return requesterUsername; }
        public java.time.LocalDateTime getStartedAt() { return startedAt; }
    }

    public static class RestartResult {
        public enum Outcome {
            RESTARTED,
            NOT_FOUND,
            NOT_RESTARTABLE
        }

        private final Outcome outcome;
        private final ArticleGenerationJob job;

        private RestartResult(Outcome outcome, ArticleGenerationJob job) {
            this.outcome = outcome;
            this.job = job;
        }

        public static RestartResult restarted(ArticleGenerationJob job) {
            return new RestartResult(Outcome.RESTARTED, job);
        }

        public static RestartResult notFound() {
            return new RestartResult(Outcome.NOT_FOUND, null);
        }

        public static RestartResult notRestartable(ArticleGenerationJob job) {
            return new RestartResult(Outcome.NOT_RESTARTABLE, job);
        }

        public Outcome getOutcome() {
            return outcome;
        }

        public ArticleGenerationJob getJob() {
            return job;
        }
    }
}
