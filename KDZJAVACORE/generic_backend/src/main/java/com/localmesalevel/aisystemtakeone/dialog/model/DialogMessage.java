package com.localmesalevel.aisystemtakeone.dialog.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.localmesalevel.aisystemtakeone.workspace.model.Workspace;
import com.localmesalevel.aisystemtakeone.workspace.model.WorkspaceFile;

import javax.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "dialog_messages")
public class DialogMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "thread_id", nullable = false, insertable = false, updatable = false)
    private Long threadId;

    @Column(nullable = false, length = 20)
    private String role;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "tool_name", length = 255)
    private String toolName;

    @Column(name = "tool_result", columnDefinition = "TEXT")
    private String toolResult;

    @Column(name = "tokens_used")
    private Integer tokensUsed;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "thread_id", nullable = false)
    @JsonIgnore
    private DialogThread thread;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "dialog_message_attachments",
        joinColumns = @JoinColumn(name = "message_id"),
        inverseJoinColumns = @JoinColumn(name = "file_id")
    )
    @JsonIgnore
    private List<WorkspaceFile> attachedFiles = new ArrayList<>();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getThreadId() {
        return threadId;
    }

    public void setThreadId(Long threadId) {
        this.threadId = threadId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getToolName() {
        return toolName;
    }

    public void setToolName(String toolName) {
        this.toolName = toolName;
    }

    public String getToolResult() {
        return toolResult;
    }

    public void setToolResult(String toolResult) {
        this.toolResult = toolResult;
    }

    public Integer getTokensUsed() {
        return tokensUsed;
    }

    public void setTokensUsed(Integer tokensUsed) {
        this.tokensUsed = tokensUsed;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public DialogThread getThread() {
        return thread;
    }

    public void setThread(DialogThread thread) {
        this.thread = thread;
    }

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "dialog_message_workspaces",
        joinColumns = @JoinColumn(name = "message_id"),
        inverseJoinColumns = @JoinColumn(name = "workspace_id")
    )
    @JsonIgnore
    private List<Workspace> attachedWorkspaces = new ArrayList<>();

    public List<WorkspaceFile> getAttachedFiles() {
        return attachedFiles;
    }

    public void setAttachedFiles(List<WorkspaceFile> attachedFiles) {
        this.attachedFiles = attachedFiles;
    }

    public List<Workspace> getAttachedWorkspaces() {
        return attachedWorkspaces;
    }

    public void setAttachedWorkspaces(List<Workspace> attachedWorkspaces) {
        this.attachedWorkspaces = attachedWorkspaces;
    }
}
