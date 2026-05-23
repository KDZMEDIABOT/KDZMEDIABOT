package com.localmesalevel.aisystemtakeone.dialog.service;

import com.localmesalevel.aisystemtakeone.dialog.model.DialogMessage;
import com.localmesalevel.aisystemtakeone.dialog.model.DialogThread;
import com.localmesalevel.aisystemtakeone.dialog.repository.DialogMessageRepository;
import com.localmesalevel.aisystemtakeone.dialog.repository.DialogThreadRepository;
import com.localmesalevel.aisystemtakeone.llm.model.LlmEndpointCredentials;
import com.localmesalevel.aisystemtakeone.llm.service.LlmLoopEngine;
import com.localmesalevel.aisystemtakeone.llm.service.LlmLoopEngine.McpServerConfig;
import com.localmesalevel.aisystemtakeone.rag.model.RAGResult;
import com.localmesalevel.aisystemtakeone.rag.service.RAGService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

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
        return addMessage(threadId, role, content, java.util.Collections.emptyList());
    }

    public DialogMessage addMessage(Long threadId, String role, String content,
                                    java.util.List<com.localmesalevel.aisystemtakeone.workspace.model.WorkspaceFile> attachedFiles) {
        DialogThread thread = threadRepository.findById(threadId).orElse(null);
        if (thread == null) {
            throw new IllegalArgumentException("Thread not found: " + threadId);
        }
        DialogMessage message = new DialogMessage();
        message.setThread(thread);
        message.setRole(role);
        message.setContent(content);
        message.setCreatedAt(Instant.now());
        if (attachedFiles != null && !attachedFiles.isEmpty()) {
            message.getAttachedFiles().addAll(attachedFiles);
        }
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

    public static final int MAX_CONTEXT_MESSAGES = 40;
    public static final int RECAP_LENGTH = 1024 * 10;

    public DialogThread sendChatMessage(Long threadId, String userContent, Long userId,
                                          LlmEndpointCredentials credentials, String modelName,
                                          String systemPrompt, List<McpServerConfig> mcpServers,
                                          LlmLoopEngine llmLoopEngine) {
        return sendChatMessage(threadId, userContent, userId, credentials, modelName, systemPrompt, mcpServers, llmLoopEngine, java.util.Collections.emptyList());
    }

    public DialogThread sendChatMessage(Long threadId, String userContent, Long userId,
                                          LlmEndpointCredentials credentials, String modelName,
                                          String systemPrompt, List<McpServerConfig> mcpServers,
                                          LlmLoopEngine llmLoopEngine,
                                          java.util.List<com.localmesalevel.aisystemtakeone.workspace.model.WorkspaceFile> attachedFiles) {
        // 1. Persist user message (with attached files)
        DialogMessage userMsg = addMessage(threadId, "user", userContent, attachedFiles);
        String errorMessage = null;
        String aiResponse = null;

        try {
            // 2. Build context from history and check if recap is needed
            List<DialogMessage> history = getMessages(threadId);
            boolean shouldRecap = false;
            int historySize = history.size();
            if (historySize > 0 && (historySize % MAX_CONTEXT_MESSAGES) == (MAX_CONTEXT_MESSAGES - 1)) {
                shouldRecap = true;
            }

            if (shouldRecap) {
                // Fetch last ~40 messages for recap
                List<DialogMessage> recapMessages = history;
                if (historySize > MAX_CONTEXT_MESSAGES) {
                    recapMessages = history.subList(history.size() - MAX_CONTEXT_MESSAGES, history.size());
                }
                String recap = generateRecap(recapMessages, credentials, modelName, systemPrompt, mcpServers, llmLoopEngine, threadId, userId);
                addMessage(threadId, "system", "--- Compacted Conversation ---\n" + recap);
            }

            // Re-fetch history after optional recap
            history = getMessages(threadId);
            List<DialogMessage> contextMessages = history;
            if (history.size() > MAX_CONTEXT_MESSAGES) {
                contextMessages = history.subList(history.size() - MAX_CONTEXT_MESSAGES, history.size());
            }

            StringBuilder historyBuilder = new StringBuilder();
            for (DialogMessage msg : contextMessages) {
                if (!"user".equals(msg.getRole()) && !"assistant".equals(msg.getRole())) {
                    continue;
                }
                historyBuilder.append(msg.getRole()).append(": ").append(msg.getContent()).append("\n");
            }

            // 3. Enhance with RAG context (using user query + last messages combined)
            String ragQuery = historyBuilder.toString() + "\n" + userContent;
            String enhancedQuery = ragService.enhancePromptWithRAG(ragQuery, userId);

            // 4. Retrieve rich RAG context from adapters
            List<RAGResult> ragResults = retrieveRagContext(ragQuery, userId);

            // 5. Build full user prompt with history, current query, and RAG context
            StringBuilder fullPromptBuilder = new StringBuilder();
            if (!ragResults.isEmpty()) {
                fullPromptBuilder.append("[Relevant context from previous conversations and knowledge base:\n");
                for (RAGResult result : ragResults) {
                    String content = result.getContent();
                    if (content != null && !content.isEmpty()) {
                        fullPromptBuilder.append("- \"").append(truncate(content, 300))
                                .append("\" (").append(result.getCitation()).append(")\n");
                    }
                }
                fullPromptBuilder.append("]\n\n");
            }
            fullPromptBuilder.append("Conversation history:\n").append(historyBuilder.toString()).append("\n");
            fullPromptBuilder.append("User query: ").append(enhancedQuery);
            String fullUserPrompt = fullPromptBuilder.toString();

            // 6. Call LLM with MCP tools
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

            // 7. Persist response (as assistant or system, depending on success)
            String role = errorMessage != null ? "system" : "assistant";
            DialogMessage assistantMsg = addMessage(threadId, role, aiResponse);

            // 8. Update thread timestamp
            Optional<DialogThread> threadOpt = threadRepository.findById(threadId);
            if (threadOpt.isPresent()) {
                DialogThread thread = threadOpt.get();
                thread.setLastMessageAt(Instant.now());
                threadRepository.save(thread);
            }

            // 9. Index attached files for RAG
            if (attachedFiles != null && !attachedFiles.isEmpty()) {
                for (com.localmesalevel.aisystemtakeone.workspace.model.WorkspaceFile attachedFile : attachedFiles) {
                    if (attachedFile.getFileData() != null && attachedFile.getFileData().length > 0) {
                        try {
                            String fileText = new String(attachedFile.getFileData(), java.nio.charset.StandardCharsets.UTF_8);
                            ragService.indexFile(fileText, attachedFile.getId(), userId);
                        } catch (Exception e) {
                            logger.warn("Failed to index file {} for RAG: {}", attachedFile.getId(), e.getMessage());
                        }
                    }
                }
            }

            // 10. Index for RAG (always index user message, error messages also useful for RAG contextually)
            ragService.indexMessage(userMsg, userId);
            if (errorMessage == null) {
                ragService.indexMessage(assistantMsg, userId);
            }

            return threadOpt.orElse(null);

        } catch (Throwable e) {
            logger.error("Unexpected error in sendChatMessage for thread {}: {}", threadId, e.toString(), e);
            errorMessage = "Chat error: " + e;
            addMessage(threadId, "system", errorMessage);

            Optional<DialogThread> threadOpt = threadRepository.findById(threadId);
            if (threadOpt.isPresent()) {
                DialogThread thread = threadOpt.get();
                thread.setLastMessageAt(Instant.now());
                threadRepository.save(thread);
            }
            return threadOpt.orElse(null);
        }
    }

    private String generateRecap(List<DialogMessage> messages, LlmEndpointCredentials credentials,
                                 String modelName, String systemPrompt, List<McpServerConfig> mcpServers,
                                 LlmLoopEngine llmLoopEngine, Long threadId, Long userId) {
        String rawMessages = messages.stream()
                .filter(m -> "user".equals(m.getRole()) || "assistant".equals(m.getRole()))
                .map(m -> m.getRole() + ": " + m.getContent())
                .collect(java.util.stream.Collectors.joining("\n"));

        StringBuilder recapPrompt = new StringBuilder();
        recapPrompt.append("Please create a compact recap of the following conversation whose max length is ")
                .append(RECAP_LENGTH).append(" characters.\n");
        recapPrompt.append("Include the key topics discussed and decisions made.\n\n");
        recapPrompt.append("Then, also use any additional relevant context retrieved from RAG to enrich this recap.\n\n");
        recapPrompt.append("CONVERSATION:\n").append(rawMessages).append("\n\n");

        // Enrich with RAG
        try {
            List<RAGResult> rag = retrieveRagContext(rawMessages, userId);
            if (!rag.isEmpty()) {
                recapPrompt.append("[Relevant RAG Context]\n");
                for (RAGResult r : rag) {
                    recapPrompt.append("- ").append(r.getContent()).append(" (").append(r.getCitation()).append(")\n");
                }
            }
        } catch (Exception e) {
            logger.warn("RAG enrichment failed during recap generation for thread {}: {}", threadId, e.getMessage());
        }

        recapPrompt.append("\nProvide the recap now.\n");

        // Use simple LLM call to generate the recap (bypass tool loop for this)
        String recapContent;
        try {
            LlmLoopEngine.LoopResult result = llmLoopEngine.run(
                    credentials,
                    modelName,
                    systemPrompt,
                    recapPrompt.toString(),
                    mcpServers,
                    8
            );
            recapContent = result.getFinalAnswer();
        } catch (RuntimeException e) {
            logger.error("Recap LLM call failed for thread {}: {}", threadId, e.toString(), e);
            // Fallback: simple string truncation of the raw messages
            String fallback = "[Conversation Recap] " + rawMessages.substring(0, Math.min(rawMessages.length(), RECAP_LENGTH));
            recapContent = fallback;
        }

        // Ensure max length
        if (recapContent.length() > RECAP_LENGTH) {
            recapContent = recapContent.substring(0, RECAP_LENGTH);
        }
        return recapContent;
    }

    private java.util.List<RAGResult> retrieveRagContext(String query, Long userId) {
        if (!ragService.isAvailable()) {
            return java.util.List.of();
        }
        try {
            return ragService.retrieveRelevantContext(query, userId, 5);
        } catch (Exception e) {
            logger.warn("RAG context retrieval failed: {}", e.getMessage());
            return java.util.List.of();
        }
    }

    private String truncate(String s, int maxLen) {
        if (s == null || s.length() <= maxLen) {
            return s;
        }
        return s.substring(0, maxLen) + "...";
    }

}
