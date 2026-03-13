package com.localmesalevel.aisystemtakeone.workflow.repository;

import com.localmesalevel.aisystemtakeone.workflow.model.ArticleGenerationJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface ArticleGenerationJobRepository extends JpaRepository<ArticleGenerationJob, Long> {
    List<ArticleGenerationJob> findAllByOrderByCreatedAtDesc();
    List<ArticleGenerationJob> findByStatusInOrderByCreatedAtAsc(Collection<ArticleGenerationJob.JobStatus> statuses);
    List<ArticleGenerationJob> findByStatusOrderByStartedAtDesc(ArticleGenerationJob.JobStatus status);
    long deleteByStatus(ArticleGenerationJob.JobStatus status);
    long deleteByStatusIn(Collection<ArticleGenerationJob.JobStatus> statuses);
}
