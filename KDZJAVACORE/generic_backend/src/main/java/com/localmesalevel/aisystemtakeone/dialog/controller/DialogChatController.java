package com.localmesalevel.aisystemtakeone.dialog.controller;

import com.localmesalevel.aisystemtakeone.dialog.model.DialogMessage;
import com.localmesalevel.aisystemtakeone.dialog.model.DialogThread;
import com.localmesalevel.aisystemtakeone.dialog.service.DialogService;
import com.localmesalevel.aisystemtakeone.llm.model.LlmEndpointCredentials;
import com.localmesalevel.aisystemtakeone.llm.service.LlmLoopEngine;
import com.localmesalevel.aisystemtakeone.llm.service.LlmLoopEngine.McpServerConfig;
import com.localmesalevel.aisystemtakeone.user.model.UserAccount;
import com.localmesalevel.aisystemtakeone.user.repository.UserAccountRepository;
import com.localmesalevel.aisystemtakeone.workspace.model.Workspace;
import com.localmesalevel.aisystemtakeone.workspace.model.WorkspaceFile;
import com.localmesalevel.aisystemtakeone.workspace.service.WorkspaceFileService;
import com.localmesalevel.aisystemtakeone.workspace.service.WorkspaceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/api/dialogs")
public class DialogChatController {

    private final DialogService dialogService;
    private final UserAccountRepository userAccountRepository;
    private final LlmLoopEngine llmLoopEngine;
    private final WorkspaceFileService workspaceFileService;
    private final WorkspaceService workspaceService;

    @Autowired
    public DialogChatController(
            DialogService dialogService,
            UserAccountRepository userAccountRepository,
            LlmLoopEngine llmLoopEngine,
            WorkspaceFileService workspaceFileService,
            WorkspaceService workspaceService
    ) {
        this.dialogService = dialogService;
        this.userAccountRepository = userAccountRepository;
        this.llmLoopEngine = llmLoopEngine;
        this.workspaceFileService = workspaceFileService;
        this.workspaceService = workspaceService;
    }

    private java.util.Map<String, Object> toMessageMap(DialogMessage msg) {
        java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("id", msg.getId());
        map.put("threadId", msg.getThreadId());
        map.put("role", msg.getRole());
        map.put("content", msg.getContent());
        map.put("toolName", msg.getToolName());
        map.put("toolResult", msg.getToolResult());
        map.put("tokensUsed", msg.getTokensUsed());
        map.put("createdAt", msg.getCreatedAt());
        map.put("error", msg.isError());
        map.put("isReplyTo", msg.getIsReplyTo());
        return map;
    }

    @PostMapping("/{id}/chat")
    public ResponseEntity<?> sendChatMessage(
            @PathVariable Long id,
            @RequestBody ChatMessageRequest request,
            @RequestAttribute("userId") Long userId
    ) {
        try {
            Optional<DialogThread> threadOpt = dialogService.getThread(id, userId);
            if (!threadOpt.isPresent()) {
                return ResponseEntity.notFound().build();
            }

            DialogThread thread = threadOpt.get();
            UserAccount user = userAccountRepository.findById(userId).orElse(null);
            if (user == null) {
                return ResponseEntity.status(401).body("User not found");
            }

            LlmEndpointCredentials credentials = user.getCurrentLlmEndpoint();
            if (credentials == null) {
                return ResponseEntity.badRequest().body("No LLM endpoint configured");
            }

            List<McpServerConfig> mcpServers = buildDefaultBotMcpServers();

            // Resolve attached files
            java.util.List<WorkspaceFile> attachedFiles = java.util.Collections.emptyList();
            if (request.fileIds != null && !request.fileIds.isEmpty()) {
                attachedFiles = request.fileIds.stream()
                        .map(workspaceFileService::getFile)
                        .filter(java.util.Optional::isPresent)
                        .map(java.util.Optional::get)
                        .collect(java.util.stream.Collectors.toList());
            }

            // Resolve attached workspaces
            java.util.List<Workspace> attachedWorkspaces = java.util.Collections.emptyList();
            if (request.workspaceIds != null && !request.workspaceIds.isEmpty()) {
                attachedWorkspaces = request.workspaceIds.stream()
                        .map(wsId -> workspaceService.getWorkspace(wsId, userId))
                        .filter(java.util.Optional::isPresent)
                        .map(java.util.Optional::get)
                        .collect(java.util.stream.Collectors.toList());
            }

            dialogService.sendChatMessage(
                    id,
                    request.content,
                    userId,
                    credentials,
                    thread.getModelName() != null ? thread.getModelName() : credentials.getModelName(),
                    thread.getSystemPrompt(),
                    mcpServers,
                    llmLoopEngine,
                    attachedFiles,
                    attachedWorkspaces
            );

            List<DialogMessage> messages = dialogService.getMessages(id);
            if (messages.isEmpty()) {
                return ResponseEntity.ok(java.util.Collections.emptyMap());
            }
            DialogMessage lastMessage = messages.get(messages.size() - 1);

            return ResponseEntity.ok(toMessageMap(lastMessage));
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Chat error: " + e.getMessage());
        }
    }

