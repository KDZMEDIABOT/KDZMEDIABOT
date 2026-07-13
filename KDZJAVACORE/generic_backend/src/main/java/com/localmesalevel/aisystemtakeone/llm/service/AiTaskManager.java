package com.localmesalevel.aisystemtakeone.llm.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;

/**
 * Manages running AI tasks for the bot.
 * Tracks task state, allows querying and killing running requests.
 */
@Service
public class AiTaskManager {

    private static final Logger logger = LoggerFactory.getLogger(AiTaskManager.class);

    private final Map<String, AiTask> tasks = new ConcurrentHashMap<>();

    /**
     * Register a new task and return its ID.
     */
    public String registerTask(String userId, String platform, String channel) {
        String taskId = UUID.randomUUID().toString();
        AiTask task = new AiTask(taskId, userId, platform, channel);
        tasks.put(taskId, task);
        logger.info("Registered AI task {} for user {} on {}/{}", taskId, userId, platform, channel);
        return taskId;
    }

    /**
     * Mark a task as completed.
     */
    public void completeTask(String taskId, String result) {
        AiTask task = tasks.get(taskId);
        if (task != null) {
            task.status = "completed";
            task.result = result;
            task.completedAt = Instant.now();
            logger.info("Completed AI task {}", taskId);
        }
    }

    /**
     * Mark a task as failed.
     */
    public void failTask(String taskId, String error) {
        AiTask task = tasks.get(taskId);
        if (task != null) {
            task.status = "failed";
            task.error = error;
            task.completedAt = Instant.now();
            logger.info("Failed AI task {}: {}", taskId, error);
        }
    }

    /**
     * Kill a running task by its ID.
     */
    public boolean killTask(String taskId) {
        AiTask task = tasks.get(taskId);
        if (task == null) {
            return false;
        }
        if (task.cancelFuture != null) {
            task.cancelFuture.cancel(true);
            task.status = "killed";
            logger.info("Killed AI task {}", taskId);
            return true;
        }
        return false;
    }

    /**
     * Kill all running tasks.
     */
    public int killAllTasks() {
        int count = 0;
        for (AiTask task : tasks.values()) {
            if ("running".equals(task.status) && task.cancelFuture != null) {
                task.cancelFuture.cancel(true);
                task.status = "killed";
                count++;
            }
        }
        logger.info("Killed {} AI tasks", count);
        return count;
    }

    /**
     * Associate a Future with a task to allow cancellation.
     */
    public void setTaskFuture(String taskId, Future<?> future) {
        AiTask task = tasks.get(taskId);
        if (task != null) {
            task.cancelFuture = future;
        }
    }

    /**
     * Get a list of all tasks (running and completed).
     */
    public List<AiTask> getTasks() {
        return new ArrayList<>(tasks.values());
    }

    /**
     * Get a specific task by ID.
     */
    public AiTask getTask(String taskId) {
        return tasks.get(taskId);
    }

    /**
     * Represents a single AI task.
     */
    public static class AiTask {
        public final String taskId;
        public final String userId;
        public final String platform;
        public final String channel;
        public final Instant startedAt;
        public volatile String status; // running, completed, failed, killed
        public volatile String result;
        public volatile String error;
        public volatile Instant completedAt;
        public volatile Future<?> cancelFuture;

        public AiTask(String taskId, String userId, String platform, String channel) {
            this.taskId = taskId;
            this.userId = userId;
            this.platform = platform;
            this.channel = channel;
            this.startedAt = Instant.now();
            this.status = "running";
        }
    }
}
