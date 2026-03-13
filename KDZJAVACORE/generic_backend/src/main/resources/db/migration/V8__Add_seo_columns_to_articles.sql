-- Add missing SEO columns to articles table for embedded SeoMetadata
ALTER TABLE articles
    ADD COLUMN IF NOT EXISTS seo_title VARCHAR(100),
    ADD COLUMN IF NOT EXISTS url_slug VARCHAR(255),
    ADD COLUMN IF NOT EXISTS primary_keyword VARCHAR(255),
    ADD COLUMN IF NOT EXISTS secondary_keywords VARCHAR(500);
