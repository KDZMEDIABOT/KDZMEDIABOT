package com.localmesalevel.aisystemtakeone.topic.repository;

import com.localmesalevel.aisystemtakeone.topic.model.Topic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TopicRepository extends JpaRepository<Topic, Long> {

    List<Topic> findByStatus(Topic.TopicStatus status);

    List<Topic> findByCategory(Topic.TopicCategory category);

    List<Topic> findByBatchNumber(Integer batchNumber);

    @Query("SELECT t FROM Topic t WHERE t.scheduledDate BETWEEN :start AND :end ORDER BY t.scheduledDate")
    List<Topic> findScheduledBetween(LocalDateTime start, LocalDateTime end);

    @Query("SELECT t FROM Topic t WHERE t.status = 'APPROVED' AND t.scheduledDate <= :date ORDER BY t.scheduledDate")
    List<Topic> findReadyForPublishing(LocalDateTime date);
}
