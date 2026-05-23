-- Workspaces table
CREATE TABLE workspaces (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

-- Workspace files table (blobs stored in PostgreSQL BYTEA)
CREATE TABLE workspace_files (
    id BIGSERIAL PRIMARY KEY,
    workspace_id BIGINT NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    file_name VARCHAR(500) NOT NULL,
    mime_type VARCHAR(255),
    file_data BYTEA NOT NULL,
    file_size BIGINT NOT NULL DEFAULT 0,
    indexed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Junction table for message-to-file attachments
CREATE TABLE dialog_message_attachments (
    message_id BIGINT NOT NULL REFERENCES dialog_messages(id) ON DELETE CASCADE,
    file_id BIGINT NOT NULL REFERENCES workspace_files(id) ON DELETE CASCADE,
    PRIMARY KEY (message_id, file_id)
);

-- Add workspace_id to dialog_threads (nullable, for linking a thread to a workspace)
ALTER TABLE dialog_threads ADD COLUMN workspace_id BIGINT REFERENCES workspaces(id) ON DELETE SET NULL;

-- Indexes
CREATE INDEX idx_workspaces_user ON workspaces(user_id);
CREATE INDEX idx_workspace_files_workspace ON workspace_files(workspace_id);
CREATE INDEX idx_dialog_threads_workspace ON dialog_threads(workspace_id);
