ALTER TABLE dialog_messages ADD COLUMN error BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE dialog_messages ADD COLUMN is_reply_to BIGINT NULL REFERENCES dialog_messages(id) ON DELETE SET NULL;
CREATE INDEX idx_dialog_messages_is_reply_to ON dialog_messages(is_reply_to);
