-- Initial database schema for AI Content Generator System

-- Topics table
CREATE TABLE IF NOT EXISTS topics (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    category VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    primary_keyword VARCHAR(255),
    secondary_keywords VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    scheduled_date TIMESTAMP,
    batch_number INT,
    version BIGINT DEFAULT 0
);

-- Articles table
CREATE TABLE IF NOT EXISTS articles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    topic_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    meta_description VARCHAR(500),
    slug VARCHAR(255) NOT NULL,
    content LONGTEXT,
    internal_links TEXT,
    external_links TEXT,
    status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMP,
    FOREIGN KEY (topic_id) REFERENCES topics(id),
    version BIGINT DEFAULT 0
);

-- Article sections
CREATE TABLE IF NOT EXISTS article_sections (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    article_id BIGINT NOT NULL,
    type VARCHAR(50),
    content TEXT,
    order_index INT,
    FOREIGN KEY (article_id) REFERENCES articles(id) ON DELETE CASCADE
);

-- SEO metadata
CREATE TABLE IF NOT EXISTS seo_metadata (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    article_id BIGINT NOT NULL,
    seo_title VARCHAR(100),
    meta_description VARCHAR(160),
    url_slug VARCHAR(255),
    primary_keyword VARCHAR(255),
    secondary_keywords VARCHAR(500),
    FOREIGN KEY (article_id) REFERENCES articles(id) ON DELETE CASCADE
);

-- FAQ items
CREATE TABLE IF NOT EXISTS faq_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    article_id BIGINT NOT NULL,
    question VARCHAR(500),
    answer TEXT,
    FOREIGN KEY (article_id) REFERENCES articles(id) ON DELETE CASCADE
);

-- Research data
CREATE TABLE IF NOT EXISTS research_data (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    topic_id BIGINT NOT NULL,
    summary LONGTEXT,
    expert_quotes TEXT,
    statistics TEXT,
    ai_provider VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (topic_id) REFERENCES topics(id)
);

-- Research sources
CREATE TABLE IF NOT EXISTS research_sources (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    research_id BIGINT NOT NULL,
    url VARCHAR(500),
    title VARCHAR(255),
    author VARCHAR(255),
    year VARCHAR(10),
    FOREIGN KEY (research_id) REFERENCES research_data(id) ON DELETE CASCADE
);

-- Research citations
CREATE TABLE IF NOT EXISTS research_citations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    research_id BIGINT NOT NULL,
    citation VARCHAR(1000),
    FOREIGN KEY (research_id) REFERENCES research_data(id) ON DELETE CASCADE
);

-- Images
CREATE TABLE IF NOT EXISTS images (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    topic_id BIGINT NOT NULL,
    url VARCHAR(500) NOT NULL,
    alt_text VARCHAR(255),
    caption VARCHAR(500),
    source VARCHAR(50) NOT NULL,
    pexels_id VARCHAR(50),
    photographer_name VARCHAR(100),
    photographer_url VARCHAR(500),
    relevance_score INT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (topic_id) REFERENCES topics(id)
);

-- Reviews
CREATE TABLE IF NOT EXISTS reviews (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    article_id BIGINT NOT NULL,
    reviewer_id VARCHAR(100) NOT NULL,
    decision VARCHAR(50) NOT NULL,
    feedback LONGTEXT,
    quality_score INT,
    revision_notes TEXT,
    reviewed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    revision_due TIMESTAMP,
    revision_count INT DEFAULT 0,
    FOREIGN KEY (article_id) REFERENCES articles(id)
);

-- Indexes
CREATE INDEX idx_topics_status ON topics(status);
CREATE INDEX idx_topics_category ON topics(category);
CREATE INDEX idx_topics_batch ON topics(batch_number);
CREATE INDEX idx_articles_status ON articles(status);
CREATE INDEX idx_articles_topic ON articles(topic_id);

-- Insert sample data
INSERT INTO topics (title, description, category, status, primary_keyword, secondary_keywords, batch_number)
VALUES
('Mindfulness-Based Stress Reduction', 'Comprehensive guide to MBSR practices', 'ART_THERAPY', 'APPROVED', 'mindfulness stress reduction', 'meditation,anxiety relief', 1),
('Dependent Personality Disorder Test', 'Self-assessment for DPD traits', 'PSYCHOLOGY_TEST', 'APPROVED', 'dependent personality test', 'personality disorder,mental health', 1),
('Best Mental Health Apps 2025', 'Curated list of top mental health applications', 'RECOMMENDATION_LIST', 'APPROVED', 'best mental health apps', 'anxiety apps,therapy apps', 1);
