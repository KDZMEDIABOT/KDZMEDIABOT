package com.localmesalevel.aisystemtakeone.dialog.service;

import com.localmesalevel.aisystemtakeone.dialog.model.DialogMessage;
import com.localmesalevel.aisystemtakeone.dialog.model.DialogThread;
import com.localmesalevel.aisystemtakeone.dialog.repository.DialogMessageRepository;
import com.localmesalevel.aisystemtakeone.dialog.repository.DialogThreadRepository;
import com.localmesalevel.aisystemtakeone.llm.model.LlmEndpointCredentials;
import com.localmesalevel.aisystemtakeone.llm.service.LlmLoopEngine;
import com.localmesalevel.aisystemtakeone.llm.service.LlmLoopEngine.McpServerConfig;
import com.localmesalevel.aisystemtakeone.rag.service.RAGService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
@Transactional
public class DialogService {

    private static final Logger logger = LoggerFactory.getLogger(DialogService.class);

    private final DialogThreadRepository threadRepository;
    private final DialogMessageRepository messageRepository;
    private final RAGService ragService;

    @Autowired
    public DialogService(DialogThreadRepository threadRepository,
                         DialogMessageRepository messageRepository,
                         RAGService ragService) {
        this.threadRepository = threadRepository;
        this.messageRepository = messageRepository;
        this.ragService = ragService;
    }

    public DialogThread createThread(Long userId, String title, String systemPrompt) {
        DialogThread thread = new DialogThread();
        thread.setUserId(userId);
        thread.setTitle(title);
        thread.setSystemPrompt(systemPrompt);
        thread.setStatus("active");
        thread.setCreatedAt(Instant.now());
        return threadRepository.save(thread);
    }

    public DialogMessage addMessage(Long threadId, String role, String content) {
        DialogThread thread = threadRepository.findById(threadId).orElse(null);
        if (thread == null) {
            throw new IllegalArgumentException("Thread not found: " + threadId);
        }
        DialogMessage message = new DialogMessage();
        message.setThread(thread);
        message.setRole(role);
        message.setContent(content);
        message.setCreatedAt(Instant.now());
        return messageRepository.save(message);
    }

    public List<DialogMessage> getMessages(Long threadId) {
        return messageRepository.findByThreadIdOrderByCreatedAtAsc(threadId);
    }

    public void deleteThread(Long threadId, Long userId) {
        threadRepository.deleteByIdAndUserId(threadId, userId);
    }

    public List<DialogThread> listThreads(Long userId) {
        return threadRepository.findByUserIdOrderByLastMessageAtDesc(userId);
    }

    public Optional<DialogThread> getThread(Long threadId, Long userId) {
        return threadRepository.findByIdAndUserId(threadId, userId);
    }

    public DialogThread updateThread(Long threadId, Long userId, String title) {
        Optional<DialogThread> opt = threadRepository.findByIdAndUserId(threadId, userId);
        if (opt.isEmpty()) {
            throw new IllegalArgumentException("Thread not found: " + threadId);
        }
        DialogThread thread = opt.get();
        thread.setTitle(title);
        thread.setUpdatedAt(Instant.now());
        return threadRepository.save(thread);
    }

    public DialogThread sendChatMessage(Long threadId, String userContent, Long userId,
                                          LlmEndpointCredentials credentials, String modelName,
                                          String systemPrompt, List<McpServerConfig> mcpServers,
                                          LlmLoopEngine llmLoopEngine) {
        // 1. Persist user message
        DialogMessage userMsg = addMessage(threadId, "user", userContent);

        // 2. Build context from history
        List<DialogMessage> history = getMessages(threadId);
        StringBuilder historyBuilder = new StringBuilder();
        for (DialogMessage msg : history) {
            if (!"user".equals(msg.getRole()) && !"assistant".equals(msg.getRole())) {
                continue;
            }
            historyBuilder.append(msg.getRole()).append(": ").append(msg.getContent()).append("\n");
        }

        // 3. Enhance with RAG context
        String enhancedQuery = ragService.enhancePromptWithRAG(userContent, userId);

        // 4. Build full user prompt with context
        String fullUserPrompt = historyBuilder.toString() + "\n" + enhancedQuery;

        // 5. Call LLM with MCP tools
        String aiResponse;
        try {
            LlmLoopEngine.LoopResult result = llmLoopEngine.run(
                    credentials,
                    modelName,
                    systemPrompt,
                    fullUserPrompt,
                    mcpServers,
                    8
            );
            aiResponse = result.getFinalAnswer();
        } catch (RuntimeException e) {
            logger.error("LLM call failed for dialog thread {}: {}", threadId, e.getMessage(), e);
            aiResponse = "I apologize, but I encountered an error processing your request. Please try again.";
        }

        // 6. Persist assistant response
        DialogMessage assistantMsg = addMessage(threadId, "assistant", aiResponse);

        // 7. Update thread timestamp
        Optional<DialogThread> threadOpt = threadRepository.findById(threadId);
        if (threadOpt.isPresent()) {
            DialogThread thread = threadOpt.get();
            thread.setLastMessageAt(Instant.now());
            threadRepository.save(thread);
        }

        // 8. Index for RAG
        ragService.indexMessage(userMsg, userId);
        ragService.indexMessage(assistantMsg, userId);

        return threadOpt.orElse(null);
    }
}
