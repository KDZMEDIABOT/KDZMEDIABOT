-- Rename citation column to citations to match @ElementCollection field name
ALTER TABLE research_citations RENAME COLUMN citation TO citations;
