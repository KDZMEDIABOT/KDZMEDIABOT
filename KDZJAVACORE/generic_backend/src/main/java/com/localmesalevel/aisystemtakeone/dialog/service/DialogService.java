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
        String errorMessage = null;
        String aiResponse = null;

        try {
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
                logger.error("LLM call failed for dialog thread {}: {}", threadId, e.toString(), e);
                errorMessage = "Chat error: " + e;
                aiResponse = errorMessage;
            }

            // 6. Persist response (as assistant or system, depending on success)
            String role = errorMessage != null ? "system" : "assistant";
            DialogMessage assistantMsg = addMessage(threadId, role, aiResponse);

            // 7. Update thread timestamp
            Optional<DialogThread> threadOpt = threadRepository.findById(threadId);
            if (threadOpt.isPresent()) {
                DialogThread thread = threadOpt.get();
                thread.setLastMessageAt(Instant.now());
                threadRepository.save(thread);
            }

            // 8. Index for RAG (always index user message, error messages also useful for RAG contextually)
            ragService.indexMessage(userMsg, userId);
            // Only index assistant messages if no error, or index error if useful; keep it simple for now
            if (errorMessage == null) {
                ragService.indexMessage(assistantMsg, userId);
            }

            return threadOpt.orElse(null);

        } catch (Throwable e) {
            logger.error("Unexpected error in sendChatMessage for thread {}: {}", threadId, e.toString(), e);
            // Persist error as system message
            errorMessage = "Chat error: " + e;
            addMessage(threadId, "system", errorMessage);

            // Update thread timestamp even on error
            Optional<DialogThread> threadOpt = threadRepository.findById(threadId);
            if (threadOpt.isPresent()) {
                DialogThread thread = threadOpt.get();
                thread.setLastMessageAt(Instant.now());
                threadRepository.save(thread);
            }

            return threadOpt.orElse(null);
        }
    }
}
