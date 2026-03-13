package com.localmesalevel.aisystemtakeone.topic.service;

import com.localmesalevel.aisystemtakeone.topic.model.Topic;
import com.localmesalevel.aisystemtakeone.topic.repository.TopicRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
public class TopicService {

    private static final Logger logger = LoggerFactory.getLogger(TopicService.class);
    private final TopicRepository topicRepository;

    public TopicService(TopicRepository topicRepository) {
        this.topicRepository = topicRepository;
    }

    public Topic createTopic(String title, Topic.TopicCategory category, String primaryKeyword) {
        Topic topic = new Topic(title, category, primaryKeyword);
        logger.info("Creating topic: {} in category: {}", title, category);
        return topicRepository.save(topic);
    }

    public List<Topic> createBatch(List<Topic> topics) {
        int batchNumber = getNextBatchNumber();
        topics.forEach(t -> t.setBatchNumber(batchNumber));
        logger.info("Creating batch of {} topics with batch number: {}", topics.size(), batchNumber);
        return topicRepository.saveAll(topics);
    }

    private int getNextBatchNumber() {
        // In production, this would query for the max batch number
        return (int) (System.currentTimeMillis() % 10000);
    }

    public List<Topic> getAllTopics() {
        return topicRepository.findAll();
    }

    public List<Topic> getTopicsByStatus(Topic.TopicStatus status) {
        return topicRepository.findByStatus(status);
    }

    public Topic approveTopic(Long topicId) {
        Topic topic = topicRepository.findById(topicId)
            .orElseThrow(() -> new IllegalArgumentException("Topic not found: " + topicId));
        topic.setStatus(Topic.TopicStatus.APPROVED);
        logger.info("Approved topic: {}", topicId);
        return topicRepository.save(topic);
    }

    public Topic rejectTopic(Long topicId, String reason) {
        Topic topic = topicRepository.findById(topicId)
            .orElseThrow(() -> new IllegalArgumentException("Topic not found: " + topicId));
        topic.setStatus(Topic.TopicStatus.REJECTED);
        logger.info("Rejected topic: {} - Reason: {}", topicId, reason);
        return topicRepository.save(topic);
    }

    public void scheduleTopic(Long topicId, LocalDateTime scheduledDate) {
        Topic topic = topicRepository.findById(topicId)
            .orElseThrow(() -> new IllegalArgumentException("Topic not found: " + topicId));
        topic.setScheduledDate(scheduledDate);
        topicRepository.save(topic);
    }

    public List<Topic> getReadyForPublishing(LocalDateTime date) {
        return topicRepository.findReadyForPublishing(date);
    }
}
