ALTER TABLE llm_endpoint_credentials
    ADD COLUMN IF NOT EXISTS use_specified_user_agent BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE llm_endpoint_credentials
    ADD COLUMN IF NOT EXISTS user_agent VARCHAR(1000);
