package com.localmesalevel.aisystemtakeone.topic.service;

import com.localmesalevel.aisystemtakeone.topic.model.Topic;
import com.localmesalevel.aisystemtakeone.topic.repository.TopicRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("dev")
class TopicServiceTest {

    @Autowired
    private TopicService topicService;

    @Autowired
    private TopicRepository topicRepository;

    @BeforeEach
    void setUp() {
        topicRepository.deleteAll();
    }

    @Test
    void createTopic() {
        Topic topic = topicService.createTopic(
                "Test Topic",
                Topic.TopicCategory.PSYCHOLOGY_TEST,
                "test-keyword"
        );

        assertNotNull(topic.getId());
        assertEquals("Test Topic", topic.getTitle());
        assertEquals(Topic.TopicCategory.PSYCHOLOGY_TEST, topic.getCategory());
        assertEquals(Topic.TopicStatus.PENDING, topic.getStatus());
    }

    @Test
    void approveTopic() {
        Topic topic = topicService.createTopic(
                "Topic to Approve",
                Topic.TopicCategory.ART_THERAPY,
                "approve-keyword"
        );

        Topic approved = topicService.approveTopic(topic.getId());
        assertEquals(Topic.TopicStatus.APPROVED, approved.getStatus());
    }

    @Test
    void rejectTopic() {
        Topic topic = topicService.createTopic(
                "Topic to Reject",
                Topic.TopicCategory.RECOMMENDATION_LIST,
                "reject-keyword"
        );

        Topic rejected = topicService.rejectTopic(topic.getId(), "Not suitable");
        assertEquals(Topic.TopicStatus.REJECTED, rejected.getStatus());
    }

    @Test
    void getTopicsByStatus() {
        topicService.createTopic("Topic 1", Topic.TopicCategory.PSYCHOLOGY_TEST, "kw1");
        topicService.createTopic("Topic 2", Topic.TopicCategory.ART_THERAPY, "kw2");
        Topic t3 = topicService.createTopic("Topic 3", Topic.TopicCategory.RECOMMENDATION_LIST, "kw3");
        topicService.approveTopic(t3.getId());

        List<Topic> pending = topicService.getTopicsByStatus(Topic.TopicStatus.PENDING);
        List<Topic> approved = topicService.getTopicsByStatus(Topic.TopicStatus.APPROVED);

        assertEquals(2, pending.size());
        assertEquals(1, approved.size());
    }

    @Test
    void createBatch() {
        List<Topic> batch = List.of(
                new Topic("Batch 1", Topic.TopicCategory.ART_THERAPY, "b1"),
                new Topic("Batch 2", Topic.TopicCategory.PSYCHOLOGY_TEST, "b2"),
                new Topic("Batch 3", Topic.TopicCategory.RECOMMENDATION_LIST, "b3")
        );

        List<Topic> created = topicService.createBatch(batch);
        assertEquals(3, created.size());

        Integer batchNumber = created.get(0).getBatchNumber();
        assertNotNull(batchNumber);
        assertEquals(batchNumber, created.get(1).getBatchNumber());
        assertEquals(batchNumber, created.get(2).getBatchNumber());
    }
}
