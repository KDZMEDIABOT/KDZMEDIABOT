CREATE TABLE IF NOT EXISTS llm_endpoint_credentials (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    llm_api_type VARCHAR(50) NOT NULL,
    base_url VARCHAR(1000) NOT NULL,
    api_key VARCHAR(2000) NOT NULL,
    user_id BIGINT NOT NULL,
    endpoint_display_name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_llm_credentials_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS current_llm_endpoint_id BIGINT;

ALTER TABLE users
    ADD CONSTRAINT fk_users_current_llm_endpoint
    FOREIGN KEY (current_llm_endpoint_id)
    REFERENCES llm_endpoint_credentials(id)
    ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_llm_credentials_user_id ON llm_endpoint_credentials(user_id);
