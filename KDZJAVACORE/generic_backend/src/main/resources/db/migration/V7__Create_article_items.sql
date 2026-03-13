-- Table for Article FaqItem element collection (embedded FaqSection -> items)
-- Default Hibernate naming: <entity>_<embedded>_<collection> = articles_faq_section_items
CREATE TABLE IF NOT EXISTS articles_faq_section_items (
    article_id BIGINT NOT NULL,
    question VARCHAR(500),
    answer TEXT,
    items_order INT,
    FOREIGN KEY (article_id) REFERENCES articles(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_articles_faq_section_items_article_id ON articles_faq_section_items(article_id);
