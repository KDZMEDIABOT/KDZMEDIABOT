ALTER TABLE users ADD COLUMN default_workspace_id BIGINT REFERENCES workspaces(id) ON DELETE SET NULL;
