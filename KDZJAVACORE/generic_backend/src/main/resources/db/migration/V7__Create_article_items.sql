-- Table for Article FaqItem element collection
CREATE TABLE IF NOT EXISTS article_items (
    article_id BIGINT NOT NULL,
    question VARCHAR(500),
    answer TEXT,
    items_order INT,
    FOREIGN KEY (article_id) REFERENCES articles(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_article_items_article_id ON article_items(article_id);
