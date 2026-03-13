DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'article_sections'
          AND column_name = 'content'
    ) THEN
        ALTER TABLE article_sections
            ALTER COLUMN content TYPE TEXT;
    END IF;
END
$$;
