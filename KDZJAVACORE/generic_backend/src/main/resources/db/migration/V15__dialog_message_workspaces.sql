-- Junction table for dialog messages to workspaces
CREATE TABLE dialog_message_workspaces (
    message_id BIGINT NOT NULL REFERENCES dialog_messages(id) ON DELETE CASCADE,
    workspace_id BIGINT NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    PRIMARY KEY (message_id, workspace_id)
);
