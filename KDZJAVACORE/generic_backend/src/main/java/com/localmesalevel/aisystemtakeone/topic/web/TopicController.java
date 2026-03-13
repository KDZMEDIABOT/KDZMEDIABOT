package com.localmesalevel.aisystemtakeone.topic.web;

import com.localmesalevel.aisystemtakeone.topic.model.Topic;
import com.localmesalevel.aisystemtakeone.topic.service.TopicService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/topics")
public class TopicController {

    private final TopicService topicService;

    public TopicController(TopicService topicService) {
        this.topicService = topicService;
    }

    @GetMapping
    public ResponseEntity<List<Topic>> getAllTopics() {
        return ResponseEntity.ok(topicService.getAllTopics());
    }

    @GetMapping("/{status}")
    public ResponseEntity<List<Topic>> getTopicsByStatus(@PathVariable Topic.TopicStatus status) {
        return ResponseEntity.ok(topicService.getTopicsByStatus(status));
    }

    @PostMapping
    public ResponseEntity<Topic> createTopic(@RequestBody Topic topic) {
        Topic created = topicService.createTopic(
                topic.getTitle(),
                topic.getCategory(),
                topic.getPrimaryKeyword());
        return ResponseEntity.ok(created);
    }

    @PostMapping("/batch")
    public ResponseEntity<List<Topic>> createBatch(@RequestBody List<Topic> topics) {
        List<Topic> created = topicService.createBatch(topics);
        return ResponseEntity.ok(created);
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<Topic> approveTopic(@PathVariable Long id) {
        return ResponseEntity.ok(topicService.approveTopic(id));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<Topic> rejectTopic(@PathVariable Long id, @RequestParam String reason) {
        return ResponseEntity.ok(topicService.rejectTopic(id, reason));
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "UP");
        return ResponseEntity.ok(response);
    }
}
