-- Table for article generation job queue/workflow
CREATE TABLE article_generation_jobs (
    id BIGSERIAL PRIMARY KEY,
    topic VARCHAR(255) NOT NULL,
    content_type VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'QUEUED',
    process_definition_id VARCHAR(128) NOT NULL DEFAULT 'article.generation.v1',
    process_instance_id BIGINT,
    article_id BIGINT,
    requester_role VARCHAR(64) NOT NULL,
    requester_user_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    error_message VARCHAR(2000)
);

-- Indexes for common queries
CREATE INDEX idx_article_gen_jobs_status ON article_generation_jobs(status);
CREATE INDEX idx_article_gen_jobs_requester ON article_generation_jobs(requester_user_id);
CREATE INDEX idx_article_gen_jobs_created_at ON article_generation_jobs(created_at);