    @PostMapping(value = "/{id}/chat/stream", produces = "text/event-stream")
    public SseEmitter streamChatMessage(
            @PathVariable Long id,
            @RequestBody ChatMessageRequest request,
            @RequestAttribute("userId") Long userId
    ) {
        SseEmitter emitter = new SseEmitter(300_000L);

        Executors.newSingleThreadExecutor().submit(() -> {
            try {
                dialogService.addMessage(id, "user", request.content);
                emitter.send(SseEmitter.event().name("message").data("{\"type\": \"user\", \"content\": \"" + request.content + "\"}"));

                Optional<DialogThread> threadOpt = dialogService.getThread(id, userId);
                if (!threadOpt.isPresent()) {
                    emitter.send(SseEmitter.event().name("error").data("Thread not found"));
                    emitter.complete();
                    return;
                }

                DialogThread thread = threadOpt.get();
                UserAccount user = userAccountRepository.findById(userId).orElse(null);
                if (user == null || user.getCurrentLlmEndpoint() == null) {
                    emitter.send(SseEmitter.event().name("error").data("No LLM configured"));
                    emitter.complete();
                    return;
                }

                LlmEndpointCredentials credentials = user.getCurrentLlmEndpoint();
                List<McpServerConfig> mcpServers = buildDefaultBotMcpServers();

                try {
                    LlmLoopEngine.LoopResult result = llmLoopEngine.run(
                            credentials,
                            thread.getModelName() != null ? thread.getModelName() : credentials.getModelName(),
                            thread.getSystemPrompt() != null ? thread.getSystemPrompt() : "You are a helpful assistant.",
                            request.content,
                            mcpServers,
                            8
                    );

                    String finalAnswer = result.getFinalAnswer();
                    String reasoning = result.getReasoning();
                    if (reasoning != null && !reasoning.isBlank()) {
                        emitter.send(SseEmitter.event().name("reasoning").data(reasoning));
                    }
                    int chunkSize = 200;
                    for (int i = 0; i < finalAnswer.length(); i += chunkSize) {
                        String chunk = finalAnswer.substring(i, Math.min(i + chunkSize, finalAnswer.length()));
                        emitter.send(SseEmitter.event().name("chunk").data(chunk));
                        Thread.sleep(50);
                    }

                    dialogService.addMessage(id, "assistant", finalAnswer);
                    emitter.send(SseEmitter.event().name("done").data(finalAnswer));
                    emitter.complete();
                } catch (Exception e) {
                    emitter.send(SseEmitter.event().name("error").data(e.getMessage()));
                    emitter.complete();
                }
            } catch (Exception e) {
                try {
                    emitter.send(SseEmitter.event().name("error").data(e.getMessage()));
                } catch (IOException ex) {
                    // Ignore send error on already-closed emitter
                }
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    private List<McpServerConfig> buildDefaultBotMcpServers() {
        List<McpServerConfig> servers = new ArrayList<>();
        String containerName = System.getenv().getOrDefault(
                "OPENSERP_MCP_CONTAINER_NAME",
                "aisystem-openserp-mcp-sidecar-dev"
        );
        servers.add(McpServerConfig.stdioLocal(
                "openserp-websearch",
                java.util.Arrays.asList("docker", "exec", "-i", containerName, "python", "-u", "/opt/openmcp_server.py"),
                McpServerConfig.StdioMessageFraming.NEWLINE_DELIMITED_JSON
        ));
        return servers;
    }

    @PostMapping("/{id}/chat/retry/{messageId}")
    public ResponseEntity<?> retryMessage(
            @PathVariable Long id,
            @PathVariable Long messageId,
            @RequestAttribute("userId") Long userId
    ) {
        try {
            Optional<DialogMessage> msgOpt = Optional.ofNullable(dialogService.getMessage(messageId));
            if (!msgOpt.isPresent()) {
                return ResponseEntity.notFound().build();
            }
            DialogMessage original = msgOpt.get();
            if (original.getThread() == null || !original.getThread().getId().equals(id)) {
                return ResponseEntity.status(403).body("Message does not belong to the specified thread");
            }
            if (!"user".equals(original.getRole())) {
                return ResponseEntity.badRequest().body("Can only retry user messages");
            }

            // Delete all error replies to this user message
            dialogService.deleteErrorReplies(messageId);

            // Re-send the original user message content
            Optional<DialogThread> threadOpt = dialogService.getThread(id, userId);
            if (!threadOpt.isPresent()) {
                return ResponseEntity.notFound().build();
            }

            DialogThread thread = threadOpt.get();
            UserAccount user = userAccountRepository.findById(userId).orElse(null);
            if (user == null) {
                return ResponseEntity.status(401).body("User not found");
            }

            LlmEndpointCredentials credentials = user.getCurrentLlmEndpoint();
            if (credentials == null) {
                return ResponseEntity.badRequest().body("No LLM endpoint configured");
            }

            // Resolve re-attachments from the original message
            java.util.List<WorkspaceFile> attachedFiles = new java.util.ArrayList<>(original.getAttachedFiles());
            java.util.List<Workspace> attachedWorkspaces = new java.util.ArrayList<>(original.getAttachedWorkspaces());

            List<McpServerConfig> mcpServers = buildDefaultBotMcpServers();

            dialogService.sendChatMessage(
                    id,
                    original.getContent(),
                    userId,
                    credentials,
                    thread.getModelName() != null ? thread.getModelName() : credentials.getModelName(),
                    thread.getSystemPrompt(),
                    mcpServers,
                    llmLoopEngine,
                    attachedFiles,
                    attachedWorkspaces
            );

            List<DialogMessage> messages = dialogService.getMessages(id);
            if (messages.isEmpty()) {
                return ResponseEntity.ok(java.util.Collections.emptyMap());
            }
            DialogMessage lastMessage = messages.get(messages.size() - 1);

            return ResponseEntity.ok(toMessageMap(lastMessage));
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Retry error: " + e.getMessage());
        }
    }

    public static class ChatMessageRequest {
        public String content;
        public java.util.List<Long> fileIds = java.util.Collections.emptyList();
        public java.util.List<Long> workspaceIds = java.util.Collections.emptyList();
    }
}
