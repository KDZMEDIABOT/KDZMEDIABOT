ALTER TABLE llm_endpoint_credentials
    ADD COLUMN IF NOT EXISTS model_name VARCHAR(255);
