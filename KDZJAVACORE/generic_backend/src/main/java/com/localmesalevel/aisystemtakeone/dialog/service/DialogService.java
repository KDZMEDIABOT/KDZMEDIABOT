package com.localmesalevel.aisystemtakeone.dialog.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.localmesalevel.aisystemtakeone.dialog.model.DialogMessage;
import com.localmesalevel.aisystemtakeone.dialog.model.DialogThread;
import com.localmesalevel.aisystemtakeone.dialog.repository.DialogMessageRepository;
import com.localmesalevel.aisystemtakeone.dialog.repository.DialogThreadRepository;
import com.localmesalevel.aisystemtakeone.llm.model.LlmEndpointCredentials;
import com.localmesalevel.aisystemtakeone.llm.service.LlmLoopEngine;
import com.localmesalevel.aisystemtakeone.llm.service.LlmLoopEngine.McpServerConfig;
import com.localmesalevel.aisystemtakeone.rag.model.RAGResult;
import com.localmesalevel.aisystemtakeone.rag.service.RAGService;
import com.localmesalevel.aisystemtakeone.workspace.service.WorkspaceFileService;

@Service
@Transactional
public class DialogService {

    private static final Logger logger = LoggerFactory.getLogger(DialogService.class);

    private final DialogThreadRepository threadRepository;
    private final DialogMessageRepository messageRepository;
    private final RAGService ragService;
    private final WorkspaceFileService workspaceFileService;

