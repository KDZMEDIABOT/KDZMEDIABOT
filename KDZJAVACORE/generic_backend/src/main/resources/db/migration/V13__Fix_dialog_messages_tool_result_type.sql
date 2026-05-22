-- Fix tool_result column type from JSONB to TEXT
-- JSONB was causing type mismatch errors when inserting string values
ALTER TABLE dialog_messages ALTER COLUMN tool_result TYPE TEXT USING tool_result::TEXT;
