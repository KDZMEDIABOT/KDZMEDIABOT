package com.localmesalevel.aisystemtakeone.workflow.service;

import com.localmesalevel.aisystemtakeone.assembly.model.Article;
import com.localmesalevel.aisystemtakeone.assembly.repository.ArticleRepository;
import com.localmesalevel.aisystemtakeone.service.ArticleGeneratorImpl;
import com.localmesalevel.aisystemtakeone.user.repository.UserAccountRepository;
import com.localmesalevel.aisystemtakeone.workflow.model.ArticleGenerationJob;
import com.localmesalevel.aisystemtakeone.workflow.repository.ArticleGenerationJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ArticleGenerationJobServiceQueueTest {

    private ArticleGenerationJobRepository repository;
    private ThreadPoolTaskExecutor taskExecutor;
    private ArticleGenerationJobService service;
    private List<Runnable> submittedRunnables;
    private Map<Long, ArticleGenerationJob> jobs;
    private AtomicLong sequence;

    @BeforeEach
    void setUp() {
        repository = mock(ArticleGenerationJobRepository.class);
        ArticleRepository articleRepository = mock(ArticleRepository.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        ArticleGeneratorImpl articleGenerator = mock(ArticleGeneratorImpl.class);
        taskExecutor = mock(ThreadPoolTaskExecutor.class);

        submittedRunnables = new ArrayList<>();
        jobs = new ConcurrentHashMap<>();
        sequence = new AtomicLong(0L);

        when(repository.save(any(ArticleGenerationJob.class))).thenAnswer(invocation -> {
            ArticleGenerationJob job = invocation.getArgument(0);
            if (job.getId() == null) {
                job.setId(sequence.incrementAndGet());
            }
            jobs.put(job.getId(), job);
            return job;
        });
        when(repository.findById(any(Long.class))).thenAnswer(invocation ->
            Optional.ofNullable(jobs.get(invocation.getArgument(0)))
        );
        when(repository.findByStatusInOrderByCreatedAtAsc(eq(EnumSet.of(ArticleGenerationJob.JobStatus.QUEUED))))
            .thenAnswer(invocation -> jobs.values().stream()
                .filter(job -> job.getStatus() == ArticleGenerationJob.JobStatus.QUEUED)
                .sorted((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()))
                .toList());

        when(taskExecutor.submit(any(Runnable.class))).thenAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            submittedRunnables.add(runnable);
            return mock(Future.class);
        });

        // Keep tests focused on queue/pool orchestration, not generation internals.
        when(articleRepository.save(any(Article.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(articleGenerator.generateArticle(any(ArticleGeneratorImpl.GenerationRequest.class))).thenReturn(new Article());

        service = spy(new ArticleGenerationJobService(
            repository,
            articleRepository,
            userAccountRepository,
            articleGenerator,
            taskExecutor,
            1
        ));
        doAnswer(invocation -> null).when(service).executeJob(any(Long.class));
    }

    @Test
    void createJob_shouldPersistQueuedInDbWhenNoPoolSlot() {
        ArticleGenerationJob first = service.createJob("topic-1", "ART_THERAPY", "maintainer", 2L);
        ArticleGenerationJob second = service.createJob("topic-2", "ART_THERAPY", "maintainer", 2L);

        assertEquals(ArticleGenerationJob.JobStatus.QUEUED, first.getStatus());
        assertEquals(ArticleGenerationJob.JobStatus.QUEUED, second.getStatus());
        assertEquals(1, submittedRunnables.size(), "Only one job should start when pool size is 1");
        assertTrue(jobs.containsKey(second.getId()), "Second job must remain persisted in DB");
        assertEquals(ArticleGenerationJob.JobStatus.QUEUED, jobs.get(second.getId()).getStatus());
    }

    @Test
    void queuedJob_shouldAutoStartWhenSlotBecomesAvailable() {
        service.createJob("topic-1", "ART_THERAPY", "maintainer", 2L);
        ArticleGenerationJob queued = service.createJob("topic-2", "ART_THERAPY", "maintainer", 2L);

        assertEquals(1, submittedRunnables.size(), "Only first job is launched immediately");
        submittedRunnables.get(0).run(); // simulate first job completion

        verify(taskExecutor, times(2)).submit(any(Runnable.class));
        assertEquals(2, submittedRunnables.size(), "Second job should be auto-started after slot is free");
        assertEquals(ArticleGenerationJob.JobStatus.QUEUED, jobs.get(queued.getId()).getStatus());
    }
}