    @Autowired
    public DialogService(DialogThreadRepository threadRepository,
                         DialogMessageRepository messageRepository,
                         RAGService ragService,
                         WorkspaceFileService workspaceFileService) {
        this.threadRepository = threadRepository;
        this.messageRepository = messageRepository;
        this.ragService = ragService;
        this.workspaceFileService = workspaceFileService;
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

    public void deleteErrorReplies(Long messageId) {
        List<DialogMessage> replies = messageRepository.findErrorRepliesTo(messageId);
        for (DialogMessage reply : replies) {
            messageRepository.delete(reply);
        }
    }

    public DialogMessage getMessage(Long messageId) {
        return messageRepository.findById(messageId).orElse(null);
    }

    public List<DialogMessage> getMessages(Long threadId) {
        List<DialogMessage> messages = messageRepository.findByThreadIdOrderByCreatedAtAsc(threadId);
        for (DialogMessage msg : messages) {
            // Force initialization of lazy collections while still inside the transaction
            msg.getAttachedFiles().size();
            msg.getAttachedWorkspaces().size();
        }
        return messages;
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
        return sendChatMessage(threadId, userContent, userId, credentials, modelName, systemPrompt, mcpServers, llmLoopEngine, java.util.Collections.emptyList(), java.util.Collections.emptyList());
    }

    public DialogThread sendChatMessage(Long threadId, String userContent, Long userId,
                                          LlmEndpointCredentials credentials, String modelName,
                                          String systemPrompt, List<McpServerConfig> mcpServers,
                                          LlmLoopEngine llmLoopEngine,
                                          java.util.List<com.localmesalevel.aisystemtakeone.workspace.model.WorkspaceFile> attachedFiles,
                                          java.util.List<com.localmesalevel.aisystemtakeone.workspace.model.Workspace> attachedWorkspaces) {
        // 1. Persist user message (with attached files and workspaces)
        DialogMessage userMsg;
        if (attachedWorkspaces != null && !attachedWorkspaces.isEmpty()) {
            DialogThread thread = threadRepository.findById(threadId).orElse(null);
            if (thread == null) {
                throw new IllegalArgumentException("Thread not found: " + threadId);
            }
            userMsg = new DialogMessage();
            userMsg.setThread(thread);
            userMsg.setRole("user");
            userMsg.setContent(userContent);
            userMsg.setCreatedAt(Instant.now());
            if (attachedFiles != null && !attachedFiles.isEmpty()) {
                userMsg.getAttachedFiles().addAll(attachedFiles);
            }
            userMsg.getAttachedWorkspaces().addAll(attachedWorkspaces);
            userMsg = messageRepository.save(userMsg);
        } else {
            userMsg = addMessage(threadId, "user", userContent, attachedFiles);
        }
        final Long userMsgId = userMsg.getId();
        String errorMessage = null;
        String aiResponse = null;
        try {
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
	
	        // 5a. Attach attached file/workspace URI block to the prompt
	        String attachmentBlock = buildAttachmentUriBlock(attachedFiles, attachedWorkspaces);
	        if (!attachmentBlock.isEmpty()) {
	            fullPromptBuilder.append(attachmentBlock).append("\n\n");
	        }
	
	        fullPromptBuilder.append("Conversation history:\n").append(historyBuilder.toString()).append("\n");
	        fullPromptBuilder.append("User query: ").append(enhancedQuery);
	        String fullUserPrompt = fullPromptBuilder.toString();
	
	        // 6. Call LLM with MCP tools (including in-memory workspaceFileReadAsText)
	        try {
	            List<LlmLoopEngine.InMemoryMcpTool> inMemoryTools = buildInMemoryTools();
	            LlmLoopEngine.LoopResult result = llmLoopEngine.run(
	                    credentials,
	                    modelName,
	                    systemPrompt,
	                    fullUserPrompt,
	                    mcpServers,
	                    8,
	                    inMemoryTools
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
        assistantMsg.setIsReplyTo(userMsgId);
        if (errorMessage != null) {
            assistantMsg.setError(true);
        }
        assistantMsg = messageRepository.save(assistantMsg);
	
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
	                        String fileText = workspaceFileService.readFileAsText(attachedFile);
	                        ragService.indexFile(fileText, attachedFile.getId(), userId);
	                    } catch (Exception e) {
	                        logger.error("Failed to index file {} for RAG: {}", attachedFile.getId(), e.toString(), e);
	                    }
	                }
	            }
	        }
	
	        // 9b. Index attached workspace files for RAG
	        if (attachedWorkspaces != null && !attachedWorkspaces.isEmpty()) {
	            for (com.localmesalevel.aisystemtakeone.workspace.model.Workspace w : attachedWorkspaces) {
	                if (w.getFiles() != null) {
	                    for (com.localmesalevel.aisystemtakeone.workspace.model.WorkspaceFile wf : w.getFiles()) {
	                        if (wf.getFileData() != null && wf.getFileData().length > 0) {
	                            try {
	                                String fileText = workspaceFileService.readFileAsText(wf);
	                                ragService.indexFile(fileText, wf.getId(), userId);
	                            } catch (Exception e) {
	                                logger.error("Failed to index workspace file {} for RAG: {}", wf.getId(), e.toString(), e);
	                            }
	                        }
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
            DialogMessage errMsg = addMessage(threadId, "system", errorMessage);
            errMsg.setIsReplyTo(userMsgId);
            errMsg.setError(true);
            messageRepository.save(errMsg);

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
            logger.error("RAG enrichment failed during recap generation for thread {}: {}", threadId, e.toString(), e);
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

    private String buildAttachmentUriBlock(java.util.List<com.localmesalevel.aisystemtakeone.workspace.model.WorkspaceFile> files,
                                          java.util.List<com.localmesalevel.aisystemtakeone.workspace.model.Workspace> workspaces) {
        boolean hasFiles = files != null && !files.isEmpty();
        boolean hasWorkspaces = workspaces != null && !workspaces.isEmpty();
        if (!hasFiles && !hasWorkspaces) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        if (hasFiles) {
            sb.append("[Attached Files]\n");
            for (com.localmesalevel.aisystemtakeone.workspace.model.WorkspaceFile f : files) {
                Long wsId = f.getWorkspace() != null ? f.getWorkspace().getId() : null;
                String uri = "workspace://" + (wsId != null ? wsId : "0") + "/file/" + f.getId();
                sb.append("- ").append(uri).append(" (").append(f.getFileName()).append(")\n");
            }
        }
        if (hasWorkspaces) {
            sb.append("[Attached Workspaces]\n");
            for (com.localmesalevel.aisystemtakeone.workspace.model.Workspace w : workspaces) {
                sb.append("- workspace://").append(w.getId()).append(" (").append(w.getName()).append(")\n");
            }
        }
        return sb.toString().trim();
    }

    private List<LlmLoopEngine.InMemoryMcpTool> buildInMemoryTools() {
        return List.of(
            new WorkspaceFileReadAsTextTool(),
            new WorkspaceReadAllFilesAsTextTool()
        );
    }

    private class WorkspaceReadAllFilesAsTextTool extends LlmLoopEngine.InMemoryMcpTool {
        private static final String SCHEMA = "{\"type\":\"object\",\"properties\":{\"workspaceUri\":{\"type\":\"string\",\"description\":\"Workspace URI, e.g. workspace://1\"}},\"required\":[\"workspaceUri\"]}";

        WorkspaceReadAllFilesAsTextTool() {
            super("workspaceReadAllFilesAsText", "Given a workspace URI (e.g. workspace://1), read ALL files in that workspace and return their combined text content. Returns a concatenated text block.", parseSchema(SCHEMA));
        }

        private static com.fasterxml.jackson.databind.JsonNode parseSchema(String s) {
            try {
                return new com.fasterxml.jackson.databind.ObjectMapper().readTree(s);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to parse tool schema", e);
            }
        }

        @Override
        public com.fasterxml.jackson.databind.JsonNode execute(com.fasterxml.jackson.databind.JsonNode arguments) {
            if (arguments == null || !arguments.isObject()) {
                return errorJson("Arguments must be a JSON object");
            }
            String workspaceUri = arguments.has("workspaceUri") ? arguments.get("workspaceUri").asText(null) : null;
            if (workspaceUri == null || workspaceUri.isBlank()) {
                return errorJson("workspaceUri is required");
            }
            Long workspaceId = parseWorkspaceId(workspaceUri);
            if (workspaceId == null) {
                return errorJson("Invalid workspace URI: " + workspaceUri);
            }
            java.util.List<com.localmesalevel.aisystemtakeone.workspace.model.WorkspaceFile> files = workspaceFileService.listFiles(workspaceId);
            if (files == null || files.isEmpty()) {
                return resultJson("Workspace " + workspaceUri + " has no files.");
            }
            StringBuilder sb = new StringBuilder();
            for (com.localmesalevel.aisystemtakeone.workspace.model.WorkspaceFile f : files) {
                sb.append("--- File: ").append(f.getFileName()).append(" ---\n");
                sb.append(workspaceFileService.readFileAsText(f)).append("\n\n");
            }
            return resultJson(sb.toString().trim());
        }

        private Long parseWorkspaceId(String uri) {
            if (uri == null) return null;
            if (uri.startsWith("workspace://")) {
                try {
                    return Long.parseLong(uri.substring("workspace://".length()));
                } catch (NumberFormatException e) {
                    return null;
                }
            }
            try {
                return Long.parseLong(uri);
            } catch (NumberFormatException e) {
                return null;
            }
        }

        private com.fasterxml.jackson.databind.JsonNode resultJson(String content) {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.node.ObjectNode node = mapper.createObjectNode();
            node.put("content", content);
            return node;
        }

        private com.fasterxml.jackson.databind.JsonNode errorJson(String message) {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.node.ObjectNode node = mapper.createObjectNode();
            node.put("error", message);
            return node;
        }
    }

    private class WorkspaceFileReadAsTextTool extends LlmLoopEngine.InMemoryMcpTool {
        private static final String SCHEMA = "{\"type\":\"object\",\"properties\":{\"workspaceId\":{\"type\":\"integer\",\"description\":\"Workspace ID\"},\"fileId\":{\"type\":\"integer\",\"description\":\"File ID\"}},\"required\":[\"workspaceId\",\"fileId\"]}";

        WorkspaceFileReadAsTextTool() {
            super("workspaceFileReadAsText", "Read a workspace file's text content given its workspaceId and fileId. Returns the file content as text.", parseSchema(SCHEMA));
        }

        private static com.fasterxml.jackson.databind.JsonNode parseSchema(String s) {
            try {
                return new com.fasterxml.jackson.databind.ObjectMapper().readTree(s);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to parse tool schema", e);
            }
        }

        @Override
        public com.fasterxml.jackson.databind.JsonNode execute(com.fasterxml.jackson.databind.JsonNode arguments) {
            if (arguments == null || !arguments.isObject()) {
                return errorJson("Arguments must be a JSON object");
            }
            Long workspaceId = arguments.has("workspaceId") ? arguments.get("workspaceId").asLong() : null;
            Long fileId = arguments.has("fileId") ? arguments.get("fileId").asLong() : null;
            if (workspaceId == null || fileId == null) {
                return errorJson("Both workspaceId and fileId are required");
            }
            Optional<com.localmesalevel.aisystemtakeone.workspace.model.WorkspaceFile> fileOpt = workspaceFileService.getFile(fileId);
            if (fileOpt.isEmpty()) {
                return errorJson("File not found: " + fileId);
            }
            com.localmesalevel.aisystemtakeone.workspace.model.WorkspaceFile file = fileOpt.get();
            Long fileWsId = file.getWorkspace() != null ? file.getWorkspace().getId() : null;
            if (fileWsId != null && !fileWsId.equals(workspaceId)) {
                return errorJson("File does not belong to workspace: " + workspaceId);
            }
            if (file.getFileData() == null) {
                return errorJson("File data is empty");
            }
            String text = workspaceFileService.readFileAsText(file);
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.node.ObjectNode result = mapper.createObjectNode();
            result.put("content", text);
            return result;
        }

        private com.fasterxml.jackson.databind.JsonNode errorJson(String message) {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.node.ObjectNode node = mapper.createObjectNode();
            node.put("error", message);
            return node;
        }
    }

}
