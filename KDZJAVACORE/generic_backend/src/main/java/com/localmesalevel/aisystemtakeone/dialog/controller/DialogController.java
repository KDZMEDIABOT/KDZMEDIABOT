package com.localmesalevel.aisystemtakeone.dialog.controller;

import com.localmesalevel.aisystemtakeone.dialog.model.DialogMessage;
import com.localmesalevel.aisystemtakeone.dialog.model.DialogThread;
import com.localmesalevel.aisystemtakeone.dialog.service.DialogService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/dialogs")
public class DialogController {

    private static final Logger logger = LoggerFactory.getLogger(DialogController.class);

    private final DialogService dialogService;

    @Autowired
    public DialogController(DialogService dialogService) {
        this.dialogService = dialogService;
    }

    @GetMapping
    public ResponseEntity<List<DialogThread>> listThreads(@RequestAttribute("userId") Long userId) {
        logger.info("[DIALOG] listThreads userId={}", userId);
        return ResponseEntity.ok(dialogService.listThreads(userId));
    }

    @PostMapping
    public ResponseEntity<DialogThread> createThread(@RequestBody CreateThreadRequest request,
                                                      @RequestAttribute("userId") Long userId) {
        logger.info("[DIALOG] createThread userId={} title={}", userId, request.title);
        DialogThread thread = dialogService.createThread(userId, request.title, request.systemPrompt);
        logger.info("[DIALOG] createThread created threadId={}", thread.getId());
        return ResponseEntity.ok(thread);
    }

    @PutMapping("/{id}")
    public ResponseEntity<DialogThread> updateThread(
            @PathVariable Long id,
            @RequestBody UpdateThreadRequest request,
            @RequestAttribute("userId") Long userId) {
        logger.info("[DIALOG] updateThread threadId={} userId={} title={}", id, userId, request.title);
        DialogThread thread = dialogService.updateThread(id, userId, request.title);
        return ResponseEntity.ok(thread);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteThread(@PathVariable Long id,
                                             @RequestAttribute("userId") Long userId) {
        dialogService.deleteThread(id, userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<DialogThread> getThread(
            @PathVariable Long id,
            @RequestAttribute("userId") Long userId
    ) {
        return dialogService.getThread(id, userId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/messages")
    public ResponseEntity<List<Map<String, Object>>> getMessages(
            @PathVariable Long id,
            @RequestAttribute("userId") Long userId
    ) {
        // Verify the user owns this thread before returning messages
        Optional<DialogThread> thread = dialogService.getThread(id, userId);
        if (thread.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        List<DialogMessage> messages = dialogService.getMessages(id);
        return ResponseEntity.ok(messages.stream().map(this::toMessageMap).collect(java.util.stream.Collectors.toList()));
    }

    private Map<String, Object> toMessageMap(DialogMessage msg) {
        Map<String, Object> map = new java.util.LinkedHashMap<>();
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
        map.put("attachedFiles", msg.getAttachedFiles().stream().map(this::toFileMap).collect(java.util.stream.Collectors.toList()));
        map.put("attachedWorkspaces", msg.getAttachedWorkspaces().stream().map(this::toWorkspaceMap).collect(java.util.stream.Collectors.toList()));
        return map;
    }

    private Map<String, Object> toFileMap(com.localmesalevel.aisystemtakeone.workspace.model.WorkspaceFile f) {
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("id", f.getId());
        map.put("fileName", f.getFileName());
        map.put("mimeType", f.getMimeType());
        map.put("fileSize", f.getFileSize());
        map.put("createdAt", f.getCreatedAt());
        return map;
    }

    private Map<String, Object> toWorkspaceMap(com.localmesalevel.aisystemtakeone.workspace.model.Workspace w) {
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("id", w.getId());
        map.put("name", w.getName());
        map.put("createdAt", w.getCreatedAt());
        return map;
    }

    public static class CreateThreadRequest {
        public String title;
        public String systemPrompt;
    }

    public static class UpdateThreadRequest {
        public String title;
    }
}
