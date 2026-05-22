-- Dialog thread table
CREATE TABLE dialog_threads (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    title VARCHAR(255) NOT NULL,
    status VARCHAR(20) DEFAULT 'active',
    model_name VARCHAR(255),
    system_prompt TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    last_message_at TIMESTAMP
);

-- Dialog message table
CREATE TABLE dialog_messages (
    id BIGSERIAL PRIMARY KEY,
    thread_id BIGINT NOT NULL REFERENCES dialog_threads(id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    tool_name VARCHAR(255),
    tool_result JSONB,
    tokens_used INT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_dialog_threads_user ON dialog_threads(user_id);
CREATE INDEX idx_dialog_threads_last_msg ON dialog_threads(last_message_at DESC);
CREATE INDEX idx_dialog_messages_thread ON dialog_messages(thread_id, created_at);
